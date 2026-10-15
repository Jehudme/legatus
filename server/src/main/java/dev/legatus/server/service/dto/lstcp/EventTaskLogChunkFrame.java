package dev.legatus.server.service.dto.lstcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.UUID;

/**
 * LSTCP EVENT_TASK_LOG_CHUNK frame forwarded to subscribed Studio sessions (studio-wire.md Section 4.5).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EventTaskLogChunkFrame(
    @JsonProperty("type") String type,
    @JsonProperty("id") UUID id,
    @JsonProperty("timestamp") long timestamp,
    @JsonProperty("payload") Payload payload
) implements LstcpFrame {

    public static final String FRAME_TYPE = "EVENT_TASK_LOG_CHUNK";

    public EventTaskLogChunkFrame(UUID id, long timestamp, Payload payload) {
        this(FRAME_TYPE, id, timestamp, payload);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Payload(
        @JsonProperty("task_id") String taskId,
        @JsonProperty("progress_pct") Double progressPct,
        @JsonProperty("instructions_consumed") Long instructionsConsumed,
        @JsonProperty("log_chunk") String logChunk
    ) {}
}
