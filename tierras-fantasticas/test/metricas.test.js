// (4 nov 2026) /metricas: los registros de nLogin que manda el puente (TF Client 1.3.174+), la contraseña del staff y
// los datos de las gráficas.
import { test, before } from 'node:test';
import assert from 'node:assert';
import metricas from '../config/metricas.json' with { type: 'json' };
import './retired.js';
import worker from '../src/index.js';
import { hashPassword } from '../src/password.js';
import { createD1 } from './d1.js';

const BRIDGE_SECRET = 'clave-del-puente';
const PASSWORD = 'Prueba-Metricas-Larga-2026';
let env;

before(async () => {
  // La huella de la contraseña de la prueba (la de verdad no está en el repositorio)
  metricas.password = await hashPassword(PASSWORD);
  env = {
    DB: createD1(),
    ASSETS: { fetch: async (req) => new Response(`estático ${new URL(req.url).pathname}`) },
    LOGGER: { log() {}, warn() {}, error() {} },
    BRIDGE_SECRET,
    ADMIN_KEY: 'clave-del-staff',
    PUBLIC_URL: 'http://tienda.test',
  };
});

let ip = 1;
const call = (path, init = {}) => worker.fetch(new Request(`http://tienda.test${path}`, { redirect: 'manual', ...init }), env);
const poll = (nlogin, players = []) =>
  call('/bridge/poll', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${BRIDGE_SECRET}` },
    body: JSON.stringify({ protocol: 2, players, max: 50, done: [], nlogin }),
  });
const login = (password, addr = `10.0.0.${ip++}`) =>
  call('/api/metricas/entrar', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'CF-Connecting-IP': addr },
    body: JSON.stringify({ password }),
  });

// Horas en el centro de México (UTC-6): 2026-10-27 20:00 = 2026-10-28 02:00 UTC
const at = (d, h, m = 0) => Date.UTC(2026, 9, d, h + 6, m);
const F = '2026-10-27-1.log';
let n = 0;
const ev = (t, kind, name = null, created = null) => [`${F}:${(n += 120)}`, t, kind, name, created];
const CREATED_ANA = Date.UTC(2026, 7, 2, 20, 46, 49, 651);

const batch1 = () => {
  n = 0;
  return [
    ev(at(27, 16), 'start'),
    ev(at(27, 17), 'in', 'Ana_TF', CREATED_ANA),
    ev(at(27, 17, 40), 'out', 'Ana_TF', CREATED_ANA),
    // Beto entra y el servidor se cae sin apuntar su salida: su sesión acaba en el apagado
    ev(at(27, 18), 'in', 'Beto', at(27, 17, 59)),
    ev(at(27, 19), 'stop'),
    ev(at(27, 19, 1), 'start'),
    ev(at(27, 19, 5), 'in', 'Beto', at(27, 17, 59)),
    ev(at(27, 19, 50), 'out', 'Beto', at(27, 17, 59)),
    // Carla se registra (cuenta nueva, sin premium): su primera entrada es el registro
    ev(at(27, 20), 'reg', 'Carla'),
    ev(at(27, 21), 'out', 'Carla', at(27, 20)),
    ev(at(27, 21, 2), 'in', 'Carla', at(27, 20)),
    ev(at(27, 21, 30), 'verr', 'V_26_2'),
    ev(at(27, 21, 31), 'mojang', 'Dani'),
  ];
};

test('el puente pregunta por dónde va la web y manda lo nuevo de nLogin, una sola vez', async () => {
  const first = await (await poll({ want: true })).json();
  assert.deepStrictEqual(first.nlogin, { cursor: null, desde: '2026-10-26', ok: 0 });

  const events = batch1();
  const to = { file: F, offset: 4000 };
  const r = await (await poll({ from: null, to, events })).json();
  assert.deepStrictEqual(r.nlogin, { cursor: to, desde: '2026-10-26', ok: events.length });

  // La misma otra vez (la respuesta se perdió y el mod lo repite): no entra dos veces
  const again = await (await poll({ from: null, to, events })).json();
  assert.deepStrictEqual(again.nlogin, { cursor: to, desde: '2026-10-26', ok: 0 });

  // Lo siguiente, desde donde se quedó: Carla sale
  n = 4000;
  const to2 = { file: '2026-10-28-1.log', offset: 300 };
  const r2 = await (await poll({ from: to, to: to2, events: [ev(at(27, 22), 'out', 'Carla', at(27, 20))] })).json();
  assert.deepStrictEqual(r2.nlogin.cursor, to2);
  assert.strictEqual(r2.nlogin.ok, 1);

  // Nada de IP ni contraseñas: lo que no es un evento válido no entra
  const bad = await (await poll({ from: to2, to: { file: '2026-10-28-1.log', offset: 900 }, events: [['x', 1, 'in', '1.2.3.4', null], ['2026-10-28-1.log:400', at(28, 1), 'in', "a' OR 1", null]] })).json();
  assert.strictEqual(bad.nlogin.ok, 0);
});

test('/metricas pide la contraseña del staff y da las sesiones desde el 26 de octubre', async () => {
  const noCookie = await call('/api/metricas');
  assert.strictEqual(noCookie.status, 401);
  assert.strictEqual((await login('no-es-esta')).status, 401);
  assert.strictEqual((await login('')).status, 400);

  const ok = await login(PASSWORD);
  assert.strictEqual(ok.status, 200);
  const cookie = ok.headers.get('Set-Cookie');
  assert.match(cookie, /^tf_metricas=.+; Path=\/; HttpOnly; SameSite=Lax; Max-Age=43200/);
  const data = await (await call('/api/metricas', { headers: { Cookie: cookie.split(';')[0] } })).json();
  assert.strictEqual(data.desde, '2026-10-26');
  assert.strictEqual(data.zona, 'America/Mexico_City');
  const name = (i) => data.players[i][0];
  const sessions = data.sessions.map(([p, s, d]) => [name(p), s, d]);
  const sec = (ms) => ms / 1000;
  assert.deepStrictEqual(sessions, [
    ['Ana_TF', sec(at(27, 17)), 40 * 60],
    ['Beto', sec(at(27, 18)), 60 * 60],
    ['Beto', sec(at(27, 19, 5)), 45 * 60],
    ['Carla', sec(at(27, 20)), 60 * 60],
    ['Carla', sec(at(27, 21, 2)), 58 * 60],
  ]);
  // La creación de la cuenta (la de Ana es de agosto: no es nueva; la de Carla, el registro)
  assert.strictEqual(data.players.find((p) => p[0] === 'Ana_TF')[1], Math.floor(CREATED_ANA / 1000));
  assert.strictEqual(data.players.find((p) => p[0] === 'Carla')[1], sec(at(27, 20)));
  assert.deepStrictEqual(data.starts, [sec(at(27, 16)), sec(at(27, 19, 1))]);
  assert.deepStrictEqual(data.stops, [sec(at(27, 19))]);
  assert.deepStrictEqual(data.verr, [['v_26_2', sec(at(27, 21, 30))]]);
  assert.deepStrictEqual(data.mojang.map(([p, t]) => [name(p), t]), [['dani', sec(at(27, 21, 31))]]);
  assert.deepStrictEqual(data.regs.map(([p, t]) => [name(p), t]), [['Carla', sec(at(27, 20))]]);
  assert.deepStrictEqual(data.open, []);
  assert.strictEqual(JSON.stringify(data).includes('1.2.3.4'), false);

  // Desde un día después: nada de antes
  const later = await (await call('/api/metricas?desde=2026-10-28', { headers: { Cookie: cookie.split(';')[0] } })).json();
  assert.strictEqual(later.sessions.length, 0);
  // Antes del 26 de octubre no se puede pedir
  const before = await (await call('/api/metricas?desde=2026-09-01', { headers: { Cookie: cookie.split(';')[0] } })).json();
  assert.strictEqual(before.desde, '2026-10-26');

  // Una cookie inventada no vale
  assert.strictEqual((await call('/api/metricas', { headers: { Cookie: 'tf_metricas=abc.def' } })).status, 401);
  // La clave de /staff también entra
  assert.strictEqual((await login('clave-del-staff')).status, 200);
  // Salir borra la cookie
  const out = await call('/api/metricas/salir', { method: 'POST' });
  assert.match(out.headers.get('Set-Cookie'), /^tf_metricas=; .*Max-Age=0/);
});

test('/metricas frena a quien prueba contraseñas y /métricas lleva a /metricas', async () => {
  const addr = '10.9.9.9';
  for (let i = 0; i < 8; i++) assert.strictEqual((await login('mala', addr)).status, 401);
  assert.strictEqual((await login(PASSWORD, addr)).status, 429);
  const r = await call('/m%C3%A9tricas');
  assert.strictEqual(r.status, 302);
  assert.strictEqual(r.headers.get('Location'), '/metricas');
});

test('las cuentas de la página (metricas-calc.js) y el Excel (xlsx.js)', async () => {
  const { compute } = await import('../public/metricas-calc.js');
  const { buildXlsx, crc32, excelDay } = await import('../public/xlsx.js');
  const cookie = (await login(PASSWORD)).headers.get('Set-Cookie').split(';')[0];
  const data = await (await call('/api/metricas', { headers: { Cookie: cookie } })).json();
  const m = compute({ ...data, now: at(28, 12) });
  assert.strictEqual(m.summary.players, 3);
  // Beto y Carla crearon la cuenta el 27; Ana es de agosto
  assert.strictEqual(m.summary.newAccounts, 2);
  // la primera sesión de Beto la cortó el apagado; Carla salió y volvió en 2 minutos sin reinicio
  assert.strictEqual(m.summary.cut, 1);
  assert.strictEqual(m.summary.recon, 1);
  // a Dani, Mojang no le contestó y no entró
  assert.strictEqual(m.summary.mojangOut, 1);
  assert.deepStrictEqual(m.versions, [['26.2', 1]]);
  const d27 = m.daily.find((r) => r.day === '2026-10-27');
  assert.deepStrictEqual([d27.players, d27.fresh, d27.old, d27.restarts, d27.sessions], [3, 2, 1, 2, 5]);
  assert.strictEqual(Math.round(d27.hours * 60), 40 + 60 + 45 + 60 + 58 + 0);
  // nadie volvió otro día (todo fue el 27): 0 de 2
  assert.deepStrictEqual([m.summary.newReturned, m.summary.newCanReturn], [0, 2]);
  assert.strictEqual(m.players[0].name, 'Carla');
  assert.strictEqual(m.weekly[0].week, '2026-10-26');
  // las 20 h del martes: Beto (hasta las 19:50 no) y Carla
  assert.ok(m.heat[1][20] > 0);

  assert.strictEqual(crc32(new TextEncoder().encode('abc')), 0x352441c2);
  assert.strictEqual(excelDay('2026-10-26'), 46321);
  const blob = await buildXlsx([{ name: 'Por día', columns: [{ title: 'Día', style: 'date' }, { title: 'N', style: 'int' }], rows: [[excelDay('2026-10-26'), 3]] }]);
  const bytes = new Uint8Array(await blob.arrayBuffer());
  assert.deepStrictEqual([...bytes.slice(0, 4)], [0x50, 0x4b, 0x03, 0x04]);
  assert.strictEqual(blob.type, 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
});

test('sin el mod nuevo: las sesiones con los conectados que manda el puente (y nLogin manda si llega)', async () => {
  const { createNLogin } = await import('../src/nlogin.js');
  const { createStore } = await import('../src/store.js');
  const db = createD1();
  const store = createStore(db);
  const nl = createNLogin(db, store.ready, { desde: '2026-10-26' });
  const t0 = at(29, 18);
  const s10 = 10000;
  const p = (...names) => names.map((name) => ({ name }));
  await nl.presence(p('Eva', 'Fer'), t0, null);
  await nl.presence(p('Eva', 'Fer'), t0 + s10, { at: t0 });
  // Fer sale: su sesión acaba en la consulta anterior
  await nl.presence(p('Eva'), t0 + 2 * s10, { at: t0 + s10 });
  // el puente no llama en 3 minutos: el servidor se apagó; Eva vuelve tras el arranque
  await nl.presence(p('Eva'), t0 + 2 * s10 + 180000, { at: t0 + 2 * s10 });
  let d = await nl.data(at(26, 0));
  const name = (i) => d.players[i][0];
  assert.deepStrictEqual(d.sessions.map(([i, s, dur]) => [name(i), s, dur]), [
    ['Eva', t0 / 1000, 20],
    ['Fer', t0 / 1000, 10],
  ]);
  assert.deepStrictEqual(d.stops, [(t0 + 2 * s10) / 1000]);
  assert.deepStrictEqual(d.starts, [(t0 + 2 * s10 + 180000) / 1000]);
  assert.deepStrictEqual(d.open.map(([i, s]) => [name(i), s]), [['Eva', (t0 + 2 * s10 + 180000) / 1000]]);
  // la cuenta de un jugador que la web no conocía: desde que entró por primera vez
  assert.strictEqual(d.players.find((x) => x[0] === 'Fer')[1], t0 / 1000);
  assert.strictEqual(d.bridgeFrom, t0);

  // Las cuentas de la base de nLogin cargadas a mano: la creación de verdad (más antigua) manda
  assert.strictEqual(await nl.importAccounts([['Fer', at(20, 12), at(28, 12)], ['Nadie', null, null], ['no vale', 1, 1]]), 2);
  d = await nl.data(at(26, 0));
  assert.strictEqual(d.players.find((x) => x[0] === 'Fer')[1], at(20, 12) / 1000);

  // Llegan registros de nLogin hasta después: lo del puente de antes ya no cuenta (no se cuenta nada dos veces)
  n = 9000;
  await nl.ingest({ from: null, to: { file: '2026-10-29-1.log', offset: 10 }, events: [ev(t0, 'in', 'Eva', at(1, 0)), ev(t0 + 5 * 60000, 'out', 'Eva', at(1, 0))] });
  d = await nl.data(at(26, 0));
  assert.deepStrictEqual(d.sessions.map(([i, s, dur]) => [d.players[i][0], s, dur]), [['Eva', t0 / 1000, 300]]);
  assert.deepStrictEqual(d.stops, []);
});

test('POST /api/metricas/importar: con la contraseña, el historial y las cuentas de nLogin', async () => {
  assert.strictEqual((await call('/api/metricas/importar', { method: 'POST', body: '{}' })).status, 401);
  const cookie = (await login(PASSWORD)).headers.get('Set-Cookie').split(';')[0];
  const post = (body) => call('/api/metricas/importar', { method: 'POST', headers: { Cookie: cookie, 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  const r = await (await post({ accounts: [['Beto', at(10, 9), at(27, 20)]] })).json();
  assert.strictEqual(r.accounts, 1);
  const data = await (await call('/api/metricas', { headers: { Cookie: cookie } })).json();
  assert.strictEqual(data.players.find((x) => x[0] === 'Beto')[1], at(10, 9) / 1000);
  // un lote que no empieza donde se quedó la web no entra
  const bad = await (await post({ nlogin: { from: null, to: { file: 'x.log', offset: 1 }, events: [] } })).json();
  assert.strictEqual(bad.nlogin.ok, 0);
  assert.notStrictEqual(bad.nlogin.cursor, null);
});
