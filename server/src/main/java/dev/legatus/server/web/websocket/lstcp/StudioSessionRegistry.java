package dev.legatus.server.web.websocket.lstcp;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Thread-safe in-memory registry of all active Legatus Studio LSTCP WebSocket sessions.
 */
@Component
public class StudioSessionRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(StudioSessionRegistry.class);

    private final Map<String, LstcpStudioSession> sessions = new ConcurrentHashMap<>();

    public void register(LstcpStudioSession session) {
        sessions.put(session.getSessionId(), session);
        LOG.info("Registered active Studio LSTCP session {} for operator {}",
            session.getSessionId(), session.getOperatorUsername());
    }

    public Optional<LstcpStudioSession> unregister(String sessionId) {
        LstcpStudioSession removed = sessions.remove(sessionId);
        if (removed != null) {
            LOG.info("Unregistered Studio LSTCP session {} for operator {}",
                sessionId, removed.getOperatorUsername());
            return Optional.of(removed);
        }
        return Optional.empty();
    }

    public Optional<LstcpStudioSession> getBySessionId(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public Collection<LstcpStudioSession> getAllSessions() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }
}
