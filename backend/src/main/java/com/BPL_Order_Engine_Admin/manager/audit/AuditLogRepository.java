package com.BPL_Order_Engine_Admin.manager.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /**
     * The filter dimensions match API.md §4.1. Null parameters are
     * ignored (Spring Data JPA "is null" semantics). The
     * {@code AuditService} (in #21) adds the engine code → UUID
     * resolution before this query.
     */
    Page<AuditLog> findByActorUsernameAndTimestampBetween(
        String actorUsername, Instant from, Instant to, Pageable pageable);

    Page<AuditLog> findByTimestampBetween(
        Instant from, Instant to, Pageable pageable);

    Page<AuditLog> findByActorUsernameAndActionAndTimestampBetween(
        String actorUsername, AuditAction action, Instant from, Instant to, Pageable pageable);

    Page<AuditLog> findByActionAndTimestampBetween(
        AuditAction action, Instant from, Instant to, Pageable pageable);
}
