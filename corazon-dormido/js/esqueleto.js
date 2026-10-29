'use strict';
// Esqueleto 2D: anima nuestro pixel art con el movimiento de animaciones profesionales CC0 (anim-humano.js). Del 3D
// solo se usa dónde está cada hueso en cada cuadro; lo que se ve lo dibujamos aquí, a la escala de píxel del juego:
//   - las piernas, los brazos, las manos y el torso se rasterizan en cada postura como cápsulas de píxeles (con luz
//     arriba a la izquierda, sombra abajo a la derecha y contorno del tono más oscuro de cada material), así nunca
//     se deforman como un dibujo girado;
//   - la cabeza es un dibujo a mano (mano.js) que se pone en el hueso de la cabeza y solo se gira a 90° (al caer);
//   - el orden de dibujo sale de la profundidad de cada pieza (un brazo pasa por delante o por detrás del cuerpo);
//   - se guarda dónde está cada mano y hacia dónde apunta (para dibujar el arma o la antorcha que lleva).

const ESQ = (() => {
  const ELEV = (28 * Math.PI) / 180, CE = Math.cos(ELEV), SE = Math.sin(ELEV);
  const PX = 13; // píxeles por unidad del esqueleto (el personaje mide unos 26 px)
  const W = 48, H = 46, OX = 24, OY = 42; // el cuadro; los pies en (OX, OY)

  // ---------------------------------------------------------------- huesos de un cuadro, proyectados
  function pose(A, clip, i) {
    const c = A.clips[clip], f = c.c[i % c.c.length], out = {};
    A.huesos.forEach((h, k) => { out[h] = [f[k * 3] / 1000, f[k * 3 + 1] / 1000, f[k * 3 + 2] / 1000]; });
    const s = c.s[i % c.s.length];
    out._arribaI = [s[0] / 1000, s[1] / 1000, s[2] / 1000];
    out._arribaD = [s[3] / 1000, s[4] / 1000, s[5] / 1000];
    return out;
  }
  // dir: ab (mira a la cámara), ar (de espaldas), de (mira a la derecha). Devuelve x a la derecha, y hacia arriba
  // (en px, desde los pies) y z (hacia la cámara).
  function proyectar([x, y, z], dir) {
    if (dir === 'ar') { x = -x; z = -z; } else if (dir === 'de') { const nx = z; z = -x; x = nx; }
    return [x * PX, (y * CE - z * SE) * PX, (y * SE + z * CE) * PX];
  }
  const vector = ([x, y, z], dir) => { const a = proyectar([x, y, z], dir), b = proyectar([0, 0, 0], dir); return [a[0] - b[0], a[1] - b[1], a[2] - b[2]]; };

  // ---------------------------------------------------------------- rasterizar
  // Un cuadro en construcción: color, pieza (para las líneas entre piezas) y orden de cada píxel.
  class Lamina {
    constructor() { this.c = new Array(W * H).fill(null); this.p = new Int16Array(W * H).fill(-1); this.r = new Array(W * H).fill(null); }
    pon(x, y, color, pieza, rampa) {
      x = Math.round(x); y = Math.round(y);
      if (x < 0 || y < 0 || x >= W || y >= H) return;
      const k = y * W + x; this.c[k] = color; this.p[k] = pieza; this.r[k] = rampa;
    }
  }
  // Cápsula de a a b (px de pantalla, y hacia abajo) de radio r, con la rampa [borde, sombra, medio, luz].
  function capsula(L, a, b, r, rampa, pieza, o = {}) {
    const x0 = Math.floor(Math.min(a[0], b[0]) - r - 1), x1 = Math.ceil(Math.max(a[0], b[0]) + r + 1);
    const y0 = Math.floor(Math.min(a[1], b[1]) - r - 1), y1 = Math.ceil(Math.max(a[1], b[1]) + r + 1);
    const dx = b[0] - a[0], dy = b[1] - a[1], l2 = dx * dx + dy * dy || 1;
    for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
      const px = x + 0.5 - a[0], py = y + 0.5 - a[1], t = Math.max(0, Math.min(1, (px * dx + py * dy) / l2));
      const qx = px - dx * t, qy = py - dy * t, d = Math.hypot(qx, qy), rr = r + (o.r1 !== undefined ? (o.r1 - r) * t : 0);
      if (d > rr) continue;
      // luz de arriba a la izquierda: el lado que mira hacia (-1,-1) se aclara, el contrario se oscurece
      const lado = d > 0.01 ? (-qx - qy) / (d * Math.SQRT2) : 0, borde = d / Math.max(0.5, rr);
      let k = 2;
      if (lado > 0.35 && borde > 0.35) k = 3;
      else if (lado < -0.3 && borde > 0.45) k = 1;
      L.pon(x, y, rampa[k], pieza, rampa);
    }
  }
  function circulo(L, c, r, rampa, pieza) { capsula(L, c, c, r, rampa, pieza); }
  // Pinta una rejilla de letras con su paleta (pal: letra → color; rampas: letra → rampa para el contorno).
  function rejilla(L, g, pal, x0, y0, pieza, rampaDe, rot90 = 0, voltear = false) {
    let filas = typeof g === 'string' ? MANO.rejilla(g) : g;
    const w = Math.max(...filas.map((f) => f.length));
    filas = filas.map((f) => f.padEnd(w, '.'));
    if (voltear) filas = filas.map((f) => [...f].reverse().join(''));
    for (let k = 0; k < rot90; k++) { // un cuarto de vuelta en el sentido del reloj
      const h = filas.length, ww = filas[0].length, n = [];
      for (let x = 0; x < ww; x++) { let f = ''; for (let y = h - 1; y >= 0; y--) f += filas[y][x]; n.push(f); }
      filas = n;
    }
    for (let y = 0; y < filas.length; y++) for (let x = 0; x < filas[y].length; x++) {
      const ch = filas[y][x];
      if (ch === '.' || ch === ' ' || !pal[ch]) continue;
      L.pon(x0 + x, y0 + y, pal[ch], pieza, rampaDe(ch));
    }
    return { w: filas[0].length, h: filas.length };
  }
  // Termina el cuadro: líneas entre piezas (lo de detrás se oscurece donde toca lo de delante) y contorno de fuera
  // con el tono más oscuro del material vecino (sin negro). Devuelve un Lienzo.
  function terminar(L, orden) {
    const out = L.c.slice();
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const k = y * W + x, p = L.p[k];
      if (p < 0) continue;
      for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
        const xx = x + dx, yy = y + dy;
        if (xx < 0 || yy < 0 || xx >= W || yy >= H) continue;
        const q = L.p[yy * W + xx];
        if (q >= 0 && q !== p && orden[q] > orden[p] && L.r[k] && !L.r[k].sinLinea) { out[k] = L.r[k][0]; break; }
      }
    }
    const lz = new Lienzo(W, H);
    lz.d = out;
    const fin = lz.copia ? lz.copia() : lz;
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      if (lz.d[y * W + x]) continue;
      let vec = -1;
      for (const [dx, dy] of [[0, 1], [1, 0], [-1, 0], [0, -1]]) {
        const xx = x + dx, yy = y + dy;
        if (xx < 0 || yy < 0 || xx >= W || yy >= H) continue;
        if (lz.d[yy * W + xx]) { vec = yy * W + xx; break; }
      }
      if (vec >= 0) fin.d[y * W + x] = L.r[vec] ? L.r[vec][0] : '#1a1018';
    }
    return fin;
  }

  return { pose, proyectar, vector, Lamina, capsula, circulo, rejilla, terminar, W, H, OX, OY, PX };
})();

// ---------------------------------------------------------------- el Despierto con el esqueleto
const PJ_ESQ = (() => {
  // qué clip usa cada animación del juego y cuántos cuadros (se reparten por todo el clip)
  const ANIMS = {
    q: ['Idle', 6], a: ['Walking_A', 8], c: ['Running_A', 8], g: ['1H_Melee_Attack_Chop', 6], h: ['1H_Melee_Attack_Slice_Horizontal', 6],
    p: ['2H_Melee_Attack_Chop', 7], r: ['Hit_A', 4], m: ['Death_A', 6], u: ['Use_Item', 6], l: ['Throw', 6], d: ['2H_Ranged_Shoot', 6], e: ['Dodge_Forward', 4],
  };
  const PIEZAS = 20;
  function cuadro(dir, anim, f, asp, eq) {
    const [clip, n] = ANIMS[anim] || ANIMS.q, A = ANIM_HUMANO, c = A.clips[clip];
    const i = Math.min(c.c.length - 1, Math.floor((f / n) * c.c.length));
    const P = ESQ.pose(A, clip, i), S = {};
    // retargeting: el modelo de las animaciones tiene el cuerpo más ancho que el nuestro; los brazos y las piernas
    // se acercan al centro del cuerpo (en el ancho, no en la profundidad) para que no queden separados
    for (const k of ['upperarml', 'lowerarml', 'handl', 'handslotl', 'upperarmr', 'lowerarmr', 'handr', 'handslotr']) {
      const c0 = P.chest, q = P[k], hombro = k.endsWith('l') ? P.upperarml : P.upperarmr;
      const f = 0.72;
      P[k] = [c0[0] + (hombro[0] - c0[0]) * 0.8 + (q[0] - hombro[0]) * f, q[1], q[2]];
    }
    for (const k of ['upperlegl', 'lowerlegl', 'footl', 'toesl', 'upperlegr', 'lowerlegr', 'footr', 'toesr']) P[k] = [P.hips[0] + (P[k][0] - P.hips[0]) * 0.85, P[k][1], P[k][2]];
    for (const k of Object.keys(P)) if (!k.startsWith('_')) { const q = ESQ.proyectar(P[k], dir); S[k] = [ESQ.OX + q[0], ESQ.OY - q[1], q[2]]; }
    const pal = PALETA_PJ(asp, eq);
    const R = pal.rampas;
    const L = new ESQ.Lamina(), piezas = [];
    const ancho = dir === 'de' ? 0.75 : 1;
    // piezas: [profundidad, función de dibujo]
    const pierna = (lado) => {
      const u = S['upperleg' + lado], m = S['lowerleg' + lado], p = S['foot' + lado], t = S['toes' + lado];
      piezas.push([(u[2] + m[2]) / 2 - 0.2, (id) => ESQ.capsula(L, u, m, 1.7, R.pant, id)]);
      piezas.push([(m[2] + p[2]) / 2 - 0.1, (id) => ESQ.capsula(L, m, p, 1.5, R.pant, id)]);
      piezas.push([(p[2] + t[2]) / 2, (id) => ESQ.capsula(L, [p[0], p[1] + 0.5], [t[0], t[1] + 0.3], 1.7, R.bota, id)]);
    };
    const brazo = (lado) => {
      const u = S['upperarm' + lado], m = S['lowerarm' + lado], h = S['hand' + lado];
      piezas.push([(u[2] + m[2]) / 2 + 0.1, (id) => ESQ.capsula(L, u, m, 1.35, R.tunica, id)]);
      piezas.push([(m[2] + h[2]) / 2 + 0.2, (id) => ESQ.capsula(L, m, h, 1.25, R.tunica, id, { r1: 1.1 })]);
      piezas.push([h[2] + 0.3, (id) => ESQ.circulo(L, [h[0], h[1]], 1.3, R.piel, id)]);
    };
    pierna('l'); pierna('r'); brazo('l'); brazo('r');
    // el torso: caderas → pecho, más ancho en los hombros; el cinto en las caderas
    const hi = S.hips, sp = S.spine, ch = S.chest, zT = (hi[2] + ch[2]) / 2 - 0.4;
    piezas.push([zT, (id) => {
      ESQ.capsula(L, [hi[0], hi[1] - 1], [sp[0], sp[1]], 3.2 * ancho, R.tunica, id);
      ESQ.capsula(L, [sp[0], sp[1]], [ch[0], ch[1] + 1], 3.6 * ancho, R.tunica, id, { r1: 3.4 * ancho });
      // cinto: una fila de cuero a la altura de las caderas
      for (let x = -3; x <= 3; x++) { const xx = Math.round(hi[0] + x * ancho); L.pon(xx, Math.round(hi[1] - 1), x === 0 && dir !== 'ar' ? pal.hebilla : R.cinto[x < 0 ? 2 : 1], id, R.cinto); }
      // la mochila de espaldas
      if (dir === 'ar') for (let y = -1; y <= 3; y++) for (let x = -2; x <= 2; x++) L.pon(ch[0] + x, ch[1] + y + 1, R.mochila[Math.abs(x) === 2 || y === 3 ? 1 : y === -1 ? 3 : 2], id, R.mochila);
      if (dir === 'de') for (let y = -1; y <= 3; y++) for (let x = -3; x <= -2; x++) L.pon(ch[0] + x, ch[1] + y + 1, R.mochila[x === -3 || y === 3 ? 1 : 2], id, R.mochila);
    }]);
    // la bufanda en el cuello
    piezas.push([ch[2] + 0.5, (id) => {
      for (let x = -3; x <= 3; x++) L.pon(Math.round(ch[0] + x * ancho), Math.round(ch[1]), R.bufanda[x < -1 ? 3 : x > 1 ? 1 : 2], id, R.bufanda);
      if (dir === 'ab') L.pon(Math.round(ch[0] + 2), Math.round(ch[1] + 1), R.bufanda[1], id, R.bufanda);
    }]);
    // la cabeza (dibujo a mano) en el hueso de la cabeza; al caer se gira un cuarto de vuelta
    const he = S.head, eje = [he[0] - ch[0], he[1] - ch[1]], ang = Math.atan2(eje[0], -eje[1]);
    const rot = Math.abs(ang) > 1.0 ? (ang > 0 ? 1 : 3) : 0;
    piezas.push([he[2] + 2, (id) => {
      const g = PJ_MANO.cabeza(dir, asp, eq);
      const tam = { w: Math.max(...MANO.rejilla(g).map((f) => f.length)), h: MANO.rejilla(g).length };
      const w = rot % 2 ? tam.h : tam.w, h = rot % 2 ? tam.w : tam.h;
      // la base de la cabeza (el cuello) sobre el hueso
      let x0 = Math.round(he[0] - w / 2), y0 = Math.round(he[1] - h + 2);
      if (rot === 1) { x0 = Math.round(he[0] - 2); y0 = Math.round(he[1] - h / 2); }
      if (rot === 3) { x0 = Math.round(he[0] - w + 2); y0 = Math.round(he[1] - h / 2); }
      ESQ.rejilla(L, g, pal.letras, x0, y0, id, (k) => pal.rampaDeLetra[k], rot);
    }]);
    piezas.sort((a, b) => a[0] - b[0]);
    const orden = new Int16Array(PIEZAS + 4);
    piezas.forEach(([, dibujar], k) => { orden[k] = k; dibujar(k); });
    const lz = ESQ.terminar(L, orden);
    MANO.normales(lz);
    // dónde están las manos (px del dibujo) y hacia dónde apunta lo que llevan
    const manos = {};
    for (const [n, h, up] of [['D', 'handslotr', P._arribaD], ['I', 'handslotl', P._arribaI]]) {
      const v = ESQ.vector(up, dir), s = S[h];
      manos[n] = { x: s[0], y: s[1], ang: Math.atan2(v[0], v[1]), delante: s[2] > zT };
    }
    return { color: lz, dx: 0, manos, pie: [ESQ.OX, ESQ.OY] };
  }
  return { cuadro, ANIMS };
})();

// Paleta del personaje por papeles (rampas de 4: borde, sombra, medio, luz) y las letras de la cabeza.
function PALETA_PJ(asp, eq) {
  const r4 = (c, o) => { const r = ESCU.rampa(c, 5, o); return [r[0], r[1], r[2], r[3]]; };
  const pelo = ESCU.rampa(ASPECTO.pelo[asp.pelo] || ASPECTO.pelo[0], 5, { giro: 18, oscuro: 0.2 });
  const piel = r4(ASPECTO.piel[asp.piel] || ASPECTO.piel[0], { giro: 10, oscuro: 0.45 });
  let tunica = r4(ASPECTO.ropa[asp.ropa] || ASPECTO.ropa[0], { giro: 16, oscuro: 0.3 });
  let pant = ['#140e1e', '#221a34', '#3e325c', '#5a4c7c'], bota = ['#140a06', '#2e1a10', '#6e4228', '#9a6236'];
  const metal = (mt) => { const r = mt.r, n = r.length; return [r[0], r[Math.round(n * 0.3)], r[Math.round(n * 0.55)], r[Math.round(n * 0.85)]]; };
  if (eq && eq.peto) tunica = metal(eq.peto);
  if (eq && eq.botas) { bota = metal(eq.botas); pant = metal(eq.botas); }
  const rampas = {
    piel, tunica, pant, bota, cinto: ['#1e0e08', '#3e2416', '#5e3a22', '#7e5432'], bufanda: ['#3a0a12', '#6a1422', '#cc3440', '#ee6a5a'],
    mochila: ['#2a1408', '#5a3420', '#8e5a34', '#b07a48'], pelo: [pelo[0], pelo[1], pelo[2], pelo[3]],
  };
  const letras = {
    o: pelo[0], H: pelo[1], h: pelo[2], g: pelo[3], G: pelo[4], S: piel[1], s: piel[2], b: ESCU.mezclar(piel[2], '#e85a64', 0.38),
    E: '#1a1830', e: '#3a9a8c', w: '#f4f2ff',
  };
  const rampaDeLetra = (k) => ('oHhgG'.includes(k) ? rampas.pelo : 'Ssb'.includes(k) ? rampas.piel : rampas.piel);
  if (eq && eq.casco) { const r = metal(eq.casco); Object.assign(letras, { c: r[0], C: r[1], k: r[2], K: r[3], X: ESCU.mezclar(r[3], '#ffffff', 0.4) }); }
  return { rampas, letras, rampaDeLetra, hebilla: '#f0c040' };
}
