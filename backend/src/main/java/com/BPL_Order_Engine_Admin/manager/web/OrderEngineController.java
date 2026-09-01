package com.BPL_Order_Engine_Admin.manager.web;

import com.BPL_Order_Engine_Admin.manager.engine.EngineStatus;
import com.BPL_Order_Engine_Admin.manager.engine.LogLine;
import com.BPL_Order_Engine_Admin.manager.engine.OrderEngineFactory;
import com.BPL_Order_Engine_Admin.manager.engine.OrderEngineOperations;
import com.BPL_Order_Engine_Admin.manager.engine.dto.EngineActionResponse;
import com.BPL_Order_Engine_Admin.manager.engine.dto.EngineStatusResponse;
import com.BPL_Order_Engine_Admin.manager.engine.dto.LogLineResponse;
import com.BPL_Order_Engine_Admin.manager.engine.dto.LogPageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * REST surface for the order engines. All paths live under
 * {@code /api/engines/{engineId}/*}; the {@code engineId} path variable
 * is resolved through {@link OrderEngineFactory} so a missing engine
 * returns 404 via {@link com.BPL_Order_Engine_Admin.manager.engine.EngineNotSupportedException}.
 *
 * <p>Authorization is enforced declaratively in
 * {@code SecurityConfig} via path matchers, not via annotations on
 * individual methods, so the role rules in SPEC &sect;3.4 stay in one
 * place.
 */
@RestController
@RequestMapping("/api/engines")
public class OrderEngineController {

    /** Whitelisted values for the {@code limit} query param. */
    private static final Set<Integer> ALLOWED_LIMITS = Set.of(50, 100, 200);

    private final OrderEngineFactory factory;

    public OrderEngineController(OrderEngineFactory factory) {
        this.factory = factory;
    }

    /** {@code GET /api/engines/{engineId}/status} &mdash; ADMIN, VIEWER. */
    @GetMapping("/{engineId}/status")
    public EngineStatusResponse status(@PathVariable String engineId) {
        OrderEngineOperations engine = factory.get(engineId);
        // status() itself is lock-free (AtomicReference); lastTransitionAt
        // is read without a lock because the field is volatile and a
        // stale-by-microseconds read is harmless for the Status screen.
        return new EngineStatusResponse(
                engine.engineId(),
                engine.displayName(),
                engine.status(),
                readLastTransitionAt(engine),
                Instant.now());
    }

    /** {@code POST /api/engines/{engineId}/start} &mdash; ADMIN only. */
    @PostMapping("/{engineId}/start")
    public EngineActionResponse start(@PathVariable String engineId) {
        OrderEngineOperations engine = factory.get(engineId);
        engine.start();
        return actionResponse(engine, "started");
    }

    /** {@code POST /api/engines/{engineId}/stop} &mdash; ADMIN only. */
    @PostMapping("/{engineId}/stop")
    public EngineActionResponse stop(@PathVariable String engineId) {
        OrderEngineOperations engine = factory.get(engineId);
        engine.stop();
        return actionResponse(engine, "stopped");
    }

    /**
     * {@code GET /api/engines/{engineId}/logs?limit=100} &mdash; ADMIN, VIEWER.
     * Defaults to 100; only 50/100/200 are accepted (SPEC &sect;3.3).
     */
    @GetMapping("/{engineId}/logs")
    public LogPageResponse logs(
            @PathVariable String engineId,
            @RequestParam(name = "limit", required = false, defaultValue = "100") int limit) {

        if (!ALLOWED_LIMITS.contains(limit)) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "limit must be one of 50, 100, 200");
        }

        OrderEngineOperations engine = factory.get(engineId);
        List<LogLine> lines = engine.getLogs(limit);
        List<LogLineResponse> lineDtos = lines.stream()
                .map(l -> new LogLineResponse(l.timestamp(), l.level(), l.message()))
                .toList();

        return new LogPageResponse(
                engine.engineId(),
                limit,
                lineDtos.size(),
                lineDtos);
    }

    private static EngineActionResponse actionResponse(OrderEngineOperations engine, String verb) {
        Instant transitionedAt = readLastTransitionAt(engine);
        EngineStatus status = engine.status();
        String message = engine.displayName() + " " + verb + " (mock).";
        return new EngineActionResponse(
                engine.engineId(),
                engine.displayName(),
                status,
                message,
                transitionedAt);
    }

    /**
     * Reflective read of the implementation's last-transition timestamp
     * without forcing every engine to expose it on the interface. The
     * BPL impl returns the volatile field directly; future impls that
     * don't need this can return {@code null} and the field will be
     * omitted from the JSON.
     *
     * <p>Kept in one place so the interface stays minimal and the
     * controller is not coupled to the BPL-specific class.
     */
    private static Instant readLastTransitionAt(OrderEngineOperations engine) {
        if (engine instanceof com.BPL_Order_Engine_Admin.manager.engine.bpl.BplOrderEngineOperations bpl) {
            return bpl.lastTransitionAt();
        }
        return null;
    }
}
