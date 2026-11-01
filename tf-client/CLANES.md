# Clanes (TF Client) — la actualización grande: estado y cómo seguir

> Léelo entero antes de tocar los clanes. Reglas de siempre: responde al dueño en español, **sin subagentes**, compila
> **solo con GitHub Actions** (`tf-client.yml`, nunca `./gradlew` en el contenedor), no subas jars, prueba en el juego
> tú mismo con `[prueba-jefes=<escenario>]` y mide las capturas con PIL antes de decir que algo está bien.

## 0. Dónde lo dejamos (1 nov 2026): la 1.3.160 (§7) sobre la 1.3.159 (§6), la 1.3.158 (§5) y la 1.3.156

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
| Banco del clan | `pad/server/PadClanBank` (`clan_banco.json`), `clans/ClanBankBlock`, `clans/ClanBlocks`, `tools/pad/banco_modelo.py` (1.3.158) | digital: `objetos {id: cantidad}`; sin NBT (encantados, con nombre, gastados no entran); 5.000 + 5.000 por mejora hasta 100.000 (19 mejoras; la n cuesta n+1 Clan Coins, 209 en total); meten todos, sacan líder y colíderes (y veteranos con «Los veteranos sacan del banco» en AJUSTES); REGISTRO de 40 movimientos; COFRE da el bloque `tfclient:banco_clan` (no guarda nada: abre la pestaña BANCO de quien lo usa) |
| Guerras | `pad/server/PadClanWar` (`clan_guerras.json`), `pad/PadClanWarPage` (cliente, app de datos «guerra») | pestaña GUERRA: el líder o un colíder declara (doble clic) y el otro acepta en 24 h; 5 días; 5 objetivos de `PadClanWar.POOL` (duros, solo Overworld, meta por miembros con `goal`); cuenta lo que se hace con las reglas de los oficios (`TFJobs.record` → `PadClanProgress.record` → `PadClanWar.record`); gana el primero al 100 % o el que más lleve al acabar; premio 2 Clan Coins + 150 prestigio + 1 verde a cada uno que aportó; el otro 30 prestigio. VER LA GUERRA abre la barra vertical doble (una por clan con su color, META de oro, insignias, % y los 3 que más aportan; a la derecha los objetivos con las dos barritas) |
| Alianzas | `pad/server/PadClanAlliances` | en GUERRA: ALIANZA (proponer), ACEPTAR/NO, ROMPER (doble clic); máximo 2; lo de cada uno suma la mitad a las misiones del otro (`ALLY_SHARE`); los aliados no se declaran la guerra |
| Duelos | `pad/server/PadClanDuels` (`/clan duelo <jugador>`, `aceptar`, `rechazar`, en `PadClanChat.command`) | clanes distintos, a menos de 48 bloques; 60 s para aceptar (clic en el chat); cuenta atrás 3-2-1; el PvP apagado (global o de la zona) no lo para (`EntityProtectionEvents.onLivingHurt`); gana el que deja al otro a 1 corazón (nadie muere: `LivingDeathEvent` cancelado); 3 min = empate; alejarse 64 bloques = huye y pierde; +25 prestigio al clan del ganador, una vez cada 24 h por pareja |
| Tienda del clan | `pad/server/PadClanShop` | pestaña TIENDA (compran líder y colíderes; desde la 1.3.158 la ven todos y sale de `clanes.json`): Protección del clan 50x50 (15 Clan Coins, máx. 2; 30 en la 1.3.158), 120x120 (40, máx. 2; 80), +2 plazas (10, máx. 5; 20), potenciador de misiones x1,5 24 h (3; 6) |
| Protecciones del clan y territorio | `claims/data/ClaimTier` (`claimstone_clan_50x50` y `_120x120`, `isClan`), `Claim.clanId`, `BlockProtectionEvents.tryPlaceClaim`, `PlayerTracker`, `tools/gen_claim_blocks.py` (ónice y plata azulada) | solo las pone el líder o un colíder; la zona es del clan: `isMember` = cualquiera del clan, `isOwner` = líder y colíderes (`PadClans.managesTerritory`); al entrar, título «Territorio de [TAG] Nombre» con el color del clan; dentro nunca hay PvP entre los del clan (`PadClans.onAttack`); no cuentan para el límite de zonas por jugador |
| Tienda del juego | `shop/TFShopProtections` (`tienda_protecciones.json`) | cada jugador compra una piedra de protección de cada tamaño, para siempre (y de una en una) |

Al disolver un clan se borran su banco, su guerra (gana el otro) y sus alianzas.

**Prueba en el juego**: `[prueba-jefes=clanes]` (`bosses/BossTestClanes`, casos «clanes 1»–«clanes 17» y capturas
`clanes_1_miembros` … `clanes_17_cofre_abre_banco`) y `[prueba-jefes=arreglos]` (`bosses/BossTestFixes`: la GTS y las
flags de spawn).

## 5. La 1.3.158 (1 nov 2026): lo que pidió el dueño al ver las capturas de la 1.3.156

El dueño, literal: «1. textura de cofre horrible; 2. hay frases y números repetidos; 3. no me mostraste la tienda del clan
completa, y sube los precios; 4. banners o logos de clan pobres y hay muy pocos, haz una vasta lista, grande, bien
dibujados sin errores» («mínimo 100 o más»); «5. no vi el banco, ¿cómo funciona?, y haz que los miembros vean todas esas
pestañas menos ajustes y estilo, esas opciones solo son para colíder y el líder; 6. haz la config de los clanes
configurable 100 por 100 (misiones, miembros y así), en el config y en el pad de admin, que incluya la tienda, aunque
quiero una tienda más específica para el clan, dame ideas». Y después: «los cofres siguen siendo una mierda, saca un
modelo de las crates, uno de madera, de los modelos 3D de las crates del mod, no de la web».

| Qué | Dónde | Cómo |
|---|---|---|
| El cofre del banco | `tools/pad/banco_modelo.py`, `models/block/clan/banco_clan.json` | el modelo 3D de la crate **`crate_lvl1`** de TF Crates (madera con herrajes; base y tapa cerrada), a escala 0,625 para que quepa en un bloque, con su textura `fscrates:block/crate_lvl1`. Para cambiarla por otra crate: `python3 tools/pad/banco_modelo.py <crate> --ver salida.png` (lo hace todo, también el blockstate si la cerradura va al sur). Para ver los modelos sin abrir el juego: `tools/pad/ver_modelo.py` (`--pitch -28` para verlos desde arriba). Fuera las texturas pixeladas de `tools/pad/banco.py` |
| Las insignias | `tools/pad/insignias.py`, `tools/pad/gameicons.json`, `LICENSE-ICONOS.txt` | **207** (antes 48): símbolos de game-icons.net (CC BY 3.0: los créditos van en el jar) sobre 9 formas, 20 campos y 4 metales; las 48 de antes conservan su id. `python3 tools/pad/insignias.py --ver <carpeta>` hace la hoja |
| Repetidos | `PadClanWar` (el hero: solo lo de ellos), `PadClanWarPage` (sin el título ni los % debajo de las barras), `PadClanBank` (la cantidad una vez), `PadClanViews` (misiones: «Misiones de la semana», hechas y Clan Coins ganadas) | |
| Pestañas | `PadClans` | miembros, misiones, banco, guerra y tienda para todos; **estilo y ajustes solo líder y colíderes** (y sus acciones: `col:`, `colhex`, `insignia:`, `vetinv`, `vetbanco`, `color`, `fuego`); disolver solo el líder |
| Config 100 % | `pad/server/PadClanConfig` (`config/tfclient/clanes.json`, en `/tf reload`) | nivel, miembros, misiones (+ lista), potenciador, guerra (+ lista), banco, alianzas, duelos y **tienda** (tipos `proteccion`, `plazas`, `potenciador`, `objeto`, `verdes`, `comando`). Lo de fábrica es lo de la 1.3.156 salvo los precios (al doble: 30, 80, 20, 6). Los números de cada clase (`PadClanProgress.MAX_LEVEL`, `PadClanWar.DAYS`…) ya no son `final`: los pone `PadClanConfig.apply`. Las misiones apagadas no salen en semanas nuevas pero las de esta semana siguen (`KNOWN` / `POOL`) |
| Pad de administrador › CLANES | `pad/server/PadAdminClans` (`a_clanes`) | GENERAL (fundar, plazas, niveles, banco, alianzas, duelos), MISIONES y GUERRA (números + la lista: SÍ/NO, EDITAR nombre, tipo, objetivo —comprobado con `TFMissionCheck.sample`—, cantidad, premio e icono; añadir y borrar), TIENDA (SÍ/NO, precio, máximo, tipo y lo suyo; «Vender lo que llevas en la mano») |

**Prueba**: `[prueba-jefes=clanes]` con lo nuevo: «clanes 5b» (pestañas de un miembro raso y del líder), «clanes 5c»
(`PadClanConfig.testConfig`: cambiar clanes.json vale al momento y se guarda), «clanes 7» con los precios nuevos y las
capturas `clanes_18_tienda_arriba` … `clanes_25_admin_mision` (la tienda entera, el banco y la app de administrador).

**El banco, como se le explicó al dueño**: es digital (no es un cofre de verdad): en la pestaña BANCO cualquiera del clan
mete lo que lleva (METER, por tipo de objeto); sacan el líder y los colíderes (y los veteranos si se activa en AJUSTES).
Caben 5.000 objetos y cada mejora suma 5.000 (con Clan Coins); los objetos con encantamientos, nombre o gastados no
entran (para que nadie pierda nada). REGISTRO enseña quién metió o sacó qué. El bloque del cofre (botón COFRE) solo abre
la pestaña BANCO de quien lo usa.

## 6. La 1.3.159 (1 nov 2026): los 12 puntos del dueño tras ver la 1.3.158

El dueño, literal (resumido): «1 la pestaña del banco es demasiado grande, no me deja ver los items ni los que puedo
meter; 2 el tema que pongo en el menú principal debe aplicarse a todas las apps (puse Limón y en una app se quita); 3 que
los temas o fotos que ponga queden perfectamente dibujados, sin discrepancias en las texturas; 4 la guerra debe incluir
algo de PvP, opcional pero que dé punto igual, solo 1 misión de matar; 5 el reto de duelo con aceptar y rechazar como
botones en el chat; 6 el cofre se ve muy pequeño, hazlo más grande, que el pad dé solo 1, y que lo rompan solo el líder o
colíder y les vuelva al inventario como las claim blocks; 7 misiones un poco más exigentes, porque las hacen en grupo; 8
que el cofre brille en la oscuridad como las crates; 9 el estandarte físico con la insignia del clan y un editor completo
de efecto arcoíris y degradado en la etiqueta; 10 un emote exclusivo del clan con animación, físicas, sonido o música y
la insignia dinámica, con una tecla, y un vídeo de frente en tercera persona; 11 en la tienda del pad, «Todos los objetos
en venta» con una lupa para buscar, los objetos en español; 12 nada de español de España: latinoamericano».

| Punto | Dónde | Cómo |
|---|---|---|
| 1 Banco | `PadClanBank.view` (modos `banco`, `banco_meter`, `banco_reg`) | dos vistas: **BANCO** (lo guardado en casillas, la capacidad y la mejora en la cabecera, sin ficha grande; abajo METER ›, MEJORAR, COFRE y REGISTRO) y **METER** (lo que llevas en casillas con «×64» en verde; tocar uno lo mete, METER TODO con confirmación, ‹ BANCO para volver) |
| 2-3 Fondo | `TFPadScreen.drawBackdrop` | el fondo elegido (Ajustes › **Fondo del pad**) en la portada y en todas las apps; probado con Limón, un PNG de 1600x900 con rejilla de 1 px y el del tema |
| 4 Guerra PvP | `PadClanWar` (`PVP`, `onDeath`, `recordKill`), `clanes.json` guerra.pvp | cada guerra lleva un objetivo PvP (derrotar a los del clan rival; con Revivir solo cuenta el remate); los demás objetivos de esa guerra no son de matar; la misma pareja cuenta una vez cada 10 min; cuenta como cualquier objetivo (la guerra es la media) |
| 5 Duelo | `PadClanDuels.challengeMessage` | «⚔ X (clan) te reta a un duelo.» y debajo [✔ ACEPTAR] [✖ RECHAZAR] con texto al pasar el ratón y los segundos; la prueba los pulsa como el ratón (`Screen.handleComponentClicked`) |
| 6-8 Cofre | `ClanBankBlock` (+ `ClanBankBlockEntity`, `ClanBankRenderer`), `ClanBlockEvents`, `PadClanBank` (`cofres`, `chestOf`) | la crate `crate_lvl1` a su escala de crate (0,83: ~20 px de ancho), dibujada a plena luz (como las crates); uno por clan (COFRE no da otro si está puesto o alguien del clan lo lleva); lo quitan solo el líder y los colíderes de su clan (le vuelve al inventario), nadie más, ni explosiones ni pistones; lo abren los de su clan |
| 7 Misiones | `PadClanProgress.FACTORY` (x1,6), `OLD_AMOUNTS`, `PadClanConfig.migrate` (versión 2) | un clanes.json de antes se pone al día solo en las misiones que tenían la meta de fábrica |
| 9 Estandarte | `ClanBannerBlock`, `ClanBannerBlockEntity`, `ClanBannerRenderer`, tienda (`tipo: estandarte`, 8 Clan Coins) | asta con remate y travesaño dorados, tela del color del clan que ondea, la insignia de ahora y la etiqueta con su efecto; de su clan (`estandartes`), lo quitan el líder y los colíderes; la etiqueta letra a letra sobre la onda, inclinada con la tela y con borde oscuro (la de un color es del color del paño); la tela ondea solo hacia delante (si no, el asta asomaba) |
| 9 Etiqueta | `ClanTagFx`, `PadColorPicker` (el editor), `PadClans.tagOf`/`syncSuffix`, `ClanClient.animateTag`, `PlayerTabOverlayClanMixin` | SÓLIDO, DEGRADADO (2-3 colores), ARCOÍRIS (velocidad) y ONDA; en el chat y en el TAB (sufijo de LuckPerms) sale quieto letra a letra; encima de la cabeza y en la lista se anima en el TF Client |
| 10 Emote | `ClanEmote` (servidor), `ClanEmoteClient`, `ClanNet`, `ClanLooks`, `tools/pad/emote_sonido.py` (`clanes.emote`) | tecla **B** (Opciones › Controles › Tierras Fantásticas › Emote del clan); 4,4 s: impulso, salto con vuelta y brazos en V, la insignia sale del pecho con un muelle y gira, rayos y halo de los colores del clan, brillos con gravedad, onda en el suelo, puño en alto y la etiqueta animada; fanfarria propia; cámara de frente mientras dura |
| 11 Tienda | `PadShop.all`, `util/TFNames`, `assets/tfclient/pad/nombres_es.json` (es_mx de la 1.20.1) | «Todos los objetos en venta» (primera fila de COMPRAR) con el buscador (la lupa) en español, inglés o id; los nombres de la tienda y del banco en español; también la leyenda al pasar el ratón; los nombres largos, en dos líneas (`PadViewPage.longName`) |
| 12 Español | clanes, protecciones, guía y armario del pad | sin vosotros (tienen, sus, ganaron…) ni «ordenador» (computadora) |

**Prueba en el juego** (`[prueba-jefes=clanes,clanes2]`, commit f0f5bee6, y `clanes2` otra vez con el estandarte): todos
los casos «bien» (clanes 1-17 y clanes2 1-12). Capturas medidas: el cofre del clan es 1,28 veces más ancho que un cofre
normal y de noche no se oscurece (brillo 112 de día y 111 de noche; el normal, de 71 a 36); el fondo Limón y un PNG de
1600x900 en la portada, CLANES, TIENDA y MISIONES; la búsqueda «espada» (5); el banco y METER; el editor (arcoíris y
degradado); la etiqueta arcoíris encima de la cabeza y en el TAB; el reto de duelo con los botones (pulsado ACEPTAR). El
vídeo `video_emote.mp4` (134 fotogramas, 30 por segundo): los brillos, los rayos, la insignia y la etiqueta.

`ClanLooks`: el servidor manda a los TF Client cómo se ve cada clan (etiqueta, color, efecto, insignia) y de qué clan es
cada jugador conectado (cada 2 s si cambió). Prueba: `[prueba-jefes=clanes2]` (`bosses/BossTestClanes2`, con el vídeo
`video_emote.mp4` que arma el workflow con ffmpeg).

## 7. La 1.3.160 (1 nov 2026): los 7 puntos del dueño tras ver la 1.3.159

El dueño, literal (resumido): «1 el emote no me gusta, necesito más de 1, mínimo 5 distintos, ninguno igual ni música
igual; 2 el banner: saca las letras de esos corchetes; 3 en la prote se pueda poner música, con la función del
reproductor, la claim debe tener su apartado; la música solo se oirá dentro de la zona, fuera se quita; hazlo bien sin
romper algo; 4 asegúrate que el tag del clan al escribir en el chat funcione bien el efecto arcoíris, no quiero que nada
se rompa; 5 el emote también: los corchetes son para el chat y el TAB; en emote y banner solo las letras con un efecto a
los lados; 6 el apartado de guerra está desalineado, quiero todo simétrico; 7 en la tienda vende una protección contra
las guerras, de 24 horas o 2 días».

| Punto | Dónde | Cómo |
|---|---|---|
| 1 Emotes | `clans/client/ClanEmotes` (los 5), `ClanEmoteFx` (lo que dibujan), `ClanEmoteClient` (reloj, música, cámara), `ClanEmoteWheel` (la rueda), `ClanEmote.IDS`, `tools/pad/emotes_sonido.py` | la tecla B abre la rueda (ratón o 1-5): **Estandarte** (sale de la tierra un estandarte gigante que se despliega; Do mayor, marcha), **Corona** (haz del cielo, se arrodilla, la corona con la insignia se le posa; Re mayor, coro y campanas), **Trueno** (carga energía, le cae un rayo del color del clan, la insignia grabada en el suelo; Mi menor, taikos), **Fiesta** (confeti, pista de baile, la insignia de bola de disco; Sol mayor, 128 ppm, 8 bits), **Órbita** (levita, 6 insignias giran y se juntan; La menor, arpa) |
| 2 y 5 Sin corchetes | `ClanBannerRenderer` (estrellas con `star`), `ClanEmoteFx.tag` | solo las letras con su efecto y borde oscuro, y una estrella que titila a cada lado del color de la letra de ese lado; los corchetes siguen en el chat y el TAB |
| 3 Música en la zona | `pad/server/PadZoneMusic` (+ `musica_zonas.json`), `PadClaims` (pestaña MÚSICA), `PadSpeakerNet.ZoneSet/Zone`, `MusicSpeaker.zone`, `PadMusicPage` (elegir para la zona) | Protecciones › zona › MÚSICA › ELEGIR abre la app Música para tocar una canción (link o de tu carpeta: se sube como las de los discos); suena en bucle, igual en toda la zona, para quien esté dentro (`Claim.contains`, cada medio segundo); al salir se apaga en 1 s; volumen de 10 en 10; «Oír la música de otros» (Ajustes del pad) también la quita |
| 4 Chat arcoíris | `mixin/ChatComponentClanMixin` (`require = 0`), `ClanClient.animateChat` | la etiqueta del clan en las líneas del chat se anima como en el TAB (solo «[TAG]» que llega con color del servidor; el mensaje no se toca); con LuckPerms el sufijo ya lleva el color letra a letra |
| 6 Guerra simétrica | `PadClanWarPage.side` | los dos lados a la misma distancia de las barras y los dos con rótulo (TU CLAN / RIVAL / GANADOR), «Sin aportes aún» si no hay |
| 7 Escudo de guerra | `PadClanWar` (`shielded`, `dropIncoming`), `PadClanShop` (tipo `escudo`), `PadClanConfig` (versión 3: `escudo_24h` 10 ¢ y `escudo_48h` 18 ¢) | mientras dura nadie les declara la guerra (el botón sale como ESCUDO), lo sin aceptar se anula al comprarlo, se suma si se compra otro; el clan con escudo sí puede declarar (el otro decide si acepta) |

Prueba: `[prueba-jefes=clanes,clanes3]` (`bosses/BossTestClanes3`, con un vídeo de cada emote: `video_emote_<nombre>.mp4`).

**Después, en la misma 1.3.160** (el dueño: «mete muchos más emotes, que sean comprables en la tienda del clan», «no
hagas test tan largos», «que los efectos se vean con shaders también, que no haya error como el evento del cumpleaños»,
«pon varios efectos de arcoíris, muchos colores, como los títulos de las crates; el botón de arcoíris bloqueado, deben
comprarlo en la tienda del clan»):

| Qué | Dónde | Cómo |
|---|---|---|
| 10 emotes más | `ClanEmotes2` (Fénix, Tornado, Ventisca, Meteoro, Fuegos artificiales, Espadas, Aura, Corazones, Concierto, Portal), música en `emotes_sonido.py` | 15 en total; los 5 primeros, de todos los clanes (`ClanEmote.FREE`); los demás, oferta `emote` de la tienda del clan (6 a 14 ¢, para todo el clan: `emotes` en el JSON del clan, `ClanEmote.owns/unlock`, `ClanLooks` manda los bits `e`) |
| La cuadrícula | `ClanEmoteWheel` | ya no es rueda: tarjetas de 5 en fila sobre fondo opaco; las no compradas con candado y TIENDA; clic o 1 a 9 |
| Con shaders | `ClanEmoteClient.onPost/renderAfterShaders`, `vacio.AfterShaders.end` | con un paquete puesto, los efectos se apuntan y se dibujan después de su pasada final, con la profundidad de la escena (como la grieta y el letrero del cumpleaños); en la pasada de sombras, nada |
| Arcoíris de pago | `ClanTagFx` (`style`, `rainbowOwned`), `PadColorPicker` (selector de estilos y candado), oferta `arcoiris` (`efectos_arcoiris`, 15 ¢) | el modo ARCOÍRIS con los 137 estilos de los títulos de las crates (`crates.client.color.FSRainbow`, el 0 es el de siempre); sin comprarlo el botón sale con candado y guardar arcoíris no vale (`PadClans` tagfx); `clanes.json` versión 4 añade las ofertas |

Prueba corta: `[prueba-jefes=emotes]` (`bosses/BossTestEmotes`: bloqueos, compras, editor, cuadrícula y un vídeo de cada
emote nuevo); con `[shaders]`, los 15 emotes en pasos de 0,25 s.

## 4. Ideas para la tienda del clan que no se hicieron (preséntaselas al dueño si pide más)

`/clan home` (un punto de teletransporte del clan), insignias exclusivas de pago, partículas del clan en su territorio,
el territorio en el mapa del pad, escudo de guerra (3 días sin que les declaren la guerra), potenciador de prestigio,
llave de crate del clan y capa del clan. (El estandarte y la etiqueta con degradado o arcoíris ya están: 1.3.159.)
