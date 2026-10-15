package com.legatus.studio.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * High-performance AES-256-GCM symmetric cryptographic provider.
 * Follows NIST SP 800-38D with 96-bit (12-byte) random IV and 128-bit authentication tag.
 */
public final class AesGcmCrypto {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AesGcmCrypto() {}

    /**
     * Encrypts plaintext bytes with AES-256-GCM.
     * The resulting payload format is: [12-byte IV] + [ciphertext + 16-byte auth tag].
     */
    public static byte[] encrypt(byte[] plaintext, byte[] keyBytes) throws GeneralSecurityException {
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        Objects.requireNonNull(keyBytes, "keyBytes must not be null");
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("AES-256 requires exactly 32 bytes (256 bits) key, got: " + keyBytes.length);
        }

        byte[] iv = new byte[IV_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        SecretKey secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

        byte[] cipherTextWithTag = cipher.doFinal(plaintext);

        ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherTextWithTag.length);
        byteBuffer.put(iv);
        byteBuffer.put(cipherTextWithTag);
        return byteBuffer.array();
    }

    /**
     * Decrypts AES-256-GCM payload format: [12-byte IV] + [ciphertext + 16-byte auth tag].
     */
    public static byte[] decrypt(byte[] cipherData, byte[] keyBytes) throws GeneralSecurityException {
        Objects.requireNonNull(cipherData, "cipherData must not be null");
        Objects.requireNonNull(keyBytes, "keyBytes must not be null");
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("AES-256 requires exactly 32 bytes (256 bits) key, got: " + keyBytes.length);
        }
        if (cipherData.length < IV_LENGTH_BYTES + (TAG_LENGTH_BITS / 8)) {
            throw new IllegalArgumentException("Cipher data is too short to contain a valid IV and GCM authentication tag");
        }

        ByteBuffer byteBuffer = ByteBuffer.wrap(cipherData);
        byte[] iv = new byte[IV_LENGTH_BYTES];
        byteBuffer.get(iv);

        byte[] cipherTextWithTag = new byte[byteBuffer.remaining()];
        byteBuffer.get(cipherTextWithTag);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        SecretKey secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

        return cipher.doFinal(cipherTextWithTag);
    }

    /**
     * Encrypts a UTF-8 string to Base64-encoded AES-GCM ciphertext.
     */
    public static String encryptString(String plaintext, byte[] keyBytes) throws GeneralSecurityException {
        if (plaintext == null) {
            return null;
        }
        byte[] encrypted = encrypt(plaintext.getBytes(StandardCharsets.UTF_8), keyBytes);
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * Decrypts Base64-encoded AES-GCM ciphertext to a UTF-8 string.
     */
    public static String decryptString(String base64Cipher, byte[] keyBytes) throws GeneralSecurityException {
        if (base64Cipher == null) {
            return null;
        }
        byte[] decoded = Base64.getDecoder().decode(base64Cipher);
        byte[] decrypted = decrypt(decoded, keyBytes);
        return new String(decrypted, StandardCharsets.UTF_8);
    }
}
