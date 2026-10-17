#include "legatus/daemon/process_supervisor.hpp"
#include <reproc++/reproc.hpp>
#include <reproc++/drain.hpp>
#include <nlohmann/json.hpp>
#include <iostream>
#include <fstream>
#include <filesystem>
#include <cstring>
#if !defined(_WIN32)
#include <sys/types.h>
#include <signal.h>
#endif

namespace legatus::daemon {

ProcessSupervisor::ProcessSupervisor(
    std::string runner_path,
    std::chrono::milliseconds grace_period,
    std::chrono::milliseconds kill_timeout)
    : runner_path_(std::move(runner_path)),
      grace_period_(grace_period),
      kill_timeout_(kill_timeout) {}

ProcessSupervisor::~ProcessSupervisor() {
    std::lock_guard<std::mutex> lock(mutex_);
    for (auto& [task_id, record] : active_processes_) {
        if (record.proc) {
            record.proc->stop({
                { reproc::stop::terminate, reproc::milliseconds(100) },
                { reproc::stop::kill, reproc::milliseconds(100) },
                { reproc::stop::noop, reproc::milliseconds(0) }
            });
        }
    }
    active_processes_.clear();
}

runner::ExecutionResult ProcessSupervisor::launch_task(
    const runner::ExecutionEnvelope& envelope,
    LogChunkCallback on_log_chunk) {
    runner::ExecutionResult result;
    result.task_id = envelope.task_id;

    nlohmann::json j;
    j["task_id"] = envelope.task_id;
    j["function_name"] = envelope.function_name;
    j["bytecode_base64"] = envelope.bytecode_base64;
    j["bytecode_path"] = envelope.bytecode_path;
    j["arguments"] = envelope.arguments_json;
    j["secrets"] = envelope.secrets;
    j["instruction_budget"] = envelope.instruction_budget;
    j["memory_limit_bytes"] = envelope.memory_limit_bytes;
    j["timeout_ms"] = envelope.timeout_ms;
    std::string input_payload = j.dump();

    auto proc = std::make_shared<reproc::process>();

    std::filesystem::path result_file_path = std::filesystem::temp_directory_path() /
        ("legatus_res_" + envelope.task_id + "_" +
         std::to_string(std::chrono::steady_clock::now().time_since_epoch().count()) + ".json");

    struct ResultFileGuard {
        std::filesystem::path path;
        ~ResultFileGuard() {
            std::error_code ignore_ec;
            std::filesystem::remove(path, ignore_ec);
        }
    } file_guard{result_file_path};

    std::vector<std::string> args = { runner_path_, "--result-file", result_file_path.string() };
    reproc::options opts;
    opts.stop = {
        { reproc::stop::terminate, reproc::milliseconds(grace_period_.count()) },
        { reproc::stop::kill, reproc::milliseconds(kill_timeout_.count()) },
        { reproc::stop::noop, reproc::milliseconds(0) }
    };
    if (envelope.timeout_ms > 0) {
        opts.deadline = reproc::milliseconds(envelope.timeout_ms);
    } else {
        opts.deadline = reproc::infinite;
    }

    std::error_code ec = proc->start(args, opts);
    if (ec) {
        result.exit_code = 70; // EX_SOFTWARE
        result.error_message = "Failed to spawn runner process: " + ec.message();
        return result;
    }

    int child_pid = 0;
    auto [pid_val, pid_ec] = proc->pid();
    if (!pid_ec) {
        child_pid = pid_val;
    }

    {
        std::lock_guard<std::mutex> lock(mutex_);
        active_processes_[envelope.task_id] = InternalTaskRecord{
            proc,
            envelope.task_id,
            child_pid,
            std::chrono::steady_clock::now(),
            0,
            0
        };
    }

    struct ProcessCleanupGuard {
        ProcessSupervisor& supervisor;
        const std::string& task_id;
        ~ProcessCleanupGuard() {
            std::lock_guard<std::mutex> lock(supervisor.mutex_);
            supervisor.active_processes_.erase(task_id);
        }
    } guard{*this, envelope.task_id};

    // Stream envelope over stdin until EOF
    if (!input_payload.empty()) {
        size_t written = 0;
        const uint8_t* data = reinterpret_cast<const uint8_t*>(input_payload.data());
        size_t to_write = input_payload.size();
        while (written < to_write) {
            size_t bytes = 0;
            std::tie(bytes, ec) = proc->write(data + written, to_write - written);
            if (ec) break;
            written += bytes;
        }
    }
    proc->close(reproc::stream::in);

    // Drain stdout and stderr with real-time chunk streaming and non-blocking rate limiting
    std::string stdout_str;
    std::string stderr_str;
    constexpr size_t MAX_LOG_VOLUME_BYTES = 8 * 1024 * 1024; // 8MB ceiling
    size_t total_volume_bytes = 0;
    bool ceiling_reached = false;

    // Rate limiter: max 1,000 chunks/second per task (no blocking sleep)
    auto rate_window_start = std::chrono::steady_clock::now();
    size_t chunks_in_window = 0;
    bool rate_warning_emitted = false;

    auto process_stream_data = [&](const std::string& stream_name, std::string& full_str, const uint8_t* buffer, size_t size) -> std::error_code {
        if (size == 0) return {};

        // Accumulate in buffer for final execution result parsing (bounded)
        if (full_str.size() + size <= MAX_LOG_VOLUME_BYTES) {
            full_str.append(reinterpret_cast<const char*>(buffer), size);
        }

        // Check for progress checkpoint pattern: [PROGRESS:<inst>:<checkpoint>]
        std::string_view buf_sv(reinterpret_cast<const char*>(buffer), size);
        auto pos = buf_sv.find("[PROGRESS:");
        if (pos != std::string_view::npos) {
            try {
                uint64_t inst = std::strtoull(buf_sv.data() + pos + 10, nullptr, 10);
                std::lock_guard<std::mutex> lock(mutex_);
                auto it = active_processes_.find(envelope.task_id);
                if (it != active_processes_.end()) {
                    it->second.instructions_consumed = inst;
                }
            } catch (...) {}
        }

        if (ceiling_reached) {
            return {};
        }

        if (total_volume_bytes + size > MAX_LOG_VOLUME_BYTES) {
            ceiling_reached = true;
            if (on_log_chunk) {
                on_log_chunk(stream_name, "\n[Output truncated: 8MB log volume ceiling reached]\n");
            }
            return {};
        }

        total_volume_bytes += size;

        if (on_log_chunk) {
            auto now = std::chrono::steady_clock::now();
            auto elapsed_ms = std::chrono::duration_cast<std::chrono::milliseconds>(now - rate_window_start).count();
            if (elapsed_ms >= 1000) {
                rate_window_start = now;
                chunks_in_window = 0;
                rate_warning_emitted = false;
            }

            chunks_in_window++;
            if (chunks_in_window <= 1000) {
                std::string chunk(reinterpret_cast<const char*>(buffer), size);
                on_log_chunk(stream_name, chunk);
            } else if (!rate_warning_emitted) {
                rate_warning_emitted = true;
                on_log_chunk(stream_name, "\n[Output throttled: rate limit of 1000 chunks/sec exceeded]\n");
            }
        }
        return {};
    };

    auto out_sink = [&](reproc::stream s, const uint8_t* b, size_t sz) {
        (void)s;
        return process_stream_data("stdout", stdout_str, b, sz);
    };
    auto err_sink = [&](reproc::stream s, const uint8_t* b, size_t sz) {
        (void)s;
        return process_stream_data("stderr", stderr_str, b, sz);
    };

    std::error_code drain_ec = reproc::drain(*proc, out_sink, err_sink);
    if (drain_ec == std::errc::timed_out) {
        // Escalate termination
        proc->stop({
            { reproc::stop::terminate, reproc::milliseconds(grace_period_.count()) },
            { reproc::stop::kill, reproc::milliseconds(kill_timeout_.count()) },
            { reproc::stop::noop, reproc::milliseconds(0) }
        });
        result.exit_code = 124; // TIMED_OUT standard exit code
        result.error_message = "Task execution timed out after " + std::to_string(envelope.timeout_ms) + " ms";
        result.stdout_output = stdout_str;
        return result;
    }

    // Flush any remaining unread bytes from stdout and stderr pipes.
    // When a child process exits quickly, reproc::drain poll() can receive POLLHUP/broken_pipe
    // before OS pipe buffers are exhausted. Read until EOF to guarantee full capture.
    constexpr size_t DRAIN_BUFFER_SIZE = 4096;
    uint8_t drain_buffer[DRAIN_BUFFER_SIZE] = {};

    while (true) {
        size_t b_out = 0;
        std::error_code read_ec;
        std::tie(b_out, read_ec) = proc->read(reproc::stream::out, drain_buffer, DRAIN_BUFFER_SIZE);
        if (!read_ec && b_out > 0) {
            out_sink(reproc::stream::out, drain_buffer, b_out);
        }
        if (read_ec || b_out == 0) {
            break;
        }
    }

    while (true) {
        size_t b_err = 0;
        std::error_code read_ec;
        std::tie(b_err, read_ec) = proc->read(reproc::stream::err, drain_buffer, DRAIN_BUFFER_SIZE);
        if (!read_ec && b_err > 0) {
            err_sink(reproc::stream::err, drain_buffer, b_err);
        }
        if (read_ec || b_err == 0) {
            break;
        }
    }

    int status = 0;
    std::tie(status, ec) = proc->wait(reproc::milliseconds(5000));
    if (ec) {
        auto [stop_status, _] = proc->stop({
            { reproc::stop::terminate, reproc::milliseconds(grace_period_.count()) },
            { reproc::stop::kill, reproc::milliseconds(kill_timeout_.count()) },
            { reproc::stop::noop, reproc::milliseconds(0) }
        });
        status = stop_status;
    }

    // 1. Try reading from dedicated result file first
    bool result_parsed = false;
    if (std::filesystem::exists(result_file_path)) {
        try {
            std::ifstream rfs(result_file_path);
            if (rfs.is_open()) {
                std::string file_content((std::istreambuf_iterator<char>(rfs)), std::istreambuf_iterator<char>());
                rfs.close();
                if (!file_content.empty()) {
                    nlohmann::json res_j = nlohmann::json::parse(file_content);
                    result.exit_code = res_j.value("exit_code", status);
                    result.instructions_executed = res_j.value("instructions_executed", 0ULL);
                    result.peak_memory_bytes = res_j.value("peak_memory_bytes", 0ULL);
                    result.stdout_output = res_j.value("stdout_output", stdout_str);
                    result.error_message = res_j.value("error_message", stderr_str);
                    result_parsed = true;
                }
            }
        } catch (...) {}
    }

    // 2. Fallback to trying to parse stdout as JSON (backward compatibility)
    if (!result_parsed && !stdout_str.empty()) {
        try {
            nlohmann::json res_j = nlohmann::json::parse(stdout_str);
            result.exit_code = res_j.value("exit_code", status);
            result.instructions_executed = res_j.value("instructions_executed", 0ULL);
            result.peak_memory_bytes = res_j.value("peak_memory_bytes", 0ULL);
            result.stdout_output = res_j.value("stdout_output", "");
            result.error_message = res_j.value("error_message", "");
            result_parsed = true;
        } catch (...) {}
    }

    if (!result_parsed) {
        result.exit_code = status;
        result.stdout_output = stdout_str;
        result.error_message = stderr_str;
    }

    // Progress checkpoint parsing fallback if instructions_executed is 0
    if (result.instructions_executed == 0 && !stdout_str.empty()) {
        size_t p_pos = stdout_str.rfind("[PROGRESS:");
        if (p_pos != std::string::npos) {
            size_t next_colon = stdout_str.find(':', p_pos + 10);
            if (next_colon != std::string::npos) {
                try {
                    uint64_t inst = std::stoull(stdout_str.substr(p_pos + 10, next_colon - (p_pos + 10)));
                    result.instructions_executed = inst;
                } catch (...) {}
            }
        }
    }

    if ((result.exit_code == 143 || result.exit_code == 130) && result.error_message.empty()) {
        result.error_message = "Task execution aborted by signal";
    }

    return result;
}

bool ProcessSupervisor::abort_task(const std::string& task_id) {
    std::shared_ptr<reproc::process> proc;
    int child_pid = 0;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = active_processes_.find(task_id);
        if (it != active_processes_.end()) {
            proc = it->second.proc;
            child_pid = it->second.pid;
        }
    }

    if (!proc && child_pid <= 0) {
        return false;
    }

#if !defined(_WIN32)
    if (child_pid > 0) {
        ::kill(child_pid, SIGTERM);
        return true;
    }
#endif

    // Two-stage kill escalation: SIGTERM (10s) -> SIGKILL (2s)
    proc->stop({
        { reproc::stop::terminate, reproc::milliseconds(grace_period_.count()) },
        { reproc::stop::kill, reproc::milliseconds(kill_timeout_.count()) },
        { reproc::stop::noop, reproc::milliseconds(0) }
    });
    return true;
}

std::vector<std::string> ProcessSupervisor::get_active_tasks() const {
    std::lock_guard<std::mutex> lock(mutex_);
    std::vector<std::string> tasks;
    tasks.reserve(active_processes_.size());
    for (const auto& [id, _] : active_processes_) {
        tasks.push_back(id);
    }
    return tasks;
}

std::vector<ActiveTaskInfo> ProcessSupervisor::get_active_tasks_info() const {
    std::lock_guard<std::mutex> lock(mutex_);
    std::vector<ActiveTaskInfo> info_list;
    info_list.reserve(active_processes_.size());
    auto now = std::chrono::steady_clock::now();
    for (const auto& [id, record] : active_processes_) {
        ActiveTaskInfo info;
        info.task_id = id;
        info.pid = record.pid;
        info.instructions_consumed = record.instructions_consumed;
        info.memory_used_bytes = record.memory_used_bytes;
        info.elapsed_wall_time_ms = std::chrono::duration_cast<std::chrono::milliseconds>(now - record.start_time).count();
        info_list.push_back(info);
    }
    return info_list;
}

bool ProcessSupervisor::is_task_active(const std::string& task_id) const {
    std::lock_guard<std::mutex> lock(mutex_);
    return active_processes_.find(task_id) != active_processes_.end();
}

std::size_t ProcessSupervisor::active_task_count() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return active_processes_.size();
}

} // namespace legatus::daemon
