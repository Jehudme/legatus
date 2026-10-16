#include <catch2/catch_test_macros.hpp>
#include "legatus/stdlib/sandbox.hpp"
#include "legatus/stdlib/solix_memory_helpers.hpp"
#include <solix/runtime.hpp>
#include <solix/native_registry.hpp>
#include <string>

TEST_CASE("Standard Library - JSON Parser and DOM Query Engine", "[stdlib][json]") {
    solix::NativeRegistry registry;
    legatus::stdlib::TaskSandboxContext ctx;
    legatus::stdlib::register_sandboxed_stdlib(registry, ctx);

    auto json_parse = registry.find_function("JsonDocument_native_parse");
    auto json_get_str = registry.find_function("JsonDocument_native_get_string");
    auto json_get_int = registry.find_function("JsonDocument_native_get_int");
    auto json_get_bool = registry.find_function("JsonDocument_native_get_bool");
    auto json_contains = registry.find_function("JsonDocument_native_contains");
    auto json_dispose = registry.find_function("JsonDocument_native_dispose");

    REQUIRE(json_parse != nullptr);
    REQUIRE(json_get_str != nullptr);
    REQUIRE(json_get_int != nullptr);
    REQUIRE(json_get_bool != nullptr);
    REQUIRE(json_contains != nullptr);
    REQUIRE(json_dispose != nullptr);

    solix::RuntimeOptions opts;
    opts.sandboxed = true;
    solix::RuntimeContext vm(opts);

    SECTION("Valid JSON Document Parsing and Field Extraction") {
        std::string sample = R"({
            "service": "legatus-worker",
            "concurrency": 16,
            "enabled": true,
            "metrics": {
                "active": 4
            }
        })";

        uint64_t json_addr = legatus::stdlib::allocate_solix_chars(vm, sample);
        uint64_t args[1] = { json_addr };
        uint64_t handle = json_parse(vm, 0, args, 1);
        REQUIRE(handle != 0);

        // Check contains
        uint64_t service_key_addr = legatus::stdlib::allocate_solix_chars(vm, "service");
        uint64_t c_args[2] = { handle, service_key_addr };
        REQUIRE(json_contains(vm, 0, c_args, 2) == 1);

        uint64_t missing_key_addr = legatus::stdlib::allocate_solix_chars(vm, "non_existent");
        uint64_t c_args2[2] = { handle, missing_key_addr };
        REQUIRE(json_contains(vm, 0, c_args2, 2) == 0);

        // Get string
        uint64_t s_args[2] = { handle, service_key_addr };
        uint64_t val_addr = json_get_str(vm, 0, s_args, 2);
        REQUIRE(legatus::stdlib::read_solix_chars(vm, val_addr) == "legatus-worker");

        // Get int
        uint64_t conc_key_addr = legatus::stdlib::allocate_solix_chars(vm, "concurrency");
        uint64_t i_args[2] = { handle, conc_key_addr };
        REQUIRE(static_cast<int64_t>(json_get_int(vm, 0, i_args, 2)) == 16);

        // Get bool
        uint64_t en_key_addr = legatus::stdlib::allocate_solix_chars(vm, "enabled");
        uint64_t b_args[2] = { handle, en_key_addr };
        REQUIRE(json_get_bool(vm, 0, b_args, 2) == 1);

        // Dispose
        uint64_t d_args[1] = { handle };
        json_dispose(vm, 0, d_args, 1);
    }

    SECTION("Invalid JSON Returns Null Handle") {
        std::string malformed = "{ this is not json: 123 }";
        uint64_t json_addr = legatus::stdlib::allocate_solix_chars(vm, malformed);
        uint64_t args[1] = { json_addr };
        uint64_t handle = json_parse(vm, 0, args, 1);
        REQUIRE(handle == 0);
    }

    SECTION("Deeply Nested JSON Exceeding Depth Guard (32) Is Rejected") {
        std::string deep;
        for (int i = 0; i < 40; ++i) {
            deep += "{\"a\":";
        }
        deep += "1";
        for (int i = 0; i < 40; ++i) {
            deep += "}";
        }

        uint64_t json_addr = legatus::stdlib::allocate_solix_chars(vm, deep);
        uint64_t args[1] = { json_addr };
        uint64_t handle = json_parse(vm, 0, args, 1);
        REQUIRE(handle == 0);
    }
}
