const { test, before, after } = require('node:test');
const assert = require('node:assert');
const http = require('node:http');
const net = require('node:net');
const os = require('node:os');
const path = require('node:path');
const fs = require('node:fs');
const { encode } = require('../lib/rcon');

const RCON_PASSWORD = 'secreto';
const received = [];
const servers = [];
let baseUrl;

// --- Servidor RCON falso que imita a Minecraft ---
function startFakeRcon() {
  const server = net.createServer((socket) => {
    let buf = Buffer.alloc(0);
    socket.on('data', (chunk) => {
      buf = Buffer.concat([buf, chunk]);
      while (buf.length >= 4 && buf.length >= 4 + buf.readInt32LE(0)) {
        const len = buf.readInt32LE(0);
        const id = buf.readInt32LE(4);
        const type = buf.readInt32LE(8);
        const body = buf.toString('utf8', 12, 4 + len - 2);
        buf = buf.subarray(4 + len);
        if (type === 3) socket.write(encode(body === RCON_PASSWORD ? id : -1, 2, ''));
        else {
          received.push(body);
          socket.write(encode(id, 0, `ok: ${body}`));
        }
      }
    });
  });
  servers.push(server);
  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve(server.address().port)));
}

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

before(async () => {
  const [rconPort, paypalPort, discordPort] = await Promise.all([startFakeRcon(), startFakePayPal(), startFakeDiscord()]);
  // Rol de Discord real (formato snowflake) para el rango de pruebas.
  require('../config/products.json').find((p) => p.id === 'rango-hechicero').discordRoles = [ROLE_ID];
  Object.assign(process.env, {
    PAYPAL_CLIENT_ID: 'client',
    PAYPAL_CLIENT_SECRET: 'secret',
    PAYPAL_WEBHOOK_ID: 'WH-1',
    PAYPAL_API_BASE: `http://127.0.0.1:${paypalPort}`,
    CURRENCY: 'USD',
    PUBLIC_URL: 'http://tienda.test',
    SESSION_SECRET: 'test-secret',
    DISCORD_CLIENT_ID: 'discord-client',
    DISCORD_CLIENT_SECRET: 'discord-secret',
    DISCORD_BOT_TOKEN: 'bot-token',
    DISCORD_GUILD_ID: '333333333333333333',
    DISCORD_API_BASE: `http://127.0.0.1:${discordPort}`,
    DISCORD_WEBHOOK_URL: `http://127.0.0.1:${discordPort}/webhook`,
    RCON_HOST: '127.0.0.1',
    RCON_PORT: String(rconPort),
    RCON_PASSWORD,
    ORDERS_FILE: path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'tf-')), 'orders.json'),
  });
  const { app } = require('../server');
  const httpServer = await new Promise((resolve) => {
    const s = app.listen(0, () => resolve(s));
  });
  servers.push(httpServer);
  baseUrl = `http://127.0.0.1:${httpServer.address().port}`;
});

after(() => servers.forEach((s) => s.close()));

const post = (url, body, cookie) =>
  fetch(`${baseUrl}${url}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(cookie ? { Cookie: cookie } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });

async function createOrder(body, cookie) {
  const res = await post('/api/orders', body, cookie);
  assert.strictEqual(res.status, 200);
  return (await res.json()).id;
}

test('la API de productos no expone los comandos y config incluye el client-id', async () => {
  const products = await (await fetch(`${baseUrl}/api/products`)).json();
  assert.ok(products.length > 0);
  assert.ok(products.every((p) => !('commands' in p)));
  const config = await (await fetch(`${baseUrl}/api/config`)).json();
  assert.strictEqual(config.paypalClientId, 'client');
  assert.strictEqual(config.paymentsEnabled, true);
  assert.ok(!JSON.stringify(config).includes('secret'));
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

test('pago capturado entrega la compra por RCON una sola vez', async () => {
  received.length = 0;
  const id = await createOrder({ productId: 'monedas-10000', username: 'Steve_123', quantity: 2 });

  const res = await post(`/api/orders/${id}/capture`);
  assert.strictEqual(res.status, 200);
  assert.strictEqual((await res.json()).status, 'delivered');
  assert.deepStrictEqual(received, ['eco give Steve_123 10000', 'eco give Steve_123 10000']);

  // Captura repetida (doble clic, recarga): no se entrega otra vez.
  await post(`/api/orders/${id}/capture`);
  assert.strictEqual(received.length, 2);

  const order = await (await fetch(`${baseUrl}/api/order/${id}`)).json();
  assert.strictEqual(order.status, 'delivered');
  assert.strictEqual(order.username, 'Steve_123');
});

test('no se entrega si el importe cobrado no coincide', async () => {
  received.length = 0;
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
    assert.strictEqual(received.length, 0);
  } finally {
    captureOverride = null;
  }
});

test('tarjeta rechazada permite reintentar sin entregar', async () => {
  received.length = 0;
  const id = await createOrder({ productId: 'llaves-epicas-5', username: 'Alex' });
  captureOverride = (order, send) =>
    send(422, { name: 'UNPROCESSABLE_ENTITY', details: [{ issue: 'INSTRUMENT_DECLINED' }] });
  try {
    const res = await post(`/api/orders/${id}/capture`);
    assert.strictEqual(res.status, 402);
    assert.strictEqual((await res.json()).retry, true);
    assert.strictEqual(received.length, 0);
  } finally {
    captureOverride = null;
  }
});

test('pago pendiente se entrega cuando llega el webhook de PayPal', async () => {
  received.length = 0;
  const id = await createOrder({ productId: 'llaves-legendarias-3', username: 'Alex' });
  captureOverride = (order, send) =>
    send(201, {
      id: order.id,
      status: 'COMPLETED',
      purchase_units: [{ payments: { captures: [{ id: 'CAP-P', status: 'PENDING', amount: order.purchase_units[0].amount } ] } }],
    });
  try {
    const res = await post(`/api/orders/${id}/capture`);
    assert.strictEqual((await res.json()).status, 'awaiting_payment');
    assert.strictEqual(received.length, 0);
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
  assert.strictEqual(received.length, 0);

  webhookValid = true;
  assert.strictEqual((await post('/webhook/paypal', event)).status, 200);
  assert.deepStrictEqual(received, ['crate key give Alex legendaria 3']);

  // PayPal reenvía el evento: sin entrega doble.
  assert.strictEqual((await post('/webhook/paypal', event)).status, 200);
  assert.strictEqual(received.length, 1);
});

test('capturar un pedido que no creó la tienda devuelve 404', async () => {
  assert.strictEqual((await post('/api/orders/FAKEORDER123/capture')).status, 404);
});

// --- Discord ---
const cookieValue = (res, name) =>
  res.headers.getSetCookie().map((c) => c.split(';')[0]).find((c) => c.startsWith(`${name}=`));

// Recorre el login de Discord y devuelve la cookie de sesión.
async function loginDiscord() {
  const start = await fetch(`${baseUrl}/auth/discord?return=${encodeURIComponent('/?buy=rango-hechicero')}`, { redirect: 'manual' });
  assert.strictEqual(start.status, 302);
  const authorize = new URL(start.headers.get('location'));
  assert.strictEqual(authorize.origin, 'https://discord.com');
  assert.strictEqual(authorize.searchParams.get('redirect_uri'), 'http://tienda.test/auth/discord/callback');
  const state = authorize.searchParams.get('state');
  const stateCookie = cookieValue(start, 'tf_oauth_state');

  const cb = await fetch(`${baseUrl}/auth/discord/callback?code=good-code&state=${state}`, {
    redirect: 'manual',
    headers: { Cookie: stateCookie },
  });
  assert.strictEqual(cb.headers.get('location'), '/?buy=rango-hechicero&discord=ok');
  return cookieValue(cb, 'tf_discord');
}

test('login de Discord rechaza un state falso y no permite redirigir fuera de la web', async () => {
  const start = await fetch(`${baseUrl}/auth/discord?return=${encodeURIComponent('//evil.com')}`, { redirect: 'manual' });
  const stateCookie = cookieValue(start, 'tf_oauth_state');
  const cb = await fetch(`${baseUrl}/auth/discord/callback?code=good-code&state=falso`, {
    redirect: 'manual',
    headers: { Cookie: stateCookie },
  });
  assert.strictEqual(cb.headers.get('location'), '/?discord=error');
  assert.strictEqual(cookieValue(cb, 'tf_discord'), undefined);

  // Una cookie de sesión manipulada no sirve.
  const me = await (await fetch(`${baseUrl}/api/me`, { headers: { Cookie: 'tf_discord=eyJpZCI6IjEifQ.firma' } })).json();
  assert.strictEqual(me.discord, null);
});

test('comprar con Discord vinculado da el rol en Discord y anuncia la compra', async () => {
  received.length = 0;
  discordCalls.length = 0;
  announcements.length = 0;
  memberExists = true;

  const cookie = await loginDiscord();
  const me = await (await fetch(`${baseUrl}/api/me`, { headers: { Cookie: cookie } })).json();
  assert.deepStrictEqual(me.discord, { id: '111111111111111111', username: 'Alex', avatar: null });

  const products = await (await fetch(`${baseUrl}/api/products`)).json();
  assert.strictEqual(products.find((p) => p.id === 'rango-hechicero').discordRole, true);
  assert.ok(products.every((p) => !('discordRoles' in p)));

  const id = await createOrder({ productId: 'rango-hechicero', username: 'Alex_MC' }, cookie);
  const res = await post(`/api/orders/${id}/capture`);
  assert.strictEqual((await res.json()).status, 'delivered');

  // En el juego…
  assert.strictEqual(received[0], 'lp user Alex_MC parent add hechicero');
  // …y en Discord: ya era miembro (204), así que se le da el rol aparte.
  const roleCall = discordCalls.find((c) => c.url.endsWith(`/roles/${ROLE_ID}`));
  assert.ok(roleCall, 'debe dar el rol');
  assert.strictEqual(roleCall.url, `/guilds/333333333333333333/members/111111111111111111/roles/${ROLE_ID}`);
  assert.strictEqual(announcements.length, 1);
  assert.match(announcements[0].embeds[0].description, /<@111111111111111111>.*Rango Hechicero/);

  const order = await (await fetch(`${baseUrl}/api/order/${id}`)).json();
  assert.deepStrictEqual(order.discord, { username: 'Alex', status: 'granted' });
  // El token de Discord del comprador no se queda guardado.
  const stored = JSON.parse(fs.readFileSync(process.env.ORDERS_FILE, 'utf8'))[id];
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
    const order = await (await fetch(`${baseUrl}/api/order/${id}`)).json();
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
    const id = await createOrder({ productId: 'rango-hechicero', username: 'Alex_MC' }, cookie);
    const res = await post(`/api/orders/${id}/capture`);
    assert.strictEqual((await res.json()).status, 'delivered');
    assert.strictEqual(received.length, 2);
    const order = await (await fetch(`${baseUrl}/api/order/${id}`)).json();
    assert.strictEqual(order.discord.status, 'failed');
  } finally {
    failRoles = false;
  }
});
