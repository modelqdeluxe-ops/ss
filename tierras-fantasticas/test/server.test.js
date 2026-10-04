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

before(async () => {
  const [rconPort, paypalPort] = await Promise.all([startFakeRcon(), startFakePayPal()]);
  Object.assign(process.env, {
    PAYPAL_CLIENT_ID: 'client',
    PAYPAL_CLIENT_SECRET: 'secret',
    PAYPAL_WEBHOOK_ID: 'WH-1',
    PAYPAL_API_BASE: `http://127.0.0.1:${paypalPort}`,
    CURRENCY: 'USD',
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

const post = (url, body) =>
  fetch(`${baseUrl}${url}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  });

async function createOrder(body) {
  const res = await post('/api/orders', body);
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
