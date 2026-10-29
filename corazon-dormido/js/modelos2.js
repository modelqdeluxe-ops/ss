'use strict';
// Modelos 3D de la naturaleza (árboles, raíces, setas, cristales, rocas con mena, plantas y cultivos) y de los
// objetos especiales (columnas, trono, tablilla, pozo, tienda, ídolos, altar y Corazón).

const MAT2 = (() => {
  const { mat, rampa, TEX } = ESCU;
  return {
    hojaSelva: mat(['#06200e', '#0c3216', '#14481e', '#1e6026', '#2a7a2e', '#3a9634', '#58b23e', '#86cc52'], {}),
    hojaSelva2: mat(['#0a1e10', '#103018', '#184622', '#225e2a', '#2e7832', '#40923a', '#5eac46', '#88c45a'], {}),
    hojaLuna: mat(['#06182e', '#0c2a48', '#143e66', '#1e5888', '#2c76a8', '#4498c8', '#6ab8e0', '#a8dcf4'], {}),
    puntoLuna: mat(['#3a8ab8', '#6ac0e8', '#a8e8ff', '#e8faff', '#ffffff'], { luz: 1, plano: true, sube: 2 }),
    fruta: mat(rampa('#d83a2a', 6), { brillo: 0.5 }),
    seta: mat(['#063848', '#0c5a6e', '#147e94', '#20a4b8', '#40c8d8', '#80e8f0', '#d0fcff'], { luz: 0.8 }),
    pieSeta: mat(['#5a5040', '#7e725e', '#a49a82', '#c8c0a8', '#e6e0cc', '#f8f4e8'], {}),
    setaNaranja: mat(['#4a1406', '#7a240c', '#ac3c14', '#d8601e', '#f08a34', '#ffb860', '#ffe0a8'], {}),
    lunar: mat(['#c8b8a0', '#e8dcc8', '#fff4e4', '#ffffff'], { plano: true, sube: 1 }),
    raiz: mat(['#1e1008', '#341c0e', '#4e2c16', '#6a3e1e', '#885428', '#a86c36', '#c88a4a'], { tex: (x, y, z) => (ESCU.ruido(x * 0.5, y * 0.5, z * 2.5, 2, 61) > 0.62 ? -1 : 0) + (ESCU.ruido(x, y, z, 4, 62) - 0.5) * 0.5 }),
    baya: mat(['#0a2a5a', '#14468a', '#2468ba', '#3c90e0', '#6ab8f8', '#b0e0ff', '#ffffff'], { luz: 0.9, brillo: 0.6 }),
    bulbo: mat(rampa('#e07a2a', 6), { brillo: 0.3 }),
    trigo: mat(['#4a3008', '#7a5410', '#a87c1c', '#d0a42e', '#ecc84a', '#fae47e'], { tex: (x, y) => (y % 2 ? -0.4 : 0) }),
    avena: mat(['#4a3e18', '#76642a', '#a08c40', '#c8b45a', '#e4d47c', '#f6ecac'], {}),
    melon: mat(['#0a2a3a', '#124660', '#1e6888', '#3090b0', '#58b8d4', '#a0e0f0', '#e8fcff'], { luz: 0.7, tex: (x, y, z) => (Math.floor((Math.atan2(z - 8, x - 8) + 3.2) * 2.2) % 2 ? -0.7 : 0) }),
    tierra: mat(['#1e120a', '#301c10', '#462a18', '#5e3a22', '#7a4e2e', '#96643c'], { tex: TEX.grumos(71, 1.4, 0.8) }),
    tallo: mat(['#0c2410', '#163a18', '#205220', '#2c6c28', '#3c8a32', '#56a640'], {}),
    fibra: mat(['#3a3418', '#5a5226', '#7e7438', '#a09650', '#c2b86c', '#ded690'], {}),
    cristalRoca: mat(['#062a3a', '#0c4a5e', '#147086', '#1e98ac', '#38c4d4', '#86ecf4', '#e8ffff'], { luz: 0.9, brillo: 1 }),
    huevo: mat(['#2a3a0c', '#4a6214', '#6e8e20', '#94b632', '#bcd856', '#e0f292', '#f8ffd8'], { luz: 0.6, tex: TEX.motas(73, 0.12, 1.5) }),
    panal: mat(['#3a2206', '#64400c', '#946214', '#c08a22', '#e0b03a', '#f4d468'], { tex: (x, y) => (y % 3 === 0 ? -1.2 : 0) }),
    flor: mat(rampa('#f0b020', 6), { luz: 0.6 }),
    centroFlor: mat(rampa('#7a3a10', 5), {}),
    piedraMusgo: mat(['#101810', '#1c281c', '#2c3a2a', '#40503c', '#566a50', '#708868', '#90a888'], { tex: TEX.piedra(79, 2.4) }),
    mena: {
      cobre: mat(['#3a1608', '#6e2c10', '#a44a1c', '#d8742e', '#f4a058', '#ffd0a0'], { brillo: 1 }),
      estano: mat(['#2a2e36', '#4a5260', '#6e7a8a', '#9aa8b8', '#c8d4e0', '#f4f8ff'], { brillo: 1 }),
      hierro: mat(['#1a2236', '#2c3a58', '#44587e', '#6680a6', '#94acc8', '#d0e0f0'], { brillo: 1 }),
      oro: mat(['#4a2a06', '#7e4c0c', '#b47a16', '#e4aa28', '#ffd658', '#fff4b0'], { brillo: 1, luz: 0.3 }),
      carmesi: mat(['#3a0610', '#6e0c1e', '#a8182e', '#dc2e42', '#ff6a6a', '#ffbcb0'], { brillo: 1, luz: 0.4 }),
    },
    piedraTemplo: mat(['#1a1c12', '#2c2e1e', '#42442c', '#5a5c3e', '#767852', '#94966a', '#b4b688'], { tex: TEX.piedra(83, 2.8) }),
    tronoTela: mat(['#1a0a24', '#2e1240', '#481c60', '#662a82', '#8640a4', '#a862c4'], {}),
    gemaMorada: mat(['#2a0a48', '#4a1480', '#7028b8', '#9a48e8', '#c482ff', '#ecd0ff'], { luz: 1, brillo: 1 }),
    corazon: mat(['#4a0a20', '#7a1232', '#aa1e48', '#d6345e', '#f05e80', '#ff9ab0', '#ffd8e2', '#ffffff'], { brillo: 1, luz: 0.5 }),
    corazonDormido: mat(['#2a0a14', '#441020', '#62182e', '#80243e', '#9e3850', '#ba5468'], { brillo: 0.6 }),
    baba: mat(['#0e2a10', '#1a4a1c', '#2a6e28', '#40963a', '#62bc4c', '#9ae070', '#d8ffb0'], { brillo: 0.8 }),
  };
})();

CONSTRUIR_MODELOS.push(function modelosNaturaleza() {
  const M = MAT, N = MAT2;
  // ---------------------------------------------------------------- árboles
  // tronco con raíces que se agarran al suelo y copa de racimos de hojas (cada racimo con su luz)
  const copa = (m, cx, cy, cz, rx, ry, rz, n, sem, mt, extra) => {
    const r = azar(sem);
    for (let k = 0; k < n; k++) {
      const a = r() * Math.PI * 2, b = Math.acos(r() * 1.6 - 0.6), f = 0.55 + r() * 0.45;
      const c = [cx + Math.cos(a) * Math.sin(b) * rx * f, cy + Math.cos(b) * ry * f, cz + Math.sin(a) * Math.sin(b) * rz * f];
      m.esfera(c, 3.2 + r() * 1.8, mt);
      if (extra && r() < extra.p) extra.fn(m, c, r);
    }
  };
  const tronco = (m, cx, cz, h, r0, sem) => {
    const r = azar(sem);
    m.tubo([[cx, 0, cz], [cx + (r() - 0.5) * 2, h * 0.4, cz], [cx + (r() - 0.5) * 3, h * 0.75, cz - 0.5], [cx + (r() - 0.5) * 2, h, cz - 1]], r0, r0 * 0.6, M.corteza);
    for (let k = 0; k < 4; k++) { const a = k * 1.57 + r() * 0.8 + 0.4; m.capsula([cx, 2.2, cz], [cx + Math.cos(a) * (r0 + 3.5), 0, cz + Math.sin(a) * (r0 + 3)], r0 * 0.6, 0.8, M.corteza); }
  };
  modelo('arbol', 40, 54, 30, (m) => {
    tronco(m, 20, 15, 30, 3.4, 3);
    m.capsula([20, 22, 15], [11, 30, 13], 1.6, 1, M.corteza); m.capsula([20, 26, 14], [29, 33, 14], 1.5, 1, M.corteza);
    copa(m, 20, 37, 14, 14, 9, 10, 52, 11, N.hojaSelva, { p: 0.18, fn: (m, c) => m.esfera([c[0] + 1.5, c[1] - 2.5, c[2] + 2.5], 1.1, N.fruta) });
  }, { alturaSombra: 60, fuerzaSombra: 110, cumulo: 3 });
  modelo('arbol_b', 36, 58, 28, (m) => {
    tronco(m, 18, 14, 34, 3, 7);
    copa(m, 18, 42, 13, 10, 11, 8, 40, 13, N.hojaSelva2);
    copa(m, 18, 30, 13, 12, 4, 9, 14, 17, N.hojaSelva2);
  }, { alturaSombra: 60, fuerzaSombra: 110, cumulo: 3 });
  modelo('arbol_luna', 38, 54, 30, (m) => {
    tronco(m, 19, 15, 30, 3, 5);
    copa(m, 19, 37, 14, 13, 9, 10, 48, 21, N.hojaLuna, { p: 0.3, fn: (m, c, r) => m.esfera([c[0] + (r() - 0.5) * 4, c[1] + 1.5, c[2] + 3], 0.9, N.puntoLuna) });
  }, { alturaSombra: 60, fuerzaSombra: 100, cumulo: 3 });

  // ---------------------------------------------------------------- raíz gruesa: un arco nudoso que sale del suelo
  modelo('raiz', 20, 14, 14, (m) => {
    m.tubo([[1, -1, 9], [4, 6, 8], [9, 9.5, 7], [14, 7.5, 6], [18, 1, 5], [19, -2, 5]], 3.2, 2.4, N.raiz);
    m.tubo([[9, 8, 7], [8, 11, 5], [6, 12, 4]], 1.4, 0.6, N.raiz);
    m.tubo([[14, 6, 6], [16, 3, 10], [17, 0, 12]], 1.6, 0.8, N.raiz);
    m.esfera([5, 7, 9.5], 1.2, M.musgo); m.esfera([12, 9.5, 8], 1.4, M.musgo);
  });
  // ---------------------------------------------------------------- arbusto de bayas de luz
  modelo('arbusto', 18, 14, 14, (m) => {
    copa(m, 9, 5, 7, 6, 3.5, 4.5, 16, 31, M.hojaOsc);
    for (const [x, y, z] of [[4, 7, 11], [9, 9, 12], [13, 6, 11], [7, 10, 8], [12, 10, 7], [3, 4, 9], [10, 4, 13]]) m.esfera([x, y, z], 1.5, N.baya);
  });
  // ---------------------------------------------------------------- setas
  const seta = (nombre, mt, alto, rad, lunares) => modelo(nombre, rad * 2 + 6, alto + 6, rad * 2 + 4, (m) => {
    const cx = rad + 3, cz = rad + 2;
    m.cilindro([cx, 0, cz], 1.8, alto, N.pieSeta, { r1: 1.3 });
    m.figura(OP.corta(SD.elipsoide(cx, alto, cz, rad, rad * 0.7, rad * 0.9), OP.sobre(alto - 1)), [0, alto - 2, 0, rad * 2 + 6, alto + rad, rad * 2 + 4], mt);
    m.cilindro([cx, alto - 1.6, cz], rad - 0.6, 0.8, ESCU.mat(ESCU.rampa('#b0a080', 5), {}));
    if (lunares) for (const [a, d] of [[0.6, 0.5], [2.2, 0.7], [3.8, 0.45], [5, 0.75], [1.4, 0.15]]) m.esfera([cx + Math.cos(a) * rad * d, alto + rad * 0.5 * (1 - d * 0.6), cz + Math.sin(a) * rad * 0.8 * d], 1.1, N.lunar, { modo: 'añadir' });
  });
  seta('hongo', N.seta, 7, 6, false);
  seta('hongo_explosivo', N.setaNaranja, 6, 5.5, true);
  // ---------------------------------------------------------------- plantas pequeñas
  modelo('planta_fibra', 16, 14, 12, (m) => {
    const r = azar(9);
    for (let k = 0; k < 11; k++) { const a = r() * 6.28, d = r() * 2; m.capsula([8 + Math.cos(a) * d, 0, 6 + Math.sin(a) * d], [8 + Math.cos(a) * (d + 3 + r() * 3), 6 + r() * 6, 6 + Math.sin(a) * (d + 2)], 0.7, 0.3, N.fibra); }
  });
  modelo('bulbo_silvestre', 14, 16, 12, (m) => {
    m.esfera([7, 3, 6], 3.4, N.bulbo, { ruido: [0.3, 1.5, 3] });
    for (const a of [0.5, 2.6, 4.4]) m.capsula([7, 5, 6], [7 + Math.cos(a) * 4, 12, 6 + Math.sin(a) * 3], 0.9, 0.3, N.tallo);
    for (const a of [1.4, 3.5, 5.6]) m.elipsoide([7 + Math.cos(a) * 3, 9, 6 + Math.sin(a) * 2.4], [2, 0.7, 1.2], N.tallo);
  });
  modelo('helecho', 22, 12, 18, (m) => {
    for (let k = 0; k < 7; k++) {
      const a = k / 7 * Math.PI * 2 + 0.2, l = 8 + (k % 3);
      const p = (t) => [11 + Math.cos(a) * l * t, 1 + Math.sin(t * Math.PI * 0.8) * 6, 9 + Math.sin(a) * l * t * 0.8];
      for (let s = 0; s < 6; s++) {
        const t = s / 6, q = p(t), q2 = p(t + 1 / 6);
        m.capsula(q, q2, 0.5, N.tallo);
        if (s > 0) for (const l2 of [-1, 1]) m.elipsoide([q[0] - Math.sin(a) * l2 * 1.2, q[1], q[2] + Math.cos(a) * l2 * 1.0], [1.3 * (1 - t * 0.5), 0.5, 1.3 * (1 - t * 0.5)], M.hoja);
      }
    }
  });
  modelo('flor_sol', 16, 22, 10, (m) => {
    m.tubo([[8, 0, 5], [7.5, 8, 5], [8, 14, 5.6]], 0.9, 0.7, N.tallo);
    for (const [y, l] of [[5, -1], [9, 1]]) m.elipsoide([8 + l * 2.8, y, 5.5], [2.6, 0.8, 1.4], M.hoja);
    for (let k = 0; k < 10; k++) { const a = k / 10 * Math.PI * 2; m.elipsoide([8 + Math.cos(a) * 3.6, 16 + Math.sin(a) * 3.6, 6.4], [1.6, 1.6, 0.8], N.flor); }
    m.cilindro([8, 16, 5.6], 2.4, 1.8, N.centroFlor, { eje: 'z', red: 0.6 });
  });
  modelo('avena_silvestre', 16, 20, 12, (m) => {
    const r = azar(17);
    for (let k = 0; k < 9; k++) {
      const x = 3 + r() * 10, z = 3 + r() * 7, h = 10 + r() * 6, tx = x + (r() - 0.5) * 4;
      m.capsula([x, 0, z], [tx, h, z], 0.6, N.tallo);
      for (let s = 0; s < 3; s++) m.elipsoide([tx + (s - 1) * 0.9, h + 1 + s * 1.6, z], [1, 1.4, 1], N.avena);
    }
    for (const a of [0.3, 2.2, 4]) m.elipsoide([8 + Math.cos(a) * 3, 1.4, 6.5 + Math.sin(a) * 2.5], [3, 0.8, 1.4], M.hoja);
  });
  modelo('cristal', 18, 16, 14, (m) => {
    m.elipsoide([9, 1.5, 7], [7, 3, 5.5], M.piedra, { ruido: [0.8, 2, 7] });
    for (const [x, z, h, ax, az] of [[9, 7, 12, 0, 0], [5, 8, 8, -2, 1], [13, 6, 9, 2, -1], [7, 4, 6, -1, -2], [12, 9, 6, 1.5, 2]]) m.capsula([x, 2, z], [x + ax, 2 + h, z + az], 1.9, 0.4, N.cristalRoca);
  });
  modelo('huevo_larva', 18, 12, 14, (m) => {
    m.elipsoide([9, 0.5, 7], [7, 1.5, 5.5], N.huevo, { ruido: [0.4, 1.5, 3] });
    for (const [x, z, r] of [[7, 6, 3.4], [11.5, 7.5, 2.9], [8, 10, 2.6], [12, 4, 2.4], [4, 9, 2.2]]) m.elipsoide([x, r * 0.9, z], [r, r * 1.05, r], N.huevo);
  });
  modelo('panal', 16, 22, 14, (m) => {
    m.figura((x, y, z) => { const t = (y - 9) / 9, r = 6.2 * Math.sqrt(Math.max(0, 1 - t * t)) + 0.3; return Math.max(Math.hypot(x - 8, z - 7) - r, -y); }, [0, 0, 0, 16, 19, 14], N.panal);
    m.cilindro([8, 4, 12.4], 1.6, 1.6, M.negro, { eje: 'z' });
    m.cilindro([8, 17, 7], 1, 4, M.maderaOsc);
  });

  // ---------------------------------------------------------------- rocas con mena
  for (const [metal, sem] of [['cobre', 3], ['estano', 5], ['hierro', 7], ['oro', 11], ['carmesi', 13]]) modelo('roca_' + metal, 20, 16, 16, (m) => {
    const piedra = metal === 'carmesi' ? N.piedraMusgo : M.piedra;
    m.elipsoide([10, 5, 8], [8.4, 6, 6.4], piedra, { ruido: [1.3, 3, sem] });
    m.elipsoide([5, 3, 11], [4, 3, 3], piedra, { ruido: [0.7, 2, sem + 1] });
    const r = azar(sem * 7), mt = N.mena[metal];
    for (let k = 0; k < 7; k++) {
      const a = r() * 6.28, b = r() * 1.2, x = 10 + Math.cos(a) * Math.sin(b + 0.3) * 7.5, y = 5 + Math.cos(b) * 5.5, z = 8 + Math.sin(a) * Math.sin(b + 0.3) * 5.5;
      m.esfera([x, y, z], 1.4 + r() * 0.9, mt, { ruido: [0.4, 1, k] });
    }
    if (metal === 'carmesi') for (let k = 0; k < 4; k++) m.esfera([4 + k * 4, 9 - (k % 2) * 2, 5 + k], 1.6, M.musgo);
  });

  // ---------------------------------------------------------------- cultivos (4 etapas)
  const monton = (m) => m.elipsoide([8, 0, 7], [5.5, 1.6, 4.4], N.tierra);
  const brote = (m, h, n, mt = N.tallo) => { for (let k = 0; k < n; k++) { const a = k / n * 6.28 + 0.4; m.capsula([8, 1, 7], [8 + Math.cos(a) * h * 0.4, 1 + h, 7 + Math.sin(a) * h * 0.3], 0.5, 0.3, mt); m.elipsoide([8 + Math.cos(a) * h * 0.45, 1 + h, 7 + Math.sin(a) * h * 0.35], [1.4, 0.6, 1], M.hoja); } };
  const CULT = {
    baya: (m, e) => { if (e === 3) { copa(m, 8, 5, 7, 4.5, 3, 3.5, 10, 41, M.hojaOsc); const r = azar(3); for (let k = 0; k < 6; k++) m.esfera([4 + r() * 8, 4 + r() * 5, 4 + r() * 7], 1.1, N.baya); } else brote(m, 2 + e * 2, 2 + e); },
    hongo: (m, e) => { if (e === 0) brote(m, 1, 1, N.pieSeta); else { m.cilindro([8, 1, 7], 1 + e * 0.3, 2 + e * 2, N.pieSeta, { r1: 0.8 + e * 0.2 }); m.figura(OP.corta(SD.elipsoide(8, 2 + e * 2, 7, 2 + e * 1.3, 1.4 + e, 2 + e), OP.sobre(1.5 + e * 2)), [0, 0, 0, 16, 16, 14], N.seta); } },
    bulbo: (m, e) => { if (e >= 2) m.esfera([8, 1.4, 7], 1.4 + (e - 1) * 1.2, N.bulbo); brote(m, 2 + e * 2.2, 2 + e); },
    trigo: (m, e) => { const r = azar(7); for (let k = 0; k < 2 + e * 2; k++) { const x = 5 + r() * 6, z = 5 + r() * 4, h = 2 + e * 3.2; m.capsula([x, 1, z], [x + (r() - 0.5), h, z], 0.4, e === 3 ? N.trigo : N.tallo); if (e === 3) m.elipsoide([x, h + 1.5, z], [0.9, 2, 0.9], N.trigo); } },
    avena: (m, e) => { const r = azar(9); for (let k = 0; k < 2 + e * 2; k++) { const x = 5 + r() * 6, z = 5 + r() * 4, h = 2 + e * 3; m.capsula([x, 1, z], [x + (r() - 0.5) * 2, h, z], 0.4, e === 3 ? N.avena : N.tallo); if (e >= 2) m.elipsoide([x, h + 1, z], [1, 1.6, 1], N.avena); } },
    melon: (m, e) => { brote(m, 1.5 + e, 2 + e); if (e >= 2) m.elipsoide([9, 2 + e * 0.6, 8], [1.6 + (e - 1) * 1.6, 1.4 + (e - 1) * 1.3, 1.5 + (e - 1) * 1.5], N.melon); },
  };
  for (const [planta, fn] of Object.entries(CULT)) for (let e = 0; e < 4; e++) modelo(`cultivo_${planta}_${e}`, 16, 18, 14, (m) => { monton(m); fn(m, e); }, { fuerzaSombra: 60 });

  // ---------------------------------------------------------------- piedra trabajada
  const columna = (nombre, mt, conMusgo) => modelo(nombre, 16, 34, 14, (m) => {
    m.caja([2, 0, 1.5], [14, 3, 12.5], mt, { r: 0.5 });
    m.caja([3, 3, 2.5], [13, 4.4, 11.5], mt, { r: 0.4 });
    m.figura((x, y, z) => { const a = Math.atan2(z - 7, x - 8), r = 4.2 + (Math.cos(a * 8) > 0.6 ? -0.5 : 0); return Math.max(Math.hypot(x - 8, z - 7) - r, 4 - y, y - 26); }, [2, 4, 1, 14, 26, 13], mt);
    m.caja([3, 26, 2.5], [13, 27.4, 11.5], mt, { r: 0.4 });
    m.caja([2, 27.4, 1.5], [14, 30, 12.5], mt, { r: 0.5 });
    if (conMusgo) {
      m.tubo([[4, 0, 11], [5, 8, 11.6], [9, 14, 11.6], [11.5, 22, 10.5], [12, 29, 11]], 0.9, 0.6, MAT2.tallo);
      for (const [x, y] of [[5, 6], [8, 13], [11, 20], [12, 27]]) m.elipsoide([x, y, 11.8], [1.8, 1, 0.8], M.hojaGrande);
      m.elipsoide([8, 30, 7], [5, 1.2, 4], M.musgo);
    }
  });
  columna('columna', M.piedraRuina, false);
  columna('columna_selva', MAT2.piedraTemplo, true);
  modelo('trono', 22, 34, 18, (m) => {
    m.caja([0, 0, 0], [22, 3, 18], M.piedraRuina, { r: 0.5 });
    m.caja([2, 3, 3], [20, 9, 16], M.piedraRuina, { r: 0.5 });
    m.caja([3, 9, 3], [19, 30, 6.5], M.piedraRuina, { r: 0.6 });
    for (const x of [1.5, 17.5]) m.caja([x, 9, 5], [x + 3, 15, 16], M.piedraRuina, { r: 0.6 });
    m.caja([4.5, 9, 6], [17.5, 10.6, 15.4], MAT2.tronoTela, { r: 0.6 });
    m.caja([5, 10.5, 5.6], [17, 27, 7], MAT2.tronoTela, { r: 0.6 });
    for (const x of [5, 11, 17]) m.capsula([x, 30, 4.8], [x, 33, 4.8], 1.2, 0.2, MAT2.gemaMorada);
    m.esfera([11, 24, 7.2], 1.4, MAT2.gemaMorada);
  });
  modelo('tablilla', 16, 22, 10, (m) => {
    m.caja([1, 0, 2], [15, 3, 9], M.piedra, { r: 0.6 });
    m.figura(OP.une(SD.caja(2.5, 2, 4, 13.5, 15, 7, 0.6), SD.cilindro(8, 15, 4, 5.5, 3, 'z')), [2, 2, 3, 14, 21, 8], M.piedraLisa);
    const r = azar(4);
    m.pintar(() => { for (let y = 5; y < 18; y += 2.2) for (let x = 4; x < 12;) { const w = 1 + Math.floor(r() * 3); if (r() < 0.8) m.caja([x, y, 6.6], [x + w, y + 1, 7.4], M.piedra); x += w + 1; } });
    m.elipsoide([4, 2, 7.6], [2.2, 1.2, 1.4], M.musgo); m.elipsoide([12, 18, 6], [1.6, 0.8, 1.2], M.musgo);
  });
  modelo('pozo', 22, 30, 20, (m) => {
    m.figura(OP.resta(SD.cilindro(11, 0, 11, 8, 7, 'y', 8, 0.6), SD.cilindro(11, 1, 11, 5.6, 8)), [2, 0, 2, 20, 8, 20], ESCU.mat(M.piedra.r, { tex: (x, y, z) => ((y % 3 === 0) || (Math.floor(Math.atan2(z - 11, x - 11) * 3 + (Math.floor(y / 3) % 2) * 0.5) % 2 === 0 && y % 3 === 1) ? -1 : 0) }));
    m.cilindro([11, 5, 11], 5.6, 1, M.agua);
    for (const x of [3, 19]) m.caja([x - 1, 6, 10], [x + 1, 22, 12], M.maderaV);
    m.figura(OP.corta(SD.caja(1, 21, 4, 21, 28, 18), (x, y, z) => Math.abs(z - 11) * 0.8 + y - 28), [0, 20, 3, 22, 29, 19], ESCU.mat(M.madera.r, { tex: ESCU.TEX.tablas('x', 2, 9) }));
    m.cilindro([3, 18, 11], 0.8, 16, M.maderaOsc, { eje: 'x' });
    m.capsula([11, 18, 11], [11, 12, 11], 0.3, M.cuerda);
    m.cilindro([11, 9, 11], 1.6, 3, M.madera, { r1: 1.9 });
  });
  modelo('tienda', 34, 30, 22, (m) => {
    m.caja([2, 0, 13], [32, 8, 19], M.maderaSelva, { r: 0.5 }); // mostrador
    m.caja([1.5, 8, 12.5], [32.5, 9.4, 19.5], M.maderaSelvaV, { r: 0.4 });
    for (const x of [2, 31]) m.caja([x, 0, 13], [x + 1.4, 26, 14.4], M.maderaSelvaV);
    for (const x of [2, 31]) m.caja([x, 0, 3], [x + 1.4, 26, 4.4], M.maderaSelvaV);
    // toldo a rayas, solo sobre el mostrador (detrás se ven los fardos)
    const RAYA = [['#3a0c10', '#5e1418', '#882024', '#b0302e', '#d0483c', '#ec7058', '#ffa080'], ['#4a4438', '#6e665a', '#948a7a', '#bab09e', '#dcd4c2', '#f4eee2', '#ffffff']];
    m.figura(OP.corta(SD.caja(0, 22, 9, 34, 28, 22), (x, y, z) => y - 28 + (z - 9) * 0.45), [0, 21, 8, 34, 29, 22], ESCU.mat(RAYA[0], { tex: (x) => 0 }));
    m.pintar(() => { for (let x = 4; x < 34; x += 8) m.caja([x, 20, 8], [x + 4, 30, 23], ESCU.mat(RAYA[1], {})); });
    for (let x = 1; x < 34; x += 4) m.esfera([x + 1, 21.4, 21.4], 1.4, ESCU.mat(ESCU.rampa(Math.floor(x / 4) % 2 ? '#e8e0d0' : '#c02a30', 5), {}));
    m.cilindro([8, 9.4, 15], 2.2, 2.2, M.terracota); m.esfera([15, 10.6, 16], 1.6, MAT2.fruta); m.esfera([18, 10.4, 15], 1.4, MAT2.bulbo);
    m.caja([22, 9.4, 14], [28, 12, 18], M.caja || M.madera, { r: 0.3 });
    m.caja([4, 0, 4], [30, 6, 12], M.lona, { r: 1 }); // sacos y fardos detrás
  });
  // ídolos de los mercaderes
  modelo('idolo_barbas', 16, 22, 14, (m) => {
    m.cilindro([8, 0, 7], 5.5, 3, M.maderaOsc, { r1: 5, red: 0.5 });
    m.esfera([8, 9, 7], 5.4, MAT2.baba);
    m.figura(SD.elipsoide(8, 7, 11, 3.6, 3.4, 1.8), [3, 3, 8, 13, 11, 14], ESCU.mat(ESCU.rampa('#f2ead8', 6), {})); // barba
    for (const l of [-1, 1]) { m.esfera([8 + l * 2, 10.5, 11.6], 1.3, ESCU.mat(['#f4f4f4', '#ffffff'], { plano: true })); m.esfera([8 + l * 2, 10.4, 12.6], 0.6, M.negro); }
    m.cilindro([8, 13.6, 7], 3.6, 1.6, M.cuero); m.cilindro([8, 15, 7], 2.4, 2.6, M.cuero); m.esfera([10, 16, 9], 0.9, M.oro);
  });
  modelo('idolo_pesca', 16, 22, 14, (m) => {
    m.cilindro([8, 0, 7], 5.5, 3, M.maderaOsc, { r1: 5, red: 0.5 });
    m.elipsoide([8, 9, 7], [3.8, 6, 3.2], ESCU.mat(ESCU.rampa('#4a90d8', 7), { brillo: 0.8 }));
    m.figura(SD.capsula(8, 14, 7, 8, 18, 7, 2.6, 0.4), [3, 12, 3, 13, 20, 11], ESCU.mat(ESCU.rampa('#4a90d8', 7), { brillo: 0.8 })); // cola
    m.esfera([6, 9, 9.8], 1, ESCU.mat(['#ffffff'], { plano: true })); m.esfera([5.9, 9, 10.5], 0.5, M.negro);
    m.capsula([10, 4, 9], [13, 2, 10], 0.5, M.cuerda); m.esfera([13.4, 2, 10], 0.9, ESCU.mat(ESCU.rampa('#e04040', 5), {}));
  });
  modelo('idolo_misterioso', 16, 24, 14, (m) => {
    m.cilindro([8, 0, 7], 5.5, 3, M.piedra, { r1: 5, red: 0.5 });
    m.figura(OP.resta(SD.capsula(8, 3, 7, 8, 15, 7, 5, 3.4), SD.elipsoide(8, 13.4, 11, 2.6, 2.6, 2.4)), [2, 0, 1, 14, 21, 13], ESCU.mat(['#140a20', '#24123a', '#3a1c58', '#542a7a', '#70409c', '#9060c0'], {}));
    for (const l of [-1, 1]) m.esfera([8 + l * 1.2, 13.2, 10.4], 0.7, MAT2.gemaMorada);
    m.esfera([8, 4, 11], 1.4, MAT2.gemaMorada);
  });

  // ---------------------------------------------------------------- el altar del Corazón y el Corazón
  modelo('altar', 40, 14, 26, (m) => {
    m.caja([0, 0, 0], [40, 4, 26], M.piedraRuina, { r: 0.8 });
    m.caja([3, 4, 3], [37, 8, 23], M.piedraRuina, { r: 0.8 });
    m.caja([7, 8, 6], [33, 10, 20], M.piedraRuina, { r: 0.6 });
    for (let x = 6; x <= 34; x += 7) m.esfera([x, 6, 23.2], 0.9, MAT.cristal);
    for (const [x, z] of [[2, 2], [38, 2], [2, 24], [38, 24]]) m.cilindro([x, 4, z], 1.6, 8, M.piedraRuina, { r1: 1.2 });
  });
  // El Corazón de Ásor: un corazón de verdad, no de San Valentín, pero que se lea de un vistazo (es el centro del
  // juego y el icono de la app). Silueta limpia: los ventrículos con la punta torcida, dos aurículas, la aorta en
  // arco con sus tres ramas, el tronco pulmonar y la cava. Por la carne le salen cristales (su sangre seca, LORE.md)
  // y las coronarias son las venas por donde vuelve el latido. Dormido: carne gris morada, cristales apagados y
  // bultos de Podre. Despierto: rojo, cristales que brillan y venas encendidas.
  const corazon = (nombre, dormido) => frontal(nombre, 44, 58, 32, (m, cx, cz) => {
    const { mat } = ESCU;
    const carne = dormido ? mat(['#0c060a', '#1a0c14', '#2a121e', '#3a1a2a', '#4c2436', '#603044', '#763e54'], { contraste: 1.25 })
      : mat(['#1a0408', '#34080e', '#520e18', '#701624', '#8e2232', '#ae3242', '#cc4a52', '#e8726a'], { brillo: 0.45, contraste: 1.25 });
    const arteria = dormido ? mat(['#140a10', '#24121a', '#361c26', '#4a2834', '#5e3644', '#744656'], {})
      : mat(['#2a0c10', '#4a1820', '#6c2430', '#8e3442', '#b04a56', '#cc6a70', '#e89490'], { brillo: 0.6 });
    const vena = dormido ? OSC.vena : mat(['#10061a', '#1e0c2c', '#2e1640', '#402256', '#54306c'], {});
    const cristal = dormido ? mat(['#0e0c18', '#1a1828', '#28263a', '#38364e', '#4a4864', '#5e5c7a'], { brillo: 0.8, detalle: true }) : M.cristal;
    const latido = mat(['#6a0a20', '#c01a40', '#ff3c64', '#ff9ab0'], { luz: 1, plano: true, detalle: true });
    m.con({ mover: [cx, 0, cz] }, () => {
      // los ventrículos mandan en la silueta (dos tercios del alto), inclinados, con la punta abajo a la derecha
      m.con({ rot: ['z', 0.3, [0, 19, 0]] }, () => {
        m.elipsoide([0, 20, 0], [14, 15.5, 11.5], carne);
        m.capsula([0, 13, 0], [1.5, 2.5, 1.5], 10.5, 2.6, carne);
      });
      // aurículas, bajas y medio tapadas
      m.elipsoide([-11, 29, 0], [6.6, 6, 6.2], carne);
      m.elipsoide([10, 30, -4], [6, 5, 5.6], carne);
      // la aorta: un arco corto y cerrado hacia atrás, con tres ramas arriba
      m.tubo([[1, 29, 0], [1, 37, 0], [4, 41.5, -2.5], [9, 41.5, -5], [11.5, 37, -7]], 4, 3.4, arteria);
      for (const [x, z, l] of [[2.5, -1.5, -1], [6.5, -3.5, 0], [10, -5, 1]]) m.tubo([[x, 42, z], [x + l * 0.5, 45, z], [x + l, 47, z]], 1.6, 1.2, arteria);
      // el tronco pulmonar delante, tapando el hueco del arco
      m.tubo([[-3, 26, 7], [-2.5, 33, 6], [2, 37, 4.5], [7, 37.5, 2]], 3.8, 3, arteria);
      m.tubo([[-3, 33, 6], [-9, 36, 4], [-13, 35, 2]], 2.6, 2, arteria);
      // las coronarias: por el surco entre los ventrículos y por arriba
      m.tubo([[-2, 28, 11.6], [0, 21, 12.6], [3, 12, 11.6], [6, 5, 8]], 1.4, 0.9, vena);
      m.tubo([[-12, 25, 7.2], [-4, 28.5, 10.8], [6, 28.5, 9.6], [13, 24, 6.4]], 1.3, 1, vena);
      if (!dormido) m.pintar(() => {
        m.tubo([[-2, 28, 12], [0, 21, 13], [3, 12, 12], [5.6, 6, 8.8]], 0.9, 0.6, latido);
        m.tubo([[-10, 25.6, 8.2], [-4, 28.8, 11.2], [5, 28.8, 10]], 0.8, 0.6, latido);
      });
      // cristales (su sangre seca) que le crecen hacia arriba por los hombros y alguno en la cara
      for (const [x, y, z, dx, dy, dz, r] of [[-12, 30, 2, -4, 9, 1, 2.3], [-15, 28, 0, -5, 5, 0, 1.6], [-9, 33, 3, -1, 8, 1, 1.4], [13, 28, -1, 4, 8, 0, 2], [15, 25, 1, 5, 4, 1, 1.4], [-7, 12, 10, -3, 3, 3, 1.3]]) {
        m.capsula([x, y, z], [x + dx, y + dy, z + dz], r, 0.3, cristal);
      }
      // la Podre: bultos negros con poros magenta, muchos si duerme
      const bultos = dormido ? [[7, 11, 9, 3.4], [-7, 17, 10, 2.8], [11, 22, 7, 2.4], [1, 4, 6, 2.2]] : [[9, 9, 8, 2.2]];
      bultos.forEach(([x, y, z, rr], k) => podre(m, [x, y, z], rr, 820 + k));
    });
  }, { cima: 0.5, sombra: false, cumulo: 3 });
  corazon('corazon', false);
  corazon('corazon_dormido', true);
});
