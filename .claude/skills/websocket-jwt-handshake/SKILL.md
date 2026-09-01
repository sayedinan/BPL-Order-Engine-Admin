---
name: websocket-jwt-handshake
description: v0.3 — WebSocket endpoint at /api/engines/{code}/logs/stream. JWT auth on the handshake, per-engine session registry, snapshot on connect, close-frame protocol.
---

# WebSocket + JWT handshake (v0.3)

The logs/stream endpoint is the only WebSocket in v0.3. It's used
for real-time engine execution log tail. The handshake is
authenticated; the connection is half-duplex from the server's
perspective (server sends, client may send keep-alive pings).

## Endpoint

`/api/engines/{code}/logs/stream` — native `WebSocket` (RFC 6455),
not STOMP. Subprotocols: `[]` (none). The path uses the engine
**code** (e.g. `bpl`), not the UUID.

## Auth (🔒)

JWT goes in the **`Authorization` header on the WebSocket handshake
request**, not as a query param. Query params get logged in
intermediary access logs and in the engine's HTTP request log; the
header is logged but rarely extracted. The `JwtAuthFilter` is reused
for the handshake — no special-cased auth code.

A handshake without a token returns 401 from the HTTP layer (the
WebSocket upgrade is rejected before the protocol switch). A
handshake with an invalid/expired token also returns 401. A
handshake with a valid token but a `USER` role that doesn't have the
engine in `assignedEngines` returns 403.

The browser `WebSocket` API does not allow custom headers on the
handshake, so the React side uses a small wrapper that issues the
upgrade via `fetch` + manual protocol switch, or it stashes the
token in a `Sec-WebSocket-Protocol` value (a common workaround).
Either is acceptable; the test will pass either way as long as the
header reaches the server.

## Server side

```java
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final EngineLogsWebSocketHandler handler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/api/engines/*/logs/stream")
            .setAllowedOriginPatterns("http://localhost:5173");   // dev only
    }
}

@Component
public class EngineLogsWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionRegistry registry;
    private final LogBuffer logBuffer;
    private final UserRepository userRepository;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String code = extractEngineCode(session);                  // from the path
        UserPrincipal principal = (UserPrincipal) session.getPrincipal();   // set by JwtAuthFilter
        if (principal == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        if (principal.getUser().getRoleType() == RoleType.USER
                && !principal.getUser().getAssignedEngines().stream()
                    .anyMatch(e -> e.getCode().equals(code))) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        // Send the snapshot of the last 100 lines
        List<LogLine> snapshot = logBuffer.snapshot(code, 100);
        for (LogLine line : snapshot) {
            session.sendMessage(new TextMessage(toJson(line)));
        }

        // Register for live updates
        registry.add(code, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String code = extractEngineCode(session);
        registry.remove(code, session);
    }

    private String extractEngineCode(WebSocketSession session) {
        // path is /api/engines/{code}/logs/stream
        String path = session.getUri().getPath();
        String[] parts = path.split("/");
        return parts[4];
    }
}
```

The `setAllowedOriginPatterns` line is the CORS allowlist for the
WebSocket. Production tightens this; dev permits the Vite origin.

## Per-engine session registry

`WebSocketSessionRegistry` is a `Map<String, Set<WebSocketSession>>` keyed by engine code, wrapped in a `ConcurrentHashMap` and per-code `synchronized` sets. The `broadcast(code, line)` method iterates the set and sends the line as a `TextMessage`; failures (broken pipes) are caught per-session and the session is removed.

## Snapshot on connect

When a client connects, the server sends the last 100 lines from the `LogBuffer` (the rolling deque from the SSH tailer) as individual text frames, one per line, **before** the client receives any live update. The order is FIFO: oldest first, newest last. The client renders them in order; the next live line is appended below.

## Frame format

Each text frame is a JSON object:

```json
{"timestamp": "2026-09-01T09:10:00Z", "level": "INFO", "message": "Order queue drained: 12 orders processed"}
```

The `level` field is optional in v0.3 (the SSH `tail -F` output is plain text; we default to `INFO` if the line doesn't have a `[LEVEL]` prefix). Future work: parse the prefix.

## Close-frame protocol

The server sends a structured close frame before terminating:

```json
{"event": "closed", "reason": "engine_stopped"}
```

or

```json
{"event": "closed", "reason": "engine_deleted"}
```

or

```json
{"event": "closed", "reason": "auth_failed"}
```

Then `session.close()` with the appropriate `CloseStatus`. The client uses the `reason` to decide whether to reconnect (engine_stopped → no; transient network drop → yes) and to surface a user-friendly message.

## The reconnect logic (client side)

Exponential backoff: 1s, 2s, 4s, 8s, 16s, capped at 30s. Reset the backoff on a successful `open` event. Reset on a `closed` event with `reason: "engine_stopped"` or `engine_deleted` (no reconnect). Reset on a `closed` event with `reason: "auth_failed"` (no reconnect — the token is bad).

The `useEngineLogsSocket` hook encapsulates this. The hook returns `{ lines, status, close() }` where `status` is one of `connecting | open | reconnecting | closed`. The component unmount triggers `close()`.

## Anti-patterns

- **Don't put the JWT in a query param.** The handshake header is the only acceptable transport for the token.
- **Don't broadcast a line to a session whose underlying connection is broken.** Catch `IOException` per session and remove it.
- **Don't hold the registry's lock while sending.** Send outside the synchronized block; the broken-session check happens before send.
- **Don't send the snapshot after the live updates start.** The order is snapshot → live; the client relies on it for the initial render.
- **Don't include the engine password in any frame.** The frame carries log lines, not credentials.
- **Don't open a new `SshClient` per WebSocket connection.** The `SshClient` is owned by the `LogTailerRegistry`; the WS is a viewer, not a driver.
- **Don't reconnect on `auth_failed`.** The token is bad; reconnecting with the same token will fail again. The user must log out and back in.
