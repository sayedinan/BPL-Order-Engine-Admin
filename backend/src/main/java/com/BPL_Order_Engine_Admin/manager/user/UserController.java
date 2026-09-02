package com.BPL_Order_Engine_Admin.manager.user;

import com.BPL_Order_Engine_Admin.manager.audit.AuditAction;
import com.BPL_Order_Engine_Admin.manager.audit.Audited;
import com.BPL_Order_Engine_Admin.manager.auth.UserPrincipal;
import com.BPL_Order_Engine_Admin.manager.user.dto.CreateUserRequest;
import com.BPL_Order_Engine_Admin.manager.user.dto.UpdateUserRolesRequest;
import com.BPL_Order_Engine_Admin.manager.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * v0.3 user management controller (SPEC §4.4 / API.md §3).
 *
 * <p>Role gates are enforced at the method level via
 * {@code @PreAuthorize}. The {@code @Audited} annotations drive the
 * {@code AuditAspect}.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SYS_ADMIN', 'ADMIN')")
    public List<UserResponse> list() {
        return userService.listAll();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SYS_ADMIN', 'ADMIN')")
    @Audited(
        action = AuditAction.CREATE_USER,
        details = "{ newUserId: #result.id(), newUsername: #result.username(), "
                + "newRole: #result.role(), assignedEngines: #result.assignedEngineCodes() }"
    )
    public ResponseEntity<UserResponse> create(
            @Valid @RequestBody CreateUserRequest req,
            @AuthenticationPrincipal UserPrincipal caller) {
        UserResponse created = userService.create(req, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SYS_ADMIN', 'ADMIN')")
    @Audited(
        action = AuditAction.DELETE_USER,
        details = "{ targetUserId: #result.targetUserId(), targetUsername: #result.targetUsername() }"
    )
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal caller) {
        userService.delete(id, caller);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/roles")
    @PreAuthorize("hasAnyRole('SYS_ADMIN', 'ADMIN')")
    @Audited(
        action = AuditAction.UPDATE_USER_ROLES,
        details = "{ targetUserId: #id, oldRoles: #result.oldRoles(), newRoles: #result.newRoles() }"
    )
    public UserResponse updateRoles(
            @PathVariable UUID id,
            @RequestBody UpdateUserRolesRequest req,
            @AuthenticationPrincipal UserPrincipal caller) {
        return userService.updateRoles(id, req, caller).user();
    }
}
