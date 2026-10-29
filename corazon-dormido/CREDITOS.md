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

## Música (CC BY 4.0: hay que citar al autor)

Toda de **Kevin MacLeod** (incompetech.com), con licencia **Creative Commons Attribution 4.0**
(https://creativecommons.org/licenses/by/4.0/). Comprobada en https://incompetech.com/music/royalty-free/music.html
(«Licensed under Creative Commons: By Attribution 4.0 License»). El juego lo cita en Créditos (título y pausa).

| Tema del juego | Pieza | Dónde suena |
|---|---|---|
| `titulo` | Heart of Nowhere | Título y Santuario del Corazón |
| `interior` | Immersed | Raíces, Arcilla, Ruinas |
| `selva` | Ascending the Vale | Selva Esmeralda y Pradera |
| `cienaga` | Mirage | Ciénaga de la Podre |
| `ciudad` | Gregorian Chant | Ciudad Velada |
| `cripta` | Oppressive Gloom | Cripta y Moho |
| `jefe` | Darkling | Peleas con los jefes |
| `jefe2` | Five Armies | Grul y el Centinela |

## Efectos de sonido (CC0)

De OpenGameArt, todos CC0 (comprobado en la página de cada uno):

| Paquete | Autor | Para |
|---|---|---|
| RPG Sound Pack (opengameart.org/content/rpg-sound-pack) | artisticdude | tajos, menú, error, burbuja, muertes |
| 80 CC0 creature SFX | rubberduck | alertas, rugido, golpe recibido, comer, muertes |
| 100 CC0 SFX | rubberduck | golpes, metal, recoger, crear, colocar, nivel, victoria, agua |
| 75 CC0 breaking/falling/hit SFX | rubberduck | picar, romper, excavar, temblor, golpes |
| Fantozzi's Footsteps (Grass/Sand & Stone) | qubodup (grabados por Fantozzi) | pasos en piedra y en hierba |
| Heartbeat sounds | bart | el latido del Corazón |
| Water splash and sand footsteps | peludo | pasos en el agua |

## Cómo entra en la app

No van al repo: `herramientas/audio/bajar.py` los baja al compilar (workflow de Android), comprueba la huella SHA-256
de cada archivo, los recorta, iguala el volumen y los pasa a OGG (música estéreo ~96 kbps; efectos mono). Si algo no
se puede bajar, se queda lo compuesto (`herramientas/musica`, sintetizado por nosotros). En total, unos 18 MB.
