# Reglas del repo (léelas siempre)

- **Empieza por `SIGUIENTE-IA.md`**: qué estamos haciendo y el procedimiento exacto (prueba en el juego con GitHub
  Actions en segundo plano, verificar, publicar). Luego lo de abajo.
- Lee `HANDOFF.md` antes de tocar nada; empieza por su **CHECKPOINT** (sección 0): estado, reglas del dueño, cómo
  publicar, qué falta por verificar y el mapa del TF Pad.
- **No uses subagentes** (Agent / Workflow): el dueño lo prohibió. Hazlo tú directamente.
- Cuando el dueño mande capturas, **mídelas** (recorta, amplía, colores con PIL) antes de decir que algo está arreglado.
- Responde al dueño en español.

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
