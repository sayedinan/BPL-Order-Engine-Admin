package com.BPL_Order_Engine_Admin.manager.engine.dto;

import com.BPL_Order_Engine_Admin.manager.engine.EngineActionResult;
import com.BPL_Order_Engine_Admin.manager.engine.EngineStatus;

import java.time.Instant;

/**
 * {@code POST /api/engines/{code}/start|stop} response (SPEC §4.5).
 */
public record EngineActionResponse(
    String engineCode,
    String displayName,
    EngineStatus status,
    String message,
    Instant transitionedAt
) {
    public static EngineActionResponse from(EngineActionResult r) {
        return new EngineActionResponse(
            r.engineId(),
            r.displayName(),
            r.status(),
            r.message(),
            r.transitionedAt()
        );
    }
}
