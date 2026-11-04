// (4 nov 2026) Las cuentas de /metricas: de las sesiones (GET /api/metricas) a lo que enseñan las gráficas y el Excel.
// Se hace en el navegador. Todo en la hora del centro de México (UTC-6, sin horario de verano desde 2022). Sin
// dependencias: se usa igual en la página y en las pruebas (node).
//
// (v2, el dueño: «dices 230 sesiones, pero si solo 1 o 2 jugadores hicieron esas sesiones no es un día exitoso…
// todo ese montón de gente que dices activa son fantasmas… cataloga al jugador por su UUID») Por eso:
// - cada jugador es su UUID (los nombres con el mismo UUID son la misma persona);
// - se cuentan VISITAS (las sesiones del mismo jugador separadas por menos de 3 minutos son una sola: una reconexión
//   no es otra visita) y, sobre todo, JUGADORES y HORAS;
// - un día cuenta como «jugó» solo si ese día estuvo al menos minMinutes (por defecto 30) en el servidor;
// - los jugadores se reparten en segmentos según los días que jugaron de verdad en los últimos 7 días.

export const OFFSET_S = 6 * 3600;
const DAY_S = 86400;
// Un jugador que sale y vuelve a entrar en menos de 3 minutos: la misma visita (se le cerró el juego o se cortó)
export const RECONNECT_S = 180;
// Una salida a menos de 20 s de un apagado: la echó el reinicio
const CUT_S = 20;
// Más arranques que estos en un día es un día malo (en rojo)
export const RESTART_LIMIT = 4;
// Las horas fuertes: las que tienen al menos el 80 % de la hora con más gente
const PRIME_SHARE = 0.8;
export const DEFAULT_MIN_MINUTES = 30;

// Los segmentos (por los días que jugó de verdad en los últimos 7 días), del más al menos metido
export const SEGMENTS = [
  { id: 'diario', name: 'Casi a diario', hint: '5 a 7 de los últimos 7 días' },
  { id: 'regular', name: 'Regulares', hint: '2 a 4 de los últimos 7 días' },
  { id: 'ocasional', name: 'Ocasionales', hint: '1 de los últimos 7 días' },
  { id: 'fantasma', name: 'Fantasmas', hint: 'entraron los últimos 7 días, pero ningún día llegaron al mínimo' },
  { id: 'sefue', name: 'Se fueron', hint: 'jugaron en el periodo, pero no los últimos 7 días' },
];

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
export const daysBetween = (a, b) => Math.round((Date.parse(`${b}T00:00:00Z`) - Date.parse(`${a}T00:00:00Z`)) / 86400000);
export const mondayOf = (iso) => addDays(iso, -((new Date(`${iso}T00:00:00Z`).getUTCDay() + 6) % 7));

export function median(list) {
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

// Reparte [start, end) entre los días (hora de México): [[día, segundos], …]
function splitDays(start, end) {
  const out = [];
  let t = start;
  while (t < end) {
    const d = localDay(t);
    const upto = Math.min(end, dayStart(addDays(d, 1)));
    out.push([d, upto - t]);
    t = upto;
  }
  return out;
}

export function compute(data, opts = {}) {
  const minMinutes = Number.isFinite(opts.minMinutes) ? opts.minMinutes : DEFAULT_MIN_MINUTES;
  const minS = minMinutes * 60;
  const now = Math.floor(data.now / 1000);
  const today = localDay(now);
  const desde = data.desde;
  const hasta = opts.hasta && opts.hasta >= desde && opts.hasta <= today ? opts.hasta : today;
  const from = dayStart(desde);
  const to = dayStart(addDays(hasta, 1));
  const endT = Math.min(now, to);
  const days = [];
  for (let d = desde; d <= hasta; d = addDays(d, 1)) days.push(d);
  const daySet = new Set(days);

  // --- Las personas: una por UUID (si no hay, por nombre) ---
  const online = new Set((data.online || []).map((n) => String(n).toLowerCase()));
  const persons = new Map();
  const pidOf = data.players.map(([name, created, uuid]) => {
    const id = uuid ? `u:${uuid}` : `n:${String(name).toLowerCase()}`;
    if (!persons.has(id)) persons.set(id, { id, uuid: uuid || null, name, names: new Set(), created: null, online: false, lastSeenName: -1 });
    const p = persons.get(id);
    p.names.add(name);
    if (created && (p.created === null || created < p.created)) p.created = created;
    if (online.has(String(name).toLowerCase())) p.online = true;
    return id;
  });
  for (const p of persons.values()) {
    p.createdDay = p.created ? localDay(p.created) : null;
    p.isNew = Boolean(p.created && p.created >= from && p.created < to);
  }

  // --- Sesiones (recortadas al periodo); las de los conectados ahora cuentan hasta ahora ---
  const stops = [...(data.stops || [])].sort((a, b) => a - b);
  const raw = [];
  for (const [i, s, d] of data.sessions) {
    const p = persons.get(pidOf[i]);
    if (s + d > p.lastSeenName) {
      p.lastSeenName = s + d;
      p.name = data.players[i][0];
    }
    raw.push({ pid: pidOf[i], start: s, end: s + d, cut: near(stops, s + d, CUT_S), live: false });
  }
  if (data.bridge) {
    for (const [i, s] of data.open || []) {
      const p = persons.get(pidOf[i]);
      if (p?.online && now > s) raw.push({ pid: pidOf[i], start: s, end: now, cut: false, live: true });
    }
  }
  const sessions = raw
    .filter((s) => s.end > from && s.start < endT)
    .map((s) => ({ ...s, start: Math.max(s.start, from), end: Math.min(s.end, endT) }))
    .filter((s) => s.end > s.start)
    .sort((a, b) => a.start - b.start);

  // --- Por persona: sesiones, visitas (uniendo reconexiones) y segundos de cada día ---
  const per = new Map([...persons.keys()].map((id) => [id, { list: [], visits: [], secsByDay: new Map(), secs: 0 }]));
  for (const s of sessions) per.get(s.pid).list.push(s);
  const reconByDay = new Map();
  let reconTotal = 0;
  for (const q of per.values()) {
    let cur = null;
    for (let k = 0; k < q.list.length; k++) {
      const s = q.list[k];
      if (k) {
        const prev = q.list[k - 1];
        const gap = s.start - prev.end;
        if (gap >= 0 && gap <= RECONNECT_S && !prev.cut) {
          const d = localDay(s.start);
          reconByDay.set(d, (reconByDay.get(d) || 0) + 1);
          reconTotal++;
        }
      }
      if (cur && s.start - cur.end <= RECONNECT_S) {
        cur.end = Math.max(cur.end, s.end);
        cur.sessions++;
        cur.live = cur.live || s.live;
      } else {
        cur = { start: s.start, end: s.end, sessions: 1, live: s.live };
        q.visits.push(cur);
      }
      for (const [d, secs] of splitDays(s.start, s.end)) {
        q.secsByDay.set(d, (q.secsByDay.get(d) || 0) + secs);
        q.secs += secs;
      }
    }
  }
  const playedReal = (q, d) => (q.secsByDay.get(d) || 0) >= minS;
  const realDays = (q, a, b) => [...q.secsByDay.entries()].filter(([d, v]) => d >= a && d <= b && v >= minS).length;

  // --- Por día ---
  const starts = [...(data.starts || [])].filter((t) => t >= from && t < to).sort((a, b) => a - b);
  const byDay = new Map(days.map((d) => [d, { day: d, any: 0, real: 0, fresh: 0, secs: 0, visits: 0, sessions: 0, peak: 0, restarts: 0, recon: reconByDay.get(d) || 0, verr: 0, mojang: 0, newAccounts: 0, top: [] }]));
  for (const [id, q] of per) {
    for (const [d, secs] of q.secsByDay) {
      const row = byDay.get(d);
      if (!row) continue;
      row.any++;
      if (secs >= minS) row.real++;
      if (persons.get(id).createdDay === d) row.fresh++;
      row.secs += secs;
      row.top.push([persons.get(id).name, secs]);
    }
    for (const v of q.visits) if (byDay.has(localDay(v.start))) byDay.get(localDay(v.start)).visits++;
    for (const s of q.list) if (byDay.has(localDay(s.start))) byDay.get(localDay(s.start)).sessions++;
  }
  for (const p of persons.values()) if (p.isNew && byDay.has(p.createdDay)) byDay.get(p.createdDay).newAccounts++;
  for (const t of starts) byDay.get(localDay(t)).restarts++;
  for (const [, t] of data.verr || []) if (byDay.has(localDay(t))) byDay.get(localDay(t)).verr++;
  let mojangOut = 0;
  let mojangAll = 0;
  const startsOf = new Map();
  for (const s of sessions) {
    const k = persons.get(s.pid).id;
    if (!startsOf.has(k)) startsOf.set(k, []);
    startsOf.get(k).push(s.start);
  }
  for (const [i, t] of data.mojang || []) {
    if (!byDay.has(localDay(t))) continue;
    mojangAll++;
    byDay.get(localDay(t)).mojang++;
    const k = i >= 0 ? pidOf[i] : null;
    if (!(k && (startsOf.get(k) || []).some((s) => s >= t && s - t <= RECONNECT_S))) mojangOut++;
  }

  // A la vez: máximo de cada día, por hora del día (media y máximo) y por día de la semana × hora
  const points = [];
  for (const s of sessions) points.push([s.start, 1], [s.end, -1]);
  points.sort((a, b) => a[0] - b[0] || a[1] - b[1]);
  let cur = 0;
  let peakAll = 0;
  const hourMax = Array(24).fill(0);
  for (const [t, k] of points) {
    cur += k;
    const d = localDay(t);
    const row = byDay.get(d);
    if (row && cur > row.peak) row.peak = cur;
    if (cur > peakAll) peakAll = cur;
    if (k > 0) hourMax[localHour(t)] = Math.max(hourMax[localHour(t)], cur);
  }
  const heatSecs = Array.from({ length: 7 }, () => Array(24).fill(0));
  for (const s of sessions) {
    let t = s.start;
    while (t < s.end) {
      const upto = Math.min(s.end, t - (t % 3600) + 3600);
      heatSecs[localWeekday(t)][localHour(t)] += upto - t;
      t = upto;
    }
  }
  const openDays = days.filter((d) => byDay.get(d).any > 0);
  const weekdayDays = Array(7).fill(0);
  for (const d of openDays) weekdayDays[localWeekday(dayStart(d))]++;
  const heat = heatSecs.map((row, w) => row.map((v) => (weekdayDays[w] ? v / 3600 / weekdayDays[w] : 0)));
  const hourly = Array.from({ length: 24 }, (_, h) => (openDays.length ? heatSecs.reduce((a, row) => a + row[h], 0) / 3600 / openDays.length : 0));
  const hourTop = Math.max(...hourly, 0);
  const primeHours = hourly.map((v, h) => (hourTop > 0 && v >= hourTop * PRIME_SHARE ? h : -1)).filter((h) => h >= 0);
  const primeAvg = primeHours.length ? primeHours.reduce((a, h) => a + hourly[h], 0) / primeHours.length : 0;

  // --- Segmentos (últimos 7 días hasta «hasta») y su evolución día a día ---
  const segmentOf = (q, endDay) => {
    const a = addDays(endDay, -6);
    const real7 = realDays(q, a, endDay);
    const any7 = [...q.secsByDay.keys()].some((d) => d >= a && d <= endDay);
    const anyBefore = [...q.secsByDay.keys()].some((d) => d < a);
    if (real7 >= 5) return 'diario';
    if (real7 >= 2) return 'regular';
    if (real7 === 1) return 'ocasional';
    if (any7) return 'fantasma';
    return anyBefore ? 'sefue' : null;
  };
  const segCount = Object.fromEntries(SEGMENTS.map((s) => [s.id, 0]));
  const segOfPerson = new Map();
  for (const [id, q] of per) {
    if (!q.secs) continue;
    const seg = segmentOf(q, hasta);
    segOfPerson.set(id, seg);
    if (seg) segCount[seg]++;
  }
  const segTrend = days.map((d) => {
    const row = { day: d, complete: daysBetween(desde, d) >= 6 };
    for (const s of SEGMENTS) row[s.id] = 0;
    for (const q of per.values()) {
      const seg = segmentOf(q, d);
      if (seg) row[seg]++;
    }
    // jugadores distintos de verdad en los 7 días (WAU) y la «pegada» DAU/WAU
    row.wau = [...per.values()].filter((q) => realDays(q, addDays(d, -6), d) > 0).length;
    row.dau = byDay.get(d).real;
    row.stick = row.wau ? row.dau / row.wau : null;
    return row;
  });

  // --- Por semana: los que siguen de la anterior, los que regresan, los nuevos (con días de verdad) ---
  const weeks = [];
  for (let w = mondayOf(desde); w <= hasta; w = addDays(w, 7)) weeks.push(w);
  const activeWeek = new Map(
    weeks.map((w) => [w, new Set([...per.entries()].filter(([, q]) => realDays(q, w, addDays(w, 6)) > 0).map(([id]) => id))]),
  );
  const weekly = [];
  let prev = null;
  for (const w of weeks) {
    const active = activeWeek.get(w);
    const fresh = [...active].filter((id) => persons.get(id).isNew && persons.get(id).createdDay >= w);
    const rest = [...active].filter((id) => !fresh.includes(id));
    weekly.push({
      week: w,
      partial: addDays(w, 6) > hasta || w < desde,
      active: active.size,
      fresh: fresh.length,
      stay: prev ? rest.filter((id) => prev.has(id)).length : null,
      back: prev ? rest.filter((id) => !prev.has(id)).length : null,
      before: prev ? null : rest.length,
      lost: prev ? [...prev].filter((id) => !active.has(id)).length : null,
      // de los de la semana anterior, cuántos siguen
      kept: prev && prev.size ? [...prev].filter((id) => active.has(id)).length / prev.size : null,
    });
    prev = active;
  }

  // --- Retención ---
  const newPeople = [...persons.values()].filter((p) => p.isNew);
  const maxN = Math.min(30, Math.max(0, ...newPeople.map((p) => daysBetween(p.createdDay, hasta))));
  const retention = [];
  for (let n = 0; n <= maxN; n++) {
    const eligible = newPeople.filter((p) => daysBetween(p.createdDay, hasta) >= n);
    if (!eligible.length) break;
    const q = (p) => per.get(p.id);
    const exact = eligible.filter((p) => playedReal(q(p), addDays(p.createdDay, n))).length;
    const plus = eligible.filter((p) => [...q(p).secsByDay.entries()].some(([d, v]) => v >= minS && d >= addDays(p.createdDay, n))).length;
    retention.push({ n, eligible: eligible.length, exact: exact / eligible.length, plus: plus / eligible.length, exactN: exact, plusN: plus });
  }
  // De los que jugaban al principio del periodo (días de verdad en los 3 primeros días), cuántos siguen cada día
  const baseIds = [...per.entries()].filter(([, q]) => realDays(q, desde, addDays(desde, 2)) > 0).map(([id]) => id);
  const survival = days.map((d) => ({
    day: d,
    share: baseIds.length ? baseIds.filter((id) => playedReal(per.get(id), d)).length / baseIds.length : null,
    n: baseIds.filter((id) => playedReal(per.get(id), d)).length,
  }));
  // Cohortes por semana de creación × semanas después
  const cohorts = weeks.map((w) => {
    const list = newPeople.filter((p) => p.createdDay >= w && p.createdDay <= addDays(w, 6));
    const cells = [];
    for (let k = 0; addDays(w, 7 * k) <= hasta; k++) {
      const a = addDays(w, 7 * k);
      cells.push(list.length ? list.filter((p) => realDays(per.get(p.id), a, addDays(a, 6)) > 0).length / list.length : null);
    }
    const played = list.filter((p) => per.get(p.id).secs > 0);
    return {
      week: w,
      accounts: list.length,
      played: played.length,
      returned: played.filter((p) => [...per.get(p.id).secsByDay.entries()].some(([d, v]) => v >= minS && d > p.createdDay)).length,
      active: list.filter((p) => ['diario', 'regular', 'ocasional'].includes(segOfPerson.get(p.id))).length,
      cells,
    };
  });
  const canReturn = newPeople.filter((p) => per.get(p.id).secs > 0 && p.createdDay < hasta);
  const newReturned = canReturn.filter((p) => [...per.get(p.id).secsByDay.entries()].some(([d, v]) => v >= minS && d > p.createdDay)).length;

  // --- Concentración: cuánto de las horas ponen los que más juegan ---
  const ranked = [...per.entries()].filter(([, q]) => q.secs > 0).sort((a, b) => b[1].secs - a[1].secs);
  const totalSecs = ranked.reduce((a, [, q]) => a + q.secs, 0);
  let acc = 0;
  const pareto = ranked.map(([id, q], i) => {
    acc += q.secs;
    return { rank: i + 1, name: persons.get(id).name, hours: q.secs / 3600, share: totalSecs ? acc / totalSecs : 0 };
  });
  const topShare = (k) => (pareto.length ? pareto[Math.min(k, pareto.length) - 1].share : 0);

  // --- Última vez que jugaron (los del periodo) ---
  const lastBuckets = [
    { id: 'hoy', name: 'Hoy', lo: 0, hi: 0 },
    { id: 'd1', name: 'Ayer', lo: 1, hi: 1 },
    { id: 'd3', name: 'Hace 2–3 días', lo: 2, hi: 3 },
    { id: 'd7', name: 'Hace 4–7 días', lo: 4, hi: 7 },
    { id: 'd14', name: 'Hace 8–14 días', lo: 8, hi: 14 },
    { id: 'd15', name: 'Hace más de 14 días', lo: 15, hi: Infinity },
  ].map((b) => ({ ...b, players: 0 }));
  for (const [, q] of ranked) {
    const lastDay = [...q.secsByDay.keys()].sort().at(-1);
    const ago = daysBetween(lastDay, hasta);
    const b = lastBuckets.find((x) => ago >= x.lo && ago <= x.hi);
    if (b) b.players++;
  }

  // --- Duración de las visitas ---
  const allVisits = [...per.values()].flatMap((q) => q.visits.filter((v) => !v.live));
  const visitBuckets = [
    ['< 5 min', 0, 5],
    ['5–15 min', 5, 15],
    ['15–30 min', 15, 30],
    ['30–60 min', 30, 60],
    ['1–2 h', 60, 120],
    ['2–4 h', 120, 240],
    ['4 h o más', 240, Infinity],
  ].map(([label, lo, hi]) => ({ label, visits: allVisits.filter((v) => (v.end - v.start) / 60 >= lo && (v.end - v.start) / 60 < hi).length }));

  // --- Los jugadores ---
  const weekStart = addDays(hasta, -6);
  const playerRows = [...persons.values()]
    .map((p) => {
      const q = per.get(p.id);
      const dayList = [...q.secsByDay.keys()].sort();
      return {
        id: p.id,
        name: p.name,
        uuid: p.uuid,
        names: [...p.names],
        created: p.created,
        isNew: p.isNew,
        online: p.online,
        segment: segOfPerson.get(p.id) || null,
        daysAny: q.secsByDay.size,
        daysReal: realDays(q, desde, hasta),
        daysReal7: realDays(q, weekStart, hasta),
        hours: q.secs / 3600,
        hours7: [...q.secsByDay.entries()].filter(([d]) => d >= weekStart).reduce((a, [, v]) => a + v, 0) / 3600,
        visits: q.visits.length,
        sessions: q.list.length,
        avgVisitMin: q.visits.length ? q.secs / q.visits.length / 60 : 0,
        first: q.list.length ? q.list[0].start : null,
        last: q.list.length ? Math.max(...q.list.map((s) => s.end)) : null,
        lastDay: dayList.at(-1) || null,
        perDay: Object.fromEntries(q.secsByDay),
        visitList: q.visits.map((v) => ({ start: v.start, end: v.end, sessions: v.sessions, live: v.live })),
      };
    })
    .filter((r) => r.hours > 0 || r.isNew)
    .sort((a, b) => b.hours - a.hours);

  const daily = days.map((d) => {
    const r = byDay.get(d);
    const top = r.top.sort((a, b) => b[1] - a[1]);
    return {
      day: d,
      players: r.any,
      real: r.real,
      short: r.any - r.real,
      fresh: r.fresh,
      hours: r.secs / 3600,
      avgOnline: r.secs / DAY_S,
      peak: r.peak,
      visits: r.visits,
      sessions: r.sessions,
      newAccounts: r.newAccounts,
      restarts: r.restarts,
      recon: r.recon,
      verr: r.verr,
      mojang: r.mojang,
      // quién puso las horas de ese día (los 5 primeros) y cuánto del día pusieron
      top: top.slice(0, 5).map(([name, secs]) => ({ name, hours: secs / 3600 })),
      topShare: r.secs ? top.slice(0, 5).reduce((a, [, v]) => a + v, 0) / r.secs : 0,
    };
  });
  const realDaily = daily.filter((r) => r.players > 0).map((r) => r.real);
  const lastFull = daily.filter((r) => r.day < today && r.players > 0).at(-1) || null;

  return {
    desde,
    hasta,
    today,
    now,
    minMinutes,
    days,
    daily,
    weekly,
    hourly,
    hourMax,
    heat,
    primeHours,
    segments: SEGMENTS.map((s) => ({ ...s, players: segCount[s.id] })),
    segTrend,
    retention,
    survival,
    survivalBase: baseIds.length,
    cohorts,
    pareto,
    lastBuckets,
    visitBuckets,
    players: playerRows,
    sessions: sessions.map((s) => ({ name: persons.get(s.pid).name, uuid: persons.get(s.pid).uuid, start: s.start, end: s.end, minutes: (s.end - s.start) / 60, cut: s.cut, live: s.live })),
    restartTimes: starts,
    versions: [...(data.verr || []).filter(([, t]) => t >= from && t < to).reduce((m, [v]) => m.set(versionName(v), (m.get(versionName(v)) || 0) + 1), new Map()).entries()].sort((a, b) => b[1] - a[1]),
    summary: {
      players: ranked.length,
      realPlayers: playerRows.filter((r) => r.daysReal > 0).length,
      avgRealPerDay: realDaily.length ? realDaily.reduce((a, b) => a + b, 0) / realDaily.length : 0,
      lastFullDay: lastFull ? { day: lastFull.day, real: lastFull.real, players: lastFull.players } : null,
      primeAvg,
      peak: peakAll,
      core: segCount.diario,
      regular: segCount.regular,
      occasional: segCount.ocasional,
      ghosts: segCount.fantasma,
      gone: segCount.sefue,
      active7: segCount.diario + segCount.regular + segCount.ocasional,
      newAccounts: newPeople.length,
      newReturned,
      newCanReturn: canReturn.length,
      newReturnedShare: canReturn.length ? newReturned / canReturn.length : null,
      d1: retention[1] ? retention[1].exact : null,
      d7plus: retention[7] ? retention[7].plus : null,
      top5Share: topShare(5),
      top10Share: topShare(10),
      hours: totalSecs / 3600,
      hoursPerPlayerDay: median(
        [...per.values()].flatMap((q) => [...q.secsByDay.values()].filter((v) => v >= minS).map((v) => v / 3600)),
      ),
      visits: allVisits.length,
      sessions: sessions.length,
      medianVisitMin: median(allVisits.map((v) => (v.end - v.start) / 60)),
      shortVisits: allVisits.filter((v) => v.end - v.start < 5 * 60).length,
      restarts: starts.length,
      badDays: daily.filter((r) => r.restarts > RESTART_LIMIT).length,
      cut: sessions.filter((s) => s.cut).length,
      recon: reconTotal,
      reconShare: sessions.length ? reconTotal / sessions.length : 0,
      verr: (data.verr || []).filter(([, t]) => t >= from && t < to).length,
      mojang: mojangAll,
      mojangOut,
      online: [...persons.values()].filter((p) => p.online).length,
      openDays: openDays.length,
      stickiness: (() => {
        const v = segTrend.filter((r) => r.complete && r.stick !== null).map((r) => r.stick);
        return v.length ? v.reduce((a, b) => a + b, 0) / v.length : null;
      })(),
    },
  };
}
