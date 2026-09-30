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
- `campo/` — página pública de la licencia de Rumentis Equipo (el QR de la administración lleva aquí): muestra la
  licencia y el botón de Google Play. La publica el mismo `pages.yml` en https://modelqdeluxe-ops.github.io/ss/campo/
  (y también en `vaquero/`, la dirección anterior).
- `servidor/zona/` — servidor de la comparación con la zona (Cloudflare Workers + D1), con pruebas; ver su `LEEME.md`.
- `servidor/equipo/` — servidor de relevo del equipo (Cloudflare Workers + D1): guarda y entrega los sobres
  cifrados entre el jefe y sus vaqueros; `npm test` lo prueba y `npm run local` lo levanta en tu computadora.
- Servidor opcional (hoy el equipo trabaja solo con archivos): `npm run local` lo levanta en una computadora del
  mismo Wi-Fi y, con su dirección, todo llega en vivo (WebSocket "timbre"). No hay botón para configurarlo en la app.
- `servidor/publicar.sh` — para más adelante: publica los dos servidores en Cloudflare; lo corre a mano
  `.github/workflows/servidores.yml`. Paso a paso en `servidor/PUBLICAR.md`.
- `scripts/mercado/actualizar.js` — junta los precios del mercado; lo corre `.github/workflows/mercado.yml`
  (cada 12 horas desde la rama principal, o a mano) y los publica en la Release `mercado`.
- `scripts/variantes.sh` — las apps (ver «Equipo»): `dist/Rumentis.apk` y `RumentisEquipo.apk` y, con `todo`,
  sus AAB para Google Play (las de prueba, con `PRUEBA=1`). GitHub Actions deja un solo Release con lo último.
  `scripts/variante.py` cambia paquete, nombre, color del ícono, `config.js` y la autoridad de archivos.
- `scripts/pagos.sh` — la app del dueño para Google Play: Gradle (proyecto en `scripts/donante/`) la arma con los
  recursos, assets y manifiesto de la variante más Google Play Billing y sus dependencias (AndroidX, Play Services)
  y `modelo/android/Pagos.java`; nuestro código smali entra como un dex más y los recursos conservan sus números
  (`--stable-ids`). Sin Gradle o sin Google Maven, la app sale sin pagos (en GitHub Actions es error).
- `scripts/build.sh` — APK: `dist/Rumentis.apk` (acepta `APP_DIR`, `SALIDA` y `DEX_EXTRA`).
- `scripts/build_aab.sh` — AAB para Google Play: `dist/Rumentis.aab` (mismas variables).

## Rumentis Beta (peso por cámara)

La beta de la próxima versión es una app aparte (**Rumentis Beta**, paquete `hn.hato.ganadero.beta`, versión en
`modelo/vision/VERSION`): se instala junto a Rumentis sin tocar sus datos. `scripts/variantes.sh` la arma con
`beta:true` en `config.js` y le copia `modelo/vision/` a `assets/vision/` (solo a ella). La app publicada sigue igual.

- **Peso con cámara** (`app/assets/pesocam.js`, página `#pesocam`, tarjeta en Hoy y botón en el pesaje), **sin nada
  que imprimir**: la escala en centímetros sale de una estatura conocida. Dos modos:
  - **Personas** (de fábrica, para probar sin estar en la finca): tu estatura (se escribe una vez); de frente y de
    costado.
  - **Ganado**: una persona de estatura conocida se para derecha a la par del animal; de costado y por detrás. El
    peso se junta en un pesaje del lote.
- **Cámara en vivo** (`app/assets/camvivo.js`): video a pantalla completa con el **contorno fino** de la silueta en
  rojo, ámbar o verde (dibujado a 60 cuadros por segundo: la caja se desliza entre un resultado del modelo y el
  siguiente), el paso ("Paso 1 de 2 · De frente") e indicadores **Detectado · Completo · Distancia · Ángulo · Quieto**
  (y **Referencia** en ganado). Al centro, la indicación que toca ("Acércate", "Aléjate", "Gírate de costado…",
  "Falta la persona de referencia junto al animal", "Quieto…"). Con todo en verde **3 cuadros seguidos** la foto se
  toma sola (vibración y sonido) y pide el siguiente ángulo; hay botón para tomarla a mano y para cambiar a la cámara
  frontal. Reglas en `CamVivo.REGLAS` y `PesoCam.MODOS`: seguridad ≥ 0.5; la silueta no toca los bordes (2 %);
  distancia por alto (personas 55–88 % del cuadro, ganado por detrás 45–85 %) o por ancho (ganado de costado
  50–88 %); ángulo por la proporción ancho/alto (persona de frente ≥ 0.24, de costado ≤ 0.3 y ≤ 75 % de la de
  frente, y el tronco a la altura del pecho ≤ 65 % de su ancho de frente: de perfil de verdad); ganado de costado ≥ 1.15, por detrás ≤ 0.9); referencia completa, de pie y ≥ 25 % del alto; quieto si la
  caja casi no se mueve (IoU ≥ 0.9).
- **Fluidez**: el cuadro se recorta y reduce con `createImageBitmap` (en la GPU) y va directo al worker, que lo lee con
  `OffscreenCanvas`; hay un cuadro en vuelo por worker del modelo rápido (tres workers en teléfonos con 8 núcleos, dos
  con 6); la cámara se pide a 30 cuadros/s (el techo real); y cuando ya encontró al sujeto el modelo mira solo esa zona (**seguimiento**, con histéresis para que el
  recorte no tiemble).
- **Visión en el teléfono** (`app/assets/vision.js`), dos modelos en **Web Workers** armados desde un Blob (la página
  es `file://`; el worker recibe de la página onnxruntime-web, el `.wasm` y el modelo ya descargados):
  - **rápido** `silueta.onnx` (ganado) y `silueta_p.onnx` (personas, v2 con refinamiento de bordes a 1/4), 13 MB cada
    uno, entrada 256×256: LR-ASPP MobileNetV3 (torchvision, BSD-3) **afinado por Rumentis** para fondo / persona / vaca
    con fotos de COCO (`modelo/vision/entrenar_silueta.py` y `entrenar_silueta2.py`, 20,135 fotos). ~60 ms por cuadro
    en una PC. Da la silueta y su contorno (se traza el borde y se suaviza).
  - **preciso** `seg.onnx` (33 MB): RF-DETR Seg Nano (Roboflow, Apache 2.0), int8. Solo mide las fotos capturadas, en
    segundo plano mientras la persona se gira, sobre un **recorte alrededor del sujeto** (ve el cuerpo con más
    detalle). Si no encuentra al sujeto, queda la medida del rápido y la app lo avisa.
  Cada silueta se queda con la mancha más grande (no suma otra persona ni pedazos sueltos). Todo en el teléfono, sin
  internet. Tabla de velocidad y precisión en `modelo/vision/LEEME.md`.
- **Peso por volumen** (`pesocam.js`, modelo v2): la escala en cm sale de la estatura (+2.5 cm de suela y pelo). Cada
  ángulo se captura con **2 fotos** (tomadas antes del pitido, aún quieto) y el modelo preciso mide todas; el volumen se
  calcula con cada par frente × costado (4 combinaciones) y se promedia; su dispersión (CV) entra en el rango. El
  cuerpo se corta en rebanadas:
  - personas: cada fila de la silueta de frente, con el fondo de costado a la misma altura relativa (mediana de 5
    filas); se descuenta la **ropa** (ajustada 0.4, normal 0.8, holgada 1.5 cm por lado); área del corte = f · ancho ·
    fondo con f = 0.81 en el tronco (superelipse de exponente ~2.2) y π/4 en cabeza, brazos y piernas; los brazos
    (tramos fuera del tronco) se toman redondos; si van pegados al tronco se estiman (5.2 % de la estatura de ancho
    cada uno) y se restan del tronco. Da el volumen por partes (cabeza, tronco, brazos, piernas). Avisa si el fondo del
    pecho pasa de 0.9 veces su ancho (la toma no era de perfil) o si el IMC sale fuera de 15–40;
  - ganado: cada columna del cuerpo de costado (sin las patas: debajo de donde la silueta se parte en tramos solo
    cuenta la panza), con la forma que se ve por detrás escalada al grueso de esa columna.
  peso = k · litros^b; de fábrica k = 1.0 kg/L en personas (la densidad del cuerpo) y 1.1 en ganado (las patas no
  entran en el volumen), b = 1. Validado con cuerpos sintéticos de volumen conocido (exacto en personas; ≤ 3 % en
  ganado). Rango = √(error del modelo² + CV entre fotos²). La hoja del resultado explica el cálculo con sus números
  ("Cómo se calcula").
- **Laboratorio** (`pclab.js`, en la misma página): cada medición queda en `S.config.pesoCam.reg` (las últimas 300:
  volumen, peso, medidas, partes, k y b usados, dispersión entre fotos, seguridad, fuente, cuadros/s, ms de cada
  modelo, duración). Se le puede anotar el peso de báscula (`real`), que también calibra: con 1 a 5 se ajusta k
  (mediana de kg/L); con 6 o más y volúmenes variados (≥ 15 %), también b, con Theil–Sen (robusto: una medición mala no
  arrastra el ajuste). Estadísticas: error esperado con **validación cruzada dejando una fuera**,
  error medio al medir (MAPE y MAE), sesgo, RMSE, R² (solo si los pesos de báscula varían ≥ 5 %), error del modelo de
  fábrica, **repetibilidad** (CV del volumen en mediciones seguidas, < 15 min), dispersión entre fotos, cuadros/s y
  tiempo del modelo preciso. Gráficas de estimado contra báscula (franja ±5 %) y de las últimas 30 mediciones.
  Detalle de cada medición y exportación a **CSV**.
- La cámara lleva la firma **Powered by Rumentis Labs** (y las fotos, "Rumentis Labs").
- Las fotos de un lote se juntan en un **pesaje** (promedio y, si se indica, el peso de cada arete) que se guarda como
  cualquier otro, marcado `metodo:'camara'`.
- Cómo se generó el modelo y sus licencias: `modelo/vision/LEEME.md`.

## Equipo

La app de la administración (**Rumentis**, de pago en Google Play) vende licencias para su personal: un producto
consumible (`licencia_vaquero`) por cada colaborador. Cada licencia es un código al azar de 95 bits
(`RV-XXXX-XXXX-XXXX-XXXX-XXXX`, con letra de control) para una sola persona, y va dentro de la compra de
Google (`obfuscatedProfileId`), así la compra queda atada a esa licencia. En pantalla se habla de administración,
colaboradores y personal.

**El día a día va por archivos (WhatsApp o correo), sin servidor:**

1. El colaborador se activa en **Rumentis Equipo** con su nombre y la licencia, y envía su *solicitud de acceso*.
   La administración la abre en Rumentis (se acepta sola, o se pregunta) y le devuelve la *actualización*.
2. La administración asigna tareas **de una vez, diarias o semanales** (1 a 5 veces por semana) y fija la **hora
   del reporte** (12:00 a 21:00). Una tarea puede ir **ligada a una acción** (entregar alimento, pesar o sanidad, en
   un lote o en cualquiera): al anotar esa acción, la app del personal pide la foto. Cada tarea puede llevar **indicaciones**. Le
   llega con la siguiente actualización.
3. El colaborador anota alimento, pesajes, sanidad y muertes y termina sus tareas. **Toda tarea se termina con una
   foto de evidencia** tomada con la cámara en ese momento (sin galería): la hoja *Terminar tarea* muestra la foto (se
   puede repetir), un comentario opcional para la administración y el botón **Enviar**, que se pone en verde solo
   cuando ya hay foto. Sin foto la tarea sigue pendiente. Los registros no
   se pueden borrar desde la app del personal. En su perfil pone foto, cargo y teléfono. A la hora del reporte le llega un
   aviso en el teléfono (Android; quedan programados 7 días aunque no abra la app): al tocarlo, la app abre el
   reporte ya armado para elegir WhatsApp o correo. La tarjeta de Hoy también lo envía con un toque.
4. **Reporte del día** (se arma solo): sus registros, tareas del día hechas (con su foto) y pendientes, avance de las
   semanales y, si quiere, sus novedades. Sale como archivo `.rumentis`, con las fotos de evidencia y el perfil.
5. La administración lo abre con Rumentis y **queda registrado al abrirlo** (en Ajustes del equipo se puede pedir
   revisarlo antes: cada registro con su marca y el botón **Registrar**). La página del reporte muestra novedades,
   tareas del día con la miniatura de su foto (se abre en grande), semanales y los registros del día con su hora.
   Desde ahí se puede **pedir que repita** una tarea: vuelve a quedar pendiente y necesita una foto nueva.
6. La administración envía la actualización (`.campo`): lotes al día, tareas y la confirmación de lo registrado,
   que el colaborador ve en la tarjeta de su reporte.

Lo que la administración todavía no registra vuelve a ir en el siguiente reporte (cada registro lleva su número, así
nunca se duplica); el reporte anterior queda como *incluido*. Cada archivo se abre una sola vez.

**App del personal (Rumentis Equipo; Team o Equipe según el idioma del teléfono)**: diseño propio para el trabajo del
día. Hoy muestra el avance de las tareas asignadas, las *Asignadas por la administración* (tarjetas con indicaciones y
el botón para hacerlas), las *Terminadas hoy* con su foto, Anotar, los *Pendientes de los lotes* (los que calcula
Rumentis: pesajes, vacunas, entregas; aparte de lo asignado) y *Tu reporte de hoy* (qué lleva y a qué hora sale). El
reporte solo lleva las tareas asignadas (y lo que se terminó de los lotes); si ese día no hay nada hecho, no sale uno
vacío. Con internet no hay botón de enviar; sin internet aparece, con su explicación, para mandarlo como archivo.

**Alimentación**: en la pestaña Equipo, los horarios de entrega y la cantidad por entrega de cada lote (*Plan de
alimentación*). En el formulario de alimento cada lote muestra la cantidad sugerida y de dónde sale: el plan de la
administración, el promedio de las últimas entregas a esa hora, o el consumo (o 2.6 % del peso vivo) repartido entre las
entregas.

**Licencias**: una sola hoja de compra con el precio y lo que incluye. En la app de Google Play se paga con Google Play;
con el código maestro también se puede crear sin costo (y probar la compra real); en la app de prueba la compra se simula.

**Con el servidor del equipo** (Cloudflare, `servidor/equipo`) todo esto llega solo: cada app hace una sola solicitud
`/v1/sync` que envía lo pendiente y trae lo nuevo (los sobres inválidos se rechazan uno por uno, sin trabar la cola),
el timbre WebSocket avisa al instante y, en reposo, no hay solicitudes. El reporte sale solo a la hora fijada; si
después anota algo o termina otra tarea, la administración recibe el reporte actualizado (uno por día en las listas).

- **Administración** (`app/assets/equipo.js`): pestaña **Equipo** con reportes por registrar, solicitudes,
  **Personal** (una tarjeta por colaborador con foto, cargo y el estado de su reporte de hoy), tareas (cada una abre
  su historial con fotos) y reportes recientes. Sin servidor, arriba van Enviar actualización / Abrir reporte; con
  servidor, esos botones quedan en Ajustes del equipo, junto con el estado de la sincronización y las licencias.
  Cada colaborador tiene su página (`#equipo/colab/<id>`): perfil, semana (días con reporte, tareas, registros),
  reportes, galería de fotos de evidencia, tareas, permisos, licencia y **Revocar licencia**: su sesión en la app del personal se
  cierra y se borran de su teléfono los datos de la finca; la licencia queda anulada para siempre, la clave del
  equipo cambia y la administración recibe una **licencia nueva** para otra persona (el pago no se pierde).
- **Fotos del equipo**: sobres `foto` (evidencia `eq-<fid>`) y `perfil` (`eq-pf-<id>`), JPEG de hasta 900 px,
  cifrados con la clave del equipo; se guardan con `Fotos` y `Fotos.limpiar` no las toca.
- **Colaborador** (`app/assets/vaquero.js`, app **Rumentis Equipo**, gratis): la bienvenida de Rumentis y luego su
  nombre y la licencia (escrita, pegada o escaneada del QR con BarcodeDetector o `lib/jsQR.js`). Solo Hoy, Lotes,
  Registrar, Tareas y Más: sin Rumi, sin dinero, sin documentos. Lo que anota va en una cola de operaciones
  numeradas que la administración confirma; al llegar la actualización se vuelven a aplicar las que faltan.
- **Tareas que se repiten** (`app/assets/extras.js`, `Agenda.progreso`): `rep` es `una`, `dia` o `s1`…`s5`; cada
  vez que se hacen queda en `hechos: [{f, v, n}]`; la semana va de lunes a domingo.
- **Núcleo** (`app/assets/equipo_nucleo.js`): llaves Ed25519 y X25519 por teléfono (tweetnacl); sobres firmados y
  cifrados (alta y bienvenida con `nacl.box`, lo demás con la clave del equipo, `nacl.secretbox`).
- **Archivos del equipo**: el colaborador manda `.rumentis` (`application/vnd.rumentis`, los abre Rumentis) y la
  administración manda `.campo` (`application/vnd.rumentis.campo`, los abre Rumentis Equipo; `variante.py` cambia el
  tipo en el manifiesto); si uno llega a la app equivocada, `Recibido.pasar` se lo entrega a la otra. Por dentro:
  `RUMENTIS` + tipo (1 = JSON, 2 = JSON comprimido) + nonce + `nacl.secretbox` con la llave de archivos de la app.
  Lleva un número único (`fid`): la app no abre dos veces el mismo archivo ni uno que hizo ella misma. En Android,
  tocar el archivo en WhatsApp o en el correo abre la app y lo recibe (`Enlace.deIntent` y el puente `Recibido`).
- **Configuración** (`app/assets/config.js`): qué app es y si es de prueba (lo escribe `variante.py`), la llave RSA
  de Google Play (para que Rumentis Equipo compruebe la compra), el producto y los enlaces.
- **Android**: `modelo/android/Enlace.java` registra los puentes `Pagos` (si la clase está en la app), `Cripto`
  (firma RSA de Google) y `Recibido`; `modelo/android/Pagos.java` es Google Play Billing 7 y solo va en la app de
  Google Play, que se arma con Gradle (`scripts/pagos.sh`).

**Código maestro**: en Equipo, «Tengo un código maestro». Con él (en `equipo.js` solo está su huella SHA-256) la app
de Google Play crea licencias sin costo; son licencias reales y Rumentis Equipo las acepta. El precio que se muestra
lo da Google Play (US$1.99 en `config.js` mientras no responde); la app vale US$3.99.

Las apps **de prueba** (paquetes `.prueba`, `PRUEBA=1 scripts/variantes.sh`: Rumentis Prueba y Equipo Prueba) no se
publican: en la de la administración las licencias se crean sin costo y en Equipo Prueba la licencia
`RV-PRUEBA-2026` entra a una finca de muestra. Una app de Google Play no acepta licencias de la app de prueba.

Para vender en Google Play: publica **Rumentis** como app de pago y crea en ella el producto integrado
`licencia_vaquero` (consumible, con su precio); publica **Rumentis Equipo** gratis (paquete `hn.hato.ganadero.vaquero`).
Copia la llave pública RSA de Rumentis en `playLlave` (`app/assets/config.js`).

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
scripts/variantes.sh todo   # y los AAB de Rumentis y Rumentis Equipo
```

Necesita Java; descarga apktool, uber-apk-signer, bundletool y las herramientas de Android la
primera vez. Para firmar con la llave de la app define `KEYSTORE` (o `KEYSTORE_B64`),
`KEYSTORE_PASS`, `KEY_ALIAS` (`hato`) y `KEY_PASS`. En GitHub Actions se toman de los secretos
del repositorio con esos mismos nombres.
