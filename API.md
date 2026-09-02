# API.md — v0.3 Backend Contract

| | |
|---|---|
| **Status** | v0.3 (implementation contract; matches SPEC.md §4) |
| **Base path** | `/api` |
| **Auth** | `Authorization: Bearer <jwt>` on every non-public request |
| **Content type** | `application/json` request/response; `text/plain` ignored |
| **Timestamps** | ISO-8601 UTC, e.g. `2026-09-01T09:02:11Z` |
| **Path engine id** | `Engine.code` (e.g. `bpl`), **not** the UUID |

> This document is the implementation contract for the v0.3 backend. SPEC.md §4
> is the high-level design; this file pins the wire shape (request body, response
> body, status codes, error envelope) so the frontend and backend can build in
> parallel. The frontend already conforms to it (see `frontend/src/api/types.ts`
> and `frontend/src/api/mock/router.ts`).

---

## 0. Conventions

### 0.1 Error envelope (every 4xx/5xx)

```json
{
  "timestamp": "2026-09-01T09:02:11Z",
  "status": 409,
  "error": "Conflict",
  "message": "Engine 'bpl' is already RUNNING",
  "path": "/api/engines/bpl/start",
  "details": { "exitCode": 127, "stderr": "sh: start.sh: not found" }
}
```

- `details` is **optional** — present only when the exception carries structured data
  (e.g. `EngineScriptException`'s `exitCode` and `stderr`, or a `MethodArgumentNotValidException`'s
  field errors). It is a JSON object, never a string.
- The message is the exception's `getMessage()` — no stack traces, no secrets.
- A USER requesting `/api/audit-logs` gets `"Access denied"`, not the resource path.
- `401` means "no/invalid credentials" (token missing, malformed, expired).
- `403` means "credentials valid but role/assignment disallows this."

### 0.2 JWT

- Signed with HMAC; secret from `JWT_SECRET` env var (≥ 256 bits, required at startup).
- TTL: 8h. After expiry, the client should re-authenticate (no refresh tokens in v0.3).
- Claims:
  ```json
  { "sub": "admin", "roles": ["SYS_ADMIN"], "mustChangePassword": false, "iss": "bpl-order-engine-admin", "iat": 1700000000, "exp": 1700028800 }
  ```
- `mustChangePassword` is the live `User.mustChangePassword` at issue time. The
  `POST /api/auth/change-password` endpoint re-issues a JWT with the new value.

### 0.3 RBAC matrix (3 roles)

| Action | SYS_ADMIN | ADMIN | USER |
|---|:---:|:---:|:---:|
| `GET /api/auth/me` | ✅ | ✅ | ✅ |
| `POST /api/auth/change-password` | ✅ self | ✅ self | ✅ self |
| `GET /api/engines` | ✅ all | ✅ all | 🔒 assigned only |
| `POST /api/engines` | ✅ | ❌ | ❌ |
| `DELETE /api/engines/{code}` | ✅ | ❌ | ❌ |
| `PATCH /api/engines/{code}/ssh` | ✅ | ❌ | ❌ |
| `GET /api/engines/{code}/status` | ✅ all | ✅ all | 🔒 assigned only |
| `POST /api/engines/{code}/start\|stop` | ✅ all | ✅ all | 🔒 assigned only |
| `GET /api/engines/{code}/logs` | ✅ all | ✅ all | 🔒 assigned only |
| `WS /api/engines/{code}/logs/stream` | ✅ all | ✅ all | 🔒 assigned only |
| `GET /api/users` | ✅ | ✅ | ❌ |
| `POST /api/users` | ✅ any role | ✅ USER only | ❌ |
| `DELETE /api/users/{id}` | ✅ any | ✅ USER only | ❌ |
| `PATCH /api/users/{id}/roles` | ✅ any | ✅ USER's role/assignments only | ❌ |
| `GET /api/audit-logs` | ✅ | ✅ | ❌ (403 outright) |

Enforced server-side via `@PreAuthorize` (controller methods) and query-layer
filters (`WHERE engine.id IN (:assignedIds)` for USER).

---

## 1. Auth

### 1.1 `POST /api/auth/login` (public)

```json
// request
{ "username": "admin", "password": "..." }

// response 200
{
  "token": "eyJhbGciOi...",
  "expiresAt": "2026-09-01T17:02:11Z",
  "user": { "id": "...", "username": "admin", "role": "SYS_ADMIN", "assignedEngineCodes": [] },
  "mustChangePassword": true
}
```

| Status | Body message | Audit row |
|---|---|---|
| 200 | — (LoginResponse) | `LOGIN_SUCCESS`, `details.reason = "OK"` |
| 400 | `Username and password are required` | — (validation 400) |
| 401 | `Invalid credentials` (no enumeration) | `LOGIN_FAIL`, `details.reason = "BAD_CREDENTIALS"` |
| 403 | (reserved for `USER_DISABLED` in v0.4) | `LOGIN_FAIL`, `details.reason = "USER_DISABLED"` |

### 1.2 `POST /api/auth/logout` (auth)

- Stateless. Server: writes a `LOGOUT` audit row. Client: drops the token.
- 204 No Content on success. 401 if token is missing/expired.

### 1.3 `GET /api/auth/me` (auth)

```json
// response 200
{
  "id": "uuid",
  "username": "admin",
  "role": "SYS_ADMIN",
  "assignedEngineCodes": ["BPL", "PCL"],
  "mustChangePassword": false
}
```

- `mustChangePassword` is read from the live `User` row, not the JWT claim, so
  a password change by the user mid-session is visible on the next `me` call.
- 401 on missing/expired token.

### 1.4 `POST /api/auth/change-password` (auth)

```json
// request
{ "currentPassword": "...", "newPassword": "..." }

// response 200 (same shape as login)
{
  "token": "eyJhbGciOi...",
  "expiresAt": "2026-09-02T01:02:11Z",
  "user": { "id": "...", "username": "admin", "role": "SYS_ADMIN", "assignedEngineCodes": [] },
  "mustChangePassword": false
}
```

| Status | Body message | Audit row |
|---|---|---|
| 200 | — (new LoginResponse) | `CHANGE_PASSWORD`, `details.reason = "OK"` |
| 400 | `Both currentPassword and newPassword are required` | — (validation 400) |
| 401 | `Current password is incorrect` (no enumeration) | `CHANGE_PASSWORD`, `details.reason = "BAD_CURRENT_PASSWORD"` |
| 422 | `Password must be at least 12 characters and include a letter and a digit` | — (validation 422) |

`PasswordStrength` rule (Bean Validation, shared by `CreateUserRequest` and
`ChangePasswordRequest.newPassword`):
- Min 12 chars, max 128 chars
- At least one ASCII letter `[A-Za-z]`
- At least one digit `[0-9]`
- No whitespace-only, no control characters (`\p{Cntrl}`)

On success: write `CHANGE_PASSWORD` audit row, set `User.mustChangePassword = false`,
persist, issue a new JWT with `mustChangePassword: false` in claims.

---

## 2. Engines

### 2.1 `GET /api/engines` (auth)

- USER: returns only `Engine.code IN :user.assignedEngineCodes`.
- ADMIN/SYS_ADMIN: returns all non-deleted engines.

```json
// response 200
[
  {
    "id": "uuid",
    "code": "BPL",
    "name": "BPL Order Engine",
    "mode": "REAL",
    "serverIp": "10.0.0.5",
    "serverUsername": "bpl",
    "startScript": "systemctl start bpl-engine",
    "stopScript":  "systemctl stop  bpl-engine",
    "logScript":   "tail -F /var/log/bpl.log",
    "status": "RUNNING",
    "lastTransitionAt": "2026-09-01T09:02:11Z",
    "createdAt": "2026-08-15T10:00:00Z",
    "updatedAt": "2026-09-01T09:02:11Z"
  }
]
```

**`serverPassword` is NEVER in the response.** The field doesn't exist on the DTO.

### 2.2 `POST /api/engines` (SYS_ADMIN)

```json
// request
{
  "code": "PCL", "name": "PCL Order Engine", "mode": "REAL",
  "serverIp": "10.0.0.6", "serverUsername": "pcl", "serverPassword": "...",
  "startScript": "systemctl start pcl-engine",
  "stopScript":  "systemctl stop  pcl-engine",
  "logScript":   "tail -F /var/log/pcl.log"
}
```

| Status | Body message | Audit |
|---|---|---|
| 201 | — (EngineResponse) | `CREATE_ENGINE`, `details: { engineCode, mode }` |
| 400 | `Missing required field(s)` (or field validation) | — |
| 403 | `Access denied` (non-SYS_ADMIN) | — |
| 409 | `Engine 'PCL' already exists` (unique code) | — |

Validation (Bean Validation, fails → 400 with `details: { field: msg }`):
- `code`: `^[A-Z0-9_]{2,16}$`, unique among non-deleted
- `name`: ≤ 80 chars
- `serverIp`: ≤ 64 chars, must parse as IPv4 or RFC 1123 hostname
- `serverUsername`: ≤ 64 chars
- `serverPassword`: ≤ 512 chars (Jasypt ciphertext size)
- For `mode = REAL`: `startScript`/`stopScript`/`logScript` must be non-blank
- For `mode = REAL`: scripts are sanity-checked (no `; rm`, `&& rm`, `| rm`, backticks) but NOT sandboxed

### 2.3 `DELETE /api/engines/{code}` (SYS_ADMIN)

- Soft delete: `UPDATE engine SET deleted_at = now() WHERE id = ?`.
- Cascade: removes `user_engine_access` rows for this engine.
- Stops the background log tailer if running.
- 204 on success, 404 if not found / already deleted.

| Status | Body | Audit |
|---|---|---|
| 204 | — | `DELETE_ENGINE`, `details: { engineCode }` |
| 404 | `Engine 'bpl' is not supported` | — |
| 403 | `Access denied` | — |

### 2.4 `PATCH /api/engines/{code}/ssh` (SYS_ADMIN)

```json
// request (all fields optional; only present fields are updated)
{ "serverIp": "...", "serverUsername": "...", "serverPassword": "...",
  "startScript": "...", "stopScript": "...", "logScript": "...",
  "name": "...", "mode": "REAL" }
```

- The new password is **never** echoed in the response or audit details.
- `details.fieldsChanged` lists the names of the fields that were updated.

| Status | Body | Audit |
|---|---|---|
| 200 | — (EngineResponse) | `UPDATE_ENGINE_SSH`, `details: { engineCode, fieldsChanged: [...] }` |
| 400 | validation | — |
| 403 | `Access denied` | — |
| 404 | `Engine 'bpl' is not supported` | — |

If `mode` is changed to `REAL`: the SSH client cache for this engine is invalidated
in `SshClientProvider`; the next `status` call attempts to connect.

### 2.5 `GET /api/engines/{code}/status` (role + assignment)

```json
// response 200
{
  "engineCode": "BPL",
  "displayName": "BPL Order Engine",
  "status": "RUNNING",
  "mode": "REAL",
  "lastTransitionAt": "2026-09-01T09:02:11Z",
  "checkedAt": "2026-09-01T09:15:44Z"
}
```

| Status | Body |
|---|---|
| 200 | — (EngineStatusResponse) |
| 403 | `Access denied` (USER without assignment) |
| 404 | `Engine 'bpl' is not supported` |

### 2.6 `POST /api/engines/{code}/start` (role + assignment)

```json
// response 200
{
  "engineCode": "BPL",
  "displayName": "BPL Order Engine",
  "status": "RUNNING",
  "message": "BPL Order Engine started.",
  "transitionedAt": "2026-09-01T09:02:11Z"
}
```

| Status | Body | Audit |
|---|---|---|
| 200 | — (EngineActionResponse) | `START_ENGINE`, success: `details: { engineCode, exitCode: 0 }` |
| 403 | `Access denied` (USER without assignment) | — |
| 403 | `SSH authentication failed for engine 'bpl'` (`EngineAuthException` → 403, per SPEC §6.2 "Auth failure → 403, no retry") | `START_ENGINE`, `details: { engineCode, error: "EngineAuthException" }` |
| 404 | `Engine 'bpl' is not supported` | — |
| 409 | `Engine 'bpl' is already RUNNING` | `START_ENGINE`, `details: { engineCode, error: "Conflict", message: "Already running" }` |
| 502 | `Engine 'bpl' is unreachable` (SSH connect fail, after retry) | `START_ENGINE`, `details: { engineCode, error: "EngineUnreachableException" }` |
| 502 | `Script exited with code 127` (non-zero exit; details has exitCode + truncated stderr ≤ 2KB) | `START_ENGINE`, `details: { engineCode, exitCode, stderr, error: "EngineScriptException" }` |
| 504 | `Operation timed out` (`Future.get(30, SECONDS)` exceeded) | `START_ENGINE`, `details: { engineCode, error: "TimeoutException" }` |

### 2.7 `POST /api/engines/{code}/stop` (role + assignment)

Mirror of `/start`. Audit action is `STOP_ENGINE`.

### 2.8 `GET /api/engines/{code}/logs?limit=N` (role + assignment)

- `limit` ∈ {50, 100, 200}; default 100; 400 on out-of-range.
- Reads from the per-engine `LogBuffer` (cap 500).

```json
// response 200
{
  "engineCode": "BPL",
  "limit": 100,
  "count": 42,
  "lines": [
    { "timestamp": "2026-09-01T09:10:00Z", "level": "INFO", "message": "Order queue drained: 12 orders processed" }
  ]
}
```

### 2.9 `WS /api/engines/{code}/logs/stream` (role + assignment)

- RFC 6455 native WebSocket; no STOMP.
- JWT via `Sec-WebSocket-Protocol: bearer.<token>` (subprotocol). The browser
  WebSocket API doesn't allow custom headers on the handshake.
- Server's `JwtAuthFilter` is reused for the handshake; an invalid/missing
  token → 401 on the upgrade.
- USER without the engine in `assignedEngines` → 403 on the upgrade.
- The server reuses the HTTP `SecurityFilterChain` path matchers
  (`/api/engines/*/logs/stream` requires authentication); the role+assignment
  check is in the `EngineLogsWebSocketHandler.afterConnectionEstablished` hook.

**Snapshot on connect:** the server sends the last 100 lines from `LogBuffer`
(per-engine deque, FIFO) as individual text frames BEFORE any live line.

**Frame format (text):** JSON object, one per line.
```json
{"timestamp": "2026-09-01T09:10:00Z", "level": "INFO", "message": "..."}
```

**Close frame (server → client before closing):**
```json
{"event": "closed", "reason": "engine_stopped"}
```
Reasons: `engine_stopped`, `engine_deleted`, `auth_failed`, `network_dropped`
(used in close event for the client; not from server).

**Reconnect (client):** exponential backoff 1s → 30s cap. Reset on `open`.
No reconnect on `engine_stopped` / `engine_deleted` / `auth_failed`.

---

## 3. Users

### 3.1 `GET /api/users` (ADMIN, SYS_ADMIN)

- Returns all users. The `passwordHash` field is NEVER in the response.

```json
// response 200
[
  { "id": "uuid", "username": "admin", "role": "SYS_ADMIN",
    "assignedEngineCodes": ["BPL", "PCL"],
    "createdAt": "...", "updatedAt": "..." }
]
```

### 3.2 `POST /api/users` (role-checked)

- SYS_ADMIN: any role.
- ADMIN: only `USER` role. Attempting `ADMIN` or `SYS_ADMIN` → 403.

```json
// request
{ "username": "alice", "password": "...", "role": "USER", "assignedEngineCodes": ["BPL"] }
```

Validation: `PasswordStrength` rule (see §1.4). `username` ≤ 64 chars, unique.

| Status | Body | Audit |
|---|---|---|
| 201 | — (UserResponse) | `CREATE_USER`, `details: { newUserId, newUsername, newRole, assignedEngines }` |
| 400 | validation | — |
| 403 | `Access denied` (ADMIN trying to create ADMIN/SYS_ADMIN, or USER trying to create anyone) | — |
| 409 | `Username 'alice' is taken` (unique constraint) | — |

New users default to `mustChangePassword = true`.

### 3.3 `DELETE /api/users/{id}` (role-checked)

- SYS_ADMIN: any user.
- ADMIN: only USER-role users.
- A user cannot delete themselves (400).
- Cannot delete the last remaining SYS_ADMIN (400).

| Status | Body | Audit |
|---|---|---|
| 204 | — | `DELETE_USER`, `details: { targetUserId, targetUsername }` |
| 400 | `You cannot delete yourself` | — |
| 400 | `Cannot delete the last SYS_ADMIN` | — |
| 403 | `Access denied` (ADMIN trying to delete a non-USER) | — |
| 404 | `User not found` | — |

### 3.4 `PATCH /api/users/{id}/roles` (role-checked)

- SYS_ADMIN: can change anyone's role and assignments.
- ADMIN: can change only USER-role users' assignments (NOT their role —
  an ADMIN cannot promote a USER to ADMIN).

```json
// request (both optional)
{ "roleType": "USER", "assignedEngineCodes": ["BPL", "PCL"] }
```

| Status | Body | Audit |
|---|---|---|
| 200 | — (UserResponse) | `UPDATE_USER_ROLES`, `details: { targetUserId, oldRoles, newRoles }` |
| 403 | `Access denied` | — |
| 404 | `User not found` | — |

`details.oldRoles` and `details.newRoles` are arrays of `{ roleType, assignedEngineCodes }`.

---

## 4. Audit logs

### 4.1 `GET /api/audit-logs` (ADMIN, SYS_ADMIN)

USER → 403 outright (rejected, not filtered).

Query params (all optional):
- `actor` — exact match on `actorUsername`
- `action` — exact match on `AuditAction` enum value
- `engine` — `Engine.code` (server resolves to `Engine.id`)
- `from` / `to` — ISO-8601 UTC instants
- `page` (default 0), `size` (default 50, max 200)

```json
// response 200
{
  "items": [
    { "id": "uuid", "timestamp": "2026-09-01T09:02:11Z",
      "actorUsername": "admin", "actorRole": "SYS_ADMIN",
      "action": "START_ENGINE", "targetEngineCode": "BPL",
      "details": { "engineCode": "BPL", "exitCode": 0 } }
  ],
  "page": 0, "size": 50, "total": 1
}
```

`details` is parsed JSON object, not a string. 2KB cap; truncation emits a
`WARN` log line "details truncated".

| Status | Body |
|---|---|
| 200 | — (AuditLogPageResponse) |
| 400 | validation (from > to, invalid datetime, etc.) |
| 403 | `Access denied` (USER) |

---

## 5. Exception → HTTP mapping

| Exception | HTTP | `details` shape |
|---|---|---|
| `MethodArgumentNotValidException` | 400 | `Map<fieldName, message>` |
| `ConstraintViolationException` | 400 | same |
| `HttpMessageNotReadableException` | 400 | — (generic "malformed JSON") |
| `OptimisticLockingFailureException` | 409 | — |
| `DataIntegrityViolationException` | 409 | — (unique, FK) |
| `AccessDeniedException` | 403 | — (generic "Access denied") |
| `EngineNotSupportedException` | 404 | — |
| `EngineAuthException` | 403 | — |
| `EngineUnreachableException` | 502 | — |
| `EngineScriptException` | 502 | `{ engineCode, exitCode, stderr (≤2KB) }` |
| `TimeoutException` | 504 | — |
| `Exception` (catch-all) | 500 | `{ incident: "<uuid>" }` (UUID is in the response message too) |

All 4xx/5xx responses use the standard envelope from §0.1. The catch-all
500 includes an incident UUID in the response so support can cross-reference
the server log.

---

## 6. Audit row writers

Three places write `AuditLog` rows. The `@Audited` annotation + AOP is the
single source — controllers never write audit rows inline.

| Writer | Actions | When |
|---|---|---|
| `AuditAspect` (`@Audited` on controller method) | `CREATE_USER`, `DELETE_USER`, `UPDATE_USER_ROLES`, `CREATE_ENGINE`, `DELETE_ENGINE`, `UPDATE_ENGINE_SSH`, `START_ENGINE`, `STOP_ENGINE`, `CHANGE_PASSWORD` | On success AND on exception (success: per-action `details`; failure: `{ error: <class>, message: <truncated> }`) |
| `AuthenticationSuccessListener` | `LOGIN_SUCCESS` | On successful authentication, `details: { reason: "OK" }` |
| `AuthenticationFailureListener` | `LOGIN_FAIL` | On failed authentication, `details: { reason: "BAD_CREDENTIALS" \| "USER_DISABLED" \| "ACCOUNT_LOCKED" }`, `actorUsername = <attempted username>` |
| `AuthController.logout` | `LOGOUT` | On `POST /api/auth/logout`, `details: {}` |

Read-only endpoints (`GET` on engines, audit logs, users, logs) are NOT audited.

---

## 7. Anti-patterns (for the backend implementer)

- **No `passwordHash`, no `serverPassword`, no `token`, no `Authorization` header in any response or audit row.** The `details` object is a typed map; non-allowed fields cannot be added.
- **No audit rows from inline `auditLogRepository.save(...)` in a controller.** The aspect is the single source. Inline writes will be forgotten when the controller is refactored.
- **No actor from a request body field.** The actor is the JWT `sub` claim (from the security context). A user could otherwise send `actorUsername=admin` and forge audit attribution.
- **No plaintext credentials in any file, code, migration, log line, or audit row.** `Engine.serverPassword` is `@Encrypted` + Jasypt at rest. The `JASYPT_ENCRYPTOR_PASSWORD` env var is required.
- **No HTTP Basic.** All auth is JWT in `Authorization: Bearer …`.
- **No `ENGINE_ID = 'bpl'` hard-coded lookup.** The factory looks up by `code` from the DB row, not from a Spring bean map.
- **No token in a query param.** WebSocket handshake uses `Sec-WebSocket-Protocol` subprotocol.
- **No `ANY` role on `@PreAuthorize` for engine control.** The 3-role matrix is enforced; "USER with assignment" requires the engine in their `assignedEngines`, not just any role match.
- **No `JasyptConfig` without the env var.** App fails to start if `JASYPT_ENCRYPTOR_PASSWORD` is missing.
- **No reading `User` rows in the WS handler without a role+assignment check.** The check is the same as the HTTP equivalent.
