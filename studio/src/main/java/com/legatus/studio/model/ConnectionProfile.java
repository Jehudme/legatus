package com.legatus.studio.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * Saved cluster connection profile.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConnectionProfile(
        String id,
        String name,
        String serverUrl,
        String username,
        String encryptedRefreshToken,
        boolean trustAllCerts,
        String caCertPath,
        Instant lastConnected
) {
    public static ConnectionProfile createDefault(String name, String serverUrl, String username) {
        return new ConnectionProfile(
                UUID.randomUUID().toString(),
                name,
                serverUrl,
                username,
                null,
                false,
                null,
                Instant.now()
        );
    }
}
