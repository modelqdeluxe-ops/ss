# Créditos y licencias de lo que no es nuestro

Todo el arte del juego (personaje, criaturas, jefes, mundo, objetos e interfaz) está dibujado para el juego. De lo de
fuera solo se usa lo que pone aquí, con su licencia comprobada (el archivo de licencia que venía con cada paquete).

## Movimiento (animaciones)

Del 3D **solo se usa el movimiento de los huesos** (dónde está cada hueso en cada cuadro); los modelos no se usan.
Se sacan con `herramientas/horno3d/extraer.cjs` y quedan en `js/anim-*.js`.

| Archivo | De dónde | Autor | Licencia |
|---|---|---|---|
| `js/anim-humano.js` | KayKit Character Pack: Adventurers 1.0 | Kay Lousberg (www.kaylousberg.com) | CC0 1.0 |
| `js/anim-esqueleto.js` | KayKit Character Pack: Skeletons 1.0 | Kay Lousberg (www.kaylousberg.com) | CC0 1.0 |
| `js/anim-baba.js`, `js/anim-murcielago.js`, `js/anim-dragon.js` | Animated Monster Pack (Slime, Bat, Dragon) | Quaternius (quaternius.com) | CC0 1.0 |

## Letra

| Archivo | De dónde | Autor | Licencia |
|---|---|---|---|
| `js/glifos8.js` | Pixel Operator 8 (https://www.dafont.com/pixel-operator.font) | Jayvee Enaguas | CC0 1.0 |
| `js/fuente.js` (la letra pequeña de 3×5) | fuente de pxl (`.claude/skills/pxl-pixel-art`) | — | MIT |

Pixel Operator se pasa a la tabla de glifos con `herramientas/fuente/glifos.py` (se dibuja a 32 px y se toma el centro
de cada bloque de 4×4: los píxeles salen exactos).

## Música y sonido

La música y los sonidos de la 0.7 son nuestros (sintetizados en `herramientas/musica` y `js/sonido.js`). Lo grabado con
licencia libre se apunta aquí cuando entre (CHECKPOINT-0.8 §4.6).
