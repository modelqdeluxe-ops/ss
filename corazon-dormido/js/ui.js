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
function abrirPanel(p) { if (J.estado !== 'juego') return; J.panel = J.panel === p ? null : p; UI.selInv = -1; UI.selCofre = -1; UI.scroll = 0; if (p === 'crear') { J.estacionAbierta = null; UI.selReceta = null; UI.cat = 'todo'; } }

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
  Sonido.iniciar(); Musica.tocar();
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
  Sonido.iniciar(); Musica.tocar();
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
  texto(ctx, txt, x + w / 2, y + Math.floor((h - 7) / 2), activo ? (o.colTexto || COL.texto) : COL.suave, { alin: 'c', sombra: COL.negro });
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
    if (s.n > 1) texto(ctx, s.n, x + 17, y + 12, COL.texto, { alin: 'd', borde: COL.negro, mini: true });
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
function infoObjeto(ctx, s, x, y, w, lineasMax = 3, sinNombre = false) {
  const it = asegurarItem(s.id);
  if (!sinNombre) texto(ctx, it.nombre, x, y, RAREZA[it.rareza || 0].color);
  const extra = [];
  if (it.poder) extra.push(`Poder de minado ${it.poder}`);
  if (it.dano && it.tipo !== 'accesorio' && it.tipo !== 'municion') extra.push(`Daño ${it.dano}`);
  if (it.def) extra.push(`Defensa ${it.def}`);
  if (it.tipo === 'mascota') extra.push(`Nivel ${nivelMascota(s.id)}`);
  if (J.panel === 'tienda' && valorVenta(s.id)) extra.push(`Vale ${valorVenta(s.id)} monedas`);
  const txt = (extra.length ? extra.join(' · ') + '. ' : '') + (it.desc || '');
  partirTexto(txt, w).slice(0, lineasMax).forEach((l, k) => texto(ctx, l, x, y + ALTO_LINEA + 1 + k * ALTO_LINEA, COL.suave));
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
  if (ob) partirTexto(ob.txt, 124).forEach((l, k) => texto(ctx, l, LW - 4, my + mm + 13 + k * ALTO_LINEA, '#ffe9a8', { alin: 'd', borde: COL.negro }));

  // jefe activo
  const j = Object.values(J.jefes).find((x) => x.activo && x.estado !== 'muerto');
  if (j) {
    const w = Math.min(140, LW - 150), x = Math.round(LW / 2 - w / 2), y = LW > 330 ? 6 : my + mm + 34;
    texto(ctx, (JEFES[j.id].nombre + (JEFES[j.id].titulo ? ', ' + JEFES[j.id].titulo : '')).toUpperCase(), LW / 2, y, '#ffd166', { alin: 'c', borde: COL.negro });
    barra(ctx, x, y + 8, w, 4, j.vida / j.vidaMax, j.fase === 2 ? '#ff4060' : '#c43b3b');
  }
  J.avisos.forEach((a, k) => {
    const y = (LW < 300 ? 122 : 40) + k * 13;
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
    const que = OBJ[o.tipo].interactua, txt = { crear: 'Crear', cofre: 'Abrir', olla: 'Cocinar', cama: 'Dormir', corazon: 'Hablar', estatua: o.extra ? 'Mirar' : 'Ofrecer', altar_titan: o.extra ? 'Mirar' : 'Ofrecer', altar_cielo: 'Ofrecer', tablilla: 'Leer', incubadora: 'Incubar', idolo: 'Mirar', brasero: o.extra ? 'Mirar' : 'Encender', palanca: 'Tirar', empujar: 'Empujar', sellada: 'Mirar', paso: J.pasos && J.pasos[o.datos && o.datos.nombre] ? 'Viajar' : 'Tocar', restos: o.datos && o.datos.visto ? 'Mirar' : 'Registrar', hablar: 'Hablar' }[que] || 'Usar';
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
  if (ps.estado === 'pica') { if (Math.floor(J.t * 8) % 2) texto(ctx, '¡Tira!', LW / 2, y, '#ffd166', { alin: 'c', esc: 2, borde: COL.negro }); return; }
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
  texto(ctx, '×', x + 4, y + 3, COL.texto);
  boton(x - 3, y - 3, 19, 19, () => { J.panel = null; UI.selInv = -1; });
}
// ancho: lo que hay de sitio (si no caben con 5 px a cada lado, se estrechan)
function pestanas(ctx, x, y, lista, ancho = 1e9) {
  let px = x;
  const textos = lista.reduce((a, [, t]) => a + anchoTexto(t), 0), pad = Math.max(2, Math.min(10, Math.floor((ancho - textos - 2 * (lista.length - 1)) / lista.length)));
  for (const [id, txt] of lista) {
    const w = anchoTexto(txt) + pad, activa = UI.pestana === id;
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

// La mochila (0.8): más ancha, con la rejilla a la izquierda y, a la derecha, lo que llevas puesto y la ficha de lo
// que tocas, con sus botones.
function panelMochila(ctx) {
  fondoPanel(ctx);
  const gw = N_BARRA * 19 - 1, w = Math.min(LW - 14, gw + 150), h = Math.min(LH - 8, 4 * 19 + 52), x = Math.round(LW / 2 - w / 2), y = Math.max(4, Math.round(LH / 2 - h / 2));
  marco(ctx, x, y, w, h); boton(x, y, w, h, () => { UI.selInv = -1; }, { mudo: true });
  pestanas(ctx, x + 5, y + 4, [['mochila', 'Mochila'], ['habilidades', 'Habilidades'], ['diario', 'Diario'], ['bestiario', 'Bestiario']], w - 28);
  cruz(ctx, x + w - 17, y + 3);
  if (UI.pestana === 'habilidades') { panelHabilidades(ctx, x, y, w, h); return; }
  if (UI.pestana === 'diario') { panelDiario(ctx, x, y, w, h); return; }
  if (UI.pestana === 'bestiario') { panelBestiario(ctx, x, y, w, h); return; }
  // equipo
  const ey = y + 21;
  const vacios = { mano2: 'i_antorcha', casco: 'i_casco_cobre', peto: 'i_peto_cobre', botas: 'i_botas_cobre', acc1: 'i_anillo_cobre', acc2: 'i_anillo_cobre', mascota: 'i_mascota_topito' };
  RANURAS_EQUIPO.forEach((r, k) => {
    const ex = x + 6 + k * 19;
    ranura(ctx, ex, ey, J.equipo[r], false, { fondo: '#2e2018', vacio: vacios[r] });
    boton(ex, ey, 18, 18, () => {
      if (UI.selInv >= 0) { ponerEquipo(UI.selInv, r); return; }
      if (J.equipo[r] && agregar(J.equipo[r].id, 1) === 0) { J.equipo[r] = null; Sonido.sfx.metal(); }
    }, { mudo: true });
  });
  const st = J.st || calcularStats();
  const sx = x + 6 + RANURAS_EQUIPO.length * 19 + 4;
  texto(ctx, `Vida ${st.vidaMax}  Def ${st.def}`, sx, ey, COL.texto);
  texto(ctx, `Daño ${Math.round(st.dano * 100)} %`, sx, ey + ALTO_LINEA, COL.suave);
  const gy = ey + 24;
  rejilla(ctx, J.inv, x + 6, gy, N_BARRA, UI.selInv, tocarRanura);
  // la ficha de lo que tocas
  const dx = x + 6 + gw + 6, dw = x + w - 6 - dx, dy = gy, dh = 4 * 19 - 1;
  marco(ctx, dx, dy, dw, dh, '#1e140e');
  const s = UI.selInv >= 0 ? J.inv[UI.selInv] : null;
  if (s) {
    const it = asegurarItem(s.id);
    marco(ctx, dx + 4, dy + 4, 22, 22, '#120c08'); sprCentro(ctx, it.icono, dx + 15, dy + 15);
    partirTexto(it.nombre, dw - 34).slice(0, 2).forEach((l, k) => texto(ctx, l, dx + 30, dy + 5 + k * ALTO_LINEA, RAREZA[it.rareza || 0].color));
    infoObjeto(ctx, s, dx + 4, dy + 30 - ALTO_LINEA - 1, dw - 8, Math.max(1, Math.floor((dh - 30 - 19) / ALTO_LINEA)), true);
    const ops = [];
    if (TIPO_RANURA[it.tipo] || it.tipo === 'accesorio' || it.tipo === 'mascota' || VA_EN_MANO2.has(s.id)) ops.push([VA_EN_MANO2.has(s.id) ? 'Otra mano' : 'Poner', () => ponerEquipo(UI.selInv)]);
    if (it.tipo === 'comida') ops.push(['Comer', () => { comer(J.inv[UI.selInv]); if (!J.inv[UI.selInv]) UI.selInv = -1; }]);
    ops.push(['Soltar', () => { const t = J.inv[UI.selInv]; if (t) { soltar(J.jug.x + J.jug.fx * 20, J.jug.y + J.jug.fy * 20, t.id, t.n); J.inv[UI.selInv] = null; UI.selInv = -1; } }]);
    const bw = Math.floor((dw - 8 - (ops.length - 1) * 2) / ops.length);
    ops.forEach(([txt, fn], k) => botonTexto(ctx, dx + 4 + k * (bw + 2), dy + dh - 17, bw, 13, anchoTexto(txt) > bw - 4 ? txt.split(' ').pop() : txt, fn, { color: txt === 'Soltar' ? COL.botonOff : COL.boton }));
  } else partirTexto('Toca algo para verlo. Toca otro hueco para moverlo; la armadura, tócala y luego su hueco.', dw - 8).slice(0, 7).forEach((l, k) => texto(ctx, l, dx + 4, dy + 4 + k * ALTO_LINEA, COL.suave));
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
  const mano2 = VA_EN_MANO2.has(s.id);
  const r = ranuraPedida && (TIPO_RANURA[ranuraPedida] === it.tipo || (ranuraPedida === 'mano2' && mano2)) ? ranuraPedida : mano2 ? 'mano2' : it.tipo === 'accesorio' ? (J.equipo.acc1 ? (J.equipo.acc2 ? 'acc1' : 'acc2') : 'acc1') : TIPO_RANURA[it.tipo] ? it.tipo : null;
  if (!r) { aviso('Eso no se pone'); return; }
  const antes = J.equipo[r];
  J.equipo[r] = { id: s.id, n: 1 };
  // de una pila (las antorchas) solo se pone una; lo que había en el hueco vuelve a la mochila
  if (s.n > 1) { s.n--; if (antes && agregar(antes.id, antes.n) > 0) soltar(J.jug.x, J.jug.y, antes.id, antes.n); } else J.inv[i] = antes;
  UI.selInv = -1; Sonido.sfx.metal(); calcularStats();
}
function panelHabilidades(ctx, x, y, w, h) {
  const ids = Object.keys(HABILIDADES), dos = ids.length > 8, cw = dos ? Math.floor((w - 16) / 2) : w - 12, filas = dos ? Math.ceil(ids.length / 2) : ids.length;
  if (!UI.selHab || !HABILIDADES[UI.selHab]) UI.selHab = ids[0];
  ids.forEach((id, k) => {
    const hb = HABILIDADES[id], n = nivel(id), xp = (J.hab[id] && J.hab[id].xp) || 0;
    const fx = x + 6 + (dos ? Math.floor(k / filas) * (cw + 4) : 0), fy = y + 21 + (k % filas) * 15, sel = UI.selHab === id;
    ctx.fillStyle = sel ? '#4a2f20' : '#24170f'; ctx.fillRect(fx, fy, cw, 14);
    texto(ctx, hb.nombre, fx + 3, fy + 3, sel ? COL.oro : COL.texto);
    texto(ctx, String(n), fx + cw - 3, fy + 3, COL.oro, { alin: 'd' });
    barra(ctx, fx + cw - 40, fy + 6, 22, 2, n >= NIVEL_MAX ? 1 : xp / xpNivel(n), '#6ab0ff', '#14243a');
    boton(fx, fy, cw, 14, () => { UI.selHab = id; Sonido.sfx.menu(); }, { mudo: true });
  });
  const hb = HABILIDADES[UI.selHab], dy = y + 21 + filas * 15 + 3;
  texto(ctx, `${hb.nombre}, nivel ${nivel(UI.selHab)}`, x + 6, dy, COL.oro);
  partirTexto(hb.desc, w - 12).slice(0, 2).forEach((l, k) => texto(ctx, l, x + 6, dy + ALTO_LINEA + k * ALTO_LINEA, COL.suave));
}

// ---------------------------------------------------------------- crear: una ventana propia para cada estación
// Cada estación abre su ventana (0.8): su nombre y su icono, el marco de su material (madera para las mesas, metal para
// los yunques, ascuas para el horno), pestañas por tipo de objeto, la rejilla de lo que se hace ahí y, al lado, la
// ficha de lo elegido con sus materiales y los botones de crear (uno o cinco). Sin estación (el botón de crear), lo
// básico y lo de las estaciones que tengas cerca.
const TEMAS_ESTACION = {
  madera: { fondo: '#2a1b14', borde: '#8a5a36', luz: '#c89058', sombra: '#5a3822', hondo: '#1a110c', titulo: '#f2c14e', boton: '#4a2f20' },
  metal: { fondo: '#1a1d24', borde: '#5c6474', luz: '#a8b2c4', sombra: '#2e3440', hondo: '#0f1116', titulo: '#d8e6f8', boton: '#2e3644' },
  horno: { fondo: '#221410', borde: '#7a3e22', luz: '#e8803c', sombra: '#3e1a0e', hondo: '#140a06', titulo: '#ffc060', boton: '#4a2216' },
  cocina: { fondo: '#261a12', borde: '#8a5236', luz: '#d0905a', sombra: '#4a2a18', hondo: '#160e08', titulo: '#ffcf7a', boton: '#5a3420' },
  incubar: { fondo: '#14201e', borde: '#3e7068', luz: '#8ad0c4', sombra: '#1e3a36', hondo: '#0a1210', titulo: '#b8ffe8', boton: '#244a44' },
};
const CATEGORIAS = [
  ['todo', 'Todo', () => true],
  ['armas', 'Armas', (t) => ['espada', 'maza', 'arco', 'municion'].includes(t)],
  ['armadura', 'Armadura', (t) => ['casco', 'peto', 'botas', 'accesorio'].includes(t)],
  ['utiles', 'Útiles', (t) => ['pico', 'azada', 'cana', 'regadera', 'bomba', 'regreso', 'especial', 'mascota', 'semilla', 'hacha'].includes(t)],
  ['casa', 'Casa', (t) => ['colocable', 'bloque', 'suelo'].includes(t)],
  ['otros', 'Otros', (t) => ['material', 'comida'].includes(t)],
];
function familiaEstacion(e) {
  const icono = (e && ITEMS[e] && ITEMS[e].icono) || 'ui_crear';
  if (!e) return { nombre: 'Crear', sub: 'A mano y con lo que tengas cerca', tema: TEMAS_ESTACION.madera, icono, vale: null };
  if (NIVEL_MESA[e]) return { nombre: NOMBRE_ESTACION[e], sub: 'Herramientas, muebles y útiles', tema: TEMAS_ESTACION.madera, acento: OBJ[e] && OBJ[e].mapa, icono, vale: (r) => !r.est || (NIVEL_MESA[r.est] && NIVEL_MESA[r.est] <= NIVEL_MESA[e]) };
  if (NIVEL_YUNQUE[e]) return { nombre: NOMBRE_ESTACION[e], sub: 'Armas y armaduras', verbo: 'Forjar', tema: TEMAS_ESTACION.metal, acento: OBJ[e] && OBJ[e].mapa, icono, vale: (r) => r.est && NIVEL_YUNQUE[r.est] && NIVEL_YUNQUE[r.est] <= NIVEL_YUNQUE[e] };
  if (e === 'horno') return { nombre: 'Horno', sub: 'Funde el mineral en lingotes', verbo: 'Fundir', tema: TEMAS_ESTACION.horno, icono, fuego: true, vale: (r) => r.est === 'horno' };
  return { nombre: NOMBRE_ESTACION[e] || 'Crear', sub: '', tema: TEMAS_ESTACION.madera, icono, vale: (r) => r.est === e };
}
function marcoTema(ctx, x, y, w, h, t, fondo) {
  ctx.fillStyle = COL.negro; ctx.fillRect(x - 1, y, w + 2, h); ctx.fillRect(x, y - 1, w, h + 2);
  ctx.fillStyle = t.borde; ctx.fillRect(x, y, w, h);
  ctx.fillStyle = t.luz; ctx.fillRect(x + 1, y, w - 2, 1); ctx.fillRect(x, y + 1, 1, h - 2);
  ctx.fillStyle = t.sombra; ctx.fillRect(x + 1, y + h - 1, w - 2, 1); ctx.fillRect(x + w - 1, y + 1, 1, h - 2);
  ctx.fillStyle = fondo || t.fondo; ctx.fillRect(x + 1, y + 1, w - 2, h - 2);
}
function botonTema(ctx, x, y, w, h, txt, fn, t, o = {}) {
  const activo = o.activo !== false;
  marcoTema(ctx, x, y, w, h, t, activo ? (o.color || t.boton) : t.hondo);
  texto(ctx, txt, x + w / 2, y + Math.floor((h - 7) / 2), activo ? (o.colTexto || COL.texto) : COL.suave, { alin: 'c', sombra: COL.negro });
  if (fn) boton(x, y, w, h, fn, o);
}
function crearVarias(r, n) {
  let k = 0;
  while (k < n && crear(r)) k++;
  if (k) { particulas(J.jug.x, J.jug.y - 10, 6 + k * 2, ['#f2c14e', '#ffffff'], { vel: 40, brilla: true }); if (k > 1) aviso(`Creaste ${k}`); }
  return k;
}
function panelCrear(ctx) {
  fondoPanel(ctx);
  const F = familiaEstacion(J.estacionAbierta), t = F.tema;
  const w = Math.min(LW - 14, 390), h = Math.min(LH - 10, 214), x = Math.round(LW / 2 - w / 2), y = Math.max(4, Math.round(LH / 2 - h / 2));
  marcoTema(ctx, x, y, w, h, t); boton(x, y, w, h, () => {}, { mudo: true });
  // cabecera: icono, nombre y lo que se hace aquí; el horno con su fuego
  marcoTema(ctx, x + 5, y + 4, 20, 20, t, t.hondo); sprCentro(ctx, F.icono, x + 15, y + 14);
  texto(ctx, F.nombre, x + 30, y + 5, t.titulo, { sombra: COL.negro });
  texto(ctx, F.sub, x + 30, y + 15, COL.suave);
  if (F.acento) { ctx.fillStyle = F.acento; ctx.fillRect(x + 1, y + 27, w - 2, 1); }
  if (F.fuego) for (let fx = x + 2; fx < x + w - 2; fx += 2) { const k = Math.sin(fx * 0.7 + J.t * 9) * 0.5 + Math.sin(fx * 0.31 - J.t * 5) * 0.5, hh = 2 + Math.round((k + 1) * 1.6); ctx.fillStyle = k > 0.3 ? '#ffd060' : k > -0.3 ? '#ff8a30' : '#c43a10'; ctx.fillRect(fx, y + 28 - hh, 2, hh); }
  cruz(ctx, x + w - 17, y + 4);
  // las recetas de esta estación (o, sin estación, lo que puedes hacer o casi)
  const ctxEst = estacionesCerca(), est = ctxEst.est;
  if (J.estacionAbierta) est.add(J.estacionAbierta);
  let todas = RECETAS.map((r) => ({ r, it: ITEMS[r.item], ok: puedeCrear(r, ctxEst), est: !r.est || est.has(r.est) }));
  todas = F.vale ? todas.filter((e) => F.vale(e.r)) : todas.filter((e) => e.est || e.ok || Object.keys(e.r.ing).some((id) => cuentaTotal(id, ctxEst) > 0));
  const cats = CATEGORIAS.filter(([id, , f]) => id === 'todo' || todas.some((e) => f(e.it.tipo)));
  if (!cats.some(([id]) => id === UI.cat)) UI.cat = 'todo';
  const filtro = cats.find(([id]) => id === UI.cat)[2];
  const lista = todas.filter((e) => filtro(e.it.tipo)).sort((a, b) => (b.ok - a.ok) || (b.est - a.est));
  let gy = y + 31;
  if (cats.length > 2) {
    let px = x + 6;
    for (const [id, nombre] of cats) {
      const tw = anchoTexto(nombre) + 8, act = UI.cat === id;
      botonTema(ctx, px, gy, tw, 12, nombre, () => { UI.cat = id; UI.scroll = 0; UI.selReceta = null; }, t, { color: act ? t.borde : t.hondo, colTexto: act ? t.titulo : COL.suave });
      px += tw + 2;
    }
    gy += 15;
  }
  // la rejilla
  const cols = Math.max(4, Math.min(8, Math.floor((w * 0.5 - 8) / 19))), gx = x + 6, gw = cols * 19 + 1, gh = y + h - 6 - gy;
  marcoTema(ctx, gx - 1, gy - 1, gw + 2, gh + 2, t, t.hondo);
  const filas = Math.ceil(lista.length / cols), alto = filas * 19;
  UI.scroll = Math.max(0, Math.min(UI.scroll, alto - gh));
  if (!lista.some((e) => e.r === UI.selReceta)) UI.selReceta = lista.length ? lista[0].r : null;
  ctx.save(); ctx.beginPath(); ctx.rect(gx, gy, gw, gh); ctx.clip();
  lista.forEach(({ r, it, ok, est: hayEst }, k) => {
    const cx = gx + 1 + (k % cols) * 19, cy = gy + 1 + Math.floor(k / cols) * 19 - Math.round(UI.scroll);
    if (cy + 19 < gy || cy > gy + gh) return;
    const sel = r === UI.selReceta;
    ranura(ctx, cx, cy, null, sel, { fondo: ok ? '#2a3a22' : t.hondo });
    ctx.globalAlpha = ok ? 1 : hayEst ? 0.55 : 0.3; sprCentro(ctx, it.icono, cx + 9, cy + 9); ctx.globalAlpha = 1;
    if (r.n > 1) texto(ctx, r.n, cx + 17, cy + 12, COL.texto, { alin: 'd', borde: COL.negro, mini: true });
    if (ok) { ctx.fillStyle = COL.oro; ctx.fillRect(cx + 1, cy + 1, 3, 1); ctx.fillRect(cx + 1, cy + 1, 1, 3); }
    boton(cx, Math.max(cy, gy), 18, Math.min(18, gy + gh - cy), () => { UI.selReceta = r; Sonido.sfx.menu(); }, { lista: true, mudo: true });
  });
  ctx.restore();
  if (alto > gh) { const k = gh / alto; ctx.fillStyle = t.borde; ctx.fillRect(gx + gw - 2, gy, 1, gh); ctx.fillStyle = t.luz; ctx.fillRect(gx + gw - 2, Math.round(gy + (UI.scroll / alto) * gh), 1, Math.max(4, Math.round(gh * k))); }
  if (!lista.length) partirTexto('Aquí todavía no puedes hacer nada.', gw - 8).forEach((l, k) => texto(ctx, l, gx + 4, gy + 4 + k * ALTO_LINEA, COL.suave));
  // la ficha de lo elegido
  const dx = gx + gw + 6, dw = x + w - 6 - dx;
  marcoTema(ctx, dx, gy - 1, dw, gh + 2, t, t.hondo);
  const sel = lista.find((e) => e.r === UI.selReceta);
  if (!sel) return;
  const { r, it, ok, est: hayEst } = sel;
  marcoTema(ctx, dx + 5, gy + 4, 38, 38, t, '#0c0808');
  const ri = SPR[it.icono];
  if (ri) { const k = Math.max(1, Math.floor(32 / Math.max(ri.w, ri.h))); ctx.drawImage(ATLAS, ri.x, ri.y, ri.w, ri.h, Math.round(dx + 24 - (ri.w * k) / 2), Math.round(gy + 23 - (ri.h * k) / 2), ri.w * k, ri.h * k); }
  let ty = gy + 6;
  partirTexto(it.nombre + (r.n > 1 ? ` ×${r.n}` : ''), dw - 54).slice(0, 2).forEach((l) => { texto(ctx, l, dx + 48, ty, RAREZA[it.rareza || 0].color); ty += ALTO_LINEA; });
  const datos = [];
  if (it.dano) datos.push(`Daño ${it.dano}`);
  if (it.def) datos.push(`Defensa ${it.def}`);
  if (it.poder) datos.push(`Minado ${it.poder}`);
  if (datos.length) { texto(ctx, datos.join(' · '), dx + 48, ty, '#9fd0ff'); ty += ALTO_LINEA; }
  ty = Math.max(ty, gy + 46);
  partirTexto(it.desc || '', dw - 10).slice(0, 3).forEach((l) => { texto(ctx, l, dx + 5, ty, COL.suave); ty += ALTO_LINEA; });
  ty += 3;
  texto(ctx, 'Necesitas', dx + 5, ty, t.titulo); ty += ALTO_LINEA + 1;
  for (const [id, n] of Object.entries(r.ing)) {
    const tiene = cuentaTotal(id, ctxEst), ii = ITEMS[id];
    sprCentro(ctx, ii.icono, dx + 12, ty + 3);
    texto(ctx, ii.nombre, dx + 22, ty, COL.texto);
    texto(ctx, `${tiene}/${n}`, dx + dw - 6, ty, tiene >= n ? COL.verde : '#ff8080', { alin: 'd' });
    ty += ALTO_LINEA + 2;
  }
  const by = gy + gh - 17;
  if (!hayEst) texto(ctx, 'Se hace en: ' + (NOMBRE_ESTACION[r.est] || r.est), dx + 5, by + 3, '#ffb070');
  else {
    const puede5 = ok && Object.entries(r.ing).every(([id, n]) => cuentaTotal(id, ctxEst) >= n * 5);
    botonTema(ctx, dx + 5, by, dw - 46, 15, F.verbo || 'Crear', () => { if (!crearVarias(r, 1)) aviso('Te faltan materiales'); }, t, { activo: ok, color: '#3e6a2a' });
    botonTema(ctx, dx + dw - 38, by, 33, 15, '×5', () => crearVarias(r, 5), t, { activo: puede5, color: '#3e6a2a' });
  }
}

function panelCofre(ctx) {
  const c = J.cofreAbierto;
  if (!c || !M.objetos.has(idx(c.x, c.y))) { J.panel = null; return; }
  fondoPanel(ctx);
  const w = N_BARRA * 19 - 1 + 12, h = 3 * 19 + 4 * 19 + 52, x = Math.round(LW / 2 - w / 2), y = Math.max(3, Math.round(LH / 2 - h / 2));
  marco(ctx, x, y, w, h); boton(x, y, w, h, () => {}, { mudo: true });
  texto(ctx, c.tipo === 'cofre_ruinas' ? 'Cofre antiguo' : 'Cofre', x + 6, y + 6, COL.oro, { sombra: COL.negro });
  cruz(ctx, x + w - 17, y + 3);
  const inv = c.datos.inv;
  rejilla(ctx, inv, x + 6, y + 17, N_BARRA, -1, (i) => pasarPila(inv, i, J.inv));
  const by = y + 17 + 3 * 19 + 3;
  botonTexto(ctx, x + 6, by, anchoTexto('Apilar') + 14, 12, 'Apilar', () => apilarEnCofre(inv));
  botonTexto(ctx, x + 6 + anchoTexto('Apilar') + 18, by, anchoTexto('Sacar todo') + 14, 12, 'Sacar todo', () => { for (let i = 0; i < inv.length; i++) if (inv[i]) pasarPila(inv, i, J.inv); });
  texto(ctx, 'Toca para pasar', x + w - 22, y + 6, COL.suave, { alin: 'd' });
  rejilla(ctx, J.inv, x + 6, by + 15, N_BARRA, -1, (i) => pasarPila(J.inv, i, inv));
}

// La olla (0.8): su ventana, como las de las estaciones. Arriba los dos ingredientes y el plato; abajo lo que llevas
// que se puede cocinar; a la derecha, el plato que sale y lo que hace.
function panelOlla(ctx) {
  const o = J.olla;
  if (!o || !M.objetos.has(idx(o.x, o.y))) { J.panel = null; return; }
  fondoPanel(ctx);
  const t = TEMAS_ESTACION.cocina, w = Math.min(LW - 14, 360), h = Math.min(LH - 10, 168), x = Math.round(LW / 2 - w / 2), y = Math.max(4, Math.round(LH / 2 - h / 2));
  marcoTema(ctx, x, y, w, h, t); boton(x, y, w, h, () => {}, { mudo: true });
  marcoTema(ctx, x + 5, y + 4, 20, 20, t, t.hondo); sprCentro(ctx, ITEMS.olla ? ITEMS.olla.icono : 'ui_crear', x + 15, y + 14);
  texto(ctx, 'Olla', x + 30, y + 5, t.titulo, { sombra: COL.negro });
  texto(ctx, 'Junta dos ingredientes y cocina', x + 30, y + 15, COL.suave);
  // vapor
  for (let k = 0; k < 3; k++) { const vy = y + 26 - ((J.t * 8 + k * 5) % 12); ctx.fillStyle = 'rgba(240,230,220,0.35)'; ctx.fillRect(x + 12 + k * 3 + Math.round(Math.sin(J.t * 3 + k) * 1.5), Math.round(vy), 1, 2); }
  cruz(ctx, x + w - 17, y + 4);
  const d = o.datos, lw = 7 * 19 + 1, lx = x + 6, sy = y + 32;
  ranura(ctx, lx, sy, d.a ? { id: d.a, n: 1 } : null, false); boton(lx, sy, 18, 18, () => { d.a = null; });
  texto(ctx, '+', lx + 23, sy + 5, COL.texto);
  ranura(ctx, lx + 30, sy, d.b ? { id: d.b, n: 1 } : null, false); boton(lx + 30, sy, 18, 18, () => { d.b = null; });
  texto(ctx, '=', lx + 53, sy + 5, COL.texto);
  const plato = d.a && d.b ? registrarPlato(...[d.a, d.b].sort()) : null;
  ranura(ctx, lx + 60, sy, plato ? { id: plato, n: 1 } : null, false, { fondo: plato ? '#2a3a22' : COL.ranura });
  texto(ctx, 'Ingredientes', lx, sy + 24, t.titulo);
  const iy = sy + 24 + ALTO_LINEA + 2, ingredientes = J.inv.map((q, i) => (q && ITEMS[q.id] && ITEMS[q.id].ingrediente ? i : -1)).filter((i) => i >= 0);
  marcoTema(ctx, lx - 1, iy - 1, lw + 2, y + h - 6 - iy + 2, t, t.hondo);
  ingredientes.slice(0, 28).forEach((i, k) => {
    const cx = lx + 1 + (k % 7) * 19, cy = iy + 1 + Math.floor(k / 7) * 19;
    ranura(ctx, cx, cy, J.inv[i], false);
    boton(cx, cy, 18, 18, () => { const id = J.inv[i].id; if (!d.a) d.a = id; else d.b = id; Sonido.sfx.menu(); }, { mudo: true });
  });
  if (!ingredientes.length) partirTexto('No llevas ingredientes: bayas, hongos, bulbos, trigo, carne o pescado.', lw - 8).forEach((l, k) => texto(ctx, l, lx + 3, iy + 3 + k * ALTO_LINEA, COL.suave));
  // el plato
  const dx = lx + lw + 6, dw = x + w - 6 - dx, dy = sy;
  marcoTema(ctx, dx, dy, dw, y + h - 6 - dy, t, t.hondo);
  if (plato) {
    const it = ITEMS[plato];
    marcoTema(ctx, dx + 4, dy + 4, 22, 22, t, '#0c0806'); sprCentro(ctx, it.icono, dx + 15, dy + 15);
    partirTexto(it.nombre, dw - 34).slice(0, 2).forEach((l, k) => texto(ctx, l, dx + 30, dy + 5 + k * ALTO_LINEA, RAREZA[it.rareza || 0].color));
    partirTexto(it.desc || '', dw - 8).slice(0, Math.floor((y + h - 6 - dy - 52) / ALTO_LINEA)).forEach((l, k) => texto(ctx, l, dx + 4, dy + 30 + k * ALTO_LINEA, COL.suave));
  } else partirTexto('Toca dos ingredientes de abajo. Puedes repetir el mismo.', dw - 8).forEach((l, k) => texto(ctx, l, dx + 4, dy + 4 + k * ALTO_LINEA, COL.suave));
  botonTema(ctx, dx + 4, y + h - 25, dw - 8, 15, 'Cocinar', () => cocinar(o), t, { activo: !!plato, color: '#3e6a2a' });
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
  texto(ctx, 'Mapa', 6, 6, COL.oro, { sombra: COL.negro });
  texto(ctx, 'Solo ves lo que has iluminado', 8 + anchoTexto('Mapa') + 8, 6, COL.suave);
  cruz(ctx, LW - 17, 3);
}

function panelPausa(ctx) {
  fondoPanel(ctx);
  const w = 120, h = APP ? 146 : 128, x = Math.round(LW / 2 - w / 2), y = Math.round(LH / 2 - h / 2);
  marco(ctx, x, y, w, h); boton(x, y, w, h, () => {}, { mudo: true });
  if (APP) botonTexto(ctx, x + 10, y + h - 19, w - 20, 13, 'Salir del juego', () => { guardarPartida(); APP.salir(); }, { color: COL.botonOff });
  texto(ctx, 'Pausa', LW / 2, y + 6, COL.oro, { alin: 'c', sombra: COL.negro });
  botonTexto(ctx, x + 10, y + 18, w - 20, 15, 'Seguir', () => { J.panel = null; });
  botonTexto(ctx, x + 10, y + 37, w - 20, 15, 'Sonido: ' + (Sonido.activo ? 'Sí' : 'No'), () => Sonido.alternar());
  botonTexto(ctx, x + 10, y + 56, w - 20, 15, 'Música: ' + (Musica.activa ? 'Sí' : 'No'), () => Musica.alternar());
  botonTexto(ctx, x + 10, y + 75, w - 20, 15, 'Guardar', () => { guardarPartida(); aviso('Partida guardada'); J.panel = null; });
  botonTexto(ctx, x + 10, y + 94, w - 20, 13, 'Salir al menú', () => { guardarPartida(); irTitulo(); });
  botonTexto(ctx, x + 10, y + h - 4 - (APP ? 34 : 15), w - 20, 13, 'Créditos', () => { J.panel = 'creditos'; UI.scroll = 0; }, { color: COL.botonOff });
}

// Créditos (la música es CC BY 4.0: hay que citar a su autor; lo demás es CC0 y se cita igual). Ver CREDITOS.md.
const CREDITOS_JUEGO = [
  ['Música', 'Kevin MacLeod (incompetech.com). Licencia CC BY 4.0 (creativecommons.org/licenses/by/4.0). «Heart of Nowhere», «Immersed», «Ascending the Vale», «Mirage», «Gregorian Chant», «Oppressive Gloom», «Darkling» y «Five Armies».'],
  ['Efectos de sonido', 'De OpenGameArt, CC0: artisticdude (RPG Sound Pack), rubberduck (criaturas, golpes y roturas), qubodup (pasos de Fantozzi), bart (latido) y peludo (agua).'],
  ['Movimiento', 'Las animaciones de los huesos (los dibujos son nuestros), CC0: KayKit de Kay Lousberg (kaylousberg.com) y Quaternius (quaternius.com).'],
  ['Letra', 'Pixel Operator, de Jayvee Enaguas (CC0).'],
  ['Corazón Dormido', 'Juego, mundo, arte, criaturas e historia: hechos para este juego.'],
];
function panelCreditos(ctx, alCerrar) {
  fondoPanel(ctx, false);
  const w = Math.min(LW - 16, 330), h = Math.min(LH - 10, 200), x = Math.round(LW / 2 - w / 2), y = Math.round(LH / 2 - h / 2);
  marco(ctx, x, y, w, h); boton(x, y, w, h, () => {}, { mudo: true });
  texto(ctx, 'Créditos', x + 8, y + 6, COL.oro, { sombra: COL.negro });
  cruz(ctx, x + w - 17, y + 3); UI.botones[UI.botones.length - 1].fn = alCerrar;
  const ly = y + 20, lh = h - 26, lineas = [];
  for (const [tit, txt] of CREDITOS_JUEGO) { lineas.push([tit, COL.oro]); for (const l of partirTexto(txt, w - 18)) lineas.push([l, COL.texto]); lineas.push(['', null]); }
  const alto = lineas.length * ALTO_LINEA;
  UI.scroll = Math.max(0, Math.min(UI.scroll, alto - lh));
  ctx.save(); ctx.beginPath(); ctx.rect(x + 2, ly, w - 4, lh); ctx.clip();
  lineas.forEach(([l, c], k) => { if (c) texto(ctx, l, x + 9, ly + 2 + k * ALTO_LINEA - Math.round(UI.scroll), c); });
  ctx.restore();
  boton(x + 2, ly, w - 4, lh, () => {}, { lista: true, mudo: true });
}

function panelMensaje(ctx, titulo, txt, boton1, fn, colorT = COL.oro) {
  fondoPanel(ctx, false);
  const w = Math.min(LW - 20, 300), lineasT = partirTexto(txt, w - 16).slice(0, Math.floor((LH - 70) / ALTO_LINEA)), h = 50 + lineasT.length * ALTO_LINEA, x = Math.round(LW / 2 - w / 2), y = Math.round(LH / 2 - h / 2);
  marco(ctx, x, y, w, h);
  texto(ctx, titulo, LW / 2, y + 7, colorT, { alin: 'c', esc: 2, sombra: COL.negro });
  lineasT.forEach((l, k) => texto(ctx, l, LW / 2, y + 26 + k * ALTO_LINEA, COL.texto, { alin: 'c' }));
  botonTexto(ctx, Math.round(LW / 2 - 50), y + h - 19, 100, 14, boton1, fn);
}
function panelCorazon(ctx) {
  const n = Object.keys(J.estatuas).length;
  panelMensaje(ctx, 'El Corazón', `Latidos: ${n}/3. ` + palabrasCorazon().join(' '), 'Seguir', () => { J.panel = null; }, '#ff9ab8');
}

function dibujarPanel(ctx) {
  if (J.panel === 'mochila') panelMochila(ctx);
  else if (J.panel === 'crear') panelCrear(ctx);
  else if (J.panel === 'cofre') panelCofre(ctx);
  else if (J.panel === 'olla') panelOlla(ctx);
  else if (J.panel === 'mapa') panelMapa(ctx);
  else if (J.panel === 'pausa') panelPausa(ctx);
  else if (J.panel === 'corazon') panelCorazon(ctx);
  else if (J.panel === 'creditos') panelCreditos(ctx, () => { J.panel = 'pausa'; });
  else if (J.panel === 'muerto') panelMensaje(ctx, 'Te desmayaste', 'Te desmayas en la oscuridad. Lo que llevabas sigue contigo.', 'Despertar', revivir, '#ff6b6b');
  else if (J.panel === 'fin') panelMensaje(ctx, 'El Corazón despierta', 'Los tres guardianes descansan y el Corazón late con fuerza. Habla con él: te dirá cómo abrir la Gran Muralla. Al otro lado espera la Selva Esmeralda.', 'Seguir', () => { J.panel = null; guardarPartida(); });
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
  texto(ctx, 'Tu Despierto', LW / 2, 8, '#f2e4d0', { alin: 'c', esc: 2, borde: COL.negro });
  const intro = partirTexto('El último latido del Corazón hizo un cuerpo nuevo en el altar. Eres tú.', LW - 24);
  intro.forEach((l, k) => texto(ctx, l, LW / 2, 24 + k * ALTO_LINEA, '#b8aab8', { alin: 'c', borde: COL.negro }));
  const k = LH >= 200 ? 3 : 2, pie = Math.round(LH * 0.66);
  // de frente quieto y de perfil andando (esqueleto.js); cada cuadro está recortado: se coloca por los pies
  const nq = (PJ_ESQ.ANIMS.q || [, 2])[1], na = (PJ_ESQ.ANIMS.a || [, 6])[1];
  const q = 'pj_ab_q' + (Math.floor((J.t / 1.6) * nq) % nq), a = 'pj_de_a' + (Math.floor(J.t * 10) % na);
  const dxDe = (n) => (META_SPR[n] ? META_SPR[n].dx * 16 * k : 0);
  sprGrande(ctx, q, LW * 0.3 + dxDe(q), pie, k);
  sprGrande(ctx, a, LW * 0.3 + 28 * k + dxDe(a), pie, k);
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
  botonTexto(ctx, x0, y + 4, w, 16, 'Despertar', () => { UI.creando = false; J.aspectoNuevo = Object.assign({}, A); empezar(false); }, { color: '#5e1430' });
  botonTexto(ctx, 8, LH - 22, 54, 14, 'Volver', () => { UI.creando = false; });
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
  texto(ctx, 'Despertando el mundo', cx, cy + 18, '#c86a8a', { alin: 'c' });
  texto(ctx, (c.txt || '') + '…', cx, cy + 28, COL.suave, { alin: 'c' });
  if (!c.consejo) c.consejo = CONSEJOS[Math.floor(Math.random() * CONSEJOS.length)];
  partirTexto(c.consejo, Math.min(LW - 30, 260)).forEach((l, k) => texto(ctx, l, cx, LH - 30 + k * ALTO_LINEA, '#8a7a8a', { alin: 'c' }));
  const w = Math.min(160, LW - 40), x = Math.round(cx - w / 2), y = cy + 40;
  ctx.fillStyle = '#1a0e14'; ctx.fillRect(x, y, w, 3);
  ctx.fillStyle = '#c83a6a'; ctx.fillRect(x, y, Math.round(w * Math.min(1, c.t)), 3);
  ctx.fillStyle = '#ff9ab8'; ctx.fillRect(x + Math.round(w * Math.min(1, c.t)) - 1, y, 1, 3);
}
function dibujarTitulo(ctx, hayPartida) {
  if (UI.creando) { dibujarAspecto(ctx); return; }
  // Composición: el santuario con el Corazón a la derecha (la cámara se corre) y, a la izquierda, una sombra en
  // franjas de 3 píxeles (sin degradado liso: es pixel art) con el título y el menú encima.
  const col = Math.round(LW * 0.6), cxL = Math.round(LW * 0.27);
  for (let x = 0; x < col; x += 3) { const k = 1 - x / col; ctx.fillStyle = `rgba(6,3,9,${(0.9 * Math.pow(k, 1.25)).toFixed(3)})`; ctx.fillRect(x, 0, 3, LH); }
  ctx.fillStyle = 'rgba(6,3,9,0.35)'; ctx.fillRect(0, 0, LW, 6); ctx.fillRect(0, LH - 6, LW, 6);
  const esc = LW >= 300 ? 3 : 2, y0 = Math.round(LH * 0.13);
  // el latido: dos golpes y una pausa, como el de la pantalla de carga
  const lat = Math.max(0, Math.sin(J.t * 5.2)) ** 6 + Math.max(0, Math.sin(J.t * 5.2 - 0.9)) ** 6 * 0.6;
  const g = ctx.createRadialGradient(cxL, y0 + 5 * esc, 2, cxL, y0 + 5 * esc, 40 + lat * 8);
  g.addColorStop(0, `rgba(255,60,140,${0.22 + lat * 0.18})`); g.addColorStop(1, 'rgba(255,60,140,0)');
  ctx.fillStyle = g; ctx.fillRect(cxL - 60, y0 - 40, 120, 90);
  texto(ctx, 'CORAZÓN', cxL, y0 + esc, '#3a0618', { alin: 'c', esc });
  texto(ctx, 'CORAZÓN', cxL, y0, lat > 0.5 ? '#ffa0c0' : '#ff6a98', { alin: 'c', esc, borde: '#1a0410' });
  texto(ctx, 'DORMIDO', cxL, y0 + 9 * esc + 3 + esc, '#2a2018', { alin: 'c', esc });
  texto(ctx, 'DORMIDO', cxL, y0 + 9 * esc + 3, '#f2e6d0', { alin: 'c', esc, borde: COL.negro });
  // filete con un rombo en medio
  const yf = y0 + 18 * esc + 8, an = 46;
  ctx.fillStyle = '#5a2a3a'; ctx.fillRect(cxL - an, yf, an * 2 + 1, 1);
  ctx.fillStyle = '#8a4a5e'; ctx.fillRect(cxL - an + 8, yf, an * 2 - 15, 1);
  ctx.fillStyle = '#ff7aa0'; ctx.fillRect(cxL - 1, yf - 1, 3, 3); ctx.fillStyle = '#ffd0e0'; ctx.fillRect(cxL, yf - 2, 1, 5); ctx.fillRect(cxL - 2, yf, 5, 1);
  partirTexto('Bajo la roca late el corazón de un dios muerto', col - 24).forEach((l, k) => texto(ctx, l, cxL, yf + 7 + k * ALTO_LINEA, '#c9a8b4', { alin: 'c', borde: COL.negro }));
  let y = Math.round(LH * 0.56);
  const w = 120, x = Math.round(cxL - w / 2);
  if (hayPartida) { botonTexto(ctx, x, y, w, 16, 'Continuar', () => empezar(true)); y += 21; }
  const confirmar = UI.confirmarNueva > J.t;
  botonTexto(ctx, x, y, w, 16, confirmar ? '¿Seguro? Toca otra vez' : 'Nueva partida', () => {
    if (hayPartida && !confirmar) { UI.confirmarNueva = J.t + 3; return; }
    UI.creando = true; UI.aspecto = Object.assign({}, ASPECTO_INICIAL); Personaje.pedir(UI.aspecto, null);
  }, { color: confirmar ? '#5e1430' : undefined });
  y += 21;
  const w2 = (w - 3) >> 1;
  botonTexto(ctx, x, y, w2, 13, 'Sonido: ' + (Sonido.activo ? 'Sí' : 'No'), () => Sonido.alternar());
  botonTexto(ctx, x + w - w2, y, w2, 13, 'Música: ' + (Musica.activa ? 'Sí' : 'No'), () => Musica.alternar());
  y += 18; botonTexto(ctx, x, y, w, 13, 'Créditos', () => { UI.creditos = true; UI.scroll = 0; }, { color: COL.botonOff });
  if (APP) { y += 18; botonTexto(ctx, x, y, w, 13, 'Salir', () => APP.salir(), { color: COL.botonOff }); }
  if (tipoPartida === 'v4' || tipoPartida === 'v3') texto(ctx, tipoPartida === 'v4' ? 'Tu partida pasa al mundo grande: tu casa se queda' : 'Tu partida de la fase 1 pasa al mundo nuevo', cxL, Math.round(LH * 0.56) - 10, '#9fe0a8', { alin: 'c', borde: COL.negro });
  const pie = partirTexto('Izquierda: moverte · derecha: picar y pelear · Usar: abrir y hablar', cxL * 2 - 12);
  pie.forEach((l, k) => texto(ctx, l, cxL, LH - 11 - (pie.length - 1 - k) * ALTO_LINEA, '#8a7a80', { alin: 'c', borde: COL.negro }));
  if (typeof VERSION_JUEGO !== 'undefined') texto(ctx, 'v' + VERSION_JUEGO, LW - 4, LH - 10, '#6a5a60', { alin: 'd', borde: COL.negro });
}
