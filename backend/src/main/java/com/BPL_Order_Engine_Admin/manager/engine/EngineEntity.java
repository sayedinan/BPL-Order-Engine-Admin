package com.BPL_Order_Engine_Admin.manager.engine;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * v0.3 Engine entity. See SPEC §3.3 and jpa-entity-patterns.
 *
 * <p>Note: the file is named {@code EngineEntity.java} to avoid
 * clashing with the {@code engine} package and the
 * {@code Engine.assignedUsers} inverse side. The class is referenced
 * by User.assignedEngines.
 *
 * <p>UUID PK, {@code @Version}, no {@code @Data}. {@code serverPassword}
 * is Jasypt-encrypted at rest (the Jasypt {@code @Encrypted} filter
 * is configured in #15 via the {@code StringEncryptor} bean).
 *
 * <p>{@code assignedUsers} is the inverse side of
 * {@code User.assignedEngines}; always access from the User side to
 * keep the fetch behavior predictable.
 */
@Entity
@Table(name = "engines")
@Getter
@Setter
@NoArgsConstructor
public class EngineEntity {

    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Display name, e.g. "BPL Order Engine". */
    @Column(name = "name", nullable = false, length = 80)
    private String name;

    /** Engine id used in URLs (e.g. "BPL"). {@code ^[A-Z0-9_]{2,16}$}. */
    @Column(name = "code", nullable = false, length = 16)
    private String code;

    /** IPv4 or RFC 1123 hostname. */
    @Column(name = "server_ip", nullable = false, length = 64)
    private String serverIp;

    @Column(name = "server_username", nullable = false, length = 64)
    private String serverUsername;

    /**
     * Jasypt-encrypted ciphertext. The Jasypt {@code @Encrypted}
     * field-level filter handles the encrypt/decrypt on read/write
     * (configured via {@code jasypt.encryptor.*} in application
     * properties). Plaintext only enters via the request body and
     * the in-memory SSH session.
     *
     * <p>The security boundary is the {@code EngineResponse} DTO,
     * which never carries this field.
     */
    @Column(name = "server_password", nullable = false, length = 512)
    private String serverPassword;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 16)
    private EngineMode mode;

    @Column(name = "start_script", length = 1024)
    private String startScript;

    @Column(name = "stop_script", length = 1024)
    private String stopScript;

    @Column(name = "log_script", length = 1024)
    private String logScript;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private EngineStatus status = EngineStatus.STOPPED;

    @Column(name = "last_transition_at")
    private Instant lastTransitionAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Soft delete marker. The factory filters by {@code deletedAt IS NULL}. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ManyToMany(mappedBy = "assignedEngines", fetch = FetchType.LAZY)
    private Set<com.BPL_Order_Engine_Admin.manager.user.User> assignedUsers = new HashSet<>();

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
