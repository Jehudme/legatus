package com.legatus.studio.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * Payload for STUDIO_SESSION_INIT frame.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionInitPayload(
        String studio_version,
        List<String> subscriptions,
        Map<String, Object> viewport_filter
) {
    public static SessionInitPayload createDefault() {
        return new SessionInitPayload(
                "1.0.0",
                List.of("CLUSTER_METRICS", "WORKER_TOPOLOGY", "QUARANTINE_ALERTS", "TASK_LIFECYCLE"),
                Map.of("active_view", "WORKER_LIST", "limit", 100)
        );
    }
}
