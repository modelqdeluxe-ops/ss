// Tierras Fantásticas: menú, estado del servidor, tienda, crates, cuentas de jugador con Discord y compra con Stripe.
(() => {
  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
  const page = document.body.dataset.page;
  const escapeHtml = (s) =>
    String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
  const asset = (path) => (/^(https?:)?\//.test(path) ? path : `/${path}`);
  const ICON_DISCORD =
    '<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" width="18" height="18"><path d="M20.3 4.4A19.8 19.8 0 0 0 15.4 3l-.6 1.3a18.4 18.4 0 0 0-5.6 0L8.6 3a19.7 19.7 0 0 0-4.9 1.5C.6 9.1-.3 13.6.1 18.1a19.9 19.9 0 0 0 6 3l1.3-2.1a12.9 12.9 0 0 1-2-1l.5-.4a14.2 14.2 0 0 0 12.2 0l.5.4c-.6.4-1.3.7-2 1l1.3 2.1a19.8 19.8 0 0 0 6-3c.5-5.2-.9-9.7-3.6-13.7zM8.3 15.3c-1.2 0-2.2-1.1-2.2-2.4s1-2.4 2.2-2.4 2.2 1.1 2.2 2.4-1 2.4-2.2 2.4zm7.4 0c-1.2 0-2.2-1.1-2.2-2.4s1-2.4 2.2-2.4 2.2 1.1 2.2 2.4-1 2.4-2.2 2.4z"/></svg>';
  const ICON_LOCK =
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/></svg>';
  const ICON_ARROW =
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6"/></svg>';

  let config = { serverIp: '216.163.187.40:19001', currency: 'USD', paymentsEnabled: false };
  let products = [];
  let selected = null;

  const formatPrice = (cents) =>
    new Intl.NumberFormat('en-US', { style: 'currency', currency: config.currency || 'USD' }).format(cents / 100);

  $$('[data-year]').forEach((el) => (el.textContent = new Date().getFullYear()));

  // Cifras que suben desde 0 (portada). Sin animación si el usuario pidió reducir el movimiento.
  const calm = matchMedia('(prefers-reduced-motion: reduce)').matches;
  function countTo(el, to, prefix = '') {
    if (calm || !Number.isFinite(to) || to <= 0) {
      el.textContent = `${prefix}${to}`;
      return;
    }
    const t0 = performance.now();
    const step = (t) => {
      const k = Math.min(1, (t - t0) / 1100);
      el.textContent = `${prefix}${Math.round(to * (1 - (1 - k) ** 3))}`;
      if (k < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }
  if ('IntersectionObserver' in window) {
    const io = new IntersectionObserver((entries) => {
      for (const e of entries) {
        if (!e.isIntersecting) continue;
        io.unobserve(e.target);
        countTo(e.target, Number(e.target.dataset.count), e.target.dataset.prefix || '');
      }
    });
    $$('[data-count]').forEach((el) => io.observe(el));
  }

  // --- Cabecera: transparente sobre la portada y sólida al bajar ---
  const header = $('#header');
  if (document.body.hasAttribute('data-hero') && 'IntersectionObserver' in window) {
    const sentinel = document.createElement('div');
    sentinel.style.cssText = 'position:absolute;top:0;left:0;width:1px;height:80px;pointer-events:none';
    document.body.prepend(sentinel);
    new IntersectionObserver(([e]) => header.classList.toggle('solid', !e.isIntersecting)).observe(sentinel);
  }

  // --- Menú del móvil ---
  const sheet = $('#sheet');
  const openBtn = $('[data-menu-open]');
  function setMenu(open) {
    sheet.classList.toggle('open', open);
    sheet.setAttribute('aria-hidden', String(!open));
    openBtn.setAttribute('aria-expanded', String(open));
    document.body.classList.toggle('lock', open);
    if (open) $('[data-menu-close]').focus();
    else openBtn.focus({ preventScroll: true });
  }
  openBtn?.addEventListener('click', () => setMenu(true));
  $('[data-menu-close]')?.addEventListener('click', () => setMenu(false));
  sheet?.addEventListener('click', (e) => {
    if (e.target.closest('nav a')) setMenu(false);
  });
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && sheet.classList.contains('open')) setMenu(false);
  });

  // --- Aparición al entrar en pantalla (una sola vez) ---
  function observeReveal(root = document) {
    const items = $$('.reveal:not(.in)', root);
    if (!('IntersectionObserver' in window)) {
      items.forEach((el) => el.classList.add('in'));
      return;
    }
    revealObserver ||= new IntersectionObserver(
      (entries) => {
        for (const e of entries) {
          if (!e.isIntersecting) continue;
          e.target.classList.add('in');
          revealObserver.unobserve(e.target);
        }
      },
      { rootMargin: '0px 0px -8% 0px' },
    );
    items.forEach((el) => revealObserver.observe(el));
  }
  let revealObserver = null;
  observeReveal();

  // --- Configuración pública ---
  async function loadConfig() {
    try {
      config = { ...config, ...(await (await fetch('/api/config')).json()) };
    } catch {
      /* usamos los valores por defecto */
    }
    $$('[data-server-ip]').forEach((el) => (el.textContent = config.serverIp));
    if (config.discordUrl) {
      $$('.discord-link').forEach((link) => {
        link.href = config.discordUrl;
        link.hidden = false;
      });
    }
    $$('[data-payments-off]').forEach((el) => (el.hidden = config.paymentsEnabled));
    loadStatus();
  }

  // --- Estado en vivo: primero el puente del servidor (mod TF Client), si no la API pública mcsrvstat.us ---
  async function fetchStatus() {
    try {
      const bridge = await (await fetch('/api/status')).json();
      if (bridge.bridge) return bridge;
    } catch {
      /* puente no disponible */
    }
    const key = `tf-status:${config.serverIp}`;
    try {
      const cached = JSON.parse(sessionStorage.getItem(key) || 'null');
      if (cached && Date.now() - cached.at < 60_000) return cached.data;
    } catch {
      /* almacenamiento no disponible */
    }
    const data = await (await fetch(`https://api.mcsrvstat.us/3/${encodeURIComponent(config.serverIp)}`)).json();
    try {
      sessionStorage.setItem(key, JSON.stringify({ at: Date.now(), data }));
    } catch {
      /* almacenamiento no disponible */
    }
    return data;
  }

  async function loadStatus() {
    const texts = $$('[data-status-text]');
    const dots = $$('[data-status-dot]');
    try {
      const data = await fetchStatus();
      const online = Boolean(data.online);
      const count = data.players?.online ?? 0;
      dots.forEach((el) => el.classList.add(online ? 'online' : 'offline'));
      const word = count === 1 ? 'jugador' : 'jugadores';
      texts.forEach((el) => (el.textContent = online ? `En línea · ${count} ${word} ${count === 1 ? "conectado" : "conectados"}` : 'Servidor desconectado'));
      $$('[data-players]').forEach((el) => (online ? countTo(el, count) : (el.textContent = 'Off')));
    } catch {
      texts.forEach((el) => (el.textContent = 'Estado no disponible'));
    }
  }

  // --- Copiar IP ---
  function toast(msg) {
    $('.toast')?.remove();
    const el = document.createElement('div');
    el.className = 'toast';
    el.setAttribute('role', 'status');
    el.textContent = msg;
    document.body.append(el);
    setTimeout(() => el.remove(), 2500);
  }

  document.addEventListener('click', async (e) => {
    const btn = e.target.closest('[data-copy-ip]');
    if (!btn) return;
    const hint = $('[data-copy-hint]', btn);
    try {
      await navigator.clipboard.writeText(config.serverIp);
      toast('IP copiada. ¡Nos vemos dentro!');
      btn.classList.add('copied');
      if (hint) {
        const original = hint.dataset.original || (hint.dataset.original = hint.textContent);
        hint.textContent = 'Copiada';
        setTimeout(() => {
          hint.textContent = original;
          btn.classList.remove('copied');
        }, 2200);
      }
    } catch {
      toast(`IP: ${config.serverIp}`);
    }
  });

  // --- Productos ---
  const CATEGORY_LABEL = {
    gratis: 'Recompensa gratis', rangos: 'Rango', crates: 'Crate', ruleta: 'Ruleta', monedas: 'Monedas de oro',
  };
  const crates = () => products.filter((p) => p.category === 'crates');
  const ranks = () => products.filter((p) => p.category === 'rangos').sort((a, b) => (a.tier || 0) - (b.tier || 0));
  // Colores propios de cada crate (config/products.json → colors)
  const HEX = /^#[0-9a-f]{6}$/i;
  function themeVars(p) {
    const [c1, c2] = Array.isArray(p.colors) ? p.colors : [];
    if (!HEX.test(c1 || '') || !HEX.test(c2 || '')) return null;
    const n = Number.parseInt(c2.slice(1), 16);
    return { '--t1': c1, '--t2': c2, '--t3': `rgba(${n >> 16}, ${(n >> 8) & 255}, ${n & 255}, 0.26)` };
  }
  const themeAttr = (p) => {
    const vars = themeVars(p);
    return vars ? ` style="${Object.entries(vars).map(([k, v]) => `${k}:${v}`).join(';')}"` : '';
  };
  function applyTheme(el, p) {
    const vars = themeVars(p) || {};
    for (const k of ['--t1', '--t2', '--t3']) {
      if (vars[k]) el.style.setProperty(k, vars[k]);
      else el.style.removeProperty(k);
    }
  }
  const buyAttrs = (p) => `data-buy="${escapeHtml(p.id)}" ${config.paymentsEnabled ? '' : 'disabled'}`;
  const isPixel = (src) => /\/ranks\/|coins-|\/gifts\//.test(src || '');
  // Las imágenes aparecen con un fundido cuando terminan de cargar (clase «ok», ver .fx en styles.css).
  const img = (src, alt, cls = '', extra = '') =>
    `<img src="${asset(src)}" alt="${escapeHtml(alt)}" loading="lazy" decoding="async" class="${[cls, 'fx', isPixel(src) ? 'pixel-img' : '']
      .filter(Boolean)
      .join(' ')}" onload="this.classList.add('ok')" onerror="this.classList.add('ok')"${extra}>`;
  const thumbOf = (set, id) => `/img/items/${set}/${id}.webp`;
  const piecesText = (p) => {
    const n = (p.models || []).length;
    const armor = (p.models || []).some((m) => m.id.startsWith('armor_'));
    return `${n} objetos${armor ? ' · armadura completa' : ''}`;
  };

  async function loadProducts() {
    try {
      products = await (await fetch('/api/products')).json();
    } catch {
      $$('#products, #crate-spotlight').forEach(
        (el) => (el.innerHTML = '<p class="muted">No se pudieron cargar los productos. Recarga la página.</p>'),
      );
      return;
    }
    if (page === 'inicio') renderHome();
    if (page === 'tienda') initShop();
  }

  // Brillo de la crate: un aura de su color que late (con retrasos distintos para que no lata todo a la vez).
  const glow = (i) => `<span class="crate-glow" aria-hidden="true" style="--d:${(-i * 0.7).toFixed(1)}s"></span>`;

  // Inicio: solo unas pocas crates (lo demás está en la tienda)
  function renderHome() {
    const list = crates();
    $$('[data-crate-count]').forEach((el) => countTo(el, list.length));
    $$('[data-crates-all]').forEach((el) => (el.innerHTML = `Ver las ${list.length} crates ${ICON_ARROW}`));
    const box = $('#crate-spotlight');
    box.innerHTML = list
      .slice(-4)
      .reverse()
      .map(
        (p, i) => `
        <a class="frame crate-tile home-tile reveal" href="/tienda#crates-${escapeHtml(p.theme || p.id)}"${themeAttr(p)}>
          <span class="crate-tile-art">${glow(i)}${img(p.image, p.name)}</span>
          <span class="body">
            <h3>${escapeHtml(p.name.replace(/^Crate\s+/i, ''))}</h3>
            <span class="tile-tag">${escapeHtml(p.tagline || '')}</span>
            <span class="card-foot"><span class="price">${formatPrice(p.price)}</span><span class="btn btn-theme btn-sm">Ver crate</span></span>
          </span>
        </a>`,
      )
      .join('');
    observeReveal();
  }

  // Los rangos usan el color de su prefijo
  function rankAttr(p) {
    const hex = p.rank?.hex;
    if (!HEX.test(hex || '')) return '';
    const n = Number.parseInt(hex.slice(1), 16);
    return ` style="--t1:${hex};--t2:${hex};--t3:rgba(${n >> 16}, ${(n >> 8) & 255}, ${n & 255}, 0.28)"`;
  }

  // Tienda: tarjetas de regalos y rangos
  function productCard(p) {
    const perks = Array.isArray(p.perks) ? p.perks.slice(0, 5) : [];
    return `
      <article class="panel product"${themeAttr(p) || rankAttr(p)}>
        ${p.set ? `<button type="button" class="thumb thumb-try" data-open-crate="${escapeHtml(p.id)}" aria-label="Ver el set de ${escapeHtml(p.name)} y probártelo">
          ${img(p.image, '')}<span class="try-on">Pruébatelo en 3D</span></button>` : p.image ? `<div class="thumb">${img(p.image, '')}</div>` : ''}
        <div class="body">
          <span class="cat">${escapeHtml(CATEGORY_LABEL[p.category] || p.category)}</span>
          <h3>${escapeHtml(p.name)}</h3>
          <p>${escapeHtml(p.description)}</p>
          ${perks.length ? `<ul class="perks">${perks.map((x) => `<li>${escapeHtml(x)}</li>`).join('')}</ul>` : ''}
          <div class="card-foot">${giftFoot(p) || rankFoot(p) || `
            <span class="price">${formatPrice(p.price)}</span>
            <button class="btn btn-primary btn-sm" type="button" ${buyAttrs(p)}>Comprar</button>`}
          </div>
        </div>
      </article>`;
  }

  // Crates: cada una es el set completo
  function crateCard(p, i = 0) {
    return `
      <article class="panel crate-tile" data-crate="${escapeHtml(p.id)}"${themeAttr(p)}>
        <button type="button" class="crate-tile-art" data-open-crate="${escapeHtml(p.id)}" aria-label="Ver ${escapeHtml(p.name)} y probártelo">
          ${glow(i)}${img(p.image, '')}
          <span class="try-on">Pruébatelo en 3D</span>
        </button>
        <div class="body">
          <h3>${escapeHtml(p.name.replace(/^Crate\s+/i, ''))}</h3>
          <p>${escapeHtml(p.tagline || '')}</p>
          <span class="pieces">${escapeHtml(piecesText(p))}</span>
          <div class="card-foot">
            <span class="price">${formatPrice(p.price)}</span>
            <button class="btn btn-theme btn-sm" type="button" ${buyAttrs(p)}>Comprar crate</button>
          </div>
        </div>
      </article>`;
  }

  function giftFoot(p) {
    if (p.price !== 0) return '';
    return `<span class="price free">Gratis</span><button class="btn btn-primary btn-sm" type="button" data-claim="${escapeHtml(p.id)}">Reclamar</button>`;
  }

  document.addEventListener('click', async (e) => {
    const btn = e.target.closest('[data-claim]');
    if (!btn) return;
    if (!me.user) {
      toast('Entra con tu cuenta para reclamar la recompensa');
      setTimeout(() => (location.href = `/cuenta?return=${encodeURIComponent(location.pathname + location.hash)}`), 900);
      return;
    }
    btn.disabled = true;
    btn.classList.add('is-loading');
    try {
      const res = await fetch('/api/claim', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ productId: btn.dataset.claim }),
      });
      const data = await res.json().catch(() => ({}));
      if (res.ok) {
        location.href = `/success?order=${encodeURIComponent(data.id)}`;
        return;
      }
      toast(data.error || 'No se pudo reclamar la recompensa.');
    } catch {
      toast('No se pudo reclamar la recompensa.');
    }
    btn.disabled = false;
    btn.classList.remove('is-loading');
  });

  // Con Minecraft vinculado: su rango actual no se vuelve a vender y los superiores salen a precio de mejora.
  function rankFoot(p) {
    const q = ownedQuote(p);
    if (!q) return '';
    if (q.owned) {
      const mine = me.user.rank.id === p.id;
      return `<span class="price owned">${mine ? 'Tu rango' : 'Incluido'}</span><button class="btn btn-ghost btn-sm" type="button" disabled>${mine ? '✓ Lo tienes' : 'Ya incluido'}</button>`;
    }
    return `<span class="price"><small>Mejora · antes ${formatPrice(p.price)}</small>${formatPrice(q.unit)}</span><button class="btn btn-primary btn-sm" type="button" ${buyAttrs(p)}>Mejorar</button>`;
  }

  function rerenderRanks() {
    if (page === 'tienda' && currentCategory === 'rangos' && me.user?.rank) selectCategory('rangos', false);
  }

  function renderCompare() {
    const box = $('#compare');
    const list = ranks().filter((p) => p.specs);
    if (!list.length) {
      box.hidden = true;
      return;
    }
    const rows = [...new Set(list.flatMap((p) => Object.keys(p.specs)))];
    const cell = (v) =>
      v === true ? '<span class="yes" aria-label="Sí">✓</span>' : v === false || v == null ? '<span class="no" aria-label="No">—</span>' : escapeHtml(v);
    box.innerHTML = `
      <table>
        <caption class="sr-only">Comparativa de rangos</caption>
        <thead><tr><th scope="col">Ventaja</th>${list
          .map((p) => `<th scope="col">${p.image ? img(p.image, '', '', ' width="44" height="44"') : ''}${escapeHtml(p.name.replace(/^Rango\s+/i, ''))}</th>`)
          .join('')}</tr></thead>
        <tbody>${rows
          .map((r) => `<tr><th scope="row">${escapeHtml(r)}</th>${list.map((p) => `<td>${cell(p.specs[r])}</td>`).join('')}</tr>`)
          .join('')}
          <tr><th scope="row">Precio</th>${list.map((p) => `<td><b>${formatPrice(p.price)}</b></td>`).join('')}</tr>
        </tbody>
      </table>`;
  }

  const INTRO = {
    gratis: 'Recompensas que puedes reclamar gratis con tu cuenta, una vez por jugador.',
    rangos: 'Cada rango trae su prefijo con color y un set completo: armas, herramientas, armadura y cosméticos animados. Toca la imagen para probártelo. Si ya tienes un rango, mejorar cuesta solo la diferencia.',
    crates: 'Cada crate es un set completo, siempre el mismo y sin nada al azar: armas, herramientas, armadura y cosméticos animados. Toca una para probártela en tu personaje.',
    ruleta: '',
    monedas: 'Monedas de oro para la economía del servidor: compra terrenos, objetos y lo que veas en la tienda de monedas.',
    tiendamonedas: '',
  };

  let currentCategory = null;
  let coinShopTimer = null;
  function initShop() {
    const tabs = $$('#shop-tabs [role="tab"]');
    for (const tab of tabs) {
      const cat = tab.dataset.category;
      const count = cat === 'tiendamonedas' ? null : products.filter((p) => p.category === cat).length;
      tab.hidden = count === 0;
      if (count) tab.insertAdjacentHTML('beforeend', `<span class="count">${cat === 'ruleta' ? '' : count}</span>`);
      tab.addEventListener('click', () => selectCategory(cat, true));
    }
    $('#shop-tabs').addEventListener('keydown', (e) => {
      if (!['ArrowLeft', 'ArrowRight'].includes(e.key)) return;
      const visible = tabs.filter((t) => !t.hidden);
      const idx = visible.findIndex((t) => t.dataset.category === currentCategory);
      const next = visible[(idx + (e.key === 'ArrowRight' ? 1 : -1) + visible.length) % visible.length];
      selectCategory(next.dataset.category, true);
      next.focus();
    });
    $('#shop-search').addEventListener('input', filterCrates);
    // #crates-<set> abre la pestaña de crates con esa crate
    const fromHash = () => {
      const h = decodeURIComponent(location.hash.slice(1));
      if (h.startsWith('crates-')) return { cat: 'crates', crate: h.slice(7) };
      if (h.startsWith('rangos-')) return { cat: 'rangos', crate: `rango-${h.slice(7)}` };
      return tabs.some((t) => t.dataset.category === h && !t.hidden) ? { cat: h } : null;
    };
    const start = fromHash() || { cat: 'crates' };
    selectCategory(start.cat, false);
    if (start.crate) openCrateView(start.crate);
    window.addEventListener('hashchange', () => {
      const h = fromHash();
      if (!h) return;
      if (h.cat !== currentCategory) selectCategory(h.cat, false);
      if (h.crate) openCrateView(h.crate);
    });
  }

  function selectCategory(category, updateUrl) {
    currentCategory = category;
    for (const tab of $$('#shop-tabs [role="tab"]')) {
      const active = tab.dataset.category === category;
      tab.setAttribute('aria-selected', String(active));
      tab.tabIndex = active ? 0 : -1;
      if (active) tab.scrollIntoView({ block: 'nearest', inline: 'nearest' });
    }
    clearInterval(coinShopTimer);
    const box = $('#products');
    box.className = 'products';
    $('#shop-intro').textContent = INTRO[category] || '';
    $('#shop-intro').hidden = !INTRO[category];
    $('#shop-search-wrap').hidden = category !== 'crates';
    $('#crate-empty').hidden = true;
    $('#compare').hidden = category !== 'rangos';
    if (category === 'crates') {
      box.classList.add('crate-grid');
      box.innerHTML = crates().map((p, i) => crateCard(p, i)).join('');
      filterCrates();
    } else if (category === 'ruleta') {
      renderRoulette(box);
    } else if (category === 'tiendamonedas') {
      renderCoinShop(box);
      coinShopTimer = setInterval(() => renderCoinShop(box, true), 15000);
    } else {
      box.classList.add('grid', 'grid-3');
      const list = products.filter((p) => p.category === category);
      if (category === 'rangos') list.sort((a, b) => (a.tier || 0) - (b.tier || 0));
      box.innerHTML = list.map(productCard).join('') || '<p class="loading">No hay productos en esta sección</p>';
      if (category === 'rangos') renderCompare();
    }
    if (updateUrl) history.replaceState(null, '', `#${category}`);
  }

  const norm = (t) => String(t || '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
  function filterCrates() {
    if (currentCategory !== 'crates') return;
    const q = norm($('#shop-search').value.trim());
    let shown = 0;
    for (const p of crates()) {
      const ok = !q || norm(`${p.name} ${p.tagline} ${p.description}`).includes(q);
      const el = $(`.crate-tile[data-crate="${p.id}"]`);
      if (el) el.hidden = !ok;
      shown += ok;
    }
    $('#crate-empty').hidden = shown > 0;
  }

  // --- Ruleta: premios con su probabilidad a la vista; las armas legendarias son el premio raro ---
  function renderRoulette(box) {
    const spins = products.filter((p) => p.category === 'ruleta').sort((a, b) => a.spins - b.spins);
    const ref = spins[0];
    if (!ref) {
      box.innerHTML = '<p class="loading">La ruleta no está disponible ahora mismo.</p>';
      return;
    }
    const weapons = ref.models || [];
    const pool = [...(ref.pool || [])].sort((a, b) => b.chance - a.chance);
    const weaponChance = pool.filter((p) => p.weapon).reduce((s, p) => s + p.chance, 0);
    const max = Math.max(...pool.map((p) => p.chance), 1);
    box.innerHTML = `
      <section class="frame roulette"${themeAttr(ref)}>
        <div class="roulette-info">
          <span class="eyebrow">Ruleta</span>
          <h2>Prueba tu suerte</h2>
          <p>Cada giro te da un premio al azar para el servidor: monedas, diamantes, netherita, tótems… y, con un ${weaponChance}% de probabilidad, un arma legendaria de la forja de Nazgul. La tienda elige el premio al confirmarse el pago y te llega al juego.</p>
          <div class="spin-buttons">${spins
            .map(
              (p, i) => `<button type="button" class="btn ${i === 0 ? 'btn-primary' : 'btn-ghost'} btn-lg" ${buyAttrs(p)}>
                ${p.spins} ${p.spins === 1 ? 'giro' : 'giros'} · ${formatPrice(p.price)}</button>`,
            )
            .join('')}</div>
          <div class="coin-spins">
            <span class="coin-spins-label"><img src="/img/coins-small.png" alt="" width="20" height="20" class="pixel-img">O gira con las monedas del servidor</span>
            <div class="spin-buttons">${spins
              .filter((p) => p.coinPrice)
              .map(
                (p) => `<button type="button" class="btn btn-ghost" data-coin-spin="${p.spins}">
                  ${p.spins} ${p.spins === 1 ? 'giro' : 'giros'} · ${new Intl.NumberFormat('es-ES', { useGrouping: 'always' }).format(p.coinPrice)} monedas</button>`,
              )
              .join('')}</div>
            <p class="muted small">Se cobra en el juego cuando estés conectado (o al entrar). Si no tienes bastantes monedas, no se cobra nada.</p>
          </div>
          <p class="muted small">Las probabilidades son las mismas en cada giro, con dinero o con monedas. En la página de tu tirada verás qué te tocó.</p>
        </div>
        <div class="odds" aria-label="Premios y probabilidades">
          <h3>Premios y probabilidades</h3>
          <ul>${pool
            .map(
              (p) => `<li class="${p.weapon ? 'rare' : ''}">
                <span class="odds-icon">${img(p.icon, '', '', ' width="36" height="36"')}</span>
                <span class="odds-name">${escapeHtml(p.name)}</span>
                <span class="odds-bar"><i style="width:${Math.round((p.chance / max) * 100)}%"></i></span>
                <b>${p.chance}%</b>
              </li>`,
            )
            .join('')}</ul>
        </div>
      </section>
      <section class="roulette-weapons">
        <div class="section-head split-head">
          <div>
            <h3>Armas legendarias <span class="muted">· ${weaponChance}% por giro</span></h3>
            <p class="muted">Si te toca el arma, es una de estas ${weapons.length}, al azar. Toca una para verla en 3D.</p>
          </div>
        </div>
        <div class="roulette-reel">${weapons
          .map(
            (w, i) => `<button type="button" class="reel-item" data-roulette-view="${i}" aria-label="Ver ${escapeHtml(w.name)} en 3D">
              ${img(thumbOf(ref.set, w.id), '', '', ' width="64" height="64"')}<span>${escapeHtml(w.name)}</span></button>`,
          )
          .join('')}</div>
      </section>`;
    for (const pic of $$('.odds-icon img', box)) pic.classList.add('pixel-img');
  }

  // Pagar con monedas (girar la ruleta o comprar en la tienda de monedas): hace falta la cuenta, porque su jugador es
  // el que paga las monedas en el juego.
  document.addEventListener('click', async (e) => {
    const btn = e.target.closest('[data-coin-spin], [data-coin-buy]');
    if (!btn) return;
    const spin = 'coinSpin' in btn.dataset;
    if (!me.user) {
      toast(spin ? 'Entra con tu cuenta para girar con tus monedas' : 'Entra con tu cuenta para comprar con tus monedas');
      const back = spin ? '/tienda#ruleta' : '/tienda#tiendamonedas';
      setTimeout(() => (location.href = `/cuenta?return=${encodeURIComponent(back)}`), 900);
      return;
    }
    const failed = spin ? 'No se pudo girar ahora mismo.' : 'No se pudo comprar ahora mismo.';
    btn.disabled = true;
    btn.classList.add('is-loading');
    try {
      const res = await fetch(spin ? '/api/roulette/coins' : '/api/coinshop/buy', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(spin ? { spins: Number(btn.dataset.coinSpin) } : { id: btn.dataset.coinBuy }),
      });
      const data = await res.json().catch(() => ({}));
      if (res.ok) {
        location.href = `/success?order=${encodeURIComponent(data.id)}`;
        return;
      }
      toast(data.error || failed);
    } catch {
      toast(failed);
    }
    btn.disabled = false;
    btn.classList.remove('is-loading');
  });

  document.addEventListener('click', async (e) => {
    const btn = e.target.closest('[data-roulette-view]');
    if (!btn) return;
    const ref = products.find((p) => p.category === 'ruleta');
    const items = (ref?.models || []).map((m) => ({
      name: m.name,
      thumb: thumbOf(ref.set, m.id),
      model: `/models/${ref.set}/${m.id}.json`,
    }));
    try {
      const { openViewer } = await import('/viewer.js');
      openViewer(items, Number(btn.dataset.rouletteView), 'Armas de la ruleta');
    } catch (err) {
      console.error(err);
      toast('No se pudo abrir el visor 3D en este navegador.');
    }
  });

  // --- Tienda de monedas (la actualiza el staff desde el juego) ---
  let coinShopVersion = null;
  async function renderCoinShop(box, refresh) {
    let data;
    try {
      data = await (await fetch('/api/coinshop')).json();
    } catch {
      if (!refresh) box.innerHTML = '<p class="muted">No se pudo cargar la tienda de monedas.</p>';
      return;
    }
    if (currentCategory !== 'tiendamonedas' || (refresh && data.version === coinShopVersion)) return;
    coinShopVersion = data.version;
    const items = data.items || [];
    const fmt = new Intl.NumberFormat('es-ES', { useGrouping: 'always' });
    box.innerHTML = `
      <div class="coin-head">
        <div>
          <h2>Tienda de monedas</h2>
          <p class="muted">Objetos del servidor que se pagan con las monedas que ganas jugando.</p>
        </div>
        <span class="live-badge"><span class="dot online"></span>Actualizado en vivo desde el servidor</span>
      </div>
      ${
        items.length
          ? `<div class="coin-grid">${items
              .map(
                (it) => `<article class="panel coin-item">
                  <div class="coin-icon" data-letter="${escapeHtml(it.name.slice(0, 1).toUpperCase())}">${it.icon ? img(it.icon, '') : ''}</div>
                  <h3>${escapeHtml(it.name)}</h3>
                  <span class="muted small">× ${it.count}</span>
                  <span class="coin-price"><img src="/img/coins-small.png" alt="" width="20" height="20" class="pixel-img">${fmt.format(it.price)}</span>
                  <button type="button" class="btn btn-coin btn-sm" data-coin-buy="${escapeHtml(it.id)}">Comprar</button>
                </article>`,
              )
              .join('')}</div>`
          : '<div class="panel coin-empty"><p>Todavía no hay objetos. El staff los añade desde el juego con <code>/tf web tienda add &lt;precio&gt;</code>.</p></div>'
      }
      <p class="muted small coin-note">Se pagan con tus monedas del servidor: se cobran y te llega el objeto en el juego cuando estés conectado. Si no tienes bastantes, no se cobra nada.</p>`;
    // Sin icono (o si no carga), la inicial del objeto.
    for (const icon of $$('.coin-icon', box)) {
      const letter = () => icon.replaceChildren(Object.assign(document.createElement('span'), { textContent: icon.dataset.letter }));
      const pic = $('img', icon);
      if (!pic) letter();
      else pic.addEventListener('error', letter, { once: true });
    }
  }

  // --- Ver una crate: el probador con el personaje y el set puesto ---
  const CV_HTML = `
  <dialog id="crate-view" class="crate-view" aria-labelledby="cv-title">
    <div class="cv-head">
      <div><span class="cat" id="cv-cat">Crate</span><h2 id="cv-title"></h2><p class="muted" id="cv-tag"></p></div>
      <button type="button" class="dialog-close" data-cv-close aria-label="Cerrar">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M6 6l12 12M18 6L6 18"/></svg>
      </button>
    </div>
    <div class="cv-body">
      <div class="cv-stage">
        <canvas aria-label="Tu personaje con el set puesto"></canvas>
        <p class="viewer-status" id="cv-status">Cargando…</p>
        <div class="cv-modes" role="group" aria-label="Vista">
          <button type="button" data-cv-mode="player" aria-pressed="true">Mi personaje</button>
          <button type="button" data-cv-mode="item" aria-pressed="false" disabled>Objeto</button>
        </div>
        <div class="cv-actions">
          <span class="cv-selected" id="cv-selected"></span>
          <button type="button" class="btn btn-theme btn-sm" id="cv-equip" hidden>Equipar</button>
        </div>
        <label class="cv-skin"><span>Skin de</span><input id="cv-skin" maxlength="16" spellcheck="false" autocomplete="off" placeholder="Tu skin"></label>
      </div>
      <div class="cv-side">
        <p class="cv-desc" id="cv-desc"></p>
        <h4>Incluye <span id="cv-count"></span> <span class="hint3d">· toca uno para verlo y equipártelo</span></h4>
        <div class="cv-items" id="cv-items"></div>
        <div class="cv-buy">
          <span class="price"><small>Set completo</small><span id="cv-price"></span></span>
          <button class="btn btn-theme btn-lg" type="button" id="cv-buy">Comprar crate</button>
        </div>
      </div>
    </div>
  </dialog>`;

  const wearCache = {};
  let cv = null;
  async function openCrateView(id) {
    const p = products.find((x) => x.id === id || x.theme === id);
    if (!p || !p.set) return;
    if (!cv) {
      document.body.insertAdjacentHTML('beforeend', CV_HTML);
      const dialog = $('#crate-view');
      cv = { dialog, wardrobe: null, set: null, crate: null, outfit: {}, selected: null, mode: 'player', lib: null };
      $('[data-cv-close]', dialog).addEventListener('click', () => dialog.close());
      dialog.addEventListener('click', (e) => {
        if (e.target === dialog) dialog.close();
      });
      dialog.addEventListener('close', () => {
        cv.wardrobe?.destroy();
        cv.wardrobe = null;
        if (/^#(crates|rangos)-/.test(location.hash)) history.replaceState(null, '', location.hash.startsWith('#rangos-') ? '#rangos' : '#crates');
      });
      $('#cv-items', dialog).addEventListener('click', (e) => {
        const b = e.target.closest('[data-cv-item]');
        if (b) selectWearItem(b.dataset.cvItem);
      });
      $$('[data-cv-mode]', dialog).forEach((b) => b.addEventListener('click', () => (b.dataset.cvMode === 'player' ? showOutfit() : selectWearItem(cv.selected))));
      $('#cv-equip', dialog).addEventListener('click', toggleEquip);
      $('#cv-buy', dialog).addEventListener('click', () => {
        dialog.close();
        openCheckout(cv.crate.id);
      });
      let t = null;
      $('#cv-skin', dialog).addEventListener('input', (e) => {
        clearTimeout(t);
        const v = e.target.value.trim();
        t = setTimeout(() => {
          if (!v || /^[A-Za-z0-9_]{3,16}$/.test(v)) {
            storageSet('tf-skin', v);
            showOutfit();
          }
        }, 600);
      });
    }
    const dialog = cv.dialog;
    cv.crate = p;
    applyTheme(dialog, p);
    $('#cv-title').textContent = p.name;
    $('#cv-tag').textContent = p.tagline || '';
    $('#cv-desc').textContent = p.description || '';
    const isRank = p.category === 'rangos';
    $('#cv-cat').textContent = isRank ? 'Rango' : 'Crate';
    $('#cv-price').textContent = formatPrice(p.price);
    $('#cv-price').previousElementSibling.textContent = isRank ? 'Rango con su set' : 'Set completo';
    $('#cv-buy').textContent = isRank ? 'Comprar rango' : 'Comprar crate';
    $('#cv-buy').disabled = !config.paymentsEnabled;
    $('#cv-skin').value = storageGet('tf-skin') || me.user?.name || storageGet('tf-username') || '';
    $('#cv-status').hidden = false;
    $('#cv-status').textContent = 'Cargando el set…';
    if (!dialog.open) dialog.showModal();
    history.replaceState(null, '', isRank ? `#${p.id.replace(/^rango-/, 'rangos-')}` : `#crates-${p.theme || p.id}`);
    try {
      cv.lib ||= await import('/wardrobe.js');
      cv.set = wearCache[p.set] ||= await (await fetch(`/wear/${p.set}.json`)).json();
    } catch (err) {
      console.error(err);
      $('#cv-status').textContent = 'No se pudo cargar el probador 3D en este navegador.';
      return;
    }
    cv.outfit = cv.lib.defaultOutfit(cv.set);
    cv.selected = null;
    const models = (p.models || []).filter((m) => cv.set.items[m.id]);
    $('#cv-count').textContent = `(${models.length})`;
    $('#cv-items').innerHTML = models
      .map(
        (m) => `<button type="button" class="cv-item" data-cv-item="${escapeHtml(m.id)}" aria-pressed="false">
          ${img(thumbOf(p.set, m.id), '', '', ' width="64" height="64"')}<span>${escapeHtml(m.name)}</span><i class="worn-dot" aria-hidden="true"></i></button>`,
      )
      .join('');
    if (!cv.wardrobe) cv.wardrobe = cv.lib.createWardrobe($('.cv-stage canvas', dialog));
    showOutfit();
  }

  function wearSlot(slug) {
    const it = cv.set.items[slug];
    if (!it) return null;
    return it.type === 'armor' ? it.slot : cv.lib.slotOf(it);
  }

  function refreshCv() {
    const worn = new Set(Object.values(cv.outfit));
    for (const b of $$('[data-cv-item]', cv.dialog)) {
      b.setAttribute('aria-pressed', String(b.dataset.cvItem === cv.selected));
      b.classList.toggle('worn', worn.has(b.dataset.cvItem));
    }
    $$('[data-cv-mode]', cv.dialog).forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.cvMode === cv.mode)));
    $('[data-cv-mode="item"]', cv.dialog).disabled = !cv.selected || !cv.set.items[cv.selected]?.model;
    const sel = cv.selected && cv.crate.models.find((m) => m.id === cv.selected);
    $('#cv-selected').textContent = sel ? sel.name : 'Toca una pieza para verla';
    const slot = cv.selected && wearSlot(cv.selected);
    const equip = $('#cv-equip');
    equip.hidden = !slot || slot === 'offhand';
    equip.textContent = slot && cv.outfit[slot] === cv.selected ? 'Quitar' : 'Equipar';
  }

  async function showOutfit() {
    if (!cv?.wardrobe) return;
    cv.mode = 'player';
    refreshCv();
    const skin = $('#cv-skin').value.trim();
    try {
      await cv.wardrobe.showPlayer(cv.set, cv.outfit, /^[A-Za-z0-9_]{3,16}$/.test(skin) ? skin : 'MHF_Steve');
      $('#cv-status').hidden = true;
    } catch (err) {
      console.error(err);
      $('#cv-status').hidden = false;
      $('#cv-status').textContent = 'No se pudo mostrar el personaje.';
    }
  }

  async function selectWearItem(slug) {
    if (!slug || !cv) return;
    cv.selected = slug;
    const it = cv.set.items[slug];
    // La armadura no tiene modelo suelto: se ve directamente puesta.
    if (!it?.model) {
      const slot = wearSlot(slug);
      if (slot) cv.outfit[slot] = slug;
      return showOutfit();
    }
    cv.mode = 'item';
    refreshCv();
    try {
      await cv.wardrobe.showItem(it.model, { upright: !['head', 'back', 'shield'].includes(it.type) });
      $('#cv-status').hidden = true;
    } catch (err) {
      console.error(err);
    }
  }

  function toggleEquip() {
    const slot = wearSlot(cv.selected);
    if (!slot) return;
    if (cv.outfit[slot] === cv.selected) delete cv.outfit[slot];
    else cv.outfit[slot] = cv.selected;
    showOutfit();
  }

  document.addEventListener('click', (e) => {
    const btn = e.target.closest('[data-open-crate]');
    if (btn) openCrateView(btn.dataset.openCrate);
  });

  // --- Ventana de compra (solo en las páginas que venden) ---
  const DIALOG_HTML = `
  <dialog id="checkout-dialog" class="dialog" aria-labelledby="dialog-title">
    <form id="checkout-form" method="dialog" novalidate>
      <button type="button" class="dialog-close" id="dialog-close" aria-label="Cerrar">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M6 6l12 12M18 6L6 18"/></svg>
      </button>
      <div class="dialog-head">
        <img class="dialog-img" id="dialog-img" alt="" hidden>
        <div><div class="cat" id="dialog-cat"></div><h3 id="dialog-title">Producto</h3></div>
      </div>
      <p class="dialog-desc" id="dialog-desc"></p>

      <div class="field">
        <label for="username">Tu nombre de Minecraft</label>
        <input id="username" name="username" autocomplete="username" autocapitalize="off" spellcheck="false" required minlength="3" maxlength="16" pattern="[A-Za-z0-9_]{3,16}" placeholder="Steve_123" aria-describedby="player-card">
        <div class="player-card" id="player-card" aria-live="polite" hidden></div>
      </div>
      <div class="field" id="quantity-field">
        <label for="quantity" id="quantity-label">Cantidad</label>
        <input id="quantity" name="quantity" type="number" inputmode="numeric" min="1" max="10" value="1">
      </div>

      <div class="discord-box" id="discord-box">
        <div class="discord-linked" id="discord-linked" hidden>
          <img id="discord-avatar" alt="" width="36" height="36" class="pixel-img">
          <div>
            <div>Tu cuenta: <strong id="discord-name"></strong></div>
            <div class="discord-hint" id="discord-linked-hint"></div>
          </div>
          <a class="link-btn" href="/cuenta">Mi cuenta</a>
        </div>
        <div id="discord-unlinked" hidden>
          <a class="btn btn-ghost btn-block" id="discord-login" href="/cuenta">Entrar o crear cuenta</a>
          <div class="discord-hint" id="discord-unlinked-hint"></div>
        </div>
      </div>

      <div class="dialog-total"><span>Total <small id="dialog-upgrade"></small></span><strong id="dialog-total"></strong></div>
      <p class="error" id="checkout-error" role="alert"></p>

      <button type="submit" class="btn btn-primary btn-lg btn-block pay-btn" id="pay-btn" disabled>Pagar con tarjeta</button>
      <p class="pay-note">${ICON_LOCK}<span>Pago seguro con <b>Stripe</b>: tarjeta, Apple Pay o Google Pay. Nunca vemos tus datos bancarios.</span></p>
      <p class="pay-legal">Precio final en USD. Al pagar aceptas los <a href="/terminos" target="_blank">Términos y condiciones</a> y el
        <a href="/privacidad" target="_blank">Aviso de privacidad</a>. Tienes 5 días hábiles para cancelar la compra.
        Tierras Fantásticas es un servidor independiente: no es un producto oficial de Minecraft ni está asociado con Mojang o Microsoft.</p>
    </form>
  </dialog>`;

  const sells = ['inicio', 'tienda', 'crates'].includes(page);
  let dialog, qtyInput;
  if (sells) {
    document.body.insertAdjacentHTML('beforeend', DIALOG_HTML);
    dialog = $('#checkout-dialog');
    qtyInput = $('#quantity');
  }

  // Jugador comprobado contra el servidor: { name, uuid, quote, ... } o null.
  let player = null;
  let lookupSeq = 0;
  let lookupTimer = null;

  const unitPrice = () => (player?.quote?.unit ?? ownedQuote(selected)?.unit ?? selected.price);

  function updateTotal() {
    const qty = Math.max(1, Number.parseInt(qtyInput.value, 10) || 1);
    $('#dialog-total').textContent = formatPrice(unitPrice() * qty);
    const from = player?.quote?.upgradeFrom;
    $('#dialog-upgrade').textContent = from ? `· mejora desde ${from.replace(/^Rango\s+/i, '')}` : '';
  }

  function setPayable() {
    const blocked = Boolean(player?.quote?.blocked);
    $('#pay-btn').disabled = !player || blocked || paying;
  }

  document.addEventListener('click', (e) => {
    const id = e.target.closest('[data-buy]')?.dataset.buy;
    if (id) openCheckout(id);
  });

  function openCheckout(id) {
    selected = products.find((p) => p.id === id);
    if (!selected || !config.paymentsEnabled || !dialog) return;

    const maxQty = selected.maxQuantity || 10;
    const isCrate = selected.category === 'crates';
    applyTheme(dialog, selected);
    const pic = $('#dialog-img');
    const src = selected.image;
    pic.hidden = !src;
    if (src) {
      pic.src = asset(src);
      pic.classList.toggle('pixel-img', isPixel(src));
    }
    $('#dialog-cat').textContent = isCrate ? 'Crate · set completo' : CATEGORY_LABEL[selected.category] || selected.category;
    $('#dialog-title').textContent = selected.name;
    $('#dialog-desc').textContent = isCrate ? selected.tagline || selected.description : selected.description;
    qtyInput.value = 1;
    qtyInput.max = maxQty;
    $('#quantity-field').hidden = maxQty === 1;
    showError('');
    paying = false;
    $('#pay-btn').textContent = 'Pagar con tarjeta';
    $('#pay-btn').classList.remove('is-loading');
    // Con la cuenta vinculada, el jugador ya se sabe.
    $('#username').value = me.user?.name || storageGet('tf-username');
    player = null;
    renderPlayer();
    updateTotal();
    renderDiscordBox();
    dialog.showModal();
    lookupPlayer();
  }

  // --- Comprobar el jugador: la tienda solo vende a nombres que han entrado al servidor ---
  function scheduleLookup() {
    clearTimeout(lookupTimer);
    player = null;
    renderPlayer('typing');
    updateTotal();
    lookupTimer = setTimeout(lookupPlayer, 350);
  }

  async function lookupPlayer() {
    const name = $('#username').value.trim();
    const seq = ++lookupSeq;
    player = null;
    if (!name) return renderPlayer();
    if (!/^[A-Za-z0-9_]{3,16}$/.test(name)) return renderPlayer('invalid');
    renderPlayer('loading');
    try {
      const res = await fetch(`/api/player/${encodeURIComponent(name)}?product=${encodeURIComponent(selected.id)}`);
      const data = await res.json();
      if (seq !== lookupSeq) return;
      player = data.found ? data : null;
      renderPlayer(data.found ? 'found' : 'missing', name);
    } catch {
      if (seq === lookupSeq) renderPlayer('error');
    }
    updateTotal();
  }

  function renderPlayer(state, name) {
    const card = $('#player-card');
    setPayable();
    card.hidden = !state || state === 'typing';
    card.className = `player-card ${state || ''}`;
    if (card.hidden) return;
    if (state === 'loading') {
      card.innerHTML = '<span class="spinner" aria-hidden="true"></span><span>Buscando en el servidor…</span>';
    } else if (state === 'invalid') {
      card.innerHTML = '<span>Solo letras, números y _ (de 3 a 16).</span>';
    } else if (state === 'missing') {
      card.innerHTML = `<span><b>${escapeHtml(name)}</b> nunca ha entrado a ${escapeHtml(config.serverName || 'el servidor')}. Revisa el nombre o entra una vez al servidor y vuelve aquí.</span>`;
    } else if (state === 'error') {
      card.innerHTML = '<span>No pudimos comprobar el nombre. Revisa tu conexión.</span>';
    } else if (state === 'found') {
      const p = player;
      const rank = p.rank
        ? `<span class="rank-pill" style="--rank:${escapeHtml(p.rank.hex || '#e3b74c')}">${escapeHtml(p.rank.prefix)}</span>`
        : '';
      const blocked = p.quote?.blocked ? `<span class="player-note bad">${escapeHtml(p.quote.blocked)}</span>` : '';
      const upgrade = p.quote?.upgradeFrom
        ? `<span class="player-note">Ya tiene ${escapeHtml(p.quote.upgradeFrom)}: solo pagas la diferencia.</span>`
        : '';
      card.innerHTML = `
        <img src="${escapeHtml(p.head)}" alt="" width="40" height="40" class="pixel-img">
        <div>
          <div class="player-name"><b>${escapeHtml(p.name)}</b>${rank}</div>
          <span class="player-ok">✓ Jugador verificado · ${p.online ? '<span class="on">conectado ahora</span>' : 'se le entrega al entrar'}</span>
          ${blocked}${upgrade}
        </div>`;
      if (p.quote?.blocked) card.classList.add('blocked');
    }
  }

  // --- Cuenta del jugador ---
  let me = { user: null };
  const discordAvatar = (d) => d?.avatar || 'https://cdn.discordapp.com/embed/avatars/0.png';

  async function loadMe() {
    try {
      me = await (await fetch('/api/me')).json();
    } catch {
      me = { user: null };
    }
    renderAccountChip();
  }

  // Botón de la cabecera: "Entrar" o la cabeza y el nombre del jugador.
  function renderAccountChip() {
    $$('[data-account-chip]').forEach((chip) => {
      chip.hidden = false;
      chip.href = '/cuenta';
      chip.classList.toggle('signed', Boolean(me.user));
      if (me.user) {
        chip.innerHTML = `<img src="${escapeHtml(me.user.head)}" alt="" width="26" height="26" class="pixel-img"><span>${escapeHtml(me.user.name)}</span>`;
        chip.setAttribute('aria-label', `Mi cuenta (${me.user.name})`);
      } else if (page !== 'cuenta') {
        chip.href = `/cuenta?return=${encodeURIComponent(location.pathname + location.hash)}`;
      }
    });
  }

  // Precio de mejora que ve el jugador con sesión en las tarjetas de rangos (el real lo calcula el servidor).
  function ownedQuote(p) {
    const owned = me.user?.rank;
    if (!owned || p?.category !== 'rangos' || !p.tier) return null;
    if (owned.tier >= p.tier) return { owned: true };
    const from = products.find((x) => x.id === owned.id);
    return from ? { unit: Math.max(50, p.price - from.price) } : null;
  }

  function renderDiscordBox() {
    const user = me.user;
    $('#discord-linked').hidden = !user;
    $('#discord-unlinked').hidden = Boolean(user);
    if (user) {
      $('#discord-name').textContent = user.name;
      $('#discord-avatar').src = user.head;
      $('#discord-linked-hint').textContent = user.discord
        ? selected.discordRole
          ? `Recibirás el rol en Discord (@${user.discord.username}).`
          : `Te mencionaremos en Discord (@${user.discord.username}).`
        : 'Conecta tu Discord en «Mi cuenta» para recibir el rol.';
    } else {
      $('#discord-login').href = `/cuenta?return=${encodeURIComponent(`${location.pathname}?buy=${selected.id}`)}`;
      $('#discord-unlinked-hint').textContent = 'Opcional: con tu cuenta se rellena tu nombre, ves tus compras y recibes el rol de Discord.';
    }
  }

  // Volver de Discord o de Stripe: reabrir la compra y avisar del resultado.
  function handleReturnParams() {
    const params = new URLSearchParams(location.search);
    const buy = params.get('buy');
    const result = params.get('discord');
    const cancelled = params.get('cancel');
    if (!buy && !result && !cancelled) return;
    const product = products.find((p) => p.id === buy);
    let hash = location.hash;
    if (product && page === 'tienda') {
      hash = `#${product.category}`;
      selectCategory(product.category, false);
    }
    history.replaceState(null, '', `${location.pathname}${hash}`);
    if (cancelled) toast('Pago cancelado. No se ha realizado ningún cargo.');
    if (buy) openCheckout(buy);
    else if (result === 'ok') toast('Sesión iniciada');
  }

  // --- Pago con Stripe ---
  const showError = (msg) => sells && ($('#checkout-error').textContent = msg);
  let paying = false;

  if (sells) {
    $('#username').addEventListener('input', scheduleLookup);
    qtyInput.addEventListener('input', updateTotal);
    $('#dialog-close').addEventListener('click', () => dialog.close());
    dialog.addEventListener('click', (e) => {
      if (e.target === dialog) dialog.close();
    });
    $('#checkout-form').addEventListener('submit', (e) => {
      e.preventDefault();
      pay();
    });
  }

  // El servidor crea el pago con el precio del catálogo y nos manda a la página segura de Stripe.
  async function pay() {
    if (paying || !player || player.quote?.blocked) return;
    const quantity = Number.parseInt(qtyInput.value, 10) || 1;
    const max = selected.maxQuantity || 10;
    if (quantity < 1 || quantity > max) return showError(`La cantidad debe estar entre 1 y ${max}.`);
    showError('');
    paying = true;
    setPayable();
    $('#pay-btn').textContent = 'Abriendo el pago seguro…';
    $('#pay-btn').classList.add('is-loading');
    try {
      const res = await fetch('/api/checkout', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ productId: selected.id, username: player.name, uuid: player.uuid, quantity }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok || !data.url) throw new Error(data.error || 'No se pudo iniciar el pago.');
      storageSet('tf-username', player.name);
      window.location.href = data.url;
    } catch (err) {
      paying = false;
      $('#pay-btn').textContent = 'Pagar con tarjeta';
      $('#pay-btn').classList.remove('is-loading');
      showError(err.message);
      setPayable();
    }
  }

  // --- Página de la cuenta ---
  const STATUS_TEXT = {
    queued: ['Pagado · se entrega al entrar', 'wait'],
    delivered: ['Entregado', 'ok'],
    delivery_failed: ['El staff lo entregará', 'wait'],
    awaiting_payment: ['Esperando el cobro', 'wait'],
    failed: ['Pago no completado', 'bad'],
    error: ['En revisión', 'bad'],
  };

  const DISCORD_RESULT = {
    ok: 'Discord conectado ✓',
    notmember: 'Discord conectado, pero aún no estás en el servidor de Discord de Tierras Fantásticas.',
    taken: 'Ese Discord ya está conectado a otro jugador.',
    noaccount: 'Ese Discord no tiene cuenta todavía: crea tu cuenta con tu nombre de Minecraft y conéctalo después.',
    cancel: 'Cancelaste el inicio de sesión en Discord.',
    error: 'No se pudo conectar con Discord. Inténtalo de nuevo.',
    off: 'Discord aún no está disponible en la web.',
  };

  // A dónde volver después de entrar (solo rutas de esta web).
  function accountReturn() {
    const ret = new URLSearchParams(location.search).get('return');
    return ret && /^\/(?!\/)/.test(ret) ? ret : null;
  }

  async function initAccount() {
    const params = new URLSearchParams(location.search);
    const result = params.get('discord');
    if (result && DISCORD_RESULT[result]) toast(DISCORD_RESULT[result]);
    if (result) {
      params.delete('discord');
      history.replaceState(null, '', `${location.pathname}${params.toString() ? `?${params}` : ''}`);
    }
    if (me.user) renderAccount();
    else renderAuth(result === 'noaccount' ? 'register' : 'login');
  }

  // --- Entrar / crear cuenta ---
  function renderAuth(tab) {
    const discordBtn = config.discordLogin
      ? `<div class="auth-or"><span>o</span></div>
         <a class="btn btn-discord btn-block" href="/auth/discord?return=${encodeURIComponent(accountReturn() || '/cuenta')}">${ICON_DISCORD} Entrar con Discord</a>
         <p class="muted small center">Solo si ya conectaste tu Discord a tu cuenta.</p>`
      : '';
    $('#account').innerHTML = `
      <div class="panel account-card auth-card">
        <div class="auth-tabs" role="tablist">
          <button type="button" role="tab" data-tab="login" aria-selected="${tab === 'login'}">Entrar</button>
          <button type="button" role="tab" data-tab="register" aria-selected="${tab === 'register'}">Crear cuenta</button>
        </div>
        <form id="auth-form" novalidate>
          <div class="field">
            <label for="auth-name">Tu nombre de Minecraft</label>
            <input id="auth-name" autocomplete="username" autocapitalize="off" spellcheck="false" required minlength="3" maxlength="16" placeholder="Steve_123">
            <div class="player-card" id="auth-player" aria-live="polite" hidden></div>
          </div>
          <div class="field">
            <label for="auth-pass">Contraseña</label>
            <input id="auth-pass" type="password" required minlength="8" maxlength="128">
          </div>
          <div class="field" id="auth-pass2-field">
            <label for="auth-pass2">Repite la contraseña</label>
            <input id="auth-pass2" type="password" autocomplete="new-password" minlength="8" maxlength="128">
          </div>
          <p class="muted small" id="auth-note"></p>
          <p class="error" id="auth-error" role="alert"></p>
          <button class="btn btn-primary btn-lg btn-block" type="submit" id="auth-submit"></button>
        </form>
        ${discordBtn}
      </div>`;
    setAuthTab(tab);
    $$('.auth-tabs [data-tab]').forEach((b) => b.addEventListener('click', () => setAuthTab(b.dataset.tab)));
    $('#auth-name').addEventListener('input', () => authTab === 'register' && scheduleAuthLookup());
    $('#auth-form').addEventListener('submit', (e) => {
      e.preventDefault();
      submitAuth();
    });
  }

  let authTab = 'login';
  let authLookupTimer = null;
  let authSeq = 0;

  function setAuthTab(tab) {
    authTab = tab;
    $$('.auth-tabs [data-tab]').forEach((b) => b.setAttribute('aria-selected', String(b.dataset.tab === tab)));
    const register = tab === 'register';
    $('#auth-pass2-field').hidden = !register;
    $('#auth-pass').autocomplete = register ? 'new-password' : 'current-password';
    $('#auth-submit').textContent = register ? 'Crear mi cuenta' : 'Entrar';
    $('#auth-note').textContent = register
      ? 'Usa tu nombre exacto de Minecraft: tienes que haber entrado al servidor al menos una vez. Contraseña de 8 caracteres o más.'
      : '';
    $('#auth-error').textContent = '';
    $('#auth-player').hidden = true;
    if (register && $('#auth-name').value) scheduleAuthLookup();
  }

  function scheduleAuthLookup() {
    clearTimeout(authLookupTimer);
    authLookupTimer = setTimeout(async () => {
      const name = $('#auth-name').value.trim();
      const box = $('#auth-player');
      const seq = ++authSeq;
      if (!/^[A-Za-z0-9_]{3,16}$/.test(name)) {
        box.hidden = !name;
        box.className = 'player-card invalid';
        box.innerHTML = '<span>Solo letras, números y _ (de 3 a 16).</span>';
        return;
      }
      try {
        const data = await (await fetch(`/api/player/${encodeURIComponent(name)}`)).json();
        if (seq !== authSeq || authTab !== 'register') return;
        box.hidden = false;
        if (data.found) {
          box.className = 'player-card found';
          box.innerHTML = `<img src="${escapeHtml(data.head)}" alt="" width="40" height="40" class="pixel-img">
            <div><div class="player-name"><b>${escapeHtml(data.name)}</b></div><span class="player-ok">✓ Jugador del servidor</span></div>`;
        } else {
          box.className = 'player-card missing';
          box.innerHTML = `<span><b>${escapeHtml(name)}</b> nunca ha entrado al servidor. Entra una vez con ese nombre y vuelve.</span>`;
        }
      } catch {
        /* sin conexión: lo dirá el servidor al enviar */
      }
    }, 350);
  }

  async function submitAuth() {
    const name = $('#auth-name').value.trim();
    const password = $('#auth-pass').value;
    const error = (msg) => ($('#auth-error').textContent = msg);
    if (!/^[A-Za-z0-9_]{3,16}$/.test(name)) return error('Escribe tu nombre de Minecraft (3-16 letras, números o _).');
    if (authTab === 'register') {
      if (password.length < 8) return error('La contraseña debe tener al menos 8 caracteres.');
      if (password !== $('#auth-pass2').value) return error('Las contraseñas no coinciden.');
    } else if (!password) {
      return error('Escribe tu contraseña.');
    }
    error('');
    const btn = $('#auth-submit');
    btn.disabled = true;
    btn.classList.add('is-loading');
    try {
      const res = await fetch(authTab === 'register' ? '/api/auth/register' : '/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, password }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(data.error || 'No se pudo entrar.');
      me = { user: data.user };
      storageSet('tf-username', data.user.name);
      const ret = accountReturn();
      if (ret) {
        location.href = ret;
        return;
      }
      renderAccountChip();
      renderAccount();
      toast(authTab === 'register' ? `¡Bienvenido, ${data.user.name}! Conecta ahora tu Discord.` : `Hola, ${data.user.name}`);
    } catch (err) {
      error(err.message);
    } finally {
      btn.disabled = false;
      btn.classList.remove('is-loading');
    }
  }

  // --- Mi cuenta ---
  function renderAccount() {
    const u = me.user;
    const d = u.discord;
    const rank = u.rank
      ? `<span class="rank-pill" style="--rank:${escapeHtml(u.rank.hex || '#e3b74c')}">${escapeHtml(u.rank.prefix)}</span>`
      : '<span class="muted small">Sin rango</span>';
    const invite = config.discordInvite || config.discordUrl;
    let discordHtml;
    if (d) {
      discordHtml = `
        <div class="account-who">
          <img src="${escapeHtml(discordAvatar(d))}" alt="" width="56" height="56" class="round">
          <div><b>${escapeHtml(d.name || d.username)}</b><span class="muted small">@${escapeHtml(d.username)}</span></div>
        </div>
        ${
          d.member
            ? '<p class="member-ok">✓ Miembro del Discord de Tierras Fantásticas</p>'
            : `<p class="member-bad">✖ Aún no estás en el Discord de Tierras Fantásticas</p>
               <div class="account-actions">
                 <a class="btn btn-discord btn-sm" href="${escapeHtml(invite)}" target="_blank" rel="noopener">Unirme al Discord</a>
                 <button class="btn btn-ghost btn-sm" type="button" id="discord-check">Ya me uní, comprobar</button>
               </div>`
        }
        <button class="link-btn left" type="button" id="discord-unlink">Desconectar Discord</button>`;
    } else if (config.discordLogin) {
      discordHtml = `
        <p class="muted">Conecta tu Discord para recibir los roles de tus rangos, que te mencionemos en los anuncios y poder entrar con Discord.</p>
        <a class="btn btn-discord btn-sm" href="/auth/discord?return=%2Fcuenta">${ICON_DISCORD} Conectar Discord</a>`;
    } else {
      discordHtml = '<p class="muted">La conexión con Discord estará disponible muy pronto.</p>';
    }

    $('#account').innerHTML = `
      <div class="account-grid">
        <section class="panel account-card">
          <span class="cat">Minecraft</span>
          <div class="account-who">
            <img src="${escapeHtml(u.head)}" alt="" width="56" height="56" class="pixel-img">
            <div><b>${escapeHtml(u.name)}</b>${rank}<span class="muted small uuid">${escapeHtml(u.uuid)}</span></div>
          </div>
          <div class="account-actions">
            <button class="btn btn-ghost btn-sm" type="button" id="logout">Cerrar sesión</button>
            <button class="link-btn" type="button" id="show-password">Cambiar contraseña</button>
          </div>
          <form id="password-form" class="password-form" hidden novalidate>
            <div class="field"><label for="pw-current">Contraseña actual</label><input id="pw-current" type="password" autocomplete="current-password"></div>
            <div class="field"><label for="pw-new">Nueva contraseña</label><input id="pw-new" type="password" autocomplete="new-password" minlength="8"></div>
            <p class="error" id="pw-error" role="alert"></p>
            <button class="btn btn-primary btn-sm" type="submit">Guardar</button>
          </form>
        </section>
        <section class="panel account-card">
          <span class="cat">Discord</span>
          ${discordHtml}
        </section>
      </div>
      <section class="panel account-card account-orders">
        <div class="orders-head"><span class="cat">Mis compras</span><a class="btn btn-primary btn-sm" href="/tienda">Ir a la tienda</a></div>
        <div id="orders"><p class="muted">Cargando…</p></div>
      </section>`;

    $('#logout').addEventListener('click', async () => {
      await fetch('/auth/logout', { method: 'POST' }).catch(() => {});
      location.href = '/';
    });
    $('#show-password').addEventListener('click', () => ($('#password-form').hidden = !$('#password-form').hidden));
    $('#password-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      const res = await fetch('/api/account/password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ current: $('#pw-current').value, password: $('#pw-new').value }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) return ($('#pw-error').textContent = data.error || 'No se pudo cambiar.');
      $('#password-form').hidden = true;
      $('#password-form').reset();
      toast('Contraseña cambiada');
    });
    $('#discord-unlink')?.addEventListener('click', async () => {
      if (!confirm('¿Desconectar tu Discord de la cuenta?')) return;
      await fetch('/api/account/discord/unlink', { method: 'POST' });
      me.user.discord = null;
      renderAccount();
    });
    $('#discord-check')?.addEventListener('click', async () => {
      const data = await (await fetch('/api/account/discord/check', { method: 'POST' })).json().catch(() => ({}));
      if (data.reconnect) {
        location.href = '/auth/discord?return=%2Fcuenta';
        return;
      }
      if (data.member) {
        me.user.discord.member = true;
        renderAccount();
        toast('✓ Ya eres miembro del Discord');
      } else {
        toast(data.error || 'Todavía no apareces en el servidor de Discord.');
      }
    });
    loadOrders();
  }

  // Lo que se pagó: dinero, monedas del servidor o nada (recompensas gratis)
  function orderTotal(o) {
    if (o.coins) return `${new Intl.NumberFormat('es-ES', { useGrouping: 'always' }).format(o.coins)} monedas`;
    if (!o.amount) return 'Gratis';
    return new Intl.NumberFormat('en-US', { style: 'currency', currency: o.currency }).format(o.amount / 100);
  }

  async function loadOrders() {
    const box = $('#orders');
    try {
      const { orders } = await (await fetch('/api/account/orders')).json();
      if (!orders.length) {
        box.innerHTML = '<p class="muted">Todavía no tienes compras. ¡Echa un vistazo a la tienda!</p>';
        return;
      }
      box.innerHTML = `<ul class="orders">${orders
        .map((o) => {
          const [text, kind] = STATUS_TEXT[o.status] || [o.status, 'wait'];
          const date = new Date(o.createdAt).toLocaleDateString('es', { day: 'numeric', month: 'short', year: 'numeric' });
          return `<li>
            ${o.image ? img(o.image, '', 'order-img') : '<span class="order-img"></span>'}
            <div class="order-main">
              <b>${escapeHtml(o.product)}${o.quantity > 1 ? ` ×${o.quantity}` : ''}</b>
              <span class="muted small">${escapeHtml(o.username)} · ${escapeHtml(date)} · ${escapeHtml(o.id)}${o.upgradeFrom ? ` · mejora desde ${escapeHtml(o.upgradeFrom)}` : ''}</span>
            </div>
            <div class="order-side">
              <b>${orderTotal(o)}</b>
              <span class="state" data-kind="${o.refunded ? 'bad' : kind}">${o.refunded ? escapeHtml(o.refunded) : text}</span>
            </div>
          </li>`;
        })
        .join('')}</ul>`;
    } catch {
      box.innerHTML = '<p class="muted">No se pudieron cargar tus compras.</p>';
    }
  }

  function storageGet(key) {
    try {
      return localStorage.getItem(key) || '';
    } catch {
      return '';
    }
  }
  function storageSet(key, value) {
    try {
      localStorage.setItem(key, value);
    } catch {
      /* almacenamiento no disponible */
    }
  }

  loadConfig()
    .then(() => Promise.all([sells ? loadProducts() : null, loadMe()]))
    .then(() => {
      if (sells) {
        rerenderRanks();
        handleReturnParams();
      }
      if (page === 'cuenta') initAccount();
    });
})();
