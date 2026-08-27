#!/bin/bash
INPUT_DATA=$(cat)

if echo "$INPUT_DATA" | grep -qE "180\.210\.129\.233|bpl-order-engine"; then
  echo "BLOCKED BY SAFETY HOOK: Interaction with staging container prohibited." >&2
  exit 1
fi

exit 0
