// Saca el icono de la app de Android (y el de la Play Store) del modelo del Corazón, horneado por el propio juego:
//   node corazon-dormido/herramientas/icono.cjs
// Escribe android/app/src/main/res/mipmap-*/ic_launcher_{background,foreground,monochrome}.png (icono adaptable)
// y android/tienda/icono-512.png. El dibujo se amplía por un número entero para que los píxeles queden nítidos.
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');
(async () => {
  const raiz = path.resolve(__dirname, '..'), res = path.join(raiz, 'android/app/src/main/res'), tienda = path.join(raiz, 'android/tienda');
  const b = await chromium.launch();
  const p = await b.newPage();
  await p.goto('file://' + path.join(raiz, 'index.html'));
  await p.waitForFunction(() => window.juegoListo, null, { timeout: 120000 });
  const salida = await p.evaluate(() => {
    const lienzo = (w, h) => { const c = document.createElement('canvas'); c.width = w; c.height = h; const x = c.getContext('2d'); x.imageSmoothingEnabled = false; return [c, x]; };
    const dibujo = (x, n, cx, cy, k) => { const r = SPR[n]; x.drawImage(ATLAS, r.x, r.y, r.w, r.h, Math.round(cx - (r.w * k) / 2), Math.round(cy - (r.h * k) / 2), r.w * k, r.h * k); };
    const fondo = (x, t) => { // la oscuridad de la cueva con el resplandor del Corazón
      const g = x.createRadialGradient(t / 2, t * 0.5, 0, t / 2, t / 2, t * 0.72);
      g.addColorStop(0, '#4a0c22'); g.addColorStop(0.35, '#22060f'); g.addColorStop(1, '#050206');
      x.fillStyle = g; x.fillRect(0, 0, t, t);
      // motas de luz, como las esporas que flotan en el juego
      let s = 7; const r = () => ((s = (s * 16807) % 2147483647) / 2147483647);
      for (let k = 0; k < 40; k++) { const a = r() * Math.PI * 2, d = t * (0.18 + r() * 0.34), q = Math.max(1, Math.round(t / 108)); x.fillStyle = r() < 0.5 ? 'rgba(255,60,180,0.55)' : 'rgba(255,140,160,0.35)'; x.fillRect(Math.round(t / 2 + Math.cos(a) * d), Math.round(t / 2 + Math.sin(a) * d), q, q); }
    };
    const frente = (x, t, k, conLuz) => {
      // resplandor detrás del Corazón
      const g = x.createRadialGradient(t / 2, t / 2, 0, t / 2, t / 2, t * 0.3);
      g.addColorStop(0, 'rgba(255,40,90,0.45)'); g.addColorStop(1, 'rgba(255,40,90,0)');
      if (conLuz) { x.fillStyle = g; x.fillRect(0, 0, t, t); }
      dibujo(x, 'corazon', t / 2, t / 2, k);
      if (conLuz) { x.globalCompositeOperation = 'lighter'; dibujo(x, 'corazon_luz', t / 2, t / 2, k); x.globalCompositeOperation = 'source-over'; }
    };
    const png = (c) => c.toDataURL('image/png');
    const out = {};
    for (const [dens, t, k] of [['mdpi', 108, 1], ['xhdpi', 216, 2], ['xxhdpi', 324, 3], ['xxxhdpi', 432, 4]]) {
      let [c, x] = lienzo(t, t); fondo(x, t); out[`mipmap-${dens}/ic_launcher_background.png`] = png(c);
      [c, x] = lienzo(t, t); frente(x, t, k, true); out[`mipmap-${dens}/ic_launcher_foreground.png`] = png(c);
      [c, x] = lienzo(t, t); dibujo(x, 'corazon', t / 2, t / 2, k); x.globalCompositeOperation = 'source-in'; x.fillStyle = '#fff'; x.fillRect(0, 0, t, t); out[`mipmap-${dens}/ic_launcher_monochrome.png`] = png(c);
    }
    const [c, x] = lienzo(512, 512); fondo(x, 512); frente(x, 512, 6, true); out['tienda/icono-512.png'] = png(c);
    return out;
  });
  for (const [n, url] of Object.entries(salida)) {
    const f = n.startsWith('tienda/') ? path.join(tienda, n.slice(7)) : path.join(res, n);
    fs.mkdirSync(path.dirname(f), { recursive: true });
    fs.writeFileSync(f, Buffer.from(url.split(',')[1], 'base64'));
  }
  console.log(`${Object.keys(salida).length} imágenes`);
  await b.close();
})();
