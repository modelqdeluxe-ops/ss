'use strict';
// Fase 2: abrir la Gran Muralla, los altares de los titanes, el altar del cielo, las tablillas, la incubadora y
// las mascotas, los mercaderes que se mudan a una casa con cama, las bombas, la piedra de regreso y las criaturas
// fijas (tentáculos y plantas trampa que esperan en su sitio y vuelven a salir al rato).

const INTERACCIONES = {}; // tipo de «interactua» → qué hace al tocar USAR
const PANELES = {};       // panel → cómo se dibuja

// ---------------------------------------------------------------- la Gran Muralla

function murallaCerca() {
  const p = J.jug, tx = Math.floor(p.x / T), ty = Math.floor(p.y / T);
  for (let y = ty - 2; y <= ty + 2; y++) for (let x = tx - 2; x <= tx + 2; x++) if (muroEn(x, y) === MURO.MURALLA) return true;
  return false;
}
function abrirMuralla() {
  if (J.murallaAbierta) return;
  J.murallaAbierta = true;
  for (let i = 0; i < M.muro.length; i++) {
    if (M.muro[i] !== MURO.MURALLA) continue;
    M.muro[i] = 0; M.piso[i] = SUELO.GRAVA;
    const x = i % N_MUNDO, y = (i - x) / N_MUNDO;
    GL.tocar(x, y); tocarTrozo(x, y); if (M.visto[i]) marcarMapa(i);
  }
  cambioLuz();
  const p = J.jug;
  J.temblor = 2.5; Sonido.sfx.rugido(); setTimeout(() => Sonido.sfx.temblor(), 500); setTimeout(() => Sonido.sfx.victoria(), 1400);
  for (let k = 0; k < 6; k++) particulas(p.x + (Math.random() - 0.5) * 120, p.y + (Math.random() - 0.5) * 80, 14, RAMPA.muralla.slice(2), { vel: 90, s: 2, vida: 0.9 });
  aviso('¡La Gran Muralla se derrumba!', null, '#9fe0a8');
  setTimeout(() => aviso(`La Selva Esmeralda queda al ${rumboA(CENTRO + Math.cos(M.angSelva) * 200, CENTRO + Math.sin(M.angSelva) * 200)}`, null, '#9fe0a8'), 1800);
}
// Lo que se puede hacer con USAR cerca aunque no haya un objeto: hablar con un mercader o tocar la Muralla.
function usoCercano() {
  const m = mercaderCerca();
  if (m) return { txt: 'HABLAR', fn: () => abrirTienda(m) };
  if (J.corazonDespierto && !J.murallaAbierta && murallaCerca()) return { txt: 'TOCAR', fn: () => (J.hito.hablo2 ? abrirMuralla() : aviso('Habla antes con el Corazón')) };
  return null;
}

// Qué dice el Corazón cuando ya está despierto.
function palabrasCorazon2() {
  const g = M.guaridas, out = [];
  const rumboSelva = rumboA(CENTRO + Math.cos(M.angSelva) * 200, CENTRO + Math.sin(M.angSelva) * 200);
  if (!J.murallaAbierta) { out.push(`Estoy despierto. Pon la mano en la Gran Muralla y caerá. La Selva Esmeralda está al ${rumboSelva}; el camino de grava lleva hasta ella.`); return out; }
  if (!J.hito.selva) out.push(`La Selva Esmeralda está al ${rumboSelva}. Allí la luz baja del cielo.`);
  if (!J.hito.tiene_mineral_carmesi && !J.hito.mesa_carmesi) out.push('En la hierba vieja de la Selva hay metal carmesí. Tu pico de hierro lo saca.');
  if (!J.altares.hiedra) out.push(J.jefesMuertos.hiedra ? 'Pon la Gema de la Hiedra en su altar, a mi izquierda.' : `Hiedra, la Flor de Sangre, pudre un jardín al ${rumboA(g.hiedra.x, g.hiedra.y)}. Rodéala de espino y entra por sus huecos.`);
  if (!J.altares.tamar) out.push(J.jefesMuertos.tamar ? 'Pon la Gema del Cielo en su altar, a mi derecha.' : `Tamar duerme sobre el templo del cielo, al ${rumboA(g.tamar.x, g.tamar.y)}. Baja si le ofreces plumas antiguas, carmesí y una gema.`);
  if (!J.hito.cofreMoho) out.push(`La Mazmorra del Moho guarda un cofre, al ${rumboA(M.mazmorra.x, M.mazmorra.y)}. Sus muros no se rompen: entra por la puerta.`);
  if (!J.hito.mercader) out.push('Construye una casa cerrada con una cama y pon un ídolo dentro: vendrá alguien a vivir contigo.');
  if (!out.length) out.push('Los titanes descansan. El mar y el desierto despertarán en la próxima fase. Tu mundo sigue aquí.');
  return out;
}

// ---------------------------------------------------------------- altares, tablillas e incubadora

INTERACCIONES.altar_titan = (o) => {
  const t = o.datos && o.datos.titan, gema = JEFES[t].cristal;
  if (o.extra) { aviso(`El altar de ${t === 'tamar' ? 'Tamar' : 'Hiedra'} brilla`); return; }
  if (!J.cuenta(gema)) { aviso(`Necesita la ${ITEMS[gema].nombre}`); return; }
  quitar(gema, 1); o.extra = 1; J.altares[t] = true; cambioLuz(); marcarMapa(idx(o.x, o.y));
  J.temblor = 1; Sonido.sfx.latido(); setTimeout(() => Sonido.sfx.victoria(), 600);
  particulas(o.x * T + 8, o.y * T, 34, t === 'tamar' ? ['#ffffff', '#c8e0ff', '#ffe890'] : ['#b0ffd0', '#5ae0a0', '#ffffff'], { vel: 90, vida: 1.1, brilla: true });
  if (J.altares.tamar && J.altares.hiedra) J.panel = 'fin2';
  else aviso('El Corazón late más fuerte (1/2)', null, '#ffb3c8');
};
INTERACCIONES.altar_cielo = () => {
  const j = J.jefes.tamar;
  if (!j || J.jefesMuertos.tamar) { aviso('El cielo está en calma'); return; }
  if (j.invocar) { aviso('Tamar ya viene'); return; }
  if (!J.cuenta('ofrenda_cielo')) { aviso('El altar pide una Ofrenda del cielo (mesa carmesí)'); return; }
  quitar('ofrenda_cielo', 1); j.invocar = true; J.hito.tamarInvocado = true; Sonido.sfx.latido();
  aviso('¡Algo enorme baja del cielo!', null, '#ffd166');
};
INTERACCIONES.tablilla = (o) => { const n = (o.datos && o.datos.n) || 0; J.tablilla = TABLILLAS[n % TABLILLAS.length]; J.hito['tablilla_' + n] = true; J.panel = 'tablilla'; Sonido.sfx.menu(); };
PANELES.tablilla = (ctx) => panelMensaje(ctx, 'TABLILLA', J.tablilla || '', 'CERRAR', () => { J.panel = null; }, '#e8d8a0');
PANELES.fin2 = (ctx) => panelMensaje(ctx, 'LOS TITANES DESCANSAN', 'Las gemas de Tamar y de Hiedra laten junto al Corazón. Pero la Selva no acaba aquí: detrás, el suelo se hunde en la Ciénaga de la Podre y, más allá, espera la Ciudad Velada, la ciudad de los que levantaron la Muralla.', 'SEGUIR', () => { J.panel = null; guardarPartida(); }, '#9fe0a8');

const TIEMPO_INCUBAR = 150;
INTERACCIONES.incubadora = (o) => { if (!o.datos) o.datos = { huevo: null, t0: 0 }; J.incubadora = o; J.panel = 'incubadora'; Sonido.sfx.menu(); };
function huevoListo(o) { return o.datos && o.datos.huevo && J.tiempoMundo - o.datos.t0 >= TIEMPO_INCUBAR; }
PANELES.incubadora = (ctx) => {
  const o = J.incubadora;
  if (!o || !M.objetos.has(idx(o.x, o.y))) { J.panel = null; return; }
  fondoPanel(ctx);
  const w = N_BARRA * 19 - 1 + 12, h = 112, x = Math.round(LW / 2 - w / 2), y = Math.max(4, Math.round(LH / 2 - h / 2));
  marco(ctx, x, y, w, h); boton(x, y, w, h, () => {}, { mudo: true });
  texto(ctx, 'INCUBADORA', x + 6, y + 6, COL.oro, { sombra: COL.negro });
  cruz(ctx, x + w - 17, y + 3);
  const d = o.datos;
  ranura(ctx, x + 8, y + 20, d.huevo ? { id: d.huevo, n: 1 } : null, false);
  if (!d.huevo) {
    texto(ctx, 'Toca un huevo de abajo para ponerlo', x + 32, y + 26, COL.suave);
    const huevos = J.inv.map((s, i) => (s && ITEMS[s.id] && ITEMS[s.id].mascota ? i : -1)).filter((i) => i >= 0);
    huevos.forEach((i, k) => {
      const cx = x + 8 + k * 19, cy = y + 48;
      ranura(ctx, cx, cy, J.inv[i], false);
      boton(cx, cy, 18, 18, () => { d.huevo = J.inv[i].id; d.t0 = J.tiempoMundo; quitar(d.huevo, 1); Sonido.sfx.colocar(); }, { mudo: true });
    });
    if (!huevos.length) partirTexto('No llevas huevos. Salen de cofres, cajas y del Encapuchado; a veces los suelta algún bicho.', w - 16).forEach((l, k) => texto(ctx, l, x + 8, y + 50 + k * 7, COL.suave));
  } else if (huevoListo(o)) {
    const mas = ITEMS[d.huevo].mascota;
    texto(ctx, `¡Ha nacido ${ITEMS[mas].nombre}!`, x + 32, y + 22, COL.verde);
    botonTexto(ctx, x + 32, y + 32, 70, 13, 'SACAR', () => {
      if (agregar(mas, 1)) { aviso('La mochila está llena'); return; }
      avisoRecogido(mas, 1); d.huevo = null; Sonido.sfx.victoria(); J.mascotas[mas] = J.mascotas[mas] || { n: 1, xp: 0 };
    });
    partirTexto(ITEMS[mas].desc, w - 16).slice(0, 3).forEach((l, k) => texto(ctx, l, x + 8, y + 56 + k * 7, COL.suave));
  } else {
    const k = (J.tiempoMundo - d.t0) / TIEMPO_INCUBAR;
    texto(ctx, `${ITEMS[d.huevo].nombre}: falta ${Math.ceil((1 - k) * TIEMPO_INCUBAR)} s`, x + 32, y + 22, COL.texto);
    barra(ctx, x + 32, y + 32, w - 44, 4, k, '#a0e0ff', '#14243a');
    texto(ctx, 'Mantenla cerca de tu casa y sigue a lo tuyo.', x + 8, y + 50, COL.suave);
  }
};

// ---------------------------------------------------------------- mascotas
// Te siguen; las que pelean van a por la criatura más cercana. Suben de nivel con lo que derrotas cerca.

const MASCOTAS = {
  mascota_topito: { dano: 16, vel: 86, cd: 0.8, aturde: 0.35, arte: 'topito' },
  mascota_babosita: { dano: 12, vel: 74, cd: 0.9, frena: true, arte: 'babosita' },
  mascota_luci: { dano: 0, vel: 96, cd: 0, vuela: true, arte: 'luci' },
  mascota_mohito: { dano: 14, vel: 70, cd: 1.3, distancia: true, veneno: 5, arte: 'mohito' },
};
function nivelMascota(id) { return (J.mascotas[id] && J.mascotas[id].n) || 1; }
function ganarXPMascota(xp) {
  const id = J.equipo.mascota && J.equipo.mascota.id;
  if (!id) return;
  const m = J.mascotas[id] || (J.mascotas[id] = { n: 1, xp: 0 });
  ganarXP('doma', xp * 0.3);
  if (m.n >= 10) return;
  m.xp += xp;
  while (m.n < 10 && m.xp >= 40 * m.n) { m.xp -= 40 * m.n; m.n++; aviso(`${ITEMS[id].nombre} sube al nivel ${m.n}`, ITEMS[id].icono, '#9fe0ff'); Sonido.sfx.nivel(); }
}
function actualizarMascota(dt) {
  const p = J.jug, eq = J.equipo.mascota;
  if (!eq) { J.mascotaEnt = null; return; }
  J.hito.mascota = true;
  let m = J.mascotaEnt;
  if (!m || m.id !== eq.id) m = J.mascotaEnt = { id: eq.id, x: p.x - 14, y: p.y + 4, hw: 3, hh: 2, t: 0, cd: 0, mira: 1, anda: false, salto: 0 };
  const d = MASCOTAS[m.id], dist = Math.hypot(p.x - m.x, p.y - m.y);
  m.t += dt; m.cd -= dt; m.salto = Math.max(0, m.salto - dt);
  if (dist > 220) { m.x = p.x - 10; m.y = p.y + 4; efecto('humo', 4, m.x, m.y, 0, 0.3); }
  let objetivo = null;
  if (d.dano) { let md = 90; for (const e of todosEnemigos()) { if (e.vida <= 0 || e.enterrado || e.invisible) continue; const de = Math.hypot(e.x - p.x, e.y - p.y); if (de < md) { md = de; objetivo = e; } } }
  let gx = p.x - p.fx * 16 + (p.fy ? 10 : 0), gy = p.y - p.fy * 12 + 4, cerca = 6;
  if (objetivo) { gx = objetivo.x; gy = objetivo.y; cerca = d.distancia ? 60 : objetivo.radio + 6; }
  const dx = gx - m.x, dy = gy - m.y, dd = Math.hypot(dx, dy);
  m.anda = dd > cerca;
  if (m.anda) { const v = d.vel * (dist > 70 && !objetivo ? 1.6 : 1); const mx = (dx / dd) * v * dt, my = (dy / dd) * v * dt; if (d.vuela) { m.x += mx; m.y += my; } else mover(m, mx, my); m.mira = dx >= 0 ? 1 : -1; }
  if (objetivo && m.cd <= 0 && dd <= cerca + 2) {
    m.cd = d.cd;
    const dano = d.dano * (1 + (nivelMascota(m.id) - 1) * 0.12 + nivel('doma') * 0.02);
    if (d.distancia) {
      const a = Math.atan2(objetivo.y - 4 - m.y, objetivo.x - m.x);
      J.disparos.push({ x: m.x, y: m.y - 4, vx: Math.cos(a) * 140, vy: Math.sin(a) * 140, dano, vida: 1, espora: true, veneno: d.veneno });
    } else {
      m.salto = 0.25;
      herir(objetivo, dano, dx / (dd || 1), dy / (dd || 1), {});
      if (d.aturde && Math.random() < d.aturde && !objetivo.jefe) objetivo.aturdido = 0.6;
      if (d.frena && !objetivo.jefe) objetivo.aturdido = Math.max(objetivo.aturdido || 0, 0.25);
    }
  }
  if (m.id === 'mascota_luci' && Math.random() < dt * 3) J.particulas.push({ x: m.x + (Math.random() - 0.5) * 6, y: m.y, z: 16, vx: 0, vy: 0, vz: -4, g: 0, vida: 0.8, c: '#fff6a0', s: 1, brilla: true, chispa: true });
}

// ---------------------------------------------------------------- mercaderes

// La casa de un ídolo: casillas que se alcanzan desde él sin pasar paredes ni puertas, como mucho 64.
function casaDe(x0, y0) {
  const vistas = new Set([idx(x0, y0)]), cola = [[x0, y0]];
  let cama = false;
  while (cola.length) {
    const [x, y] = cola.pop();
    for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
      const nx = x + dx, ny = y + dy, i = idx(nx, ny);
      if (!enMundo(nx, ny) || vistas.has(i)) continue;
      if (M.muro[i]) continue;
      const o = M.objetos.get(i);
      if (o && (o.tipo === 'puerta' || o.tipo === 'valla')) continue;
      vistas.add(i);
      if (vistas.size > 65) return null;
      cola.push([nx, ny]);
    }
  }
  const libres = [];
  for (const i of vistas) { const o = M.objetos.get(i); if (o && o.tipo === 'cama') cama = true; if (!o && !esAgua(M.piso[i])) libres.push(i); }
  if (!cama || !libres.length) return null;
  return { casillas: [...vistas], libres };
}
let relojCasas = 0;
function revisarMercaderes(dt) {
  relojCasas -= dt;
  if (relojCasas > 0) return;
  relojCasas = 2.5;
  // ¿se ha ido alguno? (sin ídolo o sin casa)
  M.mercaderes = M.mercaderes.filter((m) => {
    const o = objetoEn(m.ix, m.iy), casa = o && OBJ[o.tipo].mercader === m.tipo ? casaDe(m.ix, m.iy) : null;
    if (!casa) { aviso(`${MERCADERES[m.tipo].nombre} se ha ido: su casa ya no vale`, null, '#ffb070'); return false; }
    m.casa = casa;
    return true;
  });
  // ¿llega alguno? (un ídolo a la vista, en una casa con cama)
  for (const o of J.vistaObj || []) {
    const tipo = OBJ[o.tipo].mercader;
    if (!tipo || M.mercaderes.some((m) => m.tipo === tipo)) continue;
    const casa = casaDe(o.x, o.y);
    if (!casa) continue;
    const i = casa.libres[Math.floor(Math.random() * casa.libres.length)], x = i % N_MUNDO, y = (i - x) / N_MUNDO;
    const m = { tipo, x, y, ix: o.x, iy: o.y, stock: MERCADERES[tipo].venta.map(([id, precio, n]) => [id, precio, n]), reponer: J.tiempoMundo + 1500 + Math.random() * 600, casa };
    M.mercaderes.push(m);
    J.hito.mercader = true;
    efecto('humo', 4, x * T + 8, y * T + 10, 0, 0.5); Sonido.sfx.victoria();
    aviso(`¡${MERCADERES[tipo].nombre} se muda a tu casa!`, null, '#9fffa8');
  }
}
function actualizarMercaderes(dt) {
  revisarMercaderes(dt);
  for (const m of M.mercaderes) {
    if (J.tiempoMundo > m.reponer) { m.stock = MERCADERES[m.tipo].venta.map(([id, precio, n]) => [id, precio, n]); m.reponer = J.tiempoMundo + 1500 + Math.random() * 600; }
    if (!m.ent) m.ent = { x: m.x * T + 8, y: m.y * T + 10, hw: 4, hh: 3, t: Math.random() * 3, gx: null, gy: null, mira: 1, anda: false };
    const e = m.ent;
    e.t -= dt;
    if (e.t <= 0 && m.casa) { e.t = 2 + Math.random() * 4; const i = m.casa.libres[Math.floor(Math.random() * m.casa.libres.length)], x = i % N_MUNDO; e.gx = x * T + 8; e.gy = ((i - x) / N_MUNDO) * T + 10; }
    e.anda = false;
    if (e.gx !== null) {
      const dx = e.gx - e.x, dy = e.gy - e.y, d = Math.hypot(dx, dy);
      if (d > 2) { mover(e, (dx / d) * 22 * dt, (dy / d) * 22 * dt); e.mira = dx >= 0 ? 1 : -1; e.anda = true; } else e.gx = null;
    }
  }
}
function mercaderCerca() {
  const p = J.jug;
  for (const m of M.mercaderes) if (m.ent && Math.hypot(m.ent.x - p.x, m.ent.y - p.y) < 26) return m;
  return null;
}
function abrirTienda(m) { J.tienda = m; J.panel = 'tienda'; UI.pestana = 'comprar'; UI.selInv = -1; UI.scroll = 0; aviso(MERCADERES[m.tipo].saludo, null, MERCADERES[m.tipo].colores[0]); }
INTERACCIONES.idolo = (o) => {
  const tipo = OBJ[o.tipo].mercader, m = M.mercaderes.find((x) => x.tipo === tipo);
  if (m) { aviso(`${MERCADERES[tipo].nombre} vive aquí`); return; }
  const casa = casaDe(o.x, o.y);
  aviso(casa ? 'Casa lista: el mercader llegará enseguida' : 'Hace falta una casa cerrada (paredes y puertas), de 64 casillas o menos, con una cama dentro');
};
PANELES.tienda = (ctx) => {
  const m = J.tienda;
  if (!m || !M.mercaderes.includes(m)) { J.panel = null; return; }
  const info = MERCADERES[m.tipo];
  fondoPanel(ctx);
  const w = Math.min(LW - 12, 240), h = LH - 12, x = Math.round(LW / 2 - w / 2), y = 6;
  marco(ctx, x, y, w, h); boton(x, y, w, h, () => {}, { mudo: true });
  texto(ctx, `${info.nombre.toUpperCase()}, ${info.titulo}`, x + 6, y + 6, info.colores[0], { sombra: COL.negro });
  cruz(ctx, x + w - 17, y + 3);
  sprCentro(ctx, 'i_moneda', x + 10, y + 19);
  texto(ctx, String(J.cuenta('moneda')), x + 18, y + 17, COL.oro);
  pestanas(ctx, x + 60, y + 14, [['comprar', 'COMPRAR'], ['vender', 'VENDER']]);
  const ly = y + 30, lh = h - 36;
  if (UI.pestana === 'vender') {
    rejilla(ctx, J.inv, x + Math.round((w - (N_BARRA * 19 - 1)) / 2), ly, N_BARRA, UI.selInv, (i) => { UI.selInv = J.inv[i] ? i : -1; Sonido.sfx.menu(); });
    const s = UI.selInv >= 0 ? J.inv[UI.selInv] : null, iy = ly + 4 * 19 + 6;
    if (s) {
      const v = valorVenta(s.id);
      infoObjeto(ctx, s, x + 8, iy, w - 16, 2);
      if (v > 0) {
        botonTexto(ctx, x + 8, iy + 26, 80, 12, `VENDER 1: ${v}`, () => venderMercader(UI.selInv, 1));
        if (s.n > 1) botonTexto(ctx, x + 92, iy + 26, 100, 12, `TODO: ${v * s.n}`, () => venderMercader(UI.selInv, s.n));
      } else texto(ctx, 'Eso no lo compra', x + 8, iy + 28, COL.suave);
    } else texto(ctx, 'Toca algo de tu mochila para venderlo', x + 8, iy, COL.suave);
    return;
  }
  const fila = 21;
  ctx.save(); ctx.beginPath(); ctx.rect(x + 2, ly, w - 4, lh); ctx.clip();
  UI.scroll = Math.min(UI.scroll, Math.max(0, m.stock.length * fila - lh));
  m.stock.forEach(([id, precio, quedan], k) => {
    const fy = ly + k * fila - Math.round(UI.scroll);
    if (fy + fila < ly || fy > ly + lh) return;
    const puede = quedan > 0 && J.cuenta('moneda') >= precio, it = ITEMS[id];
    ctx.fillStyle = puede ? '#3e2a1e' : '#24170f'; ctx.fillRect(x + 3, fy, w - 6, fila - 2);
    ranura(ctx, x + 7, fy + 1, { id, n: 1 }, false);
    texto(ctx, it.nombre, x + 29, fy + 3, puede ? RAREZA[it.rareza || 0].color : COL.suave);
    sprCentro(ctx, 'i_moneda', x + 33, fy + 14);
    texto(ctx, `${precio}`, x + 40, fy + 12, J.cuenta('moneda') >= precio ? COL.oro : '#ff8080');
    texto(ctx, quedan > 0 ? `quedan ${quedan}` : 'agotado', x + w - 7, fy + 3, quedan > 0 ? COL.suave : '#ff8080', { alin: 'd' });
    boton(x + 3, Math.max(fy, ly), w - 6, Math.min(fila - 2, ly + lh - fy), () => comprarMercader(m, k), { lista: true });
  });
  ctx.restore();
};
function comprarMercader(m, k) {
  const fila = m.stock[k], [id, precio, quedan] = fila;
  if (quedan <= 0) { aviso('Agotado: vuelve más tarde'); return; }
  if (J.cuenta('moneda') < precio) { aviso('No te llegan las monedas'); Sonido.sfx.error(); return; }
  if (agregar(id, 1)) { aviso('La mochila está llena'); return; }
  quitar('moneda', precio); fila[2]--; avisoRecogido(id, 1); Sonido.sfx.crear();
}
function venderMercader(i, n) {
  const s = J.inv[i];
  if (!s) return;
  const v = valorVenta(s.id), k = Math.min(n, s.n);
  if (!v) return;
  s.n -= k; if (!s.n) { J.inv[i] = null; UI.selInv = -1; }
  const sobra = agregar('moneda', v * k);
  if (sobra) soltar(J.jug.x, J.jug.y, 'moneda', sobra);
  Sonido.sfx.recoger(3); avisoRecogido('moneda', v * k);
}

// ---------------------------------------------------------------- bombas y explosiones

function lanzarBomba(it, s) {
  const p = J.jug, obj = enemigoCerca(p, 90, true);
  if (obj) mirarA(p, obj.x, obj.y);
  quitar(s.id, 1);
  p.golpeT = 0.5; p.swing = 0.3; p.swingDur = 0.3; p.swingItem = it.icono;
  J.bombas.push({ x: p.x + p.fx * 8, y: p.y + p.fy * 6, z: 10, vx: p.fx * 110, vy: p.fy * 80, vz: 70, t: 1.5, dano: it.dano });
  Sonido.sfx.zas();
}
// Explosión (o la de un hongo explosivo, con «retraso» segundos de aviso).
function estallido(x, y, retraso, dano, o = {}) { J.bombas.push({ x, y, z: 0, vx: 0, vy: 0, vz: 0, t: retraso, dano, quieta: true, quien: o.quien }); }
function actualizarBombas(dt) {
  for (const b of J.bombas) {
    b.t -= dt;
    if (!b.quieta) {
      b.vz -= 260 * dt; b.z += b.vz * dt;
      if (b.z <= 0) { b.z = 0; b.vz = -b.vz * 0.35; b.vx *= 0.6; b.vy *= 0.6; }
      const nx = b.x + b.vx * dt, ny = b.y + b.vy * dt;
      if (solido(Math.floor(nx / T), Math.floor(ny / T), true)) { b.vx = -b.vx * 0.4; b.vy = -b.vy * 0.4; } else { b.x = nx; b.y = ny; }
      if (Math.random() < 0.5) J.particulas.push({ x: b.x, y: b.y, z: b.z + 8, vx: (Math.random() - 0.5) * 10, vy: 0, vz: 20, g: 0, vida: 0.3, c: Math.random() < 0.5 ? '#ffd040' : '#ff7020', s: 1, brilla: true, chispa: true });
    }
    if (b.t <= 0) explotar(b);
  }
  J.bombas = J.bombas.filter((b) => b.t > 0);
}
function explotar(b) {
  const p = J.jug, R = 30, fuerza = 1 + nivel('explosivos') * 0.02;
  for (const e of todosEnemigos()) {
    if (e.vida <= 0 || e.enterrado || e.invisible) continue;
    const d = Math.hypot(e.x - b.x, e.y - b.y);
    if (d < R + e.radio) herir(e, b.dano * fuerza, (e.x - b.x) / (d || 1), (e.y - b.y) / (d || 1), { maza: true, habilidad: 'explosivos' });
  }
  if (Math.hypot(p.x - b.x, p.y - b.y) < 24) danarJugador(Math.round(b.dano * 0.35), b);
  const tx = Math.floor(b.x / T), ty = Math.floor(b.y / T);
  for (let y = ty - 2; y <= ty + 2; y++) for (let x = tx - 2; x <= tx + 2; x++) {
    if (Math.hypot(x + 0.5 - b.x / T, y + 0.5 - b.y / T) > 1.9 || !enMundo(x, y)) continue;
    const i = idx(x, y), m = M.muro[i];
    if (m && INFO_MURO[m].poder <= 60) {
      const mena = M.mena[i], info = INFO_MURO[m];
      romperMuro(x, y);
      if (mena) soltar(x * T + 8, y * T + 10, INFO_MENA[mena].item, 1 + (Math.random() < 0.5 ? 1 : 0));
      else if (info.bloque && Math.random() < 0.4) soltar(x * T + 8, y * T + 10, info.bloque, 1);
    }
    const o = M.objetos.get(i), io = o && OBJ[o.tipo];
    if (o && io && (io.rompible || (io.planta && o.tipo !== 'cultivo')) && !io.interactua) {
      quitarObjeto(x, y);
      if (io.suelta) soltarLista(x * T + 8, y * T + 10, io.suelta);
      if (io.explota && b.quien !== 'cadena') estallido(x * T + 8, y * T + 12, 0.25, 40, { quien: 'cadena' });
    }
  }
  efecto('explosion', 4, b.x, b.y, 4, 0.45);
  particulas(b.x, b.y, 26, ['#ffd040', '#ff7020', '#ffffff', '#5a4a3a'], { vel: 110, s: 2, vida: 0.7, brilla: true });
  efecto('humo', 4, b.x, b.y + 2, 0, 0.6);
  J.temblor = Math.max(J.temblor, 0.6); J.destelloT = 0.15; J.destello = { x: b.x, y: b.y };
  Sonido.sfx.temblor(); Sonido.sfx.romper();
  ganarXP('explosivos', 4);
}

// ---------------------------------------------------------------- piedra de regreso

function usarRegreso(s) {
  if (J.regreso) return;
  J.regreso = { t: 1.3, id: s.id };
  J.jug.golpeT = 1.4;
  Sonido.sfx.latido();
}
function actualizarRegreso(dt) {
  const r = J.regreso, p = J.jug;
  if (!r) return;
  r.t -= dt;
  if (Math.random() < 0.6) particulas(p.x + (Math.random() - 0.5) * 14, p.y, 2, ['#a0e0ff', '#ffffff', '#ffb3c8'], { vel: 20, alto: 50, brilla: true });
  if (r.t > 0) return;
  J.regreso = null;
  if (quitar(r.id, 1)) return;
  const p0 = puntoInicio();
  p.x = p0.x; p.y = p0.y; J.cam.x = p.x; J.cam.y = p.y; p.kbx = p.kby = 0;
  J.enemigos = J.enemigos.filter((e) => Math.hypot(e.x - p.x, e.y - p.y) < 300);
  if (J.mascotaEnt) { J.mascotaEnt.x = p.x - 10; J.mascotaEnt.y = p.y + 4; }
  efecto('humo', 4, p.x, p.y, 0, 0.5); particulas(p.x, p.y - 6, 20, ['#a0e0ff', '#ffffff'], { vel: 70, brilla: true });
  aviso(J.cama ? 'De vuelta en tu cama' : 'De vuelta junto al Corazón');
}

// ---------------------------------------------------------------- criaturas fijas

let relojFijas = 0;
function aparecerFijas(dt) {
  relojFijas -= dt;
  if (relojFijas > 0 || !M.fijas.length) return;
  relojFijas = 1;
  const p = J.jug, ptx = p.x / T, pty = p.y / T;
  M.fijas.forEach((f, k) => {
    if (Math.abs(f.x - ptx) > 22 || Math.abs(f.y - pty) > 22) return;
    if (f.muerto && J.tiempoMundo - f.muerto < 600) return;
    if (J.enemigos.some((e) => e.fija === k) || solido(f.x, f.y)) return;
    const e = nuevaCriatura(f.tipo, f.x * T + 8, f.y * T + 10);
    e.fija = k; if (f.reza) { e.reza = true; e.rezaX = (f.ix ?? f.x) * T + 8; }
    J.enemigos.push(e);
  });
}

// ---------------------------------------------------------------- cada cuadro

function actualizarFase2(dt) {
  actualizarBombas(dt); actualizarRegreso(dt); actualizarMascota(dt); actualizarMercaderes(dt);
  if (J.destelloT > 0) J.destelloT -= dt;
  const p = J.jug, reg = M.region[idx(Math.floor(p.x / T), Math.floor(p.y / T))];
  if (reg !== J.regionAntes) {
    J.regionAntes = reg;
    const clave = 'region_' + reg;
    if (!J.hito[clave] && reg !== REGION.SANTUARIO && !J.presentacion) { J.hito[clave] = true; J.cartel = { txt: NOMBRE_REGION[reg], t: 3.5 }; }
    if (reg >= REGION.SELVA && reg !== REGION.SELLADO) J.hito.selva = true;
    J.hito['region_' + reg] = true;
  }
  if (J.cartel) { J.cartel.t -= dt; if (J.cartel.t <= 0) J.cartel = null; }
  if (J.presentacion) { J.presentacion.t -= dt; if (J.presentacion.t <= 0) J.presentacion = null; }
}

// ---------------------------------------------------------------- dibujo (bombas, mascota, mercaderes)

function dibujarFase2_3d() {
  for (const b of J.bombas) {
    const x = b.x / T, z = b.y / T + 0.2;
    if (b.quieta) { dibujarAviso(x, z, 13, 1 - Math.max(0, b.t) / 1.6); continue; }
    GL.plano('sombra_s', x, z, { escala: 0.5 });
    GL.sprite('bomba', x, z, { y: (b.z / 16) * GL.KY + 0.04, blanco: b.t < 0.5 && Math.floor(J.t * 16) % 2 ? 0.7 : 0 });
  }
  const m = J.mascotaEnt;
  if (m && J.estado === 'juego') {
    const d = MASCOTAS[m.id], x = m.x / T, z = m.y / T + 0.02, vuela = d.vuela ? 0.5 + Math.sin(J.t * 4) * 0.08 : m.salto > 0 ? Math.sin((m.salto / 0.25) * Math.PI) * 0.3 : 0;
    GL.plano('sombra_s', x, z - 0.02, { escala: 0.7, tinte: [1, 1, 1, 0.6] });
    const f = m.salto > 0 ? 2 : m.anda ? Math.floor(m.t * 8) % 2 : 0;
    const nm = `${d.arte}_${d.vuela ? Math.floor(J.t * 10) % 2 : f}`, dxm = META_SPR[nm] ? META_SPR[nm].dx * (m.mira < 0 ? -1 : 1) : 0;
    GL.sprite(nm, x + dxm, z, { y: vuela, flip: m.mira < 0, lx: x, lz: z });
    if (SPR[nm + '_luz'] && m.id !== 'mascota_luci') GL.sprite(nm + '_luz', x + dxm, z + 0.002, { y: vuela, flip: m.mira < 0, unlit: 1, lote: GL.lotes.brillo });
    if (m.id === 'mascota_luci') { GL.sprite(`luci_${Math.floor(J.t * 10) % 2}_luz`, x + dxm, z + 0.002, { y: vuela, flip: m.mira < 0, unlit: 1, lote: GL.lotes.brillo }); GL.halo(x, vuela + 0.3, z, 0.7, [1, 0.95, 0.6], 0.3); }
  }
  for (const me of M.mercaderes) {
    if (!me.ent) continue;
    const x = me.ent.x / T, z = me.ent.y / T + 0.02;
    GL.plano('sombra_s', x, z - 0.02, { tinte: [1, 1, 1, 0.7] });
    const nm = `mercader_${me.tipo}_${me.ent.anda ? Math.floor(J.t * 6) % 2 : 2 + (Math.floor(J.t * 1.5) % 2)}`, dxm = META_SPR[nm] ? META_SPR[nm].dx * (me.ent.mira < 0 ? -1 : 1) : 0;
    GL.sprite(nm, x + dxm, z, { flip: me.ent.mira < 0, lx: x, lz: z });
    if (SPR[nm + '_luz']) GL.sprite(nm + '_luz', x + dxm, z + 0.002, { flip: me.ent.mira < 0, unlit: 1, lote: GL.lotes.brillo });
  }
  if (J.regreso) GL.halo(J.jug.x / T, 0.8, J.jug.y / T, 1.3, [0.6, 0.85, 1], 0.35 + 0.1 * Math.sin(J.t * 20));
}
