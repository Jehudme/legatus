package dev.legatus.server.service.dto.lctp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Map;
import java.util.UUID;

/**
 * LCTP DISPATCH_TASK frame transmitted by server to dispatch workload (worker-wire.md Section 4.5).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DispatchTaskFrame(
    @JsonProperty("type") String type,
    @JsonProperty("id") UUID id,
    @JsonProperty("timestamp") long timestamp,
    @JsonProperty("payload") Payload payload
) implements LctpFrame {

    public static final String FRAME_TYPE = "DISPATCH_TASK";

    public DispatchTaskFrame(UUID id, long timestamp, Payload payload) {
        this(FRAME_TYPE, id, timestamp, payload);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Payload(
        @JsonProperty("task_id") String taskId,
        @JsonProperty("entry_point") String entryPoint,
        @JsonProperty("bytecode") BytecodeSpec bytecode,
        @JsonProperty("constraints") Constraints constraints,
        @JsonProperty("arguments") Map<String, Object> arguments,
        @JsonProperty("env") Map<String, String> env,
        @JsonProperty("secrets") Map<String, String> secrets
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record BytecodeSpec(
        @JsonProperty("format") String format,
        @JsonProperty("encoding") String encoding,
        @JsonProperty("sha256") String sha256,
        @JsonProperty("payload") String payload,
        @JsonProperty("bytecode_url") String bytecodeUrl
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Constraints(
        @JsonProperty("instruction_budget") long instructionBudget,
        @JsonProperty("max_memory_bytes") long maxMemoryBytes,
        @JsonProperty("timeout_ms") int timeoutMs
    ) {}
}
