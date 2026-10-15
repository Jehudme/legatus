package dev.legatus.server.config;

import dev.legatus.server.web.websocket.lctp.LctpHandshakeInterceptor;
import dev.legatus.server.web.websocket.lctp.LctpWorkerWebSocketHandler;
import dev.legatus.server.web.websocket.lstcp.LstcpHandshakeInterceptor;
import dev.legatus.server.web.websocket.lstcp.LstcpStudioWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * WebSocket configuration registering the LCTP worker transport broker at {@code /ws/worker}
 * and LSTCP Studio stream broker at {@code /ws/studio}.
 * Configures the 32 MB frame buffer ceiling and atomic ticket validation handshake interceptors.
 */
@Configuration
@EnableWebSocket
public class LctpWebSocketConfiguration implements WebSocketConfigurer {

    public static final int BUFFER_CEILING_BYTES = 33_554_432; // 32 MB

    private final LctpWorkerWebSocketHandler workerWebSocketHandler;
    private final LctpHandshakeInterceptor handshakeInterceptor;
    private final LstcpStudioWebSocketHandler studioWebSocketHandler;
    private final LstcpHandshakeInterceptor studioHandshakeInterceptor;

    public LctpWebSocketConfiguration(
        LctpWorkerWebSocketHandler workerWebSocketHandler,
        LctpHandshakeInterceptor handshakeInterceptor,
        LstcpStudioWebSocketHandler studioWebSocketHandler,
        LstcpHandshakeInterceptor studioHandshakeInterceptor
    ) {
        this.workerWebSocketHandler = workerWebSocketHandler;
        this.handshakeInterceptor = handshakeInterceptor;
        this.studioWebSocketHandler = studioWebSocketHandler;
        this.studioHandshakeInterceptor = studioHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(workerWebSocketHandler, "/ws/worker")
            .addInterceptors(handshakeInterceptor)
            .setAllowedOrigins("*");

        registry.addHandler(studioWebSocketHandler, "/ws/studio")
            .addInterceptors(studioHandshakeInterceptor)
            .setAllowedOrigins("*");
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer(jakarta.servlet.ServletContext servletContext) {
        if (servletContext == null || servletContext.getAttribute("jakarta.websocket.server.ServerContainer") == null) {
            return null;
        }
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(BUFFER_CEILING_BYTES);
        container.setMaxBinaryMessageBufferSize(BUFFER_CEILING_BYTES);
        return container;
    }
}
