# Prompt de continuación — NPC de escenas, TF Client 1.3.101 (SIN PUBLICAR)

> Pega esto a la siguiente IA tal cual. Escrito el 21 oct 2026, cuando el dueño pidió parar: «para, que otra IA lo
> siga». Todo lo demás está en `tf-client/NPCS.md` (cómo funciona) y en el CHECKPOINT de `HANDOFF.md`.

---

Eres la IA que sigue los **NPC de escenas** de TF Client en el repo `modelqdeluxe-ops/ss`, rama
`claude/peaceful-gauss-4nzddg` (o la que te asigne la sesión: si es otra, trae esta). **La 1.3.101 no está publicada**:
no hay PR a `main` ni Release `tfclient-v1.3.101`. `mod_version` y `TFClient.VERSION` ya dicen 1.3.101. En `main` está
la 1.3.100 (las crates).

## Reglas del dueño (no se rompen)

- Respóndele **en español**. **No uses subagentes.** Avísale de lo que haces (se enfada si estás callado).
- Compila **siempre con GitHub Actions** (push a la rama; `tf-client.yml`). Nunca `./gradlew` en el contenedor.
- **No subas jars al repo.** Publicar = Release `tfclient-v<versión>` (la crea el workflow al fusionar en `main`; no la
  marques «latest»; comprueba que existe antes de dar el enlace).
- **Una sola prueba en el juego por cambio** (`[prueba-jefes=npcs]` en el commit, ~10 min con la compilación).
- Las capturas, **mídelas** (PIL) antes de decir que algo está bien.
- **Los packs nunca en claro en el repo** (es público). Ver «Los packs» abajo.

## Qué pidió el dueño

1. «Ve convirtiendo todos estos packs al TF Client, son NPC de escenas para ambientar lugares… los quiero todos
   correctamente con sus animaciones correctas, sin errores de textura, animación… crea un ítem tipo panel de control
   para colocarlos, con posición en tiempo real, rotación y todo eso… cada pack tendrá su nombre que ya trae… el comando
   para el ítem /tf npc y me entrega el ítem, ojo solo al que ejecuta el comando».
2. «Ya puedes meterlos (en el jar), es la última actualización, la 1.3.100» → esta es la 1.3.101.
3. «Para, que otra IA lo siga, guarda todos los packs para no tener que volvérselos a adjuntar.»

## Qué hay hecho (en la rama, commits `3073e629` y `6771c9c3`)

- Los 13 packs convertidos y **en el jar** (`src/main/resources/assets/tfclient/npcs/`, `textures/npcs/`,
  `sounds/npcs/`): 130 NPC + 4 marcadores. Auditor **0 problemas** en los 13; `python3 tf-client/tools/bosses/verificar.py`
  «todo bien» (ya incluye «auditor: 0 problemas en los NPC de escenas»). Compila en GitHub Actions.
- Motor (fiel a MythicMobs/ModelEngine): variables anidadas y con «|por defecto», globales con save=true guardadas con el
  mundo (`skills/SkillSaved`), `@pin`/`distanceFromPin`, `followPath`, `teleportTo{location}` a los objetivos, `light`,
  `meleeHit`, ~onLoad de los NPC que vuelven del disco, changepart/remapmodel sin modelo no hacen nada.
- Entidad `tfclient:npc` (BossEntity en modo NPC: se guarda con el mundo). Panel `npcs/` (`TFNpcs`, `NpcNet`,
  `NpcCatalog`, `NpcPanelItem`, `client/NpcPanelScreen`) y `/tf npc` (solo a quien lo ejecuta).
- **Pieles**: casi todos los NPC del pack traen la piel de plantilla amarilla con una «S»; el panel les pone una de las
  21 pieles medievales que traen los packs (a los guardias, las de yelmo y armadura) — NPCS.md §3.
- Vista previa sin abrir el juego: `python3 tf-client/tools/npcs/hoja_npcs.py <carpeta> [pack…]`.

## La prueba en el juego del commit `6771c9c3` (informe en la rama `pruebas-jefes`)

Bajarla: `tf-client/tools/bosses/esperar_prueba.sh 6771c9c3 <carpeta>` (en segundo plano). Lo que salió:

- Bien: catálogo (13 packs, 134 NPC), `/tf npc` da 1 panel; casi todos los NPC con su modelo, su piel y su animación de
  escena (mercado, pescadores, combate, aldeanos 1-3, caminantes, guardias, mineros, herreros); el **vendedor del
  mercado anda entre sus dos pins** (4,6/9,3 → … → a 1,1 del pin 2); el panel abierto en la pestaña Editar.
- Fallos de la PRUEBA (no del mod), en `BossTest.npcsStep`:
  - la fila de la taberna y el guardia de la prueba «NO SALE»: se colocaban en el mismo tick en que el jugador llegaba
    a un chunk sin cargar → teletransportar primero y colocar 3-5 ticks después;
  - granjeros y leñadores «NO ESTÁ» en su captura: su chunk se descargó al irse la cámara y al volver se recargaron
    del disco como entidades nuevas; la prueba miraba la vieja → antes de cada captura, buscar el NPC cerca de su sitio
    (por `mobName()`), y esperar ~60 ticks (no 30) tras mover la cámara;
  - el minero no muerto «NO ESTÁ»: es un ZOMBIE sin `PreventSunburn` y se quema al sol (igual en MythicMobs).
- Fallos de verdad que hay que arreglar:
  1. **El caminante no anda su ruta.** Las skills de ruta que genera `build_npcs.route_skills` usan `@target`, y en
     MythicMobs `@target` es el objetivo de combate, no el NPC al que se lanza la skill: cámbialas a `@TargetedTarget`
     (el heredado; el motor lo tiene: `SkillTargets` «targetedtarget») y reconvierte (`build_npcs.py <packs>` y
     `--al-jar`).
  2. **El panel se sale por abajo en pantallas pequeñas** (la prueba va a 800×450 con escala 2: la pestaña Editar no
     cabe; «Duplicar/Borrar» no se ven y el texto de teclas se monta encima de los cosméticos). Hay que darle
     desplazamiento con la rueda (o compactar filas) a `NpcPanelScreen`: contenido con `scroll`, widgets fuera de la zona
     `visible = false`, textos con `g.enableScissor`.
  3. **El sedal de los pescadores sube al cielo** (captura `00_npcs_npcs_fishermen_33.jpg` y las líneas negras que se ven
     desde el mercado). Medido con las mismas cuentas del mod (vfx_bb.pose): en la animación «fishing», la caña (hueso
     `tool`, x=-57,5 con el brazo a x≈-55) queda hacia arriba y atrás y el sedal (`bone2`, escala z 2,7, x≈390°=30°)
     sube ~7 bloques con el pez al final. En «boat» (caña +25) sale bien. Hay que averiguar si es así en el pack
     (Blockbench/ModelEngine) o si es un fallo del conversor con rotaciones > 360° o el orden de los ejes; si es del
     pack, apuntarlo como hueco del pack con su porqué.
  4. Comprobar que, tras recargar el chunk, los granjeros vuelven a su animación (en la captura tenían todas las
     herramientas a la vista, que es la pose sin animación; puede ser que la captura llegó antes de su
     `defaultstate{...;delay=10} ~onLoad`). Con la prueba arreglada se ve.
- Después: **una** prueba `[prueba-jefes=npcs]`, leer informe y capturas, y publicar la 1.3.101 (CHECKPOINT de
  `HANDOFF.md`, PR a `main` con el cuerpo acabado en «🤖 Generated with [Claude Code](https://claude.com/claude-code)» y
  el enlace de tu sesión, fusión squash, comprobar la Release `tfclient-v1.3.101`, rehacer la rama desde `main` y dar el
  enlace al dueño).

## Los packs (el dueño quiere que no haya que volver a adjuntarlos)

- Los 13 zips de NPC (y `EndMobsVol1.zip`, la fauna del Void) **no están en el repo**. Se intentó guardarlos cifrados
  en `packs/npcs-13.tar.enc` (AES-256, como `packs/jefes-17.tar.enc`) pero el permiso de la sesión lo bloqueó (subir
  archivos comprados a un repo público). Hay que preguntarle al dueño cómo quiere guardarlos (cifrados en este repo con
  su contraseña, en un repo privado, o que los vuelva a adjuntar). Si dice que sí al cifrado: mismo comando que en
  `packs/README.md`, con un `README.md` dentro que diga qué zip es cada pack (los nombres están en la tabla de NPCS.md),
  y la contraseña nunca en el repo.
- Para convertir hacen falta descomprimidos, cada zip en su carpeta, y datos no fiables: Python con `-I`.
