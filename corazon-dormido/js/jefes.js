'use strict';
// Los tres guardianes del anillo interior, sus estatuas junto al Corazón y el despertar del Corazón.
// Cada jefe duerme en su guarida, despierta cuando entras, tiene una ronda de ataques con aviso, se enfurece
// a media vida y vuelve a su sitio (curado) si huyes lejos.

function crearJefes() {
  J.jefes = {};
  for (const [id, g] of Object.entries(M.guaridas)) {
    const d = JEFES[id];
    const base = { id, jefe: true, x: g.x * T + 8, y: g.y * T + 12, hw: 9, hh: 5, radio: d.radio, alto: 14, vida: d.vida, vidaMax: d.vida, estado: 'dormido', t: 0, flash: 0, def: 0, fase: 1, activo: false, ultimo: '', rafagas: 0, colores: ['#7a5238', '#a07450', '#ff8fa8'] };
    if (id === 'reina') Object.assign(base, { hw: 14, hh: 8, alto: 20, colores: ['#efe2bf', '#c9a878', '#7ac83a'], x: g.x * T + 8, y: g.y * T + 8 });
    if (id === 'ozmar') Object.assign(base, { hw: 6, hh: 4, alto: 16, colores: ['#5a2a7a', '#c080ff', '#2a1a3a'], x: g.x * T + 8, y: (g.y0 + 3) * T + 8 });
    if (d.hw) Object.assign(base, { hw: d.hw, hh: d.hh, alto: d.alto, x: g.x * T + 8, y: g.y * T + 8 });
    if (CREAR_JEFE[id]) CREAR_JEFE[id](base);
    if (J.jefesMuertos[id]) base.estado = 'muerto';
    J.jefes[id] = base;
  }
}
function reiniciarJefe(j) {
  const g = M.guaridas[j.id];
  j.vida = j.vidaMax; j.estado = 'dormido'; j.activo = false; j.enterrado = false; j.invisible = false; j.fase = 1;
  if (j.id === 'ozmar') { j.x = g.x * T + 8; j.y = (g.y0 + 3) * T + 8; }
  else if (j.id === 'reina') { j.x = g.x * T + 8; j.y = g.y * T + 8; }
  else if (JEFES[j.id].hw) { j.x = g.x * T + 8; j.y = g.y * T + 8; }
  else { j.x = g.x * T + 8; j.y = g.y * T + 12; }
  if (CREAR_JEFE[j.id]) CREAR_JEFE[j.id](j);
  J.proyectiles = []; J.avisosSuelo = [];
}
// Lo propio de cada guardián: al crearlo (o reiniciarlo), cada cuadro y al dibujarlo. titanes.js añade los suyos.
const CREAR_JEFE = {}, ACTUALIZAR_JEFE = {}, DIBUJAR_JEFE = {};
function lanzarRoca(x, y, a, vel, dano = 12) { lanzar('roca', x, y, a, vel, dano); }

function actualizarJefes(dt) {
  for (const j of Object.values(J.jefes)) {
    if (j.estado === 'muerto') continue;
    j.t -= dt; j.flash -= dt;
    if (j.veneno) { j.veneno.t -= dt; j.venT = (j.venT || 0) - dt; if (j.venT <= 0) { j.venT = 0.5; j.vida -= j.veneno.dano; numero(j.x, j.y, j.veneno.dano, '#9aff6a', 40); } if (j.veneno.t <= 0) j.veneno = null; }
    if (j.estado === 'muriendo') { morirJefe(j, dt); continue; }
    const g = M.guaridas[j.id], p = J.jug, dJug = Math.hypot(p.x / T - g.x, p.y / T - g.y), dj = JEFES[j.id];
    if (j.estado === 'dormido') {
      if (dj.invocado && !j.invocar) continue;
      const radioDespierta = dj.despierta || (j.id === 'ozmar' ? 11 : j.id === 'reina' ? 9 : 7);
      if (j.id === 'ozmar' && dJug < 16) J.hito.vioOzmar = true;
      if (dJug < radioDespierta && J.estado === 'juego') {
        j.estado = 'despierta'; j.t = 1.6; j.activo = true; J.temblor = 1; Sonido.sfx.rugido();
        const dj2 = JEFES[j.id];
        J.presentacion = { nombre: dj2.nombre, titulo: dj2.titulo || '', frase: dj2.frase || '', t: 5 }; J.cartel = null;
      }
      continue;
    }
    if (dJug > (dj.correa || (j.id === 'ozmar' ? 22 : 18))) { reiniciarJefe(j); aviso(dj.invocado ? 'Tamar vuelve al cielo' : 'El guardián vuelve a su guarida'); continue; }
    if (j.vida < j.vidaMax / 2 && j.fase === 1) { j.fase = 2; Sonido.sfx.rugido(); J.temblor = 0.6; aviso('¡Se enfurece!', null, '#ff6b6b'); }
    if (j.id === 'topo') actualizarTopo(j, dt);
    else if (j.id === 'reina') actualizarReina(j, dt);
    else if (j.id === 'ozmar') actualizarOzmar(j, dt);
    else if (ACTUALIZAR_JEFE[j.id]) ACTUALIZAR_JEFE[j.id](j, dt);
  }
}
function morirJefe(j) {
  J.temblor = Math.max(J.temblor, 0.3);
  if (Math.random() < 0.4) particulas(j.x + (Math.random() - 0.5) * 24, j.y - Math.random() * 24, 4, j.colores, { vel: 80 });
  if (j.t > 0) return;
  j.estado = 'muerto'; j.activo = false; J.jefesMuertos[j.id] = true;
  particulas(j.x, j.y - 10, 60, j.colores.concat(['#f6c84c', '#ffffff']), { vel: 120, vida: 1.1, s: 2 });
  efecto('humo', 4, j.x, j.y, 0, 0.6);
  const d = JEFES[j.id];
  soltar(j.x, j.y, d.cristal, 1);
  soltarLista(j.x, j.y, d.botin);
  Sonido.sfx.victoria(); aviso(`¡Derrotaste a ${d.nombre}!`, null, '#ffd166');
  J.enemigos = J.enemigos.filter((e) => Math.hypot(e.x - j.x, e.y - j.y) > 200);
  J.proyectiles = []; J.avisosSuelo = [];
}
// Tocar al guardián duele (menos que sus ataques con aviso: lo que más debe castigar es no esquivar).
function contacto(j, dano) {
  const p = J.jug;
  if (!j.enterrado && !j.invisible && j.estado !== 'aturdido' && j.estado !== 'despierta' && Math.abs(p.x - j.x) < j.hw + p.hw + 2 && Math.abs(p.y - j.y) < j.hh + 5) danarJugador(dano * 0.7, j);
}

// ---------------------------------------------------------------- Topo Rey: rocas, se entierra, embiste

function actualizarTopo(j, dt) {
  const p = J.jug, hacia = Math.atan2(p.y - j.y, p.x - j.x), rapido = j.fase === 2 ? 1.3 : 1, D = JEFES.topo;
  const elegir = () => { const ops = ['rocas', 'excava', 'embiste'].filter((x) => x !== j.ultimo); j.estado = ops[Math.floor(Math.random() * ops.length)]; j.ultimo = j.estado; j.t = 0.6; j.rafagas = j.fase === 2 ? 3 : 2; };
  switch (j.estado) {
    case 'despierta': if (j.t <= 0) { j.estado = 'persigue'; j.t = 2; } break;
    case 'persigue':
      mover(j, Math.cos(hacia) * 32 * rapido * dt, Math.sin(hacia) * 32 * rapido * dt);
      if (j.t <= 0) elegir();
      if (j.fase === 2 && Math.random() < dt * 0.25 && J.enemigos.length < 10) { const e = nuevaCriatura('babosa', j.x + (Math.random() - 0.5) * 30, j.y + 10); if (!choca(e.x, e.y, e.hw, e.hh)) J.enemigos.push(e); }
      break;
    case 'rocas':
      if (j.t <= 0) {
        const n = j.fase === 2 ? 7 : 5;
        for (let k = 0; k < n; k++) lanzarRoca(j.x, j.y - 12, hacia + (k - (n - 1) / 2) * 0.22, 95);
        Sonido.sfx.excavar(); j.rafagas--; j.t = 0.55;
        if (j.rafagas <= 0) { j.estado = 'persigue'; j.t = 1.8 / rapido; }
      }
      break;
    case 'excava':
      if (!j.enterrado) {
        if (Math.random() < 0.5) particulas(j.x, j.y, 3, RAMPA.tierra.slice(1), { vel: 40 });
        if (j.t <= 0) { j.enterrado = true; j.t = 2.2; Sonido.sfx.excavar(); }
      } else if (j.t > 0.8) {
        mover(j, Math.cos(hacia) * 80 * rapido * dt, Math.sin(hacia) * 80 * rapido * dt);
        if (Math.random() < 0.6) particulas(j.x, j.y, 2, RAMPA.tierra.slice(1), { vel: 30, vida: 0.4 });
      } else if (j.t > 0) J.temblor = Math.max(J.temblor, 0.12);
      else {
        j.enterrado = false; j.estado = 'persigue'; j.t = 1.6 / rapido;
        Sonido.sfx.temblor(); J.temblor = 0.5;
        particulas(j.x, j.y, 26, RAMPA.tierra.slice(1), { vel: 100, s: 2, vida: 0.7 });
        efecto('humo', 4, j.x, j.y, 0, 0.5);
        if (Math.hypot(p.x - j.x, p.y - j.y) < 32) danarJugador(22, j);
        for (let k = 0; k < 8; k++) lanzarRoca(j.x, j.y - 6, (k / 8) * Math.PI * 2, 80, 10);
      }
      break;
    case 'embiste':
      if (j.t > 0) { j.cx = Math.cos(hacia); j.cy = Math.sin(hacia); }
      else { j.estado = 'carga'; j.t = 0.85; Sonido.sfx.rugido(); }
      break;
    case 'carga':
      if (mover(j, j.cx * 175 * rapido * dt, j.cy * 175 * rapido * dt)) {
        j.estado = 'aturdido'; j.t = 1.7; J.temblor = 0.5; Sonido.sfx.temblor();
        particulas(j.x + j.cx * 10, j.y, 16, RAMPA.tierra.slice(1), { vel: 80, s: 2 });
      } else if (j.t <= 0) { j.estado = 'persigue'; j.t = 1.4; }
      break;
    case 'aturdido':
      if (Math.random() < 0.15) particulas(j.x, j.y - 30, 1, ['#ffd166', '#ffffff'], { vel: 25, g: 0, brilla: true });
      if (j.t <= 0) { j.estado = 'persigue'; j.t = 1.5; }
      break;
  }
  contacto(j, j.estado === 'carga' ? 24 : D.dano);
}

// ---------------------------------------------------------------- Reina Larva: ácido, puesta y derrumbes

function actualizarReina(j, dt) {
  const p = J.jug, rapido = j.fase === 2 ? 1.35 : 1;
  const elegir = () => {
    const larvas = J.enemigos.filter((e) => e.tipo === 'larva' || e.tipo === 'larva_grande').length;
    const ops = ['acido', 'derrumbe', larvas < 7 ? 'puesta' : 'acido'].filter((x) => x !== j.ultimo);
    j.estado = ops[Math.floor(Math.random() * ops.length)]; j.ultimo = j.estado; j.t = 0.7 / rapido; j.rafagas = j.fase === 2 ? 4 : 3;
  };
  switch (j.estado) {
    case 'despierta': if (j.t <= 0) { j.estado = 'espera'; j.t = 1.2; } break;
    case 'espera': if (j.t <= 0) elegir(); break;
    case 'acido':
      if (j.t <= 0) {
        for (let k = -1; k <= 1; k++) { const a = Math.atan2(p.y - j.y, p.x - j.x) + k * 0.35, d = Math.hypot(p.x - j.x, p.y - j.y); escupir(j.x, j.y - 14, j.x + Math.cos(a) * d + (Math.random() - 0.5) * 12, j.y + Math.sin(a) * d + (Math.random() - 0.5) * 12, JEFES.reina.dano); }
        Sonido.sfx.zas(); j.rafagas--; j.t = 0.75 / rapido;
        if (j.rafagas <= 0) { j.estado = 'espera'; j.t = 1.4 / rapido; }
      }
      break;
    case 'puesta':
      if (j.t <= 0) {
        const n = j.fase === 2 ? 4 : 3;
        for (let k = 0; k < n; k++) {
          const a = Math.random() * Math.PI * 2, e = nuevaCriatura(k === 0 && j.fase === 2 ? 'larva_grande' : 'larva', j.x + Math.cos(a) * 34, j.y + Math.sin(a) * 26);
          if (!choca(e.x, e.y, e.hw, e.hh)) { J.enemigos.push(e); efecto('humo', 4, e.x, e.y, 0, 0.4); }
        }
        Sonido.sfx.excavar(); j.estado = 'espera'; j.t = 1.8 / rapido;
      }
      break;
    case 'derrumbe':
      if (j.t <= 0) {
        for (let k = 0; k < (j.fase === 2 ? 8 : 6); k++) {
          const x = p.x + (Math.random() - 0.5) * 90, y = p.y + (Math.random() - 0.5) * 70;
          if (!solido(Math.floor(x / T), Math.floor(y / T))) avisoSuelo(x, y, 15, 1.0 + k * 0.08, 18);
        }
        avisoSuelo(p.x, p.y, 15, 1.1, 18);
        Sonido.sfx.temblor(); J.temblor = 0.4; j.estado = 'espera'; j.t = 2.2 / rapido;
      }
      break;
  }
  contacto(j, JEFES.reina.dano);
}

// ---------------------------------------------------------------- Ozmar el Corrompido: orbes, anillos, invoca y se teletransporta

function actualizarOzmar(j, dt) {
  const p = J.jug, rapido = j.fase === 2 ? 1.3 : 1, D = JEFES.ozmar, g = M.guaridas.ozmar;
  const elegir = () => {
    const sirvientes = J.enemigos.filter((e) => e.tipo.startsWith('gruto') && Math.hypot(e.x - j.x, e.y - j.y) < 250).length;
    const ops = ['orbes', 'anillo', 'tele'];
    if (sirvientes < 3) ops.push('invoca');
    if (j.fase === 2) ops.push('pilares', 'pilares');
    const sig = ops.filter((x) => x !== j.ultimo);
    j.estado = sig[Math.floor(Math.random() * sig.length)]; j.ultimo = j.estado; j.t = 0.6 / rapido; j.rafagas = j.fase === 2 ? 3 : 2;
  };
  // flota hacia un punto cómodo: a media distancia del jugador, dentro de la sala
  const flotar = () => {
    const a = Math.atan2(j.y - p.y, j.x - p.x), dx = p.x + Math.cos(a) * 50 - j.x, dy = p.y + Math.sin(a) * 38 - j.y, d = Math.hypot(dx, dy) || 1;
    mover(j, (dx / d) * 30 * dt, (dy / d) * 30 * dt);
    j.x = Math.max((g.x0 + 1) * T, Math.min((g.x0 + g.w - 1) * T, j.x)); j.y = Math.max((g.y0 + 2) * T, Math.min((g.y0 + g.h - 1) * T, j.y));
  };
  switch (j.estado) {
    case 'despierta': if (j.t <= 0) { j.estado = 'flota'; j.t = 1.2; } break;
    case 'flota': flotar(); if (j.t <= 0) elegir(); break;
    case 'orbes':
      flotar();
      if (j.t <= 0) {
        const a = Math.atan2(p.y - j.y, p.x - j.x);
        for (let k = -1; k <= 1; k++) lanzar('orbe', j.x, j.y - 14, a + k * 0.5, 75, D.dano * 0.55, { busca: 1.0, vida: 3.2 });
        Sonido.sfx.metal(); j.rafagas--; j.t = 0.8 / rapido;
        if (j.rafagas <= 0) { j.estado = 'cansado'; j.t = 1.5 / rapido; }
      }
      break;
    case 'cansado': if (j.t <= 0) { j.estado = 'flota'; j.t = 0.7 / rapido; } break; // tras una ráfaga se queda quieto: tu momento
    case 'anillo':
      if (j.t <= 0) {
        const n = j.fase === 2 ? 16 : 12, giro = Math.random();
        for (let k = 0; k < n; k++) lanzar('orbe', j.x, j.y - 10, ((k + giro) / n) * Math.PI * 2, 62, D.dano * 0.5, { vida: 3.5 });
        Sonido.sfx.metal(); j.rafagas--; j.t = 0.9 / rapido;
        if (j.rafagas <= 0) { j.estado = 'cansado'; j.t = 1.6 / rapido; }
      }
      break;
    case 'tele':
      if (!j.invisible && j.t <= 0) { j.invisible = true; j.t = 0.7; efecto('humo', 4, j.x, j.y, 0, 0.5); Sonido.sfx.zas(); }
      else if (j.invisible && j.t <= 0) {
        for (let k = 0; k < 16; k++) {
          const a = Math.random() * Math.PI * 2, nx = p.x + Math.cos(a) * 56, ny = p.y + Math.sin(a) * 42;
          if (!choca(nx, ny, j.hw, j.hh) && nx > g.x0 * T && nx < (g.x0 + g.w) * T && ny > g.y0 * T && ny < (g.y0 + g.h) * T) { j.x = nx; j.y = ny; break; }
        }
        j.invisible = false; efecto('humo', 4, j.x, j.y, 0, 0.5); j.estado = 'orbes'; j.t = 0.4; j.rafagas = 1;
      }
      break;
    case 'invoca':
      if (j.t <= 0) {
        for (let k = 0; k < (j.fase === 2 ? 3 : 2); k++) {
          const a = Math.random() * Math.PI * 2, e = nuevaCriatura(k === 2 ? 'gruto_chaman' : 'gruto_lancero', j.x + Math.cos(a) * 40, j.y + Math.sin(a) * 30);
          if (!choca(e.x, e.y, e.hw, e.hh)) { J.enemigos.push(e); efecto('humo', 4, e.x, e.y, 0, 0.5); }
        }
        Sonido.sfx.rugido(); j.estado = 'flota'; j.t = 2.2 / rapido;
      }
      break;
    case 'pilares':
      if (j.t <= 0) { // una línea de pilares de sombra que estallan en orden hacia ti
        const a = Math.atan2(p.y - j.y, p.x - j.x);
        for (let k = 1; k <= 9; k++) { const x = j.x + Math.cos(a) * k * 16, y = j.y + Math.sin(a) * k * 14; if (!solido(Math.floor(x / T), Math.floor(y / T))) avisoSuelo(x, y, 12, 0.6 + k * 0.09, D.dano, ['#c080ff', '#40204a', '#ffffff'], 'runa'); }
        Sonido.sfx.temblor(); j.rafagas--; j.t = 1.1 / rapido;
        if (j.rafagas <= 0) { j.estado = 'cansado'; j.t = 1.4 / rapido; }
      }
      break;
  }
  contacto(j, D.dano * 0.6);
}

// ---------------------------------------------------------------- estatuas y el Corazón

function activarEstatua(o, jefe) {
  quitar(JEFES[jefe].cristal, 1);
  o.extra = 1; J.estatuas[jefe] = true; cambioLuz(); marcarMapa(idx(o.x, o.y));
  J.temblor = 0.8; Sonido.sfx.latido(); setTimeout(() => Sonido.sfx.victoria(), 600);
  particulas(o.x * T + 8, o.y * T, 30, ['#ff6b8f', '#ffb3c8', '#fff0f4', '#ffd166'], { vel: 80, vida: 1, brilla: true });
  const n = Object.keys(J.estatuas).length;
  if (n >= 3) { J.corazonDespierto = true; J.panel = 'fin'; }
  else { aviso(`El Corazón late (${n}/3)`, null, '#ffb3c8'); J.panel = 'corazon'; }
}
// Qué dice el Corazón: lo que falta y hacia dónde queda.
function rumboA(x, y) {
  const a = Math.atan2(y - CENTRO, x - CENTRO), k = Math.round(((a + Math.PI * 2) % (Math.PI * 2)) / (Math.PI / 4)) % 8;
  return ['este', 'sureste', 'sur', 'suroeste', 'oeste', 'noroeste', 'norte', 'noreste'][k];
}
function palabrasCorazon() {
  const g = M.guaridas, out = [];
  if (!J.estatuas.topo) out.push(J.jefesMuertos.topo ? 'Pon el Cristal del Topo en su estatua.' : `Murdo, el que cavaba mis venas, se volvió ciego y hambriento. Escarba al ${rumboA(g.topo.x, g.topo.y)}, cerca de aquí.`);
  if (!J.estatuas.reina) out.push(J.jefesMuertos.reina ? 'Pon el Cristal de la Reina en su estatua.' : `Vhessa, la Madre de la Colmena, anida en la arcilla, al ${rumboA(g.reina.x, g.reina.y)}. Necesitarás un pico de cobre.`);
  if (!J.estatuas.ozmar) out.push(J.jefesMuertos.ozmar ? 'Pon el Cristal de Ozmar en su estatua.' : `Ozmar, el que me dio de comer la Podre, espera en su trono, en las ruinas del ${rumboA(g.ozmar.x, g.ozmar.y)}. Sus muros piden un pico de estaño.`);
  if (!out.length) return palabrasCorazon2();
  return out;
}
