#!/usr/bin/env bash
# Pega los .schem con WorldEdit 7.2.15 en un Mohist 1.20.1 de verdad (como el del dueño) y compara lo pegado con el
# archivo. Una bot operadora en creativo hace, para cada .schem (en x = 0, 300, 600…, y = 100, z = 0):
#   //perf neighbors off · //perf update off · //perf validation off · //schem load <nombre> · //paste -e
# y luego save-all; con el servidor parado, comparar.py. Termina con «WORLDEDIT <forge|bukkit>: TODO BIEN».
#   probar_worldedit.sh <carpeta de Mohist> <tfclient.jar> <carpeta con los .schem> <forge|bukkit>
# La carpeta de Mohist se monta con tools/mohist/montar.sh (lo hace este script si hace falta).
set -uo pipefail
AQUI=$(cd "$(dirname "$0")" && pwd)
MOHIST=$AQUI/../mohist
DIR=$1; JAR=$2; SCH=$(cd "$3" && pwd); TIPO=$4
bash "$MOHIST/montar.sh" "$DIR" "$JAR" >/dev/null
cd "$DIR"
case $TIPO in
  forge) URL=https://cdn.modrinth.com/data/1u6JkXh5/versions/Wrlqaul6/worldedit-mod-7.2.15.jar
         DEST=mods; SCHDIR=config/worldedit/schematics ;;
  bukkit) URL=https://cdn.modrinth.com/data/1u6JkXh5/versions/XL2dgNKo/worldedit-bukkit-7.2.15.jar
          DEST=plugins; SCHDIR=plugins/WorldEdit/schematics ;;
  *) echo "forge o bukkit"; exit 2 ;;
esac
mkdir -p we
[ -s "we/$(basename $URL)" ] || curl -fsSL -o "we/$(basename $URL)" "$URL"
rm -f mods/worldedit-*.jar plugins/worldedit-*.jar
cp "we/$(basename $URL)" "$DEST/"
rm -rf world world_nether world_the_end "$SCHDIR"
mkdir -p "$SCHDIR"
cp "$SCH"/*.schem "$SCHDIR/"
source "$MOHIST/comun.sh"
arrancar
sleep 15   # Mohist dice «Done» y aún echa a quien entra («server is still starting») unos segundos
conectar Ana
consola "op Ana" 2
consola "gamemode creative Ana" 2
di Ana "//perf neighbors off" 2
di Ana "//perf update off" 2
di Ana "//perf validation off" 2
ARGS=()
x=0
for f in "$SCH"/*.schem; do
  n=$(basename "$f" .schem)
  consola "tp Ana $x 100 0" 3
  di Ana "//schem load $n" 5
  di Ana "//paste -e" 12
  mira "$n pegado" Ana "The clipboard has been pasted at ($x, 100, 0)" sí
  ARGS+=("$f" "$x" 100 0)
  x=$((x + 300))
done
consola "save-all flush" 10
parar
python3 -I "$AQUI/comparar.py" world "${ARGS[@]}" || FALLOS=$((FALLOS + 1))
final "WORLDEDIT $TIPO"
