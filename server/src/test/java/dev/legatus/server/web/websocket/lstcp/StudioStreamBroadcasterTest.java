package dev.legatus.server.web.websocket.lstcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import dev.legatus.server.domain.WorkerNode;
import dev.legatus.server.domain.enumeration.TaskStatus;
import dev.legatus.server.domain.enumeration.WorkerState;
import dev.legatus.server.repository.ComputeTaskRepository;
import dev.legatus.server.repository.WorkerNodeRepository;
import dev.legatus.server.security.TicketBrokerService;
import dev.legatus.server.service.dto.lstcp.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

class StudioStreamBroadcasterTest {

    private StudioSessionRegistry sessionRegistry;
    private WorkerNodeRepository workerNodeRepository;
    private ComputeTaskRepository computeTaskRepository;
    private ObjectMapper objectMapper;
    private StudioStreamBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        sessionRegistry = new StudioSessionRegistry();
        workerNodeRepository = mock(WorkerNodeRepository.class);
        computeTaskRepository = mock(ComputeTaskRepository.class);
        objectMapper = new ObjectMapper();

        broadcaster = new StudioStreamBroadcaster(
            sessionRegistry,
            workerNodeRepository,
            computeTaskRepository,
            objectMapper
        );
    }

    private LstcpStudioSession createMockSession(String sessionId, String username, WebSocketSession wsSession) {
        TicketBrokerService.StudioTicket ticket = new TicketBrokerService.StudioTicket(
            "tkt-" + sessionId, username, "ROLE_OPERATOR", Instant.now()
        );
        return new LstcpStudioSession(sessionId, wsSession, ticket);
    }

    @Test
    @DisplayName("Should broadcast cluster metric tick only to subscribed sessions")
    void testBroadcastMetricTickToSubscribedSessions() throws Exception {
        WebSocketSession ws1 = mock(WebSocketSession.class);
        when(ws1.isOpen()).thenReturn(true);
        LstcpStudioSession s1 = createMockSession("sess-1", "user1", ws1);
        s1.getSubscriptions().add("CLUSTER_METRICS");
        sessionRegistry.register(s1);

        WebSocketSession ws2 = mock(WebSocketSession.class);
        when(ws2.isOpen()).thenReturn(true);
        LstcpStudioSession s2 = createMockSession("sess-2", "user2", ws2);
        s2.getSubscriptions().add("OTHER_TOPIC");
        sessionRegistry.register(s2);

        when(workerNodeRepository.countByState(WorkerState.ONLINE)).thenReturn(4L);
        when(workerNodeRepository.countByState(WorkerState.DRAINING)).thenReturn(0L);
        when(workerNodeRepository.countByState(WorkerState.OFFLINE)).thenReturn(1L);
        when(computeTaskRepository.countByStatus(TaskStatus.QUEUED)).thenReturn(2L);
        when(computeTaskRepository.countByStatus(TaskStatus.EXECUTING)).thenReturn(6L);
        when(workerNodeRepository.findAllByState(WorkerState.ONLINE)).thenReturn(List.of(
            new WorkerNode().allocatableCores(8).maxMemoryBytes(16L * 1024 * 1024 * 1024)
        ));

        broadcaster.broadcastClusterMetricTick();

        // ws1 should receive metric tick
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(ws1).sendMessage(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("\"type\":\"EVENT_CLUSTER_METRIC_TICK\"");
        assertThat(captor.getValue().getPayload()).contains("\"nodes_online\":4");

        // ws2 should NOT receive metric tick
        verify(ws2, never()).sendMessage(any());
    }

    @Test
    @DisplayName("Should publish EVENT_TASK_LOG_CHUNK strictly to sessions subscribed to that task ID")
    void testScopedTaskLogChunkDispatch() throws Exception {
        WebSocketSession wsTarget = mock(WebSocketSession.class);
        when(wsTarget.isOpen()).thenReturn(true);
        LstcpStudioSession sTarget = createMockSession("sess-target", "user-target", wsTarget);
        sTarget.getSubscribedTaskIds().add("task-watched-123");
        sessionRegistry.register(sTarget);

        WebSocketSession wsOther = mock(WebSocketSession.class);
        when(wsOther.isOpen()).thenReturn(true);
        LstcpStudioSession sOther = createMockSession("sess-other", "user-other", wsOther);
        sOther.getSubscribedTaskIds().add("task-different-456");
        sessionRegistry.register(sOther);

        EventTaskLogChunkFrame.Payload chunkPayload = new EventTaskLogChunkFrame.Payload(
            "task-watched-123",
            75.5,
            12_000_000L,
            "Iteration 75 completed\n"
        );

        broadcaster.publishTaskLogChunk(chunkPayload);

        // Target session receives chunk
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(wsTarget).sendMessage(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("\"type\":\"EVENT_TASK_LOG_CHUNK\"");
        assertThat(captor.getValue().getPayload()).contains("\"task_id\":\"task-watched-123\"");
        assertThat(captor.getValue().getPayload()).contains("Iteration 75 completed");

        // Other session receives nothing
        verify(wsOther, never()).sendMessage(any());
    }

    @Test
    @DisplayName("Should broadcast EVENT_ENROLLMENT_REQUESTED to quarantine alert subscribers")
    void testPublishEnrollmentRequested() throws Exception {
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.isOpen()).thenReturn(true);
        LstcpStudioSession session = createMockSession("sess-alerts", "admin", ws);
        session.getSubscriptions().add("QUARANTINE_ALERTS");
        sessionRegistry.register(session);

        EventEnrollmentRequestedFrame.Payload payload = new EventEnrollmentRequestedFrame.Payload(
            "enr-1", "worker-1", "host.corp", "10.0.0.1", "linux", "x86_64",
            "AA:BB:CC", "Lion-Star-99", 8, 16L * 1024 * 1024 * 1024, System.currentTimeMillis() + 60000
        );

        broadcaster.publishEnrollmentRequested(payload);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(ws).sendMessage(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("\"type\":\"EVENT_ENROLLMENT_REQUESTED\"");
        assertThat(captor.getValue().getPayload()).contains("\"visual_code\":\"Lion-Star-99\"");
    }
}
