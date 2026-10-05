// Tierras Fantásticas: menú, estado del servidor, tienda, crates, cuenta de Discord y compra con Stripe.
(() => {
  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
  const page = document.body.dataset.page;
  const escapeHtml = (s) =>
    String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
  const asset = (path) => (/^(https?:)?\//.test(path) ? path : `/${path}`);
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
      texts.forEach((el) => (el.textContent = online ? `En línea · ${count} ${word} conectados` : 'Servidor desconectado'));
      $$('[data-players]').forEach((el) => (el.textContent = online ? count : 'Off'));
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
  const CATEGORY_LABEL = { rangos: 'Rango', crates: 'Llave de crate', llaves: 'Llaves de cofre', monedas: 'Monedas de oro' };
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
  const isPixel = (src) => /\/ranks\/|coins-/.test(src || '');
  const img = (src, alt, cls = '', extra = '') =>
    `<img src="${asset(src)}" alt="${escapeHtml(alt)}" loading="lazy" decoding="async"${
      isPixel(src) || cls ? ` class="${[cls, isPixel(src) ? 'pixel-img' : ''].filter(Boolean).join(' ')}"` : ''
    }${extra}>`;

  async function loadProducts() {
    try {
      products = await (await fetch('/api/products')).json();
    } catch {
      $$('#products, #crate-spotlight, #stage-info').forEach(
        (el) => (el.innerHTML = '<p class="muted">No se pudieron cargar los productos. Recarga la página.</p>'),
      );
      return;
    }
    if (page === 'inicio') renderHome();
    if (page === 'tienda') initShop();
    if (page === 'crates') initCrates();
  }

  // Inicio: crates destacados y rangos
  function renderHome() {
    const list = crates();
    $$('[data-crate-count]').forEach((el) => (el.textContent = list.length));
    const box = $('#crate-spotlight');
    $$('[data-crates-all]').forEach((el) => (el.textContent = `Ver los ${list.length} crates`));
    box.innerHTML = list
      .slice(0, 8)
      .map(
        (p) => `
        <a class="panel crate-card reveal" href="/crates#${escapeHtml(p.theme || p.id)}"${themeAttr(p)}>
          <figure>${img(p.image, p.name)}</figure>
          <div class="body">
            ${p.rarity ? `<span class="rarity">${escapeHtml(p.rarity)}</span>` : ''}
            <h3>${escapeHtml(p.name)}</h3>
            <p>${escapeHtml(p.tagline || p.description)}</p>
            <div class="card-foot">
              <span class="price"><small>Llave desde</small>${formatPrice(p.price)}</span>
              <span class="btn btn-ghost btn-sm">Ver crate ${ICON_ARROW}</span>
            </div>
          </div>
        </a>`,
      )
      .join('');
    const strip = $('#rank-strip');
    strip.innerHTML = ranks()
      .map(
        (p) => `
        <a class="panel rank-mini reveal" href="/tienda#rangos">
          ${p.image ? img(p.image, '', '', ' width="88" height="88"') : ''}
          <b>${escapeHtml(p.name.replace(/^Rango\s+/i, ''))}</b>
          <span>${formatPrice(p.price)}</span>
        </a>`,
      )
      .join('');
    observeReveal();
  }

  // Tienda: pestañas por categoría
  function productCard(p) {
    const isCrate = p.category === 'crates';
    const perks = Array.isArray(p.perks) ? p.perks.slice(0, 5) : [];
    const thumb = isCrate ? p.image : p.image || p.keyImage;
    return `
      <article class="panel product${p.featured ? ' featured' : ''}"${themeAttr(p)}>
        ${p.featured ? '<span class="badge">Más popular</span>' : ''}
        ${thumb ? `<div class="thumb">${img(thumb, '')}</div>` : ''}
        <div class="body">
          <span class="cat">${escapeHtml(p.rarity || CATEGORY_LABEL[p.category] || p.category)}</span>
          <h3>${escapeHtml(p.name)}</h3>
          <p>${escapeHtml(isCrate ? p.tagline || p.description : p.description)}</p>
          ${perks.length ? `<ul class="perks">${perks.map((x) => `<li>${escapeHtml(x)}</li>`).join('')}</ul>` : ''}
          ${isCrate ? `<p class="small"><a class="theme-text" href="/crates#${escapeHtml(p.theme || p.id)}">Ver sus ${(p.models || []).length || ''} objetos en 3D →</a></p>` : ''}
          <div class="card-foot">${rankFoot(p) || `
            <span class="price">${formatPrice(p.price)}</span>
            <button class="btn ${isCrate ? 'btn-theme' : 'btn-gold'} btn-sm" type="button" ${buyAttrs(p)}>${isCrate ? 'Comprar llave' : 'Comprar'}</button>`}
          </div>
        </div>
      </article>`;
  }

  // Con Minecraft vinculado: su rango actual no se vuelve a vender y los superiores salen a precio de mejora.
  function rankFoot(p) {
    const q = ownedQuote(p);
    if (!q) return '';
    if (q.owned) {
      const mine = me.minecraft.rank.id === p.id;
      return `<span class="price owned">${mine ? 'Tu rango' : 'Incluido'}</span><button class="btn btn-ghost btn-sm" type="button" disabled>${mine ? '✓ Lo tienes' : 'Ya incluido'}</button>`;
    }
    return `<span class="price"><small>Mejora · antes ${formatPrice(p.price)}</small>${formatPrice(q.unit)}</span><button class="btn btn-gold btn-sm" type="button" ${buyAttrs(p)}>Mejorar</button>`;
  }

  function rerenderRanks() {
    if (page === 'tienda' && currentCategory === 'rangos' && me.minecraft?.rank) selectCategory('rangos', false);
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

  let currentCategory = null;
  function initShop() {
    const tabs = $$('#shop-tabs [role="tab"]');
    for (const tab of tabs) {
      const count = products.filter((p) => p.category === tab.dataset.category).length;
      tab.hidden = count === 0;
      tab.insertAdjacentHTML('beforeend', `<span class="count">${count}</span>`);
      tab.addEventListener('click', () => selectCategory(tab.dataset.category, true));
    }
    $('#shop-tabs').addEventListener('keydown', (e) => {
      if (!['ArrowLeft', 'ArrowRight'].includes(e.key)) return;
      const visible = tabs.filter((t) => !t.hidden);
      const idx = visible.findIndex((t) => t.dataset.category === currentCategory);
      const next = visible[(idx + (e.key === 'ArrowRight' ? 1 : -1) + visible.length) % visible.length];
      selectCategory(next.dataset.category, true);
      next.focus();
    });
    const fromHash = () => {
      const cat = location.hash.slice(1);
      return tabs.some((t) => t.dataset.category === cat && !t.hidden) ? cat : null;
    };
    selectCategory(fromHash() || tabs.find((t) => !t.hidden)?.dataset.category || 'rangos', false);
    window.addEventListener('hashchange', () => fromHash() && selectCategory(fromHash(), false));
  }

  function selectCategory(category, updateUrl) {
    currentCategory = category;
    for (const tab of $$('#shop-tabs [role="tab"]')) {
      const active = tab.dataset.category === category;
      tab.setAttribute('aria-selected', String(active));
      tab.tabIndex = active ? 0 : -1;
      if (active) tab.scrollIntoView({ block: 'nearest', inline: 'nearest' });
    }
    const list = products.filter((p) => p.category === category);
    if (category === 'rangos') list.sort((a, b) => (a.tier || 0) - (b.tier || 0));
    $('#products').innerHTML = list.map(productCard).join('') || '<p class="loading">No hay productos en esta categoría</p>';
    $('#compare').hidden = category !== 'rangos';
    if (category === 'rangos') renderCompare();
    if (updateUrl) history.replaceState(null, '', `#${category}`);
  }

  // Crates: selector y escenario con el set elegido
  function initCrates() {
    const list = crates();
    const picker = $('#crate-picker');
    if (!list.length) {
      $('#stage-info').innerHTML = '<p class="muted">No hay crates disponibles ahora mismo. ¡Vuelve pronto!</p>';
      return;
    }
    picker.innerHTML = list
      .map(
        (p) => `
        <button type="button" class="crate-pick" role="tab" data-crate="${escapeHtml(p.id)}"${themeAttr(p)} aria-selected="false" aria-controls="crate-stage">
          ${img(p.keyImage || p.image, '', '', ' width="48" height="40"')}
          <span><b>${escapeHtml(p.name)}</b><small>${escapeHtml(p.rarity || 'Crate')} · ${formatPrice(p.price)}</small></span>
        </button>`,
      )
      .join('');
    picker.addEventListener('click', (e) => {
      const id = e.target.closest('[data-crate]')?.dataset.crate;
      if (id) showCrate(id, true);
    });
    picker.addEventListener('keydown', (e) => {
      if (!['ArrowLeft', 'ArrowRight'].includes(e.key)) return;
      const idx = list.findIndex((p) => p.id === selectedCrate);
      const next = list[(idx + (e.key === 'ArrowRight' ? 1 : -1) + list.length) % list.length];
      showCrate(next.id, true);
      $(`[data-crate="${next.id}"]`, picker).focus();
    });
    const fromHash = () => {
      const h = decodeURIComponent(location.hash.slice(1));
      return list.find((p) => p.theme === h || p.id === h);
    };
    showCrate((fromHash() || list.find((p) => p.featured) || list[0]).id, false);
    window.addEventListener('hashchange', () => fromHash() && showCrate(fromHash().id, false));
  }

  let selectedCrate = null;
  function showCrate(id, updateUrl) {
    const p = products.find((x) => x.id === id);
    if (!p || id === selectedCrate) return;
    selectedCrate = id;
    const stage = $('#crate-stage');
    applyTheme(stage, p);
    for (const btn of $$('#crate-picker [data-crate]')) {
      const active = btn.dataset.crate === id;
      btn.setAttribute('aria-selected', String(active));
      btn.tabIndex = active ? 0 : -1;
      if (active && updateUrl) btn.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'smooth' });
    }

    const art = $('#stage-img');
    const fresh = art.cloneNode();
    fresh.src = asset(p.image);
    fresh.alt = `${p.name}: armas, herramientas y armadura del set`;
    fresh.hidden = false;
    art.replaceWith(fresh); // reinicia la animación de entrada

    const armor = Array.isArray(p.armor) ? p.armor : [];
    const items = Array.isArray(p.items) ? p.items : [];
    $('#stage-info').innerHTML = `
      ${p.rarity ? `<span class="rarity">${escapeHtml(p.rarity)}</span>` : ''}
      <h2 class="theme-text">${escapeHtml(p.name)}</h2>
      ${p.tagline ? `<p class="tag">${escapeHtml(p.tagline)}</p>` : ''}
      <p class="desc">${escapeHtml(p.description)}</p>
      ${
        armor.length
          ? `<h4>Armadura animada</h4>
             <div class="armor-row">${armor
               .map((a) => `<div class="armor-slot">${img(a.icon, '', 'pixel-img', ' width="40" height="40"')}<span>${escapeHtml(a.name)}</span></div>`)
               .join('')}</div>`
          : ''
      }
      ${
        modelItems(p).length
          ? `<h4>Objetos del set <span class="hint3d">· toca uno para verlo en 3D</span></h4>
             <div class="gallery">${modelItems(p)
               .map(
                 (m, i) => `<button type="button" class="gallery-item" data-view="${i}" aria-label="Ver ${escapeHtml(m.name)} en 3D">
                   ${img(m.thumb, '', '', ' width="72" height="72"')}<span>${escapeHtml(m.name)}</span></button>`,
               )
               .join('')}</div>`
          : ''
      }
      <div class="buy-bar">
        <span class="price"><small>Por llave</small>${formatPrice(p.price)}</span>
        <button class="btn btn-theme btn-lg" type="button" ${buyAttrs(p)}>Comprar llave</button>
      </div>`;
    if (updateUrl) history.replaceState(null, '', `#${p.theme || p.id}`);
  }

  // --- Visor 3D (se carga solo al abrir un objeto) ---
  function modelItems(p) {
    if (!p || !p.set || !Array.isArray(p.models)) return [];
    return p.models.map((m) => ({
      name: m.name,
      // Solo las armas se ponen de pie; cofres, alas, cascos o armaduras se ven tal cual.
      upright: !/^(chest|wings?|helmet|hat|shield|armor_|backpack|little_dragon|cape|tail)/.test(m.id),
      thumb: `img/items/${p.set}/${m.id}.webp`,
      model: `/models/${p.set}/${m.id}.json`,
    }));
  }

  document.addEventListener('click', async (e) => {
    const btn = e.target.closest('[data-view]');
    if (!btn) return;
    const p = products.find((x) => x.id === selectedCrate);
    const items = modelItems(p);
    if (!items.length) return;
    btn.classList.add('loading');
    try {
      const { openViewer } = await import('/viewer.js');
      openViewer(items, Number(btn.dataset.view), p.name);
    } catch (err) {
      console.error(err);
      toast('No se pudo abrir el visor 3D en este navegador.');
    } finally {
      btn.classList.remove('loading');
    }
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

      <div class="discord-box" id="discord-box" hidden>
        <div class="discord-linked" id="discord-linked" hidden>
          <img id="discord-avatar" alt="" width="36" height="36">
          <div>
            <div>Discord: <strong id="discord-name"></strong></div>
            <div class="discord-hint" id="discord-linked-hint"></div>
          </div>
          <button type="button" class="link-btn" id="discord-logout">Cambiar</button>
        </div>
        <div id="discord-unlinked" hidden>
          <a class="btn btn-discord btn-block" id="discord-login" href="/auth/discord">Iniciar sesión con Discord</a>
          <div class="discord-hint" id="discord-unlinked-hint"></div>
        </div>
      </div>

      <div class="dialog-total"><span>Total <small id="dialog-upgrade"></small></span><strong id="dialog-total"></strong></div>
      <p class="error" id="checkout-error" role="alert"></p>

      <button type="submit" class="btn btn-gold btn-lg btn-block pay-btn" id="pay-btn" disabled>Pagar con tarjeta</button>
      <p class="pay-note">${ICON_LOCK}<span>Pago seguro con <b>Stripe</b>: tarjeta, Apple Pay o Google Pay. Nunca vemos tus datos bancarios.</span></p>
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
    const src = isCrate ? selected.keyImage || selected.image : selected.image;
    pic.hidden = !src;
    if (src) {
      pic.src = asset(src);
      pic.classList.toggle('pixel-img', isPixel(src));
    }
    $('#dialog-cat').textContent = isCrate ? 'Llave de crate' : CATEGORY_LABEL[selected.category] || selected.category;
    $('#dialog-title').textContent = selected.name;
    $('#dialog-desc').textContent = isCrate ? selected.tagline || selected.description : selected.description;
    $('#quantity-label').textContent = isCrate ? 'Número de llaves' : 'Cantidad';
    qtyInput.value = 1;
    qtyInput.max = maxQty;
    $('#quantity-field').hidden = maxQty === 1;
    showError('');
    paying = false;
    $('#pay-btn').textContent = 'Pagar con tarjeta';
    // Con la cuenta vinculada, el jugador ya se sabe.
    $('#username').value = me.minecraft?.name || storageGet('tf-username');
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

  // --- Cuenta (Discord) ---
  let me = { discord: null, minecraft: null };

  async function loadMe() {
    if (!config.discordLogin) return renderAccountChip();
    try {
      me = await (await fetch('/api/me')).json();
    } catch {
      me = { discord: null, minecraft: null };
    }
    renderAccountChip();
  }

  // Chip de la cabecera: "Entrar" o el avatar de la cuenta.
  function renderAccountChip() {
    $$('[data-account-chip]').forEach((chip) => {
      chip.hidden = !config.discordLogin;
      if (!config.discordLogin) return;
      if (me.discord) {
        chip.href = '/cuenta';
        chip.classList.add('signed');
        chip.innerHTML = `<img src="${escapeHtml(me.discord.avatar || 'https://cdn.discordapp.com/embed/avatars/0.png')}" alt="" width="26" height="26"><span>${escapeHtml(me.discord.username)}</span>`;
        chip.setAttribute('aria-label', `Mi cuenta (${me.discord.username})`);
      } else {
        chip.href = `/auth/discord?return=${encodeURIComponent(page === 'cuenta' ? '/cuenta' : location.pathname)}`;
      }
    });
  }

  // Precio de mejora que ve el jugador vinculado en las tarjetas de rangos (el real lo calcula el servidor).
  function ownedQuote(p) {
    const owned = me.minecraft?.rank;
    if (!owned || p?.category !== 'rangos' || !p.tier) return null;
    if (owned.tier >= p.tier) return { owned: true };
    const from = products.find((x) => x.id === owned.id);
    return from ? { unit: Math.max(50, p.price - from.price) } : null;
  }

  function renderDiscordBox() {
    $('#discord-box').hidden = !config.discordLogin;
    if (!config.discordLogin) return;
    const roleText = selected.discordRole
      ? 'Recibirás el rol en nuestro Discord automáticamente.'
      : 'Te mencionaremos en el anuncio de Discord.';

    $('#discord-linked').hidden = !me.discord;
    $('#discord-unlinked').hidden = Boolean(me.discord);
    if (me.discord) {
      $('#discord-name').textContent = me.discord.username;
      $('#discord-avatar').src = me.discord.avatar || 'https://cdn.discordapp.com/embed/avatars/0.png';
      $('#discord-linked-hint').textContent = roleText;
    } else {
      // Al volver de Discord reabrimos este mismo producto en esta misma página.
      $('#discord-login').href = `/auth/discord?return=${encodeURIComponent(`${location.pathname}?buy=${selected.id}`)}`;
      $('#discord-unlinked-hint').textContent = selected.discordRole
        ? 'Opcional: inicia sesión para recibir también el rango en Discord y ver tus compras.'
        : 'Opcional: inicia sesión para que te mencionemos en el anuncio y ver tus compras.';
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
    if (product && page === 'crates' && product.category === 'crates') {
      hash = `#${product.theme || product.id}`;
      showCrate(product.id, false);
    } else if (product && page === 'tienda') {
      hash = `#${product.category}`;
      selectCategory(product.category, false);
    }
    history.replaceState(null, '', `${location.pathname}${hash}`);
    if (cancelled) toast('Pago cancelado. No se ha realizado ningún cargo.');
    if (buy) {
      openCheckout(buy);
      if (result === 'cancel') showError('No iniciaste sesión con Discord. Puedes comprar igualmente.');
      if (result === 'error') showError('No se pudo iniciar sesión con Discord. Inténtalo de nuevo o compra sin ella.');
    } else if (result === 'ok') {
      toast('Sesión iniciada con Discord');
    }
  }

  // --- Pago con Stripe ---
  const showError = (msg) => sells && ($('#checkout-error').textContent = msg);
  let paying = false;

  if (sells) {
    $('#discord-logout').addEventListener('click', async () => {
      await fetch('/auth/logout', { method: 'POST' }).catch(() => {});
      me = { discord: null, minecraft: null };
      renderDiscordBox();
      renderAccountChip();
    });
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

  async function initAccount() {
    const root = $('#account');
    if (!config.discordLogin) {
      root.innerHTML = '<div class="panel account-card"><p class="muted">El inicio de sesión con Discord no está disponible ahora mismo.</p></div>';
      return;
    }
    const params = new URLSearchParams(location.search);
    if (params.get('discord')) {
      if (params.get('discord') === 'error') toast('No se pudo iniciar sesión con Discord.');
      history.replaceState(null, '', location.pathname);
    }
    if (!me.discord) {
      root.innerHTML = `
        <div class="panel account-card account-login reveal in">
          <h2>Entra con tu Discord</h2>
          <p class="muted">Vincula tu jugador de Minecraft, mira tus compras y recibe los roles de Discord de tus rangos.</p>
          <a class="btn btn-discord btn-lg" href="/auth/discord?return=%2Fcuenta">Iniciar sesión con Discord</a>
        </div>`;
      return;
    }
    renderAccount();
  }

  function renderAccount(code) {
    const d = me.discord;
    const mc = me.minecraft;
    const rank = mc?.rank
      ? `<span class="rank-pill" style="--rank:${escapeHtml(mc.rank.hex || '#e3b74c')}">${escapeHtml(mc.rank.prefix)}</span>`
      : '<span class="muted small">Sin rango</span>';
    $('#account').innerHTML = `
      <div class="account-grid">
        <section class="panel account-card">
          <span class="cat">Discord</span>
          <div class="account-who">
            <img src="${escapeHtml(d.avatar || 'https://cdn.discordapp.com/embed/avatars/0.png')}" alt="" width="56" height="56" class="round">
            <div><b>${escapeHtml(d.username)}</b><span class="muted small">Sesión iniciada</span></div>
          </div>
          <button class="btn btn-ghost btn-sm" type="button" id="logout">Cerrar sesión</button>
        </section>
        <section class="panel account-card">
          <span class="cat">Minecraft</span>
          ${
            mc
              ? `<div class="account-who">
                  <img src="${escapeHtml(mc.head)}" alt="" width="56" height="56" class="pixel-img">
                  <div><b>${escapeHtml(mc.name)}</b>${rank}<span class="muted small uuid">${escapeHtml(mc.uuid)}</span></div>
                </div>
                <button class="btn btn-ghost btn-sm" type="button" id="unlink">Desvincular</button>`
              : code
                ? `<p>Entra al servidor y escribe en el chat:</p>
                  <button class="link-code" type="button" id="copy-code" aria-label="Copiar el comando">/tf vincular <b>${escapeHtml(code.code)}</b><span>Copiar</span></button>
                  <p class="muted small" id="link-wait"><span class="spinner" aria-hidden="true"></span> Esperando a que lo escribas… (caduca en 10 minutos)</p>`
                : `<p class="muted">Vincula tu jugador para que la tienda lo rellene sola, ver tu rango y recibir el rol de Discord aunque compres sin sesión.</p>
                  <button class="btn btn-gold btn-sm" type="button" id="link">Vincular mi Minecraft</button>`
          }
        </section>
      </div>
      <section class="panel account-card account-orders">
        <div class="orders-head"><span class="cat">Mis compras</span><a class="btn btn-gold btn-sm" href="/tienda">Ir a la tienda</a></div>
        <div id="orders"><p class="muted">Cargando…</p></div>
      </section>`;
    $('#logout').addEventListener('click', async () => {
      await fetch('/auth/logout', { method: 'POST' }).catch(() => {});
      location.href = '/';
    });
    $('#unlink')?.addEventListener('click', async () => {
      if (!confirm(`¿Desvincular a ${mc.name} de tu cuenta?`)) return;
      await fetch('/api/account/unlink', { method: 'POST' });
      me.minecraft = null;
      renderAccount();
    });
    $('#link')?.addEventListener('click', startLink);
    loadOrders();
    $('#copy-code')?.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(`/tf vincular ${code.code}`);
        toast('Comando copiado. Pégalo en el chat del juego.');
      } catch {
        toast(`/tf vincular ${code.code}`);
      }
    });
  }

  let linkTimer = null;
  async function startLink() {
    const res = await fetch('/api/account/link', { method: 'POST' });
    const code = await res.json().catch(() => ({}));
    if (!res.ok) return toast(code.error || 'No se pudo crear el código.');
    renderAccount(code);
    clearInterval(linkTimer);
    // Comprobamos cada pocos segundos si ya lo escribió en el juego.
    linkTimer = setInterval(async () => {
      if (Date.now() > code.expiresAt) {
        clearInterval(linkTimer);
        renderAccount();
        return toast('El código caducó. Pide otro.');
      }
      try {
        const data = await (await fetch('/api/account/link')).json();
        if (data.minecraft) {
          clearInterval(linkTimer);
          me = await (await fetch('/api/me')).json();
          renderAccount();
          toast(`¡${data.minecraft.name} vinculado!`);
        }
      } catch {
        /* lo intentamos en la siguiente vuelta */
      }
    }, 4000);
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
              <b>${new Intl.NumberFormat('en-US', { style: 'currency', currency: o.currency }).format(o.amount / 100)}</b>
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
