package dev.legatus.server.service.dto.lctp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.UUID;

/**
 * LCTP SESSION_ACK frame transmitted by server in response to SESSION_INIT (worker-wire.md Section 4.2).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SessionAckFrame(
    @JsonProperty("type") String type,
    @JsonProperty("id") UUID id,
    @JsonProperty("timestamp") long timestamp,
    @JsonProperty("payload") Payload payload
) implements LctpFrame {

    public static final String FRAME_TYPE = "SESSION_ACK";

    public SessionAckFrame(UUID id, long timestamp, Payload payload) {
        this(FRAME_TYPE, id, timestamp, payload);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Payload(
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("status") String status,
        @JsonProperty("assigned_cluster") String assignedCluster,
        @JsonProperty("heartbeat_interval_ms") long heartbeatIntervalMs,
        @JsonProperty("heartbeat_timeout_ms") long heartbeatTimeoutMs,
        @JsonProperty("server_time_utc") long serverTimeUtc,
        @JsonProperty("reconciled_task_ids") List<String> reconciledTaskIds,
        @JsonProperty("discarded_task_ids") List<String> discardedTaskIds,
        @JsonProperty("reason") String reason
    ) {}
}
