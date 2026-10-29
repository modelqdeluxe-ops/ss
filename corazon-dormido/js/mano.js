'use strict';
// Pixel art dibujado a mano, con las medidas de Core Keeper (ARTE.md): cada figura es una rejilla de letras y cada
// letra es un papel (pelo oscuro, piel, túnica clara…) que se pinta con la paleta que toque. Así el mismo dibujo
// sirve para todos los colores de pelo, piel y ropa, y para las armaduras (cambian los colores de la túnica, el
// pelo o las botas por los del metal).
//
// Reglas (ARTE.md): sin contorno negro (el borde es el tono más oscuro de cada material), 3–4 tonos por material,
// luz de arriba a la izquierda, ojos de 2×2 con brillo, cabeza grande (13×11 en un cuerpo de 13×20).

const MANO = (() => {
  // ---------------------------------------------------------------- rejillas
  // Lee una rejilla: filas separadas por saltos de línea, '.' = vacío.
  const rejilla = (s) => s.split('\n').map((f) => f.trim()).filter((f) => f.length);
  // Pinta una rejilla en un lienzo con una paleta (letra → color). dx/dy: dónde; voltear: espejo horizontal.
  function pintar(L, g, pal, dx = 0, dy = 0, voltear = false) {
    const filas = typeof g === 'string' ? rejilla(g) : g, w = Math.max(...filas.map((f) => f.length));
    for (let y = 0; y < filas.length; y++) for (let x = 0; x < filas[y].length; x++) {
      const k = filas[y][x];
      if (k === '.' || k === ' ') continue;
      const c = pal[k];
      if (c === undefined) throw new Error(`MANO: la letra «${k}» no tiene color`);
      if (c) L.px(dx + (voltear ? w - 1 - x : x), dy + y, c);
    }
    return L;
  }
  // Normales de cojín para la luz de lado del juego: cada píxel mira hacia fuera según lo lejos que esté del centro
  // de su fila (así una antorcha a la izquierda aclara el lado izquierdo del dibujo).
  function normales(L) {
    const n = new Int32Array(L.w * L.h);
    for (let y = 0; y < L.h; y++) {
      let x0 = -1, x1 = -1;
      for (let x = 0; x < L.w; x++) if (L.d[y * L.w + x]) { if (x0 < 0) x0 = x; x1 = x; }
      if (x0 < 0) continue;
      const c = (x0 + x1) / 2, m = Math.max(1, (x1 - x0) / 2);
      for (let x = x0; x <= x1; x++) {
        if (!L.d[y * L.w + x]) continue;
        const nx = Math.max(-1, Math.min(1, ((x - c) / m) * 0.75)), ny = 0.25, nz = Math.sqrt(Math.max(0, 1 - nx * nx - ny * ny));
        n[y * L.w + x] = (1 << 24) | (Math.round((nx * 0.5 + 0.5) * 255) << 16) | (Math.round((ny * 0.5 + 0.5) * 255) << 8) | Math.round((nz * 0.5 + 0.5) * 255);
      }
    }
    L.n = n;
    return L;
  }
  // Rampa de n tonos (oscuro → claro) desde un color medio, con el giro de tono de escultor.js.
  const rampa = (c, n, o) => ESCU.rampa(c, n, o);

  return { rejilla, pintar, normales, rampa };
})();

// ---------------------------------------------------------------- el Despierto
// Papeles: pelo o H h g G (de oscuro a brillo) · piel S s b (sombra, piel, rubor) · ojos E e w (párpado, iris, brillo)
// túnica O T t u · bufanda R r · cinto B y (hebilla) · pantalón P p · botas K k · mochila M m.
// El cuadro mide 15×22: 1 px de margen a los lados y 2 arriba (rebote, casco, moño). Cabeza en las filas 2–12,
// cuerpo 13–18 y piernas 19–21 (los pies tocan el borde de abajo).
const PJ_MANO = (() => {
  const W = 15, H = 22;
  // ---- cabezas (13×11), por dirección y peinado. Lo que no es pelo es igual en todos.
  const CABEZA = {
    ab: {
      melena: `
....ooo.oo...
...ohhgohho..
..ohgGghhhHo.
.ohgGghhhhHHo
.ohgghhhHhHHo
ohhhhhHhHHhHo
oHhsHhhHssHHo
oHhsHsssHssHo
oHseEssseEsHo
oHswessswesHo
oHHbsssssbSHo`,
      corto: `
.....ooooo...
...oohhgghoo.
..ohgGGghhHo.
.ohgGghhhhHHo
.ohgghhhHhHHo
.ohhhhhHhHHho
.oHsHhhHssHo.
.oHsssHsssHo.
.oSseEssseEs.
.oSswessswes.
..Sbsssssbs..`,
      erizo: `
.o..o.o..o.o.
.ho.hohohoho.
.hgohgGohhHo.
.ohgGghhhhHHo
.ohgghhhHhHHo
.ohhhhhHhHHho
.oHsHhhHssHo.
.oHsssHsssHo.
.oSseEssseEs.
.oSswessswes.
..Sbsssssbs..`,
    },
    ar: {
      melena: `
....ooo.oo...
...ohhgohho..
..ohgGghhhHo.
.ohgGghhhhHHo
.ohgghhhhhHHo
ohghhhhhhhHHo
ohhhhhhhhHHHo
oHhhhhHhhhHHo
oHhhhHHhhHHHo
oHHhHHoHHhHHo
.oHHoHHoHHHo.`,
      corto: `
.....ooooo...
...oohhgghoo.
..ohgGGghhHo.
.ohgGghhhhHHo
.ohgghhhhhHHo
.ohhhhhhhHHHo
.oHhhhHhhhHHo
.oHhhHHhhHHo.
.oHHHHHHHHHo.
..SHHHHHHHS..
..SsssssssS..`,
      erizo: `
.o..o.o..o.o.
.ho.hohohoho.
.hgohgGohhHo.
.ohgGghhhhHHo
.ohgghhhhhHHo
.ohhhhhhhHHHo
.oHhhhHhhhHHo
.oHhhHHhhHHo.
.oHHHHHHHHHo.
..SHHHHHHHS..
..SsssssssS..`,
    },
    de: {
      melena: `
...ooooo.....
..ohhgggoo...
.ohhgGGgghho.
ohhhgGgghhhho
ohhhhhhhhhHHo
oHhhhhhhHhHho
oHhhHhsHssHh.
oHhHhssssssH.
oHHhSsssseEs.
oHHHSsssswess
.oHHSssssbsS.`,
      corto: `
...ooooo.....
..ohhgggoo...
.ohhgGGgghho.
ohhhgGgghhhho
ohhhhhhhhhHHo
.oHhhhhhHhHho
.oHhHhsHssHh.
.oHHhssssssH.
.oHHSsssseEs.
..oSSsssswess
...SSssssbsS.`,
      erizo: `
..o.o.o.o....
..hohohoho...
.ohhgGGgghho.
ohhhgGgghhhho
ohhhhhhhhhHHo
.oHhhhhhHhHho
.oHhHhsHssHh.
.oHHhssssssH.
.oHHSsssseEs.
..oSSsssswess
...SSssssbsS.`,
    },
  };
  // la coleta y el moño son el peinado corto con algo encima
  const EXTRA = {
    coleta: { ab: null, ar: { g: `
.oHo.
.oho.
.oHo.
..o..`, x: 4, y: 9 }, de: { g: `
oo..
ohho
oHhh
.oHh
..oH
...o`, x: -2, y: 4 } },
    moño: { ab: { g: `
.ooo.
ohgho
ohhHo`, x: 4, y: -2 }, ar: { g: `
.ooo.
ohgho
ohhHo`, x: 4, y: -2 }, de: { g: `
.ooo.
ohgho
ohhHo`, x: 2, y: -2 } },
  };
  // ---- cuerpos (13×9, filas 11–19 del dibujo), sin brazos
  const CUERPO = {
    ab: `
...RrrrrrRR..
...Tuttttrt..
...Tutttttt..
...Tutttttt..
...BBByBBB...
...pPPpPPp...`,
    ar: `
...RRrrrRR...
...MmmmmmM...
...MmmmmmM...
...TMMMMMt...
...BBBBBBB...
...pPPpPPp...`,
    de: `
....RrrrR....
....Tutt.....
....Tutt.....
....Tutt.....
....BByB.....
....pPPp.....`,
  };
  // brazos (en el hombro): manga y mano
  const BRAZO = `
Tt
Tt
sS`;
  const BRAZO_DE = `
tu
tT
sS`;
  // piernas (3 filas) por cuadro de andar: 0 quieto, 1 paso con la izquierda arriba, 2 la derecha arriba
  const PIERNAS = {
    ab: [`
...ppP.pPP...
...KkK.KkK...
...KKK.KKK...`, `
...ppP.pPP...
...KkK.KKK...
.......KKK...`, `
...ppP.pPP...
...KKK.KkK...
...KKK.......`],
    ar: [`
...pPP.PPp...
...KKK.KKK...
...KKK.KKK...`, `
...pPP.PPp...
...KKK.KKK...
.......KKK...`, `
...pPP.PPp...
...KKK.KKK...
...KKK.......`],
    de: [`
....pPPp.....
....kKKk.....
....KKKKk....`, `
...pP..pP....
..kK....KKk..
..KK.....KK..`, `
....pPpP.....
....KkKk.....
...KKKKKK....`],
  };

  function paleta(asp, eq) {
    const pelo = MANO.rampa(ASPECTO.pelo[asp.pelo] || ASPECTO.pelo[0], 5, { giro: 18, oscuro: 0.2 });
    const piel = MANO.rampa(ASPECTO.piel[asp.piel] || ASPECTO.piel[0], 4, { giro: 10, oscuro: 0.6 });
    const ropa = MANO.rampa(ASPECTO.ropa[asp.ropa] || ASPECTO.ropa[0], 4, { giro: 16, oscuro: 0.32 });
    const P = {
      o: pelo[0], H: pelo[1], h: pelo[2], g: pelo[3], G: pelo[4],
      S: piel[1], s: piel[2], b: ESCU.mezclar(piel[2], '#e85a64', 0.38),
      E: '#1a1830', e: '#3a9a8c', w: '#f4f2ff',
      O: ropa[0], T: ropa[1], t: ropa[2], u: ropa[3],
      R: '#6a1422', r: '#cc3440', B: '#3e2416', y: '#f0c040',
      P: '#221a34', p: '#3e325c', K: '#24140c', k: '#6e4228', M: '#5a3420', m: '#8e5a34',
    };
    // armaduras: el peto pinta la túnica (y la bufanda) con el metal; las botas, las botas y el pantalón
    if (eq && eq.peto) { const r = rampaMetal(eq.peto); Object.assign(P, { O: r[0], T: r[1], t: r[2], u: r[3], R: r[1], r: r[2] }); }
    if (eq && eq.botas) { const r = rampaMetal(eq.botas); Object.assign(P, { K: r[0], k: r[2], P: r[1], p: r[2] }); }
    return P;
  }
  // los 4 tonos de un material del escultor (de su rampa, que va de oscuro a claro)
  function rampaMetal(mt) { const r = mt.r, n = r.length; return [r[0], r[Math.round(n * 0.3)], r[Math.round(n * 0.55)], r[Math.round(n * 0.8)]]; }

  // casco: una gorra de metal sobre el pelo (rejilla por dirección) con los colores del material
  const CASCO = {
    ab: `
...ooooooo...
..oGgggggho..
.oGggggghhHo.
.ogggghhhhHo.
ohhhhhhhHHHHo
oHHHHHHHHHHHo`,
    ar: `
...ooooooo...
..oGgggggho..
.oGggggghhHo.
.ogggghhhhHo.
ohhhhhhhHHHHo
oHHHHHHHHHHHo`,
    de: `
...oooooo....
..oGgggggho..
.oGgggggghHo.
oggggghhhhHo.
ohhhhhhhHHHHo
oHHHHHHHHHo..`,
  };

  // Un cuadro: dir ab|ar|de, anim q|a|g, f cuadro. Devuelve { color: Lienzo, dx }.
  function cuadro(dir, anim, f, asp, eq) {
    const P = paleta(asp, eq), L = new Lienzo(W, H);
    const pein = asp.peinado || 'melena', base = CABEZA[dir][pein] ? pein : pein === 'melena' ? 'melena' : 'corto';
    // andar: 6 cuadros (quieto, izquierda arriba, izquierda, quieto, derecha arriba, derecha); el cuerpo sube
    // cuando las piernas se cruzan
    const paso = anim === 'a' ? [0, 1, 1, 0, 2, 2][f] : 0;
    const bob = anim === 'a' ? (f === 1 || f === 4 ? -1 : 0) : anim === 'q' ? (f === 1 ? 1 : 0) : 0;
    // golpe: 0 se carga hacia atrás, 1 descarga hacia delante, 2 vuelve
    const lean = anim === 'g' ? [-1, 1, 0][f] : 0;
    const ox = 1 + (dir === 'de' ? lean : 0), oyCab = 2 + bob + (anim === 'g' && dir !== 'de' ? [-1, 1, 0][f] : 0);
    // piernas
    MANO.pintar(L, PIERNAS[dir][paso], P, ox, 19);
    // cuerpo y brazos (el de detrás antes del cuerpo)
    const yCu = 13 + bob;
    const swing = anim === 'a' ? [0, -1, -1, 0, 1, 1][f] : 0;
    if (dir === 'de') {
      // de perfil: el brazo de delante balancea; en el golpe sube y baja
      MANO.pintar(L, CUERPO.de, P, ox, yCu);
      const by = anim === 'g' ? [-3, 1, 0][f] : swing, bx = anim === 'g' ? [-1, 2, 1][f] : swing;
      MANO.pintar(L, BRAZO_DE, P, ox + 5 + bx, yCu + 1 + by);
    } else {
      const atras = dir === 'ar';
      MANO.pintar(L, CUERPO[dir], P, ox, yCu);
      const gIzq = anim === 'g' ? 0 : swing, gDer = anim === 'g' ? [-3, 0, -1][f] : -swing;
      MANO.pintar(L, BRAZO, P, ox + 1, yCu + 1 + (atras ? gDer : gIzq), false);
      MANO.pintar(L, BRAZO, P, ox + 10, yCu + 1 + (atras ? gIzq : gDer), true);
    }
    // cabeza (encima de todo: es lo que más se ve)
    MANO.pintar(L, CABEZA[dir][base], P, ox, oyCab);
    const ex = EXTRA[pein] && EXTRA[pein][dir];
    if (ex) MANO.pintar(L, ex.g, P, ox + ex.x, oyCab + ex.y);
    if (eq && eq.casco) {
      const r = rampaMetal(eq.casco), Pc = Object.assign({}, P, { o: r[0], H: r[1], h: r[2], g: r[3], G: ESCU.mezclar(r[3], '#ffffff', 0.4) });
      MANO.pintar(L, CASCO[dir], Pc, ox, oyCab - 1);
    }
    // golpe: los ojos se cierran en la descarga (una raya)
    if (anim === 'g' && f === 1 && dir !== 'ar') {
      for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) if (L.get(x, y) === P.e || L.get(x, y) === P.w) L.px(x, y, P.s);
    }
    MANO.normales(L);
    return { color: L, dx: 0 };
  }
  return { cuadro, W, H };
})();
