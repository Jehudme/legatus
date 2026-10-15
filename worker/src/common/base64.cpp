#include "legatus/common/base64.hpp"
#include <array>
#include <stdexcept>

namespace legatus::common {

static constexpr std::string_view BASE64_CHARS =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    "abcdefghijklmnopqrstuvwxyz"
    "0123456789+/";

std::string base64_encode(const std::uint8_t* data, std::size_t len) {
    std::string out;
    out.reserve(((len + 2) / 3) * 4);

    std::size_t i = 0;
    while (i < len) {
        std::uint32_t octet_a = i < len ? data[i++] : 0;
        std::uint32_t octet_b = i < len ? data[i++] : 0;
        std::uint32_t octet_c = i < len ? data[i++] : 0;

        std::uint32_t triple = (octet_a << 16) + (octet_b << 8) + octet_c;

        out.push_back(BASE64_CHARS[(triple >> 18) & 0x3F]);
        out.push_back(BASE64_CHARS[(triple >> 12) & 0x3F]);
        out.push_back(BASE64_CHARS[(triple >> 6) & 0x3F]);
        out.push_back(BASE64_CHARS[triple & 0x3F]);
    }

    std::size_t mod = len % 3;
    if (mod == 1) {
        out[out.size() - 1] = '=';
        out[out.size() - 2] = '=';
    } else if (mod == 2) {
        out[out.size() - 1] = '=';
    }

    return out;
}

std::vector<std::uint8_t> base64_decode(const std::string& in) {
    std::vector<std::uint8_t> out;
    if (in.empty()) return out;

    std::array<int, 256> table{};
    table.fill(-1);
    for (int i = 0; i < 64; ++i) {
        table[static_cast<unsigned char>(BASE64_CHARS[i])] = i;
    }

    std::uint32_t val = 0;
    int valb = -8;
    for (unsigned char c : in) {
        if (c == '=') break;
        if (table[c] == -1) continue; // skip whitespace / non-base64
        val = (val << 6) + table[c];
        valb += 6;
        if (valb >= 0) {
            out.push_back(static_cast<std::uint8_t>((val >> valb) & 0xFF));
            valb -= 8;
        }
    }
    return out;
}

} // namespace legatus::common
