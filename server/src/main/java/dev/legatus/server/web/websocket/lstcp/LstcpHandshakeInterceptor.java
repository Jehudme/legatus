package dev.legatus.server.web.websocket.lstcp;

import dev.legatus.server.security.TicketBrokerService;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * WebSocket handshake interceptor enforcing atomic burn-on-read of single-use 30s Studio upgrade tickets.
 * <p>
 * Complies with {@code studio-wire.md} Section 1 &amp; Section 6:
 * Replay attempts or expired tickets are rejected with HTTP 401 Unauthorized before WebSocket upgrade.
 */
@Component
public class LstcpHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(LstcpHandshakeInterceptor.class);

    public static final String STUDIO_TICKET_ATTR = "STUDIO_TICKET";

    private final TicketBrokerService ticketBrokerService;

    public LstcpHandshakeInterceptor(TicketBrokerService ticketBrokerService) {
        this.ticketBrokerService = ticketBrokerService;
    }

    @Override
    public boolean beforeHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Map<String, Object> attributes
    ) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String ticket = servletRequest.getServletRequest().getParameter("ticket");
            if (ticket == null || ticket.isBlank()) {
                LOG.warn("Rejected LSTCP upgrade request without ticket parameter from {}", request.getRemoteAddress());
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            Optional<TicketBrokerService.StudioTicket> ticketOpt = ticketBrokerService.consumeStudioTicket(ticket);
            if (ticketOpt.isEmpty()) {
                LOG.warn("Rejected LSTCP upgrade attempt with expired/burned/invalid ticket {} from {}",
                    ticket, request.getRemoteAddress());
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            attributes.put(STUDIO_TICKET_ATTR, ticketOpt.get());
            LOG.debug("Atomically consumed Studio ticket {} during handshake for operator {}",
                ticket, ticketOpt.get().operatorUsername());
            return true;
        }

        LOG.warn("Rejected non-servlet LSTCP upgrade request from {}", request.getRemoteAddress());
        response.setStatusCode(HttpStatus.BAD_REQUEST);
        return false;
    }

    @Override
    public void afterHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Exception exception
    ) {
        // No-op after handshake
    }
}
