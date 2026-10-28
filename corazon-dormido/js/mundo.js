'use strict';
// El mundo: generación por semilla del anillo interior (Las Raíces en el centro; Cuevas de Arcilla y Ruinas
// Olvidadas a cada lado; la Gran Muralla en el borde), casillas, luz (lo fijo guardado en caché y lo que se
// mueve cada cuadro), mapa de lo explorado y guardado.

let M = null;

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
const difAng = (a, b) => Math.abs(Math.atan2(Math.sin(a - b), Math.cos(a - b)));

// ---------------------------------------------------------------- generación

function generarMundo(sem) {
  const N = N_MUNDO, NN = N * N;
  const n1 = crearRuido(sem), n2 = crearRuido(sem + 11), n3 = crearRuido(sem + 23), n4 = crearRuido(sem + 37), n5 = crearRuido(sem + 51), n6 = crearRuido(sem + 67);
  const r = azar(sem + 99);
  GL.olvidarTodo();
  const lado = r() * Math.PI * 2; // hacia dónde quedan las Cuevas de Arcilla (las Ruinas, al otro lado)
  M = {
    sem, lado, muro: new Uint8Array(NN), mena: new Uint8Array(NN), piso: new Uint8Array(NN), region: new Uint8Array(NN), visto: new Uint8Array(NN),
    bloqueo: new Uint8Array(NN), objetos: new Map(), luces: new Set(), danos: new Map(), guaridas: {}, estatuas: {},
    mapa: null, mapaSucio: true, verLuz: 0, rayos: [],
  };
  const { muro, mena, piso, region } = M;
  const regionDe = (x, y, d) => {
    const dd = d + (fbm(n2, x / 26, y / 26) - 0.5) * 30;
    if (dd < R_RAICES) return REGION.RAICES;
    const ang = Math.atan2(y - CENTRO, x - CENTRO) + (fbm(n6, x / 40, y / 40) - 0.5) * 1.4;
    return difAng(ang, lado) < Math.PI / 2 ? REGION.ARCILLA : REGION.RUINAS;
  };
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const i = idx(x, y), d = Math.hypot(x - CENTRO, y - CENTRO);
    const reg = regionDe(x, y, d);
    region[i] = reg;
    const cesped = reg === REGION.RAICES && fbm(n5, x / 18, y / 18) > 0.6;
    piso[i] = reg === REGION.RAICES ? (cesped ? SUELO.CESPED : n5(x / 7, y / 7) > 0.8 ? SUELO.BARRO : SUELO.TIERRA) : reg === REGION.ARCILLA ? SUELO.ARCILLA : SUELO.PIEDRA;
    const cueva = fbm(n1, x / 12, y / 12, 3) + (n3(x / 4, y / 4) - 0.5) * 0.14;
    const umbral = reg === REGION.RAICES ? 0.565 : reg === REGION.ARCILLA ? 0.555 : 0.545;
    if (cueva > umbral) continue;
    muro[i] = reg === REGION.RAICES ? MURO.TIERRA : reg === REGION.ARCILLA ? MURO.ARCILLA : MURO.PIEDRA;
    const v = n4(x / 4.2, y / 4.2), oro = n6(x / 3, y / 3);
    if (reg === REGION.RAICES && v > 0.76) mena[i] = MENA.COBRE;
    else if (reg === REGION.ARCILLA) mena[i] = oro > 0.86 ? MENA.ORO : v > 0.77 ? MENA.ESTANO : v < 0.13 ? MENA.COBRE : 0;
    else if (reg === REGION.RUINAS) mena[i] = oro > 0.87 ? MENA.ORO : v > 0.77 ? MENA.HIERRO : 0;
  }
  const cavar = (cx, cy, rad, conPiso = 0) => {
    for (let y = Math.floor(cy - rad); y <= cy + rad; y++) for (let x = Math.floor(cx - rad); x <= cx + rad; x++) {
      if (!enMundo(x, y) || Math.hypot(x - cx, y - cy) > rad) continue;
      const i = idx(x, y); muro[i] = 0; mena[i] = 0; if (conPiso) piso[i] = conPiso;
    }
  };
  // la zanja: un anillo abierto de grava que marca el borde de Las Raíces
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const d = Math.hypot(x - CENTRO, y - CENTRO), dd = d + (fbm(n2, x / 26, y / 26) - 0.5) * 30;
    if (Math.abs(dd - R_RAICES) < 1.3) { const i = idx(x, y); muro[i] = 0; mena[i] = 0; piso[i] = SUELO.GRAVA; }
  }
  // túneles desde el centro y cavernas
  for (let k = 0; k < 7; k++) {
    let a = (k / 7) * Math.PI * 2 + r() * 0.6, x = CENTRO, y = CENTRO;
    const largo = 50 + r() * 70;
    for (let s = 0; s < largo; s++) { a += (r() - 0.5) * 0.6; x += Math.cos(a); y += Math.sin(a); cavar(x, y, s < 12 ? 1.2 : r() < 0.15 ? 2.2 : 1.3); }
  }
  for (let k = 0; k < 120; k++) {
    const a = r() * Math.PI * 2, d = 14 + r() * (R_MURALLA - 22);
    const cx = CENTRO + Math.cos(a) * d, cy = CENTRO + Math.sin(a) * d, rad = 2.5 + r() * 4.5;
    cavar(cx, cy, rad);
    if (rad > 5 && r() < 0.45) M.rayos.push({ x: cx, y: cy, a: 0.6 + r() * 0.8 }); // rayo de luz desde una grieta del techo
  }
  // vetas de cobre cerca del centro
  for (let k = 0; k < 7; k++) {
    const a = r() * Math.PI * 2, d = 11 + r() * 10, cx = CENTRO + Math.cos(a) * d, cy = CENTRO + Math.sin(a) * d;
    for (let y = Math.floor(cy - 2); y <= cy + 2; y++) for (let x = Math.floor(cx - 2); x <= cx + 2; x++) if (Math.hypot(x - cx, y - cy) <= 1.9 && muro[idx(x, y)]) mena[idx(x, y)] = MENA.COBRE;
  }
  // charcas
  for (let k = 0; k < 60; k++) {
    const a = r() * Math.PI * 2, d = 16 + r() * (R_MURALLA - 26), cx = Math.round(CENTRO + Math.cos(a) * d), cy = Math.round(CENTRO + Math.sin(a) * d), rad = 1.8 + r() * 2.8;
    for (let y = Math.floor(cy - rad); y <= cy + rad; y++) for (let x = Math.floor(cx - rad); x <= cx + rad; x++) {
      if (!enMundo(x, y) || Math.hypot(x - cx, (y - cy) * 1.2) > rad + (n3(x / 2, y / 2) - 0.5) * 1.5) continue;
      let orilla = true;
      for (let dy = -1; dy <= 1 && orilla; dy++) for (let dx = -1; dx <= 1; dx++) if (muro[idx(x + dx, y + dy)]) { orilla = false; break; }
      if (orilla) piso[idx(x, y)] = SUELO.AGUA;
    }
  }

  // ---- estructuras
  // colmenas en la arcilla (y la de la Reina)
  const colmena = (cx, cy, rad, reina) => {
    for (let y = Math.floor(cy - rad - 2); y <= cy + rad + 2; y++) for (let x = Math.floor(cx - rad - 2); x <= cx + rad + 2; x++) {
      if (!enMundo(x, y)) continue;
      const d = Math.hypot(x - cx, y - cy) + (n3(x / 2, y / 2) - 0.5) * 2, i = idx(x, y);
      if (d < rad) { muro[i] = 0; mena[i] = 0; piso[i] = SUELO.COLMENA; region[i] = REGION.ARCILLA; }
      else if (d < rad + 2) { muro[i] = MURO.COLMENA; mena[i] = 0; region[i] = REGION.ARCILLA; }
    }
    const bocas = reina ? 3 : 2;
    for (let b = 0; b < bocas; b++) { const a = r() * Math.PI * 2; for (let s = rad - 1; s < rad + 5; s++) cavar(cx + Math.cos(a) * s, cy + Math.sin(a) * s, 1.3, SUELO.COLMENA); }
    return { x: cx, y: cy, r: rad };
  };
  const puntoLado = (d, enArcilla, desvio = 1.2) => { const a = lado + (enArcilla ? 0 : Math.PI) + (r() - 0.5) * desvio * 2; return [Math.round(CENTRO + Math.cos(a) * d), Math.round(CENTRO + Math.sin(a) * d)]; };
  const colmenas = [];
  for (let k = 0; k < 6; k++) { const [x, y] = puntoLado(62 + r() * 60, true, 1.25); colmenas.push(colmena(x, y, 4 + r() * 3, false)); }
  { const [x, y] = puntoLado(96, true, 0.6); M.guaridas.reina = colmena(x, y, 11, true); }
  // ruinas: salas de piedra labrada, laberintos y la sala del trono
  const salas = [];
  const sala = (x0, y0, w, h, puertas = 2) => {
    for (let y = y0 - 1; y <= y0 + h; y++) for (let x = x0 - 1; x <= x0 + w; x++) {
      if (!enMundo(x, y)) continue;
      const i = idx(x, y), borde = x === x0 - 1 || y === y0 - 1 || x === x0 + w || y === y0 + h;
      mena[i] = 0; region[i] = REGION.RUINAS;
      if (borde) muro[i] = MURO.LADRILLO; else { muro[i] = 0; piso[i] = SUELO.LOSA; }
    }
    for (let p = 0; p < puertas; p++) {
      const lado4 = Math.floor(r() * 4), t = lado4 < 2 ? x0 + 1 + Math.floor(r() * (w - 2)) : y0 + 1 + Math.floor(r() * (h - 2));
      const [px, py, dx, dy] = lado4 === 0 ? [t, y0 - 1, 0, -1] : lado4 === 1 ? [t, y0 + h, 0, 1] : lado4 === 2 ? [x0 - 1, t, -1, 0] : [x0 + w, t, 1, 0];
      for (let s = 0; s < 6; s++) { const i = idx(px + dx * s, py + dy * s); if (!enMundo(px + dx * s, py + dy * s)) break; if (s > 0 && !muro[i]) break; muro[i] = 0; mena[i] = 0; if (s === 0) piso[i] = SUELO.LOSA; }
    }
    salas.push({ x0, y0, w, h });
  };
  for (let k = 0; k < 30; k++) {
    const [cx, cy] = puntoLado(60 + r() * 72, false, 1.3), w = 6 + Math.floor(r() * 9), h = 5 + Math.floor(r() * 7);
    if (Math.hypot(cx - CENTRO, cy - CENTRO) > R_MURALLA - 10) continue;
    sala(cx - (w >> 1), cy - (h >> 1), w, h, 1 + Math.floor(r() * 3));
  }
  // laberintos (con premio en el centro)
  const laberintos = [];
  for (let k = 0; k < 2; k++) {
    const [cx, cy] = puntoLado(80 + r() * 40, false, 1.0), C = 9, x0 = cx - C, y0 = cy - C, Wl = C * 2 + 1;
    for (let y = y0; y < y0 + Wl; y++) for (let x = x0; x < x0 + Wl; x++) { const i = idx(x, y); muro[i] = MURO.LADRILLO; mena[i] = 0; piso[i] = SUELO.LOSA; region[i] = REGION.RUINAS; }
    const visit = new Set(), pila = [[1, 1]];
    visit.add('1,1'); muro[idx(x0 + 1, y0 + 1)] = 0;
    while (pila.length) {
      const [cx2, cy2] = pila[pila.length - 1];
      const ops = [[2, 0], [-2, 0], [0, 2], [0, -2]].filter(([dx, dy]) => { const nx = cx2 + dx, ny = cy2 + dy; return nx > 0 && ny > 0 && nx < Wl - 1 && ny < Wl - 1 && !visit.has(nx + ',' + ny); });
      if (!ops.length) { pila.pop(); continue; }
      const [dx, dy] = ops[Math.floor(r() * ops.length)];
      muro[idx(x0 + cx2 + dx / 2, y0 + cy2 + dy / 2)] = 0; muro[idx(x0 + cx2 + dx, y0 + cy2 + dy)] = 0;
      visit.add(cx2 + dx + ',' + (cy2 + dy)); pila.push([cx2 + dx, cy2 + dy]);
    }
    muro[idx(x0 + 1, y0)] = 0; cavar(x0 + 1, y0 - 2, 1.5);
    laberintos.push({ x: x0 + C, y: y0 + C });
  }
  // sala del trono de Ozmar
  {
    const [cx, cy] = puntoLado(104, false, 0.5), w = 25, h = 19, x0 = cx - 12, y0 = cy - 9;
    sala(x0, y0, w, h, 0);
    for (let y = y0 + 2; y < y0 + h; y++) for (let x = cx - 1; x <= cx + 1; x++) piso[idx(x, y)] = SUELO.ALFOMBRA;
    for (let s = 0; s < 8; s++) { for (let x = cx - 1; x <= cx + 1; x++) { const i = idx(x, y0 + h + s); muro[i] = 0; mena[i] = 0; piso[i] = s < 2 ? SUELO.LOSA : piso[i]; } }
    M.guaridas.ozmar = { x: cx, y: cy, x0, y0, w, h };
  }
  // guarida del Topo Rey, en Las Raíces
  {
    const a = r() * Math.PI * 2, ax = Math.round(CENTRO + Math.cos(a) * 32), ay = Math.round(CENTRO + Math.sin(a) * 32);
    for (let y = ay - 10; y <= ay + 10; y++) for (let x = ax - 10; x <= ax + 10; x++) {
      const d = Math.hypot(x - ax, y - ay), i = idx(x, y);
      if (d < 7.5) { muro[i] = 0; mena[i] = 0; piso[i] = SUELO.ARENA; } else if (d < 9.5 && muro[i] === 0) muro[i] = MURO.TIERRA;
    }
    for (let s = 0; s < 3; s++) cavar(ax + Math.cos(a) * (9 + s), ay + Math.sin(a) * (9 + s), 1.2);
    M.guaridas.topo = { x: ax, y: ay };
  }
  // la Gran Muralla y la roca del borde
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const d = Math.hypot(x - CENTRO, y - CENTRO) + (n2(x / 9, y / 9) - 0.5) * 2, i = idx(x, y);
    if (d > R_MURALLA + 5 || x < 2 || y < 2 || x > N - 3 || y > N - 3) { muro[i] = MURO.ROCA; mena[i] = 0; }
    else if (d > R_MURALLA) { muro[i] = MURO.MURALLA; mena[i] = 0; }
  }
  // el santuario del Corazón
  for (let y = CENTRO - 11; y <= CENTRO + 11; y++) for (let x = CENTRO - 11; x <= CENTRO + 11; x++) {
    const d = Math.hypot(x - CENTRO, y - CENTRO), i = idx(x, y), ang = Math.atan2(y - CENTRO, x - CENTRO);
    region[i] = d < 10 ? REGION.SANTUARIO : region[i];
    if (d < 8) { muro[i] = 0; mena[i] = 0; piso[i] = SUELO.RUINA; }
    else if (d < 9.7) {
      const puerta = [0, Math.PI / 2, Math.PI, -Math.PI / 2].some((p) => difAng(ang, p) < 0.2);
      piso[i] = SUELO.RUINA; mena[i] = 0;
      muro[i] = puerta ? 0 : hash2(x, y, 5) < 0.88 ? MURO.SANTUARIO : 0;
    } else if (d < 11 && hash2(x, y, 6) < 0.5) piso[i] = SUELO.RUINA;
  }
  for (const [dx, dy] of [[0, -1], [1, 0], [0, 1], [-1, 0]]) for (let s = 8; s < 16; s++) cavar(CENTRO + dx * s, CENTRO + dy * s, 1.1);
  ponerObjeto(CENTRO, CENTRO, 'corazon');
  for (let y = CENTRO - 1; y <= CENTRO; y++) for (let x = CENTRO - 1; x <= CENTRO + 1; x++) M.bloqueo[idx(x, y)] = 1;
  [['topo', -Math.PI / 2], ['reina', Math.PI / 6], ['ozmar', (5 * Math.PI) / 6]].forEach(([jefe, a]) => {
    const x = Math.round(CENTRO + Math.cos(a) * 4.6), y = Math.round(CENTRO + Math.sin(a) * 4.6) + (a < 0 ? -1 : 1);
    const o = ponerObjeto(x, y, 'estatua', 0); o.datos = { jefe }; M.estatuas[jefe] = { x, y };
  });
  for (const [dx, dy] of [[-3, 3], [3, 3], [-3, -3], [3, -3], [-6, 0], [6, 0]]) ponerObjeto(CENTRO + dx, CENTRO + dy, 'antorcha');

  // ---- cosas por las salas: vasijas, columnas, cofres con botín
  for (const s of salas) {
    for (let k = 0; k < Math.floor(s.w * s.h / 18); k++) {
      const x = s.x0 + 1 + Math.floor(r() * (s.w - 2)), y = s.y0 + 1 + Math.floor(r() * (s.h - 2));
      if (!muro[idx(x, y)] && !M.objetos.has(idx(x, y))) ponerObjeto(x, y, r() < 0.7 ? 'vasija' : 'columna');
    }
    if (r() < 0.45) { const x = s.x0 + Math.floor(s.w / 2), y = s.y0 + 1; if (!muro[idx(x, y)]) { const o = ponerObjeto(x, y, 'cofre_ruinas'); o.datos = { inv: botinCofre(r, 1) }; } }
  }
  for (const l of laberintos) { const o = ponerObjeto(l.x, l.y, 'cofre_ruinas'); o.datos = { inv: botinCofre(r, 3) }; }
  { // trono, columnas y braseros de la sala de Ozmar
    const g = M.guaridas.ozmar;
    ponerObjeto(g.x, g.y0 + 1, 'trono');
    for (let k = 0; k < 4; k++) for (const sx of [-6, 6]) ponerObjeto(g.x + sx, g.y0 + 3 + k * 4, 'columna');
    for (const sx of [-9, 9]) for (const sy of [3, 14]) { const o = ponerObjeto(g.x + sx, g.y0 + sy, 'antorcha'); o.datos = { magica: true }; }
  }
  for (const c of colmenas) for (let k = 0; k < 4; k++) { const a = r() * Math.PI * 2, d = r() * (c.r - 1.5), x = Math.round(c.x + Math.cos(a) * d), y = Math.round(c.y + Math.sin(a) * d); if (!muro[idx(x, y)] && !M.objetos.has(idx(x, y))) ponerObjeto(x, y, 'huevo_larva'); }
  { const g = M.guaridas.reina; for (let k = 0; k < 10; k++) { const a = (k / 10) * Math.PI * 2, x = Math.round(g.x + Math.cos(a) * 8.5), y = Math.round(g.y + Math.sin(a) * 8.5); if (!muro[idx(x, y)] && !M.objetos.has(idx(x, y))) ponerObjeto(x, y, 'huevo_larva'); } }
  { const g = M.guaridas.topo; for (let k = 0; k < 6; k++) { const a = (k / 6) * Math.PI * 2 + 0.3, x = Math.round(g.x + Math.cos(a) * 6.5), y = Math.round(g.y + Math.sin(a) * 6.5); if (!muro[idx(x, y)]) ponerObjeto(x, y, 'hongo'); } }

  // ---- plantas y cristales
  const junto = (x, y) => muro[idx(x + 1, y)] || muro[idx(x - 1, y)] || muro[idx(x, y + 1)] || muro[idx(x, y - 1)];
  for (let y = 3; y < N - 3; y++) for (let x = 3; x < N - 3; x++) {
    const i = idx(x, y);
    if (muro[i] || M.objetos.has(i)) continue;
    const p = piso[i];
    if (p === SUELO.RUINA || p === SUELO.ARENA || p === SUELO.AGUA || p === SUELO.LOSA || p === SUELO.ALFOMBRA || p === SUELO.COLMENA || p === SUELO.GRAVA) continue;
    if (Math.hypot(x - CENTRO, y - CENTRO) < 12) continue;
    const reg = region[i], v = r(), pegado = junto(x, y);
    if (reg === REGION.RAICES) {
      if (pegado && v < 0.045) ponerObjeto(x, y, 'raiz');
      else if (v > 0.985) ponerObjeto(x, y, 'arbusto');
      else if (p === SUELO.CESPED && v > 0.96) ponerObjeto(x, y, v > 0.975 ? 'bulbo_silvestre' : 'planta_fibra');
      else if (v > 0.978) ponerObjeto(x, y, 'hongo');
    } else if (reg === REGION.ARCILLA) {
      if (pegado && v < 0.025) ponerObjeto(x, y, 'raiz');
      else if (v > 0.965) ponerObjeto(x, y, 'planta_fibra');
      else if (v > 0.955) ponerObjeto(x, y, 'hongo');
    } else if (reg === REGION.RUINAS) {
      if (pegado && v < 0.02) ponerObjeto(x, y, 'cristal');
      else if (v > 0.986) ponerObjeto(x, y, 'hongo');
      else if (v > 0.978) ponerObjeto(x, y, 'vasija');
    }
  }
  let puestas = 0;
  for (let k = 0; k < 4000 && puestas < 10; k++) {
    const a = r() * Math.PI * 2, d = 11 + r() * 10, x = Math.round(CENTRO + Math.cos(a) * d), y = Math.round(CENTRO + Math.sin(a) * d), i = idx(x, y);
    if (!muro[i] && !M.objetos.has(i) && junto(x, y) && piso[i] !== SUELO.RUINA && piso[i] !== SUELO.AGUA) { ponerObjeto(x, y, 'raiz'); puestas++; }
  }
  for (let k = 0; k < 4000 && puestas < 14; k++) {
    const a = r() * Math.PI * 2, d = 12 + r() * 14, x = Math.round(CENTRO + Math.cos(a) * d), y = Math.round(CENTRO + Math.sin(a) * d), i = idx(x, y);
    if (!muro[i] && !M.objetos.has(i) && piso[i] !== SUELO.RUINA && piso[i] !== SUELO.AGUA) { ponerObjeto(x, y, puestas % 2 ? 'planta_fibra' : 'arbusto'); puestas++; }
  }
  M.mapa = null;
  return M;
}

// Botín de los cofres de las ruinas. calidad 1: sala normal; 3: centro de un laberinto.
function botinCofre(r, calidad) {
  const inv = new Array(30).fill(null), pon = (id, n) => { const k = inv.findIndex((s) => !s); if (k >= 0) inv[k] = { id, n }; };
  const comun = [['lingote_cobre', 2, 5], ['lingote_estano', 1, 4], ['flecha', 8, 20], ['antorcha', 4, 10], ['baya', 2, 5], ['semilla_trigo', 2, 4], ['fibra', 3, 8], ['hueso', 2, 5]];
  const raro = [['lingote_hierro', 2, 5], ['cristal', 1, 3], ['gema_antigua', 1, 1], ['lingote_oro', 1, 3], ['anillo_cobre', 1, 1], ['flecha_cobre', 6, 14]];
  const epico = [['anillo_oro', 1, 1], ['collar_cristal', 1, 1], ['maza_hierro', 1, 1], ['espada_hierro', 1, 1], ['alfombra', 4, 8]];
  const sacar = (lista) => { const [id, a, b] = lista[Math.floor(r() * lista.length)]; pon(id, a + Math.floor(r() * (b - a + 1))); };
  for (let k = 0; k < 2 + calidad; k++) sacar(comun);
  for (let k = 0; k < calidad; k++) if (r() < 0.6) sacar(raro);
  if (calidad >= 3) sacar(epico);
  return inv;
}

// ---------------------------------------------------------------- casillas

function muroEn(x, y) { return enMundo(x, y) ? M.muro[idx(x, y)] : MURO.ROCA; }
function objetoEn(x, y) { return enMundo(x, y) ? M.objetos.get(idx(x, y)) : undefined; }
// ¿Se puede pasar? Las puertas dejan pasar al jugador pero no a las criaturas.
function solido(x, y, jugador = false) {
  if (!enMundo(x, y)) return true;
  const i = idx(x, y);
  if (M.muro[i] || M.bloqueo[i] || M.piso[i] === SUELO.AGUA) return true;
  const o = M.objetos.get(i);
  if (!o) return false;
  const info = OBJ[o.tipo];
  return !!(info.bloquea || (info.bloqueaCriaturas && !jugador));
}
function cambioLuz() { M.verLuz++; }
function ponerObjeto(x, y, tipo, extra = 0) {
  const i = idx(x, y), o = { tipo, x, y, extra, vida: OBJ[tipo].vida };
  if (OBJ[tipo].interactua === 'cofre') o.datos = { inv: new Array(30).fill(null) };
  else if (OBJ[tipo].interactua === 'olla') o.datos = { a: null, b: null };
  M.objetos.set(i, o);
  if (OBJ[tipo].luz || tipo === 'corazon' || tipo === 'estatua') M.luces.add(i);
  marcarMapa(i); GL.tocar(x, y); cambioLuz();
  return o;
}
function quitarObjeto(x, y) { const i = idx(x, y); M.objetos.delete(i); M.luces.delete(i); marcarMapa(i); GL.tocar(x, y); cambioLuz(); }
function romperMuro(x, y) {
  const i = idx(x, y);
  M.muro[i] = 0; M.mena[i] = 0; M.danos.delete(i);
  marcarMapa(i); GL.tocar(x, y); cambioLuz();
}
function ponerMuro(x, y, tipo) { const i = idx(x, y); M.muro[i] = tipo; M.mena[i] = 0; marcarMapa(i); GL.tocar(x, y); cambioLuz(); }
function ponerSuelo(x, y, tipo) { const i = idx(x, y); M.piso[i] = tipo; marcarMapa(i); GL.tocar(x, y); }

// ---------------------------------------------------------------- luz
// Fuentes fijas (antorchas, cristales…): se calculan sobre una zona grande y se guardan hasta que el mundo
// cambia o la vista sale de esa zona. Las que se mueven (el jugador, su antorcha) se suman cada cuadro.

const LUZ = { X0: 0, Y0: 0, W: 0, H: 0, r: null, g: null, b: null, marca: null, sello: 0, cola: null, datos: null };
const EST = { x0: 0, y0: 0, w: 0, h: 0, ver: -1, r: null, g: null, b: null, fr: null, fg: null, fb: null, marca: null, sello: 0, cola: null };

function bfsLuz(f, X0, Y0, W, H, R, G, B, marca, cola, selloObj) {
  const sx = Math.floor(f.x / T), sy = Math.floor(f.y / T);
  if (sx < X0 || sy < Y0 || sx >= X0 + W || sy >= Y0 + H) return;
  const sello = ++selloObj.sello;
  let ini = 0, fin = 0;
  const li0 = (sy - Y0) * W + (sx - X0);
  cola[fin++] = li0; marca[li0] = sello;
  const fr = f.r, k = f.k ?? 1, cr = f.c[0] * k, cg = f.c[1] * k, cb = f.c[2] * k;
  while (ini < fin) {
    const li = cola[ini++], lx = li % W, ly = (li - lx) / W, cx = lx + X0, cy = ly + Y0;
    const dist = Math.hypot((cx + 0.5) * T - f.x, (cy + 0.5) * T - f.y) / T;
    if (dist > fr) continue;
    let a = 1 - dist / fr; a = a * a * (3 - 2 * a);
    R[li] += cr * a; G[li] += cg * a; B[li] += cb * a;
    if (!(cx === sx && cy === sy) && (!enMundo(cx, cy) || M.muro[idx(cx, cy)])) continue;
    if (lx > 0 && marca[li - 1] !== sello) { marca[li - 1] = sello; cola[fin++] = li - 1; }
    if (lx < W - 1 && marca[li + 1] !== sello) { marca[li + 1] = sello; cola[fin++] = li + 1; }
    if (ly > 0 && marca[li - W] !== sello) { marca[li - W] = sello; cola[fin++] = li - W; }
    if (ly < H - 1 && marca[li + W] !== sello) { marca[li + W] = sello; cola[fin++] = li + W; }
  }
}
function luzEstatica(v, fuentesFijas) {
  const M8 = 10;
  if (EST.r && EST.ver === M.verLuz && v.x0 - M8 >= EST.x0 && v.z0 - M8 >= EST.y0 && v.x1 + M8 < EST.x0 + EST.w && v.z1 + M8 < EST.y0 + EST.h) return;
  EST.x0 = v.x0 - 28; EST.y0 = v.z0 - 28; EST.w = v.x1 - v.x0 + 57; EST.h = v.z1 - v.z0 + 57;
  const n = EST.w * EST.h;
  if (!EST.r || EST.r.length < n) for (const k of ['r', 'g', 'b', 'fr', 'fg', 'fb']) EST[k] = new Float32Array(n);
  if (!EST.marca || EST.marca.length < n) { EST.marca = new Int32Array(n); EST.cola = new Int32Array(n); }
  for (const k of ['r', 'g', 'b', 'fr', 'fg', 'fb']) EST[k].fill(0, 0, n);
  for (const f of fuentesFijas(EST.x0, EST.y0, EST.x0 + EST.w, EST.y0 + EST.h)) {
    if (f.fuego) bfsLuz(f, EST.x0, EST.y0, EST.w, EST.h, EST.fr, EST.fg, EST.fb, EST.marca, EST.cola, EST);
    else bfsLuz(f, EST.x0, EST.y0, EST.w, EST.h, EST.r, EST.g, EST.b, EST.marca, EST.cola, EST);
  }
  EST.ver = M.verLuz;
}
// v: casillas de la vista. Devuelve los datos de la textura de luz (1 casilla de borde).
function calcularLuz(v, fuentesFijas, dinamicas, ambiente, parpadeo) {
  luzEstatica(v, fuentesFijas);
  const MG = 10, X0 = v.x0 - MG, Y0 = v.z0 - MG, W = v.x1 - v.x0 + 1 + MG * 2, H = v.z1 - v.z0 + 1 + MG * 2, n = W * H;
  if (!LUZ.r || LUZ.r.length < n) { LUZ.r = new Float32Array(n); LUZ.g = new Float32Array(n); LUZ.b = new Float32Array(n); LUZ.marca = new Int32Array(n); LUZ.cola = new Int32Array(n); }
  LUZ.X0 = X0; LUZ.Y0 = Y0; LUZ.W = W; LUZ.H = H;
  const R = LUZ.r, G = LUZ.g, B = LUZ.b;
  for (let y = 0; y < H; y++) {
    const ey = Y0 + y - EST.y0, base = y * W;
    for (let x = 0; x < W; x++) {
      const ex = X0 + x - EST.x0, li = base + x;
      if (ex < 0 || ey < 0 || ex >= EST.w || ey >= EST.h) { R[li] = G[li] = B[li] = 0; continue; }
      const ei = ey * EST.w + ex;
      R[li] = EST.r[ei] + EST.fr[ei] * parpadeo; G[li] = EST.g[ei] + EST.fg[ei] * parpadeo; B[li] = EST.b[ei] + EST.fb[ei] * parpadeo;
    }
  }
  for (const f of dinamicas) bfsLuz(f, X0, Y0, W, H, R, G, B, LUZ.marca, LUZ.cola, LUZ);
  const vw = v.x1 - v.x0 + 3, vh = v.z1 - v.z0 + 3;
  if (!LUZ.datos || LUZ.datos.length < vw * vh * 4) LUZ.datos = new Uint8Array(vw * vh * 4);
  const d = LUZ.datos, [ar, ag, ab] = ambiente, k = 255 / Math.sqrt(2.4);
  for (let y = 0; y < vh; y++) for (let x = 0; x < vw; x++) {
    const wx = v.x0 - 1 + x, wy = v.z0 - 1 + y, li = (wy - Y0) * W + (wx - X0), o = (y * vw + x) * 4;
    let r = R[li], g = G[li], b = B[li];
    if (enMundo(wx, wy)) {
      const i = idx(wx, wy);
      if (!M.visto[i] && (r > 0.16 || g > 0.16 || b > 0.16)) { M.visto[i] = 1; marcarMapa(i); }
      if (M.visto[i]) { r += ar; g += ag; b += ab; }
    }
    d[o] = Math.min(255, Math.sqrt(r) * k); d[o + 1] = Math.min(255, Math.sqrt(g) * k); d[o + 2] = Math.min(255, Math.sqrt(b) * k); d[o + 3] = 255;
  }
  return { datos: d.subarray(0, vw * vh * 4), x0: v.x0 - 1, y0: v.z0 - 1, w: vw, h: vh };
}
function luzEn(tx, ty) {
  if (tx < LUZ.X0 || ty < LUZ.Y0 || tx >= LUZ.X0 + LUZ.W || ty >= LUZ.Y0 + LUZ.H) return 0;
  const li = (ty - LUZ.Y0) * LUZ.W + (tx - LUZ.X0);
  return Math.max(LUZ.r[li], LUZ.g[li], LUZ.b[li]);
}

// ---------------------------------------------------------------- mapa de lo explorado

function colorMapa(i) {
  const o = M.objetos.get(i);
  if (o && OBJ[o.tipo].mapa) return OBJ[o.tipo].mapa;
  if (M.mena[i]) return INFO_MENA[M.mena[i]].mapa;
  if (M.muro[i]) return INFO_MURO[M.muro[i]].mapa;
  return INFO_SUELO[M.piso[i]].mapa || '#201820';
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
  for (let i = 0; i < u8.length;) { const v = u8[i]; let n = 1; while (i + n < u8.length && u8[i + n] === v && n < 255) n++; out.push(v, n); i += n; }
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
    v: 3, sem: M.sem, muro: rle(M.muro), mena: rle(M.mena), piso: rle(M.piso), visto: rle(M.visto),
    objetos: [...M.objetos.values()].map((o) => [o.x, o.y, o.tipo, o.extra, o.datos || null]),
  };
}
function cargarMundo(g) {
  generarMundo(g.sem);
  const NN = N_MUNDO * N_MUNDO;
  M.muro = desRle(g.muro, NN); M.mena = desRle(g.mena, NN); M.visto = desRle(g.visto, NN);
  if (g.piso) M.piso = desRle(g.piso, NN);
  M.objetos.clear(); M.luces.clear();
  for (const [x, y, tipo, extra, datos] of g.objetos) { if (!OBJ[tipo]) continue; const o = ponerObjeto(x, y, tipo, extra); if (datos) o.datos = datos; }
  M.mapa = null;
  GL.olvidarTodo(); cambioLuz();
}
