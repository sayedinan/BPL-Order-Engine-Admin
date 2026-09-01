#!/bin/bash
# block-plaintext-secrets.sh
# Blocks any Bash command or file write that appears to embed a plaintext secret
# (password, API key, private key body, JDBC URL with embedded password).
# Replaces the v0.2 guard-staging.sh hook, which is no longer relevant
# now that v0.3 does real SSH to engine servers.
#
# Exit code 2 = block. Exit code 0 = allow.

set -euo pipefail

INPUT=$(cat)

# Two tool shapes: Bash has tool_input.command, Write/Edit have tool_input.content
# (and Write also has tool_input.file_path).
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty')
CONTENT=$(echo "$INPUT" | jq -r '.tool_input.content // empty')
PAYLOAD="${COMMAND}${CONTENT}"

if [ -z "$PAYLOAD" ]; then
  exit 0
fi

# Patterns we refuse to see in any tool input. Each is a hard block.
# Adding to this list = expanding what the project considers a secret.
# Keep it tight: false positives slow everyone down.
PATTERNS=(
  # "password=<value>" or 'password: <value>' where the value looks like a
  # real secret: either a quoted string of 6+ chars, or an unquoted
  # identifier of 16+ chars. Short identifiers like `password: secret`
  # in function signatures are not blocked — the hook should not fire
  # on documentation that shows the server's expected request shape.
  '(password|passwd|pwd)\s*[:=]\s*["'\''][A-Za-z0-9!@#\$%^&*()_+\-]{6,}'
  '(password|passwd|pwd)\s*[:=]\s*[A-Za-z0-9!@#\$%^&*()_+\-]{16,}'
  # AWS-style access keys
  'AKIA[0-9A-Z]{16}'
  # Generic bearer tokens in code (token=xxx, apiKey: "xxx")
  '(api[_-]?key|secret|token)\s*[:=]\s*["'\''][A-Za-z0-9_\-]{16,}'
  # JDBC URL with embedded password
  'jdbc:[a-z]+://[^ ]*password=[^ &\s]+'
  # PEM private key bodies
  '-----BEGIN (RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----'
  # Hardcoded BPL dev/staging IPs (left in as a reminder, not a block)
  # 180\.210\.129\.233
)

for pat in "${PATTERNS[@]}"; do
  if echo "$PAYLOAD" | grep -qE "$pat"; then
    echo "Blocked: this input appears to embed a plaintext secret (matched: ${pat})." >&2
    echo "Use environment variables, Jasypt-encrypted values, or a secrets manager instead." >&2
    exit 2
  fi
done

exit 0
