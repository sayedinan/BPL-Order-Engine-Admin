package com.BPL_Order_Engine_Admin.manager.engine.dto;

import com.BPL_Order_Engine_Admin.manager.engine.EngineMode;

/**
 * v0.3 update-engine-ssh request (SPEC §4.5). All fields optional;
 * only present fields are updated. The new password is never
 * echoed back; the audit row's {@code details.fieldsChanged} lists
 * the names of the fields that were updated.
 */
public record UpdateEngineSshRequest(
    String name,
    EngineMode mode,
    String serverIp,
    String serverUsername,
    String serverPassword,
    String startScript,
    String stopScript,
    String logScript
) {}
