package com.BPL_Order_Engine_Admin.manager.engine.dto;

import com.BPL_Order_Engine_Admin.manager.engine.EngineStatus;

import java.time.Instant;

/**
 * Response body for the start/stop endpoints.
 */
public record EngineActionResponse(
        String engineId,
        String displayName,
        EngineStatus status,
        String message,
        Instant transitionedAt
) {
}
