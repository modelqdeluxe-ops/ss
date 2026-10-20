# Prompt de continuación — TF Crates 1.3.99 (la cinemática del Santuario de las Hadas)

> Pega esto a la siguiente IA tal cual. Todo lo que necesita está aquí, en `tf-client/CRATES.md` y en el CHECKPOINT de
> `HANDOFF.md`. Escrito el 20 oct 2026, cuando el dueño pidió parar: «para, que otra IA lo haga, pushea, guarda
> checkpoint y contexto en un prompt de continuación».

---

Eres la IA que sigue el trabajo de **TF Client 1.3.99** en el repo `modelqdeluxe-ops/ss`, rama
`claude/peaceful-gauss-4nzddg` (o la que te asigne la sesión: si es otra, trae esta rama). **Nada de la 1.3.99 está
publicado**: no hay PR a `main` ni Release `tfclient-v1.3.99`. `mod_version` y `TFClient.VERSION` ya están en 1.3.99.

## Reglas del dueño (no se rompen)

- Respóndele **en español**. **No uses subagentes** (Agent / Workflow).
- Compila **siempre con GitHub Actions** (push a la rama; `tf-client.yml`). Nunca `./gradlew` en el contenedor.
- **No subas jars al repo.** Publicar = Release `tfclient-v<versión>` (la crea el workflow al fusionar en `main`; no la
  marques «latest»; comprueba que existe antes de dar el enlace). Enlace fijo:
  https://github.com/modelqdeluxe-ops/ss/releases/download/tfclient/tfclient.jar
- **Una sola prueba en el juego por cambio** (`[prueba-jefes=crates]` en el commit; tarda ~4 min). El dueño se enfadó
  porque se hicieron varias. Antes de gastarla, mira la escena con la vista previa (abajo). **Avísale de lo que haces:**
  se enfadó cuando la IA estuvo callada mientras esperaba.
- Cuando mande capturas, **mídelas** (recorta, amplía, colores con PIL) antes de decir que algo está arreglado.
- **La música**: la canción que eligió el dueño va en un resource pack aparte que ya tiene (sustituye
  `tfclient:crates.musica`). No la subas al repo y **no le vuelvas a sacar el tema**.
- Commits terminados en `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` y la línea `Claude-Session:` de tu
  sesión; nada de nombres de modelo en commits ni PRs.

## Qué hay hecho (ya en la rama)

- **Fantastic Crates dentro de TF Client** (`/tf crates`), funcionando en el juego (`tf-client/CRATES.md` §1).
- **La cinemática nueva** al abrir una crate (`tf-client/CRATES.md` §2): una escena 3D en vivo de 18 s que empieza junto
  a la crate. Ahora es **un pantano de hadas de noche**:
  - estanque de agua con **shader propio** (`crate_water`) y un pedestal de piedra con musgo con la crate;
  - **un haz de luz dorada** que baja sobre el altar;
  - una cascada y dos fuentes de agua que cae;
  - bosque cerrado de manglares y robles oscuros, con lianas, bayas luminosas, faroles, charcas, hadas y luciérnagas;
  - postproceso propio (`ScenePost` + `shaders/core/crate_*`): resplandor, profundidad de campo, colores vivos y viñeta.
- La llave, la apertura, la ruleta en anillo y el premio, a sus tiempos (`CrateCinematicTiming`: 360 ticks, premio a
  los 258).
- Las piezas:
  - `tools/crates/santuario.py`: genera la escena; vuelve a ejecutarlo si la cambias.
  - `crates/client/scene/{SanctuaryScene, CinematicFx, ScenePost, SceneLevel}`.
  - `crates/client/screen/CrateCinematicScreen`.
  - `tools/crates/sonidos_crates.py`: ambiente y música.
- **La vista previa con texturas de verdad**: `xvfb-run -a python3 -I tools/crates/santuario_render.py <carpeta> 0 76 140 258`
  (unos 25 s).
  - Dibuja con los modelos y texturas de Minecraft 1.20.1; baja el jar del cliente a `~/.cache/tfclient/` la primera
    vez.
  - Usa la misma luz que el mod, el agua con su shader, y los mismos `crate_sky`, `crate_bloom` y `crate_post`.
  - Hay dos interruptores para depurar: `SIN_FX=1` (sin la capa de luz que suma) y `SIN_AGUA=1`.
  - Si cambias colores, luz, niebla o fuerzas del resplandor en el Java, cámbialos también arriba de este script.
  - `python3 -I tools/crates/cielo_visible.py` comprueba que no se vea cielo ni horizonte; tiene que dar ~0 %.
- La última prueba en el juego se lanzó con el commit `23910313` y no se llegó a leer. Su informe está en la rama
  `pruebas-jefes`: `tf-client/tools/bosses/esperar_prueba.sh 23910313 <carpeta>`, en segundo plano. Léelo **antes** de
  lanzar otra: sirve para ver si compila (el agua con shader, la profundidad de campo) y cómo se ve de verdad.

## Lo que pidió el dueño en esta sesión (textual, en orden)

1. «haz que la escena aparezca directamente ya cerca de la crate… mejora el shader, pon un shader bonito de la escena,
   el resplandor más bello, las fuentes de agua mejor diseñadas, la estructura mejor diseñada, el fondo… está como en
   una pradera cualquiera» / «esas ruinas más bonitas, mejor trabajadas, todo» / «algo bello, como un bosque de hadas».
2. «¿y dónde está el puto bosque? veo una pradera con cerezos… un vergo de bloom que abusas, y las cascadas no se ven
   naturales».
3. «hazla más azulita el agua tipo tráileres de Minecraft o Minecraft Dungeons, más bonito el haz de luz, el bloom no
   lo exageres tanto pero está bien así… que el haz de la crate no tape los ítems… literalmente quiero una escena más
   oscura de hada pantanosa, que se note más el color… ambiente de hada oscuro, bosque oscuro, el santuario con su haz
   de luz, bonito todo».
4. Al ver la vista previa con texturas planas: «¿ese es tu producto final? sin shaders, sin bloom, sin colores vivos, no
   te dije literalmente las texturas de Minecraft así».
5. **Lo último, lo que falta (con una captura de la cascada con una cuadrícula encima):** «¿qué es esta mierda? y haz
   más azul turquesa y más brillante el agua, que el resplandor también salga del agua porque recuerda que es un lugar
   mágico, no abuses tanto del bloom a que se vea borrosa la crate, ¿y la luz del sol? no veo haz de luz bien definido y
   grandecito iluminando gran parte del altarcito».

## Hecho después (20 oct 2026, sesión siguiente)

El dueño mandó **una imagen** («quiero exactamente esto») y la escena se rehízo entera como ella: el Santuario de las
Hadas (todo en `tf-client/CRATES.md` §2, con cómo se midió la imagen y los colores por zonas). Los puntos 1-4 de abajo
quedaron resueltos así: la cuadrícula era de la vista previa (lo que suma luz escribía profundidad); en el juego todo
salía borroso porque se borraba la profundidad de la escena antes del shader; el agua es turquesa, clara (con el fondo
de baldosas y la red de líneas de luz) y florece; la crate no florece; el haz es una columna con borde marcado y un sol
con rayos. También la canción nueva del dueño (resource pack aparte) y el ambiente de día. Queda: que el dueño lo vea en
su juego y diga qué no coincide aún con su imagen (se mide igual: por zonas, con la vista previa del tick 0).

## Lo que había que hacer (ya hecho; se deja para la historia)

1. **La cuadrícula sobre la cascada.** Son rayitas horizontales y verticales regulares, en la zona del haz; se ve en
   `santuario_render.py` ampliando la cascada en el tick 140.
   - Ya está acotado: **sale de la capa de luz que suma** (efectos `GLOW_ADD`). Con `SIN_FX=1` desaparece; con
     `SIN_AGUA=1` sigue. **No son** los rayos radiales del post: con `RAYS = 0` sigue.
   - Lo más probable es el haz: `CinematicFx.sunShafts`/`band` y su copia `effects()` en `santuario_render.py`, con
     bandas anchas casi de cara a la cámara y muchas capas con poco alfa.
   - Quita los efectos uno a uno en `effects()` hasta encontrarlo. Arréglalo en el Java y en la vista previa a la vez, y
     compruébalo ampliando la imagen.
2. **El agua**: más **turquesa** y más **brillante**, y que **dé resplandor** (es un lugar mágico).
   - Cambia los colores de `shaders/core/crate_water.fsh`: profundo y orilla hacia el turquesa (algo como
     `vec3(0.0, 0.55, 0.65)` y `vec3(0.2, 0.95, 1.0)`).
   - Dale más emisión, para que pase del umbral del resplandor (`Threshold` 0.66 en `ScenePost.pass` y `THRESHOLD` en
     la vista previa).
3. **El resplandor sin emborronar la crate.**
   - La crate se dibuja a plena luz (`sc.tinted(..., 1, 0.98, 0.92)` en `drawCrate`), así que florece entera.
   - Baja `bloomK` en `CrateCinematicScreen` (ahora 0.9 + …) o sube el umbral.
   - Quizá oscurece un poco la crate (tinte ~0.85).
   - Comprueba que la profundidad de campo la deje nítida (`Focus` = la distancia a la crate).
4. **El haz de luz: grande, bien definido, que ilumine gran parte del altar.**
   - El haz está en `CinematicFx.sunShafts`: bandas desde la crate hacia `SUN`, que en `santuario.py` es casi vertical,
     `(-0.16, 0.97, -0.2)`.
   - Hazlo una columna ancha (unos 3 bloques), con borde marcado y núcleo claro, que se vea desde arriba del encuadre
     hasta el pedestal.
   - Agranda y aclara el charco de luz (`flatGlow`) en el pedestal y el agua.
   - Ensancha también la luz del haz en las superficies: `SanctuaryScene.light`, `beam = exp(-off²/5)` → más ancho, y
     su copia en `make_light` de la vista previa.
   - Sigue bajándolo mientras gira la ruleta y en el premio, para que no tape los premios (ya lo hace el factor `ring`
     en `CrateCinematicScreen`).
   - Puede ayudar abrir más el hueco de las copas encima: `clear_view` en `santuario.py`.
5. Mira la vista previa en los ticks 0, 76, 140 y 258 y mídela. Cuando esté bien, **una** prueba en el juego con
   `[prueba-jefes=crates]` en el commit:
   - Lee el informe y las capturas (`crate1_*`, `crate2_*`): brillo medio y fotogramas.
   - Si algo falla al compilar, arréglalo antes (la compilación sale en `tf-client.yml`).
6. **Publicar la 1.3.99**:
   - Apúntalo en el CHECKPOINT de `HANDOFF.md` y en `tf-client/CRATES.md`.
   - PR a `main` (el cuerpo termina con «🤖 Generated with [Claude Code](https://claude.com/claude-code)» y el enlace de
     tu sesión) y fusión squash.
   - Espera la Release `tfclient-v1.3.99` y comprueba que existe.
   - Rehaz la rama: `git fetch origin main && git checkout -B claude/peaceful-gauss-4nzddg origin/main && git push -f -u origin claude/peaceful-gauss-4nzddg`.
   - Dale al dueño el enlace y unas capturas del juego.
7. Recuérdale al dueño que **quite `Fantastic Crates 8.jar`** del servidor y de los clientes: si sigue instalado, TF
   Crates no se registra.

Preguntas que siguen pendientes del dueño, de antes; no las fuerces:
- los cascos de los otros 7 sets de crates;
- modelos o skins de los caballeros;
- el PR en borrador #137.
