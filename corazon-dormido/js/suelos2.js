'use strict';
// Suelos nuevos (los orgánicos: hierba, césped, flores, musgo, tierra de selva, veneno, moho, grava, barro) con
// zonas de tono limpias (sin píxeles sueltos) y pocos detalles bien puestos, y los bordes orgánicos entre suelos:
// el suelo con más prioridad (INFO_SUELO[].prioridad) se mete un poco en el vecino con un borde irregular y una
// línea de sombra. gl.js los pinta encima del suelo de cada casilla.

// Qué suelo va encima en los bordes (más alto, encima). Los construidos (losas, tablas, alfombras…) no se mezclan.
const PRIORIDAD_SUELO = { piedra: 1, arcilla: 2, arena: 2, grava: 3, tierra: 4, barro: 5, tierra_selva: 5, cesped: 6, musgo: 6, hierba: 7, flores: 7, moho: 8, veneno: 9 };

CONSTRUIR_MODELOS.push(function suelosNuevos() {
  // ruido que da la vuelta cada 16 px
  const ruido = (sem, celdas) => ruidoCiclico(sem, celdas, 16);
  // textura base: tres o cuatro tonos en manchas grandes, y limpieza de píxeles sueltos
  function base(sem, pal, umbrales = [0.34, 0.6, 0.82]) {
    const a = ruido(sem, 3), b = ruido(sem + 1, 6), L = new Lienzo(16, 16), id = new Int8Array(256);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const n = a(x, y) * 0.68 + b(x, y) * 0.32;
      id[y * 16 + x] = n < umbrales[0] ? 0 : n < umbrales[1] ? 1 : n < umbrales[2] ? 2 : Math.min(3, pal.length - 1);
    }
    const c = id.slice();
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const v = id[y * 16 + x], ve = [id[y * 16 + ((x + 1) & 15)], id[y * 16 + ((x + 15) & 15)], id[((y + 1) & 15) * 16 + x], id[((y + 15) & 15) * 16 + x]];
      if (!ve.includes(v)) { const cuenta = {}; for (const q of ve) cuenta[q] = (cuenta[q] || 0) + 1; c[y * 16 + x] = +Object.entries(cuenta).sort((p, q) => q[1] - p[1])[0][0]; }
    }
    for (let i = 0; i < 256; i++) L.d[i] = pal[c[i]];
    return L;
  }
  const brizna = (L, x, y, c) => { L.pxc(x, y, c.luz); L.pxc(x, y + 1, c.base); L.pxc(x - 1, y + 1, c.osc); L.pxc(x + 1, y + 1, c.osc); };
  const T = {
    hierba: (v) => {
      const L = base(700 + v * 13, ['#1c3418', '#264824', '#30582c', '#3c6a34']), r = azar(701 + v);
      for (let k = 0; k < 6; k++) brizna(L, entre(r, 0, 15), entre(r, 0, 15), { luz: '#5c9248', base: '#3c6a34', osc: '#1c3418' });
      return L;
    },
    cesped: (v) => {
      const L = base(720 + v * 13, ['#141f14', '#1a2a1a', '#223622', '#2a442a']), r = azar(721 + v);
      for (let k = 0; k < 4; k++) brizna(L, entre(r, 0, 15), entre(r, 0, 15), { luz: '#46703c', base: '#2a442a', osc: '#141f14' });
      return L;
    },
    flores: (v) => {
      const L = T.hierba(v + 4), r = azar(741 + v);
      const cols = [['#b85a74', '#e08aa0'], ['#c8a648', '#ecd27a'], ['#6a7cc0', '#a0b0e8']];
      for (let k = 0; k < (v === 3 ? 0 : 2); k++) {
        const x = entre(r, 1, 14), y = entre(r, 1, 13), [c1, c2] = cols[entre(r, 0, 2)];
        L.pxc(x, y - 1, c1); L.pxc(x - 1, y, c1); L.pxc(x + 1, y, c1); L.pxc(x, y + 1, c1); L.pxc(x, y, '#f0e0a0'); L.pxc(x - 1, y - 1, c2); L.pxc(x + 1, y + 2, '#142a14'); L.pxc(x, y + 2, '#142a14');
      }
      return L;
    },
    musgo: (v) => {
      const L = base(760 + v * 13, ['#10200f', '#172c16', '#1f3a1e', '#294a26']), r = azar(761 + v);
      for (let k = 0; k < 3; k++) { const x = entre(r, 0, 15), y = entre(r, 0, 15); L.pxc(x, y, '#3a5e32'); L.pxc(x + 1, y, '#30502a'); L.pxc(x, y + 1, '#30502a'); L.pxc(x + 1, y + 1, '#1f3a1e'); L.pxc(x + 2, y + 1, '#10200f'); L.pxc(x + 1, y + 2, '#10200f'); }
      return L;
    },
    tierra_selva: (v) => {
      const L = base(780 + v * 13, ['#1a120a', '#24180e', '#2e2012', '#3a2a18']), r = azar(781 + v);
      for (let k = 0; k < 3; k++) { const x = entre(r, 0, 15), y = entre(r, 0, 15), c = r() < 0.5 ? ['#5a4a22', '#3e3218'] : ['#6a3a1a', '#4a2812']; L.pxc(x, y, c[0]); L.pxc(x + 1, y, c[0]); L.pxc(x + 1, y + 1, c[1]); }
      if (v % 2 === 0) { let x = entre(r, 0, 15), y = entre(r, 0, 15); for (let k = 0; k < 7; k++) { L.pxc(x, y, '#120c06'); L.pxc(x, y - 1, '#3a2a18'); x++; if (r() < 0.4) y += entre(r, -1, 1); } }
      return L;
    },
    veneno: (v) => {
      // tierra podrida, negra y morada, con venas de la Podre y algún poro que apenas brilla
      const L = base(800 + v * 13, ['#0c0810', '#130c18', '#1a1220', '#22182a'], [0.36, 0.64, 0.9]), vena = ruido(805 + v, 4), r = azar(801 + v);
      for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
        const d = Math.abs(vena(x, y) - 0.5);
        if (d < 0.03) L.px(x, y, '#3a1a40'); else if (d < 0.055) L.px(x, y, '#28142e');
      }
      if (v < 3) { const x = entre(r, 2, 13), y = entre(r, 2, 13); L.pxc(x, y, '#6a1a52'); L.pxc(x + 1, y, '#4a1240'); L.pxc(x, y + 1, '#4a1240'); L.pxc(x + 1, y + 1, '#2a0c26'); }
      if (v === 1) { const x = entre(r, 1, 13), y = entre(r, 1, 13); L.pxc(x, y, '#2a1a34'); L.pxc(x + 1, y, '#2a1a34'); L.pxc(x, y + 1, '#2a1a34'); L.pxc(x + 1, y + 1, '#1a1220'); L.pxc(x, y, '#54406a'); }
      return L;
    },
    moho: (v) => {
      const L = base(820 + v * 13, ['#06100e', '#0a1816', '#0e201e', '#142a26']), r = azar(821 + v);
      for (let k = 0; k < 2; k++) { const x = entre(r, 0, 15), y = entre(r, 0, 15); L.pxc(x, y, '#2a7a6a'); L.pxc(x + 1, y, '#164a40'); L.pxc(x, y + 1, '#164a40'); }
      return L;
    },
    grava: (v) => {
      const L = base(840 + v * 13, ['#141218', '#1a181e', '#211f26', '#28262e']), r = azar(841 + v);
      for (let k = 0; k < 5; k++) { const x = entre(r, 0, 15), y = entre(r, 0, 15); L.pxc(x, y, '#4a4652'); L.pxc(x + 1, y, '#363240'); L.pxc(x, y + 1, '#363240'); L.pxc(x + 1, y + 1, '#2a2632'); L.pxc(x + 2, y + 1, '#0c0a10'); L.pxc(x + 1, y + 2, '#0c0a10'); }
      return L;
    },
    barro: (v) => {
      const L = base(860 + v * 13, ['#18140a', '#201a0e', '#2a2212', '#342a16']), r = azar(861 + v);
      for (let k = 0; k < 2; k++) { const x = entre(r, 0, 15), y = entre(r, 0, 15); L.pxc(x, y, '#4a4024'); L.pxc(x + 1, y, '#4a4024'); L.pxc(x + 2, y, '#3a3018'); }
      return L;
    },
  };
  for (const [arte, fn] of Object.entries(T)) for (let v = 0; v < 4; v++) def(`s_${arte}_${v}`, fn(v));

  bordesSuelo(Object.keys(PRIORIDAD_SUELO));
});

// Bordes de los suelos con prioridad: 4 lados (n, e, s, o) y 4 esquinas (no, ne, se, so), sacados de su textura
// s_<arte>_0 (que tiene que estar ya dibujada). Los suelos de otros archivos llaman a esto tras dibujar los suyos.
function bordesSuelo(lista) {
  const ultimo = (n) => { for (let i = DIBUJOS.length - 1; i >= 0; i--) if (DIBUJOS[i][0] === n) return DIBUJOS[i][1]; return null; };
  const oscuro = (L) => { const cs = L.d.filter(Boolean).map((c) => { const [r, g, b] = hexRgb(c.slice(0, 7)); return [r + g + b, c]; }).sort((p, q) => p[0] - q[0]); return cs.length ? mezcla(cs[0][1].slice(0, 7), '#000000', 0.45) : '#000000'; };
  for (const arte of lista) {
    const tex = ultimo(`s_${arte}_0`);
    if (!tex) continue;
    const borde = oscuro(tex), ondas = ruidoCiclico(900 + arte.length * 7, 4, 16);
    const prof = (t) => 2.4 + ondas(t, 3) * 3.2;
    const hacer = (nombre, dentro) => {
      const L = new Lienzo(16, 16);
      for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
        const d = dentro(x, y);
        if (d < 0) L.d[y * 16 + x] = tex.d[y * 16 + x];
        else if (d < 1) L.d[y * 16 + x] = borde;
      }
      def(`borde_${arte}_${nombre}`, L);
    };
    hacer('n', (x, y) => y - prof(x)); hacer('s', (x, y) => 15 - y - prof(x));
    hacer('o', (x, y) => x - prof(y)); hacer('e', (x, y) => 15 - x - prof(y));
    const esq = (cx, cy) => (x, y) => Math.hypot(x + 0.5 - cx, y + 0.5 - cy) - (3.6 + ondas(x, y) * 1.6);
    hacer('no', esq(0, 0)); hacer('ne', esq(16, 0)); hacer('se', esq(16, 16)); hacer('so', esq(0, 16));
  }
}

// Avisos de ataque en el suelo (se tiñen al dibujarlos): un anillo que late y un disco que se llena hasta el
// golpe; grietas que se abren (raíces que van a salir) y círculos de runas (magia de la Podre).
CONSTRUIR_MODELOS.push(function avisosSuelo() {
  const anillo = new Lienzo(32, 32), disco = new Lienzo(32, 32), runa = new Lienzo(32, 32);
  for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
    const d = Math.hypot(x + 0.5 - 16, y + 0.5 - 16);
    if (d <= 15.6 && d > 13.6) anillo.px(x, y, d > 14.6 ? '#ffffff' : '#ffffffb0');
    if (d <= 15.6) disco.px(x, y, d > 13 ? '#ffffff90' : '#ffffff60');
    if ((d <= 15.6 && d > 14.4) || (d <= 10.4 && d > 9.6)) runa.px(x, y, '#ffffff');
  }
  for (let k = 0; k < 8; k++) { // signos entre los dos aros
    const a = k / 8 * Math.PI * 2, cx = 16 + Math.cos(a) * 12.2, cy = 16 + Math.sin(a) * 12.2;
    if (k % 2) { runa.px(cx, cy - 1, '#ffffff'); runa.px(cx, cy, '#ffffff'); runa.px(cx, cy + 1, '#ffffff'); runa.px(cx - 1, cy, '#ffffff'); }
    else { runa.px(cx - 1, cy - 1, '#ffffff'); runa.px(cx + 1, cy - 1, '#ffffff'); runa.px(cx, cy, '#ffffff'); runa.px(cx, cy + 1, '#ffffff'); }
  }
  def('aviso_anillo', anillo); def('aviso_disco', disco); def('aviso_runa', runa);
  for (let v = 0; v < 3; v++) { // grietas: líneas quebradas desde el centro, con ramas
    const L = new Lienzo(32, 32), r = azar(950 + v);
    const grieta = (x, y, a, n, grosor) => {
      for (let s = 0; s < n; s++) {
        L.px(x, y, '#ffffff'); if (grosor > 1) { L.px(x + 1, y, '#ffffffa0'); L.px(x, y + 1, '#ffffffa0'); }
        a += (r() - 0.5) * 0.9; x += Math.cos(a); y += Math.sin(a);
        if (Math.hypot(x - 16, y - 16) > 15) break;
        if (r() < 0.12 && n > 5) grieta(x, y, a + (r() < 0.5 ? 1 : -1) * (0.7 + r() * 0.5), Math.floor(n * 0.4), 1);
      }
    };
    for (let k = 0; k < 5; k++) grieta(16, 16, k / 5 * Math.PI * 2 + r(), 13 + Math.floor(r() * 4), 2);
    def(`aviso_grieta_${v}`, L);
  }
});
