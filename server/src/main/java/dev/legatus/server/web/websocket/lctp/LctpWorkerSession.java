package dev.legatus.server.web.websocket.lctp;

import dev.legatus.server.security.TicketBrokerService.WorkerTicket;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Thread-safe encapsulation of an active LCTP worker transport session.
 */
public class LctpWorkerSession {

    private final String sessionId;
    private final String workerId;
    private final WebSocketSession webSocketSession;
    private final WorkerTicket ticket;
    private final Set<String> activeTaskIds = ConcurrentHashMap.newKeySet();
    private volatile Instant lastHeartbeatAt;
    private volatile boolean draining;
    private volatile String state;
    private volatile dev.legatus.server.service.dto.lctp.HeartbeatFrame.HostMetrics lastHostMetrics;

    public LctpWorkerSession(String sessionId, String workerId, WebSocketSession webSocketSession, WorkerTicket ticket) {
        this.sessionId = sessionId;
        this.workerId = workerId;
        this.webSocketSession = webSocketSession;
        this.ticket = ticket;
        this.lastHeartbeatAt = Instant.now();
        this.draining = false;
        this.state = "NEGOTIATING";
    }

    public synchronized void sendText(String payload) throws IOException {
        if (webSocketSession.isOpen()) {
            webSocketSession.sendMessage(new TextMessage(payload));
        }
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getWorkerId() {
        return workerId;
    }

    public WebSocketSession getWebSocketSession() {
        return webSocketSession;
    }

    public WorkerTicket getTicket() {
        return ticket;
    }

    public Set<String> getActiveTaskIds() {
        return activeTaskIds;
    }

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(Instant lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public boolean isDraining() {
        return draining;
    }

    public void setDraining(boolean draining) {
        this.draining = draining;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public dev.legatus.server.service.dto.lctp.HeartbeatFrame.HostMetrics getLastHostMetrics() {
        return lastHostMetrics;
    }

    public void setLastHostMetrics(dev.legatus.server.service.dto.lctp.HeartbeatFrame.HostMetrics lastHostMetrics) {
        this.lastHostMetrics = lastHostMetrics;
    }
}
