'use strict';
// Criaturas y jefes movidos por esqueleto (CHECKPOINT-0.8 §4.3). Como el personaje (esqueleto.js): del 3D solo se usa
// dónde está cada hueso en cada cuadro (animaciones CC0: KayKit para los humanoides, Quaternius para las babas, los
// voladores y Tamar) o un esqueleto hecho aquí (gusanos, tentáculos, la flor); lo que se ve se dibuja aquí, a la escala
// de píxel del juego, con las reglas de Core Keeper (ARTE.md):
//   - cada pieza (pierna, brazo, tronco, cola, ala…) se rasteriza en cada postura con volumen: luz arriba a la
//     izquierda y sombra abajo a la derecha, en 3–5 tonos de la paleta de Ásor (nunca se gira un dibujo: sin dientes);
//   - el contorno es el tono más oscuro del material de al lado (sin negro) y entre piezas, lo de detrás se oscurece
//     donde lo tapa lo de delante;
//   - las caras y las máscaras son dibujos a mano (rejillas) que se ponen en el hueso de la cabeza;
//   - lo que brilla (ojos, Podre, el cebo del Farolero) va también a la capa _luz;
//   - cada píxel guarda su normal de verdad (las antorchas iluminan el lado que toca).
// Cada criatura tiene sus animaciones (q quieto, a andar, p preparar el golpe, g golpe, r golpe recibido, m morir,
// n aparecer y las suyas) con varios cuadros; principal.js (cuadroCriatura) elige el cuadro por estado y tiempo.

const CRI = (() => {
  const ELEV = (28 * Math.PI) / 180, CE = Math.cos(ELEV), SE = Math.sin(ELEV);
  const norm = (v) => { const m = Math.hypot(...v) || 1; return v.map((x) => x / m); };
  const LUZ = norm([-0.52, -0.72, 0.46]); // de arriba a la izquierda y un poco de frente (y hacia abajo en pantalla)
  const sum = (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]], res = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
  const por = (a, k) => [a[0] * k, a[1] * k, a[2] * k];
  const mezcla = (a, b, k) => [a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k];

  // ---------------------------------------------------------------- poses
  // Pose de un clip en u (0..1 de su duración), mezclando los dos cuadros más cercanos. bucle: el final vuelve al
  // principio. Devuelve { hueso: [x, y, z] } (y arriba, el modelo mira hacia +z) y _arribaI/_arribaD si hay manos.
  function pose(A, clip, u, bucle) {
    const c = A.clips[clip];
    if (!c) throw new Error('CRI: no hay clip ' + clip);
    const n = c.c.length, t = bucle ? (((u % 1) + 1) % 1) * n : Math.min(1, Math.max(0, u)) * (n - 1);
    const i0 = Math.floor(t) % n, i1 = bucle ? (i0 + 1) % n : Math.min(n - 1, i0 + 1), k = t - Math.floor(t);
    const a = c.c[i0], b = c.c[i1], P = {};
    A.huesos.forEach((h, j) => { P[h] = [0, 1, 2].map((e) => (a[j * 3 + e] * (1 - k) + b[j * 3 + e] * k) / 1000); });
    if (c.s) {
      const sa = c.s[i0], sb = c.s[i1], s = sa.map((v, e) => (v * (1 - k) + sb[e] * k) / 1000);
      P._arribaI = s.slice(0, 3); P._arribaD = s.slice(3, 6);
    }
    return P;
  }
  // Proporciones de un humanoide de KayKit: cada hueso cuelga de su padre con el mismo giro y otro largo (piernas
  // cortas, brazos largos, cuello…), hombros y caderas más o menos anchos y el tronco encorvado hacia delante.
  const PADRE = {
    spine: 'hips', chest: 'spine', head: 'chest', upperarml: 'chest', lowerarml: 'upperarml', handl: 'lowerarml', handslotl: 'handl',
    upperarmr: 'chest', lowerarmr: 'upperarmr', handr: 'lowerarmr', handslotr: 'handr', upperlegl: 'hips', lowerlegl: 'upperlegl', footl: 'lowerlegl',
    toesl: 'footl', upperlegr: 'hips', lowerlegr: 'upperlegr', footr: 'lowerlegr', toesr: 'footr',
  };
  const ORDEN = ['hips', ...Object.keys(PADRE)];
  const ARRIBA = new Set(['spine', 'chest', 'head', 'upperarml', 'lowerarml', 'handl', 'handslotl', 'upperarmr', 'lowerarmr', 'handr', 'handslotr']);
  function proporciones(P, o) {
    const Q = {}, pierna = o.pierna ?? 1, alto = o.cadera ?? 1.0;
    Q.hips = [P.hips[0], P.hips[1] - (1 - pierna) * alto, P.hips[2]];
    for (const b of ORDEN) {
      if (b === 'hips' || !P[b]) continue;
      const p = PADRE[b];
      let v = res(P[b], P[p]);
      if (b === 'upperarml' || b === 'upperarmr') v = [v[0] * (o.hombros ?? 1), v[1] * (o.hombrosY ?? 1), v[2]];
      else if (b === 'upperlegl' || b === 'upperlegr') v = [v[0] * (o.caderas ?? 1), v[1], v[2]];
      else {
        const k = /leg|foot|toes/.test(b) ? pierna : /arm|hand/.test(b) ? (o.brazo ?? 1) : b === 'head' ? (o.cuello ?? 1) : (o.tronco ?? 1);
        v = por(v, k);
      }
      Q[b] = sum(Q[p], v);
    }
    for (const k of Object.keys(P)) if (k[0] === '_') Q[k] = P[k].slice();
    if (o.encorvar) encorvar(Q, o.encorvar);
    return Q;
  }
  // Gira el tronco (y lo que cuelga de él) hacia delante sobre las caderas.
  function encorvar(Q, a) {
    const c = Math.cos(a), s = Math.sin(a), h = Q.hips;
    const gira = (v) => [v[0], v[1] * c - v[2] * s, v[1] * s + v[2] * c];
    for (const b of ARRIBA) if (Q[b]) Q[b] = sum(h, gira(res(Q[b], h)));
    for (const k of ['_arribaI', '_arribaD']) if (Q[k]) Q[k] = gira(Q[k]);
  }
  // A pantalla: yaw gira el modelo (0 mira a la cámara, π/2 a la derecha); devuelve x a la derecha, y hacia arriba y
  // z hacia la cámara, en px.
  function proyectar([x, y, z], yaw, px) {
    const c = Math.cos(yaw), s = Math.sin(yaw), X = x * c + z * s, Z = -x * s + z * c;
    return [X * px, (y * CE - Z * SE) * px, (y * SE + Z * CE) * px];
  }

  // ---------------------------------------------------------------- la lámina
  class Lam {
    constructor(W, H) {
      this.W = W; this.H = H; const n = W * H;
      this.c = new Array(n).fill(null); this.p = new Int16Array(n).fill(-1); this.r = new Array(n).fill(null);
      this.l = new Uint8Array(n); this.n = new Int32Array(n);
    }
    pon(x, y, color, pieza, rampa, luz, nx = 0, ny = 0, nz = 1) {
      x = Math.floor(x); y = Math.floor(y);
      if (x < 0 || y < 0 || x >= this.W || y >= this.H || !color) return;
      const k = y * this.W + x;
      this.c[k] = color; this.p[k] = pieza; this.r[k] = rampa; this.l[k] = luz ? 1 : 0;
      // normal en el formato del atlas: x a la derecha, y arriba, z hacia la cámara
      this.n[k] = (1 << 24) | (Math.round((nx * 0.5 + 0.5) * 255) << 16) | (Math.round((-ny * 0.5 + 0.5) * 255) << 8) | Math.round((Math.max(0, nz) * 0.5 + 0.5) * 255);
    }
  }
  // Tono de la rampa para una normal (nx, ny en pantalla, y hacia abajo). rampa[0] es la línea; 1..n-1 de sombra a
  // luz. o.mate: menos contraste; o.brillo: el tono más claro solo en el punto de brillo.
  function tono(rampa, nx, ny, nz, o) {
    let I = nx * LUZ[0] + ny * LUZ[1] + nz * LUZ[2];
    if (o) { if (o.mate) I = I * 0.6 + 0.2; if (o.tex) I += o.tex; }
    const n = rampa.length - 1, top = o && o.brillo ? n - 1 : n;
    let u = (I + 0.3) / 1.2;
    u = Math.max(0, Math.min(0.999, u));
    let k = 1 + Math.floor(u * top);
    if (o && o.brillo && I > 0.9) k = n;
    return rampa[Math.min(n, k)];
  }

  // ---------------------------------------------------------------- el cuadro
  // K: lo que recibe el dibujo de cada criatura. Las piezas se apuntan con su profundidad y se pintan de lejos a cerca.
  function contexto(D, L, S, P, anim, f, u) {
    const K = { D, L, S, P, anim, f, u, piezas: [], id: 0 };
    K.pieza = (z, fn) => K.piezas.push([z, fn]);
    K.px = (x, y, color, rampa, luz) => L.pon(x, y, color, K.id, rampa, luz);
    // Cápsula de a a b (px, y abajo) con radio r0 → r1 y volumen. o: { luz, mate, brillo, tex(x, y), corta(x, y) }
    K.cap = (a, b, r0, r1, rampa, o = {}) => {
      r1 = r1 ?? r0;
      const R = Math.max(r0, r1);
      const x0 = Math.floor(Math.min(a[0], b[0]) - R - 1), x1 = Math.ceil(Math.max(a[0], b[0]) + R + 1);
      const y0 = Math.floor(Math.min(a[1], b[1]) - R - 1), y1 = Math.ceil(Math.max(a[1], b[1]) + R + 1);
      const dx = b[0] - a[0], dy = b[1] - a[1], l2 = dx * dx + dy * dy || 1e-6;
      for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
        const px = x + 0.5 - a[0], py = y + 0.5 - a[1], t = Math.max(0, Math.min(1, (px * dx + py * dy) / l2));
        const qx = px - dx * t, qy = py - dy * t, d = Math.hypot(qx, qy), rr = r0 + (r1 - r0) * t;
        if (d > rr || (o.corta && o.corta(x, y))) continue;
        const nx = qx / Math.max(rr, 0.5), ny = qy / Math.max(rr, 0.5), nz = Math.sqrt(Math.max(0, 1 - nx * nx - ny * ny));
        const op = o.tex ? Object.assign({}, o, { tex: o.tex(x, y) }) : o;
        L.pon(x, y, tono(rampa, nx, ny, nz, op), K.id, rampa, o.luz, nx, ny, nz);
      }
    };
    // Tubo por una lista de puntos (px, ya muestreados juntos) con el radio de r0 a r1: cada píxel toma la normal del
    // punto más cercano de la línea, así no quedan costuras entre trozos. o: como en cap.
    K.tubo = (pts, r0, r1, rampa, o = {}) => {
      const n = pts.length, R = Math.max(r0, r1), xs = pts.map((p) => p[0]), ys = pts.map((p) => p[1]);
      const x0 = Math.floor(Math.min(...xs) - R - 1), x1 = Math.ceil(Math.max(...xs) + R + 1), y0 = Math.floor(Math.min(...ys) - R - 1), y1 = Math.ceil(Math.max(...ys) + R + 1);
      for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
        const px = x + 0.5, py = y + 0.5;
        let best = 1e9, bi = 0, bq = null, bt = 0;
        for (let i = 0; i < n - 1; i++) {
          const a = pts[i], b = pts[i + 1], dx = b[0] - a[0], dy = b[1] - a[1], l2 = dx * dx + dy * dy || 1e-6;
          const t = Math.max(0, Math.min(1, ((px - a[0]) * dx + (py - a[1]) * dy) / l2)), qx = px - a[0] - dx * t, qy = py - a[1] - dy * t;
          const d = qx * qx + qy * qy;
          if (d < best) { best = d; bi = i; bq = [qx, qy]; bt = t; }
        }
        if (!bq) continue;
        const rr = r0 + (r1 - r0) * ((bi + bt) / Math.max(1, n - 1)), d = Math.sqrt(best);
        if (d > rr || (o.corta && o.corta(x, y))) continue;
        const nx = bq[0] / Math.max(rr, 0.5), ny = bq[1] / Math.max(rr, 0.5), nz = Math.sqrt(Math.max(0, 1 - nx * nx - ny * ny));
        const op = o.tex ? Object.assign({}, o, { tex: o.tex(x, y) }) : o;
        L.pon(x, y, tono(rampa, nx, ny, nz, op), K.id, rampa, o.luz, nx, ny, nz);
      }
    };
    // Óvalo de centro c y radios rx, ry, girado ang (radianes).
    K.ovalo = (c, rx, ry, rampa, o = {}) => {
      const ang = o.ang || 0, ca = Math.cos(ang), sa = Math.sin(ang), R = Math.max(rx, ry);
      for (let y = Math.floor(c[1] - R - 1); y <= c[1] + R + 1; y++) for (let x = Math.floor(c[0] - R - 1); x <= c[0] + R + 1; x++) {
        const px = x + 0.5 - c[0], py = y + 0.5 - c[1], u = (px * ca + py * sa) / rx, v = (-px * sa + py * ca) / ry;
        const d2 = u * u + v * v;
        if (d2 > 1 || (o.corta && o.corta(x, y))) continue;
        const nx = u * ca - v * sa, ny = u * sa + v * ca, nz = Math.sqrt(Math.max(0, 1 - d2));
        const op = o.tex ? Object.assign({}, o, { tex: o.tex(x, y) }) : o;
        L.pon(x, y, tono(rampa, nx, ny, nz, op), K.id, rampa, o.luz, nx, ny, nz);
      }
    };
    // Polígono relleno (puntos en px). El volumen sale de o.normal(x, y) → [nx, ny] o, si no, de un cilindro
    // vertical (más claro a la izquierda).
    K.poli = (pts, rampa, o = {}) => {
      const ys = pts.map((p) => p[1]), xs = pts.map((p) => p[0]);
      const y0 = Math.floor(Math.min(...ys)), y1 = Math.ceil(Math.max(...ys)), X0 = Math.min(...xs), X1 = Math.max(...xs);
      for (let y = y0; y <= y1; y++) {
        const cy = y + 0.5, cortes = [];
        for (let i = 0; i < pts.length; i++) {
          const a = pts[i], b = pts[(i + 1) % pts.length];
          if ((a[1] <= cy && b[1] > cy) || (b[1] <= cy && a[1] > cy)) cortes.push(a[0] + ((cy - a[1]) / (b[1] - a[1])) * (b[0] - a[0]));
        }
        cortes.sort((p, q) => p - q);
        for (let i = 0; i + 1 < cortes.length; i += 2) {
          const xa = cortes[i], xb = cortes[i + 1];
          for (let x = Math.round(xa); x < Math.round(xb); x++) {
            if (o.corta && o.corta(x, y)) continue;
            let nx, ny;
            if (o.normal) [nx, ny] = o.normal(x + 0.5, cy);
            else { nx = ((x + 0.5 - (xa + xb) / 2) / Math.max(1, (xb - xa) / 2)) * 0.85; ny = o.ny ?? -0.15; }
            const nz = Math.sqrt(Math.max(0, 1 - nx * nx - ny * ny));
            const op = o.tex ? Object.assign({}, o, { tex: o.tex(x, y) }) : o;
            L.pon(x, y, o.color || tono(rampa, nx, ny, nz, op), K.id, rampa, o.luz, nx, ny, nz);
          }
        }
      }
      return [X0, X1];
    };
    // Placa tallada (piedra, metal, madera): polígono con el borde biselado. El borde que mira a la luz (arriba a la
    // izquierda) sale claro, el contrario oscuro y el centro, del tono medio. o: { bisel (px), tex(x, y), corta(x, y),
    // luz, centro (tono del centro, índice de la rampa) }
    K.placa = (pts, rampa, o = {}) => {
      const n = rampa.length - 1, medio = o.centro ?? Math.ceil(n / 2), claro = n >= 4 ? n - 1 : n, bw = o.bisel ?? 1.6;
      let area = 0;
      for (let i = 0; i < pts.length; i++) { const a = pts[i], b = pts[(i + 1) % pts.length]; area += a[0] * b[1] - b[0] * a[1]; }
      const sg = area > 0 ? 1 : -1, ys = pts.map((p) => p[1]);
      const y0 = Math.floor(Math.min(...ys)), y1 = Math.ceil(Math.max(...ys));
      for (let y = y0; y <= y1; y++) {
        const cy = y + 0.5, cortes = [];
        for (let i = 0; i < pts.length; i++) {
          const a = pts[i], b = pts[(i + 1) % pts.length];
          if ((a[1] <= cy && b[1] > cy) || (b[1] <= cy && a[1] > cy)) cortes.push(a[0] + ((cy - a[1]) / (b[1] - a[1])) * (b[0] - a[0]));
        }
        cortes.sort((p, q) => p - q);
        for (let i = 0; i + 1 < cortes.length; i += 2) for (let x = Math.round(cortes[i]); x < Math.round(cortes[i + 1]); x++) {
          if (o.corta && o.corta(x, y)) continue;
          const cx = x + 0.5;
          let dmin = 1e9, nx = 0, ny = 0;
          for (let k = 0; k < pts.length; k++) {
            const a = pts[k], b = pts[(k + 1) % pts.length], ex = b[0] - a[0], ey = b[1] - a[1], l2 = ex * ex + ey * ey || 1e-6;
            const t = Math.max(0, Math.min(1, ((cx - a[0]) * ex + (cy - a[1]) * ey) / l2)), dx = cx - a[0] - ex * t, dy = cy - a[1] - ey * t, d = Math.hypot(dx, dy);
            if (d < dmin) { dmin = d; const m = Math.sqrt(l2); nx = (ey / m) * sg; ny = (-ex / m) * sg; }
          }
          // nx, ny: hacia fuera del borde más cercano (en pantalla)
          const cara = -nx * 0.6 - ny * 0.8;
          let k = medio;
          if (dmin < bw) { if (cara > 0.3) k = dmin < 1 && cara > 0.6 ? n : claro; else if (cara < -0.3) k = Math.max(1, medio - 1 - (dmin < 1 ? 1 : 0)); }
          if (o.tex) k = Math.max(1, Math.min(n, k + Math.round(o.tex(x, y))));
          const nz = dmin < bw ? 0.6 : 1;
          L.pon(x, y, rampa[k], K.id, rampa, o.luz, dmin < bw ? nx * 0.8 : 0, dmin < bw ? ny * 0.8 : 0, nz);
        }
      }
    };
    // Placa de a a b con ancho wa → wb (una pieza de armadura sobre un hueso).
    K.placaEntre = (a, b, wa, wb, rampa, o) => {
      const dx = b[0] - a[0], dy = b[1] - a[1], m = Math.hypot(dx, dy) || 1, nx = -dy / m, ny = dx / m;
      const ext = (o && o.punta) || 0, ux = dx / m, uy = dy / m;
      K.placa([[a[0] + nx * wa / 2 - ux * ext, a[1] + ny * wa / 2 - uy * ext], [b[0] + nx * wb / 2 + ux * ext, b[1] + ny * wb / 2 + uy * ext], [b[0] - nx * wb / 2 + ux * ext, b[1] - ny * wb / 2 + uy * ext], [a[0] - nx * wa / 2 - ux * ext, a[1] - ny * wa / 2 - uy * ext]], rampa, o);
    };
    // Vara fina de a a b: 1 px (color) o 2 px (luz arriba, sombra abajo).
    K.vara = (a, b, rampa, o = {}) => {
      const dx = b[0] - a[0], dy = b[1] - a[1], n = Math.max(1, Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)) * 1.5));
      const gr = o.grosor || 1, nx = -dy, ny = dx, m = Math.hypot(nx, ny) || 1, ox = nx / m, oy = ny / m;
      const arriba = oy < 0 || (oy === 0 && ox < 0) ? 1 : -1; // el lado de la normal que mira hacia la luz
      for (let i = 0; i <= n; i++) {
        const t = i / n, x = a[0] + dx * t, y = a[1] + dy * t;
        if (gr === 1) L.pon(x, y, rampa[o.k ?? 2], K.id, rampa, o.luz);
        else {
          L.pon(x + ox * 0.5 * arriba, y + oy * 0.5 * arriba, rampa[o.k1 ?? 3], K.id, rampa, o.luz, ox * arriba, oy * arriba, 0.5);
          L.pon(x - ox * 0.5 * arriba, y - oy * 0.5 * arriba, rampa[o.k0 ?? 1], K.id, rampa, o.luz, -ox * arriba, -oy * arriba, 0.5);
        }
      }
    };
    // Rejilla a mano: pal letra → [color, rampa, luz]. (x, y): esquina de arriba a la izquierda. o.voltear, o.rot90.
    K.rej = (g, pal, x0, y0, o = {}) => {
      let filas = typeof g === 'string' ? MANO.rejilla(g) : g;
      const w = Math.max(...filas.map((q) => q.length));
      filas = filas.map((q) => q.padEnd(w, '.'));
      if (o.voltear) filas = filas.map((q) => [...q].reverse().join(''));
      for (let r = 0; r < (o.rot90 || 0); r++) {
        const h = filas.length, ww = filas[0].length, nf = [];
        for (let x = 0; x < ww; x++) { let q = ''; for (let y = h - 1; y >= 0; y--) q += filas[y][x]; nf.push(q); }
        filas = nf;
      }
      const H = filas.length, W = filas[0].length;
      for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
        const ch = filas[y][x];
        if (ch === '.' || ch === ' ') continue;
        const e = pal[ch];
        if (!e) throw new Error(`CRI: la letra «${ch}» no tiene color`);
        // normal de cojín: hacia fuera desde el centro del dibujo
        const nx = ((x + 0.5) / W - 0.5) * 1.4, ny = ((y + 0.5) / H - 0.5) * 1.2, nz = Math.sqrt(Math.max(0, 1 - nx * nx - ny * ny));
        L.pon(Math.round(x0) + x, Math.round(y0) + y, e[0], K.id, e[1], e[2], nx, ny, nz);
      }
      return { w: W, h: H };
    };
    // Un punto del modelo en la lámina (como los huesos de K.S).
    K.pt = (p) => { const q = proyectar(p, D.yaw ?? 0, D.px); return [D.ox + q[0], D.oy - q[1], q[2]]; };
    // Dirección en pantalla de un vector del modelo (el «arriba» de una mano): [dx, dy] unitario, y hacia abajo.
    K.dir = (v) => { const a = proyectar(v, D.yaw ?? 0, 1), m = Math.hypot(a[0], a[1]) || 1; return [a[0] / m, -a[1] / m]; };
    return K;
  }

  // Termina la lámina: líneas entre piezas, contorno de fuera (el tono más oscuro del vecino; sin negro) y la capa de
  // lo que brilla. Devuelve { color, luz } (Lienzos; color con las normales en .n).
  function terminar(L, orden) {
    const { W, H } = L, out = L.c.slice(), nrm = L.n.slice();
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const k = y * W + x, p = L.p[k];
      if (p < 0 || !L.r[k] || L.r[k].sinLinea || L.l[k]) continue;
      for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
        const xx = x + dx, yy = y + dy;
        if (xx < 0 || yy < 0 || xx >= W || yy >= H) continue;
        const q = L.p[yy * W + xx];
        if (q >= 0 && q !== p && orden[q] > orden[p] && !(L.r[yy * W + xx] && L.r[yy * W + xx].sinLinea)) { out[k] = L.r[k].linea || L.r[k][0]; break; }
      }
    }
    const color = new Lienzo(W, H), luz = new Lienzo(W, H);
    color.d = out.slice();
    let hayLuz = false;
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const k = y * W + x;
      if (out[k]) { if (L.l[k]) { luz.d[k] = out[k]; hayLuz = true; } continue; }
      // contorno: primero el vecino de abajo y los de los lados (el borde de abajo, del tono más oscuro)
      for (const [dx, dy] of [[0, -1], [1, 0], [-1, 0], [0, 1]]) {
        const xx = x + dx, yy = y + dy;
        if (xx < 0 || yy < 0 || xx >= W || yy >= H) continue;
        const v = yy * W + xx;
        if (out[v] && L.r[v] && !L.r[v].sinBorde) { color.d[k] = dy === 1 && L.r[v].arriba !== undefined ? L.r[v][L.r[v].arriba] : L.r[v][0]; nrm[k] = nrm[v]; break; }
      }
    }
    color.n = nrm;
    return { color, luz: hayLuz ? luz : null };
  }

  // Un cuadro de una animación de una criatura. D: la ficha (ver DEF); anim: letra; f: número de cuadro.
  function cuadro(D, anim, f) {
    const A = D.anims[anim], n = A.n, bucle = !!A.bucle;
    const u = bucle ? f / n : n > 1 ? f / (n - 1) : 0;
    const uc = (A.de ?? 0) + ((A.a ?? 1) - (A.de ?? 0)) * u;
    let P = {};
    if (A.clip) {
      P = pose(D.fuente, A.clip, uc, bucle && (A.de ?? 0) === 0 && (A.a ?? 1) === 1);
      // raiz: cuánto se desplaza el cuerpo entero (1 = lo que hace la animación; al morir o al levantarse se va lejos)
      if (A.raiz !== undefined) {
        const h0 = D.fuente.clips[A.clip].c[0], k = D.fuente.huesos.indexOf('hips');
        const ox = (P.hips[0] - h0[k * 3] / 1000) * (1 - A.raiz), oz = (P.hips[2] - h0[k * 3 + 2] / 1000) * (1 - A.raiz);
        for (const b of Object.keys(P)) if (b[0] !== '_') { P[b][0] -= ox; P[b][2] -= oz; }
      }
      if (D.prop) P = proporciones(P, D.prop);
    }
    const L = new Lam(D.W, D.H), S = {};
    const K = contexto(D, L, S, P, anim, f, u);
    K.uc = uc; K.A = A;
    if (D.huesos) D.huesos(K); // esqueleto hecho aquí (o retoques del de la animación): escribe en K.P
    for (const b in K.P) if (b[0] !== '_') { const q = proyectar(K.P[b], D.yaw ?? 0, D.px); S[b] = [D.ox + q[0], D.oy - q[1], q[2]]; }
    D.dibujar(K);
    K.piezas.sort((a, b) => a[0] - b[0]);
    const orden = new Int16Array(K.piezas.length + 1);
    K.piezas.forEach(([, fn], i) => { orden[i] = i; K.id = i; fn(); });
    const r = terminar(L, orden);
    return Object.assign(r, { puntos: K.puntos || {} });
  }
  // Recorta al dibujo (con los pies en la fila de abajo) y apunta el desplazamiento para que no se mueva de sitio.
  function recortar(D, r) {
    const L = r.color, W = L.w, H = L.h, suelo = Math.min(H - 1, D.oy + 1);
    let x0 = W, x1 = -1, y0 = H, y1 = suelo;
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) if (L.d[y * W + x]) { x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); }
    if (x1 < 0) { x0 = x1 = D.ox; y0 = suelo; }
    const w = x1 - x0 + 1, h = y1 - y0 + 1;
    const corta = (S) => {
      if (!S) return null;
      const R = new Lienzo(w, h);
      if (S.n) R.n = new Int32Array(w * h);
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const k = (y0 + y) * W + x0 + x; R.d[y * w + x] = S.d[k]; if (S.n) R.n[y * w + x] = S.n[k]; }
      return R;
    };
    const puntos = {};
    for (const [k, p] of Object.entries(r.puntos)) puntos[k] = [(p[0] - D.ox) / 16, (suelo - p[1]) / 16];
    return { color: corta(L), luz: corta(r.luz), dx: ((x0 + x1 + 1) / 2 - D.ox) / 16, abajo: (y1 - suelo) / 16, puntos };
  }

  // ---------------------------------------------------------------- hornear
  // Las fichas: DEF[tipo] = { W, H, ox, oy (dónde están los pies en la lámina), px (px por unidad), yaw, fuente
  // (ANIM_*), prop (proporciones de humanoide), anims: { letra: { clip, n, de, a, bucle } }, huesos(K), dibujar(K) }.
  const DEF = {};
  // Hornea una criatura y la apunta en el atlas: <tipo>_<anim><f> (y _luz). Los nombres viejos (<tipo>_0…3) quedan
  // como alias de estos cuadros.
  function hornear(tipo) {
    const D = DEF[tipo], hechos = [];
    for (const [anim, A] of Object.entries(D.anims)) for (let f = 0; f < A.n; f++) {
      const r = recortar(D, cuadro(D, anim, f)), nombre = `${tipo}_${anim}${f}`;
      hechos.push([nombre, r]);
    }
    return hechos;
  }
  function registrar() {
    for (const tipo of Object.keys(DEF)) {
      if (DEF[tipo].jefe) continue; // los jefes se hornean al acercarte a su guarida (JEFE_ESQ)
      for (const [nombre, r] of hornear(tipo)) {
        for (const suf of ['', '_luz']) quitarDibujo(nombre + suf);
        def(nombre, r.color);
        if (r.luz && r.luz.d.some((c) => c)) def(nombre + '_luz', r.luz);
        META_SPR[nombre] = { dx: r.dx, dz: 0, abajo: r.abajo, puntos: r.puntos };
      }
      const D = DEF[tipo], an = D.anims;
      // los nombres de antes apuntan a los cuadros nuevos (el bestiario, las mascotas, el título…)
      const alias = { 0: an.a ? 'a0' : 'q0', 1: an.a ? `a${Math.floor(an.a.n / 2)}` : 'q1', 2: an.p ? `p${an.p.n - 1}` : 'q0', 3: an.g ? `g${Math.min(1, an.g.n - 1)}` : 'q0' };
      for (const [k, v] of Object.entries(alias)) {
        for (const suf of ['', '_luz']) quitarDibujo(`${tipo}_${k}${suf}`);
        ALIAS_SPR[`${tipo}_${k}`] = `${tipo}_${v}`;
        ALIAS_SPR[`${tipo}_${k}_luz`] = `${tipo}_${v}_luz`;
      }
    }
  }
  // Un dibujo quieto (objetos, el Corazón, iconos) con las mismas primitivas y el mismo acabado que las criaturas:
  // fn(K) apunta piezas con K.pieza(z, () => …) o pinta directamente; devuelve { color, luz } recortados al dibujo
  // con 1 px de margen para el contorno y { x0, y0 } (dónde quedó la esquina de la lámina).
  function lamina(W, H, fn) {
    const D = { px: 1, ox: 0, oy: 0, yaw: 0 }, L = new Lam(W + 2, H + 2), K = contexto(D, L, {}, {}, 'q', 0, 0);
    const pieza = K.pieza;
    K.pieza = (z, f) => pieza(z, f);
    // todo se pinta corrido 1 px para que quepa el contorno
    for (const k of ['cap', 'tubo', 'ovalo', 'poli', 'placa', 'placaEntre', 'vara', 'rej', 'px']) {
      const f = K[k];
      K[k] = (...a) => {
        const m = (p) => [p[0] + 1, p[1] + 1];
        if (k === 'cap' || k === 'placaEntre' || k === 'vara') { a[0] = m(a[0]); a[1] = m(a[1]); }
        else if (k === 'ovalo') a[0] = m(a[0]);
        else if (k === 'poli' || k === 'placa' || k === 'tubo') a[0] = a[0].map(m);
        else if (k === 'rej') { a[2] += 1; a[3] += 1; }
        else if (k === 'px') { a[0] += 1; a[1] += 1; }
        // las funciones de las opciones reciben las coordenadas de antes de correrlas
        const i = { cap: 5, tubo: 4, ovalo: 4, poli: 2, placa: 2 }[k], o = i && a[i];
        if (o && (o.corta || o.tex || o.normal)) {
          a[i] = Object.assign({}, o, o.corta ? { corta: (x, y) => o.corta(x - 1, y - 1) } : {}, o.tex ? { tex: (x, y) => o.tex(x - 1, y - 1) } : {}, o.normal ? { normal: (x, y) => o.normal(x - 1, y - 1) } : {});
        }
        return f(...a);
      };
    }
    K.id = 0;
    fn(K);
    K.piezas.sort((a, b) => a[0] - b[0]);
    const orden = new Int16Array(K.piezas.length + 2);
    K.piezas.forEach(([, f], i) => { orden[i + 1] = i + 1; K.id = i + 1; f(); });
    return terminar(L, orden);
  }
  // Cuántos cuadros tiene cada animación de cada criatura (para elegir en el juego).
  const animsDe = (tipo) => DEF[tipo] && !DEF[tipo].jefe && DEF[tipo].anims;

  return { DEF, pose, proporciones, encorvar, proyectar, cuadro, recortar, hornear, registrar, animsDe, lamina, tono, mezcla, sum, res, por, norm, LUZ };
})();

// Los jefes: miden 80–145 px y tienen muchos cuadros, así que no caben todos en el atlas. Se hornea el del jefe que
// tienes cerca (poco a poco, unos milisegundos por cuadro del juego) en la zona de jefes del atlas (ZONA_JEFE).
const JEFE_ESQ = (() => {
  let listo = null, trabajo = null;
  function empezar(id) {
    if (listo === id || (trabajo && trabajo.id === id) || !CRI.DEF[id]) return;
    const D = CRI.DEF[id], pend = [];
    for (const [anim, A] of Object.entries(D.anims)) for (let f = 0; f < A.n; f++) pend.push([anim, f]);
    trabajo = { id, pend, hechos: [] };
  }
  function trabajar(ms) {
    if (!trabajo) return;
    const t0 = performance.now(), D = CRI.DEF[trabajo.id];
    while (trabajo.pend.length && performance.now() - t0 < ms) {
      const [anim, f] = trabajo.pend.shift();
      trabajo.hechos.push([`${trabajo.id}_${anim}${f}`, CRI.recortar(D, CRI.cuadro(D, anim, f))]);
    }
    if (trabajo.pend.length) return;
    const lista = [];
    for (const [n, r] of trabajo.hechos) {
      lista.push([n, r.color]);
      if (r.luz && r.luz.d.some((c) => c)) lista.push([n + '_luz', r.luz]);
      META_SPR[n] = { dx: r.dx, dz: 0, abajo: r.abajo, puntos: r.puntos };
    }
    escribirZona(lista, ZONA_JEFE);
    listo = trabajo.id; trabajo = null;
  }
  return {
    // pide el jefe; yaMismo: hornearlo entero ahora (si se va a ver ya)
    pedir(id, yaMismo) { empezar(id); trabajar(yaMismo ? 1e9 : 6); return listo === id; },
    trabajar, listo: () => listo, olvidar() { listo = null; trabajo = null; },
  };
})();

// Los cuadros de las criaturas se hornean con el resto de modelos (al final: sustituyen a los dibujos viejos).
if (typeof CONSTRUIR_MODELOS !== 'undefined') CONSTRUIR_MODELOS.push(() => CRI.registrar());
