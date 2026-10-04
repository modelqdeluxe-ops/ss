require('dotenv').config();
const path = require('path');
const express = require('express');
const Stripe = require('stripe');
const products = require('./config/products.json');
const orders = require('./lib/orders');
const { runCommands } = require('./lib/rcon');

const {
  PORT = 3000,
  PUBLIC_URL = `http://localhost:${PORT}`,
  STRIPE_SECRET_KEY,
  STRIPE_WEBHOOK_SECRET,
  CURRENCY = 'usd',
  SERVER_NAME = 'Tierras Fantásticas',
  SERVER_IP = 'play.tierrasfantasticas.net',
  DISCORD_URL = '',
  RCON_HOST,
  RCON_PORT = 25575,
  RCON_PASSWORD,
} = process.env;

if (!STRIPE_SECRET_KEY) {
  console.warn('⚠️  Falta STRIPE_SECRET_KEY: la tienda se mostrará pero no se podrá pagar.');
}
const stripe = STRIPE_SECRET_KEY ? new Stripe(STRIPE_SECRET_KEY) : null;

const productById = new Map(products.map((p) => [p.id, p]));
const USERNAME_RE = /^[A-Za-z0-9_]{3,16}$/;
const MAX_QUANTITY = 10;

const app = express();

// --- Webhook de Stripe (necesita el cuerpo sin parsear para verificar la firma) ---
app.post('/webhook', express.raw({ type: 'application/json' }), async (req, res) => {
  if (!stripe || !STRIPE_WEBHOOK_SECRET) return res.status(503).send('Webhook no configurado');

  let event;
  try {
    event = stripe.webhooks.constructEvent(req.body, req.headers['stripe-signature'], STRIPE_WEBHOOK_SECRET);
  } catch (err) {
    console.error('Firma de webhook inválida:', err.message);
    return res.status(400).send(`Webhook Error: ${err.message}`);
  }

  try {
    switch (event.type) {
      case 'checkout.session.completed':
      case 'checkout.session.async_payment_succeeded': {
        const session = event.data.object;
        if (session.payment_status === 'paid') await fulfill(session);
        else await orders.upsert(session.id, { status: 'awaiting_payment' });
        break;
      }
      case 'checkout.session.async_payment_failed':
      case 'checkout.session.expired':
        await orders.upsert(event.data.object.id, { status: 'failed' });
        break;
      default:
        break;
    }
  } catch (err) {
    // Respondemos 500 para que Stripe reintente más tarde.
    console.error('Error procesando webhook:', err);
    return res.status(500).send('Error interno');
  }

  res.json({ received: true });
});

app.use(express.json({ limit: '10kb' }));
app.use(express.static(path.join(__dirname, 'public')));

// --- API pública ---
app.get('/api/config', (req, res) => {
  res.json({
    serverName: SERVER_NAME,
    serverIp: SERVER_IP,
    discordUrl: DISCORD_URL,
    currency: CURRENCY,
    paymentsEnabled: Boolean(stripe),
  });
});

app.get('/api/products', (req, res) => {
  // Los comandos RCON nunca se envían al navegador.
  res.json(products.map(({ commands, ...p }) => p));
});

app.post('/api/checkout', async (req, res) => {
  if (!stripe) return res.status(503).json({ error: 'Los pagos no están configurados todavía.' });

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

  try {
    // El precio sale siempre del catálogo del servidor, nunca del navegador.
    const session = await stripe.checkout.sessions.create({
      mode: 'payment',
      line_items: [
        {
          quantity,
          price_data: {
            currency: CURRENCY,
            unit_amount: product.price,
            product_data: {
              name: `${product.name} — ${SERVER_NAME}`,
              description: `Para el jugador: ${username}`,
            },
          },
        },
      ],
      client_reference_id: username,
      metadata: { username, productId: product.id, quantity: String(quantity) },
      success_url: `${PUBLIC_URL}/success.html?session_id={CHECKOUT_SESSION_ID}`,
      cancel_url: `${PUBLIC_URL}/#tienda`,
    });

    await orders.upsert(session.id, {
      status: 'pending',
      username,
      productId: product.id,
      quantity,
      amount: product.price * quantity,
      currency: CURRENCY,
      createdAt: new Date().toISOString(),
    });

    res.json({ url: session.url });
  } catch (err) {
    console.error('Error creando la sesión de pago:', err.message);
    res.status(500).json({ error: 'No se pudo iniciar el pago. Inténtalo de nuevo.' });
  }
});

app.get('/api/order/:sessionId', async (req, res) => {
  if (!stripe) return res.status(503).json({ error: 'Pagos no configurados.' });
  try {
    const session = await stripe.checkout.sessions.retrieve(req.params.sessionId);
    const order = orders.get(session.id) || {};
    const product = productById.get(session.metadata?.productId);
    res.json({
      paymentStatus: session.payment_status,
      deliveryStatus: order.status || 'pending',
      username: session.metadata?.username,
      product: product?.name,
      quantity: Number(session.metadata?.quantity || 1),
      amount: session.amount_total,
      currency: session.currency,
    });
  } catch {
    res.status(404).json({ error: 'Pedido no encontrado.' });
  }
});

// --- Entrega de compras en el servidor de Minecraft ---
async function fulfill(session) {
  const { username, productId } = session.metadata || {};
  const quantity = Number(session.metadata?.quantity || 1);
  const product = productById.get(productId);
  if (!product || !USERNAME_RE.test(username || '')) {
    await orders.upsert(session.id, { status: 'error', error: 'Metadatos inválidos' });
    return;
  }

  const claimed = await orders.claimForDelivery(session.id, {
    username,
    productId,
    quantity,
    amount: session.amount_total,
    currency: session.currency,
    email: session.customer_details?.email,
    paidAt: new Date().toISOString(),
  });
  if (!claimed) return; // Ya entregado (Stripe reintentó el webhook).

  const commands = [];
  for (let i = 0; i < quantity; i++) {
    for (const cmd of product.commands) commands.push(cmd.replaceAll('{player}', username));
  }

  if (!RCON_HOST || !RCON_PASSWORD) {
    console.warn(`RCON sin configurar. Entrega manual pendiente para ${username}:`, commands);
    await orders.upsert(session.id, { status: 'manual', commands });
    return;
  }

  try {
    const results = await runCommands(
      { host: RCON_HOST, port: Number(RCON_PORT), password: RCON_PASSWORD },
      commands,
    );
    console.log(`✅ Entregado ${product.name} x${quantity} a ${username}`);
    await orders.upsert(session.id, { status: 'delivered', deliveredAt: new Date().toISOString(), results });
  } catch (err) {
    // El pago ya está hecho: lo dejamos marcado para entregarlo a mano o reintentar.
    console.error(`❌ Fallo al entregar a ${username}:`, err.message);
    await orders.upsert(session.id, { status: 'delivery_failed', error: err.message, commands });
  }
}

if (require.main === module) {
  app.listen(PORT, () => console.log(`🏰 ${SERVER_NAME} escuchando en ${PUBLIC_URL}`));
}

module.exports = { app, fulfill };
