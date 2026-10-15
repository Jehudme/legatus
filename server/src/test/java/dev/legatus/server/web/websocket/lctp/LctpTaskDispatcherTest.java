package dev.legatus.server.web.websocket.lctp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import dev.legatus.server.domain.ComputeTask;
import dev.legatus.server.domain.WorkerNode;
import dev.legatus.server.domain.enumeration.TaskPriority;
import dev.legatus.server.domain.enumeration.TaskStatus;
import dev.legatus.server.domain.enumeration.WorkerState;
import dev.legatus.server.repository.ComputeTaskRepository;
import dev.legatus.server.security.SecretVaultService;
import dev.legatus.server.security.TicketBrokerService;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link LctpTaskDispatcher}.
 */
class LctpTaskDispatcherTest {

    private WorkerSessionRegistry sessionRegistry;
    private SecretVaultService secretVaultService;
    private ComputeTaskRepository computeTaskRepository;
    private dev.legatus.server.storage.BytecodeStorageService bytecodeStorageService;
    private dev.legatus.server.service.studio.StudioEventPublisher studioEventPublisher;
    private ObjectMapper objectMapper;
    private LctpTaskDispatcher dispatcher;

    @BeforeEach
    void setUp() throws Exception {
        sessionRegistry = new WorkerSessionRegistry();
        secretVaultService = mock(SecretVaultService.class);
        computeTaskRepository = mock(ComputeTaskRepository.class);
        bytecodeStorageService = mock(dev.legatus.server.storage.BytecodeStorageService.class);
        studioEventPublisher = mock(dev.legatus.server.service.studio.StudioEventPublisher.class);
        objectMapper = new ObjectMapper();

        when(bytecodeStorageService.load(anyString())).thenReturn(Optional.of("test-bytecode".getBytes()));

        dispatcher = new LctpTaskDispatcher(
            sessionRegistry,
            secretVaultService,
            computeTaskRepository,
            bytecodeStorageService,
            studioEventPublisher,
            objectMapper
        );
    }

    @Test
    @DisplayName("Should build DISPATCH_TASK frame, transmit over WebSocket, and transition task to EXECUTING")
    @SuppressWarnings("unchecked")
    void testSuccessfulDispatch() throws Exception {
        String workerId = "wk-dispatch-test";
        WebSocketSession wsSession = mock(WebSocketSession.class);
        when(wsSession.isOpen()).thenReturn(true);

        TicketBrokerService.WorkerTicket ticket = new TicketBrokerService.WorkerTicket(
            "ws_tkt_123",
            workerId,
            4,
            4L * 1024 * 1024 * 1024,
            100_000_000L,
            java.time.Instant.now()
        );

        LctpWorkerSession session = new LctpWorkerSession("sess-123", workerId, wsSession, ticket);
        sessionRegistry.register(session);

        WorkerNode worker = new WorkerNode()
            .workerId(workerId)
            .state(WorkerState.ONLINE);

        ComputeTask task = new ComputeTask()
            .taskId("task-to-dispatch")
            .name("echo-task")
            .status(TaskStatus.DISPATCHED)
            .priority(TaskPriority.HIGH)
            .priorityWeight(80)
            .entryPoint("run")
            .bytecodeSha256("sha256-abc")
            .instructionBudget(50_000_000L)
            .maxMemoryBytes(67108864L)
            .timeoutMs(15000)
            .secretRefsJson("[\"DB_KEY\"]")
            .inputJson("{\"key\":\"value\"}")
            .envJson("{\"ENV_VAR\":\"true\"}");

        // Setup mock secretVaultService to execute callback
        doAnswer(inv -> {
            SecretVaultService.SecretDispatchCallback callback = inv.getArgument(1);
            return callback.dispatch(Map.of("DB_KEY", "decrypted_secret_val"));
        }).when(secretVaultService).withDecryptedSecrets(any(), any());

        boolean result = dispatcher.dispatchTask(task, worker);

        assertThat(result).isTrue();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.EXECUTING);
        assertThat(session.getActiveTaskIds()).contains("task-to-dispatch");

        verify(wsSession).sendMessage(any(TextMessage.class));
        verify(computeTaskRepository).saveAndFlush(task);
        verify(studioEventPublisher).publishTaskLifecycle(any());
    }

    @Test
    @DisplayName("Should return false when worker has no active transport session")
    void testDispatchNoSession() {
        WorkerNode worker = new WorkerNode().workerId("wk-unconnected");
        ComputeTask task = new ComputeTask().taskId("task-orphan");

        boolean result = dispatcher.dispatchTask(task, worker);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should build ABORT_TASK frame and transmit over WebSocket")
    void testSuccessfulAbort() throws Exception {
        String workerId = "wk-abort-test";
        WebSocketSession wsSession = mock(WebSocketSession.class);
        when(wsSession.isOpen()).thenReturn(true);

        TicketBrokerService.WorkerTicket ticket = new TicketBrokerService.WorkerTicket(
            "ws_tkt_abort",
            workerId,
            4,
            4L * 1024 * 1024 * 1024,
            100_000_000L,
            java.time.Instant.now()
        );

        LctpWorkerSession session = new LctpWorkerSession("sess-abort", workerId, wsSession, ticket);
        sessionRegistry.register(session);

        boolean result = dispatcher.abortTask(workerId, "task-to-abort", "OPERATOR_CANCELLED", 5000);

        assertThat(result).isTrue();
        verify(wsSession).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("Should return false when attempting to abort task on non-connected worker")
    void testAbortNoSession() {
        boolean result = dispatcher.abortTask("wk-nonexistent", "task-to-abort", "TIMEOUT", 1000);
        assertThat(result).isFalse();
    }
}
