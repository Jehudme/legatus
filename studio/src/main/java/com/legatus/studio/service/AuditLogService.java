package com.legatus.studio.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
        return restClient.get("/api/v1/admin/audit-logs", JsonNode.class)
                .thenApply(node -> {
                    if (node == null || node.isNull()) return List.<RestDto.AuditLogEntryDto>of();
                    if (node.isArray()) {
                        return RestClient.MAPPER.convertValue(node, new TypeReference<List<RestDto.AuditLogEntryDto>>() {});
                    }
                    JsonNode content = node.path("content");
                    if (content.isArray()) {
                        return RestClient.MAPPER.convertValue(content, new TypeReference<List<RestDto.AuditLogEntryDto>>() {});
                    }
                    return List.<RestDto.AuditLogEntryDto>of();
                });
    }

    public CompletableFuture<RestDto.AuditVerifyResponse> verifyHashChain() {
        return restClient.get("/api/v1/admin/audit-logs/verify", RestDto.AuditVerifyResponse.class);
    }
}
