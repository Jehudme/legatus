package com.legatus.studio.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

public class AesGcmCryptoTest {

    private static final byte[] VALID_KEY = new byte[32];
    static {
        new SecureRandom().nextBytes(VALID_KEY);
    }

    @Test
    void testEncryptDecryptBytesSuccess() throws GeneralSecurityException {
        byte[] payload = "SuperSecretOperatorToken.123456789".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = AesGcmCrypto.encrypt(payload, VALID_KEY);

        assertNotNull(encrypted);
        assertTrue(encrypted.length > payload.length);

        byte[] decrypted = AesGcmCrypto.decrypt(encrypted, VALID_KEY);
        assertArrayEquals(payload, decrypted);
    }

    @Test
    void testEncryptDecryptStringSuccess() throws GeneralSecurityException {
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.legatus-refresh-sample";
        String encryptedBase64 = AesGcmCrypto.encryptString(token, VALID_KEY);

        assertNotNull(encryptedBase64);
        assertNotEquals(token, encryptedBase64);

        String decrypted = AesGcmCrypto.decryptString(encryptedBase64, VALID_KEY);
        assertEquals(token, decrypted);
    }

    @Test
    void testCorruptedCiphertextFailsAuthentication() throws GeneralSecurityException {
        byte[] payload = "SensitiveData".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = AesGcmCrypto.encrypt(payload, VALID_KEY);

        // Tamper with last byte (part of auth tag)
        encrypted[encrypted.length - 1] ^= 0x01;

        assertThrows(GeneralSecurityException.class, () -> {
            AesGcmCrypto.decrypt(encrypted, VALID_KEY);
        });
    }

    @Test
    void testWrongKeyFailsDecryption() throws GeneralSecurityException {
        byte[] payload = "SensitiveData".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = AesGcmCrypto.encrypt(payload, VALID_KEY);

        byte[] wrongKey = new byte[32];
        new SecureRandom().nextBytes(wrongKey);

        assertThrows(GeneralSecurityException.class, () -> {
            AesGcmCrypto.decrypt(encrypted, wrongKey);
        });
    }

    @Test
    void testInvalidKeyLengthThrows() {
        byte[] shortKey = new byte[16];
        byte[] payload = "Hello".getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> {
            AesGcmCrypto.encrypt(payload, shortKey);
        });
    }
}
