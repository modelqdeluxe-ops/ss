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
| `bot.js <nombre> [puerto] [carpeta]` | un jugador que entra en un servidor Forge: hace el apretón de manos de Forge (FML3: contesta a `fml:loginwrapper` con la lista de mods y canales del servidor y acusa recibo de los registros y la config). Escribe en el chat cada línea que se añada a `<carpeta>/<nombre>.cmd` (`quit` para salir) y apunta todo lo que le llega del chat |
| `clanes_prueba.py <mundo>/tfclient/clanes.json` | los clanes de la prueba (con el servidor parado): [TF] Ana y Beto, [OTR] Carlos, Dani y Eva sin clan |
| `probar_chat_clan.sh <carpeta> <jar>` | la prueba entera del chat del clan (abajo) |

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
