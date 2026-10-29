'use strict';
// El anillo exterior, al otro lado de la Gran Muralla. Está partido en tres sectores como en Core Keeper: el de la
// Selva (fase 2) y dos de roca dormida (el Mar Hundido y el Desierto del Origen, fases 3 y 4). Desde la 0.7 el
// sector de la Selva llega hasta el radio 620 en tres bandas, cada una más honda y más peligrosa:
//   - la Selva Esmeralda (hasta R_SELVA1): luz que baja del cielo, árboles, lagos, praderas, la Mazmorra del Moho,
//     el templo de Tamar, el jardín de Hiedra, aldeas de grutos, campamentos y ruinas con tablillas;
//   - la Ciénaga de la Podre (hasta R_SELVA2, cienaga.js);
//   - la Ciudad Velada (hasta R_EXTERIOR, ciudad.js).
// Aquí van el terreno de las tres bandas, la Selva Esmeralda y lo que crece en todas.

const R_SELVA0 = R_MURALLA + 5;           // donde empieza el anillo exterior
const ANCHO_SELVA = Math.PI / 3;          // medio ancho del sector de la Selva (±60°)
const BANDA = (reg) => (reg === REGION.CIENAGA ? 1 : reg === REGION.CIUDAD || reg === REGION.CRIPTA ? 2 : 0);

function* generarExterior(sem) {
  const N = N_MUNDO, { muro, mena, piso, region } = M;
  const n1 = crearRuido(sem + 301), n2 = crearRuido(sem + 302), n3 = crearRuido(sem + 303), n4 = crearRuido(sem + 304), n5 = crearRuido(sem + 305), n6 = crearRuido(sem + 306), n7 = crearRuido(sem + 307);
  const r = azar(sem + 777);
  const angSelva = M.lado + Math.PI / 2 + (r() - 0.5) * 0.6;
  Object.assign(M, { angSelva, soles: [], fijas: [], aldeas: [], puzles: [], pasos: [] });
  const enSelva = (x, y) => difAng(Math.atan2(y - CENTRO, x - CENTRO) + (fbm(n6, x / 30, y / 30) - 0.5) * 0.5, angSelva) < ANCHO_SELVA;

  // ---- terreno base de las tres bandas (solo se recorre el cuadrado del anillo; lo de fuera ya es roca)
  const fila0 = new Int32Array(N).fill(N), fila1 = new Int32Array(N).fill(-1);
  const E0 = Math.max(2, CENTRO - R_EXTERIOR - 12), E1 = Math.min(N - 3, CENTRO + R_EXTERIOR + 12);
  const r2min = (R_SELVA0 - 2) * (R_SELVA0 - 2), r2max = (R_EXTERIOR + 12) * (R_EXTERIOR + 12), MITAD_SELVA = (R_SELVA0 + R_SELVA1) / 2;
  for (let y = E0; y <= E1; y++) for (let x = E0; x <= E1; x++) {
    const dx = x - CENTRO, dy = y - CENTRO, d2 = dx * dx + dy * dy;
    if (d2 < r2min || d2 > r2max) continue;
    const d0 = Math.sqrt(d2), d = d0 + (n2(x / 9, y / 9) - 0.5) * 2;
    if (d <= R_SELVA0) continue;
    const i = idx(x, y);
    mena[i] = 0;
    if (d > R_EXTERIOR - 6 + fbm(n7, x / 40, y / 40) * 12) { muro[i] = MURO.ROCA; region[i] = REGION.SELLADO; piso[i] = SUELO.PIEDRA; continue; }
    const da = difAng(Math.atan2(dy, dx), angSelva);
    if (da > ANCHO_SELVA + 0.3 || (da > ANCHO_SELVA - 0.3 && !enSelva(x, y))) { muro[i] = MURO.SELLO; region[i] = REGION.SELLADO; piso[i] = SUELO.PIEDRA; continue; }
    if (x < fila0[y]) fila0[y] = x;
    if (x > fila1[y]) fila1[y] = x;
    const db = d0 + (fbm(n7, x / 70, y / 70) - 0.5) * 44, v = n4(x / 3.6, y / 3.6), o = n2(x / 3, y / 3);
    if (db < R_SELVA1) { // la Selva Esmeralda
      region[i] = REGION.SELVA;
      const p = fbm(n3, x / 16, y / 16);
      piso[i] = p > 0.62 ? SUELO.MUSGO : p < 0.34 ? SUELO.TIERRA_SELVA : SUELO.HIERBA;
      const cueva = fbm(n1, x / 15, y / 15, 3) + (n4(x / 5, y / 5) - 0.5) * 0.16;
      const borde = d < R_SELVA0 + 6 ? 1 : 0; // se abre junto a la Muralla
      if (cueva + borde * 0.2 > 0.5) { muro[i] = 0; continue; }
      const t = fbm(n5, x / 20, y / 20);
      muro[i] = d > MITAD_SELVA && t > 0.56 ? MURO.MUSGOSA : t > 0.64 ? MURO.ESPINO : MURO.HIERBA;
      if (muro[i] === MURO.HIERBA && v > 0.74) mena[i] = MENA.CARMESI;
      else if (muro[i] === MURO.MUSGOSA) mena[i] = v > 0.78 ? MENA.CARMESI : o > 0.85 ? MENA.ORO : v < 0.14 ? MENA.HIERRO : 0;
      else if (muro[i] === MURO.HIERBA && o > 0.88) mena[i] = MENA.ORO;
    } else if (db < R_SELVA2) { // la Ciénaga de la Podre: abierta, turba y raíces muertas
      region[i] = REGION.CIENAGA;
      piso[i] = fbm(n3, x / 18, y / 18) > 0.56 ? SUELO.LODO : SUELO.TURBERA;
      const cueva = fbm(n1, x / 17, y / 17, 3) + (n4(x / 5, y / 5) - 0.5) * 0.16;
      if (cueva > 0.47) { muro[i] = 0; continue; }
      muro[i] = fbm(n5, x / 22, y / 22) > 0.6 ? MURO.RAIZ_MUERTA : MURO.TURBA;
      if (muro[i] === MURO.TURBA) mena[i] = v > 0.8 ? MENA.MEDULA : o > 0.92 ? MENA.ORO : v < 0.08 ? MENA.CARMESI : 0;
    } else { // los alrededores de la Ciudad Velada: piedra gris, ceniza y vetas de plata
      region[i] = REGION.CIUDAD;
      piso[i] = fbm(n3, x / 20, y / 20) > 0.6 ? SUELO.PIEDRA : SUELO.CENIZA;
      const cueva = fbm(n1, x / 14, y / 14, 3) + (n4(x / 5, y / 5) - 0.5) * 0.16;
      if (cueva > 0.5) { muro[i] = 0; continue; }
      muro[i] = MURO.PIEDRA_GRIS;
      mena[i] = v > 0.83 ? MENA.PLATA : o > 0.93 ? MENA.ORO : v < 0.06 ? MENA.MEDULA : 0;
    }
  }
  let Y0 = N, Y1 = -1;
  for (let y = 0; y < N; y++) if (fila1[y] >= 0) { if (y < Y0) Y0 = y; Y1 = y; }
  const cadaCasilla = (fn) => { for (let y = Y0; y <= Y1; y++) for (let x = fila0[y]; x <= fila1[y]; x++) fn(x, y, idx(x, y)); };
  yield [0.3, 'La Selva Esmeralda'];

  // ---- lagos: claros en la Selva, agua negra y abierta en la Ciénaga
  cadaCasilla((x, y, i) => {
    const reg = region[i];
    if (reg === REGION.SELVA) {
      if (muro[i]) return;
      if (fbm(n6, x / 22, y / 22, 2) < 0.69) return;
      for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) if (muro[idx(x + dx, y + dy)]) return;
      piso[i] = SUELO.AGUA;
    } else if (reg === REGION.CIENAGA) {
      const l = fbm(n6, x / 26, y / 26, 2) + (n4(x / 6, y / 6) - 0.5) * 0.08;
      if (l < 0.6) return;
      muro[i] = 0; mena[i] = 0; piso[i] = l > 0.635 ? SUELO.AGUA_NEGRA : SUELO.LODO;
    }
  });
  yield [0.36, 'Los lagos'];

  // ---- herramientas para las estructuras (las usan también cienaga.js y ciudad.js)
  const cavar = (cx, cy, rad, conPiso = 0, reg = 0) => {
    for (let y = Math.floor(cy - rad); y <= cy + rad; y++) for (let x = Math.floor(cx - rad); x <= cx + rad; x++) {
      if (!enMundo(x, y) || Math.hypot(x - cx, y - cy) > rad || region[idx(x, y)] === REGION.SELLADO) continue;
      const i = idx(x, y); muro[i] = 0; mena[i] = 0; if (conPiso) piso[i] = conPiso; if (reg) region[i] = reg;
    }
  };
  const punto = (dMin, dMax, desvio = 0.85) => { const a = angSelva + (r() - 0.5) * 2 * desvio, d = dMin + r() * (dMax - dMin); return [Math.round(CENTRO + Math.cos(a) * d), Math.round(CENTRO + Math.sin(a) * d)]; };
  const libre = (x, y) => enMundo(x, y) && !muro[idx(x, y)] && !M.objetos.has(idx(x, y)) && !esAgua(piso[idx(x, y)]) && region[idx(x, y)] !== REGION.SELLADO;
  // sitios que no se pisan: primero lo grande, luego lo demás
  const ocupados = [];
  const sitio = (dMin, dMax, desvio, radio) => {
    let p = null;
    for (let k = 0; k < 160 && !p; k++) { const q = punto(dMin, dMax, desvio); if (ocupados.every((o) => Math.hypot(q[0] - o.x, q[1] - o.y) > radio + o.r + 3)) p = q; }
    p = p || punto(dMin, dMax, desvio);
    ocupados.push({ x: p[0], y: p[1], r: radio });
    return p;
  };
  const pon = (x, y, tipo) => { if (!libre(x, y)) return null; return ponerObjeto(x, y, tipo); };
  // cada zona numera sus tablillas desde 0, para que salgan todos sus textos
  const tab = {};
  const tablilla = (x, y, tipo = 'tablilla') => {
    const t = pon(x, y, tipo); if (!t) return null;
    const z = ZONA_TABLILLA(M.region[idx(x, y)]); tab[z] = tab[z] || 0; t.datos = { n: tab[z]++ };
    return t;
  };
  // un camino serpenteante de a hacia b (para que todo se pueda alcanzar andando)
  const sendero = (ax, ay, bx, by, ancho, pisoDe) => {
    let x = ax, y = ay, a = Math.atan2(by - ay, bx - ax);
    for (let s = 0; s < 3000 && Math.hypot(bx - x, by - y) > 2; s++) {
      const objetivo = Math.atan2(by - y, bx - x);
      a += (r() - 0.5) * 0.5; a += Math.atan2(Math.sin(objetivo - a), Math.cos(objetivo - a)) * 0.18;
      x += Math.cos(a); y += Math.sin(a);
      for (let yy = Math.floor(y - ancho); yy <= y + ancho; yy++) for (let xx = Math.floor(x - ancho); xx <= x + ancho; xx++) {
        if (!enMundo(xx, yy) || Math.hypot(xx - x, yy - y) > ancho) continue;
        const i = idx(xx, yy), reg = region[i];
        if (reg === REGION.SELLADO || muro[i] === MURO.MURALLA || muro[i] === MURO.MOHO || muro[i] === MURO.CRIPTA || muro[i] === MURO.VELADA_VIEJA || muro[i] === MURO.HUESO_ASOR) continue;
        if (muro[i]) { muro[i] = 0; mena[i] = 0; const p = pisoDe(reg); if (p) piso[i] = p; }
        else if (esAgua(piso[i]) && reg !== REGION.CIENAGA) piso[i] = pisoDe(reg) || piso[i];
      }
    }
  };
  const pisoSendero = (reg) => (reg === REGION.CIENAGA ? SUELO.LODO : reg === REGION.CIUDAD ? SUELO.CENIZA : SUELO.TIERRA_SELVA);
  const X = { n1, n2, n3, n4, n5, n6, n7, r, angSelva, cavar, punto, libre, sitio, pon, tablilla, sendero, pisoSendero, cadaCasilla, ocupados };

  // ---- la Selva Esmeralda
  const posTemplo = sitio(275, 312, 0.5, 19), posMazmorra = sitio(232, 290, 0.7, 25), posHiedra = sitio(196, 258, 0.7, 17);

  // camino: del anillo interior a la Muralla, mirando a la Selva (para que la encuentres)
  {
    let a = angSelva, x = CENTRO + Math.cos(a) * 96, y = CENTRO + Math.sin(a) * 96;
    for (let s = 0; s < 60 && Math.hypot(x - CENTRO, y - CENTRO) < R_MURALLA - 0.5; s++) {
      a += (r() - 0.5) * 0.3; a += (angSelva - a) * 0.2;
      x += Math.cos(a); y += Math.sin(a);
      for (let yy = Math.floor(y - 1.4); yy <= y + 1.4; yy++) for (let xx = Math.floor(x - 1.4); xx <= x + 1.4; xx++) {
        if (Math.hypot(xx - x, yy - y) > 1.4) continue;
        const i = idx(xx, yy);
        if (muro[i] === MURO.MURALLA || muro[i] === MURO.ROCA) continue;
        muro[i] = 0; mena[i] = 0; piso[i] = SUELO.GRAVA;
      }
    }
    // y una franja abierta al otro lado
    for (let k = -6; k <= 6; k++) { const aa = angSelva + k * 0.04; cavar(CENTRO + Math.cos(aa) * (R_SELVA0 + 4), CENTRO + Math.sin(aa) * (R_SELVA0 + 4), 2.6, SUELO.HIERBA); }
  }

  // praderas de flores
  for (let k = 0; k < 6; k++) {
    const rad = 13 + r() * 8, [cx, cy] = sitio(172, 318, 0.85, rad + 2);
    for (let y = Math.floor(cy - rad - 3); y <= cy + rad + 3; y++) for (let x = Math.floor(cx - rad - 3); x <= cx + rad + 3; x++) {
      if (!enMundo(x, y)) continue;
      const i = idx(x, y), d = Math.hypot(x - cx, y - cy) + (n3(x / 3, y / 3) - 0.5) * 4;
      if (d > rad || region[i] !== REGION.SELVA) continue;
      region[i] = REGION.PRADERA;
      if (d < rad - 2 || hash2(x, y, 41) < 0.6) { muro[i] = 0; mena[i] = 0; }
      if (piso[i] !== SUELO.AGUA) piso[i] = fbm(n5, x / 6, y / 6) > 0.45 ? SUELO.FLORES : SUELO.HIERBA;
    }
    M.soles.push({ x: cx, y: cy, r: rad * 0.9, k: 0.55 });
  }

  // la guarida de Hiedra: un jardín envenenado rodeado de espino
  {
    const [cx, cy] = posHiedra;
    for (let y = cy - 16; y <= cy + 16; y++) for (let x = cx - 16; x <= cx + 16; x++) {
      const d = Math.hypot(x - cx, y - cy) + (n3(x / 2, y / 2) - 0.5) * 2, i = idx(x, y);
      if (d < 12) { muro[i] = 0; mena[i] = 0; piso[i] = d < 9 || hash2(x, y, 3) < 0.5 ? SUELO.VENENO : SUELO.MUSGO; region[i] = REGION.SELVA; }
      else if (d < 15) { muro[i] = MURO.ESPINO; mena[i] = 0; region[i] = REGION.SELVA; }
    }
    for (let b = 0; b < 2; b++) { const a = r() * Math.PI * 2; for (let s = 11; s < 19; s++) cavar(cx + Math.cos(a) * s, cy + Math.sin(a) * s, 1.4, SUELO.MUSGO); }
    M.guaridas.hiedra = { x: cx, y: cy };
    M.soles.push({ x: cx, y: cy, r: 13, k: 0.5, c: [0.75, 0.55, 1.0] });
    for (let k = 0; k < 4; k++) { const a = (k / 4) * Math.PI * 2 + 0.4; M.fijas.push({ tipo: 'planta_trampa', x: Math.round(cx + Math.cos(a) * 16.5), y: Math.round(cy + Math.sin(a) * 16.5) }); }
  }

  // el templo del cielo: plataforma de losas, columnas y el altar donde se llama a Tamar
  {
    const [cx, cy] = posTemplo;
    for (let y = cy - 17; y <= cy + 17; y++) for (let x = cx - 17; x <= cx + 17; x++) {
      const d = Math.hypot(x - cx, y - cy), i = idx(x, y);
      if (d < 14) { muro[i] = 0; mena[i] = 0; piso[i] = d < 12 && (Math.abs(x - cx) + Math.abs(y - cy)) % 5 !== 0 ? SUELO.TEMPLO : SUELO.MUSGO; region[i] = REGION.SELVA; }
      else if (d < 16.5 && muro[i] === 0 && hash2(x, y, 9) < 0.7) { muro[i] = MURO.MUSGOSA; region[i] = REGION.SELVA; }
    }
    for (let b = 0; b < 3; b++) { const a = (b / 3) * Math.PI * 2 + r(); for (let s = 13; s < 22; s++) cavar(cx + Math.cos(a) * s, cy + Math.sin(a) * s, 1.5, SUELO.TEMPLO); }
    M.guaridas.tamar = { x: cx, y: cy };
    M.templo = { x: cx, y: cy };
    M.soles.push({ x: cx, y: cy, r: 11, k: 0.6 });
    M.rayos.push({ x: cx, y: cy, a: 1.4, c: [1, 0.96, 0.8], ancho: 3 });
  }

  // la Mazmorra del Moho: 3×3 salas de muros que no se rompen; la puerta da a la Selva
  const mazmorra = (() => {
    const [cx, cy] = posMazmorra, S = 9, P = 4, W = 3 * S + 4 * P - 2; // sala, pared+pasillo
    const x0 = cx - (W >> 1), y0 = cy - (W >> 1);
    for (let y = y0 - 2; y < y0 + W + 2; y++) for (let x = x0 - 2; x < x0 + W + 2; x++) { const i = idx(x, y); muro[i] = MURO.MOHO; mena[i] = 0; piso[i] = SUELO.MOHO; region[i] = REGION.MOHO; }
    const salas = [];
    for (let j = 0; j < 3; j++) for (let k = 0; k < 3; k++) {
      const sx = x0 + P + k * (S + P), sy = y0 + P + j * (S + P);
      for (let y = sy; y < sy + S; y++) for (let x = sx; x < sx + S; x++) muro[idx(x, y)] = 0;
      salas.push({ x: sx + (S >> 1), y: sy + (S >> 1), j, k });
    }
    const pasillo = (ax, ay, bx, by) => { for (let x = Math.min(ax, bx); x <= Math.max(ax, bx); x++) for (let w = -1; w <= 1; w++) muro[idx(x, ay + w)] = 0; for (let y = Math.min(ay, by); y <= Math.max(ay, by); y++) for (let w = -1; w <= 1; w++) muro[idx(bx + w, y)] = 0; };
    // laberinto de salas: un árbol que las une todas y un par de atajos
    const unidas = new Set([4]), pila = [4];
    while (pila.length) {
      const a = pila[pila.length - 1], { j, k } = salas[a];
      const vec = [[j - 1, k], [j + 1, k], [j, k - 1], [j, k + 1]].filter(([jj, kk]) => jj >= 0 && kk >= 0 && jj < 3 && kk < 3 && !unidas.has(jj * 3 + kk));
      if (!vec.length) { pila.pop(); continue; }
      const [jj, kk] = vec[Math.floor(r() * vec.length)], b = jj * 3 + kk;
      pasillo(salas[a].x, salas[a].y, salas[b].x, salas[b].y); unidas.add(b); pila.push(b);
    }
    if (r() < 0.7) pasillo(salas[0].x, salas[0].y, salas[1].x, salas[1].y);
    // la puerta: en la sala del lado que mira al Corazón; un pasillo sale hasta la Selva
    const haciaC = Math.atan2(CENTRO - cy, CENTRO - cx), c = Math.cos(haciaC), sn = Math.sin(haciaC);
    const [dx, dy] = Math.abs(c) > Math.abs(sn) ? [Math.sign(c), 0] : [0, Math.sign(sn)];
    const entrada = salas[(dy < 0 ? 0 : dy > 0 ? 2 : 1) * 3 + (dx < 0 ? 0 : dx > 0 ? 2 : 1)];
    for (let s = 0; s < (S >> 1) + P + 10; s++) {
      const x = entrada.x + dx * s, y = entrada.y + dy * s, fuera = s > (S >> 1) + P + 1;
      for (let w = -1; w <= 1; w++) { const i = idx(x + (dy ? w : 0), y + (dx ? w : 0)); muro[i] = 0; mena[i] = 0; if (fuera) { piso[i] = SUELO.MUSGO; region[i] = REGION.SELVA; } }
    }
    return { x0, y0, W, salas, cx, cy };
  })();
  M.mazmorra = { x: mazmorra.cx, y: mazmorra.cy };

  // aldeas de grutos: casitas de madera de selva con cofre, mesa y un huerto
  const aldeas = [], casitas = [];
  for (let k = 0; k < 4; k++) {
    const [cx, cy] = sitio(180, 318, 0.85, 13);
    cavar(cx, cy, 11, SUELO.TIERRA_SELVA);
    for (const [hx, hy] of [[-6, -4], [3, -5], [-2, 4]]) {
      const x0 = cx + hx, y0 = cy + hy, w = 6, h = 5;
      for (let y = y0 - 1; y <= y0 + h; y++) for (let x = x0 - 1; x <= x0 + w; x++) {
        const i = idx(x, y), borde = x === x0 - 1 || y === y0 - 1 || x === x0 + w || y === y0 + h;
        muro[i] = borde ? MURO.SELVA_MADERA : 0; piso[i] = SUELO.TABLAS; mena[i] = 0;
      }
      muro[idx(x0 + (w >> 1), y0 + h)] = 0; // puerta abajo
      casitas.push({ x0, y0, w, h });
    }
    for (let y = cy + 9; y <= cy + 10; y++) for (let x = cx - 4; x <= cx + 4; x++) { const i = idx(x, y); muro[i] = 0; piso[i] = SUELO.ARADA; }
    M.fijas.push({ tipo: 'gruto_jardinero', x: cx - 3, y: cy + 8 }, { tipo: 'gruto_jardinero', x: cx + 4, y: cy }, { tipo: 'gruto_cazador', x: cx, y: cy - 9 });
    aldeas.push({ x: cx, y: cy });
  }
  M.aldeas = aldeas.slice();

  // campamentos abandonados, ruinas del cielo (con tablillas) y los santuarios donde los grutos rezan a la Podre
  const campamentos = [], ruinas = [], santuarios = [];
  for (let k = 0; k < 12; k++) { const [x, y] = sitio(160, 322, 0.9, 5); cavar(x, y, 3.6, SUELO.TIERRA_SELVA); campamentos.push({ x, y }); }
  for (let k = 0; k < 12; k++) {
    const [x, y] = sitio(165, 322, 0.9, 6);
    for (let yy = y - 5; yy <= y + 5; yy++) for (let xx = x - 5; xx <= x + 5; xx++) {
      const d = Math.hypot(xx - x, yy - y), i = idx(xx, yy);
      if (d < 3.8) { muro[i] = 0; mena[i] = 0; piso[i] = SUELO.TEMPLO; }
      else if (d < 5 && hash2(xx, yy, 13) < 0.55) { muro[i] = MURO.MUSGOSA; mena[i] = 0; }
      else if (d < 5) { muro[i] = 0; piso[i] = SUELO.MUSGO; }
    }
    ruinas.push({ x, y });
  }
  for (let k = 0; k < 3; k++) { const [x, y] = sitio(205, 322, 0.85, 7); santuarioPodre(X, x, y, 6, REGION.SELVA); santuarios.push({ x, y }); }
  // la cámara de los braseros de la Selva: el primer puzle (se abre encendiendo las runas en orden)
  { const [x, y] = sitio(200, 300, 0.75, 9); camaraBraseros(X, x, y, REGION.SELVA, 2); }
  // piedras de paso: una junto a la Muralla y otra en lo hondo de la Selva
  piedraPaso(X, ...sitio(168, 182, 0.25, 3), 'Paso de la Muralla');
  piedraPaso(X, ...sitio(285, 318, 0.6, 3), 'Paso del Templo');

  // luz desde arriba: claros con sol y rayos (sin luz no hay Selva)
  for (let k = 0; k < 330; k++) {
    const [x, y] = punto(R_SELVA0 + 4, R_SELVA1 - 4, 1.0);
    if (!enMundo(x, y) || muro[idx(x, y)] || region[idx(x, y)] === REGION.MOHO || region[idx(x, y)] === REGION.SELLADO) continue;
    M.soles.push({ x, y, r: 5 + r() * 4, k: 0.42 + r() * 0.2 });
    if (r() < 0.6) M.rayos.push({ x, y, a: 0.8 + r() * 0.8, c: [1, 0.97, 0.78] });
  }
  yield [0.48, 'La Ciénaga de la Podre'];

  // ---- las otras dos bandas
  yield* generarCienaga(X);
  yield [0.64, 'La Ciudad Velada'];
  yield* generarCiudad(X);
  yield [0.8, 'Lo que crece en la oscuridad'];

  // senderos desde la Muralla hasta el fondo: siempre se puede llegar andando a cada banda
  for (const da of [-0.5, 0.05, 0.55]) {
    const a = angSelva + da + (r() - 0.5) * 0.2;
    sendero(CENTRO + Math.cos(a) * (R_SELVA0 + 6), CENTRO + Math.sin(a) * (R_SELVA0 + 6), CENTRO + Math.cos(a + (r() - 0.5) * 0.3) * (R_EXTERIOR - 26), CENTRO + Math.sin(a + (r() - 0.5) * 0.3) * (R_EXTERIOR - 26), 1.3, pisoSendero);
  }

  // ---- objetos de la Selva Esmeralda
  // altar del templo y columnas
  { const g = M.guaridas.tamar; ponerObjeto(g.x, g.y - 1, 'altar_cielo'); for (let k = 0; k < 8; k++) { const a = (k / 8) * Math.PI * 2 + Math.PI / 8; pon(Math.round(g.x + Math.cos(a) * 10), Math.round(g.y + Math.sin(a) * 10), 'columna_selva'); } }
  // la mazmorra: tentáculos en las salas, cofres y el gran cofre del centro
  for (const [n, s] of mazmorra.salas.entries()) {
    if (n === 4) {
      const o = ponerObjeto(s.x, s.y - 1, 'cofre_moho'); o.datos = { inv: botinSelva(r, 4), moho: true };
      for (const [dx, dy] of [[-3, -3], [3, -3], [-3, 3], [3, 3]]) M.fijas.push({ tipo: 'tentaculo_moho', x: s.x + dx, y: s.y + dy });
      continue;
    }
    M.fijas.push({ tipo: 'tentaculo_moho', x: s.x + (r() < 0.5 ? -2 : 2), y: s.y + (r() < 0.5 ? -2 : 2) });
    if (r() < 0.55) { const o = pon(s.x, s.y - 3, 'cofre_moho'); if (o) o.datos = { inv: botinSelva(r, 2) }; }
    for (let k = 0; k < 2; k++) pon(s.x + Math.round((r() - 0.5) * 6), s.y + Math.round((r() - 0.5) * 6), 'vasija_flor');
  }
  // aldeas
  for (const c of casitas) {
    const o = pon(c.x0 + 1, c.y0 + 1, 'cofre_selva'); if (o) o.datos = { inv: botinSelva(r, 1) };
    pon(c.x0 + c.w - 2, c.y0 + 1, 'mesa_selva'); pon(c.x0 + c.w - 3, c.y0 + 1, 'silla_selva'); pon(c.x0 + 1, c.y0 + c.h - 1, 'caja_madera');
  }
  for (const a of aldeas) { pon(a.x + 1, a.y, 'hoguera'); pon(a.x - 1, a.y + 1, 'pozo'); for (let x = a.x - 4; x <= a.x + 4; x += 2) { const o = pon(x, a.y + 9, 'cultivo'); if (o) o.datos = { planta: 'avena', crec: 500, regado: false }; } }
  // campamentos y ruinas
  for (const c of campamentos) {
    pon(c.x - 1, c.y - 1, 'tienda'); pon(c.x + 1, c.y + 1, 'hoguera'); pon(c.x + 2, c.y - 1, 'caja_madera'); pon(c.x - 2, c.y + 1, 'caja_madera');
    tablilla(c.x, c.y + 2);
    if (r() < 0.35) pon(c.x + 2, c.y + 2, 'restos_velador');
  }
  for (const c of ruinas) {
    for (let k = 0; k < 3; k++) pon(c.x + Math.round((r() - 0.5) * 5), c.y + Math.round((r() - 0.5) * 5), 'vasija_flor');
    pon(c.x - 2, c.y - 2, 'columna_selva');
    tablilla(c.x, c.y);
    if (r() < 0.5) pon(c.x + 2, c.y + 1, 'restos_velador');
  }
  yield [0.88, 'Los árboles'];

  // ---- lo que crece: plantas, árboles, rocas con mena, panales, hongos, juncos y cosas de la Ciudad
  const junto = (x, y) => muro[idx(x + 1, y)] || muro[idx(x - 1, y)] || muro[idx(x, y + 1)] || muro[idx(x, y - 1)];
  const juntoAgua = (x, y) => esAgua(piso[idx(x + 1, y)]) || esAgua(piso[idx(x - 1, y)]) || esAgua(piso[idx(x, y + 1)]) || esAgua(piso[idx(x, y - 1)]);
  const nadaCerca = (x, y, tipo, rad) => { for (let yy = y - rad; yy <= y + rad; yy++) for (let xx = x - rad; xx <= x + rad; xx++) { const o = M.objetos.get(idx(xx, yy)); if (o && o.tipo === tipo) return false; } return true; };
  const guaridas = Object.values(M.guaridas).filter((g) => Math.hypot(g.x - CENTRO, g.y - CENTRO) > R_SELVA0);
  cadaCasilla((x, y, i) => {
    const reg = region[i];
    if (reg === REGION.SELLADO || muro[i] || M.objetos.has(i) || M.bloqueo[i]) return;
    const p = piso[i];
    if (esAgua(p) || p === SUELO.TEMPLO || p === SUELO.TABLAS || p === SUELO.ARADA || p === SUELO.GRAVA || p === SUELO.VENENO || p === SUELO.CALZADA || p === SUELO.MOSAICO || p === SUELO.LOSA || p === SUELO.CRIPTA || p === SUELO.PLACA || p === SUELO.MADERA) return;
    for (const g of guaridas) if (Math.abs(x - g.x) < 14 && Math.abs(y - g.y) < 14) return;
    const v = r(), bosque = fbm(n5, x / 11, y / 11);
    if (reg === REGION.MOHO) { if (v > 0.985) ponerObjeto(x, y, 'hongo'); return; }
    if (reg === REGION.PRADERA) {
      if (v > 0.97) ponerObjeto(x, y, 'flor_sol');
      else if (v > 0.955) ponerObjeto(x, y, 'avena_silvestre');
      else if (v > 0.952 && nadaCerca(x, y, 'panal', 8)) ponerObjeto(x, y, 'panal');
      else if (v < 0.012 && !junto(x, y)) ponerObjeto(x, y, 'arbol');
      return;
    }
    if (reg === REGION.SELVA) {
      if (bosque > 0.52 && v < 0.11 && !junto(x, y) && nadaCerca(x, y, 'arbol', 1)) ponerObjeto(x, y, hash2(x, y, 77) < 0.06 ? 'arbol_luna' : 'arbol');
      else if (v > 0.975) ponerObjeto(x, y, 'helecho');
      else if (v > 0.968) ponerObjeto(x, y, 'flor_sol');
      else if (v > 0.962) ponerObjeto(x, y, 'avena_silvestre');
      else if (v > 0.958) ponerObjeto(x, y, 'hongo_explosivo');
      else if (v > 0.9565 && nadaCerca(x, y, 'panal', 10)) ponerObjeto(x, y, 'panal');
      else if (v > 0.95 && junto(x, y)) ponerObjeto(x, y, ['roca_carmesi', 'roca_carmesi', 'roca_carmesi', 'roca_hierro', 'roca_oro', 'roca_cobre', 'roca_estano'][Math.floor(r() * 7)]);
      else if (v > 0.9494 && v <= 0.95) ponerObjeto(x, y, 'caja_madera');
      return;
    }
    if (reg === REGION.CIENAGA) { // cada cosa tiene su tramo de v: si no cabe, no se pone otra en su lugar
      if (p === SUELO.PODRE) { if (v > 0.965) ponerObjeto(x, y, 'seta_podre'); else if (v > 0.958 && nadaCerca(x, y, 'bulto_podre', 5)) ponerObjeto(x, y, 'bulto_podre'); return; }
      if (bosque > 0.5 && v < 0.07) { if (!junto(x, y) && nadaCerca(x, y, 'arbol_muerto', 2)) ponerObjeto(x, y, 'arbol_muerto'); }
      else if (v >= 0.9 && juntoAgua(x, y)) ponerObjeto(x, y, 'junco');
      else if (v >= 0.986) ponerObjeto(x, y, 'junco');
      else if (v >= 0.98) ponerObjeto(x, y, 'seta_podre');
      else if (v >= 0.9785) { if (nadaCerca(x, y, 'capullo_podre', 12)) ponerObjeto(x, y, 'capullo_podre'); }
      else if (v >= 0.972) { if (junto(x, y)) ponerObjeto(x, y, r() < 0.75 ? 'roca_medula' : 'roca_carmesi'); }
      else if (v >= 0.971) { if (nadaCerca(x, y, 'bulto_podre', 16)) ponerObjeto(x, y, 'bulto_podre'); }
      else if (v >= 0.9706) ponerObjeto(x, y, 'restos_velador');
      else if (v >= 0.9698) ponerObjeto(x, y, 'caja_madera');
      return;
    }
    if (reg === REGION.CIUDAD) {
      if (p === SUELO.PODRE) { if (v > 0.97) ponerObjeto(x, y, 'seta_podre'); else if (v > 0.962 && nadaCerca(x, y, 'capullo_podre', 8)) ponerObjeto(x, y, 'capullo_podre'); return; }
      if (v >= 0.975) { if (junto(x, y)) ponerObjeto(x, y, r() < 0.8 ? 'roca_plata' : 'roca_oro'); }
      else if (v >= 0.9735) ponerObjeto(x, y, 'columna_rota');
      else if (v >= 0.9725) ponerObjeto(x, y, 'urna_velada');
      else if (v >= 0.9721) ponerObjeto(x, y, 'restos_velador');
      else if (v >= 0.9712) { if (nadaCerca(x, y, 'estatua_rota', 14)) ponerObjeto(x, y, 'estatua_rota'); }
      else if (v >= 0.969) ponerObjeto(x, y, 'seta_podre');
      else if (v >= 0.962 && p === SUELO.CENIZA && fbm(n5, x / 9, y / 9) > 0.62) ponerObjeto(x, y, 'arbol_muerto');
    }
  });
}

// ---------------------------------------------------------------- estructuras que comparten las bandas

// Un santuario de la Podre: un claro de tierra podrida, un ídolo y bultos alrededor. Los grutos rezan delante y
// no atacan hasta que te acercas (fijas con reza).
function santuarioPodre(X, cx, cy, rad, reg) {
  const { muro, mena, piso, region } = M, { n3, r } = X;
  for (let y = Math.floor(cy - rad - 3); y <= cy + rad + 3; y++) for (let x = Math.floor(cx - rad - 3); x <= cx + rad + 3; x++) {
    if (!enMundo(x, y)) continue;
    const i = idx(x, y), d = Math.hypot(x - cx, y - cy) + (n3(x / 2.5, y / 2.5) - 0.5) * 3;
    if (region[i] === REGION.SELLADO || d > rad + 2) continue;
    if (d < rad) { muro[i] = 0; mena[i] = 0; region[i] = reg; }
    if (d < rad + 1.5 && !esAgua(piso[i])) piso[i] = SUELO.PODRE;
    else if (d < rad + 2 && muro[i] && hash2(x, y, 51) < 0.5) muro[i] = MURO.COSTRA;
  }
  ponerObjeto(cx, cy - 1, 'idolo_podre');
  for (let k = 0; k < 5; k++) { const a = (k / 5) * Math.PI * 2 + r(), x = Math.round(cx + Math.cos(a) * (rad - 1.5)), y = Math.round(cy + Math.sin(a) * (rad - 1.5)); if (X.libre(x, y)) ponerObjeto(x, y, 'bulto_podre'); }
  const rezan = reg === REGION.SELVA ? ['gruto_jardinero', 'gruto_lancero'] : reg === REGION.CIENAGA ? ['gruto_lancero', 'gruto_chaman', 'gruto_lancero'] : ['gruto_chaman', 'gruto_bruto'];
  rezan.forEach((tipo, k) => M.fijas.push({ tipo, x: cx - rezan.length + 1 + k * 2, y: cy + 2, reza: true, ix: cx }));
  M.soles.push({ x: cx, y: cy, r: rad, k: 0.35, c: [1, 0.3, 0.7] });
}

// La cámara de los braseros: una sala sellada (puerta de runas) con un cofre; fuera, n+2 braseros con sus runas y
// una tablilla que dice el orden. Encenderlos en orden abre la puerta; si te equivocas, se apagan todos.
function camaraBraseros(X, cx, cy, reg, calidad) {
  const { muro, mena, piso, region } = M, { r } = X, W = 7, H = 5;
  const pared = reg === REGION.CIUDAD ? MURO.VELADA_VIEJA : MURO.VELADA_VIEJA, suelo = reg === REGION.SELVA ? SUELO.TEMPLO : SUELO.CALZADA;
  X.cavar(cx, cy + 3, 8.5, suelo, reg);
  const x0 = cx - (W >> 1), y0 = cy - H - 1;
  for (let y = y0 - 1; y <= y0 + H; y++) for (let x = x0 - 1; x <= x0 + W; x++) {
    const i = idx(x, y), borde = x === x0 - 1 || y === y0 - 1 || x === x0 + W || y === y0 + H;
    muro[i] = borde ? pared : 0; mena[i] = 0; region[i] = reg; if (!borde) piso[i] = SUELO.MOSAICO;
  }
  const px = cx, py = y0 + H; muro[idx(px, py)] = 0; piso[idx(px, py)] = SUELO.MOSAICO;
  ponerObjeto(px, py, 'puerta_sellada');
  const cofre = ponerObjeto(cx, y0 + 1, 'cofre_velado'); cofre.datos = { inv: botinBanda(r, BANDA(reg), calidad + 1) };
  // los braseros, en arco delante de la puerta, cada uno con una runa; el orden es una permutación
  const n = Math.min(4, 2 + calidad), runas = [0, 1, 2, 3].sort(() => r() - 0.5).slice(0, n), orden = runas.slice().sort(() => r() - 0.5);
  const braseros = [];
  runas.forEach((runa, k) => {
    const a = Math.PI * (0.15 + (0.7 * k) / Math.max(1, n - 1)), x = Math.round(cx + Math.cos(a) * 6), y = Math.round(py + 1 + Math.sin(a) * 4.5);
    muro[idx(x, y)] = 0; const o = ponerObjeto(x, y, 'brasero_runa'); o.datos = { runa, puzle: M.puzles.length }; braseros.push({ x, y, runa });
  });
  const t = ponerObjeto(cx + 7, py + 2, 'tablilla');
  t.datos = { orden: orden.slice() };
  M.puzles.push({ tipo: 'braseros', orden, braseros, puerta: { x: px, y: py }, encendidos: [] });
}

// Piedra de paso: viajas entre las que hayas tocado.
function piedraPaso(X, x, y, nombre) {
  X.cavar(x, y, 3, 0);
  const o = ponerObjeto(x, y, 'piedra_paso');
  o.datos = { nombre };
  M.pasos.push({ x, y, nombre });
}

// Botín de los cofres del mundo grande. banda 0: Selva; 1: Ciénaga; 2: Ciudad. calidad 1–4.
function botinBanda(r, banda, calidad) {
  if (banda === 0) return botinSelva(r, calidad);
  const inv = new Array(30).fill(null), pon = (id, n) => { if (!ITEMS[id]) return; const k = inv.findIndex((s) => !s); if (k >= 0) inv[k] = { id, n }; };
  const comun = banda === 1
    ? [['madera_muerta', 4, 10], ['moneda', 15, 40], ['cebo', 3, 8], ['bomba', 2, 5], ['espora_podre', 2, 5], ['lingote_carmesi', 1, 3], ['mineral_medula', 2, 5], ['savia', 1, 3], ['hueso', 3, 6]]
    : [['moneda', 25, 60], ['bloque_velada', 4, 10], ['lingote_medula', 1, 3], ['mineral_plata', 2, 5], ['pagina_velada', 1, 2], ['piedra_regreso', 1, 2], ['bomba', 3, 6], ['flecha_carmesi', 8, 16]];
  const raro = banda === 1
    ? [['lingote_medula', 2, 4], ['savia_negra', 1, 2], ['gema_antigua', 1, 1], ['piedra_regreso', 1, 3], ['pagina_velada', 1, 1], ['moneda', 40, 80], ['huevo_moho', 1, 1]]
    : [['lingote_plata', 2, 4], ['gema_antigua', 1, 2], ['pagina_velada', 2, 3], ['savia_negra', 1, 2], ['moneda', 60, 120], ['huevo_luz', 1, 1]];
  const epico = banda === 1 ? [['collar_moho', 1, 1], ['anillo_carmesi', 1, 1], ['huevo_leal', 1, 1], ['arco_carmesi', 1, 1]] : [['huevo_leal', 1, 1], ['alfombra_verde', 6, 10], ['idolo_misterioso', 1, 1], ['collar_moho', 1, 1]];
  const sacar = (lista) => { const [id, a, b] = lista[Math.floor(r() * lista.length)]; pon(id, a + Math.floor(r() * (b - a + 1))); };
  for (let k = 0; k < 2 + calidad; k++) sacar(comun);
  for (let k = 0; k < calidad; k++) if (r() < 0.65) sacar(raro);
  if (calidad >= 2 && r() < 0.4) sacar(epico);
  if (calidad >= 4) { sacar(epico); pon('moneda', 150); }
  return inv;
}

// Botín de los cofres de la Selva. calidad 1: aldea; 2: sala de la mazmorra; 4: el gran cofre del moho.
function botinSelva(r, calidad) {
  const inv = new Array(30).fill(null), pon = (id, n) => { const k = inv.findIndex((s) => !s); if (k >= 0) inv[k] = { id, n }; };
  const comun = [['madera_selva', 4, 10], ['moneda', 10, 30], ['cebo', 3, 8], ['semilla_avena', 2, 4], ['fruta_selva', 2, 4], ['bomba', 2, 4], ['flecha_carmesi', 6, 12], ['hoja_grande', 3, 6], ['savia', 1, 3]];
  const raro = [['lingote_carmesi', 2, 5], ['pluma_antigua', 1, 2], ['piedra_regreso', 1, 3], ['semilla_melon', 1, 2], ['gema_antigua', 1, 1], ['moneda', 30, 60], ['huevo_baboso', 1, 1]];
  const epico = [['anillo_carmesi', 1, 1], ['collar_moho', 1, 1], ['huevo_leal', 1, 1], ['huevo_moho', 1, 1], ['maza_carmesi', 1, 1], ['arco_carmesi', 1, 1]];
  const sacar = (lista) => { const [id, a, b] = lista[Math.floor(r() * lista.length)]; pon(id, a + Math.floor(r() * (b - a + 1))); };
  for (let k = 0; k < 2 + calidad; k++) sacar(comun);
  for (let k = 0; k < calidad; k++) if (r() < 0.65) sacar(raro);
  if (calidad >= 2 && r() < 0.35) sacar(epico);
  if (calidad >= 4) { pon('huevo_moho', 1); pon('idolo_misterioso', 1); pon('collar_moho', 1); pon('moneda', 120); }
  return inv;
}
