'use strict';
// Gusanos dibujados a mano cuadro a cuadro, construidos como las larvas de Core Keeper (ESTUDIO-CK.md): un cuerpo de
// anillos en tres vistas (de lado con la cabeza a la derecha, de frente con la cabeza abajo y de espaldas con la cabeza
// arriba). La larva cabe en una casilla (unos 13×7 de lado y 7×11 de frente), anda en 4 cuadros a 8 fps encogiendo y
// estirando los anillos y ataca en 4 a 10 fps: se alza y se lanza con las pinzas abiertas. El gusano escupidor (como el
// «bigLarva») es más gordo y anda en 6. Cada anillo se pinta píxel a píxel con los papeles de la paleta (sin girar ni
// hornear nada): luz arriba a la izquierda, el surco oscuro donde un anillo tapa al de detrás y el contorno.

const CRI_PX2 = (() => {
  const W = 40, H = 32, OX = 20, OY = 28; // la lámina; el suelo en la fila OY
  const VEC = [[1, 0], [-1, 0], [0, 1], [0, -1]];

  // pinta los anillos (en el orden de dibujo): cada uno con su rampa [_, sombra, medio, luz, brillo]
  function anillos(L, luz, segs) {
    const id = new Int16Array(W * H).fill(-1), tono = new Int8Array(W * H);
    segs.forEach((s, i) => {
      for (let y = Math.floor(s.y - s.ry - 1); y <= Math.ceil(s.y + s.ry + 1); y++) for (let x = Math.floor(s.x - s.rx - 1); x <= Math.ceil(s.x + s.rx + 1); x++) {
        if (x < 0 || y < 0 || x >= W || y >= H) continue;
        const u = (x + 0.5 - s.x) / s.rx, v = (y + 0.5 - s.y) / s.ry, d = u * u + v * v;
        if (d > 1) continue;
        const l = -0.45 * u - 0.9 * v;
        id[y * W + x] = i; tono[y * W + x] = d > 0.7 && l < -0.05 ? 1 : l > 0.55 ? 4 : l > 0.1 ? 3 : 2;
      }
    });
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const i = id[y * W + x]; if (i < 0) continue;
      let t = tono[y * W + x];
      // el surco: lo que queda junto a un anillo que está delante se oscurece
      for (const [dx, dy] of VEC) { const xx = x + dx, yy = y + dy; if (xx < 0 || yy < 0 || xx >= W || yy >= H) continue; if (id[yy * W + xx] > i) { t = Math.max(1, t - 2); break; } }
      const s = segs[i], c = s.rampa[t];
      L.px(x, y, c); if (s.brilla) luz.px(x, y, c);
    }
  }
  // el contorno de fuera (del color más oscuro de la paleta), alrededor de todo lo pintado
  function contorno(L, c) {
    const lleno = (x, y) => x >= 0 && y >= 0 && x < W && y < H && L.d[y * W + x];
    const pon = [];
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) if (!L.d[y * W + x] && VEC.some(([dx, dy]) => lleno(x + dx, y + dy))) pon.push([x, y]);
    for (const [x, y] of pon) L.px(x, y, c);
  }
  // sale del suelo: se baja n píxeles y no se ve lo que queda por debajo del suelo
  function hundir(L, n) {
    if (!n) return;
    const d = L.d.slice(); L.d.fill(null);
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) { const yy = y + n; if (yy <= OY && yy < H) L.d[yy * W + x] = d[y * W + x]; }
  }

  // ---------------------------------------------------------------- dónde va cada anillo
  // k: { fase, amp } al andar (ola que encoge y estira), alza (0..1: se levanta), lanza (px hacia delante), aplasta
  // (0..1), panza (boca arriba). Devuelve los anillos en orden de dibujo y la cabeza.
  function cuerpo(v, k, o) {
    const n = o.perfil.length, R = o.r, P = o.paso, out = [];
    const rho = o.perfil.map((p) => R * p * (1 - (k.aplasta || 0) * 0.25));
    const paso = (i) => P * (1 + (k.amp || 0) * Math.sin((k.fase || 0) - i * 1.2)) * (1 - (k.aplasta || 0) * 0.2) * (i >= n - 3 && v === 'de' ? 1 + (k.lanza || 0) * 0.12 : 1);
    const piv = n - 3, a = k.alza || 0;
    if (v === 'de') {
      let x = OX - ((n - 1) * P) / 2 - 1, y = 0;
      const pos = [];
      for (let i = 0; i < n; i++) {
        const joroba = k.amp ? Math.max(0, -Math.sin((k.fase || 0) - i * 1.2)) * k.amp * 3 : 0;
        if (i > 0 && i > piv && a) { const th = a * (i - piv) * 0.62, [px, py] = pos[i - 1]; pos.push([px + paso(i) * Math.cos(th), py - paso(i) * Math.sin(th)]); continue; }
        if (i > 0) x += paso(i);
        y = OY - rho[i] * (k.panza ? 0.7 : 1) - joroba + (i >= n - 2 && k.lanza ? 0.5 : 0);
        pos.push([x, y]);
      }
      for (let i = 0; i < n; i++) out.push({ x: pos[i][0], y: pos[i][1], rx: Math.max(1.5, rho[i] * o.grueso), ry: rho[i] * (k.panza ? 0.7 : 1), i });
      return { segs: out, cab: out[n - 1] };
    }
    // de frente (la cabeza abajo, hacia ti) o de espaldas (la cabeza arriba): los anillos en fila, acortados
    const ab = v === 'ab';
    let suelo = OY;
    const pos = [];
    for (let j = 0; j < n; j++) { // j: del más cercano al más lejano
      const i = ab ? n - 1 - j : j;
      if (j > 0) suelo -= paso(i) * 0.85;
      let y = suelo - rho[i] * 0.66;
      if (i > piv) y -= a * (i - piv) * 1.8 + (i === n - 1 ? (ab ? -0.5 : 0.4) * (k.lanza || 0) : 0);
      pos[i] = [OX + (k.amp ? Math.sin((k.fase || 0) - i * 1.2) * k.amp * 0.8 : 0), y];
    }
    const orden = ab ? [...Array(n).keys()] : [...Array(n).keys()].reverse(); // primero lo de lejos
    for (const i of orden) out.push({ x: pos[i][0], y: pos[i][1], rx: rho[i] * (i === n - 1 ? 0.92 : 1.05), ry: rho[i] * (k.panza ? 0.5 : 0.62), i });
    return { segs: out, cab: out.find((s) => s.i === n - 1) };
  }

  // ---------------------------------------------------------------- un cuadro
  function cuadro(v, k, o) {
    const L = new Lienzo(W, H), luz = new Lienzo(W, H), pal = o.pal, n = o.perfil.length;
    const { segs, cab } = cuerpo(v, k, o);
    for (const s of segs) { s.rampa = s.i === n - 1 ? pal.cabeza : pal.cuerpo; s.brilla = false; }
    // las patitas, debajo (de lado) o a los lados (de frente y de espaldas)
    if (!k.panza) for (const s of segs) {
      if (s.i === 0 || s.i === n - 1 || (o.patas && !o.patas.includes(s.i))) continue;
      const m = (s.i + Math.floor((k.fase || 0) * 0.64)) % 2;
      if (v === 'de') { if (s.y + s.ry >= OY - 1.5) L.px(Math.round(s.x) - m, OY, pal.pata); }
      else { L.px(Math.round(s.x - s.rx) - 1, Math.round(s.y) + m, pal.pata); L.px(Math.round(s.x + s.rx), Math.round(s.y) + 1 - m, pal.pata); }
    }
    anillos(L, luz, segs);
    const hx = Math.round(cab.x), hy = Math.round(cab.y), abre = k.abre || 0;
    // manchas, venas o puntos que brillan sobre los anillos (en lo alto de cada uno)
    if (o.manchas) for (const s of segs) {
      if (!o.manchas.anillos.includes(s.i)) continue;
      const x = Math.round(s.x - s.rx * 0.2), y = Math.round(s.y - s.ry * 0.55);
      if (v === 'ar' || v === 'ab' || s.i !== n - 1) { L.px(x, y, o.manchas.c); if (o.manchas.luz) luz.px(x, y, o.manchas.c); if (o.manchas.doble) { L.px(x + 1, y, o.manchas.c2 || o.manchas.c); if (o.manchas.luz) luz.px(x + 1, y, o.manchas.c2 || o.manchas.c); } }
    }
    // la boca y las pinzas
    const M = pal.pinza, m = pal.pinza2;
    if (v === 'de') {
      const fx = Math.round(cab.x + cab.rx) - 1;
      if (o.boca === 'ventosa') { L.px(fx + 1, hy, M); L.px(fx + 1, hy + 1, M); L.px(fx + 1, hy - 1 - abre, m); L.px(fx + 1, hy + 2 + abre, m); }
      else if (k.panza) { L.px(fx + 1, hy + 1, M); }
      else { L.px(fx + 1, hy - abre, M); L.px(fx + 2, hy - abre - (abre ? 1 : 0), m); L.px(fx + 1, hy + 2 + abre, M); L.px(fx + 2, hy + 2 + abre, m); if (abre) L.px(fx + 1, hy + 1, pal.boca); }
      if (o.ojos) { L.px(hx, hy - 1, o.ojos); luz.px(hx, hy - 1, o.ojos); }
    } else if (v === 'ab') {
      const by = Math.round(cab.y + cab.ry) - 1;
      if (abre || k.alza > 0.4) { // se ve la boca abierta
        const r = Math.max(1, Math.round(cab.rx * 0.45));
        for (let x = hx - r; x < hx + r; x++) L.px(x, by - 1, pal.boca);
        L.px(hx - r - 1, by, M); L.px(hx + r, by, M); L.px(hx - r - 1 - abre, by + 1, m); L.px(hx + r + abre, by + 1, m);
      } else if (o.boca === 'ventosa') { L.px(hx - 1, by, M); L.px(hx, by, M); }
      else { L.px(hx - 2, by + 1, M); L.px(hx + 1, by + 1, M); L.px(hx - 2, by + 2, m); L.px(hx + 1, by + 2, m); }
      if (o.ojos) { for (const x of [hx - 2, hx + 1]) { L.px(x, hy - 1, o.ojos); luz.px(x, hy - 1, o.ojos); } }
    } else if (o.boca !== 'ventosa') { // de espaldas: asoman las puntas de las pinzas
      const ty = Math.round(cab.y - cab.ry) - (k.alza ? 1 : 0);
      L.px(hx - 2 - abre, ty, M); L.px(hx + 1 + abre, ty, M); L.px(hx - 2 - abre, ty - 1, m); L.px(hx + 1 + abre, ty - 1, m);
    }
    if (k.escupe) { const [ex, ey] = v === 'de' ? [Math.round(cab.x + cab.rx) + 2, hy + 1] : v === 'ab' ? [hx, Math.round(cab.y + cab.ry) + 1] : [hx, Math.round(cab.y - cab.ry) - 3]; for (const [dx, dy, c] of [[0, 0, pal.acido[2]], [1, 0, pal.acido[3]], [0, 1, pal.acido[1]], [1, 1, pal.acido[2]]]) { L.px(ex + dx, ey + dy, c); luz.px(ex + dx, ey + dy, c); } }
    contorno(L, pal.K);
    hundir(L, k.hundido || 0); hundir(luz, k.hundido || 0);
    return { color: MANO.normales(L), luz, puntos: { cabeza: [hx, hy], boca: [v === 'de' ? Math.round(cab.x + cab.rx) + 1 : hx, hy] } };
  }

  // ---------------------------------------------------------------- las animaciones
  // p: se alza (dos cuadros), g: se lanza con las pinzas abiertas y vuelve; r: se encoge; m: se retuerce, se aplasta y
  // queda boca arriba; n: sale del suelo.
  function cuadros(o) {
    const na = o.anims.a.n, np = o.anims.p.n, ng = o.anims.g.n;
    return {
      q: () => ({}),
      a: (v, f) => ({ fase: (f / na) * Math.PI * 2, amp: 0.3 }),
      p: (v, f) => ({ alza: ((f + 1) / np) * 0.9, abre: f === np - 1 ? 1 : 0 }),
      g: (v, f) => (f < ng - 1 ? { lanza: 3 - f, abre: 1, escupe: o.escupe && f === 0 } : { lanza: 1, alza: 0.15 }),
      r: (v, f) => (f === 0 ? { aplasta: 0.7 } : { aplasta: 0.25 }),
      m: (v, f) => [{ alza: 0.5, abre: 1 }, { aplasta: 0.8 }, { panza: true, aplasta: 0.6 }, { panza: true, aplasta: 1 }][f],
      n: (v, f) => ({ hundido: [12, 8, 4, 0][f] }),
    };
  }
  function gusano(o) {
    const A = cuadros(o);
    return { manual: (anim, f, v) => cuadro(v, A[anim](v, f), o), anims: o.anims, tiempos: o.tiempos, W, H, ox: OX, oy: OY, px: 1, yaw: 0, zancada: 12, fpsQuieta: 10, vistas: true };
  }

  // ---------------------------------------------------------------- las fichas
  const PINZA = { pinza: '#2a1028', pinza2: '#5a2a50', boca: '#1a0610' };
  const FICHAS = {
    // gusano roedor (como la larva de Core Keeper): quieto 1, anda 4 a 8 fps, ataca 4 a 10 fps con pausas 0, 0, 1, 0
    larva: gusano({
      perfil: [0.55, 0.8, 0.95, 1, 0.95, 0.82], r: 3.2, paso: 2.1, grueso: 0.72,
      pal: Object.assign({ K: '#2a1814', cuerpo: [0, '#8e6852', '#c49e7c', '#e4c6a2', '#faecd0'], cabeza: [0, '#7a3420', '#b45a32', '#de8444', '#f6b46a'], pata: '#b45a32' }, PINZA),
      manchas: { anillos: [1, 3], c: '#b0505a' },
      anims: { q: { n: 1, bucle: true }, a: { n: 4, bucle: true }, p: { n: 2 }, g: { n: 2 }, r: { n: 2 }, m: { n: 4 }, n: { n: 4 } },
      tiempos: { q: { fps: 10 }, a: { fps: 8 }, p: { fps: 10 }, g: { fps: 10, pausas: [1, 0] } },
    }),
    // la de la Podre: oscura y con puntos de Podre que brillan
    larva_moho: gusano({
      perfil: [0.55, 0.8, 0.95, 1, 0.95, 0.82], r: 3.2, paso: 2.1, grueso: 0.72,
      pal: Object.assign({ K: '#08040a', cuerpo: [0, '#1e1622', '#342838', '#4e3e52', '#6a5670'], cabeza: [0, '#2a1428', '#4a2442', '#6a3460', '#8a4a7c'], pata: '#4a2442' }, PINZA, { pinza: '#120612', pinza2: '#ff3cb4' }),
      manchas: { anillos: [1, 2, 4], c: '#ff3cb4', c2: '#b0207a', luz: true, doble: true },
      anims: { q: { n: 1, bucle: true }, a: { n: 4, bucle: true }, p: { n: 2 }, g: { n: 2 }, r: { n: 2 }, m: { n: 4 }, n: { n: 4 } },
      tiempos: { q: { fps: 10 }, a: { fps: 8 }, p: { fps: 10 }, g: { fps: 10, pausas: [1, 0] } },
    }),
    // gusano escupidor (como el «bigLarva»): gordo, anda 6 a 10 fps y ataca 6 a 10 fps con pausas 0, 0, 2, 0, 0, 0
    // (tres alzándose y tres escupiendo); manchas de ácido que brillan
    larva_grande: gusano({
      perfil: [0.5, 0.72, 0.9, 1, 1, 0.92, 0.8], r: 5, paso: 2.9, grueso: 0.7, escupe: true,
      pal: Object.assign({ K: '#24180a', cuerpo: [0, '#6a5a3c', '#968660', '#bcac82', '#dccea4'], cabeza: [0, '#5a4a1c', '#8a7a34', '#b4a44c', '#d8cc74'], pata: '#6a5a24', acido: ['#1a3a04', '#4a9a10', '#8ae030', '#d8ff90'] }, PINZA),
      manchas: { anillos: [2, 4, 5], c: '#8ae030', c2: '#4a9a10', luz: true, doble: true },
      anims: { q: { n: 1, bucle: true }, a: { n: 6, bucle: true }, p: { n: 3 }, g: { n: 3 }, r: { n: 2 }, m: { n: 4 }, n: { n: 4 } },
      tiempos: { q: { fps: 10 }, a: { fps: 10 }, p: { fps: 10, pausas: [0, 0, 2] }, g: { fps: 10 } },
    }),
    // la sanguijuela de la Ciénaga: larga y negra, con ventosa en vez de pinzas y sin patas
    sanguijuela: gusano({
      perfil: [0.5, 0.7, 0.85, 0.95, 1, 1, 0.92, 0.8], r: 3.6, paso: 2.6, grueso: 0.8, boca: 'ventosa', patas: [],
      pal: Object.assign({ K: '#030403', cuerpo: [0, '#141a14', '#26302a', '#3a4840', '#56685c'], cabeza: [0, '#1a1414', '#2e2224', '#463234', '#644648'], pata: '#141a14' }, PINZA, { pinza: '#5a1a1a', pinza2: '#a83a3a', boca: '#3a0606' }),
      manchas: { anillos: [2, 4, 6], c: '#7a9a6a', c2: '#56685c', doble: true },
      anims: { q: { n: 1, bucle: true }, a: { n: 6, bucle: true }, p: { n: 2 }, g: { n: 2 }, r: { n: 2 }, m: { n: 4 }, n: { n: 4 } },
      tiempos: { q: { fps: 10 }, a: { fps: 10 }, p: { fps: 10 }, g: { fps: 10, pausas: [1, 0] } },
    }),
  };
  // ---------------------------------------------------------------- el gruto mole (como el «cavelingBrute»)
  // Grande y encorvado, la cabeza hundida entre los hombros y la cuchilla oxidada al hombro. Se pinta con masas
  // (elipses y cápsulas con luz arriba a la izquierda, el borde oscuro y el contorno) y la cabeza de gruto a mano.
  const BW = 64, BH = 58, BOX = 32, BOY = 54;
  // dentro de una forma: elipse { e: [cx, cy, rx, ry] } o cápsula { c: [x0, y0, x1, y1, r0, r1] }; da (u, v) normalizado
  function enForma(f, x, y) {
    if (f.e) { const [cx, cy, rx, ry] = f.e, u = (x + 0.5 - cx) / rx, v = (y + 0.5 - cy) / ry; return u * u + v * v <= 1 ? [u, v] : null; }
    const [x0, y0, x1, y1, r0, r1 = r0] = f.c, dx = x1 - x0, dy = y1 - y0, l2 = dx * dx + dy * dy || 1;
    const t = Math.max(0, Math.min(1, ((x + 0.5 - x0) * dx + (y + 0.5 - y0) * dy) / l2)), r = r0 + (r1 - r0) * t;
    const u = (x + 0.5 - x0 - dx * t) / r, v = (y + 0.5 - y0 - dy * t) / r;
    return u * u + v * v <= 1 ? [u, v] : null;
  }
  function masas(L, formas, ancho, alto) {
    const id = new Int16Array(ancho * alto).fill(-1), tono = new Int8Array(ancho * alto);
    formas.forEach((f, i) => {
      const b = f.e ? [f.e[0] - f.e[2], f.e[1] - f.e[3], f.e[0] + f.e[2], f.e[1] + f.e[3]] : [Math.min(f.c[0], f.c[2]) - Math.max(f.c[4], f.c[5] || 0), Math.min(f.c[1], f.c[3]) - Math.max(f.c[4], f.c[5] || 0), Math.max(f.c[0], f.c[2]) + Math.max(f.c[4], f.c[5] || 0), Math.max(f.c[1], f.c[3]) + Math.max(f.c[4], f.c[5] || 0)];
      for (let y = Math.max(0, Math.floor(b[1]) - 1); y <= Math.min(alto - 1, Math.ceil(b[3]) + 1); y++) for (let x = Math.max(0, Math.floor(b[0]) - 1); x <= Math.min(ancho - 1, Math.ceil(b[2]) + 1); x++) {
        const uv = enForma(f, x, y); if (!uv) continue;
        const [u, v] = uv, d = u * u + v * v, l = -0.5 * u - 0.85 * v;
        id[y * ancho + x] = i; tono[y * ancho + x] = d > 0.68 && l < -0.1 ? 1 : l > 0.55 ? 4 : l > 0.05 ? 3 : 2;
      }
    });
    for (let y = 0; y < alto; y++) for (let x = 0; x < ancho; x++) {
      const i = id[y * ancho + x]; if (i < 0) continue;
      let t = tono[y * ancho + x];
      for (const [dx, dy] of VEC) { const xx = x + dx, yy = y + dy; if (xx < 0 || yy < 0 || xx >= ancho || yy >= alto) continue; const j = id[yy * ancho + xx]; if (j > i && !formas[j].sinSurco) { t = Math.max(1, t - 2); break; } }
      L.px(x, y, formas[i].rampa[t]);
    }
  }
  // la cuchilla: mango desde el puño (dirección a) y una hoja ancha y oxidada con el filo brillante
  function cuchilla(L, pal, F, a, largo = 17) {
    const d = [Math.cos(a), Math.sin(a)], n = [-d[1], d[0]], m = 4, A = 6;
    for (let t = -1; t <= m; t++) { L.px(Math.round(F[0] + d[0] * t), Math.round(F[1] + d[1] * t), t % 2 ? pal.W : pal.w); }
    for (let y = 0; y < BH; y++) for (let x = 0; x < BW; x++) {
      const px = x + 0.5 - F[0], py = y + 0.5 - F[1], t = px * d[0] + py * d[1], s = px * n[0] + py * n[1];
      if (t < m || t > largo) continue;
      const ancho = A - (t > largo - 3 ? (t - (largo - 3)) * 1.3 : 0);
      if (s < -1 || s > ancho) continue;
      L.px(x, y, s > ancho - 1.2 ? pal.filo : s < 0 ? pal.oxido[0] : (Math.floor(t * 3 + s * 5) % 7 === 0 ? pal.oxido[1] : s < 2 ? pal.oxido[3] : pal.oxido[2]));
    }
  }
  // el arco del golpe (como los de Core Keeper: una franja clara que se deshace hacia la cola)
  function arco(L, pal, cx, cy, r, a0, a1) {
    const n = Math.ceil(Math.abs(a1 - a0) * r * 1.6);
    for (let i = 0; i <= n; i++) {
      const t = i / n, a = a0 + (a1 - a0) * t, w = 1 + Math.round(t * 2.5);
      for (let k = 0; k < w; k++) { const rr = r - k, x = Math.round(cx + Math.cos(a) * rr), y = Math.round(cy + Math.sin(a) * rr); if (t < 0.35 && (x + y) % 2) continue; L.px(x, y, k === 0 ? pal.U : pal.u); }
    }
  }
  function tumbar(L, ancho, alto, ox, oy) {
    const c = L.d.slice(); L.d.fill(null);
    for (let y = 0; y < alto; y++) for (let x = 0; x < ancho; x++) { const v = c[y * ancho + x]; if (!v) continue; const nx = ox + (oy - y) - 12, ny = oy - (x - ox) - 4; if (nx >= 0 && ny >= 0 && nx < ancho && ny < alto) L.d[ny * ancho + nx] = v; }
    let fondo = -1; for (let y = 0; y < alto; y++) for (let x = 0; x < ancho; x++) if (L.d[y * ancho + x]) fondo = y;
    const baja = oy - fondo; if (baja) { const d = L.d.slice(); L.d.fill(null); for (let y = 0; y < alto; y++) for (let x = 0; x < ancho; x++) { const yy = y + baja; if (yy >= 0 && yy < alto) L.d[yy * ancho + x] = d[y * ancho + x]; } }
  }
  function cuadroBruto(v, k, o) {
    const L = new Lienzo(BW, BH), luz = new Lienzo(BW, BH), pal = o.pal, R = o.rampas;
    const b = k.cuerpo || 0, l = k.inclina || 0, pA = k.pA || 0, pD = k.pD || 0;
    const det = [], del = [];
    let cabeza, F, aMaza, ojosY;
    if (v === 'de') {
      const x = BOX - 3, y = BOY, S = [x + 3 + l, y - 19 + b], S2 = [x - 2 + l * 0.6, y - 20 + b];
      F = k.F ? [S[0] + k.F[0], S[1] + k.F[1]] : [S[0] + 5, S[1]]; aMaza = k.a ?? -2.6;
      const E = [(S[0] + F[0]) / 2 + (k.codo || 2), (S[1] + F[1]) / 2 + 3];
      det.push({ c: [S2[0], S2[1], S2[0] + 3 + (k.bA || 0), S2[1] + 9, 2.4, 2.6], rampa: R.atras }, { e: [S2[0] + 3 + (k.bA || 0), S2[1] + 10, 2.6, 2.4], rampa: R.atras });
      det.push({ c: [x - 2, y - 9 + b, x - 4 + pA, y - 1, 2.6, 2.4], rampa: R.atras }, { e: [x - 3 + pA, y - 1, 3.2, 1.5], rampa: R.atras });
      det.push({ e: [x + l * 0.5, y - 14 + b, 9, 8], rampa: R.piel }, { e: [x - 3 + l * 0.3, y - 20 + b, 8.5, 6.5], rampa: R.manto, sinSurco: true });
      det.push({ e: [x + 3 + l * 0.6, y - 11 + b, 5.5, 4.5], rampa: R.barriga });
      det.push({ e: [x, y - 7 + b, 7.5, 2.6], rampa: R.cuero }, { c: [x + 3, y - 8 + b, x + 3, y - 4 + b, 1.8], rampa: R.cuero });
      det.push({ c: [x + 2, y - 9 + b, x + 3 + pD, y - 1, 2.8, 2.6], rampa: R.piel }, { e: [x + 4 + pD, y - 1, 3.4, 1.6], rampa: R.piel });
      cabeza = [CRI_PX.GRUTO.cabeza.de, x + 2 + l, y - 28 + b + (k.cab || 0)];
      del.push({ c: [S[0], S[1], E[0], E[1], 3, 2.8], rampa: R.piel }, { c: [E[0], E[1], F[0], F[1], 2.8, 2.6], rampa: R.piel }, { e: [F[0], F[1], 3, 2.8], rampa: R.piel });
    } else {
      const ab = v === 'ab', x = BOX, y = BOY;
      F = k.F ? [x + k.F[0], y + b + k.F[1]] : [x + 9, y - 17 + b]; aMaza = k.a ?? -1.9;
      det.push({ c: [x - 3, y - 8 + b, x - 4 + pA, y - 1, 2.8, 2.6], rampa: R.piel }, { e: [x - 4 + pA, y - 1, 3, 1.6], rampa: R.piel });
      det.push({ c: [x + 3, y - 8 + b, x + 4 + pD, y - 1, 2.8, 2.6], rampa: R.piel }, { e: [x + 4 + pD, y - 1, 3, 1.6], rampa: R.piel });
      if (ab) det.push({ e: [x, y - 20 + b, 11, 5.5], rampa: R.manto });
      det.push({ e: [x, y - 14 + b, 10, 8], rampa: R.piel });
      if (ab) det.push({ e: [x, y - 10 + b, 6, 4.5], rampa: R.barriga });
      det.push({ e: [x, y - 6 + b, 7.5, 2.4], rampa: R.cuero });
      if (ab) det.push({ c: [x, y - 7 + b, x, y - 3 + b, 1.8], rampa: R.cuero });
      cabeza = [CRI_PX.GRUTO.cabeza[v], x - 6, y - (ab ? 27 : 30) + b + (k.cab || 0)];
      const BI = k.BI ? [x + k.BI[0], y + b + k.BI[1]] : [x - 11, y - 8 + b];
      del.push({ c: [x - 9, y - 18 + b, BI[0], BI[1], 3, 2.8], rampa: R.piel }, { e: [BI[0], BI[1] + 1, 3, 2.8], rampa: R.piel });
      del.push({ c: [x + 9, y - 18 + b, F[0], F[1] + 2, 3, 2.8], rampa: R.piel }, { e: [F[0], F[1], 3, 2.8], rampa: R.piel });
      if (!ab) del.push({ e: [x, y - 20 + b, 11, 5.5], rampa: R.manto, sinSurco: true });
    }
    const mazaDetras = k.mazaDetras ?? (v === 'de' ? Math.cos(aMaza) < 0.2 : v === 'ab' ? Math.sin(aMaza) < 0 : Math.sin(aMaza) > -0.5);
    if (mazaDetras) cuchilla(L, pal, F, aMaza, o.largo);
    masas(L, det, BW, BH);
    if (v === 'de') { masas(L, del, BW, BH); if (!mazaDetras) cuchilla(L, pal, F, aMaza, o.largo); }
    {
      CRI_PX.poner(L, cabeza[0], pal, cabeza[1], cabeza[2]);
      const hx = cabeza[1], hy = cabeza[2];
      // los cuernos de toro de la máscara
      if (v === 'de') { for (const [dx, dy, c] of [[9, 1, 'u'], [10, 0, 'U'], [11, -1, 'U'], [5, 0, 'o'], [4, -1, 'u']]) L.px(hx + dx, hy + dy, pal[c]); }
      else for (const [dx, dy, c] of [[1, 3, 'u'], [0, 2, 'U'], [0, 1, 'U'], [11, 3, 'o'], [12, 2, 'u'], [12, 1, 'u']]) L.px(hx + dx, hy + dy, pal[c]);
      if (v !== 'ar' && !k.ojosC) for (let yy = hy; yy < hy + 11; yy++) for (let xx = hx; xx < hx + 13; xx++) { const q = L.get(xx, yy); if (q === pal.y || q === pal.Y) luz.px(xx, yy, q); }
      if (k.ojosC) for (let yy = hy; yy < hy + 11; yy++) for (let xx = hx; xx < hx + 13; xx++) { const q = L.get(xx, yy); if (q === pal.y || q === pal.Y) L.px(xx, yy, pal.E); }
      ojosY = hy + 6;
    }
    if (v !== 'de') { masas(L, del, BW, BH); if (!mazaDetras) cuchilla(L, pal, F, aMaza, o.largo); }
    if (k.estela) arco(L, pal, ...k.estela.map((n, i) => n + (i === 0 ? (v === 'de' ? BOX - 3 : BOX) : i === 1 ? BOY : 0)));
    contorno2(L, pal.K, BW, BH);
    if (k.tumbado) tumbar(L, BW, BH, BOX, BOY);
    if (k.hundido) { const d = L.d.slice(); L.d.fill(null); for (let y = 0; y < BH; y++) for (let x = 0; x < BW; x++) { const yy = y + k.hundido; if (yy <= BOY && yy < BH) L.d[yy * BW + x] = d[y * BW + x]; } luz.d.fill(null); }
    if (k.tumbado) luz.d.fill(null);
    return { color: MANO.normales(L), luz, puntos: { cabeza: [BOX, (ojosY || BOY - 22)] } };
  }
  function contorno2(L, c, ancho, alto) {
    const lleno = (x, y) => x >= 0 && y >= 0 && x < ancho && y < alto && L.d[y * ancho + x];
    const pon = [];
    for (let y = 0; y < alto; y++) for (let x = 0; x < ancho; x++) if (!L.d[y * ancho + x] && VEC.some(([dx, dy]) => lleno(x + dx, y + dy))) pon.push([x, y]);
    for (const [x, y] of pon) L.px(x, y, c);
  }
  // cuadros del mole (los tiempos del cavelingBrute: quieto 5 con pausas 4, 0, 0, 4, 0; anda 10; ataca 9 con pausas
  // 0, 0, 2, 0, 0, 1, 0, 0, 0: tres alzando la cuchilla y seis del golpe al suelo, quieto y recuperarse)
  const BRUTO = {
    q: (v, f) => ({ cuerpo: [0, 0, 1, 1, 0][f], cab: [0, 0, 0, 1, 0][f] }),
    a: (v, f) => { const s = Math.sin((f / 10) * Math.PI * 2), c = Math.cos((f / 10) * Math.PI * 2); return v === 'de' ? { pA: Math.round(-s * 3), pD: Math.round(s * 3), cuerpo: Math.abs(c) > 0.8 ? 1 : 0, bA: Math.round(s * 1.5), inclina: 1 } : { pA: 0, pD: 0, cuerpo: Math.abs(c) > 0.8 ? 1 : 0, cab: 0, BI: [-11 + Math.round(s), -8], F: [9, -17 + Math.round(-s)], ...(v === 'ab' || v === 'ar' ? { pA: Math.round(s * 1.5), pD: Math.round(-s * 1.5) } : {}) }; },
    p: (v, f) => v === 'de'
      ? [{ inclina: -1, F: [2, -3], a: -2.2, codo: 1 }, { inclina: -2, F: [-1, -6], a: 2.9, codo: -1, cuerpo: -1 }, { inclina: -3, F: [-2, -7], a: 2.5, codo: -2, cuerpo: -1 }][f]
      : [{ F: [6, -24], a: -1.75, cuerpo: 0 }, { F: [2, -31], a: -1.6, cuerpo: -1, BI: [-3, -31] }, { F: [1, -33], a: -1.57, cuerpo: -1, BI: [-2, -33] }][f],
    g: (v, f) => v === 'de'
      ? [{ inclina: 1, F: [4, -7], a: -0.9, codo: 1, estela: [3, -18, 21, -2.6, -0.8] }, { inclina: 3, F: [7, 3], a: 0.7, codo: 1, cuerpo: 1, estela: [3, -18, 21, -1.6, 0.75] }, { inclina: 3, F: [7, 4], a: 0.8, cuerpo: 2, cab: 1 }, { inclina: 3, F: [7, 4], a: 0.8, cuerpo: 2, cab: 1 }, { inclina: 2, F: [6, 1], a: -0.3, cuerpo: 1 }, { inclina: 1, F: [5, -1], a: -1.6 }][f]
      : v === 'ab'
        ? [{ F: [1, -16], a: 1.57, cuerpo: 1, BI: [-2, -16], mazaDetras: false, estela: [0, -26, 22, -1.3, 1.5] }, { F: [1, -8], a: 1.57, cuerpo: 2, cab: 2, BI: [-2, -8], mazaDetras: false, estela: [0, -24, 21, -0.6, 1.57] }, { F: [1, -8], a: 1.57, cuerpo: 3, cab: 2, BI: [-2, -8], mazaDetras: false }, { F: [1, -8], a: 1.57, cuerpo: 3, cab: 2, BI: [-2, -8], mazaDetras: false }, { F: [5, -14], a: 0.6, cuerpo: 1, mazaDetras: false }, { F: [8, -17], a: -1.3 }][f]
        : [{ F: [1, -26], a: -1.57, cuerpo: 1, BI: [-2, -26], mazaDetras: false, estela: [0, -14, 22, -1.6, -4.4] }, { F: [1, -22], a: -1.57, cuerpo: 2, cab: 1, BI: [-2, -22], mazaDetras: true }, { F: [1, -22], a: -1.57, cuerpo: 2, cab: 1, BI: [-2, -22], mazaDetras: true }, { F: [1, -22], a: -1.57, cuerpo: 2, cab: 1, BI: [-2, -22], mazaDetras: true }, { F: [6, -22], a: -1.8, cuerpo: 1 }, { F: [9, -17], a: -1.9 }][f],
    r: (v, f) => (f === 0 ? { inclina: -2, cuerpo: 1, ojosC: true, cab: -1 } : { inclina: -1 }),
    m: (v, f) => [{ inclina: -2, ojosC: true, cuerpo: 1 }, { inclina: 2, ojosC: true, cuerpo: 4, cab: 1 }, { tumbado: true, ojosC: true }, { tumbado: true, ojosC: true }][f],
    n: (v, f) => ({ hundido: [30, 20, 10, 0][f] }),
  };
  function mole(o) {
    return { manual: (anim, f, v) => cuadroBruto(v, BRUTO[anim](v, f), o), anims: { q: { n: 5, bucle: true }, a: { n: 10, bucle: true }, p: { n: 3 }, g: { n: 6 }, r: { n: 2 }, m: { n: 4 }, n: { n: 4 } },
      tiempos: { q: { fps: 10, pausas: [4, 0, 0, 4, 0] }, a: { fps: 10 }, p: { fps: 10, pausas: [0, 0, 5] }, g: { fps: 10, pausas: [0, 0, 1, 2, 0, 0] } },
      W: BW, H: BH, ox: BOX, oy: BOY, px: 1, yaw: 0, zancada: 30, fpsQuieta: 10, vistas: true };
  }
  FICHAS.gruto_bruto = mole({
    largo: 18,
    pal: CRI_PX.paleta({ K: '#1a1008', k: '#33200f', c: '#523418', C: '#744c26', E: '#140a08', y: '#ff7a2a', Y: '#ffd080', filo: '#d8dce4', oxido: ['#2a160c', '#5a2a14', '#8a4422', '#a8603a'] }),
    rampas: {
      piel: [0, '#26301c', '#42522e', '#5e7240', '#7c9256'], atras: [0, '#1a2214', '#2e3a22', '#40502e', '#52643a'],
      barriga: [0, '#46563a', '#687a50', '#8a9c6a', '#a8b886'], manto: [0, '#2a1c14', '#4a3424', '#6a4e36', '#8c6c4c'], cuero: [0, '#2a160c', '#4a2a16', '#6a4226', '#8a5a34'],
    },
  });
  function registrar() { for (const [t, d] of Object.entries(FICHAS)) CRI.DEF[t] = d; }
  registrar();
  return { gusano, cuadro, FICHAS, W, H, OX, OY };
})();
