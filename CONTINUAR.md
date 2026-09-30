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
| **Rumentis Beta** (5.0.0-beta.1) | `hn.hato.ganadero.beta` | **Beta de la próxima versión**: peso por cámara con visión en el teléfono. Se instala aparte. |
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

**5.0.0-beta.4** (esta rama). El dueño probó beta.3 en su teléfono: iba a 4 cuadros/s, la silueta gruesa, y la medida
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

**No probado aún en un teléfono real.**

---

## 5. SIGUIENTE TAREA

1. Que el dueño instale `RumentisBeta-5.0.0-beta.4.apk` y pruebe **Personas** (escribir su estatura, medirse de frente
   y de costado). Pedirle: cuadros/s que muestra, cuánto tarda "Midiendo…", peso estimado vs. báscula (y calibrar).
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
