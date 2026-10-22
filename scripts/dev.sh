#!/usr/bin/env bash
# Copy the datapack into the dev world, then start Paper with the plugin loaded.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
mkdir -p server/world/datapacks
rsync -a --delete datapack/ server/world/datapacks/mcdev/
exec ./gradlew :plugin:runServer "$@"
