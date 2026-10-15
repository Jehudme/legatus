package com.legatus.studio.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Payload for STUDIO_SESSION_ACK frame.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionAckPayload(
        String session_id,
        String cluster_name,
        long heartbeat_interval_ms,
        int pending_enrollments_count,
        int online_workers_count,
        int queued_tasks_count,
        int executing_tasks_count
) {}
