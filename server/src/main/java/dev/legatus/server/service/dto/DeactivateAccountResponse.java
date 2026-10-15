package dev.legatus.server.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.UUID;

/**
 * Response payload for account deactivation (DELETE /api/v1/admin/accounts/{accountId}).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DeactivateAccountResponse(
    @JsonProperty("account_id") UUID accountId,
    @JsonProperty("status") String status,
    @JsonProperty("message") String message
) {}
