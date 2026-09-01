package com.BPL_Order_Engine_Admin.manager.user.dto;

import com.BPL_Order_Engine_Admin.manager.user.RoleType;
import com.BPL_Order_Engine_Admin.manager.user.User;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public-facing user response. Used in the bodies of
 * {@code GET /api/users}, {@code POST /api/users}, and
 * {@code PATCH /api/users/{id}/roles}.
 *
 * <p>Never includes {@code passwordHash}.
 */
public record UserResponse(
    UUID id,
    String username,
    RoleType role,
    List<String> assignedEngineCodes,
    Instant createdAt,
    Instant updatedAt
) {
    public static UserResponse from(User user) {
        List<String> codes = user.getAssignedEngines().stream()
            .map(com.BPL_Order_Engine_Admin.manager.engine.EngineEntity::getCode)
            .sorted()
            .toList();
        return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getRoleType(),
            codes,
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}
