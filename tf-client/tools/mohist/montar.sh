#!/usr/bin/env bash
# Monta un servidor Mohist 1.20.1 de verdad, como el del dueño (Mohist + EssentialsX + EssentialsXChat + LuckPerms), con
# el jar del mod que se le pase. No compila nada: el jar sale de la Release o del artefacto «tfclient-jar» de GitHub
# Actions. Si la carpeta ya está montada, solo cambia el jar.
#   montar.sh <carpeta> <tfclient.jar>
set -euo pipefail
AQUI=$(cd "$(dirname "$0")" && pwd)
DIR=${1:?carpeta de trabajo}
JAR=$(cd "$(dirname "${2:?jar del mod}")" && pwd)/$(basename "$2")
mkdir -p "$DIR/mods" "$DIR/plugins" "$DIR/bot"
cd "$DIR"
baja() { [ -s "$2" ] || { echo "bajando $2"; curl -fsSL -o "$2" "$1"; }; }
baja https://api.mohistmc.com/project/mohist/1.20.1/builds/latest/download mohist.jar
baja "$(curl -fsSL https://metadata.luckperms.net/data/all | python3 -c 'import json,sys; print(json.load(sys.stdin)["downloads"]["bukkit"])')" plugins/LuckPerms.jar
baja https://github.com/EssentialsX/Essentials/releases/download/2.20.1/EssentialsX-2.20.1.jar plugins/EssentialsX.jar
baja https://github.com/EssentialsX/Essentials/releases/download/2.20.1/EssentialsXChat-2.20.1.jar plugins/EssentialsXChat.jar
cp "$JAR" mods/tfclient.jar
echo "eula=true" > eula.txt
[ -f server.properties ] || cat > server.properties <<'PROPS'
online-mode=false
enforce-secure-profile=false
level-type=minecraft\:flat
generate-structures=false
spawn-protection=0
server-port=25565
motd=TF prueba
max-players=10
view-distance=4
simulation-distance=4
spawn-monsters=false
spawn-animals=false
PROPS
cp "$AQUI/bot.js" "$AQUI/package.json" bot/
[ -d bot/node_modules/minecraft-protocol ] || (cd bot && npm install --silent --no-audit --no-fund >/dev/null)
echo "Mohist montado en $DIR con $(unzip -p mods/tfclient.jar META-INF/mods.toml | grep -m1 '^version=')"
