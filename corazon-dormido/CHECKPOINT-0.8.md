# Checkpoint 0.8 (29 oct 2026): rehacer el juego «a calidad Core Keeper». EN MARCHA, SIN PUBLICAR

Lee esto entero antes de tocar nada. Rama: `claude/magical-hopper-cwbjnh` (lleva `main` + la rama del juego
`claude/quirky-tesla-jg3j9c` fusionadas). La última versión publicada sigue siendo la **0.7.0** (APK en la Release
`corazon-dormido`).

## 0. Aviso importante para publicar (o para NO publicar)

**Cualquier push que toque `corazon-dormido/**` lanza `.github/workflows/corazon-dormido-android.yml`, que compila el
APK y SUSTITUYE el de las Releases `corazon-dormido-android` y `corazon-dormido` (lo que descarga el dueño).** Mientras
el trabajo no esté listo y probado, todos los commits llevan **`[skip ci]`** en la primera línea (GitHub no lanza
ningún workflow). Para publicar la 0.8.0: añade su línea en `VERSIONES.txt` con `HEAD` (ya está `VERSION_JUEGO =
'0.8.0'` en `js/datos.js`) y haz el push sin `[skip ci]`.

## 1. Lo que pide el dueño (sus 11 puntos, literal en lo esencial)

Está muy descontento: «está en pañales, cutre, sin amor». No quiere gastar en un artista: **lo tienes que hacer tú**,
en serio. Responde siempre en español, sin subagentes (lo prohibió), y mide las capturas con PIL antes de decir que
algo está bien.

1. Las **animaciones de los personajes** son cutres: busca animaciones de código abierto para mejorar el motor.
2. Las **animaciones de criaturas y jefes** también: se mueven horrible.
3. Las **texturas 3D y las adaptadas a 2D** son horribles (van con la animación cutre).
4. Los **sprites**, todos cutres y mal diseñados, **demasiado pequeños**.
5. Las **GUI** mal hechas, **textos ilegibles**, algunas **mesas de trabajo no tienen GUI propia**.
6. **Sonidos y música** horribles, «como un juego de 1990»: quiere calidad de 2026.
7. Las **criaturas te ven a kilómetros** y si una te ataca **vienen todas**.
8. El **radio de luz del personaje** es demasiado pequeño.
9. No hay **segunda mano**: quiere ir con antorcha en una mano y espada o pico en la otra.
10. Ve **bordes dentados** y **líneas que se deforman**: el motor no renderiza bien el 3D ni lo suaviza.
11. Las **armas e items no se dibujan bien en la mano** del personaje.
12. **Redibuja todos los conceptos y assets con los lineamientos nuevos.** Todo tiene que complementarse.

Decisiones ya tomadas con él (no las vuelvas a discutir):
- **No se copian los sprites de Core Keeper** (son de Pugstorm/Fireshine: vendiendo el juego, la Play Store lo
  retira y pueden demandar). Se copia su **construcción**: medidas, proporciones, paleta única, contorno de color.
- **De los modelos 3D CC0 solo se usa la ANIMACIÓN (el movimiento de los huesos), NUNCA el modelo.** «Nuestro juego es
  pixel art». Los personajes y criaturas son dibujos nuestros por piezas, movidos por ese esqueleto.
- Lo dibuja la IA (no se encarga a un artista).
- El arte que hice dibujando letras a mano (`js/criaturas-mano.js`) le pareció «una mierda». El esqueleto 2D con
  animaciones reales (punto 3) es el camino que estaba en marcha cuando paró.

## 2. Lo que se estudió (para no repetirlo)

**Core Keeper medido píxel a píxel** (wiki oficial `https://corekeeper.atma.gg/en/...`, las imágenes están a ×4):
- Personaje **13×20 px**, cabeza **13×11** (más de media altura), ojos de **2×2** con un píxel blanco de brillo.
- **Sin contorno negro**: el borde es el tono más oscuro de cada material (sel-out); en las babas el borde de arriba
  es claro y el de abajo oscuro.
- 6–24 colores por sprite, **3–4 tonos por material**, saturados, luz arriba a la izquierda.
- **Paleta única de todo el juego**: en 377 sprites, 64 colores cubren el 63 % de los píxeles.
- Criaturas: babas 14×13, cavelings 13×18 (con armas hasta 22×27), brutos 43×40. **Jefes enormes: 80–145 px**
  (Hive Mother 124×102, Ghorm 128×147, Azeos 143×111). Los nuestros medían 20–50.
- Pantallas del juego: la interfaz y la luz ya se parecen; lo que falla es el arte de personajes y criaturas.
- Herramientas: `herramientas/estudio-ck/medir.py` (escala, tamaño, colores, contorno), `ascii.py` (el sprite como
  letras), `hoja.py` (hoja de contacto). Las imágenes de Core Keeper NO van al repo (son suyas): se bajan para
  estudiar con `curl` de `https://corekeeper.atma.gg/en/Enemies` (busca `src="/english/images/..."`).

**Animaciones CC0 encontradas** (licencias comprobadas, uso comercial libre, crédito opcional):
- **KayKit (Kay Lousberg)**: se clonan con git (github.com da 403 por web, pero `git clone` funciona):
  `git clone --depth 1 https://github.com/KayKit-Game-Assets/KayKit-Character-Pack-Adventures-1.0` (Knight, Barbarian,
  Mage, Rogue, Rogue_Hooded: **76 animaciones** cada uno), `KayKit-Character-Pack-Skeletons-1.0` (**95 animaciones**,
  incluidas las de esqueleto: despertar del suelo, resucitar…) y `KayKit-Dungeon-Remastered-1.0` (objetos). Todos
  comparten el mismo esqueleto de 41 huesos, con **`handslot.l` / `handslot.r`** (las dos manos).
  Animaciones: andar (A/B/C, atrás), correr, 1H y 2H (chop, slice, stab, spin), bloquear, esquivar (4), golpe
  recibido (A/B), morir (A/B), saltar, lanzar, usar objeto, recoger, interactuar, conjurar, sentarse, tumbarse…
- **Quaternius** (CC0): «Animated Monster Pack» (Bat, Dragon, Skeleton, Slime; FBX) y «Cute Animated Monsters» (21:
  Bat, Bee, Crab, Cthulhu, Cyclops, Demon, Ghost, Mushroom, Skull, Tree, Yeti…; FBX), bajados de OpenGameArt:
  `https://opengameart.org/sites/default/files/Animated%20Monster%20Pack%20by%20%40Quaternius.zip` y
  `.../cute_animated_monsters_-_aug_2020.zip`. «Ultimate Monsters» (50 monstruos) está en Google Drive y daba «cuota
  excedida»: `herramientas/horno3d/drive.py <id> <carpeta>` (id de la carpeta: `18m4KpzpEzhC9wl7jzr6dUc0N8Jozr79C`);
  reintenta otro día. También en OpenGameArt (CC0): `universal-animation-library`, `animated-animales-low-poly`.
  Para criaturas: se usa el movimiento de sus huesos (FBXLoader de three.js lo lee igual que el glTF).

## 3. Lo hecho en este checkpoint (todo en la rama, sin publicar)

**Funciona y vale la pena conservar** (probado: `probar-todo.cjs` sin errores, mismos FPS que la 0.7):
- `js/gl.js`: luz en **12 escalones con media trama** (la oscuridad ya no se ve como rejilla); la luz fuerte se
  **comprime** (el personaje ya no se quema junto al fuego); viñeta más suave.
- `js/principal.js`: **estela del arma** al golpear (arco que se apaga); título nuevo: el Corazón a la derecha,
  título con latido y menú a la izquierda (`js/ui.js` `dibujarTitulo`); cámara del título corrida.
- `js/entidades.js`: parón y temblor en **críticos** (0,09 s) y **muertes** (0,075 s; jefes 0,2 s); números de daño
  que **saltan** y frenan, el crítico a doble tamaño.
- `js/escultor.js` (horneado de modelos SDF, lo que queda de él): sin manchas de 1–2 px (`cumulo`), **luz de borde**,
  contraste por material (`contraste`, `realce`, `detalle: true` para no limpiar detalles).
- `js/modelos2.js`: el Corazón rehecho (sigue sin convencer: era un «bulto»; a rehacer con el método nuevo).

**En marcha (el camino nuevo, el que hay que seguir):**
- `herramientas/horno3d/`: `npm install` dentro (three.js 0.160, MIT; `node_modules` no va al repo).
  - `extraer.cjs <modelo.glb|fbx> <salida.json> [clips] [fps]`: abre `extraer.html` en Chromium (servidor local
    `servidor.cjs`) y saca **solo el movimiento**: posición en el mundo de cada hueso (y el «arriba» de cada hueso) en
    cada cuadro de cada clip.
  - `datos.cjs <extraído.json> <salida.js> <NOMBRE> <clips>`: lo deja en un `.js` del juego (milésimas, enteros).
- `js/anim-humano.js` (65 KB): 18 clips de KayKit (Idle, Walking_A, Running_A, 1H_Melee_Attack_Chop,
  1H_Melee_Attack_Slice_Horizontal, 1H_Melee_Attack_Stab, 2H_Melee_Attack_Chop, Hit_A, Death_A, Use_Item, PickUp, Throw,
  Interact, 2H_Ranged_Shoot, Dodge_Forward, Spellcast_Shoot, Block, Cheer), 20 huesos.
- **`js/esqueleto.js`**: el esqueleto 2D.
  - `ESQ.proyectar` pasa cada hueso a la cámara del juego (elevación 28°, 13 px por unidad, el personaje mide ~26 px)
    en tres direcciones (`ab` de frente, `ar` de espaldas, `de` a la derecha; `iz` es `de` volteado).
  - `ESQ.capsula` rasteriza **piernas, brazos y torso como cápsulas de píxeles** en cada postura, con luz arriba a la
    izquierda y sombra abajo a la derecha (nunca se gira un dibujo: no hay dientes).
  - `ESQ.terminar` pone **líneas entre piezas** (lo de detrás se oscurece donde lo tapa lo de delante) y el **contorno
    de fuera con el tono más oscuro del vecino** (sin negro, como Core Keeper).
  - `PJ_ESQ.cuadro(dir, anim, f, aspecto, equipo)` → `{ color: Lienzo 48×46, manos: { D, I: { x, y, ang, delante } },
    pie: [24, 42] }`. Retargeting: los brazos se acercan al cuerpo (×0,72) y las piernas (×0,85), porque el modelo de
    la animación es más ancho que nuestro dibujo.
  - Animaciones del juego en `PJ_ESQ.ANIMS`: q quieto, a andar, c correr, g golpe a una mano (pico, hacha), h tajo
    (espada), p golpe a dos manos (maza), r golpe recibido, m morir, u usar o comer, l lanzar (pescar), d disparar
    (arco), e esquivar.
  - La cabeza sale de `PJ_MANO.cabeza(dir, aspecto, equipo)` (`js/mano.js`): dibujo a mano de 13×11 con peinado
    (melena, corto, erizo, coleta, moño) y casco; solo se gira a 90° al caer.
  - Paleta por papeles en `PALETA_PJ` (piel, pelo, túnica, pantalón, botas, cinto, bufanda, mochila; las armaduras
    cambian túnica y botas por los tonos del metal).
  - Probado en `herramientas/vista-mano.cjs` (ver §5): andar de 8 cuadros con zancada y brazos de verdad, el golpe con
    carga y descarga y la muerte cayendo. **Aún NO está conectado al juego** (el juego usa todavía `PJ_MANO.cuadro`,
    estático de 2 a 6 cuadros, en `Personaje.trabajar` de `js/personaje.js`).
- `js/mano.js`: el personaje dibujado a mano con medidas de Core Keeper (13×20) con andar y golpe hechos a mano
  (es lo que se ve ahora en el juego; lo sustituye el esqueleto).
- `js/criaturas-mano.js`: «paleta de Ásor» (`ASOR`, rampas por material) y criaturas dibujadas con letras (babas,
  grutos, gusanos, Portador, Sapo hueco, Fauce, Brazo de la Podre, Aguijonera, Moscarda, Lápida). Están cargadas y
  sustituyen a las horneadas, pero **al dueño no le gustan**: se rehacen con el esqueleto (§4.3).

## 4. Lo que falta, en este orden (cada paso, probado y medido antes del siguiente)

### 4.1 Conectar el esqueleto del personaje al juego (puntos 1, 9, 11)
- `js/personaje.js` → `Personaje.trabajar`: hornear con `PJ_ESQ.cuadro` todas las animaciones de `PJ_ESQ.ANIMS` en
  `ab`, `ar`, `de` (y `iz` volteando, con `voltearNormales`). Son ~73 cuadros por dirección: **recorta cada cuadro a
  lo que tiene dibujado** y guarda el desplazamiento para no mover los pies (META_SPR: `dx` = (centro del recorte −
  24)/16 casillas y la altura de los pies). `ZONA_DIN.h` (arte.js) es 240: súbela si no cabe. Quita los `pjB_`
  (destello blanco): usa la opción `blanco` de `GL.sprite`, como las criaturas.
- `js/principal.js` → `nombreJugador`: elegir la animación según lo que hace (pico/hacha/azada → g, espada → h, maza
  → p, arco → d, comer o colocar → u, pescar → l, herido → r unos 0,3 s, muerto → m quedándose en el último cuadro,
  andar → a, correr → c si va deprisa, quieto → q). Velocidad de cada animación: la duración del clip (`fps` en
  `anim-humano.js`); el andar, atado a la distancia recorrida para que los pies no patinen (`p.anim`).
- Las manos: `dibujarEnMano` y `dibujarHerramienta3d` (principal.js) deben poner el objeto en `manos.D` (y la
  antorcha en `manos.I`) del cuadro actual, girado `ang` (los iconos de herramienta están en diagonal: resta 45°),
  delante o detrás del cuerpo según `delante`. La estela del golpe sale del movimiento de esa mano.
- **Segunda mano** (punto 9): un hueco de equipo nuevo (`J.equipo.mano2` o similar) para antorcha/escudo; con
  antorcha ahí, luz como la antorcha en la mano (`fuentesDinamicas`) y se dibuja en `manos.I`. Panel de equipo
  (`ui.js`) con el hueco. Guardado y migración de partidas (`guardado.js`, `probar-migracion.cjs`).

### 4.2 Render nítido (punto 10)
- `js/gl.js` dibuja a ~2 px de pantalla por píxel del arte con cámara en **perspectiva** (FOV 13°) y el navegador
  amplía: los píxeles salen de tamaños distintos y lo girado tiene dientes. Arreglo: dibujar a la resolución real del
  aparato (`ajustar`), cámara **ortográfica** (o casi), y en el sombreador de sprites un **muestreo de pixel art suave**
  (filtrado de «píxeles gordos»: `uv` = centro del texel + `clamp(frac/fwidth)`; buscar «pixel art antialiasing
  shader» / «sharp bilinear»). Mide con capturas ampliadas que no haya dientes ni líneas deformadas.

### 4.3 Criaturas y jefes con esqueleto (puntos 2, 3, 4, 12)
- Extrae el movimiento de los modelos CC0 (§2) con `extraer.cjs` (los FBX de Quaternius también) y haz un esqueleto
  2D por familia: humanoides (grutos, Velador caído, Guardián de piedra, Necrófago, Vidente…) con el de KayKit
  (Skeletons trae despertar, resucitar, andar de esqueleto, taunts); babas con el del Slime; voladores con Bat/Bee;
  Velado con Ghost; Portador con Mushroom; jefes con el que encaje y **a tamaño de jefe de Core Keeper (80–140 px)**.
- Las piezas se dibujan con la paleta de Ásor (`ASOR` en criaturas-mano.js) y con las reglas de Core Keeper (§2).
  Fichas de cada criatura y jefe: `LORE.md` §5 y §6.
- Nombres de los cuadros que pide el juego: `principal.js` `dibujarCriatura3d` y `fauna.js` `SPRITE_CRIATURA`
  (`<tipo>_0/1` andar, `_2` preparar, `_3` cargar, `_luz` lo que brilla). Con más cuadros, cambia esas funciones
  para elegir por tiempo y estado (golpe recibido, morir, aparecer).

### 4.4 IA y luz (puntos 7 y 8)
- `js/criaturas.js` («percepción, deambular y turnos»): bajar el alcance de la vista y del oído, y que una criatura
  que te ataca **solo avise a las que están cerca** (radio corto), no a todo el mapa. Medir con
  `herramientas/simular-ia.cjs` (1–3 encuentros por minuto; como mucho 3 persiguiendo y 2 atacando).
- Luz del jugador: `fuentesDinamicas` en `principal.js` (sin antorcha: `r: 6, k: 0.7`; con antorcha `r: 8`). Más
  grande y cálida.

### 4.5 Interfaz (punto 5)
- Fuente legible (pixel font libre con acentos y ñ: p. ej. «Pixel Operator» CC0 o «Silver» CC-BY 4.0) y el lienzo de
  la interfaz a la resolución real del aparato (ahora es de ~435×206 y se amplía: por eso se lee mal).
- Paneles nuevos y una **GUI propia para cada mesa/estación** (mesas, yunques, horno, olla, incubadora…): busca en
  `ui.js` qué paneles hay y qué estaciones abren el panel genérico de crear.

### 4.6 Audio (punto 6)
- Sustituir la música y los efectos sintetizados (`herramientas/musica`, `js/musica.js`, `js/sonido.js`) por
  grabaciones con licencia libre: música orquestal/ambiental CC-BY 4.0 (Scott Buckley, Kevin MacLeod / incompetech,
  Alexander Nakarada) y efectos CC0 (Kenney, OpenGameArt CC0, Freesound CC0). **Comprueba la licencia de cada
  archivo** y apúntala en un `CREDITOS.md` (y en el juego, en créditos). Ojo: no metas MB y MB en el repo sin
  pensarlo (el workflow de Android empaqueta la música).

### 4.7 Después
- Mundo (suelos y paredes), objetos, el Corazón y los iconos con las mismas reglas. `ARTE.md` (biblia de arte con
  todo lo de §2) no está escrita todavía.
- Probar todo (`probar-todo.cjs`, `probar-migracion.cjs`, `simular-jefes.cjs`, `galeria.cjs`), versión 0.8.0 en
  `VERSIONES.txt` y publicar (§0).

## 5. Herramientas para ver el arte (sin abrir el juego)

- `node herramientas/taller.cjs <salida.png> '<regex de nombres>' [zoom]`: los dibujos del atlas ampliados.
- `node herramientas/vista-mano.cjs <salida.png> <zoom> "<expresión JS que devuelve [[nombre, Lienzo], ...]>"`:
  cualquier dibujo hecho en el momento; `recortar(L)` recorta al dibujo. Ejemplo (el personaje andando de perfil):
  `node herramientas/vista-mano.cjs /tmp/a.png 8 "Array.from({length:8},(_, f)=>['de a'+f, recortar(PJ_ESQ.cuadro('de','a',f,ASPECTO_INICIAL,null).color)])"`
- `node herramientas/galeria.cjs <carpeta> [escena]`: capturas del juego en cada zona, jefe, base y criaturas.
- `python3 herramientas/comparar.py antes.png despues.png salida.png`: antes arriba y después abajo.
