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
        int active_tasks_count
) {}
