#include "legatus/stdlib/sandbox.hpp"
#include "legatus/stdlib/solix_memory_helpers.hpp"
#include <curl/curl.h>
#include <nlohmann/json.hpp>
#include <iostream>
#include <string>
#include <vector>
#include <cstring>

#if defined(_WIN32)
#include <ws2tcpip.h>
#else
#include <sys/types.h>
#include <sys/socket.h>
#include <netdb.h>
#include <arpa/inet.h>
#endif

namespace legatus::stdlib {

namespace {

constexpr size_t MAX_RESPONSE_BYTES = 10 * 1024 * 1024; // 10 MB ceiling

bool is_ip_blocked(const struct sockaddr* sa) {
    if (sa->sa_family == AF_INET) {
        auto* sin = reinterpret_cast<const struct sockaddr_in*>(sa);
        uint32_t ip = ntohl(sin->sin_addr.s_addr);

        uint8_t b1 = (ip >> 24) & 0xFF;
        uint8_t b2 = (ip >> 16) & 0xFF;

        // Loopback 127.0.0.0/8
        if (b1 == 127) return true;
        // 0.0.0.0/8
        if (b1 == 0) return true;
        // RFC 1918: 10.0.0.0/8
        if (b1 == 10) return true;
        // RFC 1918: 172.16.0.0/12 (172.16.0.0 - 172.31.255.255)
        if (b1 == 172 && (b2 >= 16 && b2 <= 31)) return true;
        // RFC 1918: 192.168.0.0/16
        if (b1 == 192 && b2 == 168) return true;
        // Link-local & cloud metadata: 169.254.0.0/16
        if (b1 == 169 && b2 == 254) return true;
        // Multicast 224.0.0.0/4 and Future use 240.0.0.0/4
        if (b1 >= 224) return true;

        return false;
    } else if (sa->sa_family == AF_INET6) {
        auto* sin6 = reinterpret_cast<const struct sockaddr_in6*>(sa);
        const uint8_t* b = sin6->sin6_addr.s6_addr;

        // Loopback ::1
        bool is_loopback = true;
        for (int i = 0; i < 15; ++i) {
            if (b[i] != 0) is_loopback = false;
        }
        if (is_loopback && b[15] == 1) return true;

        // Unspecified ::
        bool is_unspec = true;
        for (int i = 0; i < 16; ++i) {
            if (b[i] != 0) is_unspec = false;
        }
        if (is_unspec) return true;

        // Unique local fc00::/7 (0xfc or 0xfd)
        if ((b[0] & 0xFE) == 0xFC) return true;
        // Link-local fe80::/10 (0xfe80 - 0xfebf)
        if (b[0] == 0xFE && (b[1] & 0xC0) == 0x80) return true;
        // Multicast ff00::/8
        if (b[0] == 0xFF) return true;

        // IPv4-mapped IPv6 ::ffff:a.b.c.d
        bool is_mapped = true;
        for (int i = 0; i < 10; ++i) {
            if (b[i] != 0) is_mapped = false;
        }
        if (is_mapped && b[10] == 0xFF && b[11] == 0xFF) {
            struct sockaddr_in sin4;
            std::memset(&sin4, 0, sizeof(sin4));
            sin4.sin_family = AF_INET;
            std::memcpy(&sin4.sin_addr.s_addr, &b[12], 4);
            return is_ip_blocked(reinterpret_cast<const struct sockaddr*>(&sin4));
        }

        return false;
    }
    return true; // Unknown family -> block by default
}

bool validate_ssrf_host(const std::string& host, std::string& err_msg) {
    struct addrinfo hints;
    std::memset(&hints, 0, sizeof(hints));
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;

    struct addrinfo* res = nullptr;
    int rc = getaddrinfo(host.c_str(), nullptr, &hints, &res);
    if (rc != 0 || !res) {
        err_msg = "DNS resolution failed for host: " + host;
        return false;
    }

    bool blocked = false;
    for (struct addrinfo* p = res; p != nullptr; p = p->ai_next) {
        if (is_ip_blocked(p->ai_addr)) {
            blocked = true;
            break;
        }
    }

    freeaddrinfo(res);
    if (blocked) {
        err_msg = "Egress to restricted IP or cloud metadata blocked by SSRF policy (host: " + host + ")";
        return false;
    }
    return true;
}

std::string extract_host_from_url(const std::string& url) {
    size_t start = url.find("://");
    if (start == std::string::npos) {
        start = 0;
    } else {
        start += 3;
    }

    size_t end = url.find_first_of("/?#:", start);
    if (end == std::string::npos) {
        return url.substr(start);
    }
    return url.substr(start, end - start);
}

struct MemoryBuffer {
    std::string data;
    bool overflow = false;
};

size_t write_callback(void* contents, size_t size, size_t nmemb, void* userp) {
    size_t total = size * nmemb;
    auto* mem = static_cast<MemoryBuffer*>(userp);
    if (mem->data.size() + total > MAX_RESPONSE_BYTES) {
        mem->overflow = true;
        return 0; // Trigger abort in curl
    }
    mem->data.append(static_cast<char*>(contents), total);
    return total;
}

static uint64_t http_native_send(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)self;
    if (argc < 4) {
        nlohmann::json err = {{"error", "Invalid arguments to http_native_send"}, {"status", 0}};
        return allocate_solix_chars(vm, err.dump());
    }

    std::string method = read_solix_chars(vm, args[0]);
    std::string url = read_solix_chars(vm, args[1]);
    std::string headers_json_str = read_solix_chars(vm, args[2]);
    std::string body = read_solix_chars(vm, args[3]);

    std::string host = extract_host_from_url(url);
    if (host.empty()) {
        nlohmann::json err = {{"error", "Malformed URL: missing host"}, {"status", 0}};
        return allocate_solix_chars(vm, err.dump());
    }

    std::string ssrf_err;
    if (!validate_ssrf_host(host, ssrf_err)) {
        nlohmann::json err = {{"error", ssrf_err}, {"status", 403}};
        return allocate_solix_chars(vm, err.dump());
    }

    CURL* curl = curl_easy_init();
    if (!curl) {
        nlohmann::json err = {{"error", "Failed to initialize libcurl"}, {"status", 500}};
        return allocate_solix_chars(vm, err.dump());
    }

    MemoryBuffer response_buffer;
    struct curl_slist* chunk = nullptr;

    if (!headers_json_str.empty()) {
        try {
            auto j = nlohmann::json::parse(headers_json_str);
            if (j.is_object()) {
                for (auto& [k, v] : j.items()) {
                    std::string header_line = k + ": " + v.get<std::string>();
                    chunk = curl_slist_append(chunk, header_line.c_str());
                }
            }
        } catch (...) {}
    }

    curl_easy_setopt(curl, CURLOPT_URL, url.c_str());
    curl_easy_setopt(curl, CURLOPT_CUSTOMREQUEST, method.c_str());
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, write_callback);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &response_buffer);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT_MS, 15000L); // 15s read timeout
    curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT_MS, 5000L); // 5s connect timeout
    curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 0L); // Disable redirects to prevent SSRF redirect bypass

    if (chunk) {
        curl_easy_setopt(curl, CURLOPT_HTTPHEADER, chunk);
    }

    if (!body.empty() && (method == "POST" || method == "PUT" || method == "PATCH")) {
        curl_easy_setopt(curl, CURLOPT_POSTFIELDS, body.c_str());
        curl_easy_setopt(curl, CURLOPT_POSTFIELDSIZE, body.size());
    }

    CURLcode res = curl_easy_perform(curl);
    long http_code = 0;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &http_code);

    if (chunk) curl_slist_free_all(chunk);
    curl_easy_cleanup(curl);

    nlohmann::json result;
    if (response_buffer.overflow) {
        result["error"] = "HTTP response exceeded 10MB payload ceiling";
        result["status"] = 413;
    } else if (res != CURLE_OK) {
        result["error"] = std::string("Curl request failed: ") + curl_easy_strerror(res);
        result["status"] = 0;
    } else {
        result["status"] = http_code;
        result["body"] = response_buffer.data;
    }

    return allocate_solix_chars(vm, result.dump());
}

} // namespace

void register_http_natives(solix::NativeRegistry &registry) {
    auto reg = [&](const std::string &cls, const std::string &name, solix::NativeFunctionPtr func) {
        registry.register_function(cls + "_" + name, func);
        registry.register_function("legatus_net_http_" + cls + "_" + name, func);
        registry.register_function("legatus.net.http." + cls + "." + name, func);
    };

    reg("WebRequest", "native_send", http_native_send);
    reg("HttpClient", "native_send", http_native_send);
}

} // namespace legatus::stdlib
