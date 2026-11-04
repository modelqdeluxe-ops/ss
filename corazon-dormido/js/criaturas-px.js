'use strict';
// Criaturas dibujadas a mano cuadro a cuadro, construidas como las de Core Keeper (ESTUDIO-CK.md: el caveling mide
// unos 15×19, quieto en 5 cuadros, anda en 6 y ataca en 6 con el arco del golpe dibujado, en tres direcciones). Cada
// criatura es un muñeco de piezas a mano (rejillas de letras: cabeza, torso, brazos, piernas, arma) que se colocan
// píxel a píxel en cada cuadro, sin girar nada; las letras son papeles (capucha, hueso, piel, cuero…) que se pintan con
// la paleta de cada variante. Sustituyen a las de esqueleto del mismo nombre (CRI.DEF[tipo].manual).

const CRI_PX = (() => {
  const W = 44, H = 34, OX = 22, OY = 31; // la lámina; los pies en la fila OY
  const fil = (g) => g.split('\n').map((f) => f.trim()).filter((f) => f.length);
  // pinta una rejilla con la paleta; (x, y) en píxeles de la lámina; o.voltear: espejo
  function poner(L, g, pal, x, y, o = {}) {
    let f = typeof g === 'string' ? fil(g) : g;
    if (o.alzada) f = f.slice(0, f.length - 1);
    if (o.voltear) f = f.map((r) => [...r].reverse().join(''));
    for (let j = 0; j < f.length; j++) for (let i = 0; i < f[j].length; i++) {
      const k = f[j][i];
      if (k === '.' || k === ' ') continue;
      const c = pal[k];
      if (c === undefined) throw new Error(`CRI_PX: la letra «${k}» no tiene color`);
      if (c) L.px(Math.round(x) + i, Math.round(y) + j, c);
    }
  }
  // línea de 1 px (asta, mango): Bresenham, con el tono del lado de la luz arriba
  function linea(L, a, b, c) {
    let x0 = Math.round(a[0]), y0 = Math.round(a[1]); const x1 = Math.round(b[0]), y1 = Math.round(b[1]);
    const dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
    let e = dx + dy;
    for (;;) { L.px(x0, y0, c); if (x0 === x1 && y0 === y1) break; const e2 = 2 * e; if (e2 >= dy) { e += dy; x0 += sx; } if (e2 <= dx) { e += dx; y0 += sy; } }
  }
  // el contorno de fuera del tono más oscuro del vecino (como terminar de CRI): aquí las rejillas ya lo traen, solo se
  // completan los huecos de 1 px; y las normales de cojín para la luz del juego
  function acabar(L) { MANO.normales(L); return L; }

  // ---------------------------------------------------------------- el gruto (caveling de Ásor)
  // Papeles: capucha K k c C · hueso O o u U · ojos E y · piel P p s S · cuero L l m · madera W w · punta de hueso
  // (la de la lanza) con las letras del hueso.
  const GRUTO = {
    cabeza: {
      ab: `
....KKKKK....
..KKcCCCcKK..
.KcCCOOOcckK.
.KcOOuUUOOkK.
KcOuUUUUUuOkK
KcOEEuUuEEOkK
KkOEyuUuyEOkK
.KOouUUUuoOK.
.KkOouuuoOkK.
..KkOOuOOkK..
...KkOUOkK...`,
      de: `
...KKKKK.....
.KKccCCKK....
KccCCcOOOO...
KcCcOOuUUUO..
KccOuUUUUUUO.
KccOEyuUUUUUO
KkcOEEuUUUOO.
KkkOouUUuoO..
.KkkOouuuOU..
..KkkOOOOU...
...KKk.......`,
      ar: `
....KKKKK....
..KKcCCCcKK..
.KcCCCCCcckK.
.KcCCcCCcckK.
KccCcccCcckkK
KcccccccckkkK
KkccckcckkkkK
.KkcckkckkkK.
.KkkkkkkkkkK.
..KkkkkkkkK..
...KKKKKKK...`,
    },
    torso: {
      ab: `
PSSssP
PsPPsP
.PssP.
LlmmlL`,
      de: `
.PSsP
PSssP
PsspP
LlmlL`,
      ar: `
PSssSP
PspPsP
.PssP.
LllllL`,
    },
    brazo: `
Ps
Ps
Ps
pS`,
    brazoAtras: `
pp
pp
pp`,
    pierna: `
Ps
Ps
LL`,
    piernaAtras: `
pp
pp
LL`,
  };
  // Cuadros. Cada uno: c [dx, dy] de la cabeza · t del torso · bI / bD brazos (de lado: b delante, ba detrás) ·
  // pI / pD piernas [dx, dy, alzada] (de lado: pd delante, pa detrás) · arma: [x0, y0, x1, y1] del asta (px del
  // muñeco) · estela: el arco del golpe · ojos: 'c' cerrados.
  const QUIETO = [{}, {}, { c: [0, 1] }, { c: [0, 1], cuerpo: 1 }, { c: [0, 1] }, {}];
  const ANDAR = {
    frente: [
      { c: [0, 1], cuerpo: 1, pI: [0, 0, 0], pD: [0, 0, 0], bI: [0, 0], bD: [0, 0] },
      { pI: [0, -1, 1], pD: [0, 0, 0], bI: [0, -1], bD: [0, 1] },
      { pI: [0, -1, 1], pD: [0, 0, 0], bI: [0, -1], bD: [0, 1], c: [0, -1] },
      { c: [0, 1], cuerpo: 1, pI: [0, 0, 0], pD: [0, 0, 0] },
      { pI: [0, 0, 0], pD: [0, -1, 1], bI: [0, 1], bD: [0, -1] },
      { pI: [0, 0, 0], pD: [0, -1, 1], bI: [0, 1], bD: [0, -1], c: [0, -1] },
    ],
    lado: [
      { c: [0, 1], cuerpo: 1, pd: [0, 0, 0], pa: [0, 0, 0], b: [0, 0], ba: [0, 0] },
      { pd: [1, -1, 1], pa: [-1, 0, 0], b: [-1, 0], ba: [1, 0] },
      { pd: [2, 0, 0], pa: [-2, 0, 0], b: [-1, 0], ba: [1, 0], c: [0, -1] },
      { c: [0, 1], cuerpo: 1, pd: [0, 0, 0], pa: [0, 0, 0] },
      { pd: [-1, 0, 0], pa: [1, -1, 1], b: [1, 0], ba: [-1, 0] },
      { pd: [-2, 0, 0], pa: [2, 0, 0], b: [1, 0], ba: [-1, 0], c: [0, -1] },
    ],
  };

  // Monta un cuadro del gruto. v: vista (de, ab, ar); k: el cuadro; o: la variante (paleta, arma, piel…).
  function montar(v, k, o) {
    const L = new Lienzo(W, H), luz = new Lienzo(W, H), pal = o.pal, G = o.piezas || GRUTO;
    // el muñeco mide 15 de ancho; su columna 0 está en OX-7 y su fila 17 (los pies) en OY
    const X = OX - 7 + (k.x || 0), Y = OY - 17 + (k.y || 0), cu = k.cuerpo || 0;
    const c = k.c || [0, 0], t = k.t || [0, 0];
    const arma = () => { if (k.arma && o.arma) o.arma(L, luz, pal, k.arma.map((n, i) => n + (i % 2 ? Y : X)), k, v); };
    if (v === 'de') {
      const pd = k.pd || [0, 0, 0], pa = k.pa || [0, 0, 0], b = k.b || [0, 0], ba = k.ba || [0, 0];
      if (k.armaDetras) arma();
      poner(L, G.brazoAtras, pal, X + 8 + ba[0] + t[0], Y + 11 + ba[1] + t[1] + cu);
      poner(L, G.piernaAtras, pal, X + 6 + pa[0], Y + 15 + pa[1], { alzada: pa[2] });
      poner(L, G.pierna, pal, X + 6 + pd[0], Y + 15 + pd[1], { alzada: pd[2] });
      poner(L, G.torso.de, pal, X + 5 + t[0], Y + 11 + t[1] + cu);
      poner(L, G.cabeza.de, pal, X + 1 + c[0], Y + c[1] + cu);
      if (o.hojas) hojas(L, v, X + 1 + c[0], Y + c[1] + cu, pal);
      if (o.adorno) adornar(L, v, X + 1 + c[0], Y + c[1] + cu, pal, o.adorno);
      if (o.ojosLuz && k.ojos !== 'c') ojos(luz, v, X + 1 + c[0], Y + c[1] + cu, pal);
      poner(L, G.brazo, pal, X + 7 + b[0] + t[0], Y + 11 + b[1] + t[1] + cu);
      if (!k.armaDetras) arma();
    } else {
      const pI = k.pI || [0, 0, 0], pD = k.pD || [0, 0, 0], bI = k.bI || [0, 0], bD = k.bD || [0, 0];
      const atras = v === 'ar';
      if (atras && !k.armaDelante) arma();
      poner(L, G.pierna, pal, X + 5 + pI[0], Y + 15 + pI[1], { alzada: pI[2] });
      poner(L, G.pierna, pal, X + 8 + pD[0], Y + 15 + pD[1], { alzada: pD[2], voltear: true });
      poner(L, G.brazo, pal, X + 2 + bI[0] + t[0], Y + 11 + bI[1] + t[1] + cu);
      poner(L, G.brazo, pal, X + 10 + bD[0] + t[0], Y + 11 + bD[1] + t[1] + cu, { voltear: true });
      poner(L, G.torso[v], pal, X + 4 + t[0], Y + 11 + t[1] + cu);
      poner(L, G.cabeza[v], pal, X + 1 + c[0], Y + c[1] + cu);
      if (o.hojas) hojas(L, v, X + 1 + c[0], Y + c[1] + cu, pal);
      if (o.adorno) adornar(L, v, X + 1 + c[0], Y + c[1] + cu, pal, o.adorno);
      if (!atras && o.ojosLuz && k.ojos !== 'c') ojos(luz, v, X + 1 + c[0], Y + c[1] + cu, pal);
      if (!atras || k.armaDelante) arma();
    }
    if (o.bolsa) { const bx = v === 'de' ? X + 3 : v === 'ab' ? X + 9 : X + 3; poner(L, `
.Ll.
Llml
LllL
.LL.`, pal, bx + t[0], Y + 13 + t[1] + cu); luz.px(bx + 2 + t[0], Y + 14 + t[1] + cu, pal.Q2); L.px(bx + 2 + t[0], Y + 14 + t[1] + cu, pal.Q2); }
    if (k.ojos === 'c') for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) { const q = L.get(x, y); if (q === pal.y || q === pal.Y) L.px(x, y, pal.E); }
    if (k.estela) estela(L, k.estela.map((n, i) => n + (i === 0 ? X : i === 1 ? Y : 0)), pal);
    if (k.tumbado) tumbar(L);
    if (k.hundido) hundir(L, k.hundido);
    return { color: acabar(L), luz, puntos: { cabeza: [X + 7, Y + 4] } };
  }
  // adornos de la cabeza por vista (x, y: la esquina de la cabeza): cuernos de cabra, aleta de pez, hojas
  const ADORNOS = {
    cabra: {
      ab: [`
O.........O
Uo.......oU
.Uo.....oU.`, -1, -2],
      de: [`
..O..
.Uo..
..Uo.
...U.`, 2, -3],
      ar: [`
O.........O
Uo.......oU
.Uo.....oU.`, -1, -2],
    },
    pez: {
      ab: [`
..o..
.oUo.
.oUo.`, 4, -2],
      de: [`
.o....
oUo...
.oUoo.
..oUUo`, 3, -3],
      ar: [`
..o..
.oUo.
.oUo.`, 4, -2],
    },
  };
  function adornar(L, v, x, y, pal, tipo) {
    const a = ADORNOS[tipo] && ADORNOS[tipo][v];
    if (a) poner(L, a[0], pal, x + a[1], y + a[2]);
  }
  // capucha de hojas: unas puntas de hoja claras por el borde
  function hojas(L, v, x, y, pal) {
    const pts = v === 'de' ? [[1, 2], [0, 5], [2, 8], [4, 0]] : [[1, 4], [11, 4], [3, 1], [9, 1], [0, 7], [12, 7]];
    for (const [i, j] of pts) { L.px(x + i, y + j, pal.C); L.px(x + i + 1, y + j, pal.c); }
  }
  // los ojos (ámbar) brillan: van también a la capa de luz
  function ojos(luz, v, x, y, pal) {
    const pts = v === 'ab' ? [[4, 6], [8, 6]] : [[5, 5]];
    for (const [i, j] of pts) luz.px(x + i, y + j, pal.y);
  }
  // el arco del golpe, como en Core Keeper: una media luna clara que deja el arma (dos tonos, punteada al final)
  function estela(L, [cx, cy, r, a0, a1], pal) {
    const n = Math.ceil(Math.abs(a1 - a0) * r * 1.6);
    for (let i = 0; i <= n; i++) {
      const t = i / n, a = a0 + (a1 - a0) * t, g = 1.6 * Math.sin(t * Math.PI);
      for (let d = -g; d <= g; d += 0.5) {
        const x = Math.round(cx + Math.cos(a) * (r + d)), y = Math.round(cy + Math.sin(a) * (r + d));
        if (L.get(x, y)) continue;
        if (t > 0.8 && (x + y) % 2) continue;
        L.px(x, y, d > 0 ? pal.U : pal.u);
      }
    }
  }
  function tumbar(L) {
    const c = L.d.slice(); L.d.fill(null);
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) { const v = c[y * W + x]; if (!v) continue; const nx = OX + (OY - y) - 8, ny = OY - (x - OX) - 2; if (nx >= 0 && ny >= 0 && nx < W && ny < H) L.d[ny * W + nx] = v; }
    let fondo = -1; for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) if (L.d[y * W + x]) fondo = y;
    const baja = OY - fondo; if (baja) { const d = L.d.slice(); L.d.fill(null); for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) { const yy = y + baja; if (yy >= 0 && yy < H) L.d[yy * W + x] = d[y * W + x]; } }
  }
  // sale del suelo: solo se ve lo que queda por encima de la línea del suelo
  function hundir(L, n) { for (let y = OY - n + 1; y < H; y++) for (let x = 0; x < W; x++) L.d[y * W + x] = null; for (let y = H - 1; y >= 0; y--) for (let x = 0; x < W; x++) { const yy = y + n; L.d[y * W + x] = yy < H && yy <= OY ? L.d[yy * W + x] : L.d[y * W + x]; } }

  // ---------------------------------------------------------------- animaciones del gruto
  // Lanza: la mano de delante agarra el asta; de frente y de espaldas va de pie a un lado. Atacar (como el caveling:
  // 0,35 s preparando, golpe con estela): se echa atrás con la lanza recogida y la clava hacia delante.
  function cuadrosGruto(o) {
    const lanza = o.lanza !== false;
    const A = {
      // de lado la lanza va baja y hacia delante (en guardia, como el lancero de Core Keeper); de frente y de
      // espaldas, de pie junto a la mano
      q: (v, f) => Object.assign({}, QUIETO[f], lanza ? { arma: v === 'de' ? [1, 14, 15, 13] : v === 'ab' ? [12, 17, 12, 1] : [1, 17, 1, 1] } : {}),
      a: (v, f) => { const k = Object.assign({}, (v === 'de' ? ANDAR.lado : ANDAR.frente)[f]); const b = (k.b || [0, 0])[0], bI = (k.bD || [0, 0])[1]; if (lanza) k.arma = v === 'de' ? [1 + b, 14, 15 + b, 13] : v === 'ab' ? [12, 17 + bI, 12, 1 + bI] : [1, 17 - bI, 1, 1 - bI]; return k; },
      // preparar (un cuadro que se queda 0,3 s, como el lancero de Core Keeper): agachado y echado atrás
      p: (v, f) => {
        const e = 2;
        if (v === 'de') return { c: [-e, e], t: [-e, 0], b: [-e - 1, 0], ba: [-1, 0], pd: [1, 0, 0], pa: [-1, 0, 0], arma: lanza ? [-2 - e, 13, 10 - e, 12] : null };
        if (v === 'ab') return { c: [0, e], cuerpo: 1, bI: [0, -1], bD: [0, -2 - e], arma: lanza ? [12, 13 - e * 2, 12, -3 - e * 2] : null };
        return { c: [0, e], cuerpo: 1, bI: [0, -2 - e], arma: lanza ? [1, 13 - e * 2, 1, -3 - e * 2] : null };
      },
      // golpe (4 cuadros: el que pincha con la estela, se queda, y vuelve en dos)
      g: (v, f) => {
        if (v === 'de') return [
          { c: [2, 0], t: [2, 0], b: [4, 0], pd: [2, 0, 0], pa: [-2, 0, 0], ojos: 'c', arma: lanza ? [6, 12, 22, 12] : null, estela: [8, 12, 12, -0.9, 0.5] },
          { c: [2, 0], t: [2, 0], b: [4, 0], pd: [2, 0, 0], pa: [-2, 0, 0], arma: lanza ? [5, 12, 21, 12] : null, estela: [8, 12, 12, -0.3, 0.5] },
          { c: [1, 0], t: [1, 0], b: [2, 0], pd: [1, 0, 0], arma: lanza ? [4, 13, 18, 12] : null },
          { c: [0, 0], b: [1, 0], arma: lanza ? [2, 14, 16, 13] : null },
        ][f];
        if (v === 'ab') return [
          { c: [0, 1], cuerpo: 1, bD: [-1, 2], ojos: 'c', arma: lanza ? [12, 9, 10, 25] : null, estela: [7, 16, 9, 0.15, 2.95] },
          { c: [0, 1], cuerpo: 1, bD: [-1, 2], arma: lanza ? [12, 9, 10, 24] : null, estela: [7, 16, 9, 0.9, 2.95] },
          { c: [0, 0], bD: [0, 1], arma: lanza ? [13, 5, 12, 20] : null },
          { c: [0, 0], arma: lanza ? [12, 17, 12, 1] : null },
        ][f];
        return [
          { c: [0, -1], bI: [1, -3], armaDelante: false, arma: lanza ? [2, 13, 4, -6] : null, estela: [7, 3, 9, -2.95, -0.15] },
          { c: [0, -1], bI: [1, -3], arma: lanza ? [2, 13, 4, -5] : null, estela: [7, 3, 9, -2.95, -0.9] },
          { c: [0, 0], bI: [1, -1], arma: lanza ? [1, 15, 2, -1] : null },
          { c: [0, 0], arma: lanza ? [1, 17, 1, 1] : null },
        ][f];
      },
      r: (v, f) => (f === 0 ? { c: [v === 'de' ? -1 : 0, -1], t: [v === 'de' ? -1 : 0, 0], ojos: 'c', b: [-1, -1], bI: [0, -1], bD: [0, -1] } : { c: [0, 0] }),
      m: (v, f) => [{ c: [0, 1], t: [0, 1], cuerpo: 0, ojos: 'c' }, { c: [0, 3], t: [0, 2], cuerpo: 0, ojos: 'c', pI: [0, 0, 1], pD: [0, 0, 1], pd: [0, 0, 1] }, { tumbado: true, ojos: 'c' }, { tumbado: true, ojos: 'c' }][f],
      n: (v, f) => Object.assign({}, QUIETO[0], { hundido: [14, 9, 4, 0][f] }),
    };
    return A;
  }
  // Los que atacan de lejos: el báculo (chamán), el arco (cazador) y las semillas (sembrador). Mismo cuerpo, otras
  // poses de preparar y de golpe, y los cuadros y tiempos de su caveling de Core Keeper.
  function cuadrosDistancia(o) {
    const A = cuadrosGruto(Object.assign({}, o, { lanza: false }));
    const T = o.tipo;
    // dónde va el arma quieto y andando (el asta de pie en la mano de delante)
    const pose = (v, k) => {
      const b = (k.b || [0, 0])[0], bD = (k.bD || [0, 0])[1], bI = (k.bI || [0, 0])[1];
      if (T === 'baculo') return v === 'de' ? [7 + b, 16, 7 + b, 1] : v === 'ab' ? [12, 17 + bD, 12, 2 + bD] : [1, 17 - bI, 1, 2 - bI];
      if (T === 'arco') return v === 'de' ? [9 + b, 9, 9 + b, 17] : v === 'ab' ? [12, 9 + bD, 12, 17 + bD] : [1, 9 - bI, 1, 17 - bI];
      return null;
    };
    const q = A.q, a = A.a;
    A.q = (v, f) => { const k = q(v, f); k.arma = pose(v, k); return k; };
    A.a = (v, f) => { const k = a(v, f); k.arma = pose(v, k); return k; };
    if (T === 'baculo') {
      // chamán: 3 cuadros subiendo el báculo por encima de la cabeza y 5 lanzando hacia delante
      A.p = (v, f) => v === 'de' ? { b: [-1, -2 - f], c: [-1, 0], arma: [6, 13 - f * 2, 6, -2 - f * 2], brillo: f } : v === 'ab' ? { bD: [0, -2 - f], arma: [12, 13 - f * 2, 12, -3 - f * 2], brillo: f } : { bI: [0, -2 - f], arma: [1, 13 - f * 2, 1, -3 - f * 2], brillo: f };
      A.g = (v, f) => {
        const lanza = f < 2;
        if (v === 'de') return { c: [1, 0], t: [1, 0], b: [2, -1], arma: [7, 13, 14, 2], brillo: lanza ? 3 : 0, ojos: f === 0 ? 'c' : null };
        if (v === 'ab') return { c: [0, 1], cuerpo: 1, bD: [-1, 0], arma: [12, 8, 11, 22], brillo: lanza ? 3 : 0 };
        return { bI: [1, -3], arma: [2, 14, 3, -6], brillo: lanza ? 3 : 0 };
      };
    } else if (T === 'arco') {
      // cazador: un cuadro tensando (se queda 0,5 s) y dos soltando
      A.p = (v) => v === 'de' ? { b: [2, -2], arma: [11, 6, 11, 15], tensa: true } : v === 'ab' ? { bD: [-1, -1], arma: [12, 7, 12, 16], tensa: true } : { bI: [1, -1], arma: [1, 7, 1, 16], tensa: true };
      A.g = (v, f) => v === 'de' ? { b: [2, -2], arma: [11, 6, 11, 15], suelta: f === 0 } : v === 'ab' ? { bD: [-1, -1], arma: [12, 7, 12, 16], suelta: f === 0 } : { bI: [1, -1], arma: [1, 7, 1, 16], suelta: f === 0 };
    } else {
      // sembrador: el brazo atrás con la semilla que brilla y la lanza
      A.p = (v, f) => v === 'de' ? { b: [-3, -3], c: [-1, 0], semilla: [3, 9] } : v === 'ab' ? { bD: [0, -4], semilla: [11, 7] } : { bI: [0, -4], semilla: [2, 7] };
      A.g = (v, f) => v === 'de' ? { c: [1, 0], t: [1, 0], b: [3, -1], estela: f < 2 ? [10, 10, 6, -1.6, 0.2] : null } : v === 'ab' ? { bD: [-1, 2], estela: f < 2 ? [7, 16, 7, 0.4, 2.7] : null } : { bI: [1, -3], estela: f < 2 ? [7, 3, 7, -2.7, -0.4] : null };
    }
    return A;
  }
  const ANIMS = { q: { n: 6, bucle: true }, a: { n: 6, bucle: true }, p: { n: 1 }, g: { n: 4 }, r: { n: 2 }, m: { n: 4 }, n: { n: 4 } };
  // los tiempos del lancero de Core Keeper (cavelingSpearman): quieto 6 a 10 fps, anda 6 a 12 fps, ataca 5 a 20 fps
  // con pausas [5, 0, 3, 0, 0] (la carga es el primero; aquí la carga es «p» y el resto «g»)
  const TIEMPOS = { q: { fps: 10 }, a: { fps: 12 }, p: { fps: 20, pausas: [5] }, g: { fps: 20, pausas: [0, 3, 0, 0] } };

  // la lanza: asta de madera y punta de hueso (de lado, la punta en el extremo de delante)
  function armaLanza(L, luz, pal, [x0, y0, x1, y1]) {
    linea(L, [x0, y0], [x1, y1], pal.w);
    const dx = Math.sign(x1 - x0), dy = Math.sign(y1 - y0);
    // la punta: 3 px en la dirección del asta, con el filo claro
    for (let i = 1; i <= 3; i++) L.px(x1 + dx * i, y1 + dy * i, i === 3 ? pal.u : pal.U);
    if (dx && !dy) { L.px(x1 + dx, y1 - 1, pal.o); L.px(x1 + dx, y1 + 1, pal.o); }
    else if (dy && !dx) { L.px(x1 - 1, y1 + dy, pal.o); L.px(x1 + 1, y1 + dy, pal.o); }
    // el atado de cuero bajo la punta
    L.px(x1, y1, pal.l);
  }

  // el arpón: como la lanza pero con punta de hierro y lengüeta
  function armaArpon(L, luz, pal, [x0, y0, x1, y1]) {
    linea(L, [x0, y0], [x1, y1], pal.w);
    const dx = Math.sign(x1 - x0), dy = Math.sign(y1 - y0);
    for (let i = 1; i <= 3; i++) L.px(x1 + dx * i, y1 + dy * i, i === 3 ? pal.h : pal.H);
    if (dx && !dy) { L.px(x1 + dx * 2, y1 - 1, pal.h); L.px(x1 + dx, y1 + 1, pal.h); } else if (dy && !dx) { L.px(x1 - 1, y1 + dy * 2, pal.h); L.px(x1 + 1, y1 + dy, pal.h); }
    L.px(x1, y1, pal.l);
  }
  // el báculo: asta y un orbe de Podre arriba que brilla (más al conjurar)
  function armaBaculo(L, luz, pal, [x0, y0, x1, y1], k) {
    linea(L, [x0, y0], [x1, y1], pal.w);
    const cx = x1, cy = y1 - 1, br = k.brillo || 0;
    for (const [i, j, c] of [[0, -1, 'Q1'], [-1, 0, 'Q1'], [0, 0, 'Q2'], [1, 0, 'Q0'], [0, 1, 'Q0'], [-1, -1, 'Q2']]) { L.px(cx + i, cy + j, pal[c]); luz.px(cx + i, cy + j, pal[c]); }
    L.px(cx, cy, pal.Q3); luz.px(cx, cy, pal.Q3);
    if (br >= 2) for (const [i, j] of [[0, -2], [2, 0], [0, 2], [-2, 0], [1, -2], [-2, 1], [2, 1], [-1, 2]]) if ((i + j + br) % 2 === 0) { L.px(cx + i, cy + j, pal.Q2); luz.px(cx + i, cy + j, pal.Q2); }
  }
  // el arco: un arco de hueso con la cuerda (tensa o suelta) y la flecha cuando tensa
  function armaArco(L, luz, pal, [x0, y0, x1, y1], k, v) {
    const n = y1 - y0, dir = v === 'ar' ? -1 : 1, tensa = k.tensa;
    for (let i = 0; i <= n; i++) { const t = (i / n) * 2 - 1, x = x0 + Math.round((1 - t * t) * 2) * dir; L.px(x, y0 + i, Math.abs(t) > 0.8 ? pal.o : pal.u); }
    const cx = tensa ? x0 - 3 * dir : x0, cy = (y0 + y1) / 2;
    linea(L, [x0, y0], [cx, cy], pal.o); linea(L, [cx, cy], [x0, y1], pal.o);
    if (tensa) { linea(L, [cx, cy], [x0 + 5 * dir, cy], pal.w); L.px(x0 + 6 * dir, cy, pal.U); }
    if (k.suelta) { L.px(x0 + 8 * dir, cy, pal.U); L.px(x0 + 10 * dir, cy, pal.u); L.px(x0 + 12 * dir, cy, pal.u); }
  }
  // Una variante de gruto como ficha de CRI (se hornea en las tres vistas).
  function gruto(o) {
    const C = o.tipo && o.tipo !== 'lanza' && o.tipo !== 'arpon' ? cuadrosDistancia(o) : cuadrosGruto(o);
    const dibuja = (anim, f, v) => {
      const k = C[anim](v, f);
      const r = montar(v, k, o);
      if (k.semilla) { const X = OX - 7, Y = OY - 17; for (const [i, j, c] of [[0, 0, 'Q2'], [1, 0, 'Q3'], [0, 1, 'Q2'], [1, 1, 'Q1']]) { r.color.px(X + k.semilla[0] + i, Y + k.semilla[1] + j, o.pal[c]); r.luz.px(X + k.semilla[0] + i, Y + k.semilla[1] + j, o.pal[c]); } }
      return r;
    };
    return {
      manual: dibuja, anims: o.anims || ANIMS, tiempos: o.tiempos || TIEMPOS, W, H, ox: OX, oy: OY, px: 1, yaw: 0, zancada: 14, fpsQuieta: 10,
    };
  }
  const paleta = (o) => Object.assign({
    K: '#1c120c', k: '#3a2618', c: '#5c3c24', C: '#7e5634',
    O: '#4a3e2c', o: '#8e7c5c', u: '#c8b890', U: '#efe4c4',
    E: '#140c10', y: '#ffb23a', Y: '#fff0a0',
    P: '#1a2618', p: '#33462c', s: '#52683e', S: '#748c52',
    L: '#2a160c', l: '#5a3620', m: '#7e5232',
    W: '#2a1408', w: '#6a4224',
  }, o);

  // las hojas (capucha del sembrador y del cazador), el metal del arpón y el orbe de Podre del chamán
  const HOJAS = { K: '#0c1a0e', k: '#1e3a1c', c: '#34602a', C: '#5a8c3a' };
  const METAL = { H: '#5a606c', h: '#c0c6d0' };
  const ORBE = { Q0: '#5a0a40', Q1: '#b0207a', Q2: '#ff3cb4', Q3: '#ffd0ee' };
  const FICHAS = {
    // como el lancero de Core Keeper: quieto 6, anda 6, ataca 5 (carga 0,3 s)
    gruto_lancero: gruto({ pal: paleta({}), arma: armaLanza, ojosLuz: true }),
    gruto_fango: gruto({ tipo: 'arpon', pal: paleta(Object.assign({ K: '#0c100a', k: '#1e281a', c: '#34422a', C: '#4e5e38', P: '#14160c', p: '#2e3320', s: '#4a5232', S: '#6c7446' }, METAL)), arma: armaArpon, adorno: 'pez', ojosLuz: true }),
    // como el chamán: quieto 6, anda 6, conjura 8 a 15 fps (3 preparando con pausas 1, 1, 4 y 5 lanzando)
    gruto_chaman: gruto({ tipo: 'baculo', pal: paleta(Object.assign({ P: '#130e1a', p: '#2e2238', s: '#4a3a58', S: '#6c5a7c' }, ORBE)), arma: armaBaculo, adorno: 'cabra', ojosLuz: true,
      anims: { q: { n: 6, bucle: true }, a: { n: 6, bucle: true }, p: { n: 3 }, g: { n: 5 }, r: { n: 2 }, m: { n: 4 }, n: { n: 4 } },
      tiempos: { q: { fps: 10 }, a: { fps: 12 }, p: { fps: 15, pausas: [1, 1, 4] }, g: { fps: 15 } } }),
    // como el sembrador: quieto 5 (pausas 1, 0, 0, 1, 0), anda 6, lanza 6 a 20 fps (2 preparando, 4 lanzando)
    gruto_jardinero: gruto({ tipo: 'semillas', pal: paleta(Object.assign({ P: '#121a10', p: '#26381e', s: '#3e5a2c', S: '#5e7c3c' }, HOJAS, ORBE)), hojas: true, bolsa: true, ojosLuz: true,
      anims: { q: { n: 5, bucle: true }, a: { n: 6, bucle: true }, p: { n: 2 }, g: { n: 4 }, r: { n: 2 }, m: { n: 4 }, n: { n: 4 } },
      tiempos: { q: { fps: 10, pausas: [1, 0, 0, 1, 0] }, a: { fps: 12 }, p: { fps: 20, pausas: [1, 5] }, g: { fps: 20, pausas: [0, 0, 3, 1] } } }),
    // como el cazador: quieto 5, anda 6, dispara 3 a 20 fps (tensa 0,5 s y suelta)
    gruto_cazador: gruto({ tipo: 'arco', pal: paleta(Object.assign({ P: '#121610', p: '#283222', s: '#404e34', S: '#5c6c48' }, HOJAS)), arma: armaArco, hojas: true, ojosLuz: true,
      anims: { q: { n: 5, bucle: true }, a: { n: 6, bucle: true }, p: { n: 1 }, g: { n: 2 }, r: { n: 2 }, m: { n: 4 }, n: { n: 4 } },
      tiempos: { q: { fps: 10, pausas: [1, 0, 0, 0, 0] }, a: { fps: 12 }, p: { fps: 20, pausas: [9] }, g: { fps: 20, pausas: [0, 3] } } }),
  };
  function registrar() { for (const [t, d] of Object.entries(FICHAS)) CRI.DEF[t] = Object.assign({ vistas: true }, d); }
  registrar();
  return { montar, gruto, paleta, FICHAS, GRUTO, linea, poner, W, H, OX, OY };
})();
