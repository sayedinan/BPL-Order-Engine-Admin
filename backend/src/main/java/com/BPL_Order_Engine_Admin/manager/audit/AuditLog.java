package com.BPL_Order_Engine_Admin.manager.audit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * v0.3 Audit log row (SPEC §3.4). Insert-only — no {@code @Version},
 * no setters called after the row is persisted, no public update.
 *
 * <p>{@code details} is a {@code jsonb} column backed by a Hibernate
 * {@code SqlTypes.JSON} mapping. We store as a {@code String} (raw
 * JSON) so the application can serialize any shape — the API converts
 * to a {@code Map} for the response.
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "timestamp", nullable = false, updatable = false)
    private Instant timestamp;

    @Column(name = "actor_username", nullable = false, length = 64, updatable = false)
    private String actorUsername;

    @Column(name = "actor_role", nullable = false, length = 16, updatable = false)
    private String actorRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 32, updatable = false)
    private AuditAction action;

    @Column(name = "target_engine_code", length = 16, updatable = false)
    private String targetEngineCode;

    /**
     * JSONB. Stored as a string; the application serializes the
     * details object before persist and deserializes on read. No
     * setters called after persist (the row is immutable in
     * practice).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", nullable = false, columnDefinition = "jsonb", updatable = false)
    private String details;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        if (details == null) {
            details = "{}";
        }
    }
}
