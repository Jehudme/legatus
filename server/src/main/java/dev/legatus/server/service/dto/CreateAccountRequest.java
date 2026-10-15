package dev.legatus.server.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.legatus.server.domain.enumeration.OperatorRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payload for creating an operator account (POST /api/v1/admin/accounts).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CreateAccountRequest(
    @NotBlank @JsonProperty("username") String username,
    @NotBlank @Email @JsonProperty("email") String email,
    @NotNull @JsonProperty("role") OperatorRole role,
    @NotBlank @Size(min = 12, message = "Password must be at least 12 characters long") @JsonProperty("initial_password") String initialPassword
) {}
