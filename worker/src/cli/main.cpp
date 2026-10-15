#include "legatus/version.hpp"
#include "legatus/daemon/ipc_bus.hpp"
#include "legatus/daemon/worker_config.hpp"
#include "legatus/common/paths.hpp"
#include "legatus/cli/table_formatter.hpp"
#include <CLI/CLI.hpp>
#include <iostream>
#include <fstream>
#include <deque>

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

    // 1. legatus down
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

    // 2. legatus drain
    auto* drain_cmd = app.add_subcommand("drain", "Toggle compute node drain mode");
    int32_t drain_timeout = 0;
    bool drain_disable = false;
    drain_cmd->add_option("--timeout", drain_timeout, "Timeout in seconds before forcing task termination");
    drain_cmd->add_flag("--disable", drain_disable, "Disable drain mode and resume task scheduling");
    drain_cmd->callback([&]() {
        auto client = get_client();
        bool enable = !drain_disable;
        auto res = client.daemon_drain(enable, drain_timeout);
        if (res.status == "SUCCESS") {
            int remaining = res.data.value("active_tasks_remaining", 0);
            std::cout << "[OK] Node drain mode " << (enable ? "ENABLED" : "DISABLED")
                      << ". Active tasks remaining: " << remaining << "\n";
        } else {
            std::cerr << "[ERROR] " << res.error.code << ": " << res.error.message << "\n";
        }
    });

    // 3. legatus status
    auto* status_cmd = app.add_subcommand("status", "Query daemon health and telemetry");
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
                    {"Version", res.data.value("version", "unknown")},
                    {"Uptime", std::to_string(res.data.value("uptime_seconds", 0LL)) + "s"},
                    {"Server Endpoint", res.data.value("server_endpoint", "unknown")},
                    {"LCTP Transport", res.data.value("websocket_connected", false) ? "CONNECTED" : "DISCONNECTED"},
                    {"Assigned Cluster", res.data.value("assigned_cluster", "none")},
                    {"Draining", res.data.value("draining", false) ? "YES" : "NO"},
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

    // 4. legatus log
    auto* log_cmd = app.add_subcommand("log", "Daemon logging control");
    auto* log_level_cmd = log_cmd->add_subcommand("level", "Set logging filter level");
    std::string target_level;
    log_level_cmd->add_option("level", target_level, "Log level (trace, debug, info, warn, error)")->required();
    log_level_cmd->callback([&]() {
        auto client = get_client();
        auto res = client.set_log_level(target_level);
        if (res.status == "SUCCESS") {
            std::cout << "[OK] Log level updated: "
                      << res.data.value("previous_level", "") << " -> "
                      << res.data.value("active_level", "") << "\n";
        } else {
            std::cerr << "[ERROR] " << res.error.code << ": " << res.error.message << "\n";
        }
    });

    auto* log_show_cmd = log_cmd->add_subcommand("show", "Display recent daemon log output");
    size_t line_count = 50;
    log_show_cmd->add_option("--lines,-n", line_count, "Number of trailing lines to view (default: 50)");
    log_show_cmd->callback([&]() {
        auto client = get_client();
        auto res = client.get_log_config();
        if (res.status != "SUCCESS") {
            std::cerr << "[ERROR] Could not fetch log configuration: " << res.error.message << "\n";
            return;
        }

        std::string log_path = res.data.value("log_file_path", "");
        if (log_path.empty() || !std::filesystem::exists(log_path)) {
            std::cout << "[INFO] Log file not found at " << log_path << "\n";
            return;
        }

        std::ifstream ifs(log_path);
        std::deque<std::string> lines;
        std::string line;
        while (std::getline(ifs, line)) {
            lines.push_back(line);
            if (lines.size() > line_count) {
                lines.pop_front();
            }
        }

        for (const auto& l : lines) {
            std::cout << l << "\n";
        }
    });

    // 5. legatus tasks
    auto* tasks_cmd = app.add_subcommand("tasks", "Inspect and control task child processes");
    auto* tasks_list_cmd = tasks_cmd->add_subcommand("list", "List active tasks");
    bool tasks_json = false;
    tasks_list_cmd->add_flag("--json", tasks_json, "Format output as JSON");
    tasks_list_cmd->callback([&]() {
        auto client = get_client();
        auto res = client.daemon_status();
        if (res.status == "SUCCESS") {
            if (tasks_json) {
                std::cout << res.data.dump(2) << "\n";
            } else {
                int count = res.data.value("active_tasks_count", 0);
                std::cout << "Active Tasks Supervised: " << count << "\n";
            }
        } else {
            std::cerr << "[ERROR] " << res.error.code << ": " << res.error.message << "\n";
        }
    });

    auto* tasks_kill_cmd = tasks_cmd->add_subcommand("kill", "Terminate an executing task");
    std::string target_task_id;
    std::string kill_reason = "Manual operator kill";
    tasks_kill_cmd->add_option("task_id", target_task_id, "ID of the task to abort")->required();
    tasks_kill_cmd->add_option("--reason", kill_reason, "Cancellation reason string");
    tasks_kill_cmd->callback([&]() {
        auto client = get_client();
        auto res = client.abort_task(target_task_id, kill_reason);
        if (res.status == "SUCCESS") {
            std::cout << "[OK] Dispatched abort signal to task " << target_task_id << ".\n";
        } else {
            std::cerr << "[ERROR] " << res.error.code << ": " << res.error.message << "\n";
        }
    });

    CLI11_PARSE(app, argc, argv);
    return 0;
}
