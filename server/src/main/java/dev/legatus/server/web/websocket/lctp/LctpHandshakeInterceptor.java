package dev.legatus.server.web.websocket.lctp;

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
 * WebSocket handshake interceptor enforcing atomic burn-on-read of single-use 30s worker upgrade tickets.
 * <p>
 * Complies with {@code server-spec.md} Section 4.2 &amp; {@code worker-wire.md} Section 1:
 * Replay attempts or expired tickets are rejected with HTTP 401 Unauthorized before WebSocket upgrade.
 */
@Component
public class LctpHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(LctpHandshakeInterceptor.class);

    public static final String WORKER_TICKET_ATTR = "WORKER_TICKET";

    private final TicketBrokerService ticketBrokerService;

    public LctpHandshakeInterceptor(TicketBrokerService ticketBrokerService) {
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
                LOG.warn("Rejected LCTP upgrade request without ticket parameter from {}", request.getRemoteAddress());
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            Optional<TicketBrokerService.WorkerTicket> ticketOpt = ticketBrokerService.consumeWorkerTicket(ticket);
            if (ticketOpt.isEmpty()) {
                LOG.warn("Rejected LCTP upgrade attempt with expired/burned/invalid ticket {} from {}", ticket, request.getRemoteAddress());
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            attributes.put(WORKER_TICKET_ATTR, ticketOpt.get());
            LOG.debug("Atomically consumed worker ticket {} during handshake for node {}", ticket, ticketOpt.get().workerId());
            return true;
        }

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
        // No-op after successful upgrade
    }
}
