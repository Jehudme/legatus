#pragma once

#include <string>
#include <unordered_map>
#include <cstdint>

namespace legatus::stdlib {

/**
 * Encapsulates hermetic in-memory execution parameters injected into guest runtime context.
 */
struct TaskSandboxContext {
    std::string task_id;
    std::string function_name;
    std::uint64_t instruction_budget = 0;
    std::unordered_map<std::string, std::string> secrets;
    std::unordered_map<std::string, std::string> env_vars;
};

} // namespace legatus::stdlib
