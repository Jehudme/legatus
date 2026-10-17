package dev.legatus.server.web.websocket.lctp;

import dev.legatus.server.domain.ComputeTask;
import dev.legatus.server.domain.WorkerNode;
import dev.legatus.server.domain.enumeration.AuditActionStatus;
import dev.legatus.server.domain.enumeration.TaskStatus;
import dev.legatus.server.domain.enumeration.WorkerState;
import dev.legatus.server.repository.ComputeTaskRepository;
import dev.legatus.server.repository.WorkerNodeRepository;
import dev.legatus.server.service.WormAuditService;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.CloseStatus;

/**
 * 1000ms dead-man's switch liveness sweeper running on Project Loom Virtual Threads.
 * <p>
 * Complies with {@code server-spec.md} Section 4.2 &amp; {@code worker-wire.md} Section 5.2:
 * <ul>
 *   <li>Sweeps active worker sessions every 1000ms.</li>
 *   <li>Closes silent worker sockets after 15,000ms with Close Code 1011 (ERR_WORKER_HEARTBEAT_TIMEOUT).</li>
 *   <li>Transitions dead worker nodes to OFFLINE in the database.</li>
 *   <li>Recovers orphan in-flight tasks: re-queues idempotent tasks or fails them with ERR_WORKER_LOST.</li>
 *   <li>Records statutory WORM audit events for all dead-man timeout evictions.</li>
 * </ul>
 */
@Component
public class WorkerDeadManSweeper {

    private static final Logger LOG = LoggerFactory.getLogger(WorkerDeadManSweeper.class);

    public static final long DEAD_MAN_TIMEOUT_MS = 15_000L;
    public static final CloseStatus TIMEOUT_CLOSE_STATUS = new CloseStatus(1011, "ERR_WORKER_HEARTBEAT_TIMEOUT");

    private final WorkerSessionRegistry sessionRegistry;
    private final WorkerNodeRepository workerNodeRepository;
    private final ComputeTaskRepository computeTaskRepository;
    private final WormAuditService wormAuditService;
    private final dev.legatus.server.service.studio.StudioEventPublisher studioEventPublisher;

    public WorkerDeadManSweeper(
        WorkerSessionRegistry sessionRegistry,
        WorkerNodeRepository workerNodeRepository,
        ComputeTaskRepository computeTaskRepository,
        WormAuditService wormAuditService
    ) {
        this(sessionRegistry, workerNodeRepository, computeTaskRepository, wormAuditService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public WorkerDeadManSweeper(
        WorkerSessionRegistry sessionRegistry,
        WorkerNodeRepository workerNodeRepository,
        ComputeTaskRepository computeTaskRepository,
        WormAuditService wormAuditService,
        @org.springframework.beans.factory.annotation.Autowired(required = false) dev.legatus.server.service.studio.StudioEventPublisher studioEventPublisher
    ) {
        this.sessionRegistry = sessionRegistry;
        this.workerNodeRepository = workerNodeRepository;
        this.computeTaskRepository = computeTaskRepository;
        this.wormAuditService = wormAuditService;
        this.studioEventPublisher = studioEventPublisher;
    }

    /**
     * Executes sweep every 1000ms. Visible for testing.
     */
    @Scheduled(fixedRate = 1000)
    @Transactional
    public void sweepSilentWorkers() {
        Instant now = Instant.now();

        for (LctpWorkerSession session : sessionRegistry.getAllActiveSessions()) {
            long silenceMs = Duration.between(session.getLastHeartbeatAt(), now).toMillis();

            if (silenceMs > DEAD_MAN_TIMEOUT_MS) {
                String workerId = session.getWorkerId();
                String sessionId = session.getSessionId();
                LOG.warn("Worker node {} (session {}) silent for {}ms (> 15000ms). Ejecting via dead-man's switch.",
                    workerId, sessionId, silenceMs);

                // 1. Close WebSocket transport channel
                try {
                    session.getWebSocketSession().close(TIMEOUT_CLOSE_STATUS);
                } catch (IOException e) {
                    LOG.debug("Error while closing silent worker socket: {}", e.getMessage());
                }

                // 2. Unregister session
                sessionRegistry.unregister(sessionId);

                // 3. Transition node to OFFLINE if no other active sessions
                if (sessionRegistry.getByWorkerId(workerId).isEmpty()) {
                    workerNodeRepository.findById(workerId).ifPresent(w -> {
                        w.setState(WorkerState.OFFLINE);
                        w.setLastSeenAt(now.truncatedTo(ChronoUnit.MILLIS));
                        workerNodeRepository.save(w);
                    });
                }

                // 4. Orphan Task Recovery
                List<ComputeTask> orphanedTasks = computeTaskRepository.findAllByAssignedWorkerWorkerIdAndStatusIn(
                    workerId, List.of(TaskStatus.DISPATCHED, TaskStatus.EXECUTING)
                );

                int requeuedCount = 0;
                int failedCount = 0;

                for (ComputeTask task : orphanedTasks) {
                    boolean canRetry = Boolean.TRUE.equals(task.getIdempotent()) &&
                        task.getAttemptCount() < task.getMaxRetries();

                    if (canRetry) {
                        task.setStatus(TaskStatus.QUEUED);
                        task.setAttemptCount(task.getAttemptCount() + 1);
                        task.setAssignedWorker(null);
                        requeuedCount++;
                    } else {
                        task.setStatus(TaskStatus.ERR_WORKER_LOST);
                        task.setCompletedAt(now.truncatedTo(ChronoUnit.MILLIS));
                        failedCount++;

                        if (studioEventPublisher != null && Boolean.TRUE.equals(task.getNotifyOnFailure())) {
                            String funcName = task.getFunction() != null ? task.getFunction().getName() : task.getName();
                            studioEventPublisher.publishTaskFailedAlert(new dev.legatus.server.service.dto.lstcp.EventTaskFailedAlertFrame.Payload(
                                task.getTaskId(),
                                task.getName(),
                                funcName,
                                workerId,
                                -1,
                                "Worker node became unresponsive (ERR_WORKER_LOST)",
                                System.currentTimeMillis()
                            ));
                        }
                    }
                    computeTaskRepository.save(task);
                }

                // 5. Append statutory WORM audit log
                wormAuditService.recordEvent(
                    "system-deadman",
                    "SYSTEM",
                    "WORKER_DEAD_MAN_TIMEOUT",
                    "WORKER",
                    workerId,
                    AuditActionStatus.SUCCESS,
                    String.format("{\"silence_ms\":%d,\"requeued_tasks\":%d,\"failed_tasks\":%d}", silenceMs, requeuedCount, failedCount),
                    "127.0.0.1",
                    "SOC2_CC6.8"
                );

                LOG.info("Dead-man switch processed worker {}: {} tasks requeued, {} tasks marked ERR_WORKER_LOST",
                    workerId, requeuedCount, failedCount);
            }
        }
    }

    /**
     * Emits WebSocket ping control frames every 30 seconds to prevent middlebox timeout disconnects.
     */
    @Scheduled(fixedRate = 30000)
    public void sendPingControlFrames() {
        org.springframework.web.socket.PingMessage ping = new org.springframework.web.socket.PingMessage();
        for (LctpWorkerSession session : sessionRegistry.getAllActiveSessions()) {
            if (session.getWebSocketSession() != null && session.getWebSocketSession().isOpen()) {
                try {
                    session.getWebSocketSession().sendMessage(ping);
                } catch (Exception e) {
                    LOG.debug("Error sending ping control frame to worker session {}: {}", session.getSessionId(), e.getMessage());
                }
            }
        }
    }

    /**
     * Resets any stale ONLINE worker node records to OFFLINE on application startup
     * if no active transport session exists in the session registry.
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    @Transactional
    public void resetStaleWorkerStatesOnStartup() {
        List<WorkerNode> onlineWorkers = workerNodeRepository.findAllByStateAndDrainingFalse(WorkerState.ONLINE);
        for (WorkerNode worker : onlineWorkers) {
            if (sessionRegistry.getByWorkerId(worker.getWorkerId()).isEmpty()) {
                LOG.info("Resetting stale worker {} state from ONLINE to OFFLINE on server startup", worker.getWorkerId());
                worker.setState(WorkerState.OFFLINE);
                workerNodeRepository.save(worker);
            }
        }
    }
}
