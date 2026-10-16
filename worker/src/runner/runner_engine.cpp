#include "legatus/runner/runner_engine.hpp"
#include "legatus/runner/legatus_runtime_context.hpp"
#include "legatus/common/base64.hpp"
#include "legatus/stdlib/sandbox.hpp"
#include <fstream>
#include <sstream>
#include <iostream>

namespace legatus::runner {

ExecutionResult execute_task(ExecutionEnvelope& envelope, SecretZeroizer& zeroizer) {
    ExecutionResult result;
    result.task_id = envelope.task_id;

    // Register secrets and input string for zeroing on scope exit
    zeroizer.register_string(envelope.arguments_json);
    for (auto& [k, v] : envelope.secrets) {
        (void)k;
        zeroizer.register_string(v);
    }

    std::vector<std::uint8_t> bytecode;
    if (!envelope.bytecode_base64.empty()) {
        bytecode = common::base64_decode(envelope.bytecode_base64);
    } else if (!envelope.bytecode_path.empty()) {
        std::ifstream file(envelope.bytecode_path, std::ios::binary | std::ios::ate);
        if (file) {
            std::streamsize size = file.tellg();
            file.seekg(0, std::ios::beg);
            bytecode.resize(size);
            file.read(reinterpret_cast<char*>(bytecode.data()), size);
        }
    }

    if (bytecode.empty()) {
        result.exit_code = 70; // EX_SOFTWARE
        result.error_message = "Bytecode payload is missing or empty";
        return result;
    }

    solix::RuntimeOptions opts;
    // 8 MB stack (1M 64-bit words)
    opts.stack_capacity = 1024 * 1024;
    // Remainder to heap (minimum 16 MB)
    constexpr std::uint64_t stack_bytes = 8ULL * 1024ULL * 1024ULL;
    if (envelope.memory_limit_bytes > stack_bytes) {
        opts.heap_capacity = static_cast<size_t>(envelope.memory_limit_bytes - stack_bytes);
    } else {
        opts.heap_capacity = 16 * 1024 * 1024;
    }

    opts.bytecode_source = bytecode;
    opts.program_args = { envelope.arguments_json };
    opts.instruction_budget = envelope.instruction_budget;
    opts.sandboxed = true;

    try {
        stdlib::TaskSandboxContext sandbox_ctx;
        sandbox_ctx.task_id = envelope.task_id;
        sandbox_ctx.function_name = envelope.function_name;
        sandbox_ctx.instruction_budget = envelope.instruction_budget;
        sandbox_ctx.secrets.insert(envelope.secrets.begin(), envelope.secrets.end());
        sandbox_ctx.env_vars.insert(envelope.environment_variables.begin(), envelope.environment_variables.end());

        stdlib::register_sandboxed_stdlib(solix::NativeRegistry::global(), sandbox_ctx);

        LegatusRuntimeContext vm(opts);
        vm.execute();

        result.exit_code = vm.exit_code;
        result.instructions_executed = vm.get_instructions_executed();
        result.peak_memory_bytes = vm.get_peak_memory_bytes();

        if (vm.is_budget_exhausted()) {
            result.exit_code = 75; // EX_TEMPFAIL
            result.error_message = "Instruction budget exhausted";
        } else if (vm.is_oom_occurred()) {
            result.exit_code = 71; // EX_OSERR
            result.error_message = "Out of memory in sandbox";
        }
    } catch (const std::exception& e) {
        std::string msg = e.what();
        if (msg.find("overflow") != std::string::npos || msg.find("Heap overflow") != std::string::npos) {
            result.exit_code = 71; // EX_OSERR
            result.error_message = "Memory limit exceeded: " + msg;
        } else {
            result.exit_code = 70; // EX_SOFTWARE
            result.error_message = "Runtime exception: " + msg;
        }
    }

    return result;
}

int run_task_runner(std::istream& in, std::ostream& out) {
    SecretZeroizer zeroizer;
    std::string input_json((std::istreambuf_iterator<char>(in)), std::istreambuf_iterator<char>());
    zeroizer.register_string(input_json);

    ExecutionResult result;
    if (input_json.empty()) {
        result.exit_code = 64; // EX_USAGE
        result.error_message = "Empty execution envelope received on stdin";
    } else {
        try {
            ExecutionEnvelope env = parse_envelope(input_json);
            result = execute_task(env, zeroizer);
        } catch (const std::exception& e) {
            result.exit_code = 64; // EX_USAGE
            result.error_message = std::string("Malformed JSON envelope: ") + e.what();
        }
    }

    nlohmann::json out_j;
    to_json(out_j, result);
    out << out_j.dump() << std::endl;

    return result.exit_code;
}

} // namespace legatus::runner
