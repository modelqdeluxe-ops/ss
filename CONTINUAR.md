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
app/assets/vision.js      BETA: RF-DETR Seg con onnxruntime-web (Vision.segmentar(img); Vision.motor() en Web Worker)
app/assets/pesocam.js     BETA: página #pesocam "Peso con cámara" (solo si config.beta): modos, fórmulas, calibración
app/assets/camvivo.js     BETA: cámara en vivo (CamVivo.abrir): silueta en color, indicadores y captura automática
app/assets/i18n*.js       Traducción en pantalla (catálogos generados, no editar a mano)
modelo/traducciones/*.tsv Frases: clave<TAB>inglés<TAB>portugués  →  python3 modelo/armar_i18n.py
modelo/vision/            BETA: seg.onnx (33 MB), ort.bundle.js, ort-wasm-simd-threaded.wasm, cv.js, aruco.js,
                          licencias, exportar.py, LEEME.md, VERSION (versión de la beta)
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

**5.0.0-beta.2** (esta rama): **cámara en vivo con captura automática**, lo que pidió el dueño:

- `#pesocam` tiene modo **Personas / Ganado** (Personas por defecto; se recuerda en `localStorage` `rumentis-pc-modo`).
  Personas: frente + costado → estatura y peso. Ganado: costado + por detrás → peso (se agrega al pesaje del lote);
  "Una sola foto de costado" sigue disponible.
- `vision.js`: núcleo compartido (`NUCLEO`) que se usa en la página y dentro de un **Web Worker** armado desde un Blob.
  La página descarga `ort.bundle.js`, el `.wasm` y `seg.onnx` y se los pasa al worker (`env.wasm.wasmBinary`, import de
  un Blob), así el worker no pide nada por red. Si falla, `Vision.motor()` corre en la página.
  Por cuadro devuelve score, caja y ancho de cada fila de la silueta en una rejilla de 312, y la máscara 78×78.
- `camvivo.js`: pantalla completa con video, silueta roja/ámbar/verde, marca encuadrada, "Paso 1 de 2 · De frente",
  chips **Detectado · Completo · Distancia · Marca · Ángulo · Quieto**, indicación grande, progreso, cuadros/s reales,
  captura manual, cambio a cámara frontal (en espejo), vibración + sonido, pantalla encendida (wakeLock).
  Reglas en `CamVivo.REGLAS` (score 0.5, margen 2 %, marca ≥ 36 px, IoU ≥ 0.93, 3 cuadros) y en `PesoCam.MODOS`
  (distancia y ángulo por paso). Cambio respecto al diseño: el ángulo de costado de una persona es **relativo** a la
  toma de frente (≤ 0.3 y ≤ 75 % de la proporción de frente), porque una regla fija fallaba con personas gruesas.
- Peso en vivo: `kg = c · V^b`, `V = A_principal(cm²) · ancho_secundario(cm) / 1000`; el ancho secundario es el
  percentil 90 del ancho por fila en una franja del cuerpo (persona de costado 18–55 % de la altura; ganado por detrás
  5–55 %). c de fábrica: persona 0.58, ganado 0.72. Calibración por modo (`cal` con `modo` y `V`): 1–5 → c = mediana
  de kg/V; 6+ → también b (0.7–1.3). Las de ganado guardan también `A` de costado (sirve para la fórmula de una foto).
- `Fotos.camara(frontal)` (fotos.js) abre la cámara trasera preferida (o la frontal) a 1280×960 para el video en vivo.
- Probado en Chromium (escenas sintéticas con fotos de COCO): pide "Acércate" si está lejos, captura sola de frente,
  rechaza el costado si sigue de frente, estatura 174 cm en una escena armada a 170 cm, calibración ajusta a la báscula.
  ~1.4 s por cuadro en CPU de escritorio (en worker); la medición completa tarda ~20 s con las escenas de prueba.

**No probado aún en un teléfono real** (velocidad en WASM de teléfono, que el WebView entregue el worker desde Blob,
cámara real, marca impresa, fórmula de personas con gente real).

---

## 5. SIGUIENTE TAREA

1. Que el dueño instale `RumentisBeta-5.0.0-beta.2.apk` y pruebe **Personas** con la marca impresa. Pedirle: cuadros/s
   que muestra, si la captura sale sola, estatura medida vs. real, peso estimado vs. báscula (y calibrar).
2. Con lo que reporte, ajustar reglas (`CamVivo.REGLAS`, `PesoCam.MODOS`) y la franja de la profundidad.
   Si va muy lento (< 0.5 cuadros/s): bajar a 2 cuadros seguidos o probar WebGPU (modelo fp16) / parte nativa.
3. Cuando esté en la finca: probar **Ganado** (costado + por detrás) y calibrar con la báscula.

### Ideas que el dueño aprobó para después
- **Aprendizaje en la nube (fase 1):** con permiso del usuario, subir foto + medidas + peso de báscula a Cloudflare
  (R2 + D1) y reentrenar cada noche la fórmula del peso (por raza/sexo/etapa); las apps descargan la fórmula nueva.
- **Fase 2:** reentrenar RF-DETR con las fotos acumuladas (GPU rentada, ~US$1–5 por entrenamiento) con evaluación
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
- `beta_t.js` peso con cámara en la beta, ganado con una sola foto (usa 8112 con `cv.js`, `aruco.js`, `lado.jpg` =
  vaca de costado, COCO 000000090062, `seg.onnx`, `ort.bundle.js`, `ort-wasm-simd-threaded.wasm`).
- `vivo_t.js` cámara en vivo con personas (8112 además con `frente.jpg` = COCO 000000223959 y `lejos.jpg` =
  COCO 000000295478; las fotos se bajan de `https://s3.amazonaws.com/images.cocodataset.org/val2017/<id>.jpg`).
  `SIN_WORKER=1` prueba el respaldo sin Web Worker.
- `vision_t.js` + `vision_prueba.html` el modelo solo en el navegador.
- `humo.js` (usa `rel_mercado.json`), `morf_t.js`, `nav_t.js`, `fondo_t.js`, `cam_t.js` regresiones generales.
- Traducciones: correr una prueba con `IDIOMA=xx` (graba las claves en `claves_*.json`) y
  `node faltan_eq.js claves_x.json` → debe decir `faltan en 0 pt 0`.
- Servidor: `cd servidor/equipo && node prueba.mjs`.

Compilación: cada push que toca `app/**`, `scripts/**`, `modelo/android/**` o `modelo/vision/**` corre
`compilar.yml` y publica un Release con `Rumentis-<v>.apk`, `RumentisEquipo-<v>.apk`, los `.aab` y
`RumentisBeta-<versión beta>.apk`.

Para volver a exportar el modelo: ver `modelo/vision/LEEME.md` (PyTorch se instala desde PyPI; `download.pytorch.org`
está bloqueado en este entorno; los pesos de RF-DETR bajan de `storage.googleapis.com/rfdetr/`).
