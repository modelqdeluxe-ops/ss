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
- `vaquero/` — página pública de la licencia del vaquero (el QR del jefe lleva aquí): muestra la licencia y el
  botón de Google Play. La publica el mismo `pages.yml` en https://modelqdeluxe-ops.github.io/ss/vaquero/.
- `servidor/zona/` — servidor de la comparación con la zona (Cloudflare Workers + D1), con pruebas; ver su `LEEME.md`.
- `servidor/equipo/` — servidor de relevo del equipo (Cloudflare Workers + D1): guarda y entrega los sobres
  cifrados entre el jefe y sus vaqueros; `npm test` lo prueba y `npm run local` lo levanta en tu computadora.
- `servidor/publicar.sh` — publica los dos servidores en Cloudflare (plan gratis) y pone sus direcciones en la app;
  lo corre `.github/workflows/servidores.yml` con los secretos `CLOUDFLARE_API_TOKEN` y `CLOUDFLARE_ACCOUNT_ID`.
  Paso a paso, y cuánto cuesta, en `servidor/PUBLICAR.md`.
- `scripts/mercado/actualizar.js` — junta los precios del mercado; lo corre `.github/workflows/mercado.yml`
  (cada 12 horas desde la rama principal, o a mano) y los publica en la Release `mercado`.
- `scripts/variantes.sh` — las apps (ver «Equipo»): `dist/Rumentis.apk` y `RumentisVaquero.apk` y, con `todo`,
  sus AAB para Google Play (las de prueba, con `PRUEBA=1`). GitHub Actions deja un solo Release con lo último.
  `scripts/variante.py` cambia paquete, nombre, color del ícono, `config.js` y la autoridad de archivos.
- `scripts/pagos.sh` — la app del dueño para Google Play: Gradle (proyecto en `scripts/donante/`) la arma con los
  recursos, assets y manifiesto de la variante más Google Play Billing y sus dependencias (AndroidX, Play Services)
  y `modelo/android/Pagos.java`; nuestro código smali entra como un dex más y los recursos conservan sus números
  (`--stable-ids`). Sin Gradle o sin Google Maven, la app sale sin pagos (en GitHub Actions es error).
- `scripts/build.sh` — APK: `dist/Rumentis.apk` (acepta `APP_DIR`, `SALIDA` y `DEX_EXTRA`).
- `scripts/build_aab.sh` — AAB para Google Play: `dist/Rumentis.aab` (mismas variables).

## Equipo

La app del dueño (**Rumentis**, de pago en Google Play) vende licencias para su equipo: un producto
consumible (`licencia_vaquero`) por cada vaquero. Cada licencia es un código al azar de 95 bits
(`RV-XXXX-XXXX-XXXX-XXXX-XXXX`, con letra de control) para una sola persona, y va dentro de la compra de
Google (`obfuscatedProfileId`), así la compra queda atada a esa licencia.

- **Jefe** (`app/assets/equipo.js`): con la primera licencia aparece la pestaña **Equipo**: licencias con QR,
  enlace y botón de WhatsApp; vaqueros con sus permisos (alimento, pesaje, sanidad, muertes, tareas); tareas
  asignadas; lo que registró cada uno; dar de baja (la licencia queda anulada y la clave del equipo cambia).
- **Vaquero** (`app/assets/vaquero.js`, app **Rumentis Vaquero**, gratis): la bienvenida de Rumentis y luego su
  nombre y la licencia (escrita, pegada o escaneada del QR con BarcodeDetector o `lib/jsQR.js`). Solo Hoy, Lotes,
  Registrar, Tareas y Más: sin Rumi, sin dinero, sin documentos. Lo que anota va en una cola de operaciones
  numeradas que el jefe confirma; al llegar el estado del jefe se vuelven a aplicar las que faltan.
- **Núcleo** (`app/assets/equipo_nucleo.js`): llaves Ed25519 y X25519 por teléfono (tweetnacl); sobres firmados y
  cifrados (alta y bienvenida con `nacl.box`, lo demás con la clave del equipo, `nacl.secretbox`); transporte por
  el servidor de relevo y, siempre, por archivo para WhatsApp.
- **Archivo `.rumentis`** (tipo `application/vnd.rumentis`): `RUMENTIS` + tipo (1 = JSON, 2 = JSON comprimido) +
  nonce + `nacl.secretbox` con la llave de archivos de la app, así que solo Rumentis lo abre. Lleva un número único
  (`fid`): la app no abre dos veces el mismo archivo ni uno que hizo ella misma. En Android, tocar el archivo en
  WhatsApp (o compartirlo a la app) abre Rumentis y lo recibe (`Enlace.deIntent` y el puente `Recibido`); los
  `.json` de la versión anterior todavía se leen.
- **Configuración** (`app/assets/config.js`): qué app es y si es de prueba (lo escribe `variante.py`), el servidor
  del equipo, la llave RSA de Google Play (para que el vaquero compruebe la compra), el producto y los enlaces.
- **Android**: `modelo/android/Enlace.java` registra los puentes `Pagos` (si la clase está en la app), `Cripto`
  (firma RSA de Google) y `Recibido` (el archivo `.rumentis` con que se abrió la app); `modelo/android/Pagos.java` es Google Play Billing 7 y solo va en la app del dueño de
  Google Play, que se arma con Gradle (`scripts/pagos.sh`).

**Modo dueño**: en Equipo, «Tengo un código de dueño». Con el código del dueño de la app (en `equipo.js` solo
está su huella SHA-256) la app de Google Play crea licencias sin cobrar; son licencias reales y Rumentis Vaquero
las acepta. La pestaña Equipo siempre se ve; ahí se compra (o se crea) la primera licencia. El precio que se
muestra lo da Google Play (US$1.99 en `config.js` mientras no responde); la app vale US$3.99.

**Sin servidor** el vaquero se activa igual escribiendo solo la licencia: su alta sale firmada en un archivo para
el jefe, y del archivo que el jefe le devuelve (lleva la ficha del equipo) toma las llaves del equipo.

Las apps **de prueba** (paquetes `.prueba`, `PRUEBA=1 scripts/variantes.sh`) ya no se publican: en la del jefe
las licencias se crean sin cobrar y en la del vaquero la licencia `RV-PRUEBA-2026` entra a una finca de muestra.
Una app de Google Play no acepta licencias de la app de prueba.

Para vender en Google Play: publica **Rumentis** como app de pago y crea en ella el producto integrado
`licencia_vaquero` (consumible, con su precio); publica **Rumentis Vaquero** gratis. Copia la llave pública RSA
de Rumentis en `playLlave` y la dirección del servidor en `servidorEquipo` (`app/assets/config.js`).

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
scripts/build.sh            # dist/Rumentis.apk
scripts/build_aab.sh        # dist/Rumentis.aab
scripts/variantes.sh        # las cuatro apps (APK)
scripts/variantes.sh todo   # y los AAB de Rumentis y Rumentis Vaquero
```

Necesita Java; descarga apktool, uber-apk-signer, bundletool y las herramientas de Android la
primera vez. Para firmar con la llave de la app define `KEYSTORE` (o `KEYSTORE_B64`),
`KEYSTORE_PASS`, `KEY_ALIAS` (`hato`) y `KEY_PASS`. En GitHub Actions se toman de los secretos
del repositorio con esos mismos nombres.
