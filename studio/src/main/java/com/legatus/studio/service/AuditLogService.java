package com.legatus.studio.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.legatus.studio.protocol.RestDto;
import com.legatus.studio.transport.RestClient;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Service managing cryptographically chained WORM audit logs and integrity verification.
 */
public class AuditLogService {

    private final RestClient restClient;

    public AuditLogService(RestClient restClient) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
    }

    public CompletableFuture<List<RestDto.AuditLogEntryDto>> getAuditLogs() {
        return restClient.get("/api/v1/admin/audit-logs", new TypeReference<List<RestDto.AuditLogEntryDto>>() {});
    }

    public CompletableFuture<RestDto.AuditVerifyResponse> verifyHashChain() {
        return restClient.get("/api/v1/admin/audit-logs/verify", RestDto.AuditVerifyResponse.class);
    }
}
