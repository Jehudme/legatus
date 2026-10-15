package dev.legatus.server.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for operator authentication (POST /api/v1/auth/operator/login).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OperatorLoginRequest(
    @NotBlank @JsonProperty("username") String username,
    @NotBlank @JsonProperty("password") String password
) {}
