# Clanes (TF Client) — la actualización grande: estado y cómo seguir

> Léelo entero antes de tocar los clanes. Reglas de siempre: responde al dueño en español, **sin subagentes**, compila
> **solo con GitHub Actions** (`tf-client.yml`, nunca `./gradlew` en el contenedor), no subas jars, prueba en el juego
> tú mismo con `[prueba-jefes=<escenario>]` y mide las capturas con PIL antes de decir que algo está bien.

## 0. Dónde lo dejamos (31 oct 2026, el dueño pidió parar y dejar el checkpoint)

Rama `claude/wizardly-euler-tw8ooy`, encima de `main` (que tiene la web de los Términos 2026-10-30.2 y la 1.3.154):

1. **TF Client 1.3.155: hecha y VERIFICADA en el juego, pero SIN PUBLICAR** (commits `71c52054` y `29bbc60b`, más el
   commit de este checkpoint). Planeo de los sets ilimitado y sin hambre, fondo del menú del pad y 44 marcos de fotos.
   La segunda prueba `[prueba-jefes=pad]` (29bbc60b) dio: «marcos: … bien; 44 marcos», «planeo: hambre 0.00 → 0.00 al
   planear, 3.00 sin planear; límite ninguno (bien)», y las miniaturas de marcos y fondos ya sin el velo oscuro
   (medido: «Noche estrellada» (28,26,71), «Cerezos» (241,221,226)). Detalle en el CHECKPOINT 1.3.155 de `HANDOFF.md`.
2. **Encima, el commit `d2f4bf5d` «WIP clanes fase 2» SIN TERMINAR** (los archivos de la §3). El último commit con
   solo la 1.3.155 (y este documento) es `484bdf00`. Los números de versión siguen en
   1.3.155. Mira en el workflow `tf-client.yml` de ese commit si compila; si no, arréglalo primero.

**El dueño quiere UN SOLO PARCHE CON TODO** (una versión, la **1.3.156**, y una Release; nada suelto). Orden de
trabajo: (1) la caída del servidor por la GTS (el dueño ya quitó la venta, pero el fallo sigue en el código) y (2) las
flags de spawn de las protecciones, las dos en el CHECKPOINT del 31 oct de `HANDOFF.md`; (3) lo de la 1.3.155, ya
hecho; (4) los clanes: termina la fase 2 (§3, «Falta») y sigue con las demás. Si las fases 3–5 van a tardar mucho,
pregúntale al dueño si espera o si el parche sale con la fase 2. Súbelo a 1.3.156 (`mod_version` en
`tf-client/gradle.properties` y `VERSION` en `TFClient.java`), pruébalo todo en el juego, apunta un CHECKPOINT en
`HANDOFF.md` y publícalo una sola vez.

Cómo publicar (siempre igual): PR borrador de la rama a `main` → listo → squash merge → comprobar que existe la Release
`tfclient-v<versión>` (el CI tarda unos minutos) → rehacer la rama:
`git fetch origin main && git checkout -B claude/wizardly-euler-tw8ooy origin/main && git push --force-with-lease -u origin claude/wizardly-euler-tw8ooy`
(si tienes cambios sin subir, guárdalos antes con `git stash -u` y recupéralos después).

## 1. Lo que pidió el dueño (literal, para no perder nada)

> Meteremos un nuevo coin, este sera azul, pero este sera llamado Fantastic Clan Coin, y solo lo tendra el lider y el
> colider en su cuenta. Con este pues lo canjearan en la tienda del clan, en el apartado de clanes. Y aqui esta lo de
> esta actualizacion:
> 1 Banco del clan (un cofre con slots mejorables mediante las clan coins, este cofre sera custom, podra contener cuanto
> item quiera comprimido para evitar el lag por tanta info metida en el, … es un almacenamiento que solo tiene el nombre
> del item y cantidad que metieron sin tenerlo adentro … como digital nomas … este cofre debes buscar una textura custom
> bien bonita y trabajada, el costo de la mejora es incremental, digo que 5k de items inicial? no se y asi de 5k en 5k
> hasta que cifra es razonable? ya que es un cofre del clan no se.
> 2 Nivel del clan, misiones del clan.
> 3 Duelos, puedes retar a cualquier jugador que este dentro de un clan, con el comando que usamos para el del chat del
> clan pero agregas el sub comando de duelo (player), ojo solo reconoce personas dentro de un clan, y no acepta duelos
> entre los mismos integrantes, y pues el clan con esos duelos sube prestigio, y en el ranking del pad pues agregar una
> pestaña de clanes, su logo, info y prestigio. Guerras de clanes, esta sera bonita, esta guerra no sera de matarse
> entre ellos, sera completando objetivos, diseña bien bonito ese apartado, todo dentro de la app de clanes, objetivos
> tipo misiones y asi, ojo no vayas a meter cosas que no se puedan cumplir o que esten en el nether y end, ya que vamos
> por early game apenas, haz tipo una barra de progreso vertical asi como la del pase esta en horizontal pero esta es
> vertical y ambos lados con su clan y color y todo, y la barra de progreso hasta la meta se llena completando misiones
> de la guerra, ojo no misiones faciles tampoco, misiones desafiantes ya que contaran como equipo entre todos pues
> considera eso.
> 3 Alianza para compartir progreso de misiones y avanzar mas rapido en el prestigio del clan, mira en los clanes no hay
> recompensas para coins amarillas, solo para verdes o coins del clan.
> 4 Haz que el tag del clan tenga una paleta extensa de colores, editable, no quiero esas putas monturas, quiero literal
> una paleta como las de paint.
> 5 En la tienda del clan vamos a vender protecciones del clan, estas no son las que tenemos, si no que son otras, mira
> cuales tenemos y dime cuales podemos poner, y no se que mas vender, dame ideas.
> 6 [arreglos del pad y los sets: hechos en la 1.3.155]

Y a las preguntas: «El estandarte sería un icono o badge que tú hagas, haz muchas» (hecho: 48 insignias), eligió
también **«Territorio del clan»** (fase 5) y, de los arreglos, «ya te los di» (los del punto 6).

Reglas que salen de ahí:
- **Ninguna recompensa de clanes da monedas amarillas**: solo verdes (`PadGreen`) o Fantastic Clan Coins.
- **Nada del Nether ni del End** en misiones ni guerras (early game); todo cumplible en el Overworld.
- Las Clan Coins son **del clan** (un monedero en `clanes.json`, `monedasClan`) y solo las ven y gastan el líder y los
  colíderes (`PadClanProgress.treasurer`).
- Nada de colores con armaduras de caballo («monturas»): se quitó el `swatch`/`colorView` viejo de `PadClans`.

## 2. Fase 1 (1.3.155): hecha y verificada (ver `HANDOFF.md`)

`items/TFWings` (`GLIDE_TICKS = Integer.MAX_VALUE`), `mixin/PlayerFoodWingsMixin`, `pad/PadWallpaper`,
`pad/PadWallpaperPage`, `pad/PadPhotoFrames`, `PadCameraPage` (elegir marco), `PadCommunityClient.encode` (lo pega),
generadores `tools/pad/fondos.py` y `tools/pad/marcos.py`, prueba en `bosses/BossTest` (escenario `pad`, casos 1358–1566).

## 3. Fase 2 (1.3.156): núcleo de los clanes — EN CURSO (commit WIP)

### Hecho (escrito, sin compilar ni probar todavía)
- `tools/pad/clanes.py`: genera la moneda azul (`textures/gui/pad/coin_clan.png`, `coin_clan_s.png`,
  `textures/item/fantastic_coin_clan.png`, recoloreando la dorada) y **48 insignias** pixel art de 64×64
  (`textures/gui/pad/insignias/<id>.png` + `pad/insignias.json` `[{id, nombre}]`). Se vuelve a correr sin problema.
- `pad/server/PadClanProgress.java` (nuevo): todo el progreso del clan, guardado en el objeto del clan de `clanes.json`:
  - `monedasClan` (Clan Coins: `coins/addCoins/takeCoins`, `treasurer(c, uuid)` = líder o colíder, `walletOf(uuid)` =
    -1 si no es tesorero), `nivel`/`xp` (`MAX_LEVEL = 20`, `xpFor(l) = round(1000·1,35^(l-1)/50)·50`; al subir, 100 ×
    nivel Clan Coins y aviso al clan), `prestigio`, `insignia` (`badge(c)`, por defecto la primera),
    `maxMembers(c) = min(40, 20 + 2·(nivel-1))`.
  - Misiones semanales: `POOL` de 20 (piedra, troncos, carbón, hierro, cobre, pala, cultivos, hostiles, zombis,
    esqueletos, arañas, creepers, pesca, criar, antorchas, herramientas, pan, lingotes, colocar, encantar; metas para 5
    miembros, `goal()` las escala por miembros ×clamp(0,5+0,1n, 0,7, 2,5)); `MISSIONS_PER_WEEK = 4` elegidas con
    `Random(semana·1_000_003 + id.hashCode())`; se guardan en `misiones = {semana, lista[{id, meta, progreso, hecha}],
    aportes{uuid: n}}`. `record(player, tipo, match, n)` (lo llama `TFJobs.record`) suma al clan y **a los aliados la
    mitad** (`ALLY_SHARE = 0.5`, lista `aliados`); `complete()` da Clan Coins, prestigio, XP y **1 moneda verde a cada uno
    que aportó** (`PadGreen.add(server, uuid, 1)`, que ya sirve con el jugador desconectado).
  - `byPrestige()` para el ranking.
- `pad/server/PadClanViews.java` (nuevo): `hero()` (ficha con la insignia, nivel y barra, prestigio, miembros, y las
  Clan Coins para los tesoreros), `missions()` (las 4 de la semana con barra y los 5 que más aportan), `style()` (la
  etiqueta con su color, botón PALETA, todas las insignias en tarjetas, campo «colhex») y `ranking()` (top 20 por
  prestigio con insignia, nivel, miembros y líder).
- `PadClans.java`: pestañas MIEMBROS (con la ficha), MISIONES, ESTILO (solo el líder; alias `color`) y AJUSTES (fila
  «Color e insignia»); acción `insignia:<id>`; tope de miembros con `maxMembers`; `allies(c)`. Las acciones de color
  `col:#RRGGBB` y `colhex` ya existían (1.3.132) y siguen. La constante `MAX_MEMBERS` ya no se usa (quítala).
- `PadRanking.java`: pestaña **CLANES** (`PadClanViews.ranking`).
- `PadUi`/`PadView`: el prefijo `¢` en los chips = Clan Coin azul (`PadView.clanCoins(v)` → «¢1.250», `coin_clan_s`,
  tono azul).
- `TFPadNet.State` lleva `clanCoins` (escrito/leído después de `green`; -1 = no es tesorero) y `TFPadScreen` la dibuja
  arriba con `coin_clan` en azul (0x9CD2FF) solo para el líder y los colíderes. **Comprueba en la captura el orden con
  la verde y la dorada** (se pintan de derecha a izquierda).
- `TFJobs.record` llama también a `PadClanProgress.record`.

### Falta (en este orden)
1. **Que compile** (CI `tf-client.yml` del commit WIP).
2. **La paleta tipo Paint** (cliente): una página `pad/PadColorPicker` (extiende `PadPage`, como `PadWallpaperPage`):
   cuadro de saturación/brillo (una `fillGradient` por columna, de blanco→tono arriba a negro abajo), barra de tono
   vertical, fila de colores básicos (los de Paint: 28), muestra antes/después con la etiqueta `[TAG]` pintada, campo
   hex editable, GUARDAR y CANCELAR. Al guardar manda la acción `col:#RRGGBB` a la app `clanes`, pestaña `estilo`
   (`TFPadNet.Action("clanes", "estilo", "col:#RRGGBB", "")`) y vuelve a abrir la app en ESTILO. `backTo()` → la app
   de clanes.
   El botón PALETA de `PadClanViews.style` ya manda `§color:clanes:estilo:<HEX>:<TAG>`; hay que atenderlo en
   `PadViewPage.run` (como `§open:`) para abrir la paleta. **Ahora mismo ese botón se manda al servidor y no hace nada.**
3. **Prueba en el juego** (añádela al escenario `pad` de `bosses/BossTest`): crear un clan con un bot (`/tf bots`),
   hacer al jugador líder, sumar progreso de misión (`PadClanProgress.record` directo o romper bloques) hasta completar
   una, comprobar Clan Coins/XP/prestigio/verde en el informe, capturas de MIEMBROS (ficha), MISIONES, ESTILO, la
   paleta abierta, la barra de arriba con la moneda azul y Ranking › CLANES. Valida los objetivos del `POOL` con
   `TFMissionCheck.sample` (que cada `target` exista y se pueda cumplir).
4. 1.3.156, `HANDOFF.md` (CHECKPOINT nuevo) y publicar.

## 4. Fase 3: Banco del clan (propuesta, sin empezar)

- **Bloque propio** «Banco del clan» (cofre con textura trabajada: genérala con un script en `tools/pad/` como las
  demás, pixel art 16×16 por cara + modelo con tapa y cerraduras doradas/azules del clan; mira cómo están hechas las
  piedras de protección en `claims/` para registrar bloque e ítem). Lo pone el líder; también se abre desde la app de
  clanes (pestaña BANCO).
- **Almacenamiento digital**: no guarda `ItemStack`s, solo `id del ítem → cantidad` en un archivo aparte
  (`clan_banco.json`, uno por clan) para no cargar `clanes.json`. Los objetos con NBT (encantados, con nombre, con
  durabilidad gastada) no se meten (o, si el dueño lo quiere, se guardan aparte con SNBT y cuentan 1 cada uno).
- **Capacidad**: 5.000 ítems al principio, +5.000 por mejora, hasta 100.000 (19 mejoras). Coste en Clan Coins
  creciente, por ejemplo 250 × mejora (250, 500, 750 … 4.750; 47.500 en total). Pregúntale al dueño la cifra final.
- Meten todos los miembros; sacan el líder y los colíderes (o un permiso por miembro); registro de movimientos (quién,
  qué, cuánto) visible en la app.
- Vista en el pad: lista con buscador, icono, nombre y cantidad; meter desde el inventario (todo de un tipo / una pila)
  y sacar por pilas.

## 5. Fase 4: duelos, guerras y alianzas (propuesta, sin empezar)

- **Duelos**: `/clan duelo <jugador>` en el mismo comando que `/clan chat` (`pad/server/PadClanChat`). Solo jugadores
  en un clan, nunca del mismo clan (ni aliados, a confirmar). El retado acepta o rechaza (60 s). Cuenta atrás, sin
  perder objetos (keepInventory solo para el duelo), gana el que deja al otro a 1 corazón (no muere). El clan del
  ganador sube prestigio (p. ej. +25; el perdedor no baja); contra la misma pareja solo cuenta 1 vez cada 24 h (para que
  no se haga farmeo).
- **Guerras de clanes** (no es PvP; por objetivos): el líder declara la guerra desde la app, el otro líder acepta;
  duran 3–7 días; misiones de guerra **difíciles y de equipo**, solo Overworld (p. ej. 40.000 piedra, 8.000 troncos,
  2.000 hierro, 2.500 hostiles, 600 creepers, 10.000 cultivos, 1.000 peces…). Pantalla con una **barra de progreso
  VERTICAL doble** (como la del pase, que es horizontal): cada lado con la insignia, el nombre y el color de su clan,
  llenándose hasta la meta. Premio: Clan Coins, prestigio y verdes a los que aportaron (nunca amarillas).
  Probablemente necesite una página de cliente propia o un tipo de «hero» especial en `PadView`.
- **Alianzas**: el campo `aliados` (lista de ids) ya se lee y `record()` ya reparte la mitad del progreso a los
  aliados. Falta la interfaz: proponer, aceptar, romper (líder), máximo 2 aliados, y que salga en la ficha.

## 6. Fase 5: tienda del clan y territorio (propuesta, sin empezar)

- Pestaña TIENDA en la app de clanes, se paga con Clan Coins (solo los tesoreros).
- **Protecciones que ya tenemos** (personales, `claims/`): `proteccion_10x10`, `25x25`, `40x40`, `64x64`, `80x80`,
  `100x100`, `150x150`, `250x250`, `300x300`, `500x500` y `proteccion_titulo`. Las del clan deben ser **otras**:
  propuesta «Protección del clan» de 50×50, 100×100 y 150×150, dueña el clan (todos los miembros con permiso solos;
  sin PvP entre miembros; título «Territorio de [TAG]» al entrar con el color del clan). **El dueño pidió que se las
  propongas antes**: enséñale la lista y espera su respuesta.
- Ideas para vender (preséntaselas): mejoras del banco, +plazas de miembros, el estandarte físico con la insignia,
  etiqueta con degradado o animada, potenciador de XP del clan ×1,5 durante 24 h, `/clan home` (un punto de
  teletransporte del clan), insignias exclusivas, partículas del clan en el territorio.
- **Territorio del clan** (lo eligió el dueño): las protecciones del clan forman su territorio; aviso al entrar y,
  si se puede, verlo en el mapa del pad.
