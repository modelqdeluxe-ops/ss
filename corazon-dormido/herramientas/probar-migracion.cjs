// Prueba que una partida de la fase 1 (mundo de 320 casillas, guardado v3) pasa bien al mundo de la fase 2:
// el personaje, la mochila, lo logrado, las estatuas y lo que había en los cofres y los muebles puestos.
//   node corazon-dormido/herramientas/probar-migracion.cjs <carpeta-capturas>
const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const salida = path.resolve(process.argv[2] || '.');
  const b = await chromium.launch();
  const p = await (await b.newContext({ viewport: { width: 870, height: 412 }, deviceScaleFactor: 2 })).newPage();
  const errs = [];
  p.on('pageerror', (e) => errs.push(e.message + '\n' + e.stack));
  await p.goto('file://' + path.resolve(__dirname, '..', 'index.html'));
  await p.waitForTimeout(600);
  // una partida v3 como las de la fase 1
  await p.evaluate(() => {
    localStorage.clear();
    localStorage.setItem('corazon-dormido-v3', JSON.stringify({
      v: 3, mundo: { v: 3, sem: 4242, objetos: [[150, 150, 'cofre', 0, { inv: [{ id: 'madera', n: 37 }, { id: 'lingote_hierro', n: 9 }, null] }], [152, 150, 'mesa_hierro', 0, null], [153, 150, 'horno', 0, null], [154, 150, 'cama', 0, null], [155, 151, 'antorcha', 0, null]] },
      jug: { x: 100, y: 100, vida: 80, hambre: 50 }, inv: [{ id: 'pico_hierro', n: 1 }, { id: 'espada_hierro', n: 1 }, { id: 'plato:baya:hongo', n: 3 }],
      sel: 0, equipo: { casco: { id: 'casco_hierro', n: 1 }, peto: null, botas: null, acc1: null, acc2: null }, hab: { mineria: { n: 12, xp: 3 } }, buffs: [], hito: { mesa: true, horno: true },
      objetivo: 13, jefesMuertos: { topo: true, reina: true, ozmar: true }, estatuas: { topo: true, reina: true, ozmar: true }, cama: { x: 154, y: 150 }, tiempo: 5000, despierto: true,
    }));
    irTitulo();
  });
  await p.waitForTimeout(400);
  await p.screenshot({ path: path.join(salida, 'migracion-titulo.png') });
  const r = await p.evaluate(() => {
    const out = { hayPartida };
    empezar(true);
    out.inv = J.inv.filter(Boolean).map((s) => `${s.id}×${s.n}`).join(' ');
    out.casco = J.equipo.casco && J.equipo.casco.id;
    out.mineria = nivel('mineria');
    out.estatuas = Object.values(M.estatuas).map((e) => objetoEn(e.x, e.y).extra).join(',');
    out.despierto = J.corazonDespierto;
    out.objetivo = J.objetivo;
    const cofres = [];
    for (let y = CENTRO - 9; y <= CENTRO + 9; y++) for (let x = CENTRO - 9; x <= CENTRO + 9; x++) { const o = objetoEn(x, y); if (o && o.tipo === 'cofre') cofres.push(o.datos.inv.filter(Boolean).map((s) => `${s.id}×${s.n}`).join(' ')); }
    out.cofres = cofres;
    out.v4 = !!localStorage.getItem('corazon-dormido-v4');
    return out;
  });
  console.log(JSON.stringify(r, null, 1));
  await p.waitForTimeout(1500);
  await p.screenshot({ path: path.join(salida, 'migracion-juego.png') });
  const ok = r.hayPartida && r.inv.includes('pico_hierro') && r.casco === 'casco_hierro' && r.mineria === 12 && r.estatuas === '1,1,1' && r.cofres.join(' ').includes('madera×37') && r.cofres.join(' ').includes('mesa_hierro×1') && r.v4;
  console.log(ok ? 'migración: bien' : 'migración: MAL');
  console.log(errs.length ? 'ERRORES:\n' + [...new Set(errs)].join('\n') : 'sin errores');
  await b.close();
  process.exitCode = ok && !errs.length ? 0 : 1;
})();
