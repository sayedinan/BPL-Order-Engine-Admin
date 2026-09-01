package com.BPL_Order_Engine_Admin.manager.audit;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * v0.3 audit listener for successful authentication events
 * (SPEC §3.5 / API.md §6). Writes a {@code LOGIN_SUCCESS} row with
 * {@code details.reason = "OK"}.
 */
@Component
public class AuthenticationSuccessListener {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationSuccessListener.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuthenticationSuccessListener(
            AuditLogRepository auditLogRepository,
            ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        String role = "USER"; // conservative default
        if (event.getAuthentication().getAuthorities() != null) {
            for (var a : event.getAuthentication().getAuthorities()) {
                String auth = a.getAuthority();
                if (auth.startsWith("ROLE_")) {
                    role = auth.substring("ROLE_".length());
                    break;
                }
            }
        }
        try {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("reason", "OK");
            AuditLog row = new AuditLog();
            row.setActorUsername(username);
            row.setActorRole(role);
            row.setAction(AuditAction.LOGIN_SUCCESS);
            row.setTargetEngineCode(null);
            row.setDetails(objectMapper.writeValueAsString(details));
            auditLogRepository.save(row);
        } catch (JacksonException e) {
            log.warn("Failed to write LOGIN_SUCCESS audit row", e);
        }
    }
}
