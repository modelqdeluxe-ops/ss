# Rumentis

App Android de engorde de ganado (paquete `hn.hato.ganadero`) con el ayudante Rumi.

## Estructura

- `app/` — la app descompilada con apktool. Aquí se trabaja.
  - `app/assets/index.html` — toda la interfaz y la lógica de Rumi (HTML + JS).
  - `app/assets/rumi_red.bin` — red neuronal de intenciones de Rumi.
  - `app/smali/` — el código Android (WebView, dictado por voz, guardar archivos).
  - `app/apktool.yml` — versión (`versionCode`, `versionName`) y SDK.
- `scripts/build.sh` — compila `app/` en `dist/Rumentis.apk`.
- `dist/Rumentis.apk` — el último APK compilado.
- `Rumentis.apk` — el APK original subido.

## Compilar

```sh
scripts/build.sh
```

Necesita Java. Descarga apktool y uber-apk-signer la primera vez. Para firmar con la llave
de la app, define `KEYSTORE`, `KEYSTORE_PASS`, `KEY_ALIAS` y `KEY_PASS`. Sin ellas firma con
una llave de depuración y Android pedirá desinstalar la versión anterior antes de instalar.
