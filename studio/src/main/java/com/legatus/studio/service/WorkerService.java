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
        return approveEnrollment(enrollmentId, leaseDuration, null);
    }

    public CompletableFuture<Void> approveEnrollment(String enrollmentId, String leaseDuration, String friendlyName) {
        RestDto.ApproveEnrollmentRequest request = new RestDto.ApproveEnrollmentRequest(
                leaseDuration != null ? leaseDuration : "24h",
                friendlyName
        );
        String encoded = java.net.URLEncoder.encode(enrollmentId, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return restClient.post("/api/v1/admin/workers/enrollments/" + encoded + "/approve", request, Void.class);
    }

    public CompletableFuture<Void> rejectEnrollment(String enrollmentId, String reason, boolean blacklistIp) {
        RestDto.RejectEnrollmentRequest request = new RestDto.RejectEnrollmentRequest(reason, blacklistIp);
        String encoded = java.net.URLEncoder.encode(enrollmentId, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return restClient.post("/api/v1/admin/workers/enrollments/" + encoded + "/reject", request, Void.class);
    }

    public CompletableFuture<Void> renameWorker(String workerId, String friendlyName) {
        String encoded = java.net.URLEncoder.encode(workerId, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        RestDto.RenameWorkerRequest request = new RestDto.RenameWorkerRequest(friendlyName);
        return restClient.put("/api/v1/admin/workers/" + encoded + "/name", request, Void.class);
    }

    public CompletableFuture<Void> updateThresholds(String workerId, double targetCpuPct, double targetRamPct, int maxConcurrentTasks) {
        String encoded = java.net.URLEncoder.encode(workerId, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        RestDto.UpdateThresholdsRequest request = new RestDto.UpdateThresholdsRequest(targetCpuPct, targetRamPct, maxConcurrentTasks);
        return restClient.put("/api/v1/admin/workers/" + encoded + "/thresholds", request, Void.class);
    }

    public CompletableFuture<Void> toggleActive(String workerId) {
        String encoded = java.net.URLEncoder.encode(workerId, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return restClient.post("/api/v1/admin/workers/" + encoded + "/toggle-active", null, Void.class);
    }

    public CompletableFuture<Void> toggleAutoRenew(String workerId, boolean autoRenew) {
        String encoded = java.net.URLEncoder.encode(workerId, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        RestDto.AutoRenewLeaseRequest request = new RestDto.AutoRenewLeaseRequest(autoRenew);
        return restClient.post("/api/v1/admin/workers/" + encoded + "/lease/auto-renew", request, Void.class);
    }

    public CompletableFuture<Void> setAutoAccept(boolean autoAccept) {
        RestDto.AutoAcceptRequest request = new RestDto.AutoAcceptRequest(autoAccept);
        return restClient.post("/api/v1/admin/enrollment/auto-accept", request, Void.class);
    }

    public CompletableFuture<Boolean> getAutoAccept() {
        return restClient.get("/api/v1/admin/enrollment/auto-accept", new TypeReference<java.util.Map<String, Object>>() {})
                .thenApply(map -> {
                    if (map != null && map.containsKey("auto_accept")) {
                        Object val = map.get("auto_accept");
                        if (val instanceof Boolean b) return b;
                        return Boolean.parseBoolean(String.valueOf(val));
                    }
                    return false;
                });
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
