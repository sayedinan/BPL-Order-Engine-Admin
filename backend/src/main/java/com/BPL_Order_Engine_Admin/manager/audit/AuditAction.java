package com.BPL_Order_Engine_Admin.manager.audit;

/**
 * v0.3 audit log action enum (SPEC §3.5 / API.md §6).
 *
 * <p>The string is stored as-is in the database; historical rows
 * survive enum renames because the column is {@code VARCHAR}, not
 * an integer.
 */
public enum AuditAction {
    CREATE_USER,
    DELETE_USER,
    UPDATE_USER_ROLES,
    CREATE_ENGINE,
    DELETE_ENGINE,
    UPDATE_ENGINE_SSH,
    START_ENGINE,
    STOP_ENGINE,
    LOGIN_SUCCESS,
    LOGIN_FAIL,
    LOGOUT,
    CHANGE_PASSWORD
}
