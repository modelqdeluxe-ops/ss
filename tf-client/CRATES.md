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

## 2. La cinemática: el Bosque de las Hadas (1.3.99)

El dueño: «quiero un rework súper bonito, épico… una escena tipo de un santuario en la naturaleza con fuente de agua,
tipo ruinas, con un haz del sol sobre el lugar, una escena completa en vivo, y ahí puesta la crate correctamente, la
apertura, la metida de llave…». Tras ver la primera versión (un santuario en un claro de día): «que aparezca
directamente ya cerca de la crate… mejora el shader… el resplandor más bello, las fuentes de agua mejor diseñadas, la
estructura mejor diseñada, el fondo celestial… esas ruinas más bonitas… algo bello, como un bosque de hadas».

Al abrir una crate, quien la abre ve una **escena 3D en vivo** (18 s) que **empieza ya junto a la crate**: un bosque de
hadas al atardecer. En medio, un estanque de agua turquesa con el fondo de linternas de mar, el borde de calcita y
cuarzo y, sobre un pedestal, **su crate de verdad** (el modelo y la tapa de su estilo); detrás, un montículo musgoso con
**una cascada** que cae a un remanso y el arroyo que baja al estanque; dos fuentes-copa que rebosan y dos faroles; un
arco élfico con lianas y amatistas; una columnata en ruinas alrededor; árboles gigantes con copas de cerezo y
hongos-lámpara, setas que brillan, azaleas, liquen y bayas luminosas; el cielo del atardecer (oro en el horizonte, rosa,
violeta, las primeras estrellas) y la bruma lila. Vuelan **hadas** de colores con estela, luciérnagas, pétalos y
mariposas; la niebla baja se mueve sobre el agua.

La imagen pasa por **un shader propio** (`ScenePost` y `shaders/core/crate_*`): el cielo (`crate_sky`), el resplandor
(`crate_bloom`: se baja a un cuarto, se queda lo que brilla y se desenfoca dos veces) y la imagen final (`crate_post`:
resplandor, brillo soñador, **rayos de sol** que salen del sol aunque esté fuera de cuadro, colores del atardecer,
curva suave y viñeta). Si los shaders no cargan, se dibuja como antes (directa, con el cielo en 2D).

| tick (20/s) | qué pasa |
|---|---|
| 0–20 | ya junto a la crate; el título de la crate y «✦ Bosque de las Hadas ✦» |
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
el dueño va en un resource pack aparte que sustituye `assets/tfclient/sounds/crates/musica.ogg` (se le dio hecho); no
se sube al repo ni a las Releases, que son públicos. La música y el ambiente se
apagan despacio al final (`CrateCinematicScreen.Track`), no se cortan.

### Piezas

- **`tools/crates/santuario.py`** → `assets/fscrates/escena/santuario.bin` (formato v2): la escena (paleta de estados
  de bloque de Minecraft con sus marcas —se mece, agua, brilla— + bloques), los puntos (`crate`, `pool`, `sun`,
  `spout0..1`, `fall`/`fall_z` (la cascada), `pond` (el remanso), `cherry0..3`, `flowers0..2`), **la ruta de la
  cámara** (`CAMERA`: tick, ojo, mira, campo de visión; Catmull-Rom) y **las luces mágicas** (cada bloque que brilla,
  con su color y su tamaño: el halo y la luz de alrededor). Cambias la escena o la cámara aquí y lo vuelves a ejecutar
  (`python3 -I tools/crates/santuario.py`).
- **`tools/crates/santuario_preview.py`**: vista previa sin el juego (bloques de colores, la crate en dorado y la
  ruleta en magenta), desde la misma cámara: `xvfb-run -a python3 -I tools/crates/santuario_preview.py <carpeta> [ticks]`.
- **`crates/client/scene/SanctuaryScene`**: carga el `.bin`, construye los bloques una vez con el renderizador de
  Minecraft (oclusión ambiental, caras tapadas, colores de bioma) sobre su mundo propio (`SceneLevel`) en búferes de la
  tarjeta gráfica, con **la luz de la escena en el color** (`light`: el atardecer frío en la sombra, el haz cálido
  sobre la crate, manchas de sol, las copas teñidas, y la luz de color de las luces mágicas cercanas, por celdas); los
  bloques que brillan van sin sombra; el agua la dibuja ella; las plantas cerca de la crate se mecen (`drawPlants`).
- **`crates/client/scene/ScenePost`**: el shader (texturas de la escena y del resplandor, el cielo, el resplandor y la
  imagen final). Se registran en `RegisterShadersEvent` (`ScenePost.Shaders`).
- **`crates/client/scene/CinematicFx`**: lo que se mueve (haces de sol, polvo, halos de las luces, hadas, luciérnagas,
  niebla, la cascada y su espuma, chorros y el agua que rebosa de las copas con la textura del agua que corre, gotas,
  ondas, pétalos, mariposas, runas, pilar de luz, ondas de la apertura y del premio, destellos, rayos). Sin estado:
  cada partícula sale de su índice y del tiempo.
- **`crates/client/scene/SceneRenderTypes`**: sus capas (todas a la imagen que esté puesta: la del shader o la
  principal). La llave y los premios se dibujan con el «modo panorámico» del GameRenderer puesto mientras tanto: así
  con gráficos «Fabulosos» no se van a la capa de objetos del mundo, que en una pantalla nadie compone.
- **`crates/client/screen/CrateCinematicScreen`**: la cinemática (tiempos, cámara, la crate y su tapa, la llave, la
  ruleta en anillo, el premio, los textos, los sonidos). El mundo no se dibuja mientras está abierta
  (`mixin/crates/LevelRendererMixin`). Si el bosque no carga o falla al dibujarse, sale **la de siempre**
  (`CrateCinematicClassic`, la de Fantastic Crates) y lo dice en el log; si falla solo el shader, sigue sin él.
- **`tools/crates/sonidos_crates.py`** → `assets/tfclient/sounds/crates/*.ogg` (eventos `tfclient:crates.*` en
  `sounds.json`): el ambiente (la cascada, el viento en las hojas, grillos, campanitas de las hadas, un mirlo lejos),
  **la música a los tiempos de la escena** (Re mayor: llegada, llave, giro, temblor, apertura, ruleta que se frena,
  hueco para el premio, final) y los efectos (llave aparece, entra, gira; runas; temblor; apertura; tic; parada;
  premio1..3 según la rareza). Si cambias los tiempos de la cinemática, cambia los de arriba del script (`KEY_APPEAR`…)
  y vuelve a generarlo.

### Probarla sin el dueño

`[prueba-jefes=crates]` en el commit (`BossTest.cratesStep`, ~1 min de juego; la prueba entera tarda menos de 10 min): comprueba que estén los registros y
`/tf crates`, pone una crate épica de estilo clásico y otra mítica de estilo cinemático y las abre como un jugador
(`CrateOpeningService.open`); el cliente saca 11 capturas por apertura a los tiempos de la cinemática
(`BossTestClient.crateShots`, a los ticks 12, 40, 58, 76, 92, 107, 140, 196, 255, 272 y 318: llegada, haz, llave,
gira, tiembla, abre, anillo, ruleta, para, premio, final) y el
informe trae los fotogramas (en Actions va con Mesa, sin tarjeta: ~11-13 por segundo; en un PC normal, muchos más).

## 3. Lo que falta / ideas

- Si el dueño quiere otra escena para otras crates (por rareza o por estilo), `santuario.py` puede escribir más `.bin`
  y `SanctuaryScene.get()` elegir por `cfg.styleId` o `cfg.rarity`.
- Las llaves planas (las tres `bg_key*`, dibujo en 2D) se orientan a ojo (`keyFlat`); las 3D van a lo largo de z con la
  punta hacia −z, que es como entran en la cerradura.
