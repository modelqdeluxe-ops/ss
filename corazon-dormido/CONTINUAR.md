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

## Lo que falta (en este orden)

1. Criaturas en 3D (16, nombres en `SPRITE_CRIATURA` y `dibujarCriatura3d`: babosas `_0/1/2`, larvas `_0/1`,
   setón `_0..2`, grutos `_0..3`, planta trampa, tentáculo, avispa, escarabajo), mascotas (`topito`,
   `babosita`, `luci`, `mohito` `_0..2`) y mercaderes (`mercader_<tipo>_0..3`). Mirando a la derecha (se voltean).
2. Los 5 jefes en 3D por piezas animadas en el juego (como Core Keeper), y las estatuas y altares de los jefes
   como versiones de piedra de sus modelos. Hiedra y Tamar primero (el dueño mandó captura de Hiedra).
3. Suelos nuevos (limpios, sin ruido de puntos) con transiciones orgánicas entre tipos; el suelo venenoso de
   Hiedra en especial. Avisos de ataque nuevos (anillo + relleno que crece; filas como una franja, no óvalos).
4. Ajustar halos (`ALTURA_HALO`) a las alturas nuevas, mano y herramienta del jugador a la marca `mano`,
   pantalla para elegir aspecto al empezar partida, iconos de colocables sacados de los modelos.
5. Caché del atlas (IndexedDB) si el arranque en móvil va lento; medir.
6. Contenido: mundo mucho más grande por fase (guardar por cambios), diario de misiones, NPC con diálogo,
   puzles (placas, palancas, bloques, runas, espejos), mazmorras. Después la fase 3.
7. Probar (probar-todo, galería, simular-jefes), versión 0.6.0 en `VERSIONES.txt` con `HEAD`, Release y artifact.
