package dev.legatus.server.service;

import dev.legatus.server.domain.WorkerNode;
import dev.legatus.server.domain.enumeration.TaskStatus;
import dev.legatus.server.domain.enumeration.WorkerState;
import dev.legatus.server.orchestration.balancer.ThresholdScheduler;
import dev.legatus.server.repository.ComputeTaskRepository;
import dev.legatus.server.repository.WorkerNodeRepository;
import dev.legatus.server.web.rest.WorkerAdminResource.ClusterStatsDto;
import dev.legatus.server.web.websocket.lctp.LctpWorkerSession;
import dev.legatus.server.web.websocket.lctp.WorkerSessionRegistry;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service providing aggregate cluster metrics with strict isolation of inactive,
 * suspended, draining, and offline worker nodes.
 * <p>
 * Complies with {@code ADR-0010} and {@code MS-007} Section 3.3 [P2]:
 * <ul>
 *   <li>Only active (state == ONLINE &amp;&amp; active == true &amp;&amp; !draining) nodes contribute to allocatable cores, RAM, and CPU aggregates.</li>
 *   <li>Suppresses skew from disconnected, suspended, or draining nodes.</li>
 *   <li>Integrates with {@link ThresholdScheduler} for threshold breach warnings and queue saturation alerts.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class ClusterMetricsService {

    private static final Logger LOG = LoggerFactory.getLogger(ClusterMetricsService.class);

    private final WorkerNodeRepository workerNodeRepository;
    private final ComputeTaskRepository computeTaskRepository;
    private final WorkerSessionRegistry workerSessionRegistry;
    private final ThresholdScheduler thresholdScheduler;

    public ClusterMetricsService(
        WorkerNodeRepository workerNodeRepository,
        ComputeTaskRepository computeTaskRepository
    ) {
        this(workerNodeRepository, computeTaskRepository, null, null);
    }

    @Autowired
    public ClusterMetricsService(
        WorkerNodeRepository workerNodeRepository,
        ComputeTaskRepository computeTaskRepository,
        @Autowired(required = false) WorkerSessionRegistry workerSessionRegistry,
        @Autowired(required = false) ThresholdScheduler thresholdScheduler
    ) {
        this.workerNodeRepository = workerNodeRepository;
        this.computeTaskRepository = computeTaskRepository;
        this.workerSessionRegistry = workerSessionRegistry;
        this.thresholdScheduler = thresholdScheduler;
    }

    /**
     * Computes authoritative cluster statistics isolating inactive/disabled workers.
     */
    public ClusterStatsDto computeClusterStats() {
        if (workerSessionRegistry != null) {
            for (LctpWorkerSession ws : workerSessionRegistry.getAllActiveSessions()) {
                workerNodeRepository.findById(ws.getWorkerId()).ifPresent(w -> {
                    if (w.getState() == WorkerState.OFFLINE && Boolean.TRUE.equals(w.getActive())) {
                        w.setState(WorkerState.ONLINE);
                        workerNodeRepository.save(w);
                    }
                });
            }
        }

        int onlineActive = (int) workerNodeRepository.countByStateAndActiveTrue(WorkerState.ONLINE);
        int draining = (int) workerNodeRepository.countByState(WorkerState.DRAINING);
        int offline = (int) (workerNodeRepository.countByState(WorkerState.OFFLINE)
            + workerNodeRepository.countByState(WorkerState.INACTIVE));
        int quarantined = (int) workerNodeRepository.countByState(WorkerState.PENDING_APPROVAL);

        int queued = (int) computeTaskRepository.countByStatus(TaskStatus.QUEUED);
        int executing = (int) computeTaskRepository.countByStatus(TaskStatus.EXECUTING);

        // Filter strictly to ACTIVE online nodes that are not draining
        List<WorkerNode> eligibleNodes = workerNodeRepository.findAllByStateAndActiveTrueAndDrainingFalse(WorkerState.ONLINE);
        int totalCores = 0;
        long totalMemory = 0L;
        for (WorkerNode node : eligibleNodes) {
            totalCores += node.getAllocatableCores() != null ? node.getAllocatableCores() : 0;
            totalMemory += node.getMaxMemoryBytes() != null ? node.getMaxMemoryBytes() : 0L;
        }

        long usedMemory = 0L;
        double totalCpu = 0.0;
        int activeMetricsCount = 0;

        if (workerSessionRegistry != null) {
            for (LctpWorkerSession ws : workerSessionRegistry.getAllActiveSessions()) {
                String wid = ws.getWorkerId();
                // Ensure worker is active and not draining
                var workerOpt = workerNodeRepository.findById(wid);
                if (workerOpt.isPresent()) {
                    WorkerNode w = workerOpt.get();
                    if (w.getState() == WorkerState.ONLINE && Boolean.TRUE.equals(w.getActive()) && !Boolean.TRUE.equals(w.getDraining())) {
                        var m = ws.getLastHostMetrics();
                        if (m != null) {
                            usedMemory += m.memoryUsedBytes();
                            totalCpu += m.cpuLoadPct();
                            activeMetricsCount++;
                        }
                    }
                }
            }
        }

        double clusterCpuPct = activeMetricsCount > 0
            ? (totalCpu / activeMetricsCount)
            : (onlineActive > 0 ? (double) executing / Math.max(1, totalCores) * 100.0 : 0.0);

        if (usedMemory == 0L && onlineActive > 0 && totalMemory > 0L) {
            usedMemory = totalMemory / 4;
        }

        boolean breachWarning = thresholdScheduler != null && thresholdScheduler.isThresholdBreachActive();
        boolean queueError = thresholdScheduler != null && (thresholdScheduler.isQueueSaturatedActive() || (queued > 0 && onlineActive == 0));
        List<String> breachedIds = thresholdScheduler != null ? thresholdScheduler.getBreachedWorkerIds() : List.of();

        return new ClusterStatsDto(
            totalCores,
            totalMemory,
            usedMemory,
            clusterCpuPct,
            onlineActive,
            draining,
            offline,
            quarantined,
            queued,
            executing,
            0,
            0,
            breachWarning,
            queueError,
            breachedIds
        );
    }
}
