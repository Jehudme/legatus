package dev.legatus.server.web.websocket.lctp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import dev.legatus.server.security.TicketBrokerService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;

@ExtendWith(MockitoExtension.class)
class LctpHandshakeInterceptorTest {

    @Mock
    private TicketBrokerService ticketBrokerService;

    @Mock
    private ServletServerHttpRequest request;

    @Mock
    private HttpServletRequest servletRequest;

    @Mock
    private ServerHttpResponse response;

    @Mock
    private WebSocketHandler wsHandler;

    private LctpHandshakeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new LctpHandshakeInterceptor(ticketBrokerService);
    }

    @Test
    @DisplayName("Should reject handshake with 401 when ticket query parameter is missing")
    void testMissingTicketRejection() {
        when(request.getServletRequest()).thenReturn(servletRequest);
        when(servletRequest.getParameter("ticket")).thenReturn(null);

        Map<String, Object> attributes = new HashMap<>();
        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        assertThat(attributes).isEmpty();
    }

    @Test
    @DisplayName("Should reject handshake with 401 when ticket is invalid or already consumed")
    void testInvalidTicketRejection() {
        when(request.getServletRequest()).thenReturn(servletRequest);
        when(servletRequest.getParameter("ticket")).thenReturn("ws_tkt_invalid");
        when(ticketBrokerService.consumeWorkerTicket("ws_tkt_invalid")).thenReturn(Optional.empty());

        Map<String, Object> attributes = new HashMap<>();
        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        assertThat(attributes).isEmpty();
    }

    @Test
    @DisplayName("Should accept handshake, burn ticket, and attach WorkerTicket attribute")
    void testValidTicketHandshakeSuccess() {
        when(request.getServletRequest()).thenReturn(servletRequest);
        when(servletRequest.getParameter("ticket")).thenReturn("ws_tkt_valid");

        TicketBrokerService.WorkerTicket ticketPayload = new TicketBrokerService.WorkerTicket(
            "ws_tkt_valid",
            "worker-001",
            8,
            16000000000L,
            50000000L,
            java.time.Instant.now()
        );
        when(ticketBrokerService.consumeWorkerTicket("ws_tkt_valid")).thenReturn(Optional.of(ticketPayload));

        Map<String, Object> attributes = new HashMap<>();
        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isTrue();
        assertThat(attributes).containsEntry(LctpHandshakeInterceptor.WORKER_TICKET_ATTR, ticketPayload);
    }
}
