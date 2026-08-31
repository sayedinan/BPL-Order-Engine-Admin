package com.enginehelm.host;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "hosts")
public class Host {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alias", nullable = false, unique = true, length = 255)
    private String alias;

    @Column(name = "hostname_or_ip", nullable = false, length = 255)
    private String hostnameOrIp;

    @Column(name = "port", nullable = false)
    private int port = 22;

    @Column(name = "ssh_username", nullable = false, length = 255)
    private String sshUsername;

    @Column(name = "host_key_fingerprint", nullable = false, length = 255)
    private String hostKeyFingerprint;

    @Column(name = "default_credential_id", nullable = false)
    private Long defaultCredentialId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }
    public String getHostnameOrIp() { return hostnameOrIp; }
    public void setHostnameOrIp(String hostnameOrIp) { this.hostnameOrIp = hostnameOrIp; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getSshUsername() { return sshUsername; }
    public void setSshUsername(String sshUsername) { this.sshUsername = sshUsername; }
    public String getHostKeyFingerprint() { return hostKeyFingerprint; }
    public void setHostKeyFingerprint(String hostKeyFingerprint) {
        this.hostKeyFingerprint = hostKeyFingerprint;
    }
    public Long getDefaultCredentialId() { return defaultCredentialId; }
    public void setDefaultCredentialId(Long defaultCredentialId) {
        this.defaultCredentialId = defaultCredentialId;
    }
}
