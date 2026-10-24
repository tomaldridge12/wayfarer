#!/usr/bin/env bash
# Publish the resource pack built from this commit as a GitHub release tagged pack-<12 hex of its sha1>.
# Run only after the server owner has OK'd this pack version. Never deletes or overwrites a release.
#   scripts/pack-release.sh --dry-run   print the tag, sha1 and URL; publish nothing
#   scripts/pack-release.sh             publish (asks gh; exits early if the release exists)
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
dry=0
[ "${1:-}" = "--dry-run" ] && dry=1

./gradlew -q :plugin:resourcePackProperties
props=plugin/build/generated/pack/wayfarer-pack.properties
zip=build/resourcepack/wayfarer-pack.zip
tag=$(sed -n 's/^tag=//p' "$props")
sha1=$(sed -n 's/^sha1=//p' "$props")
url=$(sed -n 's/^url=//p' "$props")
[ "$(shasum -a 1 "$zip" | cut -d' ' -f1)" = "$sha1" ] || { echo "zip and properties disagree; rebuild" >&2; exit 1; }
echo "tag  $tag"
echo "sha1 $sha1"
echo "url  $url"

if gh release view "$tag" >/dev/null 2>&1; then
  echo "Release $tag already exists; nothing to do"
  exit 0
fi
if [ $dry -eq 1 ]; then
  echo "Dry run: would create release $tag with $zip ($(wc -c <"$zip" | tr -d ' ') bytes)"
  exit 0
fi
notes=$(printf 'Wayfarer resource pack %s\n\nSHA-1: `%s`\n\nLicensed CC BY-NC-SA 4.0; see LICENSE.txt and CREDITS.md in the zip.\n' "$tag" "$sha1")
gh release create "$tag" "$zip" --title "Resource pack $tag" --notes "$notes"
echo "Published $url"
