package com.BPL_Order_Engine_Admin.manager.auth.dto;

import com.BPL_Order_Engine_Admin.manager.user.RoleType;
import com.BPL_Order_Engine_Admin.manager.user.User;

import java.util.List;
import java.util.UUID;

/**
 * Public-facing user representation. Used in the {@code user} field
 * of LoginResponse and the body of GET /api/auth/me.
 *
 * <p>Never includes {@code passwordHash}.
 */
public record UserSummary(
    UUID id,
    String username,
    RoleType role,
    List<String> assignedEngineCodes,
    boolean mustChangePassword
) {
    public static UserSummary from(User user) {
        List<String> codes = user.getAssignedEngines().stream()
            .map(com.BPL_Order_Engine_Admin.manager.engine.EngineEntity::getCode)
            .sorted()
            .toList();
        return new UserSummary(
            user.getId(),
            user.getUsername(),
            user.getRoleType(),
            codes,
            user.isMustChangePassword()
        );
    }
}
