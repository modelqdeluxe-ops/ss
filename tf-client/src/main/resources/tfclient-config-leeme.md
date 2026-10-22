# Configuración de TF Client (guía para editar a mano o con una IA)

Este archivo lo escribe el mod cada vez que arranca el servidor: no lo cambies, cambia los de al lado.

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
- Referencia de la economía: un diamante vale 150 Fantastic Coins en la tienda y un jugador normal gana unas 400 al
  día trabajando. Antes de subir un premio, compáralo con eso: la moneda tiene que valer algo.

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
| `tienda.json` | Tienda del servidor: categorías, objetos, precio de compra y de venta. |
| `tienda-monedas.json` | La «Tienda de monedas» de la web: objetos del servidor que se compran con Fantastic Coins (mejor con `/tf web tienda add <precio>` con el objeto en la mano). |
| `ruleta.json` | Copia de la ruleta de la web (la manda la web por el puente: cámbiala allí). |
| `protecciones.json` | Piedras de protección: tamaños, límites y opciones de las zonas. |
| `limites.json` | Límites de objetos y bloques. |
| `vfx.json` | Efectos de muerte y demás efectos visuales (los de pago se compran en la web). |
| `skills.json` | Clases de skills. |
| `ayuda.json` | Textos de la Guía del pad. |
| `pad.json` | Qué apps del pad ven los jugadores y ajustes del pad. |
| `jefes.json` | Jefes de incursión. |

Las crates (cajas) van aparte, en `config/fscrates/cajas/` (un JSON por caja; `config/fscrates/items_referencia.json`
tiene los ids de los objetos).

## Copias de seguridad

Cuando una versión nueva del mod cambia la economía de un archivo, guarda el de antes al lado como
`nombre.antes-vN.json`. Si quieres volver a algo de antes, cópialo de ahí.
