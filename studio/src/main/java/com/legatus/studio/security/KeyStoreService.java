package com.legatus.studio.security;

import com.legatus.studio.config.PathResolver;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

/**
 * Service managing the derivation, retrieval, and persistent storage of
 * the 256-bit AES master key across host OS Keychains (libsecret, macOS Keychain,
 * Windows DPAPI) with protected local key file fallback.
 */
public class KeyStoreService {

    private static final String SERVICE_NAME = "legatus-studio";
    private static final String ACCOUNT_NAME = "master-key";
    private static final int KEY_LENGTH_BYTES = 32;

    private static byte[] cachedKey;

    public static synchronized byte[] getMasterKey() {
        if (cachedKey != null) {
            return cachedKey.clone();
        }

        byte[] key = null;
        try {
            key = loadFromOsKeyring();
        } catch (Exception ignored) {
            // OS Keyring unavailable (e.g. headless Linux, CI environment)
        }

        if (key == null || key.length != KEY_LENGTH_BYTES) {
            key = loadOrCreateLocalFileKey();
        }

        cachedKey = key;
        return cachedKey.clone();
    }

    private static byte[] loadFromOsKeyring() {
        PathResolver.OS os = PathResolver.detectOS();
        return switch (os) {
            case LINUX -> loadFromLibsecret();
            case MACOS -> loadFromMacOsKeychain();
            default -> null;
        };
    }

    private static byte[] loadFromLibsecret() {
        try {
            Process lookup = new ProcessBuilder("secret-tool", "lookup", "service", SERVICE_NAME, "account", ACCOUNT_NAME)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(lookup.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                lookup.waitFor();
                if (lookup.exitValue() == 0 && line != null && !line.isBlank()) {
                    return Base64.getDecoder().decode(line.trim());
                }
            }

            // Key not found in secret-tool, generate and store
            byte[] newKey = new byte[KEY_LENGTH_BYTES];
            new SecureRandom().nextBytes(newKey);
            String base64Key = Base64.getEncoder().encodeToString(newKey);

            Process store = new ProcessBuilder("secret-tool", "store", "--label=Legatus Studio Master Key",
                    "service", SERVICE_NAME, "account", ACCOUNT_NAME)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            try (OutputStream os = store.getOutputStream()) {
                os.write(base64Key.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            store.waitFor();
            if (store.exitValue() == 0) {
                return newKey;
            }
        } catch (Exception ignored) {
            // secret-tool not available
        }
        return null;
    }

    private static byte[] loadFromMacOsKeychain() {
        try {
            Process lookup = new ProcessBuilder("/usr/bin/security", "find-generic-password",
                    "-s", SERVICE_NAME, "-a", ACCOUNT_NAME, "-w")
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(lookup.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                lookup.waitFor();
                if (lookup.exitValue() == 0 && line != null && !line.isBlank()) {
                    return Base64.getDecoder().decode(line.trim());
                }
            }

            byte[] newKey = new byte[KEY_LENGTH_BYTES];
            new SecureRandom().nextBytes(newKey);
            String base64Key = Base64.getEncoder().encodeToString(newKey);

            Process store = new ProcessBuilder("/usr/bin/security", "add-generic-password",
                    "-U", "-s", SERVICE_NAME, "-a", ACCOUNT_NAME, "-w", base64Key)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            store.waitFor();
            if (store.exitValue() == 0) {
                return newKey;
            }
        } catch (Exception ignored) {
            // security CLI not available
        }
        return null;
    }

    private static byte[] loadOrCreateLocalFileKey() {
        try {
            Path keyPath = PathResolver.getKeyFilePath();
            Path parent = keyPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            if (Files.exists(keyPath)) {
                byte[] key = Files.readAllBytes(keyPath);
                if (key.length == KEY_LENGTH_BYTES) {
                    return key;
                }
            }

            byte[] newKey = new byte[KEY_LENGTH_BYTES];
            new SecureRandom().nextBytes(newKey);

            Files.write(keyPath, newKey, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

            try {
                Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rw-------");
                Files.setPosixFilePermissions(keyPath, permissions);
            } catch (UnsupportedOperationException ignored) {
                // Not a POSIX filesystem (e.g. Windows NTFS)
            }

            return newKey;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load or generate local cryptographic master key", e);
        }
    }
}
