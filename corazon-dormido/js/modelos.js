'use strict';
// Modelos 3D (escultor.js) de los muebles y estaciones de trabajo. Cada uno se hornea con la cámara del juego.

const MAT = (() => {
  const { mat, TEX } = ESCU;
  return {
    madera: mat(['#2c170e', '#4a2a18', '#6e4224', '#925a32', '#b47844', '#d49a5e', '#ecc088'], { tex: TEX.tablas('x', 4, 3) }),
    maderaZ: mat(['#2c170e', '#4a2a18', '#6e4224', '#925a32', '#b47844', '#d49a5e', '#ecc088'], { tex: TEX.tablas('z', 4, 5) }),
    maderaV: mat(['#2c170e', '#4a2a18', '#6e4224', '#925a32', '#b47844', '#d49a5e'], { tex: TEX.vetas(2) }),
    maderaOsc: mat(['#1a0d08', '#2e1810', '#472616', '#633820', '#80502e', '#a06a3e'], { tex: TEX.vetas(4) }),
    hierro: mat(['#1c1f2a', '#2f3542', '#465062', '#636f84', '#8693a8', '#b4c0d0', '#e6eef6'], { brillo: 0.9 }),
    cobre: mat(['#3a160c', '#622a16', '#8e4522', '#b8622e', '#d8844a', '#f0ae74', '#ffd8b0'], { brillo: 0.9 }),
    estano: mat(['#24282c', '#3c4248', '#5a636a', '#7c868c', '#a2acb0', '#ccd4d6', '#f2f6f6'], { brillo: 0.8 }),
    oro: mat(['#4a2a08', '#7a4a10', '#a8701c', '#d49a2c', '#f2c450', '#ffe68a', '#fffbe0'], { brillo: 1 }),
    carmesi: mat(['#2a060e', '#4e0c1a', '#7e1426', '#b02234', '#d8414a', '#f27a74', '#ffc2b8'], { brillo: 0.9 }),
    piedra: mat(['#16161e', '#262833', '#3a3d4b', '#525868', '#6e7586', '#8e96a6', '#b4bcc8'], { tex: ESCU.TEX.piedra(3, 3) }),
    piedraLisa: mat(['#16161e', '#262833', '#3a3d4b', '#525868', '#6e7586', '#8e96a6', '#b4bcc8'], { tex: ESCU.TEX.grumos(5, 3, 0.4) }),
    ladrillo: mat(['#1a1012', '#33191a', '#53261f', '#743426', '#97492f', '#b9653f', '#d68b5c'], { tex: (x, y, z) => ((y % 4 === 0) || ((x + (Math.floor(y / 4) % 2) * 3) % 6 === 0 && z > 0) ? -1.2 : 0) }),
    tela: mat(['#2a0a14', '#4e1222', '#7a1c30', '#a82c3e', '#cc4650', '#e87068', '#f8a090'], { tex: ESCU.TEX.grumos(9, 2, 0.35) }),
    lana: mat(['#3a3024', '#5a4a38', '#7e6a52', '#a08a6c', '#c4ae8a', '#e2d0aa', '#f6ecd0'], { tex: ESCU.TEX.grumos(11, 1.6, 0.5) }),
    cuero: mat(['#1e0f0a', '#3a1d12', '#5a2e1a', '#7c4424', '#9e5e34', '#c07e4c'], { tex: ESCU.TEX.grumos(13, 2, 0.3) }),
    cuerda: mat(['#3a2a14', '#5e4422', '#86663a', '#ac8c56', '#d0b27a'], { tex: (x, y, z) => ((x + y + z) % 2 ? -0.6 : 0.2) }),
    hoja: mat(['#0b2414', '#123a1c', '#1c5626', '#287230', '#3a8f38', '#5aae44', '#88cc5a', '#bce67c'], {}),
    hojaOsc: mat(['#08180f', '#0e2a16', '#163e1c', '#205424', '#2e6c2c', '#428836', '#62a444'], { tex: ESCU.TEX.hojas(8) }),
    corteza: mat(['#1a0f0a', '#2e1a10', '#462818', '#5e3822', '#7a4c2e', '#96643e'], { tex: (x, y, z) => (ESCU.ruido(x * 2.2, y * 0.35, z * 2.2, 2, 21) > 0.6 ? -1 : 0) + (ESCU.ruido(x, y, z, 3, 22) - 0.5) * 0.6 }),
    cristal: mat(['#0a3a4a', '#126478', '#1a90a4', '#2cbccc', '#5ae4f0', '#a8f8ff', '#ffffff'], { brillo: 1, luz: 1, sube: 1 }),
    fuego: mat(['#a8301a', '#e8502a', '#ff7a2a', '#ffa83a', '#ffd060', '#fff2b0', '#ffffff'], { luz: 1, plano: true, sube: 2 }),
    brasa: mat(['#3a0a06', '#6e1a0a', '#a8340e', '#e05a18', '#ff8a2a', '#ffc050'], { luz: 1, tex: ESCU.TEX.grumos(3, 1.2, 1.2) }),
    hueso: mat(['#3a3228', '#5e5244', '#8a7c68', '#b4a68e', '#d8ccb4', '#f2ead8'], {}),
    papel: mat(['#5a4a32', '#8a7656', '#b4a07a', '#d6c49e', '#ecdec0', '#fff6e6'], {}),
    negro: mat(['#060508', '#0e0c12', '#18151e', '#24202c', '#302a3a'], {}),
    piedraHorno: mat(['#1c1214', '#30201e', '#4a322a', '#664638', '#86604a', '#a87e62', '#c8a080'], { tex: ESCU.TEX.piedra(17, 2.2) }),
    terracota: mat(['#2e1008', '#4e1c10', '#76301a', '#9c4626', '#c06236', '#dc8450', '#f0aa74'], { tex: ESCU.TEX.grumos(19, 3, 0.3) }),
    vidrio: mat(['#1a3a4a', '#2a5a6e', '#3e7e92', '#5aa6b8', '#8ccad8', '#c4ecf4', '#ffffff'], { brillo: 1 }),
    hollin: mat(['#08080a', '#121214', '#1c1c20', '#28282c', '#36363c'], {}),
    sopa: mat(['#3a1a08', '#6a3410', '#9a5418', '#c87a28', '#eaa440', '#ffd070'], { luz: 0.6, tex: ESCU.TEX.grumos(23, 1.5, 1) }),
    colchon: mat(['#4a4038', '#6e6254', '#948674', '#b8aa94', '#d8ccb4', '#f2ead8'], { tex: ESCU.TEX.grumos(29, 2, 0.3) }),
    manta: mat(['#2a0a18', '#4e1228', '#781e3a', '#a42e4c', '#c84a5e', '#e47a7e', '#f6a8a0'], { tex: (x, y, z) => ((Math.floor(x / 3) + Math.floor(z / 3)) % 2 ? -0.6 : 0) }),
    piedraRuina: mat(['#0e1820', '#1a2a34', '#2a404c', '#3e5a68', '#567886', '#7898a6', '#a0bcc6'], { tex: ESCU.TEX.piedra(31, 2.6) }),
    moho: mat(['#140a1c', '#24122e', '#381c44', '#4e2a5c', '#6a3e7a', '#8a5a98', '#b080bc'], { tex: ESCU.TEX.grumos(37, 2, 0.8) }),
    musgo: mat(['#0a1a0c', '#122a14', '#1c3e1c', '#2a5626', '#3c7030', '#548c3c', '#74aa4c'], { tex: ESCU.TEX.grumos(43, 1.4, 1) }),
    maderaSelva: mat(['#1e1208', '#34200e', '#4e3216', '#6a4620', '#885e2c', '#a87a3e', '#c89a58'], { tex: ESCU.TEX.tablas('x', 3, 47) }),
    maderaSelvaV: mat(['#1e1208', '#34200e', '#4e3216', '#6a4620', '#885e2c', '#a87a3e'], { tex: ESCU.TEX.vetas(48) }),
    hojaGrande: mat(['#0a2010', '#12361a', '#1c5024', '#286c2e', '#368a38', '#4ca844', '#72c458'], { tex: (x, y, z) => ((x + z) % 4 === 0 ? -0.8 : 0) }),
    lona: mat(['#3a2414', '#5c3a20', '#80562e', '#a4743e', '#c49454', '#e0b470'], {}),
    lonaRoja: mat(['#3a0c10', '#5e1418', '#882024', '#b0302e', '#d0483c', '#ec7058'], {}),
    agua: mat(['#06142a', '#0c2444', '#143866', '#1e528a', '#2e70aa', '#4a94c8', '#78bce0'], { brillo: 0.8 }),
    runa: mat(['#1a0a3a', '#341466', '#5426a0', '#7a44d0', '#a070f0', '#cca8ff', '#f4e8ff'], { luz: 1, plano: true, sube: 1 }),
    runaVerde: mat(['#06301a', '#0c5028', '#16783a', '#28a050', '#48c870', '#8ae8a0', '#d0ffe0'], { luz: 1, plano: true, sube: 1 }),
  };
})();

// La sombra y el dibujo del modelo: un atajo para hacer un modelo, darle forma y registrarlo.
function modelo(nombre, w, h, d, fn, o) { const m = new ESCU.Modelo(w, h, d); fn(m); return defModelo(nombre, m, o); }


const { SDF: SD, OP } = ESCU;
// cuatro patas cuadradas
function patas(m, x0, x1, z0, z1, h, g, mt) { for (const [x, z] of [[x0, z0], [x1 - g, z0], [x0, z1 - g], [x1 - g, z1 - g]]) m.caja([x, 0, z], [x + g, h, z + g], mt); }
// Material de llama para una llama concreta: el color sale de la altura y de la distancia al centro.
function matLlama(cx, y0, cz, h, sem, rampa) {
  return ESCU.mat(rampa || ['#8a200e', '#d4401c', '#f86a20', '#ff9a30', '#ffc848', '#ffec96', '#ffffff'], {
    luz: 1, plano: true,
    tex: (x, y, z) => { const t = (y + 0.5 - y0) / h, rad = Math.hypot(x + 0.5 - cx, z + 0.5 - cz); return 2.6 - t * 4.2 - rad * 0.55 + (ESCU.ruido(x, y * 0.7, z, 1.6, sem) - 0.5) * 1.4; },
  });
}
function llama(m, cx, y0, cz, h, r, sem, rampa) {
  const mt = matLlama(cx, y0, cz, h, sem, rampa);
  m.figura((x, y, z) => {
    const t = (y - y0) / h;
    if (t < 0) return Math.hypot(x - cx, y - y0, (z - cz) * 1.2) - r;
    if (t > 1.1) return 9;
    const ox = Math.sin(t * 3.4 + sem) * t * r * 0.7, rr = r * (1 - t) * (0.85 + 0.35 * Math.sin(t * 7 + sem * 1.9)) + 0.2;
    return Math.hypot(x - cx - ox, (z - cz) * 1.2) - rr;
  }, [cx - r - 3, y0 - r - 1, cz - r - 2, cx + r + 3, y0 + h + 2, cz + r + 2], mt, { ruido: [0.45, 1.3, sem] });
}

CONSTRUIR_MODELOS.push(function modelosMuebles() {
  const M = MAT;
  // ---------------------------------------------------------------- mesas de trabajo
  // tablero de tablas, patas, balda con cosas y herramientas encima; cada metal la refuerza y le pone lo suyo
  const mesa = (nombre, metal, extra) => modelo(nombre, 18, 14, 14, (m) => {
    patas(m, 2, 16, 2, 12, 8, 2, M.maderaV);
    m.caja([2.5, 2, 2.5], [15.5, 3, 11.5], M.madera);
    m.caja([4, 3, 3], [8, 5, 6], M.maderaZ, { r: 0.3 }); // tablas en la balda
    m.caja([1, 8, 1], [17, 10, 13], M.madera, { r: 0.5 });
    if (metal) {
      for (const [x, z] of [[1, 1], [14, 1], [1, 10], [14, 10]]) m.caja([x - 0.3, 7.6, z - 0.3], [x + 3.3, 10.4, z + 3.3], metal, { r: 0.4 });
      for (const [x, z] of [[2, 2], [14, 2], [2, 10], [14, 10]]) m.caja([x - 0.3, 0, z - 0.3], [x + 2.3, 1.4, z + 2.3], metal);
    }
    extra(m);
  });
  const martillo = (m, x, z, cabeza) => { m.caja([x, 10, z], [x + 1.2, 11.2, z + 7], M.maderaOsc, { r: 0.3 }); m.caja([x - 1.6, 10, z - 0.5], [x + 2.8, 12.6, z + 2], cabeza, { r: 0.4 }); };
  mesa('mesa', null, (m) => {
    martillo(m, 4, 3, M.hierro);
    m.caja([9, 10, 7], [16, 10.7, 11], M.hierro); // serrucho
    for (let x = 9.5; x < 16; x += 1.5) m.caja([x, 10, 10.6], [x + 0.7, 10.7, 11.4], M.hierro);
    m.caja([14.5, 10, 5.5], [16.5, 12, 7.5], M.maderaOsc, { r: 0.5 });
    m.caja([9, 10, 2], [15, 11.2, 5], M.maderaZ, { r: 0.2 }); // tablón
    m.esfera([6, 10.6, 10.5], 1.1, M.hierro); // tuercas
  });
  mesa('mesa_cobre', M.cobre, (m) => {
    martillo(m, 3.5, 3, M.cobre);
    m.cilindro([12, 10, 7], 3, 1.2, M.cobre, { red: 0.3 }); // rueda dentada
    for (let k = 0; k < 8; k++) { const a = k * Math.PI / 4; m.caja([12 + Math.cos(a) * 3.2 - 0.6, 10, 7 + Math.sin(a) * 3.2 - 0.6], [12 + Math.cos(a) * 3.2 + 0.6, 11.2, 7 + Math.sin(a) * 3.2 + 0.6], M.cobre); }
    m.cilindro([12, 10, 7], 1, 1.6, M.maderaOsc);
    m.caja([4, 10, 9.5], [9, 11.4, 12], M.cobre, { r: 0.4 }); // lingote
  });
  mesa('mesa_estano', M.estano, (m) => {
    // tornillo de banco
    m.caja([3, 10, 7], [9, 13, 11], M.estano, { r: 0.5 });
    m.caja([4, 10, 6], [8, 14.5, 7], M.estano);
    m.cilindro([1.5, 12, 9], 0.5, 9, M.hierro, { eje: 'x' });
    m.caja([11, 10, 3], [16, 11, 9], M.papel); // plano
    m.caja([12, 11, 4], [13, 11.4, 8], M.estano);
    m.cilindro([14.5, 10, 10.5], 1.4, 2.4, M.estano, { red: 0.4 }); // bote
  });
  mesa('mesa_hierro', M.hierro, (m) => {
    m.caja([1, 7, 1], [17, 8, 13], M.hierro);
    // pequeña forja de mesa con brasas
    m.caja([10, 10, 3], [16, 13, 9], M.piedraHorno, { r: 0.6 });
    m.quitar(() => m.caja([11, 12, 4], [15, 14, 8], null));
    m.caja([11, 11.6, 4], [15, 12.4, 8], M.brasa);
    martillo(m, 4, 4, M.hierro);
    m.caja([3, 10, 10], [8, 11.2, 12.5], M.hierro, { r: 0.4 });
  });
  mesa('mesa_carmesi', M.carmesi, (m) => {
    m.caja([4, 10, 3], [14, 10.6, 11], M.lonaRoja);
    m.cilindro([9, 10.6, 7], 2.2, 3.5, M.carmesi, { r1: 0.8 }); // cristal carmesí
    m.esfera([9, 14.4, 7], 1.2, ESCU.mat(['#5a0a14', '#a01828', '#e03040', '#ff6a6a', '#ffb0a8', '#fff0ea'], { luz: 1, sube: 1 }));
    martillo(m, 3, 9, M.carmesi);
  });

  // ---------------------------------------------------------------- yunques: sobre un tocón, con su cuerno
  const yunque = (nombre, metal) => modelo(nombre, 18, 14, 14, (m) => {
    m.cilindro([9, 0, 8], 4.6, 5, M.maderaV, { r1: 4.2 });
    m.cilindro([9, 5, 8], 4.2, 0.6, M.madera);
    m.caja([6, 5.5, 5.5], [12, 8, 10.5], metal, { r: 0.6 });
    m.caja([3.5, 8, 4.5], [15, 11, 11.5], metal, { r: 0.6 });
    m.figura(SD.capsula(15, 10, 8, 18, 10.2, 8, 2, 0.4), [12, 7, 5, 18, 13, 11], metal); // cuerno
    m.caja([1.5, 9, 5.5], [4, 11, 10.5], metal, { r: 0.4 });
    m.caja([5, 10.6, 5], [14, 11.2, 11], metal, { sube: 1 });
  });
  yunque('yunque', M.cobre); yunque('yunque_estano', M.estano); yunque('yunque_hierro', M.hierro); yunque('yunque_carmesi', M.carmesi);

  // ---------------------------------------------------------------- horno de piedra con fuego dentro
  for (let f = 0; f < 2; f++) modelo('horno_' + f, 18, 22, 16, (m) => {
    m.figura(OP.une(SD.elipsoide(9, 7, 8, 8, 9, 7), SD.caja(1.5, 0, 1.5, 16.5, 7, 14.5, 1)), [0, 0, 0, 18, 17, 16], M.piedraHorno);
    m.cilindro([9, 13, 6], 2.6, 7, M.piedraHorno, { r1: 2.2 }); // chimenea
    m.quitar(() => m.cilindro([9, 18, 6], 1.5, 3, null));
    m.cilindro([9, 18.6, 6], 1.5, 0.4, M.hollin);
    // boca del horno: arco hacia delante, con brasas y fuego dentro
    m.quitar(() => m.figura(OP.une(SD.caja(5.5, 1.5, 10, 12.5, 6, 17), SD.cilindro(9, 6, 10, 3.5, 7, 'z')), [5, 1, 10, 13, 10, 17], null));
    m.caja([5.5, 1, 9], [12.5, 2.2, 15], M.brasa);
    llama(m, 9, 2, 12, 6 + f, 2.6, 3 + f * 5);
    m.toro([9, 6, 15], 3.9, 0.9, M.piedra, { eje: 'z' });
  });

  // ---------------------------------------------------------------- olla al fuego
  for (let f = 0; f < 2; f++) modelo('olla_' + f, 18, 16, 16, (m) => {
    for (let k = 0; k < 6; k++) { const a = k / 6 * Math.PI * 2 + 0.3; m.esfera([9 + Math.cos(a) * 6, 1, 8 + Math.sin(a) * 5.4], 1.6, M.piedra, { ruido: [0.3, 1, k] }); }
    m.capsula([5, 1, 6], [13, 1, 10], 0.9, M.maderaOsc); m.capsula([5, 1, 10], [13, 1, 6], 0.9, M.maderaOsc);
    llama(m, 9, 1, 8, 4, 2.6, 11 + f * 7);
    m.figura(OP.resta(SD.elipsoide(9, 8, 8, 6, 5.2, 5.6), SD.cilindro(9, 11.4, 8, 4.4, 4)), [2, 2, 1, 16, 14, 15], M.hierro);
    m.toro([9, 11.6, 8], 4.6, 0.8, M.hierro);
    m.cilindro([9, 10, 8], 4.3, 0.8 + f * 0.3, M.sopa);
    m.esfera([7.5 + f * 2, 11, 7], 0.9, M.sopa); // burbuja
    m.capsula([5, 14, 8], [13, 14, 8], 0.5, M.hierro); m.capsula([4.6, 11, 8], [5, 14, 8], 0.5, M.hierro); m.capsula([13.4, 11, 8], [13, 14, 8], 0.5, M.hierro);
  });

  // ---------------------------------------------------------------- cofres
  const cofre = (nombre, cuerpo, fleje, cierre, extra) => modelo(nombre, 16, 13, 13, (m) => {
    m.caja([1, 0, 1.5], [15, 7, 11.5], cuerpo, { r: 0.6 });
    m.figura(OP.corta(SD.cilindro(1, 7, 6.5, 5, 14, 'x', 5, 0.6), OP.sobre(7)), [1, 7, 1, 15, 13, 12], cuerpo);
    for (const x of [2.4, 12.4]) {
      m.caja([x, 0, 1.1], [x + 1.2, 7.3, 11.9], fleje, { r: 0.2 });
      m.figura(OP.corta(SD.cilindro(x, 7, 6.5, 5.4, 1.2, 'x'), OP.sobre(7)), [x, 7, 0, x + 1.2, 13, 13], fleje);
    }
    m.caja([0.6, 6.4, 1.1], [15.4, 7.4, 11.9], fleje, { r: 0.3 });
    m.caja([6.4, 4.2, 11], [9.6, 8.6, 12.2], cierre, { r: 0.4 });
    m.caja([7.6, 5, 12], [8.4, 6.6, 12.4], M.negro);
    if (extra) extra(m);
  });
  cofre('cofre', M.madera, M.hierro, M.oro);
  cofre('cofre_ruinas', M.piedraRuina, M.oro, M.oro, (m) => { m.esfera([8, 12.4, 6.5], 1.2, M.cristal); });
  cofre('cofre_moho', M.moho, M.hierro, M.cristal, (m) => { for (const [x, z] of [[3, 3], [13, 9], [10, 2]]) m.esfera([x, 9.5 + (x % 3), z], 1.6, M.musgo); });
  cofre('cofre_selva', M.maderaSelva, M.oro, M.oro, (m) => {
    m.tubo([[0.5, 2, 11], [3, 5, 12], [6, 9, 11.5], [10, 11.6, 9], [14, 10, 5]], 0.7, 0.5, M.hoja);
    m.elipsoide([4, 6, 12.3], [1.4, 0.6, 1], M.hojaGrande); m.elipsoide([11, 11.8, 8], [1.6, 0.6, 1.2], M.hojaGrande);
  });

  // ---------------------------------------------------------------- cama: marco, colchón, almohada y manta
  modelo('cama', 16, 14, 18, (m) => {
    patas(m, 1, 15, 1, 17, 4, 2, M.maderaV);
    m.caja([1, 2, 1], [15, 4.5, 17], M.madera, { r: 0.4 });
    m.caja([0.6, 0, 0.5], [15.4, 12, 3], M.madera, { r: 0.6 }); // cabecero
    m.caja([2, 9, 0.2], [14, 11, 0.8], M.maderaOsc);
    m.caja([1.5, 4.5, 2.5], [14.5, 7, 16.5], M.colchon, { r: 1 });
    m.elipsoide([8, 7.6, 5.4], [5, 1.8, 2.6], M.colchon); // almohada
    m.caja([1.2, 4.3, 8], [14.8, 8, 16.9], M.manta, { r: 1.2 });
    m.caja([1.1, 7.2, 8], [14.9, 8.2, 9.4], ESCU.mat(['#5a4a38', '#8a7656', '#b8a888', '#e0d4bc', '#f6eedc'], {}), { r: 0.4 }); // embozo
    m.caja([0.6, 0, 15], [15.4, 6.4, 17.6], M.madera, { r: 0.6 }); // pie
  });

  // ---------------------------------------------------------------- puerta: marco y hoja de tablas con bisagras
  const puerta = (nombre, abierta) => modelo(nombre, 16, 22, 10, (m) => {
    m.caja([0, 0, 3], [2.2, 17, 7], M.maderaV); m.caja([13.8, 0, 3], [16, 17, 7], M.maderaV);
    m.caja([0, 16, 2.6], [16, 19, 7.4], M.madera, { r: 0.4 });
    if (!abierta) {
      m.caja([2.2, 0, 4.2], [13.8, 16, 5.8], ESCU.mat(M.madera.r, { tex: ESCU.TEX.tablas('z', 3, 9) }));
      for (let x = 2.2; x < 13.8; x += 2.9) m.caja([x, 0, 5.6], [x + 0.4, 16, 5.9], M.maderaOsc);
      for (const y of [3, 12]) m.caja([2, y, 5.6], [10, y + 1.4, 6.2], M.hierro, { r: 0.2 });
      m.esfera([11.6, 8, 6.2], 0.9, M.oro);
    } else {
      m.caja([2.2, 0, 5], [3.8, 16, 10], ESCU.mat(M.madera.r, { tex: ESCU.TEX.tablas('y', 3, 9) }));
      for (const y of [3, 12]) m.caja([3.6, y, 5], [4, y + 1.4, 9], M.hierro);
    }
  });
  puerta('puerta', false); puerta('puerta_abierta', true);

  // ---------------------------------------------------------------- macetas, caja, vasijas, barril
  const flores = (m, cx, y, cz, n, sem, colores) => {
    const r = azar(sem);
    for (let k = 0; k < n; k++) {
      const a = r() * 6.28, d = r() * 2.4, x = cx + Math.cos(a) * d, z = cz + Math.sin(a) * d * 0.8, h = y + 3 + r() * 3;
      m.capsula([cx + (x - cx) * 0.3, y, cz + (z - cz) * 0.3], [x, h, z], 0.35, M.hoja);
      m.esfera([x, h + 0.6, z], 1.2, ESCU.mat(ESCU.rampa(colores[k % colores.length], 5), {}));
      m.esfera([x, h + 0.9, z + 0.4], 0.5, M.oro);
    }
    for (let k = 0; k < 4; k++) { const a = k * 1.7 + sem; m.elipsoide([cx + Math.cos(a) * 2, y + 1, cz + Math.sin(a) * 1.6], [1.6, 0.7, 1], M.hoja); }
  };
  modelo('maceta', 14, 16, 12, (m) => {
    m.cilindro([7, 0, 6], 3.4, 6, M.terracota, { r1: 4.4 });
    m.toro([7, 6, 6], 4.4, 0.9, M.terracota);
    m.cilindro([7, 5, 6], 3.8, 0.8, M.tierraOscura || M.cuero);
    flores(m, 7, 6, 6, 3, 3, ['#e04050', '#f0c040', '#e070c0']);
  });
  modelo('maceta_grande', 18, 22, 16, (m) => {
    m.figura((x, y, z) => { const t = y / 9, r = 6 + Math.sin(t * 2.6) * 1.5; return Math.max(Math.hypot(x - 9, z - 8) - r, -y, y - 9); }, [0, 0, 0, 18, 10, 16], M.terracota);
    m.toro([9, 9, 8], 6.2, 1, M.terracota);
    for (const y of [3, 6]) m.toro([9, y, 8], 7.3, 0.5, ESCU.mat(ESCU.rampa('#d0a040', 5), {}));
    m.cilindro([9, 8, 8], 5.6, 1, M.cuero);
    for (let k = 0; k < 7; k++) { const a = k / 7 * 6.28; m.capsula([9, 9, 8], [9 + Math.cos(a) * 6.5, 15 + (k % 3) * 1.6, 8 + Math.sin(a) * 4.5], 1.4, 0.5, M.hojaGrande); }
    m.esfera([9, 15, 8], 2.4, M.hoja);
  });
  modelo('caja_madera', 16, 14, 14, (m) => {
    m.caja([1, 0, 1], [15, 11, 13], ESCU.mat(M.madera.r, { tex: ESCU.TEX.tablas('x', 5, 13) }), { r: 0.4 });
    for (const [a, b] of [[[1, 0.5, 13], [15, 10.5, 13]], [[1, 10.5, 13], [15, 0.5, 13]]]) m.capsula(a, b, 0.8, M.maderaOsc);
    m.caja([0.6, 10, 0.6], [15.4, 11.4, 13.4], M.maderaV, { r: 0.3 });
    m.caja([0.6, 0, 12.4], [2, 11, 13.6], M.maderaV); m.caja([14, 0, 12.4], [15.4, 11, 13.6], M.maderaV);
    for (const [x, y] of [[1.3, 1], [14.7, 1], [1.3, 10], [14.7, 10]]) m.esfera([x, y, 13.4], 0.5, M.hierro);
  });
  const vasija = (nombre, mt, banda, conFlores) => modelo(nombre, 14, conFlores ? 22 : 16, 12, (m) => {
    m.figura((x, y, z) => { const t = y / 12, r = 2.6 + Math.sin(t * 3.1) * 3.4 - (t > 0.85 ? (t - 0.85) * 6 : 0); return Math.max(Math.hypot(x - 7, z - 6) - r, -y, y - 12); }, [0, 0, 0, 14, 13, 12], mt);
    m.toro([7, 12, 6], 2.6, 0.9, mt);
    m.quitar(() => m.cilindro([7, 10, 6], 1.8, 4, null));
    if (banda) m.pintar(() => { m.caja([0, 5, 0], [14, 6.2, 12], banda); m.caja([0, 7.4, 0], [14, 8, 12], banda); });
    if (conFlores) flores(m, 7, 12, 6, 4, 9, ['#ff6070', '#ffd040', '#70a0ff']);
  });
  vasija('vasija', M.terracota, ESCU.mat(ESCU.rampa('#e8c070', 6), {}));
  vasija('vasija_flor', ESCU.mat(ESCU.rampa('#5aa0b0', 6), { tex: ESCU.TEX.grumos(51, 3, 0.3) }), ESCU.mat(ESCU.rampa('#f0e0c0', 6), {}), true);
  modelo('barril', 14, 16, 12, (m) => {
    m.figura((x, y, z) => { const t = (y - 7) / 7, r = 6 - t * t * 1.4; return Math.max(Math.hypot(x - 7, z - 6) - r, -y, y - 14); }, [0, 0, 0, 14, 14, 12], M.maderaV);
    m.cilindro([7, 13, 6], 4.8, 1, M.madera);
    for (const y of [2, 11.4]) m.toro([7, y + 0.5, 6], 5.9, 0.6, M.hierro);
  });

  // ---------------------------------------------------------------- muebles de selva
  modelo('estanteria', 16, 26, 10, (m) => {
    m.caja([0.5, 0, 1], [2, 24, 9], M.maderaSelvaV); m.caja([14, 0, 1], [15.5, 24, 9], M.maderaSelvaV);
    m.caja([0.5, 0, 1], [15.5, 24, 2], M.maderaSelva);
    for (const y of [0, 7.6, 15.2, 22.8]) m.caja([0.5, y, 1], [15.5, y + 1.3, 9], M.maderaSelva, { r: 0.2 });
    const r = azar(5);
    for (const y of [1.3, 8.9]) for (let x = 2.2; x < 13.6;) {
      const w = 1 + Math.floor(r() * 2), h = 4 + r() * 2;
      m.caja([x, y, 3], [x + w, y + h, 8], ESCU.mat(ESCU.rampa(['#a83030', '#3060a0', '#40904a', '#c09030', '#7040a0'][Math.floor(r() * 5)], 5), {}), { r: 0.2 });
      x += w + 0.2;
    }
    for (const x of [3.5, 7.5, 11.5]) { m.cilindro([x, 16.5, 5.5], 1.4, 3, M.vidrio, { red: 0.4 }); m.cilindro([x, 19.5, 5.5], 0.8, 1, M.corcho || M.cuero); }
  });
  modelo('silla_selva', 14, 18, 12, (m) => {
    patas(m, 2, 12, 2, 11, 6, 1.4, M.maderaSelvaV);
    m.caja([1.5, 6, 1.5], [12.5, 7.4, 11], M.maderaSelva, { r: 0.4 });
    m.caja([2, 6, 1.4], [3.4, 16, 2.8], M.maderaSelvaV); m.caja([10.6, 6, 1.4], [12, 16, 2.8], M.maderaSelvaV);
    m.caja([2, 11, 1.6], [12, 15.4, 2.4], M.maderaSelva, { r: 0.3 });
    m.elipsoide([7, 7.8, 6.5], [4.6, 0.9, 4], M.hojaGrande);
  });
  modelo('mesa_selva', 18, 12, 16, (m) => {
    m.cilindro([9, 0, 8], 2, 7, M.maderaSelvaV, { r1: 1.6 });
    for (const a of [0.4, 2.5, 4.6]) m.capsula([9, 1.5, 8], [9 + Math.cos(a) * 6, 0, 8 + Math.sin(a) * 5], 1.1, 0.7, M.maderaSelvaV);
    m.cilindro([9, 7, 8], 8, 1.6, M.maderaSelva, { red: 0.5 });
    m.cilindro([6, 8.6, 6], 1.6, 1.8, M.terracota); m.esfera([12, 9.4, 9], 1.4, ESCU.mat(ESCU.rampa('#e05030', 5), {}));
  });
  modelo('valla', 16, 14, 6, (m) => {
    for (const x of [1.5, 14.5]) { m.cilindro([x, 0, 3], 1.2, 11, M.maderaSelvaV); m.cilindro([x, 11, 3], 1.2, 1, M.maderaSelva, { r1: 0.3 }); }
    for (const y of [3.5, 8]) m.caja([0, y, 2.2], [16, y + 1.6, 3.8], M.maderaSelva, { r: 0.3 });
    m.toro([1.5, 8.6, 3], 1.5, 0.4, M.cuerda); m.toro([14.5, 4, 3], 1.5, 0.4, M.cuerda);
  });
  modelo('banderin', 14, 26, 8, (m) => {
    m.cilindro([3, 0, 4], 0.8, 24, M.maderaSelvaV);
    m.esfera([3, 24.6, 4], 1.2, M.oro);
    m.figura((x, y, z) => { const t = (x - 3.6) / 9; return Math.max(Math.abs(z - 4 - Math.sin(t * 5) * 0.8) - 0.5, 3.6 - x, x - 12.6, 13 - y - t * 2, y - 22); }, [3, 10, 1, 13, 23, 7], M.lonaRoja);
    m.pintar(() => m.esfera([8, 17.5, 4], 1.8, M.oro));
  });

  // ---------------------------------------------------------------- luces
  // antorcha de pie: palo, cesto de hierro y llama (4 cuadros; m = mágica, morada)
  const LLAMA_MAGICA = ['#30104a', '#54208a', '#7a38c8', '#a060f0', '#c898ff', '#ecd8ff', '#ffffff'];
  for (const mag of ['', 'm']) for (let f = 0; f < 4; f++) modelo(`antorcha_${f}${mag}`, 12, 26, 8, (m) => {
    m.cilindro([6, 0, 4], 1.3, 2, M.piedra, { r1: 1 });
    m.cilindro([6, 1, 4], 0.75, 14, M.maderaOsc);
    m.figura(OP.resta(SD.cilindro(6, 14, 4, 1.8, 3.4, 'y', 2.6), SD.cilindro(6, 15, 4, 1.8, 4, 'y', 2.1)), [3, 14, 1, 9, 18, 7], M.hierro);
    m.cilindro([6, 15, 4], 2, 1.4, M.brasa);
    llama(m, 6, 16.4, 4, 7.2 + (f % 2) * 0.8, 2.3, f * 2.3 + (mag ? 9 : 0), mag ? LLAMA_MAGICA : null);
  });
  // lámpara de cristal sobre un pie de piedra
  modelo('lampara', 12, 20, 10, (m) => {
    m.cilindro([6, 0, 5], 3.4, 2, M.piedra, { r1: 2.8, red: 0.4 });
    m.cilindro([6, 2, 5], 1.4, 6, M.piedraLisa);
    m.cilindro([6, 7.6, 5], 2.8, 1, M.hierro, { red: 0.3 });
    for (const [dx, h] of [[0, 9], [-1.6, 6], [1.7, 7]]) m.figura(SD.capsula(6 + dx * 0.5, 8.4, 5, 6 + dx, 8.4 + h, 5 + dx * 0.3, 1.5, 0.3), [0, 6, 0, 12, 20, 10], M.cristal);
  });
  // farol: poste de madera con un farol colgado
  const farol = (nombre, metal, vidrio) => modelo(nombre, 14, 30, 10, (m) => {
    m.cilindro([5, 0, 5], 2.2, 1.4, M.piedra, { red: 0.4 });
    m.cilindro([5, 1, 5], 1, 24, M.maderaV);
    m.caja([4, 23, 4.2], [12, 24.4, 5.8], M.maderaV, { r: 0.3 });
    m.capsula([10, 23, 5], [10, 21, 5], 0.3, M.hierro);
    m.caja([7.6, 14, 2.6], [12.4, 15, 7.4], metal, { r: 0.3 });
    m.caja([7.8, 20, 2.8], [12.2, 21, 7.2], metal, { r: 0.3 });
    m.figura(SD.caja(8.2, 15, 3.2, 11.8, 20, 6.8, 0.5), [8, 15, 3, 12, 20, 7], vidrio);
    for (const [x, z] of [[8, 3], [12, 3], [8, 7], [12, 7]]) m.caja([x - 0.4, 14, z - 0.4], [x + 0.4, 21, z + 0.4], metal);
  });
  const VIDRIO_LUZ = ESCU.mat(['#a05a10', '#d08a20', '#f0b030', '#ffd050', '#ffec90', '#fffbe0', '#ffffff'], { luz: 1, plano: true, sube: 2, tex: (x, y) => (y > 18 ? 1 : 0) });
  farol('farol', M.oro, VIDRIO_LUZ);
  farol('farol_carmesi', M.carmesi, ESCU.mat(['#801010', '#c02020', '#f04040', '#ff7a6a', '#ffb4a4', '#ffe8e0', '#ffffff'], { luz: 1, plano: true, sube: 2 }));
  modelo('lampara_pie', 12, 28, 10, (m) => {
    m.cilindro([6, 0, 5], 3, 1.2, M.oro, { red: 0.4 });
    m.cilindro([6, 1, 5], 0.95, 18, M.oro);
    m.figura(OP.resta(SD.cilindro(6, 18, 5, 4.6, 6, 'y', 2.6), SD.cilindro(6, 17, 5, 3.8, 6.4, 'y', 2.0)), [0, 17, 0, 12, 25, 10], ESCU.mat(['#5a3a10', '#8a5a1a', '#b8822c', '#e0aa48', '#f6d07a', '#fff0c0'], { luz: 0.7, sube: 1 }));
    m.esfera([6, 19.5, 5], 1.4, VIDRIO_LUZ);
  });
  // hoguera: troncos en cruz, piedras alrededor y fuego (3 cuadros)
  for (let f = 0; f < 3; f++) modelo('hoguera_' + f, 18, 18, 16, (m) => {
    for (let k = 0; k < 8; k++) { const a = k / 8 * Math.PI * 2; m.esfera([9 + Math.cos(a) * 6.4, 1, 8 + Math.sin(a) * 5.6], 1.7, M.piedra, { ruido: [0.4, 1, k + 3] }); }
    for (const a of [0.3, 1.9, 3.5, 5.1]) m.capsula([9 + Math.cos(a) * 5, 0.8, 8 + Math.sin(a) * 4.4], [9 - Math.cos(a) * 1, 4, 8 - Math.sin(a) * 1], 1.1, 0.8, M.corteza);
    m.cilindro([9, 0, 8], 3.4, 1.2, M.brasa);
    llama(m, 9, 2, 8, 9 + (f === 1 ? 1.5 : 0), 3.4, 21 + f * 3.7);
  });
  // incubadora: base de metal, cúpula de vidrio y, si tiene huevo, el huevo con su luz
  const incu = (nombre, huevo, lista) => modelo(nombre, 16, 20, 14, (m) => {
    m.cilindro([8, 0, 7], 6.4, 4, M.cobre, { r1: 5.8, red: 0.6 });
    m.cilindro([8, 4, 7], 5.6, 1, M.hierro);
    for (let k = 0; k < 4; k++) { const a = k * Math.PI / 2 + 0.7; m.esfera([8 + Math.cos(a) * 6, 2, 7 + Math.sin(a) * 6], 1, M.oro); }
    m.cilindro([3.5, 1.6, 12.6], 0.7, 1, ESCU.mat(lista ? ['#104010', '#20a020', '#60ff60', '#c0ffc0'] : ['#401010', '#a02020', '#ff6040', '#ffc0a0'], { luz: 1, plano: true, sube: 2 }), { eje: 'z' });
    if (huevo) m.elipsoide([8, 8.4, 7], [2.8, 3.6, 2.8], ESCU.mat(['#3a3020', '#6a5a3a', '#a08a60', '#d0bc90', '#f0e2c0', '#fffaf0'], { luz: lista ? 0.8 : 0.4, tex: ESCU.TEX.motas(61, 0.15, -2) }));
    // cúpula de vidrio: solo el aro y unos reflejos, para que se vea lo de dentro
    const vid = ESCU.mat(['#1e3a46', '#2e5664', '#44788a', '#6aa0b0', '#a4d4e0', '#e8fcff'], { brillo: 0.6 });
    m.figura(OP.resta(SD.esfera(8, 5, 7, 5.4), SD.esfera(8, 5, 7, 4.6), SD.caja(0, 0, 0, 16, 5, 14), SD.caja(3.2, 5.6, 9, 12.8, 9.4, 14)), [2, 5, 1, 14, 11, 13], vid, { modo: huevo ? 'debajo' : 'añadir' });
    m.cilindro([8, 10.3, 7], 1.2, 1.2, M.cobre);
  });
  incu('incubadora', false); incu('incubadora_on', true); incu('incubadora_lista', true, true);
});
