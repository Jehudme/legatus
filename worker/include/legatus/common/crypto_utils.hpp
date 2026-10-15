#pragma once

#include <string>
#include <vector>
#include <cstdint>

namespace legatus::common {

struct Ed25519KeyPair {
    std::string public_key_base64;
    std::string private_key_base64;
    std::string fingerprint; // Formatted 8-byte hex: "XX:XX:XX:XX:XX:XX:XX:XX"
    std::string visual_code; // "Word1-Word2-NN"
};

void secure_zero(void* ptr, std::size_t len);

std::string sha256_hex(const std::uint8_t* data, std::size_t len);

// Format first num_bytes (default 8) of hex digest as uppercase colon-separated string.
// If num_bytes is 0, formats all bytes in the string.
std::string format_fingerprint(const std::string& sha256_hex, std::size_t num_bytes = 8);

std::string derive_visual_code(const std::string& sha256_hex);

// Generates a cryptographically secure Ed25519 keypair using OpenSSL 3.0 EVP_PKEY_ED25519.
// Scrubs ephemeral private key memory prior to return.
Ed25519KeyPair generate_ed25519_keypair();

} // namespace legatus::common
