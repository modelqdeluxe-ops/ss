// Datos de la tienda en Cloudflare D1 (SQLite): pedidos, entregas para el puente del servidor de Minecraft,
// jugadores conocidos (nombre + UUID), cuentas de Discord, rangos y ajustes internos.
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
  // Una entrega por pedido (order_id único): así un pago avisado dos veces nunca entrega dos veces.
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
  // Jugadores que han entrado alguna vez al servidor, según el puente (el UUID es el del servidor).
  `CREATE TABLE IF NOT EXISTS players (
    uuid TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    name_lower TEXT NOT NULL,
    first_seen INTEGER NOT NULL,
    last_seen INTEGER NOT NULL
  )`,
  'CREATE INDEX IF NOT EXISTS players_name ON players (name_lower)',
  // Cuentas de la web (inicio de sesión con Discord) y su cuenta de Minecraft vinculada.
  `CREATE TABLE IF NOT EXISTS accounts (
    discord_id TEXT PRIMARY KEY,
    username TEXT NOT NULL,
    avatar TEXT,
    mc_uuid TEXT,
    mc_name TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
  )`,
  // Códigos de un solo uso para vincular Minecraft con /tf vincular <código> dentro del juego.
  `CREATE TABLE IF NOT EXISTS link_codes (
    code TEXT PRIMARY KEY,
    discord_id TEXT NOT NULL,
    expires_at INTEGER NOT NULL
  )`,
  // Rango comprado de cada jugador (el de nivel más alto).
  `CREATE TABLE IF NOT EXISTS player_ranks (
    uuid TEXT PRIMARY KEY,
    rank_id TEXT NOT NULL,
    tier INTEGER NOT NULL,
    updated_at TEXT NOT NULL
  )`,
];

// Columnas nuevas en tablas que ya existen en la base de datos publicada.
const MIGRATIONS = [
  'ALTER TABLE orders ADD COLUMN uuid TEXT',
  'ALTER TABLE orders ADD COLUMN session_id TEXT',
  'ALTER TABLE orders ADD COLUMN discord_id TEXT',
  'ALTER TABLE orders ADD COLUMN rank_from TEXT',
  'ALTER TABLE deliveries ADD COLUMN uuid TEXT',
  'ALTER TABLE deliveries ADD COLUMN extra TEXT',
  'CREATE INDEX IF NOT EXISTS orders_capture ON orders (capture_id)',
  'CREATE INDEX IF NOT EXISTS orders_discord ON orders (discord_id)',
  'CREATE INDEX IF NOT EXISTS orders_uuid ON orders (uuid)',
];

// Estados en los que el pedido ya se pagó y quedó en manos del puente.
export const CLAIMED = ['queued', 'delivered', 'delivery_failed'];

// Si el servidor no confirma una entrega enviada en este tiempo, se vuelve a enviar.
const RESEND_MS = 2 * 60 * 1000;
const MAX_BATCH = 20;
const LINK_CODE_MS = 10 * 60 * 1000;
const LINK_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

export const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const now = () => new Date().toISOString();

const ORDER_FIELDS = {
  status: 'status',
  username: 'username',
  uuid: 'uuid',
  discord: 'discord',
  discordId: 'discord_id',
  sessionId: 'session_id',
  paymentIntent: 'capture_id',
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
    uuid: row.uuid || null,
    productId: row.product_id,
    quantity: row.quantity,
    amount: row.amount,
    currency: row.currency,
    discord: row.discord ? JSON.parse(row.discord) : null,
    discordId: row.discord_id || null,
    sessionId: row.session_id || null,
    paymentIntent: row.capture_id || null,
    rankFrom: row.rank_from || null,
    error: row.error,
    refunded: row.refunded,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    paidAt: row.paid_at,
    deliveredAt: row.delivered_at,
  };
}

function linkCode() {
  const bytes = crypto.getRandomValues(new Uint8Array(6));
  return [...bytes].map((b) => LINK_ALPHABET[b % LINK_ALPHABET.length]).join('');
}

export function createStore(db) {
  let ready = null;

  // Crea las tablas y añade las columnas nuevas la primera vez (una vez por instancia del Worker).
  function init() {
    ready ||= (async () => {
      await db.batch(SCHEMA.map((sql) => db.prepare(sql)));
      for (const sql of MIGRATIONS) {
        try {
          await db.prepare(sql).run();
        } catch (err) {
          if (!/duplicate column/i.test(String(err?.message))) throw err;
        }
      }
    })().catch((err) => {
      ready = null;
      throw err;
    });
    return ready;
  }

  // --- Pedidos ---

  async function getOrder(id) {
    await init();
    return rowToOrder(await db.prepare('SELECT * FROM orders WHERE id = ?').bind(id).first());
  }

  async function findOrderByPaymentIntent(paymentIntent) {
    await init();
    return rowToOrder(await db.prepare('SELECT * FROM orders WHERE capture_id = ?').bind(paymentIntent).first());
  }

  async function createOrder(order) {
    await init();
    const at = now();
    await db
      .prepare(
        `INSERT INTO orders (id, status, username, uuid, product_id, quantity, amount, currency, discord, discord_id,
           session_id, rank_from, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      )
      .bind(
        order.id,
        order.status,
        order.username,
        order.uuid || null,
        order.productId,
        order.quantity,
        order.amount,
        order.currency,
        order.discord ? JSON.stringify(order.discord) : null,
        order.discordId || null,
        order.sessionId || null,
        order.rankFrom || null,
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

  // Compras de una cuenta: las hechas con su Discord y las de su jugador de Minecraft vinculado.
  async function ordersFor({ discordId, uuid }) {
    await init();
    const { results } = await db
      .prepare(
        `SELECT * FROM orders
         WHERE status NOT IN ('pending', 'expired')
           AND ((discord_id IS NOT NULL AND discord_id = ?) OR (uuid IS NOT NULL AND lower(uuid) = lower(?)))
         ORDER BY created_at DESC LIMIT 50`,
      )
      .bind(discordId || '', uuid || '')
      .all();
    return results.map(rowToOrder);
  }

  // Deja el pedido pagado en la cola del puente. Devuelve false si ya estaba en ella
  // (el webhook y la página de confirmación pueden avisar del mismo pago).
  async function queueDelivery(orderId, { username, uuid, commands, extra, paymentIntent }) {
    await init();
    const at = now();
    const [insert] = await db.batch([
      db
        .prepare('INSERT OR IGNORE INTO deliveries (order_id, username, uuid, commands, extra, created_at) VALUES (?, ?, ?, ?, ?, ?)')
        .bind(orderId, username, uuid || null, JSON.stringify(commands), extra ? JSON.stringify(extra) : null, at),
      db
        .prepare(
          `UPDATE orders SET status = 'queued', capture_id = ?, paid_at = ?, updated_at = ?
           WHERE id = ? AND status NOT IN (${CLAIMED.map(() => '?').join(', ')})`,
        )
        .bind(paymentIntent || null, at, at, orderId, ...CLAIMED),
    ]);
    return insert.meta.changes === 1;
  }

  // --- Jugadores conocidos ---

  // Guarda los jugadores que manda el puente: los conectados y los que ha visto entrar alguna vez.
  async function upsertPlayers(list) {
    await init();
    if (!list.length) return;
    const statements = list.map((p) =>
      db
        .prepare(
          `INSERT INTO players (uuid, name, name_lower, first_seen, last_seen) VALUES (?, ?, ?, ?, ?)
           ON CONFLICT (uuid) DO UPDATE SET
             name = CASE WHEN excluded.last_seen >= players.last_seen THEN excluded.name ELSE players.name END,
             name_lower = CASE WHEN excluded.last_seen >= players.last_seen THEN excluded.name_lower ELSE players.name_lower END,
             last_seen = max(players.last_seen, excluded.last_seen)`,
        )
        .bind(p.uuid.toLowerCase(), p.name, p.name.toLowerCase(), p.at, p.at),
    );
    for (let i = 0; i < statements.length; i += 100) await db.batch(statements.slice(i, i + 100));
  }

  // El jugador con ese nombre (si varios UUID lo usaron, el más reciente).
  async function findPlayer(name) {
    await init();
    const row = await db
      .prepare('SELECT uuid, name, last_seen FROM players WHERE name_lower = ? ORDER BY last_seen DESC LIMIT 1')
      .bind(String(name).toLowerCase())
      .first();
    return row ? { uuid: row.uuid, name: row.name, lastSeen: row.last_seen } : null;
  }

  async function playerByUuid(uuid) {
    await init();
    const row = await db.prepare('SELECT uuid, name, last_seen FROM players WHERE uuid = ?').bind(String(uuid).toLowerCase()).first();
    return row ? { uuid: row.uuid, name: row.name, lastSeen: row.last_seen } : null;
  }

  // --- Rangos ---

  async function getRank(uuid) {
    await init();
    const row = await db.prepare('SELECT rank_id, tier FROM player_ranks WHERE uuid = ?').bind(String(uuid).toLowerCase()).first();
    return row ? { rankId: row.rank_id, tier: row.tier } : null;
  }

  // Rangos de varios jugadores a la vez (los conectados, para el nametag del servidor).
  async function ranksFor(uuids) {
    await init();
    if (!uuids.length) return [];
    const { results } = await db
      .prepare('SELECT uuid, rank_id, tier FROM player_ranks WHERE uuid IN (SELECT lower(value) FROM json_each(?))')
      .bind(JSON.stringify(uuids))
      .all();
    return results.map((r) => ({ uuid: r.uuid, rankId: r.rank_id, tier: r.tier }));
  }

  // Solo sube de rango (una compra antigua o repetida nunca baja a nadie); `force` lo pone tal cual (staff).
  async function setRank(uuid, rankId, tier, { force = false } = {}) {
    await init();
    const id = String(uuid).toLowerCase();
    if (!rankId) {
      await db.prepare('DELETE FROM player_ranks WHERE uuid = ?').bind(id).run();
      return;
    }
    await db
      .prepare(
        `INSERT INTO player_ranks (uuid, rank_id, tier, updated_at) VALUES (?, ?, ?, ?)
         ON CONFLICT (uuid) DO UPDATE SET rank_id = excluded.rank_id, tier = excluded.tier, updated_at = excluded.updated_at
         WHERE ? OR excluded.tier > player_ranks.tier`,
      )
      .bind(id, rankId, tier, now(), force ? 1 : 0)
      .run();
  }

  // --- Cuentas (Discord) ---

  async function upsertAccount({ id, username, avatar }) {
    await init();
    const at = now();
    await db
      .prepare(
        `INSERT INTO accounts (discord_id, username, avatar, created_at, updated_at) VALUES (?, ?, ?, ?, ?)
         ON CONFLICT (discord_id) DO UPDATE SET username = excluded.username, avatar = excluded.avatar, updated_at = excluded.updated_at`,
      )
      .bind(id, username, avatar || null, at, at)
      .run();
  }

  async function getAccount(discordId) {
    await init();
    const row = await db.prepare('SELECT * FROM accounts WHERE discord_id = ?').bind(discordId).first();
    if (!row) return null;
    return {
      discordId: row.discord_id,
      username: row.username,
      avatar: row.avatar,
      minecraft: row.mc_uuid ? { uuid: row.mc_uuid, name: row.mc_name } : null,
    };
  }

  // La cuenta de Discord vinculada a un jugador (para darle el rol aunque compre sin iniciar sesión).
  async function accountByUuid(uuid) {
    await init();
    const row = await db.prepare('SELECT discord_id FROM accounts WHERE lower(mc_uuid) = lower(?)').bind(String(uuid)).first();
    return row ? getAccount(row.discord_id) : null;
  }

  async function unlinkMinecraft(discordId) {
    await init();
    await db.prepare('UPDATE accounts SET mc_uuid = NULL, mc_name = NULL, updated_at = ? WHERE discord_id = ?').bind(now(), discordId).run();
  }

  // Código para escribir en el juego (/tf vincular CÓDIGO). Uno por cuenta: pedir otro anula el anterior.
  async function createLinkCode(discordId) {
    await init();
    const code = linkCode();
    const expires = Date.now() + LINK_CODE_MS;
    await db.batch([
      db.prepare('DELETE FROM link_codes WHERE discord_id = ? OR expires_at < ?').bind(discordId, Date.now()),
      db.prepare('INSERT INTO link_codes (code, discord_id, expires_at) VALUES (?, ?, ?)').bind(code, discordId, expires),
    ]);
    return { code, expiresAt: expires };
  }

  async function pendingLinkCode(discordId) {
    await init();
    const row = await db
      .prepare('SELECT code, expires_at FROM link_codes WHERE discord_id = ? AND expires_at > ?')
      .bind(discordId, Date.now())
      .first();
    return row ? { code: row.code, expiresAt: row.expires_at } : null;
  }

  // Un jugador escribió /tf vincular CÓDIGO en el juego: une su UUID a la cuenta de Discord del código.
  async function useLinkCode(code, { uuid, name }) {
    await init();
    const row = await db
      .prepare('SELECT discord_id FROM link_codes WHERE code = ? AND expires_at > ?')
      .bind(String(code).toUpperCase(), Date.now())
      .first();
    if (!row) return null;
    const at = now();
    await db.batch([
      db.prepare('DELETE FROM link_codes WHERE code = ?').bind(String(code).toUpperCase()),
      // Un jugador solo puede estar vinculado a una cuenta.
      db.prepare('UPDATE accounts SET mc_uuid = NULL, mc_name = NULL WHERE lower(mc_uuid) = lower(?) AND discord_id <> ?').bind(uuid, row.discord_id),
      db.prepare('UPDATE accounts SET mc_uuid = ?, mc_name = ?, updated_at = ? WHERE discord_id = ?').bind(uuid.toLowerCase(), name, at, row.discord_id),
    ]);
    return getAccount(row.discord_id);
  }

  // --- Puente ---

  // Confirma las entregas hechas, guarda el estado del servidor y recoge las entregas pendientes
  // de los jugadores conectados (por UUID; las antiguas sin UUID, por nombre).
  async function bridgePoll({ players, max, done }) {
    await init();
    const at = now();
    const ms = Date.now();
    const statements = [];

    for (const item of done) {
      const ok = item.ok === true;
      const error = ok ? null : String(item.error || 'Error').slice(0, 500);
      statements.push(
        db
          .prepare(
            `UPDATE deliveries SET status = ?, done_at = ?, error = ?
             WHERE id = ? AND status IN ('pending', 'sent')`,
          )
          .bind(ok ? 'done' : 'failed', at, error, item.id),
        db
          .prepare(
            `UPDATE orders SET status = ?, delivered_at = ?, error = ?, updated_at = ?
             WHERE id = (SELECT order_id FROM deliveries WHERE id = ?) AND status = 'queued'`,
          )
          .bind(ok ? 'delivered' : 'delivery_failed', ok ? at : null, error, at, item.id),
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
    await db.batch(statements);

    const names = JSON.stringify(players.map((p) => p.name.toLowerCase()));
    const uuids = JSON.stringify(players.filter((p) => p.uuid).map((p) => p.uuid.toLowerCase()));
    const { results } = await db
      .prepare(
        `SELECT d.id, d.username, d.uuid, d.commands, d.extra, o.product_id, o.quantity FROM deliveries d
         JOIN orders o ON o.id = d.order_id
         WHERE (d.status = 'pending' OR (d.status = 'sent' AND d.sent_at < ?))
           AND ((d.uuid IS NOT NULL AND lower(d.uuid) IN (SELECT value FROM json_each(?)))
             OR (d.uuid IS NULL AND lower(d.username) IN (SELECT value FROM json_each(?))))
         ORDER BY d.id LIMIT ${MAX_BATCH}`,
      )
      .bind(ms - RESEND_MS, uuids, names)
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
      uuid: r.uuid || null,
      productId: r.product_id,
      quantity: r.quantity,
      commands: JSON.parse(r.commands),
      ...(r.extra ? JSON.parse(r.extra) : {}),
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

  return {
    getOrder,
    findOrderByPaymentIntent,
    createOrder,
    updateOrder,
    ordersFor,
    queueDelivery,
    upsertPlayers,
    findPlayer,
    playerByUuid,
    getRank,
    ranksFor,
    setRank,
    upsertAccount,
    getAccount,
    accountByUuid,
    unlinkMinecraft,
    createLinkCode,
    pendingLinkCode,
    useLinkCode,
    bridgePoll,
    serverStatus,
    pendingDeliveries,
    secret,
  };
}
