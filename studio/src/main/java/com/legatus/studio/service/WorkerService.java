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
        return restClient.post("/api/v1/admin/workers/enrollments/" + enrollmentId + "/approve", request, Void.class);
    }

    public CompletableFuture<Void> rejectEnrollment(String enrollmentId, String reason, boolean blacklistIp) {
        RestDto.RejectEnrollmentRequest request = new RestDto.RejectEnrollmentRequest(reason, blacklistIp);
        return restClient.post("/api/v1/admin/workers/enrollments/" + enrollmentId + "/reject", request, Void.class);
    }

    public CompletableFuture<Void> drainWorker(String workerId) {
        return restClient.post("/api/v1/admin/workers/" + workerId + "/drain", null, Void.class);
    }

    public CompletableFuture<Void> revokeWorker(String workerId) {
        return restClient.post("/api/v1/admin/workers/" + workerId + "/revoke", null, Void.class);
    }
}
