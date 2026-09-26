#!/usr/bin/env bash
# Compila app/ en dist/Rumentis.aab para Google Play.
#
# - Módulo base: la app sin el modelo (~45 MB).
# - Asset pack "rumi_modelo" (install-time): el modelo de Rumi en app/assets/rumi/.
#   Va en carpetas por nivel de dispositivo: rumi#tier_1 (con modelo) y rumi#tier_0 (sin modelo).
#   En Play Console se define el nivel 1 = teléfonos con 5 GB de RAM o más, así los teléfonos
#   con menos RAM no descargan el modelo. Sin esa configuración, Play usa el nivel 0.
#
# Firma: con KEYSTORE, KEYSTORE_PASS, KEY_ALIAS y KEY_PASS (o KEYSTORE_B64 en vez de KEYSTORE).
# Sin llave, el AAB queda sin firmar (Play no lo acepta así).
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS="${TOOLS_DIR:-$HOME/.cache/rumentis-tools}"
SDK="${ANDROID_SDK:-$HOME/sdk}"
BT="$SDK/build-tools"
AJAR="$SDK/android-36/android.jar"
APKTOOL_VER=2.12.1
BUNDLETOOL_VER=1.18.2
mkdir -p "$TOOLS" "$RAIZ/dist"

bajar() { [ -s "$2" ] || curl -fsSL -o "$2" "$1"; }
bajar "https://github.com/iBotPeaches/Apktool/releases/download/v$APKTOOL_VER/apktool_$APKTOOL_VER.jar" "$TOOLS/apktool.jar"
bajar "https://github.com/google/bundletool/releases/download/$BUNDLETOOL_VER/bundletool-all-$BUNDLETOOL_VER.jar" "$TOOLS/bundletool.jar"
if [ ! -x "$BT/aapt2" ] || [ ! -s "$AJAR" ]; then
  mkdir -p "$SDK"
  curl -fsSL -o "$SDK/bt.zip" https://dl.google.com/android/repository/build-tools_r36.1_linux.zip
  curl -fsSL -o "$SDK/pl.zip" https://dl.google.com/android/repository/platform-36_r02.zip
  (cd "$SDK" && unzip -q -o bt.zip && unzip -q -o pl.zip && rm -rf build-tools && mv android-16 build-tools && rm bt.zip pl.zip)
fi

leer() { grep -E "^\s*$1:" "$RAIZ/app/apktool.yml" | awk '{print $2}'; }
VC=$(leer versionCode); VN=$(leer versionName); MIN=$(leer minSdkVersion); TGT=$(leer targetSdkVersion)

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# 1. classes.dex desde smali (apktool)
java -jar "$TOOLS/apktool.jar" b "$RAIZ/app" -o "$TMP/apktool.apk" >/dev/null
unzip -q -o "$TMP/apktool.apk" classes.dex -d "$TMP"

# 2. recursos en formato proto
"$BT/aapt2" compile --dir "$RAIZ/app/res" -o "$TMP/res.zip"
"$BT/aapt2" link --proto-format -o "$TMP/base.apk" -I "$AJAR" \
  --manifest "$RAIZ/app/AndroidManifest.xml" --min-sdk-version "$MIN" --target-sdk-version "$TGT" \
  --version-code "$VC" --version-name "$VN" -R "$TMP/res.zip" --auto-add-overlay

# 3. módulo base
B="$TMP/base"; mkdir -p "$B/manifest" "$B/dex"
unzip -q "$TMP/base.apk" -d "$B"
mv "$B/AndroidManifest.xml" "$B/manifest/"
cp "$TMP/classes.dex" "$B/dex/"
mkdir -p "$B/assets"
(cd "$RAIZ/app/assets" && find . -type f ! -path './rumi/*' -exec cp --parents {} "$B/assets/" \;)
(cd "$B" && zip -q -r -D "$TMP/base.zip" manifest dex res resources.pb assets)

# 4. asset pack con el modelo
P="$TMP/pack"; mkdir -p "$P/manifest" "$P/assets/rumi#tier_1" "$P/assets/rumi#tier_0"
cat > "$TMP/pack.xml" <<EOF
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:dist="http://schemas.android.com/apk/distribution"
    package="hn.hato.ganadero" split="rumi_modelo">
  <dist:module dist:type="asset-pack">
    <dist:fusing dist:include="true"/>
    <dist:delivery><dist:install-time/></dist:delivery>
  </dist:module>
</manifest>
EOF
"$BT/aapt2" link --proto-format -o "$TMP/pack.apk" -I "$AJAR" --manifest "$TMP/pack.xml"
unzip -q "$TMP/pack.apk" AndroidManifest.xml -d "$P/manifest"
cp "$RAIZ"/app/assets/rumi/* "$P/assets/rumi#tier_1/"
echo "Este teléfono no tiene RAM suficiente para el modelo de Rumi." > "$P/assets/rumi#tier_0/sin_modelo.txt"
(cd "$P" && zip -q -r -D "$TMP/rumi_modelo.zip" manifest assets)

# 5. bundle
cat > "$TMP/BundleConfig.json" <<EOF
{
  "compression": {"uncompressedGlob": ["assets/**/*.gguf", "assets/**/*.wasm"]},
  "optimizations": {"splitsConfig": {"splitDimension": [
    {"value": "DEVICE_TIER", "negate": false, "suffixStripping": {"enabled": true, "defaultSuffix": "0"}}
  ]}}
}
EOF
AAB="$RAIZ/dist/Rumentis.aab"
rm -f "$AAB"
java -jar "$TOOLS/bundletool.jar" build-bundle --modules="$TMP/base.zip,$TMP/rumi_modelo.zip" \
  --config="$TMP/BundleConfig.json" --output="$AAB"

# 6. firma
if [ -n "${KEYSTORE_B64:-}" ] && [ -z "${KEYSTORE:-}" ]; then
  KEYSTORE="$TMP/llave.keystore"; echo "$KEYSTORE_B64" | base64 -d > "$KEYSTORE"
fi
if [ -n "${KEYSTORE:-}" ]; then
  jarsigner -keystore "$KEYSTORE" -storepass "$KEYSTORE_PASS" -keypass "$KEY_PASS" \
    -sigalg SHA256withRSA -digestalg SHA-256 "$AAB" "$KEY_ALIAS" >/dev/null
  echo "Listo (firmado): $AAB"
else
  echo "Listo (SIN FIRMAR, falta la llave): $AAB"
fi
