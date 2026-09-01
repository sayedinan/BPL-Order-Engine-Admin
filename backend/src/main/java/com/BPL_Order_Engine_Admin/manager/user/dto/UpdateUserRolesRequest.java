package com.BPL_Order_Engine_Admin.manager.user.dto;

import com.BPL_Order_Engine_Admin.manager.user.RoleType;

import java.util.List;

/**
 * Update user roles / engine assignments. Both fields are optional;
 * at least one must be present (enforced in the service).
 */
public record UpdateUserRolesRequest(
    RoleType roleType,
    List<String> assignedEngineCodes
) {}
