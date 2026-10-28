'use strict';
// Lo que hacen las criaturas de la Ciénaga y de la Ciudad Velada (0.7) y sus dos jefes. Como todas: cada ataque
// avisa antes (parpadeo o algo en el suelo) y deja un hueco para contraatacar.

// ---------------------------------------------------------------- criaturas

// Farolero: espera en lo oscuro con el cebo encendido; el cuerpo casi no se ve. Si te acercas, abre la boca y se lanza.
CONDUCTAS.farol = (e, c, r, lejos, dt) => {
  const p = J.jug;
  if (e.estado === 'prepara') { if (e.t > 0.55) { e.estado = 'carga'; e.t = 0; } return; }
  if (e.estado === 'carga') { if (mover(e, e.cx * 270 * dt, e.cy * 270 * dt) || e.t > 0.32) { e.estado = 'descansa'; e.t = 0; } return; }
  if (e.estado === 'descansa') { if (e.t > 1.2) e.estado = 'anda'; return; }
  if (!r) return;
  if (lejos < 72 && e.cd <= 0 && veJugador(e)) { e.estado = 'prepara'; e.t = 0; e.cd = 2.8; const a = Math.atan2(p.y - e.y, p.x - e.x); e.cx = Math.cos(a); e.cy = Math.sin(a); return; }
  if (lejos > 100) mover(e, r.x * c.vel * 0.5 * dt, r.y * c.vel * 0.5 * dt); // se acerca despacio, sin ruido
};

// Velado: flota hacia ti atravesando las paredes. A veces se desvanece y aparece a tu espalda.
CONDUCTAS.fantasma = (e, c, r, lejos, dt) => {
  const p = J.jug;
  e.z = 8 + Math.sin(e.t * 2.4) * 2;
  if (e.estado === 'prepara') { if (e.t > 0.6) { e.estado = 'carga'; e.t = 0; } return; }
  if (e.estado === 'carga') { e.x += e.cx * 190 * dt; e.y += e.cy * 190 * dt; if (e.t > 0.3) { e.estado = 'descansa'; e.t = 0; } return; }
  if (e.estado === 'descansa') { if (e.t > 0.9) e.estado = 'anda'; return; }
  if (e.estado === 'escondida') { // se va (alfa baja) y vuelve detrás de ti
    if (e.sub === 'va') { e.alfa = Math.max(0, 1 - e.t * 2.2); if (e.t > 0.6) { const a = Math.atan2(p.fy, p.fx) + Math.PI + (Math.random() - 0.5) * 0.8; e.x = p.x + Math.cos(a) * 42; e.y = p.y + Math.sin(a) * 30; e.sub = 'viene'; e.t = 0; } }
    else { e.alfa = Math.min(1, e.t * 2); if (e.t > 0.5) { e.alfa = 1; e.estado = 'prepara'; e.t = 0; const a = Math.atan2(p.y - e.y, p.x - e.x); e.cx = Math.cos(a); e.cy = Math.sin(a); Sonido.sfx.metal(); } }
    return;
  }
  if (lejos < 46 && e.cd <= 0) { e.estado = 'prepara'; e.t = 0; e.cd = 2.4; const a = Math.atan2(p.y - e.y, p.x - e.x); e.cx = Math.cos(a); e.cy = Math.sin(a); return; }
  if (lejos > 80 && lejos < 200 && (e.tele || 0) < J.t) { e.tele = J.t + 7 + Math.random() * 3; e.estado = 'escondida'; e.sub = 'va'; e.t = 0; return; }
  const dx = p.x - e.x, dy = p.y - e.y, d = Math.hypot(dx, dy) || 1, ond = Math.sin(e.t * 1.7);
  e.x += (dx / d - (dy / d) * ond * 0.5) * c.vel * dt; e.y += (dy / d + (dx / d) * ond * 0.5) * c.vel * dt;
};

// Guardián de piedra: es una estatua más hasta que te acercas (o le pegas). Entonces baja, lento, y golpea con aviso.
CONDUCTAS.estatua = (e, c, r, lejos, dt) => {
  const p = J.jug;
  if (!e.despierta) {
    e.estado = 'escondida'; e.kbx = e.kby = 0;
    if (lejos < 44 || e.golpeadoT) {
      e.despierta = true; e.estado = 'despierta'; e.t = 0; J.temblor = Math.max(J.temblor, 0.3); Sonido.sfx.temblor();
      particulas(e.x, e.y - 12, 12, ['#646c7a', '#3c424e', '#b4dcff'], { vel: 50 });
    }
    return;
  }
  if (e.estado === 'despierta') { if (e.t > 0.8) e.estado = 'anda'; return; }
  if (e.estado === 'prepara') { if (e.t > 0.85) { e.estado = 'descansa'; e.t = 0; Sonido.sfx.temblor(); J.temblor = Math.max(J.temblor, 0.25); } return; }
  if (e.estado === 'descansa') { if (e.t > 1.1) e.estado = 'anda'; return; }
  if (!r) return;
  if (lejos < 40 && e.cd <= 0) {
    e.estado = 'prepara'; e.t = 0; e.cd = 2.8;
    const a = Math.atan2(p.y - e.y, p.x - e.x); e.cx = Math.cos(a); e.cy = Math.sin(a);
    avisoSuelo(e.x + e.cx * 16, e.y + e.cy * 12, 26, 0.85, c.dano, ['#7ab2f0', '#2a5a9a', '#e8f6ff']);
  } else mover(e, r.x * c.vel * dt, r.y * c.vel * dt);
};

Object.assign(SPRITE_CRIATURA, {
  farolero: (e) => `farolero_${e.estado === 'prepara' ? 2 : e.estado === 'carga' ? 3 : Math.floor(e.t * 1.6) % 2}`,
  velado: (e) => `velado_${e.estado === 'prepara' ? 2 : e.estado === 'carga' ? 3 : Math.floor(e.t * 1.4) % 2}`,
  guardian_piedra: (e) => `guardian_piedra_${e.estado === 'escondida' ? 0 : e.estado === 'prepara' || e.estado === 'despierta' ? 2 : e.estado === 'descansa' ? 3 : 1}`,
  moscarda: (e) => `moscarda_${e.estado === 'prepara' || e.estado === 'carga' ? 2 : Math.floor(e.t * 16) % 2}`,
});

// ---------------------------------------------------------------- GRUL, el Sapo Hueco
// Salta y cae donde estás (círculo), saca la lengua en línea recta (fila de avisos), escupe Podre que deja charcos
// y, enfadado, aspira: te arrastra hacia la boca. Cada vez que cae suelta veneno; en la fase 2, también sapos.
const COL_PODRE = ['#ff3cb4', '#3a0f30', '#ffb0e0'];
CREAR_JEFE.grul = (j) => { j.z = 0; j.salto = null; j.paso = 0; j.colores = ['#3e4830', '#ff3cb4', '#525c3e']; };
ACTUALIZAR_JEFE.grul = (j, dt) => {
  const p = J.jug, D = JEFES.grul, rapido = j.fase === 2 ? 1.3 : 1, lejos = Math.hypot(p.x - j.x, p.y - j.y);
  const elegir = () => {
    const ops = ['salto', 'lengua', 'escupe'];
    if (j.fase === 2) ops.push('traga');
    if (lejos > 140) ops.push('salto', 'salto');
    const sig = ops.filter((x) => x !== j.ultimo);
    j.estado = sig[Math.floor(Math.random() * sig.length)]; j.ultimo = j.estado; j.t = 0.7 / rapido; j.paso = 0; j.rafagas = j.fase === 2 ? 3 : 2;
  };
  switch (j.estado) {
    case 'despierta': if (j.t <= 0) { j.estado = 'espera'; j.t = 1.2; } break;
    case 'espera': if (j.t <= 0) elegir(); break;
    case 'salto':
      if (j.paso === 0) {
        if (j.t > 0) break;
        let tx = p.x, ty = p.y;
        if (solido(Math.floor(tx / T), Math.floor(ty / T))) { tx = (tx + j.x) / 2; ty = (ty + j.y) / 2; }
        if (solido(Math.floor(tx / T), Math.floor(ty / T))) { j.estado = 'espera'; j.t = 0.4; break; }
        const dur = 0.95 / rapido;
        avisoSuelo(tx, ty, 46, dur, D.dano * 1.3, COL_PODRE);
        j.salto = { x0: j.x, y0: j.y, x1: tx, y1: ty, t: 0, dur }; j.paso = 1; Sonido.sfx.zas();
      } else {
        const s = j.salto; s.t += dt;
        const k = Math.min(1, s.t / s.dur);
        j.x = s.x0 + (s.x1 - s.x0) * k; j.y = s.y0 + (s.y1 - s.y0) * k; j.z = Math.sin(k * Math.PI) * 46;
        if (k >= 1) {
          j.z = 0; j.salto = null; J.temblor = Math.max(J.temblor, 0.8); Sonido.sfx.temblor();
          for (let n = 0; n < 6; n++) { const a = (n / 6) * Math.PI * 2, d = 40 + Math.random() * 16; J.charcos.push({ x: j.x + Math.cos(a) * d, y: j.y + Math.sin(a) * d * 0.8, t: 4, dano: D.dano * 0.5, tipo: 'veneno' }); }
          particulas(j.x, j.y, 26, ['#3e4830', '#ff3cb4', '#2a1a10'], { vel: 90, s: 2 });
          if (j.fase === 2 && J.enemigos.filter((e) => e.tipo === 'sapo_hueco').length < 4) { const e = nuevaCriatura('sapo_hueco', j.x + (Math.random() - 0.5) * 50, j.y + 20); if (!choca(e.x, e.y, e.hw, e.hh)) J.enemigos.push(e); }
          j.rafagas--; j.paso = 0; j.t = 0.5 / rapido;
          if (j.rafagas <= 0 || lejos < 60) { j.estado = 'espera'; j.t = 1.2 / rapido; }
        }
      }
      break;
    case 'lengua': // una fila de avisos hacia ti: la lengua sale por ahí
      if (j.paso === 0 && j.t <= 0) {
        const a = Math.atan2(p.y - j.y, p.x - j.x);
        for (let k = 1; k <= 10; k++) avisoSuelo(j.x + Math.cos(a) * (18 + k * 13), j.y + Math.sin(a) * (14 + k * 11), 12, 0.7 / rapido + k * 0.02, D.dano, COL_PODRE);
        j.paso = 1; j.t = 1.2 / rapido; Sonido.sfx.rugido();
      } else if (j.paso === 1 && j.t <= 0) { j.estado = 'espera'; j.t = 1.1 / rapido; }
      break;
    case 'escupe':
      if (j.t <= 0) {
        const n = j.fase === 2 ? 5 : 3;
        for (let k = 0; k < n; k++) { const a = Math.random() * Math.PI * 2, d = Math.random() * 40; escupir(j.x, j.y - 34, p.x + Math.cos(a) * d, p.y + Math.sin(a) * d * 0.8, D.dano * 0.8, 'veneno'); }
        Sonido.sfx.zas(); j.rafagas--; j.t = 0.85 / rapido;
        if (j.rafagas <= 0) { j.estado = 'espera'; j.t = 1.3 / rapido; }
      }
      break;
    case 'traga': // aspira: te arrastra hacia la boca; si llegas, muerde fuerte
      if (j.paso === 0) { j.paso = 1; j.t = 2.4; aviso('¡Grul aspira!', null, '#ff3cb4'); Sonido.sfx.rugido(); }
      else {
        const bx = j.x, by = j.y + 12, dx = bx - p.x, dy = by - p.y, d = Math.hypot(dx, dy) || 1;
        if (d > 18) mover(p, (dx / d) * 64 * dt, (dy / d) * 64 * dt);
        if (Math.random() < 0.6) J.particulas.push({ x: p.x + (Math.random() - 0.5) * 60, y: p.y + (Math.random() - 0.5) * 40, z: 6, vx: dx / d * 120, vy: dy / d * 120, vz: 0, g: 0, vida: 0.5, c: '#d8e8c0', s: 1 });
        if (j.t <= 0) { if (d < 40) { danarJugador(D.dano * 1.6, j); J.temblor = 0.5; } j.estado = 'espera'; j.t = 1.4; }
      }
      break;
  }
  if (j.z < 8) contacto(j, D.dano * 0.6);
};
DIBUJAR_JEFE.grul = (j) => {
  const x = j.x / T, z = j.y / T + 0.1, abierta = j.estado === 'lengua' || j.estado === 'escupe' || j.estado === 'traga';
  const f = j.salto ? (j.z > 3 ? 3 : 2) : j.estado === 'salto' ? 2 : abierta ? 1 : 0;
  const respira = j.estado === 'dormido' || j.estado === 'espera' ? Math.sin(J.t * 1.6) * 0.03 : 0, n = `grul_${f}`, dx = dxDe(n);
  GL.plano('sombra_l', x, z - 0.1, { tinte: [1, 1, 1, Math.max(0.3, 0.85 - j.z / 90)], escala: 2.6 });
  GL.sprite(n, x + dx, z, { y: (j.z / 16) * GL.KY + respira, unlit: 0.3, blanco: j.flash > 0 ? 0.85 : 0 });
  if (SPR[n + '_luz']) GL.sprite(n + '_luz', x + dx, z + 0.002, { y: (j.z / 16) * GL.KY + respira, unlit: 1, lote: GL.lotes.brillo, tinte: [1, 1, 1, 0.7 + 0.2 * Math.sin(J.t * 3)] });
  GL.halo(x, 1.6, z, 2.2, [1, 0.24, 0.7], 0.12 + 0.05 * Math.sin(J.t * 2));
};

// ---------------------------------------------------------------- EL CENTINELA, el Que No Duerme
// Una estatua de Velador con espada y lámpara. Tajo en abanico (tres círculos delante; después se queda expuesto),
// runas alrededor de ti, carga en línea recta (si choca con la pared, se aturde) y, en la fase 2, llama a los Velados.
const COL_RUNA = ['#7ab2f0', '#2a5a9a', '#e8f6ff'];
CREAR_JEFE.centinela = (j) => { j.paso = 0; j.colores = ['#3c424e', '#b4dcff', '#646c7a']; };
ACTUALIZAR_JEFE.centinela = (j, dt) => {
  const p = J.jug, D = JEFES.centinela, rapido = j.fase === 2 ? 1.35 : 1, a = Math.atan2(p.y - j.y, p.x - j.x), lejos = Math.hypot(p.x - j.x, p.y - j.y);
  const elegir = () => {
    const ops = ['tajo', 'runas', 'camina'];
    if (lejos > 60) ops.push('carga');
    if (j.fase === 2 && J.enemigos.filter((e) => e.tipo === 'velado').length < 3) ops.push('llamada');
    const sig = ops.filter((x) => x !== j.ultimo);
    j.estado = sig[Math.floor(Math.random() * sig.length)]; j.ultimo = j.estado; j.t = 0.5 / rapido; j.paso = 0;
  };
  if (j.fase === 2 && Math.random() < dt * 6) J.particulas.push({ x: j.x + (Math.random() - 0.5) * 10, y: j.y - 2, z: 66 + Math.random() * 6, vx: 0, vy: 0, vz: 14, g: 0, vida: 0.8, c: Math.random() < 0.5 ? '#b4dcff' : '#7ab2f0', s: 1, brilla: true, chispa: true });
  switch (j.estado) {
    case 'despierta': if (j.t <= 0) { j.estado = 'espera'; j.t = 1; } break;
    case 'espera': if (j.t <= 0) elegir(); break;
    case 'camina':
      mover(j, Math.cos(a) * 30 * rapido * dt, Math.sin(a) * 30 * rapido * dt);
      j.pasoT = (j.pasoT || 0) - dt; if (j.pasoT <= 0) { j.pasoT = 0.55; J.temblor = Math.max(J.temblor, 0.12); Sonido.sfx.excavar(); }
      if (j.t <= -1.6 || lejos < 40) elegir();
      break;
    case 'tajo':
      if (j.paso === 0 && j.t <= 0) {
        for (const da of [-0.55, 0, 0.55]) avisoSuelo(j.x + Math.cos(a + da) * 36, j.y + Math.sin(a + da) * 28, 24, 0.85 / rapido, D.dano * 1.2, COL_RUNA);
        j.paso = 1; j.t = 0.85 / rapido; Sonido.sfx.zas();
      } else if (j.paso === 1 && j.t <= 0) { j.estado = 'expuesto'; j.t = 1.1; J.temblor = Math.max(J.temblor, 0.3); Sonido.sfx.temblor(); }
      break;
    case 'expuesto': if (j.t <= 0) { j.estado = 'espera'; j.t = 0.5; } break;
    case 'runas':
      if (j.paso === 0 && j.t <= 0) {
        const n = j.fase === 2 ? 9 : 6;
        for (let k = 0; k < n; k++) { const aa = (k / n) * Math.PI * 2 + Math.random() * 0.5, d = k === 0 ? 0 : 26 + Math.random() * 40; avisoSuelo(p.x + Math.cos(aa) * d, p.y + Math.sin(aa) * d * 0.8, 18, 1.0 / rapido + k * 0.06, D.dano, COL_RUNA, 'runa'); }
        j.paso = 1; j.t = 1.4 / rapido; Sonido.sfx.metal();
      } else if (j.paso === 1 && j.t <= 0) { j.estado = 'espera'; j.t = 0.8 / rapido; }
      break;
    case 'carga':
      if (j.paso === 0 && j.t <= 0) {
        j.cx = Math.cos(a); j.cy = Math.sin(a);
        for (let k = 1; k <= 9; k++) avisoSuelo(j.x + j.cx * k * 14, j.y + j.cy * k * 11, 14, 0.8 / rapido, D.dano * 0.4, COL_RUNA);
        j.paso = 1; j.t = 0.8 / rapido; Sonido.sfx.rugido();
      } else if (j.paso === 1 && j.t <= 0) { j.paso = 2; j.t = 0.55; }
      else if (j.paso === 2) {
        if (mover(j, j.cx * 250 * dt, j.cy * 250 * dt)) { j.estado = 'expuesto'; j.t = 1.8; J.temblor = 0.7; Sonido.sfx.temblor(); particulas(j.x, j.y - 10, 20, ['#646c7a', '#3c424e', '#b4dcff'], { vel: 80 }); }
        else if (j.t <= 0) { j.estado = 'espera'; j.t = 0.6; }
        if (Math.abs(p.x - j.x) < j.hw + p.hw + 4 && Math.abs(p.y - j.y) < j.hh + 6) danarJugador(D.dano * 1.3, j);
      }
      break;
    case 'llamada':
      if (j.t <= 0) {
        for (let k = 0; k < 2; k++) { const aa = Math.random() * Math.PI * 2, e = nuevaCriatura('velado', j.x + Math.cos(aa) * 50, j.y + Math.sin(aa) * 40); J.enemigos.push(e); efecto('humo', 4, e.x, e.y, 0, 0.5); }
        Sonido.sfx.rugido(); j.estado = 'espera'; j.t = 1.4;
      }
      break;
  }
  contacto(j, D.dano * 0.6);
};
DIBUJAR_JEFE.centinela = (j) => {
  const x = j.x / T, z = j.y / T + 0.1;
  const f = j.estado === 'dormido' ? 0 : j.estado === 'tajo' || (j.estado === 'carga' && j.paso < 2) ? 1 : j.estado === 'runas' || j.estado === 'llamada' ? 3 : 2;
  const n = `centinela_${f}`, dx = dxDe(n), sacude = j.estado === 'muriendo' || j.estado === 'despierta' ? Math.sin(J.t * 50) * 0.05 : 0;
  GL.plano('sombra_l', x, z - 0.1, { tinte: [1, 1, 1, 0.85], escala: 2 });
  GL.sprite(n, x + dx + sacude, z, { unlit: 0.2, blanco: j.flash > 0 ? 0.85 : 0 });
  if (SPR[n + '_luz']) GL.sprite(n + '_luz', x + dx + sacude, z + 0.002, { unlit: 1, lote: GL.lotes.brillo, tinte: [1, 1, 1, j.estado === 'dormido' ? 0.3 : 0.75 + 0.2 * Math.sin(J.t * 4)] });
  if (j.estado !== 'dormido') GL.halo(x, 2.6, z, j.fase === 2 ? 2.6 : 1.8, [0.55, 0.72, 1], 0.14 + 0.05 * Math.sin(J.t * 3));
};
