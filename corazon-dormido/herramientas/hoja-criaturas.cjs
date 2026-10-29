// Hoja de los cuadros de las criaturas y los jefes tal como quedan en el atlas del juego (para comparar antes y
// después y medir con PIL). Cada fila es una criatura: todos sus cuadros (<tipo>_*), ampliados, sobre gris.
//   node corazon-dormido/herramientas/hoja-criaturas.cjs <salida.png> [zoom] [filtro]
// Escribe también <salida>.json con el tamaño de lo dibujado en cada cuadro.
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');
(async () => {
  const sal = path.resolve(process.argv[2] || 'hoja.png'), zoom = Number(process.argv[3] || 3), filtro = process.argv[4] || '';
  const b = await chromium.launch();
  const p = await (await b.newContext({ viewport: { width: 1200, height: 800 } })).newPage();
  const errs = [];
  p.on('pageerror', (e) => errs.push(e.message));
  await p.goto('file://' + path.resolve(__dirname, '..', 'index.html'));
  await p.waitForFunction(() => window.juegoListo, null, { timeout: 120000 });
  const r = await p.evaluate(([z, filtro]) => {
    const tipos = [...Object.keys(CRIATURAS), ...Object.keys(JEFES)].filter((t) => !filtro || t.includes(filtro));
    const filas = [], medidas = {};
    const cx = ATLAS.getContext('2d');
    for (const t of tipos) {
      const ns = Object.keys(SPR).filter((n) => n.startsWith(t + '_') && !n.endsWith('_luz') && /^[a-z]*[0-9]+$/.test(n.slice(t.length + 1)) && !(n.slice(t.length + 1).match(/^[a-z]+[0-9]/) && Object.keys(CRIATURAS).concat(Object.keys(JEFES)).some((o) => o !== t && n.startsWith(o + '_'))))
        .sort((a, b) => a.localeCompare(b, 'es', { numeric: true }));
      if (!ns.length) continue;
      const cuadros = ns.slice(0, 40).map((n) => {
        const s = SPR[n], d = cx.getImageData(s.x, s.y, s.w, s.h).data;
        let x0 = s.w, y0 = s.h, x1 = -1, y1 = -1;
        for (let y = 0; y < s.h; y++) for (let x = 0; x < s.w; x++) if (d[(y * s.w + x) * 4 + 3] > 20) { x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); }
        return { n, s, w: x1 - x0 + 1, h: y1 - y0 + 1 };
      });
      medidas[t] = { cuadros: ns.length, w: Math.max(...cuadros.map((c) => c.w)), h: Math.max(...cuadros.map((c) => c.h)) };
      filas.push([t, cuadros]);
    }
    const pad = 6;
    let W = 0, H = 0;
    for (const [, cs] of filas) { let w = 130; for (const c of cs) w += c.s.w * z + pad; W = Math.max(W, w); H += Math.max(...cs.map((c) => c.s.h)) * z + pad * 2; }
    W = Math.min(W, 8000); H = Math.min(H, 16000);
    const cv = document.createElement('canvas'); cv.width = W; cv.height = H;
    const c = cv.getContext('2d'); c.imageSmoothingEnabled = false;
    c.fillStyle = '#3a3942'; c.fillRect(0, 0, W, H);
    let y = pad;
    for (const [t, cs] of filas) {
      const h = Math.max(...cs.map((q) => q.s.h)) * z;
      c.fillStyle = '#d8d4e0'; c.font = '12px sans-serif'; c.fillText(t, 4, y + 14); c.fillStyle = '#9a96a8'; c.fillText(`${medidas[t].w}×${medidas[t].h} px`, 4, y + 30);
      let x = 130;
      for (const q of cs) { if (x + q.s.w * z > W) break; c.drawImage(ATLAS, q.s.x, q.s.y, q.s.w, q.s.h, x, y + h - q.s.h * z, q.s.w * z, q.s.h * z); x += q.s.w * z + pad; }
      y += h + pad * 2;
    }
    return { url: cv.toDataURL(), medidas };
  }, [zoom, filtro]);
  fs.writeFileSync(sal, Buffer.from(r.url.split(',')[1], 'base64'));
  fs.writeFileSync(sal.replace(/\.png$/, '') + '.json', JSON.stringify(r.medidas, null, 1));
  for (const [t, m] of Object.entries(r.medidas)) console.log(t.padEnd(18), `${m.w}×${m.h}`, `(${m.cuadros} cuadros)`);
  if (errs.length) console.log('ERRORES:\n' + errs.join('\n'));
  await b.close();
})();
