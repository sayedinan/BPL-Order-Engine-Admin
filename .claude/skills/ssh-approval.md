---
name: ssh-approval
description: |
  How to opt in for a single, real SSH / SCP / SFTP run during an
  Engine Helm dev session. Engine Helm ships a dev-time guardrail
  hook (`.claude/hooks/guard-ssh.sh`) wired in via
  `PreToolUse:Bash` in `.claude/settings.json`. This skill documents
  what the matcher catches, what it deliberately does NOT catch, how
  to opt in, and what to do if a legitimate non-SSH command gets
  flagged.
---

# ssh-approval

Engine Helm's dev-time guardrail blocks real outbound SSH during
coding and test runs unless explicitly approved. This is in addition
to the harness's standard `ask` permission list
(`Bash(ssh *)`, `Bash(scp *)`, `Bash(sftp *)` in
`.claude/settings.json`). The hook and the `ask` list are independent
gates: the `ask` list prompts you for permission, the hook blocks
unconditionally unless you opt in.

## When this matters

- You are about to invoke `ssh`, `scp`, or `sftp` from a Claude
  Code Bash call (e.g. testing a connectivity check, fetching a
  remote log, syncing a config file).
- You want to run a one-off SSH session during development without
  permanently disabling the guardrail.

## What the matcher catches

The hook scans the **command text** of every Bash call. It matches
`ssh`, `scp`, or `sftp` as **command tokens**, including:

- `ssh user@host`
- `scp file user@host:/tmp/`
- `sftp user@host`
- `... | ssh user@host echo hi` (piped)
- `ssh user@host && scp file host:/tmp` (chained)
- `/usr/bin/ssh user@host` (path-prefixed)

## What the matcher does NOT catch

The hook is intentionally strict about *what* it matches but loose
about *how* it matches substrings, so legitimate work isn't blocked:

- `echo ssh stuff` — `ssh` is an argument, not a token. **NOT
  caught.**
- `ssh-keygen -t ed25519` — different command, not `ssh`. **NOT
  caught.**
- `/usr/bin/ssh-keygen -t ed25519` — different command. **NOT
  caught.**
- `cat /etc/hosts | grep ssh` — `ssh` is an argument to `grep`,
  not a token after the pipe. **NOT caught.**

If you find a legitimate command that the hook flags, please file
it as a whitelist request — see "Whitelisting a command" below.

## How to opt in for a single run

The opt-in is a single environment variable:

```bash
export ENGINE_HELM_SSH_OK=1
# ... run the one ssh / scp / sftp command you intended ...
# ENGINE_HELM_SSH_OK is consumed on use; re-export for the next call.
```

The hook consumes the opt-in **on use**: a stray second invocation
in the same shell does not silently fly through. After one SSH
call passes, you must `export ENGINE_HELM_SSH_OK=1` again to run
another.

If you want to bypass the hook for the entire session (not
recommended), you can also remove the `PreToolUse:Bash` matcher for
`bash "$CLAUDE_PROJECT_DIR"/.claude/hooks/guard-ssh.sh` from
`.claude/settings.json`, but the project intentionally keeps the
hook on by default because this is a live demo that touches real
remote hosts.

## What the hook does NOT do

- It does not verify host keys, manage credentials, or do anything
  about the actual SSH call. The hook is a gate, not a wrapper.
- It does not inspect the contents of files you read or write. The
  `Read` tool has its own permissions; the hook is `Bash`-only.
- It does not catch `nc` / `netcat`, `rsync`, `git push` to a remote
  URL, or any non-`ssh`/`scp`/`sftp` network command. If you need
  one of those gated, add a hook.

## Whitelisting a command

If a legitimate non-SSH command gets flagged (false positive),
**do not** bypass the hook. Open an issue or note in the team's
chat with:

1. The exact command.
2. Why it's not an SSH call.
3. The path through the matcher that matched it.

The fix is a matcher refinement, not a permission override.

## V1 demo-day note

The Wednesday 2026-09-02 demo SSHes only to the **seeded host
IDs** (`web-tier-host`, `worker-tier-host`, `isolated-host` in
the `data.sql` seed). It does not touch real production hosts.
If you are running a real SSH call during demo prep, double-check
the host before `export ENGINE_HELM_SSH_OK=1`.
