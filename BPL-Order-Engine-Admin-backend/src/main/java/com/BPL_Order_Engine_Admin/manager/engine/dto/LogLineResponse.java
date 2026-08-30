package com.BPL_Order_Engine_Admin.manager.engine.dto;

import java.time.Instant;

/**
 * Single log line in the {@code logs} endpoint response.
 */
public record LogLineResponse(
        Instant timestamp,
        String level,
        String message
) {
}
