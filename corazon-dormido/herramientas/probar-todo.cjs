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
  await p.waitForFunction(() => window.juegoListo, null, { timeout: 120000 });
  await p.evaluate(async () => { localStorage.clear(); await empezar(false); });
  await p.waitForTimeout(1200);
  const foto = async (n, espera = 900) => { await p.waitForTimeout(espera); await p.screenshot({ path: path.join(salida, n + '.png') }); };
  const fps = () => p.evaluate(() => new Promise((ok) => { let n = 0; const t0 = performance.now(); const f = () => { n++; if (performance.now() - t0 < 2000) requestAnimationFrame(f); else ok(Math.round(n / ((performance.now() - t0) / 1000))); }; requestAnimationFrame(f); }));
  await foto('01-inicio');
  const [N_INV_P, N_ITEMS] = await p.evaluate(() => [N_INV, Object.keys(ITEMS).length]);
  console.log('FPS al empezar:', await fps());
  // ¿todos los objetos del inventario tienen icono?
  const sinIcono = await p.evaluate(() => Object.entries(ITEMS).filter(([, it]) => !SPR[it.icono]).map(([id]) => id));
  if (sinIcono.length) console.log('SIN ICONO:', sinIcono.join(' '));
  // a cada sitio: busca una casilla libre en la región y lleva allí al jugador
  const ir = (q) => p.evaluate((q) => {
    void 0;
    const libre = (x, y) => enMundo(x, y) && !M.muro[idx(x, y)] && M.piso[idx(x, y)] !== SUELO.AGUA && !M.objetos.has(idx(x, y));
    let x = q.x, y = q.y;
    if (q.region !== undefined) {
      for (let k = 0; k < 20000; k++) { const a = Math.random() * 6.283, r = Math.random() * 140; x = Math.round(CENTRO + Math.cos(a) * r); y = Math.round(CENTRO + Math.sin(a) * r); if (libre(x, y) && M.region[idx(x, y)] === q.region && libre(x + 1, y) && libre(x, y + 1)) break; }
    } else { for (let r = 0; r < 8 && !libre(x, y); r++) for (let d = 0; d < 16; d++) { const xx = q.x + Math.round(Math.cos(d) * r), yy = q.y + Math.round(Math.sin(d) * r); if (libre(xx, yy)) { x = xx; y = yy; break; } } }
    J.jug.x = x * T + 8; J.jug.y = y * T + 8; J.cam.x = J.jug.x; J.cam.y = J.jug.y; J.jug.vida = J.st.vidaMax;
    return [x, y, NOMBRE_REGION[M.region[idx(x, y)]]];
  }, q);
  const C0 = await p.evaluate(() => CENTRO);
  for (const [n, q] of [['santuario', { x: C0, y: C0 }], ['raices', { region: 1 }], ['arcilla', { region: 2 }], ['ruinas', { region: 3 }]]) {
    const r = await ir(q);
    if (n === 'santuario') await p.evaluate(() => { J.jug.y += 3 * T; J.cam.y = J.jug.y; });
    console.log('ir a', n, r);
    await foto('02-' + n, 1500);
  }
  // guaridas de los jefes
  const guaridas = await p.evaluate(() => Object.fromEntries(Object.entries(M.guaridas).filter(([k]) => J.jefes[k]).map(([k, g]) => [k, [g.x, g.y]])));
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
  await ir({ x: C0, y: C0 + 16 }); // al sur del santuario (no encima de los altares)
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
    await p.evaluate((n) => { J.panel = n; if (n === 'mochila') UI.pestana = 'mochila'; }, panel);
    await foto('06-' + panel, 400);
  }
  await p.evaluate(() => { J.panel = 'mochila'; UI.pestana = 'habilidades'; });
  await foto('06-habilidades', 400);
  // el resto de objetos (la segunda mitad) para ver sus iconos
  await p.evaluate(() => { const ids = Object.keys(ITEMS); J.inv = new Array(N_INV).fill(null); for (let i = N_INV; i < ids.length && i - N_INV < N_INV; i++) J.inv[i - N_INV] = { id: ids[i], n: 3 }; J.panel = 'mochila'; UI.pestana = 'mochila'; });
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
    for (let k = 0; k < 400000; k++) { const x = CENTRO - 140 + Math.floor(Math.random() * 280), y = CENTRO - 140 + Math.floor(Math.random() * 280); if (M.piso[idx(x, y)] === SUELO.AGUA && M.piso[idx(x, y + 2)] !== SUELO.AGUA && !M.muro[idx(x, y + 2)] && M.piso[idx(x, y + 1)] === SUELO.AGUA) return [x, y + 2]; }
    return null;
  });
  if (agua) {
    await ir({ x: agua[0], y: agua[1] });
    await p.evaluate(() => { J.jug.fx = 0; J.jug.fy = -1; J.jug.dir = 'ar'; agregar('cana_madera', 1); J.sel = J.inv.findIndex((s) => s && s.id === 'cana_madera'); lanzarCana(ITEMS.cana_madera); });
    await foto('08-pesca', 800);
    await p.evaluate(() => { if (J.pesca) { J.pesca.estado = 'pica'; tirarCana(); } });
    await foto('08-pesca-lucha', 600);
  }
  // ---------------------------------------------------------------- fase 2: la Selva Esmeralda
  const fase2 = await p.evaluate(() => {
    const out = [];
    window.danarJugador = () => {};
    // la Muralla: despierta el Corazón, habla con él y toca la Muralla
    J.corazonDespierto = true; J.hito.hablo2 = true;
    const a = M.angSelva;
    // el final del camino de grava, junto a la Muralla (en la dirección de la Selva)
    let mejor = null;
    for (let d = R_MURALLA - 6; d < R_MURALLA + 1; d += 0.5) for (let da = -0.08; da <= 0.08; da += 0.01) {
      const x = Math.round(CENTRO + Math.cos(a + da) * d), y = Math.round(CENTRO + Math.sin(a + da) * d);
      if (solido(x, y, true) || M.piso[idx(x, y)] !== SUELO.GRAVA) continue;
      let cerca = false; for (let yy = y - 2; yy <= y + 2; yy++) for (let xx = x - 2; xx <= x + 2; xx++) if (M.muro[idx(xx, yy)] === MURO.MURALLA) cerca = true;
      if (cerca) { mejor = [x, y]; break; }
    }
    if (mejor) { J.jug.x = mejor[0] * T + 8; J.jug.y = mejor[1] * T + 8; J.cam.x = J.jug.x; J.cam.y = J.jug.y; }
    out.push('camino hasta la Muralla: ' + (mejor ? 'sí' : 'NO'));
    const antes = M.muro.filter((m) => m === MURO.MURALLA).length;
    const u = usoCercano();
    out.push('muralla: ' + (u ? u.txt : 'NO HAY BOTÓN') + ' ' + antes);
    if (u) u.fn(); else abrirMuralla();
    out.push('muralla tras tocar: ' + M.muro.filter((m) => m === MURO.MURALLA).length + ' abierta ' + J.murallaAbierta);
    return out;
  });
  console.log(fase2.join('\n'));
  await foto('10-muralla', 1500);
  await p.evaluate(() => { const a = M.angSelva; J.jug.x = (CENTRO + Math.cos(a) * (R_MURALLA + 8)) * T; J.jug.y = (CENTRO + Math.sin(a) * (R_MURALLA + 8)) * T; J.cam.x = J.jug.x; J.cam.y = J.jug.y; });
  await foto('10-muralla-abierta', 1500);
  const zonas = await p.evaluate(() => {
    const libre = (x, y) => !solido(x, y, true) && !M.objetos.has(idx(x, y));
    const cerca = (x, y) => { for (let r = 0; r < 12; r++) for (let k = 0; k < 24; k++) { const xx = x + Math.round(Math.cos(k) * r), yy = y + Math.round(Math.sin(k) * r); if (libre(xx, yy)) return [xx, yy]; } return [x, y]; };
    const a = M.angSelva, z = {};
    z.selva = cerca(Math.round(CENTRO + Math.cos(a) * 200), Math.round(CENTRO + Math.sin(a) * 200));
    z.tamar = [M.guaridas.tamar.x, M.guaridas.tamar.y + 7];
    z.hiedra = [M.guaridas.hiedra.x, M.guaridas.hiedra.y + 9];
    z.mazmorra = cerca(M.mazmorra.x, M.mazmorra.y);
    if (M.aldeas && M.aldeas[0]) z.aldea = cerca(M.aldeas[0].x, M.aldeas[0].y + 2);
    return z;
  });
  for (const [n, [x, y]] of Object.entries(zonas)) {
    await ir({ x, y });
    if (n === 'tamar') await p.evaluate(() => { J.inv[N_INV - 1] = { id: 'ofrenda_cielo', n: 1 }; INTERACCIONES.altar_cielo(); });
    await foto('11-' + n, 2200);
    if (n === 'tamar' || n === 'hiedra') await foto('11-' + n + '-b', 1500);
  }
  // una casa con cama e ídolo: tiene que llegar el mercader
  const casa = await p.evaluate(() => {
    J.inv = new Array(N_INV).fill(null); agregar('pico_carmesi', 1); agregar('espada_carmesi', 1);
    const out = [], p = J.jug, x0 = Math.floor(p.x / T) - 3, y0 = Math.floor(p.y / T) - 3;
    for (let y = y0 - 1; y <= y0 + 5; y++) for (let x = x0 - 1; x <= x0 + 5; x++) { const i = idx(x, y); M.objetos.delete(i); M.piso[i] = SUELO.TABLAS; ponerMuro(x, y, x === x0 - 1 || y === y0 - 1 || x === x0 + 5 || y === y0 + 5 ? MURO.SELVA_MADERA : 0); }
    ponerMuro(x0 + 2, y0 + 5, 0); ponerObjeto(x0 + 2, y0 + 5, 'puerta');
    ponerObjeto(x0, y0, 'cama'); ponerObjeto(x0 + 4, y0, 'idolo_barbas'); ponerObjeto(x0 + 4, y0 + 2, 'incubadora'); ponerObjeto(x0, y0 + 3, 'lampara_pie');
    J.jug.x = (x0 + 2) * T + 8; J.jug.y = (y0 + 3) * T + 8; J.cam.x = J.jug.x; J.cam.y = J.jug.y;
    out.push('casa: ' + (casaDe(x0 + 4, y0) ? 'vale' : 'NO VALE'));
    return out;
  });
  console.log(casa.join('\n'));
  await p.waitForTimeout(3500);
  const merc = await p.evaluate(() => {
    const out = [];
    out.push('mercaderes: ' + M.mercaderes.map((m) => m.tipo).join(',') + (J.hito.mercader ? ' (hito)' : ''));
    if (M.mercaderes[0]) {
      const m = M.mercaderes[0]; agregar('moneda', 500); agregar('lingote_hierro', 5);
      const antes = J.cuenta('moneda'); comprarMercader(m, 0); out.push('compra: ' + (antes - J.cuenta('moneda')) + ' monedas por ' + m.stock[0][0]);
      const i = J.inv.findIndex((s) => s && s.id === 'lingote_hierro'), a2 = J.cuenta('moneda'); venderMercader(i, 1); out.push('venta: +' + (J.cuenta('moneda') - a2) + ' monedas');
      abrirTienda(m);
    }
    return out;
  });
  console.log(merc.join('\n'));
  await foto('12-tienda', 500);
  await p.evaluate(() => { UI.pestana = 'vender'; UI.selInv = 0; });
  await foto('12-tienda-vender', 400);
  // incubadora y mascota
  const incu = await p.evaluate(() => {
    const out = [], tx = Math.floor(J.jug.x / T), ty = Math.floor(J.jug.y / T);
    let o = null; for (let y = ty - 4; y <= ty + 4 && !o; y++) for (let x = tx - 4; x <= tx + 4; x++) { const q = objetoEn(x, y); if (q && q.tipo === 'incubadora') { o = q; break; } }
    agregar('huevo_luz', 1); INTERACCIONES.incubadora(o);
    o.datos.huevo = 'huevo_luz'; o.datos.t0 = J.tiempoMundo - TIEMPO_INCUBAR - 1; quitar('huevo_luz', 1);
    out.push('incubadora lista: ' + huevoListo(o));
    return out;
  });
  console.log(incu.join('\n'));
  await foto('13-incubadora', 400);
  await p.evaluate(() => { J.panel = null; agregar('mascota_luci', 1); agregar('mascota_topito', 1); const i = J.inv.findIndex((s) => s && s.id === 'mascota_topito'); ponerEquipo(i); });
  const pet = await p.evaluate(() => 'mascota equipada: ' + (J.equipo.mascota && J.equipo.mascota.id));
  console.log(pet);
  await p.evaluate(() => { const e = nuevaCriatura('babosa_veneno', J.jug.x + 30, J.jug.y); J.enemigos.push(e); });
  await foto('14-mascota', 1500);
  // bomba contra una pared
  const bomba = await p.evaluate(() => {
    const tx = Math.floor(J.jug.x / T), ty = Math.floor(J.jug.y / T);
    for (let y = ty - 6; y <= ty - 4; y++) for (let x = tx - 1; x <= tx + 1; x++) { M.objetos.delete(idx(x, y)); ponerMuro(x, y, MURO.HIERBA); }
    const antes = [-6, -5, -4].reduce((a, dy) => a + (M.muro[idx(tx, ty + dy)] ? 1 : 0), 0);
    estallido((tx) * T + 8, (ty - 5) * T + 8, 0.01, 70);
    return antes;
  });
  await p.waitForTimeout(400);
  const trasBomba = await p.evaluate(() => { const tx = Math.floor(J.jug.x / T), ty = Math.floor(J.jug.y / T); return [-6, -5, -4].reduce((a, dy) => a + (M.muro[idx(tx, ty + dy)] ? 1 : 0), 0); });
  console.log(`bomba: paredes ${bomba} → ${trasBomba}`);
  await foto('15-bomba', 200);
  // piedra de regreso
  const regreso = await p.evaluate(() => { agregar('piedra_regreso', 1); const s = J.inv.find((q) => q && q.id === 'piedra_regreso'); usarRegreso(s); return J.regreso ? 'regreso: en marcha' : 'regreso: NO'; });
  console.log(regreso);
  await p.waitForTimeout(1800);
  console.log(await p.evaluate(() => 'regreso: ' + Math.round(Math.hypot(J.jug.x / T - CENTRO, J.jug.y / T - CENTRO)) + ' casillas del Corazón'));
  // altares de los titanes y tablilla
  const alt = await p.evaluate(() => {
    agregar('gema_cielo', 1); agregar('gema_hiedra', 1);
    for (const [t, a] of Object.entries(M.altares)) INTERACCIONES.altar_titan(objetoEn(a.x, a.y));
    const r = `altares: ${JSON.stringify(J.altares)} panel ${J.panel}`;
    return r;
  });
  console.log(alt);
  await foto('16-fin2', 400);
  await p.evaluate(() => { J.panel = null; INTERACCIONES.tablilla({ datos: { n: 1 } }); });
  await foto('16-tablilla', 300);
  await p.evaluate(() => { J.panel = 'corazon'; });
  await foto('16-corazon', 300);
  await p.evaluate(() => { J.panel = 'mapa'; });
  await foto('16-mapa', 300);
  await p.evaluate(() => { J.panel = null; });
  // los objetos que quedan por ver en la mochila
  for (let pag = 3; pag * N_INV_P < N_ITEMS; pag++) {
    await p.evaluate((pag) => { const ids = Object.keys(ITEMS); J.inv = new Array(N_INV).fill(null); for (let i = pag * N_INV; i < ids.length && i - pag * N_INV < N_INV; i++) J.inv[i - pag * N_INV] = { id: ids[i], n: 3 }; J.panel = 'mochila'; UI.pestana = 'mochila'; }, pag);
    await foto('17-mochila-' + pag, 400);
  }
  await p.evaluate(() => { J.panel = null; });
  console.log('FPS final:', await fps());
  // guardar y cargar
  await p.evaluate(() => { J.panel = null; J.pesca = null; return guardarPartida(); });
  const antes = await p.evaluate(() => `${Math.round(J.jug.x)},${Math.round(J.jug.y)} muros ${M.muro.reduce((a, b) => a + (b ? 1 : 0), 0)} obj ${M.objetos.size} merc ${M.mercaderes.length} muralla ${J.murallaAbierta} mascota ${J.equipo.mascota && J.equipo.mascota.id}`);
  await p.reload(); await p.waitForFunction(() => window.juegoListo, null, { timeout: 120000 });
  await p.evaluate(() => empezar(true));
  const despues = await p.evaluate(() => `${Math.round(J.jug.x)},${Math.round(J.jug.y)} muros ${M.muro.reduce((a, b) => a + (b ? 1 : 0), 0)} obj ${M.objetos.size} merc ${M.mercaderes.length} muralla ${J.murallaAbierta} mascota ${J.equipo.mascota && J.equipo.mascota.id}`);
  await foto('09-cargado', 1000);
  console.log(antes === despues ? 'guardar y cargar: bien' : `guardar y cargar: DISTINTO (${antes} / ${despues})`);
  const faltan = await p.evaluate(() => [...(window.FALTAN || [])]);
  console.log(faltan.length ? 'DIBUJOS QUE FALTAN: ' + faltan.join(' ') : 'no falta ningún dibujo');
  console.log(errs.length ? 'ERRORES:\n' + [...new Set(errs)].join('\n') : 'sin errores');
  await b.close();
  process.exitCode = errs.length || faltan.length || sinIcono.length || antes !== despues ? 1 : 0;
})();
