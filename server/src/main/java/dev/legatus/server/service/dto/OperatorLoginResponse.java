package dev.legatus.server.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.UUID;

/**
 * Response payload for successful operator authentication (200 OK).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OperatorLoginResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("expires_in_seconds") long expiresInSeconds,
    @JsonProperty("studio_ws_ticket") String studioWsTicket,
    @JsonProperty("studio_ws_url") String studioWsUrl,
    @JsonProperty("account_id") UUID accountId,
    @JsonProperty("username") String username,
    @JsonProperty("role") String role
) {}
