'use strict';
// El jugador, las criaturas, el Topo Rey, lo que sueltan las cosas, las partículas y el inventario.

const J = {
  estado: 'titulo', panel: null, t: 0, jug: null, enemigos: [], jefe: null, proyectiles: [], botines: [],
  particulas: [], numeros: [], avisos: [], inv: [], sel: 0, armadura: null, hito: {}, temblor: 0,
  entrada: { mx: 0, my: 0 }, cam: { x: 0, y: 0 }, objetivo: 0, aparecer: 0, latidos: 0, guardadoT: 0,
  cuenta(id) { let n = 0; for (const s of this.inv) if (s && s.id === id) n += s.n; return n; },
};
const N_INV = 40, N_BARRA = 10;

// ---------------------------------------------------------------- inventario

const maxPila = (id) => ITEMS[id].max || 99;
function agregar(id, n = 1) {
  for (const s of J.inv) if (s && s.id === id && s.n < maxPila(id)) { const k = Math.min(n, maxPila(id) - s.n); s.n += k; n -= k; if (!n) return 0; }
  for (let i = 0; i < N_INV && n; i++) if (!J.inv[i]) { const k = Math.min(n, maxPila(id)); J.inv[i] = { id, n: k }; n -= k; }
  return n;
}
function quitar(id, n = 1) {
  for (let i = N_INV - 1; i >= 0 && n; i--) {
    const s = J.inv[i];
    if (s && s.id === id) { const k = Math.min(n, s.n); s.n -= k; n -= k; if (!s.n) J.inv[i] = null; }
  }
}
function mejor(tipo) {
  let m = null;
  for (const s of J.inv) if (s && ITEMS[s.id].tipo === tipo) {
    const it = ITEMS[s.id], v = tipo === 'pico' ? it.nivel * 100 + it.minado : it.dano;
    if (!m || v > m.v) m = { id: s.id, v };
  }
  return m ? ITEMS[m.id] : null;
}
function aviso(txt, icono = null, color = '#f2ecf8') {
  const prev = J.avisos.find((a) => a.clave === txt);
  if (prev) { prev.t = 2.6; return; }
  J.avisos.push({ txt, icono, color, t: 2.6, clave: txt });
  if (J.avisos.length > 4) J.avisos.shift();
}
function avisoRecogido(id, n) {
  const a = J.avisos.find((x) => x.item === id);
  if (a) { a.n += n; a.txt = `+${a.n} ${ITEMS[id].nombre}`; a.t = 2.6; return; }
  J.avisos.push({ txt: `+${n} ${ITEMS[id].nombre}`, icono: ITEMS[id].icono, color: '#f2ecf8', t: 2.6, item: id, n });
  if (J.avisos.length > 4) J.avisos.shift();
}

// ---------------------------------------------------------------- partículas, números y botín

function particulas(x, y, n, colores, o = {}) {
  for (let i = 0; i < n; i++) {
    const a = Math.random() * Math.PI * 2, v = (o.vel || 50) * (0.4 + Math.random() * 0.8);
    J.particulas.push({
      x: x + (Math.random() - 0.5) * (o.disp || 6), y: y + (Math.random() - 0.5) * (o.disp || 6), z: o.z ?? 4,
      vx: Math.cos(a) * v, vy: Math.sin(a) * v * 0.6, vz: (o.alto ?? 40) * (0.5 + Math.random()),
      g: o.g ?? 160, vida: (o.vida || 0.5) * (0.6 + Math.random() * 0.8), c: colores[Math.floor(Math.random() * colores.length)],
      s: o.s || 1, brilla: !!o.brilla,
    });
  }
}
// Número que sube desde (x, pie) a «alto» píxeles del suelo.
function numero(x, y, txt, c = '#ffffff', alto = 16) { J.numeros.push({ x, y, alto, txt: String(txt), c, vida: 0.8 }); }
function soltar(x, y, id, n) {
  for (let i = 0; i < n; i++) {
    const a = Math.random() * Math.PI * 2;
    J.botines.push({ x, y, z: 2, vz: 50 + Math.random() * 30, vx: Math.cos(a) * 30, vy: Math.sin(a) * 18, id, t: 0 });
  }
}
function soltarLista(x, y, lista) {
  for (const [id, a, b, p = 1] of lista) if (Math.random() < p) soltar(x, y, id, a + Math.floor(Math.random() * (b - a + 1)));
}

// ---------------------------------------------------------------- movimiento y choques

function choca(x, y, hw, hh) {
  const x0 = Math.floor((x - hw) / T), x1 = Math.floor((x + hw) / T), y0 = Math.floor((y - hh) / T), y1 = Math.floor((y + hh) / T);
  for (let ty = y0; ty <= y1; ty++) for (let tx = x0; tx <= x1; tx++) if (solido(tx, ty)) return true;
  return false;
}
// Mueve con deslizamiento contra las paredes. Devuelve true si chocó.
function mover(e, dx, dy) {
  let golpe = false;
  const pasos = Math.max(1, Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)) / 4));
  for (let p = 0; p < pasos; p++) {
    const sx = dx / pasos, sy = dy / pasos;
    if (sx && !choca(e.x + sx, e.y, e.hw, e.hh)) e.x += sx;
    else if (sx) {
      golpe = true;
      // esquina: si casi cabe, se desliza un poco en vertical para entrar en el hueco
      for (const n of [1, -1, 2, -2, 3, -3, 4, -4]) if (!choca(e.x + sx, e.y + n, e.hw, e.hh) && !choca(e.x, e.y + n, e.hw, e.hh) && !sy) { e.y += Math.sign(n) * 0.8; break; }
    }
    if (sy && !choca(e.x, e.y + sy, e.hw, e.hh)) e.y += sy;
    else if (sy) {
      golpe = true;
      for (const n of [1, -1, 2, -2, 3, -3, 4, -4]) if (!choca(e.x + n, e.y + sy, e.hw, e.hh) && !choca(e.x + n, e.y, e.hw, e.hh) && !sx) { e.x += Math.sign(n) * 0.8; break; }
    }
  }
  return golpe;
}

// ---------------------------------------------------------------- jugador

function nuevoJugador() {
  return {
    x: CENTRO * T + 8, y: (CENTRO + 3) * T + 10, hw: 5, hh: 3, dir: 'ab', fx: 0, fy: 1, anim: 0, andando: false,
    vida: 100, vidaMax: 100, hambre: 100, golpeT: 0, swing: 0, swingDur: 0.3, swingItem: null, flash: 0, invuln: 0,
    kbx: 0, kby: 0, regenT: 0, hambreT: 0,
  };
}
function defensa() { return J.armadura ? ITEMS[J.armadura.id].def : 0; }

function actualizarJugador(dt) {
  const p = J.jug;
  let { mx, my } = J.entrada;
  const m = Math.hypot(mx, my);
  if (m > 1) { mx /= m; my /= m; }
  p.andando = m > 0.15;
  if (p.andando) {
    p.fx = mx / Math.max(m, 0.001); p.fy = my / Math.max(m, 0.001);
    p.dir = Math.abs(p.fx) > Math.abs(p.fy) * 1.1 ? (p.fx > 0 ? 'de' : 'iz') : (p.fy > 0 ? 'ab' : 'ar');
    p.anim += dt * 9 * Math.min(1, m);
  } else p.anim = 0;
  const vel = 66 * (p.swing > 0 ? 0.75 : 1);
  mover(p, (mx * vel + p.kbx) * dt, (my * vel + p.kby) * dt);
  p.kbx *= Math.pow(0.002, dt); p.kby *= Math.pow(0.002, dt);
  p.golpeT -= dt; p.swing -= dt; p.flash -= dt; p.invuln -= dt;

  // hambre y vida
  p.hambreT += dt;
  if (p.hambreT > 9) { p.hambreT = 0; p.hambre = Math.max(0, p.hambre - 1); }
  p.regenT += dt;
  const enSantuario = Math.hypot(p.x / T - CENTRO, p.y / T - CENTRO) < 7.5;
  if (p.regenT > (enSantuario ? 0.35 : 3)) {
    p.regenT = 0;
    if (p.hambre <= 0) danarJugador(1, null, true);
    else if ((p.hambre > 35 || enSantuario) && p.vida < p.vidaMax) p.vida = Math.min(p.vidaMax, p.vida + 1);
  }
}

function danarJugador(d, desde, sinEmpuje = false) {
  const p = J.jug;
  if (J.estado !== 'juego' || (p.invuln > 0 && !sinEmpuje)) return;
  const real = sinEmpuje ? d : Math.max(1, d - defensa());
  p.vida -= real; p.flash = 0.12;
  numero(p.x, p.y, real, '#ff6b6b', 24);
  if (!sinEmpuje) {
    p.invuln = 0.7; J.temblor = Math.max(J.temblor, 0.25);
    if (desde) { const a = Math.atan2(p.y - desde.y, p.x - desde.x); p.kbx = Math.cos(a) * 160; p.kby = Math.sin(a) * 160; }
    Sonido.sfx.dano();
    try { navigator.vibrate && navigator.vibrate(30); } catch (e) { /* sin vibración */ }
  }
  if (p.vida <= 0) {
    p.vida = 0; J.estado = 'muerto'; J.panel = 'muerto';
    particulas(p.x, p.y - 8, 20, ['#f2c14e', '#3fa596', '#f4c49c'], { vel: 70, vida: 0.9 });
    Sonido.sfx.muere();
  }
}
function revivir() {
  const p = J.jug;
  p.x = CENTRO * T + 8; p.y = (CENTRO + 3) * T + 10; p.vida = Math.round(p.vidaMax * 0.6); p.hambre = Math.max(p.hambre, 50);
  p.invuln = 2; p.kbx = p.kby = 0;
  J.enemigos = J.enemigos.filter((e) => Math.hypot(e.x - p.x, e.y - p.y) > 400);
  if (J.jefe && J.jefe.estado !== 'muerto') reiniciarJefe();
  J.estado = 'juego'; J.panel = null;
  aviso('Despiertas junto al Corazón');
}

// Punto al que mira el jugador (para picar y colocar).
function puntoMira(p) { return { x: p.x + p.fx * 13, y: p.y - 3 + p.fy * 12 }; }
function casillaMira(p) {
  const pt = puntoMira(p);
  let tx = Math.floor(pt.x / T), ty = Math.floor(pt.y / T);
  // si justo ahí no hay pared, mira un poco a los lados del frente
  if (!muroEn(tx, ty) && !objetoEn(tx, ty)) {
    for (const da of [0.45, -0.45]) {
      const a = Math.atan2(p.fy, p.fx) + da, x = Math.floor((p.x + Math.cos(a) * 14) / T), y = Math.floor((p.y - 3 + Math.sin(a) * 13) / T);
      if (muroEn(x, y) && muroEn(x, y) !== MURO.ROCA && muroEn(x, y) !== MURO.RUINA) return { tx: x, ty: y };
    }
  }
  return { tx, ty };
}
function mirarA(p, x, y) {
  const a = Math.atan2(y - p.y, x - p.x);
  p.fx = Math.cos(a); p.fy = Math.sin(a);
  p.dir = Math.abs(p.fx) > Math.abs(p.fy) * 1.1 ? (p.fx > 0 ? 'de' : 'iz') : (p.fy > 0 ? 'ab' : 'ar');
}
function enemigoCerca(p, rango) {
  let m = null, md = rango;
  for (const e of todosEnemigos()) {
    if (e.vida <= 0 || e.enterrado) continue;
    const d = Math.hypot(e.x - p.x, e.y - p.y) - e.radio;
    if (d < md) { md = d; m = e; }
  }
  return m;
}
function todosEnemigos() { return J.jefe && J.jefe.estado !== 'dormido' && J.jefe.estado !== 'muerto' ? J.enemigos.concat([J.jefe]) : J.enemigos; }

function pedestalCerca() {
  const p = J.jug;
  for (const pd of M.pedestales) {
    const o = objetoEn(pd.x, pd.y);
    if (o && !o.extra && Math.hypot(pd.x * T + 8 - p.x, pd.y * T + 12 - p.y) < 30) return o;
  }
  return null;
}

// La acción principal: usa lo que tienes en la mano. apunte: punto del mundo tocado (opcional).
function usar(apunte) {
  const p = J.jug;
  if (p.golpeT > 0 || J.estado !== 'juego') return;
  if (apunte) mirarA(p, apunte.x, apunte.y);
  const s = J.inv[J.sel], it = s ? ITEMS[s.id] : null;

  if (it && it.tipo === 'comida') {
    if (p.vida >= p.vidaMax && p.hambre >= 100) { aviso('No tienes hambre'); p.golpeT = 0.4; return; }
    p.vida = Math.min(p.vidaMax, p.vida + it.cura); p.hambre = Math.min(100, p.hambre + it.hambre);
    quitar(s.id, 1); p.golpeT = 0.45;
    numero(p.x, p.y, '+' + it.cura, '#7dff9a', 24); Sonido.sfx.comer();
    particulas(p.x, p.y - 12, 6, ['#d6f6ff', '#58c8ff', '#f2c6b4'], { vel: 30 });
    return;
  }
  if (it && it.tipo === 'especial') {
    const pd = pedestalCerca();
    if (pd) { colocarFragmento(pd); return; }
  }
  if (it && it.tipo === 'colocable') {
    const { tx, ty } = casillaMira(p);
    if (puedeColocar(tx, ty)) {
      ponerObjeto(tx, ty, it.obj); quitar(s.id, 1); p.golpeT = 0.25;
      Sonido.sfx.colocar(); particulas(tx * T + 8, ty * T + 12, 6, ['#6b5340', '#4a372c'], { vel: 30 });
      if (it.obj === 'mesa') J.hito.mesa = true;
      if (it.obj === 'horno') J.hito.horno = true;
      if (it.obj === 'yunque') J.hito.yunque = true;
      return;
    }
    if (!muroEn(tx, ty)) { aviso('Ahí no cabe'); p.golpeT = 0.3; return; }
  }

  // Herramienta: la que tienes en la mano o, si llevas otra cosa, la mejor del inventario.
  const enMano = it && (it.tipo === 'pico' || it.tipo === 'espada') ? it : null;
  const enemigo = enemigoCerca(p, 30);
  const { tx, ty } = casillaMira(p);
  const hayMuro = muroEn(tx, ty) > 0, obj = objetoEn(tx, ty);
  let herr = enMano;
  if (!herr) herr = (enemigo ? mejor('espada') || mejor('pico') : mejor('pico') || mejor('espada'));
  if (!herr) { aviso('Te falta una herramienta'); return; }
  if (herr.tipo === 'espada' && !enemigo && hayMuro && !enMano) herr = mejor('pico') || herr;

  p.golpeT = herr.vel; p.swing = herr.vel; p.swingDur = herr.vel; p.swingItem = herr.icono;
  if (herr.tipo === 'espada' && enemigo) mirarA(p, enemigo.x, enemigo.y);
  else if (herr.tipo === 'pico' && enemigo && !hayMuro) mirarA(p, enemigo.x, enemigo.y);

  const pegado = enemigo && Math.hypot(enemigo.x - p.x, enemigo.y - p.y) - enemigo.radio < 18;
  if (herr.tipo === 'pico' && hayMuro && !pegado) { picar(tx, ty, herr); return; }
  if (obj && OBJ[obj.tipo].vida > 0 && !enemigo && (herr.tipo === 'pico' || OBJ[obj.tipo].planta)) { golpearObjeto(obj, herr); return; }
  Sonido.sfx.zas();
  atacar(herr.dano, herr.tipo === 'espada' ? 27 : 20, herr.tipo === 'espada' ? 1.35 : 1.0);
}

function puedeColocar(tx, ty) {
  if (!enMundo(tx, ty) || solido(tx, ty) || objetoEn(tx, ty)) return false;
  const p = J.jug, x0 = tx * T, y0 = ty * T;
  const tocaJug = p.x + p.hw > x0 && p.x - p.hw < x0 + T && p.y + p.hh > y0 && p.y - p.hh < y0 + T;
  return !tocaJug;
}

function picar(tx, ty, pico) {
  const i = idx(tx, ty), m = M.muro[i], info = INFO_MURO[m], mena = M.mena[i];
  const cx = tx * T + 8, cy = ty * T + 4, ram = RAMPA[info.arte];
  const req = Math.max(info.nivel, mena ? INFO_MENA[mena].nivel : 0);
  if (pico.nivel < req) {
    Sonido.sfx.metal(); particulas(cx, cy, 4, ['#ffffff', ram[4]], { vel: 40, vida: 0.25 });
    aviso(req >= 99 ? `${info.nombre}: no se puede romper` : 'Necesitas un pico mejor'); return;
  }
  const vidaMax = info.vida * (mena ? INFO_MENA[mena].extra : 1);
  const d = M.danos.get(i) || { v: 0, t: 0 };
  d.v += pico.minado; d.t = J.t; M.danos.set(i, d);
  Sonido.sfx.picar(); J.temblor = Math.max(J.temblor, 0.06);
  particulas(cx, cy, 4, [ram[2], ram[3], ram[1]], { vel: 45, vida: 0.35 });
  if (d.v >= vidaMax) {
    romperMuro(tx, ty);
    Sonido.sfx.romper();
    particulas(cx, cy + 4, 12, [ram[1], ram[2], ram[3], ram[4]], { vel: 60, vida: 0.6, s: 2 });
    if (mena) { const im = INFO_MENA[mena]; soltar(cx, cy + 6, im.item, 1 + (Math.random() < 0.45 ? 1 : 0)); particulas(cx, cy, 6, MENA_COL[im.arte], { vel: 50 }); }
    else if (m === MURO.TIERRA && Math.random() < 0.06) soltar(cx, cy + 6, 'madera', 1);
  }
}
function golpearObjeto(o, herr) {
  const info = OBJ[o.tipo], cx = o.x * T + 8, cy = o.y * T + 8;
  if (info.nivel && (herr.nivel || 0) < info.nivel) { Sonido.sfx.metal(); aviso('Necesitas un pico mejor'); return; }
  o.vida -= info.planta || info.nivel ? Math.max(herr.minado || 0, herr.dano || 0) : 1;
  Sonido.sfx.golpe();
  particulas(cx, cy, 5, info.planta ? ['#8a5a36', '#c28a52', '#4f9a64'] : ['#9aa6b8', '#c28a52'], { vel: 40 });
  o.sacude = 0.15;
  if (o.vida <= 0) {
    quitarObjeto(o.x, o.y);
    soltarLista(cx, cy + 4, info.suelta);
    particulas(cx, cy, 10, info.planta ? ['#8a5a36', '#c28a52', '#5aa06a'] : ['#c28a52', '#9aa6b8'], { vel: 55, s: 2 });
  }
}

function atacar(dano, alcance, angulo) {
  const p = J.jug, fa = Math.atan2(p.fy, p.fx);
  for (const e of todosEnemigos()) {
    if (e.vida <= 0 || e.enterrado) continue;
    const dx = e.x - p.x, dy = (e.y - (e.alto || 6)) - (p.y - 6), d = Math.hypot(dx, dy);
    if (d > alcance + e.radio) continue;
    const da = Math.abs(Math.atan2(Math.sin(Math.atan2(dy, dx) - fa), Math.cos(Math.atan2(dy, dx) - fa)));
    if (da > angulo && d > e.radio + 4) continue;
    herir(e, dano, dx / (d || 1), dy / (d || 1));
  }
}
function herir(e, dano, kx, ky) {
  let real = Math.max(1, Math.round(dano * (e.estado === 'aturdido' ? 1.5 : 1) - (e.def || 0)));
  if (Math.random() < 0.1) real = Math.round(real * 1.6); // golpe crítico
  e.vida -= real; e.flash = 0.1;
  if (!e.jefe) { e.kbx = kx * 115; e.kby = ky * 115; } else J.temblor = Math.max(J.temblor, 0.08);
  numero(e.x, e.y, real, real > dano ? '#ffd166' : '#ffffff', e.jefe ? 44 : 16);
  particulas(e.x, e.y - 4, 5, e.colores || ['#ffffff'], { vel: 60, vida: 0.35 });
  Sonido.sfx.golpe();
  if (e.vida <= 0) {
    if (e.jefe) { e.estado = 'muriendo'; e.t = 1.6; Sonido.sfx.rugido(); return; }
    particulas(e.x, e.y - 4, 14, e.colores || ['#ffffff'], { vel: 70, vida: 0.6, s: 2 });
    soltarLista(e.x, e.y, CRIATURAS[e.tipo].botin);
    Sonido.sfx.muere();
  }
}

function colocarFragmento(pd) {
  quitar('fragmento', 1);
  pd.extra = 1; J.latidos++; J.hito.fragmento = true; J.temblor = 0.8;
  Sonido.sfx.latido(); setTimeout(() => Sonido.sfx.victoria(), 600);
  particulas(pd.x * T + 8, pd.y * T, 30, ['#ff6b8f', '#ffb3c8', '#fff0f4'], { vel: 80, vida: 1, brilla: true });
  marcarMapa(idx(pd.x, pd.y));
  J.panel = 'fin';
}

// ---------------------------------------------------------------- criaturas

const COLORES_CRIATURA = { babosa: ['#86d64f', '#56a83b', '#effed8'], larva: ['#efe2bf', '#c9a878', '#a5513a'], escarabajo: ['#4f74c0', '#34508a', '#a9c8ff'] };
function nuevaCriatura(tipo, x, y) {
  const c = CRIATURAS[tipo];
  return { tipo, x, y, hw: c.radio, hh: 3, radio: c.radio, vida: c.vida, vidaMax: c.vida, def: c.def || 0, t: Math.random(), estado: 'anda', z: 0, vz: 0, kbx: 0, kby: 0, flash: 0, mira: 1, colores: COLORES_CRIATURA[tipo], alto: 6, toqueT: 0 };
}

// Campo de distancias desde el jugador: las criaturas bajan por él y rodean las paredes.
const FLUJO = { R: 20, x0: 0, y0: 0, W: 41, dist: new Int16Array(41 * 41), cola: new Int32Array(41 * 41), t: 0 };
function calcularFlujo() {
  const p = J.jug, R = FLUJO.R, W = FLUJO.W, px = Math.floor(p.x / T), py = Math.floor(p.y / T);
  FLUJO.x0 = px - R; FLUJO.y0 = py - R;
  const d = FLUJO.dist, q = FLUJO.cola;
  d.fill(-1);
  let a = 0, b = 0;
  d[R * W + R] = 0; q[b++] = R * W + R;
  while (a < b) {
    const li = q[a++], lx = li % W, ly = (li - lx) / W, v = d[li];
    for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
      const nx = lx + dx, ny = ly + dy;
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
// Dirección hacia el jugador siguiendo el campo (o en línea recta si está cerca).
function rumbo(e) {
  const p = J.jug, dx = p.x - e.x, dy = p.y - e.y, d = Math.hypot(dx, dy);
  const tx = Math.floor(e.x / T), ty = Math.floor(e.y / T), aqui = distFlujo(tx, ty);
  if (d < 26 || aqui <= 1) return { x: dx / (d || 1), y: dy / (d || 1), d };
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

function actualizarCriaturas(dt) {
  const p = J.jug;
  FLUJO.t -= dt;
  if (FLUJO.t <= 0) { FLUJO.t = 0.35; calcularFlujo(); }
  for (const e of J.enemigos) {
    const c = CRIATURAS[e.tipo];
    e.t += dt; e.flash -= dt; e.toqueT -= dt;
    mover(e, e.kbx * dt, e.kby * dt);
    e.kbx *= Math.pow(0.001, dt); e.kby *= Math.pow(0.001, dt);
    const r = Math.hypot(p.x - e.x, p.y - e.y) < 200 ? rumbo(e) : null;
    if (r) e.mira = r.x >= 0 ? 1 : -1;
    if (e.tipo === 'babosa') {
      // salta: descansa, salta hacia ti, aterriza
      if (e.z > 0 || e.vz > 0) {
        e.vz -= 420 * dt; e.z += e.vz * dt;
        if (e.saltoX !== undefined) mover(e, e.saltoX * dt, e.saltoY * dt);
        if (e.z <= 0) { e.z = 0; e.vz = 0; e.t = 0; e.aplasta = 0.15; }
      } else if (e.t > 0.7 && r) {
        e.vz = 110; e.z = 0.01; e.saltoX = r.x * c.vel * 1.6; e.saltoY = r.y * c.vel * 1.6;
      }
      e.aplasta = (e.aplasta || 0) - dt;
    } else if (e.tipo === 'escarabajo') {
      if (e.estado === 'prepara') { if (e.t > 0.45) { e.estado = 'carga'; e.t = 0; } }
      else if (e.estado === 'carga') { if (mover(e, e.cx * 150 * dt, e.cy * 150 * dt) || e.t > 0.5) { e.estado = 'anda'; e.t = -0.8; } }
      else if (r) {
        if (r.d < 70 && e.t > 1.6 && r.d > 20) { e.estado = 'prepara'; e.t = 0; const a = Math.atan2(p.y - e.y, p.x - e.x); e.cx = Math.cos(a); e.cy = Math.sin(a); }
        else mover(e, r.x * c.vel * dt, r.y * c.vel * dt);
      }
    } else if (r) {
      const ond = 0.75 + 0.25 * Math.sin(e.t * 8);
      mover(e, r.x * c.vel * ond * dt, r.y * c.vel * ond * dt);
    }
    // separarse de las demás
    for (const o of J.enemigos) {
      if (o === e) continue;
      const dx = e.x - o.x, dy = e.y - o.y, d = Math.hypot(dx, dy), min = e.radio + o.radio;
      if (d < min && d > 0.01) mover(e, (dx / d) * (min - d) * 0.3, (dy / d) * (min - d) * 0.3);
    }
    // contacto
    if (e.z < 4 && Math.abs(p.x - e.x) < e.radio + p.hw && Math.abs(p.y - e.y) < 7) danarJugador(c.dano, e);
  }
  J.enemigos = J.enemigos.filter((e) => e.vida > 0 && Math.hypot(e.x - p.x, e.y - p.y) < 34 * T);

  // aparecer criaturas en lo oscuro, lejos de la luz y del santuario
  J.aparecer -= dt;
  if (J.aparecer <= 0) {
    J.aparecer = 1.4;
    const max = 6 + Math.min(6, Object.keys(J.hito).length);
    const cerca = J.enemigos.filter((e) => Math.hypot(e.x - p.x, e.y - p.y) < 22 * T).length;
    if (cerca < max) for (let k = 0; k < 12; k++) {
      const a = Math.random() * Math.PI * 2, d = (9 + Math.random() * 7) * T;
      const tx = Math.floor((p.x + Math.cos(a) * d) / T), ty = Math.floor((p.y + Math.sin(a) * d) / T);
      if (!enMundo(tx, ty) || solido(tx, ty) || objetoEn(tx, ty)) continue;
      if (Math.hypot(tx - CENTRO, ty - CENTRO) < 13 || luzEn(tx, ty) > 0.12) continue;
      if (M.arena && Math.hypot(tx - M.arena.x, ty - M.arena.y) < 10) continue;
      if (distFlujo(tx, ty) < 0) continue;
      const b = M.piso[idx(tx, ty)];
      const tipo = b === BIOMA.ARCILLA ? 'larva' : b === BIOMA.PIEDRA ? 'escarabajo' : 'babosa';
      J.enemigos.push(nuevaCriatura(tipo, tx * T + 8, ty * T + 10));
      break;
    }
  }
}

// ---------------------------------------------------------------- el Topo Rey

function crearJefe() {
  const a = M.arena;
  J.jefe = { jefe: true, tipo: 'topo', x: a.x * T + 8, y: a.y * T + 12, hw: 9, hh: 5, radio: 12, alto: 14, vida: JEFE_TOPO.vida, vidaMax: JEFE_TOPO.vida, estado: 'dormido', t: 0, flash: 0, def: 0, enterrado: false, ultimo: '', colores: ['#7a5238', '#a07450', '#ff8fa8'], fase: 1, rafagas: 0 };
}
function reiniciarJefe() {
  const j = J.jefe, a = M.arena;
  j.x = a.x * T + 8; j.y = a.y * T + 12; j.vida = j.vidaMax; j.estado = 'dormido'; j.enterrado = false; j.fase = 1;
  J.proyectiles = [];
}
function lanzarRoca(x, y, a, vel, dano = 12) { J.proyectiles.push({ x, y, vx: Math.cos(a) * vel, vy: Math.sin(a) * vel, dano, vida: 2.5 }); }

function actualizarJefe(dt) {
  const j = J.jefe, p = J.jug;
  if (!j || j.estado === 'muerto') return;
  j.t -= dt; j.flash -= dt;
  const a = M.arena, dJug = Math.hypot(p.x / T - a.x, p.y / T - a.y);
  const haciaJug = Math.atan2(p.y - j.y, p.x - j.x);
  if (j.estado === 'dormido') {
    if (dJug < 7 && J.estado === 'juego') { j.estado = 'despierta'; j.t = 1.5; J.temblor = 1; Sonido.sfx.rugido(); aviso('¡El Topo Rey despierta!', null, '#ffd166'); }
    return;
  }
  if (j.estado === 'muriendo') {
    J.temblor = Math.max(J.temblor, 0.3);
    if (Math.random() < 0.4) particulas(j.x + (Math.random() - 0.5) * 24, j.y - Math.random() * 24, 4, j.colores, { vel: 80 });
    if (j.t <= 0) {
      j.estado = 'muerto'; J.hito.topo = true;
      particulas(j.x, j.y - 10, 50, ['#7a5238', '#a07450', '#ff8fa8', '#f6c84c'], { vel: 110, vida: 1, s: 2 });
      soltar(j.x, j.y, 'fragmento', 1); soltar(j.x, j.y, 'pico_topo', 1); soltar(j.x, j.y, 'lingote_cobre', 6); soltar(j.x, j.y, 'larva_asada', 2);
      Sonido.sfx.victoria(); aviso('¡Derrotaste al Topo Rey!', null, '#ffd166');
    }
    return;
  }
  if (dJug > 17) { reiniciarJefe(); aviso('El Topo Rey vuelve a su guarida'); return; }
  if (j.vida < j.vidaMax / 2 && j.fase === 1) { j.fase = 2; Sonido.sfx.rugido(); J.temblor = 0.6; aviso('¡El Topo Rey se enfurece!', null, '#ff6b6b'); }
  const rapido = j.fase === 2 ? 1.3 : 1;
  const elegir = () => {
    const ops = ['rocas', 'excava', 'embiste'].filter((x) => x !== j.ultimo);
    j.estado = ops[Math.floor(Math.random() * ops.length)]; j.ultimo = j.estado; j.t = 0.6; j.rafagas = j.fase === 2 ? 3 : 2;
  };
  switch (j.estado) {
    case 'despierta': if (j.t <= 0) { j.estado = 'persigue'; j.t = 2; } break;
    case 'persigue':
      mover(j, Math.cos(haciaJug) * JEFE_TOPO.vel * rapido * dt, Math.sin(haciaJug) * JEFE_TOPO.vel * rapido * dt);
      if (j.t <= 0) elegir();
      if (j.fase === 2 && Math.random() < dt * 0.25 && J.enemigos.length < 10) {
        const e = nuevaCriatura('babosa', j.x + (Math.random() - 0.5) * 30, j.y + 10);
        if (!choca(e.x, e.y, e.hw, e.hh)) J.enemigos.push(e);
      }
      break;
    case 'rocas':
      if (j.t <= 0) {
        const n = j.fase === 2 ? 7 : 5;
        for (let k = 0; k < n; k++) lanzarRoca(j.x, j.y - 12, haciaJug + (k - (n - 1) / 2) * 0.22, 95);
        Sonido.sfx.excavar(); j.rafagas--; j.t = 0.55;
        if (j.rafagas <= 0) { j.estado = 'persigue'; j.t = 1.8 / rapido; }
      }
      break;
    case 'excava':
      if (!j.enterrado) {
        if (Math.random() < 0.5) particulas(j.x, j.y, 3, RAMPA.tierra.slice(1), { vel: 40 });
        if (j.t <= 0) { j.enterrado = true; j.t = 2.2; Sonido.sfx.excavar(); }
      } else {
        if (j.t > 0.8) {
          mover(j, Math.cos(haciaJug) * 80 * rapido * dt, Math.sin(haciaJug) * 80 * rapido * dt);
          if (Math.random() < 0.6) particulas(j.x, j.y, 2, RAMPA.tierra.slice(1), { vel: 30, vida: 0.4 });
        } else if (j.t > 0) J.temblor = Math.max(J.temblor, 0.12);
        else {
          j.enterrado = false; j.estado = 'persigue'; j.t = 1.6 / rapido;
          Sonido.sfx.temblor(); J.temblor = 0.5;
          particulas(j.x, j.y, 26, RAMPA.tierra.slice(1), { vel: 100, s: 2, vida: 0.7 });
          if (Math.hypot(p.x - j.x, p.y - j.y) < 32) danarJugador(22, j);
          for (let k = 0; k < 8; k++) lanzarRoca(j.x, j.y - 6, (k / 8) * Math.PI * 2, 80, 10);
        }
      }
      break;
    case 'embiste':
      if (j.t > 0) { j.cx = Math.cos(haciaJug); j.cy = Math.sin(haciaJug); if (Math.random() < 0.3) particulas(j.x, j.y, 1, ['#ffffff'], { vel: 20 }); }
      else {
        j.estado = 'carga'; j.t = 0.85; Sonido.sfx.rugido();
      }
      break;
    case 'carga':
      if (mover(j, j.cx * 175 * rapido * dt, j.cy * 175 * rapido * dt)) {
        j.estado = 'aturdido'; j.t = 1.7; J.temblor = 0.5; Sonido.sfx.temblor();
        particulas(j.x + j.cx * 10, j.y, 16, RAMPA.tierra.slice(1), { vel: 80, s: 2 });
      } else if (j.t <= 0) { j.estado = 'persigue'; j.t = 1.4; }
      break;
    case 'aturdido':
      if (Math.random() < 0.15) particulas(j.x, j.y - 30, 1, ['#ffd166', '#ffffff'], { vel: 25, g: 0, brilla: true });
      if (j.t <= 0) { j.estado = 'persigue'; j.t = 1.5; }
      break;
  }
  if (!j.enterrado && j.estado !== 'aturdido' && j.estado !== 'despierta' && Math.abs(p.x - j.x) < j.hw + p.hw + 2 && Math.abs(p.y - j.y) < 9)
    danarJugador(j.estado === 'carga' ? 24 : JEFE_TOPO.dano, j);
}

function actualizarProyectiles(dt) {
  const p = J.jug;
  for (const r of J.proyectiles) {
    r.x += r.vx * dt; r.y += r.vy * dt; r.vida -= dt;
    if (solido(Math.floor(r.x / T), Math.floor(r.y / T))) { r.vida = 0; particulas(r.x, r.y, 4, ['#8a7a6a', '#5e5046'], { vel: 40 }); }
    else if (Math.hypot(r.x - p.x, r.y - (p.y - 6)) < 8) { r.vida = 0; danarJugador(r.dano, { x: r.x - r.vx, y: r.y - r.vy }); }
  }
  J.proyectiles = J.proyectiles.filter((r) => r.vida > 0);
}

// ---------------------------------------------------------------- botín, partículas y paredes que se curan

function actualizarMundoVivo(dt) {
  const p = J.jug;
  for (const b of J.botines) {
    b.t += dt;
    if (b.z > 0 || b.vz > 0) { b.vz -= 300 * dt; b.z = Math.max(0, b.z + b.vz * dt); b.x += b.vx * dt; b.y += b.vy * dt; if (b.z === 0) { b.vz = b.vz < -40 ? -b.vz * 0.35 : 0; } }
    const d = Math.hypot(p.x - b.x, p.y - 4 - b.y);
    if (b.t > 0.35 && d < 34 && J.estado === 'juego') { const k = Math.min(1, dt * 9); b.x += (p.x - b.x) * k; b.y += (p.y - 4 - b.y) * k; }
    if (b.t > 0.35 && d < 8 && J.estado === 'juego') {
      const sobra = agregar(b.id, 1);
      if (sobra) { if (!J.llenoT || J.t - J.llenoT > 3) { aviso('La mochila está llena'); J.llenoT = J.t; } b.t = -1; continue; }
      b.recogido = true; J.racha = (J.t - (J.rachaT || 0) < 0.6 ? (J.racha || 0) + 1 : 0); J.rachaT = J.t;
      Sonido.sfx.recoger(J.racha); avisoRecogido(b.id, 1);
      if (b.id === 'espada_cobre') J.hito.espada_cobre = true;
    }
  }
  J.botines = J.botines.filter((b) => !b.recogido);
  for (const q of J.particulas) {
    q.vida -= dt; q.x += q.vx * dt; q.y += q.vy * dt;
    if (q.g) { q.vz -= q.g * dt; q.z += q.vz * dt; if (q.z < 0) { q.z = 0; q.vz *= -0.3; q.vx *= 0.5; q.vy *= 0.5; } }
    else { q.y -= 10 * dt; }
  }
  J.particulas = J.particulas.filter((q) => q.vida > 0);
  if (J.particulas.length > 400) J.particulas.splice(0, J.particulas.length - 400);
  for (const n of J.numeros) { n.vida -= dt; n.alto += 18 * dt; }
  J.numeros = J.numeros.filter((n) => n.vida > 0);
  for (const a of J.avisos) a.t -= dt;
  J.avisos = J.avisos.filter((a) => a.t > 0);
  for (const [i, d] of M.danos) if (J.t - d.t > 4) M.danos.delete(i);
  J.temblor = Math.max(0, J.temblor - dt);
  for (const o of M.objetos.values()) if (o.sacude > 0) o.sacude -= dt;
}

// ---------------------------------------------------------------- crear

function estacionesCerca() {
  const p = J.jug, est = new Set(), tx = Math.floor(p.x / T), ty = Math.floor(p.y / T);
  for (let y = ty - 3; y <= ty + 3; y++) for (let x = tx - 3; x <= tx + 3; x++) {
    const o = objetoEn(x, y);
    if (o && OBJ[o.tipo].estacion) est.add(OBJ[o.tipo].estacion);
  }
  return est;
}
function puedeCrear(r, est) {
  if (r.est && !est.has(r.est)) return false;
  return Object.entries(r.ing).every(([id, n]) => J.cuenta(id) >= n);
}
function crear(r) {
  const est = estacionesCerca();
  if (!puedeCrear(r, est)) { Sonido.sfx.error(); return false; }
  for (const [id, n] of Object.entries(r.ing)) quitar(id, n);
  const sobra = agregar(r.item, r.n);
  if (sobra) soltar(J.jug.x, J.jug.y, r.item, sobra);
  Sonido.sfx.crear();
  avisoRecogido(r.item, r.n);
  if (r.item === 'espada_cobre') J.hito.espada_cobre = true;
  return true;
}
