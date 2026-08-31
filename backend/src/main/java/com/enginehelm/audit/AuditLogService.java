package com.enginehelm.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.enginehelm.user.User;

/**
 * The single writer to {@code audit_log}. No other code writes to
 * that table directly (per the audit-log-ui agent boundary). The
 * view side (filtering, admin-vs-sys.admin split) is owned by
 * {@code audit-log-ui} and not exposed by this service.
 */
@Service
public class AuditLogService {

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AuditLogEntry record(User actor,
                                String actorUsernameSnapshot,
                                String actorSystemRoleSnapshot,
                                String actorGroupSetSnapshot,
                                Long engineId,
                                String action,
                                AuditLogCategory category,
                                String scriptTextSnapshot,
                                Integer exitCode,
                                String stdoutExcerpt,
                                String details) {
        AuditLogEntry e = new AuditLogEntry();
        if (actor != null) {
            e.setActorUserId(actor.getId());
        }
        e.setActorUsernameSnapshot(actorUsernameSnapshot);
        e.setActorSystemRoleSnapshot(actorSystemRoleSnapshot);
        e.setActorGroupSetSnapshot(actorGroupSetSnapshot);
        e.setEngineId(engineId);
        e.setAction(action);
        e.setCategory(category);
        e.setScriptTextSnapshot(scriptTextSnapshot);
        e.setExitCode(exitCode);
        e.setStdoutExcerpt(stdoutExcerpt);
        e.setDetails(details);
        return repository.save(e);
    }
}
