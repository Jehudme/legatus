package dev.legatus.server.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.legatus.server.domain.enumeration.SecretScope;
import java.time.Instant;

/**
 * Secret metadata record returned by administrative endpoints without exposing ciphertext or plaintext.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SecretMetadataResponse(
    @JsonProperty("secret_key") String secretKey,
    @JsonProperty("scope") SecretScope scope,
    @JsonProperty("updated_at") Instant updatedAt
) {}
