# TF Client — Tierras Fantásticas (Forge 1.20.1)

Mod de cliente de Tierras Fantásticas. Hace solo esto:

- **Pantalla de carga de TF**: el banner de Tierras Fantásticas (con las letras) a pantalla completa y la barra de
  progreso de Minecraft. Sustituye a la pantalla de Mojang/Forge, al abrir el juego y al recargar recursos (F3+T).
  Se queda opaca hasta que la original termina y después se funde sola sobre el menú, así nunca asoma el rótulo rojo.
- **Menú principal de TF**, como en TierrasMon: el paisaje de TF de fondo (sin letras), el emblema TF encima de los
  botones (animado como en la web: flota subiendo y bajando, tiene un halo dorado y azul que late, y cada 7 segundos
  lo cruza despacio un destello y brilla la gema de la corona), y botones propios con la fuente Cinzel: placa con las puntas en ángulo, azul noche (como el zafiro de la
  corona del logo) con doble filete dorado y rombos en los extremos; el botón del servidor, en oro con letras oscuras:
  - **TIERRAS FANTÁSTICAS**: antes de conectar pregunta al servidor qué mods usa. Si te falta alguno, enseña la lista
    (con *Volver* o *Entrar igual*); si no falta nada, conecta directamente y acepta su paquete de recursos.
  - **Mundo local**, **Mods**, **Opciones** y **Salir**.
  - **Web** y **Discord** (de oro, debajo de Opciones y Salir). *Web* abre `web.url`. *Discord* pide al widget de
    Discord del servidor una invitación del propio servidor (no de una persona, y siempre vigente); si no responde,
    usa `discord.url`. Hace falta tener activado el widget en *Ajustes del servidor → Widget*.
  - Los botones de oro tienen su propio brillo (distinto al del emblema): el oro late suave y aparecen chispitas en
    el borde.
  - Sin textos de Mojang, versión, Forge ni Realms. Idioma y accesibilidad siguen en *Opciones*.
- **Música de TF** en la pantalla de carga y el menú: empieza en cuanto arranca el juego, suena en bucle con fundidos
  y respeta los volúmenes *General* y *Música* de las opciones. Se apaga con un fundido al entrar a un mundo o al
  servidor (ahí vuelve la música normal de Minecraft) y vuelve a sonar al regresar al menú.
- **Tema de TF en todos los menús fuera de una partida** (mundos, opciones, conexión...): botones, botones de opciones
  y deslizadores con la placa azul noche y oro y la letra Cinzel; fondo con el paisaje oscurecido y listas
  transparentes en vez de la tierra. **Dentro de una partida no se toca nada**, así cada jugador ve sus paquetes de
  recursos.

No necesita Fabric API, GeckoLib ni ningún otro mod: solo Forge. No hace falta instalarlo en el servidor.

## Instalar

1. Instala **Forge 1.20.1** (47.x).
2. Copia `tfclient-1.20.1-1.0.8.jar` en la carpeta `mods`.

## Cambiar el servidor

La primera vez que se abre el juego se crea `config/tfclient.properties`:

```properties
server.name=Tierras Fantásticas
server.address=216.163.187.40\:19001
web.url=https\://tienda.tierrasfantasticas.net
discord.guild.id=1439703812368765082
discord.url=https\://discord.gg/tRrunHBZE
```

`server.name` es el texto del botón principal, `server.address` la IP (con puerto) a la que conecta, `web.url` la
dirección que abre el botón Web, `discord.guild.id` el ID del servidor de Discord (para pedir la invitación a su widget)
y `discord.url` el enlace de reserva si el widget no responde.

## Cambiar las imágenes

- Pantalla de carga: `src/main/resources/assets/tfclient/textures/gui/loading_background.png` (16:9; se recorta para
  cubrir la pantalla).
- Fondo del menú: `src/main/resources/assets/tfclient/textures/gui/menu_background.png` (16:9).
- Emblema: `src/main/resources/assets/tfclient/textures/gui/logo.png` (PNG con transparencia).
- Icono en la lista de mods: `src/main/resources/tfclient_logo.png`.
- Efecto del emblema: `tools/gen_logo_fx.py` genera `logo_glow.png`, `logo_shine.png` y `sparkle.png` a partir de
  `logo.png` (vuelve a ejecutarlo si cambias el emblema).
- Botones: `tools/gen_buttons.py` genera `button_slice.png` (azul noche) y `button_slice_primary.png` (oro), en tres
  piezas: puntas de 16 píxeles de interfaz a tamaño fijo y centro uniforme que se estira al ancho de cada botón
  (3 estados apilados: normal, ratón encima, desactivado; 4 píxeles por píxel de interfaz).
- Fuente: `src/main/resources/assets/tfclient/font/cinzel.ttf` (Cinzel, licencia SIL Open Font License, incluida en
  `OFL-Cinzel.txt`).
- Música: `src/main/resources/assets/tfclient/music/menu.ogg` (OGG Vorbis; para convertir un MP3:
  `ffmpeg -i musica.mp3 -vn -ac 2 -ar 44100 -c:a libvorbis -q:a 4 menu.ogg`).

## Compilar

Requiere Java 17.

```bash
./gradlew build          # el .jar queda en build/libs/
./gradlew runClient      # abre el juego de pruebas con el mod
```

GitHub Actions (`.github/workflows/tf-client.yml`) lo compila en cada cambio y deja el `.jar` en los artefactos de la
ejecución (`tfclient-jar`).

## Notas

- **Ventana de arranque de Forge**: Forge lee `config/fml.toml` antes de cargar ningún mod. El TF Client pone ahí
  `earlyWindowControl = false`, así que desde el **segundo arranque** ya no sale la ventana de Forge y se ve la pantalla
  de carga de TF desde el principio. Para que no salga ni en el primero, incluye en el modpack un `config/fml.toml`
  con esa línea.
- Si algo del mod fallara al dibujar o con la música, el juego no se cierra: se usa la pantalla normal y el error queda
  en `logs/latest.log` con el prefijo `TF Client`.
- La pantalla de carga envuelve la original sin cambiar su lógica: la carga, los errores y el paso al menú siguen
  funcionando igual que en Forge.
