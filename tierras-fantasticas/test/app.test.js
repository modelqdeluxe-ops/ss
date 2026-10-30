import { test, before, after } from 'node:test';
import assert from 'node:assert';
import http from 'node:http';
import { createHmac } from 'node:crypto';
import products from '../config/products.json' with { type: 'json' };
import skillsCatalog from '../config/skills.json' with { type: 'json' };
import './retired.js';
import worker from '../src/index.js';
import { createD1 } from './d1.js';

const servers = [];
let env;
// Correos que la web ha mandado (Resend simulado): firmas, recibos y reembolsos
const mails = [];
// Lo que el servidor de Minecraft (puente) ha ejecutado.
const received = [];
const BRIDGE_SECRET = 'clave-del-puente';

// --- API de Stripe simulada ---
const stripeSessions = new Map();
const disputeEvidence = new Map();
let nextSession = 1;
const WEBHOOK_SECRET = 'whsec_prueba';

function startFakeStripe() {
  const server = http.createServer(async (req, res) => {
    let raw = '';
    for await (const chunk of req) raw += chunk;
    const send = (status, data) => {
      res.writeHead(status, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(data));
    };
    if (req.headers.authorization !== 'Bearer sk_test_tienda') return send(401, { error: { message: 'Invalid API Key' } });

    if (req.url === '/v1/checkout/sessions' && req.method === 'POST') {
      const form = new URLSearchParams(raw);
      const id = `cs_test_${nextSession++}`;
      const unit = Number(form.get('line_items[0][price_data][unit_amount]'));
      const quantity = Number(form.get('line_items[0][quantity]'));
      const session = {
        id,
        object: 'checkout.session',
        url: `https://checkout.stripe.test/${id}`,
        status: 'open',
        payment_status: 'unpaid',
        payment_intent: null,
        client_reference_id: form.get('client_reference_id'),
        amount_total: unit * quantity,
        currency: form.get('line_items[0][price_data][currency]'),
        metadata: { order_id: form.get('metadata[order_id]'), uuid: form.get('metadata[uuid]') },
        form: Object.fromEntries(form),
        idempotencyKey: req.headers['idempotency-key'],
      };
      stripeSessions.set(id, session);
      return send(200, session);
    }
    const dp = req.url.match(/^\/v1\/disputes\/(\w+)$/);
    if (dp && req.method === 'POST') {
      disputeEvidence.set(dp[1], Object.fromEntries(new URLSearchParams(raw)));
      return send(200, { id: dp[1], object: 'dispute' });
    }
    const one = req.url.match(/^\/v1\/checkout\/sessions\/(\w+)$/);
    if (one && req.method === 'GET') {
      const session = stripeSessions.get(one[1]);
      return session ? send(200, session) : send(404, { error: { message: 'No such checkout.session' } });
    }
    send(404, { error: { message: 'Unknown' } });
  });
  servers.push(server);
  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve(server.address().port)));
}

// Firma un evento como lo hace Stripe (cabecera Stripe-Signature).
function signedEvent(event, { secret = WEBHOOK_SECRET, at = Math.floor(Date.now() / 1000) } = {}) {
  const payload = JSON.stringify(event);
  const sig = createHmac('sha256', secret).update(`${at}.${payload}`).digest('hex');
  return call('/webhook/stripe', { method: 'POST', headers: { 'Stripe-Signature': `t=${at},v1=${sig}` }, body: payload });
}

const sessionOf = (orderId) => [...stripeSessions.values()].find((s) => s.client_reference_id === orderId);

// El comprador paga en Stripe y Stripe avisa con el webhook.
async function pay(orderId, changes = {}, type = 'checkout.session.completed') {
  const session = sessionOf(orderId);
  Object.assign(session, { status: 'complete', payment_status: 'paid', payment_intent: `pi_${session.id}` }, changes);
  const res = await signedEvent({ id: `evt_${session.id}`, type, data: { object: session } });
  assert.strictEqual(res.status, 200);
  return (await get(`/api/order/${orderId}`)).json();
}

// --- API de Discord simulada ---
const discordCalls = [];
const announcements = [];
// ¿Está el usuario de Discord en el servidor de Discord de Tierras Fantásticas?
let memberExists = true;
let failRoles = false;
// El usuario que devuelve Discord al iniciar sesión.
let discordProfile = { id: '111111111111111111', username: 'alex', global_name: 'Alex', avatar: null };

function startFakeDiscord() {
  const server = http.createServer(async (req, res) => {
    let raw = '';
    for await (const chunk of req) raw += chunk;
    const send = (status, data) => {
      res.writeHead(status, { 'Content-Type': 'application/json' });
      res.end(data === undefined ? '' : JSON.stringify(data));
    };
    discordCalls.push({ method: req.method, url: req.url, auth: req.headers.authorization, body: raw });

    if (req.url === '/oauth2/token') {
      const form = new URLSearchParams(raw);
      if (form.get('code') !== 'good-code') return send(400, { error: 'invalid_grant' });
      return send(200, { access_token: 'user-token', expires_in: 604800 });
    }
    if (req.url === '/users/@me') return send(200, discordProfile);
    if (/^\/users\/@me\/guilds\/\d+\/member$/.test(req.url)) {
      if (req.headers.authorization !== 'Bearer user-token') return send(401, { message: '401: Unauthorized' });
      return memberExists ? send(200, { roles: [] }) : send(404, { message: 'Unknown Guild' });
    }
    if (req.url === '/webhook') {
      announcements.push(JSON.parse(raw));
      return send(204);
    }
    if (/^\/guilds\/\d+\/widget\.json$/.test(req.url)) return send(200, { instant_invite: 'https://discord.com/invite/tfWidget' });
    if (req.headers.authorization !== 'Bot bot-token') return send(401, { message: '401: Unauthorized' });
    if (/^\/guilds\/\d+\/members\/\d+$/.test(req.url) && req.method === 'PUT') {
      if (memberExists) return send(204);
      memberExists = true;
      return send(201, { user: {} });
    }
    if (/^\/guilds\/\d+\/members\/\d+$/.test(req.url) && req.method === 'GET') {
      return memberExists ? send(200, { roles: [] }) : send(404, { message: 'Unknown Member' });
    }
    if (/^\/guilds\/\d+\/members\/\d+\/roles\/\d+$/.test(req.url) && req.method === 'PUT') {
      return failRoles ? send(403, { message: 'Missing Permissions' }) : send(204);
    }
    send(404, { message: 'Unknown' });
  });
  servers.push(server);
  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve(server.address().port)));
}

// --- API de Mercado Pago simulada (preferencias, pagos y el cambio del dólar) ---
const mpPrefs = new Map();
const mpPayments = new Map();
function startFakeMercadoPago() {
  const server = http.createServer(async (req, res) => {
    let raw = '';
    for await (const chunk of req) raw += chunk;
    const send = (status, data) => {
      res.writeHead(status, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(data));
    };
    if (req.headers.authorization !== 'Bearer APP_USR-prueba') return send(401, { message: 'invalid token' });
    const url = new URL(req.url, 'http://x');
    if (url.pathname === '/currency_conversions/search') return send(200, { ratio: 18.68 });
    if (url.pathname === '/checkout/preferences' && req.method === 'POST') {
      const body = JSON.parse(raw);
      const id = `pref-${mpPrefs.size + 1}`;
      mpPrefs.set(id, body);
      return send(201, { id, init_point: `https://www.mercadopago.test/checkout?pref_id=${id}` });
    }
    if (url.pathname === '/v1/payments/search') {
      const ref = url.searchParams.get('external_reference');
      return send(200, { results: [...mpPayments.values()].filter((p) => p.external_reference === ref).reverse() });
    }
    const one = url.pathname.match(/^\/v1\/payments\/(\d+)$/);
    if (one) return mpPayments.has(one[1]) ? send(200, mpPayments.get(one[1])) : send(404, { message: 'not found' });
    send(404, { message: 'Unknown' });
  });
  servers.push(server);
  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve(server.address().port)));
}

// --- API de PayPal simulada ---
const paypalOrders = new Map();
const paypalCaptures = new Map();
let nextPaypal = 1;

function startFakePaypal() {
  const server = http.createServer(async (req, res) => {
    let raw = '';
    for await (const chunk of req) raw += chunk;
    const send = (status, data) => {
      res.writeHead(status, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(data));
    };
    if (req.url === '/v1/oauth2/token') {
      const ok = req.headers.authorization === `Basic ${btoa('pp-client:pp-secret')}`;
      return ok ? send(200, { access_token: 'pp-token', expires_in: 3600 }) : send(401, { error_description: 'bad' });
    }
    if (req.headers.authorization !== 'Bearer pp-token') return send(401, { name: 'AUTHENTICATION_FAILURE' });
    const body = raw ? JSON.parse(raw) : {};
    if (req.url === '/v2/checkout/orders' && req.method === 'POST') {
      const id = `PPORDER${nextPaypal++}`;
      const order = { id, status: 'CREATED', approved: false, purchase_units: body.purchase_units, context: body.application_context,
        links: [{ rel: 'approve', href: `https://www.paypal.test/checkoutnow?token=${id}` }] };
      paypalOrders.set(id, order);
      return send(201, order);
    }
    const capture = req.url.match(/^\/v2\/checkout\/orders\/(\w+)\/capture$/);
    if (capture) {
      const order = paypalOrders.get(capture[1]);
      if (!order) return send(404, { name: 'RESOURCE_NOT_FOUND' });
      if (order.status === 'COMPLETED') return send(422, { details: [{ issue: 'ORDER_ALREADY_CAPTURED' }] });
      if (!order.approved) return send(422, { details: [{ issue: 'ORDER_NOT_APPROVED' }] });
      const unit = order.purchase_units[0];
      const cap = { id: `CAP${order.id}`, status: 'COMPLETED', amount: unit.amount, custom_id: unit.custom_id };
      paypalCaptures.set(cap.id, cap);
      order.status = 'COMPLETED';
      unit.payments = { captures: [cap] };
      return send(201, order);
    }
    const one = req.url.match(/^\/v2\/checkout\/orders\/(\w+)$/);
    if (one) return paypalOrders.has(one[1]) ? send(200, paypalOrders.get(one[1])) : send(404, { name: 'RESOURCE_NOT_FOUND' });
    const cap = req.url.match(/^\/v2\/payments\/captures\/(\w+)$/);
    if (cap) return paypalCaptures.has(cap[1]) ? send(200, paypalCaptures.get(cap[1])) : send(404, { name: 'RESOURCE_NOT_FOUND' });
    if (req.url === '/v1/notifications/verify-webhook-signature') {
      return send(200, { verification_status: body.transmission_sig === 'firma-buena' && body.webhook_id === 'WH-1' ? 'SUCCESS' : 'FAILURE' });
    }
    send(404, { name: 'NOT_FOUND' });
  });
  servers.push(server);
  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve(server.address().port)));
}

const paypalWebhook = (event, sig = 'firma-buena') =>
  call('/webhook/paypal', {
    method: 'POST',
    headers: { 'PayPal-Transmission-Sig': sig, 'PayPal-Transmission-Id': 't1', 'PayPal-Auth-Algo': 'SHA256withRSA' },
    body: JSON.stringify(event),
  });

const ROLE_ID = '222222222222222222';
const quietLog = { log() {}, warn() {}, error() {} };

// Archivos estáticos simulados (en Cloudflare los sirve el propio Workers).
const ASSETS = { fetch: async (req) => new Response(`estático ${new URL(req.url).pathname}`) };

before(async () => {
  const [stripePort, discordPort, paypalPort, mpPort] = await Promise.all([startFakeStripe(), startFakeDiscord(), startFakePaypal(), startFakeMercadoPago()]);
  // Rol de Discord real (formato snowflake) para el rango de pruebas.
  products.find((p) => p.id === 'rango-inmortal').discordRoles = [ROLE_ID];
  env = {
    DB: createD1(),
    ASSETS,
    LOGGER: quietLog,
    STRIPE_SECRET_KEY: 'sk_test_tienda',
    STRIPE_WEBHOOK_SECRET: WEBHOOK_SECRET,
    STRIPE_API_BASE: `http://127.0.0.1:${stripePort}`,
    CURRENCY: 'USD',
    PUBLIC_URL: 'http://tienda.test',
    DISCORD_CLIENT_ID: 'discord-client',
    DISCORD_CLIENT_SECRET: 'discord-secret',
    DISCORD_BOT_TOKEN: 'bot-token',
    DISCORD_GUILD_ID: '333333333333333333',
    DISCORD_API_BASE: `http://127.0.0.1:${discordPort}`,
    DISCORD_WEBHOOK_URL: `http://127.0.0.1:${discordPort}/webhook`,
    BRIDGE_SECRET,
    ADMIN_KEY: 'clave-del-staff',
    PAYPAL_CLIENT_ID: 'pp-client',
    PAYPAL_CLIENT_SECRET: 'pp-secret',
    PAYPAL_API_BASE: `http://127.0.0.1:${paypalPort}`,
    PAYPAL_WEBHOOK_ID: 'WH-1',
    MP_ACCESS_TOKEN: 'APP_USR-prueba',
    MP_API_BASE: `http://127.0.0.1:${mpPort}`,
    MP_RATE_API_BASE: `http://127.0.0.1:${mpPort}`,
    MP_WEBHOOK_SECRET: 'mp-secreto',
    RESEND_API_KEY: 're_prueba',
    RECORDS_EMAIL: 'duenio@correo.test',
    MAIL_FETCH: async (url, init) => {
      assert.strictEqual(url, 'https://api.resend.com/emails');
      assert.strictEqual(init.headers.Authorization, 'Bearer re_prueba');
      mails.push(JSON.parse(init.body));
      return new Response('{"id":"m"}', { status: 200 });
    },
  };
});

after(() => servers.forEach((s) => s.close()));

const call = (path, init = {}) => worker.fetch(new Request(`http://tienda.test${path}`, { redirect: 'manual', ...init }), env);
const get = (path, headers = {}) => call(path, { headers });
const post = (path, body, cookie) =>
  call(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(cookie ? { Cookie: cookie } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });


// UUID estable para cada nombre de prueba (como los que manda el servidor).
const uuidOf = (name) => {
  const hex = createHmac('sha256', 'uuid').update(name.toLowerCase()).digest('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20, 32)}`;
};

// El mod en el servidor: manda los jugadores conectados, los vistos y las entregas hechas; recibe las nuevas.
async function poll(players, done = [], { secret = BRIDGE_SECRET, ...extra } = {}) {
  return call('/bridge/poll', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${secret}` },
    body: JSON.stringify({ protocol: 2, players: players.map((name) => ({ name, uuid: uuidOf(name) })), max: 50, done, ...extra }),
  });
}

// El servidor conoce a estos jugadores (entraron alguna vez) aunque no estén conectados.
const seen = (...names) => poll([], [], { seen: names.map((name) => ({ name, uuid: uuidOf(name), at: Date.now() - 1000 })) });

// Simula al servidor: conecta a los jugadores, ejecuta lo recibido (con {player}/{uuid}) y lo confirma.
async function serverDelivers(...players) {
  const { deliveries } = await (await poll(players)).json();
  for (const d of deliveries) received.push(...d.commands.map((c) => c.replaceAll('{player}', d.player).replaceAll('{uuid}', d.uuid)));
  await poll(players, deliveries.map((d) => ({ id: d.id, ok: true })));
  return deliveries;
}

// Los comandos de un producto para un jugador (crates y rangos traen también sus recursos, tools/crates.py)
const cmdsOf = (id, player) => products.find((p) => p.id === id).commands.map((c) => c.replaceAll('{player}', player));

async function checkout(body, cookie) {
  const res = await post('/api/checkout', { acceptTerms: true, ...body }, cookie);
  const data = await res.json();
  assert.strictEqual(res.status, 200, data.error);
  assert.match(data.url, /^https:\/\/checkout\.stripe\.test\//);
  return data.id;
}

test('la API de productos no expone los comandos y la configuración no tiene secretos', async () => {
  const list = await (await get('/api/products')).json();
  assert.ok(list.length > 0);
  assert.ok(list.every((p) => !('commands' in p) && !('discordRoles' in p)));
  const config = await (await get('/api/config')).json();
  assert.strictEqual(config.paymentsEnabled, true);
  assert.ok(!JSON.stringify(config).includes('sk_test') && !JSON.stringify(config).includes('whsec'));
});

test('las rutas que no son de la API las sirve la web estática', async () => {
  assert.strictEqual(await (await get('/tienda')).text(), 'estático /tienda');
});

test('/discord lleva al servidor con la invitación del widget (sin «X te ha invitado») y /whatsapp al grupo', async () => {
  const dc = await get('/discord');
  assert.strictEqual(dc.status, 302);
  assert.strictEqual(dc.headers.get('location'), 'https://discord.gg/tfWidget');
  const wa = await get('/whatsapp');
  assert.strictEqual(wa.status, 302);
  assert.match(wa.headers.get('location'), /^https:\/\/chat\.whatsapp\.com\//);
});

test('/descargar sirve el jar del mod (y /packs los packs cifrados) desde la web, sigue la redirección de GitHub y avisa si falla', async () => {
  const jar = Buffer.from('PK\u0003\u0004 jar de prueba');
  let up = true;
  const server = http.createServer((req, res) => {
    if (!up) return res.writeHead(503).end();
    if (req.url === '/tfclient.jar') return res.writeHead(302, { Location: '/blob/tfclient.jar' }).end();
    if (req.url === '/blob/tfclient.jar') return res.writeHead(200, { 'Content-Type': 'application/octet-stream', 'Content-Length': jar.length }).end(jar);
    res.writeHead(404).end();
  });
  servers.push(server);
  const port = await new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve(server.address().port)));
  const e = { ...env, CLIENT_JAR_URL: `http://127.0.0.1:${port}/tfclient.jar`, PACKS_URL: `http://127.0.0.1:${port}/blob/tfclient.jar` };
  for (const path of ['/descargar', '/tfclient.jar']) {
    const res = await worker.fetch(new Request(`http://tienda.test${path}`), e);
    assert.strictEqual(res.status, 200);
    assert.match(res.headers.get('Content-Disposition'), /attachment; filename="tfclient.jar"/);
    assert.strictEqual(res.headers.get('Content-Type'), 'application/java-archive');
    assert.deepStrictEqual(Buffer.from(await res.arrayBuffer()), jar);
  }
  const packs = await worker.fetch(new Request('http://tienda.test/packs'), e);
  assert.strictEqual(packs.status, 200);
  assert.match(packs.headers.get('Content-Disposition'), /filename="jefes-17.tar.enc"/);
  up = false;
  const down = await worker.fetch(new Request('http://tienda.test/descargar'), e);
  assert.strictEqual(down.status, 502);
  assert.match(await down.text(), /no está disponible/);
});

test('el puente rechaza una clave incorrecta', async () => {
  assert.strictEqual((await poll(['Steve'], [], { secret: 'otra' })).status, 401);
  const saved = env.BRIDGE_SECRET;
  env.BRIDGE_SECRET = '';
  try {
    assert.strictEqual((await poll(['Steve'])).status, 503);
  } finally {
    env.BRIDGE_SECRET = saved;
  }
});

test('el registro de jugadores sale del puente: nombre exacto y UUID del servidor', async () => {
  assert.deepStrictEqual(await (await get('/api/player/Steve_123')).json(), { found: false });
  await seen('Steve_123');
  const player = await (await get('/api/player/steve_123')).json();
  assert.strictEqual(player.found, true);
  assert.strictEqual(player.name, 'Steve_123');
  assert.strictEqual(player.uuid, uuidOf('Steve_123'));
  assert.strictEqual(player.online, false);
  await poll(['Steve_123']);
  assert.strictEqual((await (await get('/api/player/Steve_123')).json()).online, true);
  assert.strictEqual((await get('/api/player/a;b')).status, 400);
  // Datos basura del puente se ignoran.
  await poll([], [], { seen: [{ name: 'x y', uuid: 'no' }, { name: 'Bueno', uuid: 'zz' }] });
  assert.strictEqual((await (await get('/api/player/Bueno')).json()).found, false);
});

test('si un jugador se cambia el nombre, la tienda usa el nuevo y el viejo deja de existir', async () => {
  const uuid = uuidOf('Antiguo');
  await poll([], [], { seen: [{ name: 'Antiguo', uuid, at: Date.now() - 5000 }] });
  await poll([], [], { seen: [{ name: 'Nuevo_Nombre', uuid, at: Date.now() - 1000 }] });
  assert.strictEqual((await (await get('/api/player/Antiguo')).json()).found, false);
  assert.strictEqual((await (await get('/api/player/Nuevo_Nombre')).json()).uuid, uuid);
  // Un aviso viejo no deshace el cambio.
  await poll([], [], { seen: [{ name: 'Antiguo', uuid, at: Date.now() - 9000 }] });
  assert.strictEqual((await (await get('/api/player/Nuevo_Nombre')).json()).found, true);
});

test('comprar rechaza productos, cantidades y jugadores que el servidor no conoce', async () => {
  await seen('Steve');
  assert.strictEqual((await post('/api/checkout', { acceptTerms: true, productId: 'no-existe', username: 'Steve' })).status, 400);
  assert.strictEqual((await post('/api/checkout', { acceptTerms: true, productId: 'monedas-10000', username: 'a; op yo' })).status, 400);
  assert.strictEqual((await post('/api/checkout', { acceptTerms: true, productId: 'monedas-10000', username: 'Steve', quantity: 50 })).status, 400);
  assert.strictEqual((await post('/api/checkout', { acceptTerms: true, productId: 'rango-magico', username: 'Steve', quantity: 2 })).status, 400);
  const unknown = await post('/api/checkout', { acceptTerms: true, productId: 'monedas-10000', username: 'Fantasma' });
  assert.strictEqual(unknown.status, 404);
  assert.strictEqual((await unknown.json()).code, 'unknown_player');
  // El UUID que vio el navegador tiene que ser el del servidor.
  const other = await post('/api/checkout', { acceptTerms: true, productId: 'monedas-10000', username: 'Steve', uuid: uuidOf('Otro') });
  assert.strictEqual(other.status, 409);
});

test('el pago en Stripe usa el precio del catálogo y el nombre exacto del servidor', async () => {
  await seen('Steve');
  const id = await checkout({ productId: 'monedas-10000', username: 'STEVE', quantity: 3, price: 1 });
  const session = sessionOf(id);
  assert.strictEqual(session.amount_total, 597);
  assert.strictEqual(session.currency, 'usd');
  assert.strictEqual(session.form['line_items[0][quantity]'], '3');
  assert.strictEqual(session.form.success_url, `http://tienda.test/success?order=${id}`);
  assert.strictEqual(session.idempotencyKey, `checkout-${id}`);
  assert.strictEqual(session.metadata.uuid, uuidOf('Steve'));
  const order = await (await get(`/api/order/${id}`)).json();
  assert.strictEqual(order.username, 'Steve');
  assert.strictEqual(order.status, 'pending');
});

test('pago confirmado queda en cola y el servidor lo entrega una sola vez por UUID', async () => {
  received.length = 0;
  await seen('Steve_123');
  const id = await checkout({ productId: 'monedas-10000', username: 'Steve_123', quantity: 2 });
  assert.strictEqual((await pay(id)).status, 'queued');
  // Stripe reenvía el evento: no se encola otra vez.
  assert.strictEqual((await pay(id)).status, 'queued');

  // El jugador no está conectado: no se le entrega nada todavía.
  assert.deepStrictEqual(await serverDelivers('Otro'), []);
  // Alguien con el mismo nombre pero otro UUID tampoco lo recibe.
  const impostor = await (await call('/bridge/poll', {
    method: 'POST',
    headers: { Authorization: `Bearer ${BRIDGE_SECRET}` },
    body: JSON.stringify({ protocol: 2, players: [{ name: 'Steve_123', uuid: uuidOf('impostor') }] }),
  })).json();
  assert.deepStrictEqual(impostor.deliveries, []);
  assert.strictEqual(received.length, 0);

  const [delivery] = await serverDelivers('Steve_123');
  assert.strictEqual(delivery.product, '10.000 Monedas de Oro');
  assert.strictEqual(delivery.quantity, 2);
  assert.strictEqual(delivery.uuid, uuidOf('Steve_123'));
  assert.strictEqual(delivery.kind, 'monedas');
  assert.deepStrictEqual(received, ['eco give Steve_123 10000', 'eco give Steve_123 10000']);
  assert.deepStrictEqual(await serverDelivers('Steve_123'), []);
  assert.strictEqual((await (await get(`/api/order/${id}`)).json()).status, 'delivered');
});

test('el mod pide las compras (app Rastreador del pad de admin): quién, qué y sus comandos; sin pedirlas no van', async () => {
  await seen('Rastreo_MC');
  const id = await checkout({ productId: 'crate-valentine', username: 'Rastreo_MC' });
  await pay(id);
  await serverDelivers('Rastreo_MC');
  const without = await (await poll([])).json();
  assert.strictEqual(without.orders, undefined);
  const data = await (await poll([], [], { wantOrders: true })).json();
  const mine = data.orders.find((o) => o.id === id);
  assert.ok(mine);
  assert.strictEqual(mine.player, 'Rastreo_MC');
  assert.strictEqual(mine.uuid, uuidOf('Rastreo_MC'));
  assert.strictEqual(mine.productId, 'crate-valentine');
  assert.ok(mine.commands.some((c) => c.startsWith('tf web sets give {player} valentine')));
  // los que no se pagaron no salen
  const unpaid = await checkout({ productId: 'crate-valentine', username: 'Rastreo_MC' });
  assert.ok(!(await (await poll([], [], { wantOrders: true })).json()).orders.some((o) => o.id === unpaid));
});

test('las versiones antiguas del mod reciben los comandos con el nombre ya puesto', async () => {
  await seen('Viejo');
  const id = await checkout({ productId: 'monedas-10000', username: 'Viejo' });
  await pay(id);
  const res = await call('/bridge/poll', {
    method: 'POST',
    headers: { Authorization: `Bearer ${BRIDGE_SECRET}` },
    body: JSON.stringify({ players: [{ name: 'Viejo', uuid: uuidOf('Viejo') }] }),
  });
  const [delivery] = (await res.json()).deliveries;
  assert.deepStrictEqual(delivery.commands, ['eco give Viejo 10000']);
});

test('el webhook rechaza firmas falsas o viejas', async () => {
  await seen('Paciente');
  const id = await checkout({ productId: 'monedas-10000', username: 'Paciente' });
  const session = sessionOf(id);
  const event = { type: 'checkout.session.completed', data: { object: { ...session, status: 'complete', payment_status: 'paid' } } };
  assert.strictEqual((await signedEvent(event, { secret: 'whsec_otro' })).status, 400);
  assert.strictEqual((await signedEvent(event, { at: Math.floor(Date.now() / 1000) - 3600 })).status, 400);
  assert.strictEqual((await call('/webhook/stripe', { method: 'POST', body: JSON.stringify(event) })).status, 400);
  assert.strictEqual((await (await get(`/api/order/${id}`)).json()).status, 'pending');
});

test('si el webhook tarda, la página de confirmación consulta el pago a Stripe', async () => {
  received.length = 0;
  await seen('Prisa');
  const id = await checkout({ productId: 'crate-oni', username: 'Prisa' });
  assert.strictEqual((await (await post(`/api/order/${id}/sync`)).json()).status, 'pending');
  Object.assign(sessionOf(id), { status: 'complete', payment_status: 'paid', payment_intent: 'pi_prisa' });
  assert.strictEqual((await (await post(`/api/order/${id}/sync`)).json()).status, 'queued');
  await serverDelivers('Prisa');
  assert.deepStrictEqual(received, cmdsOf('crate-oni', 'Prisa'));
});

test('no se entrega si el importe cobrado no coincide', async () => {
  await seen('Tramposo');
  const id = await checkout({ productId: 'rango-magico', username: 'Tramposo' });
  assert.strictEqual((await pay(id, { amount_total: 1 })).status, 'error');
  assert.deepStrictEqual(await serverDelivers('Tramposo'), []);
});

test('pagos que tardan (transferencia) se entregan cuando Stripe confirma el cobro', async () => {
  received.length = 0;
  await seen('Lento_Pago');
  const id = await checkout({ productId: 'crate-valentine', username: 'Lento_Pago' });
  assert.strictEqual((await pay(id, { payment_status: 'unpaid' })).status, 'awaiting_payment');
  assert.deepStrictEqual(await serverDelivers('Lento_Pago'), []);
  assert.strictEqual((await pay(id, {}, 'checkout.session.async_payment_succeeded')).status, 'queued');
  await serverDelivers('Lento_Pago');
  assert.deepStrictEqual(received, cmdsOf('crate-valentine', 'Lento_Pago'));
});

test('un pago caducado o fallido no entrega nada', async () => {
  await seen('Caducado');
  const a = await checkout({ productId: 'monedas-10000', username: 'Caducado' });
  assert.strictEqual((await pay(a, { status: 'expired', payment_status: 'unpaid' }, 'checkout.session.expired')).status, 'expired');
  const b = await checkout({ productId: 'monedas-10000', username: 'Caducado' });
  await pay(b, { payment_status: 'unpaid' });
  assert.strictEqual((await pay(b, { payment_status: 'unpaid' }, 'checkout.session.async_payment_failed')).status, 'failed');
  assert.deepStrictEqual(await serverDelivers('Caducado'), []);
});

test('los reembolsos y disputas quedan apuntados en el pedido', async () => {
  await seen('Reembolso');
  const id = await checkout({ productId: 'monedas-10000', username: 'Reembolso' });
  await pay(id);
  const pi = sessionOf(id).payment_intent;
  await signedEvent({ type: 'charge.refunded', data: { object: { id: 'ch_1', payment_intent: pi, refunded: true } } });
  assert.strictEqual((await (await get(`/api/order/${id}`)).json()).refunded, 'reembolsado');
  await signedEvent({ type: 'charge.dispute.created', data: { object: { id: 'dp_1', payment_intent: pi } } });
  assert.strictEqual((await (await get(`/api/order/${id}`)).json()).refunded, 'disputa');
});

test('si el servidor no confirma una entrega, se reenvía pasado un rato', async () => {
  await seen('Lento');
  const id = await checkout({ productId: 'monedas-10000', username: 'Lento' });
  await pay(id);
  const first = (await (await poll(['Lento'])).json()).deliveries;
  assert.strictEqual(first.length, 1);
  assert.deepStrictEqual((await (await poll(['Lento'])).json()).deliveries, []);
  env.DB.raw.prepare('UPDATE deliveries SET sent_at = sent_at - 200000 WHERE id = ?').run(first[0].id);
  const again = (await (await poll(['Lento'])).json()).deliveries;
  assert.strictEqual(again[0].id, first[0].id);
  await poll(['Lento'], [{ id: first[0].id, ok: false, error: 'Comando desconocido' }]);
  assert.strictEqual((await (await get(`/api/order/${id}`)).json()).status, 'delivery_failed');
});

test('el estado del servidor sale del puente', async () => {
  await poll(['Steve', 'Alex']);
  const status = await (await get('/api/status')).json();
  assert.deepStrictEqual(status, { bridge: true, online: true, players: { online: 2, max: 50, list: ['Steve', 'Alex'] } });
  env.DB.raw.prepare("UPDATE settings SET updated_at = updated_at - 60000 WHERE key = 'server_status'").run();
  assert.deepStrictEqual(await (await get('/api/status')).json(), { bridge: false });
});

test('una crate es el set completo: se compra una vez y llega entero al juego', async () => {
  const list = await (await get('/api/products')).json();
  assert.ok(!list.some((p) => p.category === 'llaves'), 'ya no se venden llaves');
  assert.ok(list.every((p) => !('rarity' in p) && !('featured' in p) && !('keyImage' in p)));
  const crate = list.find((p) => p.id === 'crate-oni');
  assert.strictEqual(crate.category, 'crates');
  assert.strictEqual(crate.maxQuantity, 1);
  assert.ok(crate.image && crate.models.length > 0 && crate.models.some((m) => m.id === 'armor_helmet'));
  assert.ok(list.some((p) => p.id === 'crate-ifrit') && !list.some((p) => p.id === 'crate-nazgul'));

  received.length = 0;
  await seen('Alex');
  const res = await post('/api/checkout', { acceptTerms: true, productId: 'crate-oni', username: 'Alex', quantity: 2 });
  assert.strictEqual(res.status, 400);
  const id = await checkout({ productId: 'crate-oni', username: 'Alex' });
  assert.strictEqual(sessionOf(id).amount_total, crate.price);
  await pay(id);
  const [delivery] = await serverDelivers('Alex');
  assert.strictEqual(delivery.color, crate.colors[1]);
  assert.deepStrictEqual(received, cmdsOf('crate-oni', 'Alex'));
});

test('un cosmético se compra por pieza, solo con Fantastic Coins, y llega solo esa pieza al juego', async () => {
  const list = await (await get('/api/products')).json();
  const cos = list.filter((p) => p.category === 'cosmeticos');
  assert.ok(cos.length > 0 && cos.every((p) => p.set && p.item && p.slot && p.theme && p.collection && p.description));
  assert.ok(cos.every((p) => p.coinsOnly === true && Number.isSafeInteger(p.coinPrice) && p.coinPrice > 0));
  const balloon = cos.find((p) => p.id === 'cos-halloween23-spider-balloon');
  assert.strictEqual(balloon.slot, 'balloon');

  // Con dinero, no (TF Client 1.3.117: el dueño, «tiene precio en dinero, no en coins»)
  await seen('Globos_MC');
  const money = await post('/api/checkout', { acceptTerms: true, productId: balloon.id, username: 'Globos_MC' });
  assert.strictEqual(money.status, 400);
  assert.strictEqual((await money.json()).code, 'coins');

  received.length = 0;
  const cookie = await register('Globos_MC');
  const res = await post('/api/coins/buy', { productId: balloon.id }, cookie);
  assert.strictEqual(res.status, 200);
  await serverDelivers('Globos_MC');
  assert.deepStrictEqual(received, [`tf web monedas quitar Globos_MC ${balloon.coinPrice}`, 'tf web sets give Globos_MC halloween23 spider_balloon']);
});

test('la ruleta da premios por su probabilidad (armas solo de vez en cuando) y la compra dice cuáles tocaron', async () => {
  const list = await (await get('/api/products')).json();
  const spin = list.find((p) => p.id === 'ruleta-10');
  assert.strictEqual(spin.category, 'ruleta');
  assert.strictEqual(spin.spins, 10);
  assert.strictEqual(spin.pool.reduce((s, p) => s + p.chance, 0), 100);
  assert.ok(spin.pool.every((p) => !('give' in p)), 'los comandos de los premios no salen en la API');
  const weaponChance = spin.pool.filter((p) => p.weapon).reduce((s, p) => s + p.chance, 0);
  assert.ok(weaponChance > 0 && weaponChance <= 10, 'las armas son el premio raro');
  const weapons = new Map(spin.models.map((m) => [m.id, m.name]));
  const pool = new Map(spin.pool.map((p) => [p.id, p]));

  received.length = 0;
  await seen('Ruleta_MC');
  const id = await checkout({ productId: 'ruleta-10', username: 'Ruleta_MC' });
  assert.strictEqual(sessionOf(id).amount_total, spin.price);
  await pay(id);
  const order = await (await get(`/api/order/${id}`)).json();
  assert.strictEqual(order.prizes.length, 10);
  for (const prize of order.prizes) {
    if (prize.weapon) {
      assert.strictEqual(weapons.get(prize.id), prize.name);
      assert.strictEqual(prize.thumb, `/img/items/nazgul/${prize.id}.webp`);
    } else {
      assert.strictEqual(pool.get(prize.id).name, prize.name);
      assert.strictEqual(prize.thumb, pool.get(prize.id).icon);
    }
  }
  await serverDelivers('Ruleta_MC');
  const full = products.find((p) => p.id === 'ruleta-10');
  const expected = order.prizes.flatMap((p) =>
    p.weapon ? [`tf web sets give Ruleta_MC nazgul ${p.id}`] : full.pool.find((e) => e.id === p.id).give.map((c) => c.replaceAll('{player}', 'Ruleta_MC')),
  );
  assert.deepStrictEqual(received, expected);
});

test('la ruleta también se gira con las monedas del servidor: se cobra en el juego y la web enseña los premios', async () => {
  // Sin cuenta no se puede
  assert.strictEqual((await post('/api/roulette/coins', { spins: 5 })).status, 401);
  await seen('Monedas_MC');
  const cookie = await register('Monedas_MC');
  assert.strictEqual((await post('/api/roulette/coins', { spins: 7 }, cookie)).status, 400);

  // El servidor recibe la ruleta (premios con sus comandos, armas y precio en monedas)
  const config = (await (await poll([])).json()).roulette;
  assert.strictEqual(config.set, 'nazgul');
  assert.ok(config.coinPrice > 0);
  assert.ok(config.pool.some((p) => p.weapon) && config.pool.every((p) => p.weapon || p.give.length));
  assert.strictEqual(config.weapons.length, 30);

  const res = await post('/api/roulette/coins', { spins: 5 }, cookie);
  assert.strictEqual(res.status, 200);
  const { id } = await res.json();
  assert.match(id, /^COIN[0-9A-F]{24}$/);
  const [delivery] = (await (await poll(['Monedas_MC'])).json()).deliveries;
  assert.deepStrictEqual(delivery.commands, ['tf web ruleta girar {player} 5']);
  assert.strictEqual(delivery.kind, 'ruleta-monedas');

  // El servidor cobra, gira y manda los premios; la web solo acepta premios que existen
  const weapon = config.weapons[0];
  await poll(['Monedas_MC'], [{ id: delivery.id, ok: true, prizes: [
    { id: 'diamantes', name: '5 diamantes' },
    { id: weapon.id, name: weapon.name, weapon: true },
    { id: 'trampa', name: 'Algo inventado' },
  ] }]);
  const order = await (await get(`/api/order/${id}`)).json();
  assert.strictEqual(order.status, 'delivered');
  assert.strictEqual(order.coins, config.coinPrice * 5);
  assert.deepStrictEqual(order.prizes.map((p) => p.id), ['diamantes', weapon.id]);
  assert.strictEqual(order.prizes[1].thumb, `/img/items/nazgul/${weapon.id}.webp`);

  // Sin monedas suficientes: no se entrega y la web dice por qué
  const again = await (await post('/api/roulette/coins', { spins: 1 }, cookie)).json();
  const [second] = (await (await poll(['Monedas_MC'])).json()).deliveries;
  await poll(['Monedas_MC'], [{ id: second.id, ok: false, error: 'No tenías bastantes monedas en el servidor. No se ha cobrado nada.' }]);
  const failed = await (await get(`/api/order/${again.id}`)).json();
  assert.strictEqual(failed.status, 'delivery_failed');
  assert.match(failed.error, /bastantes monedas/);
});

// --- Tienda de monedas ---
test('el staff llena la tienda de monedas desde el juego y la web la muestra', async () => {
  const empty = await (await get('/api/coinshop')).json();
  const ops = [
    { op: 'add', id: 'diamantes', item: 'minecraft:diamond', name: 'Diamante', count: 16, price: 2400, by: 'Admin' },
    { op: 'add', id: 'espada', item: 'tfclient:necros_sword', name: 'Espada Necros', count: 1, price: 50000, nbt: '{Damage:0}', by: 'Admin' },
    { op: 'add', id: 'MAL id', item: 'minecraft:dirt', name: 'Tierra', count: 1, price: 1 },
    { op: 'add', id: 'gratis', item: 'minecraft:dirt', name: 'Tierra', count: 1, price: 0 },
  ];
  const res = await (await poll([], [], { shop: ops })).json();
  assert.deepStrictEqual(res.coinShop.map((i) => i.id).sort(), ['diamantes', 'espada']);

  const shop = await (await get('/api/coinshop')).json();
  assert.notStrictEqual(shop.version, empty.version);
  const diamond = shop.items.find((i) => i.id === 'diamantes');
  assert.deepStrictEqual(diamond, { id: 'diamantes', item: 'minecraft:diamond', name: 'Diamante', count: 16, price: 2400, icon: '/api/itemicon/diamond' });
  assert.strictEqual(shop.items.find((i) => i.id === 'espada').icon, '/img/items/necros/sword.webp');
  assert.ok(!JSON.stringify(shop).includes('Damage'), 'el NBT se queda en el servidor');

  // Cambiar el precio, quitar y vaciar
  await poll([], [], { shop: [{ op: 'add', id: 'diamantes', item: 'minecraft:diamond', name: 'Diamante', count: 16, price: 2000 }, { op: 'remove', id: 'espada' }] });
  const after = await (await get('/api/coinshop')).json();
  assert.deepStrictEqual(after.items.map((i) => [i.id, i.price]), [['diamantes', 2000]]);
  const cleared = await (await poll([], [], { shop: [{ op: 'clear' }] })).json();
  assert.deepStrictEqual(cleared.coinShop, []);

  // Sin la clave del puente no se toca nada
  const bad = await poll([], [], { secret: 'otra', shop: [{ op: 'add', id: 'x', item: 'minecraft:dirt', name: 'X', count: 1, price: 5 }] });
  assert.strictEqual(bad.status, 401);
  assert.deepStrictEqual((await (await get('/api/coinshop')).json()).items, []);
});

test('la tienda de monedas se compra en la web y se cobra en el juego', async () => {
  await poll([], [], { shop: [{ op: 'add', id: 'diamantes', item: 'minecraft:diamond', name: 'Diamante', count: 16, price: 2400 }] });
  assert.strictEqual((await post('/api/coinshop/buy', { id: 'diamantes' })).status, 401);
  await seen('Tienda_MC');
  const cookie = await register('Tienda_MC');
  assert.strictEqual((await post('/api/coinshop/buy', { id: 'no-existe' }, cookie)).status, 404);

  const { id } = await (await post('/api/coinshop/buy', { id: 'diamantes' }, cookie)).json();
  assert.match(id, /^COIN[0-9A-F]{24}$/);
  const [delivery] = (await (await poll(['Tienda_MC'])).json()).deliveries;
  assert.deepStrictEqual(delivery.commands, ['tf web tienda comprar {player} diamantes']);
  assert.strictEqual(delivery.kind, 'tienda-monedas');

  // Aunque luego se deje de vender, el pedido recuerda qué se compró y por cuánto
  await poll(['Tienda_MC'], [{ id: delivery.id, ok: true }], { shop: [{ op: 'clear' }] });
  const order = await (await get(`/api/order/${id}`)).json();
  assert.strictEqual(order.status, 'delivered');
  assert.strictEqual(order.kind, 'tienda');
  assert.strictEqual(order.product, '16× Diamante');
  assert.strictEqual(order.coins, 2400);
  assert.strictEqual(order.prizes, null);
});

test('los iconos de objetos solo aceptan nombres válidos', async () => {
  assert.strictEqual((await get('/api/itemicon/..%2F..%2Fsecret')).status, 400);
  assert.strictEqual((await get('/api/itemicon/Diamond')).status, 400);
});

// --- Rangos ---
test('los rangos se mejoran pagando la diferencia y no se puede comprar uno igual o inferior', async () => {
  received.length = 0;
  await seen('Rangos_MC');
  const rank = (id) => products.find((p) => p.id === id);

  const a = await checkout({ productId: 'rango-mortal', username: 'Rangos_MC' });
  assert.strictEqual(sessionOf(a).amount_total, rank('rango-mortal').price);
  await pay(a);
  const [first] = await serverDelivers('Rangos_MC');
  assert.deepStrictEqual(first.rank, { id: 'rango-mortal', name: 'Rango Mortal', tier: 1, group: 'mortal', prefix: 'Mortal', color: 'green', hex: '#4ade80' });
  assert.deepStrictEqual(received, cmdsOf('rango-mortal', 'Rangos_MC'));

  // El mismo rango o uno inferior: bloqueado.
  const again = await post('/api/checkout', { acceptTerms: true, productId: 'rango-mortal', username: 'Rangos_MC' });
  assert.strictEqual(again.status, 409);
  assert.strictEqual((await again.json()).code, 'rank_owned');

  // La web muestra el precio de la mejora antes de pagar.
  const look = await (await get('/api/player/Rangos_MC?product=rango-magico')).json();
  assert.strictEqual(look.rank.id, 'rango-mortal');
  const diff = rank('rango-magico').price - rank('rango-mortal').price;
  assert.deepStrictEqual(look.quote, { unit: diff, upgradeFrom: 'Rango Mortal' });

  received.length = 0;
  const b = await checkout({ productId: 'rango-magico', username: 'Rangos_MC' });
  assert.strictEqual(sessionOf(b).amount_total, diff);
  const order = await pay(b);
  assert.strictEqual(order.upgradeFrom, 'Rango Mortal');
  const [second] = await serverDelivers('Rangos_MC');
  assert.strictEqual(second.upgradeFrom, 'Rango Mortal');
  // «parent set» deja solo el rango nuevo: no hace falta quitar los de abajo
  assert.deepStrictEqual(received, cmdsOf('rango-magico', 'Rangos_MC'));
  assert.strictEqual(received[0], 'lp user Rangos_MC parent set magico');
  assert.strictEqual((await post('/api/checkout', { acceptTerms: true, productId: 'rango-inmortal', username: 'Rangos_MC' })).status, 409);

  // El servidor recibe el rango de los conectados para su nametag.
  const { ranks, rankList } = await (await poll(['Rangos_MC', 'Steve'])).json();
  assert.deepStrictEqual(ranks.map((r) => [r.uuid, r.prefix]), [[uuidOf('Rangos_MC'), 'Mágico']]);
  assert.strictEqual(rankList.length, 7);
  // Y lo que el TF Client pone en el grupo de LuckPerms de cada rango: lo que pidió el dueño, tal cual
  const setup = Object.fromEntries(rankList.map((r) => [r.group, r]));
  const perks = {
    mortal: [5, []],
    inmortal: [7, []],
    magico: [8, ['enderchest']],
    eterno: [10, ['enderchest', 'heal']],
    cosmico: [12, ['enderchest', 'heal', 'feed']],
    celestial: [15, ['enderchest', 'heal', 'repair', 'fly']],
    fantastico: [20, ['enderchest', 'repair', 'heal', 'feed', 'fly']],
  };
  for (const [group, [homes, extra]] of Object.entries(perks)) {
    assert.strictEqual(setup[group].homes, homes);
    const base = ['sethome', 'home', 'delhome', 'sethome.multiple', `sethome.multiple.${group}`, 'workbench', 'anvil', 'loom', 'hat'];
    assert.deepStrictEqual([...setup[group].permissions].sort(), [...base, ...extra].map((n) => `essentials.${n}`).sort(), group);
  }
  // Los datos de un jugador no llevan los permisos (solo el puente)
  assert.strictEqual(ranks[0].permissions, undefined);
});

test('el staff puede cambiar o quitar un rango desde el juego', async () => {
  await seen('Staff_Rank');
  const uuid = uuidOf('Staff_Rank');
  await poll([], [], { ranks: [{ uuid, rank: 'rango-fantastico' }] });
  assert.strictEqual((await (await get('/api/player/Staff_Rank')).json()).rank.id, 'rango-fantastico');
  await poll([], [], { ranks: [{ uuid, rank: 'rango-mortal' }] });
  assert.strictEqual((await (await get('/api/player/Staff_Rank')).json()).rank.id, 'rango-mortal');
  await poll([], [], { ranks: [{ uuid, rank: null }, { uuid, rank: 'no-existe' }] });
  assert.strictEqual((await (await get('/api/player/Staff_Rank')).json()).rank, null);
});

// --- Cuentas y Discord ---
const cookieValue = (res, name) =>
  res.headers.getSetCookie().map((c) => c.split(';')[0]).find((c) => c.startsWith(`${name}=`));

// Crea la cuenta de un jugador (que el servidor ya conoce) y devuelve su cookie de sesión.
async function register(name, password = 'contraseña-segura') {
  await seen(name);
  const res = await post('/api/auth/register', { acceptTerms: true, name, password });
  assert.strictEqual(res.status, 200, (await res.clone().json()).error);
  return cookieValue(res, 'tf_session');
}

// Pasa por Discord (con o sin sesión) y devuelve la respuesta final de la web.
async function viaDiscord(cookie) {
  const start = await get(`/auth/discord?return=${encodeURIComponent('/cuenta')}`, cookie ? { Cookie: cookie } : {});
  assert.strictEqual(start.status, 302);
  const authorize = new URL(start.headers.get('location'));
  assert.strictEqual(authorize.origin, 'https://discord.com');
  assert.strictEqual(authorize.searchParams.get('redirect_uri'), 'http://tienda.test/auth/discord/callback');
  assert.match(authorize.searchParams.get('scope'), /guilds\.members\.read/);
  const state = authorize.searchParams.get('state');
  const stateCookie = cookieValue(start, 'tf_oauth_state');
  return get(`/auth/discord/callback?code=good-code&state=${state}`, { Cookie: [stateCookie, cookie].filter(Boolean).join('; ') });
}

test('crear cuenta: solo con un jugador que el servidor conoce, una por jugador y contraseña segura', async () => {
  assert.strictEqual((await post('/api/auth/register', { acceptTerms: true, name: 'Nadie_Aqui', password: 'contraseña-segura' })).status, 404);
  await seen('Cuenta_MC');
  assert.strictEqual((await post('/api/auth/register', { acceptTerms: true, name: 'Cuenta_MC', password: 'corta' })).status, 400);
  assert.strictEqual((await post('/api/auth/register', { acceptTerms: true, name: 'a b', password: 'contraseña-segura' })).status, 400);

  const res = await post('/api/auth/register', { acceptTerms: true, name: 'cuenta_mc', password: 'contraseña-segura' });
  assert.strictEqual(res.status, 200);
  const { user } = await res.json();
  assert.strictEqual(user.name, 'Cuenta_MC');
  assert.strictEqual(user.uuid, uuidOf('Cuenta_MC'));
  assert.strictEqual(user.discord, null);
  const cookie = cookieValue(res, 'tf_session');
  assert.ok(cookie);
  assert.ok(res.headers.getSetCookie()[0].includes('HttpOnly'));

  // La contraseña nunca se guarda tal cual.
  const row = env.DB.raw.prepare('SELECT password FROM users WHERE uuid = ?').get(uuidOf('Cuenta_MC'));
  assert.match(row.password, /^pbkdf2\$100000\$/);
  assert.ok(!row.password.includes('contraseña-segura'));

  // El mismo jugador no puede tener otra cuenta.
  assert.strictEqual((await post('/api/auth/register', { acceptTerms: true, name: 'Cuenta_MC', password: 'otra-contraseña' })).status, 409);

  const me = await (await get('/api/me', { Cookie: cookie })).json();
  assert.strictEqual(me.user.name, 'Cuenta_MC');
});

test('entrar con nombre y contraseña, la sesión se renueva y cerrar sesión', async () => {
  await register('Entrar_MC', 'mi-clave-123');
  assert.strictEqual((await post('/api/auth/login', { name: 'Entrar_MC', password: 'mala-clave' })).status, 401);
  assert.strictEqual((await post('/api/auth/login', { name: 'Fantasma', password: 'mi-clave-123' })).status, 401);

  const res = await post('/api/auth/login', { name: 'entrar_mc', password: 'mi-clave-123' });
  assert.strictEqual(res.status, 200);
  const cookie = cookieValue(res, 'tf_session');
  assert.match(res.headers.getSetCookie()[0], /Max-Age=5184000/);

  // Cada visita alarga la sesión.
  const me = await get('/api/me', { Cookie: cookie });
  assert.strictEqual((await me.json()).user.name, 'Entrar_MC');
  assert.ok(cookieValue(me, 'tf_session'));

  const out = await post('/auth/logout', null, cookie);
  assert.match(out.headers.getSetCookie()[0], /Max-Age=0/);
  // Una cookie manipulada no sirve.
  assert.strictEqual((await (await get('/api/me', { Cookie: 'tf_session=eyJ1dWlkIjoiMSJ9.firma' })).json()).user, null);
});

test('tras muchos intentos fallidos la cuenta se bloquea un rato', async () => {
  await register('Bloqueo_MC', 'clave-buena-1');
  for (let i = 0; i < 8; i++) await post('/api/auth/login', { name: 'Bloqueo_MC', password: `mala-${i}-xxxx` });
  const locked = await post('/api/auth/login', { name: 'Bloqueo_MC', password: 'clave-buena-1' });
  assert.strictEqual(locked.status, 429);
});

test('cambiar la contraseña cierra las sesiones viejas', async () => {
  const cookie = await register('Cambio_MC', 'clave-vieja-1');
  assert.strictEqual((await post('/api/account/password', { current: 'mala', password: 'clave-nueva-1' }, cookie)).status, 401);
  const res = await post('/api/account/password', { current: 'clave-vieja-1', password: 'clave-nueva-1' }, cookie);
  assert.strictEqual(res.status, 200);
  const fresh = cookieValue(res, 'tf_session');
  assert.strictEqual((await (await get('/api/me', { Cookie: cookie })).json()).user, null);
  assert.strictEqual((await (await get('/api/me', { Cookie: fresh })).json()).user.name, 'Cambio_MC');
  assert.strictEqual((await post('/api/auth/login', { name: 'Cambio_MC', password: 'clave-nueva-1' })).status, 200);
});

test('Discord: rechaza un state falso y no permite redirigir fuera de la web', async () => {
  const start = await get(`/auth/discord?return=${encodeURIComponent('//evil.com')}`);
  const stateCookie = cookieValue(start, 'tf_oauth_state');
  const cb = await get('/auth/discord/callback?code=good-code&state=falso', { Cookie: stateCookie });
  assert.strictEqual(cb.headers.get('location'), '/?discord=error');
  assert.strictEqual(cookieValue(cb, 'tf_session'), undefined);
});

test('sin SESSION_SECRET la web crea y guarda su propia clave de sesión', async () => {
  const other = { ...env, SESSION_SECRET: undefined };
  const a = await worker.fetch(new Request('http://tienda.test/auth/discord', { redirect: 'manual' }), other);
  assert.strictEqual(a.status, 302);
  const row = env.DB.raw.prepare("SELECT value FROM settings WHERE key = 'secret:session'").get();
  assert.match(row.value, /^[0-9a-f]{64}$/);
});

test('conectar Discord guarda su @ y comprueba que está en el servidor de Tierras Fantásticas', async () => {
  memberExists = true;
  const cookie = await register('Alex_MC');
  const cb = await viaDiscord(cookie);
  assert.strictEqual(cb.headers.get('location'), '/cuenta?discord=ok');
  const { user } = await (await get('/api/me', { Cookie: cookie })).json();
  assert.strictEqual(user.discord.id, '111111111111111111');
  assert.strictEqual(user.discord.username, 'alex');
  assert.strictEqual(user.discord.name, 'Alex');
  assert.strictEqual(user.discord.member, true);

  // Ya puede entrar con Discord sin contraseña.
  const login = await viaDiscord(null);
  assert.strictEqual(login.headers.get('location'), '/cuenta?discord=ok');
  const session = cookieValue(login, 'tf_session');
  assert.strictEqual((await (await get('/api/me', { Cookie: session })).json()).user.name, 'Alex_MC');

  // Ese Discord no se puede conectar a otro jugador.
  const other = await register('Otro_Jugador');
  assert.strictEqual((await viaDiscord(other)).headers.get('location'), '/cuenta?discord=taken');
});

test('entrar con un Discord que no tiene cuenta pide crearla primero', async () => {
  const saved = discordProfile;
  discordProfile = { id: '444444444444444444', username: 'nuevo', global_name: null, avatar: null };
  try {
    const res = await viaDiscord(null);
    assert.strictEqual(res.headers.get('location'), '/cuenta?discord=noaccount');
    assert.strictEqual(cookieValue(res, 'tf_session'), undefined);
  } finally {
    discordProfile = saved;
  }
});

test('si no está en el servidor de Discord, el bot lo mete; sin bot se avisa y se puede volver a comprobar', async () => {
  const saved = discordProfile;
  discordProfile = { id: '555555555555555555', username: 'fuera', global_name: 'Fuera', avatar: null };
  try {
    memberExists = false;
    const a = await register('Fuera_MC');
    assert.strictEqual((await viaDiscord(a)).headers.get('location'), '/cuenta?discord=ok');
    assert.ok(discordCalls.some((c) => c.method === 'PUT' && c.url === '/guilds/333333333333333333/members/555555555555555555'));

    // Sin bot: no se le puede meter, queda como "no está en el servidor".
    memberExists = false;
    const noBot = { ...env, DISCORD_BOT_TOKEN: undefined };
    const callNoBot = (path, headers = {}) => worker.fetch(new Request(`http://tienda.test${path}`, { redirect: 'manual', headers }), noBot);
    await post('/api/account/discord/unlink', null, a);
    const start = await callNoBot('/auth/discord', { Cookie: a });
    assert.doesNotMatch(new URL(start.headers.get('location')).searchParams.get('scope'), /guilds\.join/);
    const state = new URL(start.headers.get('location')).searchParams.get('state');
    const cb = await callNoBot(`/auth/discord/callback?code=good-code&state=${state}`, { Cookie: `${cookieValue(start, 'tf_oauth_state')}; ${a}` });
    assert.strictEqual(cb.headers.get('location'), '/cuenta?discord=notmember');
    assert.strictEqual((await (await get('/api/me', { Cookie: a })).json()).user.discord.member, false);

    // Se une por su cuenta y lo vuelve a comprobar (con el bot).
    memberExists = true;
    const check = await (await post('/api/account/discord/check', null, a)).json();
    assert.strictEqual(check.member, true);
    assert.strictEqual((await (await get('/api/me', { Cookie: a })).json()).user.discord.member, true);
  } finally {
    discordProfile = saved;
    memberExists = true;
  }
});

test('comprar con la sesión iniciada da el rol, anuncia la compra y sale en "mis compras"', async () => {
  received.length = 0;
  discordCalls.length = 0;
  announcements.length = 0;
  memberExists = true;

  const res = await post('/api/auth/login', { name: 'Alex_MC', password: 'contraseña-segura' });
  const cookie = cookieValue(res, 'tf_session');
  const list = await (await get('/api/products')).json();
  assert.strictEqual(list.find((p) => p.id === 'rango-inmortal').discordRole, true);

  const id = await checkout({ productId: 'rango-inmortal', username: 'Alex_MC' }, cookie);
  assert.strictEqual((await pay(id)).status, 'queued');

  await serverDelivers('Alex_MC');
  assert.strictEqual(received[0], 'lp user Alex_MC parent set inmortal');
  const roleCall = discordCalls.find((c) => c.url.endsWith(`/roles/${ROLE_ID}`));
  assert.ok(roleCall, 'debe dar el rol');
  assert.strictEqual(roleCall.url, `/guilds/333333333333333333/members/111111111111111111/roles/${ROLE_ID}`);
  assert.strictEqual(announcements.length, 1);
  assert.match(announcements[0].embeds[0].description, /<@111111111111111111>.*Rango Inmortal/);

  const order = await (await get(`/api/order/${id}`)).json();
  assert.deepStrictEqual(order.discord, { username: 'alex', status: 'granted' });

  const { orders } = await (await get('/api/account/orders', { Cookie: cookie })).json();
  assert.ok(orders.some((o) => o.id === id && o.status === 'delivered'));
  const me = await (await get('/api/me', { Cookie: cookie })).json();
  assert.strictEqual(me.user.rank.id, 'rango-inmortal');
});

test('sin iniciar sesión, el rol va al Discord conectado a la cuenta del jugador', async () => {
  discordCalls.length = 0;
  await seen('Alex_MC');
  products.find((p) => p.id === 'rango-fantastico').discordRoles = [ROLE_ID];
  try {
    const id = await checkout({ productId: 'rango-fantastico', username: 'Alex_MC' });
    await pay(id);
    assert.ok(discordCalls.some((c) => c.url === `/guilds/333333333333333333/members/111111111111111111/roles/${ROLE_ID}`));
  } finally {
    products.find((p) => p.id === 'rango-fantastico').discordRoles = ['ID_ROL_FANTASTICO'];
  }
});

test('un fallo en Discord no impide la entrega en el juego', async () => {
  received.length = 0;
  failRoles = true;
  try {
    const cookie = cookieValue(await post('/api/auth/login', { name: 'Alex_MC', password: 'contraseña-segura' }), 'tf_session');
    await seen('Fallo_MC');
    const id = await checkout({ productId: 'rango-inmortal', username: 'Fallo_MC' }, cookie);
    assert.strictEqual((await pay(id)).status, 'queued');
    await serverDelivers('Fallo_MC');
    assert.strictEqual(received[0], 'lp user Fallo_MC parent set inmortal');
    const order = await (await get(`/api/order/${id}`)).json();
    assert.strictEqual(order.status, 'delivered');
    assert.strictEqual(order.discord.status, 'failed');
  } finally {
    failRoles = false;
  }
});

test('el /tf vincular del mod antiguo ya no vincula nada', async () => {
  const res = await (await poll(['Viejo_Mod'], [], { links: [{ code: 'ABCDEF', name: 'Viejo_Mod', uuid: uuidOf('Viejo_Mod') }] })).json();
  assert.deepStrictEqual(res.linkResults, [{ uuid: uuidOf('Viejo_Mod'), ok: false, account: null }]);
});

// --- Regalos gratis ---
test('los regalos gratis se reclaman con la cuenta, una vez por jugador, y se anuncian como una compra', async () => {
  received.length = 0;
  announcements.length = 0;
  // Sin sesión no se puede.
  assert.strictEqual((await post('/api/claim', { productId: 'regalo-diamantes' })).status, 401);
  // Y no se pueden pagar con Stripe.
  await seen('Regalo_MC');
  assert.strictEqual((await post('/api/checkout', { acceptTerms: true, productId: 'regalo-diamantes', username: 'Regalo_MC' })).status, 400);

  const cookie = await register('Regalo_MC');
  // Un producto de pago no se reclama gratis.
  assert.strictEqual((await post('/api/claim', { productId: 'rango-fantastico' }, cookie)).status, 400);

  const res = await post('/api/claim', { productId: 'regalo-diamantes' }, cookie);
  assert.strictEqual(res.status, 200);
  const { id, status } = await res.json();
  assert.strictEqual(status, 'queued');
  assert.match(id, /^GIFT[0-9A-Z]{2}[0-9A-F]{32}$/);
  assert.strictEqual(announcements.length, 1);

  // Una sola vez por jugador.
  assert.strictEqual((await post('/api/claim', { productId: 'regalo-diamantes' }, cookie)).status, 409);

  const [delivery] = await serverDelivers('Regalo_MC');
  assert.strictEqual(delivery.uuid, uuidOf('Regalo_MC'));
  assert.deepStrictEqual(received, ['give Regalo_MC minecraft:diamond 3']);
  const order = await (await get(`/api/order/${id}`)).json();
  assert.strictEqual(order.status, 'delivered');
  assert.strictEqual(order.amount, 0);
  const { orders } = await (await get('/api/account/orders', { Cookie: cookie })).json();
  assert.ok(orders.some((o) => o.id === id));
});

// --- VFX: efectos de kill (gratis). Los paquetes de skills ya no se dan, pero quien tenía uno lo conserva ---
test('los VFX se compran (2,99 USD cada uno), quedan en la cuenta, se equipan y el puente los manda al servidor', async () => {
  const catalog = await (await get('/api/vfx')).json();
  assert.strictEqual(catalog.free, false);
  assert.strictEqual(catalog.price, 299);
  assert.ok(catalog.kills.length >= 50);
  // Los paquetes de skills ya no salen en la tienda
  assert.strictEqual(catalog.packs, undefined);
  // Cada kill trae su vista previa animada
  assert.ok(catalog.kills.every((k) => k.preview));
  // Cada efecto de kill está en una de las categorías de la web y es un producto a 2,99
  const cats = new Set(catalog.cats.map((c) => c.id));
  assert.ok(cats.size >= 5 && catalog.kills.every((k) => cats.has(k.cat)));
  const list = await (await get('/api/products')).json();
  assert.ok(catalog.kills.every((k) => list.some((p) => p.id === `vfx-${k.id}` && p.category === 'vfx' && p.price === 299)));
  assert.strictEqual(catalog.me, null);
  const kill = catalog.kills[0].id;
  // Ya no se obtienen gratis; equipar uno que no tiene, tampoco.
  assert.strictEqual((await post('/api/vfx/claim', { kind: 'kill', id: kill })).status, 401);
  await seen('Efectos_MC');
  const cookie = await register('Efectos_MC');
  assert.strictEqual((await post('/api/vfx/claim', { kind: 'kill', id: kill }, cookie)).status, 403);
  assert.strictEqual((await post('/api/vfx/claim', { kind: 'pack', id: 'vendaval' }, cookie)).status, 400);
  assert.strictEqual((await post('/api/vfx/equip', { kind: 'pack', id: 'vendaval' }, cookie)).status, 403);
  assert.strictEqual((await post('/api/vfx/equip', { kind: 'kill', id: kill }, cookie)).status, 403);

  // Comprarlo lo deja en la cuenta y equipado; la entrega no lleva comandos.
  const id = await checkout({ productId: `vfx-${kill}`, username: 'Efectos_MC' });
  assert.strictEqual(sessionOf(id).amount_total, 299);
  const order = await pay(id);
  assert.strictEqual(order.status, 'queued');
  let { me } = await (await get('/api/vfx', { Cookie: cookie })).json();
  assert.deepStrictEqual(me.owned, [`kill:${kill}`]);
  assert.strictEqual(me.kill, kill);
  assert.deepStrictEqual(me.packs, []);
  const first = me.at;
  // No se puede volver a comprar
  const again = await post('/api/checkout', { acceptTerms: true, productId: `vfx-${kill}`, username: 'Efectos_MC' });
  assert.strictEqual(again.status, 409);

  // El servidor recibe lo equipado de los conectados (con la hora del cambio).
  let res = await (await poll(['Efectos_MC'])).json();
  assert.deepStrictEqual(res.vfx, [{ uuid: uuidOf('Efectos_MC'), kill, pack: null, at: first }]);
  assert.deepStrictEqual(res.deliveries[0].commands, []);
  let { vfx } = await (await poll(['Otro_MC'])).json();
  assert.deepStrictEqual(vfx, []);

  // Quitar el efecto de kill cambia la hora.
  ({ me } = await (await post('/api/vfx/equip', { kind: 'kill', id: null }, cookie)).json());
  assert.strictEqual(me.kill, null);
  assert.ok(me.at > first);
  // Volver a equipar uno que ya tiene.
  ({ me } = await (await post('/api/vfx/equip', { kind: 'kill', id: kill }, cookie)).json());
  assert.strictEqual(me.kill, kill);

  // Un reembolso se lo quita (y deja de llevarlo)
  await signedEvent({ type: 'charge.refunded', data: { object: { id: `ch_${id}`, payment_intent: sessionOf(id).payment_intent, refunded: true } } });
  ({ me } = await (await get('/api/vfx', { Cookie: cookie })).json());
  assert.deepStrictEqual(me.owned, []);
  assert.strictEqual(me.kill, null);
});

test('quien ya tenía un paquete de skills lo sigue viendo y equipando (web y pad), sin que se venda', async () => {
  await seen('Paquete_MC');
  const cookie = await register('Paquete_MC');
  const uuid = uuidOf('Paquete_MC');
  // Lo obtuvo cuando se daban (antes de quitarlos de la tienda)
  env.DB.raw.prepare("INSERT INTO vfx_owned (uuid, item, created_at) VALUES (?, 'pack:vendaval', datetime('now'))").run(uuid);
  let { me } = await (await get('/api/vfx', { Cookie: cookie })).json();
  assert.deepStrictEqual(me.packs, [{ id: 'vendaval', name: 'Filo del Vendaval', image: 'img/vfx/pack_vendaval.webp' }]);
  // Lo equipa y el servidor lo recibe
  ({ me } = await (await post('/api/vfx/equip', { kind: 'pack', id: 'vendaval' }, cookie)).json());
  assert.strictEqual(me.pack, 'vendaval');
  let { vfx } = await (await poll(['Paquete_MC'])).json();
  assert.strictEqual(vfx[0].pack, 'vendaval');
  // Uno que no tenía, no (ni desde la web ni desde el pad)
  assert.strictEqual((await post('/api/vfx/equip', { kind: 'pack', id: 'trueno' }, cookie)).status, 403);
  ({ vfx } = await (await poll(['Paquete_MC'], [], { pad: { vfx: [{ uuid, kind: 'pack', id: 'trueno' }] } })).json());
  assert.strictEqual(vfx[0].pack, 'vendaval');
  // Desde el pad lo quita y lo vuelve a poner
  ({ vfx } = await (await poll(['Paquete_MC'], [], { pad: { vfx: [{ uuid, kind: 'pack', id: null }] } })).json());
  assert.strictEqual(vfx[0].pack, null);
  ({ vfx } = await (await poll(['Paquete_MC'], [], { pad: { vfx: [{ uuid, kind: 'pack', id: 'vendaval' }] } })).json());
  assert.strictEqual(vfx[0].pack, 'vendaval');
  // Quitarlo desde la web
  ({ me } = await (await post('/api/vfx/equip', { kind: 'pack', id: null }, cookie)).json());
  assert.strictEqual(me.pack, null);
});

// --- Aceptación de los Términos ---
test('los Términos se aceptan con una casilla al crear la cuenta y al pagar, y queda la prueba guardada', async () => {
  const { version } = await import('../config/legal.json', { with: { type: 'json' } }).then((m) => m.default);
  const config = await (await get('/api/config')).json();
  assert.strictEqual(config.termsVersion, version);

  // Sin marcar la casilla no se crea la cuenta ni se paga.
  await seen('Terminos_MC');
  const noTerms = await post('/api/auth/register', { name: 'Terminos_MC', password: 'contraseña-segura' });
  assert.strictEqual(noTerms.status, 400);
  assert.strictEqual((await noTerms.json()).code, 'terms');
  const noTermsPay = await post('/api/checkout', { productId: 'monedas-10000', username: 'Terminos_MC' });
  assert.strictEqual(noTermsPay.status, 400);
  assert.strictEqual((await noTermsPay.json()).code, 'terms');

  // Con la casilla: la cuenta guarda la versión aceptada y queda la fila de la prueba (con IP y navegador).
  const res = await post('/api/auth/register', { acceptTerms: true, name: 'Terminos_MC', password: 'contraseña-segura' });
  assert.strictEqual(res.status, 200);
  const cookie = cookieValue(res, 'tf_session');
  assert.strictEqual((await res.json()).user.termsOk, true);
  const id = await checkout({ productId: 'monedas-10000', username: 'Terminos_MC' }, cookie);
  const rows = env.DB.raw.prepare('SELECT * FROM terms_acceptances WHERE uuid = ? ORDER BY id').all(uuidOf('Terminos_MC'));
  assert.deepStrictEqual(rows.map((r) => [r.context, r.version, r.order_id]), [['cuenta', version, null], ['compra', version, id]]);
  const order = env.DB.raw.prepare('SELECT terms_version FROM orders WHERE id = ?').get(id);
  assert.strictEqual(order.terms_version, version);
  const mine = await (await get('/api/account/terms', { Cookie: cookie })).json();
  assert.strictEqual(mine.acceptances.length, 2);

  // Una cuenta con una versión vieja tiene que volver a aceptar antes de reclamar recompensas o comprar con monedas.
  env.DB.raw.prepare("UPDATE users SET terms_version = '2000-01-01' WHERE uuid = ?").run(uuidOf('Terminos_MC'));
  const me = await (await get('/api/me', { Cookie: cookie })).json();
  assert.strictEqual(me.user.termsOk, false);
  const blocked = await post('/api/claim', { productId: 'regalo-diamantes' }, cookie);
  assert.strictEqual(blocked.status, 403);
  assert.strictEqual((await blocked.json()).code, 'terms');
  assert.strictEqual((await post('/api/account/terms', {}, cookie)).status, 400);
  const accepted = await (await post('/api/account/terms', { acceptTerms: true }, cookie)).json();
  assert.strictEqual(accepted.user.termsOk, true);
  assert.strictEqual((await post('/api/claim', { productId: 'regalo-diamantes' }, cookie)).status, 200);
});

// --- Reembolsos: se retira del servidor lo comprado ---
test('un reembolso completo retira del servidor lo comprado, aunque el jugador no esté conectado', async () => {
  received.length = 0;
  await seen('Devuelve_MC');
  const uuid = uuidOf('Devuelve_MC');
  const refund = (id) =>
    signedEvent({ type: 'charge.refunded', data: { object: { id: `ch_${id}`, payment_intent: sessionOf(id).payment_intent, refunded: true } } });

  // Una crate entregada y una mejora de rango (Mortal → Mágico) entregada.
  const crate = await checkout({ productId: 'crate-valentine', username: 'Devuelve_MC' });
  await pay(crate);
  const mortal = await checkout({ productId: 'rango-mortal', username: 'Devuelve_MC' });
  await pay(mortal);
  const magico = await checkout({ productId: 'rango-magico', username: 'Devuelve_MC' });
  await pay(magico);
  const delivered = await serverDelivers('Devuelve_MC');
  assert.ok(delivered.every((d) => typeof d.order === 'string' && d.order.length > 0), 'cada entrega lleva su pedido');
  received.length = 0;

  // Un reembolso parcial solo se apunta.
  await signedEvent({ type: 'charge.refunded', data: { object: { id: 'ch_p', payment_intent: sessionOf(crate).payment_intent, refunded: false } } });
  assert.strictEqual((await (await get(`/api/order/${crate}`)).json()).refunded, 'reembolso parcial');
  assert.deepStrictEqual((await (await poll([])).json()).deliveries, []);

  // Reembolso completo de la crate y de la mejora: la retirada llega aunque el jugador no esté conectado.
  await refund(crate);
  await refund(magico);
  const { deliveries } = await (await poll([])).json();
  assert.deepStrictEqual(
    deliveries.map((d) => [d.kind, d.order, d.uuid, d.commands]),
    [
      // (1.3.125) y las monedas que dio la crate: si ya las gastó, saldo negativo en el servidor
      ['revocacion', crate, uuid, [`tf web sets revoke {uuid} ${crate} valentine`, 'tf web monedas retirar {uuid} 100', 'tf web verdes retirar {uuid} 1']],
      ['revocacion', magico, uuid, ['lp user {uuid} parent remove magico', `tf web sets revoke {uuid} ${magico} darkworld`,
        'tf web monedas retirar {uuid} 200', 'tf web verdes retirar {uuid} 4', 'lp user {uuid} parent add mortal']],
    ],
  );
  // Vuelve a su rango anterior en la web.
  assert.strictEqual((await (await get('/api/player/Devuelve_MC')).json()).rank.id, 'rango-mortal');
  // Un segundo aviso del mismo reembolso no la duplica.
  await refund(crate);
  await poll([], deliveries.map((d) => ({ id: d.id, ok: true })));
  assert.deepStrictEqual((await (await poll([])).json()).deliveries, []);
  assert.strictEqual((await (await get(`/api/order/${crate}`)).json()).refunded, 'reembolsado · retirado del servidor');

  // Si se reembolsa antes de entregarse, la entrega se cancela.
  const late = await checkout({ productId: 'crate-oni', username: 'Devuelve_MC' });
  await pay(late);
  await refund(late);
  const pending = (await (await poll(['Devuelve_MC'])).json()).deliveries;
  assert.deepStrictEqual(pending.map((d) => d.kind), ['revocacion']);
});

// --- PayPal ---
test('pagar con PayPal: se aprueba en PayPal, se cobra al volver y un reembolso lo retira del servidor', async () => {
  received.length = 0;
  await seen('Paypal_MC');
  const config = await (await get('/api/config')).json();
  assert.strictEqual(config.paypalEnabled, true);

  const res = await post('/api/checkout', { acceptTerms: true, method: 'paypal', productId: 'crate-valentine', username: 'Paypal_MC' });
  const { id, url } = await res.json();
  assert.strictEqual(res.status, 200);
  assert.match(url, /^https:\/\/www\.paypal\.test\/checkoutnow\?token=PPORDER/);
  const ppOrder = [...paypalOrders.values()].find((o) => o.purchase_units[0].custom_id === id);
  const price = products.find((p) => p.id === 'crate-valentine').price;
  assert.strictEqual(ppOrder.purchase_units[0].amount.value, (price / 100).toFixed(2));
  assert.strictEqual(ppOrder.context.return_url, `http://tienda.test/success?order=${id}`);
  // (2026-10-25) el artículo lleva qué es (descripción y sku); la imagen y el enlace, solo con https (aquí no)
  const ppItem = ppOrder.purchase_units[0].items[0];
  assert.ok(ppItem.description.includes('Para Paypal_MC'));
  assert.ok(ppItem.description.length <= 127);
  assert.strictEqual(ppItem.sku, 'crate-valentine');
  assert.strictEqual(ppItem.image_url, undefined);

  // Sin aprobar en PayPal no se cobra ni se entrega.
  assert.strictEqual((await (await post(`/api/order/${id}/sync`)).json()).status, 'pending');
  // Aprobado: al volver a /success se cobra y se entrega.
  ppOrder.approved = true;
  assert.strictEqual((await (await post(`/api/order/${id}/sync`)).json()).status, 'queued');
  // El webhook del mismo cobro no lo entrega dos veces.
  const cap = ppOrder.purchase_units[0].payments.captures[0];
  assert.strictEqual((await paypalWebhook({ event_type: 'PAYMENT.CAPTURE.COMPLETED', resource: cap })).status, 200);
  await serverDelivers('Paypal_MC');
  assert.deepStrictEqual(received, cmdsOf('crate-valentine', 'Paypal_MC'));

  // Un aviso sin firma válida se rechaza.
  assert.strictEqual((await paypalWebhook({ event_type: 'PAYMENT.CAPTURE.REFUNDED', resource: {} }, 'falsa')).status, 400);
  // Reembolso completo desde PayPal: se retira del servidor.
  cap.status = 'REFUNDED';
  const refund = { id: 'REF1', links: [{ rel: 'up', href: `https://api.paypal.test/v2/payments/captures/${cap.id}` }] };
  assert.strictEqual((await paypalWebhook({ event_type: 'PAYMENT.CAPTURE.REFUNDED', resource: refund })).status, 200);
  assert.strictEqual((await (await get(`/api/order/${id}`)).json()).refunded, 'reembolsado');
  const { deliveries } = await (await poll([])).json();
  assert.deepStrictEqual(deliveries.map((d) => [d.kind, d.commands]), [['revocacion', [`tf web sets revoke {uuid} ${id} valentine`, 'tf web monedas retirar {uuid} 100', 'tf web verdes retirar {uuid} 1']]]);
  await poll([], deliveries.map((d) => ({ id: d.id, ok: true })));
});

test('PayPal: un pedido aprobado se cobra también si el comprador no vuelve a la web (webhook)', async () => {
  await seen('Paypal_Web');
  const { id } = await (await post('/api/checkout', { acceptTerms: true, method: 'paypal', productId: 'crate-oni', username: 'Paypal_Web' })).json();
  const ppOrder = [...paypalOrders.values()].find((o) => o.purchase_units[0].custom_id === id);
  ppOrder.approved = true;
  assert.strictEqual((await paypalWebhook({ event_type: 'CHECKOUT.ORDER.APPROVED', resource: { id: ppOrder.id } })).status, 200);
  assert.strictEqual((await (await get(`/api/order/${id}`)).json()).status, 'queued');
  await serverDelivers('Paypal_Web');
});

test('el armario: lo comprado se pone y se quita desde la cuenta, y el puente lo manda al servidor', async () => {
  assert.strictEqual((await get('/api/account/wardrobe')).status, 401);
  const cookie = await register('Armario_MC');
  let view = await (await get('/api/account/wardrobe', { Cookie: cookie })).json();
  assert.deepStrictEqual(view.slots, ['head', 'chest', 'legs', 'feet', 'back']);
  assert.deepStrictEqual(view.owned, []);
  assert.deepStrictEqual(view.equipped, {});
  // Lo que no tiene no se puede poner.
  const chest = { slot: 'chest', set: 'oni', id: 'armor_chestplate' };
  assert.strictEqual((await post('/api/account/wardrobe', chest, cookie)).status, 403);

  // Compra la crate de Oni: su armadura, su casco y sus alas pasan al armario (las armas no).
  const id = await checkout({ productId: 'crate-oni', username: 'Armario_MC' }, cookie);
  await pay(id);
  view = await (await get('/api/account/wardrobe', { Cookie: cookie })).json();
  const keys = view.owned.map((x) => `${x.slot}:${x.set}/${x.id}`).sort();
  assert.deepStrictEqual(keys, [
    'back:oni/wings', 'chest:oni/armor_chestplate', 'feet:oni/armor_boots', 'head:oni/armor_helmet', 'head:oni/helmet', 'legs:oni/armor_leggings',
  ]);
  // Una pieza en un hueco que no es el suyo, no.
  assert.strictEqual((await post('/api/account/wardrobe', { slot: 'head', set: 'oni', id: 'armor_chestplate' }, cookie)).status, 403);
  assert.strictEqual((await post('/api/account/wardrobe', { slot: 'manos', set: 'oni', id: 'sword' }, cookie)).status, 400);

  view = await (await post('/api/account/wardrobe', chest, cookie)).json();
  assert.deepStrictEqual(view.equipped, { chest: { set: 'oni', id: 'armor_chestplate' } });
  view = await (await post('/api/account/wardrobe', { slot: 'back', set: 'oni', id: 'wings' }, cookie)).json();
  assert.deepStrictEqual(Object.keys(view.equipped).sort(), ['back', 'chest']);

  // El servidor recibe lo puesto de los conectados.
  let { wardrobe } = await (await poll(['Armario_MC'])).json();
  assert.strictEqual(wardrobe.length, 1);
  assert.strictEqual(wardrobe[0].uuid, uuidOf('Armario_MC'));
  assert.deepStrictEqual(wardrobe[0].items, { chest: 'oni/armor_chestplate', back: 'oni/wings' });
  const first = wardrobe[0].at;
  ({ wardrobe } = await (await poll(['Otro_MC'])).json());
  assert.deepStrictEqual(wardrobe, []);

  // Quitar una pieza cambia la hora.
  view = await (await post('/api/account/wardrobe', { slot: 'back', id: null }, cookie)).json();
  assert.deepStrictEqual(view.equipped, { chest: { set: 'oni', id: 'armor_chestplate' } });
  ({ wardrobe } = await (await poll(['Armario_MC'])).json());
  assert.deepStrictEqual(wardrobe[0].items, { chest: 'oni/armor_chestplate' });
  assert.ok(wardrobe[0].at > first);
});

test('el TF Pad: el armario y los VFX se cambian desde el juego y el puente lo guarda', async () => {
  const cookie = await register('Pad_MC');
  // Sin compras: el pad no recibe piezas y no puede ponerse nada.
  let res = await (await poll(['Pad_MC'], [], { padWant: [uuidOf('Pad_MC')] })).json();
  assert.deepStrictEqual(res.padData, [{ uuid: uuidOf('Pad_MC'), owned: [] }]);
  const chest = { uuid: uuidOf('Pad_MC'), slot: 'chest', set: 'oni', id: 'armor_chestplate' };
  res = await (await poll(['Pad_MC'], [], { pad: { wardrobe: [chest] } })).json();
  assert.deepStrictEqual(res.wardrobe, []);

  const id = await checkout({ productId: 'crate-oni', username: 'Pad_MC' }, cookie);
  await pay(id);
  res = await (await poll(['Pad_MC'], [], { padWant: [uuidOf('Pad_MC'), uuidOf('Nadie_MC')] })).json();
  assert.strictEqual(res.padData.length, 1);
  assert.ok(res.padData[0].owned.some((x) => x.slot === 'chest' && x.set === 'oni' && x.id === 'armor_chestplate'));

  // Ponerse una pieza desde el pad: la web la guarda y el servidor la recibe en la misma consulta.
  res = await (await poll(['Pad_MC'], [], { pad: { wardrobe: [chest, { ...chest, slot: 'head' }] } })).json();
  assert.deepStrictEqual(res.wardrobe[0].items, { chest: 'oni/armor_chestplate' });
  let view = await (await get('/api/account/wardrobe', { Cookie: cookie })).json();
  assert.deepStrictEqual(view.equipped, { chest: { set: 'oni', id: 'armor_chestplate' } });
  res = await (await poll(['Pad_MC'], [], { pad: { wardrobe: [{ uuid: uuidOf('Pad_MC'), slot: 'chest', id: null }] } })).json();
  assert.deepStrictEqual(res.wardrobe[0].items, {});
  // Un jugador desconectado no se toca.
  await poll(['Otro_MC'], [], { pad: { wardrobe: [chest] } });
  view = await (await get('/api/account/wardrobe', { Cookie: cookie })).json();
  assert.deepStrictEqual(view.equipped, {});

  // VFX: desde el pad solo se equipan los que son suyos (se compran en la web); null los quita.
  const catalog = await (await get('/api/vfx')).json();
  const kill = catalog.kills[0].id;
  res = await (await poll(['Pad_MC'], [], { pad: { vfx: [{ uuid: uuidOf('Pad_MC'), kind: 'kill', id: kill }] } })).json();
  assert.ok(!res.vfx.length || res.vfx[0].kill !== kill);
  env.DB.raw.prepare("INSERT INTO vfx_owned (uuid, item, created_at) VALUES (?, ?, datetime('now'))").run(uuidOf('Pad_MC'), `kill:${kill}`);
  res = await (await poll(['Pad_MC'], [], { pad: { vfx: [{ uuid: uuidOf('Pad_MC'), kind: 'kill', id: kill }, { uuid: uuidOf('Pad_MC'), kind: 'kill', id: 'no-existe' }] } })).json();
  assert.strictEqual(res.vfx[0].kill, kill);
  res = await (await poll(['Pad_MC'], [], { pad: { vfx: [{ uuid: uuidOf('Pad_MC'), kind: 'kill', id: null }] } })).json();
  assert.strictEqual(res.vfx[0].kill, null);

  // Las ventajas de cada rango llegan para «Mi rango».
  assert.ok(res.rankList.every((r) => Array.isArray(r.perks)));
  assert.ok(res.rankList.some((r) => r.perks.length > 0));
});

// --- Skills: clases del TF Client ---
test('el catálogo de Skills: 39 clases con sus skills, iconos y vistas previas, y cada clase se vende como producto', async () => {
  const fs = await import('node:fs');
  const pub = new URL('../public/', import.meta.url);
  const exists = (rel) => fs.existsSync(new URL(rel, pub));
  const cat = await (await get('/api/skills')).json();
  assert.strictEqual(cat.me, null);
  assert.strictEqual(cat.classes.length, 39);
  assert.deepStrictEqual(cat.keys, ['5', '6', '7', '8', '9', '0']);
  assert.ok(Number.isInteger(cat.price) && cat.price >= 50 && Number.isInteger(cat.coinPrice) && cat.coinPrice > 0);
  const groups = new Set(cat.groups.map((g) => g.id));
  const ids = new Set();
  for (const c of cat.classes) {
    assert.match(c.id, /^[a-z_]+$/);
    assert.ok(!ids.has(c.id), `clase repetida: ${c.id}`);
    ids.add(c.id);
    assert.ok(c.name && c.role && c.desc, c.id);
    assert.match(c.color, /^#[0-9a-f]{6}$/i);
    assert.ok(groups.has(c.group), `${c.id}: grupo ${c.group}`);
    assert.ok(Number.isInteger(c.price) && c.price >= 50 && Number.isInteger(c.coinPrice) && c.coinPrice > 0, c.id);
    assert.ok(c.icon && exists(c.icon), `${c.id}: falta el icono`);
    assert.ok(c.skills.length >= 4, c.id);
    // Las skills con tecla van en orden en la barra del mod; las pasivas no llevan tecla
    const actives = c.skills.filter((s) => !s.passive);
    assert.ok(actives.length >= 1 && actives.length <= 8, c.id);
    actives.forEach((s, i) => {
      assert.strictEqual(s.slot, i, `${c.id}/${s.id}`);
      assert.strictEqual(s.key, cat.keys[i]);
      assert.ok(s.cooldown >= 0.2, `${c.id}/${s.id}: cooldown`);
    });
    for (const s of c.skills) {
      assert.ok(s.id && s.name && s.desc, `${c.id}/${s.id}`);
      assert.ok(!s.passive || ['siempre', 'dano'].includes(s.passive), `${c.id}/${s.id}`);
      assert.ok(typeof s.cooldown === 'number' && !('mana' in s)); // el mod no gasta maná: la web no lo enseña
      assert.ok(s.icon && exists(s.icon), `${c.id}/${s.id}: falta el icono`);
      if (s.preview) assert.ok(exists(s.preview), `${c.id}/${s.id}: falta la vista previa`);
    }
    assert.ok(c.skills.some((s) => s.preview), `${c.id}: ninguna vista previa`);
  }
  // En la lista de productos (sin comandos), con el mismo precio
  const list = await (await get('/api/products')).json();
  const skillProducts = list.filter((p) => p.category === 'skills');
  assert.strictEqual(skillProducts.length, 39);
  for (const p of skillProducts) {
    const c = cat.classes.find((x) => x.id === p.skillClass);
    assert.ok(c && p.id === `clase-${c.id}` && p.price === c.price && p.coinPrice === c.coinPrice && p.maxQuantity === 1, p.id);
  }
});

test('Skills: ya no se venden con dinero, solo con Fantastic Coins', async () => {
  await seen('Clase_MC');
  const res = await post('/api/checkout', { acceptTerms: true, productId: 'clase-glacia', username: 'Clase_MC' });
  assert.strictEqual(res.status, 400);
  assert.strictEqual((await res.json()).code, 'coins');
  const list = await (await get('/api/products')).json();
  assert.ok(list.filter((p) => p.category === 'skills').every((p) => p.coinsOnly === true && p.coinPrice === 150000));
});


test('Skills no disponibles (30 oct 2026, hasta reajustar la economía): nadie puede comprarlas', async () => {
  assert.strictEqual(skillsCatalog.onSale, false);
  assert.strictEqual((await (await get('/api/skills')).json()).onSale, false);
  const cookie = await register('Sin_Venta');
  const res = await post('/api/skills/coins', { id: 'thor' }, cookie);
  assert.strictEqual(res.status, 403);
  assert.strictEqual((await res.json()).code, 'not_on_sale');
  // Las pruebas de abajo compran como cuando vuelvan a venderse (tools/skills.py → ON_SALE = True)
  skillsCatalog.onSale = true;
});

test('Skills con monedas: se cobra en el juego con /tf web monedas quitar y, al confirmarlo, la clase es suya y activa', async () => {
  // Sin cuenta, o con una clase que no existe, no
  assert.strictEqual((await post('/api/skills/coins', { id: 'thor' })).status, 401);
  const cookie = await register('Monedas_Skill');
  const uuid = uuidOf('Monedas_Skill');
  assert.strictEqual((await post('/api/skills/coins', { id: 'no_existe' }, cookie)).status, 400);
  const thor = (await (await get('/api/skills')).json()).classes.find((c) => c.id === 'thor');

  // Primer intento: no tiene bastantes monedas. No es suya y se puede volver a intentar.
  let res = await post('/api/skills/coins', { id: 'thor' }, cookie);
  assert.strictEqual(res.status, 200);
  const first = (await res.json()).id;
  assert.match(first, /^COIN[0-9A-F]{24}$/);
  // Mientras está en cola no se puede pedir otra vez
  assert.strictEqual((await post('/api/skills/coins', { id: 'thor' }, cookie)).status, 409);
  assert.deepStrictEqual((await (await get('/api/skills', { Cookie: cookie })).json()).me.pending, ['thor']);
  const [d1] = (await (await poll(['Monedas_Skill'])).json()).deliveries;
  assert.deepStrictEqual(d1.commands, [`tf web monedas quitar {player} ${thor.coinPrice}`]);
  assert.strictEqual(d1.kind, 'tienda-monedas');
  await poll(['Monedas_Skill'], [{ id: d1.id, ok: false, error: 'Monedas_Skill no tiene bastantes monedas.' }]);
  const failed = await (await get(`/api/order/${first}`)).json();
  assert.strictEqual(failed.status, 'delivery_failed');
  assert.strictEqual(failed.kind, 'skills');
  assert.strictEqual(failed.coins, thor.coinPrice);
  assert.match(failed.error, /bastantes monedas/);
  let me = (await (await get('/api/skills', { Cookie: cookie })).json()).me;
  assert.deepStrictEqual([me.owned, me.active, me.pending], [[], null, []]);

  // Segundo intento: el servidor cobra y lo confirma; en esa misma respuesta ya va la clase
  const second = (await (await post('/api/skills/coins', { id: 'thor' }, cookie)).json()).id;
  const [d2] = (await (await poll(['Monedas_Skill'])).json()).deliveries;
  const ack = await (await poll(['Monedas_Skill'], [{ id: d2.id, ok: true }])).json();
  assert.deepStrictEqual(ack.skills.map((s) => [s.uuid, s.clase]), [[uuid, 'thor']]);
  const order = await (await get(`/api/order/${second}`)).json();
  assert.deepStrictEqual([order.status, order.kind, order.product, order.coins], ['delivered', 'skills', 'Clase Thor', thor.coinPrice]);
  me = (await (await get('/api/skills', { Cookie: cookie })).json()).me;
  assert.deepStrictEqual([me.owned, me.active, me.pending], [['thor'], 'thor', []]);
  assert.strictEqual((await post('/api/skills/coins', { id: 'thor' }, cookie)).status, 409);
});

test('Skills: con varias clases se elige la activa en la web (solo una) y el servidor recibe la última', async () => {
  received.length = 0;
  const cookie = await register('Varias_MC');
  const uuid = uuidOf('Varias_MC');
  assert.strictEqual((await post('/api/skills/active', { id: 'mago' })).status, 401);
  // No se puede activar una que no tiene, ni una que no existe
  assert.strictEqual((await post('/api/skills/active', { id: 'mago' }, cookie)).status, 403);
  assert.strictEqual((await post('/api/skills/active', { id: 'nada' }, cookie)).status, 400);

  // Compra Mago y después Zephyr, con monedas: la última comprada queda activa
  await post('/api/skills/coins', { id: 'mago' }, cookie);
  const first = (await (await poll(['Varias_MC'])).json()).deliveries;
  await poll(['Varias_MC'], first.map((d) => ({ id: d.id, ok: true })));
  await post('/api/skills/coins', { id: 'zephyr' }, cookie);
  const pending = (await (await poll(['Varias_MC'])).json()).deliveries;
  const coin = pending.find((d) => d.kind === 'tienda-monedas');
  const acks = pending.map((d) => ({ id: d.id, ok: true }));
  let res = await (await poll(['Varias_MC'], acks)).json();
  assert.deepStrictEqual(res.skills.map((s) => s.clase), ['zephyr']);
  const t1 = res.skills[0].at;

  // Cambia a Mago: una sola clase activa, con hora más nueva
  let { me } = await (await post('/api/skills/active', { id: 'mago' }, cookie)).json();
  assert.deepStrictEqual([me.owned.sort(), me.active], [['mago', 'zephyr'], 'mago']);
  res = await (await poll(['Varias_MC'])).json();
  assert.deepStrictEqual(res.skills.map((s) => [s.uuid, s.clase]), [[uuid, 'mago']]);
  const t2 = res.skills[0].at;
  assert.ok(t2 > t1);

  // Una confirmación repetida del cobro de Zephyr (el mod la reenvía si se perdió) no la vuelve a activar
  res = await (await poll(['Varias_MC'], [{ id: coin.id, ok: true }])).json();
  assert.deepStrictEqual(res.skills.map((s) => [s.clase, s.at]), [['mago', t2]]);

  // Quitar la clase: el servidor recibe null
  ({ me } = await (await post('/api/skills/active', { id: null }, cookie)).json());
  assert.strictEqual(me.active, null);
  res = await (await poll(['Varias_MC'])).json();
  assert.strictEqual(res.skills[0].clase, null);
  assert.ok(res.skills[0].at > t2);
  // Elegir la que ya está activa no cambia la hora (el servidor no la vuelve a aplicar)
  await post('/api/skills/active', { id: 'zephyr' }, cookie);
  const t3 = (await (await poll(['Varias_MC'])).json()).skills[0].at;
  await post('/api/skills/active', { id: 'zephyr' }, cookie);
  assert.strictEqual((await (await poll(['Varias_MC'])).json()).skills[0].at, t3);
  assert.deepStrictEqual(received, []);

  // El puente manda también las clases que tiene (el TF Pad las enseña) y desde el pad se cambia la activa
  res = await (await poll(['Varias_MC'])).json();
  assert.deepStrictEqual(res.skills[0].owned.sort(), ['mago', 'zephyr']);
  res = await (await poll(['Varias_MC'], [], { pad: { skills: [{ uuid, clase: 'mago' }] } })).json();
  assert.strictEqual(res.skills[0].clase, 'mago');
  assert.ok(res.skills[0].at > t3);
  // Una que no es suya no se pone; null la quita
  res = await (await poll(['Varias_MC'], [], { pad: { skills: [{ uuid, clase: 'glacia' }] } })).json();
  assert.strictEqual(res.skills[0].clase, 'mago');
  res = await (await poll(['Varias_MC'], [], { pad: { skills: [{ uuid, clase: null }] } })).json();
  assert.strictEqual(res.skills[0].clase, null);
  assert.strictEqual((await (await get('/api/skills', { Cookie: cookie })).json()).me.active, null);
});

test('cada firma de los Términos y cada recibo llegan por correo; el recibo en PDF se descarga; una disputa fuera de plazo banea la IP 15 días (6 en las compras de antes)', async () => {
  mails.length = 0;
  await seen('Recibo_MC');
  const order = await checkout({ productId: 'crate-oni', username: 'Recibo_MC' });
  // La firma al pagar llega al correo de registros, con el pedido, la versión y la IP
  const firma = mails.find((m) => m.subject.startsWith('Firma de los Términos') && m.text.includes(order));
  assert.ok(firma, 'llega la firma');
  assert.deepStrictEqual(firma.to, ['duenio@correo.test']);
  assert.match(firma.text, /Versión de los Términos: \d{4}-\d{2}-\d{2}/);
  assert.match(firma.text, /IP: /);

  // Sin pagar no hay recibo; al pagar, el PDF (el de la página del pedido) y la copia por correo con el PDF adjunto
  assert.strictEqual((await get(`/api/order/${order}/receipt`)).status, 404);
  await pay(order);
  const res = await get(`/api/order/${order}/receipt`);
  assert.strictEqual(res.status, 200);
  assert.strictEqual(res.headers.get('Content-Type'), 'application/pdf');
  assert.match(res.headers.get('Content-Disposition'), new RegExp(`attachment; filename="recibo-${order}.pdf"`));
  const pdf = Buffer.from(await res.arrayBuffer()).toString('latin1');
  assert.ok(pdf.startsWith('%PDF-1.4') && pdf.trimEnd().endsWith('%%EOF'));
  assert.ok(pdf.includes(order) && pdf.includes('Recibo_MC') && pdf.includes('24 horas') && pdf.includes('baneo de IP') && !pdf.includes('5 días hábiles'));
  assert.ok(!pdf.includes('IP de la aceptación'), 'el recibo del jugador no lleva su IP');
  const recibo = mails.find((m) => m.subject.startsWith(`Recibo ${order}`));
  assert.ok(recibo, 'llega la copia del recibo');
  assert.match(recibo.text, /IP de la aceptación: /);
  assert.strictEqual(recibo.attachments[0].filename, `recibo-${order}.pdf`);
  assert.ok(Buffer.from(recibo.attachments[0].content, 'base64').toString('latin1').startsWith('%PDF'));
  await serverDelivers('Recibo_MC');

  // Disputa dentro del plazo: se retira, sin baneo
  const dispute = (id) => signedEvent({ type: 'charge.dispute.created', data: { object: { id: `dp_${id}`, payment_intent: sessionOf(id).payment_intent } } });
  await dispute(order);
  let { deliveries } = await (await poll([])).json();
  assert.deepStrictEqual(deliveries.map((d) => d.kind), ['revocacion']);
  await poll([], deliveries.map((d) => ({ id: d.id, ok: true })));
  assert.ok(mails.some((m) => m.subject === `DISPUTA · pedido ${order} · Recibo_MC` && m.text.includes('dentro del plazo')));

  // Disputa pasadas las 24 horas de la garantía: se retira y baneo de IP de 15 días (aunque no esté conectado)
  const late = await checkout({ productId: 'crate-valentine', username: 'Recibo_MC' });
  await pay(late);
  await serverDelivers('Recibo_MC');
  const old = new Date(Date.now() - 14 * 86400000).toISOString();
  const twoDays = new Date(Date.now() - 2 * 86400000).toISOString();
  await env.DB.prepare('UPDATE orders SET delivered_at = ?, paid_at = ? WHERE id = ?').bind(twoDays, twoDays, late).run();
  await dispute(late);
  ({ deliveries } = await (await poll([])).json());
  assert.deepStrictEqual(deliveries.map((d) => d.kind), ['revocacion', 'penalizacion']);
  const ban = deliveries.find((d) => d.kind === 'penalizacion');
  assert.strictEqual(ban.order, late);
  assert.match(ban.commands[0], new RegExp(`^tempbanip \\{player\\} 15d Reembolso injustificado del pedido ${late} .*24 horas`));
  assert.match((await (await get(`/api/order/${late}`)).json()).refunded, /fuera de plazo · baneo de IP de 15 días/);
  const aviso = mails.find((m) => m.subject === `DISPUTA · pedido ${late} · Recibo_MC`);
  assert.ok(aviso.text.includes('reembolso injustificado') && aviso.attachments[0].filename === `recibo-${late}.pdf`);
  // (30 oct 2026) el expediente de la disputa va adjunto y las pruebas quedan rellenadas en Stripe, sin enviar
  const dossier = aviso.attachments.find((a) => a.filename === `disputa-${late}.pdf`);
  assert.ok(dossier, 'llega el expediente de la disputa');
  const dossierText = Buffer.from(dossier.content, 'base64').toString('latin1');
  assert.ok(dossierText.includes('Dispute evidence') && dossierText.includes(late) && dossierText.includes('outside the refund'));
  const evidence = disputeEvidence.get(`dp_${late}`);
  assert.ok(evidence, 'las pruebas llegan a Stripe');
  assert.strictEqual(evidence.submit, 'false');
  assert.match(evidence['evidence[access_activity_log]'], new RegExp(`order ${late} created for Minecraft account Recibo_MC`));
  assert.match(evidence['evidence[refund_refusal_explanation]'], /outside the refund window/);
  assert.match(evidence['evidence[uncategorized_text]'], /Terms and refund policy accepted before paying/);
  // (30 oct 2026) cita la copia archivada de los Términos de esa compra
  assert.match(aviso.text, /Texto de esos Términos: https:\/\/tierrasfantásticas\.store\/archivo-legal\/terminos-2026-10-30\.1(-[a-z])?$/m);
  assert.match(evidence['evidence[refund_policy_disclosure]'], /archivo-legal\/terminos-2026-10-30\.1(-[a-z])? — publication verifiable at https:\/\/github\.com\/modelqdeluxe-ops\/ss\/commit\//);
  // Otro aviso de la misma disputa no duplica el baneo
  await poll([], deliveries.map((d) => ({ id: d.id, ok: true })));
  await dispute(late);
  assert.deepStrictEqual((await (await poll([])).json()).deliveries, []);

  // Un reembolso hecho por el dueño fuera de plazo no banea
  const refunded = await checkout({ productId: 'crate-luminite', username: 'Recibo_MC' });
  await pay(refunded);
  await serverDelivers('Recibo_MC');
  await env.DB.prepare('UPDATE orders SET delivered_at = ? WHERE id = ?').bind(old, refunded).run();
  await signedEvent({ type: 'charge.refunded', data: { object: { id: `ch_${refunded}`, payment_intent: sessionOf(refunded).payment_intent, refunded: true } } });
  ({ deliveries } = await (await poll([])).json());
  assert.deepStrictEqual(deliveries.map((d) => d.kind), ['revocacion']);
  await poll([], deliveries.map((d) => ({ id: d.id, ok: true })));

  // Compras de antes del 30 oct 2026 (Términos 2026-10-26.1): 5 días hábiles y baneo de 6 días
  const before = await checkout({ productId: 'crate-oni', username: 'Recibo_MC' });
  await pay(before);
  await serverDelivers('Recibo_MC');
  await env.DB.prepare('UPDATE orders SET terms_version = ?, delivered_at = ?, paid_at = ? WHERE id = ?').bind('2026-10-26.1', twoDays, twoDays, before).run();
  await dispute(before);
  ({ deliveries } = await (await poll([])).json());
  assert.deepStrictEqual(deliveries.map((d) => d.kind), ['revocacion'], 'con 2 días sigue dentro de sus 5 días hábiles: sin baneo');
  await poll([], deliveries.map((d) => ({ id: d.id, ok: true })));
  const before2 = await checkout({ productId: 'crate-valentine', username: 'Recibo_MC' });
  await pay(before2);
  await serverDelivers('Recibo_MC');
  await env.DB.prepare('UPDATE orders SET terms_version = ?, delivered_at = ?, paid_at = ? WHERE id = ?').bind('2026-10-26.1', old, old, before2).run();
  await dispute(before2);
  ({ deliveries } = await (await poll([])).json());
  assert.deepStrictEqual(deliveries.map((d) => d.kind), ['revocacion', 'penalizacion']);
  assert.match(deliveries.find((d) => d.kind === 'penalizacion').commands[0], new RegExp(`^tempbanip \\{player\\} 6d Reembolso injustificado del pedido ${before2} .*5 días hábiles`));
  const receipt = Buffer.from(await (await get(`/api/order/${before2}/receipt`)).arrayBuffer()).toString('latin1');
  assert.ok(receipt.includes('5 días hábiles') && receipt.includes('6 días'), 'su recibo lleva la política de entonces');
  assert.ok(receipt.includes('archivo-legal/terminos-2026-10-26.1-b'), 'y el enlace a la copia archivada de esos Términos');
});

test('comprar como regalo: con la cuenta de quien paga, para otro jugador verificado, con su mensaje y la sanción para quien paga', async () => {
  received.length = 0;
  discordCalls.length = 0;
  announcements.length = 0;
  mails.length = 0;
  await seen('Regalado_MC');
  const cookie = cookieValue(await post('/api/auth/login', { name: 'Alex_MC', password: 'contraseña-segura' }), 'tf_session');

  // Sin cuenta no se regala; a uno mismo, tampoco; el que lo recibe tiene que haber entrado al servidor
  const gift = { productId: 'crate-dracula', username: 'Regalado_MC', gift: true, message: '¡Feliz\ncumple! §4rojo' };
  let res = await post('/api/checkout', { acceptTerms: true, ...gift });
  assert.deepStrictEqual([res.status, (await res.json()).code], [401, 'login']);
  res = await post('/api/checkout', { acceptTerms: true, ...gift, username: 'Alex_MC' }, cookie);
  assert.deepStrictEqual([res.status, (await res.json()).code], [400, 'gift_self']);
  res = await post('/api/checkout', { acceptTerms: true, ...gift, username: 'Nadie_MC' }, cookie);
  assert.strictEqual(res.status, 404);

  const id = await checkout(gift, cookie);
  assert.match(sessionOf(id).line_items?.[0]?.price_data?.product_data?.description || JSON.stringify(sessionOf(id)), /Regalo de Alex_MC para Regalado_MC/);
  // La firma de los Términos es de quien paga
  const firma = mails.find((m) => m.subject.startsWith('Firma de los Términos') && m.text.includes(id));
  assert.match(firma.subject, /Alex_MC · al pagar un regalo/);
  await pay(id);
  await serverDelivers('Regalado_MC');
  // Lo recibe Regalado_MC, con el aviso de quién se lo regala y su mensaje (sin saltos de línea ni códigos de color)
  const notice = received.find((c) => c.startsWith('tellraw Regalado_MC '));
  assert.ok(notice, 'aviso del regalo en el juego');
  const parts = JSON.parse(notice.slice('tellraw Regalado_MC '.length));
  assert.match(parts.map((p) => p.text || '').join(''), /^¡Regalo! Alex_MC te ha regalado Crate Drácula «¡Feliz cumple! 4rojo»$/);
  // El rol no va al Discord de quien paga; el anuncio dice que es un regalo
  assert.ok(!discordCalls.some((c) => c.url.includes('/members/111111111111111111/')));
  assert.match(announcements[0].embeds[0].description, /\*\*Alex_MC\*\* le ha regalado a \*\*Regalado_MC\*\* \*\*Crate Drácula\*\*/);
  // Los dos lo ven: el que lo recibe (es suyo) y quien lo pagó, en «Mis compras»
  const order = await (await get(`/api/order/${id}`)).json();
  assert.deepStrictEqual([order.username, order.gift], ['Regalado_MC', { from: 'Alex_MC', message: '¡Feliz cumple! 4rojo' }]);
  const { orders } = await (await get('/api/account/orders', { Cookie: cookie })).json();
  assert.ok(orders.some((o) => o.id === id && o.gift?.from === 'Alex_MC'));
  const pdf = Buffer.from(await (await get(`/api/order/${id}/receipt`)).arrayBuffer()).toString('latin1');
  assert.ok(pdf.includes('Regalo de Alex_MC') && pdf.includes('Jugador que lo recibe'));

  // Disputa fuera de plazo: se retira el regalo y el baneo es para quien pagó
  const old = new Date(Date.now() - 14 * 86400000).toISOString();
  await env.DB.prepare('UPDATE orders SET delivered_at = ?, paid_at = ? WHERE id = ?').bind(old, old, id).run();
  await signedEvent({ type: 'charge.dispute.created', data: { object: { id: `dp_${id}`, payment_intent: sessionOf(id).payment_intent } } });
  const { deliveries } = await (await poll([])).json();
  const ban = deliveries.find((d) => d.kind === 'penalizacion');
  assert.match(ban.commands[0], new RegExp(`^tempbanip Alex_MC 15d Reembolso injustificado del pedido ${id}`));
  await poll([], deliveries.map((d) => ({ id: d.id, ok: true })));
});

test('los roles de los rangos se buscan por su nombre en el Discord (exacto o sin adornos) y los ids valen tal cual', async () => {
  const { Discord } = await import('../src/discord.js');
  const real = globalThis.fetch;
  let calls = 0;
  globalThis.fetch = async (url) => {
    calls++;
    assert.match(String(url), /\/guilds\/123456789012345678\/roles$/);
    const roles = [
      { id: '111111111111111111', name: '🌳┆MORTAL' },
      { id: '222222222222222222', name: '🛡️ INMORTAL' },
      { id: '333333333333333333', name: '🌋┆FANTÁSTICO' },
    ];
    return new Response(JSON.stringify(roles), { status: 200, headers: { 'Content-Type': 'application/json' } });
  };
  try {
    const d = new Discord({ botToken: 'b', guildId: '123456789012345678' });
    assert.deepEqual(await d.resolveRoles(['🌳┆MORTAL']), ['111111111111111111']);
    // sin el «┆» ni el mismo emoji: se encuentra igual
    assert.deepEqual(await d.resolveRoles(['🛡️┆INMORTAL']), ['222222222222222222']);
    assert.deepEqual(await d.resolveRoles(['🌋┆FANTÁSTICO', '999999999999999999']), ['999999999999999999', '333333333333333333']);
    // uno que no existe no se inventa; los marcadores de antes se ignoran
    assert.deepEqual(await d.resolveRoles(['🌠┆CELESTIAL', 'ID_ROL_MORTAL']), []);
    assert.equal(calls, 1, 'la lista de roles se pide una vez y se recuerda');
  } finally {
    globalThis.fetch = real;
  }
});

test('el servidor empieza limpio: la web reinicia a esos jugadores (clase, rango, efectos, armario y pedidos)', async () => {
  const cookie = await register('Limpio_MC');
  const uuid = uuidOf('Limpio_MC');
  await post('/api/skills/coins', { id: 'thor' }, cookie);
  const [d] = (await (await poll(['Limpio_MC'])).json()).deliveries;
  await poll(['Limpio_MC'], [{ id: d.id, ok: true }]);
  let me = (await (await get('/api/skills', { Cookie: cookie })).json()).me;
  assert.deepStrictEqual([me.owned, me.active], [['thor'], 'thor']);

  // TF Client 1.3.120: el servidor manda en «reset» los que tenían datos (TFDataReset) y borra su lista al ver resetDone
  const res = await (await poll(['Limpio_MC'], [], { reset: [uuid, 'no-es-un-uuid'] })).json();
  assert.strictEqual(res.resetDone, 1);
  me = (await (await get('/api/skills', { Cookie: cookie })).json()).me;
  assert.deepStrictEqual([me.owned, me.active], [[], null]);
  assert.strictEqual((await post('/api/skills/coins', { id: 'thor' }, cookie)).status, 200, 'se puede volver a comprar');
});

// --- Panel de staff (/staff) ---
test('panel de staff: solo con la clave; ve los pedidos y reembolsa a mano (también lo gratis) quitando el rango y su rol', async () => {
  const staff = (path, { key = 'clave-del-staff', ip = '198.51.100.7', ...init } = {}) =>
    call(path, { ...init, headers: { ...(init.headers || {}), 'CF-Connecting-IP': ip, ...(key ? { Authorization: `Bearer ${key}` } : {}) } });
  const refund = (id, opts) => staff('/api/staff/refund', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ id }), ...opts });

  // Sin la clave no se ve nada; sin ADMIN_KEY en Cloudflare el panel está apagado; muchos fallos bloquean esa IP.
  assert.strictEqual((await staff('/api/staff/orders', { key: null })).status, 401);
  assert.strictEqual((await refund('GIFTX', { key: 'otra-clave' })).status, 401);
  const saved = env.ADMIN_KEY;
  delete env.ADMIN_KEY;
  assert.strictEqual((await staff('/api/staff/orders')).status, 503);
  env.ADMIN_KEY = saved;
  for (let i = 0; i < 8; i++) await staff('/api/staff/orders', { key: `mala-${i}`, ip: '203.0.113.50' });
  assert.strictEqual((await staff('/api/staff/orders', { ip: '203.0.113.50' })).status, 429);

  // Un jugador con su Discord: dos rangos (gratis durante una prueba de la tienda, de pago si no) y una crate pagada.
  received.length = 0;
  const profile = discordProfile;
  discordProfile = { id: '666666666666666666', username: 'staffer', global_name: null, avatar: null };
  let cookie;
  try {
    cookie = await register('Staff_MC');
    await viaDiscord(cookie);
  } finally {
    discordProfile = profile;
  }
  const getRank = async (productId) => {
    if (products.find((p) => p.id === productId).price === 0) return (await (await post('/api/claim', { productId }, cookie)).json()).id;
    const id = await checkout({ productId, username: 'Staff_MC' }, cookie);
    await pay(id);
    return id;
  };
  const inmortal = await getRank('rango-inmortal');
  const magico = await getRank('rango-magico');
  const crate = await checkout({ productId: 'crate-valentine', username: 'Staff_MC' });
  await pay(crate);
  await serverDelivers('Staff_MC');
  assert.strictEqual((await (await get('/api/player/Staff_MC')).json()).rank.id, 'rango-magico');

  const { orders } = await (await staff('/api/staff/orders')).json();
  const row = (id) => orders.find((o) => o.id === id);
  assert.ok([inmortal, magico, crate].every((id) => row(id)?.username === 'Staff_MC' && row(id).refundable), 'salen los tres, reembolsables');
  assert.strictEqual(row(crate).product, products.find((p) => p.id === 'crate-valentine').name);

  // (30 oct 2026) «Pruebas»: el expediente en PDF para responder una disputa, solo con la clave y de pedidos pagados
  const pdfOf = (id, opts) => staff('/api/staff/dispute-pdf', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ id }), ...opts });
  assert.strictEqual((await pdfOf(crate, { key: 'otra-clave' })).status, 401);
  const dossier = await pdfOf(crate);
  assert.strictEqual(dossier.status, 200);
  assert.strictEqual(dossier.headers.get('Content-Type'), 'application/pdf');
  assert.ok(Buffer.from(await dossier.arrayBuffer()).toString('latin1').includes('Dispute evidence'));
  assert.ok((await (await staff('/api/staff/orders')).json()).orders.find((o) => o.id === crate).disputable);

  // Reembolsar: del rango más alto al más bajo. Al final no le queda rango ni rol, y lo de la crate se retira.
  assert.strictEqual((await refund('NO_EXISTE')).status, 404);
  mails.length = 0;
  assert.strictEqual((await refund(magico)).status, 200);
  discordCalls.length = 0;
  assert.strictEqual((await refund(inmortal)).status, 200);
  assert.ok(discordCalls.some((c) => c.method === 'DELETE' && c.url === `/guilds/333333333333333333/members/666666666666666666/roles/${ROLE_ID}`), 'quita el rol de Discord');
  assert.strictEqual((await refund(crate)).status, 200);
  assert.strictEqual((await refund(crate)).status, 409);
  assert.strictEqual((await (await get('/api/player/Staff_MC')).json()).rank, null);
  const { deliveries } = await (await poll([])).json();
  const commands = deliveries.flatMap((d) => d.commands);
  for (const c of ['lp user {uuid} parent remove magico', 'lp user {uuid} parent remove inmortal', `tf web sets revoke {uuid} ${crate} valentine`]) {
    assert.ok(commands.includes(c), `falta «${c}»`);
  }
  await poll([], deliveries.map((d) => ({ id: d.id, ok: true })));
  assert.ok((await (await staff('/api/staff/orders')).json()).orders.every((o) => o.username !== 'Staff_MC' || !o.refundable));
  // Al dueño le llega el aviso de cada uno
  assert.ok(mails.some((m) => m.subject === `Reembolso · pedido ${crate} · Staff_MC` && m.text.includes('panel de staff')));

  // Lo gratis reembolsado se puede volver a reclamar (con otro número); mientras lo tiene, no.
  const claim = () => post('/api/claim', { productId: 'regalo-diamantes' }, cookie);
  const first = (await (await claim()).json()).id;
  assert.strictEqual((await claim()).status, 409);
  assert.strictEqual((await refund(first)).status, 200);
  const again = await claim();
  assert.strictEqual(again.status, 200);
  const second = (await again.json()).id;
  assert.ok(second !== first && /^GIFT[0-9A-Z]{2,4}[0-9A-F]{30}01$/.test(second), second);
  assert.strictEqual((await claim()).status, 409);
  await serverDelivers('Staff_MC');
  assert.strictEqual((await (await get(`/api/order/${second}`)).json()).status, 'delivered');

  // La IP del servidor (la de la web y la de TF Client): se cambia desde el panel y /api/config la da
  const setIp = (ip, key) => staff('/api/staff/server', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ip }), key });
  assert.deepStrictEqual(await (await staff('/api/staff/server')).json(), { ip: '216.163.187.40:19015', default: '216.163.187.40:19015' });
  assert.strictEqual((await setIp('nueva.ip.test:25570', 'otra-clave')).status, 401);
  assert.strictEqual((await setIp('evil.com/x')).status, 400);
  assert.strictEqual((await setIp('a:70000')).status, 400);
  assert.strictEqual((await setIp('Nueva.IP.test:25570')).status, 200);
  assert.strictEqual((await (await get('/api/config')).json()).serverIp, 'nueva.ip.test:25570');
  assert.strictEqual((await setIp('')).status, 200);

  // Botón «Test» de los rangos: uno de pago se reclama gratis solo con la clave del panel de staff
  assert.strictEqual((await post('/api/claim', { productId: 'rango-mortal' }, cookie)).status, 400);
  const testClaim = (key) => call('/api/claim', { method: 'POST', body: JSON.stringify({ productId: 'rango-mortal' }),
    headers: { 'Content-Type': 'application/json', Cookie: cookie, Authorization: `Bearer ${key}`, 'CF-Connecting-IP': '198.51.100.7' } });
  assert.strictEqual((await testClaim('otra-clave')).status, 401);
  assert.strictEqual((await testClaim('clave-del-staff')).status, 200);

  // Probar desde el panel cualquier producto (una crate) a un jugador: se entrega y se puede reembolsar
  const tryIt = (body, key) => staff('/api/staff/test', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body), key });
  assert.strictEqual((await tryIt({ productId: 'crate-valentine', username: 'Staff_MC' }, 'otra-clave')).status, 401);
  assert.strictEqual((await tryIt({ productId: 'crate-valentine', username: 'Nadie_MC' })).status, 404);
  const tried = await tryIt({ productId: 'crate-valentine', username: 'Staff_MC' });
  assert.strictEqual(tried.status, 200);
  const triedId = (await tried.json()).id;
  assert.match(triedId, /^TEST[0-9A-Z]+$/);
  assert.strictEqual((await refund(triedId)).status, 200);
  assert.strictEqual((await (await get('/api/config')).json()).serverIp, '216.163.187.40:19015');
});


// Un aviso de Mercado Pago firmado como lo firma él (x-signature «ts=…,v1=HMAC(id;request-id;ts)»).
async function mpWebhook(paymentId, secret = 'mp-secreto') {
  const ts = String(Math.floor(Date.now() / 1000));
  const manifest = `id:${paymentId};request-id:req-1;ts:${ts};`;
  const v1 = createHmac('sha256', secret).update(manifest).digest('hex');
  return call(`/webhook/mercadopago?data.id=${paymentId}&type=payment`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'x-signature': `ts=${ts},v1=${v1}`, 'x-request-id': 'req-1' },
    body: JSON.stringify({ type: 'payment', action: 'payment.updated', data: { id: paymentId } }),
  });
}

test('Mercado Pago (cuenta de México): cobra en pesos al cambio del día, entrega al aprobarse y retira con un reembolso', async () => {
  const config = await (await get('/api/config')).json();
  assert.strictEqual(config.mercadopagoEnabled, true);
  assert.strictEqual(config.mpRate, 18.68);
  await seen('Mp_Comprador');
  const res = await post('/api/checkout', { acceptTerms: true, method: 'mercadopago', productId: 'crate-valentine', username: 'Mp_Comprador' });
  const { id, url } = await res.json();
  assert.strictEqual(res.status, 200);
  assert.match(url, /^https:\/\/www\.mercadopago\.test\/checkout\?pref_id=pref-/);
  const pref = [...mpPrefs.values()].find((p) => p.external_reference === id);
  const usd = products.find((p) => p.id === 'crate-valentine').price;
  const mxn = Math.ceil(usd * 18.68 - 1e-9) / 100;
  assert.strictEqual(pref.items[0].currency_id, 'MXN');
  assert.strictEqual(pref.items[0].unit_price, mxn);
  assert.ok(pref.items[0].description.includes('Para Mp_Comprador'));
  assert.strictEqual(pref.back_urls.success, `http://tienda.test/success?order=${id}`);

  // sin pagar, nada; un rechazo no cierra el pedido (puede reintentar)
  assert.strictEqual((await (await post(`/api/order/${id}/sync`)).json()).status, 'pending');
  mpPayments.set('9001', { id: 9001, status: 'rejected', external_reference: id, transaction_amount: mxn, currency_id: 'MXN' });
  assert.strictEqual((await (await post(`/api/order/${id}/sync`)).json()).status, 'pending');
  // aprobado: se entrega (por la página de vuelta o por el aviso)
  mpPayments.set('9002', { id: 9002, status: 'approved', external_reference: id, transaction_amount: mxn, currency_id: 'MXN' });
  assert.strictEqual((await mpWebhook('9002', 'otra-clave')).status, 401);
  assert.strictEqual((await mpWebhook('9002')).status, 200);
  assert.strictEqual((await (await get(`/api/order/${id}`)).json()).status, 'queued');
  // reembolsado en Mercado Pago: se retira lo entregado
  mpPayments.set('9002', { ...mpPayments.get('9002'), status: 'refunded' });
  assert.strictEqual((await mpWebhook('9002')).status, 200);
  assert.strictEqual((await (await get(`/api/order/${id}`)).json()).refunded, 'reembolsado');

  // el aviso de prueba que manda Mercado Pago al guardar el webhook (un pago que no existe): recibido
  assert.strictEqual((await mpWebhook('123456')).status, 200);
  // un importe que no es el del pedido no entrega
  const { id: id2 } = await (await post('/api/checkout', { acceptTerms: true, method: 'mercadopago', productId: 'crate-valentine', username: 'Mp_Comprador' })).json();
  mpPayments.set('9003', { id: 9003, status: 'approved', external_reference: id2, transaction_amount: 1, currency_id: 'MXN' });
  assert.strictEqual((await (await post(`/api/order/${id2}/sync`)).json()).status, 'error');
});
