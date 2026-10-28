'use strict';
// Arranque, bucle principal, cámara, dibujo del mundo 3D y guardado. La interfaz va en un lienzo 2D encima.

const lienzo3d = document.getElementById('mundo3d');
const lienzo = document.getElementById('pantalla');
const ctx = lienzo.getContext('2d');
let LW = 320, LH = 180, ESC = 1, hayPartida = false;
const CLAVE = 'corazon-dormido-v4', CLAVE_V3 = 'corazon-dormido-v3';
const VER_FPS = /fps/.test(location.search + location.hash);

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
      v: 4, mundo: datosMundo(), jug: { x: p.x, y: p.y, vida: Math.max(1, p.vida), hambre: p.hambre },
      inv: J.inv, sel: J.sel, equipo: J.equipo, hab: J.hab, buffs: J.buffs, hito: J.hito, objetivo: J.objetivo,
      jefesMuertos: J.jefesMuertos, estatuas: J.estatuas, cama: J.cama, tiempo: J.tiempoMundo, despierto: J.corazonDespierto,
      mascotas: J.mascotas, altares: J.altares, muralla: J.murallaAbierta, aspecto: J.aspecto,
    }));
    hayPartida = true;
  } catch (e) { /* sin almacenamiento: la partida dura lo que la pestaña */ }
  J.guardadoT = J.t;
}
function leerPartida() {
  try { const s = localStorage.getItem(CLAVE); const g = s ? JSON.parse(s) : null; return g && g.v === 4 && g.mundo.n === N_MUNDO ? g : null; } catch (e) { return null; }
}
// La partida de la fase 1 (mundo de 320 casillas): se lleva al mundo nuevo el personaje, la mochila, lo logrado y
// lo que había en los cofres y los muebles puestos (en cofres de mudanza junto al Corazón).
function leerPartidaV3() {
  try { const s = localStorage.getItem(CLAVE_V3); const g = s ? JSON.parse(s) : null; return g && g.v === 3 ? g : null; } catch (e) { return null; }
}
function migrarV3(g) {
  Object.assign(J, { sel: g.sel || 0, hito: g.hito || {}, objetivo: g.objetivo || 0, hab: g.hab || {}, jefesMuertos: g.jefesMuertos || {}, estatuas: g.estatuas || {}, tiempoMundo: g.tiempo || 0, corazonDespierto: !!g.despierto });
  J.equipo = Object.assign({ casco: null, peto: null, botas: null, acc1: null, acc2: null, mascota: null }, g.equipo || {});
  J.inv = (g.inv || []).concat(new Array(N_INV).fill(null)).slice(0, N_INV);
  for (const s of J.inv) if (s) asegurarItem(s.id);
  for (const [jefe, est] of Object.entries(M.estatuas)) if (J.estatuas[jefe]) { const o = objetoEn(est.x, est.y); if (o) o.extra = 1; }
  const caja = [];
  for (const [, , tipo, , datos] of g.mundo.objetos || []) {
    if (datos && datos.inv) for (const s of datos.inv) if (s) caja.push({ id: s.id, n: s.n });
    const info = OBJ[tipo], it = info && info.suelta && info.suelta[0] && ITEMS[info.suelta[0][0]];
    if (it && it.tipo === 'colocable' && info.suelta[0][0] !== 'antorcha' && tipo !== 'cultivo') caja.push({ id: info.suelta[0][0], n: 1 });
  }
  let k = 0;
  while (caja.length && k < 24) {
    const a = (k / 12) * Math.PI * 2 + 0.3, d = k < 12 ? 6.5 : 7.4, x = Math.round(CENTRO + Math.cos(a) * d), y = Math.round(CENTRO + Math.sin(a) * d);
    k++;
    if (solido(x, y, true) || objetoEn(x, y)) continue;
    const o = ponerObjeto(x, y, 'cofre');
    for (let i = 0; i < 30 && caja.length; i++) { const s = caja.shift(); asegurarItem(s.id); if (ITEMS[s.id]) o.datos.inv[i] = s; }
  }
  setTimeout(() => aviso('El mundo ha crecido. Tus cofres y muebles están en cofres junto al Corazón.', null, '#9fe0a8'), 1500);
}

function empezar(continuar) {
  const g = continuar ? leerPartida() : null, v3 = continuar && !g ? leerPartidaV3() : null;
  Object.assign(J, { enemigos: [], botines: [], proyectiles: [], disparos: [], particulas: [], numeros: [], avisos: [], efectos: [], charcos: [], avisosSuelo: [], pesca: null, panel: null, bombas: [], regreso: null, mascotaEnt: null, cartel: null, regionAntes: -1, rayosCielo: [] });
  if (g) {
    cargarMundo(g.mundo);
    Object.assign(J, { sel: g.sel || 0, hito: g.hito || {}, objetivo: g.objetivo || 0, hab: g.hab || {}, buffs: g.buffs || [], jefesMuertos: g.jefesMuertos || {}, estatuas: g.estatuas || {}, cama: g.cama || null, tiempoMundo: g.tiempo || 0, corazonDespierto: !!g.despierto });
    Object.assign(J, { mascotas: g.mascotas || {}, altares: g.altares || {}, murallaAbierta: !!g.muralla, aspecto: g.aspecto || Object.assign({}, ASPECTO_INICIAL) });
    J.equipo = Object.assign({ casco: null, peto: null, botas: null, acc1: null, acc2: null, mascota: null }, g.equipo || {});
    J.inv = g.inv.concat(new Array(N_INV).fill(null)).slice(0, N_INV);
    for (const s of J.inv) if (s) asegurarItem(s.id);
    for (const o of M.objetos.values()) if (o.datos && o.datos.inv) for (const s of o.datos.inv) if (s) asegurarItem(s.id);
    J.jug = nuevoJugador();
    Object.assign(J.jug, g.jug);
  } else {
    generarMundo(v3 ? v3.mundo.sem : (Math.random() * 1e9) | 0);
    Object.assign(J, { sel: 0, hito: {}, objetivo: 0, hab: {}, buffs: [], jefesMuertos: {}, estatuas: {}, cama: null, tiempoMundo: 0, corazonDespierto: false, mascotas: {}, altares: {}, murallaAbierta: false, aspecto: J.aspectoNuevo || Object.assign({}, ASPECTO_INICIAL) });
    J.equipo = { casco: null, peto: null, botas: null, acc1: null, acc2: null, mascota: null };
    J.inv = new Array(N_INV).fill(null);
    if (v3) migrarV3(v3);
    else for (const [id, n] of INICIO) agregar(id, n);
    J.jug = nuevoJugador();
  }
  calcularStats();
  crearJefes();
  J.estado = 'juego'; J.aparecer = 4;
  J.cam.x = J.jug.x; J.cam.y = J.jug.y;
  FLUJO.t = 0;
  if (!g && !v3) { aviso('El Corazón duerme. Despiértalo.', null, '#ffb3c8'); setTimeout(() => aviso('Rompe raíces para sacar madera'), 2500); }
  guardarPartida();
}
function irTitulo() { J.estado = 'titulo'; J.panel = null; hayPartida = !!leerPartida() || !!leerPartidaV3(); }
function revisarObjetivos() {
  while (J.objetivo < OBJETIVOS.length && OBJETIVOS[J.objetivo].hecho(J)) {
    aviso('¡Hecho! ' + OBJETIVOS[J.objetivo].txt, null, '#7dff9a');
    J.objetivo++;
    if (J.objetivo < OBJETIVOS.length) Sonido.sfx.nivel();
  }
}

// ---------------------------------------------------------------- bucle

let ultimo = 0, fps = 60, fpsT = 0, fpsN = 0;
function cuadro(ms) {
  const dt = Math.min(0.05, Math.max(0, (ms - ultimo) / 1000)); ultimo = ms;
  fpsT += dt; fpsN++; if (fpsT > 0.5) { fps = Math.round(fpsN / fpsT); fpsT = 0; fpsN = 0; }
  J.t += dt;
  if (J.estado === 'juego' && !J.panel) {
    leerEntrada();
    if (J.pausaGolpe > 0) J.pausaGolpe -= dt; // pausa corta al golpear: da peso al golpe
    else {
      actualizarJugador(dt); actualizarCriaturas(dt); actualizarJefes(dt); actualizarProyectiles(dt); actualizarDisparos(dt);
      actualizarCultivos(dt); actualizarPesca(dt, J.tirando); actualizarFase2(dt);
    }
    Personaje.trabajar(4);
    actualizarMundoVivo(dt);
    ambientePart(dt);
    revisarObjetivos();
    if (J.t - J.guardadoT > 25) guardarPartida();
  } else if (J.estado === 'muerto') actualizarMundoVivo(dt);
  else if (J.estado === 'titulo') { ambientePart(dt); actualizarMundoVivo(dt); Personaje.trabajar(6); }
  moverCamara(dt);
  dibujar();
  requestAnimationFrame(cuadro);
}
function moverCamara(dt) {
  let tx, ty;
  if (J.estado === 'titulo') { tx = CENTRO * T + 8 + Math.cos(J.t * 0.12) * 40; ty = CENTRO * T + 20 + Math.sin(J.t * 0.12) * 24; }
  else {
    tx = J.jug.x; ty = J.jug.y - 6;
    // en una pelea con un guardián, la cámara se va un poco hacia él para que se vean los dos
    for (const j of Object.values(J.jefes)) {
      if (!j.activo || j.estado === 'muerto' || j.invisible) continue;
      const dx = j.x - J.jug.x, dy = j.y - J.jug.y;
      if (dx * dx + dy * dy < 170 * 170) { const alto = j.alto > 24; tx += dx * (alto ? 0.5 : 0.35); ty += dy * (alto ? 0.5 : 0.35) + (alto ? -20 : 6); break; }
    }
  }
  const k = Math.min(1, dt * 7);
  J.cam.x += (tx - J.cam.x) * k; J.cam.y += (ty - J.cam.y) * k;
}

// Chispas de las antorchas y motas en el aire.
function ambientePart(dt) {
  if (!J.vistaObj) return;
  for (const o of J.vistaObj) {
    if ((o.tipo === 'antorcha' && Math.random() < dt * 2.2) || (o.tipo === 'horno' && Math.random() < dt * 3)) {
      const magica = o.datos && o.datos.magica;
      J.particulas.push({ x: o.x * T + 8 + (Math.random() - 0.5) * 4, y: o.y * T + 15, z: 14, vx: (Math.random() - 0.5) * 8, vy: 0, vz: 10 + Math.random() * 14, g: -6, vida: 1.2 + Math.random(), c: magica ? '#c080ff' : Math.random() < 0.5 ? '#ffb040' : '#ff7020', s: 1, brilla: true, chispa: true });
    }
    if ((o.tipo === 'cristal' || o.tipo === 'hongo' || o.tipo === 'arbusto') && Math.random() < dt * 0.6)
      J.particulas.push({ x: o.x * T + 8 + (Math.random() - 0.5) * 12, y: o.y * T + 10 + (Math.random() - 0.5) * 8, z: 4, vx: (Math.random() - 0.5) * 4, vy: 0, vz: 5 + Math.random() * 5, g: -2, vida: 2 + Math.random() * 2, c: '#7ff0ff', s: 1, brilla: true, chispa: true });
  }
  if (J.estado === 'juego') ambienteSelva(dt);
  if (J.estado === 'juego' && Math.random() < dt * 3) {
    const p = J.jug;
    J.particulas.push({ x: p.x + (Math.random() - 0.5) * 140, y: p.y + (Math.random() - 0.5) * 90, z: Math.random() * 20, vx: (Math.random() - 0.5) * 3, vy: 0, vz: 1 + Math.random() * 2, g: -0.5, vida: 3 + Math.random() * 3, c: '#fff0d0', s: 1, brilla: true, chispa: true, tenue: true });
  }
}

// ---------------------------------------------------------------- luz

const AMBIENTE = [0.035, 0.03, 0.055];
// Fuentes que no se mueven (se guardan en caché hasta que el mundo cambia).
function fuentesFijas(x0, y0, x1, y1) {
  const f = [];
  for (const i of M.luces) {
    const o = M.objetos.get(i);
    if (!o || o.x < x0 - 12 || o.x > x1 + 12 || o.y < y0 - 12 || o.y > y1 + 12) continue;
    const info = OBJ[o.tipo], x = o.x * T + 8, y = o.y * T + 8;
    if (o.tipo === 'corazon') f.push({ x, y: y + 4, r: 7 + Object.keys(J.estatuas).length * 3, c: [1.0, 0.42, 0.6], k: 0.9 });
    else if (o.tipo === 'estatua') { if (o.extra) f.push({ x, y, r: 4.5, c: [1, 0.55, 0.7], k: 1 }); }
    else if (o.tipo === 'altar_titan') { if (o.extra) f.push({ x, y, r: 5, c: o.datos && o.datos.titan === 'tamar' ? [0.9, 0.95, 1] : [0.5, 1, 0.7], k: 1 }); }
    else if (o.tipo === 'antorcha' && o.datos && o.datos.magica) f.push({ x, y, r: 7, c: [0.65, 0.4, 1], k: 0.9, fuego: true });
    else if (info.luz) { const [r, cr, cg, cb] = info.luz; f.push({ x, y, r, c: [cr, cg, cb], k: info.fuego ? 0.9 : 0.85, fuego: !!info.fuego }); }
  }
  // la luz que baja del cielo en la Selva
  for (const s of M.soles) if (s.x > x0 - 12 && s.x < x1 + 12 && s.y > y0 - 12 && s.y < y1 + 12) f.push({ x: s.x * T + 8, y: s.y * T + 8, r: s.r, c: s.c || [1.0, 0.95, 0.7], k: s.k });
  return f;
}
function fuentesDinamicas() {
  const f = [];
  if (J.estado !== 'titulo' && J.estado !== 'muerto') {
    const s = J.inv[J.sel], extra = J.st ? J.st.luz : 0;
    if (s && s.id === 'antorcha') f.push({ x: J.jug.x + J.jug.fx * 4, y: J.jug.y - 2, r: 8 + extra, c: [1.0, 0.8, 0.52], k: 1.0 + 0.08 * Math.sin(J.t * 14) * Math.sin(J.t * 6.1) });
    else f.push({ x: J.jug.x, y: J.jug.y - 2, r: 6 + extra, c: [1.0, 0.88, 0.7], k: 0.7 });
  }
  for (const j of Object.values(J.jefes)) {
    if (!j.activo) continue;
    if (j.id === 'ozmar') f.push({ x: j.x, y: j.y, r: 5, c: [0.6, 0.35, 1], k: 0.8 });
    if (j.id === 'reina') f.push({ x: j.x, y: j.y - 6, r: 5.5, c: [0.55, 1, 0.3], k: 0.7 });
    if (j.estado === 'aturdido') f.push({ x: j.x, y: j.y - 20, r: 2.5, c: [1, 0.9, 0.5], k: 0.6 });
  }
  for (const d of J.disparos) if (d.magico) f.push({ x: d.x, y: d.y, r: 2.5, c: [0.7, 0.4, 1], k: 0.7 });
  if (J.estado === 'juego') { const reg = M.region[idx(Math.floor(J.jug.x / T), Math.floor(J.jug.y / T))]; if (reg === REGION.SELVA || reg === REGION.PRADERA) f.push({ x: J.jug.x, y: J.jug.y - 2, r: 10, c: [0.85, 1, 0.7], k: 0.38 }); }
  if (J.mascotaEnt && J.mascotaEnt.id === 'mascota_luci') f.push({ x: J.mascotaEnt.x, y: J.mascotaEnt.y, r: 5, c: [1, 0.95, 0.6], k: 0.8 });
  for (const b of J.bombas) f.push({ x: b.x, y: b.y, r: 2, c: [1, 0.7, 0.3], k: 0.6 });
  if (J.destelloT > 0) f.push({ x: J.destello.x, y: J.destello.y, r: 8, c: [1, 0.8, 0.5], k: J.destelloT * 6 });
  for (const r of J.rayosCielo || []) if (r.t < 0.05) f.push({ x: r.x, y: r.y, r: 7, c: [0.8, 0.9, 1], k: 1.2 });
  for (const r of J.proyectiles) if (r.tipo === 'pluma') f.push({ x: r.x, y: r.y, r: 1.5, c: [1, 1, 0.9], k: 0.4 });
  for (const r of J.proyectiles) if (r.tipo === 'orbe') f.push({ x: r.x, y: r.y, r: 2, c: [0.6, 0.3, 0.9], k: 0.6 });
  return f;
}

// ---------------------------------------------------------------- dibujo de las cosas del mundo

const HALO = { arbol_luna: [1.6, [0.5, 0.8, 1], 0.22], flor_sol: [0.8, [1, 0.85, 0.4], 0.18], farol_carmesi: [1.8, [1, 0.5, 0.45], 0.3], lampara_pie: [1.3, [1, 0.85, 0.55], 0.26], hoguera: [1.2, [1, 0.6, 0.25], 0.32], altar_titan: [1.2, [0.7, 1, 0.8], 0.25], incubadora: [0.7, [0.6, 0.9, 1], 0.16], huevo_larva: [0.7, [0.5, 1, 0.3], 0.14], antorcha: [1.1, [1, 0.6, 0.25], 0.32], horno: [1.0, [1, 0.5, 0.2], 0.28], lampara: [1.5, [0.6, 0.9, 1], 0.3], farol: [1.7, [1, 0.8, 0.4], 0.3], cristal: [1.1, [0.3, 0.85, 1], 0.24], hongo: [0.9, [0.3, 0.85, 1], 0.2], arbusto: [0.8, [0.3, 0.6, 1], 0.16], olla: [0.7, [1, 0.5, 0.25], 0.15] };
const ALTURA_HALO = { arbol_luna: 2.0, flor_sol: 0.5, farol_carmesi: 1.1, lampara_pie: 1.15, hoguera: 0.4, altar_titan: 1.0, incubadora: 0.5, huevo_larva: 0.3, antorcha: 1.05, horno: 0.5, lampara: 1.0, farol: 1.1, cristal: 0.55, hongo: 0.45, arbusto: 0.3, olla: 0.3 };
const T4 = [1, 1, 1, 1];

function dibujarObjeto3d(o) {
  const info = OBJ[o.tipo], x = o.x + 0.5 + (o.sacude > 0 ? Math.sin(J.t * 80) * 0.08 : 0), z = o.y + 0.86;
  let n = info.arte;
  if (o.tipo === 'antorcha') n = `antorcha_${Math.floor(J.t * 8 + o.x * 3 + o.y) % 4}${o.datos && o.datos.magica ? 'm' : ''}`;
  else if (o.tipo === 'horno') n = `horno_${Math.floor(J.t * 5) % 2}`;
  else if (o.tipo === 'olla') n = `olla_${Math.floor(J.t * 4) % 2}`;
  else if (o.tipo === 'estatua') n = `estatua_${o.datos.jefe}${o.extra ? '_on' : ''}`;
  else if (o.tipo === 'altar_titan') n = `altar_${o.datos.titan}${o.extra ? '_on' : ''}`;
  else if (o.tipo === 'incubadora') n = o.datos && o.datos.huevo ? (huevoListo(o) && Math.floor(J.t * 3) % 2 ? 'incubadora_lista' : 'incubadora_on') : 'incubadora';
  else if (o.tipo === 'hoguera') n = `hoguera_${Math.floor(J.t * 8 + o.x) % 3}`;
  else if (o.tipo === 'arbol' && hash2(o.x, o.y, 5) < 0.5) n = 'arbol_b';
  else if (o.tipo === 'altar_cielo') n = J.jefesMuertos.tamar ? 'altar_cielo_on' : 'altar_cielo';
  else if (o.tipo === 'puerta') n = Math.hypot(J.jug.x / T - x, J.jug.y / T - z) < 1.4 ? 'puerta_abierta' : 'puerta';
  else if (o.tipo === 'cultivo') { const e = etapaCultivo(o); n = `cultivo_${o.datos.planta}_${e}`; if (o.datos.regado) GL.plano('regado', x, o.y + 0.5, { unlit: 0, y: 0.003, tinte: [1, 1, 1, 0.8] }); }
  if (o.tipo === 'corazon') {
    const flota = Math.sin(J.t * 1.6) * 0.08, despierto = J.corazonDespierto, cor = Object.keys(J.estatuas).length ? 'corazon' : 'corazon_dormido';
    GL.sprite('altar', x, z + 0.4);
    GL.sprite(cor, x, z - 0.2, { y: 0.9 + flota, unlit: 0.35 });
    GL.sprite(cor, x, z - 0.19, { y: 0.9 + flota, unlit: 1, lote: GL.lotes.brillo, tinte: [1, 1, 1, (despierto ? 0.7 : Object.keys(J.estatuas).length ? 0.45 : 0.18) + 0.2 * Math.sin(J.t * 2.2)] });
    GL.halo(x, 1.6 + flota, z - 0.2, 2.6 + Object.keys(J.estatuas).length * 0.5, [1, 0.35, 0.55], 0.28 + 0.08 * Math.sin(J.t * 2.2));
    return;
  }
  if (o.tipo === 'raiz') GL.plano('raiz_suelo', x, z - 0.3, { unlit: 0, tinte: [0.85, 0.85, 0.85, 1] });
  const meta = META_SPR[n];
  if (meta) { // modelo 3D horneado: su sombra con su forma y el dibujo con la huella en el centro de la casilla
    const cz = o.y + 0.5;
    if (SPR[n + '_sombra']) GL.plano(n + '_sombra', x, cz, { tinte: T4 });
    GL.modelo(n, x, cz);
    const brilla = info.brillo || ((o.tipo === 'estatua' || o.tipo === 'altar_titan') && o.extra) || o.tipo === 'incubadora' || (o.tipo === 'cultivo' && CULTIVOS[o.datos.planta].brilla && etapaCultivo(o) === 3);
    if (brilla && SPR[n + '_luz']) {
      GL.sprite(n + '_luz', x + meta.dx, cz + meta.dz + 0.002, { unlit: 1, lote: GL.lotes.brillo, tinte: [1, 1, 1, 0.8], lx: x, lz: cz });
      const h = HALO[o.tipo];
      if (h) GL.halo(x + meta.dx + (meta.luzX || 0), meta.luzY !== undefined ? meta.luzY * GL.KY : ALTURA_HALO[o.tipo], cz + meta.dz, h[0], o.datos && o.datos.magica ? [0.6, 0.3, 1] : h[1], h[2] * (info.fuego ? 0.85 + 0.15 * Math.sin(J.t * 11 + o.x) : 1));
    }
    return;
  }
  if (info.alto) GL.plano('sombra_l', x, z - 0.12, { tinte: [1, 1, 1, 0.75], escala: 1.15 });
  else if (info.bloquea || o.tipo === 'antorcha' || o.tipo === 'lampara' || o.tipo === 'farol') GL.plano(o.tipo === 'antorcha' ? 'sombra_s' : 'sombra_m', x, z - 0.05, { tinte: [1, 1, 1, 0.7] });
  GL.sprite(n, x, z);
  const brilla = info.brillo || ((o.tipo === 'estatua' || o.tipo === 'altar_titan') && o.extra) || o.tipo === 'incubadora' || (o.tipo === 'cultivo' && CULTIVOS[o.datos.planta].brilla && etapaCultivo(o) === 3);
  if (brilla && SPR[n + '_luz']) {
    GL.sprite(n + '_luz', x, z + 0.002, { unlit: 1, lote: GL.lotes.brillo, tinte: [1, 1, 1, 0.8] });
    const h = HALO[o.tipo];
    if (h) GL.halo(x, ALTURA_HALO[o.tipo], z, h[0], o.datos && o.datos.magica ? [0.6, 0.3, 1] : h[1], h[2] * (info.fuego ? 0.85 + 0.15 * Math.sin(J.t * 11 + o.x) : 1));
  }
}

function nombreJugador(p) {
  let a;
  if (p.swing > 0 && p.swingDur) a = 'g' + Math.min(2, Math.floor((1 - p.swing / p.swingDur) * 3));
  else if (p.andando) a = 'a' + (Math.floor(p.anim * 1.5) % 6);
  else a = 'q' + (Math.floor(J.t * 1.6) % 2);
  return `${p.flash > 0 ? 'pjB' : 'pj'}_${p.dir}_${a}`;
}
function dibujarJugador3d(p) {
  const x = p.x / T, z = p.y / T + 0.06;
  GL.plano('sombra_s', x, z - 0.04, { tinte: [1, 1, 1, 0.75] });
  const parpadea = p.invuln > 0 && Math.floor(J.t * 20) % 2;
  const s = J.inv[J.sel], it = s && asegurarItem(s.id);
  const enMano = !(p.swing > 0) && it && (['pico', 'espada', 'maza', 'arco', 'azada', 'cana', 'regadera'].includes(it.tipo) || s.id === 'antorcha');
  const detras = p.dir === 'ar';
  if (enMano && detras && !parpadea) dibujarEnMano(p, x, z, s.id, it);
  if (!parpadea) {
    GL.modelo(nombreJugador(p), x, z); // el muñeco 3D horneado con su armadura (personaje.js)
  }
  if (enMano && !detras && !parpadea) dibujarEnMano(p, x, z, s.id, it);
  if (p.swing > 0 && p.swingItem) dibujarHerramienta3d(p, x, z);
  if (J.pesca) { // sedal y corcho
    const ps = J.pesca, bx = ps.x / T, bz = ps.y / T, hund = ps.estado === 'pica' ? Math.sin(J.t * 30) * 0.05 - 0.04 : Math.sin(J.t * 3) * 0.02;
    GL.sprite('corcho', bx, bz, { y: 0.02 + hund });
    const hx = x + p.fx * 0.4, hy = 0.9;
    for (let k = 1; k < 8; k++) { const t = k / 8, cx = hx + (bx - hx) * t, cz = z + (bz - z) * t, cy = hy * (1 - t) - Math.sin(t * Math.PI) * 0.15 + 0.05; GL.sprite('blanco', cx, cz, { y: cy, escala: 0.25, unlit: 0.6, tinte: [0.9, 0.9, 0.85, 1] }); }
  }
}
function dibujarEnMano(p, x, z, id, it) {
  const lado = p.dir === 'iz' ? -1 : p.dir === 'de' ? 1 : p.dir === 'ab' ? 1 : -1;
  const bob = p.andando && Math.floor(p.anim) % 2 ? 1 : 0;
  const hx = x + lado * (p.dir === 'ab' || p.dir === 'ar' ? 0.32 : 0.3), hz = z + (p.dir === 'ar' ? -0.03 : 0.03), hy = ((7.4 + bob) / 16) * GL.KY;
  if (id === 'antorcha') {
    const f = Math.floor(J.t * 9) % 4;
    GL.sprite(`antorcha_${f}`, hx, hz, { y: hy, escala: 0.62 });
    GL.sprite(`antorcha_${f}_luz`, hx, hz + 0.002, { y: hy, escala: 0.62, unlit: 1, lote: GL.lotes.brillo, tinte: [1, 1, 1, 0.9] });
    GL.halo(hx, hy + 0.55, hz, 0.9, [1, 0.6, 0.25], 0.3 + 0.05 * Math.sin(J.t * 13));
    if (Math.random() < 0.08) J.particulas.push({ x: hx * T, y: hz * T, z: (hy / GL.KY) * 16 + 9, vx: (Math.random() - 0.5) * 6, vy: 0, vz: 12 + Math.random() * 10, g: -6, vida: 1 + Math.random(), c: Math.random() < 0.5 ? '#ffb040' : '#ff7020', s: 1, brilla: true, chispa: true });
    return;
  }
  const r = SPR[it.icono];
  if (!r) return;
  const rot = lado > 0 ? 0.25 : -0.25, c = Math.cos(rot), sn = Math.sin(rot), px = 16, pts = [];
  for (const [u, v] of [[-2, -r.h + 2], [r.w - 2, -r.h + 2], [r.w - 2, 2], [-2, 2]]) {
    const uu = lado > 0 ? u : -u, ux = uu * c - v * sn, vy = uu * sn + v * c;
    pts.push(hx + ux / px, hy - (vy / px) * GL.KY, hz);
  }
  GL.libre(it.icono, pts, T4, x, z);
}
function dibujarHerramienta3d(p, x, z) {
  const r = SPR[p.swingItem];
  if (!r) return;
  const k = 1 - p.swing / p.swingDur, lado = p.fx >= 0 ? 1 : -1, delante = p.dir !== 'ar';
  const base = p.dir === 'ar' ? -Math.PI / 2 : p.dir === 'ab' ? Math.PI / 2 : p.fx > 0 ? 0 : Math.PI;
  const ang = base + lado * (-1.4 + k * 2.4), px = 16;
  const cx = x + (p.dir === 'de' ? 0.25 : p.dir === 'iz' ? -0.25 : 0.12 * lado), cy = (8.4 / px) * GL.KY;
  const rot = ang + Math.PI / 4, c = Math.cos(rot), s = Math.sin(rot), pts = [];
  for (const [u, v] of [[-2, -r.h + 2], [r.w - 2, -r.h + 2], [r.w - 2, 2], [-2, 2]]) { const ux = u * c - v * s, vy = u * s + v * c; pts.push(cx + ux / px, cy - (vy / px) * GL.KY, z + (delante ? 0.02 : -0.02)); }
  GL.libre(p.swingItem, pts, T4, x, z);
}
function dibujarCriatura3d(e) {
  const B = e.flash > 0, x = e.x / T, z = e.y / T + 0.05, c = CRIATURAS[e.tipo];
  const aviso = (e.estado === 'prepara' || e.estado === 'conjura') && Math.floor(J.t * 16) % 2;
  const tiembla = e.estado === 'prepara' ? Math.sin(J.t * 60) * 0.05 : 0;
  const blanco = B || aviso ? 1 : 0;
  GL.plano(e.radio > 8 ? 'sombra_m' : 'sombra_s', x, z - 0.04, { tinte: [1, 1, 1, 0.7 - Math.min(0.5, (e.z || 0) / 30)], escala: e.radio > 8 ? 1.3 : 1 });
  let n;
  if (SPRITE_CRIATURA[e.tipo]) n = SPRITE_CRIATURA[e.tipo](e);
  else if (c.conducta === 'salta') n = `${e.tipo}_${e.z > 0 ? 2 : e.aplasta > 0 ? 1 : Math.floor(e.t * 3) % 2}`;
  else if (e.tipo.startsWith('larva')) n = `${e.tipo}_${Math.floor(e.t * 6) % 2}`;
  else if (e.tipo === 'seton') n = `seton_${e.estado === 'prepara' ? 2 : Math.floor(e.t * 5) % 2}`;
  else n = `${e.tipo}_${e.estado === 'prepara' || e.estado === 'conjura' ? 2 : e.estado === 'carga' ? 3 : Math.floor(e.t * 6) % 2}`;
  const flip = e.mira < 0, dx = META_SPR[n] ? META_SPR[n].dx * (flip ? -1 : 1) : 0;
  GL.sprite(n, x + tiembla + dx, z, { y: ((e.z || 0) / 16) * GL.KY, flip, blanco: blanco * 0.85, lx: x, lz: z });
  // ojos y Podre: brillan solos en la oscuridad (se ven antes que el cuerpo)
  if (SPR[n + '_luz']) GL.sprite(n + '_luz', x + tiembla + dx, z + 0.003, { y: ((e.z || 0) / 16) * GL.KY, flip, unlit: 1, lote: GL.lotes.brillo, tinte: [1, 1, 1, 0.95] });
  if (e.estado === 'conjura') GL.halo(x, 1.1, z, 0.6, [0.7, 0.4, 1], 0.4);
}
// Un aviso de ataque en el suelo. k: de 0 (recién puesto) a 1 (golpe). radio en píxeles.
const COLOR_AVISO = { circulo: [1, 0.28, 0.2], grieta: [1, 0.42, 0.16], runa: [0.78, 0.38, 1] };
function dibujarAviso(x, z, radio, k, estilo = 'circulo', v = 0) {
  const e = radio / 16, c = COLOR_AVISO[estilo] || COLOR_AVISO.circulo, late = 0.65 + 0.35 * Math.sin(J.t * (9 + k * 16));
  if (estilo === 'grieta') {
    GL.plano('aviso_disco', x, z, { escala: e * 0.9, tinte: [0.08, 0.02, 0.02, 0.35 + 0.3 * k] });
    GL.plano(`aviso_grieta_${v}`, x, z, { escala: e * (0.55 + 0.6 * k), lote: GL.lotes.brillo, unlit: 1, tinte: [c[0], c[1], c[2], (0.35 + 0.65 * k) * late], y: 0.012 });
  } else if (estilo === 'runa') {
    GL.plano('aviso_disco', x, z, { escala: e * k, tinte: [0.2, 0.05, 0.3, 0.45] });
    GL.plano('aviso_runa', x, z, { escala: e, lote: GL.lotes.brillo, unlit: 1, tinte: [c[0], c[1], c[2], (0.4 + 0.6 * k) * late], y: 0.012 });
  } else {
    GL.plano('aviso_disco', x, z, { escala: e * (0.15 + 0.85 * k), tinte: [0.8, 0.08, 0.05, 0.3] });
    GL.plano('aviso_anillo', x, z, { escala: e, tinte: [0.9, 0.12, 0.08, 0.8], y: 0.01 });
    GL.plano('aviso_anillo', x, z, { escala: e, lote: GL.lotes.brillo, unlit: 1, tinte: [c[0], c[1], c[2], (0.35 + 0.45 * k) * late], y: 0.012 });
  }
}
// desplazamiento para centrar un modelo horneado (los jefes no se voltean)
const dxDe = (n) => (META_SPR[n] ? META_SPR[n].dx : 0);
function dibujarJefe3d(j) {
  if (j.estado === 'muerto') return;
  if (DIBUJAR_JEFE[j.id]) { DIBUJAR_JEFE[j.id](j); return; }
  const x = j.x / T, z = j.y / T + 0.1;
  if (j.id === 'topo') {
    if (j.enterrado) {
      const tiembla = j.t < 0.8 ? Math.sin(J.t * 50) * 0.08 : 0;
      if (j.t < 0.8) dibujarAviso(x, z, 16, j.t / 0.8, 'grieta', 1);
      GL.sprite(`monticulo_${Math.floor(J.t * 8) % 2}`, x + tiembla + dxDe('monticulo_0'), z);
      return;
    }
    GL.plano('sombra_l', x, z - 0.1, { tinte: [1, 1, 1, 0.8] });
    const brazos = j.estado === 'rocas' || j.estado === 'embiste' || j.estado === 'despierta' ? Math.floor(J.t * 10) % 2 : Math.floor(J.t * 2) % 2;
    const tiembla = j.estado === 'embiste' || j.estado === 'muriendo' ? Math.sin(J.t * 60) * 0.08 : 0;
    GL.sprite(`topo_${brazos}`, x + tiembla + dxDe(`topo_${brazos}`), z, { unlit: 0.18, blanco: j.flash > 0 || (j.estado === 'embiste' && Math.floor(J.t * 12) % 2) ? 0.85 : 0 });
    if (SPR[`topo_${brazos}_luz`]) GL.sprite(`topo_${brazos}_luz`, x + tiembla + dxDe(`topo_${brazos}`), z + 0.002, { unlit: 1, lote: GL.lotes.brillo, tinte: [1, 1, 1, 0.9] });
  } else if (j.id === 'reina') {
    GL.plano('sombra_l', x, z - 0.1, { tinte: [1, 1, 1, 0.85], escala: 1.4 });
    const f = j.estado === 'acido' || j.estado === 'puesta' ? 1 + (Math.floor(J.t * 8) % 2) : Math.floor(J.t * 2) % 2;
    const dx = j.estado === 'muriendo' ? Math.sin(J.t * 60) * 0.08 : 0, pulso = 0.55 + 0.25 * Math.sin(J.t * 3);
    GL.sprite(`reina_${f}`, x + dx + dxDe(`reina_${f}`), z, { unlit: 0.2, blanco: j.flash > 0 ? 0.85 : 0 });
    GL.sprite(`reina_${f}_luz`, x + dx + dxDe(`reina_${f}`), z + 0.002, { unlit: 1, lote: GL.lotes.brillo, tinte: [1, 1, 1, pulso] });
    GL.halo(x, 1.0, z, 1.6, [0.5, 1, 0.3], 0.18 + 0.08 * Math.sin(J.t * 3));
  } else {
    if (j.invisible) return;
    const flota = Math.sin(J.t * 2.5) * 0.08;
    GL.plano('sombra_m', x, z - 0.05, { tinte: [1, 1, 1, 0.6] });
    const f = j.estado === 'orbes' || j.estado === 'anillo' || j.estado === 'pilares' || j.estado === 'invoca' ? 2 : j.estado === 'dormido' ? 3 : Math.floor(J.t * 3) % 2;
    const alto = j.estado === 'dormido' ? 0 : j.estado === 'cansado' ? 0.04 : 0.25 + flota, cansado = j.estado === 'cansado';
    GL.sprite(`ozmar_${f}`, x + dxDe(`ozmar_${f}`), z, { y: alto, unlit: 0.2, blanco: j.flash > 0 ? 0.85 : 0 });
    GL.sprite(`ozmar_${f}_luz`, x + dxDe(`ozmar_${f}`), z + 0.002, { y: alto, unlit: 1, lote: GL.lotes.brillo, tinte: [1, 1, 1, cansado ? 0.3 : 0.8] });
    if (cansado && Math.floor(J.t * 3) % 2) GL.sprite('destello', x + 0.3, z + 0.004, { y: 1.5, unlit: 1, lote: GL.lotes.brillo, tinte: [0.8, 0.6, 1, 0.8] });
    GL.halo(x, 1.3 + flota, z, 1.0, [0.6, 0.3, 1], 0.25 + 0.1 * Math.sin(J.t * 4));
  }
}

function dibujarMundo3d() {
  GL.limpiarLotes();
  const sx = J.temblor > 0 ? (Math.random() - 0.5) * J.temblor * 6 : 0, sy = J.temblor > 0 ? (Math.random() - 0.5) * J.temblor * 6 : 0;
  GL.camara((J.cam.x + sx) / T, (J.cam.y + sy) / T);
  const v = GL.vista();
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
  if (J.estado !== 'titulo') for (const j of Object.values(J.jefes)) dibujarJefe3d(j);
  dibujarFase2_3d();
  for (const b of J.botines) {
    const x = b.x / T, z = b.y / T, it = ITEMS[b.id];
    GL.plano('sombra_s', x, z, { escala: 0.6, tinte: [1, 1, 1, 0.6] });
    GL.sprite(it.icono, x, z, { y: (b.z / 16) * GL.KY + 0.06 + Math.sin(b.t * 4) * 0.04, escala: 0.85, lz: z });
    if ((it.rareza || 0) > 0) GL.halo(x, 0.4, z, 0.45, RAREZA_RGB[it.rareza], 0.25);
  }
  for (const r of J.proyectiles) {
    const x = r.x / T, z = r.y / T + 0.3;
    if (r.tipo === 'acido') { GL.plano('sombra_s', x, z, { escala: 0.5 }); GL.sprite('acido', x, z, { y: (r.z / 16) * GL.KY, unlit: 0.4 }); }
    else if (r.tipo === 'orbe') { GL.sprite('orbe', x, z, { y: 0.35, unlit: 1, lote: GL.lotes.brillo }); GL.halo(x, 0.55, z, 0.45, [0.6, 0.3, 1], 0.35); }
    else if (r.tipo === 'pluma') { const a = Math.atan2(r.vy, r.vx); GL.sprite(Math.abs(Math.cos(a)) > 0.7 ? 'pluma_h' : 'pluma_v', x, z, { y: 0.4, flip: Math.cos(a) < 0, unlit: 0.5 }); }
    else if (r.tipo === 'flecha_e') { const a = Math.atan2(r.vy, r.vx); GL.sprite(Math.abs(Math.cos(a)) > 0.7 ? 'flecha_h' : 'flecha_v', x, z, { y: 0.35, flip: Math.cos(a) < 0, tinte: [1, 0.6, 0.55, 1] }); }
    else if (r.tipo === 'semilla' || r.tipo === 'semilla_veneno') { GL.plano('sombra_s', x, z, { escala: 0.4 }); GL.sprite(r.tipo === 'semilla' ? 'semilla_p' : 'espora_p', x, z, { y: (r.z / 16) * GL.KY, unlit: 0.4 }); }
    else if (r.tipo === 'tornado') { GL.plano('sombra_m', x, z, { escala: 0.8, tinte: [1, 1, 1, 0.5] }); GL.sprite(`tornado_${Math.floor(J.t * 12) % 3}`, x, z, { unlit: 0.5, tinte: [1, 1, 1, 0.85] }); }
    else { GL.plano('sombra_s', x, z, { escala: 0.5 }); GL.sprite('roca_lanzada', x, z, { y: 0.35 }); }
  }
  for (const d of J.disparos) {
    const x = d.x / T, z = d.y / T + 0.2;
    if (d.magico) { GL.sprite('orbe', x, z, { y: 0.4, unlit: 1, lote: GL.lotes.brillo, tinte: [0.8, 0.7, 1, 1] }); GL.halo(x, 0.6, z, 0.5, [0.7, 0.4, 1], 0.4); }
    else if (d.espora) GL.sprite('espora_p', x, z, { y: 0.35, unlit: 1, lote: GL.lotes.brillo });
    else { const a = Math.atan2(d.vy, d.vx); GL.sprite(Math.abs(Math.cos(a)) > 0.7 ? 'flecha_h' : 'flecha_v', x, z, { y: 0.35, flip: Math.cos(a) < 0 }); }
  }
  for (const c of J.charcos) GL.plano(c.tipo === 'veneno' ? 'charco_veneno' : c.tipo === 'espora' ? 'charco_espora' : 'charco', c.x / T, c.y / T, { y: 0.008, unlit: 0.5, tinte: [1, 1, 1, Math.min(1, c.t) * 0.9] });
  dibujarRayosCielo();
  for (const a of J.avisosSuelo) if (a.t > 0) dibujarAviso(a.x / T, a.y / T, a.r, 1 - a.t / a.t0, a.estilo, a.v);
  for (const q of J.particulas) {
    const o = { y: (q.z / 16) * GL.KY, escala: q.s * 0.5 * (q.tenue ? 0.8 : 1), tinte: colorRGB(q.c, q.chispa ? Math.min(1, q.vida) * (q.tenue ? 0.35 : 0.9) : 1) };
    if (q.brilla) { o.unlit = 1; o.lote = GL.lotes.brillo; }
    GL.sprite('blanco', q.x / T, q.y / T, o);
  }
  for (const e of J.efectos) {
    const f = Math.min(e.cuadros - 1, Math.floor((e.t / e.dur) * e.cuadros)), x = e.x / T, z = e.y / T, y = (e.z / 16) * GL.KY;
    if (e.n === 'tajo') {
      const dir = e.dir, h = dir === 'ab' || dir === 'ar';
      const ox = dir === 'de' ? 0.62 : dir === 'iz' ? -0.62 : 0, oz = dir === 'ab' ? 0.32 : dir === 'ar' ? -0.32 : 0.02;
      GL.sprite(`tajo_${h ? 'h' : 'v'}_${f}`, x + ox, z + oz, { y: h ? y + (dir === 'ar' ? 0.5 : -0.1) : y - 0.2, flip: dir === 'iz', unlit: 1, lote: GL.lotes.brillo, tinte: [1, 0.96, 0.85, 0.85 * (1 - (e.t / e.dur) * 0.5)] });
    } else if (e.n === 'chispazo') GL.sprite(`chispazo_${f}`, x, z + 0.05, { y, unlit: 1, lote: GL.lotes.brillo });
    else GL.sprite(`${e.n}_${f}`, x, z + 0.04, { y });
  }
  // agua que destella, destellos de mineral en lo oscuro y rayos de luz
  for (let ty = v.z0; ty <= v.z1; ty++) for (let tx = v.x0; tx <= v.x1; tx++) {
    if (!enMundo(tx, ty)) continue;
    const i = idx(tx, ty);
    if (M.piso[i] === SUELO.AGUA) GL.plano(`aguab_${(Math.floor(J.t * 3) + tx * 7 + ty * 3) % 4}`, tx + 0.5, ty + 0.5, { y: -0.05, unlit: 0, lote: GL.lotes.brillo, tinte: [1, 1, 1, 0.9] });
    else if (M.mena[i] && M.visto[i]) {
      const fase = (J.t * 0.6 + hash2(tx, ty, 21)) % 1;
      if (fase < 0.12) { const k = Math.sin((fase / 0.12) * Math.PI); GL.sprite('destello', tx + 0.3 + hash2(tx, ty, 22) * 0.4, ty + 0.98, { y: GL.ALTO * 0.6, unlit: 1, lote: GL.lotes.brillo, tinte: [1, 1, 1, k], escala: 0.5 + k * 0.5 }); }
    }
  }
  for (const r of M.rayos) if (r.x > v.x0 - 4 && r.x < v.x1 + 4 && r.y > v.z0 - 4 && r.y < v.z1 + 6 && M.visto[idx(Math.round(r.x), Math.round(r.y))]) GL.rayo(r.x, r.y, r.ancho || 1.6, 3.2, r.c || [0.85, 0.9, 1], 0.12 * r.a + 0.03 * Math.sin(J.t * 0.7 + r.x));
  // grietas de las paredes que estás picando
  for (const [i, d] of M.danos) {
    const tx = i % N_MUNDO, ty = (i - tx) / N_MUNDO, m = M.muro[i];
    if (!m || tx < v.x0 || tx > v.x1 || ty < v.z0 || ty > v.z1) continue;
    const vm = INFO_MURO[m].vida * (M.mena[i] ? INFO_MENA[M.mena[i]].extra : 1), n = `grieta_${Math.min(3, 1 + Math.floor((d.v / vm) * 3))}`;
    GL.tapa(n, tx, ty); if (!muroEn(tx, ty + 1)) GL.cara(n, tx, ty + 1.004);
  }
  // casilla a la que apuntas
  if (J.estado === 'juego' && !J.panel && !J.pesca) {
    const s = J.inv[J.sel], it = s && asegurarItem(s.id);
    const { tx, ty } = casillaMira(J.jug);
    const muro = muroEn(tx, ty), pone = it && ['colocable', 'bloque', 'suelo', 'semilla', 'azada'].includes(it.tipo);
    const a = 0.55 + 0.3 * Math.sin(J.t * 8);
    if (muro && INFO_MURO[muro].poder < 999 && !enemigoCerca(J.jug, 30) && !pone) {
      GL.tapa('marca', tx, ty, { lote: GL.lotes.brillo, unlit: 1, tinte: [1, 0.95, 0.75, a] });
      if (!muroEn(tx, ty + 1)) GL.cara('marca', tx, ty + 1.006, { lote: GL.lotes.brillo, unlit: 1, tinte: [1, 0.95, 0.75, a] });
    } else if (pone && !muro) GL.plano('marca', tx + 0.5, ty + 0.5, { lote: GL.lotes.brillo, unlit: 1, tinte: [0.55, 1, 0.65, a] });
  }
  const parpadeo = 0.92 + 0.08 * Math.sin(J.t * 13) * Math.sin(J.t * 7.3);
  const luz = calcularLuz(v, fuentesFijas, fuentesDinamicas(), AMBIENTE, parpadeo);
  const reg = J.estado === 'titulo' ? 0 : M.region[idx(Math.floor(J.jug.x / T), Math.floor(J.jug.y / T))] || 0;
  GL.dibujar(luz, J.t, TONO_REGION[reg]);
}
const RAREZA_RGB = [[1, 1, 1], [0.4, 0.7, 1], [0.75, 0.48, 1], [1, 0.65, 0.25]];
const cacheRGB = new Map();
function colorRGB(hex, a) { let c = cacheRGB.get(hex); if (!c) { c = hexRgb(hex).map((v) => v / 255); cacheRGB.set(hex, c); } return [c[0], c[1], c[2], a]; }

// Números de daño, barras de vida de las criaturas y la «Z» de los guardianes dormidos.
function dibujarTextosMundo() {
  for (const e of J.enemigos) {
    if (!e.golpeadoT || J.t - e.golpeadoT > 3 || e.vida >= e.vidaMax) continue;
    const p = GL.aPantalla(e.x / T, ((e.radio > 8 ? 30 : 20) / 16) * GL.KY, e.y / T);
    const w = e.radio > 8 ? 20 : 14, x = Math.round(p.x * LW - w / 2), y = Math.round(p.y * LH);
    ctx.fillStyle = COL.negro; ctx.fillRect(x - 1, y - 1, w + 2, 4);
    ctx.fillStyle = '#5a1a1a'; ctx.fillRect(x, y, w, 2);
    ctx.fillStyle = '#e84040'; ctx.fillRect(x, y, Math.round(w * Math.max(0, e.vida / e.vidaMax)), 2);
  }
  for (const n of J.numeros) {
    const p = GL.aPantalla(n.x / T, (n.alto / 16) * GL.KY, n.y / T);
    ctx.globalAlpha = Math.min(1, n.vida * 3);
    texto(ctx, n.txt, p.x * LW, p.y * LH, n.c, { alin: 'c', borde: COL.negro });
  }
  ctx.globalAlpha = 1;
  if (J.estado !== 'titulo') for (const m of M.mercaderes) {
    if (!m.ent || Math.hypot(m.ent.x - J.jug.x, m.ent.y - J.jug.y) > 60) continue;
    const p = GL.aPantalla(m.ent.x / T, (26 / 16) * GL.KY, m.ent.y / T);
    texto(ctx, MERCADERES[m.tipo].nombre, p.x * LW, p.y * LH, MERCADERES[m.tipo].colores[0], { alin: 'c', borde: COL.negro });
  }
  if (J.estado !== 'titulo') for (const j of Object.values(J.jefes)) {
    if (j.estado !== 'dormido') continue;
    const k = (J.t * 0.6) % 1, p = GL.aPantalla(j.x / T + 0.7 + k * 0.4, ((40 + k * 14) / 16) * GL.KY, j.y / T);
    if (p.x < 0 || p.x > 1 || p.y < 0 || p.y > 1) continue;
    ctx.globalAlpha = 1 - k; texto(ctx, 'Z', p.x * LW, p.y * LH, '#e8e0f0', { borde: COL.negro }); ctx.globalAlpha = 1;
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
  if (VER_FPS) texto(ctx, `${fps} fps · ${GL.W}x${GL.H} · ${GL.trozos} trozos`, 4, LH - 30, '#9fffa8', { borde: COL.negro });
}

// ---------------------------------------------------------------- arranque

// ---------------------------------------------------------------- caché del arte
// Hornear todos los modelos tarda (segundos en un móvil). En el juego de un solo archivo (todo el código dentro de
// la página) el atlas horneado se guarda en IndexedDB con una clave sacada del propio código: si el código cambia,
// la clave cambia y se vuelve a hornear.
function claveArte() {
  const sc = [...document.scripts];
  if (!sc.length || sc.some((x) => x.src)) return null;
  let h = 2166136261, n = 0;
  for (const x of sc) { const t = x.textContent; n += t.length; for (let i = 0; i < t.length; i++) { h ^= t.charCodeAt(i); h = Math.imul(h, 16777619); } }
  return `arte-${(h >>> 0).toString(36)}-${n}`;
}
function baseArte() {
  return new Promise((ok, mal) => { const r = indexedDB.open('corazon-dormido', 1); r.onupgradeneeded = () => r.result.createObjectStore('arte'); r.onsuccess = () => ok(r.result); r.onerror = () => mal(r.error); });
}
async function leerArteCache(clave) {
  try {
    const db = await baseArte();
    const v = await new Promise((ok, mal) => { const q = db.transaction('arte').objectStore('arte').get('atlas'); q.onsuccess = () => ok(q.result); q.onerror = () => mal(q.error); });
    if (!v || v.clave !== clave) return false;
    const lienzoDe = async (blob) => { const b = await createImageBitmap(blob, { premultiplyAlpha: 'none', colorSpaceConversion: 'none' }); const c = document.createElement('canvas'); c.width = b.width; c.height = b.height; c.getContext('2d').drawImage(b, 0, 0); return c; };
    ATLAS = await lienzoDe(v.color); ATLAS_N = await lienzoDe(v.normales);
    Object.assign(SPR, v.spr); Object.assign(META_SPR, v.meta); ZONA_DIN.y = v.zonaY;
    Personaje.inicial();
    return true;
  } catch (e) { return false; }
}
async function guardarArteCache(clave) {
  try {
    const blob = (c) => new Promise((ok) => c.toBlob(ok, 'image/png'));
    const [color, normales] = await Promise.all([blob(ATLAS), blob(ATLAS_N)]);
    const db = await baseArte();
    db.transaction('arte', 'readwrite').objectStore('arte').put({ clave, color, normales, spr: SPR, meta: META_SPR, zonaY: ZONA_DIN.y }, 'atlas');
  } catch (e) { /* sin IndexedDB: se hornea cada vez */ }
}
async function prepararArte() {
  const clave = claveArte();
  if (clave && (await leerArteCache(clave))) return;
  // aviso mientras se hornea (la primera vez)
  const dpr = window.devicePixelRatio || 1;
  lienzo.width = Math.round(window.innerWidth * dpr / 4); lienzo.height = Math.round(window.innerHeight * dpr / 4);
  lienzo.style.width = window.innerWidth + 'px'; lienzo.style.height = window.innerHeight + 'px';
  ctx.imageSmoothingEnabled = false; ctx.fillStyle = '#000'; ctx.fillRect(0, 0, lienzo.width, lienzo.height);
  texto(ctx, 'DESPERTANDO EL MUNDO…', lienzo.width / 2, Math.round(lienzo.height / 2), '#c86a8a', { alin: 'c' });
  await new Promise((ok) => setTimeout(ok, 30));
  construirArte();
  if (clave) guardarArteCache(clave);
}

async function iniciar(datosHot) {
  await prepararArte();
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
  hayPartida = !!g || !!leerPartidaV3();
  if (g) cargarMundo(g.mundo); else generarMundo(20261028);
  J.jug = nuevoJugador();
  J.cam.x = CENTRO * T; J.cam.y = CENTRO * T;
  if (datosHot && datosHot.enJuego && hayPartida) empezar(true);
  requestAnimationFrame((ms) => { ultimo = ms; cuadro(ms); });
}

try { window.claude?.hot?.snapshot?.(() => { guardarPartida(); return { enJuego: J.estado === 'juego' }; }); } catch (e) { /* fuera del visor */ }
if (window.claude?.hot?.ready) window.claude.hot.ready(iniciar); else iniciar(window.claude?.hot?.data ?? {});
