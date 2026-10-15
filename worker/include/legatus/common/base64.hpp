#pragma once

#include <string>
#include <vector>
#include <cstdint>

namespace legatus::common {

std::string base64_encode(const std::uint8_t* data, std::size_t len);
inline std::string base64_encode(const std::vector<std::uint8_t>& data) {
    return base64_encode(data.data(), data.size());
}

std::vector<std::uint8_t> base64_decode(const std::string& in);

} // namespace legatus::common
