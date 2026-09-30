# Visión de Rumentis Beta

Archivos que solo lleva la app **Rumentis Beta** (`scripts/variante.py` los copia a `assets/vision/` cuando la
variante es beta). Todo corre en el teléfono, sin internet.

| Archivo | Qué es | Licencia |
|---|---|---|
| `silueta.onnx` | Modelo **rápido**, para cada cuadro del video. LR-ASPP MobileNetV3-Large de torchvision (preentrenado en COCO con clases VOC) afinado para 3 clases: 0 fondo, 1 persona, 2 vaca. Entrada 256×256, salida `logits` [1,3,32,32] (1/8; la app la agranda y traza el contorno). 13 MB, fp32. | BSD-3 (`LICENCIA-torchvision.txt`); fotos de entrenamiento: COCO (CC BY 4.0) |
| `silueta_p.onnx` | Modelo **rápido de personas** (v2): el mismo más un refinamiento a 1/4 de la entrada con los rasgos de esa resolución (bordes más finos); salida `logits` [1,3,64,64]. Afinado con 20,135 fotos de COCO (`entrenar_silueta2.py`). 13 MB. | BSD-3; COCO (CC BY 4.0) |
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
| `silueta_p.onnx` v2 (personas) | 60 ms | 0.76 | 0.62 | 26 % / 35 % |

Con el sujeto grande en el cuadro (recorte alrededor de la silueta real, como hace el seguimiento en vivo):

| Modelo | IoU persona | IoU vaca | Error de alto persona / vaca | Error de área persona / vaca |
|---|---|---|---|---|
| LR-ASPP sin afinar | 0.78 | 0.69 | 7 % / 16 % | 17 % / 27 % |
| `silueta.onnx` a 320 px | 0.80 | 0.73 | 5 % / 14 % | 15 % / 23 % |
| `silueta.onnx` a 256 px (ganado) | 0.79 | 0.75 | 6 % / 14 % | 15 % / 22 % |
| `silueta_p.onnx` v2 (personas) | 0.80 | 0.69 | 6 % / 21 % | 16 % / 26 % |
| RF-DETR (`seg.onnx`) | 0.89 | 0.86 | 3 % / 5 % | 7 % / 7 % |

Por eso el video usa el rápido y la medida final el preciso, en segundo plano. Un segundo afinado del mismo modelo
(16,100 fotos, 256 px) no mejoró: llegó a su techo. El v2 (refinamiento a 1/4, +2 ms) mejora personas (mediana 0.85,
cuadro completo 0.76 contra 0.73) pero no vacas: la app usa v2 para personas y v1 para ganado. Otras pruebas de
velocidad, descartadas por perder precisión o ir más lentas: int8 estático (78 ms, más lento en WASM), entrada 224 px
(50 ms, −2 a −3 puntos de IoU) y 192 px (36 ms, −4 a −6 puntos).
RF-DETR sin cuantizar es más lento (2150 ms); a 240 px, 1100 ms.

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
