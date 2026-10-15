package dev.legatus.server.service.dto.lstcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.UUID;

/**
 * LSTCP UNSUBSCRIBE_TASK_LOGS frame transmitted by Studio to detach from live task logs (studio-wire.md Section 3.3).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UnsubscribeTaskLogsFrame(
    @JsonProperty("type") String type,
    @JsonProperty("id") UUID id,
    @JsonProperty("timestamp") long timestamp,
    @JsonProperty("payload") Payload payload
) implements LstcpFrame {

    public static final String FRAME_TYPE = "UNSUBSCRIBE_TASK_LOGS";

    public UnsubscribeTaskLogsFrame(UUID id, long timestamp, Payload payload) {
        this(FRAME_TYPE, id, timestamp, payload);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Payload(
        @JsonProperty("task_id") String taskId
    ) {}
}
