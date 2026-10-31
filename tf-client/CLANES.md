# Clanes (TF Client) — la actualización grande: estado y cómo seguir

> Léelo entero antes de tocar los clanes. Reglas de siempre: responde al dueño en español, **sin subagentes**, compila
> **solo con GitHub Actions** (`tf-client.yml`, nunca `./gradlew` en el contenedor), no subas jars, prueba en el juego
> tú mismo con `[prueba-jefes=<escenario>]` y mide las capturas con PIL antes de decir que algo está bien.

## 0. Dónde lo dejamos (31 oct 2026): TODO HECHO Y PROBADO EN LA 1.3.156

La 1.3.156 es el parche único que pidió el dueño: la caída por la GTS, las flags de spawn, lo de la 1.3.155 y los
clanes enteros (fases 2–5, §3). Todo probado en el juego (`[prueba-jefes=arreglos]` y `[prueba-jefes=clanes]`, ver el
CHECKPOINT de `HANDOFF.md`). Si el dueño pide más cosas de clanes, mira las ideas de la §4 y su lista literal de la §1.

### Respuestas del dueño (31 oct 2026, sesión del parche 1.3.156)

- **Flags de spawn**: «solo que no aparezcan» (no quitar los mobs que ya estaban ni los que entran andando).
- **Fases 3–5**: «espero a tenerlo todo»: la 1.3.156 lleva TODO (fase 2, banco, duelos, guerras, alianzas, tienda y
  territorio).
- **Tienda del clan**: protecciones del clan de **50×50 y 120×120**, como mucho **2 de cada una por clan**, «caras, no
  regaladas». Y **en la tienda del juego (monedas amarillas) cada jugador solo puede comprar 1 protección de cada tipo**.
- **Clan Coins sin inflar**: «no pongas esa moneda inflada, que sea como las de gacha: 1 o 2 máximo en recompensas».
  Todo lo que da Clan Coins da 1–2 y los precios de la tienda/banco van en esa escala.

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

## 3. Lo hecho en la 1.3.156 (fases 2–5, todo en el mismo parche)

**Economía de las Clan Coins (sin inflar, orden del dueño).** Son del clan (`monedasClan` en clanes.json) y solo las ven
y gastan el líder y los colíderes. Se ganan: misiones del clan 1–2 (`PadClanProgress.POOL`: 2 las difíciles), subir de
nivel 1 (2 en los niveles 5, 10, 15 y 20: `levelReward`), ganar una guerra 2 (`PadClanWar.WIN_COINS`). Unas 6 a la
semana con las 4 misiones. Los duelos no dan monedas (solo prestigio).

| Qué | Dónde | Cómo |
|---|---|---|
| Nivel, prestigio, misiones, insignias, ranking CLANES | `pad/server/PadClanProgress`, `PadClanViews`, `PadRanking` | como en la fase 2 de antes; `maxMembers` = 20 + 2·(nivel−1) hasta 40, + 2 por cada «+2 plazas» comprada |
| Paleta tipo Paint | `pad/PadColorPicker` (cliente) | botón PALETA de ESTILO → `§color:clanes:estilo:<HEX>:<TAG>` (`PadViewPage.run`); cuadro S/B, barra de tono, los 28 colores de Paint, antes/ahora con la etiqueta (sobre oscuro y claro) y el hex escribible; GUARDAR manda `col:#RRGGBB` y vuelve a ESTILO |
| Banco del clan | `pad/server/PadClanBank` (`clan_banco.json`), `clans/ClanBankBlock`, `clans/ClanBlocks`, `tools/pad/banco.py` | digital: `objetos {id: cantidad}`; sin NBT (encantados, con nombre, gastados no entran); 5.000 + 5.000 por mejora hasta 100.000 (19 mejoras; la n cuesta n+1 Clan Coins, 209 en total); meten todos, sacan líder y colíderes (y veteranos con «Los veteranos sacan del banco» en AJUSTES); REGISTRO de 40 movimientos; COFRE da el bloque `tfclient:banco_clan` (no guarda nada: abre la pestaña BANCO de quien lo usa) |
| Guerras | `pad/server/PadClanWar` (`clan_guerras.json`), `pad/PadClanWarPage` (cliente, app de datos «guerra») | pestaña GUERRA: el líder o un colíder declara (doble clic) y el otro acepta en 24 h; 5 días; 5 objetivos de `PadClanWar.POOL` (duros, solo Overworld, meta por miembros con `goal`); cuenta lo que se hace con las reglas de los oficios (`TFJobs.record` → `PadClanProgress.record` → `PadClanWar.record`); gana el primero al 100 % o el que más lleve al acabar; premio 2 Clan Coins + 150 prestigio + 1 verde a cada uno que aportó; el otro 30 prestigio. VER LA GUERRA abre la barra vertical doble (una por clan con su color, META de oro, insignias, % y los 3 que más aportan; a la derecha los objetivos con las dos barritas) |
| Alianzas | `pad/server/PadClanAlliances` | en GUERRA: ALIANZA (proponer), ACEPTAR/NO, ROMPER (doble clic); máximo 2; lo de cada uno suma la mitad a las misiones del otro (`ALLY_SHARE`); los aliados no se declaran la guerra |
| Duelos | `pad/server/PadClanDuels` (`/clan duelo <jugador>`, `aceptar`, `rechazar`, en `PadClanChat.command`) | clanes distintos, a menos de 48 bloques; 60 s para aceptar (clic en el chat); cuenta atrás 3-2-1; el PvP apagado (global o de la zona) no lo para (`EntityProtectionEvents.onLivingHurt`); gana el que deja al otro a 1 corazón (nadie muere: `LivingDeathEvent` cancelado); 3 min = empate; alejarse 64 bloques = huye y pierde; +25 prestigio al clan del ganador, una vez cada 24 h por pareja |
| Tienda del clan | `pad/server/PadClanShop` | pestaña TIENDA (líder y colíderes): Protección del clan 50x50 (15 Clan Coins, máx. 2), 120x120 (40, máx. 2), +2 plazas (10, máx. 5), potenciador de misiones x1,5 24 h (3) |
| Protecciones del clan y territorio | `claims/data/ClaimTier` (`claimstone_clan_50x50` y `_120x120`, `isClan`), `Claim.clanId`, `BlockProtectionEvents.tryPlaceClaim`, `PlayerTracker`, `tools/gen_claim_blocks.py` (ónice y plata azulada) | solo las pone el líder o un colíder; la zona es del clan: `isMember` = cualquiera del clan, `isOwner` = líder y colíderes (`PadClans.managesTerritory`); al entrar, título «Territorio de [TAG] Nombre» con el color del clan; dentro nunca hay PvP entre los del clan (`PadClans.onAttack`); no cuentan para el límite de zonas por jugador |
| Tienda del juego | `shop/TFShopProtections` (`tienda_protecciones.json`) | cada jugador compra una piedra de protección de cada tamaño, para siempre (y de una en una) |

Al disolver un clan se borran su banco, su guerra (gana el otro) y sus alianzas.

**Prueba en el juego**: `[prueba-jefes=clanes]` (`bosses/BossTestClanes`, casos «clanes 1»–«clanes 17» y capturas
`clanes_1_miembros` … `clanes_17_cofre_abre_banco`) y `[prueba-jefes=arreglos]` (`bosses/BossTestFixes`: la GTS y las
flags de spawn).

## 4. Ideas para la tienda del clan que no se hicieron (preséntaselas al dueño si pide más)

`/clan home` (un punto de teletransporte del clan), el estandarte físico con la insignia, etiqueta con degradado o
animada, insignias exclusivas de pago, partículas del clan en su territorio y el territorio en el mapa del pad.
