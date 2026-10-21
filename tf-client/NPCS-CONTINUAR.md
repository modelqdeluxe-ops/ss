# Prompt de continuación — NPC de escenas (TF Client 1.3.101, PUBLICADA)

> Escrito el 21 oct 2026. Cómo funciona todo: `tf-client/NPCS.md`. Estado general: CHECKPOINT de `HANDOFF.md`.

---

Eres la IA que sigue los **NPC de escenas** de TF Client en el repo `modelqdeluxe-ops/ss`. La **1.3.101 está publicada**
(Release `tfclient-v1.3.101`): los 13 packs de Scenes (130 NPC + 4 marcadores) en el jar, el panel de `/tf npc` y la
cabeza, los brazos y las piernas pegados al cuerpo en todas las animaciones. La siguiente versión es la 1.3.102.

## Reglas del dueño (no se rompen)

- Respóndele **en español**. **No uses subagentes.** Avísale de lo que haces (se enfada si estás callado) y **dile
  cuánto va a tardar** antes de lanzar algo largo (preguntó «¿por qué tanto tiempo?»).
- Compila **siempre con GitHub Actions** (push a la rama; `tf-client.yml`). Nunca `./gradlew` en el contenedor.
- **No subas jars al repo.** Publicar = Release `tfclient-v<versión>` (la crea el workflow al fusionar en `main`; no la
  marques «latest»; comprueba que existe antes de dar el enlace).
- **Una sola prueba en el juego por cambio** (`[prueba-jefes=npcs]` en el commit, ~10 min con la compilación).
- Las capturas, **mídelas** (PIL) antes de decir que algo está bien. **«No quiero ningún error, no me entregues algo
  mal»**: no le digas que algo está pegado o bien sin medirlo (`tools/npcs/pegado.py`).
- **Los packs nunca en el repo** (es público y son comprados). Te los adjunta el dueño; cada zip en su carpeta, Python
  con `-I`; las carpetas con el nombre del campo «pack» de cada JSON (NPCS.md §1).

## Lo que queda

1. **Restos con la «Plantilla del pack»** (la opción del panel que quita la piel; con la piel, que es lo que se ve por
   defecto, `pegado.py` da 0 huecos en 1 456 420 poses). Con la plantilla, `pegado.py --plantilla --azar 500 --semilla 6`
   ve: «grab» del aldeano de las cajas (`scene_townsfolk_handler`), pierna derecha a 0,35 px a los 0,040 s (un salto que
   dejó una pasada anterior de `pegar.py` entre dos fotogramas a 4 ms); «idle» de `scene_townsfolk_5_pose_4`, brazos a
   0,003 px a los 11,74 s (junto a un fotograma clave del pack). `pegar.py` ya mide cada 0,5 ms entre dos fotogramas
   clave a menos de 12 ms (`close_times`), pero no se llegó a pasar: son 135 animaciones (lista con
   `pegar.close_times`), unas 2-3 h con 4 procesos:
   `python3 tf-client/tools/npcs/pegar.py --procesos 4 --animaciones <fichero «modelo:animación»>` (en tandas de menos de
   2 h: el contenedor corta los procesos de fondo a las 2 h). Luego `pegado.py` con y sin `--plantilla`, rejilla y
   `--azar 500` con semillas nuevas, hasta «todo pegado».
2. **El sedal del pescador** («fishing») sube al cielo: es del pack (NPCS.md §3, «Fallos del propio pack»). Si el dueño
   lo quiere arreglado, es cambiar esa animación del pack (con +58° en vez de −58° en la caña colgaría al agua).
3. **El minero no muerto** se quema al sol (es un ZOMBIE sin `PreventSunburn`, igual en MythicMobs).

## Cómo se comprueba (NPCS.md §4)

- `python3 tf-client/tools/bosses/verificar.py` → «todo bien».
- `python3 tf-client/tools/npcs/pegado.py [pack…] [--plantilla] [--fps 60] [--azar 500 --semilla S]` → «todo pegado»
  (los 13 packs en paralelo con `xargs -P 4`; ~5 min por pack a 60/s).
- Prueba en el juego `[prueba-jefes=npcs]`: `tf-client/tools/bosses/esperar_prueba.sh <hash> <carpeta>` en segundo plano.
