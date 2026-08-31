package com.enginehelm.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class BashSafetyScannerTest {

    private final BashSafetyScanner scanner = new BashSafetyScanner();

    @Test
    void cleanScriptPassesBashDashN() {
        Map<String, String> scripts = slotMap(
                "echo hello",
                "systemctl stop myapp",
                "systemctl is-active myapp",
                "tail -n 100 /var/log/myapp.log");

        BashValidationResult r = scanner.scan(scripts);

        assertThat(r.hasBlockingFailure()).isFalse();
        for (String slot : BashSafetyScanner.SLOTS) {
            assertThat(r.perScript().get(slot).exitCode())
                    .as("%s exit code", slot)
                    .isEqualTo(0);
        }
        assertThat(r.advisoryMatches()).isEmpty();
    }

    @Test
    void syntaxErrorBlocksSave() {
        Map<String, String> scripts = slotMap(
                "echo hi",
                "if then fi",         // missing `then` — bash syntax error
                "echo ok",
                "echo logs");

        BashValidationResult r = scanner.scan(scripts);

        assertThat(r.hasBlockingFailure()).isTrue();
        BashValidationResult.ScriptCheck stop = r.perScript().get("stop");
        assertThat(stop.exitCode()).isNotEqualTo(0);
        assertThat(stop.stderr()).containsIgnoringCase("syntax error");
    }

    @Test
    void advisoryMatchDoesNotBlock() {
        Map<String, String> scripts = slotMap(
                "rm -rf /",            // matches the `rm -rf /` advisory
                "echo ok",
                "echo ok",
                "echo ok");

        BashValidationResult r = scanner.scan(scripts);

        assertThat(r.hasBlockingFailure()).isFalse();
        assertThat(r.advisoryMatches())
                .extracting(BashValidationResult.AdvisoryMatch::script)
                .contains("start");
    }

    @Test
    void emptyScriptPassesBashDashN() {
        Map<String, String> scripts = slotMap("", "", "", "");
        BashValidationResult r = scanner.scan(scripts);
        assertThat(r.hasBlockingFailure()).isFalse();
        for (String slot : BashSafetyScanner.SLOTS) {
            assertThat(r.perScript().get(slot).exitCode()).isEqualTo(0);
        }
    }

    private static Map<String, String> slotMap(String start, String stop, String status, String log) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("start", start);
        m.put("stop", stop);
        m.put("status", status);
        m.put("log", log);
        return m;
    }
}
