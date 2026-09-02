package com.BPL_Order_Engine_Admin.manager.engine.dto;

import java.util.List;

/**
 * Internal result wrapper for {@code PATCH /api/engines/{code}/ssh}.
 *
 * <p>The controller returns the {@link EngineResponse} to the
 * client, but the audit {@code @Audited} SpEL expression on the
 * controller method references this wrapper so the audit row
 * includes {@code details.fieldsChanged} (SPEC §4.3, API.md §2.4
 * — the list of mutated field names; the new password value is
 * never recorded).
 */
public record UpdateEngineSshResult(
    EngineResponse engine,
    List<String> fieldsChanged
) {}
