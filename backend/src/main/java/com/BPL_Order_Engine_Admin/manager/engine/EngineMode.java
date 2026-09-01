package com.BPL_Order_Engine_Admin.manager.engine;

/**
 * Engine execution mode (SPEC §3.3 / §6).
 * <ul>
 *   <li>{@code MOCK} — in-memory state machine; no network.</li>
 *   <li>{@code REAL} — Apache MINA SSHD to the engine's {@code serverIp}.</li>
 * </ul>
 */
public enum EngineMode {
    MOCK,
    REAL
}
