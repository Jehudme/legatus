#include <catch2/catch_test_macros.hpp>
#include "legatus/stdlib/sandbox.hpp"
#include "legatus/stdlib/solix_memory_helpers.hpp"
#include <solix/runtime.hpp>
#include <solix/native_registry.hpp>
#include <chrono>

TEST_CASE("Standard Library - Re2 Linear-Time Regex Engine", "[stdlib][regex]") {
    solix::NativeRegistry registry;
    legatus::stdlib::TaskSandboxContext ctx;
    legatus::stdlib::register_sandboxed_stdlib(registry, ctx);

    auto regex_match = registry.find_function("Regex_native_is_match");
    auto regex_replace = registry.find_function("Regex_native_replace");
    auto regex_extract = registry.find_function("Regex_native_extract");

    REQUIRE(regex_match != nullptr);
    REQUIRE(regex_replace != nullptr);
    REQUIRE(regex_extract != nullptr);

    solix::RuntimeOptions opts;
    opts.sandboxed = true;
    solix::RuntimeContext vm(opts);

    SECTION("Basic Matching and Replacement") {
        uint64_t pat_addr = legatus::stdlib::allocate_solix_chars(vm, R"(\d{3}-\d{4})");
        uint64_t text_addr = legatus::stdlib::allocate_solix_chars(vm, "Worker phone: 555-0199");

        uint64_t args[2] = { pat_addr, text_addr };
        REQUIRE(regex_match(vm, 0, args, 2) == 1);

        uint64_t bad_text_addr = legatus::stdlib::allocate_solix_chars(vm, "Worker phone: invalid");
        uint64_t bad_args[2] = { pat_addr, bad_text_addr };
        REQUIRE(regex_match(vm, 0, bad_args, 2) == 0);

        // Replace
        uint64_t repl_addr = legatus::stdlib::allocate_solix_chars(vm, "[REDACTED]");
        uint64_t r_args[3] = { pat_addr, text_addr, repl_addr };
        uint64_t res_addr = regex_replace(vm, 0, r_args, 3);
        REQUIRE(legatus::stdlib::read_solix_chars(vm, res_addr) == "Worker phone: [REDACTED]");

        // Extract
        uint64_t ext_pat_addr = legatus::stdlib::allocate_solix_chars(vm, R"((\d{3}-\d{4}))");
        uint64_t ext_args[2] = { ext_pat_addr, text_addr };
        uint64_t ext_res = regex_extract(vm, 0, ext_args, 2);
        REQUIRE(legatus::stdlib::read_solix_chars(vm, ext_res) == "555-0199");
    }

    SECTION("ReDoS Immunity Against Catastrophic Backtracking Patterns") {
        // Pattern with nested quantifiers: (a+)+$
        std::string evil_pattern = "^(a+)+$";
        // String of 25 'a's ending with 'b' (would cause exponential catastrophe in backtracking regex)
        std::string evil_input(25, 'a');
        evil_input += 'b';

        uint64_t pat_addr = legatus::stdlib::allocate_solix_chars(vm, evil_pattern);
        uint64_t text_addr = legatus::stdlib::allocate_solix_chars(vm, evil_input);
        uint64_t args[2] = { pat_addr, text_addr };

        auto start = std::chrono::steady_clock::now();
        uint64_t matched = regex_match(vm, 0, args, 2);
        auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - start).count();

        REQUIRE(matched == 0);
        // RE2 guarantees linear time O(N), so this should finish in < 50ms
        REQUIRE(elapsed < 100);
    }
}
