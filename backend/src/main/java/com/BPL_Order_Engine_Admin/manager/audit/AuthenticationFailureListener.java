package com.BPL_Order_Engine_Admin.manager.audit;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * v0.3 audit listener for failed authentication events
 * (SPEC §3.5 / API.md §6). Writes a {@code LOGIN_FAIL} row with
 * the attempted username and the reason.
 *
 * <p>The security context is empty on failure, so we extract the
 * username from the exception's {@code authentication.getName()}
 * (which Spring populates from the submitted request).
 */
@Component
public class AuthenticationFailureListener {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationFailureListener.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuthenticationFailureListener(
            AuditLogRepository auditLogRepository,
            ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        String attempted = "<unknown>";
        if (event.getAuthentication() != null) {
            attempted = event.getAuthentication().getName();
        }
        String reason = "BAD_CREDENTIALS"; // default; expand when USER_DISABLED is added
        AuthenticationException ex = event.getException();
        if (ex != null && ex.getMessage() != null) {
            // Optional refinement: if the exception class is
            // DisabledException/AccountLockedException, surface it.
            String cls = ex.getClass().getSimpleName();
            if (cls.equals("DisabledException")) reason = "USER_DISABLED";
            else if (cls.equals("AccountLockedException")) reason = "ACCOUNT_LOCKED";
        }
        try {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("reason", reason);
            AuditLog row = new AuditLog();
            row.setActorUsername(attempted);
            row.setActorRole("USER");
            row.setAction(AuditAction.LOGIN_FAIL);
            row.setTargetEngineCode(null);
            row.setDetails(objectMapper.writeValueAsString(details));
            auditLogRepository.save(row);
        } catch (JacksonException e) {
            log.warn("Failed to write LOGIN_FAIL audit row", e);
        }
    }
}
