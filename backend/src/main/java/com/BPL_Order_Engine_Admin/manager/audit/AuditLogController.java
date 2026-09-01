package com.BPL_Order_Engine_Admin.manager.audit;

import com.BPL_Order_Engine_Admin.manager.audit.dto.AuditLogResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * v0.3 audit log read endpoint (SPEC §4.5 / API.md §4).
 *
 * <p>USER is rejected outright (403) — they never see the audit
 * trail. The route is the system audit log; engine execution logs
 * live at {@code /api/engines/{code}/logs}.
 */
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditService auditService;

    public AuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SYS_ADMIN', 'ADMIN')")
    public AuditPageResponse list(
            @RequestParam(name = "actor", required = false) String actor,
            @RequestParam(name = "action", required = false) AuditAction action,
            @RequestParam(name = "engine", required = false) String engine,
            @RequestParam(name = "from", required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(name = "to", required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        AuditService.AuditLogPage page1 = auditService.list(actor, action, engine, from, to, page, size);
        return new AuditPageResponse(page1.items(), page1.page(), page1.size(), page1.total());
    }

    public record AuditPageResponse(
        List<AuditLogResponse> items,
        int page,
        int size,
        long total
    ) {}
}
