#!/bin/bash
INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty')

if echo "$COMMAND" | grep -qE '180\.210\.129\.233|bpl-order-engine'; then
  echo "Blocked: this command references the live staging BPL Order Engine container. Get explicit confirmation before running anything against it — use the in-memory mock instead." >&2
  exit 2
fi

exit 0