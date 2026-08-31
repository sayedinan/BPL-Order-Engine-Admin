package com.enginehelm.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Local lint of a script body. Runs {@code bash -n} per script via
 * {@link ProcessBuilder} and scans for advisory patterns.
 *
 * <p><b>What this does</b>: a {@code bash -n} syntax check and a
 * non-blocking regex scan. <b>What this does not do</b>: it does not
 * execute the script, and it does not touch the network. The
 * per-action SSH path (which runs the script for real) is
 * {@code ssh-execution-service}'s territory.
 *
 * <p>Per SPEC §7.4 and Q3: the {@code bash -n} result is the only
 * hard block; the advisory list is informational.
 */
@Component
public class BashSafetyScanner {

    /** Script slots. Order is preserved in the result map. */
    public static final List<String> SLOTS = List.of("start", "stop", "status", "log");

    /** Bounded {@code bash -n} runtime, per script. */
    private static final long BASH_TIMEOUT_SECONDS = 5;

    /**
     * Advisory patterns. Each entry is a regex matched against the
     * full script body. Non-blocking per Q3 / SPEC §7.4.
     */
    private static final List<AdvisoryPattern> ADVISORIES = List.of(
            new AdvisoryPattern("rm -rf /", Pattern.compile("\\brm\\s+-rf\\s+/(?:\\s|;|$)")),
            new AdvisoryPattern("curl ... | bash",
                    Pattern.compile("\\bcurl\\b[^|\\n]*\\|\\s*\\bbash\\b")),
            new AdvisoryPattern("wget ... | bash",
                    Pattern.compile("\\bwget\\b[^|\\n]*\\|\\s*\\bbash\\b")),
            new AdvisoryPattern("fork bomb (:(){ :|:& };:)",
                    Pattern.compile(":\\(\\)\\s*\\{\\s*:\\|:&\\s*\\}\\s*;\\s*:"))
    );

    public BashValidationResult scan(Map<String, String> scripts) {
        Map<String, BashValidationResult.ScriptCheck> perScript = new LinkedHashMap<>();
        for (String slot : SLOTS) {
            String body = scripts.getOrDefault(slot, "");
            perScript.put(slot, runBashDashN(body));
        }
        List<BashValidationResult.AdvisoryMatch> advisories = scanAdvisories(scripts);
        return new BashValidationResult(perScript, advisories);
    }

    private BashValidationResult.ScriptCheck runBashDashN(String body) {
        // `bash -n -` reads the script from stdin. This avoids any
        // temp-file path (no filesystem write, no risk of leaving
        // script text on disk for the security reviewer to flag).
        ProcessBuilder pb = new ProcessBuilder("bash", "-n", "-");
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            try (var out = p.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
            StringBuilder output = new StringBuilder();
            try (var reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            boolean finished = p.waitFor(BASH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return new BashValidationResult.ScriptCheck(
                        -1, "bash -n timed out after " + BASH_TIMEOUT_SECONDS + "s");
            }
            int code = p.exitValue();
            return new BashValidationResult.ScriptCheck(code, output.toString());
        } catch (IOException e) {
            return new BashValidationResult.ScriptCheck(
                    -1, "failed to launch bash: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new BashValidationResult.ScriptCheck(
                    -1, "interrupted while waiting for bash");
        }
    }

    private List<BashValidationResult.AdvisoryMatch> scanAdvisories(Map<String, String> scripts) {
        List<BashValidationResult.AdvisoryMatch> matches = new ArrayList<>();
        for (String slot : SLOTS) {
            String body = scripts.getOrDefault(slot, "");
            String[] lines = body.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                for (AdvisoryPattern adv : ADVISORIES) {
                    Matcher m = adv.pattern.matcher(line);
                    if (m.find()) {
                        matches.add(new BashValidationResult.AdvisoryMatch(
                                slot, adv.label, i + 1));
                    }
                }
            }
        }
        return matches;
    }

    private record AdvisoryPattern(String label, Pattern pattern) {}
}
