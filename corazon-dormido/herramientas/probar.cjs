// Prueba rápida del juego en un móvil simulado (Chromium + Playwright, ya instalados en el contenedor):
// empieza partida, juega al azar, abre los paneles, guarda, recarga y continúa. Saca capturas y lista los errores.
//   node corazon-dormido/herramientas/probar.cjs <carpeta-capturas> [vertical]
const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const salida = path.resolve(process.argv[2] || '.'), vertical = process.argv[3] === 'vertical';
  const url = 'file://' + path.resolve(__dirname, '..', 'index.html');
  const b = await chromium.launch();
  const ctx = await b.newContext({ viewport: vertical ? { width: 412, height: 870 } : { width: 870, height: 412 }, deviceScaleFactor: 2.625, isMobile: true, hasTouch: true });
  const p = await ctx.newPage();
  const errs = [];
  p.on('pageerror', (e) => errs.push(e.message + '\n' + e.stack));
  p.on('console', (m) => { if (m.type() === 'error') errs.push(m.text()); });
  await p.goto(url);
  await p.waitForTimeout(500);
  await p.screenshot({ path: path.join(salida, '1-titulo.png') });
  await p.waitForFunction(() => window.juegoListo, null, { timeout: 120000 });
  await p.evaluate(async () => { localStorage.clear(); await empezar(false); });
  const teclas = ['KeyW', 'KeyA', 'KeyS', 'KeyD'];
  for (let k = 0; k < 40; k++) {
    const t = teclas[Math.floor(Math.random() * 4)];
    await p.keyboard.down(t);
    await p.touchscreen.tap(vertical ? 300 : 700, vertical ? 600 : 250);
    await p.keyboard.down('Space'); await p.waitForTimeout(200); await p.keyboard.up('Space');
    await p.keyboard.up(t);
  }
  await p.screenshot({ path: path.join(salida, '2-jugando.png') });
  for (const panel of ['mochila', 'crear', 'mapa', 'pausa']) {
    await p.evaluate((n) => { J.panel = n; }, panel);
    await p.waitForTimeout(200);
    await p.screenshot({ path: path.join(salida, `3-${panel}.png`) });
  }
  await p.evaluate(() => { J.panel = null; return guardarPartida(); });
  const antes = await p.evaluate(() => `${Math.round(J.jug.x)},${Math.round(J.jug.y)} muros ${M.muro.reduce((a, b) => a + (b ? 1 : 0), 0)}`);
  await p.reload(); await p.waitForFunction(() => window.juegoListo, null, { timeout: 120000 });
  await p.evaluate(() => empezar(true));
  const despues = await p.evaluate(() => `${Math.round(J.jug.x)},${Math.round(J.jug.y)} muros ${M.muro.reduce((a, b) => a + (b ? 1 : 0), 0)}`);
  console.log(antes === despues ? 'guardar y cargar: bien' : `guardar y cargar: DISTINTO (${antes} / ${despues})`);
  console.log(errs.length ? 'ERRORES:\n' + errs.join('\n') : 'sin errores');
  await b.close();
  process.exitCode = errs.length || antes !== despues ? 1 : 0;
})();
