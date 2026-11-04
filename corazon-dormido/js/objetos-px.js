'use strict';
// Objetos del mundo dibujados a mano (0.8, ARTE.md): el Corazón y lo que más se ve, con las primitivas de las
// criaturas (criaturas-esq.js, CRI.lamina): piezas con volumen, 3–4 tonos por material de la paleta de Ásor, luz
// arriba a la izquierda, contorno del tono más oscuro del material (nunca negro) y líneas donde una pieza tapa a otra.
// Sustituyen a los modelos horneados del mismo nombre (se registran al final de CONSTRUIR_MODELOS).

const OBJ_PX = (() => {
  // Rampas: [línea, sombra, medio, luz(, brillo)]
  const R = {
    carne: ['#2e0612', '#5a0e22', '#8a1a30', '#b82c42', '#e8606a'],
    carneD: ['#1a0c18', '#321628', '#4c2238', '#68324a', '#86485e'],
    arteria: ['#3e0e1e', '#74202e', '#a43844', '#d05a60', '#f49088'],
    arteriaD: ['#20101a', '#3a1c2c', '#54303e', '#704452', '#8c5e68'],
    cristal: ['#0c3442', '#16708a', '#26aac0', '#64e0f0', '#e4ffff'],
    cristalD: ['#141224', '#24223c', '#363456', '#4c4a72', '#6a6890'],
    vena: ['#5a0a2a', '#b81c46', '#ff4a72', '#ffa4bc'],
    venaD: ['#1a0c1e', '#2c1430', '#3e1e44', '#522a58'],
    podre: ['#0a060e', '#1c0e20', '#2e1834', '#42244a'],
    hoja: ['#0c2818', '#1a4824', '#2a6e2e', '#46963a', '#78c24c'],
    hoja2: ['#0a2420', '#16422c', '#246438', '#3c8a44', '#66b25a'],
    hojaLuna: ['#0a2240', '#164676', '#2670ac', '#48a2d8', '#98d6f2'],
    corteza: ['#1c0e08', '#3a2214', '#5a3820', '#7a5232', '#986c44'],
    fruta: ['#4a0a0a', '#9a1c18', '#d8382a', '#ff7a5a'],
    luna: ['#2a6a98', '#6ac0e8', '#c8f0ff', '#ffffff'],
    poro: ['#5a0a40', '#c0208a', '#ff4cbc', '#ffb0e4'],
  };
  for (const k of Object.keys(R)) R[k].nombre = k;
  // entre racimos de hojas la línea es la sombra, no el contorno
  for (const k of ['hoja', 'hoja2', 'hojaLuna']) R[k].linea = R[k][1];
  R.cristal.sinLinea = true; R.vena.sinLinea = true; R.venaD.sinLinea = true;

  // Fragmento de cristal: prisma de 4 caras que sale de b hacia la punta p, con ancho w. Las caras: la de la
  // izquierda clara, la del centro media y la de la derecha oscura; un brillo de 1 px en el filo.
  function cristal(K, b, p, w, rampa, luz) {
    const dx = p[0] - b[0], dy = p[1] - b[1], m = Math.hypot(dx, dy) || 1, ux = dx / m, uy = dy / m, nx = -uy, ny = ux;
    const L = (k) => [b[0] + nx * w * k, b[1] + ny * w * k];
    const cuello = (k) => [b[0] + dx * 0.72 + nx * w * k, b[1] + dy * 0.72 + ny * w * k];
    const izq = [L(-0.5), cuello(-0.5), p, cuello(0.05), L(0.05)], der = [L(0.05), cuello(0.05), p, cuello(0.5), L(0.5)];
    const n = rampa.length - 1;
    K.poli(izq, rampa, { color: rampa[n - 1], luz, normal: () => [-0.6, -0.5] });
    K.poli(der, rampa, { color: rampa[Math.max(1, n - 3)], luz, normal: () => [0.6, 0.2] });
    // filo de la luz
    for (let t = 0.15; t < 0.95; t += 0.12) K.px(b[0] + dx * t * 0.95 + nx * w * -0.15, b[1] + dy * t * 0.95 + ny * w * -0.15, rampa[n], rampa, luz);
  }
  // Curva suave por unos puntos (Catmull-Rom), muestreada cada medio píxel.
  function curva(pts) {
    const out = [];
    for (let i = 0; i < pts.length - 1; i++) {
      const p0 = pts[Math.max(0, i - 1)], p1 = pts[i], p2 = pts[i + 1], p3 = pts[Math.min(pts.length - 1, i + 2)];
      const n = Math.ceil(Math.hypot(p2[0] - p1[0], p2[1] - p1[1]) * 2);
      for (let k = 0; k < n; k++) {
        const t = k / n, t2 = t * t, t3 = t2 * t;
        out.push([0, 1].map((e) => 0.5 * (2 * p1[e] + (-p0[e] + p2[e]) * t + (2 * p0[e] - 5 * p1[e] + 4 * p2[e] - p3[e]) * t2 + (-p0[e] + 3 * p1[e] - 3 * p2[e] + p3[e]) * t3)));
      }
    }
    out.push(pts[pts.length - 1]);
    return out;
  }
  // Tubo (arteria, rama, raíz) por una curva, con el radio de r0 a r1: cápsulas cortas que se solapan.
  function tubo(K, pts, r0, r1, rampa, o) { K.tubo(curva(pts), r0, r1, rampa, o); }
  // Trazo de 1 px por una curva (venas, grietas): el tono k de la rampa.
  function trazo(K, pts, rampa, k, luz) {
    let ux = null, uy = null;
    for (const [x, y] of curva(pts)) { const X = Math.floor(x), Y = Math.floor(y); if (X === ux && Y === uy) continue; ux = X; uy = Y; K.px(X, Y, rampa[k], rampa, luz); }
  }
  // Bulto de Podre con poros que brillan.
  function podre(K, c, r, semilla) {
    K.ovalo(c, r, r * 0.85, R.podre, { tex: (x, y) => ((x * 7 + y * 13 + semilla) % 5 === 0 ? -0.3 : 0) });
    const n = Math.max(2, Math.round(r));
    for (let i = 0; i < n; i++) {
      const a = semilla * 1.7 + i * 2.4, rr = r * (0.25 + ((i * 37 + semilla) % 5) * 0.1);
      K.px(c[0] + Math.cos(a) * rr, c[1] + Math.sin(a) * rr * 0.8, R.poro[2], R.poro, true);
    }
  }

  // ---------------------------------------------------------------- el Corazón
  // Un corazón de verdad que se lee de un vistazo (es el centro del juego y el icono de la app): los ventrículos con
  // la punta torcida abajo a la derecha, dos aurículas, la aorta en arco con sus tres ramas, el tronco pulmonar
  // delante y las coronarias por el surco. Le crecen cristales (su sangre seca, LORE.md). Dormido: carne morada
  // apagada, venas sin luz y bultos de Podre. Despierto: rojo, venas que laten y cristales que brillan.
  function corazon(dormido) {
    const C = dormido ? R.carneD : R.carne, A = dormido ? R.arteriaD : R.arteria;
    const V = dormido ? R.venaD : R.vena, X = dormido ? R.cristalD : R.cristal, brilla = !dormido;
    return CRI.lamina(50, 60, (K) => {
      // detrás: la cava, la aurícula izquierda y la aorta que baja por detrás
      K.pieza(-6, () => tubo(K, [[10, 24], [9.5, 15], [9, 8]], 3.2, 2.8, A));
      K.pieza(-5, () => K.ovalo([36, 24], 6.5, 5.5, C, { ang: 0.3 }));
      K.pieza(-4, () => tubo(K, [[33, 12], [36, 16], [37, 21]], 3.6, 3.2, A));
      // las tres ramas de arriba del arco
      K.pieza(-3, () => { tubo(K, [[20.5, 10], [20, 5], [19, 1.5]], 2.1, 1.6, A); tubo(K, [[25.5, 8], [26, 4], [26.5, 1]], 2, 1.5, A); tubo(K, [[30, 9], [31.5, 5.5], [33, 2.5]], 1.9, 1.4, A); });
      // el arco de la aorta: sube, se curva hacia atrás a la derecha
      K.pieza(-2, () => tubo(K, [[23.5, 27], [22.5, 18], [23.5, 11.5], [28, 8.5], [33, 11.5]], 4.6, 3.9, A));
      // los ventrículos (la silueta manda) con la punta abajo a la derecha
      K.pieza(0, () => {
        K.ovalo([25, 36], 16.5, 15, C, { ang: 0.5, brillo: true });
        tubo(K, [[26, 40], [30, 47], [33, 54]], 10, 2.4, C, { brillo: true });
      });
      // el surco entre los ventrículos (más oscuro), de arriba a la punta
      K.pieza(0.5, () => trazo(K, [[20, 26], [23, 33], [26, 41], [30, 48], [32, 52]], C, 1));
      // la aurícula derecha, a la izquierda, por delante
      K.pieza(1, () => K.ovalo([11, 30], 7, 7.5, C, { ang: -0.2, brillo: true }));
      // el tronco pulmonar delante, tapando el hueco del arco, y su rama
      K.pieza(2, () => { tubo(K, [[19.5, 31], [18, 23], [15.5, 17.5]], 4.3, 3.7, A); tubo(K, [[16, 18], [11, 15], [7, 15]], 2.9, 2.3, A); });
      // las coronarias: por el surco de arriba y bajando por el de en medio hasta la punta, con ramas
      K.pieza(3, () => {
        const k = dormido ? 2 : 2;
        trazo(K, [[13, 37], [18, 33], [22, 31.5], [29, 32], [36, 31], [41, 34]], V, k, brilla);
        trazo(K, [[21, 32], [22, 36], [25, 42], [28, 47], [31, 51]], V, k, brilla);
        trazo(K, [[23, 38], [19, 42], [17, 46]], V, k, brilla);
        trazo(K, [[26, 43], [31, 43], [35, 45]], V, k, brilla);
        trazo(K, [[34, 31.5], [36, 37], [38, 41]], V, k, brilla);
        if (!dormido) for (const [x, y] of [[22, 31], [25, 41], [18, 33]]) K.px(x, y, V[3], V, true);
      });
      // cristales que le crecen por los hombros y alguno en la carne
      K.pieza(4, () => {
        cristal(K, [7, 26], [1, 13], 4.5, X, brilla);
        cristal(K, [10, 24], [9, 15], 2.6, X, brilla);
        cristal(K, [39, 21], [46, 10], 4, X, brilla);
        cristal(K, [41, 26], [47, 22], 2.4, X, brilla);
        cristal(K, [18, 46], [13, 52], 2.4, X, brilla);
      });
      // la Podre: muchos bultos si duerme, uno si está despierto
      K.pieza(5, () => {
        const bultos = dormido ? [[30, 44, 3.6], [15, 40, 2.8], [36, 33, 2.6], [22, 51, 2.2], [9, 33, 2]] : [[35, 46, 2.4]];
        bultos.forEach(([x, y, r], k) => podre(K, [x, y], r, 7 + k * 11));
      });
    });
  }

  // ---------------------------------------------------------------- árboles
  // Como en Core Keeper: la copa son 8–11 racimos grandes, cada uno con su volumen (claro arriba a la izquierda,
  // oscuro abajo) y una línea oscura donde uno tapa al de detrás; sobre todo, una luz de conjunto (la copa entera
  // más clara arriba a la izquierda). El tronco con corteza en vetas y raíces que se agarran al suelo.
  // o: { W, H, copa: [cx, cy, rx, ry], racimo (radio), hojas (rampa), fruta, luna, semilla, alto (del tronco) }
  function arbol(o) {
    const { W, H } = o, [cx, cy, rx, ry] = o.copa, Hj = o.hojas, rr = o.racimo || 7;
    const azarA = azar(o.semilla || 1);
    const pie = H - 3, tx = W / 2;
    return CRI.lamina(W, H, (K) => {
      // el tronco y las raíces
      const veta = (x, y) => ((x * 3 + Math.floor(y / 3)) % 4 === 0 ? -0.35 : 0);
      K.pieza(-20, () => {
        K.tubo(curva([[tx, pie], [tx + 0.5, pie - (pie - cy) * 0.45], [tx - 0.5, cy + 2]]), 3.6, 2.6, R.corteza, { tex: veta });
        for (const [dx, l] of [[-1, -7], [1, 7], [0.2, -3.5]]) K.tubo(curva([[tx + dx * 1.5, pie - 4], [tx + l * 0.6, pie - 1], [tx + l, pie + 1.5]]), 2, 0.8, R.corteza);
      });
      // ramas que entran en la copa
      K.pieza(-19, () => { K.tubo(curva([[tx, cy + 8], [tx - 5, cy + 3], [tx - 8, cy]]), 1.5, 0.9, R.corteza); K.tubo(curva([[tx + 0.5, cy + 6], [tx + 5, cy + 2], [tx + 8, cy - 1]]), 1.4, 0.9, R.corteza); });
      // los racimos: tres filas (detrás, en medio, delante), con un poco de azar
      const filas = o.filas || [[-0.55, 3], [-0.05, 4], [0.5, 3]];
      const racimos = [];
      for (const [fy, n] of filas) {
        const w = Math.sqrt(Math.max(0.15, 1 - fy * fy));
        for (let i = 0; i < n; i++) {
          const fx = n === 1 ? 0 : (i / (n - 1) - 0.5) * 2 * w * 0.78;
          racimos.push([cx + fx * rx + (azarA() - 0.5) * 2.4, cy + fy * ry + (azarA() - 0.5) * 2, rr * (0.85 + azarA() * 0.3)]);
        }
      }
      racimos.sort((a, b) => a[1] - b[1]);
      // luz de conjunto: la copa entera más clara arriba a la izquierda
      const conjunto = (x, y) => -(((x - cx) / rx) * 0.14 + ((y - cy) / ry) * 0.26);
      racimos.forEach(([x, y, r], k) => K.pieza(k, () => {
        // borde de hojas (ondulado) y dentro, escamas de hojas: cada una un poco más clara arriba que abajo
        const fase = azarA() * 6.28, borde = (px, py) => { const a = Math.atan2((py + 0.5 - y) / 0.86, px + 0.5 - x), d = Math.hypot(px + 0.5 - x, (py + 0.5 - y) / 0.86); return d > r * (0.93 + 0.1 * Math.sin(a * 6 + fase)); };
        const hojas = (px, py) => { const fy = ((py + 100) % 4), fx = ((px + 100 + (Math.floor((py + 100) / 4) % 2) * 2) % 4); return fy === 0 && fx !== 0 ? -0.22 : fy === 1 && fx === 1 ? 0.12 : 0; };
        // cada racimo, con su sombra abajo (lo que más lo separa del de detrás)
        const bajo = (px, py) => (py + 0.5 - y > r * 0.5 ? -0.28 : py + 0.5 - y > r * 0.25 ? -0.1 : 0);
        K.ovalo([x, y], r * 1.1, r * 0.95, Hj, { corta: borde, tex: (px, py) => conjunto(px, py) + hojas(px, py) + bajo(px, py) - 0.16 });
        // hojas sueltas de luz en el lado de la lámpara
        for (const [a, d] of [[-2.3, 0.55], [-1.9, 0.3], [-2.8, 0.42]]) { const lx = x + Math.cos(a) * r * d, ly = y + Math.sin(a) * r * d * 0.86; if (conjunto(lx, ly) > -0.1) { K.px(lx, ly, Hj[4], Hj); K.px(lx + 1, ly, Hj[4], Hj); } }
        if (o.fruta && azarA() < o.fruta) { const fx = x + (azarA() - 0.3) * r, fy = y + (azarA() * 0.4) * r; K.px(fx, fy, R.fruta[2], R.fruta); K.px(fx + 1, fy, R.fruta[1], R.fruta); K.px(fx, fy - 1, R.fruta[3], R.fruta); }
        if (o.luna && azarA() < o.luna) { const lx = x + (azarA() - 0.5) * r, ly = y + (azarA() - 0.2) * r * 0.6; K.px(lx, ly, R.luna[2], R.luna, true); K.px(lx, ly - 1, R.luna[1], R.luna, true); }
      }));
    });
  }
  // un dibujo del mundo: con normales de cojín (las antorchas lo iluminan por el lado)
  function mundoDe(r) { if (r.color && !r.color.n) MANO.normales(r.color); return r; }
  // Pone un dibujo quieto en lugar del modelo horneado del mismo nombre: conserva su sombra (la huella) y coloca el
  // pie del dibujo a dz casillas del centro de la casilla.
  function sustituir(nombre, r, dz, o = {}) {
    for (const suf of ['', '_luz', '_B', '_i', '_iB']) quitarDibujo(nombre + suf);
    def(nombre, r.color);
    const m = Object.assign({}, META_SPR[nombre] || {}, { dx: o.dx || 0, dz, alto: r.color.h, fondo: 16 });
    delete m.luzX; delete m.luzY;
    if (r.luz) {
      def(nombre + '_luz', r.luz);
      let sx = 0, sy = 0, n = 0;
      for (let y = 0; y < r.luz.h; y++) for (let x = 0; x < r.luz.w; x++) if (r.luz.d[y * r.luz.w + x]) { sx += x; sy += y; n++; }
      if (n) { m.luzX = (sx / n + 0.5 - r.luz.w / 2) / 16; m.luzY = (r.luz.h - sy / n - 0.5) / 16; }
    }
    META_SPR[nombre] = m;
  }
  const ARBOLES = {
    arbol: () => arbol({ W: 42, H: 58, copa: [21, 21, 16, 13], hojas: R.hoja, fruta: 0.25, semilla: 11 }),
    arbol_b: () => arbol({ W: 38, H: 62, copa: [19, 22, 13, 17], hojas: R.hoja2, semilla: 13, racimo: 6.5, filas: [[-0.75, 2], [-0.3, 3], [0.15, 3], [0.6, 3]] }),
    arbol_luna: () => arbol({ W: 40, H: 58, copa: [20, 21, 15, 13], hojas: R.hojaLuna, luna: 0.6, semilla: 21 }),
  };

  // ---------------------------------------------------------------- iconos de las estaciones
  // Todos con la misma construcción (16×16 como mucho, contorno de color, luz arriba a la izquierda): la mesa de
  // madera con su herramienta y los herrajes del metal de su nivel; el yunque del metal de su nivel sobre un tocón.
  const MET = {
    madera: ['#2a1408', '#5a3218', '#8a5428', '#b87a3e', '#dca060'],
    selva: ['#241406', '#4e3212', '#7a5420', '#a47a36', '#c8a050'],
    muerta: ['#1c1814', '#3a322a', '#584c40', '#766658', '#948472'],
    cobre: ['#3a160c', '#7a3418', '#b85a2a', '#e88a4a', '#ffc890'],
    estano: ['#2a2c34', '#5a606c', '#8c94a0', '#c0c8d0', '#f0f4f8'],
    hierro: ['#1a2030', '#3c4a62', '#6a7c98', '#9cb0c8', '#dce8f4'],
    carmesi: ['#2e0610', '#6a1020', '#a8202e', '#e0444a', '#ff9a8a'],
    medula: ['#3e2e22', '#8a7258', '#c4ac88', '#ecdcb8', '#fff8e8'],
    plata: ['#262a3a', '#5a6280', '#9aa4c0', '#d4dcee', '#ffffff'],
    piedra: ['#14161e', '#2c3040', '#464c60', '#646c82', '#8a92a8'],
    alma: ['#1a3a6a', '#4a86cc', '#7ab2f0', '#e8f6ff'],
    tuetano: ['#4a0a0a', '#a8201c', '#e04a3a', '#ff9a80'],
  };
  for (const k of Object.keys(MET)) MET[k].nombre = k;
  function recorte(L) {
    let x0 = L.w, y0 = L.h, x1 = -1, y1 = -1;
    for (let y = 0; y < L.h; y++) for (let x = 0; x < L.w; x++) if (L.d[y * L.w + x]) { x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); }
    if (x1 < 0) return L;
    const o = new Lienzo(x1 - x0 + 1, y1 - y0 + 1);
    for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) o.d[(y - y0) * o.w + x - x0] = L.d[y * L.w + x];
    return o;
  }
  // Rejilla de letras para pintar a mano (16×16): r(x0, y0, x1, y1, letra) rellena, p(x, y, letra) un píxel.
  function rejilla(w, h) {
    const g = Array.from({ length: h }, () => Array(w).fill('.'));
    const p = (x, y, c) => { if (x >= 0 && y >= 0 && x < w && y < h) g[y][x] = c; };
    const r = (x0, y0, x1, y1, c) => { for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) p(x, y, c); };
    return { g, p, r, filas: () => g.map((f) => f.join('')) };
  }
  // la paleta de las letras: 1–4 la madera (sombra → brillo), m n o p el metal, 5–8 el tercer material
  const paleta = (tabla, metal, otro) => {
    const pal = {};
    [1, 2, 3, 4].forEach((k) => { pal[k] = [tabla[k], tabla]; });
    if (metal) ['m', 'n', 'o', 'p'].forEach((c, k) => { pal[c] = [metal[k + 1], metal]; });
    if (otro) ['5', '6', '7', '8'].forEach((c, k) => { pal[c] = [otro[Math.min(otro.length - 1, k + 1)], otro, otro.luz]; });
    return pal;
  };
  // Mesa vista casi desde arriba (como los iconos de Core Keeper): el tablero manda (tablas con sus juntas, luz
  // arriba a la izquierda), el canto de delante y las patas; esquinas de metal del nivel; encima, lo suyo.
  function iconoMesa(tabla, metal, encima, otro, esquinas = true) {
    const R = rejilla(16, 14), { r, p } = R;
    r(0, 0, 15, 8, '3'); r(0, 0, 15, 0, '4'); r(0, 0, 0, 8, '4'); r(15, 1, 15, 8, '2');
    for (const y of [3, 6]) r(1, y, 15, y, '2');
    for (const [x, y] of [[5, 1], [11, 4], [3, 7], [9, 2], [13, 7]]) p(x, y, '2');
    r(0, 9, 15, 9, '1'); r(0, 10, 15, 10, '2'); r(1, 11, 2, 13, '2'); r(13, 11, 14, 13, '2'); p(1, 11, '3'); p(13, 11, '3'); r(1, 13, 2, 13, '1'); r(13, 13, 14, 13, '1');
    if (esquinas) for (const [x, y] of [[0, 0], [13, 0], [0, 8], [13, 8]]) { r(x, y, x + 2, y + 2, 'n'); p(x, y, 'p'); r(x + 1, y + 1, x + 1, y + 1, 'o'); }
    if (encima) encima(R);
    return recorte(CRI.lamina(16, 14, (K) => { K.pieza(0, () => K.rej(R.filas(), paleta(tabla, metal, otro), 0, 0)); }).color);
  }
  // lo de encima de cada mesa (en la rejilla: mismas letras)
  const martillo = (R) => { R.r(3, 5, 3, 5, '1'); R.p(4, 4, '1'); R.p(5, 3, '1'); R.p(6, 2, '1'); R.r(5, 1, 8, 2, 'n'); R.r(6, 1, 8, 1, 'p'); R.p(5, 2, 'm'); };
  const ICONOS_MESA = {
    mesa: () => iconoMesa(MET.madera, MET.estano, (R) => { martillo(R); R.r(10, 4, 13, 6, '4'); R.r(10, 6, 13, 6, '2'); R.p(10, 4, '4'); }, null, false),
    mesa_cobre: () => iconoMesa(MET.madera, MET.cobre, martillo),
    mesa_estano: () => iconoMesa(MET.madera, MET.estano, martillo),
    mesa_hierro: () => iconoMesa(MET.madera, MET.hierro, (R) => { martillo(R); R.r(9, 3, 12, 6, 'n'); R.r(9, 3, 12, 3, 'p'); R.r(12, 4, 12, 6, 'm'); }),
    mesa_carmesi: () => iconoMesa(MET.madera, MET.carmesi, (R) => { martillo(R); R.r(9, 4, 11, 6, 'n'); R.p(9, 4, 'p'); R.r(11, 5, 11, 6, 'm'); }),
    mesa_selva: () => iconoMesa(MET.selva, MET.cobre, (R) => { R.r(3, 3, 6, 5, '4'); R.r(3, 5, 6, 5, '2'); R.p(10, 5, '1'); R.p(11, 4, '1'); R.p(12, 3, '1'); R.r(12, 2, 13, 2, 'o'); }),
    mesa_medula: () => iconoMesa(MET.muerta, MET.medula, (R) => { R.r(3, 2, 6, 5, '7'); R.r(3, 2, 6, 2, '8'); R.p(4, 4, '5'); R.p(6, 4, '5'); R.r(4, 6, 5, 6, '6'); R.r(9, 2, 10, 5, 'o'); R.r(12, 2, 13, 5, 'o'); R.r(9, 4, 10, 5, 'm'); R.r(12, 4, 13, 5, 'm'); }, MET.medula),
    mesa_plata: () => iconoMesa(MET.piedra, MET.plata, (R) => { R.r(6, 4, 9, 6, 'n'); R.r(6, 4, 9, 4, 'p'); R.r(7, 1, 8, 3, '7'); R.p(7, 1, '8'); R.r(8, 2, 8, 3, '6'); }, Object.assign(MET.alma.slice(), { luz: true })),
  };
  // Yunque: la cara de arriba con su raya de luz, el cuerno a la izquierda, la sombra de debajo, la cintura y el pie.
  function iconoYunque(metal) {
    const R = rejilla(16, 12), { r, p } = R;
    r(3, 0, 15, 4, 'o'); r(3, 0, 15, 0, 'p'); r(3, 0, 3, 4, 'p'); r(15, 1, 15, 4, 'n'); r(4, 4, 15, 4, 'n');
    for (let k = 0; k < 4; k++) { p(11 - k, 1 + k, 'p'); p(12 - k, 1 + k, 'p'); }
    r(0, 1, 2, 2, 'o'); p(0, 1, 'p'); p(1, 1, 'p'); r(1, 3, 2, 3, 'n'); p(0, 2, 'n');
    r(4, 5, 14, 5, 'm');
    r(6, 6, 12, 7, 'n'); r(6, 6, 6, 7, 'o'); r(12, 6, 12, 7, 'm');
    r(3, 8, 15, 9, 'n'); r(3, 8, 15, 8, 'o'); r(3, 9, 15, 9, 'm'); p(3, 8, 'p');
    r(3, 10, 5, 11, 'm'); r(13, 10, 15, 11, 'm'); p(3, 10, 'n'); p(13, 10, 'n');
    return recorte(CRI.lamina(16, 12, (K) => { K.pieza(0, () => K.rej(R.filas(), paleta(MET.madera, metal), 0, 0)); }).color);
  }
  const ICONOS_YUNQUE = {
    yunque: () => iconoYunque(MET.cobre),
    yunque_estano: () => iconoYunque(MET.estano),
    yunque_hierro: () => iconoYunque(MET.hierro),
    yunque_carmesi: () => iconoYunque(MET.carmesi),
    yunque_medula: () => iconoYunque(MET.medula),
    yunque_plata: () => iconoYunque(MET.plata),
  };

  // Más colocables de la primera parte, al tamaño de los demás: cofre, horno, olla y cama.
  const FUEGO = Object.assign(['#7a1a08', '#e0501a', '#ffa030', '#fff0a0'], { luz: true });
  const TELA = ['#2a0a14', '#7a1c30', '#b0344a', '#e06a72', '#f8a8a0'], LANA = ['#3a3024', '#948674', '#c4b496', '#ece0c4', '#fff8e8'];
  // dibujo a mano de rejilla: { color, luz } recortados juntos (lo que brilla, en la capa de luz)
  function dibujoRej(w, h, pinta, tabla, metal, otro) {
    const R = rejilla(w, h); pinta(R);
    const r = CRI.lamina(w, h, (K) => { K.pieza(0, () => K.rej(R.filas(), paleta(tabla, metal, otro), 0, 0)); });
    const L = r.color;
    let x0 = L.w, y0 = L.h, x1 = -1, y1 = -1;
    for (let y = 0; y < L.h; y++) for (let x = 0; x < L.w; x++) if (L.d[y * L.w + x]) { x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); }
    const corta = (S) => { if (!S) return null; const o = new Lienzo(x1 - x0 + 1, y1 - y0 + 1); let hay = false; for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) { const c = S.d[y * S.w + x]; o.d[(y - y0) * o.w + x - x0] = c; if (c) hay = true; } return hay ? o : null; };
    return { color: corta(L), luz: corta(r.luz) };
  }
  function icono(w, h, pinta, tabla, metal, otro) { return dibujoRej(w, h, pinta, tabla, metal, otro).color; }
  const ICONOS_MAS_PINTA = {
    // cofre: tapa abombada con dos flejes de metal, frente con la cerradura
    cofre: ({ r, p }) => {
      r(0, 1, 14, 4, '3'); r(1, 0, 13, 0, '4'); r(0, 1, 0, 4, '4'); r(14, 1, 14, 4, '2'); r(0, 4, 14, 4, '2');
      r(0, 5, 14, 5, '1'); r(0, 6, 14, 11, '3'); r(0, 6, 0, 11, '4'); r(14, 6, 14, 11, '2'); r(0, 11, 14, 11, '1'); r(0, 8, 14, 8, '2');
      for (const x of [2, 12]) { r(x, 0, x, 11, 'n'); p(x, 0, 'p'); p(x, 5, 'm'); p(x, 11, 'm'); }
      r(6, 4, 8, 7, 'o'); p(6, 4, 'p'); r(7, 6, 7, 6, 'm'); r(8, 5, 8, 7, 'n');
    },
    // horno: cúpula de piedra con la boca de fuego y la chimenea; f: el cuadro del fuego
    horno: (f = 0) => ({ r, p }) => {
      r(5, 0, 8, 2, 'n'); r(5, 0, 6, 0, 'p'); r(8, 1, 8, 2, 'm');
      r(2, 3, 11, 4, 'o'); r(1, 5, 12, 12, 'n'); r(1, 5, 2, 12, 'o'); r(2, 3, 4, 3, 'p'); r(11, 5, 12, 12, 'm'); r(0, 13, 13, 14, 'm'); r(0, 13, 13, 13, 'n');
      for (const [x, y] of [[4, 6], [8, 9], [3, 10], [10, 7]]) p(x, y, 'm');
      r(4, 8, 9, 12, '5'); r(5, 7 + f, 8, 7 + f, '5'); r(5, 9, 8, 12, '6'); r(6 - f, 10, 7, 12, '7'); p(6 + f, 11 - f, '8');
    },
    // olla: el caldero negro con su asa, la sopa encima y el fuego debajo; f: el cuadro del fuego
    olla: (f = 0) => ({ r, p }) => {
      r(1, 2, 12, 3, 'n'); r(2, 2, 11, 2, 'o'); r(3, 3, 10, 3, '7'); r(4 + f, 3, 6 + f, 3, '8');
      r(0, 4, 13, 9, 'n'); r(0, 4, 1, 9, 'o'); r(1, 4, 1, 5, 'p'); r(11, 4, 13, 9, 'm'); r(1, 10, 12, 10, 'm');
      r(3, 0, 10, 0, 'm'); p(2, 1, 'm'); p(11, 1, 'm');
      r(3, 11, 10, 12, '6'); p(5 + f, 11, '7'); p(8 - f, 11, '7'); p(6 + f, 12, '8');
    },
  };
  const ICONOS_MAS = {
    cofre: () => icono(15, 12, ICONOS_MAS_PINTA.cofre, MET.madera, MET.estano),
    horno: () => icono(14, 15, ICONOS_MAS_PINTA.horno(0), MET.madera, MET.piedra, FUEGO),
    olla: () => icono(14, 13, ICONOS_MAS_PINTA.olla(0), MET.madera, ['#0e0e14', '#22222c', '#3a3a48', '#5a5a6a', '#8a8a9a'], FUEGO),
    // cama: vista desde arriba, la almohada y la manta con el embozo
    cama: () => icono(12, 16, ({ r, p }) => {
      r(0, 0, 11, 15, '2'); r(0, 0, 11, 0, '4'); r(0, 0, 0, 15, '3'); r(0, 15, 11, 15, '1');
      r(1, 1, 10, 4, 'o'); r(1, 1, 10, 1, 'p'); r(10, 2, 10, 4, 'n'); r(1, 4, 10, 4, 'n');
      r(1, 6, 10, 14, '6'); r(1, 6, 10, 7, '8'); r(1, 8, 1, 14, '7'); r(10, 8, 10, 14, '5'); r(1, 14, 10, 14, '5');
      for (const y of [10, 12]) for (let x = 2; x < 10; x += 2) p(x, y, '7');
    }, MET.madera, LANA, TELA),
  };

  // ---------------------------------------------------------------- los objetos del mundo, a mano
  // Como en Core Keeper (ESTUDIO-CK.md): los muebles y estaciones son dibujos de una casilla (16×16 o poco más) casi
  // iguales que su icono, y el fuego va aparte y se mueve (aquí, cuadros del horno, la olla y la antorcha).
  const FUEGO_AZUL = Object.assign(['#0a1a5a', '#2a5ae0', '#5ab0ff', '#d8f4ff'], { luz: true });
  const llama = [
    ['..7..', '.676.', '.686.', '56865', '.565.'],
    ['.7...', '.676.', '.6876', '56865', '.565.'],
    ['...7.', '.676.', '6786.', '56865', '.565.'],
    ['..7..', '.6.6.', '.686.', '56865', '.565.'],
  ];
  const MUNDO = {
    // horno: dos cuadros con el fuego distinto
    horno_0: () => dibujoRej(14, 15, ICONOS_MAS_PINTA.horno(0), MET.madera, MET.piedra, FUEGO),
    horno_1: () => dibujoRej(14, 15, ICONOS_MAS_PINTA.horno(1), MET.madera, MET.piedra, FUEGO),
    olla_0: () => dibujoRej(14, 13, ICONOS_MAS_PINTA.olla(0), MET.madera, ['#0e0e14', '#22222c', '#3a3a48', '#5a5a6a', '#8a8a9a'], FUEGO),
    olla_1: () => dibujoRej(14, 13, ICONOS_MAS_PINTA.olla(1), MET.madera, ['#0e0e14', '#22222c', '#3a3a48', '#5a5a6a', '#8a8a9a'], FUEGO),
    cofre: () => ({ color: ICONOS_MAS.cofre() }),
    cofre_ruinas: () => ({ color: icono(15, 12, ICONOS_MAS_PINTA.cofre, MET.piedra, Object.assign(['#3a2a08', '#a8701c', '#e8b040', '#ffe68a', '#fffbe0'])) }),
    cama: () => ({ color: ICONOS_MAS.cama() }),
    // la antorcha: palo, el trapo atado y la llama (4 cuadros); la mágica, con fuego azul
    ...Object.fromEntries([0, 1, 2, 3].flatMap((f) => [[`antorcha_${f}`, () => antorcha(f, FUEGO)], [`antorcha_${f}m`, () => antorcha(f, FUEGO_AZUL)]])),
    puerta: () => dibujoRej(16, 16, ({ r, p }) => {
      r(0, 0, 15, 15, '2'); r(0, 0, 15, 0, '4'); r(0, 0, 0, 15, '3');
      for (const x of [4, 8, 12]) { r(x, 1, x, 15, '1'); r(x + 1, 1, x + 1, 15, '3'); }
      for (const y of [3, 12]) { r(0, y, 15, y, 'n'); r(0, y - 1, 15, y - 1, 'o'); p(1, y, 'p'); p(14, y, 'p'); }
      r(12, 7, 13, 8, 'o'); p(12, 7, 'p');
    }, MET.madera, MET.hierro),
    puerta_abierta: () => dibujoRej(5, 16, ({ r }) => { r(0, 0, 4, 15, '2'); r(0, 0, 0, 15, '3'); r(4, 0, 4, 15, '1'); r(0, 3, 4, 3, 'n'); r(0, 12, 4, 12, 'n'); }, MET.madera, MET.hierro),
    // el farol: poste, el brazo y el farol de cristal con la llama
    farol: () => dibujoRej(10, 22, ({ r, p }) => {
      r(1, 3, 2, 21, '2'); r(1, 3, 1, 21, '3'); r(0, 20, 3, 21, '1');
      r(1, 3, 7, 3, '2'); r(1, 2, 7, 2, '3');
      r(5, 4, 9, 4, 'n'); r(5, 5, 9, 10, 'm'); r(6, 5, 8, 9, '6'); p(7, 6, '8'); r(6, 7, 8, 8, '7'); r(5, 11, 9, 11, 'n');
    }, MET.madera, MET.hierro, FUEGO),
    // la lámpara de cristal: pie de hierro y el cristal que brilla
    lampara: () => dibujoRej(7, 14, ({ r, p }) => {
      r(1, 11, 5, 13, 'n'); r(1, 11, 5, 11, 'o'); r(3, 7, 3, 10, 'm');
      r(2, 1, 4, 6, '6'); p(3, 0, '7'); r(2, 1, 2, 5, '7'); p(3, 3, '8'); r(4, 2, 4, 6, '5');
    }, MET.madera, MET.hierro, Object.assign(['#0a3a4a', '#1a90a4', '#5ae4f0', '#e8ffff'], { luz: true })),
  };
  // ---- formas para pintar a mano: una bola con luz (arriba a la izquierda claro, abajo a la derecha oscuro)
  function bola(R, cx, cy, rx, ry, t) { // t: [sombra, medio, luz, brillo] (letras)
    for (let y = Math.floor(cy - ry); y <= Math.ceil(cy + ry); y++) for (let x = Math.floor(cx - rx); x <= Math.ceil(cx + rx); x++) {
      const u = (x + 0.5 - cx) / rx, v = (y + 0.5 - cy) / ry, d = u * u + v * v;
      if (d > 1) continue;
      const l = -u * 0.55 - v * 0.8;
      R.p(x, y, d > 0.72 && l < -0.1 ? t[0] : l > 0.45 ? t[3] || t[2] : l > 0.05 ? t[2] : t[1]);
    }
  }
  // roca de mena (como los «ore boulders» de Core Keeper): un canto rodado de piedra con pepitas del metal
  function roca(metal) {
    return dibujoRej(16, 13, (R) => {
      bola(R, 8, 7.5, 7.6, 5.8, ['1', '2', '3', '4']);
      for (const [x, y] of [[4, 6], [10, 4], [12, 8], [6, 9]]) R.p(x, y, '1');
      for (const [x, y, n] of [[3, 3, 3], [9, 2, 2], [11, 6, 3], [5, 8, 2], [8, 10, 2]]) { R.r(x, y, x + n - 1, y + 1, 'n'); R.p(x, y, 'p'); R.p(x + n - 1, y + 1, 'm'); if (n > 2) R.p(x + 1, y, 'o'); }
    }, MET.piedra, metal);
  }
  const VIDRIO = ['#0a3a4a', '#16708a', '#3ac0d8', '#9af0ff', '#ffffff'];
  const VERDE = ['#0c2a12', '#1e4a1e', '#367a2c', '#5aa83c', '#9ad85a'];
  const BARRO = ['#3a1a0c', '#7a3a1c', '#b4602e', '#e08a4c', '#f8c08a'];
  const OTROS = {
    roca_cobre: () => roca(MET.cobre), roca_estano: () => roca(MET.estano), roca_hierro: () => roca(MET.hierro),
    roca_oro: () => roca(['#3a2a08', '#a8701c', '#e8b040', '#ffe68a', '#fffbe0']), roca_carmesi: () => roca(MET.carmesi),
    roca_medula: () => roca(MET.medula), roca_plata: () => roca(MET.plata),
    // cristal: tres agujas que brillan sobre una base de roca
    cristal: () => dibujoRej(13, 15, (R) => {
      R.r(1, 12, 11, 14, '2'); R.r(2, 12, 10, 12, '3'); R.r(1, 14, 11, 14, '1');
      R.r(5, 1, 7, 12, '6'); R.r(5, 1, 5, 12, '7'); R.r(7, 2, 7, 12, '5'); R.p(6, 0, '7'); R.p(5, 3, '8'); R.p(5, 4, '8');
      R.r(1, 6, 3, 12, '6'); R.r(1, 6, 1, 12, '7'); R.p(2, 5, '7'); R.r(3, 7, 3, 12, '5');
      R.r(9, 8, 11, 12, '6'); R.r(9, 8, 9, 12, '7'); R.p(10, 7, '7'); R.r(11, 9, 11, 12, '5');
    }, MET.piedra, MET.piedra, Object.assign(VIDRIO.slice(), { luz: true })),
    // caja de madera con esquinas de hierro
    caja_madera: () => dibujoRej(14, 13, (R) => {
      R.r(0, 0, 13, 12, '3'); R.r(0, 0, 13, 0, '4'); R.r(0, 0, 0, 12, '4'); R.r(13, 1, 13, 12, '2'); R.r(0, 12, 13, 12, '1');
      R.r(0, 4, 13, 4, '2'); R.r(0, 8, 13, 8, '2'); R.p(3, 2, '2'); R.p(9, 6, '2'); R.p(5, 10, '2');
      for (const [x, y] of [[0, 0], [12, 0], [0, 11], [12, 11]]) { R.r(x, y, x + 1, y + 1, 'n'); R.p(x, y, 'p'); }
    }, MET.madera, MET.hierro),
    // vasijas de barro (la de las flores, con flores)
    vasija: () => dibujoRej(11, 13, (R) => { bola(R, 5.5, 8, 5.2, 4.8, ['1', '2', '3', '4']); R.r(3, 1, 7, 3, '2'); R.r(3, 1, 5, 1, '3'); R.r(2, 0, 8, 0, '3'); R.r(4, 6, 7, 6, '1'); }, BARRO, MET.hierro),
    vasija_flor: () => dibujoRej(11, 17, (R) => {
      bola(R, 5.5, 12, 5.2, 4.8, ['1', '2', '3', '4']); R.r(3, 6, 7, 7, '2'); R.r(3, 6, 5, 6, '3');
      for (const [x, y, c] of [[2, 3, 'o'], [5, 1, 'p'], [8, 3, 'o'], [3, 5, 'n'], [7, 5, 'n']]) { R.p(x, y, c); R.p(x + 1, y, c); R.p(x, y + 1, 'm'); }
    }, BARRO, ['#3a0a20', '#9a2050', '#e04a7a', '#ff9ab8', '#ffd8e4']),
    maceta: () => dibujoRej(10, 13, (R) => {
      R.r(1, 7, 8, 12, '2'); R.r(1, 7, 8, 7, '4'); R.r(1, 8, 1, 12, '3'); R.r(8, 8, 8, 12, '1'); R.r(2, 12, 7, 12, '1');
      for (const [x, y] of [[2, 3], [5, 1], [7, 3], [4, 4]]) { R.p(x, y, 'o'); R.p(x + 1, y, 'p'); R.p(x, y + 1, 'n'); R.p(x + 1, y + 1, 'm'); }
      R.r(4, 5, 4, 6, 'n'); R.r(6, 5, 6, 6, 'm');
    }, BARRO, VERDE),
    // estantería con libros de colores
    estanteria: () => dibujoRej(14, 18, (R) => {
      R.r(0, 0, 13, 17, '2'); R.r(0, 0, 13, 1, '4'); R.r(0, 0, 0, 17, '3'); R.r(13, 0, 13, 17, '1'); R.r(0, 17, 13, 17, '1');
      for (const y of [6, 11, 16]) { R.r(1, y, 12, y, '3'); R.r(1, y - 4, 12, y - 1, '1'); }
      const libros = ['m', 'n', 'o', '5', '6', '7', '8'];
      let k = 0;
      for (const y of [2, 7, 12]) for (let x = 1; x < 12; x += 1) { if ((x * 7 + y) % 5 === 0) continue; const c = libros[k++ % libros.length]; R.r(x, y + ((x + y) % 3 === 0 ? 1 : 0), x, y + 3, c); }
    }, MET.madera, ['#1a2a5a', '#2a4aa0', '#4a7ad0', '#8ab0f0', '#d0e0ff'], ['#4a1010', '#8a2020', '#c84a2a', '#e8b040']),
    // banco de piedra
    banco_piedra: () => dibujoRej(18, 8, (R) => { R.r(0, 0, 17, 2, '3'); R.r(0, 0, 17, 0, '4'); R.r(0, 3, 17, 3, '1'); for (const x of [1, 14]) { R.r(x, 4, x + 2, 7, '2'); R.r(x, 4, x, 7, '3'); } }, MET.piedra, MET.piedra),
    // incubadora: la cúpula de cristal con el huevo, sobre un pie de cobre
    incubadora: () => dibujoRej(12, 14, (R) => {
      R.r(1, 10, 10, 13, 'n'); R.r(1, 10, 10, 10, 'o'); R.r(10, 11, 10, 13, 'm'); R.p(2, 11, 'p');
      bola(R, 5.5, 5.5, 5, 5, ['5', '6', '7', '8']); bola(R, 5.5, 6.5, 2.4, 3, ['1', '2', '3', '4']);
    }, ['#3a3024', '#948674', '#c4b496', '#ece0c4', '#fff8e8'], MET.cobre, Object.assign(VIDRIO.slice(), { luz: true })),
    // plantas
    hongo: () => dibujoRej(11, 11, (R) => { R.r(4, 5, 6, 10, '3'); R.r(4, 5, 4, 10, '4'); R.r(6, 6, 6, 10, '2'); bola(R, 5.5, 4, 5.4, 3.6, ['5', '6', '7', '8']); R.r(1, 6, 9, 6, '5'); R.p(3, 2, '8'); }, ['#3a3024', '#5e5446', '#948674', '#c4b496', '#ece0c4'], MET.piedra, Object.assign(['#0a2a5a', '#1a6ab0', '#3ab0f0', '#b0f0ff'], { luz: true })),
    flor_sol: () => dibujoRej(9, 16, (R) => {
      R.r(4, 7, 4, 15, 'n'); R.r(5, 9, 5, 15, 'm'); R.r(1, 10, 3, 11, 'o'); R.p(1, 10, 'p'); R.r(5, 12, 7, 13, 'o');
      bola(R, 4.5, 4, 4.2, 4, ['5', '6', '7', '8']); bola(R, 4.5, 4, 1.8, 1.8, ['1', '1', '2', '2']);
    }, MET.madera, VERDE, Object.assign(['#7a3a08', '#d07a10', '#f8b830', '#fff090'], { luz: true })),
    helecho: () => dibujoRej(13, 11, (R) => {
      for (const [x0, y0, dx] of [[6, 10, -1], [6, 10, 1], [6, 10, 0]]) for (let i = 0; i < 9; i++) { const x = x0 + Math.round(dx * i * 0.7), y = y0 - i; R.p(x, y, i % 2 ? 'n' : 'o'); if (i > 1 && i % 2 === 0) { R.p(x - 1, y, 'm'); R.p(x + 1, y, 'p'); } }
    }, MET.madera, VERDE),
    planta_fibra: () => dibujoRej(9, 13, (R) => { for (const [x, h] of [[1, 9], [3, 12], [5, 10], [7, 8]]) { R.r(x, 13 - h, x, 12, '3'); R.p(x, 13 - h, '4'); R.p(x + 1, 13 - h + 2, '2'); } }, ['#3a3010', '#6a5a24', '#a89040', '#d8c070', '#f0e0a0'], MET.piedra),
    bulbo_silvestre: () => dibujoRej(9, 11, (R) => { bola(R, 4.5, 7, 3.6, 3.6, ['5', '6', '7', '8']); for (const [x, y] of [[2, 1], [4, 0], [6, 1]]) { R.r(x, y, x, y + 3, 'n'); R.p(x, y, 'p'); } }, MET.madera, VERDE, ['#5a1a08', '#a83a10', '#e86a2a', '#ffb070']),
    arbusto: () => dibujoRej(15, 11, (R) => { bola(R, 7.5, 6, 7.2, 5, ['m', 'n', 'o', 'p']); for (const [x, y] of [[3, 5], [7, 3], [11, 6], [5, 8], [9, 8]]) { R.p(x, y, '7'); R.p(x + 1, y, '6'); R.p(x, y - 1, '8'); } }, MET.madera, VERDE, Object.assign(['#0a1a5a', '#2a4ad0', '#5a8aff', '#c0e0ff'], { luz: true })),
    junco: () => dibujoRej(10, 17, (R) => { for (const [x, h, c] of [[1, 12, 1], [3, 16, 1], [5, 13, 0], [7, 15, 1], [8, 10, 0]]) { R.r(x, 17 - h, x, 16, 'n'); if (c) { R.r(x, 17 - h, x, 17 - h + 3, '2'); R.p(x, 17 - h, '3'); } } }, MET.madera, VERDE),
    seta_podre: () => dibujoRej(11, 11, (R) => { R.r(4, 6, 6, 10, '2'); R.r(4, 6, 4, 10, '3'); bola(R, 5.5, 4.5, 5.2, 3.8, ['m', 'n', 'o', 'p']); for (const [x, y] of [[3, 3], [7, 4], [5, 2]]) R.p(x, y, '7'); }, ['#1a1418', '#2a2024', '#4a3a40', '#6a5a5e', '#8a7a7c'], ['#0a060e', '#1c0e20', '#2e1834', '#42244a', '#583060'], Object.assign(['#5a0a40', '#b0207a', '#ff3cb4', '#ffb0e4'], { luz: true })),
  };
  Object.assign(MUNDO, OTROS);
  function antorcha(f, fuego) {
    return dibujoRej(5, 14, ({ g }) => {
      llama[f].forEach((fila, j) => [...fila].forEach((c, i) => { if (c !== '.') g[j][i] = c; }));
      for (const [j, fila] of [[5, '.nnn.'], [6, '.mmm.']]) [...fila].forEach((c, i) => { if (c !== '.') g[j][i] = c; });
      for (let j = 7; j < 14; j++) { g[j][2] = '3'; g[j][3] = '2'; }
      g[13][2] = '1'; g[13][3] = '1';
    }, MET.madera, MET.cobre, fuego);
  }
  // dónde va el pie de cada objeto (casillas al sur del centro de su casilla)
  const PIE = { horno_0: 0.4, horno_1: 0.4, olla_0: 0.35, olla_1: 0.35, cofre: 0.3, cofre_ruinas: 0.3, cama: 0.45, puerta: 0.15, puerta_abierta: 0.15, farol: 0.15, lampara: 0.15 };

  function registrar() {
    for (const [n, f] of Object.entries(Object.assign({}, ICONOS_MESA, ICONOS_YUNQUE))) sustituir(n, mundoDe({ color: f() }), 0.3);
    for (const [n, f] of Object.entries(MUNDO)) sustituir(n, mundoDe(f()), PIE[n] ?? 0.08);
    for (const [id, f] of Object.entries(ICONOS_MAS)) { quitarDibujo('i_' + id); def('i_' + id, f()); }
    for (const [id, f] of Object.entries(Object.assign({}, ICONOS_MESA, ICONOS_YUNQUE))) { quitarDibujo('i_' + id); def('i_' + id, f()); }
    for (const [n, f] of Object.entries(ARBOLES)) sustituir(n, f(), 0.18);
    for (const [nombre, dormido] of [['corazon', false], ['corazon_dormido', true]]) {
      const r = corazon(dormido);
      for (const suf of ['', '_luz', '_B', '_sombra']) quitarDibujo(nombre + suf);
      def(nombre, r.color);
      if (r.luz) def(nombre + '_luz', r.luz);
      delete META_SPR[nombre];
    }
  }
  return { R, MET, corazon, cristal, podre, tubo, trazo, curva, arbol, ARBOLES, ICONOS_MESA, ICONOS_YUNQUE, ICONOS_MAS, MUNDO, sustituir, recorte, registrar };
})();

if (typeof CONSTRUIR_MODELOS !== 'undefined') CONSTRUIR_MODELOS.push(() => OBJ_PX.registrar());
