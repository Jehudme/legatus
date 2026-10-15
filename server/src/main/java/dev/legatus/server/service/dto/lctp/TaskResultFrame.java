package dev.legatus.server.service.dto.lctp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.UUID;

/**
 * LCTP TASK_RESULT frame transmitted by worker upon task completion or termination (worker-wire.md Section 4.7).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TaskResultFrame(
    @JsonProperty("type") String type,
    @JsonProperty("id") UUID id,
    @JsonProperty("timestamp") long timestamp,
    @JsonProperty("payload") Payload payload
) implements LctpFrame {

    public static final String FRAME_TYPE = "TASK_RESULT";

    public TaskResultFrame(UUID id, long timestamp, Payload payload) {
        this(FRAME_TYPE, id, timestamp, payload);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Payload(
        @JsonProperty("task_id") String taskId,
        @JsonProperty("status") String status,
        @JsonProperty("exit_code") Integer exitCode,
        @JsonProperty("metrics") TaskMetrics metrics,
        @JsonProperty("output") Object output,
        @JsonProperty("error") TaskError error
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TaskMetrics(
        @JsonProperty("instructions_consumed") long instructionsConsumed,
        @JsonProperty("peak_memory_bytes") long peakMemoryBytes,
        @JsonProperty("execution_wall_time_ms") long executionWallTimeMs
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TaskError(
        @JsonProperty("code") String code,
        @JsonProperty("message") String message
    ) {}
}
