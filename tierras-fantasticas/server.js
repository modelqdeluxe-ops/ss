require('dotenv').config();
const path = require('path');
const express = require('express');
const products = require('./config/products.json');
const orders = require('./lib/orders');
const { PayPal, formatAmount } = require('./lib/paypal');
const { runCommands } = require('./lib/rcon');

const {
  PORT = 3000,
  SERVER_NAME = 'Tierras Fantásticas',
  SERVER_IP = 'play.tierrasfantasticas.net',
  DISCORD_URL = '',
  PAYPAL_CLIENT_ID,
  PAYPAL_CLIENT_SECRET,
  PAYPAL_ENV = 'sandbox',
  PAYPAL_WEBHOOK_ID,
  PAYPAL_API_BASE,
  RCON_HOST,
  RCON_PORT = 25575,
  RCON_PASSWORD,
} = process.env;
const CURRENCY = (process.env.CURRENCY || 'USD').toUpperCase();

if (!PAYPAL_CLIENT_ID || !PAYPAL_CLIENT_SECRET) {
  console.warn('⚠️  Faltan PAYPAL_CLIENT_ID / PAYPAL_CLIENT_SECRET: la tienda se mostrará pero no se podrá pagar.');
}
const paypal =
  PAYPAL_CLIENT_ID && PAYPAL_CLIENT_SECRET
    ? new PayPal({ clientId: PAYPAL_CLIENT_ID, clientSecret: PAYPAL_CLIENT_SECRET, env: PAYPAL_ENV, apiBase: PAYPAL_API_BASE })
    : null;

const productById = new Map(products.map((p) => [p.id, p]));
const USERNAME_RE = /^[A-Za-z0-9_]{3,16}$/;
const ORDER_ID_RE = /^[A-Z0-9]{5,40}$/;
const MAX_QUANTITY = 10;

const app = express();
app.use(express.json({ limit: '50kb' }));
app.use(express.static(path.join(__dirname, 'public')));

// --- API pública ---
app.get('/api/config', (req, res) => {
  res.json({
    serverName: SERVER_NAME,
    serverIp: SERVER_IP,
    discordUrl: DISCORD_URL,
    currency: CURRENCY,
    paypalClientId: PAYPAL_CLIENT_ID || null,
    paymentsEnabled: Boolean(paypal),
  });
});

app.get('/api/products', (req, res) => {
  // Los comandos RCON nunca se envían al navegador.
  res.json(products.map(({ commands, ...p }) => p));
});

// 1) El botón de PayPal pide crear el pedido. El precio sale del catálogo del servidor.
app.post('/api/orders', async (req, res) => {
  if (!paypal) return res.status(503).json({ error: 'Los pagos no están configurados todavía.' });

  const { productId, username } = req.body || {};
  const quantity = Number.parseInt(req.body?.quantity ?? 1, 10);
  const product = productById.get(productId);

  if (!product) return res.status(400).json({ error: 'Producto no válido.' });
  if (typeof username !== 'string' || !USERNAME_RE.test(username)) {
    return res.status(400).json({ error: 'Nombre de usuario de Minecraft no válido (3-16 letras, números o _).' });
  }
  const maxQuantity = product.maxQuantity || MAX_QUANTITY;
  if (!Number.isInteger(quantity) || quantity < 1 || quantity > maxQuantity) {
    return res.status(400).json({ error: `La cantidad debe estar entre 1 y ${maxQuantity}.` });
  }

  const amount = product.price * quantity;
  try {
    const order = await paypal.createOrder({
      amount,
      currency: CURRENCY,
      description: `${product.name} para ${username} — ${SERVER_NAME}`.slice(0, 127),
      customId: `${product.id}:${username}:${quantity}`,
      item: { name: product.name, quantity, unitAmount: product.price },
      brandName: SERVER_NAME,
    });

    await orders.upsert(order.id, {
      status: 'pending',
      username,
      productId: product.id,
      quantity,
      amount,
      currency: CURRENCY,
      createdAt: new Date().toISOString(),
    });

    res.json({ id: order.id });
  } catch (err) {
    console.error('Error creando el pedido de PayPal:', err.message);
    res.status(500).json({ error: 'No se pudo iniciar el pago. Inténtalo de nuevo.' });
  }
});

// 2) El comprador aprobó el pago en PayPal: lo cobramos y entregamos la compra.
app.post('/api/orders/:orderId/capture', async (req, res) => {
  if (!paypal) return res.status(503).json({ error: 'Los pagos no están configurados todavía.' });

  const { orderId } = req.params;
  const local = ORDER_ID_RE.test(orderId) ? orders.get(orderId) : null;
  if (!local) return res.status(404).json({ error: 'Pedido no encontrado.' });

  let result;
  try {
    result = await paypal.captureOrder(orderId);
  } catch (err) {
    // Tarjeta rechazada: el comprador puede elegir otro método sin cerrar PayPal.
    if (err.issue === 'INSTRUMENT_DECLINED') {
      return res.status(402).json({ error: 'El pago fue rechazado.', retry: true });
    }
    if (err.issue === 'ORDER_ALREADY_CAPTURED') {
      result = await paypal.getOrder(orderId).catch(() => null);
    }
    if (!result) {
      console.error('Error capturando el pago:', err.message);
      return res.status(500).json({ error: 'No se pudo completar el pago.' });
    }
  }

  try {
    const status = await handleCapture(orderId, extractCapture(result));
    res.json({ id: orderId, status });
  } catch (err) {
    console.error('Error procesando la captura:', err);
    res.status(500).json({ error: 'Pago recibido, pero hubo un error al procesarlo. Contacta con el staff.' });
  }
});

app.get('/api/order/:orderId', (req, res) => {
  const order = ORDER_ID_RE.test(req.params.orderId) ? orders.get(req.params.orderId) : null;
  if (!order) return res.status(404).json({ error: 'Pedido no encontrado.' });
  res.json({
    status: order.status,
    username: order.username,
    product: productById.get(order.productId)?.name,
    quantity: order.quantity,
    amount: order.amount,
    currency: order.currency,
  });
});

// 3) Webhook de PayPal: respaldo para pagos que se confirman más tarde (p. ej. eCheck)
//    o si el navegador se cerró justo después de pagar.
app.post('/webhook/paypal', async (req, res) => {
  if (!paypal || !PAYPAL_WEBHOOK_ID) return res.status(503).send('Webhook no configurado');

  const event = req.body;
  try {
    const valid = await paypal.verifyWebhook({ headers: req.headers, event, webhookId: PAYPAL_WEBHOOK_ID });
    if (!valid) return res.status(400).send('Firma no válida');
  } catch (err) {
    console.error('No se pudo verificar el webhook:', err.message);
    return res.status(400).send('Firma no válida');
  }

  try {
    const capture = event.resource || {};
    const orderId = capture.supplementary_data?.related_ids?.order_id;
    if (orderId && orders.get(orderId)) {
      switch (event.event_type) {
        case 'PAYMENT.CAPTURE.COMPLETED':
          await handleCapture(orderId, capture);
          break;
        case 'PAYMENT.CAPTURE.DENIED':
          await orders.upsert(orderId, { status: 'failed' });
          break;
        case 'PAYMENT.CAPTURE.REFUNDED':
        case 'PAYMENT.CAPTURE.REVERSED':
          // Avisamos en el log para que el staff retire el rango/objetos si procede.
          console.warn(`↩️  Pago ${event.event_type} del pedido ${orderId}`);
          await orders.upsert(orderId, { refunded: true, refundEvent: event.event_type });
          break;
        default:
          break;
      }
    }
  } catch (err) {
    // 500 para que PayPal reintente más tarde.
    console.error('Error procesando webhook:', err);
    return res.status(500).send('Error interno');
  }
  res.sendStatus(200);
});

function extractCapture(order) {
  return order?.purchase_units?.[0]?.payments?.captures?.[0] || null;
}

// Valida lo cobrado contra el pedido guardado y entrega si está completado.
async function handleCapture(orderId, capture) {
  const local = orders.get(orderId);
  if (orders.isClaimed(orderId)) return local.status; // Ya pagado y procesado.
  if (!capture) {
    await orders.upsert(orderId, { status: 'failed' });
    return 'failed';
  }

  const expected = formatAmount(local.amount, local.currency);
  if (capture.amount?.value !== expected || capture.amount?.currency_code !== local.currency) {
    console.error(`❌ Importe incorrecto en ${orderId}: esperado ${expected} ${local.currency}, recibido`, capture.amount);
    await orders.upsert(orderId, { status: 'error', error: 'Importe no coincide', capture: capture.amount });
    return 'error';
  }

  if (capture.status === 'PENDING') {
    await orders.upsert(orderId, { status: 'awaiting_payment', captureId: capture.id });
    return 'awaiting_payment';
  }
  if (capture.status !== 'COMPLETED') {
    await orders.upsert(orderId, { status: 'failed', captureId: capture.id });
    return 'failed';
  }

  return fulfill(orderId, capture);
}

// --- Entrega de compras en el servidor de Minecraft ---
async function fulfill(orderId, capture) {
  const claimed = await orders.claimForDelivery(orderId, {
    captureId: capture.id,
    paidAt: new Date().toISOString(),
  });
  if (!claimed) return orders.get(orderId).status; // Ya entregado.

  const { username, productId, quantity } = orders.get(orderId);
  const product = productById.get(productId);
  const commands = [];
  for (let i = 0; i < quantity; i++) {
    for (const cmd of product.commands) commands.push(cmd.replaceAll('{player}', username));
  }

  if (!RCON_HOST || !RCON_PASSWORD) {
    console.warn(`RCON sin configurar. Entrega manual pendiente para ${username}:`, commands);
    await orders.upsert(orderId, { status: 'manual', commands });
    return 'manual';
  }

  try {
    const results = await runCommands(
      { host: RCON_HOST, port: Number(RCON_PORT), password: RCON_PASSWORD },
      commands,
    );
    console.log(`✅ Entregado ${product.name} x${quantity} a ${username}`);
    await orders.upsert(orderId, { status: 'delivered', deliveredAt: new Date().toISOString(), results });
    return 'delivered';
  } catch (err) {
    // El pago ya está hecho: lo dejamos marcado para entregarlo a mano.
    console.error(`❌ Fallo al entregar a ${username}:`, err.message);
    await orders.upsert(orderId, { status: 'delivery_failed', error: err.message, commands });
    return 'delivery_failed';
  }
}

if (require.main === module) {
  app.listen(PORT, () => console.log(`🏰 ${SERVER_NAME} escuchando en el puerto ${PORT} (PayPal: ${PAYPAL_ENV})`));
}

module.exports = { app };
