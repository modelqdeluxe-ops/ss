# Jefes de incursión de TF Client: documentación técnica completa

Este documento sirve para que otra IA (o persona) retome los jefes sin tener que redescubrir nada. Léelo después del
CHECKPOINT de `HANDOFF.md`, porque las reglas del dueño están allí (español, nada de subagentes, compilar con GitHub
Actions, publicar como Release y sin abrir nunca el juego ni un servidor).

- **Estado:** TF Client **1.3.80**.
  - 1.3.75: jefes, grietas y `/tf boss`.
  - 1.3.76: la IA, fiel a MythicMobs/ModelEngine.
  - 1.3.77: el cerebro táctico.
  - 1.3.78: peleas más vivas (combos, tempo, giro, espaciado, paso atrás), los modelos completos (el «ojo» de
    Blockbench), `partvis` como ModelEngine, la esfera de Astwood y el auditor `tools/bosses/audit.py`.
  - 1.3.79: el motor, listo para packs nuevos: `~onDamaged` antes del golpe con `cancelevent`/`modifydamage`, tabla
    de amenaza (`threat`, `clearthreat`, `rally`, `@ThreatTable`/`@TT`, `@RandomThreatTarget`), `setinvulnerable`,
    `goto`, `suicide`, `setmaxhealth`, `shield`, `explosion`, `forcepull`, `disengage`; disparadores
    `~onDropCombat`, `~onChangeTarget`, `~onKill`/`~onPlayerKill`, `~onInteract`; condiciones que antes se daban
    por cumplidas (`lineofsight`, `world`, `biome`…) y 20 nuevas; el auditor ve lo que el motor acepta «de mentira»,
    los disparadores que no saltan, la cobertura (`--cobertura`) y se prueba solo (`--prueba`).
  - 1.3.80: **ModelEngine 4 completo en el motor** (§3.6): colas y segmentos `seg_`/`tl_` que se arrastran, física
    `jig_`, IK de Blockbench y `ikchain`/`iktarget`, cajas `b_`, cabezas con límites/velocidad/bloqueo/objetivo,
    `tag_` con nombre, objetos en huesos, miembros con la skin del jugador, brillo de encantado, billboard, contorno
    (`glowbone`), `vfx` pegados a mobs y proyectiles, `modifystate`/`statetoggle`, `cycleparts`, `hitboxconfig`,
    parpadeo rojo al recibir daño (`damagetint`) y `step`; el conversor lee las 26 etiquetas de hueso y hornea bezier,
    Molang y fotogramas antes/después; efectos de MythicMobs nuevos (`particletornado`, `particlelinehelix`, `atom`,
    `geyser`…); auditor de modelos y texturas; pruebas `test_convert.py` y `sim_physics.py`. Ver §10 para Mohist.
- **Regla del dueño:** «quiero que este motor sea perfecto y bien corregido para todos los bosses que metamos». Ver
  §7: nada se publica sin el auditor a 0, su prueba y el simulador.
- **Packs que hay:** 5 de LittleRoom y otros: Gárgola, Hidra, Abominación Parasitaria, Esqueleto de la Torre y Astwood.
- **Cómo llegan los packs:** el dueño los manda «de 5 en 5» en zips (MythicMobs + ModelEngine). **Nunca subas los
  packs al repo.** Solo se sube lo convertido.

---

## 1. Qué es, en una frase por pieza

| Pieza | Qué hace |
|---|---|
| `tools/bosses/build_bosses.py` | Convierte cada pack a datos del mod: el programa del jefe, los modelos, las texturas, los sonidos, los glifos y los objetos. |
| `assets/tfclient/bosses/<id>.json` | El «programa» del jefe: el árbol de skills de MythicMobs que alcanza, sus mobs, sus textos y sus valores base. |
| `skills/` (motor de skills) | Ejecuta ese programa con las reglas de MythicMobs: mecánicas, condiciones, objetivos, auras, proyectiles y tótems. |
| `vfx/VfxLayers` | Animaciones a capas como ModelEngine 4: una máquina de estados por prioridad. |
| `skills/SkillMobs` | Convierte la entidad del jefe en un «host» de MythicMobs: disparadores, temporizadores, scripts de animación y fases. |
| `skills/SkillBrain` | El **cerebro táctico**: elige qué ataque del pack sale y cuándo, y cómo se mueve el jefe. |
| `bosses/BossEntity` | La entidad del jefe y de sus esbirros: vida, golpes, movimiento, giro, objetivo y barra de jefe. |
| `bosses/Rifts` y `RiftEntity` | La grieta de cristal: al golpearla despierta al jefe. Lleva la incursión, la recarga y las recompensas. |
| `bosses/BossScreen` | La ventana `/tf boss`: lista de jefes, vista previa 3D, dificultad y grietas. |
| `tools/bosses/sim_ai.py` | Simula la IA sin el juego: qué ataques salen, si castiga y si se bloquea. |
| `tools/bosses/sim_gui.py` | Comprueba la disposición de la ventana a 8 tamaños: que nada se solape ni se salga. |
| `skills/SkillMeg` | Las mecánicas de ModelEngine que cambian cómo se ve el modelo (§3.6): las manda al cliente. |
| `vfx/VfxPhysics`, `vfx/VfxIk` | Colas/segmentos y física `jig_`; cinemática inversa (§3.6). Las usa el cliente al dibujar. |
| `tools/molang.py` | Evalúa el Molang de los keyframes para hornearlo al convertir. |
| `tools/bosses/audit.py` | **El auditor**: revisa cada jefe contra el motor (mecánicas, condiciones y objetivos sin implementar, skills que faltan, animaciones y huesos que no existen, sonidos y modelos). Lo lanza `build_bosses.py` al acabar. |

Todas las rutas Java cuelgan de `tf-client/src/main/java/net/tierrasfantasticas/tfclient/`. Los recursos cuelgan de
`tf-client/src/main/resources/assets/tfclient/`.

---

## 2. De los packs al juego (datos)

### 2.1 Convertir

```bash
# 1) Descomprime cada pack en una carpeta (Astwood venía en RAR: apt-get install libarchive-tools && bsdtar -xf X.rar)
# 2) Ejecuta (desde tf-client/):
python3 tools/bosses/build_bosses.py <carpeta con los packs descomprimidos>        # todos (borra y rehace la salida)
python3 tools/bosses/build_bosses.py <carpeta> LITTLEROOM_HYDRA_v1_0                # solo uno
```

Debe decir `problemas: 0` en cada jefe y, al final, `auditoría <jefe> problemas: 0` en cada uno (ver §6). Las líneas
con `·` son avisos: erratas del pack que el conversor ha corregido solo, como `errata del pack: animación
«absord_life_start» → «absorb_life_start»` (Astwood; en ModelEngine esa animación no salía). Solo corrige una letra
cambiada por otra y solo si hay un único nombre así (`head_6_bite` nunca se convierte en `head_1_bite`).

`JEFES`, al principio de `build_bosses.py`, relaciona cada carpeta del pack con su jefe:

- `id`: el nombre del jefe en el mod.
- `mob`: el mob de MythicMobs que es el jefe.
- `vida` y `dano`: la vida y el daño base en Normal con 1 jugador. Los packs traían valores de ejemplo (la Abominación,
  50 de vida).

`tools/bosses/es.json` lleva, por jefe:

- `name`, `title`, `color`, `desc` y `attacks`: los textos de la ventana.
- `preview_hide`: los huesos que no salen en la vista previa (estelas, piezas de escena).
- `drops`: los nombres en español de los objetos que suelta.

`FIXES` completa skills que el pack llama sin traerlas (`LR_HYDRA_stomp_damage`).

### 2.2 Qué sale

| Salida | Qué es |
|---|---|
| `bosses/<id>.json` | `boss` (mob, nombre, color, vida/daño base, `pack_health`, modelo, `hitbox`, ataques, `preview_hide`), `tree` (las skills alcanzables, ya compiladas), `mobs` (sus mobs: tipo, opciones, IA, mecánicas con disparador, `kind` entity/actor, `speed` del tipo), `models`, `item_models`, `drops` (con `mechs`, `blocking`, `name_es`), `glyphs` y `missing`. |
| `bosses/models/<id>.<modelo>.json` | Los `.bbmodel` convertidos por `tools/vfx_bb.py` en **modo jefe**: prioridad y override de las animaciones, scripts `mm:` de la línea de tiempo (`fx`), cajas `ob_` (`hb`), asientos `p_`, cabezas `h_`/`hi_`, el hueso `hitbox` que da el tamaño de la entidad, y texturas `_e` que brillan. **El «ojo» de Blockbench (visibility) no cuenta en los jefes**: ModelEngine dibuja esos huesos y cubos igual, y el pack los enseña y esconde con `partvis`. Hasta la 1.3.77 se perdían: el Esqueleto no tenía escudo, ni espada a la espalda, ni escudo junto al trono; el trono no tenía escalinata ni pozo de almas; la Abominación, ni pústulas ni zarpazo. |
| `bosses/models/grieta.cristal.json` | El cristal de la grieta. Es procedural (`tools/bosses/rift_model.py`). Sus texturas `*_tint_e` se tiñen del color del jefe y brillan. La pasada completa de `build_bosses.py` lo rehace solo. |
| `textures/bosses/<id>/`, `models/bosses/<id>/`, `models/item/jefe_<id>_<objeto>.json` | Texturas, modelos de ítem (balas «display», objetos) y los modelos de los objetos que sueltan. El escudo lleva el override `blocking`. |
| `sounds/bosses/<id>/*.ogg` + `sounds.json` | Eventos `tfclient:bosses.<id>.<x>`. |
| `font/bosses_<id>.json` | Glifos de sus `text_display`, como el lanzallamas de la Hidra. |

En el juego, `SkillDefs.bosses()` lee el catálogo y cada `<id>.json` como un `ClassDef` con `boss = true` y `bossInfo`.
Cada mob del jefe es un `MobDef` con `entity`, `speed`, `typeSpeed`, `chases`, `invincible`, `damageModifiers`, etc.

---

## 3. El motor (cómo se ejecuta un jefe)

### 3.1 La entidad y su «host»

`BossEntity` (`tfclient:jefe` para el jefe, `tfclient:esbirro` para sus esbirros) implementa `SkillMobs.Host`:

- **`configure(boss, mob, dificultad, jugadores, dueño)`:** pone la vida con
  `Difficulty.healthFor(vida base, jugadores)`. El tope de 1024 de Minecraft se salta con `mixin/BossHealthMixin`
  (`getMaxHealth` es final). También pone el daño de ataque, la armadura, la velocidad y la barra.
- **`healthScale`** = vida real / vida del pack. Las cifras fijas de vida del pack (el escudo del Esqueleto aguanta
  «500») y el `damage-amount` de `~onDamaged` van en la escala del pack.
- **`hurt()`:**
  - cada jugador tiene su propio respiro de 8 ticks entre golpes;
  - aplica los modificadores de daño del pack;
  - suma amenaza;
  - lanza `~onDamaged` con el **daño del golpe**, no con lo que le quitó (con Resistencia V, eso sería 0).
- **`customServerAiStep()`:** elige objetivo (`pickTarget`), le persigue con navegación hasta casi tocarlo (su cuerpo
  + 1 bloque) y aplica el plan del cerebro (§4.3): ponerse a la distancia de lo que va a hacer, apartarse si se le
  pegan más de lo que sus ataques permiten (con paso atrás, `backstep`) y rodear. La correa con su grieta es de 40
  bloques.
- **`tempo()`** = velocidad de la dificultad × (1 + 0,12 × inteligencia): Fácil 0,93, Normal 1,07, Difícil 1,18,
  Pesadilla 1,29, Demencial 1,4. Ver §3.3.
- **`turnBody()`:** el giro, como ModelEngine.
  - `lookYaw` es hacia dónde mira la **entidad**: sigue al objetivo a 30° por tick. Con eso miden fieldOfView,
    @forward y los proyectiles.
  - `bodyYaw` es el **modelo**. Andando, sigue esa dirección. Parado, la cabeza no pasa de `mh` y arrastra al cuerpo,
    y pasados `rde` ticks el cuerpo se pone de frente en `rdu`. **Con la inteligencia, `rde` y `rdu` se acortan**
    (× (1 − 0,8 × int.) y × (1 − 0,7 × int.)): la Abominación del pack esperaba 4 s y tardaba 6 s en ponerse de
    frente («lentos al girar»).
  - Mientras ataca (su gcd, o lo que dura el ataque en los packs que no ponen gcd), con el cerebro, sigue al jugador
    hasta que `lockmodel` lo congela.
- **`motion()` / `restoreMotion()`:** foto de cómo se mueve (velocidad, `lockmodel`, `bodyrotation`, IA) al empezar un
  ataque, para dejarlo igual si el cerebro lo corta para encadenar otro.
- **`BossPart` (piezas):** hasta 40 cajas de golpe por hueso `ob_` (las cabezas de la Hidra). Llevan id = id del jefe
  + 1 + i.
- **Muerte:** `die()` → `~onDeath`, mata a sus esbirros, `Rifts.bossKilled` → monedas. La animación de muerte dura lo
  que su animación `death`.

### 3.2 SkillMobs: disparadores

Las líneas del mob (`mobs.<mob>.mechs`) llevan `tr`/`trv` (disparador) y `hp` (umbral de vida, para las fases).

| Disparador | Cuándo salta |
|---|---|
| `onspawn` | El primer tick. |
| `ontimer:N` | Cada N ticks, en el orden del pack. |
| `onentercombat` | Al tener objetivo, **una vez por pelea**: si lo pierde un momento no vuelve a saltar (salvo tras 10 s sin objetivo). La Abominación volvía a gritar cada vez. |
| `ondropcombat` | Tras 10 s sin objetivo después de pelear (la misma regla que la entrada). @trigger: nadie. |
| `onchangetarget` | Pasa de un objetivo a otro. @trigger = el nuevo. |
| `ondamaged` | Al recibir un golpe, **antes de que entre** (como el `EntityDamageEvent` de MythicMobs): `damage-amount` (golpe en la escala del pack), `damage-cause`, la vida de **antes** para sus umbrales (`<50%`), y sus skills pueden anularlo (`cancelevent`) o cambiarlo (`modifydamage`). Solo cuenta lo que hagan en el momento (lo que vaya con `delay` llega tarde, como en MythicMobs). |
| `ondeath` | Al morir. |
| `onkill` / `onplayerkill` | Mata a algo / a un jugador con un golpe o una skill (`BossEntity.killedEntity`). @trigger = la víctima. |
| `oninteract` | Clic derecho de un jugador (`BossEntity.mobInteract`). En los packs de ahora solo lleva `cancelevent` (no hace nada: no hay golpe que anular). |
| `onsignal:X` | Al recibir `signal{s=X}`. |
| `onload` | No salta: los jefes no se guardan en el mundo (`shouldBeSaved = false`), nunca vuelven del disco. El auditor lo comprueba en el Java. |
| `onattack` | No salta: no hay golpe cuerpo a cuerpo de Minecraft. En los packs solo lo usan para cancelarlo; si un pack hace algo más con él, el auditor lo marca como problema. |

Los scripts `mm:` de la línea de tiempo de las animaciones se lanzan desde `SkillMobs.scripts`.

### 3.3 SkillRuntime: reglas de MythicMobs que importan (no las rompas)

| Regla | Dónde | Por qué |
|---|---|---|
| El cooldown de una skill **solo se pone si sale** (después de sus condiciones y de las de sus objetivos). | `runMeta` | Si se pone antes, los ataques que se miran cada segundo se quedan en recarga sin salir (antes de la 1.3.76 quedaba uno). |
| `setvar{...;duration=N}` **caduca** a los N ticks (vuelve a valer «UNDEFINED»). | `putVar` | La Hidra y la Abominación marcan «estoy mordiendo / saltando» para unos segundos. |
| `gcd{ticks}` es **siempre de quien lanza** y **no se divide por el ritmo** de la dificultad (sí por el tempo, ver abajo). | `apply` → `gcd` | Es lo que dura el ataque. Si se acorta sin acortar la animación, el siguiente la corta a medias. |
| **Tempo de los jefes** (`ctx.host != null`): los `delay` (de línea y del guion), `repeatinterval`, el `gcd`, las duraciones de `setvar` del jefe y el `li`/`lo` de `state` se **dividen** por `tempo()`, y la velocidad de sus animaciones se **multiplica**. | `SkillRuntime.tempo`, `SkillModels.state`, `putVar` | Todo el ataque va más rápido a la vez: los golpes siguen cayendo donde se ven. Las variables «estoy atacando» (Abominación, Astwood) acaban con su ataque. |
| `partvis` en los jefes, **como ModelEngine**: sin `v` **esconde**; sin `child`, solo ese hueso; `em=true` por defecto (exacto, o sin el prefijo de comportamiento: `right_arm_trail` = `tl_right_arm_trail`); `em=false` = todos los que lo llevan en el nombre. Las clases siguen como antes (v y child a true). | `SkillModels.partVis`, `VfxModel.bone` | Con los valores de las clases, el Esqueleto enseñaba la espada de su muerte junto a la pierna. |
| `@EIR`/`@PIR`… de un jefe **no cuentan a sus propios esbirros ni a sus efectos** (salvo que la línea busque un tipo concreto). | `SkillTargets.entities` | La esfera de Astwood (`@EIR{r=32;limit=8}`) salía hacia sus retoños y muros. |
| `missile` de un jefe: apunta al **centro** del objetivo, persigue a media altura y su inercia es como mucho 12. | `SkillMovers.projectile` | Con `inertia=100` y apuntando a los pies, la esfera de Astwood se clavaba en el suelo («cae como un huevo»). |
| Los usos de una skill llevan un **id** (`Ctx.cast`) en sus tareas pendientes y su guion. | `later(ticks, cast, r)`, `cancelCast` | El cerebro corta lo que le queda a un ataque para encadenar otro (combos). |
| En los jefes, las mecánicas de ModelEngine **sin objetivo** van a su propio modelo. | `targets()` + `OWN_MODEL` | `bodyrotation{mh=0}` dentro de un ataque lanzado con @target es para el jefe, no para el jugador. |
| `propel` en los jefes: «v» bloques por tick hacia el objetivo (se pone, no se suma). | `SkillMotion.propel` | Con la regla de las clases (v/10, sumado) la Gárgola no saltaba. |
| `setspeed{s}` = velocidad **del tipo** de Minecraft × s. Hasta el primer setspeed manda `MovementSpeed`. | `BossEntity.applySpeed` | La Abominación sale con MovementSpeed 0 y su `setspeed{s=1.25}` la pone a andar. |
| `potion{level}` en los jefes = **amplificador** (0 = I). En las clases, nivel − 1, como estaba. | `potion()` | El escudo del Esqueleto (`DAMAGE_RESISTANCE level=4`) es Resistencia V: inmune. |
| `throw` y `pull` van entre 10 (como MythicMobs); `lunge` no. | `SkillMotion` | Velocidades del pack. |
| `playerWithin` cuenta a quien está en creativo (no a los espectadores). **Pero no atacan a quien está en creativo**, como en MythicMobs. | `SkillConds`, `BossEntity.pickTarget` | El staff los despierta; para probar la pelea, en supervivencia. |
| Los golpes de jefe dan 10 ticks de inmunidad por mob (como MythicMobs). | `hurt()` | |
| `~onDamaged` va **antes** del golpe. `cancelevent` lo anula (quien pega sigue sumando amenaza) y `modifydamage{amount;action}` lo cambia: `MULTIPLY` por defecto, `ADD`/`SUBTRACT`/`SET` en la escala del pack, `DIVIDE`. Fuera de un `~onDamaged` no hacen nada (no hay evento), como en MythicMobs. | `BossEntity.hurt`, `SkillMobs.damaged` → `Hit`, `SkillRuntime.modifyDamage` | Fases invulnerables y armaduras de los packs. |
| Las cifras de vida del pack van **en su escala**: `health{h}`, `setmaxhealth{a}`, `shield{a;maxshield}`, `heal{a}`, `damage @self`. | `SkillConds` (health), `apply` | El pack no sabe de dificultades: «500» es 500 de su vida, no de la nuestra. |
| `@TT` en un jefe es **@ThreatTable** (MythicMobs); en las skills de clase sigue siendo el objetivo heredado (`pistolero_despertado`). `threat{amount;mode}` = ADD/SUBTRACT/MULTIPLY/DIVIDE/SET/RESET/FORCE (FORCE: pasa a ser el primero y su objetivo). `rally` = FORCE en los de su grupo cercanos. | `SkillTargets.threat`, `BossEntity.threat` | Burlas, cambios de objetivo y ataques «al que más le pega». |
| `explosion{yield;fire}` **nunca rompe bloques** y solo daña a quien el jefe puede dañar (la cuenta de daño de Minecraft, con la dificultad). `fakeexplosion`/`effect:explosion`: solo se ve y se oye. | `SkillRuntime.explosion` | El servidor tiene construcciones y protecciones. |
| Lo que el motor acepta sin hacer nada (`-> { /* nada */ }` en `apply`, `-> true;` en `SkillConds`) está explicado en `STUB_OK` de `audit.py`. Si añades uno, explícalo allí o el auditor lo marcará en cuanto un pack lo use. | `apply`, `SkillConds`, `audit.py` | Que nada se quede «de mentira» sin que se sepa. |

### 3.4 Animaciones: VfxLayers (ModelEngine 4)

- **Prioridades:** cada **prioridad** (`state{p=N}`) es una máquina de estados con **una** animación. La nueva sustituye
  a la que hubiera en esa prioridad, fundiéndose en su `li`. Las prioridades se apilan de menor a mayor.
  - Con **override** (la casilla de Blockbench, `ov` en el JSON), tapa a las de debajo **en los huesos que mueve**.
  - Sin override, se suma.
- **Fin de una animación:** una de una vez (`once`) acaba sola y libra su prioridad; `hold` se queda en su final;
  `loop` se repite.
- **Por defecto:** debajo de todo va la animación por defecto (`idle`/`walk`, o lo que ponga `defaultstate`).
- **Tempo:** en los jefes, `state{sp}` × `tempo()` y `li`/`lo` ÷ `tempo()`. Al encadenar un ataque, las animaciones
  entran con al menos 5 ticks de mezcla (`Host.minLerp`, 6 ticks) y las del ataque cortado se van en 5
  (`SkillModels.cutSince`), así no salta de una pose a otra.
- **Muerte:** `death` lo tapa todo.
- **Ejemplo:** el «sleep» del Esqueleto lo sube 5,6 bloques sobre el trono. Si no se sustituye al despertar, se queda
  flotando (fallo de la 1.3.75).
- **Servidor y cliente:** el servidor lleva las mismas capas (el actor «disfraz» del jefe, `SkillModels.disguise`) para
  saber dónde está cada hueso (`@modelpart`, tótems de los golpes) y lanzar los scripts. El cliente las reproduce con
  los mensajes `lstate`/`lstop`/`dstate`/`death`, y a quien llega tarde se le repiten (`BossEvents` →
  `SkillMobs.replay`).
- **Piezas escondidas** (`partvis`): el servidor lleva qué huesos de cada jefe están escondidos ahora
  (`SkillActor.trackVis`, con la misma regla de `child` que el cliente) y a quien llega tarde le manda ese estado
  hueso a hueso. Lo usan también sus cajas de golpe (`SkillModels.hiddenBones`: una cabeza cortada de la Hidra ya no
  recibe golpes). Guardar solo el último `partvis` de cada hueso no basta: uno con `child=true` en el padre tapa a los
  de sus hijos y el orden importa.

### 3.5 Golpes

Casi todos los ataques de los packs son **tótems** en un hueso (`totem{oh=X} @modelpart{pid=r_hand}`, repetidos cada
tick mientras dura el golpe): `SkillMovers.totem`. Por defecto el tótem va 1 bloque por encima del sitio.

`basedamage{m}` = daño de ataque del jefe (con la dificultad) × m. Los daños fijos (`damage{a=15}`) se multiplican por
el daño de la dificultad.

Los proyectiles «MOB» (las llamas en `text_display`) y los «display» (la esfera de Astwood) son actores del cliente.

---

### 3.6 ModelEngine 4 en el motor (1.3.80)

Todo según la wiki de ModelEngine («Bone Behaviors» y «Skills/Mechanics»). Lo visual lo hace el cliente (`SkillClient.Actor`)
con lo que le manda el servidor en «props» del actor del modelo (se guardan y se repiten a quien llega tarde;
`SkillActor.prop`, `PER_MODEL`). Las mecánicas del servidor están en `skills/SkillMeg.java`.

**Etiquetas de hueso** (las lee `tools/vfx_bb.py`, `bone_tag`; en el JSON del modelo van en `beh` y `bp`):

| Etiqueta | Qué hace aquí |
|---|---|
| `h_`, `hi_`, `h[yaw;pitch;minyaw;maxyaw;minpitch;maxpitch;speed]_` | Cabeza: sigue la mirada (repartida entre las encadenadas), con límites (`VfxModel.headLimits`, `VfxPose.build`) y velocidad máxima por tick (`Actor.pose`). `hi_`: sus hijos también. |
| `p_`, `mount` | Asiento (agarres). |
| `ob_` / `b_` | Caja de golpe que gira con su hueso / recta del mundo que solo le sigue (`VfxHitboxes`; `b_` debe ser cuadrada en X/Z). |
| `seg_`, `segf_`, `tl_`, `tlf_` | Cola o segmento (solo sin cubos, pivote atrás; `f` = delante): `vfx/VfxPhysics` (abajo). Conservan el nombre entero (`tl_tail_2` y `tail_2` conviven en la Gárgola). |
| `jig_`, `jig[stiff;damp;grav;angle;repeat]_` | Física: muelle amortiguado con gravedad y ángulo máximo (`VfxPhysics.jiggle`). `repeat` (eslabones) se trata como 1. |
| `ik[minx..maxz]_` | Límites de esa articulación en las cadenas IK. |
| `tag_` | Hueso de nombre: `setmodeltag` o `model{nametag=hueso}` ponen el texto (mirando a la cámara). |
| `l_` (sin cubos) | Correa: se lee, pero `leash`/`leashself` aún no se dibujan. Con cubos es un hueso normal («l_hand» = mano izquierda). |
| `ih_`, `ir_`, `il_` | Objeto en la cabeza / mano derecha / izquierda (`setitemmodel`, `linkitembone`). |
| `phead_`, `prarm_`, `plarm_`, `pbody_`, `prleg_`, `plleg_`, `limb[type=X]_` | Miembros con la skin del jugador (`modelplayerskin`; sin ella, la skin por defecto). El conversor pone la caja del miembro con el reparto de la skin (caras -4 y su capa -5); `limb[type]` son mitades (chest, forearm, foreleg...). |
| `g_` | Hueso fantasma. |

**Colas y segmentos** (`VfxPhysics`, igual en `tools/bosses/sim_physics.py`): cada eslabón cuelga del de delante (o
de su padre) y, en el mundo, apunta a donde estaba (la cola se arrastra al girar y andar), vuelve a su forma animada
con `extendrate` por tick, no se aparta más de `anglelimit` (o `x`/`y`, `minx`...) del eslabón de delante si `bounded`,
y `elasticity` le da inercia. Sin `segment{}` del pack: bounded, 50°, 0,01 (los de ModelEngine). Su trozo visible se
orienta con su eslabón, o con el siguiente si cuelga hacia atrás (`decideOutgoing`). Verificado: la Gárgola girando
120° en 10 ticks dobla la cola en un arco continuo (sin huecos) y la capa del Esqueleto hace la onda de arriba abajo.

**IK** (`VfxIk`, igual en `tools/bosses/test_convert.py`): las cadenas de Blockbench (objeto nulo con IK Target: el
conversor lo hace hueso invisible `iknull` que su animación mueve; `ik` del modelo = raíz…punta) y las de `ikchain`.
CCD de 16 vueltas: cada articulación lleva consigo lo que cuelga de ella tal cual (si se rehiciera con su giro
animado se perdería lo girado y no converge: fallo encontrado y arreglado al probarlo); si la cadena está recta y el
objetivo en su línea, se dobla un poco por su eje X (bisagra). Polo, `lockrotation`, peso y `blendticks` como
ModelEngine; una cadena de Blockbench solo manda mientras una animación que suena mueve su nulo (o `alwaysactive`).

**Mecánicas de ModelEngine nuevas**: `modifystate`, `statetoggle`, `headconfig`, `lockmodelhead`, `headtarget`,
`jiggleimpulse`, `hitboxconfig` (cuánto daño deja pasar cada caja: `BossEntity.hurtPart`), `setitemmodel`,
`linkitembone`, `setmodeltag`, `setmodeltagvisible`, `enchant` (brillo de encantado), `billboard`, `glowbone`
(`meg:glow`: el contorno de Minecraft solo en esos huesos; `BossEntity.isCurrentlyGlowing` con `SkillMobs.clientGlow`),
`cycleparts`, `modelplayerskin`, `ik`, `iktarget`, `ikchain`, `scale`, `nofall`, y la familia `vfx` (`vfx`, `vfxpos`,
`vfxrot`, `vfxscale`, sus `reset`, `vfxchangemodel`, `vfxsetvis`, `vfxenchant`, `vfxtint`, `vfxbrightness`: un modelo
o un hueso pegado a un mob o a un proyectil). Y de `model{}`: `damagetint` (parpadeo rojo al recibir daño, sí por
defecto), `nametag`, `lockpitch`/`lockyaw` y `step` (**en los jefes `s` es step, no la escala**: antes se leía como
escala).

**Nombres con espacio**: `meg:X` es la de ModelEngine (`meg:glow` → `glowbone`, `meg:scale`, `meg:jump`) y
`mythicmobs:X` la de MythicMobs (`tools/skills/compile.py`, `parse_line`).

**El conversor** (`tools/vfx_bb.py`) además: bezier con sus asas, expresiones Molang (`tools/molang.py`: math.*,
`q.anim_time`, variables...) y fotogramas con valor antes/después se **hornean** cada 1/40 s como lo calcula
Blockbench (el juego no evalúa Molang); Box UV sin UV por cara; texturas `render_mode: emissive`; objetos nulos y
localizadores como huesos invisibles. Lo que no entiende sale en «problemas».

**Efectos de MythicMobs nuevos** (`SkillFx`): `particletornado`, `particlelinehelix`, `particlelinering`, `atom`,
`smoke`, `flames`, `ender`, `geyser`, `spring`, `itemspray`, `firework`, `stopsound`. Y `summonfallingblock` (nunca
se queda en el mundo: se crea vacío y el bloque va por NBT; `FallingBlockEntity.fall()` borraría el bloque de ese
sitio), `shootpotion`, `shootshulkerbullet`.

## 4. El cerebro táctico (SkillBrain), de 1.3.77

El dueño pidió «que la IA sea muy inteligente, tipo Dark Souls o Elden Ring».

**La idea:** los ataques siguen siendo **los del pack**, con sus animaciones, golpes, condiciones y cooldowns. El cerebro
solo decide **cuál sale y cuándo**, y **cómo se mueve el jefe** entre ataques.

Se gradúa con la **inteligencia** de la dificultad (`Difficulty.smart`, de 0 a 1, fila «Inteligencia» de `/tf boss`).
**0 = el pack exactamente como MythicMobs.**

| Preset | Fácil | Normal | Difícil | Pesadilla | Demencial |
|---|---|---|---|---|---|
| Inteligencia | 30 % | 55 % | 75 % | 90 % | 100 % |

### 4.1 Qué líneas decide

`SkillBrain.build`, al primer tick del mob, mira sus líneas `~onTimer` de 10 ticks o más:

- **Las que decide:**
  - `skill{s=X} @<algo que no sea self>` cuya skill sea **ofensiva**, es decir, que ella o lo que llama tenga daño,
    `throw`, proyectil, tótem, `summon`, `lunge`, `propel`, `mountmodel`, `stun`, `potion`/`velocity` a otros o una
    carga (`setspeed` ≥ 2);
  - cada skill ofensiva de una `randomskill` (Astwood).
- **Las que siguen como en el pack:**
  - las de `@self`: poner el escudo, la espada de almas;
  - las de cada tick: los giros de la Hidra, el final de la carga;
  - las no ofensivas: los giros en salto de la Hidra;
  - las de los demás disparadores.

Esas líneas ya no salen solas (`SkillMobs.tick` las salta). Para cada ataque se apunta, leyendo sus condiciones:

- **El alcance**, `min`–`max`, a partir de `distance{d=4to16}`, `targetwithin`/`targetnotwithin` y `<6`/`>14`.
- **Su tipo:**

| Tipo | Cuándo se marca |
|---|---|
| `gap` (acorta distancia) | `lunge`, `propel`, `leap`, una carga, o un proyectil que solo deja una marca (`oe`). |
| `ranged` (a distancia) | Proyectiles; o, sin acercarse, `min ≥ 6` y `max ≥ 12`. |
| `aoe` (en área) | Daño, `throw` o `potion` sobre `@PIR`/`@EIR`… de radio 4–16, un `summon` anidado o un tótem de radio ≥ 3. |
| `grab` (agarre) | `mountmodel`, `stun` o `lock`, o el nombre lleva grab, enclose, absorb o cage. |
| `rear` (da detrás) | Un fieldOfView con rotación 110°–250°, o tail / leap_turn en el nombre. |

Para ver las etiquetas de cada jefe: `python3 tools/bosses/sim_ai.py --smart 1 -v | grep cerebro`.

### 4.2 Cuándo y cómo decide (`SkillBrain.tick`)

- **Cuándo piensa:**
  - Cada `interval()` ticks: de 20 (inteligencia 0) a 4 (inteligencia 1), × 0,85 por debajo del 50 % de vida y × 0,8
    por debajo del 25 %, ÷ el ritmo.
  - Nunca a mitad de un ataque, mientras haya gcd (salvo para encadenar: combos, abajo).
  - Si el jugador **se cura** (come o bebe) y la inteligencia es ≥ 0,5, decide en cuanto quede libre.
- **Qué puede salir:** prueba cada ataque **sin lanzarlo** (`SkillRuntime.usable`). Mira el cooldown, las condiciones
  de la skill y de sus objetivos y el umbral y las condiciones de la línea.
- **Se lo piensa:** con inteligencia ≥ 0,5, un 24 % × (1 − inteligencia) de las veces espera 3–12 ticks antes de atacar
  (no es predecible; con el 100 % no se para nunca).
- **Puntuación** (todo × inteligencia salvo el azar):

| Situación | Suma |
|---|---|
| Encaje del alcance con la distancia | hasta +1,5 |
| El jugador se cura | gap +4, ranged +3 |
| Dispara o tensa el arco | gap +2,2, ranged +1 |
| Lejos (> 10) | gap +1,4, ranged +1,4 |
| Se cubre con escudo | grab +3, aoe +1,2 |
| Está detrás | rear +3 |
| 2 o más jugadores a 7 bloques | aoe +1 +0,7 por jugador |
| Al jugador le queda < 30 % de vida | gap +1, el resto +0,4 |
| Es el que tenía pensado (`planned`, §4.3) | +1,2 |
| Repetir el último ataque | −2,2 (el penúltimo −0,9, los otros recientes −0,3) |
| Aprender | +1,2 × aciertos/usos − 0,35 × fallos seguidos (con 2 usos o más) |
| Azar | entre 0 y 0,35 + 2,6 × (1 − inteligencia) |

- **Lanzar:** sale el que más puntúa, con `exec` de su línea o `callNamed` si es de una `randomskill`.
- **A la vez:** si ese ataque **no pone gcd** (las cabezas de la Hidra), puede salir otro distinto a la vez, hasta 3,
  con una probabilidad de 0,35 + 0,4 × inteligencia.
- **Combos (`SkillBrain.chain`), inteligencia ≥ 0,6:** de cada ataque se sabe, al leerlo, hasta qué tick hace algo
  (`activeEnd`: su último daño, tótem, proyectil, salto… siguiendo sus delay y lo que llama); lo de después es
  recuperación. Pasado ese tick (÷ tempo) + 3, si le quedan más de 6 ticks de gcd, con una probabilidad de
  0,3 + 0,55 × inteligencia **corta la recuperación y lanza otro ataque distinto** que saldría ahora: cancela lo que
  le quedaba al anterior (`SkillRuntime.cancelCast`), le devuelve el movimiento y las animaciones por defecto de antes
  (`restoreMotion`, `restoreDefaults`), quita sus animaciones (`cutSince`) y el nuevo entra suave. No se cortan los
  ataques con agarre ni los que dejan una variable sin duración (la carga del Esqueleto): `noCancel`. Solo encadena
  quien pone gcd y deja recuperación (el Esqueleto mucho; la Gárgola y la Hidra poco; la Abominación y Astwood no:
  marcan sus ataques con variables).
- **Aprender:** los golpes del jefe que dan a un jugador (`SkillRuntime.hurt` → `SkillMobs.landed`) cuentan para el
  ataque en curso (70 ticks).

### 4.3 Movimiento, giro, objetivo y furia

| Qué | Dónde | Cómo |
|---|---|---|
| Plan de movimiento (inteligencia ≥ 0,4) | `SkillBrain.plan` | Devuelve {distancia que busca, rodear, distancia mínima, máxima}. Elige un ataque **de su fase y su postura** (`possible`: su umbral de vida y las condiciones del pack que no son de distancia ni de gcd, como el escudo arriba o abajo del Esqueleto) que tendrá listo pronto y que **hace más que no usa**: así rota todo el repertorio. Se pone a su distancia ideal (`sweet`); lo cambia cada 3–6 s o al usarlo. Antes podía «pensar» en un ataque de otra fase (la plaga de la Abominación, < 50 %) y quedarse quieto. |
| Apartarse y paso atrás | `BossEntity.customServerAiStep`, `backstep` | Si no le sale nada y el jugador está más cerca que el mínimo del ataque pensado, se aparta; con inteligencia ≥ 0,6 y 0,8 bloques por dentro, da un **salto atrás** (0,8 horizontal + 0,3 arriba; cada 3,5–6,5 s). Con poca inteligencia (< 0,4), solo no se pega más que el alcance mínimo de sus ataques. La Abominación pide 3 bloques o más en todos sus ataques: pegado a ella se quedaba gritando. |
| Rodear (inteligencia ≥ 0,6) | `BossEntity.customServerAiStep` | Si no tiene ningún ataque posible, **le rodea** (cambia de lado cada 2–4,5 s). Si está demasiado cerca para lo que quiere hacer, se aparta. |
| Seguir al atacar (inteligencia ≥ 0,4) | `SkillBrain.tracking` | Mientras ataca (`attacking`: gcd o lo que dura el ataque), se gira hacia el jugador a 2 + 8 × inteligencia grados por tick hasta que `lockmodel` lo congela. |
| Girarse parado | `BossEntity.turnBody` | `rde`/`rdu` del pack, acortados con la inteligencia (§3.1). |
| Objetivo (inteligencia ≥ 0,6) | `BossEntity.pickTarget` | Además de la amenaza: + a quien se cura, + a quien le dispara de lejos y, con ≥ 0,8, + a quien está por debajo del 30 % de vida. Solo cambia si el nuevo le compensa bastante. |
| Furia por fases (inteligencia ≥ 0,5) | `BossEntity.rhythm` | Por debajo del 50 % y del 25 % de vida, los cooldowns se acortan otro 12 % × inteligencia en cada fase. |

### 4.4 Ajustarlo

- **Los pesos:** están en `SkillBrain.score`. Si se tocan, se tocan **igual** en `tools/bosses/sim_ai.py`
  (`Brain.score`), que es una copia en Python (también `plan`, `chain`, `attacking`, `possible` y el tempo).
- **Comprobarlo:** `python3 tools/bosses/sim_ai.py --min 8 --dif demencial` (o `--smart X`). Muestra los ataques por
  minuto, cuántos distintos, cuántos repite seguidos, cuántas curas castiga, los combos y los pasos atrás.
- **Si un jefe nuevo no usa un ataque:** con `-v` mira sus etiquetas y su alcance.
  - Si el alcance sale 0–40, el pack no pone distancia; el cerebro lo trata como «a cualquier distancia».
  - Si no aparece, el cerebro no lo ve como ofensivo y sigue el camino del pack.

---

## 5. Grietas, dificultad, ventana y objetos

### 5.1 Grietas

**Ciclo (`Rifts`, `RiftEntity`):** LISTA → (golpe al cristal) DESPIERTA 70 ticks → INCURSIÓN → (cae el jefe) RECARGA
`respawnMin` minutos → LISTA.

- **El jefe:** sale mirando al jugador más cercano, con la vida para los jugadores (en supervivencia) que haya a 32
  bloques.
- **Si se quedan solos:** si nadie (contando creativo) está a 64 bloques durante 30 s, el jefe se va y la grieta vuelve
  a estar lista. Si el jefe se descarga, también.
- **Monedas:** las da `TFEconomy.give` a cada jugador que le hizo daño y sigue cerca (96 bloques).
- **Se guardan** en `<mundo>/tfclient/grietas.json`. La entidad del cristal no se guarda: se crea al cargar el chunk.
- **El cristal** (`RiftRenderer`) se ve del color del jefe y lleva un cartel con el nombre, la dificultad y el estado.

### 5.2 Dificultad

Es un record (`Difficulty`) con estos campos:

| Campo | Clave JSON | Qué es |
|---|---|---|
| `preset` | `preset` | facil, normal, dificil, pesadilla o demencial. |
| `health` | `vida` | Multiplicador de vida. |
| `damage` | `dano` | Multiplicador de daño. |
| `speed` | `velocidad` | Multiplicador de velocidad. |
| `rhythm` | `ritmo` | Divide los cooldowns y el tiempo que piensa el cerebro. |
| `armor` | `armadura` | Puntos de armadura. |
| `perPlayer` | `porJugador` | Vida extra por cada jugador más. |
| `respawnMin` | `recargaMin` | Recarga de la grieta, en minutos. |
| `coins` | `monedas` | Monedas por jugador. |
| `smart` | `inteligencia` | La inteligencia del cerebro (0 a 1). |

- **Dónde se guarda:** la elegida de cada jefe, en `config/tfclient/jefes.json` (`BossConfig`). La de cada grieta va en
  su JSON.
- **Por la red** viaja con `write`/`read`. Si se añade un campo, hay que tocar el constructor, `PRESETS`,
  `sameValues`, `clamped`, `toJson`/`fromJson`, `write`/`read` y `BossScreen.step`.

### 5.3 Ventana `/tf boss`

Es solo para el staff (permiso 2). El dueño la pidió aunque la regla general es solo `/tf web` y `/tf reload`.

- **Ventana:** `BossScreen`. Las cuentas de dónde va cada cosa están en `layout()`, `chipRect()`, `stepRect()` y
  `riftButtons()`.
- **Si cambias la disposición**, cámbiala igual en `tools/bosses/sim_gui.py` y ejecútalo: tiene que salir OK a los
  8 tamaños. Con `--png carpeta` dibuja cada tamaño.
- **Mensajes (`BossNet`):** `Open` va del servidor al cliente (jefes, dificultad elegida, grietas y aviso). `Action` va
  del cliente al servidor, con estos tipos:

| Acción | Qué hace |
|---|---|
| `SAVE` | Guarda la dificultad del jefe. |
| `PLACE` | Pone una grieta donde está el staff. |
| `TEST` | Saca el jefe delante para probarlo. |
| `RIFT_TP` | Lleva a la grieta. |
| `RIFT_SAVE` | Guarda el jefe y la dificultad de una grieta. |
| `RIFT_REMOVE` | Quita la grieta. |
| `RIFT_RESET` | La deja lista otra vez. |
| `REFRESH` | Pide la lista al día. |
| `CLEAR_TESTS` | Quita los jefes de prueba (`BossEntity.isTest`: no son de una grieta). |

### 5.4 Objetos

Los objetos de los packs (la Guja, la Égida y los Cuernos del Esqueleto) los registra `TFBosses` como
`tfclient:jefe_<jefe>_<objeto>`. Llevan sus skills del pack (`BossItems`):

- **El arma:** `~onSwing` al golpear con el golpe cargado (paquete `Swing` desde `BossClient`).
- **El escudo:** `~onTimer` mientras te cubres.

`BossWorld` hace lo que el motor pide al mundo: los esbirros (hasta 24 por jefe), los objetos que caen y los asientos
de los agarres (`SeatEntity`; con `BossEvents` no se sale con mayúsculas).

---

## 6. Herramientas de comprobación (sin abrir el juego)

| Comando | Para qué |
|---|---|
| `python3 tools/bosses/sim_ai.py [jefe] --min 8 [--seed N] [--dif P] [--smart X] [-v] [--antes]` | La IA. El jugador simulado se mueve, pega, sale despedido, a veces está detrás, se cura, se cubre y dispara. `--dif` pone una dificultad de `/tf boss` (su inteligencia, tempo y ritmo), `--smart` solo la inteligencia y `--antes` reproduce el motor de la 1.3.75 para comparar. Saca los ataques por minuto, cuántos distintos de los que tiene, los que no salieron, cuántos repite, las curas castigadas, los combos y los pasos atrás. Con `-v`, cada ataque y cada combo. |
| `python3 tools/bosses/audit.py [jefe ...] [--sin-avisos]` | El auditor (también al final de `build_bosses.py`). Tiene que dar **0 problemas** en cada jefe. Sabe solo lo que tiene el motor: lee las etiquetas `case` de `SkillRuntime.apply`, `SkillConds.test` y `SkillTargets.targets`, los `run(h, "...")` de `SkillMobs` (disparadores) y qué `case` no hacen nada. Los **avisos** (`·`) no cuentan pero dicen lo que no se verá igual (hoy: `segment`, las capas y colas rígidas). |
| `python3 tools/bosses/audit.py --prueba` | Se prueba a sí mismo (16 errores en la 1.3.80, también de modelos: UV fuera de la textura, pista de un hueso que no existe, textura que falta, IK rota). Antes: mete 11 errores a propósito en una copia del Esqueleto (mecánica, condición, objetivo, skill, animación, hueso, sonido y modelo que no existen, un disparador que no salta, un `~onAttack` que hace algo y una mecánica «de mentira» sin su porqué) y tiene que verlos todos. Si tocas el auditor, pásala. |
| `python3 tools/bosses/audit.py --cobertura` | El catálogo de MythicMobs 5 / ModelEngine 4 (`tools/bosses/mythic_ref.py`, con alias; `*` = habitual en jefes) contra el motor: cuántas hay, cuáles no hacen nada y cuáles faltan. En la 1.3.79: mecánicas 110/192 (todas las `*` salvo `mount`, `dismount` y `segment`), condiciones 81/88, objetivos 42/47, disparadores 11/26. |
| `tools/vfx_render.py` (`model_tris`, `raster`, `B.pose`) | Dibujar un modelo de jefe con una animación y unos huesos visibles (ver §7, paso 4). |
| `python3 tools/bosses/sim_gui.py [--png carpeta]` | La ventana `/tf boss` a 8 tamaños. |
| `python3 tools/bosses/sim_physics.py <modelo> [carpeta] [--anim idle] [--giro 120] [--anda 0.3] [--sin]` | Colas y segmentos (lo mismo que VfxPhysics): gira y anda el jefe, dice cuánto se aparta cada eslabón del de delante (no debe pasar de su límite) y dibuja fotogramas desde arriba y de lado. Usa la configuración `segment{}` del pack. |
| `python3 tools/bosses/test_convert.py` | Pruebas del conversor con .bbmodel hechos al vuelo: etiquetas, bezier, Molang, antes/después, escalón, Box UV, miembros, `b_` y la IK llegando a su objetivo. Tiene que decir «todo bien». |
| `python3 tools/bosses/rift_model.py --preview carpeta` | Imágenes del cristal. |

**Referencia** (1.3.78, `--min 8`, ataques por minuto; entre paréntesis, el motor de la 1.3.77 con la misma
inteligencia):

| Jefe | Normal | Demencial | Distintos |
|---|---|---|---|
| Gárgola | 20,6 (18,9) | 27,5 (19,0) | 5/5 |
| Hidra | 30,6 (29,4) | 36,9 (33,4) | 13–14/15 (la rotura de cabezas sale por debajo del 50 %) |
| Abominación | 13,5 (12,2) | 18,8 (13,8) | 5–6/7 (la plaga, < 50 %; el agarre depende de que el tentáculo pille) |
| Esqueleto | 13,5 (13,4) | 19,5 (13,2), 42 combos | 3 fases; en 30 min en Demencial, 25/26 con 77 combos |
| Astwood | 23,2 (24,5) | 29,1 (22,5) | 12/12 |

(El simulador de la 1.3.77 no aplicaba el ritmo de la dificultad: parte de la subida en Demencial es eso.)

**Lo que el simulador no ve:** dónde caen de verdad los golpes (los tótems en los huesos), la física, el pathfinding y lo
que se ve. Eso solo se comprueba en el juego. Pídeselo al dueño, en supervivencia, y **mide sus capturas con PIL**
antes de dar algo por arreglado.

---

## 7. Añadir un jefe nuevo (pasos)

1. Descomprime el pack (fuera del repo).
2. Añádelo a `JEFES` (en `build_bosses.py`) y a `tools/bosses/es.json`.
3. Ejecuta `build_bosses.py`. Si dice «falta skill X», complétala en `FIXES` imitando las parecidas del pack. **El
   auditor del final tiene que dar 0 problemas** (y lee sus avisos): si sale una mecánica, condición, objetivo o
   disparador sin implementar, o algo que el motor acepta «de mentira», impleméntalo (paso 6); si sale un hueso o una animación que no existen, mira el pack (puede ser una errata suya: si ModelEngine
   tampoco la encuentra, decide con el dueño si se corrige en `FIXES`).
4. Dibuja sus modelos **como aparecen**: la visibilidad inicial (todo visible: el ojo de Blockbench no cuenta) más
   los `partvis` de su `~onSpawn` con las reglas de ModelEngine (§3.3), y después de cada `partvis{v=true}` del
   pack (despertar, fases). Busca piezas fuera de sitio (la espada de la muerte del Esqueleto en la pierna) y piezas
   que falten (su escudo). Ajusta su `preview_hide` (la vista previa de `/tf boss` lo enseña como en la pelea).
5. Ejecuta `python3 tools/bosses/sim_ai.py <id> --min 8` y con `--smart 1 -v`:
   - ¿salen todos sus ataques?
   - ¿las etiquetas del cerebro tienen sentido?
   - Si algo no sale nunca, busca por qué con `-v` (condiciones, variables, fases).
6. Mecánicas, condiciones, objetivos o disparadores nuevos: el auditor los marca antes del juego (y en el juego salen
   en el log como «TF Skills: …»). **Nunca los quites del pack**: impleméntalos con la semántica de MythicMobs (mira
   cómo se hicieron los de la 1.3.79), cópialos en `sim_ai.py`, añádelos a `mythic_ref.py` si no estaban y comprueba
   que los demás jefes dan las mismas cifras en `sim_ai.py` (misma `--seed`, antes y después). Si algo de verdad no
   importa, déjalo «sin efecto» y explícalo en `STUB_OK`. Van en `SkillRuntime`/`SkillConds`/`SkillTargets`/
   `SkillMobs`, solo para jefes si cambia algo de las clases (`ctx.host != null`).
7. Antes de publicar: `audit.py` con 0 problemas en TODOS los jefes (ahora también modelos y texturas),
   `audit.py --prueba` entera, `test_convert.py` «todo bien», `sim_physics.py <modelo>` en los que tengan
   `seg_`/`tl_`/`jig_` (que se doble sin pasarse del límite) y `sim_ai.py` de los demás jefes igual que antes (si
   cambia, que sea a propósito y explicado).
8. Sube la versión (`mod_version` y `TFClient.VERSION`), apunta la entrega en `HANDOFF.md`, haz push a la rama
   (**compila SIEMPRE en GitHub Actions**, nunca con `./gradlew` en el contenedor: orden del dueño), PR y fusión.
   Comprueba la Release.

---

## 8. Si el dueño dice… (síntomas → dónde mirar)

| Síntoma | Causa probable | Dónde |
|---|---|---|
| «Solo hace un ataque» | Cooldowns o variables que no caducan; una condición que nunca se cumple; el cerebro no ve el ataque como ofensivo | `sim_ai.py <id> -v`, `SkillRuntime.runMeta`/`putVar`, `SkillBrain.build` |
| «Se queda en el aire / en una pose rara» | Una animación que no se sustituye (prioridades) o un `hold` que nadie quita | `VfxLayers.play/stop`, los `state{p=..;r=true}` del pack |
| «Pega al aire / no le da» | Hueso mal (`@modelpart`), pose del servidor distinta, el tótem a otra altura | `SkillModels.modelPart/pose`, `SkillMovers.totem` (`yo`) |
| «No se gira / ataca de lado» | `lookYaw`/`bodyYaw`, `bodyrotation` del pack, `lockmodel` que no se suelta | `BossEntity.turnBody` |
| «No anda» | `MovementSpeed` 0 sin `setspeed`, navegación de un mob muy grande, la correa | `BossEntity.applySpeed`, `customServerAiStep` |
| «No me ataca» | Estás en creativo; la `FollowRange` del pack; fuera de la correa | `BossEntity.pickTarget` |
| «Es tonto / muy predecible» | La inteligencia de su dificultad; los pesos del cerebro | `/tf boss` (fila Inteligencia), `SkillBrain.score` |
| «Va demasiado rápido / corta animaciones» | El ritmo o la furia; el tempo; un combo que corta | `Difficulty.rhythm`, `BossEntity.rhythm`/`tempo`, `SkillBrain.chain` (`noCancel`) |
| «Lento al girar» | `bodyrotation{rde;rdu}` del pack | `BossEntity.turnBody` (se acortan con la inteligencia) |
| «Se queda quieto delante de mí / gritando» | Sus ataques piden más distancia que a la que se pone; un `~onEnterCombat` que se repite | `SkillBrain.plan` (mínimo, `possible`), `BossEntity.backstep`, `SkillMobs.tick` |
| «Solo me tira 2 ataques» | La fase del pack (el Esqueleto, con el escudo arriba, solo tiene golpe y aplastamiento de escudo de cerca: hay que romperlo o ir a por su espalda); o el plan no rota | `sim_ai.py <id> -v`, `SkillBrain.plan` |
| «Tira algo que no hace nada / cae al suelo» | El objetivo del proyectil (sus propios esbirros), hacia dónde apunta, la inercia | `SkillTargets.entities`, `SkillMovers.projectile` |
| «Es inmortal / no le baja la vida» | Un `cancelevent`/`modifydamage` en `~onDamaged` (fase invulnerable del pack), `setinvulnerable` sin quitar | `sim_ai.py <id>` («golpes anulados por el pack»), `BossEntity.hurt` |
| «Se va a una esquina / deja de perseguir» | Un `goto` del pack (dura hasta llegar o 10 s) | `BossEntity.goTo`, `customServerAiStep` |
| «Va siempre a por el mismo» | `threat{mode=FORCE}`/`rally` del pack o la tabla de amenaza | `BossEntity.threat`/`pickTarget` |
| «La cola / capa va rígida o se dobla raro» | Su `segment{}` (angle, extend), la orientación del trozo visible (`decideOutgoing`) | `sim_physics.py <modelo>`, `VfxPhysics` |
| «Se ve un cubo que no debería» | Un hueso de comportamiento que el conversor no reconoció (`b_`, `l_`, `ih_`...) | Los «problemas» de `build_bosses.py`, `vfx_bb.bone_tag` |
| «Parpadea en rojo» / «no parpadea» | `model{d=false}` del pack (damagetint) | `SkillModels.modelOptions`, `Actor.damageTint` |
| «La animación salta o se queda quieta» | Bezier/Molang del .bbmodel (hay que volver a convertir con el conversor de la 1.3.80) | `vfx_bb.needs_bake/bake_channel`, `tools/molang.py` |
| «Tiene una pieza fuera de sitio / le falta una pieza» | `partvis` (defaults de ModelEngine), el ojo de Blockbench al convertir | `SkillModels.partVis`, `tools/vfx_bb.py` (`boss`), §7 paso 4 |

---

## 9. Ideas pendientes (si el dueño pide más)

- **Volver a convertir los 5 jefes** con el conversor de la 1.3.80 (bezier, Molang y antes/después horneados): hace
  falta que el dueño vuelva a mandar sus zips (no están en el repo). `build_bosses.py` dirá si había Molang que no
  entiende.
- De ModelEngine falta: `leash`/`leashself` (la cuerda), `move`, `changeparent`, `submodel`, `animatemodel`,
  `rootmotion`, `swapentity`, `petrify`, `pairmodel`, `modeldisguise`; `jig[repeat]` con varios eslabones.
  `--cobertura` lo lista; el auditor normal lo marca en cuanto un pack lo use.
- De MythicMobs: `bloodyscreen`, `blackscreen`, `bossborder`, `skybox`, `particleequation`, `chainmissile` y lo que
  cambia el mundo (`breakblock`, `fill`, `time`, `weather`...).

- **Fintas y ataques retrasados de verdad:** ralentizar el principio de una animación y retrasar a la vez sus golpes.
  Hoy solo «se lo piensa» antes de empezar.
- **Esquivas** cuando el jugador carga un golpe fuerte. Hoy hay paso atrás (`backstep`) solo para hacer sitio, sin
  animación propia: los packs no la traen, salvo el `swoop_back` de la Gárgola.
- **Combos en los packs sin gcd** (Abominación, Astwood): marcan «atacando» con variables; habría que saber qué
  variable es de cada ataque para poder cortarlo.
- **Memoria entre peleas:** qué ataques esquiva cada jugador, guardado por grieta.
- **Coordinación de esbirros:** rodear, cortar la retirada.
- **Ver en el juego:** un modo depuración para el staff que muestre encima del jefe el ataque elegido y su puntuación.

---

## 10. Mohist (el servidor)

El servidor es **Mohist 1.20.1** (Forge + plugins de Bukkit). La vía elegida, y por qué:

- **Motor propio dentro de TF Client (lo que hay)**: los packs de MythicMobs + ModelEngine se convierten fuera
  (`build_bosses.py`) y el mod los ejecuta y los dibuja. No necesita ningún plugin, no depende de NMS de Bukkit, y
  los jugadores ya llevan TF Client, que dibuja los modelos tal cual. Es lo más fiable en un servidor híbrido.
- **Plugins MythicMobs + ModelEngine en Mohist (descartado)**: ModelEngine (y ItemsAdder) no dan soporte a servidores
  híbridos: van por paquetes y NMS de Spigot, que Mohist cambia. MythicMobs a veces va, pero sin ModelEngine los
  jefes no se ven. Además se dibujarían dos veces si conviviera con el motor. **No instales ModelEngine para estos
  jefes.** Si el servidor tiene MythicMobs para otros mobs, no choca: los jefes del mod no lo usan.

**Qué mirar en el servidor** (no se puede probar desde aquí):

- Mohist aplica las reglas de Spigot también a las entidades de mods. Las nuestras piden 160 bloques de seguimiento
  (`clientTrackingRange(10)` en `TFBosses`), pero `spigot.yml` puede recortarlo: si un jefe grande (la Hidra) aparece o
  desaparece de golpe a unos 48-64 bloques, sube en `spigot.yml` → `world-settings.default.entity-tracking-range` los
  valores `monsters` y `other` (por ejemplo a 128).
- `entity-activation-range` de Spigot deja «dormidas» las entidades sin jugadores cerca. Un jefe **con objetivo** no se
  duerme (Spigot lo exime), pero si se le ve quieto con jugadores a más de 32 bloques, sube `monsters`/`animals` (por
  ejemplo a 64).
- Los plugins que borran entidades (ClearLagg y parecidos) deben ignorar `tfclient:jefe`, `tfclient:esbirro`,
  `tfclient:grieta` y `tfclient:asiento`.
- Las protecciones (WorldGuard, GriefPrevention...) ven el daño de los jefes como daño de un mob (sale de
  `SkillRuntime.hurt` con `mobAttack` del jefe): en una zona sin daño de mobs, los jefes no harán daño, y es lo
  esperado. Las explosiones de los jefes (`explosion`) no rompen bloques y los bloques que caen no se quedan.

