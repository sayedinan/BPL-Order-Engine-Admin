package com.BPL_Order_Engine_Admin.manager.engine.dto;

import com.BPL_Order_Engine_Admin.manager.engine.LogLine;

import java.time.Instant;

public record LogLineResponse(Instant timestamp, String level, String message) {
    public static LogLineResponse from(LogLine l) {
        return new LogLineResponse(l.timestamp(), l.level(), l.message());
    }
}
