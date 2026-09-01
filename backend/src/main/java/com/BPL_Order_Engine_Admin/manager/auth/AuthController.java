package com.BPL_Order_Engine_Admin.manager.auth;

import com.BPL_Order_Engine_Admin.manager.audit.AuditAction;
import com.BPL_Order_Engine_Admin.manager.audit.AuditLog;
import com.BPL_Order_Engine_Admin.manager.audit.AuditLogRepository;
import com.BPL_Order_Engine_Admin.manager.auth.dto.ChangePasswordRequest;
import com.BPL_Order_Engine_Admin.manager.auth.dto.LoginRequest;
import com.BPL_Order_Engine_Admin.manager.auth.dto.LoginResponse;
import com.BPL_Order_Engine_Admin.manager.auth.dto.UserSummary;
import com.BPL_Order_Engine_Admin.manager.user.User;
import tools.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * v0.3 Auth controller (SPEC §4.2, API.md §1).
 *
 * <p>Endpoints: login, logout, me, change-password.
 *
 * <p>Audit row for {@code LOGOUT} is written inline here (small,
 * single-purpose, and the @Audited annotation would add no value
 * over an explicit write). The {@code LOGIN_SUCCESS} /
 * {@code LOGIN_FAIL} rows are written by the auth listeners
 * (#15.13). The {@code CHANGE_PASSWORD} row is written by
 * {@code AuthService}.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuthController(
            AuthService authService,
            AuditLogRepository auditLogRepository,
            ObjectMapper objectMapper) {
        this.authService = authService;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    /**
     * Stateless logout. Writes a {@code LOGOUT} audit row. Client
     * drops the token; the server has no blacklist in v0.3 (token
     * expiry is the revocation mechanism).
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal != null) {
            writeLogoutAudit(principal.getUser());
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/me")
    public UserSummary me(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedException("Authentication required");
        }
        return UserSummary.from(principal.getUser());
    }

    @PostMapping("/change-password")
    public LoginResponse changePassword(
            @Valid @RequestBody ChangePasswordRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedException("Authentication required");
        }
        return authService.changePassword(principal.getUser(), req);
    }

    private void writeLogoutAudit(User user) {
        try {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("actorUsername", user.getUsername());
            AuditLog row = new AuditLog();
            row.setActorUsername(user.getUsername());
            row.setActorRole(user.getRoleType().name());
            row.setAction(AuditAction.LOGOUT);
            row.setTargetEngineCode(null);
            row.setDetails(objectMapper.writeValueAsString(details));
            auditLogRepository.save(row);
        } catch (Exception e) {
            log.warn("Failed to write LOGOUT audit row", e);
        }
    }
}
