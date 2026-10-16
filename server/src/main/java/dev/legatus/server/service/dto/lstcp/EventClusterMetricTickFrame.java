package dev.legatus.server.service.dto.lstcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.UUID;

/**
 * LSTCP EVENT_CLUSTER_METRIC_TICK frame broadcast every 1000ms (studio-wire.md Section 4.2).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EventClusterMetricTickFrame(
    @JsonProperty("type") String type,
    @JsonProperty("id") UUID id,
    @JsonProperty("timestamp") long timestamp,
    @JsonProperty("payload") Payload payload
) implements LstcpFrame {

    public static final String FRAME_TYPE = "EVENT_CLUSTER_METRIC_TICK";

    public EventClusterMetricTickFrame(UUID id, long timestamp, Payload payload) {
        this(FRAME_TYPE, id, timestamp, payload);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Payload(
        @JsonProperty("nodes_online") int nodesOnline,
        @JsonProperty("nodes_draining") int nodesDraining,
        @JsonProperty("nodes_offline") int nodesOffline,
        @JsonProperty("total_allocatable_cores") int totalAllocatableCores,
        @JsonProperty("active_allocated_cores") int activeAllocatedCores,
        @JsonProperty("cluster_cpu_load_pct") double clusterCpuLoadPct,
        @JsonProperty("total_allocatable_memory_bytes") long totalAllocatableMemoryBytes,
        @JsonProperty("active_used_memory_bytes") long activeUsedMemoryBytes,
        @JsonProperty("tasks_queued") int tasksQueued,
        @JsonProperty("tasks_executing") int tasksExecuting,
        @JsonProperty("tasks_completed_last_60s") int tasksCompletedLast60s,
        @JsonProperty("tasks_failed_last_60s") int tasksFailedLast60s,
        @JsonProperty("threshold_breach_warning") Boolean thresholdBreachWarning,
        @JsonProperty("queue_saturated_error") Boolean queueSaturatedError,
        @JsonProperty("breached_worker_ids") List<String> breachedWorkerIds
    ) {
        public Payload(
            int nodesOnline,
            int nodesDraining,
            int nodesOffline,
            int totalAllocatableCores,
            int activeAllocatedCores,
            double clusterCpuLoadPct,
            long totalAllocatableMemoryBytes,
            long activeUsedMemoryBytes,
            int tasksQueued,
            int tasksExecuting,
            int tasksCompletedLast60s,
            int tasksFailedLast60s
        ) {
            this(
                nodesOnline,
                nodesDraining,
                nodesOffline,
                totalAllocatableCores,
                activeAllocatedCores,
                clusterCpuLoadPct,
                totalAllocatableMemoryBytes,
                activeUsedMemoryBytes,
                tasksQueued,
                tasksExecuting,
                tasksCompletedLast60s,
                tasksFailedLast60s,
                false,
                false,
                List.of()
            );
        }
    }

}
