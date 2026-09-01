package com.BPL_Order_Engine_Admin.manager.engine;

/**
 * Thrown by {@code SshBackedEngine} when the SSH connect fails
 * (after the one retry). Maps to HTTP 502.
 */
public class EngineUnreachableException extends RuntimeException {
    private final String engineCode;

    public EngineUnreachableException(String engineCode, Throwable cause) {
        super("Engine '" + engineCode + "' is unreachable", cause);
        this.engineCode = engineCode;
    }

    public String getEngineCode() {
        return engineCode;
    }
}
