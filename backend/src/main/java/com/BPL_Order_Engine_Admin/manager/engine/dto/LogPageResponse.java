package com.BPL_Order_Engine_Admin.manager.engine.dto;

import java.util.List;

/**
 * Response body for {@code GET /api/engines/{engineId}/logs}.
 */
public record LogPageResponse(
        String engineId,
        int limit,
        int count,
        List<LogLineResponse> lines
) {
}
