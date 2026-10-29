'use strict';
// Las fichas de las criaturas con esqueleto (criaturas-esq.js): cómo es cada una (piezas, paleta de Ásor, tamaño a
// la escala de Core Keeper) y qué animación usa en cada estado. Las caras, máscaras y armas se dibujan aquí con
// primitivas (volumen y luz) y detalles puestos a mano.

const PIEZAS_CRI = (() => {
  const { DEF } = CRI;
  // Rampas de la paleta de Ásor: [línea, sombra, medio, luz(, brillo)]
  const R = {
    gruto: ['#10160f', '#283426', '#435440', '#647a58', '#8ea07a'],
    grutoV: ['#130e1a', '#2e2238', '#4a3a58', '#6c5a7c', '#9482a2'],
    cuero: ['#1c0e08', '#3a2214', '#5a3820', '#7c5430'],
    pielBestia: ['#1a120c', '#3a2a1c', '#5c4430', '#806248', '#a68462'],
    hueso: ['#3e3222', '#7c6a4c', '#b0a07c', '#ddd0aa', '#fbf4dc'],
    madera: ['#1c0f08', '#43271a', '#6a4228', '#946036'],
    trapo: ['#1a0c10', '#3e1a20', '#682a2c', '#8e4434'],
    piedra: ['#1e2130', '#3a3f50', '#5a6072', '#7e8496', '#a8aebc'],
    musgo: ['#0e1a10', '#1e3420', '#34562e', '#527a3a', '#78a04a'],
    metal: ['#16181e', '#363a44', '#5a606c', '#868c98', '#c0c6d0'],
    oxido: ['#200c06', '#4a2010', '#7a3a18', '#a85a28'],
  };
  for (const k of Object.keys(R)) R[k].nombre = k;
  const OSCURO = '#0d0b12';
  const OJO = { ambar: ['#ff9a20', '#fff0a0'], rojo: ['#ff3020', '#ffb070'], frio: ['#7ad8ff', '#e8fbff'], podre: ['#ff3cb4', '#ffb0e0'] };

  // ---------------------------------------------------------------- humanoides (KayKit)
  // Profundidad media de unos huesos (para ordenar las piezas).
  const zDe = (K, ...bs) => bs.reduce((s, b) => s + K.S[b][2], 0) / bs.length;
  // El cuerpo de un humanoide por piezas. o: { piel, pierna: rampa de las piernas, pie, brazo, mano, tronco,
  // r: { muslo, pierna, pie, brazo, antebrazo, mano, cadera, pecho }, costillas, sinPies }
  function cuerpo(K, o) {
    const S = K.S, r = Object.assign({ muslo: 1.5, pierna: 1.25, pie: 1.35, brazo: 1.2, antebrazo: 1.1, mano: 1.35, cadera: 2.6, pecho: 3.2 }, o.r || {});
    const piel = o.piel;
    for (const l of ['l', 'r']) {
      if (o.sinPiernas) break;
      const u = S['upperleg' + l], m = S['lowerleg' + l], p = S['foot' + l], t = S['toes' + l];
      K.pieza((u[2] + m[2]) / 2 - 0.3, () => K.cap(u, m, r.muslo, r.pierna * 1.05, o.pierna || piel));
      K.pieza((m[2] + p[2]) / 2 - 0.2, () => K.cap(m, p, r.pierna, r.pierna * 0.9, o.pierna || piel));
      if (!o.sinPies) K.pieza((p[2] + t[2]) / 2, () => K.cap([p[0], p[1] + 0.6], [t[0], t[1] + 0.2], r.pie, r.pie * 0.85, o.pie || piel));
    }
    for (const l of ['l', 'r']) {
      const u = S['upperarm' + l], m = S['lowerarm' + l], h = S['hand' + l];
      K.pieza((u[2] + m[2]) / 2 + 0.1, () => K.cap(u, m, r.brazo, r.antebrazo, o.brazo || piel));
      K.pieza((m[2] + h[2]) / 2 + 0.2, () => K.cap(m, h, r.antebrazo, r.antebrazo * 0.9, o.antebrazo || o.brazo || piel));
      K.pieza(h[2] + 0.3, () => K.ovalo([h[0], h[1]], r.mano, r.mano, o.mano || piel));
    }
    const hi = S.hips, sp = S.spine, ch = S.chest;
    K.zTronco = zDe(K, 'hips', 'chest') - 0.5;
    K.pieza(K.zTronco, () => {
      K.cap([hi[0], hi[1] - 0.5], sp, r.cadera, (r.cadera + r.pecho) / 2, o.tronco || piel);
      K.cap(sp, [ch[0], ch[1] + 0.5], (r.cadera + r.pecho) / 2, r.pecho, o.tronco || piel);
      if (o.costillas) for (let k = 0; k < 3; k++) { const t = 0.45 + k * 0.16, x = sp[0] + (ch[0] - sp[0]) * t, y = sp[1] + (ch[1] - sp[1]) * t; for (let i = -1; i <= 1; i++) K.px(x + i + 0.5, y + Math.abs(i) * 0.5, (o.tronco || piel)[1], o.tronco || piel); }
    });
  }
  // El arma que lleva en una mano (o.mano 'D' derecha, 'I' izquierda): una vara que pasa por la mano en la dirección
  // del «arriba» de la mano. atras/delante: px del mango por detrás y por delante. o.alzar: cuánto se levanta (las
  // lanzas van en guardia al andar y horizontales al pinchar). Devuelve los extremos (para la punta).
  function vara(K, atras, delante, rampa, o = {}) {
    const I = o.mano === 'I', up = (I ? K.P._arribaI : K.P._arribaD) || [0, 1, 0], al = o.alzar ?? (K.A.alzar ?? 0);
    const h = K.S[I ? 'handslotl' : 'handslotr'] || K.S[I ? 'handl' : 'handr'], d = o.dir || K.dir([up[0], up[1] + al, up[2]]);
    const a = [h[0] - d[0] * atras, h[1] - d[1] * atras], b = [h[0] + d[0] * delante, h[1] + d[1] * delante];
    const z = h[2] + (o.z ?? 0.25);
    if (rampa) K.pieza(z, () => K.vara(a, b, rampa, { grosor: o.grosor || 1 }));
    return { a, b, d, z, h, n: [-d[1], d[0]] };
  }

  // ---------------------------------------------------------------- los grutos
  // Flacos, gris verdoso, encorvados; máscara de cráneo de animal con ojos ámbar (LORE.md §5).
  const PROP_GRUTO = { pierna: 0.92, brazo: 1.05, hombros: 0.78, caderas: 0.75, encorvar: 0.3, cuello: 0.62, cadera: 0.39 };
  // Cabeza de gruto con máscara de cráneo, mirando a la derecha (3/4). c: el hueso de la cabeza en px. o: { e
  // (escala), hueso, capucha (rampa o 'hojas'), cuernos ('cabra' | 'toro'), pez, mandibula (rampa de hierro), ojo }
  function mascara(K, c, o = {}) {
    const hu = o.hueso || R.hueso, e = o.e || 1, x = c[0] + 1 * e, y = c[1] - 3.5 * e, P = (dx, dy) => [x + dx * e, y + dy * e];
    const ojo = o.ojo || OJO.ambar;
    // lo de detrás: la capucha (de piel o de hojas) y los cuernos
    K.pieza(c[2] + 1.5, () => {
      if (o.capucha === 'hojas') {
        for (const [dx, dy, a] of [[-3.5, -2.5, -0.6], [-4.5, 0.5, 0.2], [-1, -4.2, -1.2], [-3.8, 3, 0.8], [1.6, -4.2, -1.9]]) K.ovalo(P(dx, dy), 2.6 * e, 1.5 * e, R.musgo, { ang: a });
      } else K.ovalo(P(-2, -0.3), 3.9 * e, 3.8 * e, o.capucha || R.pielBestia);
      if (o.cuernos === 'cabra') { K.cap(P(-2.5, -3), P(-5.5, -7.5), 1.3 * e, 0.6 * e, hu); K.cap(P(0.5, -3.5), P(1.5, -8.5), 1.3 * e, 0.6 * e, hu); }
      if (o.cuernos === 'toro') { K.cap(P(-2, -2.2), P(-5.5, -3.2), 1.2 * e, 0.9 * e, hu); K.cap(P(-5.5, -3.2), P(-6.2, -6), 0.9 * e, 0.4 * e, hu); }
    });
    K.pieza(c[2] + 2, () => {
      if (o.pez) { // cráneo de pez: largo y plano, con la cresta de la aleta
        K.poli([P(-2, -3), P(1, -6.5), P(2.5, -3.2)], R.hueso, { normal: () => [-0.4, -0.6] });
        K.ovalo(P(0, 0), 3.4 * e, 3.0 * e, hu);
        K.cap(P(1.5, 1.0), P(5.6, 1.6), 1.7 * e, 1.1 * e, hu);
        for (let i = 0; i < 4; i++) K.px(x + (2 + i * 1.1) * e, y + 2.8 * e, i % 2 ? hu[4] : hu[3], hu);
      } else {
        K.ovalo(P(0, 0), 3.6 * e, 3.3 * e, hu); // el cráneo
        K.cap(P(1.5, 1.2), P(4.6, 2.3), 2.0 * e, 1.45 * e, hu); // el hocico
        K.px(x + 5.2 * e, y + 1.5 * e, OSCURO, hu);
        if (!o.mandibula) for (let i = 0; i < 3 * e; i++) K.px(x + 2 * e + i * 1.1, y + 3.6 * e, i % 2 ? hu[4] : hu[3], hu);
      }
      // cuencas y ojos (brillan)
      const ce = Math.max(2, Math.round(2 * e));
      for (const [ex, ey] of [[-1.4, -0.6], [1.6, -0.4]]) {
        const ox = Math.round(x + ex * e), oy = Math.round(y + ey * e);
        for (let j = 0; j < ce; j++) for (let i = 0; i < ce; i++) K.px(ox + i, oy + j, OSCURO, hu);
        K.px(ox + ce - 1, oy, ojo[0], hu, true); K.px(ox + (ex < 0 ? 0 : ce - 1), oy + ce - 1, ojo[1], hu, true);
        if (ce > 2) K.px(ox + 1, oy + 1, ojo[0], hu, true);
      }
      K.px(x - 1.6 * e, y - 2.2 * e, hu[4], hu); K.px(x - 0.6 * e, y - 2.6 * e, hu[4], hu); // brillo arriba a la izquierda
      if (o.grieta) for (let k = 0; k < 3; k++) K.px(x + (-0.5 + k * 0.5) * e, y + (-2.4 + k * 0.6) * e, hu[1], hu);
      if (o.mandibula) { // mandíbula de hierro remachada
        const m = o.mandibula;
        K.poli([P(-1, 2.2), P(5.4, 2.6), P(5, 4.6), P(0, 4.8)], m);
        for (let i = 0; i < 3; i++) K.px(x + (0.8 + i * 1.8) * e, y + 3.5 * e, m[4] || m[3], m);
        for (let i = 0; i < 4; i++) K.px(x + (1 + i * 1.3) * e, y + 2.2 * e, i % 2 ? hu[4] : hu[3], hu);
      }
    });
    if (o.cuernos === 'toro') K.pieza(c[2] + 2.2, () => { K.cap(P(1.5, -2.6), P(4.6, -3.6), 1.2 * e, 0.9 * e, hu); K.cap(P(4.6, -3.6), P(5.6, -6.4), 0.9 * e, 0.4 * e, hu); });
    K.puntos = Object.assign(K.puntos || {}, { cabeza: [x, y] });
  }
  // La base de todos los grutos: el cuerpo, la máscara, el taparrabos y lo que lleva cada uno.
  function gruto(o) {
    return {
      W: o.W || 56, H: o.H || 48, ox: o.ox || 24, oy: o.oy || 44, px: o.px || 13, yaw: 1.05, fuente: ANIM_ESQUELETO, prop: Object.assign({}, PROP_GRUTO, o.prop || {}),
      anims: o.anims,
      dibujar(K) {
        const piel = o.piel || R.gruto, e = (o.mascara && o.mascara.e) || 1;
        cuerpo(K, { piel, costillas: !o.barriga, pie: R.cuero, r: o.r, tronco: o.tronco });
        const hi = K.S.hips, ch = K.S.chest;
        if (o.barriga) K.pieza(K.zTronco + 0.2, () => { // la barriga cosida del Mole
          K.ovalo([(hi[0] + ch[0]) / 2 + 2 * e, (hi[1] + ch[1]) / 2 + 1], 4.6 * e, 4.4 * e, piel);
          for (let k = 0; k < 6; k++) { const yy = (hi[1] + ch[1]) / 2 - 3 * e + k * e * 1.2, xx = (hi[0] + ch[0]) / 2 + 3 * e; K.px(xx, yy, piel[0], piel); K.px(xx - 1, yy, k % 2 ? R.hueso[2] : piel[0], piel); K.px(xx + 1, yy, k % 2 ? R.hueso[2] : piel[0], piel); }
        });
        // taparrabos de piel
        K.pieza(K.zTronco + 0.3, () => {
          const t = e;
          K.poli([[hi[0] - 3 * t, hi[1] - 1], [hi[0] + 3 * t, hi[1] - 1], [hi[0] + 3.2 * t, hi[1] + 2 * t], [hi[0] + 1 * t, hi[1] + 3.5 * t], [hi[0] - 1.5 * t, hi[1] + 2.5 * t], [hi[0] - 3.2 * t, hi[1] + 2 * t]], o.ropa || R.cuero);
          for (let i = -3 * t; i <= 3 * t; i++) K.px(hi[0] + i, hi[1] - 1, R.cuero[i < 0 ? 3 : 2], R.cuero);
        });
        // capa (de pieles o de musgo) sobre los hombros
        if (o.capa) K.pieza(K.zTronco - 0.6, () => {
          K.poli([[ch[0] - 4 * e, ch[1] - 1], [ch[0] + 2 * e, ch[1] - 1.5], [ch[0] + 0.5 * e, ch[1] + 6 * e], [hi[0] - 2 * e, hi[1] + 3 * e], [hi[0] - 5.5 * e, hi[1] + 1 * e]], o.capa, {
            corta: (x, y) => y > hi[1] + 1 && (x * 7 + y * 3) % 5 === 0,
          });
        });
        // collar de dientes
        K.pieza(K.zTronco + 0.6, () => { for (let i = -2; i <= 2; i++) K.px(ch[0] + (i + 0.5) * e, ch[1] + 1 + Math.abs(i) * 0.4, i % 2 ? R.hueso[3] : R.hueso[2], R.hueso); });
        mascara(K, K.S.head, o.mascara || {});
        if (o.extra) o.extra(K);
      },
    };
  }
  // Anims comunes de los grutos (los golpes los pone cada uno).
  const ANDAR_GRUTO = { q: { clip: 'Idle', n: 4, bucle: true }, a: { clip: 'Walking_C', n: 8, bucle: true }, r: { clip: 'Hit_A', n: 3, de: 0.1, a: 0.6 }, m: { clip: 'Death_A', n: 6, raiz: 0.4 }, n: { clip: 'Skeletons_Awaken_Floor', n: 6, de: 0.3, a: 0.75, raiz: 0.2 } };
  const con = (base, extra, alzar) => { const r = {}; for (const [k, v] of Object.entries(Object.assign({}, base, extra))) r[k] = Object.assign({ alzar: alzar[k] ?? alzar._ }, v); return r; };
  // punta de lanza (o de arpón con lengüeta) en el extremo b de una vara
  function punta(K, v, o = {}) {
    K.pieza(v.z + 0.01, () => {
      const b = v.b, d = v.d, n = v.n;
      K.poli([[b[0] - n[0] * 1.6, b[1] - n[1] * 1.6], [b[0] + d[0] * 5, b[1] + d[1] * 5], [b[0] + n[0] * 1.6, b[1] + n[1] * 1.6]], o.rampa || R.hueso, { normal: () => [n[0] * 0.3 - 0.4, n[1] * 0.3 - 0.4] });
      if (o.lengueta) { const q = [b[0] + n[0] * 1.5 - d[0] * 0.5, b[1] + n[1] * 1.5 - d[1] * 0.5]; K.px(q[0] - d[0], q[1] - d[1], (o.rampa || R.hueso)[2], o.rampa || R.hueso); K.px(q[0] + n[0] * 0.8 - d[0] * 2, q[1] + n[1] * 0.8 - d[1] * 2, (o.rampa || R.hueso)[3], o.rampa || R.hueso); }
      const t = [b[0] - d[0] * 2.5, b[1] - d[1] * 2.5], tr = o.trapo || R.trapo;
      K.px(t[0], t[1], tr[2], tr); K.px(t[0] + 1, t[1] + 1, tr[1], tr); K.px(t[0], t[1] + 2, tr[2], tr);
    });
  }

  DEF.gruto_lancero = gruto({
    anims: con(ANDAR_GRUTO, {
      p: { clip: '1H_Melee_Attack_Stab', n: 3, de: 0, a: 0.2 },
      g: { clip: '1H_Melee_Attack_Stab', n: 4, de: 0.25, a: 0.6, alzar: 0.05 },
    }, { _: 0.5, m: 0.6, n: 1.5 }),
    extra(K) { punta(K, vara(K, 7, 15, R.madera)); },
  });

  DEF.gruto_fango = gruto({
    piel: ['#14160c', '#2e3320', '#4a5232', '#6c7446', '#949a62'],
    ropa: ['#10140e', '#22301e', '#3a4a2c', '#56683a'],
    mascara: { pez: true, capucha: ['#10140e', '#22301e', '#3a4a2c', '#56683a'] },
    anims: con(ANDAR_GRUTO, {
      p: { clip: '1H_Melee_Attack_Stab', n: 3, de: 0, a: 0.2 },
      g: { clip: '1H_Melee_Attack_Stab', n: 4, de: 0.25, a: 0.6, alzar: 0.05 },
    }, { _: 0.5, m: 0.6, n: 1.5 }),
    extra(K) {
      punta(K, vara(K, 7, 16, R.madera), { rampa: R.metal, lengueta: true, trapo: ['#10140e', '#22301e', '#3a4a2c', '#56683a'] });
      // la red enrollada al hombro (detrás)
      const ch = K.S.chest;
      K.pieza(K.zTronco - 0.8, () => { K.ovalo([ch[0] - 3, ch[1] + 2], 3, 2.4, ['#1a1610', '#3a3224', '#5e5238', '#847452'], { tex: (x, y) => ((x + y) % 3 === 0 ? -0.5 : 0) }); });
    },
  });

  DEF.gruto_chaman = gruto({
    piel: R.grutoV,
    capa: R.pielBestia,
    mascara: { cuernos: 'cabra', capucha: R.pielBestia, grieta: true },
    anims: con(ANDAR_GRUTO, {
      p: { clip: 'Spellcast_Shoot', n: 4, de: 0, a: 0.4 },
      g: { clip: 'Spellcast_Shoot', n: 3, de: 0.4, a: 0.9 },
    }, { _: 3 }),
    extra(K) {
      // bastón en la izquierda (detrás del cuerpo) con la Podre enjaulada arriba
      const v = vara(K, 9, 12, R.madera, { mano: 'I', z: -0.4 });
      K.pieza(v.z + 0.01, () => {
        const b = v.b;
        K.ovalo([b[0], b[1] - 1], 2.1, 2.1, OJO.podre.length ? ['#3a0f30', '#a01e78', '#ff3cb4', '#ff7ad0', '#ffd0ee'] : R.trapo, { luz: true });
        for (const dx of [-2, 0, 2]) K.px(b[0] + dx, b[1] - 1 + (dx ? 0 : -2), R.madera[0], R.madera);
        K.px(b[0], b[1] + 1.5, R.madera[1], R.madera);
      });
      K.puntos = Object.assign(K.puntos || {}, { orbe: v.b });
    },
  });

  DEF.gruto_jardinero = gruto({
    piel: ['#121a10', '#26381e', '#3e5a2c', '#5e7c3c', '#86a052'],
    mascara: { capucha: 'hojas' },
    anims: con(ANDAR_GRUTO, {
      p: { clip: 'Throw', n: 4, de: 0, a: 0.48 },
      g: { clip: 'Throw', n: 3, de: 0.5, a: 0.85 },
    }, { _: 1 }),
    extra(K) {
      // el saco de semillas de Podre a la espalda
      const ch = K.S.chest;
      K.pieza(K.zTronco - 0.9, () => {
        K.ovalo([ch[0] - 4, ch[1] + 3], 3.4, 3.8, R.cuero);
        K.px(ch[0] - 5, ch[1] + 2, '#ff3cb4', R.cuero, true); K.px(ch[0] - 3, ch[1] + 4, '#ff7ad0', R.cuero, true);
        K.cap([ch[0] - 4, ch[1] - 1], [ch[0] - 3.5, ch[1] - 2.5], 1, 0.8, R.cuero);
      });
      // la semilla en la mano mientras prepara el tiro
      if (K.anim === 'p') { const h = K.S.handr; K.pieza(h[2] + 0.5, () => K.ovalo([h[0], h[1] - 1], 1.4, 1.4, ['#3a0f30', '#a01e78', '#ff3cb4', '#ff9ad8'], { luz: true })); }
    },
  });

  DEF.gruto_cazador = gruto({
    piel: ['#121610', '#283222', '#404e34', '#5c6c48', '#7e8c64'],
    capa: R.musgo,
    mascara: { capucha: 'hojas' },
    anims: con(ANDAR_GRUTO, {
      p: { clip: '2H_Ranged_Shoot', n: 4, de: 0, a: 0.35 },
      g: { clip: '2H_Ranged_Shoot', n: 2, de: 0.4, a: 0.6 },
    }, { _: 0 }),
    extra(K) {
      // el arco de hueso en la izquierda (derecho, curvado hacia delante) y la cuerda hasta la derecha si tensa
      const hl = K.S.handl, hr = K.S.handr, tensa = K.anim === 'p' || K.anim === 'g';
      const cx = hl[0] + (tensa ? 2 : 0.5), cy = hl[1] - (tensa ? 1 : 0);
      const z = Math.max(hl[2], K.zTronco) + 0.5;
      K.pieza(z, () => {
        const pts = [];
        for (let k = 0; k <= 10; k++) { const t = (k / 10) * 2 - 1; pts.push([cx + (1 - t * t) * 2.6, cy + t * 8]); }
        for (let k = 0; k < 10; k++) K.vara(pts[k], pts[k + 1], R.hueso, { k: 2 + (k < 5 ? 1 : 0) });
        const tira = tensa ? [hr[0] - 1, hr[1]] : [cx, cy];
        K.vara([cx, cy - 8], tira, ['#2a2620', '#6a6458', '#a8a090', '#d8d0c0'], { k: 2 });
        K.vara(tira, [cx, cy + 8], ['#2a2620', '#6a6458', '#a8a090', '#d8d0c0'], { k: 2 });
        if (tensa) K.vara([hr[0] - 1, hr[1]], [cx + 5, cy], R.madera, { k: 3 });
      });
    },
  });

  DEF.gruto_bruto = gruto({
    W: 84, H: 72, ox: 38, oy: 66, px: 22,
    piel: ['#121a0c', '#2a3a1c', '#46582c', '#68783e', '#8c9a5a'],
    barriga: true,
    tronco: ['#1a120c', '#3a2a1c', '#5c4430', '#806248', '#a68462'],
    prop: { pierna: 0.8, brazo: 1.0, hombros: 1.25, caderas: 1.1, encorvar: 0.38, cuello: 0.45 },
    r: { muslo: 3.4, pierna: 2.8, pie: 2.8, brazo: 3.0, antebrazo: 2.8, mano: 3.0, cadera: 6, pecho: 7.6 },
    mascara: { e: 1.7, cuernos: 'toro', mandibula: R.metal, capucha: R.pielBestia },
    anims: con(ANDAR_GRUTO, {
      a: { clip: 'Walking_D_Skeletons', n: 8, bucle: true },
      p: { clip: '2H_Melee_Attack_Chop', n: 4, de: 0, a: 0.38 },
      g: { clip: '2H_Melee_Attack_Chop', n: 4, de: 0.42, a: 0.75 },
      r: { clip: 'Hit_B', n: 3, de: 0.1, a: 0.6 },
    }, { _: 0 }),
    extra(K) {
      // la cuchilla oxidada: mango corto y una hoja ancha
      const v = vara(K, 3, 7, R.madera, { grosor: 2 });
      K.pieza(v.z + 0.02, () => {
        const b = v.b, d = v.d, n = v.n, L = 15, A = 6;
        const p0 = [b[0] - n[0] * 1, b[1] - n[1] * 1], p1 = [b[0] + d[0] * L - n[0] * 1, b[1] + d[1] * L - n[1] * 1];
        const p2 = [b[0] + d[0] * (L + 1) + n[0] * A, b[1] + d[1] * (L + 1) + n[1] * A], p3 = [b[0] + n[0] * (A - 1), b[1] + n[1] * (A - 1)];
        K.poli([p0, p1, p2, p3], R.oxido, { normal: (x, y) => [n[0] * 0.2 - 0.3, n[1] * 0.2 - 0.3] });
        K.vara(p2, p3, R.metal, { k: 4 }); // el filo, brillante
        const ag = [b[0] + d[0] * 4 + n[0] * 2, b[1] + d[1] * 4 + n[1] * 2]; K.px(ag[0], ag[1], R.oxido[0], R.oxido); // el agujero
      });
    },
  });

  // ---------------------------------------------------------------- las babas (movimiento del Slime de Quaternius)
  // Una gota con el borde de arriba claro y el de abajo oscuro, brillo arriba a la izquierda, por dentro lo que se ha
  // tragado (huesos, un cráneo) visto a través y los ojillos flotando. Se estira y se aplasta con el Slime: quieta
  // respira, al andar da saltitos, antes de saltar se agacha, en el aire se estira y al morir se derrite.
  const mezclar = (a, b, k) => ESCU.mezclar(a, b, k);
  function aplasto(K) { // [alto, ancho, inclinación] según la animación
    const P = K.P, A = K.A;
    if (A.proc === 'agacha') { const k = Math.sin(K.u * Math.PI * 0.5); return [1 - 0.28 * k, 1 + 0.2 * k, -0.15 * k]; }
    if (A.proc === 'golpe') return [K.f ? 0.86 : 0.78, K.f ? 1.1 : 1.18, -0.25];
    if (!P.Head) return [1, 1, 0];
    if (A.clip === 'Slime_Attack') { const s = (P.Head[1] - P.Body[1]) / 0.85; return [s, 1 / Math.sqrt(s), (P.Head[0] - P.Body[0]) * 0.6]; }
    const s = P.Head[1] / 1.26;
    return [s, 1 / Math.sqrt(Math.max(0.3, s)) * (A.clip === 'Slime_Death' ? 1.25 : 1), (P.Head[0] - P.Body[0] - 0.03) * 1.5];
  }
  // o: { rampa (5 tonos), rx, ry, dentro: 'craneo' | 'visceras' | 'pustulas', ojo, W, H }
  function baba(o) {
    const W = o.W || 36, H = o.H || 30;
    return {
      W, H, ox: Math.round(W / 2), oy: H - 4, px: 1, yaw: 0, fuente: ANIM_BABA, zancada: 16, fpsQuieta: 4,
      anims: {
        q: { clip: 'Slime_Idle', n: 4, bucle: true }, a: { clip: 'Slime_Walk', n: 6, bucle: true },
        p: { proc: 'agacha', n: 3 }, g: { clip: 'Slime_Attack', n: 3, de: 0.3, a: 0.6 },
        r: { proc: 'golpe', n: 2 }, m: { clip: 'Slime_Death', n: 5 },
      },
      dibujar(K) {
        const D = K.D, [sy, sx, inc] = aplasto(K), rx = o.rx * sx, ry = o.ry * sy, ox = D.ox, oy = D.oy;
        const rampa = Object.assign(o.rampa.slice(), { arriba: 3, sinLinea: true }), cx = ox, cy = oy + 0.5 - ry * 0.8;
        const sesgo = (y) => inc * (cy - y) * 0.6; // la parte de arriba se va hacia donde mira
        const enBaba = (x, y) => { const u = (x + 0.5 - cx - sesgo(y + 0.5)) / rx, v = (y + 0.5 - cy) / ry; return u * u + v * v <= 1 && y <= oy; };
        K.pieza(0, () => {
          for (let y = Math.floor(cy - ry - 1); y <= oy; y++) for (let x = Math.floor(cx - rx - 3); x <= cx + rx + 3; x++) {
            if (!enBaba(x, y)) continue;
            const u = (x + 0.5 - cx - sesgo(y + 0.5)) / rx, v = (y + 0.5 - cy) / ry, nz = Math.sqrt(Math.max(0, 1 - u * u - v * v));
            // por dentro, abajo, más oscuro (lo que lleva dentro, visto a través)
            let I = u * CRI.LUZ[0] + v * CRI.LUZ[1] + nz * CRI.LUZ[2];
            if (v > 0.25 && Math.abs(u) < 0.75) I -= 0.35 * (v - 0.25) * 2;
            const k = I > 0.88 ? 4 : I > 0.5 ? 3 : I > 0.08 ? 2 : 1;
            K.L.pon(x, y, rampa[k], K.id, rampa, false, u, v, nz);
          }
        });
        // lo que se ha tragado
        const tinte = (c) => mezclar(c, rampa[2], 0.45);
        K.pieza(1, () => {
          const bx = cx - rx * 0.25, by = oy - ry * 0.55;
          if (o.dentro === 'craneo' && ry > 3) {
            const hu = [R.hueso[0], tinte(R.hueso[1]), tinte(R.hueso[2]), tinte(R.hueso[3])];
            K.ovalo([bx, by], 2.6 * sx, 2.2 * Math.min(1, sy), hu, { mate: true });
            K.px(bx - 1, by, tinte(OSCURO), rampa); K.px(bx + 1, by, tinte(OSCURO), rampa);
            K.vara([bx + 3 * sx, by + 2], [bx + 7 * sx, by + 0.5], [0, 0, tinte(R.hueso[3])], { k: 2 });
          } else if (o.dentro === 'visceras' && ry > 3) {
            K.ovalo([bx, by + 0.5], 3 * sx, 1.6, [0, tinte('#5a0a18'), tinte('#8a1428'), tinte('#c03040')], { mate: true });
            K.vara([bx + 2, by - 1], [bx + 6 * sx, by + 1], [0, 0, tinte('#e8c0b0')], { k: 2 });
          }
        });
        K.pieza(2, () => {
          // ojillos (brillan)
          if (ry > 2.5) for (const [ex, ey] of [[0.32, -0.25], [0.66, -0.18]]) {
            const x = cx + rx * ex + sesgo(cy + ry * ey), y = cy + ry * ey;
            K.px(x, y, (o.ojo || OJO.rojo)[0], rampa, true); K.px(x, y - 1, mezclar((o.ojo || OJO.rojo)[0], rampa[1], 0.5), rampa);
          }
          // brillo arriba a la izquierda, en cruz
          if (ry > 3) { const x = cx - rx * 0.45 + sesgo(cy - ry * 0.5), y = cy - ry * 0.5; K.px(x, y, rampa[4], rampa); K.px(x + 1, y, rampa[4], rampa); K.px(x, y - 1, rampa[4], rampa); K.px(x, y + 1, rampa[3], rampa); }
          if (o.dentro === 'pustulas') for (const [px, py, r] of [[-0.4, -0.75, 1.6], [0.15, -0.9, 1.3], [0.55, -0.6, 1.2], [-0.75, -0.25, 1.1]]) {
            const x = cx + rx * px + sesgo(cy + ry * py), y = cy + ry * py;
            K.ovalo([x, y], r, r * Math.min(1.2, sy), ['#3a0f30', '#a01e78', '#ff3cb4', '#ff7ad0', '#ffd0ee'], { luz: true });
          }
        });
      },
    };
  }
  DEF.babosa = baba({ rampa: ['#33400c', '#6a7a16', '#a0b028', '#d0dc4c', '#f4fca0'], rx: 8.5, ry: 6.8, dentro: 'craneo' });
  DEF.babosa_roja = baba({ rampa: ['#2e0610', '#62101e', '#a01c2c', '#d83a3a', '#ff8a6c'], rx: 9.5, ry: 7.6, dentro: 'visceras', ojo: ['#ffe060', '#fff8c0'] });
  DEF.babosa_veneno = baba({ rampa: ['#180a20', '#36183e', '#5a2864', '#84388a', '#b45cb0'], rx: 9.5, ry: 7.2, dentro: 'pustulas', ojo: ['#c8ff40', '#f0ffb0'] });

  // ---------------------------------------------------------------- gusanos, sanguijuela y tentáculo (esqueleto propio)
  // Una cadena de anillos (de la cola a la cabeza): cada anillo es una pieza (las líneas entre anillos salen solas).
  const PODRE = ['#3a0f30', '#a01e78', '#ff3cb4', '#ff7ad0', '#ffd0ee'];
  const ACIDO = ['#1a3a10', '#3a8a1a', '#7ad83a', '#c0ff70', '#f0ffc0'];
  const fase = (K) => (K.A.bucle ? K.u : Math.min(1, K.u));
  // Los anillos de un gusano en el suelo, mirando a la derecha. o: { n, r (radio de la cabeza), paso }
  function anillosGusano(K, o) {
    const D = K.D, A = K.A, u = fase(K), n = o.n, pts = [];
    let alza = 0, abre = 0, enc = 1, sale = 1, cae = 0;
    if (A.proc === 'alza') { alza = Math.sin(Math.min(1, u * 1.2) * Math.PI / 2); abre = u; }
    if (A.proc === 'muerde') { alza = 1 - u * 0.7; abre = 1 - u; enc = 1 + 0.25 * Math.sin(u * Math.PI); }
    if (A.proc === 'golpe') { alza = 0.25 * (1 - u); enc = 0.85; }
    if (A.proc === 'muere') { cae = u; }
    if (A.proc === 'sale') sale = u;
    for (let i = 0; i < n; i++) {
      const t = i / (n - 1); // 0 cola, 1 cabeza
      let r = o.r * (0.55 + 0.45 * Math.sin(Math.min(1, t * 1.15) * Math.PI * 0.5)) * (o.gordo ? 1 + 0.25 * Math.sin(t * Math.PI) : 1);
      // la onda al reptar: los anillos se juntan y se separan (como una oruga) y suben un poco
      const ola = A.proc === 'repta' ? Math.sin((u - t * 0.8) * Math.PI * 2) : A.proc === 'quieta' ? Math.sin((u - t) * Math.PI * 2) * 0.35 : 0;
      let x = D.ox - (n - 1 - i) * o.paso * enc * (1 + 0.18 * ola) + o.paso * (n - 1) * 0.45;
      let y = D.oy + 0.5 - r - Math.max(0, ola) * 1.2;
      // alzarse: la parte de delante se levanta y se echa atrás
      const k = Math.max(0, (t - 0.45) / 0.55);
      if (alza > 0) { y -= alza * k * k * o.r * 3.2; x -= alza * k * k * o.paso * 1.4; }
      if (cae > 0) { y = D.oy + 0.5 - r * (1 - cae * 0.45) + Math.sin(t * 9 + cae * 3) * cae; r *= 1 - cae * 0.3; }
      if (sale < 1) { y += (1 - sale) * (o.r * 2 + (1 - t) * 4); }
      pts.push([x, y, r]);
    }
    return { pts, abre };
  }
  // o: { piel (rampa), n, r, paso, puntos: 'acido' | 'podre', venas, boca: 'dientes' | 'ventosa' }
  function gusano(o) {
    return {
      W: o.W || 44, H: o.H || 34, ox: o.ox || 22, oy: (o.H || 34) - 4, px: 1, zancada: o.zancada || 14, fpsQuieta: 3,
      anims: {
        q: { proc: 'quieta', n: 4, bucle: true }, a: { proc: 'repta', n: 6, bucle: true },
        p: { proc: 'alza', n: 4 }, g: { proc: 'muerde', n: 3 }, r: { proc: 'golpe', n: 2 }, m: { proc: 'muere', n: 5 }, n: { proc: 'sale', n: 5 },
      },
      dibujar(K) {
        const { pts, abre } = anillosGusano(K, o), piel = o.piel, D = K.D;
        pts.forEach(([x, y, r], i) => K.pieza(i, () => {
          K.ovalo([x, y], r, r * 0.92, piel, { corta: (xx, yy) => yy > D.oy });
          if (o.venas && i % 2 === 0 && r > 2) K.px(x - r * 0.3, y - r * 0.2, o.venas, piel);
          if (o.puntos && i % 2 === 1 && r > 2) { const c = o.puntos === 'acido' ? ACIDO : PODRE; K.px(x, y + r * 0.2, c[2], piel, true); if (r > 3.5) K.px(x + 1, y - r * 0.3, c[3], piel, true); }
        }));
        // la cabeza: boca redonda (se abre al alzarse) con dientes alrededor
        const [hx, hy, hr] = pts[pts.length - 1];
        K.pieza(pts.length + 1, () => {
          const bx = hx + hr * 0.55, by = hy + hr * 0.1, br = Math.max(1, hr * (0.35 + 0.45 * abre));
          K.ovalo([bx, by], br * 0.7, br, ['#1a0610', '#2a0a14', '#4a0e1c', '#6a1424'], { mate: true });
          const nd = Math.max(5, Math.round(br * 3));
          for (let k = 0; k < nd; k++) { const a = (k / nd) * Math.PI * 2; K.px(bx + Math.cos(a) * br * 0.7, by + Math.sin(a) * br, R.hueso[k % 2 ? 3 : 4], R.hueso); }
          if (o.boca === 'ventosa') for (let k = 0; k < 6; k++) { const a = (k / 6) * Math.PI * 2; K.px(bx + Math.cos(a) * (br * 0.7 + 1), by + Math.sin(a) * (br + 1), piel[3], piel); }
        });
        K.puntos = { boca: [hx + hr, hy] };
      },
    };
  }
  DEF.larva = gusano({ piel: ['#4a3a30', '#a88e78', '#d2bea2', '#efe2c4', '#fffaee'], n: 6, r: 4.4, paso: 3.4, venas: '#b0505a' });
  DEF.larva_moho = gusano({ piel: ['#0e0a12', '#221a26', '#3a2e3e', '#564658', '#786478'], n: 6, r: 4.4, paso: 3.4, puntos: 'podre' });
  DEF.larva_grande = gusano({ W: 56, H: 40, ox: 28, piel: ['#4a3a20', '#9a8458', '#c8b47c', '#e8dcac', '#fbf6dc'], n: 7, r: 6.5, paso: 4.6, gordo: true, puntos: 'acido', venas: '#7a8a3a', zancada: 20 });
  DEF.sanguijuela = gusano({ W: 56, H: 34, ox: 28, piel: ['#080a08', '#161c16', '#26302a', '#3a4840', '#56685c'], n: 8, r: 4.6, paso: 3.8, gordo: true, boca: 'ventosa', zancada: 22 });

  // Tentáculo de la Podre: sale de una pústula del suelo, se mece y se echa atrás antes de golpear donde estás.
  DEF.tentaculo_moho = {
    W: 40, H: 50, ox: 20, oy: 46, px: 1, fpsQuieta: 3,
    anims: { e: { proc: 'escondido', n: 3, bucle: true }, q: { proc: 'mece', n: 6, bucle: true }, p: { proc: 'atras', n: 4 }, g: { proc: 'golpe', n: 3 }, r: { proc: 'duele', n: 2 }, m: { proc: 'muere', n: 5 }, n: { proc: 'sale', n: 5 } },
    dibujar(K) {
      const D = K.D, A = K.A, u = fase(K), N = 9, base = [D.ox, D.oy - 1], piel = ['#06050a', '#14101c', '#241c30', '#382c48', '#544264'];
      let largo = 1, curva = 0, ola = 0;
      if (A.proc === 'escondido') largo = 0;
      if (A.proc === 'mece') { curva = Math.sin(u * Math.PI * 2) * 0.5; ola = u; }
      if (A.proc === 'atras') { curva = -1.1 * u; largo = 1 + 0.1 * u; }
      if (A.proc === 'golpe') { curva = -1.1 + 2.6 * Math.min(1, u * 1.6); largo = 1.1 - 0.35 * u; }
      if (A.proc === 'duele') { curva = -0.4; largo = 0.9; }
      if (A.proc === 'muere') { largo = 1 - u; curva = 0.8 * u; }
      if (A.proc === 'sale') largo = u;
      // la pústula del suelo
      K.pieza(-1, () => {
        K.ovalo([base[0], base[1] - 1], 7, 4, ['#140a18', '#2e1636', '#4a2052', '#6a2e70'], { corta: (x, y) => y > D.oy });
        for (const [dx, dy] of [[-4, -2], [3, -3], [5, -1]]) K.px(base[0] + dx, base[1] + dy, PODRE[2], piel, true);
        if (A.proc === 'escondido') { const k = Math.sin(u * Math.PI * 2) * 0.5 + 0.5; K.ovalo([base[0], base[1] - 2], 2 + k, 1.5 + k * 0.5, PODRE, { luz: true }); }
      });
      if (largo <= 0.02) return;
      let x = base[0], y = base[1] - 2, a = -Math.PI / 2;
      const pts = [];
      for (let i = 0; i < N; i++) {
        const t = i / (N - 1), r = 4 * (1 - t * 0.75);
        pts.push([x, y, r]);
        a += (curva * 0.22 + Math.sin(ola * Math.PI * 2 + t * 4) * 0.08) * (0.5 + t);
        x += Math.cos(a) * 3.6 * largo; y += Math.sin(a) * 3.6 * largo;
      }
      pts.forEach(([px, py, r], i) => K.pieza(i, () => {
        K.ovalo([px, py], r, r, piel);
        if (i % 2 === 1 && r > 1.6) { K.px(px + r * 0.2, py, PODRE[3], piel, true); K.px(px + r * 0.2 - 1, py, '#2a0a20', piel); }
      }));
      K.puntos = { punta: pts[N - 1] };
    },
  };

  // Fauce: un tallo con una boca vertical de dos filas de dientes que babea; escondida entre las hojas.
  DEF.planta_trampa = {
    W: 40, H: 46, ox: 20, oy: 42, px: 1, fpsQuieta: 3,
    anims: { e: { proc: 'escondida', n: 2, bucle: true }, q: { proc: 'mece', n: 4, bucle: true }, p: { proc: 'abre', n: 4 }, g: { proc: 'muerde', n: 3 }, r: { proc: 'duele', n: 2 }, m: { proc: 'muere', n: 5 }, n: { proc: 'sale', n: 4 } },
    dibujar(K) {
      const D = K.D, A = K.A, u = fase(K), hoja = R.musgo, tallo = ['#0e1a0c', '#1e3a1a', '#346028', '#4e8434'];
      const carne = ['#2a0812', '#5e1424', '#a02a3c', '#d04a5a', '#f08080'];
      let alto = 1, abre = 0.1, inc = 0;
      if (A.proc === 'escondida') alto = 0;
      if (A.proc === 'mece') inc = Math.sin(u * Math.PI * 2) * 0.12;
      if (A.proc === 'abre') { abre = 0.1 + 0.9 * u; inc = -0.25 * u; alto = 1 + 0.1 * u; }
      if (A.proc === 'muerde') { abre = 1 - u; inc = -0.25 + 0.6 * Math.min(1, u * 2); }
      if (A.proc === 'duele') { inc = -0.3; abre = 0.4; }
      if (A.proc === 'muere') { alto = 1 - u * 0.7; inc = 0.9 * u; abre = 0.5; }
      if (A.proc === 'sale') alto = u;
      // hojas del suelo
      K.pieza(-2, () => { for (const [dx, a] of [[-6, 0.25], [6, -0.25], [-3, 0.7], [4, -0.6]]) K.ovalo([D.ox + dx, D.oy - 1.5], 5, 1.8, hoja, { ang: a }); });
      if (alto <= 0.02) return;
      const base = [D.ox, D.oy - 2], h = 22 * alto, cab = [base[0] + Math.sin(inc) * h, base[1] - Math.cos(inc) * h];
      const med = [(base[0] + cab[0]) / 2 + 2 * Math.cos(inc), (base[1] + cab[1]) / 2];
      K.pieza(0, () => { K.cap(base, med, 2.2, 1.8, tallo); K.cap(med, cab, 1.8, 1.6, tallo); });
      // la cabeza: dos labios que se abren en vertical, con dientes y baba
      K.pieza(1, () => {
        const ab = abre * 4.5, rx = 6.5, ry = 7.5;
        K.ovalo([cab[0] - 1.5 - ab * 0.4, cab[1] - 4], rx * 0.8, ry, carne, { ang: inc - 0.15 });
        K.ovalo([cab[0] + 1.5 + ab * 0.4, cab[1] - 4], rx * 0.8, ry, carne, { ang: inc + 0.15 });
        const bx = cab[0], by = cab[1] - 4;
        if (ab > 0.4) K.ovalo([bx, by], ab * 0.55 + 0.4, ry * 0.75, ['#12040a', '#1e060e', '#300a16', '#400e1c'], { mate: true });
        for (let k = -3; k <= 3; k++) { const yy = by + k * 1.6; K.px(bx - ab * 0.5 - 0.5, yy, R.hueso[k % 2 ? 3 : 4], R.hueso); K.px(bx + ab * 0.5 + 0.5, yy + 0.8, R.hueso[k % 2 ? 4 : 3], R.hueso); }
        if (abre > 0.3) for (let k = 0; k < 2; k++) K.px(bx + (k ? 1 : -1), by + ry * 0.9 + k, '#b8f070', carne, true); // baba
        // manchas claras en los labios
        K.px(bx - rx * 0.7, by - ry * 0.5, carne[4], carne); K.px(bx + rx * 0.5, by - ry * 0.6, carne[4], carne);
      });
      K.puntos = { boca: [cab[0], cab[1] - 4] };
    },
  };

  // Sapo hueco: flaco, la espalda llena de bultos de la Podre; salta y aplasta (con el Slime de Quaternius).
  DEF.sapo_hueco = Object.assign(baba({ rampa: R.gruto, rx: 1, ry: 1 }), {
    W: 44, H: 34, ox: 20, oy: 30,
    dibujar(K) {
      const D = K.D, [sy, sx, inc] = aplasto(K), piel = ['#10140c', '#2a3420', '#465430', '#687846', '#909e66'], vientre = ['#2a2418', '#6a5c40', '#9a8a62', '#c4b48a'];
      const cx = D.ox + inc * 3, cy = D.oy - 6 * sy, enAire = K.A.clip === 'Slime_Attack', muerto = K.anim === 'm';
      // patas de atrás (dobladas en el suelo, estiradas en el aire)
      K.pieza(0, () => {
        if (enAire) { K.cap([cx - 6, cy + 2], [cx - 11, cy + 6], 2.2, 1.4, piel); K.cap([cx - 11, cy + 6], [cx - 15, cy + 8], 1.3, 1.0, piel); }
        else { K.ovalo([cx - 6, D.oy - 3], 4.2 * sx, 3 * sy, piel); K.cap([cx - 4, D.oy - 0.5], [cx + 1, D.oy - 0.5], 1.2, 1.2, piel); }
      });
      K.pieza(1, () => { // el cuerpo, flaco
        K.ovalo([cx, cy], 9 * sx, 5.6 * sy, piel, { ang: -0.12 });
        K.ovalo([cx + 2, cy + 2.6 * sy], 6 * sx, 2.6 * sy, vientre, { mate: true });
      });
      K.pieza(2, () => { // la cabeza: boca ancha y ojos arriba
        const hx = cx + 7.5 * sx, hy = cy - 1.5 * sy;
        K.ovalo([hx, hy], 4.6 * sx, 3.6 * sy, piel);
        K.vara([hx - 1, hy + 1.5 * sy], [hx + 4.4 * sx, hy + 0.8 * sy], [0, '#0e0a08', '#0e0a08'], { k: 2 });
        if (!muerto) { K.px(hx - 0.5, hy - 3 * sy, OJO.rojo[0], piel, true); K.px(hx + 2, hy - 2.6 * sy, OJO.rojo[1], piel, true); }
        else { K.px(hx - 0.5, hy - 3 * sy, piel[0], piel); K.px(hx + 2, hy - 2.6 * sy, piel[0], piel); }
      });
      K.pieza(3, () => { // patas de delante
        K.cap([cx + 5, cy + 2], [cx + 6.5, D.oy - 0.5], 1.3, 1.1, piel);
      });
      K.pieza(4, () => { // los bultos de la Podre en la espalda (brillan)
        for (const [dx, dy, r] of [[-4, -5, 2], [0, -5.6, 2.4], [3.6, -4.6, 1.6], [-7, -2.6, 1.5]]) K.ovalo([cx + dx * sx, cy + dy * sy], r, r * Math.min(1.15, sy), PODRE, { luz: !muerto });
      });
    },
  });

  // ---------------------------------------------------------------- voladores (aleteo del murciélago de Quaternius)
  // Del murciélago se usa el aleteo (dónde está la punta de cada ala en cada cuadro) y el vaivén del cuerpo; el
  // cuerpo de insecto (tórax, cabeza y abdomen en horizontal) se pone aquí. o: { cuerpo, abdomen, cabeza, ala,
  // rayas, ojos: 'calavera' | 'mosca', gordo }
  function volador(o) {
    return {
      W: 44, H: 40, ox: 22, oy: 36, px: 4.2, yaw: 1.25, fuente: ANIM_MURCIELAGO, fpsQuieta: 14, zancada: 999,
      anims: {
        q: { clip: 'Bat_Flying', n: 6, bucle: true }, a: { clip: 'Bat_Flying', n: 6, bucle: true },
        p: { clip: 'Bat_Flying', n: 4, bucle: true, proc: 'curva' }, g: { clip: 'Bat_Attack', n: 3, de: 0.3, a: 0.6, proc: 'pica' },
        r: { clip: 'Bat_Hit', n: 3, de: 0, a: 0.5 }, m: { clip: 'Bat_Death', n: 5, proc: 'cae' },
      },
      dibujar(K) {
        const D = K.D, S = K.S, A = K.A, u = fase(K), P = K.P;
        const bob = (P.Body[1] - 1.55) * D.px * 2, cae = A.proc === 'cae' ? u : 0;
        const c = [D.ox, D.oy - 9 + bob + cae * 7];
        const curva = A.proc === 'curva' ? 0.35 + u * 0.9 : A.proc === 'pica' ? 1.2 - u * 0.4 : 0.2;
        const inc = A.proc === 'pica' ? 0.35 : A.clip === 'Bat_Hit' ? -0.3 : cae * 1.4;
        const rot = (x, y) => [c[0] + x * Math.cos(inc) - y * Math.sin(inc), c[1] + x * Math.sin(inc) + y * Math.cos(inc)];
        const g = o.gordo ? 1.25 : 1;
        // alas: del hombro hacia la punta del ala del murciélago (en pantalla), estrechas y con venas
        const ala = (lado, z) => K.pieza(z, () => {
          const sh = S['Shoulder' + lado], tip = S['Wing4' + lado + '_end'], m = S['Wing3' + lado];
          if (!sh || cae > 0.7) return;
          const base = rot(0, -2.2 * g), dx = (tip[0] - sh[0]) * 0.5, dy = (tip[1] - sh[1]) * 0.5;
          const pt = [base[0] + dx, base[1] + dy], len = Math.hypot(dx, dy), ang = Math.atan2(dy, dx);
          const ra = Object.assign(o.ala.slice(), { sinLinea: true });
          K.ovalo([(base[0] + pt[0]) / 2, (base[1] + pt[1]) / 2], Math.max(1.5, len / 2 + 1), Math.max(1.2, 2.4 * g * (0.6 + 0.4 * Math.abs(Math.cos(ang)))), ra, { ang, mate: true });
          K.vara(base, [base[0] + dx * 0.85, base[1] + dy * 0.85], ra, { k: 1 }); // la vena
          void m;
        });
        ala('L', -3);
        // abdomen (detrás), con rayas y el aguijón al final; se curva hacia delante al preparar
        K.pieza(-1, () => {
          const ac = curva, ax = -5 * g * Math.cos(ac * 0.6), ay = 2 + 3 * Math.sin(ac * 0.6);
          const pa = rot(ax, ay), angA = inc + ac * 0.9;
          K.ovalo(pa, 4.8 * g, 3.2 * g, o.abdomen, { ang: angA, tex: o.rayas ? (x, y) => (Math.floor(((x - pa[0]) * Math.cos(angA) + (y - pa[1]) * Math.sin(angA)) / 1.6) % 2 ? -0.55 : 0) : undefined });
          if (o.aguijon) { const t0 = [pa[0] - Math.cos(angA) * 4.8 * g, pa[1] - Math.sin(angA) * 4.8 * g], t1 = [t0[0] - Math.cos(angA) * 2.5, t0[1] - Math.sin(angA) * 2.5]; K.vara(t0, t1, R.metal, { k: 1 }); }
        });
        K.pieza(0, () => { // patas colgando
          for (let i = 0; i < 3; i++) { const a0 = rot(-1 + i * 1.6, 1.8), a1 = [a0[0] - 0.5 + i * 0.4, a0[1] + 3 + (i === 1 ? 1 : 0) - cae * 2]; K.vara(a0, a1, o.cuerpo, { k: 1 }); }
        });
        K.pieza(1, () => K.ovalo(rot(0, 0), 3.4 * g, 3 * g, o.cuerpo)); // tórax
        K.pieza(2, () => { // cabeza
          const hc = rot(4.2 * g, -1.4);
          if (o.ojos === 'calavera') {
            K.ovalo(hc, 3, 2.8, R.hueso);
            K.px(hc[0] - 0.5, hc[1] - 0.5, OSCURO, R.hueso); K.px(hc[0] + 1.5, hc[1] - 0.3, OSCURO, R.hueso);
            if (cae < 0.5) { K.px(hc[0] - 0.5, hc[1] - 0.5, OJO.rojo[0], R.hueso, true); K.px(hc[0] + 1.5, hc[1] - 0.3, OJO.rojo[0], R.hueso, true); }
            K.px(hc[0] + 1, hc[1] + 2, R.hueso[1], R.hueso); K.px(hc[0] + 2, hc[1] + 1.8, R.hueso[3], R.hueso);
            K.vara([hc[0] + 1, hc[1] - 2.5], [hc[0] + 3, hc[1] - 5], o.cuerpo, { k: 2 }); // antena
          } else {
            K.ovalo(hc, 2.4, 2.2, o.cuerpo);
            K.ovalo([hc[0] + 1, hc[1] - 0.8], 2.2, 2.4, ['#3a0408', '#8a0c14', '#d42420', '#ff5a40', '#ffb0a0'], { luz: cae < 0.5, brillo: true }); // ojos compuestos
          }
        });
        ala('R', 4);
      },
    };
  }
  DEF.avispa = volador({ cuerpo: ['#120c06', '#2a1a0c', '#4a3010', '#6e4a18'], abdomen: ['#1a0e04', '#6a3a08', '#c07a10', '#f0b028', '#ffe080'], ala: ['#4a5a6a', '#8aa0b4', '#b8cede', '#e0f0fa'], rayas: true, aguijon: true, ojos: 'calavera' });
  DEF.moscarda = volador({ cuerpo: ['#06080a', '#141c20', '#22323a', '#344c58'], abdomen: ['#081410', '#183a2e', '#2a6a4a', '#48a070', '#a0e0b0'], ala: ['#3a4048', '#6a7480', '#9aa4b0', '#cad4de'], gordo: true, ojos: 'mosca' });

  // ---------------------------------------------------------------- la Lápida (escarabajo de seis patas)
  // El caparazón es una lápida con musgo y una cruz; seis patas con paso en trípode; antes de embestir rasca el
  // suelo y baja la cabeza.
  DEF.escarabajo_musgo = {
    W: 48, H: 40, ox: 24, oy: 36, px: 1, zancada: 14, fpsQuieta: 3,
    anims: { q: { proc: 'quieta', n: 4, bucle: true }, a: { proc: 'anda', n: 6, bucle: true }, p: { proc: 'rasca', n: 4, bucle: true }, g: { proc: 'embiste', n: 4, bucle: true }, r: { proc: 'golpe', n: 2 }, m: { proc: 'muere', n: 5 }, n: { proc: 'sale', n: 4 } },
    dibujar(K) {
      const D = K.D, A = K.A, u = fase(K), cuerpo = ['#0a0806', '#1e1812', '#36281c', '#523c28'], pata = ['#080604', '#1a140e', '#2e2418', '#44362a'];
      let alto = 7, inc = 0, vel = 0, sale = 1, vuelta = 0;
      if (A.proc === 'anda') vel = 1;
      if (A.proc === 'rasca') { inc = 0.12; alto = 6; }
      if (A.proc === 'embiste') { vel = 2; inc = 0.16; alto = 6.5; }
      if (A.proc === 'golpe') { inc = -0.12; alto = 7.5; }
      if (A.proc === 'muere') { vuelta = u; alto = 7 - u * 4; }
      if (A.proc === 'sale') sale = u;
      const cx = D.ox, cy = D.oy - alto + (1 - sale) * 14 + (A.proc === 'quieta' ? Math.sin(u * Math.PI * 2) * 0.4 : 0);
      // patas: 3 por lado; trípode (0 y 2 de un lado con la 1 del otro)
      const patas = (lejos) => {
        for (let i = 0; i < 3; i++) {
          const grupo = (i + (lejos ? 1 : 0)) % 2, fz = (u * 2 * Math.max(1, vel) + grupo * 0.5) % 1;
          const lev = vel ? Math.max(0, Math.sin(fz * Math.PI * 2)) * 2 : A.proc === 'rasca' && i === 2 && !lejos ? Math.max(0, Math.sin(u * Math.PI * 4)) * 2.5 : 0;
          const avance = vel ? Math.cos(fz * Math.PI * 2) * 2 : 0;
          const hx = cx - 5 + i * 5 + (lejos ? 1 : 0), hy = cy + 2.5;
          const rod = [hx + (i - 1) * 2.2 + (lejos ? 1.5 : -1), hy + 2.2 - lev];
          const pie = vuelta > 0.5 ? [rod[0] + (i - 1) * 2, rod[1] - 5 * vuelta] : [hx + (i - 1) * 4 + avance + (lejos ? 2 : -1), D.oy + 0.5 - lev * 0.5];
          K.vara([hx, hy], rod, lejos ? pata.map((c) => c) : pata, { grosor: 2, k0: lejos ? 0 : 1, k1: lejos ? 1 : 3 });
          K.vara(rod, pie, pata, { grosor: 1, k: lejos ? 1 : 2 });
        }
      };
      K.pieza(-2, () => patas(true));
      K.pieza(0, () => K.ovalo([cx, cy + 1.5], 9, 4, cuerpo, { ang: inc })); // el cuerpo, debajo del caparazón
      K.pieza(1, () => { // la cabeza con mandíbulas y ojos rojos
        const hx = cx + 9 * Math.cos(inc) + 1, hy = cy + 2 + 9 * Math.sin(inc) + (inc > 0.1 ? 1.5 : 0);
        K.ovalo([hx, hy], 3.4, 3, cuerpo);
        K.vara([hx + 2, hy + 1], [hx + 5.5, hy + 0.5], R.hueso, { k: 3 }); K.vara([hx + 2, hy + 2.4], [hx + 5.2, hy + 3.2], R.hueso, { k: 2 });
        if (vuelta < 0.5) { K.px(hx + 1, hy - 1, OJO.rojo[0], cuerpo, true); K.px(hx - 0.5, hy - 1.4, OJO.rojo[1], cuerpo, true); }
      });
      K.pieza(2, () => { // el caparazón-lápida: piedra redondeada arriba, musgo encima y la cruz grabada
        const lx = cx - 1, base = cy + 3 - vuelta * 3, h = 13, w = 8;
        const pts = [[lx - w, base], [lx - w, base - h + 4], [lx - w + 2, base - h + 1], [lx - 3, base - h], [lx + 3, base - h], [lx + w - 2, base - h + 1], [lx + w, base - h + 4], [lx + w, base]];
        const rot = (q) => { const dx = q[0] - cx, dy = q[1] - cy; const a = inc * 0.6 + vuelta * 2.6; return [cx + dx * Math.cos(a) - dy * Math.sin(a), cy + dx * Math.sin(a) + dy * Math.cos(a)]; };
        const pr = pts.map(rot);
        K.poli(pr, R.piedra, { normal: (x, y) => [((x - lx) / w) * 0.7, -0.3 + ((y - (base - h)) / h) * 0.6], tex: (x, y) => ((x * 7 + y * 13) % 11 === 0 ? -0.4 : 0) });
        if (vuelta < 0.4) {
          for (let k = -2; k <= 3; k++) K.px(rot([lx, base - h + 4 + k])[0], rot([lx, base - h + 4 + k])[1], R.piedra[0], R.piedra);
          for (let k = -2; k <= 2; k++) K.px(rot([lx + k, base - h + 5])[0], rot([lx + k, base - h + 5])[1], R.piedra[0], R.piedra);
          for (let k = 0; k < 9; k++) { const q = rot([lx - w + 1 + k * 1.8, base - h + 1 + Math.abs(k - 4) * 0.4]); K.px(q[0], q[1], R.musgo[k % 3 === 0 ? 4 : 3], R.musgo); K.px(q[0], q[1] + 1, R.musgo[2], R.musgo); }
        }
      });
      K.pieza(3, () => patas(false));
    },
  };

  // ---------------------------------------------------------------- humanoides de la Ciénaga, la Ciudad y la Cripta
  // Calavera (Velador caído, Guardián): cráneo con cuencas y dos puntos de luz fría.
  function calavera(K, c, o = {}) {
    const hu = o.hueso || R.hueso, x = c[0] + 0.5, y = c[1] - 3.5, e = o.e || 1;
    K.pieza(c[2] + 2, () => {
      K.ovalo([x, y], 3.4 * e, 3.4 * e, hu);
      K.ovalo([x + 1.2 * e, y + 2.6 * e], 2.2 * e, 1.5 * e, hu); // la mandíbula
      for (const [ex, ey] of [[-1.2, -0.3], [1.6, -0.2]]) { K.px(x + ex * e, y + ey * e, OSCURO, hu); K.px(x + ex * e + 1, y + ey * e, OSCURO, hu); K.px(x + ex * e, y + ey * e + 1, OSCURO, hu); K.px(x + ex * e + 1, y + ey * e + 1, OSCURO, hu); K.px(x + ex * e + (ex < 0 ? 1 : 0), y + ey * e, (o.ojo || OJO.frio)[0], hu, true); }
      for (let i = 0; i < 3; i++) K.px(x + (0.4 + i) * e, y + 2.6 * e, i % 2 ? OSCURO : hu[4], hu);
      K.px(x - 1.5 * e, y - 2.2 * e, hu[4], hu);
    });
  }
  // Huesos: las piezas del cuerpo como huesos finos, costillas en el pecho.
  function esqueletoDe(K, o = {}) {
    const S = K.S, hu = o.hueso || R.hueso;
    for (const l of ['l', 'r']) {
      const u = S['upperleg' + l], m = S['lowerleg' + l], p = S['foot' + l], t = S['toes' + l];
      K.pieza((u[2] + m[2]) / 2 - 0.3, () => { K.cap(u, m, 0.8, 0.7, hu); K.ovalo([m[0], m[1]], 1.1, 1.1, hu); });
      K.pieza((m[2] + p[2]) / 2 - 0.2, () => K.cap(m, p, 0.7, 0.7, hu));
      K.pieza((p[2] + t[2]) / 2, () => K.cap([p[0], p[1] + 0.5], t, 1.0, 0.8, hu));
    }
    for (const l of ['l', 'r']) {
      const u = S['upperarm' + l], m = S['lowerarm' + l], h = S['hand' + l];
      K.pieza((u[2] + m[2]) / 2 + 0.1, () => K.cap(u, m, 0.8, 0.7, hu));
      K.pieza((m[2] + h[2]) / 2 + 0.2, () => K.cap(m, h, 0.7, 0.6, hu));
      K.pieza(h[2] + 0.3, () => K.ovalo([h[0], h[1]], 1.1, 1.1, hu));
    }
    const hi = S.hips, sp = S.spine, ch = S.chest;
    K.zTronco = (hi[2] + ch[2]) / 2 - 0.5;
    K.pieza(K.zTronco, () => {
      K.cap(hi, ch, 0.8, 0.8, hu); // espinazo
      K.ovalo([hi[0], hi[1]], 2.4, 1.4, hu); // pelvis
      for (let k = 0; k < 3; k++) { const t = 0.5 + k * 0.17, x = sp[0] + (ch[0] - sp[0]) * t, y = sp[1] + (ch[1] - sp[1]) * t; K.cap([x - 2.4, y], [x + 2.4, y + 0.3], 0.6, 0.6, hu); }
    });
  }
  // Túnica: un polígono del pecho a las rodillas, con el borde de abajo roto.
  function tunica(K, rampa, o = {}) {
    const S = K.S, ch = S.chest, hi = S.hips, la = o.largo ?? 1, ab = o.ancho ?? 1;
    const kn = [(S.lowerlegl[0] + S.lowerlegr[0]) / 2, Math.max(S.lowerlegl[1], S.lowerlegr[1])];
    const bajo = hi[1] + (kn[1] - hi[1]) * la;
    K.pieza(K.zTronco + (o.z ?? 0.2), () => {
      K.poli([[ch[0] - 3 * ab, ch[1] - 1], [ch[0] + 3 * ab, ch[1] - 1], [hi[0] + 4 * ab + (kn[0] - hi[0]) * 0.5, bajo], [hi[0] - 4.5 * ab + (kn[0] - hi[0]) * 0.5, bajo]], rampa, {
        corta: (x, y) => y > bajo - 2 && ((x * 5 + Math.floor(y)) % 3 === 0 || (y > bajo - 1 && x % 2 === 0)),
      });
    });
  }
  const ANDAR_HUMANO = { q: { clip: 'Idle', n: 4, bucle: true }, a: { clip: 'Walking_C', n: 8, bucle: true }, r: { clip: 'Hit_A', n: 3, de: 0.1, a: 0.6 }, m: { clip: 'Death_A', n: 6, raiz: 0.4 } };

  // Velador caído: esqueleto con túnica y alabarda. Al morir se desmonta (las piernas se quedan de pie).
  DEF.velador_caido = {
    W: 60, H: 52, ox: 26, oy: 48, px: 14, yaw: 1.05, fuente: ANIM_ESQUELETO, zancada: 22,
    prop: { pierna: 1, brazo: 1, hombros: 0.85, caderas: 0.8, encorvar: 0.15, cuello: 0.7, cadera: 0.39 },
    anims: con(ANDAR_HUMANO, {
      a: { clip: 'Walking_D_Skeletons', n: 8, bucle: true },
      p: { clip: '2H_Melee_Attack_Slice', n: 3, de: 0, a: 0.3 },
      g: { clip: '2H_Melee_Attack_Slice', n: 4, de: 0.32, a: 0.75 },
      m: { clip: 'Death_C_Skeletons', n: 6, de: 0, a: 0.5, raiz: 0.3 },
      n: { clip: 'Skeletons_Awaken_Floor', n: 6, de: 0.3, a: 0.8, raiz: 0.2 },
    }, { _: 0.6, a: 0.8 }),
    dibujar(K) {
      esqueletoDe(K);
      tunica(K, ['#06080e', '#141a2a', '#222c44', '#344264'], { largo: 0.7 });
      calavera(K, K.S.head);
      K.pieza(K.S.head[2] + 1.5, () => { const h = K.S.head; K.poli([[h[0] - 4.5, h[1] - 5], [h[0] + 1, h[1] - 9.5], [h[0] + 3.5, h[1] - 6], [h[0] - 1, h[1] - 3], [h[0] - 4.5, h[1] + 1]], ['#06080e', '#141a2a', '#222c44', '#344264']); });
      // la alabarda: asta larga y hoja de hacha con pico
      const v = vara(K, 9, 16, R.madera, { grosor: 1 });
      K.pieza(v.z + 0.01, () => {
        const b = v.b, d = v.d, n = v.n;
        K.poli([[b[0] - d[0] * 5, b[1] - d[1] * 5], [b[0] - d[0] * 6 + n[0] * 4.5, b[1] - d[1] * 6 + n[1] * 4.5], [b[0] + n[0] * 4, b[1] + n[1] * 4], [b[0] - d[0] * 1, b[1] - d[1] * 1]], R.metal, { normal: () => [-0.4, -0.5] });
        K.vara(b, [b[0] + d[0] * 4, b[1] + d[1] * 4], R.metal, { k: 3 });
      });
    },
  };

  // Portador: cadáver de minero encorvado; de la espalda le sale un hongo enorme que le tapa la cabeza.
  DEF.seton = {
    W: 52, H: 48, ox: 24, oy: 44, px: 13, yaw: 1.05, fuente: ANIM_ESQUELETO, zancada: 16,
    prop: { pierna: 0.9, brazo: 1.05, hombros: 0.85, caderas: 0.85, encorvar: 0.55, cuello: 0.55, cadera: 0.39 },
    anims: con(ANDAR_HUMANO, {
      a: { clip: 'Walking_D_Skeletons', n: 8, bucle: true },
      p: { clip: 'Taunt', n: 4, de: 0, a: 0.45 },
      g: { clip: 'Taunt', n: 3, de: 0.5, a: 0.9 },
    }, {}),
    dibujar(K) {
      const muerto = ['#141418', '#2e2e36', '#4a4a56', '#6a6a78', '#8e8e9a'];
      cuerpo(K, { piel: muerto, tronco: ['#140c08', '#30200e', '#4e3418', '#6e4c26'], pierna: ['#10101a', '#20202e', '#363648', '#4c4c62'], pie: R.cuero });
      const h = K.S.head, ch = K.S.chest;
      // la cara hundida (casi no se ve bajo el sombrero)
      K.pieza(h[2] + 1, () => { K.ovalo([h[0] + 1.5, h[1] - 2], 2.8, 2.8, muerto); K.px(h[0] + 2, h[1] - 2.5, OSCURO, muerto); K.px(h[0] + 3.5, h[1] - 2.3, OSCURO, muerto); });
      // el hongo: tallo desde la espalda y un sombrero enorme con manchas, que le tapa la cabeza
      const c = [ch[0] - 1, ch[1] - 9];
      K.pieza(h[2] + 1.6, () => {
        K.cap([ch[0] - 2, ch[1] + 1], [c[0], c[1] + 3], 2.2, 1.8, ['#2a2418', '#6a5c40', '#9a8a62', '#c4b48a']);
        K.ovalo([c[0], c[1] + 2.4], 9, 2.4, ['#2a1408', '#5a3418', '#7a4a24', '#9a6234'], { mate: true }); // las láminas de abajo
        K.ovalo(c, 9.5, 6, ['#3a1006', '#7a2a10', '#b8481c', '#e0703a', '#ffb070'], { corta: (x, y) => y > c[1] + 2 });
        for (const [dx, dy, r] of [[-4, -3, 1.4], [1, -4.4, 1.1], [5, -1.6, 1.3], [-6.5, 0.4, 0.9], [2, -1, 0.9]]) K.ovalo([c[0] + dx, c[1] + dy], r, r * 0.8, ['#8a7a60', '#d8ccb0', '#efe6d0', '#fffaf0'], { mate: true });
      });
      // esporas que brillan cuando prepara la nube
      if (K.anim === 'p') K.pieza(h[2] + 2, () => { for (let k = 0; k < 5; k++) K.px(c[0] - 6 + k * 3, c[1] + 4 + (k % 2) * 2 - K.f, '#f0f070', R.hueso, true); });
    },
  };

  // Farolero: casi invisible, muy alto y flaco; delante de la boca cuelga una luz que te llama.
  DEF.farolero = {
    W: 64, H: 64, ox: 28, oy: 60, px: 19, yaw: 1.1, fuente: ANIM_ESQUELETO, zancada: 30, fpsQuieta: 3,
    prop: { pierna: 1.25, brazo: 1.45, hombros: 0.7, caderas: 0.7, encorvar: 0.6, cuello: 0.8, tronco: 1.15, cadera: 0.39 },
    anims: con(ANDAR_HUMANO, {
      a: { clip: 'Walking_D_Skeletons', n: 8, bucle: true },
      p: { clip: 'Unarmed_Melee_Attack_Punch_A', n: 3, de: 0, a: 0.3 },
      g: { clip: 'Unarmed_Melee_Attack_Punch_A', n: 3, de: 0.32, a: 0.6 },
    }, {}),
    dibujar(K) {
      const sombra = ['#030305', '#08080e', '#101018', '#181826', '#222234'];
      cuerpo(K, { piel: sombra, r: { muslo: 1.6, pierna: 1.2, pie: 1.3, brazo: 1.1, antebrazo: 1.0, mano: 1.4, cadera: 2.6, pecho: 3.6 } });
      const h = K.S.head, abre = K.anim === 'p' ? K.u : K.anim === 'g' ? 1 - K.u * 0.5 : 0.1;
      K.pieza(h[2] + 1, () => {
        K.ovalo([h[0] + 1, h[1] - 3], 3.4, 3.2, sombra);
        // la boca, enorme, con dientes finos
        K.ovalo([h[0] + 3.5, h[1] - 1 + abre * 1.5], 2.6, 0.6 + abre * 2.4, ['#000000', '#05030a', '#0a0610', '#120a18'], { mate: true });
        for (let k = 0; k < 4; k++) { K.px(h[0] + 2 + k, h[1] - 1.5, '#5a5a6a', sombra); if (abre > 0.3) K.px(h[0] + 2.5 + k, h[1] + abre * 3.2, '#4a4a5a', sombra); }
      });
      // la antena que sale de la frente y el cebo encendido delante de la boca
      const base = [h[0] + 2, h[1] - 6], cebo = [h[0] + 9 + Math.sin(K.u * Math.PI * 2) * 0.8, h[1] - 1 + Math.cos(K.u * Math.PI * 2) * 0.6];
      K.pieza(h[2] + 2, () => {
        const m = [(base[0] + cebo[0]) / 2 + 1, base[1] - 3];
        K.vara(base, m, sombra, { k: 3 }); K.vara(m, [cebo[0], cebo[1] - 2], sombra, { k: 3 });
        K.ovalo(cebo, 1.8, 1.8, ['#806020', '#e0a030', '#ffd060', '#fff0a0', '#ffffff'], { luz: true, brillo: true });
      });
      K.puntos = { cebo };
    },
  };

  // Guardián de piedra: Velador de piedra encapuchado, con espada; espera como una estatua.
  DEF.guardian_piedra = {
    W: 72, H: 66, ox: 32, oy: 62, px: 18, yaw: 1.0, fuente: ANIM_ESQUELETO, zancada: 26, fpsQuieta: 2,
    prop: { pierna: 1.0, brazo: 1.05, hombros: 1.05, caderas: 0.95, encorvar: 0.1, cuello: 0.7, cadera: 0.39 },
    anims: con(ANDAR_HUMANO, {
      e: { clip: '2H_Melee_Idle', n: 1, de: 0, a: 0 },
      q: { clip: '2H_Melee_Idle', n: 4, bucle: true },
      a: { clip: 'Walking_D_Skeletons', n: 8, bucle: true },
      d: { clip: 'Skeletons_Awaken_Floor', n: 5, de: 0.62, a: 0.95 },
      p: { clip: '2H_Melee_Attack_Chop', n: 4, de: 0, a: 0.4 },
      g: { clip: '2H_Melee_Attack_Chop', n: 4, de: 0.45, a: 0.8 },
      r: { clip: 'Block', n: 2, de: 0.2, a: 0.5 },
    }, { _: 0 }),
    dibujar(K) {
      const pie = R.piedra, vivo = K.anim !== 'e' && K.anim !== 'm';
      cuerpo(K, { piel: pie, r: { muslo: 2.4, pierna: 2.1, pie: 2.2, brazo: 2.2, antebrazo: 2.0, mano: 2.1, cadera: 4.2, pecho: 5.4 } });
      tunica(K, ['#14161e', '#2a2e3a', '#444a58', '#5e6676', '#7e8696'], { largo: 0.85, ancho: 1.5 });
      const h = K.S.head;
      // la capucha de piedra y, dentro, la rendija con dos puntos de luz fría
      K.pieza(h[2] + 1.5, () => {
        K.poli([[h[0] - 6, h[1] + 1], [h[0] - 5, h[1] - 7], [h[0] - 1, h[1] - 11], [h[0] + 4, h[1] - 8], [h[0] + 6, h[1] - 2], [h[0] + 5, h[1] + 2]], pie, { normal: (x, y) => [((x - h[0]) / 6) * 0.8, ((y - h[1] + 5) / 8) * 0.6] });
        K.ovalo([h[0] + 2, h[1] - 3.5], 2.8, 3.2, ['#020204', '#06060a', '#0a0a10', '#101018'], { mate: true });
        if (vivo) { K.px(h[0] + 1.5, h[1] - 4, OJO.frio[0], pie, true); K.px(h[0] + 3.5, h[1] - 3.8, OJO.frio[1], pie, true); }
      });
      // la espada de piedra (a dos manos)
      const v = vara(K, 4, 6, R.piedra, { grosor: 2 });
      K.pieza(v.z + 0.02, () => {
        const b = v.b, d = v.d, n = v.n, L = 20;
        K.vara([b[0] - n[0] * 3, b[1] - n[1] * 3], [b[0] + n[0] * 3, b[1] + n[1] * 3], R.piedra, { grosor: 2 }); // la cruz
        K.poli([[b[0] - n[0] * 1.6, b[1] - n[1] * 1.6], [b[0] + d[0] * L - n[0] * 1.2, b[1] + d[1] * L - n[1] * 1.2], [b[0] + d[0] * (L + 3), b[1] + d[1] * (L + 3)], [b[0] + d[0] * L + n[0] * 1.2, b[1] + d[1] * L + n[1] * 1.2], [b[0] + n[0] * 1.6, b[1] + n[1] * 1.6]], R.piedra, { normal: (x, y) => [n[0] * 0.3 - 0.35, n[1] * 0.3 - 0.35] });
        if (vivo) for (let k = 3; k < L; k += 4) K.px(b[0] + d[0] * k, b[1] + d[1] * k, OJO.frio[0], R.piedra, true); // runas
      });
    },
  };

  // Velado: fantasma encorvado como quien llora, sin pies; el velo le tapa la cara.
  DEF.velado = {
    W: 60, H: 60, ox: 28, oy: 56, px: 17, yaw: 1.05, fuente: ANIM_ESQUELETO, zancada: 999, fpsQuieta: 3,
    prop: { pierna: 0.9, brazo: 1.2, hombros: 0.8, caderas: 0.8, encorvar: 0.55, cuello: 0.6, cadera: 0.39 },
    anims: con(ANDAR_HUMANO, {
      q: { clip: 'Walking_D_Skeletons', n: 6, bucle: true },
      a: { clip: 'Walking_D_Skeletons', n: 6, bucle: true },
      p: { clip: 'Spellcast_Shoot', n: 3, de: 0, a: 0.3 },
      g: { clip: 'Spellcast_Shoot', n: 3, de: 0.3, a: 0.6 },
      m: { clip: 'Death_A', n: 6, raiz: 0.3 },
    }, {}),
    dibujar(K) {
      const S = K.S, velo = ['#0c1018', '#1c2638', '#2e3e58', '#4a6080', '#7090b0'], mano = ['#1a2030', '#4a5a74', '#7a8ca6', '#aabcd2'];
      const ch = S.chest, hi = S.hips, h = S.head, u = fase(K);
      K.zTronco = (hi[2] + ch[2]) / 2;
      for (const l of ['l', 'r']) {
        const a = S['upperarm' + l], m = S['lowerarm' + l], hd = S['hand' + l];
        K.pieza((a[2] + m[2]) / 2 + (l === 'r' ? 1 : -1), () => { K.cap(a, m, 1.8, 1.6, velo); K.cap(m, hd, 1.6, 1.2, velo); K.ovalo([hd[0], hd[1] + 1], 1.2, 1.6, mano); K.vara([hd[0] + 0.5, hd[1] + 1], [hd[0] + 1, hd[1] + 3], mano, { k: 2 }); });
      }
      // el cuerpo: un velo que cae desde la cabeza y se deshace en jirones que ondean (sin pies)
      K.pieza(K.zTronco, () => {
        const pie = K.D.oy - 2, ond = (y) => Math.sin(u * Math.PI * 2 + y * 0.5) * 1.2;
        K.poli([[h[0] - 5, h[1] - 5], [h[0] + 3, h[1] - 6], [ch[0] + 5, ch[1] + 2], [hi[0] + 6 + ond(1), pie - 3], [hi[0] + 2 + ond(2), pie], [hi[0] - 3 + ond(3), pie - 2], [hi[0] - 8 + ond(4), pie + 1], [hi[0] - 9, hi[1] - 2], [ch[0] - 7, ch[1]]], velo, {
          normal: (x, y) => [((x - ch[0]) / 8) * 0.8, -0.2], corta: (x, y) => y > pie - 4 && (x + Math.floor(u * 6)) % 3 === 0,
        });
      });
      // la capucha del velo y, debajo, dos puntos de luz fría
      K.pieza(h[2] + 1, () => {
        K.ovalo([h[0], h[1] - 3], 4.6, 4.8, velo);
        K.ovalo([h[0] + 2.4, h[1] - 2], 2.4, 3, ['#020306', '#04060c', '#080c14', '#0c121c'], { mate: true });
        K.px(h[0] + 2, h[1] - 2.5, OJO.frio[0], velo, true); K.px(h[0] + 3.5, h[1] - 2.3, OJO.frio[1], velo, true);
      });
    },
  };

  // Necrófago: un Velador vaciado por la Podre, a cuatro patas como una araña: espinazo arqueado, mandíbula suelta y
  // brazos larguísimos (esqueleto propio: paso de cuatro patas en diagonal).
  DEF.necrofago = {
    W: 64, H: 44, ox: 30, oy: 40, px: 1, zancada: 26, fpsQuieta: 3,
    anims: { q: { proc: 'quieta', n: 4, bucle: true }, a: { proc: 'anda', n: 8, bucle: true }, p: { proc: 'alza', n: 3 }, g: { proc: 'salta', n: 3 }, r: { proc: 'golpe', n: 2 }, m: { proc: 'muere', n: 5 }, n: { proc: 'sale', n: 5 } },
    dibujar(K) {
      const D = K.D, A = K.A, u = fase(K), piel = ['#0e0a0c', '#2a1e22', '#4a3638', '#6e5450', '#94786c'], hu = R.hueso;
      let alza = 0, vel = 0, cae = 0, sale = 1, estira = 0;
      if (A.proc === 'anda') vel = 1;
      if (A.proc === 'alza') alza = u;
      if (A.proc === 'salta') { alza = 1 - u; estira = u; }
      if (A.proc === 'golpe') alza = -0.3;
      if (A.proc === 'muere') cae = u;
      if (A.proc === 'sale') sale = u;
      const cad = [D.ox - 8, D.oy - 12 + cae * 7 + (1 - sale) * 16], hom = [D.ox + 6 + estira * 4, D.oy - 13 - alza * 9 + cae * 8 + (1 - sale) * 16];
      const lomo = [(cad[0] + hom[0]) / 2, Math.min(cad[1], hom[1]) - 6 + cae * 4]; // el espinazo arqueado
      // patas: dos brazos larguísimos delante y dos piernas detrás, paso en diagonal
      // patas de araña: la rodilla (el codo) alta y hacia fuera, el pie lejos del cuerpo
      const pata = (raiz, lado, fz, largo, delante) => {
        const f = (u + fz) % 1, lev = vel ? Math.max(0, Math.sin(f * Math.PI * 2)) * 3 : 0, av = vel ? Math.cos(f * Math.PI * 2) * 4 : 0;
        const s = delante ? 1 : -1;
        let pie = [raiz[0] + s * (largo * 0.75) + av + lado * 2, D.oy + 0.5 - lev];
        if (delante && alza > 0) pie = [raiz[0] + 9 + estira * 7, raiz[1] + 3 - alza * 6];
        if (cae > 0) pie = [raiz[0] + s * largo * (0.7 + cae * 0.4), D.oy + 0.5];
        const med = [raiz[0] + s * largo * 0.45 + lado, Math.min(raiz[1], pie[1]) - largo * 0.42 * (1 - cae * 0.6)];
        return [raiz, med, pie];
      };
      const dibujaPata = (pts, r, lejos) => {
        const rp = lejos ? piel.map((c, i) => piel[Math.max(0, i - 1)]) : piel;
        K.cap(pts[0], pts[1], r, r * 0.75, rp); K.cap(pts[1], pts[2], r * 0.75, r * 0.45, rp);
        K.ovalo([pts[1][0], pts[1][1]], r * 0.85, r * 0.85, rp); // el codo, huesudo
        K.vara(pts[2], [pts[2][0] + (pts[2][0] > pts[0][0] ? 2 : -2), pts[2][1]], rp, { k: 3 }); // los dedos largos
      };
      const P1 = pata(hom, -1, 0.5, 17, true), P2 = pata(cad, -1, 0, 13, false), P3 = pata(hom, 1, 0, 17, true), P4 = pata(cad, 1, 0.5, 13, false);
      K.pieza(-2, () => { dibujaPata(P1, 1.3, true); dibujaPata(P2, 1.4, true); });
      K.pieza(0, () => { // el cuerpo: arco de costillas de la cadera al hombro
        K.cap(cad, lomo, 3.2, 3.6, piel); K.cap(lomo, hom, 3.6, 3.4, piel);
        for (let k = 0; k < 4; k++) { const t = 0.3 + k * 0.15, x = cad[0] + (hom[0] - cad[0]) * t, y = lomo[1] + 2 + Math.abs(t - 0.5) * 6; K.vara([x, y - 1], [x + 0.5, y + 2.5], piel, { k: 1 }); }
        for (let k = 0; k < 5; k++) { const t = 0.15 + k * 0.17, x = cad[0] + (hom[0] - cad[0]) * t, y = lomo[1] - 2.4 + Math.abs(t - 0.5) * 4; K.px(x, y, hu[3], hu); } // vértebras
      });
      K.pieza(3, () => { // la cabeza colgando (la cara pálida de lo que fue un Velador), la mandíbula suelta y los ojos de Podre
        const hc = [hom[0] + 5.5 + estira * 3, hom[1] + 1 - alza * 2], cara = ['#2a2420', '#6a5e54', '#9a8c7e', '#c4b6a6', '#e6dccc'];
        K.ovalo(hc, 4, 3.4, cara);
        K.px(hc[0] + 1, hc[1] - 1, OSCURO, cara); K.px(hc[0] + 2.5, hc[1] - 0.8, OSCURO, cara);
        const ab = 1.5 + alza * 2.5 + (A.proc === 'salta' ? 2 : 0);
        K.poli([[hc[0] - 1, hc[1] + 1.5], [hc[0] + 4, hc[1] + 1], [hc[0] + 3.5, hc[1] + 2 + ab], [hc[0] - 0.5, hc[1] + 2.5 + ab * 0.6]], hu);
        K.ovalo([hc[0] + 1.6, hc[1] + 1.5 + ab * 0.5], 1.6, ab * 0.45, ['#000000', '#140608', '#200a0e', '#300e14'], { mate: true });
        if (cae < 0.6) { K.px(hc[0] + 1, hc[1] - 1, OJO.podre[0], piel, true); K.px(hc[0] + 2.5, hc[1] - 0.8, OJO.podre[1], piel, true); }
      });
      K.pieza(2, () => { dibujaPata(P4, 1.5, false); dibujaPata(P3, 1.4, false); });
    },
  };

  return { R, OJO, cuerpo, vara, mascara, gruto, baba };
})();
