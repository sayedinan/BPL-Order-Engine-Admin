package com.BPL_Order_Engine_Admin.manager.engine;

import java.time.Instant;

/**
 * A single log line emitted by an engine. Internal representation; the
 * wire DTO is {@code LogLineResponse}.
 */
public record LogLine(Instant timestamp, String level, String message) {
}
