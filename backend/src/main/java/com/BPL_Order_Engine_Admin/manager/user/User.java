package com.BPL_Order_Engine_Admin.manager.user;

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
 * v0.3 User entity. See SPEC §3.2 and jpa-entity-patterns.
 *
 * <p>UUID PK, {@code @Version} for optimistic locking, no {@code @Data}
 * (per-entity accessors are explicit). The {@code assignedEngines} side
 * is {@code LAZY}; the {@code Engine.assignedUsers} side is the
 * inverse. Always access from the {@code User} side.
 *
 * <p>Plain fields, no {@code @Data} — Lombok would generate
 * {@code equals}/{@code hashCode} that include the to-many collection
 * and trigger a fetch on every equality check. The class-level
 * {@code @Getter}/{@code @Setter} generate accessors for the simple
 * fields; the password hash uses package-private accessors so the
 * hash never leaves the service layer.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "username", nullable = false, length = 64, unique = true)
    private String username;

    /** BCrypt hash. Lombok's class-level @Getter/@Setter generate
     *  the accessors; the security boundary is the UserSummary DTO,
     *  which never carries the hash. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_type", nullable = false, length = 16)
    private RoleType roleType;

    /** Force-change-password flag. Default true on create. */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "user_engine_access",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "engine_id")
    )
    private Set<com.BPL_Order_Engine_Admin.manager.engine.EngineEntity> assignedEngines = new HashSet<>();

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
