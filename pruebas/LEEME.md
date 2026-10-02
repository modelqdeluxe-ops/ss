# Pruebas end-to-end (Playwright + Chromium)

Se corren desde una carpeta de trabajo (escriben capturas .png donde se ejecutan). Ver `CONTINUAR.md`, sección 6,
para los servidores locales que necesitan (8111 app, 8790 servidor del equipo, 8112 archivos de visión con CORS).
`equipo_t.js` necesita la variable `DUENO` con el código maestro (no está en el repo).

`vivo_t.js` prueba el peso con cámara en vivo, sin marca, con personas y con ganado: la cámara se reemplaza por
lienzos con escenas (8112 necesita `silueta.onnx`, `silueta_p.onnx`, `seg.onnx`, `cuerpo.onnx`, `cuerpo_v.onnx`, `animal_m.onnx`,
`ort.bundle.js`, `ort-wasm-simd-threaded.wasm` y las
fotos de COCO val2017 `frente.jpg` = 000000223959, `lejos.jpg` = 000000295478, `perfil.jpg` = 000000438907, `lado.jpg` = 000000090062 y
`atras.jpg` = 000000467776). `SIN_WORKER=1` prueba el camino sin Web Worker. Comprueba también los puntos del cuerpo en vivo
(≥ 100 de 133 en la persona, ≥ 8 de 17 en la vaca), el modelo de peso v3 (medidas, `pred`, puntos en las dos fotos) y
que el peso casi no cambie entre tomas de la misma escena (< 6 %).

`enlace_t.js` prueba los teléfonos enlazados: tres páginas (la administración de costado, una cámara por detrás con
Rumentis Beta y una del otro costado con Equipo Beta) conectadas por WebRTC real con los códigos de enlace. Comprueba
que la administración espere a las otras cámaras, que un disparo tome las tres vistas (sin pedir el paso por detrás),
que el peso use el otro costado, que el resultado llegue a las cámaras y que si un teléfono se cae se siga sin él.
Mismos servidores y fotos que `vivo_t.js`.

`aprende_t.js` (solo 8111, sin cámara) arma mediciones en el registro sobre la finca de muestra y comprueba que la app
aprenda sola del peso de entrada de cada animal, de los pesajes a mano y de las ventas; que el error baje (sin
aprender ~14 %, con el modelo ajustado < 5 %, con el factor de cada animal < 2 %); el peso de cada animal con su
historial; el modelo con cinta y la tarjeta "La app aprende sola".
