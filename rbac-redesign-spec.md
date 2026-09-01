# RBAC & Multi-Engine Administration — Decisions Record

> **Status:** Historical / resolution. This file was a Phase 2 design sketch (transcribed Aug/Sep 2026) with six open questions. Those questions have been resolved against v0.3 of `SPEC.md`. **Treat this file as the resolution record, not a parallel spec.** The active spec is [SPEC.md](SPEC.md); the build plan is [TASKS.md](TASKS.md); the subtask tables are in [TASKS-decomposed.md](TASKS-decomposed.md).

The original 81-line sketch is preserved below in §4 ("Original sketch, verbatim") for historical context, with a clear "do not implement from this section" banner.

---

## 1. Why this exists

Phase 1 of BPL Order Engine Admin shipped a minimal two-role (`ADMIN`/`VIEWER`), single hardcoded engine, in-memory-only system. The paper sketch captured a much larger design — three tiers, per-engine role grants, two log subsystems, runtime-registered engines. Rather than let the sketch drift out of sync with v0.3 of the SPEC, this file records the decisions that were made when the v0.3 SPEC was written, so future readers can see the reasoning without re-deriving it from the code.

Every decision below points to the section in `SPEC.md` that answers it. If the two ever disagree, **`SPEC.md` wins** — this file is downstream.

## 2. Decisions for the 6 open questions

The original §6.7 listed six unresolved questions. Each is answered below against v0.3.

### 2.1 Is `ADMIN`'s reach always global, or does some form of per-engine scoping apply?

**Decision:** `ADMIN` is always global — sees all users and all engines, full audit log access. The crossed-out per-engine scoping in the original sketch is discarded.

**Why:** Operational admins need to triage across engines (a USER's "engine BPL is stuck" report requires ADMIN to see BPL even if ADMIN's primary scope is PCL). The complexity of per-engine ADMIN scoping was not justified by any concrete use case in the conversation, and it interacts badly with audit-log reviews.

**See:** SPEC.md §3.1 (RBAC matrix).

### 2.2 Can `ADMIN` create other `ADMIN`s?

**Decision:** No. Only `SYS_ADMIN` can create a `SYS_ADMIN` or another `ADMIN`. `ADMIN` can create only `USER`-role users.

**Why:** "Create admin" is a privilege-escalation step. Mixing it into the operational `ADMIN` tier means a compromised admin account can mint more admins. v0.3 keeps the operational/admin split clean: `ADMIN` is for "manage regular users and watch engines," `SYS_ADMIN` is for "manage the system."

**See:** SPEC.md §3.1, §4.4 (`POST /api/users` role-checked).

### 2.3 Is a role's grant always "start/stop + log," or can it be log-view-only?

**Decision:** A grant is always "view status + start/stop + view logs + view real-time logs" — a single bundle. There is no log-view-only role in v0.3.

**Why:** The bundle is what every USER-needs-engine scenario in the v0.3 conversation required. A "log-view-only" tier would need a fourth role and a second-tier UI; the cost outweighs the only theoretical benefit (letting an auditor see logs without being able to start/stop). If that need materializes, it is a v0.4 feature, not a v0.3 change.

**See:** SPEC.md §3.1 (`User` row in the matrix).

### 2.4 How literal is "manually write a script" — template, or arbitrary code the app executes on a remote server?

**Decision:** SYS_ADMIN-authored scripts are **arbitrary text** that the app executes on the remote server via SSH. They are sanity-checked (blocklist: no `; rm`, no `&& rm`, no `| rm`, no backticks) but not sandboxed.

**Why:** The scripts are operator knowledge — `systemctl start bpl-engine`, `tail -F /var/log/bpl.log`, etc. A template/wizard would force-fit every real engine into one shape. The risk is real (a hostile or careless SYS_ADMIN could write `rm -rf /` and trigger it via the engine's start button), so v0.3 takes three mitigations: (a) SYS_ADMIN is a single-trusted-role, (b) every script execution is logged in the audit table with the script body not stored, (c) the serverPassword is Jasypt-encrypted at rest and zeroed in memory after SSH session establishment.

The blocklist is **not** a security boundary — a determined SYS_ADMIN can encode around it. The boundary is the audit log + SYS_ADMIN role + operator review.

**See:** SPEC.md §3.3 (`startScript` / `stopScript` / `logScript` validation), §6.2 (SSH execution, 3 error categories).

### 2.5 Credential storage: plaintext, encrypted-at-rest, or key-based?

**Decision:** **Jasypt-encrypted at rest**, master password from `JASYPT_ENCRYPTOR_PASSWORD` env var. The plaintext password lives in the `POST /api/engines` / `PATCH /api/engines/{code}/ssh` request body and in a `char[]` inside the SSH auth callback; it is zeroed after the `ClientSession` is established. Never logged, never in an exception message, never in an audit row, never in a response DTO.

**Why:** Key-based auth is the strongest option but requires the SSH target to host a `~/.ssh/authorized_keys` per sysadmin, which is a deployment cost the v0.3 environment cannot meet (the engines are shared with the JMeter suite). Jasypt with a master-password env var is the second-strongest option and ships with the v0.3 build. The plaintext-in-the-original-sketch path is rejected.

**See:** SPEC.md §2.4 (`jasypt-spring-boot-starter` dep), §3.3 (`serverPassword` field), §6.2 (credential lifecycle in `SshBackedEngine`).

### 2.6 Do compiled-in engines and generic scripted engines coexist, or does everything migrate to scripted?

**Decision:** Both coexist, selected per `Engine.mode` (`MOCK` or `REAL`). The factory looks up by `code` from `EngineRepository.findByCodeAndDeletedAtIsNull`, not from Spring beans.

**Why:** The MOCK implementation is essential for local dev, automated tests, and the "show the user the dashboard without standing up an SSH server" path. A scripted-only design would force every developer to have a working SSH target. The two-implementation shape also keeps the v0.2 mock code reusable while the new REAL impl handles the SSH case the v0.2 design punted on.

**See:** SPEC.md §3.6, §3.7, §6.1, §6.2.

## 3. USER audit visibility — the one decision that was not in the original sketch

The original sketch treated the audit log as USER-visible, scoped to assigned engines ("only those rows whose `targetEngineCode` is in their `assignedRoles`"). v0.3 reverses this: **the audit log is admin-only; USER is rejected with 403.**

**Why:** The audit log includes rows that are not engine-scoped — `LOGIN_SUCCESS`, `LOGIN_FAIL`, `LOGOUT`, `CREATE_USER`, `UPDATE_USER_ROLES`, `DELETE_USER`. A "filter to assigned engines" approach would silently drop those for USER, which is a UX trap (a USER who sees "0 results" doesn't know it's because none of the rows are engine-scoped, or because they have no assignments, or because the filter is wrong). The cleaner contract is "USER does not have access; the UI hides the option; the server returns 403 if the option is misused."

The engine execution logs (`/api/engines/{code}/logs` + WebSocket) are still USER-visible per their `assignedEngines`. The split is intentional: a USER needs to see what the engine *printed* to do their job; a USER does not need to see who else logged in, who got created, or which admins promoted which other admins.

**See:** SPEC.md §3.1 (matrix `View audit logs` row), §4.5 (`GET /api/audit-logs` is `SYS_ADMIN`/`ADMIN` only), §5.4 (Logs page hides `System Audit Logs` for `USER`).

## 4. Original sketch, verbatim

> **Do not implement from this section.** The sketch is preserved for historical context only. The decisions above are the v0.3 contract; this section is what they answered.

```markdown
# SPEC.md Addendum — Full RBAC & Multi-Engine Administration (Phase 2 Draft)

> Transcribed from the paper RBAC sketch (Aug/Sep 2026). This describes a
> **target** design — Sys.Admin/Admin/User tiers, per-engine role grants,
> audit logging, and generic runtime-registered engines — sketched
> independently of the current Phase 1 build. It doesn't supersede §§1–5
> of SPEC.md yet; treat it as the Phase 2 design pending a scoping
> decision. See §6.7 for open questions before any of this gets
> implemented.

## 6.1 Why this exists

Phase 1 (§§1–5) intentionally shipped a minimal two-role (ADMIN/VIEWER),
single hardcoded engine, in-memory-only system. This section captures a
considerably larger design: persistent users, a third admin tier,
per-engine access grants, and two separate log subsystems. It's recorded
here as-is so the sketch isn't lost, not as a decision to build all of
it immediately.

## 6.2 Roles & capability matrix

Three tiers replace the Phase 1 ADMIN/VIEWER pair:

| Capability | Sys.Admin | Admin | User |
|---|---|---|---|
| Create / delete users | ✅ | ✅ | ❌ |
| Create / delete Admins | ✅ | ❌ | ❌ |
| See full user list | ✅ | ✅ | ❌ |
| Add / delete engines | ✅ | ❌ | ❌ |
| See engines (already added) | ✅ all | ✅ all | Only engines granted via role |
| Start / stop engines | ✅ all | ✅ all | Only engines granted via role |
| Assign roles to users | ✅ | ✅ | ❌ |
| View audit log | ✅ all | ✅ all | ❌ |
| View engine log | ✅ all | ✅ all | Only engines granted via role |

Sys.Admin is the dev-facing tier — the only one that can create engines
or other Admins. Admin is the non-dev operational tier. User is scoped
entirely by role grants.

> **Note:** a line scoping Admin down to only their assigned engines was
> crossed out in the sketch. As written above, Admin is global — flagging
> this because it's the one place the design visibly changed its mind
> mid-sketch. See §6.7 item 1.

## 6.3 The role model: role = per-engine grant

A "role" here isn't an abstract permission bundle — its name *is* the
engine it grants access to. Granting a user the role `BPL` means they
can view the `BPL` engine's status/log and start/stop it, and nothing
else. A user can hold two or more roles at once, i.e. access to
multiple engines.

This only applies to the **User** tier — Sys.Admin and Admin aren't
gated by role grants; they see/control every engine regardless.

Implied data shape:

\`\`\`
User        { id, username, passwordHash, tier: SYS_ADMIN | ADMIN | USER }
EngineGrant { userId, engineId }     // one row per (user, engine) — the "role"
Engine      { id, name, host, sshUsername, credentialRef, startScript, stopScript, logScript }
\`\`\`

## 6.4 Two log subsystems

- **Audit log** — administrative/operational actions: who started or
  stopped which engine and when, and who assigned which role to whom and
  when. Visible to Sys.Admin and Admin only.
- **Engine log** — one stream per engine, produced by running that
  engine's own log script. Visible to Sys.Admin, Admin, and any User
  holding that engine's role.

UI-wise, the logs view has a dropdown to switch between "Audit Log" and
each individual engine's log.

\`\`\`
AuditLogEntry { timestamp, actorUserId, action, targetEngineId?, targetUserId?, detail }
EngineLogLine { engineId, timestamp, level, message }
\`\`\`

## 6.5 Engine registration model (architecture pivot)

This is the biggest structural change from what's built. Phase 1's
`OrderEngineOperations` is a compiled-in Java Strategy (§2.2–2.3) —
adding an engine means writing and deploying a new class (§5.1). The
sketch instead describes engines as **runtime-registered, server-backed
records**:

- Each engine lives on a server, identified by **IP address + username +
  password** for that server.
- Sys.Admin adds an engine by pointing it at a server and supplying (or
  writing) three scripts: **start**, **stop**, and **log**. The app runs
  these against the server rather than calling in-process Java code.
- Multiple engines can share one server, or each can have its own.

This is a different extensibility mechanism than §5.1's factory/registry
pattern — it trades "new engine = new Java class + redeploy" for "new
engine = new DB row + three scripts," at the cost of needing a real
remote-execution layer (SSH) and credential storage that don't exist in
Phase 1 at all. Whether the two mechanisms coexist or one replaces the
other is an open decision — see §6.7 item 6.

**Worth flagging directly:** storing server passwords in plaintext, as
literally sketched, is a real risk beyond demo purposes — worth deciding
early whether to move to SSH keys or at least encrypt credentials at
rest before any real server IP/password goes into the system.

## 6.6 UI notes from the sketch

- Sys.Admin gets "add engine" / "add user" buttons.
- Logs view has a dropdown to filter by engine, plus an audit-log option.
- Sys.Admin is expected to hand-write each engine's start/stop/log
  scripts through this UI — no template or wizard specified yet.

## 6.7 Open questions (not resolved in the sketch)

1. Is Admin's reach always global (all engines, all users), or does some
   form of the crossed-out per-engine scoping still apply in special
   cases?
2. Can Admin create other Admins, or is that strictly Sys.Admin-only?
   The sketch only ever shows Sys.Admin doing this.
3. Is a role's grant always "start/stop + log," or could a role ever be
   log-view-only? The sketch's worked example (a user with role `BPL`)
   bundles both.
4. How literal is "manually write a script" — a Sys.Admin-authored
   script filled into a template, or arbitrary code the app executes on
   a remote server using stored credentials? The latter has meaningfully
   larger security implications than the former.
5. Credential storage: plaintext, encrypted-at-rest, or a shift to
   key-based auth?
6. Do compiled-in engines (like the current `bpl` mock) and generic
   scripted engines coexist long-term, or does everything eventually
   migrate to the scripted model?
```

---

**Last updated:** Sept 1, 2026. Resolved against SPEC.md v0.3. Maintained by the QA Intern (technical owner) per the SPEC.md header.
