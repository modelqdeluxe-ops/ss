'use strict';
// Arte de la fase 1: suelos nuevos, muebles, cultivos, criaturas del anillo interior, los guardianes,
// la armadura que se ve puesta y los iconos de todo lo nuevo. Mismas herramientas que arte.js.

function construirExtra() {
  sueloNuevos(); objetosNuevos(); criaturasNuevas(); jefesNuevos(); armaduraPuesta(); iconosNuevos();
}

const pal8 = (c, a) => c + Math.round(Math.max(0, Math.min(1, a)) * 255).toString(16).padStart(2, '0');

// ---------------------------------------------------------------- suelos

function sueloNuevos() {
  for (let v = 0; v < 4; v++) {
    const r = azar(5100 + v);
    // césped de cueva: denso y vivo, con briznas y alguna flor
    const C = ['#24401c', '#2e5222', '#3a6428', '#4a7832', '#5e8e3e', '#7aa84e'];
    const ces = new Lienzo(16, 16).rect(0, 0, 16, 16, C[2]), n = ruidoCiclico(5200 + v, 4);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const f = n(x, y); ces.px(x, y, f < 0.32 ? C[1] : f > 0.7 ? C[3] : C[2]); }
    for (let k = 0; k < 26; k++) { const x = entre(r, 0, 15), y = entre(r, 0, 15); ces.pxc(x, y, C[4]); ces.pxc(x, y + 1, C[3]); if (r() < 0.3) ces.pxc(x, y - 1, C[5]); }
    for (let k = 0; k < 6; k++) ces.pxc(entre(r, 0, 15), entre(r, 0, 15), C[0]);
    if (v === 1) { const x = entre(r, 2, 12), y = entre(r, 2, 12); ces.px(x, y, '#ffe680'); ces.px(x + 1, y, '#fff6c0'); }
    if (v === 3) { const x = entre(r, 2, 12), y = entre(r, 2, 12); ces.px(x, y, '#c8a0ff'); ces.px(x, y + 1, '#8a60c8'); }
    def(`s_cesped_${v}`, ces);
    // colmena: celdas de cera
    const H = ['#3a2408', '#5a3c12', '#7a5420', '#9a6e2a', '#c0903c'];
    const col = new Lienzo(16, 16).rect(0, 0, 16, 16, H[2]);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const fila = Math.floor(y / 4), cx = (x + (fila % 2) * 2 + v) % 5, cy = y % 4; col.px(x, y, cx === 0 || cy === 0 ? H[1] : cx === 1 && cy === 1 ? H[4] : cy === 3 ? H[2] : H[3]); }
    for (let k = 0; k < 3; k++) { const x = entre(r, 1, 14), y = entre(r, 1, 14); col.px(x, y, '#e8c060'); }
    def(`s_colmena_${v}`, col);
    // losas de las ruinas: cuatro losas por casilla con bisel
    const Lr = ['#20262e', '#2c343e', '#3a4450', '#485462', '#5a6878', '#748496'];
    const los = new Lienzo(16, 16).rect(0, 0, 16, 16, Lr[1]);
    for (const [x0, y0] of [[0, 0], [8, 0], [0, 8], [8, 8]]) { los.rect(x0 + 1, y0 + 1, 6, 6, Lr[3]); los.hl(x0 + 1, y0 + 1, 6, Lr[4]); los.vl(x0 + 1, y0 + 1, 6, Lr[4]); los.hl(x0 + 1, y0 + 6, 6, Lr[2]); los.vl(x0 + 6, y0 + 1, 6, Lr[2]); }
    if (v === 1) los.linea(9, 9, 13, 14, Lr[1]);
    if (v === 2) for (let k = 0; k < 4; k++) { const x = entre(r, 0, 14), y = entre(r, 0, 14); los.px(x, y, '#3f6b47'); los.px(x + 1, y, '#5c8f52'); }
    if (v === 3) { los.rect(4, 4, 8, 8, Lr[2]); los.rect(6, 6, 4, 4, Lr[4]); los.px(7, 7, '#5fd0e0'); }
    def(`s_losa_${v}`, los);
    // alfombra roja con ribete dorado
    const al = new Lienzo(16, 16).rect(0, 0, 16, 16, '#7a1e30');
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) if ((x + y + v * 2) % 8 === 0 || (x - y + 16) % 8 === 0) al.px(x, y, '#9a2a40');
    al.px(4, 4, '#d0a040'); al.px(12, 12, '#d0a040'); al.px(12, 4, '#c8a040'); al.px(4, 12, '#c8a040');
    def(`s_alfombra_${v}`, al);
    // grava
    const G = ['#1e1a16', '#2a2420', '#38302a', '#4a4038', '#5e5448', '#7a6e60'];
    const gr = new Lienzo(16, 16).rect(0, 0, 16, 16, G[1]);
    for (let k = 0; k < 22; k++) { const x = entre(r, 0, 15), y = entre(r, 0, 15), c = 2 + Math.floor(r() * 3); gr.pxc(x, y, G[c + 1]); gr.pxc(x + 1, y, G[c]); gr.pxc(x, y + 1, G[c - 1]); }
    def(`s_grava_${v}`, gr);
    // tierra arada: surcos
    const AR = ['#2a180e', '#3e2416', '#53321e', '#6a4228', '#845436'];
    const ara = new Lienzo(16, 16).rect(0, 0, 16, 16, AR[2]);
    for (let y = 0; y < 16; y += 4) { ara.hl(0, y, 16, AR[0]); ara.hl(0, y + 1, 16, AR[1]); ara.hl(0, y + 2, 16, AR[3]); ara.hl(0, y + 3, 16, AR[4]); for (let k = 0; k < 3; k++) ara.px(entre(r, 0, 15), y + 2, AR[2]); }
    def(`s_arada_${v}`, ara);
    // suelo de madera: tablas con vetas y clavos
    const MD = ['#3a2010', '#5a3418', '#7a4a24', '#94603a', '#b07a4c'];
    const md = new Lienzo(16, 16).rect(0, 0, 16, 16, MD[2]);
    for (let y = 0; y < 16; y += 4) {
      md.hl(0, y, 16, MD[3]); md.hl(0, y + 3, 16, MD[0]);
      const j = ((y / 4) * 5 + v * 3) % 16; md.vl(j, y, 4, MD[0]); md.px((j + 1) % 16, y + 1, '#c0c0c8');
      for (let k = 0; k < 2; k++) md.hl(entre(r, 0, 12), y + 1 + (k % 2), 3, MD[1]);
    }
    def(`s_madera_${v}`, md);
    // barro
    const B = ['#2a2410', '#3a3218', '#4a4020', '#5a4e2a', '#6e6036'];
    const ba = new Lienzo(16, 16).rect(0, 0, 16, 16, B[2]), nb = ruidoCiclico(5300 + v, 3);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const f = nb(x, y); if (f < 0.3) ba.px(x, y, B[1]); else if (f > 0.72) ba.px(x, y, B[3]); }
    for (let k = 0; k < 2; k++) { const x = entre(r, 1, 12), y = entre(r, 1, 12); ba.hl(x, y, 3, '#5a6a5a'); ba.px(x + 1, y, '#8a9a8a'); }
    def(`s_barro_${v}`, ba);
  }
  // adornos nuevos
  const FL = { a: '#f0e060', A: '#c8a020', b: '#e070a0', B: '#a04070', t: '#3a6a28', T: '#2a4a1c', w: '#ffffff' };
  def('dc_flor_0', rej(FL, `
    .a.a.
    aAwAa
    .aAa.
    ..t..
    .Tt..
    ..t..`).contorno('#101808', 0.7));
  def('dc_flor_1', rej(FL, `
    .b...b.
    bBb.bBb
    .b..tb.
    ..t.t..
    ..tTt..`).contorno('#101808', 0.7));
  def('dc_fibra', rej({ a: '#b8b060', b: '#8a8040', c: '#5a5428' }, `
    .a..a..
    .a.ab.a
    ab.ab.b
    b.bb.bb
    .cbcbc.`).contorno('#141008', 0.7));
  def('dc_huevo', new Lienzo(9, 9).esfera(4.5, 5, 3.6, 3.8, ['#8a7a4a', '#c0aa70', '#e8d8a0', '#fff4d0'], { brillo: '#ffffff' }).contorno('#2a2010', 0.7));
  def('dc_vasija_rota', rej({ a: '#a8704a', b: '#7a4a30', c: '#5a3020' }, `
    .a....a.
    ab.a.ab.
    .bc..bc.`).contorno('#140a06', 0.7));
  def('ad_baba', new Lienzo(14, 8).elipse(7, 4, 6.5, 3.5, '#c8a040b0').elipse(5, 3, 2, 1, '#f0d070c0'));
  def('regado', (() => { const L = new Lienzo(16, 16), r = azar(77); for (let k = 0; k < 40; k++) L.px(entre(r, 0, 15), entre(r, 0, 15), '#0a0604a0'); return L.elipse(8, 8, 7, 6, '#0a0604'); })());
  def('charco', new Lienzo(20, 10).elipse(10, 5, 9.5, 4.5, '#3a8a2ad0').elipse(10, 5, 7.5, 3.2, '#6ac83ae0').elipse(7, 3.5, 2, 1, '#cfff8a'));
  def('destello', rej({ w: '#ffffff', y: '#fff6c8' }, `
    ..w..
    ..y..
    wyw yw
    ..y..
    ..w..`.replace(' ', 'y')));
  def('corcho', rej({ r: '#e83a3a', w: '#ffffff', k: '#2a1414' }, `
    .k.
    rrr
    rrr
    www
    .w.`).contorno('#100808', 0.6));
  def('flecha_h', rej({ m: '#a07040', p: '#c8ccd8', f: '#e8e0d0' }, `
    f.........p.
    ffmmmmmmmppp
    f.........p.`));
  def('flecha_v', rej({ m: '#a07040', p: '#c8ccd8', f: '#e8e0d0' }, `
    .p.
    ppp
    .m.
    .m.
    .m.
    .m.
    .m.
    .m.
    fff
    f.f`));
  def('orbe', new Lienzo(9, 9).esfera(4.5, 4.5, 4, 4, ['#3a1050', '#6a2aa0', '#a060e0', '#d0a0ff', '#ffffff'], { brillo: '#ffffff' }));
  def('acido', new Lienzo(8, 8).esfera(4, 4, 3.4, 3.4, ['#2a5a1a', '#4a8a2a', '#7ac83a', '#cfff8a'], { brillo: '#ffffff' }).contorno('#0a1a04', 0.6));
}

// ---------------------------------------------------------------- muebles y objetos

function objetosNuevos() {
  // antorchas mágicas (las de la sala del trono): llama morada
  const MAG = { y: '#e8c8ff', o: '#b070ff', r: '#6a2ab0', w: '#ffffff' };
  for (let f = 0; f < 4; f++) {
    const base = SPR[`antorcha_${f}`];
    void base;
    const L = new Lienzo(9, 20), alt = [0, 1, 0, -1][f], lado = [0, 1, 0, -1][(f + 1) % 4] * 0.4;
    L.rect(3, 10, 3, 2, '#3a3a4a'); L.hl(3, 10, 3, '#6a6a7a'); L.rect(3, 12, 2, 8, '#4a4a5a'); L.vl(4, 12, 8, '#2a2a36');
    L.elipse(4 + lado * 0.5, 5.5 - alt * 0.3, 3, 4.5 + alt * 0.4, MAG.r); L.elipse(4 + lado, 6, 2.2, 3.4, MAG.o); L.elipse(4 + lado, 6.8, 1.4, 2.2, MAG.y); L.elipse(4, 7.6, 0.8, 1.1, MAG.w);
    const C = L.contorno('#0a0614', 0.7);
    def(`antorcha_${f}m`, C); def(`antorcha_${f}m_luz`, C.solo(Object.values(MAG)));
  }
  // farol de oro
  const far = new Lienzo(10, 22);
  far.rect(4, 8, 2, 13, '#3a3640'); far.vl(4, 8, 13, '#5a5660'); far.rect(2, 20, 6, 2, '#2a2630');
  far.rect(1, 0, 8, 2, '#b8861a'); far.hl(1, 0, 8, '#f2c14e');
  far.rect(2, 2, 6, 6, '#ffe58a'); far.rect(3, 3, 4, 4, '#fffbe0'); far.vl(2, 2, 6, '#b8861a'); far.vl(7, 2, 6, '#8a5a10');
  far.rect(1, 8, 8, 1, '#b8861a');
  const farC = far.contorno();
  def('farol', farC); def('farol_luz', farC.solo(['#ffe58a', '#fffbe0']));
  // mesas de metal: la de madera con planchas y herramientas del metal
  const METALES = { cobre: ['#7c3a22', '#c8693a', '#f2a466'], estano: ['#5f5c7a', '#a9a6c6', '#ebe9fb'], hierro: ['#2c3a4f', '#6787a6', '#b9d6ec'] };
  for (const [m, c] of Object.entries(METALES)) {
    const L = new Lienzo(18, 17);
    L.rect(0, 0, 18, 9, c[1]); for (let x = 0; x < 18; x += 6) { L.vl(x, 0, 9, c[0]); L.vl(x + 1, 0, 9, c[2]); } L.hl(0, 0, 18, c[2]);
    L.rect(0, 9, 18, 4, MADERA.d); L.hl(0, 9, 18, MADERA.w); L.hl(0, 12, 18, MADERA.D);
    L.rect(1, 13, 3, 4, c[0]); L.rect(14, 13, 3, 4, c[0]);
    L.rect(3, 2, 5, 3, '#2a2a30'); L.hl(3, 2, 5, '#5a5a60'); L.linea(9, 6, 14, 2, MADERA.D); L.rect(13, 1, 3, 2, '#b9c4d2');
    L.px(2, 10, '#c8c8d0'); L.px(15, 10, '#c8c8d0');
    def(`mesa_${m}`, L.contorno());
  }
  // yunques del estaño y del hierro (el de cobre lleva tinte de cobre)
  const yun = (c) => rej({ i: c[0], I: c[1], j: c[2], J: '#ffffff', ...MADERA }, `
    JJJJJJJJJJJJJJJ.
    jjjjjjjjjjjjjjjI
    IIIIIIIIIIIIIIi.
    .iIIIIIIIIIIii..
    .....jIIIi......
    ....jIIIIii.....
    ...eWWwwwwWWd...
    ...WwwwwwwwWd...
    ...ddddddddddd..`).contorno();
  def('yunque', yun(['#5a3020', '#a85a30', '#e0905a'])); def('yunque_estano', yun(METALES.estano)); def('yunque_hierro', yun(METALES.hierro));
  // olla sobre el fuego
  for (let f = 0; f < 2; f++) {
    const L = new Lienzo(16, 16);
    L.esfera(8, 8, 7, 5.5, ['#141418', '#24242c', '#363642', '#4c4c5c', '#6a6a7e'], { brillo: '#9a9ab0' });
    L.hl(1, 4, 14, '#4c4c5c'); L.hl(2, 3, 12, '#2a2a34'); L.rect(3, 3, 10, 1, '#c8a060'); L.px(5, 3, '#f0d090'); // sopa
    L.px(0, 6, '#24242c'); L.px(15, 6, '#24242c');
    L.rect(3, 13, 10, 3, '#4a2a1a'); L.hl(3, 13, 10, '#7a4a2a');
    L.px(5 + f, 14, FUEGO.o); L.px(8, 14 - f, FUEGO.y); L.px(10 - f, 14, FUEGO.r); L.px(7, 15, FUEGO.o);
    const C = L.contorno();
    def(`olla_${f}`, C); def(`olla_${f}_luz`, C.solo([FUEGO.o, FUEGO.y, FUEGO.r]));
  }
  // cofres
  const cofre = (mad, metal, oro) => {
    const L = new Lienzo(16, 14);
    L.rect(0, 0, 16, 6, mad[2]); L.hl(0, 0, 16, mad[3]); L.hl(0, 5, 16, mad[0]);
    L.rect(0, 6, 16, 8, mad[1]); L.hl(0, 6, 16, mad[2]); L.hl(0, 13, 16, mad[0]);
    for (const x of [0, 15]) { L.vl(x, 0, 14, metal[0]); }
    L.vl(1, 0, 14, metal[1]); L.vl(14, 0, 14, metal[1]); L.hl(0, 5, 16, metal[1]);
    L.rect(6, 4, 4, 4, oro ? '#f2c14e' : metal[2]); L.px(7, 6, '#1a1010'); L.px(8, 6, '#1a1010');
    return L.contorno();
  };
  def('cofre', cofre(['#4a2a16', '#6e4222', '#925a30', '#b67640'], ['#3a3a44', '#6a6a78', '#b0b0c0']));
  def('cofre_ruinas', cofre(['#1d2e36', '#2c4550', '#3e5f6b', '#56808c'], ['#7a5a10', '#c8962a', '#f2c14e'], true));
  // cama
  const cama = new Lienzo(16, 16);
  cama.rect(0, 2, 16, 12, '#6e4222'); cama.rect(1, 3, 14, 10, '#c84858'); cama.hl(1, 3, 14, '#e86878'); cama.hl(1, 12, 14, '#8a2a3a');
  cama.rect(2, 3, 12, 3, '#f0ecf0'); cama.hl(2, 3, 12, '#ffffff'); cama.hl(2, 5, 12, '#c8c0d0');
  for (let y = 7; y < 13; y += 3) cama.hl(1, y, 14, '#b03848');
  cama.rect(0, 0, 16, 3, '#925a30'); cama.hl(0, 0, 16, '#b67640'); cama.rect(0, 14, 2, 2, '#4a2a16'); cama.rect(14, 14, 2, 2, '#4a2a16');
  def('cama', cama.contorno());
  // puerta
  const puerta = (abierta) => {
    const L = new Lienzo(16, 22);
    L.rect(0, 0, 16, 2, '#4a2a16'); L.vl(0, 0, 22, '#4a2a16'); L.vl(15, 0, 22, '#4a2a16'); L.vl(1, 0, 22, '#6e4222'); L.vl(14, 0, 22, '#6e4222');
    if (abierta) { L.rect(2, 2, 12, 20, '#120a06'); L.rect(2, 2, 3, 20, '#925a30'); L.vl(2, 2, 20, '#b67640'); L.vl(4, 2, 20, '#6e4222'); }
    else {
      for (let x = 2; x < 14; x += 3) { L.rect(x, 2, 3, 20, '#925a30'); L.vl(x, 2, 20, '#b67640'); L.vl(x + 2, 2, 20, '#6e4222'); }
      L.hl(2, 6, 12, '#4a2a16'); L.hl(2, 16, 12, '#4a2a16'); L.rect(11, 11, 2, 2, '#f2c14e');
    }
    return L.contorno();
  };
  def('puerta', puerta(false)); def('puerta_abierta', puerta(true));
  def('maceta', (() => { const L = new Lienzo(10, 14); L.rect(2, 8, 6, 6, '#c86a4a'); L.hl(1, 8, 8, '#e08a6a'); L.vl(7, 9, 5, '#8a4a30'); L.vl(5, 2, 6, '#3a6a28'); L.px(4, 4, '#4a8a32'); L.px(6, 5, '#4a8a32'); L.esfera(5, 2, 2.2, 2, ['#a04070', '#e070a0', '#ffc0e0'], { brillo: '#ffffff' }); return L.contorno(); })());
  // cultivos en 4 etapas
  const HOJA = ['#1e3a18', '#2e5a22', '#428030', '#5ea440', '#86c860'];
  const brote = (L, x, y, alto, hojas) => { L.vl(x, y - alto, alto, HOJA[1]); for (let k = 0; k < hojas; k++) { const hy = y - 1 - k * 2, lado = k % 2 ? 1 : -1; L.px(x + lado, hy, HOJA[3]); L.px(x + lado * 2, hy - 1, HOJA[4]); } };
  for (const [planta, fruto] of Object.entries({ baya: ['#1a5a8c', '#2f8fd0', '#58c8ff', '#c8f4ff'], hongo: CRISTAL_R, bulbo: ['#6a2a10', '#b0501a', '#e0803a', '#ffc080'], trigo: ['#7a5a10', '#c8962a', '#f2c14e', '#fff0a0'] })) {
    for (let e = 0; e < 4; e++) {
      const L = new Lienzo(14, 16);
      L.elipse(7, 14.5, 5, 1.5, '#3a2416');
      if (planta === 'hongo') {
        if (e === 0) { L.px(7, 13, '#e9e0c6'); L.px(7, 12, fruto[2]); }
        else { const h = [0, 3, 5, 7][e], rr = [0, 2, 3, 4.5][e]; L.rect(6, 14 - h, 2, h, '#e9e0c6'); L.vl(7, 14 - h, h, '#b7aa8a'); L.esfera(7, 14 - h, rr, rr * 0.75, fruto, { brillo: '#ffffff', donde: (x, y) => y <= 14 - h + 1 }); }
      } else if (planta === 'trigo') {
        const alto = [2, 5, 9, 12][e];
        for (const x of [4, 7, 10]) { L.vl(x, 14 - alto, alto, e === 3 ? fruto[1] : HOJA[2]); if (e >= 2) { L.rect(x - 1, 14 - alto - 2, 2, 3, e === 3 ? fruto[2] : HOJA[3]); L.px(x - 1, 14 - alto - 2, e === 3 ? fruto[3] : HOJA[4]); } }
      } else {
        brote(L, 7, 14, [2, 5, 8, 10][e], [1, 2, 3, 4][e]);
        if (e === 3) {
          if (planta === 'baya') for (const [x, y] of [[4, 6], [10, 5], [5, 9], [9, 9]]) L.esfera(x + 0.5, y + 0.5, 1.5, 1.5, fruto, { brillo: fruto[3] });
          else { L.esfera(7, 12.5, 3, 2.4, fruto, { brillo: fruto[3] }); brote(L, 7, 11, 4, 2); }
        } else if (e === 2 && planta === 'baya') { L.px(5, 7, '#2f8fd0'); L.px(9, 7, '#2f8fd0'); }
      }
      const C = L.contorno('#0a1206', 0.7);
      def(`cultivo_${planta}_${e}`, C);
      if (e === 3) def(`cultivo_${planta}_3_luz`, C.solo(fruto));
    }
  }
  // plantas silvestres
  const fib = new Lienzo(14, 16), rf = azar(91);
  for (let k = 0; k < 7; k++) { let x = 3 + k * 1.3, y = 15; const alto = 7 + Math.floor(rf() * 7); for (let s = 0; s < alto; s++) { fib.px(x, y, s > alto - 3 ? '#e0d890' : s > alto / 2 ? '#b8b060' : '#8a8040'); y--; if (s > 3 && rf() < 0.35) x += k < 3 ? -1 : 1; } }
  def('planta_fibra', fib.contorno('#141008', 0.7));
  const bul = new Lienzo(14, 14);
  bul.esfera(7, 11, 3.5, 2.8, ['#6a2a10', '#b0501a', '#e0803a', '#ffc080'], { brillo: '#fff0d0' });
  brote(bul, 7, 9, 6, 3); brote(bul, 5, 9, 4, 2); brote(bul, 9, 9, 5, 2);
  def('bulbo_silvestre', bul.contorno('#0a1206', 0.7));
  // vasija, huevo, columna y trono
  def('vasija', (() => { const L = new Lienzo(12, 15); L.esfera(6, 9, 5.5, 5.5, ['#4a2414', '#7a3e24', '#a85a36', '#c87a4e', '#e8a070']); L.rect(3, 1, 6, 3, '#7a3e24'); L.hl(2, 1, 8, '#c87a4e'); L.hl(1, 8, 11, '#e8c070'); L.hl(1, 10, 11, '#5a2a14'); return L.contorno(); })());
  // huevos de larva: cáscara clara con la cría verde que brilla dentro
  const huevo = def('huevo_larva', (() => {
    const L = new Lienzo(16, 14);
    for (const [x, y, r] of [[5, 8, 4], [11, 8, 4], [8, 5, 4]]) { L.esfera(x, y, r, r * 1.1, ['#7a6a3a', '#b0a070', '#d8c898', '#f4ecc8'], { brillo: '#ffffff' }); L.px(x, y + 1, '#7ac83a'); L.px(x + 1, y + 1, '#4a8a2a'); L.px(x, y + 2, '#4a8a2a'); }
    L.linea(4, 6, 6, 10, '#a89060'); L.linea(10, 5, 12, 9, '#a89060');
    return L.contorno('#2a2010', 0.7);
  })());
  def('huevo_larva_luz', huevo.solo(['#7ac83a']));
  def('columna', (() => { const R = RAMPA.ladrillo, L = new Lienzo(12, 28); L.rect(0, 0, 12, 4, R[4]); L.hl(0, 0, 12, R[5]); L.hl(0, 3, 12, R[2]); L.rect(2, 4, 8, 20, R[3]); L.vl(2, 4, 20, R[4]); L.vl(3, 4, 20, R[4]); L.vl(9, 4, 20, R[1]); L.vl(6, 4, 20, R[2]); L.rect(0, 24, 12, 4, R[3]); L.hl(0, 24, 12, R[4]); L.hl(0, 27, 12, R[1]); L.px(4, 12, '#3f6b47'); L.px(5, 13, '#5c8f52'); return L.contorno(); })());
  def('trono', (() => { const R = RAMPA.ladrillo, L = new Lienzo(26, 30); L.rect(2, 0, 22, 18, R[3]); L.hl(2, 0, 22, R[5]); L.rect(5, 3, 16, 14, '#4a1a3a'); L.rect(6, 4, 14, 12, '#6a2a5a'); L.hl(6, 4, 14, '#8a4a7a'); for (const x of [4, 12, 20]) { L.esfera(x + 0.5, 1, 1.6, 1.6, ['#3a1050', '#6a2aa0', '#c080ff']); } L.rect(0, 16, 26, 6, R[3]); L.hl(0, 16, 26, R[4]); L.rect(4, 18, 18, 3, '#6a2a5a'); L.rect(0, 22, 26, 8, R[2]); L.hl(0, 22, 26, R[4]); L.hl(0, 29, 26, R[0]); return L.contorno(); })());
  // estatuas de los guardianes: apagadas y con su cristal encendido
  const P = RAMPA.ruina;
  const estatua = (jefe, on) => {
    const L = new Lienzo(16, 28);
    L.rect(0, 22, 16, 6, P[3]); L.hl(0, 22, 16, P[5]); L.hl(0, 27, 16, P[1]);
    L.rect(2, 19, 12, 3, P[2]); L.hl(2, 19, 12, P[4]);
    const piedra = ['#2a3a40', '#3e545c', '#56727a', '#748e94', '#98b0b4'];
    if (jefe === 'topo') { L.esfera(8, 13, 6.5, 6, piedra); L.esfera(8, 12, 2, 1.5, piedra.slice(2)); L.rect(4, 5, 8, 2, piedra[3]); for (const x of [4, 7, 10]) L.px(x + 1, 4, piedra[4]); L.px(5, 10, piedra[0]); L.px(10, 10, piedra[0]); }
    else if (jefe === 'reina') { for (let k = 0; k < 4; k++) L.esfera(8, 17 - k * 3.5, 5 - k * 0.6, 2.4, piedra); L.esfera(8, 5, 3.5, 3, piedra); L.px(6, 4, piedra[0]); L.px(10, 4, piedra[0]); }
    else { L.rect(5, 8, 6, 11, piedra[2]); L.vl(5, 8, 11, piedra[3]); L.vl(10, 8, 11, piedra[1]); L.esfera(8, 6, 3.4, 3.4, piedra); L.rect(4, 3, 8, 2, piedra[1]); L.vl(13, 4, 15, piedra[1]); L.esfera(13, 3, 1.6, 1.6, piedra.slice(1)); }
    if (on) {
      const col = { topo: ['#8a5a10', '#f2c14e', '#fff0a0'], reina: ['#3a7a1a', '#7ac83a', '#cfff8a'], ozmar: ['#5a2a8a', '#c080ff', '#f0d8ff'] }[jefe];
      L.esfera(8, 1.5, 2, 2.2, col, { brillo: '#ffffff' });
      if (jefe === 'topo') { L.px(5, 10, col[1]); L.px(10, 10, col[1]); } else if (jefe === 'reina') { L.px(6, 4, col[1]); L.px(10, 4, col[1]); } else { L.px(7, 6, col[1]); L.px(9, 6, col[1]); L.px(13, 3, col[2]); }
      return { L, col };
    }
    return { L, col: null };
  };
  for (const jefe of ['topo', 'reina', 'ozmar']) {
    def(`estatua_${jefe}`, estatua(jefe, false).L.contorno());
    const { L, col } = estatua(jefe, true), C = L.contorno();
    def(`estatua_${jefe}_on`, C); def(`estatua_${jefe}_on_luz`, C.solo(col.concat(['#ffffff'])));
  }
}

// ---------------------------------------------------------------- criaturas

function criaturasNuevas() {
  const ROJO = ['#3a0a10', '#6a1a22', '#a82a3a', '#e8504a', '#ff8a7a', '#ffd0c0'];
  const bab = (rx, ry, alto) => {
    const L = new Lienzo(18, 14), cx = 9, base = 13, cy = base - ry;
    L.esfera(cx, cy, rx, ry, ROJO, { brillo: '#fff0e8', amb: 0.35, donde: (x, y) => y < base });
    const ey = Math.round(cy + ry * 0.05) - alto;
    for (const ex of [Math.round(cx - rx * 0.4) - 1, Math.round(cx + rx * 0.3)]) { L.vl(ex, ey, 2, TINTA); L.px(ex, ey, '#ffffff'); L.vl(ex + 1, ey, 2, TINTA); }
    L.hl(Math.round(cx) - 2, ey - 2, 2, TINTA); L.hl(Math.round(cx) + 1, ey - 2, 2, TINTA); // cejas de enfado
    L.px(Math.round(cx) - 1, ey + 3, ROJO[0]); L.px(Math.round(cx), ey + 3, ROJO[0]);
    return L.contorno();
  };
  def('babosa_roja_0', bab(7, 5, 0)); def('babosa_roja_1', bab(8.2, 4, 0)); def('babosa_roja_2', bab(6, 6.5, 1));

  // Setón: un hongo que anda, con sombrero de motas
  const SOMB = ['#4a1a0a', '#7e2e14', '#b8481e', '#e06a2a', '#ff9a4a'];
  const seton = (f) => {
    const L = new Lienzo(18, 20);
    L.rect(6, 10, 6, 7, '#e8dcc0'); L.vl(11, 10, 7, '#b8a888'); L.vl(6, 10, 7, '#f8f0e0');
    L.px(7, 12, TINTA); L.px(10, 12, TINTA); L.px(7, 11, '#ffffff'); L.hl(8, 14, 2, '#8a6a5a');
    const pie = f === 1 ? [[5, 17], [11, 18]] : f === 0 ? [[6, 18], [10, 17]] : [[5, 18], [11, 18]];
    for (const [x, y] of pie) { L.rect(x, y, 2, 2, '#c8b898'); }
    const aplasta = f === 2 ? 1.5 : 0;
    L.esfera(9, 7 + aplasta, 8.5 + aplasta, 6 - aplasta, SOMB, { brillo: '#ffd0a0', donde: (x, y) => y < 11 });
    for (const [x, y] of [[5, 4], [11, 3], [13, 7], [7, 8], [3, 8]]) { L.rect(x, y + aplasta, 2, 2, '#fff4e0'); L.px(x + 1, y + 1 + aplasta, '#d8c8b0'); }
    return L.contorno();
  };
  for (let f = 0; f < 3; f++) def(`seton_${f}`, seton(f));

  // Larva grande: más segmentos, más grande, babea ácido
  const CREMA = ['#5a4230', '#84664a', '#a88a62', '#c4a87c', '#dcc69a'];
  const CAB = ['#3a1410', '#6a2a1c', '#9a4630', '#c86a48'];
  const larvaG = (f) => {
    const L = new Lienzo(34, 18);
    for (let k = 0; k < 6; k++) { const sube = (k + f) % 2 ? 1 : 0; L.esfera(5 + k * 4.4, 10 - sube, 5, 5.4, CREMA, { amb: 0.4 }); L.px(4 + k * 4.4, 15 + ((k + f) % 2), CREMA[0]); }
    L.esfera(29, 9, 5, 5.6, CAB, { brillo: '#f0a080' });
    L.px(29, 7, TINTA); L.px(30, 7, '#ffd040'); L.px(31, 7, TINTA);
    L.rect(31, 11, 3, 2, '#2a1414'); L.vl(32, 13, 2 + f, '#7ac83a'); L.px(32, 15 + f, '#cfff8a');
    for (let k = 0; k < 3; k++) L.px(8 + k * 6, 6 - ((k + f) % 2), '#7ac83a');
    return L.contorno();
  };
  def('larva_grande_0', larvaG(0)); def('larva_grande_1', larvaG(1));

  // Grutos: criaturas de las cuevas con orejas puntiagudas y ojos amarillos
  const PIEL_G = ['#1e2a18', '#2e4024', '#425a32', '#5a7642', '#7a9658'];
  const gruto = (tipo, f) => {
    const bruto = tipo === 'bruto', L = new Lienzo(bruto ? 24 : 16, bruto ? 24 : 19);
    const cx = bruto ? 12 : 8, s = bruto ? 1.5 : 1, y0 = bruto ? 2 : 1, bob = f === 1 ? 1 : 0;
    // piernas
    const piernas = f === 0 ? [-2, 2] : f === 1 ? [-1, 1] : [-3, 3];
    for (const dx of piernas) L.rect(cx + dx * s - 1, (bruto ? 19 : 15), Math.round(2 * s), bruto ? 5 : 4, PIEL_G[1]);
    // cuerpo
    L.esfera(cx, y0 + 10 * s - bob, 4.5 * s, 4.2 * s, PIEL_G, { amb: 0.3 });
    if (!bruto) L.rect(cx - 4, y0 + 11 - bob, 8, 3, tipo === 'chaman' ? '#4a2a6a' : '#6a4a2a');
    if (tipo === 'chaman') { L.esfera(cx, y0 + 9 - bob, 5, 5, ['#2a1a3a', '#3a2a5a', '#5a3a7a', '#7a5a9a'], { donde: (x, y) => y > y0 + 6 - bob }); }
    // cabeza con orejas
    const hy = y0 + 4 * s - bob;
    L.esfera(cx, hy, 4 * s, 3.6 * s, PIEL_G, { brillo: PIEL_G[4] });
    for (const lado of [-1, 1]) { L.linea(cx + lado * 3 * s, hy - 1, cx + lado * 6 * s, hy - 3 * s, PIEL_G[3]); L.px(cx + lado * 6 * s, hy - 3 * s, PIEL_G[4]); }
    if (tipo === 'chaman') { L.esfera(cx, hy - 2, 4.5, 3.4, ['#2a1a3a', '#3a2a5a', '#5a3a7a', '#7a5a9a'], { donde: (x, y) => y < hy }); }
    const ojo = '#ffd040';
    L.px(cx - Math.round(1.5 * s), hy, ojo); L.px(cx + Math.round(1.5 * s) - 1, hy, ojo);
    if (bruto) { L.px(cx - 2, hy, ojo); L.px(cx + 1, hy, ojo); L.hl(cx - 2, hy + 3, 4, '#e8e0c8'); }
    // arma
    const ataque = f === 3, prepara = f === 2;
    if (tipo === 'lancero') { const lx = ataque ? cx + 6 : cx + 5; L.linea(lx, prepara ? 4 : 2, lx - (ataque ? -2 : 0), 17, '#8a5a36'); L.rect(lx - 1, prepara ? 1 : -1, 3, 3, '#c8ccd8'); L.px(lx, prepara ? 0 : -2, '#ffffff'); }
    if (tipo === 'chaman') { L.vl(cx + 5, 3, 15, '#5a3a20'); L.esfera(cx + 5, 2.5, 2, 2, ['#3a1050', '#6a2aa0', '#c080ff', '#ffffff'], { brillo: '#ffffff' }); }
    if (bruto) { const arriba = prepara; L.rect(cx + 8, arriba ? 0 : 8, 5, 6, '#6a6a7a'); L.hl(cx + 8, arriba ? 0 : 8, 5, '#9a9aaa'); L.linea(cx + 6, arriba ? 6 : 12, cx + 9, arriba ? 4 : 14, '#5a3a20'); }
    return L.contorno();
  };
  for (const [tipo, nombre] of [['lancero', 'gruto_lancero'], ['chaman', 'gruto_chaman'], ['bruto', 'gruto_bruto']]) for (let f = 0; f < 4; f++) def(`${nombre}_${f}`, gruto(tipo, f));
  // los de antes con su nombre nuevo (sin destello blanco aparte: lo hace el sombreador)
  for (let f = 0; f < 3; f++) if (!SPR[`babosa_${f}`]) void 0;
}

// ---------------------------------------------------------------- jefes nuevos

function jefesNuevos() {
  // Reina Larva: enorme, con caparazón de corona y saco de ácido brillante
  const CREMA = ['#4a3a24', '#7a6040', '#a88a5e', '#d0b88a', '#ecdcb0', '#fff4d8'];
  const CAP = ['#2a1a0a', '#4a2e14', '#6e4420', '#94622e', '#bc8440'];
  const ACIDO = ['#2a5a1a', '#4a8a2a', '#7ac83a', '#b8f070', '#eaffc0'];
  const reina = (f) => {
    const L = new Lienzo(56, 44);
    for (let k = 0; k < 5; k++) L.esfera(28 + (k - 2) * 7, 32 - Math.abs(k - 2) * 1.5 + ((k + f) % 2), 7.5 - Math.abs(k - 2) * 0.6, 7, CREMA, { amb: 0.35 });
    for (let k = 0; k < 8; k++) { const x = 9 + k * 5.4, y = 38 + ((k + f) % 2); L.linea(x, y - 2, x - 1, y + 3, CAP[1]); }
    L.esfera(28, 22, 12, 11, CREMA, { amb: 0.3 });
    L.esfera(28, 26, 7, 6, ACIDO, { brillo: ACIDO[4] });
    L.esfera(28, 12, 10, 8, CAP, { brillo: CAP[4] });
    for (let k = 0; k < 5; k++) { const x = 20 + k * 4; L.linea(x, 6, x + (k - 2), 0 + Math.abs(k - 2), CAP[3]); L.px(x + (k - 2), Math.abs(k - 2), CAP[4]); }
    for (const ex of [23, 31]) { L.rect(ex, 13, 3, 2, '#ffd040'); L.px(ex + 1, 13, '#ffffff'); }
    if (f === 2) { L.rect(24, 17, 9, 4, '#1a0a0a'); L.hl(25, 18, 7, ACIDO[2]); } else { L.hl(25, 18, 7, '#1a0a0a'); }
    for (const lado of [-1, 1]) L.linea(28 + lado * 6, 18, 28 + lado * 11, 22 + (f % 2), CAP[2]);
    return L.contorno('#140a04', 0.7);
  };
  for (let f = 0; f < 3; f++) { const C = reina(f); def(`reina_${f}`, C); def(`reina_${f}_luz`, C.solo(ACIDO.slice(2).concat(['#ffd040']))); }
  // Ozmar el Corrompido: gruto chamán rey, encapuchado, con corona de hueso y báculo
  const MANTO = ['#140a1e', '#24143a', '#3a2258', '#563478', '#764a9a'];
  const ozmar = (f) => {
    const L = new Lienzo(24, 30), sentado = f === 3, brazos = f === 2;
    L.esfera(12, 20, 8, 9, MANTO, { amb: 0.3, donde: (x, y) => y < (sentado ? 27 : 29) });
    for (let k = 0; k < 4; k++) L.px(6 + k * 4, 28 - (k % 2), MANTO[1]);
    L.esfera(12, 9, 6, 6, MANTO, { brillo: MANTO[4] });
    L.esfera(12, 10, 4, 3.6, ['#1e2a18', '#2e4024', '#425a32', '#5a7642']);
    L.rect(9, 9, 2, 2, '#e080ff'); L.rect(13, 9, 2, 2, '#e080ff'); L.px(9, 9, '#ffffff'); L.px(13, 9, '#ffffff');
    for (let k = 0; k < 5; k++) { const x = 8 + k * 2; L.vl(x, 1 + Math.abs(k - 2), 3, '#e8e0c8'); L.px(x, Math.abs(k - 2), '#ffffff'); }
    L.px(12, 3, '#c080ff');
    const bx = brazos ? 20 : 19, by = brazos ? 2 : 8;
    L.vl(bx, by + 3, 26 - by - 3, '#5a3a20'); L.vl(bx + 1, by + 3, 26 - by - 3, '#3a2410');
    L.esfera(bx + 0.5, by + 1.5, 2.6, 2.6, ['#3a1050', '#6a2aa0', '#c080ff', '#f0d8ff'], { brillo: '#ffffff' });
    if (brazos) { L.linea(5, 16, 2, 8, MANTO[3]); L.esfera(2, 7, 2, 2, ['#3a1050', '#6a2aa0', '#c080ff', '#f0d8ff']); L.linea(18, 16, 20, 9, MANTO[3]); }
    else { L.linea(6, 16, 5, 20, MANTO[3]); L.linea(17, 16, 19, 18, MANTO[3]); }
    return L.contorno('#08040e', 0.7);
  };
  for (let f = 0; f < 4; f++) { const C = ozmar(f); def(`ozmar_${f}`, C); def(`ozmar_${f}_luz`, C.solo(['#e080ff', '#c080ff', '#f0d8ff', '#ffffff'])); }
}

// ---------------------------------------------------------------- armadura puesta (encima del cuerpo)

function armaduraPuesta() {
  const MAT = { cobre: ['#7c3a22', '#c8693a', '#f2a466'], estano: ['#5f5c7a', '#a9a6c6', '#ebe9fb'], hierro: ['#2c3a4f', '#6787a6', '#b9d6ec'], larva: ['#8a7a4a', '#d8c890', '#f4ecc0'], carmesi: ['#5a0e16', '#c0283a', '#ff7a7a'], musgo: ['#2a4a1c', '#5a8a32', '#a8d870'] };
  for (const [m, c] of Object.entries(MAT)) for (const d of ['ab', 'ar', 'de']) for (let paso = 0; paso < 4; paso++) {
    const Y = paso % 2 ? 0 : 1;
    // casco: cúpula sobre el pelo
    const casco = new Lienzo(14, 21);
    casco.esfera(d === 'de' ? 6.2 : 7, 4 + Y, 7.2, 4.6, [c[0], c[1], c[1], c[2]], { donde: (x, y) => y <= 5 + Y });
    casco.hl(d === 'de' ? 0 : 0, 5 + Y, 14, c[0]);
    if (d === 'ab') { casco.px(7, 1 + Y, c[2]); casco.px(6, 2 + Y, c[2]); }
    // peto
    const peto = new Lienzo(14, 21);
    if (d === 'de') { peto.rect(4, 11 + Y, 6, 4, c[1]); peto.hl(4, 11 + Y, 6, c[2]); peto.vl(9, 12 + Y, 3, c[0]); }
    else { peto.rect(3, 11 + Y, 8, 4, c[1]); peto.hl(3, 11 + Y, 8, c[2]); peto.vl(10, 12 + Y, 3, c[0]); peto.vl(7, 12 + Y, 3, c[0]); peto.px(2, 12 + Y, c[1]); peto.px(11, 12 + Y, c[0]); }
    // botas (siguen a las piernas)
    const botas = new Lienzo(14, 21);
    const pierna = (x, alza) => { botas.rect(x, 18 - (alza ? 1 : 0), 2, 3 - (alza ? 1 : 0), c[1]); botas.px(x, 18 - (alza ? 1 : 0), c[2]); };
    if (d === 'de') { if (paso === 1) { pierna(3, false); pierna(8, true); } else if (paso === 3) { pierna(4, true); pierna(7, false); } else { pierna(5, false); pierna(7, false); } }
    else { pierna(4, paso === 1); pierna(8, paso === 3); }
    for (const [pieza, L] of [['casco', casco], ['peto', peto], ['botas', botas]]) {
      const C = L.contorno('#100806', 0.55);
      def(`eq_${pieza}_${m}_${d}_${paso}`, C);
      if (d === 'de') def(`eq_${pieza}_${m}_iz_${paso}`, C.voltear());
    }
  }
}

// ---------------------------------------------------------------- iconos

function iconosNuevos() {
  const C = (L) => L.contorno();
  const MET = { madera: ['#704626', '#b07a48', '#dcae7a'], cobre: ['#7c3a22', '#c8693a', '#f2a466'], estano: ['#5f5c7a', '#a9a6c6', '#ebe9fb'], hierro: ['#2c3a4f', '#6787a6', '#b9d6ec'], oro: ['#8a5a10', '#e0a830', '#fff0a0'], larva: ['#8a7a4a', '#d8c890', '#f4ecc0'] };
  // materiales
  def('i_fibra', C(rej({ a: '#d8d090', b: '#a8a060', c: '#6a6430' }, `
    ..a..a..
    .ab.ab.a
    .ab.ab.b
    .bb.bb.b
    cccccccc
    .bb.bb.b
    .b..b..b`)));
  def('i_baba_roja', C(new Lienzo(9, 7).esfera(4.5, 3.8, 4, 3.2, ['#6a1a22', '#a82a3a', '#e8504a', '#ff8a7a'], { brillo: '#fff0e8' })));
  def('i_quitina', C(rej({ a: '#f4ecc0', b: '#d8c890', c: '#a89a60', d: '#6a5a30' }, `
    ..aaaa..
    .abbbba.
    abbccbba
    bbcddcbb
    .ccddcc.
    ..dddd..`)));
  def('i_hueso', C(rej({ a: '#ffffff', b: '#e0d8c8', c: '#a89a86' }, `
    ab......
    bbb.....
    .bab....
    ..bab...
    ...bab..
    ....bbb.
    .....bba`)));
  def('i_gema_antigua', C(rej({ a: '#e8fffc', b: '#7af0e0', c: '#2ab8b0', d: '#0e6a6a' }, `
    ..abb..
    .abbbc.
    abbbccd
    bbbccdd
    .cccdd.
    ..cdd..`)));
  // bloques: cubitos con tapa clara y cara oscura
  const cubo = (R) => { const L = new Lienzo(10, 10); L.rect(0, 0, 10, 5, R[3]); L.hl(0, 0, 10, R[4]); L.rect(0, 5, 10, 5, R[2]); L.hl(0, 9, 10, R[1]); L.px(2, 2, R[5] || R[4]); L.px(6, 7, R[1]); return C(L); };
  for (const [id, r] of [['bloque_tierra', 'tierra'], ['bloque_arcilla', 'arcilla'], ['bloque_colmena', 'colmena'], ['bloque_piedra', 'piedra'], ['bloque_ladrillo', 'ladrillo'], ['pared_madera', 'madera']]) def('i_' + id, cubo(RAMPA[r]));
  def('i_suelo_madera', C(new Lienzo(10, 8).rect(0, 0, 10, 8, '#7a4a24').hl(0, 0, 10, '#94603a').hl(0, 3, 10, '#3a2010').hl(0, 4, 10, '#94603a').hl(0, 7, 10, '#3a2010').vl(4, 0, 4, '#3a2010').vl(7, 4, 4, '#3a2010')));
  def('i_suelo_losa', C(new Lienzo(10, 8).rect(0, 0, 10, 8, '#2c343e').rect(1, 1, 3, 2, '#485462').rect(5, 1, 4, 2, '#485462').rect(1, 4, 4, 3, '#485462').rect(6, 4, 3, 3, '#485462').px(1, 1, '#5a6878').px(5, 1, '#5a6878')));
  def('i_alfombra', C(new Lienzo(10, 8).rect(0, 2, 10, 6, '#7a1e30').hl(0, 2, 10, '#d0a040').hl(0, 7, 10, '#d0a040').esfera(8, 4, 2.4, 3, ['#5a1420', '#7a1e30', '#a83040'])));
  // herramientas
  for (const m of ['madera', 'cobre']) {
    const [o, b, c] = MET[m];
    def(`i_azada_${m}`, C(rej({ h: '#8a5a36', p: '#5a3a26', o, b, c }, `
      .cbbb....
      cbbbo....
      .o.h.....
      ...h.....
      ....h....
      .....h...
      ......h..
      .......p.`)));
  }
  def('i_regadera', C(rej({ o: '#7c3a22', b: '#c8693a', c: '#f2a466', w: '#8ad0ff' }, `
    ...ccc....
    ..c...c...
    .bbbbbb..w
    cbbbbbbbbo
    bbbbbbbbo.
    .ooooooo..`)));
  for (const [m, col] of [['madera', '#8a5a36'], ['cobre', '#c8693a']]) {
    def(`i_cana_${m}`, C(rej({ h: col, H: '#3a2a20', l: '#e8e8f0', r: '#9aa6b8' }, `
      .........h
      ........h.
      .......h.l
      ......h..l
      .....h...l
      ...rh....l
      ..rrh....l
      ..hr.....l
      .H.......l`)));
  }
  for (const m of ['cobre', 'hierro']) {
    const [o, b, c] = MET[m];
    def(`i_maza_${m}`, C(rej({ h: '#8a5a36', p: '#5a3a26', o, b, c }, `
      ....c.c.
      ...cbbbc
      ...bbbbo
      ..cbbbo.
      ...oho..
      ..h.....
      .h......
      p.......`)));
  }
  def('i_garra_reina', C(rej({ a: '#eaffc0', b: '#7ac83a', c: '#4a8a2a', d: '#2a5a1a', h: '#94622e' }, `
    ........a
    .......ab
    ......abc
    .....abc.
    ....abc..
    ..dbbc...
    ..hdc....
    .h.......
    h........`)));
  def('i_baculo_ozmar', C(rej({ a: '#f0d8ff', b: '#c080ff', c: '#6a2aa0', h: '#5a3a20', H: '#3a2410' }, `
    ......abb
    .....abbc
    .....bbcc
    ....hcc..
    ...hH....
    ..hH.....
    .hH......
    hH.......`)));
  for (const [m, cuerda] of [['madera', '#e8e0c8'], ['estano', '#ffffff']]) {
    const [o, b, c] = m === 'madera' ? MET.madera : MET.estano;
    def(`i_arco_${m}`, C(rej({ o, b, c, s: cuerda }, `
      ..bbc...
      .b...s..
      b.....s.
      b......s
      b......s
      b.....s.
      .b...s..
      ..bbo...`)));
  }
  def('i_flecha', C(rej({ m: '#a07040', p: '#9aa6b8', f: '#e8e0d0' }, `
    ......pp
    .....mpp
    ....m...
    ...m....
    ..m.....
    ff......
    f.......`)));
  def('i_flecha_cobre', C(rej({ m: '#a07040', p: '#e08a50', f: '#e8e0d0' }, `
    ......pp
    .....mpp
    ....m...
    ...m....
    ..m.....
    ff......
    f.......`)));
  // armadura: casco, peto y botas por material
  for (const m of ['cobre', 'estano', 'hierro', 'larva']) {
    const [o, b, c] = MET[m];
    def(`i_casco_${m}`, C(rej({ o, b, c }, `
      ..ccbb..
      .cbbbbo.
      cbbbbbbo
      bbbbbbbo
      boo..oob
      o......o`)));
    def(`i_peto_${m}`, C(rej({ o, b, c }, `
      .cb....bc.
      cbbb..bbbo
      bbcbbbbbbo
      .bcbbbbbo.
      .bbbbbbbo.
      .bbbbbboo.
      ..bbbboo..`)));
    def(`i_botas_${m}`, C(rej({ o, b, c }, `
      .cb..cb.
      .bb..bb.
      .bb..bb.
      cbbo.bbbc
      bbbo.bbbo`)));
  }
  // accesorios
  const anillo = (oro, gema) => C(rej({ a: oro[2], b: oro[1], c: oro[0], g: gema[1], G: gema[0], w: '#ffffff' }, `
    ..wg..
    .gGGg.
    .abba.
    a....b
    b....c
    .bccc.`));
  def('i_anillo_cobre', anillo(MET.cobre, ['#1a8a9c', '#3fd8e8']));
  def('i_anillo_oro', anillo(MET.oro, ['#a02020', '#ff4040']));
  def('i_collar_cristal', C(rej({ a: '#fff0a0', b: '#e0a830', g: '#3fd8e8', G: '#1a8a9c', w: '#ffffff' }, `
    a......a
    .b....b.
    ..b..b..
    ...wg...
    ...gG...
    ....G...`)));
  def('i_amuleto_topo', C(rej({ a: '#c8a070', c: '#ffe2d2', C: '#d99c8a', o: '#f2c14e' }, `
    a.....a
    .a...a.
    ..aoa..
    .cCcCc.
    .c.c.c.
    .C.C.C.`)));
  def('i_broche_larva', C(new Lienzo(9, 9).esfera(4.5, 4.5, 4, 4, ['#2a5a1a', '#4a8a2a', '#7ac83a', '#cfff8a'], { brillo: '#ffffff' }).rect(1, 4, 7, 1, '#d8c890')));
  def('i_corona_ozmar', C(rej({ a: '#ffffff', b: '#e8e0c8', c: '#a89a86', g: '#c080ff', G: '#6a2aa0' }, `
    a..a..a..a
    b..b.ab..b
    bb.bgbb.bb
    bbbbGbbbbb
    cccccccccc`)));
  // comida y semillas
  def('i_bulbo', C(new Lienzo(9, 11).esfera(4.5, 7, 3.6, 3.4, ['#6a2a10', '#b0501a', '#e0803a', '#ffc080'], { brillo: '#fff0d0' }).linea(4, 3, 3, 0, '#428030').linea(5, 3, 6, 0, '#5ea440')));
  def('i_trigo', C(rej({ a: '#fff0a0', b: '#f2c14e', c: '#c8962a', t: '#7a5a10' }, `
    .a.a.a.
    .b.b.b.
    abbabba
    .bcbcb.
    ..t.t..
    ..ttt..
    ...t...
    ...t...`)));
  def('i_carne_larva', C(rej({ c: '#f2c6b4', C: '#d88f80', r: '#b24a4a', w: '#fff4ea' }, `
    ..cccc..
    .cwcccC.
    cccCCCCr
    cCCCrrrr
    .Crrrrr.
    ..rrrr..`)));
  def('i_carne_seton', C(rej({ a: '#ffd0a0', b: '#e06a2a', c: '#b8481e', w: '#fff4e0', m: '#e8dcc0' }, `
    ..aab..
    .abwbbc
    abbbwbc
    .cccccc
    ..mmm..
    ..mmm..`)));
  const pez = (cs) => C(rej({ a: cs[3], b: cs[2], c: cs[1], d: cs[0], e: '#14101c', w: '#ffffff' }, `
    ....abb....
    ..abbbbbc.d
    .abwebbbbcdd
    abbbbbbbbcd.
    .cbbbbbbccdd
    ..cccccc..d`));
  def('i_pez_guppy', pez(['#3a5a6a', '#5a8aa0', '#8abed0', '#d0f0ff']));
  def('i_pez_barro', pez(['#3a2a1a', '#6a5032', '#9a7a52', '#c8a878']));
  def('i_pez_cristal', pez(['#0e4a5c', '#2cbccc', '#5ae4f0', '#e0ffff']));
  def('i_pez_dorado', pez(['#8a5a10', '#e0a830', '#f2c14e', '#fff0a0']));
  const semilla = (col) => C(rej({ a: col[0], b: col[1], c: col[2] }, `
    .ab..
    abbc.
    .bc.a
    ..abb
    ..bbc`));
  def('i_semilla_baya', semilla(['#88d8ff', '#3a8ad0', '#1a4a8c']));
  def('i_espora_hongo', semilla(['#b8fbff', '#3fd8e8', '#1a8a9c']));
  def('i_semilla_bulbo', semilla(['#ffc080', '#e0803a', '#8a3a10']));
  def('i_semilla_trigo', semilla(['#fff0a0', '#e0a830', '#8a5a10']));
  // platos: un cuenco, un pan, una brocheta…
  const cuenco = (sopa, brillo) => C(rej({ s: sopa, S: brillo, b: '#c8a070', B: '#8a6040', o: '#5a3a20' }, `
    .SSSsss.
    bsssssSb
    bBbbbbbB
    .bBBBBb.
    ..oooo..`));
  def('i_plato_baya', C(rej({ g: '#c8e8f0', G: '#8ab0c0', s: '#58c8ff', S: '#c8f4ff', t: '#c8a070' }, `
    .tttt.
    gGGGGg
    gsSssg
    gsssSg
    gssssg
    .gggg.`)));
  def('i_plato_hongo', cuenco('#8a5a3a', '#c08a5a'));
  def('i_plato_bulbo', cuenco('#e0803a', '#ffc080'));
  def('i_plato_trigo', C(rej({ a: '#ffe0a0', b: '#e0a850', c: '#a86a28', d: '#6a3a10' }, `
    ..aaab...
    .abbbbbc.
    abbbbbbcd
    bbbbbbccd
    .ccccccd.`)));
  def('i_plato_carne_larva', C(rej({ m: '#a07040', c: '#e09060', C: '#a85a30', g: '#5ea440' }, `
    ........m
    ......cm.
    .....cCm.
    ...gcCm..
    ..cCcm...
    .cCm.....
    .m.......
    m........`)));
  def('i_plato_carne_seton', C(rej({ p: '#e8e8f0', P: '#a8a8b8', c: '#c86a3a', C: '#8a3a1a', w: '#fff0d0' }, `
    ..cccw..
    .ccCCcc.
    pcCCCCcp
    PppppppP
    .PPPPPP.`)));
  def('i_plato_pez_guppy', cuenco('#6ab0d0', '#c0e8ff'));
  def('i_plato_pez_barro', cuenco('#7a6040', '#b89870'));
  def('i_plato_pez_cristal', C(rej({ p: '#e8e8f0', P: '#a8a8b8', a: '#ffd0c8', b: '#ff9a8a', c: '#3fd8e8' }, `
    ..ab.ab.
    .abbabb.
    pbbcbbcp
    PppppppP
    .PPPPPP.`)));
  def('i_plato_pez_dorado', C(rej({ p: '#fff0a0', P: '#c8962a', a: '#f2c14e', b: '#e0a830', w: '#ffffff' }, `
    ..wabb..
    .aabbba.
    pabbbbap
    PppppppP
    .PPPPPP.`)));
  // muebles en icono
  const mini = (n) => { const r = SPR[n]; if (!r) return null; return n; };
  void mini;
  for (const [id, cols] of [['mesa_cobre', MET.cobre], ['mesa_estano', MET.estano], ['mesa_hierro', MET.hierro]]) {
    def('i_' + id, C(rej({ e: cols[2], W: cols[1], d: cols[0], D: '#4a2a1c' }, `
      eeeeeeeeee
      WWWWdWWWWW
      WWWWdWWWWW
      dddddddddd
      D.D....D.D
      D......D..`)));
  }
  for (const [id, cols] of [['yunque_estano', MET.estano], ['yunque_hierro', MET.hierro]]) {
    def('i_' + id, C(rej({ j: cols[2], I: cols[1], i: cols[0], W: '#9a6438', w: '#c28a52', d: '#6e4228' }, `
      jjjjjjjjjj
      IIIIIIIIIi
      ...IIIi...
      ..iIIIii..
      .WWwwwwWW.
      .dddddddd.`)));
  }
  def('i_farol_oro', C(rej({ a: '#fffbe0', b: '#ffe58a', g: '#f2c14e', G: '#b8861a', k: '#3a3640' }, `
    .GggG.
    .gaag.
    .gabg.
    .gbbg.
    .GGGG.
    ..kk..
    ..kk..`)));
  def('i_olla', C(new Lienzo(10, 8).esfera(5, 4.5, 4.6, 3.6, ['#141418', '#24242c', '#363642', '#4c4c5c', '#6a6a7e'], { brillo: '#9a9ab0' }).hl(1, 2, 8, '#c8a060')));
  def('i_cofre', C(rej({ a: '#b67640', b: '#925a30', c: '#6e4222', d: '#4a2a16', m: '#6a6a78', o: '#b0b0c0' }, `
    mbbbbbbbbm
    mabbbbbbam
    mddddooddm
    mccccooccm
    mccccccccm
    mddddddddm`)));
  def('i_cama', C(rej({ w: '#ffffff', W: '#c8c0d0', r: '#e86878', R: '#c84858', k: '#8a2a3a', m: '#925a30' }, `
    mmmmmmmmmm
    mwwwWrrrrm
    mWWWWRRRRm
    mrrrrrrrrm
    mkkkkkkkkm
    m........m`)));
  def('i_puerta', C(rej({ a: '#b67640', b: '#925a30', c: '#6e4222', d: '#4a2a16', o: '#f2c14e' }, `
    dddddd
    dabbbd
    dabbbd
    dddddd
    dabbod
    dabbbd
    dabbbd
    dddddd`)));
  def('i_maceta', C(rej({ f: '#e070a0', F: '#a04070', t: '#3a6a28', p: '#c86a4a', P: '#e08a6a', o: '#8a4a30' }, `
    .fFf.
    ..t..
    .tt..
    PPPPP
    .ppo.
    .ppo.`)));
  const cristalJefe = (col) => C(rej({ a: col[2], b: col[1], c: col[0], w: '#ffffff' }, `
    ...w...
    ..wab..
    .wabbc.
    .abbbc.
    .abbcc.
    ..bcc..
    ...c...`));
  def('i_cristal_topo', cristalJefe(['#8a5a10', '#f2c14e', '#fff0a0']));
  def('i_cristal_reina', cristalJefe(['#3a7a1a', '#7ac83a', '#cfff8a']));
  def('i_cristal_ozmar', cristalJefe(['#5a2a8a', '#c080ff', '#f0d8ff']));
}
