package com.BPL_Order_Engine_Admin.manager.user;

/**
 * v0.3 role hierarchy (SPEC §3.1). Order is significant:
 * <ol>
 *   <li>SYS_ADMIN — can do anything; full access to the audit log, engines, users.</li>
 *   <li>ADMIN — can manage USER-role users and read all engines; cannot create/delete engines.</li>
 *   <li>USER — limited to {@code assignedEngines}; no admin panel access; no audit log.</li>
 * </ol>
 *
 * <p>Hierarchy in code: {@code USER < ADMIN < SYS_ADMIN}. The order
 * is enforced by Spring Security's role hierarchy in
 * {@code SecurityConfig} (built in #15).
 */
public enum RoleType {
    SYS_ADMIN,
    ADMIN,
    USER
}
