#!/usr/bin/env bash
# Espera el informe de la prueba de jefes en el juego de un commit y lo baja.
#   tools/bosses/esperar_prueba.sh <hash corto del commit> <carpeta destino>
# Úsalo en SEGUNDO PLANO (en Claude Code: Bash con run_in_background): te avisa al acabar, sin sleeps a mano.
# La prueba la lanza GitHub Actions (.github/workflows/jefes-prueba.yml) con un push cuyo commit diga
# [prueba-jefes] o [prueba-jefes=a,b]; el resultado va a la rama huérfana «pruebas-jefes» (su commit dice
# «Prueba de jefes <hash>»). Tarda ~8 min con 1-4 jefes y ~30 min con los 17.
set -u
hash="$1"
dest="$2"
for i in $(seq 1 120); do
  rm -rf "$dest"
  if git clone -q --depth 1 -b pruebas-jefes https://github.com/modelqdeluxe-ops/ss.git "$dest" 2>/dev/null \
      && (cd "$dest" && git log -1 --format=%s) | grep -q "$hash"; then
    echo "listo: $dest/informe.txt"
    exit 0
  fi
  sleep 30
done
echo "tiempo agotado (mira el workflow «Prueba de jefes en el juego» en Actions)"
exit 1
