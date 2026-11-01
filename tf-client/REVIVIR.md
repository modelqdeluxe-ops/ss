# Revivir (el Revive Mod del dueño dentro del TF Client) — 1.3.157

El dueño (1 nov 2026): «es mi mod igual, será nueva integración a TF Client… en la config pon que puedan rematarme los
jugadores, ojo no mobs (déjalo opcional en la config, funcional), y el reload de tf que aplique los cambios en tiempo
real. Hazlo bien, exactamente todo igual, solo lo añadirás».

Su mod era `ReviveForge-1.20.1V2.jar` (modId `revivemod`, 2.1.0, `com.revivemod`, fuente en su GitHub
`pewez267-dot/Rangos`, que esta sesión no podía abrir). Se sacó el código del jar (Vineflower) y se tradujeron los
nombres de Minecraft (SRG → oficiales con `mcp_config` 1.20.1 + las mappings de Mojang, sin adivinar ninguno).

## Qué hace (igual que el revivemod)

- Al morir no mueres: quedas **noqueado** («Desangrándose», barra roja con los segundos; `downTimeSeconds`, 90), tumbado
  (pose de nadar), con Lentitud y Brillo, sin poder romper, usar, atacar ni abrir inventarios, y con la ranura fija.
  Mensaje a todos: «X se está desangrando.». Los mobs te pierden de vista.
- Te **revive** otro jugador manteniendo clic derecho cerca (`reviveDistance` 3 bloques; `reviveTimeTicks` 80; con
  varios, más rápido). El que revive no recibe daño. Vuelves con `reviveHealth` (6) y `reviveFood` (10), Regeneración y
  Resistencia 10 s, y los efectos que tenías.
- **Rendirse** (mantener E 4 s) o **auto-revivir** pagando `selfReviveLevelCost` niveles (mantener F 4 s).
- El vacío y `/kill` matan aunque estés noqueado. En creativo o espectador no se noquea.
- `/revive` (help, surrender, self; con permiso 2: status, reload, force, kill, down, set time|distance|channel|selfcost).
- Teclas en Opciones › Controles › «Revive Mod» (mismos nombres que antes: se conservan las que tuviera cada uno) y la
  pantalla de teclas del mod (`ReviveConfigScreen`), que se abre desde **TF Pad › Ajustes › Teclas de revivir** (la
  fila enseña las dos teclas, «E · F») y desde Mods › TF Client › Configuración.

## Lo nuevo: rematar

| Clave de `config/tfclient/revivir.json` | Por defecto | Qué hace |
| --- | --- | --- |
| `playersCanFinish` | `true` | Otro jugador (de verdad, no las máquinas de otros mods) remata a un noqueado: un golpe suyo que haga daño (espada, flecha…) lo mata de verdad, con tumba, mensaje de muerte y asesinato (efecto de kill, guerras). Las zonas sin PvP y el PvP apagado lo siguen parando. |
| `mobsCanFinish` | `false` | Los mobs (y los jefes) también rematan; con `true` no pierden de vista al que cae. |

Cómo: `CombatEvents.onAttack` deja pasar ese golpe; `onHurt` (prioridad más baja, después de las protecciones) lo sube a
1 000 000; `onDeath` (prioridad alta, antes que el resto del TF Client, después de los duelos) deja morir y
`DownManager.finished` quita el noqueo (barra, efectos, tumbado) con el sonido de morir.

`/tf reload` y `/revive reload` lo aplican al momento; `/revive set rematar jugadores|mobs true|false` también.

## Cosas a saber

- **Quitar el revivemod** del servidor (carpeta `mods`) y del AutoModpack (`automodpack/host-modpack/…/mods`): si sigue
  instalado, el revivir del TF Client no se enciende (registro: «TF Revivir: el mod «revivemod» sigue instalado…»). La
  primera vez se copia `config/revivemod.json` a `config/tfclient/revivir.json`.
- El canal de red (`tfclient:revivir`) se registra siempre, para que servidor y jugadores tengan los mismos.
- Prueba en el juego: `[prueba-jefes=revivir]` (`bosses/BossTestRevive`), con «dianas»: `TFBotPlayer.spawnTarget`,
  jugadores de prueba que sí reciben daño. Capturas `revivir_1_noqueada`, `revivir_2_pantalla_noqueado`,
  `revivir_3_reviviendo`, `revivir_4_ajustes_pad` y `revivir_5_pantalla_teclas`. Las dianas se golpean a partir del
  tick 80: un jugador recién entrado es invulnerable 60 ticks (`spawnInvulnerableTime`; la 1.ª prueba falló por eso).
