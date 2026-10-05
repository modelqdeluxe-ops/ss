import { test, before, after } from 'node:test';
import assert from 'node:assert';
import http from 'node:http';
import products from '../config/products.json' with { type: 'json' };
import worker from '../src/index.js';
import { createD1 } from './d1.js';

const servers = [];
let env;
// Lo que el servidor de Minecraft (puente) ha ejecutado.
const received = [];
const BRIDGE_SECRET = 'clave-del-puente';

// --- API de PayPal simulada ---
const paypalOrders = new Map();
let nextOrder = 1;
// Permite simular respuestas concretas de la captura en cada prueba.
let captureOverride = null;
let webhookValid = true;

function startFakePayPal() {
  const server = http.createServer(async (req, res) => {
    let raw = '';
    for await (const chunk of req) raw += chunk;
    const body = raw && req.headers['content-type']?.includes('json') ? JSON.parse(raw) : {};
    const send = (status, data) => {
      res.writeHead(status, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(data));
    };

    if (req.url === '/v1/oauth2/token') return send(200, { access_token: 'tok', expires_in: 3600 });
    if (req.headers.authorization !== 'Bearer tok') return send(401, { name: 'AUTHENTICATION_FAILURE' });

    if (req.url === '/v2/checkout/orders' && req.method === 'POST') {
      const id = `ORDER${nextOrder++}`;
      paypalOrders.set(id, { id, status: 'CREATED', purchase_units: body.purchase_units });
      return send(201, { id, status: 'CREATED' });
    }

    const capture = req.url.match(/^\/v2\/checkout\/orders\/(\w+)\/capture$/);
    if (capture) {
      const order = paypalOrders.get(capture[1]);
      if (!order) return send(404, { name: 'RESOURCE_NOT_FOUND' });
      if (captureOverride) return captureOverride(order, send);
      order.status = 'COMPLETED';
      return send(201, {
        id: order.id,
        status: 'COMPLETED',
        purchase_units: [
          { payments: { captures: [{ id: `CAP-${order.id}`, status: 'COMPLETED', amount: order.purchase_units[0].amount }] } },
        ],
      });
    }

    if (req.url === '/v1/notifications/verify-webhook-signature') {
      return send(200, { verification_status: webhookValid ? 'SUCCESS' : 'FAILURE' });
    }
    send(404, { name: 'NOT_FOUND' });
  });
  servers.push(server);
  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve(server.address().port)));
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
  const [paypalPort, discordPort] = await Promise.all([startFakePayPal(), startFakeDiscord()]);
  // Rol de Discord real (formato snowflake) para el rango de pruebas.
  products.find((p) => p.id === 'rango-hechicero').discordRoles = [ROLE_ID];
  env = {
    DB: createD1(),
    ASSETS,
    LOGGER: quietLog,
    PAYPAL_CLIENT_ID: 'client',
    PAYPAL_CLIENT_SECRET: 'secret',
    PAYPAL_WEBHOOK_ID: 'WH-1',
    PAYPAL_API_BASE: `http://127.0.0.1:${paypalPort}`,
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

async function createOrder(body, cookie) {
  const res = await post('/api/orders', body, cookie);
  assert.strictEqual(res.status, 200);
  return (await res.json()).id;
}

// El mod en el servidor: manda los jugadores conectados y las entregas hechas, recibe las nuevas.
async function poll(players, done = [], secret = BRIDGE_SECRET) {
  return call('/bridge/poll', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${secret}` },
    body: JSON.stringify({ players: players.map((name) => ({ name, uuid: null })), max: 50, done }),
  });
}

// Simula al servidor: conecta a los jugadores, ejecuta lo recibido y lo confirma.
async function serverDelivers(...players) {
  const { deliveries } = await (await poll(players)).json();
  for (const d of deliveries) received.push(...d.commands);
  await poll(players, deliveries.map((d) => ({ id: d.id, ok: true })));
  return deliveries;
}

test('la API de productos no expone los comandos y config incluye el client-id', async () => {
  const list = await (await get('/api/products')).json();
  assert.ok(list.length > 0);
  assert.ok(list.every((p) => !('commands' in p)));
  const config = await (await get('/api/config')).json();
  assert.strictEqual(config.paypalClientId, 'client');
  assert.strictEqual(config.paymentsEnabled, true);
  assert.ok(!JSON.stringify(config).includes('secret'));
});

test('las rutas que no son de la API las sirve la web estática', async () => {
  assert.strictEqual(await (await get('/tienda')).text(), 'estático /tienda');
});

test('crear pedido rechaza usuarios, productos y cantidades no válidos', async () => {
  assert.strictEqual((await post('/api/orders', { productId: 'no-existe', username: 'Steve' })).status, 400);
  assert.strictEqual((await post('/api/orders', { productId: 'monedas-10000', username: 'a; op yo' })).status, 400);
  assert.strictEqual((await post('/api/orders', { productId: 'monedas-10000', username: 'Steve', quantity: 50 })).status, 400);
  assert.strictEqual((await post('/api/orders', { productId: 'rango-dragon', username: 'Steve', quantity: 2 })).status, 400);
});

test('el pedido en PayPal usa el precio del catálogo', async () => {
  const id = await createOrder({ productId: 'monedas-10000', username: 'Steve', quantity: 3, price: 1 });
  const unit = paypalOrders.get(id).purchase_units[0];
  assert.deepStrictEqual(unit.amount.value, '5.97');
  assert.strictEqual(unit.amount.currency_code, 'USD');
  assert.strictEqual(unit.items[0].quantity, '3');
});

test('el puente rechaza una clave incorrecta', async () => {
  assert.strictEqual((await poll(['Steve'], [], 'otra')).status, 401);
  const saved = env.BRIDGE_SECRET;
  env.BRIDGE_SECRET = '';
  try {
    assert.strictEqual((await poll(['Steve'])).status, 503);
  } finally {
    env.BRIDGE_SECRET = saved;
  }
});

test('pago capturado queda en cola y el servidor lo entrega una sola vez cuando el jugador entra', async () => {
  received.length = 0;
  const id = await createOrder({ productId: 'monedas-10000', username: 'Steve_123', quantity: 2 });

  const res = await post(`/api/orders/${id}/capture`);
  assert.strictEqual(res.status, 200);
  assert.strictEqual((await res.json()).status, 'queued');

  // Captura repetida (doble clic, recarga): no se encola otra vez.
  assert.strictEqual((await (await post(`/api/orders/${id}/capture`)).json()).status, 'queued');

  // El jugador no está conectado: no se le entrega nada todavía.
  assert.deepStrictEqual(await serverDelivers('Otro'), []);
  assert.strictEqual(received.length, 0);

  // Entra (sin importar mayúsculas): se entrega y se confirma.
  const [delivery] = await serverDelivers('steve_123');
  assert.strictEqual(delivery.product, '10.000 Monedas de Oro');
  assert.strictEqual(delivery.quantity, 2);
  assert.strictEqual(delivery.player, 'Steve_123');
  assert.deepStrictEqual(received, ['eco give Steve_123 10000', 'eco give Steve_123 10000']);
  assert.deepStrictEqual(await serverDelivers('Steve_123'), []);
  assert.strictEqual(received.length, 2);

  const order = await (await get(`/api/order/${id}`)).json();
  assert.strictEqual(order.status, 'delivered');
  assert.strictEqual(order.username, 'Steve_123');
});

test('si el servidor no confirma una entrega, se reenvía pasado un rato', async () => {
  const id = await createOrder({ productId: 'monedas-10000', username: 'Lento' });
  await post(`/api/orders/${id}/capture`);
  const first = (await (await poll(['Lento'])).json()).deliveries;
  assert.strictEqual(first.length, 1);
  // Enseguida no se repite…
  assert.deepStrictEqual((await (await poll(['Lento'])).json()).deliveries, []);
  // …pero si pasan más de 2 minutos sin confirmar, sí.
  env.DB.raw.prepare('UPDATE deliveries SET sent_at = sent_at - 200000 WHERE id = ?').run(first[0].id);
  const again = (await (await poll(['Lento'])).json()).deliveries;
  assert.strictEqual(again[0].id, first[0].id);
  // Un fallo del comando queda marcado para el staff.
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
  const id = await createOrder({ productId: 'crate-necros', username: 'Alex', quantity: 2 });
  assert.strictEqual(paypalOrders.get(id).purchase_units[0].amount.value, '7.98');
  const res = await post(`/api/orders/${id}/capture`);
  assert.strictEqual((await res.json()).status, 'queued');
  await serverDelivers('Alex');
  assert.deepStrictEqual(received, ['crate key give Alex necros 1', 'crate key give Alex necros 1']);
});

test('no se entrega si el importe cobrado no coincide', async () => {
  const id = await createOrder({ productId: 'rango-dragon', username: 'Tramposo' });
  captureOverride = (order, send) =>
    send(201, {
      id: order.id,
      status: 'COMPLETED',
      purchase_units: [{ payments: { captures: [{ id: 'CAP-X', status: 'COMPLETED', amount: { currency_code: 'USD', value: '0.01' } }] } }],
    });
  try {
    const res = await post(`/api/orders/${id}/capture`);
    assert.strictEqual((await res.json()).status, 'error');
    assert.deepStrictEqual(await serverDelivers('Tramposo'), []);
  } finally {
    captureOverride = null;
  }
});

test('tarjeta rechazada permite reintentar sin entregar', async () => {
  const id = await createOrder({ productId: 'llaves-epicas-5', username: 'Rechazado' });
  captureOverride = (order, send) => send(422, { name: 'UNPROCESSABLE_ENTITY', details: [{ issue: 'INSTRUMENT_DECLINED' }] });
  try {
    const res = await post(`/api/orders/${id}/capture`);
    assert.strictEqual(res.status, 402);
    assert.strictEqual((await res.json()).retry, true);
    assert.deepStrictEqual(await serverDelivers('Rechazado'), []);
  } finally {
    captureOverride = null;
  }
});

test('pago pendiente se entrega cuando llega el webhook de PayPal', async () => {
  received.length = 0;
  const id = await createOrder({ productId: 'llaves-legendarias-3', username: 'Paciente' });
  captureOverride = (order, send) =>
    send(201, {
      id: order.id,
      status: 'COMPLETED',
      purchase_units: [{ payments: { captures: [{ id: 'CAP-P', status: 'PENDING', amount: order.purchase_units[0].amount }] } }],
    });
  try {
    const res = await post(`/api/orders/${id}/capture`);
    assert.strictEqual((await res.json()).status, 'awaiting_payment');
  } finally {
    captureOverride = null;
  }

  const event = {
    event_type: 'PAYMENT.CAPTURE.COMPLETED',
    resource: {
      id: 'CAP-P',
      status: 'COMPLETED',
      amount: { currency_code: 'USD', value: '5.99' },
      supplementary_data: { related_ids: { order_id: id } },
    },
  };

  webhookValid = false;
  assert.strictEqual((await post('/webhook/paypal', event)).status, 400);
  assert.deepStrictEqual(await serverDelivers('Paciente'), []);

  webhookValid = true;
  assert.strictEqual((await post('/webhook/paypal', event)).status, 200);
  // PayPal reenvía el evento: sin entrega doble.
  assert.strictEqual((await post('/webhook/paypal', event)).status, 200);
  await serverDelivers('Paciente');
  assert.deepStrictEqual(received, ['crate key give Paciente legendaria 3']);
});

test('capturar un pedido que no creó la tienda devuelve 404', async () => {
  assert.strictEqual((await post('/api/orders/FAKEORDER123/capture')).status, 404);
});

// --- Discord ---
const cookieValue = (res, name) =>
  res.headers.getSetCookie().map((c) => c.split(';')[0]).find((c) => c.startsWith(`${name}=`));

// Recorre el login de Discord y devuelve la cookie de sesión.
async function loginDiscord() {
  const start = await get(`/auth/discord?return=${encodeURIComponent('/?buy=rango-hechicero')}`);
  assert.strictEqual(start.status, 302);
  const authorize = new URL(start.headers.get('location'));
  assert.strictEqual(authorize.origin, 'https://discord.com');
  assert.strictEqual(authorize.searchParams.get('redirect_uri'), 'http://tienda.test/auth/discord/callback');
  const state = authorize.searchParams.get('state');
  const stateCookie = cookieValue(start, 'tf_oauth_state');

  const cb = await get(`/auth/discord/callback?code=good-code&state=${state}`, { Cookie: stateCookie });
  assert.strictEqual(cb.headers.get('location'), '/?buy=rango-hechicero&discord=ok');
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
});

test('sin SESSION_SECRET la web crea y guarda su propia clave de sesión', async () => {
  const other = { ...env, SESSION_SECRET: undefined };
  const a = await worker.fetch(new Request('http://tienda.test/auth/discord', { redirect: 'manual' }), other);
  assert.strictEqual(a.status, 302);
  const row = env.DB.raw.prepare("SELECT value FROM settings WHERE key = 'secret:session'").get();
  assert.match(row.value, /^[0-9a-f]{64}$/);
});

test('comprar con Discord vinculado da el rol en Discord y anuncia la compra', async () => {
  received.length = 0;
  discordCalls.length = 0;
  announcements.length = 0;
  memberExists = true;

  const cookie = await loginDiscord();
  const me = await (await get('/api/me', { Cookie: cookie })).json();
  assert.deepStrictEqual(me.discord, { id: '111111111111111111', username: 'Alex', avatar: null });

  const list = await (await get('/api/products')).json();
  assert.strictEqual(list.find((p) => p.id === 'rango-hechicero').discordRole, true);
  assert.ok(list.every((p) => !('discordRoles' in p)));

  const id = await createOrder({ productId: 'rango-hechicero', username: 'Alex_MC' }, cookie);
  const res = await post(`/api/orders/${id}/capture`);
  assert.strictEqual((await res.json()).status, 'queued');

  // En el juego…
  await serverDelivers('Alex_MC');
  assert.strictEqual(received[0], 'lp user Alex_MC parent add hechicero');
  // …y en Discord: ya era miembro (204), así que se le da el rol aparte.
  const roleCall = discordCalls.find((c) => c.url.endsWith(`/roles/${ROLE_ID}`));
  assert.ok(roleCall, 'debe dar el rol');
  assert.strictEqual(roleCall.url, `/guilds/333333333333333333/members/111111111111111111/roles/${ROLE_ID}`);
  assert.strictEqual(announcements.length, 1);
  assert.match(announcements[0].embeds[0].description, /<@111111111111111111>.*Rango Hechicero/);

  const order = await (await get(`/api/order/${id}`)).json();
  assert.deepStrictEqual(order.discord, { username: 'Alex', status: 'granted' });
  // El token de Discord del comprador no se queda guardado.
  const stored = env.DB.raw.prepare('SELECT * FROM orders WHERE id = ?').get(id);
  assert.ok(!JSON.stringify(stored).includes('user-token'));
});

test('si el comprador no está en el Discord, el bot lo añade con el rol', async () => {
  discordCalls.length = 0;
  memberExists = false;
  try {
    const cookie = await loginDiscord();
    const id = await createOrder({ productId: 'rango-hechicero', username: 'Alex_MC' }, cookie);
    await post(`/api/orders/${id}/capture`);
    const join = discordCalls.find((c) => c.url === '/guilds/333333333333333333/members/111111111111111111');
    assert.deepStrictEqual(JSON.parse(join.body), { access_token: 'user-token', roles: [ROLE_ID] });
    assert.ok(!discordCalls.some((c) => c.url.includes('/roles/')));
    const order = await (await get(`/api/order/${id}`)).json();
    assert.strictEqual(order.discord.status, 'joined');
  } finally {
    memberExists = true;
  }
});

test('un fallo en Discord no impide la entrega en el juego', async () => {
  received.length = 0;
  failRoles = true;
  try {
    const cookie = await loginDiscord();
    const id = await createOrder({ productId: 'rango-hechicero', username: 'Fallo_MC' }, cookie);
    const res = await post(`/api/orders/${id}/capture`);
    assert.strictEqual((await res.json()).status, 'queued');
    await serverDelivers('Fallo_MC');
    assert.strictEqual(received.length, 2);
    const order = await (await get(`/api/order/${id}`)).json();
    assert.strictEqual(order.status, 'delivered');
    assert.strictEqual(order.discord.status, 'failed');
  } finally {
    failRoles = false;
  }
});
