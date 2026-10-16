#include "legatus/version.hpp"
#include "legatus/daemon/ipc_bus.hpp"
#include "legatus/daemon/worker_config.hpp"
#include "legatus/common/paths.hpp"
#include "legatus/cli/table_formatter.hpp"
#include <CLI/CLI.hpp>
#include <iostream>
#include <fstream>
#include <filesystem>

int main(int argc, char* argv[]) {
    CLI::App app{"Legatus Worker Subsystem Operator CLI"};
    app.set_version_flag("--version,-v", std::string("legatus v") + std::string(legatus::VERSION));

    std::string custom_ipc_endpoint;
    app.add_option("--ipc", custom_ipc_endpoint, "Custom IPC bus endpoint URI");

    auto get_client = [&]() {
        std::string ep = custom_ipc_endpoint.empty()
            ? legatus::common::PathResolver::get_default_ipc_path().string()
            : custom_ipc_endpoint;
        return legatus::daemon::IpcClient(ep);
    };

    // 1. legatus up [-s, --server <url>] [-c, --config <path>] [-d, --daemon]
    auto* up_cmd = app.add_subcommand("up", "Start the legatusd supervisor daemon and connect to the server");
    std::string server_url_arg;
    std::string config_arg;
    bool daemon_flag = false;
    up_cmd->add_option("-s,--server", server_url_arg, "Legatus server endpoint URL (e.g. http://localhost:8080)");
    up_cmd->add_option("-c,--config", config_arg, "Path to worker configuration cache file");
    up_cmd->add_flag("-d,--daemon", daemon_flag, "Run as detached background daemon (redirect output to log file)");
    up_cmd->callback([&]() {
        auto client = get_client();
        auto res = client.daemon_status();
        if (res.status == "SUCCESS") {
            std::cout << "[INFO] legatusd daemon is already running (name: "
                      << res.data.value("friendly_name", "worker-node") << ", active: "
                      << (res.data.value("active", true) ? "true" : "false") << ").\n";
            return;
        }

        std::string cfg = config_arg.empty()
            ? legatus::common::PathResolver::get_config_path().string()
            : config_arg;

        // If server URL is explicitly provided on CLI, ensure it is written to the config cache
        if (!server_url_arg.empty()) {
            legatus::daemon::WorkerConfig config;
            if (std::filesystem::exists(cfg)) {
                try {
                    config = legatus::daemon::WorkerConfig::load_from_file(cfg);
                } catch (...) {
                    config = legatus::daemon::WorkerConfig::default_for_system();
                }
            } else {
                config = legatus::daemon::WorkerConfig::default_for_system();
            }
            config.server.endpoint = server_url_arg;
            config.save_to_file(cfg);
            std::cout << "[INFO] Configured server endpoint: " << server_url_arg << "\n";
        }

        std::cout << "[INFO] Launching legatusd daemon with config: " << cfg << (daemon_flag ? " [detached daemon mode]" : "") << "\n";
        auto log_dir = legatus::common::PathResolver::get_log_dir();
        std::error_code ec;
        std::filesystem::create_directories(log_dir, ec);
        std::string log_file = (log_dir / "legatusd.log").string();

#if defined(_WIN32)
        std::string cmd = daemon_flag
            ? ("start /B legatusd.exe --config \"" + cfg + "\" > \"" + log_file + "\" 2>&1")
            : ("start /B legatusd.exe --config \"" + cfg + "\"");
#else
        std::string cmd = daemon_flag
            ? ("nohup legatusd --config \"" + cfg + "\" > \"" + log_file + "\" 2>&1 &")
            : ("legatusd --config \"" + cfg + "\" >/dev/null 2>&1 &");
#endif
        int rc = std::system(cmd.c_str());
        if (rc == 0) {
            std::cout << "[OK] legatusd daemon launched in background.\n";
        } else {
            std::cerr << "[ERROR] Failed to start legatusd (system exit code: " << rc << ").\n";
        }
    });

    // 2. legatus down [-f, --force]
    auto* down_cmd = app.add_subcommand("down", "Gracefully terminate the active legatusd daemon");
    bool down_force = false;
    down_cmd->add_flag("-f,--force", down_force, "Force immediate daemon shutdown without waiting for tasks");
    down_cmd->callback([&]() {
        auto client = get_client();
        auto res = client.daemon_down(down_force);
        if (res.status == "SUCCESS") {
            int grace = res.data.value("grace_period_ms", 0);
            std::cout << "[OK] Sent shutdown signal to legatusd (grace period: " << grace << " ms).\n";
        } else {
            std::cerr << "[ERROR] " << res.error.code << ": " << res.error.message << "\n";
        }
    });

    // 3. legatus status [--json]
    auto* status_cmd = app.add_subcommand("status", "Query daemon health, dynamic configuration, and telemetry");
    bool status_json = false;
    status_cmd->add_flag("--json", status_json, "Format output as raw JSON");
    status_cmd->callback([&]() {
        auto client = get_client();
        auto res = client.daemon_status();
        if (res.status == "SUCCESS") {
            if (status_json) {
                std::cout << res.data.dump(2) << "\n";
            } else {
                std::vector<legatus::cli::TableFormatter::Row> rows = {
                    {"Friendly Name", res.data.value("friendly_name", "worker-node")},
                    {"Control Plane State", res.data.value("active", true) ? "ACTIVE (SCHEDULABLE)" : "INACTIVE (SUSPENDED)"},
                    {"Config Version", std::to_string(res.data.value("config_version", 1))},
                    {"Remaining Lease", std::to_string(res.data.value("remaining_lease_sec", 0ULL)) + "s"},
                    {"Version", res.data.value("version", "unknown")},
                    {"Uptime", std::to_string(res.data.value("uptime_seconds", 0LL)) + "s"},
                    {"Server Endpoint", res.data.value("server_endpoint", "unknown")},
                    {"LCTP Transport", res.data.value("websocket_connected", false) ? "CONNECTED" : "DISCONNECTED"},
                    {"Assigned Session", res.data.value("assigned_cluster", "none")},
                    {"Active Tasks", std::to_string(res.data.value("active_tasks_count", 0))},
                    {"Log Level", res.data.value("log_level", "info")},
                    {"Allocatable Cores", std::to_string(res.data.value("allocatable_cores", 0))},
                    {"Max Memory", std::to_string(res.data.value("max_memory_bytes", 0ULL) / (1024 * 1024)) + " MB"}
                };
                std::cout << legatus::cli::TableFormatter::format_key_value_table("Legatus Worker Status", rows);
            }
        } else {
            std::cerr << "[ERROR] " << res.error.code << ": " << res.error.message << "\n";
        }
    });

    CLI11_PARSE(app, argc, argv);
    return 0;
}
