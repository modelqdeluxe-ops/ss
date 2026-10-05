// API de la tienda de Tierras Fantásticas para Cloudflare Workers.
// Pagos con PayPal, cuentas de Discord y el puente con el servidor de Minecraft (mod TF Client).
import products from '../config/products.json' with { type: 'json' };
import { PayPal, formatAmount } from './paypal.js';
import { Discord, isSnowflake } from './discord.js';
import { createStore, CLAIMED } from './store.js';
import { createSession, parseCookies, serializeCookie, randomHex, safeEqual } from './session.js';

const USERNAME_RE = /^[A-Za-z0-9_]{3,16}$/;
const ORDER_ID_RE = /^[A-Z0-9]{5,40}$/;
const MAX_QUANTITY = 10;
const DISCORD_COOKIE = 'tf_discord';
const STATE_COOKIE = 'tf_oauth_state';
// Si el puente no ha llamado en este tiempo, damos el servidor por desconectado.
const BRIDGE_FRESH_MS = 45 * 1000;
const BRIDGE_INTERVAL_S = 10;

const productById = new Map(products.map((p) => [p.id, p]));
const productRoles = (p) => (p.discordRoles || []).filter(isSnowflake);

export function json(data, status = 200, headers = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', ...headers },
  });
}

function redirect(location, cookies = []) {
  const headers = new Headers({ Location: location, 'Cache-Control': 'no-store' });
  for (const c of cookies) headers.append('Set-Cookie', c);
  return new Response(null, { status: 302, headers });
}

async function readJson(request) {
  try {
    return await request.json();
  } catch {
    return null;
  }
}

// Solo permitimos volver a rutas de esta misma web.
const safeReturn = (value) => (typeof value === 'string' && /^\/(?!\/)[\w\-./?=&%]*$/.test(value) ? value : '/');

export function createApp(env) {
  const SERVER_NAME = env.SERVER_NAME || 'Tierras Fantásticas';
  const SERVER_IP = env.SERVER_IP || '216.163.187.40:19001';
  const DISCORD_URL = env.DISCORD_URL || 'https://discord.gg/tRrunHBZE';
  const CURRENCY = (env.CURRENCY || 'USD').toUpperCase();
  const PUBLIC_URL = (env.PUBLIC_URL || '').replace(/\/$/, '');
  const log = env.LOGGER || console;

  const store = createStore(env.DB);
  const paypal =
    env.PAYPAL_CLIENT_ID && env.PAYPAL_CLIENT_SECRET
      ? new PayPal({
          clientId: env.PAYPAL_CLIENT_ID,
          clientSecret: env.PAYPAL_CLIENT_SECRET,
          env: env.PAYPAL_ENV || 'sandbox',
          apiBase: env.PAYPAL_API_BASE,
        })
      : null;

  let sessionPromise = null;
  const session = () =>
    (sessionPromise ||= (env.SESSION_SECRET ? Promise.resolve(env.SESSION_SECRET) : store.secret('session'))
      .then(createSession)
      .catch((err) => {
        sessionPromise = null;
        throw err;
      }));

  // La dirección pública sale de la configuración o, si falta, de la propia petición.
  function discordFor(origin) {
    return new Discord({
      clientId: env.DISCORD_CLIENT_ID,
      clientSecret: env.DISCORD_CLIENT_SECRET,
      botToken: env.DISCORD_BOT_TOKEN,
      guildId: env.DISCORD_GUILD_ID,
      webhookUrl: env.DISCORD_WEBHOOK_URL,
      redirectUri: `${PUBLIC_URL || origin}/auth/discord/callback`,
      apiBase: env.DISCORD_API_BASE,
    });
  }

  async function discordUser(request) {
    const data = await (await session()).decode(parseCookies(request.headers.get('Cookie'))[DISCORD_COOKIE]);
    return data?.id ? data : null;
  }

  // --- Rutas ---
  const routes = {
    'GET /api/config': async (req, { discord }) =>
      json({
        serverName: SERVER_NAME,
        serverIp: SERVER_IP,
        discordUrl: DISCORD_URL,
        discordLogin: discord.loginEnabled,
        currency: CURRENCY,
        paypalClientId: env.PAYPAL_CLIENT_ID || null,
        paymentsEnabled: Boolean(paypal),
      }),

    // Los comandos y los IDs de rol nunca se envían al navegador.
    'GET /api/products': async (req, { discord }) =>
      json(
        products.map(({ commands, discordRoles, ...p }) => ({
          ...p,
          discordRole: discord.rolesEnabled && productRoles({ discordRoles }).length > 0,
        })),
      ),

    'GET /api/me': async (req) => {
      const user = await discordUser(req);
      return json({ discord: user ? { id: user.id, username: user.username, avatar: user.avatar } : null });
    },

    // Estado del servidor según el puente (null si el puente no está conectado).
    'GET /api/status': async () => {
      const status = await store.serverStatus();
      if (!status || Date.now() - status.at > BRIDGE_FRESH_MS) return json({ bridge: false });
      return json({
        bridge: true,
        online: true,
        players: { online: status.players.length, max: status.max, list: status.players.map((p) => p.name) },
      });
    },

    // --- Vincular la cuenta de Discord (OAuth2) ---
    'GET /auth/discord': async (req, { url, discord, secure }) => {
      if (!discord.loginEnabled) return redirect('/');
      const state = randomHex(16);
      const ret = safeReturn(url.searchParams.get('return'));
      const cookie = serializeCookie(
        STATE_COOKIE,
        await (await session()).encode({ state, ret, exp: Date.now() + 10 * 60 * 1000 }),
        { maxAge: 600, secure },
      );
      return redirect(discord.authorizeUrl(state), [cookie]);
    },

    'GET /auth/discord/callback': async (req, { url, discord, secure }) => {
      const s = await session();
      const saved = await s.decode(parseCookies(req.headers.get('Cookie'))[STATE_COOKIE]);
      const clearState = serializeCookie(STATE_COOKIE, '', { maxAge: 0, secure });
      const ret = saved?.ret || '/';
      const back = (flag) => `${ret}${ret.includes('?') ? '&' : '?'}discord=${flag}`;
      const state = url.searchParams.get('state');
      const code = url.searchParams.get('code');

      if (!saved || !state || state !== saved.state) return redirect(back('error'), [clearState]);
      if (url.searchParams.get('error') || !code) return redirect(back('cancel'), [clearState]);

      try {
        const user = await discord.exchangeCode(code);
        const exp = Math.min(user.expiresAt, Date.now() + 7 * 24 * 3600 * 1000);
        const cookie = serializeCookie(DISCORD_COOKIE, await s.encode({ ...user, exp }), {
          maxAge: Math.floor((exp - Date.now()) / 1000),
          secure,
        });
        return redirect(back('ok'), [clearState, cookie]);
      } catch (err) {
        log.error('Error en el login de Discord:', err.message);
        return redirect(back('error'), [clearState]);
      }
    },

    'POST /auth/logout': async (req, { secure }) =>
      json({ ok: true }, 200, { 'Set-Cookie': serializeCookie(DISCORD_COOKIE, '', { maxAge: 0, secure }) }),

    // 1) El botón de PayPal pide crear el pedido. El precio sale del catálogo de la web.
    'POST /api/orders': async (req) => {
      if (!paypal) return json({ error: 'Los pagos no están configurados todavía.' }, 503);

      const body = (await readJson(req)) || {};
      const { productId, username } = body;
      const quantity = Number.parseInt(body.quantity ?? 1, 10);
      const product = productById.get(productId);

      if (!product) return json({ error: 'Producto no válido.' }, 400);
      if (typeof username !== 'string' || !USERNAME_RE.test(username)) {
        return json({ error: 'Nombre de usuario de Minecraft no válido (3-16 letras, números o _).' }, 400);
      }
      const maxQuantity = product.maxQuantity || MAX_QUANTITY;
      if (!Number.isInteger(quantity) || quantity < 1 || quantity > maxQuantity) {
        return json({ error: `La cantidad debe estar entre 1 y ${maxQuantity}.` }, 400);
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
        const linked = await discordUser(req);
        await store.createOrder({
          id: order.id,
          status: 'pending',
          username,
          productId: product.id,
          quantity,
          amount,
          currency: CURRENCY,
          discord: linked ? { id: linked.id, username: linked.username, accessToken: linked.accessToken } : null,
        });
        return json({ id: order.id });
      } catch (err) {
        log.error('Error creando el pedido de PayPal:', err.message);
        return json({ error: 'No se pudo iniciar el pago. Inténtalo de nuevo.' }, 500);
      }
    },

    'GET /api/order/:id': async (req, { params }) => {
      const order = ORDER_ID_RE.test(params.id) ? await store.getOrder(params.id) : null;
      if (!order) return json({ error: 'Pedido no encontrado.' }, 404);
      return json({
        status: order.status,
        username: order.username,
        product: productById.get(order.productId)?.name,
        quantity: order.quantity,
        amount: order.amount,
        currency: order.currency,
        discord: order.discord ? { username: order.discord.username, status: order.discord.status || null } : null,
      });
    },

    // 3) Webhook de PayPal: respaldo para pagos que se confirman más tarde (p. ej. eCheck)
    //    o si el navegador se cerró justo después de pagar.
    'POST /webhook/paypal': async (req, ctx) => {
      if (!paypal || !env.PAYPAL_WEBHOOK_ID) return new Response('Webhook no configurado', { status: 503 });

      const event = await readJson(req);
      if (!event) return new Response('JSON no válido', { status: 400 });
      try {
        const h = (name) => req.headers.get(name);
        const headers = {
          'paypal-auth-algo': h('paypal-auth-algo'),
          'paypal-cert-url': h('paypal-cert-url'),
          'paypal-transmission-id': h('paypal-transmission-id'),
          'paypal-transmission-sig': h('paypal-transmission-sig'),
          'paypal-transmission-time': h('paypal-transmission-time'),
        };
        const valid = await paypal.verifyWebhook({ headers, event, webhookId: env.PAYPAL_WEBHOOK_ID });
        if (!valid) return new Response('Firma no válida', { status: 400 });
      } catch (err) {
        log.error('No se pudo verificar el webhook:', err.message);
        return new Response('Firma no válida', { status: 400 });
      }

      try {
        const capture = event.resource || {};
        const orderId = capture.supplementary_data?.related_ids?.order_id;
        if (orderId && ORDER_ID_RE.test(orderId) && (await store.getOrder(orderId))) {
          switch (event.event_type) {
            case 'PAYMENT.CAPTURE.COMPLETED':
              await handleCapture(orderId, capture, ctx);
              break;
            case 'PAYMENT.CAPTURE.DENIED':
              await store.updateOrder(orderId, { status: 'failed' });
              break;
            case 'PAYMENT.CAPTURE.REFUNDED':
            case 'PAYMENT.CAPTURE.REVERSED':
              // Avisamos en el log para que el staff retire el rango/objetos si procede.
              log.warn(`Pago ${event.event_type} del pedido ${orderId}`);
              await store.updateOrder(orderId, { refunded: event.event_type });
              break;
            default:
              break;
          }
        }
      } catch (err) {
        // 500 para que PayPal reintente más tarde.
        log.error('Error procesando webhook:', err);
        return new Response('Error interno', { status: 500 });
      }
      return new Response('OK');
    },

    // --- Puente con el servidor de Minecraft (mod TF Client instalado en el servidor) ---
    // Cada pocos segundos el servidor manda sus jugadores conectados y las entregas que ya hizo,
    // y recibe las compras pendientes de los jugadores que están dentro.
    'POST /bridge/poll': async (req) => {
      if (!env.BRIDGE_SECRET) return json({ error: 'Puente no configurado (falta BRIDGE_SECRET).' }, 503);
      const auth = req.headers.get('Authorization') || '';
      if (!safeEqual(auth, `Bearer ${env.BRIDGE_SECRET}`)) return json({ error: 'Clave del puente incorrecta.' }, 401);

      const body = (await readJson(req)) || {};
      const players = (Array.isArray(body.players) ? body.players : [])
        .filter((p) => p && typeof p.name === 'string' && USERNAME_RE.test(p.name))
        .slice(0, 500)
        .map((p) => ({ name: p.name, uuid: typeof p.uuid === 'string' ? p.uuid.slice(0, 36) : null }));
      const max = Number.isInteger(body.max) ? body.max : null;
      const done = (Array.isArray(body.done) ? body.done : [])
        .filter((d) => d && Number.isInteger(d.id))
        .slice(0, 100);

      const deliveries = await store.bridgePoll({ players, max, done });
      return json({ deliveries, interval: BRIDGE_INTERVAL_S });
    },
  };

  // 2) El comprador aprobó el pago en PayPal: lo cobramos y lo dejamos listo para entregar.
  routes['POST /api/orders/:id/capture'] = async (req, ctx) => {
    if (!paypal) return json({ error: 'Los pagos no están configurados todavía.' }, 503);

    const orderId = ctx.params.id;
    const local = ORDER_ID_RE.test(orderId) ? await store.getOrder(orderId) : null;
    if (!local) return json({ error: 'Pedido no encontrado.' }, 404);

    let result;
    try {
      result = await paypal.captureOrder(orderId);
    } catch (err) {
      // Tarjeta rechazada: el comprador puede elegir otro método sin cerrar PayPal.
      if (err.issue === 'INSTRUMENT_DECLINED') return json({ error: 'El pago fue rechazado.', retry: true }, 402);
      if (err.issue === 'ORDER_ALREADY_CAPTURED') result = await paypal.getOrder(orderId).catch(() => null);
      if (!result) {
        log.error('Error capturando el pago:', err.message);
        return json({ error: 'No se pudo completar el pago.' }, 500);
      }
    }

    try {
      const status = await handleCapture(orderId, result?.purchase_units?.[0]?.payments?.captures?.[0] || null, ctx);
      return json({ id: orderId, status });
    } catch (err) {
      log.error('Error procesando la captura:', err);
      return json({ error: 'Pago recibido, pero hubo un error al procesarlo. Contacta con el staff.' }, 500);
    }
  };

  // Valida lo cobrado contra el pedido guardado y lo pone en la cola de entrega si está completado.
  async function handleCapture(orderId, capture, ctx) {
    const local = await store.getOrder(orderId);
    if (CLAIMED.includes(local.status)) return local.status; // Ya pagado y procesado.
    if (!capture) {
      await store.updateOrder(orderId, { status: 'failed' });
      return 'failed';
    }

    const expected = formatAmount(local.amount, local.currency);
    if (capture.amount?.value !== expected || capture.amount?.currency_code !== local.currency) {
      log.error(`Importe incorrecto en ${orderId}: esperado ${expected} ${local.currency}, recibido`, capture.amount);
      await store.updateOrder(orderId, { status: 'error', error: 'Importe no coincide' });
      return 'error';
    }
    if (capture.status === 'PENDING') {
      await store.updateOrder(orderId, { status: 'awaiting_payment', captureId: capture.id });
      return 'awaiting_payment';
    }
    if (capture.status !== 'COMPLETED') {
      await store.updateOrder(orderId, { status: 'failed', captureId: capture.id });
      return 'failed';
    }

    const product = productById.get(local.productId);
    const commands = [];
    for (let i = 0; i < local.quantity; i++) {
      for (const cmd of product.commands) commands.push(cmd.replaceAll('{player}', local.username));
    }
    const queued = await store.queueDelivery(orderId, { username: local.username, commands, captureId: capture.id });
    if (!queued) return (await store.getOrder(orderId)).status;
    log.log(`Pedido ${orderId} pagado: ${product.name} x${local.quantity} para ${local.username}, en cola del puente`);

    // En Discord: rol y anuncio. Un fallo aquí no afecta a la entrega en el juego.
    await deliverDiscord(orderId, product, ctx.discord);
    return 'queued';
  }

  async function deliverDiscord(orderId, product, discord) {
    const order = await store.getOrder(orderId);
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
      } catch (err) {
        log.error(`No se pudo dar el rol de Discord a ${linked.username}:`, err.message);
        discordStatus = { status: 'failed', error: err.message, roles };
      }
      // El token del comprador ya no hace falta: no lo dejamos guardado.
      await store.updateOrder(orderId, { discord: { id: linked.id, username: linked.username, ...discordStatus } });
    } else if (linked?.accessToken) {
      await store.updateOrder(orderId, { discord: { id: linked.id, username: linked.username } });
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
      log.warn('No se pudo anunciar la compra en Discord:', err.message);
    }
  }

  const compiled = Object.entries(routes).map(([key, handler]) => {
    const [method, path] = key.split(' ');
    const names = [];
    const re = new RegExp(
      `^${path.replace(/:(\w+)/g, (_, name) => {
        names.push(name);
        return '([^/]+)';
      })}$`,
    );
    return { method, re, names, handler };
  });

  // Devuelve la respuesta de la API, o null si la ruta es de la web estática.
  async function handle(request) {
    const url = new URL(request.url);
    const matches = compiled.filter((r) => r.re.test(url.pathname));
    if (!matches.length) return null;
    const route = matches.find((r) => r.method === request.method);
    if (!route) return json({ error: 'Método no permitido.' }, 405);

    const values = url.pathname.match(route.re).slice(1);
    const params = Object.fromEntries(route.names.map((n, i) => [n, decodeURIComponent(values[i])]));
    const secure = (PUBLIC_URL || url.origin).startsWith('https://');
    const discord = discordFor(url.origin);
    try {
      return await route.handler(request, { url, params, secure, discord });
    } catch (err) {
      log.error(`Error en ${request.method} ${url.pathname}:`, err);
      return json({ error: 'Error interno.' }, 500);
    }
  }

  return { handle };
}

