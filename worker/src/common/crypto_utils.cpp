#include "legatus/common/crypto_utils.hpp"
#include <openssl/evp.h>
#include <iomanip>
#include <sstream>
#include <cstring>
#include <array>

#if defined(_WIN32)
#include <windows.h>
#endif

namespace legatus::common {

void secure_zero(void* ptr, std::size_t len) {
    if (!ptr || len == 0) return;
#if defined(_WIN32)
    SecureZeroMemory(ptr, len);
#elif defined(__GLIBC__) || defined(__FreeBSD__) || defined(__OpenBSD__)
    explicit_bzero(ptr, len);
#else
    volatile unsigned char* p = static_cast<volatile unsigned char*>(ptr);
    while (len--) {
        *p++ = 0;
    }
#endif
}

std::string sha256_hex(const std::uint8_t* data, std::size_t len) {
    std::array<unsigned char, 32> hash{};
    unsigned int hash_len = 0;

    EVP_MD_CTX* ctx = EVP_MD_CTX_new();
    if (ctx) {
        if (EVP_DigestInit_ex(ctx, EVP_sha256(), nullptr) &&
            EVP_DigestUpdate(ctx, data, len) &&
            EVP_DigestFinal_ex(ctx, hash.data(), &hash_len)) {
            // success
        }
        EVP_MD_CTX_free(ctx);
    }

    std::ostringstream oss;
    for (unsigned int i = 0; i < hash_len; ++i) {
        oss << std::hex << std::setw(2) << std::setfill('0') << static_cast<int>(hash[i]);
    }
    return oss.str();
}

std::string format_fingerprint(const std::string& sha256_hex_str) {
    std::string result;
    for (std::size_t i = 0; i < sha256_hex_str.length(); i += 2) {
        if (!result.empty()) {
            result += ':';
        }
        result += static_cast<char>(std::toupper(sha256_hex_str[i]));
        if (i + 1 < sha256_hex_str.length()) {
            result += static_cast<char>(std::toupper(sha256_hex_str[i + 1]));
        }
    }
    return result;
}

static constexpr std::array<const char*, 16> WORDLIST_A = {
    "Lion", "Tiger", "Falcon", "Eagle", "Wolf", "Bear", "Panther", "Viper",
    "Hawk", "Shark", "Bison", "Cobra", "Fox", "Lynx", "Raven", "Stag"
};

static constexpr std::array<const char*, 16> WORDLIST_B = {
    "Nebula", "Galaxy", "Cosmos", "Star", "Orbit", "Pulsar", "Quasar", "Comet",
    "Meteor", "Solar", "Lunar", "Aurora", "Eclipse", "Horizon", "Zenith", "Vortex"
};

std::string derive_visual_code(const std::string& sha256_hex_str) {
    if (sha256_hex_str.length() < 8) {
        return "Unknown-Unknown-00";
    }
    std::uint32_t val1 = static_cast<std::uint32_t>(std::stoul(sha256_hex_str.substr(0, 4), nullptr, 16));
    std::uint32_t val2 = static_cast<std::uint32_t>(std::stoul(sha256_hex_str.substr(4, 4), nullptr, 16));
    std::uint32_t num = (val1 ^ val2) % 100;

    const char* word1 = WORDLIST_A[val1 % WORDLIST_A.size()];
    const char* word2 = WORDLIST_B[val2 % WORDLIST_B.size()];

    std::ostringstream oss;
    oss << word1 << "-" << word2 << "-" << std::setw(2) << std::setfill('0') << num;
    return oss.str();
}

} // namespace legatus::common
