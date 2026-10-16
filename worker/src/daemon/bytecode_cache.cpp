#include "legatus/daemon/bytecode_cache.hpp"
#include "legatus/common/base64.hpp"
#include "legatus/common/crypto_utils.hpp"
#include <ixwebsocket/IXHttpClient.h>
#include <fstream>
#include <chrono>

namespace legatus::daemon {

BytecodeCache::BytecodeCache(
    std::filesystem::path cache_dir,
    std::string server_endpoint,
    bool tls_verify_peer,
    std::string ca_cert_path)
    : cache_dir_(std::move(cache_dir)),
      server_endpoint_(std::move(server_endpoint)),
      tls_verify_peer_(tls_verify_peer),
      ca_cert_path_(std::move(ca_cert_path)) {
    std::error_code ec;
    std::filesystem::create_directories(cache_dir_, ec);
}

std::filesystem::path BytecodeCache::get_path(const std::string& sha256_hex) const {
    return cache_dir_ / (sha256_hex + ".slxb");
}

bool BytecodeCache::has(const std::string& sha256_hex) const {
    std::lock_guard<std::mutex> lock(mutex_);
    return std::filesystem::exists(get_path(sha256_hex));
}

std::vector<uint8_t> BytecodeCache::get(const std::string& sha256_hex) const {
    std::lock_guard<std::mutex> lock(mutex_);
    auto p = get_path(sha256_hex);
    if (!std::filesystem::exists(p)) {
        return {};
    }

    std::ifstream ifs(p, std::ios::binary);
    if (!ifs.is_open()) {
        return {};
    }

    return std::vector<uint8_t>(
        (std::istreambuf_iterator<char>(ifs)),
        std::istreambuf_iterator<char>());
}

void BytecodeCache::store(const std::string& sha256_hex, const std::vector<uint8_t>& bytecode) {
    std::lock_guard<std::mutex> lock(mutex_);
    std::error_code ec;
    std::filesystem::create_directories(cache_dir_, ec);

    auto target_path = get_path(sha256_hex);
    auto tmp_path = cache_dir_ / (sha256_hex + ".tmp." +
        std::to_string(std::chrono::steady_clock::now().time_since_epoch().count()));

    {
        std::ofstream ofs(tmp_path, std::ios::binary);
        if (!ofs.is_open()) return;
        ofs.write(reinterpret_cast<const char*>(bytecode.data()), bytecode.size());
        ofs.flush();
    }

    std::filesystem::rename(tmp_path, target_path, ec);
}

void BytecodeCache::store_base64(const std::string& sha256_hex, const std::string& base64_str) {
    auto bytes = common::base64_decode(base64_str);
    if (!bytes.empty()) {
        store(sha256_hex, bytes);
    }
}

std::vector<uint8_t> BytecodeCache::get_or_fetch(
    const std::string& sha256_hex,
    const std::string& base64_str,
    const std::string& bytecode_url) {

    if (!sha256_hex.empty() && has(sha256_hex)) {
        return get(sha256_hex);
    }

    if (!base64_str.empty()) {
        auto bytes = common::base64_decode(base64_str);
        if (!bytes.empty()) {
            if (!sha256_hex.empty()) {
                store(sha256_hex, bytes);
            }
            return bytes;
        }
    }

    std::string target_path = bytecode_url;
    if (target_path.empty() && !sha256_hex.empty()) {
        target_path = "/api/v1/bytecode/" + sha256_hex;
    }

    if (!target_path.empty() && !server_endpoint_.empty()) {
        std::string ep = server_endpoint_;
        if (ep.rfind("ws://", 0) == 0) {
            ep = "http://" + ep.substr(5);
        } else if (ep.rfind("wss://", 0) == 0) {
            ep = "https://" + ep.substr(6);
        }
        while (!ep.empty() && ep.back() == '/') {
            ep.pop_back();
        }

        std::string full_url;
        if (target_path.rfind("http://", 0) == 0 || target_path.rfind("https://", 0) == 0) {
            full_url = target_path;
        } else {
            if (target_path.front() != '/') {
                full_url = ep + "/" + target_path;
            } else {
                full_url = ep + target_path;
            }
        }

        ix::HttpClient client;

        ix::SocketTLSOptions tlsOptions;
        if (full_url.rfind("https://", 0) == 0) {
            tlsOptions.tls = true;
            if (!tls_verify_peer_) {
                tlsOptions.caFile = "NONE";
                tlsOptions.disable_hostname_validation = true;
            } else if (!ca_cert_path_.empty()) {
                tlsOptions.caFile = ca_cert_path_;
            }
        }
        client.setTLSOptions(tlsOptions);

        auto args = client.createRequest(full_url, ix::HttpClient::kGet);
        args->connectTimeout = 10;
        args->transferTimeout = 30;

        auto resp = client.get(full_url, args);
        if (resp && resp->statusCode == 200 && !resp->body.empty()) {
            const auto* data = reinterpret_cast<const uint8_t*>(resp->body.data());
            std::vector<uint8_t> bytes(data, data + resp->body.size());
            if (!sha256_hex.empty()) {
                store(sha256_hex, bytes);
            }
            return bytes;
        }
    }

    return {};
}

} // namespace legatus::daemon
