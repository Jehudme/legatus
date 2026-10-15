package com.legatus.studio.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SecretZeroizerTest {

    @Test
    void testZeroCharArray() {
        char[] password = "SecretPassword123".toCharArray();
        SecretZeroizer.zero(password);

        for (char c : password) {
            assertEquals('\0', c);
        }
    }

    @Test
    void testZeroByteArray() {
        byte[] key = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        SecretZeroizer.zero(key);

        for (byte b : key) {
            assertEquals(0, b);
        }
    }

    @Test
    void testAutoCloseableSecretScrubbing() {
        byte[] secretBytes = new byte[]{42, 43, 44};
        try (var wrapper = new SecretZeroizer.AutoCloseableBytes(secretBytes)) {
            assertEquals(42, wrapper.get()[0]);
        }
        assertEquals(0, secretBytes[0]);
        assertEquals(0, secretBytes[1]);
        assertEquals(0, secretBytes[2]);

        char[] secretChars = new char[]{'a', 'b', 'c'};
        try (var wrapper = new SecretZeroizer.AutoCloseableChars(secretChars)) {
            assertEquals('a', wrapper.get()[0]);
        }
        assertEquals('\0', secretChars[0]);
        assertEquals('\0', secretChars[1]);
        assertEquals('\0', secretChars[2]);
    }
}
