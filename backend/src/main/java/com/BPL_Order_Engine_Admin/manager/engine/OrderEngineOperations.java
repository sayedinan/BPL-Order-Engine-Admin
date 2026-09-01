package com.BPL_Order_Engine_Admin.manager.engine;

import java.util.List;

/**
 * Strategy interface every order engine must implement. The controller
 * talks only to this interface; concrete engines live under their own
 * sub-packages (see SPEC.md &sect;2.3 and &sect;5.1).
 *
 * <p>Implementations are expected to be thread-safe: the controller, the
 * status poller, and the mock log generator may all call into a single
 * instance concurrently.
 */
public interface OrderEngineOperations {

    /**
     * The {@code engineId} under which this implementation is registered
     * as a Spring bean (e.g. {@code "bpl"}). Used by the factory and
     * surfaced in API responses.
     */
    String engineId();

    /**
     * Human-readable name for the UI (e.g. {@code "BPL Order Engine"}).
     */
    default String displayName() {
        return engineId();
    }

    /**
     * Returns the current engine state. Implementations are expected to
     * be cheap &mdash; this is polled by the Status screen every 5s.
     */
    EngineStatus status();

    /**
     * Transitions the engine from {@code STOPPED} to {@code RUNNING}.
     *
     * @throws IllegalStateException if the engine is already in a state
     *         that cannot accept a start (mapped to HTTP 409).
     */
    void start();

    /**
     * Transitions the engine from {@code RUNNING} to {@code STOPPED}.
     *
     * @throws IllegalStateException if the engine is already in a state
     *         that cannot accept a stop (mapped to HTTP 409).
     */
    void stop();

    /**
     * Returns up to {@code limit} most-recent log lines, ordered oldest
     * to newest. {@code limit} is a hard cap; implementations may return
     * fewer lines if the buffer is shorter.
     */
    List<LogLine> getLogs(int limit);
}
