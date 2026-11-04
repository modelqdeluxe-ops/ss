#!/usr/bin/env bash
# (1.3.174) Las métricas de nLogin con un Mohist de verdad: el TF Client lee los registros de nLogin de
# plugins/nLogin/logs y los manda por el puente a la web (el Worker de la tienda en local, web_metricas.mjs).
#   probar_metricas.sh <carpeta> <tfclient.jar> <carpeta de registros de nLogin>
# Comprueba: el mod pregunta por dónde va la web; manda todo lo de desde el 26 de octubre (lo mismo que cuenta grep en los
# registros) sin repetir nada; sin IP ni contraseñas; una línea nueva en el último registro llega una vez; y tras
# reiniciar el servidor sigue donde iba (no vuelve a mandar nada).
set -uo pipefail
AQUI=$(cd "$(dirname "$0")" && pwd)
DIR=${1:?carpeta}; JAR=${2:?jar}; LOGS=$(cd "${3:?registros de nLogin}" && pwd)
"$AQUI/montar.sh" "$DIR" "$JAR" || exit 1
cd "$DIR"
source "$AQUI/comun.sh"
PUERTO=8791
rm -rf plugins/nLogin && mkdir -p plugins/nLogin/logs
for f in "$LOGS"/*.log; do cp "$f" plugins/nLogin/logs/; done
CLAVE=clave-de-prueba-metricas
mkdir -p config/tfclient
rm -f config/tfclient-server.properties tfclient-puente.clave config/tfclient/.puente.clave
cat > config/tfclient/servidor.properties <<PROPS
bridge.enabled=true
bridge.url=http://127.0.0.1:$PUERTO
bridge.secret=$CLAVE
bridge.interval=5
PROPS
pkill -f "[w]eb_metricas.mjs" || true
rm -f estado.json
node "$AQUI/web_metricas.mjs" $PUERTO "$CLAVE" "$PWD/estado.json" > web.log 2>&1 &
sleep 2
esperado() { cat plugins/nLogin/logs/*.log >/dev/null; local n=0; for f in $(ls plugins/nLogin/logs | awk '$0 >= "2026-10-26"'); do n=$((n + $(grep -c -E "applied to \[(last_ip, )?last_seen, settings\]|By: www.nickuc.com|Successful plugin core shutdown|Unable to handle incoming packet LOGIN_START|Unable to contact Mojang servers for|has successfully registered" "plugins/nLogin/logs/$f"))); done; echo $n; }
total() { python3 -c "import json; print(sum(json.load(open('estado.json'))['kinds'].values()))" 2>/dev/null || echo 0; }
espera_total() { for _ in $(seq 1 ${2:-120}); do [ "$(total)" = "$1" ] && return 0; sleep 1; done; return 1; }
QUIERO=$(esperado)
echo "en los registros desde el 26 oct: $QUIERO eventos"
arrancar
comprueba "la web recibió los $QUIERO eventos" sí espera_total "$QUIERO" 180
sleep 12
comprueba "y ni uno más (no se repite nada)" sí test "$(total)" = "$QUIERO"
comprueba "en lo mandado no hay IP" sí python3 -c "import json,sys; e=json.load(open('estado.json')); sys.exit(1 if e['ips'] or e['rawHasIp'] else 0)"
comprueba "hay sesiones" sí python3 -c "import json,sys; sys.exit(0 if json.load(open('estado.json'))['sessions'] > 100 else 1)"
grep -q "mandando a la web los registros de nLogin" server.out && echo "bien  · el registro del servidor lo dice" || { echo "MAL   · el registro del servidor no dice que manda nLogin"; FALLOS=$((FALLOS + 1)); }
# Una entrada nueva en el último registro (como si nLogin la escribiera ahora)
ULTIMO=$(ls plugins/nLogin/logs | sort -t- -k1,3 -k4n | tail -1)
AHORA=$(date -u +"%Y/%m/%d %H:%M:%S")
printf '[%s DEBUG]: [SQLite] [UPDATE]: "UPDATE `nlogin` SET `last_name` = '"'"'Bot_Nuevo'"'"', `unique_id` = x, `password` = null, `last_ip` = '"'"'10.1.2.3'"'"', `creation_date` = 2026-11-04 10:00:00.000, `last_seen` = 2026-11-04 10:00:00.000 WHERE `ai` = 999" (applied to [last_ip, last_seen, settings] columns)\n' "$AHORA" >> "plugins/nLogin/logs/$ULTIMO"
comprueba "una línea nueva del último registro llega" sí espera_total "$((QUIERO + 1))" 30
comprueba "y llega sin la IP" sí python3 -c "import json,sys; e=json.load(open('estado.json')); sys.exit(1 if e['ips'] else 0)"
# Reiniciar el servidor: sigue donde iba
parar
CUR=$(python3 -c "import json; print(json.load(open('estado.json'))['cursor'])")
arrancar
sleep 15
comprueba "tras reiniciar no vuelve a mandar nada (sigue en $CUR)" sí test "$(total)" = "$((QUIERO + 1))"
comprueba "y pregunta por dónde va la web" sí grep -q "mandando a la web los registros de nLogin" server.out
parar
pkill -f "[w]eb_metricas.mjs" || true
final "METRICAS DE NLOGIN EN MOHIST"
