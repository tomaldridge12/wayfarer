#!/usr/bin/env bash
# Extracts the vanilla textures the Guide draws with from the Minecraft client jar matching paperVersion,
# into guide/public/textures/minecraft/. Run again after a version bump. Usage: scripts/guide-textures.sh [client.jar]
set -euo pipefail
cd "$(dirname "$0")/.."
version=$(sed -n 's/^val paperVersion = "\([0-9.]*\)\.build.*/\1/p' plugin/build.gradle.kts)
jar=${1:-"$HOME/Library/Application Support/minecraft/versions/$version/$version.jar"}
[ -f "$jar" ] || { echo "No client jar at $jar (launch $version once in the launcher, or pass the jar path)"; exit 1; }
out=guide/public/textures/minecraft
rm -rf "$out"; mkdir -p "$out"
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
unzip -q "$jar" 'assets/minecraft/textures/item/*' 'assets/minecraft/textures/block/*' \
  'assets/minecraft/textures/entity/banner/*' 'assets/minecraft/textures/entity/zombie/*' \
  'assets/minecraft/textures/entity/skeleton/*' 'assets/minecraft/textures/entity/creeper/*' \
  'assets/minecraft/textures/entity/piglin/*' 'assets/minecraft/textures/entity/spider/*' \
  'assets/minecraft/textures/entity/enderman/*' 'assets/minecraft/textures/entity/witch*' \
  'assets/minecraft/textures/entity/player/wide/*' \
  'assets/minecraft/textures/gui/advancements/*' 'assets/minecraft/textures/gui/sprites/advancements/*' \
  'assets/minecraft/textures/gui/sprites/container/*' \
  -d "$tmp" || true
cp -R "$tmp/assets/minecraft/textures/." "$out/"
find "$out" -name '*.mcmeta' -delete
echo "Guide textures from $version: $(find "$out" -name '*.png' | wc -l | tr -d ' ') files, $(du -sh "$out" | cut -f1)"
