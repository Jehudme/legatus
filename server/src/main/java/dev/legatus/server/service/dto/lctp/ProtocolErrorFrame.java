package dev.legatus.server.service.dto.lctp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.UUID;

/**
 * LCTP PROTOCOL_ERROR frame transmitted when frame parsing or schema validation fails (worker-wire.md Section 4.11).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ProtocolErrorFrame(
    @JsonProperty("type") String type,
    @JsonProperty("id") UUID id,
    @JsonProperty("timestamp") long timestamp,
    @JsonProperty("payload") Payload payload
) implements LctpFrame {

    public static final String FRAME_TYPE = "PROTOCOL_ERROR";

    public ProtocolErrorFrame(UUID id, long timestamp, Payload payload) {
        this(FRAME_TYPE, id, timestamp, payload);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Payload(
        @JsonProperty("originating_frame_id") String originatingFrameId,
        @JsonProperty("error_code") String errorCode,
        @JsonProperty("message") String message
    ) {}
}
