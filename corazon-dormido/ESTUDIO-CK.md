# Estudio de los archivos de Core Keeper (3 nov 2026)

El dueño compró Core Keeper y pasó la carpeta `CoreKeeper_Data` (zip en su Google Drive, 570 MB). Se estudia para
hacer un juego **fiel en cómo está construido y cómo se juega**. **Nada de esos archivos entra en el repo ni en el
juego** (ni dibujos, ni sonidos, ni código): solo medidas y números apuntados aquí; lo dibujamos y programamos
nosotros (ver `ARTE.md`).

## Cómo volver a sacarlo

- Bajar el zip a una carpeta temporal (fuera del repo) y descomprimir. Los paquetes están en
  `CoreKeeper_Data/StreamingAssets/aa/StandaloneWindows64/*.bundle` (Unity, Addressables).
- `pip install UnityPy` y las herramientas de `herramientas/estudio-ck/`:
  - `ck_listar.py <bundle>`: qué hay (tipos y nombres).
  - `ck_exportar.py <bundle> <carpeta> '<regex>'`: lista todas las texturas y guarda las que casen.
  - `ck_entidad.py <bundle> <GameObject...>`: los datos de juego de una entidad (vida, velocidad, IA, ataque…).
  - `ck_hoja.py <salida.png> <zoom> <png...>`: hoja de contacto.
- Paquetes que importan: `defaultlocalgroup_assets_all.bundle` (7047 texturas, 611 animaciones, los datos de todas
  las entidades), `equipmentsprites_assets_playercustomization_*` (el personaje por capas),
  `equipmentsprites_assets_<set>set_*` (armaduras), `tilesets_assets_<bioma>_noseason.bundle` (paredes y suelos),
  `ui_assets_all.bundle` (interfaz). El código está en `CoreKeeper_Data/Managed/Pug.*.dll` (no se ha descompilado:
  instalar el descompilador de .NET pide permiso).

## Lo que se ha visto

### El personaje (no es un esqueleto: cuadro a cuadro y por capas)
- Hoja de **celdas de 26×26** con el personaje de **13×20** dentro. Capas separadas con la misma rejilla: piel,
  camisa, pantalón, ojos, pelo (y pelo con casco), sombra del pelo; las armaduras son otras capas encima. 23 colores
  en total.
- Filas: quieto (abajo, lado, arriba: 1 cuadro cada uno), andar abajo / lado / arriba (**6 cuadros**), y dos filas de
  acciones de 9 cuadros (3 por dirección). El lado se dibuja una vez y se voltea.
- Tiempos (clips de animación): correr 6 cuadros en 1,5 s a velocidad base (el juego lo acelera con la velocidad),
  quieto 1 cuadro por segundo, **preparar el golpe 0,29 s** (4 cuadros a 14 fps) y **golpe 0,17 s**, pinchar 0,15 s,
  disparar 0,17 s, golpe recibido 0,1 s, morir 4 s a 12 fps. La herramienta es un dibujo aparte que el clip mueve.

### Criaturas
- También **cuadro a cuadro**, con hoja por animación y dirección: `<criatura>_<anim>[_side|_up]` (idle, move,
  attack, rangedAttack, sleep, taunt, point…). Caveling: quieto 5 cuadros de 17×18, andar 6 cuadros de ~17×19, ataque
  6 cuadros de 33×29 con **el arco del golpe dibujado en el cuadro**. Muchas hojas son grises y se colorean con un
  mapa de color (variantes con la misma hoja) y llevan una capa `_emissive` (lo que brilla).
- Babas: `slimeBlob_<color>_idle/move` 160×13 o 230×18 (unos 8 cuadros), `_jump` aparte.

### Números de las criaturas (datos de cada entidad)
| | Caveling | Baba |
|---|---|---|
| Vida | 510 (se cura 50 % cada 5 s, 5 s después de dejar de pelear) | 105 |
| Velocidad | 50 | 10 (salta: 100) |
| Deambular | anda 2–4 casillas (máx. 3 s), para 0,5–1 s | anda 1–3 casillas, para 0,5–1 s |
| Te ve / persigue | radio 8 casillas; se queda a 0,5–2 casillas; esquiva de lado a 1,5 | ataca a 3 casillas |
| Ataque | prepara 0,35 s, golpe 0,35 s (el daño a los 0,05 s), espera 1–3 s, alcance 1,5, radio 0,6, se lanza hacia delante (fuerza 15) | prepara 0,55 s, 0,45 s en el aire, daño 19 |
| Daño | 102 cuerpo a cuerpo; 165 a las paredes | 19 |
| Duerme | cada 20–120 s, 20–60 s; se despierta si estás a 2 casillas | — |
| Gestos | bostezo/habla al estar quieto (cada 5–20 s); señala/burla al empezar a pelear | — |

### Objetos
- **Una casilla** y dibujos pequeños: mesa de trabajo 16×16 (de lado 16×20), cofre 16×16 (de lado 16×19), horno
  16×16 con el fuego en otra capa animada (4 cuadros), yunque 16×16, antorcha de unos 4×10 con la llama aparte (4
  cuadros). **Cuatro orientaciones** por objeto (`_down`, `_up`, `_side`, `_side2`).
- Antorcha en la mano: brillo de color (1; 0,93; 0,8) e intensidad 6.

### Paredes y suelos (cada bioma, una plantilla)
- `<bioma>_tileset` es la plantilla de la que se generan las piezas: **la tapa de la pared es negra** con un marco
  del material (2–3 px) por dentro del borde; la cara de la pared es una textura del material (escamas de tierra);
  el suelo es claro y tranquilo y **cada zona de suelo tiene un borde oscuro** alrededor; las piezas de unión salen de
  una cruz (autotile). Aparte: decoraciones sueltas (guijarros, hierba), agua, raíces, el suelo cavado y regado.
- `tileset_<Bioma>_wall` (256×320) son las piezas ya generadas: celdas de 16×16.

### Interfaz
- Ranura de objeto de 32×32 con borde; letra Euclid Circular B y Noto (no se pueden usar: tienen licencia).

## Lo que cambia para nuestro juego (propuesta, por orden de lo que más se nota)
1. Personaje cuadro a cuadro como el suyo (celdas de 26×26, 6 cuadros al andar, 3 direcciones, capas para ropa y
   armadura, herramienta aparte), dibujado por nosotros: sustituye al esqueleto de la 0.8.
2. Criaturas igual: hojas cuadro a cuadro por animación y dirección, el arco del golpe en el cuadro.
3. IA y tiempos con sus números (tabla de arriba): deambular, distancias, preparar y golpear, esperas, sueño,
   curarse fuera de pelea.
4. Paredes con la tapa negra y el marco, suelos con borde oscuro por zona y autotile.
5. Objetos de una casilla con cuatro orientaciones y el fuego en una capa animada.

## Hecho en la 0.9.0
- Personaje cuadro a cuadro (`js/personaje-px.js`, `PJ_PX`): sustituye al esqueleto (`fuentePJ()` en personaje.js).
- Ataque cuerpo a cuerpo de las criaturas con sus tiempos y sin daño por contacto (`js/criaturas.js`:
  `cuerpoACuerpo`, `SIN_CONTACTO`), babas que saltan encima, paseo y curación con sus números.
- Babas cuadro a cuadro (`criaturas-fichas.js`, `baba` y `BABA_CUADROS`); el sapo sigue con el esqueleto (`babaEsq`).
- Vistas de frente y de espaldas (`D.vistas`, `CRI.hornear`: `<tipo>_ab_*` y `<tipo>_ar_*`) en los grutos, con la
  máscara de frente y de espaldas; el juego elige la vista por hacia dónde anda (`principal.js`, `cuadroCriatura`).
- Paredes: tapa negra con marco de 3 px (`arte.js`, `muroArriba`; `gl.js`, tapa 0,8).

## Hecho en la 0.9.1 (todo a mano, sin esqueleto ni modelos 3D)
- Grutos cuadro a cuadro por piezas a mano (`js/criaturas-px.js`), con los tiempos de cada caveling.
- Gusanos de anillos en tres vistas (`js/criaturas-px2.js`, `gusano`): larva (quieta 1, anda 4 a 8 fps, ataca 4 a
  10 fps con pausas 0, 0, 1, 0; cabe en 16×16), larva de la Podre, gusano escupidor (como el «bigLarva»: anda 6 a
  10 fps, ataca 6 a 10 fps con pausas 0, 0, 2, 0, 0, 0) y sanguijuela.
- Gruto mole (`criaturas-px2.js`, `mole`; como el «cavelingBrute», cuadros de unos 44×40): quieto 5 a 10 fps con
  pausas 4, 0, 0, 4, 0; anda 10 a 10 fps; ataca 9 a 10 fps con pausas 0, 0, 2, 0, 0, 1, 0, 0, 0 (tres alzando y seis
  del golpe; en el juego el golpe sigue mientras «descansa», `principal.js`). Se pinta con masas (elipses y
  cápsulas con luz arriba a la izquierda, surco y contorno) y la cabeza de gruto a mano.
- Objetos del mundo a mano (`js/objetos-px.js`, `MUNDO`): casi todos; faltan la tienda, el trono, el altar del
  cielo, la fuente seca, el Archivero, Muda, la raíz y el árbol muerto.

## Hecho en la 0.10.0 (rework de lo que se ve en toda la pantalla)
- **Suelos** (`js/suelos-ck.js`), medidos de `tileset_Dirt_ground`: 5 colores por bioma (base, trama, brillo, borde,
  borde oscuro); la trama es un ajedrezado del tono claro que solo sale en trazos cortos en diagonal; un brillo suelto;
  una grieta del color del borde de vez en cuando. Donde el suelo da a una pared, un borde oscuro de 2 px con
  goterones de 0 a 3 px hacia dentro y alguna piedrecita clara (`pared_<arte>_<lado>`, `gl.js`). La sombra de las
  esquinas se suaviza (el borde hace de sombra).
- **Luz**: el color de su antorcha (1; 0,93; 0,8) en la antorcha en la mano y la puesta; caída suave (1 − d²)^1,5 en
  vez de un foco; sin escalones (la luz es continua, como la suya).
- **Interfaz** (`js/ui.js`), medida de `32x32_menu_border`, `32x32_itemui_border`, `health_bar_expandable`: ventanas
  negras translúcidas con borde lavanda de 1 px (#a1aedd arriba e izquierda, #677fae abajo y derecha) y gemas azules
  en dos esquinas; ranuras azul noche (#142b5c al 80 %) con muesca a media altura; barras con marco de tierra.
- **Armas** (`js/armas-px.js`), de `sword_copper` y `pickaxe_scarlet` (hojas de 3×3 celdas de 40×40): el arma en la
  mano no se gira libremente; se lleva en 8 posiciones fijas (cada 45°) hechas a nivel de píxel (las de su diagonal
  girando 90° sin pérdida; las otras con un giro de 45° a 8 aumentos con suavizado, como RotSprite) y en lo rápido
  del golpe se pone un abanico del color del arma (como su «smear»). Iconos de espadas, picos, mazas, azadas, lanzas,
  cañas y arcos redibujados a mano por material.

## Hecho en la 0.10.1
- Iconos a mano (`js/iconos-px.js`): lingotes, minerales, cascos, petos, botas y bloques, cada familia con el mismo
  dibujo y la paleta de su material.
- Criaturas a mano en tres vistas (`js/criaturas-px2.js`): la Lápida (escarabajo; boca arriba al morir), el sapo
  hueco (salta como las babas), el Velado (túnica que flota, se deshace al morir), el Farolero (con el farol de
  cebo), el Necrófago (a cuatro patas), el Guardián de piedra (duerme como estatua y despierta), la Fauce y el Brazo
  de la Podre (salen del suelo, se curvan y muerden o golpean).

## Hecho en la 0.10.2
- Personaje nuevo: cabezas de 13×11 con la construcción de las de Core Keeper (de sus capas `Miner_skin`,
  `Miner_eyes`, `Miner_hair*`: cara ancha, ojos de 2×3 —raya oscura, brillo e iris—, colorete, el pelo con volumen y
  flequillo) en `js/mano.js` (`CABEZA`), cuello claro de camisa en vez de la bufanda roja (`js/personaje-px.js`) y
  aspecto por defecto nuevo (`ASPECTO_INICIAL` en `js/personaje.js`; quien tenía el viejo sin tocar pasa al nuevo).

## Lo que queda (por orden)
0. **El dueño dice que los bichos hechos con masas (elipses y cápsulas generadas, `criaturas-px2.js`) se ven mal
   hechos.** Rehacerlos dibujados píxel a píxel como los grutos de `criaturas-px.js`: piezas de rejillas de letras con
   el contorno puesto a mano y colocadas en cada cuadro con desplazamientos enteros (nada generado). Orden: larvas,
   babas, mole, voladores, Lápida, sapo, Velado, Farolero, Necrófago, Guardián, Fauce, Brazo de la Podre.
1. Lo que aún es de esqueleto: el setón y el velador caído, las mascotas, los mercaderes y los siete jefes (por
   piezas a mano, con masas como el mole; `criaturas-px2.js` tiene `masas`, `contorno2`, `tumbar` y `acabarH`).
2. Iconos que faltan por rehacer a mano: comida, semillas, peces, materiales sueltos, muebles de la casa.
3. Mecánicas: comparar con Core Keeper lo que pasa al morir, el hambre y las habilidades (sus números están en los
   datos de cada entidad; las fórmulas del jugador están en `Pug.Other.dll`, que pide permiso para descompilar).
4. Objetos con cuatro orientaciones y los que faltan a mano (tienda, trono, altar del cielo, fuente seca, Archivero,
   Muda, raíz, árbol muerto).
5. Herramientas para revisar (en la carpeta temporal de cada sesión): una hoja de cuadros de una criatura
   (`CRI.DEF[tipo].manual(anim, f, vista)` en `herramientas/taller.html`) y una partida con criaturas andando y
   atacando alrededor del jugador.
