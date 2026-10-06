# TF Client — Tierras Fantásticas (Forge 1.20.1)

Mod de Tierras Fantásticas. En el **cliente** hace esto:

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
  - Los botones de oro tienen la misma línea luminosa del emblema, pero más suave: justo después de que el destello
    cruza el emblema, pasa en cascada por el botón del servidor, Web y Discord.
  - Sin textos de Mojang, versión, Forge ni Realms. Idioma y accesibilidad siguen en *Opciones*.
- **Música de TF** en la pantalla de carga y el menú: empieza en cuanto arranca el juego, suena en bucle con fundidos
  y respeta los volúmenes *General* y *Música* de las opciones. Se apaga con un fundido al entrar a un mundo o al
  servidor (ahí vuelve la música normal de Minecraft) y vuelve a sonar al regresar al menú.
- **Tema de TF en todos los menús fuera de una partida** (mundos, opciones, conexión...): botones, botones de opciones
  y deslizadores con la placa azul noche y oro y la letra Cinzel; fondo con el paisaje oscurecido y las listas
  (mundos, paquetes...) en un panel azul noche con filetes dorados, en vez de la tierra. **Dentro de una partida no se toca nada**, así cada jugador ve sus paquetes de
  recursos.

Y en el **servidor** (opcional) hace de **puente con la web**:

- Cada 10 segundos manda a la web los jugadores conectados (la web los muestra en vivo) y los que han entrado alguna
  vez (nombre + UUID, de `usercache.json` y los datos del mundo). Así la tienda comprueba el nombre **antes de cobrar**
  y solo vende a jugadores que existen en el servidor.
- Recibe las compras de los jugadores que están dentro **por UUID**: aunque alguien se cambie el nombre o use uno
  parecido, la compra llega al que pagó. Los comandos de la tienda llevan `{player}` y `{uuid}`, que se rellenan al
  entregar con el nombre actual del jugador.
- Al entregar, **todo el servidor se entera**: un anuncio enmarcado en el chat con el color del producto (y el rango
  nuevo si lo es), un sonido para todos, fuegos artificiales del mismo color alrededor del comprador (solo si tiene el
  cielo encima, para que no hagan daño) y un título de *¡GRACIAS!* en su pantalla. El enlace a la tienda se puede pulsar.
- **Rangos en el nametag**: el prefijo del rango (`[DRAGÓN]`, `[REY]`...) con su color delante del nombre, encima de la
  cabeza, en la lista de jugadores (Tab, los rangos altos primero) y en el chat. Usa equipos del marcador vanilla, así
  que funciona sin plugins; a un jugador que ya está en el equipo de otro sistema no se le toca.
- Si el comprador no está conectado, la compra espera en la web y se le da en cuanto entra.
- Es el servidor el que llama a la web: no hace falta RCON ni abrir puertos. Cada entrega tiene un número y el puente
  apunta las que ya ejecutó (`config/tfclient-bridge-entregas.txt`), así que nunca entrega dos veces.

No necesita Fabric API, GeckoLib ni ningún otro mod: solo Forge.

## Sets de Tierras Fantásticas (objetos del juego)

El mod añade los **41 sets** de la tienda (859 objetos, los mismos que enseña la web: las 40 crates y las 30 armas de
la ruleta de Nazgul) con sus modelos 3D y texturas
animadas: armas, herramientas, arcos y ballestas (se tensan con sus propias animaciones), cañas, escudos, tridentes,
armaduras completas (la de Pequeño Unicornio, animada también puesta), cascos y sombreros que se ponen en la cabeza, y
alas, mochilas, capas y colas que se ven en la espalda. Van en el hueco del pecho o, con los mods Accessories o
Curios (el que usa Artifacts), en el hueco de la espalda, que deja el pecho libre para la pechera.

- No tienen receta: se sacan del **modo creativo** (pestaña *Tierras Fantásticas · Sets*) o con el comando.
- Con el mod **Better Combat**, cada arma usa la plantilla que le toca por su forma (espada, mandoble, daga, lanza,
  guadaña, bastón, alabarda, hacha, martillo, maza, puño...) y hace sus combos encadenando clics. Arcos, ballestas,
  cañas, escudos, palas y azadas se quedan como en Minecraft.
- Valores de netherita (daño, durabilidad, armadura) y no se queman en lava.
- Hace falta el TF Client **en el servidor y en todos los jugadores**, con la misma versión (Forge lo comprueba al
  conectar). Funciona en servidores Forge y Mohist.

### Comando `/tf` (operadores, nivel 2)

```
/tf web sets list                              lista los sets
/tf web sets give <jugadores> <set>            da el set entero
/tf web sets give <jugadores> <set> <objeto>   da un objeto del set (p. ej. /tf web sets give Steve necros sword)
```

Con el puente activo, además:

```
/tf vincular <código>              (todos) une tu jugador con tu cuenta de la web; el código sale en «Mi cuenta»
/tf rango <jugador>                (staff, nivel 3) muestra su rango
/tf rango <jugador> <rango>        (staff, nivel 3) pone el rango: grupo de LuckPerms, nametag y web
/tf rango <jugador> ninguno        (staff, nivel 3) se lo quita (p. ej. tras un reembolso)
```

Tienda de monedas, monedas y oficios (más abajo):

```
/tf tienda                         (todos) abre la tienda de monedas
/tf tienda add <precio> [nombre]   (staff, nivel 3) vende lo que tienes en la mano, con esa cantidad
/tf tienda precio <id> <precio>    (staff) cambia el precio
/tf tienda quitar <id> | lista | vaciar
/tf monedas                        (todos) tus monedas
/tf monedas dar|quitar|poner <jugador> <cantidad>   (staff, nivel 3)
/tf jobs                           (todos) menú de oficios
/tf jobs ver|unirse <oficio>, /tf jobs abandonar
/tf jobs recargar                  (staff) vuelve a leer config/tfclient-jobs.json
/tf jobs nivel|xp <jugador> <oficio> <n>, /tf jobs reiniciar <jugador> [oficio]   (staff)
```

Los nombres de set y de objeto se autocompletan con Tab. Los ids de los objetos son `tfclient:<set>_<objeto>`
(por ejemplo `tfclient:valentine_sword`), por si los usa un plugin de crates.

### Regenerar los objetos

`tools/build_mod_items.py <carpeta con los packs descomprimidos>` copia modelos, texturas, animaciones y armaduras de
los packs (los sets y sus nombres salen de `tierras-fantasticas/tools/build_items.py` y `crates.py`, igual que en la
web) y `tools/check_mod_items.py` revisa que todo cumpla las reglas de Minecraft 1.20.1.
`tools/build_jobs_gui.py <carpeta>` prepara los fondos, la barra de botones y los iconos de los oficios (pack *Medieval
Jobs*): quita los textos en inglés del cartel y de la cinta, que el mod escribe en español.

Lo que va puesto en la espalda sale del modelo que el propio pack usa en HMCCosmetics (`tools/hmc_worn.py`), que puede
no ser el del inventario, y se coloca por su forma: alas a la altura de los hombros, mochilas y carcaj sin tapar la
cabeza (el carcaj en diagonal), todo pegado a la espalda. Los planos sin grosor se recortan a lo que se ve para que no
salgan rayas sueltas en los bordes.

## Monedas

La tienda de monedas y los oficios pagan y cobran con la economía que diga `economy.mode` en
`config/tfclient-server.properties`:

| `economy.mode` | De dónde salen las monedas |
| -------------- | -------------------------- |
| `auto` (por defecto) | Vault si está (servidor Mohist con EssentialsX, CMI…); si no, las del TF Client |
| `vault`        | La economía del servidor a través de Vault |
| `tf`           | Monedas propias del TF Client, guardadas en `<mundo>/tfclient/monedas.json`; se ven con `/tf monedas` |
| `comandos`     | `economy.give` / `economy.take` (por defecto `eco give/take {player} {amount}`); el de quitar tiene que fallar si no hay bastantes |

`economy.currency` es el nombre de la moneda en los mensajes (`monedas`). Si usas `tf`, los productos de monedas de la
web tienen que dar con `tf monedas dar {player} <cantidad>` en vez de `eco give`.

## Tienda de monedas

El staff pone a la venta lo que tiene en la mano con `/tf tienda add <precio> [nombre]` (el objeto exacto: cantidad,
encantamientos, nombre y NBT). Los jugadores compran con `/tf tienda` (clic y otro clic para confirmar). Se guarda en
`config/tfclient-tienda.json` y, con el puente activo, la pestaña *Tienda de monedas* de la web se actualiza sola en
unos segundos (al conectar se le manda la tienda entera: el servidor es el que manda).

## Oficios (`/tf jobs`)

Diez oficios con el arte del pack *Medieval Jobs*: granjero, minero, leñador, excavador, pescador, cazador, alquimista,
herrero, constructor y encantador. La ventana es propia del TF Client y no enseña el inventario del jugador: arriba el
marco de madera con el dibujo del oficio y su rejilla de 5×2 (los oficios en el menú principal, las misiones en el de
cada oficio), con el nombre en español en la cinta; debajo, aparte, una barra de 9 botones (volver, nivel, páginas,
cómo se gana, monedas, recompensas, unirse o abandonar y cerrar). Nada va encima del dibujo.

- Se elige un oficio y se gana experiencia del oficio y monedas haciendo su trabajo (las monedas se pagan juntas cada
  `pagoCadaSegundos`, con un aviso en la barra de acción). Cada nivel paga más (`bonusMonedasPorNivel`).
- Misiones por nivel (diarias, repetibles o de una vez) que se cobran con un clic: monedas, experiencia, objetos o
  comandos. Subir de nivel también da monedas y, en los hitos, premios.
- *Abandonar oficio* vuelve al menú de selección y **no pierde nada**: el nivel y las misiones se guardan por si vuelve.
  `esperaCambioMinutos` evita cambiar de oficio a cada rato.
- Antitrampas: romper lo que uno mismo ha colocado (o colocar en el mismo sitio otra vez) no cuenta, los cultivos solo
  cuentan maduros, los monstruos de generadores no cuentan (`generadoresCuentan`) y en creativo no se gana nada.
- Todo está en `config/tfclient-jobs.json` (se crea la primera vez, en español): oficios, nombres, colores, fondos,
  acciones que pagan (`romper`, `cosechar`, `colocar`, `matar`, `pescar`, `fabricar`, `fundir`, `preparar`,
  `encantar`, `reparar`, `criar`, con ids, etiquetas `#...` o `*`), misiones, niveles y recompensas. Se aplica sin
  reiniciar con `/tf jobs recargar`. El progreso está en `<mundo>/tfclient/oficios.json`.

## Instalar

1. Instala **Forge 1.20.1** (47.x).
2. Copia `tfclient-1.20.1-1.3.1.jar` en la carpeta `mods` (del juego y, para el puente, también del servidor).

### Puente en el servidor

1. Sube el jar a la carpeta `mods` del servidor y reinícialo.
2. Se crea `config/tfclient-server.properties`:
   ```properties
   bridge.enabled=true
   bridge.url=https\://xn--tierrasfantsticas-hpb.store
   bridge.secret=(clave aleatoria)
   bridge.interval=10
   broadcast.enabled=true
   broadcast.fireworks=true
   ranks.nametag=true
   economy.mode=auto
   economy.give=eco give {player} {amount}
   economy.take=eco take {player} {amount}
   economy.currency=monedas
   ```
   `bridge.url` es `tierrasfantásticas.store` escrito como lo usa internet. `broadcast.enabled=false` quita el anuncio
   a todos (el comprador sigue recibiendo su mensaje), `broadcast.fireworks=false` los fuegos artificiales y
   `ranks.nametag=false` el prefijo del rango (por si ya lo pone otro plugin, como TAB).
3. Copia `bridge.secret` en Cloudflare (*Workers & Pages → tierras-fantasticas → Settings → Variables and Secrets*)
   como *Secret* `BRIDGE_SECRET`. En la consola del servidor saldrá `TF Bridge: conectado con la web`.

## Cambiar el servidor

La primera vez que se abre el juego se crea `config/tfclient.properties`:

```properties
server.name=Tierras Fantásticas
server.address=216.163.187.40\:19229
web.url=https\://xn--tierrasfantsticas-hpb.store
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
- Efecto del emblema: `tools/gen_logo_fx.py` genera `logo_glow.png`, `logo_shine.png`, `sparkle.png` y
  `button_shine.png` (la línea luminosa de los botones de oro) a partir de `logo.png` (vuelve a ejecutarlo si cambias el emblema).
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
