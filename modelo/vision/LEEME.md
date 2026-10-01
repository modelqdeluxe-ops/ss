# Visión de Rumentis Beta

Archivos que solo lleva la app **Rumentis Beta** (`scripts/variante.py` los copia a `assets/vision/` cuando la
variante es beta). Todo corre en el teléfono, sin internet.

| Archivo | Qué es | Licencia |
|---|---|---|
| `silueta.onnx` | Modelo **rápido**, para cada cuadro del video. LR-ASPP MobileNetV3-Large de torchvision (preentrenado en COCO con clases VOC) afinado para 3 clases: 0 fondo, 1 persona, 2 vaca. Entrada 256×256, salida `logits` [1,3,32,32] (1/8; la app la agranda y traza el contorno). 6.5 MB (pesos en 16 bits). | BSD-3 (`LICENCIA-torchvision.txt`); fotos de entrenamiento: COCO (CC BY 4.0) |
| `silueta_p.onnx` | Modelo **rápido de personas** (v4, destilado): el mismo más un refinamiento a 1/4 de la entrada con los rasgos de esa resolución (bordes más finos); salida `logits` [1,3,64,64]. Afinado con 24,135 fotos de COCO, entre ellas 4,000 escenas de casa (personas con cama, sillón, silla, mesa, tv…) al doble de peso (`entrenar_silueta2.py`, `datos_casa.py`); v4: 2 épocas más con las siluetas del modelo preciso (RF-DETR) en 5,866 fotos como respuesta (`maestro_silueta.py`). 6.5 MB (pesos en 16 bits). | BSD-3; COCO (CC BY 4.0) |
| `cuerpo.onnx` | **Puntos del cuerpo** de personas: RTMW (`rtmw-dw-m-s_simcc-cocktail14_270e-256x192`, OpenMMLab), 133 puntos de COCO-WholeBody (cuerpo, pies, cara, manos y dedos). Entrada `input` [1,3,256,192] (un recorte alrededor de la persona con 25 % de margen), salidas SimCC `simcc_x` [1,133,384] y `simcc_y` [1,133,512] (el máximo de cada una, a medio píxel, con una parábola alrededor). 31 MB (pesos en 16 bits). | Apache 2.0 (`LICENCIA-mmpose.txt`) |
| `cuerpo_v.onnx` | **Puntos del cuerpo en el video**: DWPose-s (`rtmpose-s_simcc-ucoco_dw-ucoco_270e-256x192`, OpenMMLab), los mismos 133 puntos, ~2 veces más rápido que RTMW-m. 17 MB (pesos en 16 bits). | Apache 2.0 (`LICENCIA-mmpose.txt`) |
| `animal_m.onnx` | **Puntos del cuerpo** de animales: RTMPose-m AP-10K (OpenMMLab), 17 puntos (ojos, nariz, cuello, base de la cola y hombro/codo/pata y cadera/rodilla/pata de cada lado). Entrada [1,3,256,256], salidas SimCC [1,17,512]. 27 MB (pesos en 16 bits). | Apache 2.0 (`LICENCIA-mmpose.txt`) |
| `seg.onnx` | Modelo **preciso**, solo para las fotos capturadas. RF-DETR Seg Nano (Roboflow), preentrenado en COCO, exportado a ONNX (opset 17, entrada 312×312) y cuantizado a int8 (pesos) con `onnxruntime.quantization.quantize_dynamic`. 33 MB. Máscaras casi idénticas al original (IoU ≈ 0.99). | Apache 2.0 (`LICENCIA-rf-detr.txt`) |
| `ort.bundle.js`, `ort-wasm-simd-threaded.wasm` | onnxruntime-web 1.30.0 (solo WebAssembly), `ort.wasm.bundle.min.mjs` con otro nombre para que el WebView lo entregue como JavaScript. | MIT (`LICENCIA-onnxruntime.txt`) |

## Velocidad y precisión

Medido en Chromium de escritorio (WASM, 1 hilo, como en el teléfono) y contra la silueta dibujada a mano de
COCO val2017 (160 personas y 28 vacas grandes y completas, `conjunto_prueba.py` + `evaluar_silueta.py`):

| Modelo | Tiempo por cuadro | IoU persona | IoU vaca | Error de área persona / vaca |
|---|---|---|---|---|
| RF-DETR Seg Nano int8 (`seg.onnx`) | 1400 ms | 0.88 | 0.86 | 7 % / 7 % |
| LR-ASPP de torchvision, sin afinar | 88 ms | 0.73 | 0.60 | 30 % / 32 % |
| `silueta.onnx` a 320 px (afinado, época 5 de 6) | 83 ms | 0.75 | 0.66 | 26 % / 29 % |
| `silueta.onnx` a 256 px (ganado) | 59 ms | 0.73 | 0.63 | 27 % / 33 % |
| `silueta_p.onnx` v2 | 60 ms | 0.76 | 0.62 | 26 % / 35 % |
| `silueta_p.onnx` v3 | 60 ms | 0.77 | 0.63 | 23 % / 33 % |

Con el sujeto grande en el cuadro (recorte alrededor de la silueta real, como hace el seguimiento en vivo):

| Modelo | IoU persona | IoU vaca | Error de alto persona / vaca | Error de área persona / vaca |
|---|---|---|---|---|
| LR-ASPP sin afinar | 0.78 | 0.69 | 7 % / 16 % | 17 % / 27 % |
| `silueta.onnx` a 320 px | 0.80 | 0.73 | 5 % / 14 % | 15 % / 23 % |
| `silueta.onnx` a 256 px (ganado) | 0.79 | 0.75 | 6 % / 14 % | 15 % / 22 % |
| `silueta_p.onnx` v2 | 0.80 | 0.69 | 6 % / 21 % | 16 % / 26 % |
| `silueta_p.onnx` v3 | 0.81 | 0.70 | 5 % / 17 % | 14 % / 25 % |
| RF-DETR (`seg.onnx`) | 0.89 | 0.86 | 3 % / 5 % | 7 % / 7 % |

Por eso el video usa el rápido y la medida final el preciso, en segundo plano. Un segundo afinado del mismo modelo
(16,100 fotos, 256 px) no mejoró: llegó a su techo. El v2 (refinamiento a 1/4, +2 ms) mejora personas (mediana 0.85,
cuadro completo 0.76 contra 0.73) pero no vacas: la app usa v2 para personas y v1 para ganado. Otras pruebas de
velocidad, descartadas por perder precisión o ir más lentas: int8 estático (78 ms, más lento en WASM), entrada 224 px
(50 ms, −2 a −3 puntos de IoU) y 192 px (36 ms, −4 a −6 puntos).
RF-DETR sin cuantizar es más lento (2150 ms); a 240 px, 1100 ms.

**Escenas de casa** (`ev/casa.json`: 144 personas de cuerpo completo junto a camas, sillones, sillas o mesas, de COCO
val2017; `casa.py val`): el fondo que más confunde al modelo rápido. Error de área con el sujeto grande / en cuadro
completo: v2 19 % / 38 %; **v3 17 % / 31 %**; RF-DETR 9 % (sujeto grande). IoU con el sujeto grande: v2 0.77, v3 0.78,
RF-DETR 0.87. Sesgo de tamaño (mediana, COCO): RF-DETR −0.5 % de área en personas; los rápidos 0 %: los modelos no
inflan la silueta. Subir el umbral de la silueta del rápido no ayuda (0 es el mejor en COCO).

**v4, destilado del modelo preciso** (el de la app para personas): RF-DETR marca la silueta de la persona principal en
5,866 fotos de entrenamiento (4,000 de casa y 1,866 de personas; `maestro_silueta.py`) y el rápido aprende a copiarla
(sus bordes son más fieles que los polígonos a mano de COCO). Mismo tamaño y velocidad. Medido con `ev/` (IoU / error
de área / error de alto / error de ancho entre 20 y 60 % del alto):

| Conjunto | v3 | v4 |
|---|---|---|
| Casa, sujeto grande | 0.769 / 19.7 % / 7.7 % / 19.6 % | **0.794 / 12.8 % / 6.5 % / 16.7 %** |
| COCO, sujeto grande | 0.811 / 13.4 % / 5.1 % / 14.6 % | **0.819 / 9.9 % / 5.1 % / 11.9 %** |
| Casa, cuadro completo | 0.724 / 29.8 % / 9.2 % / 25.2 % | **0.747 / 19.6 % / 8.6 % / 23.4 %** |
| COCO, cuadro completo | 0.775 / 21.8 % / 9.0 % / 21.2 % | **0.779 / 17.1 % / 8.5 % / 16.2 %** |

Desde beta.21 `silueta_p.onnx` acepta cualquier tamaño de entrada (los mismos pesos). La app usa 256 (beta.22: a 320
el video del teléfono bajó a 9 cuadros/s). Con 320 px IoU con el sujeto grande casa 0.794 → 0.802 y calle 0.819 → 0.826, error de alto 6.5 → 6.2 % y
5.1 → 4.7 %, con +35 % de tiempo; a 384 px empeora (casa 0.790). Tres épocas más con las siluetas del maestro en
19,373 fotos (todas las de personas) no mejoraron: mejor ancho, peor área e IoU. El modelo llegó a su techo.

**Ganado v2 (`silueta.onnx`, beta.24)**: la arquitectura de personas (refinamiento a 1/4), afinada desde la v4 de
personas con las vacas de COCO (siluetas del maestro, `maestro_vacas.py`, ×3), 5,926 fotos de campo de Kaggle BMGF
(costado y atrás, siluetas a mano, CC BY 4.0; personas del modelo preciso; `datos_campo.py`) y 30 % de las demás
fotos (para que siga viendo bien a la persona de referencia); 3 épocas, se usa la 2. Evaluación con fotos que no
entraron al entrenamiento (IoU / error de área):

| Conjunto | v1 | v2 | preciso (RF-DETR) |
|---|---|---|---|
| Campo, de costado (210 fotos, Kaggle) | 0.831 / 10.5 % | **0.927 / 2.5 %** | 0.940 / 3.0 % |
| Campo, por detrás (90 fotos, Kaggle) | 0.681 / 28.2 % | **0.917 / 4.6 %** | 0.938 / 3.6 % |
| Vacas de COCO (238, sujeto grande) | 0.806 / 14.3 % | **0.841 / 9.1 %** | 0.882 / 6.1 % |
| Personas, casa / calle (la persona de referencia) | 0.734 / 0.785 | **0.792 / 0.825** | — |

## Puntos del cuerpo (pose)

Medido contra los puntos marcados a mano de COCO val2017 (233 personas grandes de `ev/`, error en fracción del alto de
la persona; 17 puntos del cuerpo):

| Modelo | Tiempo (CPU, 1 hilo) | Error medio | Error mediano | Puntos a < 5 % |
|---|---|---|---|---|
| RTMPose-t (17 puntos) | 9 ms | 3.0 % | 1.6 % | 87 % |
| RTMPose-m (17 puntos) | 35 ms | 2.2 % | 1.3 % | 92 % |
| DWPose-t, 133 puntos (el del video en beta.9–10) | 13 ms (66 ms en WASM) | 3.9 % | 1.9 % | — |
| **DWPose-s, 133 puntos (`cuerpo_v.onnx`, el del video)** | 18 ms (94 ms en WASM) | 3.4 % | 1.7 % | — |
| **RTMW-m, 133 puntos (`cuerpo.onnx`, el de las fotos)** | 39 ms (180 ms en WASM) | 2.8 % | 1.4 % | — |
| RTMW-l, 133 puntos | 133 ms | 2.1 % | 1.2 % | — |

Con la caja movida como en el video (centro ±8 %/6 %, escala 0.85–1.3): DWPose-t 4.6 %, DWPose-s 3.6 %, RTMW-m 3.0 %;
manos contra el maestro RTMW-l: 5.1 %, 4.1 % y 3.3 %. En el video, DWPose-s (el mejor que va rápido); en la pantalla
solo se dibujan los puntos con confianza ≥ 0.4 (cuerpo) o ≥ 0.5 (pies, cara, manos): los muy fuera de lugar (> 10 % de
la estatura) bajan de ~3.7 % a 1–2 %.

**Reentrenar el del video** (`destilar/`, sin GPU): DWPose-t pasado a PyTorch (`a_torch.py`, onnx2torch), etiquetas del
maestro RTMW-l en 28,108 personas de COCO train2017 más sus puntos a mano (`etiquetar.py`), recortes con la caja movida,
girada, en espejo, con color, desenfoque y baja resolución (`entrenar.py`, pérdida SimCC como RTMPose), de vuelta a
ONNX (`exportar.py`) y `evaluar.py`. No mejoró: con aprendizaje 2e-4 empeora (cuerpo 3.9 → 5.4 %: olvida), con 1e-5
queda igual (4.0 %; caja movida 4.6 %). Superar el entrenamiento original (270 épocas en GPU) necesita GPU y más datos
de manos. Descartado; se usa DWPose-s.

En el video, DWPose-s (con manos y dedos); en las fotos, RTMW-m con la foto y su espejo promediados (el l
pesa 229 MB: no cabe en el teléfono).

**Limpiar la silueta con los puntos** (quitar lo que queda lejos del esqueleto), medido contra COCO (176 personas de
`ev/` más las de casa; IoU, error de área): con el modelo preciso siempre empeora (0.878 → 0.820 con márgenes de beta.8,
0.859 con el doble de holgura; también en escenas de casa): la foto que se mide no se limpia. Con el modelo rápido y 1.5
veces de holgura baja el error de área (casa 19 → 16 %, COCO 13 → 12 %) sin perder IoU: se limpia solo el video. La
estatura estimada con los puntos (oreja a pies) sale 0.95 veces la real (mediana; 95 % debajo de 1.03): sirve para
avisar de una silueta incompleta (> 1.08), no para medir. Cuantizar a int8 los
estropea (error de 40–55 %): los pesos van en 16 bits con `pesos16.py` (mismo resultado, la mitad de tamaño). Afinar el
borde de la silueta del preciso (medirla en dos mitades a más resolución, o un filtro guiado con la imagen) no mejoró
contra COCO: IoU 0.881 → 0.869 y 0.880.

```
curl -O https://download.openmmlab.com/mmpose/v1/projects/rtmw/onnx_sdk/rtmw-dw-m-s_simcc-cocktail14_270e-256x192_20231122.zip
curl -O https://download.openmmlab.com/mmpose/v1/projects/rtmposev1/onnx_sdk/rtmpose-m_simcc-ap10k_pt-aic-coco_210e-256x256-7a041aa1_20230206.zip
python3 pesos16.py <rtmw>/end2end.onnx cuerpo.onnx && python3 pesos16.py <ap10k>/end2end.onnx animal_m.onnx
python3 pesos16.py silueta_fp32.onnx silueta.onnx      # (igual con silueta_p)
```

## Volver a entrenar el modelo rápido

Sin GPU (4 CPU, ~1.3 s por paso de 16 fotos a 320 px; 6 épocas ≈ 100 min):

```
python3 -m venv vis && vis/bin/pip install --index-url https://download.pytorch.org/whl/cpu torch torchvision
vis/bin/pip install onnx onnxruntime onnxscript pillow numpy scipy pycocotools
python3 bajar_anotaciones.py train && python3 bajar_anotaciones.py val   # inst_train.json, inst_val.json
vis/bin/python datos_silueta.py        # td/: todas las fotos con vacas, 6000 con personas, 1500 con otros animales
EPOCAS=6 vis/bin/python entrenar_silueta.py   # m/sil<época>_320.onnx y _256.onnx (la app usa el de 256)
vis/bin/python conjunto_prueba.py      # ev/: fotos de COCO val2017 que no se usaron para entrenar
vis/bin/python evaluar_silueta.py m/sil6_320.onnx:full
```

El entrenamiento parte de los pesos de torchvision y pasa las 21 clases a 3 conservando las de fondo, persona y vaca;
usa recortes con el sujeto grande y el cuadro estirado a cuadrado (como el video), vacas repetidas 3 veces y otros
animales (caballo, oveja, perro, oso, elefante) como fondo para no confundirlos con ganado.

## Volver a generar el modelo preciso

```
python3 -m venv vis && vis/bin/pip install torch torchvision rfdetr onnx onnxruntime onnxscript
vis/bin/python exportar.py            # rfdetr-seg-nano.onnx (fp32, 123 MB)
vis/bin/python -c "from onnxruntime.quantization import quantize_dynamic,QuantType;quantize_dynamic('output/rfdetr-seg-nano.onnx','seg.onnx',weight_type=QuantType.QUInt8)"
```

Salidas: `dets` [1,100,4] (cx, cy, w, h normalizados), `labels` [1,100,91] (logits COCO; 1 = persona, 21 = vaca) y
`masks` [1,100,78,78] (logits de la silueta). Entrada: `input` [1,3,312,312], RGB normalizado con la media y la
desviación de ImageNet (igual que el rápido).
