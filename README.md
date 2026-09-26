# Rumentis

App Android de engorde de ganado (paquete `hn.hato.ganadero`) con el ayudante Rumi.

## Estructura

- `app/` — la app descompilada con apktool. Aquí se trabaja.
  - `app/assets/index.html` — toda la interfaz y la lógica de Rumi (HTML + JS).
  - `app/assets/rumi_red.bin` — red de intenciones de Rumi (fastText, 27.9M de parámetros).
  - `app/assets/rumi/` — **Rumi avanzado**: modelo de lenguaje de 494M de parámetros
    (Qwen2.5-0.5B-Instruct ajustado para Rumi), en GGUF partido en trozos de 90 MB.
  - `app/assets/rumi_llm.js` — carga el modelo con wllama y lo conecta al chat de Rumi.
  - `app/assets/rumi_rag.js`, `app/assets/rumi_saber.json` — la Guía que el modelo consulta.
  - `app/assets/wllama/` — llama.cpp en WebAssembly (y versión compatible para WebView viejos).
  - `app/assets/licencias.txt` — licencias de Qwen (Apache 2.0), wllama y llama.cpp (MIT).
  - `app/smali/` — el código Android (WebView, dictado por voz, guardar archivos, RAM del teléfono).
  - `app/apktool.yml` — versión (`versionCode`, `versionName`) y SDK.
- `modelo/` — cómo se entrena Rumi avanzado.
  - `saber_extra.json` — temas nuevos de la Guía (se suman a los que ya trae la app).
  - `datos/` — preguntas y respuestas de entrenamiento.
  - `construir_saber.js` → arma `app/assets/rumi_saber.json`.
  - `armar_datos.js` → arma `datos/train.jsonl` y `datos/eval.jsonl` con la misma búsqueda que usa la app.
  - `entrenar.py` → ajuste LoRA en CPU. `exportar.sh` → GGUF cuantizado en `app/assets/rumi/`.
- `scripts/build.sh` — APK con todo adentro, para probar: `dist/Rumentis.apk`.
- `scripts/build_aab.sh` — AAB para Google Play: `dist/Rumentis.aab`.
- `scripts/probar_rumi.js` — prueba Rumi avanzado en Chromium como si fuera el WebView.

## Cómo funciona Rumi avanzado

1. Las reglas de Rumi contestan primero (cálculos, dosis, formularios, datos de los lotes).
2. Si no entienden la pregunta o está fuera de su alcance, contesta el modelo, que corre en el
   teléfono sin internet. Antes busca en la Guía los temas relacionados y se los pasa como contexto.
3. Solo se activa en teléfonos con unos 5 GB de RAM o más (`RAM_MIN_MB` en `rumi_llm.js`).
4. Cada respuesta del modelo dice que es generada por IA y se puede reportar
   (política de contenido generado por IA de Google Play). Pon el correo que recibe los reportes
   en `REPORTE_CORREO` dentro de `rumi_llm.js`.

## Entrenar de nuevo

```sh
pip install torch transformers peft safetensors gguf
node modelo/construir_saber.js && node modelo/armar_datos.js
python3 modelo/entrenar.py --base <carpeta de Qwen2.5-0.5B-Instruct> --salida ~/work/rumi-hf
LLAMA_CPP=<llama.cpp compilado> modelo/exportar.sh ~/work/rumi-hf q8_0
```

## Compilar

```sh
scripts/build.sh       # dist/Rumentis.apk
scripts/build_aab.sh   # dist/Rumentis.aab
```

Necesita Java; descarga apktool, uber-apk-signer, bundletool y las herramientas de Android la
primera vez. Para firmar con la llave de la app define `KEYSTORE` (o `KEYSTORE_B64`),
`KEYSTORE_PASS`, `KEY_ALIAS` (`hato`) y `KEY_PASS`.

### Google Play: nivel de dispositivo

El modelo va en el asset pack `rumi_modelo`, en la carpeta `rumi#tier_1`. Para que Play lo
entregue solo a teléfonos con 5 GB de RAM o más, crea en Play Console una configuración de
niveles de dispositivo (Device tier config) con el nivel 1 = RAM ≥ 5 GB. Los demás teléfonos
reciben el nivel 0, sin modelo, y Rumi funciona con sus reglas como siempre.
