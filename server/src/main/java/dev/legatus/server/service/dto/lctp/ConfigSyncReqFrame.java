package dev.legatus.server.service.dto.lctp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.UUID;

/**
 * LCTP CONFIG_SYNC_REQ frame transmitted by worker upon connection or resync (worker-wire.md Section 4.6).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ConfigSyncReqFrame(
    @JsonProperty("type") String type,
    @JsonProperty("id") UUID id,
    @JsonProperty("timestamp") long timestamp,
    @JsonProperty("payload") Payload payload
) implements LctpFrame {

    public static final String FRAME_TYPE = "CONFIG_SYNC_REQ";

    public ConfigSyncReqFrame(UUID id, long timestamp, Payload payload) {
        this(FRAME_TYPE, id, timestamp, payload);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Payload(
        @JsonProperty("worker_id") String workerId,
        @JsonProperty("config_version") Long configVersion
    ) {}
}
