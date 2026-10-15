package dev.legatus.server.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.legatus.server.domain.enumeration.AccountStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for mutating account status (PATCH /api/v1/admin/accounts/{accountId}/status).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MutateAccountStatusRequest(
    @NotNull @JsonProperty("status") AccountStatus status,
    @JsonProperty("reason") String reason
) {}
