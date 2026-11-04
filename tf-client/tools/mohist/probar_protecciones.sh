#!/usr/bin/env bash
# Prueba en un Mohist 1.20.1 de verdad (tools/mohist/README.md) la piedra puesta dentro de la zona de un grupo, UNIDA o
# INDEPENDIENTE (1.3.170, el dueño: «que me dé a elegir si mi prote es independiente o la uno a la más grande»).
#   La zona grande es de Ana (100x100 en 0,-60,0), con el grupo «Reino de Ana» (Ana y Beto). Carlos, de fuera.
# Beto pone una piedra de 25x25 dentro (en 40,-60,40) y elige en el pad como un jugador (el bot manda las acciones del
# pad del TF Client). Se comprueba: que se le abre el pad con la elección; unida, Ana construye
# en su cuadrado y al entrar no hay mensaje nuevo; independiente, Ana NO construye ahí (fuera sí), al entrar sale la
# zona de Beto y al volver la de Ana; otra vez unida; Carlos (fuera del grupo) no puede poner una piedra dentro; el
# staff lo cambia con /tf web claims modo y Carlos no; y al salir Beto del grupo su piedra independiente se le devuelve.
#   probar_protecciones.sh <carpeta de trabajo> <tfclient.jar>
set -uo pipefail
AQUI=$(cd "$(dirname "$0")" && pwd)
DIR=${1:?carpeta de trabajo}
JAR=${2:?jar del mod}
"$AQUI/montar.sh" "$DIR" "$JAR" || exit 1
cd "$DIR"
source "$AQUI/comun.sh"

ANTES_DE_ARRANCAR='mkdir -p world && python3 "$AQUI/protecciones_prueba.py" world/claimblocks_data.json'
arrancar
conectar Ana Beto Carlos

# ¿Está ese bloque? (execute if block, por la consola)
bloque() {
  local m="MARCA$RANDOM$RANDOM"
  consola "say $m" 0.5
  consola "execute if block $1 $2 $3 $4" 1
  awk -v m="$m" 'f{print} index($0,m){f=1}' server.out | grep -q "Test passed"
}
# La zona de Beto en claimblocks_data.json: «unida», «independiente» o «no está»
zona_beto() {
  python3 - "$1" <<'PY'
import json, sys
d = json.load(open("world/claimblocks_data.json"))
b = [c for c in d["claims"] if c.get("ownerName") == "Beto"]
estado = "no está" if not b else ("unida" if b[0].get("groupId") else ("independiente" if b[0].get("insideGroup") else "suelta"))
sys.exit(0 if estado == sys.argv[1] else 1)
PY
}
id_beto() { python3 -c 'import json; print([c["claimId"] for c in json.load(open("world/claimblocks_data.json"))["claims"] if c["ownerName"]=="Beto"][0])'; }
cuenta() { grep -cF -- "$2" "bots/$1.log"; }

for p in "40 -60 40" "46 -60 45" "47 -60 45" "80 -60 80" "90 -60 88"; do consola "setblock $p air" 0.2; done

# 1. Beto pone su piedra dentro de la zona de Ana
consola "tf claim give Beto claimstone_25x25"
consola "tp Beto 38 -60 40" 1.5
di Beto "!usar 40 -61 40" 2.5
mira "Beto pone la piedra dentro: se une como siempre" Beto 'Piedra unida a la zona "Reino de Ana"' sí
mira "y le dice que elija en el pad (UNIDA / INDEPENDIENTE)" Beto "Elige en el pad: UNIDA (una sola zona con la grande) o INDEPENDIENTE" sí
comprueba "en los datos, unida al grupo" sí zona_beto unida
ID=$(id_beto)
mira "y se le abre el pad en su piedra" Beto "PAD abre: protecciones z:$ID:a" sí

# 2. Unida: Ana (dueña de la grande) construye en el cuadrado de Beto; Carlos entra sin mensaje nuevo
consola "clear Ana" 0.5
consola "give Ana dirt 4"
consola "tp Ana 45 -60 43" 1.5
di Ana "!usar 46 -61 45" 1.5
comprueba "unida: Ana construye dentro del cuadrado de Beto" sí bloque 46 -60 45 minecraft:dirt
consola "tp Carlos 90 -60 90" 1.5
mira "Carlos entra en la zona grande: su mensaje" Carlos "Bienvenido al Reino de Ana" sí
consola "tp Carlos 40 -60 45" 1.5
mira "unida: al pasar a lo de Beto no sale otra zona" Carlos "Beto (25x25)" no

# 3. Independiente
di Beto "!pad protecciones|z:$ID:a|modo:$ID:indep|" 2
mira "Beto pulsa INDEPENDIENTE en el pad" Beto "PAD aviso: Tu zona ahora es independiente dentro de «Reino de Ana»" sí
comprueba "en los datos, independiente dentro del grupo" sí zona_beto independiente
di Ana "!usar 47 -61 45" 1.5
comprueba "independiente: Ana ya NO construye en el cuadrado de Beto" no bloque 47 -60 45 minecraft:dirt
mira "y le sale el aviso (sin permiso en la zona de Beto)" Ana "[!] No tienes ningún permiso de interacción en esta zona" sí
consola "tp Ana 80 -60 79" 1.5
di Ana "!usar 80 -61 80" 1.5
comprueba "independiente: fuera del cuadrado de Beto, Ana construye en lo suyo" sí bloque 80 -60 80 minecraft:dirt
consola "tp Carlos 90 -60 90" 1.5
consola "tp Carlos 40 -60 45" 1.5
mira "independiente: al entrar en lo de Beto sale su zona" Carlos "Entrando a la zona Beto (25x25)" sí
N=$(cuenta Carlos "Bienvenido al Reino de Ana")
consola "tp Carlos 90 -60 90" 1.5
[ "$(cuenta Carlos "Bienvenido al Reino de Ana")" -gt "$N" ] && echo "bien  · al volver a la grande sale otra vez la de Ana" || { echo "MAL   · al volver a la grande no sale la de Ana"; FALLOS=$((FALLOS + 1)); }

# 4. Otra vez unida
di Beto "!pad protecciones|z:$ID:a|modo:$ID:unir|" 2
mira "Beto pulsa UNIDA en el pad: otra vez una sola zona" Beto "PAD aviso: Tu piedra se unió a «Reino de Ana»: ahora es una sola zona." sí
comprueba "en los datos, unida otra vez" sí zona_beto unida
N=$(cuenta Carlos "Entrando a la zona Beto")
consola "tp Carlos 40 -60 45" 1.5
[ "$(cuenta Carlos "Entrando a la zona Beto")" -eq "$N" ] && echo "bien  · unida otra vez: al pasar a lo de Beto no sale su zona" || { echo "MAL   · unida otra vez y sale la zona de Beto"; FALLOS=$((FALLOS + 1)); }

# 5. Carlos (fuera del grupo) no pone una piedra dentro
consola "tf claim give Carlos claimstone_10x10"
consola "tp Carlos 90 -60 85" 1.5
di Carlos "!usar 90 -61 88" 2
mira "Carlos (fuera del grupo) no puede poner su piedra dentro" Carlos "[x] No puedes construir en esta zona" sí

# 6. El staff la deja independiente desde la consola; Carlos no puede tocar el modo de la piedra de Beto
consola "tf web claims modo $ID independiente" 1.5
comprueba "el staff: /tf web claims modo … independiente" sí zona_beto independiente
di Carlos "!pad protecciones|z:$ID:a|modo:$ID:unir|" 1.5
comprueba "Carlos (no es el dueño) no puede cambiarla desde el pad" sí zona_beto independiente

# 7. Beto sale del grupo (pad › SALIR, dos veces: «¿SEGURO?»): su piedra independiente vuelve
di Beto "!pad protecciones||salirgrupo|" 0.6
di Beto "!pad protecciones||salirgrupo|" 2.5
comprueba "Beto sale del grupo: su zona independiente se quita" sí zona_beto "no está"
comprueba "y la piedra ya no está puesta" sí bloque 40 -60 40 minecraft:air

echo "--- lo que vio Beto:"
grep -a "CHAT" bots/Beto.log | cut -c1-220 | tail -12
parar
final "PROTECCIONES DENTRO DE UN GRUPO EN MOHIST"
