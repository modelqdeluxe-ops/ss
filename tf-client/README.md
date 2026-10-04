# TF Client — Tierras Fantásticas (Forge 1.20.1)

Mod de cliente de Tierras Fantásticas. Hace solo esto:

- **Pantalla de carga de TF**: el banner del servidor de fondo, el emblema TF flotando y una barra de progreso dorada.
  Sustituye a la pantalla roja de Mojang, tanto al abrir el juego como al recargar recursos (F3+T o al cambiar
  paquetes de recursos).
- **Menú principal de TF**: el mismo banner con un zoom lento y un poco de movimiento con el ratón, y cuatro botones:
  - **Jugar en Tierras Fantásticas**: conecta directamente al servidor y acepta su paquete de recursos sin preguntar,
    para que se vean los modelos animados.
  - **Mundo local**: abre la lista de mundos para crear o jugar uno en local (si no hay ninguno, abre directamente
    la creación de mundo).
  - **Opciones** y **Salir del juego**.
- **Música de TF** en la pantalla de carga y el menú: empieza en cuanto arranca el juego, suena en bucle con fundidos
  y respeta los volúmenes *General* y *Música* de las opciones. Se apaga con un fundido al entrar a un mundo o al
  servidor (ahí vuelve la música normal de Minecraft) y vuelve a sonar al regresar al menú.
- Fuera de una partida, el resto de menús (opciones, mundos, conexión) usan el banner oscurecido en vez del fondo de tierra.

No necesita Fabric API, GeckoLib ni ningún otro mod: solo Forge. No hace falta instalarlo en el servidor.

## Instalar

1. Instala **Forge 1.20.1** (47.x).
2. Copia `tfclient-1.20.1-1.0.0.jar` en la carpeta `mods`.

## Cambiar el servidor

La primera vez que se abre el juego se crea `config/tfclient.properties`:

```properties
server.name=Tierras Fantásticas
server.address=216.163.187.40\:19001
```

`server.name` es el texto del botón y `server.address` la IP (con puerto) a la que conecta.

## Cambiar las imágenes

- Fondo: `src/main/resources/assets/tfclient/textures/gui/background.png` (16:9; se recorta para cubrir la pantalla).
- Emblema: `src/main/resources/assets/tfclient/textures/gui/logo.png` (PNG con transparencia).
- Icono en la lista de mods: `src/main/resources/tfclient_logo.png`.
- Música: `src/main/resources/assets/tfclient/music/menu.ogg` (OGG Vorbis; para convertir un MP3:
  `ffmpeg -i musica.mp3 -vn -ac 2 -ar 44100 -c:a libvorbis -q:a 4 menu.ogg`).

## Compilar

Requiere Java 17.

```bash
./gradlew build          # el .jar queda en build/libs/
./gradlew runClient      # abre el juego de pruebas con el mod
```

GitHub Actions (`.github/workflows/tf-client.yml`) lo compila en cada cambio y deja el `.jar` en los artefactos de la
ejecución (`tfclient-jar`). También arranca el juego en una pantalla virtual y guarda capturas de la pantalla de carga y
del menú (`tfclient-capturas`).

## Notas

- Antes de la pantalla de carga, Forge muestra unos segundos su propia ventana de arranque (barras de progreso sobre
  fondo oscuro). Esa ventana sale antes de que se cargue ningún mod, así que no se puede personalizar desde un mod.
- La pantalla de carga envuelve la original sin cambiar su lógica: la carga, los errores y el paso al menú siguen
  funcionando igual que en Forge.
