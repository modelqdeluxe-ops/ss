'use strict';
// Criaturas: aparecen en lo oscuro según la región, siguen un campo de distancias para rodear paredes y cada
// conducta tiene su ataque con aviso (parpadeo o círculo en el suelo) y un hueco para contraatacar.
// No saben dónde estás hasta que te ven, te oyen o les pegas (percibir); mientras, deambulan por su sitio. Y no
// atacan todas a la vez: unas pocas tienen el turno y las demás te rodean esperando hueco (TURNOS).

function nuevaCriatura(tipo, x, y) {
  const c = CRIATURAS[tipo];
  return {
    tipo, x, y, hw: Math.min(7, c.radio), hh: 3, radio: c.radio, vida: c.vida, vidaMax: c.vida, def: c.def || 0, t: Math.random(), estado: 'anda',
    z: c.vuela ? 10 : 0, vz: 0, kbx: 0, kby: 0, flash: 0, mira: 1, colores: c.colores, alto: c.radio > 8 ? 12 : 6, toqueT: 0, cd: 1 + Math.random() * 2, aturdido: 0,
    // las que llama un jefe ya vienen a por ti
    casa: { x, y }, alerta: Object.values(J.jefes || {}).some((j) => j.activo && j.estado !== 'muerto'), turno: false,
  };
}
// Conductas nuevas (fauna.js las añade): f(e, c, r, lejos, dt). r: rumbo hacia el jugador o null.
const CONDUCTAS = {};

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

// ---------------------------------------------------------------- percepción, deambular y turnos
// Las de emboscada tienen su propio disparador (salen cuando pasas cerca); las de distancia atacan desde lejos.
const EMBOSCADA = new Set(['tentaculo', 'trampa', 'estatua']);
const A_DISTANCIA = new Set(['escupe', 'chaman', 'jardinero', 'cazador']);
// Hasta dónde ve (en píxeles): menos si estás a oscuras, más si estás en la luz. Tu antorcha te delata.
function vista(c) {
  const p = J.jug, l = luzEn(Math.floor(p.x / T), Math.floor(p.y / T));
  return (c.vista || 6.5) * T * (l > 0.5 ? 1.25 : l < 0.15 ? 0.7 : 1);
}
function alertar(e, grito = true) {
  if (e.alerta) return;
  e.alerta = true; e.vistoT = 0;
  if (EMBOSCADA.has(CRIATURAS[e.tipo].conducta)) return;
  if (grito) { numero(e.x, e.y, '!', '#ff5a40', 30); Sonido.sfx.alerta(); }
  // avisa a las que están a su lado
  for (const o of J.enemigos) if (o !== e && !o.alerta && Math.hypot(o.x - e.x, o.y - e.y) < 4 * T) alertar(o, false);
}
// Un ruido (picar, romper, pelear, una bomba) despierta a las que están cerca.
function ruido(x, y, casillas) {
  for (const e of J.enemigos) if (!e.alerta && Math.hypot(e.x - x, e.y - y) < casillas * T) alertar(e);
}
// true si sabe dónde estás
function percibir(e, c, lejos, dt) {
  if (e.golpeadoT && J.t - e.golpeadoT < 0.5) alertar(e);
  if (!e.alerta) {
    e.mirarT = (e.mirarT || Math.random() * 0.3) - dt;
    if (lejos < 1.7 * T) alertar(e);
    else if (e.mirarT <= 0) { e.mirarT = 0.3; if (lejos < vista(c) && veJugador(e)) alertar(e); }
    return e.alerta;
  }
  // te pierde si te alejas mucho o pasa un rato sin verte; entonces vuelve a su sitio
  const v = vista(c);
  e.mirarT = (e.mirarT || 0) - dt;
  if (e.mirarT <= 0) { e.mirarT = 0.3; if (lejos < v * 1.6 && veJugador(e)) e.vistoT = 0; }
  e.vistoT = (e.vistoT || 0) + dt;
  if (lejos > v * 2.6 || e.vistoT > 6) { e.alerta = false; e.turno = false; e.modo = e.fija !== undefined ? 'casa' : 'quieta'; e.paseoT = 3; }
  return e.alerta;
}
// Sin saber de ti: da vueltas, se para a ratos y, las que no guardan un sitio, te rastrean: siguen tu olor (el
// campo de distancias) despacio hasta quedarse rondando a unas casillas, donde te acaban viendo u oyendo.
function deambular(e, c, dt) {
  if (c.vuela) e.z = 10 + Math.sin(e.t * 3) * 2;
  else if (e.z > 0 || e.vz > 0) { e.vz -= 420 * dt; e.z = Math.max(0, e.z + e.vz * dt); if (!e.z) e.vz = 0; }
  if (e.estado !== 'escondida') e.estado = 'anda';
  const p = J.jug, lejos = Math.hypot(p.x - e.x, p.y - e.y), guarda = e.fija !== undefined;
  e.paseoT = (e.paseoT || 0) - dt;
  if (e.paseoT <= 0) {
    e.paseoT = 1.5 + Math.random() * 3;
    const s = Math.random(), vuelve = guarda && Math.hypot(e.x - e.casa.x, e.y - e.casa.y) > 6 * T;
    if (vuelve) e.modo = 'casa';
    else if (!guarda && lejos > 5 * T && s < 0.6) e.modo = 'rastrea';
    else if (s < 0.75) { e.modo = 'pasea'; const a = Math.random() * Math.PI * 2, d = (1 + Math.random() * 3) * T, o = guarda ? e.casa : e; e.paseo = { x: o.x + Math.cos(a) * d, y: o.y + Math.sin(a) * d }; }
    else e.modo = 'quieta';
  }
  let dx = 0, dy = 0;
  if (e.modo === 'rastrea') { const r = lejos > 4 * T ? rumbo(e) : null; if (r) { dx = r.x; dy = r.y; } else e.modo = 'quieta'; }
  else if (e.modo === 'pasea' || e.modo === 'casa') {
    const q = e.modo === 'casa' ? e.casa : e.paseo, ex = q.x - e.x, ey = q.y - e.y, d = Math.hypot(ex, ey);
    if (d < 3) e.modo = 'quieta'; else { dx = ex / d; dy = ey / d; }
  }
  e.quieta = !dx && !dy;
  if (e.quieta) return;
  e.mira = dx >= 0 ? 1 : -1;
  if (mover(e, dx * c.vel * 0.38 * dt, dy * c.vel * 0.38 * dt) && e.modo !== 'rastrea') e.modo = 'quieta';
}
// Sin turno: te rodea a media distancia, esperando hueco.
function rodear(e, c, r, lejos, dt) {
  if (!e.giro) e.giro = Math.random() < 0.5 ? 1 : -1;
  const radial = lejos < 4 * T ? -0.8 : lejos > 5 * T ? 0.7 : 0;
  if (mover(e, (-r.y * e.giro + r.x * radial) * c.vel * 0.45 * dt, (r.x * e.giro + r.y * radial) * c.vel * 0.45 * dt)) e.giro = -e.giro;
  e.mira = r.x >= 0 ? 1 : -1; e.cd = Math.max(e.cd, 0.4);
}
// Quién tiene el turno: las más cercanas (y las que ya están atacando), unas pocas cuerpo a cuerpo y dos de lejos.
const TURNOS = { t: 0 };
function repartirTurnos(dt) {
  TURNOS.t -= dt;
  if (TURNOS.t > 0) return;
  TURNOS.t = 0.4;
  const p = J.jug, atacando = (e) => e.estado === 'prepara' || e.estado === 'carga' || e.estado === 'conjura';
  const cola = J.enemigos.filter((e) => e.alerta && !EMBOSCADA.has(CRIATURAS[e.tipo].conducta))
    .map((e) => [e, Math.hypot(e.x - p.x, e.y - p.y) - (atacando(e) ? 1e4 : 0)]).sort((a, b) => a[1] - b[1]);
  const maxCuerpo = 2 + (Object.keys(J.jefesMuertos).length >= 3 ? 1 : 0);
  let cuerpo = 0, lejos = 0;
  for (const [e] of cola) e.turno = A_DISTANCIA.has(CRIATURAS[e.tipo].conducta) ? lejos++ < 2 : cuerpo++ < maxCuerpo;
}

// ---------------------------------------------------------------- proyectiles y charcos de las criaturas

function lanzar(tipo, x, y, a, vel, dano, o = {}) { J.proyectiles.push({ tipo, x, y, vx: Math.cos(a) * vel, vy: Math.sin(a) * vel, dano, vida: o.vida || 2.5, z: o.z || 8, vz: o.vz || 0, busca: o.busca || 0, ...o }); }
// Glóbulo que vuela en arco hasta un punto y deja un charco de ácido.
function escupir(x, y, tx, ty, dano, tipoCharco = 'acido') {
  const t = 0.8, vx = (tx - x) / t, vy = (ty - y) / t;
  J.proyectiles.push({ tipo: tipoCharco === 'veneno' ? 'semilla_veneno' : 'acido', x, y, vx, vy, dano, vida: t, z: 10, vz: 120, g: 300, charco: tipoCharco });
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
    if (r.charco || r.alCaer) {
      if (r.vida <= 0) {
        if (r.charco) { const ven = r.charco === 'veneno'; J.charcos.push({ x: r.x, y: r.y, t: ven ? 6 : 4.5, dano: r.dano, tipo: ven ? 'veneno' : undefined }); particulas(r.x, r.y, 8, ven ? ['#a060e0', '#e8c0ff', '#6a2aa0'] : ['#9ae04a', '#cfff8a', '#4a8a2a'], { vel: 40 }); }
        if (r.alCaer) r.alCaer(r);
      }
      continue;
    }
    if (r.tipo !== 'pilar' && !r.vuela && solido(Math.floor(r.x / T), Math.floor(r.y / T))) { r.vida = 0; particulas(r.x, r.y, 4, r.tipo === 'orbe' ? ['#c080ff', '#40204a'] : ['#8a7a6a', '#5e5046'], { vel: 40 }); }
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
// estilo: 'circulo' (anillo y disco que se llena), 'grieta' (raíces que van a salir) o 'runa' (magia de la Podre)
function avisoSuelo(x, y, r, t, dano, colores, estilo = 'circulo') { J.avisosSuelo.push({ x, y, r, t, t0: t, dano, colores, estilo, v: Math.floor(Math.random() * 3), giro: Math.random() < 0.5 }); }

// ---------------------------------------------------------------- conductas

function morirCriatura(e) {
  const c = CRIATURAS[e.tipo];
  particulas(e.x, e.y - 4, 14, e.colores || ['#ffffff'], { vel: 70, vida: 0.6, s: 2 });
  efecto('humo', 4, e.x, e.y + 1, 0, 0.5);
  soltarLista(e.x, e.y, c.botin);
  Sonido.sfx.muere();
  if (!J.bestiario) J.bestiario = {};
  J.bestiario[e.tipo] = (J.bestiario[e.tipo] || 0) + 1;
  if (e.fija !== undefined && M.fijas[e.fija]) M.fijas[e.fija].muerto = J.tiempoMundo || 1;
  if (c.esporas) J.charcos.push({ x: e.x, y: e.y, t: 5, dano: c.dano, tipo: 'espora' });
  if (J.mascotaEnt && Math.hypot(J.mascotaEnt.x - e.x, J.mascotaEnt.y - e.y) < 200) ganarXPMascota(c.xp || 5);
  if (c.divide) for (let k = 0; k < 2; k++) { const h = nuevaCriatura(c.divide, e.x + (k ? 6 : -6), e.y); if (!choca(h.x, h.y, h.hw, h.hh)) J.enemigos.push(h); }
}

function actualizarCriaturas(dt) {
  const p = J.jug;
  FLUJO.t -= dt;
  if (FLUJO.t <= 0) { FLUJO.t = 0.35; calcularFlujo(); }
  repartirTurnos(dt);
  for (const e of J.enemigos) {
    const c = CRIATURAS[e.tipo];
    e.t += dt; e.flash -= dt; e.cd -= dt;
    if (e.veneno) { e.veneno.t -= dt; e.venT = (e.venT || 0) - dt; if (e.venT <= 0) { e.venT = 0.5; e.vida -= e.veneno.dano; numero(e.x, e.y, e.veneno.dano, '#9aff6a', 14); if (e.vida <= 0) morirCriatura(e); } if (e.veneno.t <= 0) e.veneno = null; }
    mover(e, e.kbx * dt, e.kby * dt);
    e.kbx *= Math.pow(0.001, dt); e.kby *= Math.pow(0.001, dt);
    if (e.aturdido > 0) { e.aturdido -= dt; continue; }
    if (e.reza && rezaGruto(e, Math.hypot(p.x - e.x, p.y - e.y))) continue;
    const lejos = Math.hypot(p.x - e.x, p.y - e.y);
    conducta: {
    const emboscada = EMBOSCADA.has(c.conducta);
    if (!emboscada && !percibir(e, c, lejos, dt)) { deambular(e, c, dt); break conducta; }
    e.quieta = false;
    const r = lejos < 240 ? rumbo(e) : null;
    if (r) e.mira = r.x >= 0 ? 1 : -1;
    if (!emboscada && !e.turno && r && e.estado === 'anda' && lejos < 6 * T) { rodear(e, c, r, lejos, dt); break conducta; }
    const vel = c.vel;
    if (CONDUCTAS[c.conducta]) CONDUCTAS[c.conducta](e, c, r, lejos, dt);
    else switch (c.conducta) {
      case 'salta':
        if (e.z > 0 || e.vz > 0) {
          e.vz -= 420 * dt; e.z += e.vz * dt;
          if (e.saltoX !== undefined) mover(e, e.saltoX * dt, e.saltoY * dt);
          if (e.z <= 0) { e.z = 0; e.vz = 0; e.t = 0; e.aplasta = 0.15; if (c.veneno) J.charcos.push({ x: e.x, y: e.y, t: 3.5, dano: c.dano * 0.6, tipo: 'veneno' }); }
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
    }
    // separarse de las demás
    for (const o of J.enemigos) {
      if (o === e) continue;
      const dx = e.x - o.x, dy = e.y - o.y, d = Math.hypot(dx, dy), min = e.radio + o.radio;
      if (d < min && d > 0.01) mover(e, (dx / d) * (min - d) * 0.3, (dy / d) * (min - d) * 0.3);
    }
    if ((e.z < 4 || c.vuela) && e.estado !== 'prepara' && e.estado !== 'escondida' && Math.abs(p.x - e.x) < e.radio + p.hw && Math.abs(p.y - e.y) < 7) danarJugador(e.estado === 'carga' ? c.dano * 1.5 : c.dano, e);
  }
  // las que no saben de ti y se han quedado muy lejos se van (el mundo se renueva)
  J.enemigos = J.enemigos.filter((e) => e.vida > 0 && Math.hypot(e.x - p.x, e.y - p.y) < (e.alerta || e.fija !== undefined ? 36 : 26) * T);
  aparecerCriaturas(dt);
  aparecerFijas(dt);
}

// Aparecen en lo oscuro, lejos de la luz, del santuario y de las guaridas.
function aparecerCriaturas(dt) {
  const p = J.jug;
  J.aparecer -= dt;
  if (J.aparecer > 0) return;
  J.aparecer = 2.4 + Math.random() * 2;
  if (Object.values(J.jefes).some((j) => j.activo && j.estado !== 'muerto')) return; // en la pelea con un guardián, solo él y los suyos
  const progreso = Object.keys(J.jefesMuertos).length;
  const max = 3 + Math.min(3, J.objetivo >> 3) + progreso;
  const cerca = J.enemigos.filter((e) => Math.hypot(e.x - p.x, e.y - p.y) < 22 * T).length;
  if (cerca >= max) return;
  for (let k = 0; k < 14; k++) {
    const a = Math.random() * Math.PI * 2, d = (12 + Math.random() * 8) * T;
    const tx = Math.floor((p.x + Math.cos(a) * d) / T), ty = Math.floor((p.y + Math.sin(a) * d) / T);
    if (!enMundo(tx, ty) || solido(tx, ty) || objetoEn(tx, ty)) continue;
    const i = idx(tx, ty), reg = M.region[i];
    const selva = reg >= REGION.SELVA;
    if (reg === REGION.SANTUARIO || reg === REGION.SELLADO || Math.hypot(tx - CENTRO, ty - CENTRO) < 14 || luzEn(tx, ty) > (selva ? 0.6 : 0.12)) continue;
    if (M.piso[i] === SUELO.MADERA || M.piso[i] === SUELO.ARADA || M.piso[i] === SUELO.TABLAS || M.piso[i] === SUELO.ALFOMBRA || M.piso[i] === SUELO.ALFOMBRA_VERDE) continue; // tu base es tuya
    if (M.mercaderes.some((m) => Math.hypot(tx - m.x, ty - m.y) < 10)) continue;
    if (Object.values(M.guaridas).some((g) => Math.hypot(tx - g.x, ty - g.y) < 18)) continue;
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
