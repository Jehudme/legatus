package dev.legatus.server.config;

import dev.legatus.server.web.websocket.lctp.LctpHandshakeInterceptor;
import dev.legatus.server.web.websocket.lctp.LctpWorkerWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * WebSocket configuration registering the LCTP worker transport broker at {@code /ws/worker}.
 * Configures the 32 MB frame buffer ceiling and atomic ticket validation handshake interceptor.
 */
@Configuration
@EnableWebSocket
public class LctpWebSocketConfiguration implements WebSocketConfigurer {

    public static final int BUFFER_CEILING_BYTES = 33_554_432; // 32 MB

    private final LctpWorkerWebSocketHandler workerWebSocketHandler;
    private final LctpHandshakeInterceptor handshakeInterceptor;

    public LctpWebSocketConfiguration(
        LctpWorkerWebSocketHandler workerWebSocketHandler,
        LctpHandshakeInterceptor handshakeInterceptor
    ) {
        this.workerWebSocketHandler = workerWebSocketHandler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(workerWebSocketHandler, "/ws/worker")
            .addInterceptors(handshakeInterceptor)
            .setAllowedOrigins("*");
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(BUFFER_CEILING_BYTES);
        container.setMaxBinaryMessageBufferSize(BUFFER_CEILING_BYTES);
        return container;
    }
}
