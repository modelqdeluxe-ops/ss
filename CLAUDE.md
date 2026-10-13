# Reglas del repo (léelas siempre)

- Lee `HANDOFF.md` entero antes de tocar nada: ahí están el estado del proyecto y las reglas del dueño.

## Publicar TF Client (el mod) — SIEMPRE como Release de GitHub

- **No subas jars al repo** (`descargas/` ya no se usa para versiones nuevas: hace el repo enorme y rompe el
  despliegue de Cloudflare). La descarga oficial es la Release de GitHub.
- Para publicar una versión: sube `mod_version` en `tf-client/gradle.properties` **y** `VERSION` en
  `tf-client/src/main/java/net/tierrasfantasticas/tfclient/TFClient.java` (iguales), apunta la entrega en `HANDOFF.md`
  y fusiona en `main`. El workflow `.github/workflows/tf-client.yml` compila el jar y crea solo la Release
  `v<mod_version>` con `tfclient-1.20.1-<versión>.jar` y una copia `tfclient.jar`.
- Enlace fijo para el dueño y los jugadores (siempre la última versión):
  **https://github.com/modelqdeluxe-ops/ss/releases/download/tfclient/tfclient.jar**
  De una versión concreta: `https://github.com/modelqdeluxe-ops/ss/releases/download/v<versión>/tfclient-1.20.1-<versión>.jar`.
- No marques las Releases de TF Client como «latest»: el repo tiene otras (StickerYa) y su enlace de la última
  dejaría de funcionar. El workflow ya lo hace bien.
- Tras fusionar, comprueba que la Release existe (el CI tarda unos minutos) antes de dar el enlace: si falla el CI,
  arréglalo; no vuelvas a subir el jar al repo como atajo.
