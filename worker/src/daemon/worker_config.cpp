#include "legatus/daemon/worker_config.hpp"
#include <toml++/toml.hpp>
#include <fstream>
#include <sstream>
#include <filesystem>
#include <thread>
#include <chrono>

#if defined(_WIN32)
#include <windows.h>
#else
#include <unistd.h>
#endif

namespace legatus::daemon {

WorkerConfig WorkerConfig::parse_toml(const std::string& toml_content) {
    WorkerConfig cfg;
    toml::table tbl = toml::parse(toml_content);

    if (auto server_tbl = tbl["server"].as_table()) {
        if (auto val = (*server_tbl)["endpoint"].value<std::string>()) {
            cfg.server.endpoint = *val;
        }
        if (auto val = (*server_tbl)["tls_verify_peer"].value<bool>()) {
            cfg.server.tls_verify_peer = *val;
        }
        if (auto val = (*server_tbl)["ca_cert_path"].value<std::string>()) {
            cfg.server.ca_cert_path = *val;
        }
    }

    if (auto identity_tbl = tbl["identity"].as_table()) {
        if (auto val = (*identity_tbl)["worker_id"].value<std::string>()) {
            cfg.identity.worker_id = *val;
        }
        if (auto val = (*identity_tbl)["secret_token"].value<std::string>()) {
            cfg.identity.secret_token = *val;
        }
        if (auto val = (*identity_tbl)["public_key"].value<std::string>()) {
            cfg.identity.public_key = *val;
        }
    }

    if (auto res_tbl = tbl["resources"].as_table()) {
        if (auto val = (*res_tbl)["allocatable_cores"].value<int64_t>()) {
            cfg.resources.allocatable_cores = static_cast<uint32_t>(*val);
        }
        if (auto val = (*res_tbl)["max_memory_bytes"].value<int64_t>()) {
            cfg.resources.max_memory_bytes = static_cast<uint64_t>(*val);
        }
        if (auto val = (*res_tbl)["max_instruction_budget_per_task"].value<int64_t>()) {
            cfg.resources.max_instruction_budget_per_task = static_cast<uint64_t>(*val);
        }
    }

    if (auto telem_tbl = tbl["telemetry"].as_table()) {
        if (auto val = (*telem_tbl)["heartbeat_interval_ms"].value<int64_t>()) {
            cfg.telemetry.heartbeat_interval_ms = static_cast<uint32_t>(*val);
        }
        if (auto val = (*telem_tbl)["heartbeat_timeout_ms"].value<int64_t>()) {
            cfg.telemetry.heartbeat_timeout_ms = static_cast<uint32_t>(*val);
        }
    }

    if (auto log_tbl = tbl["logging"].as_table()) {
        if (auto val = (*log_tbl)["level"].value<std::string>()) {
            cfg.logging.level = *val;
        }
        if (auto val = (*log_tbl)["log_directory"].value<std::string>()) {
            cfg.logging.log_directory = *val;
        }
        if (auto val = (*log_tbl)["max_file_size_mb"].value<int64_t>()) {
            cfg.logging.max_file_size_mb = static_cast<uint32_t>(*val);
        }
        if (auto val = (*log_tbl)["max_backup_files"].value<int64_t>()) {
            cfg.logging.max_backup_files = static_cast<uint32_t>(*val);
        }
    }

    return cfg;
}

std::string WorkerConfig::serialize_toml() const {
    toml::table tbl;

    toml::table server_tbl;
    server_tbl.insert_or_assign("endpoint", server.endpoint);
    server_tbl.insert_or_assign("tls_verify_peer", server.tls_verify_peer);
    server_tbl.insert_or_assign("ca_cert_path", server.ca_cert_path);
    tbl.insert_or_assign("server", std::move(server_tbl));

    toml::table identity_tbl;
    identity_tbl.insert_or_assign("worker_id", identity.worker_id);
    identity_tbl.insert_or_assign("secret_token", identity.secret_token);
    identity_tbl.insert_or_assign("public_key", identity.public_key);
    tbl.insert_or_assign("identity", std::move(identity_tbl));

    toml::table res_tbl;
    res_tbl.insert_or_assign("allocatable_cores", static_cast<int64_t>(resources.allocatable_cores));
    res_tbl.insert_or_assign("max_memory_bytes", static_cast<int64_t>(resources.max_memory_bytes));
    res_tbl.insert_or_assign("max_instruction_budget_per_task", static_cast<int64_t>(resources.max_instruction_budget_per_task));
    tbl.insert_or_assign("resources", std::move(res_tbl));

    toml::table telem_tbl;
    telem_tbl.insert_or_assign("heartbeat_interval_ms", static_cast<int64_t>(telemetry.heartbeat_interval_ms));
    telem_tbl.insert_or_assign("heartbeat_timeout_ms", static_cast<int64_t>(telemetry.heartbeat_timeout_ms));
    tbl.insert_or_assign("telemetry", std::move(telem_tbl));

    toml::table log_tbl;
    log_tbl.insert_or_assign("level", logging.level);
    log_tbl.insert_or_assign("log_directory", logging.log_directory);
    log_tbl.insert_or_assign("max_file_size_mb", static_cast<int64_t>(logging.max_file_size_mb));
    log_tbl.insert_or_assign("max_backup_files", static_cast<int64_t>(logging.max_backup_files));
    tbl.insert_or_assign("logging", std::move(log_tbl));

    std::stringstream ss;
    ss << tbl;
    return ss.str();
}

WorkerConfig WorkerConfig::load_from_file(const std::string& path) {
    std::ifstream ifs(path);
    if (!ifs.is_open()) {
        throw std::runtime_error("Could not open config file: " + path);
    }
    std::stringstream buffer;
    buffer << ifs.rdbuf();
    return parse_toml(buffer.str());
}

void WorkerConfig::save_to_file(const std::string& path) const {
    std::filesystem::path p(path);
    if (p.has_parent_path()) {
        std::filesystem::create_directories(p.parent_path());
    }

    std::string tmp_path = path + ".tmp." + std::to_string(std::chrono::steady_clock::now().time_since_epoch().count());
    {
        std::ofstream ofs(tmp_path);
        if (!ofs.is_open()) {
            throw std::runtime_error("Could not write to temporary config file: " + tmp_path);
        }
        ofs << serialize_toml();
        ofs.flush();
    }
    std::filesystem::rename(tmp_path, path);
}

WorkerConfig WorkerConfig::default_for_system() {
    WorkerConfig cfg;

    std::string hostname = "worker-node";
#if defined(_WIN32)
    char name_buf[256] = {0};
    DWORD size = sizeof(name_buf);
    if (GetComputerNameA(name_buf, &size)) {
        hostname = name_buf;
    }
#else
    char name_buf[256] = {0};
    if (gethostname(name_buf, sizeof(name_buf) - 1) == 0) {
        hostname = name_buf;
    }
#endif

    cfg.identity.worker_id = hostname;

    unsigned int cores = std::thread::hardware_concurrency();
    if (cores > 0) {
        cfg.resources.allocatable_cores = cores;
    }

#if defined(_SC_PHYS_PAGES) && defined(_SC_PAGE_SIZE)
    long pages = sysconf(_SC_PHYS_PAGES);
    long page_size = sysconf(_SC_PAGE_SIZE);
    if (pages > 0 && page_size > 0) {
        uint64_t total_ram = static_cast<uint64_t>(pages) * static_cast<uint64_t>(page_size);
        cfg.resources.max_memory_bytes = total_ram / 2; // Default 50% RAM
    }
#endif

    return cfg;
}

} // namespace legatus::daemon
