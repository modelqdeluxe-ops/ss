# Pruebas end-to-end (Playwright + Chromium)

Se corren desde una carpeta de trabajo (escriben capturas .png donde se ejecutan). Ver `CONTINUAR.md`, sección 6,
para los servidores locales que necesitan (8111 app, 8790 servidor del equipo, 8112 archivos de visión con CORS).
`equipo_t.js` necesita la variable `DUENO` con el código maestro (no está en el repo).

`vivo_t.js` prueba la cámara en vivo con personas: la cámara se reemplaza por un lienzo con escenas (8112 necesita
`frente.jpg` = COCO 000000223959 y `lejos.jpg` = COCO 000000295478, además de los archivos de visión). `SIN_WORKER=1`
prueba el camino sin Web Worker. `beta_t.js` prueba el ganado con una sola foto (`lado.jpg` = COCO 000000090062).
