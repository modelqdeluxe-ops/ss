'use strict';
// Iconos de objetos a mano con la construcción de los de Core Keeper (16×16 como mucho, contorno de color, luz arriba a
// la izquierda, pocos tonos): lingotes, minerales, cascos, petos, botas y bloques, cada familia con el mismo dibujo
// y la paleta de su material. Letras: p o n m el material (brillo → sombra), 1–4 la piedra del mineral, G g el
// adorno; el contorno lo pone la lámina.

const ICONOS_PX = (() => {
  const LINGOTE = [
    '................', '................', '................', '................', '................', '....pppppppppo..',
    '...poooooooooon.', '..poooooooooonn.', '.nnnnnnnnnnnnmm.', '.nnnnnnnnnnnnm..', '.mmmmmmmmmmmm...', '................'];
  const CASCO = [
    '................', '......pppo......', '....ppoooonn....', '...pooooooonn...', '..pooooooooonm..', '..oooooooonnnm..',
    '..GGGGGGGGGGgg..', '..onm..nn..onm..', '..onm..nm..onm..', '..nm...nm...nm..', '...m........m...'];
  const PETO = [
    '................', '................', '..ppo......onn..', '.ppooo....onnnm.', '.poooooooonnnnm.', '.poooooGgnnnnnm.',
    '..oooooGgnnnnm..', '..oooooonnnnnm..', '...ooooonnnnm...', '...GGGGGGGGgg...', '...ooooonnnnm...', '....oooonnnm....'];
  const BOTAS = [
    '................', '................', '................', '................', '................', '..poo....poo....',
    '..pon....pon....', '..pon....pon....', '..pon....pon....', '..GGg....GGg....', '..ponn...ponn...', '..pooonn.pooonn.',
    '..mmmmmm.mmmmmm.'];
  const BLOQUE = [
    '................', '................', '..pppppppppppo..', '..poooooooooon..', '..poooooooooon..', '..oooooooooonn..',
    '..nnnnnnnnnnnm..', '..nnnnnnnnnnnm..', '..nnnnnnnnnnnm..', '..nnnnnnnnnnnm..', '..nnnnnnnnnnnm..', '..nnnnnnnnnnnm..',
    '..mmmmmmmmmmmm..'];
  function lamina(filas, pal) {
    const r = CRI.lamina(16, Math.max(16, filas.length), (K) => { K.pieza(0, () => K.rej(filas, pal, 0, 0)); });
    return OBJ_PX.recorte(r.color);
  }
  function pal4(t, adorno) {
    const p = {};
    ['m', 'n', 'o', 'p'].forEach((c, k) => { p[c] = [t[k + 1], t]; });
    if (adorno) ['g', 'G'].forEach((c, k) => { p[c] = [adorno[k + 2], adorno]; });
    return p;
  }
  // el mineral: un canto de piedra con pepitas del metal (como las rocas del mundo, en pequeño)
  function mineral(metal) {
    const g = Array.from({ length: 13 }, () => Array(14).fill('.'));
    const cx = 7, cy = 7, rx = 6.4, ry = 5.2;
    for (let y = 0; y < 13; y++) for (let x = 0; x < 14; x++) {
      const u = (x + 0.5 - cx) / rx, v = (y + 0.5 - cy) / ry, d = u * u + v * v;
      if (d > 1) continue;
      const l = -u * 0.55 - v * 0.8;
      g[y][x] = d > 0.7 && l < -0.1 ? '1' : l > 0.45 ? '4' : l > 0.05 ? '3' : '2';
    }
    for (const [x, y, n] of [[3, 4, 3], [8, 3, 2], [9, 7, 3], [4, 8, 2], [7, 10, 2]]) { for (let k = 0; k < n; k++) { g[y][x + k] = 'n'; g[y + 1][x + k] = k ? 'm' : 'o'; } g[y][x] = 'p'; }
    return g.map((f) => f.join(''));
  }
  function construir() {
    if (typeof OBJ_PX === 'undefined') return;
    const M = OBJ_PX.MET;
    const ORO = ['#3a2a08', '#a8701c', '#e8b040', '#ffe68a', '#fffbe0'];
    const MAT = {
      cobre: M.cobre, estano: M.estano, hierro: M.hierro, oro: ORO, carmesi: M.carmesi, medula: M.medula, plata: M.plata,
      larva: ['#3a2a14', '#8a6a3a', '#c8a86a', '#ecd8a0', '#fff4d8'], musgo: ['#0e1a0a', '#2a4a1a', '#4a7a2a', '#7aaa44', '#b4d878'],
    };
    const AD = { cobre: ORO, estano: M.hierro, hierro: ORO, oro: M.carmesi, carmesi: ORO, medula: M.carmesi, plata: ['#0a1a3a', '#1a3a6a', '#4a86cc', '#7ab2f0', '#e8f6ff'], larva: M.carmesi, musgo: ORO };
    const poner = (n, L) => { quitarDibujo('i_' + n); def('i_' + n, L); };
    for (const m of ['cobre', 'estano', 'hierro', 'oro', 'carmesi', 'medula', 'plata']) {
      poner('lingote_' + m, lamina(LINGOTE, pal4(MAT[m])));
      const pm = pal4(MAT[m]); [1, 2, 3, 4].forEach((k) => { pm[k] = [M.piedra[k], M.piedra]; });
      poner('mineral_' + m, lamina(mineral(MAT[m]), pm));
    }
    for (const m of ['cobre', 'estano', 'hierro', 'larva', 'carmesi', 'musgo', 'medula', 'plata']) {
      poner('casco_' + m, lamina(CASCO, pal4(MAT[m], AD[m])));
      poner('peto_' + m, lamina(PETO, pal4(MAT[m], AD[m])));
      poner('botas_' + m, lamina(BOTAS, pal4(MAT[m], AD[m])));
    }
    // los bloques: un cubo con la tapa clara y la cara de delante, con los colores de su pared y algo de su textura
    if (typeof INFO_MURO !== 'undefined' && typeof RAMPA !== 'undefined') {
      const hechos = new Set();
      for (const im of Object.values(INFO_MURO)) {
        if (!im.bloque || !im.bloque.startsWith('bloque_') || hechos.has(im.bloque) || !RAMPA[im.arte]) continue;
        hechos.add(im.bloque);
        const R = RAMPA[im.arte], t = [R[0], R[1], R[2], R[3], R[Math.min(R.length - 1, 4)]];
        const filas = BLOQUE.map((f, y) => [...f].map((c, x) => (c === 'n' && (x * 7 + y * 3) % 9 === 0 ? 'm' : c === 'o' && (x * 5 + y) % 7 === 0 ? 'p' : c)).join(''));
        poner(im.bloque, lamina(filas, pal4(t)));
      }
    }
  }
  CONSTRUIR_MODELOS.push(construir);
  return { construir };
})();
