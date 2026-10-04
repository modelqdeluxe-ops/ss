require('dotenv').config();
const crypto = require('crypto');
const path = require('path');
const express = require('express');
const products = require('./config/products.json');
const orders = require('./lib/orders');
const { PayPal, formatAmount } = require('./lib/paypal');
const { runCommands } = require('./lib/rcon');
const { Discord, isSnowflake } = require('./lib/discord');
const { createSession, parseCookies, serializeCookie } = require('./lib/session');

const {
  PORT = 3000,
  SERVER_NAME = 'Tierras Fantásticas',
  SERVER_IP = '216.163.187.40:19001',
  DISCORD_URL = 'https://discord.gg/tRrunHBZE',
  PAYPAL_CLIENT_ID,
  PAYPAL_CLIENT_SECRET,
  PAYPAL_ENV = 'sandbox',
  PAYPAL_WEBHOOK_ID,
  PAYPAL_API_BASE,
  RCON_HOST,
  RCON_PORT = 25575,
  RCON_PASSWORD,
  DISCORD_CLIENT_ID,
  DISCORD_CLIENT_SECRET,
  DISCORD_BOT_TOKEN,
  DISCORD_GUILD_ID,
  DISCORD_WEBHOOK_URL,
  SESSION_SECRET,
} = process.env;
const CURRENCY = (process.env.CURRENCY || 'USD').toUpperCase();
const PUBLIC_URL = (process.env.PUBLIC_URL || `http://localhost:${PORT}`).replace(/\/$/, '');
const SECURE_COOKIES = PUBLIC_URL.startsWith('https://');

if (!PAYPAL_CLIENT_ID || !PAYPAL_CLIENT_SECRET) {
  console.warn('⚠️  Faltan PAYPAL_CLIENT_ID / PAYPAL_CLIENT_SECRET: la tienda se mostrará pero no se podrá pagar.');
}
const paypal =
  PAYPAL_CLIENT_ID && PAYPAL_CLIENT_SECRET
    ? new PayPal({ clientId: PAYPAL_CLIENT_ID, clientSecret: PAYPAL_CLIENT_SECRET, env: PAYPAL_ENV, apiBase: PAYPAL_API_BASE })
    : null;

const discord = new Discord({
  clientId: DISCORD_CLIENT_ID,
  clientSecret: DISCORD_CLIENT_SECRET,
  botToken: DISCORD_BOT_TOKEN,
  guildId: DISCORD_GUILD_ID,
  webhookUrl: DISCORD_WEBHOOK_URL,
  redirectUri: `${PUBLIC_URL}/auth/discord/callback`,
});
if (DISCORD_CLIENT_ID && !SESSION_SECRET) {
  console.warn('⚠️  Falta SESSION_SECRET: las cuentas de Discord vinculadas se perderán al reiniciar la web.');
}
const session = createSession(SESSION_SECRET);
const DISCORD_COOKIE = 'tf_discord';
const STATE_COOKIE = 'tf_oauth_state';

const productById = new Map(products.map((p) => [p.id, p]));
const USERNAME_RE = /^[A-Za-z0-9_]{3,16}$/;
const ORDER_ID_RE = /^[A-Z0-9]{5,40}$/;
const MAX_QUANTITY = 10;

for (const p of products) {
  const invalid = (p.discordRoles || []).filter((id) => !isSnowflake(id));
  if (invalid.length) console.warn(`⚠️  ${p.id}: IDs de rol de Discord no válidos (se ignoran):`, invalid.join(', '));
}
const productRoles = (p) => (p.discordRoles || []).filter(isSnowflake);

const app = express();
app.use(express.json({ limit: '50kb' }));
app.use(express.static(path.join(__dirname, 'public')));

function discordUser(req) {
  const data = session.decode(parseCookies(req.headers.cookie)[DISCORD_COOKIE]);
  return data?.id ? data : null;
}

// --- API pública ---
app.get('/api/config', (req, res) => {
  res.json({
    serverName: SERVER_NAME,
    serverIp: SERVER_IP,
    discordUrl: DISCORD_URL,
    discordLogin: discord.loginEnabled,
    currency: CURRENCY,
    paypalClientId: PAYPAL_CLIENT_ID || null,
    paymentsEnabled: Boolean(paypal),
  });
});

app.get('/api/products', (req, res) => {
  // Los comandos RCON y los IDs de rol nunca se envían al navegador.
  res.json(
    products.map(({ commands, discordRoles, ...p }) => ({
      ...p,
      discordRole: discord.rolesEnabled && productRoles({ discordRoles }).length > 0,
    })),
  );
});

app.get('/api/me', (req, res) => {
  const user = discordUser(req);
  res.json({ discord: user ? { id: user.id, username: user.username, avatar: user.avatar } : null });
});

// --- Vincular la cuenta de Discord (OAuth2) ---
// Solo permitimos volver a rutas de esta misma web.
const safeReturn = (value) => (typeof value === 'string' && /^\/(?!\/)[\w\-./?=&%]*$/.test(value) ? value : '/');

app.get('/auth/discord', (req, res) => {
  if (!discord.loginEnabled) return res.redirect('/');
  const state = crypto.randomBytes(16).toString('hex');
  const ret = safeReturn(req.query.return);
  res.setHeader(
    'Set-Cookie',
    serializeCookie(STATE_COOKIE, session.encode({ state, ret, exp: Date.now() + 10 * 60 * 1000 }), {
      maxAge: 600,
      secure: SECURE_COOKIES,
    }),
  );
  res.redirect(discord.authorizeUrl(state));
});

app.get('/auth/discord/callback', async (req, res) => {
  const saved = session.decode(parseCookies(req.headers.cookie)[STATE_COOKIE]);
  const clearState = serializeCookie(STATE_COOKIE, '', { maxAge: 0, secure: SECURE_COOKIES });
  const ret = saved?.ret || '/';
  const back = (flag) => `${ret}${ret.includes('?') ? '&' : '?'}discord=${flag}`;

  if (!saved || typeof req.query.state !== 'string' || req.query.state !== saved.state) {
    res.setHeader('Set-Cookie', clearState);
    return res.redirect(back('error'));
  }
  if (req.query.error || typeof req.query.code !== 'string') {
    res.setHeader('Set-Cookie', clearState);
    return res.redirect(back('cancel'));
  }

  try {
    const user = await discord.exchangeCode(req.query.code);
    const exp = Math.min(user.expiresAt, Date.now() + 7 * 24 * 3600 * 1000);
    res.setHeader('Set-Cookie', [
      clearState,
      serializeCookie(DISCORD_COOKIE, session.encode({ ...user, exp }), {
        maxAge: Math.floor((exp - Date.now()) / 1000),
        secure: SECURE_COOKIES,
      }),
    ]);
    res.redirect(back('ok'));
  } catch (err) {
    console.error('Error en el login de Discord:', err.message);
    res.setHeader('Set-Cookie', clearState);
    res.redirect(back('error'));
  }
});

app.post('/auth/logout', (req, res) => {
  res.setHeader('Set-Cookie', serializeCookie(DISCORD_COOKIE, '', { maxAge: 0, secure: SECURE_COOKIES }));
  res.json({ ok: true });
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

    // Si el comprador vinculó Discord, guardamos su cuenta para darle el rol al pagar.
    const linked = discordUser(req);
    await orders.upsert(order.id, {
      status: 'pending',
      username,
      productId: product.id,
      quantity,
      amount,
      currency: CURRENCY,
      discord: linked ? { id: linked.id, username: linked.username, accessToken: linked.accessToken } : null,
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
    discord: order.discord
      ? { username: order.discord.username, status: order.discord.status || null }
      : null,
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

  // 1) En el juego, por RCON.
  let status;
  if (!RCON_HOST || !RCON_PASSWORD) {
    console.warn(`RCON sin configurar. Entrega manual pendiente para ${username}:`, commands);
    await orders.upsert(orderId, { status: 'manual', commands });
    status = 'manual';
  } else {
    try {
      const results = await runCommands(
        { host: RCON_HOST, port: Number(RCON_PORT), password: RCON_PASSWORD },
        commands,
      );
      console.log(`✅ Entregado ${product.name} x${quantity} a ${username}`);
      await orders.upsert(orderId, { status: 'delivered', deliveredAt: new Date().toISOString(), results });
      status = 'delivered';
    } catch (err) {
      // El pago ya está hecho: lo dejamos marcado para entregarlo a mano.
      console.error(`❌ Fallo al entregar a ${username}:`, err.message);
      await orders.upsert(orderId, { status: 'delivery_failed', error: err.message, commands });
      status = 'delivery_failed';
    }
  }

  // 2) En Discord: rol y anuncio. Un fallo aquí no afecta a la entrega en el juego.
  await deliverDiscord(orderId, product);
  return status;
}

async function deliverDiscord(orderId, product) {
  const order = orders.get(orderId);
  const linked = order.discord;
  const roles = productRoles(product);

  if (linked && roles.length && discord.rolesEnabled) {
    let discordStatus;
    try {
      const result = await discord.grantRoles({
        userId: linked.id,
        accessToken: linked.accessToken,
        roleIds: roles,
        reason: `Compra en la tienda: ${product.name} (pedido ${orderId})`,
      });
      discordStatus = { status: result.joined ? 'joined' : 'granted', roles: result.roles };
      console.log(`✅ Rol de Discord dado a ${linked.username} (${linked.id})`);
    } catch (err) {
      console.error(`❌ No se pudo dar el rol de Discord a ${linked.username}:`, err.message);
      discordStatus = { status: 'failed', error: err.message, roles };
    }
    // El token del comprador ya no hace falta: no lo dejamos guardado.
    await orders.upsert(orderId, { discord: { id: linked.id, username: linked.username, ...discordStatus } });
  } else if (linked?.accessToken) {
    await orders.upsert(orderId, { discord: { id: linked.id, username: linked.username } });
  }

  try {
    const who = linked ? `<@${linked.id}> (**${order.username}**)` : `**${order.username}**`;
    await discord.announce({
      embed: {
        title: '🎉 ¡Nueva compra en la tienda!',
        description: `${who} ha conseguido **${product.name}**${order.quantity > 1 ? ` ×${order.quantity}` : ''}. ¡Gracias por apoyar ${SERVER_NAME}!`,
        color: 0xf4c95d,
        timestamp: new Date().toISOString(),
      },
    });
  } catch (err) {
    console.warn('No se pudo anunciar la compra en Discord:', err.message);
  }
}

if (require.main === module) {
  app.listen(PORT, () => console.log(`🏰 ${SERVER_NAME} escuchando en el puerto ${PORT} (PayPal: ${PAYPAL_ENV})`));
}

module.exports = { app };
