#!/usr/bin/env bash
# Boot Paper twice and assert log lines. Exit 0 = pass.
# Boot 1: default config. Boot 2: banners module switched off in its config.
set -euo pipefail
cd "$(dirname "$0")/.."
CFG=server/plugins/Wayfarer/banners.yml
trap 'rm -f "$CFG"' EXIT

boot() { # log file
  (sleep "${BOOT_WAIT:-45}"; echo stop) | scripts/dev.sh >"$1" 2>&1 || true
}
check() { # log file, pattern
  grep -qF "$2" "$1" && echo "PASS: $2" || { echo "FAIL: $2 (log: $1)"; fail=1; }
}

fail=0
rm -f "$CFG"
LOG1=$(mktemp); boot "$LOG1"
check "$LOG1" "Module banners enabled"
check "$LOG1" "Wayfarer enabled"

printf 'version: 1\nenabled: false\n' >"$CFG"
LOG2=$(mktemp); boot "$LOG2"
check "$LOG2" "Module banners skipped (disabled in config)"
[ $fail -eq 0 ]
