// (4 nov 2026, el dueño: «que la web con el puente se conecte a la data de nLogin y saque esas gráficas sola») Las
// métricas de jugadores con los registros de nLogin del servidor: el TF Client (1.3.174+) los lee de
// plugins/nLogin/logs y los manda por el puente (/bridge/poll, campo «nlogin»). Aquí se guardan y se juntan en sesiones;
// la página /metricas (con contraseña de staff) hace las gráficas y el Excel con lo que da nloginData.
//
// Solo llegan el nombre de la cuenta, su fecha de creación y las horas: nunca IP, contraseñas ni correos (el mod no los
// lee de la línea).
//
// El cursor (por dónde va la lectura: archivo de registro + byte) lo guarda la web: el mod pregunta por dónde seguir y
// solo manda lo que va después. Si la web perdiera los datos, el mod vuelve a empezar desde «desde».

// Tipos de evento: entra (nLogin apunta la IP y la hora), sale, se registra, arranca o se apaga el servidor, alguien
// intenta entrar con otra versión de Minecraft (el nombre es entonces su versión, p. ej. «V_1_20») y Mojang no contesta
// al comprobar una cuenta premium.
export const NLOGIN_KINDS = ['in', 'out', 'reg', 'start', 'stop', 'verr', 'mojang'];
// Lo que se acepta por consulta del puente (el mod manda como mucho 400)
export const NLOGIN_MAX_EVENTS = 1000;
// Una sesión sin salida apuntada (el servidor se cayó) termina en el siguiente arranque o apagado si fue antes de 6 h;
// una de más de 12 h no se cuenta.
const CUT_MAX_MS = 6 * 3600 * 1000;
const SESSION_MAX_MS = 12 * 3600 * 1000;
const NAME_RE = /^[A-Za-z0-9_.*-]{1,32}$/;
const ID_RE = /^[\w.-]{1,80}:\d{1,12}$/;
const FILE_RE = /^[\w.-]{1,80}$/;
const CURSOR_KEY = 'nlogin_cursor';
// Sin consulta del puente en este tiempo, el servidor se apagó (o se cortó la conexión)
export const BRIDGE_GAP_MS = 60 * 1000;

function cleanCursor(c) {
  if (c == null) return null;
  if (typeof c !== 'object' || typeof c.file !== 'string' || !FILE_RE.test(c.file)) return undefined;
  if (!Number.isSafeInteger(c.offset) || c.offset < 0) return undefined;
  return { file: c.file, offset: c.offset };
}

const sameCursor = (a, b) => (a === null && b === null) || (a && b && a.file === b.file && a.offset === b.offset);

// Un evento del mod: [id, t (ms), tipo, nombre o null, creación de la cuenta (ms) o null]
function cleanEvent(e, now) {
  if (!Array.isArray(e)) return null;
  const [id, t, kind, name, created] = e;
  if (typeof id !== 'string' || !ID_RE.test(id)) return null;
  if (!Number.isSafeInteger(t) || t < 1.6e12 || t > now + 86400000) return null;
  if (!NLOGIN_KINDS.includes(kind)) return null;
  const needsName = kind === 'in' || kind === 'out' || kind === 'reg';
  const okName = typeof name === 'string' && NAME_RE.test(name);
  if (needsName && !okName) return null;
  return {
    id,
    t,
    kind,
    name: okName ? name : null,
    created: Number.isSafeInteger(created) && created > 1.4e12 && created <= now + 86400000 ? created : null,
  };
}

// Varias filas en un INSERT (D1: como mucho 100 valores por consulta)
function multiInsert(db, head, tail, rows, width) {
  const out = [];
  const per = Math.max(1, Math.floor(99 / width));
  for (let i = 0; i < rows.length; i += per) {
    const chunk = rows.slice(i, i + per);
    const sql = `${head} VALUES ${chunk.map(() => `(${Array(width).fill('?').join(', ')})`).join(', ')} ${tail}`;
    out.push(db.prepare(sql).bind(...chunk.flat()));
  }
  return out;
}

export function createNLogin(db, init, { desde }) {
  async function cursor() {
    await init();
    const row = await db.prepare('SELECT value FROM settings WHERE key = ?').bind(CURSOR_KEY).first();
    if (!row) return null;
    try {
      return cleanCursor(JSON.parse(row.value)) || null;
    } catch {
      return null;
    }
  }

  // Lo que manda el puente: { want } para preguntar por dónde seguir, o { from, to, events }. Solo se guarda si «from»
  // es justo donde se quedó la web (así nunca entra nada dos veces). Devuelve lo que contesta la web al mod.
  async function ingest(body, now = Date.now()) {
    const stored = await cursor();
    const answer = (ok) => ({ cursor: stored, desde, ok });
    if (!body || typeof body !== 'object' || !Array.isArray(body.events)) return answer(0);
    const from = cleanCursor(body.from);
    const to = cleanCursor(body.to);
    if (from === undefined || !to || !sameCursor(from, stored)) return answer(0);

    const events = body.events.slice(0, NLOGIN_MAX_EVENTS).map((e) => cleanEvent(e, now)).filter(Boolean);
    const keys = [...new Set(events.filter((e) => e.name).map((e) => e.name.toLowerCase()))];

    // Lo que ya hay de esos jugadores: su sesión abierta y su ficha
    const open = new Map();
    const players = new Map();
    for (let i = 0; i < keys.length; i += 90) {
      const chunk = JSON.stringify(keys.slice(i, i + 90));
      const o = await db.prepare('SELECT key, start FROM nlogin_open WHERE key IN (SELECT value FROM json_each(?))').bind(chunk).all();
      for (const r of o.results) open.set(r.key, r.start);
      const p = await db
        .prepare('SELECT key, name, created, first_seen, last_seen FROM nlogin_players WHERE key IN (SELECT value FROM json_each(?))')
        .bind(chunk)
        .all();
      for (const r of p.results) players.set(r.key, { ...r, dirty: false });
    }
    // Los arranques y apagados desde la sesión abierta más antigua (para cerrar las que no tienen salida)
    const bounds = [];
    const oldest = Math.min(...open.values(), Infinity);
    if (oldest !== Infinity) {
      const b = await db.prepare("SELECT t FROM nlogin_events WHERE kind IN ('start', 'stop') AND t >= ? ORDER BY t").bind(oldest).all();
      for (const r of b.results) bounds.push(r.t);
    }

    const sessions = [];
    const closed = new Set();
    const touch = (e) => {
      const key = e.name.toLowerCase();
      const p = players.get(key) || { key, name: e.name, created: null, first_seen: e.t, last_seen: e.t };
      p.name = e.name;
      const created = e.created ?? (e.kind === 'reg' ? e.t : null);
      if (created != null && (p.created == null || created < p.created)) p.created = created;
      p.first_seen = Math.min(p.first_seen ?? e.t, e.t);
      p.last_seen = Math.max(p.last_seen ?? e.t, e.t);
      p.dirty = true;
      players.set(key, p);
      return key;
    };
    for (const e of events) {
      if (e.kind === 'start' || e.kind === 'stop') {
        bounds.push(e.t);
        continue;
      }
      if (e.kind !== 'in' && e.kind !== 'out' && e.kind !== 'reg') continue;
      const key = touch(e);
      if (e.kind === 'out') {
        if (open.has(key)) {
          const s = open.get(key);
          open.delete(key);
          closed.add(key);
          if (e.t >= s && e.t - s <= SESSION_MAX_MS) sessions.push([key, s, e.t]);
        }
        continue;
      }
      // Entra (o se registra: su primera entrada no pasa por la línea de la IP)
      if (e.kind === 'reg' && open.has(key)) continue;
      if (open.has(key)) {
        // Entró otra vez sin salida apuntada: la de antes acabó en el arranque o apagado de en medio
        const s = open.get(key);
        const cut = bounds.filter((b) => b > s && b <= e.t).sort((a, b) => a - b)[0];
        if (cut !== undefined && cut - s < CUT_MAX_MS) sessions.push([key, s, cut]);
      }
      open.set(key, e.t);
      closed.delete(key);
    }

    const st = [];
    st.push(
      ...multiInsert(
        db,
        'INSERT OR IGNORE INTO nlogin_events (id, t, kind, name)',
        '',
        events.map((e) => [e.id, e.t, e.kind, e.name ? e.name.toLowerCase() : null]),
        4,
      ),
    );
    st.push(...multiInsert(db, 'INSERT OR REPLACE INTO nlogin_sessions (key, start, end)', '', sessions, 3));
    if (closed.size) {
      st.push(db.prepare('DELETE FROM nlogin_open WHERE key IN (SELECT value FROM json_each(?))').bind(JSON.stringify([...closed])));
    }
    const opened = keys.filter((k) => open.has(k)).map((k) => [k, open.get(k)]);
    st.push(...multiInsert(db, 'INSERT OR REPLACE INTO nlogin_open (key, start)', '', opened, 2));
    const dirty = [...players.values()].filter((p) => p.dirty).map((p) => [p.key, p.name, p.created, p.first_seen, p.last_seen]);
    st.push(...multiInsert(db, 'INSERT OR REPLACE INTO nlogin_players (key, name, created, first_seen, last_seen)', '', dirty, 5));
    st.push(
      db
        .prepare('INSERT INTO settings (key, value, updated_at) VALUES (?, ?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at')
        .bind(CURSOR_KEY, JSON.stringify(to), now),
    );
    await db.batch(st);
    return { cursor: to, desde, ok: events.length };
  }

  // --- Sin el TF Client 1.3.174 en el servidor: lo que ya manda el puente (4 nov 2026) ---
  // El dueño no puede cambiar el mod ahora: la web apunta las sesiones con la lista de conectados que manda el puente
  // cada 10 s (/bridge/poll). Entra = aparece en la lista; sale = deja de aparecer (acaba en la consulta anterior). Si el
  // puente no llama en 60 s, el servidor se apagó (o se cortó): todas las sesiones acaban en la última consulta y se
  // apunta un apagado y un arranque. Solo escribe cuando alguien entra o sale (nada por cada consulta). En data(), estas
  // sesiones solo cuentan después del último registro de nLogin (así, cuando llegue nLogin, no se cuenta nada dos veces).
  async function presence(online, now, prev) {
    await init();
    const opens = (await db.prepare('SELECT key, name, start FROM bridge_open').all()).results;
    const onlineBy = new Map(online.filter((p) => p && typeof p.name === 'string' && NAME_RE.test(p.name)).map((p) => [p.name.toLowerCase(), p.name]));
    const lastPoll = prev && Number.isFinite(prev.at) ? prev.at : null;
    const gap = lastPoll !== null && now - lastPoll > BRIDGE_GAP_MS;
    const st = [];
    if (gap) {
      st.push(db.prepare("INSERT OR IGNORE INTO bridge_events (t, kind) VALUES (?, 'stop'), (?, 'start')").bind(lastPoll, now));
    }
    const still = new Set();
    for (const o of opens) {
      if (!gap && onlineBy.has(o.key)) {
        still.add(o.key);
        continue;
      }
      // salió (o se apagó el servidor): estaba en la consulta anterior y en esta ya no
      const end = lastPoll ?? now;
      if (end > o.start && end - o.start <= SESSION_MAX_MS) {
        st.push(db.prepare('INSERT OR REPLACE INTO bridge_sessions (key, start, end, cut) VALUES (?, ?, ?, ?)').bind(o.key, o.start, end, gap ? 1 : 0));
      }
      st.push(db.prepare('DELETE FROM bridge_open WHERE key = ?').bind(o.key));
      st.push(db.prepare('UPDATE nlogin_players SET last_seen = MAX(COALESCE(last_seen, 0), ?) WHERE key = ?').bind(end, o.key));
    }
    for (const [key, name] of onlineBy) {
      if (still.has(key)) continue;
      st.push(db.prepare('INSERT OR REPLACE INTO bridge_open (key, name, start) VALUES (?, ?, ?)').bind(key, name, now));
      // un jugador que la web no conocía: su cuenta es de ahora (si luego llega nLogin, pone la fecha de verdad)
      st.push(
        db
          .prepare(
            `INSERT INTO nlogin_players (key, name, created, first_seen, last_seen) VALUES (?, ?, ?, ?, ?)
             ON CONFLICT (key) DO UPDATE SET name = excluded.name, first_seen = COALESCE(nlogin_players.first_seen, excluded.first_seen),
               last_seen = MAX(COALESCE(nlogin_players.last_seen, 0), excluded.last_seen)`,
          )
          .bind(key, name, now, now, now),
      );
    }
    if (st.length) await db.batch(st);
  }

  // Cuentas de la base de nLogin cargadas a mano (POST /api/metricas/importar): [nombre, creada (ms), última vez (ms)].
  // Solo eso de cada cuenta. La creación se queda con la más antigua.
  async function importAccounts(list, now = Date.now()) {
    await init();
    const rows = (Array.isArray(list) ? list : [])
      .slice(0, 5000)
      .filter((a) => Array.isArray(a) && typeof a[0] === 'string' && NAME_RE.test(a[0]))
      .map(([name, created, seen]) => [
        name.toLowerCase(),
        name,
        Number.isSafeInteger(created) && created > 1.4e12 && created <= now + 86400000 ? created : null,
        Number.isSafeInteger(seen) && seen > 1.4e12 && seen <= now + 86400000 ? seen : null,
      ]);
    const st = rows.map((r) =>
      db
        .prepare(
          `INSERT INTO nlogin_players (key, name, created, first_seen, last_seen) VALUES (?, ?, ?, NULL, ?)
           ON CONFLICT (key) DO UPDATE SET
             created = CASE WHEN nlogin_players.created IS NULL OR (excluded.created IS NOT NULL AND excluded.created < nlogin_players.created)
               THEN excluded.created ELSE nlogin_players.created END,
             last_seen = MAX(COALESCE(nlogin_players.last_seen, 0), COALESCE(excluded.last_seen, 0))`,
        )
        .bind(...r),
    );
    for (let i = 0; i < st.length; i += 100) await db.batch(st.slice(i, i + 100));
    return rows.length;
  }

  // Todo lo de la página de métricas desde «since» (ms), compacto: las sesiones como [jugador, inicio, duración] en
  // segundos, con los jugadores en una lista aparte. Lo de nLogin hasta su último registro y, después, lo del puente.
  async function data(since) {
    await init();
    const s = Math.floor(since);
    const lastAny = (await db.prepare('SELECT MAX(t) AS t FROM nlogin_events').first())?.t || null;
    // lo del puente cuenta desde el último registro de nLogin (o desde «since» si es después)
    const fromBridge = Math.max(s, (lastAny || 0) + 1);
    const sessions = [
      ...(await db.prepare('SELECT key, start, end FROM nlogin_sessions WHERE start >= ?').bind(s).all()).results,
      ...(await db.prepare('SELECT key, start, end FROM bridge_sessions WHERE start >= ?').bind(fromBridge).all()).results,
    ].sort((a, b) => a.start - b.start || (a.key < b.key ? -1 : a.key > b.key ? 1 : 0));
    const events = [
      ...(
        await db
          .prepare("SELECT t, kind, name FROM nlogin_events WHERE t >= ? AND kind IN ('reg', 'start', 'stop', 'verr', 'mojang')")
          .bind(s)
          .all()
      ).results,
      ...(await db.prepare('SELECT t, kind, NULL AS name FROM bridge_events WHERE t >= ?').bind(fromBridge).all()).results,
    ].sort((a, b) => a.t - b.t);
    const entries = (await db.prepare("SELECT COUNT(*) AS n FROM nlogin_events WHERE t >= ? AND kind = 'in'").bind(s).first()) || {};
    // Los que están dentro ahora: los del puente (si no, los abiertos de nLogin)
    const bridgeOpen = (await db.prepare('SELECT key, start FROM bridge_open').all()).results;
    const nloginOpen = (await db.prepare('SELECT key, start FROM nlogin_open WHERE start >= ?').bind(s - SESSION_MAX_MS).all()).results;
    const openKeys = new Set(bridgeOpen.map((r) => r.key));
    const open = [...bridgeOpen, ...nloginOpen.filter((r) => !openKeys.has(r.key))];
    const bridgeFirst = (await db.prepare('SELECT MIN(t) AS t FROM (SELECT MIN(start) AS t FROM bridge_sessions UNION ALL SELECT MIN(start) FROM bridge_open)').first())?.t || null;
    const keys = new Set([
      ...sessions.map((r) => r.key),
      ...open.map((r) => r.key),
      ...events.filter((e) => e.name && (e.kind === 'reg' || e.kind === 'mojang')).map((e) => e.name),
    ]);
    const players = [];
    const index = new Map();
    for (let list = [...keys], i = 0; i < list.length; i += 90) {
      const rows = (
        await db.prepare('SELECT key, name, created, first_seen, last_seen FROM nlogin_players WHERE key IN (SELECT value FROM json_each(?))').bind(JSON.stringify(list.slice(i, i + 90))).all()
      ).results;
      for (const r of rows) {
        index.set(r.key, players.length);
        players.push([r.name, r.created ? Math.floor(r.created / 1000) : null]);
      }
    }
    const idx = (key) => {
      if (!index.has(key)) {
        index.set(key, players.length);
        players.push([key, null]);
      }
      return index.get(key);
    };
    const sec = (ms) => Math.floor(ms / 1000);
    const byKind = (k) => events.filter((e) => e.kind === k);
    return {
      cursor: await cursor(),
      lastEvent: lastAny,
      // desde cuándo hay datos del puente (sin nLogin)
      bridgeFrom: bridgeFirst ? Math.max(bridgeFirst, fromBridge) : null,
      entries: entries.n || 0,
      players,
      sessions: sessions.map((r) => [idx(r.key), sec(r.start), sec(r.end) - sec(r.start)]),
      open: open.map((r) => [idx(r.key), sec(r.start)]),
      regs: byKind('reg').map((e) => [idx(e.name), sec(e.t)]),
      starts: byKind('start').map((e) => sec(e.t)),
      stops: byKind('stop').map((e) => sec(e.t)),
      // con la versión del cliente que lo intentó (p. ej. «V_1_20»), si la dijo nLogin
      verr: byKind('verr').map((e) => [e.name || null, sec(e.t)]),
      mojang: byKind('mojang').map((e) => [e.name ? idx(e.name) : -1, sec(e.t)]),
    };
  }

  return { cursor, ingest, data, presence, importAccounts };
}
