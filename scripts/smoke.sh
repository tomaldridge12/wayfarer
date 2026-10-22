#!/usr/bin/env bash
# Boot Paper, run the datapack function, assert the expected log lines. Exit 0 = pass.
set -euo pipefail
cd "$(dirname "$0")/.."
LOG=$(mktemp)
(sleep "${BOOT_WAIT:-45}"; echo "function mcdev:hello"; sleep 3; echo stop) | scripts/dev.sh >"$LOG" 2>&1 || true
fail=0
for pat in "McDev enabled" "Running function mcdev:hello"; do
  grep -qF "$pat" "$LOG" && echo "PASS: $pat" || { echo "FAIL: $pat"; fail=1; }
done
[ $fail -eq 0 ] || { echo "log: $LOG"; exit 1; }
