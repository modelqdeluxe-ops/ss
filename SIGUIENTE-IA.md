# Para la siguiente IA: qué hacemos y cómo seguir EXACTAMENTE igual

Estado al dejarlo: **19 oct 2026, TF Client 1.3.92** (el jefe del Void por toda la isla sin salirse, el agujero de
gusano limpio con la antesala del Void, el agujero negro lento y sin dientes, la espada y el diálogo de Caelthar:
JEFES.md §3.17; antes, 1.3.91: la grieta que se abre al romper el cristal y la distorsión, §3.16; y el CHECKPOINT de
HANDOFF.md).
Rama de trabajo: `claude/intelligent-cray-63dqai` (o la que te asigne la sesión), **reiniciada desde `main`**.

Lee también, en este orden: `CLAUDE.md` (reglas), el CHECKPOINT de `HANDOFF.md` (sección 0) y
`tf-client/JEFES.md` (el motor de jefes; §3.10 la prueba en el juego, §3.17 lo último, §11 lo que falta).

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
   Void y lo demás con un paquete de shaders; va lento, úsalo solo con `vacio` y uno o dos jefes).
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
| Alas de Caelthar (mide y escribe «reposo») | `python3 -I tools/void/caballero_alas.py [--solo-medir]` |
| La isla (con el mapa del dueño descifrado fuera del repo) | `python3 -I tools/void/isla.py <EndCarageKitMap.schematic> [--preview carpeta]` |
| Isla del Void (santuario, plaza) | `python3 -I tools/void/isla.py <EndCarageKitMap.schematic> --preview carpeta` (mapa cifrado en `packs/void.tar.enc`) |
| Ventana /tf boss (tamaños) | `python3 tools/bosses/sim_gui.py` |
| Cielo de la grieta | `tools/bosses/rift_sky.py --preview carpeta` |
| Sonidos de grieta, muerte y música | `tools/bosses/sonidos_grieta.py --wav carpeta` |
| Estado de Actions / Releases | herramientas `mcp__github__*` (no hay `gh`) |

## 5. Publicar una versión

1. Sube `mod_version` en `tf-client/gradle.properties` **y** `VERSION` en
   `tf-client/src/main/java/net/tierrasfantasticas/tfclient/TFClient.java` (iguales; la siguiente es **1.3.93**).
2. Apunta la entrega en `HANDOFF.md` (CHECKPOINT) y en `JEFES.md`.
3. Push a la rama → espera `tf-client.yml` en verde → PR a `main` → **squash** merge.
4. El workflow crea la Release `tfclient-v<versión>` con el jar (no la marca «latest»). **Compruébala** antes de dar
   el enlace. Enlaces para el dueño: https://xn--tierrasfantsticas-hpb.store/descargar (pasa por Cloudflare; su red
   no llega a GitHub) y https://github.com/modelqdeluxe-ops/ss/releases/download/tfclient/tfclient.jar
5. Reinicia la rama desde `main` (`git fetch origin main && git checkout -B <rama> origin/main && git push -f`).

## 6. Lo que queda / lo que hay que vigilar

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
Lee SIGUIENTE-IA.md, CLAUDE.md, el CHECKPOINT de HANDOFF.md y tf-client/JEFES.md (§3.10, §3.17, §11). Seguimos con
el motor de jefes de TF Client (1.3.92): fiel a MythicMobs 5 + ModelEngine 4. Responde en español, sin subagentes,
compila solo con GitHub Actions, publica como Release, nunca subas los packs en claro. Si el dueño se queja de algo,
reprodúcelo TÚ con la prueba en el juego ([prueba-jefes=<jefes>] en el commit, espera en segundo plano con
tools/bosses/esperar_prueba.sh), lee el informe y las capturas, arréglalo con la semántica de MythicMobs, pasa
verificar.py («todo bien»), vuelve a probar en el juego, documenta (JEFES.md §3.x y HANDOFF.md) y publica.
```
