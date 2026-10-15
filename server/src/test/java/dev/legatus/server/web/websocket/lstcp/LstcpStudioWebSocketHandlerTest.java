package dev.legatus.server.web.websocket.lstcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import dev.legatus.server.domain.enumeration.TaskStatus;
import dev.legatus.server.domain.enumeration.WorkerState;
import dev.legatus.server.repository.ComputeTaskRepository;
import dev.legatus.server.repository.WorkerNodeRepository;
import dev.legatus.server.security.TicketBrokerService;
import dev.legatus.server.service.dto.lstcp.StudioHeartbeatFrame;
import dev.legatus.server.service.dto.lstcp.StudioSessionInitFrame;
import dev.legatus.server.service.dto.lstcp.SubscribeTaskLogsFrame;
import dev.legatus.server.service.dto.lstcp.UnsubscribeTaskLogsFrame;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

class LstcpStudioWebSocketHandlerTest {

    private StudioSessionRegistry sessionRegistry;
    private WorkerNodeRepository workerNodeRepository;
    private ComputeTaskRepository computeTaskRepository;
    private ObjectMapper objectMapper;
    private LstcpStudioWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        sessionRegistry = new StudioSessionRegistry();
        workerNodeRepository = mock(WorkerNodeRepository.class);
        computeTaskRepository = mock(ComputeTaskRepository.class);
        objectMapper = new ObjectMapper();

        handler = new LstcpStudioWebSocketHandler(
            sessionRegistry,
            workerNodeRepository,
            computeTaskRepository,
            objectMapper
        );
    }

    @Test
    @DisplayName("Should establish connection and register Studio session when ticket is present")
    void testConnectionEstablishedWithTicket() throws Exception {
        WebSocketSession wsSession = mock(WebSocketSession.class);
        Map<String, Object> attrs = new HashMap<>();
        TicketBrokerService.StudioTicket ticket = new TicketBrokerService.StudioTicket(
            "st_tkt_123", "operator@legatus.internal", "ROLE_OPERATOR", Instant.now()
        );
        attrs.put(LstcpHandshakeInterceptor.STUDIO_TICKET_ATTR, ticket);
        when(wsSession.getAttributes()).thenReturn(attrs);

        handler.afterConnectionEstablished(wsSession);

        assertThat(sessionRegistry.getActiveSessionCount()).isEqualTo(1);
        LstcpStudioSession session = sessionRegistry.getAllSessions().iterator().next();
        assertThat(session.getOperatorUsername()).isEqualTo("operator@legatus.internal");
        assertThat(session.getRole()).isEqualTo("ROLE_OPERATOR");
    }

    @Test
    @DisplayName("Should close socket with 1008 when connection established without ticket attribute")
    void testConnectionEstablishedWithoutTicket() throws Exception {
        WebSocketSession wsSession = mock(WebSocketSession.class);
        when(wsSession.getAttributes()).thenReturn(new HashMap<>());

        handler.afterConnectionEstablished(wsSession);

        verify(wsSession).close(any(CloseStatus.class));
        assertThat(sessionRegistry.getActiveSessionCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should process STUDIO_SESSION_INIT and reply with STUDIO_SESSION_ACK containing cluster metrics")
    void testHandleStudioSessionInit() throws Exception {
        WebSocketSession wsSession = mock(WebSocketSession.class);
        when(wsSession.isOpen()).thenReturn(true);
        Map<String, Object> attrs = new HashMap<>();
        TicketBrokerService.StudioTicket ticket = new TicketBrokerService.StudioTicket(
            "st_tkt_123", "operator@legatus.internal", "ROLE_OPERATOR", Instant.now()
        );
        attrs.put(LstcpHandshakeInterceptor.STUDIO_TICKET_ATTR, ticket);
        when(wsSession.getAttributes()).thenReturn(attrs);

        handler.afterConnectionEstablished(wsSession);

        when(workerNodeRepository.countByState(WorkerState.PENDING_APPROVAL)).thenReturn(2L);
        when(workerNodeRepository.countByState(WorkerState.ONLINE)).thenReturn(10L);
        when(computeTaskRepository.countByStatus(TaskStatus.QUEUED)).thenReturn(5L);
        when(computeTaskRepository.countByStatus(TaskStatus.EXECUTING)).thenReturn(8L);

        StudioSessionInitFrame initFrame = new StudioSessionInitFrame(
            UUID.randomUUID(),
            System.currentTimeMillis(),
            new StudioSessionInitFrame.Payload(
                "1.0.0",
                List.of("CLUSTER_METRICS", "QUARANTINE_ALERTS", "TASK_LIFECYCLE"),
                new StudioSessionInitFrame.ViewportFilter("TOPOLOGY", "prod-cluster", 100)
            )
        );

        String json = objectMapper.writeValueAsString(initFrame);
        handler.handleTextMessage(wsSession, new TextMessage(json));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(wsSession).sendMessage(captor.capture());

        String ackJson = captor.getValue().getPayload();
        assertThat(ackJson).contains("\"type\":\"STUDIO_SESSION_ACK\"");
        assertThat(ackJson).contains("\"pending_enrollments_count\":2");
        assertThat(ackJson).contains("\"online_workers_count\":10");
        assertThat(ackJson).contains("\"queued_tasks_count\":5");
        assertThat(ackJson).contains("\"executing_tasks_count\":8");

        LstcpStudioSession session = sessionRegistry.getAllSessions().iterator().next();
        assertThat(session.getSubscriptions()).contains("CLUSTER_METRICS", "QUARANTINE_ALERTS");
        assertThat(session.getViewportFilter()).isNotNull();
        assertThat(session.getViewportFilter().clusterPool()).isEqualTo("prod-cluster");
    }

    @Test
    @DisplayName("Should handle SUBSCRIBE_TASK_LOGS and UNSUBSCRIBE_TASK_LOGS")
    void testHandleTaskLogSubscriptions() throws Exception {
        WebSocketSession wsSession = mock(WebSocketSession.class);
        when(wsSession.isOpen()).thenReturn(true);
        Map<String, Object> attrs = new HashMap<>();
        TicketBrokerService.StudioTicket ticket = new TicketBrokerService.StudioTicket(
            "st_tkt_123", "operator@legatus.internal", "ROLE_OPERATOR", Instant.now()
        );
        attrs.put(LstcpHandshakeInterceptor.STUDIO_TICKET_ATTR, ticket);
        when(wsSession.getAttributes()).thenReturn(attrs);

        handler.afterConnectionEstablished(wsSession);
        LstcpStudioSession session = sessionRegistry.getAllSessions().iterator().next();

        // Subscribe
        SubscribeTaskLogsFrame sub = new SubscribeTaskLogsFrame(
            UUID.randomUUID(), System.currentTimeMillis(),
            new SubscribeTaskLogsFrame.Payload("task-xyz-123")
        );
        handler.handleTextMessage(wsSession, new TextMessage(objectMapper.writeValueAsString(sub)));
        assertThat(session.getSubscribedTaskIds()).contains("task-xyz-123");

        // Unsubscribe
        UnsubscribeTaskLogsFrame unsub = new UnsubscribeTaskLogsFrame(
            UUID.randomUUID(), System.currentTimeMillis(),
            new UnsubscribeTaskLogsFrame.Payload("task-xyz-123")
        );
        handler.handleTextMessage(wsSession, new TextMessage(objectMapper.writeValueAsString(unsub)));
        assertThat(session.getSubscribedTaskIds()).doesNotContain("task-xyz-123");
    }

    @Test
    @DisplayName("Should handle STUDIO_HEARTBEAT and respond with STUDIO_HEARTBEAT_ACK")
    void testHandleStudioHeartbeat() throws Exception {
        WebSocketSession wsSession = mock(WebSocketSession.class);
        when(wsSession.isOpen()).thenReturn(true);
        Map<String, Object> attrs = new HashMap<>();
        TicketBrokerService.StudioTicket ticket = new TicketBrokerService.StudioTicket(
            "st_tkt_123", "operator@legatus.internal", "ROLE_OPERATOR", Instant.now()
        );
        attrs.put(LstcpHandshakeInterceptor.STUDIO_TICKET_ATTR, ticket);
        when(wsSession.getAttributes()).thenReturn(attrs);

        handler.afterConnectionEstablished(wsSession);

        StudioHeartbeatFrame hb = new StudioHeartbeatFrame(
            UUID.randomUUID(), System.currentTimeMillis(),
            new StudioHeartbeatFrame.Payload(1L)
        );
        handler.handleTextMessage(wsSession, new TextMessage(objectMapper.writeValueAsString(hb)));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(wsSession).sendMessage(captor.capture());

        assertThat(captor.getValue().getPayload()).contains("\"type\":\"STUDIO_HEARTBEAT_ACK\"");
        assertThat(captor.getValue().getPayload()).contains("\"server_time\":");
    }

    @Test
    @DisplayName("Should unregister session upon connection close")
    void testConnectionClosed() throws Exception {
        WebSocketSession wsSession = mock(WebSocketSession.class);
        Map<String, Object> attrs = new HashMap<>();
        TicketBrokerService.StudioTicket ticket = new TicketBrokerService.StudioTicket(
            "st_tkt_123", "operator@legatus.internal", "ROLE_OPERATOR", Instant.now()
        );
        attrs.put(LstcpHandshakeInterceptor.STUDIO_TICKET_ATTR, ticket);
        when(wsSession.getAttributes()).thenReturn(attrs);

        handler.afterConnectionEstablished(wsSession);
        assertThat(sessionRegistry.getActiveSessionCount()).isEqualTo(1);

        handler.afterConnectionClosed(wsSession, CloseStatus.NORMAL);
        assertThat(sessionRegistry.getActiveSessionCount()).isEqualTo(0);
    }
}
