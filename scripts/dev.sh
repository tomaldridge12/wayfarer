#!/usr/bin/env bash
# Start Paper with the plugin loaded.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
exec ./gradlew :plugin:runServer "$@"
