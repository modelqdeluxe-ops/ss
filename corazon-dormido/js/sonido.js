'use strict';
// Sonidos con WebAudio. Los efectos son muestras (sonidos/*.ogg, sintetizadas con herramientas/musica/efectos.py)
// con variantes y un poco de tono al azar; si no están (el .html suelto), se sintetizan al momento como antes.
// Y el ambiente de la cueva: un rumor grave, gotas con eco y, sin banda sonora, una cajita de música.
const Sonido = (() => {
  let ac = null, maestro = null, eco = null, bufRuido = null, activo = true;
  try { activo = localStorage.getItem('cd_sonido') !== '0'; } catch (e) { /* sin almacenamiento */ }

  // muestras: nombre → cuántas variantes
  const LISTA = { picar: 4, romper: 2, excavar: 2, metal: 2, golpe: 4, zas: 3, dano: 2, muere: 3, alerta: 3, rugido: 1, temblor: 1, latido: 1,
    recoger: 3, crear: 1, comer: 1, colocar: 2, error: 1, menu: 1, nivel: 1, victoria: 1, paso_piedra: 4, paso_hierba: 4, paso_agua: 3 };
  const VOL = { menu: 0.5, paso_piedra: 0.45, paso_hierba: 0.4, paso_agua: 0.5, recoger: 0.7, error: 0.6, latido: 0.9, rugido: 1, victoria: 0.9 };
  const muestras = {};
  function cargarMuestras() {
    if (location.protocol === 'file:') return; // el .html suelto: sin muestras (fetch no puede leer archivos)
    for (const [n, k] of Object.entries(LISTA)) {
      muestras[n] = [];
      for (let v = 0; v < k; v++) {
        fetch(`sonidos/${k > 1 ? `${n}_${v}` : n}.ogg`).then((r) => (r.ok ? r.arrayBuffer() : Promise.reject())).then((b) => ac.decodeAudioData(b))
          .then((buf) => { muestras[n][v] = buf; }, () => { /* sin el archivo: se sintetiza */ });
      }
    }
  }
  // true si había muestra y ha sonado
  function muestra(n, vol = 1, tonoBase = 1) {
    const lista = muestras[n];
    if (!ac || !activo || !lista) return false;
    const vs = lista.filter(Boolean);
    if (!vs.length) return false;
    const s = ac.createBufferSource(), g = ac.createGain();
    s.buffer = vs[Math.floor(Math.random() * vs.length)];
    s.playbackRate.value = tonoBase * (0.94 + Math.random() * 0.12);
    g.gain.value = vol * (VOL[n] ?? 0.8);
    s.connect(g); g.connect(maestro); s.start();
    return true;
  }

  function iniciar() {
    if (ac) { if (ac.state === 'suspended') ac.resume(); return; }
    try { ac = new (window.AudioContext || window.webkitAudioContext)(); } catch (e) { return; }
    cargarMuestras();
    maestro = ac.createGain(); maestro.gain.value = activo ? 0.55 : 0; maestro.connect(ac.destination);
    // eco de cueva compartido
    eco = ac.createDelay(1); eco.delayTime.value = 0.23;
    const fb = ac.createGain(); fb.gain.value = 0.38;
    const filtro = ac.createBiquadFilter(); filtro.type = 'lowpass'; filtro.frequency.value = 1800;
    eco.connect(filtro); filtro.connect(fb); fb.connect(eco); filtro.connect(maestro);
    bufRuido = ac.createBuffer(1, ac.sampleRate, ac.sampleRate);
    const d = bufRuido.getChannelData(0);
    for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
    ambiente();
  }

  function tono({ f = 440, f2 = null, dur = 0.1, tipo = 'square', vol = 0.15, ataque = 0.004, retraso = 0, conEco = 0 }) {
    if (!ac || !activo) return;
    const t = ac.currentTime + retraso, o = ac.createOscillator(), g = ac.createGain();
    o.type = tipo; o.frequency.setValueAtTime(f, t);
    if (f2) o.frequency.exponentialRampToValueAtTime(Math.max(20, f2), t + dur);
    g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(vol, t + ataque);
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    o.connect(g); g.connect(maestro);
    if (conEco) { const ge = ac.createGain(); ge.gain.value = conEco; g.connect(ge); ge.connect(eco); }
    o.start(t); o.stop(t + dur + 0.02);
  }
  function ruido({ dur = 0.1, vol = 0.2, f = 1200, f2 = null, q = 0.8, tipo = 'lowpass', retraso = 0 }) {
    if (!ac || !activo) return;
    const t = ac.currentTime + retraso, s = ac.createBufferSource(), fl = ac.createBiquadFilter(), g = ac.createGain();
    s.buffer = bufRuido; fl.type = tipo; fl.Q.value = q; fl.frequency.setValueAtTime(f, t);
    if (f2) fl.frequency.exponentialRampToValueAtTime(f2, t + dur);
    g.gain.setValueAtTime(vol, t); g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    s.connect(fl); fl.connect(g); g.connect(maestro);
    s.start(t, Math.random() * 0.5); s.stop(t + dur + 0.02);
  }

  const notas = [220, 261.6, 293.7, 329.6, 392, 440, 523.3, 587.3];
  const sintetizados = {
    picar: () => { ruido({ dur: 0.07, vol: 0.3, f: 2200, f2: 700, tipo: 'bandpass', q: 1.2 }); tono({ f: 200 + Math.random() * 40, f2: 110, dur: 0.06, tipo: 'triangle', vol: 0.2 }); },
    romper: () => { ruido({ dur: 0.28, vol: 0.4, f: 1400, f2: 200 }); tono({ f: 120, f2: 50, dur: 0.2, tipo: 'square', vol: 0.08 }); },
    metal: () => { tono({ f: 1300, f2: 1250, dur: 0.12, tipo: 'triangle', vol: 0.12 }); tono({ f: 2100, dur: 0.08, tipo: 'sine', vol: 0.06 }); },
    golpe: () => { ruido({ dur: 0.08, vol: 0.35, f: 900, f2: 300 }); tono({ f: 160, f2: 70, dur: 0.08, tipo: 'square', vol: 0.12 }); },
    zas: () => ruido({ dur: 0.09, vol: 0.12, f: 3000, f2: 900, tipo: 'bandpass', q: 0.7 }),
    dano: () => { tono({ f: 300, f2: 90, dur: 0.22, tipo: 'sawtooth', vol: 0.15 }); ruido({ dur: 0.12, vol: 0.2, f: 600 }); },
    muere: () => { tono({ f: 400, f2: 80, dur: 0.25, tipo: 'square', vol: 0.1 }); ruido({ dur: 0.2, vol: 0.25, f: 1600, f2: 300 }); },
    recoger: (n = 0) => tono({ f: 700 + Math.min(n, 8) * 60, f2: 1100 + Math.min(n, 8) * 60, dur: 0.07, tipo: 'square', vol: 0.07 }),
    crear: () => { tono({ f: 523, dur: 0.08, tipo: 'square', vol: 0.08 }); tono({ f: 784, dur: 0.12, tipo: 'square', vol: 0.08, retraso: 0.07 }); },
    comer: () => { for (let i = 0; i < 3; i++) ruido({ dur: 0.05, vol: 0.2, f: 1800, tipo: 'bandpass', retraso: i * 0.09 }); },
    colocar: () => { tono({ f: 140, f2: 90, dur: 0.1, tipo: 'triangle', vol: 0.2 }); ruido({ dur: 0.06, vol: 0.15, f: 800 }); },
    error: () => { tono({ f: 180, dur: 0.08, tipo: 'square', vol: 0.08 }); tono({ f: 140, dur: 0.1, tipo: 'square', vol: 0.08, retraso: 0.09 }); },
    menu: () => tono({ f: 880, dur: 0.04, tipo: 'square', vol: 0.05 }),
    alerta: () => { tono({ f: 160, f2: 95, dur: 0.32, tipo: 'sawtooth', vol: 0.07 }); ruido({ dur: 0.28, vol: 0.12, f: 600, f2: 180 }); },
    rugido: () => { tono({ f: 110, f2: 55, dur: 0.9, tipo: 'sawtooth', vol: 0.2 }); ruido({ dur: 0.9, vol: 0.3, f: 500, f2: 120 }); },
    excavar: () => ruido({ dur: 0.3, vol: 0.18, f: 400, f2: 150 }),
    temblor: () => { ruido({ dur: 0.5, vol: 0.4, f: 300, f2: 60 }); tono({ f: 60, f2: 30, dur: 0.5, tipo: 'sine', vol: 0.3 }); },
    latido: () => { tono({ f: 70, f2: 40, dur: 0.25, tipo: 'sine', vol: 0.4 }); tono({ f: 70, f2: 40, dur: 0.25, tipo: 'sine', vol: 0.3, retraso: 0.28 }); },
    nivel: () => [0, 2, 4, 7].forEach((k, i) => tono({ f: notas[k], dur: 0.25, tipo: 'triangle', vol: 0.12, retraso: i * 0.12, conEco: 0.3 })),
    victoria: () => [0, 2, 4, 5, 7].forEach((k, i) => tono({ f: notas[k] * 2, dur: 0.4, tipo: 'triangle', vol: 0.12, retraso: i * 0.15, conEco: 0.4 })),
    paso: () => {},
  };
  // cada efecto: la muestra si está; si no, el sintetizado
  const sfx = {};
  for (const [n, fn] of Object.entries(sintetizados)) sfx[n] = (...a) => { if (!muestra(n)) fn(...a); };
  sfx.recoger = (n = 0) => { if (!muestra('recoger', 1, 1 + Math.min(n, 8) * 0.03)) sintetizados.recoger(n); };
  // pasos según el suelo que pisas
  sfx.paso = (tipo) => muestra('paso_' + tipo);

  function ambiente() {
    // rumor grave de fondo
    const s = ac.createBufferSource(); s.buffer = bufRuido; s.loop = true;
    const fl = ac.createBiquadFilter(); fl.type = 'lowpass'; fl.frequency.value = 140;
    const g = ac.createGain(); g.gain.value = 0.12;
    s.connect(fl); fl.connect(g); g.connect(maestro); s.start();
    const gota = () => {
      if (activo && document.visibilityState === 'visible') tono({ f: 1300 + Math.random() * 900, f2: 500, dur: 0.07, tipo: 'sine', vol: 0.05, conEco: 0.6 });
      setTimeout(gota, 2500 + Math.random() * 6000);
    };
    const cajita = () => {
      if (activo && document.visibilityState === 'visible' && !(typeof Musica !== 'undefined' && Musica.activa)) {
        const base = Math.floor(Math.random() * 3);
        for (let i = 0; i < 4; i++) tono({ f: notas[base + [0, 2, 4, 3][i]] * 2, dur: 0.6, tipo: 'triangle', vol: 0.035, retraso: i * 0.42, conEco: 0.5 });
      }
      setTimeout(cajita, 14000 + Math.random() * 16000);
    };
    setTimeout(gota, 3000); setTimeout(cajita, 8000);
  }

  function alternar() {
    activo = !activo;
    try { localStorage.setItem('cd_sonido', activo ? '1' : '0'); } catch (e) { /* sin almacenamiento */ }
    if (maestro) maestro.gain.value = activo ? 0.55 : 0;
    return activo;
  }
  // la app se va al fondo / vuelve
  function pausar() { if (ac && ac.state === 'running') ac.suspend(); }
  function seguir() { if (ac && ac.state === 'suspended') ac.resume(); }
  return { iniciar, sfx, alternar, pausar, seguir, get activo() { return activo; }, get muestras() { return Object.values(muestras).reduce((a, l) => a + l.filter(Boolean).length, 0); } };
})();
