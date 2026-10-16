#pragma once

#include <solix/runtime.hpp>
#include <string>
#include <vector>
#include <cstring>
#include <cstdint>

namespace legatus::stdlib {

template <typename T>
inline uint64_t bit_cast_to_u64(T value) {
    uint64_t result = 0;
    std::memcpy(&result, &value, sizeof(T));
    return result;
}

template <typename T>
inline T bit_cast_from_u64(uint64_t value) {
    T result;
    std::memcpy(&result, &value, sizeof(T));
    return result;
}

inline std::string read_solix_chars(solix::RuntimeContext &vm, uint64_t addr) {
    if (addr == 0) return "";
    uint32_t len = vm.memory.get_array_length(static_cast<solix::Address>(addr));
    std::string s;
    s.reserve(len);
    for (uint32_t i = 0; i < len; ++i) {
        s.push_back(vm.memory.read_char(static_cast<solix::Address>(addr), i));
    }
    return s;
}

inline uint64_t allocate_solix_chars(solix::RuntimeContext &vm, const std::string &s) {
    uint32_t len = static_cast<uint32_t>(s.length());
    uint64_t addr = vm.memory.dynamic_allocation(len, solix::BlockTag::ARRAY_U8);
    for (size_t i = 0; i < s.length(); ++i) {
        vm.memory.write_u8(static_cast<solix::Address>(addr), static_cast<uint32_t>(i), static_cast<uint8_t>(s[i]));
    }
    return addr;
}

inline std::vector<uint8_t> read_solix_bytes(solix::RuntimeContext &vm, uint64_t addr) {
    if (addr == 0) return {};
    uint32_t len = vm.memory.get_array_length(static_cast<solix::Address>(addr));
    std::vector<uint8_t> buf(len);
    for (uint32_t i = 0; i < len; ++i) {
        buf[i] = vm.memory.read_u8(static_cast<solix::Address>(addr), i);
    }
    return buf;
}

inline uint64_t allocate_solix_bytes(solix::RuntimeContext &vm, const uint8_t *data, size_t len) {
    uint32_t ulen = static_cast<uint32_t>(len);
    uint64_t addr = vm.memory.dynamic_allocation(ulen, solix::BlockTag::ARRAY_U8);
    for (size_t i = 0; i < len; ++i) {
        vm.memory.write_u8(static_cast<solix::Address>(addr), static_cast<uint32_t>(i), data[i]);
    }
    return addr;
}

} // namespace legatus::stdlib
