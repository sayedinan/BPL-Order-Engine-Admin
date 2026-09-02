package com.BPL_Order_Engine_Admin.manager.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.ArrayList;
import java.util.List;

/**
 * Fail-fast check for required environment variables in production.
 *
 * <p>The SPEC §8.1 list of required prod vars includes
 * {@code JWT_SECRET}, {@code JASYPT_ENCRYPTOR_PASSWORD},
 * {@code DB_URL}, {@code DB_USERNAME}, {@code DB_PASSWORD}, and
 * {@code CORS_ALLOWED_ORIGINS}. If any of these is missing or
 * blank, the app should refuse to start with one clear error
 * message naming every missing variable, rather than crashing
 * later with a confusing stack trace.
 *
 * <p>Fires when ANY of the following is true:
 * <ul>
 *   <li>The {@code prod} profile is in the active profile list (the
 *       normal path: {@code SPRING_PROFILES_ACTIVE=prod} via the
 *       prod compose file).</li>
 *   <li>No profile is active AND at least one of the required
 *       prod vars is set. This catches the mistake of deploying
 *       the prod image without {@code SPRING_PROFILES_ACTIVE=prod}
 *       — a half-configured env is almost always a real prod boot
 *       that forgot the profile flag, not a dev boot.</li>
 * </ul>
 *
 * <p>A pure dev boot (no profile, none of the prod vars set) is
 * never blocked — that's the {@code run.bat} path.
 *
 * <p>Auto-detected via
 * {@code META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports}
 * (Spring Boot 3+).
 */
public class RequiredEnvValidator implements EnvironmentPostProcessor {

    private static final List<String> REQUIRED_VARS = List.of(
        "JWT_SECRET",
        "JASYPT_ENCRYPTOR_PASSWORD",
        "DB_URL",
        "DB_USERNAME",
        "DB_PASSWORD",
        "CORS_ALLOWED_ORIGINS"
    );

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!shouldEnforce(environment)) {
            return;
        }
        List<String> missing = new ArrayList<>();
        for (String name : REQUIRED_VARS) {
            String value = environment.getProperty(name);
            if (value == null || value.isBlank()) {
                missing.add(name);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                "Required environment variables missing or blank: "
                    + String.join(", ", missing)
                    + ". See RUNBOOK.md and dev-secrets.template for the full list."
            );
        }
    }

    /**
     * Enforce iff the operator is in a prod-shaped boot:
     *   - the prod profile is active, OR
     *   - no profile is active AND at least one required prod var
     *     is set (catches "deployed prod image but forgot
     *     SPRING_PROFILES_ACTIVE=prod").
     *
     * Pure dev boots (no profile, no prod vars) skip enforcement.
     */
    private boolean shouldEnforce(ConfigurableEnvironment environment) {
        if (hasProdProfile(environment)) {
            return true;
        }
        if (environment.getActiveProfiles().length > 0) {
            // Some other profile is active (e.g. "dev"). Dev profile
            // supplies its own defaults; the operator opted into
            // dev. Don't second-guess them.
            return false;
        }
        // No profile active — treat as prod if it looks prod-shaped.
        for (String name : REQUIRED_VARS) {
            String value = environment.getProperty(name);
            if (value != null && !value.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasProdProfile(ConfigurableEnvironment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile.trim())) {
                return true;
            }
        }
        return false;
    }
}
