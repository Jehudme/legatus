package dev.legatus.server.service.dto.lstcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.UUID;

/**
 * LSTCP STUDIO_HEARTBEAT frame transmitted periodically by Studio (studio-wire.md Section 5).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record StudioHeartbeatFrame(
    @JsonProperty("type") String type,
    @JsonProperty("id") UUID id,
    @JsonProperty("timestamp") long timestamp,
    @JsonProperty("payload") Payload payload
) implements LstcpFrame {

    public static final String FRAME_TYPE = "STUDIO_HEARTBEAT";

    public StudioHeartbeatFrame(UUID id, long timestamp, Payload payload) {
        this(FRAME_TYPE, id, timestamp, payload);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Payload(
        @JsonProperty("sequence") Long sequence
    ) {}
}
