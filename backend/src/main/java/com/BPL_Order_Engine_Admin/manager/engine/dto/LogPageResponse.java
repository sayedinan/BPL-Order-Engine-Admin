package com.BPL_Order_Engine_Admin.manager.engine.dto;

import java.util.List;

public record LogPageResponse(
    String engineCode,
    int limit,
    int count,
    List<LogLineResponse> lines
) {}
