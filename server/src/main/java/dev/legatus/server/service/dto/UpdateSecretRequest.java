package dev.legatus.server.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.legatus.server.domain.enumeration.SecretScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for updating an existing secret in the secrets vault.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UpdateSecretRequest(
    @NotNull
    @JsonProperty("scope")
    SecretScope scope,

    @NotBlank
    @JsonProperty("value")
    String value
) {}
