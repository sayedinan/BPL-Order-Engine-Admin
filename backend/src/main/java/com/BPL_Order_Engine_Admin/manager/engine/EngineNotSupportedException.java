package com.BPL_Order_Engine_Admin.manager.engine;

/**
 * Thrown by the {@code OrderEngineFactory} when the requested engine
 * code is not in the {@code engines} table (or has been soft-deleted).
 * Maps to HTTP 404 via {@code ApiExceptionHandler}.
 */
public class EngineNotSupportedException extends RuntimeException {
    private final String engineCode;

    public EngineNotSupportedException(String engineCode) {
        super("Engine '" + engineCode + "' is not supported");
        this.engineCode = engineCode;
    }

    public String getEngineCode() {
        return engineCode;
    }
}
