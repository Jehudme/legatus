package com.legatus.studio.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Payload for EVENT_CLUSTER_METRIC_TICK frame broadcast every 1000ms.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClusterMetricTickPayload(
        int nodes_online,
        int nodes_draining,
        int nodes_offline,
        int total_allocatable_cores,
        int active_allocated_cores,
        double cluster_cpu_load_pct,
        long total_allocatable_memory_bytes,
        long active_used_memory_bytes,
        int tasks_queued,
        int tasks_executing,
        int tasks_completed_last_60s,
        int tasks_failed_last_60s,
        Boolean threshold_breach_warning,
        Boolean queue_saturated_error,
        java.util.List<String> breached_worker_ids
) {
    public ClusterMetricTickPayload(
            int nodes_online,
            int nodes_draining,
            int nodes_offline,
            int total_allocatable_cores,
            int active_allocated_cores,
            double cluster_cpu_load_pct,
            long total_allocatable_memory_bytes,
            long active_used_memory_bytes,
            int tasks_queued,
            int tasks_executing,
            int tasks_completed_last_60s,
            int tasks_failed_last_60s
    ) {
        this(nodes_online, nodes_draining, nodes_offline, total_allocatable_cores, active_allocated_cores,
                cluster_cpu_load_pct, total_allocatable_memory_bytes, active_used_memory_bytes,
                tasks_queued, tasks_executing, tasks_completed_last_60s, tasks_failed_last_60s,
                false, false, java.util.List.of());
    }
}
