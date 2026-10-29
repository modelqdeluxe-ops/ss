'use strict';
// Las criaturas dibujadas a mano (mano.js, ARTE.md): a la escala de Core Keeper (las pequeñas de 12–16 px, los
// grutos de 13×18, los brutos de 30–40), 3/4 de frente y mirando un poco a la derecha (el juego las voltea), con
// la paleta de Ásor y sin contorno negro. Los ojos y la Podre van también en la capa _luz (brillan en lo oscuro).
// Sustituyen a los modelos horneados del mismo nombre.

// La paleta de Ásor: todos los dibujos a mano salen de aquí (como Core Keeper, que pinta todo el juego con unos
// 64 colores: así todo encaja). Rampas de oscuro a claro, con giro de tono (sombras frías, luces cálidas).
const ASOR = {
  negro: ['#0b0a10', '#16141f', '#23202e'],
  piedra: ['#2e3140', '#474b5c', '#6a6f80', '#9aa0ad', '#c9cdd4'],
  madera: ['#2a1710', '#4a2a18', '#6e4228', '#9a6236', '#c48e52', '#e8bf80'],
  hueso: ['#5a4c36', '#8c7a58', '#bfae86', '#e8dcb8', '#fffbea'],
  carne: ['#5e2a28', '#9a4a3c', '#d2836a', '#f2b890', '#ffe0c4'],
  sangre: ['#3a0a14', '#6a1422', '#a8202e', '#dc3a3c', '#ff7a62'],
  fuego: ['#7a2a08', '#c4520c', '#f08a1a', '#ffc040', '#fff0a0'],
  musgo: ['#14261a', '#24402a', '#3e6638', '#649446', '#9ac45a', '#d4ea8a'],
  bilis: ['#4a5a10', '#7a8a1a', '#a8b82a', '#d4e050', '#f4fca0'],
  cristal: ['#0c3a44', '#14707a', '#22b0b4', '#6ee8e0', '#d0fff6'],
  azul: ['#141e44', '#22357a', '#3a5cc0', '#6a92e8'],
  morado: ['#1e0e26', '#3a1a44', '#5e2a68', '#8a3e8e', '#b860b0'],
  podre: ['#ff3cb4', '#ffa0dc'],
  ojo: ['#ff3020', '#ffb070'],
  ambar: ['#ffb030', '#fff0a0'],
  blanco: '#ffffff',
};

const CRIATURAS_MANO = (() => {
  const A = ASOR;
  // Registra una criatura: cuadros { nombre: rejilla }, paleta (letra → color o null), luz: letras que brillan.
  // Un cuadro puede ser una rejilla o { g, cx }: cx es dónde está el centro del cuerpo (en px del dibujo), para que
  // la criatura no salte de sitio cuando un cuadro es más ancho (la estocada, el arco tensado).
  function criatura(cuadros, pal, luz = '') {
    for (const [n, c] of Object.entries(cuadros)) {
      const g = typeof c === 'string' ? c : c.g;
      const filas = MANO.rejilla(g), w = Math.max(...filas.map((f) => f.length)), h = filas.length;
      const L = new Lienzo(w, h);
      MANO.pintar(L, filas, pal);
      MANO.normales(L);
      for (const suf of ['', '_luz', '_B', '_sombra']) quitarDibujo(n + suf);
      def(n, L);
      META_SPR[n] = { dx: typeof c === 'string' || c.cx === undefined ? 0 : (w / 2 - c.cx) / 16, dz: 0 };
      if (luz) {
        const pl = {};
        for (const k of Object.keys(pal)) pl[k] = luz.includes(k) ? pal[k] : null;
        const Lz = MANO.pintar(new Lienzo(w, h), filas, pl);
        if (Lz.d.some((c) => c)) def(n + '_luz', Lz);
      }
    }
  }

  // ---------------------------------------------------------------- las babas
  // Una baba es una gota medio transparente: borde de arriba claro, borde de abajo oscuro, el brillo en cruz
  // arriba a la izquierda y por dentro, abajo, lo oscuro (lo que se ha tragado). Tres cuadros: 0 y 1 tiembla, 2 en
  // el aire (estirada). Letras: a borde claro, l luz, c cuerpo, s sombra, f borde oscuro, g dentro, d brillo.
  const BABA = {
    0: `
....aaaaaaaa....
..aallllllllca..
.alldllllllllcc.
alldddlllllllccs
allldllllllllccs
clllllllllllccsf
cllcggggggglccsf
fcgggggggggggcsf
fcgggggggggggcff
.fcgggggggggccf.
..ffcggggggcff..
....ffffffff....`,
    1: `
.....aaaaaaa......
...aallllllllca...
..alldlllllllllcc.
.alldddllllllllccs
allldllllllllllccs
cllllllllllllllcsf
cllcgggggggggglcsf
fcggggggggggggggcf
.fcgggggggggggggcf
..fffcggggggggcff.
.....ffffffffff...`,
    2: `
....aaaaaa....
..aallllllca..
.alldlllllllc.
alldddllllllcs
allldlllllllcs
cllllllllllccs
cllllllllllccs
cllcgggggglccs
fcggggggggggcf
fcggggggggggcf
.fcgggggggggf.
.fcggggggggcf.
..ffcggggcff..
....ffffff....`,
  };
  // lo que se ve dentro de cada baba (se pinta encima, en la zona oscura)
  function babaCon(base, extra) {
    const filas = MANO.rejilla(base).map((f) => f.replace(/ /g, 'l').split(''));
    for (const [x, y, k] of extra) if (filas[y] && filas[y][x] && filas[y][x] !== '.') filas[y][x] = k;
    return filas.map((f) => f.join('')).join('\n');
  }
  // la Baba carroñera: amarillenta, con un cráneo y huesos dentro y dos ojillos rojos flotando
  const craneo = (dx, dy) => [[dx, dy, 'H'], [dx + 1, dy, 'h'], [dx + 2, dy, 'h'], [dx + 3, dy, 'H'], [dx, dy + 1, 'h'], [dx + 1, dy + 1, 'k'], [dx + 2, dy + 1, 'h'], [dx + 3, dy + 1, 'k'], [dx, dy + 2, 'H'], [dx + 1, dy + 2, 'h'], [dx + 2, dy + 2, 'H'], [dx + 3, dy + 2, 'h'], [dx + 1, dy + 3, 'H'], [dx + 2, dy + 3, 'k'], [dx + 3, dy + 3, 'H']];
  const hueso = (dx, dy) => [[dx, dy, 'h'], [dx + 1, dy + 1, 'H'], [dx + 2, dy + 1, 'h'], [dx + 3, dy + 1, 'H'], [dx + 4, dy, 'h']];
  const babaCarr = { a: A.bilis[4], l: A.bilis[3], c: A.bilis[2], s: A.bilis[1], f: A.bilis[0], g: '#3a3a10', d: A.blanco, h: A.hueso[3], H: A.hueso[1], k: '#2a2010', e: A.ojo[0] };
  // la Baba sangrante: roja, con trozos de carne y vísceras
  const babaSangre = { a: A.sangre[4], l: A.sangre[3], c: A.sangre[2], s: A.sangre[1], f: A.sangre[0], g: '#2a0610', d: '#ffe0d0', h: A.carne[2], H: A.carne[1], k: A.sangre[0], e: A.ojo[1] };
  // la Pústula: negra y morada, llena de bultos que brillan en magenta
  const babaVeneno = { a: A.morado[4], l: A.morado[3], c: A.morado[2], s: A.morado[1], f: A.morado[0], g: '#12081a', d: '#ffd8f4', h: A.podre[0], H: '#a8207a', k: A.podre[1], e: A.podre[0] };

  function babas() {
    const dentro = {
      babosa: [[...craneo(6, 6), ...hueso(2, 9), [11, 7, 'e'], [4, 7, 'e']], [...craneo(7, 6), ...hueso(3, 8), [12, 6, 'e'], [5, 6, 'e']], [...craneo(5, 8), ...hueso(2, 11), [10, 8, 'e'], [3, 9, 'e']]],
      babosa_roja: [[[3, 7, 'h'], [4, 7, 'H'], [9, 8, 'h'], [10, 8, 'h'], [10, 9, 'H'], [6, 9, 'H'], [12, 7, 'e'], [5, 9, 'e']], [[4, 7, 'h'], [5, 7, 'H'], [10, 7, 'h'], [11, 7, 'h'], [11, 8, 'H'], [7, 8, 'H'], [13, 7, 'e'], [6, 8, 'e']], [[3, 8, 'h'], [4, 8, 'H'], [8, 9, 'h'], [9, 9, 'h'], [9, 10, 'H'], [5, 11, 'H'], [10, 8, 'e'], [4, 10, 'e']]],
      babosa_veneno: [[[2, 3, 'h'], [3, 3, 'k'], [2, 4, 'H'], [10, 2, 'h'], [11, 2, 'h'], [11, 3, 'H'], [12, 5, 'h'], [6, 8, 'h'], [7, 8, 'k'], [7, 9, 'H'], [11, 8, 'e'], [3, 8, 'h']], [[3, 3, 'h'], [4, 3, 'k'], [3, 4, 'H'], [11, 2, 'h'], [12, 2, 'h'], [12, 3, 'H'], [14, 5, 'h'], [7, 7, 'h'], [8, 7, 'k'], [8, 8, 'H'], [13, 7, 'e'], [4, 7, 'h']], [[2, 4, 'h'], [3, 4, 'k'], [2, 5, 'H'], [9, 2, 'h'], [10, 2, 'h'], [10, 3, 'H'], [11, 6, 'h'], [5, 9, 'h'], [6, 9, 'k'], [6, 10, 'H'], [9, 10, 'e'], [3, 10, 'h']]],
    };
    for (const [tipo, pal] of [['babosa', babaCarr], ['babosa_roja', babaSangre], ['babosa_veneno', babaVeneno]]) {
      const cuadros = {};
      for (let f = 0; f < 3; f++) cuadros[`${tipo}_${f}`] = babaCon(BABA[f], dentro[tipo][f]);
      criatura(cuadros, pal, tipo === 'babosa_veneno' ? 'ehk' : 'e');
    }
  }

  // ---------------------------------------------------------------- los grutos
  // Veladores que bajaron a la oscuridad: encorvados, piel verde de musgo, la máscara de cráneo de un antepasado con
  // dos brasas ámbar por ojos, taparrabos y algún bulto de Podre. 3/4 de frente, mirando a la derecha.
  // Letras: H h j J hueso (de oscuro a claro) · k cuenca · e E ojos (brillan) · G g n N piel · M m w cuero
  // x X bulto de Podre · p poro (brilla) · s S astil · t T punta de hueso.
  const PAL_GRUTO = {
    H: A.hueso[0], h: A.hueso[1], j: A.hueso[2], J: A.hueso[3], k: A.negro[1], e: A.ambar[0], E: A.ambar[1],
    G: A.musgo[1], g: A.musgo[2], n: A.musgo[3], N: A.musgo[4], M: A.madera[1], m: A.madera[2], w: A.madera[3],
    x: A.morado[1], X: A.morado[2], p: A.podre[0], s: A.madera[2], S: A.madera[3], t: A.hueso[2], T: A.hueso[3],
  };
  // Junta capas [rejilla, x, y] en una rejilla de w×h (lo de después tapa a lo de antes).
  function componer(w, h, capas) {
    const out = Array.from({ length: h }, () => Array(w).fill('.'));
    for (const [g, x0, y0] of capas) {
      if (!g) continue;
      const filas = MANO.rejilla(g);
      for (let y = 0; y < filas.length; y++) for (let x = 0; x < filas[y].length; x++) {
        const k = filas[y][x], X = x0 + x, Y = y0 + y;
        if (k !== '.' && X >= 0 && Y >= 0 && X < w && Y < h) out[Y][X] = k;
      }
    }
    return out.map((f) => f.join('')).join('\n');
  }
  // el gruto sin arma: capucha, máscara, cuerpo y taparrabos (14×15); las piernas aparte (3 filas)
  const GRUTO_CUERPO = `
..MMMMM.......
.MmHHHHmM.....
MmHjJJjHmM....
MHjJJJJJhM....
MhJJJJJJjhM...
HkkjjjkkjhM...
hkejjjkejhM...
MhjjhjjjhMM...
MMHkJkJkHM....
XGnjhjjhnG....
pXGnNNnnngG...
xGnNNnnngGGn..
GgnnnnggGG....
.GgMmwmmMG....
..MmwwmmM.....`;
  const GRUTO_PIERNAS = [`
..gnG..gnG
.gG.....gG
GNG....GNG`, `
...gnGgnG.
...gG.gG..
..GNGGNG..`];
  // la lanza de hueso: de pie, levantada para atacar y lanzando la estocada
  const LANZA_PIE = `
.T.
JTj
jJh
.j.
.h.
.s.
.S.
.s.
.s.
.s.
.s.
.S.
.s.
.s.
.s.
.s.
.S.
.s.`;
  const LANZA_HORIZ = `
.......................jJ.
sssSssssSssssSssssSsjJJTT
.......................hj.`;
  const cuerpo = (dx, dy, g = GRUTO_CUERPO) => [g, 1 + dx, 4 + dy];
  const piernas = (k, dx, dy) => [GRUTO_PIERNAS[k], 2 + dx, 19 + dy];
  const MANO_G = 'Nn';
  function lancero(lanza = LANZA_PIE, horiz = LANZA_HORIZ, g = GRUTO_CUERPO) {
    return {
      0: { g: componer(20, 22, [[lanza, 14, 0], cuerpo(0, 0, g), piernas(0, 0, 0), [MANO_G, 13, 14]]), cx: 7 },
      1: { g: componer(20, 22, [[lanza, 14, 1], cuerpo(0, 1, g), piernas(1, 0, 0), [MANO_G, 13, 15]]), cx: 7 },
      // se carga hacia atrás: la lanza sube y el cuerpo se echa atrás
      2: { g: componer(20, 22, [[lanza, 12, -2], cuerpo(-1, 1, g), piernas(0, -1, 0), [MANO_G, 11, 12]]), cx: 6 },
      // estocada: la lanza horizontal hacia delante
      3: { g: componer(32, 22, [cuerpo(2, 1, g), piernas(1, 2, 0), [horiz, 7, 13], [MANO_G, 14, 14]]), cx: 9 },
    };
  }
  const nombrar = (tipo, cuadros) => Object.fromEntries(Object.entries(cuadros).map(([k, v]) => [`${tipo}_${k}`, v]));
  // el arpón del gruto del fango: la lanza con un garfio
  const ARPON_PIE = `
.T..
JTjh
jJ.h
.j..
.h..
.s..
.S..
.s..
.s..
.s..
.s..
.S..
.s..
.s..
.s..
.s..
.S..
.s..`;
  const ARPON_HORIZ = `
.....................h..jJ.
sssSssssSssssSssssSsj.jJJTT
.....................hhhj..`;
  // el arco del Acechador (el cuerpo de la cuerda a la izquierda) y la flecha
  const ARCO = `
jM...
j.m..
j..m.
j..m.
j..M.
j..m.
j.m..
jM...`;
  const ARCO_TENSO = `
.M...
.jm..
..jm.
...jM
..jm.
.jm..
.M...`;
  const FLECHA = `ssSsjJT`;
  // el báculo del vidente: un palo torcido con una gema de Podre arriba (brilla)
  const BACULO = `
.xp.
xPpx
.xX.
.h..
.s..
.S..
.s..
.s..
.s..
.S..
.s..
.s..
.s..
.S..
.s..
.s..
.s..`;
  // astas sobre la capucha del vidente
  const ASTAS = `
H.......H
hH..H..Hh
.jh.h.hj.
..jhhhj..`;
  // la bolsa de semillas del Sembrador y la semilla que lanza
  const BOLSA = `
.mM.
mwmM
mmMM
.MM.`;
  const SEMILLA = `
.nN
NnG
.G.`;
  // ---- el Gruto mole: el doble de grande, cráneo de buey con cuernos, joroba de Podre y maza de piedra
  const MOLE_CABEZA = `
H...........H
jH.........Hj
.jhHHHHHHHhj.
..HjJJJJJjH..
.HjJJJJJJJjH.
.hkkJJJJJkkh.
.hkeJJJJJkeh.
.HjjjhJhjjjH.
..HjkJkJkjH..
...HkHkHkH...`;
  const MOLE_TORSO = `
......xXXx.GGGGGG.......
....xXpXXpXGgnnngGG.....
...xXXxxXXGgnNNnnggG....
..xpXXxxXGgnNNNNnnggG...
..xxXXxGGgnNNNNNnnnggG..
...xxGGgnNNNNNnnnnnggGG.
....GggnNNNnnnnnnngggGG.
....GgnnnnnnnnnngggGGG..
....GgnnnnnnnngggGGGG...
.....GggggggggggGGGG....
.....GMMmmwwwmmMMGG.....
......MmwwmmmmmMM.......
.......MmmmmmmM.........`;
  const MOLE_BRAZO = `
.GgG
GgnG
GnnG
GnnG
GngG
GngG
GgnG
GnNG
NnNn
nNnN`;
  const MOLE_PIERNAS = [`
..GgnnG....GgnnG..
..GgnnG....GgnnG..
.GgnnNG...GgnnNG..
NnNnNN...NnNnNN...`, `
....GgnnG.GgnnG...
....GgnnG.GgnnG...
...GgnnNGGgnnNG...
..NnNnNNNnNnNN....`];
  // la maza: mango de cuero y una piedra atada (colgando, levantada y en el suelo)
  const MAZA_PIE = `
...wM....
...mM....
...mM....
...mM....
...mMw...
.qqQQQqq.
qQQQQQQQq
qQRrrrQQq
qQRRrrQQq
.qQRRRQq.
..qQQQq..`;
  const MAZA_ARRIBA = `
..qQQQq..
.qQRRRQq.
qQRRrrQQq
qQRrrrQQq
qQQQQQQQq
.qqQQQqq.
...MMm...
...mMw...
...mM....
...mM....
...wM....`;
  const MAZA_SUELO = `
.........qQQQQq..
mmmmmmmMqQRRRRQq.
wwmmmmmMqQRrrrQQq
.........qQQQQQq.`;
  function mole() {
    const pal = Object.assign({}, PAL_GRUTO, { q: A.piedra[0], Q: A.piedra[1], r: A.piedra[2], R: A.piedra[3] });
    const c = (k, dy, maza, mx, my, by) => componer(36, 33 - 4, [
      [MOLE_PIERNAS[k], 5, 25], [MOLE_BRAZO, 1, 16 + dy], [MOLE_TORSO, 2, 13 + dy], [maza, mx, my], [MOLE_BRAZO, 21, by + dy], [MOLE_CABEZA, 9, 4 + dy]]);
    criatura({
      gruto_bruto_0: { g: c(0, 0, MAZA_PIE, 22, 18, 15), cx: 14 },
      gruto_bruto_1: { g: c(1, 1, MAZA_PIE, 22, 18, 15), cx: 14 },
      // levanta la maza por encima de la cabeza
      gruto_bruto_2: { g: c(0, 0, MAZA_ARRIBA, 20, 0, 7), cx: 14 },
      // golpe: la maza contra el suelo, el cuerpo echado hacia delante
      gruto_bruto_3: { g: componer(44, 31, [[MOLE_PIERNAS[1], 6, 27], [MOLE_BRAZO, 3, 18], [MOLE_TORSO, 4, 15], [MOLE_BRAZO, 23, 19], [MAZA_SUELO, 25, 27], [MOLE_CABEZA, 12, 7]]), cx: 16 },
    }, pal, 'eEp');
  }
  function grutos() {
    mole();
    criatura(nombrar('gruto_lancero', lancero()), PAL_GRUTO, 'eEp');
    // el del fango: piel de barro, capucha de juncos, arpón
    criatura(nombrar('gruto_fango', lancero(ARPON_PIE, ARPON_HORIZ)), Object.assign({}, PAL_GRUTO, {
      G: '#2a1e14', g: '#4a3624', n: '#6e5634', N: '#8e7a4a', M: '#1c2a1a', m: '#34482a', w: '#56703a',
    }), 'eEp');
    // el Acechador: capa de musgo, arco
    const PAL_CAZ = Object.assign({}, PAL_GRUTO, { M: A.musgo[0], m: A.musgo[1], w: A.musgo[2] });
    criatura(nombrar('gruto_cazador', {
      0: { g: componer(20, 22, [[ARCO, 14, 9], cuerpo(0, 0), piernas(0, 0, 0), [MANO_G, 13, 13]]), cx: 7 },
      1: { g: componer(20, 22, [[ARCO, 14, 10], cuerpo(0, 1), piernas(1, 0, 0), [MANO_G, 13, 14]]), cx: 7 },
      // tensa el arco: la flecha puesta
      2: { g: componer(22, 22, [cuerpo(-1, 0), piernas(0, -1, 0), [ARCO_TENSO, 14, 10], [FLECHA, 12, 13], [MANO_G, 11, 13]]), cx: 6 },
      // suelta
      3: { g: componer(22, 22, [cuerpo(0, 0), piernas(1, 0, 0), [ARCO, 15, 9], [MANO_G, 14, 13]]), cx: 7 },
    }), PAL_CAZ, 'eEp');
    // el Sembrador: capucha de hojas, bolsa de semillas; lanza semillas que revientan
    const PAL_SEM = Object.assign({}, PAL_GRUTO, { M: A.bilis[0], m: A.bilis[1], w: A.bilis[2], G: A.carne[0], g: '#7a5a48', n: '#9a7a5e', N: '#bca07a' });
    criatura(nombrar('gruto_jardinero', {
      0: { g: componer(20, 22, [cuerpo(0, 0), piernas(0, 0, 0), [BOLSA, 10, 16]]), cx: 7 },
      1: { g: componer(20, 22, [cuerpo(0, 1), piernas(1, 0, 0), [BOLSA, 10, 17]]), cx: 7 },
      2: { g: componer(20, 22, [[SEMILLA, 0, 9], cuerpo(1, 1), piernas(0, 1, 0), [MANO_G, 0, 12]]), cx: 8 },
      3: { g: componer(22, 22, [cuerpo(0, 0), piernas(1, 0, 0), [MANO_G, 13, 13], [SEMILLA, 16, 11]]), cx: 7 },
    }), PAL_SEM, 'eEp');
    // el Vidente: túnica morada, astas sobre la capucha, báculo con una gema de Podre
    const PAL_VID = Object.assign({}, PAL_GRUTO, { M: A.morado[1], m: A.morado[2], w: A.morado[3], P: A.podre[1] });
    const vid = (bx, by, dy, k) => componer(20, 22, [[BACULO, 14 + bx, by], [ASTAS, 2, dy], cuerpo(0, dy), piernas(k, 0, 0), [MANO_G, 13 + bx, 12 + by + 1]]);
    criatura(nombrar('gruto_chaman', {
      0: { g: vid(0, 2, 0, 0), cx: 7 }, 1: { g: vid(0, 3, 1, 1), cx: 7 }, 2: { g: vid(-1, -1, 0, 0), cx: 7 }, 3: { g: vid(1, 0, 0, 0), cx: 7 },
    }), PAL_VID, 'eEpP');
  }

  // ---------------------------------------------------------------- gusanos
  // Gusano roedor: pálido, ciego y venoso, alzado, con la boca redonda llena de dientes. Dos cuadros (se mece).
  // Letras: o borde · H h j carne (oscuro → claro) · v vena · k boca · r garganta · T dientes · a saco (brilla)
  const GUSANO = [`
...oHHo...
..okkkko..
.okTkkTko.
.oTkrrkTo.
.HkTkkTkH.
..HhkkhH..
..jhhhhH..
..HjvhhH..
...jhvH...
..HjhhvH..
..jhhhhH..
.HjjvhhhH.
.jhhhvhhH.
HjjhhhhvhH
.HHHHHHHH.`, `
....oHHo..
...okkkko.
..okTkkTko
..oTkrrkTo
..HkTkkTkH
...HhkkhH.
...jhhhhH.
..HjvhhH..
..jhvhH...
..HjhhvH..
..jhhhhH..
.HjjvhhhH.
.jhhhvhhH.
HjjhhhhvhH
.HHHHHHHH.`];
  const GUSANO_GORDO = [`
......oHHHo.......
.....okkkkko......
....okTkTkTko.....
....oTkkrrkkTo....
....HkTkkkkTkH....
.....HhkTkThH.....
....HjhhhhhhhH....
...HjjhaahhhhaH...
..HjjhaAAahhhaAH..
..jjhhaAAahhhaAah.
.HjjhhhaahhvhhaH..
.jjhhvhhhhhhvhhhH.
Hjjhhhhaahhhhhhhh.
jjhhhhaAAahhvhhhhH
HjhhvhhaahhhhhhhhH
.HHHHHHHHHHHHHHHH.`, `
.......oHHHo......
......okkkkko.....
.....okTkTkTko....
.....oTkkrrkkTo...
.....HkTkkkkTkH...
......HhkTkThH....
....HjhhhhhhhH....
...HjjhaahhhhaH...
..HjjhaAAahhhaAH..
..jjhhaAAahhhaAah.
.HjjhhhaahhvhhaH..
.jjhhvhhhhhhvhhhH.
Hjjhhhhaahhhhhhhh.
jjhhhhaAAahhvhhhhH
HjhhvhhaahhhhhhhhH
.HHHHHHHHHHHHHHHH.`];
  function gusanos() {
    const palido = { o: '#4a3030', H: '#8a6a5a', h: '#c8a888', j: '#f0e2c8', v: '#8a4a6a', k: '#2a0a10', r: A.sangre[2], T: A.hueso[4], a: A.bilis[3], A: A.bilis[4] };
    criatura({ larva_0: GUSANO[0], larva_1: GUSANO[1] }, palido);
    criatura({ larva_grande_0: GUSANO_GORDO[0], larva_grande_1: GUSANO_GORDO[1] }, palido, 'aA');
    // el de Podre: ennegrecido, con bultos y poros que brillan
    const podrido = { o: A.negro[1], H: A.morado[1], h: A.morado[2], j: A.morado[3], v: A.podre[0], k: A.negro[0], r: A.podre[0], T: A.hueso[2], a: A.podre[0], A: A.podre[1] };
    criatura({ larva_moho_0: GUSANO[0], larva_moho_1: GUSANO[1] }, podrido, 'vr');
  }

  // ---------------------------------------------------------------- el Portador
  // Un minero muerto, encorvado; de la espalda le sale un hongo enorme que le tapa la cabeza (cuencas vacías debajo).
  // Letras: C c K l sombrero del hongo (oscuro → claro) · L motas · i láminas · P p q piel muerta · R r ropa · B botas
  const PORTADOR = {
    0: `
.....CCCCCC.......
...CCcKKKKcCC.....
..CcKlLlKKKcCC....
.CcKlLLlKKLKKcC...
.cKlllKKKLLKKKc...
CcKlLKKKKLKKKKcC..
cKKKKKKlKKKKKKKc..
CcccccccccccccC...
.iiiiiiiiiiiii....
..iqPkqPkqi.......
...pqpqqpq........
...RRrrrRR........
..RRrrrrrRR.......
..qRrrRrrRq.......
..pPRrrrRPp.......
...RRRRRRR........
...rR...rR........
...BB...BB........
..BBB..BBB........`,
  };
  function portador() {
    const pal = { C: A.carne[0], c: A.carne[1], K: A.carne[2], l: A.carne[3], L: A.hueso[3], i: A.carne[4], P: '#5e5a54', p: '#8a8478', q: '#aaa498', k: A.negro[0], R: A.piedra[0], r: A.piedra[1], B: A.madera[0] };
    const g = PORTADOR[0];
    // andar: el hongo se mece un píxel; preparar: se hincha (las esporas salen después)
    const filas = MANO.rejilla(g);
    const mece = filas.map((f, y) => (y < 9 ? '.' + f.slice(0, -1) : f)).join('\n');
    const hincha = componer(20, 20, [[g, 1, 1], ['iiiiiiiiiiiiiii', 1, 9]]);
    criatura({ seton_0: { g, cx: 7 }, seton_1: { g: mece, cx: 7 }, seton_2: { g: hincha, cx: 8 } }, pal);
  }

  // ---------------------------------------------------------------- el Sapo hueco
  // Flaco, con la espalda llena de bultos de Podre; salta y aplasta. 0 sentado, 1 aplastado, 2 en el aire.
  // Letras: o borde · G g n N piel (oscuro → claro) · e ojo (brilla) · x X bulto · p poro (brilla) · w vientre
  const SAPO = [`
....xXpX.xXx....
...xXpXXxXpXx...
..oGxXXxxXXxGo..
.oGgnnNNnnnggGo.
.Gnoennnnnneong.
oGgnnnnnnnnnngGo
GgnwwwwwwwwwnggG
GgnwwwwwwwwwnngG
.Ggnwwwwwwwwng..
oGG.GGgggGG.GGo.
NnG..........GnN`, `
.....xXpX.xXx.....
...xXpXXxxXXpXx...
..oGxXXxxxXXxXGo..
.oGgnnNNNNnnnnggGo
oGnoennnnnnnneongG
GgnwwwwwwwwwwwwnggG
GgnwwwwwwwwwwwwnngG
.GGgggggggggggggGG.
NnNG..........GNnN`, `
....xXpX.xXx....
...xXpXXxXpXx...
..oGxXXxxXXxGo..
.oGgnnNNnnnggGo.
.Gnoennnnnneong.
oGgnnnnnnnnnngGo
GgnwwwwwwwwwnggG
GgnwwwwwwwwwnngG
.Ggnwwwwwwwwng..
..Gnwwwwwwwng...
..GgnnggnnggG...
.Gg.........gG..
NG...........GN.`];
  function sapo() {
    const pal = { o: A.musgo[0], G: A.musgo[1], g: A.musgo[2], n: '#7a8a4a', N: '#a8b468', e: A.ojo[0], x: A.morado[0], X: A.morado[2], p: A.podre[0], w: '#c8c08a' };
    criatura({ sapo_hueco_0: SAPO[0], sapo_hueco_1: SAPO[1], sapo_hueco_2: SAPO[2] }, pal, 'ep');
  }

  // ---------------------------------------------------------------- la Fauce
  // Un tallo con una boca vertical de dos filas de dientes que babea, entre hojas. 0 escondida (solo las hojas),
  // 1 alzada, 2 abre la boca y se echa atrás, 3 muerde hacia delante.
  // Letras: G g n N tallo y boca · m labio · r carne · T dientes · b baba · L l hojas
  const FAUCE_HOJAS = `
..LL..GnG..LL.
.LNnL.GgG.LnNL
LNnngLGnGLgnnL
.LggGGGgGGGggL
..LLGGgggGGLL.`;
  const FAUCE_CABEZA = `
....GnnG....
...GnNNnG...
..GnmmmmnG..
..nmrTTrmn..
.GnTrrrrTnG.
.nmTrmmrTmn.
.nmrTrrTrmn.
.GnmTTTTmnG.
..GnmmmmnG..
...GnnnnG...`;
  const FAUCE_ABIERTA = `
...GnnnnG...
..GnNNNNnG..
.GnmTTTTmnG.
.nmTrrrrTmn.
GnTrrmmrrTnG
nmTrmmmmrTmn
nmTrmmmmrTmn
GnTrrmmrrTnG
.nmTrrrrTmn.
.GnmTTTTmnG.
..GnnnnnnG..`;
  const FAUCE_TALLO = `
.bGnG
.bGgG
..GnG
..GgG
..GnG
..GgG`;
  function fauce() {
    const pal = { G: A.musgo[1], g: A.musgo[2], n: A.musgo[3], N: A.musgo[4], m: A.sangre[1], r: A.sangre[2], T: A.hueso[3], b: A.bilis[4], L: A.musgo[2], l: A.musgo[3] };
    pal.L = A.musgo[1];
    criatura({
      planta_trampa_0: { g: componer(14, 5, [[FAUCE_HOJAS, 0, 0]]), cx: 7 },
      planta_trampa_1: { g: componer(14, 21, [[FAUCE_TALLO, 4, 10], [FAUCE_CABEZA, 1, 0], [FAUCE_HOJAS, 0, 16]]), cx: 7 },
      planta_trampa_2: { g: componer(15, 22, [[FAUCE_TALLO, 3, 11], [FAUCE_ABIERTA, 0, 0], [FAUCE_HOJAS, 1, 17]]), cx: 7 },
      planta_trampa_3: { g: componer(18, 19, [[FAUCE_TALLO, 5, 8], [FAUCE_CABEZA, 6, 0], [FAUCE_HOJAS, 1, 14]]), cx: 8 },
    }, pal);
  }

  // ---------------------------------------------------------------- el Brazo de la Podre
  // Un tentáculo negro con ojos a lo largo, saliendo de una pústula del suelo. 0 escondido, 1 y 2 se mece, 3 se alza.
  // Letras: o borde · x X carne negra · e ojo (brilla) · p poro (brilla)
  const PUSTULA = `
pXxxXxxp.
XpxXXxpXp
xXpXXpXXx
.xxxxxxx.`;
  const TENTACULO = [`
....xo...
...xXxo..
...xXeo..
..xXxxo..
..xXexo..
.xXxxo...
.xXexo...
.xXxxo...
..xXexo..
..xXxxo..
...xXexo.
...xXxxo.
...xXexo.
..xXXxxo.
..xXexo..
.xXXxxo..`, `
.....xo..
....xXxo.
....xXeo.
...xXxxo.
...xXexo.
..xXxxo..
..xXexo..
.xXxxo...
.xXexo...
.xXxxo...
..xXexo..
..xXxxo..
..xXexo..
..xXXxxo.
..xXexo..
.xXXxxo..`, `
..oxxo.....
.xXXXxo....
.xXexXxo...
..o.xXeo...
.....xXxo..
.....xXexo.
....xXxxo..
....xXexo..
...xXxxo...
...xXexo...
...xXxxo...
..xXexo....
..xXxxo....
..xXexo....
..xXXxxo...
..xXexo....
.xXXxxo....
.xXexo.....
.xXXxxo....`];
  function tentaculo() {
    const pal = { o: A.negro[0], x: A.negro[2], X: A.morado[1], e: A.ojo[0], p: A.podre[0] };
    criatura({
      tentaculo_moho_0: { g: componer(9, 4, [[PUSTULA, 0, 0]]), cx: 4 },
      tentaculo_moho_1: { g: componer(9, 20, [[TENTACULO[0], 0, 0], [PUSTULA, 0, 16]]), cx: 4 },
      tentaculo_moho_2: { g: componer(9, 20, [[TENTACULO[1], 0, 0], [PUSTULA, 0, 16]]), cx: 4 },
      tentaculo_moho_3: { g: componer(11, 23, [[TENTACULO[2], 0, 0], [PUSTULA, 1, 19]]), cx: 5 },
    }, pal, 'ep');
  }

  // ---------------------------------------------------------------- moscas y avispas
  // La Aguijonera: avispa grande con cara de calavera y alas venosas. 0 y 1 aletea, 2 se lanza (aguijón delante).
  // Letras: W w v alas (borde, ala, vena) · o borde · J cara de hueso · k cuenca · e ojo (brilla) · y Y rayas · s aguijón
  const AVISPA = [`
..WW......WW....
.WwwW....WwwW...
.WwvwW..WwvwW...
..WwwWooWwwW....
...WWoJJoWW.....
....oJkJkJo.....
....oJeJeJo.....
.....oJJJo......
....yYoooYy.....
...yYyYyYyYy....
...oYyYyYyYo....
....oyYyYyo.....
.....ooYoo......
.......s........`, `
................
................
................
...WWoooWW......
.WWwWoJJoWwWW...
WwvwwJkJkJwwvwW.
.WwwWoJeJeJWwwW.
.....oJJJo......
....yYoooYy.....
...yYyYyYyYy....
...oYyYyYyYo....
....oyYyYyo.....
.....ooYoo......
.......s........`, `
....WW...WW.......
...WwwW.WwwW......
...WwvwWwvwW......
....WwwoowwW......
.....oJJJJo.......
...yYoJkJkJo......
..yYyYoJeJeJo.....
.yYyYyYoJJJo......
.oYyYyYyooo.......
..oYyYyYo.........
...ooYoo..........
....s.............`];
  const MOSCA = [`
..ww.....ww...
.wvww...wwvw..
..wwwoooowww..
...oeeooeeo...
..oeEeooeEeo..
..oooHHHHooo..
.oHhhHHHhhHo..
.oHhpHHhhpHo..
..oHhhhhhHo...
...oooHooo....
....o...o.....`, `
..............
..............
.wwwwoooowwww.
wvwwoeeooeewvw
.wwoeEeooeEeo.
..oooHHHHooo..
.oHhhHHHhhHo..
.oHhpHHhhpHo..
..oHhhhhhHo...
...oooHooo....
....o...o.....`, `
...ww.ww......
..wvwwwvw.....
...wwoooo.....
...oeeooeeo...
..oeEeooeEeo..
..oooHHHHooo..
..oHhhHHhhHo..
...oHhpHhpHo..
....oHhhhHo...
.....ooHoo....
......o.o.....`];
  function voladores() {
    const alas = { W: '#5a6a7a', w: '#a8c0cc', v: '#6a7a8a' };
    criatura({ avispa_0: AVISPA[0], avispa_1: AVISPA[1], avispa_2: AVISPA[2] },
      Object.assign({ o: A.negro[1], J: A.hueso[3], k: A.negro[0], e: A.ojo[0], y: A.fuego[3], Y: A.negro[2], s: A.hueso[2] }, alas), 'e');
    criatura({ moscarda_0: MOSCA[0], moscarda_1: MOSCA[1], moscarda_2: MOSCA[2] },
      Object.assign({ o: A.negro[0], e: A.ojo[0], E: A.ojo[1], H: '#1e3a2a', h: '#3a6a4a', p: A.podre[0] }, alas), 'eEp');
  }

  // ---------------------------------------------------------------- la Lápida
  // Escarabajo con el caparazón como una lápida (redonda arriba, con una cruz grabada) cubierta de musgo; ojos rojos.
  // Letras: q Q R r piedra (oscuro → claro) · c grabado · g n N musgo · o borde · e ojo (brilla) · k cabeza · l patas
  const LAPIDA = `
......qqqqqqqq......
.....qQRRRRRRQq.....
....qQRRrrrrRRQq....
....qQRrrRrrrRQq....
....qQRrcccrrRQq....
....qQRrrcrrrRQq....
...gqQRrrcrrrRQqg...
..gnqQRRrcrrRRQqng..
.gnNqQRRRRRRRRQqNng.
.ggnqQQQQQQQQQQqnngg
..ggqqqqqqqqqqqqgg..
...okkkoeookkkeo....
..okkkkkkkkkkkkko...`;
  const PATAS = [`
.l.l.l......l.l.l.
l..l..l....l..l..l`, `
..l.l.l....l.l.l..
.l..l..l..l..l..l.`];
  function lapida() {
    const pal = { q: A.piedra[0], Q: A.piedra[1], R: A.piedra[2], r: A.piedra[3], c: A.piedra[0], g: A.musgo[1], n: A.musgo[2], N: A.musgo[3], o: A.negro[0], e: A.ojo[0], k: A.negro[2], l: A.negro[1] };
    criatura({
      escarabajo_musgo_0: { g: componer(20, 15, [[LAPIDA, 0, 0], [PATAS[0], 1, 13]]), cx: 10 },
      escarabajo_musgo_1: { g: componer(20, 15, [[LAPIDA, 0, 0], [PATAS[1], 1, 13]]), cx: 10 },
      escarabajo_musgo_2: { g: componer(20, 15, [[LAPIDA, 0, 1], [PATAS[0], 1, 13]]), cx: 10 },
      escarabajo_musgo_3: { g: componer(22, 15, [[LAPIDA, 2, 0], [PATAS[1], 3, 13]]), cx: 12 },
    }, pal, 'e');
  }

  return { criatura, babas, grutos, gusanos, portador, sapo, fauce, tentaculo, voladores, lapida };
})();

CONSTRUIR_MODELOS.push(function criaturasMano() {
  CRIATURAS_MANO.babas();
  CRIATURAS_MANO.grutos();
  CRIATURAS_MANO.gusanos();
  CRIATURAS_MANO.portador();
  CRIATURAS_MANO.sapo();
  CRIATURAS_MANO.fauce();
  CRIATURAS_MANO.tentaculo();
  CRIATURAS_MANO.voladores();
  CRIATURAS_MANO.lapida();
});
