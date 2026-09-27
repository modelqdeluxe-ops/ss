# Rumentis

App Android de engorde de ganado (paquete `hn.hato.ganadero`) con el ayudante Rumi.

## Estructura

- `app/` — la app descompilada con apktool. Aquí se trabaja.
  - `app/assets/index.html` — toda la interfaz y la lógica (HTML + JS): lotes, registros, gráficos,
    cálculos de Rumi, pantalla Más y paletas de color.
  - `app/assets/rumi_menu.js` — Rumi por menús: saludo, áreas (Mi engorde, Anotar, Alimentación,
    Salud, Calculadoras, Dinero y ventas, Guía de engorde, Usar la app) y sus opciones. No hay texto
    libre ni voz: cada opción llama a una respuesta fija de la app, un formulario o un tema de la guía.
  - `app/assets/rumi_guia.js` — la Guía de engorde (483 temas por área).
  - `app/assets/fotos.js` — fotos de los animales: cámara dentro de la app (getUserMedia) y guardado
    local en IndexedDB por id de animal. Las fotos no salen del teléfono ni van en el respaldo.
  - `app/smali/` — el código Android (WebView, guardar archivos, permiso de cámara para las fotos).
  - `app/apktool.yml` — versión (`versionCode`, `versionName`) y SDK.
- `modelo/` — el conocimiento de Rumi.
  - `saber_extra.json`, `saber/*.json` — temas de la Guía (se suman a los que ya trae la app).
  - `areas.json` — área de cada tema; `correcciones_menu.json` — textos ajustados al Rumi por menús.
  - `construir_saber.js` → arma `app/assets/rumi_guia.js` (y `modelo/rumi_saber.json` para revisarla).
  - `ilustraciones/` → toros y paisajes de cada pantalla (`escenas.py` los escribe en index.html).
  - `embeddings/`, `datos/` — el modelo de comprensión de la versión 3.3 (ya no se usa; queda como historia).
- `scripts/build.sh` — APK: `dist/Rumentis.apk`.
- `scripts/build_aab.sh` — AAB para Google Play: `dist/Rumentis.aab`.

## Rumi

Rumi funciona solo con selecciones. Al abrirlo saluda y muestra sus áreas; al elegir una, muestra
las preguntas y acciones de esa área. El botón **Menú** vuelve a las áreas y la flecha regresa un
nivel. Las calculadoras piden solo los números que necesitan (peso, temperatura, cabezas…).

Para que Rumi sepa más: agrega temas en `modelo/saber/` (con su área en el campo `a`, o en
`areas.json`) y corre `node modelo/construir_saber.js`.

## Nuevo lote

Cada animal lleva arete, peso de entrada, raza, color y foto. El precio de compra puede ser:

- **Precio del lote** (por kilo o total): el costo se reparte igual entre los animales.
- **Precio por animal**: cada animal lleva lo que se pagó por él.

Cada animal guarda su `costo`; la compra del lote es la suma más el flete.

## Compilar

```sh
scripts/build.sh       # dist/Rumentis.apk
scripts/build_aab.sh   # dist/Rumentis.aab
```

Necesita Java; descarga apktool, uber-apk-signer, bundletool y las herramientas de Android la
primera vez. Para firmar con la llave de la app define `KEYSTORE` (o `KEYSTORE_B64`),
`KEYSTORE_PASS`, `KEY_ALIAS` (`hato`) y `KEY_PASS`. En GitHub Actions se toman de los secretos
del repositorio con esos mismos nombres.
