# 🏰 Tierras Fantásticas — Web y tienda del servidor

Página web del servidor de Minecraft **Tierras Fantásticas** con tienda y pasarela de pago real:

- Portada con la IP (clic para copiar) y el estado del servidor en vivo (jugadores conectados).
- Tienda con rangos, llaves y monedas, organizados por categorías.
- **Pago con PayPal**: botones oficiales de PayPal; el comprador paga con su cuenta o con tarjeta de crédito/débito
  sin necesidad de cuenta. Los datos bancarios nunca pasan por tu servidor y el dinero llega a tu cuenta PayPal.
- **Entrega automática por RCON**: en cuanto PayPal confirma el cobro, se ejecutan los comandos en tu servidor (`lp`, `eco`, `crate`...).
- **Discord conectado**: el comprador vincula su cuenta de Discord al comprar. Al confirmarse el pago, un bot le da el rol
  de su rango (y lo mete en el servidor de Discord si aún no estaba) y se anuncia la compra en un canal.
- Página de confirmación que muestra el estado del pago y de la entrega en el juego y en Discord.
- Pedidos guardados en `data/orders.json`. Si el servidor está apagado o falla RCON, el pedido queda como `delivery_failed` y guarda los comandos para entregarlo a mano.

## Cómo está todo conectado

```
 Jugador ──► Web (tienda) ──► PayPal (paga) ──► Web confirma el cobro
                 │                                   │
                 └─ "Vincular Discord" (opcional)     ├─► Minecraft (RCON): lp user Steve parent add hechicero
                                                     ├─► Discord (bot): rol "Hechicero" al usuario vinculado
                                                     └─► Discord (webhook): "🎉 Steve ha conseguido Rango Hechicero"
```

Todo pasa en segundos y sin intervención del staff. Si una parte falla (el servidor está apagado, el bot no tiene
permisos…), el pago queda registrado en `data/orders.json` con lo que falta por entregar y el error en el log.

## Puesta en marcha

Requiere Node.js 18 o superior.

```bash
cd tierras-fantasticas
npm install
cp .env.example .env    # y rellena los valores
npm start               # http://localhost:3000
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

### 2. RCON del servidor de Minecraft

En `server.properties`:

```properties
enable-rcon=true
rcon.port=25575
rcon.password=una-contraseña-larga
```

Pon esos datos en `RCON_HOST`, `RCON_PORT` y `RCON_PASSWORD`. No abras el puerto RCON a internet; si la web está en
otra máquina, limita el puerto con el firewall a la IP de la web.

### 3. Discord

Discord es opcional: si no lo configuras, la tienda funciona igual, solo que sin roles ni anuncios.

1. **Crea la aplicación**: en <https://discord.com/developers/applications> → *New Application* (p. ej. "Tierras Fantásticas").
2. **OAuth2** (pestaña *OAuth2*): copia el *Client ID* y el *Client Secret* en `DISCORD_CLIENT_ID` y
   `DISCORD_CLIENT_SECRET`. En *Redirects* añade exactamente `PUBLIC_URL` + `/auth/discord/callback`
   (p. ej. `https://tienda.tierrasfantasticas.net/auth/discord/callback`).
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
8. Pon un texto aleatorio largo en `SESSION_SECRET` (por ejemplo, el resultado de `openssl rand -hex 32`).

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
| `commands`    | Comandos RCON; `{player}` se sustituye por el nombre del jugador |

Los comandos de ejemplo usan LuckPerms (`lp`), EssentialsX (`eco`, `broadcast`) y un plugin de cofres (`crate`).
Ajústalos a los plugins de tu servidor. Los precios siempre se leen del servidor, así que nadie puede cambiarlos desde el navegador.

## Despliegue

Cualquier hosting con Node.js sirve (VPS, Railway, Render, Fly.io...). Necesitas HTTPS (PayPal lo exige para los webhooks)
y un dominio propio. Ejemplo en un VPS con PM2:

```bash
npm install --omit=dev
pm2 start server.js --name tierras-fantasticas
```

## Pruebas

```bash
npm test
```

Las pruebas usan una API de PayPal y un servidor RCON simulados. Comprueban la validación de compras, que el precio sale
del catálogo, que un pago cobrado se entrega una sola vez aunque se repita la captura o el webhook, que no se entrega si
el importe cobrado no coincide o la tarjeta es rechazada, y que se rechazan los webhooks con firma no válida. Con una
API de Discord simulada comprueban también el login, que se da el rol (o se añade al servidor), el anuncio, y que un
fallo de Discord no impide la entrega en el juego.
