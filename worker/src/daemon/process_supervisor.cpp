#include "legatus/daemon/process_supervisor.hpp"
#include <reproc++/reproc.hpp>
#include <reproc++/drain.hpp>
#include <nlohmann/json.hpp>
#include <iostream>

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
    std::string input_payload = j.dump();

    auto proc = std::make_shared<reproc::process>();

    std::vector<std::string> args = { runner_path_ };
    reproc::options opts;
    opts.stop = {
        { reproc::stop::terminate, reproc::milliseconds(grace_period_.count()) },
        { reproc::stop::kill, reproc::milliseconds(kill_timeout_.count()) },
        { reproc::stop::noop, reproc::milliseconds(0) }
    };

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

    // Drain stdout and stderr with real-time chunk streaming and backpressure
    std::string stdout_str;
    std::string stderr_str;
    constexpr size_t MAX_LOG_VOLUME_BYTES = 8 * 1024 * 1024; // 8MB ceiling
    size_t total_volume_bytes = 0;
    bool ceiling_reached = false;

    // Rate limiter: max 1,000 chunks/second per task
    auto rate_window_start = std::chrono::steady_clock::now();
    size_t chunks_in_window = 0;

    auto make_sink = [&](const std::string& stream_name, std::string& full_str) {
        return [&](reproc::stream /*stream*/, const uint8_t* buffer, size_t size) -> std::error_code {
            if (size == 0) return {};

            // Accumulate in buffer for final execution result parsing (bounded)
            if (full_str.size() + size <= MAX_LOG_VOLUME_BYTES) {
                full_str.append(reinterpret_cast<const char*>(buffer), size);
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
                // Rate-limiting backpressure: max 1000 chunks per 1 second
                chunks_in_window++;
                if (chunks_in_window > 1000) {
                    auto now = std::chrono::steady_clock::now();
                    auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(now - rate_window_start).count();
                    if (elapsed < 1000) {
                        std::this_thread::sleep_for(std::chrono::milliseconds(1000 - elapsed));
                    }
                    rate_window_start = std::chrono::steady_clock::now();
                    chunks_in_window = 1;
                }

                std::string chunk(reinterpret_cast<const char*>(buffer), size);
                on_log_chunk(stream_name, chunk);
            }
            return {};
        };
    };

    auto out_sink = make_sink("stdout", stdout_str);
    auto err_sink = make_sink("stderr", stderr_str);
    ec = reproc::drain(*proc, out_sink, err_sink);

    int status = 0;
    std::tie(status, ec) = proc->wait(reproc::infinite);

    // Try parsing stdout as JSON ExecutionResult
    try {
        if (!stdout_str.empty()) {
            nlohmann::json res_j = nlohmann::json::parse(stdout_str);
            result.exit_code = res_j.value("exit_code", status);
            result.instructions_executed = res_j.value("instructions_executed", 0ULL);
            result.peak_memory_bytes = res_j.value("peak_memory_bytes", 0ULL);
            result.stdout_output = res_j.value("stdout_output", "");
            result.error_message = res_j.value("error_message", "");
            return result;
        }
    } catch (...) {
        // Fallback to raw output
    }

    result.exit_code = status;
    result.stdout_output = stdout_str;
    result.error_message = stderr_str;
    return result;
}

bool ProcessSupervisor::abort_task(const std::string& task_id) {
    std::shared_ptr<reproc::process> proc;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = active_processes_.find(task_id);
        if (it != active_processes_.end()) {
            proc = it->second.proc;
        }
    }

    if (!proc) {
        return false;
    }

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
