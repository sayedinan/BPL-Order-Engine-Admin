---
name: add-engine-via-ui
description: v0.3 — Sys.Admins create new engines through the Admin Panel (POST /api/engines). No new Spring bean or class is required; engines are rows in the engine table.
---

# Add a new Order Engine (v0.3)

In v0.3, engines are **data, not code**. There is exactly one runtime
implementation (`SshBackedEngine` in `manager.engine.impl.ssh`) and one
optional mock (`MockEngineOperations` in `manager.engine.impl.mock`).
Adding a new engine is an admin action, not a code change.

## What the sysadmin does

1. Open the Admin Panel → Engines tab → **Add Engine**.
2. Fill in the form:
   - `name` (display name, e.g. `"BPL Order Engine"`) — required, 1–80 chars.
   - `code` (machine id, e.g. `"BPL"`) — required, `^[A-Z0-9_]{2,16}$`, **unique**.
   - `serverIp` — required, valid IPv4 or hostname.
   - `serverUsername` — required, 1–64 chars.
   - `serverPassword` — required, 1–256 chars. Sent over TLS, **encrypted at rest** with Jasypt (see below).
   - `mode` — `MOCK` (in-memory state machine, no SSH) or `REAL` (Apache MINA SSHD, runs the configured scripts).
   - `startScript` — required if `mode=REAL`; e.g. `systemctl start bpl-engine` or `./start.sh`.
   - `stopScript` — required if `mode=REAL`; e.g. `systemctl stop bpl-engine` or `./stop.sh`.
   - `logScript` — required if `mode=REAL`; a command that tails the log, e.g. `tail -n 100 /var/log/bpl.log` for on-demand reads, or `tail -F /var/log/bpl.log` for the background tailer.
3. Click **Create**. The app issues `POST /api/engines` (SYS_ADMIN only).
4. The new engine is immediately selectable in the engine dropdown on the dashboard.

## What the backend does

- The `Engine` row is persisted (Flyway migration `V1__init.sql` defines the table).
- The `serverPassword` is encrypted with Jasypt before `INSERT`, using the master password from `JASYPT_ENCRYPTOR_PASSWORD` env var. Plaintext never touches the DB or the wire after the POST.
- The `OrderEngineFactory` resolves engines by `code` from `EngineRepository.findByCode(...)` — **not** from Spring beans. The factory throws `EngineNotSupportedException` → 404 when a code is not in the DB.
- If `mode=REAL`, the next `GET /api/engines/{code}/status` lazily opens an SSHD connection (bounded timeout 5s, see `ssh-engine-ops` skill).
- If `mode=MOCK`, the existing in-memory state machine pattern from v0.2 is reused via `MockEngineOperations` — no SSH, no network.

## Editing or deleting an engine

- `PATCH /api/engines/{id}/ssh` (SYS_ADMIN) updates IP, username, password (re-encrypted), scripts, mode. Changing `mode` from `MOCK` → `REAL` does NOT start the engine — it only makes the next status call attempt SSH.
- `DELETE /api/engines/{id}` (SYS_ADMIN) is **soft-delete** in v0.3: the row's `deletedAt` is set, and the factory's `findByCode` excludes soft-deleted rows. Existing audit log rows referencing the engine keep its `code` for historical lookups.
- **Cascade rule:** deleting an engine removes the `user_engine_access` join rows for that engine. Users who had only that engine assigned fall back to having no engines; their next dashboard load shows an empty engine list, not an error.

## What the agent must NOT do

- Do **not** add a new `@Service("<code>")` bean to register an engine. The factory no longer keys on bean names; the DB row is the source of truth.
- Do **not** add code to `OrderEngineFactory` to handle a new engine type. Add a row, not a branch.
- Do **not** write a new `OrderEngineOperations` subclass. The two existing implementations (`SshBackedEngine`, `MockEngineOperations`) cover every engine the PRD describes. If you think a third impl is needed, stop and ask — that's a spec change, not a code change.
- Do **not** store `serverPassword` in plaintext. The `@Encrypted` annotation + Jasypt `StringEncryptor` bean handles the round trip; if you see a `String serverPassword` field without `@Encrypted`, that's a bug.
- Do **not** hardcode SSH credentials, IPs, or master passwords in any file. `application.properties` reads from env vars: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JASYPT_ENCRYPTOR_PASSWORD`, `JWT_SECRET`. The `.env` is gitignored (already denied in `settings.json`).

## Validation rules (also enforced server-side)

| Field | Rule |
|---|---|
| `code` | `^[A-Z0-9_]{2,16}$`, unique among non-deleted engines |
| `serverIp` | valid IPv4 or RFC 1123 hostname |
| `mode` | `MOCK` or `REAL` |
| Scripts (REAL mode) | non-blank, ≤ 1024 chars, no shell-injection patterns (`; rm`, `&& rm`, `\| rm`, backticks, `$(...)` of untrusted input) — scripts are operator-authored, not user-authored, so this is a coarse sanity check, not a sandbox |
