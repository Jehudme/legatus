#include "legatus/daemon/process_supervisor.hpp"
#include "legatus/runner/execution_envelope.hpp"
#include "legatus/common/base64.hpp"
#include <solix/compilation.hpp>
#include <catch2/catch_all.hpp>
#include <thread>
#include <chrono>
#include <vector>
#include <string>

#ifndef LEGATUS_RUNNER_PATH
#define LEGATUS_RUNNER_PATH "./legatus-runner"
#endif

#if defined(__unix__) || defined(__APPLE__) || defined(__linux__)
#include <csignal>
namespace {
    struct TestSigpipeIgnorer {
        TestSigpipeIgnorer() {
            std::signal(SIGPIPE, SIG_IGN);
        }
    };
    static TestSigpipeIgnorer g_sigpipe_ignorer;
}
#endif

static std::vector<std::uint8_t> compile_solix_to_bytecode(const std::string& source) {
    solix::CompilationOptions copts;
    copts.log_level = solix::CompilationOptions::LogLevel::OFF;
    copts.sources[solix::Source(std::string("main.slx"))] = source;
    return solix::run(copts);
}

TEST_CASE("Process Isolation and Signal Lifecycle Integration Suite", "[worker][integration][isolation]") {
    legatus::daemon::ProcessSupervisor supervisor(
        LEGATUS_RUNNER_PATH,
        std::chrono::milliseconds(500),
        std::chrono::milliseconds(500)
    );

    SECTION("Guest Stdout Flood: High volume prints do not corrupt result file") {
        const std::string solix_src = R"(
            class Console {
                public static native void native_print_string(char[] str);
            }

            public class Main {
                public static int32 main(char[][] args) {
                    for (int32 i = 0; i < 2000; i++) {
                        Console.native_print_string("FLOOD_LOG_LINE_ALPHA_BETA_GAMMA_DELTA_EPSILON_ZETA_ETA_THETA_1234567890\n");
                    }
                    return 0;
                }
            }
        )";
        auto bytecode = compile_solix_to_bytecode(solix_src);
        REQUIRE(!bytecode.empty());

        legatus::runner::ExecutionEnvelope envelope;
        envelope.task_id = "task-flood-test";
        envelope.function_name = "main";
        envelope.bytecode_base64 = legatus::common::base64_encode(bytecode);
        envelope.arguments_json = "{}";
        envelope.instruction_budget = 5000000;
        envelope.memory_limit_bytes = 64 * 1024 * 1024;

        size_t chunk_count = 0;
        size_t total_bytes_streamed = 0;
        auto on_chunk = [&](const std::string& stream, const std::string& chunk) {
            if (stream == "stdout") {
                chunk_count++;
                total_bytes_streamed += chunk.size();
            }
        };

        auto res = supervisor.launch_task(envelope, on_chunk);

        // Verification: Exit code must be 0, instructions executed recorded, no JSON parse error 70
        REQUIRE(res.exit_code == 0);
        REQUIRE(res.error_message.empty());
        REQUIRE(res.instructions_executed > 0);
        REQUIRE(chunk_count > 0);
        REQUIRE(total_bytes_streamed > 10000);
        REQUIRE(supervisor.active_task_count() == 0);
    }

    SECTION("Signal Abort: Cooperative signal termination returns exit code 143 or 130") {
        const std::string solix_src = R"(
            public class Main {
                public static int32 main(char[][] args) {
                    while (true) {}
                    return 0;
                }
            }
        )";
        auto bytecode = compile_solix_to_bytecode(solix_src);
        REQUIRE(!bytecode.empty());

        legatus::runner::ExecutionEnvelope envelope;
        envelope.task_id = "task-signal-abort-test";
        envelope.function_name = "main";
        envelope.bytecode_base64 = legatus::common::base64_encode(bytecode);
        envelope.arguments_json = "{}";
        envelope.instruction_budget = 1000000000ULL;
        envelope.memory_limit_bytes = 64 * 1024 * 1024;

        legatus::runner::ExecutionResult res;
        std::thread task_thread([&]() {
            res = supervisor.launch_task(envelope);
        });

        // Wait until task is running
        int retries = 0;
        while (!supervisor.is_task_active("task-signal-abort-test") && retries++ < 50) {
            std::this_thread::sleep_for(std::chrono::milliseconds(20));
        }
        REQUIRE(supervisor.is_task_active("task-signal-abort-test"));

        auto abort_start = std::chrono::steady_clock::now();
        bool aborted = supervisor.abort_task("task-signal-abort-test");
        REQUIRE(aborted);

        task_thread.join();
        auto abort_elapsed_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now() - abort_start).count();

        // Process should terminate rapidly
#if defined(_WIN32)
        REQUIRE(abort_elapsed_ms < 6000);
        REQUIRE((res.exit_code == 143 || res.exit_code == 130 || res.exit_code == 137));
#else
        REQUIRE(abort_elapsed_ms < 1500);
        // On Linux/POSIX SIGTERM maps to 143 (128+15) or 130 (SIGINT / handled)
        REQUIRE((res.exit_code == 143 || res.exit_code == 130));
#endif
        REQUIRE(supervisor.active_task_count() == 0);
    }

    SECTION("Execution Timeout: Wall-clock timeout enforcement terminates with code 124") {
        const std::string solix_src = R"(
            public class Main {
                public static int32 main(char[][] args) {
                    while (true) {}
                    return 0;
                }
            }
        )";
        auto bytecode = compile_solix_to_bytecode(solix_src);
        REQUIRE(!bytecode.empty());

        legatus::runner::ExecutionEnvelope envelope;
        envelope.task_id = "task-timeout-test";
        envelope.function_name = "main";
        envelope.bytecode_base64 = legatus::common::base64_encode(bytecode);
        envelope.arguments_json = "{}";
        envelope.instruction_budget = 1000000000ULL;
        envelope.memory_limit_bytes = 64 * 1024 * 1024;
        envelope.timeout_ms = 800; // 800ms wall-clock deadline

        auto start = std::chrono::steady_clock::now();
        auto res = supervisor.launch_task(envelope);
        auto elapsed_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now() - start).count();

        REQUIRE(res.exit_code == 124);
        REQUIRE(res.error_message.find("timed out") != std::string::npos);
        REQUIRE(elapsed_ms >= 750);
        REQUIRE(elapsed_ms < 2500);
        REQUIRE(supervisor.active_task_count() == 0);
    }
}
