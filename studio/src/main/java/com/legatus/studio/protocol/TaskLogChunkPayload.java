package com.legatus.studio.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Payload for EVENT_TASK_LOG_CHUNK frame.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskLogChunkPayload(
        String task_id,
        Double progress_pct,
        Long instructions_consumed,
        String log_chunk
) {}
