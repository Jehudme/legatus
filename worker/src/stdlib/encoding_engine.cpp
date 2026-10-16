#include "legatus/stdlib/sandbox.hpp"
#include "legatus/stdlib/solix_memory_helpers.hpp"
#include <zlib.h>
#include <string>
#include <vector>
#include <sstream>
#include <iomanip>
#include <cctype>

namespace legatus::stdlib {

namespace {

constexpr size_t MAX_UNCOMPRESSED_CEILING = 16 * 1024 * 1024; // 16MB ceiling
constexpr size_t MAX_EXPANSION_RATIO = 10;                     // 10x max expansion

// URL Encode (RFC 3986)
static uint64_t url_native_encode(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)self; (void)argc;
    std::string value = read_solix_chars(vm, args[0]);

    std::ostringstream escaped;
    escaped.fill('0');
    escaped << std::hex;

    for (char c : value) {
        if (std::isalnum(static_cast<unsigned char>(c)) || c == '-' || c == '_' || c == '.' || c == '~') {
            escaped << c;
        } else {
            escaped << '%' << std::setw(2) << static_cast<int>(static_cast<unsigned char>(c));
        }
    }
    return allocate_solix_chars(vm, escaped.str());
}

// URL Decode (RFC 3986)
static uint64_t url_native_decode(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)self; (void)argc;
    std::string value = read_solix_chars(vm, args[0]);

    std::string decoded;
    decoded.reserve(value.size());

    for (size_t i = 0; i < value.size(); ++i) {
        if (value[i] == '%' && i + 2 < value.size()) {
            int h1 = value[i + 1];
            int h2 = value[i + 2];
            auto from_hex = [](char c) -> int {
                if (c >= '0' && c <= '9') return c - '0';
                if (c >= 'a' && c <= 'f') return c - 'a' + 10;
                if (c >= 'A' && c <= 'F') return c - 'A' + 10;
                return -1;
            };
            int d1 = from_hex(static_cast<char>(h1));
            int d2 = from_hex(static_cast<char>(h2));
            if (d1 >= 0 && d2 >= 0) {
                decoded.push_back(static_cast<char>((d1 << 4) | d2));
                i += 2;
                continue;
            }
        }
        if (value[i] == '+') {
            decoded.push_back(' ');
        } else {
            decoded.push_back(value[i]);
        }
    }
    return allocate_solix_chars(vm, decoded);
}

// Gzip/Zlib compress
static uint64_t compress_native_deflate(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)self; (void)argc;
    auto input = read_solix_bytes(vm, args[0]);
    if (input.empty()) {
        return allocate_solix_bytes(vm, nullptr, 0);
    }

    uLongf dest_len = compressBound(static_cast<uLong>(input.size()));
    std::vector<uint8_t> output(dest_len);

    int rc = compress(output.data(), &dest_len, input.data(), static_cast<uLong>(input.size()));
    if (rc != Z_OK) {
        return 0ULL;
    }
    return allocate_solix_bytes(vm, output.data(), dest_len);
}

// Gzip/Zlib decompress with zip bomb guard
static uint64_t compress_native_inflate(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)self; (void)argc;
    auto input = read_solix_bytes(vm, args[0]);
    if (input.empty()) {
        return allocate_solix_bytes(vm, nullptr, 0);
    }

    size_t max_allowed_bytes = std::min(
        std::max<size_t>(64 * 1024, input.size() * MAX_EXPANSION_RATIO),
        MAX_UNCOMPRESSED_CEILING
    );

    z_stream strm;
    std::memset(&strm, 0, sizeof(strm));
    if (inflateInit(&strm) != Z_OK) {
        return 0ULL;
    }

    strm.avail_in = static_cast<uInt>(input.size());
    strm.next_in = input.data();

    std::vector<uint8_t> output;
    uint8_t chunk[16384];

    int rc;
    do {
        strm.avail_out = sizeof(chunk);
        strm.next_out = chunk;
        rc = inflate(&strm, Z_NO_FLUSH);
        if (rc != Z_OK && rc != Z_STREAM_END && rc != Z_BUF_ERROR) {
            inflateEnd(&strm);
            return 0ULL;
        }

        size_t written = sizeof(chunk) - strm.avail_out;
        if (output.size() + written > max_allowed_bytes) {
            // Zip bomb detected: exceeded expansion ratio or ceiling
            inflateEnd(&strm);
            return 0ULL;
        }
        output.insert(output.end(), chunk, chunk + written);
    } while (rc != Z_STREAM_END && strm.avail_out == 0);

    inflateEnd(&strm);
    return allocate_solix_bytes(vm, output.data(), output.size());
}

} // namespace

void register_encoding_natives(solix::NativeRegistry &registry) {
    auto reg = [&](const std::string &cls, const std::string &name, solix::NativeFunctionPtr func) {
        registry.register_function(cls + "_" + name, func);
        registry.register_function("legatus_encoding_" + cls + "_" + name, func);
        registry.register_function("legatus.encoding." + cls + "." + name, func);
    };

    reg("UrlEncoding", "native_encode", url_native_encode);
    reg("UrlEncoding", "native_decode", url_native_decode);
    reg("Compression", "native_deflate", compress_native_deflate);
    reg("Compression", "native_inflate", compress_native_inflate);
}

} // namespace legatus::stdlib
