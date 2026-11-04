// (4 nov 2026) Las gráficas de /metricas, en SVG y sin librerías: líneas (con cruz y globo, series que se encienden y
// apagan en la leyenda), áreas apiladas, donas (que filtran al tocarlas), barras, mapa de calor, matriz jugador × día,
// dispersión y la línea de tiempo de las visitas de un jugador. Todas se dibujan al ancho de su caja.

const SVG = 'http://www.w3.org/2000/svg';
export const nf = new Intl.NumberFormat('es-MX');
export const nf1 = new Intl.NumberFormat('es-MX', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
export const pf = new Intl.NumberFormat('es-MX', { style: 'percent', maximumFractionDigits: 0 });
export const COLORS = {
  accent: '#ea580c',
  blue: '#2563eb',
  green: '#16a34a',
  red: '#dc2626',
  muted: '#d6c8b8',
  grid: '#f1e6da',
  axis: '#cdbba8',
  text: '#6b5a4b',
};

export function el(tag, attrs = {}, parent) {
  const node = tag.startsWith('svg:') ? document.createElementNS(SVG, tag.slice(4)) : document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (v === undefined || v === null || v === false) continue;
    if (k === 'text') node.textContent = v;
    else if (k === 'class') node.setAttribute('class', v);
    else if (k.startsWith('on') && typeof v === 'function') node.addEventListener(k.slice(2), v);
    else node.setAttribute(k, v === true ? '' : v);
  }
  if (parent) parent.appendChild(node);
  return node;
}

export function empty(box, text) {
  box.replaceChildren(el('div', { class: 'empty', text }));
}

const textWidth = (t) => (t.getComputedTextLength ? t.getComputedTextLength() : String(t.textContent).length * 6.5);

// El globo de una gráfica (dentro de su caja, que va con position: relative)
export function tipper(box) {
  const tip = el('div', { class: 'tip', hidden: true }, box);
  return {
    show(title, lines, x, y = 6) {
      tip.replaceChildren(el('div', { class: 't', text: title }));
      for (const l of lines) {
        if (l.note) {
          el('div', { class: 'n', text: l.note }, tip);
          continue;
        }
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
      tip.style.top = `${Math.max(0, y)}px`;
    },
    hide() {
      tip.hidden = true;
    },
  };
}

// Marcas del eje y: pasos redondos (1, 2, 5, 10…; o de 10 % en 10 %) y unas 4 marcas
export function niceTicks(max, { percent = false } = {}) {
  if (percent) {
    const top = Math.min(1, Math.max(0.2, Math.ceil(max * 5) / 5));
    const step = top <= 0.4 ? 0.1 : top <= 0.8 ? 0.2 : 0.25;
    const out = [];
    for (let v = 0; v <= top + 1e-9; v += step) out.push(Math.round(v * 100) / 100);
    return out;
  }
  if (!(max > 0)) return [0, 1];
  const raw = max / 4;
  const p = 10 ** Math.floor(Math.log10(Math.max(raw, 1)));
  const step = [1, 2, 5, 10].map((k) => k * p).find((v) => v >= raw) || 10 * p;
  const ticks = [];
  for (let v = 0; v < max + step; v += step) ticks.push(v);
  return ticks;
}

// El marco común: eje y medido, rejilla y etiquetas del eje x sin que se pisen
function frame(box, { height = 240, ticks, yFmt, n, xLabel, tick, label }) {
  box.replaceChildren();
  const W = Math.max(280, box.clientWidth);
  const H = height;
  const svg = el('svg:svg', { viewBox: `0 0 ${W} ${H}`, role: 'img', 'aria-label': label || '' }, box);
  const top = ticks.at(-1);
  const plotTop = 12;
  const plotBottom = H - 26;
  const y = (v) => plotBottom - (v / top) * (plotBottom - plotTop);
  const gy = el('svg:g', {}, svg);
  let ml = 0;
  for (const v of ticks) ml = Math.max(ml, textWidth(el('svg:text', { x: 0, y: y(v), dy: '0.32em', 'text-anchor': 'end', text: yFmt(v) }, gy)));
  ml = Math.ceil(ml) + 10;
  gy.setAttribute('transform', `translate(${ml - 6},0)`);
  const mr = 10;
  const grid = el('svg:g', {}, svg);
  svg.insertBefore(grid, gy);
  for (const v of ticks) el('svg:line', { x1: ml, x2: W - mr, y1: y(v), y2: y(v), stroke: v === 0 ? COLORS.axis : COLORS.grid, 'stroke-width': 1 }, grid);
  return { svg, W, H, ml, mr, y, plotTop, plotBottom, ticksX(xs) {
    const gx = el('svg:g', {}, svg);
    let lastRight = -Infinity;
    for (let i = 0; i < n; i++) {
      if (tick && !tick(i)) continue;
      const cx = xs(i);
      const t = el('svg:text', { x: cx, y: H - 8, 'text-anchor': 'middle', text: xLabel(i) }, gx);
      const w = textWidth(t);
      let left = cx - w / 2;
      // la primera y la última, pegadas al borde si no caben centradas
      if (cx + w / 2 > W - 2) {
        t.setAttribute('text-anchor', 'end');
        t.setAttribute('x', W - 2);
        left = W - 2 - w;
      } else if (left < 0) {
        t.setAttribute('text-anchor', 'start');
        t.setAttribute('x', 0);
        left = 0;
      }
      if (left < lastRight + 8) t.remove();
      else lastRight = left + w;
    }
  } };
}

// Líneas: series [{ name, color, values, dash, area, hidden }]; tip(i) → { title, lines }
export function lineChart(box, o) {
  const shown = o.series.filter((s) => !s.hidden);
  const n = o.n;
  if (!n) return empty(box, o.empty || 'Aún no hay datos.');
  const vals = shown.flatMap((s) => s.values.filter((v) => v !== null && Number.isFinite(v)));
  const ticks = niceTicks(Math.max(...vals, o.minTop || 0), { percent: o.percent });
  const yFmt = o.yFmt || (o.percent ? (v) => pf.format(v) : (v) => nf.format(v));
  const f = frame(box, { height: o.height, ticks, yFmt, n, xLabel: o.xLabel, tick: o.tick, label: o.label });
  const { svg, W, ml, mr, y, plotTop, plotBottom } = f;
  const xs = (i) => (n === 1 ? (ml + W - mr) / 2 : ml + ((W - ml - mr) * i) / (n - 1));
  if (o.band) {
    for (const b of o.band) {
      el('svg:rect', { x: xs(b.from) - 4, y: plotTop, width: Math.max(8, xs(b.to) - xs(b.from) + 8), height: plotBottom - plotTop, fill: b.color || '#fde7d3', opacity: 0.6 }, svg);
      if (b.label) el('svg:text', { x: xs(b.from) + 2, y: plotTop + 12, class: 'lbl', text: b.label }, svg);
    }
  }
  if (o.ref) {
    el('svg:line', { x1: ml, x2: W - mr, y1: y(o.ref.value), y2: y(o.ref.value), stroke: o.ref.color || COLORS.red, 'stroke-dasharray': '4 3', opacity: 0.8 }, svg);
    if (o.ref.label) el('svg:text', { x: W - mr, y: y(o.ref.value) - 5, 'text-anchor': 'end', class: 'lbl', text: o.ref.label }, svg);
  }
  const defs = el('svg:defs', {}, svg);
  shown.forEach((s, k) => {
    const pts = s.values.map((v, i) => (v === null || !Number.isFinite(v) ? null : [xs(i), y(v)]));
    // tramos sin huecos
    const runs = [];
    let run = [];
    for (const p of pts) {
      if (p) run.push(p);
      else if (run.length) {
        runs.push(run);
        run = [];
      }
    }
    if (run.length) runs.push(run);
    if (s.area) {
      const id = `g${Math.random().toString(36).slice(2, 8)}`;
      const g = el('svg:linearGradient', { id, x1: 0, x2: 0, y1: 0, y2: 1 }, defs);
      [[0, 0.28], [0.5, 0.1], [1, 0]].forEach(([o2, a]) => el('svg:stop', { offset: o2, 'stop-color': s.color, 'stop-opacity': a }, g));
      for (const r of runs) {
        if (r.length < 2) continue;
        el('svg:path', { d: `M${r[0][0]},${plotBottom}L${r.map((p) => p.join(',')).join('L')}L${r.at(-1)[0]},${plotBottom}Z`, fill: `url(#${id})` }, svg);
      }
    }
    for (const r of runs) {
      el('svg:path', { d: `M${r.map((p) => p.join(',')).join('L')}`, fill: 'none', stroke: s.color, 'stroke-width': s.width || 2.25, 'stroke-linejoin': 'round', 'stroke-linecap': 'round', 'stroke-dasharray': s.dash || null }, svg);
    }
    if (n <= 45 || s.dots) {
      pts.forEach((p) => p && el('svg:circle', { cx: p[0], cy: p[1], r: n <= 20 ? 3.5 : 2.5, fill: '#fff', stroke: s.color, 'stroke-width': 2 }, svg));
    }
    // la etiqueta al final de la línea
    if (o.endLabels) {
      const last = [...pts].reverse().find(Boolean);
      if (last) el('svg:text', { x: Math.min(last[0] + 6, W - 4), y: last[1] - 8 - k * 0, 'text-anchor': last[0] + 6 > W - 60 ? 'end' : 'start', class: 'lbl', fill: s.color, text: s.endLabel || '' }, svg);
    }
  });
  f.ticksX(xs);
  // cruz y globo
  if (o.tip) {
    const tp = tipper(box);
    const cross = el('svg:line', { y1: plotTop, y2: plotBottom, stroke: COLORS.axis, 'stroke-width': 1, opacity: 0 }, svg);
    const dots = shown.map((s) => el('svg:circle', { r: 5, fill: s.color, stroke: '#fff', 'stroke-width': 2, opacity: 0 }, svg));
    const hit = el('svg:rect', { x: ml, y: 0, width: W - ml - mr, height: f.H, fill: 'transparent' }, svg);
    const at = (ev) => {
      const r = svg.getBoundingClientRect();
      const px = ((ev.clientX - r.left) / r.width) * W;
      return Math.max(0, Math.min(n - 1, Math.round(n === 1 ? 0 : ((px - ml) / (W - ml - mr)) * (n - 1))));
    };
    const move = (ev) => {
      const i = at(ev);
      cross.setAttribute('x1', xs(i));
      cross.setAttribute('x2', xs(i));
      cross.setAttribute('opacity', 1);
      shown.forEach((s, k) => {
        const v = s.values[i];
        if (v === null || !Number.isFinite(v)) dots[k].setAttribute('opacity', 0);
        else {
          dots[k].setAttribute('cx', xs(i));
          dots[k].setAttribute('cy', y(v));
          dots[k].setAttribute('opacity', 1);
        }
      });
      const t = o.tip(i);
      tp.show(t.title, t.lines, (xs(i) / W) * box.clientWidth);
    };
    hit.addEventListener('pointermove', move);
    hit.addEventListener('pointerdown', move);
    hit.addEventListener('pointerleave', () => {
      tp.hide();
      cross.setAttribute('opacity', 0);
      dots.forEach((d) => d.setAttribute('opacity', 0));
    });
    if (o.onClick) hit.addEventListener('click', (ev) => o.onClick(at(ev)));
  }
}

// Áreas apiladas: series [{ name, color, values }] (de abajo arriba)
export function stackedArea(box, o) {
  const n = o.n;
  if (!n) return empty(box, o.empty || 'Aún no hay datos.');
  const shown = o.series.filter((s) => !s.hidden);
  const totals = Array.from({ length: n }, (_, i) => shown.reduce((a, s) => a + (s.values[i] || 0), 0));
  const ticks = niceTicks(Math.max(...totals, 1), { percent: o.percent });
  const yFmt = o.percent ? (v) => pf.format(v) : (v) => nf.format(v);
  const f = frame(box, { height: o.height, ticks, yFmt, n, xLabel: o.xLabel, tick: o.tick, label: o.label });
  const { svg, W, ml, mr, y, plotTop, plotBottom } = f;
  const xs = (i) => (n === 1 ? (ml + W - mr) / 2 : ml + ((W - ml - mr) * i) / (n - 1));
  const base = Array(n).fill(0);
  for (const s of shown) {
    const lo = [...base];
    const hi = base.map((b, i) => b + (s.values[i] || 0));
    if (n === 1) {
      el('svg:rect', { x: xs(0) - 20, y: y(hi[0]), width: 40, height: Math.max(0, y(lo[0]) - y(hi[0])), fill: s.color, opacity: 0.9 }, svg);
    } else {
      const top = hi.map((v, i) => `${xs(i)},${y(v)}`).join('L');
      const bottom = lo.map((v, i) => `${xs(i)},${y(v)}`).reverse().join('L');
      el('svg:path', { d: `M${top}L${bottom}Z`, fill: s.color, opacity: 0.88, stroke: '#fff', 'stroke-width': 1 }, svg);
    }
    hi.forEach((v, i) => (base[i] = v));
  }
  f.ticksX(xs);
  if (o.tip) {
    const tp = tipper(box);
    const cross = el('svg:line', { y1: plotTop, y2: plotBottom, stroke: '#3d2b1f', 'stroke-width': 1, opacity: 0 }, svg);
    const hit = el('svg:rect', { x: ml, y: 0, width: W - ml - mr, height: f.H, fill: 'transparent' }, svg);
    const move = (ev) => {
      const r = svg.getBoundingClientRect();
      const px = ((ev.clientX - r.left) / r.width) * W;
      const i = Math.max(0, Math.min(n - 1, Math.round(n === 1 ? 0 : ((px - ml) / (W - ml - mr)) * (n - 1))));
      cross.setAttribute('x1', xs(i));
      cross.setAttribute('x2', xs(i));
      cross.setAttribute('opacity', 0.5);
      const t = o.tip(i);
      tp.show(t.title, t.lines, (xs(i) / W) * box.clientWidth);
    };
    hit.addEventListener('pointermove', move);
    hit.addEventListener('pointerdown', move);
    hit.addEventListener('pointerleave', () => {
      tp.hide();
      cross.setAttribute('opacity', 0);
    });
  }
}

// Dona: items [{ id, name, value, color, hint }]; onClick(id); selected = id
export function donut(box, o) {
  box.replaceChildren();
  const total = o.items.reduce((a, it) => a + it.value, 0);
  if (!total) return empty(box, o.empty || 'Aún no hay datos.');
  const wrap = el('div', { class: 'donut' }, box);
  const S = 200;
  const R = 92;
  const r = 58;
  const svg = el('svg:svg', { viewBox: `0 0 ${S} ${S}`, role: 'img', 'aria-label': o.label || '', class: 'donut-svg' }, wrap);
  const tp = tipper(box);
  let a0 = -Math.PI / 2;
  const arc = (a, b, R1, R2) => {
    const large = b - a > Math.PI ? 1 : 0;
    const p = (ang, rad) => `${S / 2 + rad * Math.cos(ang)},${S / 2 + rad * Math.sin(ang)}`;
    return `M${p(a, R1)}A${R1},${R1} 0 ${large} 1 ${p(b, R1)}L${p(b, R2)}A${R2},${R2} 0 ${large} 0 ${p(a, R2)}Z`;
  };
  for (const it of o.items) {
    if (!it.value) continue;
    const a1 = a0 + (2 * Math.PI * it.value) / total;
    const sel = o.selected === it.id;
    const dim = o.selected && !sel;
    const path = el('svg:path', {
      d: arc(a0 + 0.012, a1 - 0.012, sel ? R + 5 : R, r),
      fill: it.color,
      opacity: dim ? 0.35 : 1,
      class: o.onClick ? 'clickable' : null,
      tabindex: o.onClick ? 0 : null,
      role: o.onClick ? 'button' : null,
      'aria-label': `${it.name}: ${it.value}`,
    }, svg);
    const mid = (a0 + a1) / 2;
    path.addEventListener('pointermove', (ev) => {
      const b = box.getBoundingClientRect();
      tp.show(it.name, [{ name: 'Jugadores', value: nf.format(it.value) }, { name: 'Del total', value: pf.format(it.value / total) }, ...(it.hint ? [{ note: it.hint }] : [])], ev.clientX - b.left, ev.clientY - b.top - 40);
    });
    path.addEventListener('pointerleave', () => tp.hide());
    if (o.onClick) {
      path.addEventListener('click', () => o.onClick(it.id));
      path.addEventListener('keydown', (e) => (e.key === 'Enter' || e.key === ' ') && o.onClick(it.id));
    }
    void mid;
    a0 = a1;
  }
  el('svg:text', { x: S / 2, y: S / 2 - 4, 'text-anchor': 'middle', class: 'donut-v', text: o.center?.value ?? nf.format(total) }, svg);
  el('svg:text', { x: S / 2, y: S / 2 + 16, 'text-anchor': 'middle', class: 'donut-l', text: o.center?.label ?? 'en total' }, svg);
  const legend = el('div', { class: 'donut-legend' }, wrap);
  for (const it of o.items) {
    const row = el(o.onClick ? 'button' : 'div', { class: `dl${o.selected === it.id ? ' on' : ''}`, type: o.onClick ? 'button' : null, 'aria-pressed': o.onClick ? String(o.selected === it.id) : null }, legend);
    el('i', { class: 'sw', style: `background:${it.color}` }, row);
    const tx = el('span', { class: 'dl-t' }, row);
    el('b', { text: it.name }, tx);
    if (it.hint) el('small', { text: it.hint }, tx);
    el('span', { class: 'dl-v', text: `${nf.format(it.value)} · ${pf.format(it.value / total)}` }, row);
    if (o.onClick) row.addEventListener('click', () => o.onClick(it.id));
  }
}

// Barras verticales (una serie o apiladas)
export function bars(box, rows, o) {
  box.replaceChildren();
  if (!rows.length) return empty(box, 'Aún no hay datos.');
  const total = (r) => o.series.reduce((a, s) => a + (Number(r[s.key]) || 0), 0);
  const ticks = niceTicks(Math.max(...rows.map(total), o.minTop || 0));
  const f = frame(box, { height: o.height || 230, ticks, yFmt: o.yFmt || ((v) => nf.format(v)), n: rows.length, xLabel: (i) => (o.xLabel ? o.xLabel(rows[i]) : ''), tick: o.tick ? (i) => o.tick(rows[i], i) : null, label: o.label });
  const { svg, W, ml, mr, y } = f;
  const step = (W - ml - mr) / rows.length;
  const bw = Math.min(34, Math.max(2, step * (1 - (o.pad ?? 0.25))));
  const cx = (i) => ml + step * i + step / 2;
  if (o.ref) el('svg:line', { x1: ml, x2: W - mr, y1: y(o.ref), y2: y(o.ref), stroke: COLORS.red, 'stroke-dasharray': '4 3', opacity: 0.7 }, svg);
  rows.forEach((r, i) => {
    let base = 0;
    const lastIdx = o.series.reduce((li, s, k) => ((Number(r[s.key]) || 0) > 0 ? k : li), -1);
    o.series.forEach((s, k) => {
      const v = Number(r[s.key]) || 0;
      if (v <= 0) return;
      const yTop = y(base + v);
      const gap = base > 0 ? 2 : 0;
      const h = Math.max(1, y(base) - yTop - gap);
      const x0 = cx(i) - bw / 2;
      const rr = Math.min(4, bw / 2, h);
      el('svg:path', {
        d: k === lastIdx ? `M${x0},${yTop + h}V${yTop + rr}Q${x0},${yTop} ${x0 + rr},${yTop}H${x0 + bw - rr}Q${x0 + bw},${yTop} ${x0 + bw},${yTop + rr}V${yTop + h}Z` : `M${x0},${yTop}h${bw}v${h}h${-bw}Z`,
        fill: o.color ? o.color(r, s, k) : s.color,
        opacity: o.dim && o.dim(r) ? 0.45 : 1,
      }, svg);
      base += v;
    });
  });
  f.ticksX(cx);
  if (o.tip) {
    const tp = tipper(box);
    rows.forEach((r, i) => {
      const rect = el('svg:rect', { x: ml + step * i, y: 0, width: step, height: f.H, fill: 'transparent' }, svg);
      const show = () => {
        const t = o.tip(r);
        tp.show(t.title, t.lines, (cx(i) / W) * box.clientWidth);
      };
      rect.addEventListener('pointerenter', show);
      rect.addEventListener('pointerdown', show);
      rect.addEventListener('pointerleave', () => tp.hide());
    });
  }
}

// Escala de color secuencial (de crema a naranja oscuro); k entre 0 y 1
export function seqColor(k) {
  const lo = [255, 241, 227];
  const hi = [154, 52, 18];
  const t = Math.max(0, Math.min(1, k));
  return `rgb(${lo.map((c, i) => Math.round(c + (hi[i] - c) * t)).join(',')})`;
}

// Matriz: filas × columnas con color; rows [{ id, label, cells: [valor|null], sub }]; cols [etiqueta]
export function matrix(box, o) {
  box.replaceChildren();
  if (!o.rows.length) return empty(box, o.empty || 'Aún no hay datos.');
  const W = Math.max(300, box.clientWidth);
  const nC = o.cols.length;
  const labelW = Math.min(o.labelWidth || 150, W * 0.36);
  const cw = (W - labelW - 4) / nC;
  const ch = o.cellHeight || 18;
  const top = 22;
  const H = top + ch * o.rows.length + 6;
  const svg = el('svg:svg', { viewBox: `0 0 ${W} ${H}`, role: 'img', 'aria-label': o.label || '' }, box);
  const tp = tipper(box);
  // columnas: las etiquetas sin pisarse
  let lastRight = -Infinity;
  o.cols.forEach((c, j) => {
    const x = labelW + cw * j + cw / 2;
    const t = el('svg:text', { x, y: 14, 'text-anchor': 'middle', text: c }, svg);
    const w = textWidth(t);
    if (x - w / 2 < lastRight + 6 || x + w / 2 > W) t.remove();
    else lastRight = x + w / 2;
  });
  o.rows.forEach((row, i) => {
    const y0 = top + ch * i;
    const label = el('svg:text', { x: labelW - 8, y: y0 + ch / 2, dy: '0.32em', 'text-anchor': 'end', class: `rowlbl${o.onRow ? ' clickable' : ''}`, text: row.label }, svg);
    let s = row.label;
    while (textWidth(label) > labelW - 12 && s.length > 4) {
      s = s.slice(0, -2);
      label.textContent = `${s}…`;
    }
    if (o.onRow) label.addEventListener('click', () => o.onRow(row.id));
    row.cells.forEach((v, j) => {
      const x = labelW + cw * j;
      const rect = el('svg:rect', { x: x + 1, y: y0 + 1, width: Math.max(1, cw - 2), height: ch - 2, rx: 2.5, fill: o.color(v, row, j) }, svg);
      const show = (ev) => {
        const b = box.getBoundingClientRect();
        const t = o.tip(row, j);
        tp.show(t.title, t.lines, ev.clientX - b.left, Math.max(0, ev.clientY - b.top - 60));
      };
      rect.addEventListener('pointermove', show);
      rect.addEventListener('pointerdown', show);
      rect.addEventListener('pointerleave', () => tp.hide());
      if (o.onRow) {
        rect.classList.add('clickable');
        rect.addEventListener('click', () => o.onRow(row.id));
      }
      if (o.text) {
        const tx = o.text(v, row, j);
        if (tx && cw > 26) el('svg:text', { x: x + cw / 2, y: y0 + ch / 2, dy: '0.32em', 'text-anchor': 'middle', class: `celltxt${o.darkText && !o.darkText(v) ? ' light' : ''}`, text: tx }, svg);
      }
    });
  });
}

// Dispersión: points [{ id, x, y, r, color, label }]
export function scatter(box, o) {
  box.replaceChildren();
  if (!o.points.length) return empty(box, 'Aún no hay datos.');
  const yt = niceTicks(Math.max(...o.points.map((p) => p.y), 1));
  const xMax = Math.max(...o.points.map((p) => p.x), 1);
  const f = frame(box, { height: o.height || 300, ticks: yt, yFmt: (v) => nf.format(v), n: 0, xLabel: () => '', label: o.label });
  const { svg, W, ml, mr, y, H } = f;
  const maxR = Math.max(...o.points.map((p) => p.r), 4);
  const x = (v) => ml + 10 + ((W - ml - mr - 20 - maxR) * v) / xMax;
  for (let v = 0; v <= xMax; v += Math.max(1, Math.ceil(xMax / 10))) el('svg:text', { x: x(v), y: H - 8, 'text-anchor': 'middle', text: nf.format(v) }, svg);
  if (o.xTitle) el('svg:text', { x: W - mr, y: H - 22, 'text-anchor': 'end', class: 'lbl', text: o.xTitle }, svg);
  if (o.yTitle) el('svg:text', { x: ml + 4, y: 10, class: 'lbl', text: o.yTitle }, svg);
  const tp = tipper(box);
  const sorted = [...o.points].sort((a, b) => b.r - a.r);
  for (const p of sorted) {
    const c = el('svg:circle', { cx: x(p.x), cy: y(p.y), r: p.r, fill: p.color, 'fill-opacity': 0.75, stroke: '#fff', 'stroke-width': 1.5, class: o.onClick ? 'clickable' : null }, svg);
    const show = () => {
      const t = o.tip(p);
      tp.show(t.title, t.lines, (x(p.x) / W) * box.clientWidth, Math.max(0, y(p.y) - 70));
      c.setAttribute('stroke', '#3d2b1f');
    };
    c.addEventListener('pointerenter', show);
    c.addEventListener('pointerdown', show);
    c.addEventListener('pointerleave', () => {
      tp.hide();
      c.setAttribute('stroke', '#fff');
    });
    if (o.onClick) c.addEventListener('click', () => o.onClick(p.id));
  }
  if (o.labels) {
    for (const p of o.points.filter(o.labels)) {
      const t = el('svg:text', { x: x(p.x) + p.r + 3, y: y(p.y), dy: '0.32em', class: 'lbl', text: p.label }, svg);
      if (x(p.x) + p.r + 3 + textWidth(t) > W) {
        t.setAttribute('text-anchor', 'end');
        t.setAttribute('x', x(p.x) - p.r - 3);
      }
    }
  }
}

// Línea de tiempo de visitas: rows [{ label, bars: [{ from, to (horas 0–24), title, live }] }]
export function timeline(box, o) {
  box.replaceChildren();
  if (!o.rows.length) return empty(box, 'Sin visitas en el periodo.');
  const W = Math.max(300, box.clientWidth);
  const lw = 74;
  const ch = 18;
  const top = 18;
  const H = top + ch * o.rows.length + 4;
  const svg = el('svg:svg', { viewBox: `0 0 ${W} ${H}`, role: 'img', 'aria-label': 'Visitas por día' }, box);
  const x = (h) => lw + ((W - lw - 6) * h) / 24;
  for (let h = 0; h <= 24; h += 3) {
    el('svg:line', { x1: x(h), x2: x(h), y1: top - 4, y2: H, stroke: COLORS.grid }, svg);
    if (h < 24) el('svg:text', { x: x(h), y: 11, 'text-anchor': 'middle', text: `${h} h` }, svg);
  }
  const tp = tipper(box);
  o.rows.forEach((row, i) => {
    const y0 = top + ch * i;
    el('svg:text', { x: lw - 8, y: y0 + ch / 2, dy: '0.32em', 'text-anchor': 'end', text: row.label }, svg);
    el('svg:rect', { x: lw, y: y0 + 3, width: W - lw - 6, height: ch - 6, rx: 3, fill: '#fbf5ee' }, svg);
    for (const b of row.bars) {
      const r = el('svg:rect', { x: x(b.from), y: y0 + 3, width: Math.max(2, x(b.to) - x(b.from)), height: ch - 6, rx: 3, fill: b.live ? COLORS.green : COLORS.accent }, svg);
      const show = (ev) => {
        const bb = box.getBoundingClientRect();
        tp.show(row.label, [{ name: 'Visita', value: b.title }], ev.clientX - bb.left, Math.max(0, ev.clientY - bb.top - 50));
      };
      r.addEventListener('pointermove', show);
      r.addEventListener('pointerdown', show);
      r.addEventListener('pointerleave', () => tp.hide());
    }
  });
}

// Leyenda con botones que encienden y apagan series
export function toggleLegend(box, series, onChange) {
  box.replaceChildren();
  for (const s of series) {
    const b = el('button', { type: 'button', class: `lg${s.hidden ? ' off' : ''}`, 'aria-pressed': String(!s.hidden) }, box);
    el('i', { class: 'sw', style: `background:${s.color}` }, b);
    b.append(s.name);
    b.addEventListener('click', () => {
      s.hidden = !s.hidden;
      onChange();
    });
  }
}
