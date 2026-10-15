package dev.legatus.server.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for self-service operator password change (PUT /api/v1/auth/operator/password).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OperatorPasswordChangeRequest(
    @NotBlank @JsonProperty("current_password") String currentPassword,
    @NotBlank @Size(min = 12) @JsonProperty("new_password") String newPassword
) {}
