package com.BPL_Order_Engine_Admin.manager.engine.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v0.3 per-engine WebSocket session registry (SPEC §2.9 / §6.2).
 *
 * <p>Tracks the set of connected sessions per engine code. The
 * {@link EngineLogsWebSocketHandler} registers on connect and
 * unregisters on close; the {@code LogTailerRegistry} (or any other
 * broadcaster) can push a line to every session for an engine in
 * O(1) per engine.
 *
 * <p>The send path is per-session to keep the synchronized block
 * small and to catch per-session {@code IOException} (the WS
 * connection is broken) without affecting other sessions.
 */
@Component
public class WebSocketSessionRegistry {

    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void add(String engineCode, WebSocketSession session) {
        sessions.computeIfAbsent(engineCode, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void remove(String engineCode, WebSocketSession session) {
        Set<WebSocketSession> set = sessions.get(engineCode);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) sessions.remove(engineCode);
        }
    }

    public Set<WebSocketSession> sessionsFor(String engineCode) {
        return sessions.getOrDefault(engineCode, Set.of());
    }

    public int totalSessions() {
        int total = 0;
        for (var s : sessions.values()) total += s.size();
        return total;
    }
}
