// API de la tienda de Tierras Fantásticas para Cloudflare Workers.
// Pagos con Stripe, cuentas con Discord y el puente con el servidor de Minecraft (mod TF Client).
import products from '../config/products.json' with { type: 'json' };
import { Stripe, toStripeAmount } from './stripe.js';
import { Discord, isSnowflake } from './discord.js';
import { createStore, CLAIMED, UUID_RE } from './store.js';
import { createSession, parseCookies, serializeCookie, randomHex, safeEqual } from './session.js';

const USERNAME_RE = /^[A-Za-z0-9_]{3,16}$/;
const ORDER_ID_RE = /^[A-Z0-9]{5,40}$/;
const LINK_CODE_RE = /^[A-Z0-9]{6}$/;
const MAX_QUANTITY = 10;
// Stripe no cobra menos de 0,50 (en USD/EUR); una mejora de rango nunca baja de aquí.
const MIN_CHARGE = 50;
const DISCORD_COOKIE = 'tf_discord';
const STATE_COOKIE = 'tf_oauth_state';
// Si el puente no ha llamado en este tiempo, damos el servidor por desconectado.
const BRIDGE_FRESH_MS = 45 * 1000;
const BRIDGE_INTERVAL_S = 10;
// Versión del protocolo del puente desde la que el mod pone él mismo {player}/{uuid} en los comandos.
const BRIDGE_TEMPLATES = 2;

const productById = new Map(products.map((p) => [p.id, p]));
const productRoles = (p) => (p.discordRoles || []).filter(isSnowflake);
const ranks = products.filter((p) => p.category === 'rangos' && p.rank && Number.isInteger(p.tier));
const rankById = new Map(ranks.map((r) => [r.id, r]));

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

function orderId() {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  return `TF${[...crypto.getRandomValues(new Uint8Array(16))].map((b) => alphabet[b % alphabet.length]).join('')}`;
}

const head = (name) => `https://mc-heads.net/avatar/${encodeURIComponent(name)}/64`;

// Datos del rango que necesitan la web y el mod (prefijo y color del nametag).
function rankInfo(rank) {
  if (!rank) return null;
  return { id: rank.id, name: rank.name, tier: rank.tier, group: rank.rank.group, prefix: rank.rank.prefix, color: rank.rank.color, hex: rank.rank.hex };
}

export function createApp(env) {
  const SERVER_NAME = env.SERVER_NAME || 'Tierras Fantásticas';
  const SERVER_IP = env.SERVER_IP || '216.163.187.40:19001';
  const DISCORD_URL = env.DISCORD_URL || 'https://discord.gg/tRrunHBZE';
  const CURRENCY = (env.CURRENCY || 'USD').toUpperCase();
  const PUBLIC_URL = (env.PUBLIC_URL || '').replace(/\/$/, '');
  const log = env.LOGGER || console;

  const store = createStore(env.DB);
  const stripe = env.STRIPE_SECRET_KEY ? new Stripe({ secretKey: env.STRIPE_SECRET_KEY, apiBase: env.STRIPE_API_BASE }) : null;

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

  // Cuenta de la web con su Minecraft vinculado (o null si no ha iniciado sesión).
  async function accountOf(request) {
    const user = await discordUser(request);
    if (!user) return null;
    const account = await store.getAccount(user.id);
    return { user, minecraft: account?.minecraft || null };
  }

  // Precio de un producto para un jugador: los rangos se mejoran pagando solo la diferencia.
  async function quote(product, uuid) {
    if (!rankById.has(product.id)) return { unit: product.price };
    const current = await store.getRank(uuid);
    const owned = current && rankById.get(current.rankId);
    if (current && current.tier >= product.tier) {
      const name = owned?.name || 'un rango igual o superior';
      return { blocked: `Este jugador ya tiene ${name}. Solo puedes mejorar a un rango superior.` };
    }
    if (!owned) return { unit: product.price };
    return { unit: Math.max(MIN_CHARGE, product.price - owned.price), from: owned };
  }

  function orderView(order) {
    const product = productById.get(order.productId);
    return {
      id: order.id,
      status: order.status,
      username: order.username,
      product: product?.name || order.productId,
      image: product?.image || null,
      quantity: order.quantity,
      amount: order.amount,
      currency: order.currency,
      upgradeFrom: order.rankFrom ? productById.get(order.rankFrom)?.name || null : null,
      refunded: order.refunded || null,
      createdAt: order.createdAt,
      deliveredAt: order.deliveredAt,
      discord: order.discord ? { username: order.discord.username, status: order.discord.status || null } : null,
    };
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
        paymentsEnabled: Boolean(stripe),
        // Solo se puede comprar a jugadores que el puente ya conoce.
        bridge: Boolean(env.BRIDGE_SECRET),
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
      const account = await accountOf(req);
      if (!account) return json({ discord: null, minecraft: null });
      const { user, minecraft } = account;
      const rank = minecraft ? await store.getRank(minecraft.uuid) : null;
      return json({
        discord: { id: user.id, username: user.username, avatar: user.avatar },
        minecraft: minecraft ? { ...minecraft, head: head(minecraft.name), rank: rankInfo(rankById.get(rank?.rankId)) } : null,
      });
    },

    // ¿Existe este jugador en el servidor? La tienda solo vende a nombres que el servidor ha visto entrar.
    'GET /api/player/:name': async (req, { params, url }) => {
      if (!USERNAME_RE.test(params.name)) return json({ found: false, error: 'Nombre no válido.' }, 400);
      const player = await store.findPlayer(params.name);
      if (!player) return json({ found: false });
      const status = await store.serverStatus();
      const fresh = status && Date.now() - status.at <= BRIDGE_FRESH_MS;
      const online = Boolean(fresh && status.players.some((p) => p.uuid?.toLowerCase() === player.uuid));
      const rank = await store.getRank(player.uuid);
      const out = { found: true, name: player.name, uuid: player.uuid, online, head: head(player.name), rank: rankInfo(rankById.get(rank?.rankId)) };
      // Si nos dicen qué producto quiere, devolvemos el precio para ese jugador (mejora de rango).
      const product = productById.get(url.searchParams.get('product'));
      if (product) {
        const q = await quote(product, player.uuid);
        out.quote = q.blocked ? { blocked: q.blocked } : { unit: q.unit, upgradeFrom: q.from?.name || null };
      }
      return json(out);
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

    // --- Iniciar sesión con Discord (OAuth2) ---
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
        await store.upsertAccount(user);
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

    // --- Cuenta ---
    // Código para escribir en el juego (/tf vincular CÓDIGO) y unir la cuenta con el jugador.
    'POST /api/account/link': async (req) => {
      const user = await discordUser(req);
      if (!user) return json({ error: 'Inicia sesión con Discord primero.' }, 401);
      await store.upsertAccount(user);
      const { code, expiresAt } = await store.createLinkCode(user.id);
      return json({ code, expiresAt, command: `/tf vincular ${code}` });
    },

    // La página de la cuenta consulta si el código ya se usó en el juego.
    'GET /api/account/link': async (req) => {
      const account = await accountOf(req);
      if (!account) return json({ error: 'Inicia sesión con Discord primero.' }, 401);
      const pending = await store.pendingLinkCode(account.user.id);
      return json({ minecraft: account.minecraft, pending: pending ? { code: pending.code, expiresAt: pending.expiresAt } : null });
    },

    'POST /api/account/unlink': async (req) => {
      const user = await discordUser(req);
      if (!user) return json({ error: 'Inicia sesión con Discord primero.' }, 401);
      await store.unlinkMinecraft(user.id);
      return json({ ok: true });
    },

    'GET /api/account/orders': async (req) => {
      const account = await accountOf(req);
      if (!account) return json({ error: 'Inicia sesión con Discord primero.' }, 401);
      const orders = await store.ordersFor({ discordId: account.user.id, uuid: account.minecraft?.uuid });
      return json({ orders: orders.map(orderView) });
    },

    // --- Compra ---
    // 1) Crea el pago en Stripe y devuelve su página. El precio sale del catálogo, nunca del navegador,
    //    y el jugador tiene que existir en el servidor: la entrega va a su UUID, sin errores de nombre.
    'POST /api/checkout': async (req, { url }) => {
      if (!stripe) return json({ error: 'Los pagos no están configurados todavía.' }, 503);

      const body = (await readJson(req)) || {};
      const product = productById.get(body.productId);
      const quantity = Number.parseInt(body.quantity ?? 1, 10);
      if (!product) return json({ error: 'Producto no válido.' }, 400);
      if (typeof body.username !== 'string' || !USERNAME_RE.test(body.username)) {
        return json({ error: 'Nombre de usuario de Minecraft no válido (3-16 letras, números o _).' }, 400);
      }
      const maxQuantity = product.maxQuantity || MAX_QUANTITY;
      if (!Number.isInteger(quantity) || quantity < 1 || quantity > maxQuantity) {
        return json({ error: `La cantidad debe estar entre 1 y ${maxQuantity}.` }, 400);
      }

      const player = await store.findPlayer(body.username);
      if (!player) {
        return json(
          { error: `No encontramos a ${body.username} en ${SERVER_NAME}. Entra al servidor al menos una vez con ese nombre y vuelve a intentarlo.`, code: 'unknown_player' },
          404,
        );
      }
      // Si el navegador dice qué UUID vio, tiene que ser el mismo: así nadie compra para otro jugador por error.
      if (body.uuid && String(body.uuid).toLowerCase() !== player.uuid) {
        return json({ error: 'El jugador ha cambiado. Vuelve a comprobar el nombre.', code: 'player_changed' }, 409);
      }

      const q = await quote(product, player.uuid);
      if (q.blocked) return json({ error: q.blocked, code: 'rank_owned' }, 409);

      const id = orderId();
      const base = PUBLIC_URL || url.origin;
      const amount = q.unit * quantity;
      const account = await discordUser(req);
      const name = q.from ? `${product.name} (mejora desde ${q.from.name})` : product.name;
      try {
        const checkout = await stripe.createCheckoutSession({
          orderId: id,
          currency: CURRENCY,
          unitAmount: q.unit,
          quantity,
          name,
          description: `Para ${player.name} en ${SERVER_NAME}. ${product.description || ''}`.trim(),
          image: base.startsWith('https://') && product.image ? `${base}/${product.image.replace(/^\//, '')}` : undefined,
          successUrl: `${base}/success?order=${id}`,
          cancelUrl: `${base}/tienda?cancel=${id}`,
          metadata: { order_id: id, product_id: product.id, player: player.name, uuid: player.uuid },
        });
        await store.createOrder({
          id,
          status: 'pending',
          username: player.name,
          uuid: player.uuid,
          productId: product.id,
          quantity,
          amount,
          currency: CURRENCY,
          // El token de Discord sirve para meter al comprador en el servidor de Discord con su rol.
          discord: account ? { id: account.id, username: account.username, accessToken: account.accessToken } : null,
          discordId: account?.id,
          sessionId: checkout.id,
          rankFrom: q.from?.id,
        });
        return json({ id, url: checkout.url });
      } catch (err) {
        log.error('Error creando el pago en Stripe:', err.message);
        return json({ error: 'No se pudo iniciar el pago. Inténtalo de nuevo.' }, 502);
      }
    },

    'GET /api/order/:id': async (req, { params }) => {
      const order = ORDER_ID_RE.test(params.id) ? await store.getOrder(params.id) : null;
      if (!order) return json({ error: 'Pedido no encontrado.' }, 404);
      return json(orderView(order));
    },

    // 2) Webhook de Stripe: la confirmación del pago que de verdad cuenta (firmada por Stripe).
    'POST /webhook/stripe': async (req, ctx) => {
      if (!stripe || !env.STRIPE_WEBHOOK_SECRET) return new Response('Webhook no configurado', { status: 503 });

      const payload = await req.text();
      const valid = await Stripe.verifyWebhook(payload, req.headers.get('Stripe-Signature'), env.STRIPE_WEBHOOK_SECRET);
      if (!valid) return new Response('Firma no válida', { status: 400 });

      let event;
      try {
        event = JSON.parse(payload);
      } catch {
        return new Response('JSON no válido', { status: 400 });
      }

      try {
        const object = event.data?.object || {};
        switch (event.type) {
          case 'checkout.session.completed':
          case 'checkout.session.async_payment_succeeded':
          case 'checkout.session.async_payment_failed':
          case 'checkout.session.expired':
            await handleSession(object, ctx, event.type);
            break;
          case 'charge.refunded':
          case 'charge.dispute.created':
            await handleReversal(object, event.type);
            break;
          default:
            break;
        }
      } catch (err) {
        // 500 para que Stripe reintente más tarde.
        log.error('Error procesando el webhook de Stripe:', err);
        return new Response('Error interno', { status: 500 });
      }
      return json({ received: true });
    },

    // 3) Respaldo: la página de confirmación pide a Stripe el estado del pago por si el webhook tarda.
    'POST /api/order/:id/sync': async (req, ctx) => {
      const order = ORDER_ID_RE.test(ctx.params.id) ? await store.getOrder(ctx.params.id) : null;
      if (!order) return json({ error: 'Pedido no encontrado.' }, 404);
      if (stripe && order.sessionId && ['pending', 'awaiting_payment'].includes(order.status)) {
        try {
          await handleSession(await stripe.retrieveCheckoutSession(order.sessionId), ctx, 'sync');
        } catch (err) {
          log.warn(`No se pudo consultar el pago de ${order.id}:`, err.message);
        }
      }
      return json(orderView(await store.getOrder(order.id)));
    },

    // --- Puente con el servidor de Minecraft (mod TF Client instalado en el servidor) ---
    // Cada pocos segundos el servidor manda sus jugadores (conectados y vistos), las entregas que ya hizo,
    // los códigos de vinculación escritos en el juego y los cambios de rango del staff. Recibe las compras
    // pendientes de los jugadores conectados y el rango de cada uno para su nametag.
    'POST /bridge/poll': async (req) => {
      if (!env.BRIDGE_SECRET) return json({ error: 'Puente no configurado (falta BRIDGE_SECRET).' }, 503);
      const auth = req.headers.get('Authorization') || '';
      if (!safeEqual(auth, `Bearer ${env.BRIDGE_SECRET}`)) return json({ error: 'Clave del puente incorrecta.' }, 401);

      const body = (await readJson(req)) || {};
      const list = (value, limit) => (Array.isArray(value) ? value : []).slice(0, limit);
      const validPlayer = (p) => p && typeof p.name === 'string' && USERNAME_RE.test(p.name) && typeof p.uuid === 'string' && UUID_RE.test(p.uuid);
      const ms = Date.now();

      const players = list(body.players, 500)
        .filter((p) => p && typeof p.name === 'string' && USERNAME_RE.test(p.name))
        .map((p) => ({ name: p.name, uuid: typeof p.uuid === 'string' && UUID_RE.test(p.uuid) ? p.uuid.toLowerCase() : null }));
      // Los conectados cuentan como vistos ahora mismo; `seen` trae los que entraron alguna vez (usercache.json).
      const seen = [
        ...list(body.seen, 2000)
          .filter(validPlayer)
          .map((p) => ({ name: p.name, uuid: p.uuid, at: Number.isFinite(p.at) && p.at > 0 && p.at <= ms ? Math.floor(p.at) : ms - 1 })),
        ...players.filter((p) => p.uuid).map((p) => ({ ...p, at: ms })),
      ];
      await store.upsertPlayers(seen);

      // /tf vincular CÓDIGO escrito dentro del juego.
      const linkResults = [];
      for (const link of list(body.links, 20)) {
        if (!link || !validPlayer(link) || typeof link.code !== 'string') continue;
        const code = link.code.trim().toUpperCase();
        const account = LINK_CODE_RE.test(code) ? await store.useLinkCode(code, link) : null;
        linkResults.push({ uuid: link.uuid.toLowerCase(), ok: Boolean(account), account: account?.username || null });
      }

      // Rangos puestos o quitados por el staff con /tf rango (sustituyen al comprado).
      for (const change of list(body.ranks, 50)) {
        if (!change || typeof change.uuid !== 'string' || !UUID_RE.test(change.uuid)) continue;
        if (change.rank === null) await store.setRank(change.uuid, null, 0, { force: true });
        else if (rankById.has(change.rank)) await store.setRank(change.uuid, change.rank, rankById.get(change.rank).tier, { force: true });
      }

      const max = Number.isInteger(body.max) ? body.max : null;
      const done = list(body.done, 100).filter((d) => d && Number.isInteger(d.id));
      const templates = Number(body.protocol) >= BRIDGE_TEMPLATES;
      const deliveries = (await store.bridgePoll({ players, max, done })).map(({ productId, commands, ...d }) => ({
        ...d,
        product: productById.get(productId)?.name || productId,
        // Las versiones antiguas del mod ejecutan los comandos tal cual: les ponemos nosotros el nombre.
        commands: templates ? commands : commands.map((c) => c.replaceAll('{player}', d.player).replaceAll('{uuid}', d.uuid || '')),
      }));

      const online = players.filter((p) => p.uuid).map((p) => p.uuid);
      const playerRanks = (await store.ranksFor(online)).map((r) => ({ uuid: r.uuid, ...rankInfo(rankById.get(r.rankId)) })).filter((r) => r.id);

      return json({
        deliveries,
        interval: BRIDGE_INTERVAL_S,
        ranks: playerRanks,
        rankList: ranks.map(rankInfo),
        linkResults,
        store: (PUBLIC_URL || new URL(req.url).origin).replace(/^https?:\/\//, ''),
      });
    },
  };

  // Un pago de Stripe terminó (o caducó): valida lo cobrado contra el pedido y lo pone en la cola de entrega.
  async function handleSession(checkout, ctx, type) {
    const id = checkout.client_reference_id || checkout.metadata?.order_id;
    const local = id && ORDER_ID_RE.test(id) ? await store.getOrder(id) : null;
    if (!local) return null;
    if (CLAIMED.includes(local.status)) return local.status; // Ya pagado y procesado.
    if (local.sessionId && checkout.id && checkout.id !== local.sessionId) {
      log.error(`El pago ${checkout.id} no es el del pedido ${id}`);
      return local.status;
    }

    if (type === 'checkout.session.expired' || checkout.status === 'expired') {
      if (local.status === 'pending') await store.updateOrder(id, { status: 'expired' });
      return 'expired';
    }
    if (type === 'checkout.session.async_payment_failed') {
      await store.updateOrder(id, { status: 'failed' });
      return 'failed';
    }
    if (checkout.status !== 'complete') return local.status;

    const paymentIntent = typeof checkout.payment_intent === 'string' ? checkout.payment_intent : checkout.payment_intent?.id;
    if (checkout.payment_status === 'unpaid') {
      // Pagos que tardan en confirmarse (transferencias, etc.): Stripe avisará con async_payment_succeeded.
      await store.updateOrder(id, { status: 'awaiting_payment', paymentIntent });
      return 'awaiting_payment';
    }
    if (checkout.payment_status !== 'paid') return local.status;

    const expected = toStripeAmount(local.amount, local.currency);
    if (checkout.amount_total !== expected || String(checkout.currency).toUpperCase() !== local.currency) {
      log.error(`Importe incorrecto en ${id}: esperado ${expected} ${local.currency}, cobrado ${checkout.amount_total} ${checkout.currency}`);
      await store.updateOrder(id, { status: 'error', error: 'Importe no coincide', paymentIntent });
      return 'error';
    }

    const product = productById.get(local.productId);
    const rank = rankById.get(product.id);
    // Los comandos se guardan con {player}/{uuid}: el mod los rellena al entregar con el nombre actual del UUID.
    const commands = [];
    for (let i = 0; i < local.quantity; i++) commands.push(...product.commands);
    if (rank) {
      // Al mejorar se quitan los grupos de los rangos inferiores (si no los tiene, LuckPerms no hace nada).
      for (const lower of ranks) if (lower.tier < rank.tier) commands.push(`lp user {player} parent remove ${lower.rank.group}`);
    }
    const extra = {
      kind: product.category,
      color: rank?.rank.hex || product.colors?.[1] || '#f4c95d',
      rank: rankInfo(rank),
      upgradeFrom: local.rankFrom ? productById.get(local.rankFrom)?.name || null : null,
    };

    const queued = await store.queueDelivery(id, { username: local.username, uuid: local.uuid, commands, extra, paymentIntent });
    if (!queued) return (await store.getOrder(id)).status;
    if (rank && local.uuid) await store.setRank(local.uuid, rank.id, rank.tier);
    log.log(`Pedido ${id} pagado: ${product.name} x${local.quantity} para ${local.username} (${local.uuid}), en cola del puente`);

    // En Discord: rol y anuncio. Un fallo aquí no afecta a la entrega en el juego.
    await deliverDiscord(id, product, ctx.discord);
    return 'queued';
  }

  // Reembolsos y disputas: se apuntan en el pedido para que el staff retire lo entregado si procede.
  async function handleReversal(object, type) {
    const paymentIntent = typeof object.payment_intent === 'string' ? object.payment_intent : object.payment_intent?.id;
    const order = paymentIntent ? await store.findOrderByPaymentIntent(paymentIntent) : null;
    if (!order) return;
    const what = type === 'charge.dispute.created' ? 'disputa' : object.refunded ? 'reembolsado' : 'reembolso parcial';
    log.warn(`Pedido ${order.id} (${order.username}): ${what}`);
    await store.updateOrder(order.id, { refunded: what });
  }

  async function deliverDiscord(orderId, product, discord) {
    const order = await store.getOrder(orderId);
    // Si compró sin iniciar sesión, usamos la cuenta de Discord vinculada a su jugador.
    let linked = order.discord;
    if (!linked && order.uuid) {
      const account = await store.accountByUuid(order.uuid);
      if (account) linked = { id: account.discordId, username: account.username };
    }
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
      const rank = rankById.get(product.id);
      const upgrade = order.rankFrom ? productById.get(order.rankFrom)?.name : null;
      await discord.announce({
        embed: {
          title: rank ? `👑 ¡${order.username} ahora es ${rank.rank.prefix}!` : '🎉 ¡Nueva compra en la tienda!',
          description: `${who} ha conseguido **${product.name}**${order.quantity > 1 ? ` ×${order.quantity}` : ''}${upgrade ? ` (mejora desde ${upgrade})` : ''}. ¡Gracias por apoyar ${SERVER_NAME}!`,
          color: Number.parseInt((rank?.rank.hex || product.colors?.[1] || '#f4c95d').slice(1), 16),
          thumbnail: { url: head(order.username) },
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

    let params;
    try {
      const values = url.pathname.match(route.re).slice(1);
      params = Object.fromEntries(route.names.map((n, i) => [n, decodeURIComponent(values[i])]));
    } catch {
      return json({ error: 'Ruta no válida.' }, 400);
    }
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
