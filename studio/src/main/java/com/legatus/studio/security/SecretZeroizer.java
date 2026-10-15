package com.legatus.studio.security;

import java.util.Arrays;

/**
 * Memory scrubber for wiping sensitive buffers (passwords, JWT tokens, keys)
 * from heap memory after use.
 */
public final class SecretZeroizer {

    private SecretZeroizer() {}

    /**
     * Wipes a character array by overwriting all entries with null bytes.
     */
    public static void zero(char[] buffer) {
        if (buffer != null) {
            Arrays.fill(buffer, '\0');
        }
    }

    /**
     * Wipes a byte array by overwriting all entries with zeros.
     */
    public static void zero(byte[] buffer) {
        if (buffer != null) {
            Arrays.fill(buffer, (byte) 0);
        }
    }

    /**
     * RAII-style AutoCloseable wrapper for sensitive byte buffers.
     */
    public static class AutoCloseableBytes implements AutoCloseable {
        private final byte[] data;

        public AutoCloseableBytes(byte[] data) {
            this.data = data;
        }

        public byte[] get() {
            return data;
        }

        @Override
        public void close() {
            zero(data);
        }
    }

    /**
     * RAII-style AutoCloseable wrapper for sensitive char buffers.
     */
    public static class AutoCloseableChars implements AutoCloseable {
        private final char[] data;

        public AutoCloseableChars(char[] data) {
            this.data = data;
        }

        public char[] get() {
            return data;
        }

        @Override
        public void close() {
            zero(data);
        }
    }
}
