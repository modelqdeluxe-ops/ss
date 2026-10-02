# Rumentis — guía para continuar el trabajo

Documento para la IA (o persona) que retome el proyecto. Léelo completo antes de tocar código.
Última actualización: 29 sep 2026. Rama de trabajo: `claude/new-session-fdysl6` (sale de `claude/happy-ritchie-u6007w`;
si el dueño indica otra rama, usa esa).

---

## 1. Qué es el proyecto

**Rumentis**: app Android para engordes de ganado (feedlots). La app es un **WebView** que carga
`app/assets/index.html` (HTML/JS/CSS sin framework). El APK se arma con **apktool** (smali ya compilado en `app/smali`)
y, la versión de Google Play del dueño, con **Gradle** para llevar Google Play Billing (`scripts/pagos.sh`).

Hay varias apps que salen del mismo código (`scripts/variantes.sh` + `scripts/variante.py`):

| App | Paquete | Qué es |
|---|---|---|
| **Rumentis** (4.9.0) | `hn.hato.ganadero` | La app del dueño/administración. De pago. Vende licencias para el personal (US$1.99 c/u). |
| **Rumentis Equipo** (Team / Equipe según idioma) | `hn.hato.ganadero.vaquero` | App gratis del colaborador. Se activa con una licencia. |
| **Rumentis Beta** (5.0.0-beta.27) | `hn.hato.ganadero.beta` | **Beta de la próxima versión**: peso por cámara con visión en el teléfono. Se instala aparte. |
| **Equipo Beta** (5.0.0-beta.27) | `hn.hato.ganadero.vaquero.beta` | La del personal para la beta (con la visión): su teléfono es una **cámara enlazada** del peso con cámara. |
| Rumentis Prueba / Equipo Prueba | `.prueba` | Solo con `PRUEBA=1`, para probar sin cobrar. |

`config.js` dice qué app es: `window.RUMENTIS={app:'jefe'|'vaquero',prueba:bool,beta:bool,...}` (variante.py lo reescribe).

**Estado:** la app publicada (4.9.0) está **en pausa** por decisión del dueño. **Todo el trabajo nuevo va en la Beta.**

---

## 2. Reglas del dueño (respétalas siempre)

- Responde **en español**. El dueño escribe informal; sé directo y claro.
- Trabaja, haz commit y push **solo** en la rama de trabajo indicada arriba (`git push -u origin <rama>`).
  No hagas push a otra rama sin permiso. No crees Pull Requests si no te lo pide.
- No pongas identificadores de modelo de IA en commits ni en archivos del repo.
- Nunca pidas tokens de Cloudflare en el chat: van en los secretos de GitHub (`CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`, ya puestos).
- Textos de la app: **profesionales, claros, sin relleno**. Nada de "se actualiza solo", "conectado por internet",
  "en tiempo real" como frase decorativa, ni textos obvios de IA. No escribir "jefe", "vaquero" ni "dueño" en pantalla:
  usar **administración**, **colaborador(es)**, **personal**. La app del personal se llama **Rumentis Equipo** (Team/Equipe).
- "No rompas nada": corre las pruebas antes de subir (ver sección 6).
- Es un producto comercial: calidad alta, diseño cuidado, todo traducido a inglés y portugués.
- Licencias de terceros: solo permisivas (MIT, Apache 2.0, BSD). **No usar Ultralytics YOLO (AGPL)**; el dueño eligió
  RF-DETR (Apache 2.0).

---

## 3. Mapa del código

```
app/assets/index.html     App principal (enorme): estado S, calc(), PAGES, FORMS, SAVE, ACTS, render(), CSS
app/assets/config.js      Qué app es (app/prueba/beta) y URLs de servidores
app/assets/equipo.js      Pestaña Equipo (administración): licencias, colaboradores, tareas, reportes, alimentación
app/assets/equipo_nucleo.js Cripto (tweetnacl), sobres firmados/cifrados, archivos .rumentis/.campo, timbre WebSocket
app/assets/vaquero.js     App del personal (Rumentis Equipo): Hoy con tareas asignadas, terminar tarea con foto, reporte
app/assets/fotos.js       Cámara propia (Fotos.tomar(titulo,{soloCamara,max})) y fotos guardadas (IndexedDB / Android)
app/assets/fondo.js       Fondo animado (curvas de nivel + degradado)
app/assets/vision.js      BETA: dos modelos en Web Workers: Vision.motor('rapido') para el video, ('preciso') para la foto
app/assets/pesocam.js     BETA: página #pesocam "Peso con cámara" (solo si config.beta): modos, fórmulas, calibración
app/assets/camvivo.js     BETA: cámara en vivo (CamVivo.abrir): silueta en color, indicadores, seguimiento y captura sola
app/assets/pclab.js       BETA: laboratorio del peso con cámara (registro, estadísticas, gráficas, CSV)
app/assets/i18n*.js       Traducción en pantalla (catálogos generados, no editar a mano)
modelo/traducciones/*.tsv Frases: clave<TAB>inglés<TAB>portugués  →  python3 modelo/armar_i18n.py
modelo/vision/            BETA: silueta.onnx (13 MB, rápido), seg.onnx (33 MB, preciso), ort.bundle.js, el .wasm,
                          licencias, scripts para entrenar/evaluar, LEEME.md, VERSION (versión de la beta)
servidor/equipo/          Cloudflare Worker + D1 + Durable Object (relevo del equipo, /v1/sync). Prueba: node prueba.mjs
scripts/                  variantes.sh, variante.py, build.sh, pagos.sh…
.github/workflows/        compilar.yml (APKs + Release en cada push), servidores.yml (publica los Workers)
pruebas/                  Pruebas end-to-end con Playwright (ver sección 6)
README.md                 Documentación larga del producto (secciones Equipo, Rumentis Beta, etc.)
```

Detalles útiles:
- Los assets se sirven en el WebView desde `https://rumentis.local/<ruta>` (MainActivity intercepta, con CORS `*`;
  tipos MIME solo para .js, .wasm, .json, .html; lo demás octet-stream). La página se carga desde `file:///android_asset/index.html`.
- `Vision` usa `https://rumentis.local/vision/` en el teléfono; en pruebas se cambia con `window.VISION_BASE`.
- `ort.bundle.js` es `ort.wasm.bundle.min.mjs` de onnxruntime-web 1.30.0 renombrado (para que salga como JavaScript).
  Hay que pasar `env.wasm.wasmPaths={wasm:URL}` (solo el .wasm; el .mjs va incluido en el bundle).
- Traducción: `i18n.js` normaliza números/fechas/datos del usuario a `{0}`, `{1}`… Para saber la clave exacta usa
  `I18N.norm(texto)` en el navegador. Datos del usuario van con `data-no-tr`.

---

## 4. Rumentis Beta: dónde quedó

**5.0.0-beta.1** (commit `ba23cb8`): modelo RF-DETR Seg Nano (Apache 2.0) en ONNX int8 (33 MB, entrada 312×312;
salidas `dets` [1,100,4], `labels` [1,100,91] logits COCO (**1 = persona, 21 = vaca**), `masks` [1,100,78,78]); marca
ArUco MIP_36h12 id 7 de 16 cm con PDF para imprimir; peso con **una sola foto** de costado (`kg = a·área^b`) y
calibración con báscula.

**5.0.0-beta.2**: cámara en vivo con captura automática (con marca impresa).

**5.0.0-beta.3**, lo que pidió el dueño después de probar: **sin marca** ("no me pidas eso") y
**mucho más rápido**:

- **Sin marca de medida.** La escala sale de una estatura conocida: en Personas, la del usuario (se escribe una vez,
  `S.config.pesoCam.estatura`); en Ganado, la de una persona parada derecha a la par del animal (`.refAlto`), que el
  mismo modelo detecta (chip **Referencia**). Se quitaron la marca, su PDF, `cv.js`/`aruco.js` y la medición de una
  sola foto.
- **Dos modelos** (`vision.js`), cada uno en su Web Worker armado desde un Blob (la página le pasa onnxruntime, el
  `.wasm` y el modelo; el worker no pide nada por red; si no arranca, corre en la página):
  - **rápido** `silueta.onnx`: LR-ASPP MobileNetV3 (torchvision, BSD-3) **afinado** aquí para fondo/persona/vaca con
    ~9,100 fotos de COCO (`modelo/vision/entrenar_silueta.py`, 6 épocas en CPU; se usa la 5: IoU con sujeto grande
    0.80 persona / 0.73 vaca, antes 0.78 / 0.69; tabla en `modelo/vision/LEEME.md`). ~90 ms por cuadro en PC (antes
    1.4 s): el video va a 5–10 cuadros/s. Se queda con la mancha más grande de cada clase.
  - **preciso** `seg.onnx` (RF-DETR int8): solo mide las fotos capturadas, en segundo plano mientras la persona se
    gira. IoU 0.88 persona / 0.86 vaca contra la silueta real de COCO. Si falla, queda la medida del rápido y se avisa.
- **Seguimiento** (`zonaSig` en camvivo.js): cuando ya encontró al sujeto, el rápido mira solo esa zona (con margen),
  así ve el cuerpo con más detalle; si la zona lo corta, el siguiente cuadro se mira completo.
- Medidas en píxeles (camvivo) → cm en pesocam con la estatura: persona `cm/px = estatura / alto de su silueta`;
  ganado `cm/px = refAlto / alto de la persona de referencia` en esa misma foto.
- Probado en Chromium con escenas de COCO: personas, medición completa ~7.5 s (antes ~22 s), de verde a foto 0.25 s;
  ganado: largo 202 cm (real 200) y alto 149 cm (real 150) solo con la estatura de la persona de referencia.

**5.0.0-beta.4**. El dueño probó beta.3 en su teléfono: iba a 4 cuadros/s, la silueta gruesa, y la medida
"súper imprecisa y variable". Cambios:

- **Fluidez**: modelo rápido a 256 px (59 ms en PC, antes 83); el cuadro se recorta y reduce con `createImageBitmap` y va
  directo al worker (`OffscreenCanvas`); un cuadro en vuelo por worker y **dos workers** del rápido en teléfonos con 6+
  núcleos; el dibujo va aparte a 60 cuadros/s con `requestAnimationFrame` (la caja se desliza entre resultados). En PC:
  12.8 cuadros/s con un worker, 20 con dos (antes 5–9).
- **Silueta fina**: el worker traza el contorno exterior (vecinos de Moore) y lo suaviza; se dibuja una línea de 2 px
  con relleno suave y esquinas de 1.5 px (antes una mancha y un rectángulo de 4 px). Seguimiento con histéresis (el
  recorte se queda quieto mientras el sujeto esté cómodo dentro: si no, la silueta temblaba y "Quieto" no se cumplía).
- **Medida precisa**: RF-DETR mide cada foto sobre un **recorte alrededor del sujeto** (+ persona de referencia).
- **Modelo matemático por volumen** (antes área × ancho × constante): rebanadas elípticas. Personas: cada fila de
  frente con la profundidad de costado a la misma altura relativa; brazos (tramos fuera del tronco) redondos. Ganado:
  cada columna de costado sin patas, con la forma de atrás escalada. kg = k·L^b (k 0.98 personas, 1.1 ganado; b = 1).
  Validado con cuerpos sintéticos (≤ 3 %). Escala: estatura + 2.5 cm (suela y pelo). Calibraciones nuevas `{modo, L, kg}`
  (las de beta.2/3 con `V` ya no se usan).
- Un segundo afinado del modelo rápido (16,100 fotos, 256 px) no mejoró: se quedó el de la época 5.
- Prueba `vivo_t.js`: repetibilidad (3 mediciones con la persona movida y a otra distancia: 63 / 63 / 63 L).

**5.0.0-beta.5**. El dueño probó beta.4: ~20 cuadros/s ("super mejor"). Pidió más precisión, datos de
desarrollador, mejor modelo matemático, letras más chicas en la cámara, explicar el modelo y la firma de su empresa:

- `camvivo.js`: **2 fotos por ángulo** para el modelo preciso, tomadas antes del pitido (la primera versión las tomaba
  después y mezclaba ángulos); "Midiendo… n/m"; métricas de rendimiento en `caps.stats`; texto de la cámara más chico;
  firma "Powered by Rumentis Labs" en la cámara y "Rumentis Labs" en las fotos.
- `pesocam.js`, **modelo v2**: promedio de las 4 combinaciones de fotos y su CV; ropa (selector); forma por parte del
  cuerpo (tronco 0.81, resto π/4); fondo con mediana de 5 filas; volumen por partes; k de fábrica 1.0 kg/L (personas).
  Hoja del resultado con "Cómo se calcula". Registro de mediciones `S.config.pesoCam.reg`; la calibración sale del
  registro (mediciones con `real`) y de las calibraciones viejas `cal` con `L`.
- `pclab.js` (nuevo): laboratorio con estadísticas (validación cruzada, MAPE, MAE, sesgo, RMSE, R², repetibilidad del
  volumen, dispersión entre fotos, rendimiento), gráficas SVG, detalle por medición, anotar peso de báscula, CSV.
- `documentos.js`: `Documentos.enviar` acepta el tipo de archivo (para el CSV).
- Prueba `vivo_t.js`: además del laboratorio (registro, estadísticas, detalle, báscula desde el registro, CSV).

**5.0.0-beta.6**. El dueño pidió más cuadros ("romper los 20"), más precisión, más entrenamiento (+4,000
fotos) y mandó un CSV del laboratorio de beta.5 (15 mediciones, 180 cm, sin báscula): 147–189 L, IMC 45–58, pecho de
fondo 36–48 cm, brazos 0 L. Diagnóstico: tomas "de costado" giradas a ~45° (el fondo sale (ancho+fondo)/√2 y las dos
piernas no se tapan) y brazos pegados contados como tronco. Cambios:

- **Chequeo de perfil real** (`ang.perfil` .65): el ancho del tronco a la altura del pecho (mediana de filas 25–45 %,
  sobre la altura) de costado debe ser ≤ 65 % del de frente; si no, "Gírate un poco más: de perfil completo".
- **Brazos pegados** (`volPersona`): si de frente casi no se ven separados (< 15 % de filas entre hombros y caderas),
  cada brazo se estima de 5.2 % de la estatura de ancho: se resta del tramo central y se cuenta redondo. (Exigir brazos
  separados en vivo no sirve: ni el modelo rápido ni el preciso ven bien el hueco.)
- **Plausibilidad**: fondo/ancho del pecho > 0.9 o IMC fuera de 15–40 → aviso en el resultado. Nuevos campos en el
  registro y el CSV: `fondo_ancho`, `brazos_pegados`.
- **Calibración robusta**: Theil–Sen en logaritmos (solo con 6+ y volúmenes ≥ 15 % de rango; si no, b = 1 y k = mediana).
- **Modelo rápido de personas v2** (`silueta_p.onnx`, `entrenar_silueta2.py`): refinamiento a 1/4 empezando en cero,
  20,135 fotos (4,000 más: 3,000 personas y 1,000 otros animales), 3 épocas. Personas mejor; vacas no → ganado sigue
  con `silueta.onnx`.
- **Cuadros/s**: 3 workers en teléfonos de 8 núcleos; la cámara se pide a 30 cuadros/s. Probado y descartado: int8
  (más lento en WASM), entradas de 224/192 px (pierden precisión). WebGPU: en este entorno solo hay GPU por software
  (no carga el modelo en 90 s), no se pudo medir: pendiente para probar en el teléfono.

**5.0.0-beta.7**. CSV de beta.6 (5 mediciones, 180 cm): 118–136 L (mejor que 147–189 pero alto), pecho de
fondo 34–44 cm, piernas 50–54 L, 28–53 s por medición, preciso 2.8–4.4 s por foto. El dueño vio que la silueta en vivo
tomaba suelo, cama y cosas, y que tardaba en ponerse en verde; pidió un indicador de qué tan cerca está. Medido: ningún
modelo infla la silueta en COCO (RF-DETR −0.5 % de área), así que el exceso viene de la escena. Cambios:

- **Topes anatómicos** en `volPersona` (`TOPE`, `TOPE_ALTO`) y en las piernas de costado el tramo más grande (una
  pierna). Campo `topadas` (filas recortadas) en el registro y el CSV; aviso si pasa de 25 %.
- **Indicador de posición** en la cámara (producto de qué tanto se cumple cada regla), "quieto" contra la mediana de
  las últimas 3 cajas, 1 foto por ángulo si el preciso tardó > 1.5 s la vez anterior (`rumentis-pc-msp`).
- **Fotos de cada medición** en el teléfono (`Fotos`, claves `pc-<id>-0/1`, las últimas 40) y en el detalle del
  laboratorio; `Fotos.limpiar` ya no borra las `pc-`.
- **Entrenamiento con escenas de casa**: 4,000 fotos de COCO con personas y muebles (cama, sillón, silla, mesa, tv…)
  ×2, más prueba `ev/casa.json` (144 personas de cuerpo completo junto a muebles). Ver `modelo/vision/LEEME.md`.
- "Cómo hacerlo": pared lisa detrás y perfil completo (hombro hacia la cámara).

**5.0.0-beta.8**. CSV de beta.7 (11 mediciones, 180 cm, sin báscula): 89–109 L; la app decía ±10 %
(el error de fábrica). Diagnóstico: `brazos_pegados` = 1 en todas (el tramo más ancho del pecho incluía los brazos y
se le restaban ~19 cm), `filas_recortadas` 50–87 % (los topes decidían el volumen), hombros 18–44 cm (fila fija al 22 %).
El dueño pidió 1 % de error, más entrenamiento y puntos blancos en el cuerpo "tipo traje de captura", hasta en los dedos.
Cambios:

- **Puntos del cuerpo** (`cuerpo.onnx`, RTMW 133 puntos; `animal_m.onnx`, AP-10K 17 puntos; OpenMMLab, Apache 2.0):
  en vivo (worker propio, recorte alrededor de la silueta; entre resultados siguen la caja de la silueta) y en las
  fotos del resultado y del laboratorio. `w.dataset.puntos` = puntos confiables (para las pruebas).
- **Silueta limpia con el esqueleto** (`limpiarSil`): en vivo (con puntos de < 600 ms y la caja casi igual) y en las
  fotos (con los puntos de la misma foto). Campo `limpia` (fracción quitada).
- **Perfil con los hombros** (`ang.hombros` .3, `hombrosN`): además del ancho del tronco.
- **Modelo de peso v3** (`VERSION_MODELO` 3): fórmula ajustada con ANSUR II (`modelo/peso/`), medidas a las alturas de
  ANSUR con los brazos y manos quitados por los puntos; `pred` (kg sin calibrar), `dims`, `topes`, `puntos` en el
  registro y el CSV. La calibración de personas usa `pred` (solo mediciones v3); error de fábrica 4 %.
- Dos fotos por ángulo siempre (una sola si el preciso tardó > 4 s). Instrucciones: de frente con las manos un poco
  separadas de las piernas; de perfil con los brazos a los lados; ropa ajustada, sin zapatos ni gorra.
- Modelos rápidos y de pose con pesos en 16 bits (`pesos16.py`): la APK crece ~45 MB en vez de ~90.

**No probado aún en un teléfono real.** El 1 % que pidió el dueño: sin calibrar, el límite del modelo es ~3 % (lo que
no se ve en la silueta: grasa, músculo, hueso); con su báscula (k y b por persona) el error que queda es la
repetibilidad de la medición (en las pruebas, < 1 % entre tomas de la misma escena).

**5.0.0-beta.9**. CSV de beta.8 (5 mediciones, 180 cm): 118–141 kg, cv entre fotos 0–1 %. Contra hombres
de ANSUR II de 175–185 cm e IMC 30–40 (mediana 104 kg): hombros bien (53 vs 55 cm), pero pecho de frente 22 cm (se
cortaban "brazos" ya separados), cintura de frente 42–49 (brazos sin cortar) y todo lo de perfil 20–40 % alto. El dueño:
puntos lentos (~1 por segundo) y "al final del shape no cuenta toda mi figura". Medido en COCO: la limpieza con los
puntos de beta.8 le quitaba cuerpo a la silueta precisa (IoU 0.88 → 0.82, error de alto 3 → 7 %). Cambios:

- Puntos en vivo con **DWPose-t** (`cuerpo_v.onnx`, ~3 veces más rápido), dos workers a turnos en 8 núcleos,
  suavizado al llegar; fotos con RTMW-m y su espejo promediados.
- La foto que se mide ya no se limpia; el video sí, con 1.5 veces de holgura.
- `cortar` (pesocam.js): brazo o mano se quitan solo si están pegados al borde del tramo (y no hay otro tramo junto).
- `marco`: si la silueta es > 8 % más corta que el cuerpo según los puntos, la escala y las alturas salen de los puntos.
- `giroPerfil`: el giro de la toma de perfil corrige los fondos (corte ovalado) y la separación de las piernas; el fondo
  del muslo no pasa de 1.3 veces su ancho. Campos `giro` e `incompleta` en el registro y el CSV.

**5.0.0-beta.27** (esta rama): **la app aprende sola** y **cinta para quien no tiene báscula** (el dueño: "8 % es
intolerable", "debe entrenarse sola", "¿y los que no tienen báscula?").
- `etiquetas()` en pesocam.js: toma como pesos reales la báscula, el peso de entrada de cada animal (medido a ≤ 3 días
  de su ingreso), los pesajes a mano (del animal o del lote) y las ventas (peso del comprador/matadero contra la cámara
  de los 7 días antes); ajusta k y b (b por el crecimiento de cada animal), el factor propio de cada animal
  (`factorAnimal`) y su peso con sus mediciones anteriores (`historialAnimal`, Kalman). El registro guarda lote y arete.
- Cinta: perímetro del pecho en el resultado → `pesoCinta` (5.4 % en Hereford sin calibrar); aprende aparte.
- Prueba `pruebas/aprende_t.js`; vivo_t.js usa la cinta y el arete.
- Lo siguiente: el modelo global con lo que aprende cada finca (capas 1 y 2: solo sumas, sin fotos, con permiso).

**5.0.0-beta.26**: **teléfonos enlazados** y razas.
- `enlace.js`: hasta tres teléfonos toman al animal en el mismo instante: la administración (de costado, con la persona
  de referencia) y uno o dos más (por detrás, otro costado o desde arriba). Conexión directa WebRTC (canal de datos
  cifrado) por el mismo Wi-Fi o el punto de acceso de un teléfono, **sin internet**. Enlace: con QR (la administración
  muestra el suyo, el otro teléfono lo lee y muestra el suyo, la administración lo lee) o, si hay servidor del equipo,
  por sobres cifrados (`cam` de la administración al colaborador, `camr` de vuelta; `EquipoJefe` en equipo.js,
  `tarjetaCam` en vaquero.js). Con internet usa STUN de Cloudflare; sin internet, las direcciones de la red local
  (se abre la cámara un instante para que el teléfono las dé, no nombres .local).
- `camvivo.js`: modo `enlace` (la administración espera a que todas las cámaras estén en verde y un disparo las hace
  tomar a la vez; las fotos llegan por el canal y las mide el modelo preciso de este teléfono; si llega la de atrás, no
  se pide ese paso) y modo `personal` (la cámara del otro teléfono sigue al animal, avisa si está lista, toma sus fotos
  al disparo y muestra el resultado). Si un teléfono se cae, la administración sigue sola.
- `pesocam.js`: el otro costado (sin persona de referencia) da el fondo de pecho entre la alzada y se promedia; desde
  arriba se guarda el ancho entre el largo (laboratorio). Etapa del animal (ternero −0.40, en crecimiento −0.20,
  adulto 0) y puntos de partida revisados con promedios publicados por raza (`modelo/peso/ganado/tablas/promedios_razas.csv`).
- Nueva app **Equipo Beta** (`scripts/variantes.sh`, `compilar.yml`).
- Búsqueda por raza (388 consultas en 7 repositorios + 496 artículos): no hay bases abiertas con cada animal de
  Brahman, Pardo Suizo o Girolando; con los promedios publicados, el modelo queda a ±8 % para la mayoría de las razas.
  Lo que separa razas es el ancho (con perímetro, una sola fórmula para todas: 6–17 %); medido en fotos todavía es
  ruidoso (r = 0.30 desde arriba en Hereford): ver `modelo/peso/ganado/LEEME.md`.
- Prueba: `pruebas/enlace_t.js` (tres teléfonos en tres páginas, WebRTC real).

**5.0.0-beta.25**: ganado con modelo de peso **v7**. Una hora de búsqueda masiva (Kaggle, Hugging Face,
Zenodo, figshare, Mendeley, DataCite, Dryad, Dataverse, Embrapa, ScienceDB, GitHub; registro en
`modelo/peso/ganado/LEEME.md`) y ajuste con 1,526 animales pesados (CC BY 4.0): Horqin (fotos), cebú Bororo de Níger,
criollos Curraleiro Pé-Duro, bovinos de Indonesia y terneros Simmental (tablas en `modelo/peso/ganado/tablas/`).
Lo que se aprendió: a igual alzada y fondo de pecho, un cebú o criollo pesa ~0.6 veces lo de un europeo de carne y un
animal de menos de un año ~0.67 veces lo de un adulto; v6 (un solo punto de partida europeo) daba 50–120 % de error en
cebú y criollos. v7: `ln peso = a(tipo) [−0.40 joven] + 2.0 ln alzada + 0.54 ln(fondo/0.52 alzada)`; el tipo de animal
(cebú o criollo, cruce, europeo de carne, lechero) y la edad se eligen por lote en la cámara (de inicio, por la raza
del lote; `tipoDeRaza`). Sin calibrar, en otra base del mismo tipo: 8–17 % (Bororo 16.7, Indonesia 7.9, Curraleiro
14.2, JXcow 14.2, Hereford 12.0); dentro de la raza igual que v6 (Horqin 7.9 %). Calibración de ganado: b de 0.4 a 1.15
(con fotos ruidosas el peso se acerca al promedio del lote: JXcow 13 → 10 % con 10 animales). VERSION_MODELO 7: las
calibraciones de ganado de v6 no cuentan. Campos `tipo` y `joven` en el registro y el CSV del laboratorio.

**5.0.0-beta.24**: silueta rápida de ganado v2 (`silueta.onnx`): afinada con las vacas de COCO (maestro) y
5,926 fotos de campo de Kaggle BMGF con silueta a mano (costado y atrás). Campo: IoU de costado 0.83 → 0.93, por detrás
0.68 → 0.92; COCO 0.81 → 0.84; personas (la de referencia) también mejor. Tabla en `modelo/vision/LEEME.md`; scripts
`maestro_vacas.py`, `datos_campo.py`, `entrenar_silueta2.py` (PESO_VACA, PESO_CAMPO, FRAC_OTROS). Captura: con los
puntos del cuerpo, el perfil se decide con los hombros (las reglas de la silueta contra la toma de frente solo sin
puntos); sin worker la prueba se quedaba en "Gírate de costado" (fallaba desde beta.18 por una milésima).

**5.0.0-beta.23**: ganado con modelo de peso v6 (alzada y fondo de pecho de la foto de costado, con la
silueta del modelo preciso y los puntos AP-10K; `GANADO`, `medidasGanado`, `pesoGanado` en pesocam.js). Ajustado con
Horqin (Mendeley Data h2s22wr5py, Bai 2025, CC BY 4.0): 8.2 % dentro de la raza; Hereford/Angus calibrando con 5
animales 9.6/6.3 % (el volumen × 1.1 anterior daba 30–39 %). La calibración de ganado ahora es sobre el peso del
modelo (como personas): las calibraciones viejas en litros ya no cuentan. Todo en `modelo/peso/ganado/LEEME.md`;
atribución CC BY: Horqin (Bai, Mendeley Data, doi 10.17632/h2s22wr5py.3) y Kaggle BMGF (Acme AI, bhalo y mPower, doi
10.34740/KAGGLE/DSV/8858637).

**5.0.0-beta.22**: el dueño vio 9 cuadros/s con beta.21 (con beta.4 tenía ~20). Seis modelos a la vez en
un teléfono de 8 núcleos: 3 workers de silueta + 3 de puntos, y la silueta a 320 px. Arreglo: silueta otra vez a 256
px (320 mejoraba poco), puntos en 2 workers (6 núcleos o más) y, si la silueta baja de 15 cuadros/s, los puntos
esperan entre uno y otro (hasta 150 ms; el dibujo los desliza igual). Regla: **la velocidad del video manda**; medir
cuadros/s en el teléfono antes de agregar carga. En la PC de pruebas (8 núcleos simulados en 4): 8.3 → 11.4 cuadros/s.

**5.0.0-beta.21**: `silueta_p.onnx` con tamaño de entrada libre (mismos pesos v4); 320 px en teléfonos
de 6 núcleos o más (IoU casa 0.794 → 0.802, calle 0.819 → 0.826; +35 % de tiempo), 256 en los demás. Probado y
descartado: 384 px (peor) y 3 épocas más con el maestro en 19,373 fotos (`td/msk_t` completo; mejor ancho, peor área).

**5.0.0-beta.20**: pecho, cintura y cadera de frente se miden de tres maneras (sin cortar, brazo de
tabla, brazo medido) y se queda la que cuadra con los hombros (`PRIOR` en pesocam.js, de ANSUR II). Banco de 33
adultos de pie de COCO (`pruebas/banco_medidas/`): fuera de rango pecho 18 → 6 %, cintura 12 → 6 %, cadera 15 → 3 %.

**5.0.0-beta.19**: el dueño veía siempre "una medida salió fuera". Causas: de frente, `cortar` suponía un
brazo de medio ancho fijo (4.9 cm) y con manga no lo reconocía (pecho con brazos: 55 cm en vez de 28); ahora mide el
medio brazo (del borde a la línea de sus puntos, hasta 1.8 medios anchos) y lo quita entero. De perfil el brazo
relajado queda encima del tronco y se cortaba (fondo del pecho y del glúteo 3–7 cm cortos): ya no se corta. El aviso
sale solo si la medida se salió en la mitad o más de las combinaciones de fotos. Captura: un cuadro que falla por poco
(posición ≥ 85 %) no reinicia la cuenta (dos seguidos sí); quieto 0.9 → 0.86; perfil algo más holgado (hombros
0.36, tronco 0.7, proporción 0.8 de la de frente, máx 0.32). Repetibilidad de la prueba 3.0 → 1.8 %. Todo lo
aprendido para llevarlo al ganado: `modelo/APRENDIZAJES.md`.

**5.0.0-beta.18**: silueta rápida de personas v4, destilada del modelo preciso: RF-DETR marcó 5,866
fotos (`modelo/vision/maestro_silueta.py` → `td/msk_t`) y el rápido se afinó 2 épocas con ellas
(`SOLO_MAESTRO=1 DESDE2=v3_256_e2.pt RES=256 EPOCAS=2 LR=2e-4 entrenar_silueta2.py`). Error de área con el sujeto
grande: casa 19.7 → 12.8 %, COCO 13.4 → 9.9 %; IoU casa 0.769 → 0.794 (tabla en `modelo/vision/LEEME.md`).
Dos épocas más (LR 1e-4) no mejoraron: IoU igual, error de área con sujeto grande peor (casa 12.8 → 13.6 %,
COCO 9.9 → 10.4 %); solo el cuadro completo en casa mejoró un poco. Descartado: v4 llegó a su techo con este maestro.

**5.0.0-beta.17**: puntos en vivo más rápidos: DWPose-s en tres workers a turnos con 8 núcleos (dos con
6), respiro de 60 ms (antes 120) con menos de 6 núcleos, suavizado más ligero (55 % lo nuevo si se movió < 1.2 % del
alto; antes 35 % y 1.5 %) y el dibujo se desliza 70 % por cuadro hacia el último resultado (antes 50 %).

**5.0.0-beta.16**: el dueño no quiso el escáner ni el recuadro de beta.15. Contorno con halo suave del
color de estado, línea limpia y relleno apenas visible; recuadro solo con cuatro esquinas finas redondeadas (blancas;
de color al estar listo); sin línea de escaneo. Puntos: solo si describen un cuerpo (`poseBuena`: hombros y caderas y
≥ 10 de 17 puntos confiables; con la mano de cerca el modelo inventaba un cuerpo) y dedos solo con muñeca confiable y
mano de tamaño lógico (`manoBuena`). Probado y descartado para la silueta en vivo: espejo promediado (0.811 → 0.814,
ruido), filtro guiado (igual) y MediaPipe Selfie (peor: IoU casa 0.73 contra 0.77).

**5.0.0-beta.15**: diseño de la cámara más profesional: contorno con curvas (sin escalones), línea fina con
brillo y filo blanco, relleno en degradado; recuadro con esquinas redondeadas con brillo y marco fino; línea de escaneo
mientras no está en verde; etiqueta de estado (Ajusta / Casi / Listo) sobre el recuadro (en el DOM: se traduce; en
espejo con la cámara frontal); colores esmeralda, ámbar y coral.

**5.0.0-beta.14**: modelo v5 sin datos demográficos (el dueño no los quiere): 2.3 % con el ruido de una
foto (con sexo y edad era 2.23 %), 1.65 % exacto. Se quitaron sexo y edad del formulario. Más medidas visibles en la
foto (tobillo, pie, cabeza, largos) bajan a lo más ~0.05 puntos; el brazo ayudaría (→ ~2.0 %) pero de perfil queda
tapado. Lo que falta para bajar de verdad son datos reales con báscula (ver `modelo/peso/LEEME.md`).

**5.0.0-beta.13**: 3 fotos por ángulo y la mediana de las 9 combinaciones (menos ruido por medida). El
dueño pidió 0.5–1 % sin báscula: el límite con las 93 medidas a mano de ANSUR II es 1.1 % (ver `modelo/peso/LEEME.md`);
bases revisadas: NHANES (otras definiciones de medidas) y BodyM (no comercial).

**5.0.0-beta.12**. El dueño pidió bajar al máximo el error de fábrica (sin báscula). Modelo de peso v4
(`VERSION_MODELO` 4): ANSUR II con pantorrilla, cuello, muslo sobre la rodilla, sexo y edad: error de validación 2.2 %
con el ruido de una foto (antes 3.1 %), 1.6 % con medidas exactas. Probado: modelo no lineal (gradient boosting) no
mejora al lineal; el antebrazo mejora en ANSUR pero de la foto solo sale su ancho (sesgo): descartado. Sexo obligatorio
y edad opcional en el formulario de la estatura (`PC().sexo`, `PC().edad`). Las medidas que no se pueden tomar se
estiman con las demás (`imputar`, campo `estimadas`). La calibración de personas usa solo mediciones v4.

**5.0.0-beta.10 y beta.11** (beta.11: beta.11: DWPose-s en el video y solo puntos confiables en pantalla). CSV de beta.9 (7 mediciones, 180 cm, 91–106 kg): hombros siempre al mínimo (37.9) y
pecho de frente al máximo (40.1): las alturas caían corridas hacia arriba (la silueta sin limpiar tenía algo sobre la
cabeza, o la escala). El dueño: los puntos en vivo "se salen, no se pegan". Cambios:

- Puntos del video con **DWPose-s** (cuerpo 3.4 % contra 3.9 % de DWPose-t; con la caja movida 3.6 contra 4.6 %) y
  solo los confiables en pantalla (≥ 0.4 cuerpo, ≥ 0.5 pies/cara/manos).
- Probado y descartado: reentrenar DWPose-t con RTMW-l de maestro (`modelo/vision/destilar/`): sin GPU no mejora
  (2e-4 olvida: 5.4 %; 1e-5 igual: 4.0 %).
- **Seguimiento con los puntos** (`cajaPuntos`): el recorte del modelo de pose sale de los puntos del cuadro anterior;
  los puntos ya no siguen la caja de la silueta (los arrastraba fuera del cuerpo).
- **Alturas ancladas a los puntos** (`nivel` en pesocam.js): hombros 22 %, caderas 51.5 %, rodillas 71.4 % (COCO, de
  pie); si la silueta y los puntos difieren más de 4 % de la estatura, mandan los puntos. Hombros medidos entre 19 % y
  26 % y nunca menos de 1.1 veces la distancia entre las articulaciones.
- **Exportar para análisis** (laboratorio): las últimas 10 mediciones con sus fotos originales (`pcr-<id>-<ángulo>-<toma>`)
  en un JSON, para repetir la medición fuera del teléfono.

---

## 5. SIGUIENTE TAREA

1. Que el dueño instale `RumentisBeta-5.0.0-beta.24.apk` y pruebe **Personas** (escribir su estatura, medirse de frente
   y de costado, pared lisa, ropa ajustada). Pedirle: cuadros/s, cuánto tarda "Midiendo…", **su peso de báscula** en
   3–5 mediciones (calibra y da el error real) y el CSV del laboratorio.
2. Ajustar reglas (`CamVivo.REGLAS`, `PesoCam.MODOS`) con lo que reporte. Si el video va lento en su teléfono: bajar el
   rápido a 256 px (`MODELOS.rapido.R` en vision.js; el modelo es convolucional y se exporta a 256 sin reentrenar).
3. Mejor modelo rápido: más fotos de ganado propias (con permiso) y reentrenar; o WebGPU / parte nativa (TFLite).
4. Cuando esté en la finca: probar **Ganado** con una persona de referencia y calibrar con la báscula.

### Ideas que el dueño aprobó para después
- **Aprendizaje en la nube (fase 1):** con permiso del usuario, subir foto + medidas + peso de báscula a Cloudflare
  (R2 + D1) y reentrenar cada noche la fórmula del peso (por raza/sexo/etapa); las apps descargan la fórmula nueva.
- **Fase 2:** reentrenar los modelos con las fotos acumuladas (GPU rentada, ~US$1–5 por entrenamiento) con evaluación
  automática antes de publicar; el modelo se descarga en la app sin sacar otra APK.
- **Tiempo real nativo** para pesar animales pasando por la manga.

---

## 6. Cómo probar

Requisitos en este entorno: Chromium en `/opt/pw-browsers` (Playwright ya configurado), Node, Python 3.

```bash
# servir la app
npx http-server app/assets -p 8111 -s -c-1 &
# servidor del equipo local (para pruebas por internet)
(cd servidor/equipo && LOCAL=1 SIN_GUARDAR=1 node local.mjs) &
# archivos de visión para la beta (con CORS), p. ej. desde una carpeta con modelo/vision/* + fotos de prueba
npx http-server <carpeta> -p 8112 -s -c-1 --cors &
```

Pruebas en `pruebas/` (se corren desde una carpeta de trabajo; escriben capturas .png ahí):
- `reporte_t.js` reporte del día por archivo, fotos de evidencia, pedir repetir.
- `equipo_t.js` todo el equipo (necesita `DUENO=<código maestro>` en el entorno; el dueño lo tiene, no está en el repo).
- `red_t.js` equipo por internet (servidor local 8790), revocar licencia, cuenta solicitudes.
- `v2_t.js` recorrido visual del equipo 4.9.0.
- `aprende_t.js` la app aprende sola (peso de entrada, pesajes, ventas; factor de cada animal), sin cámara.
- `enlace_t.js` tres teléfonos enlazados (WebRTC en tres páginas; mismos servidores que vivo_t.js).
- `vivo_t.js` peso con cámara en vivo, personas y ganado (8112 con `silueta.onnx`, `seg.onnx`, `ort.bundle.js`,
  `ort-wasm-simd-threaded.wasm` y las fotos `frente.jpg` = COCO 000000223959, `lejos.jpg` = 000000295478,
  `lado.jpg` = 000000090062, `atras.jpg` = 000000467776; se bajan de
  `https://s3.amazonaws.com/images.cocodataset.org/val2017/<id 12 dígitos>.jpg`). `SIN_WORKER=1` prueba sin worker.
- `humo.js` (usa `rel_mercado.json`), `morf_t.js`, `nav_t.js`, `fondo_t.js`, `cam_t.js` regresiones generales.
- Traducciones: correr una prueba con `IDIOMA=xx` (graba las claves en `claves_*.json`) y
  `node faltan_eq.js claves_x.json` → debe decir `faltan en 0 pt 0`.
- Servidor: `cd servidor/equipo && node prueba.mjs`.

Compilación: cada push que toca `app/**`, `scripts/**`, `modelo/android/**` o `modelo/vision/**` corre
`compilar.yml` y publica un Release con `Rumentis-<v>.apk`, `RumentisEquipo-<v>.apk`, los `.aab` y
`RumentisBeta-<versión beta>.apk`.

Para volver a exportar el modelo: ver `modelo/vision/LEEME.md` (PyTorch se instala desde PyPI; `download.pytorch.org`
está bloqueado en este entorno; los pesos de RF-DETR bajan de `storage.googleapis.com/rfdetr/`).
