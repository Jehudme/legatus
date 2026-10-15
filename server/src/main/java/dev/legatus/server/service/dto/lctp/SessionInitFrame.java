package dev.legatus.server.service.dto.lctp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.UUID;

/**
 * LCTP SESSION_INIT frame transmitted by worker upon HTTP 101 upgrade (worker-wire.md Section 4.1).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SessionInitFrame(
    @JsonProperty("type") String type,
    @JsonProperty("id") UUID id,
    @JsonProperty("timestamp") long timestamp,
    @JsonProperty("payload") Payload payload
) implements LctpFrame {

    public static final String FRAME_TYPE = "SESSION_INIT";

    public SessionInitFrame(UUID id, long timestamp, Payload payload) {
        this(FRAME_TYPE, id, timestamp, payload);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Payload(
        @JsonProperty("worker_id") String workerId,
        @JsonProperty("previous_session_id") String previousSessionId,
        @JsonProperty("hostname") String hostname,
        @JsonProperty("os") String os,
        @JsonProperty("architecture") String architecture,
        @JsonProperty("agent_version") String agentVersion,
        @JsonProperty("runtime") RuntimeInfo runtime,
        @JsonProperty("hardware") HardwareInfo hardware,
        @JsonProperty("allocatable_capacity") Capacity allocatableCapacity,
        @JsonProperty("reconcile_tasks") List<ReconcileTask> reconcileTasks
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RuntimeInfo(
        @JsonProperty("engine") String engine,
        @JsonProperty("version") String version,
        @JsonProperty("isa_version") String isaVersion,
        @JsonProperty("features") List<String> features
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record HardwareInfo(
        @JsonProperty("physical_cores") int physicalCores,
        @JsonProperty("logical_cores") int logicalCores,
        @JsonProperty("total_memory_bytes") long totalMemoryBytes,
        @JsonProperty("gpu_present") boolean gpuPresent,
        @JsonProperty("gpu_model") String gpuModel
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Capacity(
        @JsonProperty("allocatable_cores") int allocatableCores,
        @JsonProperty("max_memory_bytes") long maxMemoryBytes,
        @JsonProperty("max_instruction_budget_per_task") long maxInstructionBudgetPerTask
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ReconcileTask(
        @JsonProperty("task_id") String taskId,
        @JsonProperty("status") String status,
        @JsonProperty("instructions_consumed") long instructionsConsumed,
        @JsonProperty("elapsed_wall_time_ms") long elapsedWallTimeMs
    ) {}
}
