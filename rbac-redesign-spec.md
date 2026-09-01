# SPEC.md Addendum — Full RBAC & Multi-Engine Administration (Phase 2 Draft)

> Transcribed from the paper RBAC sketch (Aug/Sep 2026). This describes a **target** design — Sys.Admin/Admin/User tiers, per-engine role grants, audit logging, and generic runtime-registered engines — sketched independently of the current Phase 1 build. It doesn't supersede §§1–5 of SPEC.md yet; treat it as the Phase 2 design pending a scoping decision. See §6.7 for open questions before any of this gets implemented.

## 6.1 Why this exists

Phase 1 (§§1–5) intentionally shipped a minimal two-role (ADMIN/VIEWER), single hardcoded engine, in-memory-only system. This section captures a considerably larger design: persistent users, a third admin tier, per-engine access grants, and two separate log subsystems. It's recorded here as-is so the sketch isn't lost, not as a decision to build all of it immediately.

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

Sys.Admin is the dev-facing tier — the only one that can create engines or other Admins. Admin is the non-dev operational tier. User is scoped entirely by role grants.

> **Note:** a line scoping Admin down to only their assigned engines was crossed out in the sketch. As written above, Admin is global — flagging this because it's the one place the design visibly changed its mind mid-sketch. See §6.7 item 1.

## 6.3 The role model: role = per-engine grant

A "role" here isn't an abstract permission bundle — its name *is* the engine it grants access to. Granting a user the role `BPL` means they can view the `BPL` engine's status/log and start/stop it, and nothing else. A user can hold two or more roles at once, i.e. access to multiple engines.

This only applies to the **User** tier — Sys.Admin and Admin aren't gated by role grants; they see/control every engine regardless.

Implied data shape:

```
User        { id, username, passwordHash, tier: SYS_ADMIN | ADMIN | USER }
EngineGrant { userId, engineId }     // one row per (user, engine) — the "role"
Engine      { id, name, host, sshUsername, credentialRef, startScript, stopScript, logScript }
```

## 6.4 Two log subsystems

- **Audit log** — administrative/operational actions: who started or stopped which engine and when, and who assigned which role to whom and when. Visible to Sys.Admin and Admin only.
- **Engine log** — one stream per engine, produced by running that engine's own log script. Visible to Sys.Admin, Admin, and any User holding that engine's role.

UI-wise, the logs view has a dropdown to switch between "Audit Log" and each individual engine's log.

```
AuditLogEntry { timestamp, actorUserId, action, targetEngineId?, targetUserId?, detail }
EngineLogLine { engineId, timestamp, level, message }
```

## 6.5 Engine registration model (architecture pivot)

This is the biggest structural change from what's built. Phase 1's `OrderEngineOperations` is a compiled-in Java Strategy (§2.2–2.3) — adding an engine means writing and deploying a new class (§5.1). The sketch instead describes engines as **runtime-registered, server-backed records**:

- Each engine lives on a server, identified by **IP address + username + password** for that server.
- Sys.Admin adds an engine by pointing it at a server and supplying (or writing) three scripts: **start**, **stop**, and **log**. The app runs these against the server rather than calling in-process Java code.
- Multiple engines can share one server, or each can have its own.

This is a different extensibility mechanism than §5.1's factory/registry pattern — it trades "new engine = new Java class + redeploy" for "new engine = new DB row + three scripts," at the cost of needing a real remote-execution layer (SSH) and credential storage that don't exist in Phase 1 at all. Whether the two mechanisms coexist or one replaces the other is an open decision — see §6.7 item 6.

**Worth flagging directly:** storing server passwords in plaintext, as literally sketched, is a real risk beyond demo purposes — worth deciding early whether to move to SSH keys or at least encrypt credentials at rest before any real server IP/password goes into the system.

## 6.6 UI notes from the sketch

- Sys.Admin gets "add engine" / "add user" buttons.
- Logs view has a dropdown to filter by engine, plus an audit-log option.
- Sys.Admin is expected to hand-write each engine's start/stop/log scripts through this UI — no template or wizard specified yet.

## 6.7 Open questions (not resolved in the sketch)

1. Is Admin's reach always global (all engines, all users), or does some form of the crossed-out per-engine scoping still apply in special cases?
2. Can Admin create other Admins, or is that strictly Sys.Admin-only? The sketch only ever shows Sys.Admin doing this.
3. Is a role's grant always "start/stop + log," or could a role ever be log-view-only? The sketch's worked example (a user with role `BPL`) bundles both.
4. How literal is "manually write a script" — a Sys.Admin-authored script filled into a template, or arbitrary code the app executes on a remote server using stored credentials? The latter has meaningfully larger security implications than the former.
5. Credential storage: plaintext, encrypted-at-rest, or a shift to key-based auth?
6. Do compiled-in engines (like the current `bpl` mock) and generic scripted engines coexist long-term, or does everything eventually migrate to the scripted model?
