#pragma once

#include <string>
#include <vector>
#include <filesystem>
#include <mutex>

namespace legatus::daemon {

class BytecodeCache {
public:
    explicit BytecodeCache(
        std::filesystem::path cache_dir,
        std::string server_endpoint = "",
        bool tls_verify_peer = true,
        std::string ca_cert_path = "");

    bool has(const std::string& sha256_hex) const;

    std::filesystem::path get_path(const std::string& sha256_hex) const;

    std::vector<uint8_t> get(const std::string& sha256_hex) const;

    void store(const std::string& sha256_hex, const std::vector<uint8_t>& bytecode);

    void store_base64(const std::string& sha256_hex, const std::string& base64_str);

    std::vector<uint8_t> get_or_fetch(
        const std::string& sha256_hex,
        const std::string& base64_str = "",
        const std::string& bytecode_url = "");

    const std::filesystem::path& cache_dir() const { return cache_dir_; }

private:
    std::filesystem::path cache_dir_;
    std::string server_endpoint_;
    bool tls_verify_peer_;
    std::string ca_cert_path_;
    mutable std::mutex mutex_;
};

} // namespace legatus::daemon
