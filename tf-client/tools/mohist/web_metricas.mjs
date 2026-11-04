// La web de la tienda (el Worker de tierras-fantasticas con una base de datos en memoria) para probar el puente de
// nLogin (1.3.174) con un Mohist de verdad. Escribe en <estado.json> lo que tiene después de cada consulta del puente.
//   node web_metricas.mjs <puerto> <clave del puente> <estado.json>
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const WEB = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../tierras-fantasticas');
const [port, secret, estado] = process.argv.slice(2);
await import(path.join(WEB, 'test/retired.js'));
const worker = (await import(path.join(WEB, 'src/index.js'))).default;
const { createD1 } = await import(path.join(WEB, 'test/d1.js'));
const db = createD1();
const env = { DB: db, ASSETS: { fetch: async () => new Response('', { status: 404 }) }, LOGGER: { log() {}, warn() {}, error: console.error }, BRIDGE_SECRET: secret, PUBLIC_URL: `http://127.0.0.1:${port}` };
let polls = 0;
http
  .createServer(async (req, res) => {
    let body = '';
    for await (const c of req) body += c;
    const r = await worker.fetch(new Request(`http://127.0.0.1:${port}${req.url}`, { method: req.method, headers: req.headers, body: req.method === 'GET' ? undefined : body }), env);
    const text = await r.text();
    res.writeHead(r.status, { 'Content-Type': 'application/json' });
    res.end(text);
    if (req.url === '/bridge/poll') {
      polls++;
      let sent = null;
      try {
        sent = JSON.parse(body).nlogin || null;
      } catch {}
      const kinds = Object.fromEntries(db.raw.prepare('SELECT kind, COUNT(*) AS n FROM nlogin_events GROUP BY kind').all().map((x) => [x.kind, Number(x.n)]));
      const cursor = db.raw.prepare("SELECT value FROM settings WHERE key = 'nlogin_cursor'").get()?.value || null;
      const sessions = Number(db.raw.prepare('SELECT COUNT(*) AS n FROM nlogin_sessions').get().n);
      const ips = db.raw.prepare("SELECT COUNT(*) AS n FROM nlogin_events WHERE name LIKE '%.%.%.%'").get().n;
      fs.writeFileSync(estado, JSON.stringify({ polls, status: r.status, kinds, cursor: cursor && JSON.parse(cursor), sessions, ips: Number(ips), sentWant: Boolean(sent?.want), sentEvents: sent?.events?.length ?? 0, rawHasIp: /\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b/.test(body) && /"nlogin"/.test(body), answer: JSON.parse(text).nlogin || null }, null, 1));
    }
  })
  .listen(Number(port), '127.0.0.1', () => console.log(`web de prueba en ${port}`));
