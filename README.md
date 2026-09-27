# Rumentis

App Android de engorde de ganado (paquete `hn.hato.ganadero`) con el ayudante Rumi.

## Estructura

- `app/` — la app descompilada con apktool. Aquí se trabaja.
  - `app/assets/index.html` — toda la interfaz y la lógica de Rumi (HTML + JS).
  - `app/assets/rumi_red.bin` — red de intenciones de Rumi (fastText, 27.9M de parámetros).
  - `app/assets/rumi/` — **modelo de comprensión de Rumi**: multilingual-e5-base (278M parámetros,
    licencia MIT) ajustado con miles de preguntas de engorde, en GGUF partido en trozos de 90 MB,
    más `referencias.bin` (vectores de temas y preguntas de ejemplo) y `modelo.json`.
  - `app/assets/rumi_entiende.js` — decide qué contestar: tema de la Guía, "¿te refieres a…?",
    "no es de mi tema" o la respuesta de las reglas. Nunca genera texto.
  - `app/assets/rumi_motor.html` — corre el modelo con wllama en un iframe `https://rumentis.local`.
  - `app/assets/rumi_rag.js`, `app/assets/rumi_saber.json` — la Guía y la búsqueda por palabras de respaldo.
  - `app/assets/wllama/` — llama.cpp en WebAssembly (y versión compatible para WebView viejos).
  - `app/assets/licencias.txt` — licencias del modelo e5, wllama y llama.cpp (MIT).
  - `app/smali/` — el código Android (WebView, dictado por voz, guardar archivos, RAM del teléfono).
  - `app/apktool.yml` — versión (`versionCode`, `versionName`) y SDK.
- `modelo/` — el conocimiento y el entrenamiento de Rumi.
  - `saber_extra.json`, `saber/*.json` — temas de la Guía (se suman a los que ya trae la app).
  - `datos/preguntas_*.json` — preguntas de ejemplo por tema; `fuera.json` — preguntas de otros temas;
    `prueba_dificil.json` — examen aparte para medir.
  - `construir_saber.js` → arma `app/assets/rumi_saber.json`.
  - `embeddings/entrenar.py` → ajusta el modelo; `calibrar.py` → mide y elige umbrales;
    `exportar.py` → GGUF y referencias en `app/assets/rumi/`.
  - `ilustraciones/` → toros y paisajes de cada pantalla (`escenas.py` los escribe en index.html).
- `scripts/build.sh` — APK con todo adentro, para probar: `dist/Rumentis.apk`.
- `scripts/build_aab.sh` — AAB para Google Play: `dist/Rumentis.aab`.
- `scripts/probar_rumi.js` — prueba Rumi en Chromium como si fuera el WebView.

## Cómo contesta Rumi

1. Las reglas contestan primero: cálculos, dosis que la app sabe calcular, formularios y tus lotes.
2. Si no entienden la pregunta o es de otro tema, el modelo de comprensión la compara con los
   temas de la Guía y con miles de preguntas de ejemplo:
   - tema claro → contesta con el texto revisado de ese tema;
   - dudoso → "¿Te refieres a…?" con opciones;
   - de otro tema → "Eso no es de mi tema" y sugiere preguntas de la app.
3. Dosis de medicinas que la app no calcula → respuesta fija (etiqueta y veterinario).
4. Sin RAM suficiente (menos de ~3 GB) usa la búsqueda por palabras con las mismas reglas.

Para que Rumi sepa más: agrega temas en `modelo/saber/` y preguntas en `modelo/datos/`, y vuelve a
exportar (`construir_saber.js` y `embeddings/exportar.py`); reentrenar solo mejora la comprensión.

## Entrenar de nuevo

```sh
pip install sentence-transformers datasets
node modelo/construir_saber.js
python3 modelo/embeddings/entrenar.py <multilingual-e5-base> ~/work/e5-rumi
python3 modelo/embeddings/calibrar.py ~/work/e5-rumi
LLAMA_CPP=<llama.cpp> python3 modelo/embeddings/exportar.py ~/work/e5-rumi
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
entregue solo a teléfonos con suficiente RAM, crea en Play Console una configuración de niveles
de dispositivo (Device tier config) con el nivel 1 = RAM ≥ 3 GB. Los demás reciben el nivel 0 y
Rumi usa la búsqueda por palabras.
