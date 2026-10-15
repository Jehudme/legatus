#include "legatus/daemon/ipc_bus.hpp"
#include <catch2/catch_all.hpp>
#include <chrono>

namespace {

class MockIpcHandler : public legatus::daemon::IIpcHandler {
public:
    std::string current_log_level = "info";
    bool draining = false;
    bool terminating = false;
    std::string last_aborted_task;

    legatus::daemon::IpcResponse handle_daemon_status() override {
        nlohmann::json d = {
            {"version", "1.0.0-worker-mvp"},
            {"uptime_seconds", 3600},
            {"server_endpoint", "https://cloud.legatus.dev:8443"},
            {"websocket_connected", true},
            {"assigned_cluster", "cluster-west-1"},
            {"draining", draining},
            {"active_tasks_count", 2},
            {"log_level", current_log_level},
            {"allocatable_cores", 8},
            {"max_memory_bytes", 8589934592ULL}
        };
        return legatus::daemon::IpcResponse::success(d);
    }

    legatus::daemon::IpcResponse handle_daemon_down(bool force) override {
        terminating = true;
        nlohmann::json d = {
            {"terminating", true},
            {"grace_period_ms", force ? 0 : 5000}
        };
        return legatus::daemon::IpcResponse::success(d);
    }

    legatus::daemon::IpcResponse handle_daemon_drain(bool enabled, int32_t timeout_seconds) override {
        (void)timeout_seconds;
        draining = enabled;
        nlohmann::json d = {
            {"draining", draining},
            {"active_tasks_remaining", 2}
        };
        return legatus::daemon::IpcResponse::success(d);
    }

    legatus::daemon::IpcResponse handle_set_log_level(const std::string& level) override {
        std::string prev = current_log_level;
        current_log_level = level;
        nlohmann::json d = {
            {"previous_level", prev},
            {"active_level", current_log_level}
        };
        return legatus::daemon::IpcResponse::success(d);
    }

    legatus::daemon::IpcResponse handle_get_log_config() override {
        nlohmann::json d = {
            {"active_level", current_log_level},
            {"log_file_path", "/var/log/legatus/legatusd.log"},
            {"max_file_size_mb", 50},
            {"max_backup_files", 5}
        };
        return legatus::daemon::IpcResponse::success(d);
    }

    legatus::daemon::IpcResponse handle_abort_task(const std::string& task_id, const std::string& reason) override {
        (void)reason;
        last_aborted_task = task_id;
        nlohmann::json d = {
            {"task_id", task_id},
            {"escalation_state", "SIGTERM_SENT"}
        };
        return legatus::daemon::IpcResponse::success(d);
    }
};

} // namespace

TEST_CASE("IPC Envelope Serialization", "[worker][daemon][ipc]") {
    SECTION("Request serialization and deserialization") {
        legatus::daemon::IpcRequest req;
        req.action = "SET_LOG_LEVEL";
        req.params = {{"level", "debug"}};

        std::string json_str = req.serialize();
        auto parsed = legatus::daemon::IpcRequest::deserialize(json_str);

        REQUIRE(parsed.action == "SET_LOG_LEVEL");
        REQUIRE(parsed.params["level"] == "debug");
    }

    SECTION("Response success and error serialization") {
        auto succ = legatus::daemon::IpcResponse::success({{"active", true}});
        std::string s_succ = succ.serialize();
        auto p_succ = legatus::daemon::IpcResponse::deserialize(s_succ);
        REQUIRE(p_succ.status == "SUCCESS");
        REQUIRE(p_succ.data["active"] == true);

        auto fail = legatus::daemon::IpcResponse::failure("ERR_CODE", "Something broke");
        std::string s_fail = fail.serialize();
        auto p_fail = legatus::daemon::IpcResponse::deserialize(s_fail);
        REQUIRE(p_fail.status == "ERROR");
        REQUIRE(p_fail.error.code == "ERR_CODE");
        REQUIRE(p_fail.error.message == "Something broke");
    }
}

TEST_CASE("IPC Bus - REQ0/REP0 Actions Over Local Transport", "[worker][daemon][ipc]") {
    std::string test_endpoint = "ipc:///tmp/legatus_ipc_test_" +
        std::to_string(std::chrono::steady_clock::now().time_since_epoch().count()) + ".ipc";

    auto handler = std::make_shared<MockIpcHandler>();
    legatus::daemon::IpcServer server(test_endpoint);
    server.set_handler(handler);

    REQUIRE(server.start());
    REQUIRE(server.is_running());

    legatus::daemon::IpcClient client(test_endpoint);
    REQUIRE(client.connect());

    SECTION("Action 1: DAEMON_STATUS returns valid cluster & state metrics") {
        auto res = client.daemon_status();
        REQUIRE(res.status == "SUCCESS");
        REQUIRE(res.data["version"] == "1.0.0-worker-mvp");
        REQUIRE(res.data["websocket_connected"] == true);
        REQUIRE(res.data["assigned_cluster"] == "cluster-west-1");
        REQUIRE(res.data["allocatable_cores"] == 8);
    }

    SECTION("Action 2: DAEMON_DRAIN adjusts drain mode") {
        auto res = client.daemon_drain(true, 30);
        REQUIRE(res.status == "SUCCESS");
        REQUIRE(res.data["draining"] == true);
        REQUIRE(handler->draining == true);
    }

    SECTION("Action 3: SET_LOG_LEVEL and GET_LOG_CONFIG mutate and read log configuration") {
        auto set_res = client.set_log_level("trace");
        REQUIRE(set_res.status == "SUCCESS");
        REQUIRE(set_res.data["previous_level"] == "info");
        REQUIRE(set_res.data["active_level"] == "trace");
        REQUIRE(handler->current_log_level == "trace");

        auto cfg_res = client.get_log_config();
        REQUIRE(cfg_res.status == "SUCCESS");
        REQUIRE(cfg_res.data["active_level"] == "trace");
        REQUIRE(cfg_res.data["max_file_size_mb"] == 50);
    }

    SECTION("Action 4: ABORT_TASK initiates task termination") {
        auto res = client.abort_task("task-uuid-42", "Manual stop");
        REQUIRE(res.status == "SUCCESS");
        REQUIRE(res.data["task_id"] == "task-uuid-42");
        REQUIRE(res.data["escalation_state"] == "SIGTERM_SENT");
        REQUIRE(handler->last_aborted_task == "task-uuid-42");
    }

    SECTION("Action 5: DAEMON_DOWN triggers termination response") {
        auto res = client.daemon_down(false);
        REQUIRE(res.status == "SUCCESS");
        REQUIRE(res.data["terminating"] == true);
        REQUIRE(res.data["grace_period_ms"] == 5000);
        REQUIRE(handler->terminating == true);
    }

    SECTION("Unknown action returns ERR_UNKNOWN_ACTION error envelope") {
        legatus::daemon::IpcRequest req;
        req.action = "NON_EXISTENT_ACTION";
        auto res = client.send_request(req);
        REQUIRE(res.status == "ERROR");
        REQUIRE(res.error.code == "ERR_UNKNOWN_ACTION");
    }

    client.disconnect();
    server.stop();
    REQUIRE(!server.is_running());
}
