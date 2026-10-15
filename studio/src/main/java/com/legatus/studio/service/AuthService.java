package com.legatus.studio.service;

import com.legatus.studio.protocol.RestDto;
import com.legatus.studio.security.OperatorSession;
import com.legatus.studio.transport.RestClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Service managing operator login, session lifecycle, token refresh, and WebSocket ticket acquisition.
 */
public class AuthService {

    private volatile RestClient restClient;

    public AuthService(RestClient restClient) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
    }

    public void setRestClient(RestClient restClient) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
    }

    public RestClient getRestClient() {
        return restClient;
    }

    public CompletableFuture<RestDto.LoginResponse> login(String username, String password) {
        RestDto.LoginRequest request = new RestDto.LoginRequest(username, password);
        return restClient.post("/api/v1/auth/operator/login", request, RestDto.LoginResponse.class)
                .thenApply(response -> {
                    Instant expiresAt = Instant.now().plus(response.expires_in_seconds(), ChronoUnit.SECONDS);
                    OperatorSession.getInstance().setSession(
                            response.access_token(),
                            response.refresh_token(),
                            expiresAt,
                            response.username(),
                            response.account_id(),
                            response.role()
                    );
                    return response;
                });
    }

    public CompletableFuture<String> acquireStudioTicket() {
        return restClient.post("/api/v1/auth/studio/ticket", null, RestDto.TicketResponse.class)
                .thenApply(RestDto.TicketResponse::ticket);
    }

    public CompletableFuture<Void> changePassword(String oldPassword, String newPassword) {
        RestDto.UpdatePasswordRequest request = new RestDto.UpdatePasswordRequest(oldPassword, newPassword);
        return restClient.put("/api/v1/auth/operator/password", request, Void.class);
    }

    public void logout() {
        OperatorSession.getInstance().clearSession();
    }
}
