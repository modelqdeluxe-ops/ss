'use strict';
// El personaje: un muñeco 3D articulado (escultor.js) que se hornea en las tres direcciones (abajo, arriba y a la
// derecha, vista de tres cuartos; la izquierda es la derecha volteada) y en tres animaciones (quieto, andar y
// golpe). Se vuelve a hornear cuando cambian el aspecto (piel, pelo, peinado, ropa) o la armadura puesta, así la
// armadura se ve de verdad encima del cuerpo, con su forma y su luz. Va a la zona dinámica del atlas.

const ASPECTO = {
  piel: ['#f2c2a0', '#e0a882', '#b8805a', '#8a5a3c', '#5e3a28'],
  pelo: ['#b0582c', '#2a2230', '#e8c060', '#d8402c', '#e4e4ec', '#4a6ad8', '#e870a8', '#3aa070', '#8a5ad0'],
  peinado: ['melena', 'corto', 'coleta', 'moño', 'erizo'],
  ropa: ['#2c8276', '#b03a3a', '#3a5ab0', '#7a3aa0', '#c09a2a', '#5a6070', '#3a8a3a', '#d0702a'],
};
const NOMBRE_PEINADO = { melena: 'Melena', corto: 'Corto', coleta: 'Coleta', moño: 'Moño', erizo: 'Erizo' };
const ASPECTO_INICIAL = { piel: 0, pelo: 0, peinado: 'melena', ropa: 0 };

const MATS_ARMADURA = (() => {
  const { mat, rampa } = ESCU;
  return {
    cobre: MAT.cobre, estano: MAT.estano, hierro: MAT.hierro, carmesi: MAT.carmesi,
    larva: mat(['#1e2a0c', '#34481a', '#506a26', '#749032', '#9ab842', '#c4dc6a', '#ecf6a8'], { brillo: 0.6 }),
    musgo: mat(['#16200e', '#243418', '#364a22', '#4c642c', '#688238', '#86a048', '#aac060'], { tex: ESCU.TEX.grumos(41, 1.6, 0.8) }),
    pluma: mat(rampa('#d84040', 6), {}), hoja: MAT.hoja,
  };
})();

const Personaje = (() => {
  const cacheMat = new Map();
  const tela = (c, o = {}) => { const k = 'tela' + c; if (!cacheMat.has(k)) cacheMat.set(k, ESCU.mat(ESCU.rampa(c, 6, { giro: 16, oscuro: 0.32 }), o)); return cacheMat.get(k); };
  const piel = (c) => { const k = 'piel' + c; if (!cacheMat.has(k)) cacheMat.set(k, ESCU.mat(ESCU.rampa(c, 6, { giro: 12, oscuro: 0.5 }), {})); return cacheMat.get(k); };
  const pelo = (c) => { const k = 'pelo' + c; if (!cacheMat.has(k)) cacheMat.set(k, ESCU.mat(ESCU.rampa(c, 6, { giro: 22, oscuro: 0.3 }), {})); return cacheMat.get(k); };

  function colores(asp) {
    return {
      piel: piel(ASPECTO.piel[asp.piel] || ASPECTO.piel[0]), pelo: pelo(ASPECTO.pelo[asp.pelo] || ASPECTO.pelo[0]), tunica: tela(ASPECTO.ropa[asp.ropa] || ASPECTO.ropa[0]),
      pant: tela('#3e2f4c'), bota: tela('#6a4029'), cinto: tela('#5a3624'), hebilla: MAT.oro, bufanda: tela('#c7363f'), mochila: MAT.cuero, mochilaT: tela('#9a6a44'),
    };
  }

  // ---------------------------------------------------------------- el muñeco
  // o: { dir, anim: 'q'|'a'|'g', f: cuadro, asp, eq: {casco, peto, botas} (material de cada pieza o null) }
  function muneco(m, o) {
    const C = colores(o.asp), eq = o.eq || {};
    const cx = 9, cz = 8, ang = o.dir === 'ab' ? 0 : o.dir === 'ar' ? Math.PI : Math.PI / 2;
    const anda = o.anim === 'a', fase = o.f / 6;
    const sw = anda ? Math.sin(fase * Math.PI * 2) : 0;
    const bob = anda ? Math.round(Math.abs(Math.cos(fase * Math.PI * 2))) : o.anim === 'q' ? o.f % 2 * 0.5 : 0;
    const golpe = o.anim === 'g' ? o.f / 2 : null;
    m.con({ rot: ['y', ang, [cx, 0, cz]] }, () => {
      // piernas: giran en la cadera
      for (const [lado, s] of [[-1, sw], [1, -sw]]) {
        m.con({ rot: ['x', -s * 0.6, [cx + lado * 2, 7, cz]] }, () => {
          m.capsula([cx + lado * 2, 7, cz], [cx + lado * 2, 2.6, cz], 1.7, 1.5, C.pant);
          m.elipsoide([cx + lado * 2, 1.6, cz + 0.8], [1.8, 1.6, 2.4], C.bota);
          if (eq.botas) {
            m.elipsoide([cx + lado * 2, 1.8, cz + 0.9], [2.2, 1.9, 2.8], eq.botas);
            m.capsula([cx + lado * 2, 2.5, cz + 0.6], [cx + lado * 2, 5.2, cz + 0.9], 1.9, 1.7, eq.botas);
          }
        });
      }
      m.con({ mover: [0, bob, 0], rot: ['x', golpe !== null ? 0.15 - golpe * 0.25 : 0, [cx, 7, cz]] }, () => {
        // cuerpo
        m.elipsoide([cx, 10, cz], [3.9, 3.6, 2.9], C.tunica);
        m.caja([cx - 3.6, 7, cz - 2.6], [cx + 3.6, 8.2, cz + 2.6], C.cinto, { r: 0.5 });
        m.caja([cx - 0.8, 7, cz + 2.4], [cx + 0.8, 8.3, cz + 3], C.hebilla);
        if (eq.peto) {
          const { SDF, OP } = ESCU;
          m.figura(OP.corta(SDF.elipsoide(cx, 10.4, cz + 0.2, 4.4, 3.5, 3.4), OP.sobre(7.6)), [cx - 5, 7, cz - 4, cx + 5, 14, cz + 4], eq.peto);
          for (const l of [-1, 1]) m.esfera([cx + l * 4.1, 12.4, cz], 2.1, eq.peto);
          m.caja([cx - 3.8, 7, cz - 2.9], [cx + 3.8, 8.2, cz + 3.1], C.cinto, { r: 0.5 });
          m.caja([cx - 0.8, 7, cz + 2.9], [cx + 0.8, 8.3, cz + 3.5], C.hebilla);
        }
        // mochila
        m.caja([cx - 3, 7.5, cz - 4.6], [cx + 3, 13, cz - 2], C.mochila, { r: 1 });
        m.caja([cx - 3.2, 11.6, cz - 4.8], [cx + 3.2, 13.2, cz - 2.4], C.mochilaT, { r: 0.6 });
        // brazos (el derecho lleva la herramienta: en el golpe sube y baja)
        for (const [lado, s] of [[-1, -sw], [1, sw]]) {
          const g = golpe !== null && lado === 1 ? 2.2 - golpe * 3.2 : golpe !== null ? -0.3 : s * 0.65;
          m.con({ rot: ['x', -g, [cx + lado * 4, 12.2, cz]] }, () => {
            m.capsula([cx + lado * 4, 12.2, cz], [cx + lado * 4.6, 8.4, cz + 0.4], 1.35, 1.2, C.tunica);
            m.esfera([cx + lado * 4.7, 7.8, cz + 0.5], 1.4, C.piel);
            if (lado === 1) m.marca('mano', cx + lado * 4.7, 7.8, cz + 0.5);
          });
        }
        // bufanda
        m.toro([cx, 13.1, cz], 2.9, 1.25, C.bufanda);
        cabeza(m, o, C, eq, cx, cz);
      });
    });
  }
  function cabeza(m, o, C, eq, cx, cz) {
    const hy = 17.6, pein = o.asp.peinado || 'melena', perfil = o.dir === 'de';
    m.esfera([cx, hy, cz + 0.4], 5.6, C.piel);
    const rPelo = pein === 'corto' || pein === 'erizo' ? 5.95 : 6.2;
    m.esfera([cx, hy + 0.9, cz - 0.4], rPelo, C.pelo);
    // se quita el pelo de delante de la cara (de perfil, también del lado que mira a la cámara: la mejilla)
    const frente = hy + (pein === 'corto' ? 2.4 : 1.8);
    m.quitar(() => m.caja([cx - 4.6, hy - 7, cz + 1.2], [cx + 4.6, frente, cz + 8], null));
    if (perfil) m.quitar(() => m.caja([cx - 8, hy - 7, cz - 0.6], [cx - 1, frente - 0.4, cz + 8], null));
    m.esfera([cx, hy, cz + 0.4], 5.6, C.piel, { modo: 'debajo' });
    // flequillo
    const fl = pein === 'corto' ? [-2.4, 0, 2.4] : [-3.4, -1.1, 1.2, 3.4];
    for (const dx of fl) m.capsula([cx + dx, hy + 3.6, cz + 3.4], [cx + dx * 1.08, hy + (pein === 'corto' ? 1.8 : 0.9), cz + 5.2], 1.5, 0.8, C.pelo);
    if (perfil) m.capsula([cx - 5.2, hy + 3.2, cz - 0.8], [cx - 5.6, hy - 1, cz - 2], 1.7, 1, C.pelo); // patilla de detrás de la oreja
    if (pein === 'melena' || pein === 'coleta' || pein === 'moño') for (const l of perfil ? [1] : [-1, 1]) m.capsula([cx + l * 5.3, hy + 2, cz + 1.5], [cx + l * 5.6, hy - (pein === 'melena' ? 3.4 : 1.5), cz + 2.2], 1.7, 1, C.pelo);
    if (pein === 'melena') m.elipsoide([cx, hy - 1.4, cz - 4.2], [5, 3.2, 1.8], C.pelo);
    if (pein === 'coleta') m.tubo([[cx, hy + 1, cz - 5.4], [cx, hy - 2.5, cz - 7], [cx + 0.6, hy - 6, cz - 6.8]], 2.2, 1.2, C.pelo);
    if (pein === 'moño') m.esfera([cx, hy + 6.4, cz - 2], 2.8, C.pelo);
    if (pein === 'erizo') for (const [dx, dz] of [[-3, -1], [0, -2], [3, -1], [-1.5, 1.5], [1.5, 1.5]]) m.capsula([cx + dx * 0.8, hy + 4, cz + dz], [cx + dx * 1.3, hy + 8.2, cz + dz * 1.5 - 1], 1.6, 0.5, C.pelo);
    if (eq.casco) casco(m, eq.casco, eq.cascoTipo, cx, cz, hy);
    if (perfil) m.marca('ojoD', cx - 3.2, hy - 0.4, cz + 4.6);
    else { m.marca('ojoI', cx - 2.2, hy - 0.4, cz + 5.7); m.marca('ojoD', cx + 2.2, hy - 0.4, cz + 5.7); }
  }
  function casco(m, mt, tipo, cx, cz, hy) {
    const { SDF, OP } = ESCU;
    const cara = SDF.caja(cx - 4.4, hy - 9, cz + 1.6, cx + 4.4, hy + 2.2, cz + 9);
    m.figura(OP.resta(OP.corta(SDF.esfera(cx, hy + 1.3, cz - 0.2, 6.6), OP.sobre(hy + 0.4)), cara), [cx - 7, hy, cz - 7, cx + 7, hy + 8, cz + 7], mt);
    m.figura(OP.resta(SDF.toro(cx, hy + 0.9, cz - 0.2, 6.3, 0.75), cara), [cx - 8, hy - 1, cz - 8, cx + 8, hy + 3, cz + 8], mt);
    if (tipo === 'estano' || tipo === 'hierro') m.caja([cx - 0.6, hy - 1.8, cz + 5.4], [cx + 0.6, hy + 2.8, cz + 6.6], mt); // nasal
    if (tipo === 'hierro') { for (const l of [-1, 1]) m.caja([cx + l * 5.6 - 1, hy - 3.6, cz - 1], [cx + l * 5.6 + 1, hy + 1, cz + 3.5], mt, { r: 0.5 }); m.caja([cx - 0.7, hy + 6, cz - 5], [cx + 0.7, hy + 8.4, cz + 4], mt, { r: 0.6 }); }
    if (tipo === 'larva') for (const l of [-1, 1]) m.tubo([[cx + l * 2.5, hy + 6, cz + 1], [cx + l * 4, hy + 9.5, cz + 2.5], [cx + l * 5.5, hy + 10.5, cz + 4]], 0.9, 0.5, mt);
    if (tipo === 'carmesi') { for (const l of [-1, 1]) m.tubo([[cx + l * 5, hy + 3, cz], [cx + l * 7.2, hy + 5.5, cz + 0.5], [cx + l * 7.6, hy + 8.4, cz + 1]], 1.3, 0.5, mt); m.tubo([[cx, hy + 7.4, cz - 1], [cx, hy + 9, cz - 4], [cx, hy + 7, cz - 7.5]], 1.6, 1, MATS_ARMADURA.pluma); }
    if (tipo === 'musgo') { m.elipsoide([cx + 1, hy + 7.6, cz - 0.5], [2.6, 0.9, 2], MATS_ARMADURA.hoja); m.capsula([cx, hy + 6.6, cz - 0.5], [cx + 0.5, hy + 7.6, cz - 0.4], 0.5, MATS_ARMADURA.hoja); }
    if (tipo === 'cobre') m.esfera([cx, hy + 7.6, cz - 0.4], 1.1, mt);
  }

  // ---------------------------------------------------------------- hornear
  const ANIMS = [['q', 2], ['a', 6], ['g', 3]];
  function hornearCuadro(dir, anim, f, asp, eq) {
    const m = new ESCU.Modelo(18, 30, 18);
    muneco(m, { dir, anim, f, asp, eq });
    const r = m.hornear({ luz: [-0.5, 0.72, 0.5], amb: 0.32, cima: 0.45, sombra: false });
    if (dir !== 'ar' && !(eq && eq.casco && false)) {
      for (const k of dir === 'ab' ? ['ojoI', 'ojoD'] : ['ojoD']) {
        const [x, y] = r.marcas[k];
        const mt = r.mapa ? r.mapa(x - 0, y) : null;
        if (!mt || mt.r.length !== 6 || !/^#/.test(mt.r[0]) || !esPiel(mt)) continue;
        r.color.px(x, y - 1, '#1a1222'); r.color.px(x, y, '#1a1222');
      }
    }
    return r;
  }
  const esPiel = (mt) => [...cacheMat.entries()].some(([k, v]) => v === mt && k.startsWith('piel'));
  function equipoMats(equipo) {
    const eq = {};
    for (const r of ['casco', 'peto', 'botas']) {
      const s = equipo && equipo[r];
      if (!s) continue;
      const tipo = s.id.split('_')[1];
      eq[r] = MATS_ARMADURA[tipo] || MAT.hierro;
      if (r === 'casco') eq.cascoTipo = tipo;
    }
    return eq;
  }
  function clave(asp, eq) { return JSON.stringify([asp, Object.fromEntries(Object.entries(eq).map(([k, v]) => [k, v && v.r ? v.r[3] : v]))]); }
  // Trabajo pendiente: se hornea poco a poco (unos ms por cuadro) y se cambia todo de golpe al terminar.
  let trabajo = null, actual = null;
  function lista(asp, eq) {
    const out = [];
    for (const dir of ['ab', 'ar', 'de']) for (const [anim, n] of ANIMS) for (let f = 0; f < n; f++) out.push([dir, anim, f]);
    return out;
  }
  function terminar(t) {
    const L = [];
    for (const [nombre, l, dx] of t.hechos) {
      L.push([nombre, l]); L.push([nombre.replace('pj_', 'pjB_'), l.blanco()]);
      META_SPR[nombre] = META_SPR[nombre.replace('pj_', 'pjB_')] = { dx, dz: 0 };
      if (nombre.startsWith('pj_de_')) {
        const v = l.voltear(); v.n = voltearNormales(l);
        const ni = nombre.replace('_de_', '_iz_');
        L.push([ni, v]); L.push([ni.replace('pj_', 'pjB_'), l.blanco().voltear()]);
        META_SPR[ni] = META_SPR[ni.replace('pj_', 'pjB_')] = { dx: -dx, dz: 0 };
      }
    }
    escribirZona(L);
    actual = t.clave;
  }
  function empezarTrabajo(asp, equipo) {
    const eq = equipoMats(equipo), k = clave(asp, eq);
    if (k === actual || (trabajo && trabajo.clave === k)) return;
    trabajo = { clave: k, asp, eq, pend: lista(asp, eq), hechos: [] };
  }
  function trabajar(ms = 4) {
    if (!trabajo) return;
    const t0 = performance.now();
    while (trabajo.pend.length && performance.now() - t0 < ms) {
      const [dir, anim, f] = trabajo.pend.shift();
      const r = hornearCuadro(dir, anim, f, trabajo.asp, trabajo.eq);
      trabajo.hechos.push([`pj_${dir}_${anim}${f}`, r.color, r.dx]);
    }
    if (!trabajo.pend.length) { const t = trabajo; trabajo = null; terminar(t); }
  }
  return {
    inicial() { empezarTrabajo(ASPECTO_INICIAL, null); trabajar(1e9); },
    // pide el personaje con este aspecto y este equipo (si ya está, no hace nada)
    pedir(asp, equipo, yaMismo) { empezarTrabajo(asp || ASPECTO_INICIAL, equipo); if (yaMismo) trabajar(1e9); },
    trabajar,
    get ocupado() { return !!trabajo; },
    // para el taller y la pantalla de aspecto
    hornearCuadro, equipoMats,
  };
})();
