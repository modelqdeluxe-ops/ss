// Servidor estático mínimo para las páginas del horno (los módulos ES no cargan desde file://).
const http = require('http'), fs = require('fs'), path = require('path');
const TIPOS = { '.html': 'text/html', '.js': 'text/javascript', '.json': 'application/json', '.png': 'image/png', '.glb': 'model/gltf-binary', '.gltf': 'model/gltf+json', '.bin': 'application/octet-stream', '.fbx': 'application/octet-stream' };
module.exports = (raiz, extra = {}) => new Promise((ok) => {
  const s = http.createServer((req, res) => {
    let p = decodeURIComponent(req.url.split('?')[0]);
    let f = null;
    for (const [pref, dir] of Object.entries(extra)) if (p.startsWith(pref)) f = path.join(dir, p.slice(pref.length));
    if (!f) f = path.join(raiz, p);
    fs.readFile(f, (e, d) => { if (e) { res.writeHead(404); res.end(); return; } res.writeHead(200, { 'Content-Type': TIPOS[path.extname(f)] || 'application/octet-stream' }); res.end(d); });
  });
  s.listen(0, () => ok({ url: 'http://127.0.0.1:' + s.address().port, cerrar: () => s.close() }));
});
