// Junta los precios de mercado que Rumentis muestra y los publica para la app.
// Corre en GitHub Actions (tiene internet); la app los lee de la Release "mercado" por la API de GitHub.
// Fuentes públicas, sin llaves:
//   - USDA AMS LMR Datamart, informe LM_CT150 (slug 2477): 5 Area Weekly Weighted Average Direct Slaughter Cattle.
//   - Ganado de engorde en EE. UU. (Oklahoma National Stockyards, la referencia nacional del ternero):
//     historia mensual de USDA ERS (Livestock prices, xlsx) y cada semana el reporte USDA AMS_1280 (PDF, se lee con
//     pdftotext de poppler-utils).
//   - Cepea/Esalq: indicadores del boi gordo (SP), bezerro (MS) y milho (Campinas).
//   - Banco Central do Brasil, serie SGS 1: dólar PTAX (venta).
//   - ExchangeRate-API (open.er-api.com): tipos de cambio contra el dólar.
// Guarda la historia: cada corrida suma los días nuevos a lo que ya estaba publicado (hasta 2 años).
'use strict';
const fs = require('fs');

const UA = { 'User-Agent': 'Rumentis/1.0 (+https://github.com/modelqdeluxe-ops/ss)', 'Accept': '*/*' };
const DIAS_MAX = 2 * 366; // la Release admite unos 125 000 caracteres

async function traer(url, tipo = 'text', intentos = 3) {
  let ult;
  for (let i = 0; i < intentos; i++) {
    try {
      const r = await fetch(url, { headers: UA, signal: AbortSignal.timeout(45000) });
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      return tipo === 'json' ? await r.json() : await r.text();
    } catch (e) { ult = e; await new Promise(res => setTimeout(res, 3000 * (i + 1))); }
  }
  throw new Error(`${url}: ${ult && ult.message}`);
}

// "09/21/2026" → "2026-09-21"; "25/09/2026" → "2026-09-25"
const deMDY = s => { const m = /^(\d\d)\/(\d\d)\/(\d{4})$/.exec(String(s || '').trim()); return m ? `${m[3]}-${m[1]}-${m[2]}` : null; };
const deDMY = s => { const m = /^(\d\d)\/(\d\d)\/(\d{4})$/.exec(String(s || '').trim()); return m ? `${m[3]}-${m[2]}-${m[1]}` : null; };
const num = s => { if (s == null) return null; const v = +String(s).replace(/,/g, ''); return isFinite(v) ? v : null; };
const numBR = s => { if (s == null) return null; const v = +String(s).replace(/\./g, '').replace(',', '.'); return isFinite(v) ? v : null; };

// une series [fecha, valor]: lo nuevo manda, sin repetidos, en orden, recortada
function unir(vieja, nueva) {
  const m = new Map((vieja || []).filter(p => Array.isArray(p) && p[0] && isFinite(p[1])));
  for (const [f, v] of nueva || []) if (f && isFinite(v)) m.set(f, v);
  const lim = new Date(Date.now() - DIAS_MAX * 864e5).toISOString().slice(0, 10);
  return [...m.entries()].filter(([f]) => f >= lim).sort((a, b) => a[0] < b[0] ? -1 : 1);
}

/* ---------- EE. UU.: USDA LM_CT150 ---------- */
async function usda() {
  const j = await traer('https://mpr.datamart.ams.usda.gov/services/v1.1/reports/2477/History', 'json');
  const filas = (j.results || []).filter(r => /WEEKLY WEIGHTED AVERAGES/i.test(r.current_period || '') && /^steer$/i.test((r.class_description || '').trim()));
  const serie = basis => filas.filter(r => (r.selling_basis_desc || '').trim().toLowerCase() === basis)
    .map(r => [deMDY(r.report_date), num(r.weighted_avg_price), num(r.head_count), num(r.weight_range_avg)])
    .filter(p => p[0] && p[1] > 50 && p[1] < 1000);
  const vivo = serie('live'), canal = serie('dressed');
  if (vivo.length < 4) throw new Error('USDA: pocas filas de novillos en pie (' + vivo.length + ')');
  return {
    novillo_gordo_pie: { nombre: 'Novillo gordo en pie', unidad: 'USD/cwt', base: 'vivo', region: '5 áreas (TX/OK/NM, KS, NE, CO, IA/MN)', frecuencia: 'semanal',
      fuente: 'USDA AMS, LM_CT150 (5 Area Weekly Weighted Average Direct Slaughter Cattle), novillos, promedio ponderado',
      url: 'https://mpr.datamart.ams.usda.gov/', serie: vivo.map(p => [p[0], p[1]]), cabezas: vivo[0] && vivo[0][2], peso_lb: vivo[0] && vivo[0][3] },
    novillo_gordo_canal: canal.length >= 4 ? { nombre: 'Novillo gordo en canal', unidad: 'USD/cwt', base: 'canal', region: '5 áreas', frecuencia: 'semanal',
      fuente: 'USDA AMS, LM_CT150, novillos en canal (dressed delivered), promedio ponderado', url: 'https://mpr.datamart.ams.usda.gov/', serie: canal.map(p => [p[0], p[1]]) } : null
  };
}

/* ---------- EE. UU.: ganado de engorde, Oklahoma City ----------
   Novillos Medium and Large #1 de 500-550 lb (ternero) y 750-800 lb (novillo de engorde), en USD/cwt. */
async function traerBin(url) {
  let ult;
  for (let i = 0; i < 3; i++) {
    try { const r = await fetch(url, { headers: UA, signal: AbortSignal.timeout(60000) }); if (!r.ok) throw new Error(`HTTP ${r.status}`); return Buffer.from(await r.arrayBuffer()); }
    catch (e) { ult = e; await new Promise(res => setTimeout(res, 3000 * (i + 1))); }
  }
  throw new Error(`${url}: ${ult && ult.message}`);
}
const deSerial = n => new Date(Date.UTC(1899, 11, 30) + Math.round(n) * 864e5).toISOString().slice(0, 10);
const MESES_EN = { jan: 1, feb: 2, mar: 3, apr: 4, may: 5, jun: 6, jul: 7, aug: 8, sep: 9, oct: 10, nov: 11, dec: 12 };
// "Aug-26/*" → "2026-08-01"
const deMesEn = t => { const m = /^([A-Za-z]{3})-(\d{2})/.exec(String(t || '').trim()); if (!m || !MESES_EN[m[1].toLowerCase()]) return null; return `20${m[2]}-${String(MESES_EN[m[1].toLowerCase()]).padStart(2, '0')}-01`; };
// Excel de ERS: hoja Historical (desde 2000) y hoja Current (los últimos meses, con el preliminar)
function ersEngorde(buf) {
  const X = require('xlsx'), wb = X.read(buf, { type: 'buffer' }), out = { t500: new Map(), t750: new Map() };
  const H = wb.Sheets.Historical && X.utils.sheet_to_json(wb.Sheets.Historical, { header: 1 });
  if (H) {
    // columnas de novillos: la fila de rangos de peso dice "500-550 lbs" y "750-800 lbs" bajo "Steers: medium and large #1"
    const iSub = H.findIndex(r => r && r.some(c => /500-550 lbs/i.test(String(c)))), sub = H[iSub] || [], cab = H[iSub - 1] || [];
    const col = t => sub.findIndex((c, j) => new RegExp(t).test(String(c)) && /steers/i.test(String(cab[j] || '')));
    const c5 = col('500-550'), c7 = col('750-800');
    for (const r of H) if (r && typeof r[0] === 'number' && r[0] > 30000) {
      const f = deSerial(r[0]);
      if (c5 > 0 && typeof r[c5] === 'number') out.t500.set(f, Math.round(r[c5] * 100) / 100);
      if (c7 > 0 && typeof r[c7] === 'number') out.t750.set(f, Math.round(r[c7] * 100) / 100);
    }
  }
  const C = wb.Sheets.Current && X.utils.sheet_to_json(wb.Sheets.Current, { header: 1 });
  if (C) {
    const cab = C[0] || [], fechas = cab.map(c => typeof c === 'number' ? deSerial(c) : deMesEn(c));
    const i0 = C.findIndex(r => r && /feeder cattle, oklahoma city/i.test(String(r[0] || '')));
    if (i0 >= 0) {
      let steers = false;
      for (let i = i0 + 1; i < Math.min(C.length, i0 + 14); i++) {
        const r = C[i] || [], t = String(r[0] || '');
        if (/steers/i.test(t)) steers = true; else if (/heifers/i.test(t)) steers = false;
        const k = steers && /500-550/.test(t) ? 't500' : steers && /750-800/.test(t) ? 't750' : null;
        if (k) r.forEach((v, j) => { if (j && fechas[j] && typeof v === 'number') out[k].set(fechas[j], Math.round(v * 100) / 100); });
      }
    }
  }
  return { t500: [...out.t500].sort(), t750: [...out.t750].sort() };
}
/* reporte semanal AMS_1280 ya pasado a texto: promedio ponderado por cabezas de las filas de novillos M&L 1
   con peso promedio en el rango; primero las filas sin comentario (Fleshy, Thin Fleshed, Fancy, Unweaned...) */
function okcSemana(txt) {
  const m = /Weighted Average Report for (\d{1,2})\/(\d{1,2})\/(\d{4})/i.exec(txt);
  if (!m) throw new Error('OKC: sin fecha del reporte');
  const fecha = `${m[3]}-${m[1].padStart(2, '0')}-${m[2].padStart(2, '0')}`;
  const filas = [];let dentro = false;
  for (const l of txt.split(/\r?\n/)) {
    if (/STEERS\s*-\s*Medium and Large 1\s*\(Per Cwt/i.test(l)) { dentro = true; continue; }
    if (/\(Per Cwt|^\s*Source:|^\s*(HEIFERS|BULLS|SLAUGHTER|STEERS)\b/i.test(l)) { dentro = false; continue; }
    if (!dentro) continue;
    const r = /^\s*(\d+)\s+(\d{3,4})(?:-(\d{3,4}))?\s+(\d{3,4})\s+([\d.]+)(?:-([\d.]+))?\s+([\d.]+)\s*(.*)$/.exec(l);
    if (r) filas.push({ cab: +r[1], peso: +r[4], precio: +r[7], nota: r[8].trim() });
  }
  if (!filas.length) throw new Error('OKC: no encontré filas de novillos Medium and Large 1');
  const prom = (a, b) => {
    const en = filas.filter(f => f.peso >= a && f.peso <= b && f.precio > 50 && f.precio < 1000);
    const lisas = en.filter(f => !f.nota), usar = lisas.length ? lisas : en, cab = usar.reduce((s, f) => s + f.cab, 0);
    return cab ? Math.round(usar.reduce((s, f) => s + f.precio * f.cab, 0) / cab * 100) / 100 : null;
  };
  return { fecha, t500: prom(500, 550), t750: prom(750, 800), filas: filas.length };
}
async function engorde() {
  const t500 = new Map(), t750 = new Map(), avisos = [];
  try {
    const pag = await traer('https://www.ers.usda.gov/data-products/livestock-and-meat-domestic-data');
    const u = (/href="(\/media\/\d+\/livestock-prices\.xlsx[^"]*)"/.exec(pag) || [])[1];
    if (!u) throw new Error('no encontré el enlace del xlsx');
    const e = ersEngorde(await traerBin('https://www.ers.usda.gov' + u.replace(/&amp;/g, '&')));
    e.t500.forEach(([f, v]) => t500.set(f, v)); e.t750.forEach(([f, v]) => t750.set(f, v));
    console.log('ERS engorde:', e.t500.length, 'meses de 500-550 lb,', e.t750.length, 'de 750-800 lb; último', e.t500[e.t500.length - 1]);
  } catch (err) { avisos.push('ERS: ' + err.message); }
  try {
    const pdf = await traerBin('https://www.ams.usda.gov/mnreports/AMS_1280.pdf');
    fs.writeFileSync('okc.pdf', pdf);
    const txt = require('child_process').execFileSync('pdftotext', ['-layout', 'okc.pdf', '-']).toString();
    const o = okcSemana(txt);
    console.log('Oklahoma City', o.fecha, 'filas', o.filas, '500-550 lb:', o.t500, '750-800 lb:', o.t750);
    if (o.t500) t500.set(o.fecha, o.t500); if (o.t750) t750.set(o.fecha, o.t750);
  } catch (err) { avisos.push('Oklahoma City: ' + err.message); }
  if (!t500.size && !t750.size) throw new Error(avisos.join('; '));
  const base = { unidad: 'USD/cwt', base: 'vivo', region: 'Oklahoma City (Oklahoma National Stockyards)', frecuencia: 'semanal',
    fuente: 'USDA AMS (reporte AMS_1280, subasta de Oklahoma City) y USDA ERS (Livestock prices), novillos Medium and Large #1', url: 'https://www.ams.usda.gov/mnreports/AMS_1280.pdf' };
  const orden = m => [...m.entries()].sort((a, b) => a[0] < b[0] ? -1 : 1);
  return { avisos, ternero: t500.size ? { ...base, nombre: 'Ternero de engorde (500-550 lb)', serie: orden(t500) } : null,
    novillo: t750.size ? { ...base, nombre: 'Novillo de engorde (750-800 lb)', serie: orden(t750) } : null };
}

/* ---------- Brasil: Cepea ---------- */
async function cepea(pagina) {
  const t = await traer(`https://www.cepea.org.br/br/indicador/${pagina}.aspx`);
  const tabla = /<table[^>]*id="imagenet-indicador1"[\s\S]*?<\/table>/i.exec(t);
  if (!tabla) throw new Error(`Cepea ${pagina}: no encontré la tabla del indicador`);
  const filas = [...tabla[0].matchAll(/<tr>\s*<td>(\d\d\/\d\d\/\d{4})<\/td>\s*<td>([\d.,]+)<\/td>/g)].map(m => [deDMY(m[1]), numBR(m[2])]).filter(p => p[0] && p[1] > 0);
  if (!filas.length) throw new Error(`Cepea ${pagina}: tabla sin datos`);
  // la serie histórica (planilla) del mismo indicador, si se puede leer
  let hist = [];
  try { hist = await cepeaSerie(t, pagina); } catch (e) { console.warn(`Cepea ${pagina}: sin serie histórica (${e.message})`); }
  return hist.concat(filas);
}
async function cepeaSerie(html, pagina) {
  const m = /href="([^"]*indicador\/series\/[^"]+)"/i.exec(html);
  if (!m) throw new Error('sin enlace a la serie');
  const url = new URL(m[1].replace(/&amp;/g, '&'), 'https://www.cepea.org.br/').href;
  let XLSX; try { XLSX = require('xlsx'); } catch (e) { throw new Error('falta el paquete xlsx'); }
  const r = await fetch(url, { headers: UA, signal: AbortSignal.timeout(60000) });
  if (!r.ok) throw new Error(`HTTP ${r.status} en ${url}`);
  const libro = XLSX.read(Buffer.from(await r.arrayBuffer()), { type: 'buffer' });
  const hoja = libro.Sheets[libro.SheetNames[0]];
  const filas = XLSX.utils.sheet_to_json(hoja, { header: 1, raw: true });
  const out = [];
  for (const f of filas) {
    if (!f || f.length < 2) continue;
    let d = f[0], v = f[1];
    if (typeof d === 'number') { const o = XLSX.SSF.parse_date_code(d); d = o ? `${o.y}-${String(o.m).padStart(2, '0')}-${String(o.d).padStart(2, '0')}` : null; }
    else if (typeof d === 'string') d = deDMY(d.trim()) || (/^\d\d\/\d{4}$/.test(d.trim()) ? null : null);
    if (typeof v === 'string') v = numBR(v);
    if (d && typeof v === 'number' && v > 0) out.push([d, v]);
  }
  console.log(`Cepea ${pagina}: serie ${url} → ${out.length} filas (${out[0] && out[0][0]} a ${out[out.length - 1] && out[out.length - 1][0]})`);
  if (!out.length) throw new Error('planilla sin filas con fecha');
  return out;
}
async function brasil() {
  const [boi, bez, mil] = await Promise.allSettled([cepea('boi-gordo'), cepea('bezerro'), cepea('milho')]);
  const o = {};
  if (boi.status === 'fulfilled') o.boi_gordo = { nombre: 'Boi gordo', unidad: 'BRL/@', base: 'canal (arroba de 15 kg)', region: 'São Paulo', frecuencia: 'diaria',
    fuente: 'Indicador do Boi Gordo Cepea/Esalq (à vista, São Paulo)', url: 'https://www.cepea.org.br/br/indicador/boi-gordo.aspx', serie: boi.value };
  else console.warn(boi.reason.message);
  if (bez.status === 'fulfilled') o.bezerro = { nombre: 'Bezerro', unidad: 'BRL/cabeza', base: 'por cabeza (8 a 12 meses)', region: 'Mato Grosso do Sul', frecuencia: 'diaria',
    fuente: 'Indicador do Bezerro Cepea/Esalq (Mato Grosso do Sul)', url: 'https://www.cepea.org.br/br/indicador/bezerro.aspx', serie: bez.value };
  else console.warn(bez.reason.message);
  if (mil.status === 'fulfilled') o.milho = { nombre: 'Milho', unidad: 'BRL/saca 60 kg', base: 'saca de 60 kg', region: 'Campinas (SP)', frecuencia: 'diaria',
    fuente: 'Indicador do Milho Esalq/B3 (Campinas)', url: 'https://www.cepea.org.br/br/indicador/milho.aspx', serie: mil.value };
  else console.warn(mil.reason.message);
  return o;
}

/* ---------- tipos de cambio ---------- */
async function cambios() {
  const j = await traer('https://open.er-api.com/v6/latest/USD', 'json');
  if (j.result !== 'success' || !j.rates) throw new Error('open.er-api: respuesta sin tasas');
  const fecha = new Date((j.time_last_update_unix || Date.now() / 1000) * 1000).toISOString().slice(0, 10);
  const monedas = ['HNL', 'GTQ', 'NIO', 'CRC', 'PAB', 'BZD', 'DOP', 'MXN', 'CAD', 'COP', 'VES', 'PEN', 'BOB', 'BRL', 'PYG', 'UYU', 'ARS', 'CLP', 'EUR', 'AUD', 'NZD', 'ZAR', 'PHP', 'INR'];
  const r = {}; for (const m of monedas) if (isFinite(j.rates[m])) r[m] = j.rates[m];
  return { fecha, base: 'USD', tasas: r, fuente: 'ExchangeRate-API (open.er-api.com), tasas de referencia diarias', url: 'https://www.exchangerate-api.com' };
}
async function ptax() {
  const j = await traer('https://api.bcb.gov.br/dados/serie/bcdata.sgs.1/dados/ultimos/20?formato=json', 'json');
  return j.map(x => [deDMY(x.data), numBR(String(x.valor).replace('.', ','))]).filter(p => p[0] && p[1] > 0);
}

(async () => {
  const previo = fs.existsSync('mercado_previo.json') ? JSON.parse(fs.readFileSync('mercado_previo.json', 'utf8')) : {};
  const ant = k => (previo.series && previo.series[k] && previo.series[k].serie) || [];
  const out = { version: 1, generado: new Date().toISOString(), series: {}, cambio: previo.cambio || null, errores: [] };
  const poner = (k, s) => { if (!s) return; out.series[k] = { ...s, serie: unir(ant(k), s.serie) }; };

  try { const u = await usda(); poner('us_novillo_gordo_pie', u.novillo_gordo_pie); poner('us_novillo_gordo_canal', u.novillo_gordo_canal); }
  catch (e) { out.errores.push('USDA: ' + e.message); }
  try { const e = await engorde(); poner('us_ternero', e.ternero); poner('us_novillo_engorde', e.novillo); out.errores.push(...e.avisos); }
  catch (e) { out.errores.push('Ganado de engorde EE. UU.: ' + e.message); }
  try { const b = await brasil(); poner('br_boi_gordo', b.boi_gordo); poner('br_bezerro', b.bezerro); poner('br_milho', b.milho); }
  catch (e) { out.errores.push('Cepea: ' + e.message); }
  try { poner('br_dolar_ptax', { nombre: 'Dólar PTAX', unidad: 'BRL/USD', base: 'venta', region: 'Brasil', frecuencia: 'diaria', fuente: 'Banco Central do Brasil, serie SGS 1', url: 'https://www.bcb.gov.br', serie: await ptax() }); }
  catch (e) { out.errores.push('BCB: ' + e.message); }
  try { out.cambio = await cambios(); } catch (e) { out.errores.push('Cambio: ' + e.message); }
  // lo que no se pudo traer hoy se conserva de la corrida anterior
  for (const [k, s] of Object.entries(previo.series || {})) if (!out.series[k]) out.series[k] = s;

  if (!Object.keys(out.series).length) { console.error('Sin datos:', out.errores.join(' | ')); process.exit(1); }
  fs.writeFileSync('mercado.json', JSON.stringify(out));
  // resumen legible para la página de la Release
  const ult = s => s.serie[s.serie.length - 1];
  const filas = Object.values(out.series).map(s => `| ${s.nombre} | ${ult(s)[1]} ${s.unidad} | ${ult(s)[0]} | ${s.fuente} |`).join('\n');
  const md = `Datos de mercado que usa Rumentis. Se actualizan solos dos veces al día; no borres esta Release.\n\n| Serie | Último | Fecha | Fuente |\n|---|---|---|---|\n${filas}\n\n` +
    (out.errores.length ? `Avisos de la última corrida: ${out.errores.join('; ')}\n\n` : '') +
    `<!-- RUMENTIS_MERCADO\n${JSON.stringify(out)}\nRUMENTIS_MERCADO -->\n`;
  fs.writeFileSync('mercado.md', md);
  console.log(md.slice(0, md.indexOf('<!--')));
  console.log('bytes json', JSON.stringify(out).length, 'series', Object.keys(out.series).join(', '));
})().catch(e => { console.error(e); process.exit(1); });
