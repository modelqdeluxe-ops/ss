# Rumentis — guía para continuar el trabajo

Documento para la IA (o persona) que retome el proyecto. Léelo completo antes de tocar código.
Última actualización: 29 sep 2026. Rama de trabajo: `claude/happy-ritchie-u6007w`.

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
- Trabaja, haz commit y push **solo** en la rama `claude/happy-ritchie-u6007w` (`git push -u origin claude/happy-ritchie-u6007w`).
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
app/assets/vision.js      BETA: RF-DETR Seg con onnxruntime-web (Vision.segmentar(img))
app/assets/pesocam.js     BETA: página #pesocam "Peso con cámara" (solo si config.beta)
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

**Hecho y subido (commit `ba23cb8`, APK `RumentisBeta-5.0.0-beta.1.apk` en el Release de GitHub):**

- Modelo **RF-DETR Seg Nano** (Roboflow, Apache 2.0), preentrenado en COCO, exportado a ONNX (entrada 312×312) y
  cuantizado a int8 (33 MB; máscaras casi iguales al original, IoU ≈ 0.99). Salidas: `dets` [1,100,4] cxcywh normalizado,
  `labels` [1,100,91] logits COCO (**1 = persona, 21 = vaca**), `masks` [1,100,78,78] logits.
- `vision.js`: `Vision.segmentar(img,{max,umbral})` → `{ok,score,clase,caja,W,H,mask,area,lienzo,ms}`. Corre en el
  hilo principal (≈1.5 s por imagen en CPU de escritorio con WASM de 1 hilo). Clase fija: vaca (21) con respaldo en
  animales parecidos.
- Marca de medida: cuadro ArUco **MIP_36h12 id 7**, **16 cm** el cuadro negro. PDF para imprimir (carta) con regla de
  10 cm (`ACTS.pcMarcaPdf`). Detección con js-aruco2 (`PesoCam.buscarMarca`) → píxeles por cm.
- `pesocam.js` (página `#pesocam`, tarjeta en Hoy, botón en el formulario de pesaje): foto → silueta + marca →
  medidas en cm (área, largo, alto) → `kg = a·área^b` (de fábrica a = 450/12500^1.5, b = 1.5, ±15 %).
  Calibración con báscula (`S.config.pesoCam.cal`): 2–5 fotos ajusta a; 6+ ajusta a y b; muestra error típico.
  Las fotos de un lote se guardan como un pesaje (`addItem({tipo:'pesaje',…,metodo:'camara'})`).
- Probado con fotos de COCO (vacas) en Chromium: detección 94–97 %, marca bien medida (5.94 px/cm vs 6 reales).

**No probado aún en un teléfono real** (velocidad en WASM de teléfono, cámara real, marca impresa).

---

## 5. SIGUIENTE TAREA (lo que pidió el dueño y quedó por hacer)

El dueño no está en su finca, así que **la beta primero se prueba con PERSONAS** y luego se cambia a ganado.
Pidió **video en tiempo real** con indicadores y **captura automática**:

> "que sea en video en tiempo real, programa algo para que me muestre indicadores de reconocimiento en tiempo real,
> distancia perfecta y eso, entonces cuando esté en verde la silueta captura una foto automáticamente, y así me pide
> otro ángulo si es el caso"

### Diseño propuesto (aprobado en espíritu por el dueño)

1. **Modo Personas / Ganado** en `#pesocam` (Personas por defecto mientras no esté en la finca).
   - Personas: clase COCO **1**, ángulos **frente** y **costado**; resultado: estatura (cm) y peso estimado.
   - Ganado: clase **21**, ángulos **costado** y **atrás**; resultado: peso.
2. **Motor en un Web Worker** para que el video no se trabe: crear el worker desde un **Blob URL** (la página es
   `file://`, un worker de otro origen no se permite) y dentro hacer `import()` de `ort.bundle.js` desde
   `Vision.BASE` (tiene CORS). El hilo principal dibuja el cuadro del video en un canvas 312×312, manda los píxeles
   (transferibles); el worker normaliza, corre el modelo, elige la mejor detección de la clase pedida y devuelve
   score, caja, máscara 78×78 y medidas (área y caja de la silueta en una rejilla de 312). Si el worker falla, usar el
   camino actual en el hilo principal.
3. **Pantalla de cámara en vivo** (nuevo `camvivo.js` o dentro de pesocam.js), pantalla completa:
   - `<video>` + `<canvas>` encima con la **silueta** (máscara 78×78 escalada con suavizado) en **rojo / ámbar / verde**
     y la marca encuadrada.
   - Arriba: "Paso 1 de 2 · De frente" y chips de estado: **Detectado · Completo · Distancia · Marca · Ángulo · Quieto**.
   - Al centro, una indicación grande: "Acércate", "Aléjate", "Falta la marca", "Gírate de costado", "Quieto…".
   - Reglas (ajustables):
     - detectado: score ≥ 0.5;
     - completo: la silueta no toca los bordes (margen 2 %);
     - distancia: persona, alto de la silueta entre 55 % y 85 % del cuadro; ganado, ancho entre 55 % y 88 %;
     - marca: encontrada y con lado ≥ 36 px;
     - ángulo por proporción ancho/alto de la silueta: persona de frente ≥ 0.24 (brazos un poco separados),
       de costado ≤ 0.22; ganado de costado ≥ 1.15, de atrás ≤ 0.9;
     - quieto: IoU de la caja entre cuadros seguidos ≥ 0.93.
   - Cuando todo está en verde **3 cuadros seguidos** → captura automática: mediana de las medidas de esos cuadros +
     imagen JPEG del cuadro, vibración (`navigator.vibrate`), y pasa al siguiente ángulo. Botón de captura manual de
     respaldo. Mostrar cuadros por segundo reales, sin exagerar.
4. **Cálculo con dos ángulos** (volumen): `kg = c · A_principal(cm²) · ancho_secundario(cm) / 1000`.
   - Persona: A de frente × profundidad de costado; c de fábrica ≈ 0.58 (70 kg, 170 cm).
   - Ganado: A de costado × ancho de atrás; c ≈ 0.72 (novillo de 450 kg).
   - Calibración por modo con báscula (la de baño para personas): ajustar c con la mediana de kg·1000/(A·ancho); con 6
     o más, ajustar también el exponente. Guardar en `S.config.pesoCam.cal` con `modo`.
5. **Instrucciones para personas:** pegar la marca en la pared a la altura del pecho y pararse pegado a la pared junto
   a ella; de frente con los brazos un poco separados; de costado con los brazos pegados.
6. **Rendimiento:** WASM de 1 hilo (no hay SharedArrayBuffer en `file://`). En teléfono se espera ~1–3 inferencias
   por segundo. A futuro: WebGPU (modelo fp16) o parte nativa de Android (TFLite/NNAPI) para 10–20 por segundo.
7. **Pruebas:** Chromium acepta video falso: `--use-fake-device-for-media-stream --use-file-for-fake-video-capture=archivo.mjpeg`
   (un .mjpeg es una secuencia de JPEG). Armar un video con una foto de persona de cuerpo completo más la marca
   dibujada (fotos de personas de COCO: `https://s3.amazonaws.com/images.cocodataset.org/val2017/000000295478.jpg`,
   `000000575081.jpg`, `000000481573.jpg`; así se descargan desde este entorno) y comprobar que se captura solo.
8. Traducir todo (en/pt), actualizar README, subir `modelo/vision/VERSION` (p. ej. 5.0.0-beta.2), commit y push.

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
- `beta_t.js` peso con cámara en la beta (usa 8112 con `cv.js`, `aruco.js`, `lado.jpg` = foto de vaca de costado,
  `seg.onnx`, `ort.bundle.js`, `ort-wasm-simd-threaded.wasm`).
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
