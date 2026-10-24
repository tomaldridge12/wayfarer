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
  'assets/minecraft/textures/entity/player/wide/*' 'assets/minecraft/textures/entity/shield/shield_base_nopattern.png' \
  'assets/minecraft/textures/gui/advancements/*' 'assets/minecraft/textures/gui/sprites/advancements/*' \
  'assets/minecraft/textures/gui/sprites/container/*' \
  -d "$tmp" || true
cp -R "$tmp/assets/minecraft/textures/." "$out/"
find "$out" -name '*.mcmeta' -delete
# The shield's inventory icon is a 3D model, so draw a flat 16x16 one from its own texture's colours.
python3 - "$out" <<'PY'
import sys
from PIL import Image
out = sys.argv[1]
tex = Image.open(f"{out}/entity/shield/shield_base_nopattern.png").convert("RGBA")
wood = [tex.getpixel((x, y)) for x, y in ((3, 4), (5, 9), (8, 14), (4, 18))]
rim, rim_dark = (176, 176, 176, 255), (110, 110, 110, 255)
icon = Image.new("RGBA", (16, 16))
rows = {1: (3, 12), 2: (2, 13), 12: (3, 12), 13: (4, 11), 14: (6, 9)}
for y in range(1, 15):
    x0, x1 = rows.get(y, (2, 13))
    for x in range(x0, x1 + 1):
        edge = x in (x0, x1) or y in (1, 14) or (y == 13 and x in (4, 11)) or (y == 12 and x in (3, 12))
        icon.putpixel((x, y), (rim_dark if x >= 8 or y >= 12 else rim) if edge else wood[(x * 3 + y) % len(wood)])
icon.save(f"{out}/item/shield.png")
PY
echo "Guide textures from $version: $(find "$out" -name '*.png' | wc -l | tr -d ' ') files, $(du -sh "$out" | cut -f1)"
