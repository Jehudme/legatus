#pragma once

#include <string>
#include <map>
#include <cstdint>
#include <nlohmann/json.hpp>

namespace legatus::runner {

struct ExecutionEnvelope {
    std::string task_id;
    std::string function_name;
    std::string bytecode_base64;
    std::string bytecode_path;
    std::string arguments_json = "{}";
    std::map<std::string, std::string> secrets;
    std::map<std::string, std::string> environment_variables;
    std::uint64_t instruction_budget = 1'000'000;
    std::uint64_t memory_limit_bytes = 128 * 1024 * 1024; // 128 MB default
};

struct ExecutionResult {
    std::string task_id;
    int32_t exit_code = 0;
    std::uint64_t instructions_executed = 0;
    std::uint64_t peak_memory_bytes = 0;
    std::string stdout_output;
    std::string error_message;
};

inline void to_json(nlohmann::json& j, const ExecutionResult& r) {
    j = nlohmann::json{
        {"task_id", r.task_id},
        {"exit_code", r.exit_code},
        {"instructions_executed", r.instructions_executed},
        {"peak_memory_bytes", r.peak_memory_bytes},
        {"stdout_output", r.stdout_output},
        {"error_message", r.error_message}
    };
}

inline ExecutionEnvelope parse_envelope(const std::string& json_str) {
    nlohmann::json j = nlohmann::json::parse(json_str);
    ExecutionEnvelope env;
    env.task_id = j.value("task_id", "");
    env.function_name = j.value("function_name", "");
    env.bytecode_base64 = j.value("bytecode_base64", "");
    env.bytecode_path = j.value("bytecode_path", "");

    if (j.contains("arguments")) {
        if (j["arguments"].is_string()) {
            env.arguments_json = j["arguments"].get<std::string>();
        } else {
            env.arguments_json = j["arguments"].dump();
        }
    }

    if (j.contains("secrets") && j["secrets"].is_object()) {
        for (auto it = j["secrets"].begin(); it != j["secrets"].end(); ++it) {
            env.secrets[it.key()] = it.value().get<std::string>();
        }
    }

    if (j.contains("environment") && j["environment"].is_object()) {
        for (auto it = j["environment"].begin(); it != j["environment"].end(); ++it) {
            env.environment_variables[it.key()] = it.value().get<std::string>();
        }
    } else if (j.contains("environment_variables") && j["environment_variables"].is_object()) {
        for (auto it = j["environment_variables"].begin(); it != j["environment_variables"].end(); ++it) {
            env.environment_variables[it.key()] = it.value().get<std::string>();
        }
    }

    env.instruction_budget = j.value("instruction_budget", 1'000'000ULL);
    env.memory_limit_bytes = j.value("memory_limit_bytes", 128ULL * 1024ULL * 1024ULL);
    return env;
}

} // namespace legatus::runner
