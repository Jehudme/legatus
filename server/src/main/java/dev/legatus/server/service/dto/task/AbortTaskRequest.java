package dev.legatus.server.service.dto.task;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Request payload for aborting an in-flight compute task (api-endpoint.md Section 4.3).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AbortTaskRequest(
    @JsonProperty("reason")
    String reason,

    @JsonProperty("grace_period_ms")
    Integer gracePeriodMs
) {}
