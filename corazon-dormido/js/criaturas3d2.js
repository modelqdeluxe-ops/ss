'use strict';
// Las criaturas de la Ciénaga de la Podre y de la Ciudad Velada (0.7) y sus dos jefes, con la dirección de LORE.md:
// siluetas que se reconocen por la sombra, ojos que son puntos de luz y la Podre en magenta. De perfil (mirando a la
// derecha) las criaturas, de frente los jefes. Cuadros: 0-1 andan, 2 prepara, 3 ataca (salvo donde se dice).

CONSTRUIR_MODELOS.push(function criaturasBandas() {
  const O = OSC, M = MAT, { SDF, OP, mat, rampa, TEX } = ESCU;
  const ANDA = [{ fase: 1 }, { fase: -1 }];
  const P = {
    sanguijuela: mat(['#040506', '#080a0c', '#0e1212', '#151b18', '#1e261f', '#2a3428', '#3a4634'], { brillo: 0.9, tex: (x, y, z) => (Math.floor(x / 2.2) % 2 ? -0.35 : 0) }),
    vientre: mat(['#2a1e1a', '#46322a', '#644a3c', '#846452', '#a4826a'], {}),
    sapo: mat(['#0a0c08', '#141810', '#20261a', '#2e3624', '#3e4830', '#525c3e', '#687450'], { brillo: 0.5, tex: TEX.motas(331, 0.18, 1.3) }),
    mosca: mat(['#060608', '#0e0e12', '#18181e', '#24242c', '#30303a', '#40404c'], { brillo: 0.9 }),
    ojoMosca: mat(['#3a0606', '#6a0c0c', '#9a1a12', '#c8301e', '#e85a3a'], { luz: 0.8, brillo: 1, tex: (x, y, z) => ((x + y + z) % 2 ? -0.8 : 0) }),
    alaSucia: mat(['#1e2020', '#343834', '#4c524a', '#666e62', '#828a7c'], { tex: (x, yy) => ((Math.round(x * 1.3 + yy) % 3 === 0) ? -2 : 0) }),
    farolero: mat(['#040406', '#08080c', '#0e0e14', '#16161e', '#20202a', '#2c2c38'], {}),
    cebo: mat(['#3a5a0a', '#6a9a14', '#a8d82a', '#e0ff6a', '#f8ffc8', '#ffffff'], { luz: 1, plano: true, sube: 2 }),
    fango: mat(['#0e0a06', '#1c140c', '#2c2014', '#3e2e1c', '#523e26', '#684e30'], { tex: TEX.grumos(333, 1.4, 1) }),
    red: mat(['#2a2418', '#463c28', '#625438', '#806e4a'], { tex: (x, y, z) => ((Math.round(x) + Math.round(y)) % 3 === 0 || (Math.round(x) - Math.round(y)) % 3 === 0 ? 0 : -9) }),
    velo: mat(['#0c0e14', '#161a24', '#222836', '#303848', '#40495c', '#525e74', '#6a7890'], { tex: (x, y) => (y % 3 === 0 ? -0.8 : 0) }),
    velado: mat(['#1a2a4a', '#2a4472', '#3e64a0', '#5a88c8', '#86b0e6', '#c0dcff'], { luz: 0.45, brillo: 0.4, tex: (x, y) => (y % 3 === 0 ? -0.9 : 0) }),
    almaFria: mat(['#1a3a6a', '#2a5a9a', '#4a86cc', '#7ab2f0', '#b4dcff', '#e8f6ff', '#ffffff'], { luz: 1, plano: true, sube: 2 }),
    piedra: mat(['#121418', '#1e2128', '#2c3038', '#3c424e', '#4e5562', '#646c7a', '#7e8696'], { tex: TEX.piedra(335, 2.6) }),
    grieta: mat(['#2a5a9a', '#4a86cc', '#7ab2f0', '#b4dcff', '#e8f6ff'], { luz: 1, plano: true }),
    pielGris: mat(['#0e0c0e', '#1c181a', '#2c2628', '#3e3638', '#52484a', '#685c5c', '#807270'], { tex: (x, y, z) => (ESCU.ruido(x * 1.4, y * 1.4, z * 1.4, 1.2, 337) > 0.7 ? -1.4 : 0) }),
    hierroViejo: mat(['#0e0e10', '#1a1a1e', '#28282e', '#38383f', '#4a4a52', '#5e5e68'], { brillo: 0.5, tex: TEX.grumos(339, 1.4, 0.6) }),
  };

  // ---------------------------------------------------------------- Sanguijuela de turba
  for (let f = 0; f < 4; f++) perfil(`sanguijuela_${f}`, 46, 26, 18, (m, cz) => {
    const alza = f === 2 ? 1 : f === 3 ? 0.5 : 0, pts = [];
    for (let k = 0; k < 10; k++) {
      const t = k / 9, ond = f < 2 ? Math.sin(t * 7 + f * 3) * 1.2 : 0;
      pts.push([4 + t * 32 + (f === 3 ? t * 5 : 0), 3 + ond + (t > 0.6 ? Math.pow((t - 0.6) / 0.4, 2) * 10 * alza : 0), cz + Math.sin(t * 5 + f) * 1.4]);
    }
    pts.forEach((p, k) => { const t = k / 9, r = 2.4 + Math.sin(t * Math.PI) * 2.2; m.esfera(p, r, P.sanguijuela); m.elipsoide([p[0], p[1] - r * 0.55, p[2]], [r * 0.9, r * 0.35, r * 0.8], P.vientre); });
    const h = pts[9], b = [h[0] + 2.4, h[1], cz + 0.6];
    m.cilindro([b[0], b[1], cz - 2.6], 3.4, 4, P.sanguijuela, { eje: 'z', red: 1 });
    m.cilindro([b[0] + 0.4, b[1], cz + 1.4], f >= 2 ? 2.8 : 2, 0.6, O.boca, { eje: 'z' });
    for (let k = 0; k < 9; k++) { const a = k / 9 * Math.PI * 2, R = f >= 2 ? 2.8 : 2; m.capsula([b[0] + 0.4 + Math.cos(a) * R, b[1] + Math.sin(a) * R, cz + 1.8], [b[0] + 0.4 + Math.cos(a) * R * 0.5, b[1] + Math.sin(a) * R * 0.5, cz + 2.2], 0.45, 0.12, O.diente); }
    for (const dy of [1.6, 2.4]) m.esfera([h[0] - 1, h[1] + dy, cz + 2.4], 0.5, O.ojoRojo);
    if (f === 0) podre(m, [pts[4][0], pts[4][1] + 3, cz + 1], 1.4, 341);
  }, { cx: 20 });

  // ---------------------------------------------------------------- Farolero: un cuerpo casi invisible y una luz que te llama
  for (let f = 0; f < 4; f++) perfil(`farolero_${f}`, 50, 54, 20, (m, cz) => {
    const inc = f === 2 ? -0.15 : f === 3 ? 0.45 : 0.1, boca = f >= 2 ? 1 : 0.25, paso = f < 2 ? (f ? 1 : -1) : 0;
    for (const [dz, s] of [[-2.4, -1], [2.4, 1]]) m.con({ rot: ['z', paso * s * 0.3, [18, 16, cz + dz]] }, () => {
      m.tubo([[18, 16, cz + dz], [22, 9, cz + dz], [17, 3, cz + dz], [20, 0.4, cz + dz]], 1.4, 0.8, P.farolero);
      for (const d of [-1, 1]) m.capsula([20, 0.4, cz + dz], [23, 0.2, cz + dz + d * 0.8], 0.4, 0.15, P.farolero);
    });
    m.con({ rot: ['z', -inc, [18, 16, cz]] }, () => {
      m.tubo([[18, 16, cz], [19, 26, cz], [22, 34, cz]], 3, 2.2, P.farolero);
      for (let k = 0; k < 6; k++) m.esfera([18 + k * 0.6, 19 + k * 2.6, cz - 2.2], 0.9, O.hueso); // columna que asoma
      // cabeza alargada, mandíbula colgante llena de agujas
      const hx = 25, hy = 36;
      m.elipsoide([hx, hy, cz], [5.6, 3.4, 3.4], P.farolero);
      m.con({ rot: ['z', -0.4 - boca * 0.5, [hx - 3, hy - 1.4, cz]] }, () => {
        m.elipsoide([hx + 1, hy - 3, cz], [5.4, 1.6, 3], P.farolero);
        for (let k = 0; k < 6; k++) m.capsula([hx - 2 + k * 1.4, hy - 2, cz + 2.2], [hx - 2 + k * 1.4, hy - 0.2, cz + 2.4], 0.4, 0.1, O.diente);
      });
      for (let k = 0; k < 6; k++) m.capsula([hx - 2 + k * 1.4, hy - 1.6, cz + 2.4], [hx - 2 + k * 1.4, hy - 3, cz + 2.6], 0.4, 0.1, O.diente);
      m.esfera([hx + 2.4, hy + 1.4, cz + 2.4], 0.35, O.ojoPalido);
      // el tallo con el cebo, colgando delante de la boca
      const sway = f === 0 ? 1 : f === 1 ? -1 : 0;
      m.tubo([[hx - 2, hy + 3, cz], [hx + 4, hy + 9, cz], [hx + 10, hy + 7 + sway, cz + 1], [hx + 11 + sway, hy + 1, cz + 1.4]], 0.7, 0.35, P.farolero);
      m.esfera([hx + 11 + sway, hy - 0.6, cz + 1.4], 2, P.cebo);
      // brazos larguísimos
      for (const [dz, a] of [[-3, 0.3], [3, f === 3 ? 1.4 : f === 2 ? -0.4 : 0.1]]) m.con({ rot: ['z', a, [20, 30, cz + dz]] }, () => {
        m.tubo([[20, 30, cz + dz], [26, 22, cz + dz], [29, 12, cz + dz]], 1.1, 0.7, P.farolero);
        for (const d of [-1, 0, 1]) m.capsula([29, 12, cz + dz], [31 + d * 0.6, 8, cz + dz + d * 0.6], 0.4, 0.12, O.hueso);
      });
    });
  }, { cx: 19 });

  // ---------------------------------------------------------------- Sapo hueco (0 quieto, 1 aplastado, 2 en el aire)
  for (let f = 0; f < 3; f++) perfil(`sapo_hueco_${f}`, 34, 28, 22, (m, cz) => {
    const y = f === 2 ? 4 : 0, ach = f === 1 ? 0.8 : 1;
    for (const dz of [-1, 1]) { m.tubo([[12, 4 + y, cz + dz * 5], [7, f === 2 ? 1 + y : 2, cz + dz * 7], [11, 0.4 + (f === 2 ? y - 2 : 0), cz + dz * 8]], 2.2, 1.2, P.sapo); m.capsula([20, 4 + y, cz + dz * 4], [22, 0.4 + (f === 2 ? y : 0), cz + dz * 5], 1.3, 0.8, P.sapo); }
    m.elipsoide([15, 8 * ach + y, cz], [10, 7 * ach, 8.4], P.sapo, { ruido: [0.6, 1.6, 343] });
    m.elipsoide([17, 4 * ach + y, cz + 1], [8, 3.4 * ach, 7], O.pielPalida);
    m.elipsoide([24, 9 * ach + y, cz], [4.6, 3.6 * ach, 5.4], P.sapo);
    m.figura(SDF.elipsoide(26, 7.4 * ach + y, cz, 3.4, 0.8, 4.6), [22, 5 + y, cz - 6, 31, 10 + y, cz + 6], O.boca, { modo: 'pintar' });
    for (const dz of [-1, 1]) { m.esfera([24.6, 12 * ach + y, cz + dz * 2.8], 1.3, O.carneMuerta); m.esfera([25.4, 12.2 * ach + y, cz + dz * 3.2], 0.55, O.ojoAmbar); }
    podre(m, [13, 13 * ach + y, cz + 1], 2.4, 345); podre(m, [9, 11 * ach + y, cz - 3], 1.8, 346);
    for (const [x, dz] of [[17, 4], [11, 5], [14, -4]]) m.esfera([x, 12 * ach + y, cz + dz], 0.6, O.poro);
  }, { cx: 16 });

  // ---------------------------------------------------------------- Moscarda (0-1 aletea, 2 ataca)
  for (let f = 0; f < 3; f++) perfil(`moscarda_${f}`, 34, 28, 22, (m, cz) => {
    const y = 13;
    m.elipsoide([16, y, cz], [3.6, 3.2, 3.4], P.mosca);
    m.con({ rot: ['z', f === 2 ? 0.5 : -0.1, [14, y, cz]] }, () => { m.elipsoide([8.6, y - 1, cz], [5.6, 4.4, 4.2], O.podre); for (let k = 0; k < 4; k++) m.esfera([6 + k * 1.6, y + 1.6 + (k % 2), cz + 3], 0.55, O.poro); });
    m.esfera([21, y + 0.4, cz], 2.4, P.mosca);
    m.esfera([21.6, y + 1.2, cz + 1.6], 1.8, P.ojoMosca);
    m.capsula([22.4, y - 1.4, cz + 0.6], [24.4, y - 4, cz + 0.8], 0.6, 0.3, P.mosca);
    const ala = f === 1 ? -0.6 : 0.8;
    for (const dz of [-1, 1]) m.con({ rot: ['x', dz * ala, [16, y + 2.6, cz]] }, () => m.elipsoide([12, y + 5.6, cz + dz * 1.2], [6.4, 2.6, 0.4], P.alaSucia));
    for (const dz of [-1, 1]) for (const dx of [-1, 1, 3]) m.tubo([[16 + dx, y - 2, cz + dz * 1.6], [17 + dx, y - 5, cz + dz * 2.6], [18 + dx, y - 7.6, cz + dz * 2.8]], 0.4, 0.2, P.mosca);
  }, { cx: 15 });

  // ---------------------------------------------------------------- Gruto del fango: máscara de cráneo de pez, red y arpón
  const arpon = (m, [x, y, z], e) => {
    m.capsula([x - 9 * e, y - 6 * e, z], [x + 12 * e, y + 9 * e, z], 0.55 * e, M.maderaOsc);
    m.figura(SDF.capsula(x + 12 * e, y + 9 * e, z, x + 15.4 * e, y + 11.6 * e, z, 1.2 * e, 0.1), [x + 10 * e, y + 7 * e, z - 2, x + 17 * e, y + 13 * e, z + 2], P.hierroViejo);
    m.capsula([x + 13 * e, y + 10.2 * e, z], [x + 12.4 * e, y + 12 * e, z], 0.4 * e, 0.1, P.hierroViejo);
  };
  const cabezaPez = (m, x, y, cz, e) => {
    m.esfera([x, y, cz], 3.6 * e, O.hueso);
    m.figura(SDF.capsula(x + 1, y - 0.4, cz, x + 6, y - 1.2, cz, 2.6, 1.2), [x - 2, y - 5, cz - 4, x + 9, y + 3, cz + 4], O.hueso); // hocico de pez
    for (let k = 0; k < 7; k++) m.capsula([x + 1 + k * 0.8, y - 1.6, cz + 1.4], [x + 1.2 + k * 0.8, y - 3, cz + 1.6], 0.35, 0.1, O.diente);
    m.esfera([x + 0.6, y + 0.8, cz + 2.8], 1.7, O.hueco, { modo: 'pintar' }); m.esfera([x + 1, y + 0.8, cz + 3.4], 0.6, O.ojoAmbar);
    for (let k = 0; k < 4; k++) m.capsula([x - 2 - k, y + 3 - k * 0.6, cz], [x - 3 - k, y + 5.4 - k, cz], 0.5, 0.15, O.hueso); // aleta dorsal
  };
  const grutosFango = (poses) => poses.forEach((p, f) => perfil(`gruto_fango_${f}`, 46, 40, 18, (m, cz) => grutoPerfil(m, cz, Object.assign({ piel: P.fango, armaL: arpon, cabeza: cabezaPez, espalda: (m2, cz2) => { m2.elipsoide([14, 20, cz2 - 1], [4.6, 3.6, 3.6], P.red); podre(m2, [16, 23, cz2 + 1], 1.4, 347); } }, p)), { cx: 18 }));
  grutosFango([...ANDA, { brazoL: -0.8, inclina: -0.18 }, { brazoL: 0.7, inclina: 0.32, fase: 0.6 }]);

  // ---------------------------------------------------------------- Necrófago: un Velador que la Podre vació, a cuatro patas
  // Encorvado como una araña: el espinazo arqueado se ve bajo la piel, la cabeza cuelga delante con la mandíbula
  // suelta y los brazos, larguísimos, tantean el suelo por delante. Jirones del velo le cuelgan de la cintura.
  for (let f = 0; f < 4; f++) perfil(`necrofago_${f}`, 52, 34, 22, (m, cz) => {
    const salta = f === 3, aga = f === 2, paso = f < 2 ? (f ? 1 : -1) : 0, y = salta ? 4 : aga ? -2.5 : 0, adel = salta ? 4 : 0;
    const cad = [12 + adel, 13 + y], hom = [27 + adel * 1.5, 15 + y], lomo = [19 + adel, 21 + y + (aga ? 1.5 : 0)];
    m.tubo([[cad[0], cad[1], cz], [lomo[0] - 3, lomo[1] - 1, cz], lomo, [hom[0], hom[1] + 1, cz]], 3, 3.2, P.pielGris);
    for (let k = 0; k < 9; k++) { const t = k / 8, x = cad[0] + (hom[0] - cad[0]) * t, yy = cad[1] + 2.6 + Math.sin(t * Math.PI) * (lomo[1] - cad[1] + 1); m.esfera([x, yy, cz], 1.1 - Math.abs(t - 0.5) * 0.6, O.hueso); }
    for (let k = 0; k < 4; k++) { const x = 15.6 + adel + k * 2.6; for (const s of [-1, 1]) m.tubo([[x, 18 + y - Math.abs(k - 1.5) * 0.4, cz + s * 1.4], [x + 0.6, 15 + y, cz + s * 3.2], [x + 1, 11.6 + y, cz + s * 2.4]], 0.45, 0.3, OSC.carneMuerta); }
    // cabeza colgando delante de los hombros
    const hx = hom[0] + 5, hy = hom[1] - 4 - (aga ? 1.5 : 0);
    m.capsula([hom[0], hom[1], cz], [hx - 1, hy + 1.5, cz], 1.6, 1.2, P.pielGris);
    m.esfera([hx, hy, cz], 3.2, P.pielGris);
    m.esfera([hx + 0.6, hy + 1.6, cz], 2.4, O.hueso, { modo: 'pintar' });
    m.con({ rot: ['z', -0.35 - (aga ? 0.25 : 0), [hx - 1, hy - 1.6, cz]] }, () => {
      m.caja([hx - 1, hy - 7, cz - 1.6], [hx + 2.6, hy - 2.4, cz + 1.6], O.hueso, { r: 0.6 });
      for (let k = 0; k < 4; k++) m.capsula([hx + 2.2, hy - 6.4 + k * 1.1, cz + 1.2], [hx + 3.4, hy - 6 + k * 1.1, cz + 1.4], 0.35, 0.1, O.diente);
    });
    for (let k = 0; k < 4; k++) m.capsula([hx + 1 + k * 0.6, hy - 2, cz + 1.6], [hx + 1.2 + k * 0.6, hy - 3.2, cz + 1.8], 0.32, 0.1, O.diente);
    m.esfera([hx + 1.4, hy + 0.6, cz + 2.4], 1.1, O.hueco, { modo: 'pintar' }); m.esfera([hx + 1.6, hy + 0.6, cz + 2.9], 0.45, O.ojoPalido);
    // brazos: hombro → codo alto → mano en el suelo por delante (dedos largos)
    for (const [dz, s] of [[-2.6, -1], [2.6, 1]]) {
      const a = paso * s, mano = salta ? [hom[0] + 16, 2, cz + dz * 1.4] : [hom[0] + 9 + a * 3, 0.4, cz + dz * 1.4], codo = salta ? [hom[0] + 8, hom[1] + 2, cz + dz * 1.3] : [hom[0] + 3 + a * 1.5, hom[1] + 5, cz + dz * 1.3];
      m.tubo([[hom[0], hom[1], cz + dz], codo, mano], 1.2, 0.7, P.pielGris);
      for (const d of [-1, 0, 1]) m.capsula(mano, [mano[0] + 3.4, mano[1] - (salta ? 1 : 0.2), mano[2] + d * 0.8], 0.4, 0.12, O.hueso);
      // piernas dobladas detrás
      const pie = salta ? [cad[0] - 9, 3, cz + dz] : [cad[0] - 4 - a * 2, 0.4, cz + dz], rod = salta ? [cad[0] - 4, cad[1] - 2, cz + dz] : [cad[0] + 1 - a, cad[1] + 3, cz + dz];
      m.tubo([[cad[0], cad[1], cz + dz * 0.8], rod, pie], 1.6, 0.9, P.pielGris);
      m.capsula(pie, [pie[0] + 2.6, pie[1] - 0.2, pie[2]], 0.6, 0.3, O.hueso);
    }
    // jirones del velo colgando de la cintura
    for (let k = 0; k < 4; k++) { const x = cad[0] - 2 + k * 2.4; m.capsula([x, cad[1] + 1, cz + (k % 2 ? 2.6 : -2.6)], [x - 1.6, cad[1] - 6 - (k % 2) * 2, cz + (k % 2 ? 3 : -3)], 1.1, 0.4, P.velo); }
    podre(m, [lomo[0] - 2, lomo[1] + 1.6, cz + 1.6], 1.6, 351);
  }, { cx: 22 });

  // ---------------------------------------------------------------- Velado: un Velador que no se fue del todo. Flota.
  // Encorvado como quien llora, sin pies: la túnica acaba en jirones. El velo le cae delante de la cara y, debajo,
  // apenas dos puntos de luz fría. Brazos larguísimos que se alargan hacia ti.
  const pielVelado = mat(['#121a26', '#1c2836', '#283a4c', '#384e64', '#4c647c', '#667e96', '#8aa2b6'], { luz: 0.22, brillo: 0.3, tex: (x, y, z) => (Math.sin(z * 1.5 + x * 0.7) > 0.55 ? -0.9 : 0) });
  for (let f = 0; f < 4; f++) perfil(`velado_${f}`, 44, 52, 22, (m, cz) => {
    const sube = f === 2 ? 2.4 : 0, ade = f === 3 ? 4 : 0, ond = f === 1 ? 1 : f === 0 ? -1 : 0, inc = f === 3 ? 0.35 : 0.18;
    m.con({ rot: ['z', -inc, [18, 14 + sube, cz]] }, () => {
      // la túnica: un cono que se abre hacia abajo y acaba en jirones de distinto largo
      m.figura((x, y, z) => { const t = (40 + sube - y) / 30, r = 3.4 + t * 5.4 + Math.sin(y * 0.7 + ond) * 0.25; return Math.max(Math.hypot(x - 18 - ade - t * ond * 1.4, (z - cz) * 1.15) - r, 12 + sube - y, y - 40 - sube); }, [6, 10, cz - 10, 32, 42, cz + 10], pielVelado);
      for (let k = 0; k < 9; k++) { const a = (k / 9) * Math.PI * 2, r = 8.4, x = 18 + ade + Math.cos(a) * r * 0.9, z = cz + Math.sin(a) * r * 0.85, l = 4 + ((k * 7) % 5) * 1.6 + (f === 2 ? 1 : 0); m.capsula([x, 13 + sube, z], [x - ond * 0.8 - 1, 13 + sube - l, z], 1.2, 0.3, pielVelado); }
      // la cabeza agachada bajo el velo
      const hx = 23 + ade, hy = 41 + sube;
      m.esfera([hx, hy, cz], 3.4, P.velo);
      m.caja([hx + 0.6, hy - 4.6, cz - 1.4], [hx + 3, hy - 3, cz + 1.4], O.hueso, { r: 0.4 }); // la mandíbula, lo único que asoma
      m.figura(OP.corta(SDF.elipsoide(hx - 0.6, hy - 0.4, cz, 4.8, 5.6, 4.6), (x, y) => Math.max(-(x - hx - 3.6) - (hy + 4 - y) * 0.7, y - hy - 6)), [hx - 7, hy - 10, cz - 6, hx + 6, hy + 6, cz + 6], P.velo, { ruido: [0.25, 0.8, 353] });
      m.capsula([hx + 2.6, hy - 1, cz + 2], [hx + 4, hy - 7, cz + 2.4], 1.4, 0.6, P.velo); // el velo cae por delante
      for (const dz of [1.4, 3]) m.esfera([hx + 2.4, hy + 0.4, cz + dz * 0.7], 0.45, P.almaFria);
      // brazos larguísimos
      const brazo = f === 2 ? 1.7 : f === 3 ? 0.55 : 0.25 + ond * 0.08;
      for (const [dz, k] of [[-3.6, 0.8], [3.6, 1]]) m.con({ rot: ['z', brazo * k, [20 + ade, 35 + sube, cz + dz]] }, () => {
        m.tubo([[20 + ade, 35 + sube, cz + dz], [24 + ade, 28 + sube, cz + dz], [30 + ade, 24 + sube, cz + dz]], 1.3, 0.55, pielVelado);
        for (const d of [-1, 0, 1]) m.capsula([30 + ade, 24 + sube, cz + dz], [34.6 + ade, 22 + sube + d * 0.9, cz + dz + d * 0.5], 0.35, 0.1, O.hueso);
      });
    });
  }, { cx: 19 });

  // ---------------------------------------------------------------- Guardián de piedra: un Velador de piedra con espada.
  // Cuadro 0: dormido, en pose de estatua (las manos en el pomo, la punta en el suelo); 1 anda con la espada baja;
  // 2 la alza por encima de la cabeza; 3 descarga el golpe. La capucha velada tapa la cara: dentro, luz fría.
  const piedraG = mat(['#101216', '#1b1e24', '#282c34', '#363b44', '#464c56', '#5a616c', '#727a86'], { tex: ESCU.TEX.piedra(421, 2.2) });
  const espadaG = (m, x, y, z, ang, vivo) => m.con({ rot: ['z', ang, [x, y, z]] }, () => {
    m.capsula([x, y + 3, z], [x, y - 1, z], 0.8, P.hierroViejo); m.esfera([x, y + 3.6, z], 1, piedraG);
    m.caja([x - 0.8, y - 1.8, z - 3.4], [x + 0.8, y - 0.6, z + 3.4], piedraG, { r: 0.3 });
    m.figura(SDF.caja(x - 1.5, y - 22, z - 0.6, x + 1.5, y - 1.8, z + 0.6, 0.3), [x - 3, y - 24, z - 2, x + 3, y - 1, z + 2], piedraG);
    if (vivo) m.capsula([x, y - 3, z + 0.7], [x, y - 20, z + 0.7], 0.3, P.grieta, { modo: 'pintar' });
  });
  for (let f = 0; f < 4; f++) perfil(`guardian_piedra_${f}`, 50, 60, 24, (m, cz) => {
    const vivo = f > 0, paso = f === 1 ? 1 : 0;
    for (const [dz, s] of [[-2.8, -1], [2.8, 1]]) m.con({ rot: ['z', paso * s * 0.25, [17, 14, cz + dz]] }, () => { m.capsula([17, 14, cz + dz], [18, 3, cz + dz], 2.6, 2.2, piedraG); m.caja([15.6, 0, cz + dz - 2.2], [22, 2, cz + dz + 2.2], piedraG, { r: 0.4 }); });
    m.figura((x, y, z) => { const t = (y - 8) / 26, r = 5.4 - t * 1.4 + Math.max(0, 0.2 - t) * 5; return Math.max(Math.hypot(x - 18, (z - cz) * 1.1) - r - Math.sin(Math.atan2(z - cz, x - 18) * 8) * 0.45, 8 - y, y - 34); }, [10, 6, cz - 8, 26, 35, cz + 8], piedraG); // túnica
    m.caja([12, 30, cz - 6.4], [24, 36, cz + 6.4], piedraG, { r: 1.6 }); // hombros
    // capucha velada: cono hacia arriba, cara vacía
    m.figura(OP.corta((x, y, z) => { const t = (46 - y) / 12, r = 3 + t * 5.2; return Math.hypot(x - 18.6, (z - cz) * 1.05) - r; }, (x, y) => Math.max(y - 47, 34 - y)), [9, 33, cz - 10, 28, 48, cz + 10], piedraG);
    m.quitar(() => m.figura(SDF.elipsoide(23.6, 40, cz, 2.6, 3.6, 3), [20, 35, cz - 4, 27, 45, cz + 4], null));
    m.figura(SDF.elipsoide(22.4, 40, cz, 1.6, 3.4, 2.6), [20, 36, cz - 3, 25, 44, cz + 3], O.hueco);
    m.capsula([23, 42, cz + 1], [23, 38, cz + 1], vivo ? 0.45 : 0.3, vivo ? P.almaFria : O.hueco);
    if (vivo) for (const [a, b] of [[[15, 26, cz + 5.4], [17, 18, cz + 5.2]], [[20, 31, cz + 5.6], [22, 24, cz + 5]], [[17, 12, cz + 5.6], [19, 9, cz + 5.4]]]) m.capsula(a, b, 0.35, P.grieta, { modo: 'pintar' });
    // brazos y espada
    if (f === 0) { m.tubo([[19, 33, cz + 4], [22, 27, cz + 4], [24, 23, cz + 1]], 1.9, 1.5, piedraG); m.tubo([[19, 33, cz - 4], [22, 27, cz - 4], [24, 23, cz - 1]], 1.9, 1.5, piedraG); espadaG(m, 25, 23, cz, 0, false); }
    else if (f === 1) { m.tubo([[19, 33, cz + 4], [23, 26, cz + 4], [26, 21, cz + 2]], 1.9, 1.5, piedraG); espadaG(m, 26.6, 21, cz + 2, 0.7, true); m.tubo([[19, 33, cz - 4], [21, 26, cz - 4], [20, 20, cz - 4]], 1.8, 1.4, piedraG); }
    else if (f === 2) { m.tubo([[19, 33, cz + 4], [16, 40, cz + 4], [14, 46, cz + 1]], 1.9, 1.5, piedraG); m.tubo([[19, 33, cz - 4], [16, 40, cz - 4], [14, 46, cz - 1]], 1.9, 1.5, piedraG); espadaG(m, 14, 46, cz, 2.6, true); }
    else { m.tubo([[19, 33, cz + 4], [26, 30, cz + 4], [32, 25, cz + 1]], 1.9, 1.5, piedraG); m.tubo([[19, 33, cz - 4], [26, 30, cz - 4], [32, 25, cz - 1]], 1.9, 1.5, piedraG); espadaG(m, 32.6, 25, cz, 1.25, true); }
    if (vivo) podre(m, [14, 30, cz - 3], 1.6, 423);
  }, { cx: 19 });

  // ---------------------------------------------------------------- Velador caído: esqueleto con túnica y alabarda (la Cripta)
  const alabarda = (m, [x, y, z], e) => {
    m.capsula([x - 6 * e, y - 9 * e, z], [x + 9 * e, y + 15 * e, z], 0.55 * e, M.maderaOsc);
    m.figura(OP.corta(SDF.elipsoide(x + 9 * e, y + 13 * e, z, 3.6 * e, 2.4 * e, 0.6 * e), (px) => x + 8 * e - px), [x + 7 * e, y + 10 * e, z - 2, x + 14 * e, y + 16 * e, z + 2], P.hierroViejo);
    m.capsula([x + 9 * e, y + 15 * e, z], [x + 10.4 * e, y + 18 * e, z], 0.6 * e, 0.1, P.hierroViejo);
  };
  const craneoVelo = (m, x, y, cz, e) => {
    m.esfera([x, y, cz], 3.3 * e, O.hueso);
    m.caja([x - 0.4 * e, y - 4.4 * e, cz - 1.6 * e], [x + 3.2 * e, y - 2.6 * e, cz + 1.6 * e], O.hueso, { r: 0.5 });
    m.esfera([x + 1.2 * e, y + 0.4 * e, cz + 2.4 * e], 1.3 * e, O.hueco, { modo: 'pintar' }); m.esfera([x + 1.4 * e, y + 0.4 * e, cz + 2.9 * e], 0.45, P.almaFria);
    m.figura(OP.corta(SDF.elipsoide(x - 1, y + 0.6 * e, cz, 4.4 * e, 4.6 * e, 4.2 * e), (px, py) => Math.max(px - x - 0.6 * e, py - y - 6 * e)), [x - 7 * e, y - 6 * e, cz - 6 * e, x + 2 * e, y + 6 * e, cz + 6 * e], P.velo, { ruido: [0.3, 0.8, 357] });
  };
  const tunicaCaida = (m, cz) => { m.figura(OP.corta(SDF.elipsoide(15, 11, cz, 6, 10, 5.2), OP.bajo(17)), [8, 0, cz - 7, 22, 18, cz + 7], P.velo, { ruido: [0.4, 1, 359] }); for (let k = 0; k < 5; k++) m.capsula([11 + k * 2, 3, cz + (k % 2 ? 4 : -4)], [10.6 + k * 2, -0.6, cz + (k % 2 ? 4.4 : -4.4)], 0.8, 0.3, P.velo); };
  [...ANDA, { brazoL: -0.8, inclina: -0.18 }, { brazoL: 0.7, inclina: 0.32, fase: 0.6 }].forEach((p, f) => perfil(`velador_caido_${f}`, 46, 44, 18, (m, cz) => grutoPerfil(m, cz, Object.assign({ piel: O.hueso, torso: P.velo, manga: P.velo, armaL: alabarda, cabeza: craneoVelo, sinVertebras: true, cadera: tunicaCaida }, p)), { cx: 18 }));

  // ---------------------------------------------------------------- GRUL, el Sapo Hueco (de frente)
  // Un saco de carne muerta que no deja de comer. La espalda es un bosque de Podre; por la boca, de lado a lado,
  // asoma lo último que se tragó (un Velador). Ojos en racimo, pequeños y rojos. Costillas ajenas bajo la piel.
  // 0 quieto, 1 boca abierta (lengua y escupitajo), 2 agachado (va a saltar), 3 en el aire (las patas cuelgan)
  const J = MAT_JEFE;
  const pielGrul = mat(['#060706', '#0d100c', '#151a13', '#1f261b', '#2a3324', '#36412e', '#44503a'], { tex: (x, y, z) => (ESCU.ruido(x * 0.7, y * 0.7, z * 0.7, 1.4, 391) > 0.64 ? -1.6 : 0) + (ESCU.ruido(x, y, z, 3, 392) - 0.5) * 0.6 });
  const vientreGrul = mat(['#0e0b08', '#18130e', '#221b14', '#2e241a', '#3a2e22', '#48382a'], { tex: (x, y) => (y % 3 === 0 ? -0.8 : 0) });
  const babaGrul = mat(['#2a3a14', '#46601c', '#6a8a2a', '#98b84a', '#c8e078'], { brillo: 1, luz: 0.3 });
  for (let f = 0; f < 4; f++) frontal(`grul_${f}`, 108, 96, 74, (m, cx, cz) => {
    const aire = f === 3, aga = f === 2, abre = f === 1 ? 1 : 0.18;
    const y = aire ? 14 : 0, ach = aga ? 0.78 : aire ? 1.12 : 1;
    m.con({ mover: [cx, 0, cz] }, () => {
      // patas: las de delante abiertas con dedos huesudos; en el aire cuelgan
      for (const l of [-1, 1]) {
        const pie = aire ? [l * 22, y - 12, 18] : aga ? [l * 30, 0.6, 22] : [l * 26, 0.6, 24];
        m.tubo([[l * 18, 16 * ach + y, 14], [l * (aga ? 30 : 26), (aga ? 10 : 12) + y * 0.6, 20], pie], 5, 3, pielGrul);
        for (const d of [-1.2, 0, 1.2]) m.tubo([pie, [pie[0] + l * 2 + d * 3, pie[1] + (aire ? -4 : 0.2), pie[2] + 5], [pie[0] + l * 3 + d * 4.4, pie[1] + (aire ? -7 : -0.2), pie[2] + 7]], 1.1, 0.4, O.hueso);
        const atras = aire ? [l * 34, y - 6, -10] : [l * 40, aga ? 1 : 3, 4];
        m.elipsoide([l * 32, 14 * ach + y, -8], [11, 10 * ach, 14], pielGrul, { ruido: [0.8, 2, 393] });
        m.tubo([[l * 34, 10 + y, -2], [l * 42, aga ? 4 : 8, 2], atras], 4, 2.4, pielGrul);
        for (const d of [-1, 0, 1]) m.capsula(atras, [atras[0] + l * 3, atras[1] - (aire ? 4 : 0.2), atras[2] + 4 + d * 2], 0.9, 0.3, O.hueso);
      }
      // el saco: ancho, bajo, colgando por delante
      m.elipsoide([0, 24 * ach + y, -2], [34, 20 * ach, 28], pielGrul, { ruido: [1.4, 3, 395] });
      m.elipsoide([0, 15 * ach + y, 12], [26, 12 * ach, 16], vientreGrul, { ruido: [0.8, 2, 396] });
      // costillas de los que se tragó, marcándose bajo la piel del vientre
      for (let k = 0; k < 5; k++) m.tubo([[-14 + k * 7, 22 * ach + y, 24], [-12 + k * 7, 15 * ach + y, 27], [-14 + k * 7, 8 * ach + y, 24]], 0.9, 0.6, O.hueso);
      // la espalda: un bosque de Podre, con tallos torcidos que hacen la silueta
      const r = azar(397);
      for (let k = 0; k < 11; k++) { const a = r() * Math.PI, d = 6 + r() * 22; podre(m, [Math.cos(a) * d, (38 + r() * 6) * ach + y, -8 + Math.sin(a) * 10 - 4], 4 + r() * 3.4, 400 + k); }
      // costillas rotas de lo que se tragó, saliéndole por la espalda, torcidas hacia atrás
      for (let k = 0; k < 6; k++) {
        const x0 = -20 + k * 8 + (r() - 0.5) * 3, z0 = -6 - r() * 8, h = 12 + r() * 8, l = x0 < 0 ? -1 : 1;
        m.tubo([[x0, 36 * ach + y, z0], [x0 + l * 3, (36 + h * 0.6) * ach + y, z0 - 5], [x0 + l * 5, (36 + h) * ach + y, z0 - 12]], 1.9, 0.4, O.hueso);
      }
      for (let k = 0; k < 22; k++) { const a = r() * Math.PI * 2, b = r() * 1.2; m.esfera([Math.cos(a) * Math.sin(b + 0.3) * 32, (24 + Math.cos(b) * 19) * ach + y, -2 + Math.sin(a) * Math.sin(b + 0.3) * 26], 0.8 + r() * 0.7, O.poro); }
      // la cabeza: plana, metida en el saco; la boca de lado a lado
      const hy = 30 * ach + y, hz = 21;
      m.elipsoide([0, hy, hz], [25, 12 * ach, 13], pielGrul, { ruido: [0.6, 1.6, 398] });
      const alto = 2.6 + abre * 9;
      m.figura(OP.corta(SDF.elipsoide(0, hy - 4, hz + 6, 22, alto, 9), (x, yy, z) => -Math.abs(x) + 22 - Math.abs(yy - hy + 4) * 0.4), [-24, hy - 16, hz - 4, 24, hy + 8, hz + 16], O.hueco, { modo: 'quitar' });
      m.figura(SDF.elipsoide(0, hy - 4, hz + 1, 20, alto * 0.9, 6), [-22, hy - 16, hz - 6, 22, hy + 8, hz + 8], O.boca);
      // dientes de aguja, arriba y abajo, torcidos
      for (let k = 0; k < 16; k++) {
        const x = -19 + k * 2.55, z = hz + 10.4 - Math.abs(x) * 0.12, larga = (k % 3 === 0 ? 1.6 : 1) * (1 + abre * 0.5);
        m.capsula([x, hy - 4 + alto * 0.9, z], [x + (r() - 0.5), hy - 4 + alto * 0.9 - 2.4 * larga, z + 0.4], 0.6, 0.12, O.diente);
        m.capsula([x + 1.2, hy - 4 - alto * 0.9, z], [x + 1.2 + (r() - 0.5), hy - 4 - alto * 0.9 + 2.2 * larga, z + 0.4], 0.55, 0.12, O.diente);
      }
      // lo que se tragó: un brazo de Velador con su manga y una calavera velada, entre los dientes
      m.tubo([[6, hy - 4, hz + 2], [10, hy - 6 - abre * 3, hz + 10], [13, hy - 12 - abre * 4, hz + 14]], 1.2, 0.8, O.hueso);
      for (const d of [-1, 0, 1]) m.capsula([13, hy - 12 - abre * 4, hz + 14], [13.6 + d * 0.8, hy - 14.6 - abre * 4, hz + 15], 0.4, 0.12, O.hueso);
      m.capsula([5, hy - 3.6, hz + 3], [9, hy - 5.6 - abre * 2, hz + 8], 2, 1.6, P.velo);
      if (abre > 0.5) {
        m.esfera([-8, hy - 6, hz + 6], 3, O.hueso); m.esfera([-7, hy - 5.4, hz + 8.6], 0.9, O.hueco, { modo: 'pintar' }); m.esfera([-9.4, hy - 5.4, hz + 8.4], 0.9, O.hueco, { modo: 'pintar' });
        m.tubo([[0, hy - 7, hz + 2], [3, hy - 10, hz + 12], [-1, hy - 14, hz + 20]], 3, 2, J.estrella); // la lengua
      }
      for (const [x, l] of [[-15, 9], [-4, 7], [12, 10], [18, 6]]) m.capsula([x, hy - 4 - alto * 0.9, hz + 9.6], [x + 0.6, hy - 4 - alto * 0.9 - l * (0.5 + abre * 0.5), hz + 10.4], 0.5, 0.3, babaGrul); // babas
      // ojos: racimos de puntos rojos, hundidos encima de las comisuras
      for (const l of [-1, 1]) {
        m.esfera([l * 15, hy + 6 * ach, hz + 6], 4.4, pielGrul);
        for (const [dx, dy] of [[0, 0], [2.2, 1.4], [-1.6, 1.8], [1.2, -1.6], [-2.6, -0.6]]) { m.esfera([l * (15 + dx), hy + 6 * ach + dy, hz + 9.6], 1.2, O.hueco, { modo: 'pintar' }); m.esfera([l * (15 + dx), hy + 6 * ach + dy, hz + 10.2], 0.55, O.ojoRojo); }
      }
    });
  }, { cima: 0.6 });

  // ---------------------------------------------------------------- EL CENTINELA, el Que No Duerme (de frente)
  // Un caballero Velador de piedra, más alto que una casa. El velo de piedra le cae de la cabeza a los hombros y
  // tapa la cara: dentro no hay nada, solo una rendija de luz fría. Espadón y lámpara.
  // 0 dormido (la pose de estatua: las manos en el pomo, la punta en el suelo), 1 alza la espada, 2 la descarga,
  // 3 alza la lámpara (runas y Velados)
  const piedraC = mat(['#0e1014', '#191c22', '#252930', '#333840', '#434952', '#565d68', '#6c7480', '#868e9a'], { tex: ESCU.TEX.piedra(411, 2.4) });
  const veloC = mat(['#0e1014', '#191c22', '#252930', '#333840', '#434952', '#565d68', '#6c7480'], { tex: (x, y, z) => (Math.sin(Math.atan2(z, x) * 11) > 0.55 ? -1.2 : 0) });
  const hoja = mat(['#141820', '#222832', '#323a46', '#46505e', '#5e6a7a', '#7a8698', '#a0acbc'], { brillo: 0.6 });
  const espadon = (m, x, y, z, ang, vivo, eje = 'x') => m.con({ rot: [eje, ang, [x, y, z]] }, () => { // la espada: empuñadura en (x,y,z) y la hoja hacia -y (local)
    m.capsula([x, y + 6, z], [x, y - 1, z], 1.3, 1, P.hierroViejo);
    m.esfera([x, y + 7, z], 1.8, piedraC);
    m.caja([x - 6, y - 2.4, z - 1.2], [x + 6, y - 0.4, z + 1.2], piedraC, { r: 0.6 }); // guarda
    m.figura(SDF.caja(x - 2.6, y - 40, z - 0.9, x + 2.6, y - 2.4, z + 0.9, 0.4), [x - 4, y - 44, z - 2, x + 4, y - 1, z + 2], hoja);
    m.figura(SDF.capsula(x, y - 40, z, x, y - 44, z, 2.4, 0.2), [x - 3, y - 46, z - 2, x + 3, y - 38, z + 2], hoja);
    m.capsula([x, y - 4, z + 1], [x, y - 38, z + 1], 0.35, vivo ? P.grieta : P.hierroViejo, { modo: 'pintar' });
  });
  for (let f = 0; f < 4; f++) frontal(`centinela_${f}`, 96, 124, 60, (m, cx, cz) => {
    const vivo = f > 0, G = vivo ? P.grieta : O.hueco;
    m.con({ mover: [cx, 0, cz] }, () => {
      m.figura(OP.resta(SDF.caja(-22, 0, -16, 22, 6, 16, 0.8), SDF.caja(14, 2, 8, 24, 8, 18)), [-23, 0, -17, 23, 7, 17], piedraC); // pedestal roto
      for (const l of [-1, 1]) { m.capsula([l * 8, 34, 0], [l * 9, 12, 2], 5.4, 4.6, piedraC); m.caja([l * 9 - 5.4, 6, -3], [l * 9 + 5.4, 10, 9], piedraC, { r: 1 }); }
      // la túnica con pliegues hondos hasta las rodillas, y la sobreveste
      m.figura((x, y, z) => { const t = (y - 18) / 48, r = 14 - t * 3.6 + Math.max(0, 0.15 - t) * 14; return Math.max(Math.hypot(x * 0.92, z * 1.3) - r - Math.sin(Math.atan2(z, x) * 10) * 0.9, 18 - y, y - 66); }, [-20, 16, -15, 20, 67, 15], piedraC);
      m.figura(SDF.caja(-7, 22, 10, 7, 62, 13, 1), [-8, 21, 9, 8, 63, 14], veloC); // la sobreveste con el velo bordado
      m.pintar(() => { m.elipsoide([0, 50, 13.4], [3.4, 4.2, 1], piedraC); m.capsula([-3, 47, 13.6], [-3.6, 42, 13.6], 0.5, piedraC); m.capsula([3, 47, 13.6], [3.6, 42, 13.6], 0.5, piedraC); });
      // hombros enormes
      for (const l of [-1, 1]) { m.esfera([l * 17, 62, 0], 8, piedraC); m.figura(OP.corta(SDF.esfera(l * 17, 63, 0, 9.4), OP.sobre(60)), [l * 17 - 10, 59, -10, l * 17 + 10, 73, 10], piedraC); }
      m.caja([-14, 58, -10], [14, 66, 10], piedraC, { r: 2 });
      // la cabeza bajo el velo: una cúpula alta, el velo cae en pliegues hasta los hombros; la cara es un hueco
      m.figura(SDF.elipsoide(0, 74, 1, 7.4, 10, 7.4), [-9, 63, -8, 9, 85, 10], piedraC);
      m.figura(OP.corta((x, y, z) => { const t = (84 - y) / 20, r = 6.4 + t * 9; return Math.hypot(x, (z - 1) * 1.08) - r + Math.sin(Math.atan2(z - 1, x) * 9) * 0.8 * t; }, (x, y) => Math.max(y - 85, 64 - y)), [-17, 63, -16, 17, 86, 18], veloC);
      m.quitar(() => m.figura(SDF.elipsoide(0, 74, 9.4, 4.4, 6.2, 4), [-6, 66, 5, 6, 82, 14], null));
      m.figura(SDF.elipsoide(0, 74, 6.4, 4.2, 6, 2), [-6, 67, 4, 6, 81, 9], O.hueco);
      m.capsula([0, 77, 7.6], [0, 70, 7.6], vivo ? 0.6 : 0.4, vivo ? P.almaFria : O.hueco); // la rendija de luz
      // grietas
      for (const [a, b] of [[[-7, 56, 13.6], [-3, 44, 14.8]], [[5, 52, 14], [10, 38, 13]], [[-10, 32, 13], [-6, 24, 14]], [[3, 80, 7], [6, 71, 8]], [[-14, 64, 8], [-17, 56, 8]]]) m.capsula(a, b, 0.6, G, { modo: 'pintar' });
      // los brazos: según el cuadro
      if (f === 0) { // las dos manos en el pomo; la punta en el suelo, delante
        for (const l of [-1, 1]) m.tubo([[l * 17, 60, 2], [l * 13, 48, 10], [l * 3, 40, 16]], 4.4, 3.4, piedraC);
        espadon(m, 0, 40, 18, 0, false);
      } else if (f === 1) { // la espada en alto, por encima de la cabeza
        m.tubo([[17, 60, 2], [20, 72, 6], [12, 86, 6]], 4.4, 3.4, piedraC); m.tubo([[-17, 60, 2], [-14, 74, 6], [8, 86, 6]], 4.2, 3.2, piedraC);
        espadon(m, 10, 86, 6, Math.PI * 0.92, true);
      } else if (f === 2) { // descargada: la hoja barre hacia delante, casi en el suelo
        m.tubo([[17, 60, 2], [24, 48, 10], [26, 40, 16]], 4.4, 3.4, piedraC); m.tubo([[-17, 60, 2], [-4, 46, 12], [22, 40, 16]], 4.2, 3.2, piedraC);
        espadon(m, 24, 40, 16, 1.95, true, 'z');
      } else { // la lámpara en alto con la izquierda; la espada cuelga de la derecha
        m.tubo([[17, 60, 2], [20, 46, 6], [19, 34, 10]], 4.4, 3.4, piedraC);
        espadon(m, 19, 34, 10, 0.12, true);
        m.tubo([[-17, 60, 2], [-22, 74, 6], [-20, 88, 8]], 4.2, 3.2, piedraC);
        m.capsula([-20, 88, 8], [-20, 92, 8], 0.5, P.hierroViejo);
        m.figura(SDF.elipsoide(-20, 97, 8, 3.8, 4.6, 3.8), [-25, 91, 3, -15, 103, 13], P.almaFria);
        for (const a of [0, 1.57, 3.14, 4.71]) m.capsula([-20 + Math.cos(a) * 4, 102, 8 + Math.sin(a) * 4], [-20 + Math.cos(a) * 4, 92, 8 + Math.sin(a) * 4], 0.45, P.hierroViejo);
        m.capsula([-20, 102, 8], [-20, 104, 8], 1.4, P.hierroViejo);
      }
      if (vivo) { podre(m, [-10, 46, -12], 4.4, 415); podre(m, [14, 66, -6], 3, 416); }
      m.elipsoide([-15, 6, 13], [4, 1.4, 2], M.musgo); m.elipsoide([12, 4, -14], [5, 1.6, 2.4], M.musgo);
    });
  }, { cima: 0.55 });
});

// ---------------------------------------------------------------- estaciones de médula y plata, e iconos de lo nuevo
CONSTRUIR_MODELOS.push(function estacionesEIconosBandas() {
  const { mat, rampa, SDF } = ESCU, M = MAT, O = OSC;
  const hueso = mat(['#3a3226', '#5e5240', '#867860', '#ae9e80', '#d0c2a2', '#ece0c4', '#fff8ea'], { brillo: 0.3 });
  const tuetano = mat(['#3a0606', '#6a0c0c', '#9a1a16', '#c8302a', '#e85a48'], { luz: 0.4 });
  const muerta = mat(['#0a0909', '#161313', '#221e1c', '#302a26', '#403832', '#524840', '#665a50'], { tex: ESCU.TEX.tablas('x', 3, 381) });
  const plata = mat(['#1e242e', '#323a48', '#4a5466', '#68748a', '#8e9ab0', '#b8c4d8', '#e4ecfa'], { brillo: 1 });
  const velada = mat(['#121418', '#1e2128', '#2c3038', '#3c424e', '#4e5562', '#646c7a', '#7e8696'], { tex: ESCU.TEX.piedra(383, 3) });
  const alma = mat(['#1a3a6a', '#2a5a9a', '#4a86cc', '#7ab2f0', '#b4dcff', '#e8f6ff', '#ffffff'], { luz: 1, plano: true, sube: 2 });
  // mesa de médula: patas de hueso, tablero de madera muerta, un cráneo y frascos de tuétano
  modelo('mesa_medula', 18, 16, 14, (m) => {
    for (const [x, z] of [[2.5, 2.5], [15.5, 2.5], [2.5, 11.5], [15.5, 11.5]]) m.capsula([x, 0, z], [x, 8, z], 1.2, 0.9, hueso);
    m.caja([1, 8, 1], [17, 10, 13], muerta, { r: 0.5 });
    m.esfera([5, 12, 6], 2.4, O.hueso); m.esfera([5.8, 12.2, 8.2], 0.6, O.hueco, { modo: 'pintar' }); m.esfera([4.2, 12.2, 8.2], 0.6, O.hueco, { modo: 'pintar' });
    for (const [x, z] of [[11, 4], [13.5, 5], [12, 9]]) { m.cilindro([x, 10, z], 1.1, 2.6, M.vidrio); m.cilindro([x, 10.2, z], 0.8, 1.8, tuetano); }
    m.capsula([8, 10.6, 11], [15, 10.6, 11.6], 0.7, hueso);
  });
  modelo('yunque_medula', 18, 16, 14, (m) => { // una vértebra del dios sobre un tocón
    m.cilindro([9, 0, 8], 4.6, 5, muerta, { r1: 4.2 });
    m.elipsoide([9, 8, 8], [6.6, 3, 4.6], hueso);
    for (const l of [-1, 1]) m.capsula([9 + l * 3, 9, 8], [9 + l * 8, 11, 8], 1.3, 0.6, hueso);
    m.capsula([9, 10, 8], [9, 13, 7], 1.4, 0.7, hueso);
    m.esfera([9, 8.6, 12.4], 1, tuetano);
  });
  modelo('mesa_plata', 20, 22, 14, (m) => { // la forja velada: un altar de piedra con plata y una luz fría
    m.caja([1, 0, 1], [19, 9, 13], velada, { r: 0.6 });
    m.caja([0.5, 9, 0.5], [19.5, 10.4, 13.5], plata, { r: 0.4 });
    m.cilindro([10, 10.4, 5], 2.4, 1.2, plata);
    m.figura(SDF.elipsoide(10, 14, 5, 2.2, 2.6, 2.2), [7, 11, 2, 13, 17, 8], alma);
    for (const a of [0, 1.57, 3.14, 4.71]) m.capsula([10 + Math.cos(a) * 2.6, 11, 5 + Math.sin(a) * 2.6], [10 + Math.cos(a) * 2.2, 17, 5 + Math.sin(a) * 2.2], 0.35, plata);
    m.caja([3, 10.4, 8], [8, 11.4, 12], M.papel); m.caja([13, 10.4, 9], [17, 12, 12], plata, { r: 0.3 });
  });
  modelo('yunque_plata', 18, 14, 14, (m) => {
    m.cilindro([9, 0, 8], 4.6, 5, velada, { r1: 4.2 });
    m.caja([6, 5.5, 5.5], [12, 8, 10.5], plata, { r: 0.6 });
    m.caja([3.5, 8, 4.5], [15, 11, 11.5], plata, { r: 0.6 });
    m.figura(SDF.capsula(15, 10, 8, 18, 10.2, 8, 2, 0.4), [12, 7, 5, 18, 13, 11], plata);
    m.pintar(() => m.elipsoide([9, 9.5, 11.6], [1.6, 1.2, 0.4], alma));
  });

  // ---- iconos
  const C = (L) => L.contorno();
  for (const [id, sp] of [['mesa_medula', 'mesa_medula'], ['yunque_medula', 'yunque_medula'], ['mesa_plata', 'mesa_plata'], ['yunque_plata', 'yunque_plata'], ['farol_cebo', 'farol_pantano'], ['farola', 'farola_velada'], ['banco', 'banco_piedra'], ['estatua_decor', 'estatua_velador']]) {
    const L = dibujoHecho(sp); if (L) def('i_' + id, reducir(L, 15, 15));
  }
  const MANGO = { h: '#4c443c', p: '#2a2420' };
  const T = {
    medula: { o: '#5a4a38', b: '#c8b896', c: '#f2ead6', w: '#fff8ec', x: '#c8302a' },
    plata: { o: '#3a4456', b: '#9aa8c0', c: '#dce6f6', w: '#ffffff', x: '#7ab2f0' },
  };
  for (const [m, P] of Object.entries(T)) {
    def(`i_pico_${m}`, C(rej({ J: P.w, H: P.b, D: P.o, X: P.x, ...MANGO }, `
      ..JJJH....
      .HHDDHH...
      .D...hHH..
      ....h.DH..
      ...h...DX.
      ..h.....D.
      .h........
      p.........`)));
    def(`i_espada_${m}`, C(rej({ J: P.w, H: P.b, D: P.o, G: P.x, g: P.o, ...MANGO }, `
      ........JJ
      .......JHH
      ......JHD.
      .....JHD..
      ....JHD...
      .G.JHD....
      ..GHD.....
      ..hGG.....
      .h..G.....
      p.........`)));
    def(`i_arco_${m}`, C(rej({ o: P.o, b: P.b, c: P.c, s: m === 'plata' ? '#c0dcff' : '#e8d8b8' }, `
      ..bbc...
      .b...s..
      b.....s.
      b......s
      b......s
      b.....s.
      .b...s..
      ..bbo...`)));
    def(`i_flecha_${m}`, C(rej({ m: '#4c443c', p: P.b, f: '#e8e0d0' }, `
      ......pp
      .....mpp
      ....m...
      ...m....
      ..m.....
      ff......
      f.......`)));
    def(`i_casco_${m}`, C(rej({ o: P.o, b: P.b, c: P.c, x: P.x }, `
      x.ccbb.x
      .cbbbbo.
      cbbbbbbo
      bbbbbbbo
      boo..oob
      o......o`)));
    def(`i_peto_${m}`, C(rej({ o: P.o, b: P.b, c: P.c, x: P.x }, `
      .cb....bc.
      cbbb..bbbo
      bbcbxbbbbo
      .bcbbbbbo.
      .bbbbbbbo.
      .bbbbbboo.
      ..bbbboo..`)));
    def(`i_botas_${m}`, C(rej({ o: P.o, b: P.b, c: P.c }, `
      .cb..cb.
      .bb..bb.
      .bb..bb.
      cbbo.bbbc
      bbbo.bbbo`)));
  }
  def('i_maza_medula', C(rej({ o: '#5a4a38', b: '#c8b896', c: '#f2ead6', x: '#c8302a', ...MANGO }, `
    ...c.c..
    ..cbbbc.
    .cbbxbbo
    ..cbbbo.
    ...oho..
    ..h.....
    .h......
    p.......`)));
  def('i_lanza_plata', C(rej({ a: '#ffffff', b: '#9aa8c0', c: '#3a4456', d: '#2a2420', e: '#4c443c' }, `
    .........a
    ........ab
    .......cb.
    ......ec..
    .....ed...
    ....ed....
    ...ed.....
    ..ed......
    .ed.......
    d.........`)));
  def('i_lengua_grul', C(rej({ a: '#ff9ab0', b: '#c83c48', c: '#7a1a2a', g: '#74d028', h: '#3e4830' }, `
    ..bbbc....
    .b....c...
    b......c..
    b.......c.
    .c......ga
    ..cc...g..
    ....h.....
    ...h......
    ..h.......`)));
  def('i_espada_centinela', C(rej({ J: '#9aa2b0', H: '#646c7a', D: '#2c3038', G: '#7ab2f0', g: '#2a5a9a', ...MANGO }, `
    ........JJ
    .......JHH
    ......JGD.
    .....JHD..
    ....JGD...
    .g.JHD....
    ..gHD.....
    ..hgg.....
    .h..g.....
    p.........`)));
  def('i_anillo_medula', C(rej({ a: '#f2ead6', b: '#c8b896', c: '#5a4a38', g: '#e85a48', G: '#8a1420', w: '#ffffff' }, `
    ..wg..
    .gGGg.
    .abba.
    a....b
    b....c
    .bccc.`)));
  def('i_collar_farolero', C(rej({ a: '#c8b896', b: '#7a6a50', g: '#e0ff6a', G: '#a8d82a', w: '#ffffff' }, `
    a......a
    .b....b.
    ..b..b..
    ...wg...
    ...gG...
    ....G...`)));
  def('i_amuleto_velado', C(rej({ a: '#dce6f6', b: '#9aa8c0', g: '#b4dcff', G: '#4a86cc', w: '#ffffff' }, `
    a......a
    .b....b.
    ..b..b..
    ..bwgb..
    ..bgGb..
    ...bb...`)));
  def('i_velo_centinela', C(rej({ a: '#526074', b: '#3a4456', c: '#222836', g: '#b4dcff', w: '#ffffff' }, `
    ..aaaa..
    .abbbba.
    abwbbgba
    abbbbbba
    abbbbbba
    .abcbcba
    .c.c.c.c`)));
  def('i_cebo_luz', C(new Lienzo(8, 10).esfera(4, 6, 3.4, 3.4, ['#3a5a0a', '#6a9a14', '#a8d82a', '#e0ff6a'], { brillo: '#ffffff' }).vl(4, 0, 3, '#16161e')));
  def('i_esencia_velada', C(new Lienzo(9, 10).esfera(4.5, 6, 3.6, 3.6, ['#1a3a6a', '#2a5a9a', '#4a86cc', '#7ab2f0', '#b4dcff'], { brillo: '#ffffff' }).rect(3, 0, 3, 2, '#9aa8c0').hl(3, 2, 3, '#646c7a')));
  def('i_corazon_grul', C(new Lienzo(11, 10).esfera(5.5, 5, 4.8, 4.2, ['#2a0a14', '#5a1a2a', '#8a2a3a', '#b83c48'], { brillo: '#ff9ab0' }).px(3, 4, '#ff3cb4').px(7, 6, '#ff3cb4').px(6, 3, '#74d028')));
  def('i_llama_centinela', C(rej({ a: '#ffffff', b: '#b4dcff', c: '#7ab2f0', d: '#4a86cc', e: '#2a5a9a' }, `
    ...a...
    ..aba..
    ..bcb..
    .bccdb.
    .cddde.
    .cdede.
    ..eee..`)));
  def('i_carne_sanguijuela', C(new Lienzo(10, 7).esfera(5, 3.6, 4.6, 3, ['#0c1012', '#1e2622', '#3a4636', '#4e5a44'], { brillo: '#8a9a70' }).hl(2, 5, 6, '#646f52')));
});

// ---------------------------------------------------------------- los dos que todavía hablan (de frente)
CONSTRUIR_MODELOS.push(function personajesBandas() {
  const { mat, SDF, OP } = ESCU, O = OSC, M = MAT;
  const espectro = mat(['#141c28', '#1e2a3a', '#2c3c50', '#3e5268', '#566c84', '#7890a8', '#a0b8cc'], { luz: 0.3, tex: (x, y, z) => (Math.sin(Math.atan2(z, x) * 9) > 0.55 ? -0.8 : 0) });
  const alma = mat(['#1a3a6a', '#2a5a9a', '#4a86cc', '#7ab2f0', '#b4dcff', '#e8f6ff', '#ffffff'], { luz: 1, plano: true, sube: 2 });
  // el Archivero: un Velador sentado en su silla de piedra, el libro abierto en las rodillas y una lámpara de alma
  frontal('archivero', 34, 46, 26, (m, cx, cz) => m.con({ mover: [cx, 0, cz] }, () => {
    const piedra = mat(['#101216', '#1b1e24', '#282c34', '#363b44', '#464c56', '#5a616c'], { tex: ESCU.TEX.piedra(431, 2.4) });
    m.caja([-10, 0, -8], [10, 12, 6], piedra, { r: 0.8 }); m.caja([-10, 12, -9], [10, 30, -6], piedra, { r: 0.8 }); // la silla
    m.figura((x, y, z) => { const t = (y - 8) / 22, r = 8 - t * 3; return Math.max(Math.hypot(x * 0.95, (z + 1) * 1.2) - r - Math.sin(Math.atan2(z, x) * 8) * 0.5, 8 - y, y - 30); }, [-11, 6, -10, 11, 31, 10], espectro); // la túnica, sentada
    for (const l of [-1, 1]) m.capsula([l * 3.6, 12, 2], [l * 4, 4, 9], 2.6, 2, espectro);
    m.esfera([0, 33, 0], 4, espectro);
    m.figura(OP.corta(SDF.elipsoide(0, 32.6, 0, 5.4, 7, 5), OP.bajo(38)), [-7, 24, -6, 7, 39, 7], espectro, { ruido: [0.25, 0.8, 433] });
    for (const dx of [-1.6, 1.6]) m.esfera([dx, 32.6, 4.6], 0.6, alma);
    m.caja([-6, 13, 6], [6, 14.4, 12], M.papel, { r: 0.3 }); m.caja([-0.4, 13.2, 6], [0.4, 15, 12], M.cuero); // el libro
    for (const l of [-1, 1]) m.tubo([[l * 6, 26, 2], [l * 7, 18, 6], [l * 3, 15, 10]], 1.4, 1, espectro);
    m.capsula([12, 0, 2], [12, 22, 2], 0.6, M.hierro); m.figura(SDF.elipsoide(12, 25, 2, 2.4, 3, 2.4), [9, 21, -1, 15, 29, 5], alma); // la lámpara
  }), { cima: 0.6 });
  // Muda: una gruta sin máscara. Cara gris y flaca, ojos ámbar, capa de juncos y un farol de cebo (no reza: quema)
  frontal('muda', 30, 42, 22, (m, cx, cz) => m.con({ mover: [cx, 0, cz] }, () => {
    const piel = O.pielGruto, juncos = mat(['#141a0e', '#222c16', '#323e20', '#46542c', '#5a6a38'], { tex: (x, y) => (Math.round(x * 1.7) % 2 ? -0.7 : 0) });
    for (const l of [-1, 1]) { m.capsula([l * 3, 14, 0], [l * 3.4, 2, 1], 1.8, 1.4, piel); m.elipsoide([l * 3.4, 1, 3], [1.6, 1, 2.4], O.hueso); }
    m.figura((x, y, z) => { const t = (y - 6) / 22, r = 7 - t * 2.6; return Math.max(Math.hypot(x, z * 1.2) - r - Math.sin(Math.atan2(z, x) * 13) * 0.6, 6 - y, y - 28); }, [-9, 4, -8, 9, 29, 8], juncos); // capa de juncos
    m.esfera([0, 30, 1], 3.8, piel);
    m.elipsoide([0, 28.4, 3.6], [2.4, 1.6, 1.4], piel); // la cara, flaca, al aire
    for (const dx of [-1.4, 1.4]) { m.esfera([dx, 31, 4], 0.9, O.hueco, { modo: 'pintar' }); m.esfera([dx, 31, 4.5], 0.45, O.ojoAmbar); }
    for (const l of [-1, 1]) m.tubo([[l * 2.6, 32, -1], [l * 6, 35, -3], [l * 8.6, 34, -4]], 0.9, 0.3, piel); // orejas largas
    m.tubo([[5, 24, 2], [9, 18, 5], [10, 12, 6]], 1.1, 0.8, piel);
    m.capsula([10, 0, 6], [10, 34, 6], 0.6, M.maderaOsc);
    m.capsula([10, 34, 6], [13, 34, 6], 0.4, M.hierro); m.figura(SDF.elipsoide(13, 30.6, 6, 1.8, 2.2, 1.8), [10, 27, 3, 16, 34, 9], mat(['#3a5a0a', '#6a9a14', '#a8d82a', '#e0ff6a', '#f8ffc8'], { luz: 1, plano: true, sube: 2 }));
  }), { cima: 0.6 });
});

// ---------------------------------------------------------------- iconos rehechos (los que eran manchas o anillos)
CONSTRUIR_MODELOS.push(function iconosRehechos() {
  const C = (L) => L.contorno();
  // arcos de médula y de plata: palas curvas, cuerda y la empuñadura atada
  const arco = (b, c, s, x) => C(rej({ b, c, s, x }, `
    sbc.....
    s..b....
    s...c...
    s....b..
    s....xb.
    s....xb.
    s....b..
    s...c...
    s..b....
    sbc.....`));
  def('i_arco_medula', arco('#c8b896', '#f2ead6', '#e8e0cc', '#c8302a'));
  def('i_arco_plata', arco('#9aa8c0', '#dce6f6', '#b4dcff', '#4a86cc'));
  // los de antes también eran anillos
  def('i_arco', arco('#8a5a36', '#c28a52', '#e8e0cc', '#4c2e1a'));
  def('i_arco_madera', arco('#8a5a36', '#c28a52', '#e8e0cc', '#4c2e1a'));
  def('i_arco_estano', arco('#8a88a0', '#d8d6e8', '#ffffff', '#4a4860'));
  def('i_arco_carmesi', arco('#a01c2c', '#e04a5a', '#ffe0e0', '#4a0c14'));
  def('i_arco_tamar', arco('#b8861a', '#f2c14e', '#eef2fa', '#6a4a10'));
  // el corazón de Grul: un corazón de verdad, podrido, con la aorta y un bulto de la Podre
  def('i_corazon_grul', C(rej({ e: '#c86a6a', f: '#e8a0a0', b: '#4a0c18', c: '#8a1c2c', d: '#2e0810', w: '#e86a74', p: '#ff3cb4', q: '#5a1040', v: '#3a1a4a' }, `
    ....ef......
    ....efe.ee..
    ..bbefebfeb.
    .bcccecccbb.
    bccwccvcccbb
    bcwcccvccccb
    bcccccvqpccb
    .bcccvccqcb.
    .bccvccccdb.
    ..bcvcccdb..
    ...bccddb...
    ....bddb....
    .....bb.....`)));
  // espora de la Podre: una vaina negra con poros que brillan
  def('i_espora_podre', C(rej({ b: '#24142e', c: '#42204e', p: '#ff3cb4', q: '#c01a84', d: '#3a2a20', w: '#6a3a7a' }, `
    ...pq...
    ..cwbc..
    .cbbpbc.
    cbqbbbbc
    cbbbbqbc
    cbpbbbbc
    .cbbbpc.
    ..cbbc..
    ...dd...
    ...d....`)));
  // carne de sanguijuela: un trozo anillado con el corte rosado
  def('i_carne_sanguijuela', C(rej({ b: '#1e2622', c: '#3a4636', w: '#5a6a50', r: '#8a3a4a', s: '#c86a74' }, `
    ..cccccc..
    .cwbcwbcr.
    cwbbcbbcrs
    cbbbcbbcrs
    cbbbcbbcrs
    .cbbcbbcr.
    ..cccccc..`)));
  // ojo del Velador: un amuleto de plata con un ojo que no parpadea
  def('i_ojo_velador', C(rej({ a: '#dce6f6', b: '#9aa8c0', c: '#5a6476', w: '#ffffff', n: '#7ab2f0', g: '#0a1020', l: '#4a86cc' }, `
    ...cc...
    ..c..c..
    ..baab..
    .baaaab.
    babwwwab
    awnlgnwa
    awnggnwa
    babwwwab
    .baaaab.
    ..bccb..`)));
  // llama del Centinela: más grande, con el núcleo blanco
  def('i_llama_centinela', C(rej({ a: '#ffffff', b: '#d8f0ff', c: '#7ab2f0', d: '#4a86cc', e: '#2a5a9a' }, `
    ....a....
    ...ab....
    ...abc...
    ..cabc...
    ..cbacd..
    .cbaacd..
    .cbaabcd.
    .dcbbcdd.
    ..dccdd..
    ...eee...`)));
});
