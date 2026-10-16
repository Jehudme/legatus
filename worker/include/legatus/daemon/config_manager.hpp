#pragma once

#include <string>
#include <cstdint>
#include <mutex>
#include <functional>
#include <vector>
#include <chrono>
#include <nlohmann/json.hpp>

namespace legatus::daemon {

/**
 * Authoritative dynamic configuration manager received from the Legatus server control plane.
 * Complies with MS-007 and ADR-0010.
 */
class ConfigManager {
public:
    ConfigManager();
    ~ConfigManager() = default;

    ConfigManager(const ConfigManager&) = delete;
    ConfigManager& operator=(const ConfigManager&) = delete;

    // Apply JSON configuration frame payload from CONFIG_SYNC_RESP or CONFIG_UPDATE_PUSH
    bool apply_server_config(const nlohmann::json& payload);

    // Dynamic config accessors
    uint64_t config_version() const;
    std::string friendly_name() const;
    bool is_active() const;
    double target_cpu_pct() const;
    double target_ram_pct() const;
    uint32_t max_concurrent_tasks() const;
    bool auto_renew_lease() const;
    uint64_t remaining_lease_sec() const;
    uint32_t heartbeat_interval_ms() const;
    std::string log_level() const;
    std::string runtime_config_json() const;

    // Register callback for dynamic configuration changes
    void register_listener(std::function<void()> listener);

    // Exponential backoff calculation for server reconnection attempts
    static std::chrono::milliseconds compute_backoff(
        int retry_count,
        std::chrono::milliseconds base = std::chrono::milliseconds(1000),
        std::chrono::milliseconds max_backoff = std::chrono::milliseconds(30000)
    );

private:
    mutable std::mutex mutex_;
    uint64_t config_version_{1};
    std::string friendly_name_{"worker-node"};
    bool active_{true};
    double target_cpu_pct_{75.0};
    double target_ram_pct_{80.0};
    uint32_t max_concurrent_tasks_{16};
    bool auto_renew_lease_{true};
    uint64_t remaining_lease_sec_{86400};
    std::chrono::steady_clock::time_point lease_updated_at_{std::chrono::steady_clock::now()};
    uint32_t heartbeat_interval_ms_{5000};
    std::string log_level_{"info"};
    std::string runtime_config_json_{"{}"};

    std::vector<std::function<void()>> listeners_;
};

} // namespace legatus::daemon
