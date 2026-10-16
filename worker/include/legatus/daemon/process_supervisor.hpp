#pragma once

#include "legatus/runner/execution_envelope.hpp"
#include <string>
#include <vector>
#include <memory>
#include <mutex>
#include <unordered_map>
#include <chrono>
#include <functional>

namespace reproc {
class process;
}

namespace legatus::daemon {

using LogChunkCallback = std::function<void(const std::string& stream, const std::string& chunk)>;

struct ActiveTaskInfo {
    std::string task_id;
    int pid{0};
    uint64_t instructions_consumed{0};
    uint64_t memory_used_bytes{0};
    int64_t elapsed_wall_time_ms{0};
};

class ProcessSupervisor {
public:
    explicit ProcessSupervisor(
        std::string runner_path,
        std::chrono::milliseconds grace_period = std::chrono::milliseconds(10000),
        std::chrono::milliseconds kill_timeout = std::chrono::milliseconds(2000));
    ~ProcessSupervisor();

    ProcessSupervisor(const ProcessSupervisor&) = delete;
    ProcessSupervisor& operator=(const ProcessSupervisor&) = delete;

    runner::ExecutionResult launch_task(
        const runner::ExecutionEnvelope& envelope,
        LogChunkCallback on_log_chunk = nullptr);

    bool abort_task(const std::string& task_id);

    std::vector<std::string> get_active_tasks() const;
    std::vector<ActiveTaskInfo> get_active_tasks_info() const;
    bool is_task_active(const std::string& task_id) const;
    std::size_t active_task_count() const;

    void set_runner_path(std::string runner_path) {
        std::lock_guard<std::mutex> lock(mutex_);
        runner_path_ = std::move(runner_path);
    }

private:
    struct InternalTaskRecord {
        std::shared_ptr<reproc::process> proc;
        std::string task_id;
        int pid{0};
        std::chrono::steady_clock::time_point start_time;
        uint64_t instructions_consumed{0};
        uint64_t memory_used_bytes{0};
    };

    std::string runner_path_;
    std::chrono::milliseconds grace_period_;
    std::chrono::milliseconds kill_timeout_;

    mutable std::mutex mutex_;
    std::unordered_map<std::string, InternalTaskRecord> active_processes_;
};

} // namespace legatus::daemon
