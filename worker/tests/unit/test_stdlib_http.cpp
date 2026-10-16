#include <catch2/catch_test_macros.hpp>
#include "legatus/stdlib/sandbox.hpp"
#include "legatus/stdlib/solix_memory_helpers.hpp"
#include <solix/runtime.hpp>
#include <solix/native_registry.hpp>
#include <nlohmann/json.hpp>

TEST_CASE("Standard Library - SSRF Guardrails and Egress Security", "[stdlib][http][ssrf]") {
    solix::NativeRegistry registry;
    legatus::stdlib::TaskSandboxContext ctx;
    legatus::stdlib::register_sandboxed_stdlib(registry, ctx);

    auto http_send = registry.find_function("HttpClient_native_send");
    REQUIRE(http_send != nullptr);

    solix::RuntimeOptions opts;
    opts.sandboxed = true;
    solix::RuntimeContext vm(opts);

    auto test_url_blocked = [&](const std::string& url) {
        uint64_t method_addr = legatus::stdlib::allocate_solix_chars(vm, "GET");
        uint64_t url_addr = legatus::stdlib::allocate_solix_chars(vm, url);
        uint64_t headers_addr = legatus::stdlib::allocate_solix_chars(vm, "{}");
        uint64_t body_addr = legatus::stdlib::allocate_solix_chars(vm, "");

        uint64_t args[4] = { method_addr, url_addr, headers_addr, body_addr };
        uint64_t res_addr = http_send(vm, 0, args, 4);

        std::string res_json_str = legatus::stdlib::read_solix_chars(vm, res_addr);
        auto j = nlohmann::json::parse(res_json_str);

        REQUIRE(j.contains("status"));
        REQUIRE(j["status"].get<int>() == 403);
        REQUIRE(j.contains("error"));
        REQUIRE(j["error"].get<std::string>().find("SSRF") != std::string::npos);
    };

    SECTION("Blocks IPv4 Loopback Addresses") {
        test_url_blocked("http://127.0.0.1:8080/admin");
        test_url_blocked("http://127.0.0.2:9000/info");
    }

    SECTION("Blocks Localhost Domain Resolution") {
        test_url_blocked("http://localhost:3000/secret");
    }

    SECTION("Blocks RFC 1918 Private IPv4 Addresses") {
        test_url_blocked("http://10.0.0.1/metadata");
        test_url_blocked("http://172.16.0.5:8000/api");
        test_url_blocked("http://192.168.1.1:80/status");
    }

    SECTION("Blocks Cloud Metadata IP (169.254.169.254)") {
        test_url_blocked("http://169.254.169.254/latest/meta-data/");
        test_url_blocked("http://169.254.1.1/");
    }

    SECTION("Malformed URL Without Host Returns Error") {
        uint64_t method_addr = legatus::stdlib::allocate_solix_chars(vm, "GET");
        uint64_t url_addr = legatus::stdlib::allocate_solix_chars(vm, "not_a_valid_url");
        uint64_t headers_addr = legatus::stdlib::allocate_solix_chars(vm, "{}");
        uint64_t body_addr = legatus::stdlib::allocate_solix_chars(vm, "");

        uint64_t args[4] = { method_addr, url_addr, headers_addr, body_addr };
        uint64_t res_addr = http_send(vm, 0, args, 4);

        std::string res_json_str = legatus::stdlib::read_solix_chars(vm, res_addr);
        auto j = nlohmann::json::parse(res_json_str);

        REQUIRE(j.contains("error"));
    }

    SECTION("DNS Rebinding & SSRF: IPv6 link-local and mapped IPv4 blocked") {
        test_url_blocked("http://[fe80::1]:8080/secret");
        test_url_blocked("http://[::ffff:127.0.0.1]:8080/admin");
        test_url_blocked("http://[::ffff:169.254.169.254]/latest");
    }
}
