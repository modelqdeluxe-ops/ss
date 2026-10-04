const { test, before, after } = require('node:test');
const assert = require('node:assert');
const net = require('node:net');
const os = require('node:os');
const path = require('node:path');
const fs = require('node:fs');
const { encode } = require('../lib/rcon');

const WEBHOOK_SECRET = 'whsec_test_secret';
const RCON_PASSWORD = 'secreto';
const received = [];
let rconServer;
let httpServer;
let baseUrl;

// Servidor RCON falso que imita a Minecraft.
function startFakeRcon() {
  return new Promise((resolve) => {
    rconServer = net.createServer((socket) => {
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
    rconServer.listen(0, '127.0.0.1', () => resolve(rconServer.address().port));
  });
}

before(async () => {
  const rconPort = await startFakeRcon();
  Object.assign(process.env, {
    STRIPE_SECRET_KEY: 'sk_test_dummy',
    STRIPE_WEBHOOK_SECRET: WEBHOOK_SECRET,
    RCON_HOST: '127.0.0.1',
    RCON_PORT: String(rconPort),
    RCON_PASSWORD,
    ORDERS_FILE: path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'tf-')), 'orders.json'),
  });
  const { app } = require('../server');
  await new Promise((resolve) => {
    httpServer = app.listen(0, resolve);
  });
  baseUrl = `http://127.0.0.1:${httpServer.address().port}`;
});

after(() => {
  httpServer?.close();
  rconServer?.close();
});

test('la API de productos no expone los comandos', async () => {
  const products = await (await fetch(`${baseUrl}/api/products`)).json();
  assert.ok(products.length > 0);
  assert.ok(products.every((p) => !('commands' in p)));
});

test('checkout rechaza usuarios, productos y cantidades no válidos', async () => {
  const post = (body) =>
    fetch(`${baseUrl}/api/checkout`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
  assert.strictEqual((await post({ productId: 'no-existe', username: 'Steve' })).status, 400);
  assert.strictEqual((await post({ productId: 'monedas-10000', username: 'a; op yo' })).status, 400);
  assert.strictEqual((await post({ productId: 'monedas-10000', username: 'Steve', quantity: 50 })).status, 400);
  assert.strictEqual((await post({ productId: 'rango-dragon', username: 'Steve', quantity: 2 })).status, 400);
});

test('webhook con firma inválida se rechaza', async () => {
  const res = await fetch(`${baseUrl}/webhook`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'stripe-signature': 't=1,v1=falsa' },
    body: '{}',
  });
  assert.strictEqual(res.status, 400);
});

test('pago confirmado entrega la compra por RCON una sola vez', async () => {
  const Stripe = require('stripe');
  const stripe = new Stripe('sk_test_dummy');
  const payload = JSON.stringify({
    id: 'evt_1',
    object: 'event',
    type: 'checkout.session.completed',
    data: {
      object: {
        id: 'cs_test_1',
        object: 'checkout.session',
        payment_status: 'paid',
        amount_total: 398,
        currency: 'usd',
        metadata: { username: 'Steve_123', productId: 'monedas-10000', quantity: '2' },
        customer_details: { email: 'jugador@example.com' },
      },
    },
  });
  const send = () =>
    fetch(`${baseUrl}/webhook`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'stripe-signature': stripe.webhooks.generateTestHeaderString({ payload, secret: WEBHOOK_SECRET }),
      },
      body: payload,
    });

  assert.strictEqual((await send()).status, 200);
  assert.deepStrictEqual(received, ['eco give Steve_123 10000', 'eco give Steve_123 10000']);

  // Stripe puede reenviar el mismo evento: no debe entregarse dos veces.
  assert.strictEqual((await send()).status, 200);
  assert.strictEqual(received.length, 2);

  const orders = JSON.parse(fs.readFileSync(process.env.ORDERS_FILE, 'utf8'));
  assert.strictEqual(orders.cs_test_1.status, 'delivered');
});
