# Corazón Dormido

Juego de exploración y minería bajo tierra para el móvil, al estilo de Core Keeper, con arte, nombres y jefes
propios. Como Core Keeper, el mundo es **3D**: bloques con texturas de pixel art vistos con una cámara inclinada,
luz de colores que no atraviesa las paredes, y personajes y objetos de pie dentro del mundo. Cavas paredes, sacas mineral, creas herramientas, enciendes la
oscuridad con antorchas y derrotas a los guardianes para despertar el Corazón que duerme en el centro del mundo.

Estado: **versión 0.7.0, el mundo vasto y la app de Android**. Es un juego para **Android** (app nativa que lleva
el juego dentro; va a la Play Store) que también se puede abrir en el navegador. Fantasía oscura: el mundo es el
cuerpo de un dios muerto, Ásor, y su Corazón duerme enfermo de la Podre (la historia, la dirección de arte, el
bestiario y los jefes están en **`LORE.md`**, que manda sobre todo lo demás). Mundo de 2048×2048 con la Selva
Esmeralda, la Ciénaga de la Podre y la Ciudad Velada; siete jefes; puzles, encargos, bestiario y 48 tablillas de
historia; banda sonora original. Lo que viene después está en `DISEÑO.md` y `CONTINUAR.md`.

## Descargar

**La app de Android**, siempre la última versión:
https://github.com/modelqdeluxe-ops/ss/releases/download/corazon-dormido/corazon-dormido.apk

Se descarga en el móvil y se abre para instalarla (la primera vez Android pide permitir instalar apps de esa
fuente). Cada APK nuevo se instala encima del anterior y la partida se conserva. Cada versión está en su Release
(`https://github.com/modelqdeluxe-ops/ss/releases/tag/corazon-dormido-v<versión>`) con su `.apk` y el `.aab` para la
Play Store; la compilación de cada push está en la Release `corazon-dormido-android`. Hasta la 0.6.0 las Releases
llevaban el juego de navegador (`.html` y `.zip`).

Las versiones están en `VERSIONES.txt`. Para publicar una nueva, añade su línea (con `HEAD` como commit) y
súbela: `.github/workflows/corazon-dormido.yml` prueba el juego y crea la Release (nunca como «latest») y
`.github/workflows/corazon-dormido-android.yml` compila la app y le pone el APK y el AAB. Para el juego de navegador
a mano: `python3 corazon-dormido/herramientas/empaquetar.py`.

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

## Android

`android/` es la app: una actividad en Kotlin (`MainActivity.kt`) a pantalla completa y en horizontal que lleva
el juego dentro (servido desde el propio APK con `WebViewAssetLoader`: funciona sin internet y guarda la partida
en el móvil, con copia de seguridad de Android). El botón «atrás» cierra paneles o pausa; al irse al fondo guarda
y para la música; vibra al recibir golpes. `js/app.js` es el puente con el juego (`window.CorazonApp`).

- **Compilar**: lo hace GitHub Actions (`.github/workflows/corazon-dormido-android.yml`) en cada push: compone la
  música y los efectos (`herramientas/musica`), mete el juego en la app (`herramientas/android.py`) y saca el APK
  y el AAB en la Release fija `corazon-dormido-android` (nunca «latest»). En local: `python3
  corazon-dormido/herramientas/android.py` y `gradle assembleRelease` dentro de `android/` (con el SDK de Android).
- **El icono** sale del modelo del Corazón: `node corazon-dormido/herramientas/icono.cjs` (escribe los mipmap y
  `android/tienda/icono-512.png` para la ficha de la Play Store).
- **Firmar para la Play Store**: el APK de prueba va firmado con `android/app/prueba.keystore` (una llave pública,
  como la de debug: no vale para la tienda). Para el AAB de la tienda hay que crear la llave de subida una vez y
  guardarla en los secretos del repo (Settings → Secrets and variables → Actions):

  ```bash
  keytool -genkeypair -keystore subida.jks -alias corazon -keyalg RSA -keysize 4096 -validity 10000
  ```

  ```bash
  base64 -w0 subida.jks
  ```

  Secretos: `CD_LLAVE_B64` (lo que saca el base64), `CD_LLAVE_CLAVE` (la contraseña del almacén), `CD_ALIAS`
  (`corazon`) y `CD_ALIAS_CLAVE` (la contraseña de la llave). Con ellos, el workflow firma el AAB con esa llave y
  ese AAB es el que se sube a la Play Console (con «Firma de apps de Google Play»). Guarda `subida.jks` y sus
  contraseñas fuera del repo: sin ellas no se pueden subir actualizaciones.
- Paquete: `com.corazondormido.juego` (se puede cambiar antes de la primera subida a la tienda, no después).

## Lo nuevo de la 0.7.0

- **Mundo de 2048×2048** con carga por pasos y guardado por trozos en IndexedDB (solo lo que cambias); las
  partidas de la 0.6 y de la fase 1 pasan solas al mundo nuevo.
- **La Selva llega mucho más lejos**, en tres bandas: la Selva Esmeralda ampliada, la **Ciénaga de la Podre** (la
  Caja Torácica de Ásor, palafitos, santuarios, el Pozo) y la **Ciudad Velada** (nueve barrios, la Gran
  Biblioteca, el Templo de las Placas, la torre del Archivero y la Cripta de los Veladores).
- **Puzles**: cámaras de braseros de runas, el templo de bloques y placas, las palancas de la Cripta y seis
  piedras de paso para viajar.
- **Dos jefes**: Grul, el Sapo Hueco, y el Centinela, el Que No Duerme. **Nueve criaturas** nuevas y dos metales
  (médula y plata velada) con sus herramientas, armas, armaduras y mesas.
- **Historia y misiones**: 48 tablillas que cuentan la historia por zonas, el Archivero y Muda con diálogo y
  nueve encargos, DIARIO y BESTIARIO (con los jefes) en la mochila.
- **Criaturas con cabeza**: no saben dónde estás hasta que te ven o te oyen, deambulan, te rastrean por el olor,
  te pierden, y solo unas pocas atacan a la vez (las demás te rodean esperando hueco).
- **Animación**: rebote y balanceo al andar, respiración, anticipación antes de atacar, retroceso, caída al
  morir, salir del suelo al aparecer; polvo al pisar.
- **Banda sonora original** (8 temas: título, interior, Selva, Ciénaga, Ciudad, Cripta y dos de jefe) y 50
  efectos de sonido, todo sintetizado por `herramientas/musica` (ver «La música»).
- El Corazón es ahora un corazón anatómico, y es el icono de la app.

## La música

`herramientas/musica` es un sintetizador propio en Python (numpy, scipy y soundfile): cuerdas, coro con
formantes, piano, arpa (Karplus-Strong), campanas, celesta, marimba, flauta, violonchelo, metales y percusión,
con reverb de cueva y masterizado. `temas.py` son las partituras (melodías en texto: `"A4:1 D5:1 F5:1.5 E5:0.5"`) y
`efectos.py` los efectos. Cada tema es un bucle perfecto (la cola de la reverb vuelve al principio).

```bash
python3 corazon-dormido/herramientas/musica/componer.py corazon-dormido/musica
```

```bash
python3 corazon-dormido/herramientas/musica/efectos.py corazon-dormido/sonidos
```

Los .ogg no van al repo: los compone el workflow de Android. `js/musica.js` elige el tema por zona (y en las
peleas de jefe) con fundidos; `js/sonido.js` carga los efectos y, si no están (el .html suelto), los sintetiza.

## Lo nuevo de la 0.6.0 (el arte)

- **Escultor 3D** (`js/escultor.js`): cada cosa se modela con figuras (esferas, cajas, cilindros, cápsulas,
  toros, restas) y se hornea a pixel art con la cámara del juego: tapa y frente con volumen, luz, oclusión en
  los rincones, brillo de los metales, líneas donde una pieza tapa a otra, contorno de color, la capa que brilla
  sola, la sombra en el suelo con su forma y un mapa de normales.
- **Luz de lado**: los modelos reaccionan a las antorchas: el lado que mira a la luz se aclara.
- **Tu Despierto**: al empezar eliges piel, color de pelo, peinado y ropa (`js/personaje.js`). El personaje es un
  muñeco 3D con andar, golpe y reposo en tres direcciones; las seis armaduras (cobre, estaño, hierro, larva,
  carmesí y musgo) se ven puestas, cada casco con su forma.
- **Unos 130 objetos rehechos** (`js/modelos.js`, `js/modelos2.js`): mesas, yunques, horno, olla, cofres, cama,
  puertas, luces, muebles, árboles, rocas con mena, plantas, cultivos, pozo, tienda, ídolos, trono y el Corazón.
- **Criaturas y jefes de fantasía oscura** (`js/criaturas3d.js`, `js/jefes3d.js`): ojos que son puntos de luz y
  la Podre magenta que brilla en la oscuridad. Bestiario nuevo: Baba carroñera, Portador, Gusano roedor, Gruto
  lancero, vidente, mole, Sembrador, Acechador, Fauce, Lápida, Aguijonera, Brazo de la Podre…
- **Los cinco jefes**, con nombre, título y una frase al despertar: Murdo el Rey Ciego, Vhessa Madre de la
  Colmena, Ozmar el Último Velador, Hiedra la Flor de Sangre y Tamar el Ojo del Cielo. Las estatuas y los
  altares son sus modelos en piedra.
- **Suelos** sin ruido de puntos y con bordes orgánicos entre suelos (`js/suelos2.js`); **avisos de ataque**
  nuevos: anillo que late con un disco que se llena, grietas que se abren antes de las raíces y círculos de runas.
- **Arranque rápido**: en el juego de un solo archivo el arte horneado se guarda en el navegador (la segunda vez
  carga en medio segundo).

## Lo que hay en la fase 2 (la Selva Esmeralda)

- **El mundo crece a 640×640 casillas.** Detrás de la Gran Muralla, el anillo exterior se parte en tres sectores
  (como en Core Keeper): la **Selva Esmeralda** ya está; el Mar Hundido y el Desierto del Origen son roca dormida
  hasta sus fases.
- **Abrir la Muralla**: con las tres estatuas, el Corazón despierta; habla con él y pon la mano en la Muralla (el
  botón TOCAR sale al lado). Se derrumba entera. Un camino de grava lleva hasta ella en la dirección de la Selva.
- **La Selva**: más abierta y con luz que baja del cielo (claros con sol, rayos, luciérnagas, hojas que caen).
  Árboles grandes (y árboles de luna que brillan), helechos, flores del sol, avena silvestre, hongos explosivos,
  panales de avispas, rocas grandes con mena de cada metal y lagos con peces nuevos.
  - **Praderas de flores**, **la Mazmorra del Moho** (salas de muros que no se rompen, tentáculos y el gran
    cofre del centro), **el templo del cielo**, **el jardín de Hiedra Viva**, **aldeas de grutos**,
    **campamentos** y **ruinas con tablillas** que cuentan la historia.
- **Metal carmesí** (paredes de hierba, pico de hierro): pico con poder 80, espada, maza, arco y flechas, caña,
  azada y regadera que cubren varias casillas, armadura, anillo, mesa, yunque y farol. Armadura de caparazón
  musgoso, lanza de espino y flechas de aguijón que envenenan.
- **8 criaturas nuevas**: babosa venenosa (deja charcos), gruto jardinero (semillas que revientan en
  enredaderas), gruto cazador (arco), planta trampa, avispa esmeralda (se lanza en línea recta), escarabajo de
  musgo (embiste y se aturde si choca), tentáculo y larva del moho.
- **Dos titanes**:
  - **Hiedra Viva**: filas de raíces, esporas venenosas, el látigo (después se queda expuesta) y brotes de
    plantas trampa.
  - **Tamar, Titán del Cielo**: baja al templo cuando pones la Ofrenda del cielo en su altar. Abanicos de plumas,
    vuelo (su sombra te sigue) y picado (se queda aturdido), rayos y tornados.
  - Sus gemas van a dos altares nuevos junto al Corazón.
- **Mercaderes**: haz una casa cerrada (paredes y puertas, 64 casillas o menos) con una cama y pon un ídolo. Llega
  Barbas, Marina la pescadora o el Encapuchado. Tienda con COMPRAR y VENDER; se paga con monedas antiguas.
- **Mascotas**: cuatro huevos (Topito, Babosita, Luci y Mohito) que nacen en la incubadora; hueco de mascota en el
  equipo; suben de nivel.
- **Bombas** que rompen paredes, **piedra de regreso** para volver a tu cama y **muebles de selva**.
- **Habilidades nuevas**: Explosivos y Doma. Cebo para pescar antes.
- **182 objetos y 99 recetas** (antes 98 y 58), 16 criaturas, 5 guardianes y 26 objetivos.
- **Tu partida de la fase 1 pasa al mundo nuevo**: personaje, mochila, equipo, habilidades, jefes vencidos y
  estatuas; lo de tus cofres y los muebles que tenías puestos llega en cofres junto al Corazón.

## Lo que hay del anillo interior (fase 1)

- **Mundo distinto en cada partida**, en anillos alrededor del Corazón.
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
js/arte3.js        arte de la fase 2: suelos y paredes de la Selva, iconos (lo que está de pie se rehízo en 3D)
js/escultor.js     el escultor: modelos 3D con figuras horneados a pixel art (luz, oclusión, contorno, normales, sombra)
js/modelos.js      modelos 3D de muebles, estaciones y luces
js/modelos2.js     modelos 3D de la naturaleza y de los objetos especiales (columnas, pozo, tienda, ídolos, Corazón)
js/personaje.js    el personaje: muñeco 3D articulado con aspecto y armaduras, horneado en marcha
js/criaturas3d.js  las criaturas, mascotas y mercaderes en 3D (de perfil, estilo de LORE.md)
js/jefes3d.js      los cinco jefes en 3D y sus estatuas y altares de piedra
js/suelos2.js      suelos nuevos, bordes entre suelos y avisos de ataque
js/gl.js           motor 3D con WebGL2: trozos del mundo, cámara, luz, sprites de pie, sombras, brillos, halos, rayos
js/fuente.js       letra de 3×5 píxeles con tildes y Ñ
js/sonido.js       sonidos con WebAudio
js/datos.js        números del juego: suelos, paredes, menas, objetos, recetas, criaturas, jefes, cultivos, peces
js/datos2.js       lo de la fase 2: carmesí, madera de selva, objetos, recetas, criaturas, titanes, mercaderes, tablillas, objetivos
js/mundo.js        generación del anillo interior, luz, mapa y guardado
js/selva.js        el anillo exterior: los tres sectores y la Selva Esmeralda con sus subzonas
js/entidades.js    jugador, inventario, equipo, habilidades, herramientas, botín, partículas
js/criaturas.js    criaturas: aparecer, mapa de distancias, conductas, proyectiles y avisos en el suelo
js/jefes.js        los tres guardianes, las estatuas y el despertar del Corazón
js/sistemas.js     crear (con cofres cerca), cofres, olla, cultivos y pesca
js/fauna.js        conductas de las criaturas de la Selva
js/titanes.js      Hiedra Viva y Tamar
js/fase2.js        Muralla, altares, tablillas, incubadora, mascotas, mercaderes y tienda, bombas, piedra de regreso
js/datos3.js       lo de la 0.7: suelos, paredes, menas, médula y plata, criaturas, Grul y el Centinela
js/guardado.js     guardado por trozos de 64×64 en IndexedDB (solo los trozos tocados)
js/cienaga.js      la Ciénaga de la Podre       js/ciudad.js  la Ciudad Velada y la Cripta
js/puzles.js       braseros de runas, placas y bloques, palancas, piedras de paso
js/bestias.js      conductas de las criaturas de las bandas, Grul y el Centinela
js/misiones.js     tablillas de historia, el Archivero y Muda, encargos, diario y bestiario
js/musica.js       la banda sonora por zonas      js/app.js     el puente con la app de Android
js/arte4.js, js/modelos3.js, js/criaturas3d2.js   el arte de la 0.7
js/ui.js           controles táctiles y de teclado, HUD y paneles
js/principal.js    bucle, cámara, dibujo del mundo y arranque
herramientas/probar.cjs         prueba rápida en un móvil simulado (capturas + errores + guardar/cargar)
herramientas/probar-todo.cjs    prueba a fondo: regiones, jefes, criaturas, paneles, base, pesca, FPS y dibujos que falten
herramientas/simular-jefes.cjs  un bot pelea contra cada guardián con el equipo de su momento (equilibrio)
herramientas/probar-migracion.cjs  una partida de la fase 1 pasa bien al mundo de la fase 2
herramientas/empaquetar.py      saca el .html de un solo archivo y el .zip de una versión (para las Releases)
herramientas/galeria.cjs        capturas en el juego de cada zona, jefe, base y criaturas (para revisar el arte)
herramientas/taller.cjs         el atlas ampliado con un filtro (taller.html): para modelar y revisar dibujos
herramientas/musica/            la banda sonora y los efectos (componer.py, efectos.py, temas.py, motor.py)
herramientas/android.py         mete el juego, la música y los efectos en la app de Android
herramientas/icono.cjs          el icono de la app (y el de la Play Store) desde el modelo del Corazón
android/           la app de Android (Kotlin, Gradle); android/tienda: imágenes para la ficha de la Play Store
LORE.md            la biblia del mundo: historia, dirección de arte, bestiario y jefes
CONTINUAR.md       dónde va el trabajo y qué falta
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

Lo que está de pie en el mundo se modela en 3D con `js/escultor.js` (ver «Lo nuevo de la 0.6.0»). Para revisar un
modelo: `node corazon-dormido/herramientas/taller.cjs /tmp/t.png '^nombre' 6` saca el dibujo ampliado.
Los suelos, las paredes, los iconos y la interfaz siguen siendo 2D en `js/arte.js`. Usa la misma idea que el skill **pxl**, copiado en `.claude/skills/pxl-pixel-art`
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

```bash
node corazon-dormido/herramientas/probar-migracion.cjs /tmp/capturas
```

```bash
node corazon-dormido/herramientas/simular-ia.cjs
```

`probar-todo` tiene que acabar en «guardar y cargar: bien», «no falta ningún dibujo» y «sin errores». Mira las
capturas antes de dar algo por bueno; también recorre la Selva, abre la Muralla, construye una casa (llega el
mercader, compra y vende), incuba, pone la mascota, tira una bomba, usa la piedra de regreso y los altares.
`simular-jefes` dice si el bot gana y cuánta comida gasta: con el equipo de cada momento (carmesí para los
titanes) y 10 platos, el bot (que esquiva peor que una persona) gana la mayoría de las peleas.

## Cómo seguir (por orden)

Las fases están en `DISEÑO.md`:

0. **Publicar en la Play Store**: la llave de subida (ver «Android»), la ficha (icono en `android/tienda`,
   capturas, descripción, clasificación de edad) y la prueba interna antes de producción.
1. **Fase 3: el Mar Hundido** (el segundo sector del anillo exterior): barcos, peces de mar y su titán.
2. **Fase 4: el Desierto del Origen**: vagonetas, lava y dos jefes.
3. **Fase 5: la Frontera Cristalina** y el final.
4. **Fase 6**: app para Android e iOS (Capacitor), guardado en la nube y cooperativo.

**Para ganar dinero** (como Core Keeper, que es de pago): la demo gratis hasta el primer jefe y el juego
completo con un pago único. Si se hace gratis, que los anuncios sean opcionales y den premio, por ejemplo revivir
con todo o doblar el botín de un cofre. Nada de pagar para ganar.
