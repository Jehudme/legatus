package com.legatus.studio.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.legatus.studio.model.ConnectionProfile;

import java.util.Collections;
import java.util.List;

/**
 * Immutable Studio configuration preferences stored in studio.json.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StudioConfig(
        String theme,
        double windowWidth,
        double windowHeight,
        double windowX,
        double windowY,
        boolean maximized,
        int connectionTimeoutMs,
        int heartbeatIntervalMs,
        int logBufferLimit,
        String activeProfileId,
        List<ConnectionProfile> profiles
) {
    public static StudioConfig defaultConfiguration() {
        return new StudioConfig(
                "PRIMER_DARK",
                1280.0,
                800.0,
                -1.0,
                -1.0,
                false,
                10000,
                15000,
                10000,
                null,
                List.of()
        );
    }

    public List<ConnectionProfile> profiles() {
        return profiles == null ? Collections.emptyList() : Collections.unmodifiableList(profiles);
    }
}
