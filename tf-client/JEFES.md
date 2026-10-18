# Jefes de incursión de TF Client: documentación técnica completa

Este documento sirve para que otra IA (o persona) retome los jefes sin tener que redescubrir nada. Léelo después del
CHECKPOINT de `HANDOFF.md`, porque las reglas del dueño están allí (español, nada de subagentes, compilar con GitHub
Actions, publicar como Release y sin abrir nunca el juego ni un servidor).

- **Estado:** TF Client **1.3.83**.
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
  - 1.3.81: los 5 jefes **reconvertidos** (6 animaciones cambian por bezier; ningún pack trae Molang); **cabezas como
    ModelEngine 4** (globales y sin acumularse, §3.6); **el resto de ModelEngine 4** (§3.6): `leash`/`leashself`,
    cabezas/colas/física/IK **también en el servidor** (`vfx/VfxRig`), `animatemodel`, `rootmotion`, `changeparent`,
    `submodel`, `swapentity`, `petrify`, `pairmodel`, `modeldisguise`, `move`, `syncyaw`, `jig[repeat=N]`, la caja de
    la entidad con el modelo (`hitboxscale`…), skins slim/base64/de jugadores desconectados y `setmodeltag` con
    alineación; y **lo que faltaba de MythicMobs sin tocar el mundo** (§3.2 y §3.7): `~onCombat` (las líneas sin
    disparador), `~onSkillDamage`, `~onShoot`/`~onBowHit`, `~onProjectileHit`/`Land`, `~onDespawn`, `~onHear`…;
    `@Cone`, `@Sphere`, `@Rectangle`, `@BlocksInRadius`, `@BlocksNearOrigin`; `score`, `globalscore`, `wearing`,
    `haspermission`… de verdad; pantallas (`blackscreen`, `bloodyscreen`, `bossborder`, `skybox`, `sendtoast`),
    `enderbeam`, `hologram`, `chainmissile`, `switch`, `variablemath`, `consume`, `swap`, `teleportin`…
  - 1.3.82: la IA de MythicMobs (AIGoalSelectors/AITargetSelectors, el golpe de Minecraft) y 12 jefes de LittleRoom
    más (§3.8).
  - 1.3.83: lo que pidió el dueño al probarlos (§3.9): el alcance real de cada golpe, girarse antes de golpear, el
    cuerpo hacia donde anda, sin objetos, la barra se va al morir, **música de la pelea** y **muerte épica** (rayos
    como el dragón del End).
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
| `vfx/VfxRig` | Lo que se mueve solo en un modelo (cabezas, colas/segmentos, física `jig_`, IK) con el mismo código en el cliente y en el servidor (§3.6). |
| `skills/SkillMore`, `skills/SkillScreen` | Las mecánicas de MythicMobs de la 1.3.81 (§3.7) y lo que ponen en la pantalla del jugador (pantalla negra, borde rojo, aviso de logro). |

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
| `bosses/grieta3.json`, `bosses/grieta3_madre.json` | La grieta 3.0 (1.3.88) y la grieta madre (1.3.89): geometría facetada de `tools/bosses/grieta3.py`, sin texturas (shaders `rift_obsidian` y `rift_fx`, §3.13 y §3.14). La pasada completa de `build_bosses.py` las rehace solas. |
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
  pegan más de lo que sus ataques permiten (desde la 1.3.84 se queda quieto: sin paso atrás) y rodear. La correa con
  su grieta es de 40 bloques (§3.10).
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
| (ninguno) = `oncombat` | **Como MythicMobs**: una línea sin `~disparador` es `~onCombat`, que salta al aparecer, al recibir un golpe (con el mismo golpe: puede anularlo) y al morir (y al pegar cuerpo a cuerpo, que los jefes no hacen). Hasta la 1.3.80 se trataba como `~onSpawn`; ningún pack de los 5 tiene líneas sin disparador. En un efecto (actor), que ni pega ni recibe, es lo mismo que aparecer. |
| `onspawn` | El primer tick. |
| `onspawnorload`, `onready` | Al aparecer (los jefes no se guardan: nunca «cargan»). `onready` («sale de un generador»): solo el jefe, que sale de su grieta; sus esbirros invocados, no. |
| `ontimer:N` | Cada N ticks, en el orden del pack. |
| `onentercombat` | Al tener objetivo, **una vez por pelea**: si lo pierde un momento no vuelve a saltar (salvo tras 10 s sin objetivo). La Abominación volvía a gritar cada vez. |
| `ondropcombat` | Tras 10 s sin objetivo después de pelear (la misma regla que la entrada). @trigger: nadie. |
| `onchangetarget` | Pasa de un objetivo a otro. @trigger = el nuevo. |
| `ondamaged` | Al recibir un golpe, **antes de que entre** (como el `EntityDamageEvent` de MythicMobs): `damage-amount` (golpe en la escala del pack), `damage-cause`, la vida de **antes** para sus umbrales (`<50%`), y sus skills pueden anularlo (`cancelevent`) o cambiarlo (`modifydamage`). Solo cuenta lo que hagan en el momento (lo que vaya con `delay` llega tarde, como en MythicMobs). |
| `ondeath` | Al morir. |
| `onkill` / `onplayerkill` | Mata a algo / a un jugador con un golpe o una skill (`BossEntity.killedEntity`). @trigger = la víctima. |
| `oninteract` | Clic derecho de un jugador (`BossEntity.mobInteract`). En los packs de ahora solo lleva `cancelevent` (no hace nada: no hay golpe que anular). |
| `onsignal:X` | Al recibir `signal{s=X}`. |
| `onskilldamage` | Hace daño con una mecánica (`damage`, un proyectil suyo…; `SkillRuntime.hurt`). @trigger = a quien ha dado. Uno que dentro hace daño no se vuelve a disparar a sí mismo. |
| `onshoot` / `onbowhit` | `shoot`/`shootfireball` (en MythicMobs son proyectiles de Minecraft): `onshoot` al disparar (@trigger = él, `bow-tension` = 1) y `onbowhit` al dar a alguien (@trigger = a quien da). |
| `onprojectilehit` / `onprojectileland` | Sus `projectile`/`missile` dan a alguien (@trigger = él, una vez por cada uno) / chocan con un bloque (@origin = donde caen). |
| `ondespawn` | Se va sin morir (se cierra la grieta): `BossEntity.remove`. |
| `ondismounted` | Se baja (o le bajan) de lo que montaba (`BossEvents.onDismount`). @trigger = la montura. |
| `onhear` | Con `Hearing: Enabled: true` en el mob: oye como el Warden (la wiki lo dice así) las vibraciones de Minecraft (`VanillaGameEvent` del tag `vibrations`) a 16 bloques: no a quien va agachado si ese evento se calla así, ni lo amortiguado (lana), ni a sí mismo; tarda un tick por bloque en llegarle, mientras viaja no oye otra y después 2 s de calma. `volume` = distancia (1–15), `sound-type` = el evento (`STEP`, `HIT_GROUND`…), @trigger = quien la hizo, @origin = dónde. |
| `onexplode`, `onprime`, `oncreepercharge`, `onteleport`, `ontame`, `onbreed`, `ontrade`, `onbucket` | Son de lo que hace por sí mismo su **tipo** de Minecraft (un creeper explota, un enderman se teletransporta…), que `BossEntity` no copia. Si el `Type` del pack no lo hace, tampoco saltan en MythicMobs (el auditor lo dice como aviso); si lo hace, es un problema (§11.3). |
| `onchangeworld` | No salta: los jefes no pasan por portales (`BossEntity.canChangeDimensions = false`; su copia en el otro mundo no tendría su programa). |
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
| Una línea **sin disparador** es `~onCombat` (no `~onSpawn`). | `SkillMobs.run`, `audit.py`, `sim_ai.py` | Wiki de MythicMobs («Skills without a trigger default to onCombat»). |
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

### 3.6 ModelEngine 4 en el motor (1.3.80 y 1.3.81)

Todo según la wiki de ModelEngine («Bone Behaviors» y «Skills/Mechanics»). Lo visual lo hace el cliente (`SkillClient.Actor`)
con lo que le manda el servidor en «props» del actor del modelo (se guardan y se repiten a quien llega tarde;
`SkillActor.prop`, `PER_MODEL`). Las mecánicas del servidor están en `skills/SkillMeg.java`.

**Etiquetas de hueso** (las lee `tools/vfx_bb.py`, `bone_tag`; en el JSON del modelo van en `beh` y `bp`):

| Etiqueta | Qué hace aquí |
|---|---|
| `h_`, `hi_`, `h[yaw;pitch;minyaw;maxyaw;minpitch;maxpitch;speed]_` | Cabeza (1.3.81, como ModelEngine 4 y BetterModel, que lo copia): el giro de la mirada es **global** (en los ejes del modelo, sobre su pivote) y **no se acumula** (una cabeza dentro de otra no gira el doble: `hi_chest` + `hi_head` de la Gárgola miran una vez). Un hijo **sin etiqueta** de un `h_` va con él pero **no gira** («los hijos necesitan su propio h_»); con `hi_`, todo lo que cuelga es cabeza (el conversor lo marca y hereda sus límites). Cada cabeza con sus límites: un `hi[yaw=30]_pecho` con un `h[yaw=90]_cabeza` dentro gira 30° el pecho y la cabeza 60° más (`VfxPose.build`, `VfxModel.headLimits`); velocidad máxima por tick en `VfxRig.look`. Las clases siguen con la regla vieja (`VfxPose.compute`). |
| `p_`, `mount` | Asiento (agarres). |
| `ob_` / `b_` | Caja de golpe que gira con su hueso / recta del mundo que solo le sigue (`VfxHitboxes`; `b_` debe ser cuadrada en X/Z). |
| `seg_`, `segf_`, `tl_`, `tlf_` | Cola o segmento (solo sin cubos, pivote atrás; `f` = delante): `vfx/VfxPhysics` (abajo). Conservan el nombre entero (`tl_tail_2` y `tail_2` conviven en la Gárgola). |
| `jig_`, `jig[stiff;damp;grav;angle;repeat]_` | Física: muelle amortiguado con gravedad y ángulo máximo (`VfxPhysics.jiggle`). `repeat=N` (1.3.81): el conversor parte el hueso en N eslabones encadenados (`hueso`, `hueso_2`…, cada uno con su trozo) y lo que colgaba va tras el último. |
| `ik[minx..maxz]_` | Límites de esa articulación en las cadenas IK. |
| `tag_` | Hueso de nombre: `setmodeltag` o `model{nametag=hueso}` ponen el texto (mirando a la cámara). |
| `l_` (sin cubos) | Correa: `leash`/`leashself` la dibujan (1.3.81). Con cubos es un hueso normal («l_hand» = mano izquierda). |
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

**Lo de la 1.3.81** (wiki de ModelEngine «Skills/Mechanics» y «Model Options»):

- **Cabezas, colas, física e IK también en el servidor** (`vfx/VfxRig`, el mismo código que el cliente): `@modelpart`,
  los tótems de los golpes y las cajas `ob_` que cuelgan de una cola, una cabeza o una cadena IK van donde se ven
  (antes, en la pose animada sin doblar). Lo que cambia la pose (`headtgt`, `segment`, `ik`, `jiggleimpulse`…) llega al
  servidor por los mismos «props» (`SkillActor.rig`, `RIG_KEYS`) y el disfraz se posa cada tick si tiene algo que
  dependa del tiempo (`rig.stateful()`).
- **`leash{m;p;leash}` @objetivo** y **`leashself{m;s;d}`**: correa como la de Minecraft (`LeashKnotRenderer`/
  `MobRenderer.renderLeash`, misma cuenta y colores) desde un hueso `l_` hasta una entidad o hasta otro hueso `l_`
  del mismo modelo. Como ModelEngine, solo se ve: no tira de nadie. `leash=false` / `leashself` sin `d` la quitan.
- **`animatemodel{m;p;em;position;rotation;scale;r;o;copy;rm}`**: mueve un hueso a mano (píxeles y grados de
  Blockbench; `r` suma, `o` tapa sus animaciones, `copy` parte de su forma del modelo, `rm=GLOBAL` gira en los ejes del
  modelo; `VfxLayers.manual`, `VfxPose.build(..., global)`).
- **`rootmotion{w;bw;ov}`** + **`model{rootbone}`**: la entidad anda lo que sus animaciones mueven el hueso raíz (en
  horizontal, con su giro y escala; `SkillModels.rootStep` cada tick) y el modelo deja de mover ese hueso (no se ve
  dos veces). `w`: peso con sus ataques; `bw`: con el reposo y el andar.
- **`changeparent{m;p;c}`** (un hueso pasa a colgar de otro) y **`submodel{m;p;sp;pfx;r}`** (copia una pieza bajo
  otro hueso, o la quita con `r=true`: ni se ve, ni caja, ni asiento): `VfxModel.derive` rehace el modelo igual en el
  servidor y en el cliente (`SkillModels.modelOf`; las capas se reenganchan con `VfxLayers.rebind`).
- **`swapentity{m;hitbox;invisible;step}`** (el modelo pasa a otra entidad), **`petrify{m}`** (estatua quieta del
  modelo tal como está: pose, piezas, colores; se va al minuto como los efectos), **`pairmodel{hidden;remove}`** (a
  esos jugadores se les esconde o se les enseña siempre), **`modeldisguise{m;seeself}`/`modelundisguise`** (un modelo
  encima de un jugador), **`move{x;y;z;speed;global}`** (anda ese tick, con su escalón; `BossEntity.move`) y
  **`syncyaw{t;b}`** (un giro de la entidad pasa a valer otro).
- **`model{}`**: `hitbox` (su caja es la del hueso `hitbox` o la de su tipo), `hitboxscale` (y `scale{hitbox=true}`:
  `BossEntity.getDimensions` con `HB_SCALE`), `showhitbox` (false = no se le apunta), `syncbody`, `viewrange`/`radius`
  (hasta dónde se ve), `renderfire`/`forcefire` (el fuego de Minecraft), `showshadow` y el hueso `shadow` (diámetro de
  la sombra; `BossRenderer.shadowRadius`), `seeself`.
- **Skins**: brazos *slim* (3 px; el conversor guarda las dos versiones, `sq`), `modelplayerskin{skin=<base64>}` y por
  nombre o uuid de un jugador **desconectado** (se baja una vez de Mojang, en otro hilo).
- **`setmodeltag{...;alignment;linewidth;billboard}`**: texto alineado, con saltos de línea y fijo o mirando a la cámara.

**Pendiente de decidir con el dueño**: la altura de los ojos de ModelEngine es el pivote del hueso `hitbox` (la wiki);
aquí la entidad sigue con la de su tipo de Minecraft (cambiarla movería de dónde salen proyectiles y miradas).

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

### 3.7 MythicMobs que se añadió en la 1.3.81 (sin tocar el mundo)

Todo con la wiki de MythicMobs (https://wiki.mythiccraft.io/mythicmobs/Skills/...). Los disparadores están en §3.2.
Donde la wiki no dice cómo es algo, se eligió lo más parecido a Minecraft y está en §11.4.

**Objetivos** (`SkillTargets`, igual en `tools/skills/sim.py`, `shape_points`; como mucho 4096 puntos):

| Objetivo | Qué da |
|---|---|
| `@Cone{angle=90;range=16;points;slices;minpoints=1;rotation;yoffset;exact}` | Puntos en un cono **horizontal** delante de quien lanza (no se inclina). Va en `slices` capas (floor(range)); cada una con su parte de `points` (angle·range·0,1 = 144 por defecto) según lo que ocupa, y al menos `minpoints`; al azar o, con `exact`, repartidos por el borde de cada capa. |
| `@Sphere{radius=5;points=10;yoffset;exact}` | Puntos en la superficie de una esfera alrededor de quien lanza (al azar, o repartidos por igual con `exact`). |
| `@Rectangle{x;y;z;xoffset;yoffset;zoffset;points=10;filled;outline;rotation=a,b,c;fromorigin}` (`@cube`, `@cuboid`) | Una caja centrada en quien lanza (o el origen): sus caras, sus aristas (`outline`) o llena (`filled`), con `points` por arista; `rotation` en grados x, y, z (como `Vec3.xRot/yRot/zRot`). |
| `@BlocksInRadius{radius=2;radiusy;shape=SPHERE;noise=0;noair=true;onlyair;nearorigin}` (`@BIR`) | Los bloques (su esquina) alrededor de cada objetivo heredado (sin ninguno, de quien disparó la skill; `nearorigin`: del origen). Sin aire o solo aire; `noise` = la parte que se salta al azar. Solo chunks cargados, radio hasta 16. |
| `@BlocksNearOrigin{...}` (`@BNO`) | Lo mismo alrededor del origen. |

**Condiciones** (`SkillConds`): `score{objective;entry;value}` (la puntuación del marcador del servidor; sin `entry`, la
del objetivo; sin puntuación, 0), `globalscore{objective;value}` (la entrada `__GLOBAL__`), `itemrecharging` (el
objeto de la mano en enfriamiento), `mobsinchunk{amount}` (seres vivos que no son jugadores y efectos del pack en
ese chunk), `playerkills{kills}` (jugadores que ha matado ese mob) y **de verdad** `wearing{armorslot;material}`
(material de Minecraft u objeto del pack; `iswearing`, `wielding`) y `haspermission{permission}` (el permiso de
Bukkit en Mohist, por reflexión; sin Bukkit, que sea op). Hasta la 1.3.80 estas dos se daban por cumplidas.

**Mecánicas** (`SkillMore`, salvo donde se dice):

| Mecánica | Qué hace aquí |
|---|---|
| `consume{damage;heal}` | Daña (como `damage`) y quien lanza se cura `heal` por cada uno al que hizo daño (en un jefe, en su escala). |
| `oxygen{amount}`, `shieldbreak{duration}`, `setgliding{gliding}`, `setpose{pose}`, `setmobcolor{color}`, `setpathfindingmalus{malus;value}` | Aire del jugador; romper la guardia del escudo (enfriamiento y su sonido); planear con élitros (solo jugadores en el aire); la postura de Minecraft; el color de ovejas, shulkers y collares; lo que le cuesta a un mob pasar por un tipo de sitio. |
| `blackscreen{duration}` | Pantalla negra (`SkillScreen`), en tramos de 10 ticks (es un aura de intervalo 10). |
| `bloodyscreen{duration=20;cancel}` | El borde rojo de «cerca del borde del mundo», dibujado como `Gui.renderVignette` (solo con gráficos detallados, como en Minecraft). |
| `sendtoast{icon;message;frame}` | El aviso de logro de Minecraft (`AdvancementToast`) con ese icono (material u objeto del pack) y texto. |
| `skybox{skybox}` | Cielo de lluvia solo para ese jugador (el «nivel de lluvia» de Minecraft por paquete; >1 lo oscurece más); 0 = el de verdad. Dura hasta que cambie el tiempo del mundo. |
| `bossborder{radius=32}` | Un borde del mundo de Minecraft **solo para ese jugador** (paquete) alrededor de quien lanza: su cliente no le deja salir. Se le quita (vuelve el de verdad) al morir o irse quien lo puso o si cambia de mundo (`SkillMore.tick`). El borde real del servidor no cambia. |
| `enderbeam{duration=60;yoffset}` | Un cristal del End con su rayo hacia el objetivo, **solo dibujado** (`SkillClient.renderBeam`): el de MythicMobs es un cristal de verdad que puede explotar y romper bloques. |
| `hologram{text;stay=100}` | Un texto flotando (un efecto con texto, como los `text_display`). |
| `blockunmask{radius;shape}` | Deshace los `blockmask` a ese radio (en el cliente). |
| `summonareaeffectcloud{...}` | La nube de una poción persistente de Minecraft (partícula, poción, nivel, duración, radio y lo que encoge). |
| `swap`, `teleportin{vector;yaw;targetasorigin}` | Cambiarse el sitio con el objetivo; llevar al objetivo a un punto según hacia dónde mira quien lanza (x delante, y arriba, z a la derecha). |
| `resetai` | Vuelve a la IA de su tipo: un jefe con `AIGoalSelectors: clear` vuelve a perseguir y mirar (`BossEntity.resetAi`). |
| `randommessage{messages}` | Un mensaje al azar de la lista a cada jugador. |
| `variablemath{var;equation}` | La variable pasa a valer la cuenta, con `x` = lo que vale ahora (`SkillVars.math`, como la librería Crunch: `+ - * / % ^`, comparaciones, `& | !`, `sin`…`max`). |
| `switch{condition;cases;unique}` | `tools/skills/compile.py` convierte cada `case X=[ ... ]` en una skill cuya condición de objetivo es la del switch con `<case>` = X; en el juego, con cada objetivo, el primer caso que cumple (todos con `unique=false`), o `DEFAULT`. |
| `chainmissile{bounces=2;bounceradius=5;returntocaster;bounceconditions}` | Un `missile` que, al dar a su objetivo, salta al más cercano que no haya tocado (y cumpla `bounceconditions`, que el compilador guarda aparte: `bc`), y al final vuelve a quien lo lanzó o se acaba (`SkillMovers.bounce`). |
| `barcreate`/`barremove` | Sobre la barra del propio jefe (así la usan los packs): se ve / se quita. |
| `particleequation` | Sin efecto, como en MythicMobs (su wiki: «not yet functional»; en `STUB_OK`). |

### 3.8 La IA de MythicMobs y lo que se añadió en la 1.3.82 (packs nuevos)

**IA** (`skills/SkillAI.java`, leída por `tools/bosses/mythic_ai.py` para el simulador y el auditor; wiki
Mobs/Custom-AI). Cada mob de jefe tiene sus `AIGoalSelectors` (qué hace) y `AITargetSelectors` (a quién busca) **sobre
la IA de su tipo de Minecraft** (`SkillAI.vanillaGoals/vanillaTargets`: un zombi persigue y pega a los jugadores, un
gólem solo a quien le pega y a los monstruos, un cerdo pasea): sin lista, la de su tipo; `clear` (alias `reset`) la
quita entera y lo de después se añade; `1 meleeattack` lleva la prioridad delante (aquí no hace falta: cada objetivo
es una cosa distinta); lo que MythicMobs no conoce se ignora (aviso del auditor). `runaigoalselector{goal}`
(`aigoal`, `aigoals`) y `runaitargetselector{target}` (`aitarget`) añaden uno en mitad de la pelea (`clear` borra los de
antes y, en los de búsqueda, a quien perseguía); `resetai` vuelve a los de su tipo. Lo que hace `BossEntity`:

| Objetivo | En `BossEntity` |
|---|---|
| `meleeattack`, `zombieattack`, `spiderattack` | Persigue y **pega con el golpe de Minecraft** (`meleeStep` + `doHurtTarget`): alcance (ancho×2)² + ancho del objetivo (araña: 4 + ancho), 20 ticks entre golpes, el primero al llegar. Antes de entrar salen sus **`~onAttack` y `~onCombat`** (`SkillMobs.attack`, @trigger = a quien pega, `damage-amount` en la escala del pack): `cancelevent` lo anula, `modifydamage` lo cambia. Por tipo: el gólem pega entre media y vez y media su daño y lanza hacia arriba (0,4), el hoglin/zoglin lanzan, el husk da hambre, el esqueleto del Wither marchita, la araña de cueva envenena, el zombi en llamas prende; `ATTACK_KNOCKBACK` del tipo (devastador 1,5). Los 5 jefes de antes cancelan su `~onAttack`: siguen igual. |
| `movetowardstarget` | Persigue sin pegar. |
| `lookattarget{r}`, `lookatplayers{r=8}` | Mira a su objetivo / de vez en cuando (2 % por tick, 2-4 s) a un jugador cerca. |
| `randomstroll`, `panic`, `gotospawnlocation`, `fleesun`, `restrictsun` | Sin objetivo que perseguir: pasea (1 entre 120 por tick, a 10 bloques, sin salir de su grieta), huye 5 bloques si le pegan o arde, vuelve a donde apareció, busca sombra si arde al sol; no anda al sol. |
| `float`, `leapattarget` | Flota en el agua; salta sobre su objetivo a 2-4 bloques desde el suelo. |
| `type:ghast` | El ghast (`ghastStep`): vuela a un punto al azar a 16 bloques (empujones de 0,1 cada 2-6 ticks si no hay nada en medio), mira a su objetivo a 64 bloques y le lanza una bola de fuego (20 de carga, 40 de espera) que **`~onShoot` puede cancelar** (`SkillMobs.shootCancelled`; el Contemplador y el Arconte lo hacen). Sin gravedad. |
| `randomlookaround`, `randomnod`, `donothing`, `breed`, `eatgrass`, `opendoor`, `breakdoors`, `movethroughvillage`, `movetowardsrestriction` | Sin efecto, con su porqué en `mythic_ai.AI_OK` (o no cambian la pelea o cambiarían el mundo). |
| Búsqueda: `players`, `hurtbytarget`, `monsters`, `villagers`, `irongolem`, `type:spider` | `pickTarget`: con `players`, el jugador con más amenaza (§4.3); con `hurtbytarget` solo, entre los que le han pegado; si no hay jugador, el monstruo/aldeano/gólem más cercano que no sea de su grupo; la araña, a los jugadores solo a oscuras. Sin ninguno, solo sus skills le dan objetivo. |

Lo que no está en `AI_OK` (el arco del esqueleto `bowattack`, la carga del vex, el slime, el fantasma, el enderman,
`nearestotherfaction`…) lo marca el auditor como problema en el pack que lo use: se copia entonces. `NoAI` y `setai`
son el NoAI de Minecraft (ni piensa, ni anda, ni cae; sus skills siguen; `Rifts.spawnBoss` baja el jefe al suelo).
Opciones nuevas: `NoGravity`, `LockPitch`, `KnockbackResistance` (o la de su tipo), `PreventSunburn` (los zombis y
esqueletos arden al sol si no), `PreventRandomEquipment` (un esqueleto sin arco pega cuerpo a cuerpo); las opciones se
leen sin mirar mayúsculas (`noAI`).

**Más de la 1.3.82**:

| Qué | Cómo |
|---|---|
| Balas de `projectile`/`missile` | `ITEM`/`MYTHICITEM` (objeto suelto que gira), `TRACKING`/`REALTRACKING` (en la cabeza de un soporte, girada hacia donde va), `BLOCK`/`SMALLBLOCK` (el bloque), `ARROW`: el conversor les pone el modelo de su objeto (el del pack con su `Model`, o el de Minecraft); `material=air` no se ve. |
| `bindhitbox{mid;p;t}` | Saca el mob `t` atado a la caja `ob_`/`b_` `p`: va siempre en ella y **los golpes a esa caja son para él** (`BossEntity.hurtPart`); si el modelo no tiene esa caja, nada (como ModelEngine). Las tres cabezas de la Tormenta del Wither. |
| **Relevo** | Los mobs de verdad que el jefe saca en su `~onDeath` con al menos la mitad de su vida (`build_bosses.successors`, `boss.successors` con su nombre en español) siguen la pelea: salen como jefe de la grieta (`BossEntity.becomeSuccessor`: vida a la misma escala, su barra, la amenaza) y la grieta se gana cuando cae el último. El Mamut del Druida Boreal. |
| `summon` de un mob de Minecraft | `summon{type=WITHER_SKELETON}`: el de Minecraft tal cual (con `finalizeSpawn`), como mucho 16 de una vez y 24 cerca. |
| `@parent` de un esbirro | Su jefe (antes no tenía): `signal @parent`. |
| `@RandomRingPoint`, `isflying`, `setstance`/`stance` | Como la wiki. |
| Modificadores de vida en las skills | `- skill{s=X} @self <75%` dentro de una skill también se mira (antes solo en las líneas del mob). |
| `state{s=hit_<random.1to3>}` | El marcador se resuelve cada vez; el auditor comprueba cada animación posible. |

**Conversor** (`build_bosses.py`): las condiciones en la **forma antigua** (`- hasaura Dive false`, `- targetwithin 25`,
`- stance rage false`: el valor suelto es su atributo principal, `compile.LEGACY_CONDITION_KEY`; si falta alguna, el
auditor lo dice); los **nombres de MythicMobs son de todo el servidor** (`global_index`: Ignevar usa
`THEARCHIVIST_tele_to_floor` del Archivista; un nombre de mob de Minecraft no se busca en otros packs); **`HUECOS`**:
lo que un pack nombra y no trae (skills, animaciones, huesos, modelos), revisado a mano, que en MythicMobs tampoco hace
nada: queda como aviso con su porqué (para completarlo, `FIXES`, si el dueño quiere); **`ITEM_FIXES`**: objetos cuyo
override olvidó el pack; la **vista previa** esconde lo que el jefe esconde al aparecer (`spawn_hidden`) si `es.json` no
dice `preview_hide`. El simulador corre también los scripts `mm:` de las animaciones (Eldric empieza su pelea desde
uno) y `stance`, `altitude`, `variableisset`, `moving`…

### 3.9 Lo que pidió el dueño al probar los jefes nuevos (1.3.83)

Queja del dueño: *«algunos se quedan estúpidos y a veces me ignoran, se giran mal, caminan de lado, tiran ataques al
aire, sus boss bar no desaparecen al morir, sus ítems no deben dropearse»*, y que suene **siempre una música** al
empezar la pelea (la manda él: `sounds/jefes/musica.ogg`) y que **cada muerte sea épica, como la del dragón del End**.

| Qué | Dónde | Cómo |
|---|---|---|
| **Hasta dónde llega cada golpe** | `SkillBrain.reachOf/hits/boneHit` (copia: `sim_ai.reach_of`) | Al leer el ataque se mira a dónde van sus golpes: un área alrededor (`@PIR{r}`, `@EIR{r}`…), un cono (`@LivingInCone{r}`), un anillo, `@Forward{f}` o un **tótem en un hueso** (`@modelpart`): dónde está ese hueso en la animación que el ataque tiene puesta **en el instante en que sale el tótem** (siguiendo sus `delay`), hacia delante (lo que alcanza a quien tiene enfrente) más el radio del tótem; si el pack ya elige por el lado (`fieldofview` girado ≥ 45°, golpes detrás), en redondo. Si algún golpe va a `@target` (o al de la línea), dispara, se lanza o salta, **no tiene tope** (`far`: valen las condiciones del pack). Los packs ponen condiciones con holgura (los golpes de escudo del Esqueleto llegan a 4-7 y su condición es `0to8`; su pisotón, a 3,7). |
| No golpear al aire | `SkillBrain.usable`, `Attack.sweet` | La distancia que busca no pasa del 80 % de lo que llega su golpe; un golpe con tope no se lanza si el jugador está más lejos que su alcance + 0,6 + medio ancho del jugador. Si en **3 s** no entra (el jefe no puede andar hasta él), lo lanza igual, como el pack. |
| Mirar antes de un golpe por delante | `SkillBrain.tick/facing`, `Host.face` | Un golpe por delante (cono, hueso, `@forward`) sin `fieldofview` girado ni etiqueta de detrás: si el cuerpo no tiene al jugador a menos de 50°, **primero se gira** (medio camino cada 2 ticks, 4 veces como mucho) y luego golpea; con el modelo bloqueado (`lockmodel` del ataque de antes) espera hasta 20 ticks a que el pack lo suelte. En un combo (`chain`) o a la vez que otro, solo si ya le mira. El simulador cuenta «golpes por delante lanzados sin mirarle» y «golpes con alcance lanzados fuera de su alcance (al aire)»; `--sin-alcance` simula como la 1.3.82 para comparar (el Esqueleto: 6 de 32 al aire → 0; Kazk, el Rey Calabaza y el Mecha Piglin: 3-22 de lado → 0). |
| No andar de lado | `BossEntity.turnBody` | Andando, **el cuerpo va hacia donde anda** (BodyRotationControl de Minecraft, que copia ModelEngine) y la cabeza mira a su objetivo hasta `mh`. Antes el cuerpo miraba al jugador al rodearle o al apartarse. (1.3.84: sin «retroceder mirándole», que en el juego era correr de espaldas, y girando hasta 90° por tick: §3.10.) |
| No quedarse plantado | `BossEntity.customServerAiStep` (`noPath`) | Si Minecraft no le encuentra camino (cajas de 3-6 bloques no caben) o el camino no llega y está a menos de 32 bloques, va derecho (`MoveControl.setWantedPosition`). |
| No ignorar | `BossEntity.configure` | El jefe ve a todo el que esté en su arena: `FollowRange` al menos la correa + 8 (48); sus esbirros, lo de su pack. (El Mega Guardián es un Warden: no te ve si vas agachado y no corres; eso es del pack.) |
| La barra se va al morir | `BossEntity.die` | Se quita en cuanto muere (antes se quedaba a 0 mientras duraba su muerte). **Muerto es muerto**: `isDeadOrDying` cuenta `dead` (una skill de `~onDeath` que le devuelva vida no lo levanta a medias). |
| Sin objetos | `SkillRuntime` (`dropitem` sin efecto, en `STUB_OK` del auditor), `BossEvents.onDrops/onExperience` | Los jefes no sueltan nada (orden del dueño): ni los `dropitem` del pack (el cofre del Esqueleto, el sombrero del Rey Calabaza, el ojo del Contemplador) ni lo que llevan o la experiencia de los mobs de Minecraft que sacan (`summon{type=ZOMBIE}`, marca `SkillMobs.SUMMONED`). Lo que se gana es lo de la grieta. |
| **Música de la pelea** | `client/TFBossMusic`, `BossEntity.updateBar/stopMusic`, `SkillScreen` («music»), `MusicManagerMixin` | El jefe se la pide a quien ve su barra (mensaje de pantalla `music`, a = el jefe); suena en bucle (`tfclient:jefes.musica`, `stream`, sin posición) con 1,5 s de entrada y 3 s de salida mientras algún jefe la pida; se va al morir el jefe, si se va o si el jugador se aleja. Con el relevo (el Mamut del Druida) no se corta. Va con el volumen de **«Discos»** (así suena aunque se tenga la música de Minecraft quitada) y la música normal se calla mientras suena. La pista está fuera de `sounds/bosses/` (que el conversor borra al rehacer). |
| **Muerte épica** | `BossEntity.die/epicTick` (dato `EPIC`), `BossRenderer.rays`, `VfxLayers.death` | Solo el jefe que acaba la pelea (no el que deja relevo): dura su animación de muerte + 2 s, como poco 8 s; grita como el dragón (`ENDER_DRAGON_DEATH`); le salen **rayos de luz** como los del dragón (las cuentas de `EnderDragonRenderer`, blancos en el centro y del color del jefe hacia fuera, a su tamaño), chispas de su color y destellos que se le escapan, explosiones cada vez más seguidas y al final una grande cada tick mientras **el modelo se encoge hasta desaparecer**, y un estallido (destello, chispas, explosión, cohete). Las partículas se ven a 96 bloques. La animación de muerte del pack se queda en su último fotograma hasta el final (si no, volvía a ponerse de pie). |

### 3.10 La prueba en el juego y lo que encontró (1.3.84)

Queja del dueño tras la 1.3.83: *«se quedan bugueados, aparecen se van reaparecen, no me hacen caso, se van volando
lejos algunos, corren en retroceso, tienen texturas bugueadas, sin su animación…»* y **«haz tú las pruebas»**. Desde la
1.3.84 hay una **prueba en el juego de verdad** en GitHub Actions (`.github/workflows/jefes-prueba.yml`): Minecraft con TF
Client en una pantalla virtual (xvfb + Mesa por software), un mundo plano y, por cada jefe, una grieta, la pelea (800
ticks: el jugador se acerca y se aleja, le rodea y le pega; no muere), su muerte y capturas.

- **Cómo lanzarla**: un push a una rama `claude/**` cuyo commit diga `[prueba-jefes]` (todos, ~25 min) o
  `[prueba-jefes=gargola,kazk]`, o a mano (workflow_dispatch, campo «jefes»). Variable `TFCLIENT_PRUEBA_JEFES` (`1` =
  todos). Código: `bosses/BossTest.java` (servidor: la pelea y el informe) y `bosses/BossTestClient.java` (cliente: crea
  el mundo, capturas, cuenta las barras que ve, cierra el juego). Con la variable sin poner no hace nada.
- **Resultados**: la rama `pruebas-jefes` (sin historia, se rehace cada vez): `informe.txt`, `registro.txt` (lo del log
  de TF Jefes y los errores), `final_del_registro.txt` y capturas JPEG (`NN_<jefe>_pelea_<s>.jpg`, `_muerte_<t>.jpg`).
  `git clone --depth 1 -b pruebas-jefes https://github.com/modelqdeluxe-ops/ss.git pj`. Cada captura se saca al acabar
  un fotograma empezado después de pedirla (en cola, ninguna se pisa) y en el Void la prueba espera a que salga (hasta
  10 s): con shaders el cliente va a pocos fotogramas por segundo.
- **Qué mide** por jefe: si sale y cuántos jefes distintos (reaparecer = más de uno), % del tiempo con el jugador de
  objetivo, golpes que da y recibe, ticks andando, **hacia atrás / de lado** (respecto a su cuerpo), rápido,
  teletransportes, en el aire, lo más lejos de la grieta, esbirros, ticks sin modelo o sin animación, las animaciones que
  usó, y al morir: animación de muerte, barras que ve el cliente a los 3 s, si se quita, objetos, experiencia y esbirros
  vivos. Además, «episodios» con el tick y las animaciones del momento (anda hacia atrás, en el aire, fuera de la arena,
  sin objetivo). Si el jefe mueve al jugador (empujón, rayo tractor), la prueba le deja ir 1,5 s.

Lo que encontró y se arregló:

| Síntoma | Causa | Arreglo |
|---|---|---|
| «Aparecen, se van, reaparecen» | Con el jugador lejos de las entidades que tickean, la grieta veía «no tickea» y se reiniciaba al instante, quitando al jefe. | `Rifts`: los chunks de la arena (7×7) se **fuerzan** mientras despierta o hay pelea (`setChunkForced`, se sueltan al acabar, al quitar la grieta y al arrancar/parar el servidor); sin tick solo se reinicia tras `EMPTY_TICKS` sin nadie cerca. |
| «Corren en retroceso» | (a) La 1.3.83 hacía retroceder mirando al jugador y el cerebro le apartaba andando; (b) el cuerpo giraba 20°/tick y Minecraft le mueve hacia su yRot, que gira 90°/tick: al cambiar de dirección andaba de espaldas; (c) al pararse solo se paraba la navegación y el MoveControl (los jefes grandes van derechos) le seguía llevando al último punto mientras el cuerpo ya miraba al jugador (el Druida, ~13 ticks de espaldas cada vez). | Sin retroceder andando ni el salto atrás (`backstep`, que resbalaba ~13 ticks a 0,8 b/t con la animación de andar: el Druida a 12 bloques; no es de MythicMobs): demasiado cerca, se queda quieto; el cuerpo gira hasta 90°/tick andando (`turnBody`, `sim_ai`); al pararse, `BossEntity.halt` para también el MoveControl. Para un golpe por delante, el cerebro le **planta 3 ticks** a girarse (`BossEntity.face` → `faceHold`; si no, andar le volvía a girar). |
| «No me hacen caso» | La correa soltaba al objetivo en cuanto el jefe pasaba de 40 bloques de su grieta (una carga, un salto) y se iba dándole la espalda; y a quien sus golpes lanzaban a más de 48 bloques lo dejaba de ver (el Druida perdía al jugador justo al ir a volar hacia él). | Al que ya persigue lo sigue hasta 64 bloques de la grieta (`pickTarget`, `targetInArena`); a otros, solo en la arena (48); si el jefe acaba a más de 64, vuelve a la grieta con partículas de portal. |
| «Anda de lado» (Astwood, 19 % del tiempo) | Minecraft mueve al mob hacia su yRot (el MoveControl lo gira hasta 90°/tick hacia el siguiente punto del camino) y el cuerpo del jefe era otro giro aparte, calculado antes de moverse: al recalcular el camino iba unos ticks de lado. | `turnBody` pone yRot = cuerpo antes de moverse y `aiStep`, tras moverse, cuerpo = yRot (el BodyRotationControl de Minecraft); `sim_ai.walk` igual. Astwood, Kazk, Druida: 0 ticks de lado; lo que queda en la Abominación, la Gárgola y el Mecha Piglin es casi todo con `lockmodel` de su pack (el modelo no gira y el jefe sigue andando, como en ModelEngine: la prueba lo cuenta aparte). |
| «Se van volando lejos» | Ignevar: `velocity{relative}` y `spinentity` usaban el yRot de Minecraft, que en los jefes no se mueve (volaba de espaldas). Contemplador/Arconte: el vuelo al azar del ghast no tenía límite, y `altitude{a=<6}` miraba solo 8 bloques hacia abajo, así que el Contemplador subía sin parar (61 bloques). | `Who.yaw()` en esas mecánicas; el ghast flota alrededor de su objetivo (o grieta) a 3-10 bloques; `altitude` mide sobre el primer suelo de debajo, sin límite, como MythicMobs (`SkillConds.floorBelow`). |
| «Texturas bugueadas» | En las capturas los modelos salen bien; el cuadrado blanco grande junto al Contemplador es la partícula `flash` de Minecraft que lanza su pack (el destello de los cohetes), y lo naranja es el jugador ardiendo por su láser. | Nada (fiel al pack). |

La Tormenta del Wither es NoAI (no tiene objetivo; ataca con `@PIR`): su mordisco solo sale cuando el rayo tractor te
arrastra a menos de 10 bloques de una cabeza; sus otros ataques, por debajo del 75 % y del 50 % de vida. **Sus tres
cabezas no salían nunca**: el pack las ata con `bindhitbox{p=ob_head_1_hitbox}` y el conversor guarda el hueso sin la
etiqueta (`head_1_hitbox`); `VfxModel.bone` ahora también busca el nombre sin su etiqueta (como ya hacía al revés).
Lo mismo pasaba con `hitboxconfig{pass}` (guardaba la caja con `ob_` y la buscaba sin él).

### 3.11 La grieta del vacío, su cielo y sus sonidos (1.3.86)

Pidió el dueño: *«la grieta es fea, mejora esa textura, el sonido también es feo y simple; las estelas blancas al
derrotar un boss (las del dragón del End) hacen que vea a través de los bloques; no me gusta el sonido de TNT; hazla
imponente; que la grieta haga que el cielo se tone como el del End, con nebulosas y estrellas fugaces, que combine con
la música; el color de la grieta, uno para todas: oscuridad, morado intenso oscuro»*.

| Qué | Dónde | Cómo |
|---|---|---|
| **La grieta** | `tools/bosses/rift_model.py` → `bosses/models/grieta.cristal.json`, `textures/bosses/grieta/me/` | Ya no se tiñe del color del jefe (`RiftRenderer` pasa blanco): un solo morado del vacío. Plataforma escalonada y cuatro agujas de obsidiana casi negra con vetas que brillan (`*_e`), dos anillos de runas violetas que giran, **el desgarro** (dos planos cruzados de ~7,5 bloques con una textura animada de 16 fotogramas: remolino morado, estrellas que titilan y borde magenta), el cristal de obsidiana translúcida con núcleo que late, ocho esquirlas que orbitan. Animaciones: `idle` (el desgarro respira), `activate` (se abre ×2 y gira), `dormant` (rendija), `regrow`. Para cambiarla: edita el script y `python3 tools/bosses/rift_model.py --preview carpeta` (borra las texturas viejas de `grieta/me/`). El cartel va a 9,2 bloques; la caja de recorte, +4 arriba. |
| Partículas | `RiftEntity.clientFx` | En el cliente: portal al revés que sube del círculo, partículas de portal que se traga el desgarro, polvo morado (`VOID_PURPLE`), destellos; al despertar, más. El despertar del servidor (`Rifts.wakingFx`) también en ese morado. |
| **El cielo** | `bosses/RiftSky`, `mixin/RiftSkyMixin`, `tools/bosses/rift_sky.py` → `textures/environment/grieta_*.png` | Solo cliente, no toca el mundo. Fuerza 0..1 (cambia 0,01 por tick, ~5 s): grieta lista o recargando, entera a 20 bloques y nada a 64; despertando o en pelea, entera hasta 80 y nada a 128. En `RenderLevelStageEvent.AFTER_SKY` dibuja, con esa fuerza: una esfera con el panorama de nebulosas (equirectangular 1024×512 hecho con ruido 3D: sin costura; u = longitud, v = latitud, las mismas cuentas en Java y Python) que gira muy despacio, siete nubes de nebulosa que laten (luz que suma), 700 estrellas que titilan y **estrellas fugaces** (cada 0,6-3 s, a veces dos, con estela que se apaga). `ComputeFogColor` y el mixin (`ClientLevel.getSkyColor/getCloudColor`) llevan la niebla, el cielo y las nubes de Minecraft al morado del vacío; con más de la mitad de fuerza, sin nubes (`RiftCloudsMixin`: teñidas se veían como manchas oscuras cuadradas). |
| **Sonidos** | `tools/bosses/sonidos_grieta.py` → `sounds/grieta/*.ogg`, `sounds/jefes/muerte_*.ogg`; `BossSounds`; `sounds.json` | Sintetizados (numpy/scipy, sin muestras de nadie; mono para que suenen en su sitio): `grieta.golpe` (al golpear el cristal), `grieta.despertar` (los 3,5 s que sube), `grieta.estallido` (sale el jefe), `grieta.lista` (vuelve a estar lista), `grieta.ambiente` (zumbido en bucle a menos de 30 bloques, lo pone `RiftSky`, categoría «Ambiente»), `jefes.muerte_crepita` (3 variantes: los chasquidos de la muerte épica, antes la explosión de la TNT) y `jefes.muerte_estallido` (el final). El rugido del dragón al empezar la muerte se queda. Para rehacerlos: `python3 tools/bosses/sonidos_grieta.py --wav carpeta` (las WAV, para escucharlas). |
| **Rayos de la muerte** | `BossRenderTypes.RAYS`, `BossRenderer.rays` | Antes `RenderType.lightning()`: escribe profundidad y, con gráficos «Fabulosos», va a la capa del clima: se veían a través de los bloques. Ahora una capa propia: luz que suma, prueba la profundidad sin escribirla, en la imagen principal (la tapan los bloques y no tapa nada). |

La prueba en el juego saca dos capturas más por jefe: `grieta_lista` (la grieta en reposo con el cielo ya entrado) y
`grieta_despierta` (despertando).

### 3.12 Lo que pidió el dueño tras la 1.3.86 y lo que encontró la prueba (1.3.87)

Quejas: *«texturas del cielo y la grieta pixeladas, estrellas fugaces horribles, una grieta más grande; que desaparezca
al salir el jefe y vuelva al matarlo; las llamas no hacen la animación; Astwood te manda a volar fuera del mapa y su bola
azul no impacta; el ataque de área del Archivista cae en el aire; el Mamut lanza ataques al aire, no te sigue, aparece y
desaparece en bucle y al pegarte ya no le puedes hacer daño; más radio del cielo, música siniestra que se quite al
empezar la pelea; quita el sonido de tambores al morir; los rayos son exagerados»*.

| Síntoma | Causa (medida en la prueba) | Arreglo |
|---|---|---|
| «Te manda a volar fuera del mapa» (Astwood: 128 bloques de alto en la prueba) | `potion` cortaba el amplificador en 127. La jaula de Astwood pone `JUMP 155` (Bukkit manda el amplificador al cliente en un byte: 155 → −101, **no poder saltar**); cortado a 127 era Salto 128 = un supersalto de ~130 bloques. | `SkillRuntime.potion`: hasta 255, como Bukkit; y `BossEvents.onJump` (en los dos lados): con Salto de nivel 128 o más (o negativo) **no se salta**, que es lo que ve un cliente de Bukkit (en Forge el cliente veía el nivel positivo: con el `jump_boost 200` del aturdimiento de Astwood la prueba volvió a ver 128 bloques). |
| «Al pegarme ya no le puedo hacer daño» (Mamut) | `freeze{ticks=480}` (el búho de nieve que invoca el Mamut, y el zarpazo del Druida) se hacía como `stun`: 24 s con Lentitud X y **Debilidad V** (tu daño cuerpo a cuerpo, 0). En MythicMobs `freeze` es el frío de la nieve en polvo (`setFreezeTicks`). | `SkillMotion.freeze`: `setTicksFrozen(max(actual, ticks))`: escarcha en pantalla, algo de lentitud, a partir de 140 un poco de daño cada 2 s, y se va solo (2 por tick). |
| «Aparece y desaparece en bucle» (Mamut) | Su `spawn` (sale de debajo de la tierra) está en **bucle** en el `.bbmodel` y el motor lo jugaba al poner el modelo: 800 de 800 ticks de pelea. | `VfxLayers.playSpawn`: la de aparecer de ModelEngine se juega una vez (luego la de reposo), la tenga como la tenga el `.bbmodel`. |
| El trono del Esqueleto vivo en la pelea siguiente | **Todos los jefes y esbirros volvían a «nacer» al quitarse**: la muerte acaba dentro de `super.tick()` (`remove` → `SkillMobs.removed` olvida su estado) y `BossEntity.tick` seguía con `SkillMobs.tick`, que rehacía el estado y lanzaba otra vez su `~onSpawn` (el Esqueleto invocaba un trono nuevo; la prueba lo vio con «de qué skill y en qué tick sale»). Además `TOWER_SKELETON_THRONE` (`Despawn: NEVER`) se queda para siempre en el servidor del pack. | `BossEntity.tick` para si se ha quitado y `SkillMobs.tick` no hace nada con un mob quitado; `SkillMobs.removed`: lo que invocó un jefe (y lo que invocaron esos) se va cuando se quita él. |
| Primer fotograma del lanzallamas (Esqueleto, Hidra) | El conversor solo metía en la fuente los glifos de `settextdisplay`, no el `Text` con el que nace el text_display (⛿): sus 2 primeros ticks se veía la letra de Minecraft. Los demás fotogramas sí cambian (en las capturas se ven llama verde y humo gris a la vez). | `build_skills.write_glyphs` mira también `DisplayOptions.Text`; reconvertidos los dos. |
| «Cae en el aire» (área del Archivista) | `tyo=64` se sumaba también al apuntar a un sitio. | `tyo` solo con un ser de objetivo; el alcance (`maxRange`) se mide en línea recta desde la salida; `sdir` solo en misiles (MythicMobs). |
| La bola azul de Astwood | En la prueba le da al jugador las 2 de 2. Con `inertia=100` del pack apenas gira (el motor la deja en 12): andando se esquiva, como en el pack. | Nada. |

**La grieta 2.0** (`rift_model.py`, `GEO_S = 1.6`): 1,6 veces más grande (entidad 3,2×8,5, se ve a 160 bloques),
texturas de 256 (vetas orgánicas con supermuestreo, cristal sin cruces de brillo), desgarro 96×288 de 16 fotogramas.
**Desaparece al salir el jefe** (`RiftRenderer`: se encoge en 14 ticks en `FIGHT`, sin cartel) y **vuelve** al recargar
(crece en 30 ticks).

**El cielo 2.0** (`RiftSky`, `rift_sky.py`): todas sus texturas llevan `.png.mcmeta` con `blur` (filtro suave: eran los
«píxeles horribles»); panorama de 2048×1024 sin estrellas pintadas (eran píxeles gordos al estirarlo), con filamentos,
compresión suave (antes manchas planas de borde duro) y tramado; 1400 estrellas-destello; una capa de nebulosas lejana
que gira al revés (paralaje); un **sol negro** (`grieta_eclipse.png`) 38° sobre la grieta más cercana; las fugaces son
una estela con textura (`grieta_fugaz.png`: cabeza y cola que se afina, suave de ancho), cada 2,5-9 s, y a veces un
**bólido** grande, lento y rosado. Radio: lista o recargando, entero hasta 48 bloques y nada a 112; despertando o en
pelea, entero hasta 112 y nada a 128.

**La música de la grieta** (`grieta.musica`, 96 s en bucle, re frigio, coro, campanas y viento; `sonidos_grieta.py`):
categoría «Discos», sin posición, entra en 5 s cuando la fuerza del cielo pasa de 0,3 sin pelea y sin la música de un
jefe; se va en 2 s al despertar la grieta; vuelve cuando se calla la del jefe. Mientras suena, la de Minecraft espera
(`MusicManagerMixin` con `RiftSky.musicActive`). En el CI no hay tarjeta de sonido: la prueba mira `RiftSky.musicWanted`.

**La muerte**: sin los chasquidos («tambores»): `jefes.muerte_disolucion` (~9 s de viento, cristal y coro, sin golpes)
al empezar y `jefes.muerte_estallido` suave al final; rayos de 0,4-0,9 según el tamaño del jefe y hasta 30.

**La prueba** cuenta además: daño por skill («skills que le dan»), proyectiles y misiles por skill (cuántos tocan al
jugador y por qué acabó uno que no), lo más lejos/alto que le lanzan (con la skill, la velocidad y sus efectos),
golpes del jugador que no quitan vida, efectos que no se quitan (más de 15 s), la pelea entera con el relevo (el Mamut:
`<jefe>_relevo`) y la música (`música de la grieta antes de la pelea` / `música en la pelea`).

### 3.13 The Void, la grieta 3.0 y lo que pidió el dueño tras la 1.3.87 (1.3.88)

El dueño (con dos capturas, el pack «NPCs For Mini Games vol.1» y el mapa «EndCarageKitMap.schematic»): *«la textura de
la grieta está mal trabajada: falta pintura, bordes dentados, caras planas; quiero algo más imponente, más grande. El
cielo: ¿el sol me sigue? quítalo; hazle un rework, que haya un agujero negro gigante animado, como el cielo de
Iterationt en el End, que me haga sentir pequeño. Ignevar se hace pendejo, me deja de perseguir, se queda quieto lejos y
ataca sin sentido; el caballero esqueleto que nace quebrado sale completo y luego se cae a pedazos y luego vuelve; el ojo
volador no me deja respirar, sus ataques me elevan al cielo. Quiero que The Void sea una dimensión nueva, a la que se va
solo con el caballero dorado con alas; con esta isla (sin casas, rellenas con bloques de la isla), el cielo de la
dimensión, sin sol, la luz del End, sin errores en Mohist; el NPC con /tf void npc, un diálogo bonito con su nombre y el
lore, una animación espectacular de teletransporte y una estructura para volver; la segunda vez solo saluda»*.

**The Void** (paquete `vacio`; la isla, `tools/void/isla.py`; los originales, cifrados en `packs/void.tar.enc`):

| Qué | Dónde | Cómo |
|---|---|---|
| La dimensión | `data/tfclient/dimension/void.json`, `dimension_type/void.json`, `worldgen/biome/void.json` | Como el End: sin luz del cielo, hora fija, `effects` propios (`VoidEffects`: el mapa de luz más claro, sin nubes ni lluvia, niebla morada). Bioma sin criaturas ni estructuras, con motas de portal y los sonidos del valle de almas. Camas y anclas no se usan (no explotan). |
| La isla | `tools/void/isla.py` → `tfclient-void-isla.bin`; `VoidIsland`, `VoidIslandGenerator` | El mapa del dueño (MCEdit «Alpha», 297×121×492) pasado a 1.20.1. Su centro era un pozo de ~50×50 que atravesaba la isla, con puentes en cruz, cuatro casetas y una torre: fuera, y relleno con el terreno de la isla (ruido 3D en su proporción de lana gris, terracota cian, arcilla y piedra del End; arriba, franjas de lana y terracota moradas y magenta). Encima, el santuario del caballero (tres escalones de piedra negra, cuarzo y oro; seis pilares con varas del End y luz invisible). El generador copia en cada chunk su trozo al nacer: nada se pega de golpe en el servidor. El santuario queda en x=0, z=0, y=75. |
| El cielo | `VoidSky`, `shaders/core/void_sky.*` | Todo el cielo es un shader: rayos que se curvan con la gravedad (Schwarzschild: a = −1,5 h² r/r⁵) alrededor de un agujero negro con disco de acreción (Kepler, más caliente dentro, Doppler) sobre el panorama de nebulosas y estrellas que titilan. Al norte, encima del santuario (se ve de frente al llegar). Calidad según los gráficos (Rápidos: menos pasos). Sin el sol negro de la 1.3.87. Encima, las estrellas fugaces de `RiftSky`. Vista previa: `tools/void/cielo_preview.py`. |
| El ambiente | `RiftSky` | El cielo de las grietas ya no sale al acercarse a una: es el de la dimensión. En The Void suena la música siniestra (`grieta.musica`, se calla en las peleas) y hay fugaces y niebla morada. |
| Protección y rescate | `TFVoid` | Solo el staff rompe o pone bloques (ni cubos ni mecheros); quien cae por debajo de y=2 vuelve al santuario con caída lenta. |
| El caballero de luz | `KnightEntity`, `KnightRenderer`, `KnightScreen` | Aurelion (`skywars_npc.bbmodel` del pack de Wardenful Studio → `bosses/models/vacio.caballero.json`; sin sus islitas), 1,4 veces más grande, a luz llena, mira a quien tiene cerca, con motas doradas. Clic derecho: el diálogo (placa maciza de oro y morado, su retrato de verdad, letra Cinzel, texto que se escribe, respuestas con el ratón o 1-2-3; lore del Void, las grietas y su orden). La primera vez se presenta; luego solo saluda por tu nombre. `/tf void npc` lo pone (staff), `/tf void quitar` lo quita (a 6 bloques). |
| El viaje | `VoidTravel`, `TravelFx`, `VoidNet` | 64 ticks: espirales de luz que se cierran, flotas, faro y amatista, la pantalla se llena de rayos dorados y luz; destello y sonido del portal del End; llegas delante del caballero del santuario (sale solo, uno). Dónde estabas se guarda (ForgeData → «TFVoid»); el del santuario te devuelve allí. `/tf void ir|volver [jugadores]` (staff). |

**La grieta 3.0** (`tools/bosses/grieta3.py` → `bosses/grieta3.json`; `RiftRenderer`, `RiftShaders`, shaders
`rift_obsidian` y `rift_fx`): sin texturas (eran los «bordes dentados» y las «caras planas»). Estrado roto de 14 lados y
16 piedras, siete garras facetadas de 6 a 18 bloques, el cristal que se golpea (bipirámide de 8 lados, 5,8 bloques) y 14
esquirlas que orbitan; la luz de cada cara se calcula por vértice (de arriba, la morada del desgarro, el borde según la
cámara) y el shader pone las vetas que laten. El desgarro (20 bloques de alto) y el círculo de runas (radio 13,5) son
cuadros con el shader `rift_fx`: borde suave y ondulado, el vacío girando dentro con estrellas y el ojo del vórtice, el
halo. Estados como antes (despertando: más luz, se abre, el cristal tiembla; pelea: desaparece; recarga: rendija; se
vuelve a abrir en 40 ticks). Vista previa con los shaders de verdad: `tools/bosses/grieta3.py --preview`.

**Los jefes:**

| Síntoma | Causa (medida en la prueba) | Arreglo |
|---|---|---|
| «Ignevar se hace pendejo, se queda quieto, ataca sin sentido» (anda 25 de 800 ticks, 0 ticks en el aire aunque pone la animación de vuelo, 41 llamas y ninguna da) | Su despegue es `velocity{m=SET;y=<caster.var.ramping_current>;repeat=40}` y la variable sube tick a tick: el motor leía los marcadores **una vez** al empezar la línea (0): nunca volaba, así que nunca se giraba hacia ti en el aire (`IGNEVAR_rotate_flying`) ni caía en picado; en el suelo solo se gira andando (a más de 8 bloques y contigo delante) y escupía fuego hacia donde miraba. | `SkillRuntime.exec`: con `delay` o `repeat`, los marcadores se leen cada vez que sale la línea, como MythicMobs. |
| «El ojo volador me eleva al cielo» (55 bloques y 14 de alto) | (1) `throw{v=4;vy=5} @self` del láser (el jugador lo lanza sobre sí mismo con sudoskill): el motor lo hacía un salto de 0,5 hacia arriba en cada impacto; en MythicMobs el vector (objetivo − quien lanza) normalizado sale NaN y Bukkit no lo acepta: no mueve. (2) Cada impacto de la danza de rayos hace `throw{v=4;vy=5}` (también en MythicMobs: con impactos casi cada tick, subías sin parar). | `throw` como MythicMobs: (objetivo − quien lanza) normalizado en 3D, la Y = velocityY, `@self` no mueve, `fromOrigin`. Y (orden del dueño) «suelo» en `es.json` del Contemplador (`BossInfo.ground`): sus empujes al jugador dan como mucho un saltito (0,25) y nada si ya va por el aire; sus explosiones no le suben. En la prueba: 0 empujones (antes 3, hasta 55 bloques). |
| El caballero no se veía (prueba) | `SkillDefs.modelDir` buscaba los modelos que no son de un jefe en `skills/models/`. | Los `vacio.` van con los de los jefes. |
| Ignevar en el aire no se giraba hacia ti (`target_rot`, `yaw_diff`, `newrot` siempre 0) | Su `IGNEVAR_rotate_flying` calcula `-atan2(dx, dz) * 180 / 3.14159`: el evaluador de `variablemath` cortaba los nombres en la primera cifra (`atan` + `2`) y no tenía `atan2`: daba error y la variable no cambiaba. | `SkillVars.math` (y `sim_ai.crunch`, con su prueba en `test_convert.py`): nombres con cifras, `atan2`, `pow`, `exp`, `hypot`. |
| «El caballero esqueleto que nace quebrado sale completo, luego se cae a pedazos y luego vuelve» (Guardián Hueco; capturas cada 10 ticks del despertar) | Nace dormido (`defaultstate{s=sleep}`, `setai{ai=false}`) y despierta con `awaken` si estás a menos de 8. Pero en su primer tick el motor le buscaba objetivo **antes** de sus `~onSpawn` (en MythicMobs salen al aparecer, antes de la IA): dormido y con objetivo, sus `turn_left/right @target` (animaciones override de todo el esqueleto, de pie) lo ponían entero mientras se giraba; al acabar volvía al montón de huesos y luego se montaba con `awaken`. | `BossEntity.customServerAiStep`: no busca objetivo en su primer tick. Sin IA no busca (NoAI de Minecraft), así que dormido no se gira. |

**Prueba completa de la 1.3.88** (los 17 y el Void, frente a la de la 1.3.87): Ignevar anda 204 ticks (25), vuela 270
(0) y «quieto y lejos» 39 (317); el Contemplador no lanza (0 bloques; 55) y, por eso, le llegan más láseres (131
impactos; 73); el Guardián Hueco duerme hasta que te acercas a 8 bloques; el Rey Calabaza ya no se queda a 3 bloques del
suelo (149 ticks en el aire → 0) y sigue atacando; los demás, igual. Los «efectos que no se quitan» son los del pack (el
trono, el yunque de Eldric, los bloques de nieve, las estrellas del Arconte), los mismos que en la 1.3.87.

### 3.14 La grieta del Void, la grieta madre, el santuario nuevo y lo que pidió el dueño tras la 1.3.88 (1.3.89)

El dueño (con tres capturas): *«excelente con el Void y una mierda con el dragón Ignevar: veo invisibles sus llamas, es
pendejo, lento. Haz el agujero negro más grande e imponente. Con shaders el Void se jode completamente, con cualquier
shader; y los píxeles están como jodidos. El centro debe tener la grieta, configurable con /tf boss: la grieta del Void,
para no estar poniendo una grieta cada rato con un jefe distinto, la pongo desde ahí a todos los que quiera; dos
grietas, la normal y la grieta madre, más imponente, para criaturas más poderosas; por defecto la normal. El santuario
hazlo en una esquina, en esa construcción; ese modelo es horrible y está 1 bloque volando del suelo; quita las demás
estructuras cerca del centro. Checa esa línea en la grieta, parece vidrio quebrado. El caballero: se buguean las alas,
le traspasan al pecho; ponle otro nombre épico no conocido, y ponle Caballero Sacro; los diálogos van a 3 fps. Esas
franjas o círculos del teletransporte están horribles: si no lo vas a hacer bien, mejor no pongas nada»*.

**The Void:**

| Qué | Causa (medida) | Arreglo |
|---|---|---|
| Agujero «más grande e imponente» y «píxeles jodidos» (captura: el disco lleno de puntitos y rayas) | El ruido fino del disco (hebras de 0,07 radios: `noise3(r·14…)`) y la capa de estrellas más densa (celdas de 0,08°) eran más pequeños que un píxel: grano y parpadeo. | `void_sky.fsh`: la cámara más cerca (DIST 12 → 8; la sombra mide ~18° de radio, antes 12°), disco hasta 10,5; cada octava del ruido se apaga antes de bajar de ~2 píxeles (lo que mide un píxel a la distancia que ha recorrido el rayo, `fwidth` de la dirección antes del bucle) y las estrellas son manchas de al menos un píxel con la misma luz. La nebulosa se apaga junto al agujero y el borde del disco que pasaría junto a la cámara se difumina. El agujero está ahora al sur, subido 22°: se ve al llegar al santuario, detrás de la grieta del centro. Vista previa a 1080p con gráficos rápidos: sin grano. |
| «Con shaders el Void se jode, con cualquier shader» (captura: cielo azul con nubes) | Oculus (el Iris de Forge, 1.6 a 1.8) elige los programas del paquete por el **id** de la dimensión: `tfclient:void` no sale en el `dimension.properties` de los paquetes (Complementary: `world0=*`), así que usaba los del mundo normal (sol, cielo azul, nubes). | `vacio/ShaderPacks` (por reflexión, Oculus no es dependencia): al estar en el Void, al paquete cargado se le dice que `tfclient:void` use la carpeta de su End (la de `minecraft:the_end`) y se rehace su pipeline; `VoidEffects` (SkyType END) deja entonces el cielo del End de Minecraft, que el paquete cambia por el suyo. **Probado en el juego** con Oculus 1.8.0 + Embeddium 0.3.31 + Complementary Reimagined r5.3 (commit con `[shaders]`: `build.gradle -Ptfshaders` y el paquete en `run/shaderpacks`): el registro dice «el Void usa world1 del paquete» y las capturas, el End morado de Complementary. |
| «El centro debe tener la grieta»; «el santuario, en una esquina, en esa construcción»; «está 1 bloque volando»; «quita las demás estructuras cerca del centro» | El terreno con el que se rellenó el pozo quedaba a veces un bloque más bajo que el estrado (y=60 con el escalón en 62): había huecos debajo. En el centro quedaban el santuario, farolillos de cristal y trozos de camino. | `tools/void/isla.py`: en el centro, la **plaza de la grieta** (llana en 24 bloques, oscura de piedra negra y obsidiana con siete vetas de obsidiana llorosa, que se funde con las franjas de la isla) y nada construido a menos de 45 bloques; las franjas del relleno siguen las de alrededor (cada columna toma la de la orilla más cercana por un camino con ruido). El **santuario nuevo**, en la punta norte, donde el mapa tenía un templete con cúpula: estrado de dos escalones (piedra negra y cuarzo con un anillo cincelado y oro), ocho columnas con basa y capitel, entablamento con cornisa, cúpula de cristal blanco con nervios de cuarzo, remate de oro con vara del End, farolillos colgando en las entradas, escaleras al este (por donde sigue el camino), balaustrada hacia el barranco y luz invisible dentro. Sus **cimientos bajan hasta el terreno** en cada columna. El caballero, en el pedestal del centro mirando al sur; se llega a la puerta del este, mirando a la grieta (desde el borde sur la tapaba una loma de la isla; y al agujero negro detrás). El `.bin` pasa a la versión 2 (lleva dónde va la grieta). |
| Los mundos donde el Void ya estaba generado (el del dueño) | Los chunks guardados no se vuelven a generar: seguirían el santuario viejo y el templete. | `vacio/IslandUpdate`: si al cargar el centro (o la punta norte) lo que hay no es lo que debería haber (un par de bloques que distinguen las islas), esa zona (47 bloques alrededor de la grieta, 14 alrededor del santuario) se reescribe como la isla nueva, 48 columnas por tick y solo los bloques que cambian; hecho, se apunta en `data/tfclient_void.dat` y no se vuelve a mirar. El caballero que se quedó en el centro se va solo y sale el del santuario nuevo. |
| «La grieta del Void, configurable con /tf boss, a todos los que quiera; la normal y la grieta madre; por defecto la normal» | — | `Rifts`: una grieta fija (`Rift.fixed`, id fijo) en el centro de la plaza, con dos listas de jefes con su dificultad (`VoidConfig`: la de la normal y la de la madre) y una activa (al principio la normal; la normal con los 17 en su dificultad guardada, la madre con los 17 en Pesadilla). Cada vez que vuelve a estar lista elige uno al azar de la lista activa (sin repetir el último). En `/tf boss` → GRIETAS, su tarjeta arriba (IR y ✎) y su panel: elegir la lista, ACTIVAR (ponerla en el centro), REINICIAR, AÑADIR el jefe elegido con la dificultad de DIFICULTAD y quitar con ✕. No se quita ni se edita como las demás. `tools/bosses/sim_gui.py` comprueba el panel a todos los tamaños. |
| La grieta madre «más imponente» | — | Su propia malla (`grieta3.py` → `bosses/grieta3_madre.json`): estrado de 18 lados con 24 piedras, 11 garras de 9 a 27 bloques, cristal ×1,5, 24 esquirlas, desgarro de 30 bloques y círculo de runas de 19,5; en carmesí (luz de cada cara en `RiftRenderer`, material +4 en `rift_obsidian`, modo 0,25/1 en `rift_fx`, partículas carmesí). Caja y cartel más altos (`RiftEntity.KIND`). |
| «Esa línea en la grieta parece vidrio quebrado» (captura: una raya recta del ojo del remolino hacia un lado) | El remolino del desgarro salía de `atan(y, x)`: en su corte (−π/π) el ruido saltaba (raya) y en el centro todo convergía (el «estallido»). Lo mismo en las runas del círculo. Además, los destellos de la obsidiana eran cuadraditos duros. | `rift_fx.fsh`: el remolino gira el plano más cuanto más cerca del ojo y lee el ruido ahí (sin cortes); las runas, 48 casillas exactas por vuelta. `rift_obsidian.fsh`: destellos redondos con borde suave, solo en la roca. Reproducido y comprobado en la vista previa de cerca (`grieta3.py --preview`, vista «pegado»). |

**El caballero y el viaje:**

| Qué | Causa | Arreglo |
|---|---|---|
| «Otro nombre épico no conocido, y Caballero Sacro» | — | **Caelthar, el Caballero Sacro** (`KnightEntity.NAME`/`TITLE`): cartel, diálogo, idiomas y textos del santuario nuevo. |
| «Se buguean las alas, le traspasan al pecho» | El «idle» del modelo (16,5 s) trae a los 9,1 s una reverencia con un aleteo de hasta −94°/44°/−84° en las alas: se meten por el pecho. | Animación «reposo» en `vacio.caballero.json`: la parte tranquila del idle (0-9 s, respira y mece las alas), en bucle. |
| «Los diálogos van a 3 fps» | El halo del retrato se pintaba fila a fila: ~430 `fill` por fotograma, y en 1.20.1 cada uno es una llamada de dibujo aparte. | `KnightScreen`: el halo es una textura que se hace una vez; la placa y las motas, en una sola tanda (`drawManaged`). |
| «Esas franjas o círculos del teletransporte» | Rayos que giraban, viñeta de franjas desde los bordes, la onda de llegada y el destello FLASH. | `TravelFx`: solo un fundido a pantalla entera, suave, a luz blanca al salir y desde blanco al llegar (lila en el Void). Sin FLASH. Las espirales de luz en el mundo siguen. |

**Los jefes:**

| Síntoma | Causa | Arreglo |
|---|---|---|
| «Veo invisibles sus llamas» (Ignevar; en la prueba, el cliente tenía 19 llamas y las 19 «sin modelo») | **(1) `SkillClient.itemModels()`, la lista de modelos de objeto que se cargan al arrancar, solo recorría las clases de skills, no los jefes: ningún modelo de efecto de un jefe se cargaba** (25 de 9 jefes: las llamas de Ignevar, la bola azul de Astwood, el láser del Contemplador, los pinchos de hielo del Druida Boreal, la chispa de Eldric, las piezas del Esqueleto de la Torre, el tajo del Arconte, el láser y el misil del Piglin Mecha, el sombrero del Rey Calabaza: todos invisibles). (2) Sus llamas son `item_display` (`ignevar_flamethower_particle`, la bala MOB de su aliento) que cambian de objeto cada 2 ticks (`setDisplayEntityItem{i=ignevar_flamethower_02..10}`, objetos INK_SAC con su Model) y nacen con `DisplayOptions: Item: ignevar_flamethower_01, Billboard: CENTER, Scale: 4,4,4`. El conversor buscaba esos objetos solo entre los que suelta el jefe: quedaban como `minecraft:item/ignevar_flamethower_02` (no existe); el `Item` de las DisplayOptions no se pasaba y el motor dibujaba el objeto sin billboard ni escala. | `itemModels()` también recorre los jefes; `build_bosses.py` busca en todos los objetos del pack (`display_item`, también para `DisplayOptions.Item`); el motor hace nacer el item_display con su objeto y lo dibuja como el `DisplayRenderer` de Minecraft (billboard, traslación, giro izquierdo, escala, giro derecho, sin transformación de objeto), y `displaytransformation` acepta `val` (como lo escribe el pack) y cuaterniones. Lo mismo arregla las partículas del láser del **Contemplador** y del **Piglin Mecha** (también item_display con su `Item`, que nunca se veían). |
| «Es lento» (Ignevar) | Anda empujándose a sí mismo: `velocity{m=add;z=0.05;r=true}` cada tick (en Minecraft, con el rozamiento del suelo, ~0,11 bloques/tick; en la prueba, **0,104**: como el pack): la «Velocidad» de la dificultad no le afectaba (solo cambia la de los que andan con su IA). | Un jefe que se empuja a sí mismo (velocity add sobre él) va a la Velocidad de su dificultad (Normal ×1 = el pack; Demencial ×1,25). La prueba mide ahora la velocidad media al andar. |
| «Es pendejo» (Ignevar; en la prueba echa el fuego hacia 162° con el jugador a −135°) | Su aliento de fuego pone `stopturn` 140 ticks: el pack no lo deja girarse mientras escupe y sus llamas van hacia donde mira la cabeza; si te mueves de lado, el fuego va al aire. Como se gira con sus skills (`setrotation`) y no tiene IA de andar, el «te sigue al atacar» del cerebro táctico no le llegaba. | `gira_al_atacar` en `es.json` (`BossInfo.turnWhileAttacking`): con inteligencia, mientras dura un ataque se gira despacio hacia su objetivo (40 % del ritmo de los que andan: 2,6° por tick en Normal, lo que gira él al andar; nada con 0 % de inteligencia). Solo Ignevar (con todos los que no andan, la Hidra cambiaba: ella se gira con sus saltos). `sim_ai.py` igual; referencia de Ignevar actualizada (menos `walk_turn`, ya está de cara; los mismos ataques). |

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
| Demasiado cerca | `BossEntity.customServerAiStep` | Si el jugador está más cerca que el mínimo del ataque pensado, **se queda quieto** (no se pega más) hasta que algo salga o el jugador se aparte. Hasta la 1.3.83 se apartaba andando y hasta la 1.3.84 daba un salto atrás (`backstep`): en el juego se veía correr hacia atrás (§3.10), y MythicMobs no lo hace. |
| Rodear (inteligencia ≥ 0,6) | `BossEntity.customServerAiStep` | Si no tiene ningún ataque posible, **le rodea** (cambia de lado cada 2–4,5 s). Si está demasiado cerca para lo que quiere hacer, se aparta. |
| Seguir al atacar (inteligencia ≥ 0,4) | `SkillBrain.tracking` | Mientras ataca (`attacking`: gcd o lo que dura el ataque), se gira hacia el jugador a 2 + 8 × inteligencia grados por tick hasta que `lockmodel` lo congela. |
| Girarse parado | `BossEntity.turnBody` | `rde`/`rdu` del pack, acortados con la inteligencia (§3.1). |
| Andar | `BossEntity.turnBody` | El cuerpo hacia donde anda; la cabeza a su objetivo hasta `mh`; retrocede mirándole (1.3.83, §3.9). |
| Alcance de cada golpe y mirarle antes (1.3.83) | `SkillBrain.reachOf`, `usable`, `facing` | §3.9: se pone a la distancia a la que su golpe llega, no lo lanza al aire y se gira antes de un golpe por delante. |
| Objetivo (inteligencia ≥ 0,6) | `BossEntity.pickTarget` | Además de la amenaza: + a quien se cura, + a quien le dispara de lejos y, con ≥ 0,8, + a quien está por debajo del 30 % de vida. Solo cambia si el nuevo le compensa bastante. |
| Furia por fases (inteligencia ≥ 0,5) | `BossEntity.rhythm` | Por debajo del 50 % y del 25 % de vida, los cooldowns se acortan otro 12 % × inteligencia en cada fase. |

### 4.4 Ajustarlo

- **Los pesos:** están en `SkillBrain.score`. Si se tocan, se tocan **igual** en `tools/bosses/sim_ai.py`
  (`Brain.score`), que es una copia en Python (también `plan`, `chain`, `attacking`, `possible` y el tempo).
- **Comprobarlo:** `python3 tools/bosses/sim_ai.py --min 8 --dif demencial` (o `--smart X`). Muestra los ataques por
  minuto, cuántos distintos, cuántos repite seguidos, cuántas curas castiga, los combos (los pasos atrás, hasta la 1.3.84).
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

`BossWorld` hace lo que el motor pide al mundo: los esbirros (hasta 24 por jefe) y los asientos de los agarres
(`SeatEntity`; con `BossEvents` no se sale con mayúsculas). Desde la 1.3.83 **los jefes no sueltan objetos** (orden del
dueño, §3.9): los objetos siguen registrados (se pueden dar con comandos), pero `dropitem` no hace nada.

---

## 6. Herramientas de comprobación (sin abrir el juego)

**La red de seguridad** (desde la 1.3.81, orden del dueño: «no queremos que esto rompa a los bosses actuales y
venideros»): `python3 tools/bosses/verificar.py` pasa, en este orden, el auditor (0 problemas), su autoprueba,
`test_convert.py` y, por cada jefe del catálogo, compara con sus **referencias** de `tools/bosses/referencias/`:
`<jefe>.sim.txt` (el simulador de la pelea con 5 pasadas: semillas 3 y 11, el pack tal cual y varias dificultades,
`--min 8`) y `<jefe>.ficha.txt` (lo que el simulador no ve: la caja y los ojos de cada mob calculados como
`BossEntity.getDimensions` —si cambias esa función, cambia también `dims()` de `verificar.py`—, tipo, velocidad, vida,
daño, modificadores; cada modelo con sus huesos por tipo, cabezas y sus límites, animaciones y su duración, una huella
del archivo; y cuánto se doblan sus colas al girar). **GitHub Actions lo exige en cada compilación** (paso «Verificar
los jefes» de `tf-client.yml`, con Python 3.13): si algo cambia a un jefe ya publicado, la compilación se para.
- Si el cambio es **a propósito** (un arreglo, un pack reconvertido): `python3 tools/bosses/verificar.py --actualizar
  <jefe>` y explica en el commit y en HANDOFF.md qué cambia y por qué.
- Un **jefe nuevo** necesita sus referencias en el mismo cambio (`--actualizar <jefe>`); sin ellas, falla.
- Nunca se actualizan las referencias de todos los jefes «para que pase» sin mirar el cambio de cada uno.

| Comando | Para qué |
|---|---|
| `python3 tools/bosses/sim_ai.py [jefe] --min 8 [--seed N] [--dif P] [--smart X] [-v] [--antes]` | La IA. El jugador simulado se mueve, pega, sale despedido, a veces está detrás, se cura, se cubre y dispara. `--dif` pone una dificultad de `/tf boss` (su inteligencia, tempo y ritmo), `--smart` solo la inteligencia, `--antes` reproduce el motor de la 1.3.75 y `--sin-alcance` el cerebro de la 1.3.82 (sin mirar hasta dónde llega cada golpe ni girarse antes) para comparar. Saca los ataques por minuto, cuántos distintos de los que tiene, los que no salieron, cuántos repite, las curas castigadas, los combos y, desde la 1.3.83, los golpes lanzados fuera de su alcance («al aire») y los golpes por delante lanzados sin mirarle. Con `-v`, cada ataque y cada combo. |
| `python3 tools/bosses/audit.py [jefe ...] [--sin-avisos]` | El auditor (también al final de `build_bosses.py`). Tiene que dar **0 problemas** en cada jefe. Sabe solo lo que tiene el motor: lee las etiquetas `case` de `SkillRuntime.apply`, `SkillConds.test` y `SkillTargets.targets`, los `run(h, "...")` de `SkillMobs` (disparadores) y qué `case` no hacen nada. Los **avisos** (`·`) no cuentan pero dicen lo que no se verá igual (hoy: `segment`, las capas y colas rígidas). |
| `python3 tools/bosses/audit.py --prueba` | Se prueba a sí mismo (20 errores en la 1.3.81 —con un disparador del tipo de mob que no se copia y una correa en un hueso que no es `l_`—; 16 en la 1.3.80, también de modelos: UV fuera de la textura, pista de un hueso que no existe, textura que falta, IK rota). Antes: mete 11 errores a propósito en una copia del Esqueleto (mecánica, condición, objetivo, skill, animación, hueso, sonido y modelo que no existen, un disparador que no salta, un `~onAttack` que hace algo y una mecánica «de mentira» sin su porqué) y tiene que verlos todos. Si tocas el auditor, pásala. |
| `python3 tools/bosses/audit.py --cobertura` | El catálogo de MythicMobs 5 / ModelEngine 4 (`tools/bosses/mythic_ref.py`, con alias; `*` = habitual en jefes) contra el motor: cuántas hay, cuáles no hacen nada y cuáles faltan. En la 1.3.81: mecánicas 198/235 (faltan las que cambian el mundo o el inventario, dueño/montura y `disguise`: §11.3), condiciones 88/88, objetivos 47/47, disparadores 22/39 (faltan los del tipo de mob, regiones y cinemáticas). En la 1.3.79: 110/192, 81/88, 42/47, 11/26. |
| `tools/vfx_render.py` (`model_tris`, `raster`, `B.pose`) | Dibujar un modelo de jefe con una animación y unos huesos visibles (ver §7, paso 4). |
| `python3 tools/bosses/sim_gui.py [--png carpeta]` | La ventana `/tf boss` a 8 tamaños (también el panel de la grieta del Void, 1.3.89). |
| `python3 tools/bosses/sim_physics.py <modelo> [carpeta] [--anim idle] [--giro 120] [--anda 0.3] [--sin]` | Colas y segmentos (lo mismo que VfxPhysics): gira y anda el jefe, dice cuánto se aparta cada eslabón del de delante (no debe pasar de su límite) y dibuja fotogramas desde arriba y de lado. Usa la configuración `segment{}` del pack. |
| `python3 tools/bosses/test_convert.py` | Pruebas del conversor con .bbmodel hechos al vuelo: etiquetas, bezier, Molang, antes/después, escalón, Box UV, miembros (y slim), `b_`, `jig[repeat]`, la sombra, las cabezas de ModelEngine 4 y la IK llegando a su objetivo; y (1.3.81) lo de MythicMobs en el compilador y los simuladores: `switch`, alias de disparadores, `bounceConditions`, los objetivos de formas y bloques y las cuentas de `variablemath`. Tiene que decir «todo bien». |
| `xvfb-run -a python3 tools/bosses/grieta3.py --preview carpeta [--madre] [--estado lista\|despierta\|dormida] [--t s]` | La grieta 3.0 (o la madre) con sus shaders de verdad (Mesa, moderngl) desde cinco sitios (también «pegado», de cerca mirando arriba). |
| `xvfb-run -a python3 tools/void/cielo_preview.py carpeta [--t s] [--q calidad] [--size 1920x1080]` | El cielo de The Void (el agujero negro) con su shader de verdad, mirando a seis sitios. |
| `python3 -I tools/void/isla.py <schematic> --preview carpeta` | La isla del Void (`.bin` y un `isla.npz` para mirarla): plaza de la grieta, santuario de la punta norte. |

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
que se ve. Eso se comprueba **en el juego con la prueba de GitHub Actions** (§3.10: `[prueba-jefes]` en el commit) —
hazla tú antes de publicar un cambio de IA o de movimiento, no se la pidas al dueño— y, si el dueño manda capturas,
**mídelas con PIL** antes de dar algo por arreglado.

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
7. Antes de publicar: `python3 tools/bosses/verificar.py` «todo bien» (el auditor a 0 en TODOS los jefes, su
   autoprueba, `test_convert.py` y los demás jefes iguales que sus referencias), las referencias del jefe nuevo
   (`verificar.py --actualizar <jefe>`) y `sim_physics.py <modelo>` con imágenes en los que tengan `seg_`/`tl_`/`jig_`
   (que se doble sin pasarse del límite). GitHub Actions vuelve a pasar `verificar.py` y no deja publicar si falla.
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
| «No se gira / ataca de lado» | `lookYaw`/`bodyYaw`, `bodyrotation` del pack, `lockmodel` que no se suelta | `BossEntity.turnBody`, `SkillBrain.facing` (§3.9) |
| «Anda de lado» / «corre en retroceso» | El cuerpo debe ir hacia donde anda (1.3.83) y girar como el MoveControl (90°/tick, 1.3.84) | `BossEntity.turnBody` (rama «moving && walks»); la prueba en el juego (§3.10) cuenta «hacia atrás» y «de lado» |
| «Aparece, se va y reaparece» | La grieta se reinicia porque su chunk no tickea | `Rifts.tick` (chunks forzados, `EMPTY_TICKS`); prueba en el juego: «jefes distintos» |
| «Se va volando lejos» | Un impulso que no usa `Who.yaw()`, el vuelo del ghast, `altitude` | `SkillMotion`, `BossEntity.ghastStep`, `SkillConds.floorBelow`; prueba en el juego: «en el aire», «lo más lejos de la grieta» |
| «Tira ataques al aire» | Alcance de su golpe más corto que su condición; se lanzó sin mirarle | `SkillBrain.reachOf` (`sim_ai.py <id> --dif normal`: «al aire», «sin mirarle»; `-v`) |
| «La barra no se va» / «suelta objetos» / «no suena la música» | §3.9 | `BossEntity.die`, `BossEvents.onDrops`, `TFBossMusic` (volumen de «Discos») |
| «No anda» | `MovementSpeed` 0 sin `setspeed`, navegación de un mob muy grande, la correa | `BossEntity.applySpeed`, `customServerAiStep` |
| «No me ataca» | Estás en creativo; la `FollowRange` del pack; fuera de la correa | `BossEntity.pickTarget` |
| «Es tonto / muy predecible» | La inteligencia de su dificultad; los pesos del cerebro | `/tf boss` (fila Inteligencia), `SkillBrain.score` |
| «Va demasiado rápido / corta animaciones» | El ritmo o la furia; el tempo; un combo que corta | `Difficulty.rhythm`, `BossEntity.rhythm`/`tempo`, `SkillBrain.chain` (`noCancel`) |
| «Lento al girar» | `bodyrotation{rde;rdu}` del pack | `BossEntity.turnBody` (se acortan con la inteligencia) |
| «Se queda quieto delante de mí / gritando» | Sus ataques piden más distancia que a la que se pone; un `~onEnterCombat` que se repite | `SkillBrain.plan` (mínimo, `possible`), `SkillMobs.tick` |
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
| «La cabeza / el torso gira de más o se tuerce» | Cabezas de ModelEngine 4: globales, sin acumularse, cada una con sus límites | `VfxPose.build`, `VfxModel.headLimits`, `tools/vfx_bb.py` (`hi_`, `head_limits`) |
| «Se me ha puesto la pantalla negra / roja / un borde» | `blackscreen`, `bloodyscreen`, `bossborder` del pack (se quitan solos; el borde, al morir o irse el jefe) | `SkillMore`, `SkillScreen` |
| «Un golpe sale de otro sitio cuando el jefe mira o mueve la cola» | Desde la 1.3.81 el servidor dobla cabezas, colas e IK como el cliente | `vfx/VfxRig`, `SkillModels.pose` |

---

## 9. Ideas pendientes (si el dueño pide más)

- Lo que falta del motor (MythicMobs y ModelEngine) está en §11.

- **Fintas y ataques retrasados de verdad:** ralentizar el principio de una animación y retrasar a la vez sus golpes.
  Hoy solo «se lo piensa» antes de empezar.
- **Esquivas** cuando el jugador carga un golpe fuerte. No hay (el paso atrás `backstep` se quitó en la 1.3.84: sin
  animación propia resbalaba hacia atrás andando); los packs no la traen, salvo el `swoop_back` de la Gárgola.
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
  `tfclient:grieta`, `tfclient:asiento` y `tfclient:caballero` (el Caballero Sacro del Void).
- Las protecciones (WorldGuard, GriefPrevention...) ven el daño de los jefes como daño de un mob (sale de
  `SkillRuntime.hurt` con `mobAttack` del jefe): en una zona sin daño de mobs, los jefes no harán daño, y es lo
  esperado. Las explosiones de los jefes (`explosion`) no rompen bloques y los bloques que caen no se quedan.

---

## 11. Lo que falta (para la siguiente IA) — estado al publicar la 1.3.89

**1.3.88**: §3.13 (The Void con la isla del dueño y su cielo con agujero negro, Aurelion y el viaje, la grieta 3.0 con
shaders; Ignevar vuelve a volar y a girarse en el aire; el Guardián Hueco no se levanta dormido; el Contemplador no sube
al jugador). Cambios del motor que tocan a todos: los marcadores de una línea con `delay`/`repeat` se leen al salir
(antes una vez), `throw` normaliza en 3D y no mueve con `@self`, `variablemath` con `atan2`/`pow`/`exp`/`hypot` y no se
busca objetivo antes de los `~onSpawn`. Queda que el dueño lo pruebe y, en Ignevar, mirar en la prueba si sus llamaradas
apuntan bien (la prueba ahora lo dice: «lanzallamas ... apunta a ...»).

**1.3.87**: §3.12 (supersalto, `freeze`, `spawn` una vez, jefes que renacían al morir, grieta y cielo 2.0, música de
la grieta). El procedimiento exacto para seguir está en `SIGUIENTE-IA.md` (raíz del repo). Los packs de los 17 jefes
están **cifrados** en `packs/jefes-17.tar.enc` (la contraseña, el dueño). Queda que el dueño pruebe la 1.3.87 y las
dudas de `SIGUIENTE-IA.md` §6 (Contemplador, Archivista, bola de Astwood).

**1.3.86**: §3.11 (grieta del vacío, cielo de nebulosas, sonidos propios, rayos que respetan los bloques). Los packs
no van en claro en `ss`, que es público (el repo privado `tf-packs` no se pudo crear; el dueño aceptó subirlos cifrados).

**1.3.84**: la prueba en el juego en GitHub Actions y lo que encontró (§3.10: grietas que se reiniciaban, correr de
espaldas, correa que soltaba al jugador, Ignevar y el Contemplador volando lejos). Lo de la 1.3.83 que pedía «probar en
el juego» ya se mira ahí (barras al morir, objetos, muerte épica en las capturas); la música no se oye en la prueba
(el sonido va a 0).

**1.3.83**: lo que pidió el dueño al probar los jefes nuevos (§3.9: alcance real de los golpes, girarse antes de
golpear, no andar de lado, sin objetos, barra al morir, música de la pelea y muerte épica). **Sin probar en el juego**:
pedir al dueño que mire una pelea entera (que la música entre y se vaya, los rayos y el estallido de la muerte, que
no ande de lado ni golpee al aire). La música es una versión de Final Destination (Super Smash Bros. Brawl) de
AdamCrossing: va dentro del jar que se descarga desde GitHub; si alguien reclama sus derechos, cambiarla por otra
(`sounds/jefes/musica.ogg`, OGG Vorbis; la del dueño se pasó con `ffmpeg -c:a libvorbis -q:a 3` y se le quitaron los
4 s de silencio del final).

**1.3.82**: §3.8 y los 12 jefes de LittleRoom de la segunda tanda (Rey Calabaza, Archivista, Eldric, Druida Boreal,
Contemplador, Arconte Astral, Kazk, Guardián Hueco, Ignevar, Mecha Piglin, Mega Guardián, Tormenta del Wither). Quedan
**51 packs** de los 64 que mandó el dueño (en el contenedor de la sesión, no en el repo: hay que volver a pedírselos si
se pierden) y **Pohaku**, que espera al dueño (§11.0).

### 11.0 Packs nuevos: lo que queda y lo que hay que preguntar

- **Pohaku**: su pelea necesita el escenario del pack (`schematic`) con balistas y mercenarios que el admin pone a
  mano (`/mm mobs spawn`); sin ellos no se le aturde y no recibe daño. Preguntar: ¿se monta el escenario en la
  grieta (que el jefe saque sus balistas y mercenarios al aparecer) o se deja fuera?
- **IA de tipo que falta** (el auditor la marcará): `bowattack`/`fleewolf` (esqueletos de Abyss, Furacas, Moon Glow,
  UKing, Guardians of Light), la carga del vex (Void Hydra, las cajas de Xiddri), el slime (Abyss, Furacas), el
  fantasma (Void Hydra), el enderman (Toro EnderDragon), `rangedattack` (ElementalFire), `movewithindistanceoftarget`
  (samus2002); búsqueda por facción (`nearestotherfaction`, `specificfaction`: ElementalFire, Gilded Guardians,
  FallenDefender, FallenHeroes, MexBots).
- **Inventario o estado del jugador** (preguntar): `consumeslot` (Mega Guardián mascota, Moon Glow), `giveitem`,
  `setlevel`, `giveexperiencelevels`, `setitemnbt`, `consumeuseddurability`, la condición `equipslot`.
- **Tamaño del jar**: los 17 jefes suman ~29 MB de recursos (los sonidos, 18 MB); con los 64 packs el jar pasaría de
  100 MB. Si el dueño lo pide, se pueden recodificar los `.ogg` (mono, menos calidad).
- **Licencias**: los packs de LittleRoom dicen que no se pueden poner a descargar en sitios de terceros (el jar va en
  las Releases públicas de GitHub). Lo decide el dueño (ya estaban los 4 primeros de LittleRoom).

**Hecho y publicado**: todo lo de §3.6 y §3.7. **Sin verificar en el juego**: nada de la 1.3.75 a la 1.3.81 lo ha
probado nadie todavía; las pruebas son el simulador (`sim_ai.py`), la física copiada en Python (`sim_physics.py`),
el conversor y los simuladores de skills (`test_convert.py`) y el auditor (`audit.py`, `--prueba`). Compila en GitHub
Actions.

### 11.1 Lo primero

1. **Prueba en el juego** (pídeselo al dueño, en SUPERVIVENCIA, con capturas o vídeo; mide las capturas con PIL):
   las cabezas al mirar (la Gárgola con `hi_chest` + `hi_head`: el torso y la cabeza giran una vez, no el doble); la
   cola y las estelas de la Gárgola y la capa del Esqueleto al girar y andar; que los golpes salgan de donde se ven
   (ahora el servidor dobla cabezas y colas); el parpadeo rojo (el Esqueleto no parpadea: `d=false`); que suban un
   bloque andando (`step`). Las 6 animaciones reconvertidas con bezier: `soul_sword_attack_3` del Esqueleto, `spawn`
   de la rotura del escudo y del agujero de invocación, e `idle`, `walk` y `land` de la cabeza cortada de la Hidra.
   Si algo se ve mal, §8.
2. **Mohist** (§10): con los ajustes de `spigot.yml` del dueño, comprobar alcance de seguimiento y de activación y que
   ningún limpiador de entidades borre `tfclient:jefe`/`esbirro`/`grieta`/`asiento`.

### 11.2 ModelEngine 4 que falta

`--cobertura` ya no lista ninguna mecánica de ModelEngine sin hacer. Queda **decidir con el dueño**:

- **Altura de los ojos**: en ModelEngine es el pivote del hueso `hitbox` del modelo; aquí sigue la del tipo de Minecraft.
  Cambiarla mueve de dónde salen proyectiles, miradas y `@SelfEyeLocation` en los 5 jefes (hay que mirarlo con
  `sim_ai.py` y en el juego).

### 11.3 MythicMobs que falta

Todo esto lo marca el auditor como problema en cuanto un pack lo use (salvo lo de `STUB_OK`).

- **Lo que cambia el mundo** (no se ha hecho: **pregúntale al dueño**, el servidor tiene construcciones):
  `breakblock`, `fill`, `blockphysics`, `blockdestabilize`, `setblockopen`, `time`, `weather`.
- **Lo que cambia el estado o el inventario de un jugador** (también para preguntar): `giveitem`, `takeitem`,
  `pickupitem`, `decapitate` (da su cabeza como objeto), `setgamemode`, `setlevel`, `fly`, y escribir en el marcador
  (`modifyscore`, `modifytargetscore`, `modifyglobalscore`/`setglobalscore`, que hoy no hacen nada; las condiciones
  `score`/`globalscore` ya leen el marcador de verdad).
- **Dueño, padre y montura**: `setowner`, `removeowner`, `setparent`, `mountme`, `remount` y la opción `Mount:` del
  mob (el motor de jefes no la lee; `mount`/`dismount`/`mounttarget` siguen en `STUB_OK` porque los packs de ahora no
  montan sus efectos). Hoy el dueño de un efecto es quien lo invoca (`SkillActor.owner`, final) y el de un esbirro es
  su jefe: cambiarlo toca a quién se cuenta el daño y a quién no se ataca.
- **`disguise`**: necesita Libs' Disguises en MythicMobs; aquí haría falta dibujar el mob con la forma de otra entidad.
- **El comportamiento propio del `Type` de Minecraft**: desde la 1.3.82 `BossEntity` copia su IA (§3.8: persigue,
  pega con el golpe de Minecraft, el ghast vuela y dispara); falta lo que marque el auditor (`type:x` fuera de
  `mythic_ai.AI_OK`): un creeper no explota, un esqueleto no dispara su arco, un enderman no se teletransporta. Con eso irían `~onExplode`, `~onPrime`,
  `~onCreeperCharge`, `~onTeleport` (y `~onTame`, `~onBreed`, `~onTrade`, `~onBucket` para los que se dejan). El
  auditor ya distingue: si el `Type` del pack no lo hace, es un aviso; si lo hace, un problema.
- **Regiones y cinemáticas** (`~onEnterBounds`, `~onExitBounds`, `~onPlayerEnter`, `~onPlayerExit`,
  `~onCinematicStart/End`): necesitan las regiones (`Bounds`) y las cámaras de MythicMobs.

### 11.4 Aproximaciones que conviene saber (no son fallos, pero no son idénticas)

- Molang: `q.life_time` = `q.anim_time`; `math.random` vale la mitad del rango (para que el horneado sea estable); las
  consultas del mundo (`q.ground_speed`, `q.is_on_ground`...) valen 0 y salen como problema al convertir.
- Efectos `geyser`, `firework`, `smoke`, `flames`, `ender`, `spring`, `atom`: ráfagas de partículas de Minecraft que se
  parecen, no copias exactas de MythicMobs.
- `billboard HORIZONTAL` gira solo con el cabeceo de la cámara. `vfxpos/rot/scale` relativos: a quien llega tarde se le
  repite solo el último.
- `segment`: los límites `minz`/`maxz` no se usan (el giro sobre el propio eslabón no cambia hacia dónde apunta).
- (1.3.81, la wiki no lo dice) `@Cone`: puntos repartidos por área en cada capa; `@Sphere`: en la superficie;
  `@Rectangle`: centrado en quien lanza, `points` por arista, caras por defecto; `@BIR`: la esquina de cada bloque y
  `noise` = probabilidad de saltarse uno; `blackscreen`: negro opaco en tramos de 10 ticks; `skybox` > 1: más oscuro
  (el truco del nivel de lluvia); `bossborder`: se quita al morir/irse quien lo puso; `teleportin{yaw}`: el objetivo
  queda mirando hacia quien lanza + `yaw`; `hologram`: texto sin fondo; `~onShoot` salta con `shoot`/`shootfireball`
  (aquí no son flechas de verdad) y `bow-tension` = 1; `~onProjectileHit` una vez por cada ser tocado; `~onHear`
  copia al Warden (16 bloques, viaje de un tick por bloque y 2 s de calma).
- `enderbeam` solo se dibuja (el cristal de MythicMobs es real y puede explotar); `itemrecharging` mira el
  enfriamiento del objeto de la mano; `mobsinchunk` cuenta seres vivos que no son jugadores y efectos del pack.

### 11.5 Prompt para seguir (cópialo tal cual en una sesión nueva)

```
Lee SIGUIENTE-IA.md, CLAUDE.md, el CHECKPOINT de HANDOFF.md y tf-client/JEFES.md entero (sobre todo §3.6-§3.12,
§10 y §11). Estamos con el motor de jefes de TF Client (1.3.87): tiene que ser fiel a MythicMobs + ModelEngine 4 para todos los packs que
metamos; el servidor es Mohist 1.20.1. Reglas: responde en español, no uses subagentes, compila SIEMPRE con GitHub
Actions (nunca ./gradlew en el contenedor), publica como Release (sube mod_version y TFClient.VERSION, PR a main,
squash, comprueba la Release), nunca subas los packs al repo.

Tareas, en este orden:
1. Lo que el dueño haya decidido de JEFES.md §11.2 (altura de los ojos) y §11.3 (lo que cambia el mundo o el
   inventario, dueño/montura); y si se queja de algo en el juego, reprodúcelo TÚ con la prueba en el juego (§3.10,
   `[prueba-jefes=<jefes>]` en el commit; espera en segundo plano con tools/bosses/esperar_prueba.sh), lee el
   informe y las capturas, arréglalo (§8) y vuelve a probar.
2. El comportamiento propio del Type de Minecraft (§11.3) si el dueño trae un pack que lo use.
3. Cada cosa nueva: con la semántica de la wiki de ModelEngine/MythicMobs (léela con WebFetch), en sim_ai.py si
   cambia la pelea, en mythic_ref.py y en las pruebas (test_convert.py / audit.py --prueba).
Antes de publicar: audit.py con 0 problemas, audit.py --prueba entero, test_convert.py «todo bien», sim_physics.py
en los modelos con colas, y sim_ai.py igual en los demás jefes (misma --seed y --min 8, antes y después). Documenta en
JEFES.md (§3.6, §3.7 y §11) y en HANDOFF.md lo que hagas y lo que quede.
```
