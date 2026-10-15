#pragma once

#include "legatus/common/crypto_utils.hpp"
#include <string>
#include <vector>

namespace legatus::runner {

class SecretZeroizer {
public:
    SecretZeroizer() = default;
    ~SecretZeroizer() {
        zero_all();
    }

    SecretZeroizer(const SecretZeroizer&) = delete;
    SecretZeroizer& operator=(const SecretZeroizer&) = delete;

    SecretZeroizer(SecretZeroizer&&) = default;
    SecretZeroizer& operator=(SecretZeroizer&&) = default;

    void register_buffer(void* ptr, std::size_t len) {
        if (ptr && len > 0) {
            buffers_.push_back({ptr, len});
        }
    }

    void register_string(std::string& str) {
        if (!str.empty()) {
            register_buffer(str.data(), str.size());
        }
    }

    void register_bytes(std::vector<std::uint8_t>& bytes) {
        if (!bytes.empty()) {
            register_buffer(bytes.data(), bytes.size());
        }
    }

    void zero_all() {
        for (const auto& [ptr, len] : buffers_) {
            legatus::common::secure_zero(ptr, len);
        }
        buffers_.clear();
    }

private:
    std::vector<std::pair<void*, std::size_t>> buffers_;
};

} // namespace legatus::runner
