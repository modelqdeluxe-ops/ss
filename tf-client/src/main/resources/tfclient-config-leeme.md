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

## Qué archivo cambia cada cosa

| Archivo | Qué controla |
| --- | --- |
| `servidor.properties` | Economía (`economy.mode`, `economy.currency` = nombre de la moneda), rangos, puente con la web, anuncios. |
| `pase.json` | TF Pass: temporada (número, nombre, inicio, días, renovar), niveles, XP por nivel, XP de diarias/semanales/cazas y `premios` de cada nivel. |
| `gachapon.json` | Gachapón: precio de una tirada y de la múltiple, garantía (`garantia.cada`, `garantia.rareza`), `rarezas` (peso = probabilidad) y `premios` (cada uno con `rareza` y `peso`). |
| `oficios.json` | Oficios: ajustes generales, niveles y su recompensa, y cada oficio con `acciones` (lo que paga cada bloque o mob: `monedas`, `xp`) y `misiones` (tipo, objetivo, cantidad, nivel, repetir, recompensa). Lleva su propia `_ayuda`. |
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

Las crates (cajas) van aparte, en `config/fscrates/cajas/` (un JSON por caja; `config/fscrates/items_referencia.json`
tiene los ids de los objetos).

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

## Warps en Viajes

A cada jugador solo le salen los warps de EssentialsX que puede usar: con `per-warp-permission: true` en la config de
EssentialsX, los de su permiso `essentials.warps.<nombre>`; sin eso, todos menos los que se llaman como del staff
(admin, staff, mod, op, privado, oculto, test). Los OP los ven todos. Además se puede ocultar cualquiera en el pad de
administrador (VIAJES › WARPS).
