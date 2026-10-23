#!/usr/bin/env bash
# Boot a throwaway Paper server several times and assert log lines. Exit 0 = pass.
# Boot 1: default config, commands, awards. Boot 1b: restart, player data, placed banner and head persisted.
# Boot 2: banners module switched off. Boot 4: without ExcellentEnchants. Boot 3: unreadable banners config.
set -euo pipefail
cd "$(dirname "$0")/.."
# A throwaway server of its own (port 25566), so the smoke test never touches server/, the world you play in.
RUN=build/smoke-server
mkdir -p "$RUN"
[ -f "$RUN/server.properties" ] || printf 'server-port=25566\nonline-mode=false\n' >"$RUN/server.properties"
CFG=$RUN/plugins/Wayfarer/banners.yml
DATA=$RUN/plugins/Wayfarer/data
PLAYER=00000000-0000-0000-0000-000000000001
PLAYER2=00000000-0000-0000-0000-000000000002
trap 'rm -f "$CFG"' EXIT

# Starts the server, waits for its "Done" line (not a fixed sleep), sends each console command a second apart,
# then stops it. Boots take about 15 seconds instead of a minute.
# Ends any leftover smoke server still holding this throwaway world (never touches server/).
clear_stale() {
  local lock="$RUN/world/session.lock" n=0 pids
  while pids=$(lsof -t "$lock" 2>/dev/null) && [ -n "$pids" ] && [ "$n" -lt 40 ]; do
    [ "$n" -eq 20 ] && kill $pids 2>/dev/null
    sleep 0.5
    n=$((n + 1))
  done
}
trap '' PIPE # a server that died early must not kill the script; its checks fail instead

boot() { # log file, then console commands to run once the server is up
  local log=$1; shift
  clear_stale
  local fifo
  fifo=$(mktemp -u)
  mkfifo "$fifo"
  scripts/dev.sh -PrunDir="$RUN" <"$fifo" >"$log" 2>&1 &
  local pid=$!
  exec 3>"$fifo"
  local ticks=0
  until grep -q 'Done (' "$log" 2>/dev/null; do
    sleep 0.5
    ticks=$((ticks + 1))
    if ! kill -0 "$pid" 2>/dev/null || [ "$ticks" -gt 360 ]; then break; fi
  done
  for c in "$@"; do echo "$c" >&3 2>/dev/null || true; sleep "${CMD_WAIT:-1}"; done
  echo stop >&3 2>/dev/null || true
  exec 3>&-
  wait "$pid" || true
  rm -f "$fifo"
  # Gradle returns a moment before the server process lets go of the world; wait (or end it) before the next boot.
  clear_stale
}
check() { # log file, pattern
  grep -qF "$2" "$1" && echo "PASS: $2" || { echo "FAIL: $2 (log: $1)"; fail=1; }
}

fail=0
LOGS=build/smoke-logs
rm -rf "$LOGS" && mkdir -p "$LOGS"
rm -f "$CFG"
rm -rf "$DATA"
LOG1=$LOGS/log1.log; boot "$LOG1" "mobbanners reload" "mobbanners give nobody nope" "mobbanners give nobody zombie" "mobbanners setkills $PLAYER zombie 400" "mobbanners setkills $PLAYER all 250" "mobbanners progress $PLAYER" "forceload add 4000 4000" "forceload remove 4000 4000" "mobbanners place zombie 0 100 0 world" "mobbanners placehead $PLAYER zombie 2 100 0" "mobbanners forcedrop $PLAYER2 skeleton" "mobbanners forcedrop $PLAYER2 relic:zombie_weapon_2" "mobbanners progress $PLAYER2"
check "$LOG1" "Module banners enabled"
check "$LOG1" "Wayfarer enabled"
check "$LOG1" "Banners loaded: 5 banners, 5 families, 0 problems"
check "$LOG1" "Banner items built: 5 of 5"
check "$LOG1" "Ladder loaded: 5 trophy heads, 21 relics"
check "$LOG1" "Relic items built: 21 of 21"
check "$LOG1" "relic enchants can still be found outside Wayfarer"
check "$LOG1" "Banner feedback: action bar on, damage numbers on (max 40), edge particles off, marker effect off (luck)"
check "$LOG1" "Banners reloaded: 5 banners, 0 problems"
check "$LOG1" "Unknown banner 'nope'"
check "$LOG1" "No online player named 'nobody'"
check "$LOG1" "$PLAYER earned banner zombie"
check "$LOG1" "$PLAYER earned head zombie"
check "$LOG1" "$PLAYER earned relic zombie_weapon"
check "$LOG1" "✦ A Wayfarer earned the Zombie Cleaver"
check "$LOG1" "✔  Zombie Banner"
check "$LOG1" "Placed banner registry loaded: "
check "$LOG1" "Marked chunk"
check "$LOG1" "Placed banner registry unloaded: "
check "$LOG1" "Placed banner zombie at 0,100,0 in world"
check "$LOG1" "Placed head zombie of $PLAYER at 2,100,0"
check "$LOG1" "$PLAYER2 earned banner skeleton by forced drop"
check "$LOG1" "$PLAYER2 earned relic zombie_weapon_2 by forced drop"
check "$LOG1" "✔  Skeleton Banner"
grep -q '"version":3' "$DATA/banners/$PLAYER.json" && echo "PASS: player file written with version" || { echo "FAIL: player file"; fail=1; }

LOG1B=$LOGS/log1b.log; boot "$LOG1B" "mobbanners progress $PLAYER" "mobbanners progress $PLAYER2" "forceload add 0 0" "mobbanners reload" "forceload remove 0 0"
check "$LOG1B" "✔  Zombie Banner"
check "$LOG1B" "✔  Zombie Trophy"
check "$LOG1B" "✔  Zombie Cleaver"
check "$LOG1B" "✔  Zombie Cleaver II"
check "$LOG1B" "0/100  Skeleton Banner"
check "$LOG1B" "All mobs · 250 kills"
check "$LOG1B" "✔  Skeleton Banner"
check "$LOG1B" "Placed banner registry loaded: "
check "$LOG1B" "2 label(s) shown in chunk 0,0"
check "$LOG1B" "Banner labels refreshed: 2"

printf 'version: 1\nenabled: false\n' >"$CFG"
LOG2=$LOGS/log2.log; boot "$LOG2"
check "$LOG2" "Module banners skipped (disabled in config)"

# Boot 4: without ExcellentEnchants, relics still build and only lose those enchants.
rm -f "$CFG"
EE=$(ls "$RUN"/plugins/ExcellentEnchants-*.jar 2>/dev/null | head -1)
if [ -n "$EE" ]; then
  mkdir -p "$RUN/plugins-off"
  mv "$EE" "$RUN/plugins-off/"
  trap 'rm -f "$CFG"; mv "$RUN"/plugins-off/*.jar "$RUN/plugins/" 2>/dev/null || true' EXIT
  LOG4=$LOGS/log4.log; boot "$LOG4"
  check "$LOG4" "Relic items built: 21 of 21"
  check "$LOG4" "ExcellentEnchants is not installed; relics are given without its enchants"
  check "$LOG4" "Wayfarer enabled"
  mv "$RUN"/plugins-off/*.jar "$RUN/plugins/"
else
  echo "SKIP: ExcellentEnchants is not in $RUN/plugins, so the without-it boot proves nothing"
fi

printf 'banners: [unclosed\n' >"$CFG"
LOG3=$LOGS/log3.log; boot "$LOG3"
check "$LOG3" "Module banners failed to start, skipped"
check "$LOG3" "Wayfarer enabled"
traces=0
for log in "$LOG1" "$LOG1B" "$LOG2"; do
  if grep -q 'at dev.wayfarer' "$log"; then echo "FAIL: Wayfarer stack trace in $log"; traces=1; fail=1; fi
done
[ $traces -eq 1 ] || echo "PASS: no Wayfarer stack traces in normal boots"
[ $fail -eq 0 ]
