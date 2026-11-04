#!/usr/bin/env bash
# Prueba el chat del clan (/clan chat enable|disable) en un Mohist 1.20.1 de verdad (Mohist + EssentialsX + EssentialsXChat
# + LuckPerms, como el servidor del dueño) con 5 jugadores bot:
#   Ana y Beto: clan [TF]; Carlos: clan [OTR]; Dani: sin clan; Eva: sin clan y operadora.
# Comprueba la regla estricta del dueño (lo del clan solo a su clan conectado y a los operadores), que quien lo tiene
# activado no ve el chat público, que no sale en el registro como chat público y que «disable» lo devuelve al público.
#   probar_chat_clan.sh <carpeta de trabajo> <tfclient.jar>
# Sale con 0 si todo está bien. Deja el registro del servidor en <carpeta>/server.out y lo de cada bot en <carpeta>/bots/.
set -uo pipefail
AQUI=$(cd "$(dirname "$0")" && pwd)
DIR=${1:?carpeta de trabajo}
JAR=${2:?jar del mod}
"$AQUI/montar.sh" "$DIR" "$JAR" || exit 1
cd "$DIR"

pkill -f "[b]ot/bot.js" || true
# un servidor de antes: que se cierre del todo (al cerrarse guarda clanes.json) antes de escribir los clanes
pkill -f "[m]ohist.jar nogui" || true
for _ in $(seq 1 60); do pgrep -f "[m]ohist.jar nogui" >/dev/null || break; sleep 1; done
python3 "$AQUI/clanes_prueba.py" world/tfclient/clanes.json
: > cmds.txt
rm -f server.out
(tail -n0 -f cmds.txt | java -Xms1G -Xmx4G -jar mohist.jar nogui > server.out 2>&1) &
for _ in $(seq 1 300); do grep -q "Done (" server.out 2>/dev/null && break; sleep 1; done
grep -q "Done (" server.out || { echo "MAL: el servidor no arrancó"; tail -40 server.out; exit 1; }
echo "Mohist arrancado: $(grep -m1 -o 'Done ([^)]*)' server.out)"
echo "op Eva" >> cmds.txt

mkdir -p bots
rm -f bots/*.log bots/*.cmd
for n in Ana Beto Carlos Dani Eva; do
  node bot/bot.js "$n" 25565 "$DIR/bots" > "bots/$n.log" 2>&1 &
  sleep 0.5
done
for _ in $(seq 1 60); do
  [ "$(grep -l "dentro del mundo" bots/*.log 2>/dev/null | wc -l)" -eq 5 ] && break
  sleep 1
done
[ "$(grep -l "dentro del mundo" bots/*.log 2>/dev/null | wc -l)" -eq 5 ] || { echo "MAL: no entraron los 5 bots"; tail -5 bots/*.log; exit 1; }
sleep 2

T=$(date +%s)
say() { echo "$2" >> "bots/$1.cmd"; sleep "${3:-1.5}"; }
say Ana "/clan chat enable"
say Ana "hola clan $T"
say Carlos "publico de Carlos $T"
say Carlos "/clan chat enable"
say Carlos "secreto de OTR $T"
say Ana "/clan chat disable"
say Ana "otra vez publico $T" 2.5

FALLOS=0
mira() { # mira <descripción> <bot> <texto> <sí|no>
  if grep -qF -- "$3" "bots/$2.log"; then got=sí; else got=no; fi
  if [ "$got" = "$4" ]; then echo "bien  · $1"; else echo "MAL   · $1 (a $2 le llegó: $got)"; FALLOS=$((FALLOS + 1)); fi
}
mira "Ana activa el chat del clan" Ana "Chat del clan ACTIVADO" sí
for n in Ana Beto; do mira "el chat de [TF] le llega a $n (su clan)" $n "[Clan TF] Ana: hola clan $T" sí; done
mira "el chat de [TF] le llega a Eva (operadora)" Eva "[Clan TF] Ana: hola clan $T" sí
for n in Carlos Dani; do mira "el chat de [TF] NO le llega a $n" $n "hola clan $T" no; done
mira "Ana (con el chat del clan) no ve el público" Ana "publico de Carlos $T" no
for n in Beto Dani Eva Carlos; do mira "el público de Carlos le llega a $n" $n "publico de Carlos $T" sí; done
for n in Carlos Eva; do mira "el chat de [OTR] le llega a $n" $n "[Clan OTR] Carlos: secreto de OTR $T" sí; done
for n in Ana Beto Dani; do mira "el chat de [OTR] NO le llega a $n" $n "secreto de OTR $T" no; done
for n in Beto Dani Eva; do mira "tras disable, el público de Ana le llega a $n" $n "otra vez publico $T" sí; done
mira "tras disable, Carlos (con el suyo activado) no ve el público de Ana" Carlos "otra vez publico $T" no
if grep -qF "[Clan TF] Ana: hola clan $T" server.out; then echo "bien  · el registro apunta el chat del clan"; else echo "MAL   · el registro no tiene «[Clan TF] Ana: hola clan»"; FALLOS=$((FALLOS + 1)); fi
if grep -F "hola clan $T" server.out | grep -qv "Clan"; then echo "MAL   · el chat del clan salió como chat público en el registro"; FALLOS=$((FALLOS + 1)); else echo "bien  · el chat del clan no sale como público en el registro"; fi
echo "--- registro de TF Clanes:"
grep -a "TF Clanes\|\[Clan " server.out | cut -c1-200

for n in Ana Beto Carlos Dani Eva; do echo quit >> "bots/$n.cmd"; done
echo stop >> cmds.txt
for _ in $(seq 1 60); do pgrep -f "[m]ohist.jar nogui" >/dev/null || break; sleep 1; done
pkill -f "[m]ohist.jar nogui" || true
pkill -f "[b]ot/bot.js" || true
if [ "$FALLOS" -eq 0 ]; then echo "CHAT DEL CLAN EN MOHIST: TODO BIEN"; else echo "CHAT DEL CLAN EN MOHIST: $FALLOS FALLOS"; fi
exit "$FALLOS"
