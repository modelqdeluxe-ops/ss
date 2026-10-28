'use strict';
// La Ciénaga de la Podre (banda media del sector de la Selva, de R_SELVA1 a R_SELVA2). Por aquí subió la Podre:
// agua negra, turba, árboles muertos y, en el centro, la Caja Torácica de Ásor, las costillas del dios saliendo del
// fango (donde duerme Grul). Palafitos de grutos sobre el agua, santuarios de la Podre, ruinas de los Veladores
// medio hundidas y el Pozo de la Podre, el agujero por donde sale. El terreno base está en selva.js.

function* generarCienaga(X) {
  const { muro, mena, piso, region } = M, { r, sitio, cavar, pon, tablilla, n3 } = X;
  const enBanda = (x, y) => enMundo(x, y) && region[idx(x, y)] !== REGION.SELLADO;

  // ---- la Caja Torácica de Ásor: la columna del dios de lado, las costillas alrededor y la arena en el centro
  {
    const [cx, cy] = sitio(378, 425, 0.4, 40);
    const a = Math.atan2(cy - CENTRO, cx - CENTRO) + Math.PI / 2, ux = Math.cos(a), uy = Math.sin(a), vx = -uy, vy = ux;
    const local = (x, y) => [(x - cx) * ux + (y - cy) * uy, (x - cx) * vx + (y - cy) * vy]; // s: a lo largo de la columna; t: de lado
    const ARENA = 14;
    for (let y = cy - 44; y <= cy + 44; y++) for (let x = cx - 44; x <= cx + 44; x++) {
      if (!enBanda(x, y)) continue;
      const [s, t] = local(x, y), i = idx(x, y), e = (s / 40) ** 2 + (t / 28) ** 2;
      if (e > 1) continue;
      region[i] = REGION.CIENAGA; mena[i] = 0; muro[i] = 0;
      const d = Math.hypot(s, t) + (n3(x / 3, y / 3) - 0.5) * 3;
      piso[i] = d < ARENA - 3 ? SUELO.PODRE : d < ARENA + 1 ? SUELO.LODO : fbm(n3, x / 7, y / 7) > 0.62 ? SUELO.AGUA_NEGRA : SUELO.LODO;
      // la columna: vértebras grandes con hueco entre ellas, fuera de la arena
      const sv = ((s % 6) + 6) % 6;
      if (Math.abs(t) < 2.2 && sv < 4 && Math.abs(s) > ARENA - 1 && Math.abs(s) < 38) muro[i] = MURO.HUESO_ASOR;
      // las costillas: arcos que salen de la columna hacia los dos lados y se curvan hacia la cola
      for (const s0 of [-30, -22, -14, -6, 6, 14, 22, 30]) {
        const tt = Math.abs(t), sc = s0 + Math.sign(s0) * (tt / 26) ** 2 * 7;
        if (tt > 3 && tt < 25 && Math.abs(s - sc) < 1.25 && Math.hypot(s, t) > ARENA + 0.5) muro[i] = MURO.HUESO_ASOR;
      }
    }
    M.guaridas.grul = { x: cx, y: cy };
    M.soles.push({ x: cx, y: cy, r: ARENA, k: 0.32, c: [0.9, 0.35, 0.75] });
    for (let k = 0; k < 6; k++) { const aa = (k / 6) * Math.PI * 2, x = Math.round(cx + Math.cos(aa) * (ARENA - 1.5)), y = Math.round(cy + Math.sin(aa) * (ARENA - 1.5)); if (X.libre(x, y)) ponerObjeto(x, y, 'bulto_podre'); }
    for (let k = 0; k < 5; k++) { const aa = r() * Math.PI * 2, d = ARENA + 4 + r() * 12; const x = Math.round(cx + Math.cos(aa) * d), y = Math.round(cy + Math.sin(aa) * d); pon(x, y, 'restos_velador'); }
    M.costillas = { x: cx, y: cy };
  }
  yield [0.52, 'Las costillas de Ásor'];

  // ---- palafitos de los grutos del fango: plataformas de tablas sobre el agua negra
  for (let k = 0; k < 3; k++) {
    const [cx, cy] = sitio(345, 455, 0.8, 18);
    for (let y = cy - 17; y <= cy + 17; y++) for (let x = cx - 17; x <= cx + 17; x++) {
      if (!enBanda(x, y)) continue;
      const i = idx(x, y), d = Math.hypot(x - cx, y - cy) + (n3(x / 3, y / 3) - 0.5) * 3;
      if (d < 13) { muro[i] = 0; mena[i] = 0; piso[i] = SUELO.AGUA_NEGRA; region[i] = REGION.CIENAGA; }
      else if (d < 16) { muro[i] = 0; mena[i] = 0; piso[i] = SUELO.LODO; region[i] = REGION.CIENAGA; }
    }
    const tablas = (x0, y0, w, h) => { for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) if (enBanda(x, y)) { const i = idx(x, y); muro[i] = 0; piso[i] = SUELO.TABLAS; } };
    tablas(cx - 4, cy - 4, 9, 9);
    const chozas = [];
    for (let h = 0; h < 3; h++) {
      const aa = (h / 3) * Math.PI * 2 + r(), hx = Math.round(cx + Math.cos(aa) * 9), hy = Math.round(cy + Math.sin(aa) * 8);
      tablas(Math.min(cx, hx), cy, Math.abs(hx - cx) + 1, 2); tablas(hx, Math.min(cy, hy), 2, Math.abs(hy - cy) + 1);
      const x0 = hx - 2, y0 = hy - 2, w = 5, hh = 4;
      for (let y = y0 - 1; y <= y0 + hh; y++) for (let x = x0 - 1; x <= x0 + w; x++) {
        if (!enBanda(x, y)) continue;
        const i = idx(x, y), borde = x === x0 - 1 || y === y0 - 1 || x === x0 + w || y === y0 + hh;
        muro[i] = borde ? MURO.RAIZ_MUERTA : 0; piso[i] = SUELO.TABLAS;
      }
      muro[idx(hx, y0 + hh)] = 0; muro[idx(hx, y0 - 1)] = 0;
      chozas.push({ x0, y0, w, h: hh });
    }
    for (let p = 0; p < 2; p++) { // pasarelas a la orilla
      const aa = r() * Math.PI * 2;
      for (let s = 4; s < 17; s++) { const x = Math.round(cx + Math.cos(aa) * s), y = Math.round(cy + Math.sin(aa) * s); tablas(x, y, 2, 2); }
    }
    ponerObjeto(cx, cy - 1, 'idolo_podre');
    for (const [dx, dy] of [[-4, -4], [4, -4], [-4, 4], [4, 4]]) pon(cx + dx, cy + dy, 'farol_pantano');
    for (const c of chozas) {
      const o = pon(c.x0, c.y0, 'cofre_selva'); if (o) o.datos = { inv: botinBanda(r, 1, 1) };
      pon(c.x0 + c.w - 1, c.y0, 'caja_madera');
      if (r() < 0.5) pon(c.x0 + c.w - 1, c.y0 + c.h - 1, 'restos_velador');
    }
    M.fijas.push({ tipo: 'gruto_lancero', x: cx - 2, y: cy + 2 }, { tipo: 'gruto_lancero', x: cx + 2, y: cy + 2 }, { tipo: 'gruto_cazador', x: cx, y: cy - 3 });
    M.soles.push({ x: cx, y: cy, r: 9, k: 0.26, c: [0.6, 1, 0.6] });
  }

  // ---- santuarios de la Podre: aquí los grutos rezan de verdad
  for (let k = 0; k < 5; k++) { const [x, y] = sitio(340, 465, 0.85, 8); santuarioPodre(X, x, y, 6 + Math.floor(r() * 2), REGION.CIENAGA); }

  // ---- ruinas de los Veladores medio hundidas: lo que hubo antes de la Muralla
  for (let k = 0; k < 7; k++) {
    const [cx, cy] = sitio(338, 465, 0.85, 9), w = 9 + Math.floor(r() * 4), h = 7 + Math.floor(r() * 3), x0 = cx - (w >> 1), y0 = cy - (h >> 1);
    for (let y = y0 - 1; y <= y0 + h; y++) for (let x = x0 - 1; x <= x0 + w; x++) {
      if (!enBanda(x, y)) continue;
      const i = idx(x, y), borde = x === x0 - 1 || y === y0 - 1 || x === x0 + w || y === y0 + h;
      region[i] = REGION.CIENAGA; mena[i] = 0;
      if (borde) muro[i] = hash2(x, y, 61) < 0.6 ? MURO.VELADA_VIEJA : 0;
      else { muro[i] = 0; piso[i] = fbm(n3, x / 4, y / 4) > 0.6 ? SUELO.AGUA_NEGRA : SUELO.CALZADA; }
    }
    cavar(cx, y0 + h + 1, 2.2, SUELO.LODO);
    tablilla(cx, cy, r() < 0.5 ? 'tablilla' : 'atril');
    pon(x0 + 1, y0 + 1, 'urna_velada'); pon(x0 + w - 2, y0 + 1, 'urna_velada'); pon(x0 + 1, y0 + h - 2, 'restos_velador');
    if (r() < 0.4) { const o = pon(x0 + w - 2, y0 + h - 2, 'cofre_ruinas'); if (o) o.datos = { inv: botinBanda(r, 1, 2) }; }
  }
  yield [0.58, 'El Pozo de la Podre'];

  // ---- el Pozo de la Podre: el agujero por donde sale. Costra, bultos, brazos y lo que guarda
  {
    const [cx, cy] = sitio(430, 458, 0.6, 20), R = 16;
    for (let y = cy - R - 4; y <= cy + R + 4; y++) for (let x = cx - R - 4; x <= cx + R + 4; x++) {
      if (!enBanda(x, y)) continue;
      const i = idx(x, y), d = Math.hypot(x - cx, y - cy) + (n3(x / 3, y / 3) - 0.5) * 4;
      if (d < 3.4) { muro[i] = 0; mena[i] = 0; piso[i] = SUELO.AGUA_NEGRA; region[i] = REGION.CIENAGA; }
      else if (d < R) { muro[i] = 0; mena[i] = 0; piso[i] = SUELO.PODRE; region[i] = REGION.CIENAGA; }
      else if (d < R + 3) { muro[i] = hash2(x, y, 71) < 0.85 ? MURO.COSTRA : 0; mena[i] = 0; piso[i] = SUELO.PODRE; region[i] = REGION.CIENAGA; }
    }
    for (let b = 0; b < 3; b++) { const aa = (b / 3) * Math.PI * 2 + r(); for (let s = R - 2; s < R + 6; s++) cavar(cx + Math.cos(aa) * s, cy + Math.sin(aa) * s, 1.6, SUELO.PODRE); }
    for (let k = 0; k < 10; k++) { const aa = (k / 10) * Math.PI * 2, x = Math.round(cx + Math.cos(aa) * 5.5), y = Math.round(cy + Math.sin(aa) * 5.5); if (X.libre(x, y)) ponerObjeto(x, y, 'bulto_podre'); }
    for (let k = 0; k < 6; k++) { const aa = (k / 6) * Math.PI * 2 + 0.5; M.fijas.push({ tipo: 'tentaculo_moho', x: Math.round(cx + Math.cos(aa) * 10), y: Math.round(cy + Math.sin(aa) * 10) }); }
    for (let k = 0; k < 5; k++) { const aa = r() * Math.PI * 2, d = 7 + r() * 7; pon(Math.round(cx + Math.cos(aa) * d), Math.round(cy + Math.sin(aa) * d), 'capullo_podre'); }
    const o = ponerObjeto(cx, cy + 7, 'cofre_velado'); o.datos = { inv: botinBanda(r, 1, 4) };
    M.pozo = { x: cx, y: cy };
    M.soles.push({ x: cx, y: cy, r: R, k: 0.26, c: [1, 0.25, 0.7] });
    M.rayos.push({ x: cx, y: cy, a: 1.2, c: [1, 0.3, 0.75], ancho: 2 });
  }

  // ---- la cámara de los braseros de la Ciénaga y sus piedras de paso
  { const [x, y] = sitio(350, 455, 0.75, 9); camaraBraseros(X, x, y, REGION.CIENAGA, 2); }
  piedraPaso(X, ...sitio(340, 360, 0.5, 3), 'Paso del Fango');
  piedraPaso(X, ...sitio(440, 462, 0.5, 3), 'Paso del Pozo');

  // ---- luz: poca, morada y enferma
  for (let k = 0; k < 180; k++) {
    const [x, y] = X.punto(R_SELVA1, R_SELVA2, 1.0);
    if (!enMundo(x, y) || muro[idx(x, y)] || region[idx(x, y)] !== REGION.CIENAGA) continue;
    M.soles.push({ x, y, r: 4 + r() * 4, k: 0.22 + r() * 0.12, c: [0.78, 0.62, 0.95] });
    if (r() < 0.25) M.rayos.push({ x, y, a: 0.6 + r() * 0.5, c: [0.8, 0.7, 1] });
  }
}
