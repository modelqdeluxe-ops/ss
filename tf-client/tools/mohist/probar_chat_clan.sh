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
source "$AQUI/comun.sh"

ANTES_DE_ARRANCAR='python3 "$AQUI/clanes_prueba.py" world/tfclient/clanes.json'
arrancar
consola "op Eva"
conectar Ana Beto Carlos Dani Eva

T=$(date +%s)
di Ana "/clan chat enable"
di Ana "hola clan $T"
di Carlos "publico de Carlos $T"
di Carlos "/clan chat enable"
di Carlos "secreto de OTR $T"
di Ana "/clan chat disable"
di Ana "otra vez publico $T" 2.5

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
comprueba "el registro apunta el chat del clan" sí grep -qF "[Clan TF] Ana: hola clan $T" server.out
comprueba "el chat del clan no sale como público en el registro" no sh -c "grep -F 'hola clan $T' server.out | grep -qv Clan"
echo "--- registro de TF Clanes:"
grep -a "TF Clanes\|\[Clan " server.out | cut -c1-200

parar
final "CHAT DEL CLAN EN MOHIST"
