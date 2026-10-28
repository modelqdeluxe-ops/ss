'use strict';
// Guardar y cargar el mundo grande (desde la 0.7). El mundo mide 2048×2048 casillas: guardarlo entero cada vez no
// cabe en localStorage ni vale la pena. Se parte en trozos de 64×64; al cargar se vuelve a generar con la semilla y
// encima se ponen los trozos guardados. Solo se guardan los trozos que el jugador ha tocado: lo picado, lo
// construido, los cofres abiertos y lo que ha visto (el mapa). Todo va a IndexedDB en una sola transacción.
//
// Si cambia lo que sale del generador para una semilla, hay que subir GEN_MUNDO y decidir qué hacer con las
// partidas viejas: los trozos que nunca se tocaron salen ya con el generador nuevo (añadir cosas donde nadie ha
// estado es gratis), pero lo ya visitado se queda como se guardó.

const TROZO_G = 64, NT_G = N_MUNDO / TROZO_G;
const GEN_MUNDO = 5;
const CAPAS_G = ['muro', 'mena', 'piso', 'visto', 'region'];

const trozoDe = (x, y) => ((y / TROZO_G) | 0) * NT_G + ((x / TROZO_G) | 0);
// Lo llaman todas las funciones que cambian una casilla después de generar el mundo.
function tocarTrozo(x, y) {
  if (M.generando) return;
  const c = trozoDe(x, y);
  M.tocados[c] = 1; M.sucios.add(c);
}
function tocarTrozoI(i) { const x = i % N_MUNDO; tocarTrozo(x, (i - x) / N_MUNDO); }

// Las cinco capas de un trozo, comprimidas por tramos (valor, cuántos): la mayoría es roca o lo mismo seguido.
function capasTrozo(c) {
  const cx = (c % NT_G) * TROZO_G, cy = ((c / NT_G) | 0) * TROZO_G, out = [];
  for (const k of CAPAS_G) {
    const a = M[k];
    let v = -1, n = 0;
    for (let y = cy; y < cy + TROZO_G; y++) {
      const b = y * N_MUNDO + cx;
      for (let x = 0; x < TROZO_G; x++) {
        const w = a[b + x];
        if (w === v && n < 255) n++;
        else { if (n) out.push(v, n); v = w; n = 1; }
      }
    }
    out.push(v, n);
  }
  return new Uint8Array(out);
}
function ponerCapasTrozo(c, u8) {
  const cx = (c % NT_G) * TROZO_G, cy = ((c / NT_G) | 0) * TROZO_G, T2 = TROZO_G * TROZO_G;
  let p = 0, capa = 0, k = 0;
  while (p < u8.length && capa < CAPAS_G.length) {
    const v = u8[p], n = u8[p + 1], a = M[CAPAS_G[capa]];
    p += 2;
    for (let j = 0; j < n; j++, k++) { const ly = (k / TROZO_G) | 0; a[(cy + ly) * N_MUNDO + cx + (k - ly * TROZO_G)] = v; }
    if (k >= T2) { capa++; k = 0; }
  }
}
// Pone un trozo guardado sobre el mundo recién generado: sus casillas y sus objetos (los generados se quitan).
function aplicarTrozo(c, reg) {
  ponerCapasTrozo(c, reg.capas);
  const cx = (c % NT_G) * TROZO_G, cy = ((c / NT_G) | 0) * TROZO_G;
  for (let y = cy; y < cy + TROZO_G; y++) for (let x = cx; x < cx + TROZO_G; x++) { const i = idx(x, y); if (M.objetos.delete(i)) M.luces.delete(i); }
  const lista = JSON.parse(reg.obj);
  M.generando = true;
  for (const [x, y, tipo, extra, datos] of lista) { if (!OBJ[tipo]) continue; const o = ponerObjeto(x, y, tipo, extra); if (datos) o.datos = datos; }
  M.generando = false;
  M.tocados[c] = 1; M.objGuardados.set(c, reg.obj);
}

// Los trozos que hay que escribir: los que han cambiado de casillas y los que tienen objetos distintos de lo guardado.
function trozosParaGuardar() {
  const porTrozo = new Map();
  for (const o of M.objetos.values()) {
    const c = trozoDe(o.x, o.y);
    if (!M.tocados[c]) continue;
    let a = porTrozo.get(c);
    if (!a) porTrozo.set(c, (a = []));
    a.push([o.x, o.y, o.tipo, o.extra, o.datos || null]);
  }
  const salen = [];
  for (let c = 0; c < M.tocados.length; c++) {
    if (!M.tocados[c]) continue;
    const obj = JSON.stringify(porTrozo.get(c) || []);
    if (!M.sucios.has(c) && obj === M.objGuardados.get(c)) continue;
    salen.push([c, { capas: capasTrozo(c), obj }]);
    M.objGuardados.set(c, obj);
  }
  M.sucios.clear();
  return salen;
}

// ---------------------------------------------------------------- IndexedDB

const Guardado = (() => {
  let base = null;
  const abrir = () => base || (base = new Promise((ok, mal) => {
    const r = indexedDB.open('corazon-dormido-partida', 1);
    r.onupgradeneeded = () => { r.result.createObjectStore('meta'); r.result.createObjectStore('trozos'); };
    r.onsuccess = () => ok(r.result);
    r.onerror = () => { base = null; mal(r.error); };
  }));
  const fin = (tx) => new Promise((ok, mal) => { tx.oncomplete = () => ok(true); tx.onerror = () => mal(tx.error); tx.onabort = () => mal(tx.error); });
  const pedir = (q) => new Promise((ok, mal) => { q.onsuccess = () => ok(q.result); q.onerror = () => mal(q.error); });
  return {
    async escribir(meta, trozos) {
      const db = await abrir(), tx = db.transaction(['meta', 'trozos'], 'readwrite');
      const st = tx.objectStore('trozos');
      for (const [c, reg] of trozos) st.put(reg, c);
      tx.objectStore('meta').put(meta, 'partida');
      return fin(tx);
    },
    async leer() {
      const db = await abrir(), tx = db.transaction(['meta', 'trozos']);
      const meta = await pedir(tx.objectStore('meta').get('partida'));
      if (!meta) return null;
      const st = tx.objectStore('trozos');
      const [claves, valores] = await Promise.all([pedir(st.getAllKeys()), pedir(st.getAll())]);
      return { meta, trozos: claves.map((c, k) => [c, valores[k]]) };
    },
    async hay() { const db = await abrir(); return !!(await pedir(db.transaction('meta').objectStore('meta').getKey('partida'))); },
    async borrar() {
      const db = await abrir(), tx = db.transaction(['meta', 'trozos'], 'readwrite');
      tx.objectStore('meta').clear(); tx.objectStore('trozos').clear();
      return fin(tx);
    },
  };
})();
