package com.BPL_Order_Engine_Admin.manager.auth;

import com.BPL_Order_Engine_Admin.manager.auth.dto.ChangePasswordRequest;
import com.BPL_Order_Engine_Admin.manager.auth.dto.LoginRequest;
import com.BPL_Order_Engine_Admin.manager.auth.dto.LoginResponse;
import com.BPL_Order_Engine_Admin.manager.auth.dto.UserSummary;
import com.BPL_Order_Engine_Admin.manager.user.User;
import com.BPL_Order_Engine_Admin.manager.user.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Auth business logic.
 *
 * <p>This service does NOT write audit rows directly. The login
 * success row is written by {@code AuthenticationSuccessListener};
 * the login failure row by {@code AuthenticationFailureListener};
 * the change-password row by the {@code AuditAspect} via the
 * {@code @Audited} annotation on {@code AuthController.changePassword}.
 * The aspect stamps {@code details.reason} from the thrown
 * exception's {@link com.BPL_Order_Engine_Admin.manager.audit.AuditableFailure}
 * payload (see {@link BadCurrentPasswordException}).
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Authenticate by username + password. Throws
     * {@link BadCredentialsException} on a mismatch (caught by the
     * {@code AuthenticationFailureListener} for the audit row, then
     * surfaced to {@code ApiExceptionHandler} as 401 with the
     * message "Invalid credentials").
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest req) {
        User user = userRepository.findByUsernameIgnoreCase(req.username())
            .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        String token = jwtService.sign(user.getUsername(), user.getRoleType(), user.isMustChangePassword());
        Instant expiresAt = Instant.now().plus(jwtService.ttl());
        return new LoginResponse(token, expiresAt, UserSummary.from(user), user.isMustChangePassword());
    }

    /**
     * Change the current user's password. Throws
     * {@link BadCurrentPasswordException} (which is {@code AuditableFailure}
     * so the aspect stamps {@code details.reason = "BAD_CURRENT_PASSWORD"}
     * on the failure row) when the current password doesn't match.
     */
    @Transactional
    public LoginResponse changePassword(User user, ChangePasswordRequest req) {
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new BadCurrentPasswordException();
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);

        String token = jwtService.sign(user.getUsername(), user.getRoleType(), false);
        return new LoginResponse(token, Instant.now().plus(jwtService.ttl()), UserSummary.from(user), false);
    }
}
