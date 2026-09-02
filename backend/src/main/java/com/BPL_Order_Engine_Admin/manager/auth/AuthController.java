package com.BPL_Order_Engine_Admin.manager.auth;

import com.BPL_Order_Engine_Admin.manager.audit.AuditAction;
import com.BPL_Order_Engine_Admin.manager.audit.Audited;
import com.BPL_Order_Engine_Admin.manager.auth.dto.ChangePasswordRequest;
import com.BPL_Order_Engine_Admin.manager.auth.dto.LoginRequest;
import com.BPL_Order_Engine_Admin.manager.auth.dto.LoginResponse;
import com.BPL_Order_Engine_Admin.manager.auth.dto.UserSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * v0.3 Auth controller (SPEC §4.2, API.md §1).
 *
 * <p>Endpoints: login, logout, me, change-password.
 *
 * <p>Audit rows for {@code LOGOUT} and {@code CHANGE_PASSWORD} are
 * written by the {@code AuditAspect} via {@code @Audited} — the
 * controller never calls {@code auditLogRepository.save(...)}
 * directly (SPEC §7.4 anti-pattern). {@code LOGIN_SUCCESS} and
 * {@code LOGIN_FAIL} rows are written by the auth listeners.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    /**
     * Stateless logout. The {@code @Audited} annotation drives the
     * {@code LOGOUT} audit row (actor = security context). The
     * client drops the token; the server has no blacklist in v0.3
     * (token expiry is the revocation mechanism).
     */
    @PostMapping("/logout")
    @Audited(action = AuditAction.LOGOUT)
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedException("Authentication required");
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

    /**
     * Force-change-password endpoint. The {@code @Audited} annotation
     * drives the {@code CHANGE_PASSWORD} audit row:
     * <ul>
     *   <li>success: {@code details: { actorUsername: #principal.user.username, reason: "OK" }}</li>
     *   <li>bad current password: the thrown {@code BadCurrentPasswordException}
     *       implements {@code AuditableFailure} so the aspect stamps
     *       {@code details.reason = "BAD_CURRENT_PASSWORD"} before
     *       adding {@code error} and {@code message}.</li>
     * </ul>
     */
    @PostMapping("/change-password")
    @Audited(
        action = AuditAction.CHANGE_PASSWORD,
        details = "{ actorUsername: #principal.user.username, reason: 'OK' }"
    )
    public LoginResponse changePassword(
            @Valid @RequestBody ChangePasswordRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedException("Authentication required");
        }
        return authService.changePassword(principal.getUser(), req);
    }
}
