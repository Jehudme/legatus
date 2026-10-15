package dev.legatus.server.web.websocket.lctp;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * High-concurrency thread-safe in-memory registry of active LCTP worker node sessions.
 */
@Component
public class WorkerSessionRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(WorkerSessionRegistry.class);

    private final ConcurrentMap<String, LctpWorkerSession> sessionsBySessionId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> sessionIdByWorkerId = new ConcurrentHashMap<>();

    public void register(LctpWorkerSession session) {
        sessionsBySessionId.put(session.getSessionId(), session);
        sessionIdByWorkerId.put(session.getWorkerId(), session.getSessionId());
        LOG.info("Registered active LCTP session {} for worker node {}", session.getSessionId(), session.getWorkerId());
    }

    public Optional<LctpWorkerSession> unregister(String sessionId) {
        LctpWorkerSession session = sessionsBySessionId.remove(sessionId);
        if (session != null) {
            sessionIdByWorkerId.remove(session.getWorkerId(), sessionId);
            LOG.info("Unregistered LCTP session {} for worker node {}", sessionId, session.getWorkerId());
            return Optional.of(session);
        }
        return Optional.empty();
    }

    public Optional<LctpWorkerSession> getBySessionId(String sessionId) {
        return Optional.ofNullable(sessionsBySessionId.get(sessionId));
    }

    public Optional<LctpWorkerSession> getByWorkerId(String workerId) {
        String sessionId = sessionIdByWorkerId.get(workerId);
        if (sessionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessionsBySessionId.get(sessionId));
    }

    public Collection<LctpWorkerSession> getAllActiveSessions() {
        return sessionsBySessionId.values();
    }

    public int activeSessionCount() {
        return sessionsBySessionId.size();
    }

    public void clear() {
        sessionsBySessionId.clear();
        sessionIdByWorkerId.clear();
    }
}
