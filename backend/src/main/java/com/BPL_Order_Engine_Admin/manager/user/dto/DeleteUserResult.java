package com.BPL_Order_Engine_Admin.manager.user.dto;

import java.util.UUID;

/**
 * Internal result wrapper for {@code DELETE /api/users/{id}}.
 *
 * <p>The HTTP response is 204 No Content (no body), but the audit
 * {@code @Audited} annotation on the controller method needs to
 * record {@code details.targetUsername} (SPEC §4.4, API.md §3.3).
 * The controller returns this wrapper to the aspect via the
 * return-value SpEL, then discards the response by returning 204.
 *
 * <p>Field set: {@code targetUserId} (from the URL path) and
 * {@code targetUsername} (looked up before deletion).
 */
public record DeleteUserResult(
    UUID targetUserId,
    String targetUsername
) {}
