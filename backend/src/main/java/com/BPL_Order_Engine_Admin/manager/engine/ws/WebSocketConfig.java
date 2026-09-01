package com.BPL_Order_Engine_Admin.manager.engine.ws;

import org.springframework.beans.factory.annotation.Value;
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
 *
 * <p>Allowed origin patterns are driven by the same
 * {@code app.cors.allowed-origins} property as {@code CorsConfig}.
 * Spring's CORS preflight does not run for WebSocket handshakes
 * (it's an HTTP Upgrade), so the CORS filter and the WebSocket
 * origin check would otherwise be out of sync; reading from the
 * same property keeps them aligned.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final EngineLogsWebSocketHandler handler;

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    public WebSocketConfig(EngineLogsWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Comma-separated list, same parsing as CorsConfig. An empty
        // or unset value leaves Spring's default behavior in place
        // (SameOrigin only), which is the safe production default.
        String[] patterns = allowedOrigins.split(",");
        registry.addHandler(handler, "/api/engines/*/logs/stream")
            .setAllowedOriginPatterns(patterns);
    }
}
