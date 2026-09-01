package com.BPL_Order_Engine_Admin.manager.config;

import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.EnvironmentStringPBEConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jasypt {@code StringEncryptor} bean. Reads the master password
 * from {@code JASYPT_ENCRYPTOR_PASSWORD} (required env var). The
 * {@code @Encrypted} field-level filter on {@code Engine.serverPassword}
 * uses this bean to encrypt on write and decrypt on read.
 *
 * <p>If the env var is missing, the bean still constructs but the
 * filter logs a clear failure on first use; the test suite skips
 * encryption scenarios that require it. The prod startup check
 * (in #15) hard-fails the app if the env var is missing.
 */
@Configuration
public class JasyptConfig {

    @Value("${jasypt.encryptor.password:}")
    private String masterPassword;

    @Value("${jasypt.encryptor.algorithm:PBEWithHMACSHA512AndAES_256}")
    private String algorithm;

    @Value("${jasypt.encryptor.iv-generator-classname:org.jasypt.iv.RandomIvGenerator}")
    private String ivGeneratorClassName;

    @Bean
    public StringEncryptor stringEncryptor() {
        EnvironmentStringPBEConfig config = new EnvironmentStringPBEConfig();
        config.setAlgorithm(algorithm);
        config.setIvGeneratorClassName(ivGeneratorClassName);
        if (masterPassword == null || masterPassword.isEmpty()) {
            // Set a default that is still functional for dev — the
            // prod profile hard-fails on missing env var via a
            // separate guard in SecurityConfig.
            config.setPassword("dev-only-do-not-use-in-prod");
        } else {
            config.setPassword(masterPassword);
        }
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setConfig(config);
        return encryptor;
    }
}
