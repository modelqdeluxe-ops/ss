# 🏰 Tierras Fantásticas — Web y tienda del servidor

Página web del servidor de Minecraft **Tierras Fantásticas** con tienda y pasarela de pago real. Funciona en
**Cloudflare Workers** (gratis, siempre encendida) con su base de datos **D1**, en https://tierrasfantásticas.store:

- Web por secciones con una página para cada una. Diseño oscuro con oro (fuentes Cinzel y Manrope) sobre un fondo de
  aurora animada, cabecera de cristal flotante y una portada ligera (el emblema animado, la IP y cuatro crates). Las
  crates tienen un aura de su color que late y destellos que parpadean alrededor. Pensado primero para el móvil: menú a pantalla completa en el teléfono, sin partículas ni efectos pesados
  (solo animaciones de `transform`/`opacity`, que se apagan con «reducir movimiento»):
  - **Inicio** (`/`): el emblema animado, la IP (clic para copiar), el estado del servidor en vivo y cuatro crates.
  - **Tienda** (`/tienda`), con buscador y pestañas (`/tienda#crates` abre una directamente): **Recompensas gratis**,
    **Rangos**, **Crates**, **Ruleta**, **Monedas** y **Tienda de monedas**.
    - **Crates**: cada crate es un set completo (armas, herramientas, armadura y cosméticos) y se compra entera, sin
      llaves. Al tocarla se abre el **probador** (`/tienda#crates-necros`): tu personaje en 3D con tu skin y el set
      puesto, tal como lo coloca el TF Client en el juego; al tocar una pieza se ve suelta en 3D y se puede
      **equipar** o quitar.
    - **Ruleta**: 1, 5 o 10 giros. Cada giro da un premio al azar con su probabilidad a la vista (monedas, diamantes,
      netherita, tótems… y un 5% de que sea un arma legendaria de Nazgul). La tienda lo elige al confirmarse el pago y
      la página de la compra dice qué tocó. Los premios y probabilidades están en `ROULETTE_POOL` de `tools/crates.py`.
      También se gira con las monedas del servidor (`ROULETTE_COIN_PRICE` por giro): con la cuenta iniciada, la web
      manda la tirada al juego, el TF Client cobra las monedas y devuelve los premios, que salen en la página de la
      tirada. En el juego es `/tf ruleta`.
    - **Tienda de monedas**: objetos del servidor que se compran con monedas del juego. La llena el staff desde el
      juego (`/tf tienda add <precio>`) y la web se actualiza sola.
  - `/crates` lleva a la pestaña de crates (enlaces antiguos).
  - Rangos con escudos animados y una tabla para comparar sus ventajas (`/tienda#rangos`).
  - **El mundo** (`/mundo`) y **Ayuda** (`/ayuda`).
- **Pago con Stripe**: la página de pago segura de Stripe (tarjeta, Apple Pay, Google Pay, Link). Los datos bancarios
  nunca pasan por la web y el dinero llega a tu cuenta de Stripe. Stripe avisa de cada pago con un webhook firmado.
- **Nombre comprobado antes de cobrar**: al escribir el nombre, la tienda lo busca en la lista de jugadores que manda
  el servidor y enseña su cabeza, si está conectado y su rango. Solo se vende a jugadores que han entrado alguna vez, y
  la compra se guarda con su **UUID**: llega al que pagó aunque se cambie el nombre.
- **Rangos con mejora**: si el jugador ya tiene un rango, al comprar uno superior paga solo la diferencia; el mismo
  rango o uno inferior no se pueden comprar. Al entregar se quitan los grupos de los rangos inferiores.
- **Cuentas de jugador** (`/cuenta`, botón «Entrar» de la cabecera): cada jugador crea su cuenta en la web con su
  **nombre de Minecraft y una contraseña**. El nombre se comprueba con el puente (tiene que haber entrado al servidor)
  y la cuenta queda unida a su UUID; un jugador, una cuenta. Dentro del juego no hay que hacer nada. La sesión dura 60
  días y se renueva en cada visita. Contraseñas con PBKDF2 y freno tras 8 intentos fallidos.
- **Discord conectado a la cuenta**: botón «Conectar Discord» → Discord le dice a la web su @ (no se escribe a mano, así
  nadie puede poner el de otro) y si está en el servidor de Discord de Tierras Fantásticas. Si no está y hay bot, lo
  mete; si no, le ofrece unirse y volver a comprobar. Después puede entrar a la web con Discord sin contraseña.
- **Entrega automática con el puente del TF Client**: el mod TF Client instalado en el servidor de Minecraft pregunta a
  la web cada 10 segundos si hay compras y ejecuta sus comandos (`lp`, `eco`, `crate`...) en cuanto el comprador está
  conectado, con un **anuncio para todo el servidor** (mensaje enmarcado, sonido y fuegos artificiales del color de la
  compra) y el **prefijo del rango en el nametag**, la lista de jugadores y el chat. Sin RCON ni puertos abiertos: es
  el servidor el que llama a la web.
- **Jugadores en vivo**: el puente manda los jugadores conectados y la web los muestra al momento.
- **Discord conectado**: el comprador inicia sesión con Discord (o tiene su jugador vinculado). Al confirmarse el pago, un
  bot le da el rol de su rango (y lo mete en el servidor de Discord si aún no estaba) y se anuncia la compra en un canal.
- Página de confirmación que muestra el estado del pago y de la entrega en el juego y en Discord.
- Pedidos y entregas guardados en la base de datos D1. Si el comprador no está conectado, la compra espera y se le
  entrega al entrar. Si un comando falla, el pedido queda como `delivery_failed` con el error para que el staff lo revise.
  Los reembolsos y disputas de Stripe quedan apuntados en el pedido (`refunded`); el rango se quita con `/tf rango <jugador> ninguno`.

## Cómo está todo conectado

```
 Servidor (TF Client) ──► Web: jugadores (nombre + UUID), entregas hechas, /tf vincular
 Jugador ──► Web: escribe "steve" ──► ✓ Steve (UUID del servidor) ──► Stripe (paga) ──► webhook firmado
                                                                                          │
      ┌───────────────────────────────────────────────────────────────────────────────────┤
      ├─► Cola de entregas por UUID ◄── puente cada 10 s: lp user {player} parent add hechicero
      │        └─► anuncio en el chat para todos + fuegos artificiales + [HECHICERO] en el nametag
      ├─► Discord (bot): rol "Hechicero" a su cuenta (sesión iniciada o jugador vinculado)
      └─► Discord (webhook): "👑 ¡Steve ahora es Hechicero!"
```

Todo pasa en segundos y sin intervención del staff. Si una parte falla (el servidor está apagado, el bot no tiene
permisos…), el pago queda registrado en la base de datos con lo que falta por entregar y el error en el log.

## Puesta en marcha (Cloudflare, gratis)

1. Crea una cuenta en <https://dash.cloudflare.com> y añade tu dominio (plan **Free**). En Hostinger cambia los
   *nameservers* del dominio por los dos que te da Cloudflare.
2. En Cloudflare: **Workers & Pages → Create → Import a repository** → conecta GitHub y elige este repositorio.
   - *Project name*: `tierras-fantasticas`
   - *Build command*: vacío. *Deploy command*: `npx wrangler deploy` (lo que viene por defecto: la configuración
     `wrangler.jsonc` está en la raíz del repositorio y apunta a esta carpeta)
   La base de datos D1 se crea sola en el primer despliegue. Cada cambio en `main` se publica solo.
3. **Settings → Domains & Routes → Add → Custom domain**: `tierrasfantásticas.store` (y `www.` si quieres).
   Cloudflare crea el registro DNS y el certificado HTTPS.
4. **Settings → Variables and Secrets**: añade como *Secret* las claves de Stripe, Discord y `BRIDGE_SECRET`
   (ver abajo). Las variables públicas (nombre, IP, moneda) están en `wrangler.jsonc` (raíz del repositorio).
   Las claves **nunca** van en el repositorio ni se comparten por chat.

Para probarla en tu PC (Node.js 22.13 o superior):

```bash
cd tierras-fantasticas
npm install
cp .dev.vars.example .dev.vars   # y rellena los valores
npm run dev                      # http://localhost:8787
```

### 1. Stripe

1. Crea tu cuenta en <https://dashboard.stripe.com> y activa el **modo de prueba** (interruptor *Test mode*).
2. **Developers → API keys**: copia la *Secret key* (`sk_test_…`) en Cloudflare como *Secret* `STRIPE_SECRET_KEY`.
   La *Publishable key* no hace falta: el pago se hace en la página de Stripe.
3. **Developers → Webhooks → Add endpoint**: `https://xn--tierrasfantsticas-hpb.store/webhook/stripe` con los eventos
   `checkout.session.completed`, `checkout.session.async_payment_succeeded`, `checkout.session.async_payment_failed`,
   `checkout.session.expired`, `charge.refunded` y `charge.dispute.created`. Copia su *Signing secret* (`whsec_…`) en
   Cloudflare como *Secret* `STRIPE_WEBHOOK_SECRET`. El webhook es lo que confirma los pagos: sin él, la página de
   confirmación pregunta a Stripe como respaldo, pero no te enterarás de reembolsos ni disputas.
4. Prueba una compra con la tarjeta `4242 4242 4242 4242` (cualquier fecha futura y CVC). No se cobra dinero real.
5. Elige la moneda en `CURRENCY` (`USD`, `EUR`, `MXN`, `COP`, `CLP`, `PEN`, `ARS`…: Stripe las admite casi todas; los
   precios de `products.json` están en céntimos y la web los convierte para las monedas sin decimales como `CLP`).
6. Cuando todo funcione, desactiva el modo de prueba, completa los datos de tu negocio en Stripe y repite los pasos 2 y
   3 en **Live** (`sk_live_…` y un webhook nuevo en Live con su propio `whsec_…`).

#### Cómo funciona el pago

1. El jugador elige producto y escribe su nombre. La web lo busca en los jugadores que ha mandado el servidor
   (`GET /api/player/<nombre>`) y enseña su cabeza, su rango y el precio (el de mejora si ya tiene un rango).
2. Al pulsar *Pagar*, la web crea la sesión de pago en Stripe con el precio del catálogo (nadie puede cambiarlo desde el
   navegador) y guarda el pedido con el UUID del jugador. El comprador paga en la página de Stripe.
3. Stripe avisa con el webhook firmado. La web comprueba la firma, que el importe y la moneda coinciden, y deja la
   compra en la cola del puente. Si el webhook se retrasa, la página de confirmación (`/success`) le pregunta a Stripe.
4. Un pago avisado dos veces (webhook repetido, página recargada) nunca se entrega dos veces.

### 2. Puente con el servidor de Minecraft (TF Client)

1. Sube el jar del **TF Client** a la carpeta `mods` del servidor (en Ultra Servers, desde el gestor de archivos del
   panel) y reinicia. No obliga a los jugadores a tenerlo instalado.
2. Al arrancar, el mod crea `config/tfclient-server.properties` con una clave aleatoria en `bridge.secret` y la
   dirección de la web en `bridge.url`.
3. Copia esa clave en Cloudflare como *Secret* `BRIDGE_SECRET` y reinicia el servidor. En la consola verás
   `TF Bridge: conectado con la web`.

Cada 10 segundos el servidor manda a `POST /bridge/poll` sus jugadores conectados, los que han entrado alguna vez (de
`usercache.json`, para que la tienda pueda comprobar nombres), las entregas que ya hizo, los `/tf vincular` y los
cambios de rango del staff (`/tf rango`). Recibe las compras pendientes de los jugadores que están dentro y el rango de
cada uno para su nametag. Si no confirma una entrega en 2 minutos, se le vuelve a enviar (el mod recuerda las que ya
ejecutó, así que nunca entrega dos veces).

> El comando `/tf vincular` del TF Client 1.2.8 ya no hace falta: las cuentas se crean en la web.
>
> Con el TF Client 1.2.7 o anterior en el servidor, la web sigue entregando (les manda los comandos con el nombre ya
> puesto), pero sin anuncio, sin nametag ni vinculación. Actualiza el mod del servidor a la 1.2.8.

### 3. Discord

Discord es opcional: si no lo configuras, la tienda y las cuentas funcionan igual, solo que sin «Conectar Discord»,
roles ni anuncios. Para que los jugadores conecten su Discord y se compruebe que están en tu servidor bastan los pasos
1, 2 y `DISCORD_GUILD_ID` (ya puesto en `wrangler.jsonc`); el bot (pasos 3-5) sirve para meterlos al servidor si no
están, darles los roles y volver a comprobarlo sin pasar por Discord.

1. **Crea la aplicación**: en <https://discord.com/developers/applications> → *New Application* (p. ej. "Tierras Fantásticas").
2. **OAuth2** (pestaña *OAuth2*): copia el *Client ID* y el *Client Secret* en Cloudflare como *Secrets*
   `DISCORD_CLIENT_ID` y `DISCORD_CLIENT_SECRET`. En *Redirects* añade exactamente la dirección de la web + `/auth/discord/callback`:
   `https://xn--tierrasfantsticas-hpb.store/auth/discord/callback` (es `tierrasfantásticas.store` escrito como lo
   guarda internet; si usas `PUBLIC_URL`, la misma con ese dominio).
3. **Bot** (pestaña *Bot*): pulsa *Reset Token* y copia el token en Cloudflare como *Secret* `DISCORD_BOT_TOKEN`. No compartas nunca este token.
4. **Invita el bot a tu servidor** abriendo esta URL (cambia `TU_CLIENT_ID`):
   `https://discord.com/oauth2/authorize?client_id=TU_CLIENT_ID&scope=bot&permissions=268435457`
   (permisos *Gestionar roles* y *Crear invitación*, necesarios para dar roles y añadir jugadores al servidor).
5. **Orden de roles**: en *Ajustes del servidor → Roles*, arrastra el rol del bot **por encima** de los roles de rango
   (Aventurero, Hechicero, Dragón). Discord no deja a un bot dar roles que estén por encima del suyo.
6. **IDs**: activa *Ajustes de usuario → Avanzado → Modo desarrollador*. El ID del servidor ya está en `wrangler.jsonc`
   (`DISCORD_GUILD_ID`, el mismo que usa el TF Client); si cambias de servidor, clic derecho en él → *Copiar ID*. Clic derecho en cada rol → *Copiar ID* para ponerlo en `config/products.json`:
   ```json
   "discordRoles": ["123456789012345678"]
   ```
7. **Anuncios**: en el canal donde quieras anunciar las compras, *Editar canal → Integraciones → Webhooks → Nuevo webhook*,
   y copia su URL en `DISCORD_WEBHOOK_URL`.
8. `SESSION_SECRET` es opcional: si no lo pones, la web crea su propia clave la primera vez y la guarda en la base de datos.

> **Alternativa en el lado de Minecraft:** si ya usas el plugin **DiscordSRV** con la sincronización de grupos
> (`GroupRoleSynchronizationGroupsAndRolesToSync`), los jugadores que vincularon su cuenta con `/discord link` reciben el
> rol automáticamente cuando LuckPerms les da el rango. Puedes usar las dos cosas a la vez: la tienda da el rol al
> instante y DiscordSRV lo mantiene sincronizado (por ejemplo, si el rango caduca o se lo quitas).

### 4. Productos

Edita `config/products.json`. Cada producto tiene:

| Campo         | Descripción                                                      |
|---------------|------------------------------------------------------------------|
| `id`          | Identificador único                                              |
| `category`    | `gratis`, `rangos`, `crates`, `ruleta` o `monedas` (las pestañas de la tienda) |
| `price`       | Precio en **céntimos** (`499` = 4,99)                            |
| `maxQuantity` | Opcional. Cantidad máxima por compra (por defecto 10; rangos = 1)|
| `discordRoles`| Opcional. IDs de los roles de Discord que se dan al comprar      |
| `commands`    | Comandos que ejecuta el servidor; `{player}` es el nombre actual del jugador y `{uuid}` su UUID |
| `image`       | Opcional. Imagen de la tarjeta (`img/ranks/…`, `img/gifts/…`, `img/crates/…`)     |
| `perks`       | Opcional. Lista corta de ventajas que se ve en la tarjeta                        |
| `tier`, `specs` | Rangos: orden y filas de la tabla comparativa (`true`/`false` o un texto)      |
| `rank`        | Rangos: `group` (grupo de LuckPerms), `prefix` (texto del nametag), `color` (color de Minecraft) y `hex` (el de la web y el anuncio) |

#### Crates

Las crates (`"category": "crates"`) las escribe `tools/crates.py` y llevan además:

| Campo         | Descripción                                                                      |
|---------------|----------------------------------------------------------------------------------|
| `theme`, `set`| Id del set (ancla de la URL: `/tienda#crates-necros`)                              |
| `colors`      | Sus dos colores (claro, intenso)                                                   |
| `tagline`     | Frase corta bajo el nombre                                                        |
| `image`       | Portada del set en `public/img/crates/`                                           |
| `models`      | Todas las piezas del set (`id`, `name`): miniaturas, visor 3D y probador           |

Cada crate se compra una vez (`maxQuantity: 1`) y entrega el set entero con `tf web sets give {player} <set>` (TF
Client). La ruleta (`"category": "ruleta"`, campos `spins` y `pool`) no lleva comandos: al pagar, la tienda elige un
premio por giro según su `chance` y manda sus comandos (`give`, `tf monedas dar`, o `tf web sets give {player} nazgul
<arma>` si toca un arma).

Los comandos de ejemplo usan LuckPerms (`lp`) y EssentialsX (`eco`). Ajústalos a los plugins de tu servidor.
Ajústalos a los plugins de tu servidor. Los precios siempre se leen del servidor, así que nadie puede cambiarlos desde el navegador.

## Crates y visor 3D

Cada crate enseña todos sus objetos (armas, herramientas, alas, cascos y las 4 piezas de armadura) en el probador
(`public/wardrobe.js`), que pone al jugador (su skin sale de `/api/skin/<nombre>`) con las piezas en el mismo sitio que
el TF Client: en la mano, en la cabeza y en la espalda. Los modelos del probador salen del mod ya convertidos con
`python3 tools/wear_export.py` (después de `tf-client/tools/build_mod_items.py`), a `public/wear/`.
En la ruleta, al tocar un arma se abre un visor 3D (`public/viewer.js`, con three.js en `public/vendor/`) que gira el modelo real del
pack con sus texturas animadas. Los datos salen de los packs con tres scripts (necesitan Pillow y numpy):

```bash
python3 tools/build_items.py <carpeta con los packs descomprimidos> [set ...]   # modelos y miniaturas
python3 tools/compose_cover.py <carpeta con los packs> [set ...]                 # portada igual para todos
python3 tools/crates.py                                                          # escribe los crates en products.json
```

Para añadir un pack nuevo: súmalo a `SETS` en `tools/build_items.py` (ruta a su carpeta `assets` y espacio de
nombres), añade su línea en `tools/crates.py` (nombre, frase, descripción, precio y colores) y ejecuta
los tres scripts (y `wear_export.py` tras regenerar el mod). Las páginas comunes (cabecera, pie…) se generan con `python3 tools/pages.py`.

## Pruebas

```bash
npm test
```

Las pruebas usan una API de Stripe simulada (con webhooks firmados como los de Stripe), una base de datos D1 de prueba
(SQLite) y un puente simulado. Comprueban el registro de jugadores y los cambios de nombre, que solo se vende a jugadores
conocidos, que el precio sale del catálogo, que un pago se entrega una sola vez y solo al UUID que pagó (no a otro con el
mismo nombre), que se reenvía si el servidor no confirma, que no se entrega si el importe no coincide, el pago caduca o
falla, que se rechazan firmas falsas o viejas, los pagos que tardan, reembolsos y disputas, las mejoras de rango (precio
de la diferencia, rango repetido bloqueado, grupos inferiores quitados) y los cambios de rango del staff. Con una API de
Discord simulada comprueban el login, la vinculación con `/tf vincular`, «mis compras», que se da el rol (o se añade al
servidor) también sin sesión si el jugador está vinculado, el anuncio, y que un fallo de Discord no impide la entrega.
También que una crate entrega el set entero, que la ruleta da premios de su lista (con las armas como premio raro) y los guarda en la compra, y que la
tienda de monedas se llena, cambia y vacía solo desde el puente.
