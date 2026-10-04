# 🏰 Tierras Fantásticas — Web y tienda del servidor

Página web del servidor de Minecraft **Tierras Fantásticas** con tienda y pasarela de pago real:

- Portada con la IP (clic para copiar) y el estado del servidor en vivo (jugadores conectados).
- Tienda con rangos, llaves y monedas, organizados por categorías.
- **Pago con Stripe Checkout**: tarjeta, Google Pay o Apple Pay. Los datos de la tarjeta nunca pasan por tu servidor.
- **Entrega automática por RCON**: cuando Stripe confirma el pago, se ejecutan los comandos en tu servidor (`lp`, `eco`, `crate`...).
- Página de confirmación que muestra el estado del pago y de la entrega.
- Pedidos guardados en `data/orders.json`. Si el servidor está apagado o falla RCON, el pedido queda como `delivery_failed` y guarda los comandos para entregarlo a mano.

## Puesta en marcha

Requiere Node.js 18 o superior.

```bash
cd tierras-fantasticas
npm install
cp .env.example .env    # y rellena los valores
npm start               # http://localhost:3000
```

### 1. Stripe

1. Crea una cuenta en <https://dashboard.stripe.com> y copia la **clave secreta** (`sk_test_...` para pruebas) en `STRIPE_SECRET_KEY`.
2. Configura el webhook:
   - **En local**: instala la [Stripe CLI](https://docs.stripe.com/stripe-cli) y ejecuta
     `stripe listen --forward-to localhost:3000/webhook`. Copia el `whsec_...` que muestra en `STRIPE_WEBHOOK_SECRET`.
   - **En producción**: en *Desarrolladores → Webhooks*, añade el endpoint `https://TU-DOMINIO/webhook` con los eventos
     `checkout.session.completed`, `checkout.session.async_payment_succeeded`, `checkout.session.async_payment_failed`
     y `checkout.session.expired`. Copia su *signing secret* en `STRIPE_WEBHOOK_SECRET`.
3. Elige la moneda en `CURRENCY` (`usd`, `eur`, `mxn`, `cop`, `clp`, `pen`, `ars`...).
4. Para probar, paga con la tarjeta `4242 4242 4242 4242`, cualquier fecha futura y cualquier CVC.
5. Cuando todo funcione, cambia a las claves `sk_live_...` (y al webhook en modo *live*).

### 2. RCON del servidor de Minecraft

En `server.properties`:

```properties
enable-rcon=true
rcon.port=25575
rcon.password=una-contraseña-larga
```

Pon esos datos en `RCON_HOST`, `RCON_PORT` y `RCON_PASSWORD`. No abras el puerto RCON a internet; si la web está en
otra máquina, limita el puerto con el firewall a la IP de la web.

### 3. Productos

Edita `config/products.json`. Cada producto tiene:

| Campo         | Descripción                                                      |
|---------------|------------------------------------------------------------------|
| `id`          | Identificador único                                              |
| `category`    | `rangos`, `llaves` o `monedas` (las pestañas de la tienda)       |
| `price`       | Precio en **céntimos** (`499` = 4,99)                            |
| `maxQuantity` | Opcional. Cantidad máxima por compra (por defecto 10; rangos = 1)|
| `featured`    | Opcional. Lo destaca como "Más popular"                          |
| `commands`    | Comandos RCON; `{player}` se sustituye por el nombre del jugador |

Los comandos de ejemplo usan LuckPerms (`lp`), EssentialsX (`eco`, `broadcast`) y un plugin de cofres (`crate`).
Ajústalos a los plugins de tu servidor. Los precios siempre se leen del servidor, así que nadie puede cambiarlos desde el navegador.

## Despliegue

Cualquier hosting con Node.js sirve (VPS, Railway, Render, Fly.io...). Necesitas HTTPS (Stripe lo exige para los webhooks
en producción) y poner `PUBLIC_URL` con tu dominio. Ejemplo en un VPS con PM2:

```bash
npm install --omit=dev
pm2 start server.js --name tierras-fantasticas
```

## Pruebas

```bash
npm test
```

Las pruebas comprueban la validación de compras, que se rechazan los webhooks con firma falsa, y que un pago confirmado
se entrega por RCON (con un servidor RCON simulado) sin entregarse dos veces si Stripe repite el evento.
