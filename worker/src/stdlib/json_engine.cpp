#include "legatus/stdlib/sandbox.hpp"
#include "legatus/stdlib/solix_memory_helpers.hpp"
#include <nlohmann/json.hpp>
#include <unordered_map>
#include <string>
#include <cstdint>

namespace legatus::stdlib {

namespace {

constexpr size_t MAX_JSON_STRING_LEN = 8 * 1024 * 1024; // 8MB limit
constexpr int MAX_JSON_DEPTH = 32;

thread_local std::unordered_map<int64_t, nlohmann::json> g_json_docs;
thread_local int64_t g_next_json_handle = 1;

int check_json_depth(const nlohmann::json& j, int current_depth = 1) {
    if (current_depth > MAX_JSON_DEPTH) return current_depth;
    int max_d = current_depth;
    if (j.is_structured()) {
        for (const auto& item : j) {
            int d = check_json_depth(item, current_depth + 1);
            if (d > max_d) max_d = d;
            if (max_d > MAX_JSON_DEPTH) return max_d;
        }
    }
    return max_d;
}

const nlohmann::json* resolve_json_node(const nlohmann::json& root, const std::string& path) {
    if (path.empty() || path == "/") return &root;
    if (path[0] == '/') {
        try {
            return &root.at(nlohmann::json::json_pointer(path));
        } catch (...) {
            return nullptr;
        }
    }
    if (root.is_object() && root.contains(path)) {
        return &root[path];
    }
    return nullptr;
}

static uint64_t json_native_parse(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)vm; (void)self; (void)argc;
    std::string s = read_solix_chars(vm, args[0]);
    if (s.size() > MAX_JSON_STRING_LEN) {
        return 0ULL; // Error: payload too large
    }

    try {
        nlohmann::json j = nlohmann::json::parse(s);
        if (check_json_depth(j) > MAX_JSON_DEPTH) {
            return 0ULL; // Exceeded recursion depth
        }

        int64_t handle = g_next_json_handle++;
        g_json_docs[handle] = std::move(j);
        return static_cast<uint64_t>(handle);
    } catch (...) {
        return 0ULL; // Invalid JSON
    }
}

static uint64_t json_native_stringify(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)self; (void)argc;
    int64_t handle = static_cast<int64_t>(args[0]);
    auto it = g_json_docs.find(handle);
    if (it == g_json_docs.end()) {
        return allocate_solix_chars(vm, "null");
    }
    return allocate_solix_chars(vm, it->second.dump());
}

static uint64_t json_native_get_string(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)self; (void)argc;
    int64_t handle = static_cast<int64_t>(args[0]);
    std::string path = read_solix_chars(vm, args[1]);

    auto it = g_json_docs.find(handle);
    if (it == g_json_docs.end()) return 0ULL;

    const nlohmann::json* node = resolve_json_node(it->second, path);
    if (!node) return 0ULL;

    if (node->is_string()) {
        return allocate_solix_chars(vm, node->get<std::string>());
    }
    return allocate_solix_chars(vm, node->dump());
}

static uint64_t json_native_get_int(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)vm; (void)self; (void)argc;
    int64_t handle = static_cast<int64_t>(args[0]);
    std::string path = read_solix_chars(vm, args[1]);

    auto it = g_json_docs.find(handle);
    if (it == g_json_docs.end()) return 0ULL;

    const nlohmann::json* node = resolve_json_node(it->second, path);
    if (node && node->is_number_integer()) {
        return static_cast<uint64_t>(node->get<int64_t>());
    }
    return 0ULL;
}

static uint64_t json_native_get_double(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)self; (void)argc;
    int64_t handle = static_cast<int64_t>(args[0]);
    std::string path = read_solix_chars(vm, args[1]);

    auto it = g_json_docs.find(handle);
    if (it == g_json_docs.end()) return 0ULL;

    const nlohmann::json* node = resolve_json_node(it->second, path);
    if (node && node->is_number()) {
        return bit_cast_to_u64(node->get<double>());
    }
    return bit_cast_to_u64(0.0);
}

static uint64_t json_native_get_bool(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)vm; (void)self; (void)argc;
    int64_t handle = static_cast<int64_t>(args[0]);
    std::string path = read_solix_chars(vm, args[1]);

    auto it = g_json_docs.find(handle);
    if (it == g_json_docs.end()) return 0ULL;

    const nlohmann::json* node = resolve_json_node(it->second, path);
    if (node && node->is_boolean()) {
        return node->get<bool>() ? 1ULL : 0ULL;
    }
    return 0ULL;
}

static uint64_t json_native_contains(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)vm; (void)self; (void)argc;
    int64_t handle = static_cast<int64_t>(args[0]);
    std::string path = read_solix_chars(vm, args[1]);

    auto it = g_json_docs.find(handle);
    if (it == g_json_docs.end()) return 0ULL;

    const nlohmann::json* node = resolve_json_node(it->second, path);
    return node != nullptr ? 1ULL : 0ULL;
}

static uint64_t json_native_free(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)vm; (void)self; (void)argc;
    int64_t handle = static_cast<int64_t>(args[0]);
    g_json_docs.erase(handle);
    return 1ULL;
}

} // namespace

void register_json_natives(solix::NativeRegistry &registry) {
    auto reg = [&](const std::string &cls, const std::string &name, solix::NativeFunctionPtr func) {
        registry.register_function(cls + "_" + name, func);
        registry.register_function("legatus_json_" + cls + "_" + name, func);
        registry.register_function("legatus.json." + cls + "." + name, func);
    };

    reg("JsonDocument", "native_parse", json_native_parse);
    reg("JsonDocument", "native_stringify", json_native_stringify);
    reg("JsonDocument", "native_get_string", json_native_get_string);
    reg("JsonDocument", "native_get_int", json_native_get_int);
    reg("JsonDocument", "native_get_double", json_native_get_double);
    reg("JsonDocument", "native_get_bool", json_native_get_bool);
    reg("JsonDocument", "native_contains", json_native_contains);
    reg("JsonDocument", "native_free", json_native_free);
    reg("JsonDocument", "native_dispose", json_native_free);
}

} // namespace legatus::stdlib
