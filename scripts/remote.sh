#!/usr/bin/env bash
# Install and update the Paper server on the remote host. Usage: scripts/remote.sh <command>
#   key      one-time: copy your default SSH key to the host (needs one from ssh-keygen; you type the password)
#   setup    create ~/minecraft, download Paper, BlueMap, Chunky, Terralith, Incendium, Nullscape and Structory, write start.sh
#   sync     build the Wayfarer jar and rsync it plus the other plugin jars to ~/minecraft/plugins, then enchants
#   enchants make every ExcellentEnchants enchant reward-only on the host (scripts/ee_lockdown.py; restart to apply)
#   all      setup then sync
# Override with REMOTE=user@host REMOTE_DIR=minecraft PAPER_VERSION=26.3
set -euo pipefail
cd "$(dirname "$0")/.."
REMOTE=${REMOTE:-tom@192.168.68.59}
REMOTE_DIR=${REMOTE_DIR:-minecraft}
PAPER_VERSION=${PAPER_VERSION:-26.3}
# Third-party plugins not in Maven: copied from the local dev server so versions match what was tested.
LOCAL_PLUGINS=(ExcellentEnchants-5.4.3.jar nightcore-2.16.7.jar packetevents-spigot-2.14.0.jar)
BLUEMAP_URL=https://cdn.modrinth.com/data/swbUV1cr/versions/uHQIrUdf/bluemap-5.29-paper.jar
CHUNKY_URL=https://cdn.modrinth.com/data/fALzjamp/versions/MdY6JATr/Chunky-Bukkit-1.5.3.jar
INCENDIUM_URL='https://cdn.modrinth.com/data/ZVzW5oNS/versions/1phtTdhj/Incendium_Legacy_v5.5.4%2B26.3.zip'
NULLSCAPE_URL='https://cdn.modrinth.com/data/LPjGiSO4/versions/BJjbILcf/Nullscape_v2.0.1%2B26.3.zip'
STRUCTORY_URL='https://cdn.modrinth.com/data/aKCwCJlY/versions/EV74q3tR/Structory_v1.3.18%2B26.3.zip'
TERRALITH_URL='https://cdn.modrinth.com/data/8oi3bsk5/versions/fqnsIp6d/Terralith_v2.6.5%2B26.3.zip'

rsh() { ssh -o BatchMode=yes "$REMOTE" "$@"; }

cmd_key() { ssh-copy-id "$REMOTE"; }

cmd_setup() {
  rsh "REMOTE_DIR=$REMOTE_DIR PAPER_VERSION=$PAPER_VERSION BLUEMAP_URL='$BLUEMAP_URL' CHUNKY_URL='$CHUNKY_URL' INCENDIUM_URL='$INCENDIUM_URL' NULLSCAPE_URL='$NULLSCAPE_URL' STRUCTORY_URL='$STRUCTORY_URL' TERRALITH_URL='$TERRALITH_URL' bash -s" <<'REMOTE_SH'
set -euo pipefail
mkdir -p ~/"$REMOTE_DIR"/plugins ~/"$REMOTE_DIR"/world/datapacks
cd ~/"$REMOTE_DIR"
# No sudo needed: a private Temurin JDK 25 lives in ~/minecraft/jdk (the host has wget and tar, not curl).
if [ ! -x jdk/bin/java ]; then
  mkdir -p jdk
  wget -q -O jdk.tar.gz "https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse"
  tar -xzf jdk.tar.gz -C jdk --strip-components=1
  rm jdk.tar.gz
fi
jdk/bin/java -version 2>&1 | head -1
api=https://fill.papermc.io/v3/projects/paper/versions/$PAPER_VERSION/builds/latest
url=$(wget -qO- "$api" | python3 -c 'import sys,json;print(json.load(sys.stdin)["downloads"]["server:default"]["url"])')
[ -f paper.jar ] && echo "paper.jar exists, keeping" || wget -q -O paper.jar "$url"
[ -f plugins/bluemap.jar ] || wget -q -O plugins/bluemap.jar "$BLUEMAP_URL"
[ -f plugins/chunky.jar ] || wget -q -O plugins/chunky.jar "$CHUNKY_URL"
[ -f world/datapacks/Incendium.zip ] || wget -q -O world/datapacks/Incendium.zip "$INCENDIUM_URL"
[ -f world/datapacks/Nullscape.zip ] || wget -q -O world/datapacks/Nullscape.zip "$NULLSCAPE_URL"
[ -f world/datapacks/Structory.zip ] || wget -q -O world/datapacks/Structory.zip "$STRUCTORY_URL"
[ -f world/datapacks/Terralith.zip ] || wget -q -O world/datapacks/Terralith.zip "$TERRALITH_URL"
echo 'eula=true' > eula.txt
cat > start.sh <<'EOS'
#!/usr/bin/env bash
cd "$(dirname "$0")"
exec ./jdk/bin/java -Xms6G -Xmx6G -jar paper.jar --nogui
EOS
chmod +x start.sh
ls -la . plugins world/datapacks
REMOTE_SH
}

cmd_sync() {
  source scripts/env.sh
  ./gradlew :plugin:jar -q
  jar="" stage="" p=""
  jar=$(ls -t plugin/build/libs/plugin-*.jar | head -1)
  stage=$(mktemp -d)
  trap 'rm -rf "${stage:-}"' EXIT
  cp "$jar" "$stage/Wayfarer.jar"
  for p in "${LOCAL_PLUGINS[@]}"; do cp "server/plugins/$p" "$stage/"; done
  rsync -avz --checksum "$stage"/ "$REMOTE:$REMOTE_DIR/plugins/"
  cmd_enchants
  echo "Synced. Restart the server on the host to load new jars (Wayfarer's configs on the host are left alone)."
}

cmd_enchants() {
  # ExcellentEnchants writes its enchant files on its first boot; until then there is nothing to lock down.
  if rsh "test -d ~/$REMOTE_DIR/plugins/ExcellentEnchants/enchants"; then
    rsh "python3 - ~/$REMOTE_DIR/plugins/ExcellentEnchants/enchants" < scripts/ee_lockdown.py | tail -1
  else
    echo "ExcellentEnchants has not booted on the host yet; run 'scripts/remote.sh enchants' after its first start"
  fi
}

case "${1:-}" in
  key) cmd_key ;;
  setup) cmd_setup ;;
  sync) cmd_sync ;;
  enchants) cmd_enchants ;;
  all) cmd_setup; cmd_sync ;;
  *) sed -n '2,9p' "$0"; exit 1 ;;
esac
