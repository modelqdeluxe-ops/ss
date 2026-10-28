'use strict';
// Arranque, bucle principal, cámara y guardado. El mundo se dibuja en 3D (gl.js) y la interfaz en un
// lienzo 2D encima, en baja resolución y escalado sin suavizar.

const lienzo3d = document.getElementById('mundo3d');
const lienzo = document.getElementById('pantalla');
const ctx = lienzo.getContext('2d');
let LW = 320, LH = 180, ESC = 1, hayPartida = false;
const CLAVE = 'corazon-dormido-v1';

function ajustar() {
  const dpr = window.devicePixelRatio || 1, wc = window.innerWidth, hc = window.innerHeight;
  const wd = Math.round(wc * dpr), hd = Math.round(hc * dpr);
  ESC = Math.max(1, Math.round(Math.min(wd, hd) / 220));
  LW = Math.floor(wd / ESC); LH = Math.floor(hd / ESC);
  lienzo.width = LW; lienzo.height = LH;
  lienzo.style.width = wc + 'px'; lienzo.style.height = hc + 'px';
  ctx.imageSmoothingEnabled = false;
  GL.ajustar(wc, hc, dpr);
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
  J.cam.x = J.jug.x; J.cam.y = J.jug.y;
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
    ambientePart(dt);
    revisarObjetivos();
    if (J.t - J.guardadoT > 25) guardarPartida();
  } else if (J.estado === 'muerto') actualizarMundoVivo(dt);
  else if (J.estado === 'titulo') { ambientePart(dt); actualizarMundoVivo(dt); }
  moverCamara(dt);
  dibujar();
  requestAnimationFrame(cuadro);
}
function moverCamara(dt) {
  let tx, ty;
  if (J.estado === 'titulo') { tx = CENTRO * T + 8 + Math.cos(J.t * 0.12) * 40; ty = CENTRO * T + 20 + Math.sin(J.t * 0.12) * 24; }
  else { tx = J.jug.x; ty = J.jug.y - 6; }
  const k = Math.min(1, dt * 7);
  J.cam.x += (tx - J.cam.x) * k; J.cam.y += (ty - J.cam.y) * k;
}

// Chispas de las antorchas y motas en el aire, como en Core Keeper.
function ambientePart(dt) {
  if (!J.vistaObj) return;
  for (const o of J.vistaObj) {
    if ((o.tipo === 'antorcha' && Math.random() < dt * 2.2) || (o.tipo === 'horno' && Math.random() < dt * 3)) {
      J.particulas.push({ x: o.x * T + 8 + (Math.random() - 0.5) * 4, y: o.y * T + 15, z: 14, vx: (Math.random() - 0.5) * 8, vy: 0, vz: 10 + Math.random() * 14, g: -6, vida: 1.2 + Math.random(), c: Math.random() < 0.5 ? '#ffb040' : '#ff7020', s: 1, brilla: true, chispa: true });
    }
    if ((o.tipo === 'cristal' || o.tipo === 'hongo' || o.tipo === 'arbusto') && Math.random() < dt * 0.6) {
      J.particulas.push({ x: o.x * T + 8 + (Math.random() - 0.5) * 12, y: o.y * T + 10 + (Math.random() - 0.5) * 8, z: 4, vx: (Math.random() - 0.5) * 4, vy: 0, vz: 5 + Math.random() * 5, g: -2, vida: 2 + Math.random() * 2, c: '#7ff0ff', s: 1, brilla: true, chispa: true });
    }
  }
  if (J.estado === 'juego' && Math.random() < dt * 3) {
    const p = J.jug;
    J.particulas.push({ x: p.x + (Math.random() - 0.5) * 120, y: p.y + (Math.random() - 0.5) * 80, z: Math.random() * 20, vx: (Math.random() - 0.5) * 3, vy: 0, vz: 1 + Math.random() * 2, g: -0.5, vida: 3 + Math.random() * 3, c: '#fff0d0', s: 1, brilla: true, chispa: true, tenue: true });
  }
}

// ---------------------------------------------------------------- mundo 3D

const AMBIENTE = [0.035, 0.03, 0.055];

function fuentesLuz(v) {
  const f = [];
  if (J.estado !== 'titulo' && J.estado !== 'muerto') f.push({ x: J.jug.x, y: J.jug.y - 2, r: 6, c: [1.0, 0.86, 0.64], k: 1.05 });
  for (const i of M.luces) {
    const o = M.objetos.get(i);
    if (!o || o.x < v.x0 - 12 || o.x > v.x1 + 12 || o.y < v.z0 - 12 || o.y > v.z1 + 12) continue;
    const info = OBJ[o.tipo], x = o.x * T + 8, y = o.y * T + 8;
    if (o.tipo === 'corazon') f.push({ x, y: y + 4, r: 7 + J.latidos * 3, c: [1.0, 0.42, 0.6], k: 0.8 + 0.2 * Math.sin(J.t * 2.2) });
    else if (o.tipo === 'pedestal') { if (o.extra) f.push({ x, y, r: 3.5, c: [1, 0.4, 0.6], k: 1 }); }
    else if (info.luz) {
      const [r, cr, cg, cb] = info.luz;
      f.push({ x, y, r, c: [cr, cg, cb], k: info.parpadeo ? 1.05 + 0.1 * Math.sin(J.t * 13 + i) * Math.sin(J.t * 7.3 + i * 0.7) : 1 });
    }
  }
  if (J.jefe && J.jefe.estado === 'aturdido') f.push({ x: J.jefe.x, y: J.jefe.y - 20, r: 2.5, c: [1, 0.9, 0.5], k: 0.6 });
  return f;
}

const HALO = { antorcha: [1.1, [1, 0.6, 0.25], 0.32], horno: [1.0, [1, 0.5, 0.2], 0.28], lampara: [1.5, [0.6, 0.9, 1], 0.3], cristal: [1.1, [0.3, 0.85, 1], 0.24], hongo: [0.9, [0.3, 0.85, 1], 0.2], arbusto: [0.8, [0.3, 0.6, 1], 0.16] };
const ALTURA_HALO = { antorcha: 1.05, horno: 0.5, lampara: 1.0, cristal: 0.55, hongo: 0.45, arbusto: 0.3 };

function dibujarObjeto3d(o) {
  const info = OBJ[o.tipo], x = o.x + 0.5 + (o.sacude > 0 ? Math.sin(J.t * 80) * 0.08 : 0), z = o.y + 0.86;
  let n = info.arte;
  if (o.tipo === 'antorcha') n = `antorcha_${Math.floor(J.t * 8 + o.x * 3 + o.y) % 4}`;
  else if (o.tipo === 'horno') n = `horno_${Math.floor(J.t * 5) % 2}`;
  else if (o.tipo === 'pedestal') n = o.extra ? 'pedestal_lleno' : 'pedestal';
  if (o.tipo === 'corazon') {
    const flota = Math.sin(J.t * 1.6) * 0.08, cor = J.latidos ? 'corazon' : 'corazon_dormido';
    GL.sprite('altar', x, z + 0.4, {});
    GL.sprite(cor, x, z - 0.2, { y: 0.9 + flota, unlit: 0.35 });
    GL.sprite(cor, x, z - 0.19, { y: 0.9 + flota, unlit: 1, lote: GL.lotes.brillo, tinte: [1, 1, 1, (J.latidos ? 0.5 : 0.2) + 0.2 * Math.sin(J.t * 2.2)] });
    GL.halo(x, 1.6 + flota, z - 0.2, 2.6 + J.latidos * 0.6, [1, 0.35, 0.55], 0.28 + 0.08 * Math.sin(J.t * 2.2));
    return;
  }
  if (o.tipo === 'raiz') GL.plano('raiz_suelo', x, z - 0.3, { unlit: 0, tinte: [0.85, 0.85, 0.85, 1] });
  if (info.bloquea || o.tipo === 'antorcha' || o.tipo === 'lampara') GL.plano(o.tipo === 'antorcha' ? 'sombra_s' : 'sombra_m', x, z - 0.05, { tinte: [1, 1, 1, 0.7] });
  GL.sprite(n, x, z, {});
  if (info.brillo || (o.tipo === 'pedestal' && o.extra)) {
    GL.sprite(n + '_luz', x, z + 0.002, { unlit: 1, lote: GL.lotes.brillo, tinte: [1, 1, 1, 0.8] });
    const h = HALO[o.tipo];
    if (h) GL.halo(x, ALTURA_HALO[o.tipo], z, h[0], h[1], h[2] * (info.parpadeo ? 0.85 + 0.15 * Math.sin(J.t * 11 + o.x) : 1));
  }
}

function nombreJugador(p) {
  const f = p.andando ? Math.floor(p.anim) % 4 : 0;
  return `${p.flash > 0 ? 'jugB' : 'jug'}_${p.dir}_${f}`;
}
function dibujarJugador3d(p) {
  const x = p.x / T, z = p.y / T + 0.06;
  GL.plano('sombra_s', x, z - 0.04, { tinte: [1, 1, 1, 0.75] });
  const parpadea = p.invuln > 0 && Math.floor(J.t * 20) % 2;
  if (!parpadea) GL.sprite(nombreJugador(p), x, z, { blanco: p.flash > 0 ? 0 : 0 });
  if (p.swing > 0 && p.swingItem) dibujarHerramienta3d(p, x, z);
}
// La herramienta gira alrededor de la mano, en el plano del sprite.
function dibujarHerramienta3d(p, x, z) {
  const r = SPR[p.swingItem];
  if (!r) return;
  const k = 1 - p.swing / p.swingDur, lado = p.fx >= 0 ? 1 : -1;
  const delante = p.dir !== 'ar';
  // ángulo en el plano de la pantalla: de arriba por detrás a abajo por delante
  const base = p.dir === 'ar' ? -Math.PI / 2 : p.dir === 'ab' ? Math.PI / 2 : p.fx > 0 ? 0 : Math.PI;
  const ang = base + lado * (-1.4 + k * 2.4);
  const px = 16, cx = x + (p.dir === 'de' ? 0.25 : p.dir === 'iz' ? -0.25 : 0.12 * lado), cy = 7 / px * GL.KY;
  const rot = ang + Math.PI / 4, c = Math.cos(rot), s = Math.sin(rot);
  const pts = [[-2, -r.h + 2], [r.w - 2, -r.h + 2], [r.w - 2, 2], [-2, 2]].map(([u, v]) => {
    const ux = u * c - v * s, vy = u * s + v * c; // v hacia abajo en la imagen
    return [cx + ux / px, cy - (vy / px) * GL.KY, z + (delante ? 0.02 : -0.02)];
  });
  GL.lotes.spr.quad(pts, GL.uv(p.swingItem), [1, 1, 1, 1], x, z, 0, 0);
}
function dibujarCriatura3d(e) {
  const B = e.flash > 0, x = e.x / T, z = e.y / T + 0.05;
  if (e.tipo === 'babosa') {
    GL.plano('sombra_s', x, z - 0.04, { tinte: [1, 1, 1, 0.7 - Math.min(0.5, e.z / 30)] });
    const f = e.z > 0 ? 2 : e.aplasta > 0 ? 1 : Math.floor(e.t * 3) % 2;
    GL.sprite(`babosa_${f}${B ? '_B' : ''}`, x, z, { y: (e.z / 16) * GL.KY });
  } else if (e.tipo === 'larva') {
    GL.plano('sombra_m', x, z - 0.04, { tinte: [1, 1, 1, 0.7] });
    GL.sprite(`larva_${Math.floor(e.t * 6) % 2}${e.mira < 0 ? '_i' : ''}${B ? (e.mira < 0 ? 'B' : '_B') : ''}`, x, z);
  } else if (e.tipo === 'escarabajo') {
    GL.plano('sombra_m', x, z - 0.04, { tinte: [1, 1, 1, 0.7] });
    const tiembla = e.estado === 'prepara' ? Math.sin(J.t * 60) * 0.06 : 0;
    const rojo = e.estado === 'prepara' && Math.floor(J.t * 16) % 2;
    GL.sprite(`escarabajo_${Math.floor(e.t * (e.estado === 'carga' ? 16 : 8)) % 2}${B || rojo ? '_B' : ''}`, x + tiembla, z);
  }
}
function dibujarJefe3d(j) {
  if (j.estado === 'muerto') return;
  const x = j.x / T, z = j.y / T + 0.1;
  if (j.enterrado) {
    const tiembla = j.t < 0.8 ? Math.sin(J.t * 50) * 0.08 : 0;
    if (j.t < 0.8) GL.plano('aviso_suelo', x, z, { escala: 2.2, tinte: [1, 1, 1, 0.5 + 0.3 * Math.sin(J.t * 20)] });
    GL.sprite(`monticulo_${Math.floor(J.t * 8) % 2}`, x + tiembla, z);
    return;
  }
  GL.plano('sombra_l', x, z - 0.1, { tinte: [1, 1, 1, 0.8] });
  const brazos = j.estado === 'rocas' || j.estado === 'embiste' || j.estado === 'despierta' ? Math.floor(J.t * 10) % 2 : Math.floor(J.t * 2) % 2;
  const tiembla = j.estado === 'embiste' || j.estado === 'muriendo' ? Math.sin(J.t * 60) * 0.08 : 0;
  const rojo = j.estado === 'embiste' && Math.floor(J.t * 12) % 2;
  GL.sprite(`topo_${brazos}${j.flash > 0 || rojo ? '_B' : ''}`, x + tiembla, z);
}

function dibujarMundo3d() {
  GL.limpiarLotes();
  const sx = J.temblor > 0 ? (Math.random() - 0.5) * J.temblor * 6 : 0, sy = J.temblor > 0 ? (Math.random() - 0.5) * J.temblor * 6 : 0;
  GL.camara((J.cam.x + sx) / T, (J.cam.y + sy) / T);
  const v = GL.vista();
  // objetos a la vista
  const vistaObj = [];
  for (let ty = v.z0; ty <= v.z1; ty++) for (let tx = v.x0; tx <= v.x1; tx++) {
    if (!enMundo(tx, ty)) continue;
    const o = M.objetos.get(idx(tx, ty));
    if (o) vistaObj.push(o);
  }
  J.vistaObj = vistaObj;
  for (const o of vistaObj) dibujarObjeto3d(o);
  if (J.estado === 'juego') dibujarJugador3d(J.jug);
  for (const e of J.enemigos) dibujarCriatura3d(e);
  if (J.jefe && J.estado !== 'titulo') dibujarJefe3d(J.jefe);
  for (const b of J.botines) {
    const x = b.x / T, z = b.y / T;
    GL.plano('sombra_s', x, z, { escala: 0.6, tinte: [1, 1, 1, 0.6] });
    GL.sprite(ITEMS[b.id].icono, x, z, { y: (b.z / 16) * GL.KY + 0.06 + Math.sin(b.t * 4) * 0.04, escala: 0.85, lz: z });
  }
  for (const r of J.proyectiles) { GL.plano('sombra_s', r.x / T, r.y / T + 0.3, { escala: 0.5 }); GL.sprite('roca_lanzada', r.x / T, r.y / T + 0.3, { y: 0.35 }); }
  for (const q of J.particulas) {
    const o = { y: (q.z / 16) * GL.KY, escala: q.s * 0.5 * (q.tenue ? 0.8 : 1), tinte: [...hexRgb(q.c).map((c) => c / 255), q.chispa ? Math.min(1, q.vida) * (q.tenue ? 0.35 : 0.9) : 1] };
    if (q.brilla) { o.unlit = 1; o.lote = GL.lotes.brillo; }
    GL.sprite('blanco', q.x / T, q.y / T, o);
  }
  // grietas de las paredes que estás picando
  for (const [i, d] of M.danos) {
    const tx = i % N_MUNDO, ty = (i - tx) / N_MUNDO, m = M.muro[i];
    if (!m || tx < v.x0 || tx > v.x1 || ty < v.z0 || ty > v.z1) continue;
    const vm = INFO_MURO[m].vida * (M.mena[i] ? INFO_MENA[M.mena[i]].extra : 1), n = `grieta_${Math.min(3, 1 + Math.floor((d.v / vm) * 3))}`;
    GL.tapa(n, tx, ty); if (!muroEn(tx, ty + 1)) GL.cara(n, tx, ty + 1.004);
  }
  // casilla a la que apuntas
  if (J.estado === 'juego' && !J.panel) {
    const s = J.inv[J.sel], it = s && ITEMS[s.id];
    const { tx, ty } = casillaMira(J.jug);
    const muro = muroEn(tx, ty), colocar = it && it.tipo === 'colocable';
    const a = 0.55 + 0.3 * Math.sin(J.t * 8);
    if (muro && muro !== MURO.ROCA && muro !== MURO.RUINA && !enemigoCerca(J.jug, 30)) {
      GL.tapa('marca', tx, ty, { lote: GL.lotes.brillo, unlit: 1, tinte: [1, 0.95, 0.75, a] });
      if (!muroEn(tx, ty + 1)) GL.cara('marca', tx, ty + 1.006, { lote: GL.lotes.brillo, unlit: 1, tinte: [1, 0.95, 0.75, a] });
    } else if (colocar && puedeColocar(tx, ty)) GL.plano('marca', tx + 0.5, ty + 0.5, { lote: GL.lotes.brillo, unlit: 1, tinte: [0.55, 1, 0.65, a] });
  }
  const luz = calcularLuz(v.x0, v.z0, v.x1 - v.x0 + 1, v.z1 - v.z0 + 1, fuentesLuz(v), AMBIENTE);
  GL.dibujar(luz);
}

// Números de daño y la «Z» del topo dormido, sobre la interfaz.
function dibujarTextosMundo() {
  for (const n of J.numeros) {
    const p = GL.aPantalla(n.x / T, (n.alto / 16) * GL.KY, n.y / T);
    ctx.globalAlpha = Math.min(1, n.vida * 3);
    texto(ctx, n.txt, p.x * LW, p.y * LH, n.c, { alin: 'c', borde: COL.negro });
  }
  ctx.globalAlpha = 1;
  const j = J.jefe;
  if (j && j.estado === 'dormido' && J.estado !== 'titulo') {
    const k = (J.t * 0.6) % 1, p = GL.aPantalla(j.x / T + 0.7 + k * 0.4, ((40 + k * 14) / 16) * GL.KY, j.y / T);
    ctx.globalAlpha = 1 - k; texto(ctx, 'Z', p.x * LW, p.y * LH, '#e8e0f0', { borde: COL.negro, esc: 1 }); ctx.globalAlpha = 1;
  }
}

function dibujar() {
  UI.botones = [];
  ctx.clearRect(0, 0, LW, LH);
  if (!M) return;
  dibujarMundo3d();
  dibujarTextosMundo();
  if (J.estado === 'titulo') dibujarTitulo(ctx, hayPartida);
  else { dibujarHUD(ctx); if (J.panel) dibujarPanel(ctx); }
}

// ---------------------------------------------------------------- arranque

function iniciar(datosHot) {
  construirArte();
  if (!GL.iniciar(lienzo3d)) {
    document.body.insertAdjacentHTML('beforeend', '<p style="color:#fff;font:16px sans-serif;padding:24px">Este navegador no tiene WebGL2. Prueba con Chrome o Safari actualizados.</p>');
    return;
  }
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
  J.cam.x = CENTRO * T; J.cam.y = CENTRO * T;
  if (datosHot && datosHot.enJuego && hayPartida) empezar(true);
  requestAnimationFrame((ms) => { ultimo = ms; cuadro(ms); });
}

try { window.claude?.hot?.snapshot?.(() => { guardarPartida(); return { enJuego: J.estado === 'juego' }; }); } catch (e) { /* fuera del visor */ }
if (window.claude?.hot?.ready) window.claude.hot.ready(iniciar); else iniciar(window.claude?.hot?.data ?? {});
