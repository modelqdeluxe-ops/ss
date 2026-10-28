'use strict';
// El anillo exterior, al otro lado de la Gran Muralla. Está partido en tres sectores como en Core Keeper: la
// Selva Esmeralda (fase 2) y dos sectores de roca dormida (el Mar Hundido y el Desierto del Origen, fases 3 y
// 4). La Selva tiene luz que baja del cielo, árboles, lagos, praderas de flores, la Mazmorra del Moho, el templo
// de Tamar, la guarida de Hiedra Viva, aldeas de grutos, campamentos y ruinas con tablillas.

const R_SELVA0 = R_MURALLA + 5;           // donde empieza el anillo exterior
const ANCHO_SELVA = Math.PI / 3;          // medio ancho del sector de la Selva (±60°)

function generarExterior(sem) {
  const N = N_MUNDO, { muro, mena, piso, region } = M;
  const n1 = crearRuido(sem + 301), n2 = crearRuido(sem + 302), n3 = crearRuido(sem + 303), n4 = crearRuido(sem + 304), n5 = crearRuido(sem + 305), n6 = crearRuido(sem + 306);
  const r = azar(sem + 777);
  const angSelva = M.lado + Math.PI / 2 + (r() - 0.5) * 0.6;
  M.angSelva = angSelva; M.soles = []; M.fijas = [];
  const enSelva = (x, y) => difAng(Math.atan2(y - CENTRO, x - CENTRO) + (fbm(n6, x / 30, y / 30) - 0.5) * 0.5, angSelva) < ANCHO_SELVA;

  // ---- terreno base
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const d = Math.hypot(x - CENTRO, y - CENTRO) + (n2(x / 9, y / 9) - 0.5) * 2;
    if (d <= R_SELVA0) continue;
    const i = idx(x, y);
    mena[i] = 0;
    if (d > R_EXTERIOR || x < 2 || y < 2 || x > N - 3 || y > N - 3) { muro[i] = MURO.ROCA; region[i] = REGION.SELLADO; piso[i] = SUELO.PIEDRA; continue; }
    if (!enSelva(x, y)) { muro[i] = MURO.SELLO; region[i] = REGION.SELLADO; piso[i] = SUELO.PIEDRA; continue; }
    region[i] = REGION.SELVA;
    const p = fbm(n3, x / 16, y / 16);
    piso[i] = p > 0.62 ? SUELO.MUSGO : p < 0.34 ? SUELO.TIERRA_SELVA : SUELO.HIERBA;
    const cueva = fbm(n1, x / 15, y / 15, 3) + (n4(x / 5, y / 5) - 0.5) * 0.16;
    const borde = d < R_SELVA0 + 6 ? 1 : d > R_EXTERIOR - 8 ? -0.2 : 0; // se abre junto a la Muralla y se cierra al fondo
    if (cueva + borde * 0.2 > 0.5) { muro[i] = 0; continue; }
    const t = fbm(n5, x / 20, y / 20);
    muro[i] = d > 238 && t > 0.56 ? MURO.MUSGOSA : t > 0.64 ? MURO.ESPINO : MURO.HIERBA;
    const v = n4(x / 3.6, y / 3.6), o = n2(x / 3, y / 3);
    if (muro[i] === MURO.HIERBA && v > 0.74) mena[i] = MENA.CARMESI;
    else if (muro[i] === MURO.MUSGOSA) mena[i] = v > 0.78 ? MENA.CARMESI : o > 0.85 ? MENA.ORO : v < 0.14 ? MENA.HIERRO : 0;
    else if (muro[i] === MURO.HIERBA && o > 0.88) mena[i] = MENA.ORO;
  }
  // lagos (con orilla) en lo abierto
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const i = idx(x, y);
    if (region[i] !== REGION.SELVA || muro[i]) continue;
    const l = fbm(n6, x / 22, y / 22, 2);
    if (l < 0.69) continue;
    let orilla = true;
    for (let dy = -1; dy <= 1 && orilla; dy++) for (let dx = -1; dx <= 1; dx++) if (muro[idx(x + dx, y + dy)]) { orilla = false; break; }
    if (orilla) piso[i] = SUELO.AGUA;
  }
  const cavar = (cx, cy, rad, conPiso = 0, reg = 0) => {
    for (let y = Math.floor(cy - rad); y <= cy + rad; y++) for (let x = Math.floor(cx - rad); x <= cx + rad; x++) {
      if (!enMundo(x, y) || Math.hypot(x - cx, y - cy) > rad) continue;
      const i = idx(x, y); muro[i] = 0; mena[i] = 0; if (conPiso) piso[i] = conPiso; if (reg) region[i] = reg;
    }
  };
  const punto = (dMin, dMax, desvio = 0.85) => { const a = angSelva + (r() - 0.5) * 2 * desvio, d = dMin + r() * (dMax - dMin); return [Math.round(CENTRO + Math.cos(a) * d), Math.round(CENTRO + Math.sin(a) * d)]; };
  const libre = (x, y) => enMundo(x, y) && !muro[idx(x, y)] && !M.objetos.has(idx(x, y)) && piso[idx(x, y)] !== SUELO.AGUA;
  // sitios que no se pisan: primero lo grande (templo, mazmorra, jardín de Hiedra), luego lo demás
  const ocupados = [];
  const sitio = (dMin, dMax, desvio, radio) => {
    let p = null;
    for (let k = 0; k < 120 && !p; k++) { const q = punto(dMin, dMax, desvio); if (ocupados.every((o) => Math.hypot(q[0] - o.x, q[1] - o.y) > radio + o.r + 3)) p = q; }
    p = p || punto(dMin, dMax, desvio);
    ocupados.push({ x: p[0], y: p[1], r: radio });
    return p;
  };
  const posTemplo = sitio(246, 262, 0.5, 19), posMazmorra = sitio(205, 238, 0.75, 25), posHiedra = sitio(188, 228, 0.75, 17);

  // ---- camino: del anillo interior a la Muralla, mirando a la Selva (para que la encuentres)
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

  // ---- praderas de flores
  const praderas = [];
  for (let k = 0; k < 3; k++) {
    const rad = 13 + r() * 7, [cx, cy] = sitio(175, 270, 0.85, rad + 2);
    for (let y = Math.floor(cy - rad - 3); y <= cy + rad + 3; y++) for (let x = Math.floor(cx - rad - 3); x <= cx + rad + 3; x++) {
      if (!enMundo(x, y)) continue;
      const i = idx(x, y), d = Math.hypot(x - cx, y - cy) + (n3(x / 3, y / 3) - 0.5) * 4;
      if (d > rad || region[i] !== REGION.SELVA) continue;
      region[i] = REGION.PRADERA;
      if (d < rad - 2 || hash2(x, y, 41) < 0.6) { muro[i] = 0; mena[i] = 0; }
      if (piso[i] !== SUELO.AGUA) piso[i] = fbm(n5, x / 6, y / 6) > 0.45 ? SUELO.FLORES : SUELO.HIERBA;
    }
    praderas.push({ x: cx, y: cy, r: rad });
    M.soles.push({ x: cx, y: cy, r: rad * 0.9, k: 0.55 });
  }

  // ---- la guarida de Hiedra Viva: un jardín envenenado rodeado de espino
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

  // ---- el templo del cielo: plataforma de losas, columnas y el altar donde se llama a Tamar
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

  // ---- la Mazmorra del Moho: 3×3 salas de muros que no se rompen; la puerta da a la Selva
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

  // ---- aldeas de grutos: casitas de madera de selva con cofre, mesa y un huerto
  const aldeas = [];
  for (let k = 0; k < 2; k++) {
    const [cx, cy] = sitio(180, 240, 0.85, 13);
    cavar(cx, cy, 11, SUELO.TIERRA_SELVA);
    for (const [hx, hy] of [[-6, -4], [3, -5], [-2, 4]]) {
      const x0 = cx + hx, y0 = cy + hy, w = 6, h = 5;
      for (let y = y0 - 1; y <= y0 + h; y++) for (let x = x0 - 1; x <= x0 + w; x++) {
        const i = idx(x, y), borde = x === x0 - 1 || y === y0 - 1 || x === x0 + w || y === y0 + h;
        muro[i] = borde ? MURO.SELVA_MADERA : 0; piso[i] = SUELO.TABLAS; mena[i] = 0;
      }
      muro[idx(x0 + (w >> 1), y0 + h)] = 0; // puerta abajo
      aldeas.push({ x0, y0, w, h });
    }
    for (let y = cy + 9; y <= cy + 10; y++) for (let x = cx - 4; x <= cx + 4; x++) { const i = idx(x, y); muro[i] = 0; piso[i] = SUELO.ARADA; }
    M.fijas.push({ tipo: 'gruto_jardinero', x: cx - 3, y: cy + 8 }, { tipo: 'gruto_jardinero', x: cx + 4, y: cy }, { tipo: 'gruto_cazador', x: cx, y: cy - 9 });
    M.aldeas = (M.aldeas || []).concat([{ x: cx, y: cy }]);
  }

  // ---- campamentos abandonados y ruinas del cielo (con tablillas)
  const campamentos = [], ruinas = [];
  for (let k = 0; k < 7; k++) { const [x, y] = sitio(160, 285, 0.9, 5); cavar(x, y, 3.6, SUELO.TIERRA_SELVA); campamentos.push({ x, y }); }
  for (let k = 0; k < 8; k++) {
    const [x, y] = sitio(165, 285, 0.9, 6);
    for (let yy = y - 5; yy <= y + 5; yy++) for (let xx = x - 5; xx <= x + 5; xx++) {
      const d = Math.hypot(xx - x, yy - y), i = idx(xx, yy);
      if (d < 3.8) { muro[i] = 0; mena[i] = 0; piso[i] = SUELO.TEMPLO; }
      else if (d < 5 && hash2(xx, yy, 13) < 0.55) { muro[i] = MURO.MUSGOSA; mena[i] = 0; }
      else if (d < 5) { muro[i] = 0; piso[i] = SUELO.MUSGO; }
    }
    ruinas.push({ x, y });
  }

  // ---- luz desde arriba: claros con sol y rayos (sin luz no hay Selva)
  for (let k = 0; k < 140; k++) {
    const [x, y] = punto(R_SELVA0 + 4, R_EXTERIOR - 6, 1.0);
    if (!enMundo(x, y) || muro[idx(x, y)] || region[idx(x, y)] === REGION.MOHO || region[idx(x, y)] === REGION.SELLADO) continue;
    M.soles.push({ x, y, r: 5 + r() * 4, k: 0.42 + r() * 0.2 });
    if (r() < 0.6) M.rayos.push({ x, y, a: 0.8 + r() * 0.8, c: [1, 0.97, 0.78] });
  }

  // ---- objetos
  const pon = (x, y, tipo) => { if (!libre(x, y)) return null; return ponerObjeto(x, y, tipo); };
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
  for (const c of aldeas) {
    const o = pon(c.x0 + 1, c.y0 + 1, 'cofre_selva'); if (o) o.datos = { inv: botinSelva(r, 1) };
    pon(c.x0 + c.w - 2, c.y0 + 1, 'mesa_selva'); pon(c.x0 + c.w - 3, c.y0 + 1, 'silla_selva'); pon(c.x0 + 1, c.y0 + c.h - 1, 'caja_madera');
  }
  for (const a of M.aldeas || []) { pon(a.x + 1, a.y, 'hoguera'); pon(a.x - 1, a.y + 1, 'pozo'); for (let x = a.x - 4; x <= a.x + 4; x += 2) { const o = pon(x, a.y + 9, 'cultivo'); if (o) o.datos = { planta: 'avena', crec: 500, regado: false }; } }
  // campamentos
  let tab = 0;
  for (const c of campamentos) {
    pon(c.x - 1, c.y - 1, 'tienda'); pon(c.x + 1, c.y + 1, 'hoguera'); pon(c.x + 2, c.y - 1, 'caja_madera'); pon(c.x - 2, c.y + 1, 'caja_madera');
    const t = pon(c.x, c.y + 2, 'tablilla'); if (t) t.datos = { n: tab++ % TABLILLAS.length };
  }
  for (const c of ruinas) {
    for (let k = 0; k < 3; k++) pon(c.x + Math.round((r() - 0.5) * 5), c.y + Math.round((r() - 0.5) * 5), 'vasija_flor');
    pon(c.x - 2, c.y - 2, 'columna_selva');
    const t = pon(c.x, c.y, 'tablilla'); if (t) t.datos = { n: tab++ % TABLILLAS.length };
  }
  // plantas, árboles, rocas con mena, panales y hongos explosivos
  const junto = (x, y) => muro[idx(x + 1, y)] || muro[idx(x - 1, y)] || muro[idx(x, y + 1)] || muro[idx(x, y - 1)];
  const nadaCerca = (x, y, tipo, rad) => { for (let yy = y - rad; yy <= y + rad; yy++) for (let xx = x - rad; xx <= x + rad; xx++) { const o = M.objetos.get(idx(xx, yy)); if (o && o.tipo === tipo) return false; } return true; };
  for (let y = 3; y < N - 3; y++) for (let x = 3; x < N - 3; x++) {
    const i = idx(x, y), reg = region[i];
    if ((reg !== REGION.SELVA && reg !== REGION.PRADERA && reg !== REGION.MOHO) || muro[i] || M.objetos.has(i)) continue;
    const p = piso[i];
    if (p === SUELO.AGUA || p === SUELO.TEMPLO || p === SUELO.TABLAS || p === SUELO.ARADA || p === SUELO.GRAVA || p === SUELO.VENENO) continue;
    if (Object.values(M.guaridas).some((g) => Math.hypot(x - g.x, y - g.y) < 13)) continue;
    const v = r(), bosque = fbm(n5, x / 11, y / 11);
    if (reg === REGION.MOHO) { if (v > 0.985) ponerObjeto(x, y, 'hongo'); continue; }
    if (reg === REGION.PRADERA) {
      if (v > 0.97) ponerObjeto(x, y, 'flor_sol');
      else if (v > 0.955) ponerObjeto(x, y, 'avena_silvestre');
      else if (v > 0.952 && nadaCerca(x, y, 'panal', 8)) ponerObjeto(x, y, 'panal');
      else if (v < 0.012 && !junto(x, y)) ponerObjeto(x, y, 'arbol');
      continue;
    }
    if (bosque > 0.52 && v < 0.11 && !junto(x, y) && nadaCerca(x, y, 'arbol', 1)) ponerObjeto(x, y, hash2(x, y, 77) < 0.06 ? 'arbol_luna' : 'arbol');
    else if (v > 0.975) ponerObjeto(x, y, 'helecho');
    else if (v > 0.968) ponerObjeto(x, y, 'flor_sol');
    else if (v > 0.962) ponerObjeto(x, y, 'avena_silvestre');
    else if (v > 0.958) ponerObjeto(x, y, 'hongo_explosivo');
    else if (v > 0.9565 && nadaCerca(x, y, 'panal', 10)) ponerObjeto(x, y, 'panal');
    else if (v > 0.95 && junto(x, y)) ponerObjeto(x, y, ['roca_carmesi', 'roca_carmesi', 'roca_carmesi', 'roca_hierro', 'roca_oro', 'roca_cobre', 'roca_estano'][Math.floor(r() * 7)]);
    else if (v > 0.9494 && v <= 0.95) ponerObjeto(x, y, 'caja_madera');
  }
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
