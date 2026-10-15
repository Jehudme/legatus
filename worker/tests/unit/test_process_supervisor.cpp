#include "legatus/daemon/process_supervisor.hpp"
#include "legatus/runner/execution_envelope.hpp"
#include "legatus/common/base64.hpp"
#include <solix/compilation.hpp>
#include <catch2/catch_all.hpp>
#include <thread>
#include <chrono>

#ifndef LEGATUS_RUNNER_PATH
#define LEGATUS_RUNNER_PATH "./legatus-runner"
#endif

static std::vector<std::uint8_t> compile_solix_to_bytecode(const std::string& source) {
    solix::CompilationOptions copts;
    copts.log_level = solix::CompilationOptions::LogLevel::OFF;
    copts.sources[solix::Source(std::string("main.slx"))] = source;
    return solix::run(copts);
}

TEST_CASE("ProcessSupervisor - Construction and Empty State", "[worker][daemon][supervisor]") {
    legatus::daemon::ProcessSupervisor supervisor(LEGATUS_RUNNER_PATH);
    REQUIRE(supervisor.active_task_count() == 0);
    REQUIRE(supervisor.get_active_tasks().empty());
    REQUIRE(!supervisor.is_task_active("non-existent-task"));
    REQUIRE(!supervisor.abort_task("non-existent-task"));
}

TEST_CASE("ProcessSupervisor - Non-existent runner binary failure", "[worker][daemon][supervisor]") {
    legatus::daemon::ProcessSupervisor supervisor("/invalid/path/to/legatus-runner-bin");
    legatus::runner::ExecutionEnvelope envelope;
    envelope.task_id = "task-fail-binary";
    envelope.function_name = "main";

    auto res = supervisor.launch_task(envelope);
    REQUIRE(res.exit_code == 70); // EX_SOFTWARE
    REQUIRE(!res.error_message.empty());
}

TEST_CASE("ProcessSupervisor - Out-of-Process Task Execution", "[worker][daemon][supervisor]") {
    legatus::daemon::ProcessSupervisor supervisor(LEGATUS_RUNNER_PATH);

    SECTION("Normal task executes out-of-process and returns exit code 0") {
        const std::string solix_src = R"(
            public class Main {
                public static int32 main(char[][] args) {
                    int32 sum = 0;
                    for (int32 i = 1; i <= 10; i++) {
                        sum += i;
                    }
                    return sum == 55 ? 0 : 1;
                }
            }
        )";
        auto bytecode = compile_solix_to_bytecode(solix_src);
        REQUIRE(!bytecode.empty());

        legatus::runner::ExecutionEnvelope envelope;
        envelope.task_id = "task-proc-normal";
        envelope.function_name = "main";
        envelope.bytecode_base64 = legatus::common::base64_encode(bytecode);
        envelope.arguments_json = "{}";
        envelope.instruction_budget = 100000;
        envelope.memory_limit_bytes = 64 * 1024 * 1024;

        auto res = supervisor.launch_task(envelope);
        REQUIRE(res.exit_code == 0);
        REQUIRE(res.instructions_executed > 0);
        REQUIRE(supervisor.active_task_count() == 0);
    }

    SECTION("Instruction budget exhausted terminates out-of-process with exit code 75") {
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
        envelope.task_id = "task-proc-budget";
        envelope.function_name = "main";
        envelope.bytecode_base64 = legatus::common::base64_encode(bytecode);
        envelope.arguments_json = "{}";
        envelope.instruction_budget = 200;
        envelope.memory_limit_bytes = 64 * 1024 * 1024;

        auto res = supervisor.launch_task(envelope);
        REQUIRE(res.exit_code == 75);
        REQUIRE(supervisor.active_task_count() == 0);
    }

    SECTION("Task abort via two-stage kill escalation") {
        // Shorter grace and kill periods for fast testing
        legatus::daemon::ProcessSupervisor fast_supervisor(
            LEGATUS_RUNNER_PATH,
            std::chrono::milliseconds(200),
            std::chrono::milliseconds(200)
        );

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
        envelope.task_id = "task-proc-abort";
        envelope.function_name = "main";
        envelope.bytecode_base64 = legatus::common::base64_encode(bytecode);
        envelope.arguments_json = "{}";
        envelope.instruction_budget = 1000000000ULL; // virtually infinite
        envelope.memory_limit_bytes = 64 * 1024 * 1024;

        std::thread task_thread([&]() {
            fast_supervisor.launch_task(envelope);
        });

        // Wait until task is registered as active
        int retries = 0;
        while (!fast_supervisor.is_task_active("task-proc-abort") && retries++ < 50) {
            std::this_thread::sleep_for(std::chrono::milliseconds(20));
        }

        REQUIRE(fast_supervisor.is_task_active("task-proc-abort"));
        REQUIRE(fast_supervisor.active_task_count() == 1);

        // Abort task
        bool aborted = fast_supervisor.abort_task("task-proc-abort");
        REQUIRE(aborted);

        task_thread.join();
        REQUIRE(fast_supervisor.active_task_count() == 0);
    }
}
