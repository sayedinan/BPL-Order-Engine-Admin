-- V1__init.sql — v0.3 schema
-- Tables: users, engines, audit_log, user_engine_access
-- Per SPEC §3.2 / §3.3 / §3.4 / §3.7 / jpa-entity-patterns / API.md

-- ---- users ----
CREATE TABLE users (
    id                   UUID         PRIMARY KEY,
    version              BIGINT       NOT NULL DEFAULT 0,
    username             VARCHAR(64)  NOT NULL UNIQUE,
    password_hash        VARCHAR(100) NOT NULL,
    role_type            VARCHAR(16)  NOT NULL CHECK (role_type IN ('SYS_ADMIN', 'ADMIN', 'USER')),
    must_change_password BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL
);

-- Case-insensitive username lookup at auth time.
CREATE INDEX idx_users_username_lower ON users (LOWER(username));

-- ---- engines ----
CREATE TABLE engines (
    id                UUID         PRIMARY KEY,
    version           BIGINT       NOT NULL DEFAULT 0,
    code              VARCHAR(16)  NOT NULL,
    name              VARCHAR(80)  NOT NULL,
    server_ip         VARCHAR(64)  NOT NULL,
    server_username   VARCHAR(64)  NOT NULL,
    server_password   VARCHAR(512) NOT NULL, -- Jasypt-encrypted ciphertext
    mode              VARCHAR(16)  NOT NULL CHECK (mode IN ('MOCK', 'REAL')),
    start_script      VARCHAR(1024),
    stop_script       VARCHAR(1024),
    log_script        VARCHAR(1024),
    status            VARCHAR(16)  NOT NULL DEFAULT 'STOPPED' CHECK (status IN ('RUNNING', 'STOPPED', 'ERROR')),
    last_transition_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    deleted_at        TIMESTAMPTZ
);

-- Code is unique among non-deleted rows (the factory filters by
-- findByCodeAndDeletedAtIsNull). A partial unique index gives that
-- behavior without a CHECK clause.
CREATE UNIQUE INDEX uq_engines_code_active
    ON engines (code) WHERE deleted_at IS NULL;

-- ---- user_engine_access (many-to-many join) ----
CREATE TABLE user_engine_access (
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    engine_id  UUID NOT NULL REFERENCES engines (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, engine_id)
);

CREATE INDEX idx_user_engine_access_engine ON user_engine_access (engine_id);

-- ---- audit_log (insert-only, no @Version) ----
CREATE TABLE audit_log (
    id                  UUID         PRIMARY KEY,
    timestamp           TIMESTAMPTZ  NOT NULL,
    actor_username      VARCHAR(64)  NOT NULL,
    actor_role          VARCHAR(16)  NOT NULL,
    action              VARCHAR(32)  NOT NULL,
    target_engine_code  VARCHAR(16),
    details             JSONB        NOT NULL DEFAULT '{}'::jsonb
);

-- Per SPEC §3.4 indexes.
CREATE INDEX idx_audit_log_timestamp         ON audit_log (timestamp);
CREATE INDEX idx_audit_log_actor_username    ON audit_log (actor_username);
CREATE INDEX idx_audit_log_target_engine_code ON audit_log (target_engine_code);

-- `details` capped at 2KB by the application layer; we keep the column
-- unbounded at the DB level so the app can emit a "details truncated"
-- WARN log line and write the row anyway.
