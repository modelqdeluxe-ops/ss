# Pruebas end-to-end (Playwright + Chromium)

Se corren desde una carpeta de trabajo (escriben capturas .png donde se ejecutan). Ver `CONTINUAR.md`, sección 6,
para los servidores locales que necesitan (8111 app, 8790 servidor del equipo, 8112 archivos de visión con CORS).
`equipo_t.js` necesita la variable `DUENO` con el código maestro (no está en el repo).

`vivo_t.js` prueba el peso con cámara en vivo, sin marca, con personas y con ganado: la cámara se reemplaza por
lienzos con escenas (8112 necesita `silueta.onnx`, `seg.onnx`, `ort.bundle.js`, `ort-wasm-simd-threaded.wasm` y las
fotos de COCO val2017 `frente.jpg` = 000000223959, `lejos.jpg` = 000000295478, `lado.jpg` = 000000090062 y
`atras.jpg` = 000000467776). `SIN_WORKER=1` prueba el camino sin Web Worker.
