package com.BPL_Order_Engine_Admin.manager.engine.dto;

import com.BPL_Order_Engine_Admin.manager.engine.EngineEntity;
import com.BPL_Order_Engine_Admin.manager.engine.EngineMode;
import com.BPL_Order_Engine_Admin.manager.engine.EngineStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Public-facing engine response. Used in {@code GET /api/engines},
 * {@code POST /api/engines}, {@code PATCH /api/engines/{code}/ssh}.
 *
 * <p>The {@code serverPassword} field is intentionally absent.
 * Plaintext never crosses this boundary.
 */
public record EngineResponse(
    UUID id,
    String code,
    String name,
    EngineMode mode,
    String serverIp,
    String serverUsername,
    String startScript,
    String stopScript,
    String logScript,
    EngineStatus status,
    Instant lastTransitionAt,
    Instant createdAt,
    Instant updatedAt
) {
    public static EngineResponse from(EngineEntity e) {
        return new EngineResponse(
            e.getId(),
            e.getCode(),
            e.getName(),
            e.getMode(),
            e.getServerIp(),
            e.getServerUsername(),
            e.getStartScript(),
            e.getStopScript(),
            e.getLogScript(),
            e.getStatus(),
            e.getLastTransitionAt(),
            e.getCreatedAt(),
            e.getUpdatedAt()
        );
    }
}
