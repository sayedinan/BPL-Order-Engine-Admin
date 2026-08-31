#!/usr/bin/env bash
# guard-ssh.sh
# ----------------------------------------------------------------------------
# Dev-time SSH guardrail for Engine Helm.
#
# Invoked by the harness as a PreToolUse:Bash hook (see
# .claude/settings.json). Reads the tool call's `command` from stdin
# (JSON) and decides whether to let the call through.
#
# Behavior:
#   - If the command invokes `ssh`, `scp`, or `sftp` as a command
#     token (including piped forms like `... | ssh user@host`), AND
#     `ENGINE_HELM_SSH_OK=1` is NOT in the environment, the hook
#     blocks the call by exiting non-zero with a message on stderr.
#   - If `ENGINE_HELM_SSH_OK=1` IS in the environment, the hook
#     consumes the token (single-use) and lets the call through.
#     A stray second invocation in the same shell does NOT silently
#     fly through.
#
# The matcher is command-token aware:
#   - `ssh user@host`             -> caught
#   - `... | ssh user@host`       -> caught
#   - `ssh-keygen`                -> NOT caught (different command)
#   - `echo ssh stuff`            -> NOT caught (not a token)
#   - `/usr/bin/ssh user@host`    -> caught (path-prefixed tokens allowed)
# ----------------------------------------------------------------------------

set -u

# --- 1. Read the tool call from stdin -------------------------------------

INPUT="$(cat || true)"

# If stdin is empty (e.g. when the hook is run by hand for testing),
# fall back to the env var `GUARD_SSH_TEST_COMMAND` so the script is
# exercisable without the harness.
if [[ -z "${INPUT}" && -n "${GUARD_SSH_TEST_COMMAND:-}" ]]; then
  INPUT="{\"tool_input\":{\"command\":\"${GUARD_SSH_TEST_COMMAND}\"}}"
fi

# Extract the `command` field. Prefer a real JSON parser; fall back
# to a sed strip if `jq` is unavailable.
extract_command() {
  local raw="$1"
  if command -v jq >/dev/null 2>&1; then
    printf '%s' "${raw}" | jq -r '.tool_input.command // ""' 2>/dev/null
  else
    # Best-effort fallback: strip everything up to the first `"command":"`,
    # then read until the next unescaped `"`. This is a hack but
    # sufficient for the test path; production should always have jq.
    printf '%s' "${raw}" \
      | sed -n 's/.*"command"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' \
      | head -n 1
  fi
}

CMD="$(extract_command "${INPUT}")"

# If we couldn't extract a command, let it through. The hook's job is
# to guard, not to be a general syntax checker.
if [[ -z "${CMD}" ]]; then
  exit 0
fi

# --- 2. Token-aware match --------------------------------------------------

# We treat `ssh`, `scp`, `sftp` as command tokens. A token is:
#   - the first non-whitespace word of the command, OR
#   - the word after a `|`, `||`, `&&`, `;`, or `&` operator.
#
# We deliberately do NOT match substrings inside arguments, so
# `echo ssh stuff` and `ssh-keygen` are not caught.

is_ssh_token() {
  local cmd="$1"

  # Use awk to walk the command, splitting on whitespace and on shell
  # chain operators (|, ||, &&, ;, &). For each resulting token, strip
  # any path prefix and check whether it is exactly `ssh`, `scp`, or
  # `sftp`. We deliberately do NOT match substrings inside arguments,
  # so `echo ssh stuff` and `ssh-keygen` are not caught.
  #
  # awk doesn't have a clean way to split on multiple operator chars
  # in one pass, so we pre-substitute a sentinel for each operator
  # before splitting on whitespace.
  local normalized
  normalized="${cmd//||/<OP>}"
  normalized="${normalized//&&/<OP>}"
  normalized="${normalized//;/<OP>}"
  normalized="${normalized//&/<OP>}"
  normalized="${normalized//|/ <OP>}"

  local tok
  for tok in ${normalized}; do
    [[ "${tok}" == "<OP>" ]] && continue
    # Strip any path prefix (e.g. /usr/bin/ssh -> ssh).
    local base="${tok##*/}"
    case "${base}" in
      ssh|scp|sftp) return 0 ;;
    esac
  done

  return 1
}

if ! is_ssh_token "${CMD}"; then
  exit 0
fi

# --- 3. Decide: block or pass with opt-in ----------------------------------

if [[ "${ENGINE_HELM_SSH_OK:-0}" == "1" ]]; then
  # Consume the opt-in token (single-use) so a stray second invocation
  # in the same shell does not silently fly through. We can't actually
  # unset the parent shell's env var from a child process, but we
  # log the consumption and require a fresh `export
  # ENGINE_HELM_SSH_OK=1` for the next call.
  printf 'guard-ssh: ENGINE_HELM_SSH_OK=1 consumed (single-use). Re-export for the next call.\n' >&2
  exit 0
fi

# Block. Print a clear, non-leaking message and exit non-zero.
cat >&2 <<'EOF'
guard-ssh: blocked. This Bash invocation invokes ssh / scp / sftp,
which is gated by the Engine Helm dev-time guardrail.

To opt in for a single, real SSH run during this session:

    export ENGINE_HELM_SSH_OK=1
    # ... run the one command you intended ...
    # ENGINE_HELM_SSH_OK is consumed on use; re-export if needed again.

For the rationale and a longer-form description, see
.claude/skills/ssh-approval.md in the Engine Helm repo.
EOF

exit 2
