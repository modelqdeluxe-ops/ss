'use strict';
// Letra de 3×5 píxeles. Los glifos vienen de la fuente de pxl (MIT, .claude/skills/pxl-pixel-art/src/core/font.ts);
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

const ALTO_LINEA = 8;
const cacheFuente = new Map();

function glifo(ch) {
  const up = ch.toUpperCase();
  if (TILDES[up]) {
    const [base, marca] = TILDES[up];
    const g = glifo(base);
    return { w: g.w, px: g.px.concat(marca.map((x) => [x, -2])) };
  }
  const s = GLIFOS[ch] || GLIFOS[up] || GLIFOS['?'];
  const filas = s.split('/'), px = [];
  filas.forEach((f, y) => [...f].forEach((c, x) => { if (c === '#') px.push([x, y]); }));
  return { w: filas[0].length, px };
}

// Hoja de glifos de un color, hecha una vez y reutilizada.
const CARACTERES = Object.keys(GLIFOS).join('') + Object.keys(TILDES).join('') + 'abcdefghijklmnopqrstuvwxyzáéíóúñü';
function hojaFuente(color) {
  let h = cacheFuente.get(color);
  if (h) return h;
  const cv = document.createElement('canvas'), mapa = {};
  cv.width = CARACTERES.length * 5; cv.height = 8;
  const cx = cv.getContext('2d');
  cx.fillStyle = color;
  [...CARACTERES].forEach((ch, i) => {
    const g = glifo(ch);
    for (const [x, y] of g.px) cx.fillRect(i * 5 + x, y + 2, 1, 1);
    mapa[ch] = { x: i * 5, w: g.w };
  });
  h = { cv, mapa };
  cacheFuente.set(color, h);
  return h;
}

function anchoTexto(s, esc = 1) {
  let w = 0;
  for (const ch of s) { const g = hojaFuente('#fff').mapa[ch] || hojaFuente('#fff').mapa['?']; w += (g.w + 1) * esc; }
  return Math.max(0, w - esc);
}

// alin: 'i' izquierda, 'c' centro, 'd' derecha. sombra: color de una sombra 1 px abajo.
function texto(ctx, s, x, y, color = '#f2ecf8', o = {}) {
  s = String(s);
  const esc = o.esc || 1;
  if (o.alin === 'c') x -= Math.floor(anchoTexto(s, esc) / 2);
  else if (o.alin === 'd') x -= anchoTexto(s, esc);
  x = Math.round(x); y = Math.round(y);
  if (o.sombra) texto(ctx, s, x, y + esc, o.sombra, { esc });
  if (o.borde) for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) texto(ctx, s, x + dx * esc, y + dy * esc, o.borde, { esc });
  const h = hojaFuente(color);
  for (const ch of s) {
    const g = h.mapa[ch] || h.mapa['?'];
    if (ch !== ' ') ctx.drawImage(h.cv, g.x, 0, g.w, 8, x, y - 2 * esc, g.w * esc, 8 * esc);
    x += (g.w + 1) * esc;
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
