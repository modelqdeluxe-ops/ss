#!/usr/bin/env bash
# Pasa un mundo (o un trozo) a la 1.20.1 con el servidor oficial de Mojang (--forceUpgrade): bloques, carteles
# (front_text de la 1.20), cuadros… quedan como los dejaría el juego. Trabaja sobre una COPIA (la carpeta que se le da
# se modifica). Para ir rápido, copia solo los .mca de region/ y entities/ donde está la construcción (r.X.Z: X = x>>9,
# Z = z>>9) y el level.dat.
#   actualizar.sh <carpeta del mundo> <server.jar 1.20.1>
set -euo pipefail
MUNDO=$(cd "$1" && pwd)
JAR=$(cd "$(dirname "$2")" && pwd)/$(basename "$2")
TRABAJO=$(dirname "$MUNDO")
cd "$TRABAJO"
echo "eula=true" > eula.txt
printf 'level-name=%s\nserver-port=25599\nonline-mode=false\nspawn-protection=0\nmax-tick-time=-1\n' \
  "$(basename "$MUNDO")" > server.properties
rm -f actualizar.log
(java -Xmx3G -jar "$JAR" --forceUpgrade --nogui > actualizar.log 2>&1 < /dev/null) &
for _ in $(seq 1 900); do grep -q "Done (" actualizar.log 2>/dev/null && break; sleep 1; done
grep -q "Done (" actualizar.log || { echo "MAL: el servidor no terminó"; tail -20 actualizar.log; exit 1; }
sleep 3
pkill -TERM -f "[j]ava -Xmx3G -jar $JAR --forceUpgrade" || true   # al cerrarse guarda
for _ in $(seq 1 60); do pgrep -f "[j]ava -Xmx3G -jar $JAR --forceUpgrade" >/dev/null || break; sleep 1; done
echo "mundo en la 1.20.1: $MUNDO ($(grep -c '' actualizar.log) líneas en $TRABAJO/actualizar.log)"
