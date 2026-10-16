#include "legatus/stdlib/sandbox.hpp"
#include "legatus/stdlib/solix_memory_helpers.hpp"
#include <openssl/evp.h>
#include <openssl/hmac.h>
#include <openssl/rand.h>
#include <string>
#include <vector>
#include <cstring>
#include <iomanip>
#include <sstream>

namespace legatus::stdlib {

namespace {

static std::string to_hex(const uint8_t* data, size_t len) {
    std::ostringstream oss;
    oss << std::hex << std::setfill('0');
    for (size_t i = 0; i < len; ++i) {
        oss << std::setw(2) << static_cast<unsigned int>(data[i]);
    }
    return oss.str();
}

static uint64_t hmac_native_sha256(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)self; (void)argc;
    std::string key = read_solix_chars(vm, args[0]);
    std::string data = read_solix_chars(vm, args[1]);

    unsigned int len = 32;
    uint8_t out[32];
    HMAC(EVP_sha256(), key.data(), static_cast<int>(key.size()),
         reinterpret_cast<const unsigned char*>(data.data()), data.size(),
         out, &len);

    return allocate_solix_chars(vm, to_hex(out, len));
}

static uint64_t hmac_native_sha512(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)self; (void)argc;
    std::string key = read_solix_chars(vm, args[0]);
    std::string data = read_solix_chars(vm, args[1]);

    unsigned int len = 64;
    uint8_t out[64];
    HMAC(EVP_sha512(), key.data(), static_cast<int>(key.size()),
         reinterpret_cast<const unsigned char*>(data.data()), data.size(),
         out, &len);

    return allocate_solix_chars(vm, to_hex(out, len));
}

static uint64_t aes_gcm_native_encrypt(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)self; (void)argc;
    auto key = read_solix_bytes(vm, args[0]);   // 32 bytes
    auto iv = read_solix_bytes(vm, args[1]);    // 12 bytes
    auto plaintext = read_solix_bytes(vm, args[2]);

    if (key.size() != 32 || iv.size() != 12) {
        return 0ULL; // Error: invalid key or IV size
    }

    EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();
    if (!ctx) return 0ULL;

    std::vector<uint8_t> ciphertext(plaintext.size() + 16);
    int out_len = 0;
    int final_len = 0;
    uint8_t tag[16];

    bool ok = EVP_EncryptInit_ex(ctx, EVP_aes_256_gcm(), nullptr, nullptr, nullptr) == 1 &&
              EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, static_cast<int>(iv.size()), nullptr) == 1 &&
              EVP_EncryptInit_ex(ctx, nullptr, nullptr, key.data(), iv.data()) == 1 &&
              EVP_EncryptUpdate(ctx, ciphertext.data(), &out_len, plaintext.data(), static_cast<int>(plaintext.size())) == 1 &&
              EVP_EncryptFinal_ex(ctx, ciphertext.data() + out_len, &final_len) == 1 &&
              EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_GET_TAG, 16, tag) == 1;

    EVP_CIPHER_CTX_free(ctx);

    if (!ok) return 0ULL;

    ciphertext.resize(out_len + final_len);
    // Append 16-byte tag to ciphertext
    ciphertext.insert(ciphertext.end(), tag, tag + 16);

    return allocate_solix_bytes(vm, ciphertext.data(), ciphertext.size());
}

static uint64_t aes_gcm_native_decrypt(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)self; (void)argc;
    auto key = read_solix_bytes(vm, args[0]);   // 32 bytes
    auto iv = read_solix_bytes(vm, args[1]);    // 12 bytes
    auto payload = read_solix_bytes(vm, args[2]); // ciphertext + 16-byte tag

    if (key.size() != 32 || iv.size() != 12 || payload.size() < 16) {
        return 0ULL;
    }

    size_t cipher_len = payload.size() - 16;
    const uint8_t* tag_ptr = payload.data() + cipher_len;

    EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();
    if (!ctx) return 0ULL;

    std::vector<uint8_t> plaintext(cipher_len);
    int out_len = 0;
    int final_len = 0;

    bool ok = EVP_DecryptInit_ex(ctx, EVP_aes_256_gcm(), nullptr, nullptr, nullptr) == 1 &&
              EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, static_cast<int>(iv.size()), nullptr) == 1 &&
              EVP_DecryptInit_ex(ctx, nullptr, nullptr, key.data(), iv.data()) == 1 &&
              EVP_DecryptUpdate(ctx, plaintext.data(), &out_len, payload.data(), static_cast<int>(cipher_len)) == 1 &&
              EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_TAG, 16, const_cast<uint8_t*>(tag_ptr)) == 1 &&
              EVP_DecryptFinal_ex(ctx, plaintext.data() + out_len, &final_len) == 1;

    EVP_CIPHER_CTX_free(ctx);

    if (!ok) return 0ULL; // Tag mismatch or decryption error

    plaintext.resize(out_len + final_len);
    return allocate_solix_bytes(vm, plaintext.data(), plaintext.size());
}

static uint64_t ed25519_native_verify(solix::RuntimeContext &vm, uint64_t self, uint64_t *args, size_t argc) {
    (void)self; (void)argc;
    auto pubkey_bytes = read_solix_bytes(vm, args[0]); // 32 bytes
    auto message_bytes = read_solix_bytes(vm, args[1]);
    auto sig_bytes = read_solix_bytes(vm, args[2]);    // 64 bytes

    if (pubkey_bytes.size() != 32 || sig_bytes.size() != 64) {
        return 0ULL;
    }

    EVP_PKEY* pkey = EVP_PKEY_new_raw_public_key(EVP_PKEY_ED25519, nullptr, pubkey_bytes.data(), 32);
    if (!pkey) return 0ULL;

    EVP_MD_CTX* ctx = EVP_MD_CTX_new();
    if (!ctx) {
        EVP_PKEY_free(pkey);
        return 0ULL;
    }

    bool valid = EVP_DigestVerifyInit(ctx, nullptr, nullptr, nullptr, pkey) == 1 &&
                 EVP_DigestVerify(ctx, sig_bytes.data(), sig_bytes.size(), message_bytes.data(), message_bytes.size()) == 1;

    EVP_MD_CTX_free(ctx);
    EVP_PKEY_free(pkey);

    return valid ? 1ULL : 0ULL;
}

} // namespace

void register_crypto_extended_natives(solix::NativeRegistry &registry) {
    auto reg = [&](const std::string &cls, const std::string &name, solix::NativeFunctionPtr func) {
        registry.register_function(cls + "_" + name, func);
        registry.register_function("legatus_crypto_extended_" + cls + "_" + name, func);
        registry.register_function("legatus.crypto.extended." + cls + "." + name, func);
    };

    reg("Hmac", "native_sha256", hmac_native_sha256);
    reg("Hmac", "native_sha512", hmac_native_sha512);
    reg("AesGcm", "native_encrypt", aes_gcm_native_encrypt);
    reg("AesGcm", "native_decrypt", aes_gcm_native_decrypt);
    reg("Ed25519", "native_verify", ed25519_native_verify);
}

} // namespace legatus::stdlib
