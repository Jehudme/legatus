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
import dev.legatus.server.service.dto.lctp.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class LctpWorkerWebSocketHandlerTest {

    @Mock
    private WorkerNodeRepository workerNodeRepository;

    @Mock
    private ComputeTaskRepository computeTaskRepository;

    @Mock
    private WormAuditService wormAuditService;

    @Mock
    private WebSocketSession webSocketSession;

    private WorkerSessionRegistry sessionRegistry;
    private ObjectMapper objectMapper;
    private LctpWorkerWebSocketHandler handler;

    private final String WORKER_ID = "worker-lctp-test";
    private TicketBrokerService.WorkerTicket ticket;

    @BeforeEach
    void setUp() {
        sessionRegistry = new WorkerSessionRegistry();
        objectMapper = new ObjectMapper();
        handler = new LctpWorkerWebSocketHandler(
            sessionRegistry,
            workerNodeRepository,
            computeTaskRepository,
            wormAuditService,
            objectMapper
        );

        ticket = new TicketBrokerService.WorkerTicket(
            "ws_tkt_1234",
            WORKER_ID,
            16,
            32000000000L,
            50000000L,
            Instant.now()
        );
    }

    private void establishConnection(Map<String, Object> sessionAttributes) throws Exception {
        when(webSocketSession.getAttributes()).thenReturn(sessionAttributes);
        lenient().when(webSocketSession.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(webSocketSession);
    }

    @Test
    @DisplayName("Should successfully negotiate SESSION_INIT, reconcile in-flight tasks, and mark worker ONLINE")
    void testSessionInitSuccess() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(LctpHandshakeInterceptor.WORKER_TICKET_ATTR, ticket);
        establishConnection(attributes);

        WorkerNode worker = new WorkerNode();
        worker.setWorkerId(WORKER_ID);
        worker.setState(WorkerState.APPROVED);
        when(workerNodeRepository.findById(WORKER_ID)).thenReturn(Optional.of(worker));

        // Setup 1 reconcilable task and 1 invalid task
        ComputeTask validTask = new ComputeTask()
            .taskId("task-valid")
            .status(TaskStatus.DISPATCHED)
            .assignedWorker(worker);
        when(computeTaskRepository.findById("task-valid")).thenReturn(Optional.of(validTask));
        when(computeTaskRepository.findById("task-stale")).thenReturn(Optional.empty());

        SessionInitFrame initFrame = new SessionInitFrame(
            UUID.randomUUID(),
            Instant.now().toEpochMilli(),
            new SessionInitFrame.Payload(
                WORKER_ID,
                null,
                "node.corp",
                "linux",
                "x86_64",
                "1.0.0",
                new SessionInitFrame.RuntimeInfo("solix", "1.0.0", "v1.1", List.of("arc_deterministic")),
                new SessionInitFrame.HardwareInfo(8, 16, 32000000000L, false, null),
                new SessionInitFrame.Capacity(12, 16000000000L, 50000000L),
                List.of(
                    new SessionInitFrame.ReconcileTask("task-valid", "EXECUTING", 500L, 100L),
                    new SessionInitFrame.ReconcileTask("task-stale", "EXECUTING", 0L, 0L)
                )
            )
        );

        handler.handleTextMessage(webSocketSession, new TextMessage(objectMapper.writeValueAsString(initFrame)));

        // Verify worker updated
        assertThat(worker.getState()).isEqualTo(WorkerState.ONLINE);
        assertThat(worker.getAllocatableCores()).isEqualTo(12);
        verify(workerNodeRepository).save(worker);

        // Verify task reconciled
        assertThat(validTask.getStatus()).isEqualTo(TaskStatus.EXECUTING);
        assertThat(validTask.getInstructionsConsumed()).isEqualTo(500L);
        verify(computeTaskRepository).save(validTask);

        // Verify SESSION_ACK sent with reconciled and discarded lists
        ArgumentCaptor<TextMessage> msgCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession).sendMessage(msgCaptor.capture());
        SessionAckFrame ack = (SessionAckFrame) objectMapper.readValue(msgCaptor.getValue().getPayload(), LctpFrame.class);
        assertThat(ack.payload().status()).isEqualTo("ACCEPTED");
        assertThat(ack.payload().reconciledTaskIds()).containsExactly("task-valid");
        assertThat(ack.payload().discardedTaskIds()).containsExactly("task-stale");

        verify(wormAuditService).recordEvent(
            eq(WORKER_ID), eq("WORKER"), eq("WORKER_SESSION_ESTABLISHED"), eq("WORKER"), eq(WORKER_ID),
            eq(AuditActionStatus.SUCCESS), any(), eq("127.0.0.1"), eq("SOC2_CC6.8")
        );
    }

    @Test
    @DisplayName("Should reply HEARTBEAT_ACK on HEARTBEAT and update session timestamp")
    void testHeartbeatHandling() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(LctpHandshakeInterceptor.WORKER_TICKET_ATTR, ticket);
        establishConnection(attributes);

        HeartbeatFrame hb = new HeartbeatFrame(
            UUID.randomUUID(),
            Instant.now().toEpochMilli(),
            new HeartbeatFrame.Payload(
                "sess-any",
                false,
                new HeartbeatFrame.HostMetrics(10.0, 0.0, 1000L, 2000L),
                List.of()
            )
        );

        handler.handleTextMessage(webSocketSession, new TextMessage(objectMapper.writeValueAsString(hb)));

        ArgumentCaptor<TextMessage> msgCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession).sendMessage(msgCaptor.capture());
        HeartbeatAckFrame ack = (HeartbeatAckFrame) objectMapper.readValue(msgCaptor.getValue().getPayload(), LctpFrame.class);
        assertThat(ack.type()).isEqualTo("HEARTBEAT_ACK");
    }

    @Test
    @DisplayName("Should transition worker to DRAINING on DRAIN_NOTICE")
    void testDrainNotice() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(LctpHandshakeInterceptor.WORKER_TICKET_ATTR, ticket);
        establishConnection(attributes);

        WorkerNode worker = new WorkerNode();
        worker.setWorkerId(WORKER_ID);
        worker.setState(WorkerState.ONLINE);
        when(workerNodeRepository.findById(WORKER_ID)).thenReturn(Optional.of(worker));

        DrainNoticeFrame drain = new DrainNoticeFrame(
            UUID.randomUUID(),
            Instant.now().toEpochMilli(),
            new DrainNoticeFrame.Payload(true, 1, "OPERATOR_MAINTENANCE")
        );

        handler.handleTextMessage(webSocketSession, new TextMessage(objectMapper.writeValueAsString(drain)));

        assertThat(worker.getState()).isEqualTo(WorkerState.DRAINING);
        verify(workerNodeRepository).save(worker);
    }

    @Test
    @DisplayName("Should commit task outcome to database and reply TASK_RESULT_ACK on TASK_RESULT")
    void testTaskResultCommit() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(LctpHandshakeInterceptor.WORKER_TICKET_ATTR, ticket);
        establishConnection(attributes);

        ComputeTask task = new ComputeTask()
            .taskId("task-exec-1")
            .status(TaskStatus.EXECUTING);
        when(computeTaskRepository.findById("task-exec-1")).thenReturn(Optional.of(task));

        TaskResultFrame result = new TaskResultFrame(
            UUID.randomUUID(),
            Instant.now().toEpochMilli(),
            new TaskResultFrame.Payload(
                "task-exec-1",
                "COMPLETED",
                0,
                new TaskResultFrame.TaskMetrics(100000L, 8388608L, 1200L),
                Map.of("res", "success"),
                null
            )
        );

        handler.handleTextMessage(webSocketSession, new TextMessage(objectMapper.writeValueAsString(result)));

        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(task.getExitCode()).isEqualTo(0);
        assertThat(task.getInstructionsConsumed()).isEqualTo(100000L);
        assertThat(task.getCompletedAt()).isNotNull();
        verify(computeTaskRepository).save(task);

        ArgumentCaptor<TextMessage> msgCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession).sendMessage(msgCaptor.capture());
        TaskResultAckFrame ack = (TaskResultAckFrame) objectMapper.readValue(msgCaptor.getValue().getPayload(), LctpFrame.class);
        assertThat(ack.payload().status()).isEqualTo("COMMITTED");
    }

    @Test
    @DisplayName("Should immediately close connection with 1009 when frame exceeds 32 MB limit")
    void testOversizedFrameRejection() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(LctpHandshakeInterceptor.WORKER_TICKET_ATTR, ticket);
        establishConnection(attributes);

        TextMessage oversizedMessage = mock(TextMessage.class);
        when(oversizedMessage.getPayloadLength()).thenReturn(35_000_000); // 35 MB

        handler.handleTextMessage(webSocketSession, oversizedMessage);

        verify(webSocketSession).close(eq(new CloseStatus(1009, "ERR_FRAME_TOO_LARGE")));
    }
}
