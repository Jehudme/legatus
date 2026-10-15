package dev.legatus.server.service.dto.task;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Response payload returned upon successful raw bytecode binary ingestion.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BytecodeUploadResponse(
    @JsonProperty("sha256")
    String sha256,

    @JsonProperty("size_bytes")
    long sizeBytes,

    @JsonProperty("bytecode_url")
    String bytecodeUrl
) {}
