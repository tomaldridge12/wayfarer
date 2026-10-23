#!/usr/bin/env bash
# Boot Paper twice and assert log lines. Exit 0 = pass.
# Boot 1: default config, commands, awards. Boot 1b: restart, player data and the placed banner (label) persisted. Boot 2: banners module switched off. Boot 3: unreadable banners config.
set -euo pipefail
cd "$(dirname "$0")/.."
CFG=server/plugins/Wayfarer/banners.yml
DATA=server/plugins/Wayfarer/data
PLAYER=00000000-0000-0000-0000-000000000001
PLAYER2=00000000-0000-0000-0000-000000000002
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
rm -rf "$DATA"
LOG1=$(mktemp); boot "$LOG1" "mobbanners reload" "mobbanners give nobody nope" "mobbanners give nobody zombie" "mobbanners setkills $PLAYER zombie 100" "mobbanners progress $PLAYER" "forceload add 4000 4000" "forceload remove 4000 4000" "mobbanners place zombie 0 100 0 world" "mobbanners forcedrop skeleton $PLAYER2" "mobbanners progress $PLAYER2"
check "$LOG1" "Module banners enabled"
check "$LOG1" "Wayfarer enabled"
check "$LOG1" "Banners loaded: 5 banners, 5 families, 0 problems"
check "$LOG1" "Banner items built: 5 of 5"
check "$LOG1" "Banner feedback: action bar on, indicator on (max 25), edge particles off, marker effect off (luck)"
check "$LOG1" "Banners reloaded: 5 banners, 0 problems"
check "$LOG1" "Unknown banner 'nope'"
check "$LOG1" "No online player named 'nobody'"
check "$LOG1" "$PLAYER earned banner zombie"
check "$LOG1" "zombie: 100/100 (earned)"
check "$LOG1" "Placed banner registry loaded: "
check "$LOG1" "Marked chunk"
check "$LOG1" "Placed banner registry unloaded: "
check "$LOG1" "Placed banner zombie at 0,100,0 in world"
check "$LOG1" "$PLAYER2 earned banner skeleton by forced drop"
check "$LOG1" "skeleton: 0/100 (earned)"
grep -q '"version":1' "$DATA/banners/$PLAYER.json" && echo "PASS: player file written with version" || { echo "FAIL: player file"; fail=1; }

LOG1B=$(mktemp); boot "$LOG1B" "mobbanners progress $PLAYER" "mobbanners progress $PLAYER2" "forceload add 0 0" "mobbanners reload" "forceload remove 0 0"
check "$LOG1B" "zombie: 100/100 (earned)"
check "$LOG1B" "skeleton: 0/100"
check "$LOG1B" "skeleton: 0/100 (earned)"
check "$LOG1B" "Placed banner registry loaded: "
check "$LOG1B" "label(s) shown in chunk 0,0"
check "$LOG1B" "Banner labels refreshed: 1"

printf 'version: 1\nenabled: false\n' >"$CFG"
LOG2=$(mktemp); boot "$LOG2"
check "$LOG2" "Module banners skipped (disabled in config)"

printf 'banners: [unclosed\n' >"$CFG"
LOG3=$(mktemp); boot "$LOG3"
check "$LOG3" "Module banners failed to start, skipped"
check "$LOG3" "Wayfarer enabled"
traces=0
for log in "$LOG1" "$LOG1B" "$LOG2"; do
  if grep -q 'at dev.wayfarer' "$log"; then echo "FAIL: Wayfarer stack trace in $log"; traces=1; fail=1; fi
done
[ $traces -eq 1 ] || echo "PASS: no Wayfarer stack traces in normal boots"
[ $fail -eq 0 ]
