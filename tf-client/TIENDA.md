# La tienda del servidor y lo que cuesta ganar monedas (1.3.134)

El dueño: «estudia lo que cuesta ganar monedas con lo que ofrece el mod; de ahí fija precios en la tienda, con todos y
cada uno de los objetos del juego agrupados en categorías (Bloques y Materiales de Construcción, Decorativos, Minerales
—sin diamante ni netherita—, Equipo, Drops de mobs —nada del Nether ni del End— y las protecciones). Precios justos, ni
regalados. Y como tienda no compramos nada más que Tesoros, objetos extremadamente raros, con un límite diario».

- La tienda: `config/tfclient/tienda.json` (por defecto, `src/main/resources/tfclient-shop-default.json`). Se genera
  con `tools/tienda/precios.py`; la tabla entera de precios, en [`TIENDA-PRECIOS.md`](TIENDA-PRECIOS.md).
- Al arrancar la 1.3.134, el servidor cambia su `tienda.json` por la nueva (lleva `versionTienda: 2`; la de antes se
  guarda en `tienda.antes-v2.json`). Lo que cambie el dueño después (en el pad de administrador o en el archivo) se
  queda. Si vuelven a cambiar los precios: subir `VERSION` en `precios.py` y en `TFShopConfig.VERSION`.

## 1. Lo que se gana (medido en la configuración por defecto del mod)

Todo sale de `tfclient-jobs-default.json` (oficios), `tfclient-misiones-default.json` (misiones y cazas),
`PadRewards` (calendario) y `Difficulty` (jefes), ya rebajados al 60 % en la 1.3.115 («economía exigente»).

**Oficios** (se tiene uno; cada nivel suma un 3 %: nivel 10 ×1,3, nivel 25 ×1,75, nivel 50 ×2,5):

| Oficio | Lo que paga cada acción | Ritmo normal en una hora | Monedas/hora (nivel 1) |
|---|---|---|---:|
| Minero | mena 0,36 · piedra 0,01 | ~120 menas y ~1.500 de piedra | ~58 |
| Leñador | tronco 0,09 | ~700 troncos | ~63 |
| Excavador | bloque de pala 0,02 | ~2.500 bloques | ~50 |
| Pescador | captura 0,48 | ~110 capturas | ~53 |
| Cazador | hostil 0,36 · animal 0,03 | ~120 hostiles (de noche) | ~43 |
| Granjero | cultivo maduro 0,06 · criar 0,24 | ~900 cultivos | ~54 |
| Constructor | bloque colocado 0,02 | ~2.400 bloques | ~48 |
| Herrero / Alquimista / Encantador | 0,06–0,9 | lo que dejen los materiales | ~40–60 |

Más: subir de nivel da 6 + 1,8 × nivel (y los hitos de los niveles 10, 25 y 50).

**Lo demás, al día**: misiones del oficio (3 al día, 12–21 cada una) ~40; misiones del pad (3 diarias, 7–21, que suben
×1–2,5 en la temporada) ~30; semanales (×4 de pago) ~15 al día; cazas (4 cada 12 h, 18–54) ~15–40; calendario de
recompensas 5–40 (media ~15). Los jefes de las grietas pagan 150 (fácil) a 2.000 (demencial) a cada jugador que les hizo
daño, pero son de vez en cuando.

| Jugador | Horas al día | Oficio | Al día |
|---|---:|---|---:|
| Normal | 2 | nivel ~15 (×1,45, ~80/h) | **~270** |
| Activo | 5 | nivel ~40 (×2,2, ~120/h) | ~750 (más los jefes) |

**Unidad del estudio: una hora de trabajo ≈ 75 monedas** (`SALARIO_HORA`).

## 2. Cómo sale cada precio

1. **Lo de la naturaleza** (`BASE` en `precios.py`): cuántas unidades consigue un jugador en una hora buscándolo él (con
   herramientas de hierro y sin granjas). Comprarlo cuesta **6 veces** lo que ganaría trabajando ese mismo rato:
   `precio = 75 × 6 / unidades por hora`. Con ese 6 salen justo las referencias que el servidor ya tenía (hierro 12,
   oro 20, carbón 4, esmeralda 40, la pila de roca 12): comprar nunca es más barato que trabajar (no es regalado), y
   tampoco es imposible.
2. **Lo fabricado**: lo que valen sus ingredientes en la receta más barata del juego (mesa, horno, cortapiedras o
   herrería, leídas del jar de Minecraft 1.20.1) **+ 5 %**; en el horno, más el combustible (0,15 por fundición). Los
   cubos que devuelve una receta (la tarta) no se cuentan. Lo que se hace en el mundo (hormigón con agua, cobre que se
   oxida, troncos sin corteza, yunques gastados) va en `MUNDO`.
3. **Lotes**: lo de construir y decorar, de pila en pila (64 o 16) mientras la pila no pase de 256 monedas; lo demás,
   de 1 en 1 (o de 8/16 si es muy barato). Precios redondos (de 5 en 5 desde 100) y 2 como poco.
4. **Las menas** con toque de seda valen al menos lo que sueltan con fortuna (×2,2).

**Qué compras con un día normal (~270)**: 22 lingotes de hierro, o 14 de oro, o 6 pilas de troncos, o 11 pilas de
cristal, o un peto y una espada de hierro con su pico, o una manzana dorada y media.

## 3. Lo que hay en la tienda (1.039 objetos en 10 categorías)

| Categoría | Objetos | Ejemplos |
|---|---:|---|
| Bloques y Materiales de Construcción | 391 | roca 12/64, piedra 23/64, tablones 11/64, troncos 41/64, cristal 20/64, ladrillos 150/64, hormigón 15/64, obsidiana 120/16 |
| Decorativos | 274 | antorchas 15/64, faroles 190/16, flores, alfombras, camas, estandartes, velas, corales, cabezas |
| Minerales | 45 | carbón 4, hierro 12, oro 19, cobre 14/8, redstone 24/16, lapislázuli 14/8, esmeralda 38, cuarzo |
| Equipo | 52 | espada de hierro 25, pico de hierro 38, peto de hierro 100, arco 5, escudo 14, flechas 11/16 |
| Drops de mobs | 24 | hueso 24/16, hilo 24/16, pólvora 4, bola de slime 8, cuero 24/8, membrana 11, nautilo 30 |
| Granja y Comida | 80 | semillas, brotes, cultivos, carne, pan 13/8, manzana dorada 165, zanahoria dorada 19 |
| Redstone y Transporte | 79 | pistón 15, tolva 64, raíles, vagonetas, barcas, montura 75 |
| Utilidades | 71 | cofre 23/16, horno 13/8, yunque 405, cubo 38, mapa 59, etiqueta 75, discos 45, tintes |
| Protecciones | 7 | 10x10 300 · 25x25 1.000 · 40x40 2.500 · 64x64 5.000 · 80x80 8.000 · 100x100 12.000 · 150x150 20.000 |
| Tesoros (solo VENDER) | 16 | ver abajo |

**Las protecciones**: la de 250x250 cuesta 10 $ en la web, que son 25.000 monedas (2.500 por dólar), así que la de
150x150 se queda por debajo (20.000, ~10 semanas de un jugador normal) y la de 10x10 cuesta un día. Las de 250, 300 y
500 siguen siendo solo de la web.

**Fuera de la venta (238)**, con su motivo en `precios.py`:
- No se consiguen en supervivencia: bedrock, generadores, bloques de comandos, huevos de criatura, pociones, libros
  encantados, cabezas de jugador…
- **Diamante y netherita** y todo lo que los lleva en su receta (herramientas y armaduras de diamante, la mesa de
  encantamientos, el tocadiscos, el faro, la magnetita).
- **Lo del Nether que hace avanzar** (vara y polvo de blaze, lágrima de ghast, crema de magma, verruga y sus ladrillos
  rojos, soporte de pociones, rosa del Wither, luces de rana, cabeza de piglin) y **todo lo del End** (piedra del End,
  púrpura, coros, perla y ojo de ender, cofre de ender, varas del End, cristales, cajas de shulker, aliento de dragón).
  Los bloques del Nether para construir (netherrack, basalto, piedra negra, cuarzo, ladrillos del Nether) sí se venden.
- Las plantillas de herrería (se duplican con diamantes).
- Los Tesoros (la tienda no los vende: solo los compra).

## 4. Tesoros: lo único que compra la tienda

| Tesoro | Paga | Al día |
|---|---:|---:|
| Huevo de dragón | 5.000 | 1 |
| Estrella del Nether | 1.800 | 1 |
| Élitros | 1.200 | 1 |
| Cabeza de dragón | 700 | 1 |
| Manzana dorada encantada | 600 | 1 |
| Disco «5» | 500 | 1 |
| Corazón del mar | 450 | 1 |
| Disco «otherside» | 400 | 1 |
| Disco «Pigstep» / «Relic» | 350 | 1 |
| Tridente | 350 | 1 |
| Tótem de inmortalidad | 300 | 1 |
| Huevo de sniffer | 200 | 1 |
| Calavera de esqueleto del Wither | 150 | 2 |
| Fragmento resonante | 80 | 4 |
| Fragmento de disco | 40 | 9 |

Lo que se puede sacar en granja (tótems de las incursiones, tridentes de los ahogados, calaveras del Wither) va con
límite 1–2 al día y a precio de ~1 día de trabajo, para que no sea una fuente de monedas sin fin. Se venden sin nombre,
encantamientos ni daño (los élitros y el tridente, sin usar). En el pad, VENDER enseña lo que llevas y debajo la lista
de Tesoros con lo que pagan.
