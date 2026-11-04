// (4 nov 2026) Las cuentas de /metricas: de las sesiones de nLogin (GET /api/metricas) a lo que enseñan las gráficas y
// el Excel. Se hace en el navegador (la web solo da las sesiones). Todo en la hora del centro de México (UTC-6, sin
// horario de verano desde 2022). Sin dependencias: se usa igual en la página y en las pruebas (node).

export const OFFSET_S = 6 * 3600;
const DAY_S = 86400;
// Un jugador que sale y vuelve a entrar en menos de 3 minutos sin un apagado en medio: se le cerró el juego o se cortó
export const RECONNECT_S = 180;
// Una salida a menos de 20 s de un apagado: la echó el reinicio
const CUT_S = 20;
// Más arranques que estos en un día es un día malo (en rojo)
export const RESTART_LIMIT = 4;
// Las horas fuertes: las que tienen al menos el 80 % de la hora con más gente
const PRIME_SHARE = 0.8;

export const localDay = (sec) => new Date((sec - OFFSET_S) * 1000).toISOString().slice(0, 10);
export const localHour = (sec) => new Date((sec - OFFSET_S) * 1000).getUTCHours();
// Lunes = 0 … domingo = 6
export const localWeekday = (sec) => (new Date((sec - OFFSET_S) * 1000).getUTCDay() + 6) % 7;
export const dayStart = (iso) => Date.parse(`${iso}T00:00:00Z`) / 1000 + OFFSET_S;
export function addDays(iso, n) {
  const d = new Date(`${iso}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}
export const mondayOf = (iso) => addDays(iso, -((new Date(`${iso}T00:00:00Z`).getUTCDay() + 6) % 7));

function median(list) {
  if (!list.length) return 0;
  const s = [...list].sort((a, b) => a - b);
  const m = s.length >> 1;
  return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
}

// ¿Hay algún valor de la lista ordenada a menos de w de t?
function near(sorted, t, w) {
  let lo = 0;
  let hi = sorted.length;
  while (lo < hi) {
    const mid = (lo + hi) >> 1;
    if (sorted[mid] < t - w) lo = mid + 1;
    else hi = mid;
  }
  return lo < sorted.length && sorted[lo] <= t + w;
}

// «v_1_20_1» → «1.20.1»
export const versionName = (v) => (typeof v === 'string' && /^v_[\d_]+$/i.test(v) ? v.slice(2).replace(/_/g, '.') : 'desconocida');

export function compute(data) {
  const now = Math.floor(data.now / 1000);
  const today = localDay(now);
  const desde = data.desde;
  const from = dayStart(desde);
  const days = [];
  for (let d = desde; d <= today; d = addDays(d, 1)) days.push(d);

  const online = new Set((data.online || []).map((n) => String(n).toLowerCase()));
  const players = data.players.map(([name, created], i) => ({
    i,
    name,
    key: String(name).toLowerCase(),
    created,
    createdDay: created ? localDay(created) : null,
    isNew: Boolean(created && created >= from),
    online: online.has(String(name).toLowerCase()),
  }));
  const stops = [...(data.stops || [])].sort((a, b) => a - b);
  const starts = [...(data.starts || [])].sort((a, b) => a - b);

  // Las sesiones; las de los que están conectados ahora cuentan hasta ahora
  const sessions = data.sessions.map(([p, s, d]) => ({ p, start: s, end: s + d, dur: d, day: localDay(s), cut: near(stops, s + d, CUT_S), live: false }));
  if (data.bridge) {
    for (const [p, s] of data.open || []) {
      if (players[p]?.online && s >= from && now > s) sessions.push({ p, start: s, end: now, dur: now - s, day: localDay(s), cut: false, live: true });
    }
  }
  sessions.sort((a, b) => a.start - b.start);

  // Por jugador
  const per = players.map(() => ({ sessions: 0, secs: 0, days: new Set(), first: null, last: null, list: [] }));
  for (const s of sessions) {
    const q = per[s.p];
    q.sessions++;
    q.secs += s.dur;
    q.days.add(s.day);
    q.first = q.first === null ? s.start : Math.min(q.first, s.start);
    q.last = q.last === null ? s.end : Math.max(q.last, s.end);
    q.list.push(s);
  }

  // Reconexiones: la siguiente sesión empieza en menos de 3 minutos y la anterior no la cortó un apagado
  const recon = new Map();
  let reconTotal = 0;
  for (const q of per) {
    for (let k = 1; k < q.list.length; k++) {
      const prev = q.list[k - 1];
      const next = q.list[k];
      const gap = next.start - prev.end;
      if (gap >= 0 && gap <= RECONNECT_S && !prev.cut) {
        recon.set(next.day, (recon.get(next.day) || 0) + 1);
        reconTotal++;
      }
    }
  }

  // Por día
  const byDay = new Map(days.map((d) => [d, { day: d, players: new Set(), fresh: new Set(), sessions: 0, secs: 0, peak: 0, newAccounts: 0, restarts: 0, recon: recon.get(d) || 0, verr: 0, mojang: 0 }]));
  for (const s of sessions) {
    const row = byDay.get(s.day);
    if (!row) continue;
    row.players.add(s.p);
    if (players[s.p].createdDay === s.day) row.fresh.add(s.p);
    row.sessions++;
    row.secs += s.dur;
  }
  for (const pl of players) if (pl.isNew && byDay.has(pl.createdDay)) byDay.get(pl.createdDay).newAccounts++;
  for (const t of starts) if (byDay.has(localDay(t))) byDay.get(localDay(t)).restarts++;
  for (const [, t] of data.verr || []) if (byDay.has(localDay(t))) byDay.get(localDay(t)).verr++;

  // Mojang no contestó: ¿entró igual en 3 minutos?
  const startsOf = new Map();
  for (const s of sessions) {
    const k = players[s.p].key;
    if (!startsOf.has(k)) startsOf.set(k, []);
    startsOf.get(k).push(s.start);
  }
  let mojangOut = 0;
  for (const [p, t] of data.mojang || []) {
    if (byDay.has(localDay(t))) byDay.get(localDay(t)).mojang++;
    const k = p >= 0 ? players[p]?.key : null;
    if (!(k && (startsOf.get(k) || []).some((s) => s >= t && s - t <= RECONNECT_S))) mojangOut++;
  }

  // Máximo a la vez de cada día
  const points = [];
  for (const s of sessions) points.push([s.start, 1], [s.end, -1]);
  points.sort((a, b) => a[0] - b[0] || a[1] - b[1]);
  let cur = 0;
  let peakAll = 0;
  for (const [t, k] of points) {
    cur += k;
    const row = byDay.get(localDay(t));
    if (row && cur > row.peak) row.peak = cur;
    if (cur > peakAll && t >= from) peakAll = cur;
  }

  // Por hora: media de conectados a cada hora (y por día de la semana), en los días con alguien
  const heatSecs = Array.from({ length: 7 }, () => Array(24).fill(0));
  for (const s of sessions) {
    let t = s.start;
    while (t < s.end) {
      const hourEnd = t - ((t - OFFSET_S) % 3600) + 3600;
      const upto = Math.min(s.end, hourEnd);
      heatSecs[localWeekday(t)][localHour(t)] += upto - t;
      t = upto;
    }
  }
  const openDays = days.filter((d) => byDay.get(d).players.size > 0);
  const weekdayDays = Array(7).fill(0);
  for (const d of openDays) weekdayDays[localWeekday(dayStart(d))]++;
  const heat = heatSecs.map((row, w) => row.map((v) => (weekdayDays[w] ? v / 3600 / weekdayDays[w] : 0)));
  const hourly = Array.from({ length: 24 }, (_, h) => (openDays.length ? heatSecs.reduce((a, row) => a + row[h], 0) / 3600 / openDays.length : 0));
  const hourTop = Math.max(...hourly, 0);
  const primeHours = hourly.map((v, h) => (hourTop > 0 && v >= hourTop * PRIME_SHARE ? h : -1)).filter((h) => h >= 0);

  // Por semana (de lunes): los que siguen de la anterior, los que regresan, los nuevos
  const weeks = [];
  for (let w = mondayOf(desde); w <= today; w = addDays(w, 7)) weeks.push(w);
  const activeIn = (w) => {
    const set = new Set();
    for (const s of sessions) if (s.day >= w && s.day <= addDays(w, 6)) set.add(s.p);
    return set;
  };
  const weekly = [];
  let prev = null;
  for (const w of weeks) {
    const active = activeIn(w);
    const fresh = new Set([...active].filter((p) => players[p].isNew && players[p].createdDay >= w));
    const rest = [...active].filter((p) => !fresh.has(p));
    weekly.push({
      week: w,
      partial: addDays(w, 6) > today,
      active: active.size,
      fresh: fresh.size,
      // la primera semana no tiene una anterior con la que comparar
      stay: prev ? rest.filter((p) => prev.has(p)).length : null,
      back: prev ? rest.filter((p) => !prev.has(p)).length : null,
      before: prev ? null : rest.length,
      lost: prev ? [...prev].filter((p) => !active.has(p)).length : null,
    });
    prev = active;
  }

  // Días jugados por jugador
  const buckets = [
    ['1', 1, 1],
    ['2', 2, 2],
    ['3', 3, 3],
    ['4–7', 4, 7],
    ['8–14', 8, 14],
    ['15+', 15, Infinity],
  ];
  const withPlay = per.map((q, i) => ({ q, i })).filter(({ q }) => q.sessions > 0);
  const daysPlayed = buckets.map(([label, lo, hi]) => ({ label, players: withPlay.filter(({ q }) => q.days.size >= lo && q.days.size <= hi).length }));

  // Los nuevos: ¿vuelven?
  const weekAgo = now - 7 * DAY_S;
  const isActive7 = (q) => q.last !== null && q.last >= weekAgo;
  const newPlayers = players.filter((p) => p.isNew);
  const returned = (p) => [...per[p.i].days].some((d) => d > p.createdDay);
  const returnedWeek = (p) => [...per[p.i].days].some((d) => d >= addDays(p.createdDay, 7));
  const cohort = newPlayers.filter((p) => p.created <= weekAgo);
  const funnel = [
    { step: 'Cuenta nueva', players: cohort.length },
    { step: 'Jugaron', players: cohort.filter((p) => per[p.i].sessions > 0).length },
    { step: 'Volvieron otro día', players: cohort.filter((p) => returned(p)).length },
    { step: 'Volvieron una semana después o más', players: cohort.filter((p) => returnedWeek(p)).length },
    { step: 'Siguen jugando (última semana)', players: cohort.filter((p) => returned(p) && isActive7(per[p.i])).length },
  ];
  // «Nuevos que vuelven»: de los nuevos que jugaron y ya tuvieron al menos un día para volver
  const canReturn = newPlayers.filter((p) => per[p.i].sessions > 0 && p.createdDay < today);
  const newReturned = canReturn.filter((p) => returned(p)).length;

  // Por semana de creación de la cuenta
  const cohorts = weeks.map((w) => {
    const list = newPlayers.filter((p) => p.createdDay >= w && p.createdDay <= addDays(w, 6));
    const played = list.filter((p) => per[p.i].sessions > 0);
    const eligible7 = list.filter((p) => p.created <= weekAgo);
    return {
      week: w,
      accounts: list.length,
      played: played.length,
      returned: played.filter((p) => returned(p)).length,
      returned7: eligible7.length ? eligible7.filter((p) => returnedWeek(p)).length : null,
      eligible7: eligible7.length,
      active: list.filter((p) => isActive7(per[p.i])).length,
    };
  });

  // Últimos 7 días y los 7 de antes
  const prevWeekAgo = now - 14 * DAY_S;
  const active7 = per.filter((q) => q.list.some((s) => s.end >= weekAgo)).length;
  const active7prev = from <= prevWeekAgo ? per.filter((q) => q.list.some((s) => s.end >= prevWeekAgo && s.start < weekAgo)).length : null;

  const durations = sessions.filter((s) => !s.live).map((s) => s.dur);
  const versions = new Map();
  for (const [v] of data.verr || []) versions.set(versionName(v), (versions.get(versionName(v)) || 0) + 1);

  const playerRows = players
    .map((p) => {
      const q = per[p.i];
      return {
        name: p.name,
        created: p.created,
        isNew: p.isNew,
        first: q.first,
        last: q.last,
        days: q.days.size,
        sessions: q.sessions,
        hours: q.secs / 3600,
        avgMin: q.sessions ? q.secs / q.sessions / 60 : 0,
        state: p.online ? 'Conectado' : q.last !== null && q.last >= weekAgo ? 'Activo' : q.sessions ? 'No ha vuelto' : 'Sin jugar',
      };
    })
    .filter((r) => r.sessions > 0 || r.isNew)
    .sort((a, b) => b.hours - a.hours);

  const daily = days.map((d) => {
    const r = byDay.get(d);
    return {
      day: d,
      players: r.players.size,
      fresh: r.fresh.size,
      old: r.players.size - r.fresh.size,
      sessions: r.sessions,
      hours: r.secs / 3600,
      peak: r.peak,
      newAccounts: r.newAccounts,
      restarts: r.restarts,
      recon: r.recon,
      verr: r.verr,
      mojang: r.mojang,
    };
  });

  return {
    desde,
    today,
    now,
    days,
    daily,
    weekly,
    hourly,
    heat,
    primeHours,
    daysPlayed,
    funnel,
    cohorts,
    players: playerRows,
    sessions: sessions.map((s) => ({ name: players[s.p].name, start: s.start, end: s.end, minutes: s.dur / 60, cut: s.cut, live: s.live })),
    restartTimes: starts.filter((t) => t >= from),
    versions: [...versions.entries()].sort((a, b) => b[1] - a[1]),
    summary: {
      players: withPlay.length,
      active7,
      active7prev,
      newAccounts: newPlayers.length,
      newReturned,
      newCanReturn: canReturn.length,
      newReturnedShare: canReturn.length ? newReturned / canReturn.length : null,
      sessions: sessions.length,
      medianMin: median(durations) / 60,
      hours: sessions.reduce((a, s) => a + s.dur, 0) / 3600,
      peak: peakAll,
      oneDay: withPlay.filter(({ q }) => q.days.size === 1).length,
      restarts: starts.filter((t) => t >= from).length,
      badDays: daily.filter((r) => r.restarts > RESTART_LIMIT).length,
      cut: sessions.filter((s) => s.cut).length,
      recon: reconTotal,
      reconShare: sessions.length ? reconTotal / sessions.length : 0,
      verr: (data.verr || []).length,
      mojang: (data.mojang || []).length,
      mojangOut,
      online: players.filter((p) => p.online).length,
      openDays: openDays.length,
    },
  };
}
