'use strict';
// Arranque, bucle principal, cámara, dibujo del mundo por filas (para que las paredes tapen bien),
// la luz encima, y guardar/cargar la partida.

const lienzo = document.getElementById('pantalla');
const ctx = lienzo.getContext('2d');
let LW = 320, LH = 180, ESC = 1, hayPartida = false;
const CLAVE = 'corazon-dormido-v1';

function ajustar() {
  const dpr = window.devicePixelRatio || 1;
  const wd = Math.round(window.innerWidth * dpr), hd = Math.round(window.innerHeight * dpr);
  ESC = Math.max(1, Math.round(Math.min(wd, hd) / 220));
  LW = Math.floor(wd / ESC); LH = Math.floor(hd / ESC);
  lienzo.width = LW; lienzo.height = LH;
  lienzo.style.width = (LW * ESC) / dpr + 'px'; lienzo.style.height = (LH * ESC) / dpr + 'px';
  ctx.imageSmoothingEnabled = false;
}

// ---------------------------------------------------------------- guardar y cargar

function guardarPartida() {
  if (!M || !J.jug || (J.estado !== 'juego' && J.estado !== 'muerto')) return;
  const p = J.jug;
  try {
    localStorage.setItem(CLAVE, JSON.stringify({
      v: 1, mundo: datosMundo(), jug: { x: p.x, y: p.y, vida: Math.max(1, p.vida), hambre: p.hambre },
      inv: J.inv, sel: J.sel, armadura: J.armadura, hito: J.hito, objetivo: J.objetivo, latidos: J.latidos,
      jefeMuerto: !!(J.jefe && J.jefe.estado === 'muerto'),
    }));
    hayPartida = true;
  } catch (e) { /* sin almacenamiento: la partida dura lo que la pestaña */ }
  J.guardadoT = J.t;
}
function leerPartida() {
  try { const s = localStorage.getItem(CLAVE); return s ? JSON.parse(s) : null; } catch (e) { return null; }
}

function empezar(continuar) {
  const g = continuar ? leerPartida() : null;
  J.enemigos = []; J.botines = []; J.proyectiles = []; J.particulas = []; J.numeros = []; J.avisos = [];
  J.jug = nuevoJugador();
  if (g) {
    cargarMundo(g.mundo);
    Object.assign(J.jug, g.jug);
    J.inv = g.inv.concat(new Array(N_INV).fill(null)).slice(0, N_INV); J.sel = g.sel || 0; J.armadura = g.armadura || null;
    J.hito = g.hito || {}; J.objetivo = g.objetivo || 0; J.latidos = g.latidos || 0;
  } else {
    generarMundo((Math.random() * 1e9) | 0);
    J.inv = new Array(N_INV).fill(null); J.sel = 0; J.armadura = null; J.hito = {}; J.objetivo = 0; J.latidos = 0;
    for (const [id, n] of INICIO) agregar(id, n);
  }
  crearJefe();
  if (g && g.jefeMuerto) J.jefe.estado = 'muerto';
  J.estado = 'juego'; J.panel = null; J.aparecer = 4;
  J.cam.x = J.jug.x - LW / 2; J.cam.y = J.jug.y - LH / 2;
  FLUJO.t = 0;
  if (!g) { aviso('El Corazón duerme. Despiértalo.', null, '#ffb3c8'); setTimeout(() => aviso('Rompe raíces para sacar madera'), 2500); }
  guardarPartida();
}
function irTitulo() {
  J.estado = 'titulo'; J.panel = null; hayPartida = !!leerPartida();
}
function revisarObjetivos() {
  while (J.objetivo < OBJETIVOS.length && OBJETIVOS[J.objetivo].hecho(J)) {
    aviso('¡Hecho! ' + OBJETIVOS[J.objetivo].txt, null, '#7dff9a');
    J.objetivo++;
    if (J.objetivo < OBJETIVOS.length) Sonido.sfx.nivel();
    if (J.objetivo === 6 && M.arena) {
      const a = Math.atan2(M.arena.y - CENTRO, M.arena.x - CENTRO), k = Math.round(((a + Math.PI * 2) % (Math.PI * 2)) / (Math.PI / 4)) % 8;
      const rumbos = ['este', 'sureste', 'sur', 'suroeste', 'oeste', 'noroeste', 'norte', 'noreste'];
      setTimeout(() => aviso(`Se oye escarbar al ${rumbos[k]} del Corazón (mira el mapa)`, 'ui_calavera', '#ffd166'), 1800);
    }
  }
}

// ---------------------------------------------------------------- bucle

let ultimo = 0;
function cuadro(ms) {
  const dt = Math.min(0.05, Math.max(0, (ms - ultimo) / 1000)); ultimo = ms;
  J.t += dt;
  if (J.estado === 'juego' && !J.panel) {
    leerEntrada();
    actualizarJugador(dt); actualizarCriaturas(dt); actualizarJefe(dt); actualizarProyectiles(dt); actualizarMundoVivo(dt);
    revisarObjetivos();
    if (J.t - J.guardadoT > 25) guardarPartida();
  } else if (J.estado === 'muerto') actualizarMundoVivo(dt);
  moverCamara(dt);
  dibujar();
  requestAnimationFrame(cuadro);
}
function moverCamara(dt) {
  let tx, ty;
  if (J.estado === 'titulo') { tx = CENTRO * T + 8 + Math.cos(J.t * 0.12) * 50 - LW / 2; ty = CENTRO * T + Math.sin(J.t * 0.12) * 30 - LH / 2; }
  else { tx = J.jug.x - LW / 2; ty = J.jug.y - 10 - LH / 2; }
  const k = Math.min(1, dt * 7);
  J.cam.x += (tx - J.cam.x) * k; J.cam.y += (ty - J.cam.y) * k;
}

// ---------------------------------------------------------------- dibujo del mundo

const ADORNOS = { 1: ['ad_piedras', 'ad_raices', 'ad_hierba'], 2: ['ad_piedras', 'ad_raices', 'ad_huesos'], 3: ['ad_piedras', 'ad_hongos', 'ad_piedras'], 4: ['ad_hierba', 'ad_piedras'], 5: ['ad_huesos', 'ad_piedras', 'ad_raices'] };
const AMBIENTE = [0.05, 0.045, 0.075];

function sombra(x, y, w) {
  ctx.fillStyle = 'rgba(8,4,12,0.45)';
  ctx.fillRect(Math.round(x - w / 2), Math.round(y - 1), w, 2);
  ctx.fillRect(Math.round(x - w / 2 + 1), Math.round(y - 2), w - 2, 4);
}

function fuentesLuz(tx0, ty0, tx1, ty1) {
  const f = [];
  if (J.estado !== 'titulo') f.push({ x: J.jug.x, y: J.jug.y - 8, r: 5.5, c: [1.0, 0.88, 0.66], k: 1 });
  for (const i of M.luces) {
    const o = M.objetos.get(i);
    if (!o || o.x < tx0 - 12 || o.x > tx1 + 12 || o.y < ty0 - 12 || o.y > ty1 + 12) continue;
    const info = OBJ[o.tipo], x = o.x * T + 8, y = o.y * T + 8;
    if (o.tipo === 'corazon') f.push({ x, y: y - 8, r: 6 + J.latidos * 3, c: [1.0, 0.45, 0.62], k: 0.75 + 0.25 * Math.sin(J.t * 2.2) });
    else if (o.tipo === 'pedestal') { if (o.extra) f.push({ x, y, r: 3.5, c: [1, 0.4, 0.6], k: 1 }); }
    else if (info.luz) {
      const [r, cr, cg, cb] = info.luz;
      f.push({ x, y, r, c: [cr, cg, cb], k: info.parpadeo ? 0.9 + 0.1 * Math.sin(J.t * 13 + i) * Math.sin(J.t * 7.3 + i * 0.7) : 1 });
    }
  }
  return f;
}

function dibujarObjeto(o, brillo) {
  const info = OBJ[o.tipo], x = o.x * T + 8 + (o.sacude > 0 ? Math.sin(J.t * 80) * 1.5 : 0), pie = o.y * T + 15;
  let n = info.arte;
  if (o.tipo === 'antorcha') n = `antorcha_${Math.floor(J.t * 7 + o.x * 3 + o.y) % 3}`;
  else if (o.tipo === 'horno') n = `horno_${Math.floor(J.t * 5) % 2}`;
  else if (o.tipo === 'pedestal') n = o.extra ? 'pedestal_lleno' : 'pedestal';
  if (o.tipo === 'corazon') {
    const flota = Math.round(Math.sin(J.t * 1.6) * 2), cor = J.latidos ? 'corazon' : 'corazon_dormido';
    if (brillo) { ctx.globalAlpha = 0.45 + 0.35 * Math.sin(J.t * 2.2) * (J.latidos ? 1 : 0.5); sprPie(ctx, cor, x, pie - 16 + flota); ctx.globalAlpha = 1; return; }
    sprPie(ctx, 'altar', x, pie + 2);
    sprPie(ctx, cor, x, pie - 16 + flota);
    return;
  }
  if (brillo) {
    if (!info.brillo && !(o.tipo === 'pedestal' && o.extra)) return;
    ctx.globalAlpha = 0.85; sprPie(ctx, n + '_luz', x, pie); ctx.globalAlpha = 1;
    return;
  }
  sprPie(ctx, n, x, pie);
}

function nombreJugador(p) {
  const f = p.andando ? Math.floor(p.anim) % 4 : 0;
  return `${p.flash > 0 ? 'jugB' : 'jug'}_${p.dir}_${f}`;
}
function dibujarJugador(p) {
  if (J.estado === 'titulo' || J.estado === 'muerto') return;
  sombra(p.x, p.y + 2, 10);
  if (p.invuln > 0 && Math.floor(J.t * 20) % 2) ctx.globalAlpha = 0.45;
  const delante = p.dir !== 'ar';
  if (!delante) dibujarHerramienta(p);
  sprPie(ctx, nombreJugador(p), p.x, p.y + 3);
  if (delante) dibujarHerramienta(p);
  ctx.globalAlpha = 1;
}
function dibujarHerramienta(p) {
  if (p.swing <= 0 || !p.swingItem) return;
  const r = SPR[p.swingItem];
  if (!r) return;
  const k = 1 - p.swing / p.swingDur, base = Math.atan2(p.fy, p.fx);
  const lado = p.fx >= 0 ? 1 : -1, ang = base + lado * (-1.3 + k * 2.6);
  ctx.save();
  ctx.translate(Math.round(p.x + p.fx * 3), Math.round(p.y - 7 + p.fy * 2));
  ctx.rotate(ang + Math.PI / 4);
  ctx.drawImage(ATLAS, r.x, r.y, r.w, r.h, -2, -r.h + 2, r.w, r.h);
  ctx.restore();
}
function dibujarCriatura(e) {
  const B = e.flash > 0 ? 'B' : '';
  if (e.tipo === 'babosa') {
    sombra(e.x, e.y + 1, 10);
    const f = e.z > 0 ? 0 : e.aplasta > 0 ? 1 : Math.floor(e.t * 3) % 2;
    sprPie(ctx, `babosa_${f}${B ? '_B' : ''}`, e.x, e.y + 2 - e.z);
  } else if (e.tipo === 'larva') {
    sombra(e.x, e.y + 1, 14);
    sprPie(ctx, `larva_${Math.floor(e.t * 6) % 2}${e.mira < 0 ? '_i' : ''}${B ? (e.mira < 0 ? 'B' : '_B') : ''}`, e.x, e.y + 2);
  } else if (e.tipo === 'escarabajo') {
    sombra(e.x, e.y + 1, 12);
    const tiembla = e.estado === 'prepara' ? Math.sin(J.t * 60) : 0;
    sprPie(ctx, `escarabajo_${Math.floor(e.t * (e.estado === 'carga' ? 16 : 8)) % 2}${B || e.estado === 'prepara' && Math.floor(J.t * 16) % 2 ? '_B' : ''}`, e.x + tiembla, e.y + 2);
  }
}
function dibujarJefe(j) {
  if (j.estado === 'muerto') return;
  if (j.enterrado) {
    const tiembla = j.t < 0.8 ? Math.sin(J.t * 50) * 1.5 : 0;
    if (j.t < 0.8) { ctx.fillStyle = 'rgba(255,60,60,0.25)'; circulo(ctx, j.x, j.y, 30); }
    sprPie(ctx, `monticulo_${Math.floor(J.t * 8) % 2}`, j.x + tiembla, j.y + 3);
    return;
  }
  sombra(j.x, j.y + 2, 28);
  const brazos = j.estado === 'rocas' || j.estado === 'embiste' || j.estado === 'despierta' ? Math.floor(J.t * 10) % 2 : Math.floor(J.t * 2) % 2;
  const tiembla = j.estado === 'embiste' || j.estado === 'muriendo' ? Math.sin(J.t * 60) * 1.5 : 0;
  const rojo = j.estado === 'embiste' && Math.floor(J.t * 12) % 2;
  sprPie(ctx, `topo_${brazos}${j.flash > 0 || rojo ? '_B' : ''}`, j.x + tiembla, j.y + 3);
  if (j.estado === 'dormido') {
    const k = (J.t * 0.6) % 1;
    ctx.globalAlpha = 1 - k; texto(ctx, 'Z', j.x + 10 + k * 6, j.y - 34 - k * 14, '#e8e0f0', { borde: COL.negro }); ctx.globalAlpha = 1;
  }
}

function dibujarMundo() {
  const sx = J.temblor > 0 ? (Math.random() - 0.5) * J.temblor * 8 : 0, sy = J.temblor > 0 ? (Math.random() - 0.5) * J.temblor * 8 : 0;
  const camX = Math.round(J.cam.x + sx), camY = Math.round(J.cam.y + sy);
  const tx0 = Math.floor(camX / T) - 1, ty0 = Math.floor(camY / T) - 1, tx1 = Math.floor((camX + LW) / T) + 1, ty1 = Math.floor((camY + LH) / T) + 2;
  ctx.fillStyle = '#000'; ctx.fillRect(0, 0, LW, LH);
  ctx.save(); ctx.translate(-camX, -camY);

  // suelos
  for (let ty = ty0; ty <= ty1; ty++) for (let tx = tx0; tx <= tx1; tx++) {
    if (!enMundo(tx, ty)) continue;
    const i = idx(tx, ty);
    if (M.muro[i]) continue;
    const h = hash2(tx, ty), b = M.piso[i];
    spr(ctx, `s_${SUELO_DE[b]}_${Math.floor(h * 4)}`, tx * T, ty * T);
    const h2 = hash2(tx, ty, 3);
    if (h2 < 0.1 && !M.objetos.has(i)) { const ad = ADORNOS[b]; spr(ctx, ad[Math.floor(h2 * 1000) % ad.length], tx * T + 2 + Math.floor(h * 8), ty * T + 3 + (Math.floor(h2 * 997) % 8)); }
    if (muroEn(tx, ty - 1)) { ctx.fillStyle = 'rgba(6,3,10,0.45)'; ctx.fillRect(tx * T, ty * T, T, 3); ctx.fillStyle = 'rgba(6,3,10,0.2)'; ctx.fillRect(tx * T, ty * T + 3, T, 3); }
  }

  // cosas por fila: paredes, objetos y criaturas, de arriba abajo
  const filas = new Map();
  const meter = (fy, f) => { if (!filas.has(fy)) filas.set(fy, []); filas.get(fy).push(f); };
  const objetosVista = [];
  for (let ty = ty0; ty <= ty1 + 2; ty++) for (let tx = tx0 - 1; tx <= tx1 + 1; tx++) {
    if (!enMundo(tx, ty)) continue;
    const o = M.objetos.get(idx(tx, ty));
    if (o) { objetosVista.push(o); meter(ty, { y: ty * T + 15, f: () => dibujarObjeto(o, false) }); }
  }
  if (J.estado !== 'titulo') meter(Math.floor(J.jug.y / T), { y: J.jug.y, f: () => dibujarJugador(J.jug) });
  for (const e of J.enemigos) meter(Math.floor(e.y / T), { y: e.y, f: () => dibujarCriatura(e) });
  if (J.jefe && J.estado !== 'titulo') meter(Math.floor(J.jefe.y / T), { y: J.jefe.y, f: () => dibujarJefe(J.jefe) });
  for (const b of J.botines) meter(Math.floor(b.y / T), { y: b.y, f: () => { sombra(b.x, b.y + 1, 6); sprPie(ctx, ITEMS[b.id].icono, b.x, b.y - b.z + Math.sin(b.t * 4) * 1); } });
  for (const r of J.proyectiles) meter(Math.floor(r.y / T), { y: r.y, f: () => { sombra(r.x, r.y + 6, 5); sprCentro(ctx, 'roca_lanzada', r.x, r.y - 4); } });
  for (const q of J.particulas) if (!q.brilla) meter(Math.floor(q.y / T), { y: q.y, f: () => { ctx.fillStyle = q.c; ctx.fillRect(Math.round(q.x), Math.round(q.y - q.z), q.s, q.s); } });

  for (let ty = ty0; ty <= ty1 + 2; ty++) {
    for (let tx = tx0; tx <= tx1; tx++) {
      const m = muroEn(tx, ty);
      if (!m || !enMundo(tx, ty)) continue;
      const i = idx(tx, ty), arte = INFO_MURO[m].arte, v = hash2(tx, ty, 1) < 0.5 ? 0 : 1;
      const N = !muroEn(tx, ty - 1), E = !muroEn(tx + 1, ty), S = !muroEn(tx, ty + 1), O = !muroEn(tx - 1, ty);
      spr(ctx, `m_${arte}_${N | (E << 1) | (S << 2) | (O << 3)}_${v}`, tx * T, ty * T - 8);
      if (S) spr(ctx, `c_${arte}_${(O ? 1 : 0) | (E ? 2 : 0)}_${v}`, tx * T, ty * T + 8);
      const mena = M.mena[i];
      if (mena) { spr(ctx, `mena_${INFO_MENA[mena].arte}_${v}`, tx * T, ty * T - 8); if (S) spr(ctx, `menac_${INFO_MENA[mena].arte}_${v}`, tx * T, ty * T + 8); }
      const d = M.danos.get(i);
      if (d) { const vm = INFO_MURO[m].vida * (mena ? INFO_MENA[mena].extra : 1); spr(ctx, `grieta_${Math.min(3, 1 + Math.floor((d.v / vm) * 3))}`, tx * T, ty * T - 8); }
    }
    const fila = filas.get(ty);
    if (fila) { fila.sort((a, b) => a.y - b.y); for (const c of fila) c.f(); }
  }
  // marca de la casilla a la que apuntas
  if (J.estado === 'juego' && !J.panel) {
    const s = J.inv[J.sel], it = s && ITEMS[s.id];
    const { tx, ty } = casillaMira(J.jug);
    const muro = muroEn(tx, ty), colocar = it && it.tipo === 'colocable';
    if ((muro && muro !== MURO.ROCA && muro !== MURO.RUINA && !enemigoCerca(J.jug, 30)) || (colocar && puedeColocar(tx, ty))) {
      const y0 = muro ? ty * T - 8 : ty * T, a = 0.55 + 0.35 * Math.sin(J.t * 8);
      ctx.fillStyle = colocar ? `rgba(140,255,170,${a})` : `rgba(255,246,200,${a})`;
      for (const [x, y, w, h] of [[0, 0, 4, 1], [0, 0, 1, 4], [12, 0, 4, 1], [15, 0, 1, 4], [0, 15, 4, 1], [0, 12, 1, 4], [12, 15, 4, 1], [15, 12, 1, 4]]) ctx.fillRect(tx * T + x, y0 + y, w, h);
    }
  }
  ctx.restore();

  // luz
  const vx0 = Math.floor(camX / T), vy0 = Math.floor(camY / T), vw = Math.ceil(LW / T) + 1, vh = Math.ceil(LH / T) + 2;
  const luz = calcularLuz(vx0, vy0, vw, vh, fuentesLuz(vx0, vy0, vx0 + vw, vy0 + vh), AMBIENTE);
  ctx.globalCompositeOperation = 'multiply';
  ctx.imageSmoothingEnabled = true;
  ctx.drawImage(luz, 0, 0, vw + 2, vh + 2, (vx0 - 1) * T - camX, (vy0 - 1) * T - camY, (vw + 2) * T, (vh + 2) * T);
  ctx.imageSmoothingEnabled = false;
  ctx.globalCompositeOperation = 'source-over';

  // lo que brilla solo, encima de la oscuridad
  ctx.save(); ctx.translate(-camX, -camY);
  for (const o of objetosVista) dibujarObjeto(o, true);
  for (const q of J.particulas) if (q.brilla) { ctx.fillStyle = q.c; ctx.fillRect(Math.round(q.x), Math.round(q.y - q.z), q.s, q.s); }
  for (const n of J.numeros) { ctx.globalAlpha = Math.min(1, n.vida * 3); texto(ctx, n.txt, n.x, n.y, n.c, { alin: 'c', borde: COL.negro }); }
  ctx.globalAlpha = 1;
  ctx.restore();
}

function dibujar() {
  UI.botones = [];
  if (!M) { ctx.fillStyle = '#000'; ctx.fillRect(0, 0, LW, LH); return; }
  dibujarMundo();
  if (J.estado === 'titulo') dibujarTitulo(ctx, hayPartida);
  else { dibujarHUD(ctx); if (J.panel) dibujarPanel(ctx); }
}

// ---------------------------------------------------------------- arranque

function iniciar(datosHot) {
  construirArte();
  ajustar();
  window.addEventListener('resize', ajustar);
  lienzo.addEventListener('pointerdown', (e) => { if (e.pointerType === 'mouse') UI.raton = true; alTocar(e); });
  window.addEventListener('pointermove', alMover);
  window.addEventListener('pointerup', alSoltar);
  window.addEventListener('pointercancel', alSoltar);
  window.addEventListener('keydown', alTecla);
  window.addEventListener('keyup', (e) => UI.teclas.delete(e.code));
  window.addEventListener('blur', () => UI.teclas.clear());
  lienzo.addEventListener('contextmenu', (e) => e.preventDefault());
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') { guardarPartida(); if (J.estado === 'juego' && !J.panel) J.panel = 'pausa'; }
  });
  const g = leerPartida();
  hayPartida = !!g;
  if (g) cargarMundo(g.mundo); else generarMundo(20261028);
  J.jug = nuevoJugador();
  J.cam.x = CENTRO * T - LW / 2; J.cam.y = CENTRO * T - LH / 2;
  if (datosHot && datosHot.enJuego && hayPartida) empezar(true);
  requestAnimationFrame((ms) => { ultimo = ms; cuadro(ms); });
}

try { window.claude?.hot?.snapshot?.(() => { guardarPartida(); return { enJuego: J.estado === 'juego' }; }); } catch (e) { /* fuera del visor */ }
if (window.claude?.hot?.ready) window.claude.hot.ready(iniciar); else iniciar(window.claude?.hot?.data ?? {});
