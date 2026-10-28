'use strict';
// Criaturas: aparecen en lo oscuro según la región, siguen un campo de distancias para rodear paredes y cada
// conducta tiene su ataque con aviso (parpadeo o círculo en el suelo) y un hueco para contraatacar.

function nuevaCriatura(tipo, x, y) {
  const c = CRIATURAS[tipo];
  return {
    tipo, x, y, hw: Math.min(7, c.radio), hh: 3, radio: c.radio, vida: c.vida, vidaMax: c.vida, def: c.def || 0, t: Math.random(), estado: 'anda',
    z: 0, vz: 0, kbx: 0, kby: 0, flash: 0, mira: 1, colores: c.colores, alto: c.radio > 8 ? 12 : 6, toqueT: 0, cd: 1 + Math.random() * 2, aturdido: 0,
  };
}

// Campo de distancias desde el jugador: las criaturas bajan por él y rodean las paredes (y no cruzan puertas).
const FLUJO = { R: 22, x0: 0, y0: 0, W: 45, dist: new Int16Array(45 * 45), cola: new Int32Array(45 * 45), t: 0 };
function calcularFlujo() {
  const p = J.jug, R = FLUJO.R, W = FLUJO.W, px = Math.floor(p.x / T), py = Math.floor(p.y / T);
  FLUJO.x0 = px - R; FLUJO.y0 = py - R;
  const d = FLUJO.dist, q = FLUJO.cola;
  d.fill(-1);
  let a = 0, b = 0;
  d[R * W + R] = 0; q[b++] = R * W + R;
  while (a < b) {
    const li = q[a++], lx = li % W, ly = (li - lx) / W, v = d[li];
    for (let k = 0; k < 4; k++) {
      const nx = lx + (k === 0 ? 1 : k === 1 ? -1 : 0), ny = ly + (k === 2 ? 1 : k === 3 ? -1 : 0);
      if (nx < 0 || ny < 0 || nx >= W || ny >= W) continue;
      const ni = ny * W + nx;
      if (d[ni] >= 0 || solido(FLUJO.x0 + nx, FLUJO.y0 + ny)) continue;
      d[ni] = v + 1; q[b++] = ni;
    }
  }
}
function distFlujo(tx, ty) {
  const lx = tx - FLUJO.x0, ly = ty - FLUJO.y0;
  if (lx < 0 || ly < 0 || lx >= FLUJO.W || ly >= FLUJO.W) return -1;
  return FLUJO.dist[ly * FLUJO.W + lx];
}
function rumbo(e) {
  const p = J.jug, dx = p.x - e.x, dy = p.y - e.y, d = Math.hypot(dx, dy);
  const tx = Math.floor(e.x / T), ty = Math.floor(e.y / T), aqui = distFlujo(tx, ty);
  if (d < 26 || aqui === 1 || aqui === 0) return { x: dx / (d || 1), y: dy / (d || 1), d };
  let mejorD = aqui < 0 ? 9999 : aqui, bx = 0, by = 0;
  for (const [ox, oy] of [[1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [1, -1], [-1, 1], [-1, -1]]) {
    if (ox && oy && (solido(tx + ox, ty) || solido(tx, ty + oy))) continue;
    const v = distFlujo(tx + ox, ty + oy);
    if (v >= 0 && v < mejorD) { mejorD = v; bx = ox; by = oy; }
  }
  if (!bx && !by) return aqui < 0 ? null : { x: dx / (d || 1), y: dy / (d || 1), d };
  const gx = (tx + bx) * T + 8 - e.x, gy = (ty + by) * T + 9 - e.y, g = Math.hypot(gx, gy) || 1;
  return { x: gx / g, y: gy / g, d };
}
// ¿Ve al jugador en línea recta? (para disparar)
function veJugador(e) {
  const p = J.jug, n = Math.ceil(Math.hypot(p.x - e.x, p.y - e.y) / 6);
  for (let k = 1; k < n; k++) { const x = e.x + ((p.x - e.x) * k) / n, y = e.y + ((p.y - e.y) * k) / n; if (muroEn(Math.floor(x / T), Math.floor(y / T))) return false; }
  return true;
}

// ---------------------------------------------------------------- proyectiles y charcos de las criaturas

function lanzar(tipo, x, y, a, vel, dano, o = {}) { J.proyectiles.push({ tipo, x, y, vx: Math.cos(a) * vel, vy: Math.sin(a) * vel, dano, vida: o.vida || 2.5, z: o.z || 8, vz: o.vz || 0, busca: o.busca || 0, ...o }); }
// Glóbulo que vuela en arco hasta un punto y deja un charco de ácido.
function escupir(x, y, tx, ty, dano) {
  const t = 0.8, vx = (tx - x) / t, vy = (ty - y) / t;
  J.proyectiles.push({ tipo: 'acido', x, y, vx, vy, dano, vida: t, z: 10, vz: 120, g: 300, charco: true });
}
function actualizarProyectiles(dt) {
  const p = J.jug;
  for (const r of J.proyectiles) {
    if (r.busca && J.estado === 'juego') {
      const a = Math.atan2(p.y - 4 - r.y, p.x - r.x), v = Math.hypot(r.vx, r.vy), b = Math.atan2(r.vy, r.vx);
      const g = b + Math.atan2(Math.sin(a - b), Math.cos(a - b)) * Math.min(1, dt * r.busca);
      r.vx = Math.cos(g) * v; r.vy = Math.sin(g) * v;
    }
    r.x += r.vx * dt; r.y += r.vy * dt; r.vida -= dt;
    if (r.g) { r.vz -= r.g * dt; r.z = Math.max(0, r.z + r.vz * dt); }
    if (r.charco) {
      if (r.vida <= 0) { J.charcos.push({ x: r.x, y: r.y, t: 4.5, dano: r.dano }); particulas(r.x, r.y, 8, ['#9ae04a', '#cfff8a', '#4a8a2a'], { vel: 40 }); }
      continue;
    }
    if (r.tipo !== 'pilar' && solido(Math.floor(r.x / T), Math.floor(r.y / T))) { r.vida = 0; particulas(r.x, r.y, 4, r.tipo === 'orbe' ? ['#c080ff', '#40204a'] : ['#8a7a6a', '#5e5046'], { vel: 40 }); }
    else if (r.tipo !== 'pilar' && Math.hypot(r.x - p.x, r.y - (p.y - 6)) < 8) { r.vida = 0; danarJugador(r.dano, { x: r.x - r.vx, y: r.y - r.vy }); }
  }
  J.proyectiles = J.proyectiles.filter((r) => r.vida > 0);
  // charcos de ácido y golpes con aviso (círculos en el suelo que estallan)
  for (const c of J.charcos) {
    c.t -= dt; c.tic = (c.tic || 0) - dt;
    if (c.tic <= 0 && Math.hypot(p.x - c.x, p.y - c.y) < 11) { c.tic = 0.5; const st = J.st || calcularStats(); danarJugador(Math.max(1, Math.round(c.dano * 0.35 * (60 / (60 + st.def * 4)))), null, true); Sonido.sfx.dano(); }
  }
  J.charcos = J.charcos.filter((c) => c.t > 0);
  for (const a of J.avisosSuelo) {
    a.t -= dt;
    if (a.t <= 0 && !a.hecho) {
      a.hecho = true;
      if (Math.hypot(p.x - a.x, p.y - a.y) < a.r) danarJugador(a.dano, a);
      particulas(a.x, a.y, 14, a.colores || ['#8a7a6a', '#5e5046', '#cfc6b8'], { vel: 70, s: 2 });
      efecto('humo', 4, a.x, a.y + 2, 0, 0.45);
      Sonido.sfx.excavar(); J.temblor = Math.max(J.temblor, 0.15);
    }
  }
  J.avisosSuelo = J.avisosSuelo.filter((a) => a.t > -0.1);
}
// Un golpe que avisa: círculo rojo en el suelo durante «t» segundos y luego daño en el radio.
function avisoSuelo(x, y, r, t, dano, colores) { J.avisosSuelo.push({ x, y, r, t, t0: t, dano, colores }); }

// ---------------------------------------------------------------- conductas

function morirCriatura(e) {
  const c = CRIATURAS[e.tipo];
  particulas(e.x, e.y - 4, 14, e.colores || ['#ffffff'], { vel: 70, vida: 0.6, s: 2 });
  efecto('humo', 4, e.x, e.y + 1, 0, 0.5);
  soltarLista(e.x, e.y, c.botin);
  Sonido.sfx.muere();
  if (c.divide) for (let k = 0; k < 2; k++) { const h = nuevaCriatura(c.divide, e.x + (k ? 6 : -6), e.y); if (!choca(h.x, h.y, h.hw, h.hh)) J.enemigos.push(h); }
}

function actualizarCriaturas(dt) {
  const p = J.jug;
  FLUJO.t -= dt;
  if (FLUJO.t <= 0) { FLUJO.t = 0.35; calcularFlujo(); }
  for (const e of J.enemigos) {
    const c = CRIATURAS[e.tipo];
    e.t += dt; e.flash -= dt; e.cd -= dt;
    if (e.veneno) { e.veneno.t -= dt; e.venT = (e.venT || 0) - dt; if (e.venT <= 0) { e.venT = 0.5; e.vida -= e.veneno.dano; numero(e.x, e.y, e.veneno.dano, '#9aff6a', 14); if (e.vida <= 0) morirCriatura(e); } if (e.veneno.t <= 0) e.veneno = null; }
    mover(e, e.kbx * dt, e.kby * dt);
    e.kbx *= Math.pow(0.001, dt); e.kby *= Math.pow(0.001, dt);
    if (e.aturdido > 0) { e.aturdido -= dt; continue; }
    const lejos = Math.hypot(p.x - e.x, p.y - e.y), r = lejos < 240 ? rumbo(e) : null;
    if (r) e.mira = r.x >= 0 ? 1 : -1;
    const vel = c.vel;
    switch (c.conducta) {
      case 'salta':
        if (e.z > 0 || e.vz > 0) {
          e.vz -= 420 * dt; e.z += e.vz * dt;
          if (e.saltoX !== undefined) mover(e, e.saltoX * dt, e.saltoY * dt);
          if (e.z <= 0) { e.z = 0; e.vz = 0; e.t = 0; e.aplasta = 0.15; }
        } else if (e.t > (e.tipo === 'babosa_roja' ? 0.55 : 0.7) && r) { e.vz = 110; e.z = 0.01; e.saltoX = r.x * vel * 1.6; e.saltoY = r.y * vel * 1.6; }
        e.aplasta = (e.aplasta || 0) - dt;
        break;
      case 'seton':
        if (e.estado === 'prepara') { if (e.t > 0.6) { e.estado = 'anda'; e.t = 0; e.cd = 4; avisoSuelo(e.x, e.y, 26, 0.01, c.dano, ['#c8d070', '#9aa040', '#f0f0b0']); } }
        else if (r) { if (lejos < 34 && e.cd <= 0) { e.estado = 'prepara'; e.t = 0; } else mover(e, r.x * vel * dt, r.y * vel * dt); }
        break;
      case 'escupe':
        if (e.estado === 'prepara') { if (e.t > 0.5) { e.estado = 'anda'; e.cd = 3; escupir(e.x, e.y - 6, p.x, p.y, c.dano); Sonido.sfx.zas(); } }
        else if (r) {
          if (lejos < 110 && lejos > 30 && e.cd <= 0 && veJugador(e)) { e.estado = 'prepara'; e.t = 0; }
          else { const ond = 0.75 + 0.25 * Math.sin(e.t * 6); mover(e, r.x * vel * ond * dt, r.y * vel * ond * dt); }
        }
        break;
      case 'lancea':
        if (e.estado === 'prepara') { if (e.t > 0.45) { e.estado = 'carga'; e.t = 0; } }
        else if (e.estado === 'carga') { if (mover(e, e.cx * 230 * dt, e.cy * 230 * dt) || e.t > 0.24) { e.estado = 'descansa'; e.t = 0; } }
        else if (e.estado === 'descansa') { if (e.t > 0.8) e.estado = 'anda'; }
        else if (r) {
          if (lejos < 44 && e.cd <= 0) { e.estado = 'prepara'; e.t = 0; e.cd = 2.2; const a = Math.atan2(p.y - e.y, p.x - e.x); e.cx = Math.cos(a); e.cy = Math.sin(a); }
          else mover(e, r.x * vel * dt, r.y * vel * dt);
        }
        break;
      case 'chaman':
        if (e.estado === 'conjura') {
          if (e.t > 0.55) { e.estado = 'anda'; e.cd = 2.4; const a = Math.atan2(p.y - 4 - e.y, p.x - e.x); lanzar('orbe', e.x, e.y - 8, a, 72, c.dano, { busca: 1.4, vida: 3.2 }); Sonido.sfx.metal(); }
        } else if (r) {
          if (lejos < 36 && (e.tele || 0) <= J.t) { // se teletransporta lejos
            e.tele = J.t + 5;
            efecto('humo', 4, e.x, e.y, 0, 0.4);
            for (let k = 0; k < 12; k++) { const a = Math.random() * Math.PI * 2, d = 70 + Math.random() * 30, nx = p.x + Math.cos(a) * d, ny = p.y + Math.sin(a) * d; if (!choca(nx, ny, e.hw, e.hh)) { e.x = nx; e.y = ny; break; } }
            efecto('humo', 4, e.x, e.y, 0, 0.4);
          } else if (lejos < 120 && e.cd <= 0 && veJugador(e)) { e.estado = 'conjura'; e.t = 0; }
          else if (lejos > 80) mover(e, r.x * vel * dt, r.y * vel * dt);
          else if (lejos < 60) mover(e, -r.x * vel * 0.7 * dt, -r.y * vel * 0.7 * dt);
        }
        break;
      case 'bruto':
        if (e.estado === 'prepara') { if (e.t > 0.8) { e.estado = 'descansa'; e.t = 0; avisoSuelo(e.x + e.cx * 14, e.y + e.cy * 10, 26, 0.01, c.dano); Sonido.sfx.temblor(); } }
        else if (e.estado === 'descansa') { if (e.t > 1.0) e.estado = 'anda'; }
        else if (r) {
          if (lejos < 36 && e.cd <= 0) { e.estado = 'prepara'; e.t = 0; e.cd = 2.6; const a = Math.atan2(p.y - e.y, p.x - e.x); e.cx = Math.cos(a); e.cy = Math.sin(a); }
          else mover(e, r.x * vel * dt, r.y * vel * dt);
        }
        break;
      default: // repta
        if (r) { const ond = 0.75 + 0.25 * Math.sin(e.t * 8); mover(e, r.x * vel * ond * dt, r.y * vel * ond * dt); }
    }
    // separarse de las demás
    for (const o of J.enemigos) {
      if (o === e) continue;
      const dx = e.x - o.x, dy = e.y - o.y, d = Math.hypot(dx, dy), min = e.radio + o.radio;
      if (d < min && d > 0.01) mover(e, (dx / d) * (min - d) * 0.3, (dy / d) * (min - d) * 0.3);
    }
    if (e.z < 4 && e.estado !== 'prepara' && Math.abs(p.x - e.x) < e.radio + p.hw && Math.abs(p.y - e.y) < 7) danarJugador(e.estado === 'carga' ? c.dano * 1.5 : c.dano, e);
  }
  J.enemigos = J.enemigos.filter((e) => e.vida > 0 && Math.hypot(e.x - p.x, e.y - p.y) < 36 * T);
  aparecerCriaturas(dt);
}

// Aparecen en lo oscuro, lejos de la luz, del santuario y de las guaridas.
function aparecerCriaturas(dt) {
  const p = J.jug;
  J.aparecer -= dt;
  if (J.aparecer > 0) return;
  J.aparecer = 1.3;
  const progreso = Object.keys(J.jefesMuertos).length;
  const max = 6 + Math.min(6, Object.keys(J.hito).length >> 1) + progreso * 2;
  const cerca = J.enemigos.filter((e) => Math.hypot(e.x - p.x, e.y - p.y) < 22 * T).length;
  if (cerca >= max) return;
  for (let k = 0; k < 14; k++) {
    const a = Math.random() * Math.PI * 2, d = (9 + Math.random() * 7) * T;
    const tx = Math.floor((p.x + Math.cos(a) * d) / T), ty = Math.floor((p.y + Math.sin(a) * d) / T);
    if (!enMundo(tx, ty) || solido(tx, ty) || objetoEn(tx, ty)) continue;
    const i = idx(tx, ty), reg = M.region[i];
    if (reg === REGION.SANTUARIO || Math.hypot(tx - CENTRO, ty - CENTRO) < 14 || luzEn(tx, ty) > 0.12) continue;
    if (M.piso[i] === SUELO.MADERA || M.piso[i] === SUELO.ARADA) continue; // tu base es tuya
    if (Object.values(M.guaridas).some((g) => Math.hypot(tx - g.x, ty - g.y) < 13)) continue;
    if (distFlujo(tx, ty) < 0) continue;
    // elige criatura por la región
    const pesos = [];
    for (const [tipo, c] of Object.entries(CRIATURAS)) if (c.regiones[reg]) pesos.push([tipo, c.regiones[reg] * (tipo === 'babosa_roja' && reg === 1 && progreso === 0 ? 0.3 : 1)]);
    if (!pesos.length) continue;
    let s = Math.random() * pesos.reduce((a, b) => a + b[1], 0), tipo = pesos[0][0];
    for (const [t, w] of pesos) { s -= w; if (s <= 0) { tipo = t; break; } }
    if (M.piso[i] === SUELO.COLMENA) tipo = Math.random() < 0.8 ? 'larva' : 'larva_grande';
    J.enemigos.push(nuevaCriatura(tipo, tx * T + 8, ty * T + 10));
    break;
  }
}
