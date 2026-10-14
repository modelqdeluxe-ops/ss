# Jefes de incursión de TF Client: documentación técnica completa

Este documento sirve para que otra IA (o persona) retome los jefes sin tener que redescubrir nada. Léelo después del
CHECKPOINT de `HANDOFF.md`, porque las reglas del dueño están allí (español, nada de subagentes, compilar con GitHub
Actions, publicar como Release y sin abrir nunca el juego ni un servidor).

- **Estado:** TF Client **1.3.77**.
  - 1.3.75: jefes, grietas y `/tf boss`.
  - 1.3.76: la IA, fiel a MythicMobs/ModelEngine.
  - 1.3.77: el cerebro táctico.
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

Debe decir `problemas: 0` en cada jefe.

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
| `bosses/models/<id>.<modelo>.json` | Los `.bbmodel` convertidos por `tools/vfx_bb.py` en **modo jefe**: prioridad y override de las animaciones, scripts `mm:` de la línea de tiempo (`fx`), cajas `ob_` (`hb`), asientos `p_`, cabezas `h_`/`hi_`, el hueso `hitbox` que da el tamaño de la entidad, y texturas `_e` que brillan. |
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
  + 1 bloque) y aplica el plan del cerebro (espaciarse y rodear). La correa con su grieta es de 40 bloques.
- **`turnBody()`:** el giro, como ModelEngine.
  - `lookYaw` es hacia dónde mira la **entidad**: sigue al objetivo a 30° por tick. Con eso miden fieldOfView,
    @forward y los proyectiles.
  - `bodyYaw` es el **modelo**. Andando, sigue esa dirección. Parado, la cabeza no pasa de `mh` y arrastra al cuerpo,
    y pasados `rde` ticks el cuerpo se pone de frente en `rdu`.
  - Mientras ataca (gcd), con el cerebro, sigue al jugador hasta que `lockmodel` lo congela.
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
| `onentercombat` | Al tener objetivo. |
| `ondamaged` | Al recibir un golpe. Lleva `damage-amount` y `damage-cause`. |
| `ondeath` | Al morir. |
| `onsignal:X` | Al recibir `signal{s=X}`. |
| `onload` | No salta: no se guardan en el mundo. |
| `onattack` | No salta: no hay golpe cuerpo a cuerpo de Minecraft. En los packs solo lo usan para cancelarlo. |

Los scripts `mm:` de la línea de tiempo de las animaciones se lanzan desde `SkillMobs.scripts`.

### 3.3 SkillRuntime: reglas de MythicMobs que importan (no las rompas)

| Regla | Dónde | Por qué |
|---|---|---|
| El cooldown de una skill **solo se pone si sale** (después de sus condiciones y de las de sus objetivos). | `runMeta` | Si se pone antes, los ataques que se miran cada segundo se quedan en recarga sin salir (antes de la 1.3.76 quedaba uno). |
| `setvar{...;duration=N}` **caduca** a los N ticks (vuelve a valer «UNDEFINED»). | `putVar` | La Hidra y la Abominación marcan «estoy mordiendo / saltando» para unos segundos. |
| `gcd{ticks}` es **siempre de quien lanza** y **no se divide por el ritmo** de la dificultad. | `apply` → `gcd` | Es lo que dura el ataque. Si se acorta, el siguiente corta la animación a medias. |
| En los jefes, las mecánicas de ModelEngine **sin objetivo** van a su propio modelo. | `targets()` + `OWN_MODEL` | `bodyrotation{mh=0}` dentro de un ataque lanzado con @target es para el jefe, no para el jugador. |
| `propel` en los jefes: «v» bloques por tick hacia el objetivo (se pone, no se suma). | `SkillMotion.propel` | Con la regla de las clases (v/10, sumado) la Gárgola no saltaba. |
| `setspeed{s}` = velocidad **del tipo** de Minecraft × s. Hasta el primer setspeed manda `MovementSpeed`. | `BossEntity.applySpeed` | La Abominación sale con MovementSpeed 0 y su `setspeed{s=1.25}` la pone a andar. |
| `potion{level}` en los jefes = **amplificador** (0 = I). En las clases, nivel − 1, como estaba. | `potion()` | El escudo del Esqueleto (`DAMAGE_RESISTANCE level=4`) es Resistencia V: inmune. |
| `throw` y `pull` van entre 10 (como MythicMobs); `lunge` no. | `SkillMotion` | Velocidades del pack. |
| `playerWithin` cuenta a quien está en creativo (no a los espectadores). **Pero no atacan a quien está en creativo**, como en MythicMobs. | `SkillConds`, `BossEntity.pickTarget` | El staff los despierta; para probar la pelea, en supervivencia. |
| Los golpes de jefe dan 10 ticks de inmunidad por mob (como MythicMobs). | `hurt()` | |

### 3.4 Animaciones: VfxLayers (ModelEngine 4)

- **Prioridades:** cada **prioridad** (`state{p=N}`) es una máquina de estados con **una** animación. La nueva sustituye
  a la que hubiera en esa prioridad, fundiéndose en su `li`. Las prioridades se apilan de menor a mayor.
  - Con **override** (la casilla de Blockbench, `ov` en el JSON), tapa a las de debajo **en los huesos que mueve**.
  - Sin override, se suma.
- **Fin de una animación:** una de una vez (`once`) acaba sola y libra su prioridad; `hold` se queda en su final;
  `loop` se repite.
- **Por defecto:** debajo de todo va la animación por defecto (`idle`/`walk`, o lo que ponga `defaultstate`).
- **Muerte:** `death` lo tapa todo.
- **Ejemplo:** el «sleep» del Esqueleto lo sube 5,6 bloques sobre el trono. Si no se sustituye al despertar, se queda
  flotando (fallo de la 1.3.75).
- **Servidor y cliente:** el servidor lleva las mismas capas (el actor «disfraz» del jefe, `SkillModels.disguise`) para
  saber dónde está cada hueso (`@modelpart`, tótems de los golpes) y lanzar los scripts. El cliente las reproduce con
  los mensajes `lstate`/`lstop`/`dstate`/`death`, y a quien llega tarde se le repiten (`BossEvents` →
  `SkillMobs.replay`).

### 3.5 Golpes

Casi todos los ataques de los packs son **tótems** en un hueso (`totem{oh=X} @modelpart{pid=r_hand}`, repetidos cada
tick mientras dura el golpe): `SkillMovers.totem`. Por defecto el tótem va 1 bloque por encima del sitio.

`basedamage{m}` = daño de ataque del jefe (con la dificultad) × m. Los daños fijos (`damage{a=15}`) se multiplican por
el daño de la dificultad.

Los proyectiles «MOB» (las llamas en `text_display`) y los «display» (la esfera de Astwood) son actores del cliente.

---

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
  - Nunca a mitad de un ataque, mientras haya gcd.
  - Si el jugador **se cura** (come o bebe) y la inteligencia es ≥ 0,5, decide en cuanto quede libre.
- **Qué puede salir:** prueba cada ataque **sin lanzarlo** (`SkillRuntime.usable`). Mira el cooldown, las condiciones
  de la skill y de sus objetivos y el umbral y las condiciones de la línea.
- **Se lo piensa:** con inteligencia ≥ 0,5, el 16 % de las veces espera 3–12 ticks antes de atacar (no es predecible).
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
| Repetir el último ataque | −2,2 (el penúltimo −0,9, los otros recientes −0,3) |
| Aprender | +1,2 × aciertos/usos − 0,35 × fallos seguidos (con 2 usos o más) |
| Azar | entre 0 y 0,35 + 2,6 × (1 − inteligencia) |

- **Lanzar:** sale el que más puntúa, con `exec` de su línea o `callNamed` si es de una `randomskill`.
- **Encadenar:** si ese ataque **no pone gcd** (las cabezas de la Hidra), puede salir otro distinto a la vez, hasta 3,
  con una probabilidad de 0,35 + 0,4 × inteligencia.
- **Aprender:** los golpes del jefe que dan a un jugador (`SkillRuntime.hurt` → `SkillMobs.landed`) cuentan para el
  ataque en curso (70 ticks).

### 4.3 Movimiento, giro, objetivo y furia

| Qué | Dónde | Cómo |
|---|---|---|
| Plan de movimiento (inteligencia ≥ 0,4) | `SkillBrain.plan` | Se pone a la distancia ideal (`sweet`) de un ataque que tendrá listo pronto; la cambia cada 3–6 s. |
| Rodear (inteligencia ≥ 0,6) | `BossEntity.customServerAiStep` | Si no tiene ningún ataque posible, **le rodea** (cambia de lado cada 2–4,5 s). Si está demasiado cerca para lo que quiere hacer, se aparta. |
| Seguir al atacar (inteligencia ≥ 0,4) | `SkillBrain.tracking` | Mientras ataca, se gira hacia el jugador a 2 + 8 × inteligencia grados por tick hasta que `lockmodel` lo congela. |
| Objetivo (inteligencia ≥ 0,6) | `BossEntity.pickTarget` | Además de la amenaza: + a quien se cura, + a quien le dispara de lejos y, con ≥ 0,8, + a quien está por debajo del 30 % de vida. Solo cambia si el nuevo le compensa bastante. |
| Furia por fases (inteligencia ≥ 0,5) | `BossEntity.rhythm` | Por debajo del 50 % y del 25 % de vida, los cooldowns se acortan otro 12 % × inteligencia en cada fase. |

### 4.4 Ajustarlo

- **Los pesos:** están en `SkillBrain.score`. Si se tocan, se tocan **igual** en `tools/bosses/sim_ai.py`
  (`Brain.score`), que es una copia en Python.
- **Comprobarlo:** `python3 tools/bosses/sim_ai.py --min 8 --smart 1`. Muestra los ataques por minuto, cuántos
  distintos, cuántos repite seguidos y cuántas curas castiga.
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
| `python3 tools/bosses/sim_ai.py [jefe] --min 8 [--seed N] [--smart X] [-v] [--antes]` | La IA. El jugador simulado se mueve, pega, sale despedido, a veces está detrás, se cura, se cubre y dispara. `--smart` usa el cerebro y `--antes` reproduce el motor de la 1.3.75 para comparar. Saca los ataques por minuto, cuántos distintos de los que tiene, los que no salieron, cuántos repite y las curas castigadas. |
| `python3 tools/bosses/sim_gui.py [--png carpeta]` | La ventana `/tf boss` a 8 tamaños. |
| `python3 tools/bosses/rift_model.py --preview carpeta` | Imágenes del cristal. |

**Referencia** (1.3.77, `--min 8`):

| Jefe | Ataques distintos |
|---|---|
| Gárgola | 5/5 |
| Hidra | 14/15 (la rotura de cabezas sale por debajo del 50 %) |
| Abominación | 6/7 (el agarre depende de que el tentáculo pille) |
| Esqueleto | 3 fases; en 16 min, 24/26 |
| Astwood | 12/12 |

Con el cerebro al 100 %, Astwood y la Abominación atacan el doble de veces que con el pack solo y casi no repiten
seguido.

**Lo que el simulador no ve:** dónde caen de verdad los golpes (los tótems en los huesos), la física, el pathfinding y lo
que se ve. Eso solo se comprueba en el juego. Pídeselo al dueño, en supervivencia, y **mide sus capturas con PIL**
antes de dar algo por arreglado.

---

## 7. Añadir un jefe nuevo (pasos)

1. Descomprime el pack (fuera del repo).
2. Añádelo a `JEFES` (en `build_bosses.py`) y a `tools/bosses/es.json`.
3. Ejecuta `build_bosses.py`. Si dice «falta skill X», complétala en `FIXES` imitando las parecidas del pack.
4. Mira las hojas de sus modelos (render en Python, como en la 1.3.75: `tools/vfx_render.py`) y su `preview_hide`.
5. Ejecuta `python3 tools/bosses/sim_ai.py <id> --min 8` y con `--smart 1 -v`:
   - ¿salen todos sus ataques?
   - ¿las etiquetas del cerebro tienen sentido?
   - Si algo no sale nunca, busca por qué con `-v` (condiciones, variables, fases).
6. Mecánicas o condiciones nuevas: si el pack usa algo que el motor no tiene, sale en el log del juego como «TF Skills:
   …». Impleméntalo en `SkillRuntime`/`SkillConds`/`SkillTargets` **con la semántica de MythicMobs**, solo para jefes
   si cambia algo de las clases (`ctx.host != null`), y cópialo en `sim_ai.py`.
7. Sube la versión (`mod_version` y `TFClient.VERSION`), apunta la entrega en `HANDOFF.md`, haz push a la rama
   (compila en Actions), PR y fusión. Comprueba la Release.

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
| «Va demasiado rápido / corta animaciones» | El ritmo o la furia | `Difficulty.rhythm`, `BossEntity.rhythm` (el gcd no se divide) |

---

## 9. Ideas pendientes (si el dueño pide más)

- **Fintas y ataques retrasados de verdad:** ralentizar el principio de una animación y retrasar a la vez sus golpes.
  Hoy solo «se lo piensa» antes de empezar.
- **Esquivas o pasos atrás genéricos** del jefe cuando el jugador carga un golpe fuerte. Haría falta una animación de
  paso: los packs no la traen, salvo el `swoop_back` de la Gárgola.
- **Memoria entre peleas:** qué ataques esquiva cada jugador, guardado por grieta.
- **Coordinación de esbirros:** rodear, cortar la retirada.
- **Ver en el juego:** un modo depuración para el staff que muestre encima del jefe el ataque elegido y su puntuación.
