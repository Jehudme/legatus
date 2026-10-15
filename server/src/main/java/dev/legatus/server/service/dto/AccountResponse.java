package dev.legatus.server.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.legatus.server.domain.enumeration.AccountStatus;
import dev.legatus.server.domain.enumeration.OperatorRole;
import java.time.Instant;
import java.util.UUID;

/**
 * Public response DTO for operator account details.
 * Strictly excludes password digests to prevent credential leaks.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AccountResponse(
    @JsonProperty("account_id") UUID accountId,
    @JsonProperty("username") String username,
    @JsonProperty("email") String email,
    @JsonProperty("role") OperatorRole role,
    @JsonProperty("status") AccountStatus status,
    @JsonProperty("failed_login_attempts") Integer failedLoginAttempts,
    @JsonProperty("lockout_until") Instant lockoutUntil,
    @JsonProperty("created_at") Long createdAt,
    @JsonProperty("updated_at") Long updatedAt
) {}
