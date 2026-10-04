# TF Client — Tierras Fantásticas (Forge 1.20.1)

Mod de cliente de Tierras Fantásticas. Hace solo esto:

- **Pantalla de carga de TF**: el banner de Tierras Fantásticas (con las letras) a pantalla completa y la barra de
  progreso de Minecraft. Sustituye a la pantalla de Mojang/Forge, al abrir el juego y al recargar recursos (F3+T).
  Se queda opaca hasta que la original termina y después se funde sola sobre el menú, así nunca asoma el rótulo rojo.
- **Menú principal de TF**, como en TierrasMon: el paisaje de TF de fondo (sin letras), el emblema TF encima de los
  botones y botones propios con la fuente Cinzel: placa con las puntas en ángulo, azul noche (como el zafiro de la
  corona del logo) con doble filete dorado y rombos en los extremos; el botón del servidor, en oro con letras oscuras:
  - **TIERRAS FANTÁSTICAS**: antes de conectar pregunta al servidor qué mods usa. Si te falta alguno, enseña la lista
    (con *Volver* o *Entrar igual*); si no falta nada, conecta directamente y acepta su paquete de recursos.
  - **Mundo local**, **Mods**, **Opciones** y **Salir**.
  - Sin textos de Mojang, versión, Forge ni Realms. Idioma y accesibilidad siguen en *Opciones*.
- **Música de TF** en la pantalla de carga y el menú: empieza en cuanto arranca el juego, suena en bucle con fundidos
  y respeta los volúmenes *General* y *Música* de las opciones. Se apaga con un fundido al entrar a un mundo o al
  servidor (ahí vuelve la música normal de Minecraft) y vuelve a sonar al regresar al menú.
- Fuera de una partida, el resto de menús (opciones, mundos, conexión) usan el paisaje oscurecido en vez del fondo de tierra.

No necesita Fabric API, GeckoLib ni ningún otro mod: solo Forge. No hace falta instalarlo en el servidor.

## Instalar

1. Instala **Forge 1.20.1** (47.x).
2. Copia `tfclient-1.20.1-1.0.4.jar` en la carpeta `mods`.

## Cambiar el servidor

La primera vez que se abre el juego se crea `config/tfclient.properties`:

```properties
server.name=Tierras Fantásticas
server.address=216.163.187.40\:19001
```

`server.name` es el texto del botón y `server.address` la IP (con puerto) a la que conecta.

## Cambiar las imágenes

- Pantalla de carga: `src/main/resources/assets/tfclient/textures/gui/loading_background.png` (16:9; se recorta para
  cubrir la pantalla).
- Fondo del menú: `src/main/resources/assets/tfclient/textures/gui/menu_background.png` (16:9).
- Emblema: `src/main/resources/assets/tfclient/textures/gui/logo.png` (PNG con transparencia).
- Icono en la lista de mods: `src/main/resources/tfclient_logo.png`.
- Botones: `tools/gen_buttons.py` genera `button_primary.png`, `button_wide.png` y `button_half.png` (3 estados
  apilados: normal, ratón encima, desactivado; 4 píxeles por píxel de interfaz).
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
