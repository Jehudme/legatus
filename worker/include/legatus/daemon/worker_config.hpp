#pragma once

#include <string>
#include <cstdint>

namespace legatus::daemon {

struct ServerConfig {
    std::string endpoint = "https://cloud.legatus.dev:8443";
    bool tls_verify_peer = true;
    std::string ca_cert_path = "";
};

struct IdentityConfig {
    std::string worker_id;
    std::string secret_token;
    std::string public_key;
};

struct ResourcesConfig {
    uint32_t allocatable_cores = 4;
    uint64_t max_memory_bytes = 4ULL * 1024 * 1024 * 1024; // 4 GB default
    uint64_t max_instruction_budget_per_task = 50000000;
};

struct TelemetryConfig {
    uint32_t heartbeat_interval_ms = 5000;
    uint32_t heartbeat_timeout_ms = 15000;
};

struct LoggingConfig {
    std::string level = "info";
    std::string log_directory = "/var/log/legatus";
    uint32_t max_file_size_mb = 50;
    uint32_t max_backup_files = 5;
};

class WorkerConfig {
public:
    ServerConfig server;
    IdentityConfig identity;
    ResourcesConfig resources;
    TelemetryConfig telemetry;
    LoggingConfig logging;

    static WorkerConfig load_from_file(const std::string& path);
    void save_to_file(const std::string& path) const;

    static WorkerConfig parse_toml(const std::string& toml_content);
    std::string serialize_toml() const;

    static WorkerConfig default_for_system();
};

} // namespace legatus::daemon
