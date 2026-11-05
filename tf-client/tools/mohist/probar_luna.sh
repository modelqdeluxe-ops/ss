#!/usr/bin/env bash
# La Luna de Sangre (1.3.175) en un Mohist 1.20.1 de verdad (README.md), con jugadores bot:
#   - con 2 conectados, al tocar la luna no sale, se avisa («hay 2») y la cuenta vuelve a 10;
#   - con 3, sale al anochecer y todos lo leen;
#   - nadie duerme (un bot usa una cama);
#   - el daño mágico: un zombi Élite contra armadura de diamante quita mucho más que uno normal (la vida de los bots);
#   - los mobs extra aparecen alrededor de los jugadores;
#   - al amanecer se acaba, se avisa, y los mobs del evento se van.
# Termina con «LUNA DE SANGRE EN MOHIST: TODO BIEN» (y sale con 0) o con el número de fallos.
#   probar_luna.sh <carpeta de trabajo> <tfclient.jar>
set -uo pipefail
AQUI=$(cd "$(dirname "$0")" && pwd)
DIR=${1:?carpeta de trabajo}
JAR=${2:?jar del mod}
"$AQUI/montar.sh" "$DIR" "$JAR" || exit 1
cd "$DIR"
source "$AQUI/comun.sh"
MUNDO="luna$(date +%s)"
grep -v '^level-name=' server.properties > server.properties.tmp && mv server.properties.tmp server.properties
echo "level-name=$MUNDO" >> server.properties
# (sin la marca de la limpieza de la 1.3.120, el TF Client borra datos de jugadores en un mundo nuevo)
ANTES_DE_ARRANCAR='mkdir -p "$MUNDO/tfclient" && touch "$MUNDO/tfclient/limpieza-1.3.120.hecha"'
arrancar

tras() {
  local m="MARCA$RANDOM$RANDOM"
  consola "say $m" 0.4
  consola "$1" "${2:-1}"
  awk -v m="$m" 'f{print} index($0,m){f=1}' server.out
}
config() {  # config <clave> <valor json> : cambia config/tfclient/luna_sangre.json y /tf reload
  python3 - "$1" "$2" <<'PY'
import json, sys
p = "config/tfclient/luna_sangre.json"
d = json.load(open(p, encoding="utf-8"))
d[sys.argv[1]] = json.loads(sys.argv[2])
json.dump(d, open(p, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
PY
  consola "tf reload" 1.5
}
anochecer() {
  consola "time set 6000" 1.5
  consola "time set 12900" 2.5
  consola "time set 13100" 3.5
}
armadura() {
  for s in "armor.head diamond_helmet" "armor.chest diamond_chestplate" "armor.legs diamond_leggings" "armor.feet diamond_boots"; do
    set -- $s
    consola "minecraft:item replace entity $BOT $1 with minecraft:$2" 0.3
  done
}
golpe_mayor() {  # el mayor daño de una vez que recibió el bot (líneas «VIDA: x (-d)»)
  grep -o 'VIDA: [0-9.]* (-[0-9.]*)' "bots/$1.log" | grep -o '(-[0-9.]*)' | tr -d '()-' | sort -g | tail -1
}

consola "gamerule doDaylightCycle false" 0.5
consola "gamerule doMobSpawning false" 0.5
consola "difficulty normal" 0.5
config porOleada 0

# 1. Dos jugadores: no sale
conectar Ana Beto
consola "tf luna noches 1"
anochecer
mira "con 2 jugadores la Luna de Sangre no sale" Ana "La Luna de Sangre no sale esta noche" sí
mira "y dice cuántos hay" Beto "hay 2. La cuenta vuelve a empezar: 10 noches" sí
comprueba "la cuenta vuelve a 10" sí bash -c "$(declare -f tras consola); cd '$PWD'; tras 'tf luna estado' | grep -q 'faltan 10 noches'"

# 2. Tres jugadores: sale
conectar Carlos
consola "tf luna noches 1"
anochecer
for b in Ana Beto Carlos; do mira "con 3 jugadores sale (lo lee $b)" $b "LA LUNA DE SANGRE SE ALZA" sí; done
comprueba "está activa" sí bash -c "$(declare -f tras consola); cd '$PWD'; tras 'tf luna estado' | grep -q 'ACTIVA'"
sleep 15   # la escena (16 s): mientras, los mobs no hacen daño

# 3. Nadie duerme
consola "tp Beto 10.5 -60 12.5 180 30" 1.5
consola "setblock 12 -60 10 minecraft:red_bed[part=head,facing=north]" 0.4
consola "setblock 12 -60 11 minecraft:red_bed[part=foot,facing=north]" 0.6
di Beto "!usar 12 -60 11" 1.5
mira "Beto no puede dormir" Beto "La Luna de Sangre no te deja dormir" sí

# 4. El daño mágico (armadura de diamante): un zombi normal junto a Ana y uno Élite junto a Beto
consola "tp Ana 60 -60 60" 1
consola "tp Beto -60 -60 -60" 1
consola "tp Carlos 0 -60 -120" 1
for BOT in Ana Beto; do armadura; done
sleep 1
consola "execute at Ana run tf luna invocar normal" 0.5
consola "execute at Beto run tf luna invocar elite" 5
NA=$(golpe_mayor Ana); NB=$(golpe_mayor Beto)
echo "        golpe mayor: a Ana (zombi normal) ${NA:-0}, a Beto (Élite) ${NB:-0}"
comprueba "el zombi normal casi no hace daño con armadura de diamante (< 1,5)" sí python3 -c "import sys; sys.exit(0 if 0 < float('${NA:-0}') < 1.5 else 1)"
comprueba "el Élite atraviesa la armadura (≥ 3)" sí python3 -c "import sys; sys.exit(0 if float('${NB:-0}') >= 3.0 else 1)"
comprueba "en el registro, «fue desgarrado» o vida bajando sin errores del daño" no grep -qE "Exception.*(luna|BloodMo)|BloodMob.*Exception" server.out

# 5. Los mobs extra
config porOleada 3
consola "gamemode survival Carlos" 0.5
sleep 12
comprueba "aparecen mobs de la luna alrededor de los jugadores" sí bash -c "$(declare -f tras consola); cd '$PWD'; tras 'execute if entity @e[nbt={ForgeData:{tf_luna:{extra:1b}}}]' | grep -q 'Test passed'"

# 6. Amanece
consola "time set 23300" 3
mira "al amanecer se acaba y lo dice" Ana "Amanece. La Luna de Sangre se oculta" sí
comprueba "y ya no está activa (faltan 10 noches)" sí bash -c "$(declare -f tras consola); cd '$PWD'; tras 'tf luna estado' | grep -q 'faltan 10 noches'"
comprueba "los mobs del evento se han ido" sí bash -c "$(declare -f tras consola); cd '$PWD'; tras 'execute if entity @e[nbt={ForgeData:{tf_luna:{extra:1b}}}]' | grep -q 'Test failed'"
comprueba "sin errores de la Luna de Sangre en el registro" no grep -qiE "(Exception|ERROR).*(luna|BloodMo|tfclient\.luna)|^\s+at net\.tierrasfantasticas\.tfclient\.luna" server.out
grep -iE "luna de sangre|TF Luna" server.out | head -12
parar
final "LUNA DE SANGRE EN MOHIST"
