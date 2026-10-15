package com.legatus.studio.security;

import java.time.Instant;
import java.util.Objects;

/**
 * Thread-safe singleton managing the active operator's authentication session,
 * access token, refresh token, expiry, and role-based permissions.
 */
public class OperatorSession {

    private static final OperatorSession INSTANCE = new OperatorSession();

    private String accessToken;
    private String refreshToken;
    private Instant expiresAt;
    private String username;
    private String accountId;
    private String role;

    private OperatorSession() {}

    public static OperatorSession getInstance() {
        return INSTANCE;
    }

    public synchronized void setSession(String accessToken,
                                        String refreshToken,
                                        Instant expiresAt,
                                        String username,
                                        String accountId,
                                        String role) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAt = expiresAt;
        this.username = username;
        this.accountId = accountId;
        this.role = role;
    }

    public synchronized void updateAccessToken(String newAccessToken, Instant newExpiresAt) {
        this.accessToken = newAccessToken;
        this.expiresAt = newExpiresAt;
    }

    public synchronized void clearSession() {
        this.accessToken = null;
        this.refreshToken = null;
        this.expiresAt = null;
        this.username = null;
        this.accountId = null;
        this.role = null;
    }

    public synchronized boolean isAuthenticated() {
        return accessToken != null && !isTokenExpired();
    }

    public synchronized boolean isTokenExpired() {
        if (expiresAt == null) {
            return true;
        }
        return Instant.now().isAfter(expiresAt);
    }

    public synchronized String getAccessToken() {
        return accessToken;
    }

    public synchronized String getRefreshToken() {
        return refreshToken;
    }

    public synchronized Instant getExpiresAt() {
        return expiresAt;
    }

    public synchronized String getUsername() {
        return username;
    }

    public synchronized String getAccountId() {
        return accountId;
    }

    public synchronized String getRole() {
        return role;
    }

    public synchronized boolean hasRole(String targetRole) {
        if (this.role == null || targetRole == null) {
            return false;
        }
        if (Objects.equals(this.role, targetRole)) {
            return true;
        }
        // Admin has all permissions
        return "CLUSTER_ADMIN".equalsIgnoreCase(this.role);
    }
}
