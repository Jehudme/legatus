package dev.legatus.server.web.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.legatus.server.domain.WorkerNode;
import dev.legatus.server.domain.enumeration.TaskStatus;
import dev.legatus.server.domain.enumeration.WorkerState;
import dev.legatus.server.repository.ComputeTaskRepository;
import dev.legatus.server.repository.WorkerNodeRepository;
import dev.legatus.server.security.SecurityUtils;
import dev.legatus.server.service.WorkerEnrollmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST controller for fleet administration, quarantine review, and cluster statistics.
 * Conforms to api-endpoint.md Section 5.
 */
@RestController
@RequestMapping("/api/v1")
public class WorkerAdminResource {

    private static final Logger LOG = LoggerFactory.getLogger(WorkerAdminResource.class);

    private final WorkerNodeRepository workerNodeRepository;
    private final WorkerEnrollmentService workerEnrollmentService;
    private final ComputeTaskRepository computeTaskRepository;
    private final dev.legatus.server.web.websocket.lctp.WorkerSessionRegistry workerSessionRegistry;
    private final dev.legatus.server.service.ClusterMetricsService clusterMetricsService;

    public WorkerAdminResource(
        WorkerNodeRepository workerNodeRepository,
        WorkerEnrollmentService workerEnrollmentService,
        ComputeTaskRepository computeTaskRepository
    ) {
        this(workerNodeRepository, workerEnrollmentService, computeTaskRepository, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public WorkerAdminResource(
        WorkerNodeRepository workerNodeRepository,
        WorkerEnrollmentService workerEnrollmentService,
        ComputeTaskRepository computeTaskRepository,
        @org.springframework.beans.factory.annotation.Autowired(required = false) dev.legatus.server.web.websocket.lctp.WorkerSessionRegistry workerSessionRegistry,
        @org.springframework.beans.factory.annotation.Autowired(required = false) dev.legatus.server.service.ClusterMetricsService clusterMetricsService
    ) {
        this.workerNodeRepository = workerNodeRepository;
        this.workerEnrollmentService = workerEnrollmentService;
        this.computeTaskRepository = computeTaskRepository;
        this.workerSessionRegistry = workerSessionRegistry;
        this.clusterMetricsService = clusterMetricsService;
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record WorkerItemDto(
        @JsonProperty("worker_id") String workerId,
        @JsonProperty("friendly_name") String friendlyName,
        @JsonProperty("hostname") String hostname,
        @JsonProperty("source_ip") String sourceIp,
        @JsonProperty("os") String os,
        @JsonProperty("architecture") String architecture,
        @JsonProperty("state") String state,
        @JsonProperty("active") boolean active,
        @JsonProperty("allocatable_cores") int allocatableCores,
        @JsonProperty("max_memory_bytes") long maxMemoryBytes,
        @JsonProperty("target_cpu_pct") double targetCpuPct,
        @JsonProperty("target_ram_pct") double targetRamPct,
        @JsonProperty("max_concurrent_tasks") int maxConcurrentTasks,
        @JsonProperty("auto_renew_lease") boolean autoRenewLease,
        @JsonProperty("remaining_lease_sec") long remainingLeaseSec,
        @JsonProperty("lease_expires_at") String leaseExpiresAt,
        @JsonProperty("uptime_seconds") long uptimeSeconds,
        @JsonProperty("cpu_load_pct") double cpuLoadPct,
        @JsonProperty("memory_used_bytes") long memoryUsedBytes,
        @JsonProperty("active_tasks_count") int activeTasksCount,
        @JsonProperty("config_version") long configVersion,
        @JsonProperty("last_heartbeat") String lastHeartbeat,
        @JsonProperty("created_at") String createdAt
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RenameWorkerRequest(
        @JsonProperty("friendly_name") String friendlyName
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ThresholdsRequest(
        @JsonProperty("target_cpu_pct") Double targetCpuPct,
        @JsonProperty("target_ram_pct") Double targetRamPct,
        @JsonProperty("max_concurrent_tasks") Integer maxConcurrentTasks
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ToggleActiveRequest(
        @JsonProperty("active") Boolean active
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AutoRenewRequest(
        @JsonProperty("auto_renew") Boolean autoRenew
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AutoAcceptRequest(
        @JsonProperty("auto_accept") boolean autoAccept
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record EnrollmentItemDto(
        @JsonProperty("enrollment_id") String enrollmentId,
        @JsonProperty("worker_id") String workerId,
        @JsonProperty("hostname") String hostname,
        @JsonProperty("source_ip") String sourceIp,
        @JsonProperty("os") String os,
        @JsonProperty("architecture") String architecture,
        @JsonProperty("fingerprint_hex") String fingerprintHex,
        @JsonProperty("visual_code") String visualCode,
        @JsonProperty("allocatable_cores") int allocatableCores,
        @JsonProperty("max_memory_bytes") long maxMemoryBytes,
        @JsonProperty("submitted_at") String submittedAt
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ApproveRequest(
        @JsonProperty("lease_duration") String leaseDuration,
        @JsonProperty("friendly_name") String friendlyName
    ) {
        public ApproveRequest(String leaseDuration) {
            this(leaseDuration, null);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RejectRequest(
        @JsonProperty("reason") String reason,
        @JsonProperty("blacklist_ip") Boolean blacklistIp
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ClusterStatsDto(
        @JsonProperty("total_allocatable_cores") int totalAllocatableCores,
        @JsonProperty("total_allocatable_memory_bytes") long totalAllocatableMemoryBytes,
        @JsonProperty("active_used_memory_bytes") long activeUsedMemoryBytes,
        @JsonProperty("cluster_cpu_load_pct") double clusterCpuLoadPct,
        @JsonProperty("nodes_online") int nodesOnline,
        @JsonProperty("nodes_draining") int nodesDraining,
        @JsonProperty("nodes_offline") int nodesOffline,
        @JsonProperty("nodes_quarantined") int nodesQuarantined,
        @JsonProperty("tasks_queued") int tasksQueued,
        @JsonProperty("tasks_executing") int tasksExecuting,
        @JsonProperty("tasks_completed_last_60s") int tasksCompletedLast60s,
        @JsonProperty("tasks_failed_last_60s") int tasksFailedLast60s,
        @JsonProperty("threshold_breach_warning") Boolean thresholdBreachWarning,
        @JsonProperty("queue_saturated_error") Boolean queueSaturatedError,
        @JsonProperty("breached_worker_ids") List<String> breachedWorkerIds
    ) {
        public ClusterStatsDto(
            int totalAllocatableCores,
            long totalAllocatableMemoryBytes,
            long activeUsedMemoryBytes,
            double clusterCpuLoadPct,
            int nodesOnline,
            int nodesDraining,
            int nodesOffline,
            int nodesQuarantined,
            int tasksQueued,
            int tasksExecuting,
            int tasksCompletedLast60s,
            int tasksFailedLast60s
        ) {
            this(
                totalAllocatableCores,
                totalAllocatableMemoryBytes,
                activeUsedMemoryBytes,
                clusterCpuLoadPct,
                nodesOnline,
                nodesDraining,
                nodesOffline,
                nodesQuarantined,
                tasksQueued,
                tasksExecuting,
                tasksCompletedLast60s,
                tasksFailedLast60s,
                false,
                false,
                List.of()
            );
        }
    }


    /**
     * GET /api/v1/workers : Lists all registered workers with load metrics and control plane properties.
     */
    @GetMapping("/workers")
    public ResponseEntity<List<WorkerItemDto>> listWorkers() {
        LOG.debug("REST request to list all registered workers");
        Instant now = Instant.now();
        List<WorkerItemDto> dtos = workerNodeRepository.findAll().stream()
            .map(w -> {
                double cpuLoad = 0.0;
                long memUsed = 0L;
                int activeTasks = 0;
                long uptimeSec = 0L;
                if (workerSessionRegistry != null) {
                    var sessOpt = workerSessionRegistry.getByWorkerId(w.getWorkerId());
                    if (sessOpt.isPresent()) {
                        var ws = sessOpt.get();
                        activeTasks = ws.getActiveTaskIds().size();
                        var m = ws.getLastHostMetrics();
                        if (m != null) {
                            cpuLoad = m.cpuLoadPct();
                            memUsed = m.memoryUsedBytes();
                        }
                    }
                }
                if (w.getCreatedAt() != null) {
                    uptimeSec = Math.max(0L, java.time.Duration.between(w.getCreatedAt(), now).toSeconds());
                }
                long remainingSec = 0L;
                if (w.getLeaseExpiresAt() != null) {
                    remainingSec = Math.max(0L, java.time.Duration.between(now, w.getLeaseExpiresAt()).toSeconds());
                } else if (w.getState() == WorkerState.ONLINE) {
                    remainingSec = 86400L;
                }

                return new WorkerItemDto(
                    w.getWorkerId(),
                    w.getFriendlyName() != null ? w.getFriendlyName() : w.getHostname(),
                    w.getHostname(),
                    w.getSourceIp() != null ? w.getSourceIp() : "",
                    w.getOs() != null ? w.getOs().name().toLowerCase() : "linux",
                    w.getArch() != null ? w.getArch().name().toLowerCase() : "x86_64",
                    w.getState() != null ? w.getState().name() : "OFFLINE",
                    Boolean.TRUE.equals(w.getActive()),
                    w.getAllocatableCores() != null ? w.getAllocatableCores() : 1,
                    w.getMaxMemoryBytes() != null ? w.getMaxMemoryBytes() : 1073741824L,
                    w.getTargetCpuPct() != null ? w.getTargetCpuPct() : 75.0,
                    w.getTargetRamPct() != null ? w.getTargetRamPct() : 80.0,
                    w.getMaxConcurrentTasks() != null ? w.getMaxConcurrentTasks() : 16,
                    Boolean.TRUE.equals(w.getAutoRenewLease()),
                    remainingSec,
                    w.getLeaseExpiresAt() != null ? w.getLeaseExpiresAt().toString() : "",
                    uptimeSec,
                    cpuLoad,
                    memUsed,
                    activeTasks,
                    w.getConfigVersion() != null ? w.getConfigVersion() : 1L,
                    w.getLastSeenAt() != null ? w.getLastSeenAt().toString() : "",
                    w.getCreatedAt() != null ? w.getCreatedAt().toString() : ""
                );
            })
            .toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * GET /api/v1/admin/workers/enrollments : Lists all quarantined nodes currently in PENDING_APPROVAL.
     */
    @GetMapping("/admin/workers/enrollments")
    public ResponseEntity<List<EnrollmentItemDto>> listQuarantinedEnrollments() {
        LOG.debug("REST request to list quarantined worker enrollments");
        List<EnrollmentItemDto> dtos = workerNodeRepository.findAllByState(WorkerState.PENDING_APPROVAL).stream()
            .map(w -> new EnrollmentItemDto(
                w.getEnrollmentId() != null ? w.getEnrollmentId() : w.getWorkerId(),
                w.getWorkerId(),
                w.getHostname(),
                w.getSourceIp() != null ? w.getSourceIp() : "",
                w.getOs() != null ? w.getOs().name().toLowerCase() : "linux",
                w.getArch() != null ? w.getArch().name().toLowerCase() : "x86_64",
                w.getFingerprint() != null ? w.getFingerprint() : "",
                w.getVisualCode() != null ? w.getVisualCode() : "",
                w.getAllocatableCores() != null ? w.getAllocatableCores() : 1,
                w.getMaxMemoryBytes() != null ? w.getMaxMemoryBytes() : 1073741824L,
                w.getCreatedAt() != null ? w.getCreatedAt().toString() : ""
            ))
            .toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * POST /api/v1/admin/workers/enrollments/{id}/approve : Approves a quarantined node.
     */
    @PostMapping("/admin/workers/enrollments/{id}/approve")
    public ResponseEntity<Map<String, String>> approveEnrollment(
        @PathVariable("id") String id,
        @RequestBody(required = false) ApproveRequest request
    ) {
        String operator = SecurityUtils.getCurrentUserLogin().orElse("operator");
        String friendlyName = request != null ? request.friendlyName() : null;
        LOG.info("REST request to approve worker enrollment {} by {}: friendlyName={}", id, operator, friendlyName);
        workerEnrollmentService.approveWorker(id, operator, "production-us-east", friendlyName);
        return ResponseEntity.ok(Map.of("status", "APPROVED"));
    }

    /**
     * POST /api/v1/admin/workers/enrollments/{id}/reject : Rejects a quarantined node.
     */
    @PostMapping("/admin/workers/enrollments/{id}/reject")
    public ResponseEntity<Map<String, String>> rejectEnrollment(
        @PathVariable("id") String id,
        @RequestBody(required = false) RejectRequest request
    ) {
        String operator = SecurityUtils.getCurrentUserLogin().orElse("operator");
        String reason = request != null ? request.reason() : "Operator rejected";
        Boolean blacklistIp = request != null && Boolean.TRUE.equals(request.blacklistIp());
        LOG.info("REST request to reject worker enrollment {} by {}: reason={}", id, operator, reason);
        workerEnrollmentService.rejectWorker(id, operator, reason, blacklistIp);
        return ResponseEntity.ok(Map.of("status", "REJECTED"));
    }

    /**
     * PUT /api/v1/admin/workers/{workerId}/name : Renames a worker compute node.
     */
    @PutMapping("/admin/workers/{workerId}/name")
    public ResponseEntity<Map<String, String>> renameWorker(
        @PathVariable("workerId") String workerId,
        @RequestBody RenameWorkerRequest request
    ) {
        String operator = SecurityUtils.getCurrentUserLogin().orElse("operator");
        String name = request != null ? request.friendlyName() : null;
        LOG.info("REST request to rename worker {} to '{}' by {}", workerId, name, operator);
        workerEnrollmentService.renameWorker(workerId, name, operator);
        return ResponseEntity.ok(Map.of("status", "RENAMED", "friendly_name", name != null ? name : ""));
    }

    /**
     * PUT /api/v1/admin/workers/{workerId}/thresholds : Updates soft resource thresholds for auto-balancing.
     */
    @PutMapping("/admin/workers/{workerId}/thresholds")
    public ResponseEntity<Map<String, Object>> updateThresholds(
        @PathVariable("workerId") String workerId,
        @RequestBody ThresholdsRequest request
    ) {
        String operator = SecurityUtils.getCurrentUserLogin().orElse("operator");
        LOG.info("REST request to update thresholds for worker {} by {}", workerId, operator);
        Double cpu = request != null ? request.targetCpuPct() : null;
        Double ram = request != null ? request.targetRamPct() : null;
        Integer tasks = request != null ? request.maxConcurrentTasks() : null;
        workerEnrollmentService.updateThresholds(workerId, cpu, ram, tasks, operator);
        return ResponseEntity.ok(Map.of("status", "UPDATED"));
    }

    /**
     * POST /api/v1/admin/workers/{workerId}/toggle-active : Toggles or sets active/suspended execution state.
     */
    @PostMapping("/admin/workers/{workerId}/toggle-active")
    public ResponseEntity<Map<String, Object>> toggleActive(
        @PathVariable("workerId") String workerId,
        @RequestBody(required = false) ToggleActiveRequest request
    ) {
        String operator = SecurityUtils.getCurrentUserLogin().orElse("operator");
        Boolean active = request != null ? request.active() : null;
        LOG.info("REST request to toggle active state for worker {} by {}", workerId, operator);
        boolean newActive = workerEnrollmentService.toggleActive(workerId, active, operator);
        return ResponseEntity.ok(Map.of("status", newActive ? "ACTIVE" : "INACTIVE", "active", newActive));
    }

    /**
     * POST /api/v1/admin/workers/{workerId}/lease/auto-renew : Toggles automatic lease renewal.
     */
    @PostMapping("/admin/workers/{workerId}/lease/auto-renew")
    public ResponseEntity<Map<String, Object>> toggleAutoRenew(
        @PathVariable("workerId") String workerId,
        @RequestBody(required = false) AutoRenewRequest request
    ) {
        String operator = SecurityUtils.getCurrentUserLogin().orElse("operator");
        Boolean autoRenew = request != null ? request.autoRenew() : null;
        LOG.info("REST request to toggle auto renew for worker {} by {}", workerId, operator);
        boolean newAutoRenew = workerEnrollmentService.toggleAutoRenew(workerId, autoRenew, operator);
        return ResponseEntity.ok(Map.of("status", "UPDATED", "auto_renew_lease", newAutoRenew));
    }

    /**
     * POST /api/v1/admin/enrollment/auto-accept : Configures cluster-wide automatic quarantine approval.
     */
    @PostMapping("/admin/enrollment/auto-accept")
    public ResponseEntity<Map<String, Object>> setAutoAccept(@RequestBody AutoAcceptRequest request) {
        String operator = SecurityUtils.getCurrentUserLogin().orElse("operator");
        LOG.info("REST request to set quarantine auto-accept to {} by {}", request.autoAccept(), operator);
        workerEnrollmentService.setAutoAccept(request.autoAccept(), operator);
        return ResponseEntity.ok(Map.of("status", "UPDATED", "auto_accept", request.autoAccept()));
    }

    /**
     * GET /api/v1/admin/enrollment/auto-accept : Returns current auto-accept status.
     */
    @GetMapping("/admin/enrollment/auto-accept")
    public ResponseEntity<Map<String, Object>> getAutoAccept() {
        boolean autoAccept = workerEnrollmentService.isAutoAcceptEnabled();
        return ResponseEntity.ok(Map.of("auto_accept", autoAccept));
    }

    /**
     * POST /api/v1/admin/workers/{workerId}/drain : Remotely toggles drain mode on an active worker.
     */
    @PostMapping("/admin/workers/{workerId}/drain")
    public ResponseEntity<Map<String, Object>> drainWorker(@PathVariable("workerId") String workerId) {
        String operator = SecurityUtils.getCurrentUserLogin().orElse("operator");
        LOG.info("REST request to toggle drain mode for worker {} by {}", workerId, operator);
        boolean draining = workerEnrollmentService.drainWorker(workerId, operator);
        return ResponseEntity.ok(Map.of("status", draining ? "DRAINING" : "ONLINE", "draining", draining));
    }

    /**
     * POST /api/v1/admin/workers/{workerId}/revoke : Revokes a worker's credentials.
     */
    @PostMapping("/admin/workers/{workerId}/revoke")
    public ResponseEntity<Map<String, String>> revokeWorker(@PathVariable("workerId") String workerId) {
        String operator = SecurityUtils.getCurrentUserLogin().orElse("operator");
        LOG.info("REST request to revoke worker {} by {}", workerId, operator);
        workerEnrollmentService.revokeWorker(workerId, operator);
        return ResponseEntity.ok(Map.of("status", "REVOKED"));
    }

    /**
     * DELETE /api/v1/admin/workers/{workerId} : Graceful 4-step revocation and deletion cascade.
     */
    @DeleteMapping("/admin/workers/{workerId}")
    public ResponseEntity<Map<String, String>> deleteWorker(@PathVariable("workerId") String workerId) {
        String operator = SecurityUtils.getCurrentUserLogin().orElse("operator");
        LOG.info("REST request to delete worker {} by {}", workerId, operator);
        workerEnrollmentService.deleteWorker(workerId, operator);
        return ResponseEntity.ok(Map.of("status", "DELETED"));
    }

    /**
     * GET /api/v1/cluster/stats : Returns aggregate cluster metrics and capacity.
     */
    @GetMapping("/cluster/stats")
    public ResponseEntity<ClusterStatsDto> getClusterStats() {
        if (clusterMetricsService != null) {
            return ResponseEntity.ok(clusterMetricsService.computeClusterStats());
        }

        int online = (int) workerNodeRepository.countByState(WorkerState.ONLINE);
        int draining = (int) workerNodeRepository.countByState(WorkerState.DRAINING);
        int offline = (int) workerNodeRepository.countByState(WorkerState.OFFLINE);
        int quarantined = (int) workerNodeRepository.countByState(WorkerState.PENDING_APPROVAL);

        int queued = (int) computeTaskRepository.countByStatus(TaskStatus.QUEUED);
        int executing = (int) computeTaskRepository.countByStatus(TaskStatus.EXECUTING);

        List<WorkerNode> activeNodes = workerNodeRepository.findAllByState(WorkerState.ONLINE);
        int totalCores = 0;
        long totalMemory = 0L;
        for (WorkerNode node : activeNodes) {
            totalCores += node.getAllocatableCores() != null ? node.getAllocatableCores() : 0;
            totalMemory += node.getMaxMemoryBytes() != null ? node.getMaxMemoryBytes() : 0L;
        }

        long usedMemory = 0L;
        double totalCpu = 0.0;
        int metricsCount = 0;
        if (workerSessionRegistry != null) {
            for (dev.legatus.server.web.websocket.lctp.LctpWorkerSession ws : workerSessionRegistry.getAllActiveSessions()) {
                dev.legatus.server.service.dto.lctp.HeartbeatFrame.HostMetrics m = ws.getLastHostMetrics();
                if (m != null) {
                    usedMemory += m.memoryUsedBytes();
                    totalCpu += m.cpuLoadPct();
                    metricsCount++;
                }
            }
        }

        double clusterCpuPct = metricsCount > 0
            ? (totalCpu / metricsCount)
            : (online > 0 ? (double) executing / Math.max(1, totalCores) * 100.0 : 0.0);

        if (usedMemory == 0L && online > 0 && totalMemory > 0L) {
            usedMemory = totalMemory / 4;
        }

        ClusterStatsDto stats = new ClusterStatsDto(
            totalCores,
            totalMemory,
            usedMemory,
            clusterCpuPct,
            online,
            draining,
            offline,
            quarantined,
            queued,
            executing,
            0,
            0,
            false,
            false,
            List.of()
        );

        return ResponseEntity.ok(stats);
    }
}

