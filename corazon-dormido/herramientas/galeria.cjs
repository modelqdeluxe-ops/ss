// Galería para revisar el arte: lleva al jugador a cada sitio (zonas, jefes, base con muebles, criaturas) y saca
// capturas a tamaño de móvil y recortes ampliados del centro, para medir con PIL antes de dar algo por bueno.
//   node corazon-dormido/herramientas/galeria.cjs <carpeta> [filtro]
// El filtro (opcional) es parte del nombre de la escena: solo saca las que lo contienen.
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');
(async () => {
  const salida = path.resolve(process.argv[2] || '.'), filtro = process.argv[3] || '';
  fs.mkdirSync(salida, { recursive: true });
  const b = await chromium.launch();
  const ctx = await b.newContext({ viewport: { width: 870, height: 412 }, deviceScaleFactor: 2.625, isMobile: true, hasTouch: true });
  const p = await ctx.newPage();
  const errs = [];
  p.on('pageerror', (e) => errs.push(e.message + '\n' + e.stack));
  p.on('console', (m) => { if (m.type() === 'error') errs.push(m.text()); });
  await p.goto('file://' + path.resolve(__dirname, '..', 'index.html'));
  await p.waitForFunction(() => window.juegoListo, null, { timeout: 120000 });
  await p.evaluate(async () => { localStorage.clear(); await empezar(false); window.danarJugador = () => {}; J.aparecer = 1e9; });
  const foto = async (n, espera = 900, recorte = true) => {
    await p.waitForTimeout(espera);
    await p.screenshot({ path: path.join(salida, n + '.png') });
    if (recorte) {
      const v = p.viewportSize();
      await p.screenshot({ path: path.join(salida, n + '-cerca.png'), clip: { x: v.width / 2 - 150, y: v.height / 2 - 95, width: 300, height: 190 } });
    }
  };
  const ir = (x, y, extra) => p.evaluate(([x, y, extra]) => {
    const libre = (x, y) => enMundo(x, y) && !solido(x, y, true) && !M.objetos.has(idx(x, y));
    let bx = x, by = y;
    outer: for (let r = 0; r < 14; r++) for (let k = 0; k < 32; k++) { const xx = x + Math.round(Math.cos(k / 5.1) * r), yy = y + Math.round(Math.sin(k / 5.1) * r); if (libre(xx, yy)) { bx = xx; by = yy; break outer; } }
    J.jug.x = bx * T + 8; J.jug.y = by * T + 8; J.cam.x = J.jug.x; J.cam.y = J.jug.y - 6; J.enemigos = [];
    if (extra) J.jug.dir = extra;
    return [bx, by];
  }, [x, y, extra]);
  const escenas = [];
  const escena = (n, fn) => { if (!filtro || n.includes(filtro)) escenas.push([n, fn]); };
  const C = await p.evaluate(() => CENTRO);
  escena('santuario', async () => { await ir(C, C + 4); await foto('santuario'); });
  for (const [n, reg] of [['raices', 1], ['arcilla', 2], ['ruinas', 3], ['selva', 4], ['pradera', 5], ['cienaga', 8], ['ciudad', 9], ['cripta', 10]]) escena(n, async () => {
    const q = await p.evaluate((reg) => { for (let k = 0; k < 200000; k++) { const x = Math.floor(Math.random() * N_MUNDO), y = Math.floor(Math.random() * N_MUNDO); if (M.region[idx(x, y)] === reg && !solido(x, y, true) && !solido(x + 1, y, true) && !solido(x, y + 1, true)) return [x, y]; } return [CENTRO, CENTRO]; }, reg);
    await ir(q[0], q[1]); await foto(n, 1400);
  });
  // los sitios de las bandas nuevas (0.7)
  for (const [n, q] of [['costillas', 'M.costillas'], ['pozo', 'M.pozo'], ['barrio', 'M.barrios[0]'], ['biblioteca', 'M.biblioteca'], ['templo-placas', 'M.templo2'], ['archivero', 'M.archivero'], ['muda', 'M.muda'], ['cripta-entrada', 'M.cripta.entrada'], ['braseros', 'M.puzles[0].puerta'], ['palafito', 'M.fijas.find((f) => f.tipo === "gruto_cazador" && M.region[idx(f.x, f.y)] === 8)']]) escena('sitio-' + n, async () => {
    const g = await p.evaluate((q) => { const g = eval(q); return g ? [g.x, g.y + 3] : null; }, q);
    if (!g) return;
    await ir(g[0], g[1]); await foto('sitio-' + n, 1400);
  });
  escena('jugador', async () => {
    await ir(C + 6, C + 6);
    for (const d of ['ab', 'de', 'ar', 'iz']) { await p.evaluate((d) => { J.jug.dir = d; J.jug.fx = d === 'de' ? 1 : d === 'iz' ? -1 : 0; J.jug.fy = d === 'ab' ? 1 : d === 'ar' ? -1 : 0; }, d); await foto('jugador-' + d, 300); }
    await p.evaluate(() => { J.equipo.casco = { id: 'casco_hierro', n: 1 }; J.equipo.peto = { id: 'peto_hierro', n: 1 }; J.equipo.botas = { id: 'botas_hierro', n: 1 }; J.jug.dir = 'ab'; calcularStats(); });
    await foto('jugador-armadura', 300);
  });
  escena('base', async () => {
    await ir(C + 10, C + 12);
    await p.evaluate(() => {
      const tx = Math.floor(J.jug.x / T), ty = Math.floor(J.jug.y / T);
      for (let y = ty - 4; y <= ty + 4; y++) for (let x = tx - 6; x <= tx + 6; x++) { M.muro[idx(x, y)] = 0; M.objetos.delete(idx(x, y)); }
      const sitios = [['mesa', 2, -2], ['mesa_cobre', -2, -2], ['yunque', 4, -2], ['cofre', -4, -2], ['olla', 0, -3], ['horno', 4, 1], ['cama', -4, 1], ['puerta', 2, 2], ['farol', -2, 2], ['maceta', 5, 2], ['caja_madera', -5, 3], ['vasija_flor', 6, -1], ['incubadora', 0, 3], ['hoguera', -1, 1]];
      for (const [t, dx, dy] of sitios) if (OBJ[t]) ponerObjeto(tx + dx, ty + dy, t);
      GL.olvidarTodo();
    });
    await foto('base', 1200);
  });
  escena('criaturas', async () => {
    await ir(C + 8, C + 14);
    await p.evaluate(() => {
      const tipos = Object.keys(CRIATURAS); let k = 0;
      for (const tipo of tipos) { const e = nuevaCriatura(tipo, J.jug.x - 100 + (k % 8) * 28, J.jug.y - 40 + Math.floor(k / 8) * 36); e.aturdido = 99; J.enemigos.push(e); k++; }
    });
    await foto('criaturas', 400, false);
  });
  for (const id of ['topo', 'reina', 'ozmar', 'hiedra', 'tamar', 'grul', 'centinela']) escena('jefe-' + id, async () => {
    const g = await p.evaluate((id) => { const g = M.guaridas[id]; return [g.x, id === 'ozmar' ? g.y0 + g.h - 4 : g.y + (id === 'tamar' ? 7 : 6)]; }, id);
    await ir(g[0], g[1]);
    if (id === 'tamar') await p.evaluate(() => { J.hito.tamarInvocado = true; J.jefes.tamar.invocar = true; });
    await foto('jefe-' + id, 2600);
    await foto('jefe-' + id + '-b', 1600);
  });
  for (const [n, fn] of escenas) { console.log('escena', n); await fn(); }
  const faltan = await p.evaluate(() => [...(window.FALTAN || [])]);
  if (faltan.length) console.log('FALTAN: ' + faltan.join(' '));
  console.log(errs.length ? 'ERRORES:\n' + [...new Set(errs)].join('\n') : 'sin errores');
  await b.close();
})();
