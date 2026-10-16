#include "legatus/daemon/bytecode_cache.hpp"
#include "legatus/daemon/process_supervisor.hpp"
#include "legatus/runner/execution_envelope.hpp"
#include "legatus/common/crypto_utils.hpp"
#include "legatus/common/base64.hpp"
#include <solix/compilation.hpp>
#include <ixwebsocket/IXHttpServer.h>
#include <catch2/catch_all.hpp>
#include <filesystem>
#include <random>

#ifndef LEGATUS_RUNNER_PATH
#define LEGATUS_RUNNER_PATH "./legatus-runner"
#endif

static std::vector<std::uint8_t> compile_solix_to_bytecode(const std::string& source) {
    solix::CompilationOptions copts;
    copts.log_level = solix::CompilationOptions::LogLevel::OFF;
    copts.sources[solix::Source(std::string("main.slx"))] = source;
    return solix::run(copts);
}

TEST_CASE("Bytecode Cache HTTP Fallback Integration", "[worker][integration][bytecode]") {
    // Compile a simple arithmetic task
    const std::string solix_src = R"(
        public class Main {
            public static int32 main(char[][] args) {
                int32 val = 42;
                return val == 42 ? 0 : 1;
            }
        }
    )";
    auto bytecode = compile_solix_to_bytecode(solix_src);
    REQUIRE(!bytecode.empty());

    // Compute SHA-256 hex
    std::string sha256_hex = legatus::common::sha256_hex(bytecode.data(), bytecode.size());
    REQUIRE(!sha256_hex.empty());

    // Setup an in-process mock HTTP server on an available port
    std::random_device rd;
    int port = 19100 + (rd() % 500);

    ix::HttpServer server(port, "127.0.0.1");
    server.setOnConnectionCallback([&](ix::HttpRequestPtr request, std::shared_ptr<ix::ConnectionState>) -> ix::HttpResponsePtr {
        if (request->uri == "/api/v1/bytecode/" + sha256_hex) {
            std::string body(reinterpret_cast<const char*>(bytecode.data()), bytecode.size());
            ix::WebSocketHttpHeaders headers;
            headers["Content-Type"] = "application/octet-stream";
            return std::make_shared<ix::HttpResponse>(
                200, "OK", ix::HttpErrorCode::Ok, headers, body);
        }
        return std::make_shared<ix::HttpResponse>(404, "Not Found");
    });

    auto listen_res = server.listen();
    REQUIRE(listen_res.first);
    server.start();

    // Use a clean temporary cache directory
    auto temp_cache_dir = std::filesystem::temp_directory_path() /
        ("legatus_test_bc_" + std::to_string(std::chrono::steady_clock::now().time_since_epoch().count()));
    std::filesystem::create_directories(temp_cache_dir);

    struct DirCleanup {
        std::filesystem::path dir;
        ~DirCleanup() {
            std::error_code ec;
            std::filesystem::remove_all(dir, ec);
        }
    } cleanup{temp_cache_dir};

    std::string server_endpoint = "http://127.0.0.1:" + std::to_string(port);
    legatus::daemon::BytecodeCache cache(temp_cache_dir, server_endpoint, false);

    SECTION("Fetches absent bytecode via HTTP GET and persists to disk") {
        REQUIRE(!cache.has(sha256_hex));

        // Attempt get_or_fetch with empty base64 string
        auto fetched = cache.get_or_fetch(sha256_hex, "", "/api/v1/bytecode/" + sha256_hex);
        REQUIRE(fetched == bytecode);
        REQUIRE(cache.has(sha256_hex));

        // Stop HTTP server to prove subsequent fetch hits local disk cache
        server.stop();

        auto cached = cache.get_or_fetch(sha256_hex);
        REQUIRE(cached == bytecode);
    }

    SECTION("Out-of-process runner executes task resolved from HTTP bytecode fallback") {
        auto fetched = cache.get_or_fetch(sha256_hex, "", "/api/v1/bytecode/" + sha256_hex);
        REQUIRE(!fetched.empty());

        legatus::daemon::ProcessSupervisor supervisor(LEGATUS_RUNNER_PATH);
        legatus::runner::ExecutionEnvelope envelope;
        envelope.task_id = "task-http-bytecode";
        envelope.function_name = "main";
        envelope.bytecode_base64 = legatus::common::base64_encode(fetched);
        envelope.bytecode_path = cache.get_path(sha256_hex).string();
        envelope.arguments_json = "{}";

        auto res = supervisor.launch_task(envelope);
        REQUIRE(res.exit_code == 0);
        REQUIRE(res.error_message.empty());
    }

    server.stop();
}
