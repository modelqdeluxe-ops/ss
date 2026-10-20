# Prompt de continuación — TF Crates 1.3.99: el Santuario de las Hadas como la imagen del dueño (SIN PUBLICAR)

> Pega esto a la siguiente IA tal cual. Escrito el 20 oct 2026, cuando el dueño pidió: «para, otra IA lo seguirá, deja
> checkpoint y prompt» y «deja ambas comparaciones para que la otra IA siga replicando lo mismo exactamente».

---

Eres la IA que sigue el trabajo de **TF Client 1.3.99** en `modelqdeluxe-ops/ss`, rama `claude/vigilant-goodall-wjpq1m`
(o la que te asigne la sesión: si es otra, trae esta rama con `git fetch origin claude/vigilant-goodall-wjpq1m` y ponte
encima). **Nada de la 1.3.99 está publicado**: no hay PR a `main` ni Release `tfclient-v1.3.99`. `mod_version` y
`TFClient.VERSION` ya están en 1.3.99.

## Reglas del dueño (no se rompen)

- Respóndele **en español** y **avísale de lo que haces** (se enfada si estás callado).
- **No uses subagentes.** Compila **solo con GitHub Actions** (push; `tf-client.yml`). **No subas jars.**
- **Una sola prueba en el juego por cambio** (`[prueba-jefes=crates]` en el commit; ~4 min). Antes, la vista previa.
- **No le digas que coincide si no lo has medido.** Se enfadó varias veces: «no veo idéntica a mi foto», «¿seguro que
  coinciden?», «el color para nada coincide», «quiero precisión en los colores», «no veo mejoría en saturación,
  contraste», «esa mierda de cascada cortada». Mide siempre con `tools/crates/comparar.py` (abajo) y enséñale las dos
  imágenes, una encima de otra.
- Cuando mande recortes o capturas, **mídelos** (PIL) y busca qué es exactamente (con la profundidad, si hace falta).
- **La canción** («La petite fille de la mer») va en un resource pack aparte (`TF-Santuario-musica.zip`, ya se le dio;
  sustituye `tfclient:crates.musica`). No la subas al repo.
- Commits con `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` y la línea `Claude-Session:` de tu sesión.

## Lo que quiere el dueño

Su imagen: **`tf-client/docs/crates/imagen-del-dueno.webp`**, «quiero exactamente esto, debes ser capaz de renderizarlo
bien tal como quiero: colores, iluminación, shaders». La escena ya está construida como ella (CRATES.md §2): templo en
ruinas de piedra con musgo y hiedra, vigas de roble oscuro con flores de esporas y faroles con cadena, el sol con rayos
entrando por el hueco, la cascada alta y la escalera de agua al fondo, las dos cascadas de los lados bajo un dintel, el
estanque turquesa con el fondo de baldosas y la red de luz, el altar con dos amatistas y la crate.

## Las dos comparaciones (hazlas igual)

```
# 1) la vista previa (texturas de Minecraft y los mismos shaders del mod), tick 0 = el encuadre de la imagen
xvfb-run -a python3 -I tf-client/tools/crates/santuario_render.py <carpeta> 0 76 140 258
python3 -I tf-client/tools/crates/comparar.py <carpeta>/render_000.png --lado <carpeta>/lado_vista.png

# 2) el juego: tras la prueba, la captura del tick 12 de la rama pruebas-jefes
tf-client/tools/bosses/esperar_prueba.sh <hash> <carpeta>          (en segundo plano)
python3 -I tf-client/tools/crates/comparar.py <carpeta>/00_crates_crate2_00_llegada.jpg --lado <carpeta>/lado_juego.png
```

`comparar.py` saca, foto → imagen: los colores por tipo (agua, hojas, piedra, faroles y sol, rosa, sombras), saturación,
luz p5/p50/p95, contraste global y local, nitidez y el color de zonas fijas. Las de la última vez están en
`tf-client/docs/crates/comparacion-vista-previa.jpg` y `comparacion-juego.jpg` (arriba la foto).

**Ojo: el juego y la vista previa no salen iguales.** El juego sale más saturado y más oscuro. Lo que manda es **el juego**
(lo que ve el dueño); la vista previa sirve para iterar rápido. Últimas medidas (prueba `5ba276f`, antes del último
cambio, foto → juego): saturación 0,621 → 0,708 (+14 %), luz media 0,318 → 0,282 (−11 %), luz alta 0,832 → 0,780,
contraste local 0,080 → 0,092 (+14 %), hojas (43,80,39) → (32,73,26) y 30,8 % de la imagen (foto 23,4 %), faroles
(189,145,63) → (219,165,42), rosa (182,74,149) → (201,73,87), piedra (76,81,80) → (88,99,92); el agua casi igual
(27,168,182) → (26,168,195).

## Lo último que se hizo (en el último commit, SIN probar en el juego)

Medido en la prueba `5ba276f`: en el juego salían **dos «tablones» grises de borde duro en diagonal** (eran los rayos en
abanico y los que bajan del hueco, hechos con bandas de geometría: en la vista previa se ven suaves y en el juego no, sin
saber aún por qué; los halos de los faroles también salían como discos con aro). Se cambió:

- los rayos de sol ya no son geometría: los hace el shader final, píxel a píxel (`crate_post.fsh`, `godRays`, con el
  uniforme nuevo `Time`; `ScenePost.finish(..., time)`). Se quitaron de `CinematicFx.sunShafts` y de la vista previa;
- para acercar el juego a la foto: saturación del shader final 1,3 → 1,12, exposición 1,45 → 1,65
  (`CrateCinematicScreen`, y `EXPOSURE` en la vista previa), claridad 0,42 → 0,30.

Con eso la vista previa (tick 0) da: saturación 0,599 (foto 0,621), luz media 0,322 (0,318), contraste local 0,083
(0,080). **Falta ver cómo sale en el juego.**

## Lo que tienes que hacer (en este orden)

1. Mira la vista previa y compárala (paso 1 de arriba). Luego **una** prueba en el juego (`[prueba-jefes=crates]`) y
   compárala (paso 2). Comprueba en las capturas que ya no hay tablones grises ni discos con aro, y que los rayos salen
   suaves desde el sol como en la foto.
2. Ajusta lo que siga lejos **midiendo en el juego**: saturación y luz media (en `crate_post.fsh`: la saturación
   `mix(vec3(l), col, 1.12 + …)`, la exposición en `CrateCinematicScreen`), las hojas (demasiadas y oscuras:
   `SceneLevel.FOLIAGE`/`GRASS` y la luz `AMB` de `SanctuaryScene.light`, la misma en `make_light` de la vista previa),
   los faroles (más amarillos y brillantes que en la foto), las flores (más rojas en el juego: en la foto son magenta).
   Cada número que cambies en el Java, cámbialo también en la vista previa.
3. Cuando el dueño diga que está bien: **publicar la 1.3.99** (CLAUDE.md: CHECKPOINT de `HANDOFF.md`, CRATES.md, PR a
   `main`, squash, espera la Release `tfclient-v1.3.99`, compruébala, rehaz la rama). Dale el enlace y recuérdale
   **quitar `Fantastic Crates 8.jar`** del servidor y de los clientes.

## Qué hay y dónde (lee `tf-client/CRATES.md` §2 antes de tocar)

- La escena: `tools/crates/santuario.py` (vuelve a ejecutarlo si la cambias); `cielo_visible.py` tiene que dar 0 %.
- La luz: `SanctuaryScene.light(x, y, z, nx, ny, nz)` = `make_light` de `santuario_render.py` (`AMB`, `BEAM_C`,
  `SKY_C`, `POOL_C`, `CROWN_C`).
- Los shaders: `assets/tfclient/shaders/core/crate_water.fsh` (agua y cascadas), `crate_post.fsh` (imagen final),
  `crate_bloom.fsh`, `crate_sky.fsh`.
- Lo que se mueve: `CinematicFx` = `effects()` de la vista previa.
- Los errores que ya se arreglaron y no hay que volver a meter están en CRATES.md §2 («Lo que se encontró por el camino»).

Preguntas pendientes del dueño, de antes (no las fuerces): los cascos de los otros 7 sets de crates; modelos o skins de
los caballeros; el PR en borrador #137.
