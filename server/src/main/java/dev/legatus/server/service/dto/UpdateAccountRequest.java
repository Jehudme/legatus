package dev.legatus.server.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.legatus.server.domain.enumeration.OperatorRole;
import jakarta.validation.constraints.Email;

/**
 * Request payload for updating account profile and RBAC role (PUT /api/v1/admin/accounts/{accountId}).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UpdateAccountRequest(
    @Email @JsonProperty("email") String email,
    @JsonProperty("role") OperatorRole role
) {}
