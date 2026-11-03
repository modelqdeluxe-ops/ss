// API de la tienda de Tierras Fantásticas para Cloudflare Workers.
// Pagos con Stripe, cuentas de jugador (nombre de Minecraft + contraseña) con su Discord conectado,
// y el puente con el servidor de Minecraft (mod TF Client).
import products from '../config/products.json' with { type: 'json' };
// Copias JPG de las imágenes de los productos para PayPal (tools/paypal_images.py): no acepta WebP.
import paypalImages from './paypal-images.json' with { type: 'json' };
import legal from '../config/legal.json' with { type: 'json' };
import vfx from '../config/vfx.json' with { type: 'json' };
import skillsCatalog from '../config/skills.json' with { type: 'json' };
import { Stripe, toStripeAmount } from './stripe.js';
import { PayPal, formatAmount } from './paypal.js';
import { MercadoPago, mxnCents, usdToMxn } from './mercadopago.js';
import { Discord, isSnowflake } from './discord.js';
import { createStore, CLAIMED, UUID_RE } from './store.js';
import { createSession, parseCookies, serializeCookie, randomHex, safeEqual } from './session.js';
import { hashPassword, verifyPassword } from './password.js';
import { createMailer, mailHtml, fieldsText } from './mail.js';
import { receiptPdf, receiptRows, money, refundPolicyFor, disputeFacts, disputePdf, stripeEvidence } from './receipt.js';
import { toBase64 } from './pdf.js';
import { normalizeCode, couponFromInput, couponProblem, couponDiscount, couponLabel, allocate, MIN_TOTAL as COUPON_MIN_TOTAL } from './coupons.js';

const USERNAME_RE = /^[A-Za-z0-9_]{3,16}$/;
const ORDER_ID_RE = /^[A-Z0-9]{5,40}$/;
const PASSWORD_MIN = 8;
const PASSWORD_MAX = 128;
// La sesión dura 60 días desde la última visita.
const SESSION_DAYS = 60;
const MAX_QUANTITY = 10;
// (3 nov 2026) El carrito: como mucho tantos productos distintos en un mismo pago
const CART_MAX_LINES = 50;
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
// Reembolsos: el plazo para cancelar y la penalización de un reembolso injustificado (una disputa o contracargo pasado
// el plazo: baneo de IP del servidor con EssentialsX, tempbanip) son los de los Términos que aceptó cada pedido
// (receipt.js, refundPolicyFor): desde la versión 2026-10-30.1, 24 horas desde la entrega y 15 días; antes, 5 días
// hábiles y 6 días.
const CONTEXT_LABEL = { cuenta: 'al crear la cuenta', compra: 'al pagar', regalo: 'al pagar un regalo', actualizacion: 'al aceptar la versión nueva' };

// Mensaje de un regalo: una línea corta, sin códigos de color (§) ni caracteres de control (va en un tellraw)
const GIFT_MESSAGE_MAX = 100;
function cleanGiftMessage(text) {
  if (typeof text !== 'string') return '';
  return text
    .replace(/[\p{Cc}\p{Cf}§]/gu, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, GIFT_MESSAGE_MAX);
}

// Días hábiles (lunes a viernes) que han pasado desde una fecha
function businessDaysSince(iso, now = Date.now()) {
  const start = new Date(iso);
  if (Number.isNaN(start.getTime())) return 0;
  let days = 0;
  for (let t = start.getTime() + 86400000; t <= now; t += 86400000) {
    const d = new Date(t).getUTCDay();
    if (d !== 0 && d !== 6) days++;
  }
  return days;
}
const TERMS_ERROR = 'Marca la casilla para aceptar los Términos y condiciones y el Aviso de privacidad.';

// --- Skills: las clases del TF Client (config/skills.json, de tools/skills.py) ---
// Cada clase es un producto «clase-<id>»; (TF Client 1.3.117, el dueño: «no se venderán por dinero, se venderán por
// coins») solo se compra con las monedas del servidor (POST /api/skills/coins). Las compradas son del jugador para
// siempre; tiene UNA activa a la vez, la que el puente manda al servidor.
const SKILL_PREFIX = 'clase-';
const skillClasses = skillsCatalog.classes;
const skillById = new Map(skillClasses.map((c) => [c.id, c]));
const skillProducts = skillClasses.map((c) => ({
  id: `${SKILL_PREFIX}${c.id}`,
  category: 'skills',
  skillClass: c.id,
  name: `Clase ${c.name}`,
  description: `${c.role}. ${c.desc}`,
  price: c.price,
  coinPrice: c.coinPrice,
  coinsOnly: true,
  maxQuantity: 1,
  image: c.icon,
  colors: [c.color, c.color],
  // La clase no se entrega con comandos: va en el campo «skills» de /bridge/poll (el mod la pone y da su equipo).
  commands: [],
  discordRoles: [],
}));
// --- VFX (efectos de kill) ---
// (TF Client 1.3.114, el dueño: «los efectos de kill los vendemos a 2.99 cada uno») cada efecto es un producto
// «vfx-<id>»: al pagarse queda en la cuenta (vfx_owned) y se equipa. Los que ya se obtuvieron gratis siguen siendo suyos.
const VFX_FREE = false;
const VFX_PREFIX = 'vfx-';
const VFX_PRICE = 299;
const vfxCatColor = new Map((vfx.cats || []).map((c) => [c.id, c.color]));
const vfxProducts = vfx.kills.map((k) => ({
  id: `${VFX_PREFIX}${k.id}`,
  category: 'vfx',
  vfxKill: k.id,
  name: `Efecto ${k.name}`,
  description: `Efecto de kill: ${k.desc} Sale en el juego al derrotar a alguien; queda en tu cuenta para siempre.`,
  price: VFX_PRICE,
  maxQuantity: 1,
  image: k.image,
  colors: [vfxCatColor.get(k.cat) || '#45e9ff', vfxCatColor.get(k.cat) || '#45e9ff'],
  // No se entrega con comandos: queda en la cuenta y el puente lo manda equipado al servidor.
  commands: [],
  discordRoles: [],
}));
const allProducts = [...products, ...skillProducts, ...vfxProducts];
const productById = new Map(allProducts.map((p) => [p.id, p]));
const skillOf = (product) => (product?.category === 'skills' ? skillById.get(product.skillClass) || null : null);
const vfxOf = (product) => (product?.category === 'vfx' && product.vfxKill ? { kind: 'kill', id: product.vfxKill, key: `kill:${product.vfxKill}` } : null);
// --- Armario: lo comprado que se puede llevar puesto (solo apariencia) ---
// Huecos del armario y qué piezas van en cada uno (por el id de la pieza en el set).
const WARDROBE_SLOTS = ['head', 'chest', 'legs', 'feet', 'back'];
const BACK_PIECES = new Set(['wings', 'wing', 'backpack', 'cape', 'tail', 'quiver']);
function wardrobeSlot(pieceId) {
  if (pieceId === 'armor_helmet' || pieceId === 'helmet' || pieceId === 'hat' || pieceId === 'crown') return 'head';
  if (pieceId === 'armor_chestplate') return 'chest';
  if (pieceId === 'armor_leggings') return 'legs';
  if (pieceId === 'armor_boots') return 'feet';
  if (BACK_PIECES.has(pieceId)) return 'back';
  return null;
}
// Piezas que da cada producto: los rangos y las crates, su set entero; los cosméticos, su pieza (cabeza o espalda).
function productPieces(p) {
  if (p.category === 'cosmeticos') {
    const slot = p.slot === 'head' || p.slot === 'back' ? p.slot : null;
    return slot && p.set && p.item ? [{ set: p.set, id: p.item, name: p.name, slot }] : [];
  }
  if ((p.category === 'rangos' || p.category === 'crates' || p.category === 'especiales') && p.set) {
    return (p.models || []).map((m) => ({ set: p.set, id: m.id, name: m.name, slot: wardrobeSlot(m.id) })).filter((x) => x.slot);
  }
  return [];
}

const vfxKills = new Set(vfx.kills.map((k) => k.id));
// Paquetes de skills de los VFX: ya no se dan ni se enseñan en la tienda (ahora están las clases de Skills). Quien ya
// tenía uno lo conserva: lo puede seguir equipando o quitando desde la web y el pad, y el puente lo sigue mandando.
const VFX_OLD_PACKS = new Map([
  ['vendaval', { name: 'Filo del Vendaval', image: 'img/vfx/pack_vendaval.webp' }],
  ['trueno', { name: 'Ronin del Trueno', image: 'img/vfx/pack_trueno.webp' }],
]);
// Lo que se puede obtener ahora (solo efectos de kill)
function vfxItem(kind, id) {
  if (typeof id !== 'string') return null;
  if (kind === 'kill' && vfxKills.has(id)) return { kind, id, key: `kill:${id}` };
  return null;
}
// Lo que se puede equipar si ya es suyo: los efectos de kill y los paquetes viejos
function vfxEquipable(kind, id) {
  if (kind === 'pack' && typeof id === 'string' && VFX_OLD_PACKS.has(id)) return { kind, id, key: `pack:${id}` };
  return vfxItem(kind, id);
}
// Los roles de Discord de un producto: ids o nombres de rol (1.3.115: los rangos van por nombre, Discord.resolveRoles)
const hasRoles = (p) => (p.discordRoles || []).some((r) => isSnowflake(r) || !/^ID_ROL_/.test(String(r)));
const productRoles = async (p, discord) => {
  try {
    return await discord.resolveRoles(p.discordRoles || []);
  } catch (err) {
    console.error('No se pudieron leer los roles del Discord:', err.message);
    return (p.discordRoles || []).filter(isSnowflake);
  }
};
const ranks = products.filter((p) => p.category === 'rangos' && p.rank && Number.isInteger(p.tier));
const rankById = new Map(ranks.map((r) => [r.id, r]));

export function json(data, status = 200, headers = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', ...headers },
  });
}

/** Un archivo de GitHub (el jar del mod, los packs cifrados) pasado tal cual (sigue sus redirecciones) como descarga de esta web. */
async function passDownload(source, filename, type) {
  let upstream;
  try {
    upstream = await fetch(source, { redirect: 'follow' });
  } catch {
    upstream = null;
  }
  if (!upstream || !upstream.ok || !upstream.body) {
    return new Response('La descarga no está disponible ahora mismo. Prueba otra vez en unos minutos.', {
      status: 502,
      headers: { 'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'no-store' },
    });
  }
  const headers = new Headers({
    'Content-Type': type,
    'Content-Disposition': `attachment; filename="${filename}"`,
    'Cache-Control': 'no-store',
  });
  const length = upstream.headers.get('Content-Length');
  if (length) headers.set('Content-Length', length);
  return new Response(upstream.body, { status: 200, headers });
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

function orderId(prefix = 'TF') {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  return `${prefix}${[...crypto.getRandomValues(new Uint8Array(16))].map((b) => alphabet[b % alphabet.length]).join('')}`;
}
// El carrito es CART + 16 letras; sus pedidos, el número del carrito + 01, 02… (así se ve de qué carrito son)
const cartOrderId = (cart, i) => `${cart}${String(i + 1).padStart(2, '0')}`;
const sum = (list) => list.reduce((a, b) => a + b, 0);

const head = (name) => `https://mc-heads.net/avatar/${encodeURIComponent(name)}/64`;

// Datos del rango que necesitan la web y el mod (prefijo y color del nametag).
function rankInfo(rank) {
  if (!rank) return null;
  return { id: rank.id, name: rank.name, tier: rank.tier, group: rank.rank.group, prefix: rank.rank.prefix, color: rank.rank.color, hex: rank.rank.hex };
}

// Para el puente: además, lo que el TF Client pone en el grupo de LuckPerms del rango (permisos de EssentialsX y hogares).
function rankSetup(rank) {
  return {
    ...rankInfo(rank),
    homes: rank.rank.homes,
    permissions: rank.rank.permissions,
    // Para «Mi rango» del TF Pad: lo que trae, en frases cortas
    perks: [
      ...(rank.perks || []),
      ...(rank.serverPerks || []).map((x) => (x.cmd ? `${x.cmd}: ${x.text}` : x.text)),
      // (30 oct 2026) el bonus, aparte de lo que incluye el rango
      ...(rank.bonus ? [`Bonus: ${rank.bonus.charAt(0).toLowerCase()}${rank.bonus.slice(1)} (regalo extra, aparte del rango)`] : []),
    ].slice(0, 30),
  };
}

export function createApp(env) {
  const SERVER_NAME = env.SERVER_NAME || 'Tierras Fantásticas';
  const SERVER_IP = env.SERVER_IP || '216.163.187.40:19015';
  // La IP que se enseña en la web y a la que conecta TF Client: la que el dueño pone en /staff (con ADMIN_KEY) o, si
  // no puso ninguna, SERVER_IP. El mod la lee de /api/config y, si la web no responde, usa la suya (TFLock.OFFICIAL).
  const SERVER_IP_RE = /^(?=.{1,253}(?::\d{1,5})?$)[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)*(?::\d{1,5})?$/i;
  async function serverIp() {
    const saved = (await store.getSetting('server_ip'))?.value;
    return saved && SERVER_IP_RE.test(saved) ? saved : SERVER_IP;
  }
  const DISCORD_URL = env.DISCORD_URL || 'https://discord.gg/tRrunHBZE';
  // Grupo de WhatsApp. La web siempre enlaza a /whatsapp: si algún día el enlace del grupo cambia, basta con poner el
  // nuevo en la variable WHATSAPP_URL de Cloudflare (o aquí) y todos los botones siguen funcionando.
  const WHATSAPP_URL = env.WHATSAPP_URL || 'https://chat.whatsapp.com/CqULG1UFixUJFuN3xwtXrm';
  // El mod (TF Client): la última Release de GitHub. /descargar lo sirve desde aquí (Cloudflare lo baja de GitHub): hay
  // redes que no llegan a release-assets.githubusercontent.com, adonde GitHub manda la descarga («site not reached»).
  const CLIENT_JAR_URL = env.CLIENT_JAR_URL || 'https://github.com/modelqdeluxe-ops/ss/releases/download/tfclient/tfclient.jar';
  // Los packs originales de los jefes (cifrados: sin la contraseña del dueño no sirven), por el mismo motivo
  const PACKS_URL = env.PACKS_URL || 'https://raw.githubusercontent.com/modelqdeluxe-ops/ss/main/packs/jefes-17.tar.enc';
  const CURRENCY = (env.CURRENCY || 'USD').toUpperCase();
  const PUBLIC_URL = (env.PUBLIC_URL || '').replace(/\/$/, '');
  const log = env.LOGGER || console;

  const store = createStore(env.DB);
  // Copias por correo para el dueño (src/mail.js): firmas de los Términos, recibos y reembolsos o disputas
  const mailer = createMailer(env, log);

  // Lo del jugador para la web: lo suyo, lo equipado y, si tenía alguno, sus paquetes viejos (nombre y miniatura)
  async function vfxView(uuid) {
    const mine = await store.vfxFor(uuid);
    const packs = mine.owned
      .filter((k) => k.startsWith('pack:') && VFX_OLD_PACKS.has(k.slice(5)))
      .map((k) => ({ id: k.slice(5), ...VFX_OLD_PACKS.get(k.slice(5)) }));
    return { ...mine, packs };
  }
  const stripe = env.STRIPE_SECRET_KEY ? new Stripe({ secretKey: env.STRIPE_SECRET_KEY, apiBase: env.STRIPE_API_BASE }) : null;
  // PayPal (opcional): con PAYPAL_CLIENT_ID y PAYPAL_CLIENT_SECRET en Cloudflare aparece el botón «Pagar con PayPal».
  // PAYPAL_ENV = 'sandbox' para pruebas (por defecto, live).
  const paypal =
    env.PAYPAL_CLIENT_ID && env.PAYPAL_CLIENT_SECRET
      ? new PayPal({ clientId: env.PAYPAL_CLIENT_ID, clientSecret: env.PAYPAL_CLIENT_SECRET, env: env.PAYPAL_ENV || 'live', apiBase: env.PAYPAL_API_BASE })
      : null;
  // (2026-10-26) Mercado Pago (opcional, cuenta de México): con MP_ACCESS_TOKEN aparece «Pagar con Mercado Pago». Una
  // cuenta de México solo cobra en pesos: el precio en dólares al cambio del día (usdToMxn; MP_RATE si no hay cambio).
  // MP_WEBHOOK_SECRET firma los avisos (sin ella, igual se pregunta a la API).
  const mp = env.MP_ACCESS_TOKEN ? new MercadoPago({ accessToken: env.MP_ACCESS_TOKEN, apiBase: env.MP_API_BASE }) : null;
  const MP_CURRENCY = 'MXN';
  // (31 oct 2026) Desde México, la tarjeta (Stripe) cobra en pesos: muchas tarjetas mexicanas (Carnet, débito) no
  // admiten cargos en dólares («Divisa no aceptada»). El país lo dice Cloudflare (request.cf.country).
  const visitorCountry = (req) => String(req.cf?.country || req.headers.get('CF-IPCountry') || '').toUpperCase();
  const cardInPesos = (req) => visitorCountry(req) === 'MX';
  const mpRate = () => usdToMxn({ token: env.MP_ACCESS_TOKEN, fallback: Number(env.MP_RATE) > 0 ? Number(env.MP_RATE) : 18.5, apiBase: env.MP_RATE_API_BASE });

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

  // Piezas del armario de un jugador: las de sus compras pagadas y las del set de su rango (también si se lo dio el staff)
  async function ownedPieces(uuid) {
    const ids = new Set(await store.ownedProducts(uuid));
    const rank = await store.getRank(uuid);
    if (rank?.rankId) ids.add(rank.rankId);
    const out = [];
    const seen = new Set();
    for (const id of ids) {
      const p = productById.get(id);
      if (!p) continue;
      for (const piece of productPieces(p)) {
        const key = `${piece.set}/${piece.id}`;
        if (seen.has(key)) continue;
        seen.add(key);
        out.push({ ...piece, from: p.name });
      }
    }
    return out;
  }

  // Para el puente: los efectos de kill de cada conectado que tenga alguno
  async function vfxOwnedForServer(uuids) {
    const out = [];
    for (const raw of uuids) {
      const kills = (await store.vfxFor(raw)).owned.filter((k) => k.startsWith('kill:')).map((k) => k.slice(5));
      if (kills.length) out.push({ uuid: raw.toLowerCase(), kills });
    }
    return out;
  }

  // Para el puente: de cada conectado con clases, la activa (solo si sigue siendo suya) y las que tiene (el TF Pad las
  // enseña para activarlas desde el juego)
  async function skillsForServer(uuids) {
    const active = new Map((await store.skillActiveFor(uuids)).map((a) => [a.uuid, a]));
    const out = [];
    for (const raw of uuids) {
      const uuid = raw.toLowerCase();
      const owned = (await store.skillsOwned(uuid)).filter((id) => skillById.has(id));
      const a = active.get(uuid);
      if (!a && !owned.length) continue;
      const clase = a?.cls && owned.includes(a.cls) ? a.cls : null;
      out.push({ uuid, clase, at: a?.at || 0, owned });
    }
    return out;
  }

  // Para el puente: lo puesto de cada conectado, solo con lo que sigue teniendo
  async function wardrobesForServer(uuids) {
    const out = [];
    for (const w of await store.wardrobesFor(uuids)) {
      const owned = new Set((await ownedPieces(w.uuid)).map((x) => `${x.set}/${x.id}`));
      const items = {};
      for (const slot of WARDROBE_SLOTS) if (w.items[slot] && owned.has(w.items[slot])) items[slot] = w.items[slot];
      out.push({ uuid: w.uuid, items, at: w.at });
    }
    return out;
  }

  async function wardrobeView(uuid) {
    const owned = await ownedPieces(uuid);
    const { items } = await store.wardrobeFor(uuid);
    // Solo cuenta lo que sigue teniendo (si se reembolsó, deja de verse)
    const ownedKeys = new Set(owned.map((x) => `${x.set}/${x.id}`));
    const equipped = {};
    for (const slot of WARDROBE_SLOTS) {
      if (items[slot] && ownedKeys.has(items[slot])) {
        const [set, id] = items[slot].split('/');
        equipped[slot] = { set, id };
      }
    }
    return { slots: WARDROBE_SLOTS, owned, equipped };
  }

  // Las clases de skills de un jugador: las que tiene, la activa (solo si sigue siendo suya) y las que paga con monedas.
  async function skillsView(uuid) {
    const owned = (await store.skillsOwned(uuid)).filter((id) => skillById.has(id));
    const active = await store.skillActive(uuid);
    const player = await store.playerByUuid(uuid);
    return {
      owned,
      active: active?.cls && owned.includes(active.cls) ? active.cls : null,
      pending: (await store.skillsPending(uuid)).filter((id) => skillById.has(id) && !owned.includes(id)),
      coins: player?.coins ?? null,
    };
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
    const ip = req.headers.get('CF-Connecting-IP') || null;
    const userAgent = req.headers.get('User-Agent') || null;
    await store.acceptTerms({ uuid, username, context, version: TERMS_VERSION, orderId, ip, userAgent });
    // Copia de la firma para el dueño (si el correo está configurado; si falla, la firma ya está guardada)
    const rows = [
      ['Jugador', username || '—'],
      ['UUID', uuid || '—'],
      ['Cuándo firmó', `${CONTEXT_LABEL[context] || context}`],
      ['Fecha y hora (UTC)', new Date().toISOString()],
      ['Versión de los Términos', `${TERMS_VERSION} (${legal.date})`],
      ['Pedido', orderId || '—'],
      ['IP', ip || '—'],
      ['Navegador', userAgent || '—'],
      ['Términos', `${PUBLIC_URL || 'https://xn--tierrasfantsticas-hpb.store'}/terminos`],
    ];
    const intro = `${username || 'Un jugador'} aceptó los Términos y condiciones y el Aviso de privacidad de ${SERVER_NAME} marcando la casilla.`;
    await mailer.send({
      subject: `Firma de los Términos · ${username || uuid} · ${CONTEXT_LABEL[context] || context}`,
      text: `${intro}\n\n${fieldsText(rows)}`,
      html: mailHtml('Firma de los Términos y condiciones', intro, rows),
    });
  }

  // Recibo (PDF) y sus datos: el del jugador y la copia del dueño (con IP y navegador de la aceptación)
  async function receiptFor(order, owner) {
    // Un pedido que se pagó en un carrito: la aceptación de los Términos es la del carrito, y el recibo lo dice
    const acceptance = await store.termsForOrder(order.cartId || order.id);
    const productName = orderView(order).product;
    const cartRec = order.cartId ? await store.getCart(order.cartId) : null;
    const cart = cartRec ? { id: cartRec.id, count: cartRec.items.length, amount: cartRec.amount, currency: cartRec.currency } : null;
    const args = { order, productName, acceptance, owner, cart };
    return { pdf: receiptPdf(args), rows: receiptRows(args) };
  }

  // El carrito como un pedido (para el recibo, el correo y el expediente de una disputa): el pago entero, con sus
  // productos. Entregado cuando lo están todos.
  function cartAsOrder(cart, children) {
    const done = children.length && children.every((o) => o.deliveredAt);
    return {
      id: cart.id,
      status: cart.status,
      username: cart.username,
      uuid: cart.uuid,
      productId: null,
      quantity: 1,
      amount: cart.amount,
      currency: cart.currency,
      discord: children.find((o) => o.discord)?.discord || cart.discord,
      discordId: cart.discordId,
      sessionId: cart.sessionId,
      paymentIntent: cart.paymentIntent,
      termsVersion: cart.termsVersion,
      method: cart.method,
      gift: cart.gift,
      refunded: cart.refunded,
      createdAt: cart.createdAt,
      paidAt: cart.paidAt,
      deliveredAt: done ? children.map((o) => o.deliveredAt).sort().at(-1) : null,
    };
  }
  const productLabel = (o) => `${productById.get(o.productId)?.name || o.productId}${o.quantity > 1 ? ` ×${o.quantity}` : ''}${o.gift ? ` (regalo para ${o.username})` : ''}`;
  const cartProducts = (children) => children.map(productLabel).join(', ');

  async function cartReceiptFor(cart, owner) {
    const children = await store.cartOrders(cart.id);
    const acceptance = await store.termsForOrder(cart.id);
    const order = cartAsOrder(cart, children);
    const items = children.map((o) => ({
      name: `${productById.get(o.productId)?.name || o.productId}${o.gift ? ` (regalo para ${o.username})` : cart.gift ? '' : o.username !== cart.username ? ` (para ${o.username})` : ''}`,
      quantity: o.quantity,
      amount: o.amount,
      currency: o.currency,
    }));
    const coupon = cart.coupon ? { code: cart.coupon, subtotal: cart.subtotal, discount: cart.discount } : null;
    const args = { order, productName: cartProducts(children), acceptance, owner, items, coupon };
    return { pdf: receiptPdf(args), rows: receiptRows(args), order };
  }

  // Copia del recibo del carrito para el dueño (una sola, con todos sus productos)
  async function mailCartReceipt(cartId) {
    if (!mailer.enabled) return;
    const cart = await store.getCart(cartId);
    if (!cart || !cart.amount) return;
    const { pdf, rows } = await cartReceiptFor(cart, true);
    const intro = `Compra pagada en ${SERVER_NAME} con el carrito: ${cart.username}, ${money(cart.amount, cart.currency)} (${cart.items.length} productos${cart.coupon ? `, cupón ${cart.coupon}` : ''}).`;
    const policy = refundPolicyFor(cart).lines;
    await mailer.send({
      subject: `Recibo ${cart.id} · ${cart.username} · ${money(cart.amount, cart.currency)}`,
      text: `${intro}\n\n${fieldsText(rows)}\n\n${policy.join('\n')}`,
      html: mailHtml('Recibo de compra (carrito)', intro, rows, policy.join(' ')),
      attachments: [{ filename: `recibo-${cart.id}.pdf`, content: toBase64(pdf) }],
    });
  }

  // Copia del recibo de cada compra pagada con dinero, para el dueño
  async function mailReceipt(orderId) {
    if (!mailer.enabled) return;
    const order = await store.getOrder(orderId);
    if (!order || !order.amount) return;
    const { pdf, rows } = await receiptFor(order, true);
    const intro = `Compra pagada en ${SERVER_NAME}: ${order.username}, ${money(order.amount, order.currency)}.`;
    const policy = refundPolicyFor(order).lines;
    await mailer.send({
      subject: `Recibo ${order.id} · ${order.username} · ${money(order.amount, order.currency)}`,
      text: `${intro}\n\n${fieldsText(rows)}\n\n${policy.join('\n')}`,
      html: mailHtml('Recibo de compra', intro, rows, policy.join(' ')),
      attachments: [{ filename: `recibo-${order.id}.pdf`, content: toBase64(pdf) }],
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
    const cls = skillOf(product);
    if (cls) {
      if ((await store.skillsOwned(uuid)).includes(cls.id)) return { blocked: `Este jugador ya tiene la clase ${cls.name}.`, code: 'owned' };
      return { unit: product.price };
    }
    const fx = vfxOf(product);
    if (fx) {
      if ((await store.vfxFor(uuid)).owned.includes(fx.key)) return { blocked: `Este jugador ya tiene ${product.name}.`, code: 'owned' };
      return { unit: product.price };
    }
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
      kind: shopItem ? 'tienda' : product?.category === 'skills' ? 'skills' : order.id.startsWith('COIN') ? 'ruleta' : null,
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
      gift: order.gift ? { from: order.gift.fromName, message: order.gift.message || null } : null,
      // (3 nov 2026) el carrito con el que se pagó (junto con otros productos) y el cupón usado
      cartId: order.cartId || null,
      coupon: order.coupon ? { code: order.coupon, discount: order.discount || 0 } : null,
    };
  }

  // --- Cupones (los crea el staff en /staff) ---

  // El cupón que escribió el comprador, comprobado para esta compra: { coupon, discount } (sin cupón: coupon null) o
  // { error }. subtotal en céntimos de dólar; payer = el UUID de quien paga (para «un uso por jugador»). A quien prueba
  // muchos códigos que no existen se le frena unos minutos (como a quien prueba contraseñas).
  async function applyCoupon(req, raw, { subtotal, payer }) {
    if (raw === undefined || raw === null || String(raw).trim() === '') return { coupon: null, discount: 0 };
    const key = `coupon:${req.headers.get('CF-Connecting-IP') || 'local'}`;
    if (await store.loginLocked(key)) return { error: 'Has probado demasiados cupones que no existen. Espera unos minutos.', code: 'coupon_locked' };
    const code = normalizeCode(String(raw));
    const coupon = code ? await store.getCoupon(code) : null;
    if (!coupon) {
      await store.loginFailed(key);
      return { error: 'Ese cupón no existe. Revisa que esté bien escrito.', code: 'coupon' };
    }
    const usage = coupon.maxUses || coupon.perPlayer ? await store.couponUsage(coupon.code, payer) : null;
    const problem = couponProblem(coupon, { subtotal, usage });
    if (problem) return { error: problem, code: 'coupon' };
    return { coupon, discount: couponDiscount(coupon, subtotal) };
  }

  const couponInfo = (c, discount) => (c ? { code: c.code, label: couponLabel(c), discount } : null);

  // --- Carrito: varios productos en un solo pago ---

  /**
   * Las líneas de una compra. (3 nov 2026, el dueño: «quiero que añada todo lo que yo quiera… poder mandar varios
   * regalos a 4 jugadores distintos en un solo pago») Cada línea es un producto PARA UN JUGADOR: el de «para ti» (self:
   * el nombre que escribe quien compra) o un regalo («to», con su mensaje). El precio es el de ese jugador: lo que ya
   * tiene sale bloqueado y las mejoras de rango cuestan la diferencia; varios rangos para el mismo jugador van de menor
   * a mayor y cada uno es una mejora desde el anterior (como comprarlos seguidos). Lo mismo para el mismo jugador se
   * junta (y su cantidad no pasa del máximo del producto).
   * Devuelve { lines: [{ product, quantity, unit, line, from, blocked, blockedCode, keys, to, recipient, gift, message }] }
   * o { error, code }. keys: las líneas del navegador («producto|destinatario») que forman esta.
   */
  async function priceLines(rawItems, { self = null, buyerUuid = null } = {}) {
    if (!Array.isArray(rawItems) || !rawItems.length) return { error: 'Tu carrito está vacío.', code: 'empty' };
    if (rawItems.length > CART_MAX_LINES * 2) return { error: 'Demasiados productos.', code: 'cart' };
    const buyer = buyerUuid ? String(buyerUuid).toLowerCase() : null;
    const raw = [];
    for (const it of rawItems) {
      const id = typeof it?.productId === 'string' ? it.productId : null;
      if (!id) return { error: 'Producto no válido.', code: 'invalid' };
      const qty = Number.parseInt(it?.quantity ?? 1, 10);
      const to = typeof it?.to === 'string' ? it.to.trim() : '';
      if (to && !USERNAME_RE.test(to)) return { error: `«${to.slice(0, 20)}» no es un nombre de Minecraft válido.`, code: 'invalid_player' };
      raw.push({ id, qty: Number.isInteger(qty) ? qty : Number.NaN, to, key: `${id}|${to.toLowerCase()}`, message: cleanGiftMessage(it?.message) || null });
    }
    // Quién recibe cada una (los regalos, por su nombre en el servidor)
    const found = new Map();
    for (const r of raw) if (r.to && !found.has(r.to.toLowerCase())) found.set(r.to.toLowerCase(), await store.findPlayer(r.to));
    const merged = new Map();
    for (const r of raw) {
      const target = r.to ? found.get(r.to.toLowerCase()) : self;
      const who = target ? target.uuid.toLowerCase() : r.to ? `?${r.to.toLowerCase()}` : 'self';
      const mk = `${r.id}|${who}`;
      const m = merged.get(mk);
      if (m) {
        m.quantity += r.qty;
        if (!m.keys.includes(r.key)) m.keys.push(r.key);
        m.message ||= r.message;
      } else {
        merged.set(mk, { id: r.id, quantity: r.qty, to: r.to || null, target, keys: [r.key], message: r.message });
      }
    }
    if (merged.size > CART_MAX_LINES) return { error: `Como mucho ${CART_MAX_LINES} líneas por compra: paga estas y sigue después.`, code: 'cart' };
    const lines = [];
    for (const m of merged.values()) {
      const product = productById.get(m.id);
      if (!product || product.staffOnly) return { error: 'Hay un producto que ya no está a la venta: quítalo del carrito.', code: 'invalid', productId: m.id };
      if (product.price === 0) return { error: `${product.name} es gratis: reclámalo con tu cuenta.`, code: 'free', productId: m.id };
      if (product.coinsOnly) return { error: `${product.name} solo se compra con Fantastic Coins.`, code: 'coins', productId: m.id };
      const max = product.maxQuantity || MAX_QUANTITY;
      const whom = m.target ? ` para ${m.target.name}` : m.to ? ` para ${m.to}` : '';
      if (!Number.isInteger(m.quantity) || m.quantity < 1 || m.quantity > max) {
        return {
          error: max === 1 ? `${product.name}${whom} ya está en el carrito: es uno por jugador.` : `La cantidad de ${product.name}${whom} debe estar entre 1 y ${max}.`,
          code: 'quantity',
          productId: m.id,
        };
      }
      const recipient = m.target ? { uuid: m.target.uuid.toLowerCase(), name: m.target.name } : null;
      // Un «regalo» a uno mismo es una compra normal
      const gift = Boolean(m.to && recipient && recipient.uuid !== buyer);
      lines.push({ product, quantity: m.quantity, keys: m.keys, to: m.to, recipient, gift, message: gift ? m.message : null, unknown: Boolean(m.to && !m.target) });
    }
    // Los rangos al final y de menor a mayor (el orden en que se entregan): así cada uno mejora desde el anterior
    lines.sort((a, b) => (rankById.get(a.product.id)?.tier ?? -1) - (rankById.get(b.product.id)?.tier ?? -1));
    const cartRank = new Map();
    for (const l of lines) {
      l.unit = null;
      l.line = 0;
      l.from = null;
      l.blocked = null;
      l.blockedCode = null;
      if (l.unknown) {
        l.blocked = `No encontramos a ${l.to} en ${SERVER_NAME}: tiene que haber entrado al servidor al menos una vez.`;
        l.blockedCode = 'unknown_player';
        continue;
      }
      const uuid = l.recipient?.uuid;
      const rank = rankById.get(l.product.id);
      let q;
      if (rank) {
        // (3 nov 2026, el dueño: «los precios no los calcula bien el carrito») también sin saber aún quién es (sin el
        // nombre escrito, «para ti»): dos rangos para el mismo jugador siempre se encadenan, como al cobrarlos
        const who = uuid || 'self';
        const inCart = cartRank.get(who);
        const current = inCart || (uuid ? await store.getRank(uuid) : null);
        const owned = current ? rankById.get(current.rankId) : null;
        if (current && current.tier >= rank.tier) {
          q = inCart
            ? { blocked: `${l.recipient?.name || 'Este jugador'} ya lleva ${owned?.name || 'un rango igual o superior'} en este carrito.`, code: 'rank_owned' }
            : l.gift
              ? { blocked: `${l.recipient.name} ya tiene ${owned?.name || 'un rango igual o superior'}: solo se le puede regalar un rango superior.`, code: 'rank_owned' }
              : { blocked: `Este jugador ya tiene ${owned?.name || 'un rango igual o superior'}. Solo puedes mejorar a un rango superior.`, code: 'rank_owned' };
        } else {
          // fromCart: la mejora es desde otro rango que va en este mismo carrito para el mismo jugador (no desde el que ya
          // tiene en el servidor)
          q = { unit: owned ? Math.max(MIN_CHARGE, rank.price - owned.price) : rank.price, from: owned || null, fromCart: Boolean(inCart && owned) };
          cartRank.set(who, { rankId: rank.id, tier: rank.tier });
        }
      } else {
        q = uuid ? await quote(l.product, uuid) : { unit: l.product.price };
      }
      if (q.blocked) {
        l.blocked = q.blocked;
        l.blockedCode = q.code || 'rank_owned';
      } else {
        l.unit = q.unit;
        l.line = q.unit * l.quantity;
        l.from = q.from || null;
        l.fromCart = Boolean(q.fromCart);
      }
    }
    return { lines };
  }

  // Lo que manda el navegador viejo (antes del 3 nov, el carrito entero para un jugador con «gift: true»): cada producto
  // pasa a ser un regalo para ese jugador
  function legacyCartItems(body) {
    const items = Array.isArray(body.items) ? body.items : [];
    if (body.gift !== true || typeof body.username !== 'string') return items;
    return items.map((it) => (it && typeof it === 'object' && !it.to ? { ...it, to: body.username, message: it.message ?? body.message } : it));
  }

  /**
   * Lo que se cobra de cada línea: el precio en dólares menos su parte del cupón (allocate) y, si se paga en pesos, en
   * pesos al cambio del día (sin cupón, igual que en la compra suelta: el precio de una unidad en pesos × cantidad).
   */
  function chargeLines(lines, discount, rate) {
    const usd = allocate(lines.map((l) => l.line), sum(lines.map((l) => l.line)) - discount);
    const charged = rate ? usd.map((c, i) => (c === lines[i].line ? mxnCents(lines[i].unit, rate) * lines[i].quantity : mxnCents(c, rate))) : usd;
    return { usd, charged };
  }

  const lineName = (l) => (l.from ? `${l.product.name} (mejora desde ${l.from.name})` : l.product.name);

  // El carrito para la página de confirmación: sus productos con el estado de cada uno
  async function cartView(cart) {
    const children = await store.cartOrders(cart.id);
    const sts = children.map((o) => o.status);
    let status = cart.status;
    if (cart.status === 'paid') {
      status = sts.length && sts.every((x) => x === 'delivered') ? 'delivered' : sts.some((x) => x === 'delivery_failed') ? 'delivery_failed' : 'queued';
    }
    const views = children.map(orderView);
    return {
      id: cart.id,
      cart: true,
      status,
      username: cart.username,
      product: `${views.length} productos`,
      quantity: 1,
      amount: cart.amount,
      currency: cart.currency,
      subtotal: cart.subtotal,
      discount: cart.discount,
      coupon: cart.coupon ? { code: cart.coupon, discount: cart.discount } : null,
      items: views.map((v) => ({
        id: v.id,
        product: v.product,
        image: v.image,
        quantity: v.quantity,
        amount: v.amount,
        currency: v.currency,
        status: v.status,
        refunded: v.refunded,
        upgradeFrom: v.upgradeFrom,
        // Para quién es (y si es un regalo, de quién y su mensaje)
        username: v.username,
        gift: v.gift,
      })),
      refunded: cart.refunded || null,
      createdAt: cart.createdAt,
      discord: views.find((v) => v.discord)?.discord || null,
      gift: cart.gift ? { from: cart.gift.fromName, message: cart.gift.message || null } : null,
    };
  }

  // Un pedido o un carrito (el pago): los avisos de Stripe, PayPal y Mercado Pago llevan uno de los dos
  async function loadPayable(id) {
    if (!id || !ORDER_ID_RE.test(id)) return null;
    return (await store.getOrder(id)) || (id.startsWith('CART') ? await store.getCart(id) : null);
  }
  const settled = (local) => CLAIMED.includes(local.status) || local.status === 'paid';
  // El cupón del pago (si lo lleva) sigue al pago: caducado o fallido = el uso vuelve a quedar libre; esperando el cobro =
  // apartado hasta que se cobre; cobrado (o con un importe que no cuadra: hubo dinero) = uso gastado
  const CLAIM_FOR = { expired: 'void', failed: 'void', awaiting_payment: 'waiting', error: 'used' };
  const couponOf = (local) => (local.coupon && (local.isCart || !local.cartId) ? local.coupon : null);
  const claimInfo = (local) => ({ code: couponOf(local), payer: local.isCart ? local.payer : local.gift?.fromUuid || local.uuid, method: local.method });
  async function setPay(local, fields) {
    await (local.isCart ? store.updateCart(local.id, fields) : store.updateOrder(local.id, fields));
    if (couponOf(local) && CLAIM_FOR[fields.status]) await store.setClaimState(local.id, CLAIM_FOR[fields.status], claimInfo(local));
  }
  async function complete(local, ctx, paymentIntent) {
    const status = await (local.isCart ? fulfillCart(local, ctx, paymentIntent) : fulfill(local, ctx, paymentIntent));
    if (couponOf(local)) await store.setClaimState(local.id, 'used', claimInfo(local));
    return status;
  }

  // Por qué no se pudo reclamar el uso (otro pago lo acaba de gastar o apartar, o se pausó)
  async function couponGone(code, payer) {
    const c = await store.getCoupon(code);
    return couponProblem(c, { subtotal: Number.MAX_SAFE_INTEGER, usage: c ? await store.couponUsage(code, payer) : null }) || 'Ese cupón ya no se puede usar.';
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
        serverIp: await serverIp(),
        discordUrl: DISCORD_URL,
        discordLogin: discord.loginEnabled,
        currency: CURRENCY,
        paymentsEnabled: Boolean(stripe || paypal || mp),
        cardEnabled: Boolean(stripe),
        paypalEnabled: Boolean(paypal),
        mercadopagoEnabled: Boolean(mp),
        ...(mp || (stripe && cardInPesos(req)) ? { mpRate: await mpRate() } : {}),
        // (31 oct 2026) la tarjeta cobra en pesos desde México (y en la moneda de la tienda desde fuera)
        cardCurrency: stripe && cardInPesos(req) ? MP_CURRENCY : CURRENCY,
        discordInvite: DISCORD_URL,
        termsVersion: TERMS_VERSION,
        // Solo se puede comprar a jugadores que el puente ya conoce.
        bridge: Boolean(env.BRIDGE_SECRET),
      }),

    // Los comandos y los IDs de rol nunca se envían al navegador.
    // (TF Client 1.3.163) los «staffOnly» (el set de Discord Nitro) no salen en la tienda: solo en el panel de staff
    'GET /api/products': async (req, { discord }) =>
      json(
        allProducts.filter((p) => !p.staffOnly).map(({ commands, discordRoles, ...p }) => ({
          ...p,
          ...(p.pool ? { pool: p.pool.map(({ give, ...entry }) => entry) } : {}),
          discordRole: discord.rolesEnabled && hasRoles({ discordRoles }),
        })),
      ),

    // VFX: el catálogo y, con la cuenta iniciada, lo que tiene y lleva equipado el jugador.
    'GET /api/vfx': async (req) => {
      const user = await currentUser(req);
      return json({ free: VFX_FREE, price: VFX_PRICE, cats: vfx.cats, kills: vfx.kills, me: user ? await vfxView(user.uuid) : null });
    },

    // Obtener un VFX (gratis): queda en la cuenta y se equipa; el servidor lo pone en la siguiente consulta del puente.
    'POST /api/vfx/claim': async (req) => {
      const user = await currentUser(req);
      if (!user) return json({ error: 'Inicia sesión para obtener efectos.', code: 'login' }, 401);
      const terms = termsMissing(user);
      if (terms) return terms;
      const body = (await readJson(req)) || {};
      const item = vfxItem(body.kind, body.id);
      if (!item) return json({ error: body.kind === 'pack' ? 'Los paquetes de skills ya no están disponibles.' : 'Ese efecto no existe.' }, 400);
      if (!VFX_FREE) return json({ error: 'Este efecto ya no es gratis.' }, 403);
      await store.vfxGive(user.uuid, item.key);
      await store.vfxEquip(user.uuid, item.kind, item.id);
      return json({ me: await vfxView(user.uuid) });
    },

    // Equipar uno que ya tiene, o quitarlo (id null).
    'POST /api/vfx/equip': async (req) => {
      const user = await currentUser(req);
      if (!user) return json({ error: 'Inicia sesión para equipar efectos.', code: 'login' }, 401);
      const body = (await readJson(req)) || {};
      if (body.kind !== 'kill' && body.kind !== 'pack') return json({ error: 'Petición no válida.' }, 400);
      if (body.id != null) {
        const item = vfxEquipable(body.kind, body.id);
        if (!item) return json({ error: 'Ese efecto no existe.' }, 400);
        const mine = await store.vfxFor(user.uuid);
        if (!mine.owned.includes(item.key)) return json({ error: 'Primero tienes que obtenerlo.', code: 'not_owned' }, 403);
      }
      await store.vfxEquip(user.uuid, body.kind, body.id || null);
      return json({ me: await vfxView(user.uuid) });
    },

    // Skills: el catálogo de clases y, con la cuenta iniciada, las que tiene, la activa y las que está pagando con monedas.
    'GET /api/skills': async (req) => {
      const user = await currentUser(req);
      return json({
        price: skillsCatalog.price,
        coinPrice: skillsCatalog.coinPrice,
        onSale: skillsCatalog.onSale !== false,
        keys: skillsCatalog.keys,
        groups: skillsCatalog.groups,
        classes: skillClasses,
        me: user ? await skillsView(user.uuid) : null,
      });
    },

    // Comprar una clase con las monedas del servidor: se cobra en el juego cuando el jugador está conectado (o al entrar)
    // con /tf web monedas quitar; si no tiene bastantes, el servidor no cobra nada y el pedido queda como no entregado.
    // Cuando el servidor confirma el cobro, la clase es suya y pasa a ser la activa (ver /bridge/poll).
    'POST /api/skills/coins': async (req) => {
      const user = await currentUser(req);
      if (!user) return json({ error: 'Inicia sesión para comprar con tus monedas.', code: 'login' }, 401);
      // (30 oct 2026, el dueño: «nadie puede comprarlas ahora hasta que hagas un reajuste en la economía»)
      if (skillsCatalog.onSale === false) {
        return json({ error: 'Las skills no están a la venta por ahora: estamos reajustando la economía del servidor.', code: 'not_on_sale' }, 403);
      }
      const terms = termsMissing(user);
      if (terms) return terms;
      const body = (await readJson(req)) || {};
      const cls = skillById.get(String(body.id || ''));
      if (!cls) return json({ error: 'Esa clase no existe.' }, 400);
      if (!Number.isSafeInteger(cls.coinPrice) || cls.coinPrice < 1) return json({ error: 'Esta clase no se vende con monedas.' }, 400);
      if ((await store.skillsOwned(user.uuid)).includes(cls.id)) return json({ error: `Ya tienes la clase ${cls.name}.`, code: 'owned' }, 409);
      if ((await store.skillsPending(user.uuid)).includes(cls.id)) {
        return json({ error: `Ya estás comprando ${cls.name}: se cobra en cuanto entres al servidor.`, code: 'pending' }, 409);
      }
      const player = await store.playerByUuid(user.uuid);
      const username = player?.name || user.name;
      const bytes = crypto.getRandomValues(new Uint8Array(12));
      const id = `COIN${[...bytes].map((b) => b.toString(16).padStart(2, '0')).join('').toUpperCase()}`;
      await store.createOrder({
        id,
        status: 'pending',
        username,
        uuid: user.uuid,
        productId: `${SKILL_PREFIX}${cls.id}`,
        quantity: 1,
        amount: 0,
        currency: CURRENCY,
      });
      await store.queueDelivery(id, {
        username,
        uuid: user.uuid,
        commands: [`tf web monedas quitar {player} ${cls.coinPrice}`],
        // «tienda-monedas»: el mod lo trata como un cobro con monedas (sin anuncio y con el motivo tal cual si falla)
        extra: { kind: 'tienda-monedas', color: cls.color },
      });
      log.log(`Clase con monedas ${id}: ${cls.name} (${cls.coinPrice} monedas) para ${username} (${user.uuid}), en cola del puente`);
      return json({ id, status: 'queued' });
    },

    // (1.3.114, el dueño: «en la tienda web ponle precio en monedas a las skills y a los cosméticos») un cosmético con
    // las Fantastic Coins del servidor: como las clases, se cobra en el juego (tf web monedas quitar) y, solo si se
    // pudo cobrar, se da (el mod para en el primer comando que falla de un pedido con monedas).
    'POST /api/coins/buy': async (req) => {
      const user = await currentUser(req);
      if (!user) return json({ error: 'Inicia sesión para comprar con tus monedas.', code: 'login' }, 401);
      const terms = termsMissing(user);
      if (terms) return terms;
      const body = (await readJson(req)) || {};
      const product = productById.get(String(body.productId || ''));
      if (!product || product.category !== 'cosmeticos' || !Number.isSafeInteger(product.coinPrice) || product.coinPrice < 1) {
        return json({ error: 'Esto no se vende con monedas.' }, 400);
      }
      if ((await store.ownedProducts(user.uuid)).includes(product.id)) return json({ error: `Ya tienes ${product.name}.`, code: 'owned' }, 409);
      if ((await store.coinPending(user.uuid)).includes(product.id)) {
        return json({ error: `Ya estás comprando ${product.name}: se cobra en cuanto entres al servidor.`, code: 'pending' }, 409);
      }
      const player = await store.playerByUuid(user.uuid);
      const username = player?.name || user.name;
      const bytes = crypto.getRandomValues(new Uint8Array(12));
      const id = `COIN${[...bytes].map((b) => b.toString(16).padStart(2, '0')).join('').toUpperCase()}`;
      await store.createOrder({ id, status: 'pending', username, uuid: user.uuid, productId: product.id, quantity: 1, amount: 0, currency: CURRENCY });
      await store.queueDelivery(id, {
        username,
        uuid: user.uuid,
        commands: [`tf web monedas quitar {player} ${product.coinPrice}`, ...product.commands],
        extra: { kind: 'tienda-monedas', color: product.colors?.[1] || '#f4c95d' },
      });
      log.log(`Cosmético con monedas ${id}: ${product.name} (${product.coinPrice} monedas) para ${username} (${user.uuid}), en cola del puente`);
      return json({ id, status: 'queued' });
    },

    // Elegir la clase activa entre las que tiene (o ninguna, con id null). El servidor la pone en la siguiente consulta.
    'POST /api/skills/active': async (req) => {
      const user = await currentUser(req);
      if (!user) return json({ error: 'Inicia sesión para elegir tu clase.', code: 'login' }, 401);
      const body = (await readJson(req)) || {};
      if (body.id != null) {
        const cls = skillById.get(String(body.id));
        if (!cls) return json({ error: 'Esa clase no existe.' }, 400);
        if (!(await store.skillsOwned(user.uuid)).includes(cls.id)) return json({ error: 'Primero tienes que comprar esta clase.', code: 'not_owned' }, 403);
      }
      const cur = await store.skillActive(user.uuid);
      if ((cur?.cls || null) !== (body.id || null)) await store.setSkillActive(user.uuid, body.id || null);
      return json({ me: await skillsView(user.uuid) });
    },

    // Armario: lo que tiene (de sus compras y de su rango) y lo que lleva puesto en cada hueco.
    'GET /api/account/wardrobe': async (req) => {
      const user = await currentUser(req);
      if (!user) return json({ error: 'Inicia sesión primero.' }, 401);
      return json(await wardrobeView(user.uuid));
    },

    // Poner una pieza que tiene en su hueco, o quitarla (id null).
    'POST /api/account/wardrobe': async (req) => {
      const user = await currentUser(req);
      if (!user) return json({ error: 'Inicia sesión primero.' }, 401);
      const body = (await readJson(req)) || {};
      if (!WARDROBE_SLOTS.includes(body.slot)) return json({ error: 'Petición no válida.' }, 400);
      let piece = null;
      if (body.id != null) {
        const owned = await ownedPieces(user.uuid);
        const found = owned.find((x) => x.set === body.set && x.id === body.id && x.slot === body.slot);
        if (!found) return json({ error: 'Esa pieza no es tuya o no va en ese hueco.', code: 'not_owned' }, 403);
        piece = `${found.set}/${found.id}`;
      }
      await store.setWardrobe(user.uuid, body.slot, piece);
      return json(await wardrobeView(user.uuid));
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
    'GET /descargar': async () => passDownload(CLIENT_JAR_URL, 'tfclient.jar', 'application/java-archive'),
    'GET /tfclient.jar': async () => passDownload(CLIENT_JAR_URL, 'tfclient.jar', 'application/java-archive'),
    'GET /packs': async () => passDownload(PACKS_URL, 'jefes-17.tar.enc', 'application/octet-stream'),

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
      const method = body.method === 'paypal' ? 'paypal' : body.method === 'mercadopago' ? 'mercadopago' : 'stripe';
      if (method === 'stripe' && !stripe) {
        return json({ error: paypal || mp ? 'El pago con tarjeta no está disponible ahora mismo. Usa otro método.' : 'Los pagos no están configurados todavía.' }, 503);
      }
      if (method === 'paypal' && !paypal) return json({ error: 'El pago con PayPal no está disponible ahora mismo.' }, 503);
      if (method === 'mercadopago' && !mp) return json({ error: 'El pago con Mercado Pago no está disponible ahora mismo.' }, 503);
      const product = productById.get(body.productId);
      const quantity = Number.parseInt(body.quantity ?? 1, 10);
      if (!product || product.staffOnly) return json({ error: 'Producto no válido.' }, 400);
      if (product.price === 0) return json({ error: 'Este regalo es gratis: reclámalo con tu cuenta.', code: 'free' }, 400);
      // (1.3.117) los cosméticos solo con Fantastic Coins (POST /api/coins/buy)
      if (product.coinsOnly) return json({ error: 'Esto solo se compra con Fantastic Coins.', code: 'coins' }, 400);
      if (typeof body.username !== 'string' || !USERNAME_RE.test(body.username)) {
        return json({ error: 'Nombre de usuario de Minecraft no válido (3-16 letras, números o _).' }, 400);
      }
      const maxQuantity = product.maxQuantity || MAX_QUANTITY;
      if (!Number.isInteger(quantity) || quantity < 1 || quantity > maxQuantity) {
        return json({ error: `La cantidad debe estar entre 1 y ${maxQuantity}.` }, 400);
      }
      if (body.acceptTerms !== true) return json({ error: TERMS_ERROR, code: 'terms' }, 400);

      // Comprar como regalo: hace falta la cuenta de quien paga (el regalo dice de quién es y la firma de los Términos
      // y la sanción por reembolso injustificado son suyas, no del que lo recibe)
      const gift = body.gift === true;
      const buyer = await currentUser(req);
      if (gift && !buyer) {
        return json({ error: 'Inicia sesión para regalar: así sabrá quién se lo regala.', code: 'login' }, 401);
      }
      const giverName = gift ? (await store.playerByUuid(buyer.uuid))?.name || buyer.name : null;

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

      if (gift && player.uuid === String(buyer.uuid).toLowerCase()) {
        return json({ error: 'Ese eres tú: para comprártelo a ti, usa «Comprar».', code: 'gift_self' }, 400);
      }

      const q = await quote(product, player.uuid);
      if (q.blocked) return json({ error: q.blocked, code: q.code || 'rank_owned' }, 409);

      // (3 nov 2026) Cupón de descuento: sale del precio en dólares; el que paga es quien regala (en un regalo)
      const applied = await applyCoupon(req, body.coupon, { subtotal: q.unit * quantity, payer: gift ? String(buyer.uuid).toLowerCase() : player.uuid });
      if (applied.error) return json({ error: applied.error, code: applied.code }, applied.code === 'coupon_locked' ? 429 : 400);
      const discount = applied.discount;

      const id = orderId();
      const base = PUBLIC_URL || url.origin;
      // (3 nov 2026) El uso del cupón se reclama ANTES de abrir el pago, de una vez: si se gastó (un cupón de un solo uso
      // que alguien acaba de usar), no se cobra con descuento
      const couponPayer = gift ? String(buyer.uuid).toLowerCase() : player.uuid;
      if (applied.coupon && !(await store.claimCoupon({ code: applied.coupon.code, paymentId: id, payer: couponPayer, method }))) {
        return json({ error: await couponGone(applied.coupon.code, couponPayer), code: 'coupon' }, 409);
      }
      // Con cupón se cobra una sola línea con el total ya descontado (cantidad 1, «×N» en el nombre); sin él, como siempre
      const amount = q.unit * quantity - discount;
      // Si el que compra tiene sesión con Discord conectado, el anuncio y el rol van a su Discord. En un regalo, no: el
      // rol es del que lo recibe (si tiene su Discord conectado a su cuenta).
      const account = !gift && buyer?.discord ? { id: buyer.discord.id, username: buyer.discord.username } : null;
      const giftInfo = gift ? { fromUuid: String(buyer.uuid).toLowerCase(), fromName: giverName, message: cleanGiftMessage(body.message) || null } : null;
      const forWhom = gift ? `Regalo de ${giverName} para ${player.name}` : `Para ${player.name}`;
      const baseName = q.from ? `${product.name} (mejora desde ${q.from.name})` : product.name;
      const name = discount ? `${baseName}${quantity > 1 ? ` ×${quantity}` : ''} · cupón ${applied.coupon.code}` : baseName;
      // Con Mercado Pago se cobra en pesos (su precio redondeado); lo demás, en la moneda de la tienda
      // (31 oct 2026) con tarjeta desde México, también en pesos (las tarjetas mexicanas que no aceptan dólares)
      const pesos = method === 'mercadopago' || (method === 'stripe' && cardInPesos(req));
      const rate = pesos ? await mpRate() : 0;
      const lineQty = discount ? 1 : quantity;
      const lineUsd = discount ? amount : q.unit;
      const mxUnit = pesos ? mxnCents(lineUsd, rate) : 0;
      const charged = pesos ? { amount: mxUnit * lineQty, currency: MP_CURRENCY } : { amount, currency: CURRENCY };
      try {
        let checkout;
        if (method === 'mercadopago') {
          const https = base.startsWith('https://');
          const pref = await mp.createPreference({
            orderId: id,
            title: name,
            description: `${forWhom} en ${SERVER_NAME}. ${product.description || ''}`.trim(),
            pictureUrl: https && paypalImages[product.image] ? `${base}/${paypalImages[product.image]}` : undefined,
            quantity: lineQty,
            unitPrice: mxUnit / 100,
            currency: MP_CURRENCY,
            successUrl: `${base}/success?order=${id}`,
            failureUrl: `${base}/tienda?cancel=${id}`,
            pendingUrl: `${base}/success?order=${id}`,
            notificationUrl: https ? `${base}/webhook/mercadopago` : undefined,
            statement: 'TIERRASFANTASTICAS',
            // Con cupón, la página de pago caduca a la hora (como la de Stripe): un uso apartado no se paga días después
            ...(applied.coupon ? { expiresInMinutes: 60 } : {}),
          });
          checkout = { id: pref.id, url: pref.init_point };
          if (!checkout.url) throw new Error('Mercado Pago no devolvió el enlace de pago');
        } else if (method === 'paypal') {
          const created = await paypal.createOrder({
            amount,
            currency: CURRENCY,
            description: `${forWhom} en ${SERVER_NAME}`.slice(0, 127),
            customId: id,
            item: {
              name,
              quantity: lineQty,
              unitAmount: lineUsd,
              description: `${forWhom}. ${product.description || ''}`.trim(),
              sku: product.id,
              imageUrl: base.startsWith('https://') && paypalImages[product.image] ? `${base}/${paypalImages[product.image]}` : undefined,
              url: base.startsWith('https://') ? `${base}/tienda` : undefined,
            },
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
            currency: charged.currency,
            unitAmount: pesos ? mxUnit : lineUsd,
            quantity: lineQty,
            name,
            description: `${forWhom} en ${SERVER_NAME}. ${product.description || ''}`.trim(),
            image: base.startsWith('https://') && product.image ? `${base}/${product.image.replace(/^\//, '')}` : undefined,
            successUrl: `${base}/success?order=${id}`,
            cancelUrl: `${base}/tienda?cancel=${id}`,
            metadata: {
              order_id: id,
              product_id: product.id,
              player: player.name,
              uuid: player.uuid,
              ...(gift ? { gift_from: giverName, gift_from_uuid: giftInfo.fromUuid } : {}),
              ...(discount ? { coupon: applied.coupon.code } : {}),
            },
          });
        }
        await store.createOrder({
          id,
          status: 'pending',
          username: player.name,
          uuid: player.uuid,
          productId: product.id,
          quantity,
          amount: charged.amount,
          currency: charged.currency,
          // El token de Discord sirve para meter al comprador en el servidor de Discord con su rol.
          discord: account,
          discordId: account?.id,
          sessionId: checkout.id,
          rankFrom: q.from?.id,
          termsVersion: TERMS_VERSION,
          method,
          gift: giftInfo,
          coupon: applied.coupon?.code || null,
          discount,
        });
        await recordTerms(
          req,
          gift
            ? { uuid: giftInfo.fromUuid, username: giverName, context: 'regalo', orderId: id }
            : { uuid: player.uuid, username: player.name, context: 'compra', orderId: id },
        );
        return json({ id, url: checkout.url });
      } catch (err) {
        // El pago no se abrió: el uso del cupón queda libre
        if (applied.coupon) await store.setClaimState(id, 'void');
        log.error(`Error creando el pago en ${method === 'paypal' ? 'PayPal' : method === 'mercadopago' ? 'Mercado Pago' : 'Stripe'}:`, err.message);
        return json({ error: 'No se pudo iniciar el pago. Inténtalo de nuevo.' }, 502);
      }
    },

    // --- Carrito (3 nov 2026, el dueño: «agrega en la tienda la opción del carrito, para que paguen un solo pago al
    // comprar varios productos») ---
    // El precio de lo que hay en el carrito (o de un solo producto) para el jugador que lo recibe, con el cupón: lo que
    // enseña la tienda antes de pagar. El precio de verdad lo vuelve a calcular /api/cart/checkout (o /api/checkout).
    'POST /api/cart/quote': async (req) => {
      const body = (await readJson(req)) || {};
      const items = legacyCartItems(body);
      const buyer = await currentUser(req);
      const buyerUuid = buyer?.uuid ? String(buyer.uuid).toLowerCase() : null;
      // «Para ti»: el nombre que escribe quien compra (con el navegador viejo y «gift», ese nombre era el del regalo)
      const self = body.gift !== true && typeof body.username === 'string' && USERNAME_RE.test(body.username) ? await store.findPlayer(body.username) : null;
      const priced = await priceLines(items, { self, buyerUuid });
      if (priced.error) return json({ error: priced.error, code: priced.code, productId: priced.productId || null }, 400);
      const { lines } = priced;
      const subtotal = sum(lines.map((l) => l.line));
      // El que paga: la cuenta con la que se entra o, sin cuenta, el jugador de «para ti»
      const payer = buyerUuid || self?.uuid || null;
      let coupon = null;
      let couponError = null;
      let discount = 0;
      // (con algo bloqueado, el cupón se mira sobre lo demás: el carrito dice qué quitar y el cupón sigue puesto)
      if (body.coupon) {
        const applied = await applyCoupon(req, body.coupon, { subtotal, payer });
        if (applied.error) couponError = applied.error;
        else {
          discount = applied.discount;
          coupon = couponInfo(applied.coupon, discount);
        }
      }
      // En pesos: con Mercado Pago, y con tarjeta desde México
      const rate = mp || (stripe && cardInPesos(req)) ? await mpRate() : null;
      const ok = lines.every((l) => !l.blocked);
      const mxn = rate && ok ? sum(chargeLines(lines, discount, rate).charged) : null;
      return json({
        lines: lines.map((l) => ({
          productId: l.product.id,
          keys: l.keys,
          name: l.product.name,
          image: l.product.image || null,
          category: l.product.category,
          quantity: l.quantity,
          maxQuantity: l.product.maxQuantity || MAX_QUANTITY,
          unit: l.unit,
          line: l.line,
          upgradeFrom: l.from?.name || null,
          // (3 nov 2026) la mejora es desde un rango que va en este carrito (true) o desde el que ya tiene (false)
          upgradeInCart: l.fromCart || false,
          blocked: l.blocked,
          to: l.recipient?.name || l.to || null,
          gift: l.gift,
        })),
        subtotal,
        discount,
        total: subtotal - discount,
        coupon,
        couponError,
        mxn,
        // Los regalos dicen de quién son: hace falta la cuenta de quien paga
        needsLogin: lines.some((l) => l.gift || (l.to && !l.recipient)) && !buyer,
        player: self ? { name: self.name, uuid: self.uuid } : null,
      });
    },

    // Pagar el carrito: un solo pago (Stripe, PayPal o Mercado Pago) con todos sus productos, cada uno para su jugador
    // (para ti o de regalo, con su mensaje). Cada producto es un pedido (con cart_id) que se entrega y se reembolsa como
    // cualquier otro; el carrito es el pago, y es de quien paga.
    'POST /api/cart/checkout': async (req, { url }) => {
      const body = (await readJson(req)) || {};
      const method = body.method === 'paypal' ? 'paypal' : body.method === 'mercadopago' ? 'mercadopago' : 'stripe';
      if (method === 'stripe' && !stripe) {
        return json({ error: paypal || mp ? 'El pago con tarjeta no está disponible ahora mismo. Usa otro método.' : 'Los pagos no están configurados todavía.' }, 503);
      }
      if (method === 'paypal' && !paypal) return json({ error: 'El pago con PayPal no está disponible ahora mismo.' }, 503);
      if (method === 'mercadopago' && !mp) return json({ error: 'El pago con Mercado Pago no está disponible ahora mismo.' }, 503);
      if (body.acceptTerms !== true) return json({ error: TERMS_ERROR, code: 'terms' }, 400);

      const wanted = legacyCartItems(body);
      const buyer = await currentUser(req);
      const buyerUuid = buyer?.uuid ? String(buyer.uuid).toLowerCase() : null;
      // «Para ti»: solo hace falta el nombre si hay algo que no es regalo
      const hasSelf = wanted.some((it) => !(typeof it?.to === 'string' && it.to.trim()));
      let self = null;
      if (hasSelf) {
        if (typeof body.username !== 'string' || !USERNAME_RE.test(body.username)) {
          return json({ error: 'Nombre de usuario de Minecraft no válido (3-16 letras, números o _).' }, 400);
        }
        self = await store.findPlayer(body.username);
        if (!self) {
          return json(
            { error: `No encontramos a ${body.username} en ${SERVER_NAME}. Entra al servidor al menos una vez con ese nombre y vuelve a intentarlo.`, code: 'unknown_player' },
            404,
          );
        }
        if (body.uuid && String(body.uuid).toLowerCase() !== self.uuid) {
          return json({ error: 'El jugador ha cambiado. Vuelve a comprobar el nombre.', code: 'player_changed' }, 409);
        }
      }

      const priced = await priceLines(wanted, { self, buyerUuid });
      if (priced.error) return json({ error: priced.error, code: priced.code, productId: priced.productId || null }, 400);
      const { lines } = priced;
      const nobody = lines.find((l) => l.blockedCode === 'unknown_player');
      if (nobody) return json({ error: nobody.blocked, code: 'unknown_player', productId: nobody.product.id }, 404);
      if (!buyer && lines.some((l) => l.gift)) {
        return json({ error: 'Inicia sesión para regalar: así sabrán quién se lo regala.', code: 'login' }, 401);
      }
      const stuck = lines.find((l) => l.blocked);
      if (stuck) return json({ error: `${stuck.product.name} para ${stuck.recipient?.name || stuck.to}: ${stuck.blocked}`, code: stuck.blockedCode, productId: stuck.product.id }, 409);
      const subtotal = sum(lines.map((l) => l.line));
      const payer = buyerUuid || self.uuid;
      const payerName = buyer ? (await store.playerByUuid(buyer.uuid))?.name || buyer.name : self.name;
      const applied = await applyCoupon(req, body.coupon, { subtotal, payer });
      if (applied.error) return json({ error: applied.error, code: applied.code }, applied.code === 'coupon_locked' ? 429 : 400);
      const discount = applied.discount;
      const coupon = applied.coupon;

      const pesos = method === 'mercadopago' || (method === 'stripe' && cardInPesos(req));
      const rate = pesos ? await mpRate() : null;
      const { usd, charged } = chargeLines(lines, discount, rate);
      const currency = pesos ? MP_CURRENCY : CURRENCY;
      const amount = sum(charged);
      const id = orderId('CART');
      const base = PUBLIC_URL || url.origin;
      // El uso del cupón, reclamado antes de abrir el pago (ver /api/checkout)
      if (coupon && !(await store.claimCoupon({ code: coupon.code, paymentId: id, payer, method }))) {
        return json({ error: await couponGone(coupon.code, payer), code: 'coupon' }, 409);
      }
      const https = base.startsWith('https://');
      // El rol y el anuncio de lo que es «para ti» van al Discord de quien compra (si entró con su cuenta)
      const account = buyer?.discord ? { id: buyer.discord.id, username: buyer.discord.username } : null;
      const giftOf = (l) => (l.gift ? { fromUuid: buyerUuid, fromName: payerName, message: l.message || null } : null);
      const forWhom = (l) => (l.gift ? `Regalo de ${payerName} para ${l.recipient.name}` : `Para ${l.recipient.name}`);
      const gifts = lines.filter((l) => l.gift);
      const count = sum(lines.map((l) => l.quantity));
      const title = `Carrito de ${SERVER_NAME}: ${count} ${count === 1 ? 'producto' : 'productos'}`;
      // Las líneas del pago: la de siempre (precio × cantidad) si no lleva parte del cupón; si la lleva, una sola con su
      // total ya descontado («×N» en el nombre)
      const payLines = lines.map((l, i) => {
        const whole = usd[i] === l.line;
        const qty = whole ? l.quantity : 1;
        const unitCharged = whole ? charged[i] / l.quantity : charged[i];
        const unitUsd = whole ? l.unit : usd[i];
        const name = `${lineName(l)}${qty === 1 && l.quantity > 1 ? ` ×${l.quantity}` : ''}${l.gift ? ` · regalo para ${l.recipient.name}` : ''}${whole ? '' : ` · cupón ${coupon.code}`}`;
        return {
          name,
          quantity: qty,
          unitCharged,
          unitUsd,
          description: `${forWhom(l)} en ${SERVER_NAME}. ${l.product.description || ''}`.trim(),
          short: forWhom(l),
          product: l.product,
        };
      });
      const items = lines.map((l, i) => ({
        orderId: cartOrderId(id, i),
        productId: l.product.id,
        quantity: l.quantity,
        unit: l.unit,
        line: l.line,
        charged: charged[i],
        rankFrom: l.from?.id || null,
        to: l.recipient.name,
        gift: l.gift,
      }));
      try {
        let checkout;
        if (method === 'mercadopago') {
          const pref = await mp.createPreference({
            orderId: id,
            title,
            currency: MP_CURRENCY,
            items: payLines.map((pl, i) => ({
              id: items[i].orderId,
              title: pl.name,
              description: pl.description,
              pictureUrl: https && paypalImages[pl.product.image] ? `${base}/${paypalImages[pl.product.image]}` : undefined,
              quantity: pl.quantity,
              unitPrice: pl.unitCharged / 100,
            })),
            successUrl: `${base}/success?order=${id}`,
            failureUrl: `${base}/tienda?cancel=${id}`,
            pendingUrl: `${base}/success?order=${id}`,
            notificationUrl: https ? `${base}/webhook/mercadopago` : undefined,
            statement: 'TIERRASFANTASTICAS',
            ...(coupon ? { expiresInMinutes: 60 } : {}),
          });
          checkout = { id: pref.id, url: pref.init_point };
          if (!checkout.url) throw new Error('Mercado Pago no devolvió el enlace de pago');
        } else if (method === 'paypal') {
          const created = await paypal.createOrder({
            amount,
            currency: CURRENCY,
            description: `Compra de ${payerName} en ${SERVER_NAME}: ${count} ${count === 1 ? 'producto' : 'productos'}`.slice(0, 127),
            customId: id,
            items: payLines.map((pl) => ({
              name: pl.name,
              quantity: pl.quantity,
              unitAmount: pl.unitUsd,
              description: `${pl.short}. ${pl.product.description || ''}`.trim(),
              sku: pl.product.id,
              imageUrl: https && paypalImages[pl.product.image] ? `${base}/${paypalImages[pl.product.image]}` : undefined,
              url: https ? `${base}/tienda` : undefined,
            })),
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
            currency,
            name: title,
            lines: payLines.map((pl) => ({
              name: pl.name,
              description: pl.description,
              image: https && pl.product.image ? `${base}/${pl.product.image.replace(/^\//, '')}` : undefined,
              unitAmount: pl.unitCharged,
              quantity: pl.quantity,
            })),
            successUrl: `${base}/success?order=${id}`,
            cancelUrl: `${base}/tienda?cancel=${id}`,
            metadata: {
              order_id: id,
              cart: 'si',
              products: lines.map((l) => `${l.product.id}${l.quantity > 1 ? `x${l.quantity}` : ''}${l.gift ? `>${l.recipient.name}` : ''}`).join(',').slice(0, 500),
              player: payerName,
              uuid: payer,
              ...(gifts.length ? { gifts_to: [...new Set(gifts.map((l) => l.recipient.name))].join(',').slice(0, 500) } : {}),
              ...(coupon ? { coupon: coupon.code } : {}),
            },
          });
        }
        const orders = lines.map((l, i) => ({
          id: items[i].orderId,
          status: 'pending',
          username: l.recipient.name,
          uuid: l.recipient.uuid,
          productId: l.product.id,
          quantity: l.quantity,
          amount: charged[i],
          currency,
          discord: l.gift ? null : account,
          discordId: l.gift ? null : account?.id,
          rankFrom: l.from?.id,
          termsVersion: TERMS_VERSION,
          method,
          gift: giftOf(l),
          cartId: id,
          coupon: coupon?.code || null,
          discount: l.line - usd[i],
        }));
        await store.createCart(
          {
            id,
            status: 'pending',
            // El carrito es de quien paga (cada producto dice para quién es)
            username: payerName,
            uuid: payer,
            payer,
            amount,
            currency,
            subtotal,
            discount,
            coupon: coupon?.code || null,
            method,
            sessionId: checkout.id,
            items,
            gift: null,
            discord: account,
            discordId: account?.id,
            termsVersion: TERMS_VERSION,
          },
          orders,
        );
        await recordTerms(req, { uuid: payer, username: payerName, context: gifts.length ? 'regalo' : 'compra', orderId: id });
        return json({ id, url: checkout.url });
      } catch (err) {
        if (coupon) await store.setClaimState(id, 'void');
        log.error(`Error creando el pago del carrito en ${method === 'paypal' ? 'PayPal' : method === 'mercadopago' ? 'Mercado Pago' : 'Stripe'}:`, err.message);
        return json({ error: 'No se pudo iniciar el pago. Inténtalo de nuevo.' }, 502);
      }
    },

    // Panel de staff (/staff), con la clave ADMIN_KEY (secreto de Cloudflare): los últimos pedidos y reembolsar uno a
    // mano. Es lo mismo que un reembolso completo en Stripe/PayPal: se retira del servidor lo entregado (el rango y sus
    // objetos) y sus roles de Discord. Sirve también para lo reclamado gratis, que no tiene pago que reembolsar. El
    // dinero, si lo hubo, se devuelve aparte desde Stripe o PayPal (si se hace allí, esto ya pasa solo).
    'GET /api/staff/orders': async (req) => {
      const denied = await staffDenied(req);
      if (denied) return denied;
      const orders = await store.recentOrders(100);
      return json({
        orders: orders.map((o) => ({
          id: o.id,
          status: o.status,
          username: o.username,
          product: productById.get(o.productId)?.name || o.productId,
          amount: o.amount,
          currency: o.currency,
          method: o.method || (o.amount === 0 ? 'gratis' : 'stripe'),
          refunded: o.refunded || null,
          error: o.error || null,
          createdAt: o.createdAt,
          refundable: CLAIMED.includes(o.status) && !o.refunded,
          disputable: o.amount > 0 && ['queued', ...CLAIMED].includes(o.status),
          // (3 nov 2026) pagado junto con otros en un carrito, y el cupón que llevó (descuento en céntimos de dólar)
          cartId: o.cartId || null,
          coupon: o.coupon ? { code: o.coupon, discount: o.discount || 0 } : null,
        })),
      });
    },

    // La IP del servidor (la de la web y la de TF Client): verla y cambiarla. Vacía = la de SERVER_IP.
    'GET /api/staff/server': async (req) => {
      const denied = await staffDenied(req);
      if (denied) return denied;
      return json({ ip: await serverIp(), default: SERVER_IP });
    },

    'POST /api/staff/server': async (req) => {
      const denied = await staffDenied(req);
      if (denied) return denied;
      const body = (await readJson(req)) || {};
      const ip = typeof body.ip === 'string' ? body.ip.trim().toLowerCase() : '';
      if (ip && !SERVER_IP_RE.test(ip)) return json({ error: 'Esa IP no es válida (ejemplo: 216.163.187.40:19015 o 1.2.3.4:25565).' }, 400);
      const port = ip.includes(':') ? Number(ip.split(':')[1]) : 25565;
      if (port < 1 || port > 65535) return json({ error: 'El puerto tiene que ir de 1 a 65535.' }, 400);
      await store.setSetting('server_ip', ip);
      log.warn(`IP del servidor cambiada desde el panel de staff: ${ip || `(la de siempre: ${SERVER_IP})`}`);
      return json({ ok: true, ip: await serverIp() });
    },

    // Probar cualquier producto (rangos, crates, packs, efectos VFX, clases de skills…) en el juego: se entrega gratis a un jugador como un pedido de
    // verdad (rango, rol de Discord, anuncio y comandos) y se puede reembolsar desde el mismo panel.
    // (TF Client 1.3.163) El catálogo entero para el panel de staff, con los que no salen en la tienda (staffOnly)
    'GET /api/staff/products': async (req) => {
      const denied = await staffDenied(req);
      if (denied) return denied;
      return json(allProducts.map(({ commands, discordRoles, pool, ...p }) => p));
    },

    'POST /api/staff/test': async (req, ctx) => {
      const denied = await staffDenied(req);
      if (denied) return denied;
      const body = (await readJson(req)) || {};
      const product = productById.get(body.productId);
      // (31 oct 2026, el dueño: «agrega los efectos VFX y las skills al test») los efectos y las clases de skills no
      // llevan comandos: fulfill los apunta en la cuenta y los equipa, y el puente los manda al servidor
      if (!product || !((product.commands || []).length || skillOf(product) || vfxOf(product))) {
        return json({ error: 'Ese producto no se entrega en el juego.' }, 400);
      }
      const player = typeof body.username === 'string' && USERNAME_RE.test(body.username) ? await store.findPlayer(body.username) : null;
      if (!player) return json({ error: 'Ese jugador no ha entrado nunca al servidor.' }, 404);
      const rank = rankById.get(product.id);
      const current = rank ? await store.getRank(player.uuid) : null;
      const account = await store.accountByUuid(player.uuid);
      const id = `TEST${Date.now().toString(36).toUpperCase()}${randomHex(6).toUpperCase()}`.slice(0, 40);
      await store.createOrder({
        id,
        status: 'pending',
        username: player.name,
        uuid: player.uuid,
        productId: product.id,
        quantity: 1,
        amount: 0,
        currency: CURRENCY,
        discord: account?.discordId ? { id: account.discordId, username: account.username } : null,
        discordId: account?.discordId,
        rankFrom: current && current.rankId !== rank.id && rankById.get(current.rankId)?.tier < rank.tier ? current.rankId : undefined,
      });
      const status = await fulfill(await store.getOrder(id), ctx, null);
      log.warn(`Prueba desde el panel de staff: ${product.id} para ${player.name} (${id})`);
      return json({ ok: true, id, status, product: product.name, username: player.name });
    },

    // (30 oct 2026) El expediente de un pedido para responder una disputa o un contracargo (PDF), cuando sea
    'POST /api/staff/dispute-pdf': async (req) => {
      const denied = await staffDenied(req);
      if (denied) return denied;
      const body = (await readJson(req)) || {};
      const order = typeof body.id === 'string' ? await store.getOrder(body.id) : null;
      if (!order) return json({ error: 'Ese pedido no existe.' }, 404);
      if (!order.amount) return json({ error: 'Ese pedido no se pagó con dinero.' }, 400);
      const pdf = disputePdf(await disputeCase(order));
      return new Response(pdf, {
        headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': `attachment; filename="disputa-${order.id}.pdf"`, 'Cache-Control': 'no-store' },
      });
    },

    'POST /api/staff/refund': async (req, ctx) => {
      const denied = await staffDenied(req);
      if (denied) return denied;
      const body = (await readJson(req)) || {};
      const order = typeof body.id === 'string' ? await store.getOrder(body.id) : null;
      if (!order) return json({ error: 'Ese pedido no existe.' }, 404);
      if (order.refunded) return json({ error: `Ese pedido ya está ${order.refunded}.` }, 409);
      if (!CLAIMED.includes(order.status)) return json({ error: 'Ese pedido no se llegó a entregar: no hay nada que retirar.' }, 400);
      await handleReversal({ order, full: true, dispute: false, manual: true }, ctx);
      log.warn(`Pedido ${order.id} (${order.username}): reembolsado a mano desde el panel de staff`);
      return json({ ok: true, order: (await store.getOrder(order.id))?.refunded || 'reembolsado' });
    },

    // --- Cupones de descuento (3 nov 2026, el dueño: «dame la posibilidad de crear cupones de descuento, tanto en
    // porcentaje como en valores enteros») ---
    'GET /api/staff/coupons': async (req) => {
      const denied = await staffDenied(req);
      if (denied) return denied;
      const now = Date.now();
      const coupons = (await store.listCoupons()).map((c) => ({
        ...c,
        label: couponLabel(c),
        expired: Boolean(c.expiresAt && Date.parse(c.expiresAt) <= now),
        exhausted: Boolean(c.maxUses && c.used >= c.maxUses),
      }));
      return json({ coupons, minTotal: COUPON_MIN_TOTAL });
    },

    'POST /api/staff/coupons': async (req) => {
      const denied = await staffDenied(req);
      if (denied) return denied;
      const made = couponFromInput(await readJson(req));
      if (made.error) return json({ error: made.error }, 400);
      if (!(await store.createCoupon(made.coupon))) return json({ error: `Ya hay un cupón ${made.coupon.code}. Elige otro código.` }, 409);
      log.warn(`Cupón creado desde el panel de staff: ${made.coupon.code} (${couponLabel(made.coupon)})`);
      return json({ ok: true, coupon: await store.getCoupon(made.coupon.code) });
    },

    // Quién usó un cupón: cada pago con el jugador que pagó, cuándo, qué compró, cuánto y si está pagado o pagándose
    'GET /api/staff/coupons/:code/uses': async (req, { params }) => {
      const denied = await staffDenied(req);
      if (denied) return denied;
      const code = normalizeCode(params.code);
      const coupon = code ? await store.getCoupon(code) : null;
      if (!coupon) return json({ error: 'Ese cupón no existe.' }, 404);
      const uses = [];
      for (const u of await store.couponUses(code)) {
        const pay = await loadPayable(u.paymentId);
        const children = pay?.isCart ? await store.cartOrders(pay.id) : pay ? [pay] : [];
        uses.push({
          id: u.paymentId,
          state: u.state,
          at: u.at,
          payer: u.payerName || pay?.gift?.fromName || pay?.username || null,
          method: u.method || pay?.method || null,
          amount: pay?.amount ?? null,
          currency: pay?.currency || null,
          discount: pay ? (pay.isCart ? pay.discount : pay.discount || 0) : null,
          refunded: pay?.refunded || null,
          products: children.map((o) => `${productById.get(o.productId)?.name || o.productId}${o.quantity > 1 ? ` ×${o.quantity}` : ''}${o.gift ? ` (regalo para ${o.username})` : ''}`),
        });
      }
      return json({ coupon: { ...coupon, label: couponLabel(coupon) }, uses });
    },

    // Pausar o volver a activar un cupón ({ active: true|false })
    'POST /api/staff/coupons/:code': async (req, { params }) => {
      const denied = await staffDenied(req);
      if (denied) return denied;
      const body = (await readJson(req)) || {};
      if (typeof body.active !== 'boolean') return json({ error: 'Petición no válida.' }, 400);
      const code = normalizeCode(params.code);
      if (!code || !(await store.setCouponActive(code, body.active))) return json({ error: 'Ese cupón no existe.' }, 404);
      return json({ ok: true, coupon: await store.getCoupon(code) });
    },

    'DELETE /api/staff/coupons/:code': async (req, { params }) => {
      const denied = await staffDenied(req);
      if (denied) return denied;
      const code = normalizeCode(params.code);
      if (!code || !(await store.deleteCoupon(code))) return json({ error: 'Ese cupón no existe.' }, 404);
      log.warn(`Cupón borrado desde el panel de staff: ${code}`);
      return json({ ok: true });
    },

    // Regalos gratis (precio 0): sin Stripe, con la cuenta iniciada y una sola vez por jugador.
    'POST /api/claim': async (req, ctx) => {
      const user = await currentUser(req);
      if (!user) return json({ error: 'Inicia sesión para reclamar el regalo.', code: 'login' }, 401);
      const terms = termsMissing(user);
      if (terms) return terms;
      const body = (await readJson(req)) || {};
      const product = productById.get(body.productId);
      // (1.3.125, el dueño: «ponles el precio a los rangos, solo déjame a un lado el botón de test para probarlos en el
      // juego») Los de pago con testClaim se reclaman gratis solo con la clave del panel de staff (ADMIN_KEY).
      const test = product && product.price !== 0 && product.testClaim && req.headers.get('Authorization');
      if (!product || product.staffOnly || (product.price !== 0 && !test)) return json({ error: 'Ese regalo no existe.' }, 400);
      if (test) {
        const denied = await staffDenied(req);
        if (denied) return denied;
      }
      const player = await store.playerByUuid(user.uuid);
      // El número de pedido sale del jugador y del regalo: el mismo jugador nunca lo tiene dos veces a la vez.
      // giftCode fijo en products.json: no cambia aunque se reordene el catálogo. Si el staff se lo reembolsó (/staff),
      // lo puede volver a reclamar, con otro número (…01, …02).
      const code = String(product.giftCode || product.id).toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 4);
      const hex = user.uuid.replaceAll('-', '').toUpperCase();
      let id = null;
      for (let n = 0; n < 100 && !id; n++) {
        const next = (n ? `GIFT${code}${hex.slice(0, 30)}${String(n).padStart(2, '0')}` : `GIFT${code}${hex}`).slice(0, 40);
        const old = await store.getOrder(next);
        if (!old) id = next;
        else if (!old.refunded) return json({ error: 'Ya reclamaste este regalo.', code: 'claimed', id: next }, 409);
      }
      if (!id) return json({ error: 'Ya reclamaste este regalo.', code: 'claimed' }, 409);
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

    // El recibo en PDF de una compra pagada con dinero (la página del pedido lo descarga sola al confirmarse el pago)
    'GET /api/order/:id/receipt': async (req, { params }) => {
      const order = await loadPayable(params.id);
      if (!order || !order.amount || !settled(order)) return json({ error: 'Recibo no disponible.' }, 404);
      const { pdf } = order.isCart ? await cartReceiptFor(order, false) : await receiptFor(order, false);
      return new Response(pdf, {
        headers: {
          'Content-Type': 'application/pdf',
          'Content-Disposition': `attachment; filename="recibo-${order.id}.pdf"`,
          'Cache-Control': 'private, no-store',
        },
      });
    },

    'GET /api/order/:id': async (req, { params }) => {
      const order = await loadPayable(params.id);
      if (!order) return json({ error: 'Pedido no encontrado.' }, 404);
      return json(order.isCart ? await cartView(order) : orderView(order));
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
            await handleReversal({ paymentIntent, full: dispute || object.refunded === true, dispute, disputeId: dispute ? object.id : null }, ctx);
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
            const local = r.id ? (await store.findOrderBySession(r.id)) || (await store.findCartBySession(r.id)) : null;
            if (local) await capturePayPal(local, ctx);
            break;
          }
          case 'PAYMENT.CAPTURE.COMPLETED':
          case 'PAYMENT.CAPTURE.DENIED': {
            const local = await loadPayable(r.custom_id);
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

    // Webhook de Mercado Pago: un pago creado o actualizado (aprobado, rechazado, reembolsado, contracargo). Se comprueba
    // la firma (si hay MP_WEBHOOK_SECRET) y, sobre todo, se pregunta el pago a la API con nuestro token.
    'POST /webhook/mercadopago': async (req, ctx) => {
      // (2026-10-26, el dueño: «no me deja guardar el webhook») Mercado Pago manda un aviso de prueba al guardarlo y
      // quiere un 200: sin el token todavía, o con un pago de prueba que no existe, se contesta que recibido
      if (!mp) return json({ received: true, configured: false });
      const url = new URL(req.url);
      let event = {};
      try {
        event = JSON.parse((await req.text()) || '{}');
      } catch {
        /* algunos avisos van solo en la dirección */
      }
      const type = event.type || url.searchParams.get('type') || url.searchParams.get('topic');
      const dataId = String(event.data?.id || url.searchParams.get('data.id') || url.searchParams.get('id') || '');
      const ok = await MercadoPago.verifySignature({
        secret: env.MP_WEBHOOK_SECRET,
        signature: req.headers.get('x-signature'),
        requestId: req.headers.get('x-request-id'),
        dataId: url.searchParams.get('data.id') || dataId,
      });
      if (!ok) return new Response('Firma no válida', { status: 401 });
      if (type !== 'payment' || !/^\d+$/.test(dataId)) return json({ received: true });
      try {
        const payment = await mp.getPayment(dataId);
        const local = await loadPayable(payment.external_reference);
        if (local) await handleMpPayments(local, [payment], ctx);
      } catch (err) {
        if (err.status === 404) return json({ received: true, unknown: true }); // aviso de prueba (pago inventado)
        log.error('Error procesando el webhook de Mercado Pago:', err);
        return new Response('Error interno', { status: 500 });
      }
      return json({ received: true });
    },

    // 3) Respaldo: la página de confirmación pide a Stripe el estado del pago por si el webhook tarda.
    'POST /api/order/:id/sync': async (req, ctx) => {
      const order = await loadPayable(ctx.params.id);
      if (!order) return json({ error: 'Pedido no encontrado.' }, 404);
      if (order.method === 'mercadopago' && mp && ['pending', 'awaiting_payment'].includes(order.status)) {
        try {
          await handleMpPayments(order, await mp.paymentsFor(order.id), ctx);
        } catch (err) {
          log.warn(`No se pudo consultar el pago de Mercado Pago de ${order.id}:`, err.message);
        }
      } else if (order.method === 'paypal' && paypal && order.sessionId && ['pending', 'awaiting_payment'].includes(order.status)) {
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
      const fresh = await loadPayable(order.id);
      return json(fresh.isCart ? await cartView(fresh) : orderView(fresh));
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
      // Clases compradas con monedas cuyo cobro confirma ahora el servidor: pasan a ser suyas y su clase activa
      const coinSkills = await store.coinSkillDeliveries(done.filter((d) => d.ok === true).map((d) => d.id));
      const deliveries = (await store.bridgePoll({ players, max, done })).map(({ productId, commands, ...d }) => ({
        ...d,
        product: productById.get(productId)?.name || productId,
        // Las versiones antiguas del mod ejecutan los comandos tal cual: les ponemos nosotros el nombre.
        commands: templates ? commands : commands.map((c) => c.replaceAll('{player}', d.player).replaceAll('{uuid}', d.uuid || '')),
      }));

      for (const s of coinSkills) {
        if (skillById.has(s.cls)) await store.setSkillActive(s.uuid, s.cls);
      }

      const online = players.filter((p) => p.uuid).map((p) => p.uuid);
      const onlineSet = new Set(online);

      // Lo que los jugadores cambian desde el TF Pad (armario y VFX): se guarda como si lo hicieran en la web.
      const pad = body.pad && typeof body.pad === 'object' ? body.pad : {};
      for (const w of list(pad.wardrobe, 50)) {
        if (!w || typeof w.uuid !== 'string' || !UUID_RE.test(w.uuid) || !onlineSet.has(w.uuid.toLowerCase())) continue;
        if (!WARDROBE_SLOTS.includes(w.slot)) continue;
        let piece = null;
        if (w.id != null) {
          const found = (await ownedPieces(w.uuid)).find((x) => x.set === w.set && x.id === w.id && x.slot === w.slot);
          if (!found) continue;
          piece = `${found.set}/${found.id}`;
        }
        await store.setWardrobe(w.uuid, w.slot, piece);
      }
      for (const v of list(pad.vfx, 50)) {
        if (!v || typeof v.uuid !== 'string' || !UUID_RE.test(v.uuid) || !onlineSet.has(v.uuid.toLowerCase())) continue;
        if (v.kind !== 'kill' && v.kind !== 'pack') continue;
        if (v.id == null) {
          await store.vfxEquip(v.uuid, v.kind, null);
          continue;
        }
        // Los efectos de kill gratis se dan al equiparlos; los paquetes viejos solo si ya eran suyos
        const item = vfxEquipable(v.kind, v.id);
        if (!item) continue;
        if (VFX_FREE && item.kind === 'kill') await store.vfxGive(v.uuid, item.key);
        else if (!(await store.vfxFor(v.uuid)).owned.includes(item.key)) continue;
        await store.vfxEquip(v.uuid, item.kind, item.id);
      }
      // La clase de skills activa elegida desde el pad (solo una que sea suya; null = ninguna)
      for (const k of list(pad.skills, 50)) {
        if (!k || typeof k.uuid !== 'string' || !UUID_RE.test(k.uuid) || !onlineSet.has(k.uuid.toLowerCase())) continue;
        if (k.clase == null) {
          await store.setSkillActive(k.uuid, null);
          continue;
        }
        if (typeof k.clase !== 'string' || !skillById.has(k.clase)) continue;
        if (!(await store.skillsOwned(k.uuid)).includes(k.clase)) continue;
        await store.setSkillActive(k.uuid, k.clase);
      }
      // Lo que tiene cada jugador que abrió el Armario del pad (las piezas de sus compras y de su rango)
      const padData = [];
      for (const uuid of list(body.padWant, 20)) {
        if (typeof uuid !== 'string' || !UUID_RE.test(uuid) || !onlineSet.has(uuid.toLowerCase())) continue;
        padData.push({ uuid: uuid.toLowerCase(), owned: (await ownedPieces(uuid)).map(({ set, id, name, slot, from }) => ({ set, id, name, slot, from })) });
      }

      // (TF Client 1.3.120) el servidor empezó limpio: lo de estos jugadores en la web, también (store.resetPlayer)
      let resetDone = null;
      const resetList = list(body.reset, 100).filter((u) => typeof u === 'string' && UUID_RE.test(u));
      if (resetList.length) {
        resetDone = 0;
        for (const uuid of resetList) {
          await store.resetPlayer(uuid);
          resetDone++;
        }
        log.warn(`Puente: reiniciados en la web ${resetDone} jugadores (el servidor empezó limpio)`);
      }

      const playerRanks = (await store.ranksFor(online)).map((r) => ({ uuid: r.uuid, ...rankInfo(rankById.get(r.rankId)) })).filter((r) => r.id);

      return json({
        deliveries,
        ...(resetDone !== null ? { resetDone } : {}),
        interval: BRIDGE_INTERVAL_S,
        ranks: playerRanks,
        rankList: ranks.map(rankSetup),
        linkResults,
        coinShop: (await store.coinShop()).map((it) => ({ id: it.id, name: it.name, count: it.count, price: it.price })),
        vfx: await store.vfxEquipFor(online),
        // (1.3.114) los efectos de kill se compran: el pad del juego enseña cuáles son de cada conectado
        vfxFree: VFX_FREE,
        vfxOwned: await vfxOwnedForServer(online),
        // Clase de skills activa de los conectados (null = ninguna); el mod solo aplica un «at» más nuevo que el último
        skills: await skillsForServer(online),
        // Armario de los conectados: «set/pieza» por hueco (el mod lo convierte en tfclient:set_pieza)
        wardrobe: await wardrobesForServer(online),
        padData,
        // (TF Client 1.3.142, el dueño: «quiero ver cada jugador que compró y todos sus objetos y dónde están») cuando el
        // mod lo pide: las compras pagadas (las 1.000 últimas) con lo que entrega cada una, para la app Rastreador
        ...(body.wantOrders === true ? { orders: await ordersForServer() } : {}),
        roulette: rouletteForServer(),
        store: (PUBLIC_URL || new URL(req.url).origin).replace(/^https?:\/\//, ''),
      });
    },
  };

  // Las compras pagadas para la app Rastreador del pad de administrador: quién, qué, cuándo y sus comandos (el mod saca de
  // ellos los objetos: «tf web sets give {player} <set> [pieza]» y «give {player} <objeto> [n]»).
  async function ordersForServer() {
    const rows = await store.recentOrders(1000);
    return rows.map((o) => {
      const product = productById.get(o.productId);
      return {
        id: o.id,
        player: o.username,
        uuid: o.uuid,
        status: o.status,
        refunded: o.refunded || null,
        product: product?.name || o.productId,
        productId: o.productId,
        quantity: o.quantity,
        paidAt: o.paidAt || o.createdAt,
        gift: o.gift ? { from: o.gift.fromName || null } : null,
        commands: product?.commands || [],
      };
    });
  }

  // Un pago de Stripe terminó (o caducó): valida lo cobrado contra el pedido y lo pone en la cola de entrega.
  async function handleSession(checkout, ctx, type) {
    const id = checkout.client_reference_id || checkout.metadata?.order_id;
    // (3 nov 2026) un pedido suelto o un carrito (CART…)
    const local = await loadPayable(id);
    if (!local) return null;
    if (settled(local)) return local.status; // Ya pagado y procesado.
    if (local.sessionId && checkout.id && checkout.id !== local.sessionId) {
      log.error(`El pago ${checkout.id} no es el del pedido ${id}`);
      return local.status;
    }

    if (type === 'checkout.session.expired' || checkout.status === 'expired') {
      if (local.status === 'pending') await setPay(local, { status: 'expired' });
      return 'expired';
    }
    if (type === 'checkout.session.async_payment_failed') {
      await setPay(local, { status: 'failed' });
      return 'failed';
    }
    if (checkout.status !== 'complete') return local.status;

    const paymentIntent = typeof checkout.payment_intent === 'string' ? checkout.payment_intent : checkout.payment_intent?.id;
    if (checkout.payment_status === 'unpaid') {
      // Pagos que tardan en confirmarse (transferencias, etc.): Stripe avisará con async_payment_succeeded.
      await setPay(local, { status: 'awaiting_payment', paymentIntent });
      return 'awaiting_payment';
    }
    if (checkout.payment_status !== 'paid') return local.status;

    const expected = toStripeAmount(local.amount, local.currency);
    if (checkout.amount_total !== expected || String(checkout.currency).toUpperCase() !== local.currency) {
      log.error(`Importe incorrecto en ${id}: esperado ${expected} ${local.currency}, cobrado ${checkout.amount_total} ${checkout.currency}`);
      await setPay(local, { status: 'error', error: 'Importe no coincide', paymentIntent });
      return 'error';
    }

    return complete(local, ctx, paymentIntent);
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
    if (settled(local)) return local.status; // Ya pagado y procesado.
    if (local.method !== 'paypal' || (capture.custom_id && capture.custom_id !== local.id)) {
      log.error(`El cobro de PayPal ${capture.id} no es del pedido ${local.id}`);
      return local.status;
    }
    if (capture.status === 'DECLINED' || capture.status === 'FAILED') {
      await setPay(local, { status: 'failed' });
      return 'failed';
    }
    if (capture.status === 'PENDING') {
      // PayPal lo está revisando: avisará con PAYMENT.CAPTURE.COMPLETED.
      await setPay(local, { status: 'awaiting_payment', paymentIntent: capture.id });
      return 'awaiting_payment';
    }
    if (capture.status !== 'COMPLETED') return local.status;
    const expected = formatAmount(local.amount, local.currency);
    if (capture.amount?.value !== expected || String(capture.amount?.currency_code).toUpperCase() !== local.currency) {
      log.error(`Importe incorrecto en ${local.id}: esperado ${expected} ${local.currency}, cobrado ${capture.amount?.value} ${capture.amount?.currency_code}`);
      await setPay(local, { status: 'error', error: 'Importe no coincide', paymentIntent: capture.id });
      return 'error';
    }
    return complete(local, ctx, capture.id);
  }

  /**
   * Mercado Pago: los pagos de un pedido (los de la API). Uno aprobado con el importe justo entrega; uno en proceso
   * (OXXO, revisión) deja el pedido esperando; reembolsado o contracargo retira lo entregado. Uno rechazado no cierra el
   * pedido (el comprador puede reintentar con otra tarjeta en la misma página).
   */
  async function handleMpPayments(local, payments, ctx) {
    if (local.method !== 'mercadopago') return local.status;
    const mine = payments.filter((p) => p && p.external_reference === local.id);
    for (const p of mine) {
      if (p.status === 'refunded' || p.status === 'charged_back') {
        await handleReversal({ paymentIntent: String(p.id), full: true, dispute: p.status === 'charged_back' }, ctx);
        return (await loadPayable(local.id))?.status;
      }
    }
    if (settled(local)) return local.status; // Ya pagado y procesado.
    const approved = mine.find((p) => p.status === 'approved');
    if (approved) {
      const paid = Math.round(Number(approved.transaction_amount) * 100);
      if (paid !== local.amount || String(approved.currency_id).toUpperCase() !== local.currency) {
        log.error(`Importe incorrecto en ${local.id}: esperado ${local.amount / 100} ${local.currency}, cobrado ${approved.transaction_amount} ${approved.currency_id}`);
        await setPay(local, { status: 'error', error: 'Importe no coincide', paymentIntent: String(approved.id) });
        return 'error';
      }
      return complete(local, ctx, String(approved.id));
    }
    const waiting = mine.find((p) => ['pending', 'in_process', 'authorized', 'in_mediation'].includes(p.status));
    if (waiting && local.status === 'pending') {
      await setPay(local, { status: 'awaiting_payment', paymentIntent: String(waiting.id) });
      return 'awaiting_payment';
    }
    return local.status;
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
  // quiet: un producto de un carrito (el anuncio de Discord y el recibo salen una vez para todo el carrito)
  async function fulfill(local, ctx, paymentIntent, { quiet = false } = {}) {
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
    // Regalo: el que lo recibe ve en el chat de quién es y su mensaje (texto JSON, sin nada que el jugador pueda colar)
    if (local.gift) {
      const parts = [
        { text: '¡Regalo! ', color: 'gold', bold: true },
        { text: `${local.gift.fromName} te ha regalado ${product.name}${local.quantity > 1 ? ` ×${local.quantity}` : ''}`, color: 'yellow' },
      ];
      if (local.gift.message) parts.push({ text: ` «${local.gift.message}»`, color: 'white', italic: true });
      commands.push(`tellraw {player} ${JSON.stringify(['', ...parts])}`);
    }
    // (TF Client 1.3.117) Al mejorar no hace falta quitar los rangos inferiores: «parent set» deja solo el nuevo (y el
    // TF Client no le quita al staff su grupo).
    const extra = {
      kind: product.category,
      color: rank?.rank.hex || product.colors?.[1] || '#f4c95d',
      rank: rankInfo(rank),
      upgradeFrom: local.rankFrom ? productById.get(local.rankFrom)?.name || null : null,
      prizes: prizes ? prizes.map((p) => p.name) : null,
      gift: local.gift ? { from: local.gift.fromName } : null,
    };

    const queued = await store.queueDelivery(id, { username: local.username, uuid: local.uuid, commands, extra, paymentIntent });
    if (!queued) return (await store.getOrder(id)).status;
    if (rank && local.uuid) await store.setRank(local.uuid, rank.id, rank.tier);
    // Clase de skills pagada: ya es suya y pasa a ser la activa (el puente la manda al servidor)
    const cls = skillOf(product);
    if (cls && local.uuid) await store.setSkillActive(local.uuid, cls.id);
    // Efecto de kill pagado: queda en su cuenta y se equipa (el puente lo manda al servidor)
    const fx = vfxOf(product);
    if (fx && local.uuid) {
      await store.vfxGive(local.uuid, fx.key);
      await store.vfxEquip(local.uuid, fx.kind, fx.id);
    }
    log.log(`Pedido ${id} ${product.price === 0 ? 'reclamado (gratis)' : 'pagado'}: ${product.name} x${local.quantity} para ${local.username} (${local.uuid}), en cola del puente`);

    // En Discord: rol y anuncio. Un fallo aquí no afecta a la entrega en el juego.
    await deliverDiscord(id, product, ctx.discord, { announce: !quiet });
    if (!quiet) await mailReceipt(id);
    return 'queued';
  }

  // Carrito pagado: cada producto, a la cola del puente como un pedido normal (con su rango, su rol de Discord…), y
  // luego un solo anuncio y un solo recibo. Se puede repetir sin entregar dos veces (cada pedido entra una vez en la
  // cola, y el carrito se marca pagado una vez).
  async function fulfillCart(cart, ctx, paymentIntent) {
    for (const child of await store.cartOrders(cart.id)) {
      if (CLAIMED.includes(child.status) || child.status === 'reiniciado') continue;
      await fulfill(child, ctx, paymentIntent, { quiet: true });
    }
    if (await store.markCartPaid(cart.id, paymentIntent)) {
      log.log(`Carrito ${cart.id} pagado: ${cart.items.length} productos, de ${cart.username}${cart.coupon ? ` (cupón ${cart.coupon})` : ''}`);
      await announceCart(cart.id, ctx.discord);
      await mailCartReceipt(cart.id);
    }
    return 'queued';
  }

  // Un solo anuncio en Discord con todo lo del carrito: lo de quien compra y cada regalo con su destinatario
  async function announceCart(cartId, discord) {
    try {
      const cart = await store.getCart(cartId);
      const children = await store.cartOrders(cartId);
      if (!cart || !children.length) return;
      const names = (list) => {
        const n = list.map((o) => `**${productById.get(o.productId)?.name || o.productId}**${o.quantity > 1 ? ` ×${o.quantity}` : ''}`);
        return n.length > 1 ? `${n.slice(0, -1).join(', ')} y ${n.at(-1)}` : n[0];
      };
      const mention = async (o) => {
        let linked = o.discord?.id ? o.discord : null;
        if (!linked && o.uuid) {
          const account = await store.accountByUuid(o.uuid);
          if (account) linked = { id: account.discordId };
        }
        return linked ? `<@${linked.id}> (**${o.username}**)` : `**${o.username}**`;
      };
      const parts = [];
      const own = children.filter((o) => !o.gift);
      if (own.length) parts.push(`${await mention(own[0])} ha conseguido ${names(own)}`);
      const byWho = new Map();
      for (const o of children.filter((x) => x.gift)) byWho.set(o.uuid || o.username, [...(byWho.get(o.uuid || o.username) || []), o]);
      for (const list of byWho.values()) parts.push(`**${list[0].gift.fromName}** le ha regalado ${names(list)} a ${await mention(list[0])}`);
      // El título: el rango más alto del carrito (y quién lo recibe), o regalo, o compra
      const rankOrders = children.filter((o) => rankById.has(o.productId));
      const top = rankOrders.sort((x, y) => rankById.get(y.productId).tier - rankById.get(x.productId).tier)[0];
      const rank = top ? rankById.get(top.productId) : null;
      const first = productById.get(children[0].productId);
      await discord.announce({
        embed: {
          title: rank ? `👑 ¡${top.username} ahora es ${rank.rank.prefix}!` : own.length ? '🎉 ¡Nueva compra en la tienda!' : '🎁 ¡Regalos en la tienda!',
          description: `${parts.join('. ')}. ¡Gracias por apoyar ${SERVER_NAME}!`,
          color: Number.parseInt((rank?.rank.hex || first?.colors?.[1] || '#f4c95d').slice(1), 16),
          thumbnail: { url: head(cart.username) },
          timestamp: new Date().toISOString(),
        },
      });
    } catch (err) {
      log.warn('No se pudo anunciar la compra del carrito en Discord:', err.message);
    }
  }

  // Reembolsos y disputas. Con el reembolso completo (o una disputa) se retira del servidor todo lo de ese pedido: el mod
  // quita los objetos marcados con el pedido estén donde estén (inventario, cofres, cofre de ender, suelo…), se quita
  // el grupo del rango (volviendo al anterior si fue una mejora) y sus roles de Discord. Un reembolso parcial solo se
  // apunta en el pedido.
  // La clave del panel de staff (ADMIN_KEY). Tras varios intentos fallidos desde una IP, esa IP espera unos minutos.
  async function staffDenied(req) {
    if (!env.ADMIN_KEY) return json({ error: 'El panel de staff no está configurado: falta el secreto ADMIN_KEY en Cloudflare.' }, 503);
    const key = `staff:${req.headers.get('CF-Connecting-IP') || 'local'}`;
    if (await store.loginLocked(key)) return json({ error: 'Demasiados intentos con una clave incorrecta. Espera unos minutos.' }, 429);
    if (!safeEqual(req.headers.get('Authorization') || '', `Bearer ${env.ADMIN_KEY}`)) {
      await store.loginFailed(key);
      return json({ error: 'Clave incorrecta.' }, 401);
    }
    await store.loginSucceeded(key);
    return null;
  }

  // El expediente de un pedido para responder una disputa (receipt.js): los hechos, con lo que hay en la web. Un
  // pedido de un carrito: el del carrito entero (el pago que se disputa es el del carrito).
  async function disputeCase(order, late) {
    if (order.cartId) {
      const cart = await store.getCart(order.cartId);
      if (cart) return cartDisputeCase(cart, late);
    }
    const acceptance = await store.termsForOrder(order.id);
    const payerUuid = order.gift?.fromUuid || order.uuid;
    const player = order.uuid ? await store.playerByUuid(order.uuid) : null;
    const delivery = await store.deliveryOf(order.id);
    const history = payerUuid ? (await store.ordersFor({ uuid: payerUuid })).map((o) => ({ ...o, productName: productById.get(o.productId)?.name })) : [];
    const facts = disputeFacts({ order, productName: orderView(order).product, acceptance, player, delivery, history });
    if (late !== undefined) facts.inWindow = !late;
    return facts;
  }

  async function cartDisputeCase(cart, late) {
    const children = await store.cartOrders(cart.id);
    const order = cartAsOrder(cart, children);
    const acceptance = await store.termsForOrder(cart.id);
    const payerUuid = cart.payer || cart.gift?.fromUuid || cart.uuid;
    const player = payerUuid ? await store.playerByUuid(payerUuid) : null;
    const delivery = children.length ? await store.deliveryOf(children[0].id) : null;
    const history = payerUuid
      ? (await store.ordersFor({ uuid: payerUuid })).filter((o) => o.cartId !== cart.id).map((o) => ({ ...o, productName: productById.get(o.productId)?.name }))
      : [];
    const facts = disputeFacts({ order, productName: cartProducts(children), acceptance, player, delivery, history });
    if (late !== undefined) facts.inWindow = !late;
    return facts;
  }

  // (3 nov 2026) Reembolso o disputa del pago de un carrito. Completo (o disputa): cada producto se retira del servidor
  // como un pedido reembolsado. Parcial: solo se apunta en el carrito (el staff retira desde /staff los productos que
  // devolvió). La sanción por un reembolso injustificado y el correo al dueño salen una vez para todo el carrito.
  async function cartReversal(cartId, { full, dispute, disputeId }, ctx) {
    const cart = await store.getCart(cartId);
    if (!cart) return;
    const what = dispute ? 'disputa' : full ? 'reembolsado' : 'reembolso parcial';
    log.warn(`Carrito ${cart.id} (${cart.username}): ${what}`);
    if (!keepsLabel(cart.refunded)) await store.updateCart(cart.id, { refunded: what });
    const children = await store.cartOrders(cart.id);
    const delivered = children.filter((o) => CLAIMED.includes(o.status));
    if (full) {
      // Al revés de como se entregó: con dos rangos del mismo jugador, primero se quita el más alto (y vuelve al de antes)
      for (const child of [...children].reverse()) {
        if (!keepsLabel(child.refunded)) await store.updateOrder(child.id, { refunded: what });
        if (CLAIMED.includes(child.status)) await revoke(child, ctx);
      }
    }

    const asOrder = cartAsOrder(cart, children);
    const policy = refundPolicyFor(asOrder);
    const since = asOrder.deliveredAt || asOrder.paidAt || asOrder.createdAt;
    const hours = Math.max(0, Math.floor((Date.now() - Date.parse(since)) / 3600000));
    const days = businessDaysSince(since);
    const late = policy.id === '24h' ? hours >= 24 : days > 5;
    const elapsed = policy.id === '24h' ? `${hours} horas` : `${days} días hábiles`;
    // Quien paga (y reclama el dinero): la sanción es suya, no de quien recibió los regalos
    const payerUuid = String(cart.payer || cart.gift?.fromUuid || cart.uuid || '').toLowerCase();
    const payer = (payerUuid && (await store.playerByUuid(payerUuid))?.name) || cart.gift?.fromName || cart.username;
    let penalty = false;
    if (dispute && late && delivered.length) {
      // La sanción va en la entrega de uno de sus pedidos (el puente solo manda entregas de pedidos que existen): si
      // hay uno suyo, con {player} (su nombre de ahora); si no, con su nombre
      const own = delivered.find((o) => o.uuid && o.uuid.toLowerCase() === payerUuid);
      penalty = await store.queuePenalty(own || delivered[0], [
        `tempbanip ${own ? '{player}' : payer} ${policy.ban} Reembolso injustificado del pedido ${cart.id} (Términos de ${SERVER_NAME}: sin reembolsos pasadas ${policy.windowText} desde la entrega)`,
      ]);
      if (penalty) {
        const label = `${what} fuera de plazo · baneo de IP de ${policy.banText}`;
        await store.updateCart(cart.id, { refunded: label });
        for (const child of children) await store.updateOrder(child.id, { refunded: label });
        log.warn(`Carrito ${cart.id}: baneo de IP de ${policy.banText} en cola para ${payer}`);
      }
    }

    let facts = null;
    if (dispute && cart.amount > 0) {
      try {
        facts = await cartDisputeCase(cart, late);
        if (disputeId && stripe && cart.method !== 'paypal') {
          await stripe.updateDisputeEvidence(disputeId, stripeEvidence(facts));
          log.log(`Carrito ${cart.id}: pruebas de la disputa ${disputeId} rellenadas en Stripe (sin enviar)`);
        }
      } catch (err) {
        log.error(`Carrito ${cart.id}: no se pudieron preparar las pruebas de la disputa:`, err);
      }
    }

    if (mailer.enabled) {
      const { pdf, rows } = await cartReceiptFor((await store.getCart(cart.id)) || cart, true);
      const verdict = dispute
        ? late
          ? `Disputa abierta ${elapsed} después de la entrega (fuera del plazo de ${policy.windowText}): reembolso injustificado.${penalty ? ` Se puso en cola el baneo de IP de ${policy.banText} para ${payer}.` : ''}`
          : `Disputa abierta ${elapsed} después de la entrega (dentro del plazo de ${policy.windowText}): según los Términos se trata como una cancelación.`
        : `${full ? 'Reembolso completo' : 'Reembolso parcial'} hecho desde ${cart.method === 'paypal' ? 'PayPal' : cart.method === 'mercadopago' ? 'Mercado Pago' : 'Stripe'}, ${elapsed} después de la entrega.`;
      const after = full
        ? ` Se retira del servidor todo lo del carrito (${children.length} productos).`
        : ' Es parcial: no se retiró nada; si devolviste algún producto, retíralo en el panel de staff con «Reembolsar».';
      const intro = `${what[0].toUpperCase()}${what.slice(1)} del carrito ${cart.id} (${cart.username}). ${verdict}${after}`;
      const attachments = [{ filename: `recibo-${cart.id}.pdf`, content: toBase64(pdf) }];
      if (facts) attachments.push({ filename: `disputa-${cart.id}.pdf`, content: toBase64(disputePdf(facts)) });
      await mailer.send({
        subject: `${dispute ? 'DISPUTA' : 'Reembolso'} · pedido ${cart.id} · ${cart.username}`,
        text: `${intro}\n\n${fieldsText(rows)}\n\n${policy.lines.join('\n')}`,
        html: mailHtml(dispute ? 'Disputa de un pago' : 'Reembolso de un pago', intro, rows, policy.lines.join(' ')),
        attachments,
      });
    }
  }

  // Un aviso repetido (el mismo reembolso o la misma disputa) no le quita a la etiqueta lo que ya dice: que se retiró del
  // servidor o que fue fuera de plazo con su baneo
  const keepsLabel = (label) => Boolean(label && /retirado|fuera de plazo/.test(label));

  async function handleReversal({ paymentIntent, full, dispute, order: given = null, manual = false, disputeId = null }, ctx) {
    const order = given || (paymentIntent ? await store.findOrderByPaymentIntent(paymentIntent) : null);
    // (3 nov 2026) el pago de un carrito: se trata entero (si lo retira el staff a mano, solo ese producto)
    if (!given && order?.cartId) return cartReversal(order.cartId, { full, dispute, disputeId }, ctx);
    if (!order) {
      const cart = paymentIntent ? await store.findCartByPaymentIntent(paymentIntent) : null;
      if (cart) await cartReversal(cart.id, { full, dispute, disputeId }, ctx);
      return;
    }
    const what = dispute ? 'disputa' : full ? 'reembolsado' : 'reembolso parcial';
    log.warn(`Pedido ${order.id} (${order.username}): ${what}`);
    if (!keepsLabel(order.refunded)) await store.updateOrder(order.id, { refunded: what });
    if (full && CLAIMED.includes(order.status)) await revoke(order, ctx, { manual });

    // Reembolso injustificado (Términos, «Cancelaciones y reembolsos»): una disputa o contracargo pasado el plazo de los
    // Términos que aceptó (24 horas o, antes del 30 oct 2026, 5 días hábiles desde la entrega) → baneo de IP del
    // servidor (15 o 6 días). Los reembolsos que hace el dueño no se castigan.
    const policy = refundPolicyFor(order);
    const since = order.deliveredAt || order.paidAt || order.createdAt;
    const hours = Math.max(0, Math.floor((Date.now() - Date.parse(since)) / 3600000));
    const days = businessDaysSince(since);
    const late = policy.id === '24h' ? hours >= 24 : days > 5;
    const elapsed = policy.id === '24h' ? `${hours} horas` : `${days} días hábiles`;
    let penalty = false;
    // En un regalo la sanción es para quien pagó (y reclamó el dinero), no para el que lo recibió
    const payer = order.gift ? (await store.playerByUuid(order.gift.fromUuid))?.name || order.gift.fromName : order.username;
    if (dispute && late && CLAIMED.includes(order.status)) {
      penalty = await store.queuePenalty(order, [
        `tempbanip ${order.gift ? payer : '{player}'} ${policy.ban} Reembolso injustificado del pedido ${order.id} (Términos de ${SERVER_NAME}: sin reembolsos pasadas ${policy.windowText} desde la entrega)`,
      ]);
      if (penalty) {
        await store.updateOrder(order.id, { refunded: `${what} fuera de plazo · baneo de IP de ${policy.banText}` });
        log.warn(`Pedido ${order.id}: baneo de IP de ${policy.banText} en cola para ${payer}`);
      }
    }

    // (30 oct 2026) Disputa: el expediente con las pruebas. En Stripe se dejan rellenadas las pruebas de la disputa (sin
    // enviar: el dueño las revisa, adjunta el PDF y pulsa «Enviar»); en PayPal se pega la respuesta y se adjunta el PDF.
    let facts = null;
    if (dispute && order.amount > 0) {
      try {
        facts = await disputeCase(order, late);
        if (disputeId && stripe && order.method !== 'paypal') {
          await stripe.updateDisputeEvidence(disputeId, stripeEvidence(facts));
          log.log(`Pedido ${order.id}: pruebas de la disputa ${disputeId} rellenadas en Stripe (sin enviar)`);
        }
      } catch (err) {
        log.error(`Pedido ${order.id}: no se pudieron preparar las pruebas de la disputa:`, err);
      }
    }

    // Aviso para el dueño, con todo lo que sirve de prueba (el recibo y, en una disputa, el expediente)
    if (mailer.enabled) {
      const fresh = (await store.getOrder(order.id)) || order;
      const { pdf, rows } = await receiptFor(fresh, true);
      const verdict = dispute
        ? late
          ? `Disputa abierta ${elapsed} después de la entrega (fuera del plazo de ${policy.windowText}): reembolso injustificado.${penalty ? ` Se puso en cola el baneo de IP de ${policy.banText} para ${payer}.` : ''}`
          : `Disputa abierta ${elapsed} después de la entrega (dentro del plazo de ${policy.windowText}): según los Términos se trata como una cancelación.`
        : manual
          ? `Reembolso hecho a mano desde el panel de staff, ${elapsed} después de la entrega.`
          : `${full ? 'Reembolso completo' : 'Reembolso parcial'} hecho desde ${order.method === 'paypal' ? 'PayPal' : 'Stripe'}, ${elapsed} después de la entrega.`;
      const how = dispute
        ? order.method === 'paypal'
          ? ' Responde en el Centro de resoluciones de PayPal antes de que venza el plazo: pega la respuesta en inglés del expediente adjunto y sube el expediente y el recibo. Antes, comprueba que el comprador no te escribió pidiendo el reembolso.'
          : ' En Stripe las pruebas ya están rellenadas (sin enviar): revísalas, adjunta el expediente y el recibo y pulsa «Enviar pruebas». Antes, comprueba que el comprador no te escribió pidiendo el reembolso.'
        : '';
      const intro = `${what[0].toUpperCase()}${what.slice(1)} del pedido ${order.id} (${order.username}). ${verdict} Lo entregado se retira del servidor.${how}`;
      const attachments = [{ filename: `recibo-${order.id}.pdf`, content: toBase64(pdf) }];
      if (facts) attachments.push({ filename: `disputa-${order.id}.pdf`, content: toBase64(disputePdf(facts)) });
      await mailer.send({
        subject: `${dispute ? 'DISPUTA' : 'Reembolso'} · pedido ${order.id} · ${order.username}`,
        text: `${intro}\n\n${fieldsText(rows)}\n\n${policy.lines.join('\n')}`,
        html: mailHtml(dispute ? 'Disputa de un pago' : 'Reembolso de un pago', intro, rows, policy.lines.join(' ')),
        attachments,
      });
    }
  }

  // Lo que hay que deshacer de un pedido: los sets que dio y el grupo de su rango (si sigue siendo su rango actual).
  // Desde el panel de staff (manual) se quitan el grupo y el rol de ese rango aunque ya no sea el actual: el staff
  // retira ese pedido entero (p. ej. rangos reclamados gratis en una prueba, que no guardan el rango anterior).
  async function revoke(order, ctx, { manual = false } = {}) {
    const product = productById.get(order.productId);
    if (!product) return;
    // Clase de skills reembolsada: deja de ser suya; si era la activa, se quita también en el servidor
    const cls = skillOf(product);
    if (cls && order.uuid) {
      const active = await store.skillActive(order.uuid);
      if (active?.cls === cls.id && !(await store.skillsOwned(order.uuid)).includes(cls.id)) {
        await store.setSkillActive(order.uuid, null);
        log.warn(`Pedido ${order.id}: clase ${cls.id} retirada de ${order.username}`);
      }
      return;
    }
    // Efecto de kill reembolsado: deja de ser suyo (y se quita si lo llevaba)
    const fx = vfxOf(product);
    if (fx && order.uuid) {
      await store.vfxTake(order.uuid, fx.key);
      const mine = await store.vfxFor(order.uuid);
      if (mine.kill === fx.id) await store.vfxEquip(order.uuid, 'kill', null);
      log.warn(`Pedido ${order.id}: efecto ${fx.id} retirado de ${order.username}`);
      return;
    }
    const rank = rankById.get(product.id);
    const current = rank && order.uuid ? await store.getRank(order.uuid) : null;
    const rankStillCurrent = Boolean(rank && current?.rankId === rank.id);
    const from = order.rankFrom ? rankById.get(order.rankFrom) : null;
    const strip = !rank || rankStillCurrent || manual;
    const commands = [];
    for (const command of product.commands || []) {
      const set = command.match(/^tf web sets give \{player\} (\S+)(?: (\S+))?$/);
      if (set) commands.push(`tf web sets revoke {uuid} ${order.id} ${set[1]}${set[2] ? ` ${set[2]}` : ''}`);
      const group = command.match(/^lp user \{player\} parent (?:add|set) (\S+)$/);
      if (group && strip) commands.push(`lp user {uuid} parent remove ${group[1]}`);
      // TF Client 1.3.125: las monedas que dio la compra se retiran (si ya las gastó, saldo negativo que paga con lo
      // que gane: TFDebt en el mod)
      const coins = command.match(/^tf web (monedas|verdes) dar \{player\} (\d+)$/);
      if (coins) commands.push(`tf web ${coins[1]} retirar {uuid} ${coins[2]}`);
    }
    if (rankStillCurrent && from) commands.push(`lp user {uuid} parent add ${from.rank.group}`);
    const queued = commands.length ? await store.queueRevocation(order, [...new Set(commands)]) : false;
    if (rankStillCurrent) await store.setRank(order.uuid, from?.id || null, from?.tier, { force: true });
    if (queued) log.warn(`Pedido ${order.id}: retirada del servidor en cola (${commands.length} comandos)`);

    // Roles de Discord del producto (y los del rango anterior, si era una mejora)
    const account = order.discordId ? { discordId: order.discordId } : order.uuid ? await store.accountByUuid(order.uuid) : null;
    const discord = ctx?.discord;
    if (queued && account?.discordId && discord?.rolesEnabled && strip) {
      try {
        await discord.removeRoles({ userId: account.discordId, roleIds: await productRoles(product, discord), reason: `Reembolso del pedido ${order.id}` });
        if (from && rankStillCurrent) await discord.grantRoles({ userId: account.discordId, roleIds: await productRoles(from, discord), reason: `Reembolso del pedido ${order.id}` });
      } catch (err) {
        log.error(`No se pudieron quitar los roles de Discord del pedido ${order.id}:`, err.message);
      }
    }
  }

  async function deliverDiscord(orderId, product, discord, { announce = true } = {}) {
    const order = await store.getOrder(orderId);
    // Si compró sin iniciar sesión, usamos la cuenta de Discord vinculada a su jugador.
    let linked = order.discord;
    if (!linked && order.uuid) {
      const account = await store.accountByUuid(order.uuid);
      if (account) linked = { id: account.discordId, username: account.username };
    }
    const roles = linked && discord.rolesEnabled && hasRoles(product) ? await productRoles(product, discord) : [];

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

    if (!announce) return;
    try {
      const who = linked ? `<@${linked.id}> (**${order.username}**)` : `**${order.username}**`;
      const rank = rankById.get(product.id);
      const upgrade = order.rankFrom ? productById.get(order.rankFrom)?.name : null;
      await discord.announce({
        embed: {
          title: rank ? `👑 ¡${order.username} ahora es ${rank.rank.prefix}!` : order.gift ? '🎁 ¡Un regalo en la tienda!' : '🎉 ¡Nueva compra en la tienda!',
          description: `${order.gift ? `**${order.gift.fromName}** le ha regalado a ${who}` : `${who} ha conseguido`} **${product.name}**${order.quantity > 1 ? ` ×${order.quantity}` : ''}${upgrade ? ` (mejora desde ${upgrade})` : ''}${
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
