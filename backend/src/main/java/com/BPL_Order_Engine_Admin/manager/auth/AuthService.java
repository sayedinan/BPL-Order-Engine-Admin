package com.BPL_Order_Engine_Admin.manager.auth;

import com.BPL_Order_Engine_Admin.manager.audit.AuditAction;
import com.BPL_Order_Engine_Admin.manager.audit.AuditLog;
import com.BPL_Order_Engine_Admin.manager.audit.AuditLogRepository;
import com.BPL_Order_Engine_Admin.manager.auth.dto.ChangePasswordRequest;
import com.BPL_Order_Engine_Admin.manager.auth.dto.LoginRequest;
import com.BPL_Order_Engine_Admin.manager.auth.dto.LoginResponse;
import com.BPL_Order_Engine_Admin.manager.auth.dto.UserSummary;
import com.BPL_Order_Engine_Admin.manager.user.User;
import com.BPL_Order_Engine_Admin.manager.user.UserRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Auth business logic. Login and change-password both write audit
 * rows. The successful login audit is also written by the
 * {@code AuthenticationSuccessListener} (#15.13) — this service
 * only writes the change-password audit and the failure case (the
 * AuthenticationFailureListener covers the generic "bad creds" path).
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuditLogRepository auditLogRepository,
            ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Authenticate by username + password. Throws
     * {@link BadCredentialsException} on a mismatch (caught by the
     * {@code AuthenticationFailureListener} for the audit row, then
     * surfaced to the controller as 401).
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest req) {
        User user = userRepository.findByUsernameIgnoreCase(req.username())
            .orElseThrow(() -> new BadCredentialsException("Bad credentials"));
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Bad credentials");
        }
        String token = jwtService.sign(user.getUsername(), user.getRoleType(), user.isMustChangePassword());
        Instant expiresAt = Instant.now().plus(jwtService.ttl());
        return new LoginResponse(token, expiresAt, UserSummary.from(user), user.isMustChangePassword());
    }

    /**
     * Change the current user's password. Writes a CHANGE_PASSWORD
     * audit row with reason "OK" or "BAD_CURRENT_PASSWORD".
     */
    @Transactional
    public LoginResponse changePassword(User user, ChangePasswordRequest req) {
        boolean ok = passwordEncoder.matches(req.currentPassword(), user.getPasswordHash());
        if (!ok) {
            writeAudit(user, "BAD_CURRENT_PASSWORD");
            throw new BadCredentialsException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);

        String token = jwtService.sign(user.getUsername(), user.getRoleType(), false);
        writeAudit(user, "OK");
        return new LoginResponse(token, Instant.now().plus(jwtService.ttl()), UserSummary.from(user), false);
    }

    /** Write a CHANGE_PASSWORD audit row for the user. */
    private void writeAudit(User user, String reason) {
        try {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("actorUsername", user.getUsername());
            details.put("reason", reason);
            AuditLog row = new AuditLog();
            row.setActorUsername(user.getUsername());
            row.setActorRole(user.getRoleType().name());
            row.setAction(AuditAction.CHANGE_PASSWORD);
            row.setTargetEngineCode(null);
            row.setDetails(objectMapper.writeValueAsString(details));
            auditLogRepository.save(row);
        } catch (JacksonException e) {
            log.warn("Failed to serialize change-password audit details", e);
        }
    }
}
