package dev.legatus.server.service.dto.lstcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.UUID;

/**
 * LSTCP EVENT_ENROLLMENT_REQUESTED frame pushed when a worker enters quarantine (studio-wire.md Section 4.1).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EventEnrollmentRequestedFrame(
    @JsonProperty("type") String type,
    @JsonProperty("id") UUID id,
    @JsonProperty("timestamp") long timestamp,
    @JsonProperty("payload") Payload payload
) implements LstcpFrame {

    public static final String FRAME_TYPE = "EVENT_ENROLLMENT_REQUESTED";

    public EventEnrollmentRequestedFrame(UUID id, long timestamp, Payload payload) {
        this(FRAME_TYPE, id, timestamp, payload);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Payload(
        @JsonProperty("enrollment_id") String enrollmentId,
        @JsonProperty("worker_id") String workerId,
        @JsonProperty("hostname") String hostname,
        @JsonProperty("source_ip") String sourceIp,
        @JsonProperty("os") String os,
        @JsonProperty("architecture") String architecture,
        @JsonProperty("fingerprint_hex") String fingerprintHex,
        @JsonProperty("visual_code") String visualCode,
        @JsonProperty("allocatable_cores") int allocatableCores,
        @JsonProperty("max_memory_bytes") long maxMemoryBytes,
        @JsonProperty("expires_at") long expiresAt
    ) {}
}
