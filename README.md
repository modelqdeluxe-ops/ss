# Rumentis

App Android de engorde de ganado (paquete `hn.hato.ganadero`) con el ayudante Rumi.

## Estructura

- `app/` — la app descompilada con apktool. Aquí se trabaja.
  - `app/assets/index.html` — toda la interfaz y la lógica (HTML + JS): lotes, registros, gráficos,
    cálculos de Rumi, pantalla Más y paletas de color.
  - `app/assets/rumi_menu.js` — Rumi por menús: áreas (Mi engorde, Mis lotes, Anotar, Alimentación,
    Salud, ¿Qué tiene mi animal?, Calculadoras, Dinero y ventas, Guía de engorde, Usar la app), cada lote
    y sus animales, síntomas, más de 25 calculadoras y los temas de la guía por partes. Sin texto ni voz.
  - `app/assets/rumi_guia.js` — la Guía: `RUMI_FICHAS` (temas a fondo con partes y puntos) y
    `RUMI_GUIA` (datos rápidos).
  - `app/assets/fotos.js` — fotos de los animales: cámara dentro de la app (getUserMedia). En Android se
    guardan como JPEG en la memoria interna (`Android.fotoGuardar/fotoLeer`, clase `Fotos` en smali);
    en un navegador, en IndexedDB. No salen del teléfono ni van en el respaldo.
  - `app/assets/extras.js` — Agenda de tareas, Bodega de alimento e informe de lote para compartir.
  - `app/assets/ambiente.js` — colores según la hora y la época del año, clima de la zona (Open-Meteo,
    sin clave; ubicación aproximada del teléfono o la de Configuración), efectos de sol, nubes, lluvia y
    estrellas dibujados en canvas, fotos de novillos en bucle en cada encabezado y la bienvenida animada (la primera vez,
    Rumi saluda y abre el recorrido).
  - `app/assets/fondos/` — `g01`–`g25.webp`, fotos de ganado (Wikimedia Commons, CC0 y dominio público;
    créditos en Más, Ayuda, Acerca de) 
  - `app/assets/iconos.js` — íconos Phosphor duotono (licencia MIT, `LICENCIA-phosphor.txt`).
  - `app/assets/fondos/rumi.webp` — retrato de Rumi: Brahman en Costa Rica, foto de Bernard Gagnon (CC0).
  - `app/smali/` — el código Android (WebView, guardar archivos, permisos de cámara y ubicación, fotos,
    compartir y lectura en voz alta: la clase `Voz` elige la voz en español más natural del teléfono;
    su fuente Java está en `modelo/android/Voz.java`).
  - `app/apktool.yml` — versión (`versionCode`, `versionName`) y SDK.
- `modelo/` — el conocimiento de Rumi.
  - `fichas/*.json` — temas a fondo: `{t, a, r (resumen), s: [{h, p: [puntos]}], rel: [temas]}`. Áreas:
    sanidad, nutricion, manejo, forrajes, instalaciones, negocio, normas, conceptos, fisiologia, genetica,
    carne, actualidad y app.
  - `saber_extra.json`, `saber/*.json` — datos rápidos (se suman a los que ya trae la app).
  - `areas.json` — área de cada tema; `correcciones_menu.json` — textos ajustados al Rumi por menús.
  - `construir_saber.js` → arma `app/assets/rumi_guia.js` (y `modelo/rumi_saber.json` para revisarla).
  - `ilustraciones/` → toros y paisajes de cada pantalla (`escenas.py` los escribe en index.html).
  - `embeddings/`, `datos/` — el modelo de comprensión de la versión 3.3 (ya no se usa; queda como historia).
- `scripts/build.sh` — APK: `dist/Rumentis.apk`.
- `scripts/build_aab.sh` — AAB para Google Play: `dist/Rumentis.aab`.

## Rumi

Rumi funciona con selecciones y un buscador (temas, cálculos, síntomas y lotes). Puede leer en voz alta
sus mensajes y el recorrido. Las opciones se ocultan solas cuando la respuesta es larga. Al abrirlo saluda y muestra sus áreas; al elegir una, muestra
las preguntas y acciones de esa área. El botón **Menú** vuelve a las áreas y la flecha regresa un
nivel. Las calculadoras piden solo los números que necesitan (peso, temperatura, cabezas…).

Para que Rumi sepa más: agrega temas en `modelo/fichas/` (o datos rápidos en `modelo/saber/`) y corre
`node modelo/construir_saber.js`; el script valida títulos repetidos y temas relacionados.

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
