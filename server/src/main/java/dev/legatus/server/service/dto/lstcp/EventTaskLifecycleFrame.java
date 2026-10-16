package dev.legatus.server.service.dto.lstcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.UUID;

/**
 * LSTCP EVENT_TASK_LIFECYCLE frame emitted upon task state transitions (studio-wire.md Section 4.4).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EventTaskLifecycleFrame(
    @JsonProperty("type") String type,
    @JsonProperty("id") UUID id,
    @JsonProperty("timestamp") long timestamp,
    @JsonProperty("payload") Payload payload
) implements LstcpFrame {

    public static final String FRAME_TYPE = "EVENT_TASK_LIFECYCLE";

    public EventTaskLifecycleFrame(UUID id, long timestamp, Payload payload) {
        this(FRAME_TYPE, id, timestamp, payload);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Payload(
        @JsonProperty("task_id") String taskId,
        @JsonProperty("name") String name,
        @JsonProperty("status") String status,
        @JsonProperty("exit_code") Integer exitCode,
        @JsonProperty("assigned_worker_id") String assignedWorkerId,
        @JsonProperty("instructions_consumed") Long instructionsConsumed,
        @JsonProperty("peak_memory_bytes") Long peakMemoryBytes,
        @JsonProperty("execution_wall_time_ms") Long executionWallTimeMs,
        @JsonProperty("instruction_budget") Long instructionBudget,
        @JsonProperty("timeout_ms") Integer timeoutMs,
        @JsonProperty("started_at") Long startedAt,
        @JsonProperty("created_at") Long createdAt
    ) {
        public Payload(
            String taskId,
            String name,
            String status,
            Integer exitCode,
            String assignedWorkerId,
            Long instructionsConsumed,
            Long peakMemoryBytes,
            Long executionWallTimeMs
        ) {
            this(taskId, name, status, exitCode, assignedWorkerId, instructionsConsumed, peakMemoryBytes, executionWallTimeMs, null, null, null, null);
        }

        public Payload(
            String taskId,
            String name,
            String status,
            Integer exitCode,
            String assignedWorkerId,
            Long instructionsConsumed,
            Long peakMemoryBytes,
            Long executionWallTimeMs,
            Long instructionBudget,
            Integer timeoutMs
        ) {
            this(taskId, name, status, exitCode, assignedWorkerId, instructionsConsumed, peakMemoryBytes, executionWallTimeMs, instructionBudget, timeoutMs, null, null);
        }
    }
}
