# 🏰 Tierras Fantásticas — Web y tienda del servidor

Página web del servidor de Minecraft **Tierras Fantásticas** con tienda y pasarela de pago real. Funciona en
**Cloudflare Workers** (gratis, siempre encendida) con su base de datos **D1**, en https://tierrasfantásticas.store:

- Web por secciones con pestañas horizontales y una página para cada una. Diseño oscuro con oro (fuentes Cinzel e
  Inter), pensado primero para el móvil: menú a pantalla completa en el teléfono, sin partículas ni efectos pesados
  (solo animaciones de `transform`/`opacity`, que se apagan con «reducir movimiento»):
  - **Inicio** (`/`): IP (clic para copiar), estado del servidor en vivo y los crates destacados.
  - **Tienda** (`/tienda`): rangos, crates, llaves y monedas en pestañas (`/tienda#crates` abre la pestaña directamente).
  - **Crates** (`/crates`): escaparate de cada crate con su modelo, la armadura y todo lo que contiene (`/crates#necros`).
    La **Forja de Nazgul** (`/crates#nazgul`) enseña sus diez armas renderizadas a partir de los modelos 3D del pack.
  - Rangos con escudos animados y una tabla para comparar sus ventajas (`/tienda#rangos`).
  - **El mundo** (`/mundo`) y **Ayuda** (`/ayuda`).
- **Pago con PayPal**: botones oficiales de PayPal; el comprador paga con su cuenta o con tarjeta de crédito/débito
  sin necesidad de cuenta. Los datos bancarios nunca pasan por tu servidor y el dinero llega a tu cuenta PayPal.
- **Entrega automática con el puente del TF Client**: el mod TF Client instalado en el servidor de Minecraft pregunta a
  la web cada 10 segundos si hay compras y ejecuta sus comandos (`lp`, `eco`, `crate`...) en cuanto el comprador está
  conectado. Sin RCON ni puertos abiertos: es el servidor el que llama a la web.
- **Jugadores en vivo**: el puente manda los jugadores conectados y la web los muestra al momento.
- **Discord conectado**: el comprador vincula su cuenta de Discord al comprar. Al confirmarse el pago, un bot le da el rol
  de su rango (y lo mete en el servidor de Discord si aún no estaba) y se anuncia la compra en un canal.
- Página de confirmación que muestra el estado del pago y de la entrega en el juego y en Discord.
- Pedidos y entregas guardados en la base de datos D1. Si el comprador no está conectado, la compra espera y se le
  entrega al entrar. Si un comando falla, el pedido queda como `delivery_failed` con el error para que el staff lo revise.

## Cómo está todo conectado

```
 Jugador ──► Web (tienda) ──► PayPal (paga) ──► Web confirma el cobro
                 │                                   │
                 └─ "Vincular Discord" (opcional)     ├─► Cola de entregas ◄── puente del servidor (TF Client)
                                                     │        cada 10 s:  lp user Steve parent add hechicero
                                                     ├─► Discord (bot): rol "Hechicero" al usuario vinculado
                                                     └─► Discord (webhook): "🎉 Steve ha conseguido Rango Hechicero"
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
4. **Settings → Variables and Secrets**: añade como *Secret* las claves de PayPal, Discord y `BRIDGE_SECRET`
   (ver abajo). Las variables públicas (nombre, IP, moneda, `PAYPAL_ENV`) están en `wrangler.jsonc` (raíz del repositorio).

Para probarla en tu PC (Node.js 22.13 o superior):

```bash
cd tierras-fantasticas
npm install
cp .dev.vars.example .dev.vars   # y rellena los valores
npm run dev                      # http://localhost:8787
```

### 1. PayPal

1. Entra en <https://developer.paypal.com/dashboard/applications> con tu cuenta PayPal (mejor una cuenta **Business**,
   que es gratuita) y crea una app en **Sandbox**. Copia el *Client ID* en `PAYPAL_CLIENT_ID` y el *Secret* en
   `PAYPAL_CLIENT_SECRET`, con `PAYPAL_ENV=sandbox`.
2. Prueba una compra con una de las cuentas de comprador de prueba (*Testing Tools → Sandbox Accounts*).
   No se cobra dinero real.
3. **Webhook (recomendado)**: dentro de la app, en *Webhooks*, añade `https://TU-DOMINIO/webhook/paypal` con los eventos
   `PAYMENT.CAPTURE.COMPLETED`, `PAYMENT.CAPTURE.DENIED`, `PAYMENT.CAPTURE.REFUNDED` y `PAYMENT.CAPTURE.REVERSED`.
   Copia el *Webhook ID* en `PAYPAL_WEBHOOK_ID`. Sirve para entregar los pagos que PayPal confirma más tarde
   (por ejemplo, pagos con cuenta bancaria) y para avisarte en el log de reembolsos y contracargos.
4. Elige la moneda en `CURRENCY`. PayPal admite USD, EUR, MXN, BRL, GBP, CAD y otras, pero **no** COP, CLP, PEN ni ARS;
   en esos países usa USD.
5. Cuando todo funcione, cambia a la pestaña **Live** del panel de desarrolladores, crea la app ahí, pon sus claves y
   `PAYPAL_ENV=live`, y crea también el webhook en Live.

#### Cómo funciona el pago

1. El jugador elige producto, escribe su nombre de Minecraft y pulsa el botón de PayPal.
2. El servidor crea el pedido en PayPal con el precio del catálogo (nadie puede cambiarlo desde el navegador).
3. El jugador aprueba el pago en la ventana de PayPal.
4. El servidor cobra el pedido, comprueba que el importe y la moneda cobrados coinciden y entrega la compra por RCON.
   Si el pago queda pendiente, la entrega se hace cuando llega el webhook `PAYMENT.CAPTURE.COMPLETED`.

### 2. Puente con el servidor de Minecraft (TF Client)

1. Sube el jar del **TF Client** a la carpeta `mods` del servidor (en Ultra Servers, desde el gestor de archivos del
   panel) y reinicia. No obliga a los jugadores a tenerlo instalado.
2. Al arrancar, el mod crea `config/tfclient-server.properties` con una clave aleatoria en `bridge.secret` y la
   dirección de la web en `bridge.url`.
3. Copia esa clave en Cloudflare como *Secret* `BRIDGE_SECRET` y reinicia el servidor. En la consola verás
   `TF Bridge: conectado con la web`.

Cada 10 segundos el servidor manda a `POST /bridge/poll` sus jugadores conectados y las entregas que ya hizo, y recibe
las compras pendientes de los jugadores que están dentro. Si no confirma una entrega en 2 minutos, se le vuelve a
enviar (el mod recuerda las que ya ejecutó, así que nunca entrega dos veces).

### 3. Discord

Discord es opcional: si no lo configuras, la tienda funciona igual, solo que sin roles ni anuncios.

1. **Crea la aplicación**: en <https://discord.com/developers/applications> → *New Application* (p. ej. "Tierras Fantásticas").
2. **OAuth2** (pestaña *OAuth2*): copia el *Client ID* y el *Client Secret* en `DISCORD_CLIENT_ID` y
   `DISCORD_CLIENT_SECRET`. En *Redirects* añade exactamente la dirección de la web + `/auth/discord/callback`:
   `https://xn--tierrasfantsticas-hpb.store/auth/discord/callback` (es `tierrasfantásticas.store` escrito como lo
   guarda internet; si usas `PUBLIC_URL`, la misma con ese dominio).
3. **Bot** (pestaña *Bot*): pulsa *Reset Token* y copia el token en `DISCORD_BOT_TOKEN`. No compartas nunca este token.
4. **Invita el bot a tu servidor** abriendo esta URL (cambia `TU_CLIENT_ID`):
   `https://discord.com/oauth2/authorize?client_id=TU_CLIENT_ID&scope=bot&permissions=268435457`
   (permisos *Gestionar roles* y *Crear invitación*, necesarios para dar roles y añadir compradores al servidor).
5. **Orden de roles**: en *Ajustes del servidor → Roles*, arrastra el rol del bot **por encima** de los roles de rango
   (Aventurero, Hechicero, Dragón). Discord no deja a un bot dar roles que estén por encima del suyo.
6. **IDs**: activa *Ajustes de usuario → Avanzado → Modo desarrollador*. Clic derecho en tu servidor → *Copiar ID* para
   `DISCORD_GUILD_ID`, y clic derecho en cada rol → *Copiar ID* para ponerlo en `config/products.json`:
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
| `category`    | `rangos`, `llaves` o `monedas` (las pestañas de la tienda)       |
| `price`       | Precio en **céntimos** (`499` = 4,99)                            |
| `maxQuantity` | Opcional. Cantidad máxima por compra (por defecto 10; rangos = 1)|
| `featured`    | Opcional. Lo destaca como "Más popular"                          |
| `discordRoles`| Opcional. IDs de los roles de Discord que se dan al comprar      |
| `commands`    | Comandos que ejecuta el servidor; `{player}` es el nombre del jugador |
| `image`       | Opcional. Imagen de la tarjeta (`img/ranks/…`, `img/keys/…`, `img/crates/…`)     |
| `perks`       | Opcional. Lista corta de ventajas que se ve en la tarjeta                        |
| `tier`, `specs` | Rangos: orden y filas de la tabla comparativa (`true`/`false` o un texto)      |

#### Crates

Los crates (`"category": "crates"`) llevan además estos campos, que se muestran en la página `/crates`:

| Campo         | Descripción                                                                      |
|---------------|----------------------------------------------------------------------------------|
| `theme`       | `valentine`, `necros` o `luminite`: colores del crate y ancla de la URL           |
| `rarity`      | Etiqueta de rareza ("Legendario", "Mítico"...)                                   |
| `tagline`     | Frase corta bajo el nombre                                                        |
| `image`       | Imagen del set en `public/img/crates/`                                            |
| `armor`       | Piezas de armadura: `{ "name": "Casco", "icon": "img/crates/necros-helmet.png" }` |
| `items`       | Lista de armas, herramientas y cosméticos que puede tocar                          |
| `gallery`     | Opcional. Armas con imagen: `{ "name": "Hoja Abisal", "icon": "img/crates/nazgul/abyssal_blade.webp" }` |
| `keyImage`    | Imagen de la llave (selector de crates y ventana de compra)                        |

Cada compra da una llave por unidad con `crate key give {player} <crate> 1` (sintaxis de ExcellentCrates/CrazyCrates).
Crea en tu plugin de crates un crate con ese mismo nombre (`valentine`, `necros`, `luminite`) cuyas recompensas sean
las piezas del set (ItemsAdder/Nexo/Oraxen, según el pack instalado). Para añadir un crate nuevo con otro tema, copia
uno de los bloques `[data-theme='...']` del principio de `public/styles.css` con sus tres colores.

Los comandos de ejemplo usan LuckPerms (`lp`), EssentialsX (`eco`, `broadcast`) y un plugin de cofres (`crate`).
Ajústalos a los plugins de tu servidor. Los precios siempre se leen del servidor, así que nadie puede cambiarlos desde el navegador.

## Crates y visor 3D

Cada crate enseña todos sus objetos (armas, herramientas, cofre, llave, alas, cascos y las 4 piezas de armadura).
Al tocar uno se abre un visor 3D (`public/viewer.js`, con three.js en `public/vendor/`) que gira el modelo real del
pack con sus texturas animadas. Los datos salen de los packs con tres scripts (necesitan Pillow y numpy):

```bash
python3 tools/build_items.py <carpeta con los packs descomprimidos> [set ...]   # modelos y miniaturas
python3 tools/compose_cover.py <carpeta con los packs> [set ...]                 # portada igual para todos
python3 tools/crates.py                                                          # escribe los crates en products.json
```

Para añadir un pack nuevo: súmalo a `SETS` en `tools/build_items.py` (ruta a su carpeta `assets` y espacio de
nombres), añade su línea en `tools/crates.py` (nombre, rareza, frase, descripción, llave, precio y colores) y ejecuta
los tres scripts. Las páginas comunes (cabecera, pie…) se generan con `python3 tools/pages.py`.

## Pruebas

```bash
npm test
```

Las pruebas usan una API de PayPal simulada, una base de datos D1 de prueba (SQLite) y un puente simulado. Comprueban la
validación de compras, que el precio sale del catálogo, que un pago cobrado se entrega una sola vez aunque se repita la
captura o el webhook, que solo se entrega con el jugador conectado y se reenvía si el servidor no confirma, que no se entrega si
el importe cobrado no coincide o la tarjeta es rechazada, y que se rechazan los webhooks con firma no válida. Con una
API de Discord simulada comprueban también el login, que se da el rol (o se añade al servidor), el anuncio, y que un
fallo de Discord no impide la entrega en el juego.
