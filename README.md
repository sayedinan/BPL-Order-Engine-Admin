# Engine Helm

> Internal Order Engines dashboard. Lets sys.admin / admin / standard
> users trigger **start / stop / status / logs** on real remote engines
> over SSH.
>
> This is a **V1** project, also serving as a live agentic-coding
> workflow demo for the team (presented Wed, Sept 2, 11:00 AM).

## Where to start

Read **[SPEC.md](./SPEC.md)** first. It is the source of truth for V1
scope, the role model, the permission matrix, the data model, the SSH
execution flow, the demo seed set, and the V1 constraints.

The planning file at
`~/.claude/plans/check-and-read-instruction-md-shimmering-spring.md`
records every locked-in decision and the original open-questions list.

## Stack

- **Backend:** Spring Boot (Java 21)
- **Frontend:** React (Vite/TypeScript)
- **DB:** H2, file-backed, seeded with fixed users / engines /
  permissions. No external DB for V1.

## Hard requirements before you run anything

1. **`ENGINE_HELM_MASTER_KEY`** must be set in your shell. The app
   **fails to start** without it. Never commit it, never paste it,
   never put it in a tracked `.env`. See the
   [credential-store-usage skill](./.claude/skills/credential-store-usage.md).
2. **SSH is key-only.** No passwords. The SSH public key for the demo
   hosts is shown in the admin UI after first boot — paste it into the
   target hosts' `authorized_keys`.
3. **The dev-time SSH guardrail hook is on by default.** Real `ssh` /
   `scp` / `sftp` invocations from Claude Code are blocked unless you
   opt in with `ENGINE_HELM_SSH_OK=1` (single-use). See the
   [ssh-approval skill](./.claude/skills/ssh-approval.md).

## Do NOT do this

- **Do not point the demo at production hosts.** The Wednesday demo
  uses the seeded `web-tier-host`, `worker-tier-host`, and
  `isolated-host` entries. Real production hosts are out of scope for
  V1.
- **Do not paste `ENGINE_HELM_MASTER_KEY` into chat, project Context,
  or any tracked file.** Rotate immediately if it ever leaks.
- **Do not edit the `audit_log` table by hand.** It's append-only;
  mutating prior rows breaks the audit trail and the in-flight Q2
  snapshot.
- **Do not name a Spring `@RestController` `EngineController`.** Use
  `EngineControlController` to avoid colliding with `SshExecutionService`.

## Sub-agents

The repo defines four sub-agents under `.claude/agents/`:

- **`security-reviewer.md`** — read-only. Hard gate for anything that
  touches the SSH service package, the JCE keystore, or any
  shell-build helper. Nothing ships without its sign-off.
- **`ssh-execution-service.md`** — owns the SSH / script-execution
  backend layer. Cannot edit RBAC, audit-log writer, or credential
  store wrappers.
- **`admin-rbac-ui.md`** — owns the sys.admin / admin screens: user
  management, System Role assignment (respecting the sys.admin-tier
  restriction), Access Groups, engine / host / script / credential
  config.
- **`audit-log-ui.md`** — owns the filterable audit log view, with the
  admin-vs-sys.admin visibility split enforced at the query layer.

Tool allowlists and trigger conditions for each agent are documented in
their respective files.

## V1 demo flow (Wed, Sept 2, 11:00 AM)

1. Log in as `sysadmin@local`. Show engine list, audit log, and the
   three `engine-*` rows.
2. Open `eng-isolated`. Note: it's reachable as `sys.admin` even
   though it's in *no* Access Group.
3. Log out. Log in as `bob@local`. Open the user page (as
   `sysadmin@local` again) and walk through Bob's effective
   permissions: `eng-web-01`, `eng-web-02`, `eng-worker-01`,
   `eng-worker-02`. Note **`eng-isolated` is not on the list** — the
   negative test of the union model.
4. Trigger a `status` on `eng-web-01`. Show the green "healthy" badge
   (exit-code convention: `0` = healthy). Show the audit log row with
   the snapshotted script text.
5. Log in as `admin@local`. Show the audit log view: operational
   entries only, no `CONFIG` rows (admin-vs-sys.admin split).
6. Log back in as `sysadmin@local`. Show the `CONFIG` rows that the
   admin view hid.

## V1 out of scope

Production DB, SSO, multi-tenant isolation, output streaming,
cross-host orchestration, HA / clustering, metrics export. See
SPEC.md §12 for the full list.
