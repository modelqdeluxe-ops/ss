# Banco de medidas: el camino de la app sobre fotos reales

Sirve para saber si una medida sale sesgada o fuera del rango humano sin tener a las personas: se corre la medición
de la app (modelo preciso + puntos de fotos) sobre fotos de personas de pie y cada medida (en fracción de la
estatura) se compara con la distribución de ANSUR II. Ver `modelo/APRENDIZAJES.md`, sección 4.

1. `python3 sel_pose.py` (necesita `kp_train.json` y `kp_val.json`, las anotaciones de puntos de COCO) → `sel_pose.json`
   con personas de cuerpo completo, de frente (y de perfil, casi no hay en COCO). Bajar sus fotos a `banco/<id>.jpg`
   en la carpeta que sirve 8112 y la lista a `banco/lista.json` (`id`, `bbox`, `tipo`, `W`, `H`).
2. `node banco.js` (8111 y 8112 como en `pruebas/LEEME.md`) → `banco.json`: silueta y puntos de cada foto.
3. `node filtra.js` → `banco_app.json`: solo adultos de pie y derechos, de frente y con los brazos abajo (la postura
   que pide la app).
4. `python3 ansur_pct.py` y `node anal.js [pesocam.js] banco_app.json`: mediana y % fuera de los límites de cada
   medida de frente. `anal.js` usa una toma de perfil fija (`caps.json`: las tomas de una medición de `vivo_t.js`,
   guardadas desde `CamVivo.abrir`), así que solo valen las medidas de frente (bid, cb, wb, hb).

Con la versión anterior de `pesocam.js` como primer argumento se compara antes y después de un cambio.
