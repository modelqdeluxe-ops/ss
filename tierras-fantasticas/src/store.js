// Datos de la tienda en Cloudflare D1 (SQLite): pedidos, entregas pendientes para el
// puente del servidor de Minecraft y ajustes internos.
import { randomHex } from './session.js';

const SCHEMA = [
  `CREATE TABLE IF NOT EXISTS orders (
    id TEXT PRIMARY KEY,
    status TEXT NOT NULL,
    username TEXT NOT NULL,
    product_id TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    amount INTEGER NOT NULL,
    currency TEXT NOT NULL,
    discord TEXT,
    capture_id TEXT,
    error TEXT,
    refunded TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    paid_at TEXT,
    delivered_at TEXT
  )`,
  // Una entrega por pedido (order_id único): así cobrar dos veces nunca entrega dos veces.
  `CREATE TABLE IF NOT EXISTS deliveries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id TEXT NOT NULL UNIQUE,
    username TEXT NOT NULL,
    commands TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending',
    attempts INTEGER NOT NULL DEFAULT 0,
    sent_at INTEGER,
    done_at TEXT,
    error TEXT,
    created_at TEXT NOT NULL
  )`,
  'CREATE INDEX IF NOT EXISTS deliveries_status ON deliveries (status)',
  `CREATE TABLE IF NOT EXISTS settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at INTEGER NOT NULL
  )`,
];

// Estados en los que el pedido ya se pagó y quedó en manos del puente.
export const CLAIMED = ['queued', 'delivered', 'delivery_failed'];

// Si el servidor no confirma una entrega enviada en este tiempo, se vuelve a enviar.
const RESEND_MS = 2 * 60 * 1000;
const MAX_BATCH = 20;

const now = () => new Date().toISOString();

const ORDER_FIELDS = {
  status: 'status',
  discord: 'discord',
  captureId: 'capture_id',
  error: 'error',
  refunded: 'refunded',
  paidAt: 'paid_at',
  deliveredAt: 'delivered_at',
};

function rowToOrder(row) {
  if (!row) return null;
  return {
    id: row.id,
    status: row.status,
    username: row.username,
    productId: row.product_id,
    quantity: row.quantity,
    amount: row.amount,
    currency: row.currency,
    discord: row.discord ? JSON.parse(row.discord) : null,
    captureId: row.capture_id,
    error: row.error,
    refunded: row.refunded,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    paidAt: row.paid_at,
    deliveredAt: row.delivered_at,
  };
}

export function createStore(db) {
  let ready = null;

  // Crea las tablas la primera vez (una vez por instancia del Worker).
  function init() {
    ready ||= db.batch(SCHEMA.map((sql) => db.prepare(sql))).catch((err) => {
      ready = null;
      throw err;
    });
    return ready;
  }

  async function getOrder(id) {
    await init();
    return rowToOrder(await db.prepare('SELECT * FROM orders WHERE id = ?').bind(id).first());
  }

  async function createOrder(order) {
    await init();
    const at = now();
    await db
      .prepare(
        `INSERT INTO orders (id, status, username, product_id, quantity, amount, currency, discord, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      )
      .bind(
        order.id,
        order.status,
        order.username,
        order.productId,
        order.quantity,
        order.amount,
        order.currency,
        order.discord ? JSON.stringify(order.discord) : null,
        at,
        at,
      )
      .run();
  }

  async function updateOrder(id, fields) {
    await init();
    const sets = [];
    const values = [];
    for (const [key, column] of Object.entries(ORDER_FIELDS)) {
      if (!(key in fields)) continue;
      sets.push(`${column} = ?`);
      const value = fields[key];
      values.push(key === 'discord' && value ? JSON.stringify(value) : value ?? null);
    }
    sets.push('updated_at = ?');
    values.push(now(), id);
    await db.prepare(`UPDATE orders SET ${sets.join(', ')} WHERE id = ?`).bind(...values).run();
  }

  // Deja el pedido pagado en la cola del puente. Devuelve false si ya estaba en ella
  // (captura repetida o reintento del webhook de PayPal).
  async function queueDelivery(orderId, { username, commands, captureId }) {
    await init();
    const at = now();
    const [insert] = await db.batch([
      db
        .prepare('INSERT OR IGNORE INTO deliveries (order_id, username, commands, created_at) VALUES (?, ?, ?, ?)')
        .bind(orderId, username, JSON.stringify(commands), at),
      db
        .prepare(
          `UPDATE orders SET status = 'queued', capture_id = ?, paid_at = ?, updated_at = ?
           WHERE id = ? AND status NOT IN (${CLAIMED.map(() => '?').join(', ')})`,
        )
        .bind(captureId, at, at, orderId, ...CLAIMED),
    ]);
    return insert.meta.changes === 1;
  }

  // Lo que hace el puente en cada consulta: confirma las entregas hechas, guarda el estado
  // del servidor y recoge las entregas pendientes de los jugadores conectados.
  async function bridgePoll({ players, max, done }) {
    await init();
    const at = now();
    const ms = Date.now();
    const statements = [];

    for (const item of done) {
      const ok = item.ok === true;
      statements.push(
        db
          .prepare(
            `UPDATE deliveries SET status = ?, done_at = ?, error = ?
             WHERE id = ? AND status IN ('pending', 'sent')`,
          )
          .bind(ok ? 'done' : 'failed', at, ok ? null : String(item.error || 'Error').slice(0, 500), item.id),
        db
          .prepare(
            `UPDATE orders SET status = ?, delivered_at = ?, error = ?, updated_at = ?
             WHERE id = (SELECT order_id FROM deliveries WHERE id = ?) AND status = 'queued'`,
          )
          .bind(ok ? 'delivered' : 'delivery_failed', ok ? at : null, ok ? null : String(item.error || 'Error').slice(0, 500), at, item.id),
      );
    }

    statements.push(
      db
        .prepare(
          `INSERT INTO settings (key, value, updated_at) VALUES ('server_status', ?, ?)
           ON CONFLICT (key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at`,
        )
        .bind(JSON.stringify({ players, max }), ms),
    );
    if (statements.length) await db.batch(statements);

    const names = JSON.stringify(players.map((p) => p.name.toLowerCase()));
    const { results } = await db
      .prepare(
        `SELECT d.id, d.username, d.commands, o.product_id, o.quantity FROM deliveries d
         JOIN orders o ON o.id = d.order_id
         WHERE (d.status = 'pending' OR (d.status = 'sent' AND d.sent_at < ?))
           AND lower(d.username) IN (SELECT value FROM json_each(?))
         ORDER BY d.id LIMIT ${MAX_BATCH}`,
      )
      .bind(ms - RESEND_MS, names)
      .all();

    if (results.length) {
      await db
        .prepare(
          `UPDATE deliveries SET status = 'sent', sent_at = ?, attempts = attempts + 1
           WHERE id IN (SELECT value FROM json_each(?))`,
        )
        .bind(ms, JSON.stringify(results.map((r) => r.id)))
        .run();
    }
    return results.map((r) => ({
      id: r.id,
      player: r.username,
      productId: r.product_id,
      quantity: r.quantity,
      commands: JSON.parse(r.commands),
    }));
  }

  async function serverStatus() {
    await init();
    const row = await db.prepare("SELECT value, updated_at FROM settings WHERE key = 'server_status'").first();
    return row ? { ...JSON.parse(row.value), at: row.updated_at } : null;
  }

  async function pendingDeliveries(username) {
    await init();
    const row = await db
      .prepare("SELECT COUNT(*) AS n FROM deliveries WHERE lower(username) = lower(?) AND status IN ('pending', 'sent')")
      .bind(username)
      .first();
    return row?.n || 0;
  }

  // Secreto propio de la web (firma de cookies) guardado en la base de datos la primera vez.
  async function secret(name) {
    await init();
    await db
      .prepare('INSERT OR IGNORE INTO settings (key, value, updated_at) VALUES (?, ?, ?)')
      .bind(`secret:${name}`, randomHex(32), Date.now())
      .run();
    const row = await db.prepare('SELECT value FROM settings WHERE key = ?').bind(`secret:${name}`).first();
    return row.value;
  }

  return { getOrder, createOrder, updateOrder, queueDelivery, bridgePoll, serverStatus, pendingDeliveries, secret };
}
