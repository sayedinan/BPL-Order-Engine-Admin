package com.BPL_Order_Engine_Admin.manager.engine;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * Factory Method that resolves an {@link OrderEngineOperations} bean by
 * engine id. Backed by Spring's {@code Map<String, OrderEngineOperations>}
 * autowiring &mdash; every {@code @Service("bpl")} (or {@code "pcl"})
 * bean is picked up automatically by name, so adding a new engine
 * requires no changes here (see SPEC.md &sect;5.1).
 *
 * <p>On a miss, throws {@link EngineNotSupportedException}, which the
 * {@code @ControllerAdvice} maps to HTTP 404.
 */
@Component
public class OrderEngineFactory {

    private final Map<String, OrderEngineOperations> engines;

    public OrderEngineFactory(Map<String, OrderEngineOperations> engines) {
        // Defensive copy + unmodifiable view; prevents callers from
        // mutating Spring's autowired map.
        this.engines = Collections.unmodifiableMap(engines);
    }

    /**
     * @throws EngineNotSupportedException if no bean is registered for
     *         {@code engineId}.
     */
    public OrderEngineOperations get(String engineId) {
        OrderEngineOperations ops = engines.get(engineId);
        if (ops == null) {
            throw new EngineNotSupportedException(engineId);
        }
        return ops;
    }

    /**
     * Read-only view of the registered engines. Intended for the optional
     * {@code GET /api/engines} discovery endpoint (SPEC &sect;5.1, step 6);
     * not currently exposed via a controller.
     */
    public Map<String, OrderEngineOperations> engines() {
        return engines;
    }
}
