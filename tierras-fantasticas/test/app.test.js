import { test, before, after } from 'node:test';
import assert from 'node:assert';
import http from 'node:http';
import { createHmac } from 'node:crypto';
import products from '../config/products.json' with { type: 'json' };
import worker from '../src/index.js';
import { createD1 } from './d1.js';

const servers = [];
let env;
// Lo que el servidor de Minecraft (puente) ha ejecutado.
const received = [];
const BRIDGE_SECRET = 'clave-del-puente';

// --- API de Stripe simulada ---
const stripeSessions = new Map();
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
let memberExists = true;
let failRoles = false;

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
    if (req.url === '/users/@me') return send(200, { id: '111111111111111111', username: 'alex', global_name: 'Alex', avatar: null });
    if (req.url === '/webhook') {
      announcements.push(JSON.parse(raw));
      return send(204);
    }
    if (req.headers.authorization !== 'Bot bot-token') return send(401, { message: '401: Unauthorized' });
    if (/^\/guilds\/\d+\/members\/\d+$/.test(req.url) && req.method === 'PUT') {
      return memberExists ? send(204) : send(201, { user: {} });
    }
    if (/^\/guilds\/\d+\/members\/\d+\/roles\/\d+$/.test(req.url) && req.method === 'PUT') {
      return failRoles ? send(403, { message: 'Missing Permissions' }) : send(204);
    }
    send(404, { message: 'Unknown' });
  });
  servers.push(server);
  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve(server.address().port)));
}

const ROLE_ID = '222222222222222222';
const quietLog = { log() {}, warn() {}, error() {} };

// Archivos estáticos simulados (en Cloudflare los sirve el propio Workers).
const ASSETS = { fetch: async (req) => new Response(`estático ${new URL(req.url).pathname}`) };

before(async () => {
  const [stripePort, discordPort] = await Promise.all([startFakeStripe(), startFakeDiscord()]);
  // Rol de Discord real (formato snowflake) para el rango de pruebas.
  products.find((p) => p.id === 'rango-hechicero').discordRoles = [ROLE_ID];
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

async function checkout(body, cookie) {
  const res = await post('/api/checkout', body, cookie);
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
  assert.strictEqual((await post('/api/checkout', { productId: 'no-existe', username: 'Steve' })).status, 400);
  assert.strictEqual((await post('/api/checkout', { productId: 'monedas-10000', username: 'a; op yo' })).status, 400);
  assert.strictEqual((await post('/api/checkout', { productId: 'monedas-10000', username: 'Steve', quantity: 50 })).status, 400);
  assert.strictEqual((await post('/api/checkout', { productId: 'rango-dragon', username: 'Steve', quantity: 2 })).status, 400);
  const unknown = await post('/api/checkout', { productId: 'monedas-10000', username: 'Fantasma' });
  assert.strictEqual(unknown.status, 404);
  assert.strictEqual((await unknown.json()).code, 'unknown_player');
  // El UUID que vio el navegador tiene que ser el del servidor.
  const other = await post('/api/checkout', { productId: 'monedas-10000', username: 'Steve', uuid: uuidOf('Otro') });
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
  const id = await checkout({ productId: 'llaves-legendarias-3', username: 'Paciente' });
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
  const id = await checkout({ productId: 'llaves-epicas-5', username: 'Prisa' });
  assert.strictEqual((await (await post(`/api/order/${id}/sync`)).json()).status, 'pending');
  Object.assign(sessionOf(id), { status: 'complete', payment_status: 'paid', payment_intent: 'pi_prisa' });
  assert.strictEqual((await (await post(`/api/order/${id}/sync`)).json()).status, 'queued');
  await serverDelivers('Prisa');
  assert.deepStrictEqual(received, ['crate key give Prisa epica 5']);
});

test('no se entrega si el importe cobrado no coincide', async () => {
  await seen('Tramposo');
  const id = await checkout({ productId: 'rango-dragon', username: 'Tramposo' });
  assert.strictEqual((await pay(id, { amount_total: 1 })).status, 'error');
  assert.deepStrictEqual(await serverDelivers('Tramposo'), []);
});

test('pagos que tardan (transferencia) se entregan cuando Stripe confirma el cobro', async () => {
  received.length = 0;
  await seen('Lento_Pago');
  const id = await checkout({ productId: 'llaves-legendarias-3', username: 'Lento_Pago' });
  assert.strictEqual((await pay(id, { payment_status: 'unpaid' })).status, 'awaiting_payment');
  assert.deepStrictEqual(await serverDelivers('Lento_Pago'), []);
  assert.strictEqual((await pay(id, {}, 'checkout.session.async_payment_succeeded')).status, 'queued');
  await serverDelivers('Lento_Pago');
  assert.deepStrictEqual(received, ['crate key give Lento_Pago legendaria 3']);
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

test('comprar llaves de un crate las entrega y el catálogo trae su contenido', async () => {
  const list = await (await get('/api/products')).json();
  const crate = list.find((p) => p.id === 'crate-necros');
  assert.strictEqual(crate.category, 'crates');
  assert.ok(crate.image && crate.models.length > 0 && crate.models.some((m) => m.id === 'armor_helmet'));

  received.length = 0;
  await seen('Alex');
  const id = await checkout({ productId: 'crate-necros', username: 'Alex', quantity: 2 });
  assert.strictEqual(sessionOf(id).amount_total, 798);
  await pay(id);
  const [delivery] = await serverDelivers('Alex');
  assert.strictEqual(delivery.color, crate.colors[1]);
  assert.deepStrictEqual(received, ['crate key give Alex necros 1', 'crate key give Alex necros 1']);
});

// --- Rangos ---
test('los rangos se mejoran pagando la diferencia y no se puede comprar uno igual o inferior', async () => {
  received.length = 0;
  await seen('Rangos_MC');
  const rank = (id) => products.find((p) => p.id === id);

  const a = await checkout({ productId: 'rango-aventurero', username: 'Rangos_MC' });
  assert.strictEqual(sessionOf(a).amount_total, rank('rango-aventurero').price);
  await pay(a);
  const [first] = await serverDelivers('Rangos_MC');
  assert.deepStrictEqual(first.rank, { id: 'rango-aventurero', name: 'Rango Aventurero', tier: 1, group: 'aventurero', prefix: 'Aventurero', color: 'gold', hex: '#f4c95d' });
  assert.strictEqual(received[0], 'lp user Rangos_MC parent add aventurero');

  // El mismo rango o uno inferior: bloqueado.
  const again = await post('/api/checkout', { productId: 'rango-aventurero', username: 'Rangos_MC' });
  assert.strictEqual(again.status, 409);
  assert.strictEqual((await again.json()).code, 'rank_owned');

  // La web muestra el precio de la mejora antes de pagar.
  const look = await (await get('/api/player/Rangos_MC?product=rango-dragon')).json();
  assert.strictEqual(look.rank.id, 'rango-aventurero');
  const diff = rank('rango-dragon').price - rank('rango-aventurero').price;
  assert.deepStrictEqual(look.quote, { unit: diff, upgradeFrom: 'Rango Aventurero' });

  received.length = 0;
  const b = await checkout({ productId: 'rango-dragon', username: 'Rangos_MC' });
  assert.strictEqual(sessionOf(b).amount_total, diff);
  const order = await pay(b);
  assert.strictEqual(order.upgradeFrom, 'Rango Aventurero');
  const [second] = await serverDelivers('Rangos_MC');
  assert.strictEqual(second.upgradeFrom, 'Rango Aventurero');
  assert.deepStrictEqual(received, [
    'lp user Rangos_MC parent add dragon',
    'lp user Rangos_MC parent remove aventurero',
    'lp user Rangos_MC parent remove hechicero',
  ]);
  assert.strictEqual((await post('/api/checkout', { productId: 'rango-hechicero', username: 'Rangos_MC' })).status, 409);

  // El servidor recibe el rango de los conectados para su nametag.
  const { ranks, rankList } = await (await poll(['Rangos_MC', 'Steve'])).json();
  assert.deepStrictEqual(ranks.map((r) => [r.uuid, r.prefix]), [[uuidOf('Rangos_MC'), 'Dragón']]);
  assert.strictEqual(rankList.length, 4);
});

test('el staff puede cambiar o quitar un rango desde el juego', async () => {
  await seen('Staff_Rank');
  const uuid = uuidOf('Staff_Rank');
  await poll([], [], { ranks: [{ uuid, rank: 'rango-rey' }] });
  assert.strictEqual((await (await get('/api/player/Staff_Rank')).json()).rank.id, 'rango-rey');
  await poll([], [], { ranks: [{ uuid, rank: 'rango-aventurero' }] });
  assert.strictEqual((await (await get('/api/player/Staff_Rank')).json()).rank.id, 'rango-aventurero');
  await poll([], [], { ranks: [{ uuid, rank: null }, { uuid, rank: 'no-existe' }] });
  assert.strictEqual((await (await get('/api/player/Staff_Rank')).json()).rank, null);
});

// --- Discord y cuentas ---
const cookieValue = (res, name) =>
  res.headers.getSetCookie().map((c) => c.split(';')[0]).find((c) => c.startsWith(`${name}=`));

// Recorre el login de Discord y devuelve la cookie de sesión.
async function loginDiscord() {
  const start = await get(`/auth/discord?return=${encodeURIComponent('/cuenta')}`);
  assert.strictEqual(start.status, 302);
  const authorize = new URL(start.headers.get('location'));
  assert.strictEqual(authorize.origin, 'https://discord.com');
  assert.strictEqual(authorize.searchParams.get('redirect_uri'), 'http://tienda.test/auth/discord/callback');
  const state = authorize.searchParams.get('state');
  const stateCookie = cookieValue(start, 'tf_oauth_state');

  const cb = await get(`/auth/discord/callback?code=good-code&state=${state}`, { Cookie: stateCookie });
  assert.strictEqual(cb.headers.get('location'), '/cuenta?discord=ok');
  return cookieValue(cb, 'tf_discord');
}

test('login de Discord rechaza un state falso y no permite redirigir fuera de la web', async () => {
  const start = await get(`/auth/discord?return=${encodeURIComponent('//evil.com')}`);
  const stateCookie = cookieValue(start, 'tf_oauth_state');
  const cb = await get('/auth/discord/callback?code=good-code&state=falso', { Cookie: stateCookie });
  assert.strictEqual(cb.headers.get('location'), '/?discord=error');
  assert.strictEqual(cookieValue(cb, 'tf_discord'), undefined);

  // Una cookie de sesión manipulada no sirve.
  const me = await (await get('/api/me', { Cookie: 'tf_discord=eyJpZCI6IjEifQ.firma' })).json();
  assert.strictEqual(me.discord, null);
  assert.strictEqual((await post('/api/account/link')).status, 401);
});

test('sin SESSION_SECRET la web crea y guarda su propia clave de sesión', async () => {
  const other = { ...env, SESSION_SECRET: undefined };
  const a = await worker.fetch(new Request('http://tienda.test/auth/discord', { redirect: 'manual' }), other);
  assert.strictEqual(a.status, 302);
  const row = env.DB.raw.prepare("SELECT value FROM settings WHERE key = 'secret:session'").get();
  assert.match(row.value, /^[0-9a-f]{64}$/);
});

test('la cuenta se vincula con Minecraft escribiendo el código en el juego', async () => {
  const cookie = await loginDiscord();
  let me = await (await get('/api/me', { Cookie: cookie })).json();
  assert.deepStrictEqual(me.discord, { id: '111111111111111111', username: 'Alex', avatar: null });
  assert.strictEqual(me.minecraft, null);

  const { code, command } = await (await post('/api/account/link', null, cookie)).json();
  assert.match(code, /^[A-Z2-9]{6}$/);
  assert.strictEqual(command, `/tf vincular ${code}`);
  assert.strictEqual((await (await get('/api/account/link', { Cookie: cookie })).json()).pending.code, code);

  // Un código falso no vincula nada.
  const bad = await (await poll(['Alex_MC'], [], { links: [{ code: 'ZZZZZZ', name: 'Alex_MC', uuid: uuidOf('Alex_MC') }] })).json();
  assert.deepStrictEqual(bad.linkResults, [{ uuid: uuidOf('Alex_MC'), ok: false, account: null }]);

  const ok = await (await poll(['Alex_MC'], [], { links: [{ code: code.toLowerCase(), name: 'Alex_MC', uuid: uuidOf('Alex_MC') }] })).json();
  assert.deepStrictEqual(ok.linkResults, [{ uuid: uuidOf('Alex_MC'), ok: true, account: 'Alex' }]);
  // El código es de un solo uso.
  const reuse = await (await poll(['Alex_MC'], [], { links: [{ code, name: 'Alex_MC', uuid: uuidOf('Alex_MC') }] })).json();
  assert.strictEqual(reuse.linkResults[0].ok, false);

  me = await (await get('/api/me', { Cookie: cookie })).json();
  assert.strictEqual(me.minecraft.name, 'Alex_MC');
  assert.strictEqual(me.minecraft.uuid, uuidOf('Alex_MC'));
  assert.strictEqual((await (await get('/api/account/link', { Cookie: cookie })).json()).pending, null);
});

test('comprar con Discord da el rol, anuncia la compra y sale en "mis compras"', async () => {
  received.length = 0;
  discordCalls.length = 0;
  announcements.length = 0;
  memberExists = true;

  const cookie = await loginDiscord();
  const list = await (await get('/api/products')).json();
  assert.strictEqual(list.find((p) => p.id === 'rango-hechicero').discordRole, true);

  const id = await checkout({ productId: 'rango-hechicero', username: 'Alex_MC' }, cookie);
  assert.strictEqual((await pay(id)).status, 'queued');

  await serverDelivers('Alex_MC');
  assert.strictEqual(received[0], 'lp user Alex_MC parent add hechicero');
  const roleCall = discordCalls.find((c) => c.url.endsWith(`/roles/${ROLE_ID}`));
  assert.ok(roleCall, 'debe dar el rol');
  assert.strictEqual(roleCall.url, `/guilds/333333333333333333/members/111111111111111111/roles/${ROLE_ID}`);
  assert.strictEqual(announcements.length, 1);
  assert.match(announcements[0].embeds[0].description, /<@111111111111111111>.*Rango Hechicero/);

  const order = await (await get(`/api/order/${id}`)).json();
  assert.deepStrictEqual(order.discord, { username: 'Alex', status: 'granted' });
  const stored = env.DB.raw.prepare('SELECT * FROM orders WHERE id = ?').get(id);
  assert.ok(!JSON.stringify(stored).includes('user-token'));

  const { orders } = await (await get('/api/account/orders', { Cookie: cookie })).json();
  assert.ok(orders.some((o) => o.id === id && o.status === 'delivered'));
  // El rango sale en la cuenta.
  const me = await (await get('/api/me', { Cookie: cookie })).json();
  assert.strictEqual(me.minecraft.rank.id, 'rango-hechicero');
});

test('sin iniciar sesión, el rol va a la cuenta de Discord vinculada al jugador', async () => {
  discordCalls.length = 0;
  await seen('Alex_MC');
  // Alex_MC ya tiene Hechicero (prueba anterior): mejora a Rey.
  products.find((p) => p.id === 'rango-rey').discordRoles = [ROLE_ID];
  try {
    const id = await checkout({ productId: 'rango-rey', username: 'Alex_MC' });
    await pay(id);
    assert.ok(discordCalls.some((c) => c.url === `/guilds/333333333333333333/members/111111111111111111/roles/${ROLE_ID}`));
  } finally {
    products.find((p) => p.id === 'rango-rey').discordRoles = ['ID_ROL_REY'];
  }
});

test('si el comprador no está en el Discord, el bot lo añade con el rol', async () => {
  discordCalls.length = 0;
  memberExists = false;
  try {
    const cookie = await loginDiscord();
    await seen('Nuevo_MC');
    const id = await checkout({ productId: 'rango-hechicero', username: 'Nuevo_MC' }, cookie);
    await pay(id);
    const join = discordCalls.find((c) => c.url === '/guilds/333333333333333333/members/111111111111111111');
    assert.deepStrictEqual(JSON.parse(join.body), { access_token: 'user-token', roles: [ROLE_ID] });
    assert.ok(!discordCalls.some((c) => c.url.includes('/roles/')));
    assert.strictEqual((await (await get(`/api/order/${id}`)).json()).discord.status, 'joined');
  } finally {
    memberExists = true;
  }
});

test('un fallo en Discord no impide la entrega en el juego', async () => {
  received.length = 0;
  failRoles = true;
  try {
    const cookie = await loginDiscord();
    await seen('Fallo_MC');
    const id = await checkout({ productId: 'rango-hechicero', username: 'Fallo_MC' }, cookie);
    assert.strictEqual((await pay(id)).status, 'queued');
    await serverDelivers('Fallo_MC');
    assert.strictEqual(received[0], 'lp user Fallo_MC parent add hechicero');
    const order = await (await get(`/api/order/${id}`)).json();
    assert.strictEqual(order.status, 'delivered');
    assert.strictEqual(order.discord.status, 'failed');
  } finally {
    failRoles = false;
  }
});
