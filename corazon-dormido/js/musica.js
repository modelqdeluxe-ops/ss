'use strict';
// La banda sonora (musica/*.ogg, compuesta con herramientas/musica): un tema por zona y otro para las peleas de
// jefe, con fundidos al cambiar. Se reproduce con <audio> (va leyendo el archivo sin cargarlo entero en memoria).
// En el .html suelto (abierto como archivo) no hay música: el juego sigue igual sin ella.
const MUSICA_REGION = {
  [REGION.SANTUARIO]: 'titulo', [REGION.RAICES]: 'interior', [REGION.ARCILLA]: 'interior', [REGION.RUINAS]: 'interior',
  [REGION.SELVA]: 'selva', [REGION.PRADERA]: 'selva', [REGION.MOHO]: 'cripta', [REGION.SELLADO]: 'interior',
  [REGION.CIENAGA]: 'cienaga', [REGION.CIUDAD]: 'ciudad', [REGION.CRIPTA]: 'cripta',
};
const Musica = (() => {
  const VOL = 0.6;
  let activa = true, pausada = false, bloqueada = false, actual = null, pedido = null, pedidoT = 0;
  try { activa = localStorage.getItem('cd_musica') !== '0'; } catch (e) { /* sin almacenamiento */ }
  const capas = []; // { nombre, el, vol, objetivo }
  // el .html suelto (file://) no lleva la música al lado: sin ella, nada que cargar
  const hay = location.protocol !== 'file:';
  function sonar(n) {
    actual = n;
    if (!hay) return;
    for (const c of capas) c.objetivo = 0;
    let c = capas.find((q) => q.nombre === n);
    if (!c) {
      const el = new Audio(`musica/${n}.ogg`);
      el.loop = true; el.preload = 'auto'; el.volume = 0;
      c = { nombre: n, el, vol: 0, objetivo: 0 };
      capas.push(c);
    }
    c.objetivo = 1;
    if (activa && !pausada) c.el.play().then(() => { bloqueada = false; }, () => { bloqueada = true; });
  }
  // qué tema toca ahora
  function queToca() {
    if (J.estado !== 'juego' && J.estado !== 'muerto') return 'titulo';
    const jefe = Object.values(J.jefes || {}).find((j) => j.activo && j.estado !== 'muerto');
    if (jefe) return jefe.id === 'grul' || jefe.id === 'centinela' ? 'jefe2' : 'jefe';
    const p = J.jug;
    return MUSICA_REGION[M.region[idx(Math.floor(p.x / T), Math.floor(p.y / T))]] || 'interior';
  }
  function actualizar(dt) {
    const quiere = queToca();
    if (quiere !== pedido) { pedido = quiere; pedidoT = 0; }
    pedidoT += dt;
    // a una pelea se entra al momento; al salir de ella o al cambiar de zona se espera un poco (las fronteras)
    const espera = !actual || quiere.startsWith('jefe') ? 0 : actual.startsWith('jefe') ? 2 : 3.5;
    if (quiere !== actual && pedidoT >= espera) sonar(quiere);
    for (const c of capas) {
      const v = c.objetivo > c.vol ? 1 / 2.5 : 1 / 1.8;
      c.vol += Math.sign(c.objetivo - c.vol) * Math.min(Math.abs(c.objetivo - c.vol), v * dt);
      c.el.volume = Math.max(0, Math.min(1, c.vol * VOL * (activa ? 1 : 0)));
    }
    // las que ya se apagaron se sueltan
    for (let k = capas.length - 1; k >= 0; k--) if (capas[k].objetivo === 0 && capas[k].vol <= 0) { capas[k].el.pause(); capas[k].el.removeAttribute('src'); capas[k].el.load(); capas.splice(k, 1); }
  }
  // el navegador no deja sonar nada hasta que se toca la pantalla
  function tocar() {
    if (!bloqueada || !activa || pausada) return;
    const c = capas.find((q) => q.nombre === actual);
    if (c) c.el.play().then(() => { bloqueada = false; }, () => {});
  }
  function alternar() {
    activa = !activa;
    try { localStorage.setItem('cd_musica', activa ? '1' : '0'); } catch (e) { /* sin almacenamiento */ }
    for (const c of capas) { if (activa && !pausada) c.el.play().catch(() => {}); else c.el.pause(); }
    return activa;
  }
  function pausar() { pausada = true; for (const c of capas) c.el.pause(); }
  function seguir() { pausada = false; if (activa) for (const c of capas) c.el.play().catch(() => { bloqueada = true; }); }
  return { actualizar, tocar, alternar, pausar, seguir, get activa() { return activa; }, get actual() { return actual; } };
})();
