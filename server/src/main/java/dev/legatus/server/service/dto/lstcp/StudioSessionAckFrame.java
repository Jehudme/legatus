package dev.legatus.server.service.dto.lstcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.UUID;

/**
 * LSTCP STUDIO_SESSION_ACK frame returned by server to acknowledge session (studio-wire.md Section 3.2).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record StudioSessionAckFrame(
    @JsonProperty("type") String type,
    @JsonProperty("id") UUID id,
    @JsonProperty("timestamp") long timestamp,
    @JsonProperty("payload") Payload payload
) implements LstcpFrame {

    public static final String FRAME_TYPE = "STUDIO_SESSION_ACK";

    public StudioSessionAckFrame(UUID id, long timestamp, Payload payload) {
        this(FRAME_TYPE, id, timestamp, payload);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Payload(
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("cluster_name") String clusterName,
        @JsonProperty("heartbeat_interval_ms") long heartbeatIntervalMs,
        @JsonProperty("pending_enrollments_count") int pendingEnrollmentsCount,
        @JsonProperty("online_workers_count") int onlineWorkersCount,
        @JsonProperty("queued_tasks_count") int queuedTasksCount,
        @JsonProperty("executing_tasks_count") int executingTasksCount
    ) {}
}
