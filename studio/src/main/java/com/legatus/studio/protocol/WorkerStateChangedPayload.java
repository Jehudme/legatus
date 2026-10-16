package com.legatus.studio.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Payload for EVENT_WORKER_STATE_CHANGED frame.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkerStateChangedPayload(
        String worker_id,
        String previous_state,
        String current_state,
        double cpu_load_pct,
        long memory_used_bytes,
        int active_tasks_count,
        Long uptime_seconds,
        Long remaining_lease_sec,
        Integer allocatable_cores,
        Long max_memory_bytes,
        String friendly_name
) {
    public WorkerStateChangedPayload(
        String worker_id,
        String previous_state,
        String current_state,
        double cpu_load_pct,
        long memory_used_bytes,
        int active_tasks_count
    ) {
        this(worker_id, previous_state, current_state, cpu_load_pct, memory_used_bytes, active_tasks_count, null, null, null, null, null);
    }
}
