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
  function registrar() { for (const [t, d] of Object.entries(FICHAS)) CRI.DEF[t] = d; }
  registrar();
  return { gusano, cuadro, FICHAS, W, H, OX, OY };
})();
