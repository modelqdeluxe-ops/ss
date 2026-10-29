// Hoja de las criaturas con esqueleto (criaturas-esq.js + criaturas-fichas.js), sin abrir el juego entero:
//   node corazon-dormido/herramientas/criaturas.cjs <salida.png> [tipos separados por comas] [zoom] [anims]
const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const [sal, tipos = '', zoom = '4', anims = ''] = process.argv.slice(2);
  const b = await chromium.launch();
  const p = await (await b.newContext({ viewport: { width: 1600, height: 900 } })).newPage();
  const errs = [];
  p.on('pageerror', (e) => errs.push(e.message + '\n' + e.stack));
  p.on('console', (m) => { if (m.type() === 'error') errs.push(m.text()); });
  await p.goto('file://' + path.resolve(__dirname, 'criaturas.html') + '#zoom=' + zoom + (tipos ? '&tipos=' + tipos : '') + (anims ? '&anims=' + anims : ''));
  await p.waitForFunction(() => window.LISTO || false, null, { timeout: 60000 }).catch(() => {});
  const m = await p.evaluate(() => window.MEDIDAS).catch(() => null);
  if (m) for (const [t, v] of Object.entries(m)) console.log(t.padEnd(18), `${v.w}×${v.h}`, `${v.cuadros} cuadros`);
  await p.locator('canvas').screenshot({ path: path.resolve(sal) });
  if (errs.length) console.log('ERRORES:\n' + errs.join('\n'));
  await b.close();
})();
