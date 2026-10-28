'use strict';
// Controles (táctil, ratón y teclado), el HUD y los paneles: mochila (con equipo y habilidades), crear, cofre,
// olla, mapa, Corazón y pausa. Los botones se registran al dibujar (interfaz inmediata) y se comprueban al tocar.

const UI = { botones: [], joy: null, accion: null, teclas: new Set(), selInv: -1, scroll: 0, arrastre: null, confirmarNueva: 0, pestana: 'mochila', selCofre: -1 };
// Madera oscura con canto claro, como los marcos de Core Keeper.
const COL = {
  fondo: '#2a1b14', borde: '#8a5a36', luz: '#c89058', negro: '#100806', texto: '#f6ecda', suave: '#bba48a',
  oro: '#f2c14e', rojo: '#d8343c', verde: '#8ef08a', hambre: '#e8903a', ranura: '#1c120d', boton: '#4a2f20', botonOff: '#2a1b14',
};

function boton(x, y, w, h, fn, o = {}) { UI.botones.push({ x, y, w, h, fn, ...o }); }
function enBoton(b, x, y) { return x >= b.x && y >= b.y && x < b.x + b.w && y < b.y + b.h; }
function abrirPanel(p) { if (J.estado !== 'juego') return; J.panel = J.panel === p ? null : p; UI.selInv = -1; UI.selCofre = -1; UI.scroll = 0; }

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
  if (e.code === 'KeyE' || e.code === 'KeyI' || e.code === 'Tab') { abrirPanel('mochila'); e.preventDefault(); }
  if (e.code === 'KeyC') abrirPanel('crear');
  if (e.code === 'KeyM') abrirPanel('mapa');
  if (e.code === 'KeyF' && J.estado === 'juego' && !J.panel) { const o = interactivoCerca(); if (o) interactuar(o); else { const u = usoCercano(); if (u) u.fn(); } }
  if (e.code === 'Escape' || e.code === 'KeyP') { if (J.estado === 'juego') J.panel = J.panel ? null : 'pausa'; }
  if (e.code === 'Space' || TECLAS_MOV[e.code]) e.preventDefault();
}
function leerEntrada() {
  let mx = 0, my = 0;
  for (const k of UI.teclas) if (TECLAS_MOV[k]) { mx += TECLAS_MOV[k][0]; my += TECLAS_MOV[k][1]; }
  if (UI.joy) {
    const dx = UI.joy.x - UI.joy.ox, dy = UI.joy.y - UI.joy.oy, d = Math.hypot(dx, dy), R = 20;
    if (d > R) { UI.joy.ox = UI.joy.x - (dx / d) * R; UI.joy.oy = UI.joy.y - (dy / d) * R; }
    if (d > 2) { mx = dx / R; my = dy / R; }
  }
  J.entrada.mx = mx; J.entrada.my = my;
  const accion = !!(UI.accion || UI.teclas.has('Space') || UI.teclas.has('KeyJ'));
  const nueva = accion && !J.accionAntes;
  J.accionAntes = accion; J.tirando = accion;
  if (J.panel || J.estado !== 'juego') return;
  if (J.pesca) { if (nueva && J.pesca.estado !== 'lucha') usar(null); return; }
  if (accion) usar(UI.accion ? UI.accion.apunte : null);
}

// ---------------------------------------------------------------- piezas

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
  const it = s && asegurarItem(s.id), rar = it ? it.rareza || 0 : 0;
  ctx.fillStyle = marcada ? '#fff4d8' : rar ? RAREZA[rar].color : COL.borde; ctx.fillRect(x, y, 18, 18);
  ctx.fillStyle = marcada ? '#c8b088' : '#5a3822'; ctx.fillRect(x + 1, y + 17, 17, 1); ctx.fillRect(x + 17, y + 1, 1, 16);
  ctx.fillStyle = o.fondo || COL.ranura; ctx.fillRect(x + 1, y + 1, 16, 16);
  ctx.fillStyle = 'rgba(255,255,255,0.05)'; ctx.fillRect(x + 1, y + 1, 16, 1);
  if (s && it) {
    sprCentro(ctx, it.icono, x + 9, y + 9);
    if (s.n > 1) texto(ctx, s.n, x + 17, y + 12, COL.texto, { alin: 'd', borde: COL.negro });
  } else if (o.vacio) { ctx.globalAlpha = 0.3; sprCentro(ctx, o.vacio, x + 9, y + 9); ctx.globalAlpha = 1; }
}
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
function circulo(ctx, cx, cy, r) { for (let y = -r; y <= r; y++) { const w = Math.floor(Math.sqrt(r * r - y * y)); ctx.fillRect(Math.round(cx - w), Math.round(cy + y), w * 2 + 1, 1); } }
function anillo(ctx, cx, cy, r) {
  let prev = 0;
  for (let y = -r; y <= r; y++) {
    const w = Math.floor(Math.sqrt(r * r - y * y)), ancho = Math.max(1, Math.abs(w - prev));
    ctx.fillRect(Math.round(cx - w), Math.round(cy + y), ancho, 1); ctx.fillRect(Math.round(cx + w - ancho + 1), Math.round(cy + y), ancho, 1);
    prev = w;
  }
}
// Nombre (con el color de su rareza) y descripción de un objeto.
function infoObjeto(ctx, s, x, y, w, lineasMax = 3) {
  const it = asegurarItem(s.id);
  texto(ctx, it.nombre, x, y, RAREZA[it.rareza || 0].color);
  const extra = [];
  if (it.poder) extra.push(`Poder de minado ${it.poder}`);
  if (it.dano && it.tipo !== 'accesorio' && it.tipo !== 'municion') extra.push(`Daño ${it.dano}`);
  if (it.def) extra.push(`Defensa ${it.def}`);
  if (it.tipo === 'mascota') extra.push(`Nivel ${nivelMascota(s.id)}`);
  if (J.panel === 'tienda' && valorVenta(s.id)) extra.push(`Vale ${valorVenta(s.id)} monedas`);
  const txt = (extra.length ? extra.join(' · ') + '. ' : '') + (it.desc || '');
  partirTexto(txt, w).slice(0, lineasMax).forEach((l, k) => texto(ctx, l, x, y + 8 + k * 7, COL.suave));
}

// ---------------------------------------------------------------- HUD

function dibujarHUD(ctx) {
  const p = J.jug, st = J.st || calcularStats();
  spr(ctx, 'ui_vida', 3, 3);
  barra(ctx, 15, 5, 64, 5, p.vida / st.vidaMax, p.flash > 0 ? '#ffffff' : COL.rojo);
  texto(ctx, Math.ceil(p.vida), 84, 5, COL.texto, { borde: COL.negro });
  spr(ctx, 'ui_hambre', 4, 14);
  barra(ctx, 15, 16, 64, 3, p.hambre / 100, p.hambre < 20 && Math.floor(J.t * 3) % 2 ? '#ff6b6b' : COL.hambre, '#3a2210');
  let fx = 3;
  if (st.def) { spr(ctx, 'ui_escudo', fx, 23); texto(ctx, st.def, fx + 11, 25, COL.texto, { borde: COL.negro }); fx += 22; }
  for (const b of J.buffs) { // mejoras de comida activas
    sprCentro(ctx, b.icono, fx + 6, 28);
    const quedan = Math.ceil((b.fin - J.tiempoMundo) / 60);
    texto(ctx, quedan + "'", fx + 12, 30, COL.verde, { borde: COL.negro });
    fx += 22;
  }

  const iconos = [['ui_pausa', 'pausa'], ['ui_mapa', 'mapa'], ['ui_crear', 'crear'], ['ui_mochila', 'mochila']];
  iconos.forEach(([ic, panel], k) => {
    const x = LW - 21 - k * 20, y = 3;
    marco(ctx, x, y, 18, 18, COL.boton);
    sprCentro(ctx, ic, x + 9, y + 9);
    boton(x - 1, y - 1, 20, 20, () => abrirPanel(panel));
  });
  const mm = 50, mx = LW - mm - 4, my = 26;
  marco(ctx, mx - 2, my - 2, mm + 4, mm + 4, '#08060c');
  const mapa = lienzoMapa(), ptx = p.x / T, pty = p.y / T;
  ctx.drawImage(mapa, Math.round(ptx - mm / 2), Math.round(pty - mm / 2), mm, mm, mx, my, mm, mm);
  if (Math.floor(J.t * 4) % 2) { ctx.fillStyle = '#fff6c8'; ctx.fillRect(mx + mm / 2 - 1, my + mm / 2 - 1, 2, 2); }
  boton(mx, my, mm, mm, () => abrirPanel('mapa'));
  const reg = M.region[idx(Math.floor(ptx), Math.floor(pty))];
  texto(ctx, NOMBRE_REGION[reg] || '', LW - 4, my + mm + 4, COL.suave, { alin: 'd', borde: COL.negro });
  const ob = OBJETIVOS[J.objetivo];
  if (ob) partirTexto(ob.txt, 90).forEach((l, k) => texto(ctx, l, LW - 4, my + mm + 13 + k * 7, '#ffe9a8', { alin: 'd', borde: COL.negro }));

  // jefe activo
  const j = Object.values(J.jefes).find((x) => x.activo && x.estado !== 'muerto');
  if (j) {
    const w = Math.min(140, LW - 150), x = Math.round(LW / 2 - w / 2), y = LW > 330 ? 6 : my + mm + 34;
    texto(ctx, (JEFES[j.id].nombre + (JEFES[j.id].titulo ? ', ' + JEFES[j.id].titulo : '')).toUpperCase(), LW / 2, y, '#ffd166', { alin: 'c', borde: COL.negro });
    barra(ctx, x, y + 8, w, 4, j.vida / j.vidaMax, j.fase === 2 ? '#ff4060' : '#c43b3b');
  }
  J.avisos.forEach((a, k) => {
    const y = (LW < 300 ? 122 : 40) + k * 11;
    ctx.globalAlpha = Math.min(1, a.t * 2);
    const w = anchoTexto(a.txt) + (a.icono ? 14 : 0), x0 = Math.round(LW / 2 - w / 2);
    if (a.icono) sprCentro(ctx, a.icono, x0 + 5, y + 2);
    texto(ctx, a.txt, x0 + (a.icono ? 14 : 0), y, a.color, { borde: COL.negro });
    ctx.globalAlpha = 1;
  });

  // barra rápida
  const bx = Math.round(LW / 2 - (N_BARRA * 19 - 1) / 2), by = LH - 21;
  for (let i = 0; i < N_BARRA; i++) {
    ranura(ctx, bx + i * 19, by, J.inv[i], i === J.sel);
    boton(bx + i * 19, by, 18, 18, () => { J.sel = i; J.pesca = null; }, { mudo: true });
  }
  const s = J.inv[J.sel];
  if (J.selPrev !== J.sel) { J.selPrev = J.sel; J.selT = J.t; }
  if (s && J.t - (J.selT || 0) < 1.6) { const it = asegurarItem(s.id); texto(ctx, it.nombre, LW / 2, by - 8, RAREZA[it.rareza || 0].color, { alin: 'c', borde: COL.negro }); }

  // botón de acción y botón de «usar»
  const ax = LW - 26, ay = LH - 52, pulsado = !!UI.accion;
  ctx.globalAlpha = pulsado ? 0.9 : 0.65;
  ctx.fillStyle = COL.negro; circulo(ctx, ax, ay, 17);
  ctx.fillStyle = COL.borde; circulo(ctx, ax, ay, 16);
  ctx.fillStyle = pulsado ? '#6a4630' : COL.boton; circulo(ctx, ax, ay, 15);
  ctx.globalAlpha = 1;
  const icono = iconoAccion();
  if (icono) sprCentro(ctx, icono, ax, ay);
  boton(ax - 18, ay - 18, 36, 36, null, { mantener: true });
  const o = !J.pesca && interactivoCerca(), esp = !J.pesca && !o ? usoCercano() : null;
  if (o) {
    const que = OBJ[o.tipo].interactua, txt = { crear: 'CREAR', cofre: 'ABRIR', olla: 'COCINAR', cama: 'DORMIR', corazon: 'HABLAR', estatua: o.extra ? 'MIRAR' : 'OFRECER', altar_titan: o.extra ? 'MIRAR' : 'OFRECER', altar_cielo: 'OFRECER', tablilla: 'LEER', incubadora: 'INCUBAR', idolo: 'MIRAR', brasero: o.extra ? 'MIRAR' : 'ENCENDER', palanca: 'TIRAR', empujar: 'EMPUJAR', sellada: 'MIRAR', paso: J.pasos && J.pasos[o.datos && o.datos.nombre] ? 'VIAJAR' : 'TOCAR', restos: o.datos && o.datos.visto ? 'MIRAR' : 'REGISTRAR' }[que] || 'USAR';
    botonTexto(ctx, ax - 30, ay - 40, 52, 14, txt, () => interactuar(o), { color: '#3e6a2a' });
  } else if (esp) botonTexto(ctx, ax - 30, ay - 40, 52, 14, esp.txt, esp.fn, { color: '#3e6a2a' });
  if (J.cartel) { // nombre de la región al entrar por primera vez
    ctx.globalAlpha = Math.min(1, J.cartel.t, (3.5 - J.cartel.t) * 2);
    texto(ctx, J.cartel.txt.toUpperCase(), LW / 2, Math.round(LH * 0.3), '#f6ecda', { alin: 'c', esc: 2, borde: COL.negro });
    ctx.globalAlpha = 1;
  }
  if (J.presentacion) { // el jefe se presenta al despertar: nombre, título y lo que dice
    const P = J.presentacion, a = Math.max(0, Math.min(1, (5 - P.t) * 2, P.t * 1.2));
    const y0 = Math.round(LH * 0.6);
    ctx.globalAlpha = a * 0.55; ctx.fillStyle = '#000000'; ctx.fillRect(0, y0, LW, 44); ctx.globalAlpha = a;
    texto(ctx, P.nombre.toUpperCase(), LW / 2, y0 + 6, '#f2e4d0', { alin: 'c', esc: 2, borde: COL.negro });
    texto(ctx, P.titulo, LW / 2, y0 + 22, '#c86a8a', { alin: 'c', borde: COL.negro });
    texto(ctx, '«' + P.frase + '»', LW / 2, y0 + 32, '#b8aab8', { alin: 'c', borde: COL.negro });
    ctx.globalAlpha = 1;
  }
  if (J.regreso) texto(ctx, 'Volviendo…', LW / 2, Math.round(LH * 0.62), '#a0e0ff', { alin: 'c', borde: COL.negro });
  if (UI.joy) {
    ctx.globalAlpha = 0.5; ctx.fillStyle = '#ffffff';
    anillo(ctx, UI.joy.ox, UI.joy.oy, 20);
    ctx.globalAlpha = 0.7; circulo(ctx, UI.joy.ox + J.entrada.mx * 20, UI.joy.oy + J.entrada.my * 20, 7);
    ctx.globalAlpha = 1;
  } else if (J.t < 40 && !UI.raton) {
    ctx.globalAlpha = 0.22 + 0.1 * Math.sin(J.t * 3); ctx.fillStyle = '#ffffff';
    anillo(ctx, 34, LH - 52, 20); circulo(ctx, 34, LH - 52, 7); ctx.globalAlpha = 1;
  }
  if (J.pesca) dibujarPesca(ctx);
}
function iconoAccion() {
  const s = J.inv[J.sel], it = s && asegurarItem(s.id);
  if (it && it.tipo !== 'material') return it.icono;
  const h = (enemigoCerca(J.jug, 30) ? mejor('espada') : null) || mejor('pico') || mejor('espada');
  return h ? h.icono : null;
}
function dibujarPesca(ctx) {
  const ps = J.pesca, w = 120, x = Math.round(LW / 2 - w / 2), y = LH - 64;
  if (ps.estado === 'espera') { texto(ctx, 'Espera a que pique…', LW / 2, y + 6, COL.suave, { alin: 'c', borde: COL.negro }); return; }
  if (ps.estado === 'pica') { if (Math.floor(J.t * 8) % 2) texto(ctx, '¡TIRA!', LW / 2, y, '#ffd166', { alin: 'c', esc: 2, borde: COL.negro }); return; }
  marco(ctx, x - 4, y - 14, w + 8, 34);
  texto(ctx, ps.lucha ? '¡SUELTA! Está tirando' : 'MANTÉN para recoger', LW / 2, y - 10, ps.lucha ? '#ff8080' : COL.verde, { alin: 'c' });
  barra(ctx, x, y, w, 5, ps.progreso, '#6ab0ff', '#14243a');
  const fxp = x + Math.round(ps.progreso * w), sacude = ps.lucha ? Math.sin(J.t * 50) * 1.5 : 0;
  sprCentro(ctx, ITEMS[ps.pez].icono, fxp + sacude, y + 2);
  barra(ctx, x, y + 11, w, 3, ps.tension, ps.tension > 0.7 ? '#ff4040' : '#e8c050', '#2a1a10');
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
function pestanas(ctx, x, y, lista) {
  let px = x;
  for (const [id, txt] of lista) {
    const w = anchoTexto(txt) + 10, activa = UI.pestana === id;
    botonTexto(ctx, px, y, w, 12, txt, () => { UI.pestana = id; UI.selInv = -1; }, { color: activa ? '#7a4a2a' : COL.botonOff, colTexto: activa ? COL.oro : COL.suave });
    px += w + 2;
  }
}
// Rejilla de un inventario. al(i) se llama al tocar.
function rejilla(ctx, inv, x, y, cols, marcada, al, desde = 0, hasta = inv.length) {
  for (let i = desde; i < hasta; i++) {
    const k = i - desde, cx = x + (k % cols) * 19, cy = y + Math.floor(k / cols) * 19;
    ranura(ctx, cx, cy, inv[i], i === marcada, { fondo: inv === J.inv && i < N_BARRA ? '#2e2018' : COL.ranura });
    boton(cx, cy, 18, 18, () => al(i), { mudo: true });
  }
}

function panelMochila(ctx) {
  fondoPanel(ctx);
  const hab = UI.pestana === 'habilidades';
  const w = N_BARRA * 19 - 1 + 12, h = hab ? Math.min(LH - 8, 28 + Object.keys(HABILIDADES).length * 14) : 4 * 19 + 96, x = Math.round(LW / 2 - w / 2), y = Math.max(4, Math.round(LH / 2 - h / 2));
  marco(ctx, x, y, w, h); boton(x, y, w, h, () => { UI.selInv = -1; }, { mudo: true });
  pestanas(ctx, x + 5, y + 4, [['mochila', 'MOCHILA'], ['habilidades', 'HABILIDADES']]);
  cruz(ctx, x + w - 17, y + 3);
  if (UI.pestana === 'habilidades') { panelHabilidades(ctx, x, y, w, h); return; }
  // equipo
  const ey = y + 20;
  const vacios = { casco: 'i_casco_cobre', peto: 'i_peto_cobre', botas: 'i_botas_cobre', acc1: 'i_anillo_cobre', acc2: 'i_anillo_cobre', mascota: 'i_mascota_topito' };
  RANURAS_EQUIPO.forEach((r, k) => {
    const ex = x + 6 + k * 19;
    ranura(ctx, ex, ey, J.equipo[r], false, { fondo: '#2e2018', vacio: vacios[r] });
    boton(ex, ey, 18, 18, () => {
      if (UI.selInv >= 0) { ponerEquipo(UI.selInv, r); return; }
      if (J.equipo[r] && agregar(J.equipo[r].id, 1) === 0) { J.equipo[r] = null; Sonido.sfx.metal(); }
    }, { mudo: true });
  });
  const st = J.st || calcularStats();
  texto(ctx, `Vida ${st.vidaMax} Def ${st.def}`, x + 6 + 6 * 19 + 3, ey + 2, COL.texto);
  texto(ctx, `Vel ${Math.round(st.vel * 100)}% Daño ${Math.round(st.dano * 100)}%`, x + 6 + 6 * 19 + 3, ey + 10, COL.suave);
  const gy = ey + 23;
  rejilla(ctx, J.inv, x + 6, gy, N_BARRA, UI.selInv, tocarRanura);
  const s = UI.selInv >= 0 ? J.inv[UI.selInv] : null, iy = gy + 4 * 19 + 5;
  if (s) {
    infoObjeto(ctx, s, x + 6, iy, w - 12, 2);
    let bx = x + w - 6;
    const mini = (txt, fn) => { const bw = anchoTexto(txt) + 8; bx -= bw; botonTexto(ctx, bx, iy - 3, bw, 11, txt, fn); bx -= 3; };
    mini('SOLTAR', () => { const t = J.inv[UI.selInv]; if (t) { soltar(J.jug.x + J.jug.fx * 20, J.jug.y + J.jug.fy * 20, t.id, t.n); J.inv[UI.selInv] = null; UI.selInv = -1; } });
    const it = asegurarItem(s.id);
    if (TIPO_RANURA[it.tipo] || it.tipo === 'accesorio' || it.tipo === 'mascota') mini('PONER', () => ponerEquipo(UI.selInv));
    if (it.tipo === 'comida') mini('COMER', () => { comer(J.inv[UI.selInv]); if (!J.inv[UI.selInv]) UI.selInv = -1; });
  } else partirTexto('Toca algo para verlo; toca otro hueco para moverlo. La armadura se pone tocándola y luego su hueco.', w - 12).slice(0, 3).forEach((l, k) => texto(ctx, l, x + 6, iy + k * 7, COL.suave));
}
function tocarRanura(i) {
  if (UI.selInv === -1) { if (J.inv[i]) UI.selInv = i; Sonido.sfx.menu(); return; }
  if (UI.selInv === i) { UI.selInv = -1; return; }
  const a = J.inv[UI.selInv], b = J.inv[i];
  if (a && b && a.id === b.id && b.n < maxPila(b.id)) { const k = Math.min(a.n, maxPila(b.id) - b.n); b.n += k; a.n -= k; if (!a.n) J.inv[UI.selInv] = null; }
  else { J.inv[UI.selInv] = b; J.inv[i] = a; }
  UI.selInv = -1; Sonido.sfx.colocar();
}
function ponerEquipo(i, ranuraPedida) {
  const s = J.inv[i];
  if (!s) return;
  const it = ITEMS[s.id];
  const r = ranuraPedida && TIPO_RANURA[ranuraPedida] === it.tipo ? ranuraPedida : it.tipo === 'accesorio' ? (J.equipo.acc1 ? (J.equipo.acc2 ? 'acc1' : 'acc2') : 'acc1') : TIPO_RANURA[it.tipo] ? it.tipo : null;
  if (!r) { aviso('Eso no se pone'); return; }
  const antes = J.equipo[r];
  J.equipo[r] = { id: s.id, n: 1 };
  J.inv[i] = antes;
  UI.selInv = -1; Sonido.sfx.metal(); calcularStats();
}
function panelHabilidades(ctx, x, y, w) {
  let fy = y + 22;
  for (const [id, hb] of Object.entries(HABILIDADES)) {
    const n = nivel(id), xp = (J.hab[id] && J.hab[id].xp) || 0;
    texto(ctx, hb.nombre, x + 8, fy, COL.texto);
    texto(ctx, 'Nv ' + n, x + 82, fy, COL.oro);
    barra(ctx, x + 104, fy, w - 116, 3, n >= NIVEL_MAX ? 1 : xp / xpNivel(n), '#6ab0ff', '#14243a');
    texto(ctx, hb.desc, x + 8, fy + 6, COL.suave);
    fy += 14;
  }
}

function panelCrear(ctx) {
  fondoPanel(ctx);
  const w = Math.min(LW - 12, 240), h = LH - 12, x = Math.round(LW / 2 - w / 2), y = 6;
  marco(ctx, x, y, w, h); boton(x, y, w, h, () => {}, { mudo: true });
  texto(ctx, 'CREAR', x + 6, y + 6, COL.oro, { sombra: COL.negro });
  cruz(ctx, x + w - 17, y + 3);
  const ctxEst = estacionesCerca(), est = ctxEst.est;
  const nombres = [...est].filter((e) => !(NIVEL_MESA[e] && [...est].some((o) => (NIVEL_MESA[o] || 0) > NIVEL_MESA[e])) && !(NIVEL_YUNQUE[e] && [...est].some((o) => (NIVEL_YUNQUE[o] || 0) > NIVEL_YUNQUE[e])));
  const cab = (est.size ? 'Cerca: ' + nombres.map((e) => NOMBRE_ESTACION[e]).join(', ') : 'Sin estaciones cerca: solo lo básico') + (ctxEst.cofres.length ? ` · ${ctxEst.cofres.length} cofre${ctxEst.cofres.length > 1 ? 's' : ''}` : '');
  partirTexto(cab, w - 12).slice(0, 2).forEach((l, k) => texto(ctx, l, x + 6, y + 15 + k * 7, COL.suave));
  const lista = RECETAS.map((r) => ({ r, ok: puedeCrear(r, ctxEst), est: !r.est || est.has(r.est) }))
    .filter((e) => e.est || e.ok || Object.keys(e.r.ing).some((id) => cuentaTotal(id, ctxEst) > 0))
    .sort((a, b) => (b.ok - a.ok) || (b.est - a.est));
  const ly = y + 30, lh = h - 36, fila = 21;
  UI.scroll = Math.min(UI.scroll, Math.max(0, lista.length * fila - lh));
  ctx.save(); ctx.beginPath(); ctx.rect(x + 2, ly, w - 4, lh); ctx.clip();
  lista.forEach(({ r, ok, est: hayEst }, k) => {
    const fy = ly + k * fila - Math.round(UI.scroll);
    if (fy + fila < ly || fy > ly + lh) return;
    ctx.fillStyle = ok ? '#3e2a1e' : '#24170f'; ctx.fillRect(x + 3, fy, w - 6, fila - 2);
    if (ok) { ctx.fillStyle = COL.luz; ctx.fillRect(x + 3, fy, 2, fila - 2); }
    const it = ITEMS[r.item];
    ranura(ctx, x + 7, fy + 1, { id: r.item, n: r.n }, false);
    texto(ctx, it.nombre, x + 29, fy + 3, ok ? RAREZA[it.rareza || 0].color : COL.suave);
    let ix = x + 29;
    for (const [id, n] of Object.entries(r.ing)) {
      const tiene = cuentaTotal(id, ctxEst);
      sprCentro(ctx, ITEMS[id].icono, ix + 5, fy + 14);
      const t = `${tiene}/${n}`;
      texto(ctx, t, ix + 12, fy + 12, tiene >= n ? COL.verde : '#ff8080');
      ix += 16 + anchoTexto(t);
    }
    if (!hayEst) texto(ctx, NOMBRE_ESTACION[r.est], x + w - 7, fy + 3, '#ffb070', { alin: 'd' });
    else if (ok) texto(ctx, 'CREAR', x + w - 7, fy + 3, COL.oro, { alin: 'd' });
    boton(x + 3, Math.max(fy, ly), w - 6, Math.min(fila - 2, ly + lh - fy), () => {
      if (crear(r)) particulas(J.jug.x, J.jug.y - 10, 8, ['#f2c14e', '#ffffff'], { vel: 40, brilla: true });
      else if (!hayEst) aviso(`Necesitas estar cerca de: ${NOMBRE_ESTACION[r.est]}`);
      else aviso('Te faltan materiales');
    }, { lista: true });
  });
  ctx.restore();
  if (lista.length * fila > lh) {
    const t = lh / (lista.length * fila), sy = ly + (UI.scroll / (lista.length * fila)) * lh;
    ctx.fillStyle = COL.borde; ctx.fillRect(x + w - 3, ly, 1, lh); ctx.fillStyle = COL.suave; ctx.fillRect(x + w - 3, Math.round(sy), 1, Math.round(lh * t));
  }
}

function panelCofre(ctx) {
  const c = J.cofreAbierto;
  if (!c || !M.objetos.has(idx(c.x, c.y))) { J.panel = null; return; }
  fondoPanel(ctx);
  const w = N_BARRA * 19 - 1 + 12, h = 3 * 19 + 4 * 19 + 52, x = Math.round(LW / 2 - w / 2), y = Math.max(3, Math.round(LH / 2 - h / 2));
  marco(ctx, x, y, w, h); boton(x, y, w, h, () => {}, { mudo: true });
  texto(ctx, c.tipo === 'cofre_ruinas' ? 'COFRE ANTIGUO' : 'COFRE', x + 6, y + 6, COL.oro, { sombra: COL.negro });
  cruz(ctx, x + w - 17, y + 3);
  const inv = c.datos.inv;
  rejilla(ctx, inv, x + 6, y + 17, N_BARRA, -1, (i) => pasarPila(inv, i, J.inv));
  const by = y + 17 + 3 * 19 + 3;
  botonTexto(ctx, x + 6, by, 56, 11, 'APILAR', () => apilarEnCofre(inv));
  botonTexto(ctx, x + 66, by, 56, 11, 'SACAR TODO', () => { for (let i = 0; i < inv.length; i++) if (inv[i]) pasarPila(inv, i, J.inv); });
  texto(ctx, 'Toca para pasar', x + w - 6, by + 3, COL.suave, { alin: 'd' });
  rejilla(ctx, J.inv, x + 6, by + 15, N_BARRA, -1, (i) => pasarPila(J.inv, i, inv));
}

function panelOlla(ctx) {
  const o = J.olla;
  if (!o || !M.objetos.has(idx(o.x, o.y))) { J.panel = null; return; }
  fondoPanel(ctx);
  const w = N_BARRA * 19 - 1 + 12, h = 4 * 19 + 66, x = Math.round(LW / 2 - w / 2), y = Math.max(3, Math.round(LH / 2 - h / 2));
  marco(ctx, x, y, w, h); boton(x, y, w, h, () => {}, { mudo: true });
  texto(ctx, 'OLLA', x + 6, y + 6, COL.oro, { sombra: COL.negro });
  cruz(ctx, x + w - 17, y + 3);
  const d = o.datos, sy = y + 18;
  ranura(ctx, x + 8, sy, d.a ? { id: d.a, n: 1 } : null, false); boton(x + 8, sy, 18, 18, () => { d.a = null; });
  texto(ctx, '+', x + 31, sy + 7, COL.texto);
  ranura(ctx, x + 38, sy, d.b ? { id: d.b, n: 1 } : null, false); boton(x + 38, sy, 18, 18, () => { d.b = null; });
  texto(ctx, '=', x + 61, sy + 7, COL.texto);
  if (d.a && d.b) {
    const [a, b] = [d.a, d.b].sort(), id = registrarPlato(a, b), it = ITEMS[id];
    ranura(ctx, x + 68, sy, { id, n: 1 }, false);
    texto(ctx, it.nombre, x + 90, sy + 1, RAREZA[it.rareza || 0].color);
    partirTexto(it.desc, w - 96).slice(0, 2).forEach((l, k) => texto(ctx, l, x + 90, sy + 9 + k * 7, COL.suave));
  } else texto(ctx, 'Toca dos ingredientes de abajo', x + 72, sy + 7, COL.suave);
  botonTexto(ctx, x + w - 64, sy + 23, 58, 12, 'COCINAR', () => cocinar(o), { activo: !!(d.a && d.b) });
  const ingredientes = J.inv.map((s, i) => (s && ITEMS[s.id] && ITEMS[s.id].ingrediente ? i : -1)).filter((i) => i >= 0);
  texto(ctx, 'Ingredientes', x + 6, sy + 27, COL.suave);
  const iy = sy + 38;
  ingredientes.slice(0, 30).forEach((i, k) => {
    const cx = x + 6 + (k % N_BARRA) * 19, cy = iy + Math.floor(k / N_BARRA) * 19;
    ranura(ctx, cx, cy, J.inv[i], false);
    boton(cx, cy, 18, 18, () => { const id = J.inv[i].id; if (!d.a) d.a = id; else d.b = id; Sonido.sfx.menu(); }, { mudo: true });
  });
  if (!ingredientes.length) partirTexto('No llevas ingredientes: bayas, hongos, bulbos, trigo, carne o pescado.', w - 12).forEach((l, k) => texto(ctx, l, x + 6, iy + 4 + k * 7, COL.suave));
}

function panelMapa(ctx) {
  fondoPanel(ctx);
  ctx.fillStyle = '#08060c'; ctx.fillRect(0, 0, LW, LH);
  const mapa = lienzoMapa(), p = J.jug;
  const esc = Math.max(1, Math.floor(Math.min(LW, LH) / 120));
  const vw = Math.floor((LW - 8) / esc), vh = Math.floor((LH - 24) / esc);
  const cx = p.x / T, cy = p.y / T, sx = Math.round(cx - vw / 2), sy = Math.round(cy - vh / 2);
  const ox = Math.round((LW - vw * esc) / 2), oy = 18;
  ctx.drawImage(mapa, sx, sy, vw, vh, ox, oy, vw * esc, vh * esc);
  const marca = (tx, ty, f) => { const x = ox + (tx - sx) * esc, y = oy + (ty - sy) * esc; if (x > ox && y > oy && x < ox + vw * esc && y < oy + vh * esc) f(x, y); };
  marca(CENTRO, CENTRO, (x, y) => { ctx.fillStyle = '#ff6b8f'; circulo(ctx, x, y, 2 + (Math.floor(J.t * 2) % 2)); });
  for (const [id, g] of Object.entries(M.guaridas)) {
    if (J.jefesMuertos[id]) continue;
    const visto = M.visto[idx(g.x, g.y)] || (id === 'topo' && J.objetivo >= 6);
    if (visto) marca(g.x, g.y, (x, y) => sprCentro(ctx, 'ui_calavera', x, y));
  }
  if (J.cama) marca(J.cama.x, J.cama.y, (x, y) => { ctx.fillStyle = '#ff8090'; ctx.fillRect(x - 2, y - 1, 4, 3); });
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
  botonTexto(ctx, x + 10, y + 75, w - 20, 13, 'SALIR AL MENÚ', () => { guardarPartida(); irTitulo(); });
}

function panelMensaje(ctx, titulo, txt, boton1, fn, colorT = COL.oro) {
  fondoPanel(ctx, false);
  const w = Math.min(LW - 20, 210), lineasT = partirTexto(txt, w - 16), h = 44 + lineasT.length * 8, x = Math.round(LW / 2 - w / 2), y = Math.round(LH / 2 - h / 2);
  marco(ctx, x, y, w, h);
  texto(ctx, titulo, LW / 2, y + 7, colorT, { alin: 'c', esc: 2, sombra: COL.negro });
  lineasT.forEach((l, k) => texto(ctx, l, LW / 2, y + 24 + k * 8, COL.texto, { alin: 'c' }));
  botonTexto(ctx, Math.round(LW / 2 - 50), y + h - 18, 100, 13, boton1, fn);
}
function panelCorazon(ctx) {
  const n = Object.keys(J.estatuas).length;
  panelMensaje(ctx, 'EL CORAZÓN', `Latidos: ${n}/3. ` + palabrasCorazon().join(' '), 'SEGUIR', () => { J.panel = null; }, '#ff9ab8');
}

function dibujarPanel(ctx) {
  if (J.panel === 'mochila') panelMochila(ctx);
  else if (J.panel === 'crear') panelCrear(ctx);
  else if (J.panel === 'cofre') panelCofre(ctx);
  else if (J.panel === 'olla') panelOlla(ctx);
  else if (J.panel === 'mapa') panelMapa(ctx);
  else if (J.panel === 'pausa') panelPausa(ctx);
  else if (J.panel === 'corazon') panelCorazon(ctx);
  else if (J.panel === 'muerto') panelMensaje(ctx, 'TE DESMAYASTE', 'Te desmayas en la oscuridad. Lo que llevabas sigue contigo.', 'DESPERTAR', revivir, '#ff6b6b');
  else if (J.panel === 'fin') panelMensaje(ctx, 'EL CORAZÓN DESPIERTA', 'Los tres guardianes descansan y el Corazón late con fuerza. Habla con él: te dirá cómo abrir la Gran Muralla. Al otro lado espera la Selva Esmeralda.', 'SEGUIR', () => { J.panel = null; guardarPartida(); });
  else if (PANELES[J.panel]) PANELES[J.panel](ctx);
}

// ---------------------------------------------------------------- pantalla de título

// Pantalla para hacer tu Despierto antes de una partida nueva: piel, pelo, peinado y ropa, con el muñeco 3D
// horneado al momento de frente y de perfil.
function sprGrande(ctx, n, cx, pie, k) {
  const r = SPR[n];
  if (!r) return;
  ctx.drawImage(ATLAS, r.x, r.y, r.w, r.h, Math.round(cx - (r.w * k) / 2), Math.round(pie - r.h * k), r.w * k, r.h * k);
}
function dibujarAspecto(ctx) {
  const A = UI.aspecto;
  ctx.fillStyle = 'rgba(6,4,10,0.72)'; ctx.fillRect(0, 0, LW, LH);
  texto(ctx, 'TU DESPIERTO', LW / 2, 8, '#f2e4d0', { alin: 'c', esc: 2, borde: COL.negro });
  const intro = partirTexto('El último latido del Corazón hizo un cuerpo nuevo en el altar. Eres tú.', LW - 24);
  intro.forEach((l, k) => texto(ctx, l, LW / 2, 24 + k * 8, '#b8aab8', { alin: 'c', borde: COL.negro }));
  const k = LH >= 200 ? 3 : 2, pie = Math.round(LH * 0.66), anda = Math.floor(J.t * 6) % 6;
  sprGrande(ctx, 'pj_ab_q' + (Math.floor(J.t * 1.6) % 2), LW * 0.3, pie, k);
  sprGrande(ctx, 'pj_de_a' + anda, LW * 0.3 + 28 * k, pie, k);
  if (Personaje.ocupado) texto(ctx, '…', LW * 0.3 + 14 * k, pie + 4, COL.suave, { alin: 'c' });
  const filas = [['Piel', 'piel', ASPECTO.piel.length], ['Pelo', 'pelo', ASPECTO.pelo.length], ['Peinado', 'peinado', ASPECTO.peinado.length], ['Ropa', 'ropa', ASPECTO.ropa.length]];
  const x0 = Math.round(LW * 0.56), w = Math.min(130, LW - x0 - 8);
  let y = Math.round(LH * 0.3);
  for (const [nombre, clave, n] of filas) {
    const i = clave === 'peinado' ? ASPECTO.peinado.indexOf(A.peinado) : A[clave];
    const cambia = (d) => { const j = (i + d + n) % n; A[clave] = clave === 'peinado' ? ASPECTO.peinado[j] : j; Personaje.pedir(A, null); };
    botonTexto(ctx, x0, y, 16, 14, '<', () => cambia(-1));
    const valor = clave === 'peinado' ? NOMBRE_PEINADO[A.peinado] : `${nombre} ${i + 1}/${n}`;
    marco(ctx, x0 + 18, y, w - 36, 14, COL.ranura);
    if (clave !== 'peinado') { ctx.fillStyle = ASPECTO[clave][i]; ctx.fillRect(x0 + 21, y + 3, 8, 8); }
    texto(ctx, valor, x0 + 18 + (w - 36) / 2 + (clave !== 'peinado' ? 5 : 0), y + 5, COL.texto, { alin: 'c', sombra: COL.negro });
    botonTexto(ctx, x0 + w - 16, y, 16, 14, '>', () => cambia(1));
    y += 18;
  }
  botonTexto(ctx, x0, y + 4, w, 16, 'DESPERTAR', () => { UI.creando = false; J.aspectoNuevo = Object.assign({}, A); empezar(false); }, { color: '#5e1430' });
  botonTexto(ctx, 8, LH - 22, 54, 14, 'VOLVER', () => { UI.creando = false; });
}
// Pantalla de carga mientras se genera o se carga el mundo: negro, el Corazón que late y una barra fina.
function dibujarCarga(ctx) {
  ctx.fillStyle = '#000000'; ctx.fillRect(0, 0, LW, LH);
  const c = J.carga || { t: 0, txt: '' }, cx = Math.round(LW / 2), cy = Math.round(LH * 0.42), lat = (Math.sin(J.t * 5.2) + Math.sin(J.t * 10.4) * 0.5) * 0.5 + 0.5;
  const r = 5 + lat * 1.5;
  ctx.fillStyle = `rgba(255, 60, 120, ${0.08 + lat * 0.08})`; circulo(ctx, cx, cy, r * 3.2);
  ctx.fillStyle = '#8a1a3a'; circulo(ctx, cx - r * 0.45, cy - r * 0.2, r * 0.62); circulo(ctx, cx + r * 0.45, cy - r * 0.2, r * 0.62);
  ctx.beginPath(); ctx.moveTo(cx - r * 1.05, cy); ctx.lineTo(cx, cy + r * 1.15); ctx.lineTo(cx + r * 1.05, cy); ctx.fill();
  ctx.fillStyle = '#ff6a9a'; ctx.fillRect(Math.round(cx - r * 0.55), Math.round(cy - r * 0.45), 2, 2);
  texto(ctx, 'DESPERTANDO EL MUNDO', cx, cy + 18, '#c86a8a', { alin: 'c' });
  texto(ctx, (c.txt || '') + '…', cx, cy + 28, COL.suave, { alin: 'c' });
  const w = Math.min(160, LW - 40), x = Math.round(cx - w / 2), y = cy + 40;
  ctx.fillStyle = '#1a0e14'; ctx.fillRect(x, y, w, 3);
  ctx.fillStyle = '#c83a6a'; ctx.fillRect(x, y, Math.round(w * Math.min(1, c.t)), 3);
  ctx.fillStyle = '#ff9ab8'; ctx.fillRect(x + Math.round(w * Math.min(1, c.t)) - 1, y, 1, 3);
}
function dibujarTitulo(ctx, hayPartida) {
  if (UI.creando) { dibujarAspecto(ctx); return; }
  ctx.fillStyle = 'rgba(8,6,12,0.3)'; ctx.fillRect(0, 0, LW, LH);
  const esc = LW >= 300 ? 3 : 2, y0 = Math.round(LH * 0.16), sacude = Math.sin(J.t * 1.3) * 1.5;
  texto(ctx, 'CORAZÓN', LW / 2, y0 + sacude, '#ff7aa0', { alin: 'c', esc, borde: '#2a0a18' });
  texto(ctx, 'DORMIDO', LW / 2, y0 + 9 * esc + 4 + sacude, '#f6ecda', { alin: 'c', esc, borde: COL.negro });
  texto(ctx, 'Bajo la roca late el corazón de un dios muerto', LW / 2, y0 + 18 * esc + 10, COL.suave, { alin: 'c', borde: COL.negro });
  let y = Math.round(LH * 0.55);
  const w = 120, x = Math.round(LW / 2 - w / 2);
  if (hayPartida) { botonTexto(ctx, x, y, w, 16, 'CONTINUAR', () => empezar(true)); y += 21; }
  const confirmar = UI.confirmarNueva > J.t;
  botonTexto(ctx, x, y, w, 16, confirmar ? '¿SEGURO? TOCA OTRA VEZ' : 'NUEVA PARTIDA', () => {
    if (hayPartida && !confirmar) { UI.confirmarNueva = J.t + 3; return; }
    UI.creando = true; UI.aspecto = Object.assign({}, ASPECTO_INICIAL); Personaje.pedir(UI.aspecto, null);
  }, { color: confirmar ? '#5e1430' : undefined });
  y += 21;
  botonTexto(ctx, x, y, w, 13, 'SONIDO: ' + (Sonido.activo ? 'SÍ' : 'NO'), () => Sonido.alternar());
  if (tipoPartida === 'v4' || tipoPartida === 'v3') texto(ctx, tipoPartida === 'v4' ? 'Tu partida pasa al mundo grande: tu casa se queda' : 'Tu partida de la fase 1 pasa al mundo nuevo', LW / 2, Math.round(LH * 0.55) - 10, '#9fe0a8', { alin: 'c', borde: COL.negro });
  const pie = partirTexto('Izquierda: moverte · derecha: picar y pelear · USAR: abrir y hablar', LW - 16);
  pie.forEach((l, k) => texto(ctx, l, LW / 2, LH - 10 - (pie.length - 1 - k) * 8, COL.suave, { alin: 'c', borde: COL.negro }));
}
