#include "legatus/runner/runner_engine.hpp"
#include "legatus/runner/secret_zeroizer.hpp"
#include "legatus/runner/execution_envelope.hpp"
#include "legatus/common/base64.hpp"
#include <solix/compilation.hpp>
#include <catch2/catch_all.hpp>
#include <sstream>

static std::vector<std::uint8_t> compile_solix_to_bytecode(const std::string& source) {
    solix::CompilationOptions copts;
    copts.log_level = solix::CompilationOptions::LogLevel::OFF;
    copts.sources[solix::Source(std::string("main.slx"))] = source;
    return solix::run(copts);
}

TEST_CASE("Base64 Utilities Roundtrip", "[worker][runner][base64]") {
    std::vector<std::uint8_t> original = {0x00, 0xFF, 0x12, 0x34, 0x56, 0x78, 0x9A, 0xBC, 0xDE};
    std::string encoded = legatus::common::base64_encode(original);
    REQUIRE(!encoded.empty());

    std::vector<std::uint8_t> decoded = legatus::common::base64_decode(encoded);
    REQUIRE(decoded == original);

    // Empty roundtrip
    REQUIRE(legatus::common::base64_decode("").empty());
}

TEST_CASE("SecretZeroizer Memory Scrubbing", "[worker][runner][zeroizer]") {
    std::string secret = "top_secret_token_12345";
    std::vector<std::uint8_t> sensitive_buf = {0xDE, 0xAD, 0xBE, 0xEF};

    {
        legatus::runner::SecretZeroizer zeroizer;
        zeroizer.register_string(secret);
        zeroizer.register_bytes(sensitive_buf);
        // Will zero on destruction
    }

    for (char c : secret) {
        REQUIRE(c == '\0');
    }
    for (auto b : sensitive_buf) {
        REQUIRE(b == 0);
    }
}

TEST_CASE("ExecutionEnvelope Parsing & Serialization", "[worker][runner][envelope]") {
    std::string json_input = R"({
        "task_id": "84e27f67-8973-4554-b3ef-ff09a3c9bfa0",
        "function_name": "computeHash",
        "bytecode_base64": "AQIDBA==",
        "arguments": "{\"x\": 42}",
        "secrets": {"DB_PASS": "secret123"},
        "instruction_budget": 500000,
        "memory_limit_bytes": 67108864
    })";

    auto env = legatus::runner::parse_envelope(json_input);
    REQUIRE(env.task_id == "84e27f67-8973-4554-b3ef-ff09a3c9bfa0");
    REQUIRE(env.function_name == "computeHash");
    REQUIRE(env.bytecode_base64 == "AQIDBA==");
    REQUIRE(env.arguments_json == "{\"x\": 42}");
    REQUIRE(env.secrets["DB_PASS"] == "secret123");
    REQUIRE(env.instruction_budget == 500000);
    REQUIRE(env.memory_limit_bytes == 67108864);
}

TEST_CASE("Runner Engine - Stdin Empty or Malformed Input", "[worker][runner][engine]") {
    SECTION("Empty stdin returns exit code 64") {
        std::istringstream in("");
        std::ostringstream out;
        int exit_code = legatus::runner::run_task_runner(in, out);
        REQUIRE(exit_code == 64); // EX_USAGE

        nlohmann::json res = nlohmann::json::parse(out.str());
        REQUIRE(res["exit_code"] == 64);
        REQUIRE(res["error_message"].get<std::string>().find("Empty execution envelope") != std::string::npos);
    }

    SECTION("Malformed JSON returns exit code 64") {
        std::istringstream in("not valid json");
        std::ostringstream out;
        int exit_code = legatus::runner::run_task_runner(in, out);
        REQUIRE(exit_code == 64);

        nlohmann::json res = nlohmann::json::parse(out.str());
        REQUIRE(res["exit_code"] == 64);
        REQUIRE(res["error_message"].get<std::string>().find("Malformed JSON") != std::string::npos);
    }
}

TEST_CASE("Runner Engine - Solix Bytecode Execution", "[worker][runner][solix]") {
    SECTION("Case 1: Normal task executes successfully and emits exit code 0") {
        const std::string solix_src = R"(
            public class Main {
                public static int32 main(char[][] args) {
                    int32 acc = 0;
                    for (int32 i = 0; i < 5; i++) {
                        acc += i;
                    }
                    return acc == 10 ? 0 : 1;
                }
            }
        )";
        auto bytecode = compile_solix_to_bytecode(solix_src);
        REQUIRE(!bytecode.empty());

        nlohmann::json env_j = {
            {"task_id", "task-normal-01"},
            {"function_name", "main"},
            {"bytecode_base64", legatus::common::base64_encode(bytecode)},
            {"arguments", "{\"test\": true}"},
            {"instruction_budget", 100000},
            {"memory_limit_bytes", 64 * 1024 * 1024}
        };

        std::istringstream in(env_j.dump());
        std::ostringstream out;
        int exit_code = legatus::runner::run_task_runner(in, out);
        REQUIRE(exit_code == 0);

        nlohmann::json res = nlohmann::json::parse(out.str());
        REQUIRE(res["exit_code"] == 0);
        REQUIRE(res["instructions_executed"].get<uint64_t>() > 0);
    }

    SECTION("Case 2: Infinite loop task exceeds instruction budget and exits with 75") {
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

        nlohmann::json env_j = {
            {"task_id", "task-infinite-02"},
            {"function_name", "main"},
            {"bytecode_base64", legatus::common::base64_encode(bytecode)},
            {"arguments", "{}"},
            {"instruction_budget", 150}, // very small budget
            {"memory_limit_bytes", 64 * 1024 * 1024}
        };

        std::istringstream in(env_j.dump());
        std::ostringstream out;
        int exit_code = legatus::runner::run_task_runner(in, out);
        REQUIRE(exit_code == 75); // EX_TEMPFAIL

        nlohmann::json res = nlohmann::json::parse(out.str());
        REQUIRE(res["exit_code"] == 75);
        REQUIRE(res["error_message"].get<std::string>().find("Instruction budget exhausted") != std::string::npos);
    }
}
