'use strict';
// Suelos como los de Core Keeper (ESTUDIO-CK.md, «tileset_<Bioma>_ground»): un color base claro y tranquilo, una trama
// fina en diagonal de un tono algo más claro (un ajedrezado que solo sale en trazos cortos), algún brillo suelto y, donde
// el suelo da a una pared, un borde oscuro de 2 px con goterones (raíces) hacia dentro y alguna piedrecita clara al
// lado. Cinco colores por bioma. Lo dibujamos nosotros con esa construcción; los colores son los nuestros.
// Los suelos hechos a mano (losas, tablas, alfombras…) conservan su dibujo y solo llevan el borde junto a las paredes.

const SUELOS_CK = (() => {
  // [base, trama, brillo, borde, borde oscuro]
  const PAL = {
    tierra: ['#a87454', '#b6825f', '#d6a684', '#76482c', '#54321e'],
    cesped: ['#4e7a3a', '#5a8844', '#84b05e', '#2e4e22', '#203a18'],
    arcilla: ['#ac5e3c', '#ba6c48', '#dc9670', '#7a3a22', '#582616'],
    piedra: ['#5c6680', '#68738e', '#8a96b0', '#3c4458', '#2a3040'],
    arena: ['#ac8858', '#b99666', '#d8bc8a', '#785832', '#584024'],
    grava: ['#625f5a', '#6e6b64', '#8e8a80', '#3e3c38', '#2a2826'],
    barro: ['#5e4e2e', '#6a5a36', '#8a7a4e', '#3c3018', '#2a2010'],
    hierba: ['#3c7844', '#48864e', '#76b66e', '#24502c', '#183a20'],
    musgo: ['#466a3a', '#527842', '#6e9858', '#2a4424', '#1c3018'],
    tierra_selva: ['#6c4c30', '#7a583a', '#9c7854', '#46301c', '#302012'],
    flores: ['#3c7844', '#48864e', '#76b66e', '#24502c', '#183a20'],
    moho: ['#2c4c4a', '#365a58', '#507c76', '#1a302e', '#10201e'],
    veneno: ['#402c4c', '#4c365a', '#6c4e7c', '#2a1c32', '#1a1020'],
    lodo: ['#3c3626', '#463f2c', '#605842', '#242010', '#18140a'],
    turbera: ['#40301f', '#4c3a26', '#685238', '#2a1e10', '#1a1208'],
    podre: ['#2c1c2e', '#362138', '#502e4e', '#1c101e', '#120812'],
    ceniza: ['#4c4844', '#58544e', '#726c64', '#302d2a', '#201e1c'],
  };
  const NATURALES = Object.keys(PAL);
  // suelos con dibujo propio: solo el borde junto a la pared (negro con transparencia, sobre su dibujo)
  const HECHOS = ['colmena', 'ruina', 'losa', 'alfombra', 'alfombra_verde', 'arada', 'madera', 'tablas', 'templo', 'mosaico', 'calzada', 'cripta', 'placa'];
  const NEGRO = ['#0000004c', '#00000080'];

  function sueloCK(arte, v) {
    const [base, trama, brillo, borde] = PAL[arte], r = azar(5100 + v * 97 + arte.length * 131);
    const L = new Lienzo(16, 16).rect(0, 0, 16, 16, base);
    // la trama: un ajedrezado que solo se enciende en celdas en diagonal (2 de ancho a lo largo de «/», 4 de largo)
    const enc = Array.from({ length: 8 * 4 }, () => r() < 0.42);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      if ((x + y) % 2 === 0) continue;
      const a = ((x + y) % 16) >> 1, b = (((x - y) % 16) + 16) % 16 >> 2;
      if (enc[a * 4 + b]) L.px(x, y, trama);
    }
    // brillos sueltos (guijarros finos)
    for (let k = 0; k < 1 + (v % 2); k++) L.px(entre(r, 0, 15), entre(r, 0, 15), brillo);
    // una grieta de vez en cuando, del color del borde (como las suyas)
    if (v === 3) { let x = entre(r, 3, 12), y = entre(r, 3, 12); const dx = r() < 0.5 ? 1 : -1; for (let s = 0; s < 6; s++) { L.pxc(x, y, borde); if (s === 2) { L.pxc(x + dx, y + 1, borde); } x += r() < 0.6 ? dx : 0; y++; } }
    // lo propio de algunos
    if (arte === 'flores') for (let k = 0; k < 3; k++) { const x = entre(r, 1, 14), y = entre(r, 1, 14), c = ['#e86a8a', '#f0d060', '#8ab0f0'][(k + v) % 3]; L.px(x, y, c); L.pxc(x + 1, y, mezcla(c, '#ffffff', 0.4)); L.pxc(x, y + 1, PAL.flores[3]); }
    if ((arte === 'cesped' || arte === 'hierba') && v % 2 === 0) for (let k = 0; k < 2; k++) { const x = entre(r, 1, 14), y = entre(r, 2, 14); L.px(x, y, brillo); L.pxc(x + 1, y - 1, brillo); L.pxc(x + 2, y, trama); }
    if (arte === 'podre' && v === 1) { const x = entre(r, 3, 12), y = entre(r, 3, 12); L.pxc(x, y, '#a0206e'); L.pxc(x + 1, y, '#4a1238'); L.pxc(x, y + 1, '#3a0f30'); }
    if (arte === 'moho' && v === 2) { const x = entre(r, 3, 12), y = entre(r, 3, 12); L.pxc(x, y, '#3ac0a0'); L.pxc(x + 1, y + 1, '#2a8a74'); }
    return L;
  }

  // el borde junto a la pared: lado n (pared al norte), s, e, o; esquinas de fuera no, ne, se, so
  function pared(cols, lado, v) {
    const [borde, osc, claro] = cols, r = azar(5300 + v * 61 + lado.charCodeAt(0) * 7 + (lado.charCodeAt(1) || 0));
    const L = new Lienzo(16, 16);
    if (lado.length === 2) { // esquina de fuera: solo el pico
      const fx = lado.includes('o') ? (x) => x : (x) => 15 - x, fy = lado.includes('n') ? (y) => y : (y) => 15 - y;
      L.px(fx(0), fy(0), osc); L.px(fx(1), fy(0), osc); L.px(fx(0), fy(1), osc); L.px(fx(1), fy(1), borde); L.px(fx(2), fy(0), borde); L.px(fx(0), fy(2), borde);
      return L;
    }
    // en coordenadas del lado norte: (t a lo largo, p hacia dentro); luego se gira
    const pon = (t, p, c) => { const [x, y] = lado === 'n' ? [t, p] : lado === 's' ? [t, 15 - p] : lado === 'o' ? [p, t] : [15 - p, t]; L.px(x, y, c); };
    let ant = 0;
    for (let t = 0; t < 16; t++) {
      pon(t, 0, osc); pon(t, 1, borde);
      // goterones: la mayoría cortos, alguno largo, pegados unos a otros para que parezcan raíces
      let g = r() < 0.45 ? 0 : r() < 0.6 ? 1 : r() < 0.7 ? 2 : 3;
      if (ant >= 2 && g === 0) g = 1;
      for (let p = 2; p < 2 + g; p++) pon(t, p, p === 1 + g && g > 1 ? osc : borde);
      ant = g;
      if (g === 0 && claro && r() < 0.12) pon(t, 3, claro);
    }
    return L;
  }

  function construir() {
    for (const arte of NATURALES) for (let v = 0; v < 4; v++) { quitarDibujo(`s_${arte}_${v}`); def(`s_${arte}_${v}`, sueloCK(arte, v)); }
    // las transiciones entre suelos (borde_<arte>_<lado>) se rehacen con las texturas nuevas
    if (typeof bordesSuelo === 'function') {
      const lista = Object.keys(PRIORIDAD_SUELO);
      for (const a of lista) for (const l of ['n', 'e', 's', 'o', 'no', 'ne', 'se', 'so']) quitarDibujo(`borde_${a}_${l}`);
      bordesSuelo(lista);
    }
    for (const arte of NATURALES.concat(HECHOS)) {
      const p = PAL[arte], cols = p ? [p[3], p[4], p[2]] : [NEGRO[0], NEGRO[1], null];
      for (const lado of ['n', 's', 'e', 'o']) for (let v = 0; v < 2; v++) def(`pared_${arte}_${lado}_${v}`, pared(cols, lado, v));
      for (const lado of ['no', 'ne', 'se', 'so']) def(`pared_${arte}_${lado}`, pared(cols, lado, 0));
    }
  }
  CONSTRUIR_MODELOS.push(construir);
  return { PAL, sueloCK, pared };
})();
