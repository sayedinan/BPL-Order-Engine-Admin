package com.BPL_Order_Engine_Admin.manager.engine;

import java.time.Instant;

/**
 * Result of an engine action (start/stop). Returned to the controller
 * which converts it to {@code EngineActionResponse}.
 */
public record EngineActionResult(
    String engineId,
    String displayName,
    EngineStatus status,
    String message,
    Instant transitionedAt
) {}
