package dev.legatus.server.service.dto.lstcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.UUID;

/**
 * LSTCP EVENT_USER_STATE_CHANGED frame emitted upon account creation, update, mutation, or deletion (MS-010).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EventUserStateChangedFrame(
    @JsonProperty("type") String type,
    @JsonProperty("id") UUID id,
    @JsonProperty("timestamp") long timestamp,
    @JsonProperty("payload") Payload payload
) implements LstcpFrame {

    public static final String FRAME_TYPE = "EVENT_USER_STATE_CHANGED";

    public EventUserStateChangedFrame(UUID id, long timestamp, Payload payload) {
        this(FRAME_TYPE, id, timestamp, payload);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Payload(
        @JsonProperty("account_id") String accountId,
        @JsonProperty("username") String username,
        @JsonProperty("email") String email,
        @JsonProperty("role") String role,
        @JsonProperty("status") String status,
        @JsonProperty("action") String action
    ) {}
}
