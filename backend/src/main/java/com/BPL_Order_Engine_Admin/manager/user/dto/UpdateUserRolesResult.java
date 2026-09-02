package com.BPL_Order_Engine_Admin.manager.user.dto;

import com.BPL_Order_Engine_Admin.manager.user.RoleType;

import java.util.List;
import java.util.UUID;

/**
 * Internal result wrapper for {@code PATCH /api/users/{id}/roles}.
 *
 * <p>The HTTP response is the updated {@link UserResponse}, but the
 * audit {@code @Audited} annotation needs {@code details.oldRoles}
 * and {@code details.newRoles} (SPEC §4.4, API.md §3.4). Each entry
 * is {@code { roleType, assignedEngineCodes }} — the bundle
 * recorded before and after the mutation.
 */
public record UpdateUserRolesResult(
    UserResponse user,
    List<RoleAssignment> oldRoles,
    List<RoleAssignment> newRoles
) {
    public record RoleAssignment(RoleType roleType, List<String> assignedEngineCodes) {}
}
