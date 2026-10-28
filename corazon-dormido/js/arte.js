'use strict';
// Arte de Corazón Dormido, dibujado con código al estilo del skill pxl (.claude/skills/pxl-pixel-art):
// cuadrículas de letras con su paleta, espejo, contorno automático y variantes por cambio de color.
// Al arrancar se pinta todo en una sola hoja (atlas) en memoria; SPR[nombre] da su recuadro.

const TINTA = '#14101c';

class Lienzo {
  constructor(w, h) { this.w = w; this.h = h; this.d = new Array(w * h).fill(null); }
  dentro(x, y) { return x >= 0 && y >= 0 && x < this.w && y < this.h; }
  get(x, y) { return this.dentro(x, y) ? this.d[y * this.w + x] : null; }
  px(x, y, c) {
    x = Math.round(x); y = Math.round(y);
    if (c && this.dentro(x, y)) this.d[y * this.w + x] = c === '_' ? null : c;
    return this;
  }
  rect(x, y, w, h, c) { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) this.px(x + i, y + j, c); return this; }
  hl(x, y, n, c) { return this.rect(x, y, n, 1, c); }
  vl(x, y, n, c) { return this.rect(x, y, 1, n, c); }
  linea(x0, y0, x1, y1, c) {
    const dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
    let e = dx + dy;
    for (;;) {
      this.px(x0, y0, c);
      if (x0 === x1 && y0 === y1) break;
      const e2 = 2 * e;
      if (e2 >= dy) { e += dy; x0 += sx; }
      if (e2 <= dx) { e += dx; y0 += sy; }
    }
    return this;
  }
  circ(cx, cy, r, c) {
    for (let y = -r; y <= r; y++) for (let x = -r; x <= r; x++) if (x * x + y * y <= r * r + r * 0.8) this.px(cx + x, cy + y, c);
    return this;
  }
  elipse(cx, cy, rx, ry, c) {
    for (let y = -ry; y <= ry; y++) for (let x = -rx; x <= rx; x++)
      if ((x * x) / ((rx + 0.4) * (rx + 0.4)) + (y * y) / ((ry + 0.4) * (ry + 0.4)) <= 1) this.px(cx + x, cy + y, c);
    return this;
  }
  // Cuadrícula de letras: cada letra es un color de la paleta, «.» deja el píxel y «_» lo borra.
  rej(x, y, pal, s) {
    lineas(s).forEach((f, j) => [...f].forEach((ch, i) => {
      if (ch === '.' || ch === ' ') return;
      if (ch !== '_' && !pal[ch]) throw new Error(`arte: la letra «${ch}» no está en la paleta`);
      this.px(x + i, y + j, ch === '_' ? '_' : pal[ch]);
    }));
    return this;
  }
  sello(o, x, y) { for (let j = 0; j < o.h; j++) for (let i = 0; i < o.w; i++) { const c = o.d[j * o.w + i]; if (c) this.px(x + i, y + j, c); } return this; }
  copia() { const l = new Lienzo(this.w, this.h); l.d = this.d.slice(); return l; }
  // Escribe la mitad izquierda; esto la refleja. centro: la columna del medio es compartida (ancho impar).
  espejo(centro = false) {
    const w = centro ? this.w * 2 - 1 : this.w * 2, l = new Lienzo(w, this.h);
    l.sello(this, 0, 0);
    for (let y = 0; y < this.h; y++) for (let x = 0; x < this.w; x++) { const c = this.get(x, y); if (c) l.px(w - 1 - x, y, c); }
    return l;
  }
  voltear() { const l = new Lienzo(this.w, this.h); for (let y = 0; y < this.h; y++) for (let x = 0; x < this.w; x++) l.d[y * this.w + this.w - 1 - x] = this.get(x, y); return l; }
  cambia(mapa) { const l = this.copia(); l.d = l.d.map((c) => (c && mapa[c]) || c); return l; }
  // Contorno de 1 px alrededor de lo opaco; el lienzo crece 1 px por lado.
  contorno(c = TINTA, diag = false) {
    const l = new Lienzo(this.w + 2, this.h + 2);
    l.sello(this, 1, 1);
    const vec = diag ? [[1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [1, -1], [-1, 1], [-1, -1]] : [[1, 0], [-1, 0], [0, 1], [0, -1]];
    for (let y = 0; y < l.h; y++) for (let x = 0; x < l.w; x++) {
      if (this.get(x - 1, y - 1)) continue;
      if (vec.some(([dx, dy]) => this.get(x - 1 + dx, y - 1 + dy))) l.d[y * l.w + x] = c;
    }
    return l;
  }
  solo(cols) { const l = this.copia(); const s = new Set(cols); l.d = l.d.map((c) => (s.has(c) ? c : null)); return l; }
  blanco() { const l = this.copia(); l.d = l.d.map((c) => (c ? '#ffffff' : null)); return l; }
  oscurece(rampas, pasos = 1, donde = () => true) {
    const l = this.copia();
    for (let y = 0; y < this.h; y++) for (let x = 0; x < this.w; x++) {
      const c = this.get(x, y); if (!c || !donde(x, y)) continue;
      for (const r of rampas) { const i = r.indexOf(c); if (i >= 0) { l.d[y * this.w + x] = r[Math.max(0, Math.min(r.length - 1, i - pasos))]; break; } }
    }
    return l;
  }
}

function lineas(s) {
  const f = s.split('\n').map((l) => l.trim());
  while (f.length && !f[0]) f.shift();
  while (f.length && !f[f.length - 1]) f.pop();
  return f;
}
function rej(pal, s) {
  const f = lineas(s);
  return new Lienzo(Math.max(...f.map((l) => [...l].length)), f.length).rej(0, 0, pal, s);
}
function azar(sem) {
  let a = sem >>> 0 || 1;
  return () => { a = (a + 0x6d2b79f5) | 0; let t = Math.imul(a ^ (a >>> 15), 1 | a); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
}
const entre = (r, a, b) => a + Math.floor(r() * (b - a + 1));
// Pasa los colores de una paleta a otra por letra (variantes de cobre, estaño, hierro…).
const cambioPal = (a, b) => Object.fromEntries(Object.keys(b).filter((k) => a[k]).map((k) => [a[k], b[k]]));

const DIBUJOS = [];
function def(nombre, l) { DIBUJOS.push([nombre, l]); return l; }

// ---------------------------------------------------------------- paredes y suelos

// Rampas de oscuro a claro: [sombra, oscuro, base, claro, brillo]
const RAMPA = {
  tierra: ['#2a1c19', '#3f2a22', '#5a3d2e', '#7a553d', '#9c7350'],
  arcilla: ['#3b1a1c', '#5e2a28', '#83402f', '#a95c3e', '#cc7f55'],
  piedra: ['#1b1d29', '#2b3042', '#3f4760', '#58637f', '#7a87a3'],
  ruina: ['#18242b', '#24363f', '#33505b', '#4a6d78', '#6b919b'],
  roca: ['#0b0a10', '#141219', '#1d1a24', '#272331', '#332e3f'],
};
const BORDE_MURO = '#0e0b12';

const TEXTURA = {
  tierra(L, r, [c0, c1, , c3, c4]) {
    for (let i = 0; i < 9; i++) { // terrones: una mancha oscura con su luz arriba
      const x = entre(r, 1, 13), y = entre(r, 2, 13);
      L.hl(x, y, 2, c1); if (r() < 0.6) L.px(x + 2, y, c1); L.hl(x, y - 1, 2, c3);
    }
    for (let i = 0; i < 3; i++) L.px(entre(r, 2, 13), entre(r, 2, 13), c4);
    if (r() < 0.35) { let x = entre(r, 3, 12), y = 2; for (let k = 0; k < 9; k++) { L.px(x, y + k, c0); x += entre(r, -1, 1); } }
  },
  arcilla(L, r, [c0, c1, , c3]) {
    for (let y = entre(r, 2, 4); y < 15; y += entre(r, 4, 5)) { // vetas horizontales, rotas
      let x = 0;
      while (x < 16) { const n = entre(r, 3, 7); L.hl(x, y, n, c1); L.hl(x, y - 1, n, c3); x += n + entre(r, 1, 3); }
    }
    for (let i = 0; i < 3; i++) L.px(entre(r, 1, 14), entre(r, 1, 14), c0);
  },
  piedra(L, r, [c0, c1, , c3, c4]) {
    for (let i = 0; i < 3; i++) { // grietas entre bloques
      let x = entre(r, 1, 14), y = entre(r, 1, 14);
      const dx = r() < 0.5 ? 1 : -1;
      for (let k = 0; k < entre(r, 4, 7); k++) {
        L.px(x, y, c1); L.px(x - 1, y - 1, c3);
        if (r() < 0.5) x += dx; else y += 1;
      }
    }
    for (let i = 0; i < 2; i++) { const x = entre(r, 2, 12), y = entre(r, 2, 12); L.rect(x, y, 2, 2, c3); L.px(x, y, c4); L.px(x + 1, y + 1, c1); }
    L.px(entre(r, 1, 14), entre(r, 1, 14), c0);
  },
  ruina(L, r, [c0, c1, , c3, c4]) {
    for (let y = 0; y < 16; y += 4) { // sillares con juntas desplazadas
      L.hl(0, y + 3, 16, c1);
      const d = (y / 4) % 2 ? 4 : 0;
      for (let x = d; x < 16; x += 8) { L.vl(x, y, 3, c1); L.px(x + 1, y, c3); }
      L.hl(0, y, 16, c3);
    }
    for (let i = 0; i < 2; i++) { const x = entre(r, 1, 13), y = entre(r, 1, 13); L.rect(x, y, 2, 1, '#3e6a44'); L.px(x, y - 1, '#5c8f4f'); }
    L.px(entre(r, 1, 14), entre(r, 1, 14), c4); L.px(entre(r, 1, 14), entre(r, 1, 14), c0);
  },
  roca(L, r, [, c1, , c3]) {
    for (let i = 0; i < 4; i++) { const x = entre(r, 1, 14), y = entre(r, 1, 14); L.px(x, y, c1); L.px(x - 1, y - 1, c3); }
  },
};

// Cara de arriba de una pared (16×16). mascara: 1 norte, 2 este, 4 sur, 8 oeste abiertos (suelo al lado).
function muroArriba(tipo, mascara, sem) {
  const ram = RAMPA[tipo], [c0, c1, c2, c3, c4] = ram;
  const L = new Lienzo(16, 16).rect(0, 0, 16, 16, c2);
  TEXTURA[tipo](L, azar(sem), ram);
  const N = mascara & 1, E = mascara & 2, S = mascara & 4, O = mascara & 8;
  if (S) { L.hl(0, 14, 16, c3); L.hl(0, 15, 16, c4); }           // labio de delante, con luz
  if (N) { L.hl(0, 1, 16, c3); L.hl(0, 0, 16, BORDE_MURO); }
  if (O) { L.vl(1, 0, 16, c3); L.vl(0, 0, 16, BORDE_MURO); }
  if (E) { L.vl(14, 0, 16, c1); L.vl(15, 0, 16, BORDE_MURO); }
  // esquinas redondeadas
  const esquina = (x, y, ix, iy) => { L.px(x, y, '_'); L.px(ix, y, BORDE_MURO); L.px(x, iy, BORDE_MURO); L.px(ix, iy, BORDE_MURO); };
  if (N && O) esquina(0, 0, 1, 1);
  if (N && E) esquina(15, 0, 14, 1);
  if (S && O) { L.px(0, 15, '_'); L.px(1, 15, c3); L.px(0, 14, BORDE_MURO); }
  if (S && E) { L.px(15, 15, '_'); L.px(14, 15, c3); L.px(15, 14, BORDE_MURO); }
  return L;
}
// Cara frontal (16×8), visible cuando al sur hay suelo. lados: 1 oeste abierto, 2 este abierto.
function muroCara(tipo, lados, sem) {
  const [c0, c1, c2] = RAMPA[tipo], r = azar(sem);
  const L = new Lienzo(16, 8).rect(0, 0, 16, 8, c1);
  L.hl(0, 0, 16, c2);
  if (tipo === 'arcilla') { L.hl(0, 3, 16, c0); L.hl(entre(r, 0, 6), 5, entre(r, 4, 9), c0); }
  else if (tipo === 'ruina') { L.hl(0, 4, 16, c0); for (let x = 3; x < 16; x += 8) L.vl(x, 1, 3, c0); for (let x = 7; x < 16; x += 8) L.vl(x, 5, 2, c0); }
  else for (let i = 0; i < 4; i++) { const x = entre(r, 1, 14); L.vl(x, entre(r, 1, 3), entre(r, 2, 4), c0); }
  L.hl(0, 7, 16, c0);
  if (lados & 1) L.vl(0, 0, 8, BORDE_MURO);
  if (lados & 2) L.vl(15, 0, 8, BORDE_MURO);
  return L;
}

const SUELO = {
  tierra: { base: '#3a2a23', os: '#30221d', cl: '#4a372c', br: '#5e4636' },
  arcilla: { base: '#4a2925', os: '#3a1e1c', cl: '#5a332d', br: '#6c4034' },
  piedra: { base: '#242836', os: '#1c1f2b', cl: '#2d3243', br: '#3a4155' },
  ruina: { base: '#2c3b44', os: '#202c33', cl: '#384b55', br: '#4a6470' },
  arena: { base: '#3d2c22', os: '#2e2019', cl: '#4c382b', br: '#634a37' },
};
function suelo(tipo, sem) {
  const s = SUELO[tipo], r = azar(sem), L = new Lienzo(16, 16).rect(0, 0, 16, 16, s.base);
  if (tipo === 'tierra' || tipo === 'arena') {
    for (let i = 0; i < 4; i++) { const x = entre(r, 0, 13), y = entre(r, 0, 13); L.hl(x, y + 1, 2, s.os); L.px(x, y, s.cl); if (r() < 0.5) L.px(x + 1, y, s.br); }
    for (let i = 0; i < 2; i++) L.px(entre(r, 0, 15), entre(r, 0, 15), s.os);
    if (tipo === 'arena' && r() < 0.6) { const x = entre(r, 2, 10), y = entre(r, 2, 12); for (let k = 0; k < 3; k++) L.linea(x + k * 2, y, x + k * 2 + 2, y + 3, s.os); }
  } else if (tipo === 'arcilla') {
    let x = entre(r, 0, 15), y = 0; // barro seco cuarteado
    for (let k = 0; k < 16; k++) { L.px(x, y, s.os); y++; x += entre(r, -1, 1); }
    let y2 = entre(r, 3, 12), x2 = 0;
    for (let k = 0; k < 16; k++) { L.px(x2, y2, s.os); x2++; if (r() < 0.3) y2 += entre(r, -1, 1); }
    for (let i = 0; i < 3; i++) L.px(entre(r, 0, 15), entre(r, 0, 15), s.cl);
  } else if (tipo === 'piedra') {
    const x = entre(r, 1, 8), y = entre(r, 1, 8), w = entre(r, 5, 7), h = entre(r, 4, 6);
    L.rect(x, y, w, h, s.cl); L.hl(x, y, w, s.br); L.hl(x, y + h, w, s.os);
    for (let i = 0; i < 2; i++) L.px(entre(r, 0, 15), entre(r, 0, 15), s.os);
  } else if (tipo === 'ruina') {
    for (let y = 0; y < 16; y += 8) {
      L.hl(0, y, 16, s.os);
      for (let x = (y / 8) % 2 ? 4 : 0; x < 16; x += 8) L.vl(x, y, 8, s.os);
      L.hl(0, y + 1, 16, s.cl);
    }
    if (r() < 0.5) { const x = entre(r, 1, 12), y = entre(r, 2, 12); L.hl(x, y, 3, '#2f5a3a'); L.px(x + 1, y - 1, '#467a48'); }
  }
  return L;
}

// Menas incrustadas en la pared (se dibujan encima de la cara de arriba y de la frontal).
const MENA_COL = {
  cobre: ['#7c3a22', '#c8693a', '#f2a466'],
  estano: ['#5f5c7a', '#a9a6c6', '#ebe9fb'],
  hierro: ['#2c3a4f', '#6787a6', '#b9d6ec'],
};
function pepita(L, x, y, [o, m, c]) { L.px(x, y, c); L.px(x + 1, y, m); L.px(x, y + 1, m); L.px(x + 1, y + 1, o); L.px(x + 2, y + 1, o); }
function menaArriba(ore, sem) {
  const r = azar(sem), L = new Lienzo(16, 16);
  for (let i = 0; i < 4; i++) pepita(L, entre(r, 2, 11), entre(r, 2, 11), MENA_COL[ore]);
  return L;
}
function menaCara(ore, sem) {
  const r = azar(sem), L = new Lienzo(16, 8);
  for (let i = 0; i < 2; i++) pepita(L, entre(r, 2, 11), entre(r, 1, 4), MENA_COL[ore]);
  return L;
}
function grieta(n) {
  const L = new Lienzo(16, 16), r = azar(77 + n), c = '#0d0a10';
  const ramas = [[8, 8]];
  for (let k = 0; k < n * 2; k++) {
    let [x, y] = ramas[entre(r, 0, ramas.length - 1)];
    const dx = entre(r, -1, 1) || 1, dy = entre(r, -1, 1);
    for (let i = 0; i < 2 + n; i++) { x += dx; y += dy || (r() < 0.5 ? 1 : -1); L.px(x, y, c); }
    ramas.push([x, y]);
  }
  return L;
}

function construirTerreno() {
  for (const tipo of ['tierra', 'arcilla', 'piedra', 'ruina', 'roca']) {
    for (let m = 0; m < 16; m++) for (let v = 0; v < 2; v++) def(`m_${tipo}_${m}_${v}`, muroArriba(tipo, m, 101 + m * 7 + v * 131 + tipo.length * 977));
    for (let l = 0; l < 4; l++) for (let v = 0; v < 2; v++) def(`c_${tipo}_${l}_${v}`, muroCara(tipo, l, 55 + l * 13 + v * 71 + tipo.length * 31));
  }
  for (const tipo of Object.keys(SUELO)) for (let v = 0; v < 4; v++) def(`s_${tipo}_${v}`, suelo(tipo, 900 + v * 37 + tipo.length * 311));
  for (const ore of Object.keys(MENA_COL)) {
    for (let v = 0; v < 2; v++) { def(`mena_${ore}_${v}`, menaArriba(ore, 400 + v * 19 + ore.length * 7)); def(`menac_${ore}_${v}`, menaCara(ore, 600 + v * 23)); }
  }
  for (let n = 1; n <= 3; n++) def(`grieta_${n}`, grieta(n));
  // adornos del suelo
  const AD = { p: '#6b5340', P: '#4a372c', g: '#8db06a', G: '#5c7a48', h: '#e8dccb', H: '#a89a88', r: '#5a3d2e', z: '#6fe3ef', c: '#d8cfb8' };
  def('ad_piedras', rej(AD, `
    .pp...
    pPP.p.
    ....PP`));
  def('ad_hierba', rej(AD, `
    .g..g.
    .g.gG.
    gG.G..
    G.GG.G`));
  def('ad_huesos', rej(AD, `
    h....h.
    Hhhhhh.
    h....H.
    .......
    ..hh...
    .hHHh..`));
  def('ad_raices', rej(AD, `
    r.....
    .r..r.
    .rrr..
    ...r..`));
  def('ad_hongos', rej(AD, `
    .zz...
    zzzz.z
    .cc.zz
    .cc..c`));
}

// ---------------------------------------------------------------- personaje

const PJ = { 3: '#f2c14e', 4: '#b9822c', w: '#fff6c8', 1: '#f4c49c', 2: '#d08c68', e: '#2a2236', h: '#6b3f2a', 5: '#3fa596', 6: '#2b7368', b: '#7a5434', 7: '#5d4d74', 8: '#3b2b33', m: '#9a6a44', M: '#6e4a30' };
const PJ_CUERPO = {
  ab: `
    ....3333....
    ..33333333..
    .3333ww3333.
    .4333ww3334.
    .4444444444.
    ..h111111h..
    ..1e1111e1..
    ..1e1111e1..
    ..21111112..
    ...222222...
    .6555555556.
    .6555555556.
    .1bbbbbbbb1.`,
  ar: `
    ....3333....
    ..33333333..
    .3333333333.
    .4333333334.
    .4444444444.
    ..hhhhhhhh..
    ..hhhhhhhh..
    ..hhhhhhhh..
    ..2hhhhhh2..
    ...222222...
    .65mmmmmm56.
    .65mmmmmm56.
    .1bMMMMMMb1.`,
  de: `
    ...33333....
    ..3333333...
    .33333333ww.
    .44333333ww.
    .44444444444
    .hhh111111..
    .hhh1111e1..
    .hhh1111e1..
    ..hh1111112.
    ...222222...
    ...655555...
    ...6555551..
    ...6bbbbb...`,
};
const PJ_PIERNAS = {
  ab: [['...777777...', '...77..77...', '...88..88...'], ['...777777...', '...77..88...', '...88.......'],
    ['...777777...', '...77..77...', '...88..88...'], ['...777777...', '...88..77...', '.......88...']],
  de: [['...77777....', '...77.77....', '...88.88....'], ['...77777....', '..77...77...', '..88...88...'],
    ['...77777....', '...77.77....', '...88.88....'], ['...77777....', '....777.....', '....888.....']],
};
PJ_PIERNAS.ar = PJ_PIERNAS.ab;

function construirJugador() {
  for (const d of ['ab', 'ar', 'de']) for (let f = 0; f < 4; f++) {
    const L = rej(PJ, PJ_CUERPO[d] + '\n' + PJ_PIERNAS[d][f].join('\n')).contorno();
    def(`jug_${d}_${f}`, L); def(`jugB_${d}_${f}`, L.blanco());
    if (d === 'de') { def(`jug_iz_${f}`, L.voltear()); def(`jugB_iz_${f}`, L.blanco().voltear()); }
  }
}

// ---------------------------------------------------------------- criaturas

function conBlanco(nombre, L, voltear = false) {
  def(nombre, L); def(nombre + '_B', L.blanco());
  if (voltear) { def(nombre + '_i', L.voltear()); def(nombre + '_iB', L.blanco().voltear()); }
}

function construirCriaturas() {
  const BAB = { g: '#86d64f', G: '#56a83b', d: '#2f6e35', w: '#effed8', e: '#14101c', c: '#f58ea0' };
  const bab0 = new Lienzo(14, 9).rej(1, 0, BAB, `
    ....gggg....
    ..ggwwgggg..
    .gwwggggggG.
    ggggggggggGG
    gggeggggegGG
    gggeggggegGG
    GGcggggggcGd
    .GGGGGGGGGd.
    ..dddddddd..`);
  const bab1 = new Lienzo(14, 9).rej(0, 1, BAB, `
    .....gggg.....
    ..ggwwggggggg.
    .gwwgggggggggG
    ggggeggggegggG
    gggceggggecggG
    GGgggggggggggd
    .dGGGGGGGGGGd.
    ..dddddddddd..`);
  conBlanco('babosa_0', bab0.contorno()); conBlanco('babosa_1', bab1.contorno());

  const LAR = { c: '#efe2bf', C: '#c9a878', D: '#8f6d4c', h: '#a5513a', H: '#6e2e22', m: '#2a1a1a', e: '#14101c', w: '#ffffff' };
  const lar = (cima, patas) => rej(LAR, `
    ${cima}
    .cccccccccccchh.
    ccCcccCcccCchhhh
    cCCccCCccCCchewh
    CDDCCDDCCDDChhhm
    ${patas}`).contorno();
  conBlanco('larva_0', lar('..cc..cc..cc....', '.D.D.D.D.D.DHhm.'), true);
  conBlanco('larva_1', lar('...cc..cc..cc...', 'D.D.D.D.D.D.Hhm.'), true);

  const ESC = { b: '#34508a', B: '#4f74c0', w: '#a9c8ff', d: '#1d2a4c', l: '#3a3048', h: '#252a40', e: '#ffc857', m: '#6b5a7a' };
  const esc = (patas) => rej(ESC, `
    ......
    ...dd.
    ..dBBd
    ${patas[0]}dBwd
    .dBBbd
    ${patas[1]}dbBbd
    .dbbbd
    ${patas[2]}dbbd
    ..ddhh
    ...heh
    ...lhh
    ....m.`).espejo().contorno();
  conBlanco('escarabajo_0', esc(['l.', 'l', 'l.']));
  conBlanco('escarabajo_1', esc(['.l', '.', '.l']));

  // El Topo Rey, guardián de la tierra. Mitad izquierda reflejada.
  const TOPO = { f: '#7a5238', F: '#56382a', L: '#a07450', b: '#d4ab84', B: '#b08860', n: '#ff8fa8', N: '#d0607e', c: '#ffe2d2', C: '#d99c8a', y: '#f6c84c', Y: '#b8862a', r: '#e43b44', k: '#2a1a18', w: '#ffffff' };
  const topoMitad = (garra) => rej(TOPO, `
    ..........y..y
    .........yy.yy
    .........yyyyy
    .........YYYYr
    ........FFFFFF
    ......FFfLLfff
    .....FfLLfffff
    ....FfLffffkkf
    ....Fffffkkfff
    ...Fffffffffnn
    ...FfffffffnnN
    ...FffffffffNN
    ..FffffffffBbb
    ..FfffffffBbbb
    ${garra[0]}
    ${garra[1]}
    ${garra[2]}
    ${garra[3]}
    ${garra[4]}
    ..FfffffffBbbb
    ...FffffffBBBB
    ...FFfffFFFFFF
    ...FF.ffF.....
    ....CCcc......`);
  const garras = [
    ['.cFffffffBbbbb', 'ccFffffffBbbbb', 'cccFfffffbbbbb', 'Ccc.FffffBbbbb', 'CC..FffffBbbbb'],
    ['cc.Fffffffbbbb', 'Ccc.FfffffBbbb', '.CcFffffffBbbb', '..FfffffffBbbb', '..FfffffffBbbb'],
  ];
  conBlanco('topo_0', topoMitad(garras[0]).espejo().contorno());
  conBlanco('topo_1', topoMitad(garras[1]).espejo().contorno());
  const MON = { a: RAMPA.tierra[1], b: RAMPA.tierra[2], c: RAMPA.tierra[3], d: RAMPA.tierra[4], y: '#f6c84c', Y: '#b8862a' };
  const monticulo = (s) => rej(MON, s).contorno();
  def('monticulo_0', monticulo(`
    ........y..y........
    ......cccccccc......
    ....ccdddcccccbb....
    ..ccddcccbbbbbbbbb..
    .bcccbbbbbbbbbbbaaa.
    abbbbbbbbbbbaaaaaaaa`));
  def('monticulo_1', monticulo(`
    ...................
    .......cccccccc......
    ....cccdddcccccbb...
    ..cccddcccbbbbbbbbb.
    .bbccbbbbbbbbbbbaaaa
    abbbbbbbbbbbbaaaaaaa`));
  def('roca_lanzada', rej({ p: '#8a7a6a', P: '#5e5046', q: '#b8a898' }, `
    .qp.
    qppP
    pPPP
    .PP.`).contorno());
}

// ---------------------------------------------------------------- objetos del mundo

const MADERA = { w: '#c28a52', W: '#9a6438', d: '#6e4228', D: '#4a2a1c', e: '#e2b07a' };
const FUEGO = { y: '#ffe066', o: '#ff9a3c', r: '#e8502a', w: '#fff7d0' };
const PIEDRA_OBJ = { p: '#4d5466', P: '#6d7588', q: '#949db0', Q: '#323645', k: '#14101c' };
const CRISTAL = { z: '#1a8a9c', Z: '#3fd8e8', l: '#b8fbff' };

function construirObjetos() {
  const antorcha = (llama) => rej({ ...MADERA, ...FUEGO }, llama + `
    ..DDD..
    ...W...
    ...W...
    ...d...
    ...d...
    ...d...`).contorno();
  const llamas = [`
    ...r...
    ..ror..
    .royor.
    .oywyo.
    ..oyo..`, `
    ..r....
    ..ror..
    .royyr.
    .oywyo.
    ..oyo..`, `
    ....r..
    ..oro..
    .rooyr.
    .oyywo.
    ..oyo..`];
  llamas.forEach((l, i) => { const L = antorcha(l); def(`antorcha_${i}`, L); def(`antorcha_${i}_luz`, L.solo(Object.values(FUEGO))); });

  const mesa = new Lienzo(16, 14);
  mesa.rect(0, 0, 16, 7, MADERA.W).hl(0, 0, 16, MADERA.e).vl(5, 1, 6, MADERA.d).vl(10, 1, 6, MADERA.d);
  mesa.rect(0, 7, 16, 3, MADERA.d).hl(0, 7, 16, MADERA.w).rect(1, 10, 2, 4, MADERA.D).rect(13, 10, 2, 4, MADERA.D);
  mesa.linea(2, 4, 7, 2, '#7a3a2a').rect(6, 1, 3, 2, '#9aa6b8').px(6, 1, '#d5dde8'); // martillo
  mesa.rect(9, 3, 6, 2, '#b9c4d2').hl(9, 5, 6, '#7d889a').rect(13, 2, 2, 2, '#7a3a2a'); // serrucho
  def('mesa', mesa.contorno());

  const horno = (f) => {
    const L = rej({ ...PIEDRA_OBJ, ...FUEGO }, `
      .....PqqP.....
      .....pPPp.....
      ...PPqqqqPP...
      ..PqqPPPPqqP..
      .PqPPppppPPqP.
      .PPppQQQQppPP.
      PqPpQkkkkQpPqP
      PPpQ${f[0]}QpPP
      PqpQ${f[1]}QpqP
      PPpQ${f[2]}QpPP
      pPppQQQQQQppPp
      pppPPPPPPPPppp
      QpppppppppppQ.`);
    return L.contorno();
  };
  const fh = [['kkoykk', 'kroyok', 'oywwyo'], ['kkyokk', 'koyrok', 'oywwyo']];
  fh.forEach((f, i) => { const L = horno(f); def(`horno_${i}`, L); def(`horno_${i}_luz`, L.solo(Object.values(FUEGO))); });

  const YUN = { i: '#4a5468', I: '#76839c', j: '#b9c6da', ...MADERA };
  def('yunque', rej(YUN, `
    jjjjjjjjjjjjjj
    IIIIIIIIIIIIIi
    .iIIIIIIIIIii.
    .....IIIIi....
    ....iIIIIii...
    ..WWwwwwwwWW..
    ..dWWWWWWWWd..
    ..dddddddddd..`).contorno());

  const LAMP = { ...CRISTAL, i: '#3c4658', I: '#68758e' };
  const lampara = rej(LAMP, `
    ..ll..
    .lZZl.
    lZZlZz
    .ZzZz.
    .iIIi.
    ..Ii..
    ..Ii..
    ..Ii..
    ..Ii..
    .iIIi.
    iiiiii`).contorno();
  def('lampara', lampara); def('lampara_luz', lampara.solo(Object.values(CRISTAL)));

  def('raiz', rej(MADERA, `
    .....eWW........
    ....eWWWd.......
    ....WWWdd..eW...
    ...eWWWdd.eWWd..
    ...WWWddWWWdd...
    ..eWWWdddWdd....
    .eWWWWWddd......
    eWWWwWWWddd..eW.
    WWwWWWWWWdddWWd.
    .dWWWWWWWWWWdd..
    ..ddDDDDDDdDD...
    ...D..D..D......`).contorno());

  const ARB = { v: '#24453a', V: '#356b4f', u: '#4f9a64', B: '#58c8ff', b: '#d6f6ff' };
  const arbusto = rej(ARB, `
    ....VVuV.....
    ..VVuuVVVv...
    .VuuVBbVVVv..
    VuuVVBBVuVVv.
    VVVbVVVVuuVv.
    vVBBVuVVVBbVv
    vVVVVVVVVBBvv
    .vvVVVvVVVvv.
    ..vvvvvvvv...`).contorno();
  def('arbusto', arbusto); def('arbusto_luz', arbusto.solo([ARB.B, ARB.b]));

  const HON = { ...CRISTAL, c: '#e9e0c6', C: '#b7aa8a', s: '#ffffff' };
  const hongo = rej(HON, `
    ....lllll...
    ..llZZZZZll.
    .lZZsZZZZZZl
    lZZZZZZzsZZz
    zZzZZZZZZzzz
    .zzzzzzzzzz.
    ....cCc.....
    ....cCc.....
    ...ccCcc....
    ..cccCCcc...`).contorno();
  def('hongo', hongo); def('hongo_luz', hongo.solo(Object.values(CRISTAL).concat(HON.s)));

  const cristal = rej({ ...CRISTAL, ...PIEDRA_OBJ }, `
    ......l.....
    .....lZz....
    ..l..lZz....
    .lZz.ZZz.l..
    .lZz.ZZzlZz.
    .ZZz.lZzZZz.
    .ZZzlZZzZZz.
    .lZzZZZzZzz.
    PpZzZZZzZzpP
    qPppZzzzppPP
    .qPPpppppPP.`).contorno();
  def('cristal', cristal); def('cristal_luz', cristal.solo(Object.values(CRISTAL)));

  // El Corazón: un cristal en forma de corazón sobre un altar en ruinas.
  const COR = { Q: '#5e1430', R: '#b02a58', S: '#ee5a86', V: '#ffb3c8', w: '#fff0f4' };
  const corMitad = rej(COR, `
    ...QQQQ.....
    ..QRSSRQ....
    .QRSVVSRQ...
    QRSVwVSSRQ..
    QRSVVSSRRQQQ
    QRSSSSRRRRRS
    QRRSSRRRRRSS
    .QRRRRRRRRSS
    .QQRRRRRRRSS
    ..QRRRRRRRSS
    ...QRRRRRRRS
    ....QQRRRRRR
    ......QRRRRR
    .......QQRRR
    .........QQR
    ..........QQ`);
  const corazon = corMitad.espejo().contorno('#2a0a18');
  def('corazon', corazon);
  def('corazon_dormido', corazon.cambia({ [COR.S]: COR.R, [COR.V]: COR.S, [COR.w]: COR.V, [COR.R]: '#7a1e42' }));
  const RU = { a: RAMPA.ruina[1], b: RAMPA.ruina[2], c: RAMPA.ruina[3], d: RAMPA.ruina[4], m: '#467a48' };
  def('altar', rej(RU, `
    ....dddddddddddddddddddddd....
    ...dccccccccccccccccccccccd...
    ..dcbbbbbbbbbbbbbbbbbbbbbbcd..
    ..cbbbbbbbbbbbbbbbbbbbbbbbbc..
    .ddddddddddddddddddddddddddddd.
    .cccccccccccccccccccccccccccccc
    .bbbbabbbbbbbabbbbbbbbabbbbbbb.
    .bmbbabbbbbbbabbbbbmbbabbbbbbb.
    .aaaaaaaaaaaaaaaaaaaaaaaaaaaaa.`).contorno());
  const PED = { ...RU, Q: COR.Q, R: COR.R, S: COR.S, V: COR.V };
  const pedestal = (hueco) => rej(PED, `
    .dddddddd.
    dc${hueco[0]}cd
    dc${hueco[1]}cd
    .cccccccc.
    ..bbbbbb..
    ..bdbbab..
    ..bbbbab..
    ..bdbbab..
    ..bbbbab..
    .cccccccc.
    dbbbbbbbbd
    aaaaaaaaaa`).contorno();
  def('pedestal', pedestal(['aaaaaa', 'aaaaaa']));
  const lleno = pedestal(['aSVSaa', 'aRSRaa']);
  def('pedestal_lleno', lleno); def('pedestal_lleno_luz', lleno.solo([COR.S, COR.V, COR.R]));
}

// ---------------------------------------------------------------- objetos del inventario (iconos)

const METAL = {
  viejo: { H: '#8c8478', J: '#c2b8a6', D: '#5a5248' },
  madera: { H: '#b07a48', J: '#dcae7a', D: '#704626' },
  cobre: { H: '#c8693a', J: '#f2a466', D: '#7c3a22' },
  estano: { H: '#a9a6c6', J: '#ebe9fb', D: '#5f5c7a' },
  hierro: { H: '#6787a6', J: '#b9d6ec', D: '#2c3a4f' },
  topo: { H: '#ffd0c0', J: '#ffffff', D: '#c9806e' },
};
const MANGO = { h: '#8a5a36', g: '#d9a441', G: '#8f6420', p: '#5a3a26' };

function construirIconos() {
  const DE = { pico: ['viejo', 'cobre', 'estano', 'hierro', 'topo'], espada: ['madera', 'cobre', 'estano', 'hierro'] };
  for (const [t, m] of Object.entries(METAL)) {
    const P = { ...m, ...MANGO };
    if (DE.pico.includes(t)) def(`i_pico_${t}`, rej(P, `
      ..JJJH....
      .HHDDHH...
      .D...hHH..
      ....h.DH..
      ...h...DH.
      ..h.....D.
      .h........
      p.........`).contorno());
    if (DE.espada.includes(t)) def(`i_espada_${t}`, rej(P, `
      ........JJ
      .......JHH
      ......JHD.
      .....JHD..
      ....JHD...
      .G.JHD....
      ..GHD.....
      ..hGG.....
      .h..G.....
      p.........`).contorno());
    if (!['cobre', 'estano', 'hierro'].includes(t)) continue;
    def(`i_armadura_${t}`, rej(P, `
      .JH....HJ.
      JHHH..HHHD
      HHJHHHHHHD
      .HJHHHHHD.
      .HHHHHHHD.
      .HHHHHHDD.
      ..HHHHDD..`).contorno());
    def(`i_lingote_${t}`, rej(P, `
      ...JJJJJJ.
      ..JJJJJJHD
      .HHHHHHHDD
      HHHHHHHDD.
      DDDDDDDD..`).contorno());
    def(`i_mineral_${t}`, rej({ ...P, p: '#5d6070', P: '#7f8392', q: '#a5a9b6' }, `
      ...qqPP...
      .qPPJHPPp.
      qPJHDPPPpp
      PPHDPPJHpp
      PPPPPPHDPp
      .pPJHPPPp.
      ..ppppppp.`).contorno());
  }
  def('i_madera', rej(MADERA, `
    ....WWWWWee.
    ..WWwWWWeWWe
    WWWWWWWWeWWe
    WdWWWWdWWeed
    dddWddddddd.`).contorno());
  def('i_baba', rej({ g: '#86d64f', G: '#56a83b', d: '#2f6e35', w: '#effed8' }, `
    ...gggg..
    .gwwgggG.
    gwggggGGd
    gggggGGGd
    .GGGGGGd.
    ..ddddd..`).contorno());
  def('i_cristal', rej(CRISTAL, `
    ..l...
    .lZz..
    .lZz..
    lZZzz.
    lZZzz.
    .Zzz..
    ..z...`).contorno());
  def('i_baya', rej({ B: '#58c8ff', b: '#d6f6ff', D: '#2f7fb8', v: '#356b4f' }, `
    ...vv..
    ..v....
    .BbDBb.
    BbBBbBD
    BBDBBDD
    .DD.DD.`).contorno());
  def('i_hongo', rej({ ...CRISTAL, c: '#e9e0c6', C: '#b7aa8a' }, `
    ..llll..
    .lZZZZl.
    lZZZZZZz
    zzzzzzzz
    ...cC...
    ...cC...
    ..ccCc..`).contorno());
  def('i_hongo_asado', rej({ z: '#8a5a3a', Z: '#c08550', l: '#e8b878', c: '#d9c8a0', C: '#9a8a6a' }, `
    ..llll..
    .lZZZZl.
    lZZZZZZz
    zzzzzzzz
    ...cC...
    ...cC...
    ..ccCc..`).contorno());
  def('i_carne', rej({ c: '#f2c6b4', C: '#d88f80', r: '#b24a4a', w: '#fff4ea' }, `
    ..cccc..
    .cwcccC.
    cccCCCCr
    cCCCrrrr
    .Crrrrr.
    ..rrrr..`).contorno());
  def('i_carne_asada', rej({ c: '#c9844a', C: '#9a5a32', r: '#6e3a20', w: '#f0c080' }, `
    ..cccc..
    .cwcccC.
    cccCCCCr
    cCCCrrrr
    .Crrrrr.
    ..rrrr..`).contorno());
  def('i_fragmento', rej({ Q: '#5e1430', R: '#b02a58', S: '#ee5a86', V: '#ffb3c8', w: '#fff0f4' }, `
    ...VS..
    ..VwSR.
    .VVSSRQ
    SSSRRQ.
    .RRRQ..
    ..RQ...`).contorno());
  def('i_antorcha', rej({ ...MADERA, ...FUEGO }, `
    ..r..
    .ror.
    .oyo.
    .DDD.
    ..W..
    ..d..
    ..d..`).contorno());
  def('i_mesa', rej(MADERA, `
    eeeeeeeeee
    WWWWdWWWWW
    WWWWdWWWWW
    dddddddddd
    D.D....D.D
    D......D..`).contorno());
  def('i_horno', rej({ ...PIEDRA_OBJ, ...FUEGO }, `
    ...PqqP...
    ..PqPPqP..
    .PqppppqP.
    PpQkkkkQpP
    PpQoyyoQpP
    PpQyowyQpP
    pppppppppp`).contorno());
  def('i_yunque', rej({ i: '#4a5468', I: '#76839c', j: '#b9c6da', ...MADERA }, `
    jjjjjjjjjj
    IIIIIIIIIi
    ...IIIi...
    ..iIIIii..
    .WWwwwwWW.
    .dddddddd.`).contorno());
  def('i_lampara', rej({ ...CRISTAL, i: '#3c4658', I: '#68758e' }, `
    .ll.
    lZZz
    .Zz.
    .Ii.
    .Ii.
    .Ii.
    iIIi`).contorno());

  // interfaz
  def('ui_mochila', rej({ b: '#c28a52', B: '#8a5a36', D: '#5a3a26', y: '#f2c14e' }, `
    ...BBBB...
    ..B....B..
    .bbbbbbbb.
    bbbbyybbbb
    bBBByyBBBb
    bbbbbbbbbb
    bbbbbbbbbb
    BBBBBBBBBB
    .DDDDDDDD.`).contorno());
  def('ui_crear', rej({ s: '#b9c4d2', S: '#7d889a', h: '#c28a52', H: '#8a5a36' }, `
    .sssss....
    sSSSSSs...
    .SSSSSh...
    .....h....
    ....h.....
    ...h......
    ..H.......
    .H........`).contorno());
  def('ui_mapa', rej({ p: '#e8d8b0', P: '#b8a07a', r: '#e43b44', l: '#7a6a5a' }, `
    PpppppppP.
    PplllpppP.
    Pppplppr.P
    PpppplppP.
    PppppplpP.
    PpprpppppP
    PppplllpP.
    PpppppppP.`).contorno());
  def('ui_pausa', rej({ w: '#e8e0f0' }, `
    ww.ww
    ww.ww
    ww.ww
    ww.ww
    ww.ww`).contorno());
  def('ui_vida', rej({ r: '#e43b44', R: '#a3203a', w: '#ffc0c8' }, `
    .rr.rr.
    rwrrrrR
    rrrrrRR
    .rrrRR.
    ..rRR..
    ...R...`).contorno());
  def('ui_hambre', rej({ c: '#e8a050', C: '#b06a30', h: '#f2e0c8', H: '#c8b8a0' }, `
    ..ccc..
    .ccccC.
    .cccCC.
    ..cCC..
    ...hH..
    ...hH..
    ..hHHh.`).contorno());
  def('ui_escudo', rej({ s: '#9aa6b8', S: '#5d6b80', w: '#d5dde8' }, `
    sssssss
    swsssSS
    sssssSS
    .sssSS.
    ..sSS..
    ...S...`).contorno());
  def('ui_calavera', rej({ w: '#f0e8dc', W: '#b8ae9e', k: '#14101c' }, `
    .wwwww.
    wwwwwwW
    wkkwkkW
    wkkwkkW
    .wwkwW.
    .w.w.W.`).contorno());
  def('ui_sonido', rej({ w: '#e8e0f0' }, `
    ...w..
    ..ww.w
    wwww..
    wwww.w
    ..ww..
    ...w.w`).contorno());
}

// ---------------------------------------------------------------- atlas

const SPR = {};
let ATLAS = null;

function construirArte() {
  construirTerreno(); construirJugador(); construirCriaturas(); construirObjetos(); construirIconos();
  // Empaquetado en estantes, de más alto a más bajo.
  const lista = DIBUJOS.slice().sort((a, b) => b[1].h - a[1].h || b[1].w - a[1].w);
  const ANCHO = 512;
  let x = 0, y = 0, alto = 0;
  for (const [n, l] of lista) {
    if (x + l.w > ANCHO) { x = 0; y += alto + 1; alto = 0; }
    SPR[n] = { x, y, w: l.w, h: l.h };
    x += l.w + 1; alto = Math.max(alto, l.h);
  }
  const H = y + alto;
  ATLAS = document.createElement('canvas');
  ATLAS.width = ANCHO; ATLAS.height = H;
  const cx = ATLAS.getContext('2d'), img = cx.createImageData(ANCHO, H), d = img.data;
  const cache = new Map();
  const rgba = (c) => {
    let v = cache.get(c);
    if (!v) {
      const h = c.slice(1);
      v = [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16), h.length >= 8 ? parseInt(h.slice(6, 8), 16) : 255];
      cache.set(c, v);
    }
    return v;
  };
  for (const [n, l] of lista) {
    const r = SPR[n];
    for (let j = 0; j < l.h; j++) for (let i = 0; i < l.w; i++) {
      const c = l.d[j * l.w + i]; if (!c) continue;
      const v = rgba(c), o = ((r.y + j) * ANCHO + r.x + i) * 4;
      d[o] = v[0]; d[o + 1] = v[1]; d[o + 2] = v[2]; d[o + 3] = v[3];
    }
  }
  cx.putImageData(img, 0, 0);
  DIBUJOS.length = 0;
}

// Dibuja un sprite con su esquina arriba-izquierda en (x, y).
function spr(ctx, n, x, y) {
  const r = SPR[n];
  if (!r) return;
  ctx.drawImage(ATLAS, r.x, r.y, r.w, r.h, Math.round(x), Math.round(y), r.w, r.h);
}
// Centrado en x, con los pies en y.
function sprPie(ctx, n, x, y) {
  const r = SPR[n];
  if (r) ctx.drawImage(ATLAS, r.x, r.y, r.w, r.h, Math.round(x - r.w / 2), Math.round(y - r.h), r.w, r.h);
}
function sprCentro(ctx, n, x, y) {
  const r = SPR[n];
  if (r) ctx.drawImage(ATLAS, r.x, r.y, r.w, r.h, Math.round(x - r.w / 2), Math.round(y - r.h / 2), r.w, r.h);
}
