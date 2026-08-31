package com.enginehelm.engine;

/**
 * Thrown by {@link EngineConfigService#create} when the {@code bash -n}
 * check fails on at least one script. Carries the full
 * {@link BashValidationResult} so the controller can return a useful
 * 400 body to the UI.
 */
public class BashSyntaxException extends RuntimeException {
    private final BashValidationResult result;

    public BashSyntaxException(BashValidationResult result) {
        super("bash -n failed for one or more scripts");
        this.result = result;
    }

    public BashValidationResult getResult() {
        return result;
    }
}
