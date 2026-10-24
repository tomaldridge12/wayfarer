#!/usr/bin/env bash
# Boot a throwaway Paper server several times and assert log lines. Exit 0 = pass.
# Boot 1: default config, /wayfarer commands, awards, bosses (spawn every variant, a forced defeat with loot). Boot 1b: restart (/mobbanners alias), player data, placed banner and head persisted.
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
rm -f "$CFG" "$RUN/plugins/Wayfarer/bosses.yml" # start from the shipped configs
rm -rf "$DATA"
LOG1=$LOGS/log1.log; boot "$LOG1" "forceload add 20 20" "setblock 20 101 20 minecraft:air" "setblock 24 101 20 minecraft:air" "setblock 20 100 20 minecraft:campfire[lit=true]" "setblock 20 101 20 minecraft:cauldron" "setblock 24 101 20 minecraft:cauldron" "wayfarer alloys load 20 101 20 world iron_ingot 4 $PLAYER" "wayfarer alloys load 20 101 20 world coal_block 1 $PLAYER" "wayfarer alloys load 24 101 20 world iron_ingot 4 $PLAYER" "wayfarer alloys load 24 101 20 world coal_block 1 $PLAYER" "wayfarer alloys load 24 101 20 world dirt 1 $PLAYER" "wayfarer reload" "wayfarer give nobody nope" "wayfarer give nobody zombie" "wayfarer setcount $PLAYER kill:zombie 400" "wayfarer progress $PLAYER" "forceload add 4000 4000" "forceload remove 4000 4000" "wayfarer place zombie 0 100 0 world" "wayfarer placehead $PLAYER zombie 2 100 0" "wayfarer forcedrop $PLAYER2 skeleton" "wayfarer forcedrop $PLAYER2 relic:zombie_weapon_2" "wayfarer progress $PLAYER2" "forceload add ~-16 ~-16 ~112 ~16" "wayfarer boss spawnall 1" "wayfarer boss killall" "wayfarer boss spawnall 5" "wayfarer boss killall" "wayfarer boss spawn rotting_warlord rotting_warlord 5" "wayfarer boss spawn bone_marshal bone_marshal 3 guardian" "wayfarer boss defeat $PLAYER" "forceload remove ~-16 ~-16 ~112 ~16" "wayfarer setcount $PLAYER kill:incendium:knight 2" "wayfarer setcount $PLAYER harvest:wheat 3" "wayfarer setcount $PLAYER discover:biome/terralith:alpine_grove 1" "wayfarer stats $PLAYER harvest:" "wayfarer stats $PLAYER discover:" "wayfarer pack" "wayfarer pack send nobody" "wayfarer setcount $PLAYER kill:zombie" "wayfarer stats $PLAYER kill:" "wayfarer alloys list" "wayfarer alloys give nobody ingot:steel" "wayfarer alloys reload" "wayfarer stats $PLAYER forge:" "forceload remove 20 20"
check "$LOG1" "Command /wayfarer registered: core [progress, claim, stats, setcount, pack], banners [reload, give, place, placehead, forcedrop], bosses [boss], alloys [alloys]"
check "$LOG1" "Module alloys enabled"
check "$LOG1" "Alloy gear built: 60 of 60"
check "$LOG1" "Alloy recipes registered: 10 smithing upgrades, 6 template duplications (16 live)"
check "$LOG1" "Alloy gear check: iron_pickaxe model wayfarer:steel_pickaxe, 1000 durability, speed 5.5, repairable false, attributes [attack_damage 3.0, attack_speed"
check "$LOG1" "Alloy gear check: netherite_chestplate model wayfarer:adamant_chestplate, 720 durability, worn wayfarer:adamant, repairable false, attributes [armor 8.0, armor_toughness 4.0, knockback_resistance 0.1, movement_speed -0.025]"
check "$LOG1" "Alloys loaded: 6 alloys (steel, bronze, shakudo, electrum, hepatizon, adamant), 0 problems; endless repair on (cap 30 levels)"
check "$LOG1" "Steel: 4 iron_ingot + 1 coal_block at low heat, 30s -> 1 ingot; upgrades iron gear; template from bone_marshal"
check "$LOG1" "Alloys reloaded: 6 alloys, 0 problems"
check "$LOG1" "Crucibles loaded: 0 in loaded chunks"
check "$LOG1" "Crucible at 20,101,20 took 1 coal_block; now {iron_ingot=4, coal_block=1}, melts at low heat"
check "$LOG1" "Crucible at 24,101,20 took 1 coal_block; now {iron_ingot=4, coal_block=1}, no_heat with no heat"
check "$LOG1" "'dirt' is not a crucible ingredient"
check "$LOG1" "[alloys] Crucible at 20,101,20 melted 1 steel for $PLAYER"
check "$LOG1" "forge:steel = 1"
check "$LOG1" "Progression loaded: 8 excluded spawn reasons, min lifetime 0 ticks, 0 problems"
check "$LOG1" "Pity table loaded: 2 drops (wither_skeleton_skull every 40, trident every 12)"
check "$LOG1" "Stage markers loaded: 6 (story/smelt_iron, story/mine_diamond, story/enter_the_nether, nether/netherite_armor, end/kill_dragon, end/elytra)"
check "$LOG1" "Set $PLAYER kill:zombie to 400; newly earned: [banner:zombie, head:zombie, relic:zombie_weapon"
check "$LOG1" "Usage: /wayfarer setcount"
check "$LOG1" "Counters for $PLAYER ($PLAYER) starting 'kill:': 2"
check "$LOG1" "kill:incendium:knight = 2"
check "$LOG1" "kill:zombie = 400"
check "$LOG1" "starting 'harvest:': 1"
check "$LOG1" "discover:biome/terralith:alpine_grove = 1"
check "$LOG1" "Resource pack offered: pack-"
check "$LOG1" "loaded by 0 of 0 online"  # reachable or not depends on GitHub, so only the shape is checked
check "$LOG1" "Module banners enabled"
check "$LOG1" "Module bosses enabled"
check "$LOG1" "Bosses loaded: 9 bosses, 23 variants, 0 problems; 8 spawn naturally, 7 guard structures"
check "$LOG1" "Spawned 23 boss variants at ★1"
check "$LOG1" "Spawned 23 boss variants at ★5"
check "$LOG1" "Removed 23 bosses"
check "$LOG1" "Defeated 2 bosses"
check "$LOG1" "Spawned bone_marshal/bone_marshal ★3 as a guardian at"
check "$LOG1" "[alloys] Guardian bone_marshal: template:steel for $PLAYER (offline, not given)"
check "$LOG1" "$PLAYER earned banner rotting_warlord by defeating rotting_warlord"
check "$LOG1" "Boss rotting_warlord ★5 loot for $PLAYER: [head, "
check "$LOG1" "Wayfarer enabled"
check "$LOG1" "Banners loaded: 14 banners, 14 families, 0 problems"
check "$LOG1" "Banner items built: 14 of 14"
check "$LOG1" "Ladder loaded: 14 trophy heads, 76 relics"
check "$LOG1" "Ladders registered by banners: 15 (all, zombie, skeleton, creeper, spider, enderman, rotting_warlord, "
check "$LOG1" "Relic items built: 76 of 76"
check "$LOG1" "relic enchants can still be found outside Wayfarer"
check "$LOG1" "Banner feedback: action bar on, damage numbers on (max 40), edge particles off, marker effect off (luck)"
check "$LOG1" "Banners reloaded: 14 banners, 0 problems"
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
grep -q '^{"version":1,' "$DATA/progression/$PLAYER.json" && echo "PASS: progression record written with version 1" || { echo "FAIL: progression record"; fail=1; }

LOG1B=$LOGS/log1b.log; boot "$LOG1B" "forceload add 20 20" "wayfarer stats $PLAYER kill:zombie" "mobbanners progress $PLAYER" "wayfarer progress $PLAYER2" "forceload add 0 0" "mobbanners reload" "forceload remove 0 0" "forceload remove 20 20"
check "$LOG1B" "kill:zombie = 400"
check "$LOG1B" "[alloys] 1 crucible(s) loaded in chunk 1,1"
check "$LOG1B" "✔  Zombie Banner"
check "$LOG1B" "✔  Zombie Trophy"
check "$LOG1B" "✔  Zombie Cleaver"
check "$LOG1B" "✔  Zombie Cleaver II"
check "$LOG1B" "0/100  Skeleton Banner"
check "$LOG1B" "All mobs · 402 kills"  # 400 zombies set, two boss defeats (warlord and guardian)
check "$LOG1B" "Rotting Warlord · 1 defeat"
check "$LOG1B" "✔  Skeleton Banner"
check "$LOG1B" "Placed banner registry loaded: "
check "$LOG1B" "2 label(s) shown in chunk 0,0"
check "$LOG1B" "Banner labels refreshed: 2"

printf 'version: 1\nenabled: false\n' >"$CFG"
LOG2=$LOGS/log2.log; boot "$LOG2"
check "$LOG2" "Module banners skipped (disabled in config)"
check "$LOG2" "Command /wayfarer registered: core [progress, claim, stats, setcount, pack], bosses [boss], alloys [alloys]"

# Boot 4: without ExcellentEnchants, relics still build and only lose those enchants.
rm -f "$CFG"
EE=$(ls "$RUN"/plugins/ExcellentEnchants-*.jar 2>/dev/null | head -1)
if [ -n "$EE" ]; then
  mkdir -p "$RUN/plugins-off"
  mv "$EE" "$RUN/plugins-off/"
  trap 'rm -f "$CFG"; mv "$RUN"/plugins-off/*.jar "$RUN/plugins/" 2>/dev/null || true' EXIT
  LOG4=$LOGS/log4.log; boot "$LOG4"
  check "$LOG4" "Relic items built: 76 of 76"
  check "$LOG4" "ExcellentEnchants is not installed; relics are given without its enchants"
  check "$LOG4" "Wayfarer enabled"
  mv "$RUN"/plugins-off/*.jar "$RUN/plugins/"
else
  echo "SKIP: ExcellentEnchants is not in $RUN/plugins, so the without-it boot proves nothing"
fi

printf 'banners: [unclosed\n' >"$CFG"
LOG3=$LOGS/log3.log; boot "$LOG3"
check "$LOG3" "Module banners failed to start, skipped"
check "$LOG3" "Command /wayfarer registered: core [progress, claim, stats, setcount, pack], bosses [boss], alloys [alloys]"
check "$LOG3" "Wayfarer enabled"
traces=0
for log in "$LOG1" "$LOG1B" "$LOG2"; do
  if grep -q 'at dev.wayfarer' "$log"; then echo "FAIL: Wayfarer stack trace in $log"; traces=1; fail=1; fi
done
[ $traces -eq 1 ] || echo "PASS: no Wayfarer stack traces in normal boots"
[ $fail -eq 0 ]
