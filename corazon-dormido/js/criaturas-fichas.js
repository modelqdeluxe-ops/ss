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

  // ---------------------------------------------------------------- jefes (CHECKPOINT §4.3: 80–145 px, como Core Keeper)
  // Ruido fijo por píxel (manchas de musgo, piedra, pelo).
  const ruido = (x, y, s = 0) => { let h = (Math.floor(x) * 374761393 + Math.floor(y) * 668265263 + s * 1442695041) | 0; h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967296; };
  const PIEDRA6 = ['#12141c', '#242834', '#383d4c', '#525868', '#6e7586', '#959cac'];
  const VELADOR = ['#070a14', '#121a30', '#1e2c4e', '#2e4272', '#465e98'];
  const ORO = ['#2a1606', '#6a3e10', '#a8701e', '#e0a838', '#ffe08a'];

  // EL CENTINELA: caballero Velador de piedra, más alto que una casa; el velo de piedra le tapa la cara y dentro solo
  // hay una rendija de luz fría; espadón y lámpara. Movimiento de KayKit (espada a dos manos).
  DEF.centinela = {
    jefe: true, W: 230, H: 190, ox: 96, oy: 182, px: 64, yaw: 1.0, fuente: ANIM_ESQUELETO,
    prop: { pierna: 1.2, brazo: 1.05, hombros: 1.15, caderas: 1.0, encorvar: 0.05, cuello: 0.55, tronco: 1.1, cadera: 0.39 },
    anims: {
      e: { clip: '2H_Melee_Idle', n: 1, de: 0, a: 0 },
      d: { clip: 'Taunt', n: 6, de: 0, a: 1 },
      q: { clip: '2H_Melee_Idle', n: 4, bucle: true },
      a: { clip: 'Walking_D_Skeletons', n: 8, bucle: true },
      p: { clip: '2H_Melee_Attack_Chop', n: 5, de: 0, a: 0.4 },
      g: { clip: '2H_Melee_Attack_Chop', n: 5, de: 0.42, a: 0.85 },
      c: { clip: 'Running_B', n: 6, bucle: true },
      s: { clip: 'Spellcast_Summon', n: 6, de: 0.1, a: 0.6 },
      r: { clip: 'Hit_B', n: 3, de: 0.1, a: 0.6 },
      m: { clip: 'Death_A', n: 8, raiz: 0.3 },
    },
    // qué cuadro toca en cada estado: [anim, avance 0..1]
    cuadroJefe(j, t) {
      const e = j.estado;
      if (e === 'dormido') return ['e', 0];
      if (e === 'despierta') return ['d', t / 1.6];
      if (e === 'muriendo') return ['m', t / 1.8];
      if (j.golpeadoT && J.t - j.golpeadoT < 0.25 && (e === 'espera' || e === 'camina')) return ['r', (J.t - j.golpeadoT) / 0.25];
      if (e === 'camina') return ['a', (J.t * 1.1) % 1];
      if (e === 'tajo' || (e === 'carga' && j.paso < 2)) return ['p', t / 0.9];
      if (e === 'expuesto') return ['g', t / 0.5];
      if (e === 'carga') return ['c', (J.t * 2.2) % 1];
      if (e === 'runas' || e === 'llamada') return ['s', t / 1.2];
      return ['q', (J.t * 0.6) % 1];
    },
    dibujar(K) {
      const S = K.S, e = K.D.px / 13, vivo = K.anim !== 'e' && !(K.anim === 'm' && K.u > 0.6);
      const piedra = Object.assign(PIEDRA6.slice(), { nombre: 'piedra' }), oscura = Object.assign(['#0c0e14', '#1a1d28', '#2a2e3c', '#3c4252', '#525a6c', '#6c7488'], { nombre: 'piedraO' });
      const B = { bisel: 0.45 * e };
      const grietas = (c, r, sem) => { for (let k = 0; k < 2; k++) { let x = c[0] + (ruido(sem, k, 1) - 0.5) * r, y = c[1] + (ruido(sem, k, 2) - 0.5) * r; for (let i = 0; i < 4 * e / 2; i++) { K.px(x, y, piedra[1], piedra); x += ruido(sem + i, k, 3) < 0.5 ? 1 : 0; y += 1; } } };
      const musgo = (c, w, sem) => { for (let k = 0; k < w; k++) { const x = c[0] + (ruido(sem, k, 5) - 0.5) * w * 1.5, y = c[1] + ruido(sem, k, 6) * 2; K.px(x, y, R.musgo[3 + (k % 2)], piedra); K.px(x, y + 1, R.musgo[2], piedra); } };
      const hem = (bajo, sem) => (x, y) => y > bajo - (0.4 + ruido(x >> 1, sem, 4) * 2.2) * e;
      const hi = S.hips, ch = S.chest, h = S.head;
      K.zTronco = (hi[2] + ch[2]) / 2 - 0.5;
      const kn = Math.max(S.lowerlegl[1], S.lowerlegr[1]);
      // la capa de piedra, detrás de todo
      K.pieza(K.zTronco - 3, () => K.placa([[ch[0] - 5.5 * e, ch[1] - 1.5 * e], [ch[0] + 2 * e, ch[1] - 2 * e], [hi[0] + 2 * e, kn + 3 * e], [hi[0] - 9 * e, kn + 2.5 * e]], oscura, Object.assign({ corta: hem(kn + 3 * e, 1) }, B)));
      // piernas: grebas, rodilleras y escarpes
      for (const l of ['l', 'r']) {
        const u = S['upperleg' + l], m = S['lowerleg' + l], p = S['foot' + l], t = S['toes' + l], ra = l === 'l' ? oscura : piedra;
        K.pieza((u[2] + m[2]) / 2 - 0.3, () => K.placaEntre(u, m, 4.8 * e, 4.2 * e, ra, B));
        K.pieza((m[2] + p[2]) / 2 - 0.2, () => { K.placaEntre(m, p, 4.2 * e, 3.4 * e, ra, B); K.placa([[m[0] - 2.4 * e, m[1]], [m[0], m[1] - 2.2 * e], [m[0] + 2.4 * e, m[1]], [m[0], m[1] + 2.2 * e]], ra, B); });
        K.pieza((p[2] + t[2]) / 2, () => K.placaEntre([p[0], p[1] + 0.8 * e], [t[0], t[1] - 0.3 * e], 3.6 * e, 2.4 * e, ra, Object.assign({ punta: 0.6 * e }, B)));
      }
      // el tronco: peto con arista, faldones y el tabardo
      K.pieza(K.zTronco, () => {
        K.placa([[ch[0] - 4.6 * e, ch[1] - 1.6 * e], [ch[0] + 4.8 * e, ch[1] - 2 * e], [hi[0] + 4.2 * e, hi[1] - 0.5 * e], [hi[0] - 4 * e, hi[1]]], piedra, B);
        K.vara([ch[0] + 0.6 * e, ch[1] - 1.6 * e], [hi[0] + 0.4 * e, hi[1] - e], piedra, { k: 5 });
        grietas([ch[0] - e, ch[1] + 2 * e], 5 * e, 11);
        for (let k = 0; k < 2; k++) K.placa([[hi[0] - 4.6 * e, hi[1] + (k * 1.6 - 0.6) * e], [hi[0] + 4.6 * e, hi[1] + (k * 1.6 - 0.8) * e], [hi[0] + 4.8 * e, hi[1] + (k * 1.6 + 1) * e], [hi[0] - 4.8 * e, hi[1] + (k * 1.6 + 1.2) * e]], piedra, B);
      });
      K.pieza(K.zTronco + 0.4, () => K.placa([[hi[0] - 1.6 * e, hi[1] + 2 * e], [hi[0] + 2.4 * e, hi[1] + 2 * e], [hi[0] + 2.8 * e, kn + 1.5 * e], [hi[0] - 1.8 * e, kn + 1.5 * e]], oscura, Object.assign({ corta: hem(kn + 1.5 * e, 2) }, B)));
      // brazos: brazales, guanteletes y hombreras de tres láminas
      for (const l of ['l', 'r']) {
        const u = S['upperarm' + l], m = S['lowerarm' + l], hd = S['hand' + l], ra = l === 'l' ? oscura : piedra, z0 = (u[2] + m[2]) / 2;
        K.pieza(z0 + 0.1, () => K.placaEntre(u, m, 3.6 * e, 3.2 * e, ra, B));
        K.pieza((m[2] + hd[2]) / 2 + 0.2, () => K.placaEntre(m, hd, 3.0 * e, 2.6 * e, ra, B));
        K.pieza(hd[2] + 0.3, () => K.placa([[hd[0] - 1.3 * e, hd[1] - 1.2 * e], [hd[0] + 1.3 * e, hd[1] - 1.3 * e], [hd[0] + 1.5 * e, hd[1] + 1.2 * e], [hd[0] - 1.2 * e, hd[1] + 1.3 * e]], ra, B));
        K.pieza(u[2] + 0.5, () => {
          for (let k = 2; k >= 0; k--) {
            const r = (4.6 - k * 0.5) * e, c = [u[0], u[1] + k * 1.5 * e - 0.8 * e], pts = [];
            for (let a = 0; a <= 8; a++) { const an = Math.PI * (1.05 + a * 0.1125); pts.push([c[0] + Math.cos(an) * r, c[1] + Math.sin(an) * r * 0.75]); }
            pts.push([c[0] + r * 0.9, c[1] + 0.9 * e], [c[0] - r * 0.9, c[1] + 0.9 * e]);
            K.placa(pts, ra, B);
          }
          if (l === 'r') musgo([u[0] - e, u[1] - 4 * e], 6, 21);
        });
      }
      // el velo de piedra sobre la cabeza y, dentro, la rendija de luz fría
      K.pieza(h[2] + 2, () => {
        K.placa([[h[0] - 5.4 * e, h[1] + 2 * e], [h[0] - 5.2 * e, h[1] - 5.2 * e], [h[0] - 1.6 * e, h[1] - 9.4 * e], [h[0] + 3.2 * e, h[1] - 7.4 * e], [h[0] + 5 * e, h[1] - 1.8 * e], [h[0] + 4.6 * e, h[1] + 2.4 * e]], piedra, B);
        K.placa([[h[0] + 0.4 * e, h[1] - 6.8 * e], [h[0] + 4.2 * e, h[1] - 4.6 * e], [h[0] + 4.4 * e, h[1] + 1.6 * e], [h[0] + 0.6 * e, h[1] + 1.6 * e]], ['#020204', '#05060a', '#080a10', '#0c0e16'], { bisel: 0 });
        if (vivo) for (let y = -5.5; y <= 0.5; y += 1 / e * 1) K.px(h[0] + 2.4 * e, h[1] + y * e, Math.abs(y + 2.5) < 0.6 ? OJO.frio[1] : OJO.frio[0], piedra, true);
        musgo([h[0] - 2 * e, h[1] - 10 * e], 8, 31); grietas([h[0] - 3.5 * e, h[1] - 4 * e], 3 * e, 37);
      });
      // el espadón: hoja tallada con runas que brillan
      const v = vara(K, 4 * e, 4 * e, piedra, { grosor: 2 });
      K.pieza(v.z + 0.02, () => {
        const b = v.b, d = v.d, n = v.n, L = 16 * e, w = 1.5 * e;
        K.placaEntre([b[0] - n[0] * 4 * e, b[1] - n[1] * 4 * e], [b[0] + n[0] * 4 * e, b[1] + n[1] * 4 * e], 1.4 * e, 1.4 * e, piedra, B);
        K.placa([[b[0] - n[0] * w, b[1] - n[1] * w], [b[0] + d[0] * L - n[0] * w * 0.85, b[1] + d[1] * L - n[1] * w * 0.85], [b[0] + d[0] * (L + 3.4 * e), b[1] + d[1] * (L + 3.4 * e)], [b[0] + d[0] * L + n[0] * w * 0.85, b[1] + d[1] * L + n[1] * w * 0.85], [b[0] + n[0] * w, b[1] + n[1] * w]], piedra, B);
        if (vivo) for (let k = 3; k < 15; k += 3) { const q = [b[0] + d[0] * k * e, b[1] + d[1] * k * e]; K.px(q[0], q[1], OJO.frio[0], piedra, true); K.px(q[0] + d[0] * 1.2, q[1] + d[1] * 1.2, OJO.frio[1], piedra, true); }
      });
      // la lámpara en la izquierda, colgando de una cadena (en alto cuando llama a las runas)
      const hl = S.handl, alta = K.anim === 's';
      K.pieza(hl[2] + (alta ? 0.6 : -0.2), () => {
        const lp = alta ? [hl[0], hl[1] - 4.5 * e] : [hl[0] + 0.5 * e, hl[1] + 5 * e];
        if (!alta) for (let k = 0; k < 3 * e; k += 2) K.px(hl[0] + 0.5 * e * (k / (3 * e)), hl[1] + k, piedra[1], piedra);
        K.placa([[lp[0] - 1.8 * e, lp[1] - 2.6 * e], [lp[0] + 1.8 * e, lp[1] - 2.6 * e], [lp[0] + 2.2 * e, lp[1] + 2.4 * e], [lp[0] - 2.2 * e, lp[1] + 2.4 * e]], oscura, B);
        K.ovalo(lp, 0.8 * e, 1.2 * e, ['#2a5a9a', '#4a86c8', '#7ab2f0', '#b4dcff', '#e8f6ff'], { luz: vivo, mate: true });
        K.puntos = Object.assign(K.puntos || {}, { lampara: lp });
      });
    },
  };

  // MURDO, el Rey Ciego: topo colosal, calvo a parches, sin ojos; nariz de estrella de tentáculos; corona de clavos de
  // hierro hundidos en el cráneo; garras de pala.
  DEF.topo = {
    jefe: true, W: 200, H: 150, ox: 92, oy: 142, px: 44, yaw: 1.05, fuente: ANIM_ESQUELETO,
    prop: { pierna: 0.7, brazo: 1.0, hombros: 1.25, caderas: 1.2, encorvar: 0.45, cuello: 0.45, cadera: 0.39 },
    anims: {
      q: { clip: 'Idle', n: 4, bucle: true }, a: { clip: 'Walking_D_Skeletons', n: 8, bucle: true },
      d: { clip: 'Taunt', n: 6 }, t: { clip: 'Throw', n: 5, de: 0.25, a: 0.8 },
      x: { clip: 'Spawn_Ground', n: 5, de: 0.6, a: 0.05 }, s: { clip: 'Spawn_Ground', n: 5, de: 0.15, a: 0.8 },
      w: { clip: '2H_Melee_Attack_Chop', n: 4, de: 0, a: 0.35 }, c: { clip: 'Running_B', n: 6, bucle: true },
      z: { clip: 'Hit_B', n: 4, de: 0.1, a: 0.5 }, r: { clip: 'Hit_A', n: 3, de: 0.1, a: 0.6 }, m: { clip: 'Death_A', n: 8, raiz: 0.3 },
    },
    cuadroJefe(j, t) {
      const e = j.estado;
      if (e === 'dormido') return ['q', 0];
      if (e === 'despierta') return ['d', t / 1.6];
      if (e === 'muriendo') return ['m', t / 1.8];
      if (j.saleT && J.t - j.saleT < 0.6) return ['s', (J.t - j.saleT) / 0.6];
      if (e === 'rocas') return ['t', 1 - Math.max(0, j.t) / 0.55];
      if (e === 'excava') return ['x', t / 0.6];
      if (e === 'embiste') return ['w', t / 0.6];
      if (e === 'carga') return ['c', (J.t * 2.4) % 1];
      if (e === 'aturdido') return ['z', 0.5 + 0.5 * Math.sin(J.t * 3)];
      if (j.golpeadoT && J.t - j.golpeadoT < 0.25) return ['r', (J.t - j.golpeadoT) / 0.25];
      if (e === 'persigue') return ['a', (J.t * 1.3) % 1];
      return ['q', (J.t * 0.7) % 1];
    },
    dibujar(K) {
      const S = K.S, e = K.D.px / 13;
      const pelo = ['#0e0a0a', '#221a18', '#3a2e28', '#544238', '#6e5a4a'], calva = ['#3a1a1c', '#7a3a3a', '#b0605a', '#d88a7c', '#f0b0a0'];
      const garra = ['#3a2e1a', '#8a7448', '#c4ac74', '#ead8a8', '#fff6dc'];
      const pelaje = (x, y) => (ruido(x, Math.floor(y / 3), 2) < 0.16 ? -1 : 0); // mechones: trazos cortos más oscuros
      // piernas cortas y gruesas
      for (const l of ['l', 'r']) {
        const u = S['upperleg' + l], m = S['lowerleg' + l], p = S['foot' + l], t = S['toes' + l];
        K.pieza((u[2] + m[2]) / 2 - 0.3, () => K.cap(u, m, 3.4 * e, 3.0 * e, pelo, { tex: pelaje }));
        K.pieza((m[2] + p[2]) / 2 - 0.2, () => K.cap(m, p, 3.0 * e, 2.6 * e, pelo, { tex: pelaje }));
        K.pieza((p[2] + t[2]) / 2, () => { K.cap([p[0], p[1] + e], t, 2.4 * e, 2.0 * e, calva); for (let k = 0; k < 3; k++) K.cap([t[0] + (k - 1) * e, t[1]], [t[0] + (k - 0.5) * e + 1.5 * e, t[1] + 0.5 * e], 0.7 * e, 0.4 * e, garra); });
      }
      const hi = S.hips, ch = S.chest, h = S.head;
      K.zTronco = (hi[2] + ch[2]) / 2 - 0.5;
      // el cuerpo: una bola enorme de pelo, calva a parches
      K.pieza(K.zTronco, () => {
        const c = [(hi[0] + ch[0]) / 2, (hi[1] + ch[1]) / 2];
        K.ovalo(c, 8.6 * e, 8.2 * e, pelo, { tex: pelaje, ang: Math.atan2(ch[1] - hi[1], ch[0] - hi[0]) + Math.PI / 2 });
        // calvo a parches: la barriga y dos calvas en el lomo
        K.ovalo([c[0] + 3.2 * e, c[1] + 2.4 * e], 4.2 * e, 5 * e, calva);
        K.ovalo([c[0] - 3.5 * e, c[1] - 3.5 * e], 2 * e, 1.5 * e, calva, { ang: -0.5 });
        K.ovalo([c[0] - 5.5 * e, c[1] + 1.5 * e], 1.4 * e, 1.8 * e, calva);
        for (let k = 0; k < 5; k++) K.vara([c[0] + 1.5 * e + k * 0.9 * e, c[1] + (0.5 + k * 0.4) * e], [c[0] + 1.8 * e + k * 0.9 * e, c[1] + (2.5 + k * 0.4) * e], calva, { k: 1 }); // pliegues
      });
      // brazos enormes con garras de pala
      for (const l of ['l', 'r']) {
        const u = S['upperarm' + l], m = S['lowerarm' + l], hd = S['hand' + l], lejos = l === 'l';
        K.pieza((u[2] + m[2]) / 2 + (lejos ? -0.5 : 0.5), () => { K.cap(u, m, 3.0 * e, 2.8 * e, pelo, { tex: pelaje }); K.cap(m, hd, 2.8 * e, 2.6 * e, pelo, { tex: pelaje }); });
        K.pieza(hd[2] + (lejos ? -0.4 : 0.6), () => {
          const dir = [hd[0] - m[0], hd[1] - m[1]], L = Math.hypot(...dir) || 1, d = [dir[0] / L, dir[1] / L], n = [-d[1], d[0]];
          K.ovalo([hd[0], hd[1]], 3 * e, 2.6 * e, calva);
          for (let k = -2; k <= 2; k++) { const b = [hd[0] + n[0] * k * 1.3 * e + d[0] * e, hd[1] + n[1] * k * 1.3 * e + d[1] * e]; K.cap(b, [b[0] + d[0] * 4.2 * e + n[0] * k * 0.4 * e, b[1] + d[1] * 4.2 * e + n[1] * k * 0.4 * e], 0.9 * e, 0.35 * e, garra); }
        });
      }
      // la cabeza: hocico sin ojos con la nariz de estrella y la corona de clavos
      K.pieza(h[2] + 2, () => {
        const c = [h[0] + 2 * e, h[1] - 1 * e];
        K.ovalo(c, 4.6 * e, 4 * e, pelo, { tex: pelaje });
        K.cap([c[0] + 1.5 * e, c[1] + 0.5 * e], [c[0] + 5.5 * e, c[1] + 1.6 * e], 2.6 * e, 1.8 * e, calva); // el hocico pelado
        const nz = [c[0] + 6.8 * e, c[1] + 1.6 * e];
        for (let k = 0; k < 11; k++) { const a = (k / 11) * Math.PI * 2; K.cap(nz, [nz[0] + Math.cos(a) * 2.6 * e, nz[1] + Math.sin(a) * 2.2 * e], 0.6 * e, 0.35 * e, ['#4a1020', '#a03050', '#e05a7a', '#ff9ab0', '#ffd0dc']); }
        K.ovalo(nz, 0.9 * e, 0.9 * e, ['#2a0a14', '#6a1a30', '#a0304a', '#c04a60'], { mate: true });
        K.vara([c[0] + 3 * e, c[1] + 3.2 * e], [c[0] + 6 * e, c[1] + 3.4 * e], [0, OSCURO, OSCURO], { k: 2 }); // la boca
        for (let k = 0; k < 6; k++) { const a = -Math.PI * 0.85 + k * 0.32, b = [c[0] + Math.cos(a) * 3.6 * e, c[1] + Math.sin(a) * 3.2 * e]; K.cap(b, [b[0] + Math.cos(a) * 3 * e, b[1] + Math.sin(a) * 3 * e - e], 0.7 * e, 0.3 * e, R.metal); } // la corona de clavos
      });
    },
  };

  // OZMAR, el Último Velador: cáscara flotante en túnica de sacerdote; dentro solo Podre; máscara de porcelana rota
  // con un ojo; brazos largos como raíces.
  DEF.ozmar = {
    jefe: true, W: 200, H: 180, ox: 90, oy: 172, px: 44, yaw: 1.0, fuente: ANIM_ESQUELETO,
    prop: { pierna: 0.9, brazo: 1.55, hombros: 0.9, caderas: 0.8, encorvar: 0.25, cuello: 0.7, tronco: 1.2, cadera: 0.39 },
    anims: {
      q: { clip: 'Idle', n: 6, bucle: true }, d: { clip: 'Spellcast_Summon', n: 6, de: 0, a: 0.5 },
      o: { clip: 'Spellcast_Shoot', n: 5, de: 0, a: 0.8 }, s: { clip: 'Spellcast_Summon', n: 6, de: 0.1, a: 0.7 },
      c: { clip: 'Hit_B', n: 3, de: 0.3, a: 0.6 }, r: { clip: 'Hit_A', n: 3, de: 0.1, a: 0.6 }, m: { clip: 'Death_A', n: 8, raiz: 0.3 },
    },
    cuadroJefe(j, t) {
      const e = j.estado;
      if (e === 'dormido') return ['c', 1];
      if (e === 'despierta') return ['d', t / 1.6];
      if (e === 'muriendo') return ['m', t / 1.8];
      if (e === 'orbes' || e === 'anillo' || e === 'pilares') return ['o', 1 - Math.max(0, j.t) / 0.85];
      if (e === 'invoca') return ['s', t / 0.6];
      if (e === 'cansado') return ['c', 1];
      if (j.golpeadoT && J.t - j.golpeadoT < 0.25) return ['r', (J.t - j.golpeadoT) / 0.25];
      return ['q', (J.t * 0.5) % 1];
    },
    dibujar(K) {
      const S = K.S, e = K.D.px / 13, u = K.uc || 0;
      const raiz = ['#0e0806', '#24160e', '#3e2818', '#5a3e26', '#7a5838'], porcelana = ['#3a3440', '#8a8494', '#c8c4d0', '#eeeaf2', '#ffffff'];
      const hi = S.hips, ch = S.chest, h = S.head, abajo = K.D.oy - 1.5 * e;
      K.zTronco = (hi[2] + ch[2]) / 2 - 0.5;
      // la túnica: cae desde los hombros en campana y se deshace en jirones (no hay piernas: flota)
      K.pieza(K.zTronco, () => {
        const ond = (k) => Math.sin(u * Math.PI * 2 + k) * 1.2 * e;
        K.poli([[ch[0] - 4.5 * e, ch[1] - e], [ch[0] + 4.2 * e, ch[1] - 1.2 * e], [hi[0] + 6 * e + ond(1), abajo], [hi[0] - 8 * e + ond(4), abajo - 0.5 * e]], VELADOR, {
          normal: (x, y) => [((x - hi[0] + e) / (7 * e)) * 0.85, -0.1], corta: (x, y) => y > abajo - (0.5 + ruido(x >> 1, 3, 4) * 2.6) * e,
        });
        // el pecho abierto: dentro, la Podre que brilla
        K.ovalo([ch[0] + 1.4 * e, ch[1] + 3.5 * e], 1.8 * e, 3 * e, ['#1a0414', '#5a0c44', '#a01e78', '#e0309e', '#ff6ac8'], { luz: true, mate: true });
        // los bordes de oro de la túnica
        K.vara([ch[0] - 0.2 * e, ch[1] + 6.5 * e], [hi[0] + 1.5 * e + ond(1) * 0.3, abajo - 1.5 * e], ORO, { k: 3 });
        K.vara([ch[0] - 4.2 * e, ch[1]], [ch[0] + 3.8 * e, ch[1] - 0.5 * e], ORO, { grosor: 2 });
        for (let x = hi[0] - 7 * e; x < hi[0] + 5.5 * e; x += 1) { const y = abajo - (0.6 + ruido(Math.floor(x) >> 1, 3, 4) * 2.6) * e - 1; if (ruido(Math.floor(x), 9, 2) < 0.5) K.px(x, y, ORO[2], VELADOR); }
      });
      // brazos largos como raíces, con dedos que se abren
      for (const l of ['l', 'r']) {
        const a = S['upperarm' + l], m = S['lowerarm' + l], hd = S['hand' + l], lejos = l === 'l';
        K.pieza(lejos ? K.zTronco - 1 : Math.max(K.zTronco + 1, (a[2] + m[2]) / 2 + 1), () => {
          K.cap(a, m, 1.5 * e, 1.2 * e, VELADOR); K.cap(m, hd, 1.1 * e, 0.8 * e, raiz);
          const d = [hd[0] - m[0], hd[1] - m[1]], L = Math.hypot(...d) || 1;
          for (let k = -2; k <= 2; k++) { const an = Math.atan2(d[1], d[0]) + k * 0.35; K.cap([hd[0], hd[1]], [hd[0] + Math.cos(an) * 4.5 * e, hd[1] + Math.sin(an) * 4.5 * e], 0.55 * e, 0.25 * e, raiz); }
          if (K.anim === 'o' || K.anim === 's') K.ovalo([hd[0] + d[0] / L * 3 * e, hd[1] + d[1] / L * 3 * e], 1.5 * e, 1.5 * e, ['#3a0f30', '#a01e78', '#ff3cb4', '#ff7ad0', '#ffd0ee'], { luz: true, mate: true });
        });
      }
      // la capucha y la máscara de porcelana rota, con un solo ojo
      K.pieza(h[2] + 2, () => {
        K.ovalo([h[0] - 0.5 * e, h[1] - 3 * e], 5 * e, 5.4 * e, VELADOR);
        const c = [h[0] + 1.6 * e, h[1] - 2.6 * e];
        K.ovalo(c, 3 * e, 3.8 * e, porcelana, { brillo: true });
        K.ovalo([c[0] + 1 * e, c[1] - 0.6 * e], 0.9 * e, 1.1 * e, [OSCURO, OSCURO, OSCURO, OSCURO]);
        K.px(c[0] + 1 * e, c[1] - 0.6 * e, OJO.podre[0], porcelana, true); K.px(c[0] + 1 * e + 1, c[1] - 0.6 * e, OJO.podre[1], porcelana, true);
        for (let k = 0; k < 5; k++) K.px(c[0] - 1.5 * e + k * 0.5, c[1] - 1 * e + k * 0.9, porcelana[0], porcelana); // la grieta
        K.ovalo([c[0] - 1.4 * e, c[1] + 0.4 * e], 0.8 * e, 0.6 * e, ['#160410', '#300a24', '#4a103a', '#6a1a52']); // el trozo que falta
      });
    },
  };

  // VHESSA, Madre de la Colmena: gusana reina hinchada; saco de huevos translúcido con larvas que se mueven dentro;
  // racimo de ojos negros, mandíbulas. Esqueleto propio: el saco detrás y el cuerpo que se alza hacia delante.
  DEF.reina = {
    jefe: true, W: 200, H: 160, ox: 96, oy: 152, px: 1,
    anims: {
      z: { proc: 'duerme', n: 1 }, d: { proc: 'alza', n: 6 }, q: { proc: 'mece', n: 6, bucle: true },
      a: { proc: 'escupe', n: 5 }, l: { proc: 'pone', n: 4, bucle: true }, s: { proc: 'golpe', n: 5 },
      r: { proc: 'duele', n: 2 }, m: { proc: 'muere', n: 7 },
    },
    cuadroJefe(j, t) {
      const e = j.estado;
      if (e === 'dormido') return ['z', 0];
      if (e === 'despierta') return ['d', t / 1.6];
      if (e === 'muriendo') return ['m', t / 1.8];
      if (e === 'acido') return ['a', 1 - Math.max(0, j.t) / 0.75];
      if (e === 'puesta') return ['l', (J.t * 2) % 1];
      if (e === 'derrumbe') return ['s', 1 - Math.max(0, j.t) / 0.7];
      if (j.golpeadoT && J.t - j.golpeadoT < 0.25) return ['r', (J.t - j.golpeadoT) / 0.25];
      return ['q', (J.t * 0.45) % 1];
    },
    dibujar(K) {
      const D = K.D, A = K.A, u = fase(K), suelo = D.oy;
      const piel = ['#3a2c18', '#7a6440', '#b49a68', '#dcc898', '#f6ecca'], saco = ['#4a4020', '#a09048', '#d8c878', '#f0e6aa', '#fffbe0'];
      let alza = 0.75, inc = 0, abre = 0.2, pulso = 0, cae = 0, mece = 0;
      if (A.proc === 'duerme') { alza = 0; abre = 0; }
      if (A.proc === 'alza') { alza = u * 0.9; abre = u; }
      if (A.proc === 'mece') { mece = Math.sin(u * Math.PI * 2); pulso = mece * 0.03; }
      if (A.proc === 'escupe') { alza = 0.85 - Math.sin(u * Math.PI) * 0.15; inc = u < 0.5 ? -u * 0.6 : -0.3 + (u - 0.5) * 1.6; abre = u < 0.6 ? u * 1.6 : 1; }
      if (A.proc === 'pone') { pulso = Math.sin(u * Math.PI * 2) * 0.07; }
      if (A.proc === 'golpe') { alza = u < 0.5 ? 0.75 + u * 0.5 : 1 - (u - 0.5) * 1.6; abre = 0.6; }
      if (A.proc === 'duele') { inc = -0.25; alza = 0.65; }
      if (A.proc === 'muere') { cae = u; alza = 0.75 * (1 - u); abre = 0.4; }
      // el saco de huevos (detrás, a la izquierda): translúcido, con larvas que se mueven dentro y venas
      const sc = [D.ox - 34, suelo - 28 + cae * 8], srx = 40 * (1 + pulso), sry = 28 * (1 - pulso * 0.6) * (1 - cae * 0.25);
      K.pieza(0, () => {
        K.ovalo(sc, srx, sry, Object.assign(saco.slice(), { sinLinea: true, arriba: 3 }), { corta: (x, y) => y > suelo });
        for (let k = 0; k < 9; k++) { // las larvas dentro: bultos más oscuros (vistos a través) que se mueven despacio
          const a = (k / 9) * Math.PI * 2 + (K.anim === 'l' ? u * 2 : (K.uc || 0) * 0.6), r = 0.35 + (k % 3) * 0.17;
          const c = [sc[0] + Math.cos(a) * srx * r, sc[1] + Math.sin(a) * sry * r * 0.8];
          K.ovalo(c, 3.2, 2.2, [saco[2], ESCU.mezclar(saco[1], '#4a3a20', 0.35), ESCU.mezclar(saco[2], '#6a5a30', 0.3), saco[3]], { mate: true, ang: a });
        }
        for (let k = 0; k < 5; k++) { let x = sc[0] - srx * 0.7 + k * srx * 0.35, y = sc[1] - sry * 0.6; for (let i = 0; i < 9; i++) { K.px(x, y, saco[1], saco); x += ruido(k, i, 3) < 0.5 ? 1 : -1; y += 1.5; } } // venas
      });
      // el cuerpo: anillos que suben del saco a la cabeza (se alza y se echa hacia atrás o delante)
      const N = 7, pts = [];
      for (let i = 0; i < N; i++) {
        const t = i / (N - 1), r = 16 - t * 4.5;
        const ang = (-0.15 - alza * 1.25 * t) + inc * t + mece * 0.05 * t;
        const prev = pts.length ? pts[pts.length - 1] : [sc[0] + srx * 0.55, suelo - 12, 0];
        const paso = i === 0 ? 0 : 12;
        pts.push([prev[0] + Math.cos(ang) * paso, prev[1] + Math.sin(ang) * paso, r]);
      }
      pts.forEach(([x, y, r], i) => K.pieza(1 + i, () => {
        K.ovalo([x, Math.min(y, suelo - r * 0.7)], r, r * 0.92, piel, { corta: (xx, yy) => yy > suelo });
        if (i > 0) for (let k = -1; k <= 1; k += 2) K.vara([x + k * r * 0.2, y + r * 0.6], [x + k * r * 0.35, Math.min(suelo, y + r + 4)], piel, { k: 1 }); // patitas
      }));
      // la cabeza: racimo de ojos negros y mandíbulas que se abren
      const [hx, hy, hr] = pts[N - 1], cab = [hx + 6, hy - 2];
      K.pieza(N + 2, () => {
        K.ovalo(cab, 14, 12, piel);
        for (const [dx, dy, r] of [[2, -5, 2.4], [6, -3.5, 2], [-1.5, -3, 1.8], [4.5, -7, 1.6], [8.5, -6, 1.4], [0.5, -7.5, 1.3], [-3.5, -6, 1.2]]) {
          K.ovalo([cab[0] + dx, cab[1] + dy], r, r, ['#000000', '#05060a', '#0c0e16', '#1a1e2c', '#6a7488'], { brillo: true });
          K.px(cab[0] + dx - r * 0.4, cab[1] + dy - r * 0.4, '#c8d4e8', piel);
        }
        // mandíbulas
        const ab = abre * 0.6;
        for (const s of [-1, 1]) {
          const b0 = [cab[0] + 8, cab[1] + 4 + s * 2], an = 0.25 + s * (0.35 + ab);
          const m1 = [b0[0] + Math.cos(an) * 8, b0[1] + Math.sin(an) * 8], m2 = [m1[0] + Math.cos(an - s * 1.2) * 6, m1[1] + Math.sin(an - s * 1.2) * 6];
          K.cap(b0, m1, 2.4, 1.8, R.hueso); K.cap(m1, m2, 1.8, 0.6, R.hueso);
        }
        if (abre > 0.5) { K.ovalo([cab[0] + 9, cab[1] + 4], 3, 2 + abre * 2, ['#100608', '#1e0a0e', '#300e16', '#401420'], { mate: true }); for (let k = 0; k < 3; k++) K.px(cab[0] + 9 + k - 1, cab[1] + 7 + abre * 2 + k, ACIDO[2 + (k % 2)], piel, true); }
      });
      K.puntos = { boca: [cab[0] + 10, cab[1] + 4] };
    },
  };

  // GRUL, el Sapo Hueco: saco de carne muerta; la espalda es un bosque de Podre con púas de hueso; ojos en racimo,
  // pequeños y rojos; por la boca asoma el brazo de un Velador. Se estira y se aplasta con el Slime de Quaternius.
  DEF.grul = {
    jefe: true, simetrico: true, W: 220, H: 160, ox: 110, oy: 152, px: 1, fuente: ANIM_BABA,
    anims: {
      z: { proc: 'duerme', n: 1 }, q: { clip: 'Slime_Idle', n: 6, bucle: true }, p: { proc: 'agacha', n: 3 }, g: { clip: 'Slime_Attack', n: 3, de: 0.3, a: 0.6 },
      b: { proc: 'boca', n: 4 }, t: { proc: 'traga', n: 4, bucle: true }, r: { proc: 'golpe', n: 2 }, m: { clip: 'Slime_Death', n: 6 },
    },
    cuadroJefe(j, t) {
      const e = j.estado;
      if (e === 'dormido') return ['z', 0];
      if (e === 'muriendo') return ['m', t / 1.8];
      if (j.salto) return j.z > 4 ? ['g', Math.min(1, j.salto.t / j.salto.dur) ] : ['p', 1];
      if (e === 'salto' && j.paso === 0) return ['p', 1 - Math.max(0, j.t) / 0.7];
      if (e === 'lengua' || e === 'escupe') return ['b', Math.min(1, t / 0.4)];
      if (e === 'traga') return ['t', (J.t * 3) % 1];
      if (j.golpeadoT && J.t - j.golpeadoT < 0.25) return ['r', (J.t - j.golpeadoT) / 0.25];
      return ['q', (J.t * 0.4) % 1];
    },
    dibujar(K) {
      // de frente (como en LORE.md): la boca de lado a lado, los ojos en racimo arriba y la Podre por detrás
      const D = K.D, A = K.A, u = fase(K);
      const carne = ['#10140c', '#262e1e', '#3e4a30', '#5a6844', '#7c8a5e', '#a0ac7c'], vientre = ['#2a2218', '#5e4e36', '#8a7656', '#b09c78', '#d0c09a'];
      const mancha = ['#0e100a', '#1e2418', '#2e3824', '#404c32'];
      let sy = 1, sx = 1, abre = 0.05, dormido = false, traga = 0, lengua = 0;
      if (A.proc === 'duerme') { dormido = true; sy = 0.94; sx = 1.04; }
      else if (A.proc === 'agacha') { const k = Math.sin(u * Math.PI / 2); sy = 1 - 0.22 * k; sx = 1 + 0.14 * k; }
      else if (A.proc === 'golpe') { sy = 0.88; sx = 1.08; }
      else if (A.proc === 'boca') { abre = 0.15 + 0.85 * u; lengua = u; }
      else if (A.proc === 'traga') { abre = 1; traga = Math.sin(u * Math.PI * 2); sx = 1 + traga * 0.03; }
      else { const [a, b] = aplasto(K); sy = a; sx = b; if (K.anim === 'm') { abre = 0.3; dormido = u > 0.5; } }
      const cx = D.ox, base = D.oy, rx = 52 * sx, ry = 38 * sy, cy = base + 0.5 - ry * 0.86;
      // detrás: el bosque de Podre y las púas de hueso que asoman por encima del lomo
      K.pieza(-2, () => {
        for (let k = 0; k < 11; k++) {
          const a = Math.PI * (1.1 + k * 0.08), x = cx + Math.cos(a) * rx * 0.9, y = cy + Math.sin(a) * ry * 0.98 - 3;
          if (k % 2 === 0) { const L = 12 + (k % 3) * 5; K.cap([x, y], [x + Math.cos(a) * 3, y - L], 2.6, 0.6, R.hueso); }
          K.ovalo([x, y], 5 + (k % 3), 4 + (k % 2), PODRE, { luz: !dormido || k % 3 === 0 });
        }
      });
      // patas de atrás, dobladas a los lados
      K.pieza(-1, () => { for (const s of [-1, 1]) K.ovalo([cx + s * rx * 0.86, base - 11 * sy], 17 * sx, 12 * sy, carne, { tex: (x, y) => (ruido(x >> 2, y >> 2, 3) < 0.12 ? -1 : 0) }); });
      // el saco de carne muerta, con manchas y verrugas
      K.pieza(0, () => {
        K.ovalo([cx, cy], rx, ry, carne);
        for (const [dx, dy, r] of [[-0.55, -0.35, 7], [0.4, -0.5, 5], [-0.2, -0.65, 4], [0.65, 0.05, 6], [-0.75, 0.1, 5]]) K.ovalo([cx + dx * rx, cy + dy * ry], r * sx, r * 0.8 * sy, mancha, { mate: true });
        for (let k = 0; k < 18; k++) { const x = cx + (ruido(k, 1, 5) - 0.5) * rx * 1.6, y = cy + (ruido(k, 2, 5) - 0.75) * ry * 1.2; if (((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 < 0.8) { K.px(x, y, carne[5], carne); K.px(x, y + 1, carne[2], carne); } } // verrugas
        K.ovalo([cx, cy + ry * 0.5], rx * 0.62, ry * 0.42, vientre, { mate: true, corta: (x, y) => y > base });
      });
      // la boca de lado a lado: cerrada es una raja; abierta, una cueva con dos filas de dientes
      K.pieza(1, () => {
        const by = cy + ry * 0.22, ancho = rx * 0.78, alto = 1.5 + abre * ry * 0.75;
        if (abre < 0.12) {
          for (let x = -ancho; x <= ancho; x++) { const y = by + (x / ancho) ** 2 * -5 + 5; K.px(cx + x, y, '#080406', carne); K.px(cx + x, y + 1, carne[1], carne); }
        } else {
          K.ovalo([cx, by + alto * 0.45], ancho, alto * 0.6 + 2, ['#060204', '#100408', '#1a080e', '#240c14'], { mate: true });
          for (let k = -6; k <= 6; k++) { const x = cx + k * ancho * 0.15, yt = by + alto * 0.45 - (alto * 0.6 + 1) * Math.sqrt(Math.max(0, 1 - (k / 6.6) ** 2)), yb = by + alto * 0.45 + (alto * 0.6 + 1) * Math.sqrt(Math.max(0, 1 - (k / 6.6) ** 2));
            K.cap([x, yt], [x, yt + 3 + (k % 2)], 1.1, 0.3, R.hueso); K.cap([x + 1, yb], [x + 1, yb - 3 - ((k + 1) % 2)], 1.1, 0.3, R.hueso); }
          if (lengua > 0.2 || traga) { const lg = ['#3a0a1a', '#8a1e3e', '#c83a5e', '#ee6a86', '#ffa0b4'], L = 6 + lengua * 26; K.cap([cx + 4, by + alto * 0.5], [cx + 6, by + alto * 0.5 + L], 6, 4.5, lg); K.ovalo([cx + 6, by + alto * 0.5 + L], 7, 4, lg); }
        }
        // el brazo de un Velador que asoma por la comisura: manga azul, mano de hueso
        const c0 = [cx - ancho * 0.82, by + 3 + abre * 6];
        K.cap(c0, [c0[0] - 8, c0[1] + 10], 3, 2.6, VELADOR);
        K.cap([c0[0] - 8, c0[1] + 10], [c0[0] - 10, c0[1] + 15], 1.8, 1.4, R.hueso);
        for (let k = 0; k < 4; k++) K.vara([c0[0] - 10, c0[1] + 15], [c0[0] - 13 + k * 1.6, c0[1] + 19], R.hueso, { k: 3 });
      });
      // los ojos en racimo, pequeños y rojos, arriba en el centro
      K.pieza(2, () => {
        const ox = cx, oy = cy - ry * 0.5;
        for (const [dx, dy] of [[0, 0], [-5, 1], [5, 1], [-2.5, -3.5], [2.5, -3.5], [-8, -2], [8, -2], [0, -6.5], [-4, 4], [4, 4]]) {
          K.ovalo([ox + dx, oy + dy], 2, 1.8, carne);
          if (!dormido) { K.px(ox + dx, oy + dy, OJO.rojo[0], carne, true); K.px(ox + dx + 1, oy + dy, OJO.rojo[1], carne, true); }
          else K.px(ox + dx, oy + dy + 0.5, carne[0], carne);
        }
      });
      // las patas de delante, abiertas, con los dedos en el suelo
      K.pieza(3, () => { for (const s of [-1, 1]) { const h = [cx + s * rx * 0.55, base - 1]; K.cap([cx + s * rx * 0.45, cy + ry * 0.55], h, 5, 4, carne); for (let k = -1; k <= 1; k++) K.cap(h, [h[0] + s * 3 + k * 4, base + 0.5], 1.8, 1.2, carne); } });
      K.puntos = { boca: [cx, cy + ry * 0.3] };
    },
  };

  // HIEDRA, la Flor de Sangre: flor gigante de pétalos de carne; en el centro, el cadáver del Velador que intentó
  // quemarla; látigos de espinas con bocas. No se mueve ni se gira (de frente). Esqueleto propio.
  DEF.hiedra = {
    jefe: true, simetrico: true, W: 230, H: 200, ox: 115, oy: 192, px: 1,
    anims: {
      c: { proc: 'capullo', n: 1 }, d: { proc: 'abre', n: 6 }, q: { proc: 'mece', n: 6, bucle: true }, o: { proc: 'late', n: 4, bucle: true },
      l: { proc: 'latigo', n: 6 }, z: { proc: 'cae', n: 3 }, r: { proc: 'duele', n: 2 }, m: { proc: 'muere', n: 7 },
    },
    cuadroJefe(j, t) {
      const e = j.estado;
      if (e === 'dormido') return ['c', 0];
      if (e === 'despierta') return ['d', t / 1.6];
      if (e === 'muriendo') return ['m', t / 1.8];
      if (e === 'latigo') return ['l', t / 1.0];
      if (e === 'aturdido') return ['z', Math.min(1, t / 0.4)];
      if (e === 'raices' || e === 'esporas' || e === 'brotes') return ['o', (J.t * 2) % 1];
      if (j.golpeadoT && J.t - j.golpeadoT < 0.25) return ['r', (J.t - j.golpeadoT) / 0.25];
      return ['q', (J.t * 0.4) % 1];
    },
    dibujar(K) {
      const D = K.D, A = K.A, u = fase(K), suelo = D.oy;
      const carne = ['#1a0408', '#420a16', '#701424', '#a02434', '#c8404a', '#e4706a'], tallo = ['#0c120a', '#1a2a14', '#2c4420', '#42602c', '#5c7c3a'];
      let abre = 1, mece = 0, late = 0, barre = 0, cae = 0, muere = 0;
      if (A.proc === 'capullo') abre = 0;
      if (A.proc === 'abre') abre = u;
      if (A.proc === 'mece') mece = Math.sin(u * Math.PI * 2);
      if (A.proc === 'late') { late = Math.sin(u * Math.PI * 2); abre = 1.05 + late * 0.06; }
      if (A.proc === 'latigo') barre = u;
      if (A.proc === 'cae') { cae = u; abre = 1 - u * 0.25; }
      if (A.proc === 'duele') { cae = 0.3; }
      if (A.proc === 'muere') { muere = u; abre = 1 - u * 0.5; cae = u; }
      const c = [D.ox, suelo - 40 + cae * 10 + muere * 10];
      // la mata de raíces del suelo
      K.pieza(-5, () => {
        K.ovalo([D.ox, suelo - 6], 52, 12, tallo, { corta: (x, y) => y > suelo });
        for (let k = 0; k < 8; k++) { const a = Math.PI * (0.05 + k * 0.13), x0 = D.ox + Math.cos(a) * 40, y0 = suelo - 6; K.cap([x0, y0], [x0 + Math.cos(a) * 18, suelo - 1], 3, 1.2, tallo); }
      });
      // los látigos de espinas con bocas: salen de detrás y se mecen (al barrer, se abren en abanico)
      const latigos = [[-1, 0.7], [1, 0.65], [-0.45, 1], [0.5, 0.95]], negro = ['#08040c', '#180c22', '#2a1638', '#40224e', '#5a3266'];
      latigos.forEach(([lado, alto], k) => K.pieza(-3 + (k > 1 ? 0.5 : 0), () => {
        if (abre < 0.4) return;
        let x = c[0] + lado * 22, y = c[1] + 4, ang = -Math.PI / 2 + lado * 0.5;
        const N = 12, pts = [];
        for (let i = 0; i < N; i++) {
          const t = i / (N - 1);
          pts.push([x, y, 4.2 - t * 2.2]);
          ang += (lado * (0.08 + barre * 0.18) + Math.sin(u * Math.PI * 2 + k + t * 3) * 0.06 * (1 + mece)) * (1 - muere) + muere * lado * 0.25;
          const paso = 7 * alto * (0.6 + 0.4 * abre) * (1 - cae * 0.3);
          x += Math.cos(ang) * paso; y += Math.sin(ang) * paso;
        }
        pts.forEach(([px, py, r], i) => { K.ovalo([px, py], r * 1.3, r * 1.3, negro); if (i % 2 && i < N - 1) K.cap([px, py], [px + Math.cos(ang + 1.6) * (r + 4), py + Math.sin(ang + 1.6) * (r + 4)], 1.1, 0.3, R.hueso); });
        const [hx, hy] = pts[N - 1]; // la boca de la punta
        K.ovalo([hx, hy], 7, 6.4, carne);
        K.ovalo([hx + Math.cos(ang) * 2, hy + Math.sin(ang) * 2], 3, 2.6, ['#0c0206', '#1a040a', '#2a0810', '#3a0c18'], { mate: true });
        for (let d = 0; d < 6; d++) { const a = (d / 6) * Math.PI * 2; K.px(hx + Math.cos(ang) * 2 + Math.cos(a) * 3, hy + Math.sin(ang) * 2 + Math.sin(a) * 2.6, R.hueso[3], R.hueso); }
      }));
      // pétalos de carne: tres detrás (hacia arriba) y dos delante (bajos y anchos)
      const petalo = (ang, largo, ancho, z) => K.pieza(z, () => {
        const a = ang + mece * 0.03 * (z > 0 ? -1 : 1), L = largo * (0.35 + 0.65 * abre) * (1 - muere * 0.3);
        const p0 = [c[0] + Math.cos(a) * 6, c[1] + Math.sin(a) * 4], p1 = [c[0] + Math.cos(a) * (L + 6), c[1] + Math.sin(a) * (L * 0.85 + 4) + muere * 12];
        const m = [(p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2];
        K.ovalo(m, L / 2 + 2, ancho * (0.6 + 0.4 * abre), carne, { ang: Math.atan2(p1[1] - p0[1], p1[0] - p0[0]) });
        for (let k = -1; k <= 1; k++) K.vara(p0, [p1[0] - Math.sin(a) * k * ancho * 0.4, p1[1] + Math.cos(a) * k * ancho * 0.4], carne, { k: k ? 2 : 1 }); // venas
      });
      petalo(-Math.PI / 2, 52, 20, -2); petalo(-Math.PI / 2 - 0.85, 50, 19, -1.8); petalo(-Math.PI / 2 + 0.85, 50, 19, -1.8);
      petalo(-Math.PI / 2 - 1.75, 42, 17, -1.5); petalo(-Math.PI / 2 + 1.75, 42, 17, -1.5);
      // el centro: el cadáver del Velador sentado entre los estambres
      K.pieza(0, () => {
        if (abre < 0.25) return;
        const t0 = [c[0], c[1] - 20 * abre + cae * 8], E = 1.6;
        K.ovalo([c[0], c[1] + 2], 16, 8, tallo); // el cáliz
        for (let k = 0; k < 9; k++) { const a = Math.PI * (1.1 + k * 0.1); K.cap([c[0] + Math.cos(a) * 10, c[1] + 2], [c[0] + Math.cos(a) * 14, c[1] - 8 + Math.sin(k) * 2], 0.8, 0.8, ['#3a2a08', '#8a7020', '#d8b440', '#ffe070'], { luz: late > 0.2 }); }
        K.poli([[t0[0] - 7 * E, t0[1]], [t0[0] + 7 * E, t0[1]], [c[0] + 9 * E, c[1] + 4], [c[0] - 9 * E, c[1] + 4]], VELADOR, { corta: (x, y) => y > c[1] - 2 && ruido(x >> 1, 2, 6) < 0.35 }); // la túnica
        for (let k = 0; k < 2; k++) K.cap([t0[0] - 4 * E, t0[1] + (3.5 + k * 3) * E], [t0[0] + 1 * E, t0[1] + (3 + k * 3) * E], 0.8 * E, 0.8 * E, R.hueso); // costillas (por el roto de la túnica)
        K.cap([t0[0] - 6 * E, t0[1] + E], [t0[0] - 11 * E, t0[1] + (12 + cae * 4) * E], 1.6 * E, 1.3 * E, R.hueso); K.cap([t0[0] + 6 * E, t0[1] + E], [t0[0] + 12 * E, t0[1] + (11 + cae * 4) * E], 1.6 * E, 1.3 * E, R.hueso); // brazos caídos
        const cr = [t0[0] + cae * 4, t0[1] - 6 * E + cae * 5]; // el cráneo, caído hacia un lado
        K.ovalo(cr, 5 * E, 5 * E, R.hueso); K.ovalo([cr[0], cr[1] + 4 * E], 3 * E, 2 * E, R.hueso);
        for (const dx of [-2, 2]) { K.ovalo([cr[0] + dx * E, cr[1]], 1.3 * E, 1.6 * E, [OSCURO, OSCURO, OSCURO, OSCURO]); if (!muere) { K.px(cr[0] + dx * E, cr[1], OJO.podre[0], R.hueso, true); K.px(cr[0] + dx * E + 1, cr[1], OJO.podre[1], R.hueso, true); } }
        // raíces que lo atraviesan
        K.cap([c[0] - 12, c[1] - 4], [t0[0] - 2, t0[1] + 6], 1.4, 1, tallo); K.cap([c[0] + 13, c[1] - 6], [t0[0] + 3, t0[1] + 2], 1.4, 1, tallo);
        if (late > 0.3) for (let k = 0; k < 6; k++) { const a = (k / 6) * Math.PI * 2 + u * 3; K.px(c[0] + Math.cos(a) * 18, c[1] - 14 + Math.sin(a) * 8, '#d8ff70', tallo, true); } // esporas
      });
      petalo(Math.PI / 2 - 0.75, 26, 16, 2); petalo(Math.PI / 2 + 0.75, 26, 16, 2);
      if (abre < 0.1) K.pieza(3, () => { K.ovalo([c[0], c[1] - 18], 22, 30, carne); for (let k = -1; k <= 1; k++) K.vara([c[0] + k * 8, c[1] + 8], [c[0] + k * 4, c[1] - 44], carne, { k: 1 }); }); // el capullo cerrado
      K.puntos = { centro: c };
    },
  };

  // TAMAR, el Ojo del Cielo: ave colosal de hueso y plumas rotas; un solo ojo enorme en el pecho; pico de calavera.
  // Movimiento del dragón de Quaternius (alas, cola y patas); quieta pliega las alas.
  const PLUMA = ['#08080c', '#16161e', '#262632', '#3a3a4a', '#545466'];
  DEF.tamar = {
    jefe: true, W: 240, H: 200, ox: 118, oy: 190, px: 30, yaw: 0.55, fuente: ANIM_DRAGON,
    anims: {
      q: { clip: 'Dragon_Flying', n: 4, bucle: true, plegar: 0.8 }, v: { clip: 'Dragon_Flying', n: 8, bucle: true },
      p: { clip: 'Dragon_Attack', n: 6 }, y: { clip: 'Dragon_Attack2', n: 6, de: 0, a: 0.6 },
      z: { clip: 'Dragon_Hit', n: 4, de: 0.2, a: 0.5, plegar: 0.4 }, r: { clip: 'Dragon_Hit', n: 3, de: 0, a: 0.3 }, m: { clip: 'Dragon_Death', n: 8 },
    },
    cuadroJefe(j, t) {
      const e = j.estado;
      if (e === 'muriendo') return ['m', t / 1.8];
      if (e === 'despierta' || (j.vuelo || 0) > 0.1 || e === 'viento') return ['v', (J.t * (e === 'viento' ? 2.4 : 1.6)) % 1];
      if (e === 'plumas') return ['p', 1 - Math.max(0, j.t) / 0.8];
      if (e === 'rayos') return ['y', Math.min(1, t / 0.5)];
      if (e === 'aturdido') return ['z', 0.5 + 0.5 * Math.sin(J.t * 3)];
      if (j.golpeadoT && J.t - j.golpeadoT < 0.25) return ['r', (J.t - j.golpeadoT) / 0.25];
      return ['q', (J.t * 0.5) % 1];
    },
    huesos(K) {
      const P = K.P, pl = K.A.plegar || 0;
      for (const b of Object.keys(P)) if (b[0] !== '_') P[b] = [P[b][0], P[b][1] - 0.75, P[b][2]]; // los pies en el suelo
      if (pl) for (const l of ['L', 'R']) { // alas plegadas: las puntas vuelven hacia el cuerpo y caen
        const sh = P['Shoulder' + l];
        ['Wing1', 'Wing2', 'Wing3', 'Wing4', 'Wing4'].forEach((w, i) => {
          const b = i === 4 ? 'Wing4' + l + '_end' : w + l, q = P[b], t = (i + 1) / 5;
          const plg = [sh[0] + (q[0] - sh[0]) * 0.35, sh[1] + 0.3 - t * 1.4, sh[2] - 0.2 - t * 0.9];
          P[b] = CRI.mezcla(q, plg, pl);
        });
      }
    },
    dibujar(K) {
      const S = K.S, e = K.D.px / 13, A = K.A, muerto = K.anim === 'm' && K.u > 0.5;
      const hu = R.hueso;
      // la cola: abanico de plumas rotas
      K.pieza(S.Tail3[2] - 2, () => {
        const t = S.Tail4_end, b = S.Tail1;
        for (let k = -3; k <= 3; k++) { const L = (k % 2 ? 0.8 : 1) * 1; K.placaEntre(b, [t[0] + k * 4 * e, t[1] + Math.abs(k) * 2 * e - 2 * e * L], 3.4 * e, 1.6 * e, PLUMA, { bisel: e }); }
      });
      // las alas: huesos finos y plumas largas que cuelgan del borde (alguna rota)
      const ala = (l, z) => K.pieza(z, () => {
        const nombres = ['Shoulder', 'Wing1', 'Wing2', 'Wing3', 'Wing4'].map((w) => w + l).concat(['Wing4' + l + '_end']);
        const P3 = nombres.map((n) => K.P[n]), cad = nombres.map((n) => S[n]);
        for (let i = 1; i < cad.length; i++) {
          const a = cad[i], q = cad[i - 1];
          if (i >= 2) { // plumas: de cada tramo del ala hacia abajo y atrás (en 3D: giran con el ala)
            const d3 = CRI.res(P3[i], P3[i - 1]), m3 = Math.hypot(...d3) || 1, du = d3.map((v) => v / m3);
            let t3 = [0, -0.8, -0.6]; const pr = t3[0] * du[0] + t3[1] * du[1] + t3[2] * du[2];
            t3 = CRI.norm([t3[0] - du[0] * pr, t3[1] - du[1] * pr, t3[2] - du[2] * pr]);
            const n = i === cad.length - 1 ? 4 : 3;
            for (let k = 0; k < n; k++) {
              const t = k / n, rota = ruido(i, k, l === 'L' ? 1 : 2) < 0.2, L = (0.35 + i * 0.13) * (rota ? 0.55 : 1);
              const o3 = CRI.mezcla(P3[i - 1], P3[i], t), f3 = CRI.sum(o3, CRI.por(t3, L));
              K.placaEntre(K.pt(o3), K.pt(f3), 3 * e, rota ? 1.6 * e : 1 * e, PLUMA, { bisel: 0.6 * e });
            }
          }
          K.cap(q, a, 1 * e, 0.8 * e, hu);
        }
      });
      ala('L', -3);
      // patas de hueso con garras
      for (const l of ['L', 'R']) K.pieza(S['UpperLeg' + l][2] - 1, () => {
        const u = S['UpperLeg' + l], m = S['LowerLeg' + l], f = S['Feet' + l];
        K.cap(u, m, 1.6 * e, 1.1 * e, PLUMA); K.cap(m, f, 0.9 * e, 0.8 * e, hu);
        for (let k = -1; k <= 1; k++) K.cap(f, [f[0] + k * 2.4 * e + e, f[1] + 1.2 * e], 0.6 * e, 0.3 * e, hu);
      });
      // el cuerpo: plumas rotas, costillas y el ojo enorme del pecho
      const bo = S.Body, ne = S.Neck, he = S.Head, no = S.Nose;
      K.pieza(bo[2], () => {
        K.ovalo([bo[0], (bo[1] + ne[1]) / 2 + e], 8 * e, 11 * e, PLUMA, { ang: Math.atan2(ne[1] - bo[1], ne[0] - bo[0]) + Math.PI / 2, tex: (x, y) => (ruido(x >> 1, Math.floor(y / 3), 3) < 0.15 ? 1 : 0) });
        for (let k = 0; k < 4; k++) K.cap([bo[0] - 5 * e, bo[1] - (2 + k * 2.2) * e], [bo[0] + 5 * e, bo[1] - (1.6 + k * 2.2) * e], 0.6 * e, 0.6 * e, hu); // costillas
        const ojo = [bo[0] + 0.5 * e, bo[1] - 4.5 * e];
        K.ovalo(ojo, 4.6 * e, 4 * e, ['#2a2620', '#a8a090', '#e0d8c8', '#f8f4ea', '#ffffff'], { luz: !muerto });
        if (!muerto) {
          K.ovalo([ojo[0] + 0.6 * e, ojo[1]], 2.6 * e, 2.6 * e, ['#3a2400', '#a06000', '#f0a820', '#ffd860', '#fff4b0'], { luz: true });
          K.ovalo([ojo[0] + 0.9 * e, ojo[1]], 1.1 * e, 1.6 * e, [OSCURO, OSCURO, '#000000', '#0a0a10'], { mate: true });
          K.px(ojo[0] - 0.4 * e, ojo[1] - 1.2 * e, '#ffffff', hu, true);
        } else K.vara([ojo[0] - 4 * e, ojo[1]], [ojo[0] + 4 * e, ojo[1]], PLUMA, { grosor: 2 });
        K.puntos = { ojo };
      });
      // el cuello y la cabeza: calavera con pico
      K.pieza(ne[2] + 1, () => {
        K.cap([bo[0], bo[1] - 8 * e], ne, 3.4 * e, 2.6 * e, PLUMA);
        K.ovalo(he, 3.6 * e, 3.2 * e, hu);
        K.placa([[he[0] + 1 * e, he[1] - 1.6 * e], [no[0] + 2.6 * e, no[1] + 1.2 * e], [he[0] + 1 * e, he[1] + 1.8 * e]], hu, { bisel: 0.6 * e }); // el pico
        K.ovalo([he[0] + 0.6 * e, he[1] - 0.4 * e], 1.2 * e, 1.2 * e, [OSCURO, OSCURO, OSCURO, OSCURO]);
        if (!muerto) K.px(he[0] + 0.6 * e, he[1] - 0.4 * e, OJO.rojo[0], hu, true);
      });
      ala('R', 6);
    },
  };

  return { R, OJO, cuerpo, vara, mascara, gruto, baba };
})();
