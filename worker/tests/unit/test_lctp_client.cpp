#include "legatus/daemon/lctp_client.hpp"
#include "legatus/daemon/process_supervisor.hpp"
#include "legatus/common/base64.hpp"
#include "legatus/common/crypto_utils.hpp"
#include <ixwebsocket/IXWebSocketServer.h>
#include <solix/compilation.hpp>
#include <catch2/catch_all.hpp>
#include <atomic>
#include <mutex>
#include <vector>

#ifndef LEGATUS_RUNNER_PATH
#define LEGATUS_RUNNER_PATH "./legatus-runner"
#endif

static std::vector<std::uint8_t> compile_solix_to_bytecode(const std::string& source) {
    solix::CompilationOptions copts;
    copts.log_level = solix::CompilationOptions::LogLevel::OFF;
    copts.sources[solix::Source(std::string("main.slx"))] = source;
    return solix::run(copts);
}

TEST_CASE("LCTP Framing Specification", "[worker][daemon][lctp]") {
    SECTION("LctpFrame::create populates UUIDv4 and monotonic timestamp") {
        nlohmann::json payload = {{"foo", "bar"}, {"count", 42}};
        auto frame = legatus::daemon::LctpFrame::create("TEST_FRAME", payload);

        REQUIRE(frame.type == "TEST_FRAME");
        REQUIRE(frame.id.length() == 36);
        REQUIRE(frame.id[8] == '-');
        REQUIRE(frame.id[13] == '-');
        REQUIRE(frame.id[18] == '-');
        REQUIRE(frame.id[23] == '-');
        REQUIRE(frame.timestamp > 0);
        REQUIRE(frame.payload["foo"] == "bar");
        REQUIRE(frame.payload["count"] == 42);

        // Roundtrip serialization
        std::string json_str = frame.serialize();
        auto parsed = legatus::daemon::LctpFrame::deserialize(json_str);
        REQUIRE(parsed.type == "TEST_FRAME");
        REQUIRE(parsed.id == frame.id);
        REQUIRE(parsed.timestamp == frame.timestamp);
        REQUIRE(parsed.payload == payload);
    }
}

TEST_CASE("LCTP Client - In-Process Mock Broker Full Lifecycle", "[worker][daemon][lctp]") {
    const int mock_port = 19185;
    ix::WebSocketServer server(mock_port, "127.0.0.1");

    std::atomic<bool> session_init_received{false};
    std::atomic<bool> heartbeat_received{false};
    std::atomic<bool> drain_notice_received{false};
    std::atomic<bool> task_result_received{false};
    std::atomic<bool> task_log_chunk_received{false};
    std::string task_result_status;

    std::mutex server_ws_mutex;
    ix::WebSocket* active_client_ws = nullptr;

    server.setOnClientMessageCallback([&](std::shared_ptr<ix::ConnectionState>, ix::WebSocket& ws, const ix::WebSocketMessagePtr& msg) {
        if (msg->type == ix::WebSocketMessageType::Open) {
            std::lock_guard<std::mutex> lock(server_ws_mutex);
            active_client_ws = &ws;
        } else if (msg->type == ix::WebSocketMessageType::Close) {
            std::lock_guard<std::mutex> lock(server_ws_mutex);
            if (active_client_ws == &ws) active_client_ws = nullptr;
        } else if (msg->type == ix::WebSocketMessageType::Message) {
            try {
                auto frame = legatus::daemon::LctpFrame::deserialize(msg->str);
                if (frame.type == "SESSION_INIT") {
                    session_init_received = true;
                    // Respond with SESSION_ACK (fast heartbeat for test)
                    nlohmann::json ack_payload = {
                        {"session_id", "sess-mock-integration-01"},
                        {"status", "ACCEPTED"},
                        {"assigned_cluster", "test-cluster"},
                        {"heartbeat_interval_ms", 100},
                        {"heartbeat_timeout_ms", 3000},
                        {"server_time_utc", 1728907200000LL}
                    };
                    auto ack = legatus::daemon::LctpFrame::create("SESSION_ACK", ack_payload);
                    ws.sendText(ack.serialize());
                } else if (frame.type == "HEARTBEAT") {
                    heartbeat_received = true;
                    nlohmann::json ack_p = {
                        {"session_id", "sess-mock-integration-01"},
                        {"server_time_utc", 1728907200000LL}
                    };
                    auto ack = legatus::daemon::LctpFrame::create("HEARTBEAT_ACK", ack_p);
                    ws.sendText(ack.serialize());
                } else if (frame.type == "DRAIN_NOTICE") {
                    drain_notice_received = true;
                } else if (frame.type == "TASK_LOG_CHUNK") {
                    task_log_chunk_received = true;
                } else if (frame.type == "TASK_RESULT") {
                    task_result_received = true;
                    task_result_status = frame.payload.value("status", "");
                }
            } catch (...) {}
        }
    });

    REQUIRE(server.listenAndStart());

    auto supervisor = std::make_shared<legatus::daemon::ProcessSupervisor>(LEGATUS_RUNNER_PATH);

    legatus::daemon::WorkerConfig config;
    config.server.endpoint = "http://127.0.0.1:" + std::to_string(mock_port);
    config.server.tls_verify_peer = false;
    config.identity.worker_id = "test-lctp-worker";

    legatus::daemon::LctpClient client(config, supervisor);
    client.set_custom_ws_url("ws://127.0.0.1:" + std::to_string(mock_port));

    client.start();

    // Wait for SESSION_ACK and OPERATIONAL state
    int retries = 0;
    while (client.state() != legatus::daemon::LctpState::OPERATIONAL && retries++ < 100) {
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
    }

    REQUIRE(session_init_received);
    REQUIRE(client.state() == legatus::daemon::LctpState::OPERATIONAL);
    REQUIRE(client.session_id() == "sess-mock-integration-01");

    // 1. Heartbeat cycle emits frames and handles HEARTBEAT_ACK
    retries = 0;
    while (!heartbeat_received && retries++ < 100) {
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
    }
    REQUIRE(heartbeat_received);

    // 2. Expedited DRAIN_NOTICE is sent immediately
    REQUIRE(client.send_drain_notice("OPERATOR_TEST_DRAIN"));
    retries = 0;
    while (!drain_notice_received && retries++ < 50) {
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
    }
    REQUIRE(drain_notice_received);
    REQUIRE(client.is_draining());

    // 3. DISPATCH_TASK launches child process and returns TASK_RESULT
    const std::string solix_src = R"(
        class Console {
            public static native void native_print_string(char[] str);
        }

        public class Main {
            public static int32 main(char[][] args) {
                Console.native_print_string("Task log chunk test output\n");
                return 0;
            }
        }
    )";
    auto bytecode = compile_solix_to_bytecode(solix_src);
    REQUIRE(!bytecode.empty());

    std::string test_sha256 = legatus::common::sha256_hex(bytecode.data(), bytecode.size());
    nlohmann::json dispatch_payload = {
        {"task_id", "task-ws-integration-99"},
        {"entry_point", "main"},
        {"bytecode", {
            {"format", "solix_bc_v1"},
            {"encoding", "base64"},
            {"sha256", test_sha256},
            {"payload", legatus::common::base64_encode(bytecode)}
        }},
        {"constraints", {
            {"instruction_budget", 100000},
            {"max_memory_bytes", 64 * 1024 * 1024},
            {"timeout_ms", 10000}
        }},
        {"arguments", "{}"},
        {"secrets", {}}
    };

    auto dispatch_frame = legatus::daemon::LctpFrame::create("DISPATCH_TASK", dispatch_payload);

    {
        std::lock_guard<std::mutex> lock(server_ws_mutex);
        REQUIRE(active_client_ws != nullptr);
        active_client_ws->sendText(dispatch_frame.serialize());
    }

    retries = 0;
    while (!task_result_received && retries++ < 100) {
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
    }

    REQUIRE(task_result_received);
    REQUIRE(task_result_status == "COMPLETED");
    REQUIRE(task_log_chunk_received);

    // 4. Bytecode deduplication dispatch (no payload, only sha256)
    task_result_received = false;
    task_result_status = "";
    nlohmann::json dedup_dispatch_payload = {
        {"task_id", "task-ws-dedup-100"},
        {"entry_point", "main"},
        {"bytecode", {
            {"format", "solix_bc_v1"},
            {"encoding", "base64"},
            {"sha256", test_sha256},
            {"payload", nullptr}
        }},
        {"constraints", {
            {"instruction_budget", 100000},
            {"max_memory_bytes", 64 * 1024 * 1024},
            {"timeout_ms", 10000}
        }},
        {"arguments", "{}"},
        {"secrets", {}}
    };
    auto dedup_frame = legatus::daemon::LctpFrame::create("DISPATCH_TASK", dedup_dispatch_payload);

    {
        std::lock_guard<std::mutex> lock(server_ws_mutex);
        REQUIRE(active_client_ws != nullptr);
        active_client_ws->sendText(dedup_frame.serialize());
    }

    retries = 0;
    while (!task_result_received && retries++ < 100) {
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
    }

    REQUIRE(task_result_received);
    REQUIRE(task_result_status == "COMPLETED");

    client.stop();
    REQUIRE(client.state() == legatus::daemon::LctpState::DISCONNECTED);
    server.stop();
}
