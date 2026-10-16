package dev.legatus.server.service.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for write-only password reset (POST /api/v1/admin/accounts/{accountId}/reset-password).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ResetPasswordRequest(
    @NotBlank
    @Size(min = 12, message = "Password must be at least 12 characters long")
    @JsonProperty("new_password")
    @JsonAlias({ "new_password", "newPassword", "password" })
    String newPassword
) {}
