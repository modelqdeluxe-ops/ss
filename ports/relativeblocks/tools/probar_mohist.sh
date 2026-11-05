#!/usr/bin/env bash
# Prueba el port de Relative Blocks en un Mohist 1.20.1 de verdad, como el del dueño (Mohist + EssentialsX +
# EssentialsXChat + LuckPerms + TF Client), con jugadores bot (tf-client/tools/mohist/README.md):
#   - arranca sin errores del mod ni de sus mixins, con una losa vertical por losa y sus recetas;
#   - Ana (del grupo de la zona) junta flores y antorchas en un hueco, mezcla losas de roble y piedra, y pone una losa
#     vertical de roble con otra de piedra en su otra mitad;
#   - Carlos (fuera del grupo de la zona de Ana) no puede añadir nada a un grupo ni romper uno de sus bloques
#     (las protecciones del TF Client y las de los plugins de Bukkit ven lo que hace el mod);
#   - Ana rompe uno de los dos bloques de un grupo y se queda el otro;
#   - tras reiniciar, los grupos siguen igual (se guardan).
# Termina con «RELATIVE BLOCKS EN MOHIST: TODO BIEN» (y sale con 0) o con el número de fallos.
#   probar_mohist.sh <carpeta de trabajo> <relativeblocks-forge.jar> <tfclient.jar>
set -uo pipefail
AQUI=$(cd "$(dirname "$0")" && pwd)
MOHIST=$(cd "$AQUI/../../../tf-client/tools/mohist" && pwd)
DIR=${1:?carpeta de trabajo}
RB=$(cd "$(dirname "${2:?jar del port}")" && pwd)/$(basename "$2")
TF=${3:?jar del TF Client}
"$MOHIST/montar.sh" "$DIR" "$TF" || exit 1
cp "$RB" "$DIR/mods/relativeblocks.jar"
cd "$DIR"
source "$MOHIST/comun.sh"
# un mundo nuevo cada vez (plano), con la zona de Ana (100x100 en 0,-60,0; grupo: Ana y Beto)
MUNDO="rb$(date +%s)"
grep -v '^level-name=' server.properties > server.properties.tmp && mv server.properties.tmp server.properties
echo "level-name=$MUNDO" >> server.properties
# (la marca de la limpieza de la 1.3.120 del TF Client: sin ella, en un mundo nuevo borra claimblocks_data.json)
ANTES_DE_ARRANCAR='mkdir -p "$MUNDO/tfclient" && touch "$MUNDO/tfclient/limpieza-1.3.120.hecha" && python3 "$MOHIST/protecciones_prueba.py" "$MUNDO/claimblocks_data.json"'
arrancar
echo "Mundo: $MUNDO · $(unzip -p mods/relativeblocks.jar META-INF/mods.toml | grep -m1 '^version=')"

# (EssentialsX tapa /item y /recipe con los suyos: van con «minecraft:» delante)
# lo que dice el servidor tras una orden (entre una marca y la siguiente)
tras() {
  local m="MARCA$RANDOM$RANDOM"
  consola "say $m" 0.4
  consola "$1" "${2:-1}"
  awk -v m="$m" 'f{print} index($0,m){f=1}' server.out
}
# cuántos bloques tiene el grupo de x y z (0 si no es un grupo)
miembros() { tras "data get block $1 $2 $3" | grep -o 'Cell: [0-9]b' | wc -l; }
es() { tras "execute if block $1 $2 $3 $4" | grep -q "Test passed"; }
dame() { consola "minecraft:item replace entity $1 hotbar.0 with $2" 0.6; }

# 1. El arranque
N=$(grep -o 'Registered [0-9]* vertical slabs' server.out | grep -o '[0-9]*' | head -1)
comprueba "una losa vertical por losa (${N:-0})" sí test "${N:-0}" -ge 50
R=$(grep -o 'Worked out [0-9]* vertical slab recipes' server.out | grep -o '[0-9]*' | tail -1)
comprueba "y sus recetas (${R:-0})" sí test "${R:-0}" -ge 40
comprueba "sin errores del mod en el arranque" no grep -qiE "(ERROR|Exception|WARN).*relativeblocks|relativeblocks.*(Exception|ERROR)" server.out
comprueba "sin mixins rotos" no grep -qE "Mixin apply failed|InvalidInjectionException|MixinTransformerError|InjectionError|Critical injection failure" server.out

conectar Ana Carlos
consola "op Ana" 0.5
consola "gamemode creative Ana" 0.5
comprueba "existe la receta de la losa vertical de roble" sí bash -c "$(declare -f tras consola); cd '$PWD'; tras 'minecraft:recipe give Ana relativeblocks:vertical_slab/minecraft/oak_slab' | grep -q 'Unlocked 1 recipe'"
for p in "2 -60 4" "4 -60 4" "6 -60 4" "8 -60 4"; do consola "setblock $p air" 0.2; done

# 2. Ana junta bloques
consola "tp Ana 5.5 -60 1.5 0 30" 1.5
consola "setblock 2 -60 4 minecraft:poppy" 0.5
dame Ana minecraft:dandelion
di Ana "!usar 2 -61 4 1 0.85 1 0.85" 1.5
comprueba "Ana: amapola + diente de león en un grupo" sí test "$(miembros 2 -60 4)" -eq 2
consola "setblock 4 -60 4 minecraft:torch" 0.5
dame Ana minecraft:redstone_torch
di Ana "!usar 4 -61 4 1 0.85 1 0.5" 1.5
comprueba "Ana: antorcha + antorcha de redstone en un grupo" sí test "$(miembros 4 -60 4)" -eq 2
consola "setblock 6 -60 4 minecraft:oak_slab[type=bottom]" 0.5
dame Ana minecraft:stone_slab
di Ana "!usar 6 -60 4 1 0.5 0.5 0.5" 1.5
comprueba "Ana: losa de roble abajo + losa de piedra arriba" sí test "$(miembros 6 -60 4)" -eq 2
dame Ana relativeblocks:vertical_slab/minecraft/oak_slab
di Ana "!usar 8 -61 4 1 0.5 1 0.2" 1.5
comprueba "Ana: pone una losa vertical de roble" sí es 8 -60 4 "relativeblocks:vertical_slab/minecraft/oak_slab[facing=north]"
dame Ana relativeblocks:vertical_slab/minecraft/stone_slab
di Ana "!usar 8 -60 4 1 0.5 1 0.75" 1.5
comprueba "Ana: y una de piedra en su otra mitad" sí test "$(miembros 8 -60 4)" -eq 2

# 3. Carlos, de fuera del grupo de la zona de Ana: ni juntar, ni romper. Fuera de la zona, sí junta (control)
comprueba "la zona de Ana está cargada" no grep -q "No existing claims file" server.out
consola "setblock 10 -60 6 minecraft:poppy" 0.5
consola "tp Carlos 10.5 -60 3.5 0 40" 1.5
dame Carlos minecraft:dandelion
di Carlos "!usar 10 -61 6 1 0.85 1 0.85" 1.5
comprueba "Carlos (fuera del grupo) no junta una flor con otra en la zona de Ana" sí es 10 -60 6 minecraft:poppy
consola "tp Carlos 4.5 -60 2.5 0 40" 1.5
di Carlos "!romper 4 -60 4" 1.5
di Carlos "!romper 6 -60 4" 5
comprueba "Carlos no rompe ninguna de las antorchas del grupo" sí test "$(miembros 4 -60 4)" -eq 2
comprueba "ni ninguna de las losas mezcladas (ni terminando de picar)" sí test "$(miembros 6 -60 4)" -eq 2
consola "tp Carlos 130.5 -60 129.5 0 40" 3
consola "setblock 130 -60 132 minecraft:poppy" 0.5
di Carlos "!usar 130 -61 132 1 0.85 1 0.85" 1.5
comprueba "fuera de la zona, Carlos sí junta flores (control)" sí test "$(miembros 130 -60 132)" -eq 2

# 4. Ana rompe uno de los dos bloques de un grupo: se va uno y se queda el otro
consola "tp Ana 4.5 -59 2.5 0 50" 1.5
di Ana "!mirar 0 50" 0.5
di Ana "!romper 4 -60 4" 1.5
comprueba "Ana rompe uno: queda el otro (en su grupo o como bloque normal)" sí bash -c "$(declare -f tras consola es miembros); cd '$PWD'; [ \"\$(miembros 4 -60 4)\" -eq 1 ] || es 4 -60 4 minecraft:torch || es 4 -60 4 minecraft:redstone_torch"
comprueba "no se rompió todo" no es 4 -60 4 minecraft:air

# 5. Tras reiniciar, siguen ahí
parar
cp server.out server-parte1.out
BOTS=()
ANTES_DE_ARRANCAR=""
arrancar
comprueba "tras reiniciar: las flores siguen juntas" sí test "$(miembros 2 -60 4)" -eq 2
comprueba "las losas mezcladas también" sí test "$(miembros 6 -60 4)" -eq 2
comprueba "y las verticales" sí test "$(miembros 8 -60 4)" -eq 2
comprueba "y las de fuera de la zona" sí test "$(miembros 130 -60 132)" -eq 2
comprueba "sin errores del mod en todo el rato" no grep -qiE "(ERROR|Exception|WARN).*relativeblocks|relativeblocks.*(Exception|ERROR)|^\s+at com\.lukeolafp" server-parte1.out server.out
grep -iE "relativeblocks|lukeolafp|vertical slab" server.out | head -20
parar
final "RELATIVE BLOCKS EN MOHIST"
