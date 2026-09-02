package com.BPL_Order_Engine_Admin.manager.engine;

import com.BPL_Order_Engine_Admin.manager.audit.AuditAction;
import com.BPL_Order_Engine_Admin.manager.audit.Audited;
import com.BPL_Order_Engine_Admin.manager.auth.UserPrincipal;
import com.BPL_Order_Engine_Admin.manager.engine.dto.CreateEngineRequest;
import com.BPL_Order_Engine_Admin.manager.engine.dto.EngineActionResponse;
import com.BPL_Order_Engine_Admin.manager.engine.dto.EngineResponse;
import com.BPL_Order_Engine_Admin.manager.engine.dto.EngineStatusResponse;
import com.BPL_Order_Engine_Admin.manager.engine.dto.LogPageResponse;
import com.BPL_Order_Engine_Admin.manager.engine.dto.UpdateEngineSshRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * v0.3 engine controller (SPEC §4.3 / API.md §2).
 *
 * <p>All paths under {@code /api/engines/{code}/*} are role-gated via
 * {@code @PreAuthorize}; the {@code EngineActionService} applies the
 * per-engine USER-assignment filter. Action endpoints
 * (status / start / stop / logs) and CRUD endpoints (list / create /
 * delete / patch ssh) live in the same controller for now;
 * splitting them is a follow-up.
 */
@RestController
@RequestMapping("/api/engines")
public class EngineController {

    private final EngineService engineService;
    private final EngineActionService actionService;

    public EngineController(EngineService engineService, EngineActionService actionService) {
        this.engineService = engineService;
        this.actionService = actionService;
    }

    // ---- CRUD (task #17) ----

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<EngineResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return engineService.listFor(principal.getUser());
    }

    @PostMapping
    @PreAuthorize("hasRole('SYS_ADMIN')")
    @Audited(
        action = AuditAction.CREATE_ENGINE,
        details = "{ engineCode: #result.code(), mode: #result.mode() }"
    )
    public ResponseEntity<EngineResponse> create(@Valid @RequestBody CreateEngineRequest req) {
        EngineResponse created = engineService.create(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{code}")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    @Audited(
        action = AuditAction.DELETE_ENGINE,
        targetEngineFromPath = true,
        details = "{ engineCode: #code }"
    )
    public ResponseEntity<Void> delete(@PathVariable String code) {
        engineService.softDelete(code);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{code}/ssh")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    @Audited(
        action = AuditAction.UPDATE_ENGINE_SSH,
        targetEngineFromPath = true,
        details = "{ engineCode: #code, fieldsChanged: #result.fieldsChanged() }"
    )
    public EngineResponse updateSsh(@PathVariable String code,
                                    @RequestBody UpdateEngineSshRequest req) {
        return engineService.updateSsh(code, req).engine();
    }

    // ---- Action endpoints (task #18) ----

    @GetMapping("/{code}/status")
    @PreAuthorize("isAuthenticated()")
    public EngineStatusResponse status(@PathVariable String code,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        return actionService.status(code, principal);
    }

    @PostMapping("/{code}/start")
    @PreAuthorize("isAuthenticated()")
    @Audited(
        action = AuditAction.START_ENGINE,
        targetEngineFromPath = true,
        details = "{ engineCode: #code, status: #result.status(), exitCode: #result.exitCode() }"
    )
    public EngineActionResponse start(@PathVariable String code,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        return actionService.start(code, principal);
    }

    @PostMapping("/{code}/stop")
    @PreAuthorize("isAuthenticated()")
    @Audited(
        action = AuditAction.STOP_ENGINE,
        targetEngineFromPath = true,
        details = "{ engineCode: #code, status: #result.status(), exitCode: #result.exitCode() }"
    )
    public EngineActionResponse stop(@PathVariable String code,
                                    @AuthenticationPrincipal UserPrincipal principal) {
        return actionService.stop(code, principal);
    }

    @GetMapping("/{code}/logs")
    @PreAuthorize("isAuthenticated()")
    public LogPageResponse logs(@PathVariable String code,
                                @RequestParam(name = "limit", defaultValue = "100") int limit,
                                @AuthenticationPrincipal UserPrincipal principal) {
        return actionService.logs(code, limit, principal);
    }
}
