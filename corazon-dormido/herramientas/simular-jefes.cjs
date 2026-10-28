// Un bot pelea contra cada guardián con el equipo que tocaría a esa altura del juego (sin modo dios) y cuenta
// cuánto tarda, cuánta comida gasta y si gana. Simula sin dibujar (paso fijo de 1/30 s), así va rápido.
//   node corazon-dormido/herramientas/simular-jefes.cjs [intentos] [topo|reina|ozmar]
const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const intentos = Number(process.argv[2] || 3);
  const b = await chromium.launch();
  const p = await (await b.newContext({ viewport: { width: 870, height: 412 } })).newPage();
  const errs = [];
  p.on('pageerror', (e) => errs.push(e.message + '\n' + e.stack));
  await p.goto('file://' + path.resolve(__dirname, '..', 'index.html'));
  await p.waitForTimeout(500);
  const EQUIPO = {
    topo: { arma: 'espada_cobre', casco: 'casco_cobre', peto: 'peto_cobre', botas: 'botas_cobre', comida: ['baya', 'hongo'] },
    reina: { arma: 'espada_estano', casco: 'casco_estano', peto: 'peto_estano', botas: 'botas_estano', comida: ['baya', 'carne_larva'] },
    ozmar: { arma: 'espada_hierro', casco: 'casco_hierro', peto: 'peto_hierro', botas: 'botas_hierro', comida: ['pez_guppy', 'bulbo'] },
  };
  for (const [id, eq] of Object.entries(EQUIPO)) for (let k = 0; k < intentos && (!process.argv[3] || process.argv[3] === id); k++) {
    const r = await p.evaluate(([id, eq]) => {
      localStorage.clear(); empezar(false);
      cancelAnimationFrame(0);
      J.inv = new Array(N_INV).fill(null);
      J.inv[0] = { id: eq.arma, n: 1 };
      const plato = registrarPlato(...eq.comida.slice().sort());
      J.inv[1] = { id: plato, n: 10 };
      J.equipo = { casco: { id: eq.casco, n: 1 }, peto: { id: eq.peto, n: 1 }, botas: { id: eq.botas, n: 1 }, acc1: null, acc2: null };
      J.sel = 0; calcularStats();
      const g = M.guaridas[id], p = J.jug;
      p.x = g.x * T + 8; p.y = (id === 'ozmar' ? g.y0 + g.h - 3 : g.y + 6) * T + 8; p.vida = J.st.vidaMax;
      const j = J.jefes[id], dt = 1 / 30, causas = {};
      const danarReal = window.__danar || (window.__danar = danarJugador);
      window.danarJugador = (d, desde, sin) => {
        const v0 = J.jug.vida; danarReal(d, desde, sin);
        const c = !desde ? (sin ? 'charco' : 'otro') : desde.jefe ? 'contacto:' + desde.estado : desde.tipo && CRIATURAS[desde.tipo] ? 'criatura:' + desde.tipo : desde.r !== undefined ? 'aviso' : 'proyectil';
        causas[c] = (causas[c] || 0) + Math.round(v0 - J.jug.vida);
      };
      let t = 0, comidas = 0, golpes = 0, vidaMin = p.vida, danoRecibido = 0, vidaAntes = p.vida;
      const libre = (x, y) => !solido(Math.floor(x / T), Math.floor(y / T), true);
      while (t < 300 && j.estado !== 'muerto' && J.estado === 'juego') {
        // ---- el bot: esquiva avisos y proyectiles, se acerca y pega; come si va mal
        let mx = 0, my = 0, esquiva = false;
        for (const a of J.avisosSuelo) { const d = Math.hypot(p.x - a.x, p.y - a.y); if (d < a.r + 10) { mx += (p.x - a.x) / (d || 1); my += (p.y - a.y) / (d || 1); esquiva = true; } }
        for (const c of J.charcos) { const d = Math.hypot(p.x - c.x, p.y - c.y); if (d < 16) { mx += (p.x - c.x) / (d || 1); my += (p.y - c.y) / (d || 1); esquiva = true; } }
        for (const pr of J.proyectiles) {
          const dx = p.x - pr.x, dy = p.y - pr.y, d = Math.hypot(dx, dy), v = Math.hypot(pr.vx || 0, pr.vy || 0);
          if (d < 50 && v > 1 && (dx * pr.vx + dy * pr.vy) / (d * v) > 0.8) { mx += -pr.vy / v; my += pr.vx / v; esquiva = true; }
        }
        const cerca = J.enemigos.filter((e) => Math.hypot(e.x - p.x, e.y - p.y) < 40).sort((a, b) => Math.hypot(a.x - p.x, a.y - p.y) - Math.hypot(b.x - p.x, b.y - p.y))[0];
        const blanco = cerca || (!j.enterrado && !j.invisible && j.estado !== 'dormido' ? j : null) || j;
        if (p.vida < J.st.vidaMax * 0.45 && J.inv[1] && p.golpeT <= 0) { const antes = J.cuenta(plato); J.sel = 1; usar(null); J.sel = 0; if (J.cuenta(plato) < antes) comidas++; }
        if (!esquiva) {
          const dx = blanco.x - p.x, dy = blanco.y - p.y, d = Math.hypot(dx, dy), alcance = (blanco.radio || 6) + 14;
          if (d > alcance) { mx = dx / d; my = dy / d; }
          else if (p.golpeT <= 0) { mirarA(p, blanco.x, blanco.y); usar(null); golpes++; }
          // el embiste del topo y los pilares: no te quedes delante
          if (j.estado === 'embiste' || j.estado === 'carga' || j.estado === 'prepara') { mx = -dy / (d || 1); my = dx / (d || 1); }
        }
        const m = Math.hypot(mx, my); if (m > 0) { mx /= m; my /= m; }
        if (!libre(p.x + mx * 6, p.y + my * 6)) { const a = Math.atan2(my, mx) + 1.2; mx = Math.cos(a); my = Math.sin(a); }
        J.entrada.mx = mx; J.entrada.my = my; J.tirando = false;
        // ---- un paso del mundo (lo mismo que hace el bucle del juego)
        if (J.pausaGolpe > 0) J.pausaGolpe -= dt;
        else { actualizarJugador(dt); actualizarCriaturas(dt); actualizarJefes(dt); actualizarProyectiles(dt); actualizarDisparos(dt); }
        actualizarMundoVivo(dt);
        if (p.vida < vidaAntes) danoRecibido += vidaAntes - p.vida;
        vidaAntes = p.vida; vidaMin = Math.min(vidaMin, p.vida); t += dt;
      }
      const res = { gana: j.estado === 'muerto', t: Math.round(t), comidas, golpes, danoRecibido: Math.round(danoRecibido), vidaMin: Math.round(vidaMin), vidaJefe: Math.round(Math.max(0, j.vida)), fase: j.fase, muerto: J.estado === 'muerto', causas };
      J.estado = 'juego'; J.panel = null;
      return res;
    }, [id, eq]);
    console.log(id.padEnd(6), JSON.stringify(r));
  }
  console.log(errs.length ? 'ERRORES:\n' + [...new Set(errs)].join('\n') : 'sin errores');
  await b.close();
})();
