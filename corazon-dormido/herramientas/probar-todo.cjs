// Prueba a fondo: recorre las regiones, saca cada criatura y cada guardián, abre todos los paneles con todo el
// inventario lleno, mide los FPS y lista los dibujos que el juego pide y no existen (window.FALTAN).
//   node corazon-dormido/herramientas/probar-todo.cjs <carpeta-capturas> [vertical]
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
  await p.waitForTimeout(800);
  await p.screenshot({ path: path.join(salida, '00-titulo.png') });
  await p.evaluate(() => { localStorage.clear(); empezar(false); });
  await p.waitForTimeout(1200);
  const foto = async (n, espera = 900) => { await p.waitForTimeout(espera); await p.screenshot({ path: path.join(salida, n + '.png') }); };
  const fps = () => p.evaluate(() => new Promise((ok) => { let n = 0; const t0 = performance.now(); const f = () => { n++; if (performance.now() - t0 < 2000) requestAnimationFrame(f); else ok(Math.round(n / ((performance.now() - t0) / 1000))); }; requestAnimationFrame(f); }));
  await foto('01-inicio');
  console.log('FPS al empezar:', await fps());
  // ¿todos los objetos del inventario tienen icono?
  const sinIcono = await p.evaluate(() => Object.values(ITEMS).filter((it) => !SPR[it.icono]).map((it) => it.id));
  if (sinIcono.length) console.log('SIN ICONO:', sinIcono.join(' '));
  // a cada sitio: busca una casilla libre en la región y lleva allí al jugador
  const ir = (q) => p.evaluate((q) => {
    const libre = (x, y) => enMundo(x, y) && !M.muro[idx(x, y)] && M.piso[idx(x, y)] !== SUELO.AGUA && !M.objetos.has(idx(x, y));
    let x = q.x, y = q.y;
    if (q.region !== undefined) {
      for (let k = 0; k < 20000; k++) { const a = Math.random() * 6.283, r = Math.random() * 160; x = Math.round(CENTRO + Math.cos(a) * r); y = Math.round(CENTRO + Math.sin(a) * r); if (libre(x, y) && M.region[idx(x, y)] === q.region && libre(x + 1, y) && libre(x, y + 1)) break; }
    } else { for (let r = 0; r < 8 && !libre(x, y); r++) for (let d = 0; d < 16; d++) { const xx = q.x + Math.round(Math.cos(d) * r), yy = q.y + Math.round(Math.sin(d) * r); if (libre(xx, yy)) { x = xx; y = yy; break; } } }
    J.jug.x = x * T + 8; J.jug.y = y * T + 8; J.cam.x = J.jug.x; J.cam.y = J.jug.y; J.jug.vida = J.st.vidaMax;
    return [x, y, NOMBRE_REGION[M.region[idx(x, y)]]];
  }, q);
  for (const [n, q] of [['santuario', { x: 160, y: 160 }], ['raices', { region: 1 }], ['arcilla', { region: 2 }], ['ruinas', { region: 3 }]]) {
    const r = await ir(q);
    if (n === 'santuario') await p.evaluate(() => { J.jug.y += 3 * T; J.cam.y = J.jug.y; });
    console.log('ir a', n, r);
    await foto('02-' + n, 1500);
  }
  // guaridas de los jefes
  const guaridas = await p.evaluate(() => Object.fromEntries(Object.entries(M.guaridas).map(([k, g]) => [k, [g.x, g.y]])));
  console.log('guaridas', JSON.stringify(guaridas));
  for (const [id, [gx, gy]] of Object.entries(guaridas)) {
    await ir({ x: gx, y: gy + (id === 'ozmar' ? 4 : 5) });
    await p.evaluate(() => { window.danarJugador = () => {}; });
    await foto('03-jefe-' + id, 2500);
    const est = await p.evaluate((id) => { const j = J.jefes[id]; return `${j.estado} vida ${Math.round(j.vida)}/${j.vidaMax} activo ${j.activo}`; }, id);
    console.log('jefe', id, est, 'FPS', await fps());
    await foto('03-jefe-' + id + '-b', 1200);
  }
  // criaturas en fila delante del jugador (en el suelo abierto del santuario, bajo el Corazón)
  await ir({ x: 160, y: 166 });
  await p.evaluate(() => {
    J.enemigos = []; let k = 0;
    for (const tipo of Object.keys(CRIATURAS)) { const e = nuevaCriatura(tipo, J.jug.x - 60 + (k % 5) * 30, J.jug.y - 30 + Math.floor(k / 5) * 40); e.aturdido = 99; J.enemigos.push(e); k++; }
    window.danarJugador = () => {};
  });
  await foto('04-criaturas', 600);
  await p.evaluate(() => { for (const e of J.enemigos) e.aturdido = 0; });
  await foto('04-criaturas-b', 1500);
  // todo el inventario y el equipo puesto
  await p.evaluate(() => {
    J.enemigos = [];
    J.inv = new Array(N_INV).fill(null);
    const ids = Object.keys(ITEMS);
    for (let i = 0; i < Math.min(N_INV, ids.length); i++) J.inv[i] = { id: ids[i], n: Math.min(ITEMS[ids[i]].max || 99, 5) };
    J.equipo = { casco: { id: 'casco_hierro', n: 1 }, peto: { id: 'peto_hierro', n: 1 }, botas: { id: 'botas_hierro', n: 1 }, acc1: { id: 'anillo_oro', n: 1 }, acc2: null };
    calcularStats();
  });
  await foto('05-equipado', 500);
  for (const panel of ['mochila', 'crear', 'mapa', 'pausa']) {
    await p.evaluate((n) => { J.panel = n; if (n === 'mochila') J.pestana = 'mochila'; }, panel);
    await foto('06-' + panel, 400);
  }
  await p.evaluate(() => { J.panel = 'mochila'; J.pestana = 'habilidades'; });
  await foto('06-habilidades', 400);
  // el resto de objetos (la segunda mitad) para ver sus iconos
  await p.evaluate(() => { const ids = Object.keys(ITEMS); J.inv = new Array(N_INV).fill(null); for (let i = N_INV; i < ids.length && i - N_INV < N_INV; i++) J.inv[i - N_INV] = { id: ids[i], n: 3 }; J.panel = 'mochila'; J.pestana = 'mochila'; });
  await foto('06-mochila-2', 400);
  await p.evaluate(() => { const ids = Object.keys(ITEMS); J.inv = new Array(N_INV).fill(null); for (let i = 2 * N_INV; i < ids.length && i - 2 * N_INV < N_INV; i++) J.inv[i - 2 * N_INV] = { id: ids[i], n: 3 }; });
  await foto('06-mochila-3', 400);
  // mesa, cofre y olla junto al jugador
  await p.evaluate(() => {
    J.panel = null;
    const tx = Math.floor(J.jug.x / T), ty = Math.floor(J.jug.y / T);
    const sitios = [['mesa', 2, -1], ['mesa_cobre', -2, -1], ['yunque', 3, 1], ['cofre', -3, 1], ['olla', 0, -2], ['horno', 1, 2], ['cama', -1, 2], ['puerta', 2, 2], ['farol', -2, -2], ['maceta', 3, -2]];
    for (const [t, dx, dy] of sitios) { const x = tx + dx, y = ty + dy; M.muro[idx(x, y)] = 0; M.objetos.delete(idx(x, y)); ponerObjeto(x, y, t); }
    for (const [pl, dx] of [['baya', -4], ['hongo', -3], ['bulbo', 4], ['trigo', 5]]) for (let e = 0; e < 4; e++) { const x = tx + dx, y = ty - 3 + e; M.muro[idx(x, y)] = 0; M.objetos.delete(idx(x, y)); ponerSuelo(x, y, SUELO.ARADA); ponerObjeto(x, y, 'cultivo'); const o = objetoEn(x, y); o.datos = { planta: pl, crec: CULTIVOS[pl].etapa * e + 0.1, regado: e % 2 === 1 }; }
    const c = objetoEn(tx - 3, ty + 1); if (c && c.datos) { c.datos.inv[0] = { id: 'lingote_cobre', n: 20 }; c.datos.inv[1] = { id: 'madera', n: 50 }; }
  });
  await foto('07-base', 1200);
  await p.evaluate(() => { J.panel = 'crear'; });
  await foto('07-crear-mesas', 400);
  await p.evaluate(() => { J.cofreAbierto = objetoEn(Math.floor(J.jug.x / T) - 3, Math.floor(J.jug.y / T) + 1); J.panel = 'cofre'; });
  await foto('07-cofre', 400);
  await p.evaluate(() => { J.olla = objetoEn(Math.floor(J.jug.x / T), Math.floor(J.jug.y / T) - 2); J.panel = 'olla'; });
  await foto('07-olla', 400);
  await p.evaluate(() => { J.panel = null; });
  // agua y pesca
  const agua = await p.evaluate(() => {
    for (let k = 0; k < 400000; k++) { const x = 40 + Math.floor(Math.random() * 240), y = 40 + Math.floor(Math.random() * 240); if (M.piso[idx(x, y)] === SUELO.AGUA && M.piso[idx(x, y + 2)] !== SUELO.AGUA && !M.muro[idx(x, y + 2)] && M.piso[idx(x, y + 1)] === SUELO.AGUA) return [x, y + 2]; }
    return null;
  });
  if (agua) {
    await ir({ x: agua[0], y: agua[1] });
    await p.evaluate(() => { J.jug.fx = 0; J.jug.fy = -1; J.jug.dir = 'ar'; agregar('cana_madera', 1); J.sel = J.inv.findIndex((s) => s && s.id === 'cana_madera'); lanzarCana(ITEMS.cana_madera); });
    await foto('08-pesca', 800);
    await p.evaluate(() => { if (J.pesca) { J.pesca.estado = 'pica'; tirarCana(); } });
    await foto('08-pesca-lucha', 600);
  }
  console.log('FPS final:', await fps());
  // guardar y cargar
  await p.evaluate(() => { J.panel = null; J.pesca = null; guardarPartida(); });
  const antes = await p.evaluate(() => `${Math.round(J.jug.x)},${Math.round(J.jug.y)} muros ${M.muro.reduce((a, b) => a + (b ? 1 : 0), 0)} obj ${M.objetos.size}`);
  await p.reload(); await p.waitForTimeout(800);
  await p.evaluate(() => empezar(true));
  const despues = await p.evaluate(() => `${Math.round(J.jug.x)},${Math.round(J.jug.y)} muros ${M.muro.reduce((a, b) => a + (b ? 1 : 0), 0)} obj ${M.objetos.size}`);
  await foto('09-cargado', 1000);
  console.log(antes === despues ? 'guardar y cargar: bien' : `guardar y cargar: DISTINTO (${antes} / ${despues})`);
  const faltan = await p.evaluate(() => [...(window.FALTAN || [])]);
  console.log(faltan.length ? 'DIBUJOS QUE FALTAN: ' + faltan.join(' ') : 'no falta ningún dibujo');
  console.log(errs.length ? 'ERRORES:\n' + [...new Set(errs)].join('\n') : 'sin errores');
  await b.close();
  process.exitCode = errs.length || faltan.length || sinIcono.length || antes !== despues ? 1 : 0;
})();
