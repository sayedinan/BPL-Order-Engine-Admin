package com.BPL_Order_Engine_Admin.manager.auth;

import com.BPL_Order_Engine_Admin.manager.audit.AuditableFailure;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thrown by {@code AuthService.changePassword} when the supplied
 * {@code currentPassword} does not match the stored hash.
 *
 * <p>Maps to HTTP 401 via {@code ApiExceptionHandler} with the
 * message "Current password is incorrect" (SPEC §4.2, no
 * enumeration). Implements {@link AuditableFailure} so the
 * {@code AuditAspect} stamps {@code details.reason =
 * "BAD_CURRENT_PASSWORD"} on the failure path — keeping the aspect
 * as the sole audit row writer.
 */
public class BadCurrentPasswordException extends RuntimeException implements AuditableFailure {

    public BadCurrentPasswordException() {
        super("Current password is incorrect");
    }

    @Override
    public Map<String, Object> auditDetails() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", "BAD_CURRENT_PASSWORD");
        return details;
    }
}
