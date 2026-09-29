# Visión de Rumentis Beta

Archivos que solo lleva la app **Rumentis Beta** (`scripts/variante.py` los copia a `assets/vision/` cuando la
variante es beta). Todo corre en el teléfono, sin internet.

| Archivo | Qué es | Licencia |
|---|---|---|
| `seg.onnx` | RF-DETR Seg Nano (Roboflow), preentrenado en COCO, exportado a ONNX (opset 17, entrada 312×312) y cuantizado a int8 (pesos) con `onnxruntime.quantization.quantize_dynamic`. 33 MB. Máscaras casi idénticas al original (IoU ≈ 0.99). | Apache 2.0 (`LICENCIA-rf-detr.txt`) |
| `ort.bundle.js`, `ort-wasm-simd-threaded.wasm` | onnxruntime-web 1.30.0 (solo WebAssembly), `ort.wasm.bundle.min.mjs` con otro nombre para que el WebView lo entregue como JavaScript. | MIT (`LICENCIA-onnxruntime.txt`) |
| `cv.js`, `aruco.js` | js-aruco2 2.0.0: detecta la marca de medida (diccionario ARUCO_MIP_36h12, id 7). | MIT (`LICENCIA-js-aruco2.txt`) |

## Volver a generar el modelo

```
python3 -m venv vis && vis/bin/pip install torch torchvision rfdetr onnx onnxruntime onnxscript
vis/bin/python exportar.py            # rfdetr-seg-nano.onnx (fp32, 123 MB)
vis/bin/python -c "from onnxruntime.quantization import quantize_dynamic,QuantType;quantize_dynamic('output/rfdetr-seg-nano.onnx','seg.onnx',weight_type=QuantType.QUInt8)"
```

Salidas del modelo: `dets` [1,100,4] (cx, cy, w, h normalizados), `labels` [1,100,91] (logits COCO; 21 = vaca) y
`masks` [1,100,78,78] (logits de la silueta). Entrada: `input` [1,3,312,312], RGB normalizado con la media y la
desviación de ImageNet.
