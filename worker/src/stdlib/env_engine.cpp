#include "legatus/stdlib/sandbox.hpp"
#include "legatus/stdlib/solix_memory_helpers.hpp"
#include "legatus/version.hpp"
#include <unordered_map>
#include <string>

namespace legatus::stdlib {

namespace {

thread_local std::unordered_map<std::string, std::string> g_task_env;

static uint64_t env_native_get_env(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)self; (void)argc;
    std::string key = read_solix_chars(vm, args[0]);

    if (key == "LEGATUS_CLUSTER_VERSION") {
        return allocate_solix_chars(vm, std::string(legatus::VERSION));
    }
    if (key == "LEGATUS_PLATFORM") {
        return allocate_solix_chars(vm, "pure-in-memory-compute");
    }

    auto it = g_task_env.find(key);
    if (it != g_task_env.end()) {
        return allocate_solix_chars(vm, it->second);
    }
    return 0ULL; // null: strictly no access to host OS getenv()
}

static uint64_t env_native_has_env(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)vm; (void)self; (void)argc;
    std::string key = read_solix_chars(vm, args[0]);
    if (key == "LEGATUS_CLUSTER_VERSION" || key == "LEGATUS_PLATFORM") return 1ULL;
    return g_task_env.contains(key) ? 1ULL : 0ULL;
}

} // namespace

void register_env_natives(solix::NativeRegistry &registry, const TaskSandboxContext &context) {
    g_task_env = context.env_vars;

    auto reg = [&](const std::string &cls, const std::string &name, solix::NativeFunctionPtr func) {
        registry.register_function(cls + "_" + name, func);
        registry.register_function("legatus_env_" + cls + "_" + name, func);
        registry.register_function("legatus.env." + cls + "." + name, func);
        // Also register under solix.system.Environment to safely intercept legacy calls
        registry.register_function("solix_system_" + cls + "_" + name, func);
        registry.register_function("solix.system." + cls + "." + name, func);
    };

    reg("Environment", "native_get_env", env_native_get_env);
    reg("Environment", "native_has_env", env_native_has_env);
}

} // namespace legatus::stdlib
