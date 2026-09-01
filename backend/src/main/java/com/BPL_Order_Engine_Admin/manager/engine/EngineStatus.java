package com.BPL_Order_Engine_Admin.manager.engine;

/**
 * Lifecycle states for an order engine. The mock implementation only ever
 * transitions between {@link #STOPPED} and {@link #RUNNING}; {@link #ERROR}
 * exists so the contract is already compatible with a future real health
 * check that reports failures (see SPEC.md &sect;2.4 and &sect;5.2).
 */
public enum EngineStatus {
    RUNNING,
    STOPPED,
    ERROR
}
