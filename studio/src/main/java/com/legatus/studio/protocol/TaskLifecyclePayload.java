package com.legatus.studio.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Payload for EVENT_TASK_LIFECYCLE frame.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskLifecyclePayload(
        String task_id,
        String name,
        String status,
        Integer exit_code,
        String assigned_worker_id,
        Long instructions_consumed,
        Long peak_memory_bytes,
        Long execution_wall_time_ms
) {}
