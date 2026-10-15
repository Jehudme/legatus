package dev.legatus.server.web.websocket.lstcp;

import dev.legatus.server.config.LctpWebSocketConfiguration;
import dev.legatus.server.domain.enumeration.TaskStatus;
import dev.legatus.server.domain.enumeration.WorkerState;
import dev.legatus.server.repository.ComputeTaskRepository;
import dev.legatus.server.repository.WorkerNodeRepository;
import dev.legatus.server.security.TicketBrokerService;
import dev.legatus.server.service.dto.lstcp.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

/**
 * WebSocket handler managing full-duplex LSTCP telemetry connections for Legatus Studio (studio-wire.md).
 */
@Component
public class LstcpStudioWebSocketHandler extends TextWebSocketHandler {

    private static final Logger LOG = LoggerFactory.getLogger(LstcpStudioWebSocketHandler.class);
    private static final String SESSION_ATTR_KEY = "LSTCP_STUDIO_SESSION";

    private final StudioSessionRegistry sessionRegistry;
    private final WorkerNodeRepository workerNodeRepository;
    private final ComputeTaskRepository computeTaskRepository;
    private final ObjectMapper objectMapper;

    public LstcpStudioWebSocketHandler(
        StudioSessionRegistry sessionRegistry,
        WorkerNodeRepository workerNodeRepository,
        ComputeTaskRepository computeTaskRepository,
        ObjectMapper objectMapper
    ) {
        this.sessionRegistry = sessionRegistry;
        this.workerNodeRepository = workerNodeRepository;
        this.computeTaskRepository = computeTaskRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        TicketBrokerService.StudioTicket ticket = (TicketBrokerService.StudioTicket)
            session.getAttributes().get(LstcpHandshakeInterceptor.STUDIO_TICKET_ATTR);

        if (ticket == null) {
            LOG.warn("No StudioTicket found in session attributes. Closing socket.");
            try {
                session.close(new CloseStatus(1008, "Missing Handshake Ticket"));
            } catch (IOException ignored) {}
            return;
        }

        String sessionId = "st-sess-" + UUID.randomUUID().toString().substring(0, 8);
        LstcpStudioSession studioSession = new LstcpStudioSession(sessionId, session, ticket);
        session.getAttributes().put(SESSION_ATTR_KEY, studioSession);
        sessionRegistry.register(studioSession);

        LOG.info("Established LSTCP transport channel for operator {} (role: {}, session: {})",
            ticket.operatorUsername(), ticket.role(), sessionId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (message.getPayloadLength() > LctpWebSocketConfiguration.BUFFER_CEILING_BYTES) {
            LOG.error("Oversized LSTCP frame rejected ({} bytes, limit: 32MB). Closing socket.",
                message.getPayloadLength());
            session.close(new CloseStatus(1009, "Frame Too Large"));
            return;
        }

        LstcpStudioSession studioSession = (LstcpStudioSession) session.getAttributes().get(SESSION_ATTR_KEY);
        if (studioSession == null) {
            session.close(new CloseStatus(1008, "Session Not Initialized"));
            return;
        }

        LstcpFrame frame;
        try {
            frame = objectMapper.readValue(message.getPayload(), LstcpFrame.class);
        } catch (Exception e) {
            LOG.warn("Malformed LSTCP frame received from operator {}: {}",
                studioSession.getOperatorUsername(), e.getMessage());
            return;
        }

        switch (frame) {
            case StudioSessionInitFrame init -> handleSessionInit(studioSession, init);
            case SubscribeTaskLogsFrame sub -> {
                studioSession.getSubscribedTaskIds().add(sub.payload().taskId());
                LOG.debug("Operator {} subscribed to task logs for {}",
                    studioSession.getOperatorUsername(), sub.payload().taskId());
            }
            case UnsubscribeTaskLogsFrame unsub -> {
                studioSession.getSubscribedTaskIds().remove(unsub.payload().taskId());
                LOG.debug("Operator {} unsubscribed from task logs for {}",
                    studioSession.getOperatorUsername(), unsub.payload().taskId());
            }
            case StudioHeartbeatFrame hb -> {
                studioSession.setLastHeartbeatUtc(System.currentTimeMillis());
                StudioHeartbeatAckFrame ack = new StudioHeartbeatAckFrame(
                    UUID.randomUUID(),
                    System.currentTimeMillis(),
                    new StudioHeartbeatAckFrame.Payload(System.currentTimeMillis())
                );
                studioSession.sendText(objectMapper.writeValueAsString(ack));
            }
            default -> LOG.debug("Unhandled LSTCP frame: {}", frame.type());
        }
    }

    private void handleSessionInit(LstcpStudioSession studioSession, StudioSessionInitFrame init) throws IOException {
        if (init.payload().subscriptions() != null) {
            studioSession.getSubscriptions().addAll(init.payload().subscriptions());
        }
        if (init.payload().viewportFilter() != null) {
            studioSession.setViewportFilter(init.payload().viewportFilter());
        }

        int pendingEnrollments = (int) workerNodeRepository.countByState(WorkerState.PENDING_APPROVAL);
        int onlineWorkers = (int) workerNodeRepository.countByState(WorkerState.ONLINE);
        int queuedTasks = (int) computeTaskRepository.countByStatus(TaskStatus.QUEUED);
        int executingTasks = (int) computeTaskRepository.countByStatus(TaskStatus.EXECUTING);

        StudioSessionAckFrame ack = new StudioSessionAckFrame(
            UUID.randomUUID(),
            System.currentTimeMillis(),
            new StudioSessionAckFrame.Payload(
                studioSession.getSessionId(),
                "production-core",
                10000L,
                pendingEnrollments,
                onlineWorkers,
                queuedTasks,
                executingTasks
            )
        );

        String json = objectMapper.writeValueAsString(ack);
        studioSession.sendText(json);
        LOG.info("Acknowledged Studio session {} with initial cluster snapshot (online={}, queued={})",
            studioSession.getSessionId(), onlineWorkers, queuedTasks);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        LstcpStudioSession studioSession = (LstcpStudioSession) session.getAttributes().get(SESSION_ATTR_KEY);
        if (studioSession != null) {
            sessionRegistry.unregister(studioSession.getSessionId());
            LOG.info("Closed LSTCP transport channel for operator {} (session: {}, status: {})",
                studioSession.getOperatorUsername(), studioSession.getSessionId(), status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        LOG.error("LSTCP transport error on session {}: {}", session.getId(), exception.getMessage());
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException ignored) {}
    }
}
