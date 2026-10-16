#include "legatus/daemon/config_manager.hpp"
#include "legatus/common/logging.hpp"
#include <spdlog/spdlog.h>
#include <cmath>
#include <algorithm>

namespace legatus::daemon {

ConfigManager::ConfigManager() = default;

bool ConfigManager::apply_server_config(const nlohmann::json& payload) {
    std::vector<std::function<void()>> to_notify;
    std::string new_level;
    bool level_changed = false;

    {
        std::lock_guard<std::mutex> lock(mutex_);

        if (payload.contains("config_version")) {
            config_version_ = payload.value("config_version", config_version_);
        }
        if (payload.contains("friendly_name") && !payload["friendly_name"].is_null()) {
            friendly_name_ = payload.value("friendly_name", friendly_name_);
        }
        if (payload.contains("active")) {
            active_ = payload.value("active", active_);
        }
        if (payload.contains("target_cpu_pct")) {
            target_cpu_pct_ = payload.value("target_cpu_pct", target_cpu_pct_);
        }
        if (payload.contains("target_ram_pct")) {
            target_ram_pct_ = payload.value("target_ram_pct", target_ram_pct_);
        }
        if (payload.contains("max_concurrent_tasks")) {
            max_concurrent_tasks_ = payload.value("max_concurrent_tasks", max_concurrent_tasks_);
        }
        if (payload.contains("auto_renew_lease")) {
            auto_renew_lease_ = payload.value("auto_renew_lease", auto_renew_lease_);
        }
        if (payload.contains("remaining_lease_sec")) {
            remaining_lease_sec_ = payload.value("remaining_lease_sec", remaining_lease_sec_);
        }
        if (payload.contains("heartbeat_interval_ms")) {
            heartbeat_interval_ms_ = payload.value("heartbeat_interval_ms", heartbeat_interval_ms_);
        }
        if (payload.contains("log_level") && !payload["log_level"].is_null()) {
            std::string lvl = payload.value("log_level", log_level_);
            if (lvl != log_level_) {
                new_level = lvl;
                log_level_ = lvl;
                level_changed = true;
            }
        }
        if (payload.contains("runtime_config_json") && !payload["runtime_config_json"].is_null()) {
            runtime_config_json_ = payload.value("runtime_config_json", runtime_config_json_);
        }

        to_notify = listeners_;
    }

    if (level_changed && !new_level.empty()) {
        common::set_log_level_string(new_level);
        spdlog::info("ConfigManager applied dynamic log level: {}", new_level);
    }

    for (const auto& cb : to_notify) {
        if (cb) {
            cb();
        }
    }

    spdlog::info("ConfigManager updated: version={}, name='{}', active={}, max_tasks={}, cpu_target={:.1f}%, ram_target={:.1f}%",
                 config_version(), friendly_name(), is_active(), max_concurrent_tasks(), target_cpu_pct(), target_ram_pct());
    return true;
}

uint64_t ConfigManager::config_version() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return config_version_;
}

std::string ConfigManager::friendly_name() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return friendly_name_;
}

bool ConfigManager::is_active() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return active_;
}

double ConfigManager::target_cpu_pct() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return target_cpu_pct_;
}

double ConfigManager::target_ram_pct() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return target_ram_pct_;
}

uint32_t ConfigManager::max_concurrent_tasks() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return max_concurrent_tasks_;
}

bool ConfigManager::auto_renew_lease() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return auto_renew_lease_;
}

uint64_t ConfigManager::remaining_lease_sec() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return remaining_lease_sec_;
}

uint32_t ConfigManager::heartbeat_interval_ms() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return heartbeat_interval_ms_;
}

std::string ConfigManager::log_level() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return log_level_;
}

std::string ConfigManager::runtime_config_json() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return runtime_config_json_;
}

void ConfigManager::register_listener(std::function<void()> listener) {
    std::lock_guard<std::mutex> lock(mutex_);
    listeners_.push_back(std::move(listener));
}

std::chrono::milliseconds ConfigManager::compute_backoff(
    int retry_count,
    std::chrono::milliseconds base,
    std::chrono::milliseconds max_backoff
) {
    if (retry_count <= 0) return base;
    double factor = std::pow(2.0, std::min(retry_count, 10));
    int64_t delay_ms = static_cast<int64_t>(base.count() * factor);
    if (delay_ms > max_backoff.count()) {
        delay_ms = max_backoff.count();
    }
    return std::chrono::milliseconds(delay_ms);
}

} // namespace legatus::daemon
