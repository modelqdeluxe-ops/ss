'use strict';
// Controles (táctil, ratón y teclado), el HUD y los paneles: mochila, crear, mapa, pausa.
// Los botones se registran al dibujar (interfaz inmediata) y se comprueban al tocar.

const UI = {
  botones: [], joy: null, accion: null, teclas: new Set(), selInv: -1, scroll: 0, arrastre: null, confirmarNueva: 0,
};
// Madera oscura con canto claro, como los marcos de Core Keeper.
const COL = {
  fondo: '#2a1b14', borde: '#8a5a36', luz: '#c89058', negro: '#100806', texto: '#f6ecda', suave: '#bba48a',
  oro: '#f2c14e', rojo: '#d8343c', verde: '#8ef08a', hambre: '#e8903a', ranura: '#1c120d', boton: '#4a2f20', botonOff: '#2a1b14',
};

function boton(x, y, w, h, fn, o = {}) { UI.botones.push({ x, y, w, h, fn, ...o }); }
function enBoton(b, x, y) { return x >= b.x && y >= b.y && x < b.x + b.w && y < b.y + b.h; }

// ---------------------------------------------------------------- entrada

function posLogica(e) {
  const r = lienzo.getBoundingClientRect();
  return { x: ((e.clientX - r.left) * LW) / r.width, y: ((e.clientY - r.top) * LH) / r.height };
}
function puntoMundoTocado(x, y) {
  const p = J.jug, w = GL.aMundo(x / LW, y / LH, 0.3), wx = w.x * T, wy = w.z * T;
  return Math.hypot(wx - p.x, wy - p.y) < 48 ? { x: wx, y: wy } : null;
}
function alTocar(e) {
  e.preventDefault();
  Sonido.iniciar();
  const { x, y } = posLogica(e);
  for (let i = UI.botones.length - 1; i >= 0; i--) {
    const b = UI.botones[i];
    if (!enBoton(b, x, y)) continue;
    if (b.lista) { UI.arrastre = { id: e.pointerId, y0: y, s0: UI.scroll, movido: false, b }; return; }
    if (b.mantener) { UI.accion = { id: e.pointerId, apunte: null }; return; }
    b.fn(x, y); if (!b.mudo) Sonido.sfx.menu(); return;
  }
  if (J.estado !== 'juego' || J.panel) return;
  if (e.pointerType === 'mouse' || x > LW * 0.5) {
    UI.accion = { id: e.pointerId, apunte: puntoMundoTocado(x, y) };
    if (e.pointerType === 'mouse' && !UI.accion.apunte) { const w = GL.aMundo(x / LW, y / LH, 0.3); mirarA(J.jug, w.x * T, w.z * T); }
  } else UI.joy = { id: e.pointerId, ox: x, oy: y, x, y };
}
function alMover(e) {
  const { x, y } = posLogica(e);
  if (UI.joy && UI.joy.id === e.pointerId) { UI.joy.x = x; UI.joy.y = y; }
  if (UI.accion && UI.accion.id === e.pointerId && UI.accion.apunte) UI.accion.apunte = puntoMundoTocado(x, y);
  if (UI.arrastre && UI.arrastre.id === e.pointerId) {
    const dy = y - UI.arrastre.y0;
    if (Math.abs(dy) > 3) UI.arrastre.movido = true;
    UI.scroll = Math.max(0, UI.arrastre.s0 - dy);
  }
}
function alSoltar(e) {
  if (UI.joy && UI.joy.id === e.pointerId) UI.joy = null;
  if (UI.accion && UI.accion.id === e.pointerId) UI.accion = null;
  if (UI.arrastre && UI.arrastre.id === e.pointerId) {
    const a = UI.arrastre; UI.arrastre = null;
    if (!a.movido) { const { x, y } = posLogica(e); a.b.fn(x, y); }
  }
}
const TECLAS_MOV = { KeyW: [0, -1], ArrowUp: [0, -1], KeyS: [0, 1], ArrowDown: [0, 1], KeyA: [-1, 0], ArrowLeft: [-1, 0], KeyD: [1, 0], ArrowRight: [1, 0] };
function alTecla(e) {
  Sonido.iniciar();
  if (e.repeat) return;
  UI.teclas.add(e.code);
  if (e.code.startsWith('Digit')) { const n = +e.code.slice(5) || 10; if (n >= 1 && n <= N_BARRA) J.sel = n - 1; }
  const abrir = (p) => { if (J.estado === 'juego') { J.panel = J.panel === p ? null : p; UI.selInv = -1; UI.scroll = 0; } };
  if (e.code === 'KeyE' || e.code === 'KeyI' || e.code === 'Tab') { abrir('mochila'); e.preventDefault(); }
  if (e.code === 'KeyC') abrir('crear');
  if (e.code === 'KeyM') abrir('mapa');
  if (e.code === 'Escape' || e.code === 'KeyP') { if (J.estado === 'juego') J.panel = J.panel ? null : 'pausa'; }
  if (e.code === 'KeyF' && J.estado === 'juego' && !J.panel) { const pd = pedestalCerca(); if (pd && J.cuenta('fragmento')) colocarFragmento(pd); }
  if (e.code === 'Space' || TECLAS_MOV[e.code]) e.preventDefault();
}
function leerEntrada() {
  let mx = 0, my = 0;
  for (const k of UI.teclas) if (TECLAS_MOV[k]) { mx += TECLAS_MOV[k][0]; my += TECLAS_MOV[k][1]; }
  if (UI.joy) {
    const dx = UI.joy.x - UI.joy.ox, dy = UI.joy.y - UI.joy.oy, d = Math.hypot(dx, dy), R = 20;
    if (d > R) { UI.joy.ox = UI.joy.x - (dx / d) * R; UI.joy.oy = UI.joy.y - (dy / d) * R; } // la base sigue al dedo
    if (d > 2) { mx = dx / R; my = dy / R; }
  }
  J.entrada.mx = mx; J.entrada.my = my;
  const accion = UI.accion || UI.teclas.has('Space') || UI.teclas.has('KeyJ');
  if (accion && !J.panel && J.estado === 'juego') usar(UI.accion ? UI.accion.apunte : null);
}

// ---------------------------------------------------------------- dibujo de piezas

function marco(ctx, x, y, w, h, fondo = COL.fondo) {
  ctx.fillStyle = COL.negro; ctx.fillRect(x - 1, y, w + 2, h); ctx.fillRect(x, y - 1, w, h + 2);
  ctx.fillStyle = COL.borde; ctx.fillRect(x, y, w, h);
  ctx.fillStyle = COL.luz; ctx.fillRect(x + 1, y, w - 2, 1); ctx.fillRect(x, y + 1, 1, h - 2);
  ctx.fillStyle = '#5a3822'; ctx.fillRect(x + 1, y + h - 1, w - 2, 1); ctx.fillRect(x + w - 1, y + 1, 1, h - 2);
  ctx.fillStyle = fondo; ctx.fillRect(x + 1, y + 1, w - 2, h - 2);
}
function botonTexto(ctx, x, y, w, h, txt, fn, o = {}) {
  const activo = o.activo !== false;
  marco(ctx, x, y, w, h, o.color || (activo ? COL.boton : COL.botonOff));
  texto(ctx, txt, x + w / 2, y + Math.floor((h - 5) / 2), activo ? (o.colTexto || COL.texto) : COL.suave, { alin: 'c', sombra: COL.negro });
  if (fn) boton(x, y, w, h, fn, o);
}
function ranura(ctx, x, y, s, marcada, o = {}) {
  ctx.fillStyle = marcada ? '#fff4d8' : COL.borde; ctx.fillRect(x, y, 18, 18);
  ctx.fillStyle = marcada ? '#c8b088' : '#5a3822'; ctx.fillRect(x + 1, y + 17, 17, 1); ctx.fillRect(x + 17, y + 1, 1, 16);
  ctx.fillStyle = o.fondo || COL.ranura; ctx.fillRect(x + 1, y + 1, 16, 16);
  ctx.fillStyle = 'rgba(255,255,255,0.05)'; ctx.fillRect(x + 1, y + 1, 16, 1);
  if (s) {
    sprCentro(ctx, ITEMS[s.id].icono, x + 9, y + 9);
    if (s.n > 1) texto(ctx, s.n, x + 17, y + 12, COL.texto, { alin: 'd', borde: COL.negro });
  }
}
// Barra con marco de madera y cortes cada cuarto, como en Core Keeper.
function barra(ctx, x, y, w, h, v, col, fondo = '#3a1a14') {
  ctx.fillStyle = COL.negro; ctx.fillRect(x - 2, y - 2, w + 4, h + 4);
  ctx.fillStyle = COL.borde; ctx.fillRect(x - 1, y - 1, w + 2, h + 2);
  ctx.fillStyle = fondo; ctx.fillRect(x, y, w, h);
  const lleno = Math.round(w * Math.max(0, Math.min(1, v)));
  ctx.fillStyle = col; ctx.fillRect(x, y, lleno, h);
  ctx.fillStyle = 'rgba(255,255,255,0.3)'; ctx.fillRect(x, y, lleno, 1);
  ctx.fillStyle = 'rgba(0,0,0,0.25)'; ctx.fillRect(x, y + h - 1, lleno, 1);
  ctx.fillStyle = 'rgba(16,8,6,0.75)'; for (let k = 1; k < 4; k++) ctx.fillRect(x + Math.round((w * k) / 4), y, 1, h);
}

// ---------------------------------------------------------------- HUD

function dibujarHUD(ctx) {
  const p = J.jug;
  // vida, hambre, defensa
  spr(ctx, 'ui_vida', 3, 3);
  barra(ctx, 15, 5, 64, 5, p.vida / p.vidaMax, p.flash > 0 ? '#ffffff' : COL.rojo);
  texto(ctx, Math.ceil(p.vida), 84, 5, COL.texto, { borde: COL.negro });
  spr(ctx, 'ui_hambre', 4, 14);
  barra(ctx, 15, 16, 64, 3, p.hambre / 100, p.hambre < 20 && Math.floor(J.t * 3) % 2 ? '#ff6b6b' : COL.hambre, '#3a2210');
  if (defensa()) { spr(ctx, 'ui_escudo', 3, 23); texto(ctx, defensa(), 14, 25, COL.texto, { borde: COL.negro }); }

  // botones de arriba a la derecha
  const iconos = [['ui_pausa', 'pausa'], ['ui_mapa', 'mapa'], ['ui_crear', 'crear'], ['ui_mochila', 'mochila']];
  iconos.forEach(([ic, panel], k) => {
    const x = LW - 21 - k * 20, y = 3;
    marco(ctx, x, y, 18, 18, COL.boton);
    sprCentro(ctx, ic, x + 9, y + 9);
    boton(x - 1, y - 1, 20, 20, () => { J.panel = panel; UI.selInv = -1; UI.scroll = 0; });
  });
  // minimapa
  const mm = 50, mx = LW - mm - 4, my = 26;
  marco(ctx, mx - 2, my - 2, mm + 4, mm + 4, '#08060c');
  const mapa = lienzoMapa(), ptx = p.x / T, pty = p.y / T;
  ctx.drawImage(mapa, Math.round(ptx - mm / 2), Math.round(pty - mm / 2), mm, mm, mx, my, mm, mm);
  if (Math.floor(J.t * 4) % 2) { ctx.fillStyle = '#fff6c8'; ctx.fillRect(mx + mm / 2 - 1, my + mm / 2 - 1, 2, 2); }
  boton(mx, my, mm, mm, () => { J.panel = 'mapa'; });
  // objetivo
  const ob = OBJETIVOS[J.objetivo];
  if (ob) {
    const lineasOb = partirTexto(ob.txt, 86);
    lineasOb.forEach((l, k) => texto(ctx, l, LW - 4, my + mm + 5 + k * 7, '#ffe9a8', { alin: 'd', borde: COL.negro }));
  }

  // jefe
  const j = J.jefe;
  if (j && j.estado !== 'dormido' && j.estado !== 'muerto') {
    const w = Math.min(130, LW - 150), x = Math.round(LW / 2 - w / 2), y = LW > 330 ? 6 : my + mm + 22;
    texto(ctx, JEFE_TOPO.nombre, LW / 2, y, '#ffd166', { alin: 'c', borde: COL.negro });
    barra(ctx, x, y + 8, w, 4, j.vida / j.vidaMax, j.fase === 2 ? '#ff4060' : '#c43b3b');
  }

  // avisos
  J.avisos.forEach((a, k) => {
    const y = (LW < 300 ? 112 : 34) + k * 11, al = Math.min(1, a.t * 2);
    ctx.globalAlpha = al;
    const w = anchoTexto(a.txt) + (a.icono ? 14 : 0);
    const x0 = Math.round(LW / 2 - w / 2);
    if (a.icono) sprCentro(ctx, a.icono, x0 + 5, y + 2);
    texto(ctx, a.txt, x0 + (a.icono ? 14 : 0), y, a.color, { borde: COL.negro });
    ctx.globalAlpha = 1;
  });

  // barra rápida
  const bx = Math.round(LW / 2 - (N_BARRA * 19 - 1) / 2), by = LH - 21;
  for (let i = 0; i < N_BARRA; i++) {
    ranura(ctx, bx + i * 19, by, J.inv[i], i === J.sel);
    boton(bx + i * 19, by, 18, 18, () => { J.sel = i; }, { mudo: true });
  }
  const s = J.inv[J.sel];
  if (s && J.t - (J.selT || 0) < 1.6) texto(ctx, ITEMS[s.id].nombre, LW / 2, by - 8, COL.texto, { alin: 'c', borde: COL.negro });
  if (J.selPrev !== J.sel) { J.selPrev = J.sel; J.selT = J.t; }

  // botón de acción
  const ax = LW - 26, ay = LH - 50, pulsado = !!UI.accion;
  ctx.globalAlpha = pulsado ? 0.9 : 0.6;
  ctx.fillStyle = COL.negro; circulo(ctx, ax, ay, 17);
  ctx.fillStyle = COL.borde; circulo(ctx, ax, ay, 16);
  ctx.fillStyle = pulsado ? '#6a4630' : COL.boton; circulo(ctx, ax, ay, 15);
  ctx.globalAlpha = 1;
  const icono = iconoAccion();
  if (icono) sprCentro(ctx, icono, ax, ay);
  boton(ax - 18, ay - 18, 36, 36, null, { mantener: true });
  // botón de contexto (pedestal)
  if (J.cuenta('fragmento') && pedestalCerca()) {
    botonTexto(ctx, ax - 34, ay - 40, 60, 14, 'COLOCAR', () => { const pd = pedestalCerca(); if (pd) colocarFragmento(pd); }, { color: '#5e1430' });
  }
  // joystick
  if (UI.joy) {
    ctx.globalAlpha = 0.5; ctx.fillStyle = '#ffffff';
    anillo(ctx, UI.joy.ox, UI.joy.oy, 20);
    ctx.globalAlpha = 0.7; circulo(ctx, UI.joy.ox + J.entrada.mx * 20, UI.joy.oy + J.entrada.my * 20, 7);
    ctx.globalAlpha = 1;
  } else if (J.t < 30 && !UI.raton) {
    ctx.globalAlpha = 0.25 + 0.1 * Math.sin(J.t * 3); ctx.fillStyle = '#ffffff';
    anillo(ctx, 34, LH - 50, 20); circulo(ctx, 34, LH - 50, 7); ctx.globalAlpha = 1;
  }
}
function iconoAccion() {
  const s = J.inv[J.sel], it = s && ITEMS[s.id];
  if (it && (it.tipo === 'pico' || it.tipo === 'espada' || it.tipo === 'comida' || it.tipo === 'colocable')) return it.icono;
  const h = (enemigoCerca(J.jug, 30) ? mejor('espada') : null) || mejor('pico') || mejor('espada');
  return h ? h.icono : null;
}
function circulo(ctx, cx, cy, r) {
  for (let y = -r; y <= r; y++) { const w = Math.floor(Math.sqrt(r * r - y * y)); ctx.fillRect(Math.round(cx - w), Math.round(cy + y), w * 2 + 1, 1); }
}
function anillo(ctx, cx, cy, r) {
  let prev = 0;
  for (let y = -r; y <= r; y++) {
    const w = Math.floor(Math.sqrt(r * r - y * y));
    const ancho = Math.max(1, Math.abs(w - prev));
    ctx.fillRect(Math.round(cx - w), Math.round(cy + y), ancho, 1); ctx.fillRect(Math.round(cx + w - ancho + 1), Math.round(cy + y), ancho, 1);
    prev = w;
  }
}

// ---------------------------------------------------------------- paneles

function fondoPanel(ctx, cerrar = true) {
  ctx.fillStyle = 'rgba(8,6,12,0.72)'; ctx.fillRect(0, 0, LW, LH);
  boton(0, 0, LW, LH, cerrar ? () => { J.panel = null; UI.selInv = -1; } : () => {}, { mudo: true });
}
function cruz(ctx, x, y) {
  marco(ctx, x, y, 13, 13, '#3a1e2e');
  texto(ctx, '×', x + 5, y + 4, COL.texto);
  boton(x - 3, y - 3, 19, 19, () => { J.panel = null; UI.selInv = -1; });
}

function panelMochila(ctx) {
  fondoPanel(ctx);
  const w = N_BARRA * 19 - 1 + 12, h = 4 * 19 + 62, x = Math.round(LW / 2 - w / 2), y = Math.max(4, Math.round(LH / 2 - h / 2));
  marco(ctx, x, y, w, h); boton(x, y, w, h, () => { UI.selInv = -1; }, { mudo: true });
  texto(ctx, 'MOCHILA', x + 6, y + 6, COL.oro, { sombra: COL.negro });
  cruz(ctx, x + w - 17, y + 3);
  // armadura
  const axr = x + w - 41;
  texto(ctx, 'ARMADURA', axr - 3, y + 8, COL.suave, { alin: 'd' });
  ranura(ctx, axr, y + 3, J.armadura, false, { fondo: '#2e2018' });
  boton(axr, y + 3, 18, 18, () => {
    if (!J.armadura) return;
    const i = J.inv.findIndex((s, k) => !s && k < N_INV);
    if (i >= 0) { J.inv[i] = J.armadura; J.armadura = null; }
  });
  const gx = x + 6, gy = y + 24;
  for (let i = 0; i < N_INV; i++) {
    const cx = gx + (i % N_BARRA) * 19, cy = gy + Math.floor(i / N_BARRA) * 19 + (i >= N_BARRA ? 3 : 0);
    ranura(ctx, cx, cy, J.inv[i], i === UI.selInv, { fondo: i < N_BARRA ? '#2e2018' : COL.ranura });
    boton(cx, cy, 18, 18, () => tocarRanura(i), { mudo: true });
  }
  // información de lo marcado
  const s = UI.selInv >= 0 ? J.inv[UI.selInv] : null;
  const iy = gy + 4 * 19 + 7;
  if (s) {
    const it = ITEMS[s.id];
    texto(ctx, it.nombre, x + 6, iy, COL.oro);
    partirTexto(it.desc, w - 12).slice(0, 3).forEach((l, k) => texto(ctx, l, x + 6, iy + 9 + k * 7, COL.suave));
    let bx = x + w - 6;
    const mini = (txt, fn) => { const bw = anchoTexto(txt) + 8; bx -= bw; botonTexto(ctx, bx, iy - 3, bw, 11, txt, fn); bx -= 3; };
    mini('SOLTAR', () => { const t = J.inv[UI.selInv]; if (t) { soltar(J.jug.x + J.jug.fx * 20, J.jug.y + J.jug.fy * 20, t.id, t.n); J.inv[UI.selInv] = null; UI.selInv = -1; } });
    if (it.tipo === 'armadura') mini('PONER', () => ponerArmadura(UI.selInv));
  } else partirTexto('Toca algo para verlo. Toca otro hueco para moverlo.', w - 12).forEach((l, k) => texto(ctx, l, x + 6, iy + k * 7, COL.suave));
}
function tocarRanura(i) {
  if (UI.selInv === -1) { if (J.inv[i]) UI.selInv = i; Sonido.sfx.menu(); return; }
  if (UI.selInv === i) { UI.selInv = -1; return; }
  const a = J.inv[UI.selInv], b = J.inv[i];
  if (a && b && a.id === b.id && b.n < maxPila(b.id)) { const k = Math.min(a.n, maxPila(b.id) - b.n); b.n += k; a.n -= k; if (!a.n) J.inv[UI.selInv] = null; }
  else { J.inv[UI.selInv] = b; J.inv[i] = a; }
  UI.selInv = -1; Sonido.sfx.colocar();
}
function ponerArmadura(i) {
  const s = J.inv[i];
  if (!s || ITEMS[s.id].tipo !== 'armadura') return;
  J.inv[i] = J.armadura; J.armadura = s; UI.selInv = -1;
  Sonido.sfx.metal(); aviso(`Defensa ${defensa()}`);
}

function panelCrear(ctx) {
  fondoPanel(ctx);
  const w = Math.min(LW - 12, 236), h = LH - 12, x = Math.round(LW / 2 - w / 2), y = 6;
  marco(ctx, x, y, w, h); boton(x, y, w, h, () => {}, { mudo: true });
  texto(ctx, 'CREAR', x + 6, y + 6, COL.oro, { sombra: COL.negro });
  cruz(ctx, x + w - 17, y + 3);
  const est = estacionesCerca();
  texto(ctx, est.size ? 'Cerca: ' + [...est].map((e) => NOMBRE_ESTACION[e]).join(', ') : 'Sin estaciones cerca: solo lo básico', x + 6, y + 15, COL.suave);
  const lista = RECETAS.map((r) => ({ r, ok: puedeCrear(r, est), est: !r.est || est.has(r.est) }))
    .sort((a, b) => (b.ok - a.ok) || (b.est - a.est));
  const ly = y + 25, lh = h - 31, fila = 21;
  UI.scroll = Math.min(UI.scroll, Math.max(0, lista.length * fila - lh));
  ctx.save(); ctx.beginPath(); ctx.rect(x + 2, ly, w - 4, lh); ctx.clip();
  lista.forEach(({ r, ok, est: hayEst }, k) => {
    const fy = ly + k * fila - Math.round(UI.scroll);
    if (fy + fila < ly || fy > ly + lh) return;
    ctx.fillStyle = ok ? '#3e2a1e' : '#24170f'; ctx.fillRect(x + 3, fy, w - 6, fila - 2);
    if (ok) { ctx.fillStyle = COL.luz; ctx.fillRect(x + 3, fy, 2, fila - 2); }
    const it = ITEMS[r.item];
    ranura(ctx, x + 7, fy + 1, { id: r.item, n: r.n }, false);
    texto(ctx, it.nombre, x + 29, fy + 3, ok ? COL.texto : COL.suave);
    let ix = x + 29;
    for (const [id, n] of Object.entries(r.ing)) {
      const tiene = J.cuenta(id) >= n;
      sprCentro(ctx, ITEMS[id].icono, ix + 5, fy + 14);
      const t = `${J.cuenta(id)}/${n}`;
      texto(ctx, t, ix + 12, fy + 12, tiene ? COL.verde : '#ff8080');
      ix += 16 + anchoTexto(t);
    }
    if (!hayEst) texto(ctx, NOMBRE_ESTACION[r.est], x + w - 7, fy + 3, '#ffb070', { alin: 'd' });
    else if (ok) texto(ctx, 'CREAR', x + w - 7, fy + 3, COL.oro, { alin: 'd' });
    if (fy + fila > ly && fy < ly + lh) boton(x + 3, Math.max(fy, ly), w - 6, Math.min(fila - 2, ly + lh - fy), () => {
      if (crear(r)) particulas(J.jug.x, J.jug.y - 10, 8, ['#f2c14e', '#ffffff'], { vel: 40, brilla: true });
      else if (!hayEst) aviso(`Necesitas estar cerca de: ${NOMBRE_ESTACION[r.est]}`);
      else aviso('Te faltan materiales');
    }, { lista: true });
  });
  ctx.restore();
  if (lista.length * fila > lh) { // barra de desplazamiento
    const t = lh / (lista.length * fila), sy = ly + (UI.scroll / (lista.length * fila)) * lh;
    ctx.fillStyle = COL.borde; ctx.fillRect(x + w - 3, ly, 1, lh); ctx.fillStyle = COL.suave; ctx.fillRect(x + w - 3, Math.round(sy), 1, Math.round(lh * t));
  }
}

function panelMapa(ctx) {
  fondoPanel(ctx);
  ctx.fillStyle = '#08060c'; ctx.fillRect(0, 0, LW, LH);
  const mapa = lienzoMapa(), p = J.jug;
  const esc = Math.max(1, Math.floor(Math.min(LW, LH) / 110));
  const vw = Math.floor((LW - 8) / esc), vh = Math.floor((LH - 24) / esc);
  const cx = p.x / T, cy = p.y / T, sx = Math.round(cx - vw / 2), sy = Math.round(cy - vh / 2);
  const ox = Math.round((LW - vw * esc) / 2), oy = 18;
  ctx.drawImage(mapa, sx, sy, vw, vh, ox, oy, vw * esc, vh * esc);
  const marca = (tx, ty, f) => { const x = ox + (tx - sx) * esc, y = oy + (ty - sy) * esc; if (x > ox && y > oy && x < ox + vw * esc && y < oy + vh * esc) f(x, y); };
  marca(CENTRO, CENTRO, (x, y) => { ctx.fillStyle = '#ff6b8f'; circulo(ctx, x, y, 2 + (Math.floor(J.t * 2) % 2)); });
  if (M.arena && (J.objetivo >= 6 || M.visto[idx(M.arena.x, M.arena.y)]) && !J.hito.topo) marca(M.arena.x, M.arena.y, (x, y) => sprCentro(ctx, 'ui_calavera', x, y));
  marca(cx, cy, (x, y) => { if (Math.floor(J.t * 4) % 2) { ctx.fillStyle = '#fff6c8'; ctx.fillRect(x - 1, y - 1, 3, 3); } });
  texto(ctx, 'MAPA', 6, 6, COL.oro, { sombra: COL.negro });
  texto(ctx, 'Solo ves lo que has iluminado', 30, 6, COL.suave);
  cruz(ctx, LW - 17, 3);
}

function panelPausa(ctx) {
  fondoPanel(ctx);
  const w = 120, h = 92, x = Math.round(LW / 2 - w / 2), y = Math.round(LH / 2 - h / 2);
  marco(ctx, x, y, w, h); boton(x, y, w, h, () => {}, { mudo: true });
  texto(ctx, 'PAUSA', LW / 2, y + 6, COL.oro, { alin: 'c', sombra: COL.negro });
  botonTexto(ctx, x + 10, y + 18, w - 20, 15, 'SEGUIR', () => { J.panel = null; });
  botonTexto(ctx, x + 10, y + 37, w - 20, 15, 'SONIDO: ' + (Sonido.activo ? 'SÍ' : 'NO'), () => Sonido.alternar());
  botonTexto(ctx, x + 10, y + 56, w - 20, 15, 'GUARDAR', () => { guardarPartida(); aviso('Partida guardada'); J.panel = null; });
  botonTexto(ctx, x + 10, y + 73 + 2, w - 20, 13, 'SALIR AL MENÚ', () => { guardarPartida(); irTitulo(); });
}

function panelMensaje(ctx, titulo, txt, boton1, fn, colorT = COL.oro) {
  fondoPanel(ctx, false);
  const w = Math.min(LW - 20, 200), lineasT = partirTexto(txt, w - 16), h = 44 + lineasT.length * 8, x = Math.round(LW / 2 - w / 2), y = Math.round(LH / 2 - h / 2);
  marco(ctx, x, y, w, h);
  texto(ctx, titulo, LW / 2, y + 7, colorT, { alin: 'c', esc: 2, sombra: COL.negro });
  lineasT.forEach((l, k) => texto(ctx, l, LW / 2, y + 24 + k * 8, COL.texto, { alin: 'c' }));
  botonTexto(ctx, Math.round(LW / 2 - 50), y + h - 18, 100, 13, boton1, fn);
}

function dibujarPanel(ctx) {
  if (J.panel === 'mochila') panelMochila(ctx);
  else if (J.panel === 'crear') panelCrear(ctx);
  else if (J.panel === 'mapa') panelMapa(ctx);
  else if (J.panel === 'pausa') panelPausa(ctx);
  else if (J.panel === 'muerto') panelMensaje(ctx, 'TE DESMAYASTE', 'Te desmayas en la oscuridad. Lo que llevabas sigue contigo.', 'DESPERTAR', revivir, '#ff6b6b');
  else if (J.panel === 'fin') panelMensaje(ctx, 'EL CORAZÓN LATE', 'Despertaste el primer latido. En el juego completo quedan dos guardianes más: la Reina Larva en la arcilla y el Coloso de cristal en la piedra. ¡Sigue explorando!', 'SEGUIR', () => { J.panel = null; guardarPartida(); });
}

// ---------------------------------------------------------------- pantalla de título

function dibujarTitulo(ctx, hayPartida) {
  ctx.fillStyle = 'rgba(8,6,12,0.35)'; ctx.fillRect(0, 0, LW, LH);
  const esc = LW >= 300 ? 3 : 2, y0 = Math.round(LH * 0.18);
  const sacude = Math.sin(J.t * 1.3) * 1.5;
  texto(ctx, 'CORAZÓN', LW / 2, y0 + sacude, '#ff7aa0', { alin: 'c', esc, borde: '#2a0a18' });
  texto(ctx, 'DORMIDO', LW / 2, y0 + 9 * esc + 4 + sacude, '#f2ecf8', { alin: 'c', esc, borde: COL.negro });
  texto(ctx, 'Cava, crea y despierta lo que duerme bajo tierra', LW / 2, y0 + 18 * esc + 10, COL.suave, { alin: 'c', borde: COL.negro });
  let y = Math.round(LH * 0.55);
  const w = 110, x = Math.round(LW / 2 - w / 2);
  if (hayPartida) { botonTexto(ctx, x, y, w, 16, 'CONTINUAR', () => empezar(true)); y += 21; }
  const confirmar = UI.confirmarNueva > J.t;
  botonTexto(ctx, x, y, w, 16, confirmar ? '¿SEGURO? TOCA OTRA VEZ' : 'NUEVA PARTIDA', () => {
    if (hayPartida && !confirmar) { UI.confirmarNueva = J.t + 3; return; }
    empezar(false);
  }, { color: confirmar ? '#5e1430' : undefined });
  y += 21;
  botonTexto(ctx, x, y, w, 13, 'SONIDO: ' + (Sonido.activo ? 'SÍ' : 'NO'), () => Sonido.alternar());
  const pie = partirTexto('Demo 0.1 · izquierda: moverte · derecha: picar y pelear', LW - 16);
  pie.forEach((l, k) => texto(ctx, l, LW / 2, LH - 10 - (pie.length - 1 - k) * 8, COL.suave, { alin: 'c', borde: COL.negro }));
}
