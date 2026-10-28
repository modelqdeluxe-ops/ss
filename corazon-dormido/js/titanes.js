'use strict';
// Los dos titanes de la Selva Esmeralda.
//  - Hiedra Viva: un jardín envenenado con raíces. No se mueve: saca filas de raíces, lanza esporas que dejan
//    charcos de veneno, barre a su alrededor con el látigo (y se queda expuesta) y hace brotar plantas trampa.
//  - Tamar, Titán del Cielo: baja al templo cuando haces la ofrenda. Lanza abanicos de plumas, levanta el vuelo
//    (solo se ve su sombra, que te sigue) y cae en picado (se queda aturdido), llama a los rayos y sopla tornados.

// ---------------------------------------------------------------- Hiedra Viva

ACTUALIZAR_JEFE.hiedra = (j, dt) => {
  const p = J.jug, rapido = j.fase === 2 ? 1.35 : 1, D = JEFES.hiedra, lejos = Math.hypot(p.x - j.x, p.y - j.y);
  const elegir = () => {
    const brotes = J.enemigos.filter((e) => e.tipo === 'planta_trampa' && e.fija === undefined).length;
    const ops = ['raices', 'esporas', lejos < 60 ? 'latigo' : 'raices'];
    if (brotes < 4) ops.push('brotes');
    if (lejos < 48) ops.push('latigo');
    const sig = ops.filter((x) => x !== j.ultimo);
    j.estado = sig[Math.floor(Math.random() * sig.length)]; j.ultimo = j.estado; j.t = 0.6 / rapido; j.rafagas = j.fase === 2 ? 3 : 2;
  };
  switch (j.estado) {
    case 'despierta': if (j.t <= 0) { j.estado = 'espera'; j.t = 1.2; } break;
    case 'espera': if (j.t <= 0) elegir(); break;
    case 'raices':
      if (j.t <= 0) { // filas de raíces que salen hacia ti, una detrás de otra
        const a0 = Math.atan2(p.y - j.y, p.x - j.x), filas = j.fase === 2 ? [-0.35, 0, 0.35] : [0];
        for (const da of filas) for (let k = 1; k <= 10; k++) {
          const x = j.x + Math.cos(a0 + da) * (14 + k * 14), y = j.y + Math.sin(a0 + da) * (10 + k * 12);
          if (!solido(Math.floor(x / T), Math.floor(y / T))) avisoSuelo(x, y, 12, 0.55 + k * 0.08, D.dano, ['#5ea440', '#3a2a10', '#c8f070']);
        }
        Sonido.sfx.excavar(); j.rafagas--; j.t = 1.0 / rapido;
        if (j.rafagas <= 0) { j.estado = 'espera'; j.t = 1.3 / rapido; }
      }
      break;
    case 'esporas':
      if (j.t <= 0) {
        const n = j.fase === 2 ? 7 : 5;
        for (let k = 0; k < n; k++) { const a = Math.random() * Math.PI * 2, d = Math.random() * 46; escupir(j.x, j.y - 22, p.x + Math.cos(a) * d, p.y + Math.sin(a) * d * 0.8, D.dano * 0.8, 'veneno'); }
        Sonido.sfx.zas(); j.rafagas--; j.t = 0.9 / rapido;
        if (j.rafagas <= 0) { j.estado = 'espera'; j.t = 1.4 / rapido; }
      }
      break;
    case 'latigo': // anillo de aviso alrededor; después se queda expuesta
      if (!j.avisado) { j.avisado = true; avisoSuelo(j.x, j.y, 54, 0.95 / rapido, D.dano * 1.2, ['#c83a5a', '#3a8a3a', '#ffffff']); j.t = 1.0 / rapido; Sonido.sfx.rugido(); }
      else if (j.t <= 0) { j.avisado = false; j.estado = 'aturdido'; j.t = 2.0; J.temblor = 0.4; }
      break;
    case 'aturdido':
      if (Math.random() < 0.15) particulas(j.x, j.y - 36, 1, ['#ffd166', '#ffffff'], { vel: 25, g: 0, brilla: true });
      if (j.t <= 0) { j.estado = 'espera'; j.t = 1.0; }
      break;
    case 'brotes':
      if (j.t <= 0) {
        for (let k = 0; k < (j.fase === 2 ? 3 : 2); k++) {
          const a = Math.random() * Math.PI * 2, e = nuevaCriatura('planta_trampa', j.x + Math.cos(a) * 52, j.y + Math.sin(a) * 40);
          if (!choca(e.x, e.y, e.hw, e.hh)) { J.enemigos.push(e); efecto('humo', 4, e.x, e.y, 0, 0.4); }
        }
        Sonido.sfx.excavar(); j.estado = 'espera'; j.t = 1.6 / rapido;
      }
      break;
  }
  // en la fase 2 el jardín suelta nubes de veneno de vez en cuando
  if (j.fase === 2 && Math.random() < dt * 0.5) { const a = Math.random() * Math.PI * 2, d = 30 + Math.random() * 120; J.charcos.push({ x: j.x + Math.cos(a) * d, y: j.y + Math.sin(a) * d * 0.8, t: 4, dano: D.dano * 0.6, tipo: 'veneno' }); }
  contacto(j, D.dano * 0.6);
};
DIBUJAR_JEFE.hiedra = (j) => {
  const x = j.x / T, z = j.y / T + 0.1, abierta = j.estado === 'latigo' || j.estado === 'raices' || j.estado === 'esporas';
  const f = j.estado === 'aturdido' ? 3 : abierta ? 2 : Math.floor(J.t * 2) % 2, sacude = j.estado === 'muriendo' || (j.estado === 'latigo' && j.t > 0) ? Math.sin(J.t * 50) * 0.06 : 0;
  GL.plano('sombra_l', x, z - 0.1, { tinte: [1, 1, 1, 0.85], escala: 1.6 });
  GL.sprite(`hiedra_${f}`, x + sacude + dxDe(`hiedra_${f}`), z, { unlit: 0.35, blanco: j.flash > 0 ? 0.85 : 0 });
  GL.sprite(`hiedra_${f}_luz`, x + sacude + dxDe(`hiedra_${f}`), z + 0.002, { unlit: 1, lote: GL.lotes.brillo, tinte: [1, 1, 1, 0.6 + 0.25 * Math.sin(J.t * 3)] });
  GL.halo(x, 1.4, z, 1.8, [0.7, 0.3, 0.9], 0.16 + 0.06 * Math.sin(J.t * 2));
};

// ---------------------------------------------------------------- Tamar, Titán del Cielo

CREAR_JEFE.tamar = (j) => { j.alto = 30; j.vuelo = 3.5; j.invocar = !!J.hito.tamarInvocado; j.invisible = true; j.subiendo = j.persigue = j.cae = j.sopla = false; };
CREAR_JEFE.hiedra = (j) => { j.avisado = false; };
ACTUALIZAR_JEFE.tamar = (j, dt) => {
  const p = J.jug, rapido = j.fase === 2 ? 1.3 : 1, D = JEFES.tamar, g = M.guaridas.tamar;
  const elegir = () => {
    const ops = ['plumas', 'vuelo', 'rayos', 'viento'].filter((x) => x !== j.ultimo);
    j.estado = ops[Math.floor(Math.random() * ops.length)]; j.ultimo = j.estado; j.t = 0.7 / rapido; j.rafagas = j.fase === 2 ? 3 : 2;
  };
  const dentro = () => { j.x = Math.max((g.x - 12) * T, Math.min((g.x + 12) * T, j.x)); j.y = Math.max((g.y - 12) * T, Math.min((g.y + 12) * T, j.y)); };
  switch (j.estado) {
    case 'despierta': // baja del cielo
      j.vuelo = Math.max(0, j.t / 1.6) * 3.5; j.invisible = j.vuelo > 0.4;
      if (j.t <= 0) { j.vuelo = 0; j.invisible = false; j.estado = 'espera'; j.t = 1.2; J.temblor = 0.8; Sonido.sfx.temblor(); particulas(j.x, j.y, 30, ['#e8e8f0', '#c8d0e0', '#ffffff'], { vel: 100, s: 2 }); }
      break;
    case 'espera': {
      const a = Math.atan2(p.y - j.y, p.x - j.x), d = Math.hypot(p.x - j.x, p.y - j.y);
      if (d > 50) mover(j, Math.cos(a) * 30 * rapido * dt, Math.sin(a) * 30 * rapido * dt);
      if (j.t <= 0) elegir();
      break;
    }
    case 'plumas':
      if (j.t <= 0) {
        const a0 = Math.atan2(p.y - j.y, p.x - j.x), n = j.fase === 2 ? 11 : 7;
        for (let k = 0; k < n; k++) lanzar('pluma', j.x, j.y - 18, a0 + (k - (n - 1) / 2) * 0.13, 120, D.dano * 0.55, { vida: 2.6 });
        Sonido.sfx.zas(); j.rafagas--; j.t = 0.8 / rapido;
        if (j.rafagas <= 0) { j.estado = 'espera'; j.t = 1.3 / rapido; }
      }
      break;
    case 'vuelo': // sube, su sombra te persigue y cae en picado
      if (!j.subiendo) { j.subiendo = true; j.t = 0.8; Sonido.sfx.rugido(); }
      if (j.t > 0 && j.vuelo < 3.5 && !j.persigue) { j.vuelo = Math.min(3.5, j.vuelo + dt * 5); j.invisible = j.vuelo > 0.6; break; }
      if (!j.persigue) { j.persigue = true; j.t = 2.2 / rapido; }
      if (j.t > 0) { const a = Math.atan2(p.y - j.y, p.x - j.x), d = Math.hypot(p.x - j.x, p.y - j.y); const v = Math.min(d / dt, 120 * rapido); j.x += Math.cos(a) * v * dt; j.y += Math.sin(a) * v * dt; dentro(); break; }
      if (!j.cae) { j.cae = true; j.t = 0.85; avisoSuelo(j.x, j.y, 36, 0.85, D.dano * 1.3, ['#e8e8f0', '#8a8aa0', '#ffffff']); break; }
      j.vuelo = Math.max(0, j.t / 0.85) * 3.5; j.invisible = j.vuelo > 0.6;
      if (j.t <= 0) {
        j.vuelo = 0; j.invisible = false; j.subiendo = j.persigue = j.cae = false;
        j.estado = 'aturdido'; j.t = 1.8; J.temblor = 0.9; Sonido.sfx.temblor();
        particulas(j.x, j.y, 30, RAMPA.ruina.slice(2), { vel: 110, s: 2, vida: 0.8 });
        for (let k = 0; k < (j.fase === 2 ? 10 : 6); k++) lanzar('pluma', j.x, j.y - 8, (k / (j.fase === 2 ? 10 : 6)) * Math.PI * 2, 80, D.dano * 0.4, { vida: 1.8 });
      }
      break;
    case 'aturdido':
      if (Math.random() < 0.15) particulas(j.x, j.y - 44, 1, ['#ffd166', '#ffffff'], { vel: 25, g: 0, brilla: true });
      if (j.t <= 0) { j.estado = 'espera'; j.t = 1.0; }
      break;
    case 'rayos': // rayos donde estás, uno detrás de otro
      if (j.t <= 0) {
        avisoSuelo(p.x, p.y, 16, 0.7, D.dano * 0.9, ['#fff6a0', '#a0c0ff', '#ffffff']);
        J.rayosCielo = (J.rayosCielo || []).concat([{ x: p.x, y: p.y, t: 0.7 }]);
        Sonido.sfx.metal(); j.rafagas -= 0.34; j.t = 0.38 / rapido;
        if (j.rafagas <= 0) { j.estado = 'espera'; j.t = 1.2 / rapido; }
      }
      break;
    case 'viento': // aletea: te empuja hacia atrás y suelta tornados que te siguen despacio
      if (!j.sopla) { j.sopla = true; j.t = 1.6; for (let k = 0; k < (j.fase === 2 ? 3 : 2); k++) { const a = Math.atan2(p.y - j.y, p.x - j.x) + (k - 0.5) * 0.8; lanzar('tornado', j.x + Math.cos(a) * 20, j.y + Math.sin(a) * 14, a, 42, D.dano * 0.5, { busca: 0.7, vida: 4.5, vuela: true }); } Sonido.sfx.zas(); }
      { const a = Math.atan2(p.y - j.y, p.x - j.x), d = Math.hypot(p.x - j.x, p.y - j.y); if (d < 200) { p.kbx += Math.cos(a) * 260 * dt; p.kby += Math.sin(a) * 200 * dt; } }
      if (Math.random() < 0.5) particulas(j.x + (Math.random() - 0.5) * 30, j.y, 1, ['#e8f0ff', '#ffffff'], { vel: 80, g: 0, vida: 0.5 });
      if (j.t <= 0) { j.sopla = false; j.estado = 'espera'; j.t = 1.1; }
      break;
  }
  if (J.rayosCielo) { for (const r of J.rayosCielo) r.t -= dt; J.rayosCielo = J.rayosCielo.filter((r) => r.t > -0.25); }
  if (!j.invisible) contacto(j, D.dano * 0.6);
};
DIBUJAR_JEFE.tamar = (j) => {
  if (j.estado === 'dormido' && !j.invocar) return;
  const x = j.x / T, z = j.y / T + 0.1, alto = (j.vuelo || 0) * GL.KY;
  const sombra = 1.4 + (j.vuelo || 0) * 0.25;
  GL.plano('sombra_l', x, z - 0.1, { tinte: [1, 1, 1, 0.9 - (j.vuelo || 0) * 0.12], escala: sombra });
  const f = j.estado === 'aturdido' ? 4 : j.vuelo > 0.1 ? 2 + (Math.floor(J.t * 8) % 2) : j.estado === 'plumas' || j.estado === 'viento' ? 2 + (Math.floor(J.t * 10) % 2) : Math.floor(J.t * 2) % 2;
  const sacude = j.estado === 'muriendo' ? Math.sin(J.t * 50) * 0.06 : 0;
  GL.sprite(`tamar_${f}`, x + sacude + dxDe(`tamar_${f}`), z, { y: alto, unlit: 0.25, blanco: j.flash > 0 ? 0.85 : 0 });
  GL.sprite(`tamar_${f}_luz`, x + sacude + dxDe(`tamar_${f}`), z + 0.002, { y: alto, unlit: 1, lote: GL.lotes.brillo, tinte: [1, 1, 1, 0.75] });
  GL.halo(x, 1.6 + alto, z, 1.7, [1, 0.95, 0.7], 0.14);
};
// Rayos que caen del cielo (los de Tamar): se dibujan al estallar el aviso.
function dibujarRayosCielo() {
  for (const r of J.rayosCielo || []) {
    if (r.t > 0.05) continue;
    const k = 1 + r.t * 4;
    GL.sprite(`rayo_cielo_${Math.floor(J.t * 20) % 2}`, r.x / T, r.y / T + 0.05, { unlit: 1, lote: GL.lotes.brillo, tinte: [1, 1, 1, Math.max(0, k)] });
    GL.halo(r.x / T, 0.8, r.y / T, 1.4, [0.8, 0.9, 1], 0.5 * Math.max(0, k));
  }
}
