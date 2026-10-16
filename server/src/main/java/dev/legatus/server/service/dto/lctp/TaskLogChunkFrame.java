package dev.legatus.server.service.dto.lctp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.UUID;

/**
 * LCTP TASK_LOG_CHUNK frame transmitted by worker for incremental stdout/stderr streaming (worker-wire.md Section 4.5).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TaskLogChunkFrame(
    @JsonProperty("type") String type,
    @JsonProperty("id") UUID id,
    @JsonProperty("timestamp") long timestamp,
    @JsonProperty("payload") Payload payload
) implements LctpFrame {

    public static final String FRAME_TYPE = "TASK_LOG_CHUNK";

    public TaskLogChunkFrame(UUID id, long timestamp, Payload payload) {
        this(FRAME_TYPE, id, timestamp, payload);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Payload(
        @JsonProperty("task_id") String taskId,
        @JsonProperty("stream") String stream,
        @JsonProperty("log_chunk") String logChunk,
        @JsonProperty("instructions_consumed") Long instructionsConsumed,
        @JsonProperty("progress_pct") Double progressPct
    ) {}
}
