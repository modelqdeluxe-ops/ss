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
    estrellas (el cielo se dibuja una vez como imagen y se mueve con CSS; la lluvia se pinta en cada cuadro), fotos de novillos en bucle en cada encabezado y la bienvenida animada (la primera vez,
    Rumi saluda y abre el recorrido).
  - `app/assets/fondos/` — `g01`–`g25.webp`, fotos de ganado (Wikimedia Commons, CC0 y dominio público;
    créditos en Más, Ayuda, Acerca de) 
  - `app/assets/iconos.js` — íconos Phosphor duotono (licencia MIT, `LICENCIA-phosphor.txt`).
  - `app/assets/finanzas.js` — Finanzas (balance con el ganado como activo biológico, estado de resultados,
    flujo de dinero, créditos, rentabilidad de lotes) e Inventario (ganado, alimento, medicinas e insumos,
    equipo con depreciación). Se guarda en `S.config.fin`.
  - `app/assets/rumi_pro.js` — Análisis de Rumi: puntaje por lote y finca, riesgo con simulación de
    Montecarlo, escenarios, ranking de proveedores y razas, semáforo de animales e informe semanal.
  - `app/assets/i18n.js` y `i18n_en.js`, `i18n_pt.js` — idiomas: traduce lo que aparece en pantalla con
    catálogos de frases; los números, fechas y datos del usuario van como `{0}`, `{1}`…
  - `app/assets/rumi_guia_en.js`, `rumi_guia_pt.js` — la Guía traducida; `index.html` carga la del idioma
    elegido (la arma `modelo/armar_guia_idiomas.js`).
  - `app/assets/rumi_mas.js` — análisis avanzados de Rumi (señales de la semana, cuándo vender, curva de
    ganancia, punto de equilibrio, costos, flujo a 90 días, compras, calendario, escenarios y simulador) y la
    página `#analisis` con su índice.
  - `app/assets/rumi_analisis2.js` — más análisis de Rumi: lo que te está costando dinero, ¿estoy dando de
    más o de menos? (comedero), de dónde sale la ganancia (compra y venta contra engorde), comparar lotes,
    sanidad y muertes, y cuántos días alcanza el alimento de la bodega.
  - `app/assets/rumi_saber2.js` — tablas de referencia: requerimientos por raza y clima, razas para engorde,
    precios orientativos por país y calendario sanitario por región.
  - `app/assets/ayuda.js` — ayuda en cada formulario («¿Qué anoto aquí?» y una nota por campo) y el
    tutorial corto de cada página (botón de Rumi en el encabezado; se repite en Rumi, Usar la app,
    Muéstrame la app).
  - `app/assets/avisos.js` — avisos de Rumi al teléfono (opcionales, apagados al inicio, uno al día como
    máximo y solo lo importante) y los datos del widget de inicio. Se configuran en Más, Configuración.
  - `app/assets/huella.js` — huella de carbono de cada lote y de la finca (IPCC 2019, nivel 2, con el alimento
    anotado): metano del rumen y del estiércol y óxido nitroso, en CO2e (AR6), y cómo bajarla. En la pestaña
    Números del lote, en Análisis y en los documentos.
  - `app/assets/documentos.js` — PDF para otros: informe productivo y financiero para el banco y certificado de
    lote. Van firmados digitalmente: cada teléfono crea una llave Ed25519 (`S.config.firma`, viaja en el respaldo);
    el PDF lleva al final `%RUMENTIS-FIRMA {…}` con la huella SHA-256 de todo lo anterior y la firma, y el QR es un
    enlace a la página pública de verificación con los datos firmados después de `#`. En Más, Documentos se
    verifica cualquier PDF de Rumentis (de cualquier finca) o un código emitido en el teléfono. Usa
    `lib/jspdf.umd.min.js`, `lib/qrcode.js` (MIT) y `lib/nacl-fast.min.js` (TweetNaCl, dominio público); ver
    `lib/LICENCIAS.txt`. Se cargan al hacer el primer documento. En Android el PDF se abre, se comparte o se
    guarda con las clases `Archivos`, `ProveedorArchivos` y `GuardarArchivo` (fuente en `modelo/android/`).
  - `app/assets/mercado.js` — precios del mercado (página `#mercado`): lee la Release `mercado` del repositorio,
    que llena `.github/workflows/mercado.yml` con `scripts/mercado/actualizar.js` dos veces al día (USDA AMS,
    Cepea/Esalq-USP, Banco Central do Brasil y tipos de cambio; en EE. UU. también el ternero y el novillo de engorde
    de la subasta de Oklahoma City, de USDA AMS y ERS). Estados Unidos y Brasil ven su mercado; los
    demás, referencias en su moneda y los precios que anotan de su zona. Da señales de venta y compra que
    entran en las alertas de Rumi y en los avisos al teléfono.
  - `app/assets/rumi_analisis3.js` — más análisis de Rumi: ¿qué mejorar primero? (cada palanca en dinero),
    ¿vendo ahora o espero, con el mercado?, ¿estoy mejorando lote a lote? y ¿con qué ración sale más barato engordar?
  - `app/assets/zona.js` — comparación anónima y voluntaria con otros engordes de la zona (cuartiles de
    ganancia, conversión, mortalidad, costo y días), contra el servidor de `servidor/zona`. Sin servidor
    configurado, compara con la referencia.
  - `app/assets/fondos/rumi.webp` — retrato de Rumi: Brahman en Costa Rica, foto de Bernard Gagnon (CC0).
  - `app/assets/fondo.js` — fondo vivo sin partículas: curvas de nivel que respiran (se recalculan cada 3 s en un
    rato libre y se funden), pasto en el cerro de adelante que mece el viento real de la zona y sombras de nubes.
    Todo se dibuja fuera de pantalla y se muestra como imagen; lo que no se mueve (cielo, luces, cerros y textura)
    va horneado en una sola imagen, sin máscaras ni mezclas CSS, para que la tarjeta gráfica del teléfono pinte
    una capa y no ocho. El movimiento es transform u opacity y se pausa mientras tocas o haces scroll.
  - Dibujado de páginas (`render` y `pintarEn` en `index.html`): la página nueva se compara con la que ya está y
    solo se cambia lo distinto (no se rehace todo con `innerHTML`), así las fotos, los íconos y los botones que no
    cambian no parpadean. Lo que el código pone después lleva `data-fijo` y se respeta; al terminar se lanza el
    evento `rumentis-pintado`. No se usan View Transitions: la página cambia en el mismo cuadro y el contenido
    solo se desliza un poco.
  - `app/smali/` — el código Android (WebView, guardar archivos, permisos de cámara y ubicación, fotos,
    compartir y lectura en voz alta: la clase `Voz` elige la voz más natural del teléfono en el idioma de la app;
    su fuente Java está en `modelo/android/Voz.java`). Los avisos (`Avisos`, `AvisoReceiver`) y el widget
    (`RumiWidget`, `res/layout/rumi_widget.xml`) tienen su fuente en `modelo/android/`. `Pantalla`
    (`modelo/android/Pantalla.java`) pide al teléfono su tasa de refresco más alta (90, 120 o 144 Hz) en
    `onCreate` y `onResume`, así la app va a la tasa de la pantalla. Se compilan con
    `javac --release 8` contra `android-all.jar`, se pasan a dex con `dx` y a smali con apktool.
  - `app/apktool.yml` — versión (`versionCode`, `versionName`) y SDK.
- `modelo/` — el conocimiento de Rumi.
  - `fichas/*.json` — temas a fondo: `{t, a, r (resumen), s: [{h, p: [puntos]}], rel: [temas]}`. Áreas:
    sanidad, nutricion, manejo, forrajes, instalaciones, negocio, normas, conceptos, fisiologia, genetica,
    carne, actualidad y app.
  - `saber_extra.json`, `saber/*.json` — datos rápidos (se suman a los que ya trae la app).
  - `areas.json` — área de cada tema; `correcciones_menu.json` — textos ajustados al Rumi por menús.
  - `construir_saber.js` → arma `app/assets/rumi_guia.js` (y `modelo/rumi_saber.json` para revisarla).
  - `traducciones/*.tsv` → catálogos de la interfaz (`armar_i18n.py`); `traducciones/guia/*.tsv` → la Guía
    en inglés y portugués (`armar_guia_idiomas.js`, con `-v` lista lo que falte); `traducciones/guia/region/`
    → lo que cambia por región: el inglés trae datos de Estados Unidos y el portugués de Brasil (instituciones,
    leyes, estaciones, unidades, pastos, razas, precios y enfermedades). Ver `traducciones/LEEME.md`.
  - `ilustraciones/` → toros y paisajes de cada pantalla (`escenas.py` los escribe en index.html).
  - `embeddings/`, `datos/` — el modelo de comprensión de la versión 3.3 (ya no se usa; queda como historia).
- `verificar/` — página pública de verificación de documentos (un solo HTML + `nacl-fast.min.js`): abre el enlace
  del QR o recibe el PDF y comprueba la firma en el navegador, sin servidor. La publica `.github/workflows/pages.yml`
  en https://modelqdeluxe-ops.github.io/ss/verificar/ desde `main` (una vez: Settings → Pages → Source: GitHub Actions).
- `servidor/zona/` — servidor de la comparación con la zona (Cloudflare Workers + D1), con pruebas; ver su `LEEME.md`.
- `scripts/mercado/actualizar.js` — junta los precios del mercado; lo corre `.github/workflows/mercado.yml`
  (cada 12 horas desde la rama principal, o a mano) y los publica en la Release `mercado`.
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
