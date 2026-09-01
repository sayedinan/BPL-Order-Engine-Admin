package com.BPL_Order_Engine_Admin.manager.engine.dto;

import com.BPL_Order_Engine_Admin.manager.engine.EngineMode;
import com.BPL_Order_Engine_Admin.manager.engine.EngineStatus;
import com.BPL_Order_Engine_Admin.manager.engine.OrderEngineOperations;

import java.time.Instant;

/**
 * {@code GET /api/engines/{code}/status} response (SPEC §4.5).
 */
public record EngineStatusResponse(
    String engineCode,
    String displayName,
    EngineStatus status,
    EngineMode mode,
    Instant lastTransitionAt,
    Instant checkedAt
) {
    public static EngineStatusResponse from(OrderEngineOperations op) {
        return new EngineStatusResponse(
            op.engineId(),
            op.displayName(),
            op.status(),
            op.currentMode(),
            op.lastTransitionAt(),
            Instant.now()
        );
    }
}
