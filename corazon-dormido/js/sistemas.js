'use strict';
// Sistemas: crear (mesas por nivel y cofres cercanos), cofres, olla, cultivos y pesca.


// ---------------------------------------------------------------- crear

// Estaciones a menos de 3,5 casillas (las de nivel alto valen para lo de nivel bajo) y cofres a menos de 10.
function estacionesCerca() {
  const p = J.jug, est = new Set(), cofres = [], tx = Math.floor(p.x / T), ty = Math.floor(p.y / T);
  let mesa = 0, yunque = 0;
  for (let y = ty - 10; y <= ty + 10; y++) for (let x = tx - 10; x <= tx + 10; x++) {
    const o = objetoEn(x, y);
    if (!o) continue;
    const info = OBJ[o.tipo], d = Math.hypot(x * T + 8 - p.x, y * T + 8 - p.y) / T;
    if (info.estacion && d < 3.5) {
      est.add(info.estacion);
      mesa = Math.max(mesa, NIVEL_MESA[info.estacion] || 0); yunque = Math.max(yunque, NIVEL_YUNQUE[info.estacion] || 0);
    }
    if (info.interactua === 'cofre' && o.datos && d < 10) cofres.push(o);
  }
  for (const [m, n] of Object.entries(NIVEL_MESA)) if (n <= mesa) est.add(m);
  for (const [m, n] of Object.entries(NIVEL_YUNQUE)) if (n <= yunque) est.add(m);
  return { est, cofres };
}
function cuentaTotal(id, ctx) { let n = J.cuenta(id); for (const c of ctx.cofres) for (const s of c.datos.inv) if (s && s.id === id) n += s.n; return n; }
function puedeCrear(r, ctx) {
  if (r.est && !ctx.est.has(r.est)) return false;
  for (const id in r.ing) if (cuentaTotal(id, ctx) < r.ing[id]) return false;
  return true;
}
function crear(r) {
  const ctx = estacionesCerca();
  if (!puedeCrear(r, ctx)) { Sonido.sfx.error(); return false; }
  for (const [id, n] of Object.entries(r.ing)) {
    let falta = quitar(id, n);
    for (const c of ctx.cofres) if (falta) falta = quitar(id, falta, c.datos.inv);
  }
  const doble = Math.random() < nivel('artesania') * 0.01 ? 2 : 1;
  const sobra = agregar(r.item, r.n * doble);
  if (sobra) soltar(J.jug.x, J.jug.y, r.item, sobra);
  Sonido.sfx.crear();
  avisoRecogido(r.item, r.n * doble);
  J.hito[r.item] = true;
  ganarXP('artesania', 2 + Object.values(r.ing).reduce((a, b) => a + b, 0) * 0.3);
  return true;
}

// ---------------------------------------------------------------- cofres

// Pasa una pila entera de un inventario a otro (toque en el panel del cofre).
function pasarPila(desde, i, hacia) {
  const s = desde[i];
  if (!s) return;
  const sobra = agregar(s.id, s.n, hacia);
  if (sobra === s.n) { aviso('No cabe'); return; }
  if (sobra) s.n = sobra; else desde[i] = null;
  Sonido.sfx.recoger(0);
}
// Guarda en el cofre todo lo que ya hay en él (como el «apilar rápido» de Core Keeper).
function apilarEnCofre(cofre) {
  let movidas = 0;
  for (let i = N_BARRA; i < N_INV; i++) {
    const s = J.inv[i];
    if (!s || !cofre.some((c) => c && c.id === s.id)) continue;
    const sobra = agregar(s.id, s.n, cofre);
    if (sobra !== s.n) movidas++;
    if (sobra) s.n = sobra; else J.inv[i] = null;
  }
  if (movidas) Sonido.sfx.crear(); else aviso('Nada que apilar');
}

// ---------------------------------------------------------------- olla

function cocinar(o) {
  const { a, b } = o.datos;
  if (!a || !b) { aviso('Pon dos ingredientes'); return; }
  if (J.cuenta(a) < (a === b ? 2 : 1) || (a !== b && J.cuenta(b) < 1)) { aviso('Te faltan ingredientes'); return; }
  quitar(a, 1); quitar(b, 1);
  const [x, y] = [a, b].sort(), id = registrarPlato(x, y);
  const n = 1 + (Math.random() < 0.2 + nivel('cocina') * 0.01 ? 1 : 0);
  const sobra = agregar(id, n);
  if (sobra) soltar(J.jug.x, J.jug.y, id, sobra);
  avisoRecogido(id, n); Sonido.sfx.crear();
  particulas(o.x * T + 8, o.y * T + 4, 10, ['#fff0d0', '#ffd080', '#ffffff'], { vel: 30, alto: 30, brilla: true });
  ganarXP('cocina', 6);
  if (J.cuenta(a) < (a === b ? 2 : 1)) o.datos.a = null;
  if (J.cuenta(b) < 1) o.datos.b = null;
}

// ---------------------------------------------------------------- cultivos

function etapaCultivo(o) { const c = CULTIVOS[o.datos.planta]; return Math.min(3, Math.floor(o.datos.crec / c.etapa)); }
let relojCultivos = 0;
function actualizarCultivos(dt) {
  relojCultivos += dt;
  if (relojCultivos < 1) return;
  for (const o of M.objetos.values()) if (o.tipo === 'cultivo') o.datos.crec += relojCultivos * (o.datos.regado ? 2 : 1);
  relojCultivos = 0;
}
function cosechar(o) {
  const c = CULTIVOS[o.datos.planta], cx = o.x * T + 8, cy = o.y * T + 10;
  if (etapaCultivo(o) < 3) { aviso(`${c.nombre}: todavía está creciendo`); J.jug.golpeT = 0.3; return; }
  quitarObjeto(o.x, o.y);
  const doble = Math.random() < 0.15 + nivel('jardineria') * 0.01;
  soltar(cx, cy, c.item, 1 + (doble ? 1 : 0) + Math.floor(Math.random() * 2));
  soltar(cx, cy, c.semilla, 1 + (Math.random() < 0.5 ? 1 : 0));
  particulas(cx, cy, 10, ['#6aa04a', '#a8d070', '#e8f0b0'], { vel: 40 });
  Sonido.sfx.recoger(2); ganarXP('jardineria', 5);
  J.jug.golpeT = 0.25;
}

// ---------------------------------------------------------------- pesca
// Lanza al agua; cuando pica, tira. En la lucha: mantén para recoger cuando el pez descansa y suelta cuando
// tira (en rojo); si tensas de más, el sedal se rompe.

function lanzarCana(it) {
  const p = J.jug;
  for (const d of [1.6, 2.4, 3.2]) {
    const x = p.x + p.fx * d * T, y = p.y + p.fy * d * T, tx = Math.floor(x / T), ty = Math.floor(y / T);
    if (enMundo(tx, ty) && M.piso[idx(tx, ty)] === SUELO.AGUA) {
      const cebo = J.cuenta('cebo') > 0;
      J.pesca = { estado: 'espera', x, y, t: (2 + Math.random() * 5) * (cebo ? 0.45 : 1), cana: it, region: M.region[idx(tx, ty)], cebo };
      p.golpeT = 0.5; p.swing = 0.4; p.swingDur = 0.4; p.swingItem = it.icono;
      Sonido.sfx.zas(); particulas(x, y, 6, ['#a8dcff', '#ffffff'], { vel: 20, alto: 10 });
      return;
    }
    if (muroEn(tx, ty)) break;
  }
  aviso('Lanza la caña al agua'); p.golpeT = 0.3;
}
function tirarCana() {
  const ps = J.pesca, p = J.jug;
  p.golpeT = 0.3;
  if (ps.estado === 'pica') {
    const lista = PECES[ps.region] || PECES[1], tot = lista.reduce((a, b) => a + b[1], 0);
    let s = Math.random() * tot, pez = lista[0][0];
    for (const [id, w] of lista) { s -= w; if (s <= 0) { pez = id; break; } }
    const dificil = (ITEMS[pez].rareza || 0) + 1;
    Object.assign(ps, { estado: 'lucha', pez, progreso: 0.35, tension: 0, lucha: false, cambio: 1, dificil });
    Sonido.sfx.metal();
  } else if (ps.estado === 'espera') { J.pesca = null; aviso('Recoges el sedal'); }
}
function actualizarPesca(dt, tirando) {
  const ps = J.pesca;
  if (!ps) return;
  ps.t -= dt;
  if (ps.estado === 'espera' && ps.t <= 0) { ps.estado = 'pica'; ps.t = 1.3; Sonido.sfx.recoger(4); particulas(ps.x, ps.y, 8, ['#a8dcff', '#ffffff'], { vel: 30, alto: 20 }); }
  else if (ps.estado === 'pica' && ps.t <= 0) { ps.estado = 'espera'; ps.t = 2 + Math.random() * 4; aviso('Se escapó: tira cuando pique'); }
  else if (ps.estado === 'lucha') {
    ps.cambio -= dt;
    if (ps.cambio <= 0) { ps.lucha = !ps.lucha; ps.cambio = ps.lucha ? 0.6 + Math.random() * 0.8 * ps.dificil : 0.9 + Math.random() * 1.2; }
    const facil = (1 + nivel('pesca') * 0.02 + ((J.st && J.st.pesca) || 0)) * ps.cana.fuerza;
    if (tirando) {
      if (ps.lucha) { ps.tension += (0.9 * ps.dificil / facil) * dt; ps.progreso -= 0.05 * dt; }
      else ps.progreso += 0.32 * facil * dt;
    } else { ps.tension = Math.max(0, ps.tension - 0.5 * dt); ps.progreso -= (ps.lucha ? 0.12 : 0.04) * dt; }
    if (ps.progreso >= 1) {
      if (ps.cebo) quitar('cebo', 1);
      const sobra = agregar(ps.pez, 1);
      if (sobra) soltar(J.jug.x, J.jug.y, ps.pez, 1);
      avisoRecogido(ps.pez, 1); Sonido.sfx.victoria(); ganarXP('pesca', 6 * ps.dificil);
      particulas(ps.x, ps.y, 12, ['#a8dcff', '#ffffff', '#ffd166'], { vel: 50 });
      J.pesca = null;
    } else if (ps.tension >= 1) { aviso('¡Se rompió el sedal!'); Sonido.sfx.error(); J.pesca = null; }
    else if (ps.progreso <= 0) { aviso('El pez se escapó'); J.pesca = null; }
  }
}
