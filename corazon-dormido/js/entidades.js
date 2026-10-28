'use strict';
// El jugador: inventario, equipo, habilidades, mejoras de comida, herramientas (picar, golpear, disparar,
// arar, regar, pescar, construir), daño y muerte. También el botín, las partículas y los efectos.

const J = {
  estado: 'titulo', panel: null, t: 0, jug: null, enemigos: [], jefes: {}, proyectiles: [], disparos: [], botines: [], efectos: [], charcos: [], avisosSuelo: [],
  particulas: [], numeros: [], avisos: [], inv: [], sel: 0, hito: {}, temblor: 0, pausaGolpe: 0,
  entrada: { mx: 0, my: 0 }, cam: { x: 0, y: 0 }, objetivo: 0, aparecer: 0, guardadoT: 0, tiempoMundo: 0,
  equipo: { casco: null, peto: null, botas: null, acc1: null, acc2: null, mascota: null }, hab: {}, buffs: [], st: null, bombas: [],
  mascotas: {}, altares: {}, murallaAbierta: false, regreso: null,
  jefesMuertos: {}, estatuas: {}, cama: null, cofreAbierto: null, olla: null, pesca: null, corazonDespierto: false,
  cuenta(id) { let n = 0; for (const s of this.inv) if (s && s.id === id) n += s.n; return n; },
};
const N_INV = 40, N_BARRA = 10;
const RANURAS_EQUIPO = ['casco', 'peto', 'botas', 'acc1', 'acc2', 'mascota'];
const TIPO_RANURA = { casco: 'casco', peto: 'peto', botas: 'botas', acc1: 'accesorio', acc2: 'accesorio', mascota: 'mascota' };

// ---------------------------------------------------------------- objetos y platos

// Los platos se crean al cocinar: id «plato:a:b» (ingredientes en orden).
function registrarPlato(a, b) {
  const id = `plato:${a}:${b}`;
  if (ITEMS[id]) return id;
  const ia = ITEMS[a], ib = ITEMS[b];
  const principal = (ib.rareza || 0) > (ia.rareza || 0) ? b : a, otro = principal === a ? b : a;
  const efectos = {};
  for (const ing of [ia.ingrediente, ib.ingrediente]) for (const [k, v] of Object.entries(ing || {})) efectos[k] = (efectos[k] || 0) + v;
  if (a === b) for (const k in efectos) efectos[k] *= 0.75;
  const rareza = Math.max(ia.rareza || 0, ib.rareza || 0);
  ITEMS[id] = {
    nombre: `${PLATO_TIPO[principal] || 'Plato'} ${PLATO_PREFIJO[otro] || ''}`.trim(), tipo: 'comida', plato: true, icono: 'i_plato_' + (PLATO_TIPO[principal] ? principal : 'baya'),
    cura: Math.round((ia.cura + ib.cura) * 2.2 + 8), hambre: Math.min(100, (ia.hambre + ib.hambre) * 1.6), efectos, duracion: 180 + rareza * 60, rareza, max: 99,
    desc: 'Mejoras: ' + Object.entries(efectos).map(([k, v]) => `${NOMBRE_EFECTO[k]} +${k === 'vidaMax' || k === 'defensa' || k === 'vida' ? Math.round(v) : k === 'regen' ? v.toFixed(1) + '/s' : Math.round(v * 100) + '%'}`).join(', ') + '. Dura 3 min.',
  };
  return id;
}
function asegurarItem(id) { if (!ITEMS[id] && id.startsWith('plato:')) { const [, a, b] = id.split(':'); if (ITEMS[a] && ITEMS[b]) registrarPlato(a, b); } return ITEMS[id]; }

const maxPila = (id) => (asegurarItem(id) || {}).max || 99;
function agregar(id, n = 1, inv = J.inv) {
  asegurarItem(id);
  for (const s of inv) if (s && s.id === id && s.n < maxPila(id)) { const k = Math.min(n, maxPila(id) - s.n); s.n += k; n -= k; if (!n) return 0; }
  for (let i = 0; i < inv.length && n; i++) if (!inv[i]) { const k = Math.min(n, maxPila(id)); inv[i] = { id, n: k }; n -= k; }
  return n;
}
function quitar(id, n = 1, inv = J.inv) {
  for (let i = inv.length - 1; i >= 0 && n; i--) {
    const s = inv[i];
    if (s && s.id === id) { const k = Math.min(n, s.n); s.n -= k; n -= k; if (!s.n) inv[i] = null; }
  }
  return n;
}
function mejor(tipo) {
  let m = null;
  for (const s of J.inv) {
    if (!s) continue;
    const it = ITEMS[s.id];
    if (!it || it.tipo !== tipo) continue;
    const v = tipo === 'pico' ? it.poder * 100 + it.dano : it.dano;
    if (!m || v > m.v) m = { id: s.id, v };
  }
  return m ? ITEMS[m.id] : null;
}
function aviso(txt, icono = null, color = '#f6ecda') {
  const prev = J.avisos.find((a) => a.clave === txt);
  if (prev) { prev.t = 2.6; return; }
  J.avisos.push({ txt, icono, color, t: 2.6, clave: txt });
  if (J.avisos.length > 4) J.avisos.shift();
}
function avisoRecogido(id, n) {
  const a = J.avisos.find((x) => x.item === id), it = ITEMS[id];
  if (a) { a.n += n; a.txt = `+${a.n} ${it.nombre}`; a.t = 2.6; return; }
  J.avisos.push({ txt: `+${n} ${it.nombre}`, icono: it.icono, color: RAREZA[it.rareza || 0].color, t: 2.6, item: id, n });
  if (J.avisos.length > 4) J.avisos.shift();
}

// ---------------------------------------------------------------- habilidades y mejoras

function nivel(h) { return (J.hab[h] && J.hab[h].n) || 0; }
function ganarXP(h, cant) {
  if (!J.hab[h]) J.hab[h] = { n: 0, xp: 0 };
  const e = J.hab[h];
  if (e.n >= NIVEL_MAX) return;
  e.xp += cant;
  while (e.n < NIVEL_MAX && e.xp >= xpNivel(e.n)) {
    e.xp -= xpNivel(e.n); e.n++;
    aviso(`${HABILIDADES[h].nombre}: nivel ${e.n}`, null, '#9fe0ff');
    Sonido.sfx.nivel();
  }
}
function calcularStats() {
  const s = { vidaMax: 100, def: 0, vel: 1, dano: 1, danoDist: 1, minado: 1, critico: 0.08, luz: 0, regen: 0, suerte: 0, pesca: 0 };
  s.vidaMax += nivel('vitalidad'); s.vel += nivel('carrera') * 0.005;
  s.dano += nivel('combate') * 0.01; s.danoDist += nivel('distancia') * 0.01; s.minado += nivel('mineria') * 0.01;
  for (const r of RANURAS_EQUIPO) {
    const e = J.equipo[r]; if (!e) continue;
    const it = ITEMS[e.id];
    if (it.tipo === 'mascota') { if (e.id === 'mascota_luci') { s.luz += 3; s.regen += 0.4; } continue; }
    s.def += it.def || 0; s.vel += it.vel || 0; s.vidaMax += it.vidaMax || 0; s.minado += it.minado || 0; s.critico += it.critico || 0; s.luz += it.luz || 0;
    s.regen += it.regen || 0; s.suerte += it.suerte || 0; s.pesca += it.pesca || 0;
    if (it.tipo === 'accesorio' && it.dano) { s.dano += it.dano; s.danoDist += it.dano; }
  }
  for (const b of J.buffs) for (const k in b.efectos) {
    const v = b.efectos[k];
    if (k === 'vidaMax') s.vidaMax += v; else if (k === 'defensa') s.def += v; else if (k === 'vel') s.vel += v;
    else if (k === 'dano') { s.dano += v; s.danoDist += v; } else if (k === 'minado') s.minado += v;
    else if (k === 'luz') s.luz += v; else if (k === 'regen') s.regen += v; else if (k === 'suerte') s.suerte += v;
    else if (k === 'critico') s.critico += v;
  }
  J.st = s;
  return s;
}

// ---------------------------------------------------------------- partículas, números, efectos y botín

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
function efecto(n, cuadros, x, y, z, dur, o = {}) { J.efectos.push({ n, cuadros, x, y, z, dur, t: 0, ...o }); }
function numero(x, y, txt, c = '#ffffff', alto = 16) { J.numeros.push({ x, y, alto, txt: String(txt), c, vida: 0.8 }); }
function soltar(x, y, id, n) {
  asegurarItem(id);
  for (let i = 0; i < n; i++) {
    const a = Math.random() * Math.PI * 2;
    J.botines.push({ x, y, z: 2, vz: 50 + Math.random() * 30, vx: Math.cos(a) * 30, vy: Math.sin(a) * 18, id, t: 0 });
  }
}
function soltarLista(x, y, lista) {
  const suerte = J.st ? J.st.suerte : 0;
  for (const [id, a, b, p = 1] of lista) if (Math.random() < p * (1 + suerte)) soltar(x, y, id, a + Math.floor(Math.random() * (b - a + 1)));
}

// ---------------------------------------------------------------- movimiento y choques

function choca(x, y, hw, hh, jugador = false) {
  const x0 = Math.floor((x - hw) / T), x1 = Math.floor((x + hw) / T), y0 = Math.floor((y - hh) / T), y1 = Math.floor((y + hh) / T);
  for (let ty = y0; ty <= y1; ty++) for (let tx = x0; tx <= x1; tx++) if (solido(tx, ty, jugador)) return true;
  return false;
}
function mover(e, dx, dy) {
  let golpe = false;
  const jug = e === J.jug, pasos = Math.max(1, Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)) / 4));
  for (let p = 0; p < pasos; p++) {
    const sx = dx / pasos, sy = dy / pasos;
    if (sx && !choca(e.x + sx, e.y, e.hw, e.hh, jug)) e.x += sx;
    else if (sx) {
      golpe = true;
      if (!sy) for (const n of [1, -1, 2, -2, 3, -3, 4, -4]) if (!choca(e.x + sx, e.y + n, e.hw, e.hh, jug) && !choca(e.x, e.y + n, e.hw, e.hh, jug)) { e.y += Math.sign(n) * 0.8; break; }
    }
    if (sy && !choca(e.x, e.y + sy, e.hw, e.hh, jug)) e.y += sy;
    else if (sy) {
      golpe = true;
      if (!sx) for (const n of [1, -1, 2, -2, 3, -3, 4, -4]) if (!choca(e.x + n, e.y + sy, e.hw, e.hh, jug) && !choca(e.x + n, e.y, e.hw, e.hh, jug)) { e.x += Math.sign(n) * 0.8; break; }
    }
  }
  return golpe;
}

// ---------------------------------------------------------------- jugador

function puntoInicio() {
  if (J.cama && objetoEn(J.cama.x, J.cama.y) && objetoEn(J.cama.x, J.cama.y).tipo === 'cama') return { x: J.cama.x * T + 8, y: (J.cama.y + 1) * T + 9 };
  return { x: CENTRO * T + 8, y: (CENTRO + 3) * T + 10 };
}
function nuevoJugador() {
  const p0 = puntoInicio();
  return {
    x: p0.x, y: p0.y, hw: 5, hh: 3, dir: 'ab', fx: 0, fy: 1, anim: 0, andando: false,
    vida: 100, hambre: 100, golpeT: 0, swing: 0, swingDur: 0.3, swingItem: null, flash: 0, invuln: 0,
    kbx: 0, kby: 0, regenT: 0, hambreT: 0, distT: 0,
  };
}

function actualizarJugador(dt) {
  const p = J.jug, st = calcularStats();
  J.tiempoMundo += dt;
  J.buffs = J.buffs.filter((b) => b.fin > J.tiempoMundo);
  let { mx, my } = J.entrada;
  const m = Math.hypot(mx, my);
  if (m > 1) { mx /= m; my /= m; }
  p.andando = m > 0.15 && !J.pesca;
  if (J.pesca) { mx = my = 0; }
  if (p.andando) {
    p.fx = mx / Math.max(m, 0.001); p.fy = my / Math.max(m, 0.001);
    p.dir = Math.abs(p.fx) > Math.abs(p.fy) * 1.1 ? (p.fx > 0 ? 'de' : 'iz') : (p.fy > 0 ? 'ab' : 'ar');
    p.anim += dt * 9 * Math.min(1, m);
  } else p.anim = 0;
  const vel = 68 * st.vel * (p.swing > 0 ? 0.75 : 1);
  const x0 = p.x, y0 = p.y;
  mover(p, (mx * vel + p.kbx) * dt, (my * vel + p.kby) * dt);
  p.distT += Math.hypot(p.x - x0, p.y - y0);
  if (p.distT > 160) { p.distT = 0; ganarXP('carrera', 1); }
  p.kbx *= Math.pow(0.002, dt); p.kby *= Math.pow(0.002, dt);
  p.golpeT -= dt; p.swing -= dt; p.flash -= dt; p.invuln -= dt;
  p.vida = Math.min(p.vida, st.vidaMax);
  // hambre y vida
  p.hambreT += dt;
  if (p.hambreT > 9) { p.hambreT = 0; p.hambre = Math.max(0, p.hambre - 1); }
  p.regenT += dt;
  const enSantuario = Math.hypot(p.x / T - CENTRO, p.y / T - CENTRO) < 8;
  if (p.regenT > (enSantuario ? 0.35 : 3)) {
    p.regenT = 0;
    if (p.hambre <= 0) danarJugador(1, null, true);
    else if ((p.hambre > 35 || enSantuario) && p.vida < st.vidaMax) p.vida = Math.min(st.vidaMax, p.vida + 1);
  }
  if (st.regen > 0 && p.vida < st.vidaMax) p.vida = Math.min(st.vidaMax, p.vida + st.regen * dt);
}

function danarJugador(d, desde, sinEmpuje = false) {
  const p = J.jug, st = J.st || calcularStats();
  if (J.estado !== 'juego' || (p.invuln > 0 && !sinEmpuje)) return;
  const real = sinEmpuje ? d : Math.max(1, Math.round(d * (60 / (60 + st.def * 4))));
  p.vida -= real; p.flash = 0.12;
  numero(p.x, p.y, real, '#ff6b6b', 26);
  if (!sinEmpuje) {
    ganarXP('vitalidad', real * 0.5);
    p.invuln = 0.7; J.temblor = Math.max(J.temblor, 0.25);
    if (desde) { const a = Math.atan2(p.y - desde.y, p.x - desde.x); p.kbx = Math.cos(a) * 160; p.kby = Math.sin(a) * 160; }
    Sonido.sfx.dano();
    try { navigator.vibrate && navigator.vibrate(30); } catch (e) { /* sin vibración */ }
    if (J.pesca) J.pesca = null;
  }
  if (p.vida <= 0) {
    p.vida = 0; J.estado = 'muerto'; J.panel = 'muerto'; J.pesca = null;
    particulas(p.x, p.y - 8, 20, ['#d18a4c', '#2c8276', '#f6cfac'], { vel: 70, vida: 0.9 });
    Sonido.sfx.muere();
  }
}
function revivir() {
  const p = J.jug, p0 = puntoInicio();
  p.x = p0.x; p.y = p0.y; p.vida = Math.round(calcularStats().vidaMax * 0.6); p.hambre = Math.max(p.hambre, 50);
  p.invuln = 2; p.kbx = p.kby = 0;
  J.enemigos = J.enemigos.filter((e) => Math.hypot(e.x - p.x, e.y - p.y) > 400);
  for (const j of Object.values(J.jefes)) if (j.estado !== 'muerto') reiniciarJefe(j);
  J.estado = 'juego'; J.panel = null;
  aviso(J.cama ? 'Despiertas en tu cama' : 'Despiertas junto al Corazón');
}

function puntoMira(p) { return { x: p.x + p.fx * 13, y: p.y - 3 + p.fy * 12 }; }
function casillaMira(p) {
  const pt = puntoMira(p);
  let tx = Math.floor(pt.x / T), ty = Math.floor(pt.y / T);
  if (!muroEn(tx, ty) && !objetoEn(tx, ty)) {
    for (const da of [0.45, -0.45]) {
      const a = Math.atan2(p.fy, p.fx) + da, x = Math.floor((p.x + Math.cos(a) * 14) / T), y = Math.floor((p.y - 3 + Math.sin(a) * 13) / T);
      const m = muroEn(x, y);
      if (m && INFO_MURO[m].poder < 999) return { tx: x, ty: y };
    }
  }
  return { tx, ty };
}
function mirarA(p, x, y) {
  const a = Math.atan2(y - p.y, x - p.x);
  p.fx = Math.cos(a); p.fy = Math.sin(a);
  p.dir = Math.abs(p.fx) > Math.abs(p.fy) * 1.1 ? (p.fx > 0 ? 'de' : 'iz') : (p.fy > 0 ? 'ab' : 'ar');
}
function todosEnemigos() {
  const out = J.enemigos.slice();
  for (const j of Object.values(J.jefes)) if (j.activo && j.estado !== 'muerto') out.push(j);
  return out;
}
function enemigoCerca(p, rango, delante = false) {
  let m = null, md = rango;
  for (const e of todosEnemigos()) {
    if (e.vida <= 0 || e.enterrado || e.invisible) continue;
    const dx = e.x - p.x, dy = e.y - p.y, d = Math.hypot(dx, dy) - e.radio;
    if (delante && dx * p.fx + dy * p.fy < -4) continue;
    if (d < md) { md = d; m = e; }
  }
  return m;
}

// Objeto con el que se puede «Usar» cerca del jugador.
function interactivoCerca() {
  const p = J.jug, tx = Math.floor(p.x / T), ty = Math.floor(p.y / T);
  let m = null, md = 1e9;
  for (let y = ty - 2; y <= ty + 2; y++) for (let x = tx - 2; x <= tx + 2; x++) {
    const o = objetoEn(x, y);
    if (!o || !OBJ[o.tipo].interactua) continue;
    const d = Math.hypot(x * T + 8 - p.x, y * T + 12 - p.y);
    if (d < (o.tipo === 'corazon' ? 40 : 30) && d < md) { md = d; m = o; }
  }
  return m;
}
function interactuar(o) {
  const que = OBJ[o.tipo].interactua;
  if (que === 'crear') { J.panel = 'crear'; UI.scroll = 0; return; }
  if (que === 'cofre') {
    if (!o.datos) o.datos = { inv: new Array(30).fill(null) };
    J.cofreAbierto = o; J.panel = 'cofre'; Sonido.sfx.colocar();
    if (o.datos.moho) J.hito.cofreMoho = true;
    return;
  }
  if (que === 'olla') { J.olla = o; if (!o.datos) o.datos = { a: null, b: null }; J.panel = 'olla'; return; }
  if (que === 'cama') { J.cama = { x: o.x, y: o.y }; aviso('Tu cama: aquí despertarás'); Sonido.sfx.crear(); return; }
  if (que === 'corazon') { J.panel = 'corazon'; if (J.corazonDespierto) J.hito.hablo2 = true; return; }
  if (INTERACCIONES[que]) { INTERACCIONES[que](o); return; }
  if (que === 'estatua') {
    const jefe = o.datos && o.datos.jefe, cristal = JEFES[jefe].cristal;
    if (o.extra) { aviso(`La estatua de ${JEFES[jefe].nombre.toLowerCase()} brilla`); return; }
    if (J.cuenta(cristal)) activarEstatua(o, jefe); else aviso(`Necesita el ${ITEMS[cristal].nombre}`);
  }
}

// ---------------------------------------------------------------- la acción principal
// Usa lo que tienes en la mano. apunte: punto del mundo tocado (opcional).

function usar(apunte) {
  const p = J.jug;
  if (p.golpeT > 0 || J.estado !== 'juego') return;
  if (J.pesca) { tirarCana(); return; }
  if (apunte) mirarA(p, apunte.x, apunte.y);
  const s = J.inv[J.sel], it = s ? asegurarItem(s.id) : null;
  const { tx, ty } = casillaMira(p);

  if (it && it.tipo === 'comida') { comer(s); return; }
  if (it && it.tipo === 'especial') {
    const o = interactivoCerca();
    if (o && o.tipo === 'estatua') { interactuar(o); p.golpeT = 0.4; return; }
  }
  if (it && (it.tipo === 'colocable' || it.tipo === 'bloque' || it.tipo === 'suelo' || it.tipo === 'semilla')) {
    if (colocar(it, s, tx, ty)) return;
    if (!muroEn(tx, ty) && !objetoEn(tx, ty)) { aviso('Ahí no cabe'); p.golpeT = 0.3; return; }
  }
  if (it && it.tipo === 'azada') { arar(it, tx, ty); return; }
  if (it && it.tipo === 'regadera') { regar(tx, ty); return; }
  if (it && it.tipo === 'cana') { lanzarCana(it); return; }
  if (it && it.tipo === 'arco') { disparar(it); return; }
  if (it && it.tipo === 'bomba') { lanzarBomba(it, s); return; }
  if (it && it.tipo === 'regreso') { usarRegreso(s); return; }

  // Herramienta: la que llevas o, si llevas otra cosa, la mejor del inventario según lo que tengas delante.
  const enMano = it && (it.tipo === 'pico' || it.tipo === 'espada' || it.tipo === 'maza') ? it : null;
  const enemigo = enemigoCerca(p, it && it.alcance ? it.alcance + 4 : 30);
  const hayMuro = muroEn(tx, ty) > 0, obj = objetoEn(tx, ty);
  let herr = enMano;
  if (!herr) herr = enemigo ? mejor('espada') || mejor('maza') || mejor('pico') : mejor('pico') || mejor('espada') || mejor('maza');
  if (!herr) { aviso('Te falta una herramienta'); return; }
  if (herr.tipo !== 'pico' && !enemigo && (hayMuro || (obj && !OBJ[obj.tipo].planta)) && !enMano) herr = mejor('pico') || herr;

  p.golpeT = herr.vel; p.swing = herr.vel; p.swingDur = herr.vel; p.swingItem = herr.icono;
  if (enemigo && (herr.tipo !== 'pico' || !hayMuro)) mirarA(p, enemigo.x, enemigo.y);
  const pegado = enemigo && Math.hypot(enemigo.x - p.x, enemigo.y - p.y) - enemigo.radio < 18;
  if (herr.tipo === 'pico' && hayMuro && !pegado) { picar(tx, ty, herr); return; }
  if (obj && !enemigo && (OBJ[obj.tipo].vida > 0 || obj.tipo === 'cultivo') && (herr.tipo === 'pico' || OBJ[obj.tipo].planta || OBJ[obj.tipo].rompible)) { golpearObjeto(obj, herr); return; }
  Sonido.sfx.zas();
  efecto('tajo', 3, p.x, p.y, 8, herr.vel * 0.8, { dir: p.dir });
  const maza = herr.tipo === 'maza';
  atacar(herr.dano * J.st.dano, herr.alcance || (maza ? 30 : herr.tipo === 'espada' ? 27 : 20), maza ? 1.6 : herr.tipo === 'espada' ? 1.35 : 1.0, { maza, veneno: herr.veneno });
}

function comer(s) {
  const p = J.jug, it = ITEMS[s.id], st = J.st;
  if (!it.plato && p.vida >= st.vidaMax && p.hambre >= 100) { aviso('No tienes hambre'); p.golpeT = 0.4; return; }
  p.vida = Math.min(st.vidaMax, p.vida + it.cura); p.hambre = Math.min(100, p.hambre + it.hambre);
  if (it.plato && it.efectos) {
    const fuerza = 1 + nivel('cocina') * 0.02, ef = {};
    for (const [k, v] of Object.entries(it.efectos)) if (k !== 'vida') ef[k] = v * fuerza;
    J.buffs = J.buffs.filter((b) => b.id !== s.id);
    J.buffs.push({ id: s.id, nombre: it.nombre, icono: it.icono, efectos: ef, fin: J.tiempoMundo + it.duracion });
    aviso(`${it.nombre}: ${it.desc.split('.')[0].replace('Mejoras: ', '')}`, it.icono, '#9fffa8');
  }
  quitar(s.id, 1); p.golpeT = 0.45;
  numero(p.x, p.y, '+' + Math.round(it.cura), '#7dff9a', 26); Sonido.sfx.comer();
  particulas(p.x, p.y - 12, 6, ['#d6f6ff', '#58c8ff', '#f2c6b4'], { vel: 30 });
}

function ocupadaPorAlguien(tx, ty) {
  const x0 = tx * T, y0 = ty * T, toca = (e) => e.x + e.hw > x0 && e.x - e.hw < x0 + T && e.y + e.hh > y0 && e.y - e.hh < y0 + T;
  return toca(J.jug) || J.enemigos.some(toca);
}
function puedeColocar(tx, ty) {
  return enMundo(tx, ty) && !solido(tx, ty, true) && !objetoEn(tx, ty) && !ocupadaPorAlguien(tx, ty) && M.piso[idx(tx, ty)] !== SUELO.AGUA;
}
function colocar(it, s, tx, ty) {
  const p = J.jug;
  if (it.tipo === 'suelo') {
    if (!enMundo(tx, ty) || muroEn(tx, ty) || objetoEn(tx, ty) || M.piso[idx(tx, ty)] === it.suelo || M.piso[idx(tx, ty)] === SUELO.AGUA) return false;
    ponerSuelo(tx, ty, it.suelo);
  } else if (it.tipo === 'semilla') {
    if (!enMundo(tx, ty) || M.piso[idx(tx, ty)] !== SUELO.ARADA || objetoEn(tx, ty)) { aviso('Las semillas van en tierra arada (usa la azada)'); p.golpeT = 0.3; return true; }
    const o = ponerObjeto(tx, ty, 'cultivo');
    o.datos = { planta: it.cultivo, t0: J.tiempoMundo, crec: 0, regado: false };
    Sonido.sfx.colocar(); particulas(tx * T + 8, ty * T + 12, 5, ['#6a4a30', '#8a5a36'], { vel: 25 });
  } else {
    if (!puedeColocar(tx, ty)) return false;
    if (it.tipo === 'bloque') ponerMuro(tx, ty, it.muro);
    else {
      ponerObjeto(tx, ty, it.obj);
      J.hito[it.obj] = true;
    }
  }
  quitar(s.id, 1); p.golpeT = 0.22;
  Sonido.sfx.colocar(); particulas(tx * T + 8, ty * T + 12, 6, ['#6b5340', '#4a372c'], { vel: 30 });
  return true;
}
function arar(it, tx, ty) {
  const p = J.jug;
  p.golpeT = 0.35; p.swing = 0.35; p.swingDur = 0.35; p.swingItem = it.icono;
  const casillas = [[tx, ty]];
  if (it.area) { const px = Math.round(-p.fy), py = Math.round(p.fx); for (let k = 1; k <= it.area; k++) casillas.push([tx + px * k, ty + py * k], [tx - px * k, ty - py * k]); }
  let alguna = false;
  for (const [x, y] of casillas) {
    if (!enMundo(x, y) || muroEn(x, y) || objetoEn(x, y)) continue;
    if (!INFO_SUELO[M.piso[idx(x, y)]].arable) continue;
    ponerSuelo(x, y, SUELO.ARADA); alguna = true;
    particulas(x * T + 8, y * T + 10, 6, ['#6a4a30', '#4a2e1e', '#8a5a36'], { vel: 35 });
  }
  if (alguna) { Sonido.sfx.picar(); ganarXP('jardineria', 1); } else aviso('Aquí no se puede arar');
}
function regar(tx, ty) {
  const p = J.jug, s = J.inv[J.sel], area = (s && ITEMS[s.id] && ITEMS[s.id].area) || 0;
  p.golpeT = 0.4;
  let alguno = false;
  for (let y = ty - area; y <= ty + area; y++) for (let x = tx - area; x <= tx + area; x++) {
    const o = objetoEn(x, y);
    if (!o || o.tipo !== 'cultivo') continue;
    o.datos.regado = true; alguno = true;
    particulas(x * T + 8, y * T + 8, 6, ['#6ab8ff', '#a8dcff', '#ffffff'], { vel: 25, alto: 10 });
  }
  if (alguno) { Sonido.sfx.recoger(0); ganarXP('jardineria', 0.5); } else aviso('Riega un cultivo');
}

function picar(tx, ty, pico) {
  const i = idx(tx, ty), m = M.muro[i], info = INFO_MURO[m], mena = M.mena[i];
  const cx = tx * T + 8, cy = ty * T + 4, ram = RAMPA[info.arte] || RAMPA.tierra;
  const req = Math.max(info.poder, mena ? INFO_MENA[mena].poder : 0);
  if (pico.poder < req) {
    Sonido.sfx.metal(); particulas(cx, cy, 4, ['#ffffff', ram[4]], { vel: 40, vida: 0.25 });
    aviso(req >= 999 ? `${info.nombre}: no se puede romper` : `Necesitas un pico con poder ${req} (el tuyo: ${pico.poder})`); return;
  }
  const vidaMax = info.vida * (mena ? INFO_MENA[mena].extra : 1);
  const d = M.danos.get(i) || { v: 0, t: 0 };
  const golpe = pico.poder * J.st.minado;
  d.v += golpe; d.t = J.t; M.danos.set(i, d);
  ganarXP('mineria', golpe * 0.05);
  Sonido.sfx.picar(); J.temblor = Math.max(J.temblor, 0.06);
  particulas(cx, cy, 4, [ram[2], ram[3], ram[1]], { vel: 45, vida: 0.35 });
  if (d.v >= vidaMax) {
    romperMuro(tx, ty);
    efecto('humo', 4, cx, cy + 10, 0, 0.45);
    Sonido.sfx.romper();
    particulas(cx, cy + 4, 12, [ram[1], ram[2], ram[3], ram[4]], { vel: 60, vida: 0.6, s: 2 });
    if (mena) { const im = INFO_MENA[mena]; soltar(cx, cy + 6, im.item, 1 + (Math.random() < 0.45 + J.st.suerte ? 1 : 0)); particulas(cx, cy, 6, MENA_COL[im.arte], { vel: 50 }); }
    else if (info.bloque && Math.random() < 0.55) soltar(cx, cy + 6, info.bloque, 1);
    if (m === MURO.TIERRA && Math.random() < 0.05) soltar(cx, cy + 6, 'madera', 1);
  }
}
function golpearObjeto(o, herr) {
  const info = OBJ[o.tipo], cx = o.x * T + 8, cy = o.y * T + 8;
  if (o.tipo === 'cultivo') { cosechar(o); return; }
  if (info.nivel && (herr.poder || 0) < info.nivel) { Sonido.sfx.metal(); aviso('Necesitas un pico mejor'); return; }
  if (o.tipo === 'cofre' && o.datos && o.datos.inv.some(Boolean)) { aviso('Vacía el cofre antes de quitarlo'); return; }
  o.vida -= info.planta || info.nivel || info.rompible ? Math.max(herr.poder || 0, herr.dano || 0) : 1;
  Sonido.sfx.golpe();
  particulas(cx, cy, 5, info.planta ? ['#8a5a36', '#c28a52', '#4f9a64'] : ['#9aa6b8', '#c28a52'], { vel: 40 });
  o.sacude = 0.15;
  if (info.nido && Math.random() < 0.35 && J.enemigos.length < 14) { const e = nuevaCriatura(info.nido, cx + (Math.random() - 0.5) * 16, cy + 8); if (!choca(e.x, e.y, e.hw, e.hh)) J.enemigos.push(e); }
  if (o.vida <= 0) {
    quitarObjeto(o.x, o.y);
    if (info.explota) estallido(cx, cy + 4, 0.7, 40, { quien: 'hongo' });
    if (info.suelta) soltarLista(cx, cy + 4, info.suelta);
    if (o.tipo === 'cama' && J.cama && J.cama.x === o.x && J.cama.y === o.y) J.cama = null;
    efecto('humo', 4, cx, cy + 6, 0, 0.4);
    particulas(cx, cy, 10, info.planta ? ['#8a5a36', '#c28a52', '#5aa06a'] : ['#c28a52', '#9aa6b8'], { vel: 55, s: 2 });
    if (o.tipo === 'vasija' || o.tipo === 'huevo_larva') Sonido.sfx.romper();
  }
}

function atacar(dano, alcance, angulo, o = {}) {
  const p = J.jug, fa = Math.atan2(p.fy, p.fx);
  let alguno = false;
  for (const e of todosEnemigos()) {
    if (e.vida <= 0 || e.enterrado || e.invisible) continue;
    const dx = e.x - p.x, dy = (e.y - (e.alto || 6)) - (p.y - 6), d = Math.hypot(dx, dy);
    if (d > alcance + e.radio) continue;
    const da = Math.abs(Math.atan2(Math.sin(Math.atan2(dy, dx) - fa), Math.cos(Math.atan2(dy, dx) - fa)));
    if (da > angulo && d > e.radio + 4) continue;
    herir(e, dano, dx / (d || 1), dy / (d || 1), { ...o, habilidad: 'combate' });
    alguno = true;
  }
  if (alguno) J.pausaGolpe = 0.045;
  // las vasijas y huevos se rompen al golpearlos aunque no apuntes
  const { tx, ty } = casillaMira(p), ob = objetoEn(tx, ty);
  if (ob && OBJ[ob.tipo].rompible) golpearObjeto(ob, { dano });
}
function herir(e, dano, kx, ky, o = {}) {
  const st = J.st || calcularStats();
  let real = Math.max(1, Math.round(dano * (e.estado === 'aturdido' ? 1.5 : 1) - (e.def || 0)));
  const critico = Math.random() < st.critico;
  if (critico) real = Math.round(real * 1.8);
  e.vida -= real; e.flash = 0.1; e.golpeadoT = J.t;
  efecto('chispazo', 3, e.x - kx * e.radio * 0.6, e.y + 1, e.jefe ? 20 : 7, 0.2);
  if (o.habilidad) ganarXP(o.habilidad, real * 0.25);
  if (!e.jefe && !CRIATURAS[e.tipo].fija) {
    const k = o.maza ? 220 : 115;
    e.kbx = kx * k; e.kby = ky * k;
    if (o.maza) { e.aturdido = 0.7; }
  } else J.temblor = Math.max(J.temblor, 0.08);
  if (o.veneno) e.veneno = { dano: o.veneno, t: 3 };
  numero(e.x, e.y, critico ? real + '!' : real, critico ? '#ffd166' : '#ffffff', e.jefe ? 44 : 16);
  particulas(e.x, e.y - 4, 5, e.colores || ['#ffffff'], { vel: 60, vida: 0.35 });
  Sonido.sfx.golpe();
  if (e.vida <= 0) {
    if (e.jefe) { e.estado = 'muriendo'; e.t = 1.8; Sonido.sfx.rugido(); return; }
    morirCriatura(e);
  }
}

// ---------------------------------------------------------------- disparos (arco y báculo)

function disparar(it) {
  const p = J.jug;
  let municion = null;
  if (!it.magico) {
    municion = ['flecha_carmesi', 'flecha_aguijon', 'flecha_cobre', 'flecha'].find((f) => J.cuenta(f)) || null;
    if (!municion) { aviso('No te quedan flechas'); p.golpeT = 0.4; return; }
    quitar(municion, 1);
  }
  const obj = enemigoCerca(p, 150, true);
  if (obj) mirarA(p, obj.x, obj.y - 2);
  p.golpeT = it.vel; p.swing = it.vel * 0.6; p.swingDur = it.vel * 0.6; p.swingItem = it.icono;
  const a0 = Math.atan2(p.fy, p.fx), vel = it.magico ? 150 : 230;
  const dano = (it.dano + (municion ? ITEMS[municion].dano : 0)) * J.st.danoDist, veneno = municion && ITEMS[municion].veneno;
  const n = it.multi || 1;
  for (let k = 0; k < n; k++) {
    const a = a0 + (k - (n - 1) / 2) * 0.2;
    J.disparos.push({ x: p.x + p.fx * 6, y: p.y - 1 + p.fy * 4, vx: Math.cos(a) * vel, vy: Math.sin(a) * vel, dano, vida: it.magico ? 2.2 : 1.1, magico: !!it.magico, obj: k === (n - 1) / 2 || n === 1 ? obj : null, municion: k ? null : municion, veneno });
  }
  Sonido.sfx.zas();
}
function actualizarDisparos(dt) {
  for (const d of J.disparos) {
    if (d.magico && d.obj && d.obj.vida > 0) { // el orbe busca a su presa
      const a = Math.atan2(d.obj.y - 4 - d.y, d.obj.x - d.x), v = Math.hypot(d.vx, d.vy), b = Math.atan2(d.vy, d.vx);
      const g = b + Math.atan2(Math.sin(a - b), Math.cos(a - b)) * Math.min(1, dt * 5);
      d.vx = Math.cos(g) * v; d.vy = Math.sin(g) * v;
    }
    d.x += d.vx * dt; d.y += d.vy * dt; d.vida -= dt;
    if (solido(Math.floor(d.x / T), Math.floor(d.y / T), true)) {
      d.vida = 0;
      particulas(d.x, d.y, 4, d.magico ? ['#c080ff', '#ffffff'] : ['#c28a52', '#9aa6b8'], { vel: 30 });
      if (!d.magico && d.municion && Math.random() < 0.5) soltar(d.x - d.vx * 0.02, d.y - d.vy * 0.02, d.municion, 1);
      continue;
    }
    for (const e of todosEnemigos()) {
      if (e.vida <= 0 || e.enterrado || e.invisible) continue;
      if (Math.hypot(e.x - d.x, e.y - 4 - d.y) < e.radio + 3) {
        const v = Math.hypot(d.vx, d.vy) || 1;
        herir(e, d.dano, d.vx / v, d.vy / v, { habilidad: 'distancia', veneno: d.veneno });
        d.vida = 0;
        break;
      }
    }
  }
  J.disparos = J.disparos.filter((d) => d.vida > 0);
}

// ---------------------------------------------------------------- botín, partículas y paredes que se curan

function actualizarMundoVivo(dt) {
  const p = J.jug;
  for (const b of J.botines) {
    b.t += dt;
    if (b.z > 0 || b.vz > 0) { b.vz -= 300 * dt; b.z = Math.max(0, b.z + b.vz * dt); b.x += b.vx * dt; b.y += b.vy * dt; if (b.z === 0) b.vz = b.vz < -40 ? -b.vz * 0.35 : 0; }
    const d = Math.hypot(p.x - b.x, p.y - 4 - b.y);
    if (b.t > 0.35 && d < 38 && J.estado === 'juego') { const k = Math.min(1, dt * 9); b.x += (p.x - b.x) * k; b.y += (p.y - 4 - b.y) * k; }
    if (b.t > 0.35 && d < 8 && J.estado === 'juego') {
      const sobra = agregar(b.id, 1);
      if (sobra) { if (!J.llenoT || J.t - J.llenoT > 3) { aviso('La mochila está llena'); J.llenoT = J.t; } b.t = -1; continue; }
      b.recogido = true; J.racha = J.t - (J.rachaT || 0) < 0.6 ? (J.racha || 0) + 1 : 0; J.rachaT = J.t;
      Sonido.sfx.recoger(J.racha); avisoRecogido(b.id, 1);
      J.hito['tiene_' + b.id] = true;
    }
  }
  J.botines = J.botines.filter((b) => !b.recogido);
  if (J.botines.length > 300) J.botines.splice(0, J.botines.length - 300);
  for (const q of J.particulas) {
    q.vida -= dt; q.x += q.vx * dt; q.y += q.vy * dt;
    if (q.g) { q.vz -= q.g * dt; q.z += q.vz * dt; if (q.z < 0) { q.z = 0; q.vz *= -0.3; q.vx *= 0.5; q.vy *= 0.5; } }
    else q.y -= 10 * dt;
  }
  J.particulas = J.particulas.filter((q) => q.vida > 0);
  if (J.particulas.length > 500) J.particulas.splice(0, J.particulas.length - 500);
  for (const n of J.numeros) { n.vida -= dt; n.alto += 18 * dt; }
  J.numeros = J.numeros.filter((n) => n.vida > 0);
  for (const e of J.efectos) e.t += dt;
  J.efectos = J.efectos.filter((e) => e.t < e.dur);
  if (J.jug && J.jug.andando && J.estado === 'juego') { J.pasoT = (J.pasoT || 0) + dt; if (J.pasoT > 0.32) { J.pasoT = 0; efecto('polvo', 3, J.jug.x + (Math.random() - 0.5) * 4, J.jug.y + 1, 0, 0.35); } }
  for (const a of J.avisos) a.t -= dt;
  J.avisos = J.avisos.filter((a) => a.t > 0);
  for (const [i, d] of M.danos) if (J.t - d.t > 4) M.danos.delete(i);
  J.temblor = Math.max(0, J.temblor - dt);
  for (const o of M.objetos.values()) if (o.sacude > 0) o.sacude -= dt;
}
