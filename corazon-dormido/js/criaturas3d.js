'use strict';
// Criaturas y jefes en 3D según LORE.md: fantasía oscura. Siluetas encorvadas, ojos que son puntos de luz, carne
// enferma, hueso, y la marca de la Podre (bultos negros con poros magenta que brillan). Se hornean mirando a la
// derecha en tres cuartos; el juego los voltea al mirar a la izquierda.

const OSC = (() => {
  const { mat, rampa, TEX } = ESCU;
  const brilla = (cols) => mat(cols, { luz: 1, plano: true, sube: 2 });
  return {
    carneMuerta: mat(['#0c0e12', '#181c22', '#262c34', '#384048', '#4c565e', '#647078', '#808c90'], {}),
    pielGruto: mat(['#0c100c', '#182016', '#263222', '#384630', '#4c5c40', '#647452', '#80906a'], {}),
    pielPalida: mat(['#1e1418', '#34262a', '#4e3a3c', '#6c5452', '#8c726a', '#ae9488', '#cab4a6'], { tex: (x, y, z) => (ESCU.ruido(x * 1.4, y * 1.4, z * 1.4, 1.2, 107) > 0.7 ? -1.4 : 0) }),
    hueso: mat(['#2a2418', '#463c28', '#685c3e', '#8c7e58', '#ae9e74', '#cabc92', '#e2d6b0'], {}),
    harapo: mat(['#0c0a08', '#18130e', '#261e16', '#362a1e', '#483826', '#5a4630'], { tex: TEX.grumos(109, 1.2, 0.9) }),
    cuero: mat(['#0e0806', '#1c100a', '#2c1a10', '#3e2616', '#52341e', '#684428'], { tex: TEX.grumos(111, 2, 0.4) }),
    baba: mat(['#0e0e04', '#1e1e08', '#34340e', '#4e4e16', '#6a6a20', '#8a882c', '#aca63c', '#d0c862'], { brillo: 0.9, tex: TEX.grumos(113, 2.2, 0.5) }),
    babaSangre: mat(['#120406', '#26080c', '#3e0e14', '#5a141c', '#7a1c24', '#9c2a2e', '#be4440', '#dc6a5c'], { brillo: 0.9, tex: TEX.grumos(117, 2.2, 0.5) }),
    podre: mat(['#06040a', '#0e0814', '#180e20', '#24142e', '#32183e', '#42204e'], { tex: TEX.grumos(119, 1, 1) }),
    poro: mat(['#6a0a4a', '#c01a84', '#ff3cb4', '#ff7ad0'], { luz: 1, plano: true }),
    ojoRojo: mat(['#6a0a06', '#c01a0c', '#ff3a1a', '#ff6a3a'], { luz: 1, plano: true }),
    ojoAmbar: mat(['#7a3a06', '#d0780c', '#ffa424', '#ffc860'], { luz: 1, plano: true }),
    ojoPalido: mat(['#3a5a58', '#6a9a94', '#a8dcd2', '#d8fff6'], { luz: 1, plano: true }),
    hueco: mat(['#020103', '#060408', '#0a070c'], { plano: true }),
    boca: mat(['#100204', '#220408', '#3a080e', '#561018', '#701a22'], {}),
    diente: mat(['#4a4230', '#7a6e52', '#a89a78', '#ccc0a0', '#ece4cc'], { brillo: 0.3 }),
    hongoCap: mat(['#140c08', '#24160e', '#382218', '#4e3022', '#68422e', '#84583c', '#a07050'], { tex: (x, y, z) => (ESCU.ruido(x, y * 3, z, 2, 121) > 0.68 ? -1.2 : 0) }),
    laminas: mat(['#2a0a20', '#4a1238', '#6e1e52', '#9a2a6e', '#c63c8c'], { luz: 0.55, tex: (x, y, z) => ((Math.floor(Math.atan2(z, x) * 9) % 2) ? -1 : 0) }),
    petalo: mat(['#120206', '#24040c', '#3c0812', '#580e1a', '#781624', '#9a2230', '#bc3640', '#d8545a'], { tex: (x, y, z) => (ESCU.ruido(x * 0.4, y * 2, z * 0.4, 1.4, 127) > 0.7 ? -1.5 : 0) }),
    espina: mat(['#0e0806', '#1e140c', '#322216', '#4a3420', '#644a2e'], {}),
    vena: mat(['#040306', '#0c0810', '#160e1c', '#22162a', '#301e3a', '#40284c', '#52345e'], { tex: (x, y, z) => (ESCU.ruido(x * 2, y * 2, z * 2, 2, 131) > 0.65 ? -1 : 0) }),
    musgoOsc: mat(['#04080a', '#08100c', '#0e1a12', '#162618', '#20341e', '#2c4426', '#3a5630'], { tex: TEX.grumos(161, 1.2, 1) }),
    hojaSeca: mat(['#0a0806', '#16100a', '#241a0e', '#342614', '#46341a', '#5a4622', '#70582c'], { tex: (x, y, z) => ((x + y + z) % 3 === 0 ? -1 : 0) }),
    lanzaHueso: mat(['#2a2418', '#463c28', '#685c3e', '#8c7e58', '#ae9e74', '#cabc92', '#e2d6b0'], { brillo: 0.3 }),
    pluma: mat(rampa('#6a2a1e', 5), {}),
  };
})();

// Bultos de Podre: un racimo negro con poros que brillan, pegado a una superficie
function podre(m, [x, y, z], r, sem = 1) {
  const a = azar(sem);
  for (let k = 0; k < 3; k++) m.esfera([x + (a() - 0.5) * r, y + (a() - 0.5) * r * 0.6, z + (a() - 0.5) * r], r * (0.5 + a() * 0.35), OSC.podre, { ruido: [0.3, 1, sem + k] });
  for (let k = 0; k < 4; k++) m.esfera([x + (a() - 0.5) * r * 1.3, y + r * 0.35 + a() * r * 0.3, z + (a() - 0.3) * r * 1.2], 0.6 + a() * 0.3, OSC.poro);
}

// Modelado de perfil: x hacia delante (a la derecha), y arriba, z hacia la cámara (el lado que se ve es +z).
// cx: dónde está el centro del cuerpo (para que la criatura quede centrada en su sitio aunque lleve una lanza).
function perfil(nombre, w, h, d, fn, o = {}) {
  const m = new ESCU.Modelo(w, h, d);
  fn(m, d / 2);
  const r = defModelo(nombre, m, Object.assign({ luz: [-0.5, 0.78, 0.38], amb: 0.27, envuelve: 0.2, cima: o.cima ?? 0.42, sombra: false, tinta: '#05030a', contraste: 1.22, realce: 0.05, cumulo: 3 }, o));
  if (o.cx !== undefined) META_SPR[nombre].dx = r.dx + (w / 2 - o.cx) / 16;
  return r;
}

// ---------------------------------------------------------------- el gruto, de perfil
// o: { esc, piel, pie, fase (-1..1, paso), inclina (+ hacia delante), brazoC / brazoL (ángulo del brazo cercano /
//      lejano: + hacia delante), cabeza(m, x, y, cz), armaC(m, mano), armaL(m, mano), espalda(m, cz), cadera(m, cz) }
function grutoPerfil(m, cz, o) {
  const O = OSC, e = o.esc || 1, P = o.piel, fase = o.fase || 0;
  const cad = [14 * e, 13 * e];
  for (const [dz, s] of [[-2.4, -1], [2.4, 1]]) {
    const f = fase * s, z = cz + dz * e;
    m.con({ rot: ['z', f * 0.42, [cad[0], cad[1], z]] }, () => {
      const ro = [17 * e, 8.4 * e, z], to = [13.4 * e, 3.2 * e, z], de = [16.6 * e, 0.7 * e, z];
      m.capsula([cad[0], cad[1], z], ro, 2.2 * e, 1.7 * e, P); m.capsula(ro, to, 1.6 * e, 1.1 * e, P); m.capsula(to, de, 1.1 * e, 0.8 * e, o.pie || P);
      for (const dd of [-0.8, 0.8]) m.capsula(de, [de[0] + 1.8 * e, 0.3, z + dd * e], 0.5 * e, 0.2, O.hueso);
    });
  }
  m.esfera([cad[0], cad[1], cz], 2.9 * e, P);
  if (o.cadera) o.cadera(m, cz, e); else {
    m.figura(OP.corta(ESCU.SDF.elipsoide(14.6 * e, 11.6 * e, cz, 3.4 * e, 3 * e, 3.4 * e), OP.bajo(13 * e)), [9 * e, 7 * e, cz - 4 * e, 19 * e, 14 * e, cz + 4 * e], O.cuero);
    m.capsula([14.6 * e, 10 * e, cz + 0.6 * e], [15.4 * e, 6.6 * e, cz + 1.4 * e], 1.6 * e, 0.8 * e, O.harapo);
  }
  m.con({ rot: ['z', -(o.inclina || 0), [cad[0], cad[1], cz]] }, () => {
    m.capsula([cad[0], cad[1], cz], [19.6 * e, 20.6 * e, cz], 3.1 * e, 3.7 * e, o.torso || P);
    m.esfera([16 * e, 19.6 * e, cz], 3.9 * e, o.torso || P);
    if (!o.sinVertebras) for (let k = 0; k < 7; k++) { const a = 2.6 - k * 0.32, r = 4.4 * e; m.esfera([16 * e + Math.cos(a) * r, 18.6 * e + Math.sin(a) * r, cz], (0.95 - k * 0.05) * e, O.hueso); }
    m.capsula([19.6 * e, 20.6 * e, cz], [22.4 * e, 21.2 * e, cz], 2.1 * e, 1.8 * e, P);
    if (o.espalda) o.espalda(m, cz, e);
    const brazo = (dz, ang, arma) => {
      const hombro = [19.5 * e, 20.4 * e, cz + dz * e];
      m.con({ rot: ['z', ang, hombro] }, () => {
        const codo = [22 * e, 15.4 * e, cz + dz * 1.1 * e], mano = [24.2 * e, 10.4 * e, cz + dz * 1.05 * e];
        m.tubo([hombro, codo, mano], 1.5 * e, 1.05 * e, o.manga || P);
        for (const d of [-0.8, 0.3, 1.3]) m.capsula(mano, [mano[0] + 1.4 * e, mano[1] - 2 * e + d * 0.4 * e, mano[2] + d * 0.6 * e * Math.sign(dz)], 0.5 * e, 0.15 * e, O.hueso);
        if (arma) arma(m, mano, e);
      });
    };
    brazo(-3.6, o.brazoL ?? -fase * 0.3, o.armaL);
    o.cabeza(m, 24.6 * e, 21.6 * e, cz, e);
    brazo(3.6, o.brazoC ?? fase * 0.3, o.armaC);
  });
}
// cráneo de animal como máscara (hocico corto, mandíbula con dientes, cuenca con ojo que brilla)
function mascaraCraneo(m, hx, hy, cz, e, o = {}) {
  const O = OSC, Hb = o.hueso || O.hueso;
  for (const dz of [-1, 1]) m.tubo([[hx - 2 * e, hy + 1.4 * e, cz + dz * 2.2 * e], [hx - 5.4 * e, hy + 3 * e, cz + dz * 2.8 * e], [hx - 8.6 * e, hy + 3.8 * e, cz + dz * 3 * e]], 1.1 * e, 0.2 * e, o.piel); // orejas
  m.esfera([hx, hy, cz], 3.7 * e, Hb);
  m.figura(ESCU.SDF.capsula(hx + 1.2 * e, hy - 1 * e, cz, hx + 4.2 * e, hy - 1.6 * e, cz, 2.6 * e, 2 * e), [hx - 2 * e, hy - 5 * e, cz - 4 * e, hx + 7 * e, hy + 2 * e, cz + 4 * e], Hb);
  m.caja([hx - 0.6 * e, hy - 5 * e, cz - 1.8 * e], [hx + 4.8 * e, hy - 3.6 * e, cz + 1.8 * e], o.mandibula || Hb, { r: 0.6 });
  for (let k = 0; k < 4; k++) m.caja([hx + (1 + k) * e, hy - 3.8 * e, cz + 1.3 * e], [hx + (1.5 + k) * e, hy - 2.7 * e, cz + 2.1 * e], O.diente);
  m.esfera([hx + 0.8 * e, hy + 0.6 * e, cz + 2.8 * e], 1.5 * e, O.hueco, { modo: 'pintar' });
  m.esfera([hx + 1.2 * e, hy + 0.5 * e, cz + 3.4 * e], 0.62 * Math.max(1, e * 0.9), o.ojo || O.ojoAmbar);
  m.esfera([hx + 4.6 * e, hy - 1.2 * e, cz + 1.6 * e], 0.6 * e, O.hueco, { modo: 'pintar' });
  if (o.cuernos === 'carnero') for (const dz of [-1, 1]) m.tubo([[hx - 0.6 * e, hy + 2.8 * e, cz + dz * 1.8 * e], [hx - 2.6 * e, hy + 5.6 * e, cz + dz * 2.6 * e], [hx - 5.4 * e, hy + 5 * e, cz + dz * 3 * e], [hx - 5.8 * e, hy + 2.4 * e, cz + dz * 3 * e], [hx - 4 * e, hy + 1.2 * e, cz + dz * 3 * e]], 1.35 * e, 0.5 * e, Hb);
  if (o.cuernos === 'astas') for (const dz of [-1, 1]) {
    const z = cz + dz * 2 * e;
    m.tubo([[hx - 0.4 * e, hy + 3 * e, z], [hx - 1.6 * e, hy + 7 * e, z + dz * e], [hx - 3.6 * e, hy + 10.6 * e, z + dz * 1.6 * e]], 0.9 * e, 0.3 * e, Hb);
    m.capsula([hx - 1.2 * e, hy + 6 * e, z + dz * e], [hx + 1.4 * e, hy + 9 * e, z + dz * 1.2 * e], 0.6 * e, 0.2 * e, Hb);
    m.capsula([hx - 2.6 * e, hy + 9 * e, z + dz * 1.4 * e], [hx - 5.6 * e, hy + 11 * e, z + dz * 1.4 * e], 0.55 * e, 0.2 * e, Hb);
  }
}

CONSTRUIR_MODELOS.push(function criaturasOscuras() {
  const O = OSC, M = MAT, N = MAT2, { SDF, OP } = ESCU;
  const ANDA = [{ fase: 1 }, { fase: -1 }];

  // ---------------------------------------------------------------- grutos (0-1 andan, 2 prepara, 3 ataca)
  const grutos = (nombre, w, h, cx, base, poses, opt = {}) => poses.forEach((p, f) => perfil(`${nombre}_${f}`, w, h, opt.d || 18, (m, cz) => grutoPerfil(m, cz, Object.assign({}, base, p)), { cx }));
  // lancero: máscara de cráneo con cuernos de carnero y lanza de hueso
  const lanza = (m, [x, y, z], e) => {
    m.capsula([x - 10 * e, y - 7 * e, z], [x + 13 * e, y + 10 * e, z], 0.6 * e, M.maderaOsc);
    m.figura(SDF.capsula(x + 13 * e, y + 10 * e, z, x + 16.4 * e, y + 12.4 * e, z, 1.35 * e, 0.1), [x + 11 * e, y + 8 * e, z - 2, x + 18 * e, y + 14 * e, z + 2], O.lanzaHueso);
    for (const d of [-1, 1]) m.capsula([x + 12.2 * e, y + 9.4 * e, z], [x + 10.4 * e, y + 9.4 * e + d * 1.6 * e, z + 0.4], 0.5 * e, 0.2, O.pluma);
  };
  grutos('gruto_lancero', 46, 40, 18, { piel: O.pielGruto, armaL: lanza, cabeza: (m, x, y, cz, e) => mascaraCraneo(m, x, y, cz, e, { piel: O.pielGruto, cuernos: 'carnero' }), espalda: (m, cz) => podre(m, [14.4, 22.4, cz + 1], 1.8, 3) },
    [...ANDA, { brazoL: -0.8, inclina: -0.18 }, { brazoL: 0.7, inclina: 0.32, fase: 0.6 }]);
  // vidente: máscara con astas, túnica, bastón con un corazón de Podre enjaulado
  const baston = (m, [x, y, z], e) => {
    m.capsula([x - 1, y - 8, z], [x + 1, y + 13, z], 0.6, M.maderaOsc);
    const c = [x + 1.2, y + 15, z];
    for (let k = 0; k < 4; k++) { const a = k / 4 * Math.PI * 2; m.tubo([[c[0], c[1] - 2.6, c[2]], [c[0] + Math.cos(a) * 2.2, c[1], c[2] + Math.sin(a) * 2.2], [c[0], c[1] + 2.6, c[2]]], 0.4, 0.4, O.hueso); }
    m.esfera(c, 1.6, O.podre); for (const [dx, dy] of [[0.6, 0.6], [-0.5, -0.3], [0.3, -0.8]]) m.esfera([c[0] + dx, c[1] + dy, c[2] + 1.2], 0.55, O.poro);
  };
  const tunica = (m, cz) => { m.figura(OP.corta(SDF.elipsoide(15, 13, cz, 5.4, 8, 4.6), OP.bajo(17)), [9, 4, cz - 6, 21, 18, cz + 6], ESCU.mat(ESCU.rampa('#3a2a4a', 6, { oscuro: 0.25 }), {})); };
  grutos('gruto_chaman', 40, 46, 17, { piel: ESCU.mat(O.pielGruto.r.map((c) => ESCU.mezclar(c, '#4a3a5a', 0.3)), {}), torso: ESCU.mat(ESCU.rampa('#3a2a4a', 6, { oscuro: 0.25 }), {}), manga: ESCU.mat(ESCU.rampa('#2e2240', 6, { oscuro: 0.25 }), {}), cadera: tunica, sinVertebras: true, armaL: baston, cabeza: (m, x, y, cz, e) => mascaraCraneo(m, x, y, cz, e, { piel: O.pielGruto, cuernos: 'astas', ojo: O.poro }) },
    [...ANDA, { brazoL: 1.9, inclina: -0.25 }, { brazoL: 1.1, inclina: 0.15 }]);
  // mole: enorme, mandíbula de hierro, cuchilla oxidada
  const oxido = ESCU.mat(['#140806', '#281008', '#401a0c', '#5a2a12', '#764018', '#8e5a2a', '#a8784a'], { brillo: 0.4, tex: ESCU.TEX.grumos(141, 1.4, 0.8) });
  const cuchilla = (m, [x, y, z], e) => {
    m.capsula([x, y + 2 * e, z], [x, y - 3 * e, z], 0.8 * e, M.maderaOsc);
    m.figura(OP.corta(SDF.caja(x - 3 * e, y - 16 * e, z - 0.5 * e, x + 4 * e, y - 2.6 * e, z + 0.5 * e, 0.3), (px, py) => (px - x - 4 * e) + (y - 2.6 * e - py) * 0.18), [x - 4 * e, y - 17 * e, z - 2, x + 5 * e, y - 2 * e, z + 2], oxido);
  };
  grutos('gruto_bruto', 56, 60, 25, {
    esc: 1.5, piel: ESCU.mat(['#0a0e0a', '#141c12', '#20301c', '#2e4228', '#3e5634', '#526c44', '#6a8656'], {}), armaC: cuchilla,
    cabeza: (m, x, y, cz, e) => {
      m.esfera([x - 0.6 * e, y + 0.4 * e, cz], 3.6 * e, OSC.pielGruto);
      m.caja([x - 0.4 * e, y - 4.8 * e, cz - 2.2 * e], [x + 3.4 * e, y - 1.6 * e, cz + 2.2 * e], M.hierro, { r: 0.8 }); // mandíbula de hierro
      for (let k = 0; k < 3; k++) m.capsula([x + (0.4 + k) * e, y - 1.8 * e, cz + 1.8 * e], [x + (0.6 + k) * e, y - 0.4 * e, cz + 2 * e], 0.45 * e, 0.15, O.diente);
      m.esfera([x + 1.4 * e, y + 1.2 * e, cz + 2.6 * e], 0.95 * e, O.hueco, { modo: 'pintar' }); m.esfera([x + 1.6 * e, y + 1.1 * e, cz + 3.1 * e], 0.5 * e, O.ojoRojo);
      for (const [a, b] of [[[x - 3, y + 3.4, cz + 2.6], [x + 1, y + 3.8, cz + 3]], [[x - 2.4, y - 1, cz + 3], [x - 2, y + 2.4, cz + 3.2]]]) m.capsula(a.map((v, i) => (i === 2 ? v * 1 : v)), b, 0.3 * e, OSC.hueco); // costuras
    },
    espalda: (m, cz, e) => { podre(m, [14 * e, 23.4 * e, cz + 1.4 * e], 2.6 * e, 7); m.toro([20 * e, 20 * e, cz], 3.2 * e, 0.5 * e, M.hierro, { eje: 'x' }); },
  }, [...ANDA, { brazoC: 2.7, inclina: -0.15 }, { brazoC: 1.0, inclina: 0.35, fase: 0.5 }], { d: 26 });
  // sembrador: capucha de hojas, cara en sombra con ojos ámbar, saco de semillas de Podre
  const capuchaHojas = (m, x, y, cz, e) => {
    m.esfera([x, y, cz], 3.5, OSC.pielGruto);
    // capucha de hojas secas superpuestas como escamas, con la cara en sombra
    m.figura(OP.resta(SDF.capsula(x - 0.4, y + 0.6, cz, x - 4.4, y - 2.4, cz, 4.4, 4.2), SDF.elipsoide(x + 3.4, y - 0.8, cz + 1.6, 2.8, 3.2, 3.2)), [x - 10, y - 8, cz - 7, x + 6, y + 6, cz + 7], O.hojaSeca);
    for (let k = 0; k < 9; k++) { const t = k / 8; m.elipsoide([x - 1 - t * 5, y + 4 - t * 6, cz + 3.6 * Math.cos(t * 2)], [2.2, 0.6, 1.6], O.hojaSeca); }
    m.esfera([x + 2.2, y - 0.6, cz + 2.6], 1.8, O.hueco, { modo: 'pintar' });
    for (const dz of [1.6, 3]) m.esfera([x + 2.6, y - 0.4, cz + dz], 0.5, O.ojoAmbar);
  };
  const saco = (m, [x, y, z]) => { m.elipsoide([x + 1, y - 2, z], [2.4, 3, 2.2], ESCU.mat(ESCU.rampa('#6a5434', 6), {})); for (const [dx, dy] of [[1.6, -0.6], [0.4, -2.8], [2.4, -2.4]]) m.esfera([x + dx, y + dy, z + 1.9], 0.55, O.poro); };
  grutos('gruto_jardinero', 40, 40, 17, { piel: O.pielGruto, cabeza: capuchaHojas, armaC: saco, espalda: (m, cz) => { m.elipsoide([14, 19.4, cz], [3.6, 3, 3.6], ESCU.mat(ESCU.rampa('#4a3a24', 6, { oscuro: 0.25 }), {})); podre(m, [13.4, 22, cz], 1.4, 9); } },
    [...ANDA, { brazoC: 2.3, inclina: -0.2 }, { brazoC: 1.2, inclina: 0.2 }]);
  // acechador: capa de musgo que le tapa la cabeza, ojos entre las hojas, arco de hueso
  const capaMusgo = (m, x, y, cz) => {
    m.esfera([x, y, cz], 3.5, OSC.pielGruto);
    m.figura(OP.resta(SDF.capsula(x - 0.6, y + 0.6, cz, x - 3.4, y - 2, cz, 4.4, 4.2), SDF.elipsoide(x + 3.4, y - 0.6, cz + 1.6, 3, 3, 3.2)), [x - 9, y - 8, cz - 7, x + 6, y + 6, cz + 7], O.musgoOsc, { ruido: [0.35, 1.2, 5] });
    for (let k = 0; k < 5; k++) m.capsula([x - 2 - k, y + 3 - k * 0.6, cz + (k % 2 ? 3 : -3)], [x - 3 - k, y - 1 - k, cz + (k % 2 ? 3.6 : -3.6)], 0.6, 0.2, O.musgoOsc);
    m.esfera([x + 2.2, y - 0.4, cz + 2.4], 1.7, O.hueco, { modo: 'pintar' });
    for (const dz of [1.4, 2.8]) m.esfera([x + 2.6, y - 0.2, cz + dz], 0.5, O.ojoAmbar);
  };
  const arco = (m, [x, y, z]) => {
    m.figura(OP.corta(SDF.toro(x + 1, y + 1, z, 6.6, 0.65, 'z'), (px) => x + 2.2 - px), [x - 7, y - 7, z - 2, x + 3, y + 9, z + 2], O.hueso);
    m.capsula([x + 1.2, y - 5.6, z], [x + 1.2, y + 7.6, z], 0.25, O.hueso);
  };
  grutos('gruto_cazador', 42, 42, 17, { piel: O.pielGruto, cabeza: capaMusgo, armaL: arco, espalda: (m, cz) => { m.figura(OP.corta(SDF.capsula(17, 21, cz, 11, 8, cz, 4.4, 3), OP.bajo(23)), [5, 4, cz - 6, 23, 26, cz + 6], O.musgoOsc, { ruido: [0.5, 1.2, 7] }); for (let k = 0; k < 6; k++) m.capsula([9 + k * 1.4, 8 + k * 0.4, cz + (k % 2 ? 2.6 : -2.6)], [8.4 + k * 1.4, 4.6, cz + (k % 2 ? 3 : -3)], 0.7, 0.3, O.musgoOsc); } },
    [...ANDA.map((p) => Object.assign({ brazoL: 0.9 }, p)), { brazoL: 1.5, brazoC: 1.6, inclina: -0.1 }, { brazoL: 1.5, brazoC: 0.9 }]);

  // ---------------------------------------------------------------- babas (0 quieta, 1 aplastada, 2 en el aire)
  const baba = (nombre, mt, extra, esc = 1) => {
    for (let f = 0; f < 3; f++) perfil(`${nombre}_${f}`, Math.round(36 * esc), Math.round(24 * esc), Math.round(24 * esc), (m, cz) => {
      const cx = 17 * esc, rx = (f === 1 ? 12 : f === 2 ? 8.6 : 10.4) * esc, ry = (f === 1 ? 5.6 : f === 2 ? 9.4 : 7.4) * esc, rz = 8.4 * esc;
      if (f !== 2) m.elipsoide([cx, 1, cz], [rx + 2.6 * esc, 1.4, rz + 1.6], mt, { ruido: [0.9, 2, 3] });
      m.figura(OP.corta(SDF.elipsoide(cx, ry * 0.6 + (f === 2 ? 1.5 : 0), cz, rx, ry, rz), OP.sobre(0)), [cx - rx - 2, 0, cz - rz - 2, cx + rx + 2, ry * 2 + 2, cz + rz + 2], mt, { ruido: [1.2, 2.6, 5 + f] });
      extra(m, cx, ry * 0.6 + (f === 2 ? 1.5 : 0), cz, rx, ry, rz, f, esc);
    }, { cx: 17 * esc });
  };
  const craneoEn = (m, sx, sy, sz, esc, ojo) => {
    m.esfera([sx, sy, sz], 4 * esc, O.hueso);
    m.caja([sx - 0.6 * esc, sy - 5 * esc, sz - 2 * esc], [sx + 4 * esc, sy - 3.2 * esc, sz + 2 * esc], O.hueso, { r: 0.7 });
    m.esfera([sx + 1.2 * esc, sy + 0.6 * esc, sz + 2.9 * esc], 1.5 * esc, O.hueco, { modo: 'pintar' });
    m.esfera([sx + 1.5 * esc, sy + 0.5 * esc, sz + 3.5 * esc], 0.62, ojo);
    m.esfera([sx + 3.3 * esc, sy - 1.3 * esc, sz + 2.3 * esc], 0.7 * esc, O.hueco, { modo: 'pintar' });
    for (let k = 0; k < 4; k++) m.caja([sx + k * 1.05 * esc, sy - 3.4 * esc, sz + 1.8 * esc], [sx + (0.5 + k * 1.05) * esc, sy - 2.3 * esc, sz + 2.6 * esc], O.diente);
  };
  baba('babosa', O.baba, (m, cx, cy, cz, rx, ry, rz, f) => {
    craneoEn(m, cx + rx * 0.42, cy + ry * 0.1, cz + rz * 0.5, 1, O.ojoRojo);
    for (let k = 0; k < 4; k++) m.tubo([[cx - 7 + k * 2.2, cy + 1, cz + 1 - k], [cx - 8 + k * 2.2, cy + ry * 0.62, cz + 2 - k], [cx - 6.4 + k * 2.2, cy + ry * 0.82, cz + 3 - k]], 0.7, 0.45, O.hueso);
    for (const [x, y, z] of [[-3, 0.5, 0.75], [-7.4, -0.2, 0.7]]) m.esfera([cx + x, cy + ry * y + 2, cz + rz * z], 0.6, O.ojoRojo);
  });
  baba('babosa_roja', O.babaSangre, (m, cx, cy, cz, rx, ry, rz) => {
    for (const [x, y, z, r] of [[-3, 0.3, 0.55, 2.6], [2.6, 0.1, 0.62, 2.2], [-6.4, -0.1, 0.4, 1.8]]) m.esfera([cx + x, cy + ry * y + 1, cz + rz * z], r, ESCU.mat(['#1a0408', '#3a0a12', '#5e1420', '#84202e', '#a8343c'], { brillo: 0.7 }));
    for (let k = 0; k < 5; k++) m.tubo([[cx - 5 + k * 2.4, cy, cz + 2], [cx - 5.6 + k * 2.4, cy + ry * 0.7, cz + 3.6], [cx - 4.4 + k * 2.4, cy + ry * 0.9, cz + 4.6]], 0.6, 0.4, O.hueso);
    for (const [x, y] of [[4.4, 0.5], [-1, 0.75], [6.6, 0.1]]) m.esfera([cx + x, cy + ry * y + 1.4, cz + rz * 0.72], 0.6, O.ojoAmbar);
  }, 1.12);
  baba('babosa_veneno', ESCU.mat(['#06040a', '#0e0814', '#1a0e24', '#281434', '#381c48', '#4a265c', '#5e3272', '#784490'], { brillo: 0.8 }), (m, cx, cy, cz, rx, ry, rz, f) => {
    const r = azar(31 + f);
    for (let k = 0; k < 9; k++) {
      const a = r() * Math.PI * 1.1 - 0.05, b = r() * Math.PI, x = cx + Math.cos(b) * rx * 0.86, y = cy + Math.sin(a) * ry * 0.92, z = cz + Math.sin(b) * rz * 0.86;
      m.esfera([x, y, z], 1.2 + r() * 1.3, ESCU.mat(['#3a0a2a', '#7a1458', '#c0288c', '#ff3cb4', '#ff8ad6'], { luz: 0.8, brillo: 0.6 }));
    }
    for (const [x, y] of [[3.4, 0.55], [5.4, 0.35]]) m.esfera([cx + x, cy + ry * y + 1, cz + rz * 0.8], 0.6, O.ojoRojo);
  }, 1.08);
  baba('babosita', ESCU.mat(['#140c04', '#2a1a08', '#463010', '#66481a', '#886424', '#aa8434', '#c8a44c'], { brillo: 0.9 }), (m, cx, cy, cz, rx, ry, rz) => { m.esfera([cx + 2, cy + ry * 0.2 + 1, cz + rz * 0.62], 0.8, O.ojoAmbar); }, 0.62);

  // ---------------------------------------------------------------- gusanos (0 alzado con la boca abierta, 1 más bajo)
  const gusano = (nombre, piel, esc, extra) => {
    for (let f = 0; f < 2; f++) perfil(`${nombre}_${f}`, Math.round(42 * esc), Math.round(34 * esc), Math.round(20 * esc), (m, cz) => {
      const pts = [], alto = f ? 8 : 14;
      for (let k = 0; k < 9; k++) { const t = k / 8, x = 4 + t * 24, y = t < 0.5 ? 3.4 + Math.sin(t * 9 + f * 2) * 0.7 : 3.4 + Math.pow((t - 0.5) / 0.5, 1.6) * alto; pts.push([(x - (t > 0.5 ? (t - 0.5) * (f ? 4 : 9) : 0)) * esc, y * esc, cz]); }
      pts.forEach((p, k) => {
        const t = k / 8, r = (3 + Math.sin(t * Math.PI) * 1.3) * esc;
        m.esfera(p, r, piel);
        if (k > 0) m.toro(p, r * 0.95, 0.45 * esc, ESCU.mat(piel.r.slice(0, 4), {}), { eje: 'x' });
        if (k < 5) for (const dz of [-1, 1]) m.capsula([p[0], p[1] - r * 0.7, cz + dz * r * 0.6], [p[0] + 0.8 * esc + (f ? -0.6 : 0.6), 0, cz + dz * r * 1.1], 0.5 * esc, 0.2, O.hueso);
        if (extra) extra(m, p, r, k, cz);
      });
      const h = pts[8], b = [h[0] + 1.6 * esc, h[1] + 1 * esc, cz + 2.6 * esc], abre = f ? 0.7 : 1;
      m.cilindro([b[0], b[1], b[2] - 3 * esc], 5 * esc, 3.4 * esc, piel, { eje: 'z', red: 1 });
      m.cilindro([b[0], b[1], b[2] + 0.2], 4 * esc * abre, 0.6, O.boca, { eje: 'z' });
      m.cilindro([b[0], b[1], b[2] - 0.6], 2.2 * esc * abre, 1.2, O.hueco, { eje: 'z' });
      for (let k = 0; k < 10; k++) { const a = k / 10 * Math.PI * 2; m.capsula([b[0] + Math.cos(a) * 4 * esc * abre, b[1] + Math.sin(a) * 4 * esc * abre, b[2] + 0.4], [b[0] + Math.cos(a) * 2.2 * esc * abre, b[1] + Math.sin(a) * 2.2 * esc * abre, b[2] + 0.8], 0.6 * esc, 0.15, O.diente); }
    }, { cx: 18 * esc });
  };
  gusano('larva', O.pielPalida, 1);
  gusano('larva_grande', O.pielPalida, 1.3, (m, p, r, k, cz) => { if (k > 1 && k < 7 && k % 2 === 0) m.esfera([p[0] - r * 0.2, p[1] + r * 0.7, cz + r * 0.3], r * 0.55, O.acido || ESCU.mat(['#1a4a08', '#2e7a10', '#4aa818', '#74d028', '#aaf05a'], { luz: 0.9, brillo: 0.6 })); });
  gusano('larva_moho', ESCU.mat(['#08060a', '#121018', '#1e1a26', '#2c2636', '#3c3448', '#4e445c', '#625872'], {}), 1, (m, p, r, k, cz) => { if (k % 3 === 1) podre(m, [p[0], p[1] + r * 0.8, cz + r * 0.4], r * 0.6, 20 + k); });

  // ---------------------------------------------------------------- Portador (0-1 anda, 2 suelta esporas)
  for (let f = 0; f < 3; f++) perfil(`seton_${f}`, 40, 44, 20, (m, cz) => {
    const P = O.carneMuerta, fase = f < 2 ? (f ? -1 : 1) : 0, prep = f === 2;
    for (const [dz, s] of [[-2.6, -1], [2.6, 1]]) m.con({ rot: ['z', fase * s * 0.35, [15, 13, cz + dz]] }, () => {
      m.capsula([15, 13, cz + dz], [16.6, 7, cz + dz], 2, 1.6, O.harapo);
      m.capsula([16.6, 7, cz + dz], [15.4, 1.4, cz + dz], 1.5, 1.2, P);
      m.elipsoide([16.4, 0.9, cz + dz], [2.4, 0.9, 1.3], P);
    });
    m.esfera([15, 13, cz], 3, O.harapo);
    m.con({ rot: ['z', prep ? 0.25 : -0.05, [15, 13, cz]] }, () => {
      m.capsula([15, 13, cz], [19.6, 21, cz], 3.6, 4.2, O.harapo);
      m.capsula([19.6, 21, cz], [22.6, 19.6, cz], 1.9, 1.6, P);
      const hx = 24.6, hy = 18.2;
      m.esfera([hx, hy, cz], 3.5, P);
      m.caja([hx - 0.2, hy - 5.6, cz - 1.7], [hx + 3, hy - 3.2, cz + 1.7], P, { r: 0.6 });
      m.esfera([hx + 2.4, hy - 2.6, cz + 1.4], 1.5, O.boca, { modo: 'pintar' });
      m.esfera([hx + 1.4, hy + 0.6, cz + 2.8], 1.3, O.hueco, { modo: 'pintar' });
      m.esfera([hx + 1.7, hy + 0.5, cz + 3.3], 0.55, O.ojoPalido);
      for (const [dz, s] of [[-3.8, -1], [3.8, 1]]) m.con({ rot: ['z', prep ? 1.2 : fase * s * 0.25, [19.6, 20.4, cz + dz]] }, () => {
        m.tubo([[19.6, 20.4, cz + dz], [21.6, 14, cz + dz], [22.4, 6.4, cz + dz * 0.95]], 1.5, 1.05, P);
        for (const d of [-1, 0, 1]) m.capsula([22.4, 6.4, cz + dz * 0.95], [23 + d * 0.4, 4, cz + dz * 0.95 + d * 0.6], 0.5, 0.2, P);
      });
      m.tubo([[16.6, 19, cz], [17, 26, cz], [19, 30.4, cz]], 3, 2.2, ESCU.mat(P.r, { tex: (x, y) => (y % 3 === 0 ? -1 : 0) }));
      const sx = 20.4, sy = 31 + (prep ? 1.4 : 0), ab = prep ? 0.35 : 0.22;
      m.figura(OP.corta(SDF.elipsoide(sx, sy, cz, prep ? 11 : 10, prep ? 4.2 : 5, prep ? 9.2 : 8.4), (x, y) => sy - 0.6 - y + (x - sx) * ab), [sx - 12, sy - 6, cz - 10, sx + 12, sy + 6, cz + 10], O.hongoCap, { ruido: [0.8, 2, 9] });
      m.figura(OP.corta(SDF.elipsoide(sx, sy - 0.8, cz, prep ? 10.6 : 9.6, 1.8, prep ? 8.8 : 8), (x, y) => y - (sy - 0.6) - (x - sx) * ab), [sx - 12, sy - 7, cz - 10, sx + 12, sy + 2, cz + 10], prep ? ESCU.mat(O.laminas.r, { luz: 1, plano: true, sube: 1 }) : O.laminas);
      podre(m, [16.6, 17, cz + 3.4], 1.8, 8);
    });
  }, { cx: 19 });

  // ---------------------------------------------------------------- Fauce (0 escondida, 1 quieta, 2 abre, 3 muerde)
  for (let f = 0; f < 4; f++) perfil(`planta_trampa_${f}`, 40, 40, 22, (m, cz) => {
    for (let k = 0; k < 6; k++) { const a = k / 6 * 6.28 + 0.4; m.elipsoide([14 + Math.cos(a) * 5, 1, cz + Math.sin(a) * 4], [4, 0.8, 2], N.hojaSelva2); }
    if (f === 0) { m.elipsoide([14, 3, cz], [4, 3.4, 4], O.petalo); for (let k = 0; k < 5; k++) m.capsula([11 + k * 1.5, 5.5, cz + 3.6], [11.4 + k * 1.5, 4, cz + 4], 0.4, 0.1, O.diente); return; }
    const hx = f === 3 ? 26 : f === 2 ? 18 : 21, hy = f === 3 ? 16 : f === 2 ? 22 : 21, abre = f === 2 ? 0.75 : f === 3 ? 0 : 0.22;
    m.tubo([[14, 0, cz], [13, 8, cz], [hx - 8, hy - 4, cz], [hx - 3, hy, cz]], 2.4, 1.8, O.vena);
    for (let k = 1; k < 4; k++) m.capsula([13 - k * 0.2 + k * 1.6, k * 4.4, cz], [11 + k * 1.6, k * 4.4 + 2, cz + 1.4], 0.6, 0.1, O.espina);
    for (const s of [1, -1]) m.con({ rot: ['z', s * abre, [hx - 3, hy, cz]] }, () => {
      m.figura(OP.corta(SDF.elipsoide(hx + 2, hy, cz, 7, 4.6, 5), (x, y) => (s > 0 ? hy - y : y - hy)), [hx - 6, hy - 6, cz - 6, hx + 10, hy + 6, cz + 6], O.petalo);
      m.figura(OP.corta(SDF.elipsoide(hx + 2.4, hy, cz, 6, 3.4, 3.8), (x, y) => (s > 0 ? hy + 0.3 - y : y - hy + 0.3)), [hx - 5, hy - 5, cz - 5, hx + 9, hy + 5, cz + 5], null, { modo: 'quitar' });
      m.figura(OP.corta(SDF.elipsoide(hx + 2.4, hy, cz, 6, 3.4, 3.8), (x, y) => (s > 0 ? hy + 0.3 - y : y - hy + 0.3), (x, y) => (s > 0 ? y - hy - 1 : hy - 1 - y)), [hx - 5, hy - 5, cz - 5, hx + 9, hy + 5, cz + 5], O.boca);
      for (let k = 0; k < 6; k++) m.capsula([hx - 2 + k * 1.6, hy, cz + 3.6], [hx - 1.8 + k * 1.6, hy - s * 1.9, cz + 3.6], 0.55, 0.12, O.diente);
      for (let k = 0; k < 4; k++) m.capsula([hx - 1 + k * 2.2, hy + s * 4, cz + 2], [hx - 1.4 + k * 2.2, hy + s * 6.2, cz + 2.4], 0.6, 0.1, O.espina);
    });
    if (f === 2) m.capsula([hx + 3, hy - 0.4, cz + 3], [hx + 3.4, hy - 5, cz + 3.4], 0.45, 0.2, ESCU.mat(['#5a6a20', '#8aa030', '#b8d04a', '#e0f080'], { brillo: 1 })); // baba que gotea
  }, { cx: 15 });

  // ---------------------------------------------------------------- Brazo de la Podre (0 agujero, 1-2 se mece, 3 se alza)
  for (let f = 0; f < 4; f++) perfil(`tentaculo_moho_${f}`, 34, 44, 22, (m, cz) => {
    m.toro([15, 0.6, cz], 4.6, 1.8, O.podre, { ruido: [0.4, 1, 3] });
    m.cilindro([15, 0, cz], 3.6, 0.6, O.hueco);
    for (const [x, z] of [[11, cz + 3], [19, cz + 3.4], [14, cz + 5.4]]) m.esfera([x, 1.6, z], 0.7, O.poro);
    if (f === 0) return;
    const alto = f === 3 ? 34 : 22, lado = f === 2 ? -1 : 1, pts = [];
    for (let k = 0; k <= 9; k++) { const t = k / 9; pts.push([15 + Math.sin(t * 2.6) * 4 * lado * (f === 3 ? 0.4 : 1) + (f === 3 ? t * t * 9 : 0), t * alto, cz]); }
    m.tubo(pts, 3.6, 0.8, O.podre);
    for (let k = 1; k < 9; k++) { const t = k / 9, p = pts[k], r = 3.6 + (0.8 - 3.6) * t; m.esfera([p[0], p[1], cz + r * 0.85], r * 0.34, k % 3 === 1 ? O.ojoAmbar : O.poro); }
  }, { cx: 15 });

  // ---------------------------------------------------------------- Aguijonera (0-1 aletea, 2 ataca)
  for (let f = 0; f < 3; f++) perfil(`avispa_${f}`, 40, 30, 22, (m, cz) => {
    const y = 14, rayas = ESCU.mat(['#08060a', '#14100e', '#2a2412', '#4a3e14', '#7a6418', '#a88a22', '#d0b030'], { brillo: 0.6, tex: (x) => (Math.floor((x + 40) / 2.4) % 2 ? 0 : -4) });
    m.elipsoide([18, y, cz], [3.2, 2.8, 2.8], O.podre); // tórax
    m.con({ rot: ['z', f === 2 ? 1.2 : -0.2, [16, y, cz]] }, () => {
      m.elipsoide([10.4, y - 0.6, cz], [5.4, 3.4, 3.2], rayas);
      m.figura(SDF.capsula(5.4, y - 0.8, cz, 1.6, y - 1.6, cz, 1, 0.1), [0, y - 4, cz - 2, 7, y + 2, cz + 2], O.hueso);
    });
    m.esfera([22.4, y + 0.4, cz], 2.6, O.hueso); // cara de calavera
    m.esfera([23.4, y + 0.8, cz + 1.8], 1.2, O.hueco, { modo: 'pintar' }); m.esfera([23.8, y + 0.7, cz + 2.3], 0.5, O.ojoRojo);
    m.capsula([24.4, y - 1.6, cz + 0.8], [25.6, y - 3, cz + 1.4], 0.5, 0.2, O.diente); m.capsula([24.4, y - 1.6, cz - 0.8], [25.6, y - 3, cz - 1.4], 0.5, 0.2, O.diente);
    m.capsula([22, y + 2.4, cz + 1], [25, y + 6, cz + 1.6], 0.3, O.podre);
    const ala = f === 1 ? -0.5 : 0.9;
    for (const dz of [-1, 1]) m.con({ rot: ['x', dz * ala, [17, y + 2.4, cz]] }, () => m.elipsoide([14, y + 6, cz + dz * 1.4], [6, 3.4, 0.5], ESCU.mat(['#2a2c34', '#4a4e5a', '#707686', '#9aa0ae', '#c4c8d2'], { tex: (x, yy) => ((Math.round(x + yy) % 4 === 0) ? -2 : 0) })));
    for (const dz of [-1, 1]) for (const dx of [-1, 1, 3]) m.tubo([[17 + dx, y - 2, cz + dz * 1.6], [17 + dx + 1, y - 5, cz + dz * 2.6], [17 + dx + 2.4, y - 8, cz + dz * 2.8]], 0.45, 0.25, O.podre);
  }, { cx: 16 });

  // ---------------------------------------------------------------- Lápida (0-1 anda, 2 prepara, 3 carga)
  for (let f = 0; f < 4; f++) perfil(`escarabajo_musgo_${f}`, 48, 34, 26, (m, cz) => {
    const incl = f === 2 ? 0.16 : f === 3 ? -0.12 : 0, pata = f === 1 ? 1 : f === 3 ? 1.6 : -1;
    const lapida = ESCU.mat(['#0e100e', '#1a1c1a', '#2a2c2a', '#3c3e3a', '#50524c', '#686a62', '#84867c'], { tex: ESCU.TEX.piedra(151, 2.6) });
    m.con({ rot: ['z', incl, [20, 4, cz]] }, () => {
      m.figura(OP.corta(SDF.caja(8, 4, cz - 7, 30, 22, cz + 7, 2), (x, y) => (y > 16 ? Math.hypot(x - 19, (y - 16) * 1.6) - 11 : -1)), [7, 3, cz - 8, 31, 26, cz + 8], lapida);
      m.pintar(() => { m.caja([16, 17, cz + 6], [22, 18, cz + 8], O.hueco); m.caja([18.4, 11, cz + 6], [19.6, 20, cz + 8], O.hueco); m.caja([12, 8, cz + 6], [26, 8.8, cz + 8], O.hueco); });
      for (const [x, y, r] of [[12, 21, 2.6], [22, 23.4, 2.2], [27, 18, 2], [17, 24.6, 1.8], [10, 15, 1.6]]) m.esfera([x, y, cz + 3], r, M.musgo, { ruido: [0.4, 1, x] });
      m.esfera([16, 25.2, cz + 2], 0.8, N.lunar); m.capsula([25, 21, cz + 3], [25, 24, cz + 3.4], 0.35, N.tallo);
      m.esfera([33, 9, cz], 4.6, O.podre);
      for (const dz of [-1, 1]) { m.esfera([35, 10.6, cz + dz * 2.6], 0.75, O.ojoRojo); m.tubo([[35.4, 7, cz + dz * 2], [38.6, 6, cz + dz * 2.8], [40, 7.6, cz + dz * 1.4]], 0.9, 0.3, O.hueso); }
    });
    for (const dz of [-1, 1]) for (const [k, dx] of [[0, 12], [1, 19], [2, 26]]) {
      const s = (k % 2 ? 1 : -1) * pata * dz * 0.6;
      m.tubo([[dx, 6, cz + dz * 6], [dx + 2 + s * 2, 4.4, cz + dz * 9.4], [dx + 1 + s * 3.4, 0, cz + dz * 10.6]], 1.1, 0.6, O.podre);
    }
  }, { cx: 21 });

  // ---------------------------------------------------------------- mascotas (0 quieta, 1 anda, 2 salta; luci 0-1)
  for (let f = 0; f < 3; f++) perfil(`topito_${f}`, 22, 18, 16, (m, cz) => {
    const y = f === 2 ? 1.6 : 0, P = O.pielPalida;
    m.elipsoide([10, 4.4 + y, cz], [5.4, 4.2, 4.4], P);
    m.elipsoide([15, 4 + y, cz], [2.4, 2.2, 2.4], P);
    for (let k = 0; k < 8; k++) { const a = k / 8 * Math.PI * 2; m.capsula([17, 4 + y, cz], [18.4, 4 + y + Math.sin(a) * 1.6, cz + Math.cos(a) * 1.6], 0.45, 0.25, ESCU.mat(ESCU.rampa('#d87888', 5), {})); } // nariz de estrella
    for (const dz of [-1, 1]) m.elipsoide([13 + (f === 1 ? dz : 0), 1 + y, cz + dz * 2.6], [1.8, 0.9, 1.2], O.hueso);
  }, { cx: 11 });
  for (let f = 0; f < 3; f++) perfil(`mohito_${f}`, 18, 22, 16, (m, cz) => {
    const y = f === 2 ? 1.6 : 0;
    for (const dz of [-1, 1]) m.elipsoide([9 + (f === 1 ? dz : 0), 0.8 + y, cz + dz * 1.6], [1.4, 0.8, 1.1], O.carneMuerta);
    m.cilindro([9, 1 + y, cz], 2.6, 6, O.carneMuerta, { r1: 2.2, red: 0.6 });
    m.esfera([10.8, 5 + y, cz + 2.2], 0.9, O.hueco, { modo: 'pintar' }); m.esfera([11, 5 + y, cz + 2.6], 0.45, O.ojoPalido);
    m.figura(OP.corta(SDF.elipsoide(9, 7.6 + y, cz, 5.4, 3.6, 5), OP.sobre(6.8 + y)), [3, 6 + y, cz - 6, 15, 12 + y, cz + 6], O.hongoCap);
    m.figura(OP.corta(SDF.elipsoide(9, 7.2 + y, cz, 5, 1, 4.6), OP.bajo(7.2 + y)), [3, 5 + y, cz - 6, 15, 9 + y, cz + 6], O.laminas);
  }, { cx: 9 });
  for (let f = 0; f < 2; f++) perfil(`luci_${f}`, 22, 18, 16, (m, cz) => {
    m.elipsoide([7, 8, cz], [4, 2.8, 2.8], ESCU.mat(['#5a7010', '#90b020', '#c8e040', '#eeff80'], { luz: 1, plano: true }));
    m.esfera([12, 8.6, cz], 2.2, O.podre); m.esfera([15, 9, cz], 1.8, O.podre);
    m.esfera([15.8, 9.4, cz + 1.4], 0.6, O.ojoAmbar);
    m.capsula([16, 10.4, cz + 0.6], [18.6, 13, cz + 1], 0.3, O.podre);
    for (const dz of [-1, 1]) m.con({ rot: ['x', dz * (f ? -0.4 : 0.7), [12, 10, cz]] }, () => m.elipsoide([10, 12.6, cz + dz], [4.6, 2.2, 0.4], ESCU.mat(['#3a4048', '#5e6672', '#8a94a2', '#b8c2ce'], {})));
    for (const dz of [-1, 1]) for (const dx of [-1, 1]) m.capsula([12 + dx, 6.6, cz + dz], [13 + dx * 1.4, 4, cz + dz * 2], 0.3, 0.2, O.podre);
  }, { cx: 11 });

  // ---------------------------------------------------------------- mercaderes, de perfil (0-1 andan, 2-3 quietos)
  const humano = (nombre, o) => {
    for (let f = 0; f < 4; f++) perfil(`mercader_${nombre}_${f}`, 34, 40, 18, (m, cz) => {
      const fase = f < 2 ? (f ? -1 : 1) : 0, resp = f === 3 ? 0.4 : 0, P = o.piel;
      for (const [dz, s] of [[-2.2, -1], [2.2, 1]]) m.con({ rot: ['z', fase * s * 0.35, [14, 12, cz + dz]] }, () => {
        m.capsula([14, 12, cz + dz], [14.6, 6.4, cz + dz], 1.9, 1.5, o.pant);
        m.capsula([14.6, 6.4, cz + dz], [14, 1.6, cz + dz], 1.5, 1.3, o.pant);
        m.elipsoide([15, 1, cz + dz], [2.6, 1.2, 1.5], o.bota);
      });
      m.capsula([14, 12, cz], [14.6, 20 + resp, cz], 3.6, 3.8, o.ropa);
      if (o.abrigo) m.figura(OP.corta(SDF.elipsoide(14, 12, cz, 4.6, 9, 4.4), OP.bajo(16)), [8, 3, cz - 6, 20, 17, cz + 6], o.abrigo);
      const hx = 15.6, hy = 24.6 + resp;
      m.capsula([14.6, 20 + resp, cz], [hx, hy - 2, cz], 1.6, P);
      m.esfera([hx, hy, cz], 3.8, P);
      m.capsula([hx + 3.4, hy - 0.2, cz + 0.8], [hx + 4.6, hy - 1.2, cz + 0.8], 0.9, 0.6, P); // nariz
      m.esfera([hx + 2.2, hy + 0.6, cz + 2.6], 0.5, O.hueco);
      if (o.cabeza) o.cabeza(m, hx, hy, cz);
      for (const [dz, s] of [[-3.6, -1], [3.6, 1]]) m.con({ rot: ['z', fase * s * 0.3 + (s > 0 ? o.brazo || 0 : 0), [14.6, 19.6 + resp, cz + dz]] }, () => {
        m.tubo([[14.6, 19.6 + resp, cz + dz], [15.6, 14.4, cz + dz * 1.05], [16.6, 9.6, cz + dz]], 1.4, 1.1, o.manga || o.ropa);
        m.esfera([16.8, 9, cz + dz], 1.3, P);
        if (s > 0 && o.mano) o.mano(m, [16.8, 9, cz + dz]);
      });
    }, { cx: 15 });
  };
  humano('barbas', {
    piel: ESCU.mat(ESCU.rampa('#c89a7a', 6, { oscuro: 0.4 }), {}), ropa: ESCU.mat(ESCU.rampa('#4a5a3a', 6), {}), pant: ESCU.mat(ESCU.rampa('#3a3028', 6), {}), bota: O.cuero,
    abrigo: ESCU.mat(ESCU.rampa('#5a4030', 6), {}),
    cabeza: (m, x, y, cz) => {
      const barba = ESCU.mat(ESCU.rampa('#d8d2c4', 6, { oscuro: 0.4 }), {});
      m.elipsoide([x + 1.6, y - 3.6, cz + 0.6], [3.4, 4.2, 3], barba, { ruido: [0.4, 1, 3] });
      m.figura(OP.corta(SDF.esfera(x - 0.2, y + 0.4, cz, 4.3), OP.sobre(y + 1)), [x - 6, y, cz - 6, x + 6, y + 6, cz + 6], M.hierro);
      m.caja([x - 4.6, y + 0.6, cz - 4.4], [x + 4.6, y + 1.4, cz + 4.4], M.hierro, { r: 0.4 });
      m.cilindro([x + 3.6, y + 2.6, cz], 1.2, 1.4, ESCU.mat(['#a07010', '#ffe050', '#fff4b0'], { luz: 1, plano: true }), { eje: 'x' });
    },
    mano: (m, [x, y, z]) => { m.capsula([x, y - 2, z], [x - 1, y + 10, z], 0.55, M.maderaOsc); m.figura(SDF.capsula(x - 5, y + 10.6, z, x + 3, y + 10, z, 1, 0.3), [x - 7, y + 8, z - 2, x + 5, y + 13, z + 2], M.hierro); },
  });
  humano('pescadora', {
    piel: ESCU.mat(ESCU.rampa('#a87458', 6, { oscuro: 0.4 }), {}), ropa: ESCU.mat(ESCU.rampa('#3a4a5a', 6), {}), pant: ESCU.mat(ESCU.rampa('#2a3440', 6), {}), bota: O.cuero,
    abrigo: ESCU.mat(ESCU.rampa('#6a6a3a', 6), {}),
    cabeza: (m, x, y, cz) => {
      m.esfera([x - 0.8, y + 0.6, cz], 4, ESCU.mat(ESCU.rampa('#3a2a24', 6), {}), { modo: 'debajo' });
      m.capsula([x - 3, y, cz - 1], [x - 5, y - 5, cz - 1.6], 1.6, 0.8, ESCU.mat(ESCU.rampa('#3a2a24', 6), {}));
      m.cilindro([x, y + 2.4, cz], 7.4, 0.8, ESCU.mat(N.trigo.r, {}), { red: 0.3 });
      m.figura(OP.corta(SDF.esfera(x, y + 2.2, cz, 3.8), OP.sobre(y + 2.6)), [x - 5, y + 2, cz - 5, x + 5, y + 7, cz + 5], ESCU.mat(N.trigo.r, {}));
    },
    mano: (m, [x, y, z]) => { m.capsula([x, y - 1, z], [x + 4, y + 17, z], 0.45, M.maderaV); m.capsula([x + 4, y + 17, z], [x + 7, y + 4, z + 1], 0.15, ESCU.mat(['#a0a0a0', '#e0e0e0'], {})); },
  });
  humano('encapuchado', {
    piel: O.hueco, ropa: ESCU.mat(ESCU.rampa('#2e2238', 6, { oscuro: 0.25 }), {}), pant: ESCU.mat(ESCU.rampa('#221a2a', 6), {}), bota: O.cuero, brazo: 0.5,
    abrigo: ESCU.mat(ESCU.rampa('#2e2238', 6, { oscuro: 0.25 }), {}),
    cabeza: (m, x, y, cz) => {
      m.figura(OP.resta(SDF.capsula(x - 0.6, y + 0.6, cz, x - 2.4, y - 3, cz, 5, 4.6), SDF.elipsoide(x + 3.6, y - 0.6, cz + 1.4, 3.2, 3.4, 3.2)), [x - 9, y - 9, cz - 7, x + 6, y + 7, cz + 7], ESCU.mat(ESCU.rampa('#2e2238', 6, { oscuro: 0.25 }), {}));
      m.esfera([x + 1.8, y - 0.2, cz + 2.4], 2, O.hueco, { modo: 'pintar' });
      for (const dz of [1.6, 3]) m.esfera([x + 2.4, y, cz + dz], 0.5, O.poro);
    },
    mano: (m, [x, y, z]) => m.cilindro([x + 1, y + 0.6, z + 1.4], 1.6, 0.6, MAT.oro, { eje: 'z' }),
  });
});
