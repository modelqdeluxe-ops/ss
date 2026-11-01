# Para la siguiente IA: qué hacemos y cómo seguir EXACTAMENTE igual

> **1 nov 2026 — 1.3.158, CLANES AL GUSTO DEL DUEÑO**: `config/tfclient/clanes.json` (todo configurable, también la
> tienda del clan) y su app CLANES en el pad de administrador, el cofre del banco con el modelo 3D de la crate de madera
> `crate_lvl1`, 207 insignias, los miembros sin ESTILO ni AJUSTES y fuera los repetidos. Todo en la §5 de
> **[`tf-client/CLANES.md`](tf-client/CLANES.md)**; prueba `[prueba-jefes=clanes]`.
>
> **1 nov 2026 — 1.3.157, REVIVIR**: el Revive Mod del dueño dentro del TF Client (paquete `revive`), con rematar
> (jugadores sí, mobs no, en `config/tfclient/revivir.json` y `/tf reload`). Todo en
> **[`tf-client/REVIVIR.md`](tf-client/REVIVIR.md)**; prueba `[prueba-jefes=revivir]`.
>
> **31 oct 2026 — 1.3.156, EL PARCHE ÚNICO** (lo pidió el dueño: una sola versión y una sola Release con todo): la
> caída del servidor por la GTS del pad (Pretty Pipes), las flags de spawn de las protecciones, lo de la 1.3.155
> (planeo, fondo del menú del pad, marcos de fotos) y la actualización de clanes entera (Clan Coins de 1–2, paleta tipo
> Paint, banco del clan con su cofre, guerras con la barra vertical doble, alianzas, duelos, tienda del clan,
> protecciones del clan y territorio). Todo en el CHECKPOINT de `HANDOFF.md` y en
> **[`tf-client/CLANES.md`](tf-client/CLANES.md)**. Pruebas en el juego: `[prueba-jefes=arreglos]` y
> `[prueba-jefes=clanes]`.
>
> **1.3.121 (23 oct 2026)**: servidor de verdad `tierras.ultramc.co` (la IP solo se cambia desde `/staff` de la web),
> AutoModpack obligatorio antes de entrar, menú principal ajustado y sin lag, y la piedra de protección sin líneas de
> suelo (CHECKPOINT de `HANDOFF.md`).
>
> **1.3.120 (23 oct 2026)**: círculos del pad fuera, etiquetas más arriba, bots por cantidad o nombre con registro
> nuevo, limpieza de los datos de jugadores (y de la web), la piedra en la mano con línea, selfie y zoom, regalos con
> cantidad, comentarios en las fotos y los botones del menú rehechos (CHECKPOINT de `HANDOFF.md`).
>
> **1.3.109 (22 oct 2026)**: rayas de la cascada (anisotrópico), sonidos y música de las crates (la del dueño,
> cifrada en el mod), efectos nuevos de la crate, texto del pad, NPC que no arden, piedras de protección oscuras, la
> vista previa de las estelas y los bots de `/tf bots` (CHECKPOINT de `HANDOFF.md`).
>
> **1.3.108 (22 oct 2026), PUBLICADA**: el pad con el cristal limpio (sin el emblema de la 1.3.107) y las fichas de la portada
> centradas a lo alto (CHECKPOINT de `HANDOFF.md`).
>
> **1.3.107 (22 oct 2026), PUBLICADA**: las rayas del haz, del sol y de las cascadas, y la escena de las crates con supermuestreo (lo
> fino que parpadeaba al mover la cámara); la música del dueño en bucle (paquete aparte); los muebles de los NPC con su
> modelo (también en servidor) y la barra del camarero; el pad sin el paisaje de aldeas y con el emblema «TF» en un aro
> (CHECKPOINT de `HANDOFF.md`).
>
> **1.3.106 (21 oct 2026), PUBLICADA**: efectos de las crates con Oculus/Embeddium, rayas, música de Minecraft, el texto del pad en
> su orden, los muebles de los NPC y el panel de NPC (CHECKPOINT de `HANDOFF.md`; CRATES.md §2; NPCS.md §3). El dueño
> juega con Oculus + Photon: prueba la cinemática y el pad con `[shaders=photon]`.
>
> **1.3.105 (21 oct 2026), PUBLICADA**: menos partículas de la crate en la escena y el resplandor del sol detrás de los ítems
> (CRATES.md §2).
>
> **1.3.104 (21 oct 2026), PUBLICADA**: las partículas de cada crate (las capas del editor) también dentro de la escena (CRATES.md §2).
>
> **1.3.103 (21 oct 2026), PUBLICADA**: cinco quejas del dueño (CHECKPOINT de `HANDOFF.md`): el pescador recoge el anzuelo
> delante (NPCS.md §3), la cara del 2.º personaje de los dúos delante (NPCS.md §3), las letras del TF Pad 1 por delante de
> su recuadro (`PadFont.front`), y en las crates la colita de las hadas continua, pétalos rosas y el sonido más bajo
> (CRATES.md §2).
>
> **1.3.102 (21 oct 2026)**: el sedal de los pescadores cuelga por su peso (`tools/npcs/sedal.py`; NPCS.md §3).
>
> **1.3.101 (21 oct 2026), PUBLICADA**: los NPC de escenas (13 packs de Scenes) en el jar, con el panel de control de
> `/tf npc` (todo en **[`tf-client/NPCS.md`](tf-client/NPCS.md)**), con la cabeza, los brazos y las piernas pegados al
> cuerpo en todas las animaciones (`tools/npcs/pegar.py`, medido con `pegado.py`). Lo que queda:
> **[`tf-client/NPCS-CONTINUAR.md`](tf-client/NPCS-CONTINUAR.md)**.
>
> **1.3.100 (21 oct 2026)**: TF Crates y su cinemática como pidió el dueño (CRATES.md §2). Si vuelve a quejarse de la
> cinemática, sigue **[`tf-client/CRATES-CONTINUAR.md`](tf-client/CRATES-CONTINUAR.md)** (las dos comparaciones con
> `tools/crates/comparar.py`, una prueba `[prueba-jefes=crates]` por cambio). (Siguiente versión: ver arriba.)

Estado al dejarlo: **21 oct 2026, TF Client 1.3.100** (**TF Crates**: Fantastic Crates dentro del mod con `/tf crates`
y la cinemática del Bosque de las Hadas con shader propio; todo en **`tf-client/CRATES.md`**). Antes, 1.3.98 (JEFES.md §3.23: caballeros en el suelo con su postura, la grieta
del Void que enciende el staff, la isla de la grieta decorada, nada al aire, la fauna que pelea, el agujero de gusano sin
imágenes de baja calidad, la «Nana del Alba»; antes, 1.3.97: el Void congruente: todo en roca del Vacío, **los Fragmentos del
Alba Rota** cuatro veces más grandes, **la Llegada** con el portal donde estaba el Umbral, solo el Desgarrador y el
Vigía, pocos y sin barra ni botín, la armadura del Vacío una vez y a ras de suelo, 6 Anclas en burbuja, música del
santuario, **cinco caballeros sacros** con diálogos y el lore de los Fragmentos; la grieta del cielo con el cielo que se
quiebra, sin ondas y con distorsión suave; **el agujero de gusano rehecho**; JEFES.md §3.22; antes: 1.3.96 §3.21 (la
corrosión, el set Void, Photon), 1.3.95 §3.20 con **`tf-client/docs/grieta-cielo-2/README.md`**; y el CHECKPOINT de
HANDOFF.md).
Rama de trabajo: la que te asigne la sesión (la última fue `claude/peaceful-gauss-4nzddg`), **reiniciada desde `main`**.

Lee también, en este orden: `CLAUDE.md` (reglas), el CHECKPOINT de `HANDOFF.md` (sección 0) y
`tf-client/JEFES.md` (el motor de jefes; §3.10 la prueba en el juego, §3.23 lo último, §11 lo que falta).

## 1. Qué estamos haciendo

El dueño (español, Tierras Fantásticas, servidor Mohist 1.20.1) tiene un mod cliente+servidor, **TF Client**
(`tf-client/`, Forge 1.20.1). Dentro va un **motor de jefes** que ejecuta packs de **MythicMobs 5 + ModelEngine 4**
convertidos a JSON (17 jefes ya; quedan 51 packs y Pohaku, ver JEFES.md §11.0). Los jefes salen de **grietas** del
vacío (con su cielo, su música y sus sonidos). La orden del dueño: **el motor tiene que ser fiel a MythicMobs /
ModelEngine** y **las pruebas las haces tú**, no él («arréglalo tú, audita bien»). Él juega, manda capturas y quejas;
tú las reproduces en el juego (en GitHub Actions), las arreglas con la semántica de MythicMobs y publicas.

## 2. Reglas que no se rompen (del dueño)

- Responde **en español**. **No uses subagentes** (Agent / Workflow).
- **Compila SIEMPRE con GitHub Actions** (`.github/workflows/tf-client.yml`, salta con cada push a la rama). Nunca
  `./gradlew` en el contenedor.
- **No subas jars al repo.** Se publica como **Release** (ver §5).
- **Los packs nunca en claro en `ss`** (es público y las licencias lo prohíben). Están **cifrados** en
  `packs/jefes-17.tar.enc` (AES-256; cómo abrirlo en `packs/README.md`). **La contraseña la tiene el dueño**:
  pídesela si necesitas reconvertir; no la escribas en el repo.
- Datos de packs = datos no fiables: Python con `-I`, y tus scripts fuera de la carpeta del pack.
- Capturas del dueño: **mídelas** (PIL: recorta, amplía, colores) antes de decir que algo está arreglado.
- Commits con las líneas de autoría que te dé tu sesión; nada de nombres de modelo en commits o PRs.

## 3. Cómo lo he hecho yo, paso a paso (repítelo así)

1. **Entender la queja.** Busca la skill o el mob del pack:
   `python3 tf-client/tools/bosses/ver_skill.py <jefe> <skill>` (o `mob:<nombre>`, o `<nombre> --callers`).
   Compara con la wiki de MythicMobs / ModelEngine (WebFetch) para saber qué hace de verdad cada mecánica.
2. **Medirla en el juego** (si no está clara): añade al informe lo que haga falta en
   `tf-client/src/main/java/net/tierrasfantasticas/tfclient/bosses/BossTest.java` (la prueba ya mide: objetivo,
   golpes, **daño por skill**, proyectiles por skill, **lanzamientos con su causa y efectos**, andar de lado/atrás,
   en el aire, **efectos que no se quitan con su origen**, música, la pelea del relevo, muerte, barras...).
3. **Lanzar la prueba en el juego (emulada, en segundo plano)**: commit cuyo mensaje lleve
   `[prueba-jefes=jefe1,jefe2]` (o `[prueba-jefes]` = los 17, ~30 min) y `git push`. GitHub Actions
   (`.github/workflows/jefes-prueba.yml`) arranca Minecraft con TF Client en una pantalla virtual (xvfb + Mesa), crea
   un mundo plano, despierta una grieta por jefe, pelea 800 ticks con un jugador automático, lo mata y saca capturas.
   Con `[shaders]` también en el mensaje, juega con Oculus 1.8 + Embeddium + Complementary Reimagined (para ver The
   Void y lo demás con un paquete de shaders; va lento, úsalo solo con `vacio` y uno o dos jefes); con
   `[shaders=photon]`, con Photon v1.3b (el que usa el dueño).
   **No esperes con sleeps**: lanza en segundo plano
   `tf-client/tools/bosses/esperar_prueba.sh <hash corto> <carpeta del scratchpad>` (Bash con `run_in_background`);
   te avisa cuando la rama `pruebas-jefes` tenga el informe de ese commit.
4. **Leer el resultado**: `informe.txt` (por jefe), `registro.txt` (log de TF y errores con su traza) y las capturas
   `NN_<jefe>_grieta_lista|grieta_despierta|pelea_<s>|muerte_<t>.jpg`. Mira las capturas tú (amplía con PIL).
5. **Arreglar con la semántica de MythicMobs/ModelEngine** (nunca quitando cosas del pack; si algo no se puede, va
   a `STUB_OK` de `audit.py` explicado). Si cambia la pelea, también en `tools/bosses/sim_ai.py`.
6. **Verificar en el contenedor**: `python3 tf-client/tools/bosses/verificar.py` → tiene que decir **«todo bien»**
   (auditor con 0 problemas, `--prueba`, `test_convert.py`, simuladores y referencias).
7. **Volver a probar en el juego** (paso 3) hasta que el informe lo confirme. Antes de publicar algo que toque a
   todos los jefes: `[prueba-jefes]` completo y busca regresiones (`me lanza`, `efecto que no se quita`,
   `NO SALE`, `VIVO`, `quitado: false`, errores en `registro.txt`).
8. **Documentar**: `tf-client/JEFES.md` (nueva sección §3.x con tabla síntoma → causa medida → arreglo) y el
   CHECKPOINT de `HANDOFF.md`.
9. **Publicar** (§5) y contestar al dueño en español: qué encontraste (con los números de la prueba), qué
   arreglaste, qué es fiel al pack y no se toca, y el enlace.

## 4. Herramientas

| Qué | Dónde |
|---|---|
| Comprobación completa sin juego | `python3 tf-client/tools/bosses/verificar.py` («todo bien») |
| Auditor / simulador de pelea / física | `tools/bosses/audit.py` (`--prueba`), `sim_ai.py`, `sim_physics.py` |
| Ver skills y mobs de un jefe | `tools/bosses/ver_skill.py` |
| Esperar la prueba en el juego | `tools/bosses/esperar_prueba.sh` (en segundo plano) |
| Reconvertir un pack | `python3 -I tools/bosses/build_bosses.py <carpeta packs> <PACK>` (packs descifrados fuera del repo; mira el `git diff` después y deja `sounds.json` como estaba si solo cambia el orden) |
| Grieta normal y madre (con sus shaders) | `xvfb-run -a python3 tools/bosses/grieta3.py --preview carpeta [--madre] [--estado lista|despierta|estalla|abierta|dormida]` |
| Cielo del Void (agujero negro) | `xvfb-run -a python3 tools/void/cielo_preview.py carpeta [--size 1920x1080] [--q 0.35] [--t segundos] [--furia]` (prueba con `--t 1500` y `--t 3599.9`: mucho tiempo de juego; `--furia`: con una grieta activa) |
| Viaje por el agujero de gusano | `xvfb-run -a python3 -I tools/void/tunel_preview.py carpeta [--vuelta]` |
| Por dónde andan los jefes en la isla del Void | `python3 -I tools/void/mascara_jefes.py tf-client/src/main/resources/tfclient-void-isla.bin salida.png` |
| Santuario, Fragmentos, Anclas, la Llegada y el portal | `python3 -I tools/void/isla_alba.py [--preview carpeta]` (parte de `tools/void/isla_base.bin`, la isla del dueño; sale igual cada vez, ~45 s; pasa `tools/void/paleta.py` —todo a roca del Vacío—, `tools/void/isla_grieta.py` —la isla de la grieta: relieve, decoración, Anclas— y `tools/void/bioma.py` —los Fragmentos—, limpia lo que queda al aire y falla si queda un bloque prohibido; luego sube `IslandUpdate.VERSION`) |
| Caballeros sacros (sus texturas y modelos) | `python3 -I tools/void/caballeros.py [--ver carpeta]` (repinta a Caelthar en cada metal de `METALES`) |
| Música del santuario / sonidos de la luz | `python3 tools/void/musica_santuario.py`, `python3 tools/void/sonidos_viaje.py [nombres]` (con `tools/void/instrumentos.py`: arpa, campana, flauta, coro, sala) |
| Textura de la corrosión | `python3 tools/void/corrosion_textura.py [--ver carpeta]` (escribe también su `.mcmeta` con `blur`) |
| Velo del Alba y Corazón (sus shaders) | `xvfb-run -a python3 -I tools/void/santuario_preview.py carpeta` |
| Grieta del cielo (vista previa, GIF) | `xvfb-run -a python3 -I tools/void/grieta_cielo_preview.py carpeta [--gif]` |
| Sonidos de la grieta del cielo / del viaje y la luz | `python3 tools/void/sonidos_cielo.py [--medir] [--wav carpeta] [nombres]` (el golpe más fuerte tiene que quedar < 10 dB), `python3 tools/void/sonidos_viaje.py` (necesitan scipy y ffmpeg) |
| Criaturas de los Fragmentos (EndMobsVol1 como «fauna») | `python3 -I tools/bosses/build_bosses.py <carpeta packs> EndMobsVol1` (entrada con `fauna`, `raices` —solo `endermauler` y `ender_watchman`, orden del dueño—, `sin_modelos`); en el juego `[prueba-jefes=fauna]` |
| Set Void (y cualquier set de recompensa) | `tierras-fantasticas/tools/build_items.py` (`SETS`, `crates.py: REWARD_SETS`) y `tf-client/tools/build_mod_items.py void`; **después deja los `lang/*.json` como estaban y añade solo las claves nuevas** (se come las de las clases y el pad) |
| Panoramas del agujero de gusano | `[prueba-jefes=panoramas]` y luego `python3 -I tools/void/panoramas.py <carpeta de la prueba>` |
| Alas de Caelthar (mide y escribe «reposo») | `python3 -I tools/void/caballero_alas.py [--solo-medir]` |
| La isla (con el mapa del dueño descifrado fuera del repo) | `python3 -I tools/void/isla.py <EndCarageKitMap.schematic> [--preview carpeta]` |
| Isla del Void (santuario, plaza) | `python3 -I tools/void/isla.py <EndCarageKitMap.schematic> --preview carpeta` (mapa cifrado en `packs/void.tar.enc`) |
| Ventana /tf boss (tamaños) | `python3 tools/bosses/sim_gui.py` |
| Cielo de la grieta | `tools/bosses/rift_sky.py --preview carpeta` |
| Sonidos de grieta, muerte y música | `tools/bosses/sonidos_grieta.py --wav carpeta` |
| Estado de Actions / Releases | herramientas `mcp__github__*` (no hay `gh`) |

## 5. Publicar una versión

1. Sube `mod_version` en `tf-client/gradle.properties` **y** `VERSION` en
   `tf-client/src/main/java/net/tierrasfantasticas/tfclient/TFClient.java` (iguales; la siguiente es **1.3.103**).
2. Apunta la entrega en `HANDOFF.md` (CHECKPOINT) y en `JEFES.md`.
3. Push a la rama → espera `tf-client.yml` en verde → PR a `main` → **squash** merge.
4. El workflow crea la Release `tfclient-v<versión>` con el jar (no la marca «latest»). **Compruébala** antes de dar
   el enlace. Enlaces para el dueño: https://xn--tierrasfantsticas-hpb.store/descargar (pasa por Cloudflare; su red
   no llega a GitHub) y https://github.com/modelqdeluxe-ops/ss/releases/download/tfclient/tfclient.jar
5. Reinicia la rama desde `main` (`git fetch origin main && git checkout -B <rama> origin/main && git push -f`).

## 6. Lo que queda / lo que hay que vigilar

- **El dueño no quiere pruebas en el juego de más de 10 minutos**: usa `[prueba-jefes=corta]` (o una parte: `fauna`,
  un jefe) y no `vacio` entero salvo que haga falta; y espera con un bucle propio de 15 min como mucho.
- **Que el dueño vea la 1.3.98** (JEFES §3.23): los caballeros, el soporte, la grieta del Void apagada tras cada jefe
  (`/tf void grieta encender`), la isla de la grieta, la fauna, el agujero de gusano, la música y los volúmenes.
- **Que el dueño vea la 1.3.97**: el Void entero (los Fragmentos, la Llegada con el portal, ningún bloque del End, de
  lana ni de tierra), las dos criaturas (¿pocas o muchas?: `VoidFauna.CAP`/`EVERY`), la armadura (a ras de suelo, aura,
  una vez), las Anclas en burbuja, la corrosión (textura y `HURT` 1,5), la música del santuario, los caballeros sacros
  y lo que cuentan, la grieta del cielo (el sonido nuevo: si algo le suena a explosión, `sonidos_cielo.py --medir` y
  bajar ese ataque; la distorsión: `RiftWarp`, bloque «La grieta del cielo»; el temblor: `SkyRiftClient.shake`) y el
  agujero de gusano (`wormhole.fsh`, vista previa `tools/void/tunel_preview.py`). Preguntarle si los otros 7 sets de
  crates sin casco de armadura deben tenerlo (JEFES §11).
- **Que el dueño vea la 1.3.96**: con Photon, la grieta del cielo sin barras; la aparición, el estallido y el cierre
  nuevos con su sonido corrido (si algo le suena a golpe, `tools/void/sonidos_cielo.py --medir` y bajar ese ataque); la
  secuencia de jefes del Void en `/tf boss` (▲▼, ✎, «a todos», al acabar); el agujero de gusano (Nether, End y Void de
  verdad); y en el Void: la corrosión (¿4 min es mucho o poco?: `VoidLight.CORRODE_MIN`; el daño: `HURT`/`HURT_EVERY`;
  las grietas en pantalla: `VoidLightClient.draw` y `tools/void/corrosion_textura.py --ver carpeta`), los Pozos de Luz (¿se ven de lejos?, ¿curan rápido?: `REGEN`), el Páramo y sus
  criaturas (¿muchas o pocas?: `VoidFauna.CAP`/`EVERY`; las reglas son las del pack), la armadura del Vacío (5 jefes:
  `VoidArmor.NEED`), el altar, el portal y lo que cuenta Caelthar. Si quiere otro tamaño o sitio: constantes de
  `tools/void/bioma.py` e `isla_alba.py` (volver a pasarlo y subir `IslandUpdate.VERSION`: en su servidor la isla se
  pone al día sola).
- **Que el dueño vea la 1.3.95** (la grieta del cielo 2.0, la que pidió «chingón»): en su servidor, `/tf void cielo
  quitar` y volver a poner a Caelthar (o `/tf void cielo evento`); que mire la aparición entera (12 s de tormenta,
  estalla, se abre), los sonidos (sin cristal) y la grieta abierta, también desde lejos. Si algo no le gusta, ajústalo
  con la vista previa (`xvfb-run -a python3 -I tools/void/grieta_cielo_preview.py carpeta --gif`) y luego en el juego
  (`[prueba-jefes=vacio]`, capturas `00_vacio_cielo_*`). La línea de tiempo está dos veces (vista previa y
  `SkyRiftClient.look()`): cambia las dos. Las nubes de Minecraft quedan detrás del desgarro (`renderMask`): si el
  dueño ve nubes «cortadas» raras alrededor, es ahí.
- **Que el dueño vea la 1.3.94**: la grieta del cielo en el mismo sitio del cielo esté donde esté, la aparición
  nueva (12 s), la grieta del Void madre sin cartel y la espada de Caelthar como un jugador.
- **Que el dueño vea la 1.3.93** en su servidor: al poner a Caelthar en el spawn, el evento (chat, temblor, el cielo
  que se rompe) en el spawn y en el Overworld de los jugadores. Si quiere repetirlo: `/tf void cielo quitar` y volver
  a poner a Caelthar (o `/tf void cielo evento`). **El jefe final** «super fuerte» lo mandará más adelante: entonces,
  que su muerte llame a `SkyRift.end` (hoy el final es el comando `/tf void cielo final`).
- **Que el dueño vea la 1.3.92**: que el jefe del Void le persiga por la isla y se pare a ~6 bloques del borde; el
  agujero de gusano nuevo (ida y vuelta, con la antesala del Void); el agujero negro lento y con el borde suave; la
  espada de Caelthar y el diálogo nuevo. Si la espada queda mal en la mano, se ajusta en `KnightRenderer.sword`.
- **Que el dueño vea la 1.3.91**: la grieta cerrada que estalla al despertarla, la distorsión durante la pelea (si le
  molesta para pelear, se baja en `RiftWarp.profile`: lente y remolino de FIGHT), el agujero negro que se enfurece y
  el viaje por el agujero de gusano (ida y vuelta). Si en su PC el túnel va a pocos fotogramas, el informe de la prueba
  dice cuántos da ahí («agujero de gusano: ...»); el shader se puede aligerar (menos cubos, `CUBES`, o sin la
  aberración cromática, que lo calcula tres veces).
- **Que el dueño pruebe la 1.3.90**: al arrancar su servidor, la isla de su Void se rehace sola (en el registro «TF
  Void: la isla de este mundo es de antes; se pone al día (N chunks, primero el santuario)» y, al acabar, «isla al día»;
  menos de un minuto). Que mire la grieta en el centro desde arriba, el disco del agujero negro tras horas de juego
  (sin muaré), los chorros y las nebulosas que se mueven, y las alas de Caelthar. Si el disco se ve borroso o el cielo
  va lento con un PC flojo, en Gráficos «Rápidos» el cielo usa menos pasos (`Quality`).
- **(1.3.89) Lo de antes, por si vuelve a salir**: la grieta del Void (en `/tf boss` → GRIETAS, la tarjeta de arriba con ✎: listas
  de la normal y de la madre), el santuario nuevo de la punta norte (su Void ya generado se pone al día solo al ir: en el
  registro «TF Void: ... se pone al día»), el cielo con y sin shaders, Caelthar y las llamas de Ignevar. **Con
  shaders** (Oculus) la grieta y nuestro cielo se dibujan después de la pasada final del paquete (`vacio/AfterShaders`,
  JEFES §3.14): si algo de eso falla con otro paquete, mira el registro («después del paquete») y prueba con `[shaders]`.
- **Ignevar «pendejo, lento»**: anda como el pack (se empuja `velocity z=0.05` por tick, ~0,11 b/t en Normal) y ahora
  a la Velocidad de la dificultad; si sigue pareciendo lento en Normal, es el pack: pregúntale si quiere que la grieta
  del Void use Difícil o más para él.
- **Contemplador**: ya no te sube al cielo (orden del dueño, «suelo»), pero sus láseres siguen al ritmo del pack (en
  la prueba, 140 impactos en 40 s). Si el dueño dice que dispara demasiado, pregúntale antes de tocarlo.
- **La grieta del Void** es fija (no se quita ni se edita como las demás); las grietas normales se siguen poniendo con
  `/tf boss` donde quieras (también en The Void).
- **Que el dueño pruebe la 1.3.87** (Astwood sin supersalto, Mamut, grieta y cielo nuevos, música de la grieta,
  muerte). La música no se puede oír en el CI (no hay tarjeta de sonido): la prueba solo mira si *quiere* sonar.
- **Contemplador**: el jugador de prueba recibe ~500 de daño (cada impacto del láser te quema, te lanza y avisa al
  jefe para que te pegue: es el pack). Si el dueño dice que es injusto, pregúntale antes de tocarlo.
- **Archivista**: el jugador de prueba no llega a pegarle (se aleja y empuja; es su diseño); sus velas hacen 1 de
  daño porque el pack les da `Damage: 1`.
- **Bola azul de Astwood**: impacta en la prueba (2/2); con `inertia=100` se esquiva andando (es el pack).
- **51 packs más y Pohaku** (JEFES.md §11.0): el dueño tiene que volver a mandar los packs si se perdieron.
- Lo que falta de MythicMobs/ModelEngine y lo que hay que preguntar: JEFES.md §11.2-11.4.

## 7. Prompt para empezar una sesión nueva (cópialo tal cual)

```
Lee SIGUIENTE-IA.md, CLAUDE.md, el CHECKPOINT de HANDOFF.md y tf-client/JEFES.md (§3.10, §3.21, §11). Seguimos con
el motor de jefes de TF Client (1.3.96): fiel a MythicMobs 5 + ModelEngine 4. Si es sobre la grieta del cielo, lee
también tf-client/docs/grieta-cielo-2/README.md (con sus imágenes). Responde en español, sin subagentes,
compila solo con GitHub Actions, publica como Release, nunca subas los packs en claro. Si el dueño se queja de algo,
reprodúcelo TÚ con la prueba en el juego ([prueba-jefes=<jefes>] en el commit, espera en segundo plano con
tools/bosses/esperar_prueba.sh), lee el informe y las capturas, arréglalo con la semántica de MythicMobs, pasa
verificar.py («todo bien»), vuelve a probar en el juego, documenta (JEFES.md §3.x y HANDOFF.md) y publica.
```
