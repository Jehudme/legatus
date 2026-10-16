package dev.legatus.server.service.dto.lctp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.UUID;

/**
 * LCTP HEARTBEAT frame transmitted periodically by worker (worker-wire.md Section 4.3).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record HeartbeatFrame(
    @JsonProperty("type") String type,
    @JsonProperty("id") UUID id,
    @JsonProperty("timestamp") long timestamp,
    @JsonProperty("payload") Payload payload
) implements LctpFrame {

    public static final String FRAME_TYPE = "HEARTBEAT";

    public HeartbeatFrame(UUID id, long timestamp, Payload payload) {
        this(FRAME_TYPE, id, timestamp, payload);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Payload(
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("draining") boolean draining,
        @JsonProperty("host_metrics") HostMetrics hostMetrics,
        @JsonProperty("running_tasks") List<RunningTask> runningTasks,
        @JsonProperty("config_version") Long configVersion,
        @JsonProperty("active") Boolean active,
        @JsonProperty("remaining_lease_sec") Long remainingLeaseSec
    ) {
        public Payload(String sessionId, boolean draining, HostMetrics hostMetrics, List<RunningTask> runningTasks) {
            this(sessionId, draining, hostMetrics, runningTasks, null, null, null);
        }
    }


    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record HostMetrics(
        @JsonProperty("cpu_load_pct") double cpuLoadPct,
        @JsonProperty("gpu_load_pct") double gpuLoadPct,
        @JsonProperty("memory_used_bytes") long memoryUsedBytes,
        @JsonProperty("memory_free_bytes") long memoryFreeBytes
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RunningTask(
        @JsonProperty("task_id") String taskId,
        @JsonProperty("pid") long pid,
        @JsonProperty("status") String status,
        @JsonProperty("instructions_consumed") long instructionsConsumed,
        @JsonProperty("memory_used_bytes") long memoryUsedBytes,
        @JsonProperty("elapsed_wall_time_ms") long elapsedWallTimeMs
    ) {}
}
