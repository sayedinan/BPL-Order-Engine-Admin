package com.BPL_Order_Engine_Admin.manager.engine;

import java.time.Instant;
import java.util.List;

/**
 * v0.3 engine operations contract (SPEC §3.6, preserved from v0.2).
 *
 * <p>Two implementations:
 * <ul>
 *   <li>{@code MockEngineOperations} (in {@code impl/}) — in-memory
 *       state machine, no network.</li>
 *   <li>{@code SshBackedEngine} (in {@code impl/}) — Apache MINA SSHD
 *       to the engine's {@code serverIp}, in #19.</li>
 * </ul>
 *
 * <p>The {@code OrderEngineFactory} (in this package) chooses
 * between the two based on {@code Engine.mode}.
 */
public interface OrderEngineOperations {

    /** Engine code (e.g. "BPL"). Matches {@code Engine.code}. */
    String engineId();

    /** Display name (e.g. "BPL Order Engine"). Matches {@code Engine.name}. */
    String displayName();

    /** Current status. */
    EngineStatus status();

    /** Last status transition timestamp; {@code null} before the first transition. */
    Instant lastTransitionAt();

    /**
     * STOPPED → RUNNING. Throws {@link EngineScriptException} on
     * non-zero exit, {@link EngineUnreachableException} on
     * connect fail, {@link EngineAuthException} on auth fail.
     */
    EngineActionResult start();

    /**
     * RUNNING → STOPPED. Same exceptions as {@link #start()}.
     */
    EngineActionResult stop();

    /**
     * Last {@code limit} log lines (newest last). Used by
     * {@code GET /api/engines/{code}/logs?limit=N} and the WebSocket
     * snapshot.
     */
    List<LogLine> getLogs(int limit);

    /** MOCK or REAL. */
    EngineMode currentMode();
}
