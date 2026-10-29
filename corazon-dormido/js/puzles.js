'use strict';
// Puzles y viaje del mundo grande (0.7): braseros de runas que se encienden en orden, palancas con clave, bloques
// que se empujan sobre placas, puertas selladas que se abren al resolverlos, piedras de paso para viajar, restos de
// Veladores que se registran y grutos que rezan a la Podre hasta que te acercas. Los puzles los pone el generador
// (M.puzles); lo que cambia (braseros encendidos, palancas, bloques, puertas abiertas) va en los trozos guardados.

// ---------------------------------------------------------------- puertas selladas

function abrirPuerta(k) {
  const pz = M.puzles[k];
  if (!pz) return;
  const o = objetoEn(pz.puerta.x, pz.puerta.y);
  if (!o || o.tipo !== 'puerta_sellada') return;
  quitarObjeto(pz.puerta.x, pz.puerta.y);
  const cx = pz.puerta.x * T + 8, cy = pz.puerta.y * T + 8;
  particulas(cx, cy, 30, ['#c8a8ff', '#7a48e0', '#ffffff', '#e8d8ff'], { vel: 80, vida: 1, brilla: true });
  efecto('humo', 6, cx, cy + 6, 0, 0.6);
  J.temblor = Math.max(J.temblor, 0.6); Sonido.sfx.temblor(); setTimeout(() => Sonido.sfx.victoria(), 400);
  J.hito['puzle_' + k] = true;
  if (pz.tipo === 'palancas') J.hito.cripta = true;
  aviso('La puerta sellada se abre', null, '#c8a8ff');
}
const puzleResuelto = (k) => { const pz = M.puzles[k]; const o = pz && objetoEn(pz.puerta.x, pz.puerta.y); return !o || o.tipo !== 'puerta_sellada'; };
INTERACCIONES.sellada = (o) => {
  const k = M.puzles.findIndex((pz) => pz.puerta.x === o.x && pz.puerta.y === o.y), pz = M.puzles[k];
  const pista = !pz ? '' : pz.tipo === 'braseros' ? 'Fuera hay braseros con runas.' : pz.tipo === 'palancas' ? 'En la entrada hay cuatro palancas.' : 'En la sala hay bloques y placas.';
  aviso('Una puerta sellada con runas. ' + pista, null, '#c8a8ff');
  Sonido.sfx.metal();
};

// ---------------------------------------------------------------- braseros de runas

INTERACCIONES.brasero = (o) => {
  const k = o.datos ? o.datos.puzle : -1, pz = M.puzles[k], runa = o.datos ? o.datos.runa : 0;
  if (!pz || puzleResuelto(k)) { aviso(`La runa del ${RUNAS[runa]} arde`); return; }
  if (o.extra) { aviso(`La runa del ${RUNAS[runa]} ya arde`); return; }
  // lo encendido de antes de cargar no cuenta como orden: se apaga y se empieza de nuevo
  const encendidos = pz.braseros.filter((b) => { const q = objetoEn(b.x, b.y); return q && q.extra; });
  if (encendidos.length !== pz.encendidos.length) { apagarBraseros(pz); }
  o.extra = 1; pz.encendidos.push(runa);
  tocarTrozo(o.x, o.y); cambioLuz(); Sonido.sfx.crear();
  particulas(o.x * T + 8, o.y * T - 6, 12, ['#c478ff', '#e4b4ff', '#ffffff'], { vel: 50, brilla: true });
  const bien = pz.encendidos.every((r, i) => r === pz.orden[i]);
  if (!bien) {
    aviso(`La runa del ${RUNAS[runa]}… las llamas tiemblan`, null, '#ff8a6a');
    setTimeout(() => { apagarBraseros(pz); aviso('Las llamas se apagan. No era ese orden.', null, '#ff8a6a'); Sonido.sfx.metal(); }, 900);
  } else if (pz.encendidos.length === pz.orden.length) abrirPuerta(k);
  else aviso(`La runa del ${RUNAS[runa]} arde`, null, '#c8a8ff');
};
function apagarBraseros(pz) {
  for (const b of pz.braseros) { const q = objetoEn(b.x, b.y); if (q && q.extra) { q.extra = 0; tocarTrozo(b.x, b.y); } }
  pz.encendidos = []; cambioLuz();
}

// ---------------------------------------------------------------- palancas

INTERACCIONES.palanca = (o) => {
  Sonido.sfx.metal(); J.temblor = Math.max(J.temblor, 0.15);
  if (o.datos && o.datos.reinicio !== undefined) { reiniciarBloques(o.datos.reinicio); return; }
  o.extra = o.extra ? 0 : 1; tocarTrozo(o.x, o.y);
  const k = o.datos ? o.datos.puzle : -1, pz = M.puzles[k];
  if (!pz || pz.tipo !== 'palancas' || puzleResuelto(k)) return;
  const estado = pz.palancas.map((p) => { const q = objetoEn(p.x, p.y); return q && q.extra ? 1 : 0; });
  if (estado.every((v, i) => v === pz.clave[i])) abrirPuerta(k);
};

// ---------------------------------------------------------------- bloques y placas

const hayBloque = (x, y) => { const o = objetoEn(x, y); return !!o && o.tipo === 'bloque_empuje'; };
const placaPisada = (x, y) => hayBloque(x, y) || (J.jug && Math.floor(J.jug.x / T) === x && Math.floor(J.jug.y / T) === y);
INTERACCIONES.empujar = (o) => {
  const p = J.jug, dx = o.x + 0.5 - p.x / T, dy = o.y + 0.5 - (p.y - 4) / T;
  const [mx, my] = Math.abs(dx) > Math.abs(dy) ? [Math.sign(dx), 0] : [0, Math.sign(dy)];
  const tx = o.x + mx, ty = o.y + my;
  if (!enMundo(tx, ty) || solido(tx, ty, true) || objetoEn(tx, ty)) { aviso('No se mueve: hay algo detrás'); Sonido.sfx.metal(); return; }
  const datos = o.datos;
  quitarObjeto(o.x, o.y);
  const n = ponerObjeto(tx, ty, 'bloque_empuje'); n.datos = datos;
  Sonido.sfx.picar(); efecto('humo', 3, tx * T + 8, ty * T + 12, 0, 0.35);
  if (M.piso[idx(tx, ty)] === SUELO.PLACA) { Sonido.sfx.crear(); particulas(tx * T + 8, ty * T + 10, 8, ['#c8a8ff', '#7a48e0'], { vel: 30, brilla: true }); }
  comprobarPlacas();
};
function comprobarPlacas() {
  M.puzles.forEach((pz, k) => {
    if (pz.tipo !== 'placas' || puzleResuelto(k)) return;
    if (pz.placas.every((q) => hayBloque(q.x, q.y))) abrirPuerta(k);
  });
}
// La palanca de la sala devuelve los bloques a su sitio (por si alguno se queda atascado en una esquina).
function reiniciarBloques(k) {
  const pz = M.puzles[k];
  if (!pz || puzleResuelto(k)) { aviso('No pasa nada'); return; }
  const bloques = [];
  for (let y = pz.puerta.y - 2; y <= pz.puerta.y + 16; y++) for (let x = pz.puerta.x - 12; x <= pz.puerta.x + 12; x++) { const o = objetoEn(x, y); if (o && o.tipo === 'bloque_empuje' && o.datos && o.datos.puzle === k) bloques.push(o); }
  for (const o of bloques) quitarObjeto(o.x, o.y);
  for (const o of bloques) { if (!objetoEn(o.datos.x0, o.datos.y0)) { const n = ponerObjeto(o.datos.x0, o.datos.y0, 'bloque_empuje'); n.datos = o.datos; } }
  J.temblor = Math.max(J.temblor, 0.5); Sonido.sfx.temblor();
  aviso('Los bloques vuelven a su sitio', null, '#c8a8ff');
}

// ---------------------------------------------------------------- tablillas de los puzles

const NOMBRE_PALANCA = ['abajo', 'arriba'];
const leerTablillaVieja = INTERACCIONES.tablilla;
INTERACCIONES.tablilla = (o) => {
  const d = o.datos || {};
  if (d.orden) J.tablilla = `«La llama pasa primero por la runa del ${RUNAS[d.orden[0]]}${d.orden.slice(1, -1).map((r) => `, luego por la del ${RUNAS[r]}`).join('')} y acaba en la del ${RUNAS[d.orden[d.orden.length - 1]]}. Quien la lleve en otro orden se quedará fuera.»`;
  else if (d.clave) J.tablilla = `«Las palancas de los muertos, de izquierda a derecha: ${d.clave.map((v) => NOMBRE_PALANCA[v]).join(', ')}. Que nadie despierte a lo que guarda la última sala.»`;
  else if (d.texto === 'placas') J.tablilla = '«Cuatro piedras sobre cuatro sellos abren la cámara. Empuja las piedras con las manos. Si una se atasca, la palanca de la entrada las devuelve a su sitio.»';
  else { leerTablillaVieja(o); return; }
  J.panel = 'tablilla'; Sonido.sfx.menu();
};

// ---------------------------------------------------------------- piedras de paso

INTERACCIONES.paso = (o) => {
  const nombre = (o.datos && o.datos.nombre) || 'Piedra de paso';
  if (!J.pasos) J.pasos = {};
  if (!J.pasos[nombre]) {
    J.pasos[nombre] = true; o.extra = 1; tocarTrozo(o.x, o.y); cambioLuz();
    particulas(o.x * T + 8, o.y * T - 10, 24, ['#80e0ff', '#ffffff', '#c0f0ff'], { vel: 60, brilla: true });
    Sonido.sfx.nivel();
    aviso(`${nombre}: despierta. Desde cualquier piedra de paso podrás volver aquí.`, null, '#80e0ff');
    return;
  }
  J.pasoDesde = nombre; J.panel = 'pasos'; Sonido.sfx.menu();
};
PANELES.pasos = (ctx) => {
  fondoPanel(ctx);
  const lista = M.pasos.filter((p) => J.pasos && J.pasos[p.nombre]);
  const w = Math.min(LW - 20, 190), h = 30 + lista.length * 16, x = Math.round(LW / 2 - w / 2), y = Math.max(4, Math.round(LH / 2 - h / 2));
  marco(ctx, x, y, w, h); boton(x, y, w, h, () => {}, { mudo: true });
  texto(ctx, 'Piedras de paso', x + 6, y + 6, '#80e0ff', { sombra: COL.negro });
  cruz(ctx, x + w - 17, y + 3);
  lista.forEach((p, k) => {
    const aqui = p.nombre === J.pasoDesde;
    botonTexto(ctx, x + 6, y + 20 + k * 16, w - 12, 13, aqui ? p.nombre + ' (aquí)' : p.nombre, () => { if (!aqui) viajarA(p); }, { color: aqui ? COL.botonOff : '#2a4a5a' });
  });
};
function viajarA(p) {
  J.panel = null;
  const pj = J.jug;
  particulas(pj.x, pj.y - 6, 20, ['#80e0ff', '#ffffff'], { vel: 70, brilla: true });
  let tx = p.x, ty = p.y + 1;
  for (let r = 1; r < 5 && solido(tx, ty, true); r++) for (const [dx, dy] of [[0, r], [r, 0], [-r, 0], [0, -r]]) if (!solido(p.x + dx, p.y + dy, true)) { tx = p.x + dx; ty = p.y + dy; break; }
  pj.x = tx * T + 8; pj.y = ty * T + 10; J.cam.x = pj.x; J.cam.y = pj.y;
  J.enemigos = J.enemigos.filter((e) => e.fija !== undefined && Math.hypot(e.x - pj.x, e.y - pj.y) < 400);
  particulas(pj.x, pj.y - 6, 26, ['#80e0ff', '#ffffff', '#c0f0ff'], { vel: 80, brilla: true });
  Sonido.sfx.nivel(); J.temblor = 0.3;
  aviso(p.nombre, null, '#80e0ff');
}

// ---------------------------------------------------------------- restos de Veladores

// Lo que llevaba encima cada Velador que no llegó a casa. Las líneas salen por orden de la casilla.
const RESTOS = [
  'Lleva el velo puesto. Debajo no hay cara: solo hueso limpio y una mancha morada en la frente.',
  'Aprieta una llave contra el pecho. No abre nada que siga en pie.',
  'Escribió algo en la piedra con el dedo: «Volved a la Muralla». La última letra está a medias.',
  'La túnica está rajada por la espalda. Huía de algo, y ese algo era más rápido.',
  'Tiene los brazos cruzados y las manos atadas con raíz. Lo dejaron aquí a propósito.',
  'Junto a él, una lámpara de plata vacía. Los Veladores llevaban luz a todas partes. Este se quedó sin ella.',
  'Dos cuerpos abrazados. El pequeño tiene la marca de la Podre; el grande, no.',
  'En el cinturón, un sello con el dibujo de un ojo cerrado. El sello de los que vigilaban la Muralla.',
  'Un saco de semillas de la Selva, intacto. Pensaba volver a plantar.',
  'Le faltan los dientes. Alguien se los llevó, uno a uno. Los grutos coleccionan cosas de sus abuelos.',
  'Un libro de rezos con todas las páginas en blanco salvo una: «Ásor duerme. Que siga durmiendo».',
  'Sus huesos tienen el color de la médula de la Ciénaga. Se quedó demasiado tiempo junto al Pozo.',
];
INTERACCIONES.restos = (o) => {
  if (o.datos && o.datos.visto) { aviso('Ya lo registraste. Descansa.'); return; }
  o.datos = { visto: true }; tocarTrozo(o.x, o.y);
  const banda = BANDA(M.region[idx(o.x, o.y)]), r = Math.random;
  const botin = banda === 0 ? [['moneda', 3, 10, 0.8], ['piedra_regreso', 1, 1, 0.15], ['pluma_antigua', 1, 1, 0.15], ['gema_antigua', 1, 1, 0.05]]
    : banda === 1 ? [['moneda', 6, 16, 0.8], ['pagina_velada', 1, 1, 0.3], ['piedra_regreso', 1, 1, 0.15], ['savia_negra', 1, 1, 0.12], ['gema_antigua', 1, 1, 0.06]]
      : [['moneda', 10, 24, 0.8], ['pagina_velada', 1, 2, 0.45], ['lingote_plata', 1, 1, 0.1], ['piedra_regreso', 1, 1, 0.15], ['gema_antigua', 1, 1, 0.08]];
  soltarLista(o.x * T + 8, o.y * T + 10, botin);
  void r;
  J.hito.restos = (J.hito.restos || 0) + 1;
  J.tablilla = RESTOS[Math.floor(hash2(o.x, o.y, 91) * RESTOS.length)];
  J.panel = 'restos'; Sonido.sfx.menu();
};
PANELES.restos = (ctx) => panelMensaje(ctx, 'Un Velador', J.tablilla || '', 'Cerrar', () => { J.panel = null; }, '#c8c0a8');

// ---------------------------------------------------------------- grutos que rezan
// Las fijas con reza: true se quedan quietas mirando a su ídolo hasta que te acercas o les pegas; entonces se
// levantan todos los del santuario.
function rezaGruto(e, lejos) {
  if (!e.reza) return false;
  if (lejos < 5.5 * T || e.golpeadoT) {
    for (const o of J.enemigos) if (o.reza && Math.hypot(o.x - e.x, o.y - e.y) < 10 * T) { o.reza = false; o.cd = 0.4 + Math.random() * 0.6; }
    Sonido.sfx.rugido();
    return false;
  }
  e.mira = e.rezaX !== undefined ? (e.rezaX >= e.x ? 1 : -1) : e.mira;
  if (Math.random() < 0.02) J.particulas.push({ x: e.x + (Math.random() - 0.5) * 6, y: e.y - 2, z: 6, vx: 0, vy: 0, vz: 7, g: 0, vida: 1.6, c: Math.random() < 0.5 ? '#ff3cb4' : '#a0206e', s: 1, brilla: true, chispa: true });
  return true;
}
