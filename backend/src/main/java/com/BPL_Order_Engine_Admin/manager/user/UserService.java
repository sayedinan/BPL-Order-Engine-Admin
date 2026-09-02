package com.BPL_Order_Engine_Admin.manager.user;

import com.BPL_Order_Engine_Admin.manager.auth.UserPrincipal;
import com.BPL_Order_Engine_Admin.manager.engine.EngineEntity;
import com.BPL_Order_Engine_Admin.manager.engine.EngineRepository;
import com.BPL_Order_Engine_Admin.manager.user.dto.CreateUserRequest;
import com.BPL_Order_Engine_Admin.manager.user.dto.DeleteUserResult;
import com.BPL_Order_Engine_Admin.manager.user.dto.UpdateUserRolesRequest;
import com.BPL_Order_Engine_Admin.manager.user.dto.UpdateUserRolesResult;
import com.BPL_Order_Engine_Admin.manager.user.dto.UserResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * v0.3 user CRUD service (SPEC §4.4 / API.md §3).
 *
 * <p>Role rules:
 * <ul>
 *   <li>SYS_ADMIN: can do anything (create any role, change any role,
 *       delete anyone except themselves; cannot delete the last
 *       SYS_ADMIN).</li>
 *   <li>ADMIN: can create only USER-role users; can update only
 *       USER-role users' assignments (NOT their role — an ADMIN
 *       cannot promote a USER to ADMIN).</li>
 *   <li>USER: 403.</li>
 * </ul>
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final EngineRepository engineRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            EngineRepository engineRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.engineRepository = engineRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listAll() {
        return userRepository.findAll().stream()
            .map(UserResponse::from)
            .toList();
    }

    @Transactional
    public UserResponse create(CreateUserRequest req, UserPrincipal caller) {
        if (caller.getUser().getRoleType() == RoleType.ADMIN && req.role() != RoleType.USER) {
            throw new AccessDeniedException("Access denied");
        }
        if (userRepository.findByUsernameIgnoreCase(req.username()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username '" + req.username() + "' is taken");
        }
        User u = new User();
        u.setUsername(req.username());
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setRoleType(req.role());
        u.setMustChangePassword(true);
        u.setAssignedEngines(resolveEngines(req.assignedEngineCodes()));
        userRepository.save(u);
        return UserResponse.from(u);
    }

    @Transactional
    public DeleteUserResult delete(UUID id, UserPrincipal caller) {
        if (caller.getUser().getId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot delete yourself");
        }
        User target = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        // Role check: ADMIN can only delete USER; SYS_ADMIN can delete anyone.
        if (caller.getUser().getRoleType() == RoleType.ADMIN && target.getRoleType() != RoleType.USER) {
            throw new AccessDeniedException("Access denied");
        }
        if (caller.getUser().getRoleType() != RoleType.SYS_ADMIN
                && caller.getUser().getRoleType() != RoleType.ADMIN) {
            throw new AccessDeniedException("Access denied");
        }
        // Last SYS_ADMIN guard.
        if (target.getRoleType() == RoleType.SYS_ADMIN) {
            long remaining = userRepository.findAll().stream()
                .filter(u -> u.getRoleType() == RoleType.SYS_ADMIN && !u.getId().equals(id))
                .count();
            if (remaining == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete the last SYS_ADMIN");
            }
        }
        DeleteUserResult result = new DeleteUserResult(target.getId(), target.getUsername());
        userRepository.delete(target);
        return result;
    }

    @Transactional
    public UpdateUserRolesResult updateRoles(UUID id, UpdateUserRolesRequest req, UserPrincipal caller) {
        User target = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        // ADMIN can only update USER-role users' assignments (not their role).
        if (caller.getUser().getRoleType() == RoleType.ADMIN) {
            if (target.getRoleType() != RoleType.USER) {
                throw new AccessDeniedException("Access denied");
            }
            if (req.roleType() != null && req.roleType() != RoleType.USER) {
                throw new AccessDeniedException("Access denied");
            }
        } else if (caller.getUser().getRoleType() != RoleType.SYS_ADMIN) {
            throw new AccessDeniedException("Access denied");
        }
        // Snapshot the old bundle for the audit row.
        List<String> oldCodes = target.getAssignedEngines().stream()
            .map(EngineEntity::getCode)
            .sorted()
            .toList();
        var oldRoles = List.of(new UpdateUserRolesResult.RoleAssignment(target.getRoleType(), oldCodes));
        if (req.roleType() != null) {
            target.setRoleType(req.roleType());
        }
        if (req.assignedEngineCodes() != null) {
            target.setAssignedEngines(resolveEngines(req.assignedEngineCodes()));
        }
        userRepository.save(target);
        List<String> newCodes = target.getAssignedEngines().stream()
            .map(EngineEntity::getCode)
            .sorted()
            .toList();
        var newRoles = List.of(new UpdateUserRolesResult.RoleAssignment(target.getRoleType(), newCodes));
        return new UpdateUserRolesResult(UserResponse.from(target), oldRoles, newRoles);
    }

    private Set<EngineEntity> resolveEngines(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return new LinkedHashSet<>();
        }
        Set<EngineEntity> resolved = new LinkedHashSet<>();
        for (String code : codes) {
            engineRepository.findByCodeAndDeletedAtIsNull(code)
                .ifPresent(resolved::add);
        }
        return resolved;
    }
}
