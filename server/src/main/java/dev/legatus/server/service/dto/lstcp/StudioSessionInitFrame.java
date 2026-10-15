package dev.legatus.server.service.dto.lstcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.UUID;

/**
 * LSTCP STUDIO_SESSION_INIT frame transmitted by Studio upon connection (studio-wire.md Section 3.1).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record StudioSessionInitFrame(
    @JsonProperty("type") String type,
    @JsonProperty("id") UUID id,
    @JsonProperty("timestamp") long timestamp,
    @JsonProperty("payload") Payload payload
) implements LstcpFrame {

    public static final String FRAME_TYPE = "STUDIO_SESSION_INIT";

    public StudioSessionInitFrame(UUID id, long timestamp, Payload payload) {
        this(FRAME_TYPE, id, timestamp, payload);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Payload(
        @JsonProperty("studio_version") String studioVersion,
        @JsonProperty("subscriptions") List<String> subscriptions,
        @JsonProperty("viewport_filter") ViewportFilter viewportFilter
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ViewportFilter(
        @JsonProperty("active_view") String activeView,
        @JsonProperty("cluster_pool") String clusterPool,
        @JsonProperty("limit") Integer limit
    ) {}
}
