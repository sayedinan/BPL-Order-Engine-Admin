package com.BPL_Order_Engine_Admin.manager.audit;

import com.BPL_Order_Engine_Admin.manager.audit.dto.AuditLogResponse;
import com.BPL_Order_Engine_Admin.manager.engine.EngineEntity;
import com.BPL_Order_Engine_Admin.manager.engine.EngineRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * v0.3 audit log read service (SPEC §4.5 / API.md §4).
 *
 * <p>USER is rejected outright at the controller (the
 * {@code @PreAuthorize} returns 403). ADMIN / SYS_ADMIN see the full
 * system log.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final EngineRepository engineRepository;
    private final ObjectMapper objectMapper;

    public AuditService(
            AuditLogRepository auditLogRepository,
            EngineRepository engineRepository,
            ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.engineRepository = engineRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AuditLogPage list(
            String actor,
            AuditAction action,
            String engineCode,
            Instant from,
            Instant to,
            int page,
            int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "from must be before to");
        }
        if (size < 1 || size > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "size must be between 1 and 200");
        }

        // Resolve engine code to UUID if present.
        UUID engineId = null;
        if (engineCode != null && !engineCode.isBlank()) {
            Optional<EngineEntity> eng = engineRepository.findByCodeAndDeletedAtIsNull(engineCode);
            // If the engine code is provided but not found, return empty
            // rather than erroring — the user might be searching
            // historical data.
            if (eng.isEmpty()) {
                return new AuditLogPage(List.of(), 0, page, size);
            }
            engineId = eng.get().getId();
        }

        Instant fromOrMin = from == null ? Instant.EPOCH : from;
        Instant toOrMax = to == null ? Instant.now().plusSeconds(60) : to;

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<AuditLog> rows;
        // Note: the engine filter is implemented as a "filter the page
        // after the repo" step. The AuditLogRepository query helpers
        // don't take the engine UUID; for v0.3's low write volume the
        // post-filter is fine. (For high volume we'd add a custom
        // Specification.)
        if (actor != null && !actor.isBlank() && action != null) {
            rows = auditLogRepository.findByActorUsernameAndActionAndTimestampBetween(
                actor, action, fromOrMin, toOrMax, pageable);
        } else if (actor != null && !actor.isBlank()) {
            rows = auditLogRepository.findByActorUsernameAndTimestampBetween(
                actor, fromOrMin, toOrMax, pageable);
        } else if (action != null) {
            rows = auditLogRepository.findByActionAndTimestampBetween(
                action, fromOrMin, toOrMax, pageable);
        } else {
            rows = auditLogRepository.findByTimestampBetween(
                fromOrMin, toOrMax, pageable);
        }

        // Post-filter by engine UUID if present.
        List<AuditLog> filtered = engineId == null
            ? rows.getContent()
            : rows.getContent().stream()
                .filter(r -> engineCode.equals(r.getTargetEngineCode()))
                .toList();

        List<AuditLogResponse> items = filtered.stream()
            .map(r -> AuditLogResponse.from(r, objectMapper))
            .toList();
        return new AuditLogPage(items, rows.getTotalElements(), page, size);
    }

    public record AuditLogPage(List<AuditLogResponse> items, long total, int page, int size) {}
}
