package com.BPL_Order_Engine_Admin.manager.engine;

import com.BPL_Order_Engine_Admin.manager.auth.UserPrincipal;
import com.BPL_Order_Engine_Admin.manager.engine.dto.EngineActionResponse;
import com.BPL_Order_Engine_Admin.manager.engine.dto.EngineStatusResponse;
import com.BPL_Order_Engine_Admin.manager.engine.dto.LogLineResponse;
import com.BPL_Order_Engine_Admin.manager.engine.dto.LogPageResponse;
import com.BPL_Order_Engine_Admin.manager.user.RoleType;
import com.BPL_Order_Engine_Admin.manager.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * v0.3 engine action service (SPEC §4.3 / API.md §2.5-2.8).
 *
 * <p>Resolves an engine by code, applies the role+assignment
 * gate, calls the matching {@code OrderEngineOperations} method, and
 * returns the DTO. The audit row is written by the controller's
 * {@code @Audited} annotation (the {@code AuditAspect} sees the
 * throw / return).
 *
 * <p>For REAL-mode engines, the start/stop also drives the
 * {@link LogTailerRegistry} — start begins a background tail; stop
 * ends it.
 */
@Service
public class EngineActionService {

    private final OrderEngineFactory factory;
    private final LogTailerRegistry tailerRegistry;
    private final EngineRepository engineRepository;

    public EngineActionService(
            OrderEngineFactory factory,
            LogTailerRegistry tailerRegistry,
            EngineRepository engineRepository) {
        this.factory = factory;
        this.tailerRegistry = tailerRegistry;
        this.engineRepository = engineRepository;
    }

    public EngineStatusResponse status(String code, UserPrincipal caller) {
        OrderEngineOperations op = resolveAndAuthorize(code, caller);
        return EngineStatusResponse.from(op);
    }

    public EngineActionResponse start(String code, UserPrincipal caller) {
        OrderEngineOperations op = resolveAndAuthorize(code, caller);
        if (op.status() == EngineStatus.RUNNING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Engine '" + code + "' is already RUNNING");
        }
        EngineActionResponse res = EngineActionResponse.from(op.start());
        // Start a background tailer for REAL engines only.
        if (op.currentMode() == EngineMode.REAL) {
            tailerRegistry.start(code);
        }
        return res;
    }

    public EngineActionResponse stop(String code, UserPrincipal caller) {
        OrderEngineOperations op = resolveAndAuthorize(code, caller);
        if (op.status() == EngineStatus.STOPPED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Engine '" + code + "' is already STOPPED");
        }
        EngineActionResponse res = EngineActionResponse.from(op.stop());
        tailerRegistry.stop(code);
        return res;
    }

    public LogPageResponse logs(String code, int limit, UserPrincipal caller) {
        OrderEngineOperations op = resolveAndAuthorize(code, caller);
        if (limit != 50 && limit != 100 && limit != 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "limit must be one of 50, 100, 200");
        }
        var lines = op.getLogs(limit).stream().map(LogLineResponse::from).toList();
        return new LogPageResponse(code, limit, lines.size(), lines);
    }

    private OrderEngineOperations resolveAndAuthorize(String code, UserPrincipal caller) {
        if (caller == null) {
            throw new AccessDeniedException("Authentication required");
        }
        OrderEngineOperations op = factory.get(code);
        User u = caller.getUser();
        if (u.getRoleType() == RoleType.USER) {
            Set<String> codes = u.getAssignedEngines().stream()
                .map(EngineEntity::getCode)
                .collect(Collectors.toSet());
            if (!codes.contains(code)) {
                throw new AccessDeniedException("Access denied");
            }
        }
        return op;
    }
}
