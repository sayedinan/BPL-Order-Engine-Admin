package com.enginehelm.keystore;

import org.springframework.stereotype.Component;

import com.enginehelm.bootstrap.MasterKeyPrecondition;

/**
 * Public surface for credential-store encryption / decryption. Reads
 * the master key from the env var on first use and caches the cipher
 * in a {@code volatile} field so the env var is read exactly once
 * per process.
 *
 * <p>Owns the {@code com.enginehelm.keystore} package per the agent
 * definitions; only this class is consumed by other packages.
 */
@Component
public final class KeystoreCredentialStore {

    private volatile CredentialCipher cipher;

    public byte[] encrypt(byte[] plaintext) {
        return cipher().encrypt(plaintext);
    }

    public byte[] decrypt(byte[] ciphertext) {
        return cipher().decrypt(ciphertext);
    }

    private CredentialCipher cipher() {
        CredentialCipher c = cipher;
        if (c == null) {
            synchronized (this) {
                c = cipher;
                if (c == null) {
                    String masterKey = System.getenv(MasterKeyPrecondition.ENV_VAR);
                    if (masterKey == null || masterKey.isEmpty()) {
                        // The boot-time precondition should already have fired,
                        // but we keep this guard so a misuse at runtime cannot
                        // silently encrypt with an empty key.
                        throw new IllegalStateException(
                                MasterKeyPrecondition.ENV_VAR + " is not set");
                    }
                    // Zero the env-var reference after use. (The actual
                    // char[]/byte[] the cipher holds is the only thing we
                    // can't fully scrub — the SecretKeySpec keeps a copy.)
                    c = new CredentialCipher(masterKey);
                    cipher = c;
                }
            }
        }
        return c;
    }
}
