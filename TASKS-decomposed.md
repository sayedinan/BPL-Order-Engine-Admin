# TASKS-decomposed.md

Per-task subtask tables, produced via the [`task-decomposition`](.claude/skills/task-decomposition/SKILL.md) skill.

> **How to use this file:** For each task in `TASKS.md`, the relevant subagent
> (backend-agent, frontend-agent, or qa-reviewer) produces the subtask table
> below. The orchestrator reviews the table. On approval, the subagent
> executes the subtasks one at a time and reports when the whole task is
> done. The qa-reviewer cross-checks the implementation against the
> planned subtasks.

Legend:
- **🔒** = security-relevant seam (auth, RBAC, credentials, SSH). Extra scrutiny in review.
- `15.1`, `15.2`, … = subtask numbers; they nest under the parent task's TASKS.md number.
- `done when` is something you could paste into a terminal or a test file right now.

---

## Task #11 — SPEC.md USER audit log fix  📄

**Parent task:** TASKS.md #11. **Skill used:** `task-decomposition`.
**Files the task will touch:** `SPEC.md` (one file, one commit).
**Decomposed by:** orchestrator (Sayed). **Date:** Sept 1, 2026.

| # | Subtask | Touches | Done when |
|---|---|---|---|
| 11.1 | Edit §3.1 RBAC matrix `View audit logs` row: USER column from `🔒 assigned engines only` to `❌` | `SPEC.md` (one row, line 324) | `grep -n "View audit logs" SPEC.md` shows the USER cell is `❌` |
| 11.2 | Edit §4.5 `GET /api/audit-logs` heading: from "depends on role" to "`SYS_ADMIN`, `ADMIN` only" | `SPEC.md` (one heading, line 580) | `grep -n "GET /api/audit-logs" SPEC.md` shows the new heading |
| 11.3 | Edit §4.5 USER clause: replace the assignedEngines-filtered bullet with a 403 note | `SPEC.md` (one bullet, line 583) | `grep -c "403" SPEC.md` is `>= 2` (the new note + the standard envelope reference) |
| 11.4 | Edit §5.4 Logs page: add a sentence that the Source dropdown hides "System Audit Logs" for USER | `SPEC.md` (one paragraph, ~line 671) | `grep -n "Source dropdown" SPEC.md` returns the new sentence |
| 11.5 | Edit §5.1 routes table: keep `/logs` as "all authenticated" but add a note that USER sees a reduced view | `SPEC.md` (one row in the routes table) | `grep -n "reduced view" SPEC.md` returns the new note |

**Review notes for the orchestrator:**

- Five edits, all in one file. Each is a single line/row change. No behavioral ambiguity.
- No subtask is 🔒 — this is a doc task. The behavior change (USER → 403) is enforced by the runtime, not by the doc; the doc is the *contract* the runtime will be built against in tasks #16 and #21.
- Suggested execution order matches the table top-to-bottom (matrix → endpoint → UI). If the editor reads SPEC.md linearly, the changes land in narrative order.

---

## Task #12 — qa-reviewer gains USER→403 audit log check  ⚙️ (agent config)

**Parent task:** TASKS.md #12. **Skill used:** `task-decomposition`.
**Files the task will touch:** `.claude/agents/qa-reviewer.md` (one file, one section).
**Decomposed by:** orchestrator. **Date:** Sept 1, 2026.

| # | Subtask | Touches | Done when |
|---|---|---|---|
| 12.1 | Add a new bullet under "1. RBAC drift" for the `GET /api/audit-logs` USER→403 check | `qa-reviewer.md` (one bullet, one section) | `grep -n "USER.*403.*audit" qa-reviewer.md` returns the new line |
| 12.2 🔒 | Add a severity tag: the missing USER check is `blocker` if the endpoint leaks audit rows | `qa-reviewer.md` (one inline edit) | The new bullet's severity line says `blocker` |
| 12.3 | Cross-reference the SPEC.md §4.5 wording in the check (so future drift between SPEC and reviewer is obvious) | `qa-reviewer.md` (one line) | The new bullet ends with `See SPEC.md §4.5.` |

**Review notes:**

- Three edits, all in one file's RBAC section. Each is independent.
- 12.2 is 🔒 because it tags a *missing RBAC check* — the failure mode is a data leak.

---

## Task #13 — Rewrite rbac-redesign-spec.md as resolution doc  📄

**Parent task:** TASKS.md #13. **Skill used:** `task-decomposition`.
**Files the task will touch:** `rbac-redesign-spec.md` (full rewrite).
**Decomposed by:** orchestrator. **Date:** Sept 1, 2026.

| # | Subtask | Touches | Done when |
|---|---|---|---|
| 13.1 | Write the new header: historical framing, link to v0.3 SPEC, link to TASKS.md | `rbac-redesign-spec.md` (top 10 lines) | Header includes the phrase "Phase 2 design, transcribed" |
| 13.2 | Write §1 "Why this exists" — mark the original sketch as historical, not active | `rbac-redesign-spec.md` (one section) | §1 says "treat this file as the resolution record, not a parallel spec" |
| 13.3 | Write §2 "Decisions for the 6 open questions" — one subsection per question, each citing the v0.3 SPEC section that answers it | `rbac-redesign-spec.md` (one section, 6 subsections) | All 6 sketch questions present, each with a v0.3 answer |
| 13.4 | Write §3 "USER audit visibility" — explain why USER sees engine execution logs but never the audit log | `rbac-redesign-spec.md` (one section) | §3 cross-references SPEC.md §3.1 and §4.5 |

**Review notes:**

- Full rewrite of one file. The original 81 lines become ~50 lines of "what was decided."
- No 🔒 — this is a doc task.

---

## Task #14a — SPEC.md adds `mustChangePassword` + change-password endpoint  📄

**Parent task:** TASKS.md #14a. **Skill used:** `task-decomposition`.
**Files the task will touch:** `SPEC.md` (multiple sections, one file).
**Decomposed by:** orchestrator. **Date:** Sept 1, 2026.

| # | Subtask | Touches | Done when |
|---|---|---|---|
| 14a.1 | §3.2 `User` entity: add `mustChangePassword: boolean` row to the field table | `SPEC.md` (one row in §3.2 table) | `grep -n "mustChangePassword" SPEC.md` returns the row in §3.2 |
| 14a.2 | §3.5 `AuditAction` enum: add `CHANGE_PASSWORD` to the enum block | `SPEC.md` (one line in §3.5) | `grep -n "CHANGE_PASSWORD" SPEC.md` returns the enum value |
| 14a.3 | §4.2 `LoginResponse` gains `mustChangePassword: boolean` | `SPEC.md` (one field in the LoginResponse shape) | The LoginResponse JSON example includes `mustChangePassword` |
| 14a.4 🔒 | §4.2 new endpoint: `POST /api/auth/change-password` with body, errors, and audit row documented | `SPEC.md` (one new endpoint section) | `grep -n "POST /api/auth/change-password" SPEC.md` returns the new section |
| 14a.5 | §5.1 routes: new `/change-password` page | `SPEC.md` (one row in the routes table) | The routes table includes `/change-password` |
| 14a.6 | §5.2 Login page: redirect to `/change-password` if `mustChangePassword = true`; new §5.2.1 Change Password page | `SPEC.md` (one paragraph + one new page section) | `grep -n "Change Password" SPEC.md` returns the new page section |

**Review notes:**

- Six edits, all in one file. Each is independent.
- 14a.4 is 🔒 because it documents an auth-credential mutation endpoint. The contract is the security boundary.

---

## Task #14 — Backend foundation: Gradle deps + Flyway V1__init.sql + 3 JPA entities  ⚙️

**Parent task:** TASKS.md #14. **Skill used:** `task-decomposition`.
**Files the task will touch:** `BPL-Order-Engine-Admin-backend/build.gradle`, `…/src/main/resources/db/migration/V1__init.sql`, three new entity files in `…/manager/{user,engine,audit}/`.
**Decomposed by:** orchestrator. **Date:** Sept 1, 2026.

| # | Subtask | Touches | Done when |
|---|---|---|---|
| 14.1 | Update `build.gradle` with the v0.3 dep set per SPEC §2.4 | `build.gradle` (one file) | `./gradlew dependencies --configuration runtimeClasspath` lists `spring-boot-starter-data-jpa`, `jjwt-api`, `jasypt-spring-boot-starter`, `sshd-core` |
| 14.2 | Create `V1__init.sql` with the `users` table (UUID PK, `mustChangePassword` deferred to #14b) | `db/migration/V1__init.sql` (one table) | `grep -c "create table users" V1__init.sql` returns `1` |
| 14.3 | Create `V1__init.sql` `engines` table with all v0.3 columns including `serverPassword` and `mode` | `V1__init.sql` (one table) | `grep -c "create table engines" V1__init.sql` returns `1` |
| 14.4 | Create `V1__init.sql` `audit_log` table with `jsonb` details + 3 indexes | `V1__init.sql` (one table, one index block) | `grep -c "create table audit_log" V1__init.sql` returns `1`, all 3 indexes present |
| 14.5 | Create `V1__init.sql` `user_engine_access` join table | `V1__init.sql` (one table) | `grep -c "create table user_engine_access" V1__init.sql` returns `1` |
| 14.6 | Create `User` JPA entity without `mustChangePassword` (added in #14b) | `…/user/User.java` (one file) | `./gradlew compileJava` green; `User` has UUID id, `@Version`, `passwordHash`, `roleType`, `assignedEngines` LAZY |
| 14.7 | Create `Engine` JPA entity | `…/engine/EngineEntity.java` (one file) | `./gradlew compileJava` green; `Engine` has all v0.3 fields, no Lombok `@Data` |
| 14.8 | Create `AuditLog` JPA entity (insert-only, no `@Version`) | `…/audit/AuditLog.java` (one file) | `./gradlew compileJava` green; `details` field is `@JdbcTypeCode(SqlTypes.JSON)` |

**Review notes:**

- Eight subtasks, three different files. The five SQL subtasks could be one, but each table is its own migration audit row in production; keep them separate.
- No 🔒 yet — no security behavior in the foundation. The auth filter lands in #15.

---

## Task #14b — `User` entity gains `mustChangePassword`  ⚙️

**Parent task:** TASKS.md #14b. **Skill used:** `task-decomposition`.
**Files the task will touch:** `…/user/User.java`, `…/db/migration/V1__init.sql`.
**Depends on:** #14 (entity must exist).
**Decomposed by:** orchestrator. **Date:** Sept 1, 2026.

| # | Subtask | Touches | Done when |
|---|---|---|---|
| 14b.1 🔒 | Add `private boolean mustChangePassword;` with `@Column(nullable = false)` to `User` | `User.java` (one field) | `./gradlew compileJava` green; field exists with `@Column(nullable = false)` |
| 14b.2 | Default `mustChangePassword = true` in the JPA constructor and `@PrePersist` | `User.java` (one method) | New `User` instances default to `mustChangePassword = true` (covered by the test in #15) |
| 14b.3 | Add the `must_change_password BOOLEAN NOT NULL DEFAULT TRUE` column to `V1__init.sql` (this is actually a v0.1→v0.3 schema reconciliation — see notes) | `V1__init.sql` (one column) | The column appears in the `users` CREATE TABLE block |

**Review notes:**

- Three subtasks, two files. The `V1__init.sql` edit in 14b.3 is a doc-only change here; in a real project, this would be a `V2__add_must_change_password.sql` instead. Since this codebase is being built fresh, the column lands in `V1`.
- 14b.1 is 🔒 because the field is part of the auth flow's contract.

---

## Task #15 — Backend: repositories + SecurityConfig + JWT filter + login  ⚙️

**Parent task:** TASKS.md #15. **Skill used:** `task-decomposition`. **Worked example** in the `task-decomposition` skill.
**Files the task will touch:** ~14 new files in `…/manager/{auth,user,engine,audit,config,web}/`, plus `application.properties` and `V2__seed_admin.sql`.
**Depends on:** #14, #14b.
**Decomposed by:** orchestrator. **Date:** Sept 1, 2026.

| # | Subtask | Touches | Done when |
|---|---|---|---|
| 15.1 | Create `UserRepository extends JpaRepository<User, UUID>` with `findByUsername(String)` and the role-checked query helpers | `…/user/UserRepository.java` | `./gradlew compileJava` green |
| 15.2 | Create `EngineRepository extends JpaRepository<EngineEntity, UUID>` with `findByCodeAndDeletedAtIsNull(String)` | `…/engine/EngineRepository.java` | `./gradlew compileJava` green |
| 15.3 | Create `AuditLogRepository extends JpaRepository<AuditLog, UUID>` with the filter-by-actor/engine/date query | `…/audit/AuditLogRepository.java` | `./gradlew compileJava` green |
| 15.4 🔒 | Create `JasyptConfig` with `StringEncryptor` bean reading `JASYPT_ENCRYPTOR_PASSWORD` env var | `…/config/JasyptConfig.java` | App fails to start if the env var is missing |
| 15.5 | Create `CorsConfig` (dev origin `http://localhost:5173` with `allowCredentials=true`) and `JacksonConfig` (`JavaTimeModule`, `NON_NULL`) | `…/config/CorsConfig.java`, `…/config/JacksonConfig.java` | App starts, CORS preflight from the dev origin returns the right headers |
| 15.6 🔒 | Create `JwtService` (sign/validate, claims `sub`, `roles`, `mustChangePassword`) | `…/auth/JwtService.java` | `JwtService.sign(username, roles, mustChange)` returns a token that `JwtService.parse` can validate |
| 15.7 🔒 | Create `UserPrincipal` (Spring `UserDetails` wrapping `User`) | `…/auth/UserPrincipal.java` | `UserPrincipal` exposes `getUsername()`, `getPassword()`, `getAuthorities()` from the entity |
| 15.8 🔒 | Create `JwtAuthFilter` that extracts `Authorization: Bearer …` and parses it (parse step only — no security context population yet) | `…/auth/JwtAuthFilter.java` | A request with a valid token reaches the controller; one with a malformed token returns 401 |
| 15.9 🔒 | Extend `JwtAuthFilter` to populate `SecurityContext` with the `UserPrincipal` (authenticate step) | `…/auth/JwtAuthFilter.java` (same file, second pass) | A request with a valid token has `SecurityContextHolder` populated; `Authentication.getName()` returns the username |
| 15.10 | Create `SecurityConfig` with the filter chain, JPA `UserDetailsService`, `@EnableMethodSecurity`, role hierarchy | `…/config/SecurityConfig.java` | `POST /api/auth/login` is permit-all; `GET /api/auth/me` requires authentication; `GET /api/engines` requires authentication |
| 15.11 | Create `AuthController` with `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me` | `…/auth/AuthController.java` | `curl -X POST /api/auth/login -d '{...}'` returns a JWT and a `mustChangePassword` field |
| 15.12 🔒 | Create `POST /api/auth/change-password` endpoint per SPEC §4.2 (the #14a endpoint, implemented here) | `…/auth/AuthController.java` (one new method) | `curl -X POST /api/auth/change-password -d '{currentPassword, newPassword}'` with the right JWT returns a new JWT with `mustChangePassword=false` |
| 15.13 🔒 | Create `AuthenticationSuccessListener` and `AuthenticationFailureListener` for `LOGIN_SUCCESS` / `LOGIN_FAIL` audit rows | `…/audit/AuthenticationSuccessListener.java`, `…/audit/AuthenticationFailureListener.java` | A successful login writes a `LOGIN_SUCCESS` row; a failed login writes a `LOGIN_FAIL` row with `reason: "BAD_CREDENTIALS"` and the attempted username |
| 15.14 | Add `V2__seed_admin.sql` (dev profile only) with one SYS_ADMIN (`mustChangePassword=true`, BCrypt-hashed password) | `db/seed/V2__seed_admin.sql` (new file, dev profile only) | `application-dev.properties` activates the seed location; running bootRun creates the admin row |
| 15.15 | Write a `@SpringBootTest` smoke that boots the context, logs in as the seeded admin, and calls `GET /api/auth/me` | `…/src/test/…/AuthSmokeTest.java` | `./gradlew test` is green; the smoke passes |

**Review notes:**

- 15 subtasks, ~14 files. The biggest task in the build.
- 🔒 subtasks (7): 15.4 (Jasypt master-password source — credential in env), 15.6 (JWT signing key + claim with `mustChangePassword` — token + credential), 15.7 (`UserPrincipal` exposes the entity's `passwordHash` via `getPassword()` — credential-adjacent principal), 15.8 (parse only), 15.9 (authenticate — populates `SecurityContext`), 15.12 (the change-password endpoint — credential mutation), 15.13 (login audit rows containing usernames and failure reasons — audit-row writer).
- 15.8 and 15.9 are intentionally split per the skill's anti-pattern: *"Bundling the filter chain's parse step with its authenticate step. This pair is the single highest-leverage bug in a JWT filter — always two subtasks."*
- 15.14 lives in `db/seed/` and is gated by the dev profile so it never lands in prod.

---

## Task #16 — Backend: User CRUD endpoints + @Audited + AuditAspect  ⚙️

**Parent task:** TASKS.md #16. **Skill used:** `task-decomposition`.
**Files the task will touch:** ~8 new files in `…/manager/{audit,user,web}/`.
**Depends on:** #15.
**Decomposed by:** orchestrator. **Date:** Sept 1, 2026.

| # | Subtask | Touches | Done when |
|---|---|---|---|
| 16.1 | Create `AuditAction` enum with the full set (`CREATE_USER`, `DELETE_USER`, `UPDATE_USER_ROLES`, `CREATE_ENGINE`, `DELETE_ENGINE`, `UPDATE_ENGINE_SSH`, `START_ENGINE`, `STOP_ENGINE`, `LOGIN_SUCCESS`, `LOGIN_FAIL`, `LOGOUT`, `CHANGE_PASSWORD`) | `…/audit/AuditAction.java` | `./gradlew compileJava` green; all 12 values present |
| 16.2 | Create `@Audited` annotation (`action`, `targetEngineFromPath`, `details` SpEL) | `…/audit/Audited.java` | The annotation compiles and is usable on a method |
| 16.3 🔒 | Create `AuditAspect` (AOP): extract actor from `SecurityContext`, resolve target engine from path, write row on success AND on exception | `…/audit/AuditAspect.java` | A `@Audited` controller method produces exactly one `AuditLog` row per call (success or failure) |
| 16.4 | Create DTOs: `CreateUserRequest`, `UpdateUserRolesRequest`, `UserResponse` with validation annotations | `…/user/dto/*.java` (three files) | Validation rejects missing/blank fields; `UserResponse` does not include `passwordHash` |
| 16.5 | Create `UserService` (BCrypt hash, set `mustChangePassword = true` on create, prevent last-SYS_ADMIN delete, prevent self-delete) | `…/user/UserService.java` | The integration test in 16.10 verifies four curl-level checks: `curl -X DELETE /api/users/{selfId}` as a SYS_ADMIN returns 400; `curl -X DELETE /api/users/{id}` where the target is the only remaining SYS_ADMIN returns 400; `curl -X POST /api/users -d '{"username":"u","password":"...","roleType":"USER"}'` writes a row with `passwordHash` starting `$2a$10$` and `mustChangePassword=true`; `curl -X POST /api/users -d '{"username":"u","password":"plain"}'` rejects passwords shorter than the rule |
| 16.6 | Create `UserController` `GET /api/users` (SYS_ADMIN + ADMIN) | `…/user/UserController.java` (one method) | `curl` as SYS_ADMIN returns all users; as ADMIN returns all users; as USER returns 403 |
| 16.7 🔒 | Create `UserController` `POST /api/users` with the role-checked rule (ADMIN can only create USER; SYS_ADMIN can create any) | `…/user/UserController.java` (one method) | `curl` as ADMIN with `roleType=ADMIN` returns 403; as ADMIN with `roleType=USER` returns 201 |
| 16.8 | Create `UserController` `DELETE /api/users/{id}` with the self-delete and last-SYS_ADMIN guards | `…/user/UserController.java` (one method) | `curl` as a SYS_ADMIN trying to delete themselves returns 400; trying to delete the last SYS_ADMIN returns 400 |
| 16.9 🔒 | Create `UserController` `PATCH /api/users/{id}/roles` with the role-checked rule (ADMIN can only change USER; SYS_ADMIN any) | `…/user/UserController.java` (one method) | `curl` as ADMIN trying to promote a USER to ADMIN returns 403 |
| 16.10 | Write `@SpringBootTest` cases for each endpoint covering success + each failure mode | `…/src/test/…/UserControllerTest.java` | `./gradlew test` is green; the 4 endpoint cases pass |

**Review notes:**

- 10 subtasks, ~8 files. 4 of the 5 controller methods land in `UserController.java`; the skill says one verb per subtask, so each is its own row.
- 🔒 subtasks: 16.3 (audit aspect — writes the row that the rest of the system depends on), 16.7/16.9 (the role-checked creation/promotion rules are the *core* RBAC for users).
- Anti-pattern check: 16.6, 16.7, 16.8, 16.9 are all in the same controller. The skill's anti-pattern warns against bundling, but per-verb is the rule, and each is a different `@PreAuthorize` value. Splitting them is correct.

---

## Task #17 — Backend: Engine CRUD + factory + MOCK impl  ⚙️

**Parent task:** TASKS.md #17. **Skill used:** `task-decomposition`.
**Files the task will touch:** ~9 new files in `…/manager/engine/`.
**Depends on:** #15.
**Decomposed by:** orchestrator. **Date:** Sept 1, 2026.

| # | Subtask | Touches | Done when |
|---|---|---|---|
| 17.1 | Create the `OrderEngineOperations` interface (preserved from v0.2) | `…/engine/OrderEngineOperations.java` | The interface compiles; the 7 methods match SPEC §3.6 |
| 17.2 | Create the `EngineStatus` and `EngineMode` enums | `…/engine/EngineStatus.java`, `…/engine/EngineMode.java` | The enums compile and are referenced by the entity (already in #14) |
| 17.3 | Create `EngineNotSupportedException` (→ 404), `EngineUnreachableException` (→ 502), `EngineAuthException` (→ 403), `EngineScriptException` (→ 502, carries `exitCode` and `stderr`) | `…/engine/*.java` (four files) | All four exceptions compile; mapped to HTTP codes by `ApiExceptionHandler` (created in #18) |
| 17.4 | Create the `LogLine` value type (timestamp, level, message) | `…/engine/LogLine.java` | The type compiles |
| 17.5 | Create `MockEngineOperations` (in-memory state machine; per-engine wrapper via the factory) | `…/engine/impl/MockEngineOperations.java` | A `MOCK` engine's `start()` transitions STOPPED → RUNNING and returns 0; `stop()` does the reverse; `getLogs(100)` returns the last 100 lines from the buffer |
| 17.6 | Create `OrderEngineFactory` (looks up by `code` from `EngineRepository.findByCodeAndDeletedAtIsNull`, throws `EngineNotSupportedException` on miss) | `…/engine/OrderEngineFactory.java` | An unknown code throws → 404; a `MOCK` engine returns a wrapper; a `REAL` engine returns a `SshBackedEngine` (created in #19) |
| 17.7 | Create `EngineService` (CRUD: create, soft-delete with cascade, PATCH ssh; emits `EngineStatusChangedEvent`) | `…/engine/EngineService.java` | A created engine appears in the DB; a soft-deleted engine is no longer returned by the factory; the event fires on status transitions |
| 17.8 | Create `EngineController` with the 4 CRUD endpoints (`GET /api/engines`, `POST /api/engines`, `DELETE /api/engines/{code}`, `PATCH /api/engines/{code}/ssh`) | `…/engine/EngineController.java` | `curl` as SYS_ADMIN can create, soft-delete (204), PATCH ssh; as USER returns 403 on the create/delete/patch and only their assigned engines on `GET` |
| 17.9 | Write `@SpringBootTest` cases for the 4 CRUD endpoints | `…/src/test/…/EngineControllerTest.java` | `./gradlew test` green |

**Review notes:**

- 9 subtasks, ~9 files. The factory is the load-bearing piece — 17.6 — and has no 🔒 marker because the lookup itself is not security-relevant; the *gate* on the controller method is what matters, and that lives in `EngineService` and `@PreAuthorize`.
- No 🔒 in this task: the engine CRUD endpoints are gated by `@PreAuthorize` and `@Audited`, but those annotations land in #18 (the controller methods that need audit). #17 ships the entities and factory; #18 wires the action endpoints and their audit rows.

---

## Task #18 — Backend: engine status/start/stop/logs endpoints  ⚙️

**Parent task:** TASKS.md #18. **Skill used:** `task-decomposition`.
**Files the task will touch:** `…/engine/EngineController.java` (extend from #17), `…/web/ApiExceptionHandler.java` (new), `…/engine/EngineService.java` (extend for the action methods).
**Depends on:** #16, #17.
**Decomposed by:** orchestrator. **Date:** Sept 1, 2026.

| # | Subtask | Touches | Done when |
|---|---|---|---|
| 18.1 | Create `ApiExceptionHandler` (`@ControllerAdvice`) mapping the 4 engine exceptions to their HTTP codes per `ssh-engine-ops.md` | `…/web/ApiExceptionHandler.java` | A thrown `EngineAuthException` produces 403, `EngineUnreachableException` produces 502, `EngineScriptException` produces 502, future-timeout produces 504, all with the standard envelope |
| 18.2 🔒 | Add `GET /api/engines/{code}/status` to `EngineController` with `@PreAuthorize` and the assignedEngines filter for USER | `EngineController.java` (one method) | `curl` as USER for an assigned engine returns 200; for an unassigned engine returns 403 |
| 18.3 🔒 | Add `POST /api/engines/{code}/start` to `EngineController` with `@Audited(action = START_ENGINE, targetEngineFromPath = true)` and `@PreAuthorize` | `EngineController.java` (one method) | A successful start writes a `START_ENGINE` row with `exitCode: 0`; a failed start (e.g. already RUNNING) writes a row with the error in `details` |
| 18.4 🔒 | Add `POST /api/engines/{code}/stop` to `EngineController`, mirror of #18.3 | `EngineController.java` (one method) | Same as #18.3 for `STOP_ENGINE` |
| 18.5 | Add `GET /api/engines/{code}/logs?limit=N` to `EngineController` with validation on the `limit` enum (`{50, 100, 200}`) | `EngineController.java` (one method) | `curl ?limit=42` returns 400; `?limit=100` returns the last 100 lines from `LogBuffer` |
| 18.6 | Write `@SpringBootTest` cases for each of the 4 endpoints with both happy path and the exception-mapping cases | `…/src/test/…/EngineActionsTest.java` | `./gradlew test` green |
| 18.7 | Add `EngineStatusChangedEvent` + Spring `ApplicationEventPublisher` wiring in `EngineService` so the WS handler in #20 can subscribe | `…/engine/EngineService.java` (one method) | A successful start publishes the event; a test listener receives it |

**Review notes:**

- 7 subtasks, 3 files. The 4 controller methods (18.2–18.5) are one verb per subtask per the skill.
- 18.2, 18.3, 18.4 are 🔒 because they handle role gates, audit rows, and SSH-execution error mapping — three of the four security-relevant seams in the skill.
- 18.7 is needed for the WS handler in #20 but is also a clean separation point: the service emits, the handler subscribes later. Better to land it here than to bundle in #20.

---

## Task #19 — Backend: SshBackedEngine + background log tailer  ⚙️

**Parent task:** TASKS.md #19. **Skill used:** `task-decomposition`.
**Files the task will touch:** `…/engine/impl/SshBackedEngine.java` (new), `…/engine/SshClientProvider.java` (new), `…/engine/LogBuffer.java` (new), `…/engine/LogTailerRegistry.java` (new).
**Depends on:** #17, #18.
**Decomposed by:** orchestrator. **Date:** Sept 1, 2026.

| # | Subtask | Touches | Done when |
|---|---|---|---|
| 19.1 🔒 | Create `SshClientProvider`: per-engine cached `SshClient`, idle-evicted at 5 min via a `ScheduledExecutorService`, closed in `@PreDestroy` | `SshClientProvider.java` | Two calls to the same engine within 5 min reuse one `SshClient`; a third call after 6 min opens a new one |
| 19.2 🔒 | Create `SshBackedEngine.status()` with the 5s connect + 5s op timeouts; throws `EngineUnreachableException` on connect fail (with one retry) | `SshBackedEngine.java` (one method) | A `status()` against a reachable host returns `RUNNING`/`STOPPED`; against an unreachable host throws within 5s, then retries once |
| 19.3a 🔒 | Create `SshBackedEngine.start()` with the 30s bound via `Future.get(30, SECONDS)`; throws `EngineScriptException(exitCode, stderr)` on non-zero exit | `SshBackedEngine.java` (one method) | A 60s-hanging start script is cancelled at 30s and returns 504; a start script that exits 127 returns 502 with the stderr in `details` |
| 19.3b 🔒 | Create `SshBackedEngine.stop()` with the same 30s bound; mirror of start for the running → stopped transition | `SshBackedEngine.java` (one method) | A 60s-hanging stop script is cancelled at 30s and returns 504; a stop script that exits 127 returns 502 with the stderr in `details` |
| 19.4 | Create `SshBackedEngine.getLogs(int)` with the 10s bound; returns the last N lines via a single `tail -n N` invocation | `SshBackedEngine.java` (one method) | A request for 100 lines returns 100 lines within 10s on a normal host |
| 19.5 🔒 | Create `LogBuffer`: per-engine `ArrayDeque<LogLine>`, cap 500, oldest evicted on insert | `LogBuffer.java` | Inserting a 501st line evicts the oldest; the buffer is per-engine (two engines don't share) |
| 19.6 | Create the SSH channel reader loop (one method) that reads a `ChannelExec` stdout line-by-line and pushes to the buffer | `SshBackedEngine.java` (one method) | A `tail -F` channel's lines appear in the buffer in real time |
| 19.7 🔒 | Create `LogTailerRegistry` listening to `EngineStatusChangedEvent`: starts a tailer thread on `RUNNING` for `mode=REAL` engines, stops on `STOPPED`/deletion/5 reconnect failures | `LogTailerRegistry.java` | A `RUNNING` REAL engine has a tailer thread; a `STOPPED` engine does not; an engine with 5 consecutive reconnect failures emits a single `WARN` and stops |
| 19.8 | Write integration tests against a `sshd` Maven-embedded server (covers the 4 SSH operations end to end) | `…/src/test/…/SshBackedEngineIT.java` | `./gradlew test` green; the 4 ops are exercised against a real `sshd` instance |

**Review notes:**

- 9 subtasks, 4 files. `SshBackedEngine.java` is the big one — 5 methods land in it.
- 🔒 count is high (6/9) because every SSH call touches credentials and execution: `SshClientProvider` (credential injection), `status`/`start`/`stop` (SSH execution with the 3 error categories — split into 19.2, 19.3a, 19.3b to match the per-verb rule and to let start and stop be reviewed independently — the failure modes for a hanging start script and a hanging stop script differ in how the `LogTailerRegistry` reacts), `LogBuffer` (sensitive output), `LogTailerRegistry` (lifecycle of the credentialed thread).
- 19.6 is *not* 🔒 because it's the data pump — it reads a channel and pushes bytes; no credential or RBAC decision lives here.

---

## Task #20 — Backend: WebSocket logs/stream handler  ⚙️

**Parent task:** TASKS.md #20. **Skill used:** `task-decomposition`.
**Files the task will touch:** `…/engine/ws/EngineLogsWebSocketHandler.java` (new), `…/engine/ws/WebSocketSessionRegistry.java` (new), `…/web/WebSocketConfig.java` (new), `…/config/SecurityConfig.java` (extend from #15 to allow the WS handshake path with JWT).
**Depends on:** #15, #18, #19.
**Decomposed by:** orchestrator. **Date:** Sept 1, 2026.

| # | Subtask | Touches | Done when |
|---|---|---|---|
| 20.1 | Add `spring-boot-starter-websocket` to `build.gradle` (already on the classpath from #14) and the `WebSocketConfigurer` bean | `WebSocketConfig.java` (new) | A client can open a WebSocket to the configured path |
| 20.2 | Create `WebSocketSessionRegistry`: per-engine set of sessions, `add(code, session)`, `remove(code, session)`, `broadcast(code, line)` | `WebSocketSessionRegistry.java` | A line broadcast to engine `bpl` reaches all sessions registered for `bpl` and no others |
| 20.3 🔒 | Create `EngineLogsWebSocketHandler.afterConnectionEstablished`: snapshot last 100 lines from `LogBuffer`, send them as JSON, register the session | `EngineLogsWebSocketHandler.java` (one method) | A new connection receives the snapshot before any live line |
| 20.4 | Create `EngineLogsWebSocketHandler.handleMessage` (client pings; currently no-op beyond keep-alive) | `EngineLogsWebSocketHandler.java` (one method) | Client pings don't crash; the server sends no data in response (snapshot is on connect) |
| 20.5 | Create `EngineLogsWebSocketHandler.afterConnectionClosed`: remove the session from the registry | `EngineLogsWebSocketHandler.java` (one method) | A closed client is no longer in the registry; subsequent broadcasts don't reach it |
| 20.6 🔒 | JWT auth on the WebSocket handshake via the existing `JwtAuthFilter`: extend `SecurityConfig` to require authentication for the WS path and reject the handshake with 401 if the token is missing/invalid | `SecurityConfig.java` (extend), `WebSocketConfig.java` (wire) | A WS handshake without a token returns 401; one with a valid token proceeds |
| 20.7 | Role + assignment gate on the WS path: reject with 403 if the caller is a USER without the engine in their `assignedEngines` | `EngineLogsWebSocketHandler.java` (one method) | A USER's WS connection to a non-assigned engine is rejected with 403; a USER's connection to an assigned engine succeeds |
| 20.8 | Wire the `LogTailerRegistry` (from #19) to also push into the WS session registry: every tailer line is broadcast to live sessions AND pushed to the buffer | `LogTailerRegistry.java` (extend) | A live `tail -F` line appears in both the buffer and every connected viewer within 100ms |

**Review notes:**

- 8 subtasks, 4 files. The 4 methods of `EngineLogsWebSocketHandler` are split per the skill's "one verb per subtask."
- 🔒: 20.3 (snapshot reveal — the buffer may contain content the user shouldn't see if RBAC is wrong) and 20.6 (WS handshake auth — credentialed path).
- 20.4 and 20.5 are the boring lifecycle plumbing; no 🔒.

---

## Task #21 — Backend: /api/audit-logs endpoint  ⚙️

**Parent task:** TASKS.md #21. **Skill used:** `task-decomposition`.
**Files the task will touch:** `…/audit/AuditLogController.java` (new), `…/audit/AuditService.java` (new), `…/audit/dto/AuditLogResponse.java` (new).
**Depends on:** #16.
**Decomposed by:** orchestrator. **Date:** Sept 1, 2026.

| # | Subtask | Touches | Done when |
|---|---|---|---|
| 21.1 | Create `AuditLogResponse` DTO with `details` as a parsed `Map<String, Object>` (not a string) | `…/audit/dto/AuditLogResponse.java` | The DTO compiles; `details` round-trips as a JSON object |
| 21.2 🔒 | Create `AuditService` query method: filters by `actor`, `action`, `engine` (resolves code to UUID), `from`/`to` (Instant), with paginated `Pageable` | `AuditService.java` | A query with all 5 filters returns the expected subset; an out-of-range `from` after `to` returns 400 |
| 21.3 🔒 | Create `AuditLogController.GET /api/audit-logs` with `@PreAuthorize("hasAnyRole('SYS_ADMIN', 'ADMIN')")` — USER is rejected outright, not filtered | `AuditLogController.java` (one method) | `curl` as USER returns 403; as ADMIN returns the full system log |
| 21.4 | Write `@SpringBootTest` cases for the 3 filter dimensions and the USER→403 case | `…/src/test/…/AuditLogControllerTest.java` | `./gradlew test` green |

**Review notes:**

- 4 subtasks, 3 files.
- 21.2 and 21.3 are 🔒. 21.2 because the `engine` filter resolves a *code* to a UUID — if the lookup is wrong, the query returns audit rows for the wrong engine. 21.3 because the RBAC here is the *USER→403* contract from #11.

---

## Task #22 — Backend: build + test pass + DB up  ✅

**Parent task:** TASKS.md #22. **Skill used:** `task-decomposition`.
**Files the task will touch:** `docker-compose.yml` (new), no app code. Verification only.
**Depends on:** #14–#21.
**Decomposed by:** orchestrator. **Date:** Sept 1, 2026.

| # | Subtask | Touches | Done when |
|---|---|---|---|
| 22.1 | Create `docker-compose.yml` for local Postgres (`postgres:16-alpine`, port 5432, volume for data) | `docker-compose.yml` (new) | `docker compose up -d` brings up Postgres; `psql` connects |
| 22.2 | Set the env vars in a `.env.local` (gitignored) for the local run: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `JASYPT_ENCRYPTOR_PASSWORD` | `.env.local` (new, gitignored) | `source .env.local && ./gradlew bootRun` starts the app without missing-env errors |
| 22.3 | `./gradlew compileJava` clean | (no file) | Exit 0 |
| 22.4 | `./gradlew test` clean (all backend test classes from #15–#21) | (no file) | All tests pass |
| 22.5 | `./gradlew bootRun` starts; verify `GET /actuator/health` is `UP` | (no file) | `curl /actuator/health` returns `{"status":"UP"}` |
| 22.6 | Curl as SYS_ADMIN: every endpoint returns the expected 2xx; verify `@Audited` writes one `AuditLog` row per state change | (no file) | The `audit_log` table has the expected rows after the curl session |
| 22.7 | Curl as ADMIN: full audit access; can create only USER-role users; cannot delete another ADMIN | (no file) | `curl -H "Authorization: Bearer $ADMIN_JWT" /api/audit-logs` returns 200 with rows; `curl -X POST -H "Authorization: Bearer $ADMIN_JWT" /api/users -d '{"roleType":"ADMIN",...}'` returns 403; `curl -X DELETE -H "Authorization: Bearer $ADMIN_JWT" /api/users/{adminTargetId}` returns 403; `curl -X POST -H "Authorization: Bearer $ADMIN_JWT" /api/users -d '{"roleType":"USER",...}'` returns 201 |
| 22.8 | Curl as USER: 403 on `/api/audit-logs` (per #11); empty engine list if no assignments; only assigned engines visible | (no file) | `curl -H "Authorization: Bearer $USER_JWT" /api/audit-logs` returns 403; with no assignments, `curl -H "Authorization: Bearer $USER_JWT" /api/engines` returns `[]`; with one assignment, the same call returns exactly that one engine row |
| 22.9 | WebSocket handshake with a USER JWT for an assigned engine returns 200 + initial snapshot; for an unassigned engine returns 403 | (no file) | `wscat -c "ws://localhost:8080/ws/logs/{assignedCode}" -H "Authorization: Bearer $USER_JWT"` (header-based auth per SPEC §4.3) accepts and delivers a snapshot JSON frame; the same handshake against an unassigned engine code returns 403 |
| 22.10 | `qa-reviewer` pass on the full backend diff (#14–#21) | (no file) | qa-reviewer reports no `blocker` findings |

**Review notes:**

- 10 subtasks, 1 new file. The first two are setup; the next two are build; the next four are the curl smoke; the last is the review.
- No 🔒. The verification *is* the gate; the curl commands in 22.6–22.9 are the audit of the security seams.

---

## Task #23 — Frontend: AuthContext + api/client + router shell  🎨

**Parent task:** TASKS.md #23. **Skill used:** `task-decomposition`.
**Files the task will touch:** `BPL-Order_Engine-Admin_ui/src/auth/AuthContext.tsx`, `…/src/api/client.ts`, `…/src/App.tsx`, `…/src/components/AppShell.tsx`, `…/src/main.tsx`.
**Depends on:** #15.
**Decomposed by:** orchestrator. **Date:** Sept 1, 2026.

| # | Subtask | Touches | Done when |
|---|---|---|---|
| 23.1 🔒 | Create `api/client.ts`: `fetch` wrapper with `Authorization: Bearer …`, 401 → clear token + redirect to `/login`, error envelope unwrap to thrown `Error(message)` | `src/api/client.ts` | A request with a valid token succeeds; one with an expired token redirects to `/login`; the thrown `Error` message matches the server's `message` field |
| 23.2 🔒 | Create `AuthContext`: state `{ user, token, mustChangePassword, isLoading }`; on mount, validate token via `GET /api/auth/me`; on 401, clear + redirect | `src/auth/AuthContext.tsx` | The context provides the user; an expired token clears the user and redirects; a fresh token hydrates the user |
| 23.3 | Wire `AuthContext.login(username, password)`: POST `/api/auth/login`, store token, set user | `src/auth/AuthContext.tsx` (one method) | A successful login sets `user` and `token`; an invalid login throws an `Error("Invalid credentials")` |
| 23.4 | Wire `AuthContext.logout()`: clear token, clear user, redirect to `/login` | `src/auth/AuthContext.tsx` (one method) | A click on the logout button redirects to `/login` and the next protected-route render bounces back to `/login` |
| 23.5 | Create `App.tsx` with the React Router routes: `/login`, `/dashboard`, `/logs`, `/admin` (lazy for non-USER), `/change-password`, `/404`, `/403` | `src/App.tsx` | All 7 routes resolve; `/admin` is not in the bundle when the user role is USER (verified by inspecting the build output) |
| 23.6 | Create `AppShell` (top bar with role badge + logout button) used by all authenticated pages | `src/components/AppShell.tsx` | A logged-in SYS_ADMIN sees the admin link; a USER does not; both see their role badge |
| 23.7 | `npm run build` clean; `npm run lint` clean | (no file) | Build passes; no lint errors |

**Review notes:**

- 7 subtasks, 5 files.
- 🔒: 23.1 (token in `Authorization` header) and 23.2 (the auth context itself — the source of truth for who's logged in).
- 23.5's bundle check for `/admin` is the skill's "Admin Panel not in the bundle for USER role" rule from `frontend-agent.md`.

---

## Task #24 — Frontend: Login page  🎨

**Parent task:** TASKS.md #24. **Skill used:** `task-decomposition`.
**Files the task will touch:** `src/pages/Login.tsx` (new), `src/auth/AuthContext.tsx` (extend the redirect logic).
**Depends on:** #23.
**Decomposed by:** orchestrator. **Date:** Sept 1, 2026.

| # | Subtask | Touches | Done when |
|---|---|---|---|
| 24.1 | Create `Login.tsx`: username + password form, submit handler, inline error display | `src/pages/Login.tsx` | The form renders; submit triggers `AuthContext.login`; an error renders inline |
| 24.2 | Wire the post-login redirect: `/change-password` if `mustChangePassword = true`, else `/dashboard` | `Login.tsx` (or `AuthContext.login`) | A login where the server returns `mustChangePassword: true` navigates to `/change-password`; one with `false` goes to `/dashboard` |
| 24.3 🔒 | Inline "Invalid credentials" on 401, with no field-level enumeration | `Login.tsx` | A bad password shows a single message; a missing user shows the same message |
| 24.4 | `npm run build` clean | (no file) | Build passes |

**Review notes:**

- 4 subtasks, 2 files.
- 24.3 is 🔒: the response must not enumerate which field was wrong (defense against username enumeration via timing or wording).

---

## Task #24a — Frontend: ChangePassword page  🎨

**Parent task:** TASKS.md #24a. **Skill used:** `task-decomposition`.
**Files the task will touch:** `src/pages/ChangePassword.tsx` (new), `src/App.tsx` (route registration), `src/auth/AuthContext.tsx` (extend the user object's `mustChangePassword`).
**Depends on:** #15, #23.
**Decomposed by:** orchestrator. **Date:** Sept 1, 2026.

| # | Subtask | Touches | Done when |
|---|---|---|---|
| 24a.1 🔒 | Create `ChangePassword.tsx` form: current password, new password, confirm new password, with client-side "new matches confirm" check | `src/pages/ChangePassword.tsx` | The form renders; mismatched confirm shows an inline error before submit |
| 24a.2 🔒 | Wire submit to `POST /api/auth/change-password`; on success, store the new JWT, clear `mustChangePassword`, redirect to `/dashboard` | `ChangePassword.tsx` (one method) | A successful change navigates to `/dashboard`; a 401 (bad current) shows the right error; a 422 shows the validation message |
| 24a.3 | Register the `/change-password` route in `App.tsx` (authenticated-only) | `src/App.tsx` (one line) | An unauthenticated visit to `/change-password` redirects to `/login`; an authenticated visit with `mustChangePassword = true` renders the form |
| 24a.4 | `npm run build` clean | (no file) | Build passes |

**Review notes:**

- 4 subtasks, 3 files.
- 🔒: 24a.1 (password fields with weak-validation surface) and 24a.2 (the credential-mutation endpoint).

---

## Task #25 — Frontend: Dashboard with EngineCards + WS + polling  🎨

**Parent task:** TASKS.md #25. **Skill used:** `task-decomposition`.
**Files the task will touch:** `src/pages/Dashboard.tsx`, `src/components/EngineCard.tsx`, `src/components/StatusPill.tsx`, `src/hooks/useEngineLogsSocket.ts`, `src/hooks/useEngineStatus.ts`.
**Depends on:** #23, #18, #19, #20.
**Decomposed by:** orchestrator. **Date:** Sept 1, 2026.

| # | Subtask | Touches | Done when |
|---|---|---|---|
| 25.1 | Create `useEngineLogsSocket` hook: WS connect with JWT, exponential backoff reconnect (1s → 30s cap), clean teardown on unmount | `src/hooks/useEngineLogsSocket.ts` | A closed network reconnects with backoff; unmounting the component closes the socket |
| 25.2 | Create `useEngineStatus` hook: WS primary, polling fallback (every 5s) when WS is closed | `src/hooks/useEngineStatus.ts` | With WS open, the hook reports the live status; with WS closed, it polls and reports every 5s |
| 25.3 | Create `StatusPill` component (color-coded pill: green=RUNNING, gray=STOPPED, red=ERROR) | `src/components/StatusPill.tsx` | The pill renders the right color for each status |
| 25.4 | Create `EngineCard` component: name, code, `StatusPill`, `lastTransitionAt`, Start/Stop buttons (role-gated, in-flight disabled) | `src/components/EngineCard.tsx` | A USER sees Start/Stop only for assigned engines; ADMIN/SYS_ADMIN see them for all visible; in-flight buttons are disabled |
| 25.5 | Create `Dashboard.tsx`: grid of `EngineCard` per visible engine, filtered by `currentUser.assignedEngines`; SYS_ADMIN sees "+ Add Engine" | `src/pages/Dashboard.tsx` | The dashboard shows the right cards for each role; the "+ Add Engine" button is SYS_ADMIN-only |
| 25.6 | Wire `EngineCard` to `useEngineStatus` and to the start/stop `POST` actions | `src/components/EngineCard.tsx` (extend) | A click on Start transitions the card to RUNNING within 5s; a failure surfaces the error message |
| 25.7 🔒 | Verify the dashboard never renders a card for an engine the user is not assigned to (defense in depth — the server filters, but the UI must not flash unassigned engines during the role transition) | `src/pages/Dashboard.tsx` (one filter line) | A USER with no assignments sees an empty state, not a 403 error; a USER who loses assignment mid-session doesn't see the engine card on next render |
| 25.8 | `npm run build` clean; `npm run lint` clean | (no file) | Build passes |

**Review notes:**

- 8 subtasks, 5 files. The two hooks are the reusable bits.
- 🔒: 25.7 (defense in depth on the engine list filter). The server is the source of truth, but the UI filter is a defense against timing/loading-state leaks.

---

## Task #26 — Frontend: Logs page with both filter types  🎨

**Parent task:** TASKS.md #26. **Skill used:** `task-decomposition`.
**Files the task will touch:** `src/pages/Logs.tsx`, `src/components/AuditLogsTable.tsx`, `src/components/EngineLogsTable.tsx`.
**Depends on:** #21, #23, #25.
**Decomposed by:** orchestrator. **Date:** Sept 1, 2026.

| # | Subtask | Touches | Done when |
|---|---|---|---|
| 26.1 | Create `AuditLogsTable` component: paginated table with `actor`, `action`, `targetEngineCode`, `timestamp`, `details` (raw-JSON toggle) | `src/components/AuditLogsTable.tsx` | A page of audit logs renders; the raw-JSON toggle shows the parsed `details` object |
| 26.2 | Create `EngineLogsTable` component: virtualized list of `LogLine`s, fed by both the initial `GET /api/engines/{code}/logs?limit=100` snapshot and the WS stream | `src/components/EngineLogsTable.tsx` | The table shows the initial 100 lines, then live updates as the WS pushes; no duplicates from the snapshot + first live lines |
| 26.3 🔒 | Filter the Source dropdown: USER sees only "Engine Execution Logs"; ADMIN/SYS_ADMIN see both options | `src/pages/Logs.tsx` | A USER visiting `/logs` cannot select "System Audit Logs" — the option is not in the dropdown |
| 26.4 | Filter dropdown for Engine: shows all visible engines; selecting one switches the table to engine execution logs | `src/pages/Logs.tsx` | Selecting engine `bpl` switches the table to its `LogBuffer` + WS |
| 26.5 | Query params for audit logs (`actor`, `action`, `engine`, `from`, `to`, `page`, `size`) wired to filter controls | `src/pages/Logs.tsx` | Setting a filter updates the URL query and the table |
| 26.6 | Wire the WS subscription for the selected engine: when the user changes engine, close the old socket and open a new one | `src/pages/Logs.tsx` (one effect) | Switching engines closes the old socket and opens a new one; the table switches without orphaning the old subscription |
| 26.7 | `npm run build` clean; `npm run lint` clean | (no file) | Build passes |

**Review notes:**

- 7 subtasks, 3 files.
- 26.3 is 🔒: the Source dropdown for USER is the UI half of the contract from #11. The server also returns 403, but the dropdown filter prevents the user from even trying.

---

## Task #27 — Frontend: Admin Panel (Users + Engines tabs)  🎨

**Parent task:** TASKS.md #27. **Skill used:** `task-decomposition`.
**Files the task will touch:** `src/pages/Admin.tsx`, `src/components/UserForm.tsx`, `src/components/EngineForm.tsx`.
**Depends on:** #16, #17, #23.
**Decomposed by:** orchestrator. **Date:** Sept 1, 2026.

| # | Subtask | Touches | Done when |
|---|---|---|---|
| 27.1 | Create `Admin.tsx` with two tabs (Users, Engines); route is lazy-imported for non-USER | `src/pages/Admin.tsx` | The page renders; the Users tab is the default; the Engines tab is SYS_ADMIN-only |
| 27.2 | Create the Users tab: table with `username`, `role`, `assignedEngineCodes`, [Edit] [Delete]; the table is the same for ADMIN and SYS_ADMIN, with row-action restrictions | `src/pages/Admin.tsx` (one component) | A USER row's [Edit]/[Delete] is enabled for both ADMIN and SYS_ADMIN; an ADMIN/SYS_ADMIN row's [Edit]/[Delete] is enabled only for SYS_ADMIN |
| 27.3 | Create `UserForm.tsx`: username, password, role (constrained by caller's role per SPEC §3.1), assignedEngines multi-select; used for both Add and Edit | `src/components/UserForm.tsx` | An ADMIN's role select does not include `ADMIN` or `SYS_ADMIN`; a SYS_ADMIN's does |
| 27.4 | Create the Engines tab (SYS_ADMIN only): table with `name`, `code`, `mode`, `serverIp`, [Edit SSH] [Delete] | `src/pages/Admin.tsx` (one component) | ADMIN sees the tab as read-only; SYS_ADMIN sees the full actions |
| 27.5 | Create `EngineForm.tsx`: name, code, mode, serverIp, serverUsername, serverPassword (write-only), startScript, stopScript, logScript; used for both Add and Edit | `src/components/EngineForm.tsx` | The form sends a `POST` on Add and a `PATCH` on Edit; the password field is a write-only text input |
| 27.6 | Wire the [Delete] actions to soft-delete with a confirm dialog (engines) or a hard delete with a confirm dialog (users) | `src/pages/Admin.tsx` | A click on [Delete] opens a confirm dialog; on confirm, the row disappears and a refresh shows the new state |
| 27.7 🔒 | Ensure the serverPassword is **never** returned in the response that populates the form (no `defaultValue` from a fetched row, since the field doesn't exist on the DTO) | `src/components/EngineForm.tsx` | Opening [Edit SSH] on an existing engine shows the existing IP/username/scripts but an empty password field — the user must retype it to change it |
| 27.8 | Wire the "+ Add Engine" button on the Dashboard to open the same `EngineForm` for creation | `src/pages/Dashboard.tsx` (extend) | A click on "+ Add Engine" opens the form modal; submit creates the engine and refreshes the dashboard |
| 27.9 | `npm run build` clean; `npm run lint` clean | (no file) | Build passes |

**Review notes:**

- 9 subtasks, 4 files.
- 27.7 is 🔒: the password field on the Edit form. The server never returns the password, so the UI must reflect that. Showing a stale or empty-but-populated field would mislead the user.

---

## Task #28 — Frontend: build + manual role smoke + screenshots  ✅

**Parent task:** TASKS.md #28. **Skill used:** `task-decomposition`.
**Files the task will touch:** no new files; verification only. Screenshots go to `docs/screenshots/`.
**Depends on:** #23–#27.
**Decomposed by:** orchestrator. **Date:** Sept 1, 2026.

| # | Subtask | Touches | Done when |
|---|---|---|---|
| 28.1 | `npm run build` clean | (no file) | Build passes |
| 28.2 | `npm run lint` clean | (no file) | Lint passes |
| 28.3 | Manual smoke as SYS_ADMIN: every page (Dashboard, Logs, Admin → Users, Admin → Engines) renders and the expected actions work | (no file) | All 4 pages render; adding an engine, adding a user, assigning a user all work end-to-end |
| 28.4 | Manual smoke as ADMIN: Admin Panel visible, Engines tab is read-only, Users tab works for USER-role rows only | (no file) | ADMIN cannot create ADMIN/SYS_ADMIN; the engine SSH fields are not editable |
| 28.5 | Manual smoke as USER: Admin Panel is not in the bundle (verified by `npm run build && grep`); Logs page Source dropdown hides "System Audit Logs"; only assigned engines appear on the dashboard | (no file) | The build output for USER's bundle does not include the admin chunk; the Logs Source dropdown has one option |
| 28.6 | Screenshots: capture Login, Dashboard (SYS_ADMIN), Dashboard (USER with assigned engines), Logs (audit view), Logs (engine view), Admin → Users, Admin → Engines (Add modal) | `docs/screenshots/*.png` (7 files) | 7 PNGs in `docs/screenshots/` matching the slide deck layout |
| 28.7 | `qa-reviewer` pass on the full frontend diff (#23–#27) | (no file) | qa-reviewer reports no `blocker` findings |

**Review notes:**

- 7 subtasks, 7 screenshot files.
- No 🔒. The smoke is the gate; the screenshots are the deliverable for the slide deck.

---

## Summary

| Task | Subtasks | 🔒 count |
|---|---:|---:|
| #11 | 5 | 0 |
| #12 | 3 | 1 |
| #13 | 4 | 0 |
| #14a | 6 | 1 |
| #14 | 8 | 0 |
| #14b | 3 | 1 |
| #15 | 15 | 7 |
| #16 | 10 | 3 |
| #17 | 9 | 0 |
| #18 | 7 | 3 |
| #19 | 9 | 6 |
| #20 | 8 | 2 |
| #21 | 4 | 2 |
| #22 | 10 | 0 |
| #23 | 7 | 2 |
| #24 | 4 | 1 |
| #24a | 4 | 2 |
| #25 | 8 | 1 |
| #26 | 7 | 1 |
| #27 | 9 | 1 |
| #28 | 7 | 0 |
| **Total** | **147** | **34** |

The `task-decomposition` skill is in `.claude/skills/task-decomposition/SKILL.md`; the agent loaders know to use it. Every TASKS.md task has a subtask table here. Each subtask has a `done when` that's paste-able into a terminal or test file.

When you approve a task, the subagent walks the table, reports when done. After the task, qa-reviewer cross-checks the implementation against the planned subtasks.

