// vermano.cjs salida.png zoom 'expresión JS que devuelve [[nombre, Lienzo], ...]'
const { chromium } = require('playwright'); const path = require('path');
(async () => {
  const [sal, zoom, expr] = process.argv.slice(2);
  const b = await chromium.launch(); const p = await (await b.newContext({ viewport: { width: 1400, height: 900 } })).newPage();
  const errs = []; p.on('pageerror', (e) => errs.push(e.message));
  await p.goto('file://' + path.resolve(__dirname, 'taller.html') + '#filtro=^$');
  await p.waitForFunction(() => window.LISTO || false, null, { timeout: 60000 }).catch(() => {});
  const require_recorte = require('fs').readFileSync(require('path').join(__dirname, 'recorte.js'), 'utf8');
  const url = await p.evaluate(([expr, z, require_recorte]) => {
    eval(require_recorte); const lista = eval(expr), pad = 6; let W = pad, H = 0;
    for (const [, L] of lista) { W += L.w * z + pad; H = Math.max(H, L.h * z); }
    const cv = document.createElement('canvas'); cv.width = W; cv.height = H + 30; const c = cv.getContext('2d');
    c.fillStyle = '#2b2a33'; c.fillRect(0, 0, W, H + 30); let x = pad;
    for (const [n, L] of lista) {
      c.fillStyle = '#aaa'; c.font = '11px sans-serif'; c.fillText(n, x, 11);
      for (let j = 0; j < L.h; j++) for (let i = 0; i < L.w; i++) { const col = L.get(i, j); if (col) { c.fillStyle = col; c.fillRect(x + i * z, 16 + j * z, z, z); } }
      x += L.w * z + pad;
    }
    return cv.toDataURL();
  }, [expr, Number(zoom), require_recorte]);
  require('fs').writeFileSync(sal, Buffer.from(url.split(',')[1], 'base64'));
  if (errs.length) console.log(errs.join('\n'));
  await b.close();
})();
