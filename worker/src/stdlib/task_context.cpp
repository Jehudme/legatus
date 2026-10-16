#include "legatus/stdlib/sandbox.hpp"
#include "legatus/stdlib/solix_memory_helpers.hpp"
#include <string>
#include <unordered_map>
#include <iostream>

namespace legatus::stdlib {

namespace {

thread_local TaskSandboxContext g_current_context;

static uint64_t runtime_native_task_id(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)self; (void)args; (void)argc;
    return allocate_solix_chars(vm, g_current_context.task_id);
}

static uint64_t runtime_native_function_name(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)self; (void)args; (void)argc;
    return allocate_solix_chars(vm, g_current_context.function_name);
}

static uint64_t runtime_native_instruction_budget(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)vm; (void)self; (void)args; (void)argc;
    return g_current_context.instruction_budget;
}

static uint64_t runtime_native_instructions_consumed(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)self; (void)args; (void)argc;
    return vm.instructions_executed;
}

static uint64_t runtime_native_get_secret(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)self; (void)argc;
    std::string key = read_solix_chars(vm, args[0]);
    auto it = g_current_context.secrets.find(key);
    if (it != g_current_context.secrets.end()) {
        return allocate_solix_chars(vm, it->second);
    }
    return 0ULL; // null
}

static uint64_t runtime_native_emit_progress(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)self; (void)argc;
    std::string checkpoint = read_solix_chars(vm, args[0]);
    // Emit progress notice to stdout in structured format for ProcessSupervisor to catch
    std::cout << "[PROGRESS:" << vm.instructions_executed << ":" << checkpoint << "]\n" << std::flush;
    return 1ULL;
}

} // namespace

void register_runtime_natives(solix::NativeRegistry &registry, const TaskSandboxContext &context) {
    g_current_context = context;

    auto reg = [&](const std::string &cls, const std::string &name, solix::NativeFunctionPtr func) {
        registry.register_function(cls + "_" + name, func);
        registry.register_function("legatus_runtime_" + cls + "_" + name, func);
        registry.register_function("legatus.runtime." + cls + "." + name, func);
    };

    reg("TaskContext", "native_task_id", runtime_native_task_id);
    reg("TaskContext", "native_function_name", runtime_native_function_name);
    reg("TaskContext", "native_instruction_budget", runtime_native_instruction_budget);
    reg("TaskContext", "native_instructions_consumed", runtime_native_instructions_consumed);
    reg("TaskContext", "native_get_secret", runtime_native_get_secret);
    reg("TaskContext", "native_emit_progress", runtime_native_emit_progress);
}

} // namespace legatus::stdlib
