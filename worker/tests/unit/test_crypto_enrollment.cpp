#include "legatus/common/crypto_utils.hpp"
#include "legatus/common/base64.hpp"
#include "legatus/daemon/worker_config.hpp"
#include "legatus/daemon/enrollment_client.hpp"
#include <ixwebsocket/IXHttpServer.h>
#include <catch2/catch_all.hpp>
#include <nlohmann/json.hpp>
#include <filesystem>
#include <atomic>

#if defined(_WIN32)
#include <ixwebsocket/IXNetSystem.h>
namespace {
struct NetSystemInit {
    NetSystemInit() { ix::initNetSystem(); }
    ~NetSystemInit() { ix::uninitNetSystem(); }
};
static NetSystemInit g_crypto_enrollment_winsock_init;
} // namespace
#endif

TEST_CASE("Ed25519 Key Generation & Visual Attestation", "[worker][crypto][ed25519]") {
    auto keypair = legatus::common::generate_ed25519_keypair();

    SECTION("Keypair contains valid 32-byte Base64 keys") {
        REQUIRE(!keypair.public_key_base64.empty());
        REQUIRE(!keypair.private_key_base64.empty());

        auto pub_bytes = legatus::common::base64_decode(keypair.public_key_base64);
        auto priv_bytes = legatus::common::base64_decode(keypair.private_key_base64);

        REQUIRE(pub_bytes.size() == 32);
        REQUIRE(priv_bytes.size() == 32);
    }

    SECTION("Fingerprint matches 8-byte uppercase colon format") {
        REQUIRE(keypair.fingerprint.length() == 23); // 16 hex chars + 7 colons
        for (std::size_t i = 0; i < keypair.fingerprint.length(); ++i) {
            if ((i + 1) % 3 == 0) {
                REQUIRE(keypair.fingerprint[i] == ':');
            } else {
                REQUIRE(std::isxdigit(keypair.fingerprint[i]));
                REQUIRE((std::isupper(keypair.fingerprint[i]) || std::isdigit(keypair.fingerprint[i])));
            }
        }
    }

    SECTION("Visual code matches Word-Word-NN format") {
        REQUIRE(!keypair.visual_code.empty());
        auto first_dash = keypair.visual_code.find('-');
        REQUIRE(first_dash != std::string::npos);
        auto second_dash = keypair.visual_code.find('-', first_dash + 1);
        REQUIRE(second_dash != std::string::npos);
    }

    SECTION("Deterministic visual code derivation") {
        // Test with known hash prefix
        std::string test_hash = "00010001deadbeef0000";
        std::string code = legatus::common::derive_visual_code(test_hash);
        REQUIRE(code == "Tiger-Galaxy-00");
    }
}

TEST_CASE("WorkerConfig - TOML Serialization and Atomic Persistence", "[worker][daemon][config]") {
    std::string sample_toml = R"(
[server]
endpoint = "https://custom.legatus.dev:9443"
tls_verify_peer = false
ca_cert_path = "/etc/ssl/custom.crt"

[identity]
worker_id = "test-node-01"
secret_token = "wk_sec_test_secret"
public_key = "dGVzdF9wdWJsaWNfa2V5"

[resources]
allocatable_cores = 8
max_memory_bytes = 8589934592
max_instruction_budget_per_task = 100000000

[telemetry]
heartbeat_interval_ms = 3000
heartbeat_timeout_ms = 9000

[logging]
level = "debug"
log_directory = "/tmp/test-logs"
max_file_size_mb = 25
max_backup_files = 3
)";

    auto config = legatus::daemon::WorkerConfig::parse_toml(sample_toml);
    REQUIRE(config.server.endpoint == "https://custom.legatus.dev:9443");
    REQUIRE(config.server.tls_verify_peer == false);
    REQUIRE(config.server.ca_cert_path == "/etc/ssl/custom.crt");
    REQUIRE(config.identity.worker_id == "test-node-01");
    REQUIRE(config.identity.secret_token == "wk_sec_test_secret");
    REQUIRE(config.identity.public_key == "dGVzdF9wdWJsaWNfa2V5");
    REQUIRE(config.resources.allocatable_cores == 8);
    REQUIRE(config.resources.max_memory_bytes == 8589934592ULL);
    REQUIRE(config.resources.max_instruction_budget_per_task == 100000000ULL);
    REQUIRE(config.telemetry.heartbeat_interval_ms == 3000);
    REQUIRE(config.telemetry.heartbeat_timeout_ms == 9000);
    REQUIRE(config.logging.level == "debug");
    REQUIRE(config.logging.log_directory == "/tmp/test-logs");
    REQUIRE(config.logging.max_file_size_mb == 25);
    REQUIRE(config.logging.max_backup_files == 3);

    SECTION("Roundtrip serialization preserves all fields") {
        std::string serialized = config.serialize_toml();
        auto reparsed = legatus::daemon::WorkerConfig::parse_toml(serialized);
        REQUIRE(reparsed.server.endpoint == config.server.endpoint);
        REQUIRE(reparsed.identity.worker_id == config.identity.worker_id);
        REQUIRE(reparsed.identity.secret_token == config.identity.secret_token);
        REQUIRE(reparsed.resources.allocatable_cores == config.resources.allocatable_cores);
        REQUIRE(reparsed.resources.max_memory_bytes == config.resources.max_memory_bytes);
    }

    SECTION("Atomic file save and load") {
        std::string temp_path = "/tmp/test_worker_atomic_" + std::to_string(std::chrono::steady_clock::now().time_since_epoch().count()) + ".toml";
        config.save_to_file(temp_path);
        REQUIRE(std::filesystem::exists(temp_path));

        auto loaded = legatus::daemon::WorkerConfig::load_from_file(temp_path);
        REQUIRE(loaded.identity.worker_id == "test-node-01");
        REQUIRE(loaded.identity.secret_token == "wk_sec_test_secret");

        std::filesystem::remove(temp_path);
    }

    SECTION("Default for system provides sane settings") {
        auto sys_cfg = legatus::daemon::WorkerConfig::default_for_system();
        REQUIRE(!sys_cfg.identity.worker_id.empty());
        REQUIRE(sys_cfg.resources.allocatable_cores > 0);
        REQUIRE(sys_cfg.resources.max_memory_bytes > 0);
    }
}

TEST_CASE("EnrollmentClient - In-Process Mock Broker Attestation Flow", "[worker][daemon][enrollment]") {
    const int mock_port = 19183;
    ix::HttpServer server(mock_port, "127.0.0.1");

    std::atomic<int> poll_count{0};
    std::string approved_token = "wk_sec_approved_abc_9999";
    std::string enrollment_id = "enr-test-1122-3344";

    server.setOnConnectionCallback([&](ix::HttpRequestPtr req, std::shared_ptr<ix::ConnectionState>) -> ix::HttpResponsePtr {
        auto resp = std::make_shared<ix::HttpResponse>();
        resp->headers["Content-Type"] = "application/json";

        if (req->method == "POST" && req->uri == "/api/v1/workers/register") {
            nlohmann::json res_j = {
                {"enrollment_id", enrollment_id},
                {"status", "PENDING_APPROVAL"},
                {"fingerprint_hex", "4E:8A:BC:31:09:FF:7D:12"},
                {"visual_code", "Tiger-Galaxy-77"},
                {"poll_interval_ms", 50},
                {"expires_at", 1728908100000ULL}
            };
            resp->statusCode = 202;
            resp->body = res_j.dump();
            return resp;
        }

        if (req->method == "GET" && req->uri.find("/api/v1/workers/register/" + enrollment_id + "/status") != std::string::npos) {
            int current_polls = ++poll_count;
            if (current_polls < 2) {
                nlohmann::json res_j = {
                    {"enrollment_id", enrollment_id},
                    {"status", "PENDING_APPROVAL"},
                    {"secret_token", nullptr},
                    {"assigned_cluster", nullptr}
                };
                resp->statusCode = 200;
                resp->body = res_j.dump();
            } else {
                nlohmann::json res_j = {
                    {"enrollment_id", enrollment_id},
                    {"status", "APPROVED"},
                    {"secret_token", approved_token},
                    {"assigned_cluster", "cluster-alpha"}
                };
                resp->statusCode = 200;
                resp->body = res_j.dump();
            }
            return resp;
        }

        if (req->method == "POST" && req->uri == "/api/v1/auth/worker") {
            nlohmann::json body_j = nlohmann::json::parse(req->body);
            if (body_j.value("secret_token", "") == approved_token) {
                nlohmann::json res_j = {
                    {"ticket", "ws_tkt_mock_session_ticket_001"},
                    {"websocket_url", "/ws/worker"}
                };
                resp->statusCode = 200;
                resp->body = res_j.dump();
            } else {
                resp->statusCode = 401;
                resp->body = "{\"error\": \"Unauthorized\"}";
            }
            return resp;
        }

        resp->statusCode = 404;
        resp->body = "{\"error\": \"Not Found\"}";
        return resp;
    });

    auto start_res = server.listen();
    REQUIRE(start_res.first);
    server.start();

    legatus::daemon::WorkerConfig config;
    config.server.endpoint = "http://127.0.0.1:" + std::to_string(mock_port);
    config.server.tls_verify_peer = false;
    config.identity.worker_id = "test-worker-node";

    legatus::daemon::EnrollmentClient client(config);

    SECTION("Quarantine registration and polling flow succeeds upon approval") {
        auto keypair = legatus::common::generate_ed25519_keypair();
        auto reg_res = client.register_node(keypair);
        REQUIRE(reg_res.status == legatus::daemon::EnrollmentStatus::PENDING_APPROVAL);
        REQUIRE(reg_res.enrollment_id == enrollment_id);

        auto poll_res = client.poll_status(
            reg_res.enrollment_id,
            std::chrono::milliseconds(50),
            std::chrono::milliseconds(2000)
        );

        REQUIRE(poll_res.status == legatus::daemon::EnrollmentStatus::APPROVED);
        REQUIRE(poll_res.secret_token == approved_token);
        REQUIRE(poll_res.assigned_cluster == "cluster-alpha");

        // Verify Ephemeral Ticket Exchange
        config.identity.secret_token = poll_res.secret_token;
        auto ticket_res = client.exchange_ticket();
        REQUIRE(ticket_res.success);
        REQUIRE(ticket_res.ticket == "ws_tkt_mock_session_ticket_001");
        REQUIRE(ticket_res.websocket_url == "/ws/worker");
    }

    server.stop();
}
