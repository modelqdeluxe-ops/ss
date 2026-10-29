// La IA de las criaturas, medida: el jugador quieto (o picando, que hace ruido) 60 s en un sitio oscuro de cada
// región, y cuenta cuántas se enteran de él, cuántas lo persiguen y cuántas atacan a la vez, y cuántos golpes recibe.
//   node corazon-dormido/herramientas/simular-ia.cjs
// Lo bueno (0.7): 1–3 se enteran por minuto, como mucho 3 persiguiendo y 2 atacando a la vez.
const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const b = await chromium.launch(); const p = await b.newPage();
  const errs = []; p.on('pageerror', (e) => errs.push(e.message + ' ' + e.stack));
  await p.goto('file://' + path.resolve(__dirname, '..', 'index.html'));
  await p.waitForFunction(() => window.juegoListo, null, { timeout: 120000 });
  const r = await p.evaluate(async () => {
    localStorage.clear(); await empezar(false);
    let golpes = 0, ultimo = -9; window.danarJugador = () => { if (J.t - ultimo > 0.7) { golpes++; ultimo = J.t; } };
    const out = [];
    const prueba = async (nombre, reg, picando) => {
      // un sitio oscuro de la región
      let q = null;
      for (let k = 0; k < 200000 && !q; k++) { const x = Math.floor(Math.random() * N_MUNDO), y = Math.floor(Math.random() * N_MUNDO); if (M.region[idx(x, y)] === reg && !solido(x, y, true) && luzEn(x, y) < 0.1 && Math.hypot(x - CENTRO, y - CENTRO) > 30) q = [x, y]; }
      J.jug.x = q[0] * T + 8; J.jug.y = q[1] * T + 8; J.enemigos = []; J.aparecer = 0; golpes = 0; ultimo = -9;
      let maxAlerta = 0, maxAtacan = 0, sumaVivas = 0, n = 0, alertas = 0;
      const vistas = new Set();
      for (let s = 0; s < 60 * 60; s++) { // 60 s a 60 cuadros
        const dt = 1 / 60;
        J.t += dt; J.tiempoMundo = (J.tiempoMundo || 0) + dt;
        if (picando && s % 25 === 0) ruido(J.jug.x, J.jug.y, 4);
        actualizarCriaturas(dt); actualizarProyectiles(dt);
        const al = J.enemigos.filter((e) => e.alerta);
        for (const e of al) if (!vistas.has(e)) { vistas.add(e); alertas++; }
        maxAlerta = Math.max(maxAlerta, al.length);
        maxAtacan = Math.max(maxAtacan, J.enemigos.filter((e) => e.estado === 'prepara' || e.estado === 'carga' || e.estado === 'conjura').length);
        sumaVivas += J.enemigos.length; n++;
      }
      out.push(`${nombre}: vivas de media ${(sumaVivas / n).toFixed(1)}, se enteraron ${alertas}, persiguiendo a la vez máx ${maxAlerta}, atacando a la vez máx ${maxAtacan}, golpes recibidos ${golpes}`);
    };
    await prueba('Raíces, quieto', 1, false);
    await prueba('Raíces, picando', 1, true);
    await prueba('Ruinas, quieto', 3, false);
    await prueba('Ruinas, picando', 3, true);
    await prueba('Selva, picando', 4, true);
    await prueba('Ciénaga, picando', 8, true);
    await prueba('Ciudad, picando', 9, true);
    return out;
  });
  console.log(r.join('\n'));
  console.log(errs.length ? errs.join('\n') : 'sin errores');
  await b.close();
})();
