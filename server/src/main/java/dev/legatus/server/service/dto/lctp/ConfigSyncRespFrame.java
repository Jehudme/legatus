package dev.legatus.server.service.dto.lctp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.UUID;

/**
 * LCTP CONFIG_SYNC_RESP frame transmitted by server responding with authoritative runtime configuration (worker-wire.md Section 4.6).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ConfigSyncRespFrame(
    @JsonProperty("type") String type,
    @JsonProperty("id") UUID id,
    @JsonProperty("timestamp") long timestamp,
    @JsonProperty("payload") Payload payload
) implements LctpFrame {

    public static final String FRAME_TYPE = "CONFIG_SYNC_RESP";

    public ConfigSyncRespFrame(UUID id, long timestamp, Payload payload) {
        this(FRAME_TYPE, id, timestamp, payload);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Payload(
        @JsonProperty("worker_id") String workerId,
        @JsonProperty("config_version") long configVersion,
        @JsonProperty("friendly_name") String friendlyName,
        @JsonProperty("active") boolean active,
        @JsonProperty("target_cpu_pct") double targetCpuPct,
        @JsonProperty("target_ram_pct") double targetRamPct,
        @JsonProperty("max_concurrent_tasks") int maxConcurrentTasks,
        @JsonProperty("auto_renew_lease") boolean autoRenewLease,
        @JsonProperty("remaining_lease_sec") long remainingLeaseSec,
        @JsonProperty("heartbeat_interval_ms") long heartbeatIntervalMs,
        @JsonProperty("log_level") String logLevel,
        @JsonProperty("runtime_config_json") String runtimeConfigJson
    ) {}
}
