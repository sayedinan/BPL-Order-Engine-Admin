package com.BPL_Order_Engine_Admin.manager.engine;

/**
 * Thrown by {@link OrderEngineFactory#get(String)} when no implementation
 * is registered for the requested engine id. Mapped to HTTP 404 by
 * {@code ApiExceptionHandler}.
 */
public class EngineNotSupportedException extends RuntimeException {

    private final String engineId;

    public EngineNotSupportedException(String engineId) {
        super("Engine '" + engineId + "' is not supported yet");
        this.engineId = engineId;
    }

    public String getEngineId() {
        return engineId;
    }
}
