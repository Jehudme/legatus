package dev.legatus.server.web.websocket.lstcp;

import dev.legatus.server.security.TicketBrokerService;
import dev.legatus.server.service.dto.lstcp.StudioSessionInitFrame;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Encapsulates an active Legatus Studio WebSocket session and its topic/task subscriptions.
 */
public class LstcpStudioSession {

    private final String sessionId;
    private final String operatorUsername;
    private final String role;
    private final WebSocketSession webSocketSession;
    private final Set<String> subscriptions = ConcurrentHashMap.newKeySet();
    private final Set<String> subscribedTaskIds = ConcurrentHashMap.newKeySet();
    private volatile long lastHeartbeatUtc;
    private volatile StudioSessionInitFrame.ViewportFilter viewportFilter;

    public LstcpStudioSession(
        String sessionId,
        WebSocketSession webSocketSession,
        TicketBrokerService.StudioTicket ticket
    ) {
        this.sessionId = sessionId;
        this.operatorUsername = ticket.operatorUsername();
        this.role = ticket.role();
        this.webSocketSession = webSocketSession;
        this.lastHeartbeatUtc = System.currentTimeMillis();
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getOperatorUsername() {
        return operatorUsername;
    }

    public String getRole() {
        return role;
    }

    public WebSocketSession getWebSocketSession() {
        return webSocketSession;
    }

    public Set<String> getSubscriptions() {
        return subscriptions;
    }

    public Set<String> getSubscribedTaskIds() {
        return subscribedTaskIds;
    }

    public long getLastHeartbeatUtc() {
        return lastHeartbeatUtc;
    }

    public void setLastHeartbeatUtc(long lastHeartbeatUtc) {
        this.lastHeartbeatUtc = lastHeartbeatUtc;
    }

    public StudioSessionInitFrame.ViewportFilter getViewportFilter() {
        return viewportFilter;
    }

    public void setViewportFilter(StudioSessionInitFrame.ViewportFilter viewportFilter) {
        this.viewportFilter = viewportFilter;
    }

    public synchronized void sendText(String json) throws IOException {
        if (webSocketSession != null && webSocketSession.isOpen()) {
            webSocketSession.sendMessage(new TextMessage(json));
        }
    }
}
