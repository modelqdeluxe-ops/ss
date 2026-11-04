// (4 nov 2026) /metricas: entra con la contraseña del staff, pide las sesiones (GET /api/metricas), las cuenta
// (metricas-calc.js) y dibuja (metricas-charts.js). v2 (el dueño): jugadores por UUID, días de juego de verdad (un mínimo
// de minutos), grupos (casi a diario, regulares, ocasionales, fantasmas, se fueron), retención, concentración, quién
// jugó cada día, dispersión y la ficha de cada jugador; pestañas y filtros. «Descargar Excel» hace el .xlsx en el
// navegador (xlsx.js). Se actualiza sola cada minuto mientras la pestaña está a la vista.
import { compute, RESTART_LIMIT, SEGMENTS, DEFAULT_MIN_MINUTES, addDays, localDay, dayStart, OFFSET_S } from './metricas-calc.js';
import { nf, nf1, pf, COLORS, el, empty, lineChart, stackedArea, donut, bars, matrix, scatter, timeline, toggleLegend, seqColor } from './metricas-charts.js';
import { buildXlsx, excelDate, excelDay } from './xlsx.js';

const $ = (id) => document.getElementById(id);
const MONTHS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
const WEEKDAYS = ['lun', 'mar', 'mié', 'jue', 'vie', 'sáb', 'dom'];
const REFRESH_MS = 60 * 1000;
const SEG_COLOR = { diario: '#9a3412', regular: '#ea580c', ocasional: '#fdba74', fantasma: '#d6c8b8', sefue: '#57534e' };
const SEG_NAME = Object.fromEntries(SEGMENTS.map((s) => [s.id, s.name]));
const LAST_COLORS = ['#16a34a', '#86efac', '#fdba74', '#ea580c', '#9a3412', '#57534e'];
const CAL_ROWS = 40;

const dShort = (iso) => `${Number(iso.slice(8, 10))} ${MONTHS[Number(iso.slice(5, 7)) - 1]}`;
const dLong = (iso) => {
  const wd = (new Date(`${iso}T00:00:00Z`).getUTCDay() + 6) % 7;
  return `${WEEKDAYS[wd]} ${Number(iso.slice(8, 10))} de ${MONTHS[Number(iso.slice(5, 7)) - 1]}`;
};
const local = (sec) => new Date((sec - OFFSET_S) * 1000);
const hhmm = (sec) => `${String(local(sec).getUTCHours()).padStart(2, '0')}:${String(local(sec).getUTCMinutes()).padStart(2, '0')}`;
const when = (sec) => (sec ? `${local(sec).getUTCDate()} ${MONTHS[local(sec).getUTCMonth()]} ${hhmm(sec)}` : '—');
const hm = (hours) => {
  const m = Math.round(hours * 60);
  return m < 60 ? `${m} min` : `${Math.floor(m / 60)} h ${String(m % 60).padStart(2, '0')}`;
};
const isMonday = (iso) => new Date(`${iso}T00:00:00Z`).getUTCDay() === 1;
const store = {
  get(k, d) {
    try {
      const v = localStorage.getItem(k);
      return v === null ? d : JSON.parse(v);
    } catch {
      return d;
    }
  },
  set(k, v) {
    try {
      localStorage.setItem(k, JSON.stringify(v));
    } catch {
      /* sin almacenamiento: no pasa nada */
    }
  },
};

const state = {
  data: null,
  m: null,
  desde: null,
  hasta: null,
  minMinutes: store.get('tf-metricas-min', DEFAULT_MIN_MINUTES),
  tab: ['resumen', 'retencion', 'jugadores', 'horarios'].includes(location.hash.slice(1)) ? location.hash.slice(1) : 'resumen',
  seg: null,
  sort: { k: 'hours', dir: -1 },
  calAll: false,
  loading: false,
  open: null,
  series: {
    daily: [
      { key: 'players', name: 'Entraron', color: COLORS.blue },
      { key: 'real', name: 'Jugaron de verdad', color: COLORS.accent, area: true },
      { key: 'peak', name: 'Máximo a la vez', color: COLORS.green, dash: '5 4' },
    ],
    ret: [
      { key: 'exact', name: 'Jugaron justo ese día', color: COLORS.accent, area: true },
      { key: 'plus', name: 'Ese día o después', color: COLORS.blue, dash: '5 4' },
    ],
    hours: [
      { key: 'avg', name: 'Media a la vez', color: COLORS.accent, area: true },
      { key: 'max', name: 'Máximo a la vez', color: COLORS.blue, dash: '5 4' },
    ],
    stab: [
      { key: 'restarts', name: 'Arranques', color: '#57534e' },
      { key: 'recon', name: 'Reconexiones', color: COLORS.blue },
    ],
    segtrend: SEGMENTS.filter((s) => s.id !== 'sefue').map((s) => ({ key: s.id, name: s.name, color: SEG_COLOR[s.id] })),
    weekly: [
      { key: 'before', name: 'Sin semana anterior', color: COLORS.muted },
      { key: 'stay', name: 'Siguen', color: COLORS.accent },
      { key: 'back', name: 'Regresan', color: COLORS.blue },
      { key: 'fresh', name: 'Nuevos', color: COLORS.green },
    ],
  },
};

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
    state.data = await res.json();
    state.desde = state.data.desde;
    recompute();
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

function recompute() {
  state.m = compute(state.data, { minMinutes: state.minMinutes, hasta: state.hasta });
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
      body: JSON.stringify({ password: $('password').value.trim() }),
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
  if (!$('since').value) return;
  state.desde = $('since').value;
  if (state.hasta && state.hasta < state.desde) state.hasta = null;
  load();
});
$('until').addEventListener('change', () => {
  state.hasta = $('until').value || null;
  recompute();
  render();
});
document.querySelectorAll('#minctl button').forEach((b) =>
  b.addEventListener('click', () => {
    state.minMinutes = Number(b.dataset.min);
    store.set('tf-metricas-min', state.minMinutes);
    recompute();
    render();
  }),
);
document.querySelectorAll('.tabs button').forEach((b) => b.addEventListener('click', () => setTab(b.dataset.tab)));
$('excel').addEventListener('click', downloadExcel);
$('search').addEventListener('input', () => renderJugadores());
$('more-cal').addEventListener('click', () => {
  state.calAll = !state.calAll;
  renderJugadores();
});
document.querySelectorAll('#t-players th[data-k] button').forEach((b) =>
  b.addEventListener('click', () => {
    const k = b.parentElement.dataset.k;
    state.sort = { k, dir: state.sort.k === k ? -state.sort.dir : ['name', 'segment', 'uuid'].includes(k) ? 1 : -1 };
    renderJugadores();
  }),
);
$('drawer-bg').addEventListener('click', closePlayer);
$('d-close').addEventListener('click', closePlayer);
document.addEventListener('keydown', (e) => e.key === 'Escape' && closePlayer());
$('d-copy').addEventListener('click', async () => {
  const u = $('d-uuid').textContent;
  try {
    await navigator.clipboard.writeText(u);
    $('d-copy').textContent = 'Copiado';
  } catch {
    const r = document.createRange();
    r.selectNodeContents($('d-uuid'));
    getSelection().removeAllRanges();
    getSelection().addRange(r);
  }
  setTimeout(() => ($('d-copy').textContent = 'Copiar UUID'), 1500);
});

function setTab(tab) {
  state.tab = tab;
  try {
    history.replaceState(null, '', `#${tab}`);
  } catch {
    /* nada */
  }
  render();
}

// ---------- Dibujar ----------

function render() {
  const { m, data } = state;
  if (!m) return;
  $('since').min = data.inicio;
  $('since').max = m.today;
  $('since').value = m.desde;
  $('until').min = m.desde;
  $('until').max = m.today;
  $('until').value = m.hasta;
  document.querySelectorAll('#minctl button').forEach((b) => b.setAttribute('aria-pressed', String(Number(b.dataset.min) === m.minMinutes)));
  document.querySelectorAll('.tabs button').forEach((b) => b.setAttribute('aria-selected', String(b.dataset.tab === state.tab)));
  document.querySelectorAll('[data-panel]').forEach((p) => (p.hidden = p.dataset.panel !== state.tab));
  const chip = $('bridge');
  chip.className = `chip ${data.bridge ? 'on' : 'off'}`;
  chip.lastElementChild.textContent = data.bridge ? `Puente conectado · ${nf.format(m.summary.online)} ${m.summary.online === 1 ? 'jugador' : 'jugadores'} ahora` : 'Puente sin conexión';
  if (state.tab === 'resumen') renderResumen();
  if (state.tab === 'retencion') renderRetencion();
  if (state.tab === 'jugadores') renderJugadores();
  if (state.tab === 'horarios') renderHorarios();
  if (state.open) openPlayer(state.open, { keep: true });
}

function kpis(box, items) {
  box.replaceChildren();
  for (const it of items) {
    const d = el('div', { class: `kpi${it.alert ? ' alert' : ''}`, title: it.title || null }, box);
    el('span', { class: 'k', text: it.k }, d);
    const v = el('span', { class: 'v', text: it.v }, d);
    if (it.unit) el('small', { text: ` ${it.unit}` }, v);
    if (it.s) el('span', { class: 's', text: it.s }, d);
  }
}

function sentence(box, parts) {
  box.replaceChildren();
  for (const t of parts) (Array.isArray(t) ? el('b', { text: t[0] }, box) : box.append(t));
}

const dayTick = (n) => (n > 16 ? (i) => isMonday(state.m.daily[i].day) : () => true);
const minLabel = () => (state.minMinutes >= 60 ? `${state.minMinutes / 60} h` : `${state.minMinutes} min`);

function renderResumen() {
  const { m, data } = state;
  const s = m.summary;
  if (!s.players) {
    sentence($('lede'), [data.lastEvent || data.bridgeFrom ? 'Todavía no hay sesiones en este periodo.' : 'Aún no hay datos. La web apunta quién entra y sale con lo que manda el puente.']);
  } else {
    const prime = m.primeHours.length ? `${m.primeHours[0]} a ${(m.primeHours.at(-1) + 1) % 24} h` : '';
    sentence($('lede'), [
      'Cada día juegan de verdad (', [minLabel()], ' o más) unos ', [nf.format(Math.round(s.avgRealPerDay))], ' jugadores distintos, pero ',
      ['a la vez'], ' solo hay ', [nf1.format(s.primeAvg)], ' de media', prime ? ` en las horas fuertes (${prime})` : '', ' y como mucho ', [nf.format(s.peak)], '. ',
      [nf.format(s.core)], ' juegan casi a diario; ', [nf.format(s.occasional + s.ghosts)], ' apenas aparecen y ', [nf.format(s.gone)], ' ya no vienen. ',
      'Los 10 que más juegan ponen el ', [pf.format(s.top10Share)], ' de las horas.',
    ]);
  }
  const src = $('sources');
  if (data.bridgeFrom) {
    const nl = data.lastEvent ? Math.floor(data.lastEvent / 1000) : null;
    src.textContent = `${nl ? `Hasta el ${when(nl)} los datos son de nLogin; desde el ${when(Math.floor(data.bridgeFrom / 1000))}` : `Desde el ${when(Math.floor(data.bridgeFrom / 1000))}`}, de los conectados que manda el puente cada 10 s (sin intentos con otra versión ni fallos de Mojang; una cuenta nueva cuenta desde que entra por primera vez).`;
    src.hidden = false;
  } else src.hidden = true;
  const lf = s.lastFullDay;
  kpis($('kpis'), [
    { k: 'Juegan de verdad al día', v: nf.format(Math.round(s.avgRealPerDay)), s: lf ? `media; el ${dShort(lf.day)}: ${lf.real} (entraron ${lf.players})` : 'media por día' },
    { k: 'A la vez, en horas fuertes', v: nf1.format(s.primeAvg), s: `media · máximo ${nf.format(s.peak)}` },
    { k: 'Casi a diario', v: nf.format(s.core), s: `5 a 7 de los últimos 7 días · de ${nf.format(s.players)} que jugaron` },
    { k: 'Apenas juegan o se fueron', v: nf.format(s.occasional + s.ghosts + s.gone), s: `${s.occasional} un día · ${s.ghosts} fantasmas · ${s.gone} se fueron`, alert: s.occasional + s.ghosts + s.gone > s.core + s.regular },
    { k: 'Nuevos que volvieron', v: s.newReturnedShare === null ? '—' : pf.format(s.newReturnedShare), s: s.newCanReturn ? `${s.newReturned} de ${s.newCanReturn} · a la semana: ${s.d7plus === null ? 'aún no' : pf.format(s.d7plus)}` : 'sin cuentas nuevas aún' },
    { k: 'Los 10 que más juegan', v: pf.format(s.top10Share), s: `de las horas · los 5 primeros: ${pf.format(s.top5Share)}` },
  ]);

  // Cada día
  const series = state.series.daily.map((x) => ({ ...x, values: m.daily.map((r) => r[x.key]) }));
  const draw = () =>
    lineChart($('c-daily'), {
      n: m.daily.length,
      height: 280,
      label: 'Jugadores por día',
      series: series.map((x, i) => ({ ...x, hidden: state.series.daily[i].hidden })),
      xLabel: (i) => dShort(m.daily[i].day),
      tick: dayTick(m.daily.length),
      tip: (i) => {
        const r = m.daily[i];
        return {
          title: dLong(r.day) + (r.day === m.today ? ' (hoy, en curso)' : ''),
          lines: [
            { name: 'Entraron', value: nf.format(r.players), color: COLORS.blue },
            { name: `Jugaron ${minLabel()} o más`, value: nf.format(r.real), color: COLORS.accent },
            { name: 'Máximo a la vez', value: nf.format(r.peak), color: COLORS.green },
            { name: 'Media conectados', value: nf1.format(r.avgOnline) },
            { name: 'Horas jugadas', value: nf1.format(r.hours) },
            { name: 'Cuentas nuevas', value: nf.format(r.newAccounts) },
            ...(r.top.length ? [{ note: `Los que más jugaron (${pf.format(r.topShare)} de las horas del día):` }] : []),
            ...r.top.map((t) => ({ name: t.name, value: hm(t.hours) })),
          ],
        };
      },
    });
  toggleLegend($('lg-daily'), state.series.daily, draw);
  draw();

  // Grupos
  donut($('c-segments'), {
    label: 'Grupos de jugadores',
    items: m.segments.map((x) => ({ id: x.id, name: x.name, hint: x.hint, value: x.players, color: SEG_COLOR[x.id] })),
    center: { value: nf.format(s.players), label: 'jugadores' },
    selected: state.seg,
    onClick: (id) => {
      state.seg = id;
      setTab('jugadores');
    },
  });
  const trend = m.segTrend.filter((r) => r.complete);
  const drawTrend = () =>
    trend.length
      ? stackedArea($('c-segtrend'), {
          n: trend.length,
          label: 'Grupos por día',
          series: state.series.segtrend.map((x) => ({ ...x, values: trend.map((r) => r[x.key]) })),
          xLabel: (i) => dShort(trend[i].day),
          tick: trend.length > 16 ? (i) => isMonday(trend[i].day) : null,
          tip: (i) => ({
            title: `${dLong(trend[i].day)} (sus 7 días)`,
            lines: [...state.series.segtrend].reverse().filter((x) => !x.hidden).map((x) => ({ name: x.name, value: nf.format(trend[i][x.key]), color: x.color })),
          }),
        })
      : empty($('c-segtrend'), 'Hace falta un periodo de al menos 7 días.');
  toggleLegend($('lg-segtrend'), state.series.segtrend, drawTrend);
  drawTrend();

  // Concentración
  const half = m.pareto.findIndex((p) => p.share >= 0.5) + 1;
  sentence($('say-pareto'), m.pareto.length ? ['Los 5 que más juegan ponen el ', [pf.format(s.top5Share)], ' de todas las horas y los 10, el ', [pf.format(s.top10Share)], '. La mitad de las horas la ponen ', [nf.format(half)], ` de ${nf.format(m.pareto.length)} jugadores.`] : ['Aún no hay horas jugadas.']);
  lineChart($('c-pareto'), {
    n: m.pareto.length,
    percent: true,
    label: 'Parte de las horas que ponen los que más juegan',
    series: [
      { name: 'Horas acumuladas', color: COLORS.accent, area: true, values: m.pareto.map((p) => p.share) },
      { name: 'Si todos jugaran igual', color: COLORS.muted, dash: '4 4', values: m.pareto.map((p, i) => (i + 1) / m.pareto.length) },
    ],
    xLabel: (i) => `${i + 1}`,
    tick: (i) => i === 0 || (i + 1) % 5 === 0,
    tip: (i) => ({
      title: `${i + 1}.º ${m.pareto[i].name}`,
      lines: [
        { name: 'Sus horas', value: nf1.format(m.pareto[i].hours) },
        { name: `Los ${i + 1} primeros`, value: pf.format(m.pareto[i].share) },
      ],
    }),
  });
  donut($('c-last'), {
    label: 'Última vez que jugaron',
    items: m.lastBuckets.map((b, i) => ({ id: b.id, name: b.name, value: b.players, color: LAST_COLORS[i] })),
    center: { value: nf.format(m.lastBuckets.slice(0, 2).reduce((a, b) => a + b.players, 0)), label: 'hoy o ayer' },
  });
}

function renderRetencion() {
  const { m } = state;
  const s = m.summary;
  const r1 = m.retention[1];
  const r7 = m.retention[7];
  sentence(
    $('lede-ret'),
    s.newAccounts
      ? ['De ', [nf.format(s.newAccounts)], ' cuentas nuevas en el periodo, ', ...(r1 ? ['el ', [pf.format(r1.exact)], ' jugó al día siguiente'] : ['aún no hay un día para ver si vuelven']), ...(r7 ? [' y el ', [pf.format(r7.plus)], ' seguía jugando una semana después'] : [' (para la semana, aún faltan días)']), '. ',
          ...(m.survival.length && m.survivalBase ? ['De los ', [nf.format(m.survivalBase)], ' que jugaban los 3 primeros días, el último día jugó el ', [pf.format(m.survival.at(-1).share || 0)], '.'] : [])]
      : ['Aún no hay cuentas nuevas en este periodo.'],
  );
  const lastWeek = m.weekly.filter((w) => w.kept !== null).at(-1);
  kpis($('kpis-ret'), [
    { k: 'Juegan al día siguiente', v: r1 ? pf.format(r1.exact) : '—', s: r1 ? `${r1.exactN} de ${r1.eligible} cuentas nuevas` : 'aún no' },
    { k: 'Siguen a los 3 días', v: m.retention[3] ? pf.format(m.retention[3].plus) : '—', s: m.retention[3] ? `${m.retention[3].plusN} de ${m.retention[3].eligible}` : 'aún no' },
    { k: 'Siguen a la semana', v: r7 ? pf.format(r7.plus) : '—', s: r7 ? `${r7.plusN} de ${r7.eligible}` : 'aún no' },
    { k: 'Se quedan de una semana a otra', v: lastWeek ? pf.format(lastWeek.kept) : '—', s: lastWeek ? `semana del ${dShort(lastWeek.week)}${lastWeek.partial ? ' (en curso)' : ''}` : 'hace falta una semana más' },
    { k: 'Pegada (diarios / semanales)', v: s.stickiness === null ? '—' : pf.format(s.stickiness), s: 'media; bien por encima del 20 %', alert: s.stickiness !== null && s.stickiness < 0.2 },
  ]);
  const drawRet = () =>
    lineChart($('c-ret'), {
      n: m.retention.length,
      percent: true,
      height: 280,
      label: 'Curva de retención de las cuentas nuevas',
      empty: 'Aún no hay cuentas nuevas en este periodo.',
      series: state.series.ret.map((x) => ({ ...x, values: m.retention.map((r) => r[x.key]) })),
      xLabel: (i) => (i === 0 ? 'Día 0' : `${i}`),
      tip: (i) => {
        const r = m.retention[i];
        return {
          title: i === 0 ? 'El día que crearon la cuenta' : `${i} ${i === 1 ? 'día' : 'días'} después de crear la cuenta`,
          lines: [
            { name: 'Jugaron justo ese día', value: `${pf.format(r.exact)} (${r.exactN})`, color: COLORS.accent },
            { name: 'Ese día o después', value: `${pf.format(r.plus)} (${r.plusN})`, color: COLORS.blue },
            { note: `De ${r.eligible} cuentas que ya tuvieron ese día.` },
          ],
        };
      },
    });
  toggleLegend($('lg-ret'), state.series.ret, drawRet);
  drawRet();
  sentence($('say-surv'), m.survivalBase ? ['Base: los ', [nf.format(m.survivalBase)], ' que jugaron de verdad en los 3 primeros días del periodo.'] : ['Nadie jugó los primeros días del periodo.']);
  lineChart($('c-surv'), {
    n: m.survival.length,
    percent: true,
    label: 'Qué parte de los del principio sigue jugando',
    series: [{ name: 'Siguen', color: COLORS.accent, area: true, values: m.survival.map((r) => r.share) }],
    xLabel: (i) => dShort(m.survival[i].day),
    tick: dayTick(m.survival.length),
    tip: (i) => ({ title: dLong(m.survival[i].day), lines: [{ name: 'Jugaron ese día', value: `${pf.format(m.survival[i].share || 0)} (${m.survival[i].n} de ${m.survivalBase})` }] }),
  });
  const stick = m.segTrend.filter((r) => r.complete);
  if (stick.length)
    lineChart($('c-stick'), {
      n: stick.length,
      percent: true,
      label: 'Pegada',
      ref: { value: 0.2, label: '20 %', color: COLORS.green },
      series: [{ name: 'Pegada', color: COLORS.blue, area: true, values: stick.map((r) => r.stick) }],
      xLabel: (i) => dShort(stick[i].day),
      tip: (i) => ({ title: dLong(stick[i].day), lines: [{ name: 'Pegada', value: pf.format(stick[i].stick || 0) }, { name: 'Jugaron ese día', value: nf.format(stick[i].dau) }, { name: 'Jugaron en sus 7 días', value: nf.format(stick[i].wau) }] }),
    });
  else empty($('c-stick'), 'Hace falta un periodo de al menos 7 días.');
  const drawWeekly = () =>
    bars($('c-weekly'), m.weekly, {
      pad: 0.4,
      label: 'Jugadores de cada semana',
      series: state.series.weekly.filter((x) => !x.hidden),
      dim: (r) => r.partial,
      xLabel: (r) => dShort(r.week),
      tip: (r) => ({
        title: `Semana del ${dShort(r.week)}${r.partial ? ' (incompleta)' : ''}`,
        lines: [
          ...state.series.weekly.filter((x) => r[x.key] !== null).map((x) => ({ name: x.name, value: nf.format(r[x.key]), color: x.color })),
          { name: 'Total', value: nf.format(r.active) },
          ...(r.lost !== null ? [{ name: 'No volvieron de la anterior', value: nf.format(r.lost) }] : []),
          ...(r.kept !== null ? [{ name: 'Se quedaron de la anterior', value: pf.format(r.kept) }] : []),
        ],
      }),
    });
  toggleLegend($('lg-weekly'), state.series.weekly, drawWeekly);
  drawWeekly();
  // Cohortes
  const t = $('t-cohorts');
  t.replaceChildren();
  const maxK = Math.max(0, ...m.cohorts.map((c) => c.cells.length));
  const head = el('tr', {}, el('thead', {}, t));
  el('th', { text: 'Creadas la semana del' }, head);
  el('th', { text: 'Cuentas' }, head);
  for (let k = 0; k < maxK; k++) el('th', { text: k === 0 ? 'Esa semana' : `+${k} sem.` }, head);
  const tb = el('tbody', {}, t);
  const rows = m.cohorts.filter((c) => c.accounts);
  if (!rows.length) el('td', { colspan: 2 + maxK, text: 'Aún no hay cuentas nuevas.' }, el('tr', {}, tb));
  for (const c of rows) {
    const tr = el('tr', {}, tb);
    el('td', { text: dShort(c.week) }, tr);
    el('td', { text: nf.format(c.accounts) }, tr);
    for (let k = 0; k < maxK; k++) {
      const v = c.cells[k];
      el('td', { class: 'c', text: v === undefined || v === null ? '' : pf.format(v), style: v === undefined || v === null ? '' : `background:${seqColor(v)};color:${v > 0.55 ? '#fff' : '#3d2b1f'}` }, tr);
    }
  }
}

function filteredPlayers() {
  const { m } = state;
  const q = $('search').value.trim().toLowerCase();
  return m.players.filter((r) => {
    if (state.seg === 'nuevos' && !r.isNew) return false;
    if (state.seg && state.seg !== 'nuevos' && r.segment !== state.seg) return false;
    if (q && !r.names.some((n) => n.toLowerCase().includes(q)) && !(r.uuid || '').includes(q)) return false;
    return true;
  });
}

function renderJugadores() {
  const { m } = state;
  // los grupos
  const chips = $('segchips');
  chips.replaceChildren();
  const opts = [{ id: null, name: 'Todos', n: m.players.filter((r) => r.hours > 0).length }, ...m.segments.map((s) => ({ id: s.id, name: s.name, n: s.players, color: SEG_COLOR[s.id] })), { id: 'nuevos', name: 'Cuentas nuevas', n: m.players.filter((r) => r.isNew).length }];
  for (const o of opts) {
    const b = el('button', { type: 'button', 'aria-pressed': String(state.seg === o.id) }, chips);
    if (o.color) el('i', { class: 'sw', style: `background:${o.color}` }, b);
    b.append(o.name);
    el('small', { text: nf.format(o.n) }, b);
    b.addEventListener('click', () => {
      state.seg = state.seg === o.id ? null : o.id;
      renderJugadores();
    });
  }
  const list = filteredPlayers();
  const byHours = [...list].sort((a, b) => b.hours - a.hours);
  // quién jugó cada día
  const calRows = state.calAll ? byHours : byHours.slice(0, CAL_ROWS);
  const maxH = Math.max(0.5, ...calRows.flatMap((r) => Object.values(r.perDay).map((v) => v / 3600)));
  const minS = state.minMinutes * 60;
  matrix($('c-calendar'), {
    label: 'Quién jugó cada día',
    empty: 'Ningún jugador con esos filtros.',
    rows: calRows.map((r) => ({ id: r.id, label: r.name, cells: m.days.map((d) => r.perDay[d] || 0) })),
    cols: m.days.map((d) => dShort(d)),
    cellHeight: 20,
    color: (v) => (!v ? '#fbf5ee' : v < minS ? '#efe3d6' : seqColor(0.18 + 0.82 * Math.min(1, v / 3600 / maxH))),
    tip: (row, j) => {
      const v = row.cells[j];
      return { title: `${row.label} · ${dLong(m.days[j])}`, lines: [{ name: 'Jugó', value: v ? hm(v / 3600) : 'nada' }, ...(v && v < minS ? [{ note: `No llegó al mínimo (${minLabel()}).` }] : [])] };
    },
    onRow: (id) => openPlayer(id),
  });
  const more = $('more-cal');
  more.hidden = byHours.length <= CAL_ROWS;
  more.textContent = state.calAll ? `Ver solo los ${CAL_ROWS} que más juegan` : `Ver los ${nf.format(byHours.length)}`;
  // dispersión
  const top5 = new Set(byHours.slice(0, 5).map((r) => r.id));
  scatter($('c-scatter'), {
    label: 'Días jugados frente a horas',
    xTitle: 'días que jugaron de verdad →',
    yTitle: '↑ horas',
    points: list.filter((r) => r.hours > 0).map((r) => ({ id: r.id, x: r.daysReal, y: r.hours, r: Math.min(16, 4 + Math.sqrt(r.visits) * 1.3), color: SEG_COLOR[r.segment] || COLORS.muted, label: r.name, row: r })),
    labels: (p) => top5.has(p.id),
    tip: (p) => ({ title: p.row.name, lines: [{ name: 'Grupo', value: SEG_NAME[p.row.segment] || '—', color: SEG_COLOR[p.row.segment] }, { name: 'Días de verdad', value: nf.format(p.row.daysReal) }, { name: 'Horas', value: nf1.format(p.row.hours) }, { name: 'Visitas', value: nf.format(p.row.visits) }, { note: 'Toca para ver su ficha.' }] }),
    onClick: (id) => openPlayer(id),
  });
  // la tabla
  const { k, dir } = state.sort;
  const SEG_ORDER = Object.fromEntries(SEGMENTS.map((s, i) => [s.id, i]));
  const val = (r) => (k === 'segment' ? (SEG_ORDER[r.segment] ?? 9) : k === 'name' ? r.name.toLowerCase() : k === 'uuid' ? r.uuid || '~' : (r[k] ?? -1));
  const rows = [...list].sort((a, b) => (val(a) < val(b) ? -dir : val(a) > val(b) ? dir : 0));
  document.querySelectorAll('#t-players th[data-k]').forEach((th) => {
    if (th.dataset.k === k) th.setAttribute('aria-sort', dir > 0 ? 'ascending' : 'descending');
    else th.removeAttribute('aria-sort');
  });
  const tb = $('t-players').tBodies[0];
  tb.replaceChildren();
  const maxAll = Math.max(1, ...rows.map((r) => r.hours));
  for (const r of rows) {
    const tr = el('tr', { class: 'row', tabindex: 0 }, tb);
    tr.addEventListener('click', () => openPlayer(r.id));
    tr.addEventListener('keydown', (e) => e.key === 'Enter' && openPlayer(r.id));
    const name = el('td', { text: r.name }, tr);
    if (r.online) el('span', { class: 'pill live', text: 'conectado' }, name);
    if (r.isNew) el('span', { class: 'pill new', text: 'nuevo' }, name);
    const sg = el('td', {}, tr);
    if (r.segment) {
      const p = el('span', { class: 'pill', text: SEG_NAME[r.segment], style: `background:${SEG_COLOR[r.segment]};color:${['ocasional', 'fantasma'].includes(r.segment) ? '#3d2b1f' : '#fff'}` }, sg);
      void p;
    } else sg.textContent = '—';
    el('td', { text: nf.format(r.daysReal7) }, tr);
    el('td', { text: nf.format(r.daysReal) }, tr);
    const h = el('td', {}, tr);
    el('i', { class: 'bar-cell', style: `width:${Math.round((48 * r.hours) / maxAll)}px` }, h);
    h.append(nf1.format(r.hours));
    el('td', { text: nf1.format(r.hours7) }, tr);
    el('td', { text: nf.format(r.visits) }, tr);
    el('td', { text: r.visits ? nf.format(Math.round(r.avgVisitMin)) : '—' }, tr);
    el('td', { text: when(r.last) }, tr);
    el('td', { text: r.created ? dShort(localDay(r.created)) : '—' }, tr);
    el('td', { class: 'uuid', text: r.uuid || 'sin UUID' }, tr);
  }
  $('players-note').textContent = `${nf.format(rows.length)} jugadores${state.seg ? ` en «${state.seg === 'nuevos' ? 'Cuentas nuevas' : SEG_NAME[state.seg]}»` : ''}. Toca uno para ver su ficha; toca una columna para ordenar.`;
}

function renderHorarios() {
  const { m } = state;
  const hrs = Array.from({ length: 24 }, (_, h) => ({ avg: m.hourly[h], max: m.hourMax[h] }));
  const drawH = () =>
    lineChart($('c-hours'), {
      n: 24,
      label: 'Conectados por hora',
      series: state.series.hours.map((x) => ({ ...x, values: hrs.map((r) => r[x.key]) })),
      yFmt: (v) => nf.format(v),
      xLabel: (i) => `${i} h`,
      tick: (i) => i % 3 === 0,
      tip: (i) => ({ title: `De ${i} a ${(i + 1) % 24} h`, lines: [{ name: 'Media a la vez', value: nf1.format(hrs[i].avg), color: COLORS.accent }, { name: 'Máximo a la vez', value: nf.format(hrs[i].max), color: COLORS.blue }] }),
    });
  toggleLegend($('lg-hours'), state.series.hours, drawH);
  drawH();
  const maxHeat = Math.max(...m.heat.flat(), 0.01);
  matrix($('c-heat'), {
    label: 'Conectados por día de la semana y hora',
    rows: m.heat.map((row, d) => ({ id: d, label: WEEKDAYS[d], cells: row })),
    cols: Array.from({ length: 24 }, (_, h) => (h % 3 === 0 ? `${h} h` : '')),
    labelWidth: 44,
    cellHeight: 26,
    color: (v) => (v > 0 ? seqColor(Math.sqrt(v / maxHeat)) : '#fbf5ee'),
    text: (v) => (v >= 1 ? nf.format(Math.round(v)) : ''),
    darkText: (v) => Math.sqrt(v / maxHeat) < 0.55,
    tip: (row, h) => ({ title: `${row.label}, de ${h} a ${(h + 1) % 24} h`, lines: [{ name: 'Conectados de media', value: nf1.format(row.cells[h]) }] }),
  });
  bars($('c-visits'), m.visitBuckets, {
    pad: 0.3,
    label: 'Duración de las visitas',
    series: [{ key: 'visits' }],
    color: (r) => (r.label === '< 5 min' ? COLORS.red : COLORS.accent),
    xLabel: (r) => r.label,
    tip: (r) => ({ title: r.label, lines: [{ name: 'Visitas', value: nf.format(r.visits) }, { name: 'Del total', value: pf.format(r.visits / Math.max(1, m.summary.visits)) }] }),
  });
  $('limit').textContent = `más de ${RESTART_LIMIT} arranques en un día`;
  const drawS = () =>
    lineChart($('c-restarts'), {
      n: m.daily.length,
      label: 'Arranques y reconexiones',
      ref: { value: RESTART_LIMIT, label: `${RESTART_LIMIT}` },
      series: state.series.stab.map((x) => ({ ...x, values: m.daily.map((r) => r[x.key]) })),
      xLabel: (i) => dShort(m.daily[i].day),
      tick: dayTick(m.daily.length),
      tip: (i) => {
        const r = m.daily[i];
        return { title: dLong(r.day), lines: [{ name: 'Arranques', value: nf.format(r.restarts), color: '#57534e' }, { name: 'Reconexiones', value: nf.format(r.recon), color: COLORS.blue }, { name: 'Jugaron de verdad', value: nf.format(r.real) }] };
      },
    });
  toggleLegend($('lg-stab'), state.series.stab, drawS);
  drawS();
  const s = m.summary;
  const stab = $('stab');
  stab.replaceChildren();
  const tile = (value, label) => {
    const d = el('div', {}, stab);
    el('b', { text: value }, d);
    el('span', { text: label }, d);
  };
  tile(nf.format(s.restarts), `arranques (${nf.format(s.badDays)} días con más de ${RESTART_LIMIT})`);
  tile(nf.format(s.cut), 'sesiones cortadas por un reinicio');
  tile(`${nf.format(s.recon)} · ${pf.format(s.reconShare)}`, 'reconexiones en menos de 3 min (de las sesiones)');
  tile(`${nf.format(s.shortVisits)}`, 'visitas de menos de 5 minutos');
  tile(nf.format(s.verr), m.versions.length ? `intentos con otra versión (${m.versions.map(([v, n]) => `${v}: ${n}`).join(', ')})` : 'intentos con otra versión de Minecraft');
  tile(nf.format(s.mojangOut), `premium que no entraron porque Mojang no contestó (de ${nf.format(s.mojang)} fallos)`);
}

// ---------- La ficha de un jugador ----------

function openPlayer(id, { keep = false } = {}) {
  const r = state.m.players.find((p) => p.id === id);
  if (!r) return closePlayer();
  state.open = id;
  $('drawer').hidden = false;
  $('drawer-bg').hidden = false;
  $('d-name').textContent = r.name;
  $('d-uuid').textContent = r.uuid || 'sin UUID';
  $('d-copy').hidden = !r.uuid;
  $('d-names').textContent = r.names.length > 1 ? `También se llamó: ${r.names.filter((n) => n !== r.name).join(', ')}` : '';
  kpis($('d-kpis'), [
    { k: 'Grupo', v: SEG_NAME[r.segment] || '—', s: r.online ? 'conectado ahora' : r.isNew ? 'cuenta nueva' : '' },
    { k: 'Días de verdad', v: nf.format(r.daysReal), s: `${r.daysReal7} de los últimos 7 · entró ${r.daysAny} días` },
    { k: 'Horas', v: nf1.format(r.hours), s: `${nf1.format(r.hours7)} los últimos 7 días` },
    { k: 'Visitas', v: nf.format(r.visits), s: `${nf.format(Math.round(r.avgVisitMin))} min de media · ${r.sessions} sesiones` },
    { k: 'Última vez', v: r.last ? dShort(localDay(r.last)) : '—', s: r.last ? hhmm(r.last) : '' },
    { k: 'Cuenta creada', v: r.created ? dShort(localDay(r.created)) : '—', s: r.created ? String(local(r.created).getUTCFullYear()) : '' },
  ]);
  const minS = state.minMinutes * 60;
  const days = state.m.days.map((d) => ({ day: d, h: (r.perDay[d] || 0) / 3600 }));
  bars($('d-daily'), days, {
    height: 190,
    label: 'Horas por día',
    series: [{ key: 'h' }],
    color: (x) => (x.h * 3600 >= minS ? COLORS.accent : COLORS.muted),
    yFmt: (v) => nf.format(v),
    xLabel: (x) => dShort(x.day),
    tick: state.m.days.length > 16 ? (x) => isMonday(x.day) : null,
    tip: (x) => ({ title: dLong(x.day), lines: [{ name: 'Jugó', value: x.h ? hm(x.h) : 'nada' }] }),
  });
  // visitas por día (las que cruzan la medianoche, en los dos días)
  const rows = [];
  for (const d of [...state.m.days].reverse()) {
    const a = dayStart(d);
    const b = dayStart(addDays(d, 1));
    const vs = r.visitList.filter((v) => v.end > a && v.start < b);
    if (!vs.length) continue;
    rows.push({
      label: dShort(d),
      bars: vs.map((v) => ({
        from: (Math.max(v.start, a) - a) / 3600,
        to: (Math.min(v.end, b) - a) / 3600,
        live: v.live,
        title: `${hhmm(v.start)}–${v.live ? 'ahora' : hhmm(v.end)} (${hm((v.end - v.start) / 3600)}${v.sessions > 1 ? `, ${v.sessions} conexiones` : ''})`,
      })),
    });
  }
  timeline($('d-timeline'), { rows });
  if (!keep) $('d-close').focus();
}

function closePlayer() {
  state.open = null;
  $('drawer').hidden = true;
  $('drawer-bg').hidden = true;
}

// ---------- Excel ----------

async function downloadExcel() {
  const { m } = state;
  if (!m) return;
  const btn = $('excel');
  btn.disabled = true;
  try {
    const s = m.summary;
    const period = `Del ${dLong(m.desde)} al ${dLong(m.hasta)} (hora del centro de México). Un día cuenta como jugado con ${minLabel()} o más. Generado el ${when(Math.floor(Date.now() / 1000))}.`;
    const int = (v) => ({ v, style: 'int' });
    const dec = (v) => ({ v: Math.round(v * 10) / 10, style: 'dec' });
    const pct = (v) => ({ v, style: 'pct' });
    const sheets = [
      {
        name: 'Resumen',
        title: 'Métricas de jugadores — Tierras Fantásticas',
        subtitle: period,
        columns: [{ title: 'Métrica', width: 50, bold: true }, { title: 'Valor', width: 14 }, { title: 'Nota', width: 70 }],
        freeze: false,
        filter: false,
        rows: [
          ['Jugadores que jugaron en el periodo', int(s.players), 'Por UUID'],
          ['Juegan de verdad al día (media)', dec(s.avgRealPerDay), `Jugadores distintos con ${minLabel()} o más ese día`],
          ['A la vez en las horas fuertes (media)', dec(s.primeAvg), m.primeHours.length ? `De ${m.primeHours[0]} a ${(m.primeHours.at(-1) + 1) % 24} h` : ''],
          ['Máximo a la vez', int(s.peak), ''],
          ['Casi a diario (5–7 de los últimos 7 días)', int(s.core), ''],
          ['Regulares (2–4 de los últimos 7 días)', int(s.regular), ''],
          ['Ocasionales (1 de los últimos 7 días)', int(s.occasional), ''],
          ['Fantasmas (entraron sin llegar al mínimo)', int(s.ghosts), ''],
          ['Se fueron (no jugaron los últimos 7 días)', int(s.gone), ''],
          ['Cuentas nuevas', int(s.newAccounts), ''],
          ['Nuevos que volvieron otro día', pct(s.newReturnedShare), `${s.newReturned} de ${s.newCanReturn}`],
          ['Nuevos que jugaron al día siguiente', pct(s.d1), ''],
          ['Nuevos que seguían a la semana', pct(s.d7plus), ''],
          ['Pegada media (diarios / semanales)', pct(s.stickiness), ''],
          ['Horas de los 5 que más juegan', pct(s.top5Share), ''],
          ['Horas de los 10 que más juegan', pct(s.top10Share), ''],
          ['Horas jugadas', dec(s.hours), ''],
          ['Visitas', int(s.visits), 'Uniendo las reconexiones de menos de 3 min'],
          ['Minutos de la visita típica (mediana)', dec(s.medianVisitMin), ''],
          ['Visitas de menos de 5 minutos', int(s.shortVisits), ''],
          ['Arranques del servidor', int(s.restarts), `${s.badDays} días con más de ${RESTART_LIMIT}`],
          ['Reconexiones en menos de 3 minutos', int(s.recon), ''],
          ['Intentos con otra versión', int(s.verr), m.versions.map(([v, n]) => `${v}: ${n}`).join(', ')],
          ['Premium que no entraron (Mojang)', int(s.mojangOut), `De ${s.mojang} fallos`],
        ],
      },
      {
        name: 'Por día',
        columns: [
          { title: 'Día', width: 12, style: 'date' },
          { title: 'Entraron', width: 10, style: 'int' },
          { title: 'Jugaron de verdad', width: 16, style: 'int' },
          { title: 'Cuenta nueva ese día', width: 18, style: 'int' },
          { title: 'Máximo a la vez', width: 15, style: 'int' },
          { title: 'Media conectados', width: 16, style: 'dec' },
          { title: 'Horas', width: 9, style: 'dec' },
          { title: 'Visitas', width: 9, style: 'int' },
          { title: 'Sesiones', width: 9, style: 'int' },
          { title: 'Arranques', width: 10, style: 'int' },
          { title: 'Reconexiones', width: 12, style: 'int' },
          { title: 'Horas de los 5 que más jugaron', width: 26, style: 'pct' },
          { title: 'Los que más jugaron', width: 70 },
        ],
        rows: m.daily.map((r) => [excelDay(r.day), r.players, r.real, r.fresh, r.peak, Math.round(r.avgOnline * 10) / 10, Math.round(r.hours * 10) / 10, r.visits, r.sessions, r.restarts, r.recon, r.topShare, r.top.map((t) => `${t.name} ${hm(t.hours)}`).join(', ')]),
      },
      {
        name: 'Grupos por día',
        columns: [{ title: 'Día', width: 12, style: 'date' }, ...SEGMENTS.map((x) => ({ title: x.name, width: 14, style: 'int' })), { title: 'Jugaron ese día', width: 15, style: 'int' }, { title: 'Jugaron en 7 días', width: 16, style: 'int' }, { title: 'Pegada', width: 9, style: 'pct' }],
        rows: m.segTrend.filter((r) => r.complete).map((r) => [excelDay(r.day), ...SEGMENTS.map((x) => r[x.id]), r.dau, r.wau, r.stick]),
      },
      {
        name: 'Retención nuevos',
        columns: [{ title: 'Días después de crear la cuenta', width: 28, style: 'int' }, { title: 'Cuentas que ya tuvieron ese día', width: 28, style: 'int' }, { title: 'Jugaron justo ese día', width: 20, style: 'pct' }, { title: 'Ese día o después', width: 18, style: 'pct' }],
        rows: m.retention.map((r) => [r.n, r.eligible, r.exact, r.plus]),
      },
      {
        name: 'Siguen del principio',
        columns: [{ title: 'Día', width: 12, style: 'date' }, { title: `De los ${m.survivalBase} de los 3 primeros días`, width: 30, style: 'pct' }, { title: 'Jugaron', width: 10, style: 'int' }],
        rows: m.survival.map((r) => [excelDay(r.day), r.share, r.n]),
      },
      {
        name: 'Por semana',
        columns: [{ title: 'Semana (lunes)', width: 15, style: 'date' }, { title: 'Jugaron', width: 10, style: 'int' }, { title: 'Siguen', width: 9, style: 'int' }, { title: 'Regresan', width: 10, style: 'int' }, { title: 'Nuevos', width: 9, style: 'int' }, { title: 'Sin semana anterior', width: 18, style: 'int' }, { title: 'No volvieron', width: 13, style: 'int' }, { title: 'Se quedaron de la anterior', width: 24, style: 'pct' }, { title: 'Completa', width: 10 }],
        rows: m.weekly.map((r) => [excelDay(r.week), r.active, r.stay, r.back, r.fresh, r.before, r.lost, r.kept, !r.partial]),
      },
      {
        name: 'Cohortes',
        columns: [{ title: 'Creadas la semana del', width: 20, style: 'date' }, { title: 'Cuentas', width: 9, style: 'int' }, ...Array.from({ length: Math.max(0, ...m.cohorts.map((c) => c.cells.length)) }, (_, k) => ({ title: k === 0 ? 'Esa semana' : `+${k} semanas`, width: 12, style: 'pct' }))],
        rows: m.cohorts.filter((c) => c.accounts).map((c) => [excelDay(c.week), c.accounts, ...c.cells]),
      },
      {
        name: 'Concentración',
        columns: [{ title: 'Puesto', width: 8, style: 'int' }, { title: 'Jugador', width: 20 }, { title: 'Horas', width: 9, style: 'dec' }, { title: 'Horas acumuladas', width: 18, style: 'pct' }],
        rows: m.pareto.map((p) => [p.rank, p.name, Math.round(p.hours * 10) / 10, p.share]),
      },
      {
        name: 'Por hora',
        columns: [{ title: 'Hora', width: 7, style: 'int' }, { title: 'Media a la vez', width: 14, style: 'dec' }, { title: 'Máximo a la vez', width: 15, style: 'int' }, ...WEEKDAYS.map((d) => ({ title: d, width: 8, style: 'dec' }))],
        rows: m.hourly.map((v, h) => [h, Math.round(v * 100) / 100, m.hourMax[h], ...m.heat.map((row) => Math.round(row[h] * 100) / 100)]),
      },
      {
        name: 'Duración visitas',
        columns: [{ title: 'Duración', width: 14 }, { title: 'Visitas', width: 10, style: 'int' }],
        rows: m.visitBuckets.map((b) => [b.label, b.visits]),
      },
      {
        name: 'Jugadores',
        columns: [
          { title: 'Jugador', width: 20, bold: true },
          { title: 'UUID', width: 38 },
          { title: 'Grupo', width: 14 },
          { title: 'Cuenta nueva', width: 13 },
          { title: 'Días de verdad (últimos 7)', width: 22, style: 'int' },
          { title: 'Días de verdad', width: 14, style: 'int' },
          { title: 'Días que entró', width: 14, style: 'int' },
          { title: 'Horas', width: 9, style: 'dec' },
          { title: 'Horas (últimos 7)', width: 16, style: 'dec' },
          { title: 'Visitas', width: 9, style: 'int' },
          { title: 'Min por visita', width: 13, style: 'dec' },
          { title: 'Primera vez', width: 17, style: 'datetime' },
          { title: 'Última vez', width: 17, style: 'datetime' },
          { title: 'Cuenta creada', width: 17, style: 'datetime' },
          { title: 'Otros nombres', width: 24 },
        ],
        rows: m.players.map((r) => [
          r.name,
          r.uuid || '',
          SEG_NAME[r.segment] || '',
          r.isNew,
          r.daysReal7,
          r.daysReal,
          r.daysAny,
          Math.round(r.hours * 10) / 10,
          Math.round(r.hours7 * 10) / 10,
          r.visits,
          Math.round(r.avgVisitMin * 10) / 10,
          r.first ? excelDate(r.first) : null,
          r.last ? excelDate(r.last) : null,
          r.created ? excelDate(r.created) : null,
          r.names.filter((n) => n !== r.name).join(', '),
        ]),
      },
      {
        name: 'Quién jugó cada día',
        columns: [{ title: 'Jugador', width: 20, bold: true }, ...m.days.map((d) => ({ title: dShort(d), width: 8, style: 'dec' })), { title: 'Total horas', width: 11, style: 'dec' }],
        rows: m.players.filter((r) => r.hours > 0).map((r) => [r.name, ...m.days.map((d) => (r.perDay[d] ? Math.round((r.perDay[d] / 3600) * 10) / 10 : null)), Math.round(r.hours * 10) / 10]),
      },
      {
        name: 'Sesiones',
        columns: [{ title: 'Jugador', width: 20 }, { title: 'UUID', width: 38 }, { title: 'Entró', width: 17, style: 'datetime' }, { title: 'Salió', width: 17, style: 'datetime' }, { title: 'Minutos', width: 10, style: 'dec' }, { title: 'Cortada por un reinicio', width: 21 }, { title: 'En curso', width: 10 }],
        rows: m.sessions.map((x) => [x.name, x.uuid || '', excelDate(x.start), excelDate(x.end), Math.round(x.minutes * 10) / 10, x.cut, x.live]),
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
    a.download = `metricas-tierras-fantasticas-${m.desde}-a-${m.hasta}.xlsx`;
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
let resizeTimer = 0;
new ResizeObserver(() => {
  const w = document.documentElement.clientWidth;
  if (Math.abs(w - lastWidth) > 4) {
    lastWidth = w;
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(render, 120);
  }
}).observe(document.documentElement);
setInterval(() => {
  if (document.visibilityState === 'visible' && state.data && !$('panel').hidden) load({ quiet: true });
}, REFRESH_MS);

load();
