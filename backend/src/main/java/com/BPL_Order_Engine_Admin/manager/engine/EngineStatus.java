package com.BPL_Order_Engine_Admin.manager.engine;

/**
 * Engine runtime status (SPEC §3.3 / API.md §2.5).
 * <ul>
 *   <li>{@code RUNNING} — the engine is up and accepting work.</li>
 *   <li>{@code STOPPED} — the engine is down.</li>
 *   <li>{@code ERROR} — a recent action (start/stop/script) failed; needs manual recovery.</li>
 * </ul>
 */
public enum EngineStatus {
    RUNNING,
    STOPPED,
    ERROR
}
