package com.enginehelm.credential;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.enginehelm.keystore.KeystoreCredentialStore;

/**
 * Sys.admin-only credential store surface. Delegates the actual
 * ciphertext read / write to {@link KeystoreCredentialStore}; this
 * class does not touch the encrypted bytes directly.
 */
@Service
public class CredentialService {

    private final CredentialRepository credentials;
    private final KeystoreCredentialStore keystore;

    public CredentialService(CredentialRepository credentials,
                             KeystoreCredentialStore keystore) {
        this.credentials = credentials;
        this.keystore = keystore;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('SYS_ADMIN')")
    public List<Credential> list() {
        return credentials.findAll();
    }

    @Transactional
    @PreAuthorize("hasRole('SYS_ADMIN')")
    public Credential addSshKey(String alias, String fingerprint, byte[] privateKeyPlaintext) {
        Credential c = new Credential();
        c.setAlias(alias);
        c.setType(CredentialType.ssh_key);
        c.setFingerprint(fingerprint);
        c.setPrivateKeyCiphertext(keystore.encrypt(privateKeyPlaintext));
        return credentials.save(c);
    }

    @Transactional
    @PreAuthorize("hasRole('SYS_ADMIN')")
    public void delete(Long id) {
        credentials.deleteById(id);
    }

    /** Used by the seed runner and the future SSH layer. */
    public byte[] decryptPrivateKey(Credential c) {
        if (c.getType() != CredentialType.ssh_key) {
            throw new IllegalStateException("not an ssh_key credential: " + c.getAlias());
        }
        return keystore.decrypt(c.getPrivateKeyCiphertext());
    }
}
