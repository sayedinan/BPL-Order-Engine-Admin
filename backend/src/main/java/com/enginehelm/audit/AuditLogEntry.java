package com.enginehelm.audit;

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
@Table(name = "audit_log")
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp = Instant.now();

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_username_snapshot", nullable = false, length = 255)
    private String actorUsernameSnapshot;

    @Column(name = "actor_system_role_snapshot", nullable = false, length = 16)
    private String actorSystemRoleSnapshot;

    @Column(name = "actor_group_set_snapshot", length = 2048)
    private String actorGroupSetSnapshot;

    @Column(name = "engine_id")
    private Long engineId;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 16)
    private AuditLogCategory category;

    @Lob
    @Column(name = "script_text_snapshot")
    private String scriptTextSnapshot;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "stdout_excerpt", length = 4096)
    private String stdoutExcerpt;

    @Column(name = "details", length = 2048)
    private String details;

    public Long getId() { return id; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
    public String getActorUsernameSnapshot() { return actorUsernameSnapshot; }
    public void setActorUsernameSnapshot(String actorUsernameSnapshot) {
        this.actorUsernameSnapshot = actorUsernameSnapshot;
    }
    public String getActorSystemRoleSnapshot() { return actorSystemRoleSnapshot; }
    public void setActorSystemRoleSnapshot(String actorSystemRoleSnapshot) {
        this.actorSystemRoleSnapshot = actorSystemRoleSnapshot;
    }
    public String getActorGroupSetSnapshot() { return actorGroupSetSnapshot; }
    public void setActorGroupSetSnapshot(String actorGroupSetSnapshot) {
        this.actorGroupSetSnapshot = actorGroupSetSnapshot;
    }
    public Long getEngineId() { return engineId; }
    public void setEngineId(Long engineId) { this.engineId = engineId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public AuditLogCategory getCategory() { return category; }
    public void setCategory(AuditLogCategory category) { this.category = category; }
    public String getScriptTextSnapshot() { return scriptTextSnapshot; }
    public void setScriptTextSnapshot(String scriptTextSnapshot) {
        this.scriptTextSnapshot = scriptTextSnapshot;
    }
    public Integer getExitCode() { return exitCode; }
    public void setExitCode(Integer exitCode) { this.exitCode = exitCode; }
    public String getStdoutExcerpt() { return stdoutExcerpt; }
    public void setStdoutExcerpt(String stdoutExcerpt) { this.stdoutExcerpt = stdoutExcerpt; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
