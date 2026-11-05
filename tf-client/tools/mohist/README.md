# Un Mohist de verdad para probar el mod (sin el juego)

El servidor del dueño es **Mohist 1.20.1** (Forge + Bukkit) con EssentialsX, EssentialsXChat y LuckPerms. Las pruebas en
el juego de GitHub Actions (`[prueba-jefes=…]`) son Forge puro, y hay cosas que en Mohist van por otro camino (el chat,
los comandos, los eventos de Bukkit). Esto monta ese servidor en el contenedor y lo prueba con jugadores bot.

No compila nada (la regla de siempre: el mod solo se compila en GitHub Actions). El jar sale de la Release
(`https://github.com/modelqdeluxe-ops/ss/releases/download/tfclient/tfclient.jar`) o del artefacto `tfclient-jar` de la
ejecución de `tf-client.yml` de la rama:

```bash
gh run download <id de la ejecución> --repo modelqdeluxe-ops/ss -n tfclient-jar -D /tmp/jar
```

(o `gh api repos/modelqdeluxe-ops/ss/actions/runs/<id>/artifacts` y bajar el zip de `archive_download_url`).

## Piezas

| Archivo | Qué hace |
|---|---|
| `montar.sh <carpeta> <jar>` | baja Mohist 1.20.1 (la última de `api.mohistmc.com`), LuckPerms, EssentialsX 2.20.1 y EssentialsXChat, pone el jar en `mods/`, `server.properties` sin cuentas (`online-mode=false`) y los bots (`npm install` de `minecraft-protocol`). Si ya está montado, solo cambia el jar |
| `bot.js <nombre> [puerto] [carpeta]` | un jugador que entra en un servidor Forge: hace el apretón de manos de Forge (FML3: contesta a `fml:loginwrapper` con la lista de mods y canales del servidor y acusa recibo de los registros y la config). Escribe en el chat cada línea que se añada a `<carpeta>/<nombre>.cmd` (`quit` para salir), con dos especiales: `!usar x y z` (clic derecho con lo de la mano sobre la cara de arriba de ese bloque: poner una piedra o un bloque) y `!hueco n`; (1.3.175) `!usar x y z [cara cx cy cz]`, `!mirar yaw pitch` y `!romper x y z`. Apunta también la barra de acción (`BARRA:`) y cada cambio de su vida (`VIDA: 17.40 (-0.85)`). Confirma los `/tp` como el juego (si no, el servidor no le deja usar nada). Apunta todo lo que le llega del chat, los títulos y los subtítulos |
| `comun.sh` | lo de todas las pruebas (con `source`): `arrancar`, `conectar <bots…>`, `consola <orden>`, `di <bot> <línea>`, `mira` / `comprueba` (apuntan `bien` / `MAL`), `parar` y `final`. Los `pkill` llevan el patrón entero (`[n]ode bot/bot.js`, `[j]ava -Xms1G …`): uno más corto puede matar la propia terminal que lanza la prueba |
| `clanes_prueba.py <mundo>/tfclient/clanes.json` | los clanes de la prueba (con el servidor parado): [TF] Ana y Beto, [OTR] Carlos, Dani y Eva sin clan |
| `probar_chat_clan.sh <carpeta> <jar>` | la prueba entera del chat del clan (abajo) |
| `protecciones_prueba.py <mundo>/claimblocks_data.json` | la zona grande de Ana (100x100 en 0,-60,0) con el grupo «Reino de Ana» (Ana y Beto) |
| `probar_protecciones.sh <carpeta> <jar>` | la piedra dentro de la zona de un grupo, unida o independiente (1.3.170, abajo) |
| `probar_luna.sh <carpeta> <jar>` | (1.3.175) la Luna de Sangre: con 2 no sale y la cuenta vuelve a 10, con 3 sale, nadie duerme (un bot usa una cama), el daño mágico (zombi normal 0,85 y Élite 3,6 contra diamante, por la vida de los bots), los mobs extra y el amanecer. Pone `spawn-monsters=true` (con `false`, Bukkit borra todo monstruo que se añade). Termina con `LUNA DE SANGRE EN MOHIST: TODO BIEN` |
| `probar_metricas.sh <carpeta> <jar> <registros de nLogin>` | (1.3.174) el puente manda los registros de nLogin a la web (el Worker de la tienda en local, `web_metricas.mjs`): todo desde el 26 oct una vez, sin IP, lo nuevo del último registro y tras reiniciar sigue donde iba. Termina con `METRICAS DE NLOGIN EN MOHIST: TODO BIEN` |

El primer arranque baja 118 librerías de Mohist (un minuto); luego arranca en ~15 s. Java 21 del contenedor vale.

## La prueba del chat del clan

```bash
tf-client/tools/mohist/probar_chat_clan.sh /tmp/tf-mohist /tmp/jar/tfclient-1.20.1-<versión>.jar
```

Arranca Mohist, hace operadora a Eva, conecta los 5 bots y: Ana activa el chat del clan y escribe; Carlos escribe en el
público, activa el suyo y escribe; Ana lo desactiva y escribe en el público. Comprueba línea a línea (`bien` / `MAL`):
lo de [TF] solo a Ana, Beto y Eva (operadora); lo de [OTR] solo a Carlos y Eva; quien tiene el chat del clan no ve el
público; tras `disable` vuelve al público; en el registro sale `[Clan TF] Ana: …` y nunca como chat público. Termina con
`CHAT DEL CLAN EN MOHIST: TODO BIEN` (y sale con 0) o con el número de fallos. Deja `server.out` y `bots/*.log`.

Con la 1.3.168 da 7 fallos (el fallo que vio el dueño: a nadie le llega nada); con la 1.3.169, todo bien.

## La prueba de las protecciones dentro de un grupo (1.3.170)

```bash
tf-client/tools/mohist/probar_protecciones.sh /tmp/tf-mohist /tmp/jar/tfclient-1.20.1-<versión>.jar
```

Beto (del grupo de Ana) pone de verdad una piedra de 25x25 dentro de la zona grande de Ana (con `!usar`) y se comprueba
con bloques puestos de verdad (`execute if block` por la consola), los mensajes de entrar (Carlos se teletransporta) y
`claimblocks_data.json`: que sale la elección; unida, Ana construye en su cuadrado y al pasar no hay zona nueva;
independiente, Ana no construye ahí (fuera sí), al entrar sale «Entrando a la zona Beto (25x25)» y al volver la
bienvenida de Ana; otra vez unida; Carlos (fuera del grupo) no pone su piedra dentro; y al salir Beto del grupo, su
piedra independiente se le devuelve. Termina con `PROTECCIONES DENTRO DE UN GRUPO EN MOHIST: TODO BIEN`.

## Lo que se aprendió de Mohist (1.20.1, build 921, Forge 47.4.23)

- **El `ServerChatEvent` de Forge no lo lanza Forge con lo escrito.** Mohist cambia el decorador del chat
  (`ForgeHooks.getServerChatSubmittedDecorator`): lanza el `AsyncPlayerChatPreviewEvent` de Bukkit y solo si un plugin
  cambió el formato, el mensaje o el nombre visible, lanza el `ServerChatEvent`… con `getRawText()` **vacío** (lo saca
  de la raíz de un texto de Bukkit, que siempre es ""). Si no cambió nada, no lo lanza. Lo escrito de verdad está en
  `broadcastChatMessage` (`PlayerChatMessage.signedContent()`) y en el `AsyncPlayerChatEvent` de Bukkit
  (`com.mohistmc.util.ChatPatchFix.chat`).
- El `AsyncPlayerChatPreviewEvent` tiene su propia lista de oyentes: un oyente del `AsyncPlayerChatEvent` no lo ve.
- Para ver el código de Mohist: las clases de Minecraft y Forge parcheadas están en
  `libraries/net/minecraftforge/forge/1.20.1-47.4.23/forge-1.20.1-47.4.23-server.jar` (y `…-universal.jar`, con
  `com/mohistmc/…`); se leen con Vineflower (`https://repo1.maven.org/maven2/org/vineflower/vineflower/1.10.1/`).
  Los nombres de los métodos son SRG (`handleChat` = `m_7388_`, `broadcastChatMessage` = `m_243086_`).
- Volcado de hilos del servidor (si algo se queda colgado): `jcmd $(pgrep -f "[m]ohist.jar nogui" | tail -1) Thread.print`.
