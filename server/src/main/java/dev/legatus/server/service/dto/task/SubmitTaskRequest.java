package dev.legatus.server.service.dto.task;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.legatus.server.domain.enumeration.TaskPriority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * Request payload for submitting a task for execution (api-endpoint.md Section 4.1).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SubmitTaskRequest(
    @NotBlank
    @Size(max = 128)
    @JsonProperty("name")
    String name,

    @JsonProperty("priority")
    TaskPriority priority,

    @Min(0)
    @Max(100)
    @JsonProperty("priority_weight")
    Integer priorityWeight,

    @JsonProperty("idempotent")
    Boolean idempotent,

    @Min(0)
    @Max(10)
    @JsonProperty("max_retries")
    Integer maxRetries,

    @Size(max = 128)
    @JsonProperty("entry_point")
    String entryPoint,

    @NotNull
    @Valid
    @JsonProperty("bytecode")
    BytecodePayload bytecode,

    @Valid
    @JsonProperty("constraints")
    ConstraintsPayload constraints,

    @JsonProperty("arguments")
    Map<String, Object> arguments,

    @JsonProperty("env")
    Map<String, String> env,

    @JsonProperty("secret_refs")
    List<String> secretRefs
) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record BytecodePayload(
        @JsonProperty("format")
        String format,

        @JsonProperty("encoding")
        String encoding,

        @NotBlank
        @Size(min = 64, max = 64)
        @JsonProperty("sha256")
        String sha256,

        @JsonProperty("payload")
        String payload
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ConstraintsPayload(
        @Min(1000)
        @Max(1000000000L)
        @JsonProperty("instruction_budget")
        Long instructionBudget,

        @Min(8388608L)
        @Max(1099511627776L)
        @JsonProperty("max_memory_bytes")
        Long maxMemoryBytes,

        @Min(100)
        @Max(3600000)
        @JsonProperty("timeout_ms")
        Integer timeoutMs
    ) {}
}
