#pragma once

#include "legatus/runner/execution_envelope.hpp"
#include <string>
#include <vector>
#include <memory>
#include <mutex>
#include <unordered_map>
#include <chrono>

namespace reproc {
class process;
}

namespace legatus::daemon {

class ProcessSupervisor {
public:
    explicit ProcessSupervisor(
        std::string runner_path,
        std::chrono::milliseconds grace_period = std::chrono::milliseconds(10000),
        std::chrono::milliseconds kill_timeout = std::chrono::milliseconds(2000));
    ~ProcessSupervisor();

    ProcessSupervisor(const ProcessSupervisor&) = delete;
    ProcessSupervisor& operator=(const ProcessSupervisor&) = delete;

    runner::ExecutionResult launch_task(const runner::ExecutionEnvelope& envelope);

    bool abort_task(const std::string& task_id);

    std::vector<std::string> get_active_tasks() const;
    bool is_task_active(const std::string& task_id) const;
    std::size_t active_task_count() const;

    void set_runner_path(std::string runner_path) {
        std::lock_guard<std::mutex> lock(mutex_);
        runner_path_ = std::move(runner_path);
    }

private:
    std::string runner_path_;
    std::chrono::milliseconds grace_period_;
    std::chrono::milliseconds kill_timeout_;

    mutable std::mutex mutex_;
    std::unordered_map<std::string, std::shared_ptr<reproc::process>> active_processes_;
};

} // namespace legatus::daemon
