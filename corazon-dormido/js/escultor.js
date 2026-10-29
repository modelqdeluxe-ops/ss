'use strict';
// Escultor: modelos 3D hechos con figuras (esferas, cajas, cilindros, cápsulas, toros…) que se hornean a pixel
// art con la misma cámara del juego. Así todo lo que está de pie tiene volumen de verdad y la perspectiva es
// siempre la misma que la de las paredes: se ve la tapa y la cara delantera, cada una a tamaño real.
//
// Unidades: 1 = un píxel del arte (16 por casilla). x hacia el este, y hacia arriba, z hacia el sur (hacia la
// cámara). Proyección: u = x, v = z - y. Lo que está más alto y más al sur tapa a lo de detrás (prof = y + z).
//
// Al hornear se calcula, por píxel: la luz de una lámpara fija arriba a la izquierda (con luz envolvente), la
// oclusión de los vóxeles de alrededor (los rincones se oscurecen), el brillo de los metales, la textura del
// material (vetas, grietas, hojas…) y la rampa de colores del material. Después: líneas interiores donde una
// pieza tapa a otra, limpieza de píxeles sueltos, contorno de color, la capa que brilla sola (_luz), las
// normales (para que las antorchas iluminen por el lado en el juego) y la sombra en el suelo con su forma.

const ESCU = (() => {
  // ---------------------------------------------------------------- ruido
  function h3(x, y, z, s) {
    let h = Math.imul(x | 0, 374761393) + Math.imul(y | 0, 668265263) + Math.imul(z | 0, 1103515245) + Math.imul(s | 0, 1442695041);
    h = Math.imul(h ^ (h >>> 13), 1274126177); h ^= h >>> 16;
    return (h >>> 0) / 4294967296;
  }
  const suave = (t) => t * t * (3 - 2 * t);
  // ruido de valor suave en 3D (0..1)
  function ruido(x, y, z, esc = 4, s = 0) {
    x /= esc; y /= esc; z /= esc;
    const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z), u = suave(x - xi), v = suave(y - yi), w = suave(z - zi);
    const l = (a, b, t) => a + (b - a) * t;
    return l(
      l(l(h3(xi, yi, zi, s), h3(xi + 1, yi, zi, s), u), l(h3(xi, yi + 1, zi, s), h3(xi + 1, yi + 1, zi, s), u), v),
      l(l(h3(xi, yi, zi + 1, s), h3(xi + 1, yi, zi + 1, s), u), l(h3(xi, yi + 1, zi + 1, s), h3(xi + 1, yi + 1, zi + 1, s), u), v), w);
  }
  const fbm = (x, y, z, esc, s) => ruido(x, y, z, esc, s) * 0.65 + ruido(x, y, z, esc / 2.3, s + 7) * 0.35;

  // ---------------------------------------------------------------- colores
  const hex2 = (h) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
  const a2hex = (c) => '#' + c.map((v) => Math.max(0, Math.min(255, Math.round(v))).toString(16).padStart(2, '0')).join('');
  function rgb2hsv([r, g, b]) {
    r /= 255; g /= 255; b /= 255;
    const mx = Math.max(r, g, b), mn = Math.min(r, g, b), d = mx - mn;
    let h = 0;
    if (d) h = mx === r ? ((g - b) / d) % 6 : mx === g ? (b - r) / d + 2 : (r - g) / d + 4;
    return [(h * 60 + 360) % 360, mx ? d / mx : 0, mx];
  }
  function hsv2rgb([h, s, v]) {
    const c = v * s, x = c * (1 - Math.abs(((h / 60) % 2) - 1)), m = v - c;
    const [r, g, b] = h < 60 ? [c, x, 0] : h < 120 ? [x, c, 0] : h < 180 ? [0, c, x] : h < 240 ? [0, x, c] : h < 300 ? [x, 0, c] : [c, 0, x];
    return [(r + m) * 255, (g + m) * 255, (b + m) * 255];
  }
  // Rampa de n tonos desde un color medio: las sombras van hacia el azul y más saturadas; las luces hacia el
  // amarillo y menos saturadas (como hacen los pixel artists).
  function rampa(base, n = 6, o = {}) {
    const [h, s, v] = rgb2hsv(hex2(base)), out = [];
    const giro = o.giro ?? 18, oscuro = o.oscuro ?? 0.22, claro = o.claro ?? 1.0;
    for (let i = 0; i < n; i++) {
      const t = i / (n - 1), k = t - 0.5; // -0.5 .. 0.5
      const hh = k < 0 ? h + (shortest(h, 245) * -k * 2 * giro) / 60 : h + (shortest(h, 55) * k * 2 * giro) / 60;
      const vv = k < 0 ? v * (oscuro + (1 - oscuro) * (t * 2)) : v + (Math.min(1, v * 1.35 * claro) - v) * (k * 2);
      const ss = k < 0 ? Math.min(1, s * (1 + -k * 0.5)) : s * (1 - k * 0.55);
      out.push(a2hex(hsv2rgb([(hh + 360) % 360, Math.max(0, Math.min(1, ss)), Math.max(0, Math.min(1, vv))])));
    }
    return out;
  }
  function shortest(a, b) { let d = b - a; while (d > 180) d -= 360; while (d < -180) d += 360; return Math.sign(d) * Math.min(60, Math.abs(d)); }
  const mezclar = (a, b, t) => { const x = hex2(a), y = hex2(b); return a2hex(x.map((v, i) => v + (y[i] - v) * t)); };

  // ---------------------------------------------------------------- materiales
  // { r: rampa (oscuro→claro), tex(x,y,z,nx,ny,nz) → pasos de rampa (+/-), brillo: metal (0..1), luz: brilla solo
  //   (0..1), plano: sin luz direccional (solo la rampa del medio), contorno: color del borde }
  function mat(r, o = {}) { return Object.assign({ r: typeof r === 'string' ? rampa(r, o.n || 6, o) : r }, o); }
  const TEX = {
    // madera en tablas a lo largo de un eje, con vetas
    tablas: (eje = 'x', ancho = 4, s = 1) => (x, y, z) => {
      const a = eje === 'x' ? z : x, l = eje === 'x' ? x : z;
      const junta = ((Math.floor(a) % ancho) + ancho) % ancho === 0 ? -1.2 : 0;
      const veta = ruido(l * 0.25, y, a * 3, 2, s) > 0.72 ? -0.7 : 0;
      return junta + veta + (h3(Math.floor(a / ancho), 0, 0, s) - 0.5) * 0.6;
    },
    vetas: (s = 2) => (x, y, z) => (ruido(x * 0.3, y * 2.5, z * 0.3, 3, s) > 0.66 ? -0.8 : 0) + (ruido(x, y, z, 5, s + 3) - 0.5) * 0.5,
    piedra: (s = 3, esc = 3) => (x, y, z) => { const n = fbm(x, y, z, esc, s); return n < 0.3 ? -0.9 : n > 0.72 ? 0.7 : 0; },
    grumos: (s = 4, esc = 2.5, k = 1) => (x, y, z) => (fbm(x, y, z, esc, s) - 0.5) * 2.2 * k,
    hojas: (s = 5) => (x, y, z) => { const n = ruido(x, y, z, 2.2, s), m = ruido(x, y, z, 5, s + 9); return (n > 0.62 ? 0.9 : n < 0.3 ? -0.9 : 0) + (m - 0.5) * 0.6; },
    rayas: (eje = 'y', cada = 3, k = -1) => (x, y, z) => ((Math.floor(eje === 'y' ? y : eje === 'x' ? x : z) % cada) === 0 ? k : 0),
    motas: (s = 6, p = 0.12, k = 1) => (x, y, z) => (h3(Math.floor(x), Math.floor(y), Math.floor(z), s) < p ? k : 0),
    ninguna: () => 0,
  };

  // ---------------------------------------------------------------- matrices 3×4 (fila mayor)
  const ID = () => new Float64Array([1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0]);
  function mulM(a, b) {
    const o = new Float64Array(12);
    for (let r = 0; r < 3; r++) {
      for (let c = 0; c < 3; c++) o[r * 4 + c] = a[r * 4] * b[c] + a[r * 4 + 1] * b[4 + c] + a[r * 4 + 2] * b[8 + c];
      o[r * 4 + 3] = a[r * 4] * b[3] + a[r * 4 + 1] * b[7] + a[r * 4 + 2] * b[11] + a[r * 4 + 3];
    }
    return o;
  }
  const tras = (x, y, z) => new Float64Array([1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z]);
  function rotM(eje, a) {
    const c = Math.cos(a), s = Math.sin(a);
    if (eje === 'x') return new Float64Array([1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0]);
    if (eje === 'y') return new Float64Array([c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0]);
    return new Float64Array([c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0]);
  }
  const apl = (m, x, y, z) => [m[0] * x + m[1] * y + m[2] * z + m[3], m[4] * x + m[5] * y + m[6] * z + m[7], m[8] * x + m[9] * y + m[10] * z + m[11]];

  // ---------------------------------------------------------------- distancias con signo (espacio local)
  const len3 = (x, y, z) => Math.sqrt(x * x + y * y + z * z);
  const SDF = {
    esfera: (cx, cy, cz, r) => (x, y, z) => len3(x - cx, y - cy, z - cz) - r,
    elipsoide: (cx, cy, cz, rx, ry, rz) => (x, y, z) => {
      const px = (x - cx) / rx, py = (y - cy) / ry, pz = (z - cz) / rz, k0 = len3(px, py, pz), k1 = len3(px / rx, py / ry, pz / rz);
      return k1 ? (k0 * (k0 - 1)) / k1 : -Math.min(rx, ry, rz);
    },
    caja: (x0, y0, z0, x1, y1, z1, r = 0) => {
      const cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, cz = (z0 + z1) / 2, hx = (x1 - x0) / 2 - r, hy = (y1 - y0) / 2 - r, hz = (z1 - z0) / 2 - r;
      return (x, y, z) => {
        const qx = Math.abs(x - cx) - hx, qy = Math.abs(y - cy) - hy, qz = Math.abs(z - cz) - hz;
        return len3(Math.max(qx, 0), Math.max(qy, 0), Math.max(qz, 0)) + Math.min(Math.max(qx, qy, qz), 0) - r;
      };
    },
    // cilindro con la base en (cx, cy, cz), de alto h, a lo largo del eje; r1 arriba (cono si es distinto)
    cilindro: (cx, cy, cz, r, h, eje = 'y', r1 = r, red = 0) => (x, y, z) => {
      let a, b, c;
      if (eje === 'y') { a = y - cy; b = x - cx; c = z - cz; } else if (eje === 'x') { a = x - cx; b = y - cy; c = z - cz; } else { a = z - cz; b = x - cx; c = y - cy; }
      const t = Math.max(0, Math.min(1, a / h)), rr = r + (r1 - r) * t - red;
      const dx = Math.sqrt(b * b + c * c) - rr, dy = Math.abs(a - h / 2) - h / 2 + red;
      return Math.min(Math.max(dx, dy), 0) + Math.hypot(Math.max(dx, 0), Math.max(dy, 0)) - red;
    },
    // cápsula entre dos puntos con radio que cambia (un hueso, una rama, un dedo)
    capsula: (ax, ay, az, bx, by, bz, ra, rb = ra) => {
      const dx = bx - ax, dy = by - ay, dz = bz - az, L2 = dx * dx + dy * dy + dz * dz || 1;
      return (x, y, z) => {
        const px = x - ax, py = y - ay, pz = z - az, t = Math.max(0, Math.min(1, (px * dx + py * dy + pz * dz) / L2));
        return len3(px - dx * t, py - dy * t, pz - dz * t) - (ra + (rb - ra) * t);
      };
    },
    toro: (cx, cy, cz, R, r, eje = 'y') => (x, y, z) => {
      let a, b, c;
      if (eje === 'y') { a = x - cx; b = z - cz; c = y - cy; } else if (eje === 'x') { a = y - cy; b = z - cz; c = x - cx; } else { a = x - cx; b = y - cy; c = z - cz; }
      return Math.hypot(Math.hypot(a, b) - R, c) - r;
    },
  };

  // ---------------------------------------------------------------- el modelo
  class Modelo {
    // w: ancho (x), h: alto (y), d: fondo (z), en píxeles del arte
    constructor(w, h, d) {
      this.w = w; this.h = h; this.d = d;
      const n = w * h * d;
      this.m = new Uint8Array(n); // material + 1
      this.p = new Uint16Array(n); // figura que lo puso (para la normal)
      this.mats = [null]; this.figs = [null];
      this.pila = [{ M: ID(), I: ID() }];
      this.marcas = {};
    }
    idx(x, y, z) { return (z * this.h + y) * this.w + x; }
    lleno(x, y, z) { return x >= 0 && y >= 0 && z >= 0 && x < this.w && y < this.h && z < this.d && this.m[(z * this.h + y) * this.w + x] !== 0; }
    _mat(m) { let i = this.mats.indexOf(m); if (i < 0) { i = this.mats.length; this.mats.push(m); } return i; }
    get T() { return this.pila[this.pila.length - 1]; }
    // Transformaciones: con({ mover:[x,y,z], rot:[eje, ángulo, [cx,cy,cz]] , rot2: …}, () => figuras)
    con(o, fn) {
      let { M, I } = this.T;
      const pasos = Array.isArray(o) ? o : [o];
      for (const p of pasos) {
        if (p.mover) { const [x, y, z] = p.mover; M = mulM(M, tras(x, y, z)); I = mulM(tras(-x, -y, -z), I); }
        if (p.rot) {
          const [eje, a, c = [0, 0, 0]] = p.rot;
          M = mulM(M, mulM(tras(c[0], c[1], c[2]), mulM(rotM(eje, a), tras(-c[0], -c[1], -c[2]))));
          I = mulM(mulM(tras(c[0], c[1], c[2]), mulM(rotM(eje, -a), tras(-c[0], -c[1], -c[2]))), I);
        }
      }
      this.pila.push({ M, I });
      try { fn(); } finally { this.pila.pop(); }
      return this;
    }
    // un punto del espacio local al del modelo (para marcar ojos, manos…)
    punto(x, y, z) { return apl(this.T.M, x, y, z); }
    marca(n, x, y, z) { this.marcas[n] = this.punto(x, y, z); return this; }

    // Rellena con una distancia con signo. bb: caja local [x0,y0,z0,x1,y1,z1]. o: { modo, ruido:[amp, esc, sem], pieza }
    figura(sdf, bb, material, o = {}) {
      const { M, I } = this.T, amp = o.ruido ? o.ruido[0] : 0, resc = o.ruido ? o.ruido[1] : 1, rsem = o.ruido ? o.ruido[2] || 0 : 0;
      const pad = amp + 1;
      let x0 = 1e9, y0 = 1e9, z0 = 1e9, x1 = -1e9, y1 = -1e9, z1 = -1e9;
      for (const cx of [bb[0] - pad, bb[3] + pad]) for (const cy of [bb[1] - pad, bb[4] + pad]) for (const cz of [bb[2] - pad, bb[5] + pad]) {
        const [a, b, c] = apl(M, cx, cy, cz);
        x0 = Math.min(x0, a); x1 = Math.max(x1, a); y0 = Math.min(y0, b); y1 = Math.max(y1, b); z0 = Math.min(z0, c); z1 = Math.max(z1, c);
      }
      x0 = Math.max(0, Math.floor(x0)); y0 = Math.max(0, Math.floor(y0)); z0 = Math.max(0, Math.floor(z0));
      x1 = Math.min(this.w - 1, Math.ceil(x1)); y1 = Math.min(this.h - 1, Math.ceil(y1)); z1 = Math.min(this.d - 1, Math.ceil(z1));
      const modo = o.modo || 'añadir', mi = material ? this._mat(material) : 0;
      const fi = this.figs.length;
      const d = amp ? (x, y, z) => sdf(x, y, z) + (fbm(x, y, z, resc, rsem) - 0.5) * 2 * amp : sdf;
      this.figs.push({ d, R: M });
      const m = this.m, p = this.p, W = this.w, H = this.h;
      for (let z = z0; z <= z1; z++) for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
        const wx = x + 0.5, wy = y + 0.5, wz = z + 0.5;
        const lx = I[0] * wx + I[1] * wy + I[2] * wz + I[3], ly = I[4] * wx + I[5] * wy + I[6] * wz + I[7], lz = I[8] * wx + I[9] * wy + I[10] * wz + I[11];
        if (d(lx, ly, lz) > 0) continue;
        const i = (z * H + y) * W + x;
        if (modo === 'quitar') { m[i] = 0; continue; }
        if (modo === 'pintar') { if (m[i]) m[i] = mi; continue; }
        if (modo === 'debajo' && m[i]) continue;
        m[i] = mi; p[i] = fi;
      }
      return this;
    }
    // ---- figuras con su caja
    esfera(c, r, mt, o) { return this.figura(SDF.esfera(c[0], c[1], c[2], r), [c[0] - r, c[1] - r, c[2] - r, c[0] + r, c[1] + r, c[2] + r], mt, o); }
    elipsoide(c, r, mt, o) { return this.figura(SDF.elipsoide(c[0], c[1], c[2], r[0], r[1], r[2]), [c[0] - r[0], c[1] - r[1], c[2] - r[2], c[0] + r[0], c[1] + r[1], c[2] + r[2]], mt, o); }
    caja(a, b, mt, o = {}) { return this.figura(SDF.caja(a[0], a[1], a[2], b[0], b[1], b[2], o.r || 0), [a[0], a[1], a[2], b[0], b[1], b[2]], mt, o); }
    cilindro(base, r, h, mt, o = {}) {
      const eje = o.eje || 'y', r1 = o.r1 ?? r, R = Math.max(r, r1);
      const bb = eje === 'y' ? [base[0] - R, base[1], base[2] - R, base[0] + R, base[1] + h, base[2] + R]
        : eje === 'x' ? [base[0], base[1] - R, base[2] - R, base[0] + h, base[1] + R, base[2] + R] : [base[0] - R, base[1] - R, base[2], base[0] + R, base[1] + R, base[2] + h];
      return this.figura(SDF.cilindro(base[0], base[1], base[2], r, h, eje, r1, o.red || 0), bb, mt, o);
    }
    capsula(a, b, ra, rb, mt, o) {
      if (typeof rb !== 'number') { o = mt; mt = rb; rb = ra; }
      const R = Math.max(ra, rb);
      return this.figura(SDF.capsula(a[0], a[1], a[2], b[0], b[1], b[2], ra, rb), [Math.min(a[0], b[0]) - R, Math.min(a[1], b[1]) - R, Math.min(a[2], b[2]) - R, Math.max(a[0], b[0]) + R, Math.max(a[1], b[1]) + R, Math.max(a[2], b[2]) + R], mt, o);
    }
    toro(c, R, r, mt, o = {}) { const e = R + r; return this.figura(SDF.toro(c[0], c[1], c[2], R, r, o.eje || 'y'), [c[0] - e, c[1] - e, c[2] - e, c[0] + e, c[1] + e, c[2] + e], mt, o); }
    // Una línea de cápsulas que pasa por varios puntos (ramas, colas, tentáculos), con radios de r0 a r1
    tubo(pts, r0, r1, mt, o) {
      for (let k = 0; k < pts.length - 1; k++) {
        const ta = k / (pts.length - 1), tb = (k + 1) / (pts.length - 1);
        this.capsula(pts[k], pts[k + 1], r0 + (r1 - r0) * ta, r0 + (r1 - r0) * tb, mt, o);
      }
      return this;
    }
    quitar(fn) { // todo lo que se haga dentro quita en vez de poner
      const f = this.figura;
      this.figura = (s, bb, mt, o = {}) => f.call(this, s, bb, mt, Object.assign({}, o, { modo: 'quitar' }));
      try { fn(); } finally { this.figura = f; }
      return this;
    }
    pintar(fn) { // pinta encima de lo que ya hay (rayas, manchas, ojos) sin cambiar la forma
      const f = this.figura;
      this.figura = (s, bb, mt, o = {}) => f.call(this, s, bb, mt, Object.assign({}, o, { modo: 'pintar' }));
      try { fn(); } finally { this.figura = f; }
      return this;
    }

    // ---------------------------------------------------------------- hornear
    normal(i, x, y, z) {
      const f = this.figs[this.p[i]];
      if (!f) return [0, 1, 0];
      const { M } = { M: f.R };
      // vuelve al espacio local de la figura con la inversa de su matriz (solo hace falta la rotación)
      const wx = x + 0.5, wy = y + 0.5, wz = z + 0.5;
      // matriz inversa: la guardamos al vuelo
      if (!f.I) f.I = invM(M);
      const I = f.I;
      const lx = I[0] * wx + I[1] * wy + I[2] * wz + I[3], ly = I[4] * wx + I[5] * wy + I[6] * wz + I[7], lz = I[8] * wx + I[9] * wy + I[10] * wz + I[11];
      const e = 0.4, d = f.d;
      let nx = d(lx + e, ly, lz) - d(lx - e, ly, lz), ny = d(lx, ly + e, lz) - d(lx, ly - e, lz), nz = d(lx, ly, lz + e) - d(lx, ly, lz - e);
      // a la orientación del modelo
      const ax = M[0] * nx + M[1] * ny + M[2] * nz, ay = M[4] * nx + M[5] * ny + M[6] * nz, az = M[8] * nx + M[9] * ny + M[10] * nz;
      const l = len3(ax, ay, az) || 1;
      return [ax / l, ay / l, az / l];
    }

    // o: { contorno (true), luz: [x,y,z], amb, lineas (true), limpiar (true), sombra (true), suelo (oscurece abajo) }
    hornear(o = {}) {
      // cima: cuánto se ve la tapa (1 = como las paredes; los personajes se ven más de frente, ~0.45)
      const W = this.w, H = this.h, D = this.d, c = o.cima ?? 1, U = W, V = Math.floor((D - 1) * c) + H;
      this.cima = c;
      const prof = new Float32Array(U * V).fill(-1e9), vox = new Int32Array(U * V).fill(-1);
      for (let z = 0; z < D; z++) {
        const vz = Math.floor(z * c) + H - 1;
        for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
          const i = (z * H + y) * W + x;
          if (!this.m[i]) continue;
          const k = (vz - y) * U + x, pr = c * y + z;
          if (pr > prof[k]) { prof[k] = pr; vox[k] = i; }
        }
      }
      // luz fija: arriba a la izquierda y un poco hacia la cámara
      const L = o.luz || [-0.45, 0.88, 0.25], ll = len3(...L), Lx = L[0] / ll, Ly = L[1] / ll, Lz = L[2] / ll;
      const Vx = 0, Vy = 0.7071, Vz = 0.7071;
      const amb = o.amb ?? 0.28, envuelve = o.envuelve ?? 0.25, contraste = o.contraste ?? 1, realce = o.realce ?? 0;
      const idxC = new Int16Array(U * V).fill(-1), matC = new Int16Array(U * V), nrm = new Float32Array(U * V * 3);
      for (let k = 0; k < U * V; k++) {
        const i = vox[k];
        if (i < 0) continue;
        const x = i % W, y = Math.floor(i / W) % H, z = Math.floor(i / (W * H));
        const mt = this.mats[this.m[i]];
        let [nx, ny, nz] = this.normal(i, x, y, z);
        // la normal de la figura puede mentir en piezas finas (una tabla de 1 px): si apunta hacia donde hay
        // vóxeles llenos, se usa la que sale de los huecos de alrededor
        let ox = 0, oy = 0, oz = 0;
        for (let dz = -1; dz <= 1; dz++) for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
          if ((dx || dy || dz) && !this.lleno(x + dx, y + dy, z + dz)) { const l = Math.hypot(dx, dy, dz); ox += dx / l; oy += dy / l; oz += dz / l; }
        }
        const ol = Math.hypot(ox, oy, oz);
        if (ol > 0.01) {
          ox /= ol; oy /= ol; oz /= ol;
          if (nx * ox + ny * oy + nz * oz < 0.3) { nx = ox; ny = oy; nz = oz; }
        }
        nrm[k * 3] = nx; nrm[k * 3 + 1] = ny; nrm[k * 3 + 2] = nz;
        // oclusión: vóxeles llenos alrededor del punto un poco por fuera de la superficie
        let oc = 0, tot = 0;
        const sx = x + nx * 1.5, sy = y + ny * 1.5, sz = z + nz * 1.5;
        for (let dz = -1; dz <= 1; dz++) for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
          tot++; if (this.lleno(Math.round(sx + dx), Math.round(sy + dy), Math.round(sz + dz))) oc++;
        }
        const ao = Math.max(0.45, Math.min(1, 1 - (oc / tot - 0.12) * 1.25));
        let lum;
        if (mt.plano) lum = 0.62;
        else {
          const dif = (nx * Lx + ny * Ly + nz * Lz + envuelve) / (1 + envuelve);
          lum = (amb + (1 - amb) * Math.max(0, dif)) * ao;
          if (o.suelo !== false && y < 2) lum *= 0.82 + y * 0.09;
          if (mt.brillo) {
            const d2 = 2 * (nx * Lx + ny * Ly + nz * Lz), rx = d2 * nx - Lx, ry = d2 * ny - Ly, rz = d2 * nz - Lz;
            const sp = Math.pow(Math.max(0, rx * Vx + ry * Vy + rz * Vz), 8) * mt.brillo;
            // los metales: el cuerpo en los tonos de en medio y el brillo arriba (si no, la tapa sale blanca)
            lum = lum * 0.74 + sp * 0.9;
          }
        }
        // contraste de tonos (toon): la luz y la sombra se separan con un corte claro en vez de un degradado largo
        if (!mt.plano) lum = Math.max(0, Math.min(1, (lum - 0.5) * (mt.contraste ?? contraste) + 0.5 + realce));
        const n = mt.r.length;
        let t = lum * n + (mt.tex ? mt.tex(x, y, z, nx, ny, nz) : 0) + (mt.sube || 0);
        idxC[k] = Math.max(0, Math.min(n - 1, Math.floor(t)));
        matC[k] = this.m[i];
      }
      // líneas interiores: donde algo cercano tapa a algo de detrás, lo de detrás se oscurece en el borde
      if (o.lineas !== false) {
        const marca = new Uint8Array(U * V);
        for (let v = 0; v < V; v++) for (let u = 0; u < U; u++) {
          const k = v * U + u;
          if (vox[k] < 0) continue;
          for (const [du, dv] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
            const uu = u + du, vv = v + dv;
            if (uu < 0 || vv < 0 || uu >= U || vv >= V) continue;
            const k2 = vv * U + uu;
            if (vox[k2] < 0) continue;
            const dif = (prof[k2] - prof[k]) / c, otra = this.p[vox[k2]] !== this.p[vox[k]] && matC[k2] !== matC[k];
            if (dif >= (otra ? 2 : 4)) marca[k] = Math.max(marca[k], otra ? 2 : 1);
          }
        }
        for (let k = 0; k < U * V; k++) if (marca[k]) idxC[k] = this.mats[matC[k]].lineaFuerte ? 0 : Math.max(0, idxC[k] - marca[k]);
      }
      // limpieza: un píxel solo, rodeado de otro tono del mismo material, toma ese tono
      if (o.limpiar !== false) {
        const copia = idxC.slice();
        for (let v = 1; v < V - 1; v++) for (let u = 1; u < U - 1; u++) {
          const k = v * U + u;
          if (idxC[k] < 0) continue;
          const vec = [k - 1, k + 1, k - U, k + U].filter((q) => idxC[q] >= 0 && matC[q] === matC[k]);
          if (vec.length < 4) continue;
          if (vec.some((q) => idxC[q] === idxC[k])) continue;
          const cuenta = {};
          for (const q of vec) cuenta[idxC[q]] = (cuenta[idxC[q]] || 0) + 1;
          const [mejor, c] = Object.entries(cuenta).sort((a, b) => b[1] - a[1])[0];
          if (c >= 3) copia[k] = +mejor;
        }
        idxC.set(copia);
      }
      // cúmulos: en el pixel art no hay manchas sueltas de uno o dos píxeles (se leen como ruido); cada mancha de
      // un tono más pequeña que `cumulo` toma el tono vecino del mismo material que más toca. Se respetan el brillo
      // de los metales y lo que brilla solo (son detalles a propósito).
      const minC = o.cumulo ?? 2;
      if (minC > 1) for (let pasada = 0; pasada < 2; pasada++) {
        const visto = new Uint8Array(U * V), pila = [], comp = [];
        for (let k0 = 0; k0 < U * V; k0++) {
          if (idxC[k0] < 0 || visto[k0]) continue;
          const t0 = idxC[k0], m0 = matC[k0];
          comp.length = 0; pila.length = 0; pila.push(k0); visto[k0] = 1;
          while (pila.length) {
            const k = pila.pop(); comp.push(k);
            const u = k % U;
            for (const q of [u > 0 ? k - 1 : -1, u < U - 1 ? k + 1 : -1, k - U, k + U]) {
              if (q < 0 || q >= U * V || visto[q] || idxC[q] !== t0 || matC[q] !== m0) continue;
              visto[q] = 1; pila.push(q);
            }
          }
          if (comp.length >= minC) continue;
          const mt = this.mats[m0];
          if (mt.luz || mt.detalle || (mt.brillo && t0 >= mt.r.length - 2)) continue;
          const cuenta = new Map();
          for (const k of comp) {
            const u = k % U;
            for (const q of [u > 0 ? k - 1 : -1, u < U - 1 ? k + 1 : -1, k - U, k + U]) {
              if (q < 0 || q >= U * V || idxC[q] < 0 || matC[q] !== m0 || idxC[q] === t0) continue;
              cuenta.set(idxC[q], (cuenta.get(idxC[q]) || 0) + 1);
            }
          }
          let mejor = -1, mc = 0;
          for (const [t, c] of cuenta) if (c > mc || (c === mc && Math.abs(t - t0) < Math.abs(mejor - t0))) { mejor = t; mc = c; }
          if (mejor >= 0) for (const k of comp) idxC[k] = mejor;
        }
      }
      // luz de borde: el filo de la silueta que mira a la lámpara (arriba a la izquierda) sube un tono, como el
      // brillo que el pixel artist pone a mano para despegar la figura del fondo
      if (o.borde !== false) {
        const sube = [];
        for (let v = 0; v < V; v++) for (let u = 0; u < U; u++) {
          const k = v * U + u;
          if (idxC[k] < 0) continue;
          const mt = this.mats[matC[k]];
          if (mt.plano || mt.luz) continue;
          const arriba = v === 0 || vox[k - U] < 0, izq = u === 0 || vox[k - 1] < 0;
          if (!arriba && !izq) continue;
          const nx = nrm[k * 3], ny = nrm[k * 3 + 1], nz = nrm[k * 3 + 2];
          if (nx * Lx + ny * Ly + nz * Lz > 0.2) sube.push(k);
        }
        for (const k of sube) idxC[k] = Math.min(this.mats[matC[k]].r.length - 1, idxC[k] + 1);
      }
      // a lienzos (con 1 px de margen para el contorno)
      const C = o.contorno !== false ? 1 : 0, LW = U + C * 2, LH = V + C * 2;
      const col = new Lienzo(LW, LH), luz = new Lienzo(LW, LH);
      const nor = new Int32Array(LW * LH);
      let hayLuz = false;
      for (let v = 0; v < V; v++) for (let u = 0; u < U; u++) {
        const k = v * U + u;
        if (idxC[k] < 0) continue;
        const mt = this.mats[matC[k]], c = mt.r[idxC[k]];
        col.px(u + C, v + C, c);
        if (mt.luz) { luz.px(u + C, v + C, c); hayLuz = true; }
        const nx = nrm[k * 3], ny = nrm[k * 3 + 1], nz = nrm[k * 3 + 2];
        nor[(v + C) * LW + u + C] = (1 << 24) | (Math.round((nx * 0.5 + 0.5) * 255) << 16) | (Math.round((ny * 0.5 + 0.5) * 255) << 8) | Math.round((nz * 0.5 + 0.5) * 255);
      }
      if (C) {
        const tinta = o.tinta || '#140c12';
        for (let v = 0; v < LH; v++) for (let u = 0; u < LW; u++) {
          if (col.get(u, v)) continue;
          let vec = -1, arribaIzq = false;
          for (const [du, dv, ai] of [[0, 1, true], [1, 0, true], [-1, 0, false], [0, -1, false]]) {
            const uu = u - C + du, vv = v - C + dv;
            if (uu < 0 || vv < 0 || uu >= U || vv >= V) continue;
            const k = vv * U + uu;
            if (idxC[k] >= 0) { vec = k; arribaIzq = ai; break; }
          }
          if (vec < 0) continue;
          const mt = this.mats[matC[vec]];
          const borde = mt.contorno || mezclar(mt.r[0], tinta, arribaIzq ? 0.35 : 0.6);
          col.px(u, v, borde);
          nor[v * LW + u] = nor[(v + (arribaIzq ? 1 : -1)) * LW + u] || 0;
        }
      }
      col.n = nor;
      // recorte: fuera las filas y columnas vacías (con el contorno ya puesto)
      let u0 = LW, u1 = -1, v0 = LH, v1 = -1;
      for (let v = 0; v < LH; v++) for (let u = 0; u < LW; u++) if (col.d[v * LW + u]) { if (u < u0) u0 = u; if (u > u1) u1 = u; if (v < v0) v0 = v; if (v > v1) v1 = v; }
      if (u1 < 0) { u0 = 0; u1 = 0; v0 = 0; v1 = 0; }
      const rw = u1 - u0 + 1, rh = v1 - v0 + 1;
      const recorta = (L) => { if (!L) return L; const R = new Lienzo(rw, rh); for (let v = 0; v < rh; v++) for (let u = 0; u < rw; u++) R.d[v * rw + u] = L.d[(v + v0) * LW + u + u0]; return R; };
      const colR = recorta(col), luzR = recorta(luz);
      colR.n = new Int32Array(rw * rh);
      for (let v = 0; v < rh; v++) for (let u = 0; u < rw; u++) colR.n[v * rw + u] = nor[(v + v0) * LW + u + u0];
      // dónde poner el dibujo respecto al centro de la huella del modelo (en casillas): el borde de abajo del
      // dibujo es el punto del suelo más al sur que se ve (la fila de encima del contorno)
      const zSuelo = (v1 - C - (H - 1)) / c;
      const dx = ((u0 + u1 + 1) / 2 - C - W / 2) / 16, dz = (Math.min(D, zSuelo) - D / 2) / 16;
      // la sombra en el suelo: la huella del modelo vista desde arriba, corrida hacia abajo a la derecha
      let sombra = null;
      if (o.sombra !== false) {
        const huella = new Uint8Array(W * D);
        for (let z = 0; z < D; z++) for (let y = 0; y < Math.min(H, o.alturaSombra || H); y++) for (let x = 0; x < W; x++) if (this.m[(z * H + y) * W + x]) huella[z * W + x] = 1;
        const SW = W + 4, SD = D + 4;
        sombra = new Lienzo(SW, SD);
        for (let z = 0; z < SD; z++) for (let x = 0; x < SW; x++) {
          let c = 0, t = 0;
          for (let dz = -2; dz <= 2; dz++) for (let dx = -2; dx <= 2; dx++) {
            const hx = x - 3 + dx, hz = z - 3 + dz;
            t++; if (hx >= 0 && hz >= 0 && hx < W && hz < D && huella[hz * W + hx]) c++;
          }
          const a = Math.round(Math.min(1, c / t * 1.6) * (o.fuerzaSombra ?? 120));
          if (a > 8) sombra.px(x, z, '#000000' + a.toString(16).padStart(2, '0'));
        }
      }
      const marcas = this.proyectarMarcas(C);
      for (const k in marcas) { marcas[k][0] -= u0; marcas[k][1] -= v0; }
      const mapa = (u, v) => { const uu = u + u0 - C, vv = v + v0 - C; if (uu < 0 || vv < 0 || uu >= U || vv >= V) return null; const k = vv * U + uu; return idxC[k] >= 0 ? this.mats[matC[k]] : null; };
      return { color: colR, luz: hayLuz ? luzR : null, sombra, ancho: W, alto: H, fondo: D, borde: C, marcas, dx, dz, mapa };
    }
    // las marcas (ojos, manos…) en píxeles del dibujo horneado
    proyectarMarcas(C = 1) {
      const out = {};
      const c = this.cima ?? 1;
      for (const [n, [x, y, z]] of Object.entries(this.marcas)) out[n] = [Math.floor(x) + C, Math.floor(z * c) - Math.floor(y) + this.h - 1 + C];
      return out;
    }
  }
  function invM(m) {
    // inversa de una matriz de rotación + traslación
    const r = [m[0], m[4], m[8], m[1], m[5], m[9], m[2], m[6], m[10]];
    const t = [m[3], m[7], m[11]];
    return new Float64Array([
      r[0], r[1], r[2], -(r[0] * t[0] + r[1] * t[1] + r[2] * t[2]),
      r[3], r[4], r[5], -(r[3] * t[0] + r[4] * t[1] + r[5] * t[2]),
      r[6], r[7], r[8], -(r[6] * t[0] + r[7] * t[1] + r[8] * t[2]),
    ]);
  }

  // operaciones entre distancias (para recortar una pieza sin tocar lo demás)
  const OP = {
    resta: (a, ...bs) => (x, y, z) => { let d = a(x, y, z); for (const b of bs) d = Math.max(d, -b(x, y, z)); return d; },
    corta: (a, ...bs) => (x, y, z) => { let d = a(x, y, z); for (const b of bs) d = Math.max(d, b(x, y, z)); return d; },
    une: (...as) => (x, y, z) => { let d = 1e9; for (const a of as) d = Math.min(d, a(x, y, z)); return d; },
    // semiespacios: lo que queda por encima / debajo de una altura, delante / detrás de un z
    sobre: (h) => (x, y) => h - y, bajo: (h) => (x, y) => y - h,
  };
  return { Modelo, mat, rampa, mezclar, TEX, ruido, fbm, h3, SDF, OP };
})();

// Registra un modelo horneado: el dibujo, su capa de luz (si tiene), su versión en blanco (golpe) y su sombra.
// Guarda en META_SPR el fondo y el alto para colocarlo en el mundo con los pies en su sitio.
const META_SPR = {};
// Quita un dibujo viejo con ese nombre (para que no quede la capa de luz o el golpe de un dibujo anterior).
function quitarDibujo(nombre) { for (let i = DIBUJOS.length - 1; i >= 0; i--) if (DIBUJOS[i][0] === nombre) DIBUJOS.splice(i, 1); }
function defModelo(nombre, modelo, o = {}) {
  const r = modelo.hornear(o);
  for (const suf of ['_luz', '_B', '_i', '_iB', '_sombra']) quitarDibujo(nombre + suf);
  def(nombre, r.color);
  META_SPR[nombre] = { fondo: r.fondo, alto: r.alto, dx: r.dx, dz: r.dz, marcas: r.marcas };
  if (r.luz) {
    def(nombre + '_luz', r.luz);
    // dónde está la luz en el dibujo (para poner el halo a su altura): centro de los píxeles que brillan
    let sx = 0, sy = 0, n = 0;
    for (let y = 0; y < r.luz.h; y++) for (let x = 0; x < r.luz.w; x++) if (r.luz.d[y * r.luz.w + x]) { sx += x; sy += y; n++; }
    if (n) { META_SPR[nombre].luzX = (sx / n + 0.5 - r.luz.w / 2) / 16; META_SPR[nombre].luzY = (r.luz.h - sy / n - 0.5) / 16; }
  }
  if (r.sombra && o.sombra !== false) def(nombre + '_sombra', r.sombra);
  if (o.blanco) def(nombre + '_B', r.color.blanco());
  if (o.voltear) { const v = r.color.voltear(); v.n = voltearNormales(r.color); def(nombre + '_i', v); if (o.blanco) def(nombre + '_iB', v.blanco()); META_SPR[nombre + '_i'] = Object.assign({}, META_SPR[nombre], { dx: -r.dx }); }
  return r;
}
function voltearNormales(L) {
  const n = new Int32Array(L.w * L.h);
  for (let y = 0; y < L.h; y++) for (let x = 0; x < L.w; x++) {
    const v = L.n[y * L.w + x];
    if (!v) continue;
    const r = (v >> 16) & 255;
    n[y * L.w + L.w - 1 - x] = (v & ~(255 << 16)) | ((255 - r) << 16);
  }
  return n;
}
