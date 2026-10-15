package dev.legatus.server.service.dto.lctp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.UUID;

/**
 * LCTP ABORT_TASK frame transmitted by server to cancel an in-flight task (worker-wire.md Section 4.10).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AbortTaskFrame(
    @JsonProperty("type") String type,
    @JsonProperty("id") UUID id,
    @JsonProperty("timestamp") long timestamp,
    @JsonProperty("payload") Payload payload
) implements LctpFrame {

    public static final String FRAME_TYPE = "ABORT_TASK";

    public AbortTaskFrame(UUID id, long timestamp, Payload payload) {
        this(FRAME_TYPE, id, timestamp, payload);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Payload(
        @JsonProperty("task_id") String taskId,
        @JsonProperty("reason") String reason,
        @JsonProperty("grace_period_ms") long gracePeriodMs
    ) {}
}
