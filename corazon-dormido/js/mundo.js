'use strict';
// El mundo: generación por semilla (cuevas, biomas en anillos, menas, el Corazón en el centro y la guarida
// del Topo Rey), las casillas, la luz que se propaga por las cuevas y el mapa de lo explorado.

let M = null; // el mundo actual

const idx = (x, y) => y * N_MUNDO + x;
const enMundo = (x, y) => x >= 0 && y >= 0 && x < N_MUNDO && y < N_MUNDO;
function hash2(x, y, s = 0) {
  let h = Math.imul(x | 0, 374761393) + Math.imul(y | 0, 668265263) + Math.imul(s, 2246822519);
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
}

function crearRuido(sem) {
  const r = azar(sem), p = new Uint8Array(512), v = new Float32Array(256);
  for (let i = 0; i < 256; i++) { v[i] = r(); p[i] = i; }
  for (let i = 255; i > 0; i--) { const j = Math.floor(r() * (i + 1)); [p[i], p[j]] = [p[j], p[i]]; }
  for (let i = 0; i < 256; i++) p[i + 256] = p[i];
  const h = (x, y) => v[p[(x & 255) + p[y & 255]]];
  const s = (t) => t * t * (3 - 2 * t);
  return (x, y) => {
    const xi = Math.floor(x), yi = Math.floor(y), u = s(x - xi), w = s(y - yi);
    const a = h(xi, yi), b = h(xi + 1, yi), c = h(xi, yi + 1), d = h(xi + 1, yi + 1);
    return a + (b - a) * u + (c - a) * w + (a - b - c + d) * u * w;
  };
}
function fbm(n, x, y, oct = 3) {
  let s = 0, a = 0.5, f = 1, t = 0;
  for (let o = 0; o < oct; o++) { s += n(x * f + o * 17.3, y * f - o * 9.1) * a; t += a; a *= 0.5; f *= 2.03; }
  return s / t;
}

function generarMundo(sem) {
  const N = N_MUNDO, NN = N * N;
  const n1 = crearRuido(sem), n2 = crearRuido(sem + 11), n3 = crearRuido(sem + 23), n4 = crearRuido(sem + 37);
  const r = azar(sem + 99);
  GL.olvidarTodo();
  M = {
    sem, muro: new Uint8Array(NN), mena: new Uint8Array(NN), piso: new Uint8Array(NN), visto: new Uint8Array(NN),
    bloqueo: new Uint8Array(NN), objetos: new Map(), luces: new Set(), danos: new Map(), arena: null, pedestales: [],
    mapa: null, mapaSucio: true,
  };
  const { muro, mena, piso } = M;
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const i = idx(x, y), d = Math.hypot(x - CENTRO, y - CENTRO);
    const dd = d + (fbm(n2, x / 28, y / 28) - 0.5) * 34;
    const bioma = dd < 44 ? BIOMA.TIERRA : dd < 82 ? BIOMA.ARCILLA : BIOMA.PIEDRA;
    piso[i] = bioma;
    const cueva = fbm(n1, x / 12, y / 12, 3) + (n3(x / 4, y / 4) - 0.5) * 0.14;
    const umbral = bioma === BIOMA.TIERRA ? 0.57 : bioma === BIOMA.ARCILLA ? 0.555 : 0.54;
    if (cueva > umbral) continue;
    muro[i] = bioma;
    const v = n4(x / 4.2, y / 4.2);
    if (v > 0.76) mena[i] = bioma === BIOMA.TIERRA ? MENA.COBRE : bioma === BIOMA.ARCILLA ? (v > 0.86 ? MENA.COBRE : MENA.ESTANO) : MENA.HIERRO;
  }
  const cavar = (cx, cy, rad, conPiso = 0) => {
    for (let y = Math.floor(cy - rad); y <= cy + rad; y++) for (let x = Math.floor(cx - rad); x <= cx + rad; x++) {
      if (!enMundo(x, y) || Math.hypot(x - cx, y - cy) > rad) continue;
      const i = idx(x, y); muro[i] = 0; mena[i] = 0; if (conPiso) piso[i] = conPiso;
    }
  };
  // túneles que salen del centro para que siempre haya por dónde explorar
  for (let k = 0; k < 6; k++) {
    let a = (k / 6) * Math.PI * 2 + r() * 0.8, x = CENTRO, y = CENTRO;
    const largo = 40 + r() * 50;
    for (let s = 0; s < largo; s++) {
      a += (r() - 0.5) * 0.7; x += Math.cos(a); y += Math.sin(a);
      cavar(x, y, s < 12 ? 1.2 : r() < 0.15 ? 2.2 : 1.3);
    }
  }
  // cavernas sueltas
  for (let k = 0; k < 70; k++) {
    const a = r() * Math.PI * 2, d = 14 + r() * 100;
    cavar(CENTRO + Math.cos(a) * d, CENTRO + Math.sin(a) * d, 2.5 + r() * 4);
  }
  // vetas de cobre cerca del centro para empezar rápido
  for (let k = 0; k < 6; k++) {
    const a = r() * Math.PI * 2, d = 11 + r() * 9, cx = CENTRO + Math.cos(a) * d, cy = CENTRO + Math.sin(a) * d;
    for (let y = Math.floor(cy - 2); y <= cy + 2; y++) for (let x = Math.floor(cx - 2); x <= cx + 2; x++) {
      if (Math.hypot(x - cx, y - cy) > 1.9 || !muro[idx(x, y)]) continue;
      mena[idx(x, y)] = MENA.COBRE;
    }
  }
  // borde de roca madre
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const d = Math.hypot(x - CENTRO, y - CENTRO) + (n2(x / 9, y / 9) - 0.5) * 6;
    if (d > 122 || x < 2 || y < 2 || x > N - 3 || y > N - 3) { const i = idx(x, y); muro[i] = MURO.ROCA; mena[i] = 0; }
  }
  // el santuario del Corazón: suelo antiguo y un anillo de muros con cuatro entradas
  for (let y = CENTRO - 10; y <= CENTRO + 10; y++) for (let x = CENTRO - 10; x <= CENTRO + 10; x++) {
    const d = Math.hypot(x - CENTRO, y - CENTRO), i = idx(x, y), ang = Math.atan2(y - CENTRO, x - CENTRO);
    if (d < 7.5) { muro[i] = 0; mena[i] = 0; piso[i] = BIOMA.RUINA; }
    else if (d < 9.2) {
      const puerta = [0, Math.PI / 2, Math.PI, -Math.PI / 2].some((p) => Math.abs(Math.atan2(Math.sin(ang - p), Math.cos(ang - p))) < 0.2);
      piso[i] = BIOMA.RUINA; mena[i] = 0;
      muro[i] = puerta ? 0 : (hash2(x, y, 5) < 0.85 ? MURO.RUINA : 0);
    } else if (d < 10.5 && hash2(x, y, 6) < 0.5) piso[i] = BIOMA.RUINA;
  }
  for (const [dx, dy] of [[0, -1], [1, 0], [0, 1], [-1, 0]]) // las entradas siempre llevan a algún túnel
    for (let s = 8; s < 15; s++) cavar(CENTRO + dx * s, CENTRO + dy * s, 1.1);
  // la guarida del Topo Rey, en la tierra
  const aT = r() * Math.PI * 2, dT = 30;
  const ax = Math.round(CENTRO + Math.cos(aT) * dT), ay = Math.round(CENTRO + Math.sin(aT) * dT);
  for (let y = ay - 10; y <= ay + 10; y++) for (let x = ax - 10; x <= ax + 10; x++) {
    const d = Math.hypot(x - ax, y - ay), i = idx(x, y);
    if (d < 7.5) { muro[i] = 0; mena[i] = 0; piso[i] = 5; }
    else if (d < 9.5 && muro[i] === 0) { muro[i] = MURO.TIERRA; }
  }
  for (let s = 0; s < 3; s++) cavar(ax + Math.cos(aT) * (9 + s), ay + Math.sin(aT) * (9 + s), 1.2); // entrada al fondo
  M.arena = { x: ax, y: ay };
  for (let k = 0; k < 6; k++) {
    const a = (k / 6) * Math.PI * 2 + 0.3, x = Math.round(ax + Math.cos(a) * 6.5), y = Math.round(ay + Math.sin(a) * 6.5);
    if (!muro[idx(x, y)]) ponerObjeto(x, y, 'hongo');
  }

  // el Corazón y sus tres pedestales
  ponerObjeto(CENTRO, CENTRO, 'corazon');
  for (let y = CENTRO - 1; y <= CENTRO; y++) for (let x = CENTRO - 1; x <= CENTRO + 1; x++) M.bloqueo[idx(x, y)] = 1;
  for (const [dx, dy] of [[-4, 0], [4, 0], [0, -4]]) { ponerObjeto(CENTRO + dx, CENTRO + dy, 'pedestal', 0); M.pedestales.push({ x: CENTRO + dx, y: CENTRO + dy }); }
  for (const [dx, dy] of [[-3, 3], [3, 3], [-3, -3], [3, -3]]) ponerObjeto(CENTRO + dx, CENTRO + dy, 'antorcha');

  // plantas, raíces y cristales
  const junto = (x, y) => [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => muro[idx(x + dx, y + dy)]);
  for (let y = 3; y < N - 3; y++) for (let x = 3; x < N - 3; x++) {
    const i = idx(x, y);
    if (muro[i] || M.objetos.has(i) || piso[i] === BIOMA.RUINA || piso[i] === 5) continue;
    if (Math.hypot(x - CENTRO, y - CENTRO) < 11) continue;
    const b = piso[i], v = r(), pegado = junto(x, y);
    if ((b === BIOMA.TIERRA || b === BIOMA.ARCILLA) && pegado && v < 0.045) ponerObjeto(x, y, 'raiz');
    else if (b === BIOMA.TIERRA && v > 0.982) ponerObjeto(x, y, 'arbusto');
    else if ((b === BIOMA.ARCILLA || b === BIOMA.PIEDRA) && v > 0.985) ponerObjeto(x, y, 'hongo');
    else if (b === BIOMA.PIEDRA && pegado && v < 0.022) ponerObjeto(x, y, 'cristal');
  }
  // unas cuantas raíces seguras cerca del santuario
  let puestas = 0;
  for (let k = 0; k < 4000 && puestas < 8; k++) {
    const a = r() * Math.PI * 2, d = 10 + r() * 9, x = Math.round(CENTRO + Math.cos(a) * d), y = Math.round(CENTRO + Math.sin(a) * d), i = idx(x, y);
    if (!muro[i] && !M.objetos.has(i) && junto(x, y) && piso[i] !== BIOMA.RUINA) { ponerObjeto(x, y, 'raiz'); puestas++; }
  }
  M.mapa = null;
  return M;
}

// ---------------------------------------------------------------- casillas

function muroEn(x, y) { return enMundo(x, y) ? M.muro[idx(x, y)] : MURO.ROCA; }
function objetoEn(x, y) { return enMundo(x, y) ? M.objetos.get(idx(x, y)) : undefined; }
function solido(x, y) {
  if (!enMundo(x, y)) return true;
  const i = idx(x, y);
  if (M.muro[i] || M.bloqueo[i]) return true;
  const o = M.objetos.get(i);
  return !!(o && OBJ[o.tipo].bloquea);
}
function ponerObjeto(x, y, tipo, extra = 0) {
  const i = idx(x, y), o = { tipo, x, y, extra, vida: OBJ[tipo].vida };
  M.objetos.set(i, o);
  if (OBJ[tipo].luz || tipo === 'corazon' || tipo === 'pedestal') M.luces.add(i);
  marcarMapa(i); GL.tocar(x, y);
  return o;
}
function quitarObjeto(x, y) { const i = idx(x, y); M.objetos.delete(i); M.luces.delete(i); marcarMapa(i); GL.tocar(x, y); }
function romperMuro(x, y) {
  const i = idx(x, y);
  M.muro[i] = 0; M.mena[i] = 0; M.danos.delete(i);
  marcarMapa(i); GL.tocar(x, y);
}
// Daño acumulado en una pared (se cura sola si la dejas un rato).
function danoMuro(x, y) { const d = M.danos.get(idx(x, y)); return d ? d.v : 0; }

// ---------------------------------------------------------------- luz

const MARGEN_LUZ = 10;
const LUZ = { X0: 0, Y0: 0, W: 0, H: 0, r: null, g: null, b: null, marca: null, sello: 0, cola: null, datos: null };

// fuentes: [{x, y (px), r (casillas), c: [r,g,b], k}]. Devuelve la luz de la vista (tx0..tx0+w) en un canvas de 1 px por casilla.
function calcularLuz(tx0, ty0, w, h, fuentes, ambiente) {
  const X0 = tx0 - MARGEN_LUZ, Y0 = ty0 - MARGEN_LUZ, W = w + MARGEN_LUZ * 2, H = h + MARGEN_LUZ * 2, n = W * H;
  if (!LUZ.r || LUZ.r.length < n) {
    LUZ.r = new Float32Array(n); LUZ.g = new Float32Array(n); LUZ.b = new Float32Array(n);
    LUZ.marca = new Int32Array(n); LUZ.cola = new Int32Array(n);
  }
  LUZ.X0 = X0; LUZ.Y0 = Y0; LUZ.W = W; LUZ.H = H;
  const R = LUZ.r, G = LUZ.g, B = LUZ.b, marca = LUZ.marca, cola = LUZ.cola;
  R.fill(0, 0, n); G.fill(0, 0, n); B.fill(0, 0, n);
  for (const f of fuentes) {
    const sx = Math.floor(f.x / T), sy = Math.floor(f.y / T);
    if (sx < X0 - f.r || sy < Y0 - f.r || sx >= X0 + W + f.r || sy >= Y0 + H + f.r) continue;
    if (sx < X0 || sy < Y0 || sx >= X0 + W || sy >= Y0 + H) continue;
    const sello = ++LUZ.sello;
    let ini = 0, fin = 0;
    cola[fin++] = (sy - Y0) * W + (sx - X0); marca[cola[0]] = sello;
    const fr = f.r, k = f.k ?? 1;
    while (ini < fin) {
      const li = cola[ini++], lx = li % W, ly = (li - lx) / W, cx = lx + X0, cy = ly + Y0;
      const dist = Math.hypot((cx + 0.5) * T - f.x, (cy + 0.5) * T - f.y) / T;
      if (dist > fr) continue;
      let a = 1 - dist / fr; a = a * a * (3 - 2 * a) * k;
      R[li] += f.c[0] * a; G[li] += f.c[1] * a; B[li] += f.c[2] * a;
      if (!(cx === sx && cy === sy) && (!enMundo(cx, cy) || M.muro[idx(cx, cy)])) continue; // la pared se ilumina, pero no deja pasar
      if (lx > 0 && marca[li - 1] !== sello) { marca[li - 1] = sello; cola[fin++] = li - 1; }
      if (lx < W - 1 && marca[li + 1] !== sello) { marca[li + 1] = sello; cola[fin++] = li + 1; }
      if (ly > 0 && marca[li - W] !== sello) { marca[li - W] = sello; cola[fin++] = li - W; }
      if (ly < H - 1 && marca[li + W] !== sello) { marca[li + W] = sello; cola[fin++] = li + W; }
    }
  }
  // datos de la vista (con 1 casilla de borde para que el suavizado no corte), para la textura de luz
  const vw = w + 2, vh = h + 2;
  if (!LUZ.datos || LUZ.datos.length < vw * vh * 4) LUZ.datos = new Uint8Array(vw * vh * 4);
  const d = LUZ.datos, [ar, ag, ab] = ambiente, k = 255 / Math.sqrt(2.4);
  for (let y = 0; y < vh; y++) for (let x = 0; x < vw; x++) {
    const wx = tx0 - 1 + x, wy = ty0 - 1 + y, li = (wy - Y0) * W + (wx - X0), o = (y * vw + x) * 4;
    let r = R[li], g = G[li], b = B[li];
    if (enMundo(wx, wy)) {
      const i = idx(wx, wy);
      if (Math.max(r, g, b) > 0.16 && !M.visto[i]) { M.visto[i] = 1; marcarMapa(i); }
      if (M.visto[i]) { r += ar; g += ag; b += ab; }
    }
    d[o] = Math.min(255, Math.sqrt(r) * k); d[o + 1] = Math.min(255, Math.sqrt(g) * k); d[o + 2] = Math.min(255, Math.sqrt(b) * k); d[o + 3] = 255;
  }
  return { datos: d.subarray(0, vw * vh * 4), x0: tx0 - 1, y0: ty0 - 1, w: vw, h: vh };
}
// Luz en un punto del mundo (para no hacer aparecer criaturas donde hay luz).
function luzEn(tx, ty) {
  const li = (ty - LUZ.Y0) * LUZ.W + (tx - LUZ.X0);
  if (tx < LUZ.X0 || ty < LUZ.Y0 || tx >= LUZ.X0 + LUZ.W || ty >= LUZ.Y0 + LUZ.H) return 0;
  return Math.max(LUZ.r[li], LUZ.g[li], LUZ.b[li]);
}

// ---------------------------------------------------------------- mapa de lo explorado

function colorMapa(i) {
  const o = M.objetos.get(i);
  if (o && OBJ[o.tipo].mapa) return OBJ[o.tipo].mapa;
  if (M.mena[i]) return INFO_MENA[M.mena[i]].mapa;
  if (M.muro[i]) return INFO_MURO[M.muro[i]].mapa;
  return SUELO_MAPA[M.piso[i]] || '#201820';
}
function marcarMapa(i) {
  if (!M || !M.mapa || !M.visto[i]) return;
  const c = colorMapa(i), d = M.mapa.img.data, o = i * 4;
  d[o] = parseInt(c.slice(1, 3), 16); d[o + 1] = parseInt(c.slice(3, 5), 16); d[o + 2] = parseInt(c.slice(5, 7), 16); d[o + 3] = 255;
  M.mapaSucio = true;
}
function lienzoMapa() {
  if (!M.mapa) {
    const cv = document.createElement('canvas'); cv.width = N_MUNDO; cv.height = N_MUNDO;
    const cx = cv.getContext('2d');
    M.mapa = { cv, cx, img: cx.createImageData(N_MUNDO, N_MUNDO) };
    for (let i = 0; i < N_MUNDO * N_MUNDO; i++) if (M.visto[i]) marcarMapa(i);
  }
  if (M.mapaSucio) { M.mapa.cx.putImageData(M.mapa.img, 0, 0); M.mapaSucio = false; }
  return M.mapa.cv;
}

// ---------------------------------------------------------------- guardar

function rle(u8) {
  const out = [];
  for (let i = 0; i < u8.length;) {
    const v = u8[i]; let n = 1;
    while (i + n < u8.length && u8[i + n] === v && n < 255) n++;
    out.push(v, n); i += n;
  }
  let s = '';
  for (let i = 0; i < out.length; i += 8192) s += String.fromCharCode.apply(null, out.slice(i, i + 8192));
  return btoa(s);
}
function desRle(b64, n) {
  const s = atob(b64), u8 = new Uint8Array(n);
  let p = 0;
  for (let i = 0; i < s.length; i += 2) { const v = s.charCodeAt(i), c = s.charCodeAt(i + 1); u8.fill(v, p, p + c); p += c; }
  return u8;
}
function datosMundo() {
  return {
    sem: M.sem, muro: rle(M.muro), mena: rle(M.mena), visto: rle(M.visto),
    objetos: [...M.objetos.values()].map((o) => [o.x, o.y, o.tipo, o.extra]),
  };
}
function cargarMundo(g) {
  generarMundo(g.sem);
  const NN = N_MUNDO * N_MUNDO;
  M.muro = desRle(g.muro, NN); M.mena = desRle(g.mena, NN); M.visto = desRle(g.visto, NN);
  M.objetos.clear(); M.luces.clear();
  for (const [x, y, tipo, extra] of g.objetos) ponerObjeto(x, y, tipo, extra);
  M.mapa = null;
  GL.olvidarTodo();
}
