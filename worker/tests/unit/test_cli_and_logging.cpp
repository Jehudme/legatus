#include <catch2/catch_test_macros.hpp>
#include "legatus/cli/table_formatter.hpp"
#include "legatus/common/logging.hpp"
#include "legatus/daemon/ipc_bus.hpp"
#include "legatus/common/paths.hpp"
#include <spdlog/spdlog.h>
#include <filesystem>
#include <fstream>
#include <thread>
#include <chrono>

TEST_CASE("TableFormatter renders clean ANSI tables", "[cli][table]") {
    SECTION("Empty table renders header and border") {
        std::vector<legatus::cli::TableFormatter::Row> rows;
        std::string rendered = legatus::cli::TableFormatter::format_key_value_table("DAEMON STATUS", rows);
        REQUIRE(rendered.find("DAEMON STATUS") != std::string::npos);
        REQUIRE(rendered.find("+---") != std::string::npos);
    }

    SECTION("Table adjusts column widths dynamically based on cell content") {
        std::vector<legatus::cli::TableFormatter::Row> rows = {
            {"State", "OPERATIONAL"},
            {"Uptime", "3600 seconds"},
            {"Active Long Task Identifier", "task-uuid-very-long-identifier-12345"}
        };
        std::string rendered = legatus::cli::TableFormatter::format_key_value_table("STATUS", rows);
        REQUIRE(rendered.find("STATUS") != std::string::npos);
        REQUIRE(rendered.find("OPERATIONAL") != std::string::npos);
        REQUIRE(rendered.find("3600 seconds") != std::string::npos);
        REQUIRE(rendered.find("Active Long Task Identifier") != std::string::npos);
        REQUIRE(rendered.find("task-uuid-very-long-identifier-12345") != std::string::npos);
    }
}

TEST_CASE("Logging subsystem rotating sinks and level switching", "[common][logging]") {
    namespace fs = std::filesystem;
    auto test_log_dir = fs::temp_directory_path() / ("legatus_log_test_" + std::to_string(std::chrono::system_clock::now().time_since_epoch().count()));
    fs::create_directories(test_log_dir);

    legatus::daemon::LoggingConfig cfg;
    cfg.level = "info";
    cfg.log_directory = test_log_dir.string();
    cfg.max_file_size_mb = 1;
    cfg.max_backup_files = 3;

    SECTION("Init rotating sink creates log directory and logger") {
        legatus::common::init_logging(cfg);

        REQUIRE(legatus::common::get_active_log_level_string() == "info");
        REQUIRE(legatus::common::get_active_log_file_path() == (test_log_dir / "legatusd.log").string());

        spdlog::info("Test info log message 12345");
        spdlog::default_logger()->flush();

        REQUIRE(fs::exists(test_log_dir / "legatusd.log"));

        // Read log file content
        std::ifstream ifs(test_log_dir / "legatusd.log");
        std::string content((std::istreambuf_iterator<char>(ifs)), std::istreambuf_iterator<char>());
        REQUIRE(content.find("Test info log message 12345") != std::string::npos);
    }

    SECTION("Dynamic log level switching") {
        legatus::common::init_logging(cfg);

        REQUIRE(legatus::common::set_log_level_string("debug"));
        REQUIRE(legatus::common::get_active_log_level_string() == "debug");

        REQUIRE(legatus::common::set_log_level_string("warn"));
        REQUIRE(legatus::common::get_active_log_level_string() == "warn");

        REQUIRE(legatus::common::set_log_level_string("error"));
        REQUIRE(legatus::common::get_active_log_level_string() == "error");

        REQUIRE(legatus::common::set_log_level_string("trace"));
        REQUIRE(legatus::common::get_active_log_level_string() == "trace");

        REQUIRE(legatus::common::set_log_level_string("off"));
        REQUIRE(legatus::common::get_active_log_level_string() == "off");

        // Invalid level string returns false and retains previous level
        REQUIRE_FALSE(legatus::common::set_log_level_string("nonexistent_level"));
        REQUIRE(legatus::common::get_active_log_level_string() == "off");
    }

    // Cleanup
    std::error_code ec;
    fs::remove_all(test_log_dir, ec);
}

TEST_CASE("Operator CLI commands interact with IPC daemon", "[cli][ipc]") {
    using namespace legatus::daemon;
    std::string test_ep = "inproc://cli_test_bus_" + std::to_string(std::chrono::system_clock::now().time_since_epoch().count());

    class MockCliHandler : public IIpcHandler {
    public:
        std::atomic<bool> drain_status{false};
        std::atomic<bool> down_received{false};
        std::string log_level{"info"};

        IpcResponse handle_daemon_status() override {
            nlohmann::json d = {
                {"state", "OPERATIONAL"},
                {"draining", drain_status.load()},
                {"version", "1.0.0-worker-mvp"},
                {"active_tasks", 1},
                {"uptime_seconds", 42},
                {"cpu_percent", 12.5},
                {"memory_rss_bytes", 104857600},
                {"tasks", nlohmann::json::array({
                    {
                        {"task_id", "task-abc"},
                        {"status", "EXECUTING"},
                        {"function", "solix_calc"},
                        {"rss_bytes", 52428800},
                        {"pid", 12345}
                    }
                })}
            };
            return IpcResponse::success(d);
        }

        IpcResponse handle_daemon_down(bool force) override {
            (void)force;
            down_received = true;
            return IpcResponse::success({{"shutting_down", true}});
        }

        IpcResponse handle_daemon_drain(bool enabled, int32_t timeout_seconds) override {
            (void)timeout_seconds;
            drain_status = enabled;
            return IpcResponse::success({{"draining", drain_status.load()}});
        }

        IpcResponse handle_set_log_level(const std::string& level) override {
            log_level = level;
            return IpcResponse::success({{"level", log_level}});
        }

        IpcResponse handle_get_log_config() override {
            return IpcResponse::success({
                {"level", log_level},
                {"log_file", "/tmp/legatusd.log"},
                {"max_size_mb", 50},
                {"max_files", 5}
            });
        }

        IpcResponse handle_abort_task(const std::string& task_id, const std::string& reason) override {
            (void)reason;
            return IpcResponse::success({
                {"task_id", task_id},
                {"aborted", true}
            });
        }
    };

    auto handler = std::make_shared<MockCliHandler>();
    IpcServer server(test_ep);
    server.set_handler(handler);
    REQUIRE(server.start());

    // Give server thread a few ms to bind
    std::this_thread::sleep_for(std::chrono::milliseconds(20));

    IpcClient client(test_ep);

    SECTION("Query status returns structured telemetry") {
        auto resp = client.daemon_status();
        REQUIRE(resp.status == "SUCCESS");
        REQUIRE(resp.data["state"] == "OPERATIONAL");
        REQUIRE(resp.data["uptime_seconds"] == 42);
        REQUIRE(resp.data["tasks"].is_array());
        REQUIRE(resp.data["tasks"].size() == 1);
        REQUIRE(resp.data["tasks"][0]["task_id"] == "task-abc");
    }

    SECTION("Toggle drain mode") {
        auto resp1 = client.daemon_drain(true, 0);
        REQUIRE(resp1.status == "SUCCESS");
        REQUIRE(resp1.data["draining"] == true);
        REQUIRE(handler->drain_status.load() == true);

        auto resp2 = client.daemon_drain(false, 0);
        REQUIRE(resp2.status == "SUCCESS");
        REQUIRE(resp2.data["draining"] == false);
        REQUIRE(handler->drain_status.load() == false);
    }

    SECTION("Log level query and update") {
        auto resp1 = client.set_log_level("trace");
        REQUIRE(resp1.status == "SUCCESS");
        REQUIRE(resp1.data["level"] == "trace");
        REQUIRE(handler->log_level == "trace");

        auto resp2 = client.get_log_config();
        REQUIRE(resp2.status == "SUCCESS");
        REQUIRE(resp2.data["level"] == "trace");
        REQUIRE(resp2.data["max_files"] == 5);
    }

    SECTION("Abort active task") {
        auto resp = client.abort_task("task-abc", "Operator CLI abort");
        REQUIRE(resp.status == "SUCCESS");
        REQUIRE(resp.data["aborted"] == true);
        REQUIRE(resp.data["task_id"] == "task-abc");
    }

    SECTION("Daemon shutdown request") {
        auto resp = client.daemon_down(false);
        REQUIRE(resp.status == "SUCCESS");
        REQUIRE(resp.data["shutting_down"] == true);
        REQUIRE(handler->down_received.load() == true);
    }

    server.stop();
}
