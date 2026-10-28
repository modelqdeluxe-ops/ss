# Corazón Dormido: diseño del juego completo

Este documento manda sobre lo que se construye. Está basado en lo que hace grande a Core Keeper (investigado en
su wiki y en sus tráileres oficiales), con mundo, nombres, criaturas y arte propios. La meta es igualarlo en
sensación y superarlo en el móvil: controles pensados para el dedo, partidas que se pueden cortar en cualquier
momento y gráficos a la altura.

## 1. La promesa

Despiertas bajo tierra, junto a un Corazón de cristal dormido. Cavas, alumbras la oscuridad, creas
herramientas cada vez mejores, levantas tu base, cultivas, cocinas y pescas. Los guardianes de cada región te
esperan en sus guaridas: cuando los vences, el Corazón late y el mundo se abre hacia regiones nuevas.

Lo que tiene que sentirse en cada minuto:

- **Picar es un placer**: cada golpe suena, salta polvo y la pared se agrieta. Al romperla salen materiales.
- **La oscuridad da miedo y la luz da hogar**: las criaturas nacen en lo oscuro y las antorchas son tu territorio.
- **Siempre hay un siguiente paso**: un metal mejor, un jefe, una receta o una región nueva.
- **Tu base es tuya**: paredes, suelos, puertas, cofres, camas, cultivos, mesas y luces donde quieras.

## 2. El mundo

El mundo es un disco de cuevas alrededor del Corazón. Las regiones van en anillos y separadas por zanjas.

| Anillo | Región | Suelo y paredes | Metal | Guardián |
|---|---|---|---|---|
| Centro | **Santuario del Corazón** | ruinas azules | — | — |
| 1 | **Las Raíces** | tierra, césped de cueva, raíces | Cobre | **Topo Rey** (guarida en la tierra) |
| 2, un lado | **Cuevas de Arcilla** | arcilla, capullos, colmenas | Estaño | **Reina Larva** (en una colmena) |
| 2, el otro lado | **Ruinas Olvidadas** | piedra labrada, salas y laberintos | Hierro | **Ozmar el Corrompido** (sala del trono) |
| Borde | **La Gran Muralla** | raíces de piedra que no se rompen | — | Se abre cuando el Corazón despierta |
| 3 | **Selva Esmeralda** | hierba, árboles, flores | Carmesí | **Tamar, Titán del Cielo** y **Hiedra Viva** |
| 4 | **Mar Hundido** | arena de playa, algas, agua | Abisalita | **Abisal, Titán del Mar** |
| 5 | **Desierto del Origen** | arena, templos, lava | Astralita | **Ra-Sol, Titán de Arena** y **Magmo** |
| 6 | **Frontera Cristalina** | cristal | Solarita | Arena de los antiguos |

El Corazón dice hacia dónde queda cada región («la Selva Esmeralda está al noreste»). Con sus tres estatuas
activas, la Gran Muralla se abre.

## 3. Progresión por metal

Cada metal trae un **pico más fuerte** (más poder de minado: las paredes de la región siguiente exigen un
mínimo), armas, armadura (casco, peto, botas), una **mesa de trabajo** y un **yunque** de su nivel.

| Metal | Poder de minado | Abre |
|---|---|---|
| Madera y pico viejo | 10 | Tierra |
| Cobre | 20 | Arcilla |
| Estaño | 35 | Piedra de las Ruinas |
| Hierro | 55 | La Muralla (cuando se abre) |
| Carmesí | 80 | Arena de playa |
| Abisalita | 110 | Arenisca del desierto |
| Astralita | 150 | Cristal |
| Solarita | 200 | — |

## 4. Sistemas

- **Habilidades que suben con el uso**: Minería, Carrera, Cuerpo a cuerpo, A distancia, Vitalidad, Artesanía,
  Jardinería, Pesca, Cocina, Magia, Invocación y Explosivos. Cada nivel da una mejora pequeña. Más adelante cada
  una tendrá un árbol de talentos.
- **Construcción**: picar una pared da su bloque, y el bloque se puede volver a poner. También hay suelos de
  madera y piedra, puertas, vallas y escaleras de luz.
- **Cofres**: guardan 30 cosas. Las mesas crean usando lo que hay en los cofres de alrededor (10 casillas).
- **Cama**: es el punto donde reapareces.
- **Granja**: la azada ara la tierra, las semillas crecen en etapas y se riega con la regadera. A veces sale un
  cultivo dorado.
- **Cocina**: la olla junta dos ingredientes. El plato tiene tipo (sopa, guiso, brocheta…), un prefijo según los
  ingredientes y mejoras que duran unos minutos (minado, daño, velocidad, vida, regeneración, suerte).
- **Pesca**: la caña se lanza al agua. Cuando pica hay un minijuego: tirar cuando el pez descansa y soltar
  cuando lucha. Cada región tiene sus peces y alguno raro.
- **Mercaderes** (fase 2): se mudan a una habitación cerrada de hasta 64 casillas con cama y su objeto.
- **Mascotas y monturas** (fase 2 y siguientes).
- **Rareza** de los objetos (común, raro, épico y legendario), con su color.

## 5. Combate

- Cuerpo a cuerpo con arco de golpe, empuje y crítico. Cada arma tiene su velocidad (espada, maza, lanza).
- A distancia: arco con flechas y honda con piedras. En el móvil, el apuntado es automático hacia la criatura
  más cercana, delante de ti.
- Cada criatura tiene una forma de atacar que se lee bien: aviso, golpe, hueco para contraatacar. Las fuertes
  avisan con un parpadeo.
- Los jefes tienen fases, ataques con aviso en el suelo y un momento en que se quedan vulnerables.

## 6. Gráficos

- Mundo 3D de bloques con texturas de pixel art de 16×16, cámara inclinada con poca perspectiva. La tapa de la
  pared es oscura y la cara se ve.
- Luz por casilla que se propaga por las cuevas y no atraviesa paredes. Se lee en el centro de cada píxel del
  arte y se reparte en escalones tramados.
- Resplandor solo en lo que brilla, viñeta, gradación de color y grano fino.
- Los minerales destellan en blanco en la oscuridad (como pista de dónde picar). Hay rayos de luz en las
  cavernas grandes, chispas, polvo, agua con destellos y lava que brilla.
- Mucho detalle en el suelo: guijarros, hierba, setas, raíces, huesos y estalagmitas de pie.

## 7. Rendimiento en el móvil

- Se dibuja a una resolución interna de unos 2 píxeles por píxel del arte y se escala nítido.
- La geometría va en trozos de 16×16 casillas que solo se rehacen al cambiar.
- La luz de lo fijo (antorchas, cristales) se guarda y solo se recalcula al cambiar el mundo. La del jugador y
  sus antorchas se calcula cada cuadro.
- No se crea basura en cada cuadro: los lotes de vértices se reutilizan.
- Objetivo: 60 fps en un móvil de gama media.

## 8. Fases

1. **Anillo interior completo**:
   - las tres regiones con sus tres guardianes, el Corazón con sus estatuas y la Gran Muralla;
   - construcción, cofres, cama, granja, cocina, pesca y habilidades;
   - casco, peto y botas; arco;
   - optimización.
2. **Selva Esmeralda**: dos titanes, mercaderes y mascotas.
3. **Mar Hundido**: barcos, peces de mar y su titán.
4. **Desierto del Origen**: vagonetas, lava y dos jefes.
5. **Frontera Cristalina** y el final.
6. **App para Android e iOS**, guardado en la nube y cooperativo.

Cada fase se entrega completa en lo suyo: probada, sin errores y con todo dibujado.
