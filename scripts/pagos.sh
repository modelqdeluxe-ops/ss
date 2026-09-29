#!/usr/bin/env bash
# La app del dueño para Google Play, con Google Play Billing:
#   1. Gradle (proyecto de scripts/donante) arma el APK y el AAB con los recursos, assets y manifiesto de la
#      variante y con la biblioteca de pagos y todas sus dependencias (AndroidX, Play Services), más Pagos.java;
#   2. nuestro código (smali, compilado con apktool) entra como un dex más, sin su clase R (la trae Gradle con los
#      mismos números de recursos);
#   3. se alinea y se firma igual que en build.sh.
# Uso: scripts/pagos.sh <carpeta de la app (variante)> <salida.apk> [salida.aab]
# Sin Gradle o sin acceso a Google Maven (p. ej. en tu computadora sin internet) sale con 3 y variantes.sh arma la
# app sin pagos; en GitHub Actions se usa PAGOS_OBLIGATORIO=1 para que un error no pase en silencio.
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
APP="${1:?falta la carpeta de la app}"; APK="${2:?falta la salida .apk}"; AAB="${3:-}"
TOOLS="${TOOLS_DIR:-$HOME/.cache/rumentis-tools}"
VER="${BILLING_VER:-7.1.1}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
nada() { echo "pagos: $1"; if [ "${PAGOS_OBLIGATORIO:-0}" = 1 ]; then exit 1; fi; exit 3; }
command -v gradle >/dev/null || nada "no hay Gradle"
mkdir -p "$TOOLS"
[ -s "$TOOLS/apktool.jar" ] || curl -fsSL -o "$TOOLS/apktool.jar" "https://github.com/iBotPeaches/Apktool/releases/download/v2.12.1/apktool_2.12.1.jar"
leer() { grep -E "^\s*$1:" "$APP/apktool.yml" | awk '{print $2}'; }

# 1. nuestro código en un dex aparte (sin la clase R) y los números de todos los recursos tal como los pone apktool
S="$TMP/smali_app"; cp -r "$APP" "$S"
rm -f "$S"/smali/hn/hato/ganadero/R.smali "$S"/smali/hn/hato/ganadero/R\$*.smali
java -jar "$TOOLS/apktool.jar" b "$S" -o "$TMP/smali.apk" >/dev/null
unzip -q -o "$TMP/smali.apk" classes.dex -d "$TMP/nuestro"
java -jar "$TOOLS/apktool.jar" d -s -f "$TMP/smali.apk" -o "$TMP/dec" >/dev/null

# 2. Gradle
D="$TMP/donante"; mkdir -p "$D/java/hn/hato/ganadero"
cp "$RAIZ"/scripts/donante/*.kts "$RAIZ/scripts/donante/gradle.properties" "$D/"
cp "$RAIZ/modelo/android/Pagos.java" "$D/java/hn/hato/ganadero/"
python3 "$RAIZ/scripts/donante/preparar.py" "$APP" "$D" "$TMP/dec/res/values/public.xml"
PAQ=$(python3 -c "import xml.etree.ElementTree as E,sys;print(E.parse(sys.argv[1]).getroot().get('package'))" "$APP/AndroidManifest.xml")
TAREAS=(assembleRelease); [ -n "$AAB" ] && TAREAS+=(bundleRelease)
(cd "$D" && gradle --no-daemon --console=plain -q "${TAREAS[@]}" \
   -Ppaquete="$PAQ" -Pvc="$(leer versionCode)" -Pvn="$(leer versionName)" -Ptarget="$(leer targetSdkVersion)" \
   -Pbilling="$VER" -Passets="$APP/assets") || nada "Gradle no pudo armar la app"
U=$(ls "$D"/build/outputs/apk/release/*.apk | head -1)
[ -s "$U" ] || nada "Gradle no dejó el APK"

# 3. se agrega nuestro dex
N=$(unzip -Z1 "$U" | grep -cE '^classes[0-9]*\.dex$')
DEX="classes$((N+1)).dex"; mkdir -p "$TMP/z"; cp "$TMP/nuestro/classes.dex" "$TMP/z/$DEX"
cp "$U" "$TMP/sin-firmar.apk"; (cd "$TMP/z" && zip -q -0 "$TMP/sin-firmar.apk" "$DEX")
echo "pagos: APK con $(unzip -Z1 "$TMP/sin-firmar.apk" | grep -cE '^classes[0-9]*\.dex$') dex (el nuestro es $DEX)"

# 4. firma (build.sh sabe firmar un APK ya armado)
APK_LISTO="$TMP/sin-firmar.apk" SALIDA="$APK" bash "$RAIZ/scripts/build.sh"

if [ -n "$AAB" ]; then
  B=$(ls "$D"/build/outputs/bundle/release/*.aab | head -1)
  [ -s "$B" ] || nada "Gradle no dejó el AAB"
  N=$(unzip -Z1 "$B" | grep -cE '^base/dex/classes[0-9]*\.dex$')
  mkdir -p "$TMP/b/base/dex"; cp "$TMP/nuestro/classes.dex" "$TMP/b/base/dex/classes$((N+1)).dex"
  cp "$B" "$AAB"; (cd "$TMP/b" && zip -q "$AAB" "base/dex/classes$((N+1)).dex")
  if [ -n "${KEYSTORE_B64:-}" ] && [ -z "${KEYSTORE:-}" ]; then KEYSTORE="$TMP/llave.keystore"; echo "$KEYSTORE_B64" | base64 -d > "$KEYSTORE"; fi
  if [ -n "${KEYSTORE:-}" ]; then
    jarsigner -keystore "$KEYSTORE" -storepass "$KEYSTORE_PASS" -keypass "$KEY_PASS" -sigalg SHA256withRSA -digestalg SHA-256 "$AAB" "$KEY_ALIAS" >/dev/null
    echo "Listo (firmado): $AAB"
  else echo "Listo (SIN FIRMAR, falta la llave): $AAB"; fi
fi
