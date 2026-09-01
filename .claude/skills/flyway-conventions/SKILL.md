---
name: flyway-conventions
description: v0.3 — Flyway migration layout, naming, the dev-profile seed split, and the schema-as-source-of-truth rule. Replaces any use of spring.jpa.hibernate.ddl-auto.
---

# Flyway conventions (v0.3)

v0.3 introduces persistent storage via PostgreSQL + Flyway. Flyway is
the source of truth for the schema. `hibernate.ddl-auto` is
`validate` in every profile (it confirms the JPA entities match the
DB; it never changes the DB).

## Directory layout

```
src/main/resources/
├── db/
│   ├── migration/                # production schema
│   │   ├── V1__init.sql          # users, engines, audit_log, user_engine_access
│   │   ├── V2__add_must_change_password.sql
│   │   └── ...
│   └── seed/                     # dev profile only
│       └── V2__seed_admin.sql    # one SYS_ADMIN with a known BCrypt password
```

`application.properties`:
```properties
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
```

`application-dev.properties`:
```properties
spring.flyway.locations=classpath:db/migration,classpath:db/seed
```

In dev, both directories are scanned in order. The seed file's version number must be greater than the schema version it seeds against. In the example above, `V2__seed_admin.sql` seeds against `V1__init.sql`.

In prod, only `db/migration` is scanned. The seed file is never applied.

## Naming

- `V<n>__<description>.sql` — versioned schema migration. The double underscore is mandatory.
- `R__<description>.sql` — repeatable migration. We don't use these in v0.3; left here as a reminder that they exist.
- `U<n>__<description>.sql` — undo migration. We don't use these; destructive changes get a forward-only fix in the next `V<n+1>`.

Descriptions are snake_case, English, ≤ 50 chars, no abbreviation. `V1__init.sql`, `V2__add_must_change_password.sql`, `V3__add_user_email_column.sql`.

## V1__init.sql

The full schema for v0.3. UUIDs as `uuid`, JSON as `jsonb`, BCrypt hashes as `varchar(100)`. The file lives in `db/migration/`.

```sql
-- V1__init.sql

create table users (
    id                   uuid         primary key,
    version              bigint       not null default 0,
    username             varchar(64)  not null unique,
    password_hash        varchar(100) not null,
    email                varchar(100),
    role_type            varchar(16)  not null check (role_type in ('SYS_ADMIN','ADMIN','USER')),
    must_change_password boolean      not null default true,
    created_at           timestamptz  not null default now(),
    updated_at           timestamptz  not null default now()
);

create table engines (
    id              uuid         primary key,
    version         bigint       not null default 0,
    name            varchar(80)  not null,
    code            varchar(16)  not null unique,
    server_ip       varchar(64)  not null,
    server_username varchar(64)  not null,
    server_password varchar(512) not null,    -- Jasypt-encrypted at the JPA layer
    mode            varchar(16)  not null check (mode in ('MOCK','REAL')),
    start_script    varchar(1024),
    stop_script     varchar(1024),
    log_script      varchar(1024),
    status          varchar(16)  not null default 'STOPPED' check (status in ('RUNNING','STOPPED','ERROR')),
    deleted_at      timestamptz,
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    check ( mode <> 'REAL' or (start_script is not null and stop_script is not null and log_script is not null) )
);

create table user_engine_access (
    user_id   uuid not null references users(id)   on delete cascade,
    engine_id uuid not null references engines(id) on delete cascade,
    primary key (user_id, engine_id)
);

create table audit_log (
    id                  uuid         primary key,
    timestamp           timestamptz  not null default now(),
    actor_username      varchar(64)  not null,
    actor_role          varchar(16)  not null,
    action              varchar(32)  not null,
    target_engine_code  varchar(16),
    details             jsonb
);

create index idx_audit_timestamp on audit_log (timestamp);
create index idx_audit_actor     on audit_log (actor_username);
create index idx_audit_engine    on audit_log (target_engine_code);
```

The `check ( mode <> 'REAL' or (... scripts not null ...) )` enforces "REAL mode requires scripts" at the DB level. The JPA `@Column(nullable = true)` on the scripts is the application-level view; the DB check is the backstop.

## V2__seed_admin.sql (dev profile only)

```sql
-- V2__seed_admin.sql (dev profile only — never applied in prod)

-- BCrypt hash of "admin123" (strength 10). The plaintext is in
-- application-dev.properties as a comment, NOT in this file.
insert into users (id, username, password_hash, role_type, must_change_password)
values (
    '00000000-0000-0000-0000-000000000001',
    'admin',
    '$2a$10$...',
    'SYS_ADMIN',
    true
);
```

The hash is generated with `BCrypt.hashpw("admin123", BCrypt.gensalt(10))` and pasted in. The plaintext goes in `application-dev.properties` as a comment, not in this file, because the `.sql` is the artifact that gets shipped; the `.properties` is the local-dev-only config.

## Forward-only migrations

Once a migration is applied, it is never edited. Schema changes ship as new migrations. If you find a bug in `V1__init.sql` after it's been applied to any environment (dev, staging, prod), the fix is a `V2__...` migration, not an edit to `V1`.

This is true even for typos and even when "no one is using this yet." The migration history is the audit log for the schema.

## What hibernate.ddl-auto does in v0.3

```properties
# In every profile
spring.jpa.hibernate.ddl-auto=validate
```

`validate` runs the JPA entity scan on startup and confirms the entities match the DB. If they don't (a missing column, a wrong type), the app fails to start with a clear error. This catches drift between the migrations and the JPA entities; it never changes the DB.

**Never use `update`, `create`, or `create-drop` in v0.3.** They hide migration bugs and corrupt environments.

## The seed-directory pitfall

If you put a seed file in `db/migration/` (instead of `db/seed/`) and gate it with a profile, the migration engine will still see it. The dev-only seed must live in a directory that prod's `spring.flyway.locations` does not include. That's what `db/seed/` is for.

## Anti-patterns

- **Don't put `drop table` in a migration.** Even in early development, use a new `V<n>__drop_foo.sql` if you need to undo. The migration history is the contract.
- **Don't use `spring.jpa.hibernate.ddl-auto=update` to "skip writing migrations."** It will silently change the DB in ways the migration history doesn't reflect.
- **Don't put test data in a migration that ships to prod.** The `db/seed/` directory is profile-gated for a reason.
- **Don't store the plaintext password in the seed SQL.** BCrypt-hash it; the plaintext lives in `application-dev.properties` as a comment, not in the file that ships.
- **Don't use snake_case in entity field names.** The DB columns are snake_case (`role_type`, `must_change_password`); the JPA fields are camelCase (`roleType`, `mustChangePassword`). JPA's default naming strategy handles the translation.
