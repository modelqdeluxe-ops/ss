#!/usr/bin/env bash
# Compila app/ (proyecto apktool) en dist/Rumentis.apk, alineado y firmado.
#
# Firma:
#   - Si existen KEYSTORE (o KEYSTORE_B64), KEYSTORE_PASS, KEY_ALIAS y KEY_PASS, firma con esa llave.
#   - Si no, firma con la llave de depuración de uber-apk-signer (sirve para probar,
#     pero Android no deja actualizar encima de una app firmada con otra llave).
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS="${TOOLS_DIR:-$HOME/.cache/rumentis-tools}"
APKTOOL_VER=2.12.1
SIGNER_VER=1.3.0
mkdir -p "$TOOLS" "$RAIZ/dist"

bajar() { [ -s "$2" ] || curl -fsSL -o "$2" "$1"; }
bajar "https://github.com/iBotPeaches/Apktool/releases/download/v$APKTOOL_VER/apktool_$APKTOOL_VER.jar" "$TOOLS/apktool.jar"
bajar "https://github.com/patrickfav/uber-apk-signer/releases/download/v$SIGNER_VER/uber-apk-signer-$SIGNER_VER.jar" "$TOOLS/uber-apk-signer.jar"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

java -jar "$TOOLS/apktool.jar" b "$RAIZ/app" -o "$TMP/sin-firmar.apk"

if [ -n "${KEYSTORE_B64:-}" ] && [ -z "${KEYSTORE:-}" ]; then
  KEYSTORE="$TMP/llave.keystore"; echo "$KEYSTORE_B64" | base64 -d > "$KEYSTORE"
fi
FIRMA=()
if [ -n "${KEYSTORE:-}" ]; then
  FIRMA=(--ks "$KEYSTORE" --ksPass "$KEYSTORE_PASS" --ksAlias "$KEY_ALIAS" --ksKeyPass "$KEY_PASS")
fi
java -jar "$TOOLS/uber-apk-signer.jar" -a "$TMP/sin-firmar.apk" -o "$TMP/firmado" "${FIRMA[@]}"

cp "$TMP"/firmado/*.apk "$RAIZ/dist/Rumentis.apk"
echo "Listo: $RAIZ/dist/Rumentis.apk"
