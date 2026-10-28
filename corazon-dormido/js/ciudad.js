'use strict';
// La Ciudad Velada (banda honda del sector de la Selva, de R_SELVA2 a R_EXTERIOR). La ciudad de los Veladores de
// antes de la Muralla: barrios amurallados con calles, casas, plazas, fuentes y farolas que aún dan una luz fría; la
// Gran Biblioteca (sus libros cuentan la verdad), el Templo de las Placas (puzle de bloques), la torre del Archivero
// y la Cripta de los Veladores (mazmorra con palancas y el Centinela al fondo). El terreno base está en selva.js.

function* generarCiudad(X) {
  const { muro, mena, piso, region } = M, { r, sitio, cavar, pon, tablilla } = X;
  const enBanda = (x, y) => enMundo(x, y) && region[idx(x, y)] !== REGION.SELLADO;
  const pared = (x, y, tipo) => { if (!enBanda(x, y)) return; const i = idx(x, y); muro[i] = tipo; mena[i] = 0; };
  const suelo = (x, y, tipo, reg = REGION.CIUDAD) => { if (!enBanda(x, y)) return; const i = idx(x, y); muro[i] = 0; mena[i] = 0; piso[i] = tipo; region[i] = reg; };
  // una sala rectangular: paredes alrededor, suelo dentro (las puertas se abren aparte)
  const sala = (x0, y0, w, h, tipoPared, tipoSuelo, reg = REGION.CIUDAD) => {
    for (let y = y0 - 1; y <= y0 + h; y++) for (let x = x0 - 1; x <= x0 + w; x++) {
      const borde = x === x0 - 1 || y === y0 - 1 || x === x0 + w || y === y0 + h;
      if (borde) { pared(x, y, tipoPared); if (enBanda(x, y)) region[idx(x, y)] = reg; } else suelo(x, y, tipoSuelo, reg);
    }
  };

  // ---- los barrios: muralla con cuatro puertas, calles en cuadrícula y manzanas
  const barrios = [];
  for (let k = 0; k < 9; k++) {
    const [cx, cy] = sitio(502, 580, 0.82, 42), W = 62 + Math.floor(r() * 14), H = 46 + Math.floor(r() * 10), x0 = cx - (W >> 1), y0 = cy - (H >> 1);
    for (let y = y0 - 1; y <= y0 + H; y++) for (let x = x0 - 1; x <= x0 + W; x++) {
      const borde = x === x0 - 1 || y === y0 - 1 || x === x0 + W || y === y0 + H;
      if (borde) pared(x, y, MURO.VELADA_VIEJA); else suelo(x, y, SUELO.CALZADA);
    }
    for (const [px, py, dx, dy] of [[cx, y0 - 1, 0, -1], [cx, y0 + H, 0, 1], [x0 - 1, cy, -1, 0], [x0 + W, cy, 1, 0]]) // puertas y su salida
      for (let s = 0; s < 6; s++) for (let w = -1; w <= 1; w++) { const x = px + dx * s + (dy ? w : 0), y = py + dy * s + (dx ? w : 0); if (enBanda(x, y) && muro[idx(x, y)] !== MURO.VELADA_VIEJA || s === 0) suelo(x, y, SUELO.CALZADA); }
    // manzanas: entre calles de 3 casillas
    const P = 13;
    const calleX = [], calleY = [];
    for (let x = x0 + 1; x + P <= x0 + W; x += P) calleX.push(x);
    for (let y = y0 + 1; y + P <= y0 + H; y += P) calleY.push(y);
    for (const bx of calleX) for (const by of calleY) {
      const mx = bx + 3, my = by + 3, mw = P - 4, mh = P - 4; // la manzana deja 3 de calle a cada lado
      const tipo = r();
      if (tipo < 0.58) { // casa
        sala(mx, my, mw, mh, MURO.VELADA, SUELO.LOSA);
        const lado = Math.floor(r() * 4), t = lado < 2 ? mx + 1 + Math.floor(r() * (mw - 2)) : my + 1 + Math.floor(r() * (mh - 2));
        const [px, py] = lado === 0 ? [t, my - 1] : lado === 1 ? [t, my + mh] : lado === 2 ? [mx - 1, t] : [mx + mw, t];
        suelo(px, py, SUELO.LOSA);
        if (r() < 0.35) for (let k2 = 0; k2 < 4; k2++) { const x = mx - 1 + Math.floor(r() * (mw + 2)), y = my - 1 + Math.floor(r() * (mh + 2)); if (enBanda(x, y) && muro[idx(x, y)] === MURO.VELADA) suelo(x, y, SUELO.CENIZA); } // casa medio caída
        const sitios = [[mx, my], [mx + mw - 1, my], [mx, my + mh - 1], [mx + mw - 1, my + mh - 1], [mx + (mw >> 1), my]].sort(() => r() - 0.5);
        const muebles = ['estante_roto', 'urna_velada', r() < 0.45 ? 'restos_velador' : 'urna_velada', r() < 0.2 ? 'atril' : 'estante_roto', r() < 0.3 ? 'cofre_ruinas' : 'urna_velada'];
        muebles.forEach((m, j) => { const [x, y] = sitios[j]; const o = m === 'atril' ? tablilla(x, y, 'atril') : pon(x, y, m); if (o && m === 'cofre_ruinas') o.datos = { inv: botinBanda(r, 2, 1) }; });
      } else if (tipo < 0.75) { // plaza con fuente o estatua y bancos
        for (let y = my; y < my + mh; y++) for (let x = mx; x < mx + mw; x++) suelo(x, y, (x + y) % 2 ? SUELO.CALZADA : SUELO.LOSA);
        const fx = mx + (mw >> 1), fy = my + (mh >> 1);
        if (r() < 0.5) pon(fx, fy, 'fuente_seca'); else pon(fx, fy, 'estatua_velador');
        pon(fx - 3, fy + 3, 'banco_piedra'); pon(fx + 3, fy + 3, 'banco_piedra');
      } else if (tipo < 0.9) { // jardín muerto
        for (let y = my; y < my + mh; y++) for (let x = mx; x < mx + mw; x++) suelo(x, y, SUELO.CENIZA);
        pon(mx + 2, my + 2, 'arbol_muerto'); pon(mx + mw - 3, my + mh - 3, 'arbol_muerto');
        if (r() < 0.6) pon(mx + (mw >> 1), my + (mh >> 1), 'estatua_rota');
      } else { // escombros
        for (let y = my; y < my + mh; y++) for (let x = mx; x < mx + mw; x++) { if (hash2(x, y, 81) < 0.45) pared(x, y, MURO.PIEDRA_GRIS); else suelo(x, y, SUELO.CENIZA); }
      }
    }
    // farolas a lo largo de las calles y estatuas en algunos cruces
    for (const bx of calleX) for (let y = y0 + 2; y < y0 + H - 1; y += 7) pon(bx + 1, y, 'farola_velada');
    for (const by of calleY) for (let x = x0 + 4; x < x0 + W - 1; x += 9) pon(x, by + 1, 'farola_velada');
    for (const bx of calleX) for (const by of calleY) { const q = r(); if (q < 0.12) pon(bx + 1, by + 1, 'estatua_velador'); else if (q < 0.2) M.fijas.push({ tipo: 'guardian_piedra', x: bx + 1, y: by + 1 }); } // algunas estatuas no lo son
    // la Podre también ha entrado en algunos barrios
    if (r() < 0.5) {
      const px = x0 + 6 + Math.floor(r() * (W - 12)), py = y0 + 6 + Math.floor(r() * (H - 12)), rad = 5 + r() * 3;
      for (let y = Math.floor(py - rad); y <= py + rad; y++) for (let x = Math.floor(px - rad); x <= px + rad; x++) if (enBanda(x, y) && Math.hypot(x - px, y - py) < rad && !muro[idx(x, y)]) piso[idx(x, y)] = SUELO.PODRE;
      pon(px, py, 'bulto_podre');
    }
    barrios.push({ x: cx, y: cy, x0, y0, W, H });
    M.fijas.push({ tipo: 'necrofago', x: cx, y: cy + 1 }, { tipo: 'velado', x: cx + 6, y: cy - 4 });
  }
  M.barrios = barrios.map((b) => ({ x: b.x, y: b.y }));
  // avenidas de losas con farolas entre las puertas de los barrios (cada barrio con los dos más cercanos)
  const puertas = (b) => [[b.x, b.y0 - 4], [b.x, b.y0 + b.H + 3], [b.x0 - 4, b.y], [b.x0 + b.W + 3, b.y]];
  const avenida = (ax, ay, bx, by) => {
    let x = ax, y = ay, a = Math.atan2(by - ay, bx - ax), paso = 0;
    for (let s = 0; s < 1500 && Math.hypot(bx - x, by - y) > 1.5; s++) {
      const objetivo = Math.atan2(by - y, bx - x);
      a += (r() - 0.5) * 0.16; a += Math.atan2(Math.sin(objetivo - a), Math.cos(objetivo - a)) * 0.3;
      x += Math.cos(a); y += Math.sin(a); paso++;
      for (let yy = Math.floor(y - 1.6); yy <= y + 1.6; yy++) for (let xx = Math.floor(x - 1.6); xx <= x + 1.6; xx++) {
        if (!enBanda(xx, yy) || Math.hypot(xx - x, yy - y) > 1.6) continue;
        const i = idx(xx, yy), m = muro[i];
        if (m === MURO.VELADA_VIEJA || m === MURO.CRIPTA || m === MURO.VELADA || region[i] !== REGION.CIUDAD) continue;
        if (piso[i] === SUELO.LOSA || piso[i] === SUELO.MOSAICO) continue;
        muro[i] = 0; mena[i] = 0; piso[i] = SUELO.CALZADA;
      }
      if (paso % 10 === 0) { const l = paso % 20 ? 1 : -1; pon(Math.round(x - Math.sin(a) * 2.6 * l), Math.round(y + Math.cos(a) * 2.6 * l), 'farola_velada'); }
    }
  };
  const unir = (ax, ay, b) => { const p = puertas(b).reduce((m, q) => (Math.hypot(q[0] - ax, q[1] - ay) < Math.hypot(m[0] - ax, m[1] - ay) ? q : m)); avenida(ax, ay, p[0], p[1]); };
  const hechas = new Set();
  for (const a of barrios) {
    const cerca = barrios.filter((b) => b !== a).sort((p, q) => Math.hypot(p.x - a.x, p.y - a.y) - Math.hypot(q.x - a.x, q.y - a.y)).slice(0, 2);
    for (const b of cerca) {
      const clave = [barrios.indexOf(a), barrios.indexOf(b)].sort().join();
      if (hechas.has(clave) || Math.hypot(b.x - a.x, b.y - a.y) > 220) continue;
      hechas.add(clave);
      const pa = puertas(a).reduce((m, q) => (Math.hypot(q[0] - b.x, q[1] - b.y) < Math.hypot(m[0] - b.x, m[1] - b.y) ? q : m));
      unir(pa[0], pa[1], b);
    }
  }
  const alBarrio = (x, y) => { if (barrios.length) unir(x, y, barrios.reduce((m, b) => (Math.hypot(b.x - x, b.y - y) < Math.hypot(m.x - x, m.y - y) ? b : m))); };
  yield [0.7, 'La Gran Biblioteca'];

  // ---- la Gran Biblioteca: hileras de estantes, atriles con la historia de verdad
  {
    const [cx, cy] = sitio(505, 560, 0.45, 26), W = 36, H = 22, x0 = cx - (W >> 1), y0 = cy - (H >> 1);
    sala(x0, y0, W, H, MURO.VELADA_VIEJA, SUELO.MOSAICO);
    for (const [px, py, dx, dy] of [[cx, y0 + H, 0, 1], [cx, y0 - 1, 0, -1]]) for (let s = 0; s < 5; s++) for (let w = -1; w <= 1; w++) suelo(px + dx * s + w, py + dy * s, SUELO.CALZADA);
    for (let fila = 0; fila < 3; fila++) for (let x = x0 + 2; x < x0 + W - 2; x++) if ((x - x0) % 8 !== 0 && Math.abs(x - cx) > 1) pon(x, y0 + 3 + fila * 6, 'estante_roto');
    for (let k = 0; k < 6; k++) tablilla(x0 + 4 + k * 6, y0 + 6 + (k % 2) * 6, 'atril');
    for (const [dx, dy] of [[-15, -8], [15, -8], [-15, 8], [15, 8]]) pon(cx + dx, cy + dy, 'farola_velada');
    for (let k = 0; k < 4; k++) pon(x0 + 2 + Math.floor(r() * (W - 4)), y0 + H - 2, 'restos_velador');
    M.biblioteca = { x: cx, y: cy };
    alBarrio(cx, y0 + H + 5);
    M.fijas.push({ tipo: 'velado', x: cx - 6, y: cy }, { tipo: 'velado', x: cx + 6, y: cy }, { tipo: 'guardian_piedra', x: cx, y: y0 + 2 });
  }

  // ---- el Templo de las Placas: cuatro bloques sobre cuatro placas abren la cámara del tesoro
  {
    const [cx, cy] = sitio(515, 588, 0.6, 22);
    const W = 13, H = 11, x0 = cx - 6, y0 = cy - 3;
    sala(x0 - 4, y0 - 12, W + 8, H + 15, MURO.VELADA_VIEJA, SUELO.MOSAICO); // el templo entero
    for (let x = x0 - 4; x < x0 + W + 4; x++) pared(x, y0 - 1, MURO.VELADA_VIEJA); // la pared entre la sala del puzle y la cámara
    for (let s = 0; s < 6; s++) for (let w = -1; w <= 1; w++) suelo(cx + w, y0 + H + 3 + s, SUELO.CALZADA); // la entrada, al sur
    for (let y = y0; y < y0 + H; y++) for (let x = x0; x < x0 + W; x++) suelo(x, y, SUELO.LOSA);
    const placas = [[2, 2], [10, 2], [2, 8], [10, 8]], bloques = [[2, 4], [10, 4], [4, 8], [8, 8]];
    for (const [px, py] of placas) suelo(x0 + px, y0 + py, SUELO.PLACA);
    const bs = bloques.map(([bx, by]) => { const o = ponerObjeto(x0 + bx, y0 + by, 'bloque_empuje'); o.datos = { puzle: M.puzles.length, x0: x0 + bx, y0: y0 + by }; return { x: x0 + bx, y: y0 + by }; });
    suelo(cx, y0 - 1, SUELO.MOSAICO); ponerObjeto(cx, y0 - 1, 'puerta_sellada');
    const pal = ponerObjeto(cx + 3, y0 + H, 'palanca'); pal.datos = { reinicio: M.puzles.length }; // vuelve a poner los bloques
    suelo(cx + 3, y0 + H, SUELO.LOSA);
    const t = ponerObjeto(cx - 3, y0 + H + 1, 'tablilla'); t.datos = { texto: 'placas' };
    const o = ponerObjeto(cx, y0 - 8, 'cofre_velado'); o.datos = { inv: botinBanda(r, 2, 4) };
    for (const dx of [-5, 5]) pon(cx + dx, y0 - 6, 'farola_velada');
    pon(cx - 3, y0 - 10, 'estatua_velador'); pon(cx + 3, y0 - 10, 'estatua_velador');
    M.puzles.push({ tipo: 'placas', placas: placas.map(([px, py]) => ({ x: x0 + px, y: y0 + py })), bloques: bs, puerta: { x: cx, y: y0 - 1 } });
    M.templo2 = { x: cx, y: cy };
    alBarrio(cx, y0 + H + 9);
  }
  yield [0.74, 'La Cripta de los Veladores'];

  // ---- la torre del Archivero: aquí vive el último que recuerda
  {
    const [cx, cy] = sitio(492, 530, 0.3, 10), R = 6;
    for (let y = cy - R - 1; y <= cy + R + 1; y++) for (let x = cx - R - 1; x <= cx + R + 1; x++) {
      const d = Math.hypot(x - cx, y - cy);
      if (d < R - 0.5) suelo(x, y, SUELO.MOSAICO); else if (d < R + 0.7) pared(x, y, MURO.VELADA_VIEJA);
    }
    for (let s = R - 1; s < R + 5; s++) for (let w = -1; w <= 1; w++) suelo(cx + w, cy + s, SUELO.CALZADA);
    pon(cx - 3, cy - 3, 'estante_roto'); pon(cx + 3, cy - 3, 'estante_roto'); tablilla(cx - 3, cy + 1, 'atril'); pon(cx + 3, cy + 1, 'farola_velada');
    M.archivero = { x: cx, y: cy - 1 };
    alBarrio(cx, cy + R + 5);
  }

  // ---- la Cripta de los Veladores: 4×3 salas de muros que no se rompen, palancas y el Centinela al fondo
  {
    const [cx, cy] = sitio(568, 600, 0.5, 34), S = 7, P = 4, C = 4, F = 3, W = C * S + (C + 1) * P, H = F * S + (F + 1) * P;
    const x0 = cx - (W >> 1), y0 = cy - (H >> 1);
    for (let y = y0 - 2; y < y0 + H + 2; y++) for (let x = x0 - 2; x < x0 + W + 2; x++) { pared(x, y, MURO.CRIPTA); if (enBanda(x, y)) { piso[idx(x, y)] = SUELO.CRIPTA; region[idx(x, y)] = REGION.CRIPTA; } }
    const salas = [];
    for (let j = 0; j < F; j++) for (let k = 0; k < C; k++) {
      const sx = x0 + P + k * (S + P), sy = y0 + P + j * (S + P);
      for (let y = sy; y < sy + S; y++) for (let x = sx; x < sx + S; x++) suelo(x, y, SUELO.CRIPTA, REGION.CRIPTA);
      salas.push({ x: sx + (S >> 1), y: sy + (S >> 1), j, k });
    }
    const pasillo = (ax, ay, bx, by) => { for (let x = Math.min(ax, bx); x <= Math.max(ax, bx); x++) for (let w = -1; w <= 1; w++) suelo(x, ay + w, SUELO.CRIPTA, REGION.CRIPTA); for (let y = Math.min(ay, by); y <= Math.max(ay, by); y++) for (let w = -1; w <= 1; w++) suelo(bx + w, y, SUELO.CRIPTA, REGION.CRIPTA); };
    const unidas = new Set([0]), pila = [0], grado = new Array(salas.length).fill(0);
    while (pila.length) {
      const a = pila[pila.length - 1], { j, k } = salas[a];
      const vec = [[j - 1, k], [j + 1, k], [j, k - 1], [j, k + 1]].filter(([jj, kk]) => jj >= 0 && kk >= 0 && jj < F && kk < C && !unidas.has(jj * C + kk));
      if (!vec.length) { pila.pop(); continue; }
      const [jj, kk] = vec[Math.floor(r() * vec.length)], b = jj * C + kk;
      pasillo(salas[a].x, salas[a].y, salas[b].x, salas[b].y); unidas.add(b); pila.push(b); grado[a]++; grado[b]++;
    }
    // la entrada: la sala más cercana al Corazón, con un pasillo hasta la ciudad
    const haciaC = Math.atan2(CENTRO - cy, CENTRO - cx), c = Math.cos(haciaC), sn = Math.sin(haciaC);
    const [dx, dy] = Math.abs(c) > Math.abs(sn) ? [Math.sign(c), 0] : [0, Math.sign(sn)];
    const entrada = salas.reduce((a, b) => (Math.hypot(b.x - CENTRO, b.y - CENTRO) < Math.hypot(a.x - CENTRO, a.y - CENTRO) ? b : a));
    for (let s = 0; s < S + P + 14; s++) for (let w = -1; w <= 1; w++) { const x = entrada.x + dx * s + (dy ? w : 0), y = entrada.y + dy * s + (dx ? w : 0); if (s < (S >> 1) + P + 2) suelo(x, y, SUELO.CRIPTA, REGION.CRIPTA); else suelo(x, y, SUELO.CALZADA); }
    // la sala del Centinela: la más lejana, más grande, tras una puerta sellada que abren las palancas
    const hojas = salas.filter((s, n) => grado[n] === 1 && s !== entrada);
    const ultima = (hojas.length ? hojas : salas).reduce((a, b) => (Math.hypot(b.x - entrada.x, b.y - entrada.y) > Math.hypot(a.x - entrada.x, a.y - entrada.y) ? b : a));
    for (let y = ultima.y - 6; y <= ultima.y + 6; y++) for (let x = ultima.x - 6; x <= ultima.x + 6; x++) suelo(x, y, SUELO.CRIPTA, REGION.CRIPTA);
    M.guaridas.centinela = { x: ultima.x, y: ultima.y };
    // la puerta: en la casilla del pasillo por donde se entra a la sala del Centinela
    let puerta = null;
    for (let s = 7; s < 12 && !puerta; s++) for (const [ddx, ddy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
      const x = ultima.x + ddx * s, y = ultima.y + ddy * s;
      if (enBanda(x, y) && !muro[idx(x, y)] && muro[idx(x - ddx * 1, y - ddy * 1)] === 0) {
        // tapa el pasillo (3 de ancho) con muro de cripta y deja la puerta en medio
        for (let w = -1; w <= 1; w++) pared(x + (ddy ? w : 0), y + (ddx ? w : 0), MURO.CRIPTA);
        suelo(x, y, SUELO.CRIPTA, REGION.CRIPTA); ponerObjeto(x, y, 'puerta_sellada'); puerta = { x, y, dx: ddx, dy: ddy };
        break;
      }
    }
    // cuatro palancas en la sala de la entrada; la clave está escrita en una tablilla
    const clave = [0, 1, 2, 3].map(() => (r() < 0.5 ? 1 : 0));
    if (!clave.some(Boolean)) clave[1] = 1;
    const palancas = [];
    for (let k = 0; k < 4; k++) { const x = entrada.x - 3 + k * 2, y = entrada.y - 3; const o = ponerObjeto(x, y, 'palanca'); o.datos = { puzle: M.puzles.length }; palancas.push({ x, y }); }
    const t = ponerObjeto(entrada.x + 3, entrada.y + 2, 'tablilla'); t.datos = { clave: clave.slice() };
    if (puerta) M.puzles.push({ tipo: 'palancas', palancas, clave, puerta });
    // sarcófagos, velas y restos por las salas
    for (const s of salas) {
      if (s === ultima || s === entrada) continue;
      if (r() < 0.6) { const o = pon(s.x, s.y - 2, 'sarcofago'); if (o) o.datos = { inv: botinBanda(r, 2, 2) }; }
      if (r() < 0.5) pon(s.x - 2, s.y + 2, 'restos_velador');
      if (r() < 0.4) pon(s.x + 2, s.y + 2, 'urna_velada');
      M.fijas.push({ tipo: r() < 0.6 ? 'velador_caido' : 'velado', x: s.x + 1, y: s.y });
    }
    for (const [ddx, ddy] of [[-4, -4], [4, -4], [-4, 4], [4, 4]]) pon(ultima.x + ddx, ultima.y + ddy, 'sarcofago');
    M.cripta = { x: cx, y: cy, entrada: { x: entrada.x, y: entrada.y } };
    alBarrio(entrada.x + dx * (S + P + 13), entrada.y + dy * (S + P + 13));
  }

  // ---- braseros y piedras de paso de la Ciudad
  for (let k = 0; k < 2; k++) { const [x, y] = sitio(490, 600, 0.8, 9); camaraBraseros(X, x, y, REGION.CIUDAD, 3); }
  piedraPaso(X, ...sitio(480, 498, 0.4, 3), 'Paso de la Ciudad');
  piedraPaso(X, ...sitio(585, 605, 0.5, 3), 'Paso de la Cripta');

  // ---- luz: fría, de alma; poca
  for (let k = 0; k < 200; k++) {
    const [x, y] = X.punto(R_SELVA2, R_EXTERIOR - 10, 1.0);
    if (!enMundo(x, y) || muro[idx(x, y)] || region[idx(x, y)] !== REGION.CIUDAD) continue;
    M.soles.push({ x, y, r: 4 + r() * 5, k: 0.24 + r() * 0.12, c: [0.6, 0.72, 1.0] });
    if (r() < 0.3) M.rayos.push({ x, y, a: 0.6 + r() * 0.6, c: [0.7, 0.8, 1] });
  }
  void cavar;
}
