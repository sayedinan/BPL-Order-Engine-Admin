package com.enginehelm.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Hard boot-time precondition: the app fails to start if
 * {@code ENGINE_HELM_MASTER_KEY} is unset. There is no silent
 * fallback to a default or empty key (SPEC §8 / Q6).
 *
 * <p>Implemented as an {@link EnvironmentPostProcessor} registered
 * via {@code META-INF/spring.factories} so the check runs before
 * the Spring context is built. We cannot log the value (never log
 * the master key), only its presence.
 */
public final class MasterKeyPrecondition implements EnvironmentPostProcessor {

    public static final String ENV_VAR = "ENGINE_HELM_MASTER_KEY";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {
        String masterKey = environment.getProperty(ENV_VAR);
        if (masterKey == null || masterKey.isEmpty()) {
            throw new IllegalStateException(
                    "ENGINE_HELM_MASTER_KEY is not set. The application cannot "
                            + "start without a credential-store master key. "
                            + "Set the env var and restart. (SPEC §8 / Q6.)");
        }
        // Sanity: never log the value, only the length.
        // (Security-reviewer audit target.)
        application.getListeners().forEach(listener -> { /* no-op */ });
    }
}
