// API de la tienda de Tierras Fantásticas para Cloudflare Workers.
// Pagos con Stripe, cuentas de jugador (nombre de Minecraft + contraseña) con su Discord conectado,
// y el puente con el servidor de Minecraft (mod TF Client).
import products from '../config/products.json' with { type: 'json' };
import legal from '../config/legal.json' with { type: 'json' };
import vfx from '../config/vfx.json' with { type: 'json' };
import { Stripe, toStripeAmount } from './stripe.js';
import { PayPal, formatAmount } from './paypal.js';
import { Discord, isSnowflake } from './discord.js';
import { createStore, CLAIMED, UUID_RE } from './store.js';
import { createSession, parseCookies, serializeCookie, randomHex, safeEqual } from './session.js';
import { hashPassword, verifyPassword } from './password.js';

const USERNAME_RE = /^[A-Za-z0-9_]{3,16}$/;
const ORDER_ID_RE = /^[A-Z0-9]{5,40}$/;
const PASSWORD_MIN = 8;
const PASSWORD_MAX = 128;
// La sesión dura 60 días desde la última visita.
const SESSION_DAYS = 60;
const MAX_QUANTITY = 10;
// Stripe no cobra menos de 0,50 (en USD/EUR); una mejora de rango nunca baja de aquí.
const MIN_CHARGE = 50;
const SESSION_COOKIE = 'tf_session';
const STATE_COOKIE = 'tf_oauth_state';
// Si el puente no ha llamado en este tiempo, damos el servidor por desconectado.
const BRIDGE_FRESH_MS = 45 * 1000;
const BRIDGE_INTERVAL_S = 10;
// Versión del protocolo del puente desde la que el mod pone él mismo {player}/{uuid} en los comandos.
const BRIDGE_TEMPLATES = 2;

// Versión vigente de los Términos y del Aviso de privacidad (la fecha de config/legal.json, la misma que muestran).
const TERMS_VERSION = legal.version;
const TERMS_ERROR = 'Marca la casilla para aceptar los Términos y condiciones y el Aviso de privacidad.';

const productById = new Map(products.map((p) => [p.id, p]));
// VFX (efectos de kill sueltos y paquetes de skills): por ahora todos gratis.
const VFX_FREE = true;
const vfxKills = new Set(vfx.kills.map((k) => k.id));
const vfxPacks = new Set(vfx.packs.map((p) => p.id));
function vfxItem(kind, id) {
  if (typeof id !== 'string') return null;
  if (kind === 'kill' && vfxKills.has(id)) return { kind, id, key: `kill:${id}` };
  if (kind === 'pack' && vfxPacks.has(id)) return { kind, id, key: `pack:${id}` };
  return null;
}
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
  // Grupo de WhatsApp. La web siempre enlaza a /whatsapp: si algún día el enlace del grupo cambia, basta con poner el
  // nuevo en la variable WHATSAPP_URL de Cloudflare (o aquí) y todos los botones siguen funcionando.
  const WHATSAPP_URL = env.WHATSAPP_URL || 'https://chat.whatsapp.com/CqULG1UFixUJFuN3xwtXrm';
  const CURRENCY = (env.CURRENCY || 'USD').toUpperCase();
  const PUBLIC_URL = (env.PUBLIC_URL || '').replace(/\/$/, '');
  const log = env.LOGGER || console;

  const store = createStore(env.DB);
  const stripe = env.STRIPE_SECRET_KEY ? new Stripe({ secretKey: env.STRIPE_SECRET_KEY, apiBase: env.STRIPE_API_BASE }) : null;
  // PayPal (opcional): con PAYPAL_CLIENT_ID y PAYPAL_CLIENT_SECRET en Cloudflare aparece el botón «Pagar con PayPal».
  // PAYPAL_ENV = 'sandbox' para pruebas (por defecto, live).
  const paypal =
    env.PAYPAL_CLIENT_ID && env.PAYPAL_CLIENT_SECRET
      ? new PayPal({ clientId: env.PAYPAL_CLIENT_ID, clientSecret: env.PAYPAL_CLIENT_SECRET, env: env.PAYPAL_ENV || 'live', apiBase: env.PAYPAL_API_BASE })
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

  // La cuenta con la sesión iniciada (o null). La cookie guarda solo el UUID, firmado.
  async function currentUser(request) {
    const data = await (await session()).decode(parseCookies(request.headers.get('Cookie'))[SESSION_COOKIE]);
    if (!data?.uuid) return null;
    const user = await store.getUser(data.uuid);
    // Si cambió la contraseña después de iniciar sesión, las sesiones viejas dejan de valer.
    if (!user || (data.v && data.v !== user.password.slice(-12))) return null;
    return user;
  }

  async function sessionCookie(user, secure) {
    const exp = Date.now() + SESSION_DAYS * 24 * 3600 * 1000;
    const value = await (await session()).encode({ uuid: user.uuid, v: user.password.slice(-12), exp });
    return serializeCookie(SESSION_COOKIE, value, { maxAge: SESSION_DAYS * 24 * 3600, secure });
  }

  // Lo que la web enseña de la cuenta (nunca la contraseña).
  async function userView(user) {
    const rank = await store.getRank(user.uuid);
    const player = await store.playerByUuid(user.uuid);
    const name = player?.name || user.name;
    return {
      name,
      uuid: user.uuid,
      head: head(name),
      rank: rankInfo(rankById.get(rank?.rankId)),
      // Monedas del servidor (último saldo que mandó el puente; null si nunca lo mandó)
      coins: player?.coins ?? null,
      coinsAt: player?.coinsAt ?? null,
      discord: user.discord,
      // Si aceptó la versión vigente de los Términos; si no, la web se los vuelve a pedir antes de comprar o reclamar.
      termsOk: user.termsVersion === TERMS_VERSION,
      termsVersion: user.termsVersion,
      termsAt: user.termsAt,
    };
  }

  // Prueba de la aceptación de los Términos: versión, fecha, quién, dónde y desde qué IP y navegador.
  async function recordTerms(req, { uuid, username, context, orderId }) {
    await store.acceptTerms({
      uuid,
      username,
      context,
      version: TERMS_VERSION,
      orderId,
      ip: req.headers.get('CF-Connecting-IP') || null,
      userAgent: req.headers.get('User-Agent') || null,
    });
  }

  // Lo que se hace con la cuenta (recompensas, tienda de monedas) necesita haber aceptado la versión vigente.
  const termsMissing = (user) =>
    user.termsVersion === TERMS_VERSION ? null : json({ error: 'Acepta los Términos y condiciones actualizados para seguir.', code: 'terms' }, 403);

  function checkPassword(password) {
    if (typeof password !== 'string' || password.length < PASSWORD_MIN) return `La contraseña debe tener al menos ${PASSWORD_MIN} caracteres.`;
    if (password.length > PASSWORD_MAX) return 'La contraseña es demasiado larga.';
    return null;
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
    // Tienda de monedas: el objeto comprado va guardado en el pedido (puede dejar de venderse después)
    const shopItem = order.productId?.startsWith('tienda:') ? order.prizes?.[0] || null : null;
    return {
      id: order.id,
      status: order.status,
      username: order.username,
      kind: shopItem ? 'tienda' : order.id.startsWith('COIN') ? 'ruleta' : null,
      product: shopItem ? `${shopItem.count > 1 ? `${shopItem.count}× ` : ''}${shopItem.name}` : product?.name || order.productId,
      image: shopItem ? shopItem.icon || null : product?.image || null,
      quantity: order.quantity,
      amount: order.amount,
      currency: order.currency,
      upgradeFrom: order.rankFrom ? productById.get(order.rankFrom)?.name || null : null,
      prizes: order.prizes && !shopItem
        ? order.prizes.map((p) => ({ ...p, thumb: p.icon || (product?.set ? `/img/items/${product.set}/${p.id}.webp` : null) }))
        : null,
      refunded: order.refunded || null,
      coins: order.id.startsWith('COIN') ? (shopItem ? shopItem.price : product?.coinPrice) || null : null,
      error: order.id.startsWith('COIN') && order.status === 'delivery_failed' ? order.error || null : null,
      createdAt: order.createdAt,
      deliveredAt: order.deliveredAt,
      discord: order.discord ? { username: order.discord.username, status: order.discord.status || null } : null,
    };
  }

  // Icono de un objeto de la tienda de monedas: los del TF Client tienen miniatura en la web.
  const setIds = [...new Set(products.map((p) => p.set).filter(Boolean))].sort((a, b) => b.length - a.length);
  function coinShopIcon(item) {
    const vanilla = /^minecraft:([a-z0-9_]+)$/.exec(item || '');
    if (vanilla) return `/api/itemicon/${vanilla[1]}`;
    const m = /^tfclient:([a-z0-9_]+)$/.exec(item || '');
    if (!m) return null;
    const set = setIds.find((s) => m[1].startsWith(`${s}_`));
    return set ? `/img/items/${set}/${m[1].slice(set.length + 1)}.webp` : null;
  }

  // La ruleta para el servidor (los giros con monedas desde la web, /tf web ruleta girar): premios, armas y precio.
  function rouletteForServer() {
    const ruleta = products.find((p) => p.category === 'ruleta' && p.spins === 1) || products.find((p) => p.category === 'ruleta');
    if (!ruleta) return null;
    return {
      set: ruleta.set,
      coinPrice: Math.round((ruleta.coinPrice || 0) / (ruleta.spins || 1)),
      pool: (ruleta.pool || []).map(({ icon, ...p }) => p),
      weapons: (ruleta.models || []).map((m) => ({ id: m.id, name: m.name })),
    };
  }

  // Premios que manda el servidor (ruleta con monedas): solo id y nombre, cortos; el icono lo pone la web.
  function cleanPrizes(prizes) {
    if (!Array.isArray(prizes)) return null;
    const ruleta = products.find((p) => p.category === 'ruleta');
    const pool = new Map((ruleta?.pool || []).map((p) => [p.id, p]));
    const weapons = new Set((ruleta?.models || []).map((m) => m.id));
    const out = [];
    for (const p of prizes.slice(0, 50)) {
      if (!p || typeof p.id !== 'string' || typeof p.name !== 'string') continue;
      const id = p.id.slice(0, 40);
      const weapon = p.weapon === true && weapons.has(id);
      if (!weapon && !pool.has(id)) continue;
      out.push({
        id,
        name: p.name.slice(0, 64),
        ...(weapon ? { weapon: true, icon: `/img/items/${ruleta.set}/${id}.webp` } : { icon: pool.get(id).icon }),
      });
    }
    return out.length ? out : null;
  }

  function cleanShopOp(op) {
    if (!op || typeof op !== 'object') return null;
    if (op.op === 'clear') return { op: 'clear' };
    const id = typeof op.id === 'string' && /^[a-z0-9_-]{1,40}$/.test(op.id) ? op.id : null;
    if (!id) return null;
    if (op.op === 'remove') return { op: 'remove', id };
    if (op.op !== 'add') return null;
    const item = typeof op.item === 'string' && /^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(op.item) ? op.item : null;
    const price = Number(op.price);
    const count = Number(op.count);
    if (!item || !Number.isInteger(price) || price < 1 || price > 1e9 || !Number.isInteger(count) || count < 1 || count > 6400) return null;
    const name = String(op.name || item).replace(/[\u0000-\u001f]/g, '').slice(0, 64);
    const nbt = typeof op.nbt === 'string' && op.nbt.length <= 8000 ? op.nbt : null;
    const by = typeof op.by === 'string' && USERNAME_RE.test(op.by) ? op.by : null;
    return { op: 'add', id, item, name, count, price, nbt, by };
  }

  // Enlace al Discord sin invitador (widget o bot, ver Discord.serverInvite); se guarda 6 horas en settings. Si no se
  // puede, el DISCORD_URL de siempre.
  async function discordInvite(discord) {
    try {
      const cached = await store.getSetting('discord_invite');
      if (cached && Date.now() - cached.at < 6 * 3600_000) return cached.value ? `https://discord.gg/${cached.value}` : DISCORD_URL;
      const code = await discord.serverInvite();
      await store.setSetting('discord_invite', code || '');
      return code ? `https://discord.gg/${code}` : DISCORD_URL;
    } catch (err) {
      log.warn('No se pudo sacar la invitación de Discord:', err.message);
      return DISCORD_URL;
    }
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
        paymentsEnabled: Boolean(stripe || paypal),
        cardEnabled: Boolean(stripe),
        paypalEnabled: Boolean(paypal),
        discordInvite: DISCORD_URL,
        termsVersion: TERMS_VERSION,
        // Solo se puede comprar a jugadores que el puente ya conoce.
        bridge: Boolean(env.BRIDGE_SECRET),
      }),

    // Los comandos y los IDs de rol nunca se envían al navegador.
    'GET /api/products': async (req, { discord }) =>
      json(
        products.map(({ commands, discordRoles, ...p }) => ({
          ...p,
          ...(p.pool ? { pool: p.pool.map(({ give, ...entry }) => entry) } : {}),
          discordRole: discord.rolesEnabled && productRoles({ discordRoles }).length > 0,
        })),
      ),

    // VFX: el catálogo y, con la cuenta iniciada, lo que tiene y lleva equipado el jugador.
    'GET /api/vfx': async (req) => {
      const user = await currentUser(req);
      return json({ free: VFX_FREE, cats: vfx.cats, kills: vfx.kills, packs: vfx.packs, me: user ? await store.vfxFor(user.uuid) : null });
    },

    // Obtener un VFX (gratis): queda en la cuenta y se equipa; el servidor lo pone en la siguiente consulta del puente.
    'POST /api/vfx/claim': async (req) => {
      const user = await currentUser(req);
      if (!user) return json({ error: 'Inicia sesión para obtener efectos.', code: 'login' }, 401);
      const terms = termsMissing(user);
      if (terms) return terms;
      const body = (await readJson(req)) || {};
      const item = vfxItem(body.kind, body.id);
      if (!item) return json({ error: 'Ese efecto no existe.' }, 400);
      if (!VFX_FREE) return json({ error: 'Este efecto ya no es gratis.' }, 403);
      await store.vfxGive(user.uuid, item.key);
      await store.vfxEquip(user.uuid, item.kind, item.id);
      return json({ me: await store.vfxFor(user.uuid) });
    },

    // Equipar uno que ya tiene, o quitarlo (id null).
    'POST /api/vfx/equip': async (req) => {
      const user = await currentUser(req);
      if (!user) return json({ error: 'Inicia sesión para equipar efectos.', code: 'login' }, 401);
      const body = (await readJson(req)) || {};
      if (body.kind !== 'kill' && body.kind !== 'pack') return json({ error: 'Petición no válida.' }, 400);
      if (body.id != null) {
        const item = vfxItem(body.kind, body.id);
        if (!item) return json({ error: 'Ese efecto no existe.' }, 400);
        const mine = await store.vfxFor(user.uuid);
        if (!mine.owned.includes(item.key)) return json({ error: 'Primero tienes que obtenerlo.', code: 'not_owned' }, 403);
      }
      await store.vfxEquip(user.uuid, body.kind, body.id || null);
      return json({ me: await store.vfxFor(user.uuid) });
    },

    // Cada visita renueva la sesión: quien entra a menudo no tiene que volver a iniciar sesión.
    'GET /api/me': async (req, { secure }) => {
      const user = await currentUser(req);
      if (!user) return json({ user: null });
      return json({ user: await userView(user) }, 200, { 'Set-Cookie': await sessionCookie(user, secure) });
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

    // Skin del jugador para el probador (desde aquí, para que el navegador pueda usarla en 3D sin problemas de CORS).
    'GET /api/skin/:name': async (req, { params }) => {
      const name = USERNAME_RE.test(params.name) || params.name === 'MHF_Steve' ? params.name : 'MHF_Steve';
      try {
        const res = await fetch(`https://mc-heads.net/skin/${encodeURIComponent(name)}`, { cf: { cacheTtl: 3600 } });
        if (!res.ok || !String(res.headers.get('content-type')).startsWith('image/')) throw new Error(String(res.status));
        return new Response(res.body, { headers: { 'Content-Type': 'image/png', 'Cache-Control': 'public, max-age=3600' } });
      } catch {
        return new Response('Skin no disponible', { status: 404 });
      }
    },

    // Icono de un objeto de Minecraft para la tienda de monedas (textura del objeto o, si es un bloque, de su cara).
    'GET /api/itemicon/:name': async (req, { params }) => {
      if (!/^[a-z0-9_]{1,64}$/.test(params.name)) return new Response('No válido', { status: 400 });
      const base = 'https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.20.1/assets/minecraft/textures';
      const plain = params.name.replace(/^enchanted_/, '');
      const names = [`item/${params.name}`, `item/${plain}`, `block/${params.name}`, `block/${params.name}_top`, `block/${params.name}_front`];
      for (const path of names) {
        try {
          const res = await fetch(`${base}/${path}.png`, { cf: { cacheTtl: 86400 } });
          if (res.ok) {
            return new Response(res.body, { headers: { 'Content-Type': 'image/png', 'Cache-Control': 'public, max-age=86400' } });
          }
        } catch {
          break;
        }
      }
      return new Response('Sin icono', { status: 404 });
    },

    // Tienda de monedas: lo que el staff ha puesto desde el juego. La web la consulta cada pocos segundos.
    'GET /api/coinshop': async () => {
      const items = await store.coinShop();
      return json({
        version: await store.coinShopVersion(),
        items: items.map((it) => ({ id: it.id, item: it.item, name: it.name, count: it.count, price: it.price, icon: coinShopIcon(it.item) })),
      });
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

    // --- Cuentas: crear cuenta y entrar con el nombre de Minecraft y una contraseña ---
    // El nombre tiene que ser el de un jugador que ha entrado al servidor (lo sabe el puente): la cuenta queda
    // unida a su UUID. Cada jugador solo puede tener una cuenta.
    'POST /api/auth/register': async (req, { secure }) => {
      const body = (await readJson(req)) || {};
      if (typeof body.name !== 'string' || !USERNAME_RE.test(body.name)) {
        return json({ error: 'Nombre de Minecraft no válido (3-16 letras, números o _).' }, 400);
      }
      const bad = checkPassword(body.password);
      if (bad) return json({ error: bad }, 400);
      if (body.acceptTerms !== true) return json({ error: TERMS_ERROR, code: 'terms' }, 400);
      const player = await store.findPlayer(body.name);
      if (!player) {
        return json({ error: `${body.name} nunca ha entrado a ${SERVER_NAME}. Entra al servidor una vez con ese nombre y vuelve.`, code: 'unknown_player' }, 404);
      }
      if (!(await store.createUser({ uuid: player.uuid, password: await hashPassword(body.password) }))) {
        return json({ error: `${player.name} ya tiene cuenta. Inicia sesión.`, code: 'exists' }, 409);
      }
      await recordTerms(req, { uuid: player.uuid, username: player.name, context: 'cuenta' });
      const user = await store.getUser(player.uuid);
      log.log(`Cuenta nueva: ${player.name} (${player.uuid})`);
      return json({ user: await userView(user) }, 200, { 'Set-Cookie': await sessionCookie(user, secure) });
    },

    'POST /api/auth/login': async (req, { secure }) => {
      const body = (await readJson(req)) || {};
      const wrong = () => json({ error: 'Nombre o contraseña incorrectos.' }, 401);
      if (typeof body.name !== 'string' || !USERNAME_RE.test(body.name) || typeof body.password !== 'string') return wrong();
      const key = `login:${body.name.toLowerCase()}`;
      const locked = await store.loginLocked(key);
      if (locked) {
        const minutes = Math.max(1, Math.ceil((locked - Date.now()) / 60000));
        return json({ error: `Demasiados intentos. Vuelve a probar en ${minutes} min.` }, 429);
      }
      const player = await store.findPlayer(body.name);
      const user = player ? await store.getUser(player.uuid) : null;
      if (!user || !(await verifyPassword(body.password.slice(0, PASSWORD_MAX), user.password))) {
        await store.loginFailed(key);
        return wrong();
      }
      await store.loginSucceeded(key);
      return json({ user: await userView(user) }, 200, { 'Set-Cookie': await sessionCookie(user, secure) });
    },

    'POST /auth/logout': async (req, { secure }) =>
      json({ ok: true }, 200, { 'Set-Cookie': serializeCookie(SESSION_COOKIE, '', { maxAge: 0, secure }) }),

    'POST /api/account/password': async (req, { secure }) => {
      const user = await currentUser(req);
      if (!user) return json({ error: 'Inicia sesión primero.' }, 401);
      const body = (await readJson(req)) || {};
      if (typeof body.current !== 'string' || !(await verifyPassword(body.current.slice(0, PASSWORD_MAX), user.password))) {
        return json({ error: 'La contraseña actual no es correcta.' }, 401);
      }
      const bad = checkPassword(body.password);
      if (bad) return json({ error: bad }, 400);
      await store.setPassword(user.uuid, await hashPassword(body.password));
      const fresh = await store.getUser(user.uuid);
      return json({ ok: true }, 200, { 'Set-Cookie': await sessionCookie(fresh, secure) });
    },

    // --- Discord (OAuth2) ---
    // Con la sesión iniciada: conecta su Discord a la cuenta. Sin sesión: entra con el Discord ya conectado.
    // Discord nos dice su @ (no se puede escribir a mano) y si está en el servidor de Discord de Tierras Fantásticas.
    // Enlaces fijos de la comunidad: la web enlaza aquí y esto lleva a la invitación vigente (302: nunca se queda en caché).
    'GET /discord': async (req, { discord }) => redirect(await discordInvite(discord)),
    'GET /whatsapp': async () => redirect(WHATSAPP_URL),

    'GET /auth/discord': async (req, { url, discord, secure }) => {
      if (!discord.loginEnabled) return redirect('/cuenta?discord=off');
      const user = await currentUser(req);
      const state = randomHex(16);
      const ret = safeReturn(url.searchParams.get('return') || '/cuenta');
      const cookie = serializeCookie(
        STATE_COOKIE,
        await (await session()).encode({ state, ret, link: user?.uuid || null, exp: Date.now() + 10 * 60 * 1000 }),
        { maxAge: 600, secure },
      );
      return redirect(discord.authorizeUrl(state), [cookie]);
    },

    'GET /auth/discord/callback': async (req, { url, discord, secure }) => {
      const s = await session();
      const saved = await s.decode(parseCookies(req.headers.get('Cookie'))[STATE_COOKIE]);
      const clearState = serializeCookie(STATE_COOKIE, '', { maxAge: 0, secure });
      const ret = saved?.ret || '/cuenta';
      const back = (flag) => `${ret}${ret.includes('?') ? '&' : '?'}discord=${flag}`;
      const state = url.searchParams.get('state');
      const code = url.searchParams.get('code');

      if (!saved || !state || state !== saved.state) return redirect(back('error'), [clearState]);
      if (url.searchParams.get('error') || !code) return redirect(back('cancel'), [clearState]);

      let profile;
      let member = false;
      try {
        profile = await discord.exchangeCode(code);
        member = await discord.isMember(profile.accessToken);
        // Si no está en el servidor de Discord y tenemos bot, lo metemos.
        if (!member && discord.botEnabled) {
          try {
            member = await discord.addMember(profile.id, profile.accessToken);
          } catch (err) {
            log.warn(`No se pudo añadir a ${profile.username} al Discord:`, err.message);
          }
        }
      } catch (err) {
        log.error('Error en el login de Discord:', err.message);
        return redirect(back('error'), [clearState]);
      }

      // Conectar Discord a la cuenta con la sesión iniciada.
      if (saved.link) {
        const ok = await store.setUserDiscord(saved.link, { ...profile, member });
        if (!ok) return redirect(back('taken'), [clearState]);
        return redirect(back(member ? 'ok' : 'notmember'), [clearState]);
      }
      // Entrar con Discord: solo si ya está conectado a una cuenta.
      const user = await store.userByDiscord(profile.id);
      if (!user) return redirect(back('noaccount'), [clearState]);
      await store.setUserDiscord(user.uuid, { ...profile, member });
      return redirect(back(member ? 'ok' : 'notmember'), [clearState, await sessionCookie(user, secure)]);
    },

    // Vuelve a mirar si ya se unió al servidor de Discord (con el bot; sin bot, hay que reconectar).
    'POST /api/account/discord/check': async (req, { discord }) => {
      const user = await currentUser(req);
      if (!user) return json({ error: 'Inicia sesión primero.' }, 401);
      if (!user.discord) return json({ error: 'Conecta tu Discord primero.' }, 400);
      if (!discord.botEnabled) return json({ reconnect: true });
      try {
        const member = await discord.isMemberByBot(user.discord.id);
        await store.setDiscordMember(user.uuid, member);
        return json({ member });
      } catch (err) {
        log.warn('No se pudo comprobar el Discord:', err.message);
        return json({ error: 'No se pudo comprobar ahora. Inténtalo en un momento.' }, 502);
      }
    },

    'POST /api/account/discord/unlink': async (req) => {
      const user = await currentUser(req);
      if (!user) return json({ error: 'Inicia sesión primero.' }, 401);
      await store.unlinkDiscord(user.uuid);
      return json({ ok: true });
    },

    // Aceptar la versión vigente de los Términos con la cuenta (cuando cambian, o si la cuenta es de antes).
    'POST /api/account/terms': async (req) => {
      const user = await currentUser(req);
      if (!user) return json({ error: 'Inicia sesión.', code: 'login' }, 401);
      const body = (await readJson(req)) || {};
      if (body.acceptTerms !== true) return json({ error: TERMS_ERROR, code: 'terms' }, 400);
      const player = await store.playerByUuid(user.uuid);
      await recordTerms(req, { uuid: user.uuid, username: player?.name || user.name, context: 'actualizacion' });
      return json({ user: await userView(await store.getUser(user.uuid)) });
    },

    // Las aceptaciones guardadas de la cuenta (la prueba, para que el jugador la vea).
    'GET /api/account/terms': async (req) => {
      const user = await currentUser(req);
      if (!user) return json({ error: 'Inicia sesión.', code: 'login' }, 401);
      return json({ version: TERMS_VERSION, acceptances: await store.termsAcceptances(user.uuid) });
    },

    'GET /api/account/orders': async (req) => {
      const user = await currentUser(req);
      if (!user) return json({ error: 'Inicia sesión primero.' }, 401);
      const orders = await store.ordersFor({ discordId: user.discord?.id, uuid: user.uuid });
      return json({ orders: orders.map(orderView) });
    },

    // --- Compra ---
    // 1) Crea el pago en Stripe y devuelve su página. El precio sale del catálogo, nunca del navegador,
    //    y el jugador tiene que existir en el servidor: la entrega va a su UUID, sin errores de nombre.
    'POST /api/checkout': async (req, { url }) => {
      const body = (await readJson(req)) || {};
      const method = body.method === 'paypal' ? 'paypal' : 'stripe';
      if (method === 'stripe' && !stripe) {
        return json({ error: paypal ? 'El pago con tarjeta no está disponible ahora mismo. Usa PayPal.' : 'Los pagos no están configurados todavía.' }, 503);
      }
      if (method === 'paypal' && !paypal) return json({ error: 'El pago con PayPal no está disponible ahora mismo.' }, 503);
      const product = productById.get(body.productId);
      const quantity = Number.parseInt(body.quantity ?? 1, 10);
      if (!product) return json({ error: 'Producto no válido.' }, 400);
      if (product.price === 0) return json({ error: 'Este regalo es gratis: reclámalo con tu cuenta.', code: 'free' }, 400);
      if (typeof body.username !== 'string' || !USERNAME_RE.test(body.username)) {
        return json({ error: 'Nombre de usuario de Minecraft no válido (3-16 letras, números o _).' }, 400);
      }
      const maxQuantity = product.maxQuantity || MAX_QUANTITY;
      if (!Number.isInteger(quantity) || quantity < 1 || quantity > maxQuantity) {
        return json({ error: `La cantidad debe estar entre 1 y ${maxQuantity}.` }, 400);
      }
      if (body.acceptTerms !== true) return json({ error: TERMS_ERROR, code: 'terms' }, 400);

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
      // Si el que compra tiene sesión con Discord conectado, el anuncio y el rol van a su Discord.
      const buyer = await currentUser(req);
      const account = buyer?.discord ? { id: buyer.discord.id, username: buyer.discord.username } : null;
      const name = q.from ? `${product.name} (mejora desde ${q.from.name})` : product.name;
      try {
        let checkout;
        if (method === 'paypal') {
          const created = await paypal.createOrder({
            amount,
            currency: CURRENCY,
            description: `Para ${player.name} en ${SERVER_NAME}`.slice(0, 127),
            customId: id,
            item: { name, quantity, unitAmount: q.unit },
            brandName: SERVER_NAME,
            returnUrl: `${base}/success?order=${id}`,
            cancelUrl: `${base}/tienda?cancel=${id}`,
            requestId: `create-${id}`,
          });
          checkout = { id: created.id, url: PayPal.approveUrl(created) };
          if (!checkout.url) throw new Error('PayPal no devolvió el enlace de pago');
        } else {
          checkout = await stripe.createCheckoutSession({
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
        }
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
          discord: account,
          discordId: account?.id,
          sessionId: checkout.id,
          rankFrom: q.from?.id,
          termsVersion: TERMS_VERSION,
          method,
        });
        await recordTerms(req, { uuid: player.uuid, username: player.name, context: 'compra', orderId: id });
        return json({ id, url: checkout.url });
      } catch (err) {
        log.error(`Error creando el pago en ${method === 'paypal' ? 'PayPal' : 'Stripe'}:`, err.message);
        return json({ error: 'No se pudo iniciar el pago. Inténtalo de nuevo.' }, 502);
      }
    },

    // Regalos gratis (precio 0): sin Stripe, con la cuenta iniciada y una sola vez por jugador.
    'POST /api/claim': async (req, ctx) => {
      const user = await currentUser(req);
      if (!user) return json({ error: 'Inicia sesión para reclamar el regalo.', code: 'login' }, 401);
      const terms = termsMissing(user);
      if (terms) return terms;
      const body = (await readJson(req)) || {};
      const product = productById.get(body.productId);
      if (!product || product.price !== 0) return json({ error: 'Ese regalo no existe.' }, 400);
      const player = await store.playerByUuid(user.uuid);
      // El número de pedido sale del jugador y del regalo: el mismo jugador nunca puede reclamarlo dos veces.
      // giftCode fijo en products.json: no cambia aunque se reordene el catálogo.
      const code = String(product.giftCode || product.id).toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 4);
      const id = `GIFT${code}${user.uuid.replaceAll('-', '').toUpperCase()}`.slice(0, 40);
      if (await store.getOrder(id)) return json({ error: 'Ya reclamaste este regalo.', code: 'claimed', id }, 409);
      try {
        await store.createOrder({
          id,
          status: 'pending',
          username: player?.name || user.name,
          uuid: user.uuid,
          productId: product.id,
          quantity: 1,
          amount: 0,
          currency: CURRENCY,
          discord: user.discord ? { id: user.discord.id, username: user.discord.username } : null,
          discordId: user.discord?.id,
        });
      } catch {
        return json({ error: 'Ya reclamaste este regalo.', code: 'claimed', id }, 409);
      }
      const status = await fulfill(await store.getOrder(id), ctx, null);
      return json({ id, status });
    },

    // Ruleta con monedas del servidor: se cobra en el juego cuando el jugador está conectado (o al entrar).
    // Si no tiene bastantes monedas, el servidor no cobra ni da nada y el pedido queda como no entregado.
    'POST /api/roulette/coins': async (req) => {
      const user = await currentUser(req);
      if (!user) return json({ error: 'Inicia sesión para girar con tus monedas.', code: 'login' }, 401);
      const terms = termsMissing(user);
      if (terms) return terms;
      const body = (await readJson(req)) || {};
      const product = products.find((p) => p.category === 'ruleta' && p.spins === Number(body.spins));
      if (!product || !product.coinPrice) return json({ error: 'Esa tirada no existe.' }, 400);
      const player = await store.playerByUuid(user.uuid);
      const bytes = crypto.getRandomValues(new Uint8Array(12));
      const id = `COIN${[...bytes].map((b) => b.toString(16).padStart(2, '0')).join('').toUpperCase()}`;
      await store.createOrder({
        id,
        status: 'pending',
        username: player?.name || user.name,
        uuid: user.uuid,
        productId: product.id,
        quantity: 1,
        amount: 0,
        currency: CURRENCY,
      });
      await store.queueDelivery(id, {
        username: player?.name || user.name,
        uuid: user.uuid,
        commands: [`tf web ruleta girar {player} ${product.spins}`],
        extra: { kind: 'ruleta-monedas', color: product.colors?.[1] || '#9061f9' },
      });
      log.log(`Ruleta con monedas ${id}: ${product.spins} giro(s) para ${user.name} (${user.uuid}), en cola del puente`);
      return json({ id, status: 'queued' });
    },

    // Tienda de monedas: se compra aquí y se cobra en el juego (/tf web tienda comprar) cuando el jugador está dentro.
    'POST /api/coinshop/buy': async (req) => {
      const user = await currentUser(req);
      if (!user) return json({ error: 'Inicia sesión para comprar con tus monedas.', code: 'login' }, 401);
      const terms = termsMissing(user);
      if (terms) return terms;
      const body = (await readJson(req)) || {};
      const item = (await store.coinShop()).find((it) => it.id === String(body.id || ''));
      if (!item) return json({ error: 'Ese objeto ya no está a la venta.' }, 404);
      const player = await store.playerByUuid(user.uuid);
      const bytes = crypto.getRandomValues(new Uint8Array(12));
      const id = `COIN${[...bytes].map((b) => b.toString(16).padStart(2, '0')).join('').toUpperCase()}`;
      const username = player?.name || user.name;
      await store.createOrder({
        id,
        status: 'pending',
        username,
        uuid: user.uuid,
        productId: `tienda:${item.id}`,
        quantity: 1,
        amount: 0,
        currency: CURRENCY,
      });
      await store.updateOrder(id, {
        prizes: [{ id: item.id, name: item.name, count: item.count, price: item.price, icon: coinShopIcon(item.item) }],
      });
      await store.queueDelivery(id, {
        username,
        uuid: user.uuid,
        commands: [`tf web tienda comprar {player} ${item.id}`],
        extra: { kind: 'tienda-monedas', color: '#f5b83d' },
      });
      log.log(`Tienda de monedas ${id}: ${item.name} (${item.price} monedas) para ${user.name} (${user.uuid}), en cola del puente`);
      return json({ id, status: 'queued' });
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
          case 'charge.dispute.created': {
            const dispute = event.type === 'charge.dispute.created';
            const paymentIntent = typeof object.payment_intent === 'string' ? object.payment_intent : object.payment_intent?.id;
            await handleReversal({ paymentIntent, full: dispute || object.refunded === true, dispute }, ctx);
            break;
          }
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

    // Webhook de PayPal: pago aprobado o cobrado (por si el comprador cierra la página antes de volver), reembolsos y
    // disputas. PayPal no firma con una clave compartida: se le pregunta a su API si el aviso es auténtico.
    'POST /webhook/paypal': async (req, ctx) => {
      if (!paypal || !env.PAYPAL_WEBHOOK_ID) return new Response('Webhook no configurado', { status: 503 });
      let event;
      try {
        event = JSON.parse(await req.text());
      } catch {
        return new Response('JSON no válido', { status: 400 });
      }
      const headers = Object.fromEntries([...req.headers].map(([k, v]) => [k.toLowerCase(), v]));
      try {
        if (!(await paypal.verifyWebhook({ headers, event, webhookId: env.PAYPAL_WEBHOOK_ID }))) {
          return new Response('Firma no válida', { status: 400 });
        }
        const r = event.resource || {};
        switch (event.event_type) {
          case 'CHECKOUT.ORDER.APPROVED': {
            const local = r.id ? await store.findOrderBySession(r.id) : null;
            if (local) await capturePayPal(local, ctx);
            break;
          }
          case 'PAYMENT.CAPTURE.COMPLETED':
          case 'PAYMENT.CAPTURE.DENIED': {
            const local = r.custom_id && ORDER_ID_RE.test(r.custom_id) ? await store.getOrder(r.custom_id) : null;
            if (local) await handlePayPalCapture(local, r, ctx);
            break;
          }
          case 'PAYMENT.CAPTURE.REFUNDED': {
            // El recurso es el reembolso; el cobro es su enlace «up». Completo si el cobro quedó REFUNDED.
            const captureId = (r.links || []).find((l) => l.rel === 'up')?.href?.split('/').pop();
            if (captureId) {
              const capture = await paypal.getCapture(captureId);
              await handleReversal({ paymentIntent: captureId, full: capture.status === 'REFUNDED', dispute: false }, ctx);
            }
            break;
          }
          case 'PAYMENT.CAPTURE.REVERSED':
            await handleReversal({ paymentIntent: r.id, full: true, dispute: true }, ctx);
            break;
          case 'CUSTOMER.DISPUTE.CREATED':
            for (const t of r.disputed_transactions || []) {
              await handleReversal({ paymentIntent: t.seller_transaction_id, full: true, dispute: true }, ctx);
            }
            break;
          default:
            break;
        }
      } catch (err) {
        // 500 para que PayPal reintente más tarde.
        log.error('Error procesando el webhook de PayPal:', err);
        return new Response('Error interno', { status: 500 });
      }
      return json({ received: true });
    },

    // 3) Respaldo: la página de confirmación pide a Stripe el estado del pago por si el webhook tarda.
    'POST /api/order/:id/sync': async (req, ctx) => {
      const order = ORDER_ID_RE.test(ctx.params.id) ? await store.getOrder(ctx.params.id) : null;
      if (!order) return json({ error: 'Pedido no encontrado.' }, 404);
      if (order.method === 'paypal' && paypal && order.sessionId && ['pending', 'awaiting_payment'].includes(order.status)) {
        try {
          await capturePayPal(order, ctx);
        } catch (err) {
          log.warn(`No se pudo cobrar el pago de PayPal de ${order.id}:`, err.message);
        }
      } else if (stripe && order.sessionId && ['pending', 'awaiting_payment'].includes(order.status)) {
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
      // Saldo de monedas de los conectados (para enseñarlo en la web junto al nombre)
      await store.setCoins(
        list(body.players, 500)
          .filter((p) => validPlayer(p) && Number.isSafeInteger(p.coins) && p.coins >= 0)
          .map((p) => ({ uuid: p.uuid, coins: p.coins })),
      );

      // /tf vincular del mod 1.2.8 ya no hace falta: las cuentas se crean en la web. Se responde que no vale.
      const linkResults = list(body.links, 20)
        .filter((link) => link && validPlayer(link))
        .map((link) => ({ uuid: link.uuid.toLowerCase(), ok: false, account: null }));

      // Rangos puestos o quitados por el staff con /tf web rango (sustituyen al comprado).
      for (const change of list(body.ranks, 50)) {
        if (!change || typeof change.uuid !== 'string' || !UUID_RE.test(change.uuid)) continue;
        if (change.rank === null) await store.setRank(change.uuid, null, 0, { force: true });
        else if (rankById.has(change.rank)) await store.setRank(change.uuid, change.rank, rankById.get(change.rank).tier, { force: true });
      }

      // /tf web tienda add|quitar|vaciar desde el juego
      const shopOps = list(body.shop, 50)
        .map((op) => cleanShopOp(op))
        .filter(Boolean);
      if (shopOps.length) await store.applyCoinShop(shopOps);

      const max = Number.isInteger(body.max) ? body.max : null;
      const done = list(body.done, 100)
        .filter((d) => d && Number.isInteger(d.id))
        .map((d) => ({ ...d, prizes: cleanPrizes(d.prizes) }));
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
        coinShop: (await store.coinShop()).map((it) => ({ id: it.id, name: it.name, count: it.count, price: it.price })),
        vfx: await store.vfxEquipFor(online),
        roulette: rouletteForServer(),
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

    return fulfill(local, ctx, paymentIntent);
  }

  // PayPal: el comprador aprobó el pago en PayPal (vuelve a /success o avisa el webhook): se cobra y se entrega.
  async function capturePayPal(local, ctx) {
    if (!['pending', 'awaiting_payment'].includes(local.status)) return local.status;
    let order;
    try {
      order = await paypal.captureOrder(local.sessionId);
    } catch (err) {
      if (err.issue === 'ORDER_NOT_APPROVED') return local.status; // Aún no lo aprobó en PayPal
      if (err.issue !== 'ORDER_ALREADY_CAPTURED') throw err;
      order = await paypal.getOrder(local.sessionId);
    }
    const unit = order.purchase_units?.[0];
    const capture = unit?.payments?.captures?.[0];
    if (!capture) return local.status;
    return handlePayPalCapture(local, { ...capture, custom_id: capture.custom_id || unit.custom_id }, ctx);
  }

  async function handlePayPalCapture(local, capture, ctx) {
    if (CLAIMED.includes(local.status)) return local.status; // Ya pagado y procesado.
    if (local.method !== 'paypal' || (capture.custom_id && capture.custom_id !== local.id)) {
      log.error(`El cobro de PayPal ${capture.id} no es del pedido ${local.id}`);
      return local.status;
    }
    if (capture.status === 'DECLINED' || capture.status === 'FAILED') {
      await store.updateOrder(local.id, { status: 'failed' });
      return 'failed';
    }
    if (capture.status === 'PENDING') {
      // PayPal lo está revisando: avisará con PAYMENT.CAPTURE.COMPLETED.
      await store.updateOrder(local.id, { status: 'awaiting_payment', paymentIntent: capture.id });
      return 'awaiting_payment';
    }
    if (capture.status !== 'COMPLETED') return local.status;
    const expected = formatAmount(local.amount, local.currency);
    if (capture.amount?.value !== expected || String(capture.amount?.currency_code).toUpperCase() !== local.currency) {
      log.error(`Importe incorrecto en ${local.id}: esperado ${expected} ${local.currency}, cobrado ${capture.amount?.value} ${capture.amount?.currency_code}`);
      await store.updateOrder(local.id, { status: 'error', error: 'Importe no coincide', paymentIntent: capture.id });
      return 'error';
    }
    return fulfill(local, ctx, capture.id);
  }

  const random = () => crypto.getRandomValues(new Uint32Array(1))[0] / 2 ** 32;

  /** Premios de la ruleta: cada giro sale de product.pool por su probabilidad; el premio «arma» es una al azar. */
  function spinRoulette(product, spins) {
    const pool = product.pool || [];
    const weapons = product.models || [];
    const total = pool.reduce((sum, p) => sum + p.chance, 0);
    const out = [];
    for (let i = 0; i < spins && total > 0; i++) {
      let r = random() * total;
      const entry = pool.find((p) => (r -= p.chance) < 0) || pool[pool.length - 1];
      if (entry.weapon && weapons.length) {
        const w = weapons[Math.floor(random() * weapons.length)];
        out.push({ id: w.id, name: w.name, weapon: true, icon: `/img/items/${product.set}/${w.id}.webp`,
          commands: [`tf web sets give {player} ${product.set} ${w.id}`] });
      } else if (!entry.weapon) {
        out.push({ id: entry.id, name: entry.name, icon: entry.icon, commands: entry.give || [] });
      }
    }
    return out;
  }

  // Pedido pagado (o regalo reclamado): a la cola del puente, rango, rol y anuncio en Discord.
  async function fulfill(local, ctx, paymentIntent) {
    const id = local.id;
    const product = productById.get(local.productId);
    const rank = rankById.get(product.id);
    // Los comandos se guardan con {player}/{uuid}: el mod los rellena al entregar con el nombre actual del UUID.
    const commands = [];
    for (let i = 0; i < local.quantity; i++) commands.push(...product.commands);
    // Ruleta: cada giro es un premio al azar según su probabilidad, elegido aquí (no en el navegador).
    let prizes = null;
    if (product.category === 'ruleta') {
      prizes = spinRoulette(product, (product.spins || 1) * local.quantity);
      for (const prize of prizes) commands.push(...prize.commands);
      prizes = prizes.map(({ commands: _c, ...p }) => p);
      await store.updateOrder(id, { prizes });
    }
    if (rank) {
      // Al mejorar se quitan los grupos de los rangos inferiores (si no los tiene, LuckPerms no hace nada).
      for (const lower of ranks) if (lower.tier < rank.tier) commands.push(`lp user {player} parent remove ${lower.rank.group}`);
    }
    const extra = {
      kind: product.category,
      color: rank?.rank.hex || product.colors?.[1] || '#f4c95d',
      rank: rankInfo(rank),
      upgradeFrom: local.rankFrom ? productById.get(local.rankFrom)?.name || null : null,
      prizes: prizes ? prizes.map((p) => p.name) : null,
    };

    const queued = await store.queueDelivery(id, { username: local.username, uuid: local.uuid, commands, extra, paymentIntent });
    if (!queued) return (await store.getOrder(id)).status;
    if (rank && local.uuid) await store.setRank(local.uuid, rank.id, rank.tier);
    log.log(`Pedido ${id} ${product.price === 0 ? 'reclamado (gratis)' : 'pagado'}: ${product.name} x${local.quantity} para ${local.username} (${local.uuid}), en cola del puente`);

    // En Discord: rol y anuncio. Un fallo aquí no afecta a la entrega en el juego.
    await deliverDiscord(id, product, ctx.discord);
    return 'queued';
  }

  // Reembolsos y disputas. Con el reembolso completo (o una disputa) se retira del servidor todo lo de ese pedido: el mod
  // quita los objetos marcados con el pedido estén donde estén (inventario, cofres, cofre de ender, suelo…), se quita
  // el grupo del rango (volviendo al anterior si fue una mejora) y sus roles de Discord. Un reembolso parcial solo se
  // apunta en el pedido.
  async function handleReversal({ paymentIntent, full, dispute }, ctx) {
    const order = paymentIntent ? await store.findOrderByPaymentIntent(paymentIntent) : null;
    if (!order) return;
    const what = dispute ? 'disputa' : full ? 'reembolsado' : 'reembolso parcial';
    log.warn(`Pedido ${order.id} (${order.username}): ${what}`);
    if (!order.refunded || !order.refunded.includes('retirado')) await store.updateOrder(order.id, { refunded: what });
    if (full && CLAIMED.includes(order.status)) await revoke(order, ctx);
  }

  // Lo que hay que deshacer de un pedido: los sets que dio y el grupo de su rango (si sigue siendo su rango actual).
  async function revoke(order, ctx) {
    const product = productById.get(order.productId);
    if (!product) return;
    const rank = rankById.get(product.id);
    const current = rank && order.uuid ? await store.getRank(order.uuid) : null;
    const rankStillCurrent = Boolean(rank && current?.rankId === rank.id);
    const from = order.rankFrom ? rankById.get(order.rankFrom) : null;
    const commands = [];
    for (const command of product.commands || []) {
      const set = command.match(/^tf web sets give \{player\} (\S+)(?: (\S+))?$/);
      if (set) commands.push(`tf web sets revoke {uuid} ${order.id} ${set[1]}${set[2] ? ` ${set[2]}` : ''}`);
      const group = command.match(/^lp user \{player\} parent add (\S+)$/);
      if (group && (!rank || rankStillCurrent)) commands.push(`lp user {uuid} parent remove ${group[1]}`);
    }
    if (rankStillCurrent && from) commands.push(`lp user {uuid} parent add ${from.rank.group}`);
    const queued = commands.length ? await store.queueRevocation(order, [...new Set(commands)]) : false;
    if (rankStillCurrent) await store.setRank(order.uuid, from?.id || null, from?.tier, { force: true });
    if (queued) log.warn(`Pedido ${order.id}: retirada del servidor en cola (${commands.length} comandos)`);

    // Roles de Discord del producto (y los del rango anterior, si era una mejora)
    const account = order.discordId ? { discordId: order.discordId } : order.uuid ? await store.accountByUuid(order.uuid) : null;
    const discord = ctx?.discord;
    if (queued && account?.discordId && discord?.rolesEnabled && (!rank || rankStillCurrent)) {
      try {
        await discord.removeRoles({ userId: account.discordId, roleIds: productRoles(product), reason: `Reembolso del pedido ${order.id}` });
        if (from) await discord.grantRoles({ userId: account.discordId, roleIds: productRoles(from), reason: `Reembolso del pedido ${order.id}` });
      } catch (err) {
        log.error(`No se pudieron quitar los roles de Discord del pedido ${order.id}:`, err.message);
      }
    }
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
          description: `${who} ha conseguido **${product.name}**${order.quantity > 1 ? ` ×${order.quantity}` : ''}${upgrade ? ` (mejora desde ${upgrade})` : ''}${
            order.prizes?.length ? `: ${order.prizes.map((p) => `**${p.name}**`).join(', ')}` : ''
          }. ¡Gracias por apoyar ${SERVER_NAME}!`,
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
