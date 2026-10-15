package dev.legatus.server.web.websocket.lstcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import dev.legatus.server.security.TicketBrokerService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

class StudioDeadManSweeperTest {

    private StudioSessionRegistry sessionRegistry;
    private StudioDeadManSweeper sweeper;

    @BeforeEach
    void setUp() {
        sessionRegistry = new StudioSessionRegistry();
        sweeper = new StudioDeadManSweeper(sessionRegistry);
    }

    private LstcpStudioSession createSession(String id, WebSocketSession ws) {
        TicketBrokerService.StudioTicket ticket = new TicketBrokerService.StudioTicket(
            "tkt-" + id, "operator", "ROLE_OPERATOR", Instant.now()
        );
        return new LstcpStudioSession(id, ws, ticket);
    }

    @Test
    @DisplayName("Should terminate session silent for > 30000ms with Close Code 1011")
    void testSweepSilentSession() throws Exception {
        WebSocketSession wsExpired = mock(WebSocketSession.class);
        LstcpStudioSession sExpired = createSession("sess-silent", wsExpired);
        // Set last heartbeat to 35 seconds ago
        sExpired.setLastHeartbeatUtc(System.currentTimeMillis() - 35000L);
        sessionRegistry.register(sExpired);

        WebSocketSession wsActive = mock(WebSocketSession.class);
        LstcpStudioSession sActive = createSession("sess-active", wsActive);
        // Set last heartbeat to 5 seconds ago
        sActive.setLastHeartbeatUtc(System.currentTimeMillis() - 5000L);
        sessionRegistry.register(sActive);

        sweeper.sweepSilentStudioSessions();

        verify(wsExpired).close(argThat(status -> status.getCode() == 1011));
        verify(wsActive, never()).close(any());

        assertThat(sessionRegistry.getBySessionId("sess-silent")).isEmpty();
        assertThat(sessionRegistry.getBySessionId("sess-active")).isPresent();
    }
}
