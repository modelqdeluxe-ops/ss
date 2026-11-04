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
      const f = formas[i];
      let r = f.rampa;
      if (f.rayas) { const [ex, ey, an, r2] = f.rayas, c = f.e || f.c; if (Math.floor(((x + 0.5 - c[0]) * ex + (y + 0.5 - c[1]) * ey) / an + 100) % 2) r = r2; }
      if (f.trama && (x + y) % 2 && t > 1) t = Math.max(1, t - 1);
      L.px(x, y, r[t]);
      if (f.luz) f.luz.px(x, y, r[t]);
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
  // ---------------------------------------------------------------- voladores (avispa y moscarda)
  // Como las moscas de Core Keeper: aletean a 20 fps (2 cuadros quieta, 4 al moverse). Al preparar el picotazo se
  // quedan en el aire con el abdomen curvado (aguijón hacia ti) y al lanzarse van inclinadas con las alas atrás.
  const VW = 40, VH = 36, VOX = 20, VOY = 32;
  function cuadroVolador(v, k, o) {
    const L = new Lienzo(VW, VH), luz = new Lienzo(VW, VH), R = o.rampas, pal = o.pal, g = o.gordo ? 1.25 : 1;
    const C = [VOX, VOY - 10 + (k.bob || 0) + (k.cae || 0)], ala = k.ala ?? 0; // ala: 0 arriba, 1 en medio, 2 abajo, 3 atrás
    const formas = [], delante = [];
    const alaForma = (cx, cy, rx, ry, atras) => ({ e: [cx, cy, rx, ry], rampa: atras ? R.alaAtras : R.ala, trama: true, sinSurco: true });
    const alas = (lado) => { // lado: -1 izquierda / detrás, 1 derecha / delante
      if (k.plegadas) return [];
      if (v === 'de') {
        const dx = lado < 0 ? 2 : 0;
        return [ala === 0 ? alaForma(C[0] - 1 + dx, C[1] - 6 * g, 2.4, 5 * g, lado < 0) : ala === 1 ? alaForma(C[0] - 3 + dx, C[1] - 4 * g, 4, 3.2, lado < 0) : ala === 2 ? alaForma(C[0] - 4 + dx, C[1] - 1, 5, 2, lado < 0) : alaForma(C[0] - 6 + dx, C[1] - 2, 5.5, 1.6, lado < 0)];
      }
      const s = lado;
      return [ala === 0 ? alaForma(C[0] + s * 5 * g, C[1] - 5, 2.8, 5, false) : ala === 1 ? alaForma(C[0] + s * 6 * g, C[1] - 3, 4, 3.4, false) : ala === 2 ? alaForma(C[0] + s * 7 * g, C[1] - 1, 5, 2.4, false) : alaForma(C[0] + s * 4 * g, C[1] - 2, 3, 2, false)];
    };
    const curva = k.curva || 0, inc = k.inclina || 0;
    if (v === 'de') {
      formas.push(...alas(-1));
      const a0 = [C[0] - 2, C[1] + 1 + inc * 0.5], a1 = [C[0] - 7 * g + curva * 4, C[1] + 3 + curva * 4 - inc * 1.5];
      formas.push({ c: [a0[0], a0[1], a1[0], a1[1], 3.3 * g, 2.1 * g], rampa: R.abdomen, rayas: o.rayas ? [a1[0] - a0[0], a1[1] - a0[1], 1.6 * Math.hypot(a1[0] - a0[0], a1[1] - a0[1]), R.raya] : null });
      formas.push({ e: [C[0], C[1] + inc * 0.3, 3.2 * g, 3 * g], rampa: R.cuerpo });
      formas.push({ e: [C[0] + 4 * g, C[1] - 1 + inc, 2.6, 2.4], rampa: R.cabeza, luz: o.cabezaLuz ? luz : null });
      delante.push(...alas(1));
      // patas, el aguijón y los ojos
      masas(L, formas, VW, VH);
      if (o.aguijon) { const d = [a1[0] - a0[0], a1[1] - a0[1]], n = Math.hypot(...d) || 1, t0 = [a1[0] + (d[0] / n) * 1.6 * g, a1[1] + (d[1] / n) * 1.6 * g]; CRI_PX.linea(L, t0, [t0[0] + (d[0] / n) * 2.5, t0[1] + (d[1] / n) * 2.5], pal.aguijon); }
      ojosVolador(L, luz, v, [C[0] + 4 * g, C[1] - 1 + inc], o, k);
      masas(L, delante, VW, VH);
    } else {
      const ab = v === 'ab', cab = [C[0], C[1] + (ab ? 2.5 : -3)], abd = [C[0], C[1] + (ab ? -3.5 + curva * 7 : 3.5 - curva * 2)];
      formas.push(...alas(-1), ...alas(1));
      const abdF = { e: [abd[0], abd[1], 3.6 * g, 3.2 * g], rampa: R.abdomen, rayas: o.rayas ? [0, 1, 1.6, R.raya] : null };
      const cabF = { e: [cab[0], cab[1], 2.8 * g * 0.9, 2.4], rampa: R.cabeza, luz: o.cabezaLuz ? luz : null };
      const torF = { e: [C[0], C[1], 3.3 * g, 2.8 * g], rampa: R.cuerpo };
      if (ab) formas.splice(0, 0, ...(curva > 0.5 ? [] : [abdF])), formas.push(torF), curva > 0.5 && formas.push(abdF), formas.push(cabF);
      else formas.splice(0, 0, cabF), formas.push(torF, abdF);
      masas(L, formas, VW, VH);
      if (o.aguijon && (!ab || curva > 0.5)) { const y0 = Math.round(abd[1] + 3.2 * g); CRI_PX.linea(L, [abd[0], y0], [abd[0], y0 + 2], pal.aguijon); }
      if (ab) ojosVolador(L, luz, v, cab, o, k);
    }
    if (k.estela) for (let i = 0; i < 3; i++) { const y = Math.round(C[1] - 3 + i * 3), x0 = v === 'de' ? C[0] - 13 - i : C[0] - 1; if (v === 'de') for (let x = x0; x < x0 + 5 - i; x++) L.px(x, y, (x + i) % 2 ? R.ala[3] : R.ala[4]); }
    contorno2(L, pal.K, VW, VH);
    // las patas finas (sin contorno), colgando
    if (!k.plegadas) {
      if (v === 'de') for (let i = 0; i < 3; i++) { const x0 = Math.round(C[0] - 1 + i * 1.6), y0 = Math.round(C[1] + 3 * g); for (let j = 0; j < 2 + (i === 1 ? 1 : 0); j++) if (!L.get(x0 - (j > 1 ? 1 : 0), y0 + j) || j) L.px(x0 - (j > 1 ? 1 : 0), y0 + j, pal.K); }
      else for (const s of [-1, 1]) for (let i = 0; i < 2; i++) { const x0 = Math.round(C[0] + s * (1 + i * 1.6)), y0 = Math.round(C[1] + 3 * g); L.px(x0, y0, pal.K); L.px(x0 + s * (i ? 1 : 0), y0 + 1, pal.K); }
    }
    if (k.tumbado) { tumbar(L, VW, VH, VOX, VOY); luz.d.fill(null); }
    return { color: MANO.normales(L), luz, puntos: { cabeza: [C[0] + 4, C[1] - 1] } };
  }
  function ojosVolador(L, luz, v, [hx, hy], o, k) {
    hx = Math.round(hx); hy = Math.round(hy);
    const pon = (x, y, c, brilla) => { L.px(x, y, c); if (brilla && !k.ojosC) luz.px(x, y, c); };
    if (o.ojos === 'calavera') { // cuencas oscuras con un punto rojo
      const ps = v === 'de' ? [[hx, hy - 1], [hx + 2, hy - 1]] : [[hx - 1, hy - 1], [hx + 1, hy - 1]];
      for (const [x, y] of ps) pon(x, y, k.ojosC ? o.pal.cuenca : o.pal.ojo, true);
      pon(v === 'de' ? hx + 2 : hx, hy + 1, o.pal.cuenca);
    } else { // ojos compuestos grandes
      const ps = v === 'de' ? [[hx, hy - 1], [hx + 1, hy - 1], [hx, hy], [hx + 1, hy - 2]] : [[hx - 2, hy - 1], [hx - 1, hy - 1], [hx - 2, hy], [hx + 1, hy - 1], [hx + 2, hy - 1], [hx + 2, hy]];
      for (const [x, y] of ps) pon(x, y, k.ojosC ? o.pal.cuenca : o.pal.ojo, true);
      pon(ps[0][0], ps[0][1], k.ojosC ? o.pal.cuenca : o.pal.brillo, true);
    }
  }
  const VOLAR = {
    q: (v, f) => ({ ala: f ? 2 : 0, bob: f ? 1 : 0 }),
    a: (v, f) => ({ ala: [0, 1, 2, 1][f], bob: [0, 0, 1, 1][f] }),
    p: (v, f) => ({ ala: f ? 2 : 0, curva: 1, inclina: -1, bob: -1 }),
    g: (v, f) => ({ ala: 3, inclina: 2, curva: 0.6, estela: true, bob: f }),
    r: (v, f) => (f === 0 ? { ala: 1, inclina: -2, ojosC: true, bob: -1 } : { ala: 2 }),
    m: (v, f) => [{ ala: 1, inclina: -2, ojosC: true }, { plegadas: true, ojosC: true, cae: 4 }, { plegadas: true, ojosC: true, tumbado: true }, { plegadas: true, ojosC: true, tumbado: true }][f],
    n: (v, f) => ({ ala: f % 2 ? 2 : 0, cae: [6, 4, 2, 0][f] }),
  };
  function volador(o) {
    return { manual: (anim, f, v) => cuadroVolador(v, VOLAR[anim](v, f), o), anims: { q: { n: 2, bucle: true }, a: { n: 4, bucle: true }, p: { n: 2 }, g: { n: 2 }, r: { n: 2 }, m: { n: 4 } },
      tiempos: { q: { fps: 20 }, a: { fps: 20 }, p: { fps: 20, bucle: true }, g: { fps: 20, bucle: true } },
      W: VW, H: VH, ox: VOX, oy: VOY, px: 1, yaw: 0, zancada: 10, fpsQuieta: 20, vistas: true };
  }
  const ALA = { ala: [0, '#6a8094', '#a4bccc', '#cfe2ee', '#f0faff'], alaAtras: [0, '#4a5a6a', '#7a90a2', '#9ab0c0', '#b8ccda'] };
  // la Aguijonera: cara de calavera, abdomen a rayas y aguijón
  FICHAS.avispa = volador({
    rayas: true, aguijon: true, ojos: 'calavera',
    pal: { K: '#140a04', aguijon: '#c8ccd4', ojo: '#ff3a2a', cuenca: '#1a0606', brillo: '#ffd0a0' },
    rampas: Object.assign({ cuerpo: [0, '#1e1208', '#3a2610', '#5a3c18', '#7a5624'], abdomen: [0, '#7a4208', '#c07a10', '#f0b028', '#ffe080'], raya: [0, '#140a04', '#2a1a0c', '#3a2610', '#4a3218'], cabeza: [0, '#7a6a4c', '#b0a07a', '#d8ccaa', '#f4ecd4'] }, ALA),
  });
  // la Moscarda: gorda, de la Podre, con los ojos rojos que brillan
  FICHAS.moscarda = volador({
    gordo: true, ojos: 'mosca',
    pal: { K: '#040506', aguijon: '#c8ccd4', ojo: '#e0281c', cuenca: '#2a0606', brillo: '#ffb0a0' },
    rampas: Object.assign({ cuerpo: [0, '#0c1216', '#18242c', '#26384a', '#3a5266'], abdomen: [0, '#10241c', '#1e4a36', '#2e7050', '#52a476'], raya: [0, '#0a1410', '#14281e', '#1e3a2c', '#2a4c3a'], cabeza: [0, '#0c1216', '#18242c', '#26384a', '#3a5266'] }, ALA, { ala: [0, '#4a5058', '#7a848e', '#a4aeb8', '#ccd6de'], alaAtras: [0, '#30363c', '#4e565e', '#6a747c', '#8a949c'] }),
  });
  // ---------------------------------------------------------------- la Lápida (escarabajo del musgo)
  // El caparazón es una lápida de piedra con musgo y una cruz grabada; seis patas con paso en trípode (como los
  // bichos de Core Keeper: anda 4 a 13 fps); antes de embestir rasca el suelo con la cabeza baja.
  const EW = 48, EH = 36, EOX = 24, EOY = 32;
  function cuadroEscarabajo(v, k, o) {
    const L = new Lienzo(EW, EH), luz = new Lienzo(EW, EH), R = o.rampas, pal = o.pal;
    const x = EOX, y = EOY + (k.bajo || 0), f = k.fase || 0, cab = k.cabeza || 0, formas = [], delante = [];
    const pata = (hx, hy, fx, fy, atras) => ({ c: [hx, hy, fx, fy, 1.3, 1], rampa: atras ? R.pataAtras : R.pata });
    if (v === 'de') {
      for (let i = 0; i < 3; i++) { const ph = Math.sin(f + i * 2.1 + Math.PI) * 2.5; formas.push(pata(x - 5 + i * 5, y - 6, x - 6 + i * 6 + ph, y - (ph > 0 ? 1 : 0), true)); }
      formas.push({ e: [x - 1, y - 11, 11, 7.5], rampa: R.concha }, { e: [x + 10 + cab * 0.5, y - 6 + cab, 3.8, 3.2], rampa: R.cabeza });
      for (let i = 0; i < 3; i++) { const ph = Math.sin(f + i * 2.1) * 2.5; delante.push(pata(x - 4 + i * 5, y - 5, x - 4 + i * 6 + ph + (k.rasca && i === 2 ? -3 : 0), y - (ph > 0 ? 1 : 0), false)); }
      masas(L, formas, EW, EH);
      // la cruz y el musgo de la lápida, los cuernos y el ojo
      const cx0 = x - 2, cy0 = y - 13;
      for (let d = -3; d <= 3; d++) { L.px(cx0, cy0 + d, R.concha[1]); if (Math.abs(d) <= 2) L.px(cx0 + d, cy0 - 1, R.concha[1]); }
      for (const [dx, dy] of [[-8, -15], [-6, -17], [-3, -18], [1, -18], [4, -17], [-5, -16], [2, -17]]) { L.px(x + dx, y + dy, o.musgo[2]); L.px(x + dx + 1, y + dy, o.musgo[3]); L.px(x + dx, y + dy + 1, o.musgo[1]); }
      const hx = x + 10 + cab * 0.5, hy = y - 6 + cab;
      CRI_PX.linea(L, [hx + 3, hy - 1], [hx + 6, hy - 3 + cab], pal.cuerno); CRI_PX.linea(L, [hx + 3, hy + 1], [hx + 6, hy + 2], pal.cuerno);
      L.px(Math.round(hx + 1), Math.round(hy - 1), pal.ojo); luz.px(Math.round(hx + 1), Math.round(hy - 1), pal.ojo);
      masas(L, delante, EW, EH);
    } else {
      const ab = v === 'ab';
      for (const s of [-1, 1]) for (let i = 0; i < 3; i++) { const ph = Math.sin(f + i * 2.1 + (s > 0 ? Math.PI : 0)) * 1.5; formas.push(pata(x + s * 7, y - 9 + i * 3, x + s * (12 + (i === 1 ? 1 : 0)), y - 7 + i * 3 + ph, false)); }
      if (!ab) formas.push({ e: [x, y - 3 - cab, 4, 3], rampa: R.cabeza });
      formas.push({ e: [x, y - 11, 10, 8], rampa: R.concha });
      if (ab) formas.push({ e: [x, y - 4 + cab, 4.2, 3.4], rampa: R.cabeza });
      masas(L, formas, EW, EH);
      if (ab) { const hy = y - 4 + cab; for (const dx of [-2, 2]) { L.px(x + dx - (dx < 0 ? 1 : 0), Math.round(hy - 1), pal.ojo); luz.px(x + dx - (dx < 0 ? 1 : 0), Math.round(hy - 1), pal.ojo); } CRI_PX.linea(L, [x - 2, hy + 2], [x - 4, hy + 5], pal.cuerno); CRI_PX.linea(L, [x + 1, hy + 2], [x + 3, hy + 5], pal.cuerno); }
      // la cruz grabada (de espaldas, grande: es la lápida) y el musgo arriba
      const cy0 = y - (ab ? 14 : 12), lc = ab ? 2 : 4;
      for (let d = -lc; d <= lc + 1; d++) L.px(x, cy0 + d, R.concha[1]);
      for (let d = -lc + 1; d <= lc - 1; d++) L.px(x + d, cy0 - (ab ? 0 : 1), R.concha[1]);
      for (const dx of [-7, -4, -1, 2, 5]) { L.px(x + dx, y - 18 + (Math.abs(dx) > 5 ? 1 : 0), o.musgo[2]); L.px(x + dx + 1, y - 18, o.musgo[3]); L.px(x + dx, y - 17, o.musgo[1]); }
    }
    if (k.polvo) for (const [dx, dy] of [[-12, -1], [-14, -3], [-10, -2]]) L.px(x + (v === 'de' ? dx : dx / 2), y + dy, '#8a7a64');
    contorno2(L, pal.K, EW, EH);
    if (k.tumbado) { // boca arriba: el dibujo del revés, apoyado en el suelo
      const d = L.d.slice(); L.d.fill(null); let fondo = 0, techo = EH;
      for (let yy = 0; yy < EH; yy++) for (let xx = 0; xx < EW; xx++) if (d[yy * EW + xx]) { fondo = Math.max(fondo, yy); techo = Math.min(techo, yy); }
      for (let yy = techo; yy <= fondo; yy++) for (let xx = 0; xx < EW; xx++) { const y2 = EOY - (yy - techo); if (y2 >= 0) L.d[y2 * EW + xx] = d[yy * EW + xx]; }
      luz.d.fill(null);
    }
    if (k.hundido) { const d = L.d.slice(); L.d.fill(null); for (let yy = 0; yy < EH; yy++) for (let xx = 0; xx < EW; xx++) { const y2 = yy + k.hundido; if (y2 <= EOY && y2 < EH) L.d[y2 * EW + xx] = d[yy * EW + xx]; } luz.d.fill(null); }
    return { color: MANO.normales(L), luz, puntos: { cabeza: [x + 10, y - 6] } };
  }
  const ESCARABAJO = {
    q: (v, f) => ({ bajo: f ? 1 : 0 }),
    a: (v, f) => ({ fase: (f / 4) * Math.PI * 2, bajo: f % 2 }),
    p: (v, f) => ({ cabeza: 2, rasca: f === 0, fase: f ? 1 : 0, polvo: true }),
    g: (v, f) => ({ cabeza: 2, fase: (f / 2) * Math.PI * 2, polvo: true, bajo: f }),
    r: (v, f) => (f === 0 ? { bajo: 1, cabeza: -1 } : {}),
    m: (v, f) => [{ cabeza: -1 }, { bajo: 2 }, { tumbado: true }, { tumbado: true }][f],
    n: (v, f) => ({ hundido: [16, 10, 5, 0][f] }),
  };
  FICHAS.escarabajo_musgo = {
    manual: (anim, f, v) => cuadroEscarabajo(v, ESCARABAJO[anim](v, f), {
      pal: { K: '#0a0806', ojo: '#e0c040', cuerno: '#2a2018' },
      musgo: ['#0e1a0a', '#2a4a1a', '#4a7a2a', '#7aaa44'],
      rampas: { concha: [0, '#3a3c40', '#5a5e64', '#7e848a', '#a2a8ae'], cabeza: [0, '#14100c', '#2a2218', '#40342a', '#5a4a3a'], pata: [0, '#120e0a', '#241c14', '#3a2e22', '#4e3e2e'], pataAtras: [0, '#0a0806', '#160f0a', '#221810', '#2e2016'] },
    }),
    anims: { q: { n: 2, bucle: true }, a: { n: 4, bucle: true }, p: { n: 2 }, g: { n: 2 }, r: { n: 2 }, m: { n: 4 }, n: { n: 4 } },
    tiempos: { q: { fps: 3 }, a: { fps: 13 }, p: { fps: 10, bucle: true }, g: { fps: 13, bucle: true } },
    W: EW, H: EH, ox: EOX, oy: EOY, px: 1, yaw: 0, zancada: 14, fpsQuieta: 3, vistas: true,
  };
  // ---------------------------------------------------------------- el sapo hueco de la Ciénaga
  // Flaco, de piel verde oscura y vientre pálido, ojos rojos arriba y bultos de Podre en la espalda que brillan.
  // Salta como las babas (agacharse, estirarse en el aire, aplastarse al caer).
  function cuadroSapo(v, k, o) {
    const L = new Lienzo(EW, EH), luz = new Lienzo(EW, EH), R = o.rampas, pal = o.pal;
    const sy = k.sy || 1, sx = k.sx || 1, x = EOX, y = EOY, aire = !!k.aire, cy = y - 6 * sy - (k.alto || 0);
    const formas = [], bultos = [];
    const lumps = (pts) => { for (const [dx, dy, r] of pts) bultos.push({ e: [x + dx * sx, cy + dy * sy, r, r * Math.min(1.1, sy)], rampa: R.podre, luz }); };
    if (v === 'de') {
      if (aire) formas.push({ c: [x - 6, cy + 2, x - 12, cy + 6, 2.2, 1.4], rampa: R.piel }, { c: [x - 12, cy + 6, x - 16, cy + 8, 1.3, 1], rampa: R.piel });
      else formas.push({ e: [x - 6, y - 3 * sy, 4.2 * sx, 3 * sy], rampa: R.piel }, { c: [x - 4, y - 0.5, x + 1, y - 0.5, 1.2], rampa: R.piel });
      formas.push({ e: [x, cy, 9 * sx, 5.6 * sy], rampa: R.piel }, { e: [x + 2, cy + 2.6 * sy, 6 * sx, 2.6 * sy], rampa: R.vientre, sinSurco: true });
      formas.push({ e: [x + 7.5 * sx, cy - 1.5 * sy, 4.6 * sx, 3.6 * sy], rampa: R.piel });
      formas.push({ c: [x + 5, cy + 2, x + 6.5 + (aire ? 2 : 0), aire ? cy + 5 : y - 0.5, 1.3, 1.1], rampa: R.piel });
      lumps([[-4, -5, 2], [0, -5.6, 2.4], [3.6, -4.6, 1.6], [-7, -2.6, 1.5]]);
      masas(L, formas, EW, EH); masas(L, bultos, EW, EH);
      const hx = Math.round(x + 7.5 * sx), hy = Math.round(cy - 1.5 * sy);
      CRI_PX.linea(L, [hx - 1, hy + 1], [hx + 4, hy + 1], pal.boca);
      if (!k.ojosC) for (const [dx, dy] of [[-1, -3], [2, -3]]) { L.px(hx + dx, hy + dy, pal.ojo); luz.px(hx + dx, hy + dy, pal.ojo); }
    } else {
      const ab = v === 'ab';
      for (const s of [-1, 1]) {
        if (aire) formas.push({ c: [x + s * 6, cy + 1, x + s * 10, cy + 6, 2, 1.3], rampa: R.piel });
        else formas.push({ e: [x + s * 7 * sx, y - 2.5 * sy, 3.6 * sx, 2.6 * sy], rampa: R.piel });
      }
      formas.push({ e: [x, cy, 8.5 * sx, 5.8 * sy], rampa: R.piel });
      if (ab) formas.push({ e: [x, cy + 2.4 * sy, 5.5 * sx, 2.6 * sy], rampa: R.vientre, sinSurco: true });
      if (ab) formas.push({ e: [x, cy - 3 * sy, 5.2 * sx, 3.6 * sy], rampa: R.piel });
      for (const s of [-1, 1]) formas.push({ c: [x + s * 4, cy + 3, x + s * 5, y - 0.5, 1.2, 1], rampa: R.piel });
      if (!ab) lumps([[-3, -4, 2.2], [2.5, -4.6, 2.4], [0, -1.4, 1.8], [-5.5, 0, 1.4], [5, -1, 1.3]]);
      else lumps([[-5, -6.5, 1.5], [4.6, -6.6, 1.4]]);
      masas(L, formas, EW, EH); masas(L, bultos, EW, EH);
      if (ab) {
        const hy = Math.round(cy - 3 * sy);
        CRI_PX.linea(L, [x - 4, hy + 2], [x + 3, hy + 2], pal.boca);
        if (!k.ojosC) for (const dx of [-3, 2]) { L.px(x + dx, hy - 2, pal.ojo); luz.px(x + dx, hy - 2, pal.ojo); L.px(x + dx, hy - 3, pal.ojoOsc); }
      }
    }
    contorno2(L, pal.K, EW, EH);
    if (k.tumbado) { const d = L.d.slice(); L.d.fill(null); let fondo = 0, techo = EH; for (let yy = 0; yy < EH; yy++) for (let xx = 0; xx < EW; xx++) if (d[yy * EW + xx]) { fondo = Math.max(fondo, yy); techo = Math.min(techo, yy); } for (let yy = techo; yy <= fondo; yy++) for (let xx = 0; xx < EW; xx++) { const y2 = EOY - (yy - techo); if (y2 >= 0) L.d[y2 * EW + xx] = d[yy * EW + xx]; } luz.d.fill(null); }
    if (k.hundido) { const d = L.d.slice(); L.d.fill(null); for (let yy = 0; yy < EH; yy++) for (let xx = 0; xx < EW; xx++) { const y2 = yy + k.hundido; if (y2 <= EOY && y2 < EH) L.d[y2 * EW + xx] = d[yy * EW + xx]; } luz.d.fill(null); }
    return { color: MANO.normales(L), luz, puntos: { cabeza: [x + 7, cy - 2] } };
  }
  const SAPO = {
    q: (v, f) => ({ sy: [1, 1.04][f], sx: [1, 0.98][f] }),
    a: (v, f) => [{ sy: 0.85, sx: 1.08 }, { sy: 1.12, sx: 0.94, alto: 2, aire: true }, { sy: 1.05, alto: 3, aire: true }, { sy: 0.9, sx: 1.06 }][f],
    p: (v, f) => ({ sy: [0.9, 0.82, 0.76][f], sx: [1.05, 1.1, 1.14][f] }),
    g: (v, f) => ({ aire: true, sy: [1.18, 1.1, 1.0, 0.95][f], sx: [0.9, 0.94, 1, 1.03][f] }),
    r: (v, f) => (f === 0 ? { sy: 0.85, sx: 1.1, ojosC: true } : {}),
    m: (v, f) => [{ sy: 0.8, sx: 1.12, ojosC: true }, { sy: 0.7, sx: 1.15, ojosC: true }, { tumbado: true, ojosC: true }, { tumbado: true, ojosC: true, sy: 0.8 }][f],
    n: (v, f) => ({ hundido: [14, 9, 4, 0][f] }),
  };
  FICHAS.sapo_hueco = {
    manual: (anim, f, v) => cuadroSapo(v, SAPO[anim](v, f), {
      pal: { K: '#060804', boca: '#0e0a08', ojo: '#ff3a2a', ojoOsc: '#3a0a06' },
      rampas: { piel: [0, '#1e2616', '#36442a', '#52623a', '#6e7e50'], vientre: [0, '#4a4230', '#6a5c40', '#9a8a62', '#c4b48a'], podre: [0, '#5a0a40', '#b0207a', '#ff3cb4', '#ffb0e4'] },
    }),
    anims: { q: { n: 2, bucle: true }, a: { n: 4, bucle: true }, p: { n: 3 }, g: { n: 4 }, r: { n: 2 }, m: { n: 4 }, n: { n: 4 } },
    tiempos: { q: { fps: 3 }, a: { fps: 8 }, p: { fps: 10, pausas: [0, 0, 3] } },
    W: EW, H: EH, ox: EOX, oy: EOY, px: 1, yaw: 0, zancada: 16, fpsQuieta: 3, vistas: true,
  };
  // ---------------------------------------------------------------- humanoides de la Ciénaga y la Ciudad (masas)
  const HW = 56, HH = 60, HOX = 28, HOY = 56;
  function acabarH(L, luz, k, pal) {
    contorno2(L, pal.K, HW, HH);
    if (k.disuelve) { for (let y = 0; y < HH; y++) for (let x = 0; x < HW; x++) if (L.d[y * HW + x] && ((x * 7 + y * 13) % 10) / 10 < k.disuelve) L.d[y * HW + x] = null; if (k.disuelve > 0.5) luz.d.fill(null); }
    if (k.tumbado) { tumbar(L, HW, HH, HOX, HOY); luz.d.fill(null); }
    if (k.hundido) { const d = L.d.slice(); L.d.fill(null); for (let yy = 0; yy < HH; yy++) for (let xx = 0; xx < HW; xx++) { const y2 = yy + k.hundido; if (y2 <= HOY && y2 < HH) L.d[y2 * HW + xx] = d[yy * HW + xx]; } luz.d.fill(null); }
    return MANO.normales(L);
  }
  // el Velado: una túnica que flota, la capucha sin cara (dos ojos fríos) y mangas que se alzan al conjurar
  function cuadroVelado(v, k, o) {
    const L = new Lienzo(HW, HH), luz = new Lienzo(HW, HH), R = o.rampas, pal = o.pal;
    const x = HOX, y = HOY - 6 + (k.bob || 0), alza = k.alza || 0, ab = v === 'ab', ar = v === 'ar';
    const formas = [];
    // la túnica: se ensancha hacia abajo y acaba en flecos que ondulan
    const tunica = { c: [x, y - 24, x + (v === 'de' ? -2 : 0), y - 6, 4.5, 8.5], rampa: R.tunica };
    const manga = (s) => ({ c: [x + s * 5, y - 22, x + s * (7 + alza * 2) + (v === 'de' && s > 0 ? alza * 4 : 0), y - 14 - alza * 12, 2.4, 2.8], rampa: s < 0 && v === 'de' ? R.tunicaAtras : R.tunica });
    if (v === 'de') formas.push(manga(-1));
    formas.push(tunica);
    formas.push({ e: [x + (v === 'de' ? 1 : 0), y - 30, 5.2, 5.6], rampa: R.tunica });
    if (v !== 'de') formas.push(manga(-1), manga(1)); else formas.push(manga(1));
    masas(L, formas, HW, HH);
    // los flecos de abajo
    for (let i = -8; i <= 8; i++) { const yy = y - 1 + Math.round(Math.sin(i * 1.3 + (k.bob || 0) * 2) * 1.2); if (L.get(x + i, y - 3)) for (let j = y - 2; j <= yy; j++) L.px(x + i, j, (i + j) % 2 ? R.tunica[2] : R.tunica[1]); }
    // la cara: un hueco negro con dos ojos
    if (!ar) {
      const fx = x + (v === 'de' ? 3 : 0), fy = y - 30;
      for (let yy = fy - 2; yy <= fy + 3; yy++) for (let xx = fx - (v === 'de' ? 1 : 3); xx <= fx + (v === 'de' ? 2 : 3); xx++) if (L.get(xx, yy)) L.px(xx, yy, pal.hueco);
      const ojos = v === 'de' ? [[fx + 1, fy]] : [[fx - 2, fy], [fx + 1, fy]];
      if (!k.ojosC) for (const [ex, ey] of ojos) { L.px(ex, ey, pal.ojo); L.px(ex + 1, ey, pal.ojo2); luz.px(ex, ey, pal.ojo); luz.px(ex + 1, ey, pal.ojo2); }
    }
    // el orbe frío que junta al conjurar
    if (alza > 0.3) { const ox = x + (v === 'de' ? 8 + alza * 4 : 0), oy = y - 38 - alza * 4; for (let yy = -2; yy <= 2; yy++) for (let xx = -2; xx <= 2; xx++) if (xx * xx + yy * yy <= 4 + alza) { const c = xx * xx + yy * yy <= 1 ? pal.orbe[2] : pal.orbe[1]; L.px(ox + xx, oy + yy, c); luz.px(ox + xx, oy + yy, c); } }
    return { color: acabarH(L, luz, k, pal), luz, puntos: { cabeza: [x, y - 30], orbe: [x + 8, y - 40] } };
  }
  const VELADO = {
    q: (v, f) => ({ bob: [0, -1, -1, 0][f] }), a: (v, f) => ({ bob: [0, -1, -1, 0][f] }),
    p: (v, f) => ({ alza: [0.4, 0.8, 1][f], bob: -1 }), g: (v, f) => ({ alza: [1, 0.6, 0.2][f] }),
    r: (v, f) => (f === 0 ? { bob: 1, ojosC: true } : {}),
    m: (v, f) => ({ disuelve: [0.2, 0.45, 0.7, 0.9][f], ojosC: f > 1 }), n: (v, f) => ({ disuelve: [0.8, 0.55, 0.3, 0][f] }),
  };
  FICHAS.velado = {
    manual: (anim, f, v) => cuadroVelado(v, VELADO[anim](v, f), {
      pal: { K: '#0a0c18', hueco: '#04050a', ojo: '#bfe0ff', ojo2: '#6aa0e0', orbe: ['#1a3a8a', '#6a9aff', '#e8f4ff'] },
      rampas: { tunica: [0, '#2a3048', '#424a68', '#5e6890', '#8a94b8'], tunicaAtras: [0, '#1c2034', '#2e3450', '#424a6c', '#5a6488'] },
    }),
    anims: { q: { n: 4, bucle: true }, a: { n: 4, bucle: true }, p: { n: 3 }, g: { n: 3 }, r: { n: 2 }, m: { n: 4 }, n: { n: 4 } },
    tiempos: { q: { fps: 4 }, a: { fps: 5 }, p: { fps: 6 }, g: { fps: 10 } },
    W: HW, H: HH, ox: HOX, oy: HOY, px: 1, yaw: 0, zancada: 30, fpsQuieta: 4, vistas: true,
  };
  // el Farolero: alto y flaco, encorvado, con un farol de cebo colgando de una vara (como un rape de la Ciénaga)
  function cuadroFarolero(v, k, o) {
    const L = new Lienzo(HW, HH), luz = new Lienzo(HW, HH), R = o.rampas, pal = o.pal;
    const x = HOX, y = HOY, f = k.fase || 0, alza = k.alza || 0, b = k.bob || 0, det = [], del = [];
    const sw = Math.sin(f), cw = Math.cos(f);
    if (v === 'de') {
      det.push({ c: [x - 1, y - 20, x - 3 - sw * 4, y - 1, 2, 1.6], rampa: R.atras });
      det.push({ c: [x + 3, y - 34 + b, x - 6 - alza * 3, y - 24 + b - alza * 6, 2, 1.6], rampa: R.atras });
      det.push({ c: [x, y - 22 + b, x + 4, y - 34 + b, 4, 4.6], rampa: R.cuerpo });
      det.push({ c: [x + 1, y - 20, x + 1 + sw * 4, y - 1, 2.2, 1.8], rampa: R.cuerpo });
      det.push({ e: [x + 7, y - 36 + b, 4, 3.4], rampa: R.cabeza });
      del.push({ c: [x + 4, y - 32 + b, x + 10 + alza * 4, y - 22 + b - alza * 14, 2.2, 1.8], rampa: R.cuerpo });
    } else {
      const ab = v === 'ab';
      for (const s of [-1, 1]) det.push({ c: [x + s * 2, y - 20, x + s * 3 + (s > 0 ? sw : -sw) * 1.5, y - 1, 2.2, 1.8], rampa: R.cuerpo });
      det.push({ c: [x, y - 22 + b, x, y - 34 + b, 4.6, 5], rampa: R.cuerpo });
      det.push({ e: [x, y - 38 + b, 4, 3.6], rampa: R.cabeza });
      for (const s of [-1, 1]) del.push({ c: [x + s * 5, y - 33 + b, x + s * 7 + (s > 0 ? alza * 3 : 0), y - 18 + b - (s > 0 ? alza * 16 : 0), 2.2, 1.8], rampa: R.cuerpo });
      void ab;
    }
    masas(L, det, HW, HH);
    // los ojos pequeños y la boca cosida
    const hx = v === 'de' ? x + 8 : x, hy = y - (v === 'de' ? 36 : 38) + b;
    if (v !== 'ar') { const ojos = v === 'de' ? [[hx + 1, hy - 1]] : [[hx - 2, hy - 1], [hx + 1, hy - 1]]; if (!k.ojosC) for (const [ex, ey] of ojos) { L.px(ex, ey, pal.ojo); luz.px(ex, ey, pal.ojo); } for (let i = -2; i <= 2; i++) L.px(hx + i + (v === 'de' ? 1 : 0), hy + 2, i % 2 ? pal.boca : R.cabeza[1]); }
    masas(L, del, HW, HH);
    // la vara del farol y el farol que brilla
    const mano = v === 'de' ? [x + 10 + alza * 4, y - 22 + b - alza * 14] : [x + 7 + alza * 3, y - 18 + b - alza * 16];
    const punta = [mano[0] + (v === 'de' ? 6 : 3), mano[1] - 10];
    CRI_PX.linea(L, mano, punta, pal.vara);
    const lx = Math.round(punta[0] + (v === 'de' ? 2 : 1)), ly = Math.round(punta[1] + 3 + Math.round(Math.sin(f * 2)));
    L.px(lx, ly - 2, pal.vara);
    for (let yy = -1; yy <= 2; yy++) for (let xx = -1; xx <= 1; xx++) { const c = Math.abs(xx) + Math.abs(yy - 0.5) < 1.2 ? pal.luz[2] : pal.luz[1]; L.px(lx + xx, ly + yy, c); if (!k.ojosC) luz.px(lx + xx, ly + yy, c); }
    return { color: acabarH(L, luz, k, pal), luz, puntos: { cabeza: [hx, hy], farol: [lx, ly] } };
  }
  const FAROLERO = {
    q: (v, f) => ({ bob: [0, 0, 1, 1][f] }), a: (v, f) => ({ fase: (f / 8) * Math.PI * 2, bob: f % 4 === 1 || f % 4 === 2 ? 1 : 0 }),
    p: (v, f) => ({ alza: [0.4, 0.8, 1][f] }), g: (v, f) => ({ alza: [0.6, 0.1, 0][f], bob: f ? 1 : 0 }),
    r: (v, f) => (f === 0 ? { bob: 2, ojosC: true } : {}),
    m: (v, f) => [{ bob: 2, ojosC: true }, { bob: 5, ojosC: true }, { tumbado: true, ojosC: true }, { tumbado: true, ojosC: true }][f],
    n: (v, f) => ({ hundido: [30, 20, 10, 0][f] }),
  };
  FICHAS.farolero = {
    manual: (anim, f, v) => cuadroFarolero(v, FAROLERO[anim](v, f), {
      pal: { K: '#060806', ojo: '#e0ff90', boca: '#0a0806', vara: '#3a2c1c', luz: ['#2a8a2a', '#6ae04a', '#e4ffb0'] },
      rampas: { cuerpo: [0, '#1a1e16', '#2e3626', '#465238', '#5e6c4a'], atras: [0, '#101410', '#1e241a', '#2c3426', '#3a4432'], cabeza: [0, '#3a3a2c', '#6a6852', '#948e72', '#bab296'] },
    }),
    anims: { q: { n: 4, bucle: true }, a: { n: 8, bucle: true }, p: { n: 3 }, g: { n: 3 }, r: { n: 2 }, m: { n: 4 }, n: { n: 4 } },
    tiempos: { q: { fps: 3 }, a: { fps: 9 }, p: { fps: 6 }, g: { fps: 10 } },
    W: HW, H: HH, ox: HOX, oy: HOY, px: 1, yaw: 0, zancada: 30, fpsQuieta: 3, vistas: true,
  };
  // ---------------------------------------------------------------- el Necrófago (a cuatro patas, cráneo por cabeza)
  function cuadroNecrofago(v, k, o) {
    const L = new Lienzo(HW, HH), luz = new Lienzo(HW, HH), R = o.rampas, pal = o.pal;
    const x = HOX, y = HOY, f = k.fase || 0, alza = k.alza || 0, sw = Math.sin(f), det = [], del = [];
    const cuerpoY = y - 10 - alza * 6;
    if (v === 'de') {
      det.push({ c: [x - 7, cuerpoY + 2, x - 9 - sw * 3, y - 1, 2.2, 1.6], rampa: R.atras }, { c: [x + 6, cuerpoY + 1, x + 9 + sw * 3 + alza * 4, y - 1 - alza * 6, 2.2, 1.6], rampa: R.atras });
      det.push({ c: [x - 9, cuerpoY + 1, x + 7, cuerpoY - 2 - alza * 3, 5, 4.2], rampa: R.piel });
      det.push({ e: [x + 12 + alza * 2, cuerpoY - 3 - alza * 4, 4.4, 3.8], rampa: R.hueso });
      del.push({ c: [x - 6, cuerpoY + 3, x - 6 + sw * 3, y - 1, 2.2, 1.6], rampa: R.piel }, { c: [x + 7, cuerpoY + 1, x + 11 - sw * 3 + alza * 6, y - 1 - alza * 8, 2.4, 1.6], rampa: R.piel });
    } else {
      const ab = v === 'ab';
      for (const s of [-1, 1]) det.push({ c: [x + s * 6, cuerpoY + 1, x + s * 8, y - 1 + (s > 0 ? sw : -sw), 2.2, 1.6], rampa: R.atras });
      det.push({ e: [x, cuerpoY - 1, 8, 6], rampa: R.piel });
      if (ab) det.push({ e: [x, cuerpoY + 2 - alza * 3, 4.6, 4], rampa: R.hueso });
      for (const s of [-1, 1]) del.push({ c: [x + s * 5, cuerpoY + 2, x + s * (6 + alza * 3), y - 1 - alza * 8, 2.4, 1.6], rampa: R.piel });
    }
    masas(L, det, HW, HH);
    // las vértebras de la espalda y el cráneo con sus cuencas
    if (v === 'de') { for (let i = 0; i < 5; i++) { const bx = x - 7 + i * 3, by = Math.round(cuerpoY - 5 - alza * (i / 5) * 3); L.px(bx, by, R.hueso[3]); L.px(bx + 1, by, R.hueso[2]); } const hx = Math.round(x + 12 + alza * 2), hy = Math.round(cuerpoY - 3 - alza * 4); L.px(hx + 1, hy - 1, pal.cuenca); L.px(hx + 2, hy - 1, pal.cuenca); if (!k.ojosC) { L.px(hx + 2, hy - 1, pal.ojo); luz.px(hx + 2, hy - 1, pal.ojo); } for (let i = 0; i < 4; i++) L.px(hx + 1 + i, hy + 2 + (i % 2) + (k.boca || 0), R.hueso[i % 2 ? 2 : 4]); }
    else if (v === 'ab') { const hy = Math.round(cuerpoY + 2 - alza * 3); for (const dx of [-2, 1]) { L.px(x + dx, hy - 1, pal.cuenca); L.px(x + dx + 1, hy - 1, pal.cuenca); if (!k.ojosC) { L.px(x + dx + (dx < 0 ? 1 : 0), hy - 1, pal.ojo); luz.px(x + dx + (dx < 0 ? 1 : 0), hy - 1, pal.ojo); } } for (let i = -2; i <= 2; i++) L.px(x + i, hy + 2 + (k.boca || 0), R.hueso[i % 2 ? 2 : 4]); }
    else for (let i = 0; i < 5; i++) { L.px(x, Math.round(cuerpoY - 5 + i * 2), R.hueso[3]); L.px(x - 1, Math.round(cuerpoY - 5 + i * 2), R.hueso[2]); }
    masas(L, del, HW, HH);
    return { color: acabarH(L, luz, k, pal), luz, puntos: { cabeza: [x + 12, cuerpoY - 3] } };
  }
  const NECROFAGO = {
    q: (v, f) => ({ alza: [0, 0.05, 0.1, 0.05][f] }), a: (v, f) => ({ fase: (f / 8) * Math.PI * 2 }),
    p: (v, f) => ({ alza: [0.3, 0.6, 0.8][f], boca: f > 0 ? 1 : 0 }), g: (v, f) => ({ alza: [0.9, 0.5, 0.1][f], boca: f < 2 ? 2 : 0, fase: f }),
    r: (v, f) => (f === 0 ? { alza: -0.2, ojosC: true } : {}),
    m: (v, f) => [{ ojosC: true, alza: -0.3 }, { ojosC: true, alza: -0.6 }, { tumbado: true, ojosC: true }, { tumbado: true, ojosC: true }][f],
    n: (v, f) => ({ hundido: [24, 16, 8, 0][f] }),
  };
  FICHAS.necrofago = {
    manual: (anim, f, v) => cuadroNecrofago(v, NECROFAGO[anim](v, f), {
      pal: { K: '#080606', cuenca: '#0a0606', ojo: '#ff5a3a' },
      rampas: { piel: [0, '#2a1e22', '#4a3638', '#6e5450', '#94786c'], atras: [0, '#1a1214', '#2e2224', '#423234', '#584444'], hueso: [0, '#5a4c36', '#8e7c5c', '#c8b890', '#efe4c4'] },
    }),
    anims: { q: { n: 4, bucle: true }, a: { n: 8, bucle: true }, p: { n: 3 }, g: { n: 3 }, r: { n: 2 }, m: { n: 4 }, n: { n: 4 } },
    tiempos: { q: { fps: 3 }, a: { fps: 12 }, p: { fps: 8 }, g: { fps: 12 } },
    W: HW, H: HH, ox: HOX, oy: HOY, px: 1, yaw: 0, zancada: 26, fpsQuieta: 3, vistas: true,
  };
  // ---------------------------------------------------------------- el Guardián de piedra (estatua que despierta)
  function cuadroGuardian(v, k, o) {
    const L = new Lienzo(HW, HH), luz = new Lienzo(HW, HH), R = o.rampas, pal = o.pal;
    const x = HOX, y = HOY, f = k.fase || 0, alza = k.alza || 0, ag = k.agacha || 0, sw = Math.sin(f), det = [], del = [];
    const t = y - 20 + ag * 6, brazoY = t - 6 - alza * 14;
    for (const s of [-1, 1]) det.push({ c: [x + s * 4, t + 6, x + s * 5 + (v === 'de' ? s * sw * 2 : 0), y - 2, 3.2, 3], rampa: R.piedra }, { e: [x + s * 5, y - 1.5, 3.6, 2], rampa: R.piedra });
    det.push({ e: [x, t, 10, 9], rampa: R.piedra }, { e: [x, t - 9 + ag * 2, 5, 4.4], rampa: R.piedra });
    for (const s of [-1, 1]) del.push({ c: [x + s * 10, t - 4, x + s * (12 - alza * 4), brazoY + 12 + (k.golpe || 0) * 6, 3.4, 3.8], rampa: R.piedra }, { e: [x + s * (12 - alza * 4), brazoY + 14 + (k.golpe || 0) * 6, 4, 3.6], rampa: R.piedra });
    masas(L, det, HW, HH);
    // las runas y los ojos (apagados mientras es estatua)
    const viva = !k.dormida, hy = Math.round(t - 9 + ag * 2);
    if (v !== 'ar') { for (const dx of v === 'de' ? [2] : [-2, 1]) { const c = viva && !k.ojosC ? pal.runa[2] : R.piedra[1]; L.px(x + dx, hy, c); L.px(x + dx + 1, hy, c); if (viva && !k.ojosC) { luz.px(x + dx, hy, c); luz.px(x + dx + 1, hy, c); } } }
    for (const [dx, dy] of [[-3, 0], [0, -2], [3, 0], [0, 3], [-1, 1], [1, 1]]) { const c = viva ? pal.runa[1] : R.piedra[1]; L.px(x + dx, Math.round(t + dy), c); if (viva) luz.px(x + dx, Math.round(t + dy), c); }
    for (const [dx, dy] of [[-7, -5], [6, -6], [-4, 5], [7, 3]]) { L.px(x + dx, Math.round(t + dy), o.musgo[2]); L.px(x + dx + 1, Math.round(t + dy), o.musgo[1]); }
    masas(L, del, HW, HH);
    if (k.golpe > 0.5) for (const [dx, dy] of [[-15, 0], [15, 0], [-12, -2], [13, -1]]) L.px(x + dx, y + dy, '#8a8e9a');
    return { color: acabarH(L, luz, k, pal), luz, puntos: { cabeza: [x, hy] } };
  }
  const GUARDIAN = {
    q: (v, f) => ({ agacha: [0, 0, 0.1, 0.1][f] }), a: (v, f) => ({ fase: (f / 6) * Math.PI * 2 }),
    e: (v, f) => ({ dormida: true, agacha: 1 }), d: (v, f) => ({ agacha: [0.8, 0.5, 0.2][f], dormida: f === 0 }),
    p: (v, f) => ({ alza: [0.4, 0.8, 1][f] }), g: (v, f) => ({ alza: [0.5, 0, 0][f], golpe: [0.3, 1, 0.6][f], agacha: f ? 0.4 : 0.2 }),
    r: (v, f) => (f === 0 ? { agacha: 0.3, ojosC: true } : {}),
    m: (v, f) => ({ disuelve: [0.15, 0.4, 0.65, 0.9][f], dormida: f > 0, agacha: 0.6 }), n: (v, f) => ({ hundido: [30, 20, 10, 0][f] }),
  };
  FICHAS.guardian_piedra = {
    manual: (anim, f, v) => cuadroGuardian(v, GUARDIAN[anim](v, f), {
      pal: { K: '#0a0c14', runa: ['#1a3a6a', '#6a9aff', '#d8ecff'] }, musgo: ['#0e1a0a', '#2a4a1a', '#4a7a2a', '#7aaa44'],
      rampas: { piedra: [0, '#2a3040', '#464e64', '#646e86', '#8a94aa'] },
    }),
    anims: { q: { n: 4, bucle: true }, a: { n: 6, bucle: true }, e: { n: 1, bucle: true }, d: { n: 3 }, p: { n: 3 }, g: { n: 3 }, r: { n: 2 }, m: { n: 4 }, n: { n: 4 } },
    tiempos: { q: { fps: 2 }, a: { fps: 6 }, p: { fps: 5 }, g: { fps: 10 } },
    W: HW, H: HH, ox: HOX, oy: HOY, px: 1, yaw: 0, zancada: 30, fpsQuieta: 2, vistas: true,
  };
  // ---------------------------------------------------------------- las que no se mueven: la Fauce y el Brazo de la Podre
  // Un tallo (o un tentáculo) de anillos que sale del suelo y se curva; arriba, la boca. Igual en las tres vistas.
  function tallo(base, n, largo, curva, alzado) { // puntos a lo largo, de abajo arriba
    const pts = [];
    let x = base[0], y = base[1], a = -Math.PI / 2;
    for (let i = 0; i < n; i++) { pts.push([x, y]); a += curva / n + (alzado || 0) / n; x += Math.cos(a) * largo / n; y += Math.sin(a) * largo / n; }
    pts.push([x, y]);
    return pts;
  }
  function cuadroFauce(v, k, o) {
    const L = new Lienzo(HW, HH), luz = new Lienzo(HW, HH), R = o.rampas, pal = o.pal, x = HOX, y = HOY;
    const formas = [];
    // las hojas de la base
    for (const [dx, rx] of [[-7, 6], [7, 6], [0, 5]]) formas.push({ e: [x + dx, y - 2, rx, 2.6], rampa: R.hoja });
    const esc = k.escondida;
    if (!esc) {
      const pts = tallo([x, y - 3], 6, 18 * (k.sale ?? 1), (k.mece || 0) * 0.8 + (k.lanza || 0) * 1.4, 0);
      for (let i = 0; i < pts.length - 1; i++) formas.push({ c: [pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1], 2.2 - i * 0.12], rampa: R.tallo });
      const [hx, hy] = pts[pts.length - 1], ab = (k.abre || 0) * 4;
      formas.push({ e: [hx + 1, hy - 3 - ab * 0.6, 6, 3.4], rampa: R.boca }, { e: [hx + 1, hy + 2 + ab * 0.4, 6, 3], rampa: R.boca });
      masas(L, formas, HW, HH);
      // el hueco de la boca y los dientes
      for (let xx = -4; xx <= 5; xx++) { for (let yy = Math.round(hy - 1 - ab * 0.4); yy <= Math.round(hy + ab * 0.3); yy++) L.px(Math.round(hx + 1 + xx), yy, pal.boca); if (xx % 2 === 0) { L.px(Math.round(hx + 1 + xx), Math.round(hy - 1 - ab * 0.4), pal.diente); L.px(Math.round(hx + 1 + xx), Math.round(hy + ab * 0.3), pal.diente); } }
      for (const [dx, dy] of [[-3, -5], [2, -6], [5, -4]]) { const c = pal.punto; L.px(Math.round(hx + dx), Math.round(hy + dy - ab * 0.6), c); luz.px(Math.round(hx + dx), Math.round(hy + dy - ab * 0.6), c); }
    } else { formas.push({ e: [x, y - 4, 4, 3], rampa: R.boca }); masas(L, formas, HW, HH); }
    return { color: acabarH(L, luz, k, pal), luz, puntos: { cabeza: [x, y - 20] } };
  }
  const FAUCE = {
    e: (v, f) => ({ escondida: true }), q: (v, f) => ({ mece: [0, 0.3, 0, -0.3][f] }),
    p: (v, f) => ({ abre: [0.3, 0.6, 0.9, 1][f], mece: -0.4 - f * 0.1 }), g: (v, f) => ({ abre: [1, 0.2, 0][f], lanza: [0.5, 0.9, 0.4][f] }),
    r: (v, f) => (f === 0 ? { mece: -0.6 } : {}), m: (v, f) => ({ mece: 0.5 + f * 0.5, sale: 1 - f * 0.15, disuelve: f > 1 ? (f - 1) * 0.35 : 0 }),
    n: (v, f) => ({ sale: [0.25, 0.5, 0.75, 1][f] }),
  };
  FICHAS.planta_trampa = {
    manual: (anim, f, v) => cuadroFauce(v, FAUCE[anim](v, f), {
      pal: { K: '#060a04', boca: '#2a0610', diente: '#efe4c4', punto: '#ffd040' },
      rampas: { hoja: [0, '#1e3a1a', '#346028', '#4e8434', '#72aa4a'], tallo: [0, '#0e1a0c', '#1e3a1a', '#346028', '#4e8434'], boca: [0, '#4a0a1a', '#8a1a2a', '#c43a3a', '#e8705a'] },
    }),
    anims: { e: { n: 1, bucle: true }, q: { n: 4, bucle: true }, p: { n: 4 }, g: { n: 3 }, r: { n: 2 }, m: { n: 4 }, n: { n: 4 } },
    tiempos: { q: { fps: 3 }, p: { fps: 7 }, g: { fps: 12 } },
    W: HW, H: HH, ox: HOX, oy: HOY, px: 1, yaw: 0, fpsQuieta: 3, vistas: true,
  };
  function cuadroTentaculo(v, k, o) {
    const L = new Lienzo(HW, HH), luz = new Lienzo(HW, HH), R = o.rampas, pal = o.pal, x = HOX, y = HOY;
    const formas = [{ e: [x, y - 2, 7, 2.6], rampa: R.suelo }];
    if (!k.escondida) {
      const pts = tallo([x, y - 2], 8, 30 * (k.sale ?? 1), (k.mece || 0) + (k.atras || 0) * -1.6 + (k.golpe || 0) * 2.6, 0);
      for (let i = 0; i < pts.length - 1; i++) formas.push({ c: [pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1], 3.6 - i * 0.36], rampa: R.piel });
      masas(L, formas, HW, HH);
      for (let i = 1; i < pts.length - 1; i += 2) { const [px, py] = pts[i], c = pal.ventosa; L.px(Math.round(px + 1), Math.round(py), c); luz.px(Math.round(px + 1), Math.round(py), c); }
    } else { formas.push({ e: [x, y - 4, 4.6, 3], rampa: R.piel }); masas(L, formas, HW, HH); L.px(x, y - 5, pal.ventosa); luz.px(x, y - 5, pal.ventosa); }
    return { color: acabarH(L, luz, k, pal), luz, puntos: { cabeza: [x, y - 28] } };
  }
  const TENTACULO = {
    e: (v, f) => ({ escondida: true }), q: (v, f) => ({ mece: [0, 0.25, 0.4, 0.25, 0, -0.25][f] }),
    p: (v, f) => ({ atras: [0.3, 0.6, 0.85, 1][f] }), g: (v, f) => ({ golpe: [0.4, 0.9, 1][f] }),
    r: (v, f) => (f === 0 ? { mece: -0.5 } : {}), m: (v, f) => ({ golpe: 0.6 + f * 0.2, sale: 1 - f * 0.18, disuelve: f > 1 ? (f - 1) * 0.35 : 0 }),
    n: (v, f) => ({ sale: [0.2, 0.45, 0.7, 1][f] }),
  };
  FICHAS.tentaculo_moho = {
    manual: (anim, f, v) => cuadroTentaculo(v, TENTACULO[anim](v, f), {
      pal: { K: '#040306', ventosa: '#ff3cb4' },
      rampas: { piel: [0, '#14101c', '#241c30', '#382c48', '#544264'], suelo: [0, '#0a080c', '#1a1420', '#2a2032', '#3a2c44'] },
    }),
    anims: { e: { n: 1, bucle: true }, q: { n: 6, bucle: true }, p: { n: 4 }, g: { n: 3 }, r: { n: 2 }, m: { n: 4 }, n: { n: 4 } },
    tiempos: { q: { fps: 4 }, p: { fps: 8 }, g: { fps: 12 } },
    W: HW, H: HH, ox: HOX, oy: HOY, px: 1, yaw: 0, fpsQuieta: 4, vistas: true,
  };
  function registrar() { for (const [t, d] of Object.entries(FICHAS)) CRI.DEF[t] = d; }
  registrar();
  return { gusano, cuadro, FICHAS, W, H, OX, OY };
})();
