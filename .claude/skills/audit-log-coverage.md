---
name: audit-log-coverage
description: v0.3 — every state-changing endpoint writes an AuditLog row via the @Audited annotation + AOP aspect. Auth events use AuthenticationEventListener. This file lists what counts and what the details field should contain.
---

# Audit log coverage (v0.3)

Every state change in the system is auditable. The mechanism is the
`@Audited` annotation + an AOP aspect, plus an
`AuthenticationEventListener` for login events.

## What counts as a state change

| Action | Endpoint | Audit action enum | Source |
|---|---|---|---|
| Create a user | `POST /api/users` | `CREATE_USER` | `@Audited` on controller |
| Delete a user | `DELETE /api/users/{id}` | `DELETE_USER` | `@Audited` |
| Change a user's roles / engine access | `PATCH /api/users/{id}/roles` | `UPDATE_USER_ROLES` | `@Audited` |
| Create an engine | `POST /api/engines` | `CREATE_ENGINE` | `@Audited` |
| Delete an engine | `DELETE /api/engines/{id}` | `DELETE_ENGINE` | `@Audited` (soft-delete is still a state change) |
| Update an engine's SSH config | `PATCH /api/engines/{id}/ssh` | `UPDATE_ENGINE_SSH` | `@Audited` |
| Start an engine | `POST /api/engines/{id}/start` | `START_ENGINE` | `@Audited` (success AND failure both write) |
| Stop an engine | `POST /api/engines/{id}/stop` | `STOP_ENGINE` | `@Audited` (success AND failure both write) |
| Login succeeded | `POST /api/auth/login` (2xx) | `LOGIN_SUCCESS` | `AuthenticationSuccessEvent` listener |
| Login failed | `POST /api/auth/login` (401) | `LOGIN_FAIL` | `AuthenticationFailureBadCredentialsEvent` (and siblings) listener |
| Logout | `POST /api/auth/logout` | `LOGOUT` | `@Audited` |

**Read-only endpoints do NOT write audit rows.** `GET` on engines, audit logs, users, logs — none of these are audited. If you find yourself writing an audit row on a read, that's a bug; the table will fill with noise.

## The `@Audited` annotation

```java
@Target(METHOD)
@Retention(RUNTIME)
public @interface Audited {
    AuditAction action();
    boolean targetEngineFromPath() default false;  // extract {id} from the URI, look up engine.code
    String details() default "";                  // SpEL expression evaluated against the args/return
}
```

The aspect (`AuditAspect` in `manager.audit`) does this on every annotated method:

1. **Before the call:** capture `actorUsername` from the JWT (Spring Security context), `actorRole` from the JWT's `roles` claim.
2. **Resolve target engine.** If `targetEngineFromPath = true`, look up the engine by path's `{id}` → `Engine.code`. The path's `{id}` is the engine's UUID; the audit row stores the **code** (not the UUID) for human readability.
3. **Invoke the method.** Catch the return value or exception.
4. **After the call (success path):** write the row with `details` evaluated as SpEL.
5. **After the call (exception path):** write the row with `details: { "error": "<exception class>", "message": "<truncated message>" }`. Don't swallow the exception — the audit row is in addition to the normal 4xx/5xx response.

`LOGIN_FAIL` rows have `actorUsername = <the username that was tried>`. This is supplied by the failure event, not the security context (the security context is empty on a failed login). The audit row for `LOGIN_FAIL` is written by the `AuthenticationFailureListener`, not `@Audited`.

## What goes in `details` (the JSON column)

Keep it small (cap 2KB; truncate with a `WARN "details truncated"` if exceeded) and structured (a JSON object, not free text). Examples:

```json
// CREATE_USER
{
  "newUserId": "f1a2b3c4-...",
  "newUsername": "alice",
  "newRole": "USER",
  "assignedEngines": ["BPL"]
}

// UPDATE_USER_ROLES
{
  "targetUserId": "f1a2b3c4-...",
  "oldRoles": ["USER", ["BPL"]],
  "newRoles": ["ADMIN", ["BPL", "PCL"]]
}

// CREATE_ENGINE
{
  "engineCode": "BPL",
  "engineName": "BPL Order Engine",
  "mode": "REAL"
  // never include serverIp, serverUsername, or any credential
}

// UPDATE_ENGINE_SSH
{
  "engineCode": "BPL",
  "fieldsChanged": ["serverPassword", "logScript"]
  // never include the new password value, even hashed
}

// START_ENGINE (success)
{
  "engineCode": "BPL",
  "exitCode": 0
}

// START_ENGINE (failure)
{
  "engineCode": "BPL",
  "error": "EngineScriptException",
  "exitCode": 127,
  "stderr": "sh: start.sh: not found"   // truncated to 2KB
}

// LOGIN_FAIL
{
  "reason": "BAD_CREDENTIALS"
  // never include the attempted password
}
```

## Anti-patterns

- **Don't write audit rows inline in the controller.** Use `@Audited` + aspect. Inline writes will be forgotten when the controller is refactored.
- **Don't log the actor manually.** The aspect reads it from the security context. If the controller passes `actorUsername` as a parameter, that's a forgery vector (a USER could send a request with `actorUsername=admin` in the body and the audit row would lie).
- **Don't include secrets in `details`.** The aspect's `SpEL` evaluation runs against the method args, but you should keep secrets out of the args entirely — `@RequestBody` DTOs should not have password fields that get logged.
- **Don't write a `LOGIN_FAIL` row from the controller.** The `AuthenticationFailureListener` is the single source; the controller's exception handler doesn't know what the failure reason was.
- **Don't audit reads.** If you want a "user X looked at engine Y" log, that's a separate analytics stream, not the audit log.
- **Don't make audit writes block the request.** They're a DB write; if the DB is slow, the user sees a slow request. That's acceptable for v0.3. If it becomes a problem, move to an async write-behind queue — but that's a v0.4 concern, not a v0.3 one.

## Verification

`qa-reviewer.md` checks audit coverage on every diff. If you add a new `POST`/`PATCH`/`DELETE` endpoint without `@Audited`, qa-reviewer flags it as a `blocker`. If you add `@Audited` but the `details` SpEL references a field that doesn't exist, the aspect logs a warning and writes `details: { "spelError": "<message>" }` — also flagged.
