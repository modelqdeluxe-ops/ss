// Vista previa local de la web con datos de prueba (sin Stripe ni Discord reales), en http://127.0.0.1:8788.
// Uso (desde tierras-fantasticas/): node tools/preview.mjs
// Crea un jugador «Notch» con cuenta (contraseña «contraseña-segura»), un rango y una tienda de monedas de ejemplo.
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import worker from '../src/index.js';
import { createD1 } from '../test/d1.js';

const ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
const PUB = path.join(ROOT, 'public');
const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.png': 'image/png', '.webp': 'image/webp', '.jpg': 'image/jpeg', '.svg': 'image/svg+xml', '.json': 'application/json' };
const ASSETS = {
  fetch: async (req) => {
    let p = new URL(req.url).pathname;
    if (p === '/') p = '/index.html';
    if (!path.extname(p)) p += '.html';
    const f = path.join(PUB, p);
    if (!fs.existsSync(f)) return new Response('404', { status: 404 });
    return new Response(fs.readFileSync(f), { headers: { 'Content-Type': types[path.extname(f)] || 'application/octet-stream' } });
  },
};
const env = { DB: createD1(), ASSETS, BRIDGE_SECRET: 'x', STRIPE_SECRET_KEY: 'sk', SESSION_SECRET: 's', DISCORD_CLIENT_ID: 'c', DISCORD_CLIENT_SECRET: 'd', LOGGER: { log() {}, warn() {}, error() {} } };
const call = (p, init) => worker.fetch(new Request(`http://localhost${p}`, init), env);
await call('/bridge/poll', { method: 'POST', headers: { Authorization: 'Bearer x' }, body: JSON.stringify({ protocol: 2, players: [{ name: 'Steve', uuid: '11111111-2222-3333-4444-555555555555' }], seen: [{ name: 'Notch', uuid: '069a79f4-44e9-4726-a5be-fca90e38aaf5', at: Date.now() - 1e6 }] }) });
await call('/bridge/poll', { method: 'POST', headers: { Authorization: 'Bearer x' }, body: JSON.stringify({ protocol: 2, ranks: [{ uuid: '069a79f4-44e9-4726-a5be-fca90e38aaf5', rank: 'rango-mortal' }] }) });
await call('/bridge/poll', { method: 'POST', headers: { Authorization: 'Bearer x' }, body: JSON.stringify({ protocol: 2, shop: [
  { op: 'add', id: 'diamante', item: 'minecraft:diamond', name: 'Diamante', count: 16, price: 2400 },
  { op: 'add', id: 'netherita', item: 'minecraft:netherite_ingot', name: 'Lingote de netherita', count: 1, price: 9500 },
  { op: 'add', id: 'manzana', item: 'minecraft:enchanted_golden_apple', name: 'Manzana dorada encantada', count: 1, price: 6000 },
  { op: 'add', id: 'totem', item: 'minecraft:totem_of_undying', name: 'Tótem de la inmortalidad', count: 1, price: 7500 },
  { op: 'add', id: 'elitros', item: 'minecraft:elytra', name: 'Élitros', count: 1, price: 25000 },
  { op: 'add', id: 'hierro', item: 'minecraft:iron_block', name: 'Bloque de hierro', count: 16, price: 1200 },
  { op: 'add', id: 'espada', item: 'tfclient:necros_sword', name: 'Espada Necros', count: 1, price: 50000 },
  { op: 'add', id: 'raro', item: 'modx:cosa_rara', name: 'Objeto raro de un mod', count: 1, price: 999 },
] }) });
const reg = await call('/api/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name: 'Notch', password: 'contraseña-segura', acceptTerms: true }) });
const cookie = reg.headers.getSetCookie()[0].split(';')[0];
env.DB.raw.prepare("UPDATE users SET discord_id='1', discord_username='notch_tf', discord_name='Notch', discord_member=0").run();
// Cookie de sesión de Notch, por si hace falta ver «Mi cuenta» con la sesión iniciada
if (process.env.COOKIE_FILE) fs.writeFileSync(process.env.COOKIE_FILE, cookie);
http.createServer(async (req, res) => {
  let body = '';
  for await (const c of req) body += c;
  const r = await worker.fetch(new Request(`http://localhost:8788${req.url}`, { method: req.method, headers: req.headers, body: ['GET', 'HEAD'].includes(req.method) ? undefined : body, redirect: 'manual' }), env);
  res.writeHead(r.status, Object.fromEntries(r.headers));
  res.end(Buffer.from(await r.arrayBuffer()));
}).listen(8788, () => console.log('listo'));
