#include <catch2/catch_test_macros.hpp>
#include "legatus/stdlib/sandbox.hpp"
#include "legatus/stdlib/solix_memory_helpers.hpp"
#include <solix/runtime.hpp>
#include <solix/native_registry.hpp>
#include <openssl/evp.h>
#include <string>
#include <vector>

TEST_CASE("Standard Library - Extended Cryptography (HMAC, AES-GCM, Ed25519)", "[stdlib][crypto]") {
    solix::NativeRegistry registry;
    legatus::stdlib::TaskSandboxContext ctx;
    legatus::stdlib::register_sandboxed_stdlib(registry, ctx);

    auto hmac_sha256 = registry.find_function("Hmac_native_sha256");
    auto hmac_sha512 = registry.find_function("Hmac_native_sha512");
    auto aes_encrypt = registry.find_function("AesGcm_native_encrypt");
    auto aes_decrypt = registry.find_function("AesGcm_native_decrypt");
    auto ed25519_verify = registry.find_function("Ed25519_native_verify");

    REQUIRE(hmac_sha256 != nullptr);
    REQUIRE(hmac_sha512 != nullptr);
    REQUIRE(aes_encrypt != nullptr);
    REQUIRE(aes_decrypt != nullptr);
    REQUIRE(ed25519_verify != nullptr);

    solix::RuntimeOptions opts;
    opts.sandboxed = true;
    solix::RuntimeContext vm(opts);

    SECTION("HMAC SHA-256 and SHA-512 Generation") {
        std::string key = "secret-key";
        std::string msg = "Hello Legatus Serverless!";

        uint64_t key_addr = legatus::stdlib::allocate_solix_chars(vm, key);
        uint64_t msg_addr = legatus::stdlib::allocate_solix_chars(vm, msg);

        uint64_t args[2] = { key_addr, msg_addr };
        uint64_t h256_res = hmac_sha256(vm, 0, args, 2);
        std::string hex256 = legatus::stdlib::read_solix_chars(vm, h256_res);
        REQUIRE(hex256.size() == 64);

        uint64_t h512_res = hmac_sha512(vm, 0, args, 2);
        std::string hex512 = legatus::stdlib::read_solix_chars(vm, h512_res);
        REQUIRE(hex512.size() == 128);
    }

    SECTION("AES-256-GCM Encryption and Decryption Roundtrip with Tag Authentication") {
        std::vector<uint8_t> key(32, 0x42);
        std::vector<uint8_t> iv(12, 0x07);
        std::string plaintext_str = "Confidential cluster payload data";
        std::vector<uint8_t> plaintext(plaintext_str.begin(), plaintext_str.end());

        uint64_t key_addr = legatus::stdlib::allocate_solix_bytes(vm, key.data(), key.size());
        uint64_t iv_addr = legatus::stdlib::allocate_solix_bytes(vm, iv.data(), iv.size());
        uint64_t pt_addr = legatus::stdlib::allocate_solix_bytes(vm, plaintext.data(), plaintext.size());

        // Encrypt
        uint64_t enc_args[3] = { key_addr, iv_addr, pt_addr };
        uint64_t enc_res = aes_encrypt(vm, 0, enc_args, 3);
        REQUIRE(enc_res != 0);

        auto encrypted_payload = legatus::stdlib::read_solix_bytes(vm, enc_res);
        REQUIRE(encrypted_payload.size() == plaintext.size() + 16); // ciphertext + 16-byte tag

        // Decrypt valid
        uint64_t dec_args[3] = { key_addr, iv_addr, enc_res };
        uint64_t dec_res = aes_decrypt(vm, 0, dec_args, 3);
        REQUIRE(dec_res != 0);

        auto decrypted_bytes = legatus::stdlib::read_solix_bytes(vm, dec_res);
        std::string decrypted_str(decrypted_bytes.begin(), decrypted_bytes.end());
        REQUIRE(decrypted_str == plaintext_str);

        // Tamper with payload (flip 1 byte in ciphertext)
        encrypted_payload[0] ^= 0xFF;
        uint64_t tampered_addr = legatus::stdlib::allocate_solix_bytes(vm, encrypted_payload.data(), encrypted_payload.size());
        uint64_t dec_tamper_args[3] = { key_addr, iv_addr, tampered_addr };
        uint64_t dec_tamper_res = aes_decrypt(vm, 0, dec_tamper_args, 3);
        REQUIRE(dec_tamper_res == 0); // Tag verification must fail
    }

    SECTION("Ed25519 Signature Verification") {
        // Generate a real Ed25519 keypair for testing
        EVP_PKEY_CTX* pctx = EVP_PKEY_CTX_new_id(EVP_PKEY_ED25519, nullptr);
        REQUIRE(pctx != nullptr);
        EVP_PKEY_keygen_init(pctx);
        EVP_PKEY* pkey = nullptr;
        REQUIRE(EVP_PKEY_keygen(pctx, &pkey) == 1);
        EVP_PKEY_CTX_free(pctx);

        // Extract 32-byte raw public key
        size_t pubkey_len = 32;
        std::vector<uint8_t> pubkey(32);
        REQUIRE(EVP_PKEY_get_raw_public_key(pkey, pubkey.data(), &pubkey_len) == 1);

        // Sign test message
        std::string message = "Attestation payload from runner";
        EVP_MD_CTX* md_ctx = EVP_MD_CTX_new();
        REQUIRE(EVP_DigestSignInit(md_ctx, nullptr, nullptr, nullptr, pkey) == 1);
        size_t sig_len = 64;
        std::vector<uint8_t> sig(64);
        REQUIRE(EVP_DigestSign(md_ctx, sig.data(), &sig_len, reinterpret_cast<const unsigned char*>(message.data()), message.size()) == 1);
        EVP_MD_CTX_free(md_ctx);
        EVP_PKEY_free(pkey);

        uint64_t pub_addr = legatus::stdlib::allocate_solix_bytes(vm, pubkey.data(), pubkey.size());
        uint64_t msg_addr = legatus::stdlib::allocate_solix_bytes(vm, reinterpret_cast<const uint8_t*>(message.data()), message.size());
        uint64_t sig_addr = legatus::stdlib::allocate_solix_bytes(vm, sig.data(), sig.size());

        // Verify valid signature
        uint64_t verify_args[3] = { pub_addr, msg_addr, sig_addr };
        REQUIRE(ed25519_verify(vm, 0, verify_args, 3) == 1);

        // Verify tampered signature fails
        sig[0] ^= 0xFF;
        uint64_t bad_sig_addr = legatus::stdlib::allocate_solix_bytes(vm, sig.data(), sig.size());
        uint64_t bad_args[3] = { pub_addr, msg_addr, bad_sig_addr };
        REQUIRE(ed25519_verify(vm, 0, bad_args, 3) == 0);
    }
}
