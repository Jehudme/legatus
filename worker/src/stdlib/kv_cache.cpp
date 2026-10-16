#include "legatus/stdlib/sandbox.hpp"
#include "legatus/stdlib/solix_memory_helpers.hpp"
#include <unordered_map>
#include <string>
#include <chrono>
#include <mutex>

namespace legatus::stdlib {

namespace {

constexpr size_t MAX_KV_TOTAL_BYTES = 16 * 1024 * 1024; // 16MB memory cap

struct CacheEntry {
    std::string value;
    std::chrono::steady_clock::time_point expires_at;
    bool has_ttl = false;
};

thread_local std::unordered_map<std::string, CacheEntry> g_kv_store;
thread_local size_t g_kv_current_bytes = 0;

static uint64_t kv_native_set(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)self;
    std::string key = read_solix_chars(vm, args[0]);
    std::string val = read_solix_chars(vm, args[1]);
    int64_t ttl_secs = (argc > 2) ? static_cast<int64_t>(args[2]) : 0;

    size_t entry_size = key.size() + val.size();
    if (g_kv_current_bytes + entry_size > MAX_KV_TOTAL_BYTES) {
        return 0ULL; // Memory cap reached
    }

    CacheEntry entry;
    entry.value = val;
    if (ttl_secs > 0) {
        entry.has_ttl = true;
        entry.expires_at = std::chrono::steady_clock::now() + std::chrono::seconds(ttl_secs);
    } else {
        entry.has_ttl = false;
    }

    auto it = g_kv_store.find(key);
    if (it != g_kv_store.end()) {
        g_kv_current_bytes -= (it->first.size() + it->second.value.size());
    }
    g_kv_store[key] = std::move(entry);
    g_kv_current_bytes += entry_size;

    return 1ULL;
}

static uint64_t kv_native_get(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)self; (void)argc;
    std::string key = read_solix_chars(vm, args[0]);

    auto it = g_kv_store.find(key);
    if (it == g_kv_store.end()) {
        return 0ULL; // null
    }

    if (it->second.has_ttl && std::chrono::steady_clock::now() > it->second.expires_at) {
        g_kv_current_bytes -= (it->first.size() + it->second.value.size());
        g_kv_store.erase(it);
        return 0ULL; // Expired
    }

    return allocate_solix_chars(vm, it->second.value);
}

static uint64_t kv_native_has(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)self; (void)argc;
    std::string key = read_solix_chars(vm, args[0]);

    auto it = g_kv_store.find(key);
    if (it == g_kv_store.end()) return 0ULL;

    if (it->second.has_ttl && std::chrono::steady_clock::now() > it->second.expires_at) {
        g_kv_current_bytes -= (it->first.size() + it->second.value.size());
        g_kv_store.erase(it);
        return 0ULL;
    }
    return 1ULL;
}

static uint64_t kv_native_delete(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)self; (void)argc;
    std::string key = read_solix_chars(vm, args[0]);

    auto it = g_kv_store.find(key);
    if (it != g_kv_store.end()) {
        g_kv_current_bytes -= (it->first.size() + it->second.value.size());
        g_kv_store.erase(it);
        return 1ULL;
    }
    return 0ULL;
}

} // namespace

void register_kv_natives(solix::NativeRegistry &registry) {
    auto reg = [&](const std::string &cls, const std::string &name, solix::NativeFunctionPtr func) {
        registry.register_function(cls + "_" + name, func);
        registry.register_function("legatus_storage_kv_" + cls + "_" + name, func);
        registry.register_function("legatus.storage.kv." + cls + "." + name, func);
    };

    reg("EphemeralKV", "native_set", kv_native_set);
    reg("EphemeralKV", "native_get", kv_native_get);
    reg("EphemeralKV", "native_has", kv_native_has);
    reg("EphemeralKV", "native_delete", kv_native_delete);
}

} // namespace legatus::stdlib
