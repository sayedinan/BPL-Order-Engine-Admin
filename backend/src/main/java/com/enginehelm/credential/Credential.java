package com.enginehelm.credential;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "credentials")
public class Credential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alias", nullable = false, unique = true, length = 255)
    private String alias;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private CredentialType type;

    @Column(name = "fingerprint", nullable = false, length = 255)
    private String fingerprint;

    @Lob
    @Column(name = "private_key_ciphertext")
    private byte[] privateKeyCiphertext;

    @Lob
    @Column(name = "password_ciphertext")
    private byte[] passwordCiphertext;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }
    public CredentialType getType() { return type; }
    public void setType(CredentialType type) { this.type = type; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    public byte[] getPrivateKeyCiphertext() { return privateKeyCiphertext; }
    public void setPrivateKeyCiphertext(byte[] privateKeyCiphertext) {
        this.privateKeyCiphertext = privateKeyCiphertext;
    }
    public byte[] getPasswordCiphertext() { return passwordCiphertext; }
    public void setPasswordCiphertext(byte[] passwordCiphertext) {
        this.passwordCiphertext = passwordCiphertext;
    }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
