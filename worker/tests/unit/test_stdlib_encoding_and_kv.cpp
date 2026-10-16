#include <catch2/catch_test_macros.hpp>
#include "legatus/stdlib/sandbox.hpp"
#include "legatus/stdlib/solix_memory_helpers.hpp"
#include <solix/runtime.hpp>
#include <solix/native_registry.hpp>
#include <string>
#include <vector>

TEST_CASE("Standard Library - Encodings, UUID, KV, and Context", "[stdlib][encoding][kv][context]") {
    solix::NativeRegistry registry;
    legatus::stdlib::TaskSandboxContext ctx;
    ctx.task_id = "task-uuid-8888";
    ctx.function_name = "process_order";
    ctx.instruction_budget = 250000;
    ctx.secrets["STRIPE_KEY"] = "sk_live_123456789";
    ctx.env_vars["REGION"] = "us-east-1";

    legatus::stdlib::register_sandboxed_stdlib(registry, ctx);

    solix::RuntimeOptions opts;
    opts.sandboxed = true;
    solix::RuntimeContext vm(opts);

    SECTION("URL Encoding and Decoding (RFC 3986)") {
        auto url_encode = registry.find_function("UrlEncoding_native_encode");
        auto url_decode = registry.find_function("UrlEncoding_native_decode");
        REQUIRE(url_encode != nullptr);
        REQUIRE(url_decode != nullptr);

        std::string raw = "hello world! / foo & bar = 42";
        uint64_t raw_addr = legatus::stdlib::allocate_solix_chars(vm, raw);

        uint64_t enc_args[1] = { raw_addr };
        uint64_t enc_res = url_encode(vm, 0, enc_args, 1);
        std::string encoded = legatus::stdlib::read_solix_chars(vm, enc_res);
        REQUIRE(encoded.find("%20") != std::string::npos);

        uint64_t dec_args[1] = { enc_res };
        uint64_t dec_res = url_decode(vm, 0, dec_args, 1);
        std::string decoded = legatus::stdlib::read_solix_chars(vm, dec_res);
        REQUIRE(decoded == raw);
    }

    SECTION("Zlib Compression and Zip Bomb Protection") {
        auto deflate = registry.find_function("Compression_native_deflate");
        auto inflate = registry.find_function("Compression_native_inflate");
        REQUIRE(deflate != nullptr);
        REQUIRE(inflate != nullptr);

        std::string payload = "Legatus sandboxed worker pure in-memory execution payload with redundant content!";
        for (int i = 0; i < 5; ++i) payload += payload;

        uint64_t payload_addr = legatus::stdlib::allocate_solix_bytes(vm, reinterpret_cast<const uint8_t*>(payload.data()), payload.size());
        uint64_t c_args[1] = { payload_addr };
        uint64_t comp_addr = deflate(vm, 0, c_args, 1);
        REQUIRE(comp_addr != 0);

        auto comp_bytes = legatus::stdlib::read_solix_bytes(vm, comp_addr);
        REQUIRE(comp_bytes.size() < payload.size());

        // Decompress
        uint64_t d_args[1] = { comp_addr };
        uint64_t decomp_addr = inflate(vm, 0, d_args, 1);
        REQUIRE(decomp_addr != 0);

        auto decomp_bytes = legatus::stdlib::read_solix_bytes(vm, decomp_addr);
        std::string decomp_str(decomp_bytes.begin(), decomp_bytes.end());
        REQUIRE(decomp_str == payload);
    }

    SECTION("UUID v4 Generation and Validation") {
        auto uuid_random = registry.find_function("Uuid_native_random");
        auto uuid_is_valid = registry.find_function("Uuid_native_is_valid");
        REQUIRE(uuid_random != nullptr);
        REQUIRE(uuid_is_valid != nullptr);

        uint64_t res = uuid_random(vm, 0, nullptr, 0);
        std::string u1 = legatus::stdlib::read_solix_chars(vm, res);

        REQUIRE(u1.size() == 36);
        REQUIRE(u1[8] == '-');
        REQUIRE(u1[13] == '-');
        REQUIRE(u1[18] == '-');
        REQUIRE(u1[23] == '-');
        REQUIRE(u1[14] == '4'); // Version 4

        uint64_t u1_addr = legatus::stdlib::allocate_solix_chars(vm, u1);
        uint64_t v_args[1] = { u1_addr };
        REQUIRE(uuid_is_valid(vm, 0, v_args, 1) == 1);

        uint64_t invalid_addr = legatus::stdlib::allocate_solix_chars(vm, "not-a-valid-uuid");
        uint64_t bad_args[1] = { invalid_addr };
        REQUIRE(uuid_is_valid(vm, 0, bad_args, 1) == 0);
    }

    SECTION("EphemeralKV In-Memory Operations with TTL") {
        auto kv_set = registry.find_function("EphemeralKV_native_set");
        auto kv_get = registry.find_function("EphemeralKV_native_get");
        auto kv_has = registry.find_function("EphemeralKV_native_has");
        auto kv_del = registry.find_function("EphemeralKV_native_delete");
        REQUIRE(kv_set != nullptr);
        REQUIRE(kv_get != nullptr);
        REQUIRE(kv_has != nullptr);
        REQUIRE(kv_del != nullptr);

        uint64_t k1 = legatus::stdlib::allocate_solix_chars(vm, "session:1001");
        uint64_t v1 = legatus::stdlib::allocate_solix_chars(vm, "auth_token_value");
        uint64_t ttl = 3600; // 1 hour

        uint64_t set_args[3] = { k1, v1, ttl };
        REQUIRE(kv_set(vm, 0, set_args, 3) == 1);

        uint64_t get_args[1] = { k1 };
        REQUIRE(kv_has(vm, 0, get_args, 1) == 1);

        uint64_t retrieved_addr = kv_get(vm, 0, get_args, 1);
        REQUIRE(legatus::stdlib::read_solix_chars(vm, retrieved_addr) == "auth_token_value");

        // Delete
        REQUIRE(kv_del(vm, 0, get_args, 1) == 1);
        REQUIRE(kv_has(vm, 0, get_args, 1) == 0);
        REQUIRE(kv_get(vm, 0, get_args, 1) == 0);
    }

    SECTION("TaskContext and Environment Sandboxing") {
        auto task_id_fn = registry.find_function("TaskContext_native_task_id");
        auto fn_name_fn = registry.find_function("TaskContext_native_function_name");
        auto budget_fn = registry.find_function("TaskContext_native_instruction_budget");
        auto secret_fn = registry.find_function("TaskContext_native_get_secret");
        auto env_get_fn = registry.find_function("Environment_native_get_env");
        auto env_has_fn = registry.find_function("Environment_native_has_env");

        REQUIRE(task_id_fn != nullptr);
        REQUIRE(fn_name_fn != nullptr);
        REQUIRE(budget_fn != nullptr);
        REQUIRE(secret_fn != nullptr);
        REQUIRE(env_get_fn != nullptr);
        REQUIRE(env_has_fn != nullptr);

        // Verify task context values
        REQUIRE(legatus::stdlib::read_solix_chars(vm, task_id_fn(vm, 0, nullptr, 0)) == "task-uuid-8888");
        REQUIRE(legatus::stdlib::read_solix_chars(vm, fn_name_fn(vm, 0, nullptr, 0)) == "process_order");
        REQUIRE(budget_fn(vm, 0, nullptr, 0) == 250000);

        // Verify secret lookup
        uint64_t sk_key = legatus::stdlib::allocate_solix_chars(vm, "STRIPE_KEY");
        uint64_t sk_args[1] = { sk_key };
        REQUIRE(legatus::stdlib::read_solix_chars(vm, secret_fn(vm, 0, sk_args, 1)) == "sk_live_123456789");

        uint64_t missing_key = legatus::stdlib::allocate_solix_chars(vm, "AWS_SECRET_ACCESS_KEY");
        uint64_t missing_args[1] = { missing_key };
        REQUIRE(secret_fn(vm, 0, missing_args, 1) == 0); // null

        // Verify hermetic environment
        uint64_t reg_key = legatus::stdlib::allocate_solix_chars(vm, "REGION");
        uint64_t reg_args[1] = { reg_key };
        REQUIRE(env_has_fn(vm, 0, reg_args, 1) == 1);
        REQUIRE(legatus::stdlib::read_solix_chars(vm, env_get_fn(vm, 0, reg_args, 1)) == "us-east-1");

        // Verify host OS environment is completely blocked
        uint64_t path_key = legatus::stdlib::allocate_solix_chars(vm, "PATH");
        uint64_t path_args[1] = { path_key };
        REQUIRE(env_has_fn(vm, 0, path_args, 1) == 0);
        REQUIRE(env_get_fn(vm, 0, path_args, 1) == 0); // null: strictly no OS env leak
    }
}
