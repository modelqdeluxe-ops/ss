# NPC de escenas (TF Client 1.3.101)

Los 13 packs de **Scenes** (MythicMobs 5 + ModelEngine 4) que mandó el dueño, convertidos y metidos en el jar con su
orden («ya puedes meterlos», 21 oct 2026). Lo que pidió:

> «Ve convirtiendo todos estos packs al TF Client: son NPC de escenas para ambientar lugares. Los quiero todos
> correctamente, con sus animaciones correctas, sin errores de textura ni de animación. Crea un ítem tipo panel de
> control para colocarlos, con posición en tiempo real, rotación y todo eso. Cada pack tendrá el nombre que ya trae. El
> comando para el ítem es /tf npc, y me lo entrega solo a mí, al que ejecuta el comando.»

## 1. Qué hay

| Pack (id en el mod) | NPC | Qué hacen |
|---|---|---|
| Scenes Tavern (`npc_tavern`) | 12 | camarero, peleas, borrachos, charlas, risas |
| Scenes Market (`npc_market`) | 21 + 1 marcador | vendedores y clientes; 4 vendedores y 2 clientes andan entre pins |
| Scenes Fishermen (`npc_fishermen`) | 5 | pescadores (de pie, sentado, en barca…) |
| Scenes Combat Vol 1 (`npc_combat_vol1`) | 10 | duelos, entrenamiento con arco, heridos, médico, público |
| Scenes Townsfolks Vol 1 (`npc_townsfolks_vol1`) | 11 | aldeanos; el banco donde se sienta el jugador |
| Scenes Townsfolks Vol 2 (`npc_townsfolks_vol2`) | 5 | cocinero, barrendero, mozos de carga |
| Scenes Townsfolks Vol 3 (`npc_townsfolks_vol3`) | 19 | sentados, tumbados, lectores, escritor, columpio, mendigo |
| Scenes Wanderers Vol 1 (`npc_wanderers_vol1`) | 5 + 3 marcadores | caminantes por rutas (a pie y a caballo); generadores y quitador |
| Scenes Guards Vol 1 (`npc_guards_vol1`) | 12 | de guardia, saludando, durmiendo, patrullas entre pins |
| Scenes Miners (`npc_miners`) | 9 | mineros con farol y casco; uno no muerto que ataca |
| Scenes Blacksmith (`npc_blacksmith`) | 5 | herreros en su yunque |
| Scenes Farmers (`npc_farmers`) | 9 | regar, sembrar, recoger; tres andan entre pins |
| Scenes Lumberjacks (`npc_lumberjacks`) | 7 | talar, serrar, cargar troncos; tres andan entre pins |

Cada NPC sale con el nombre del menú del pack («Blacksmith 1») y hace lo de su pack: su modelo, su animación de
escena, lo que hace al verte o al hacerle clic, andar entre sus pins, etc.

## 2. El panel de control (`/tf npc`)

- `/tf npc` (staff, nivel 2): da el **Panel de NPC** solo a quien lo ejecuta (`TFNpcs.command`).
- **Clic derecho al aire**: abre el panel. **Clic derecho a un NPC** con el panel: lo elige (no salta su ~onInteract).
- El panel va a la izquierda y **no pausa el juego**: lo que se toca se ve en el momento.
  - **Colocar**: pack → NPC (» = anda, · = marcador) → «Colocar donde miro» o «a mis pies»; sale mirándote.
  - **Editar**: el NPC elegido (o «Más cercano» / «Siguiente»). Mover respecto a como lo ves (izquierda, derecha, lejos,
    cerca, subir, bajar) con paso 0,05 / 0,25 / 1; girar 5° o 45° o «Mirarme»; «A donde miro» / «A mis pies»;
    piel y cosméticos de los menús del pack (piel, sombrero, espalda, manos, armadura) con ◀ ▶ y «Quitar cosméticos»; duplicar
    (con sus cosméticos) y borrar (con confirmación). Teclas: flechas, RePág/AvPág (subir/bajar), Q/E (girar); con
    Mayús, 1 bloque y 45°.
  - Si la pestaña no cabe (pantalla pequeña o escala de interfaz grande), **se desplaza con la rueda** fuera de las
    listas (que tienen la suya); lo que queda fuera no se ve ni se pulsa y una barrita dorada a la derecha dice dónde
    estás.
  - **Puntos**: los pins del NPC (los sitios entre los que anda: «Aquí» pone el pin donde estás) y las rutas de los
    caminantes (1–6: añadir punto donde estás, deshacer, borrar, asignar al caminante elegido).
- Con el panel en la mano se ven (solo tú) los pins (verdes los del NPC elegido), las rutas (una de cada color), los
  marcadores sin modelo y un anillo blanco en el NPC elegido.

## 3. Cómo funciona (fiel a MythicMobs / ModelEngine)

- **Conversor**: `tools/npcs/build_npcs.py` usa el de los jefes (`tools/bosses/build_bosses.py`, `KIND = 'npcs'`). La
  salida va a `tf-client/npcs/` (no se sube) y con `--al-jar` a `src/main/resources/assets/tfclient/`:
  `npcs/<pack>.json`, `npcs/catalog.json` (lo que enseña el panel), `npcs/models/`, `textures/npcs/`, `sounds/npcs/`
  y sus sonidos en `sounds.json` (claves `npcs.`).
  ```
  python3 -I tools/npcs/build_npcs.py <carpeta con los 13 packs descomprimidos>   # convertir (+ auditor)
  python3 -I tools/npcs/build_npcs.py --al-jar                                    # al jar
  python3 -I tools/npcs/build_npcs.py --auditar                                   # auditar los del jar
  ```
  Los packs no están en el repo (es público): los tiene el dueño (los zips que mandó). **Los nombres de las carpetas
  importan**: las skills comunes de la serie (`skill_scenes_npc.yml`: `movescene`, `rotatescene`…) vienen en versiones
  distintas según el pack y se queda la del primero en orden alfabético de carpeta. Para que salga igual que lo que hay
  en el jar, descomprime cada zip en una carpeta con el nombre que dice el campo `pack` de su `npcs/<pack>.json`
  (por ejemplo `641d9d9d-Scenes_Blacksmith`, `0dd3e1f5-5.0_scenes_tavern`), y comprueba con `git diff` que solo cambia
  lo que querías.
- **Catálogo**: de los menús de DeluxeMenus del pack salen los nombres (`scenes_npcs*.yml`: `mm mobs spawn X`) y los
  cosméticos (`scene_hats`, `scene_backpacks`, `scene_leftarm`, `scene_rightarm`, `scene_armors`, `reset_cosmetics`):
  cada opción con lo que hace su skill (changepart/remapmodel y las variables que guarda). A cada NPC solo se le
  ofrecen las que tienen sus huesos. Las pieles «skin_1…skin_52» **no salen**: son plantillas para que el dueño del
  servidor haga las suyas, y el pack no trae esos modelos.
- **Pieles**: casi todos los NPC del pack llevan la **piel de plantilla amarilla con una «S»** (el autor espera que el
  dueño del servidor haga las suyas con su web: «skin_1…», que no vienen). Los packs traen 21 pieles medievales de
  verdad (las de los caminantes, `npc_wanderers_vol1.wanderer_N`): son el grupo **Piel** del panel para todos
  (remapmodel, como el menú del pack) y **Piel del 2.º** para el segundo personaje de los dúos (changepart head2…).
  Al colocar un NPC con la plantilla, el panel le pone una (catálogo: `autoskin`; los guardias, de las de yelmo y
  armadura; los demás, de aldeano), distinta según su UUID. «Plantilla del pack» la devuelve. Los caminantes y el
  minero no muerto se ponen la suya solos (como en el pack). Vista previa de todos con su piel:
  `python3 tools/npcs/hoja_npcs.py <carpeta> [pack…]` (vfx_render, las mismas cuentas que el mod).
- **Entidad**: `tfclient:npc` = `BossEntity` en modo NPC (`isNpc()`): se guarda con el mundo (Despawn: persistent),
  sin barra ni botín, se puede tocar aunque sea invencible. Al volver del disco lleva el modelo que tenía
  (model{save=true}) y salta **~onLoad** (`SkillMobs.spawned` con `Host.loaded()`), como MythicMobs. Ojo: al
  descargarse su chunk, Minecraft primero deja de seguirla (`onRemovedFromWorld`, **sin** motivo de baja: el chunk
  solo se oculta) y luego la quita con `setRemoved(UNLOADED_TO_CHUNK)` sin pasar por `remove()` ni avisar: su estado
  se quedaba en `SkillMobs`. Al volver con el mismo UUID el motor creía que ya había nacido, no lanzaba su ~onLoad y
  salía en la pose de base, sin su animación (lo vio la prueba con granjeros, leñadores y la taberna, 1.3.101). Ahora
  `SkillMobs.reborn` olvida lo del de antes en su primer tick y `SkillMobs.sweep` (cada 10 s) lo de los descargados
  que no vuelven. Un `onRemovedFromWorld` que mire el motivo de baja **no sirve** (se probó: nunca lo ve).
- **Cosméticos**: el panel hace lo mismo que el menú del pack: changepart sobre su modelo y
  `setvariable{var=global.hat<uuid>;save=true}`. El ~onLoad del pack los vuelve a poner con
  `<global.var.hat<caster.uuid>>`. Las variables globales con save=true se guardan con el mundo
  (`skills/SkillSaved`, `data/tfclient_mythic.dat`); al borrar un NPC se borran las suyas.
- **Motor** (lo que les faltaba; en `JEFES.md` §3.6/§11 la lista de ModelEngine):
  - variables anidadas y con valor por defecto (`<global.var.skin<caster.uuid>|scene_guards>`): `SkillVars.text`;
  - `@pin{name}` y `?distanceFromPin{pin;distance}` (pins de /mm pin, guardados con el mundo; los pone el panel);
  - `followPath{path;speed;tolerance}`: sigue los puntos desde el más cercano y vuelve a empezar (las rutas del panel
    son circuitos);
  - `teleportTo{location}` lleva a los **objetivos** (en las clases, como siempre);
  - `light{m;p;block}` de ModelEngine = el brillo del hueso (el farol y el casco del minero);
  - `meleeHit{triggerskills}`: el golpe de su tipo, con sus ~onAttack (el minero no muerto);
  - changepart/remapmodel con un modelo o hueso que no existe (variables sin poner, erratas) no hacen nada;
  - changepart{m} solo cambia la pieza si el mob lleva **ese** modelo (ModelEngine). Los dúos lo ponen con
    `<global.var.skinchange>` (el último NPC que alguien tocó) a los 40 ticks de su ~onLoad: sin mirarlo, el segundo
    personaje volvía a la plantilla «S» amarilla (el dúo de la sierra, prueba 938bc382: 26 % de su figura en ese
    amarillo). Y el panel vuelve a poner lo elegido a los **55** ticks, después de la línea más tardía de los ~onLoad de
    los 13 packs (50 ticks), no a los 30;
  - **una animación suena una sola vez**, como en ModelEngine 4 (van por nombre): si la de reposo
    (`defaultstate{t=idle;s=angry1}`) también la pone un `state{s=angry1}`, no se suman (`VfxLayers.sameAs`). 57 NPC
    lo hacen (mercado 20, townsfolks vol3 14, taberna 11, combate 9, guardias, leñadores y mineros 1) y, como sus
    animaciones no llevan «override», salían con cada giro y cada desplazamiento al doble: el «angry 1» de la taberna con
    el torso tumbado y partido de las piernas (lo vio el dueño; con la vista previa, angry1 al doble a los 4 s da esa
    misma pose). Y la de reposo o la de andar puesta por state{} se para cuando deja de ser la que toca
    (`VfxLayers.stopped`): el vendedor del mercado lleva «vendor_2_idle» por state{} y, al andar, se le sumaba a la de
    andar. Ningún jefe tiene esas combinaciones.
- **Rutas de caminantes**: el pack trae `path1`/`path_1_follow` solo de ejemplo (comentadas, con coordenadas del mundo
  de su autor). El conversor las genera como las del ejemplo (`build_npcs.route_skills`), leyendo la ruta de las
  variables `scenes_route_N` / `scenes_route_N_start`, que escribe el panel. Usan **@TargetedTarget** en vez de @EIR
  (el ejemplo podía coger a un jugador y teletransportarlo): el caminante al que se le pasa la skill, sea el generador
  del pack (`skill{skill=path1} @EIR`) o el panel, que se la pasa al propio caminante. Con @target no andaban: en
  MythicMobs es el objetivo de combate del que lanza, que un caminante no tiene (1.3.101). Para eso el motor hace
  @TargetedTarget como MythicMobs: los objetivos heredados tal cual, también el lanzador si es a él a quien se le pasó
  (`SkillTargets`; @TT de las clases sigue como estaba).
- **Fallos del propio pack** (avisos con su porqué en `build_npcs.HUECOS`): Molang «-» en las tablas del leñador
  (= 0, como Blockbench), el sonido `lumberjack_interact` que no viene, la caja del banco no cuadrada, la errata del
  mendigo en su ~onLoad, la animación «sawing» que el dúo de guardias pide al segundo modelo, los marcadores sin
  modelo («command»), y **el sedal del pescador de «fishing», que sube al cielo**: es del pack, medido con las
  cuentas de Blockbench (y con cualquier orden de ejes). En su pose de pesca el brazo va a −55° y la caña a −58°, así
  que la caña queda a 113°, hacia atrás, y el sedal sigue hacia arriba hasta unos 10 bloques del suelo. En «boat» la
  caña lleva +25° y sale bien. Todas sus animaciones tienen «override», así que no se suman. Parece una errata de signo
  del autor: con +58° colgaría al agua. Se deja como en el pack (ModelEngine lo enseña igual); si el dueño lo quiere
  arreglado, es cambiar esa animación del pack.

## 4. Comprobarlo

- `python3 tools/bosses/verificar.py` → «todo bien» (incluye «auditor: 0 problemas en los NPC de escenas»).
- `python3 tools/npcs/pegado.py [pack…] [--plantilla]`: la cabeza, los brazos y las piernas de cada NPC pegados a su
  cuerpo en cada animación de su modelo y en la pose de su escena, con las mismas cuentas que el mod (distancias
  exactas entre caras, en píxeles de Blockbench). Sin `--plantilla`, con la piel que les pone el panel (las 21 tienen la
  misma geometría: lo comprueba); con él, con la geometría del propio modelo del pack. Unos 90 s por pack (los 13 packs en
  paralelo: `xargs -P 4`).
- Prueba en el juego: `[prueba-jefes=npcs]` en el commit (`BossTest.npcsStep`, ~3 min): todos los NPC en filas con
  una captura cada 5 (`NN_npcs_npcs_<pack>_<n>.jpg`), un guardia con cosméticos movido y girado, guardado y vuelto a
  cargar, un vendedor entre dos pins, un caminante por una ruta y el panel abierto. Informe en la rama `pruebas-jefes`.
  El jugador llega y pone cada NPC 3 ticks después (en el mismo tick el chunk no estaba cargado); antes de cada captura
  se vuelve a buscar cada NPC cerca de su sitio (si su chunk se descargó, vuelve del disco como entidad nueva: «recargado
  del disco» en el informe) y la captura sale 60 ticks después de mover la cámara.
