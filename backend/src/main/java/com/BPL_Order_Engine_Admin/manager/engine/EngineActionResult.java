package com.BPL_Order_Engine_Admin.manager.engine;

import java.time.Instant;

/**
 * Result of an engine action (start/stop). Returned to the controller
 * which converts it to {@code EngineActionResponse}.
 *
 * <p>{@code exitCode} is the script's exit status for REAL-mode
 * engines (0 on success, non-zero on failure — but failures throw
 * {@link EngineScriptException} before reaching here, so the value
 * is always 0 for the success path). For MOCK engines it's always 0.
 * The field is included so audit rows can record
 * {@code details: { engineCode, exitCode }} per SPEC §4.3 / API.md §2.6.
 */
public record EngineActionResult(
    String engineId,
    String displayName,
    EngineStatus status,
    String message,
    Instant transitionedAt,
    int exitCode
) {
    /** Convenience factory for the success path (exitCode = 0). */
    public static EngineActionResult success(
        String engineId, String displayName, EngineStatus status,
        String message, Instant transitionedAt
    ) {
        return new EngineActionResult(engineId, displayName, status, message, transitionedAt, 0);
    }
}
