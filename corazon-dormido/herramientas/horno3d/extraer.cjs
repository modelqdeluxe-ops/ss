// node extraer.cjs <modelo.glb|fbx> <salida.json> [clip1,clip2,...] [fps]
// Saca el movimiento de los huesos (no el modelo) de una animación CC0 para animar nuestro pixel art.
const { chromium } = require('playwright'); const path = require('path'); const fs = require('fs');
const servir = require('./servidor.cjs');
(async () => {
  const [modelo, salida, clips, fps = '12'] = process.argv.slice(2);
  const srv = await servir(__dirname, { '/modelo/': path.dirname(path.resolve(modelo)) });
  const b = await chromium.launch(); const p = await b.newPage();
  const errs = []; p.on('pageerror', (e) => errs.push(e.message)); p.on('console', (m) => { if (m.type() === 'error') errs.push(m.text()); });
  await p.goto(srv.url + '/extraer.html'); await p.waitForFunction(() => window.LISTO, null, { timeout: 30000 });
  const r = await p.evaluate(([u, c, f]) => window.extraer(u, c, f), ['/modelo/' + path.basename(modelo), clips ? clips.split(',') : null, Number(fps)]);
  fs.writeFileSync(salida, JSON.stringify(r));
  console.log(Object.entries(r.clips).map(([n, c]) => `${n}: ${c.cuadros.length}`).join(', '), '| huesos:', r.huesos.join(' '));
  if (errs.length) console.log('ERRORES', errs.join('\n'));
  await b.close(); srv.cerrar();
})();
