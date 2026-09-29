#!/usr/bin/env bash
# Google Play Billing para la app del jefe: baja la biblioteca oficial de Google Maven, compila
# modelo/android/Pagos.java contra ella y deja en <salida>:
#   classes2.dex     la biblioteca y Pagos (se agrega tal cual al APK y al AAB)
#   manifiesto.xml   permisos, consultas y actividades que la biblioteca pide (scripts/variante.py los agrega)
# Uso: scripts/pagos.sh <salida>
# Sin acceso a dl.google.com (p. ej. compilando en tu computadora sin internet) sale sin hacer nada; la app funciona
# igual, solo que sin compras. En GitHub Actions se usa PAGOS_OBLIGATORIO=1 para que un error no pase en silencio.
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${1:?falta la carpeta de salida}"
VER="${BILLING_VER:-7.1.1}"
SDK="${ANDROID_SDK:-$HOME/sdk}"
BT="$SDK/build-tools"
AJAR="$SDK/android-36/android.jar"
mkdir -p "$OUT"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
nada() { echo "pagos: $1"; if [ "${PAGOS_OBLIGATORIO:-0}" = 1 ]; then exit 1; fi; exit 0; }

if [ ! -x "$BT/aapt2" ] || [ ! -s "$AJAR" ]; then
  mkdir -p "$SDK"
  curl -fsSL -o "$SDK/bt.zip" https://dl.google.com/android/repository/build-tools_r36.1_linux.zip || nada "sin build-tools"
  curl -fsSL -o "$SDK/pl.zip" https://dl.google.com/android/repository/platform-36_r02.zip || nada "sin android.jar"
  (cd "$SDK" && unzip -q -o bt.zip && unzip -q -o pl.zip && rm -rf build-tools && mv android-16 build-tools && rm bt.zip pl.zip)
fi

M="https://dl.google.com/android/maven2/com/android/billingclient/billing/$VER/billing-$VER"
curl -fsSL -o "$TMP/billing.aar" "$M.aar" || nada "no se pudo bajar la biblioteca de pagos $VER"
curl -fsSL -o "$TMP/billing.pom" "$M.pom" || true
# si Google publica la suma SHA-1, se comprueba
if curl -fsSL -o "$TMP/billing.aar.sha1" "$M.aar.sha1" 2>/dev/null; then
  echo "$(head -c 40 "$TMP/billing.aar.sha1")  $TMP/billing.aar" | sha1sum -c - >/dev/null || nada "la suma SHA-1 de la biblioteca no coincide"
fi
echo "pagos: billing $VER ($(wc -c < "$TMP/billing.aar") bytes)"
if [ -s "$TMP/billing.pom" ]; then
  echo "pagos: dependencias del pom:"; grep -A3 "<dependency>" "$TMP/billing.pom" | grep -E "groupId|artifactId|version|scope" | sed 's/^ */    /' || true
fi
(cd "$TMP" && unzip -q -o billing.aar -d aar)
ls "$TMP/aar"
[ -s "$TMP/aar/classes.jar" ] || nada "la biblioteca no trae classes.jar"

# Pagos.java (usa Enlace.verificarRsa: Enlace ya va en el dex principal, aquí solo se compila contra ella)
mkdir -p "$TMP/cls"
javac --release 8 -nowarn -cp "$AJAR:$TMP/aar/classes.jar" -sourcepath "$RAIZ/modelo/android" -d "$TMP/cls" \
  "$RAIZ/modelo/android/Pagos.java" 2>&1 | grep -v "^Note:" || true
[ -s "$TMP/cls/hn/hato/ganadero/Pagos.class" ] || nada "Pagos.java no compiló"
mkdir -p "$TMP/dex"
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --release --min-api 21 --lib "$AJAR" --output "$TMP/dex" \
  "$TMP/aar/classes.jar" "$TMP"/cls/hn/hato/ganadero/Pagos*.class
[ -s "$TMP/dex/classes.dex" ] || nada "d8 no produjo el dex"
[ ! -e "$TMP/dex/classes2.dex" ] || nada "la biblioteca no cabe en un solo dex"
cp "$TMP/dex/classes.dex" "$OUT/classes2.dex"
cp "$TMP/aar/AndroidManifest.xml" "$OUT/manifiesto.xml"
echo "pagos: listo ($(wc -c < "$OUT/classes2.dex") bytes de dex)"
