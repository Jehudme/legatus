package dev.legatus.server.web.websocket.lctp;

import dev.legatus.server.domain.ComputeTask;
import dev.legatus.server.domain.WorkerNode;
import dev.legatus.server.domain.enumeration.AuditActionStatus;
import dev.legatus.server.domain.enumeration.TaskStatus;
import dev.legatus.server.domain.enumeration.WorkerState;
import dev.legatus.server.repository.ComputeTaskRepository;
import dev.legatus.server.repository.WorkerNodeRepository;
import dev.legatus.server.security.TicketBrokerService;
import dev.legatus.server.service.WormAuditService;
import dev.legatus.server.service.dto.lctp.*;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

/**
 * Full-duplex Legatus Compute Transport Protocol (LCTP) WebSocket Broker at {@code /ws/worker}.
 * <p>
 * Complies with {@code worker-wire.md} &amp; {@code server-spec.md} Section 4.2:
 * <ul>
 *   <li>Enforces 32 MB frame ceiling with Close Code 1009 (Message Too Big).</li>
 *   <li>Performs in-flight session reconciliation on transient network reconnects.</li>
 *   <li>Handles periodic heartbeats, drain notices, progress metering, and terminal task results.</li>
 *   <li>Commits task receipts and sends {@code TASK_RESULT_ACK} before worker capacity is deallocated.</li>
 * </ul>
 */
@Component
public class LctpWorkerWebSocketHandler extends TextWebSocketHandler {

    private static final Logger LOG = LoggerFactory.getLogger(LctpWorkerWebSocketHandler.class);

    public static final int MAX_FRAME_SIZE = 33_554_432; // 32 MB
    public static final long DEFAULT_HEARTBEAT_INTERVAL_MS = 5000L;
    public static final long DEFAULT_HEARTBEAT_TIMEOUT_MS = 15000L;
    public static final String DEFAULT_CLUSTER_NAME = "production-us-east";

    private final WorkerSessionRegistry sessionRegistry;
    private final WorkerNodeRepository workerNodeRepository;
    private final ComputeTaskRepository computeTaskRepository;
    private final WormAuditService wormAuditService;
    private final ObjectMapper objectMapper;

    public LctpWorkerWebSocketHandler(
        WorkerSessionRegistry sessionRegistry,
        WorkerNodeRepository workerNodeRepository,
        ComputeTaskRepository computeTaskRepository,
        WormAuditService wormAuditService,
        ObjectMapper objectMapper
    ) {
        this.sessionRegistry = sessionRegistry;
        this.workerNodeRepository = workerNodeRepository;
        this.computeTaskRepository = computeTaskRepository;
        this.wormAuditService = wormAuditService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Object ticketObj = session.getAttributes().get(LctpHandshakeInterceptor.WORKER_TICKET_ATTR);
        if (!(ticketObj instanceof TicketBrokerService.WorkerTicket ticket)) {
            LOG.warn("Terminating socket without burned worker ticket attribute: {}", session.getId());
            session.close(new CloseStatus(1008, "ERR_TICKET_INVALID_OR_BURNED"));
            return;
        }

        String sessionId = "sess-" + UUID.randomUUID();
        LctpWorkerSession workerSession = new LctpWorkerSession(sessionId, ticket.workerId(), session, ticket);
        sessionRegistry.register(workerSession);
        session.getAttributes().put("workerSession", workerSession);

        LOG.info("Established LCTP transport channel for worker {} (session: {})", ticket.workerId(), sessionId);
    }

    @Override
    @Transactional
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (message.getPayloadLength() > MAX_FRAME_SIZE) {
            LOG.error("Oversized LCTP frame rejected ({} bytes, limit: 32MB). Closing socket.", message.getPayloadLength());
            session.close(new CloseStatus(1009, "ERR_FRAME_TOO_LARGE"));
            return;
        }

        LctpWorkerSession workerSession = (LctpWorkerSession) session.getAttributes().get("workerSession");
        if (workerSession == null) {
            session.close(new CloseStatus(1008, "ERR_SESSION_NOT_INITIALIZED"));
            return;
        }

        LctpFrame frame;
        try {
            frame = objectMapper.readValue(message.getPayload(), LctpFrame.class);
        } catch (Exception e) {
            LOG.warn("Malformed LCTP JSON frame received: {}", e.getMessage());
            ProtocolErrorFrame errorFrame = new ProtocolErrorFrame(
                UUID.randomUUID(),
                Instant.now().toEpochMilli(),
                new ProtocolErrorFrame.Payload(null, "INVALID_JSON_ENVELOPE", e.getMessage())
            );
            workerSession.sendText(objectMapper.writeValueAsString(errorFrame));
            return;
        }

        switch (frame) {
            case SessionInitFrame init -> handleSessionInit(workerSession, init);
            case HeartbeatFrame hb -> handleHeartbeat(workerSession, hb);
            case DrainNoticeFrame drain -> handleDrainNotice(workerSession, drain);
            case TaskProgressFrame progress -> handleTaskProgress(workerSession, progress);
            case TaskResultFrame result -> handleTaskResult(workerSession, result);
            case ProtocolErrorFrame err -> LOG.warn("Worker reported LCTP protocol error: {} - {}", err.payload().errorCode(), err.payload().message());
            default -> LOG.warn("Unhandled or unexpected inbound frame type: {}", frame.type());
        }
    }

    private void handleSessionInit(LctpWorkerSession session, SessionInitFrame frame) throws IOException {
        String workerId = session.getWorkerId();
        Optional<WorkerNode> workerOpt = workerNodeRepository.findById(workerId);

        if (workerOpt.isEmpty()) {
            LOG.warn("Rejecting SESSION_INIT: Worker {} not registered in database", workerId);
            SessionAckFrame rejection = new SessionAckFrame(
                UUID.randomUUID(),
                Instant.now().toEpochMilli(),
                new SessionAckFrame.Payload(
                    null,
                    "REJECTED_UNAUTHORIZED",
                    DEFAULT_CLUSTER_NAME,
                    0,
                    0,
                    Instant.now().toEpochMilli(),
                    List.of(),
                    List.of(),
                    "Worker node not registered or authorized"
                )
            );
            session.sendText(objectMapper.writeValueAsString(rejection));
            session.getWebSocketSession().close(new CloseStatus(1008, "REJECTED_UNAUTHORIZED"));
            return;
        }

        WorkerNode worker = workerOpt.get();

        // Reconcile in-flight tasks surviving transient reconnect
        List<String> reconciledTaskIds = new ArrayList<>();
        List<String> discardedTaskIds = new ArrayList<>();

        if (frame.payload().reconcileTasks() != null) {
            for (SessionInitFrame.ReconcileTask candidate : frame.payload().reconcileTasks()) {
                Optional<ComputeTask> taskOpt = computeTaskRepository.findById(candidate.taskId());
                if (
                    taskOpt.isPresent() &&
                    taskOpt.get().getAssignedWorker() != null &&
                    workerId.equals(taskOpt.get().getAssignedWorker().getWorkerId()) &&
                    (taskOpt.get().getStatus() == TaskStatus.DISPATCHED || taskOpt.get().getStatus() == TaskStatus.EXECUTING)
                ) {
                    ComputeTask t = taskOpt.get();
                    t.setStatus(TaskStatus.EXECUTING);
                    t.setInstructionsConsumed(candidate.instructionsConsumed());
                    computeTaskRepository.save(t);
                    reconciledTaskIds.add(candidate.taskId());
                    session.getActiveTaskIds().add(candidate.taskId());
                } else {
                    discardedTaskIds.add(candidate.taskId());
                }
            }
        }

        // Update node hardware boundaries and state in database
        worker.setState(WorkerState.ONLINE);
        worker.setLastSeenAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        if (frame.payload().allocatableCapacity() != null) {
            worker.setAllocatableCores(frame.payload().allocatableCapacity().allocatableCores());
            worker.setMaxMemoryBytes(frame.payload().allocatableCapacity().maxMemoryBytes());
            worker.setMaxInstructionBudget(frame.payload().allocatableCapacity().maxInstructionBudgetPerTask());
        }
        workerNodeRepository.save(worker);

        session.setState("OPERATIONAL");
        session.setLastHeartbeatAt(Instant.now());

        SessionAckFrame ack = new SessionAckFrame(
            UUID.randomUUID(),
            Instant.now().toEpochMilli(),
            new SessionAckFrame.Payload(
                session.getSessionId(),
                "ACCEPTED",
                DEFAULT_CLUSTER_NAME,
                DEFAULT_HEARTBEAT_INTERVAL_MS,
                DEFAULT_HEARTBEAT_TIMEOUT_MS,
                Instant.now().toEpochMilli(),
                reconciledTaskIds,
                discardedTaskIds,
                null
            )
        );
        session.sendText(objectMapper.writeValueAsString(ack));

        wormAuditService.recordEvent(
            workerId,
            "WORKER",
            "WORKER_SESSION_ESTABLISHED",
            "WORKER",
            workerId,
            AuditActionStatus.SUCCESS,
            String.format("{\"session_id\":\"%s\",\"reconciled_tasks\":%d}", session.getSessionId(), reconciledTaskIds.size()),
            "127.0.0.1",
            "SOC2_CC6.8"
        );
    }

    private void handleHeartbeat(LctpWorkerSession session, HeartbeatFrame frame) throws IOException {
        session.setLastHeartbeatAt(Instant.now());
        session.setDraining(frame.payload().draining());
        if (frame.payload().hostMetrics() != null) {
            session.setLastHostMetrics(frame.payload().hostMetrics());
        }

        if (frame.payload().draining()) {
            workerNodeRepository.findById(session.getWorkerId()).ifPresent(w -> {
                if (w.getState() != WorkerState.DRAINING) {
                    w.setState(WorkerState.DRAINING);
                    workerNodeRepository.save(w);
                }
            });
        }

        HeartbeatAckFrame ack = new HeartbeatAckFrame(
            UUID.randomUUID(),
            Instant.now().toEpochMilli(),
            new HeartbeatAckFrame.Payload(session.getSessionId(), Instant.now().toEpochMilli())
        );
        session.sendText(objectMapper.writeValueAsString(ack));
    }

    private void handleDrainNotice(LctpWorkerSession session, DrainNoticeFrame frame) {
        session.setDraining(true);
        workerNodeRepository.findById(session.getWorkerId()).ifPresent(w -> {
            w.setState(WorkerState.DRAINING);
            workerNodeRepository.save(w);
        });

        wormAuditService.recordEvent(
            session.getWorkerId(),
            "WORKER",
            "WORKER_DRAIN_REQUESTED",
            "WORKER",
            session.getWorkerId(),
            AuditActionStatus.SUCCESS,
            String.format("{\"active_tasks\":%d,\"reason\":\"%s\"}", frame.payload().activeTasksRemaining(), frame.payload().reason()),
            "127.0.0.1",
            "SOC2_CC6.8"
        );
    }

    private void handleTaskProgress(LctpWorkerSession session, TaskProgressFrame frame) {
        computeTaskRepository.findById(frame.payload().taskId()).ifPresent(task -> {
            task.setInstructionsConsumed(frame.payload().instructionsConsumed());
            task.setPeakMemoryBytes(Math.max(task.getPeakMemoryBytes() != null ? task.getPeakMemoryBytes() : 0L, frame.payload().memoryUsedBytes()));
            computeTaskRepository.save(task);
        });
    }

    private void handleTaskResult(LctpWorkerSession session, TaskResultFrame frame) throws IOException {
        String taskId = frame.payload().taskId();
        Optional<ComputeTask> taskOpt = computeTaskRepository.findById(taskId);

        if (taskOpt.isPresent()) {
            ComputeTask task = taskOpt.get();
            TaskStatus terminalStatus = mapStatus(frame.payload().status());
            task.setStatus(terminalStatus);
            task.setExitCode(frame.payload().exitCode());

            if (frame.payload().metrics() != null) {
                task.setInstructionsConsumed(frame.payload().metrics().instructionsConsumed());
                task.setPeakMemoryBytes(frame.payload().metrics().peakMemoryBytes());
                task.setWallTimeMs(frame.payload().metrics().executionWallTimeMs());
            }
            task.setCompletedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));

            if (frame.payload().output() != null) {
                try {
                    task.setOutputJson(objectMapper.writeValueAsString(frame.payload().output()));
                } catch (Exception ignored) {}
            }
            if (frame.payload().error() != null) {
                try {
                    task.setErrorJson(objectMapper.writeValueAsString(frame.payload().error()));
                } catch (Exception ignored) {}
            }

            computeTaskRepository.save(task);
            session.getActiveTaskIds().remove(taskId);

            wormAuditService.recordEvent(
                session.getWorkerId(),
                "WORKER",
                "TASK_RESULT_COMMITTED",
                "TASK",
                taskId,
                AuditActionStatus.SUCCESS,
                String.format("{\"status\":\"%s\",\"exit_code\":%s}", terminalStatus, frame.payload().exitCode()),
                "127.0.0.1",
                "SOC2_CC6.8"
            );
        }

        TaskResultAckFrame ack = new TaskResultAckFrame(
            UUID.randomUUID(),
            Instant.now().toEpochMilli(),
            new TaskResultAckFrame.Payload(taskId, "COMMITTED")
        );
        session.sendText(objectMapper.writeValueAsString(ack));
    }

    @Override
    @Transactional
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        LctpWorkerSession workerSession = (LctpWorkerSession) session.getAttributes().get("workerSession");
        if (workerSession != null) {
            sessionRegistry.unregister(workerSession.getSessionId());

            // If no other sessions for this worker, mark OFFLINE
            if (sessionRegistry.getByWorkerId(workerSession.getWorkerId()).isEmpty()) {
                workerNodeRepository.findById(workerSession.getWorkerId()).ifPresent(w -> {
                    w.setState(WorkerState.OFFLINE);
                    workerNodeRepository.save(w);
                });
            }
            LOG.info("Closed LCTP session {} for worker {} (code: {}, reason: {})",
                workerSession.getSessionId(), workerSession.getWorkerId(), status.getCode(), status.getReason());
        }
    }

    private TaskStatus mapStatus(String statusStr) {
        if (statusStr == null) return TaskStatus.FAILED;
        try {
            return TaskStatus.valueOf(statusStr);
        } catch (IllegalArgumentException e) {
            return switch (statusStr.toUpperCase()) {
                case "COMPLETED" -> TaskStatus.COMPLETED;
                case "ABORTED" -> TaskStatus.ABORTED;
                case "ERR_INSTRUCTION_BUDGET_EXCEEDED" -> TaskStatus.ERR_INSTRUCTION_BUDGET_EXCEEDED;
                case "ERR_OUT_OF_MEMORY" -> TaskStatus.ERR_OUT_OF_MEMORY;
                case "ERR_TIMEOUT" -> TaskStatus.ERR_TIMEOUT;
                case "ERR_BYTECODE_INVALID" -> TaskStatus.ERR_BYTECODE_INVALID;
                case "KILLED_FORCIBLY" -> TaskStatus.KILLED_FORCIBLY;
                case "CRASHED" -> TaskStatus.CRASHED;
                default -> TaskStatus.FAILED;
            };
        }
    }
}
