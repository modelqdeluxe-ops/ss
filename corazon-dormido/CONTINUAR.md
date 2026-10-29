> **Lo último está en [`CHECKPOINT-0.8.md`](CHECKPOINT-0.8.md)** (29 oct 2026: rehacer el juego a calidad Core Keeper,
> en marcha y sin publicar). Empieza por ahí; lo de abajo es historia de la 0.6–0.7.

# Checkpoint: arte 3D (en marcha, sin publicar)

Lo pidió el dueño tras la 0.5.0: bosses, personaje y objetos se veían planos y feos (los objetos dibujados de
frente en un mundo 3D), suelos con ruido, avisos de ataque feos; y un juego mucho más grande (mundo enorme por
fase, misiones, puzles). Este checkpoint deja hecha la base del arte nuevo. **No es una versión publicada**: la
0.5.0 sigue siendo la última Release (en `VERSIONES.txt` su línea apunta ya a su commit, `49059afe`, para que un
push no la sustituya; la próxima versión se añade con `HEAD`).

## Hecho

- `js/escultor.js`: modelos 3D con figuras (esfera, elipsoide, caja redondeada, cilindro/cono, cápsula, tubo,
  toro, distancias libres con `ESCU.OP` para restar/cortar sin tocar lo demás) que se hornean a pixel art con la
  cámara del juego (u = x, v = z·cima − y; `cima` 1 para objetos, 0.45 para personajes). Luz fija, oclusión,
  brillo de metales, texturas de material, líneas interiores, limpieza de píxeles sueltos, contorno de color,
  capa `_luz`, normales (atlas `ATLAS_N`) y sombra con forma (`_sombra`). Recorta y guarda en `META_SPR` dónde
  va el dibujo (`dx`, `dz`). `defModelo` quita las capas viejas con el mismo nombre.
- `js/gl.js`: 16 floats por vértice (normal), atlas de normales, luz de lado en el sombreador (la pendiente del
  mapa de luz ilumina el lado que mira a la antorcha), `GL.modelo` (coloca un modelo por el centro de su
  huella), `GL.subirZona`/`olvidarUV` para la zona dinámica del atlas.
- `js/arte.js`: `CONSTRUIR_MODELOS` (los modelos sustituyen a los dibujos viejos del mismo nombre), atlas de
  normales, zona dinámica (`ZONA_DIN`, `escribirZona`).
- `js/personaje.js`: el jugador es un muñeco 3D articulado (3 direcciones; quieto 2, andar 6, golpe 3 cuadros),
  con aspecto (5 pieles, 9 colores de pelo, 5 peinados, 8 ropas: `ASPECTO`, guardado en `J.aspecto`) y las 6
  armaduras puestas de verdad (cobre, estaño, hierro, larva, carmesí, musgo, cada casco con su forma). Se
  vuelve a hornear poco a poco (`Personaje.trabajar`) cuando cambia el equipo. Sprites `pj_<dir>_<anim><n>`.
- `js/modelos.js`: mesas (5), yunques (4), horno, olla, cofres (4), cama, puertas, macetas, caja, vasijas,
  barril, estantería, silla, mesa y valla de selva, banderín, antorchas (normal y mágica), lámpara, faroles,
  lámpara de pie, hoguera, incubadora. `js/modelos2.js`: árboles (3), raíz, arbusto, setas, plantas, cristal,
  huevos, panal, rocas con mena (5), cultivos (6 × 4 etapas), columnas, trono, tablilla, pozo, tienda, ídolos
  (3), altar y Corazón. `dibujarObjeto3d` (principal.js) usa el modelo y su sombra si existen.
- Herramientas: `herramientas/galeria.cjs <carpeta> [escena]` (capturas en el juego de cada zona, jefe, base y
  criaturas, con recorte ampliado) y `herramientas/taller.cjs <png> <regex> [zoom] [luz] [ancho]` (el atlas
  ampliado; `PJ=1` hornea combinaciones de aspecto y armadura).
- `probar-todo.cjs` pasa: sin errores, no falta ningún dibujo, guardar y cargar bien. FPS en el móvil simulado:
  ~48 (antes ~57): mirar qué cuesta (sprites más grandes o las 4 lecturas de luz del sombreador).

## Hecho después del checkpoint

- El dueño rechazó el estilo tierno: **`LORE.md`** es ahora la biblia (historia de Ásor, el Corazón, la Podre,
  los Veladores, grutos, guardianes y titanes; dirección de arte oscura; bestiario y jefes con nombre, título y
  frase). Manda sobre todo lo demás.
- `js/criaturas3d.js`: las 16 criaturas, las mascotas y los mercaderes de perfil con el estilo oscuro (siluetas
  encorvadas, ojos que son puntos de luz, la Podre magenta). Esqueleto `grutoPerfil` para los cinco grutos.
  Nombres nuevos en `CRIATURAS`. Su capa `_luz` se pinta sin luz (ojos y Podre brillan en la oscuridad).
- `js/jefes3d.js`: Murdo, Vhessa, Ozmar, Hiedra y Tamar (cuadros por estado), estatuas y altares como el mismo
  modelo en piedra (`materialEstatua`). Presentación al despertar (`J.presentacion`: nombre, título y frase).

## Lo que falta (en este orden)

3. Suelos nuevos (limpios, sin ruido de puntos) con transiciones orgánicas entre tipos; el suelo venenoso de
   Hiedra en especial. Avisos de ataque nuevos (anillo + relleno que crece; filas como una franja, no óvalos).
4. Ajustar halos (`ALTURA_HALO`) a las alturas nuevas, mano y herramienta del jugador a la marca `mano`,
   pantalla para elegir aspecto al empezar partida, iconos de colocables sacados de los modelos.
5. Caché del atlas (IndexedDB) si el arranque en móvil va lento; medir.
6. Contenido: mundo mucho más grande por fase (guardar por cambios), diario de misiones, NPC con diálogo,
   puzles (placas, palancas, bloques, runas, espejos), mazmorras. Después la fase 3.
7. Probar (probar-todo, galería, simular-jefes), versión 0.6.0 en `VERSIONES.txt` con `HEAD`, Release y artifact.

## 0.7.0: hecha (el mundo vasto y la app de Android)

Todo lo de la lista de la 0.7 está hecho y probado (probar-todo, probar-migracion, la simulación de jefes y las
de puzles, misiones, IA y música en `herramientas/` y en el README). Además, a petición del dueño («es un juego de
Android para la Play Store»; «los mobs me llegan todos»; «no hay música»; «animaciones e items cutres»):

- **App de Android** (`android/`, ver README «Android»): APK y AAB en la Release `corazon-dormido-android`.
  Falta para la Play Store: que el dueño cree la llave de subida y la ponga en los secretos (README), la ficha
  (capturas, textos, clasificación) y la prueba interna.
- **IA**: percepción (vista con línea de visión y luz, ruido, golpes), deambular, rastrear por el olor, perder
  interés y turnos de ataque (`js/criaturas.js`, «percepción, deambular y turnos»). Se mide con una simulación
  de 60 s por región (1–3 encuentros por minuto, como mucho 3 persiguiendo y 2 atacando a la vez).
- **Banda sonora y efectos** (`herramientas/musica`): sintetizados, sin muestras ni derechos de nadie. No los he
  podido escuchar: el dueño tiene que decir qué tema no le gusta y se retoca la partitura en `temas.py`.
- **Animación procedural** (`animarCriatura` y `dibujarCaidos` en `js/principal.js`; `sx/sy/sesgo/rot` en
  `GL.sprite`).

## Lo que falta (por orden)

1. Lo que diga el dueño al probar el APK (música, sensación de combate, animaciones).
2. Play Store: llave de subida, ficha y prueba interna (README «Android»).
3. Más cuadros de animación de verdad (andar de 4–6 cuadros) para las criaturas que más se ven, en los modelos
   (`criaturas3d.js`, `criaturas3d2.js`): ahora tienen 2 de andar más la animación procedural.
4. En la simulación de jefes, una de cada dos peleas contra el Centinela el bot se queda sin hacerle daño
   (300 s, casi toda la vida): mirar si el bot no llega o si el Centinela se queda donde no se le alcanza.
5. Fase 3: el Mar Hundido (DISEÑO.md).
