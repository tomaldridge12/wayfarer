#!/usr/bin/env bash
# Boot Paper twice and assert log lines. Exit 0 = pass.
# Boot 1: default config, then reload. Boot 2: banners module switched off. Boot 3: unreadable banners config.
set -euo pipefail
cd "$(dirname "$0")/.."
CFG=server/plugins/Wayfarer/banners.yml
trap 'rm -f "$CFG"' EXIT

boot() { # log file, then console commands to run after boot
  local log=$1; shift
  (sleep "${BOOT_WAIT:-45}"; for c in "$@"; do echo "$c"; sleep 3; done; echo stop) | scripts/dev.sh >"$log" 2>&1 || true
}
check() { # log file, pattern
  grep -qF "$2" "$1" && echo "PASS: $2" || { echo "FAIL: $2 (log: $1)"; fail=1; }
}

fail=0
rm -f "$CFG"
LOG1=$(mktemp); boot "$LOG1" "mobbanners reload"
check "$LOG1" "Module banners enabled"
check "$LOG1" "Wayfarer enabled"
check "$LOG1" "Banners loaded: 5 banners, 5 families, 0 problems"
check "$LOG1" "Banners reloaded: 5 banners, 0 problems"

printf 'version: 1\nenabled: false\n' >"$CFG"
LOG2=$(mktemp); boot "$LOG2"
check "$LOG2" "Module banners skipped (disabled in config)"

printf 'banners: [unclosed\n' >"$CFG"
LOG3=$(mktemp); boot "$LOG3"
check "$LOG3" "Module banners failed to start, skipped"
check "$LOG3" "Wayfarer enabled"
[ $fail -eq 0 ]
