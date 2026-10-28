'use strict';
// Modelos 3D de los objetos de la Ciénaga de la Podre y de la Ciudad Velada (0.7), y de los puzles y el viaje:
// braseros de runas, palancas, placas, bloques que se empujan, puertas selladas y piedras de paso.

// Las cuatro runas de los Veladores (braseros, puertas y tablillas): Hueso, Raíz, Ojo y Llama.
const RUNAS = ['Hueso', 'Raíz', 'Ojo', 'Llama'];

CONSTRUIR_MODELOS.push(function modelosBandas() {
  const { mat, rampa, TEX, SDF } = ESCU;
  const M = MAT, N = MAT2, O = OSC;
  const X = {
    maderaMuerta: mat(['#0a0909', '#161313', '#221e1c', '#302a26', '#403832', '#524840', '#665a50'], { tex: (x, y, z) => (ESCU.ruido(x * 2.2, y * 0.3, z * 2.2, 2, 301) > 0.6 ? -1.2 : 0) + (ESCU.ruido(x, y, z, 3, 302) - 0.5) * 0.6 }),
    liquen: mat(['#10140e', '#1c2418', '#2a3424', '#3a4632', '#4c5a42', '#627254'], { tex: TEX.grumos(303, 1, 1) }),
    turba: mat(['#0c0806', '#17100b', '#231811', '#302118', '#3e2c20', '#4e3a2a'], { tex: TEX.grumos(305, 1.6, 0.8) }),
    medula: mat(['#4a3c2c', '#7a6a50', '#a89474', '#d0c0a0', '#ecdec4', '#fff6e8'], { brillo: 0.4 }),
    tuetano: mat(['#3a0606', '#6a0c0c', '#9a1a16', '#c8302a', '#e85a48'], { luz: 0.35 }),
    velada: mat(['#121418', '#1e2128', '#2c3038', '#3c424e', '#4e5562', '#646c7a', '#7e8696'], { tex: TEX.piedra(307, 3) }),
    veladaLisa: mat(['#121418', '#1e2128', '#2c3038', '#3c424e', '#4e5562', '#646c7a', '#7e8696', '#9aa2b0'], { tex: TEX.grumos(309, 4, 0.35) }),
    plata: mat(['#1e242e', '#323a48', '#4a5466', '#68748a', '#8e9ab0', '#b8c4d8', '#e4ecfa'], { brillo: 1 }),
    plataOsc: mat(['#14181e', '#222830', '#323a46', '#46505e', '#5e6a7a'], { brillo: 0.7 }),
    almaFria: mat(['#1a3a6a', '#2a5a9a', '#4a86cc', '#7ab2f0', '#b4dcff', '#e8f6ff', '#ffffff'], { luz: 1, plano: true, sube: 2 }),
    verdeEnfermo: mat(['#1a4a16', '#2e7a22', '#4aa83a', '#78d45a', '#b0f08a', '#e4ffc8'], { luz: 1, plano: true, sube: 2 }),
    velo: mat(['#1a1c22', '#2a2c34', '#3c3e48', '#50525e', '#686a76', '#82848e'], { tex: (x, y) => (y % 3 === 0 ? -0.8 : 0) }),
    pergamino: mat(['#3a3020', '#5a4c34', '#7e6e50', '#a4946e', '#c8b890', '#e6d8b4'], {}),
    tinta: mat(['#100c14', '#1c1622', '#2a2232'], {}),
    hierroViejo: mat(['#0e0e10', '#1a1a1e', '#28282e', '#38383f', '#4a4a52', '#5e5e68'], { brillo: 0.5, tex: TEX.grumos(311, 1.4, 0.6) }),
    tela: mat(['#0e0c10', '#1a161e', '#28222e', '#38303e', '#4a4050', '#5c5262'], { tex: TEX.grumos(313, 1.2, 0.7) }),
    membrana: mat(['#120a12', '#1e1220', '#2e1a30', '#3e2440', '#503052', '#643e66'], { brillo: 0.5, tex: (x, y, z) => (ESCU.ruido(x * 1.6, y * 0.6, z * 1.6, 1.4, 315) > 0.66 ? -1.4 : 0) }),
    runaApagada: mat(['#100e14', '#1c1822', '#28222e'], {}),
    fuegoFrio: mat(['#3a1a8a', '#5a2ab8', '#7a48e0', '#a478ff', '#d0b4ff', '#f4ecff', '#ffffff'], { luz: 1, plano: true, sube: 2 }),
  };
  const piedraOsc = X.velada;

  // ---------------------------------------------------------------- la Ciénaga
  // árbol muerto: tronco gris retorcido con raíces que se clavan en el fango, ramas desnudas y líquen colgando
  const arbolMuerto = (nombre, sem) => modelo(nombre, 40, 56, 30, (m) => {
    const r = azar(sem), cx = 20, cz = 15;
    m.tubo([[cx, 0, cz], [cx + (r() - 0.5) * 3, 12, cz], [cx + (r() - 0.5) * 5, 24, cz - 0.5], [cx + (r() - 0.5) * 4, 34, cz - 1]], 3.6, 1.6, X.maderaMuerta);
    for (let k = 0; k < 5; k++) { const a = k * 1.26 + r() * 0.6; m.tubo([[cx, 3, cz], [cx + Math.cos(a) * 4, 1.4, cz + Math.sin(a) * 3.4], [cx + Math.cos(a) * 7.5, -1, cz + Math.sin(a) * 6]], 2, 0.8, X.maderaMuerta); }
    const rama = (p0, a, b, l, g) => {
      let p = p0;
      const pts = [p];
      for (let s = 0; s < 4; s++) { a += (r() - 0.5) * 0.9; p = [p[0] + Math.cos(a) * l / 4, p[1] + b * l / 4 + (r() - 0.3) * 1.5, p[2] + (r() - 0.5) * 2.5]; pts.push(p); }
      m.tubo(pts, g, 0.35, X.maderaMuerta);
      if (g > 1) rama(pts[2], a + (r() < 0.5 ? 0.8 : -0.8), b * 0.6, l * 0.55, g * 0.55);
      if (r() < 0.6) { const q = pts[3]; m.capsula(q, [q[0] + (r() - 0.5), q[1] - 4 - r() * 5, q[2] + 0.5], 0.5, 0.25, X.liquen); }
    };
    rama([cx, 30, cz - 0.6], Math.PI * 0.85, 0.7, 16, 1.6); rama([cx, 26, cz - 0.4], 0.2, 0.5, 15, 1.5);
    rama([cx + 1, 33, cz - 1], 1.4, 1.2, 12, 1.2);
    if (r() < 0.7) rama([cx - 1, 20, cz], Math.PI * 1.05, 0.2, 10, 1.1);
    for (let k = 0; k < 4; k++) m.esfera([cx + (r() - 0.5) * 6, 8 + r() * 18, cz + 3], 1 + r() * 0.8, X.liquen);
  }, { alturaSombra: 60, fuerzaSombra: 90 });
  arbolMuerto('arbol_muerto', 401); arbolMuerto('arbol_muerto_1', 409);
  modelo('junco', 16, 24, 12, (m) => {
    const r = azar(411), tallo = mat(['#141a0e', '#222c16', '#323e20', '#46542c', '#5a6a38'], {});
    for (let k = 0; k < 9; k++) {
      const x = 3 + r() * 10, z = 3 + r() * 6, h = 12 + r() * 9, tx = x + (r() - 0.5) * 3;
      m.capsula([x, 0, z], [tx, h, z], 0.45, 0.3, tallo);
      if (k % 3 === 0) m.capsula([tx, h + 0.5, z], [tx, h + 4.5, z], 1.1, 1, mat(['#1a0e06', '#2e180c', '#462614', '#5e361c'], {}));
    }
    for (const a of [0.4, 2.4, 4.4]) m.elipsoide([8 + Math.cos(a) * 3, 0.8, 6 + Math.sin(a) * 2], [3, 0.6, 1.2], tallo);
  });
  modelo('seta_podre', 16, 16, 14, (m) => {
    m.tubo([[8, 0, 7], [7.5, 4, 7], [8.5, 8, 7]], 1.6, 1.1, O.carneMuerta);
    m.figura(OP.corta(SDF.elipsoide(8.5, 9, 7, 5.4, 3.6, 4.6), OP.sobre(8)), [1, 7, 1, 15, 13, 13], O.podre, { ruido: [0.3, 1.2, 412] });
    m.cilindro([8.5, 7.8, 7], 4.8, 0.8, O.laminas);
    for (const [a, d] of [[0.5, 0.5], [2.1, 0.75], [3.6, 0.45], [5.2, 0.7]]) m.esfera([8.5 + Math.cos(a) * 4.6 * d, 9 + 3.4 * (1 - d * 0.6), 7 + Math.sin(a) * 4 * d], 0.7, O.poro);
    m.esfera([3.5, 1.4, 9], 1.6, O.podre); m.esfera([3.5, 2.4, 9.6], 0.5, O.poro);
  });
  // bulto de la Podre: un racimo negro que respira, con poros que brillan y raíces que se clavan en el suelo
  modelo('bulto_podre', 22, 18, 18, (m) => {
    const r = azar(421);
    for (let k = 0; k < 6; k++) { const a = k * 1.05 + r() * 0.4; m.tubo([[11, 3, 9], [11 + Math.cos(a) * 6, 1, 9 + Math.sin(a) * 5], [11 + Math.cos(a) * 10, -1, 9 + Math.sin(a) * 8]], 1.2, 0.4, O.vena); }
    m.esfera([11, 6, 9], 6, O.podre, { ruido: [0.9, 1.6, 422] });
    m.esfera([7, 4, 11], 3.6, O.podre, { ruido: [0.6, 1.4, 423] });
    m.esfera([15, 5, 10], 4, O.podre, { ruido: [0.6, 1.4, 424] });
    m.esfera([10, 11, 8], 3.4, O.podre, { ruido: [0.5, 1.2, 425] });
    for (let k = 0; k < 12; k++) { const a = r() * 6.28, b = r() * 1.3, R = 5.6; m.esfera([11 + Math.cos(a) * Math.sin(b + 0.2) * R, 6 + Math.cos(b) * R * 0.95, 9 + Math.sin(a) * Math.sin(b + 0.2) * R * 0.9 + 1], 0.55 + r() * 0.45, O.poro); }
  }, { fuerzaSombra: 80 });
  // capullo: la Podre envolvió a alguien y lo dejó de pie. Se le adivina la cara contra la membrana.
  modelo('capullo_podre', 16, 30, 14, (m) => {
    m.figura((x, y, z) => { const t = (y - 13) / 13, r = 5.4 * Math.sqrt(Math.max(0, 1 - t * t)) * (1 + 0.12 * Math.sin(y * 0.9)); return Math.hypot(x - 8, (z - 7) * 1.1) - r; }, [1, 0, 0, 15, 27, 14], X.membrana, { ruido: [0.5, 1.6, 431] });
    m.esfera([8, 20, 10.6], 2.4, O.carneMuerta); // la cara que empuja
    m.esfera([7.1, 20.4, 12.6], 0.6, O.hueco, { modo: 'pintar' }); m.esfera([9.1, 20.4, 12.6], 0.6, O.hueco, { modo: 'pintar' });
    m.capsula([7.4, 18.5, 12.6], [8.8, 18.5, 12.6], 0.45, O.hueco, { modo: 'pintar' });
    m.capsula([5, 14, 11.5], [6, 10, 12], 0.9, O.carneMuerta); m.capsula([11, 13, 11.5], [10.6, 9.5, 12], 0.9, O.carneMuerta); // manos
    for (let k = 0; k < 7; k++) m.capsula([8 + (k - 3) * 1.4, 0, 7 + (k % 2) * 2], [8 + (k - 3) * 0.8, 6, 8], 0.6, 0.3, O.vena);
    podre(m, [11, 6, 9], 2.6, 433); podre(m, [5, 22, 8], 2, 434);
  });
  // ídolo de la Podre de los grutos: un poste con máscaras de cráneo y un bulto enjaulado arriba
  modelo('idolo_podre', 20, 34, 16, (m) => {
    for (let k = 0; k < 7; k++) { const a = k * 0.9; m.esfera([10 + Math.cos(a) * 5, 1.4, 8 + Math.sin(a) * 4], 1.9, O.hueso); }
    m.cilindro([10, 0, 8], 1.4, 26, X.maderaMuerta, { r1: 1.1 });
    m.caja([4, 17, 7.2], [16, 18.4, 8.8], X.maderaMuerta);
    for (const x of [5, 15]) { m.esfera([x, 15.4, 9], 1.8, O.hueso); m.esfera([x + 0.6, 15.6, 10.6], 0.45, O.ojoAmbar); m.esfera([x - 0.6, 15.6, 10.6], 0.45, O.hueco, { modo: 'pintar' }); }
    m.esfera([10, 29, 8], 3.6, O.podre, { ruido: [0.5, 1.3, 441] });
    for (const a of [0.3, 1.9, 3.5, 5.1]) m.esfera([10 + Math.cos(a) * 3.2, 30, 8 + Math.sin(a) * 2.8 + 0.6], 0.7, O.poro);
    for (let k = 0; k < 4; k++) { const a = k * 1.57 + 0.78; m.capsula([10 + Math.cos(a) * 4.2, 25, 8 + Math.sin(a) * 4], [10 + Math.cos(a) * 3.6, 33, 8 + Math.sin(a) * 3.4], 0.4, X.hierroViejo); }
    m.capsula([10, 33, 8], [10, 34, 8], 0.8, X.hierroViejo);
  });
  modelo('farol_pantano', 14, 32, 12, (m) => {
    m.tubo([[6, 0, 6], [5.5, 10, 6], [7, 20, 6], [6.5, 27, 6]], 1.3, 0.9, X.maderaMuerta);
    m.tubo([[6.5, 26, 6], [10, 27.5, 6], [11.5, 26, 6]], 0.6, 0.5, X.maderaMuerta);
    m.capsula([11.5, 26, 6], [11.5, 23.5, 6], 0.25, X.hierroViejo);
    for (const a of [0, 1.57, 3.14, 4.71]) m.capsula([11.5 + Math.cos(a) * 1.8, 23, 6 + Math.sin(a) * 1.8], [11.5 + Math.cos(a) * 1.6, 18.5, 6 + Math.sin(a) * 1.6], 0.3, X.hierroViejo);
    m.esfera([11.5, 20.6, 6], 1.6, X.verdeEnfermo);
    m.cilindro([11.5, 18, 6], 2, 0.6, X.hierroViejo);
  });
  // rocas con mena nuevas: médula (hueso del dios en la turba) y plata velada (en la piedra gris)
  modelo('roca_medula', 20, 16, 16, (m) => {
    m.elipsoide([10, 5, 8], [8.4, 6, 6.4], X.turba, { ruido: [1.3, 3, 451] });
    m.elipsoide([5, 3, 11], [4, 3, 3], X.turba, { ruido: [0.7, 2, 452] });
    m.capsula([4, 7, 12], [15, 10, 11], 1.8, 1.2, X.medula); m.capsula([12, 3, 13], [16, 8, 9], 1.4, X.medula);
    for (const [x, y, z] of [[7, 8.4, 13.4], [14.4, 9.8, 12.2], [15.6, 6, 11]]) m.esfera([x, y, z], 0.7, X.tuetano);
    m.esfera([9, 11, 6], 1.6, X.medula);
  });
  modelo('roca_plata', 20, 16, 16, (m) => {
    const gris = mat(rampa('#3a3d47', 7), { tex: TEX.piedra(461, 2.6) });
    m.elipsoide([10, 5, 8], [8.4, 6, 6.4], gris, { ruido: [1.3, 3, 462] });
    m.elipsoide([14, 3, 11], [4, 3, 3], gris, { ruido: [0.7, 2, 463] });
    const r = azar(464);
    for (let k = 0; k < 7; k++) { const a = r() * 6.28, b = r() * 1.2, x = 10 + Math.cos(a) * Math.sin(b + 0.3) * 7.5, y = 5 + Math.cos(b) * 5.5, z = 8 + Math.sin(a) * Math.sin(b + 0.3) * 5.5; m.esfera([x, y, z], 1.3 + r() * 0.9, X.plata, { ruido: [0.3, 1, k] }); }
  });
  // restos de un Velador: túnica y velo sobre los huesos, con su bolsa al lado
  modelo('restos_velador', 26, 8, 16, (m) => {
    m.elipsoide([13, 1.2, 8], [9, 1.6, 4.4], X.tela, { ruido: [0.5, 1.4, 471] });
    m.esfera([22, 2.2, 8], 2.2, O.hueso);
    m.figura(OP.corta(SDF.elipsoide(22.4, 2.6, 8, 2.8, 2.6, 2.8), OP.sobre(2)), [19, 2, 5, 26, 6, 11], X.velo);
    m.esfera([22.6, 2.2, 10.4], 0.5, O.hueco, { modo: 'pintar' });
    for (const [a, b] of [[[15, 1.6, 4], [19, 1.4, 2]], [[15, 1.6, 12], [18, 1.4, 14.5]], [[6, 1, 6], [1.5, 0.8, 5]], [[6, 1, 10], [2, 0.8, 12]]]) m.capsula(a, b, 0.6, 0.45, O.hueso);
    for (let k = 0; k < 4; k++) m.capsula([12 + k * 1.6, 2.4, 5.6], [12 + k * 1.6, 2.4, 10.4], 0.4, O.hueso);
    m.esfera([5, 1.6, 13.4], 1.8, O.cuero);
  }, { fuerzaSombra: 60 });

  // ---------------------------------------------------------------- la Ciudad Velada
  // un Velador de piedra: túnica larga, el velo le tapa la cara entera, con una lámpara o un libro en las manos
  const veladorPiedra = (m, cx, cz, h, mt, roto) => {
    m.caja([cx - 6, 0, cz - 5], [cx + 6, 3, cz + 5], mt, { r: 0.5 });
    m.caja([cx - 5, 3, cz - 4], [cx + 5, 4.5, cz + 4], mt, { r: 0.4 });
    const alto = roto ? h * 0.55 : h;
    m.figura((x, y, z) => { const t = (y - 4.5) / (h - 4.5), r = 4.6 - t * 1.6 + Math.max(0, 0.18 - t) * 4; return Math.max(Math.hypot((x - cx) * 0.95, (z - cz) * 1.15) - r - Math.sin(Math.atan2(z - cz, x - cx) * 7) * 0.25, 4.5 - y, y - alto); }, [cx - 6, 4, cz - 5, cx + 6, alto + 1, cz + 5], mt);
    if (roto) { m.figura(SDF.elipsoide(cx, alto, cz, 3.6, 1.2, 3), [cx - 5, alto - 2, cz - 4, cx + 5, alto + 2, cz + 4], mt, { ruido: [0.8, 1.2, 481] }); return; }
    m.esfera([cx, h + 1.8, cz], 2.9, mt); // cabeza bajo el velo
    m.figura(OP.corta(SDF.elipsoide(cx, h + 1.4, cz, 3.6, 4.2, 3.4), OP.bajo(h + 4)), [cx - 5, h - 4, cz - 5, cx + 5, h + 5, cz + 5], mt, { ruido: [0.25, 0.8, 482] });
    for (const s of [-1, 1]) m.capsula([cx + s * 3.6, h - 1, cz + 1], [cx + s * 1.6, h - 6.5, cz + 3.4], 1.2, 1, mt);
    m.cilindro([cx, h - 9, cz + 4.2], 1.6, 3.2, mt, { r1: 1.2 }); // la lámpara
  };
  modelo('estatua_velador', 18, 44, 14, (m) => { veladorPiedra(m, 9, 7, 32, X.veladaLisa, false); m.elipsoide([5, 3, 11.5], [2, 1, 1.2], M.musgo); }, { alturaSombra: 50, fuerzaSombra: 90 });
  modelo('estatua_rota', 24, 30, 16, (m) => {
    veladorPiedra(m, 10, 8, 32, X.veladaLisa, true);
    m.esfera([20, 2.4, 11], 2.6, X.veladaLisa, { ruido: [0.5, 1, 491] }); // la cabeza, caída
    m.caja([16, 0, 2], [23, 2.4, 6], X.veladaLisa, { r: 0.8 });
    m.elipsoide([6, 3.2, 12], [2.4, 1, 1.2], M.musgo);
  }, { fuerzaSombra: 80 });
  modelo('fuente_seca', 34, 30, 28, (m) => {
    m.figura(OP.resta(SDF.cilindro(17, 0, 14, 15, 5, 'y', 15, 0.6), SDF.cilindro(17, 1.5, 14, 12.6, 6)), [1, 0, -1, 33, 6, 29], X.velada);
    m.cilindro([17, 1, 14], 12.6, 1, X.turba);
    for (let k = 0; k < 5; k++) m.esfera([9 + k * 4, 2, 11 + (k % 2) * 6], 1.2, O.hueso);
    m.cilindro([17, 1, 14], 2.6, 12, X.veladaLisa, { r1: 2 });
    m.cilindro([17, 12, 14], 5, 1.6, X.veladaLisa, { r1: 4.4, red: 0.5 });
    veladorPiedra(m, 17, 14, 22, X.veladaLisa, false);
    m.elipsoide([5, 5, 22], [3, 1.4, 2], M.musgo); m.elipsoide([28, 5, 6], [2.6, 1.2, 1.6], M.musgo);
  }, { alturaSombra: 40, fuerzaSombra: 90 });
  modelo('banco_piedra', 26, 10, 12, (m) => {
    for (const x of [3, 19]) m.caja([x, 0, 3], [x + 4, 4.5, 9], X.velada, { r: 0.4 });
    m.caja([1, 4.5, 2], [25, 6.4, 10], X.veladaLisa, { r: 0.6 });
    m.caja([14, 4.6, 2.5], [18, 6.3, 9.5], X.turba); // la grieta
  });
  modelo('farola_velada', 12, 40, 12, (m) => {
    m.caja([2.5, 0, 2.5], [9.5, 3, 9.5], X.velada, { r: 0.5 });
    m.cilindro([6, 3, 6], 1.2, 26, X.hierroViejo, { r1: 0.9 });
    m.capsula([6, 29, 6], [6, 30, 6], 2.4, X.hierroViejo);
    for (const a of [0.78, 2.36, 3.93, 5.5]) m.capsula([6 + Math.cos(a) * 2.6, 30, 6 + Math.sin(a) * 2.6], [6 + Math.cos(a) * 2.2, 37, 6 + Math.sin(a) * 2.2], 0.35, X.hierroViejo);
    m.figura(SDF.elipsoide(6, 34, 6, 2.6, 2.8, 2.6), [2, 30, 2, 10, 38, 10], X.almaFria);
    m.capsula([6, 37.4, 6], [6, 39, 6], 1.6, 0.4, X.hierroViejo);
  });
  modelo('columna_rota', 30, 22, 16, (m) => {
    m.caja([1, 0, 1.5], [13, 3, 12.5], X.velada, { r: 0.5 });
    m.figura((x, y, z) => { const a = Math.atan2(z - 7, x - 7), r = 4.2 + (Math.cos(a * 8) > 0.6 ? -0.5 : 0); return Math.max(Math.hypot(x - 7, z - 7) - r, 3 - y, y - 16 - Math.sin(x * 1.3) * 1.6 - Math.cos(z) * 1.2); }, [1, 3, 1, 13, 19, 13], X.veladaLisa);
    m.cilindro([22, 4.2, 9], 4.2, 7, X.veladaLisa, { eje: 'x' });
    m.esfera([27, 1, 4], 1.6, X.veladaLisa, { ruido: [0.4, 1, 501] });
    m.elipsoide([7, 17, 7], [3.4, 0.8, 3], M.musgo);
  }, { fuerzaSombra: 80 });
  // sarcófago con la tapa tallada: otro Velador tumbado con su velo
  modelo('sarcofago', 34, 14, 18, (m) => {
    m.caja([1, 0, 2], [33, 8, 16], X.velada, { r: 0.6 });
    m.caja([0.5, 8, 1.5], [33.5, 10, 16.5], X.veladaLisa, { r: 0.6 });
    m.elipsoide([16, 10.6, 9], [11, 1.6, 4.4], X.veladaLisa);
    m.esfera([27, 11, 9], 2.6, X.veladaLisa); m.figura(OP.corta(SDF.elipsoide(27.4, 11.2, 9, 3.2, 2.6, 3.4), OP.sobre(10.6)), [23, 10, 5, 31, 14, 13], X.velo);
    for (const s of [-1, 1]) m.capsula([20, 11, 9 + s * 3], [17, 11.4, 9 + s * 1], 0.9, X.veladaLisa);
    for (let x = 4; x < 31; x += 6) m.caja([x, 2, 15.6], [x + 3, 6, 16.4], X.velada);
  }, { fuerzaSombra: 80 });
  modelo('atril', 16, 22, 12, (m) => {
    m.caja([3, 0, 3], [13, 2, 9], X.velada, { r: 0.4 });
    m.cilindro([8, 2, 6], 1.6, 10, X.velada, { r1: 1.2 });
    m.figura(OP.corta(SDF.caja(2, 11, 1, 14, 15, 11, 0.4), (x, y, z) => y - 15 + (11 - z) * 0.35), [1, 10, 0, 15, 16, 12], X.velada);
    m.figura(OP.corta(SDF.caja(2.6, 14.4, 2.2, 7.8, 16, 10.4, 0.3), (x, y, z) => y - 16.4 + (10.4 - z) * 0.35), [2, 13, 2, 8, 17, 11], X.pergamino);
    m.figura(OP.corta(SDF.caja(8.2, 14.4, 2.2, 13.4, 16, 10.4, 0.3), (x, y, z) => y - 16.4 + (10.4 - z) * 0.35), [8, 13, 2, 14, 17, 11], X.pergamino);
    m.pintar(() => { for (let k = 0; k < 4; k++) for (const x0 of [3.4, 9]) m.caja([x0, 16.6 - k * 0.6, 3.5 + k * 1.6], [x0 + 3.6, 17.2 - k * 0.6, 4.1 + k * 1.6], X.tinta); });
  });
  modelo('estante_roto', 22, 28, 10, (m) => {
    m.caja([1, 0, 1], [3, 26, 9], M.maderaOsc); m.caja([19, 0, 1], [21, 20, 9], M.maderaOsc);
    for (const y of [0, 8, 16]) m.caja([1, y, 1], [21, y + 1.4, 9], M.maderaOsc);
    m.con({ rot: ['z', -0.2, [1, 25, 5]] }, () => m.caja([1, 24, 1], [12, 25.4, 9], M.maderaOsc));
    const r = azar(511), cols = ['#3a1a14', '#1e2a3a', '#2a3a1a', '#3a2e14', '#2a1a30'];
    for (const y of [1.4, 9.4]) for (let x = 3.4; x < 18;) { const w = 1.4 + r() * 1.2, h = 5 + r() * 2; if (r() < 0.75) { const lib = mat(rampa(cols[Math.floor(r() * 5)], 5), {}), caido = r() < 0.2; m.con(caido ? { rot: ['z', -0.35, [x, y, 5]] } : {}, () => m.caja([x, y, 2], [x + w, y + h, 8], lib)); } x += w + 0.4; }
    m.caja([6, 0, 9.5], [10, 1.2, 12], X.pergamino); m.caja([12, 0, 10], [15, 1.6, 12.5], mat(rampa('#3a1a14', 5), {}));
  });
  modelo('urna_velada', 14, 20, 12, (m) => {
    m.figura((x, y, z) => { const t = y / 16, r = 2.4 + Math.sin(t * Math.PI * 1.1) * 3.4 - Math.max(0, t - 0.85) * 6; return Math.max(Math.hypot(x - 7, (z - 6) * 1.1) - r, -y, y - 16); }, [0, 0, 0, 14, 17, 12], X.plataOsc);
    m.cilindro([7, 16, 6], 2.6, 1.4, X.plata, { r1: 2.2 });
    m.pintar(() => { m.figura(OP.corta(SDF.elipsoide(7, 9, 10, 2.4, 3, 1.2), OP.bajo(11)), [4, 5, 8, 10, 12, 12], X.plata); });
  });
  modelo('cofre_velado', 20, 16, 14, (m) => {
    m.caja([1, 0, 1], [19, 8, 13], X.velada, { r: 0.6 });
    m.figura(OP.corta(SDF.cilindro(1, 8, 7, 6.6, 18, 'x'), OP.sobre(8)), [0, 8, 0, 20, 15, 14], X.veladaLisa);
    for (const x of [1, 18]) m.caja([x, 0, 0.6], [x + 1.4, 14, 13.4], X.plata);
    m.caja([8, 6, 12.6], [12, 10, 13.6], X.plata); m.esfera([10, 8, 13.8], 0.7, X.almaFria);
  });

  // ---------------------------------------------------------------- puzles y viaje
  // brasero con su runa tallada delante (cuatro runas), apagado y encendido
  const glifo = (m, k, cx, cy, z, mt) => {
    const L = (a, b) => m.capsula([cx + a[0], cy + a[1], z], [cx + b[0], cy + b[1], z], 0.45, mt, { modo: 'pintar' });
    if (k === 0) { L([0, -2.4], [0, 2.4]); L([-1.6, 2.4], [1.6, 2.4]); L([-1.6, -2.4], [1.6, -2.4]); } // Hueso: un hueso de pie
    if (k === 1) { L([0, 2.4], [0, -0.6]); L([0, -0.6], [-1.8, -2.4]); L([0, -0.6], [1.8, -2.4]); L([0, 0.8], [-1.4, 2]); } // Raíz
    if (k === 2) { L([-2, 0], [0, 1.6]); L([0, 1.6], [2, 0]); L([2, 0], [0, -1.6]); L([0, -1.6], [-2, 0]); m.esfera([cx, cy, z], 0.6, mt, { modo: 'pintar' }); } // Ojo
    if (k === 3) { L([0, -2.4], [-1.6, 0.4]); L([-1.6, 0.4], [0, 2.4]); L([0, 2.4], [1.2, 0]); L([1.2, 0], [0, -2.4]); } // Llama
  };
  for (let k = 0; k < 4; k++) for (const on of [false, true]) modelo(`brasero_runa_${k}${on ? '_on' : ''}`, 18, on ? 30 : 22, 16, (m) => {
    m.caja([3, 0, 3], [15, 3, 13], X.velada, { r: 0.5 });
    m.figura((x, y, z) => { const t = (y - 3) / 11, r = 3.4 - Math.sin(t * Math.PI) * 1.2; return Math.max(Math.hypot(x - 9, z - 8) - r, 3 - y, y - 14); }, [3, 3, 2, 15, 14, 14], X.velada);
    m.figura(OP.resta(SDF.cilindro(9, 14, 8, 6.4, 4, 'y', 6.4, 0.5), SDF.cilindro(9, 15.4, 8, 5, 4)), [2, 13, 1, 16, 18.5, 15], X.hierroViejo);
    glifo(m, k, 9, 8.4, 11.2, on ? MAT.runa : X.runaApagada);
    if (on) { m.cilindro([9, 15, 8], 4.6, 1, MAT.brasa); llama(m, 9, 16, 8, 11, 3.6, 30 + k, ['#3a1a8a', '#6a2ab8', '#9a48e0', '#c478ff', '#e4b4ff', '#f8ecff', '#ffffff']); }
    else m.cilindro([9, 15, 8], 4.6, 0.8, mat(['#0a0808', '#141010', '#1e1816', '#2a2420'], { tex: TEX.grumos(521, 1, 1) }));
  }, { alturaSombra: on ? 30 : 22 });
  for (const on of [false, true]) modelo(`palanca${on ? '_on' : ''}`, 14, 18, 12, (m) => {
    m.caja([2, 0, 3], [12, 5, 10], X.velada, { r: 0.6 });
    m.caja([4, 5, 5.4], [10, 6, 7.6], X.hierroViejo);
    const a = on ? 0.55 : -0.55;
    m.con({ rot: ['z', a, [7, 5.6, 6.5]] }, () => { m.capsula([7, 5.6, 6.5], [7, 14.4, 6.5], 0.8, 0.6, X.hierroViejo); m.esfera([7, 15, 6.5], 1.4, on ? X.plata : X.plataOsc); });
    m.esfera([on ? 10.6 : 3.4, 5.4, 9.8], 0.6, on ? MAT.runaVerde : X.runaApagada);
  });
  modelo('bloque_empuje', 16, 18, 16, (m) => {
    m.caja([0.5, 0, 0.5], [15.5, 15, 15.5], X.velada, { r: 0.8 });
    m.caja([1.5, 15, 1.5], [14.5, 16, 14.5], X.veladaLisa, { r: 0.5 });
    m.pintar(() => { for (const [a, b] of [[[4, 9], [8, 12]], [[8, 12], [12, 9]], [[8, 12], [8, 3]]]) m.capsula([a[0], a[1], 15.6], [b[0], b[1], 15.6], 0.55, X.runaApagada); });
  }, { alturaSombra: 18 });
  modelo('puerta_sellada', 18, 34, 10, (m) => {
    for (const x of [0, 14]) m.caja([x, 0, 1], [x + 4, 32, 9], X.velada, { r: 0.5 });
    m.caja([0, 28, 0.5], [18, 33, 9.5], X.velada, { r: 0.6 });
    m.caja([3.5, 0, 3], [14.5, 28, 7], X.veladaLisa, { r: 0.3 });
    m.pintar(() => { for (let k = 0; k < 4; k++) glifo(m, k, 9, 5 + k * 6, 7.2, MAT.runa); });
  }, { alturaSombra: 34 });
  for (const on of [false, true]) modelo(`piedra_paso${on ? '_on' : ''}`, 18, 40, 14, (m) => {
    m.caja([2, 0, 2], [16, 3, 12], X.velada, { r: 0.6 });
    m.figura(OP.resta(SDF.caja(4, 3, 4, 14, 34, 10, 1.2), SDF.cilindro(9, 25, 3, 2.6, 8, 'z')), [3, 3, 3, 15, 35, 11], X.veladaLisa, { ruido: [0.2, 1, 531] });
    m.pintar(() => { for (let k = 0; k < 3; k++) glifo(m, (k + 1) % 4, 9, 7 + k * 5, 10.3, on ? MAT.runaVerde : X.runaApagada); });
    if (on) m.esfera([9, 25, 7], 1.8, X.almaFria);
    m.elipsoide([4, 3, 11], [2.2, 1, 1.2], M.musgo);
  }, { alturaSombra: 40 });
});
