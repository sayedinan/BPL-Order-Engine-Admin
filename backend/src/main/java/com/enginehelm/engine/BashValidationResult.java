package com.enginehelm.engine;

import java.util.List;
import java.util.Map;

/**
 * Result of a {@link BashSafetyScanner#scan} call.
 *
 * @param perScript        per-script {@code bash -n} outcome. The map
 *                         keys are the four script slots
 *                         ({@code start}, {@code stop}, {@code status},
 *                         {@code log}); the values are the exit code
 *                         and stderr (may be empty).
 * @param advisoryMatches  non-blocking advisory pattern matches, one
 *                         per line. The UI surfaces these in the
 *                         confirmation modal; they do not block the
 *                         save.
 */
public record BashValidationResult(
        Map<String, ScriptCheck> perScript,
        List<AdvisoryMatch> advisoryMatches) {

    public record ScriptCheck(int exitCode, String stderr) {}

    public record AdvisoryMatch(String script, String pattern, int line) {}

    public boolean hasBlockingFailure() {
        return perScript.values().stream().anyMatch(c -> c.exitCode() != 0);
    }
}
