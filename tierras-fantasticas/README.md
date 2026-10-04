# 🏰 Tierras Fantásticas — Web y tienda del servidor

Página web del servidor de Minecraft **Tierras Fantásticas** con tienda y pasarela de pago real:

- Portada con la IP (clic para copiar) y el estado del servidor en vivo (jugadores conectados).
- Tienda con rangos, llaves y monedas, organizados por categorías.
- **Pago con PayPal**: botones oficiales de PayPal; el comprador paga con su cuenta o con tarjeta de crédito/débito
  sin necesidad de cuenta. Los datos bancarios nunca pasan por tu servidor y el dinero llega a tu cuenta PayPal.
- **Entrega automática por RCON**: en cuanto PayPal confirma el cobro, se ejecutan los comandos en tu servidor (`lp`, `eco`, `crate`...).
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
el importe cobrado no coincide o la tarjeta es rechazada, y que se rechazan los webhooks con firma no válida.
