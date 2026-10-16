#include <catch2/catch_test_macros.hpp>
#include "legatus/stdlib/sandbox.hpp"
#include <solix/native_registry.hpp>

TEST_CASE("Standard Library - Sandbox Invariants and Ban Policy", "[stdlib][sandbox]") {
    solix::NativeRegistry registry;
    legatus::stdlib::TaskSandboxContext ctx;
    ctx.task_id = "task-test-123";
    ctx.function_name = "test_func";
    ctx.instruction_budget = 500000;
    ctx.secrets["API_KEY"] = "super-secret-token";
    ctx.env_vars["TENANT_ID"] = "tenant-42";

    legatus::stdlib::register_sandboxed_stdlib(registry, ctx);

    SECTION("Prohibited Capabilities Are Permanently Banned and Omitted") {
        // Assert solix.system.Process is NOT registered
        REQUIRE(registry.find_function("Process_native_start") == nullptr);
        REQUIRE(registry.find_function("solix_system_Process_native_start") == nullptr);
        REQUIRE(registry.find_function("solix.system.Process.native_start") == nullptr);

        // Assert solix.io.filesystem and FileStream are NOT registered
        REQUIRE(registry.find_function("File_native_read_text") == nullptr);
        REQUIRE(registry.find_function("File_native_write_text") == nullptr);
        REQUIRE(registry.find_function("Directory_native_create_directory") == nullptr);
        REQUIRE(registry.find_function("Path_native_combine") == nullptr);
        REQUIRE(registry.find_function("FileStream_native_open") == nullptr);
        REQUIRE(registry.find_function("solix_io_FileStream_native_open") == nullptr);
    }

    SECTION("Inherited Safe Solix Sublibraries Are Registered") {
        // Core String and Primitives
        REQUIRE(registry.find_function("String_native_from_int") != nullptr);
        REQUIRE(registry.find_function("Double_native_min_value") != nullptr);

        // Math
        REQUIRE(registry.find_function("Math_native_sin") != nullptr);
        REQUIRE(registry.find_function("Math_native_sqrt") != nullptr);

        // Time
        REQUIRE(registry.find_function("DateTime_native_system_millis") != nullptr);

        // Crypto
        REQUIRE(registry.find_function("Hash_native_sha256") != nullptr);
        REQUIRE(registry.find_function("Base64_native_encode") != nullptr);

        // Console
        REQUIRE(registry.find_function("Console_native_print_string") != nullptr);
    }

    SECTION("New Serverless Extensions Are Registered") {
        // HTTP
        REQUIRE(registry.find_function("HttpClient_native_send") != nullptr);

        // JSON
        REQUIRE(registry.find_function("JsonDocument_native_parse") != nullptr);

        // TaskContext
        REQUIRE(registry.find_function("TaskContext_native_task_id") != nullptr);
        REQUIRE(registry.find_function("TaskContext_native_get_secret") != nullptr);

        // Extended Crypto
        REQUIRE(registry.find_function("Hmac_native_sha256") != nullptr);
        REQUIRE(registry.find_function("AesGcm_native_encrypt") != nullptr);
        REQUIRE(registry.find_function("Ed25519_native_verify") != nullptr);

        // Regex
        REQUIRE(registry.find_function("Regex_native_is_match") != nullptr);

        // Encodings & UUID
        REQUIRE(registry.find_function("UrlEncoding_native_encode") != nullptr);
        REQUIRE(registry.find_function("Uuid_native_random") != nullptr);

        // EphemeralKV
        REQUIRE(registry.find_function("EphemeralKV_native_set") != nullptr);
    }
}
