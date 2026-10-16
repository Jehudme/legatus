#include "legatus/stdlib/sandbox.hpp"
#include "legatus/stdlib/solix_memory_helpers.hpp"
#include <openssl/rand.h>
#include <string>
#include <cstdio>
#include <cctype>

namespace legatus::stdlib {

namespace {

static uint64_t uuid_native_random(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)self; (void)args; (void)argc;
    uint8_t bytes[16];
    if (RAND_bytes(bytes, sizeof(bytes)) != 1) {
        return 0ULL;
    }

    // Set version to 4 (0100 in bits 4-7 of time_hi_and_version)
    bytes[6] = (bytes[6] & 0x0F) | 0x40;
    // Set variant to RFC 4122 (10 in bits 6-7 of clock_seq_hi_and_reserved)
    bytes[8] = (bytes[8] & 0x3F) | 0x80;

    char buf[37];
    std::snprintf(buf, sizeof(buf),
        "%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
        bytes[0], bytes[1], bytes[2], bytes[3],
        bytes[4], bytes[5],
        bytes[6], bytes[7],
        bytes[8], bytes[9],
        bytes[10], bytes[11], bytes[12], bytes[13], bytes[14], bytes[15]
    );

    return allocate_solix_chars(vm, std::string(buf));
}

static uint64_t uuid_native_is_valid(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)vm; (void)self; (void)argc;
    std::string s = read_solix_chars(vm, args[0]);
    if (s.size() != 36) return 0ULL;

    for (size_t i = 0; i < 36; ++i) {
        if (i == 8 || i == 13 || i == 18 || i == 23) {
            if (s[i] != '-') return 0ULL;
        } else {
            if (!std::isxdigit(static_cast<unsigned char>(s[i]))) return 0ULL;
        }
    }
    return 1ULL;
}

} // namespace

void register_uuid_natives(solix::NativeRegistry &registry) {
    auto reg = [&](const std::string &cls, const std::string &name, solix::NativeFunctionPtr func) {
        registry.register_function(cls + "_" + name, func);
        registry.register_function("legatus_uuid_" + cls + "_" + name, func);
        registry.register_function("legatus.uuid." + cls + "." + name, func);
    };

    reg("Uuid", "native_random", uuid_native_random);
    reg("Uuid", "native_is_valid", uuid_native_is_valid);
}

} // namespace legatus::stdlib
