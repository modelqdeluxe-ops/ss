'use strict';
// Arte de la fase 2 (la Selva Esmeralda): suelos y paredes de la Selva, el metal carmesí, árboles, plantas,
// rocas con mena, muebles, altares, la mazmorra del moho, criaturas, los titanes Tamar y Hiedra, mascotas,
// mercaderes, proyectiles y los iconos de todo. Mismas herramientas que arte.js.

// ---------------------------------------------------------------- paletas (se registran antes de construir)

Object.assign(RAMPA, {
  hierba: ['#0e1a0a', '#1a2e12', '#28441a', '#3a5e24', '#527a32', '#6e9a42'],
  espino: ['#1a0e08', '#2e1a0e', '#4a2c16', '#6a4020', '#8a5a2e', '#a8743e'],
  musgosa: ['#121a18', '#1e2c28', '#2e423a', '#40584c', '#587260', '#76907a'],
  moho: ['#071414', '#0e2424', '#163636', '#1e4a48', '#2a625e', '#3a807a'],
  sello: ['#0a0810', '#14101c', '#1e1a2a', '#2a2438', '#363048', '#443c5a'],
  selva_madera: ['#1e1408', '#3a2810', '#5a4018', '#7a5824', '#9a7232', '#b88e44'],
});
MENA_COL.carmesi = ['#4a0810', '#8a1420', '#c8283a', '#ff5a6a', '#ffd0d0'];
const TIERRA_S = ['#1e140c', '#2e2014', '#3e2c1c', '#4e3a26', '#644c32'];
const HOJA_S = ['#14280e', '#1e3e14', '#2e5a1e', '#427a2a', '#5e9a3a', '#84c050'];
const BRILLO_MOHO = ['#2ab0a0', '#5ae0c0', '#b0fff0'];

// ---------------------------------------------------------------- paredes de la Selva

MURO_ARTE.hierba = {
  arriba(L, R, r, v) { // hierba espesa vista desde arriba: matas oscuras, briznas claras y alguna flor
    const n = ruidoCiclico(1200 + v * 7, 4);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const f = n(x, y); L.px(x, y, f < 0.33 ? R[2] : f > 0.68 ? R[4] : R[3]); }
    for (let k = 0; k < 22; k++) { const x = entre(r, 0, 15), y = entre(r, 0, 15); L.pxc(x, y, R[4]); L.pxc(x, y - 1, R[5]); L.pxc(x + 1, y + 1, R[2]); }
    if (v === 1) { const x = entre(r, 3, 11), y = entre(r, 3, 11); L.px(x, y, '#f0e060'); L.px(x + 3, y + 2, '#e070a0'); }
  },
  cara(L, R, r, v) { // tierra oscura con raíces y un fleco de hierba que cuelga
    const n = ruidoCiclico(1300 + v, 4, 16);
    for (let y = 0; y < 12; y++) for (let x = 0; x < 16; x++) { const f = n(x, y * 1.3); L.px(x, y, f < 0.35 ? TIERRA_S[1] : f > 0.7 ? TIERRA_S[3] : TIERRA_S[2]); }
    for (let k = 0; k < 4; k++) { const x = entre(r, 1, 13), y = entre(r, 5, 10); L.px(x, y, TIERRA_S[4]); L.px(x + 1, y + 1, TIERRA_S[0]); }
    for (let x = 0; x < 16; x++) { const largo = 1 + Math.floor(r() * 4); for (let y = 0; y < largo; y++) L.px(x, y, y === largo - 1 ? R[2] : y === 0 ? R[4] : R[3]); }
    if (v === 1) { let x = entre(r, 4, 11); for (let y = 3; y < 12; y++) { L.px(x, y, '#6a4a2a'); if (r() < 0.3) x += entre(r, -1, 1); } }
  },
};
MURO_ARTE.espino = {
  arriba(L, R, r) { // vetas de madera con nudos y pinchos claros
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) L.px(x, y, (x + Math.round(Math.sin(y * 0.6) * 1.5)) % 4 === 0 ? R[2] : (x + y) % 7 === 0 ? R[4] : R[3]);
    for (let k = 0; k < 2; k++) { const x = entre(r, 3, 12), y = entre(r, 3, 12); L.elipse(x, y, 1.6, 1.2, R[1]); L.px(x, y, R[2]); }
    for (let k = 0; k < 5; k++) { const x = entre(r, 0, 15), y = entre(r, 0, 15); L.pxc(x, y, '#e8d8b0'); L.pxc(x + 1, y + 1, R[1]); }
  },
  cara(L, R, r) { // corteza vertical con espinas que asoman
    for (let x = 0; x < 16; x++) for (let y = 0; y < 12; y++) L.px(x, y, x % 4 === 0 ? R[2] : x % 4 === 3 ? R[4] : R[3]);
    for (let k = 0; k < 5; k++) { const x = entre(r, 1, 14), y = entre(r, 1, 9); L.px(x, y, '#f0e4c0'); L.px(x, y + 1, '#c8b890'); L.px(x - 1, y + 1, R[1]); }
  },
};
MURO_ARTE.musgosa = {
  arriba(L, R, r) {
    const pts = Array.from({ length: 6 }, () => [r() * 16, r() * 16, r()]);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      let d1 = 99, d2 = 99, c = null;
      for (const p of pts) for (const [ox, oy] of [[0, 0], [16, 0], [-16, 0], [0, 16], [0, -16]]) { const d = Math.hypot(x + 0.5 - p[0] - ox, y + 0.5 - p[1] - oy); if (d < d1) { d2 = d1; d1 = d; c = p; } else if (d < d2) d2 = d; }
      L.px(x, y, d2 - d1 < 1 ? R[1] : c[2] < 0.5 ? R[3] : R[4]);
    }
    const n = ruidoCiclico(1400, 4);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) if (n(x, y) > 0.62) L.px(x, y, n(x, y) > 0.75 ? HOJA_S[4] : HOJA_S[3]);
  },
  cara(L, R, r, v) {
    for (const [y0, h, off] of [[0, 6, v * 3], [6, 6, 5 + v * 2]]) for (let x0 = -16 + off; x0 < 16; x0 += 8) {
      L.rect(x0, y0, 8, h, R[3]); L.hl(x0, y0, 8, R[4]); L.vl(x0, y0, h, R[4]); L.hl(x0, y0 + h - 1, 8, R[1]); L.vl(x0 + 7, y0, h, R[1]);
    }
    for (let x = 0; x < 16; x++) { const largo = Math.floor(r() * 4); for (let y = 0; y < largo; y++) L.px(x, y, y === largo - 1 ? HOJA_S[2] : HOJA_S[3]); }
  },
};
MURO_ARTE.moho = {
  arriba(L, R, r) { // moho vivo: venas oscuras y puntos que brillan
    const n = ruidoCiclico(1500, 4);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const f = n(x, y); L.px(x, y, Math.abs(f - 0.5) < 0.04 ? R[1] : f > 0.6 ? R[4] : R[3]); }
    for (let k = 0; k < 4; k++) { const x = entre(r, 1, 14), y = entre(r, 1, 14); L.px(x, y, BRILLO_MOHO[1]); if (r() < 0.5) L.px(x + 1, y, BRILLO_MOHO[0]); }
  },
  cara(L, R, r) {
    for (let x = 0; x < 16; x++) for (let y = 0; y < 12; y++) { const b = Math.sin(x * 1.1 + y * 0.3) + Math.sin(y * 0.9); L.px(x, y, b > 1 ? R[4] : b > -0.5 ? R[3] : R[2]); }
    for (let k = 0; k < 4; k++) { const x = entre(r, 1, 14), y = entre(r, 1, 10); L.px(x, y, BRILLO_MOHO[1]); L.px(x, y + 1, BRILLO_MOHO[0]); }
  },
};
MURO_ARTE.sello = {
  arriba(L, R, r, v) { // losas antiguas con runas que apenas se ven
    L.rect(0, 0, 16, 16, R[2]);
    for (const [x0, y0] of [[0, 0], [8, 0], [0, 8], [8, 8]]) { L.rect(x0 + 1, y0 + 1, 6, 6, R[3]); L.hl(x0 + 1, y0 + 1, 6, R[4]); L.hl(x0 + 1, y0 + 6, 6, R[1]); }
    const runa = '#5a4a8a';
    if (v === 0) { L.vl(4, 2, 4, runa); L.hl(3, 3, 3, runa); } else { L.linea(10, 10, 13, 13, runa); L.linea(13, 10, 10, 13, runa); }
    void r;
  },
  cara(L, R, r, v) {
    for (let y0 = 0; y0 < 12; y0 += 6) for (let x0 = (y0 / 6) % 2 ? -4 : 0; x0 < 16; x0 += 8) { L.rect(x0, y0, 8, 6, R[3]); L.hl(x0, y0, 8, R[4]); L.hl(x0, y0 + 5, 8, R[1]); L.vl(x0 + 7, y0, 6, R[1]); }
    if (v === 1) { const c = '#7a6ab0'; L.vl(7, 2, 7, c); L.hl(5, 4, 5, c); L.px(5, 7, c); L.px(9, 7, c); }
    void r;
  },
};
MURO_ARTE.selva_madera = {
  arriba(L, R, r) { for (let y = 0; y < 16; y += 4) { L.rect(0, y, 16, 4, R[3]); L.hl(0, y, 16, R[4]); L.hl(0, y + 3, 16, R[1]); const n = entre(r, 2, 12); L.hl(n, y + 1, 3, R[2]); L.px((n + 6) % 16, y + 2, HOJA_S[3]); } },
  cara(L, R, r, v) {
    for (let x0 = 0; x0 < 16; x0 += 4) { L.rect(x0, 0, 4, 12, R[3]); L.vl(x0, 0, 12, R[4]); L.vl(x0 + 3, 0, 12, R[1]); L.px(x0 + 2, 2 + ((x0 + v) % 6), R[2]); }
    L.hl(0, 2, 16, R[1]); L.hl(0, 9, 16, R[1]);
    for (let x = 0; x < 16; x += 3) if (r() < 0.5) { L.px(x, 0, HOJA_S[3]); L.px(x + 1, 1, HOJA_S[2]); }
  },
};

// ---------------------------------------------------------------- suelos y adornos

function suelosSelva() {
  for (let v = 0; v < 4; v++) {
    const r = azar(6100 + v);
    // hierba de la Selva
    const H = ['#24461a', '#2e5420', '#3a6428', '#4a7a32', '#5e9040', '#7aaa52'];
    const hi = new Lienzo(16, 16), nh = ruidoCiclico(6200 + v, 4);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const f = nh(x, y); hi.px(x, y, f < 0.3 ? H[1] : f > 0.7 ? H[3] : H[2]); }
    for (let k = 0; k < 30; k++) { const x = entre(r, 0, 15), y = entre(r, 0, 15); hi.pxc(x, y, H[4]); hi.pxc(x, y + 1, H[3]); if (r() < 0.25) hi.pxc(x, y - 1, H[5]); }
    for (let k = 0; k < 5; k++) hi.pxc(entre(r, 0, 15), entre(r, 0, 15), H[0]);
    if (v === 2) { const x = entre(r, 2, 12), y = entre(r, 2, 12); hi.px(x, y, '#ffe680'); hi.px(x + 4, y + 3, '#ffffff'); }
    def(`s_hierba_${v}`, hi);
    // musgo: blando y abultado
    const Mu = ['#1a3a24', '#22482c', '#2e5a36', '#3c6e42', '#4e8450', '#68a060'];
    const mu = new Lienzo(16, 16), nm = ruidoCiclico(6300 + v, 5);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const f = nm(x, y); mu.px(x, y, f < 0.28 ? Mu[1] : f < 0.5 ? Mu[2] : f < 0.72 ? Mu[3] : Mu[4]); }
    for (let k = 0; k < 8; k++) { const x = entre(r, 0, 15), y = entre(r, 0, 15); mu.pxc(x, y, Mu[5]); mu.pxc(x + 1, y + 1, Mu[1]); }
    def(`s_musgo_${v}`, mu);
    // tierra de la Selva con hojarasca
    const ts = new Lienzo(16, 16).rect(0, 0, 16, 16, TIERRA_S[2]), nt = ruidoCiclico(6400 + v, 4);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const f = nt(x, y); if (f < 0.32) ts.px(x, y, TIERRA_S[1]); else if (f > 0.7) ts.px(x, y, TIERRA_S[3]); }
    for (let k = 0; k < 6; k++) { const x = entre(r, 0, 14), y = entre(r, 0, 14), c = ['#8a5a2a', '#a8742e', '#5e7a2a', '#7a3a1a'][k % 4]; ts.pxc(x, y, c); ts.pxc(x + 1, y, c); ts.pxc(x, y + 1, TIERRA_S[0]); }
    def(`s_tierra_selva_${v}`, ts);
    // flores: hierba llena de flores pequeñas
    const fl = hi.copia(), C = ['#f0e060', '#ffffff', '#e070a0', '#8ac0ff', '#ff9a50'];
    for (let k = 0; k < 9; k++) { const x = entre(r, 0, 15), y = entre(r, 0, 15), c = C[entre(r, 0, 4)]; fl.pxc(x, y, c); fl.pxc(x + 1, y, c); fl.pxc(x, y + 1, c); fl.pxc(x + 1, y + 1, H[1]); }
    def(`s_flores_${v}`, fl);
    // moho de la mazmorra
    const Mo = ['#071414', '#0c2020', '#123030', '#1a4040', '#245656'];
    const mo = new Lienzo(16, 16), no = ruidoCiclico(6500 + v, 4);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const f = no(x, y); mo.px(x, y, Math.abs(f - 0.5) < 0.035 ? Mo[0] : f > 0.62 ? Mo[3] : f < 0.3 ? Mo[1] : Mo[2]); }
    for (let k = 0; k < 3; k++) { const x = entre(r, 1, 14), y = entre(r, 1, 14); mo.px(x, y, BRILLO_MOHO[1]); mo.px(x + 1, y, BRILLO_MOHO[0]); }
    def(`s_moho_${v}`, mo);
    // losas del templo: piedra clara gastada con musgo entre las juntas
    const Te = ['#3e3e32', '#5a5a4a', '#6e6e5c', '#828270', '#9a9a86', '#b4b49e'];
    const te = new Lienzo(16, 16).rect(0, 0, 16, 16, Te[1]);
    for (const [x0, y0, w, h] of [[0, 0, 9, 7], [9, 0, 7, 7], [0, 7, 6, 9], [6, 7, 10, 9]]) { te.rect(x0 + 1, y0 + 1, w - 1, h - 1, Te[3]); te.hl(x0 + 1, y0 + 1, w - 1, Te[4]); te.vl(x0 + 1, y0 + 1, h - 1, Te[4]); te.px(x0 + 2, y0 + 2, Te[5]); }
    for (let k = 0; k < 4; k++) { const x = entre(r, 0, 15), y = r() < 0.5 ? 7 : entre(r, 0, 15); te.pxc(x, y, HOJA_S[3]); te.pxc(x + 1, y, HOJA_S[2]); }
    if (v === 3) te.linea(10, 9, 14, 14, Te[1]);
    def(`s_templo_${v}`, te);
    // suelo envenenado: morado y verde, con burbujas
    const Ve = ['#1e1428', '#2a1a38', '#3a2448', '#4a2e5a', '#5a8a3a', '#a060e0'];
    const ve = new Lienzo(16, 16), nv = ruidoCiclico(6600 + v, 4);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const f = nv(x, y); ve.px(x, y, f < 0.3 ? Ve[1] : f > 0.72 ? Ve[4] : f > 0.6 ? Ve[3] : Ve[2]); }
    for (let k = 0; k < 3; k++) { const x = entre(r, 1, 13), y = entre(r, 1, 13); ve.px(x, y, Ve[5]); ve.px(x + 1, y, '#e0c0ff'); ve.px(x, y + 1, Ve[0]); }
    def(`s_veneno_${v}`, ve);
    // tablas de selva
    const Ta = ['#3a2810', '#5a4018', '#7a5824', '#9a7232', '#b88e44'];
    const ta = new Lienzo(16, 16).rect(0, 0, 16, 16, Ta[2]);
    for (let x = 0; x < 16; x += 4) { ta.vl(x, 0, 16, Ta[3]); ta.vl(x + 3, 0, 16, Ta[0]); const j = ((x / 4) * 5 + v * 3) % 16; ta.hl(x, j, 4, Ta[0]); ta.px(x + 1, (j + 1) % 16, '#c0c0c8'); ta.px(x + 2, entre(r, 0, 15), Ta[1]); }
    def(`s_tablas_${v}`, ta);
    // alfombra verde con dibujo dorado
    const av = new Lienzo(16, 16).rect(0, 0, 16, 16, '#1e4a2e');
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) if ((x + y + v * 2) % 8 === 0 || (x - y + 16) % 8 === 0) av.px(x, y, '#2a5e3a');
    av.px(4, 4, '#d0a040'); av.px(12, 12, '#d0a040'); av.px(12, 4, '#c8a040'); av.px(4, 12, '#c8a040'); av.px(8, 8, '#f0d060');
    def(`s_alfombra_verde_${v}`, av);
  }
  // adornos de pie de la Selva
  for (let k = 0; k < 2; k++) {
    const L = new Lienzo(13, 14), rr = azar(810 + k);
    for (let h = 0; h < 8; h++) { let x = 1.5 + h * 1.4 + rr(), y = 13; const alto = 7 + Math.floor(rr() * 6), lado = rr() < 0.5 ? -1 : 1; for (let s = 0; s < alto; s++) { L.px(x, y, HOJA_S[Math.min(5, 1 + Math.floor((s / alto) * 5))]); y--; if (s > alto / 2 && rr() < 0.45) x += lado; } }
    def(`dc_hierba_alta_${k}`, L.contorno('#08140a', 0.7));
  }
  const FLS = [['#ffe060', '#c89020'], ['#ff80b0', '#c04070'], ['#90c8ff', '#4070c0']];
  FLS.forEach(([c, C], k) => {
    const L = new Lienzo(9, 11);
    L.vl(4, 4, 7, HOJA_S[2]); L.px(3, 7, HOJA_S[3]); L.px(5, 8, HOJA_S[3]);
    for (const [dx, dy] of [[0, -2], [2, 0], [0, 2], [-2, 0]]) L.elipse(4.5 + dx, 2.5 + dy, 1.4, 1.4, c);
    L.px(4, 2, C); L.px(4, 3, '#fff8d0');
    def(`dc_flor_selva_${k}`, L.contorno('#101808', 0.7));
  });
  def('dc_helechito', (() => { const L = new Lienzo(13, 10); for (const [a, l] of [[-2.4, 6], [-1.9, 7], [-1.3, 6], [-0.7, 5]]) for (const s of [1, -1]) { const ang = s > 0 ? a : -Math.PI - a; for (let k = 0; k < l; k++) { const x = 6.5 + Math.cos(ang) * k, y = 9 + Math.sin(ang) * k * 0.9; L.px(x, y, k > l - 2 ? HOJA_S[5] : HOJA_S[3]); if (k % 2) L.px(x, y + 1, HOJA_S[2]); } } return L.contorno('#08140a', 0.7); })());
  def('dc_seta_moho', (() => { const L = new Lienzo(9, 8); L.rect(4, 4, 1, 4, '#a0d0c8'); L.esfera(4.5, 3.5, 3, 2.2, ['#0e3a3a', '#2ab0a0', '#5ae0c0', '#b0fff0'], { brillo: '#ffffff', donde: (x, y) => y <= 4 }); L.esfera(1.5, 6, 1.4, 1, ['#2ab0a0', '#5ae0c0', '#b0fff0']); return L.contorno('#041010', 0.7); })());
  def('ad_hojas', rej({ a: '#8a5a2a', b: '#a8742e', c: '#5e7a2a', d: '#7a3a1a' }, `
    .a....c..
    aab..ccc.
    .b..d.c..
    ...ddd...
    ....d....`));
  def('ad_moho', rej({ a: '#1a4040', b: '#2a6060', c: '#5ae0c0' }, `
    .aab..
    abbbaa
    .bcba.
    ..aa..`));
}

// ---------------------------------------------------------------- construir todo

function construirFase2() {
  suelosSelva();
  if (typeof objetosSelva === 'function') objetosSelva();
  if (typeof criaturasSelva === 'function') criaturasSelva();
  if (typeof titanesArte === 'function') titanesArte();
  if (typeof iconosSelva === 'function') iconosSelva();
}

// ---------------------------------------------------------------- objetos de la Selva

function objetosSelva() {
  const TR = ['#2a1a0e', '#4a2e16', '#6a4420', '#8a5a2e', '#a8743e'];
  // árboles: tronco con raíces y una copa grande de bolas de hojas (el de luna, azul y con frutos que brillan)
  const arbol = (v, luna) => {
    const L = new Lienzo(34, 48), r = azar(900 + v * 13 + (luna ? 5 : 0));
    L.rect(14, 24, 6, 21, TR[1]); L.vl(14, 24, 21, TR[2]); L.vl(15, 24, 21, TR[3]); L.vl(19, 24, 21, TR[0]);
    for (let k = 0; k < 5; k++) L.px(16 + (k % 2), 28 + k * 3, TR[0]);
    L.rect(11, 43, 12, 3, TR[1]); L.hl(11, 43, 12, TR[2]); L.linea(13, 44, 8, 47, TR[1]); L.linea(20, 44, 25, 47, TR[1]); L.linea(16, 45, 16, 47, TR[2]);
    const H = luna ? ['#0c2434', '#163e52', '#22607a', '#3a8aa0', '#62b8cc', '#a0e8f0'] : ['#0e2c10', '#18461a', '#246422', '#36842e', '#52a63e', '#7ec85a'];
    const bolas = v === 0 ? [[17, 19, 14, 11], [8, 23, 8, 7], [26, 23, 8, 7], [11, 11, 9, 8], [23, 11, 9, 8], [17, 6, 9, 6]] : [[17, 20, 13, 10], [9, 16, 9, 8], [25, 16, 9, 8], [17, 9, 11, 8], [12, 25, 7, 5], [23, 25, 7, 5]];
    for (const [x, y, rx, ry] of bolas) L.esfera(x, y, rx, ry, H, { amb: 0.32 });
    for (let k = 0; k < 70; k++) { const x = entre(r, 2, 31), y = entre(r, 1, 31); if (!L.get(x, y)) continue; const c = L.get(x, y); L.px(x, y, c === H[4] || c === H[5] ? H[3] : c === H[1] ? H[0] : H[Math.max(1, H.indexOf(c) - 1)] || c); if (r() < 0.3 && L.get(x, y - 1)) L.px(x, y - 1, H[Math.min(5, H.indexOf(c) + 1)] || c); }
    if (luna) for (const [x, y] of [[9, 20], [25, 18], [17, 28], [14, 10], [22, 9]]) { L.px(x, y, '#e0ffff'); L.px(x + 1, y, '#a0f0ff'); L.px(x, y + 1, '#60c0e0'); }
    else for (let k = 0; k < 3; k++) { const x = entre(r, 6, 27), y = entre(r, 12, 27); if (L.get(x, y)) { L.px(x, y, '#e8503a'); L.px(x + 1, y, '#ff8a5a'); } }
    const C = L.contorno('#08140a', 0.7);
    return { C, luz: luna ? C.solo(['#e0ffff', '#a0f0ff', '#60c0e0']) : null };
  };
  for (let v = 0; v < 2; v++) { const a = arbol(v, false); def(v ? 'arbol_b' : 'arbol', a.C); }
  { const a = arbol(0, true); def('arbol_luna', a.C); def('arbol_luna_luz', a.luz); }
  // helecho grande
  def('helecho', (() => { const L = new Lienzo(20, 16); for (const [a, l] of [[-2.7, 9], [-2.3, 10], [-1.9, 9], [-1.5, 7], [-1.2, 5]]) for (const s of [1, -1]) { const ang = s > 0 ? a : -Math.PI - a; for (let k = 0; k < l; k++) { const x = 10 + Math.cos(ang) * k, y = 15 + Math.sin(ang) * k * 0.95; L.px(x, y, k > l - 2 ? HOJA_S[5] : HOJA_S[3]); L.px(x, y + 1, HOJA_S[2]); if (k % 2 && k > 1) { L.px(x + Math.cos(ang + 1.6) * 1.5, y + Math.sin(ang + 1.6) * 1.5, HOJA_S[4]); L.px(x + Math.cos(ang - 1.6) * 1.5, y + Math.sin(ang - 1.6) * 1.5, HOJA_S[3]); } } } return L.contorno('#08140a', 0.7); })());
  // flor del sol (brilla)
  { const L = new Lienzo(14, 18); L.vl(7, 7, 11, HOJA_S[2]); L.vl(8, 8, 10, HOJA_S[1]); L.esfera(4, 13, 3, 1.4, HOJA_S.slice(1)); L.esfera(10.5, 11, 3, 1.4, HOJA_S.slice(1)); for (let k = 0; k < 8; k++) { const a = (k / 8) * Math.PI * 2; L.elipse(7.5 + Math.cos(a) * 3.5, 5 + Math.sin(a) * 3, 1.8, 1.6, k % 2 ? '#ffd040' : '#ffb020'); } L.esfera(7.5, 5, 2.2, 2, ['#8a4a10', '#c87a20', '#ffd060'], { brillo: '#fff8d0' }); const C = L.contorno('#101808', 0.7); def('flor_sol', C); def('flor_sol_luz', C.solo(['#ffd040', '#ffb020', '#ffd060', '#fff8d0'])); }
  // avena silvestre
  def('avena_silvestre', (() => { const L = new Lienzo(14, 18), r = azar(31); for (let k = 0; k < 6; k++) { let x = 2 + k * 2; const alto = 9 + Math.floor(r() * 6); for (let y = 17; y > 17 - alto; y--) L.px(x, y, HOJA_S[3]); L.esfera(x + 0.5, 17 - alto, 1.4, 2.4, ['#8a7a3a', '#c8b060', '#f0e090', '#fff8c0']); } return L.contorno('#141008', 0.7); })());
  // hongo explosivo: naranja con manchas, un poco hinchado
  def('hongo_explosivo', (() => { const L = new Lienzo(14, 14); L.rect(6, 8, 3, 6, '#e8dcc0'); L.vl(8, 8, 6, '#b8a888'); L.esfera(7.5, 7, 6, 5, ['#6a1a08', '#b8401a', '#e86a2a', '#ffa050', '#ffd0a0'], { brillo: '#fff0d0', donde: (x, y) => y < 9 }); for (const [x, y] of [[4, 5], [9, 3], [10, 6], [6, 3]]) L.px(x, y, '#ffe060'); L.px(7, 6, '#2a0a04'); L.px(8, 6, '#2a0a04'); return L.contorno('#1a0804', 0.7); })());
  // panal de avispas (colgado de un tronco seco)
  def('panal', (() => { const L = new Lienzo(16, 22); L.rect(7, 0, 2, 6, TR[1]); for (let k = 0; k < 4; k++) L.esfera(8, 7 + k * 3.4, 6 - Math.abs(k - 1.5) * 1.2, 2.4, ['#6a4010', '#a87020', '#e0a840', '#ffe080']); for (let y = 6; y < 20; y += 3) L.hl(3, y, 10, '#7a4a10'); L.esfera(8, 15, 1.6, 1.8, ['#100804', '#2a1408']); L.px(8, 15, '#000000'); return L.contorno('#140a04', 0.7); })());
  // rocas con mena: un pedrusco grande con pepitas del metal
  for (const [m, ore] of [['cobre', 'cobre'], ['estano', 'estano'], ['hierro', 'hierro'], ['oro', 'oro'], ['carmesi', 'carmesi']]) {
    const L = new Lienzo(20, 16), r = azar(1700 + m.length * 7);
    L.esfera(10, 9, 9.5, 7, ['#1e2228', '#30363e', '#444c56', '#5c6670', '#7a8692'], { brillo: '#a0aab4' });
    L.esfera(5, 11, 4, 3.4, ['#30363e', '#444c56', '#5c6670', '#7a8692']);
    for (let k = 0, n = 0; k < 40 && n < 5; k++) { const x = entre(r, 2, 15), y = entre(r, 2, 11); if (L.get(x, y) && L.get(x + 3, y + 3)) { pepita(L, x, y, MENA_COL[ore], n < 2); n++; } }
    def('roca_' + m, L.contorno('#0a0c10', 0.7));
  }
  // caja de madera y vasija de flores
  def('caja_madera', (() => { const L = new Lienzo(14, 13); L.rect(0, 0, 14, 13, '#8a5a30'); L.rect(1, 1, 12, 11, '#a8743e'); L.linea(1, 1, 12, 11, '#6e4222'); L.linea(12, 1, 1, 11, '#6e4222'); L.hl(0, 0, 14, '#c8945a'); L.hl(0, 12, 14, '#5a3418'); L.vl(0, 0, 13, '#6e4222'); L.vl(13, 0, 13, '#5a3418'); for (const [x, y] of [[1, 1], [12, 1], [1, 11], [12, 11]]) L.px(x, y, '#c0c0c8'); L.px(4, 4, HOJA_S[3]); L.px(3, 5, HOJA_S[4]); return L.contorno(); })());
  def('vasija_flor', (() => { const L = new Lienzo(13, 17); L.esfera(6.5, 11, 6, 5.5, ['#2a4a2a', '#3e6a3a', '#5a8a4a', '#7aa860', '#a0c880']); L.rect(4, 4, 5, 3, '#3e6a3a'); L.hl(3, 4, 7, '#7aa860'); L.hl(1, 10, 11, '#d8c070'); for (const [x, y, c] of [[3, 2, '#ff80b0'], [6, 0, '#ffe060'], [9, 2, '#90c8ff']]) { L.vl(x, y + 1, 3, HOJA_S[3]); L.px(x, y, c); L.px(x - 1, y, c); L.px(x + 1, y, c); L.px(x, y - 1, c); } return L.contorno(); })());
  // cofres: el del moho (antiguo, con moho que brilla) y el de selva
  const cofre2 = (mad, metal, extra) => { const L = new Lienzo(16, 14); L.rect(0, 0, 16, 6, mad[2]); L.hl(0, 0, 16, mad[3]); L.hl(0, 5, 16, mad[0]); L.rect(0, 6, 16, 8, mad[1]); L.hl(0, 6, 16, mad[2]); L.hl(0, 13, 16, mad[0]); L.vl(0, 0, 14, metal[0]); L.vl(15, 0, 14, metal[0]); L.vl(1, 0, 14, metal[1]); L.vl(14, 0, 14, metal[1]); L.hl(0, 5, 16, metal[1]); L.rect(6, 4, 4, 4, metal[2]); L.px(7, 6, '#1a1010'); L.px(8, 6, '#1a1010'); if (extra) extra(L); return L.contorno(); };
  def('cofre_moho', cofre2(['#0e2424', '#163636', '#1e4a48', '#2a625e'], ['#3a3a44', '#5ae0c0', '#b0fff0'], (L) => { for (const [x, y] of [[3, 2], [11, 9], [4, 10], [12, 3]]) L.px(x, y, BRILLO_MOHO[1]); }));
  def('cofre_selva', cofre2(['#3a2810', '#5a4018', '#7a5824', '#9a7232'], ['#2a4a1a', '#4a7a2a', '#c8e070'], (L) => { L.px(3, 1, HOJA_S[4]); L.px(4, 2, HOJA_S[3]); L.px(12, 1, HOJA_S[4]); }));
  // altar del cielo (donde se llama a Tamar): piedra clara con alas talladas
  const altarCielo = (on) => {
    const L = new Lienzo(24, 22), P = ['#3e3e32', '#5a5a4a', '#6e6e5c', '#828270', '#9a9a86', '#c8c8b4'];
    L.rect(2, 14, 20, 8, P[2]); L.hl(2, 14, 20, P[4]); L.hl(2, 21, 20, P[0]); L.rect(5, 8, 14, 6, P[3]); L.hl(5, 8, 14, P[5]);
    for (const s of [-1, 1]) for (let k = 0; k < 5; k++) L.linea(12 + s * 3, 9 + k, 12 + s * (8 + k), 2 + k * 2, k % 2 ? P[4] : P[3]);
    L.esfera(12, 6, 3, 3, on ? ['#6a6a8a', '#a0b0ff', '#e0e8ff', '#ffffff'] : ['#2a2a32', '#3e3e4a', '#5a5a6a', '#7a7a8a'], { brillo: on ? '#ffffff' : '#9a9aaa' });
    for (let k = 0; k < 3; k++) { L.px(6 + k * 6, 17, HOJA_S[3]); L.px(7 + k * 6, 18, HOJA_S[2]); }
    return L.contorno();
  };
  def('altar_cielo', altarCielo(false)); { const C = altarCielo(true); def('altar_cielo_on', C); def('altar_cielo_on_luz', C.solo(['#a0b0ff', '#e0e8ff', '#ffffff'])); }
  // altares de los titanes junto al Corazón (pedestal con el hueco de la gema)
  for (const [t, col] of [['tamar', ['#8a8aa0', '#d0d8ff', '#ffffff']], ['hiedra', ['#2a8a5a', '#5ae0a0', '#d0ffe0']]]) {
    const base = (on) => {
      const L = new Lienzo(16, 22), P = RAMPA.ruina;
      L.rect(1, 16, 14, 6, P[3]); L.hl(1, 16, 14, P[5]); L.hl(1, 21, 14, P[1]); L.rect(4, 6, 8, 10, P[2]); L.vl(4, 6, 10, P[4]); L.vl(11, 6, 10, P[1]); L.rect(2, 3, 12, 4, P[3]); L.hl(2, 3, 12, P[5]);
      if (t === 'tamar') for (const s of [-1, 1]) L.linea(8 + s * 4, 8, 8 + s * 7, 2, P[4]); else for (const s of [-1, 1]) { L.linea(8 + s * 3, 14, 8 + s * 6, 6, '#3a6a3a'); L.px(8 + s * 6, 5, '#5a9a4a'); }
      if (on) L.esfera(8, 1.5, 2.6, 2.6, col, { brillo: '#ffffff' }); else { L.rect(6, 1, 4, 2, P[0]); }
      return L.contorno();
    };
    def(`altar_${t}`, base(false)); const C = base(true); def(`altar_${t}_on`, C); def(`altar_${t}_on_luz`, C.solo(col.concat(['#ffffff'])));
  }
  // tablilla de piedra con letras
  def('tablilla', (() => { const L = new Lienzo(14, 18), P = ['#4a4a3e', '#6a6a5a', '#8a8a76', '#a8a890']; L.rect(1, 2, 12, 14, P[2]); L.esfera(7, 3, 6, 2.5, [P[1], P[2], P[3]], { donde: (x, y) => y <= 3 }); L.vl(1, 2, 14, P[3]); L.vl(12, 2, 14, P[1]); for (let y = 5; y < 14; y += 2) for (let x = 3; x < 11; x++) if ((x * 7 + y * 3) % 5 !== 0) L.px(x, y, P[0]); L.rect(0, 15, 14, 3, P[1]); L.hl(0, 15, 14, P[2]); L.px(2, 6, HOJA_S[3]); L.px(11, 12, HOJA_S[3]); return L.contorno(); })());
  // tienda de campaña, hoguera, pozo y columna con enredadera
  def('tienda', (() => { const L = new Lienzo(24, 20); for (let y = 0; y < 18; y++) { const w = Math.round((y / 17) * 11); L.hl(12 - w, y, w * 2, y % 4 === 0 ? '#a8502a' : '#c8703a'); L.px(12 - w, y, '#8a3a1a'); } L.rect(9, 9, 6, 9, '#2a1408'); L.linea(12, 0, 12, 17, '#7a3a1a'); L.hl(0, 17, 24, '#6a3a1a'); L.px(12, 0, '#e8d8b0'); return L.contorno(); })());
  for (let f = 0; f < 3; f++) {
    const L = new Lienzo(14, 14);
    L.elipse(7, 12, 6, 2, '#3a3030'); for (const [x, y] of [[2, 11], [5, 12], [9, 12], [12, 11]]) L.esfera(x, y, 1.6, 1.2, ['#3a3436', '#5a5456', '#7a7476']);
    L.linea(3, 11, 11, 8, TR[1]); L.linea(11, 11, 3, 8, TR[2]);
    const o = [0, 1, -1][f];
    L.elipse(7 + o * 0.5, 6, 3.4, 4.6, FUEGO.r); L.elipse(7 + o, 6.6, 2.4, 3.4, FUEGO.o); L.elipse(7, 7.4, 1.4, 2, FUEGO.y); L.px(7, 8, FUEGO.w);
    const C = L.contorno('#100806', 0.7); def(`hoguera_${f}`, C); def(`hoguera_${f}_luz`, C.solo([FUEGO.r, FUEGO.o, FUEGO.y, FUEGO.w]));
  }
  def('pozo', (() => { const L = new Lienzo(18, 22), P = ['#3a3e46', '#5a606a', '#7a8290', '#9aa2b0']; L.rect(1, 12, 16, 10, P[1]); for (let y = 12; y < 22; y += 3) for (let x = (y % 2) * 3; x < 16; x += 6) { L.rect(1 + x, y, 5, 2, P[2]); L.hl(1 + x, y, 5, P[3]); } L.elipse(9, 12, 7.5, 2.5, '#0a1420'); L.elipse(9, 12.5, 6, 1.6, '#1a3a5a'); L.rect(1, 2, 2, 10, TR[1]); L.rect(15, 2, 2, 10, TR[1]); L.rect(0, 0, 18, 3, TR[2]); L.hl(0, 0, 18, TR[3]); L.vl(9, 3, 6, '#a89870'); L.rect(8, 8, 3, 2, TR[2]); return L.contorno(); })());
  def('columna_selva', (() => { const P = ['#3e3e32', '#5a5a4a', '#6e6e5c', '#828270', '#9a9a86', '#b4b49e'], L = new Lienzo(12, 30); L.rect(0, 0, 12, 4, P[4]); L.hl(0, 0, 12, P[5]); L.rect(2, 4, 8, 22, P[3]); L.vl(2, 4, 22, P[4]); L.vl(9, 4, 22, P[1]); L.vl(6, 4, 22, P[2]); L.rect(0, 26, 12, 4, P[3]); L.hl(0, 26, 12, P[4]); let x = 3; for (let y = 2; y < 28; y++) { L.px(x, y, HOJA_S[3]); if (y % 3 === 0) { L.px(x + 1, y, HOJA_S[4]); x = 3 + ((y / 3) % 2) * 4; } } return L.contorno(); })());
  // incubadora: vidrio con calor naranja; con huevo dentro; lista (brilla más)
  const incub = (huevo, lista) => {
    const L = new Lienzo(14, 18), M2 = ['#3a3e46', '#6a7280', '#9aa2b0', '#c8d0dc'];
    L.rect(1, 13, 12, 5, M2[1]); L.hl(1, 13, 12, M2[2]); L.hl(1, 17, 12, M2[0]); L.px(3, 15, '#ff6040'); L.px(5, 15, lista ? '#60ff80' : '#ffb040');
    L.esfera(7, 7, 6, 6.5, ['#305068', '#4a7898', '#78a8c8', '#c0e0f0'], { amb: 0.6 });
    L.esfera(7, 9.5, 4, 3, ['#8a4010', '#e08030', '#ffc060'], { donde: (x, y) => y > 8 });
    if (huevo) L.esfera(7, 8, 2.6, 3.2, lista ? ['#a0a060', '#e0e090', '#ffffc0', '#ffffff'] : ['#8a7a5a', '#c8b890', '#f0e8c8', '#ffffff'], { brillo: '#ffffff' });
    L.px(4, 3, '#ffffff'); L.px(4, 4, '#e0f0ff');
    return L.contorno();
  };
  for (const [n, h, l] of [['incubadora', false, false], ['incubadora_on', true, false], ['incubadora_lista', true, true]]) { const C = incub(h, l); def(n, C); def(n + '_luz', C.solo(['#e08030', '#ffc060', '#ffb040', '#60ff80', '#ffffc0'])); }
  // ídolos de los mercaderes
  def('idolo_barbas', (() => { const L = new Lienzo(12, 16); L.rect(2, 12, 8, 4, TR[2]); L.hl(2, 12, 8, TR[3]); L.esfera(6, 7, 5, 5.5, ['#2f6e35', '#56a83b', '#86d64f', '#c0f080'], { brillo: '#effed8' }); L.px(4, 6, TINTA); L.px(8, 6, TINTA); L.hl(4, 9, 5, '#f6ecda'); L.px(5, 10, '#f6ecda'); L.px(7, 10, '#f6ecda'); return L.contorno(); })());
  def('idolo_pesca', (() => { const L = new Lienzo(12, 16); L.rect(2, 12, 8, 4, TR[2]); L.hl(2, 12, 8, TR[3]); L.esfera(6, 7, 5, 4, ['#1a4a6a', '#3a7aa0', '#6ab0ff', '#c0e8ff'], { brillo: '#ffffff' }); L.px(8, 6, TINTA); L.linea(1, 7, 0, 4, '#3a7aa0'); L.linea(1, 7, 0, 10, '#3a7aa0'); L.linea(9, 2, 9, 0, '#c8c8d0'); return L.contorno(); })());
  def('idolo_misterioso', (() => { const L = new Lienzo(12, 17); L.rect(2, 13, 8, 4, '#2a1a3a'); L.hl(2, 13, 8, '#4a3a5a'); L.esfera(6, 7, 5, 6, ['#140a1e', '#24143a', '#3a2258', '#563478', '#764a9a'], { brillo: '#a080c0' }); L.rect(4, 6, 4, 3, '#0a0610'); L.px(4, 7, '#e080ff'); L.px(7, 7, '#e080ff'); return L.contorno(); })());
  // mesa y yunque carmesí, farol carmesí
  const CR = ['#5a0e16', '#c0283a', '#ff7a7a'];
  { const L = new Lienzo(18, 17); L.rect(0, 0, 18, 9, CR[1]); for (let x = 0; x < 18; x += 6) { L.vl(x, 0, 9, CR[0]); L.vl(x + 1, 0, 9, CR[2]); } L.hl(0, 0, 18, CR[2]); L.rect(0, 9, 18, 4, '#5a4018'); L.hl(0, 9, 18, '#9a7232'); L.hl(0, 12, 18, '#3a2810'); L.rect(1, 13, 3, 4, CR[0]); L.rect(14, 13, 3, 4, CR[0]); L.rect(3, 2, 5, 3, '#2a2a30'); L.hl(3, 2, 5, '#5a5a60'); L.esfera(13, 3, 2, 1.6, ['#2a4a1a', '#4a7a2a', '#8ac050']); def('mesa_carmesi', L.contorno()); }
  def('yunque_carmesi', rej({ i: CR[0], I: CR[1], j: CR[2], J: '#ffd0d0', ...MADERA }, `
    JJJJJJJJJJJJJJJ.
    jjjjjjjjjjjjjjjI
    IIIIIIIIIIIIIIi.
    .iIIIIIIIIIIii..
    .....jIIIi......
    ....jIIIIii.....
    ...eWWwwwwWWd...
    ...WwwwwwwwWd...
    ...ddddddddddd..`).contorno());
  { const L = new Lienzo(10, 22); L.rect(4, 8, 2, 13, '#3a1018'); L.vl(4, 8, 13, '#5a1a24'); L.rect(2, 20, 6, 2, '#2a0a10'); L.rect(1, 0, 8, 2, CR[0]); L.hl(1, 0, 8, CR[2]); L.rect(2, 2, 6, 6, '#ff8a6a'); L.rect(3, 3, 4, 4, '#ffe0c0'); L.vl(2, 2, 6, CR[0]); L.vl(7, 2, 6, CR[0]); L.rect(1, 8, 8, 1, CR[1]); const C = L.contorno(); def('farol_carmesi', C); def('farol_carmesi_luz', C.solo(['#ff8a6a', '#ffe0c0'])); }
  // muebles de selva
  const MS = ['#3a2810', '#5a4018', '#7a5824', '#9a7232', '#b88e44'];
  def('silla_selva', (() => { const L = new Lienzo(10, 16); L.rect(1, 0, 8, 8, MS[2]); L.hl(1, 0, 8, MS[4]); L.vl(1, 0, 8, MS[3]); L.rect(2, 2, 6, 4, MS[1]); L.rect(0, 8, 10, 3, MS[3]); L.hl(0, 8, 10, MS[4]); L.rect(1, 11, 2, 5, MS[1]); L.rect(7, 11, 2, 5, MS[1]); L.px(4, 3, HOJA_S[3]); return L.contorno(); })());
  def('mesa_selva', (() => { const L = new Lienzo(18, 14); L.rect(0, 0, 18, 7, MS[3]); L.hl(0, 0, 18, MS[4]); L.hl(0, 6, 18, MS[1]); L.rect(1, 7, 3, 7, MS[1]); L.rect(14, 7, 3, 7, MS[1]); L.esfera(6, 2.5, 2, 1.5, ['#c0c0c8', '#e8e8f0', '#ffffff']); L.esfera(12, 2.5, 1.6, 1.2, ['#e8503a', '#ff8a5a', '#ffd0a0']); L.px(9, 2, HOJA_S[3]); return L.contorno(); })());
  def('estanteria', (() => { const L = new Lienzo(16, 24); L.rect(0, 0, 16, 24, MS[1]); L.vl(0, 0, 24, MS[2]); L.vl(15, 0, 24, MS[0]); L.hl(0, 0, 16, MS[3]); for (let y = 7; y < 24; y += 8) L.hl(1, y, 14, MS[3]); const LIB = ['#c84858', '#4a7aa0', '#e0a840', '#5a9a4a', '#8a5aa0']; for (let fila = 0; fila < 3; fila++) for (let k = 0; k < 6; k++) { const x = 2 + k * 2, h = 4 + ((k + fila) % 3); L.rect(x, fila * 8 + 7 - h, 2, h, LIB[(k + fila * 2) % 5]); L.px(x, fila * 8 + 7 - h, '#ffffff'); } L.esfera(12, 12, 1.5, 2, ['#2a4a6a', '#6ab0ff', '#c0e8ff']); return L.contorno(); })());
  { const L = new Lienzo(12, 26); L.rect(5, 8, 2, 16, MS[1]); L.vl(5, 8, 16, MS[3]); L.rect(2, 24, 8, 2, MS[0]); for (let y = 0; y < 8; y++) { const w = 2 + Math.round(y * 0.6); L.hl(6 - w, y, w * 2, y < 2 ? '#e8d8a0' : '#f8e8b0'); } L.rect(4, 5, 4, 2, '#fff8d0'); const C = L.contorno(); def('lampara_pie', C); def('lampara_pie_luz', C.solo(['#f8e8b0', '#fff8d0', '#e8d8a0'])); }
  def('valla', (() => { const L = new Lienzo(16, 14); for (const x of [1, 7, 13]) { L.rect(x, 1, 2, 13, MS[2]); L.vl(x, 1, 13, MS[3]); L.px(x, 0, MS[4]); } for (const y of [4, 9]) { L.hl(0, y, 16, MS[3]); L.hl(0, y + 1, 16, MS[1]); } L.px(4, 3, HOJA_S[4]); L.px(10, 8, HOJA_S[3]); return L.contorno(); })());
  def('banderin', (() => { const L = new Lienzo(14, 26); L.vl(2, 0, 26, MS[1]); L.vl(3, 0, 26, MS[3]); L.rect(4, 2, 9, 7, '#c84858'); L.hl(4, 2, 9, '#e86878'); for (let x = 4; x < 13; x += 3) { L.px(x + 1, 9, '#c84858'); } L.esfera(8, 5, 1.6, 1.6, ['#d0a040', '#f0d060', '#fff0a0']); return L.contorno(); })());
  def('maceta_grande', (() => { const L = new Lienzo(16, 20); L.rect(3, 12, 10, 8, '#c86a4a'); L.hl(2, 12, 12, '#e08a6a'); L.vl(12, 13, 7, '#8a4a30'); for (const [a, l] of [[-2.6, 8], [-2.1, 9], [-1.57, 9], [-1.0, 9], [-0.5, 8]]) for (let k = 0; k < l; k++) { const x = 8 + Math.cos(a) * k, y = 12 + Math.sin(a) * k; L.px(x, y, k > l - 3 ? HOJA_S[5] : HOJA_S[3]); L.px(x + 1, y, HOJA_S[2]); } return L.contorno(); })());
  // cultivos nuevos: avena y melón de luna (4 etapas)
  for (let e = 0; e < 4; e++) {
    const A = new Lienzo(14, 16);
    A.elipse(7, 14.5, 5, 1.5, '#3a2416');
    const alto = [2, 5, 9, 12][e];
    for (const x of [4, 7, 10]) { A.vl(x, 14 - alto, alto, e === 3 ? '#a89040' : HOJA_S[3]); if (e >= 2) A.esfera(x + 0.5, 14 - alto, 1.4, 2.4, e === 3 ? ['#8a7a3a', '#c8b060', '#f0e090', '#fff8c0'] : HOJA_S.slice(2)); }
    def(`cultivo_avena_${e}`, A.contorno('#0a1206', 0.7));
    const Me = new Lienzo(16, 16);
    Me.elipse(8, 14.5, 6, 1.5, '#3a2416');
    if (e === 0) { Me.px(8, 13, HOJA_S[3]); Me.px(7, 12, HOJA_S[4]); Me.px(9, 12, HOJA_S[4]); }
    else { for (const [x, y] of [[3, 12], [12, 12], [5, 9], [11, 9]].slice(0, e + 1)) Me.esfera(x, y, 2.4, 1.4, HOJA_S.slice(1)); Me.linea(2, 13, 14, 13, HOJA_S[2]); }
    if (e === 2) Me.esfera(8, 11, 2.4, 2, ['#2a5a7a', '#4a8ab0', '#80c0e0']);
    if (e === 3) Me.esfera(8, 10, 4.5, 3.8, ['#1a4a6a', '#3a8ab0', '#70d0f0', '#c8f8ff'], { brillo: '#ffffff' });
    const C = Me.contorno('#0a1206', 0.7);
    def(`cultivo_melon_${e}`, C);
    if (e === 3) def('cultivo_melon_3_luz', C.solo(['#70d0f0', '#c8f8ff', '#ffffff']));
  }
  // charcos de veneno y de esporas
  def('charco_veneno', new Lienzo(20, 10).elipse(10, 5, 9.5, 4.5, '#6a2aa0d0').elipse(10, 5, 7.5, 3.2, '#a060e0e0').elipse(7, 3.5, 2, 1, '#e8c0ff'));
  def('charco_espora', new Lienzo(20, 10).elipse(10, 5, 9.5, 4.5, '#1a6a5ab0').elipse(10, 5, 7, 3, '#2ab0a0c0').elipse(7, 3.5, 1.6, 1, '#b0fff0').elipse(13, 6, 1.2, 0.8, '#b0fff0'));
  // bomba, explosión y proyectiles nuevos
  def('bomba', (() => { const L = new Lienzo(10, 11); L.esfera(5, 6.5, 4.4, 4.2, ['#14141a', '#24242e', '#3a3a48', '#5a5a6e'], { brillo: '#9a9ab0' }); L.rect(4, 1, 2, 2, '#6a6a7a'); L.px(5, 0, '#ffd040'); L.px(6, 0, '#ff7020'); return L.contorno(); })());
  for (let f = 0; f < 4; f++) {
    const L = new Lienzo(32, 28), rad = [6, 11, 13, 12][f];
    if (f < 3) L.elipse(16, 16, rad, rad * 0.85, f === 0 ? '#ffffff' : '#ff7020');
    if (f > 0) L.elipse(16, 16, rad * 0.75, rad * 0.65, f === 3 ? '#5a4a3a' : '#ffd040');
    if (f === 1) L.elipse(16, 16, rad * 0.4, rad * 0.35, '#ffffff');
    if (f >= 2) for (let k = 0; k < 8; k++) { const a = (k / 8) * Math.PI * 2; L.elipse(16 + Math.cos(a) * rad * 0.8, 16 + Math.sin(a) * rad * 0.7, 2.5, 2, f === 3 ? '#3a3030' : '#ff9a40'); }
    def(`explosion_${f}`, L);
  }
  def('semilla_p', new Lienzo(7, 7).esfera(3.5, 3.5, 3, 3, ['#2a4a1a', '#4a8a2a', '#8ac050', '#d0f090'], { brillo: '#ffffff' }).contorno('#0a1404', 0.6));
  def('espora_p', new Lienzo(7, 7).esfera(3.5, 3.5, 3, 3, ['#4a2a6a', '#8a4ac0', '#c080ff', '#f0d8ff'], { brillo: '#ffffff' }));
  def('pluma_h', rej({ a: '#ffffff', b: '#d8e0f0', c: '#9aa8c0', d: '#6a7890' }, `
    ....abbbb...
    .aabbbcccd..
    abbbbccccddd
    .ccccdddd...`));
  def('pluma_v', rej({ a: '#ffffff', b: '#d8e0f0', c: '#9aa8c0', d: '#6a7890' }, `
    .a..
    abb.
    abbc
    bbcc
    bccd
    .ccd
    .cd.
    ..d.
    ..d.`));
  for (let f = 0; f < 3; f++) {
    const L = new Lienzo(16, 22);
    for (let y = 0; y < 22; y++) { const w = 1 + Math.round(((22 - y) / 22) * 6), o = Math.round(Math.sin(y * 0.6 + f * 2) * 2); L.hl(8 - w + o, y, w * 2, y % 3 === f % 3 ? '#ffffff' : '#c8d8e8'); L.px(8 - w + o, y, '#8aa0b8'); }
    def(`tornado_${f}`, L);
  }
  for (let f = 0; f < 2; f++) {
    const L = new Lienzo(16, 48), r = azar(55 + f);
    let x = 8;
    for (let y = 0; y < 48; y++) { L.px(x, y, '#ffffff'); L.px(x + 1, y, '#e0f0ff'); L.px(x - 1, y, '#a0c0ff'); if (r() < 0.3) x += entre(r, -1, 1); x = Math.max(3, Math.min(12, x)); }
    def(`rayo_cielo_${f}`, L);
  }
}

// ---------------------------------------------------------------- criaturas, mascotas y mercaderes

function criaturasSelva() {
  // babosa venenosa (morada, con burbujas)
  const MOR = ['#2a0a3a', '#4a1a6a', '#6a2aa0', '#a060e0', '#d0a0ff', '#f0e0ff'];
  const bab = (rx, ry, alto) => {
    const L = new Lienzo(18, 14), cx = 9, base = 13, cy = base - ry;
    L.esfera(cx, cy, rx, ry, MOR, { brillo: '#ffffff', amb: 0.35, donde: (x, y) => y < base });
    const ey = Math.round(cy + ry * 0.05) - alto;
    for (const ex of [Math.round(cx - rx * 0.4) - 1, Math.round(cx + rx * 0.3)]) { L.vl(ex, ey, 2, TINTA); L.px(ex, ey, '#ffffff'); L.vl(ex + 1, ey, 2, TINTA); }
    L.px(Math.round(cx - rx * 0.6), Math.round(cy - ry * 0.5), '#e0c0ff'); L.px(Math.round(cx + rx * 0.5), Math.round(cy - ry * 0.2), '#e0c0ff');
    L.hl(Math.round(cx) - 1, ey + 3, 3, MOR[0]);
    return L.contorno();
  };
  def('babosa_veneno_0', bab(7, 5, 0)); def('babosa_veneno_1', bab(8.2, 4, 0)); def('babosa_veneno_2', bab(6, 6.5, 1));
  // grutos de la Selva: jardinero (sombrero de paja y bolsa de semillas) y cazador (capucha roja y arco)
  const PIEL = ['#1e2a18', '#2e4024', '#425a32', '#5a7642', '#7a9658'];
  const grutoS = (tipo, f) => {
    const L = new Lienzo(16, 20), cx = 8, bob = f === 1 ? 1 : 0;
    const piernas = f === 0 ? [-2, 2] : f === 1 ? [-1, 1] : [-3, 3];
    for (const dx of piernas) L.rect(cx + dx - 1, 16, 2, 4, PIEL[1]);
    L.esfera(cx, 12 - bob, 4.5, 4.2, PIEL, { amb: 0.3 });
    L.rect(cx - 4, 12 - bob, 8, 3, tipo === 'jardinero' ? '#7a5a2a' : '#7a2a1e'); L.hl(cx - 4, 12 - bob, 8, tipo === 'jardinero' ? '#a8804a' : '#a8402a');
    const hy = 6 - bob;
    L.esfera(cx, hy, 4, 3.6, PIEL, { brillo: PIEL[4] });
    for (const lado of [-1, 1]) { L.linea(cx + lado * 3, hy - 1, cx + lado * 6, hy - 3, PIEL[3]); L.px(cx + lado * 6, hy - 3, PIEL[4]); }
    L.px(cx - 2, hy, '#ffd040'); L.px(cx + 1, hy, '#ffd040');
    if (tipo === 'jardinero') {
      L.elipse(cx, hy - 3, 6.5, 1.6, '#c8a040'); L.esfera(cx, hy - 4.5, 3, 2, ['#8a6a20', '#c8a040', '#e8c860']); L.hl(cx - 3, hy - 3, 6, '#5a9a3a');
      L.esfera(cx + 5, 12 - bob, 2.6, 2.6, ['#5a3a18', '#8a5a2a', '#b07a3a']); L.px(cx + 5, 10 - bob, HOJA_S[4]);
      if (f === 2) { L.esfera(cx - 5, 5, 1.6, 1.6, ['#2a4a1a', '#4a8a2a', '#8ac050']); L.linea(cx - 4, 8, cx - 5, 6, PIEL[3]); }
    } else {
      L.esfera(cx, hy - 1.5, 4.6, 3.4, ['#4a0e0a', '#7a1e14', '#a83020', '#c84a2a'], { donde: (x, y) => y < hy });
      const tenso = f === 2;
      L.linea(cx + 6, 4, cx + (tenso ? 8 : 7), 10, '#8a5a36'); L.linea(cx + (tenso ? 8 : 7), 10, cx + 6, 16, '#8a5a36'); L.linea(cx + 6, 4, cx + (tenso ? 3 : 6), 10, '#e8e0d0'); L.linea(cx + (tenso ? 3 : 6), 10, cx + 6, 16, '#e8e0d0');
      if (tenso) L.hl(cx + 3, 10, 6, '#c8c0b0');
      L.rect(cx - 6, 9 - bob, 2, 6, '#6a4a2a'); L.px(cx - 6, 8 - bob, '#e8e0d0'); L.px(cx - 5, 8 - bob, '#e8e0d0');
    }
    return L.contorno();
  };
  for (const t of ['jardinero', 'cazador']) for (let f = 0; f < 4; f++) def(`gruto_${t}_${f}`, grutoS(t, Math.min(f, 2)));
  // planta trampa: capullo cerrado / abierta / se prepara / muerde
  const trampa = (f) => {
    const L = new Lienzo(22, 22), V = ['#14280e', '#1e4a18', '#2e6a24', '#46902e', '#6ab048'], R = ['#4a0a1a', '#8a1a3a', '#c83a5a', '#ff7a9a'];
    for (const [a, l] of [[-2.8, 9], [-0.4, 9], [-2.2, 7], [-1, 7]]) for (let k = 0; k < l; k++) L.px(11 + Math.cos(a) * k, 20 + Math.sin(a) * k * 0.5, k > l - 3 ? V[4] : V[3]);
    L.rect(10, 12, 2, 8, V[2]); L.vl(11, 12, 8, V[1]);
    if (f === 0) { L.esfera(11, 9, 4.5, 5.5, V, { brillo: V[4] }); L.linea(11, 4, 11, 14, V[1]); L.px(9, 7, R[2]); L.px(13, 9, R[2]); }
    else {
      const ab = f === 1 ? 4 : f === 2 ? 6 : 2, cy = f === 3 ? 10 : 8;
      L.esfera(11, cy - ab / 2, 7, 4, R, { donde: (x, y) => y < cy - ab / 2 + 1 }); L.esfera(11, cy + ab / 2, 7, 4, R, { donde: (x, y) => y >= cy + ab / 2 - 1 });
      L.rect(6, cy - ab / 2, 11, ab, '#2a0a10');
      for (let x = 6; x < 17; x += 2) { L.px(x, cy - ab / 2, '#ffffff'); L.px(x + 1, cy + ab / 2 - 1, '#f0f0e0'); }
      for (const [x, y] of [[7, cy - ab / 2 - 3], [14, cy - ab / 2 - 2], [10, cy + ab / 2 + 2]]) L.px(x, y, '#ffd0e0');
    }
    return L.contorno();
  };
  for (let f = 0; f < 4; f++) def(`planta_trampa_${f}`, trampa(f));
  // avispa esmeralda: cuerpo a rayas, alas que zumban, aguijón
  const avispa = (f) => {
    const L = new Lienzo(16, 13);
    L.esfera(9.5, 7.5, 4, 3, ['#14280e', '#2a5a1a', '#3ad06a', '#a0ff90'], { amb: 0.4 });
    for (const x of [8, 10]) L.vl(x, 5, 5, '#f2d040');
    L.esfera(4.5, 6, 2.6, 2.6, ['#14280e', '#2a5a1a', '#3ad06a', '#a0ff90'], { brillo: '#ffffff' });
    L.px(3, 5, '#ff4040'); L.px(14, 9, '#e8e8f0'); L.px(15, 10, '#c8c8d0');
    const ala = f === 1 ? 2 : 0;
    L.elipse(8, 2.5 + ala, 3, 2 - ala * 0.4, '#d8f8ffb0'); L.elipse(11, 2.5 + ala, 2.6, 1.8 - ala * 0.4, '#c0e8f0b0');
    if (f === 2) { L.px(15, 9, '#ffffff'); L.px(15, 10, '#ffffff'); }
    return L.contorno('#08140a', 0.6);
  };
  for (let f = 0; f < 3; f++) def(`avispa_${f}`, avispa(f));
  // escarabajo de musgo: caparazón con musgo, cuernos y patas
  const escarabajo = (f) => {
    const L = new Lienzo(26, 18), C = ['#14200e', '#22381a', '#30502a', '#46703a', '#5e9048'];
    const pata = f % 2;
    for (const x of [6, 12, 18]) { L.linea(x, 13, x - 2 + pata * 2, 17, '#2a1a10'); L.linea(x + 1, 13, x + 3 - pata * 2, 17, '#2a1a10'); }
    L.esfera(13, 9, 10, 7, C, { brillo: C[4] }); L.vl(13, 3, 12, C[0]);
    const n = ruidoCiclico(77, 3, 26);
    for (let y = 2; y < 13; y++) for (let x = 4; x < 23; x++) if (L.get(x, y) && n(x, y) > 0.62) L.px(x, y, n(x, y) > 0.72 ? HOJA_S[5] : HOJA_S[4]);
    const hx = f === 3 ? 23 : 21;
    L.esfera(hx, 11, 3.4, 3, ['#1a1008', '#3a2414', '#5a3a20', '#7a5030']); L.px(hx + 1, 10, f === 2 ? '#ff4040' : '#ffd040');
    L.linea(hx + 2, 9, hx + 5, 5, '#c8b890'); L.px(hx + 5, 4, '#ffffff');
    return L.contorno();
  };
  for (let f = 0; f < 4; f++) def(`escarabajo_musgo_${f}`, escarabajo(f));
  // tentáculo de moho: agujero / sale y se mece / alzado para golpear
  const tentaculo = (f) => {
    const L = new Lienzo(16, 28);
    L.elipse(8, 25, 7, 2.6, '#041010'); L.elipse(8, 25, 5.5, 1.8, '#0a2020');
    if (f > 0) {
      const alto = f === 3 ? 24 : 16, ladeo = f === 1 ? -2 : f === 2 ? 2 : 0;
      for (let y = 0; y < alto; y++) { const t = y / alto, w = 3.5 - t * 2.2, x = 8 + ladeo * t * t; L.hl(Math.round(x - w), 25 - y, Math.max(1, Math.round(w * 2)), y % 4 === 0 ? '#1e6a60' : '#2a8a7a'); L.px(Math.round(x - w), 25 - y, '#0e3a3a'); if (y % 5 === 2) L.px(Math.round(x + w - 1), 25 - y, BRILLO_MOHO[1]); }
      L.px(Math.round(8 + ladeo), 25 - alto, BRILLO_MOHO[2]);
    } else for (const [x, y] of [[5, 24], [10, 25]]) L.px(x, y, BRILLO_MOHO[0]);
    return L.contorno('#020808', 0.6);
  };
  for (let f = 0; f < 4; f++) def(`tentaculo_moho_${f}`, tentaculo(f));
  // larva de moho
  const larvaM = (f) => {
    const L = new Lienzo(22, 12), C = ['#0e3a3a', '#1e6a60', '#2ab0a0', '#5ae0c0', '#b0fff0'];
    for (let k = 0; k < 4; k++) { const s = (k + f) % 2; L.esfera(4 + k * 4, 6 - s, 3.4, 3.6, C, { amb: 0.4 }); }
    L.esfera(18, 6, 3.6, 3.8, ['#0a2a2a', '#1a5050', '#2a8080', '#5ae0c0'], { brillo: '#ffffff' });
    L.px(19, 5, '#ffffff'); L.px(19, 6, TINTA);
    return L.contorno('#020808', 0.6);
  };
  def('larva_moho_0', larvaM(0)); def('larva_moho_1', larvaM(1));

  // mascotas: Topito, Babosita, Luci y Mohito
  const topito = (f) => {
    const L = new Lienzo(13, 12), P = ['#3a2416', '#5a3a24', '#7a5236', '#9a6e4a', '#c0946a'];
    const pie = f === 1 ? 1 : 0;
    L.rect(3, 9, 2, 3 - pie, P[1]); L.rect(8, 9, 2, 2 + pie, P[1]);
    L.esfera(6.5, 6, 5.5, 4.8, P, { brillo: P[4] });
    L.esfera(10.5, 6.5, 2, 1.6, ['#c06070', '#ff9aa8', '#ffd0d8']);
    L.px(8, 4, TINTA); L.px(5, 4, TINTA); L.px(8, 3, '#ffffff');
    if (f === 2) { L.hl(9, 9, 3, '#ffffff'); }
    L.px(2, 2, P[3]); L.px(4, 1, P[3]);
    return L.contorno();
  };
  for (let f = 0; f < 3; f++) def(`topito_${f}`, topito(f));
  const babosita = (f) => { const L = new Lienzo(12, 10), ry = [3.4, 2.6, 4.2][f], cy = 9 - ry; L.esfera(6, cy, [4.6, 5.4, 4][f], ry, ['#2f6e35', '#56a83b', '#86d64f', '#c0f080'], { brillo: '#ffffff', donde: (x, y) => y < 9 }); L.px(4, Math.round(cy), TINTA); L.px(7, Math.round(cy), TINTA); L.px(4, Math.round(cy) - 1, '#ffffff'); L.px(5, Math.round(cy) + 2, '#ff9aa8'); return L.contorno(); };
  for (let f = 0; f < 3; f++) def(`babosita_${f}`, babosita(f));
  const luci = (f) => { const L = new Lienzo(12, 11); L.elipse(3, 3 + f, 2.6, 1.8 - f * 0.6, '#e0f8ffc0'); L.elipse(9, 3 + f, 2.6, 1.8 - f * 0.6, '#e0f8ffc0'); L.esfera(6, 6, 3, 2.6, ['#4a3a10', '#8a7a20', '#c8b030']); L.esfera(6, 9, 2.4, 1.8, ['#e0c040', '#fff080', '#ffffc0', '#ffffff']); L.px(5, 5, '#ffffff'); L.px(7, 5, '#ffffff'); L.px(4, 2, '#1a1a10'); L.px(8, 2, '#1a1a10'); return L.contorno('#100c04', 0.6); };
  for (let f = 0; f < 2; f++) { const C = luci(f); def(`luci_${f}`, C); def(`luci_${f}_luz`, C.solo(['#e0c040', '#fff080', '#ffffc0', '#ffffff'])); }
  const mohito = (f) => { const L = new Lienzo(12, 13); L.rect(4, 8, 4, 5 - (f === 1 ? 1 : 0), '#a0d0c8'); L.vl(7, 8, 5, '#6aa098'); L.esfera(6, 6, 5.4, 4, ['#0e3a3a', '#2ab0a0', '#5ae0c0', '#b0fff0'], { brillo: '#ffffff', donde: (x, y) => y < 8 }); L.px(4, 9, TINTA); L.px(7, 9, TINTA); if (f === 2) L.esfera(10, 4, 1.4, 1.4, ['#4a2a6a', '#8a4ac0', '#c080ff']); for (const [x, y] of [[3, 4], [8, 3]]) L.px(x, y, '#ffffff'); return L.contorno(); };
  for (let f = 0; f < 3; f++) def(`mohito_${f}`, mohito(f));

  // mercaderes: grutos amables (cuadros 0-1 andando, 2-3 quietos parpadeando)
  const mercader = (tipo, f) => {
    const L = new Lienzo(16, 22), cx = 8, anda = f < 2, bob = anda && f === 1 ? 1 : 0;
    const Pi = ['#3a2a1e', '#5a4632', '#7a6448', '#9a8460', '#c0aa80'];
    const piernas = anda ? (f === 0 ? [-2, 2] : [-1, 1]) : [-2, 2];
    for (const dx of piernas) L.rect(cx + dx - 1, 18, 2, 4, '#3a2a20');
    const ropa = { barbas: ['#2a4a1a', '#3a6a2a', '#5a8a3a', '#86d64f'], pescadora: ['#1a3a5a', '#2a5a8a', '#4a8ac0', '#8ac0ff'], encapuchado: ['#140a1e', '#24143a', '#3a2258', '#563478'] }[tipo];
    L.esfera(cx, 13 - bob, 5, 5, ropa, { amb: 0.3 });
    const hy = 7 - bob;
    L.esfera(cx, hy, 4.2, 3.8, Pi, { brillo: Pi[4] });
    for (const lado of [-1, 1]) { L.linea(cx + lado * 3, hy - 1, cx + lado * 6, hy - 2, Pi[3]); }
    const parpadeo = f === 3;
    if (!parpadeo) { L.px(cx - 2, hy, TINTA); L.px(cx + 1, hy, TINTA); L.px(cx - 2, hy - 1, '#ffffff'); } else { L.hl(cx - 2, hy, 2, TINTA); L.hl(cx + 1, hy, 2, TINTA); }
    if (tipo === 'barbas') { L.esfera(cx, hy + 4, 3.6, 3.4, ['#c8c0b0', '#e8e0d0', '#ffffff'], { donde: (x, y) => y > hy + 1 }); L.linea(cx + 5, 6, cx + 7, 16, '#8a5a36'); L.rect(cx + 5, 4, 4, 2, '#9aa6b8'); L.esfera(cx, hy - 3, 4, 2, ['#5a3a20', '#7a5030', '#a07040']); }
    else if (tipo === 'pescadora') { L.elipse(cx, hy - 3, 6, 1.6, '#e0c060'); L.esfera(cx, hy - 4, 3, 1.6, ['#a08030', '#e0c060', '#fff0a0']); L.linea(cx - 6, 3, cx - 5, 18, '#8a5a36'); L.linea(cx - 6, 3, cx - 9, 10, '#e8e8f0'); L.esfera(cx + 4, 15 - bob, 1.6, 1.2, ['#3a7aa0', '#6ab0ff', '#c0e8ff']); }
    else { L.esfera(cx, hy - 1, 5, 4.4, ['#140a1e', '#24143a', '#3a2258', '#563478'], { donde: (x, y) => y < hy + 1 }); L.rect(cx - 3, hy - 1, 6, 3, '#0a0610'); L.px(cx - 2, hy, '#e080ff'); L.px(cx + 1, hy, '#e080ff'); L.esfera(cx - 5, 14 - bob, 2, 2, ['#5a3a18', '#8a5a2a', '#f2c14e'], { brillo: '#fff0a0' }); }
    return L.contorno();
  };
  for (const t of ['barbas', 'pescadora', 'encapuchado']) for (let f = 0; f < 4; f++) def(`mercader_${t}_${f}`, mercader(t, f));
}

// ---------------------------------------------------------------- los titanes

function titanesArte() {
  // Hiedra Viva: una mata enorme de lianas y hojas con un bulbo morado que mira; flores que brillan
  const HV = ['#0e1e0a', '#18320f', '#244a16', '#346a20', '#4a8a2c', '#68aa3c'];
  const BU = ['#1e0a2a', '#3a1450', '#5a2478', '#8a3aa8', '#b860d8', '#e8a8ff'];
  const hiedra = (f) => {
    const L = new Lienzo(64, 58), r = azar(4400 + f);
    const sway = f === 1 ? 1 : f === 0 ? -1 : 0, alza = f === 2 ? 8 : f === 3 ? -4 : 0;
    // lianas que salen de la base
    for (let k = 0; k < 9; k++) {
      const a = Math.PI + (k / 8) * Math.PI, largo = 18 + (k % 3) * 6 + (f === 2 ? 6 : 0);
      let x = 32, y = 50;
      for (let s = 0; s < largo; s++) { x += Math.cos(a) * 1.2 + Math.sin(s * 0.5 + k) * 0.4 * (sway || 1); y += Math.sin(a) * 0.9 - (f === 2 ? 0.4 : 0); L.px(x, y, s % 5 === 0 ? HV[4] : HV[3]); L.px(x, y + 1, HV[1]); if (s % 6 === 3) L.esfera(x, y - 1, 2, 1.4, HV.slice(2)); }
    }
    // la mata de hojas
    for (const [x, y, rx, ry] of [[32, 44, 22, 12], [18, 38, 11, 9], [46, 38, 11, 9], [32, 34, 16, 10]]) L.esfera(x + sway, y, rx, ry, HV, { amb: 0.3 });
    for (let k = 0; k < 90; k++) { const x = entre(r, 8, 56), y = entre(r, 24, 55); if (L.get(x, y)) L.px(x, y, r() < 0.5 ? HV[2] : HV[4]); }
    // el tallo y el bulbo
    L.rect(29 + sway, 18 - alza, 6, 18 + alza, HV[2]); L.vl(29 + sway, 18 - alza, 18 + alza, HV[4]); L.vl(34 + sway, 18 - alza, 18 + alza, HV[1]);
    const by = 14 - alza, abierto = f === 2 || f === 3;
    L.esfera(32 + sway, by, 12, 10, BU, { brillo: BU[5] });
    for (let k = 0; k < 5; k++) { const a = -Math.PI / 2 + (k - 2) * 0.55; L.linea(32 + sway, by, 32 + sway + Math.round(Math.cos(a) * 12), by + Math.round(Math.sin(a) * 10), BU[1]); }
    if (abierto) { L.esfera(32 + sway, by + 1, 6, 5, ['#2a0a10', '#4a1020', '#c83a5a', '#ff9ab8']); for (let x = 27; x < 38; x += 2) { L.px(x + sway, by - 3, '#ffffff'); L.px(x + sway + 1, by + 5, '#f0f0e0'); } }
    else { L.esfera(32 + sway, by + 1, 4.5, 3.6, ['#e8e0c0', '#ffffff']); L.esfera(32 + sway, by + 1.5, 2.2, 2.4, ['#2a0a10', '#c80a3a', '#ff4060']); L.px(31 + sway, by, '#ffffff'); }
    if (f === 3) { L.hl(26 + sway, by - 8, 12, '#ffd166'); }
    // flores que brillan
    for (const [x, y] of [[12, 34], [52, 34], [22, 46], [42, 46], [32, 28]]) { L.esfera(x + sway, y, 2.4, 2, ['#8a3aa8', '#e070ff', '#ffd0ff']); L.px(x + sway, y, '#ffffff'); }
    return L.contorno('#060a04', 0.7);
  };
  for (let f = 0; f < 4; f++) { const C = hiedra(f); def(`hiedra_${f}`, C); def(`hiedra_${f}_luz`, C.solo(['#e070ff', '#ffd0ff', '#ff4060', '#ff9ab8', BU[5]])); }

  // Tamar: ave titán de plumas blancas y oro, con una gema en la frente
  const PL = ['#3a3e52', '#6a7088', '#9aa2b8', '#c8d0e0', '#eef2fa', '#ffffff'];
  const OR = ['#6a4a08', '#b8861a', '#f2c14e', '#ffe58a'];
  const tamar = (f) => {
    const L = new Lienzo(78, 56);
    const aletea = f === 2 || f === 3, arriba = f === 2, caido = f === 4;
    // alas
    for (const s of [-1, 1]) {
      const bx = 39 + s * 10, by = caido ? 36 : 28;
      for (let k = 0; k < 7; k++) {
        const ang = aletea ? (arriba ? -0.9 : 0.25) + k * (arriba ? 0.12 : 0.08) : 0.9 + k * 0.08;
        const largo = 26 - k * 2.2;
        const ex = bx + s * Math.cos(ang) * largo * (aletea ? 1.4 : 0.55), ey = by + Math.sin(ang) * largo * (aletea ? 0.7 : 1) * (caido ? 0.4 : 1);
        L.linea(bx, by + k * 1.2, ex, ey + k * 1.4, k < 2 ? PL[4] : PL[3]);
        L.linea(bx, by + k * 1.2 + 1, ex, ey + k * 1.4 + 1, PL[2]);
        L.px(ex, ey + k * 1.4, k % 2 ? OR[2] : PL[5]);
      }
    }
    // cuerpo
    const cy = caido ? 40 : 34;
    L.esfera(39, cy, 13, 14, PL, { brillo: PL[5], amb: 0.35 });
    for (let fila = 0; fila < 6; fila++) for (let k = 0; k < 7 - Math.abs(fila - 2); k++) { // plumas como escamas
      const x = 39 - (6 - Math.abs(fila - 2)) * 2 + k * 4 + (fila % 2) * 2, y = cy - 6 + fila * 4;
      if (L.get(x, y) && L.get(x, y + 2)) { L.px(x, y + 1, PL[2]); L.px(x - 1, y, PL[2]); L.px(x + 1, y, PL[2]); }
    }
    L.esfera(39, cy - 3, 3.2, 3.8, OR, { brillo: '#fffbe0' }); L.px(39, cy - 3, '#5a8ae0'); L.px(39, cy - 2, '#c0e0ff');
    L.hl(33, cy - 12, 13, OR[1]); L.hl(34, cy - 11, 11, OR[2]); for (let x = 34; x < 45; x += 3) L.px(x, cy - 10, OR[3]);
    // patas y garras
    if (!aletea || f === 3) for (const s of [-1, 1]) { L.rect(39 + s * 5 - 1, caido ? 50 : 46, 3, caido ? 4 : 8, OR[1]); L.hl(39 + s * 5 - 2, 53, 5, OR[0]); L.px(39 + s * 5 - 3, 54, OR[2]); L.px(39 + s * 5 + 3, 54, OR[2]); }
    // cuello y cabeza
    const hx = 39, hy = caido ? 26 : 14 + (f === 1 ? 1 : 0);
    L.rect(36, hy + 6, 7, caido ? 8 : 14, PL[3]); L.vl(36, hy + 6, caido ? 8 : 14, PL[4]);
    L.esfera(hx, hy, 7, 6.5, PL, { brillo: PL[5] });
    // cresta de oro
    for (let k = 0; k < 5; k++) L.linea(hx - 3 + k * 1.5, hy - 5, hx - 6 + k * 3, hy - 12 + Math.abs(k - 2), k % 2 ? OR[2] : OR[3]);
    L.esfera(hx, hy - 3, 1.8, 1.8, ['#2a4a8a', '#5a8ae0', '#c0e0ff', '#ffffff'], { brillo: '#ffffff' });
    // pico y ojos
    L.rect(hx + 4, hy + 1, 5, 2, OR[2]); L.px(hx + 8, hy + 2, OR[1]); L.hl(hx + 4, hy + 3, 4, OR[1]);
    if (caido) { L.hl(hx - 4, hy - 1, 2, TINTA); L.hl(hx + 1, hy - 1, 2, TINTA); }
    else { L.px(hx - 3, hy - 1, '#ffe060'); L.px(hx + 2, hy - 1, '#ffe060'); L.px(hx - 3, hy, TINTA); L.px(hx + 2, hy, TINTA); }
    // cola
    for (let k = -2; k <= 2; k++) L.linea(39, caido ? 50 : 46, 39 + k * 4, caido ? 55 : 55, k % 2 ? PL[4] : OR[2]);
    return L.contorno('#14141e', 0.7);
  };
  for (let f = 0; f < 5; f++) { const C = tamar(f); def(`tamar_${f}`, C); def(`tamar_${f}_luz`, C.solo(['#ffe060', '#5a8ae0', '#c0e0ff', '#ffffff', OR[3]])); }
}

// ---------------------------------------------------------------- iconos

// Reduce un dibujo ya hecho para usarlo de icono (vecino más cercano).
function reducir(L, maxW = 15, maxH = 15) {
  const k = Math.max(L.w / maxW, L.h / maxH, 1), w = Math.max(1, Math.round(L.w / k)), h = Math.max(1, Math.round(L.h / k)), o = new Lienzo(w, h);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) o.px(x, y, L.get(Math.min(L.w - 1, Math.floor((x + 0.5) * k)), Math.min(L.h - 1, Math.floor((y + 0.5) * k))));
  return o;
}
const dibujoHecho = (n) => { for (let i = DIBUJOS.length - 1; i >= 0; i--) if (DIBUJOS[i][0] === n) return DIBUJOS[i][1]; return null; };

function iconosSelva() {
  const C = (L) => L.contorno();
  const CR = { o: '#5a0e16', b: '#c0283a', c: '#ff7a7a', w: '#ffd0d0' };
  const MS = { a: '#b88e44', b: '#9a7232', c: '#7a5824', d: '#5a4018' };
  // muebles y colocables: el mismo dibujo del mundo, en pequeño
  for (const [id, sp] of [['mesa_carmesi', 'mesa_carmesi'], ['yunque_carmesi', 'yunque_carmesi'], ['farol_carmesi', 'farol_carmesi'], ['incubadora', 'incubadora'], ['idolo_barbas', 'idolo_barbas'], ['idolo_pesca', 'idolo_pesca'], ['idolo_misterioso', 'idolo_misterioso'], ['silla_selva', 'silla_selva'], ['mesa_selva', 'mesa_selva'], ['estanteria', 'estanteria'], ['lampara_pie', 'lampara_pie'], ['valla', 'valla'], ['banderin', 'banderin'], ['maceta_grande', 'maceta_grande'], ['cofre_selva', 'cofre_selva']]) {
    const L = dibujoHecho(sp); if (L) def('i_' + id, reducir(L, 15, 15));
  }
  // materiales
  def('i_madera_selva', C(rej({ W: '#9a7232', w: '#7a5824', d: '#5a4018', e: '#b88e44', g: '#5e9a3a' }, `
    ....WWWWWe..
    ..WWwWWWeWWg
    WWWWWWWWeWWe
    WdWWWWdWWeed
    dddWddddddd.`)));
  def('i_mineral_carmesi', C(rej({ ...CR, p: '#5d6070', P: '#7f8392', q: '#a5a9b6' }, `
    ...qqPP...
    .qPPcbPPp.
    qPcboPPPpp
    PPboPPcbpp
    PPPPPPboPp
    .pPcbPPPp.
    ..ppppppp.`)));
  def('i_lingote_carmesi', C(rej(CR, `
    ...wwwccc.
    ..wccccccb
    .bbbbbbboo
    bbbbbbboo.
    oooooooo..`)));
  def('i_pluma_antigua', C(rej({ a: '#ffffff', b: '#e8e0c8', c: '#c8b890', d: '#8a7a5a' }, `
    .......ab
    ......abb
    .....abbc
    ....abbc.
    ...abbc..
    ..abbc...
    .abcc....
    .dc......
    d........`)));
  def('i_hoja_grande', C(rej({ a: HOJA_S[5], b: HOJA_S[4], c: HOJA_S[3], d: HOJA_S[2] }, `
    ....abb..
    ..abbbbc.
    .abbcbbcc
    abbcbbccd
    bbcbbccd.
    .cbbccd..
    .dccdd...
    d........`)));
  def('i_savia', C(new Lienzo(8, 10).esfera(4, 6, 3.4, 3.6, ['#7a4a08', '#c88a20', '#f0c040', '#fff0a0'], { brillo: '#ffffff' }).rect(3, 0, 2, 3, '#c88a20')));
  def('i_caparazon_musgo', C(new Lienzo(11, 8).esfera(5.5, 5, 5, 3.6, ['#14200e', '#30502a', '#46703a', '#5e9048'], { brillo: HOJA_S[5] }).vl(5, 2, 6, '#14200e')));
  def('i_aguijon', C(rej({ a: '#ffffff', b: '#c8c8d0', c: '#3ad06a', d: '#1a5a2a' }, `
    ......ab
    .....ab.
    ....ab..
    ...cb...
    ..cc....
    .dc.....
    dd......`)));
  def('i_polvo_explosivo', C(rej({ a: '#ffd0a0', b: '#ffa050', c: '#e86a2a', d: '#8a3a1a', w: '#e8e0d0' }, `
    ..ww..
    .w..w.
    wabbcw
    wbbccw
    wbccdw
    .wwww.`)));
  def('i_espora_moho', C(new Lienzo(8, 8).esfera(4, 4, 3.4, 3.4, ['#0e3a3a', '#2ab0a0', '#5ae0c0', '#b0fff0'], { brillo: '#ffffff' })));
  def('i_baba_veneno', C(new Lienzo(9, 7).esfera(4.5, 3.8, 4, 3.2, ['#4a1a6a', '#6a2aa0', '#a060e0', '#d0a0ff'], { brillo: '#ffffff' })));
  def('i_pluma_tamar', C(rej({ a: '#ffffff', b: '#eef2fa', c: '#c8d0e0', g: '#f2c14e', G: '#b8861a' }, `
    .......ab
    ......abb
    .....abbg
    ....abbgc
    ...abggc.
    ..abgcc..
    .agcc....
    .Gc......
    G........`)));
  def('i_moneda', C(new Lienzo(8, 8).esfera(4, 4, 3.6, 3.6, ['#6a4a08', '#b8861a', '#f2c14e', '#ffe58a'], { brillo: '#fffbe0' }).hl(3, 3, 2, '#b8861a').vl(4, 2, 4, '#b8861a')));
  // bloques y suelos
  const cubo = (R) => { const L = new Lienzo(10, 10); L.rect(0, 0, 10, 5, R[3]); L.hl(0, 0, 10, R[4]); L.rect(0, 5, 10, 5, R[2]); L.hl(0, 9, 10, R[1]); L.px(2, 2, R[5] || R[4]); L.px(6, 7, R[1]); return C(L); };
  def('i_bloque_hierba', (() => { const L = new Lienzo(10, 10); L.rect(0, 0, 10, 5, '#46803a'); L.hl(0, 0, 10, '#6ab048'); L.rect(0, 5, 10, 5, TIERRA_S[2]); for (let x = 0; x < 10; x += 2) L.px(x, 5, '#3a6a2a'); L.hl(0, 9, 10, TIERRA_S[0]); return C(L); })());
  def('i_bloque_espino', cubo(RAMPA.espino)); def('i_bloque_musgosa', cubo(RAMPA.musgosa)); def('i_pared_selva', cubo(RAMPA.selva_madera));
  def('i_suelo_tablas', C(new Lienzo(10, 8).rect(0, 0, 10, 8, '#7a5824').vl(0, 0, 8, '#9a7232').vl(3, 0, 8, '#3a2810').vl(4, 0, 8, '#9a7232').vl(7, 0, 8, '#3a2810').vl(8, 0, 8, '#9a7232')));
  def('i_suelo_musgo', C(new Lienzo(10, 8).rect(0, 0, 10, 8, '#2e5a36').esfera(3, 3, 2.4, 2, ['#22482c', '#3c6e42', '#68a060']).esfera(7, 5, 2.4, 2, ['#22482c', '#3c6e42', '#68a060'])));
  def('i_suelo_templo', C(new Lienzo(10, 8).rect(0, 0, 10, 8, '#5a5a4a').rect(1, 1, 4, 3, '#828270').rect(6, 1, 3, 3, '#828270').rect(1, 5, 3, 2, '#828270').rect(5, 5, 4, 2, '#828270').px(1, 1, '#9a9a86')));
  def('i_alfombra_verde', C(new Lienzo(10, 8).rect(0, 2, 10, 6, '#1e4a2e').hl(0, 2, 10, '#d0a040').hl(0, 7, 10, '#d0a040').esfera(8, 4, 2.4, 3, ['#14301e', '#1e4a2e', '#2a5e3a'])));
  // herramientas y armas
  const MANGO2 = { h: '#8a5a36', p: '#5a3a26' };
  def('i_pico_carmesi', C(rej({ J: CR.w, H: CR.b, D: CR.o, ...MANGO2 }, `
    ..JJJH....
    .HHDDHH...
    .D...hHH..
    ....h.DH..
    ...h...DH.
    ..h.....D.
    .h........
    p.........`)));
  def('i_espada_carmesi', C(rej({ J: CR.w, H: CR.b, D: CR.o, G: '#f2c14e', g: '#8a6420', ...MANGO2 }, `
    ........JJ
    .......JHH
    ......JHD.
    .....JHD..
    ....JHD...
    .G.JHD....
    ..GHD.....
    ..hGG.....
    .h..G.....
    p.........`)));
  def('i_maza_carmesi', C(rej({ o: CR.o, b: CR.b, c: CR.c, ...MANGO2 }, `
    ....c.c.
    ...cbbbc
    ...bbbbo
    ..cbbbo.
    ...oho..
    ..h.....
    .h......
    p.......`)));
  def('i_lanza_espino', C(rej({ a: '#e8e0d0', b: '#a8a090', c: '#6a4020', d: '#4a2c16', e: '#8a5a2e' }, `
    .........a
    ........ab
    .......cb.
    ......ec..
    .....ed...
    ....ed....
    ...ed.....
    ..ed......
    .ed.......
    d.........`)));
  const arcoI = (o, b, c, s) => C(rej({ o, b, c, s }, `
    ..bbc...
    .b...s..
    b.....s.
    b......s
    b......s
    b.....s.
    .b...s..
    ..bbo...`));
  def('i_arco_carmesi', arcoI(CR.o, CR.b, CR.c, '#ffe0e0'));
  def('i_arco_tamar', (() => { const L = arcoI('#b8861a', '#eef2fa', '#ffffff', '#f2c14e'); L.px(1, 1, '#f2c14e'); L.px(1, 8, '#f2c14e'); return L; })());
  def('i_latigo_hiedra', C(rej({ a: '#e070ff', b: '#4a8a2c', c: '#346a20', d: '#244a16', h: '#5a3a20' }, `
    ..bbbc....
    .b....c...
    b......c..
    b.......c.
    .c......da
    ..cc...d..
    ....h.....
    ...h......
    ..h.......`)));
  const flechaI = (p, m) => C(rej({ m, p, f: '#e8e0d0' }, `
    ......pp
    .....mpp
    ....m...
    ...m....
    ..m.....
    ff......
    f.......`));
  def('i_flecha_carmesi', flechaI(CR.b, '#7a5824')); def('i_flecha_aguijon', flechaI('#3ad06a', '#7a5824'));
  def('i_bomba', reducir(dibujoHecho('bomba'), 12, 12));
  def('i_cana_carmesi', C(rej({ h: CR.b, H: '#3a2a20', l: '#e8e8f0', r: '#ff7a7a' }, `
    .........h
    ........h.
    .......h.l
    ......h..l
    .....h...l
    ...rh....l
    ..rrh....l
    ..hr.....l
    .H.......l`)));
  def('i_regadera_grande', C(rej({ o: CR.o, b: CR.b, c: CR.c, w: '#8ad0ff' }, `
    ...ccc.....
    ..c...c....
    .bbbbbbb.ww
    cbbbbbbbbbo
    bbbbbbbbbo.
    .oooooooo..`)));
  def('i_azada_carmesi', C(rej({ h: '#8a5a36', p: '#5a3a26', o: CR.o, b: CR.b, c: CR.c }, `
    cbbbb....
    cbbbo....
    .o.h.....
    ...h.....
    ....h....
    .....h...
    ......h..
    .......p.`)));
  def('i_piedra_regreso', C(new Lienzo(9, 10).esfera(4.5, 5, 4, 4.5, ['#1a3a5a', '#3a6a9a', '#7ab0e0', '#c8e8ff'], { brillo: '#ffffff' }).hl(3, 5, 3, '#ffb3c8').px(4, 4, '#ffb3c8')));
  def('i_cebo', C(rej({ a: '#e8a0a0', b: '#c06070', c: '#8a3a4a' }, `
    ..ab...
    .abbc..
    ..cbbc.
    ...cbc.
    ..abc..
    .bc....`)));
  // armadura (mismos dibujos que la de fase 1, con su color)
  for (const [m, o, b, c] of [['carmesi', CR.o, CR.b, CR.c], ['musgo', '#2a4a1c', '#5a8a32', '#a8d870']]) {
    def(`i_casco_${m}`, C(rej({ o, b, c }, `
      ..ccbb..
      .cbbbbo.
      cbbbbbbo
      bbbbbbbo
      boo..oob
      o......o`)));
    def(`i_peto_${m}`, C(rej({ o, b, c }, `
      .cb....bc.
      cbbb..bbbo
      bbcbbbbbbo
      .bcbbbbbo.
      .bbbbbbbo.
      .bbbbbboo.
      ..bbbboo..`)));
    def(`i_botas_${m}`, C(rej({ o, b, c }, `
      .cb..cb.
      .bb..bb.
      .bb..bb.
      cbbo.bbbc
      bbbo.bbbo`)));
  }
  // accesorios
  def('i_anillo_carmesi', C(rej({ a: '#f2a466', b: '#c8693a', c: '#7c3a22', g: '#ff5a6a', G: '#8a1420', w: '#ffffff' }, `
    ..wg..
    .gGGg.
    .abba.
    a....b
    b....c
    .bccc.`)));
  def('i_collar_moho', C(rej({ a: '#fff0a0', b: '#e0a830', g: '#5ae0c0', G: '#2ab0a0', w: '#ffffff' }, `
    a......a
    .b....b.
    ..b..b..
    ...wg...
    ...gG...
    ....G...`)));
  def('i_anzuelo_oro', C(rej({ a: '#fff0a0', b: '#f2c14e', c: '#b8861a', l: '#e8e8f0' }, `
    ..l...
    ..l...
    ..a...
    ..b...
    ..b..a
    b.b..b
    .bbbc.`)));
  def('i_alas_tamar', C(rej({ a: '#ffffff', b: '#eef2fa', c: '#c8d0e0', g: '#f2c14e' }, `
    a......a
    ba....ab
    bba..abb
    cbbaabbc
    .cbggbc.
    ..c..c..`)));
  def('i_semilla_hiedra', C(rej({ a: '#ffd0ff', b: '#e070ff', c: '#8a3aa8', g: '#4a8a2c', G: '#244a16' }, `
    ..g...
    .gG...
    ..abb.
    .abbbc
    .bbbcc
    ..ccc.`)));
  // comida, peces y semillas
  def('i_fruta_selva', C(new Lienzo(9, 10).esfera(4.5, 6, 3.8, 3.6, ['#7a1a10', '#c84020', '#ff8a4a', '#ffd0a0'], { brillo: '#ffffff' }).linea(4, 2, 5, 0, '#5a3a20').px(6, 1, HOJA_S[4]).px(7, 1, HOJA_S[3])));
  def('i_avena', C(rej({ a: '#fff8c0', b: '#f0e090', c: '#c8b060', t: '#8a7a3a' }, `
    .a.a.a.
    .b.b.b.
    abbabba
    .bcbcb.
    ..t.t..
    ..ttt..
    ...t...`)));
  def('i_melon_luna', C(new Lienzo(10, 9).esfera(5, 5, 4.5, 3.8, ['#1a4a6a', '#3a8ab0', '#70d0f0', '#c8f8ff'], { brillo: '#ffffff' }).vl(5, 1, 7, '#2a6a8a')));
  def('i_miel', C(rej({ a: '#fff0a0', b: '#f0c040', c: '#c88a20', g: '#c0c0c8', G: '#8a8a98' }, `
    .GggG.
    .g..g.
    gabbbg
    gbbbcg
    gbbccg
    .gggg.`)));
  def('i_carne_escarabajo', C(rej({ c: '#e0c0a0', C: '#b88a60', g: '#46703a', G: '#30502a' }, `
    ..gggg..
    .gGGGGg.
    cccccccC
    cCCCCCCC
    .CCCCCC.`)));
  const pez = (cs) => C(rej({ a: cs[3], b: cs[2], c: cs[1], d: cs[0], e: '#14101c', w: '#ffffff' }, `
    ....abb....
    ..abbbbbc.d
    .abwebbbbcdd
    abbbbbbbbcd.
    .cbbbbbbccdd
    ..cccccc..d`));
  def('i_pez_hoja', pez(['#1e4a18', '#3a7a2a', '#6ab048', '#c0f090']));
  def('i_pez_espino', pez(['#4a2010', '#8a4a20', '#c87a3a', '#f0c080']));
  def('i_pez_anguila', pez(['#1a3a5a', '#3a7ab0', '#7ac8f0', '#e0ffff']));
  def('i_pez_sol', pez(['#8a4a08', '#e08a20', '#ffc040', '#fff8c0']));
  const semilla = (col) => C(rej({ a: col[0], b: col[1], c: col[2] }, `
    .ab..
    abbc.
    .bc.a
    ..abb
    ..bbc`));
  def('i_semilla_avena', semilla(['#fff8c0', '#c8b060', '#8a7a3a'])); def('i_semilla_melon', semilla(['#c8f8ff', '#3a8ab0', '#1a4a6a']));
  // huevos y mascotas
  for (const [h, col] of [['leal', ['#5a3a24', '#9a6e4a', '#c0946a', '#f0d0a8']], ['baboso', ['#2f6e35', '#56a83b', '#86d64f', '#c0f080']], ['luz', ['#8a7a20', '#e0c040', '#fff080', '#ffffff']], ['moho', ['#0e3a3a', '#2ab0a0', '#5ae0c0', '#b0fff0']]]) {
    const L = new Lienzo(9, 11); L.esfera(4.5, 6, 3.8, 4.6, col, { brillo: '#ffffff' }); L.px(3, 7, col[0]); L.px(6, 5, col[0]); L.px(5, 8, col[0]);
    def('i_huevo_' + h, C(L));
  }
  for (const m of ['topito', 'babosita', 'luci', 'mohito']) { const L = dibujoHecho(`${m}_0`); if (L) def('i_mascota_' + m, reducir(L, 14, 14)); }
  // especiales
  def('i_ofrenda_cielo', C(rej({ a: '#ffffff', b: '#e8e0c8', g: '#f2c14e', r: CR.b, R: CR.o, p: '#c8b890' }, `
    .a...a.
    .ab.ab.
    ..bgb..
    .rgggr.
    rRrrrRr
    .ppppp.`)));
  const gema = (col) => C(rej({ a: col[2], b: col[1], c: col[0], w: '#ffffff' }, `
    ...w...
    ..wab..
    .wabbc.
    wabbbcc
    .abbcc.
    ..bcc..
    ...c...`));
  def('i_gema_cielo', gema(['#3a5aa0', '#a0c0ff', '#e8f0ff'])); def('i_gema_hiedra', gema(['#2a6a3a', '#5ae0a0', '#d0ffe0']));
  // platos nuevos
  const cuenco = (sopa, brillo) => C(rej({ s: sopa, S: brillo, b: '#c8a070', B: '#8a6040', o: '#5a3a20' }, `
    .SSSsss.
    bsssssSb
    bBbbbbbB
    .bBBBBb.
    ..oooo..`));
  def('i_plato_fruta_selva', cuenco('#e86a3a', '#ffc080')); def('i_plato_avena', cuenco('#e0d090', '#fff8c0')); def('i_plato_melon_luna', cuenco('#70d0f0', '#e0ffff'));
  def('i_plato_miel', C(rej({ a: '#fff0a0', b: '#f0c040', c: '#c88a20', p: '#e8e8f0', P: '#a8a8b8' }, `
    ..aaab..
    .abbbbb.
    pbbbbccp
    PppppppP
    .PPPPPP.`)));
  def('i_plato_carne_escarabajo', cuenco('#8a5a3a', '#c09060')); def('i_plato_pez_hoja', cuenco('#6ab048', '#c0f090'));
  def('i_plato_pez_espino', C(rej({ m: '#a07040', c: '#e09060', C: '#a85a30', g: '#5ea440' }, `
    ........m
    ......cm.
    .....cCm.
    ...gcCm..
    ..cCcm...
    .cCm.....
    .m.......
    m........`)));
  def('i_plato_pez_anguila', cuenco('#3a7ab0', '#a0e0ff'));
  def('i_plato_pez_sol', C(rej({ p: '#fff0a0', P: '#c8962a', a: '#ffc040', b: '#e08a20', w: '#ffffff' }, `
    ..wabb..
    .aabbba.
    pabbbbap
    PppppppP
    .PPPPPP.`)));
}
