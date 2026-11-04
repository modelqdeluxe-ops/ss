'use strict';
// El arma en la mano como en Core Keeper (ESTUDIO-CK.md: «sword_copper», «pickaxe_scarlet»…): el dibujo del arma no se
// gira libremente (eso rompe los píxeles); se lleva dibujado en posiciones fijas y, en lo más rápido del golpe, se
// pone un barrido: un abanico del color del arma. Aquí cada arma o herramienta que se lleva en la mano tiene 8
// posiciones (cada 45°) hechas a nivel de píxel al construir el arte: las de su misma diagonal, girando 90° sin
// perder nada; las otras, con un giro de 45° hecho a 8 aumentos con suavizado de píxel (como RotSprite). Y 8×3×2
// abanicos (empiezan en cada posición, abarcan 45°, 90° o 135°, en un sentido u otro) que se tiñen con su color.

const ARMAS_PX = (() => {
  // ---- giros sin pérdida y el giro de 45°
  function rot90(L) { // 90° en el sentido de las agujas del reloj (en pantalla)
    const o = new Lienzo(L.h, L.w);
    for (let y = 0; y < L.h; y++) for (let x = 0; x < L.w; x++) o.d[x * o.w + (L.h - 1 - y)] = L.d[y * L.w + x];
    return o;
  }
  function epx(L) { // escala 2× con suavizado de esquinas (EPX / Scale2x)
    const o = new Lienzo(L.w * 2, L.h * 2), g = (x, y) => (x < 0 || y < 0 || x >= L.w || y >= L.h ? null : L.d[y * L.w + x]);
    for (let y = 0; y < L.h; y++) for (let x = 0; x < L.w; x++) {
      const P = g(x, y), A = g(x, y - 1), B = g(x + 1, y), C = g(x - 1, y), D = g(x, y + 1);
      let p1 = P, p2 = P, p3 = P, p4 = P;
      if (C === A && C !== D && A !== B) p1 = A;
      if (A === B && A !== C && B !== D) p2 = B;
      if (D === C && D !== B && C !== A) p3 = C;
      if (B === D && B !== A && D !== C) p4 = D;
      o.d[(y * 2) * o.w + x * 2] = p1; o.d[(y * 2) * o.w + x * 2 + 1] = p2; o.d[(y * 2 + 1) * o.w + x * 2] = p3; o.d[(y * 2 + 1) * o.w + x * 2 + 1] = p4;
    }
    return o;
  }
  // gira a (radianes, en pantalla: positivo = agujas del reloj); devuelve el lienzo y la función que lleva un punto
  function rotsprite(L, a) {
    const G = epx(epx(epx(L))), k = 8, S = Math.ceil(Math.hypot(L.w, L.h)) + 2, o = new Lienzo(S, S);
    const cx = L.w / 2, cy = L.h / 2, c = Math.cos(-a), s = Math.sin(-a);
    for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
      const u = x + 0.5 - S / 2, v = y + 0.5 - S / 2; // centro de este píxel, relativo al centro
      const sx = (u * c - v * s + cx) * k, sy = (u * s + v * c + cy) * k;
      const ix = Math.floor(sx), iy = Math.floor(sy);
      if (ix < 0 || iy < 0 || ix >= G.w || iy >= G.h) continue;
      o.d[y * S + x] = G.d[iy * G.w + ix];
    }
    const lleva = ([px, py]) => { const u = px - cx, v = py - cy, cc = Math.cos(a), ss = Math.sin(a); return [u * cc - v * ss + S / 2, u * ss + v * cc + S / 2]; };
    return { L: o, lleva };
  }
  const rotPunto = (p, L) => [L.h - p[1], p[0]]; // el punto tras rot90 (coordenadas continuas)

  // 8 posiciones de un dibujo: k = 0 arriba, 1 arriba-derecha, … 7 arriba-izquierda (cada 45°, agujas del reloj)
  // base: posición en la que está dibujado (1: los iconos en diagonal; 0: los derechos, como el arco); agarre: punto
  function posiciones(L, base, agarre) {
    const out = new Array(8);
    let cur = { L, g: agarre };
    for (let m = 0; m < 4; m++) { out[(base + 2 * m) % 8] = cur; cur = { L: rot90(cur.L), g: rotPunto(cur.g, cur.L) }; }
    const r = rotsprite(L, -Math.PI / 4); // la de al lado (45° hacia atrás)
    cur = { L: r.L, g: r.lleva(agarre) };
    for (let m = 0; m < 4; m++) { out[(base + 7 + 2 * m) % 8] = cur; cur = { L: rot90(cur.L), g: rotPunto(cur.g, cur.L) }; }
    return out;
  }

  // ---- el abanico del barrido: sector de anillo de 45°, 90° o 135°; claro en el canto de fuera y que se deshace
  // hacia la cola. Blanco y grises: se tiñe con el color del arma.
  const R_AB = 15;
  function abanico(k0, pasos, sentido) {
    const S = R_AB * 2 + 3, L = new Lienzo(S, S), c = S / 2;
    const a0 = -Math.PI / 2 + k0 * Math.PI / 4, span = pasos * Math.PI / 4;
    for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
      const dx = x + 0.5 - c, dy = y + 0.5 - c, r = Math.hypot(dx, dy);
      if (r > R_AB || r < 5) continue;
      let a = Math.atan2(dy, dx) - a0; a = ((a % (2 * Math.PI)) + 3 * Math.PI) % (2 * Math.PI) - Math.PI; // -π..π desde a0
      const t = sentido > 0 ? a / span : -a / span; // 0 en el principio (cola) … 1 en el final (donde está el arma)
      if (t < 0 || t > 1) continue;
      const fuera = R_AB - r;
      if (t < 0.35 && (x + y) % 2) continue; // la cola, deshecha
      if (t < 0.15) continue;
      L.px(x, y, fuera < 1.3 ? '#ffffff' : fuera < 3 ? '#dcdcdc' : r < 7 ? '#8c8c8c' : '#b4b4b4');
    }
    return L;
  }
  // el color medio de la hoja (los tonos claros del dibujo, que son los del metal)
  function colorDe(L) {
    const cs = L.d.filter(Boolean).map((h) => hexRgb(h.slice(0, 7))).map(([r, g, b]) => [r, g, b, r + g + b]).sort((p, q) => q[3] - p[3]);
    const top = cs.slice(0, Math.max(1, Math.floor(cs.length * 0.4)));
    const m = [0, 1, 2].map((i) => top.reduce((s, c) => s + c[i], 0) / top.length / 255);
    return m;
  }

  // ---- los iconos de las armas y herramientas, a mano (16×16, en diagonal: el mango abajo a la izquierda y la punta
  // arriba a la derecha, como los de Core Keeper). Letras: p o n m el metal (brillo → sombra), x w W la madera del
  // mango, G g la guarda o el remate; el contorno de color lo pone la lámina.
  const ESPADA = [
    '...............p', '..............po', '.............pon', '............pon.', '...........pon..', '..........pon...',
    '.........pon....', '........pon.....', '....G..pon......', '.....Gpon.......', '......G.........', '.....wWg........',
    '....Ww..g.......', '...wW...........', '..xG............', '..G.............'];
  const LANZA = [
    '..............pp', '.............pon', '............pon.', '.............n..', '..........wW....', '.........wW.....',
    '........wW......', '.......wW.......', '......xW........', '.....wW.........', '....wW..........', '...wW...........',
    '..wW............', '.xW.............', '.G..............', '................'];
  const MAZA = [
    '..........pppo..', '.........pooonm.', '........poonnnmm', '........ponnnnmm', '........oonnnmm.', '.........onmmm..',
    '.........Gnmm...', '........wW......', '.......wW.......', '......wW........', '.....xW.........', '....wW..........',
    '...wW...........', '..xW............', '.GG.............', '................'];
  const AZADA = [
    '................', '.........pon....', '..........pon...', '...........pon..', '..........wWpn..', '.........wW..m..',
    '........wW......', '.......wW.......', '......xW........', '.....wW.........', '....wW..........', '...wW...........',
    '..xW............', '.wW.............', '.G..............', '................'];
  // el pico: la cabeza es un arco alrededor de la esquina de abajo a la izquierda (como los suyos)
  function pico() {
    const g = Array.from({ length: 16 }, () => Array(16).fill('.'));
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const dx = x + 0.5 - 3, dy = y + 0.5 - 13, r = Math.hypot(dx, dy), a = Math.atan2(-dy, dx); // a: 0 derecha, π/2 arriba
      if (a < 0.05 || a > 1.55) continue;
      const fin = Math.min(a - 0.05, 1.55 - a), ancho = fin < 0.22 ? 0.9 : 1.9;
      if (Math.abs(r - 10.6) > ancho / 2 + 0.35) continue;
      g[y][x] = r < 10.1 ? 'n' : r < 10.9 ? 'o' : 'p';
    }
    for (let k = 0; k < 9; k++) { const x = 2 + k, y = 13 - k; if (g[y][x] === '.') g[y][x] = k % 3 === 2 ? 'x' : 'w'; if (g[y + 1] && g[y + 1][x] === '.') g[y + 1][x] = 'W'; }
    g[14][1] = 'G'; g[14][2] = 'g';
    return g.map((f) => f.join(''));
  }
  // la caña: vara fina y larga con el carrete y el sedal colgando
  const CANA = [
    '...............w', '..............w.', '.............w.p', '............w..p', '...........w...p', '..........w....p',
    '.........w.....p', '........x......o', '.......w........', '......w.........', '.....w..........', '....GW..........',
    '...GgW..........', '...gW...........', '..xW............', '.W..............'];
  // el arco: derecho (la cuerda a la izquierda, las palas curvas a la derecha)
  const ARCO = [
    '......wx........', '.....p..wx......', '.....p...xw.....', '.....p....xw....', '.....p.....w....', '.....p.....wW...',
    '.....p......W...', '.....p.....GG...', '.....p.....GG...', '.....p......W...', '.....p.....wW...', '.....p.....w....',
    '.....p....xw....', '.....p...xw.....', '.....p..wx......', '......wx........'];
  const ORO = ['#3a2a08', '#7a5418', '#c08a2a', '#ecc04a', '#fff0a0'];
  function paletaArma(metal, madera, guarda) {
    const pal = {};
    ['m', 'n', 'o', 'p'].forEach((c, k) => { pal[c] = [metal[k + 1], metal]; });
    ['W', 'w', 'x'].forEach((c, k) => { pal[c] = [madera[k + 1], madera]; });
    ['g', 'G'].forEach((c, k) => { pal[c] = [guarda[k + 2], guarda]; });
    return pal;
  }
  function dibujarIcono(filas, pal) {
    const r = CRI.lamina(16, 16, (K) => { K.pieza(0, () => K.rej(filas, pal, 0, 0)); });
    return OBJ_PX.recorte(r.color);
  }
  function construirIconos() {
    if (typeof OBJ_PX === 'undefined') return;
    const M = OBJ_PX.MET, MAD = M.madera;
    const METALES = { madera: M.madera, cobre: M.cobre, estano: M.estano, hierro: M.hierro, carmesi: M.carmesi, medula: M.medula, plata: M.plata, espino: ['#0e1a0a', '#2a4a1a', '#4a7a2a', '#7aaa44', '#b4d878'] };
    const GUARDA = { madera: M.madera, cobre: ORO, estano: M.hierro, hierro: ORO, carmesi: ORO, medula: M.medula, plata: M.alma ? ['#0a1a3a', '#1a3a6a', '#4a86cc', '#7ab2f0', '#e8f6ff'] : ORO, espino: ORO };
    const MANGO = { carmesi: M.selva, medula: M.muerta, plata: M.muerta };
    const hacer = (id, filas, mat) => { quitarDibujo('i_' + id); def('i_' + id, dibujarIcono(filas, paletaArma(METALES[mat], MANGO[mat] || MAD, GUARDA[mat] || ORO))); };
    for (const m of ['madera', 'cobre', 'estano', 'hierro', 'carmesi', 'medula', 'plata']) hacer('espada_' + m, ESPADA, m);
    for (const m of ['cobre', 'estano', 'hierro', 'carmesi', 'medula', 'plata']) hacer('pico_' + m, pico(), m);
    hacer('pico_viejo', pico(), 'hierro');
    for (const m of ['cobre', 'hierro', 'carmesi', 'medula']) hacer('maza_' + m, MAZA, m);
    for (const m of ['madera', 'cobre', 'carmesi']) hacer('azada_' + m, AZADA, m);
    hacer('lanza_espino', LANZA, 'espino'); hacer('lanza_plata', LANZA, 'plata');
    for (const m of ['madera', 'cobre', 'carmesi']) hacer('cana_' + m, CANA, m === 'madera' ? 'estano' : m);
    for (const m of ['madera', 'estano', 'carmesi', 'medula', 'plata']) { quitarDibujo('i_arco_' + m); def('i_arco_' + m, dibujarIcono(ARCO, Object.assign(paletaArma(['#3a3020', '#8a8070', '#c8c0a8', '#efe8d8', '#ffffff'], m === 'madera' ? MAD : METALES[m], ORO)))); }
  }

  const POSES = {}, COLOR = {};
  function ultimo(n) { for (let i = DIBUJOS.length - 1; i >= 0; i--) if (DIBUJOS[i][0] === n) return DIBUJOS[i][1]; return null; }
  function construir() {
    construirIconos();
    const TIPOS = ['pico', 'espada', 'maza', 'arco', 'azada', 'cana', 'regadera', 'hacha'];
    // los objetos que se llevan en la mano (sin la lista de objetos, como en el taller: por el nombre del icono)
    const lista = typeof ITEMS !== 'undefined' ? Object.values(ITEMS).filter((it) => TIPOS.includes(it.tipo)).map((it) => ({ icono: it.icono, tipo: it.tipo }))
      : [...new Set(DIBUJOS.map(([n]) => n))].filter((n) => /^i_(espada|pico|maza|arco|azada|cana|regadera|hacha)_/.test(n)).map((n) => ({ icono: n, tipo: n.split('_')[1] }));
    for (const it of lista) {
      const n = it.icono, L = ultimo(n);
      if (!L || POSES[n]) continue;
      const recto = it.tipo === 'arco';
      const ps = posiciones(L, recto ? 0 : 1, recto ? [L.w / 2, L.h / 2] : [3.5, L.h - 3.5]);
      ps.forEach((p, k) => def(`${n}_p${k}`, p.L));
      POSES[n] = ps.map((p) => ({ g: p.g, w: p.L.w, h: p.L.h }));
      COLOR[n] = colorDe(L);
    }
    for (let k = 0; k < 8; k++) for (const pasos of [1, 2, 3]) for (const s of [1, -1]) def(`abanico_${k}_${pasos}_${s > 0 ? 'd' : 'i'}`, abanico(k, pasos, s));
  }
  CONSTRUIR_MODELOS.push(construir);
  return { POSES, COLOR, R_AB, posiciones, rotsprite, rot90, epx };
})();
