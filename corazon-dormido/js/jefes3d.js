'use strict';
// Los jefes en 3D (LORE.md §6), de frente a la cámara. Cada uno se modela con una función que acepta una escala y
// un cambio de materiales: así las estatuas del santuario son el mismo modelo en piedra, más pequeño.

const MAT_JEFE = (() => {
  const { mat, rampa, TEX } = ESCU;
  return {
    pelaje: mat(['#08070a', '#121014', '#1e1a1e', '#2c2628', '#3c3434', '#4e4442', '#625654'], { tex: (x, y, z) => (ESCU.ruido(x * 1.6, y * 0.7, z * 1.6, 1.3, 201) > 0.62 ? -1 : 0) }),
    pielEnferma: mat(['#1e0c10', '#36161a', '#542428', '#743a38', '#94544c', '#b47264', '#d09482'], { tex: TEX.grumos(203, 1.4, 0.6) }),
    estrella: mat(['#3a0a14', '#6a1424', '#9a2234', '#c83c48', '#e46a6a', '#f6a09a'], { brillo: 0.6 }),
    garra: mat(['#2a2014', '#4a3a22', '#6e5a36', '#948050', '#b8a46e', '#d8c896'], { brillo: 0.4 }),
    clavo: mat(['#0e0a08', '#22160e', '#3a2414', '#56361c', '#744c28', '#8e6436', '#a87e4a'], { brillo: 0.5 }),
    tierra: mat(['#100a06', '#20140c', '#322012', '#46301a', '#5c4024', '#74542e'], { tex: TEX.grumos(207, 1.6, 0.9) }),
    larvaPiel: mat(['#2a2418', '#4a4030', '#6e624a', '#948868', '#b8ae8a', '#d8d0ae', '#f0eacc'], { tex: (x, y, z) => (ESCU.ruido(x, y * 2.4, z, 1.6, 211) > 0.68 ? -1.2 : 0) }),
    saco: mat(['#16160c', '#2c2a16', '#464222', '#625c32', '#827a46', '#a49a62', '#c6bc86'], { luz: 0.35, tex: (x, y, z) => (ESCU.ruido(x, y, z, 2.2, 213) > 0.62 ? -2 : 0) }),
    ojoInsecto: mat(['#020203', '#08080c', '#121218', '#1e1e28', '#3a3a4c', '#8888a8'], { brillo: 1 }),
    tunica: mat(['#06080e', '#0c1220', '#141c30', '#1e2842', '#2a3654', '#384668'], { tex: TEX.grumos(217, 1.6, 0.5) }),
    oroViejo: mat(['#1e1206', '#3a240a', '#5a3a10', '#7c5418', '#9e7024', '#be8e38'], { brillo: 0.8 }),
    porcelana: mat(['#2a2826', '#4a4644', '#6e6a66', '#94908a', '#bab6ae', '#dcd8d0', '#f4f2ec'], { brillo: 0.5 }),
    pluma: mat(['#06060a', '#0e0e14', '#181820', '#24242e', '#32323e', '#42424e', '#56566a'], { tex: (x, y, z) => ((Math.floor(x * 0.7 + y) % 3) === 0 ? -1 : 0) }),
    ojoTitan: mat(['#4a3a06', '#8a700e', '#c8a820', '#f0d850', '#fff4a8', '#ffffff'], { luz: 1, plano: true }),
    piedraEstatua: mat(['#0e1014', '#1a1e24', '#2a3038', '#3e4650', '#566068', '#727c84', '#929ca2'], { tex: TEX.piedra(219, 2.2) }),
  };
})();

function frontal(nombre, w, h, d, fn, o = {}) {
  const m = new ESCU.Modelo(w, h, d);
  fn(m, w / 2, d / 2);
  return defModelo(nombre, m, Object.assign({ luz: [-0.5, 0.75, 0.42], amb: 0.25, envuelve: 0.18, cima: o.cima ?? 0.55, sombra: false, tinta: '#05030a' }, o));
}
// Cambia todos los materiales por piedra (para estatuas). Los que brillan se apagan, salvo si encendida.
function materialEstatua(encendida) {
  const piedra = MAT_JEFE.piedraEstatua, brillo = ESCU.mat(['#5a0a40', '#b0207a', '#ff3cb4', '#ff8ad6'], { luz: 1, plano: true });
  return (mt) => (mt && mt.luz >= 1 ? (encendida ? brillo : piedra) : piedra);
}

// ---------------------------------------------------------------- Murdo, el Rey Ciego (topo)
// o: { esc, garras: 0 abajo / 1 arriba, mat: cambio de materiales }
function modeloMurdo(m, cx, cz, o = {}) {
  const e = o.esc || 1, J = MAT_JEFE, O = OSC, T = o.mat || ((x) => x);
  const P = T(J.pelaje), S = T(J.pielEnferma);
  m.con({ mover: [cx, 0, cz] }, () => {
    // patas traseras cortas
    for (const l of [-1, 1]) { m.elipsoide([l * 9 * e, 4 * e, 2 * e], [5 * e, 4.4 * e, 6 * e], P); m.elipsoide([l * 10 * e, 1.2 * e, 7 * e], [4 * e, 1.4 * e, 3.4 * e], S); for (const d of [-1.6, 0, 1.6]) m.capsula([l * 10 * e + d * e, 1 * e, 9.4 * e], [l * 10 * e + d * 1.4 * e, 0.4, 12 * e], 0.8 * e, 0.3, T(J.garra)); }
    // cuerpo en forma de pera, inclinado hacia la cámara
    m.elipsoide([0, 17 * e, 0], [16 * e, 15 * e, 12 * e], P, { ruido: [1, 3, 5] });
    m.elipsoide([0, 12 * e, 3 * e], [13 * e, 10 * e, 10 * e], P);
    // calvas de piel enferma
    const r = azar(17);
    for (let k = 0; k < 10; k++) { const a = r() * Math.PI * 0.9 + 0.05, b = r() * 1.3 + 0.2, dx = Math.cos(a) * Math.sin(b), dy = Math.cos(b), dz = Math.sin(a) * Math.sin(b); m.esfera([dx * 16 * e, 17 * e + dy * 15 * e, dz * 12 * e], (2.6 + r() * 2.4) * e, S, { modo: 'pintar' }); }
    // cabeza grande y alta, sin ojos (párpados cosidos), hocico largo hacia abajo y nariz de estrella en la punta
    const hy = 31 * e, hz = 6 * e;
    m.elipsoide([0, hy, hz], [10 * e, 8.4 * e, 9 * e], P, { ruido: [0.6, 2, 9] });
    m.esfera([-4 * e, hy + 3 * e, hz + 5 * e], 3 * e, S, { modo: 'pintar' });
    m.figura(ESCU.SDF.capsula(0, hy - 1.6 * e, hz + 5 * e, 0, hy - 5.6 * e, hz + 13 * e, 4.6 * e, 2.8 * e), [-6 * e, hy - 11 * e, hz, 6 * e, hy + 4 * e, hz + 17 * e], S);
    for (const l of [-1, 1]) { m.capsula([l * 6.4 * e, hy + 1.6 * e, hz + 6 * e], [l * 3.2 * e, hy + 2.4 * e, hz + 7.6 * e], 0.55 * e, T(O.hueco)); for (let k = 0; k < 3; k++) m.capsula([l * (5.6 - k * 1) * e, hy + 0.8 * e, hz + 6.8 * e], [l * (5.4 - k * 1) * e, hy + 3.2 * e, hz + 7.2 * e], 0.3 * e, T(J.garra)); }
    const nz = hz + 14 * e, ny = hy - 6 * e;
    for (let k = 0; k < 11; k++) { const a = k / 11 * Math.PI * 2; m.tubo([[0, ny, nz], [Math.cos(a) * 2.8 * e, ny + Math.sin(a) * 2.8 * e, nz + 1.4 * e], [Math.cos(a) * 4.8 * e, ny + Math.sin(a) * 4.8 * e, nz + 2 * e]], 1 * e, 0.45 * e, T(J.estrella)); }
    m.esfera([0, ny, nz + 1 * e], 1.5 * e, T(O.boca));
    // boca bajo el hocico, con incisivos amarillos
    m.figura(ESCU.SDF.elipsoide(0, hy - 9.6 * e, hz + 7 * e, 4.4 * e, 2 * e, 3 * e), [-6 * e, hy - 13 * e, hz + 2 * e, 6 * e, hy - 7 * e, hz + 11 * e], T(O.boca));
    for (const l of [-1, 1]) m.caja([l > 0 ? 0.3 * e : -2 * e, hy - 10 * e, hz + 8.6 * e], [l > 0 ? 2 * e : -0.3 * e, hy - 6.6 * e, hz + 9.8 * e], T(J.garra), { r: 0.3 });
    // corona: clavos de hierro oxidado hundidos en el cráneo y una banda de hierro
    m.toro([0, hy + 5.6 * e, hz - 0.6 * e], 6.6 * e, 1.1 * e, T(J.clavo));
    for (let k = 0; k < 7; k++) { const a = Math.PI * (0.08 + k * 0.84 / 6), x = Math.cos(a) * 6.6 * e, z = hz - 0.6 * e + Math.sin(a) * 3 * e; m.figura(ESCU.SDF.capsula(x, hy + 5.6 * e, z, x * 1.25, hy + (12 + (k % 2) * 3) * e, z + 1 * e, 1.2 * e, 0.35 * e), [x - 3 * e, hy + 4 * e, z - 3 * e, x * 1.25 + 3 * e, hy + 17 * e, z + 4 * e], T(J.clavo)); }
    // brazos con garras de pala (abajo apoyadas, o arriba para cavar)
    const arriba = o.garras ? 1 : 0;
    for (const l of [-1, 1]) {
      const hombro = [l * 13 * e, 20 * e, 3 * e], codo = arriba ? [l * 18 * e, 22 * e, 9 * e] : [l * 17 * e, 12 * e, 7 * e], mano = arriba ? [l * 16 * e, 28 * e, 14 * e] : [l * 15 * e, 4 * e, 11 * e];
      m.tubo([hombro, codo, mano], 4.6 * e, 3.4 * e, P);
      m.elipsoide(mano, [5 * e, 2 * e, 4 * e], S);
      for (let k = 0; k < 5; k++) { const dx = (k - 2) * 1.9 * e, tip = arriba ? [mano[0] + dx, mano[1] + 6 * e, mano[2] + 3 * e] : [mano[0] + dx, 0.6, mano[2] + 6.5 * e]; m.capsula([mano[0] + dx, mano[1], mano[2] + 2 * e], tip, 1 * e, 0.3 * e, T(J.garra)); }
    }
    // la Podre: bultos en los hombros y la espalda
    if (!o.sinPodre) { podre(m, [-11 * e, 26 * e, -3 * e], 3.4 * e, 31); podre(m, [9 * e, 29 * e, -5 * e], 2.8 * e, 33); podre(m, [2 * e, 22 * e, -10 * e], 3 * e, 35); }
  });
}

CONSTRUIR_MODELOS.push(function jefesOscuros() {
  for (let f = 0; f < 2; f++) frontal(`topo_${f}`, 72, 66, 50, (m, cx, cz) => modeloMurdo(m, cx, cz - 6, { garras: f }));
  // el montículo cuando se entierra: tierra revuelta y la nariz asomando
  for (let f = 0; f < 2; f++) frontal(`monticulo_${f}`, 40, 22, 34, (m, cx, cz) => {
    m.elipsoide([cx, 2, cz], [15, 6, 12], MAT_JEFE.tierra, { ruido: [1.6, 2.4, 3 + f] });
    for (let k = 0; k < 7; k++) { const a = k / 7 * Math.PI * 2 + f; m.esfera([cx + Math.cos(a) * 14, 1.4, cz + Math.sin(a) * 11], 2.4, MAT_JEFE.tierra, { ruido: [0.6, 1, k] }); }
    const ny = 8 + f * 2.4, nz = cz + 6;
    for (let k = 0; k < 9; k++) { const a = k / 9 * Math.PI * 2; m.tubo([[cx, ny - 2, nz], [cx + Math.cos(a) * 2.4, ny + Math.sin(a) * 2.4, nz + 1.4], [cx + Math.cos(a) * (3.6 + f), ny + Math.sin(a) * (3.6 + f), nz + 1.6]], 0.8, 0.35, MAT_JEFE.estrella); }
  });
});

// ---------------------------------------------------------------- Vhessa, Madre de la Colmena (reina)
// o: { esc, f: 0-1 respira, 2 abre las mandíbulas y escupe; mat }
function modeloVhessa(m, cx, cz, o = {}) {
  const e = o.esc || 1, J = MAT_JEFE, O = OSC, T = o.mat || ((x) => x), f = o.f || 0;
  const P = T(J.larvaPiel), resp = f === 1 ? 1.2 : 0;
  m.con({ mover: [cx, 0, cz] }, () => {
    // el saco de huevos detrás: hinchado, pálido, con huevos oscuros y venas
    m.elipsoide([0, 13 * e, -14 * e], [(19 + resp) * e, (12 + resp) * e, (14 + resp) * e], T(J.saco), { ruido: [1, 3, 7] });
    const r = azar(23);
    for (let k = 0; k < 14; k++) { const a = r() * Math.PI * 2, b = r() * 1.3 + 0.2; m.esfera([Math.cos(a) * Math.sin(b) * 18 * e, 13 * e + Math.cos(b) * 11 * e, -14 * e + Math.sin(a) * Math.sin(b) * 13 * e], (1.8 + r() * 1.4) * e, T(ESCU.mat(['#0a0c06', '#14160a', '#1e2210', '#2a2e16'], {})), { modo: 'pintar' }); }
    for (let k = 0; k < 5; k++) { const a = k / 5 * Math.PI + 0.3; m.tubo([[Math.cos(a) * 10 * e, 4 * e, -2 * e], [Math.cos(a) * 16 * e, 16 * e, -6 * e], [Math.cos(a) * 9 * e, 24 * e, -12 * e]], 0.7 * e, 0.5 * e, T(J.pielEnferma)); }
    // el cuerpo: segmentos que se enroscan y el frente alzado hacia la cámara
    const seg = [[-15, 4, 2], [-10, 4.6, 9], [-2, 5, 13], [7, 5, 11], [12, 7, 5], [8, 13, 4], [3, 18, 8], [0, 23, 13]];
    seg.forEach(([x, y, z], k) => {
      const rr = (5.8 + Math.sin(k / 7 * Math.PI) * 1.8) * e;
      m.esfera([x * e, y * e, z * e], rr, P);
      if (k > 0) m.toro([x * e, y * e, z * e], rr * 0.94, 0.5 * e, T(ESCU.mat(J.larvaPiel.r.slice(0, 4), {})), { eje: k > 5 ? 'y' : 'z' });
      if (k > 1 && k < 7) for (const l of [-1, 1]) m.tubo([[x * e + l * rr * 0.6, y * e - rr * 0.5, z * e + rr * 0.5], [x * e + l * rr * 1.2, Math.max(2, y - 3) * e, z * e + rr * 0.9], [x * e + l * rr * 1.4, 0, z * e + rr * 1.1]], 0.8 * e, 0.3 * e, T(J.garra));
    });
    // la cabeza: grande, con un racimo de ojos negros, mandíbulas y una boca redonda con dientes
    const hy = 29 * e, hz = 17 * e;
    m.esfera([0, hy, hz], 7.6 * e, T(J.pielEnferma));
    for (const [x, y, s] of [[-2.6, 4, 1.9], [2.6, 4, 1.9], [-5.2, 2.2, 1.4], [5.2, 2.2, 1.4], [-1, 6.2, 1.2], [1.4, 6.4, 1.2], [-4, 5.4, 1.1], [4.2, 5.4, 1.1]]) m.esfera([x * e, hy + y * e, hz + 5 * e], s * e, T(J.ojoInsecto));
    const ab = f === 2 ? 1 : 0.25;
    for (const l of [-1, 1]) m.tubo([[l * 3.8 * e, hy - 2.4 * e, hz + 5 * e], [l * (6.4 + ab * 3.4) * e, hy - 5 * e, hz + 9.4 * e], [l * (2.8 + ab * 3.4) * e, hy - 8.4 * e, hz + 12.6 * e]], 1.6 * e, 0.45 * e, T(J.garra));
    m.cilindro([0, hy - 3 * e, hz + 5.6 * e], (3 + ab * 1.4) * e, 2.2 * e, T(O.boca), { eje: 'z' });
    for (let k = 0; k < 12; k++) { const a = k / 12 * Math.PI * 2, rr = (3 + ab * 1.4) * e; m.capsula([Math.cos(a) * rr, hy - 3 * e + Math.sin(a) * rr, hz + 7.6 * e], [Math.cos(a) * rr * 0.5, hy - 3 * e + Math.sin(a) * rr * 0.5, hz + 8 * e], 0.55 * e, 0.15, T(O.diente)); }
    if (f === 2) m.capsula([0, hy - 5 * e, hz + 8 * e], [0.6 * e, hy - 11 * e, hz + 10 * e], 1 * e, 0.6 * e, T(ESCU.mat(['#1a4a08', '#2e7a10', '#4aa818', '#74d028', '#aaf05a'], { luz: 1, brillo: 0.6 })));
    if (!o.sinPodre) { podre(m, [-10 * e, 22 * e, -2 * e], 3 * e, 41); podre(m, [12 * e, 18 * e, -6 * e], 2.6 * e, 43); }
  });
}

// ---------------------------------------------------------------- Ozmar, el Último Velador
// o: { esc, f: 0-1 flota, 2 conjura, 3 duerme arrodillado; mat }
function modeloOzmar(m, cx, cz, o = {}) {
  const e = o.esc || 1, J = MAT_JEFE, O = OSC, T = o.mat || ((x) => x), f = o.f || 0, duerme = f === 3;
  const flota = duerme ? -5 : f === 1 ? 5 : 4, TU = T(J.tunica), OR = T(J.oroViejo);
  m.con({ mover: [cx, flota * e, cz] }, () => {
    // túnica de sacerdote que acaba en jirones
    m.figura(ESCU.OP.resta(ESCU.SDF.cilindro(0, 0, 0, 9.4 * e, 22 * e, 'y', 5 * e, 1), (x, y, z) => { const a = Math.atan2(z, x); return (y < 4 * e && Math.sin(a * 7 + 1) > 0.3) ? -1 : 1; }), [-11 * e, 0, -11 * e, 11 * e, 23 * e, 11 * e], TU);
    for (let k = 0; k < 8; k++) { const a = k / 8 * Math.PI * 2 + 0.2; m.capsula([Math.cos(a) * 8.6 * e, 3 * e, Math.sin(a) * 8.6 * e], [Math.cos(a) * 9.4 * e, -3 * e - (k % 3) * e, Math.sin(a) * 9.6 * e], 1.4 * e, 0.4 * e, TU); }
    for (const y of [3, 12]) m.toro([0, y * e, 0], (y === 3 ? 9 : 7.2) * e, 0.6 * e, OR);
    m.capsula([0, 1 * e, 8 * e], [0, 21 * e, 5.4 * e], 1 * e, OR);
    // el pecho abierto: de dentro sale la Podre
    m.elipsoide([0, 22 * e, 0], [6.4 * e, 5 * e, 4.4 * e], TU);
    m.figura(ESCU.SDF.elipsoide(0, 21 * e, 3.6 * e, 3.4 * e, 4.2 * e, 2 * e), [-4 * e, 16 * e, 0, 4 * e, 26 * e, 6 * e], T(J.pielEnferma));
    podre(m, [0, 21 * e, 4.6 * e], (f === 2 ? 3.6 : 2.8) * e, 51);
    for (let k = 0; k < 4; k++) m.capsula([(k - 1.5) * 1.4 * e, 24 * e, 4 * e], [(k - 1.5) * 1.8 * e, 19 * e, 5.2 * e], 0.45 * e, T(O.hueso));
    // cuello alto y la máscara de porcelana rota con un ojo de Podre
    m.figura(ESCU.OP.corta(ESCU.SDF.cilindro(0, 25 * e, 0, 6 * e, 6 * e, 'y', 7.4 * e), (x, y, z) => z - 2 * e), [-8 * e, 25 * e, -8 * e, 8 * e, 31 * e, 3 * e], TU);
    const hy = 31 * e;
    m.elipsoide([0, hy, 1 * e], [4 * e, 5 * e, 3.6 * e], T(J.porcelana));
    m.pintar(() => { m.capsula([1 * e, hy + 4 * e, 4 * e], [2.4 * e, hy - 1 * e, 4.6 * e], 0.35 * e, T(O.hueco)); m.capsula([2.4 * e, hy - 1 * e, 4.6 * e], [1 * e, hy - 4 * e, 4 * e], 0.35 * e, T(O.hueco)); });
    m.esfera([-1.6 * e, hy + 0.6 * e, 4.2 * e], 1.2 * e, T(O.hueco), { modo: 'pintar' });
    m.esfera([-1.6 * e, hy + 0.5 * e, 4.6 * e], 0.7 * e, T(O.poro));
    m.esfera([1.8 * e, hy + 0.6 * e, 4.2 * e], 0.9 * e, T(O.hueco), { modo: 'pintar' });
    m.capsula([-1.6 * e, hy - 3 * e, 4 * e], [1.6 * e, hy - 3 * e, 4 * e], 0.4 * e, T(O.hueco));
    // corona de los Veladores, con el hueco de la gema que falta
    m.toro([0, hy + 4.2 * e, 0.6 * e], 3.8 * e, 0.6 * e, OR);
    for (let k = 0; k < 5; k++) { const a = Math.PI * (0.15 + k * 0.175); m.capsula([Math.cos(a) * 3.8 * e, hy + 4.4 * e, 0.6 * e + Math.sin(a) * 3.8 * e], [Math.cos(a) * 4.2 * e, hy + (6.4 + (k === 2 ? 1.6 : 0)) * e, 0.6 * e + Math.sin(a) * 4.2 * e], 0.55 * e, 0.2, OR); }
    // brazos largos que acaban en dedos de raíz; uno lleva el cetro roto
    const arriba = f === 2;
    for (const l of [-1, 1]) {
      const hombro = [l * 6 * e, 25 * e, 0], codo = arriba ? [l * 12 * e, 31 * e, 4 * e] : [l * 10 * e, 17 * e, 3 * e], mano = arriba ? [l * 14 * e, 38 * e, 6 * e] : [l * 11 * e, 9 * e, 6 * e];
      m.tubo([hombro, codo, mano], 2 * e, 1 * e, TU);
      for (let k = 0; k < 4; k++) { const d = (k - 1.5) * 1.2; m.tubo([mano, [mano[0] + d * e, mano[1] + (arriba ? 3 : -3) * e, mano[2] + 1.4 * e], [mano[0] + d * 1.8 * e, mano[1] + (arriba ? 5 : -6) * e, mano[2] + (1 + k % 2) * e]], 0.6 * e, 0.2 * e, T(J.pielEnferma)); }
      if (l === 1) { m.capsula([mano[0], mano[1] - 6 * e, mano[2] + 1 * e], [mano[0] + 0.4 * e, mano[1] + 12 * e, mano[2]], 0.7 * e, OR); m.esfera([mano[0] + 0.4 * e, mano[1] + 13.4 * e, mano[2]], 1.8 * e, T(ESCU.mat(['#2a0a20', '#5a1440', '#8a2a64', '#c04a8a', '#e888c0'], { luz: 1, brillo: 1 }))); }
    }
    if (!o.sinPodre) podre(m, [-5 * e, 27 * e, -3 * e], 2.2 * e, 53);
  });
}

// ---------------------------------------------------------------- Hiedra, la Flor de Sangre
// o: { esc, f: 0-1 respira, 2 se abre, 3 aturdida (cae); mat }
function modeloHiedra(m, cx, cz, o = {}) {
  const e = o.esc || 1, O = OSC, T = o.mat || ((x) => x), f = o.f || 0;
  const abre = f === 2 ? 1.25 : f === 3 ? 0.35 : f === 1 ? 0.95 : 0.85, alza = f === 2 ? 1.2 : f === 3 ? 0.3 : 1;
  m.con({ mover: [cx, 0, cz] }, () => {
    const r = azar(17);
    for (let k = 0; k < 10; k++) {
      const a = k / 10 * Math.PI * 2 + r() * 0.3, l = (26 + r() * 10) * e;
      m.tubo([[0, 4 * e, 0], [Math.cos(a) * l * 0.4, 3 * e, Math.sin(a) * l * 0.4], [Math.cos(a) * l * 0.75, 1 * e, Math.sin(a) * l * 0.75], [Math.cos(a) * l, 0, Math.sin(a) * l]], 3.4 * e, 0.8 * e, T(O.vena));
      for (let s = 1; s < 4; s++) { const t = s / 4; m.capsula([Math.cos(a) * l * t, 3 * e, Math.sin(a) * l * t], [Math.cos(a) * l * t, 5.4 * e, Math.sin(a) * l * t + 0.5], 0.6 * e, 0.1, T(O.espina)); }
    }
    m.esfera([0, 12 * e, 0], 13 * e, T(O.petalo), { ruido: [1, 3, 21] });
    // pétalos de carne con espinas en el borde como dientes
    for (let k = 0; k < 7; k++) {
      const a = k / 7 * Math.PI * 2 + 0.3;
      m.con({ rot: ['y', -a, [0, 0, 0]] }, () => m.con({ rot: ['z', abre, [8 * e, 16 * e, 0]] }, () => {
        m.figura(ESCU.OP.corta(ESCU.SDF.elipsoide(18 * e, 16 * e, 0, 14 * e, 3 * e, 8 * e), (x, y) => (Math.abs(y - 16 * e) > 2.4 * e ? 1 : -1)), [0, 10 * e, -10 * e, 34 * e, 22 * e, 10 * e], T(O.petalo), { ruido: [0.8, 2.2, k] });
        for (let s = 0; s < 5; s++) { const t = s / 4; m.capsula([(10 + t * 18) * e, 17.6 * e, (Math.sin(t * Math.PI) * 7 - 0.5) * e * (s % 2 ? 1 : -1)], [(10 + t * 18) * e, 20.4 * e, (Math.sin(t * Math.PI) * 7.6) * e * (s % 2 ? 1 : -1)], 0.7 * e, 0.1, T(O.diente)); }
      }));
    }
    // el Velador muerto en el centro: costillas, cráneo caído con ojos verdes y brazos atrapados
    const t = [0, 22 * e, 2 * e];
    for (let k = 0; k < 5; k++) m.toro([t[0], t[1] + k * 2 * e, t[2]], (4.2 - Math.abs(k - 2) * 0.4) * e, 0.6 * e, T(O.hueso), { eje: 'y' });
    m.figura((x, y, z) => -1, [t[0] - 6 * e, t[1] - 2 * e, t[2] - 6 * e, t[0] + 6 * e, t[1] + 12 * e, t[2] - 1 * e], null, { modo: 'quitar' });
    m.capsula([t[0], t[1] - 2 * e, t[2] - 2 * e], [t[0], t[1] + 10 * e, t[2] - 2 * e], 1 * e, T(O.hueso));
    m.con({ rot: ['x', f === 3 ? 1.1 : 0.6, [t[0], t[1] + 11 * e, t[2]]] }, () => {
      m.esfera([t[0], t[1] + 14 * e, t[2] + 1 * e], 3.8 * e, T(O.hueso));
      m.caja([t[0] - 2 * e, t[1] + 10 * e, t[2] + 1.4 * e], [t[0] + 2 * e, t[1] + 11.4 * e, t[2] + 4.4 * e], T(O.hueso), { r: 0.6 });
      for (const l of [-1, 1]) { m.esfera([t[0] + l * 1.4 * e, t[1] + 14.2 * e, t[2] + 4 * e], 1.2 * e, T(O.hueco), { modo: 'pintar' }); m.esfera([t[0] + l * 1.4 * e, t[1] + 14.1 * e, t[2] + 4.5 * e], 0.65 * e, T(ESCU.mat(['#1a5a10', '#3aa020', '#7ae040', '#b8ff80'], { luz: 1, plano: true }))); }
    });
    for (const l of [-1, 1]) m.tubo([[t[0] + l * 4 * e, t[1] + 8 * e, t[2]], [t[0] + l * 8 * e, t[1] + 4 * e, t[2] + 3 * e], [t[0] + l * 11 * e, t[1] - 4 * e, t[2] + 5 * e]], 0.9 * e, 0.6 * e, T(O.hueso));
    for (const l of [-1, 1]) m.tubo([[t[0] + l * 12 * e, t[1] - 8 * e, t[2] - 2 * e], [t[0] + l * 9 * e, t[1] + 2 * e, t[2] + 4 * e], [t[0] + l * 4 * e, t[1] + 9 * e, t[2] + 3 * e], [t[0] - l * 2 * e, t[1] + 6 * e, t[2] + 4 * e]], 1.4 * e, 0.6 * e, T(O.vena));
    // látigos de espinas con boca, alzados alrededor (caídos si está aturdida)
    for (const [a, alto, curva] of [[0.9, 46, 1], [2.6, 40, -1], [4.3, 50, 1], [5.6, 36, -1]]) {
      const base = [Math.cos(a) * 14 * e, 8 * e, Math.sin(a) * 12 * e], pts = [];
      for (let s = 0; s <= 6; s++) { const q = s / 6; pts.push([base[0] + Math.cos(a) * q * (10 + (f === 3 ? 18 : 0)) * e + Math.sin(q * 3 + f) * curva * 6 * e, base[1] + q * alto * alza * e * (f === 3 ? 0.18 : 1), base[2] + Math.sin(a) * q * (8 + (f === 3 ? 14 : 0)) * e + Math.cos(q * 2.6) * 3 * e]); }
      m.tubo(pts, 3 * e, 1.6 * e, T(O.vena));
      for (let s = 1; s < 6; s++) m.capsula(pts[s], [pts[s][0] + 2.4 * e, pts[s][1] + 0.6 * e, pts[s][2] + 1.2 * e], 0.7 * e, 0.1, T(O.espina));
      const p = pts[6];
      m.esfera(p, 3.4 * e, T(O.petalo));
      m.cilindro([p[0], p[1] - 0.6 * e, p[2] + 1 * e], 2.4 * e, 3 * e, T(O.boca), { eje: 'z' });
      for (let k = 0; k < 8; k++) { const b = k / 8 * Math.PI * 2; m.capsula([p[0] + Math.cos(b) * 2.6 * e, p[1] - 0.6 * e + Math.sin(b) * 2.6 * e, p[2] + 3.4 * e], [p[0] + Math.cos(b) * 1.3 * e, p[1] - 0.6 * e + Math.sin(b) * 1.3 * e, p[2] + 3.8 * e], 0.4 * e, 0.1, T(O.diente)); }
    }
    if (!o.sinPodre) { podre(m, [-9 * e, 18 * e, 9 * e], 3 * e, 11); podre(m, [10 * e, 14 * e, 6 * e], 2.4 * e, 13); }
  });
}

// ---------------------------------------------------------------- Tamar, el Ojo del Cielo
// o: { esc, f: 0-1 posado, 2-3 vuela (alas arriba / abajo), 4 aturdido en el suelo; mat }
function modeloTamar(m, cx, cz, o = {}) {
  const e = o.esc || 1, J = MAT_JEFE, O = OSC, T = o.mat || ((x) => x), f = o.f || 0;
  const H = T(O.hueso), PL = T(J.pluma), vuela = f === 2 || f === 3, caido = f === 4;
  m.con({ mover: [cx, 0, cz] }, () => m.con({ rot: ['x', caido ? 0.5 : 0, [0, 0, 0]] }, () => {
    // patas de hueso con garras
    if (!caido) for (const l of [-1, 1]) {
      m.tubo([[l * 5 * e, 16 * e, 0], [l * 6 * e, 9 * e, 3 * e], [l * 5.4 * e, 2 * e, 1 * e]], 1.6 * e, 1 * e, H);
      for (const d of [-1.4, 0, 1.4]) m.tubo([[l * 5.4 * e, 2 * e, 1 * e], [l * 5.4 * e + d * e, 0.8 * e, 4 * e], [l * 5.4 * e + d * 1.3 * e, 0, 5.6 * e]], 0.8 * e, 0.3 * e, H);
    }
    // costillar de hueso medio cubierto de plumas rotas, con el ojo en el pecho
    const by = caido ? 10 : 24;
    m.elipsoide([0, by * e, -2 * e], [9 * e, 11 * e, 8 * e], PL, { ruido: [0.8, 2, 5] });
    for (let k = 0; k < 6; k++) m.figura(ESCU.OP.corta(ESCU.SDF.toro(0, (by - 6 + k * 2.4) * e, 0, (6.4 - Math.abs(k - 2.5) * 0.6) * e, 0.7 * e, 'y'), (x, y, z) => -z), [-9 * e, (by - 9 + k * 2.4) * e, -1, 9 * e, (by - 3 + k * 2.4) * e, 9 * e], H);
    m.capsula([0, (by - 8) * e, 4.6 * e], [0, (by + 6) * e, 5.4 * e], 1.2 * e, H);
    m.esfera([0, by * e, 4.4 * e], 4.2 * e, T(O.hueco));
    m.esfera([0, by * e, 5.4 * e], 3.4 * e, T(J.ojoTitan));
    m.capsula([0, (by - 2.6) * e, 8.6 * e], [0, (by + 2.6) * e, 8.6 * e], 0.7 * e, T(O.hueco));
    // cuello de vértebras y cráneo de ave con pico largo
    const cy = caido ? 8 : 38, cz2 = caido ? 14 : 4;
    for (let k = 0; k < 5; k++) m.esfera([0, (by + 8 + k * (caido ? -0.6 : 2.6)) * e, (caido ? 4 + k * 2 : -1 + k * 1) * e], 1.7 * e, H);
    m.esfera([0, cy * e, cz2 * e], 4.6 * e, H);
    m.figura(ESCU.SDF.capsula(0, (cy - 1) * e, (cz2 + 3) * e, 0, (cy - 5) * e, (cz2 + 13) * e, 2.6 * e, 0.3 * e), [-4 * e, (cy - 8) * e, cz2 * e, 4 * e, (cy + 3) * e, (cz2 + 15) * e], H);
    for (const l of [-1, 1]) { m.esfera([l * 2.6 * e, (cy + 0.6) * e, (cz2 + 3) * e], 1.5 * e, T(O.hueco), { modo: 'pintar' }); m.esfera([l * 2.6 * e, (cy + 0.5) * e, (cz2 + 3.6) * e], 0.6 * e, T(J.ojoTitan)); }
    for (let k = 0; k < 5; k++) m.capsula([(k - 2) * 1.6 * e, (cy + 3) * e, (cz2 - 2) * e], [(k - 2) * 2.6 * e, (cy + 7 + (k % 2) * 2) * e, (cz2 - 5) * e], 0.8 * e, 0.2, PL); // cresta de plumas
    // alas: huesos de dedos con plumas rotas colgando
    const angA = caido ? -0.9 : f === 2 ? 0.8 : f === 3 ? -0.5 : 0.15, extiende = vuela ? 1 : caido ? 0.7 : 0.45;
    for (const l of [-1, 1]) m.con({ rot: ['z', l * angA, [l * 7 * e, (by + 6) * e, -2 * e]] }, () => {
      const hombro = [l * 7 * e, (by + 6) * e, -2 * e], codo = [l * (7 + 16 * extiende) * e, (by + 8) * e, -4 * e], punta = [l * (7 + 34 * extiende) * e, (by + 2) * e, -2 * e];
      m.tubo([hombro, codo, punta], 1.8 * e, 0.6 * e, H);
      for (let k = 0; k < 7; k++) {
        const t = k / 6, p = [hombro[0] + (punta[0] - hombro[0]) * t, hombro[1] + (punta[1] - hombro[1]) * t + 1 * e, hombro[2] + (punta[2] - hombro[2]) * t];
        const largo = (14 - Math.abs(t - 0.6) * 8) * e;
        m.figura(ESCU.SDF.capsula(p[0], p[1], p[2], p[0] + l * 2 * e, p[1] - largo, p[2] - 1 * e, 2 * e, 0.6 * e), [Math.min(p[0], p[0] + l * 2 * e) - 3 * e, p[1] - largo - 3 * e, p[2] - 4 * e, Math.max(p[0], p[0] + l * 2 * e) + 3 * e, p[1] + 3 * e, p[2] + 3 * e], PL, { ruido: [0.6, 1.4, k + l * 9] });
      }
    });
    if (!o.sinPodre) { podre(m, [-5 * e, (by + 5) * e, 3 * e], 2.4 * e, 61); podre(m, [6 * e, (by - 6) * e, 4 * e], 2 * e, 63); }
  }));
}

CONSTRUIR_MODELOS.push(function jefesOscuros2() {
  for (let f = 0; f < 3; f++) frontal(`reina_${f}`, 84, 70, 72, (m, cx, cz) => modeloVhessa(m, cx, cz, { f }));
  for (let f = 0; f < 4; f++) frontal(`ozmar_${f}`, 56, 70, 36, (m, cx, cz) => modeloOzmar(m, cx, cz, { f }));
  for (let f = 0; f < 4; f++) frontal(`hiedra_${f}`, 112, 92, 88, (m, cx, cz) => modeloHiedra(m, cx, cz, { f }), { cima: 0.65 });
  for (let f = 0; f < 5; f++) frontal(`tamar_${f}`, 132, 96, 56, (m, cx, cz) => modeloTamar(m, cx, cz, { f }), { cima: 0.5 });
});

// ---------------------------------------------------------------- estatuas y altares: los jefes en piedra
CONSTRUIR_MODELOS.push(function estatuasJefes() {
  const piedra = MAT_JEFE.piedraEstatua;
  const pedestal = (m, cx, cz) => {
    m.caja([cx - 8, 0, cz - 7], [cx + 8, 2.4, cz + 7], piedra, { r: 0.6 });
    m.caja([cx - 6.6, 2.4, cz - 5.6], [cx + 6.6, 5, cz + 5.6], piedra, { r: 0.5 });
  };
  const GEMA = { topo: '#f0c040', reina: '#7ae040', ozmar: '#b070ff', hiedra: '#5ae07a', tamar: '#e8f0ff' };
  const gema = (m, cx, cz, c) => m.figura(ESCU.SDF.capsula(cx, 1.4, cz + 6.6, cx, 4.6, cz + 6.6, 1.5, 0.4), [cx - 3, 0, cz + 4, cx + 3, 7, cz + 9], ESCU.mat(ESCU.rampa(c, 6), { luz: 1, brillo: 1, sube: 1 }));
  const estatua = (nombre, fn, esc, o = {}) => {
    for (const enc of [false, true]) modelo(nombre + (enc ? '_on' : ''), o.ancho || 40, o.alto || 58, 30, (m) => {
      const cx = (o.ancho || 40) / 2, cz = 15;
      pedestal(m, cx, cz);
      m.con({ mover: [0, 5, o.dz || 0] }, () => fn(m, cx, cz, Object.assign({ esc, mat: materialEstatua(enc), sinPodre: !enc }, o.extra || {})));
      if (enc) gema(m, cx, cz, o.color);
    }, { cima: 0.7 });
  };
  estatua('estatua_topo', modeloMurdo, 0.5, { color: GEMA.topo, dz: -2 });
  estatua('estatua_reina', modeloVhessa, 0.42, { color: GEMA.reina, dz: -3 });
  estatua('estatua_ozmar', modeloOzmar, 0.62, { color: GEMA.ozmar, alto: 60 });
  estatua('altar_hiedra', modeloHiedra, 0.32, { color: GEMA.hiedra, dz: -2, ancho: 44 });
  estatua('altar_tamar', modeloTamar, 0.4, { color: GEMA.tamar, ancho: 48 });
  // el altar del templo donde se pone la ofrenda para que baje Tamar: alas talladas y un cuenco
  for (const enc of [false, true]) modelo('altar_cielo' + (enc ? '_on' : ''), 40, 30, 24, (m) => {
    const cx = 20, cz = 12, P = MAT2.piedraTemplo;
    m.caja([cx - 12, 0, cz - 9], [cx + 12, 4, cz + 9], P, { r: 0.6 });
    m.cilindro([cx, 4, cz], 5, 8, P, { r1: 4, red: 0.5 });
    m.figura(ESCU.OP.resta(ESCU.SDF.cilindro(cx, 12, cz, 6.4, 3, 'y', 7.4, 0.6), ESCU.SDF.cilindro(cx, 13.4, cz, 5.4, 3)), [cx - 8, 12, cz - 8, cx + 8, 16, cz + 8], P);
    for (const l of [-1, 1]) for (let k = 0; k < 6; k++) { const t = k / 5; m.capsula([cx + l * 6, 6 + t * 4, cz - 1], [cx + l * (12 + t * 7), 10 + t * 12 - t * t * 6, cz - 2 + t], 1.4 - t * 0.6, 0.6, P); }
    m.esfera([cx, 15, cz], enc ? 2.6 : 2, enc ? ESCU.mat(['#5a6a8a', '#9ab0d8', '#d8e8ff', '#ffffff'], { luz: 1, brillo: 1 }) : MAT.piedra);
  });
});
