package com.BPL_Order_Engine_Admin.manager.engine.dto;

import com.BPL_Order_Engine_Admin.manager.engine.EngineStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Response body for {@code GET /api/engines/{engineId}/status}.
 *
 * <p>{@code lastTransitionAt} is omitted from the JSON when {@code null}
 * (i.e. before the first transition) &mdash; see SPEC.md &sect;3.3.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EngineStatusResponse(
        String engineId,
        String displayName,
        EngineStatus status,
        Instant lastTransitionAt,
        Instant checkedAt
) {
}
