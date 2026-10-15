package dev.legatus.server.service.dto.lctp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.UUID;

/**
 * LCTP DRAIN_NOTICE frame transmitted by worker for expedited scheduler ejection (worker-wire.md Section 4.9).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DrainNoticeFrame(
    @JsonProperty("type") String type,
    @JsonProperty("id") UUID id,
    @JsonProperty("timestamp") long timestamp,
    @JsonProperty("payload") Payload payload
) implements LctpFrame {

    public static final String FRAME_TYPE = "DRAIN_NOTICE";

    public DrainNoticeFrame(UUID id, long timestamp, Payload payload) {
        this(FRAME_TYPE, id, timestamp, payload);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Payload(
        @JsonProperty("draining") boolean draining,
        @JsonProperty("active_tasks_remaining") int activeTasksRemaining,
        @JsonProperty("reason") String reason
    ) {}
}
