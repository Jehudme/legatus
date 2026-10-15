package com.legatus.studio.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Canonical four-field JSON envelope for all LSTCP frames.
 * Conforms to GL-0007.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Frame<T>(
        String type,
        String id,
        long timestamp,
        T payload
) {
    public static <T> Frame<T> create(String type, T payload) {
        return new Frame<>(
                type,
                UUID.randomUUID().toString(),
                Instant.now().toEpochMilli(),
                payload
        );
    }
}
