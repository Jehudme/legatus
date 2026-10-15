package dev.legatus.server.service.dto.lctp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.UUID;

/**
 * LCTP HEARTBEAT_ACK frame transmitted by server to acknowledge heartbeat (worker-wire.md Section 4.4).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record HeartbeatAckFrame(
    @JsonProperty("type") String type,
    @JsonProperty("id") UUID id,
    @JsonProperty("timestamp") long timestamp,
    @JsonProperty("payload") Payload payload
) implements LctpFrame {

    public static final String FRAME_TYPE = "HEARTBEAT_ACK";

    public HeartbeatAckFrame(UUID id, long timestamp, Payload payload) {
        this(FRAME_TYPE, id, timestamp, payload);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Payload(
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("server_time_utc") long serverTimeUtc
    ) {}
}
