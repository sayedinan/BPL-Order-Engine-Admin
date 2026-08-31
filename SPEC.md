# Engine Helm — SPEC.md (V1)

> Internal Order Engines dashboard, also serving as a live agentic-coding
> workflow demo for the team (presented Wed, Sept 2, 11:00 AM).
>
> This document is the source of truth for V1. It cross-references every
> decision in the approved planning file
> (`~/.claude/plans/check-and-read-instruction-md-shimmering-spring.md`)
> and the original `instruction.md`.

---

## 1. Overview

**Engine Helm** is a Spring Boot + React dashboard for triggering
`start` / `stop` / `status` / `logs` actions on real remote engines over
SSH. It is used internally by ops and platform teams.

V1 is intentionally narrow:

- H2 file-backed embedded database, seeded with fixed users, engines,
  and permissions. No external DB.
- Real SSH to real hosts, with sys.admin-supplied per-engine scripts.
- Three system roles (`sys.admin` > `admin` > `standard`) plus
  sys.admin-defined **Access Groups** for standard users.
- Strict credential handling: key-only SSH, JCE keystore, master key
  via env var, host key fingerprint pinning.
- A demoable audit log with an admin-vs-sys.admin visibility split.

The Wednesday 2026-09-02 11:00 AM presentation is a first milestone, not
a launch. V1 is the smallest surface that lets the team honestly demo
the agentic-coding workflow against a real backend.

## 2. Architecture

```
+--------------------+        +----------------------------+
|  React (Vite/TS)   |  HTTPS |  Spring Boot (Java 21)     |
|  - Admin screens   | <----> |  - REST API                |
|  - Standard user   |        |  - Spring Security         |
|    screens         |        |  - AuditLog writer         |
|  - Audit log view  |        |  - SshExecutionService     |
+--------------------+        |  - JCE Keystore            |
                              +-----------+----------------+
                                          |
                                          | SSH (key only,
                                          | host key pinned)
                                          v
                              +---------------------------+
                              |  Engine host(s)            |
                              |  (real, sys.admin-supplied |
                              |   start/stop/status/log    |
                              |   scripts)                 |
                              +---------------------------+

+--------------------+        +----------------------------+
|  H2 file-backed DB  | <----> |  data.sql (idempotent seed) |
+--------------------+        +----------------------------+
```

**Key architectural decisions:**

- **Backend holds SSH credentials, never the browser.** The UI triggers
  actions via authenticated REST calls. Private key material never
  reaches the client.
- **File-backed H2**, not in-memory. The DB file lives under the
  application data directory and survives restarts. (Q5)
- **Short-lived per-action SSH sessions** in V1. No connection pool.
  Every `start` / `stop` / `status` / `logs` call opens a fresh JSch
  session, runs the script, and closes in a `try-with-resources`
  boundary. (Structural answer, locked in.)
- **Permission resolution is snapshotted immediately before dispatch**,
  not at click time, to minimize the TOCTOU window if a queue is added
  later. (Q2)
- **The audit-log query layer (not the UI) enforces the admin-vs-sys.admin
  visibility split.** No data leaks on the wire.

## 3. Role model

Two distinct concepts, never conflated.

### 3.1 System Role (single-valued, ordered)

`sys.admin` > `admin` > `standard user`

- A user has exactly one System Role.
- `sys.admin` and `admin` both have **implicit full engine reach** —
  they do not need to be a member of any Access Group to operate on any
  engine. Access Groups exist only for standard users. (Structural
  answer, locked in.)

### 3.2 Access Group (multi-valued, standard users only)

- An Access Group is a named, sys.admin-defined bundle of engines.
- Each bundle grants **both** view (status/logs) **and** operate
  (start/stop) on each engine in the bundle. There is no read-only
  Access Group in V1.
- A standard user can hold multiple groups.
- A standard user's engine reach is the **union** of all groups they
  belong to. (Q1)
- **No per-engine deny, no first-match-wins, no negative grants in V1.**
  Access Groups are purely additive. (Q1)
- `sys.admin` and `admin` do not hold Access Groups (their reach is
  implicit and full).

### 3.3 Effective-permissions view

The user detail page (sys.admin / admin view) renders an
**"Effective permissions"** panel that shows, per engine, *which*
group(s) granted reach. This is the operator's tool for answering "why
does Bob have access to `eng-worker-02`?" without diffing the group's
membership list. (Q1, Q7)

## 4. Permission matrix

| Action | sys.admin | admin | standard (with group) | standard (no group) |
|---|---|---|---|---|
| **App login / logout** | ✅ | ✅ | ✅ | ✅ |
| **Trigger start / stop / status / logs on any engine** | ✅ (implicit full reach) | ✅ (implicit full reach) | only on engines in *any* of their groups (union) | ❌ |
| **View engine config (host, scripts, credential reference)** | ✅ | ❌ | ❌ | ❌ |
| **Edit engine config** (host, start/stop/status/log scripts, credential assignment) | ✅ | ❌ | ❌ | ❌ |
| **Add / pin host key fingerprint** | ✅ | ❌ | ❌ | ❌ |
| **View credentials list** | ✅ | ❌ | ❌ | ❌ |
| **Create / delete credential entry** | ✅ | ❌ | ❌ | ❌ |
| **Create / delete users** | ✅ (any tier) | ✅ (admin and standard only) | ❌ | ❌ |
| **Set System Role** | ✅ (including create other sys.admin accounts) | ✅ (admin and standard only; **cannot create, delete, or promote/demote sys.admin-tier accounts**) | ❌ | ❌ |
| **Create / edit / delete Access Groups** | ✅ | ❌ | ❌ | ❌ |
| **Assign Access Groups to standard users** | ✅ | ✅ | ❌ | ❌ |
| **Audit log — OPERATIONAL entries** | ✅ (all) | ✅ (all) | ❌ | ❌ |
| **Audit log — CONFIG / USER_MGMT entries** | ✅ | ❌ (filtered at the query layer) | ❌ | ❌ |

Notes that follow directly from `instruction.md` and the locked-in
answers:

- The admin-vs-sys.admin audit-log split is enforced **at the query
  layer**, not in the UI, so data does not leak on the wire.
- Admin cannot touch engine config. Even a sys.admin-tier user is
  outside admin's reach for credential / script / host changes.
- Standard users do not see other users' audit entries at all.

## 5. Data model

All tables live in H2 file-backed. Columns are illustrative, not
authoritative — the JPA entities may add audit columns
(`created_at`, `updated_at`, `created_by`) not listed here.

### 5.1 `users`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `username` | VARCHAR(255) UNIQUE NOT NULL | e.g. `bob@local` |
| `password_hash` | VARCHAR(255) NOT NULL | BCrypt |
| `system_role` | VARCHAR(16) NOT NULL | `sys.admin` / `admin` / `standard` |
| `must_change_password` | BOOLEAN NOT NULL DEFAULT FALSE | See §6.3, "Future upgrade path" |
| `enabled` | BOOLEAN NOT NULL DEFAULT TRUE | |

### 5.2 `access_groups`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `name` | VARCHAR(255) UNIQUE NOT NULL | e.g. `Engines — Web Tier` |
| `description` | VARCHAR(1024) | |
| `created_by` | BIGINT FK → `users.id` | sys.admin who created it |

### 5.3 `user_access_groups` (join)

| Column | Type | Notes |
|---|---|---|
| `user_id` | BIGINT FK → `users.id` | standard users only |
| `group_id` | BIGINT FK → `access_groups.id` | |

A standard user may have any number of rows. `sys.admin` and `admin`
have zero rows (their reach is implicit). (Q1)

### 5.4 `access_group_engines` (join)

| Column | Type | Notes |
|---|---|---|
| `group_id` | BIGINT FK → `access_groups.id` | |
| `engine_id` | BIGINT FK → `engines.id` | |

A group may bundle any number of engines. Same engine may appear in
multiple groups (this is what makes Bob's union case demoable in §10).

### 5.5 `hosts`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `alias` | VARCHAR(255) UNIQUE NOT NULL | e.g. `web-tier-host` |
| `hostname_or_ip` | VARCHAR(255) NOT NULL | **configuration data, not a constant** |
| `port` | INT NOT NULL DEFAULT 22 | |
| `ssh_username` | VARCHAR(255) NOT NULL | Server-side username; the SSH service uses this when opening the connection |
| `host_key_fingerprint` | VARCHAR(255) NOT NULL | SHA-256 of public host key, pinned at add time |
| `default_credential_id` | BIGINT FK → `credentials.id` NOT NULL | Default credential for this host. The SSH service uses this unless an engine specifies otherwise. |

A host may have **multiple credentials** (current + rotation + break-glass);
`default_credential_id` points to the one in active use. See §5.6 and
§11 for the rotation path.

### 5.6 `credentials`

The credential store supports two auth types. Both are encrypted at
rest, both are referenced only by `credential_id`, both are never shown
in plaintext after creation. The `type` column is the discriminator.

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `alias` | VARCHAR(255) UNIQUE NOT NULL | e.g. `cred-host-A` |
| `type` | VARCHAR(16) NOT NULL | `ssh_key` or `ssh_password` |
| `fingerprint` | VARCHAR(255) NOT NULL | Display-only: SHA-256 of the public key (for `ssh_key`), or a SHA-256 of the ciphertext (for `ssh_password`, so admins can tell two passwords apart without decrypting) |
| `private_key_ciphertext` | VARBINARY NULL | Set when `type = ssh_key`. Encrypted via JCE, master key = `ENGINE_HELM_MASTER_KEY`. |
| `password_ciphertext` | VARBINARY NULL | Set when `type = ssh_password`. Encrypted via JCE, master key = `ENGINE_HELM_MASTER_KEY`. |
| `created_at` | TIMESTAMP | |

**Invariant:** exactly one of `private_key_ciphertext` or
`password_ciphertext` is non-NULL, matching the `type` value.

**Key-based is strongly preferred** in V1. `ssh_password` exists to
cover the long tail of legacy / vendor / bastion hosts that ops already
have to manage. The same hygiene rules apply to both — the SSH service
opens the connection, the script body is command-only (no `sshpass`,
no inline `SSHPASS=...`, no connection strings), and the secret is
never shown in plaintext after creation.

### 5.7 `engines`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `name` | VARCHAR(255) UNIQUE NOT NULL | e.g. `eng-web-01` |
| `host_id` | BIGINT FK → `hosts.id` | **Engines do not directly reference a credential.** The SSH service resolves the credential via the host's `default_credential_id` (or, in a future upgrade, an engine-level override — see §11). |
| `start_script` | CLOB NOT NULL | Free-form bash, sys.admin-authored. **Command-only** (see §7.2 step 2a). |
| `stop_script` | CLOB NOT NULL | Same |
| `status_script` | CLOB NOT NULL | Same. Exit-code convention: see §7.3. |
| `log_script` | CLOB NOT NULL | Same |

Multiple engines may share a host (and therefore a credential). Adding
a new engine that runs on an existing host does **not** require adding
a new credential.

### 5.8 `audit_log`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `timestamp` | TIMESTAMP NOT NULL | |
| `actor_user_id` | BIGINT NULL FK → `users.id` | NULL for failed logins with unknown username |
| `actor_username_snapshot` | VARCHAR(255) NOT NULL | Snapshotted at action time so deletes don't break the audit trail |
| `actor_system_role_snapshot` | VARCHAR(16) NOT NULL | |
| `actor_group_set_snapshot` | VARCHAR(2048) NULL | CSV of group names at dispatch time, per Q2 |
| `engine_id` | BIGINT NULL FK → `engines.id` | NULL for non-engine events (login, etc.) |
| `action` | VARCHAR(64) NOT NULL | See §5.8.1 |
| `category` | VARCHAR(16) NOT NULL | `OPERATIONAL` or `CONFIG` (CONFIG covers USER_MGMT too) |
| `script_text_snapshot` | CLOB NULL | **Exact** script text at dispatch time, byte-for-byte |
| `exit_code` | INT NULL | SSH / script exit code, NULL for non-execution events |
| `stdout_excerpt` | VARCHAR(4096) NULL | First ~4KB of stdout for the audit row |
| `details` | VARCHAR(2048) NULL | Free-form human-readable detail |

#### 5.8.1 Action vocabulary

`start`, `stop`, `status`, `logs`, `login_success`, `logout`,
`login_failure`, `engine_config_change`, `script_edit`, `credential_add`,
`credential_delete`, `host_add`, `host_key_pin`, `user_create`,
`user_delete`, `user_role_change`, `group_create`, `group_edit`,
`group_delete`, `group_assign`, `group_unassign`.

All `login_*`, `logout`, `start`, `stop`, `status`, `logs` are
`category = OPERATIONAL`. All `*_edit`, `*_add`, `*_delete`,
`*_change`, `*_assign`, `*_unassign` are `category = CONFIG`.

## 6. App login & session

### 6.1 Auth

- Spring Security with a custom `UserDetailsService` backed by the H2
  `users` table.
- Passwords stored as **BCrypt** hashes.
- **No self-registration.** Accounts are provisioned by sys.admin or
  admin only.
- No "remember me", no long-lived tokens, no refresh-token rotation in
  V1. (See §6.2.)
- **Session timeout: 30 minutes of inactivity.** SPEC states this
  explicitly. The inactivity clock is "last request timestamp" (i.e. a
  new authenticated request resets the clock; idle page reads do not
  count unless they hit the API).

### 6.2 V1 password-setting behavior

In V1, the sys.admin or admin sets the user's password directly at
creation time. There is no first-login flow. This is the demo behavior.

The password-setting logic must be implemented behind a swappable
service interface (e.g. `PasswordProvisioningService`) so a later
switch to "generate a temp password + force change on first login" is a
config change, not a rewrite.

### 6.3 Future upgrade path (flagged explicitly)

The `users.must_change_password` column is a **documented seam** for a
future "generate temp password + force change on first login" flow.
When that flow ships:

1. `PasswordProvisioningService` gets a second implementation
   (`TempPasswordProvisioningService`) toggled via config.
2. The temp password is shown to the admin once, never stored.
3. On first login, if `must_change_password = TRUE`, the user is
   redirected to a change-password screen and the flag is cleared on
   save.

**This is not just an implementation detail.** The flag exists in V1
even though the seam is unused, so the data model and SPEC are aligned
with the upgrade path from day one. Reviewers should be able to spot
this section and know the upgrade is wired through the model.

### 6.4 Auth event audit logging

Three auth events are logged with `category = OPERATIONAL`:

- `login_success` — actor = the user, details = source IP / user-agent
  if available.
- `logout` — actor = the user.
- `login_failure` — actor identity attempted (snapshotted), details =
  `bad_credentials` / `disabled_account` / `unknown_user`.

These entries are visible to **admin** (per the admin-vs-sys.admin
visibility split in §4) and to sys.admin.

## 7. SSH execution flow

### 7.1 Class & module naming

Per the instruction, the SSH/execution service is named to avoid
collisions with Spring's `@RestController` convention:

- Service: `SshExecutionService` (not `EngineController`).
- Controller: `EngineControlController` (this is the
  `@RestController`; the naming distinction is intentional).
- Credential store wrapper: `KeystoreCredentialStore`.

### 7.2 Per-action session lifecycle

Every `start` / `stop` / `status` / `logs` call:

1. **Authorization resolution (Q2 snapshot).** The request handler
   resolves the calling user's effective engine reach **immediately
   before dispatch**, captures the user's
   `{username, system_role, group_set}` into local variables, and
   confirms the engine is in that set. This snapshot — not the
   current DB state — is what the audit row records.
2. **Snapshot the script text.** The engine's current
   `start_script` / `stop_script` / etc. is read into a local
   variable. This byte-for-byte text is what gets stored in
   `audit_log.script_text_snapshot`. Subsequent edits to the engine
   config do **not** mutate prior audit rows.
3. **Pre-flight: bash -n + advisory patterns.** Before saving a
   *new* or *edited* script, the backend runs:
   - `bash -n` — real syntax check. **Output is blocking.** If
     `bash -n` exits non-zero, the script is not saved and the
     error stream is returned terminal-style for the UI to render.
   - Advisory pattern scan — non-blocking. Patterns flagged:
     `rm -rf /`, `curl ... | bash`, `wget ... | bash`,
     `:(){ :|:& };:` (fork bomb), and similar. The UI shows these
     in a warning list. They do not block.
4. **Open JSch session.** Try-with-resources. Connect to the
   engine's `host_id`:
   - Port from `hosts.port`.
   - User: `credential.username` (out of scope of V1 schema, fixed
     per credential entry; could be lifted to its own column).
   - Auth: the credential's **private key**, decrypted in-memory
     from the JCE keystore using `ENGINE_HELM_MASTER_KEY`. The
     decrypted key is held in a `char[]` / `byte[]` local and
     zeroed in a `finally` block.
   - **Host key verification:** the JSch `Config.STRICT_HOST_KEY_CHECKING`
     is set to `yes` and the `known_hosts` is the pinned
     `host_key_fingerprint` for the `host_id`. No
     trust-on-first-connect.
5. **Execute the script.** `bash -c <script_text>` on a fresh
   `exec` channel. Connection timeout and command timeout are set
   conservatively (TBD by ssh-execution-service agent during
   implementation; spec floor: 10s connect, 60s command).
6. **Capture stdout / stderr / exit code.** Returned to the API
   caller; `stdout_excerpt` (first ~4KB) stored in the audit row.
7. **Write audit row.** With
   `script_text_snapshot`, `exit_code`, `actor_*_snapshot`,
   `engine_id`, `action`, `category = OPERATIONAL`. (Q2)
8. **Close.** JSch session `disconnect()` in `finally`.

### 7.3 Status / log response shape

- **`status`** returns raw stdout + exit code, paginated if the
  stdout is large. The script itself defines what "status" means.
  **Exit-code convention** (Q4):
  - `0` = healthy / running
  - non-zero = not running / error
  The UI renders a green "healthy" badge on `exit 0`, a red badge
  otherwise, and shows the raw stdout below. The demo seed's
  `eng-web-01.status_script` returns `exit 0` so this is demoable.
- **`logs`** returns the tail of the engine's `log_script` stdout,
  paginated. A snapshot row is written to the audit log with
  `action = logs` and `category = OPERATIONAL`.

### 7.4 Bash safety — both halves (not just "advisory")

The instruction's "advisory only, not a safety guarantee" line is about
the **pattern warnings**, not about syntax errors. SPEC.md commits to
both:

- **`bash -n` is the only hard block** on script save. Real syntax
  errors are surfaced terminal-style and the save is rejected.
- **Pattern warnings are advisory only.** No override flag in V1
  (Q3). No per-engine "high-trust" toggle in V1.
- **Mandatory confirmation UX** on every script save: the UI shows
  the `bash -n` result (if any) and the advisory list in a
  confirmation modal before the save is allowed. The modal is not
  skippable in V1.
- **Mandatory confirmation UX on credential delete:** if the
  credential is referenced by any engine, the UI names those
  engines and requires explicit acknowledgement before the delete
  proceeds. This is non-overridable in V1.

## 8. Host, IP, and credential treatment

- **Host/IP/credentials are configuration data, never constants.**
  No `eng-web-01.example.com`-style literals anywhere in the SSH
  execution layer or in source. Everything flows from the `engines`
  and `credentials` tables and the JCE keystore.
- SSH auth is **key-based only**. No passwords.
- One keypair per credential entry. Public key goes in the target
  host's `authorized_keys`; private key lives only in the
  encrypted credential store, referenced by ID.
- **Credential store = Java keystore (JCE).** Master key supplied
  via the `ENGINE_HELM_MASTER_KEY` environment variable at deploy
  time. Never committed, never in `.env` files tracked by git,
  never pasted into chat or project Context.
- **Boot-time precondition:** Spring Boot **fails to start** if
  `ENGINE_HELM_MASTER_KEY` is unset. No silent fallback to a
  default or empty key. (Q6)
- **Host key pinning:** when a sys.admin adds a new host, they
  paste the SHA-256 fingerprint of the host's public key. The
  fingerprint is stored on `hosts.host_key_fingerprint`. JSch
  refuses to connect if the live fingerprint does not match. No
  trust-on-first-connect.
- **No rotation UI in V1.** Master-key rotation is out-of-band:
  redeploy with a new `ENGINE_HELM_MASTER_KEY` plus a re-encrypt
  helper. (Q6)

## 9. V1 constraints

- **H2 file-backed**, not in-memory. Audit log survives restarts.
  (Q5)
- **`data.sql` is idempotent.** Every `INSERT` is guarded so that
  re-running the seed is a no-op. H2-compatible shape: a per-table
  `SELECT COUNT(*) = 0` guard via a `CommandLineRunner` (or `MERGE`),
  not a raw `INSERT ... WHERE NOT EXISTS` which H2 does not accept
  directly. (Q5)
- **No cap, no export on the audit log in V1.** (Q5)
- **No "remember me" / long-lived tokens / refresh-token rotation.**
  (§6.1)
- **Standard users see only their own reach.** No global engine
  list. (Permission matrix, §4)
- **Negative reach is a documented demo case.** §10 includes
  `eng-isolated` — an engine in *no* Access Group, reachable only
  by sys.admin and admin. The user page's effective-permissions
  view must visibly exclude it for Alice and Bob. This is the
  negative test of the union model, not just a positive one. (Q1,
  Q7)
- **Demo-day safety:** the Wednesday demo does **not** touch real
  production hosts — only the seeded host IDs. The seed uses
  loopback / placeholder host entries that are clearly labeled
  as demo hosts. The SSH flow's "real" path is wired end-to-end but
  the demo SSHes to the seeded hosts, not to customer infrastructure.
- **`eng-isolated`'s host-key fingerprint is pinned in seed data** so
  the host-key-pinning rule is demoable, not just documented.
- **Class-naming guardrail:** no Spring `@RestController` is named
  `EngineController`. The SSH service is `SshExecutionService`; the
  REST controller is `EngineControlController`. Future contributors
  must not collapse these two names.

## 10. Demo seed set (Q7)

To make the Wednesday demo land cleanly, the H2 `data.sql` ships with
the following. Passwords are BCrypt-hashed. The seed is idempotent;
re-running it is a no-op.

### 10.1 Users

| Username | System Role | `must_change_password` | Notes |
|---|---|---|---|
| `sysadmin@local` | `sys.admin` | false | Demo "operator" persona |
| `admin@local` | `admin` | false | Demo "user mgmt" persona |
| `alice@local` | `standard` | false | Member of `Engines — Web Tier` only |
| `bob@local` | `standard` | false | Member of **both** `Engines — Web Tier` and `Engines — Workers` (the union-case demo) |

### 10.2 Access Groups

| Name | Engines bundled |
|---|---|
| `Engines — Web Tier` | `eng-web-01`, `eng-web-02` |
| `Engines — Workers` | `eng-worker-01`, `eng-worker-02`, **plus `eng-web-02`** (the overlap with Web Tier is what makes Bob's union case demoable) |

### 10.3 Engines

| Engine | Host | Scripts | In any group? |
|---|---|---|---|
| `eng-web-01` | `web-tier-host` (host key pinned in seed) | start / stop / status (`exit 0`) / log | Web Tier |
| `eng-web-02` | `web-tier-host` (shared) | start / stop / status / log | Web Tier **and** Workers (overlap) |
| `eng-worker-01` | `worker-tier-host` | start / stop / status / log | Workers |
| `eng-worker-02` | `worker-tier-host` (shared) | start / stop / status / log | Workers |
| `eng-isolated` | `isolated-host` (host key pinned in seed) | start / stop / status / log | **no group** — reachable only by sys.admin / admin |

### 10.4 Hosts and credentials

Three `hosts` rows, each with a pinned host-key fingerprint:

- `web-tier-host` (alias)
- `worker-tier-host` (alias)
- `isolated-host` (alias)

Three `credentials` rows, one per host, private key encrypted in JCE
with the `ENGINE_HELM_MASTER_KEY` env var. For the demo, the
keypairs are generated at first boot and the public keys are
displayed in the admin UI for the operator to paste into the
target hosts' `authorized_keys`. (Q6)

### 10.5 Audit log

Empty in seed. The Wednesday demo will populate it live.

## 11. Future upgrade paths (flagged explicitly)

These are intentional seams in V1, not deferred work. Reviewers should
be able to find each one in this SPEC.

- **Temp password + forced first-login change** (via
  `users.must_change_password` + a swappable
  `PasswordProvisioningService`). See §6.3.
- **Master-key rotation UI** (currently out-of-band; future: a
  sys.admin-only "rotate master key" screen that re-encrypts every
  credential entry). See §8.
- **Audit log export / cap** (currently none; future: CSV export
  and a retention cap). See §5.8 and §9.
- **Per-engine `dangerous_patterns_allowed` flag** (currently no
  override; future: a sys.admin opt-in for "high-trust" hosts).
  Not in V1 per Q3.
- **Negative access grants** (currently deny-by-omission only;
  future: explicit per-engine deny lists on Access Groups). Not in
  V1 per Q1.

## 12. Out of scope for V1

- Production database (Postgres / MySQL).
- External SSO / SAML / OIDC.
- Multi-tenant isolation.
- Engine output streaming to the browser (V1 returns the captured
  stdout after the SSH call returns).
- Cross-host orchestration (one action = one engine = one SSH
  session).
- High availability / clustering. H2 is single-node.
- Metrics export (Prometheus, etc.). The audit log is the only
  observability surface in V1.

## 13. Cross-references

- Original scope: `instruction.md` (TASKS 1–4).
- Planning decisions, Q1–Q8, and the four-sub-agent split:
  `~/.claude/plans/check-and-read-instruction-md-shimmering-spring.md`.
- Dev-time SSH guardrail: `.claude/hooks/guard-ssh.sh` (referenced
  from `.claude/settings.json`).
- Sub-agent definitions: `.claude/agents/security-reviewer.md`,
  `.claude/agents/ssh-execution-service.md`,
  `.claude/agents/admin-rbac-ui.md`, `.claude/agents/audit-log-ui.md`.
- Skills: `.claude/skills/ssh-approval.md`,
  `.claude/skills/credential-store-usage.md`.
