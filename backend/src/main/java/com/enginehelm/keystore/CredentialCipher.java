package com.enginehelm.keystore;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-GCM encryption for credential store rows.
 *
 * <p>V1 choice: a 256-bit key derived from {@code ENGINE_HELM_MASTER_KEY}
 * via SHA-256 (no PBKDF2 — V1 simplicity, per the bootstrap plan). Each
 * encryption uses a fresh 12-byte IV, which is prepended to the ciphertext
 * so {@link #decrypt(byte[])} can recover it.
 *
 * <p>Output format: {@code [12-byte IV][ciphertext+16-byte GCM tag]}.
 */
public final class CredentialCipher {

    private static final String CIPHER_ALG = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LEN_BYTES = 12;

    private final SecretKey key;
    private final SecureRandom rng = new SecureRandom();

    public CredentialCipher(String masterKey) {
        if (masterKey == null || masterKey.isEmpty()) {
            throw new IllegalStateException("master key is empty");
        }
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] derived = sha.digest(masterKey.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(derived, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public byte[] encrypt(byte[] plaintext) {
        try {
            byte[] iv = new byte[IV_LEN_BYTES];
            rng.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALG);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext);

            byte[] out = new byte[IV_LEN_BYTES + ct.length];
            System.arraycopy(iv, 0, out, 0, IV_LEN_BYTES);
            System.arraycopy(ct, 0, out, IV_LEN_BYTES, ct.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("encryption failed", e);
        }
    }

    public byte[] decrypt(byte[] envelope) {
        if (envelope == null || envelope.length < IV_LEN_BYTES + 16) {
            throw new IllegalArgumentException("ciphertext envelope too short");
        }
        try {
            byte[] iv = new byte[IV_LEN_BYTES];
            System.arraycopy(envelope, 0, iv, 0, IV_LEN_BYTES);

            byte[] ct = new byte[envelope.length - IV_LEN_BYTES];
            System.arraycopy(envelope, IV_LEN_BYTES, ct, 0, ct.length);

            Cipher cipher = Cipher.getInstance(CIPHER_ALG);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(ct);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("decryption failed", e);
        }
    }
}
