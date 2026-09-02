package com.BPL_Order_Engine_Admin.manager.audit;

import java.util.Map;

/**
 * Marker interface for exceptions that want to contribute their own
 * {@code details} map to the {@link AuditAspect} failure-path audit row.
 *
 * <p>The aspect writes {@code details.error = <exception class>} and
 * {@code details.message = <truncated getMessage()>} on every
 * failure. If the thrown exception implements this interface, the
 * map returned by {@link #auditDetails()} is merged in BEFORE those
 * two fields are added, so the exception can stamp structured
 * reason codes (e.g. {@code { "reason": "BAD_CURRENT_PASSWORD" }})
 * without bypassing the aspect.
 *
 * <p>This is the single hook that keeps the aspect as the sole
 * writer of audit rows (SPEC §7.4 anti-pattern: no inline
 * {@code auditLogRepository.save(...)} in controllers / services).
 */
public interface AuditableFailure {
    /**
     * @return a mutable map of structured fields to merge into the
     *         {@code details} JSON. The aspect adds {@code error} and
     *         {@code message} after this map is applied, so keys with
     *         those names are silently overwritten.
     */
    Map<String, Object> auditDetails();
}
