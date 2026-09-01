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
 * <p>This runs in the {@code prod} profile only — the dev profile
 * supplies defaults via {@code application-dev.properties}, and
 * failing in dev would block {@code run.bat} on a fresh checkout.
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
        if (!isProdProfile(environment)) {
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

    private boolean isProdProfile(ConfigurableEnvironment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile.trim())) {
                return true;
            }
        }
        return false;
    }
}
