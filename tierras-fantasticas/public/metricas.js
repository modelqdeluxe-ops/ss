// (4 nov 2026) /metricas: entra con la contraseña del staff, pide las sesiones de nLogin (GET /api/metricas), las cuenta
// (metricas-calc.js) y dibuja las gráficas en SVG; «Descargar Excel» hace el .xlsx en el navegador (xlsx.js). Se
// actualiza sola cada minuto mientras la pestaña está a la vista.
import { compute, RESTART_LIMIT, addDays, localDay } from './metricas-calc.js';
import { buildXlsx, excelDate, excelDay } from './xlsx.js';

const $ = (id) => document.getElementById(id);
const SVG = 'http://www.w3.org/2000/svg';
const nf = new Intl.NumberFormat('es-MX');
const nf1 = new Intl.NumberFormat('es-MX', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
const pf = new Intl.NumberFormat('es-MX', { style: 'percent', maximumFractionDigits: 0 });
const MONTHS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
const WEEKDAYS = ['lun', 'mar', 'mié', 'jue', 'vie', 'sáb', 'dom'];
const COLORS = { accent: '#ea580c', blue: '#2563eb', green: '#16a34a', red: '#dc2626', muted: '#d6c8b8', grid: '#f1e6da', axis: '#cdbba8', text: '#6b5a4b' };
const REFRESH_MS = 60 * 1000;

const dShort = (iso) => `${Number(iso.slice(8, 10))} ${MONTHS[Number(iso.slice(5, 7)) - 1]}`;
const dLong = (iso) => {
  const wd = (new Date(`${iso}T00:00:00Z`).getUTCDay() + 6) % 7;
  return `${WEEKDAYS[wd]} ${Number(iso.slice(8, 10))} de ${MONTHS[Number(iso.slice(5, 7)) - 1]}`;
};
const when = (sec) => {
  if (!sec) return '—';
  const d = new Date((sec - 6 * 3600) * 1000);
  return `${d.getUTCDate()} ${MONTHS[d.getUTCMonth()]} ${String(d.getUTCHours()).padStart(2, '0')}:${String(d.getUTCMinutes()).padStart(2, '0')}`;
};
const isMonday = (iso) => new Date(`${iso}T00:00:00Z`).getUTCDay() === 1;

let state = { data: null, m: null, desde: null, sort: { k: 'hours', dir: -1 }, timer: null, loading: false };

// ---------- Entrar y cargar ----------

async function load({ quiet = false } = {}) {
  if (state.loading) return;
  state.loading = true;
  if (!quiet) $('refresh').disabled = true;
  try {
    const q = state.desde ? `?desde=${encodeURIComponent(state.desde)}` : '';
    const res = await fetch(`/api/metricas${q}`, { credentials: 'same-origin', cache: 'no-store' });
    if (res.status === 401) return showLogin();
    if (!res.ok) throw new Error(`La web respondió ${res.status}`);
    const data = await res.json();
    state.data = data;
    state.desde = data.desde;
    state.m = compute(data);
    showPanel();
    render();
  } catch (err) {
    if (!quiet) {
      $('loading').hidden = false;
      $('loading').textContent = `No se pudieron cargar las métricas: ${err.message}`;
    }
  } finally {
    state.loading = false;
    $('refresh').disabled = false;
  }
}

function showLogin() {
  $('loading').hidden = true;
  $('panel').hidden = true;
  $('tools').hidden = true;
  $('login').hidden = false;
  $('password').focus();
}

function showPanel() {
  $('loading').hidden = true;
  $('login').hidden = true;
  $('panel').hidden = false;
  $('tools').hidden = false;
}

$('login-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const err = $('login-err');
  err.textContent = '';
  const btn = e.target.querySelector('button');
  btn.disabled = true;
  try {
    const res = await fetch('/api/metricas/entrar', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
      body: JSON.stringify({ password: $('password').value }),
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      err.textContent = data.error || 'No se pudo entrar.';
      return;
    }
    $('password').value = '';
    await load();
  } finally {
    btn.disabled = false;
  }
});

$('logout').addEventListener('click', async () => {
  await fetch('/api/metricas/salir', { method: 'POST', credentials: 'same-origin' });
  state.data = null;
  showLogin();
});
$('refresh').addEventListener('click', () => load());
$('since').addEventListener('change', () => {
  if ($('since').value) {
    state.desde = $('since').value;
    load();
  }
});
$('excel').addEventListener('click', downloadExcel);
$('search').addEventListener('input', () => renderPlayers());
document.querySelectorAll('#t-players th[data-k] button').forEach((b) =>
  b.addEventListener('click', () => {
    const k = b.parentElement.dataset.k;
    state.sort = { k, dir: state.sort.k === k ? -state.sort.dir : k === 'name' || k === 'state' ? 1 : -1 };
    renderPlayers();
  }),
);

// ---------- Dibujar ----------

function el(tag, attrs = {}, parent) {
  const node = tag.startsWith('svg:') ? document.createElementNS(SVG, tag.slice(4)) : document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (v === undefined || v === null) continue;
    if (k === 'text') node.textContent = v;
    else if (k === 'class') node.setAttribute('class', v);
    else node.setAttribute(k, v);
  }
  if (parent) parent.appendChild(node);
  return node;
}

function empty(box, text) {
  box.replaceChildren(el('div', { class: 'empty', text }));
}

// El globo de una gráfica
function tipper(box) {
  const tip = el('div', { class: 'tip', hidden: '' }, box);
  return {
    show(title, lines, x) {
      tip.replaceChildren(el('div', { class: 't', text: title }));
      for (const l of lines) {
        const row = el('div', { class: 'r' }, tip);
        if (l.color) el('i', { class: 'sw', style: `background:${l.color}` }, row);
        el('span', { text: l.name }, row);
        el('b', { text: l.value }, row);
      }
      tip.hidden = false;
      const w = tip.offsetWidth;
      const W = box.clientWidth;
      let left = x + 14;
      if (left + w > W) left = x - w - 14;
      tip.style.left = `${Math.max(0, Math.min(W - w, left))}px`;
      tip.style.top = '6px';
    },
    hide() {
      tip.hidden = true;
    },
  };
}

// Barra con la punta redondeada y la base recta
const topRounded = (x, y, w, h, r) => {
  r = Math.max(0, Math.min(r, w / 2, h));
  return `M${x},${y + h}V${y + r}Q${x},${y} ${x + r},${y}H${x + w - r}Q${x + w},${y} ${x + w},${y + r}V${y + h}Z`;
};

// Marcas del eje y para conteos: pasos enteros (1, 2, 5, 10, 20…) y unas 4 marcas
function niceTicks(max) {
  if (!(max > 0)) return [0, 1];
  const raw = max / 4;
  const p = 10 ** Math.floor(Math.log10(Math.max(raw, 1)));
  const step = [1, 2, 5, 10].map((k) => k * p).find((v) => v >= raw) || 10 * p;
  const ticks = [];
  for (let v = 0; v < max + step; v += step) ticks.push(v);
  return ticks;
}

// Barras verticales (una serie o apiladas)
function bars(box, rows, o) {
  box.replaceChildren();
  if (!rows.length) return empty(box, 'Aún no hay datos.');
  const W = Math.max(280, box.clientWidth);
  const H = o.height || 230;
  const svg = el('svg:svg', { viewBox: `0 0 ${W} ${H}`, role: 'img', 'aria-label': o.label || '' }, box);
  const total = (r) => o.series.reduce((a, s) => a + (Number(r[s.key]) || 0), 0);
  const ticks = niceTicks(Math.max(...rows.map(total), o.minTop || 0));
  const top = ticks.at(-1);
  const plotTop = 12;
  const plotBottom = H - 26;
  const y = (v) => plotBottom - (v / top) * (plotBottom - plotTop);
  const yfmt = o.yFmt || ((v) => nf.format(v));
  // el eje y: se mide lo que ocupan las etiquetas
  const gy = el('svg:g', {}, svg);
  let ml = 0;
  for (const v of ticks) {
    const t = el('svg:text', { x: 0, y: y(v), dy: '0.32em', 'text-anchor': 'end', text: yfmt(v) }, gy);
    ml = Math.max(ml, t.getComputedTextLength ? t.getComputedTextLength() : 20);
  }
  ml = Math.ceil(ml) + 10;
  gy.setAttribute('transform', `translate(${ml - 6},0)`);
  const mr = 4;
  const step = (W - ml - mr) / rows.length;
  const bw = Math.min(30, Math.max(2, step * (1 - (o.pad ?? 0.22))));
  const cx = (i) => ml + step * i + step / 2;
  const grid = el('svg:g', {}, svg);
  svg.insertBefore(grid, gy);
  for (const v of ticks) el('svg:line', { x1: ml, x2: W - mr, y1: y(v), y2: y(v), stroke: v === 0 ? COLORS.axis : COLORS.grid, 'stroke-width': 1 }, grid);
  if (o.ref) {
    el('svg:line', { x1: ml, x2: W - mr, y1: y(o.ref), y2: y(o.ref), stroke: COLORS.red, 'stroke-width': 1, 'stroke-dasharray': '4 3', opacity: 0.7 }, svg);
  }
  const gb = el('svg:g', {}, svg);
  rows.forEach((r, i) => {
    let base = 0;
    const lastIdx = o.series.reduce((li, s, k) => ((Number(r[s.key]) || 0) > 0 ? k : li), -1);
    o.series.forEach((s, k) => {
      const v = Number(r[s.key]) || 0;
      if (v <= 0) return;
      const yTop = y(base + v);
      const gap = base > 0 ? 2 : 0;
      const h = Math.max(1, y(base) - yTop - gap);
      const color = o.color ? o.color(r, s, k) : s.color;
      el('svg:path', {
        d: k === lastIdx ? topRounded(cx(i) - bw / 2, yTop, bw, h, 4) : `M${cx(i) - bw / 2},${yTop}h${bw}v${h}h${-bw}Z`,
        fill: color,
        opacity: o.dim && o.dim(r) ? 0.45 : 1,
      }, gb);
      base += v;
    });
  });
  // etiquetas del eje x sin que se pisen
  const gx = el('svg:g', {}, svg);
  let lastRight = -Infinity;
  rows.forEach((r, i) => {
    if (o.tick && !o.tick(r, i)) return;
    const t = el('svg:text', { x: cx(i), y: H - 8, 'text-anchor': 'middle', text: o.xLabel ? o.xLabel(r) : '' }, gx);
    const w = t.getComputedTextLength ? t.getComputedTextLength() : 30;
    if (cx(i) - w / 2 < lastRight + 8 || cx(i) - w / 2 < 0 || cx(i) + w / 2 > W) t.remove();
    else lastRight = cx(i) + w / 2;
  });
  if (o.tip) {
    const tp = tipper(box);
    const hit = el('svg:g', {}, svg);
    rows.forEach((r, i) => {
      const rect = el('svg:rect', { x: ml + step * i, y: 0, width: step, height: H, fill: 'transparent' }, hit);
      const show = () => {
        const t = o.tip(r);
        tp.show(t.title, t.lines, cx(i));
      };
      rect.addEventListener('pointerenter', show);
      rect.addEventListener('pointermove', show);
      rect.addEventListener('pointerleave', () => tp.hide());
    });
    svg.addEventListener('pointerleave', () => tp.hide());
  }
}

// Mapa de calor: día de la semana × hora
function heatmap(box, heat) {
  box.replaceChildren();
  const max = Math.max(...heat.flat(), 0);
  if (max <= 0) return empty(box, 'Aún no hay datos.');
  const W = Math.max(300, box.clientWidth);
  const ml = 34;
  const mt = 4;
  const cw = (W - ml - 2) / 24;
  const ch = Math.max(18, Math.min(30, cw * 1.15));
  const H = mt + ch * 7 + 46;
  const svg = el('svg:svg', { viewBox: `0 0 ${W} ${H}`, role: 'img', 'aria-label': 'Media de conectados por día de la semana y hora' }, box);
  const lo = [255, 241, 227];
  const hi = [154, 52, 18];
  const color = (v) => {
    const k = Math.sqrt(v / max);
    return `rgb(${lo.map((c, i) => Math.round(c + (hi[i] - c) * k)).join(',')})`;
  };
  const tp = tipper(box);
  heat.forEach((row, d) => {
    el('svg:text', { x: ml - 6, y: mt + ch * d + ch / 2, dy: '0.32em', 'text-anchor': 'end', text: WEEKDAYS[d] }, svg);
    row.forEach((v, h) => {
      const x = ml + cw * h;
      const y = mt + ch * d;
      const rect = el('svg:rect', { x: x + 1, y: y + 1, width: Math.max(1, cw - 2), height: ch - 2, rx: 3, fill: v > 0 ? color(v) : '#fbf5ee' }, svg);
      const show = () => tp.show(`${WEEKDAYS[d]}, de ${h} a ${(h + 1) % 24} h`, [{ name: 'Conectados de media', value: nf1.format(v) }], x + cw / 2);
      rect.addEventListener('pointerenter', show);
      rect.addEventListener('pointerleave', () => tp.hide());
    });
  });
  let lastRight = -Infinity;
  for (let h = 0; h < 24; h += 3) {
    const x = ml + cw * h + cw / 2;
    const t = el('svg:text', { x, y: mt + ch * 7 + 14, 'text-anchor': 'middle', text: `${h} h` }, svg);
    const w = t.getComputedTextLength ? t.getComputedTextLength() : 20;
    if (x - w / 2 < lastRight + 6) t.remove();
    else lastRight = x + w / 2;
  }
  // la escala
  const ly = mt + ch * 7 + 26;
  const lw = Math.min(180, W - ml - 80);
  const defs = el('svg:defs', {}, svg);
  const grad = el('svg:linearGradient', { id: 'heatgrad', x1: 0, x2: 1, y1: 0, y2: 0 }, defs);
  for (const k of [0, 0.25, 0.5, 0.75, 1]) el('svg:stop', { offset: k, 'stop-color': color(max * k * k) }, grad);
  el('svg:text', { x: ml, y: ly + 6, dy: '0.32em', text: '0' }, svg);
  el('svg:rect', { x: ml + 14, y: ly, width: lw, height: 12, rx: 3, fill: 'url(#heatgrad)' }, svg);
  el('svg:text', { x: ml + 20 + lw, y: ly + 6, dy: '0.32em', text: `${nf1.format(max)} a la vez` }, svg);
}

function funnel(box, steps) {
  box.replaceChildren();
  const first = steps[0]?.players || 0;
  if (!first) return empty(box, 'Aún no hay cuentas nuevas con una semana para volver. Vuelve en unos días.');
  let worst = -1;
  let worstLoss = 0;
  steps.forEach((s, i) => {
    if (i && steps[i - 1].players - s.players > worstLoss) {
      worstLoss = steps[i - 1].players - s.players;
      worst = i;
    }
  });
  steps.forEach((s, i) => {
    if (i) {
      const loss = steps[i - 1].players - s.players;
      if (loss > 0) el('div', { class: `floss${i === worst ? ' worst' : ''}`, text: `↓ se quedan fuera ${nf.format(loss)} (${pf.format(loss / (steps[i - 1].players || 1))})` }, box);
    }
    const row = el('div', { class: 'frow' }, box);
    el('span', { text: s.step }, row);
    const b = el('b', { text: nf.format(s.players) }, row);
    el('small', { text: pf.format(s.players / first) }, b);
    const track = el('div', { class: 'ftrack' }, row);
    el('i', { style: `width:${(100 * s.players) / first}%;${i === 0 ? `background:${COLORS.muted}` : ''}` }, track);
  });
}

function render() {
  const { m, data } = state;
  if (!m) return;
  const s = m.summary;
  $('since').min = data.inicio;
  $('since').max = m.today;
  $('since').value = m.desde;

  const chip = $('bridge');
  chip.className = `chip ${data.bridge ? 'on' : 'off'}`;
  chip.lastElementChild.textContent = data.bridge
    ? `Puente conectado · ${nf.format(s.online)} ${s.online === 1 ? 'jugador' : 'jugadores'} ahora`
    : `Puente sin conexión${data.lastEvent ? ` · datos hasta ${when(Math.floor(data.lastEvent / 1000))}` : ''}`;
  chip.title = data.lastEvent ? `Último registro de nLogin: ${when(Math.floor(data.lastEvent / 1000))}` : 'Aún no llegó ningún registro de nLogin';

  // La frase de arriba
  const lede = $('lede');
  lede.replaceChildren();
  if (!s.players) {
    lede.textContent = data.lastEvent || data.bridgeFrom
      ? 'Todavía no hay sesiones en este periodo.'
      : 'Aún no hay datos. La web apunta quién entra y sale con lo que manda el puente: en cuanto entre alguien, sale aquí.';
  } else {
    // trozos de texto: los de [texto] van en negrita
    const parts = ['Desde el ', [dLong(m.desde)], ' jugaron ', [nf.format(s.players)], ' jugadores, ', [nf.format(s.active7)], ' en los últimos 7 días. '];
    if (s.newAccounts) {
      parts.push('Se crearon ', [nf.format(s.newAccounts)], ' cuentas nuevas');
      if (s.newReturnedShare !== null) parts.push(' y volvió otro día el ', [pf.format(s.newReturnedShare)], ' de las que pudieron');
      parts.push('. ');
    }
    if (m.primeHours.length) parts.push('Más gente de ', [`${m.primeHours[0]} a ${(m.primeHours.at(-1) + 1) % 24} h`], '.');
    for (const t of parts) (Array.isArray(t) ? el('b', { text: t[0] }, lede) : lede.append(t));
  }

  // De dónde salen los datos
  const src = $('sources');
  if (data.bridgeFrom) {
    const nl = data.lastEvent ? Math.floor(data.lastEvent / 1000) : null;
    src.textContent = `${nl ? `Hasta el ${when(nl)} los datos son de nLogin; desde el ${when(Math.floor(data.bridgeFrom / 1000))}` : `Desde el ${when(Math.floor(data.bridgeFrom / 1000))}`}, de los conectados que manda el puente cada 10 s: ahí no se ven los intentos con otra versión ni los fallos de Mojang, una reconexión de menos de 10 s no se nota y una cuenta nueva cuenta desde que entra por primera vez. Con el TF Client 1.3.174 en el servidor vuelve a ser todo de nLogin y se rellena lo que falte.`;
    src.hidden = false;
  } else src.hidden = true;

  // Las cifras
  $('k-players').textContent = nf.format(s.players);
  $('k-players-s').textContent = `${nf.format(s.oneDay)} solo vinieron un día`;
  $('k-active').textContent = nf.format(s.active7);
  const ks = $('k-active-s');
  ks.replaceChildren();
  if (s.active7prev !== null) {
    const d = s.active7 - s.active7prev;
    el('span', { class: d >= 0 ? 'up' : 'down', text: `${d >= 0 ? '+' : '−'}${nf.format(Math.abs(d))}` }, ks);
    ks.append(' frente a los 7 días anteriores');
  } else ks.textContent = 'Aún no hay 7 días anteriores para comparar';
  $('k-new').textContent = nf.format(s.newAccounts);
  $('k-new-s').textContent = `${nf1.format(s.newAccounts / Math.max(1, m.days.length / 7))} por semana`;
  $('k-ret').textContent = s.newReturnedShare === null ? '—' : pf.format(s.newReturnedShare);
  $('k-ret-s').textContent = s.newCanReturn ? `${nf.format(s.newReturned)} de ${nf.format(s.newCanReturn)} volvieron otro día` : 'Sin nuevos con un día para volver';
  $('k-sess').replaceChildren(document.createTextNode(nf.format(Math.round(s.medianMin))), el('small', { text: ' min' }));
  $('k-sess-s').textContent = `Mediana de ${nf.format(s.sessions)} sesiones`;
  $('k-hours').textContent = nf.format(Math.round(s.hours));
  $('k-hours-s').textContent = `Máximo ${nf.format(s.peak)} a la vez`;

  drawCharts();
  // Cohortes
  const tb = $('t-cohorts').tBodies[0];
  tb.replaceChildren();
  for (const c of m.cohorts) {
    const tr = el('tr', {}, tb);
    el('td', { text: `${dShort(c.week)} – ${dShort(addDays(c.week, 6))}` }, tr);
    el('td', { text: nf.format(c.accounts) }, tr);
    el('td', { text: nf.format(c.played) }, tr);
    el('td', { text: c.played ? `${nf.format(c.returned)} (${pf.format(c.returned / c.played)})` : '—' }, tr);
    el('td', { text: c.returned7 === null ? 'aún no' : `${nf.format(c.returned7)} de ${nf.format(c.eligible7)}` }, tr);
    el('td', { text: nf.format(c.active) }, tr);
  }
  // Estabilidad
  $('limit').textContent = `${RESTART_LIMIT} arranques`;
  const stab = $('stab');
  stab.replaceChildren();
  const tile = (value, label) => {
    const d = el('div', {}, stab);
    el('b', { text: value }, d);
    el('span', { text: label }, d);
  };
  tile(nf.format(s.restarts), `arranques (${nf.format(s.badDays)} días con más de ${RESTART_LIMIT})`);
  tile(nf.format(s.cut), 'sesiones cortadas por un reinicio');
  tile(`${nf.format(s.recon)} · ${pf.format(s.reconShare)}`, 'reconexiones (de las sesiones)');
  tile(nf.format(s.verr), m.versions.length ? `intentos con otra versión (${m.versions.map(([v, n]) => `${v}: ${n}`).join(', ')})` : 'intentos con otra versión de Minecraft');
  tile(nf.format(s.mojangOut), `premium que no entraron porque Mojang no contestó (de ${nf.format(s.mojang)} fallos)`);
  renderPlayers();
}

function drawCharts() {
  const { m } = state;
  if (!m || $('panel').hidden) return;
  const dayTick = m.daily.length > 14 ? (r) => isMonday(r.day) : () => true;
  bars($('c-daily'), m.daily, {
    height: 260,
    label: 'Jugadores por día',
    series: [
      { key: 'old', color: COLORS.accent, name: 'Ya tenían cuenta' },
      { key: 'fresh', color: COLORS.green, name: 'Cuenta nueva ese día' },
    ],
    tick: dayTick,
    xLabel: (r) => dShort(r.day),
    dim: (r) => r.day === m.today,
    tip: (r) => ({
      title: dLong(r.day) + (r.day === m.today ? ' (hoy, en curso)' : ''),
      lines: [
        { name: 'Jugadores', value: nf.format(r.players) },
        { name: 'Ya tenían cuenta', value: nf.format(r.old), color: COLORS.accent },
        { name: 'Cuenta nueva ese día', value: nf.format(r.fresh), color: COLORS.green },
        { name: 'Horas jugadas', value: nf1.format(r.hours) },
        { name: 'Máximo a la vez', value: nf.format(r.peak) },
        { name: 'Sesiones', value: nf.format(r.sessions) },
      ],
    }),
  });
  funnel($('c-funnel'), m.funnel);
  bars($('c-weekly'), m.weekly, {
    pad: 0.35,
    label: 'Jugadores de cada semana',
    series: [
      { key: 'before', color: COLORS.muted },
      { key: 'stay', color: COLORS.accent },
      { key: 'back', color: COLORS.blue },
      { key: 'fresh', color: COLORS.green },
    ],
    dim: (r) => r.partial,
    xLabel: (r) => dShort(r.week),
    tip: (r) => ({
      title: `Semana del ${dShort(r.week)}${r.partial ? ' (en curso)' : ''}`,
      lines: [
        ...(r.before !== null ? [{ name: 'Sin semana anterior', value: nf.format(r.before), color: COLORS.muted }] : []),
        ...(r.stay !== null ? [{ name: 'Siguen', value: nf.format(r.stay), color: COLORS.accent }] : []),
        ...(r.back !== null ? [{ name: 'Regresan', value: nf.format(r.back), color: COLORS.blue }] : []),
        { name: 'Nuevos', value: nf.format(r.fresh), color: COLORS.green },
        { name: 'Total', value: nf.format(r.active) },
        ...(r.lost !== null && !r.partial ? [{ name: 'No volvieron de la anterior', value: nf.format(r.lost) }] : []),
      ],
    }),
  });
  bars($('c-days'), m.daysPlayed, {
    pad: 0.3,
    label: 'Jugadores según los días que jugaron',
    series: [{ key: 'players' }],
    color: (r) => (r.label === '1' ? COLORS.accent : COLORS.muted),
    xLabel: (r) => (r.label === '1' ? '1 día' : `${r.label}`),
    tip: (r) => ({ title: `${r.label} ${r.label === '1' ? 'día' : 'días'}`, lines: [{ name: 'Jugadores', value: nf.format(r.players) }] }),
  });
  heatmap($('c-heat'), m.heat);
  bars($('c-restarts'), m.daily, {
    label: 'Arranques del servidor por día',
    series: [{ key: 'restarts' }],
    minTop: RESTART_LIMIT + 1,
    ref: RESTART_LIMIT,
    color: (r) => (r.restarts > RESTART_LIMIT ? COLORS.red : COLORS.muted),
    tick: dayTick,
    xLabel: (r) => dShort(r.day),
    tip: (r) => ({
      title: dLong(r.day),
      lines: [
        { name: 'Arranques', value: nf.format(r.restarts) },
        { name: 'Jugadores', value: nf.format(r.players) },
      ],
    }),
  });
  bars($('c-recon'), m.daily, {
    height: 170,
    label: 'Reconexiones por día',
    series: [{ key: 'recon', color: COLORS.blue }],
    tick: dayTick,
    xLabel: (r) => dShort(r.day),
    tip: (r) => ({
      title: dLong(r.day),
      lines: [
        { name: 'Reconexiones', value: nf.format(r.recon) },
        { name: 'Sesiones', value: nf.format(r.sessions) },
        { name: 'Intentos con otra versión', value: nf.format(r.verr) },
        { name: 'Fallos de Mojang', value: nf.format(r.mojang) },
      ],
    }),
  });
}

const STATE_ORDER = { Conectado: 0, Activo: 1, 'No ha vuelto': 2, 'Sin jugar': 3 };

function renderPlayers() {
  const { m } = state;
  if (!m) return;
  const q = $('search').value.trim().toLowerCase();
  const { k, dir } = state.sort;
  const val = (r) => (k === 'state' ? STATE_ORDER[r.state] : k === 'name' ? r.name.toLowerCase() : (r[k] ?? -1));
  const rows = m.players.filter((r) => !q || r.name.toLowerCase().includes(q)).sort((a, b) => (val(a) < val(b) ? -dir : val(a) > val(b) ? dir : 0));
  document.querySelectorAll('#t-players th[data-k]').forEach((th) => {
    if (th.dataset.k === k) th.setAttribute('aria-sort', dir > 0 ? 'ascending' : 'descending');
    else th.removeAttribute('aria-sort');
  });
  const maxH = Math.max(...m.players.map((r) => r.hours), 1);
  const tb = $('t-players').tBodies[0];
  tb.replaceChildren();
  for (const r of rows) {
    const tr = el('tr', {}, tb);
    const name = el('td', { text: r.name }, tr);
    if (r.isNew) el('span', { class: 'pill new', text: 'nuevo' }, name);
    const st = el('td', {}, tr);
    el('span', { class: `pill ${r.state === 'Conectado' ? 'on' : r.state === 'Activo' ? 'act' : 'gone'}`, text: r.state }, st);
    const h = el('td', {}, tr);
    el('i', { class: 'bar-cell', style: `width:${Math.round((48 * r.hours) / maxH)}px` }, h);
    h.append(nf1.format(r.hours));
    el('td', { text: nf.format(r.days) }, tr);
    el('td', { text: nf.format(r.sessions) }, tr);
    el('td', { text: r.sessions ? nf.format(Math.round(r.avgMin)) : '—' }, tr);
    el('td', { text: when(r.last) }, tr);
    el('td', { text: r.created ? dShort(localDay(r.created)) : '—' }, tr);
  }
  $('players-note').textContent = `${nf.format(rows.length)} de ${nf.format(m.players.length)} jugadores. Toca una columna para ordenar.`;
}

// ---------- Excel ----------

async function downloadExcel() {
  const { m } = state;
  if (!m) return;
  const btn = $('excel');
  btn.disabled = true;
  try {
    const s = m.summary;
    const period = `Del ${dLong(m.desde)} al ${dLong(m.today)} (hora del centro de México). Generado el ${when(Math.floor(Date.now() / 1000))}.`;
    const sheets = [
      {
        name: 'Resumen',
        title: 'Métricas de jugadores — Tierras Fantásticas',
        subtitle: period,
        columns: [{ title: 'Métrica', width: 48, bold: true }, { title: 'Valor', width: 16, style: 'int' }, { title: 'Nota', width: 60 }],
        freeze: false,
        filter: false,
        rows: [
          ['Jugadores en el periodo', s.players, 'Jugadores distintos con al menos una sesión'],
          ['Jugaron los últimos 7 días', s.active7, s.active7prev !== null ? `Los 7 días anteriores: ${s.active7prev}` : ''],
          ['Solo vinieron un día', s.oneDay, ''],
          ['Cuentas nuevas', s.newAccounts, 'Creadas en el periodo'],
          ['Nuevos que volvieron otro día', s.newReturned, `De ${s.newCanReturn} que jugaron y ya tuvieron un día para volver`],
          ['Nuevos que volvieron otro día (%)', { v: s.newReturnedShare, style: 'pct' }, ''],
          ['Sesiones', s.sessions, ''],
          ['Minutos de la sesión típica (mediana)', { v: Math.round(s.medianMin * 10) / 10, style: 'dec' }, ''],
          ['Horas jugadas en total', { v: Math.round(s.hours * 10) / 10, style: 'dec' }, ''],
          ['Máximo de jugadores a la vez', s.peak, ''],
          ['Horas con más gente', m.primeHours.length ? `${m.primeHours[0]} a ${(m.primeHours.at(-1) + 1) % 24} h` : '—', 'Las que tienen al menos el 80 % de la hora con más gente'],
          ['Arranques del servidor', s.restarts, `${s.badDays} días con más de ${RESTART_LIMIT}`],
          ['Sesiones cortadas por un reinicio', s.cut, ''],
          ['Reconexiones en menos de 3 minutos', s.recon, 'Sin un reinicio en medio: el juego se cerró o se cortó la conexión'],
          ['Reconexiones (% de las sesiones)', { v: s.reconShare, style: 'pct' }, ''],
          ['Intentos de entrar con otra versión', s.verr, m.versions.map(([v, n]) => `${v}: ${n}`).join(', ')],
          ['Premium que no entraron (Mojang no contestó)', s.mojangOut, `De ${s.mojang} fallos al comprobar la cuenta`],
        ],
      },
      {
        name: 'Por día',
        columns: [
          { title: 'Día', width: 12, style: 'date' },
          { title: 'Jugadores', width: 11, style: 'int' },
          { title: 'Ya tenían cuenta', width: 15, style: 'int' },
          { title: 'Cuenta nueva ese día', width: 18, style: 'int' },
          { title: 'Sesiones', width: 10, style: 'int' },
          { title: 'Horas jugadas', width: 13, style: 'dec' },
          { title: 'Máximo a la vez', width: 14, style: 'int' },
          { title: 'Arranques', width: 11, style: 'int' },
          { title: 'Reconexiones', width: 13, style: 'int' },
          { title: 'Otra versión', width: 12, style: 'int' },
          { title: 'Fallos de Mojang', width: 15, style: 'int' },
        ],
        rows: m.daily.map((r) => [excelDay(r.day), r.players, r.old, r.fresh, r.sessions, Math.round(r.hours * 10) / 10, r.peak, r.restarts, r.recon, r.verr, r.mojang]),
      },
      {
        name: 'Por semana',
        columns: [
          { title: 'Semana (lunes)', width: 15, style: 'date' },
          { title: 'Jugadores', width: 11, style: 'int' },
          { title: 'Siguen de la anterior', width: 19, style: 'int' },
          { title: 'Regresan', width: 11, style: 'int' },
          { title: 'Nuevos', width: 10, style: 'int' },
          { title: 'Sin semana anterior', width: 18, style: 'int' },
          { title: 'No volvieron de la anterior', width: 24, style: 'int' },
          { title: 'Completa', width: 10 },
        ],
        rows: m.weekly.map((r) => [excelDay(r.week), r.active, r.stay, r.back, r.fresh, r.before, r.lost, !r.partial]),
      },
      {
        name: 'Cuentas nuevas',
        columns: [
          { title: 'Semana en que se crearon', width: 22, style: 'date' },
          { title: 'Cuentas', width: 10, style: 'int' },
          { title: 'Jugaron', width: 10, style: 'int' },
          { title: 'Volvieron otro día', width: 18, style: 'int' },
          { title: 'Con una semana para volver', width: 24, style: 'int' },
          { title: 'Volvieron una semana después', width: 26, style: 'int' },
          { title: 'Activas los últimos 7 días', width: 23, style: 'int' },
        ],
        rows: m.cohorts.map((c) => [excelDay(c.week), c.accounts, c.played, c.returned, c.eligible7, c.returned7, c.active]),
      },
      {
        name: 'Embudo de nuevos',
        columns: [{ title: 'Paso', width: 38 }, { title: 'Jugadores', width: 11, style: 'int' }, { title: 'De las cuentas nuevas', width: 20, style: 'pct' }],
        rows: m.funnel.map((f) => [f.step, f.players, m.funnel[0].players ? f.players / m.funnel[0].players : null]),
      },
      {
        name: 'Por hora',
        columns: [
          { title: 'Hora', width: 8, style: 'int' },
          { title: 'Media de conectados', width: 19, style: 'dec' },
          ...WEEKDAYS.map((d) => ({ title: d, width: 8, style: 'dec' })),
        ],
        rows: m.hourly.map((v, h) => [h, Math.round(v * 100) / 100, ...m.heat.map((row) => Math.round(row[h] * 100) / 100)]),
      },
      {
        name: 'Días jugados',
        columns: [{ title: 'Días distintos', width: 14 }, { title: 'Jugadores', width: 11, style: 'int' }],
        rows: m.daysPlayed.map((d) => [d.label, d.players]),
      },
      {
        name: 'Jugadores',
        columns: [
          { title: 'Jugador', width: 20, bold: true },
          { title: 'Estado', width: 13 },
          { title: 'Cuenta nueva', width: 13 },
          { title: 'Cuenta creada', width: 17, style: 'datetime' },
          { title: 'Primera sesión', width: 17, style: 'datetime' },
          { title: 'Última vez', width: 17, style: 'datetime' },
          { title: 'Días jugados', width: 12, style: 'int' },
          { title: 'Sesiones', width: 10, style: 'int' },
          { title: 'Horas', width: 9, style: 'dec' },
          { title: 'Minutos por sesión', width: 17, style: 'dec' },
        ],
        rows: m.players.map((r) => [
          r.name,
          r.state,
          r.isNew,
          r.created ? excelDate(r.created) : null,
          r.first ? excelDate(r.first) : null,
          r.last ? excelDate(r.last) : null,
          r.days,
          r.sessions,
          Math.round(r.hours * 10) / 10,
          Math.round(r.avgMin * 10) / 10,
        ]),
      },
      {
        name: 'Sesiones',
        columns: [
          { title: 'Jugador', width: 20 },
          { title: 'Entró', width: 17, style: 'datetime' },
          { title: 'Salió', width: 17, style: 'datetime' },
          { title: 'Minutos', width: 10, style: 'dec' },
          { title: 'Cortada por un reinicio', width: 21 },
          { title: 'En curso', width: 10 },
        ],
        rows: m.sessions.map((x) => [x.name, excelDate(x.start), excelDate(x.end), Math.round(x.minutes * 10) / 10, x.cut, x.live]),
      },
      {
        name: 'Arranques',
        columns: [{ title: 'Arranque del servidor', width: 22, style: 'datetime' }],
        rows: m.restartTimes.map((t) => [excelDate(t)]),
      },
    ];
    const blob = await buildXlsx(sheets);
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `metricas-tierras-fantasticas-${m.desde}-a-${m.today}.xlsx`;
    document.body.appendChild(a);
    a.click();
    setTimeout(() => {
      URL.revokeObjectURL(a.href);
      a.remove();
    }, 1000);
  } finally {
    btn.disabled = false;
  }
}

// Se redibuja al cambiar el ancho; se actualiza sola cada minuto si la pestaña está a la vista
let lastWidth = 0;
new ResizeObserver(() => {
  const w = document.documentElement.clientWidth;
  if (Math.abs(w - lastWidth) > 4) {
    lastWidth = w;
    drawCharts();
  }
}).observe(document.documentElement);
state.timer = setInterval(() => {
  if (document.visibilityState === 'visible' && state.data && !$('panel').hidden) load({ quiet: true });
}, REFRESH_MS);

load();
