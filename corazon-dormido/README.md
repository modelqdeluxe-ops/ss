# Corazón Dormido

Juego de exploración y minería bajo tierra para el móvil, al estilo de Core Keeper, con arte, nombres y jefes
propios. Como Core Keeper, el mundo es **3D**: bloques con texturas de pixel art vistos con una cámara inclinada,
luz de colores que no atraviesa las paredes, y personajes y objetos de pie dentro del mundo. Cavas paredes, sacas mineral, creas herramientas, enciendes la
oscuridad con antorchas y derrotas a los guardianes para despertar el Corazón que duerme en el centro del mundo.

Estado: **fase 1 completa: el anillo interior** (tres regiones, tres guardianes, el final del Corazón y todos
los sistemas de base). Lo que viene después está en `DISEÑO.md`. Funciona en el navegador del móvil y del
ordenador, sin instalar nada.

## Descargar

Cada versión es una Release de GitHub con el juego en **un solo archivo .html** (se abre en el navegador, sin
instalar nada) y en .zip:

- La última, siempre en el mismo enlace:
  https://github.com/modelqdeluxe-ops/ss/releases/download/corazon-dormido/corazon-dormido.html
- Una concreta: `https://github.com/modelqdeluxe-ops/ss/releases/tag/corazon-dormido-v<versión>`

Las versiones están en `VERSIONES.txt`. Para publicar una nueva, añade su línea (con `HEAD` como commit) y
súbela: el workflow `.github/workflows/corazon-dormido.yml` prueba el juego, crea las Releases que falten (nunca
como «latest») y actualiza la fija. Para empaquetar a mano: `python3 corazon-dormido/herramientas/empaquetar.py`.

## Cómo se juega

| Móvil | Ordenador | Qué hace |
|---|---|---|
| Arrastrar en la mitad izquierda | WASD o flechas | Moverse |
| Mantener en la mitad derecha | Espacio o clic | Usar lo que llevas: picar, pelear, comer, colocar, arar, regar, pescar o disparar |
| Tocar cerca del personaje | Clic cerca del personaje | Apuntar a esa casilla |
| Botón USAR (sale solo) | F | Abrir lo que tienes al lado: mesa, cofre, olla, cama, estatua o el Corazón |
| Barra de abajo | 1–9 y 0 | Elegir objeto |
| Botones de arriba | E, C, M, Esc | Mochila, crear, mapa y pausa |

Si llevas un material en la mano, la acción usa sola el mejor pico (o la mejor espada, si hay una criatura
cerca).

## Lo que hay (fase 1)

- **Mundo de 320×320 casillas, distinto en cada partida**, en anillos alrededor del Corazón. Fuera de la Gran
  Muralla (radio 142) sigue la roca: ahí irán las regiones de las fases siguientes.
  - **Santuario del Corazón** (centro): el Corazón sobre su altar, tres estatuas de los guardianes y antorchas.
    Ahí te curas y ahí despiertas si caes (o en tu cama, si has puesto una).
  - **Las Raíces** (hasta radio 48): tierra y césped de cueva, raíces gruesas, cobre, charcas, setas y flores.
  - **Cuevas de Arcilla**: arcilla, estaño, oro, colmenas de larvas con huevos que brillan.
  - **Ruinas Olvidadas**: salas de ladrillo con columnas, vasijas, cofres antiguos con botín, dos laberintos,
    hierro y la sala del trono de Ozmar con alfombra y antorchas mágicas.
- **Luz que no atraviesa paredes**, con trama a cuadritos, resplandor y rayos de luz que bajan por grietas en
  algunas cavernas. Las criaturas solo salen en lo oscuro: las antorchas protegen. Las menas brillan un poco en
  la oscuridad para que se vean de lejos.
- **98 objetos y 58 recetas** (`js/datos.js`): mesas de trabajo de madera, cobre, estaño y hierro; horno;
  yunques de cobre, estaño y hierro. Las recetas toman material de los **cofres** que tengas a menos de 10
  casillas, como en Core Keeper.
- **Picos por poder**: cada pared y cada mena pide un pico mínimo (tierra 0, arcilla 10, piedra 20, ladrillo
  de las ruinas 35). El pico mejor abre la región siguiente.
- **Equipo**: espadas, mazas (lentas y aturden), arcos con flechas, picos, casco, peto y botas de cobre, estaño,
  hierro y quitina, y accesorios (anillos, collar, el amuleto del Topo, el broche de la Reina, la corona de
  Ozmar). La armadura se ve puesta en el personaje. Las cosas tienen rareza (común, rara, épica, legendaria).
- **Construir**: bloques de tierra, arcilla, colmena, piedra y ladrillo, pared y suelo de madera, losas,
  alfombra, puertas (las criaturas no pasan), cama, faroles, macetas.
- **Granja**: azada para arar, semillas de baya, hongo, bulbo y trigo con cuatro etapas, regadera (crecen el
  doble de rápido).
- **Cocina**: una olla mezcla dos ingredientes y sale un plato con nombre propio que cura y da mejoras durante
  unos minutos (vida máxima, defensa, daño, velocidad, minado, regeneración…).
- **Pesca**: lanza la caña al agua, tira cuando pica y en la lucha mantén para recoger cuando el pez descansa y
  suelta cuando tira, o se rompe el sedal. Cuatro peces según la región.
- **Habilidades que suben con el uso** (hasta el nivel 50): minería, cuerpo a cuerpo, a distancia, vitalidad,
  carrera, artesanía, jardinería, pesca y cocina.
- **Criaturas** con ataques avisados (parpadean o marcan el suelo antes de golpear) y un hueco para
  contraatacar: babosas y babosas rojas (se parten en dos), setones, larvas, larvas grandes (escupen ácido) y
  grutos lanceros, chamanes (lanzan orbes) y brutos (mazazo en área). Rodean las paredes con un mapa de
  distancias.
- **Tres guardianes**, cada uno en su guarida; despiertan al entrar, se enfurecen a media vida y vuelven curados
  a su sitio si huyes:
  - **Topo Rey** (Raíces): lanza rocas, se entierra y sale debajo de ti, embiste (si choca contra la pared se
    queda aturdido).
  - **Reina Larva** (Arcilla): escupe ácido que deja charcos, pone larvas y hace caer el techo (círculos en el
    suelo).
  - **Ozmar el Corrompido** (Ruinas): orbes que te siguen, anillos de orbes, se teletransporta, invoca grutos y
    lanza filas de pilares de sombra. Tras cada ráfaga se queda cansado un momento: es tu turno.

  Cada uno suelta su **cristal**. Al ponerlo en su estatua del santuario, el Corazón late más fuerte; con los
  tres, el Corazón despierta (final de la fase 1).
- **Objetivos que te guían** (14), minimapa con el nombre de la región, mapa completo con las guaridas
  encontradas, mochila de 40 huecos, barra de 10, y guardado automático cada 25 s y al salir.
- **Sonido sintetizado**, sin archivos.

## Archivos

```
index.html         la página para jugar (ábrela en el navegador; necesita WebGL2)
artefacto.html     la misma página sin <html>/<head>, para publicarla como Artifact en claude.ai
DISEÑO.md          el diseño completo: regiones, metales, sistemas y las fases que faltan
js/arte.js         arte con código: terreno, paredes, jugador, criaturas y objetos de la demo, iconos base
js/arte2.js        arte de la fase 1: suelos nuevos, muebles, cultivos, criaturas, Reina, Ozmar, armadura e iconos
js/gl.js           motor 3D con WebGL2: trozos del mundo, cámara, luz, sprites de pie, sombras, brillos, halos, rayos
js/fuente.js       letra de 3×5 píxeles con tildes y Ñ
js/sonido.js       sonidos con WebAudio
js/datos.js        números del juego: suelos, paredes, menas, objetos, recetas, criaturas, jefes, cultivos, peces
js/mundo.js        generación del mundo, luz, mapa y guardado
js/entidades.js    jugador, inventario, equipo, habilidades, herramientas, botín, partículas
js/criaturas.js    criaturas: aparecer, mapa de distancias, conductas, proyectiles y avisos en el suelo
js/jefes.js        los tres guardianes, las estatuas y el despertar del Corazón
js/sistemas.js     crear (con cofres cerca), cofres, olla, cultivos y pesca
js/ui.js           controles táctiles y de teclado, HUD y paneles
js/principal.js    bucle, cámara, dibujo del mundo y arranque
herramientas/probar.cjs         prueba rápida en un móvil simulado (capturas + errores + guardar/cargar)
herramientas/probar-todo.cjs    prueba a fondo: regiones, jefes, criaturas, paneles, base, pesca, FPS y dibujos que falten
herramientas/simular-jefes.cjs  un bot pelea contra cada guardián con el equipo de su momento (equilibrio)
herramientas/empaquetar.py      saca el .html de un solo archivo y el .zip de una versión (para las Releases)
VERSIONES.txt      las versiones publicadas como Releases
```

Es JavaScript sin dependencias ni compilación. El mundo se dibuja con WebGL2 (`js/gl.js`) y la interfaz en un
lienzo 2D pequeño encima (unos 220 píxeles por el lado corto), escalado sin suavizar.

### Cómo se dibuja (medido sobre capturas de Core Keeper)

- **Medidas sacadas de las capturas oficiales de Steam (1920×1080):** cada píxel del arte ocupa 4 de pantalla,
  cada bloque mide 16×16 píxeles de arte, se ven unas 30×17 casillas y los personajes miden unos 16×22.
  Aquí el lado corto de la pantalla muestra unas 14 casillas, para que en el móvil no se vea todo diminuto.
- **Píxeles nítidos**: cada píxel del arte mide un número entero de píxeles de la pantalla. El mundo se pinta a
  2 píxeles por píxel del arte y el navegador lo amplía sin suavizar; la cámara se mueve a medio píxel del arte.
- **Paredes**: bloques 3D con tapa oscura arriba, cara delante y caras a los lados.
  - La cámara mira hacia abajo a 50° con poca perspectiva (13° de campo).
  - El suelo se estira en profundidad para que cada casilla se vea cuadrada.
  - El mundo va en trozos de 16×16 casillas con oclusión en las esquinas: al picar se rehace solo el trozo
    tocado (como mucho 3 por cuadro).
- **Luz**: `mundo.js` propaga la luz casilla a casilla (no atraviesa paredes). La de las cosas fijas se guarda
  y solo se rehace cuando el mundo cambia; las llamas parpadean aparte y lo que se mueve se suma cada cuadro.
  El sombreador toma la luz en el centro de cada píxel del arte y la reparte en 7 escalones con trama 4×4.
- **Sprites de pie**: personajes y objetos son planos verticales; una pared tapa de verdad al que pasa por
  detrás. Los guardianes tienen algo de luz propia para que se lean en la oscuridad.
- **Posproceso**: la escena se guarda con luz de hasta 2 y solo resplandece lo que pasa de 1 (llamas,
  cristales, ácido, magia), con dos desenfoques. Después lleva una curva suave en los blancos, un tono por
  región, viñeta y grano fino.
- **Vida en el suelo**: guijarros, hierba, flores, setas, estalagmitas, huesos, huevos y vasijas rotas según la
  región, con más junto a las paredes. Charcas con destellos que se mueven.

## El arte

El arte está en `js/arte.js`. Usa la misma idea que el skill **pxl**, copiado en `.claude/skills/pxl-pixel-art`
(MIT, de [crsolver/claude-pxl](https://github.com/crsolver/claude-pxl)): se escribe la mitad de lo simétrico y
se refleja, el contorno es automático, y las variantes salen cambiando los colores. Por ejemplo, el pico de
cobre, el de estaño y el de hierro son el mismo dibujo.

La sesión en la que se hizo la demo no dejó ejecutar `pxl`: el filtro de seguridad bloquea código descargado de
fuera, así que el arte se dibujó con código propio. Para usar `pxl` en otra sesión hay que permitirlo en la
configuración de Claude Code, con una regla de Bash para `node .claude/skills/pxl-pixel-art/bin/pxl.js`.

Para retocar un sprite, busca su nombre en `arte.js` (`jug_`, `babosa_`, `topo_`, `i_pico_`…), cambia la
cuadrícula y recarga. Cada letra es un color de la paleta de ese bloque.

## Probar

```bash
node corazon-dormido/herramientas/probar-todo.cjs /tmp/capturas
```

```bash
node corazon-dormido/herramientas/probar-todo.cjs /tmp/capturas vertical
```

```bash
node corazon-dormido/herramientas/simular-jefes.cjs 4
```

`probar-todo` tiene que acabar en «guardar y cargar: bien», «no falta ningún dibujo» y «sin errores». Mira las
capturas antes de dar algo por bueno. `simular-jefes` dice si el bot gana y cuánta comida gasta: con el equipo
de cada momento y 10 platos, el bot (que esquiva peor que una persona) gana casi siempre gastando 1–9 platos.

## Cómo seguir (por orden)

Las fases están en `DISEÑO.md`:

1. **Fase 2: la Selva Esmeralda**, al otro lado de la Gran Muralla: dos titanes, mercaderes que se mudan a tu
   base y mascotas.
2. **Fase 3: el Mar Hundido**: barcos, peces de mar y su titán.
3. **Fase 4: el Desierto del Origen**: vagonetas, lava y dos jefes.
4. **Fase 5: la Frontera Cristalina** y el final.
5. **Fase 6**: app para Android e iOS (Capacitor), guardado en la nube y cooperativo.

**Para ganar dinero** (como Core Keeper, que es de pago): la demo gratis hasta el primer jefe y el juego
completo con un pago único. Si se hace gratis, que los anuncios sean opcionales y den premio, por ejemplo revivir
con todo o doblar el botín de un cofre. Nada de pagar para ganar.
