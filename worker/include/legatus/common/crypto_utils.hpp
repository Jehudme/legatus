#pragma once

#include <string>
#include <vector>
#include <cstdint>

namespace legatus::common {

void secure_zero(void* ptr, std::size_t len);

std::string sha256_hex(const std::uint8_t* data, std::size_t len);

std::string format_fingerprint(const std::string& sha256_hex);

std::string derive_visual_code(const std::string& sha256_hex);

} // namespace legatus::common
