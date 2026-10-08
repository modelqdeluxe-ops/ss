// Datos de la tienda en Cloudflare D1 (SQLite): pedidos, entregas para el puente del servidor de Minecraft,
// jugadores conocidos (nombre + UUID), cuentas de la web con su Discord, rangos y ajustes internos.
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
  // Cuentas de la web: una por jugador (UUID del servidor), con contraseña y su Discord conectado.
  `CREATE TABLE IF NOT EXISTS users (
    uuid TEXT PRIMARY KEY,
    password TEXT NOT NULL,
    discord_id TEXT UNIQUE,
    discord_username TEXT,
    discord_name TEXT,
    discord_avatar TEXT,
    discord_member INTEGER NOT NULL DEFAULT 0,
    discord_checked_at TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
  )`,
  // Tienda de monedas: objetos del servidor que el staff añade desde el juego (/tf tienda add).
  `CREATE TABLE IF NOT EXISTS coin_shop (
    id TEXT PRIMARY KEY,
    item TEXT NOT NULL,
    name TEXT NOT NULL,
    count INTEGER NOT NULL,
    price INTEGER NOT NULL,
    nbt TEXT,
    added_by TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
  )`,
  // Intentos fallidos de inicio de sesión, para frenar a quien prueba contraseñas.
  `CREATE TABLE IF NOT EXISTS login_attempts (
    key TEXT PRIMARY KEY,
    failures INTEGER NOT NULL,
    locked_until INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL
  )`,
  // Aceptaciones de los Términos y del Aviso de privacidad (casilla al crear la cuenta, al pagar o cuando cambian):
  // quién, qué versión, cuándo, dónde (crear cuenta, pagar…) y desde qué IP y navegador. Es la prueba de que se
  // aceptaron; no se borra al cambiar los términos (cada versión aceptada queda en su fila).
  `CREATE TABLE IF NOT EXISTS terms_acceptances (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    uuid TEXT,
    username TEXT,
    context TEXT NOT NULL,
    version TEXT NOT NULL,
    order_id TEXT,
    ip TEXT,
    user_agent TEXT,
    created_at TEXT NOT NULL
  )`,
  'CREATE INDEX IF NOT EXISTS terms_uuid ON terms_acceptances (uuid)',
  // Rango comprado de cada jugador (el de nivel más alto).
  `CREATE TABLE IF NOT EXISTS player_ranks (
    uuid TEXT PRIMARY KEY,
    rank_id TEXT NOT NULL,
    tier INTEGER NOT NULL,
    updated_at TEXT NOT NULL
  )`,
  // VFX que tiene cada jugador (item = kill:<id> o pack:<id>) y los que lleva equipados. updated_at (ms) le dice al
  // servidor si el cambio es nuevo.
  `CREATE TABLE IF NOT EXISTS vfx_owned (
    uuid TEXT NOT NULL,
    item TEXT NOT NULL,
    created_at TEXT NOT NULL,
    PRIMARY KEY (uuid, item)
  )`,
  `CREATE TABLE IF NOT EXISTS vfx_equip (
    uuid TEXT PRIMARY KEY,
    kill TEXT,
    pack TEXT,
    updated_at INTEGER NOT NULL
  )`,
  // Armario: la pieza que se ve en cada hueco (head, chest, legs, feet, back → "set/pieza"), elegida en la web entre
  // lo comprado. Solo es apariencia: el servidor la manda a los clientes y la dibujan encima de lo que lleve puesto.
  `CREATE TABLE IF NOT EXISTS wardrobe (
    uuid TEXT PRIMARY KEY,
    items TEXT NOT NULL,
    updated_at INTEGER NOT NULL
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
  'ALTER TABLE orders ADD COLUMN prizes TEXT',
  'ALTER TABLE users ADD COLUMN terms_version TEXT',
  'ALTER TABLE users ADD COLUMN terms_at TEXT',
  'ALTER TABLE orders ADD COLUMN terms_version TEXT',
  // Retiradas por reembolso: una entrega «al revés» del pedido reembolsado (order_id = R + pedido).
  'ALTER TABLE deliveries ADD COLUMN revoke_of TEXT',
  // Con qué se pagó: 'stripe' (o vacío, los de antes) o 'paypal'. session_id guarda la sesión de Stripe o el pedido
  // de PayPal, y capture_id el pago (payment intent de Stripe o captura de PayPal).
  'ALTER TABLE orders ADD COLUMN method TEXT',
  'CREATE INDEX IF NOT EXISTS orders_session ON orders (session_id)',
  // Saldo de monedas del jugador según el puente (se actualiza mientras está conectado) y cuándo se leyó.
  'ALTER TABLE players ADD COLUMN coins INTEGER',
  'ALTER TABLE players ADD COLUMN coins_at INTEGER',
];

// Estados en los que el pedido ya se pagó y quedó en manos del puente.
export const CLAIMED = ['queued', 'delivered', 'delivery_failed'];

// Si el servidor no confirma una entrega enviada en este tiempo, se vuelve a enviar.
const RESEND_MS = 2 * 60 * 1000;
const MAX_BATCH = 20;
// Tras tantos fallos seguidos, la cuenta no deja probar más contraseñas durante un rato.
const MAX_FAILURES = 8;
const LOCK_MS = 15 * 60 * 1000;

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
  prizes: 'prizes',
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
    termsVersion: row.terms_version || null,
    method: row.method || (row.session_id ? 'stripe' : null),
    prizes: row.prizes ? JSON.parse(row.prizes) : null,
    error: row.error,
    refunded: row.refunded,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    paidAt: row.paid_at,
    deliveredAt: row.delivered_at,
  };
}

function rowToUser(row) {
  if (!row) return null;
  return {
    uuid: row.uuid,
    name: row.name,
    password: row.password,
    discord: row.discord_id
      ? {
          id: row.discord_id,
          username: row.discord_username,
          name: row.discord_name,
          avatar: row.discord_avatar,
          member: row.discord_member === 1,
          checkedAt: row.discord_checked_at,
        }
      : null,
    termsVersion: row.terms_version || null,
    termsAt: row.terms_at || null,
    createdAt: row.created_at,
  };
}

const USER_SELECT = 'SELECT u.*, p.name FROM users u LEFT JOIN players p ON p.uuid = u.uuid';

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

  async function findOrderBySession(sessionId) {
    await init();
    return rowToOrder(await db.prepare('SELECT * FROM orders WHERE session_id = ?').bind(sessionId).first());
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
           session_id, rank_from, terms_version, method, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
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
        order.termsVersion || null,
        order.method || null,
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
      values.push((key === 'discord' || key === 'prizes') && value ? JSON.stringify(value) : value ?? null);
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

  // Reembolso o disputa: lo que aún no se había entregado ya no se entrega y se encola la retirada del servidor (con
  // el jugador conectado o no: el mod quita lo de ese pedido esté donde esté). Devuelve false si ya estaba encolada.
  async function queueRevocation(order, commands) {
    await init();
    const at = now();
    const [, insert] = await db.batch([
      db
        .prepare(
          `UPDATE deliveries SET status = 'cancelled', error = 'Reembolsado antes de entregarse', done_at = ?
           WHERE order_id = ? AND status = 'pending'`,
        )
        .bind(at, order.id),
      db
        .prepare(
          `INSERT OR IGNORE INTO deliveries (order_id, revoke_of, username, uuid, commands, extra, created_at)
           VALUES (?, ?, ?, ?, ?, ?, ?)`,
        )
        .bind(`R${order.id}`, order.id, order.username, order.uuid || null, JSON.stringify(commands), JSON.stringify({ kind: 'revocacion' }), at),
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
    const row = await db
      .prepare('SELECT uuid, name, last_seen, coins, coins_at FROM players WHERE uuid = ?')
      .bind(String(uuid).toLowerCase())
      .first();
    return row
      ? { uuid: row.uuid, name: row.name, lastSeen: row.last_seen, coins: row.coins ?? null, coinsAt: row.coins_at ?? null }
      : null;
  }

  // Saldos de monedas que manda el puente: [{ uuid, coins }].
  async function setCoins(list) {
    await init();
    if (!list.length) return;
    const at = Date.now();
    const statements = list.map((p) =>
      db.prepare('UPDATE players SET coins = ?, coins_at = ? WHERE uuid = ?').bind(p.coins, at, p.uuid.toLowerCase()),
    );
    for (let i = 0; i < statements.length; i += 100) await db.batch(statements.slice(i, i + 100));
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

  // --- VFX ---

  // Lo que tiene y lleva equipado un jugador.
  async function vfxFor(uuid) {
    await init();
    const id = String(uuid).toLowerCase();
    const { results } = await db.prepare('SELECT item FROM vfx_owned WHERE uuid = ? ORDER BY created_at').bind(id).all();
    const eq = await db.prepare('SELECT kill, pack, updated_at FROM vfx_equip WHERE uuid = ?').bind(id).first();
    return { owned: results.map((r) => r.item), kill: eq?.kill || null, pack: eq?.pack || null, at: eq?.updated_at || 0 };
  }

  async function vfxGive(uuid, item) {
    await init();
    await db
      .prepare('INSERT OR IGNORE INTO vfx_owned (uuid, item, created_at) VALUES (?, ?, ?)')
      .bind(String(uuid).toLowerCase(), item, now())
      .run();
  }

  // Equipa (o quita, con null) el efecto de kill o el paquete de skills. Siempre con una hora nueva: el servidor solo
  // aplica lo que es más reciente que lo que ya tiene.
  async function vfxEquip(uuid, kind, id) {
    await init();
    const key = String(uuid).toLowerCase();
    const cur = await db.prepare('SELECT kill, pack, updated_at FROM vfx_equip WHERE uuid = ?').bind(key).first();
    const next = { kill: cur?.kill || null, pack: cur?.pack || null };
    next[kind] = id || null;
    const at = Math.max(Date.now(), (cur?.updated_at || 0) + 1);
    await db
      .prepare(
        `INSERT INTO vfx_equip (uuid, kill, pack, updated_at) VALUES (?, ?, ?, ?)
         ON CONFLICT (uuid) DO UPDATE SET kill = excluded.kill, pack = excluded.pack, updated_at = excluded.updated_at`,
      )
      .bind(key, next.kill, next.pack, at)
      .run();
    return { ...next, at };
  }

  // Lo equipado de varios jugadores (los conectados), para el puente.
  async function vfxEquipFor(uuids) {
    await init();
    if (!uuids.length) return [];
    const { results } = await db
      .prepare('SELECT uuid, kill, pack, updated_at FROM vfx_equip WHERE uuid IN (SELECT lower(value) FROM json_each(?))')
      .bind(JSON.stringify(uuids))
      .all();
    return results.map((r) => ({ uuid: r.uuid, kill: r.kill, pack: r.pack, at: r.updated_at }));
  }

  // --- Armario ---

  // Productos que tiene un jugador: sus pedidos pagados (entregados o en cola) que no se han reembolsado.
  async function ownedProducts(uuid) {
    await init();
    const { results } = await db
      .prepare(
        `SELECT DISTINCT product_id FROM orders
         WHERE uuid IS NOT NULL AND lower(uuid) = lower(?) AND status IN (${CLAIMED.map(() => '?').join(', ')})
           AND (refunded IS NULL OR refunded = '')`,
      )
      .bind(uuid, ...CLAIMED)
      .all();
    return results.map((r) => r.product_id);
  }

  async function wardrobeFor(uuid) {
    await init();
    const row = await db.prepare('SELECT items, updated_at FROM wardrobe WHERE uuid = ?').bind(String(uuid).toLowerCase()).first();
    return { items: row ? JSON.parse(row.items) : {}, at: row?.updated_at || 0 };
  }

  // Pone (o quita, con null) la pieza de un hueco. Siempre con una hora nueva, como los VFX.
  async function setWardrobe(uuid, slot, piece) {
    await init();
    const key = String(uuid).toLowerCase();
    const cur = await wardrobeFor(key);
    const items = { ...cur.items };
    if (piece) items[slot] = piece;
    else delete items[slot];
    const at = Math.max(Date.now(), cur.at + 1);
    await db
      .prepare(
        `INSERT INTO wardrobe (uuid, items, updated_at) VALUES (?, ?, ?)
         ON CONFLICT (uuid) DO UPDATE SET items = excluded.items, updated_at = excluded.updated_at`,
      )
      .bind(key, JSON.stringify(items), at)
      .run();
    return { items, at };
  }

  // El armario de varios jugadores (los conectados), para el puente.
  async function wardrobesFor(uuids) {
    await init();
    if (!uuids.length) return [];
    const { results } = await db
      .prepare('SELECT uuid, items, updated_at FROM wardrobe WHERE uuid IN (SELECT lower(value) FROM json_each(?))')
      .bind(JSON.stringify(uuids))
      .all();
    return results.map((r) => ({ uuid: r.uuid, items: JSON.parse(r.items), at: r.updated_at }));
  }

  // --- Cuentas de la web ---

  // Crea la cuenta del jugador. Devuelve false si ese jugador ya tiene cuenta.
  async function createUser({ uuid, password }) {
    await init();
    const at = now();
    const res = await db
      .prepare('INSERT OR IGNORE INTO users (uuid, password, created_at, updated_at) VALUES (?, ?, ?, ?)')
      .bind(String(uuid).toLowerCase(), password, at, at)
      .run();
    return res.meta.changes === 1;
  }

  async function getUser(uuid) {
    await init();
    return rowToUser(await db.prepare(`${USER_SELECT} WHERE u.uuid = ?`).bind(String(uuid).toLowerCase()).first());
  }

  async function userByDiscord(discordId) {
    await init();
    return rowToUser(await db.prepare(`${USER_SELECT} WHERE u.discord_id = ?`).bind(String(discordId)).first());
  }

  // Conecta (o actualiza) la cuenta de Discord. Devuelve false si ese Discord ya es de otro jugador.
  async function setUserDiscord(uuid, { id, username, name, avatar, member }) {
    await init();
    const other = await db.prepare('SELECT uuid FROM users WHERE discord_id = ? AND uuid <> ?').bind(id, String(uuid).toLowerCase()).first();
    if (other) return false;
    const at = now();
    await db
      .prepare(
        `UPDATE users SET discord_id = ?, discord_username = ?, discord_name = ?, discord_avatar = ?, discord_member = ?,
           discord_checked_at = ?, updated_at = ? WHERE uuid = ?`,
      )
      .bind(id, username || null, name || null, avatar || null, member ? 1 : 0, at, at, String(uuid).toLowerCase())
      .run();
    return true;
  }

  async function setDiscordMember(uuid, member) {
    await init();
    const at = now();
    await db
      .prepare('UPDATE users SET discord_member = ?, discord_checked_at = ?, updated_at = ? WHERE uuid = ?')
      .bind(member ? 1 : 0, at, at, String(uuid).toLowerCase())
      .run();
  }

  async function unlinkDiscord(uuid) {
    await init();
    await db
      .prepare(
        `UPDATE users SET discord_id = NULL, discord_username = NULL, discord_name = NULL, discord_avatar = NULL,
           discord_member = 0, discord_checked_at = NULL, updated_at = ? WHERE uuid = ?`,
      )
      .bind(now(), String(uuid).toLowerCase())
      .run();
  }

  async function setPassword(uuid, password) {
    await init();
    await db.prepare('UPDATE users SET password = ?, updated_at = ? WHERE uuid = ?').bind(password, now(), String(uuid).toLowerCase()).run();
  }

  // --- Aceptación de los Términos ---

  // Guarda la aceptación (siempre una fila nueva) y, si es de un jugador con cuenta, la versión aceptada en su cuenta.
  async function acceptTerms({ uuid, username, context, version, orderId, ip, userAgent }) {
    await init();
    const at = now();
    const id = uuid ? String(uuid).toLowerCase() : null;
    const statements = [
      db
        .prepare(
          `INSERT INTO terms_acceptances (uuid, username, context, version, order_id, ip, user_agent, created_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
        )
        .bind(id, username || null, context, version, orderId || null, ip || null, userAgent ? String(userAgent).slice(0, 300) : null, at),
    ];
    if (id) {
      statements.push(db.prepare('UPDATE users SET terms_version = ?, terms_at = ? WHERE uuid = ?').bind(version, at, id));
    }
    await db.batch(statements);
  }

  async function termsAcceptances(uuid) {
    await init();
    const { results } = await db
      .prepare('SELECT * FROM terms_acceptances WHERE uuid = ? ORDER BY id DESC LIMIT 50')
      .bind(String(uuid).toLowerCase())
      .all();
    return results.map((r) => ({
      context: r.context,
      version: r.version,
      orderId: r.order_id,
      at: r.created_at,
    }));
  }

  // La cuenta de Discord del jugador (para darle el rol aunque compre sin iniciar sesión).
  async function accountByUuid(uuid) {
    const user = await getUser(uuid);
    return user?.discord ? { discordId: user.discord.id, username: user.discord.username } : null;
  }

  // --- Freno a quien prueba contraseñas ---

  async function loginLocked(key) {
    await init();
    const row = await db.prepare('SELECT locked_until FROM login_attempts WHERE key = ?').bind(key).first();
    return row && row.locked_until > Date.now() ? row.locked_until : 0;
  }

  async function loginFailed(key) {
    await init();
    const ms = Date.now();
    await db
      .prepare(
        `INSERT INTO login_attempts (key, failures, locked_until, updated_at) VALUES (?, 1, 0, ?)
         ON CONFLICT (key) DO UPDATE SET
           failures = CASE WHEN login_attempts.updated_at < ? THEN 1 ELSE login_attempts.failures + 1 END,
           locked_until = CASE WHEN login_attempts.updated_at >= ? AND login_attempts.failures + 1 >= ? THEN ? ELSE 0 END,
           updated_at = excluded.updated_at`,
      )
      .bind(key, ms, ms - LOCK_MS, ms - LOCK_MS, MAX_FAILURES, ms + LOCK_MS)
      .run();
  }

  async function loginSucceeded(key) {
    await init();
    await db.prepare('DELETE FROM login_attempts WHERE key = ?').bind(key).run();
  }

  // --- Tienda de monedas ---

  async function coinShop() {
    await init();
    const { results } = await db.prepare('SELECT * FROM coin_shop ORDER BY created_at').all();
    return results.map((r) => ({
      id: r.id, item: r.item, name: r.name, count: r.count, price: r.price, nbt: r.nbt || null,
      addedBy: r.added_by || null, createdAt: r.created_at, updatedAt: r.updated_at,
    }));
  }

  // Cambios que manda el servidor: { op: 'add', id, item, name, count, price, nbt, by } | { op: 'remove', id } | { op: 'clear' }
  async function applyCoinShop(ops) {
    await init();
    const at = now();
    const statements = [];
    for (const op of ops) {
      if (op.op === 'clear') statements.push(db.prepare('DELETE FROM coin_shop'));
      else if (op.op === 'remove') statements.push(db.prepare('DELETE FROM coin_shop WHERE id = ?').bind(op.id));
      else if (op.op === 'add') {
        statements.push(
          db
            .prepare(
              `INSERT INTO coin_shop (id, item, name, count, price, nbt, added_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
               ON CONFLICT (id) DO UPDATE SET item = excluded.item, name = excluded.name, count = excluded.count,
                 price = excluded.price, nbt = excluded.nbt, updated_at = excluded.updated_at`,
            )
            .bind(op.id, op.item, op.name, op.count, op.price, op.nbt || null, op.by || null, at, at),
        );
      }
    }
    if (statements.length) {
      await db.batch(statements);
      await db
        .prepare(
          `INSERT INTO settings (key, value, updated_at) VALUES ('coin_shop_version', ?, ?)
           ON CONFLICT (key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at`,
        )
        .bind(at, Date.now())
        .run();
    }
    return statements.length;
  }

  async function coinShopVersion() {
    await init();
    const row = await db.prepare("SELECT value FROM settings WHERE key = 'coin_shop_version'").first();
    return row?.value || null;
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
      // Retirada por reembolso hecha: se apunta en el pedido reembolsado
      if (ok) {
        statements.push(
          db
            .prepare(
              `UPDATE orders SET refunded = COALESCE(refunded, 'reembolsado') || ' · retirado del servidor', updated_at = ?
               WHERE id = (SELECT revoke_of FROM deliveries WHERE id = ?) AND COALESCE(refunded, '') NOT LIKE '%retirado%'`,
            )
            .bind(at, item.id),
        );
      }
      // Ruleta con monedas: el servidor elige los premios y los manda con la confirmación
      if (ok && Array.isArray(item.prizes) && item.prizes.length) {
        statements.push(
          db
            .prepare(`UPDATE orders SET prizes = ? WHERE id = (SELECT order_id FROM deliveries WHERE id = ?)`)
            .bind(JSON.stringify(item.prizes), item.id),
        );
      }
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
        `SELECT d.id, d.username, d.uuid, d.commands, d.extra, COALESCE(d.revoke_of, d.order_id) AS order_ref,
           o.product_id, o.quantity FROM deliveries d
         JOIN orders o ON o.id = COALESCE(d.revoke_of, d.order_id)
         WHERE (d.status = 'pending' OR (d.status = 'sent' AND d.sent_at < ?))
           AND (d.revoke_of IS NOT NULL
             OR (d.uuid IS NOT NULL AND lower(d.uuid) IN (SELECT value FROM json_each(?)))
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
      // El pedido: el mod lo marca en lo que entrega (y así sabe qué quitar si se reembolsa).
      order: r.order_ref,
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
      .prepare("SELECT COUNT(*) AS n FROM deliveries WHERE lower(username) = lower(?) AND status IN ('pending', 'sent') AND revoke_of IS NULL")
      .bind(username)
      .first();
    return row?.n || 0;
  }

  // Ajustes sueltos guardados por la web (p. ej. la invitación de Discord): { value, at } o null.
  async function getSetting(key) {
    await init();
    const row = await db.prepare('SELECT value, updated_at FROM settings WHERE key = ?').bind(key).first();
    return row ? { value: row.value, at: row.updated_at } : null;
  }

  async function setSetting(key, value) {
    await init();
    await db
      .prepare('INSERT INTO settings (key, value, updated_at) VALUES (?, ?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at')
      .bind(key, String(value), Date.now())
      .run();
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
    getSetting,
    setSetting,
    getOrder,
    findOrderByPaymentIntent,
    findOrderBySession,
    createOrder,
    updateOrder,
    ordersFor,
    queueDelivery,
    queueRevocation,
    upsertPlayers,
    setCoins,
    findPlayer,
    playerByUuid,
    getRank,
    ranksFor,
    setRank,
    vfxFor,
    vfxGive,
    vfxEquip,
    vfxEquipFor,
    ownedProducts,
    wardrobeFor,
    setWardrobe,
    wardrobesFor,
    createUser,
    getUser,
    userByDiscord,
    setUserDiscord,
    setDiscordMember,
    unlinkDiscord,
    setPassword,
    accountByUuid,
    acceptTerms,
    termsAcceptances,
    loginLocked,
    loginFailed,
    loginSucceeded,
    bridgePoll,
    coinShop,
    applyCoinShop,
    coinShopVersion,
    serverStatus,
    pendingDeliveries,
    secret,
  };
}
