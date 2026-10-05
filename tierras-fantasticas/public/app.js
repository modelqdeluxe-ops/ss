// Tierras Fantásticas: menú, estado del servidor, tienda, crates y compra con PayPal.
(() => {
  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
  const page = document.body.dataset.page;
  const escapeHtml = (s) =>
    String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
  const asset = (path) => (/^(https?:)?\//.test(path) ? path : `/${path}`);
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
  const themeAttr = (p) => (p.theme && /^[\w-]+$/.test(p.theme) ? ` data-theme="${p.theme}"` : '');
  const buyAttrs = (p) => `data-buy="${escapeHtml(p.id)}" ${config.paymentsEnabled ? '' : 'disabled'}`;
  const isPixel = (src) => /\/ranks\//.test(src || '');
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
    box.innerHTML = list
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
          ${isCrate ? `<p class="small"><a class="theme-text" href="/crates#${escapeHtml(p.theme || p.id)}">Ver todo lo que contiene →</a></p>` : ''}
          <div class="card-foot">
            <span class="price">${formatPrice(p.price)}</span>
            <button class="btn ${isCrate ? 'btn-theme' : 'btn-gold'} btn-sm" type="button" ${buyAttrs(p)}>${isCrate ? 'Comprar llave' : 'Comprar'}</button>
          </div>
        </div>
      </article>`;
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
    if (p.theme) stage.dataset.theme = p.theme;
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
        Array.isArray(p.gallery) && p.gallery.length
          ? `<h4>El arsenal</h4>
             <div class="gallery">${p.gallery
               .map((g) => `<figure>${img(g.icon, '', '', ' width="96" height="96"')}<figcaption>${escapeHtml(g.name)}</figcaption></figure>`)
               .join('')}</div>`
          : ''
      }
      ${items.length ? `<h4>Armas, herramientas y cosméticos</h4><ul class="chips">${items.map((it) => `<li>${escapeHtml(it)}</li>`).join('')}</ul>` : ''}
      <div class="buy-bar">
        <span class="price"><small>Por llave</small>${formatPrice(p.price)}</span>
        <button class="btn btn-theme btn-lg" type="button" ${buyAttrs(p)}>Comprar llave</button>
      </div>`;
    if (updateUrl) history.replaceState(null, '', `#${p.theme || p.id}`);
  }

  // --- Ventana de compra (solo en las páginas que venden) ---
  const DIALOG_HTML = `
  <dialog id="checkout-dialog" class="dialog" aria-labelledby="dialog-title">
    <form id="checkout-form" method="dialog">
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
        <input id="username" name="username" autocomplete="username" autocapitalize="off" spellcheck="false" required minlength="3" maxlength="16" pattern="[A-Za-z0-9_]{3,16}" placeholder="Steve_123">
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
          <a class="btn btn-discord btn-block" id="discord-login" href="/auth/discord">Vincular mi Discord</a>
          <div class="discord-hint" id="discord-unlinked-hint"></div>
        </div>
      </div>

      <div class="dialog-total">Total <strong id="dialog-total"></strong></div>
      <p class="error" id="checkout-error" role="alert"></p>

      <div id="paypal-buttons" class="paypal-buttons"></div>
      <p class="processing" id="processing" hidden>Procesando tu pago…</p>
      <p class="muted small">Pago seguro con PayPal. Puedes pagar con tu cuenta o con tarjeta.</p>
    </form>
  </dialog>`;

  const sells = ['inicio', 'tienda', 'crates'].includes(page);
  let dialog, qtyInput;
  if (sells) {
    document.body.insertAdjacentHTML('beforeend', DIALOG_HTML);
    dialog = $('#checkout-dialog');
    qtyInput = $('#quantity');
  }

  function updateTotal() {
    const qty = Math.max(1, Number.parseInt(qtyInput.value, 10) || 1);
    $('#dialog-total').textContent = formatPrice(selected.price * qty);
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
    if (selected.theme) dialog.dataset.theme = selected.theme;
    else delete dialog.dataset.theme;
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
    $('#checkout-error').textContent = '';
    $('#username').value = storageGet('tf-username');
    updateTotal();
    $('#processing').hidden = true;
    $('#paypal-buttons').classList.remove('disabled');
    renderDiscordBox();
    dialog.showModal();
    renderPayPalButtons();
  }

  // --- Vincular Discord ---
  let discordAccount = null;

  async function loadMe() {
    if (!config.discordLogin || !sells) return;
    try {
      discordAccount = (await (await fetch('/api/me')).json()).discord;
    } catch {
      discordAccount = null;
    }
  }

  function renderDiscordBox() {
    $('#discord-box').hidden = !config.discordLogin;
    if (!config.discordLogin) return;
    const roleText = selected.discordRole
      ? 'Recibirás el rol en nuestro Discord automáticamente.'
      : 'Anunciaremos tu compra en nuestro Discord.';

    $('#discord-linked').hidden = !discordAccount;
    $('#discord-unlinked').hidden = Boolean(discordAccount);
    if (discordAccount) {
      $('#discord-name').textContent = discordAccount.username;
      $('#discord-avatar').src = discordAccount.avatar || 'https://cdn.discordapp.com/embed/avatars/0.png';
      $('#discord-linked-hint').textContent = roleText;
    } else {
      // Al volver de Discord reabrimos este mismo producto en esta misma página.
      $('#discord-login').href = `/auth/discord?return=${encodeURIComponent(`${location.pathname}?buy=${selected.id}`)}`;
      $('#discord-unlinked-hint').textContent = selected.discordRole
        ? 'Opcional: vincúlalo para recibir también el rango en Discord.'
        : 'Opcional: vincúlalo para que te mencionemos en el anuncio.';
    }
  }

  // Volver de Discord: reabrir la compra y avisar del resultado.
  function handleReturnParams() {
    const params = new URLSearchParams(location.search);
    const buy = params.get('buy');
    const result = params.get('discord');
    if (!buy && !result) return;
    const product = products.find((p) => p.id === buy);
    let hash = '';
    if (product && page === 'crates' && product.category === 'crates') {
      hash = `#${product.theme || product.id}`;
      showCrate(product.id, false);
    } else if (product && page === 'tienda') {
      hash = `#${product.category}`;
      selectCategory(product.category, false);
    }
    history.replaceState(null, '', `${location.pathname}${hash}`);
    if (buy) {
      openCheckout(buy);
      if (result === 'cancel') showError('No se vinculó Discord. Puedes comprar igualmente.');
      if (result === 'error') showError('No se pudo vincular Discord. Inténtalo de nuevo o compra sin vincular.');
    }
  }

  if (sells) {
    $('#discord-logout').addEventListener('click', async () => {
      await fetch('/auth/logout', { method: 'POST' }).catch(() => {});
      discordAccount = null;
      renderDiscordBox();
    });
    qtyInput.addEventListener('input', updateTotal);
    $('#dialog-close').addEventListener('click', () => dialog.close());
    dialog.addEventListener('click', (e) => {
      if (e.target === dialog) dialog.close();
    });
    // Enter en un campo no debe cerrar la ventana.
    $('#checkout-form').addEventListener('submit', (e) => e.preventDefault());
  }

  // --- PayPal ---
  const showError = (msg) => ($('#checkout-error').textContent = msg);

  function readForm() {
    return {
      username: $('#username').value.trim(),
      quantity: Number.parseInt(qtyInput.value, 10) || 1,
    };
  }

  function validateForm() {
    const { username, quantity } = readForm();
    if (!/^[A-Za-z0-9_]{3,16}$/.test(username)) {
      showError('Introduce un nombre de Minecraft válido (3-16 letras, números o _).');
      return false;
    }
    const max = selected.maxQuantity || 10;
    if (quantity < 1 || quantity > max) {
      showError(`La cantidad debe estar entre 1 y ${max}.`);
      return false;
    }
    showError('');
    return true;
  }

  let paypalReady = null;
  function loadPayPalSdk() {
    if (!paypalReady) {
      paypalReady = new Promise((resolve, reject) => {
        const params = new URLSearchParams({
          'client-id': config.paypalClientId,
          currency: config.currency,
          intent: 'capture',
          components: 'buttons',
        });
        const script = document.createElement('script');
        script.src = `https://www.paypal.com/sdk/js?${params}`;
        script.onload = () => resolve(window.paypal);
        script.onerror = () => {
          paypalReady = null;
          reject(new Error('No se pudo cargar PayPal. Revisa tu conexión o desactiva el bloqueador de anuncios.'));
        };
        document.head.appendChild(script);
      });
    }
    return paypalReady;
  }

  let buttonsRendered = false;
  async function renderPayPalButtons() {
    if (buttonsRendered) return;
    let paypal;
    try {
      paypal = await loadPayPalSdk();
    } catch (err) {
      showError(err.message);
      return;
    }
    buttonsRendered = true;

    paypal
      .Buttons({
        style: { layout: 'vertical', color: 'gold', shape: 'rect', label: 'pay', height: 48 },

        onClick: (data, actions) => (validateForm() ? actions.resolve() : actions.reject()),

        // El servidor crea el pedido con el precio del catálogo.
        createOrder: async () => {
          const { username, quantity } = readForm();
          const res = await fetch('/api/orders', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ productId: selected.id, username, quantity }),
          });
          const data = await res.json();
          if (!res.ok) {
            showError(data.error || 'No se pudo iniciar el pago.');
            throw new Error(data.error);
          }
          storageSet('tf-username', username);
          return data.id;
        },

        // El comprador aprobó: el servidor cobra y deja la compra lista para entregar.
        onApprove: async (data, actions) => {
          $('#processing').hidden = false;
          $('#paypal-buttons').classList.add('disabled');
          const res = await fetch(`/api/orders/${encodeURIComponent(data.orderID)}/capture`, { method: 'POST' });
          const result = await res.json().catch(() => ({}));

          if (result.retry) {
            // Tarjeta rechazada: PayPal deja elegir otro método de pago.
            $('#processing').hidden = true;
            $('#paypal-buttons').classList.remove('disabled');
            return actions.restart();
          }
          if (!res.ok) {
            $('#processing').hidden = true;
            $('#paypal-buttons').classList.remove('disabled');
            showError(result.error || 'No se pudo completar el pago.');
            return;
          }
          window.location.href = `/success.html?order=${encodeURIComponent(data.orderID)}`;
        },

        onCancel: () => showError('Pago cancelado. No se ha realizado ningún cargo.'),

        onError: (err) => {
          console.error(err);
          if (!$('#checkout-error').textContent) showError('Hubo un problema con PayPal. Inténtalo de nuevo.');
        },
      })
      .render('#paypal-buttons')
      .catch((err) => {
        buttonsRendered = false;
        console.error(err);
        showError('No se pudieron mostrar los botones de PayPal.');
      });
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
    .then(() => (sells ? Promise.all([loadProducts(), loadMe()]) : null))
    .then(() => sells && handleReturnParams());
})();
