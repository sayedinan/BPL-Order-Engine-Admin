# SPEC.md — BPL Order Engine Admin

| | |
|---|---|
| **Repository** | `BPL-Order-Engine-Admin` |
| **Status** | v0.3 (full rewrite — supersedes v0.2 in its entirety) |
| **Owner** | QA Intern (technical owner); this is the in-house "Commlink" admin project |
| **Backend** | Spring Boot 4.1.1 · Gradle 9.7.1 (wrapper) · Java 17 · `spring-boot-starter-webmvc` + `spring-boot-starter-security` + `spring-boot-starter-websocket` + `spring-boot-starter-data-jpa` + `spring-boot-starter-validation` + `flyway-core` + `postgresql` + `jjwt` + `jasypt-spring-boot-starter` + `apache-sshd` + Lombok |
| **Frontend** | Vite 8 · React 19 · TypeScript 6 · React Router · Tailwind CSS |
| **Database** | PostgreSQL 16+ via JPA/Hibernate 6; Flyway for migrations |
| **Auth** | Spring Security + JWT (jjwt), JPA-backed `UserDetailsService`; HTTP Basic is **gone** |
| **Engine I/O** | Apache MINA SSHD (REAL mode) + in-memory state machine (MOCK mode) — selected per `Engine.mode` |
| **Presentation** | Wed Sep 2, 2026, 11:00 AM |
| **Last updated** | Sep 1, 2026 (addendum: force-change-password flow, in-scope clarification for §3.2 / §3.5 / §4.2 / §5.2 / §5.7) |

> **v0.2 → v0.3 changes (this file supersedes v0.2 in its entirety):**
>
> - **Stack: Spring Boot 100%.** The PRD in `instruction.md` suggested Next.js + Prisma + ssh2; v0.3 keeps the existing Spring Boot backend and Vite/React frontend, and replaces only the layers that need to change. The repo layout in §2.1 reflects this.
> - **Real SSH from day one.** v0.2 was a hard "no real BPL container" environment with a hook (`guard-staging.sh`) blocking any reference to `180.210.129.233`. v0.3 builds the SSH plumbing for arbitrary `mode=REAL` engines. The hook is replaced by `block-plaintext-secrets.sh` (a general secrets guard, not a staging-specific one). The PRD's intent of "real SSH to remote engine servers" is now satisfied; see §6.
> - **3 roles, not 2.** `SYS_ADMIN`, `ADMIN`, `USER` replace `ADMIN` and `VIEWER`. v0.2's `VIEWER` is gone. `USER` is role-gated by `User.assignedEngines` (a `Set<Engine>`).
> - **Persistent DB.** v0.2 was in-memory users and in-memory engines. v0.3 is PostgreSQL with Flyway migrations, JPA entities, and a soft-delete convention for engines.
> - **JWT, not HTTP Basic.** v0.2 re-sent `Authorization: Basic <base64>` on every request. v0.3 has `POST /api/auth/login` returning a JWT; the client stores it and sends `Authorization: Bearer <token>`.
> - **Two engine implementations, not one per engine.** v0.2 had a per-engine `@Service("<code>")` class (only `bpl` was implemented). v0.3 has exactly two `OrderEngineOperations` implementations: `SshBackedEngine` (REAL) and `MockEngineOperations` (MOCK). The factory looks up engines by `code` from the `Engine` row, not from Spring beans.
> - **Audit log.** v0.2 had no audit. v0.3 has an `AuditLog` table written by `@Audited` + AOP for every state-changing endpoint, plus `AuthenticationEventListener` for login events. See §7.
> - **WebSocket for live logs.** v0.2 polled every 5s. v0.3 has `WS /api/engines/{id}/logs/stream` for real-time tail, backed by a per-engine background `tail -F` over SSH. The `GET /api/engines/{id}/logs?limit=N` endpoint remains for on-demand reads.
> - **Admin Panel.** v0.2 had no admin UI. v0.3 has an Admin Panel with Users and Engines tabs, gated by role and the `lazy-import + role check` pattern in `frontend-agent.md`.
> - **Soft delete for engines.** v0.2 didn't have engine CRUD. v0.3 does, and engine deletion is soft (`Engine.deletedAt`); see §4.5.
> - **The `OrderEngineOperations` interface is preserved across v0.2 → v0.3.** Only the implementation list and the factory's lookup source changed.
- **Force-change-password on first login.** v0.2 had no password lifecycle. v0.3 adds `User.mustChangePassword` (default `true` on create), a `mustChangePassword` claim in the issued JWT and login response, a `POST /api/auth/change-password` endpoint (authenticated, body `{ currentPassword, newPassword }`), a `CHANGE_PASSWORD` audit row, a `/change-password` page, and a post-login redirect that lands on `/change-password` while the flag is `true`. This is the *force-change* flow — distinct from the deferred "Password reset / forgot-password flow" (unauthenticated recovery, which remains out of scope).

> **Carry-forward from v0.2 (still true in v0.3):**
>
> - The error envelope format `{ timestamp, status, error, message, path }` is unchanged.
> - The base path `/api` is unchanged.
> - The v0.2 *interface contract* of `OrderEngineOperations` is the same; see §4.4.
> - The v0.2 *factory pattern* (lookup by engine code, throw `EngineNotSupportedException` on miss → 404) is the same; only the lookup source changed from a Spring bean map to `EngineRepository.findByCode(...)`.

> **On the live BPL container (clarification):**
>
> The live BPL Order Engine at `180.210.129.233` is shared with the JMeter integration suite. v0.3 builds the SSH plumbing for any `mode=REAL` engine, but **v0.3 does NOT ship a default engine pointing at this address.** The dev/test environments target a throwaway SSH server (e.g., a local `sshd` in Docker). Pointing an engine at `180.210.129.233` is a deployment decision, not a code decision, and is documented in the runbook, not in this SPEC. The `block-plaintext-secrets.sh` hook (replacing v0.2's `guard-staging.sh`) does NOT block the IP — it blocks plaintext secrets in any tool input.

---

## 1. Executive Summary

BPL Order Engine Admin is a small internal web application that lets authorized users **see, start, stop, and monitor** one or more remote order engines (BPL, PCL, others) through a browser instead of shelling into infrastructure directly. Each engine is a `mode=REAL` row (real SSH via Apache MINA SSHD) or a `mode=MOCK` row (in-memory state machine, no network). Sys.Admins add and configure engines through the UI; Admins manage regular users; Users see only the engines they've been assigned.

1. **Login** — `POST /api/auth/login` with username + password → JWT in response body, stored client-side.
2. **Engine Dashboard** — grid of `EngineCard` per visible engine; status pill, Start/Stop buttons, [View Logs]. Live status via WebSocket; polling fallback.
3. **Logs Page** — two filters: **System Audit Logs** (who did what) vs **Engine Execution Logs** (what the engine printed). Each filterable by engine.
4. **Admin Panel** — Users tab (create/delete/assign engines) and Engines tab (Sys.Admin only: add/delete/configure SSH).

There are **3 roles** with distinct capability boundaries (`SYS_ADMIN` > `ADMIN` > `USER`), enforced server-side via `@PreAuthorize` on every endpoint and client-side via route-level role gates.

**Hard constraint carried through every section below:** no plaintext credentials in any file, code, migration, log line, or audit row. The v0.2 staging-container hard block is replaced by a general secrets guard (`.claude/hooks/block-plaintext-secrets.sh`) — SSH is in scope, plaintext secrets are not.

### Out of scope this phase

- Real-time alert notifications (email, Slack) on engine state changes.
- Password reset / forgot-password flow.
- Multi-tenancy (one BPL org per deployment).
- SSO / SAML / OIDC.
- Audit log export or external SIEM forwarding.
- Rate limiting on `POST /api/auth/login` (deferred; the LDAP-style lockout is enough for v0.3).
- Cross-region engine failover.

---

## 2. Architecture & Design Patterns

### 2.1 Repository layout (v0.3 target)

```
BPL-Order-Engine-Admin/
├── SPEC.md
├── .gitignore
├── .claude/
│   ├── settings.json
│   ├── settings.local.json             # gitignored — local model/API config only
│   ├── hooks/
│   │   └── block-plaintext-secrets.sh  # PreToolUse on Bash|Write|Edit; replaces v0.2 guard-staging.sh
│   ├── skills/
│   │   ├── add-engine-via-ui.md        # sysadmin creates engines through the UI
│   │   ├── ssh-engine-ops.md           # Apache MINA SSHD lifecycle, timeouts, error categories
│   │   ├── audit-log-coverage.md       # @Audited + AOP, action enum, details shape
│   │   └── jpa-entity-patterns.md      # UUID PKs, @Version, no @Data, LAZY fetches
│   └── agents/
│       ├── backend-agent.md            # implements backend against this SPEC
│       ├── frontend-agent.md           # implements the React UI against this SPEC
│       └── qa-reviewer.md              # read-only reviewer, flags RBAC/audit/secrets drift
├── backend/                              # Spring Boot 4.1.1, Gradle 9.7.1, Java 17
│   ├── build.gradle                       # deps: webmvc, security, websocket, data-jpa, validation, flyway, postgresql, jjwt, jasypt, sshd, lombok, testcontainers, h2
│   ├── settings.gradle
│   ├── gradle/wrapper/
│   ├── gradlew, gradlew.bat
│   └── src/
│       ├── main/java/com/BPL_Order_Engine_Admin/manager/
│       │   ├── BplOrderEngineAdminBackendApplication.java
│       │   ├── auth/                      # AuthController, AuthService, JwtAuthFilter, JwtService, UserPrincipal
│       │   │   ├── validation/            # PasswordStrength + PasswordStrengthValidator
│       │   │   └── dto/                   # LoginRequest, LoginResponse, ChangePasswordRequest, UserSummary
│       │   ├── user/                      # User entity, UserRepository, UserService, UserController
│       │   │   └── dto/                   # CreateUserRequest, UpdateUserRolesRequest, UserResponse
│       │   ├── engine/
│       │   │   ├── OrderEngineOperations.java   # interface — preserved from v0.2
│       │   │   ├── OrderEngineFactory.java      # looks up by EngineRepository.findByCode
│       │   │   ├── EngineStatus.java
│       │   │   ├── EngineMode.java              # MOCK, REAL
│       │   │   ├── EngineActionResult.java      # record returned by start/stop; carries exitCode
│       │   │   ├── EngineNotSupportedException.java
│       │   │   ├── EngineUnreachableException.java
│       │   │   ├── EngineAuthException.java
│       │   │   ├── EngineScriptException.java
│       │   │   ├── LogLine.java
│       │   │   ├── LogBuffer.java               # per-engine rolling deque<LogLine>, cap 500
│       │   │   ├── LogTailerRegistry.java       # one Thread per RUNNING REAL engine
│       │   │   ├── SshClientProvider.java       # per-engine cached SshClient, idle-evicted at 5m
│       │   │   ├── EngineEntity.java            # JPA: id, code, name, serverIp, serverUsername, serverPassword (@Encrypted), scripts, mode, status, deletedAt
│       │   │   ├── EngineRepository.java
│       │   │   ├── EngineService.java           # CRUD (create / soft-delete / updateSsh / list)
│       │   │   ├── EngineActionService.java     # status / start / stop / logs (per-user assignment filter)
│       │   │   ├── EngineController.java
│       │   │   ├── dto/                         # CreateEngineRequest, UpdateEngineSshRequest, EngineResponse, EngineStatusResponse, EngineActionResponse, LogLineResponse, LogPageResponse
│       │   │   ├── impl/
│       │   │   │   ├── SshBackedEngine.java     # Apache MINA SSHD
│       │   │   │   └── MockEngineOperations.java
│       │   │   └── ws/
│       │   │       ├── EngineLogsWebSocketHandler.java
│       │   │       ├── WebSocketConfig.java
│       │   │       └── WebSocketSessionRegistry.java
│       │   ├── audit/
│       │   │   ├── AuditLog.java                # JPA: id, timestamp, actorUsername, actorRole, action enum, targetEngineCode, details (jsonb)
│       │   │   ├── AuditAction.java
│       │   │   ├── AuditLogRepository.java
│       │   │   ├── Audited.java                 # annotation
│       │   │   ├── AuditAspect.java             # AOP aspect
│       │   │   ├── AuditService.java
│       │   │   ├── AuditLogController.java      # GET /api/audit-logs
│       │   │   ├── AuthenticationSuccessListener.java
│       │   │   ├── AuthenticationFailureListener.java
│       │   │   └── dto/AuditLogResponse.java
│       │   ├── web/
│       │   │   └── ApiExceptionHandler.java     # @ControllerAdvice → standard error envelope
│       │   └── config/
│       │       ├── SecurityConfig.java          # JWT filter, JPA UserDetailsService, @PreAuthorize
│       │       ├── CorsConfig.java              # http://localhost:5173 → :8080 in dev with allowCredentials=true
│       │       ├── JasyptConfig.java            # StringEncryptor bean from JASYPT_ENCRYPTOR_PASSWORD env var
│       │       ├── JacksonConfig.java           # java.time + JavaTimeModule, NON_NULL on responses
│       │       └── DevDataInitializer.java      # dev profile only: seeds sysadmin/admin/user1/user2
│       ├── main/resources/
│       │   ├── application.properties           # base — env-driven, no secrets
│       │   ├── application-dev.properties       # dev profile overrides
│       │   ├── application-prod.properties      # prod profile
│       │   └── db/migration/
│       │       └── V1__init.sql                 # users, engines, audit_log, user_engine_access
│       └── test/java/com/BPL_Order_Engine_Admin/manager/
│           ├── auth/, user/, engine/, audit/, web/
└── frontend/                                 # Vite 8 + React 19 + TypeScript 6
    ├── package.json
    ├── tsconfig.json, tsconfig.app.json, tsconfig.node.json
    ├── vite.config.ts
    ├── eslint.config.js
    ├── index.html
    ├── public/                                 # favicon.svg, icons.svg
    └── src/
        ├── main.tsx
        ├── App.tsx                             # React Router root: /login, /dashboard, /logs, /admin
        ├── auth/AuthContext.tsx
        ├── api/client.ts                       # fetch wrapper, JWT in Authorization header, 401 → redirect
        ├── hooks/
        │   ├── useEngineLogsSocket.ts          # exponential-backoff reconnect, clean teardown
        │   └── useEngineStatus.ts              # WS primary, polling fallback
        ├── pages/
        │   ├── Login.tsx
        │   ├── Dashboard.tsx
        │   ├── Logs.tsx
        │   ├── Admin.tsx                       # lazy-loaded for non-USER roles
        │   ├── NotFound.tsx
        │   └── Forbidden.tsx
        ├── components/
        │   ├── EngineCard.tsx
        │   ├── StatusPill.tsx
        │   ├── UserForm.tsx
        │   ├── EngineForm.tsx                  # used for both add and edit
        │   └── AppShell.tsx
        └── styles/
            └── index.css                       # Tailwind directives
```

### 2.2 Design patterns

| Pattern | Where | Why |
|---|---|---|
| **Strategy / common interface** | `OrderEngineOperations` | One contract (`engineId`, `displayName`, `status`, `start`, `stop`, `getLogs(int)`, `currentMode`) implemented by exactly two classes: `SshBackedEngine` and `MockEngineOperations`. |
| **Factory Method (DB-backed)** | `OrderEngineFactory` | Resolves an `OrderEngineOperations` by `engineId` string. Looks up the `Engine` row via `EngineRepository.findByCode(...)`, then constructs a fresh per-call instance of the appropriate `OrderEngineOperations` (`new MockEngineOperations(engine)` for MOCK, `new SshBackedEngine(engine, sshClientProvider, …)` for REAL). The fresh instance holds the per-engine state (status, lastTransitionAt, log buffer for MOCK); the `SshClientProvider` retains a shared SshClient cache by engine code to amortize connect cost. On miss → `EngineNotSupportedException` → 404. |
| **JPA entities** | `User`, `Engine`, `AuditLog` | UUID PKs, `@Version` for optimistic locking, `@Getter`/`@Setter` (not `@Data`), `LAZY` on to-many. See `jpa-entity-patterns.md`. |
| **JWT auth filter** | `JwtAuthFilter` | Once-per-request: extract `Authorization: Bearer <token>`, validate with `JwtService`, populate `SecurityContext`. Stateless — no server-side session. |
| **Method-level RBAC** | `@PreAuthorize` on controllers | The 3-role matrix is enforced server-side, not just hidden in the UI. |
| **Audit log AOP** | `@Audited` + `AuditAspect` | Every state-changing endpoint is annotated. The aspect extracts the actor from the security context, evaluates `details` as SpEL, writes the row. See `audit-log-coverage.md`. |
| **Encrypted column** | `Engine.serverPassword` via `@Encrypted` + Jasypt | At-rest encryption with a master password from `JASYPT_ENCRYPTOR_PASSWORD` env var. |
| **Background tailer** | `LogTailerRegistry` | One thread per `RUNNING` `mode=REAL` engine. Started on `EngineStatusChangedEvent(RUNNING)`, stopped on `(STOPPED)` or engine deletion. Pushes lines to `LogBuffer` + WebSocket session registry. See `ssh-engine-ops.md`. |
| **WebSocket handler** | `EngineLogsWebSocketHandler` | Per-engine session registry. New connections get a snapshot of the last 100 lines from `LogBuffer`, then live updates. |
| **Soft delete** | `Engine.deletedAt` | Engine deletion is `UPDATE engine SET deleted_at = now() WHERE id = ?`. Factory's `findByCode` excludes soft-deleted rows. Audit log keeps the engine code for historical lookups. |

### 2.3 Backend package structure

Base package: `com.BPL_Order_Engine_Admin.manager`

```
manager/
├── BplOrderEngineAdminBackendApplication.java
├── auth/
│   ├── AuthController.java             # POST /api/auth/login, POST /api/auth/logout, GET /api/auth/me, POST /api/auth/change-password
│   ├── AuthService.java
│   ├── JwtService.java                 # sign/validate, reads JWT_SECRET env var
│   ├── JwtAuthFilter.java              # OncePerRequestFilter, extracts Bearer token
│   ├── UserPrincipal.java              # UserDetails impl wrapping User entity
│   ├── validation/
│   │   ├── PasswordStrength.java       # @interface
│   │   └── PasswordStrengthValidator.java
│   └── dto/ (LoginRequest, LoginResponse, ChangePasswordRequest, UserSummary)
├── user/
│   ├── User.java                       # JPA entity
│   ├── RoleType.java                   # enum: SYS_ADMIN, ADMIN, USER
│   ├── UserRepository.java
│   ├── UserService.java
│   ├── UserController.java
│   └── dto/ (CreateUserRequest, UpdateUserRolesRequest, UserResponse)
├── engine/
│   ├── OrderEngineOperations.java      # interface — preserved from v0.2
│   ├── OrderEngineFactory.java         # looks up by code
│   ├── EngineStatus.java               # enum: RUNNING, STOPPED, ERROR
│   ├── EngineMode.java                 # enum: MOCK, REAL
│   ├── EngineEntity.java               # JPA entity (note: Entity suffix to avoid clashing with the interface)
│   ├── EngineRepository.java
│   ├── EngineService.java              # CRUD (create / soft-delete / updateSsh / list)
│   ├── EngineActionService.java        # status / start / stop / logs (per-user assignment filter)
│   ├── EngineController.java
│   ├── EngineActionResult.java         # record returned by start/stop; carries exitCode
│   ├── LogLine.java
│   ├── LogBuffer.java                  # per-engine deque<LogLine>, cap 500
│   ├── LogTailerRegistry.java
│   ├── SshClientProvider.java          # per-engine cached SshClient, idle-evicted at 5m
│   ├── EngineNotSupportedException.java
│   ├── EngineUnreachableException.java
│   ├── EngineAuthException.java
│   ├── EngineScriptException.java
│   ├── impl/
│   │   ├── SshBackedEngine.java        # Apache MINA SSHD; one SshClient per engine, idle-evicted
│   │   └── MockEngineOperations.java   # in-memory state machine (formerly BplOrderEngineOperations)
│   ├── ws/
│   │   ├── EngineLogsWebSocketHandler.java
│   │   ├── WebSocketConfig.java        # /api/engines/{code}/logs/stream registration
│   │   └── WebSocketSessionRegistry.java
│   └── dto/ (EngineStatusResponse, EngineActionResponse, LogLineResponse, LogPageResponse, CreateEngineRequest, UpdateEngineSshRequest, EngineResponse)
├── audit/
│   ├── AuditLog.java                   # JPA entity
│   ├── AuditAction.java                # enum
│   ├── AuditLogRepository.java
│   ├── Audited.java                    # @interface
│   ├── AuditAspect.java
│   ├── AuditService.java
│   ├── AuditLogController.java         # GET /api/audit-logs
│   ├── AuthenticationSuccessListener.java
│   ├── AuthenticationFailureListener.java
│   └── dto/AuditLogResponse.java
├── web/
│   └── ApiExceptionHandler.java        # @ControllerAdvice → standard error envelope
└── config/
    ├── SecurityConfig.java             # JWT filter, JPA UserDetailsService, @PreAuthorize
    ├── CorsConfig.java
    ├── JasyptConfig.java
    ├── JacksonConfig.java
    └── DevDataInitializer.java         # dev profile only: seeds sysadmin/admin/user1/user2
```

### 2.4 `build.gradle` dependencies

Verbatim list (added vs v0.2: data-jpa, validation, websocket, flyway, postgresql, jjwt, jasypt-spring-boot-starter, apache-sshd):

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-websocket'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'

    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-database-postgresql'
    runtimeOnly    'org.postgresql:postgresql'

    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
    runtimeOnly    'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly    'io.jsonwebtoken:jjwt-jackson:0.12.6'

    implementation 'com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5'

    implementation 'org.apache.sshd:sshd-core:2.13.2'
    implementation 'org.apache.sshd:sshd-common:2.13.2'

    // H2 lets `gradlew bootRun` smoke-verify the app against an in-memory
    // database when a Postgres instance isn't handy; postgres is still
    // the dev/prod default via docker compose.
    runtimeOnly    'com.h2database:h2:2.2.224'

    compileOnly           'org.projectlombok:lombok'
    annotationProcessor   'org.projectlombok:lombok'

    testImplementation     'org.springframework.boot:spring-boot-starter-test'
    testImplementation     'org.springframework.security:spring-security-test'
    testImplementation     'org.springframework.boot:spring-boot-testcontainers'
    testImplementation     'org.testcontainers:junit-jupiter:1.20.4'
    testImplementation     'org.testcontainers:postgresql:1.20.4'
    runtimeOnly            'com.h2database:h2:2.2.224'
    testRuntimeOnly        'org.junit.platform:junit-platform-launcher'
    testCompileOnly        'org.projectlombok:lombok'
    testAnnotationProcessor 'org.projectlombok:lombok'
}
```

### 2.5 Safety guardrail (v0.3)

| Layer | Mechanism | What it does |
|---|---|---|
| Code | `Engine.serverPassword` is `@Encrypted` + Jasypt | Plaintext never touches the DB column. |
| Code | `EngineRepository` and `OrderEngineFactory` exclude soft-deleted engines | `findByCode(code)` returns `Optional.empty()` for `deletedAt != null`. |
| Hook | `.claude/hooks/block-plaintext-secrets.sh` (`PreToolUse` on `Bash`\|`Write`\|`Edit`) | Blocks tool input that contains `password=...`, AWS keys, bearer-token-like patterns, JDBC URLs with embedded passwords, or PEM private key bodies. Exit code `2` = block. Replaces v0.2's `guard-staging.sh`. |
| Env | `JWT_SECRET`, `JASYPT_ENCRYPTOR_PASSWORD`, `DB_USERNAME`, `DB_PASSWORD` come from env vars only | No secrets in `application.properties` or migrations. |
| Runbook | The v0.3 runbook documents that pointing an engine at `180.210.129.233` (the live BPL container shared with JMeter) is a deployment decision, not a code decision | The shipped app has no default engine pointing at that address. |

Any future work that intentionally wires a real integration against the live BPL container must follow the runbook, not just edit the code.

---

## 3. RBAC & Data Model

### 3.1 RBAC matrix (3 roles, supersedes v0.2's 2-role matrix)

| Action | SYS_ADMIN | ADMIN | USER |
|---|:---:|:---:|:---:|
| View user list | ✅ all | ✅ all | ❌ |
| Create user (any role) | ✅ | ❌ | ❌ |
| Create user (USER role only) | ✅ | ✅ | ❌ |
| Delete user (any role) | ✅ | ❌ | ❌ |
| Delete user (USER role only) | ✅ | ✅ | ❌ |
| Update user roles / engine access | ✅ any | ✅ USER only | ❌ |
| List engines | ✅ all | ✅ all | 🔒 assigned only |
| Create engine | ✅ | ❌ | ❌ |
| Delete engine | ✅ | ❌ | ❌ |
| Update engine SSH config | ✅ | ❌ | ❌ |
| View engine status / logs | ✅ all | ✅ all | 🔒 assigned only |
| Start / stop engine | ✅ all | ✅ all | 🔒 assigned only |
| View audit logs | ✅ full system | ✅ full system | ❌ |
| View real-time engine logs (WS) | ✅ all | ✅ all | 🔒 assigned only |
| Change own password (when `mustChangePassword = true`) | ✅ self | ✅ self | ✅ self |

*Notes:*
- *Role "BPL" does not exist as a string. Engine access is granted by adding the engine row to the user's `assignedEngines` set.*
- *"Assigned only" means the user must have the engine in `assignedEngines`. A USER with no assignments sees an empty dashboard, not an error.*
- *The matrix is enforced in code via `@PreAuthorize` and in queries via `WHERE engine.id IN (:assignedIds)`. Hiding the UI is UX, not security.*

### 3.2 `User` entity (JPA)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | PK, generated in `@PrePersist` |
| `version` | long | `@Version` for optimistic locking |
| `username` | String (unique, ≤ 64) | Login id; case-insensitive lookup at auth time |
| `passwordHash` | String (≤ 100) | BCrypt; one-way; never `@Encrypted` (the hash is already safe to store) |
| `roleType` | enum `RoleType` (`SYS_ADMIN`, `ADMIN`, `USER`) | A user has exactly one role. |
| `assignedEngines` | `Set<Engine>` (`@ManyToMany`, `LAZY`) | The set of engines the user can see/control. Empty for `SYS_ADMIN` / `ADMIN` (they see all), non-empty for `USER`. |
| `mustChangePassword` | boolean (NOT NULL, default `true`) | Force-change-password flag. `true` for newly created users; set to `false` by a successful `POST /api/auth/change-password`. Surfaced in the login response and as a JWT claim so the client can route to `/change-password`. See §4.2 and §5.2. |
| `createdAt` | Instant | Immutable, set in `@PrePersist` |
| `updatedAt` | Instant | Updated in `@PreUpdate` |

`Engine.assignedUsers` is the inverse side, `mappedBy = "assignedEngines"`. Always access from the `User` side to keep fetch behavior predictable. See `jpa-entity-patterns.md` for the full entity source.

### 3.3 `Engine` entity (JPA)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `version` | long | `@Version` |
| `name` | String (≤ 80) | Display name, e.g. `"BPL Order Engine"` |
| `code` | String (`^[A-Z0-9_]{2,16}$`, unique among non-deleted) | The engine id used in URLs (`/api/engines/{code}/...`) |
| `serverIp` | String (≤ 64) | IPv4 or hostname |
| `serverUsername` | String (≤ 64) | SSH user on the engine server |
| `serverPassword` | String (≤ 512) | **`@Encrypted` via Jasypt**; plaintext only in the POST/PATCH request body and the in-memory SSH session |
| `mode` | enum `EngineMode` (`MOCK`, `REAL`) | `MOCK` uses `MockEngineOperations`; `REAL` uses `SshBackedEngine` |
| `startScript` | String (≤ 1024, nullable for MOCK) | Operator-authored; sanity-checked (no `; rm`, `&& rm`, `\| rm`, backticks) but not sandboxed |
| `stopScript` | String (≤ 1024, nullable for MOCK) | Same |
| `logScript` | String (≤ 1024, nullable for MOCK) | For REAL: typically `tail -F /var/log/<engine>.log` (background) or `tail -n 100 ...` (on-demand) |
| `status` | enum `EngineStatus` (`RUNNING`, `STOPPED`, `ERROR`) | Mirrors the engine's runtime state |
| `deletedAt` | Instant (nullable) | Soft-delete marker; factory excludes soft-deleted |
| `createdAt`, `updatedAt` | Instant | Standard |

Validation: `serverIp` must be a valid IPv4 or RFC 1123 hostname. Scripts must be non-blank when `mode = REAL`. `code` must match `^[A-Z0-9_]{2,16}$` and be unique among non-deleted rows. Server returns **400** with the standard envelope on violation.

### 3.4 `AuditLog` entity (JPA, insert-only)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `timestamp` | Instant (immutable) | Set in `@PrePersist` |
| `actorUsername` | String (≤ 64) | The JWT `sub` claim (or, for `LOGIN_FAIL`, the attempted username) |
| `actorRole` | String (≤ 16) | The JWT `roles` claim as a string. Stored as string, not enum, so historical rows survive enum changes |
| `action` | enum `AuditAction` | See §7.1 for the full list |
| `targetEngineCode` | String (≤ 16, nullable) | `null` for non-engine actions |
| `details` | String (jsonb) | JSON object, capped 2KB; see `audit-log-coverage.md` for the per-action shape |

Indexes: `timestamp`, `actorUsername`, `targetEngineCode`. The audit log is **insert-only** — no `@Version`, no `updatedAt`, no setters called after save.

### 3.5 `RoleType` and `AuditAction` enums

```java
public enum RoleType { SYS_ADMIN, ADMIN, USER }

public enum AuditAction {
    CREATE_USER, DELETE_USER, UPDATE_USER_ROLES,
    CREATE_ENGINE, DELETE_ENGINE, UPDATE_ENGINE_SSH,
    START_ENGINE, STOP_ENGINE,
    LOGIN_SUCCESS, LOGIN_FAIL, LOGOUT,
    CHANGE_PASSWORD
}
```

### 3.6 `OrderEngineOperations` interface (preserved from v0.2)

```java
public interface OrderEngineOperations {
    String engineId();                                          // returns the Engine.code
    String displayName();                                       // returns the Engine.name
    EngineStatus status();                                      // current state
    Instant lastTransitionAt();                                 // null before first transition
    EngineActionResult start();                                 // STOPPED → RUNNING
    EngineActionResult stop();                                  // RUNNING → STOPPED
    List<LogLine> getLogs(int limit);                           // 50, 100, or 200
    EngineMode currentMode();                                   // MOCK or REAL
}
```

The two implementations:

| Class | When selected | Network I/O |
|---|---|---|
| `MockEngineOperations` | `Engine.mode = MOCK` | None; in-memory state machine (preserved from v0.2's `BplOrderEngineOperations` pattern) |
| `SshBackedEngine` | `Engine.mode = REAL` | Apache MINA SSHD to the engine's `serverIp` |

`SshBackedEngine` is constructed per-request from the `Engine` row (so credential rotation and IP changes take effect immediately) and uses a per-engine cached `SshClient` (idle-evicted after 5 min) to amortize connect cost. See `ssh-engine-ops.md` for the full lifecycle.

### 3.7 `OrderEngineFactory`

```java
@Component
public class OrderEngineFactory {
    private final EngineRepository engineRepository;
    private final SshClientProvider sshClientProvider;  // provides per-engine SshClient caches
    private final Duration connectTimeout;
    private final Duration startStopTimeout;
    private final Duration logsOpTimeout;

    public OrderEngineOperations get(String code) {
        EngineEntity engine = engineRepository.findByCodeAndDeletedAtIsNull(code)
            .orElseThrow(() -> new EngineNotSupportedException(code));
        return switch (engine.getMode()) {
            case MOCK -> new MockEngineOperations(engine);   // per-engine state lives in the instance
            case REAL -> new SshBackedEngine(engine, sshClientProvider,
                connectTimeout, startStopTimeout, logsOpTimeout);
        };
    }
}
```

The factory returns a **fresh** `OrderEngineOperations` per call. The MOCK implementation holds its own `AtomicReference<EngineStatus>`, `Instant lastTransitionAt`, and `Deque<LogLine>` — so two MOCK engine rows do not share state. The REAL implementation is a thin wrapper around the engine row plus the shared `SshClientProvider` cache (so credential rotation and IP changes take effect on the next call, while the underlying TCP connection is amortized).

---

## 4. API Contracts

### 4.1 Conventions

- Base path: `/api`.
- Default server port: **8080** (Spring Boot default).
- Auth: JWT in `Authorization: Bearer <token>`. The `JWT_SECRET` env var is required at startup; missing → app fails to start.
- Content type: `application/json` throughout.
- Timestamps: ISO-8601 UTC (e.g., `2026-09-01T09:15:44Z`).
- `engineId` in path segments is the `Engine.code` (e.g. `bpl`), not the UUID.
- **Error envelope** (all 4xx/5xx), unchanged from v0.2:
  ```json
  {
    "timestamp": "2026-09-01T09:02:11Z",
    "status": 409,
    "error": "Conflict",
    "message": "Engine 'bpl' is already RUNNING",
    "path": "/api/engines/bpl/start"
  }
  ```

### 4.2 Authentication endpoints

**`POST /api/auth/login`** — public

```json
// request
{ "username": "admin", "password": "..." }

// response 200 OK
{ "token": "eyJhbGciOi...", "expiresAt": "2026-09-01T17:02:11Z", "user": { "id": "...", "username": "admin", "role": "SYS_ADMIN", "assignedEngineCodes": [] }, "mustChangePassword": true }
```

- 401 on bad credentials. `LOGIN_FAIL` audit row written with `reason: "BAD_CREDENTIALS"`. The response body's `message` is `"Invalid credentials"` — no enumeration of which field was wrong. (The server throws `BadCredentialsException("Invalid credentials")` from `AuthService`; `ApiExceptionHandler` maps it to 401 with that message.)
- 403 if the user is disabled. `LOGIN_FAIL` with `reason: "USER_DISABLED"`. (Disabled users are not in v0.3's data model; the field is reserved for v0.4. The audit reason enum still exists so future code can write it without a migration.)
- BCrypt verification with strength 10. No password is logged or returned, even hashed.
- The `mustChangePassword` field in the response mirrors the `User.mustChangePassword` flag at login time. The same value is also carried in the JWT `mustChangePassword` claim so the client can route to `/change-password` from a fresh token without an extra round trip. The flag is `true` for newly created users and after a password rotation by an admin; it becomes `false` only after a successful `POST /api/auth/change-password`.

**`POST /api/auth/logout`** — authenticated

- Stateless. Server-side: writes a `LOGOUT` audit row. Client-side: drops the token and redirects to `/login`. There is no token blacklist in v0.3 (deferred; JWT expiry is the revocation mechanism for now).

**`GET /api/auth/me`** — authenticated

```json
// response 200 OK
{ "id": "...", "username": "admin", "role": "SYS_ADMIN", "assignedEngineCodes": ["BPL", "PCL"], "mustChangePassword": false }
```

- The client calls this on app load to hydrate the `AuthContext`. If the token is invalid/expired, returns 401; the client clears the token and redirects to `/login`.
- `mustChangePassword` is read from the live `User` row (not the JWT claim) so a rotation by an admin mid-session is visible on the next `me` call.

**`POST /api/auth/change-password`** — authenticated (any role)

```json
// request
{ "currentPassword": "...", "newPassword": "..." }

// response 200 OK
{ "token": "eyJhbGciOi...", "expiresAt": "2026-09-02T01:02:11Z", "user": { "id": "...", "username": "admin", "role": "SYS_ADMIN", "assignedEngineCodes": [] }, "mustChangePassword": false }
```

- The caller must be authenticated. The current JWT is read from the `Authorization: Bearer …` header; no extra session lookup.
- 400 if `currentPassword` or `newPassword` is missing/blank, or if `newPassword` fails the strength rule (min 12 chars, must include letters and digits; see §4.6.1 for the shared `PasswordStrength` validator).
- 401 if `currentPassword` does not match the stored `passwordHash` (BCrypt verify with strength 10). The response body's `message` is `"Current password is incorrect"` — do not enumerate further.
- 422 if the validator rejects the new password (same shape as other validation 422s).
- On success: write a `CHANGE_PASSWORD` audit row with `details: { actorUsername, reason: "OK" }` (no plaintext or hash), set `User.mustChangePassword = false`, persist, and **issue a new JWT** with `mustChangePassword = false` in the claims. The client must replace the stored token with the new one; the old token remains valid until its existing `expiresAt` (8h from the original login), so the new token is the only one that authenticates the post-change session cleanly.
- `@Audited(action = CHANGE_PASSWORD)` on the controller method. The aspect writes the row on success AND on the bad-current-password failure (with `details.reason: "BAD_CURRENT_PASSWORD"`); missing-field 400s are caught by the bean validator and audited separately by the standard envelope path.
- No plaintext or hashed password is ever written to the audit row, a log line, or the response.

### 4.3 Engine endpoints

**`GET /api/engines`** — authenticated

- Returns the engines the caller can see: `SYS_ADMIN` and `ADMIN` see all non-deleted engines; `USER` sees only their `assignedEngines`.
- Response: `EngineResponse[]` (see §4.5 for the shape).

**`POST /api/engines`** — `SYS_ADMIN` only

- Creates an engine row. See §4.5 for the request body and validation.
- 201 with `EngineResponse`. `@Audited(action = CREATE_ENGINE)` — see §7.
- 400 on validation failure. 409 on `code` collision (unique constraint).

**`DELETE /api/engines/{code}`** — `SYS_ADMIN` only

- Soft-delete: sets `deletedAt`. The factory's `findByCode` will return empty for this code thereafter.
- 204 on success. `@Audited(action = DELETE_ENGINE)`.
- Cascade: removes `user_engine_access` join rows for this engine. Users who had only this engine now have no assignments.
- The background log tailer, if running, is stopped.

**`PATCH /api/engines/{code}/ssh`** — `SYS_ADMIN` only

- Updates IP, username, password (re-encrypted), scripts, mode. Fields are individually optional; only present fields are updated.
- `@Audited(action = UPDATE_ENGINE_SSH)`. `details` lists the changed fields by name; never includes the new password value.
- If `mode` is changed to `REAL`, the SSH client cache for the engine is invalidated. The next `status` call attempts to connect.

**`GET /api/engines/{code}/status`** — depends on role (see §3.1)

- 404 with `"Engine '<code>' is not supported"` on unknown code (or soft-deleted). 403 if the caller is a `USER` without this engine in their assignments.
- Response: `EngineStatusResponse` (see §4.5).

**`POST /api/engines/{code}/start`** — depends on role

- 409 if already `RUNNING`. 502 on `EngineUnreachableException` or `EngineAuthException`. 504 on SSH timeout.
- `@Audited(action = START_ENGINE)` on success AND failure. On success, `details: { engineCode, exitCode: 0 }`. On failure, `details: { engineCode, error: <class>, exitCode: <n>, stderr: <truncated 2KB> }`.
- 403 if `USER` without assignment.

**`POST /api/engines/{code}/stop`** — depends on role

- Mirror of `start`, `action = STOP_ENGINE`. Same 403 / 409 / 502 / 504 mapping.

**`GET /api/engines/{code}/logs?limit=100`** — depends on role

- `limit` ∈ {50, 100, 200}; default 100. 400 on out-of-range.
- Reads from the per-engine `LogBuffer` (the rolling 500-line deque, fed by the background tailer for `REAL` engines, by the mock heartbeat for `MOCK` engines). For `REAL` engines with no tailer yet (engine just transitioned to `RUNNING`, SSH not yet connected), returns an empty `lines` array; the WS stream is the recovery path.
- Response: `LogPageResponse` (see §4.5).

**`WS /api/engines/{code}/logs/stream`** — depends on role

- Authenticated via the `Authorization: Bearer <token>` header at the WebSocket handshake (not via query param; the latter would log the token in server access logs). The `JwtAuthFilter` is reused for the handshake.
- On connect, the server sends a snapshot of the last 100 lines from `LogBuffer`, then pushes live updates.
- On `STOPPED` or engine deletion, the server sends a `{"event": "closed", "reason": "engine_stopped"}` frame and closes.
- Reconnect on the client side is exponential backoff, 1s → 30s cap, per `frontend-agent.md`.

### 4.4 User management endpoints

**`GET /api/users`** — `SYS_ADMIN`, `ADMIN`

- Returns all users. `ADMIN` can see all rows but cannot modify anyone above `USER` (the edit/delete endpoints enforce).
- Response: `UserResponse[]`.

**`POST /api/users`** — role-checked

- `SYS_ADMIN` can create any role. `ADMIN` can create only `USER` role (attempting `ADMIN` or `SYS_ADMIN` → 403).
- Request: `{ username, password, roleType, assignedEngineCodes: string[] }`.
- 201 with `UserResponse`. `@Audited(action = CREATE_USER)`. `details: { newUserId, newUsername, newRole, assignedEngines }`.
- 400 on validation (e.g., username taken, password fails the `PasswordStrength` rule in §4.6.1, invalid role for caller). 409 on unique-constraint violation.

**`DELETE /api/users/{id}`** — role-checked

- `SYS_ADMIN` can delete any user. `ADMIN` can delete only `USER`-role users.
- 204 on success. `@Audited(action = DELETE_USER)`.
- A user cannot delete themselves (400).
- Deleting the last `SYS_ADMIN` is rejected (400) to prevent lockout.

**`PATCH /api/users/{id}/roles`** — role-checked

- Request: `{ roleType?: RoleType, assignedEngineCodes?: string[] }`. Both fields are individually optional.
- `SYS_ADMIN` can change anyone's role and assignments. `ADMIN` can change only `USER`-role users' assignments (not their role — they cannot promote a USER to ADMIN).
- 200 with `UserResponse`. `@Audited(action = UPDATE_USER_ROLES)`. `details: { targetUserId, oldRoles, newRoles }` where each `Roles` is `[roleType, [engineCode, ...]]`.

### 4.5 Audit log endpoint

**`GET /api/audit-logs`** — `SYS_ADMIN`, `ADMIN` only

- `SYS_ADMIN`, `ADMIN`: full system audit log.
- `USER`: **403**, rejected outright — not filtered, not partially scoped. The audit log is admin-only. `USER` sees engine execution logs via `/api/engines/{code}/logs` and the WebSocket stream, but never the audit trail.
- Query params: `actor`, `action`, `engine`, `from`, `to`, `page` (default 0), `size` (default 50, max 200). All optional. Invalid values → 400.
- Response: `{ items: AuditLogResponse[], page, size, total }`.
- The `details` jsonb is returned as a parsed JSON object, not a string.

### 4.6 DTOs (representative)

```java
// EngineResponse
{ "id": "uuid", "code": "BPL", "name": "BPL Order Engine", "mode": "REAL",
  "serverIp": "10.0.0.5", "serverUsername": "bpl",
  "startScript": "systemctl start bpl-engine",
  "stopScript":  "systemctl stop  bpl-engine",
  "logScript":   "tail -F /var/log/bpl.log",
  "status": "RUNNING", "lastTransitionAt": "2026-09-01T09:02:11Z",
  "createdAt": "2026-08-15T10:00:00Z", "updatedAt": "2026-09-01T09:02:11Z" }
// serverPassword is NEVER in the response. Not even redacted; the field doesn't exist on the DTO.

// CreateEngineRequest / UpdateEngineSshRequest (Update omits absent fields)
{ "code": "PCL", "name": "PCL Order Engine", "mode": "REAL",
  "serverIp": "10.0.0.6", "serverUsername": "pcl", "serverPassword": "...",
  "startScript": "systemctl start pcl-engine",
  "stopScript":  "systemctl stop  pcl-engine",
  "logScript":   "tail -F /var/log/pcl.log" }

// EngineStatusResponse
{ "engineCode": "BPL", "displayName": "BPL Order Engine",
  "status": "RUNNING", "mode": "REAL",
  "lastTransitionAt": "2026-09-01T09:02:11Z", "checkedAt": "2026-09-01T09:15:44Z" }

// EngineActionResponse (start/stop success)
{ "engineCode": "BPL", "displayName": "BPL Order Engine", "status": "RUNNING",
  "message": "BPL Order Engine started.", "transitionedAt": "2026-09-01T09:02:11Z" }

// LogLineResponse
{ "timestamp": "2026-09-01T09:10:00Z", "level": "INFO",
  "message": "Order queue drained: 12 orders processed" }

// LogPageResponse
{ "engineCode": "BPL", "limit": 100, "count": 42, "lines": [LogLineResponse, ...] }

// UserResponse
{ "id": "uuid", "username": "admin", "role": "SYS_ADMIN",
  "assignedEngineCodes": ["BPL", "PCL"],
  "createdAt": "...", "updatedAt": "..." }

// AuditLogResponse
{ "id": "uuid", "timestamp": "2026-09-01T09:02:11Z",
  "actorUsername": "admin", "actorRole": "SYS_ADMIN",
  "action": "START_ENGINE", "targetEngineCode": "BPL",
  "details": { "engineCode": "BPL", "exitCode": 0 } }

// ChangePasswordRequest (used by POST /api/auth/change-password)
{ "currentPassword": "...",
  "newPassword":     "..." }

// LoginResponse (full shape; used by POST /api/auth/login and POST /api/auth/change-password)
{ "token": "eyJhbGciOi...", "expiresAt": "2026-09-01T17:02:11Z",
  "user":  { "id": "uuid", "username": "admin", "role": "SYS_ADMIN", "assignedEngineCodes": [] },
  "mustChangePassword": false }
```

#### 4.6.1 `PasswordStrength` validator

A shared Bean Validation constraint used by both `CreateUserRequest` (admin-typed initial password) and `ChangePasswordRequest.newPassword` (user-typed replacement). The rule:

- Minimum length: **12** characters.
- Must contain at least one ASCII letter (`[A-Za-z]`) and at least one digit (`[0-9]`).
- Maximum length: **128** characters (long enough for a passphrase, short enough to keep BCrypt's 72-byte input limit plus headroom).
- No whitespace-only, no control characters (`\p{Cntrl}`).
- The constraint's `message` is the string returned in the 422 envelope's `message` field (e.g. `"Password must be at least 12 characters and include a digit"`).

The constraint lives in `…/auth/validation/PasswordStrength.java` and is composed into the request DTOs via `@PasswordStrength`. The server never logs the rejected or accepted value — only the boolean result.

---

## 5. UI Requirements

Vite + React 19 + TypeScript 6 + React Router + Tailwind. Shadcn-style primitives (the v0.2 single-`AppShell` co-render is replaced by route-based pages). State is in `React.Context` (`AuthContext`, `EngineContext`); no Redux/Zustand.

### 5.1 Routes

| Path | Page | Roles |
|---|---|---|
| `/login` | `Login.tsx` | public |
| `/change-password` | `ChangePassword.tsx` | all authenticated (any role, when `mustChangePassword = true`) |
| `/dashboard` | `Dashboard.tsx` | all authenticated |
| `/logs` | `Logs.tsx` | all authenticated (USER sees a reduced view: `Engine Execution Logs` only — no `System Audit Logs`) |
| `/admin` | `Admin.tsx` (lazy) | `SYS_ADMIN`, `ADMIN` |
| `/404`, `/403` | `NotFound.tsx`, `Forbidden.tsx` | all |

The `Admin` page is **not in the bundle for `USER` role** — it's a lazy import gated by a role check at the route level. The route definition itself omits the import for `USER` so the chunk is never fetched.

### 5.2 Login page

Username + password form. On submit: `POST /api/auth/login` → JWT in response body → store in `localStorage` (or a `SameSite=Strict` non-`httpOnly` cookie) → redirect based on `mustChangePassword`:

- `mustChangePassword = true` → redirect to `/change-password` (and never to `/dashboard`, `/logs`, or `/admin`, even if the URL was deep-linked).
- `mustChangePassword = false` → redirect to `/dashboard` (or to the `?next=` query param if present and internal).

Error: 401 → inline "Invalid credentials". The error message is identical for unknown username and bad password (no enumeration). See §4.2 for the server contract.

#### 5.2.1 Change Password page (`/change-password`)

Three-field form: `current password`, `new password`, `confirm new password`. On submit:

1. Client-side check: `new` and `confirm` match; show an inline error if not.
2. `POST /api/auth/change-password` with `{ currentPassword, newPassword }` (no `confirm` field — the server trusts the client-side match).
3. On 200: store the new JWT, replace `AuthContext.user.mustChangePassword` with `false`, redirect to `/dashboard`.
4. On 401: inline "Current password is incorrect."
5. On 422: surface the server's `message` (e.g. "Password must be at least 12 characters and include a digit").
6. On 400: "Please complete all fields."

While `mustChangePassword = true`, the `/change-password` page is the only authenticated route the user can reach. A direct visit to `/dashboard`, `/logs`, or `/admin` while the flag is set is intercepted by a route guard and redirected back to `/change-password` (defense in depth — the server-side `@PreAuthorize` rules still hold, but the UI prevents the flash of protected content). The guard does not apply after the flag is cleared.

### 5.3 Engine Dashboard (`/dashboard`)

- Grid of `EngineCard` per visible engine.
- Each card: name, code, status pill (`RUNNING`/`STOPPED`/`ERROR`), `lastTransitionAt`, [Start] [Stop] [View Logs].
- Start/Stop buttons disable during in-flight calls and based on role (USER acts only on assigned engines).
- Status live: `WS /api/engines/{code}/logs/stream` (also delivers status events on a parallel channel — see §5.6) primary, polling (every 5s) fallback when WS is closed.
- SYS_ADMIN sees "+ Add Engine" → opens the engine modal.

### 5.4 Logs page (`/logs`)

- Two filter dropdowns:
  - **Source:** `System Audit Logs` (default) | `Engine Execution Logs`. The `System Audit Logs` option is hidden for `USER`; they only see `Engine Execution Logs`.
  - **Engine:** all visible engines; only relevant when Source = `Engine Execution Logs`.
- Audit logs come from `GET /api/audit-logs?actor=...&action=...&engine=...&from=...&to=...&page=...&size=...`.
- Engine execution logs come from `GET /api/engines/{code}/logs?limit=100` plus the WebSocket stream for the selected engine.
- Table view, paginated, with a "View raw JSON" toggle for debugging.

### 5.5 Admin Panel (`/admin`)

- Two tabs: **Users** and **Engines**.
- **Users tab:**
  - Table: username, role, assigned engines, [Edit] [Delete].
  - "+ Add User" modal: username / password / role (constrained by caller's role per §3.1) / assignedEngines multi-select.
  - `ADMIN` cannot create `ADMIN` or `SYS_ADMIN`; the role select excludes those.
- **Engines tab** (SYS_ADMIN full; ADMIN read-only view of engine list with assigned users):
  - Table: name, code, mode, serverIp, [Edit SSH] [Delete].
  - "+ Add Engine" / [Edit SSH] opens the engine form per `add-engine-via-ui.md`.
  - [Delete] confirms, then DELETEs the row (soft-delete).

### 5.6 WebSocket protocol (logs/stream)

- Frame format (text): `{"timestamp": "ISO", "level": "INFO", "message": "..."}` per line.
- On close: server sends `{"event": "closed", "reason": "engine_stopped" | "engine_deleted" | "auth_failed"}` then closes the socket.
- Reconnect (client): exponential backoff 1s → 30s cap, per `frontend-agent.md`.

### 5.7 The `AuthContext` contract

- `{ user, token, mustChangePassword, login(username, password), changePassword(current, next), logout(), isLoading }`.
- `user` is hydrated from `GET /api/auth/me`; `mustChangePassword` is read from the same response (the live `User` row, not the JWT claim). On 401, clear both and redirect to `/login`.
- `login(username, password)` returns a `{ mustChangePassword: boolean }` result so the caller (the `Login` page) can choose between `/change-password` and `/dashboard` without re-parsing the JWT. See §5.2 for the routing rule.
- `changePassword(currentPassword, newPassword)` calls `POST /api/auth/change-password`, replaces `token` and `user` with the new ones, and sets `mustChangePassword = false` on success. The caller (`ChangePassword` page) is responsible for the `/dashboard` redirect after the promise resolves.
- All API calls go through `src/api/client.ts`, a `fetch` wrapper that adds `Authorization: Bearer <token>`, unwraps the error envelope into a thrown `Error(message)`, and handles 401 (clear token, redirect).
- The token is **never** in a non-`httpOnly` cookie. `localStorage` or a `SameSite=Strict` cookie is acceptable; both are at the XSS-leak level, not the CSRF level.

---

## 6. Engine Implementation

### 6.1 `MockEngineOperations`

Reused from v0.2's `BplOrderEngineOperations` pattern. One `AtomicReference<EngineStatus>` per `Engine` row (the wrapper holds the state, the singleton is the dispatcher). Bounded `ReentrantLock` for transitions. `ArrayDeque<LogLine>` cap 500, seeded at construction with 3 canned lines (per the v0.2 mock). `@Scheduled(fixedDelay = 2000)` task that, **only while `status == RUNNING`**, appends a synthetic heartbeat line. On `STOPPED`, the scheduler is a no-op. The `MOCK` engine never touches the network.

### 6.2 `SshBackedEngine`

Real Apache MINA SSHD against `Engine.serverIp`. One `SshClient` per engine, lazily connected on first call, idle-evicted after 5 minutes of inactivity, closed on app shutdown (`@PreDestroy`).

**Timeouts:**

| Operation | Connect | Operation | Notes |
|---|---|---|---|
| `status()` | 5s | 5s | `echo OK` probe |
| `start()` | 5s | 30s | Future-bounded, not unbounded thread sleep |
| `stop()` | 5s | 30s | Same |
| `getLogs(limit)` | 5s | 10s | Single `tail -n` |
| `tail -F` (background) | 5s | unbounded but cancellable | Per-engine thread |

**Error categories (3):**

1. **Auth failure** (`UserAuthException`) → `EngineAuthException` → 403. No retry. `START_ENGINE` audit row with `details: { error: "SSH_AUTH_FAILED" }`. Never log the password.
2. **Connection refused / unreachable** → `EngineUnreachableException` → 502. Retry once before giving up. Background tailer reconnects with exponential backoff (1s → 60s cap).
3. **Script exit non-zero** → `EngineScriptException(exitCode, stderr)` → 502. `details` includes the exit code and the truncated stderr (2KB cap).

**Background log tailer:**

- One thread per `mode=REAL` engine in `RUNNING` state. Started by `LogTailerRegistry` on `EngineStatusChangedEvent(RUNNING)`. Stopped on `(STOPPED)`, engine deletion, or 5 consecutive reconnect failures.
- Runs `tail -F <logPath>` (or whatever `Engine.logScript` is). Each line is pushed to:
  1. `LogBuffer` (per-engine `ArrayDeque<LogLine>`, cap 500).
  2. `WebSocketSessionRegistry` (every connected viewer for that engine).
- The decrypted `serverPassword` lives in a `char[]` (not `String`) inside the auth callback and is zeroed after the `ClientSession` is established. Never logged, never in an exception message, never in an audit row.

### 6.3 `OrderEngineFactory`

- `findByCodeAndDeletedAtIsNull(code)` → `Optional<Engine>`. On empty → `EngineNotSupportedException` → 404.
- Resolves to `MockEngineOperations.forEngine(engine)` (a thin per-engine wrapper) or `new SshBackedEngine(engine, sshClientProvider)`.
- Spring bean autowiring is **not** the lookup mechanism. The DB row is the source of truth. Adding an engine is a row, not a class.

---

## 7. Audit Log

### 7.1 Action enum (the complete list)

```
CREATE_USER, DELETE_USER, UPDATE_USER_ROLES,
CREATE_ENGINE, DELETE_ENGINE, UPDATE_ENGINE_SSH,
START_ENGINE, STOP_ENGINE,
LOGIN_SUCCESS, LOGIN_FAIL, LOGOUT
```

Read-only endpoints (`GET` on engines, audit logs, users, logs) are **not** audited. The audit table would fill with noise otherwise.

### 7.2 Mechanism: `@Audited` + AOP

```java
@Target(METHOD)
@Retention(RUNTIME)
public @interface Audited {
    AuditAction action();
    boolean targetEngineFromPath() default false;
    String details() default "";  // SpEL, evaluated against method args + return value
}
```

`AuditAspect` does:

1. **Before:** extract `actorUsername` and `actorRole` from `SecurityContext` (JWT claims).
2. **Resolve target engine** if `targetEngineFromPath = true`: extract `{code}` from the URI, look up `Engine.code` (the path is the engine's UUID OR the code — see §4.3).
3. **Invoke the method.** Catch return or exception.
4. **After (success):** write `AuditLog` with `details` from SpEL.
5. **After (failure):** write `AuditLog` with `details: { error: <class>, message: <truncated> }`. Don't swallow the exception.

`LOGIN_SUCCESS` / `LOGIN_FAIL` are written by `AuthenticationSuccessListener` / `AuthenticationFailureListener`, not `@Audited`. The failure listener writes the row with `actorUsername = <the username that was tried>` (the security context is empty on failure) and `details: { reason: "BAD_CREDENTIALS" | "USER_DISABLED" | "ACCOUNT_LOCKED" }`.

### 7.3 `details` shape per action

See `audit-log-coverage.md` for the full per-action shape. Examples:

```json
// CREATE_USER
{ "newUserId": "uuid", "newUsername": "alice", "newRole": "USER", "assignedEngines": ["BPL"] }
// UPDATE_ENGINE_SSH
{ "engineCode": "BPL", "fieldsChanged": ["serverPassword", "logScript"] }   // never the new password
// START_ENGINE (success)
{ "engineCode": "BPL", "exitCode": 0 }
// START_ENGINE (failure)
{ "engineCode": "BPL", "error": "EngineScriptException", "exitCode": 127, "stderr": "sh: start.sh: not found" }
// LOGIN_FAIL
{ "reason": "BAD_CREDENTIALS" }    // never the attempted password
```

`details` is capped at 2KB. Truncation emits a `WARN` log line `"details truncated"` and the row is written with the truncated content.

### 7.4 Anti-patterns

- No audit rows from inline `auditLogRepository.save(...)` in the controller — the aspect is the single source. Inline writes will be forgotten when the controller is refactored.
- No actor in a request body field. The actor comes from the security context. A user could otherwise send `actorUsername=admin` and forge audit attribution.
- No secrets in `details`. DTOs shouldn't even carry them.
- No audit on reads.

---

## 8. Configuration & Environment

`application.properties` (base, all secrets via env):

```properties
spring.application.name=bpl-order-engine-admin-backend
spring.profiles.active=${SPRING_PROFILES_ACTIVE:dev}

# DB
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

# JWT
app.jwt.secret=${JWT_SECRET}             # required, no default
app.jwt.ttl=PT8H
app.jwt.issuer=bpl-order-engine-admin

# Jasypt
jasypt.encryptor.password=${JASYPT_ENCRYPTOR_PASSWORD}  # required
jasypt.encryptor.algorithm=PBEWithHMACSHA512AndAES_256
jasypt.encryptor.iv-generator-classname=org.jasypt.iv.RandomIvGenerator

# CORS
app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS:http://localhost:5173}

# SSH defaults
app.ssh.connect-timeout=5s
app.ssh.start-stop-timeout=30s
app.ssh.log-tailer-reconnect-cap=60s
app.ssh.idle-eviction=5m
```

`application-dev.properties` overrides:

```properties
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/bpl_admin_dev}
spring.datasource.username=${DB_USERNAME:bpl_admin}
spring.datasource.password=${DB_PASSWORD:bpl_admin}
spring.flyway.locations=classpath:db/migration,classpath:db/seed
app.cors.allowed-origins=http://localhost:5173
```

`application-prod.properties` overrides:

```properties
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.locations=classpath:db/migration
app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS}
```

### 8.1 Required env vars (production)

| Var | Purpose |
|---|---|
| `DB_URL` | JDBC URL, e.g. `jdbc:postgresql://prod-db:5432/bpl_admin` |
| `DB_USERNAME` | DB user |
| `DB_PASSWORD` | DB password |
| `JWT_SECRET` | HMAC secret for JWT signing; ≥ 256 bits |
| `JASYPT_ENCRYPTOR_PASSWORD` | Master password for `Engine.serverPassword` encryption |
| `CORS_ALLOWED_ORIGINS` | Comma-separated origin list |
| `SPRING_PROFILES_ACTIVE` | `prod` (or `dev` for local) |

Missing `JWT_SECRET` or `JASYPT_ENCRYPTOR_PASSWORD` → app fails to start with a clear error.

### 8.2 Flyway migrations & dev seed

- `V1__init.sql`: creates `users`, `engines`, `audit_log`, `user_engine_access`. UUIDs as `uuid` type; `audit_log.details` as `jsonb`; indexes on `audit_log.timestamp`, `audit_log.actorUsername`, `audit_log.targetEngineCode`.
- **Dev seed** (no SQL migration): `config/DevDataInitializer.java` is a `@Component` that runs on the `dev` profile only and seeds four users (`sysadmin`/`admin`/`user1`/`user2`) plus two MOCK engines (`BPL`/`PCL`) on first boot, when the `users` table is empty. The plaintext passwords and engine names live in the source as `@Value("${app.dev-seed.*:…}")` with dev defaults; the BCrypt hashes are produced at seed time, never stored. The seed is a no-op if any user row already exists. Demo credentials are documented in `README-dev.md`.
- No `spring.jpa.hibernate.ddl-auto=update` in any profile. Flyway is the source of truth for schema.

---

## 9. What's deferred from v0.3 (not in this phase)

- Real-time alert notifications (email, Slack) on engine state changes.
- Password reset / forgot-password flow. (The *force-change-password-on-first-login* flow is **in scope** in §4.2 / §5.2.1; what is deferred is the unauthenticated recovery flow with email/SMS tokens, account-disable support, and self-service reset links.)
- User account enable/disable (the `LOGIN_FAIL: USER_DISABLED` audit reason is reserved but the field doesn't exist yet).
- JWT refresh tokens / token blacklist / forced logout. JWT expiry (8h) is the revocation mechanism for now.
- Audit log export or external SIEM forwarding.
- Rate limiting on `POST /api/auth/login` (deferred to v0.4; account lockout after N consecutive `LOGIN_FAIL` is enough for v0.3's threat model).
- Multi-tenancy.
- SSO / SAML / OIDC.
- Cross-region engine failover.
- WebSocket for engine status events separate from the logs stream (v0.3 reuses the logs/stream WS; v0.4 may split them).

---

## 10. On the live BPL container

The live BPL Order Engine at `180.210.129.233` is shared with the JMeter integration suite and is staging infrastructure. v0.3 builds the SSH plumbing for arbitrary `mode=REAL` engines, but:

- The shipped app has **no default engine row** pointing at that address.
- Dev/test environments target a throwaway SSH server (a local `sshd` in Docker is the recommendation; a VM in a non-prod VLAN is acceptable).
- Pointing an engine at `180.210.129.233` is a deployment decision documented in the runbook, not a code decision.
- The v0.2 `guard-staging.sh` hook is replaced by `block-plaintext-secrets.sh` in v0.3 — a general secrets guard, not a staging-specific block. The IP itself is no longer special in the hook layer.

Any future work that intentionally wires a real integration against the live BPL container must follow the runbook, not just edit the code.
