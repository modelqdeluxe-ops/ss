'use strict';
// Arte de Corazón Dormido, dibujado con código al estilo del skill pxl (.claude/skills/pxl-pixel-art):
// cuadrículas y figuras con su paleta, espejo, contorno de color y variantes por cambio de color.
// Volumen: las formas redondas se sombrean como esferas (luz arriba a la izquierda) en 4-6 tonos.
// Las texturas del suelo encajan entre casillas. Al arrancar se pinta todo en una sola hoja (atlas).

const TINTA = '#170e14';

const hexRgb = (h) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
const rgbHex = (c) => '#' + c.map((v) => Math.max(0, Math.min(255, Math.round(v))).toString(16).padStart(2, '0')).join('');
const mezcla = (a, b, t) => { const x = hexRgb(a), y = hexRgb(b); return rgbHex(x.map((v, i) => v + (y[i] - v) * t)); };

class Lienzo {
  constructor(w, h) { this.w = w; this.h = h; this.d = new Array(w * h).fill(null); }
  dentro(x, y) { return x >= 0 && y >= 0 && x < this.w && y < this.h; }
  get(x, y) { return this.dentro(x, y) ? this.d[y * this.w + x] : null; }
  px(x, y, c) {
    x = Math.round(x); y = Math.round(y);
    if (c && this.dentro(x, y)) this.d[y * this.w + x] = c === '_' ? null : c;
    return this;
  }
  // como px, pero da la vuelta por los bordes (texturas que encajan)
  pxc(x, y, c) { return this.px(((Math.round(x) % this.w) + this.w) % this.w, ((Math.round(y) % this.h) + this.h) % this.h, c); }
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
  elipse(cx, cy, rx, ry, c) {
    for (let y = Math.floor(cy - ry - 1); y <= cy + ry + 1; y++) for (let x = Math.floor(cx - rx - 1); x <= cx + rx + 1; x++) {
      const nx = (x + 0.5 - cx) / rx, ny = (y + 0.5 - cy) / ry;
      if (nx * nx + ny * ny <= 1) this.px(x, y, c);
    }
    return this;
  }
  // Elipse con volumen: rampa de oscuro a claro; brillo: color del punto de luz.
  esfera(cx, cy, rx, ry, rampa, o = {}) {
    const lx = -0.5, ly = -0.62, lz = 0.6, amb = o.amb ?? 0.3;
    for (let y = Math.floor(cy - ry - 1); y <= cy + ry + 1; y++) for (let x = Math.floor(cx - rx - 1); x <= cx + rx + 1; x++) {
      const nx = (x + 0.5 - cx) / rx, ny = (y + 0.5 - cy) / ry, d = nx * nx + ny * ny;
      if (d > 1 || (o.donde && !o.donde(x, y))) continue;
      const nz = Math.sqrt(1 - d);
      const lum = Math.max(0, (nx * lx + ny * ly + nz * lz + amb) / (1 + amb));
      this.px(x, y, rampa[Math.max(0, Math.min(rampa.length - 1, Math.floor(lum * rampa.length * 0.999)))]);
    }
    if (o.brillo) {
      const bx = Math.round(cx - rx * 0.42 - 0.5), by = Math.round(cy - ry * 0.5 - 0.5);
      this.px(bx, by, o.brillo); if (rx > 3) this.px(bx + 1, by, o.brillo); if (ry > 4) this.px(bx, by + 1, o.brillo);
    }
    return this;
  }
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
  espejo(centro = false) {
    const w = centro ? this.w * 2 - 1 : this.w * 2, l = new Lienzo(w, this.h);
    l.sello(this, 0, 0);
    for (let y = 0; y < this.h; y++) for (let x = 0; x < this.w; x++) { const c = this.get(x, y); if (c) l.px(w - 1 - x, y, c); }
    return l;
  }
  voltear() { const l = new Lienzo(this.w, this.h); for (let y = 0; y < this.h; y++) for (let x = 0; x < this.w; x++) l.d[y * this.w + this.w - 1 - x] = this.get(x, y); return l; }
  cambia(mapa) { const l = this.copia(); l.d = l.d.map((c) => (c && mapa[c]) || c); return l; }
  // Contorno de color: cada borde toma el color de al lado, oscurecido (como en Core Keeper).
  contorno(oscuro = TINTA, t = 0.78) {
    const l = new Lienzo(this.w + 2, this.h + 2);
    l.sello(this, 1, 1);
    for (let y = 0; y < l.h; y++) for (let x = 0; x < l.w; x++) {
      if (this.get(x - 1, y - 1)) continue;
      let vec = null;
      for (const [dx, dy] of [[0, 1], [1, 0], [-1, 0], [0, -1]]) { const c = this.get(x - 1 + dx, y - 1 + dy); if (c) { vec = c; break; } }
      if (vec) l.d[y * l.w + x] = mezcla(vec.slice(0, 7), oscuro, t);
    }
    return l;
  }
  solo(cols) { const l = this.copia(); const s = new Set(cols); l.d = l.d.map((c) => (s.has(c) ? c : null)); return l; }
  blanco() { const l = this.copia(); l.d = l.d.map((c) => (c ? '#ffffff' : null)); return l; }
  // desplaza una franja de filas (para el balanceo al andar)
  subir(y0, y1, dy) {
    const l = new Lienzo(this.w, this.h);
    for (let y = 0; y < this.h; y++) for (let x = 0; x < this.w; x++) { const c = this.get(x, y); if (!c) continue; l.px(x, y >= y0 && y <= y1 ? y + dy : y, c); }
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
const cambioPal = (a, b) => Object.fromEntries(Object.keys(b).filter((k) => a[k]).map((k) => [a[k], b[k]]));
// Ruido suave que da la vuelta cada 16 px (para texturas que encajan).
function ruidoCiclico(sem, celdas = 4, lado = 16) {
  const r = azar(sem), g = Array.from({ length: celdas * celdas }, r);
  const v = (i, j) => g[(((j % celdas) + celdas) % celdas) * celdas + (((i % celdas) + celdas) % celdas)];
  const s = (t) => t * t * (3 - 2 * t);
  return (x, y) => {
    const fx = (x / lado) * celdas, fy = (y / lado) * celdas, i = Math.floor(fx), j = Math.floor(fy), u = s(fx - i), w = s(fy - j);
    const a = v(i, j), b = v(i + 1, j), c = v(i, j + 1), d = v(i + 1, j + 1);
    return a + (b - a) * u + (c - a) * w + (a - b - c + d) * u * w;
  };
}

const DIBUJOS = [];
function def(nombre, l) { DIBUJOS.push([nombre, l]); return l; }

// ---------------------------------------------------------------- terreno

// Rampas de oscuro a claro.
const RAMPA = {
  tierra: ['#24150f', '#3a2318', '#563524', '#734a32', '#93633f', '#b07d50'],
  arcilla: ['#2c1210', '#4a1e17', '#6e2e20', '#91432b', '#b45c38', '#d27a4c'],
  piedra: ['#15161f', '#252a37', '#373e51', '#4c566c', '#66718a', '#8591a8'],
  ruina: ['#101a20', '#1d2e36', '#2c4550', '#3e5f6b', '#56808c', '#78a3ad'],
  roca: ['#08070b', '#0f0d14', '#17141e', '#211d2a', '#2c2737', '#383246'],
};
const BORDE_MURO = '#0b0709';
const RAIZ = ['#5a3a1c', '#86592a', '#b3823a', '#dcae55'];

const SUELO = {
  tierra: { os: '#5a3826', base: '#6c4630', cl: '#7c5338', br: '#9a6c48', pd: '#46291c' },
  arcilla: { os: '#6e3424', base: '#80402c', cl: '#904b33', br: '#ad6544', pd: '#541f17' },
  piedra: { os: '#353b4b', base: '#404759', cl: '#4b5368', br: '#626c84', pd: '#262a37' },
  ruina: { os: '#2d3a44', base: '#425461', cl: '#4d616f', br: '#6a8392', pd: '#1f2930' },
  arena: { os: '#4e3122', base: '#5e3c2a', cl: '#6c4631', br: '#8a5e40', pd: '#3a2218' },
};

function suelo(tipo, v) {
  const S = SUELO[tipo], sem = 900 + v * 37 + tipo.length * 311, r = azar(sem);
  const L = new Lienzo(16, 16).rect(0, 0, 16, 16, S.base);
  if (tipo === 'ruina') {
    // losa de piedra con su marco, como las del santuario de Core Keeper
    L.rect(0, 0, 16, 16, S.cl);
    L.hl(0, 0, 16, S.pd); L.vl(0, 0, 16, S.pd);
    L.hl(1, 1, 15, S.br); L.vl(1, 1, 15, S.br);
    L.hl(1, 15, 15, S.os); L.vl(15, 1, 15, S.os);
    L.rect(4, 4, 8, 8, S.base); L.hl(4, 4, 8, S.os); L.vl(4, 4, 8, S.os); L.hl(5, 11, 7, S.br); L.vl(11, 5, 7, S.br);
    if (v === 1) { let x = entre(r, 3, 10), y = 1; for (let k = 0; k < 12; k++) { L.px(x, y, S.pd); y++; if (r() < 0.4) x += entre(r, -1, 1); } }
    if (v === 2) for (let k = 0; k < 5; k++) { const x = entre(r, 1, 14), y = entre(r, 1, 14); L.px(x, y, '#3f6b47'); L.px(x + 1, y, '#5c8f52'); }
    if (v === 3) { L.rect(6, 6, 4, 4, S.os); L.px(7, 7, S.br); L.px(8, 8, S.pd); }
    return L;
  }
  const n = ruidoCiclico(sem, 4), n2 = ruidoCiclico(sem + 7, 8);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const f = n(x, y) * 0.65 + n2(x, y) * 0.35;
    if (f < 0.3) L.px(x, y, S.os); else if (f > 0.7) L.px(x, y, S.cl);
  }
  if (tipo === 'arcilla') { // barro seco cuarteado
    let x = entre(r, 0, 15), y = 0;
    for (let k = 0; k < 16; k++) { L.pxc(x, y, S.pd); L.pxc(x + 1, y, S.os); y++; x += entre(r, -1, 1); }
    let x2 = 0, y2 = entre(r, 3, 12);
    for (let k = 0; k < 16; k++) { L.pxc(x2, y2, S.pd); L.pxc(x2, y2 + 1, S.os); x2++; if (r() < 0.35) y2 += entre(r, -1, 1); }
  }
  if (tipo === 'piedra') { // losas naturales
    for (let k = 0; k < 2; k++) {
      let x = entre(r, 0, 15), y = entre(r, 0, 15), dx = r() < 0.5 ? 1 : 0;
      for (let s = 0; s < 9; s++) { L.pxc(x, y, S.pd); L.pxc(x + (dx ? 0 : 1), y + (dx ? 1 : 0), S.br); if (dx) x++; else y++; if (r() < 0.3) dx = 1 - dx; }
    }
  }
  const piedras = tipo === 'piedra' ? 1 : 3;
  for (let k = 0; k < piedras; k++) { // piedrecitas con su luz y su sombra
    const x = entre(r, 0, 15), y = entre(r, 0, 15);
    L.pxc(x, y, S.br); L.pxc(x + 1, y, S.cl); L.pxc(x, y + 1, S.cl); L.pxc(x + 1, y + 1, S.os); L.pxc(x + 1, y + 2, S.pd); L.pxc(x + 2, y + 1, S.pd);
  }
  for (let k = 0; k < 4; k++) L.pxc(entre(r, 0, 15), entre(r, 0, 15), S.pd);
  if (tipo === 'arena' && v < 2) { // arañazos del topo
    const x = entre(r, 2, 8), y = entre(r, 2, 8);
    for (let k = 0; k < 3; k++) { L.linea(x + k * 3, y, x + k * 3 + 3, y + 5, S.pd); L.linea(x + k * 3 + 1, y, x + k * 3 + 4, y + 5, S.os); }
  }
  return L;
}

// Tapa de una pared (16×16). m: 1 norte, 2 este, 4 sur, 8 oeste abiertos.
function muroArriba(tipo, m, v) {
  const R = RAMPA[tipo], r = azar(101 + v * 131 + tipo.length * 977);
  const L = new Lienzo(16, 16).rect(0, 0, 16, 16, R[2]);
  if (tipo === 'ruina') {
    L.rect(0, 0, 16, 16, R[2]);
    for (let y = 0; y < 16; y += 4) { L.hl(0, y + 3, 16, R[1]); for (let x = (y / 4) % 2 ? 4 : 0; x < 16; x += 8) L.vl(x, y, 3, R[1]); L.hl(0, y, 16, R[3]); }
  } else if (tipo === 'arcilla') {
    const n = ruidoCiclico(sem2(tipo, v), 4);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const f = n(x, y * 0.4) + y * 0.02; L.px(x, y, f < 0.38 ? R[1] : f > 0.66 ? R[3] : R[2]); }
  } else {
    // bloques de roca (voronoi) con grietas oscuras y canto claro
    const pts = Array.from({ length: tipo === 'piedra' ? 6 : 8 }, () => [r() * 16, r() * 16, r()]);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      let d1 = 99, d2 = 99, c = null;
      for (const p of pts) for (const [ox, oy] of [[0, 0], [16, 0], [-16, 0], [0, 16], [0, -16]]) {
        const d = Math.hypot(x + 0.5 - p[0] - ox, y + 0.5 - p[1] - oy);
        if (d < d1) { d2 = d1; d1 = d; c = p; } else if (d < d2) d2 = d;
      }
      if (d2 - d1 < 1) L.px(x, y, R[1]);
      else L.px(x, y, c[2] < 0.35 ? R[2] : c[2] < 0.75 ? mezcla(R[2], R[3], 0.5) : R[3]);
    }
    for (let y = 1; y < 16; y++) for (let x = 1; x < 16; x++) if (L.get(x - 1, y - 1) === R[1] && L.get(x, y) !== R[1] && r() < 0.6) L.px(x, y, R[4]);
  }
  const N = m & 1, E = m & 2, S = m & 4, O = m & 8;
  if (N) { L.hl(0, 0, 16, BORDE_MURO); L.hl(0, 1, 16, R[1]); }
  if (O) { L.vl(0, 0, 16, BORDE_MURO); L.vl(1, 0, 16, mezcla(R[2], R[3], 0.5)); }
  if (E) { L.vl(15, 0, 16, BORDE_MURO); L.vl(14, 0, 16, R[1]); }
  if (S) { L.hl(0, 14, 16, R[4]); L.hl(0, 15, 16, R[5]); if (O) L.px(0, 15, BORDE_MURO); if (E) L.px(15, 15, BORDE_MURO); }
  if (N && O) { L.px(0, 0, '_'); L.px(1, 1, BORDE_MURO); }
  if (N && E) { L.px(15, 0, '_'); L.px(14, 1, BORDE_MURO); }
  return L;
}
function sem2(tipo, v) { return 333 + v * 71 + tipo.length * 19; }

// Cara de delante de una pared (16×12). lados: 1 oeste abierto, 2 este abierto.
function muroCara(tipo, lados, v) {
  const R = RAMPA[tipo], r = azar(55 + v * 71 + tipo.length * 31);
  const L = new Lienzo(16, 12).rect(0, 0, 16, 12, R[3]);
  if (tipo === 'tierra') {
    const n = ruidoCiclico(sem2(tipo, v) + 5, 4, 16);
    for (let y = 0; y < 12; y++) for (let x = 0; x < 16; x++) { const f = n(x, y * 1.3); if (f < 0.35) L.px(x, y, R[2]); else if (f > 0.72) L.px(x, y, R[4]); }
    for (const y0 of [4, 8]) { let y = y0 + entre(r, -1, 0); for (let x = 0; x < 16; x++) { L.px(x, y, R[2]); L.px(x, y - 1, R[4]); if (r() < 0.25) y += entre(r, -1, 1); y = Math.max(2, Math.min(10, y)); } }
    for (let k = 0; k < 3; k++) { const x = entre(r, 1, 13), y = entre(r, 2, 9); L.px(x, y, R[5]); L.px(x + 1, y, R[4]); L.px(x + 1, y + 1, R[1]); }
    if (v === 1) { // una raíz que asoma
      let x = entre(r, 4, 11);
      for (let y = 0; y < 12; y++) { L.px(x, y, RAIZ[2]); L.px(x + 1, y, RAIZ[1]); L.px(x - 1, y, RAIZ[3]); if (y > 3 && r() < 0.35) x += entre(r, -1, 1); x = Math.max(2, Math.min(13, x)); }
      L.px(x + 2, 8, RAIZ[2]); L.px(x + 3, 9, RAIZ[1]);
    }
  } else if (tipo === 'arcilla') {
    const bandas = [R[4], R[3], R[2], R[3], R[4], R[2]];
    let y = 0, k = 0;
    while (y < 12) { const h = entre(r, 2, 3); for (let x = 0; x < 16; x++) { const o = Math.round(Math.sin((x + v * 5 + k * 3) * 0.7) * 0.6); for (let j = 0; j < h; j++) L.px(x, y + j + o, bandas[k % bandas.length]); } y += h; k++; }
    for (let x = 0; x < 16; x++) L.px(x, 0, R[4]);
  } else if (tipo === 'piedra' || tipo === 'roca') {
    for (const [y0, h, off] of [[0, 6, v * 3], [6, 6, 5 + v * 2]]) {
      for (let x0 = -16 + off; x0 < 16; x0 += 8) {
        L.rect(x0, y0, 8, h, R[3]); L.hl(x0, y0, 8, R[4]); L.vl(x0, y0, h, R[4]); L.hl(x0, y0 + h - 1, 8, R[1]); L.vl(x0 + 7, y0, h, R[1]); L.px(x0 + 1, y0 + 1, R[5]);
        if (r() < 0.4) L.px(x0 + entre(r, 2, 5), y0 + entre(r, 2, 3), R[2]);
      }
    }
  } else if (tipo === 'ruina') {
    for (let y0 = 0; y0 < 12; y0 += 4) for (let x0 = (y0 / 4) % 2 ? -4 : 0; x0 < 16; x0 += 8) {
      L.rect(x0, y0, 8, 4, R[3]); L.hl(x0, y0, 8, R[4]); L.hl(x0, y0 + 3, 8, R[1]); L.vl(x0 + 7, y0, 4, R[1]);
    }
    if (v === 1) { L.rect(6, 4, 4, 4, R[1]); L.px(7, 5, '#5fd0e0'); L.px(8, 6, '#5fd0e0'); }
  }
  L.hl(0, 11, 16, R[1]);
  if (lados & 1) L.vl(0, 0, 12, BORDE_MURO);
  if (lados & 2) L.vl(15, 0, 12, BORDE_MURO);
  return L;
}

const MENA_COL = {
  cobre: ['#5e2814', '#9c4a24', '#d07436', '#f4a866', '#fff0d0'],
  estano: ['#3e3a58', '#6e6a92', '#a4a2c8', '#d6d4f0', '#ffffff'],
  hierro: ['#1e2a3c', '#3c5672', '#6888aa', '#9fc4e2', '#f0fbff'],
};
function pepita(L, x, y, c, grande) {
  const [o, m, b, cl, br] = c;
  if (grande) { L.rect(x, y, 3, 2, b); L.px(x, y, cl); L.px(x + 1, y, cl); L.px(x + 2, y + 1, m); L.hl(x, y + 2, 3, o); L.px(x + 3, y + 1, o); L.px(x, y - 1, br); }
  else { L.px(x, y, cl); L.px(x + 1, y, b); L.px(x, y + 1, m); L.px(x + 1, y + 1, o); }
}
function menaArriba(ore, v) {
  const r = azar(400 + v * 19 + ore.length * 7), L = new Lienzo(16, 16);
  for (let k = 0; k < 5; k++) pepita(L, entre(r, 2, 11), entre(r, 2, 11), MENA_COL[ore], k < 2);
  return L;
}
function menaCara(ore, v) {
  const r = azar(600 + v * 23 + ore.length), L = new Lienzo(16, 12);
  for (let k = 0; k < 3; k++) pepita(L, entre(r, 1, 12), entre(r, 2, 8), MENA_COL[ore], k === 0);
  return L;
}
function grieta(n) {
  const L = new Lienzo(16, 16), r = azar(77 + n), c = '#0a0608', c2 = '#00000000';
  const ramas = [[8, 8]];
  for (let k = 0; k < n * 3; k++) {
    let [x, y] = ramas[entre(r, 0, ramas.length - 1)];
    const dx = entre(r, -1, 1) || 1, dy = entre(r, -1, 1);
    for (let i = 0; i < 2 + n; i++) { x += dx; y += dy || (r() < 0.5 ? 1 : -1); L.px(x, y, c); }
    ramas.push([x, y]);
  }
  void c2;
  return L;
}

function construirTerreno() {
  for (const tipo of Object.keys(RAMPA)) {
    for (let m = 0; m < 16; m++) for (let v = 0; v < 2; v++) def(`m_${tipo}_${m}_${v}`, muroArriba(tipo, m, v));
    for (let l = 0; l < 4; l++) for (let v = 0; v < 2; v++) def(`c_${tipo}_${l}_${v}`, muroCara(tipo, l, v));
  }
  for (const tipo of Object.keys(SUELO)) for (let v = 0; v < 4; v++) def(`s_${tipo}_${v}`, suelo(tipo, v));
  for (const ore of Object.keys(MENA_COL)) for (let v = 0; v < 2; v++) { def(`mena_${ore}_${v}`, menaArriba(ore, v)); def(`menac_${ore}_${v}`, menaCara(ore, v)); }
  for (let n = 1; n <= 3; n++) def(`grieta_${n}`, grieta(n));

  // adornos tumbados en el suelo
  const AD = { p: '#8a6448', P: '#5a3e2c', q: '#b08a68', g: '#7aa05a', G: '#4d6e3c', h: '#ece0cc', H: '#a89a86', r: RAIZ[2], R: RAIZ[1], z: '#5ce0f0', Z: '#b8fcff', c: '#d8cfb8', m: '#3f6b47', M: '#5c8f52', k: '#3a2418', s: '#7c8aa0', S: '#4c566c' };
  def('ad_piedras', rej(AD, `
    .qp....
    qpPP.qp
    .PP..pP`));
  def('ad_terron', rej(AD, `
    .qpp.
    qppPP
    .PPk.`));
  def('ad_guijarros', rej(AD, `
    .s...s.
    sSS..SS
    .S.s...
    ...SS..`));
  def('ad_hierba', rej(AD, `
    .g...g..
    .gG.gG.g
    gG..G.gG
    G.gG..G.`));
  def('ad_huesos', rej(AD, `
    h.....h.
    Hhhhhhh.
    h.....H.
    ........
    ..hhh...
    .hHHHh..`));
  def('ad_raices', rej(AD, `
    r.......
    .rR..r..
    ..rrrR..
    ....rR.r
    .....rr.`));
  def('ad_hongos', rej(AD, `
    .Zz....
    zzzz.Zz
    .cc.zzz
    .cc..c.`));
  def('ad_musgo', rej(AD, `
    .mM.m.
    mMMmMm
    .mmM.m`));
  def('ad_garras', rej(AD, `
    k..k..k.
    .k..k..k
    .k..k..k
    ..k..k..`));
}

// ---------------------------------------------------------------- el personaje

const PJ = {
  pelo: ['#3a1d14', '#5e2f1c', '#874426', '#ad6232', '#d18a4c'],
  piel: '#f6cfac', pielS: '#df9f86', pielO: '#b8706a', mejilla: '#f29a8c',
  iris: '#3e9ec4', irisO: '#1d4a6e', brilloOjo: '#ffffff',
  tunica: ['#173a3e', '#1f5a58', '#2c8276', '#46ab8e'],
  bufanda: '#c7363f', bufandaS: '#8e2230', cinto: '#6a4029', hebilla: '#f2c14e',
  pant: '#3e2f4c', pantS: '#2a2034', bota: '#5a3424', botaS: '#3a2018', mochila: '#9a6a44', mochilaS: '#6e4a30', mochilaC: '#c08a58',
};

// Un cuadro del personaje: dir 'ab' | 'ar' | 'de'; paso 0..3
function cuerpoJugador(dir, paso) {
  const P = PJ, L = new Lienzo(14, 21);
  const y0 = paso % 2 ? 0 : 1; // balanceo: en los pasos el cuerpo sube 1 px
  // piernas y botas (no se mueven con el balanceo)
  const pierna = (x, alza) => {
    L.rect(x, 16 + y0 - (alza ? 1 : 0), 2, 3, P.pant); L.vl(x + 1, 16 + y0 - (alza ? 1 : 0), 3, P.pantS);
    L.rect(x, 19 - (alza ? 1 : 0), 2, 2 - (alza ? 1 : 0), P.bota); L.px(x + 1, 20 - (alza ? 1 : 0), P.botaS);
  };
  if (dir === 'de') {
    if (paso === 1) { pierna(3, false); pierna(8, true); } else if (paso === 3) { pierna(4, true); pierna(7, false); } else { pierna(5, false); pierna(7, false); }
  } else {
    pierna(4, paso === 1); pierna(8, paso === 3);
  }
  const Y = y0;
  // cuerpo
  if (dir === 'ar') {
    L.rect(3, 12 + Y, 8, 4, P.tunica[2]); L.vl(10, 12 + Y, 4, P.tunica[1]);
    L.rect(2, 12 + Y, 1, 3, P.tunica[1]); L.rect(11, 12 + Y, 1, 3, P.tunica[1]); L.px(2, 15 + Y, P.pielS); L.px(11, 15 + Y, P.pielS);
    L.rect(4, 11 + Y, 6, 5, P.mochila); L.hl(4, 11 + Y, 6, P.mochilaC); L.vl(9, 12 + Y, 4, P.mochilaS); L.hl(4, 15 + Y, 6, P.mochilaS); L.rect(6, 13 + Y, 2, 1, P.hebilla);
  } else if (dir === 'de') {
    L.rect(4, 12 + Y, 6, 4, P.tunica[2]); L.vl(4, 12 + Y, 4, P.tunica[3]); L.vl(9, 12 + Y, 4, P.tunica[1]);
    L.hl(4, 15 + Y, 6, P.cinto); L.px(8, 15 + Y, P.hebilla);
    L.rect(2, 12 + Y, 2, 3, P.mochila); L.vl(2, 12 + Y, 3, P.mochilaS); // mochila asomando
    L.rect(6, 12 + Y, 2, 3, P.tunica[1]); L.px(8, 14 + Y, P.piel); L.px(8, 15 + Y, P.pielS); // brazo
    L.hl(5, 11 + Y, 5, P.bufanda); L.px(9, 11 + Y, P.bufandaS); L.px(4, 12 + Y, P.bufanda); L.px(3, 13 + Y, P.bufandaS);
  } else {
    L.rect(3, 12 + Y, 8, 4, P.tunica[2]); L.vl(3, 12 + Y, 4, P.tunica[3]); L.vl(10, 12 + Y, 4, P.tunica[1]);
    L.hl(3, 15 + Y, 8, P.cinto); L.rect(6, 15 + Y, 2, 1, P.hebilla);
    L.vl(2, 12 + Y, 3, P.tunica[1]); L.vl(11, 12 + Y, 3, P.tunica[1]); L.px(2, 15 + Y, P.piel); L.px(11, 15 + Y, P.pielS);
    L.hl(4, 11 + Y, 6, P.bufanda); L.hl(4, 12 + Y, 2, P.bufanda); L.px(9, 11 + Y, P.bufandaS); L.px(5, 13 + Y, P.bufandaS); L.px(4, 13 + Y, P.bufanda);
  }
  // cabeza: una esfera de pelo grande y la cara encima
  const pelo = P.pelo;
  if (dir === 'ab') {
    L.esfera(7, 6.2 + Y, 7, 6.2, pelo, { brillo: pelo[4] });
    L.rect(3, 5 + Y, 8, 6, P.piel); L.hl(4, 11 + Y, 6, P.piel); L.vl(10, 6 + Y, 5, P.pielS); L.hl(4, 11 + Y, 6, P.pielS); L.px(3, 10 + Y, P.pielS);
    // flequillo
    L.hl(2, 4 + Y, 10, pelo[2]); L.hl(3, 5 + Y, 8, pelo[2]); L.px(4, 5 + Y, pelo[3]); L.px(7, 5 + Y, pelo[3]);
    for (const x of [3, 6, 9, 10]) L.px(x, 6 + Y, pelo[2]);
    L.px(5, 4 + Y, pelo[4]); L.px(8, 4 + Y, pelo[3]);
    L.vl(2, 5 + Y, 5, pelo[1]); L.vl(11, 5 + Y, 5, pelo[1]);
    // ojos grandes
    for (const ex of [4, 8]) { L.px(ex, 7 + Y, P.brilloOjo); L.px(ex + 1, 7 + Y, P.iris); L.px(ex, 8 + Y, P.iris); L.px(ex + 1, 8 + Y, P.irisO); }
    L.px(3, 9 + Y, P.mejilla); L.px(10, 9 + Y, P.mejilla);
    L.px(7, 10 + Y, P.pielO);
  } else if (dir === 'ar') {
    L.esfera(7, 6.2 + Y, 7, 6.2, pelo, { brillo: pelo[4] });
    for (const [x, y] of [[4, 9], [7, 10], [10, 9], [5, 6], [9, 7]]) L.px(x, y + Y, pelo[1]);
    L.hl(4, 11 + Y, 6, pelo[1]);
  } else {
    L.esfera(6.2, 6.2 + Y, 6.6, 6.2, pelo, { brillo: pelo[4] });
    L.rect(7, 5 + Y, 5, 6, P.piel); L.vl(12, 6 + Y, 3, P.piel); L.px(12, 9 + Y, P.pielS); L.hl(7, 10 + Y, 4, P.pielS); L.hl(8, 11 + Y, 3, P.pielS);
    L.hl(6, 4 + Y, 7, pelo[2]); L.hl(7, 5 + Y, 5, pelo[2]); L.px(8, 5 + Y, pelo[3]); L.px(11, 6 + Y, pelo[2]); L.px(12, 5 + Y, pelo[2]);
    L.vl(6, 5 + Y, 6, pelo[1]); L.px(7, 6 + Y, pelo[2]);
    L.px(10, 7 + Y, P.brilloOjo); L.px(11, 7 + Y, P.iris); L.px(10, 8 + Y, P.iris); L.px(11, 8 + Y, P.irisO);
    L.px(9, 9 + Y, P.mejilla);
  }
  return L.contorno();
}

function construirJugador() {
  for (const d of ['ab', 'ar', 'de']) for (let f = 0; f < 4; f++) {
    const L = cuerpoJugador(d, f);
    def(`jug_${d}_${f}`, L); def(`jugB_${d}_${f}`, L.blanco());
    if (d === 'de') { def(`jug_iz_${f}`, L.voltear()); def(`jugB_iz_${f}`, L.blanco().voltear()); }
  }
}

// ---------------------------------------------------------------- criaturas

function conBlanco(nombre, L, voltear = false) {
  def(nombre, L); def(nombre + '_B', L.blanco());
  if (voltear) { def(nombre + '_i', L.voltear()); def(nombre + '_iB', L.blanco().voltear()); }
}
const VERDE = ['#163a22', '#22592c', '#2f7f35', '#4aa83d', '#7fd056', '#bdf08a'];

function babosa(rx, ry, alto) {
  const L = new Lienzo(18, 14), cx = 9, base = 13, cy = base - ry;
  L.esfera(cx, cy, rx, ry, VERDE, { brillo: '#f0ffe0', amb: 0.35, donde: (x, y) => y < base });
  L.hl(Math.round(cx - rx + 2), base - 1, Math.round(rx * 2 - 4), VERDE[1]); // sombra de la base
  // ojos y boca
  const ey = Math.round(cy + ry * 0.05) - alto;
  for (const ex of [Math.round(cx - rx * 0.4) - 1, Math.round(cx + rx * 0.3)]) { L.vl(ex, ey, 2, TINTA); L.px(ex, ey, '#ffffff'); L.vl(ex + 1, ey, 2, TINTA); }
  L.px(Math.round(cx) - 1, ey + 3, VERDE[0]); L.px(Math.round(cx), ey + 3, VERDE[0]);
  L.px(Math.round(cx - rx * 0.6), ey + 2, '#f58ea0'); L.px(Math.round(cx + rx * 0.55), ey + 2, '#f58ea0');
  return L.contorno();
}

function construirCriaturas() {
  conBlanco('babosa_0', babosa(7, 5, 0));
  conBlanco('babosa_1', babosa(8.2, 4, 0));
  conBlanco('babosa_2', babosa(6, 6.5, 1));

  // larva de la arcilla: segmentos redondos y una cabeza con pinzas
  const CREMA = ['#6e5236', '#9a7a52', '#c7a978', '#e8d3a4', '#fff2d0'];
  const CAB = ['#3a1410', '#6a2a1c', '#9a4630', '#c86a48'];
  const larva = (f) => {
    const L = new Lienzo(22, 12);
    for (let k = 0; k < 5; k++) { const sube = (k + f) % 2 ? 1 : 0; L.esfera(3.5 + k * 3.4, 7 - sube, 3.4, 3.6, CREMA, { amb: 0.4 }); }
    for (let k = 0; k < 5; k++) L.px(2 + k * 3.4, 10 + ((k + f) % 2), CREMA[0]);
    L.esfera(18.5, 6, 3.6, 3.8, CAB, { brillo: '#f0a080' });
    L.px(19, 5, TINTA); L.px(20, 5, '#ffffff'); L.px(21, 8, '#2a1414'); L.px(21, 9, '#2a1414'); L.px(20, 9, '#2a1414');
    return L.contorno();
  };
  conBlanco('larva_0', larva(0), true); conBlanco('larva_1', larva(1), true);

  // escarabajo de la piedra: caparazón brillante con su raya
  const AZUL = ['#101a36', '#1c2e5c', '#2b4a8c', '#3f6cc0', '#6a9ce8', '#b8d8ff'];
  const escarabajo = (f) => {
    const L = new Lienzo(16, 15);
    const patas = f ? [[1, 5], [0, 8], [1, 11], [14, 5], [15, 8], [14, 11]] : [[0, 6], [1, 9], [0, 12], [15, 6], [14, 9], [15, 12]];
    for (const [x, y] of patas) L.px(x, y, '#2a2236');
    L.esfera(8, 7.5, 6.5, 6, AZUL, { brillo: '#ffffff' });
    L.vl(8, 3, 9, AZUL[1]);
    L.esfera(8, 13, 3, 2, ['#1a1a2a', '#2c2c44', '#44446a']);
    L.px(6, 13, '#ffc857'); L.px(10, 13, '#ffc857');
    return L.contorno();
  };
  conBlanco('escarabajo_0', escarabajo(0)); conBlanco('escarabajo_1', escarabajo(1));

  // el Topo Rey
  const PIEL = ['#2c160e', '#4a2818', '#6c3e24', '#8e5834', '#b07448', '#cc9462'];
  const PANZA = ['#8a6248', '#b08868', '#d0aa86', '#ead0a8'];
  const ROSA = ['#8a3048', '#c4566e', '#f08aa0', '#ffc8d2'];
  const UNA = ['#a88a80', '#e0c8bc', '#fff4ee'];
  const ORO = ['#7a4e10', '#b8862a', '#f2c14e', '#fff0a0'];
  const topo = (f) => {
    const L = new Lienzo(46, 40);
    L.esfera(23, 23, 18, 16, PIEL, { amb: 0.25 });
    L.esfera(23, 27, 10, 10, PANZA, { amb: 0.4 });
    // ojitos entornados
    for (const ex of [16, 28]) { L.hl(ex, 16, 3, TINTA); L.px(ex - 1, 15, TINTA); L.px(ex + 3, 15, TINTA); }
    L.esfera(23, 20, 4, 3, ROSA, { brillo: '#ffffff' }); // hocico
    L.px(21, 21, ROSA[0]); L.px(25, 21, ROSA[0]);
    for (const [x, y] of [[13, 19], [33, 19], [14, 21], [32, 21]]) L.px(x, y, PIEL[1]); // bigotes
    // corona
    L.rect(16, 4, 15, 4, ORO[2]); L.hl(16, 7, 15, ORO[1]); L.hl(16, 4, 15, ORO[3]);
    for (const x of [16, 20, 24, 28]) { L.px(x + 1, 3, ORO[2]); L.px(x + 1, 2, ORO[3]); }
    L.px(30, 3, ORO[2]); L.esfera(23.5, 5.5, 1.6, 1.6, ['#7a1020', '#c42838', '#ff6070']);
    // garras grandes
    const garra = (x, y, lado) => {
      L.esfera(x, y, 5, 4, PIEL, { amb: 0.3 });
      for (let k = 0; k < 3; k++) { const ux = x + lado * (2 + k * 0) - 3 + k * 3, uy = y + 3; L.vl(ux, uy, 3, UNA[1]); L.px(ux, uy, UNA[2]); L.px(ux, uy + 3, UNA[0]); }
    };
    const sube = f ? -3 : 0;
    garra(7, 24 + sube, -1); garra(39, 24 + sube, 1);
    // patas
    L.esfera(15, 37, 5, 3, PIEL); L.esfera(31, 37, 5, 3, PIEL);
    return L.contorno();
  };
  conBlanco('topo_0', topo(0)); conBlanco('topo_1', topo(1));
  const monticulo = (f) => {
    const L = new Lienzo(30, 12), R = RAMPA.tierra;
    L.esfera(15, 11, 14, 8 + f, R.slice(1), { donde: (x, y) => y < 12 });
    for (let k = 0; k < 6; k++) { const x = 4 + k * 4 + f, y = 6 + (k % 2); L.px(x, y, R[5]); L.px(x + 1, y + 1, R[2]); }
    L.px(13, 2 - f, ORO[2]); L.px(17, 2 - f, ORO[2]); L.px(15, 3 - f, ORO[3]);
    return L.contorno();
  };
  def('monticulo_0', monticulo(0)); def('monticulo_1', monticulo(1));
  def('roca_lanzada', new Lienzo(7, 7).esfera(3.5, 3.5, 3.2, 3.2, ['#3e3430', '#5e5046', '#8a7a6a', '#b8a898'], { brillo: '#e8dcd0' }).contorno());
}

// ---------------------------------------------------------------- objetos del mundo

const MADERA = { w: '#c28a52', W: '#9a6438', d: '#6e4228', D: '#4a2a1c', e: '#e2b07a' };
const FUEGO = { y: '#ffe066', o: '#ff9a3c', r: '#e8502a', w: '#fff7d0' };
const PIEDRA_OBJ = { p: '#4d5466', P: '#6d7588', q: '#949db0', Q: '#323645', k: '#14101c' };
const CRISTAL = { z: '#1a8a9c', Z: '#3fd8e8', l: '#b8fbff' };
const CRISTAL_R = ['#0e4a5c', '#1a8a9c', '#2cbccc', '#5ae4f0', '#b8fbff'];

function construirObjetos() {
  // antorcha: palo con su vendaje y una llama de varias capas
  const llama = (L, f) => {
    const alt = [0, 1, 0, -1][f], lado = [0, 1, 0, -1][(f + 1) % 4] * 0.4;
    L.elipse(4 + lado * 0.5, 5.5 - alt * 0.3, 3, 4.5 + alt * 0.4, FUEGO.r);
    L.elipse(4 + lado, 6, 2.2, 3.4, FUEGO.o);
    L.elipse(4 + lado, 6.8, 1.4, 2.2, FUEGO.y);
    L.elipse(4, 7.6, 0.8, 1.1, FUEGO.w);
    L.px(4 + Math.round(lado * 2), 0 - alt, FUEGO.r);
  };
  for (let f = 0; f < 4; f++) {
    const L = new Lienzo(9, 20);
    L.rect(3, 10, 3, 2, '#7a4a30'); L.hl(3, 10, 3, '#a87050'); L.px(5, 11, '#5a3020');
    L.rect(3, 12, 2, 8, '#8a5a36'); L.vl(4, 12, 8, '#5e3a22'); L.px(3, 12, '#b07a4a');
    llama(L, f);
    const C = L.contorno('#1a0c08', 0.7);
    def(`antorcha_${f}`, C); def(`antorcha_${f}_luz`, C.solo(Object.values(FUEGO).concat([mezcla(FUEGO.r, '#1a0c08', 0.7), mezcla(FUEGO.o, '#1a0c08', 0.7)])));
  }

  // mesa de trabajo, en tres cuartos: tablero con herramientas y patas
  const mesa = new Lienzo(18, 17);
  mesa.rect(0, 0, 18, 9, MADERA.W);
  for (let x = 0; x < 18; x += 6) { mesa.vl(x, 0, 9, MADERA.d); mesa.vl(x + 1, 0, 9, MADERA.w); }
  mesa.hl(0, 0, 18, MADERA.e);
  mesa.rect(0, 9, 18, 4, MADERA.d); mesa.hl(0, 9, 18, MADERA.w); mesa.hl(0, 12, 18, MADERA.D);
  mesa.rect(1, 13, 3, 4, MADERA.D); mesa.rect(14, 13, 3, 4, MADERA.D); mesa.vl(1, 13, 4, MADERA.d); mesa.vl(14, 13, 4, MADERA.d);
  mesa.linea(2, 6, 7, 2, '#6a3420'); mesa.linea(3, 6, 8, 2, '#9a5a30'); mesa.rect(6, 1, 4, 2, '#8a96a8'); mesa.hl(6, 1, 4, '#d5dde8'); // martillo
  mesa.rect(10, 4, 7, 2, '#b9c4d2'); mesa.hl(10, 6, 7, '#5d6b80'); for (let x = 10; x < 17; x += 2) mesa.px(x, 6, '#3d4b60'); mesa.rect(15, 3, 2, 3, '#7a3a2a'); // serrucho
  mesa.rect(3, 7, 3, 1, '#d8c8a0'); // virutas
  def('mesa', mesa.contorno());

  // horno de piedra con la boca encendida
  const horno = (f) => {
    const L = new Lienzo(16, 20);
    L.rect(6, 0, 4, 4, PIEDRA_OBJ.p); L.hl(6, 0, 4, PIEDRA_OBJ.q); L.vl(9, 0, 4, PIEDRA_OBJ.Q);
    L.esfera(8, 11, 8, 8, ['#262a36', '#3a4050', '#50586c', '#6c7488', '#8c94a8'], { donde: (x, y) => y < 18 });
    for (const [x, y] of [[3, 8], [8, 6], [12, 9], [5, 13], [11, 14], [2, 15]]) { L.hl(x, y, 3, '#2a2e3a'); L.px(x, y - 1, '#9aa2b4'); }
    L.rect(0, 17, 16, 3, '#3a4050'); L.hl(0, 17, 16, '#6c7488');
    L.elipse(8, 14, 3.5, 3.5, '#120a08'); L.rect(5, 14, 7, 3, '#120a08');
    L.elipse(8, 15.5 - f * 0.5, 2.5, 2, FUEGO.r); L.elipse(8, 16, 1.8, 1.2, FUEGO.o); L.px(8 - f, 15, FUEGO.y); L.px(8 + f, 16, FUEGO.w);
    return L.contorno();
  };
  for (let f = 0; f < 2; f++) { const L = horno(f); def(`horno_${f}`, L); def(`horno_${f}_luz`, L.solo(Object.values(FUEGO))); }

  const YUN = { i: '#38404f', I: '#5c687e', j: '#8e9cb4', J: '#c8d4e6', ...MADERA };
  def('yunque', rej(YUN, `
    JJJJJJJJJJJJJJJ.
    jjjjjjjjjjjjjjjI
    IIIIIIIIIIIIIIi.
    .iIIIIIIIIIIii..
    .....jIIIi......
    ....jIIIIii.....
    ...eWWwwwwWWd...
    ...WwwwwwwwWd...
    ...ddddddddddd..`).contorno());

  const lampara = new Lienzo(8, 18);
  lampara.esfera(4, 4, 3.6, 4, CRISTAL_R, { brillo: '#ffffff' });
  lampara.rect(2, 8, 4, 1, '#68758e'); lampara.rect(3, 9, 2, 8, '#4c566c'); lampara.vl(3, 9, 8, '#68758e'); lampara.rect(1, 17, 6, 1, '#38404f');
  const lampC = lampara.contorno();
  def('lampara', lampC); def('lampara_luz', lampC.solo(CRISTAL_R));

  // raíz gruesa: un arco retorcido que sale del suelo y vuelve a entrar, con raicillas
  const CORTEZA = ['#2e1a0e', '#4e2e16', '#74461f', '#9c6a2c', '#c8933e', '#e8bc62'];
  const raiz = new Lienzo(22, 16);
  const arco = (t) => [2.5 + t * 16, 14.5 - Math.sin(t * Math.PI) * 10 - Math.sin(t * 9) * 0.8];
  for (let k = 0; k <= 60; k++) {
    const t = k / 60, [cx, cy] = arco(t), gr = 2.6 - Math.abs(t - 0.5) * 1.2 + (t < 0.12 || t > 0.88 ? 0.8 : 0);
    raiz.esfera(cx, cy, gr, gr, CORTEZA, { amb: 0.2 });
  }
  for (let k = 3; k < 58; k += 7) { const [cx, cy] = arco(k / 60); raiz.px(cx, cy + 1, CORTEZA[1]); raiz.px(cx + 1, cy, CORTEZA[1]); }
  for (const [x0, y0, x1, y1] of [[1, 15, 0, 13], [20, 15, 21, 12], [9, 7, 7, 3], [14, 6, 17, 3]]) { raiz.linea(x0, y0, x1, y1, CORTEZA[2]); raiz.px(x1, y1, CORTEZA[4]); }
  def('raiz', raiz.contorno('#120a04', 0.7));
  const raices = new Lienzo(26, 18), rr = azar(4242);
  for (let k = 0; k < 7; k++) {
    let x = 13, y = 9, a = (k / 7) * Math.PI * 2 + rr() * 0.5;
    for (let s = 0; s < 11; s++) { raices.px(x, y, s < 4 ? CORTEZA[3] : CORTEZA[2]); raices.px(x, y + 1, CORTEZA[1]); a += (rr() - 0.5) * 0.8; x += Math.cos(a); y += Math.sin(a) * 0.7; }
  }
  def('raiz_suelo', raices);

  // arbusto con bayas que brillan
  const BAYA = ['#1a5a8c', '#2f8fd0', '#58c8ff', '#c8f4ff'];
  const HOJA = ['#14301f', '#1f4a30', '#2f6a42', '#46905a', '#68b070'];
  const arbusto = new Lienzo(16, 14);
  for (const [x, y, r] of [[5, 8, 4.5], [11, 8, 4.5], [8, 5, 4.5], [8, 10, 4.5]]) arbusto.esfera(x, y, r, r * 0.9, HOJA);
  for (const [x, y] of [[4, 6], [10, 4], [12, 9], [6, 10], [9, 8]]) arbusto.esfera(x + 0.5, y + 0.5, 1.4, 1.4, BAYA, { brillo: BAYA[3] });
  const arbC = arbusto.contorno();
  def('arbusto', arbC); def('arbusto_luz', arbC.solo(BAYA));

  // hongo azul que brilla
  const hongo = new Lienzo(14, 16);
  hongo.rect(5, 8, 4, 7, '#e9e0c6'); hongo.vl(8, 8, 7, '#b7aa8a'); hongo.hl(4, 15, 6, '#b7aa8a');
  hongo.esfera(7, 6, 6.6, 4.6, CRISTAL_R, { brillo: '#ffffff', donde: (x, y) => y < 9 });
  hongo.hl(2, 8, 10, CRISTAL_R[0]);
  for (const [x, y] of [[4, 4], [9, 3], [10, 6]]) hongo.px(x, y, '#e8fdff');
  const hongoC = hongo.contorno();
  def('hongo', hongoC); def('hongo_luz', hongoC.solo(CRISTAL_R.concat(['#e8fdff', '#ffffff'])));

  // cristales
  const cristal = new Lienzo(14, 18);
  const prisma = (x, y, w, h) => { cristal.rect(x, y, w, h, CRISTAL_R[2]); cristal.vl(x, y, h, CRISTAL_R[4]); cristal.vl(x + w - 1, y, h, CRISTAL_R[1]); cristal.px(x + Math.floor(w / 2), y - 1, CRISTAL_R[3]); if (w > 2) cristal.vl(x + 1, y + 1, h - 2, CRISTAL_R[3]); };
  prisma(5, 2, 4, 13); prisma(1, 7, 3, 8); prisma(10, 6, 3, 9);
  cristal.esfera(7, 16, 7, 3, ['#262a36', '#3a4050', '#50586c', '#6c7488'], { donde: (x, y) => y < 18 });
  const crisC = cristal.contorno('#061820', 0.75);
  def('cristal', crisC); def('cristal_luz', crisC.solo(CRISTAL_R));

  // el Corazón: cristal en forma de corazón, sobre un altar
  const ROJO = ['#3a0820', '#6e1438', '#a82a58', '#e0507e', '#ff8eaa', '#ffd8e2'];
  const corazon = (despierto) => {
    const L = new Lienzo(28, 26), R = despierto ? ROJO : ['#24081a', '#46102c', '#6e1e40', '#92305a', '#b44a74', '#d47a96'];
    L.esfera(8.5, 8.5, 7.5, 7.5, R, { brillo: R[5] });
    L.esfera(19.5, 8.5, 7.5, 7.5, R);
    for (let y = 9; y < 26; y++) { const w = Math.max(0, 14 - (y - 9) * 0.85); for (let x = Math.ceil(14 - w); x < 14 + w; x++) { const nx = (x - 14) / 14, ny = (y - 9) / 17; const lum = 0.6 - nx * 0.45 - ny * 0.35; L.px(x, y, R[Math.max(0, Math.min(5, Math.floor(lum * 6)))]); } }
    L.linea(14, 4, 14, 22, R[1]); L.linea(6, 12, 14, 22, R[2]); L.linea(22, 12, 14, 22, R[1]);
    return L.contorno('#1a0410', 0.6);
  };
  def('corazon', corazon(true)); def('corazon_dormido', corazon(false));
  const RU = RAMPA.ruina;
  const altar = new Lienzo(34, 14);
  altar.rect(2, 0, 30, 6, RU[3]); altar.hl(2, 0, 30, RU[5]); altar.hl(2, 5, 30, RU[1]);
  altar.rect(0, 6, 34, 8, RU[2]); altar.hl(0, 6, 34, RU[4]); altar.hl(0, 13, 34, RU[0]);
  for (let x = 4; x < 32; x += 6) { altar.vl(x, 7, 6, RU[1]); altar.px(x + 2, 9, '#5fd0e0'); }
  def('altar', altar.contorno());
  const pedestal = (lleno) => {
    const L = new Lienzo(12, 20);
    L.rect(1, 0, 10, 4, RU[4]); L.hl(1, 0, 10, RU[5]); L.hl(1, 3, 10, RU[2]);
    L.rect(3, 4, 6, 12, RU[3]); L.vl(3, 4, 12, RU[4]); L.vl(8, 4, 12, RU[1]); for (let y = 6; y < 16; y += 3) L.hl(3, y, 6, RU[2]);
    L.rect(0, 16, 12, 4, RU[2]); L.hl(0, 16, 12, RU[4]); L.hl(0, 19, 12, RU[0]);
    L.rect(4, 1, 4, 2, '#0a1014');
    if (lleno) L.esfera(6, 0.5, 2.5, 3, ROJO.slice(1), { brillo: '#ffffff' });
    return L.contorno();
  };
  def('pedestal', pedestal(false));
  const pl = pedestal(true);
  def('pedestal_lleno', pl); def('pedestal_lleno_luz', pl.solo(ROJO.slice(1).concat(['#ffffff'])));

  // piezas para el motor
  const sombra = (w, h) => { const L = new Lienzo(w, h); L.elipse(w / 2, h / 2, w / 2, h / 2, '#05020890'); L.elipse(w / 2, h / 2, w / 2 - 1.5, h / 2 - 1, '#050208c0'); return L; };
  def('sombra_s', sombra(12, 5)); def('sombra_m', sombra(16, 6)); def('sombra_l', sombra(36, 12));
  def('blanco', new Lienzo(2, 2).rect(0, 0, 2, 2, '#ffffff'));
  const marca = new Lienzo(16, 16);
  for (const [x, y, w, h] of [[0, 0, 4, 1], [0, 0, 1, 4], [12, 0, 4, 1], [15, 0, 1, 4], [0, 15, 4, 1], [0, 12, 1, 4], [12, 15, 4, 1], [15, 12, 1, 4]]) marca.rect(x, y, w, h, '#ffffff');
  def('marca', marca);
  def('aviso_suelo', new Lienzo(30, 12).elipse(15, 6, 15, 6, '#ff304060'));
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
  // Empaquetado en estantes, de más alto a más bajo, con 1 px libre alrededor.
  const lista = DIBUJOS.slice().sort((a, b) => b[1].h - a[1].h || b[1].w - a[1].w);
  const ANCHO = 1024;
  let x = 1, y = 1, alto = 0;
  for (const [n, l] of lista) {
    if (x + l.w + 1 > ANCHO) { x = 1; y += alto + 2; alto = 0; }
    SPR[n] = { x, y, w: l.w, h: l.h };
    x += l.w + 2; alto = Math.max(alto, l.h);
  }
  const H = y + alto + 1;
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

// Para la interfaz (lienzo 2D): esquina arriba-izquierda, pies o centro.
function spr(ctx, n, x, y) {
  const r = SPR[n];
  if (r) ctx.drawImage(ATLAS, r.x, r.y, r.w, r.h, Math.round(x), Math.round(y), r.w, r.h);
}
function sprPie(ctx, n, x, y) {
  const r = SPR[n];
  if (r) ctx.drawImage(ATLAS, r.x, r.y, r.w, r.h, Math.round(x - r.w / 2), Math.round(y - r.h), r.w, r.h);
}
function sprCentro(ctx, n, x, y) {
  const r = SPR[n];
  if (r) ctx.drawImage(ATLAS, r.x, r.y, r.w, r.h, Math.round(x - r.w / 2), Math.round(y - r.h / 2), r.w, r.h);
}
