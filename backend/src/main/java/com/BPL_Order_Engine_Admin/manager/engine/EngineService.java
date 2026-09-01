package com.BPL_Order_Engine_Admin.manager.engine;

import com.BPL_Order_Engine_Admin.manager.engine.dto.CreateEngineRequest;
import com.BPL_Order_Engine_Admin.manager.engine.dto.EngineResponse;
import com.BPL_Order_Engine_Admin.manager.engine.dto.UpdateEngineSshRequest;
import com.BPL_Order_Engine_Admin.manager.user.RoleType;
import com.BPL_Order_Engine_Admin.manager.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * v0.3 engine CRUD service (SPEC §4.3 / API.md §2).
 *
 * <p>Soft delete: the {@code deletedAt} column is set; the factory
 * filter {@code findByCodeAndDeletedAtIsNull} excludes the row from
 * lookups, so the engine is "deleted" from the runtime without
 * losing the audit trail.
 */
@Service
public class EngineService {

    private final EngineRepository engineRepository;
    private final OrderEngineFactory factory;

    public EngineService(EngineRepository engineRepository, OrderEngineFactory factory) {
        this.engineRepository = engineRepository;
        this.factory = factory;
    }

    @Transactional(readOnly = true)
    public List<EngineResponse> listFor(User viewer) {
        List<EngineEntity> rows;
        if (viewer.getRoleType() == RoleType.SYS_ADMIN
                || viewer.getRoleType() == RoleType.ADMIN) {
            rows = engineRepository.findAll().stream()
                .filter(e -> e.getDeletedAt() == null)
                .toList();
        } else {
            // USER: only assigned engines
            Set<String> assigned = viewer.getAssignedEngines().stream()
                .map(EngineEntity::getCode)
                .collect(java.util.stream.Collectors.toSet());
            rows = engineRepository.findAll().stream()
                .filter(e -> e.getDeletedAt() == null && assigned.contains(e.getCode()))
                .toList();
        }
        return rows.stream().map(EngineResponse::from).toList();
    }

    @Transactional
    public EngineResponse create(CreateEngineRequest req) {
        if (engineRepository.findByCodeAndDeletedAtIsNull(req.code()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Engine '" + req.code() + "' already exists");
        }
        // Sanity check scripts for REAL mode: reject ; rm, && rm, | rm, backticks.
        if (req.mode() == EngineMode.REAL) {
            requireSafeScript(req.startScript(), "startScript");
            requireSafeScript(req.stopScript(), "stopScript");
            requireSafeScript(req.logScript(), "logScript");
        }
        EngineEntity e = new EngineEntity();
        e.setCode(req.code());
        e.setName(req.name());
        e.setMode(req.mode());
        e.setServerIp(req.serverIp());
        e.setServerUsername(req.serverUsername());
        e.setServerPassword(req.serverPassword());
        e.setStartScript(emptyToNull(req.startScript()));
        e.setStopScript(emptyToNull(req.stopScript()));
        e.setLogScript(emptyToNull(req.logScript()));
        e.setStatus(EngineStatus.STOPPED);
        e.setLastTransitionAt(null);
        engineRepository.save(e);
        return EngineResponse.from(e);
    }

    @Transactional
    public void softDelete(String code) {
        EngineEntity e = engineRepository.findByCodeAndDeletedAtIsNull(code)
            .orElseThrow(() -> new EngineNotSupportedException(code));
        e.setDeletedAt(Instant.now());
        engineRepository.save(e);
    }

    @Transactional
    public EngineResponse updateSsh(String code, UpdateEngineSshRequest req) {
        EngineEntity e = engineRepository.findByCodeAndDeletedAtIsNull(code)
            .orElseThrow(() -> new EngineNotSupportedException(code));
        Set<String> changed = new LinkedHashSet<>();
        if (req.name() != null) { e.setName(req.name()); changed.add("name"); }
        if (req.mode() != null) { e.setMode(req.mode()); changed.add("mode"); }
        if (req.serverIp() != null) { e.setServerIp(req.serverIp()); changed.add("serverIp"); }
        if (req.serverUsername() != null) { e.setServerUsername(req.serverUsername()); changed.add("serverUsername"); }
        if (req.serverPassword() != null) { e.setServerPassword(req.serverPassword()); changed.add("serverPassword"); }
        if (req.startScript() != null) { e.setStartScript(emptyToNull(req.startScript())); changed.add("startScript"); }
        if (req.stopScript() != null) { e.setStopScript(emptyToNull(req.stopScript())); changed.add("stopScript"); }
        if (req.logScript() != null) { e.setLogScript(emptyToNull(req.logScript())); changed.add("logScript"); }
        engineRepository.save(e);
        return EngineResponse.from(e);
    }

    private static void requireSafeScript(String script, String field) {
        if (script == null) return;
        if (script.matches(".*(;\\s*rm|&&\\s*rm\\b|\\|\\s*rm\\b|`.*`).*")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                field + " contains an unsafe pattern (rm, backticks)");
        }
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
