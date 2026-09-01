package com.BPL_Order_Engine_Admin.manager.engine.ws;

import com.BPL_Order_Engine_Admin.manager.auth.JwtService;
import com.BPL_Order_Engine_Admin.manager.auth.UserPrincipal;
import com.BPL_Order_Engine_Admin.manager.engine.EngineEntity;
import com.BPL_Order_Engine_Admin.manager.engine.EngineRepository;
import com.BPL_Order_Engine_Admin.manager.engine.EngineStatus;
import com.BPL_Order_Engine_Admin.manager.engine.LogBuffer;
import com.BPL_Order_Engine_Admin.manager.engine.LogLine;
import com.BPL_Order_Engine_Admin.manager.user.RoleType;
import com.BPL_Order_Engine_Admin.manager.user.User;
import com.BPL_Order_Engine_Admin.manager.user.UserRepository;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * v0.3 WebSocket handler for {@code /api/engines/{code}/logs/stream}
 * (SPEC §2.9 / API.md §2.9 / websocket-jwt-handshake skill).
 *
 * <p>Flow:
 * <ol>
 *   <li>JWT extracted from {@code Sec-WebSocket-Protocol: bearer.<token>}.
 *       No token → close with POLICY_VIOLATION.</li>
 *   <li>Engine code extracted from the URI ({@code /api/engines/{code}/logs/stream}).</li>
 *   <li>User loaded; USER without the engine in {@code assignedEngines} → close 1008.</li>
 *   <li>Engine not found / soft-deleted → close 1008.</li>
 *   <li>Engine STOPPED → send snapshot then close with
 *       {@code {"event":"closed","reason":"engine_stopped"}}.</li>
 *   <li>Engine RUNNING → send the snapshot of the last 100 lines from
 *       {@link LogBuffer}, then register the session in the
 *       {@link WebSocketSessionRegistry}. New lines (from the tailer)
 *       are pushed as JSON text frames.</li>
 * </ol>
 */
@Component
public class EngineLogsWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(EngineLogsWebSocketHandler.class);
    private static final String BEARER_PREFIX = "bearer.";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final EngineRepository engineRepository;
    private final LogBuffer logBuffer;
    private final WebSocketSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    public EngineLogsWebSocketHandler(
            JwtService jwtService,
            UserRepository userRepository,
            EngineRepository engineRepository,
            LogBuffer logBuffer,
            WebSocketSessionRegistry sessionRegistry,
            ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.engineRepository = engineRepository;
        this.logBuffer = logBuffer;
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 1. Auth from the subprotocol header.
        String subprotocol = extractBearerToken(session);
        if (subprotocol == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        Claims claims;
        try {
            claims = jwtService.parse(subprotocol);
        } catch (Exception e) {
            log.debug("Rejected WS token: {}", e.getMessage());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        String username = claims.getSubject();
        if (username == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        Optional<User> userOpt = userRepository.findByUsernameIgnoreCase(username);
        if (userOpt.isEmpty()) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        User user = userOpt.get();

        // 2. Engine code from the URI.
        String code = extractEngineCode(session);
        if (code == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        Optional<EngineEntity> engineOpt = engineRepository.findByCodeAndDeletedAtIsNull(code);
        if (engineOpt.isEmpty()) {
            closeWithReason(session, "engine_deleted");
            return;
        }
        EngineEntity engine = engineOpt.get();

        // 3. USER must have the engine in assignedEngines.
        if (user.getRoleType() == RoleType.USER) {
            Set<String> codes = new HashSet<>();
            for (EngineEntity ae : user.getAssignedEngines()) codes.add(ae.getCode());
            if (!codes.contains(code)) {
                session.close(new CloseStatus(1008, "forbidden"));
                return;
            }
        }

        // 4. Stopped engine: send snapshot then close.
        if (engine.getStatus() == EngineStatus.STOPPED) {
            for (LogLine line : logBuffer.snapshot(code, 100)) {
                sendLine(session, line);
            }
            closeWithReason(session, "engine_stopped");
            return;
        }

        // 5. Send snapshot then register for live updates.
        for (LogLine line : logBuffer.snapshot(code, 100)) {
            sendLine(session, line);
        }
        sessionRegistry.add(code, session);
        log.info("WS connected for engine {} (user {})", code, username);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String code = extractEngineCode(session);
        if (code != null) {
            sessionRegistry.remove(code, session);
        }
    }

    private String extractBearerToken(WebSocketSession session) {
        // The browser's WebSocket API doesn't allow custom headers.
        // We accept the JWT in Sec-WebSocket-Protocol as `bearer.<token>`.
        if (session.getAcceptedProtocol() != null
                && session.getAcceptedProtocol().startsWith(BEARER_PREFIX)) {
            return session.getAcceptedProtocol().substring(BEARER_PREFIX.length());
        }
        // Also check the offered protocols in case the handshake
        // accepted one but the accepted-protocol header parsing missed.
        var offered = session.getHandshakeHeaders().get("Sec-WebSocket-Protocol");
        if (offered != null && !offered.isEmpty()) {
            for (String p : offered) {
                if (p.startsWith(BEARER_PREFIX)) return p.substring(BEARER_PREFIX.length());
            }
        }
        return null;
    }

    private String extractEngineCode(WebSocketSession session) {
        String path = session.getUri() == null ? null : session.getUri().getPath();
        if (path == null) return null;
        String[] parts = path.split("/");
        // /api/engines/{code}/logs/stream
        if (parts.length >= 6 && "api".equals(parts[1]) && "engines".equals(parts[2])) {
            return parts[3];
        }
        return null;
    }

    private void sendLine(WebSocketSession session, LogLine line) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(line)));
        } catch (Exception e) {
            // Broken pipe; the close path will unregister.
            log.debug("WS send failed for {}", session.getId(), e);
        }
    }

    private void closeWithReason(WebSocketSession session, String reason) {
        try {
            session.sendMessage(new TextMessage(
                "{\"event\":\"closed\",\"reason\":\"" + reason + "\"}"));
        } catch (Exception ignored) { /* closing anyway */ }
        try {
            session.close(CloseStatus.NORMAL);
        } catch (Exception ignored) { /* noop */ }
    }
}
