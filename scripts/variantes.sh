#!/usr/bin/env bash
# Compila las cuatro apps de Rumentis desde app/:
#   Rumentis              hn.hato.ganadero                 la del dueño (de pago en Google Play), con compras de licencias
#   Rumentis Vaquero      hn.hato.ganadero.vaquero         la del equipo (gratis), se activa con una licencia
#   Rumentis Prueba       hn.hato.ganadero.prueba          la del dueño con todo abierto: las licencias se crean sin cobrar
#   Vaquero Prueba        hn.hato.ganadero.vaquero.prueba  la del equipo para probar (acepta la licencia RV-PRUEBA-2026)
# Las cuatro se instalan juntas en el mismo teléfono. Salen en dist/ (APK) y, las de Google Play, también en AAB.
# La del dueño lleva Google Play Billing y se arma con Gradle (scripts/pagos.sh); si no hay Gradle, sale sin pagos
# (salvo con PAGOS_OBLIGATORIO=1, como en GitHub Actions). Las demás, con apktool (build.sh y build_aab.sh).
# Uso: scripts/variantes.sh [apk|todo]     (todo = APK de las cuatro + AAB de las dos de Google Play)
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
QUE="${1:-apk}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$RAIZ/dist"

variante() {
  local archivo="$1" paquete="$2" nombre="$3" app="$4" prueba="$5" color="$6"
  local D="$TMP/$archivo" aab=""
  cp -r "$RAIZ/app" "$D"
  rm -rf "$D/build" "$D/dist"
  python3 "$RAIZ/scripts/variante.py" "$D" "$paquete" "$nombre" "$app" "$prueba" "$color"
  [ "$QUE" = todo ] && [ "$prueba" = 0 ] && aab="$RAIZ/dist/$archivo.aab"
  if [ "$app" = jefe ] && [ "$prueba" = 0 ]; then
    local r=0; bash "$RAIZ/scripts/pagos.sh" "$D" "$RAIZ/dist/$archivo.apk" $aab || r=$?
    if [ "$r" = 0 ]; then return; fi
    if [ "$r" != 3 ]; then exit "$r"; fi
    echo "variantes: $archivo sin Google Play Billing"
  fi
  APP_DIR="$D" SALIDA="$RAIZ/dist/$archivo.apk" bash "$RAIZ/scripts/build.sh"
  if [ -n "$aab" ]; then APP_DIR="$D" SALIDA="$aab" bash "$RAIZ/scripts/build_aab.sh"; fi
}

variante Rumentis              hn.hato.ganadero                "Rumentis"         jefe    0 "#ff22384d"
variante RumentisVaquero       hn.hato.ganadero.vaquero        "Rumentis Vaquero" vaquero 0 "#ff12594a"
variante RumentisPrueba        hn.hato.ganadero.prueba         "Rumentis Prueba"  jefe    1 "#ffa0432a"
variante RumentisVaqueroPrueba hn.hato.ganadero.vaquero.prueba "Vaquero Prueba"   vaquero 1 "#ff7a5a12"
ls -la "$RAIZ"/dist/
