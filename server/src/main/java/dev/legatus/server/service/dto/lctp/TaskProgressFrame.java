package dev.legatus.server.service.dto.lctp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.UUID;

/**
 * LCTP TASK_PROGRESS frame transmitted by worker for intermediate progress/logs (worker-wire.md Section 4.6).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TaskProgressFrame(
    @JsonProperty("type") String type,
    @JsonProperty("id") UUID id,
    @JsonProperty("timestamp") long timestamp,
    @JsonProperty("payload") Payload payload
) implements LctpFrame {

    public static final String FRAME_TYPE = "TASK_PROGRESS";

    public TaskProgressFrame(UUID id, long timestamp, Payload payload) {
        this(FRAME_TYPE, id, timestamp, payload);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Payload(
        @JsonProperty("task_id") String taskId,
        @JsonProperty("progress_pct") double progressPct,
        @JsonProperty("instructions_consumed") long instructionsConsumed,
        @JsonProperty("memory_used_bytes") long memoryUsedBytes,
        @JsonProperty("log_chunk") String logChunk
    ) {}
}
