#include "legatus/version.hpp"
#include "legatus/daemon/worker_config.hpp"
#include "legatus/daemon/process_supervisor.hpp"
#include "legatus/daemon/enrollment_client.hpp"
#include "legatus/daemon/ipc_bus.hpp"
#include "legatus/daemon/lctp_client.hpp"
#include "legatus/common/paths.hpp"
#include "legatus/common/logging.hpp"
#include <CLI/CLI.hpp>
#include <spdlog/spdlog.h>
#include <csignal>
#include <atomic>
#include <chrono>
#include <iostream>

#ifndef LEGATUS_RUNNER_PATH
#define LEGATUS_RUNNER_PATH "./legatus-runner"
#endif

namespace {

std::atomic<bool> g_shutdown_requested{false};

void signal_handler(int sig) {
    (void)sig;
    g_shutdown_requested = true;
}

class DaemonIpcHandler : public legatus::daemon::IIpcHandler {
public:
    DaemonIpcHandler(
        legatus::daemon::WorkerConfig& config,
        std::shared_ptr<legatus::daemon::ProcessSupervisor> supervisor,
        std::shared_ptr<legatus::daemon::LctpClient> lctp_client,
        std::chrono::steady_clock::time_point start_time)
        : config_(config),
          supervisor_(std::move(supervisor)),
          lctp_client_(std::move(lctp_client)),
          start_time_(start_time) {}

    legatus::daemon::IpcResponse handle_daemon_status() override {
        auto now = std::chrono::steady_clock::now();
        int64_t uptime_sec = std::chrono::duration_cast<std::chrono::seconds>(now - start_time_).count();

        nlohmann::json d = {
            {"version", legatus::VERSION},
            {"uptime_seconds", uptime_sec},
            {"server_endpoint", config_.server.endpoint},
            {"websocket_connected", lctp_client_ ? lctp_client_->is_connected() : false},
            {"assigned_cluster", lctp_client_ ? lctp_client_->session_id() : "none"},
            {"draining", lctp_client_ ? lctp_client_->is_draining() : false},
            {"active_tasks_count", supervisor_ ? static_cast<int>(supervisor_->active_task_count()) : 0},
            {"log_level", legatus::common::get_active_log_level_string()},
            {"allocatable_cores", config_.resources.allocatable_cores},
            {"max_memory_bytes", config_.resources.max_memory_bytes}
        };
        return legatus::daemon::IpcResponse::success(d);
    }

    legatus::daemon::IpcResponse handle_daemon_down(bool force) override {
        spdlog::info("Received DAEMON_DOWN request (force={})", force);
        g_shutdown_requested = true;
        nlohmann::json d = {
            {"terminating", true},
            {"grace_period_ms", force ? 0 : 5000}
        };
        return legatus::daemon::IpcResponse::success(d);
    }

    legatus::daemon::IpcResponse handle_daemon_drain(bool enabled, int32_t timeout_seconds) override {
        (void)timeout_seconds;
        spdlog::info("Received DAEMON_DRAIN request (enabled={})", enabled);
        if (lctp_client_) {
            lctp_client_->set_draining(enabled);
            if (enabled) {
                lctp_client_->send_drain_notice("OPERATOR_CLI_DRAIN");
            }
        }
        size_t remaining = supervisor_ ? supervisor_->active_task_count() : 0;
        nlohmann::json d = {
            {"draining", enabled},
            {"active_tasks_remaining", static_cast<int>(remaining)}
        };
        return legatus::daemon::IpcResponse::success(d);
    }

    legatus::daemon::IpcResponse handle_set_log_level(const std::string& level) override {
        std::string prev = legatus::common::get_active_log_level_string();
        if (legatus::common::set_log_level_string(level)) {
            spdlog::info("Log level updated: {} -> {}", prev, level);
            nlohmann::json d = {
                {"previous_level", prev},
                {"active_level", level}
            };
            return legatus::daemon::IpcResponse::success(d);
        }
        return legatus::daemon::IpcResponse::failure("ERR_INVALID_LOG_LEVEL", "Invalid log level: " + level);
    }

    legatus::daemon::IpcResponse handle_get_log_config() override {
        nlohmann::json d = {
            {"active_level", legatus::common::get_active_log_level_string()},
            {"log_file_path", legatus::common::get_active_log_file_path()},
            {"max_file_size_mb", config_.logging.max_file_size_mb},
            {"max_backup_files", config_.logging.max_backup_files}
        };
        return legatus::daemon::IpcResponse::success(d);
    }

    legatus::daemon::IpcResponse handle_abort_task(const std::string& task_id, const std::string& reason) override {
        spdlog::warn("Received ABORT_TASK for {} (reason: {})", task_id, reason);
        bool found = false;
        if (supervisor_) {
            found = supervisor_->abort_task(task_id);
        }
        nlohmann::json d = {
            {"task_id", task_id},
            {"escalation_state", found ? "SIGTERM_SENT" : "NOT_FOUND"}
        };
        return legatus::daemon::IpcResponse::success(d);
    }

private:
    legatus::daemon::WorkerConfig& config_;
    std::shared_ptr<legatus::daemon::ProcessSupervisor> supervisor_;
    std::shared_ptr<legatus::daemon::LctpClient> lctp_client_;
    std::chrono::steady_clock::time_point start_time_;
};

} // namespace

int main(int argc, char* argv[]) {
    CLI::App app{"Legatus Worker Subsystem Daemon (legatusd)"};
    app.set_version_flag("--version,-v", std::string("legatusd v") + std::string(legatus::VERSION));

    std::string config_path;
    app.add_option("-c,--config", config_path, "Path to worker.toml configuration file");

    bool enroll_only = false;
    app.add_flag("--enroll", enroll_only, "Perform node quarantine enrollment and exit");

    CLI11_PARSE(app, argc, argv);

    auto start_time = std::chrono::steady_clock::now();

    // 1. Resolve configuration
    if (config_path.empty()) {
        config_path = legatus::common::PathResolver::get_config_path().string();
    }

    legatus::daemon::WorkerConfig config;
    if (std::filesystem::exists(config_path)) {
        try {
            config = legatus::daemon::WorkerConfig::load_from_file(config_path);
        } catch (const std::exception& e) {
            std::cerr << "[WARN] Could not load config from " << config_path << ": " << e.what()
                      << ". Using default configuration.\n";
            config = legatus::daemon::WorkerConfig::default_for_system();
        }
    } else {
        config = legatus::daemon::WorkerConfig::default_for_system();
        config.save_to_file(config_path);
    }

    // 2. Initialize Logging
    legatus::common::init_logging(config.logging);
    spdlog::info("Starting legatusd v{} (Worker ID: {})", legatus::VERSION, config.identity.worker_id);
    spdlog::info("Config loaded from: {}", config_path);

    // 3. Quarantine Enrollment if needed
    if (config.identity.secret_token.empty() || enroll_only) {
        spdlog::info("Worker is not enrolled or --enroll specified. Starting quarantine attestation...");
        legatus::daemon::EnrollmentClient enroll_client(config);
        auto enroll_res = enroll_client.enroll_and_persist(config_path, std::chrono::milliseconds(900000), [](const auto& step) {
            if (step.status == legatus::daemon::EnrollmentStatus::PENDING_APPROVAL) {
                spdlog::info("Node quarantined. Fingerprint: {} ({}) - Waiting for operator approval in Studio...",
                             step.fingerprint_hex, step.visual_code);
            }
        });

        if (enroll_res.status != legatus::daemon::EnrollmentStatus::APPROVED) {
            spdlog::error("Quarantine enrollment failed: {}", enroll_res.error_message);
            return 1;
        }

        spdlog::info("Node successfully approved! Secret token persisted to {}", config_path);
        if (enroll_only) {
            return 0;
        }
    }

    // 4. Setup signal handling
    std::signal(SIGINT, signal_handler);
    std::signal(SIGTERM, signal_handler);

    // 5. Initialize Subsystems
    auto supervisor = std::make_shared<legatus::daemon::ProcessSupervisor>(LEGATUS_RUNNER_PATH);
    auto lctp_client = std::make_shared<legatus::daemon::LctpClient>(config, supervisor);

    std::string ipc_endpoint = legatus::common::PathResolver::get_default_ipc_path().string();
    auto ipc_server = std::make_unique<legatus::daemon::IpcServer>(ipc_endpoint);
    auto ipc_handler = std::make_shared<DaemonIpcHandler>(config, supervisor, lctp_client, start_time);
    ipc_server->set_handler(ipc_handler);

    if (!ipc_server->start()) {
        spdlog::warn("Could not bind local IPC bus to {}. Proceeding without local IPC.", ipc_endpoint);
    } else {
        spdlog::info("Local IPC bus listening on {}", ipc_endpoint);
    }

    // 6. Start outbound LCTP client
    spdlog::info("Connecting outbound to Legatus Server: {}", config.server.endpoint);
    lctp_client->start();

    // 7. Daemon event loop
    while (!g_shutdown_requested.load()) {
        std::this_thread::sleep_for(std::chrono::milliseconds(250));
    }

    spdlog::info("Shutting down legatusd gracefully...");
    lctp_client->stop();
    ipc_server->stop();
    spdlog::info("legatusd stopped cleanly.");
    return 0;
}
