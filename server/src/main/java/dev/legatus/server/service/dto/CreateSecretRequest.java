package dev.legatus.server.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.legatus.server.domain.enumeration.SecretScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payload for creating a new envelope-encrypted secret in the secrets vault.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CreateSecretRequest(
    @NotBlank
    @Size(max = 128)
    @JsonProperty("secret_key")
    String secretKey,

    @NotNull
    @JsonProperty("scope")
    SecretScope scope,

    @NotBlank
    @JsonProperty("value")
    String value
) {}
