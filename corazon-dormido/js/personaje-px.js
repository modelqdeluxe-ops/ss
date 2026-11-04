'use strict';
// El personaje cuadro a cuadro, construido como el de Core Keeper (ESTUDIO-CK.md): celdas de 26×26 con el personaje
// de 13×20 dentro (cabeza de 13×11), tres direcciones (abajo, arriba y a la derecha; la izquierda es la derecha
// volteada), 6 cuadros al andar, y las acciones en pocos cuadros bien elegidos. Nada se gira: cada cuadro se monta con
// piezas dibujadas a mano (cabeza, torso, brazos, piernas) puestas píxel a píxel, como hace un pixel artist con un
// muñeco de papel. Cada pieza es una rejilla de letras (papeles: pelo, piel, túnica…) que se pinta con la paleta del
// aspecto y de la armadura. Devuelve lo mismo que el esqueleto de la 0.8 (esqueleto.js): el dibujo, dónde está cada
// mano y hacia dónde apunta lo que lleva (para el arma y la antorcha, principal.js ponerEnMano).

const PJ_PX = (() => {
  const W = 26, H = 26, X0 = 6, Y0 = 7; // el personaje (13 de ancho) empieza en la columna 6; su fila 0 es la 7
  const PIE = [12.5, 25];

  // ---------------------------------------------------------------- piezas (columnas y filas del personaje)
  // Papeles: túnica O T t u · bufanda R r q · piel Z S s · cinto B y · pantalón Q P p · botas K k j · mochila N M m n
  const P = {
    ab: {
      torso: `
ORRrqrRRO
OTuRrRutO
OTutRtutO
OTttRttTO
BBBByBBBB`,
      // brazo izquierdo (del que mira) y derecho: manga y mano
      brazoI: `
OT
OT
Ss
ZS`,
      brazoD: `
TO
TO
sS
SZ`,
      piernaI: `
QpP
Kjk
KKK`,
      piernaD: `
PpQ
kjK
KKK`,
    },
    ar: {
      torso: `
ORRRRRRRO
OTNmnmNtO
OTNmmmNtO
OTNMmMNTO
BBNNNNNBB`,
      brazoI: `
OT
OT
Ss
ZS`,
      brazoD: `
TO
TO
sS
SZ`,
      piernaI: `
QPp
KKk
KKK`,
      piernaD: `
pPQ
kKK
KKK`,
    },
    de: {
      // de perfil (mirando a la derecha): la mochila detrás, el brazo de delante encima; el pantalón de la cadera
      // va con el torso y las piernas se separan por debajo
      mochila: `
NMn
NMm
NMm
NNM`,
      torso: `
ORrqrRO
OTuuttO
OTutttO
OTtttTO
BBBByBB
.QPppQ.`,
      brazo: `
Tu
Tt
sS
SZ`,
      brazoAtras: `
TT
TT
SS`,
      pierna: `
Ppp
Kjk
KKKk`,
      piernaAtras: `
PPP
KKk
KKKK`,
    },
  };

  // ---------------------------------------------------------------- cuadros
  // Cada cuadro dice dónde va cada pieza (desplazamientos en píxeles) y dónde están las manos:
  //   c: [dx, dy] de la cabeza · t: [dx, dy] del torso · bI / bD: [dx, dy] de los brazos (de frente o de espaldas)
  //   pI / pD: [dx, dy, alzada] de las piernas (alzada: el pie se levanta y la bota pierde la suela)
  //   de perfil: b (brazo de delante), ba (el de detrás), pd / pa (pierna de delante / de detrás)
  //   ojos: 'c' cerrados · mD / mI: la mano derecha / izquierda [x, y, ángulo, delante] (ángulo desde arriba,
  //   positivo hacia la derecha del dibujo)
  // Andar (6 cuadros, como Core Keeper): apoyo, sube la izquierda, pasa, apoyo, sube la derecha, pasa. El cuerpo
  // baja un píxel en los apoyos y las manos van al revés que los pies.
  const ANDAR_FRENTE = [
    { cuerpo: 1, pI: [0, 0, 0], pD: [0, 0, 0], bI: [0, 0], bD: [0, 0] },
    { cuerpo: 0, pI: [0, -1, 1], pD: [0, 0, 0], bI: [0, -1], bD: [0, 1] },
    { cuerpo: 0, pI: [0, -1, 1], pD: [0, 0, 0], bI: [0, -1], bD: [0, 1] },
    { cuerpo: 1, pI: [0, 0, 0], pD: [0, 0, 0], bI: [0, 0], bD: [0, 0] },
    { cuerpo: 0, pI: [0, 0, 0], pD: [0, -1, 1], bI: [0, 1], bD: [0, -1] },
    { cuerpo: 0, pI: [0, 0, 0], pD: [0, -1, 1], bI: [0, 1], bD: [0, -1] },
  ];
  // De perfil: zancada (las piernas se separan) y paso (se cruzan); el brazo de delante va al revés que su pierna.
  const ANDAR_LADO = [
    { cuerpo: 1, pd: [0, 0, 0], pa: [0, 0, 0], b: [0, 0], ba: [0, 0] },
    { cuerpo: 0, pd: [1, -1, 1], pa: [-1, 0, 0], b: [-1, 0], ba: [1, 0] },
    { cuerpo: 0, pd: [2, 0, 0], pa: [-2, 0, 0], b: [-2, 0], ba: [2, 0] },
    { cuerpo: 1, pd: [0, 0, 0], pa: [0, 0, 0], b: [0, 0], ba: [0, 0] },
    { cuerpo: 0, pd: [-1, 0, 0], pa: [1, -1, 1], b: [1, 0], ba: [-1, 0] },
    { cuerpo: 0, pd: [-2, 0, 0], pa: [2, 0, 0], b: [2, 0], ba: [-2, 0] },
  ];

  // ---------------------------------------------------------------- paleta
  function paleta(asp, eq) {
    const pelo = MANO.rampa(ASPECTO.pelo[asp.pelo] || ASPECTO.pelo[0], 5, { giro: 18, oscuro: 0.22 });
    const piel = MANO.rampa(ASPECTO.piel[asp.piel] || ASPECTO.piel[0], 4, { giro: 10, oscuro: 0.55 });
    const ropa = MANO.rampa(ASPECTO.ropa[asp.ropa] || ASPECTO.ropa[0], 4, { giro: 16, oscuro: 0.3 });
    const L = {
      o: pelo[0], H: pelo[1], h: pelo[2], g: pelo[3], G: pelo[4],
      Z: piel[0], S: piel[1], s: piel[2], b: ESCU.mezclar(piel[2], '#e85a64', 0.38),
      E: '#141428', e: '#3a74c8', w: '#f4f2ff',
      O: ropa[0], T: ropa[1], t: ropa[2], u: ropa[3],
      R: '#6a5a48', r: '#c8b89a', q: '#efe4cc', // el cuello claro de la camisa (ya no la bufanda roja)
      B: '#3a2014', y: '#f0c040',
      Q: '#16161c', P: '#2c2c36', p: '#4a4a58',
      K: '#2a160c', k: '#5e3820', j: '#8a5a32',
      N: '#2e180c', M: '#5a3420', m: '#7e4c2a', n: '#a46a3c',
    };
    const metal = (mt) => { const r = mt.r, n = r.length; return [r[0], r[Math.round(n * 0.3)], r[Math.round(n * 0.55)], r[Math.round(n * 0.82)]]; };
    // armaduras (como en Core Keeper, capas del color del metal): el peto pinta la túnica; las botas, las botas
    if (eq && eq.peto) { const r = metal(eq.peto); Object.assign(L, { O: r[0], T: r[1], t: r[2], u: r[3] }); }
    if (eq && eq.botas) { const r = metal(eq.botas); Object.assign(L, { K: r[0], k: r[1], j: r[3] }); }
    return L;
  }

  // ---------------------------------------------------------------- montar
  const fil = (g) => MANO.rejilla(g);
  // pinta una rejilla en el cuadro (x, y en columnas y filas del personaje); alzada: quita la última fila (el pie
  // levantado se ve más corto)
  function poner(L, g, pal, x, y, o = {}) {
    let f = fil(g);
    if (o.alzada) f = f.slice(0, f.length - 1);
    if (o.voltear) f = f.map((r) => [...r].reverse().join(''));
    for (let j = 0; j < f.length; j++) for (let i = 0; i < f[j].length; i++) {
      const k = f[j][i];
      if (k === '.' || k === ' ') continue;
      const c = pal[k];
      if (c === undefined) throw new Error(`PJ_PX: la letra «${k}» no tiene color`);
      L.px(X0 + x + i, Y0 + y + j, c);
    }
  }
  // La cabeza: la de PJ_MANO (13×11, con peinado, coleta o moño y casco), 17×14 con 2 columnas y 3 filas de margen.
  function cabeza(L, dir, asp, eq, pal, x, y, ojos) {
    const g = PJ_MANO.cabeza(dir, asp, eq);
    const p = Object.assign({}, pal);
    if (eq && eq.casco) {
      const r = eq.casco.r, n = r.length;
      Object.assign(p, { c: r[0], C: r[Math.round(n * 0.3)], k: r[Math.round(n * 0.55)], K: r[Math.round(n * 0.82)], X: ESCU.mezclar(r[n - 1], '#ffffff', 0.3) });
    }
    if (ojos === 'c') Object.assign(p, { e: p.s, w: p.s });
    poner(L, g, p, x - 2, y - 3);
  }

  // Un cuadro. dir ab|ar|de; anim y f como PJ_ESQ.ANIMS. Devuelve { color, dx, manos, pie }.
  function cuadro(dir, anim, f, asp, eq) {
    const pal = paleta(asp, eq), L = new Lienzo(W, H);
    const k = (ANIMS[anim] || ANIMS.q)[2](dir, f);
    if (dir === 'de') lado(L, k, asp, eq, pal);
    else frente(L, dir, k, asp, eq, pal);
    if (k.tumbado) tumbar(L);
    MANO.normales(L);
    const manos = {};
    for (const [n, m] of [['D', k.mD], ['I', k.mI]]) if (m) manos[n] = { x: X0 + m[0], y: Y0 + m[1], ang: m[2], delante: !!m[3] };
    return { color: L, dx: 0, manos, pie: PIE };
  }
  // de frente (ab) o de espaldas (ar)
  function frente(L, dir, k, asp, eq, pal) {
    const D = P[dir], cu = k.cuerpo || 0, cy = (k.c ? k.c[1] : 0) + cu, cx = k.c ? k.c[0] : 0;
    const tx = (k.t ? k.t[0] : 0), ty = (k.t ? k.t[1] : 0) + cu;
    const pI = k.pI || [0, 0, 0], pD = k.pD || [0, 0, 0], bI = k.bI || [0, 0], bD = k.bD || [0, 0];
    // piernas
    poner(L, D.piernaI, pal, 3 + pI[0], 16 + pI[1], { alzada: pI[2] });
    poner(L, D.piernaD, pal, 7 + pD[0], 16 + pD[1], { alzada: pD[2] });
    // brazos que van por detrás del cuerpo (en el golpe, el que se levanta)
    const detras = k.brazoDetras || '';
    if (detras.includes('I')) poner(L, D.brazoI, pal, 0 + bI[0] + tx, 12 + bI[1] + ty);
    if (detras.includes('D')) poner(L, D.brazoD, pal, 11 + bD[0] + tx, 12 + bD[1] + ty);
    poner(L, D.torso, pal, 2 + tx, 11 + ty);
    if (!detras.includes('I')) poner(L, D.brazoI, pal, 0 + bI[0] + tx, 12 + bI[1] + ty);
    if (!detras.includes('D')) poner(L, D.brazoD, pal, 11 + bD[0] + tx, 12 + bD[1] + ty);
    cabeza(L, dir, asp, eq, pal, cx, cy, k.ojos);
  }
  // de perfil, mirando a la derecha
  function lado(L, k, asp, eq, pal) {
    const D = P.de, cu = k.cuerpo || 0, cy = (k.c ? k.c[1] : 0) + cu, cx = k.c ? k.c[0] : 0;
    const tx = (k.t ? k.t[0] : 0), ty = (k.t ? k.t[1] : 0) + cu;
    const pd = k.pd || [0, 0, 0], pa = k.pa || [0, 0, 0], b = k.b || [0, 0], ba = k.ba || [0, 0];
    poner(L, D.brazoAtras, pal, 6 + ba[0] + tx, 12 + ba[1] + ty);
    poner(L, D.piernaAtras, pal, 5 + pa[0], 17 + pa[1], { alzada: pa[2] });
    poner(L, D.pierna, pal, 5 + pd[0], 17 + pd[1], { alzada: pd[2] });
    poner(L, D.mochila, pal, 1 + tx, 11 + ty);
    poner(L, D.torso, pal, 3 + tx, 11 + ty);
    cabeza(L, 'de', asp, eq, pal, cx, cy, k.ojos);
    poner(L, D.brazo, pal, 5 + b[0] + tx, 12 + b[1] + ty);
  }
  // tumbado (al morir): el cuadro entero girado un cuarto de vuelta (sin dientes: es una rotación exacta de píxeles)
  function tumbar(L) {
    const c = L.d.slice();
    L.d.fill(null);
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const v = c[y * W + x];
      if (!v) continue;
      // gira 90° hacia la derecha con los pies hacia la izquierda, apoyado en la fila de abajo
      const nx = H - 1 - y + 1, ny = x - 1;
      if (nx >= 0 && ny >= 0 && nx < W && ny < H) L.d[ny * W + nx] = v;
    }
    // baja el dibujo hasta el suelo
    let fondo = -1;
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) if (L.d[y * W + x]) fondo = y;
    const baja = H - 1 - fondo;
    if (baja > 0) { const d = L.d.slice(); L.d.fill(null); for (let y = 0; y + baja < H; y++) for (let x = 0; x < W; x++) L.d[(y + baja) * W + x] = d[y * W + x]; }
  }

  // ---------------------------------------------------------------- animaciones
  // Las manos de cada dirección cuando no hace nada: la derecha lleva la herramienta apuntando arriba y adelante; la
  // izquierda, la antorcha. De frente la derecha está a la izquierda del dibujo; de perfil la derecha es la de
  // delante; de espaldas lo que se lleva queda detrás del cuerpo.
  function manosQuieto(dir, k) {
    const bI = k.bI || [0, 0], bD = k.bD || [0, 0], b = k.b || [0, 0], ba = k.ba || [0, 0], t = k.t || [0, 0], cu = k.cuerpo || 0;
    if (dir === 'ab') return { mD: [1 + bI[0] + t[0], 14.5 + bI[1] + t[1] + cu, -0.8, 1], mI: [11.5 + bD[0] + t[0], 14.5 + bD[1] + t[1] + cu, 0, 1] };
    if (dir === 'ar') return { mD: [11.5 + bD[0] + t[0], 14.5 + bD[1] + t[1] + cu, 0.8, 0], mI: [1 + bI[0] + t[0], 14.5 + bI[1] + t[1] + cu, 0, 0] };
    // de perfil, la herramienta hacia delante y un poco abajo (si apunta arriba tapa la cara)
    return { mD: [6.5 + b[0] + t[0], 15 + b[1] + t[1] + cu, 1.9, 1], mI: [7 + ba[0] + t[0], 14 + ba[1] + t[1] + cu, 0.15, 0] };
  }
  const conManos = (dir, k) => Object.assign(manosQuieto(dir, k), k);
  const quieto = (dir) => conManos(dir, {});
  const andar = (dir, f) => conManos(dir, Object.assign({}, (dir === 'de' ? ANDAR_LADO : ANDAR_FRENTE)[f % 6]));
  // Golpe (como el de Core Keeper: 4 cuadros de preparar y el golpe): sube el brazo detrás de la cabeza, carga,
  // descarga hacia delante y abajo y vuelve. 6 cuadros para que el arma gire suave; el cuerpo cambia cada uno.
  const GOLPE = {
    ab: [
      { c: [0, 0], t: [0, 0], bI: [0, -4], brazoDetras: 'I', mD: [0.5, 9.5, -0.9, 0] },
      { c: [0, -1], t: [0, 0], bI: [0, -5], brazoDetras: 'I', mD: [0.5, 8.5, -1.4, 0] },
      { c: [0, 0], t: [0, 0], bI: [1, -2], mD: [2, 12, 0.6, 1] },
      { c: [0, 1], t: [0, 1], bI: [2, 1], ojos: 'c', mD: [3.5, 16.5, 2.4, 1] },
      { c: [0, 1], t: [0, 1], bI: [2, 1], mD: [3.5, 16.5, 2.6, 1] },
      { c: [0, 0], t: [0, 0], bI: [1, 0], mD: [2, 15, 1.4, 1] },
    ],
    ar: [
      { c: [0, 0], bD: [0, -4], mD: [12.5, 9.5, 0.9, 0] },
      { c: [0, -1], bD: [0, -5], mD: [12.5, 8.5, 1.4, 0] },
      { c: [0, 0], bD: [-1, -2], mD: [11, 12, -0.6, 0] },
      { c: [0, 1], t: [0, 1], bD: [-2, 1], mD: [9.5, 16.5, -2.4, 0] },
      { c: [0, 1], t: [0, 1], bD: [-2, 1], mD: [9.5, 16.5, -2.6, 0] },
      { c: [0, 0], bD: [-1, 0], mD: [11, 15, -1.4, 0] },
    ],
    de: [
      { c: [-1, 0], t: [-1, 0], b: [-3, -4], mD: [3.5, 9.5, -0.7, 1] },
      { c: [-1, -1], t: [-1, 0], b: [-4, -5], mD: [2.5, 8.5, -1.2, 1] },
      { c: [0, 0], t: [0, 0], b: [0, -3], mD: [7, 10.5, 0.4, 1] },
      { c: [1, 1], t: [1, 0], b: [3, 0], ojos: 'c', pd: [1, 0, 0], mD: [11, 14, 1.9, 1] },
      { c: [1, 1], t: [1, 0], b: [3, 1], pd: [1, 0, 0], mD: [11, 15, 2.4, 1] },
      { c: [0, 0], t: [0, 0], b: [1, 0], mD: [8.5, 14.5, 1.6, 1] },
    ],
  };
  const golpe = (dir, f) => { const g = GOLPE[dir][Math.min(5, f)]; return Object.assign(manosQuieto(dir, g), g); };
  // golpe recibido: echa el cuerpo atrás y cierra los ojos; después se recompone
  const herido = (dir, f) => conManos(dir, f < 2 ? { c: [dir === 'de' ? -1 : 0, -1], t: [dir === 'de' ? -1 : 0, 0], ojos: 'c', bI: [0, -1], bD: [0, -1], b: [-1, -1] } : { c: [0, 0] });
  // morir: se dobla, cae de rodillas y se queda tumbado
  const morir = (dir, f) => {
    if (f >= 3) return { tumbado: true, ojos: 'c', cuerpo: 0 };
    return conManos(dir, [{ c: [0, 1], t: [0, 1], cuerpo: 0, ojos: 'c' }, { c: [0, 2], t: [0, 2], cuerpo: 0, ojos: 'c', bI: [0, 1], bD: [0, 1] }, { c: [0, 3], t: [0, 3], cuerpo: 0, ojos: 'c', bI: [0, 2], bD: [0, 2], pI: [0, 0, 1], pD: [0, 0, 1] }][f]);
  };
  // usar o comer: las dos manos a la boca (de frente) o delante (de perfil)
  const usar = (dir, f) => conManos(dir, f % 2 === 0 ? { bI: [1, -2], bD: [-1, -2], b: [1, -2] } : { bI: [1, -3], bD: [-1, -3], b: [1, -3], c: [0, 1] });
  // lanzar (también la caña): atrás, adelante y se queda tendido
  const lanzar = (dir, f) => {
    const g = GOLPE[dir][[0, 1, 2, 3, 5, 5][Math.min(5, f)]];
    return Object.assign(manosQuieto(dir, g), g);
  };
  // disparar (arco): el brazo estirado hacia donde mira
  const disparar = (dir, f) => {
    const k = dir === 'de' ? { b: [3, -2], mD: [11, 12.5, 1.57, 1], t: [f === 1 ? -1 : 0, 0] } : dir === 'ab' ? { bI: [1, 0], mD: [2.5, 15.5, 3.1, 1] } : { bD: [-1, -1], mD: [11, 13, 0, 0] };
    return Object.assign(manosQuieto(dir, k), k);
  };
  // esquivar: se agacha, rueda y se levanta
  const esquivar = (dir, f) => conManos(dir, [{ c: [0, 2], t: [0, 2], cuerpo: 0 }, { c: [dir === 'de' ? 2 : 0, 4], t: [dir === 'de' ? 2 : 0, 3], cuerpo: 0, ojos: 'c' }, { c: [dir === 'de' ? 1 : 0, 2], t: [dir === 'de' ? 1 : 0, 1], cuerpo: 0 }, { c: [0, 0] }][f % 4]);

  // [nombre, cuadros, función(dir, f) → cuadro]. Las letras son las de PJ_ESQ.ANIMS (principal.js animJugador).
  const ANIMS = {
    q: ['quieto', 1, quieto], a: ['andar', 6, andar], c: ['correr', 6, andar],
    g: ['golpe', 6, golpe], h: ['golpe de lado', 6, golpe], p: ['golpe a dos manos', 6, golpe],
    r: ['golpe recibido', 3, herido], m: ['morir', 4, morir], u: ['usar', 2, usar], l: ['lanzar', 6, lanzar],
    d: ['disparar', 2, disparar], e: ['esquivar', 4, esquivar],
  };
  return { cuadro, ANIMS, W, H };
})();
