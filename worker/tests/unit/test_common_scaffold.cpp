#include "legatus/version.hpp"
#include "legatus/common/crypto_utils.hpp"
#include <solix/runtime.hpp>
#include <catch2/catch_all.hpp>
#include <vector>

TEST_CASE("WorkerScaffold - Common Utilities & Solix Linkage", "[worker][scaffold]") {

    SECTION("Version string is valid") {
        REQUIRE(legatus::VERSION == "1.0.0-worker-mvp");
        REQUIRE(legatus::VERSION_MAJOR == 1);
        REQUIRE(legatus::VERSION_MINOR == 0);
        REQUIRE(legatus::VERSION_PATCH == 0);
    }

    SECTION("SHA-256 and visual fingerprint derivation") {
        const std::string input = "hello legatus worker";
        std::string hash = legatus::common::sha256_hex(
            reinterpret_cast<const std::uint8_t*>(input.data()), input.size());
        REQUIRE(hash.length() == 64);

        std::string fingerprint = legatus::common::format_fingerprint(hash);
        REQUIRE(!fingerprint.empty());
        REQUIRE(fingerprint.find(':') != std::string::npos);

        std::string visual_code = legatus::common::derive_visual_code(hash);
        REQUIRE(!visual_code.empty());
        REQUIRE(visual_code.find('-') != std::string::npos);
    }

    SECTION("Secure zero clears memory buffers") {
        std::vector<std::uint8_t> buffer = {0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF};
        legatus::common::secure_zero(buffer.data(), buffer.size());
        for (auto b : buffer) {
            REQUIRE(b == 0);
        }
    }

    SECTION("Solix runtime options can be configured with v1.1.1 fields") {
        solix::RuntimeOptions opts;
        opts.stack_capacity = 1024 * 1024;
        opts.heap_capacity = 64 * 1024 * 1024;
        opts.instruction_budget = 5000;
        opts.sandboxed = true;

        REQUIRE(opts.instruction_budget == 5000);
        REQUIRE(opts.sandboxed == true);
    }
}
