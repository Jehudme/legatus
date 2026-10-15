package com.legatus.studio.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.legatus.studio.protocol.RestDto;
import com.legatus.studio.transport.RestClient;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Service managing worker nodes, quarantine attestation approvals/rejections, and lease revocations.
 */
public class WorkerService {

    private final RestClient restClient;

    public WorkerService(RestClient restClient) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
    }

    public CompletableFuture<List<RestDto.WorkerDto>> getWorkers() {
        return restClient.get("/api/v1/workers", new TypeReference<List<RestDto.WorkerDto>>() {});
    }

    public CompletableFuture<List<RestDto.EnrollmentDto>> getEnrollments() {
        return restClient.get("/api/v1/admin/workers/enrollments", new TypeReference<List<RestDto.EnrollmentDto>>() {});
    }

    public CompletableFuture<Void> approveEnrollment(String enrollmentId, String leaseDuration) {
        RestDto.ApproveEnrollmentRequest request = new RestDto.ApproveEnrollmentRequest(leaseDuration != null ? leaseDuration : "24h");
        String encoded = java.net.URLEncoder.encode(enrollmentId, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return restClient.post("/api/v1/admin/workers/enrollments/" + encoded + "/approve", request, Void.class);
    }

    public CompletableFuture<Void> rejectEnrollment(String enrollmentId, String reason, boolean blacklistIp) {
        RestDto.RejectEnrollmentRequest request = new RestDto.RejectEnrollmentRequest(reason, blacklistIp);
        String encoded = java.net.URLEncoder.encode(enrollmentId, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return restClient.post("/api/v1/admin/workers/enrollments/" + encoded + "/reject", request, Void.class);
    }

    public CompletableFuture<Void> drainWorker(String workerId) {
        String encoded = java.net.URLEncoder.encode(workerId, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return restClient.post("/api/v1/admin/workers/" + encoded + "/drain", null, Void.class);
    }

    public CompletableFuture<Void> revokeWorker(String workerId) {
        String encoded = java.net.URLEncoder.encode(workerId, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return restClient.post("/api/v1/admin/workers/" + encoded + "/revoke", null, Void.class);
    }

    public CompletableFuture<Void> deleteWorker(String workerId) {
        String encoded = java.net.URLEncoder.encode(workerId, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return restClient.delete("/api/v1/admin/workers/" + encoded, Void.class);
    }
}
