// Saca una captura del taller de arte: node corazon-dormido/herramientas/taller.cjs <salida.png> [filtro] [zoom] [luz]
const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const [salida, filtro = '.', zoom = '4', luz = '0', ancho = '1400'] = process.argv.slice(2);
  const b = await chromium.launch();
  const p = await (await b.newContext({ viewport: { width: Number(ancho), height: 800 } })).newPage();
  const errs = [];
  p.on('pageerror', (e) => errs.push(e.message + '\n' + e.stack));
  p.on('console', (m) => { if (m.type() === 'error') errs.push(m.text()); });
  await p.goto('file://' + path.resolve(__dirname, 'taller.html') + '#filtro=' + encodeURIComponent(filtro) + '&zoom=' + zoom + '&luz=' + luz + '&ancho=' + ancho + (process.env.PJ ? '&pj=1' : ''));
  await p.waitForFunction(() => window.LISTO || false, null, { timeout: 60000 }).catch(() => {});
  await p.screenshot({ path: salida, fullPage: true });
  console.log(await p.evaluate(() => document.querySelector('canvas') && `${nombres.length} dibujos, arte ${Math.round(tArte)} ms`).catch(() => ''));
  if (errs.length) console.log('ERRORES:\n' + errs.join('\n'));
  await b.close();
})();
