package com.BPL_Order_Engine_Admin.manager.engine;

/**
 * Thrown by {@code SshBackedEngine} on non-zero script exit.
 * Maps to HTTP 502 with {@code details: { engineCode, exitCode, stderr }}.
 *
 * <p>The stderr is truncated to 2KB by {@code ApiExceptionHandler}.
 */
public class EngineScriptException extends RuntimeException {
    private final String engineCode;
    private final int exitCode;
    private final String stderr;

    public EngineScriptException(String engineCode, int exitCode, String stderr) {
        super("Script exited with code " + exitCode + " for engine '" + engineCode + "'");
        this.engineCode = engineCode;
        this.exitCode = exitCode;
        this.stderr = stderr;
    }

    public String getEngineCode() {
        return engineCode;
    }

    public int getExitCode() {
        return exitCode;
    }

    public String getStderr() {
        return stderr;
    }
}
