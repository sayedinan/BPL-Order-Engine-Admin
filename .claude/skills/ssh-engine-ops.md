---
name: ssh-engine-ops
description: v0.3 — how SshBackedEngine opens, uses, and closes Apache MINA SSHD connections. Timeouts, error handling, and the background log tailer's reconnect policy.
---

# SSH engine operations (v0.3)

Every `mode=REAL` engine in v0.3 runs its start/stop/log scripts over
Apache MINA SSHD against the engine's `serverIp` with `serverUsername` +
`serverPassword` (decrypted from the Jasypt-encrypted column on read).
This skill defines the contract; `SshBackedEngine` is the only class
that talks to the network.

## Connection lifecycle

```
+----------+    first call    +-----------+    session.close()    +-----------+
|  IDLE    | ---------------> | CONNECTED | --------------------> |  CLOSED   |
|  (no     |                  | (1 client |                       |  (client  |
|   client)|                  |  per      |                       |  closed)  |
+----------+                  |  engine)  |                       +-----------+
   ^                          +-----------+
   |                               |
   |  engine.mode = MOCK or        | idle > 5 min OR
   |  engine deleted               | app shutdown
   +-------------------------------+
```

- **One `SshClient` per engine.** Not per request. Reuse across calls.
- **Lazy connect.** Don't open the SSH connection at app startup. Open it on the first `status` / `start` / `stop` / `logs` call for that engine, or when the engine transitions to `RUNNING` and the background tailer needs to start.
- **Idle eviction.** If no operation in 5 minutes, close the `SshClient` to free the server-side slot. Next call reopens. This is per-engine; eviction of one engine does not affect another.
- **App shutdown.** A `@PreDestroy` method on `SshBackedEngine` (or a `SmartLifecycle` bean) closes every open client. Verify the close is idempotent.

## Timeouts (hard limits)

| Operation | Connect timeout | Operation timeout | Notes |
|---|---|---|---|
| `status()` (a `echo OK` probe) | 5s | 5s | Read-only; can be tight. |
| `start(script)` | 5s | 30s | Scripts are operator-authored, can be slow. |
| `stop(script)` | 5s | 30s | Same. |
| `getLogs(limit)` (on-demand read) | 5s | 10s | Should be a single `tail` command, fast. |
| `tail -F` (background tailer) | 5s | unbounded but **cancellable** | One thread per `RUNNING` engine. Cancel on `STOPPED`, app shutdown, or repeated reconnect failures. |

The 30s on start/stop is a **bound on the request thread**, not a bound on the SSH session. The pattern is:

```java
Future<EngineActionResult> future = sshExecutor.submit(() -> doStart(script));
EngineActionResult result = future.get(30, TimeUnit.SECONDS);
```

If the future doesn't complete in 30s, cancel it, log a `START_ENGINE_TIMEOUT` audit row, and return a 504 to the client. Don't leave a dangling `ChannelExec` on the server.

## Error handling

Three categories of failure, each handled differently:

### 1. Auth failure
- `UserAuthException` from SSHD.
- **Do not retry.** Different password or different username won't fix itself.
- Return `EngineAuthException` (custom, in `manager.engine.exception`) → handled by `ApiExceptionHandler` → **403** with the standard envelope, `message: "SSH authentication failed for engine '<code>'"`. No password in the message, ever.
- Write an `AuditLog` row: `action=START_ENGINE` (or whatever the call was), `details: { "error": "SSH_AUTH_FAILED" }`. Don't include the attempted password.

### 2. Connection refused / network unreachable
- `IOException` during connect, or the SSHD `ConnectFuture` completes with an exception.
- **Retry once** with the same credentials (transient blips happen).
- If retry fails, return `EngineUnreachableException` → **502** with the standard envelope, `message: "Engine '<code>' is unreachable"`.
- The background tailer treats this as "reconnect with backoff" (see below), not as a terminal failure.

### 3. Script execution failure
- The SSH session is fine, but `start` / `stop` exits non-zero.
- Read the script's stderr from the captured `ChannelExec` output.
- Return `EngineScriptException(exitCode, stderr)` → **502** with the standard envelope, `message: "Script exited with code <N>"`, and the stderr in `details`.
- Write an `AuditLog` row: `action=START_ENGINE` (or `STOP_ENGINE`), `details: { "exitCode": <N>, "stderr": "<truncated to 2KB>" }`.

## Background log tailer (the one exception to bounded timeouts)

- One `Thread` (or `ScheduledExecutorService` task) per `mode=REAL` engine in `RUNNING` state.
- Started by an `@EventListener<ApplicationEvent>` on `EngineStatusChangedEvent` (where the new status is `RUNNING`). Stopped on the next event where the new status is `STOPPED` or the engine is deleted.
- The tailer runs `tail -F <logPath>` (or whatever `Engine.logScript` is) and pushes each line into:
  1. The per-engine `ArrayDeque<LogLine>` rolling buffer (cap 500; oldest evicted).
  2. The WebSocket session registry, so every connected viewer gets the line.
- The SSH connection is owned by the tailer for its lifetime. `SshClient` connect, then a long-lived `ChannelExec` for the `tail -F`.
- On SSH drop (network blip, server restart), reconnect with exponential backoff: **1s, 2s, 4s, 8s, 16s, 32s, capped at 60s**. After 5 consecutive failures, give up for that tail cycle and emit a single log line `WARN "Log tailer lost connection to '<code>'; will retry"`. The next user `GET /api/engines/{id}/logs` call will re-trigger reconnect.

## Credentials

- The `serverPassword` is decrypted **only** at the moment an `SshClient` is being set up. The plaintext exists in a `char[]` (not `String`) inside the auth callback, and is zeroed after the `ClientSession` is established.
- The decrypted password is **never** logged, **never** returned in an exception message, **never** written to the audit log.
- If you find yourself wanting to log "tried with password X" for debugging, stop — that's a credential leak.

## Anti-patterns

- **Don't open a new `SshClient` per request.** Reuse. Per-request connections will exhaust the server's `MaxAuthTries` and `MaxStartups` in minutes.
- **Don't use `Thread.sleep` for timeouts.** Use `Future.get(timeout, unit)` and cancel.
- **Don't catch `Exception` and log the message.** Distinguish the three categories above; they have different HTTP responses and audit entries.
- **Don't run the background tailer on the request thread.** It will block a Tomcat worker until the JVM shuts down.
- **Don't store the SSH session in a `ThreadLocal`.** It will leak across requests and confuse the eviction logic.
