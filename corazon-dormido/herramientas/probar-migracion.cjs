// Prueba que las partidas viejas pasan bien al mundo grande de la 0.7 (guardado v5 en IndexedDB):
//   - v3 (fase 1, mundo de 320): el personaje, la mochila, lo logrado, las estatuas y lo que había en los cofres y
//     los muebles puestos (en cofres de mudanza junto al Corazón);
//   - v4 (0.6, mundo de 640): el anillo interior se pega tal cual (lo picado, la casa, los cofres), lo de fuera de la
//     Muralla va a cofres de mudanza y el jugador sigue donde estaba.
// Después guarda (v5), recarga y comprueba que todo sigue igual.
//   node corazon-dormido/herramientas/probar-migracion.cjs <carpeta-capturas>
const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const salida = path.resolve(process.argv[2] || '.');
  const b = await chromium.launch();
  const p = await (await b.newContext({ viewport: { width: 870, height: 412 }, deviceScaleFactor: 2 })).newPage();
  const errs = [];
  p.on('pageerror', (e) => errs.push(e.message + '\n' + e.stack));
  const url = 'file://' + path.resolve(__dirname, '..', 'index.html');
  const listo = () => p.waitForFunction(() => window.juegoListo, null, { timeout: 120000 });
  const borrarTodo = () => p.evaluate(async () => { localStorage.clear(); await Guardado.borrar(); });
  await p.goto(url); await listo();
  let ok = true;

  // ---- v3
  await borrarTodo();
  await p.evaluate(() => {
    localStorage.setItem('corazon-dormido-v3', JSON.stringify({
      v: 3, mundo: { v: 3, sem: 4242, objetos: [[150, 150, 'cofre', 0, { inv: [{ id: 'madera', n: 37 }, { id: 'lingote_hierro', n: 9 }, null] }], [152, 150, 'mesa_hierro', 0, null], [153, 150, 'horno', 0, null], [154, 150, 'cama', 0, null], [155, 151, 'antorcha', 0, null]] },
      jug: { x: 100, y: 100, vida: 80, hambre: 50 }, inv: [{ id: 'pico_hierro', n: 1 }, { id: 'espada_hierro', n: 1 }, { id: 'plato:baya:hongo', n: 3 }],
      sel: 0, equipo: { casco: { id: 'casco_hierro', n: 1 }, peto: null, botas: null, acc1: null, acc2: null }, hab: { mineria: { n: 12, xp: 3 } }, buffs: [], hito: { mesa: true, horno: true },
      objetivo: 13, jefesMuertos: { topo: true, reina: true, ozmar: true }, estatuas: { topo: true, reina: true, ozmar: true }, cama: { x: 154, y: 150 }, tiempo: 5000, despierto: true,
    }));
  });
  await p.evaluate(() => comprobarPartida());
  const r3 = await p.evaluate(async () => {
    const out = { tipo: tipoPartida };
    await empezar(true);
    out.inv = J.inv.filter(Boolean).map((s) => `${s.id}×${s.n}`).join(' ');
    out.casco = J.equipo.casco && J.equipo.casco.id;
    out.mineria = nivel('mineria');
    out.estatuas = Object.values(M.estatuas).map((e) => objetoEn(e.x, e.y).extra).join(',');
    const cofres = [];
    for (let y = CENTRO - 9; y <= CENTRO + 9; y++) for (let x = CENTRO - 9; x <= CENTRO + 9; x++) { const o = objetoEn(x, y); if (o && o.tipo === 'cofre') cofres.push(o.datos.inv.filter(Boolean).map((s) => `${s.id}×${s.n}`).join(' ')); }
    out.cofres = cofres.join(' | ');
    out.v5 = await Guardado.hay();
    return out;
  });
  console.log('v3', JSON.stringify(r3));
  const ok3 = r3.tipo === 'v3' && r3.inv.includes('pico_hierro') && r3.casco === 'casco_hierro' && r3.mineria === 12 && r3.estatuas === '1,1,1' && r3.cofres.includes('madera×37') && r3.cofres.includes('mesa_hierro×1') && r3.v5;
  console.log(ok3 ? 'migración v3: bien' : 'migración v3: MAL');
  ok = ok && ok3;

  // ---- v4: una partida de la 0.6 hecha a mano con el mismo interior (el generador lo hace igual que entonces)
  await borrarTodo();
  await p.evaluate(() => {
    const n = 640, O = CENTRO - n / 2, sem = 777;
    J.estado = 'titulo';
    generarMundo(sem, true);
    const capa = (k) => { const a = new Uint8Array(n * n); for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) a[y * n + x] = M[k][idx(x + O, y + O)]; return a; };
    const muro = capa('muro'), mena = capa('mena'), piso = capa('piso'), visto = capa('visto');
    // lo que hizo el jugador: un agujero cavado, su casa con un cofre y otro cofre fuera de la Muralla
    for (let y = 300; y < 306; y++) for (let x = 340; x < 346; x++) { muro[y * n + x] = 0; visto[y * n + x] = 1; }
    const rle = (u8) => { const out = []; for (let i = 0; i < u8.length;) { const v = u8[i]; let c = 1; while (i + c < u8.length && u8[i + c] === v && c < 255) c++; out.push(v, c); i += c; } let s = ''; for (let i = 0; i < out.length; i += 8192) s += String.fromCharCode.apply(null, out.slice(i, i + 8192)); return btoa(s); };
    const objetos = [...M.objetos.values()].map((o) => [o.x - O, o.y - O, o.tipo, o.extra, o.datos || null]);
    objetos.push([342, 303, 'cofre', 0, { inv: [{ id: 'lingote_carmesi', n: 21 }, null] }], [344, 303, 'mesa_carmesi', 0, null], [600, 320, 'cofre', 0, { inv: [{ id: 'gema_antigua', n: 5 }] }], [601, 320, 'yunque_carmesi', 0, null]);
    localStorage.setItem('corazon-dormido-v4', JSON.stringify({
      v: 4, mundo: { v: 4, n, sem, muro: rle(muro), mena: rle(mena), piso: rle(piso), visto: rle(visto), objetos, fijas: [], mercaderes: [] },
      jug: { x: 343 * T + 8, y: 304 * T + 8, vida: 90, hambre: 60 }, inv: [{ id: 'pico_carmesi', n: 1 }], sel: 0, equipo: { casco: null, peto: null, botas: null, acc1: null, acc2: null, mascota: null },
      hab: {}, buffs: [], hito: { selva: true }, objetivo: 16, jefesMuertos: { topo: true, reina: true, ozmar: true }, estatuas: { topo: true, reina: true, ozmar: true }, cama: null, tiempo: 9000, despierto: true,
      mascotas: {}, altares: {}, muralla: true, aspecto: null,
    }));
  });
  await p.evaluate(() => comprobarPartida());
  const comprobar4 = () => p.evaluate(() => {
    const O = CENTRO - 320, out = {};
    out.tipo = tipoPartida;
    out.jug = [Math.floor(J.jug.x / T) - O, Math.floor(J.jug.y / T) - O].join(',');
    out.agujero = (() => { let c = 0; for (let y = 300; y < 306; y++) for (let x = 340; x < 346; x++) if (!M.muro[idx(x + O, y + O)]) c++; return c; })();
    const cofre = objetoEn(342 + O, 303 + O);
    out.cofre = cofre ? cofre.datos.inv.filter(Boolean).map((s) => `${s.id}×${s.n}`).join(' ') : '';
    out.mesa = (objetoEn(344 + O, 303 + O) || {}).tipo;
    const mudanza = [];
    for (let y = CENTRO - 9; y <= CENTRO + 9; y++) for (let x = CENTRO - 9; x <= CENTRO + 9; x++) { const o = objetoEn(x, y); if (o && o.tipo === 'cofre') mudanza.push(o.datos.inv.filter(Boolean).map((s) => `${s.id}×${s.n}`).join(' ')); }
    out.mudanza = mudanza.join(' | ');
    out.muralla = (() => { let c = 0; for (let y = CENTRO - 160; y < CENTRO + 160; y++) for (let x = CENTRO - 160; x < CENTRO + 160; x++) if (M.muro[idx(x, y)] === MURO.MURALLA) c++; return c; })();
    out.objetivo = J.objetivo;
    return out;
  });
  const tipo4 = await p.evaluate(() => tipoPartida);
  await p.evaluate(() => empezar(true));
  const r4 = Object.assign(await comprobar4(), { tipo: tipo4 });
  console.log('v4', JSON.stringify(r4));
  const ok4 = r4.tipo === 'v4' && r4.jug === '343,304' && r4.agujero === 36 && r4.cofre === 'lingote_carmesi×21' && r4.mesa === 'mesa_carmesi' && r4.mudanza.includes('gema_antigua×5') && r4.mudanza.includes('yunque_carmesi×1') && r4.muralla === 0 && r4.objetivo >= 16;
  console.log(ok4 ? 'migración v4: bien' : 'migración v4: MAL');
  await p.screenshot({ path: path.join(salida, 'migracion-v4.png') });

  // ---- y ya en v5: guardar, recargar y seguir igual
  await p.evaluate(() => guardarPartida());
  await p.reload(); await listo();
  const tipo5 = await p.evaluate(() => tipoPartida);
  await p.evaluate(() => empezar(true));
  const r5 = Object.assign(await comprobar4(), { tipo: tipo5 });
  console.log('v5', JSON.stringify(r5));
  const ok5 = r5.tipo === 'v5' && r5.jug === r4.jug && r5.agujero === 36 && r5.cofre === r4.cofre && r5.mesa === r4.mesa && r5.mudanza === r4.mudanza && r5.muralla === 0;
  console.log(ok5 ? 'v5 tras recargar: bien' : 'v5 tras recargar: MAL');
  ok = ok && ok4 && ok5;
  console.log(ok ? 'migración: bien' : 'migración: MAL');
  console.log(errs.length ? 'ERRORES:\n' + [...new Set(errs)].join('\n') : 'sin errores');
  await b.close();
  process.exitCode = ok && !errs.length ? 0 : 1;
})();
