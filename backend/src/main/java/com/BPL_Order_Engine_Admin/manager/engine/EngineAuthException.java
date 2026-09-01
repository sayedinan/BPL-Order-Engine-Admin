package com.BPL_Order_Engine_Admin.manager.engine;

/**
 * Thrown by {@code SshBackedEngine} on SSH authentication failure
 * (bad username/password). Maps to HTTP 403.
 *
 * <p>Per SPEC §6.2: no password in the message; the audit row's
 * {@code details.error} is the only place the failure category is
 * recorded.
 */
public class EngineAuthException extends RuntimeException {
    private final String engineCode;

    public EngineAuthException(String engineCode) {
        super("SSH authentication failed for engine '" + engineCode + "'");
        this.engineCode = engineCode;
    }

    public String getEngineCode() {
        return engineCode;
    }
}
