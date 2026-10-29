'use strict';
// Letra de 3×5 píxeles (la de antes de la 0.8; queda de reserva). Los glifos vienen de la fuente de pxl (MIT, .claude/skills/pxl-pixel-art/src/core/font.ts);
// las tildes, la Ñ y ¡¿ son nuestras. «#» = tinta, filas separadas por «/». Las minúsculas se dibujan en mayúscula.
const GLIFOS = {
  A: '###/#.#/###/#.#/#.#', B: '##./#.#/##./#.#/##.', C: '###/#../#../#../###',
  D: '##./#.#/#.#/#.#/##.', E: '###/#../##./#../###', F: '###/#../##./#../#..',
  G: '###/#../#.#/#.#/###', H: '#.#/#.#/###/#.#/#.#', I: '###/.#./.#./.#./###',
  J: '..#/..#/..#/#.#/###', K: '#.#/#.#/##./#.#/#.#', L: '#../#../#../#../###',
  M: '#.#/###/###/#.#/#.#', N: '##./#.#/#.#/#.#/#.#', O: '###/#.#/#.#/#.#/###',
  P: '###/#.#/###/#../#..', Q: '###/#.#/#.#/###/..#', R: '##./#.#/##./#.#/#.#',
  S: '###/#../###/..#/###', T: '###/.#./.#./.#./.#.', U: '#.#/#.#/#.#/#.#/###',
  V: '#.#/#.#/#.#/#.#/.#.', W: '#.#/#.#/###/###/#.#', X: '#.#/#.#/.#./#.#/#.#',
  Y: '#.#/#.#/###/.#./.#.', Z: '###/..#/.#./#../###',
  0: '###/#.#/#.#/#.#/###', 1: '##./.#./.#./.#./###', 2: '###/..#/###/#../###',
  3: '###/..#/.##/..#/###', 4: '#.#/#.#/###/..#/..#', 5: '###/#../###/..#/###',
  6: '###/#../###/#.#/###', 7: '###/..#/..#/.#./.#.', 8: '###/#.#/###/#.#/###',
  9: '###/#.#/###/..#/###',
  ' ': '.../.../.../.../...', '.': '././././#', ',': '../../../.#/#.',
  '!': '#/#/#/./#', '?': '###/..#/.##/.../.#.', ':': './#/./#/.', ';': '../.#/../.#/#.',
  "'": '#/#/././.', '"': '#.#/#.#/.../.../...', '-': '.../.../###/.../...',
  '+': '.../.#./###/.#./...', '/': '..#/..#/.#./#../#..', '(': '.#/#./#./#./.#',
  ')': '#./.#/.#/.#/#.', '=': '.../###/.../###/...', '%': '#.#/..#/.#./#../#.#',
  '*': '.../#.#/.#./#.#/...', '<': '..#/.#./#../.#./..#', '>': '#../.#./..#/.#./#..',
  '¡': '#/./#/#/#', '¿': '.#./.../##./#../###', '×': '.../#.#/.#./#.#/...', '·': './././#/.',
  '«': '..../.#.#/#.#./.#.#/....', '»': '..../#.#./.#.#/#.#./....', '…': '...../...../...../...../#.#.#', '—': '...../...../#####/...../.....',
};
// Letras con marca encima: se pinta la base y un píxel (o tres) en la fila de arriba.
const TILDES = { Á: ['A', [2]], É: ['E', [2]], Í: ['I', [1]], Ó: ['O', [2]], Ú: ['U', [2]], Ñ: ['N', [0, 1, 2]], Ü: ['U', [0, 2]] };

// Desde la 0.8 la letra es Pixel Operator 8 (glifos8.js, CC0): mayúsculas de 7 px, minúsculas, tildes y ñ. La de 3×5
// (GLIFOS) queda solo para lo que no tenga. Cada glifo lleva su espacio a la derecha.
const ALTO_LINEA = 10;
const cacheFuente = new Map();

function glifo(ch) {
  if (typeof GLIFOS8 !== 'undefined' && GLIFOS8[ch]) {
    const filas = GLIFOS8[ch].split('/'), px = [];
    filas.forEach((f, y) => [...f].forEach((c, x) => { if (c === '#') px.push([x, y]); }));
    return { w: filas[0].length, px, alto: 11 };
  }
  const up = ch.toUpperCase();
  if (TILDES[up]) {
    const [base, marca] = TILDES[up];
    const g = glifo(base);
    return { w: g.w, px: g.px.concat(marca.map((x) => [x, 0])), alto: 8 };
  }
  const s = GLIFOS[ch] || GLIFOS[up] || GLIFOS['?'];
  const filas = s.split('/'), px = [];
  filas.forEach((f, y) => [...f].forEach((c, x) => { if (c === '#') px.push([x, y + 2]); }));
  return { w: filas[0].length + 1, px, alto: 8 };
}

// Hoja de glifos de un color, hecha una vez y reutilizada. Cada glifo en una celda de 9×11; la fila 2 es la de arriba
// de las mayúsculas (texto() pone ahí la «y»).
const CARACTERES = [...new Set((typeof GLIFOS8 !== 'undefined' ? Object.keys(GLIFOS8).join('') : '') + Object.keys(GLIFOS).join('') + Object.keys(TILDES).join('') + 'abcdefghijklmnopqrstuvwxyzáéíóúñü')].join('');
const CELDA = 9, ALTO_GLIFO = 11;
function hojaFuente(color) {
  let h = cacheFuente.get(color);
  if (h) return h;
  const cv = document.createElement('canvas'), mapa = {};
  cv.width = CARACTERES.length * CELDA; cv.height = ALTO_GLIFO;
  const cx = cv.getContext('2d');
  cx.fillStyle = color;
  [...CARACTERES].forEach((ch, i) => {
    const g = glifo(ch);
    for (const [x, y] of g.px) cx.fillRect(i * CELDA + x, y, 1, 1);
    mapa[ch] = { x: i * CELDA, w: g.w };
  });
  h = { cv, mapa };
  cacheFuente.set(color, h);
  return h;
}

// La letra pequeña de antes (3×5) para los números de las ranuras, que tienen poco sitio.
function hojaMini(color) {
  const k = 'mini' + color;
  let h = cacheFuente.get(k);
  if (h) return h;
  const cv = document.createElement('canvas'), mapa = {}, chars = Object.keys(GLIFOS).join('');
  cv.width = chars.length * 5; cv.height = ALTO_GLIFO;
  const cx = cv.getContext('2d');
  cx.fillStyle = color;
  [...chars].forEach((ch, i) => {
    const filas = GLIFOS[ch].split('/');
    filas.forEach((f, y) => [...f].forEach((c, x) => { if (c === '#') cx.fillRect(i * 5 + x, y + 2, 1, 1); }));
    mapa[ch] = { x: i * 5, w: filas[0].length + 1 };
  });
  h = { cv, mapa };
  cacheFuente.set(k, h);
  return h;
}
function anchoTexto(s, esc = 1, mini = false) {
  let w = 0;
  const m = (mini ? hojaMini('#fff') : hojaFuente('#fff')).mapa;
  for (const ch of String(s)) { const g = m[ch] || m['?']; w += g.w * esc; }
  return Math.max(0, w - esc);
}

// alin: 'i' izquierda, 'c' centro, 'd' derecha. sombra: color de una sombra 1 px abajo.
function texto(ctx, s, x, y, color = '#f2ecf8', o = {}) {
  s = String(s);
  const esc = o.esc || 1;
  if (o.alin === 'c') x -= Math.floor(anchoTexto(s, esc, o.mini) / 2);
  else if (o.alin === 'd') x -= anchoTexto(s, esc, o.mini);
  x = Math.round(x); y = Math.round(y);
  if (o.sombra) texto(ctx, s, x, y + esc, o.sombra, { esc, mini: o.mini });
  if (o.borde) for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) texto(ctx, s, x + dx * esc, y + dy * esc, o.borde, { esc, mini: o.mini });
  const h = o.mini ? hojaMini(color) : hojaFuente(color);
  for (const ch of s) {
    const g = h.mapa[ch] || h.mapa['?'];
    if (ch !== ' ') ctx.drawImage(h.cv, g.x, 0, g.w, ALTO_GLIFO, x, y - 2 * esc, g.w * esc, ALTO_GLIFO * esc);
    x += g.w * esc;
  }
}

// Parte un texto en líneas que caben en «ancho» píxeles.
function partirTexto(s, ancho, esc = 1) {
  const out = [];
  for (const parrafo of String(s).split('\n')) {
    let linea = '';
    for (const p of parrafo.split(' ')) {
      const prueba = linea ? linea + ' ' + p : p;
      if (anchoTexto(prueba, esc) > ancho && linea) { out.push(linea); linea = p; } else linea = prueba;
    }
    out.push(linea);
  }
  return out;
}
