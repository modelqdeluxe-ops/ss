'use strict';
// Arte de las bandas nuevas del sector de la Selva (0.7): paredes, suelos, menas, adornos e iconos de la Ciénaga de
// la Podre y de la Ciudad Velada. Las paredes se registran antes de construir el terreno (RAMPA y MURO_ARTE); los
// suelos, adornos e iconos van en CONSTRUIR_MODELOS (después de todo lo demás).

// ---------------------------------------------------------------- paletas

Object.assign(RAMPA, {
  turba: ['#0c0806', '#17100b', '#231811', '#302118', '#3e2c20', '#4e3a2a'],
  raiz_muerta: ['#0e0c0c', '#1c1816', '#2a2420', '#3a332e', '#4c443c', '#625850'],
  costra: ['#0a0408', '#140810', '#200e1a', '#2c1424', '#3a1a30', '#4a223e'],
  hueso_asor: ['#3a3226', '#5a4e3c', '#7e7058', '#a29378', '#c4b698', '#e2d6ba'],
  velada: ['#16181e', '#24272f', '#343842', '#464b57', '#5a606e', '#727a8a'],
  velada_vieja: ['#111317', '#1c1f26', '#2a2e36', '#3a3f4a', '#4c5260', '#626a7a'],
  cripta: ['#0b0a0f', '#15121b', '#1f1b27', '#2a2534', '#363042', '#443c52'],
  piedra_gris: ['#131418', '#1e2026', '#2b2d35', '#3a3d47', '#4c505b', '#626774'],
});
MENA_COL.medula = ['#4a3c2c', '#9a8668', '#dccca8', '#fff2da', '#c8302a'];
MENA_COL.plata = ['#2e3440', '#6a7688', '#aebbd0', '#e4ecfa', '#ffffff'];
const PODRE_C = ['#3a0f30', '#7a1a5c', '#c02a88', '#ff3cb4', '#ffb0e0'];
const HUESO_C = ['#5a4e3c', '#8a7c62', '#b8aa8a', '#ddd0b0', '#f6eedc'];

// ---------------------------------------------------------------- paredes

// voronoi que encaja por los bordes: devuelve, por casilla, la celda y la distancia al borde de la celda
function celdas16(r, n) {
  const pts = Array.from({ length: n }, () => [r() * 16, r() * 16, r()]);
  return (x, y) => {
    let d1 = 99, d2 = 99, c = null;
    for (const p of pts) for (const [ox, oy] of [[0, 0], [16, 0], [-16, 0], [0, 16], [0, -16], [16, 16], [-16, -16], [16, -16], [-16, 16]]) {
      const d = Math.hypot(x + 0.5 - p[0] - ox, y + 0.5 - p[1] - oy);
      if (d < d1) { d2 = d1; d1 = d; c = p; } else if (d < d2) d2 = d;
    }
    return { c, borde: d2 - d1 };
  };
}
MURO_ARTE.turba = {
  arriba(L, R, r, v) { // tierra negra empapada: fibras, raicillas y algún brillo de agua
    const n = ruidoCiclico(2100 + v * 5, 4), f = ruidoCiclico(2110 + v, 8);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const a = n(x, y) * 0.7 + f(x, y) * 0.3; L.px(x, y, a < 0.34 ? R[1] : a < 0.58 ? R[2] : a < 0.8 ? R[3] : R[4]); }
    for (let k = 0; k < 4; k++) { let x = entre(r, 0, 15), y = entre(r, 0, 15); const dx = r() < 0.5 ? 1 : -1; for (let s = 0; s < 6; s++) { L.pxc(x, y, R[5]); L.pxc(x, y + 1, R[1]); x += dx; if (r() < 0.4) y += entre(r, -1, 1); } }
    for (let k = 0; k < 2; k++) { const x = entre(r, 1, 14), y = entre(r, 1, 14); L.pxc(x, y, '#4a4a3e'); L.pxc(x + 1, y, '#2e2c24'); }
  },
  cara(L, R, r, v) { // estratos de turba con raíces colgando y algún hueso
    const n = ruidoCiclico(2200 + v, 4, 16);
    for (let y = 0; y < 12; y++) for (let x = 0; x < 16; x++) { const a = n(x, y * 2.2); L.px(x, y, a < 0.4 ? R[2] : a < 0.7 ? R[3] : R[1]); }
    for (const y0 of [3, 7, 10]) for (let x = 0; x < 16; x++) if (r() < 0.75) L.px(x, y0 + (r() < 0.2 ? 1 : 0), R[1]);
    for (let k = 0; k < 3; k++) { let x = entre(r, 1, 14); for (let y = 0; y < entre(r, 4, 9); y++) { L.px(x, y, R[5]); if (r() < 0.3) x += entre(r, -1, 1); } }
    if (v === 1) { const x = entre(r, 3, 11), y = entre(r, 5, 8); L.hl(x, y, 4, HUESO_C[3]); L.px(x - 1, y - 1, HUESO_C[2]); L.px(x + 4, y + 1, HUESO_C[2]); L.hl(x, y + 1, 4, HUESO_C[1]); }
  },
};
MURO_ARTE.raiz_muerta = {
  arriba(L, R, r) { // raíces grises que se cruzan, con huecos negros entre ellas
    L.rect(0, 0, 16, 16, R[0]);
    for (let k = 0; k < 7; k++) {
      const hor = k % 2 === 0; let a = entre(r, 0, 15), b = 0; const g = 2 + (k % 3 === 0 ? 1 : 0);
      for (let s = 0; s < 18; s++) {
        for (let w = 0; w < g; w++) { const c = w === 0 ? R[4] : w === g - 1 ? R[1] : R[3]; if (hor) L.pxc(b, a + w, c); else L.pxc(a + w, b, c); }
        b++; if (r() < 0.45) a += entre(r, -1, 1);
      }
    }
    for (let k = 0; k < 3; k++) L.pxc(entre(r, 0, 15), entre(r, 0, 15), R[5]);
  },
  cara(L, R, r, v) {
    L.rect(0, 0, 16, 12, R[0]);
    for (let k = 0; k < 6; k++) {
      let x = entre(r, -1, 15); const g = 2 + (k % 2);
      for (let y = 0; y < 12; y++) { for (let w = 0; w < g; w++) L.px(x + w, y, w === 0 ? R[4] : w === g - 1 ? R[1] : R[3]); if (r() < 0.3) x += entre(r, -1, 1); }
    }
    if (v === 1) for (let k = 0; k < 2; k++) { const x = entre(r, 2, 13), y = entre(r, 3, 9); L.px(x, y, '#1a0e0a'); L.px(x, y + 1, '#120806'); }
  },
};
MURO_ARTE.costra = {
  arriba(L, R, r, v) { // la Podre seca: placas negras con grietas moradas y poros que brillan
    const cel = celdas16(r, 7);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const { c, borde } = cel(x, y); L.px(x, y, borde < 0.9 ? R[0] : borde < 1.6 ? PODRE_C[0] : c[2] < 0.5 ? R[3] : R[4]); }
    for (let k = 0; k < 3 + v; k++) { const x = entre(r, 1, 14), y = entre(r, 1, 14); L.px(x, y, PODRE_C[3]); L.px(x + 1, y, PODRE_C[2]); L.px(x, y + 1, PODRE_C[1]); if (r() < 0.4) L.px(x - 1, y - 1, PODRE_C[4]); }
  },
  cara(L, R, r) {
    for (let y = 0; y < 12; y++) for (let x = 0; x < 16; x++) { const b = Math.sin(x * 0.9 + y * 0.5) + Math.sin(y * 1.3 - x * 0.4); L.px(x, y, b > 1.1 ? R[4] : b > -0.4 ? R[3] : R[2]); }
    for (let k = 0; k < 5; k++) { const x = entre(r, 1, 14), y = entre(r, 1, 10); L.px(x, y, PODRE_C[3]); L.px(x, y + 1, PODRE_C[1]); if (r() < 0.5) L.px(x + 1, y, PODRE_C[2]); }
  },
};
MURO_ARTE.hueso_asor = {
  arriba(L, R, r) { // hueso del dios: liso, amarillento, con poros y grietas finas
    const n = ruidoCiclico(2400, 4);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const a = n(x, y); L.px(x, y, a < 0.3 ? R[3] : a < 0.72 ? R[4] : R[5]); }
    for (let k = 0; k < 9; k++) { const x = entre(r, 0, 15), y = entre(r, 0, 15); L.pxc(x, y, R[2]); if (r() < 0.4) L.pxc(x + 1, y, R[3]); }
    let x = entre(r, 0, 15), y = 0; for (let s = 0; s < 16; s++) { L.pxc(x, y, R[2]); y++; if (r() < 0.4) x += entre(r, -1, 1); }
  },
  cara(L, R, r) { // anillos de crecimiento del hueso
    for (let y = 0; y < 12; y++) for (let x = 0; x < 16; x++) { const b = Math.sin(y * 1.1 + Math.sin(x * 0.5) * 1.2); L.px(x, y, b > 0.6 ? R[5] : b > -0.3 ? R[4] : R[3]); }
    for (let k = 0; k < 6; k++) L.px(entre(r, 0, 15), entre(r, 0, 11), R[2]);
    L.hl(0, 11, 16, R[1]);
  },
};
const sillares = (L, R, r, filas, ancho, v) => { // sillares de los Veladores: bloques grandes con junta fina
  const alto = Math.floor(16 / filas);
  for (let j = 0; j < filas; j++) for (let x0 = (j % 2) * (ancho >> 1) - ancho; x0 < 16; x0 += ancho) {
    const y0 = j * alto, t = r() < 0.5 ? R[3] : mezcla(R[3], R[4], 0.4);
    L.rect(x0, y0, ancho, alto, t); L.hl(x0, y0, ancho, R[4]); L.vl(x0, y0, alto, R[4]); L.hl(x0, y0 + alto - 1, ancho, R[1]); L.vl(x0 + ancho - 1, y0, alto, R[1]);
    if (r() < 0.35) L.px(x0 + entre(r, 2, ancho - 3), y0 + entre(r, 1, alto - 2), R[2]);
  }
  void v;
};
MURO_ARTE.velada = {
  arriba(L, R, r, v) { sillares(L, R, r, 2, 8, v); if (v === 1) { const x = entre(r, 2, 12), y = entre(r, 2, 12); L.px(x, y, HOJA_S[2]); L.px(x + 1, y, HOJA_S[1]); L.px(x, y + 1, HOJA_S[1]); } },
  cara(L, R, r, v) {
    for (const [y0, off] of [[0, v * 3], [6, 4 + v * 2]]) for (let x0 = -8 + off; x0 < 16; x0 += 8) { L.rect(x0, y0, 8, 6, R[3]); L.hl(x0, y0, 8, R[4]); L.hl(x0, y0 + 5, 8, R[1]); L.vl(x0 + 7, y0, 6, R[1]); L.vl(x0, y0, 6, R[4]); }
    void r;
  },
};
MURO_ARTE.velada_vieja = {
  arriba(L, R, r, v) { sillares(L, R, r, 2, 8, v); for (let k = 0; k < 6; k++) { const x = entre(r, 0, 15), y = entre(r, 0, 15); L.pxc(x, y, HOJA_S[1]); L.pxc(x + 1, y, HOJA_S[2]); } },
  cara(L, R, r, v) { // friso con caras veladas: el velo es la marca de los Veladores
    for (let x0 = 0; x0 < 16; x0 += 8) { L.rect(x0, 0, 8, 12, R[3]); L.vl(x0, 0, 12, R[4]); L.vl(x0 + 7, 0, 12, R[1]); }
    L.hl(0, 0, 16, R[4]); L.hl(0, 3, 16, R[1]); L.hl(0, 11, 16, R[1]);
    const cx = 4 + (v ? 8 : 0); // una cara velada: cabeza redonda, el velo cae en pliegues
    L.elipse(cx, 6.5, 2.2, 2.2, R[4]); L.vl(cx - 2, 6, 4, R[2]); L.vl(cx, 7, 3, R[2]); L.vl(cx + 2, 6, 4, R[2]); L.hl(cx - 1, 6, 3, R[1]);
    for (let k = 0; k < 4; k++) { const x = entre(r, 0, 15); L.px(x, 0, HOJA_S[2]); L.px(x, 1, HOJA_S[1]); }
  },
};
MURO_ARTE.cripta = {
  arriba(L, R, r, v) { sillares(L, R, r, 4, 8, v); if (v === 0) { L.px(7, 6, '#4a405a'); L.px(8, 6, '#4a405a'); L.px(7, 7, '#2a2434'); } },
  cara(L, R, r, v) { // nichos con huesos
    L.rect(0, 0, 16, 12, R[2]); L.hl(0, 0, 16, R[4]); L.hl(0, 11, 16, R[1]);
    for (const x0 of [1, 9]) {
      L.rect(x0, 3, 6, 6, R[0]); L.hl(x0, 2, 6, R[4]); L.vl(x0 - 1, 3, 6, R[4]); L.vl(x0 + 6, 3, 6, R[1]);
      if ((x0 === 1) !== (v === 1)) { L.elipse(x0 + 2.5, 6.5, 1.6, 1.4, HUESO_C[2]); L.px(x0 + 2, 6, '#0b0a0f'); L.px(x0 + 3, 6, '#0b0a0f'); L.hl(x0 + 1, 8, 4, HUESO_C[1]); }
      else { L.hl(x0 + 1, 7, 4, HUESO_C[2]); L.hl(x0 + 2, 8, 3, HUESO_C[1]); }
    }
    void r;
  },
};
// piedra gris: los bloques de roca de siempre (sin MURO_ARTE), con su paleta

// ---------------------------------------------------------------- suelos, adornos e iconos

CONSTRUIR_MODELOS.push(function arteBandas() {
  // textura base con tres o cuatro tonos en manchas grandes, como los suelos de suelos2.js
  function base(sem, pal, umbrales = [0.34, 0.6, 0.82]) {
    const a = ruidoCiclico(sem, 3), b = ruidoCiclico(sem + 1, 6), L = new Lienzo(16, 16), id = new Int8Array(256);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const n = a(x, y) * 0.68 + b(x, y) * 0.32;
      id[y * 16 + x] = n < umbrales[0] ? 0 : n < umbrales[1] ? 1 : n < umbrales[2] ? 2 : Math.min(3, pal.length - 1);
    }
    const c = id.slice();
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const v = id[y * 16 + x], ve = [id[y * 16 + ((x + 1) & 15)], id[y * 16 + ((x + 15) & 15)], id[((y + 1) & 15) * 16 + x], id[((y + 15) & 15) * 16 + x]];
      if (!ve.includes(v)) { const cuenta = {}; for (const q of ve) cuenta[q] = (cuenta[q] || 0) + 1; c[y * 16 + x] = +Object.entries(cuenta).sort((p, q) => q[1] - p[1])[0][0]; }
    }
    for (let i = 0; i < 256; i++) L.d[i] = pal[c[i]];
    return L;
  }
  const losas = (L, r, cols, junta, n) => { // losas irregulares que encajan
    const cel = celdas16(r, n);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const { c, borde } = cel(x, y); L.px(x, y, borde < 0.8 ? junta : cols[Math.floor(c[2] * cols.length)]); }
  };
  const S = {
    lodo: (v) => { // barro negro empapado, con charquitos que reflejan
      const L = base(3000 + v * 13, ['#100e0a', '#17140f', '#1f1b14', '#28231a']), r = azar(3001 + v);
      if (v < 3) { const x = entre(r, 2, 11), y = entre(r, 3, 12); L.elipse(x + 1.5, y, 2.6, 1.3, '#0c0f0e'); L.hl(x, y - 1, 2, '#3a4440'); L.px(x + 3, y, '#222a28'); }
      for (let k = 0; k < 3; k++) { const x = entre(r, 0, 15), y = entre(r, 0, 15); L.pxc(x, y, '#33291c'); L.pxc(x + 1, y, '#0a0806'); }
      return L;
    },
    agua_negra: (v) => { // agua quieta y negra; apenas unas ondas
      const L = base(3020 + v * 13, ['#050b0a', '#081110', '#0c1715', '#101d1a'], [0.4, 0.66, 0.88]), r = azar(3021 + v);
      for (let k = 0; k < 2; k++) { const x = entre(r, 1, 12), y = entre(r, 2, 14); L.hl(x, y, 3, '#1a2e2a'); L.px(x + 3, y, '#132420'); }
      return L;
    },
    podre: (v) => { // la tierra que la Podre ya se comió: casi negra, alguna vena morada y, solo a veces, un poro
      const L = base(3040 + v * 13, ['#09060a', '#0e080e', '#130a13', '#1a0e19'], [0.38, 0.68, 0.92]), vena = ruidoCiclico(3045 + v, 3), r = azar(3041 + v);
      for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const d = Math.abs(vena(x, y) - 0.5); if (d < 0.018) L.px(x, y, '#2a0c22'); else if (d < 0.035 && (x + y) % 3) L.px(x, y, '#1c0a18'); }
      if (v === 1) { const x = entre(r, 3, 12), y = entre(r, 3, 12); L.pxc(x, y, '#a0206e'); L.pxc(x + 1, y, '#4a1238'); L.pxc(x, y + 1, '#3a0f30'); }
      if (v === 3) { const x = entre(r, 3, 12), y = entre(r, 3, 12); L.pxc(x, y, '#3a0f30'); L.pxc(x + 1, y + 1, '#2a0c22'); }
      return L;
    },
    turbera: (v) => { // turba blanda y fibrosa
      const L = base(3060 + v * 13, ['#140e08', '#1c140c', '#251a10', '#2e2214']), r = azar(3061 + v);
      for (let k = 0; k < 2; k++) { let x = entre(r, 0, 15), y = entre(r, 0, 15); for (let s = 0; s < 6; s++) { L.pxc(x, y, '#3a2c1c'); L.pxc(x, y + 1, '#0e0a06'); x++; if (r() < 0.35) y += entre(r, -1, 1); } }
      if (v % 2) { const x = entre(r, 2, 12), y = entre(r, 2, 12); L.pxc(x, y, '#3e4a2a'); L.pxc(x + 1, y, '#2a3420'); L.pxc(x, y - 1, '#4a5a32'); }
      return L;
    },
    calzada: (v) => { // la calzada de la Ciudad: losas gastadas, alguna rota, musgo en las juntas
      const L = new Lienzo(16, 16), r = azar(3080 + v);
      losas(L, r, ['#353944', '#3c414c', '#434855', '#4a505d'], '#191b21', 5);
      if (v === 1) { const x = entre(r, 3, 11), y = entre(r, 3, 11); L.linea(x, y, x + 3, y + 2, '#1c1e24'); L.px(x + 1, y, '#5a606e'); }
      for (let k = 0; k < 3; k++) { const x = entre(r, 0, 15), y = entre(r, 0, 15); if (L.get(x, y) === '#191b21') { L.px(x, y, HOJA_S[1]); L.pxc(x + 1, y, HOJA_S[0]); } }
      return L;
    },
    mosaico: (v) => { // teselas azul noche con un rombo de oro gastado; casi no se ve hasta que le da la luz
      const L = new Lienzo(16, 16), r = azar(3100 + v), az = ['#141832', '#171c3a', '#1b2142'], oro = ['#3a3220', '#4a3e26', '#5a4a2c'];
      for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
        const t = ((x >> 1) + (y >> 1) + ((x >> 2) & 1)) % 3, dx = Math.abs(x - 7.5), dy = Math.abs(y - 7.5);
        let c = (x % 2 === 0 && y % 2 === 0) ? '#10132a' : az[t];
        if (dx + dy < 6.6 && dx + dy > 5.6) c = oro[(x + y) % 3];
        if (dx + dy < 1.2) c = oro[1];
        if (x === 0 || y === 0) c = '#0d1024';
        L.px(x, y, c);
      }
      if (v >= 2) for (let k = 0; k < 4; k++) L.px(entre(r, 1, 14), entre(r, 1, 14), '#0a0c1c'); // teselas que faltan
      return L;
    },
    ceniza: (v) => { // ceniza fría donde ardieron los jardines de la Ciudad
      const L = base(3120 + v * 13, ['#1c1a18', '#242220', '#2c2a27', '#363330']), r = azar(3121 + v);
      for (let k = 0; k < 5; k++) { const x = entre(r, 0, 15), y = entre(r, 0, 15); L.pxc(x, y, '#46423e'); if (r() < 0.4) L.pxc(x + 1, y, '#3c3936'); }
      if (v === 2) { const x = entre(r, 2, 12), y = entre(r, 2, 12); L.pxc(x, y, '#100e0c'); L.pxc(x + 1, y, '#141210'); L.pxc(x + 2, y + 1, '#100e0c'); }
      return L;
    },
    placa: () => { // placa de presión: una losa con un aro de runas apagado (al pisarla se enciende: placa_luz)
      const L = new Lienzo(16, 16);
      L.rect(0, 0, 16, 16, '#2c3038'); L.rect(1, 1, 14, 14, '#4a505c'); L.hl(1, 1, 14, '#5e6674'); L.vl(1, 1, 14, '#5e6674'); L.hl(1, 14, 14, '#262a32'); L.vl(14, 1, 14, '#262a32');
      for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8); if (d < 5.2 && d > 4.2) L.px(x, y, '#2a2434'); if (d < 1.4) L.px(x, y, '#2a2434'); }
      return L;
    },
    cripta: (v) => { // losas oscuras de la cripta con inscripciones gastadas
      const L = new Lienzo(16, 16), r = azar(3140 + v);
      for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) L.px(x, y, (x >> 3) + (y >> 3) & 1 ? '#17141d' : '#1b1822');
      L.hl(0, 0, 16, '#0c0a10'); L.vl(0, 0, 16, '#0c0a10'); L.hl(0, 8, 16, '#0c0a10'); L.vl(8, 0, 16, '#0c0a10');
      for (let k = 0; k < 2; k++) { const x0 = entre(r, 0, 1) * 8 + 2, y0 = entre(r, 0, 1) * 8 + 2; L.hl(x0, y0, 4, '#2c2736'); L.hl(x0, y0 + 2, 3, '#2c2736'); L.px(x0 + 1, y0 + 4, '#2c2736'); }
      if (v === 3) { const x = entre(r, 2, 12), y = entre(r, 2, 12); L.px(x, y, '#3a1a2e'); L.px(x + 1, y, '#2a1224'); }
      return L;
    },
  };
  for (const [arte, fn] of Object.entries(S)) for (let v = 0; v < 4; v++) def(`s_${arte}_${v}`, fn(v));
  def('placa_luz', (() => { const L = new Lienzo(16, 16); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8); if ((d < 5.2 && d > 4.2) || d < 1.4) L.px(x, y, '#c8a8ff'); else if (d < 6 && d > 3.6) L.px(x, y, '#7a48e080'); } return L; })());
  bordesSuelo(['lodo', 'podre', 'turbera', 'ceniza']);

  // ---- adornos de pie y tumbados
  const JUNCO = ['#1e2414', '#2e3a1e', '#44522a', '#5e6c38'], CAT = ['#2a180c', '#4a2c16', '#6a4222'];
  for (let k = 0; k < 2; k++) {
    const L = new Lienzo(9, 15), r = azar(3200 + k);
    for (let t = 0; t < 4 + k; t++) {
      let x = 1.5 + t * 1.6 + r() * 0.8; const alto = 9 + Math.floor(r() * 5), lado = r() < 0.5 ? -1 : 1;
      for (let s = 0; s < alto; s++) { L.px(x, 14 - s, JUNCO[Math.min(3, 1 + Math.floor((s / alto) * 3))]); if (s > alto * 0.6 && r() < 0.3) x += lado * 0.6; }
      if (t % 2 === 0) { L.vl(Math.round(x), 14 - alto - 2, 3, CAT[1]); L.px(Math.round(x), 14 - alto - 2, CAT[2]); L.px(Math.round(x) + 1, 14 - alto - 1, CAT[0]); }
    }
    def(`dc_junco_${k}`, L.contorno('#080a06', 0.7));
  }
  def('dc_poro_podre', new Lienzo(9, 6).esfera(4.5, 3.6, 4, 2.6, ['#0c060c', '#1c0d19', '#2a1224', '#3a1a30']).esfera(3, 3, 1.2, 1, PODRE_C.slice(1, 4)).px(6, 3, PODRE_C[3]).contorno('#050206', 0.7));
  def('dc_helecho_muerto', (() => { const L = new Lienzo(13, 9), C = ['#2a2218', '#4a3a28', '#6a5638']; for (const [a, l] of [[-2.5, 6], [-2, 7], [-1.3, 5], [-0.8, 4]]) for (const s of [1, -1]) { const ang = s > 0 ? a : -Math.PI - a; for (let k = 0; k < l; k++) { const x = 6.5 + Math.cos(ang) * k, y = 8 + Math.sin(ang) * k * 0.8 + (k > l - 3 ? 1 : 0); L.px(x, y, k > l - 2 ? C[2] : C[1]); if (k % 2) L.px(x, y + 1, C[0]); } } return L.contorno('#0a0806', 0.7); })());
  def('dc_hierba_seca', (() => { const L = new Lienzo(10, 8), r = azar(3210), C = ['#3a3428', '#5a5038', '#7a6e4e', '#988a62']; for (let h = 0; h < 6; h++) { let x = 1.5 + h * 1.3, y = 7; const alto = 3 + Math.floor(r() * 4), lado = r() < 0.5 ? -1 : 1; for (let s = 0; s < alto; s++) { L.px(x, y, C[Math.min(3, Math.floor((s / alto) * 4))]); y--; if (s > 1 && r() < 0.5) x += lado; } } return L.contorno('#0e0c08', 0.7); })());
  def('dc_vela', rej({ w: '#d8ccb0', W: '#a89a7e', f: '#ffd070', F: '#ff8a30', k: '#3a3020' }, `
    .f.
    fFf
    .k.
    wWW
    wWW
    wWW
    WWk`).contorno('#0c0a08', 0.6));
  def('dc_craneo', rej({ a: HUESO_C[1], b: HUESO_C[2], c: HUESO_C[3], d: HUESO_C[4], k: '#0c0a0e' }, `
    .bccb.
    bcddcb
    bkcbkb
    abccba
    .bkkb.
    ..aa..`).contorno('#0c0a08', 0.65));
  def('ad_charco', rej({ a: '#0a100f', b: '#14201e', c: '#2a3c38', d: '#3e5450' }, `
    ..aaaaa...
    .abbbbbaa.
    abbcdbbbba
    .abbbbcbba
    ..aaabbaa.`));
  def('ad_hojas_secas', rej({ a: '#3a2a18', b: '#5a4024', c: '#7a5a32', d: '#2a1e12' }, `
    .bc....a.
    bccd..ab.
    .bd..bcc.
    ...d.cbd.
    .ab...d..`));

  // ---- iconos
  const C = (L) => L.contorno();
  const cubo = (R) => { const L = new Lienzo(10, 10); L.rect(0, 0, 10, 5, R[3]); L.hl(0, 0, 10, R[4]); L.rect(0, 5, 10, 5, R[2]); L.hl(0, 9, 10, R[1]); L.px(2, 2, R[5] || R[4]); L.px(6, 7, R[1]); return C(L); };
  def('i_bloque_turba', cubo(RAMPA.turba)); def('i_bloque_raiz_muerta', cubo(RAMPA.raiz_muerta)); def('i_bloque_velada', cubo(RAMPA.velada)); def('i_bloque_piedra_gris', cubo(RAMPA.piedra_gris));
  const losaI = (tex) => { const t = dibujoHecho(tex), L = new Lienzo(10, 8); for (let y = 0; y < 8; y++) for (let x = 0; x < 10; x++) L.px(x, y, t.get(x + 3, y + 4)); return C(L); };
  def('i_suelo_calzada', losaI('s_calzada_0')); def('i_suelo_mosaico', losaI('s_mosaico_0')); def('i_suelo_turbera', losaI('s_turbera_1'));
  const mineralI = (P) => C(rej({ ...P, p: '#5d6070', P: '#7f8392', q: '#a5a9b6' }, `
    ...qqPP...
    .qPPJHPPp.
    qPJHDPPPpp
    PPHDPPJHpp
    PPPPPPHDPp
    .pPJHPPPp.
    ..ppppppp.`));
  const lingoteI = (P) => C(rej(P, `
    ...JJJJJJ.
    ..JJJJJJHD
    .HHHHHHHDD
    HHHHHHHDD.
    DDDDDDDD..`));
  const MED = { J: '#fff2da', H: '#dccca8', D: '#9a8668' }, PLA = { J: '#ffffff', H: '#bcc8da', D: '#6a7688' };
  def('i_mineral_medula', (() => { const L = mineralI(MED); L.px(4, 2, '#c8302a'); L.px(7, 4, '#c8302a'); return L; })());
  def('i_lingote_medula', (() => { const L = lingoteI(MED); L.hl(3, 3, 4, '#c8b896'); L.px(5, 3, '#a8302a'); return L; })());
  def('i_mineral_plata', mineralI(PLA)); def('i_lingote_plata', lingoteI(PLA));
  def('i_madera_muerta', C(rej({ W: '#6a5e54', e: '#4c443c', d: '#2a2420' }, `
    ....WWWWWee.
    WWWWWWWWeWWe
    WdWWWWdWWeed
    dddWddddddd.`)));
  def('i_savia_negra', C(new Lienzo(8, 10).esfera(4, 6, 3.4, 3.6, ['#06060a', '#14141e', '#24243a', '#4a4a6a'], { brillo: '#9a9ac8' }).rect(3, 0, 2, 3, '#24243a')));
  def('i_espora_podre', C(new Lienzo(9, 9).esfera(4.5, 4.5, 3.8, 3.8, ['#140912', '#2a1224', '#3a1a30', '#4a223e']).px(3, 3, PODRE_C[3]).px(5, 5, PODRE_C[2]).px(6, 3, PODRE_C[3]).px(3, 6, PODRE_C[1])));
  def('i_pagina_velada', C(rej({ p: '#d8ccb0', P: '#b0a488', k: '#4a4050', s: '#8a7a9a' }, `
    pppppp.
    pkkkpPp
    pppppPP
    pkkpkPp
    pppppPp
    pkkkkPp
    ppsppPp
    .pppppP`)));
});
