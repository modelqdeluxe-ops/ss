# TF Crates (las crates físicas, dentro de TF Client desde la 1.3.99)

Las crates de **Fantastic Crates** (mod de Pewez, el dueño: «todo jar que diga fantastic es mío») van dentro de TF Client
desde la **1.3.99**. El comando es **`/tf crates`** (antes `/fscrate`). El jar viejo (`Fantastic Crates 8.jar`) hay que
quitarlo del servidor y de los clientes: si sigue instalado, TF Crates no se registra y lo dice en el log
(`[TF Crates] Fantastic Crates sigue instalado`).

## 1. Cómo se metió (y cómo repetirlo si el dueño manda otra versión del jar)

El dueño no tiene el código, solo el jar. Se descompiló y se tradujo:

1. **Descompilar** con Vineflower 1.10.1 (lo que falle, con CFR 0.152: así salieron `MPEG4DecodingContext.readHeaders`
   y el `static {}` de `MPEG4Consts`, que Vineflower dejaba en bytecode).
2. **Nombres**: los jars de Forge en producción llevan las clases con nombre oficial pero los miembros en SRG
   (`m_12345_`, `f_12345_`). Se traducen con `joined.tsrg` (mcp_config 1.20.1-20230612.114412; obf → srg) cruzado con
   el `client.txt` de Mojang (oficial → obf), por clase obf, nombre obf y descriptor.
3. **Pasarlo al mod** (`port_crates.py`, en el scratchpad de la sesión 1.3.99; lo que hace): `com.fscrates` →
   `net.tierrasfantasticas.tfclient.crates` (las librerías que llevaba dentro —jcodec, JAAD, JLayer, para el vídeo y la
   música de la pantalla de antes de abrir— en `crates.libs.*`), el mixin a `tfclient.mixin.crates`,
   `@EventBusSubscriber(modid = "tfclient")` (con «fscrates» no se registraban), `/fscrate` → `/tf crates`
   (`FSCrateCommand.command()`, enganchado en `TFCommands`), y los recursos a `assets/fscrates/`.
4. **Arreglar lo que el descompilador deja mal** (variables repetidas en los `switch`, genéricos de listas, un `switch`
   de `short` con `case 65534`, variables sin declarar): compila con GitHub Actions; las librerías (Java puro) se pueden
   comprobar sueltas con `javac --release 17` (no es compilar el mod).

**El espacio de nombres sigue siendo `fscrates`** (bloque `fscrates:crate`, objetos `crate`, `fantastic_key`,
`unique_key`, `editor_wand`, el BE, las texturas y modelos, los datos guardados `fscrates_*`, el NBT `fscrates` de llaves
y crates y la carpeta `config/fscrates/cajas`): así las crates puestas en los mundos, las llaves de los jugadores y las
cajas en JSON siguen valiendo. Arranque: `crates/FSCrates.init` (desde `TFClient`).

## 2. La cinemática: el Santuario de las Hadas (1.3.99)

El dueño mandó **una imagen** de lo que quiere («quiero exactamente esto, colores, iluminación, shaders»): un santuario
en ruinas cerrado, de piedra con musgo y cubierto de hiedra, con vigas de roble oscuro a los lados (flores de esporas
rosas y faroles con cadena), abierto arriba en el medio por donde entra un sol blanco cálido con rayos; al fondo una
cascada alta que baja por una escalera de agua; a los lados dos columnas de piedra de las que cae su cascada (por debajo
de un dintel); delante un estanque turquesa claro, con el fondo de baldosas y la red de líneas de luz; en medio el altar
de piedra con musgo, azalea en flor y dos amatistas, con la crate encima. Antes fue un pantano de hadas de noche (el
dueño lo rechazó con esta imagen). Las quejas que llevaron hasta aquí: «¿esa es tu producto final?», «la cuadrícula
sobre la cascada», «haz más turquesa y brillante el agua… no abuses del bloom a que se vea borrosa la crate… un haz de
luz bien definido y grandecito», y con la imagen: «no veo idéntica a mi foto», «esa cascada cortada», «el color para
nada coincide», «quiero precisión en los colores».

**Cómo se ajustó a la imagen (repetible):** la imagen está en `docs/crates/imagen-del-dueno.webp` y es la referencia de
`tools/crates/santuario_render.py` en el tick 0 y de la captura del juego del tick 12 (`00_crates_crate2_00_llegada.jpg`
de la prueba). Las dos se miden con **`python3 -I tools/crates/comparar.py <imagen> --lado salida.png`** (colores por
tipo, saturación, luz, contraste, nitidez y zonas; ejemplos en `docs/crates/comparacion-*.jpg`). El juego sale más
saturado y más oscuro que la vista previa: manda el juego.
Las medidas de la escena salen de tratar la foto como una cámara (un nenúfar mide un bloque): cámara a 3,7 de alto,
10,5 bloques delante del altar, 36° de campo vertical; cascadas de los lados a ~4,5 del centro; escalera a 13 bloques y
cascada alta a 17. Los colores se compararon **por tipo de cosa** (agua turquesa, hojas, piedra, faroles y sol, rosa,
sombras: el color medio y el brillo del 90 %) entre la foto y la vista previa, y se cambió la luz y los shaders hasta
que coincidieran (con un guion de comparación en el scratchpad de la sesión; vuelve a hacerlo si se cambia algo).
Resultado al dejarlo (foto → vista previa, tick 0, con la oclusión de las hojas como en el juego): imagen entera
(56,105,97) → (50,102,94); agua (27,168,182) → (35,179,202); hojas (43,80,39) → (38,84,35); faroles (188,144,63) →
(213,160,40); rosa (181,74,148) → (199,96,169); sombras (17,24,18) y 11,5 % de la imagen → (18,24,17) y 11,8 %; luz
media 0,321 → 0,325; saturación 0,615 → 0,640; contraste local 0,081 → 0,085; nitidez 0,095 → 0,090. La piedra queda
algo más clara (76,81,80) → (94,105,98).

Lo que se encontró por el camino (y no hay que volver a romper):
- **Todo borroso en el juego**: `renderScene` borraba la profundidad de la textura de la escena antes del shader (el
  `RenderSystem.clear` del `finally`): la profundidad de campo creía que todo estaba lejos. Ahora se borra la de la
  pantalla, no la de la escena.
- **La cuadrícula sobre la cascada** era de la vista previa: moderngl no tiene `ctx.depth_mask` (está en el framebuffer),
  así que lo que suma luz escribía profundidad y las bandas coplanares del haz se pisaban. En el juego `GLOW_ADD` no
  escribe profundidad.
- **La viñeta** del shader oscurecía las esquinas un 25 % (el agua de abajo no pasaba de ~214): ahora es suave.
- **La curva de tono** no llegaba al blanco (1 salía 0,79): ahora está normalizada.
- **El velo**: los rayos del shader arrastraban el foco del sol por toda la imagen; ahora solo hacen rayos lo muy claro
  y se apagan pronto.
- **Los faroles** alumbraban 6,75 bloques (todo amarillo): ahora un charco de luz de ~4.
- **La luz según la cara**: el haz y la luz del hueco vienen de arriba y de atrás; lo que mira a la cámara recibe poca
  (el altar y los pilares, en sombra por delante, como en la imagen). `SanctuaryScene.light(x, y, z, nx, ny, nz, …)`.
- **Plano y borroso** (el dueño: «no veo mejoría en saturación, contraste»): con los colores medios iguales, el contraste
  local era un 30 % menor y la nitidez un 40 % menor que en su imagen. Se quitó la profundidad de campo del fondo y el
  shader final tiene «claridad» (la imagen menos su versión desenfocada).
- **En el juego más oscuro que en la vista previa**: el juego oscurece las esquinas también con las hojas (oclusión
  ambiental: lo que choca como un bloque entero); la vista previa ahora lo hace igual (`Mesher.occ`).
- **Discos y burbujas en el juego**: los halos de los faroles (1,3 bloques) y las hadas (orbes de un bloque) salían como
  discos con aro (la «claridad» les dibuja el borde). Ahora son pequeños; los rayos en abanico, con el borde suave.
- **Tablones grises en el juego**: los rayos en abanico y los que bajan del hueco, hechos con bandas de geometría,
  en el juego salían con el borde duro (en la vista previa, suaves; no se sabe aún por qué). Ahora los rayos los hace el
  shader final (`crate_post.fsh`, `godRays`). **Y salen del hueco de las copas** (el punto `sunspot`, arriba en el
  centro, como en la imagen), no del sol: el sol queda 2,4 alturas de imagen por encima de la pantalla y allí los rayos
  ya no llegaban a verse (`CrateCinematicScreen` y la vista previa proyectan `sunspot`).
- **Cascadas cuadriculadas** (el dueño: «no quiero texturas cuadriculadas ni quebradas»): la cascada era una rejilla
  regular de celdas con el borde blanco y destellos cuadrados (rectángulos blancos). Ahora son escamas irregulares
  (Voronoi) que bajan, con el borde fino y roto en destellos, vetas y destellos redondos, con antialiasing (`fwidth`)
  para que de lejos no parpadee; en el estanque, sin las rayitas blancas del agua del juego y con manchas de luz suaves.
- **El disco detrás de los faroles (y de las hadas)**, medido en la prueba `a877326`: no era el halo (su textura es suave)
  sino la «claridad» del shader final (la imagen menos su versión desenfocada), que dibuja un aro oscuro alrededor de lo
  que brilla suave. Ahora lo que oscurece se comprime cuando es mucho (`crate_post.fsh`): el aro desaparece y el
  detalle fino sigue igual. Los rayos llegan hasta la escalera de agua, no hasta el altar (como en la imagen).
- **La cascada, la misma agua que el estanque y que cae** (el dueño: «texturas quebradas», «incongruente con el agua del
  pozo», «la cascada tiene movimiento hacia abajo»): las dos usan la misma receta (`waterLook` en `crate_water.fsh`: el
  turquesa, la textura del agua del juego —la quieta en el estanque, la que corre en las cascadas, con sus hilos
  verticales, bajando—, sus líneas claras y los píxeles que centellean, alargados hacia abajo en la cascada) y vetas de
  luz que bajan; nada de celdas, rejillas ni remolinos. De lejos la textura se funde (no parpadea).
- **La cascada de los lados flotaba**: el chorro es más estrecho que su bloque y debajo no se dibujaba la superficie del
  estanque (franja oscura alrededor del pie). Ahora sí (`under` en `buildWaterFx` y en la vista previa) y alrededor del pie
  de cada chorro hay espuma (la orilla del shader, `splash`).
- **Los números, iguales o por encima de la imagen en el juego** (el dueño): el juego sale con la luz media ×0,90, la alta
  ×0,94, el contraste ×0,97, la nitidez ×1,05 y la saturación ×1,075 respecto a la vista previa (medido en la prueba
  `24692c0`); el shader final lleva ganancia 1,12, claridad 0,56, curva 0,60 y los brillos más fuertes tirando a blanco.
- **El agua, limpia y continua** (el dueño, con capturas: «otra vez esa mierda de resquebrajados»): fuera las escamas
  (celdas de Voronoi: se leen como grietas), la textura del agua del juego (rayitas) y las cáusticas (red de líneas). La
  cascada: hilos finos verticales que bajan, destellos pequeños y redondos y espuma abajo; el estanque: transparente (se
  ven los bloques del fondo), manchas de luz suaves y los reflejos de las cascadas en destellos redondos; los dos con el
  mismo turquesa (`WATER_DEEP`/`WATER_LIGHT` en `crate_water.fsh`). La cascada, opaca al 90 % (más transparente se veían
  las juntas del ladrillo de detrás como rayas). **No volver a meter celdas, rejillas ni líneas en el agua.**
- **El agua como en la imagen, otra vez** (lo que se probó antes y se quitó) (el dueño: «no coinciden la cascada, la textura del agua ni su transparencia»):
  el estanque es transparente (se ven los bloques de verdad del fondo, su mosaico de píxeles, teñidos de turquesa; sin
  pepinos de mar, que se veían como bultos), con la textura del agua, manchas de luz, el reflejo de las cascadas en
  columnas de destellos (`cascadeSeen`) y las escamas (`scales`: celdas redondeadas de borde fino roto en destellos). La
  cascada, translúcida, con las mismas escamas alargadas hacia abajo y bajando, vetas y espuma blanca que brilla abajo.
- **El premio, limpio**: el halo y los rayos estaban en su centro (el premio gira: la mitad quedaba detrás y se teñía del
  color de la rareza); ahora van 0,8 por detrás y más suaves. El mensaje de abajo, sobrio (sin caja, rayas, negrita ni
  estrellas).
- **Bloques como en la imagen**: el altar (centro de ladrillo agrietado, lados de hojas de azalea lisas y piedra con
  musgo, pétalos rosas; sin la azalea en flor de lunares morados), las cascadas de los lados caen desde lo alto de su
  columna (sin dintel) con una columna de hojas y piedra pegada por fuera, el borde del estanque es un muro de un bloque,
  más hojas y menos tiras de enredadera.
- **Los nenúfares negros**: el verde de Minecraft por la luz de la escena daba (6,41,37); los alumbra el agua turquesa
  (`LILY_LIFT`/`LILY_K` en la vista previa = `SanctuaryScene`), (15,74,39); en la imagen ~(25,85,45).
- **Bandas de piedra entre los escalones**: la cara del agua que cae baja 1/8 hasta la superficie del agua de delante
  y va un pelo hacia fuera (en el plano de la piedra perdía con ella en la profundidad).

Al abrir una crate, quien la abre ve una **escena 3D en vivo** (18 s) que empieza con **el encuadre de la imagen del
dueño** (de frente, centrado) y se acerca a la crate para la llave. La escena (`santuario.py`): el estanque (x −6..6) con
el fondo de linternas de mar y prismarina, el altar, la escalera de agua de 3 escalones y la cascada alta de 5 de ancho
bajo el hueco de las copas (z −17..−5), las columnas de los lados con su cascada (x ±4..5, z −8) que sale de debajo de un
dintel de piedra con musgo, las terrazas de los lados con columnas, flores azules y rosas y faroles en su poste, las
vigas de roble oscuro a 7 de alto con flores de esporas (que brillan un poco: son de hadas) y cortinas de enredadera
junto a las paredes, hiedra y bloques de hojas de azalea por toda la piedra de los lados (`lush`, `ivy`, `curtains`),
13 faroles con cadena (puestos donde están en la imagen, medidos con su cámara) y la pared del fondo. Vuelan **hadas** y
chispas. No se ve horizonte ni cielo de lado (`cielo_visible.py`: 0 %); por el hueco, el cielo de día.

La imagen va **con shaders propios, como un paquete de shaders** (`ScenePost` y `shaders/core/crate_*`):
- **el agua** (`crate_water`, desde `SanctuaryScene.drawWaterFx`): el estanque es claro y deja ver **el fondo de
  baldosas** (dibujado en el shader, con la refracción de las ondas), con la **red de líneas de luz** turquesa claro
  encima, el dibujo del agua del juego (las texturas `water_still`/`water_flow` del atlas de bloques, animadas por el
  juego, leídas con `textureLod` nivel 0: con `fract` y mipmaps saldría una raya en cada borde de bloque), el reflejo, el
  brillo del haz, destellos y espuma en las orillas; las cascadas, con el agua que corre del juego bien marcada, vetas,
  destellos que bajan y espuma abajo (mucha en las cascadas altas, poca en los saltos de la escalera: el canal r del
  vértice lleva la altura de la columna); las que salen de debajo de una piedra van más estrechas y llegan hasta ella;
- el cielo de día por el hueco (`crate_sky`);
- el resplandor (`crate_bloom`, umbral 0,78: florecen el agua clara, los faroles y el sol; la crate, no) y la imagen
  entera desenfocada (para la profundidad de campo);
- la imagen final (`crate_post`): **profundidad de campo suave** (el fondo se sigue viendo, como en la imagen),
  **resplandor**, **rayos** que salen del sol, la curva de tono normalizada, **colores** (sombras verde azuladas, nada
  amarillento en lo oscuro, luces cálidas solo en lo amarillo, saturación ×1,4, contraste) y una viñeta suave.
- `CinematicFx.sunShafts`: la columna del haz con el borde marcado (`band` con `u0..u1`), más fuerte arriba que
  abajo, el **foco del sol** (`sunspot`, arriba en el centro, crema cálido) con **9 rayos en abanico** y 7 rayos que bajan
  del hueco sobre la escalera; el polvo, las chispas, la bruma y la espuma de las cascadas.

Si los shaders no cargan, se dibuja sin ellos (directa, con el agua del juego).

| tick (20/s) | qué pasa |
|---|---|
| 0–20 | el encuadre de la imagen del dueño, que se acerca; el título de la crate y «✦ Santuario de las Hadas ✦» |
| 20–66 | **la llave** (la de la crate: Fantastic Key o la única) sale de un remolino de luz, vuela, se alinea y entra |
| 70–80 | **gira**; el anillo de runas se enciende sobre el agua y los chorros brillan del color de la rareza |
| 84–102 | la crate tiembla, la luz se escapa por la tapa |
| 102 | **¡se abre!**: la llave se deshace en chispas, la tapa salta, pilar de luz, onda, ráfaga de pétalos, más resplandor |
| 110–252 | **la ruleta**: los premios suben y giran en un anillo encima de la crate; frena en el premio |
| 258 | **el premio**: destello, rayos, delante y en el centro; «¡Has recibido! … ★ rareza ★» |
| 360 | fin (`CrateCinematicTiming.TOTAL_TICKS`); el premio llega al inventario a los 362 (CrateOpeningService) |

Los demás jugadores ven **la crate del mundo** a los mismos tiempos (`CrateRenderer.renderSceneExtras`,
`CrateBlockEntity.SCENE_*`): la llave que llega, entra y gira, el temblor, la tapa que se abre del todo, el pilar de luz
y el premio que sube y gira encima hasta que se cierra. La de siempre (`CrateCinematicClassic`) dura lo mismo.

**La música**: `tfclient:crates.musica` (la nuestra, sintetizada, a los tiempos de la escena). La canción que eligió
el dueño («La petite fille de la mer», 20 oct 2026: sus primeros 20 s, con entrada y salida suaves y volumen
normalizado) va en un resource pack aparte que sustituye `assets/tfclient/sounds/crates/musica.ogg` (se le dio hecho,
`TF-Santuario-musica.zip`); no se sube al repo ni a las Releases, que son públicos. El ambiente (`ambiente.ogg`) es de
día: las cascadas, el viento en las hojas, gotas, pájaros lejos y campanitas de las hadas. La música y el ambiente se
apagan despacio al final (`CrateCinematicScreen.Track`), no se cortan.

### Piezas

- **`tools/crates/santuario.py`** → `assets/fscrates/escena/santuario.bin` (formato v2): la escena (paleta de estados
  de bloque de Minecraft con sus marcas —se mece, agua, brilla— + bloques), los puntos (`crate`, `pool`, `sun` (la
  dirección del haz), `fall`/`fall_z` (la cascada), `pond` (el remanso), `spring0..1` (las fuentes de las peñas),
  `flowers0..2`), **la ruta de la
  cámara** (`CAMERA`: tick, ojo, mira, campo de visión; Catmull-Rom) y **las luces mágicas** (cada bloque que brilla,
  con su color y su tamaño: el halo y la luz de alrededor). Cambias la escena o la cámara aquí y lo vuelves a ejecutar
  (`python3 -I tools/crates/santuario.py`).
- **`tools/crates/santuario_render.py`**: **la vista previa con las texturas de verdad** (los modelos y texturas de
  Minecraft 1.20.1: baja el jar del cliente de Mojang la primera vez a `~/.cache/tfclient/`; no se sube al repo), la
  misma luz que `SanctuaryScene.light`, el agua, la niebla, la crate, el haz, los halos, las luciérnagas, y luego los
  **mismos shaders del mod**: `xvfb-run -a python3 -I tools/crates/santuario_render.py <carpeta> [ticks]` (unos 25 s).
  Es lo que hay que mirar antes de gastar una prueba en el juego (no dibuja la llave ni los premios ni hace oclusión
  ambiental). Si cambias colores, luz o niebla en el mod, cámbialos también arriba de este script.
- **`tools/crates/santuario_preview.py`**: la vista previa rápida (bloques de colores), desde la misma cámara:
  `xvfb-run -a python3 -I tools/crates/santuario_preview.py <carpeta> [ticks]`.
- **`crates/client/scene/SanctuaryScene`**: carga el `.bin`, construye los bloques una vez con el renderizador de
  Minecraft (oclusión ambiental, caras tapadas, colores de bioma) sobre su mundo propio (`SceneLevel`) en búferes de la
  tarjeta gráfica, con **la luz de la escena en el color** (`light(x, y, z, nx, ny, nz)`: la sombra verde azulada
  `AMB`, el haz `BEAM_C` y la luz del hueco `SKY_C` según hacia dónde mira la cara, el sol de arriba `CROWN_C`, el
  turquesa del estanque `POOL_C` y un charco de luz de cada luz mágica cercana, por celdas; los mismos números que
  `make_light` de la vista previa); los bloques que brillan van sin sombra; el agua va con su shader
  (`buildWaterFx`/`drawWaterFx`; si no carga, la dibuja el juego con `Liquid`); las plantas cerca de la crate se
  mecen (`drawPlants`).
- **`crates/client/scene/ScenePost`**: el shader (texturas de la escena y del resplandor, el cielo, el resplandor y la
  imagen final). Se registran en `RegisterShadersEvent` (`ScenePost.Shaders`).
- **`crates/client/scene/CinematicFx`**: lo que se mueve (el haz de luz y su polvo, halos de las luces, hadas,
  luciérnagas, niebla, la espuma y las ondas de la cascada y de las fuentes, pétalos, runas, pilar de luz, ondas de la
  apertura y del premio, destellos, rayos). Sin estado: cada partícula sale de su índice y del tiempo. El haz y el
  pilar bajan mientras gira la ruleta y en el premio (el dueño: que no tapen los premios).
- **`crates/client/scene/SceneRenderTypes`**: sus capas (todas a la imagen que esté puesta: la del shader o la
  principal). La llave y los premios se dibujan con el «modo panorámico» del GameRenderer puesto mientras tanto: así
  con gráficos «Fabulosos» no se van a la capa de objetos del mundo, que en una pantalla nadie compone.
- **`crates/client/screen/CrateCinematicScreen`**: la cinemática (tiempos, cámara, la crate y su tapa, la llave, la
  ruleta en anillo, el premio, los textos, los sonidos). El mundo no se dibuja mientras está abierta
  (`mixin/crates/LevelRendererMixin`). Si el bosque no carga o falla al dibujarse, sale **la de siempre**
  (`CrateCinematicClassic`, la de Fantastic Crates) y lo dice en el log; si falla solo el shader, sigue sin él.
- **`tools/crates/sonidos_crates.py`** → `assets/tfclient/sounds/crates/*.ogg` (eventos `tfclient:crates.*` en
  `sounds.json`): el ambiente (la cascada, el viento en las hojas, grillos, ranas, campanitas de las hadas, un búho),
  **la música a los tiempos de la escena** (Re mayor: llegada, llave, giro, temblor, apertura, ruleta que se frena,
  hueco para el premio, final) y los efectos (llave aparece, entra, gira; runas; temblor; apertura; tic; parada;
  premio1..3 según la rareza). Si cambias los tiempos de la cinemática, cambia los de arriba del script (`KEY_APPEAR`…)
  y vuelve a generarlo.

### Probarla sin el dueño

`[prueba-jefes=crates]` en el commit (`BossTest.cratesStep`, ~1 min de juego; la prueba entera tarda menos de 10 min): comprueba que estén los registros y
`/tf crates`, pone una crate épica de estilo clásico y otra mítica de estilo cinemático y las abre como un jugador
(`CrateOpeningService.open`); el cliente saca 11 capturas por apertura a los tiempos de la cinemática
(`BossTestClient.crateShots`, a los ticks 12, 40, 58, 76, 92, 107, 140, 196, 252, 272 y 318: llegada, haz, llave,
gira, tiembla, abre, anillo, ruleta, para, premio, final) y el
informe trae los fotogramas (en Actions va con Mesa, sin tarjeta: ~11-13 por segundo; en un PC normal, muchos más).

## 3. Lo que falta / ideas

- Si el dueño quiere otra escena para otras crates (por rareza o por estilo), `santuario.py` puede escribir más `.bin`
  y `SanctuaryScene.get()` elegir por `cfg.styleId` o `cfg.rarity`.
- Las llaves planas (las tres `bg_key*`, dibujo en 2D) se orientan a ojo (`keyFlat`); las 3D van a lo largo de z con la
  punta hacia −z, que es como entran en la cerradura.
