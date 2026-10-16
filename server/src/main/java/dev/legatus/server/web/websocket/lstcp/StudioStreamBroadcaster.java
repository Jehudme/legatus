package dev.legatus.server.web.websocket.lstcp;

import dev.legatus.server.domain.WorkerNode;
import dev.legatus.server.domain.enumeration.TaskStatus;
import dev.legatus.server.domain.enumeration.WorkerState;
import dev.legatus.server.repository.ComputeTaskRepository;
import dev.legatus.server.repository.WorkerNodeRepository;
import dev.legatus.server.service.dto.lstcp.*;
import dev.legatus.server.service.studio.StudioEventPublisher;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Real-time event broadcaster transmitting cluster metrics, topology mutations,
 * quarantine push alerts, and scoped task log streams to Legatus Studio clients (studio-wire.md).
 */
@Component
public class StudioStreamBroadcaster implements StudioEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(StudioStreamBroadcaster.class);

    private final StudioSessionRegistry sessionRegistry;
    private final WorkerNodeRepository workerNodeRepository;
    private final ComputeTaskRepository computeTaskRepository;
    private final ObjectMapper objectMapper;
    private final dev.legatus.server.web.websocket.lctp.WorkerSessionRegistry workerSessionRegistry;
    private final dev.legatus.server.service.ClusterMetricsService clusterMetricsService;

    public StudioStreamBroadcaster(
        StudioSessionRegistry sessionRegistry,
        WorkerNodeRepository workerNodeRepository,
        ComputeTaskRepository computeTaskRepository,
        ObjectMapper objectMapper
    ) {
        this(sessionRegistry, workerNodeRepository, computeTaskRepository, objectMapper, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public StudioStreamBroadcaster(
        StudioSessionRegistry sessionRegistry,
        WorkerNodeRepository workerNodeRepository,
        ComputeTaskRepository computeTaskRepository,
        ObjectMapper objectMapper,
        @org.springframework.beans.factory.annotation.Autowired(required = false) dev.legatus.server.web.websocket.lctp.WorkerSessionRegistry workerSessionRegistry,
        @org.springframework.beans.factory.annotation.Autowired(required = false) dev.legatus.server.service.ClusterMetricsService clusterMetricsService
    ) {
        this.sessionRegistry = sessionRegistry;
        this.workerNodeRepository = workerNodeRepository;
        this.computeTaskRepository = computeTaskRepository;
        this.objectMapper = objectMapper;
        this.workerSessionRegistry = workerSessionRegistry;
        this.clusterMetricsService = clusterMetricsService;
    }

    /**
     * Broadcasts cluster metric ticks every 1000ms to all Studio sessions subscribed to CLUSTER_METRICS.
     */
    @Scheduled(fixedRate = 1000)
    public void broadcastClusterMetricTick() {
        if (sessionRegistry.getActiveSessionCount() == 0) {
            return;
        }

        try {
            EventClusterMetricTickFrame frame;

            if (clusterMetricsService != null) {
                var stats = clusterMetricsService.computeClusterStats();
                frame = new EventClusterMetricTickFrame(
                    UUID.randomUUID(),
                    System.currentTimeMillis(),
                    new EventClusterMetricTickFrame.Payload(
                        stats.nodesOnline(),
                        stats.nodesDraining(),
                        stats.nodesOffline(),
                        stats.totalAllocatableCores(),
                        stats.tasksExecuting(),
                        stats.clusterCpuLoadPct(),
                        stats.totalAllocatableMemoryBytes(),
                        stats.activeUsedMemoryBytes(),
                        stats.tasksQueued(),
                        stats.tasksExecuting(),
                        0,
                        0,
                        stats.thresholdBreachWarning(),
                        stats.queueSaturatedError(),
                        stats.breachedWorkerIds()
                    )
                );
            } else {
                int online = (int) workerNodeRepository.countByState(WorkerState.ONLINE);
                int draining = (int) workerNodeRepository.countByState(WorkerState.DRAINING);
                int offline = (int) workerNodeRepository.countByState(WorkerState.OFFLINE);

                int queued = (int) computeTaskRepository.countByStatus(TaskStatus.QUEUED);
                int executing = (int) computeTaskRepository.countByStatus(TaskStatus.EXECUTING);

                List<WorkerNode> onlineNodes = workerNodeRepository.findAllByState(WorkerState.ONLINE);
                int totalCores = 0;
                long totalMemory = 0L;
                for (WorkerNode node : onlineNodes) {
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

                frame = new EventClusterMetricTickFrame(
                    UUID.randomUUID(),
                    System.currentTimeMillis(),
                    new EventClusterMetricTickFrame.Payload(
                        online,
                        draining,
                        offline,
                        totalCores,
                        executing,
                        clusterCpuPct,
                        totalMemory,
                        usedMemory,
                        queued,
                        executing,
                        0,
                        0,
                        false,
                        false,
                        List.of()
                    )
                );
            }

            String json = objectMapper.writeValueAsString(frame);
            for (LstcpStudioSession session : sessionRegistry.getAllSessions()) {
                if (session.getSubscriptions().contains("CLUSTER_METRICS") || session.getSubscriptions().isEmpty()) {
                    session.sendText(json);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to broadcast metric tick: {}", e.getMessage());
        }
    }


    @Override
    public void publishMetricTick(EventClusterMetricTickFrame.Payload payload) {
        try {
            EventClusterMetricTickFrame frame = new EventClusterMetricTickFrame(
                UUID.randomUUID(),
                System.currentTimeMillis(),
                payload
            );
            String json = objectMapper.writeValueAsString(frame);
            for (LstcpStudioSession session : sessionRegistry.getAllSessions()) {
                if (session.getSubscriptions().contains("CLUSTER_METRICS")) {
                    session.sendText(json);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to publish manual metric tick: {}", e.getMessage());
        }
    }

    @Override
    public void publishEnrollmentRequested(EventEnrollmentRequestedFrame.Payload payload) {
        try {
            EventEnrollmentRequestedFrame frame = new EventEnrollmentRequestedFrame(
                UUID.randomUUID(),
                System.currentTimeMillis(),
                payload
            );
            String json = objectMapper.writeValueAsString(frame);
            for (LstcpStudioSession session : sessionRegistry.getAllSessions()) {
                if (session.getSubscriptions().contains("QUARANTINE_ALERTS") || session.getSubscriptions().isEmpty()) {
                    session.sendText(json);
                }
            }
            LOG.info("Broadcast EVENT_ENROLLMENT_REQUESTED for node {} ({})",
                payload.workerId(), payload.visualCode());
        } catch (Exception e) {
            LOG.error("Failed to broadcast EVENT_ENROLLMENT_REQUESTED: {}", e.getMessage());
        }
    }

    @Override
    public void publishWorkerStateChanged(EventWorkerStateChangedFrame.Payload payload) {
        try {
            EventWorkerStateChangedFrame frame = new EventWorkerStateChangedFrame(
                UUID.randomUUID(),
                System.currentTimeMillis(),
                payload
            );
            String json = objectMapper.writeValueAsString(frame);
            for (LstcpStudioSession session : sessionRegistry.getAllSessions()) {
                if (session.getSubscriptions().contains("WORKER_TOPOLOGY") || session.getSubscriptions().isEmpty()) {
                    session.sendText(json);
                }
            }
            LOG.debug("Broadcast EVENT_WORKER_STATE_CHANGED for node {}: {} -> {}",
                payload.workerId(), payload.previousState(), payload.currentState());
        } catch (Exception e) {
            LOG.error("Failed to broadcast EVENT_WORKER_STATE_CHANGED: {}", e.getMessage());
        }
    }

    @Override
    public void publishTaskLifecycle(EventTaskLifecycleFrame.Payload payload) {
        try {
            EventTaskLifecycleFrame frame = new EventTaskLifecycleFrame(
                UUID.randomUUID(),
                System.currentTimeMillis(),
                payload
            );
            String json = objectMapper.writeValueAsString(frame);
            for (LstcpStudioSession session : sessionRegistry.getAllSessions()) {
                if (session.getSubscriptions().contains("TASK_LIFECYCLE") || session.getSubscriptions().isEmpty()) {
                    session.sendText(json);
                }
            }
            LOG.debug("Broadcast EVENT_TASK_LIFECYCLE for task {}: {}",
                payload.taskId(), payload.status());
        } catch (Exception e) {
            LOG.error("Failed to broadcast EVENT_TASK_LIFECYCLE: {}", e.getMessage());
        }
    }

    @Override
    public void publishTaskLogChunk(EventTaskLogChunkFrame.Payload payload) {
        try {
            EventTaskLogChunkFrame frame = new EventTaskLogChunkFrame(
                UUID.randomUUID(),
                System.currentTimeMillis(),
                payload
            );
            String json = objectMapper.writeValueAsString(frame);
            int recipients = 0;
            for (LstcpStudioSession session : sessionRegistry.getAllSessions()) {
                if (session.getSubscribedTaskIds().isEmpty() ||
                    session.getSubscribedTaskIds().contains(payload.taskId()) ||
                    session.getSubscribedTaskIds().contains("*")) {
                    session.sendText(json);
                    recipients++;
                }
            }
            if (recipients > 0) {
                LOG.debug("Dispatched log chunk for task {} to {} Studio sessions",
                    payload.taskId(), recipients);
            }
        } catch (Exception e) {
            LOG.error("Failed to dispatch EVENT_TASK_LOG_CHUNK: {}", e.getMessage());
        }
    }

    @Override
    public void publishTaskFailedAlert(EventTaskFailedAlertFrame.Payload payload) {
        try {
            EventTaskFailedAlertFrame frame = new EventTaskFailedAlertFrame(
                UUID.randomUUID(),
                System.currentTimeMillis(),
                payload
            );
            String json = objectMapper.writeValueAsString(frame);
            for (LstcpStudioSession session : sessionRegistry.getAllSessions()) {
                session.sendText(json);
            }
            LOG.warn("Broadcast EVENT_TASK_FAILED_ALERT for task {}: {}",
                payload.taskId(), payload.errorMessage());
        } catch (Exception e) {
            LOG.error("Failed to broadcast EVENT_TASK_FAILED_ALERT: {}", e.getMessage());
        }
    }
}
