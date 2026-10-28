'use strict';
// Conductas de las criaturas de la Selva. Cada una avisa antes de atacar (parpadeo o círculo en el suelo) y
// deja un hueco para contraatacar, como las del anillo interior.

// Semilla que vuela en arco y, al caer, revienta en enredaderas (círculo en el suelo).
function lanzarSemilla(x, y, tx, ty, dano, radio = 18) {
  const t = 0.9, vx = (tx - x) / t, vy = (ty - y) / t;
  J.proyectiles.push({ tipo: 'semilla', x, y, vx, vy, dano, vida: t, z: 12, vz: 130, g: 290, alCaer: (r) => avisoSuelo(r.x, r.y, radio, 0.55, dano, ['#5ea440', '#2e5a22', '#c8f070']) });
}

// Gruto jardinero: se queda a media distancia y lanza semillas que estallan en enredaderas.
CONDUCTAS.jardinero = (e, c, r, lejos, dt) => {
  const p = J.jug;
  if (e.estado === 'prepara') { if (e.t > 0.55) { e.estado = 'anda'; e.cd = 2.8; lanzarSemilla(e.x, e.y - 8, p.x, p.y, c.dano); Sonido.sfx.zas(); } return; }
  if (!r) return;
  if (lejos < 120 && lejos > 34 && e.cd <= 0 && veJugador(e)) { e.estado = 'prepara'; e.t = 0; return; }
  if (lejos > 80) mover(e, r.x * c.vel * dt, r.y * c.vel * dt);
  else if (lejos < 50) mover(e, -r.x * c.vel * 0.8 * dt, -r.y * c.vel * 0.8 * dt);
};

// Gruto cazador: tensa el arco (parpadea) y dispara una flecha recta y rápida; si te acercas, se aleja.
CONDUCTAS.cazador = (e, c, r, lejos, dt) => {
  const p = J.jug;
  if (e.estado === 'prepara') {
    if (e.t > 0.5) { e.estado = 'anda'; e.cd = 1.9; const a = Math.atan2(p.y - 4 - e.y, p.x - e.x); lanzar('flecha_e', e.x, e.y - 6, a, 200, c.dano, { vida: 1.4 }); Sonido.sfx.zas(); }
    return;
  }
  if (!r) return;
  if (lejos < 130 && lejos > 40 && e.cd <= 0 && veJugador(e)) { e.estado = 'prepara'; e.t = 0; return; }
  if (lejos > 100) mover(e, r.x * c.vel * dt, r.y * c.vel * dt);
  else if (lejos < 60) mover(e, -r.x * c.vel * dt, -r.y * c.vel * dt);
};

// Planta trampa: escondida hasta que te acercas; entonces se abre, avisa y muerde alrededor.
CONDUCTAS.trampa = (e, c, r, lejos) => {
  e.kbx = e.kby = 0;
  if (e.estado === 'prepara') { if (e.t > 0.6) { e.estado = 'muerde'; e.t = 0; avisoSuelo(e.x, e.y, 24, 0.01, c.dano, ['#c83a5a', '#3a8a3a', '#ffd0e0']); Sonido.sfx.golpe(); } return; }
  if (e.estado === 'muerde') { if (e.t > 0.5) { e.estado = 'anda'; e.cd = 1.6; } return; }
  e.estado = lejos < 70 ? 'anda' : 'escondida';
  if (lejos < 36 && e.cd <= 0) { e.estado = 'prepara'; e.t = 0; }
};

// Avispa esmeralda: vuela, zumba quieta un momento (aviso) y se lanza en línea recta; luego se aparta.
CONDUCTAS.avispa = (e, c, r, lejos, dt) => {
  const p = J.jug;
  e.z = 11 + Math.sin(e.t * 9) * 2;
  if (e.estado === 'prepara') { if (e.t > 0.55) { e.estado = 'carga'; e.t = 0; const a = Math.atan2(p.y - e.y, p.x - e.x); e.cx = Math.cos(a); e.cy = Math.sin(a); } return; }
  if (e.estado === 'carga') { if (mover(e, e.cx * 210 * dt, e.cy * 210 * dt) || e.t > 0.4) { e.estado = 'descansa'; e.t = 0; } return; }
  if (e.estado === 'descansa') { mover(e, -e.cx * 40 * dt, -e.cy * 40 * dt); if (e.t > 0.8) e.estado = 'anda'; return; }
  if (!r) return;
  if (lejos < 52 && e.cd <= 0) { e.estado = 'prepara'; e.t = 0; e.cd = 2; return; }
  const orbita = lejos < 46 ? -0.4 : 1;
  mover(e, (r.x * orbita - r.y * 0.5) * c.vel * dt, (r.y * orbita + r.x * 0.5) * c.vel * dt);
};

// Escarabajo de musgo: se planta, rasca el suelo (aviso) y embiste; si choca con una pared, se queda aturdido.
CONDUCTAS.carga = (e, c, r, lejos, dt) => {
  const p = J.jug;
  if (e.estado === 'prepara') { if (e.t > 0.75) { e.estado = 'carga'; e.t = 0; } if (Math.random() < 0.3) particulas(e.x, e.y, 1, ['#6a5040', '#8a7050'], { vel: 20 }); return; }
  if (e.estado === 'carga') {
    if (mover(e, e.cx * 250 * dt, e.cy * 250 * dt)) { e.estado = 'descansa'; e.t = 0; e.aturdido = 1.3; J.temblor = Math.max(J.temblor, 0.2); Sonido.sfx.temblor(); }
    else if (e.t > 0.55) { e.estado = 'descansa'; e.t = 0; }
    return;
  }
  if (e.estado === 'descansa') { if (e.t > 0.7) e.estado = 'anda'; return; }
  if (!r) return;
  if (lejos < 80 && lejos > 24 && e.cd <= 0 && veJugador(e)) { e.estado = 'prepara'; e.t = 0; e.cd = 2.6; const a = Math.atan2(p.y - e.y, p.x - e.x); e.cx = Math.cos(a); e.cy = Math.sin(a); return; }
  mover(e, r.x * c.vel * dt, r.y * c.vel * dt);
};

// Tentáculo de moho: sale del suelo cuando estás cerca y golpea donde estás (círculo que avisa).
CONDUCTAS.tentaculo = (e, c, r, lejos) => {
  const p = J.jug;
  e.kbx = e.kby = 0;
  if (e.estado === 'prepara') { if (e.t > 0.4) { e.estado = 'descansa'; e.t = 0; } return; }
  if (e.estado === 'descansa') { if (e.t > 1.3) e.estado = 'anda'; return; }
  e.estado = lejos < 90 ? 'anda' : 'escondida';
  if (lejos < 60 && e.cd <= 0) { e.estado = 'prepara'; e.t = 0; e.cd = 2.3; avisoSuelo(p.x, p.y, 17, 0.85, c.dano, ['#2ab0a0', '#0a3a3a', '#c0fff0']); }
};

// Qué dibujo toca a cada criatura nueva según lo que está haciendo.
const SPRITE_CRIATURA = {
  planta_trampa: (e) => `planta_trampa_${e.estado === 'escondida' ? 0 : e.estado === 'prepara' ? 2 : e.estado === 'muerde' ? 3 : 1}`,
  tentaculo_moho: (e) => `tentaculo_moho_${e.estado === 'escondida' ? 0 : e.estado === 'prepara' ? 3 : 1 + (Math.floor(e.t * 3) % 2)}`,
  avispa: (e) => `avispa_${e.estado === 'prepara' || e.estado === 'carga' ? 2 : Math.floor(e.t * 14) % 2}`,
  escarabajo_musgo: (e) => `escarabajo_musgo_${e.estado === 'prepara' ? 2 : e.estado === 'carga' ? 3 : Math.floor(e.t * 6) % 2}`,
};

// Luciérnagas, hojas que caen y polen en la Selva; esporas en la mazmorra.
function ambienteSelva(dt) {
  const p = J.jug, reg = M.region[idx(Math.floor(p.x / T), Math.floor(p.y / T))];
  if (reg === REGION.SELVA || reg === REGION.PRADERA) {
    if (Math.random() < dt * 5) J.particulas.push({ x: p.x + (Math.random() - 0.5) * 220, y: p.y + (Math.random() - 0.5) * 140, z: 4 + Math.random() * 20, vx: (Math.random() - 0.5) * 8, vy: (Math.random() - 0.5) * 4, vz: (Math.random() - 0.5) * 3, g: 0, vida: 2 + Math.random() * 3, c: Math.random() < 0.7 ? '#d8ff70' : '#fff0a0', s: 1, brilla: true, chispa: true });
    if (Math.random() < dt * 2) J.particulas.push({ x: p.x + (Math.random() - 0.5) * 220, y: p.y + (Math.random() - 0.5) * 140 - 20, z: 40, vx: 6 + Math.random() * 6, vy: 2, vz: -6, g: 0, vida: 4, c: Math.random() < 0.5 ? '#6aa040' : '#c8a040', s: 1 });
  } else if (reg === REGION.MOHO && Math.random() < dt * 4) J.particulas.push({ x: p.x + (Math.random() - 0.5) * 200, y: p.y + (Math.random() - 0.5) * 120, z: 2, vx: 0, vy: 0, vz: 4, g: -1, vida: 3, c: '#5ae0c0', s: 1, brilla: true, chispa: true });
}
