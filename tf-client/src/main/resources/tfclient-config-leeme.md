# Configuración de TF Client (guía para editar a mano)

Este archivo lo escribe el mod cada vez que arranca el servidor: no lo cambies, cambia los de al lado.

## Servidor o cliente

Manda **siempre la config del servidor**. Estos archivos solo valen en el ordenador donde corre el servidor; si los ves
en la carpeta de un jugador es porque jugó un mundo local, y en un servidor no se usan (la economía, el pase, el
gachapón, los oficios, las misiones, la tienda, las zonas… los decide el servidor, y la dirección de la web también: el
cliente usa la que le manda el servidor). En el cliente solo cuentan `cliente.properties` (el menú y el servidor al que
conecta), `pad-cliente.properties` y `estelas.properties` (preferencias de cada jugador).

## Cómo se cambia algo

1. Abre el archivo de la tabla, cambia el valor y guarda. Los `.json` tienen que seguir siendo JSON válido (comillas
   dobles, sin coma después del último elemento). Las claves `_ayuda` solo explican: no hacen nada.
2. En el juego, `/tf reload` (o RECARGAR CONFIGS en el pad de administrador). Si un archivo tiene un error, ese se
   queda como estaba y el comando dice cuál y por qué; los demás se recargan igual.
3. Casi todo se puede cambiar también desde el **TF Pad de administrador** (staff con permiso 3): TF PASS, GACHAPÓN,
   OFICIOS (lo que paga cada uno y sus misiones), CAZAS, KITS, RECOMPENSAS, TIENDA, VIAJES y AJUSTES. Lo que se
   cambia ahí se guarda en estos mismos archivos.

## Monedas

- **Fantastic Coins**: la moneda del servidor (la de la tienda, los oficios y los premios). En los JSON de premios:
  `{"tipo": "monedas", "cantidad": 50}`.
- **Fantastic Gacha Coins**: las del Gachapón (antes «monedas verdes»). En los premios: `{"tipo": "verdes", "cantidad": 1}`.
- Un objeto: `{"tipo": "objeto", "id": "minecraft:diamond", "cantidad": 2}`.
- Referencia de la economía (1.3.134, estudio en `TIENDA.md` del mod): una hora de oficio da unas 75 Fantastic Coins
  y un jugador normal gana unas 270 al día (oficio, misiones, cazas y calendario). En la tienda, algo cuesta unas 6
  veces lo que ganarías en el rato de conseguirlo tú (hierro 12, oro 19, carbón 4; el diamante y la netherita no se
  venden). Antes de subir un premio, compáralo con eso: la moneda tiene que valer algo.
- (1.3.153) Oficios, misiones del pad y cazas pagan **la mitad** que en esa referencia (una hora de oficio, unas 35
  monedas) y **ningún premio da netherita** (pase, gachapón, kits, recompensas y oficios: se quitó una vez; las
  configs llevan `"sinNetherita": true`). Los oficios **solo suben de nivel con las misiones**: las acciones pagan
  monedas y nada de XP.

## Qué archivo cambia cada cosa

| Archivo | Qué controla |
| --- | --- |
| `servidor.properties` | Economía (`economy.mode`, `economy.currency` = nombre de la moneda), rangos, puente con la web, anuncios. |
| `pase.json` | TF Pass: temporada (número, nombre, inicio, días, renovar), niveles, XP por nivel, XP de diarias/semanales/cazas y `premios` de cada nivel. |
| `gachapon.json` | Gachapón: precio de una tirada y de la múltiple, garantía (`garantia.cada`, `garantia.rareza`), `rarezas` (peso = probabilidad) y `premios` (cada uno con `rareza` y `peso`). |
| `oficios.json` | Oficios: ajustes generales, niveles y su recompensa, y cada oficio con `acciones` (lo que paga cada bloque o mob: `monedas`; su `xp` ya no cuenta, el nivel sube con las misiones) y `misiones` (tipo, objetivo, cantidad, nivel, repetir, recompensa). Lleva su propia `_ayuda`. |
| `misiones.json` | Misiones diarias y semanales del pad y las cazas (presas que rotan cada 12 h). |
| `recompensas.json` | Calendario de recompensas diarias y recompensas sueltas. |
| `kits.json` | Kits: objetos, monedas, espera y quién puede cogerlos. |
| `tienda.json` | Tienda del servidor: todos los objetos del juego por categorías, precio de compra y los Tesoros (lo único que compra la tienda, con límite al día). |
| `tienda-monedas.json` | La «Tienda de monedas» de la web: objetos del servidor que se compran con Fantastic Coins (mejor con `/tf web tienda add <precio>` con el objeto en la mano). |
| `ruleta.json` | Copia de la ruleta de la web (la manda la web por el puente: cámbiala allí). |
| `protecciones.json` | Piedras de protección: tamaños, límites y opciones de las zonas. |
| `limites.json` | Límites de objetos y bloques. |
| `vfx.json` | Efectos de muerte y demás efectos visuales (los de pago se compran en la web). |
| `skills.json` | Clases de skills. |
| `ayuda.json` | Textos de la Guía del pad. |
| `pad.json` | Qué apps del pad ven los jugadores y ajustes del pad. En `mundos`: `explorar` (el mundo al que lleva Explorar) y `spawn` (el del botón Spawn), por su id (`minecraft:overworld`, `minecraft:survival`…); vacío = automático (Explorar nunca va a un mundo vacío como el spawn de Multiverse; Spawn usa el `/spawn` de EssentialsX). También con `/tf web viajes explorar aqui` o `spawn aqui` estando en ese mundo, y `/tf web viajes lista` para ver cuáles son. |
| `jefes.json` | Jefes de incursión. |
| `clanes.json` | Clanes: niveles y su XP, plazas de miembros, misiones de la semana (la lista entera), guerras (y sus objetivos), banco, alianzas, duelos y la **tienda del clan**. Ver «Clanes» abajo. Lo que cuesta fundar un clan sigue en `pad.json` (`clanes.precio`). |
| `revivir.json` | Revivir (el Revive Mod, ahora dentro del TF Client): tiempo desangrándose, distancia y tiempo para revivir, auto-revivir, sonidos, partículas y **quién puede rematar** a un noqueado (`playersCanFinish`, `mobsCanFinish`). Ver «Revivir» abajo. |

Las crates (cajas) van aparte, en `config/fscrates/cajas/` (un JSON por caja; `config/fscrates/items_referencia.json`
tiene los ids de los objetos).

## Revivir (`revivir.json`)

Al morir no mueres: te desangras tumbado con un contador (`downTimeSeconds`, 90). Otro jugador te revive manteniendo
clic derecho cerca (`reviveDistance` bloques, `reviveTimeTicks` ticks; entre varios va más rápido) y vuelves con
`reviveHealth` de vida y `reviveFood` de comida. Tú puedes rendirte (tecla E mantenida) o auto-revivirte pagando
`selfReviveLevelCost` niveles (tecla F; `allowSelfRevive`). Las teclas se cambian en Opciones › Controles › Revive Mod
o en Mods › TF Client › Configuración.

- `playersCanFinish` (por defecto `true`): otro jugador puede **rematar** a un noqueado. Un golpe suyo que haga daño
  (espada, flecha, tridente…) lo mata de verdad: suelta su tumba, sale su mensaje de muerte y cuenta como asesinato.
  Las zonas sin PvP lo siguen impidiendo.
- `mobsCanFinish` (por defecto `false`): los mobs (y los jefes) también rematan. Con `false`, a un noqueado no le hacen
  nada y le pierden de vista.
- Con `/tf reload` (o `/revive reload`) vale al momento. También: `/revive set rematar jugadores true|false` y
  `/revive set rematar mobs true|false`.
- La primera vez se copia de `config/revivemod.json` (el del mod de antes). **Quita el revivemod (ReviveForge) de la
  carpeta mods y del AutoModpack**: mientras siga instalado, el revivir del TF Client no se enciende.

## Clanes (`clanes.json`)

Todo lo de los clanes que antes era fijo. Se cambia en el **pad de administrador › CLANES** (GENERAL, MISIONES, GUERRA y
TIENDA: se guarda y vale al momento) o en el archivo y `/tf reload`. Lo que borres del archivo vuelve con lo de fábrica.

- `nivel`: `maximo` (20), `xpBase` (1000: la XP del nivel 1 al 2), `xpCrece` (1,35: cada nivel pide eso por la del
  anterior), `monedas` (Clan Coins al subir: 1), `monedasEspeciales` (2) cada `especialCada` niveles (5).
- `miembros`: `base` (20 plazas), `porNivel` (+2), `maximoPorNivel` (40) y `porCompra` (las plazas de cada «+plazas» de
  la tienda: 2).
- `misiones`: `porSemana` (4), `verdes` (las de cada miembro que aportó: 1), `aliados` (0,5 = la mitad), `metaMin` y
  `metaMax` (la `cantidad` de cada misión es para 5 miembros; con más o menos, entre x0,7 y x2,5) y `lista`: cada misión
  con `id`, `nombre`, `descripcion`, `icono`, `tipo` (romper, colocar, matar, pescar, cosechar, criar, fabricar, fundir,
  encantar), `objetivo` (un id, una #etiqueta, `hostil` o `*`), `cantidad`, `monedas`, `xp`, `prestigio` y `activa`.
- `potenciador`: `multiplicador` (1,5).
- `guerra`: `dias` (5), `objetivos` (5), `monedas` (2) y `prestigio` (150) del ganador, `prestigioPerdedor` (30),
  `verdes` (1), `horasParaAceptar` (24) y `lista` (como las misiones, sin premio propio).
- `banco`: `inicial` (5000), `porMejora` (5000), `mejoras` (19), `primeraMejora` (2 Clan Coins) y `subePorMejora` (1).
- `alianzas`: `maximo` (2). `duelos`: `prestigio` (25), `horasPorPareja` (24), `distancia` (48), `huida` (64),
  `segundos` (180) y `segundosParaAceptar` (60).
- `tienda`: lo que se vende en la tienda del clan (con Clan Coins; compran el líder y los colíderes). Cada oferta: `id`,
  `nombre`, `descripcion`, `icono`, `precio`, `maximo` (cuántas por clan; 0 = sin tope), `activa` y `tipo`:
  `proteccion` (`piedra`), `plazas`, `potenciador` (`horas`), `objeto` (`objeto`: mejor con «Vender lo que llevas en la
  mano» del pad de administrador, que guarda encantamientos y nombre), `verdes` (`cantidad` para cada miembro) o
  `comando` (`comandos`, desde la consola: `{jugador}`, `{clan}`, `{etiqueta}`). En `nombre` y `descripcion` valen
  `{cantidad}`, `{horas}` y `{x}`. Precios de fábrica (1.3.158): protección 50x50 30, 120x120 80, +2 plazas 20,
  potenciador 6.

## Copias de seguridad

Cuando una versión nueva del mod cambia la economía de un archivo, guarda el de antes al lado como
`nombre.antes-vN.json`. Si quieres volver a algo de antes, cópialo de ahí.

## Al actualizar el mod

No hay que tocar nada: cambia el jar y reinicia el servidor. Lo que trae la versión nueva se pone solo: los ajustes
nuevos toman su valor por defecto, y si cambia la economía de `tienda.json`, `oficios.json` o `misiones.json`, el
archivo se cambia por el nuevo (con la copia de antes al lado, ver arriba). Las misiones de oficios hechas y sin cobrar
se pueden cobrar hasta 30 días después, aunque cambie `oficios.json` (con lo que valían al hacerlas).

## La clave del puente con la web

`bridge.secret` de `servidor.properties` es la que va en Cloudflare (Secret `BRIDGE_SECRET`). El mod guarda una copia en
`tfclient-puente.clave` (en la carpeta del servidor, junto a `mods/`) y en `config/tfclient/.puente.clave`: si
`servidor.properties` se pierde o se cambia al actualizar, vuelve la misma clave y la web la sigue reconociendo. Para
cambiarla de verdad, ponla en `servidor.properties` (las copias se actualizan solas) y en Cloudflare. Si la web no
contesta, el puente lo vuelve a intentar solo cada pocos segundos; no hace falta reiniciar.

## Objetos ligados (kits de rangos y crates)

Las piezas de los sets de rangos y crates (armas, armadura, escudo, y desde la 1.3.141 también alas, sombreros y
mochilas) van ligadas a quien las recibe. Solo él puede sacarlas de un cofre o de cualquier contenedor; las tolvas y
tuberías no las mueven; si otro las lleva encima, vuelven a su dueño (al entrar, si no está conectado). En creativo
(staff) sí se pueden mover. Los sets de cosméticos que se compran con monedas no se ligan: se pueden regalar.

## Rastreador (pad de administrador)

La app RASTREADOR lista a cada jugador que compró en la web (la lista la manda la web) y, al pulsarlo, cada compra con
todos sus objetos y dónde está cada uno: puesto, en la mano, inventario, cofre de ender, un cofre o barril (también
dentro de shulkers) con sus coordenadas, en el suelo o con otro jugador. IR te lleva; TRAER se lo quita a quien lo
tenga y se lo da a su dueño. Busca sola al minuto de arrancar y cada media hora (BUSCAR AHORA guarda el mundo y busca al
momento). MOVIMIENTOS y `/tf rastreo` dicen quién sacó o metió qué en cada contenedor (`<mundo>/tfclient/rastreador/`,
60 días); `/tf buscar <jugador|pedido>` lo dice por el chat.

## Warps en Viajes

A cada jugador solo le salen los warps de EssentialsX que puede usar: con `per-warp-permission: true` en la config de
EssentialsX, los de su permiso `essentials.warps.<nombre>`; sin eso, todos menos los que se llaman como del staff
(admin, staff, mod, op, privado, oculto, test). Los OP los ven todos. Además se puede ocultar cualquiera en el pad de
administrador (VIAJES › WARPS).
