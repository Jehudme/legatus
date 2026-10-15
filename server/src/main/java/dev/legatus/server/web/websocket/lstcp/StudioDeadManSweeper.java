package dev.legatus.server.web.websocket.lstcp;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

/**
 * Sweeper executing dead-man's switch enforcement for silent Legatus Studio sessions (studio-wire.md Section 5).
 * <p>
 * Closes sessions silent for more than 30,000ms with RFC 6455 Close Code 1011.
 */
@Component
public class StudioDeadManSweeper {

    private static final Logger LOG = LoggerFactory.getLogger(StudioDeadManSweeper.class);

    public static final long STUDIO_TIMEOUT_MS = 30_000L;

    private final StudioSessionRegistry sessionRegistry;

    public StudioDeadManSweeper(StudioSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Scheduled(fixedRate = 5000)
    public void sweepSilentStudioSessions() {
        long now = System.currentTimeMillis();
        for (LstcpStudioSession session : sessionRegistry.getAllSessions()) {
            long silentMs = now - session.getLastHeartbeatUtc();
            if (silentMs > STUDIO_TIMEOUT_MS) {
                LOG.warn("Studio session {} (operator: {}) silent for {}ms (> {}ms). Terminating with 1011.",
                    session.getSessionId(), session.getOperatorUsername(), silentMs, STUDIO_TIMEOUT_MS);
                try {
                    session.getWebSocketSession().close(new CloseStatus(1011, "Studio Heartbeat Timeout"));
                } catch (IOException e) {
                    LOG.debug("Error while closing timed-out Studio session: {}", e.getMessage());
                } finally {
                    sessionRegistry.unregister(session.getSessionId());
                }
            }
        }
    }
}
