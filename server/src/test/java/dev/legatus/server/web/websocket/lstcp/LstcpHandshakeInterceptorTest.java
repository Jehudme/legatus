package dev.legatus.server.web.websocket.lstcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.legatus.server.security.TicketBrokerService;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;

class LstcpHandshakeInterceptorTest {

    private TicketBrokerService ticketBrokerService;
    private LstcpHandshakeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        ticketBrokerService = mock(TicketBrokerService.class);
        interceptor = new LstcpHandshakeInterceptor(ticketBrokerService);
    }

    @Test
    @DisplayName("Should reject handshake when ticket query param is missing (401)")
    void testMissingTicket() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ServletServerHttpRequest request = new ServletServerHttpRequest(servletRequest);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(request, response, mock(WebSocketHandler.class), attributes);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should reject handshake when ticket is expired, burned, or invalid (401)")
    void testInvalidTicket() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setParameter("ticket", "st_tkt_invalid_or_burned");
        ServletServerHttpRequest request = new ServletServerHttpRequest(servletRequest);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        Map<String, Object> attributes = new HashMap<>();

        when(ticketBrokerService.consumeStudioTicket("st_tkt_invalid_or_burned")).thenReturn(Optional.empty());

        boolean result = interceptor.beforeHandshake(request, response, mock(WebSocketHandler.class), attributes);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should accept handshake when ticket is valid, burn ticket atomically, and attach to attributes")
    void testValidTicket() {
        String ticketStr = "st_tkt_valid_12345";
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setParameter("ticket", ticketStr);
        ServletServerHttpRequest request = new ServletServerHttpRequest(servletRequest);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        Map<String, Object> attributes = new HashMap<>();

        TicketBrokerService.StudioTicket ticketPayload = new TicketBrokerService.StudioTicket(
            ticketStr,
            "operator@legatus.internal",
            "ROLE_OPERATOR",
            Instant.now()
        );

        when(ticketBrokerService.consumeStudioTicket(ticketStr)).thenReturn(Optional.of(ticketPayload));

        boolean result = interceptor.beforeHandshake(request, response, mock(WebSocketHandler.class), attributes);

        assertThat(result).isTrue();
        assertThat(attributes).containsKey(LstcpHandshakeInterceptor.STUDIO_TICKET_ATTR);
        assertThat(attributes.get(LstcpHandshakeInterceptor.STUDIO_TICKET_ATTR)).isEqualTo(ticketPayload);
    }
}
