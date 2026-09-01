package com.BPL_Order_Engine_Admin.manager.engine;

import java.time.Instant;

/**
 * One log line emitted by an engine (SPEC §3.6).
 */
public record LogLine(Instant timestamp, String level, String message) {}
