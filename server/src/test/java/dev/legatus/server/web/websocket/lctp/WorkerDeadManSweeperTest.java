package dev.legatus.server.web.websocket.lctp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import dev.legatus.server.domain.ComputeTask;
import dev.legatus.server.domain.WorkerNode;
import dev.legatus.server.domain.enumeration.AuditActionStatus;
import dev.legatus.server.domain.enumeration.TaskStatus;
import dev.legatus.server.domain.enumeration.WorkerState;
import dev.legatus.server.repository.ComputeTaskRepository;
import dev.legatus.server.repository.WorkerNodeRepository;
import dev.legatus.server.security.TicketBrokerService;
import dev.legatus.server.service.WormAuditService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
class WorkerDeadManSweeperTest {

    @Mock
    private WorkerNodeRepository workerNodeRepository;

    @Mock
    private ComputeTaskRepository computeTaskRepository;

    @Mock
    private WormAuditService wormAuditService;

    @Mock
    private WebSocketSession webSocketSession;

    private WorkerSessionRegistry sessionRegistry;
    private WorkerDeadManSweeper sweeper;

    private final String WORKER_ID = "wk-deadman-test";

    @BeforeEach
    void setUp() {
        sessionRegistry = new WorkerSessionRegistry();
        sweeper = new WorkerDeadManSweeper(
            sessionRegistry,
            workerNodeRepository,
            computeTaskRepository,
            wormAuditService
        );
    }

    @Test
    @DisplayName("Should not eject healthy worker when heartbeat is within 15-second window")
    void testActiveSessionNotEjected() {
        TicketBrokerService.WorkerTicket ticket = new TicketBrokerService.WorkerTicket(
            "ws_tkt_1", WORKER_ID, 8, 16000000000L, 50000000L, Instant.now()
        );
        LctpWorkerSession session = new LctpWorkerSession("sess-1", WORKER_ID, webSocketSession, ticket);
        session.setLastHeartbeatAt(Instant.now().minus(5, ChronoUnit.SECONDS)); // 5s ago (< 15s)
        sessionRegistry.register(session);

        sweeper.sweepSilentWorkers();

        assertThat(sessionRegistry.activeSessionCount()).isEqualTo(1);
        verifyNoInteractions(workerNodeRepository);
        verifyNoInteractions(computeTaskRepository);
    }

    @Test
    @DisplayName("Should eject silent worker (> 15s), mark node OFFLINE, and recover orphan in-flight tasks")
    void testSilentWorkerEjectedAndTasksRecovered() throws Exception {
        TicketBrokerService.WorkerTicket ticket = new TicketBrokerService.WorkerTicket(
            "ws_tkt_1", WORKER_ID, 8, 16000000000L, 50000000L, Instant.now()
        );
        LctpWorkerSession session = new LctpWorkerSession("sess-1", WORKER_ID, webSocketSession, ticket);
        session.setLastHeartbeatAt(Instant.now().minus(16, ChronoUnit.SECONDS)); // 16s ago (> 15s)
        sessionRegistry.register(session);

        WorkerNode worker = new WorkerNode();
        worker.setWorkerId(WORKER_ID);
        worker.setState(WorkerState.ONLINE);
        when(workerNodeRepository.findById(WORKER_ID)).thenReturn(Optional.of(worker));

        // Task 1: Idempotent and has retries remaining -> must be REQUEUED
        ComputeTask taskRequeued = new ComputeTask()
            .taskId("task-retry")
            .status(TaskStatus.EXECUTING)
            .idempotent(true)
            .attemptCount(0)
            .maxRetries(2)
            .assignedWorker(worker);

        // Task 2: Non-idempotent -> must be marked ERR_WORKER_LOST
        ComputeTask taskNonIdempotent = new ComputeTask()
            .taskId("task-non-idempotent")
            .status(TaskStatus.EXECUTING)
            .idempotent(false)
            .attemptCount(0)
            .maxRetries(2)
            .assignedWorker(worker);

        // Task 3: Idempotent but max retries exhausted -> must be marked ERR_WORKER_LOST
        ComputeTask taskRetriesExhausted = new ComputeTask()
            .taskId("task-exhausted")
            .status(TaskStatus.EXECUTING)
            .idempotent(true)
            .attemptCount(2)
            .maxRetries(2)
            .assignedWorker(worker);

        when(computeTaskRepository.findAllByAssignedWorkerWorkerIdAndStatusIn(
            eq(WORKER_ID), any()
        )).thenReturn(List.of(taskRequeued, taskNonIdempotent, taskRetriesExhausted));

        sweeper.sweepSilentWorkers();

        // 1. Socket closed with 1011
        verify(webSocketSession).close(WorkerDeadManSweeper.TIMEOUT_CLOSE_STATUS);

        // 2. Session removed from registry
        assertThat(sessionRegistry.activeSessionCount()).isEqualTo(0);

        // 3. Worker marked OFFLINE
        assertThat(worker.getState()).isEqualTo(WorkerState.OFFLINE);
        verify(workerNodeRepository).save(worker);

        // 4. Task 1 requeued
        assertThat(taskRequeued.getStatus()).isEqualTo(TaskStatus.QUEUED);
        assertThat(taskRequeued.getAttemptCount()).isEqualTo(1);
        assertThat(taskRequeued.getAssignedWorker()).isNull();
        verify(computeTaskRepository).save(taskRequeued);

        // 5. Task 2 failed (non-idempotent)
        assertThat(taskNonIdempotent.getStatus()).isEqualTo(TaskStatus.ERR_WORKER_LOST);
        assertThat(taskNonIdempotent.getCompletedAt()).isNotNull();
        verify(computeTaskRepository).save(taskNonIdempotent);

        // 6. Task 3 failed (retries exhausted)
        assertThat(taskRetriesExhausted.getStatus()).isEqualTo(TaskStatus.ERR_WORKER_LOST);
        assertThat(taskRetriesExhausted.getCompletedAt()).isNotNull();
        verify(computeTaskRepository).save(taskRetriesExhausted);

        // 7. Statutory WORM audit event appended
        verify(wormAuditService).recordEvent(
            eq("system-deadman"),
            eq("SYSTEM"),
            eq("WORKER_DEAD_MAN_TIMEOUT"),
            eq("WORKER"),
            eq(WORKER_ID),
            eq(AuditActionStatus.SUCCESS),
            any(),
            eq("127.0.0.1"),
            eq("SOC2_CC6.8")
        );
    }
}
