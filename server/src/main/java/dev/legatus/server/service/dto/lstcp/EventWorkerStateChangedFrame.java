package dev.legatus.server.service.dto.lstcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.UUID;

/**
 * LSTCP EVENT_WORKER_STATE_CHANGED frame emitted upon node state/metric transitions (studio-wire.md Section 4.3).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EventWorkerStateChangedFrame(
    @JsonProperty("type") String type,
    @JsonProperty("id") UUID id,
    @JsonProperty("timestamp") long timestamp,
    @JsonProperty("payload") Payload payload
) implements LstcpFrame {

    public static final String FRAME_TYPE = "EVENT_WORKER_STATE_CHANGED";

    public EventWorkerStateChangedFrame(UUID id, long timestamp, Payload payload) {
        this(FRAME_TYPE, id, timestamp, payload);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Payload(
        @JsonProperty("worker_id") String workerId,
        @JsonProperty("previous_state") String previousState,
        @JsonProperty("current_state") String currentState,
        @JsonProperty("cpu_load_pct") Double cpuLoadPct,
        @JsonProperty("memory_used_bytes") Long memoryUsedBytes,
        @JsonProperty("active_tasks_count") Integer activeTasksCount
    ) {}
}
