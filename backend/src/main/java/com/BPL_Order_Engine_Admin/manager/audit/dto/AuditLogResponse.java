package com.BPL_Order_Engine_Admin.manager.audit.dto;

import com.BPL_Order_Engine_Admin.manager.audit.AuditAction;
import com.BPL_Order_Engine_Admin.manager.audit.AuditLog;
import com.BPL_Order_Engine_Admin.manager.user.RoleType;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * v0.3 audit log row DTO. {@code details} is deserialized from the
 * jsonb column into a {@code Map<String, Object>} for the response
 * (per API.md §4.1).
 */
public record AuditLogResponse(
    UUID id,
    Instant timestamp,
    String actorUsername,
    RoleType actorRole,
    AuditAction action,
    String targetEngineCode,
    Map<String, Object> details
) {
    public static AuditLogResponse from(AuditLog row, ObjectMapper objectMapper) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (row.getDetails() != null && !row.getDetails().isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(row.getDetails(), Map.class);
                details.putAll(parsed);
            } catch (Exception e) {
                details.put("raw", row.getDetails());
            }
        }
        return new AuditLogResponse(
            row.getId(),
            row.getTimestamp(),
            row.getActorUsername(),
            row.getActorRole() == null ? null : RoleType.valueOf(row.getActorRole()),
            row.getAction(),
            row.getTargetEngineCode(),
            details
        );
    }
}
