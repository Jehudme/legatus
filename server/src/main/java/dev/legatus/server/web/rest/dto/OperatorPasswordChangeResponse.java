package dev.legatus.server.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Response payload for operator password update (200 OK).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OperatorPasswordChangeResponse(
    @JsonProperty("status") String status,
    @JsonProperty("updated_at") long updatedAt
) {}
