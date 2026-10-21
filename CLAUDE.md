# Reglas del repo (léelas siempre)

- **Empieza por `SIGUIENTE-IA.md`**: qué estamos haciendo y el procedimiento exacto (prueba en el juego con GitHub
  Actions en segundo plano, verificar, publicar). Luego lo de abajo.
- Lee `HANDOFF.md` antes de tocar nada; empieza por su **CHECKPOINT** (sección 0): estado, reglas del dueño, cómo
  publicar, qué falta por verificar y el mapa del TF Pad.
- **No uses subagentes** (Agent / Workflow): el dueño lo prohibió. Hazlo tú directamente.
- Cuando el dueño mande capturas, **mídelas** (recorta, amplía, colores con PIL) antes de decir que algo está arreglado.
- Responde al dueño en español.
- Crates (TF Crates, `/tf crates`, la cinemática del Bosque de las Hadas): lee `tf-client/CRATES.md` antes de tocarlas.
- NPC de escenas (`/tf npc`, el panel de NPC, los 13 packs de Scenes): lee `tf-client/NPCS.md` antes de tocarlos.
  La 1.3.103 (el pescador recoge delante, la cara del 2.º de los dúos delante) es la última; lo que queda, en
  `tf-client/NPCS-CONTINUAR.md`.
  La 1.3.99 está publicada; si hay que volver a tocar la cinemática, sigue `tf-client/CRATES-CONTINUAR.md`.
- Jefes de incursión (TF Client): lee `tf-client/JEFES.md` antes de tocarlos (motor, cerebro, herramientas y cómo
  comprobarlos sin abrir el juego).
- **El motor de jefes tiene que ser fiel a MythicMobs/ModelEngine para TODOS los packs que metamos** (orden del dueño).
  Antes de publicar un jefe: `python3 tools/bosses/audit.py` con **0 problemas** (y sus avisos leídos),
  `audit.py --prueba` (el auditor ve todos los errores metidos a propósito), `test_convert.py` «todo bien»,
  `sim_physics.py` en los modelos con colas/segmentos/física y `sim_ai.py` sin cambios inesperados en los demás jefes.
  Si tocas la IA, el movimiento, las grietas o lo que se ve: **prueba en el juego tú mismo** con `[prueba-jefes]` en
  el commit (`tf-client/JEFES.md` §3.10) y lee el informe y las capturas de la rama `pruebas-jefes`; no se la pidas al dueño.
  ModelEngine 4 completo está en `tf-client/JEFES.md` §3.6, el servidor (Mohist) en §10 y lo que falta (con un
  prompt para seguir) en §11. Si un pack usa algo que
  el motor no tiene, se implementa con la semántica de MythicMobs/ModelEngine (y en `sim_ai.py`); nunca se quita del
  pack ni se deja «de mentira» sin explicarlo en `STUB_OK` de `audit.py`.
- **Compila SIEMPRE con GitHub Actions** (orden del dueño): push a la rama y mira el workflow `tf-client.yml`. No
  compiles en el contenedor con `./gradlew`.

## Publicar TF Client (el mod) — SIEMPRE como Release de GitHub

- **No subas jars al repo** (`descargas/` ya no se usa para versiones nuevas: hace el repo enorme y rompe el
  despliegue de Cloudflare). La descarga oficial es la Release de GitHub.
- Para publicar una versión: sube `mod_version` en `tf-client/gradle.properties` **y** `VERSION` en
  `tf-client/src/main/java/net/tierrasfantasticas/tfclient/TFClient.java` (iguales), apunta la entrega en `HANDOFF.md`
  y fusiona en `main`. El workflow `.github/workflows/tf-client.yml` compila el jar y crea solo la Release
  `tfclient-v<mod_version>` con `tfclient-1.20.1-<versión>.jar` y una copia `tfclient.jar` (el tag no empieza por
  `v<número>`: el workflow de Rumentis borra esas Releases).
- Enlace fijo para el dueño y los jugadores (siempre la última versión):
  **https://github.com/modelqdeluxe-ops/ss/releases/download/tfclient/tfclient.jar**
  De una versión concreta: `https://github.com/modelqdeluxe-ops/ss/releases/download/tfclient-v<versión>/tfclient-1.20.1-<versión>.jar`
  (desde la 1.3.68; las `v1.3.x` de antes las borró Rumentis).
- No marques las Releases de TF Client como «latest»: el repo tiene otras (StickerYa) y su enlace de la última
  dejaría de funcionar. El workflow ya lo hace bien.
- Tras fusionar, comprueba que la Release existe (el CI tarda unos minutos) antes de dar el enlace: si falla el CI,
  arréglalo; no vuelvas a subir el jar al repo como atajo.
