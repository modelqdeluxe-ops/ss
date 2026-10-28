# Corazón Dormido

Juego de exploración y minería bajo tierra para el móvil, al estilo de Core Keeper, con arte, nombres y jefes
propios. Vista desde arriba en pixel art. Cavas paredes, sacas mineral, creas herramientas, enciendes la
oscuridad con antorchas y derrotas a los guardianes para despertar el Corazón que duerme en el centro del mundo.

Estado: **demo 0.1 jugable** (un jefe, tres zonas y toda la cadena de crear). Funciona en el navegador del móvil
y del ordenador, sin instalar nada.

## Cómo se juega

| Móvil | Ordenador | Qué hace |
|---|---|---|
| Arrastrar en la mitad izquierda | WASD o flechas | Moverse |
| Mantener en la mitad derecha | Espacio o clic | Usar lo que llevas: picar, pelear, comer o colocar |
| Tocar cerca del personaje | Clic cerca del personaje | Apuntar a esa casilla |
| Barra de abajo | 1–8 | Elegir objeto |
| Botones de arriba | E, C, M, Esc | Mochila, crear, mapa y pausa |

Si llevas un material en la mano, la acción usa sola el mejor pico (o la mejor espada, si hay una criatura
cerca).

## Lo que hay en la demo

- **Mundo de 256×256 casillas, distinto en cada partida.** Tiene tres anillos: tierra, arcilla y piedra.
- **Cuevas y túneles.** Hay menas de cobre, estaño y hierro dentro de las paredes, y raíces, arbustos con bayas, hongos y cristales que brillan.
- **Luz que se mueve por las cuevas y no atraviesa las paredes.** El casco del minero alumbra un poco y las antorchas, lámparas, cristales y el Corazón alumbran más. Solo ves y queda en el mapa lo que has iluminado. Las criaturas solo salen en lo oscuro, así que las antorchas protegen.
- **El santuario del Corazón en el centro.** Tiene ruinas, cuatro entradas y tres pedestales. Ahí te curas rápido y ahí despiertas si te desmayas.
- **Cadena de crear** (`js/datos.js`, `RECETAS`): raíces → madera → mesa de trabajo → cobre → horno → lingotes → yunque → picos, espadas y armaduras de cobre, estaño y hierro, y lámparas de cristal. Cada pico más fuerte abre la zona siguiente: la piedra necesita cobre y el hierro necesita estaño.
- **Hambre y comida**: bayas, hongos y larvas, y en el horno se asan para que curen más.
- **Criaturas**:
  - babosas en la tierra: saltan hacia ti;
  - larvas en la arcilla;
  - escarabajos en la piedra: embisten.
- **El Topo Rey**, el guardián de la tierra, en su guarida a 30 casillas del centro. Tiene cuatro ataques:
  - lanza rocas;
  - se entierra y sale debajo de ti;
  - te embiste: si choca contra la pared se queda aturdido y recibe más daño;
  - con la mitad de vida se enfurece y llama babosas.

  Suelta el **Fragmento del Corazón** y la **Garra del Topo**. Al poner el fragmento en un pedestal, el Corazón late. Ahí acaba la demo.
- **Objetivos que te guían** (arriba a la derecha), minimapa, mapa completo, mochila de 32 huecos con
  armadura, y guardado automático en el navegador cada 25 s y al salir.
- **Sonido sintetizado**, sin archivos: golpes, picar, recoger, el rugido del jefe, y una cueva con gotas,
  eco y una cajita de música.

## Archivos

```
index.html         la página para jugar (ábrela en el navegador)
artefacto.html     la misma página sin <html>/<head>, para publicarla como Artifact en claude.ai
js/arte.js         TODO el arte, dibujado con código (cuadrículas de letras, espejo, contorno, recolor)
js/fuente.js       letra de 3×5 píxeles con tildes y Ñ
js/sonido.js       sonidos con WebAudio
js/datos.js        números del juego: paredes, objetos, recetas, criaturas, jefe, objetivos
js/mundo.js        generación del mundo, luz, mapa y guardado
js/entidades.js    jugador, criaturas, Topo Rey, botín, partículas, inventario, crear
js/ui.js           controles táctiles y de teclado, HUD y paneles
js/principal.js    bucle, cámara, dibujo por filas y arranque
herramientas/probar.cjs   prueba automática en un móvil simulado (capturas + errores + guardar/cargar)
```

Es JavaScript sin dependencias ni compilación. El dibujo se hace en un lienzo pequeño (unos 220 píxeles por el
lado corto) que se escala por un número entero, así que los píxeles siempre quedan nítidos.

### Cómo se dibuja

- Las paredes se dibujan levantadas medio bloque, con su cara de delante, y se pintan fila a fila junto con las
  criaturas. Así una pared tapa al personaje que está detrás.
- La cara de arriba de cada pared elige entre 16 bordes según qué vecinas están abiertas.
- La luz se calcula por casilla y se pone encima en modo «multiplicar», suavizada.
- Lo que brilla solo (llamas, cristales, el Corazón) se vuelve a dibujar encima de la oscuridad.

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
node corazon-dormido/herramientas/probar.cjs /tmp/capturas
```

```bash
node corazon-dormido/herramientas/probar.cjs /tmp/capturas vertical
```

Las dos tienen que acabar en «guardar y cargar: bien» y «sin errores». Mira las capturas antes de dar algo por
bueno.

## Cómo seguir (por orden)

1. **Jefe 2: la Reina Larva** (arcilla): pone huevos que se abren y escupe ácido. Suelta el segundo fragmento.
2. **Jefe 3: el Coloso de cristal** (piedra): refleja la luz y hay que apagarle las lámparas. Suelta el tercer fragmento y el final de verdad.
3. **Cofres** para guardar cosas y **granja**: azada, semillas de bayas y de hongos que crecen con el tiempo.
4. **Cocina**: una olla que mezcla dos ingredientes y da comida con mejoras (más velocidad, más minado…).
5. **Habilidades que suben con el uso**: minería, combate y vida, como en Core Keeper.
6. **Más zonas**: bosque de hongos gigantes, lago subterráneo con pesca y ruinas con trampas.
7. **Pasarlo a app** con Capacitor (Android e iOS) y guardar en la nube.
8. Cooperativo para 2–4 jugadores, solo si lo anterior funciona y gusta.

**Para ganar dinero** (como Core Keeper, que es de pago): la demo gratis hasta el primer jefe y el juego
completo con un pago único. Si se hace gratis, que los anuncios sean opcionales y den premio, por ejemplo revivir
con todo o doblar el botín de un cofre. Nada de pagar para ganar.
