# v0.3 Build — Task List

Source: SPEC.md (v0.3), instruction.md, rbac-redesign-spec.md.
18 tasks. Run in order. Each task is a checkpoint; review between any two.

Legend: 📄 = doc-only, ⚙️ = backend, 🎨 = frontend, ✅ = verification.

| # | Layer | Task | Done |
|---|---|---|:---:|
| 11 | 📄 | SPEC.md — apply USER audit log fix | ☐ |
| 12 | ⚙️ | qa-reviewer — add USER→403 audit log check | ☐ |
| 13 | 📄 | Rewrite rbac-redesign-spec.md as resolution doc | ☐ |
| 14 | ⚙️ | Backend foundation: Gradle deps + Flyway V1__init.sql + 3 JPA entities | ☐ |
| 15 | ⚙️ | Backend: repositories + SecurityConfig + JWT filter + login | ☐ |
| 16 | ⚙️ | Backend: User CRUD endpoints + @Audited + AuditAspect | ☐ |
| 17 | ⚙️ | Backend: Engine CRUD + factory + MOCK impl | ☐ |
| 18 | ⚙️ | Backend: engine status/start/stop/logs endpoints | ☐ |
| 19 | ⚙️ | Backend: SshBackedEngine + background log tailer | ☐ |
| 20 | ⚙️ | Backend: WebSocket logs/stream handler | ☐ |
| 21 | ⚙️ | Backend: /api/audit-logs endpoint | ☐ |
| 22 | ✅ | Backend: build + test pass + DB up | ☐ |
| 23 | 🎨 | Frontend: AuthContext + api/client + router shell | ☐ |
| 24 | 🎨 | Frontend: Login page | ☐ |
| 25 | 🎨 | Frontend: Dashboard with EngineCards + WS + polling | ☐ |
| 26 | 🎨 | Frontend: Logs page with both filter types | ☐ |
| 27 | 🎨 | Frontend: Admin Panel (Users + Engines tabs) | ☐ |
| 28 | ✅ | Frontend: build + manual role smoke + screenshots | ☐ |

---

## 11. SPEC.md — apply USER audit log fix  📄

**Scope:** `SPEC.md` only.

- §3.1 RBAC matrix, `View audit logs` row: change `🔒 assigned engines only` (USER column) to `❌`.
- §4.5 `GET /api/audit-logs`: change title from "depends on role" to "`SYS_ADMIN`, `ADMIN` only". USER gets **403**. The audit log is admin-only; USER sees engine execution logs via `/api/engines/{code}/logs` and the WebSocket stream, but never the audit trail.
- §5.4 Logs page: the Source dropdown hides "System Audit Logs" for USER. USER sees only "Engine Execution Logs".
- §5.1 routes: `/logs` stays "all authenticated" but with a note that USER sees a reduced view.

## 12. qa-reviewer — add USER→403 audit log check  ⚙️ (agent config)

**Scope:** `.claude/agents/qa-reviewer.md`.

Add a check: `GET /api/audit-logs` with USER role must return 403. The query must not filter by `assignedEngines` (USER is rejected outright, not partially filtered). Cross-reference the new §4.5 wording. Severity: `blocker` if the endpoint leaks audit rows to USER.

## 13. Rewrite rbac-redesign-spec.md as resolution doc  📄

**Scope:** `rbac-redesign-spec.md` (full rewrite).

- Mark the original sketch as historical (Phase 2 design, transcribed Aug/Sep 2026).
- For each of the 6 open questions in the sketch's §6.7, record the v0.3 decision:
  1. Admin's reach is always global — the crossed-out per-engine scoping was discarded.
  2. Admin cannot create other Admins; only SYS_ADMIN can.
  3. A grant is always "start/stop + log" — no log-view-only role in v0.3.
  4. Sysadmin-authored scripts are arbitrary text, sanity-checked (no `; rm`, etc.) but not sandboxed.
  5. Credentials are Jasypt-encrypted at rest; master password from env. Not plaintext, not key-based.
  6. Compiled-in (MOCK) and scripted (REAL) engines coexist via `Engine.mode`.
- Explain the USER-audit decision in context (USER sees engine execution logs but never the audit log).
- The file becomes "what was decided and why," not a parallel spec.

## 14. Backend foundation: Gradle deps + Flyway V1__init.sql + 3 JPA entities  ⚙️

**Scope:** `BPL-Order-Engine-Admin-backend/`.

- `build.gradle`: full v0.3 deps (data-jpa, validation, websocket, flyway, postgresql, jjwt, jasypt, sshd, lombok). Use the artifact list in SPEC §2.4.
- `src/main/resources/db/migration/V1__init.sql`: create `users`, `engines`, `audit_log`, `user_engine_access`. UUIDs as `uuid` type; `audit_log.details` as `jsonb`; indexes on `audit_log.timestamp`, `audit_log.actorUsername`, `audit_log.targetEngineCode`.
- Three JPA entities per `jpa-entity-patterns.md`: `User`, `Engine`, `AuditLog`. UUID PKs, `@Version` on the first two, `@Getter`/`@Setter` (not `@Data`), `LAZY` on to-many.
- No repositories, services, or controllers yet. This task ends with `./gradlew compileJava` green.

## 15. Backend: repositories + SecurityConfig + JWT filter + login  ⚙️

**Scope:** backend, auth + security layer.

- `UserRepository`, `EngineRepository`, `AuditLogRepository` (interface only).
- `SecurityConfig`: JWT filter, JPA `UserDetailsService`, `@PreAuthorize` enabled, role hierarchy.
- `JasyptConfig`: `StringEncryptor` bean from `JASYPT_ENCRYPTOR_PASSWORD` env var.
- `CorsConfig`, `JacksonConfig`.
- `JwtService` (sign/validate), `JwtAuthFilter`, `UserPrincipal` (`UserDetails` wrapping `User`).
- `AuthController`: `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`.
- `AuthenticationSuccessListener` and `AuthenticationFailureListener` for `LOGIN_SUCCESS`/`LOGIN_FAIL` audit rows.
- `V2__seed_admin.sql` (dev profile only): one SYS_ADMIN with a known BCrypt-hashed password.
- `./gradlew test` green with a `@SpringBootTest` smoke.

## 16. Backend: User CRUD endpoints + @Audited + AuditAspect  ⚙️

**Scope:** backend, user management.

- `@Audited` annotation + `AuditAspect` (AOP). The aspect extracts actor from security context, resolves target engine from path, writes `AuditLog` on success and on exception.
- `UserService`, `UserController`.
- Endpoints per SPEC §4.4: `GET /api/users`, `POST /api/users` (role-checked: SYS_ADMIN any, ADMIN USER only), `DELETE /api/users/{id}` (self-delete rejected, last-SYS_ADMIN delete rejected), `PATCH /api/users/{id}/roles`.
- Validation on request DTOs. Standard error envelope on every failure.

## 17. Backend: Engine CRUD + factory + MOCK impl  ⚙️

**Scope:** backend, engine layer base.

- `EngineService`, `EngineController`. CRUD per SPEC §4.3: `GET /api/engines` (filtered by caller role), `POST /api/engines`, `DELETE /api/engines/{code}` (soft-delete + cascade `user_engine_access`), `PATCH /api/engines/{code}/ssh`.
- `OrderEngineFactory` looks up by `code` from `EngineRepository.findByCodeAndDeletedAtIsNull`.
- `MockEngineOperations` impl (refactor of v0.2's in-memory state machine, per-engine wrapper).
- `EngineService` emits `EngineStatusChangedEvent` on transitions.

## 18. Backend: engine status/start/stop/logs endpoints  ⚙️

**Scope:** backend, engine control surface.

- `GET /api/engines/{code}/status`, `POST /api/engines/{code}/start`, `POST /api/engines/{code}/stop`, `GET /api/engines/{code}/logs?limit=N`.
- `@PreAuthorize` role-checked, query-layer filter by `USER.assignedEngines`.
- `@Audited` on start/stop for success AND failure paths.
- Error mapping per `ssh-engine-ops.md`: `EngineAuthException`→403, `EngineUnreachableException`→502, `EngineScriptException`→502, future timeout→504.

## 19. Backend: SshBackedEngine + background log tailer  ⚙️

**Scope:** backend, REAL-mode engine impl.

- `SshClientProvider`: per-engine cached `SshClient`, idle-evicted at 5 min, closed on app shutdown.
- `SshBackedEngine` impl: the three error categories, bounded timeouts (5s connect, 30s start/stop, 10s on-demand logs).
- `LogBuffer`: per-engine `ArrayDeque<LogLine>`, cap 500.
- `LogTailerRegistry`: one thread per `RUNNING` `mode=REAL` engine. Exponential backoff reconnect, capped 60s. Starts on `EngineStatusChangedEvent(RUNNING)`, stops on `STOPPED`/deletion/5 consecutive failures.

## 20. Backend: WebSocket logs/stream handler  ⚙️

**Scope:** backend, real-time logs.

- `spring-boot-starter-websocket` already on the classpath from #14.
- `WebSocketConfig` with `EngineLogsWebSocketHandler` at `/api/engines/{code}/logs/stream`.
- JWT auth on handshake via the existing `JwtAuthFilter`. Token in `Authorization: Bearer …` header (not query param).
- `WebSocketSessionRegistry`: per-engine set of sessions.
- On connect: snapshot of last 100 lines from `LogBuffer`, then live updates from `LogTailerRegistry`.
- On `STOPPED`/engine deletion: send `{"event": "closed", "reason": "engine_stopped" | "engine_deleted"}` and close.
- Same ROLE and assignment gates as the HTTP endpoints.

## 21. Backend: /api/audit-logs endpoint  ⚙️

**Scope:** backend, audit read.

- `GET /api/audit-logs` with query params `actor`, `action`, `engine`, `from`, `to`, `page`, `size` per SPEC §4.5.
- `SYS_ADMIN`/`ADMIN`: full access.
- `USER`: **403** (rejected outright, not filtered).
- `details` returned as parsed JSON object, not a string.

## 22. Backend: build + test pass + DB up  ✅

**Scope:** backend verification.

- `./gradlew compileJava`, `./gradlew test`, `./gradlew bootRun` with a local Postgres (docker compose).
- Curl every endpoint as the seeded `SYS_ADMIN`, then `ADMIN`, then `USER`. Verify the RBAC matrix.
- Verify `@Audited` writes rows for every state change.
- Verify the WebSocket handshake + initial snapshot + live update.
- `qa-reviewer` pass on the full backend diff.

## 23. Frontend: AuthContext + api/client + router shell  🎨

**Scope:** `BPL-Order_Engine-Admin_ui/src/`.

- `AuthContext`: `{ user, token, login, logout, isLoading }`. On mount, validate token via `GET /api/auth/me`, hydrate user. On 401, clear + redirect to `/login`.
- `api/client.ts`: `fetch` wrapper with `Authorization: Bearer …`, 401 → redirect, error envelope unwrap to thrown `Error(message)`.
- React Router root: `/login`, `/dashboard`, `/logs`, `/admin` (lazy for non-USER), `/404`, `/403`.
- `AppShell`: top bar with role badge + logout button.

## 24. Frontend: Login page  🎨

**Scope:** frontend, login flow.

- Per SPEC §5.2. Username + password form.
- POST `/api/auth/login`, store JWT in `localStorage`, redirect to `/dashboard`.
- Inline "Invalid credentials" on 401.
- Token validation on mount via `GET /api/auth/me` (handled by `AuthContext`).

## 25. Frontend: Dashboard with EngineCards + WS + polling  🎨

**Scope:** frontend, engine dashboard.

- Per SPEC §5.3. Grid of `EngineCard` per visible engine, filtered by `currentUser.assignedEngines`.
- `StatusPill`, `lastTransitionAt`, Start/Stop buttons (role-gated, in-flight disabled).
- `useEngineLogsSocket` hook: exponential backoff reconnect (1s → 30s), clean teardown on unmount.
- `useEngineStatus` hook: WebSocket primary, polling (every 5s) fallback when WS closed.
- SYS_ADMIN sees "+ Add Engine" button.

## 26. Frontend: Logs page with both filter types  🎨

**Scope:** frontend, logs view.

- Per SPEC §5.4. Two filter dropdowns: **Source** (`System Audit Logs` | `Engine Execution Logs`) and **Engine**.
- For USER, the Source dropdown hides "System Audit Logs" — only "Engine Execution Logs" is selectable.
- Audit logs via `GET /api/audit-logs?actor=…&action=…&engine=…&from=…&to=…&page=…&size=…`.
- Engine execution logs via `GET /api/engines/{code}/logs?limit=100` + the WebSocket stream.
- Paginated table, "View raw JSON" toggle.

## 27. Frontend: Admin Panel (Users + Engines tabs)  🎨

**Scope:** frontend, admin surface.

- Per SPEC §5.5. Two tabs: **Users** and **Engines**.
- **Users tab** (SYS_ADMIN full, ADMIN USER-only): table with [Edit] [Delete], + Add User modal. ADMIN cannot see/create ADMIN or SYS_ADMIN. The role select excludes them.
- **Engines tab** (SYS_ADMIN only): table with [Edit SSH] [Delete], + Add Engine modal. The same form handles create and edit.
- `UserForm`, `EngineForm` components.
- All POSTs/PATCHes/DELETEs write `AuditLog` rows on the backend (via `@Audited`).

## 28. Frontend: build + manual role smoke + screenshots  ✅

**Scope:** frontend verification.

- `npm run build`, `npm run lint`.
- Manual smoke as SYS_ADMIN, ADMIN, USER: Admin Panel hidden, engine list filter, role gates, log filters.
- Screenshots of each page in each role for the slides.
- `qa-reviewer` pass on the frontend diff.

---

## Conventions

- One task = one PR-sized diff. Don't bundle.
- After every backend task, `./gradlew compileJava` is green. After every frontend task, `npm run build` is green.
- After task 22 and 28, `qa-reviewer` runs.
- If a task's scope grows, split it. If a task's scope shrinks, leave a note in the commit and continue.
- Doc tasks (#11, #12, #13) come first because the spec must be locked before any code.
