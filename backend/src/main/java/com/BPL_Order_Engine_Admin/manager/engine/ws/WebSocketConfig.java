package com.BPL_Order_Engine_Admin.manager.engine.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * v0.3 WebSocket config (SPEC §2.9 / API.md §2.9).
 *
 * <p>Maps the engine-logs-stream path to the engine logs handler.
 * The handler enforces JWT + role+assignment auth; the
 * SecurityConfig path matchers take care of the upstream filter
 * chain ordering.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final EngineLogsWebSocketHandler handler;

    public WebSocketConfig(EngineLogsWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/api/engines/*/logs/stream")
            .setAllowedOriginPatterns("http://localhost:5173"); // dev
    }
}
