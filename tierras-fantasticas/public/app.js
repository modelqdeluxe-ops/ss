(() => {
  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
  const page = document.body.dataset.page;
  let config = { currency: 'usd', paymentsEnabled: false };
  let products = [];
  let selected = null;

  const formatPrice = (cents) =>
    new Intl.NumberFormat('es', { style: 'currency', currency: config.currency.toUpperCase() }).format(cents / 100);

  const escapeHtml = (s) =>
    String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

  // Rutas de imagen del catálogo: solo archivos de esta web.
  const asset = (p) => (typeof p === 'string' && /^[\w\-./]+$/.test(p) && !p.includes('..') ? `/${p.replace(/^\//, '')}` : '');

  $$('[data-year]').forEach((el) => (el.textContent = new Date().getFullYear()));

  // En móvil la fila de pestañas tiene scroll: dejamos visible la pestaña activa.
  const activeTab = $('.tabs-nav [aria-current="page"]');
  if (activeTab) {
    const bar = activeTab.parentElement;
    bar.scrollLeft = activeTab.offsetLeft - (bar.clientWidth - activeTab.offsetWidth) / 2;
  }

  // --- Configuración del servidor ---
  async function loadConfig() {
    try {
      config = await (await fetch('/api/config')).json();
    } catch {
      return;
    }
    $$('[data-server-name]').forEach((el) => (el.textContent = config.serverName));
    $$('[data-server-ip]').forEach((el) => (el.textContent = config.serverIp));
    document.title = document.title.replace('Tierras Fantásticas', config.serverName);
    window.tfSplitTitle?.();
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
    const marks = [...$$('[data-status]'), ...$$('.nav-ip')];
    try {
      const data = await fetchStatus();
      marks.forEach((el) => el.classList.add(data.online ? 'online' : 'offline'));
      const text = data.online
        ? `En línea · ${data.players?.online ?? 0}/${data.players?.max ?? '?'} jugadores`
        : 'Servidor desconectado';
      texts.forEach((el) => (el.textContent = text));
      $$('[data-players]').forEach((el) => (el.textContent = data.online ? data.players?.online ?? 0 : 'Off'));
    } catch {
      texts.forEach((el) => (el.textContent = 'Estado no disponible'));
    }
  }

  // --- Copiar IP (cualquier botón con data-copy-ip) ---
  function toast(msg) {
    $('.toast')?.remove();
    const el = document.createElement('div');
    el.className = 'toast pixel';
    el.setAttribute('role', 'status');
    el.textContent = msg;
    document.body.append(el);
    setTimeout(() => el.remove(), 2500);
  }

  document.addEventListener('click', async (e) => {
    const btn = e.target.closest('[data-copy-ip]');
    if (!btn) return;
    const ipEl = $('[data-server-ip]', btn) || $('[data-server-ip]');
    const hint = $('[data-copy-hint]', btn) || $('.copy-tag', btn);
    try {
      await navigator.clipboard.writeText(config.serverIp || ipEl.textContent);
      toast('¡IP copiada! Nos vemos dentro');
      btn.classList.remove('copied');
      void btn.offsetWidth; // reinicia la animación
      btn.classList.add('copied');
      if (hint) {
        const original = hint.dataset.original || (hint.dataset.original = hint.textContent);
        hint.textContent = '¡Copiada!';
        setTimeout(() => (hint.textContent = original), 2500);
      }
    } catch {
      // Sin portapapeles: seleccionamos la IP para copiarla a mano.
      const range = document.createRange();
      range.selectNodeContents(ipEl);
      getSelection().removeAllRanges();
      getSelection().addRange(range);
      toast('Pulsa Ctrl+C para copiar la IP');
    }
  });

  const CATEGORY_LABEL = { rangos: 'Rango', crates: 'Llave de crate', llaves: 'Llaves de cofre', monedas: 'Monedas de oro' };
  const crates = () => products.filter((p) => p.category === 'crates');
  const themeAttr = (p) => (p.theme && /^[\w-]+$/.test(p.theme) ? ` data-theme="${p.theme}"` : '');
  const buyAttrs = (p) => `data-buy="${escapeHtml(p.id)}" ${config.paymentsEnabled ? '' : 'disabled'}`;

  // --- Productos ---
  async function loadProducts() {
    try {
      products = await (await fetch('/api/products')).json();
    } catch {
      $$('#products, #crate-spotlight, #stage-info').forEach(
        (el) => (el.innerHTML = '<p class="muted small">No se pudieron cargar los productos. Recarga la página.</p>'),
      );
      return;
    }
    if (page === 'inicio') renderSpotlight();
    if (page === 'tienda') initShop();
    if (page === 'crates') initCrates();
  }

  // Inicio: tarjetas de los crates destacados
  function renderSpotlight() {
    const list = crates();
    $$('[data-crate-count]').forEach((el) => (el.textContent = list.length));
    const box = $('#crate-spotlight');
    if (!list.length) {
      box.closest('section').hidden = true;
      return;
    }
    const minPrice = (p) => formatPrice(p.price);
    box.innerHTML = list
      .map(
        (p, i) => `
        <a class="crate-card pixel" href="/crates#${escapeHtml(p.theme || p.id)}"${themeAttr(p)} style="--i:${i}">
          <div class="rays" aria-hidden="true"></div>
          ${p.rarity ? `<span class="rarity">${escapeHtml(p.rarity)}</span>` : ''}
          <figure><img src="${asset(p.image)}" alt="${escapeHtml(p.name)}" loading="lazy"></figure>
          <h3 class="theme-text">${escapeHtml(p.name)}</h3>
          <p>${escapeHtml(p.tagline || p.description)}</p>
          <div class="card-foot">
            <div><span class="from">Llave</span><span class="price">${minPrice(p)}</span></div>
            <span class="btn btn-theme btn-sm pixel">Ver crate</span>
          </div>
        </a>`,
      )
      .join('');
  }

  // Tienda: pestañas por categoría con indicador deslizante
  function productCard(p, i) {
    const isCrate = p.category === 'crates';
    const lore = isCrate
      ? `<a href="/crates#${escapeHtml(p.theme || p.id)}">Ver todo lo que contiene →</a>`
      : p.discordRole
        ? 'Entrega en el juego y en Discord'
        : 'Entrega instantánea en el juego';
    return `
      <article class="product pixel cat-${escapeHtml(p.category)}${p.featured ? ' featured' : ''}"${themeAttr(p)} style="--i:${i}">
        ${p.featured ? '<span class="badge pixel">Más popular</span>' : ''}
        ${p.image ? `<div class="thumb"><img src="${asset(p.image)}" alt="" loading="lazy"></div>` : ''}
        <span class="cat">${escapeHtml(p.rarity || CATEGORY_LABEL[p.category] || p.category)}</span>
        <h3>${escapeHtml(p.name)}</h3>
        <p>${escapeHtml(p.description)}</p>
        <span class="lore">${lore}</span>
        <div class="product-footer">
          <span class="price">${formatPrice(p.price)}</span>
          <button class="btn btn-primary btn-sm pixel" ${buyAttrs(p)}>${isCrate ? 'Comprar llave' : 'Comprar'}</button>
        </div>
      </article>`;
  }

  let currentCategory = null;
  function initShop() {
    const bar = $('#shop-tabs');
    const tabs = $$('[role="tab"]', bar);
    for (const tab of tabs) {
      const count = products.filter((p) => p.category === tab.dataset.category).length;
      tab.hidden = count === 0;
      tab.insertAdjacentHTML('beforeend', ` <span class="count">${count}</span>`);
      tab.addEventListener('click', () => selectCategory(tab.dataset.category, true));
    }
    // Flechas del teclado entre pestañas
    bar.addEventListener('keydown', (e) => {
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
    window.addEventListener('resize', moveIndicator);
    document.fonts?.ready.then(moveIndicator);
    // Activamos la animación del indicador después de colocarlo
    requestAnimationFrame(() => bar.classList.add('ready'));
  }

  function selectCategory(category, updateUrl) {
    currentCategory = category;
    for (const tab of $$('#shop-tabs [role="tab"]')) {
      const active = tab.dataset.category === category;
      tab.setAttribute('aria-selected', String(active));
      tab.tabIndex = active ? 0 : -1;
    }
    const list = products.filter((p) => p.category === category);
    $('#products').innerHTML = list.map(productCard).join('') || '<p class="loading">No hay productos en esta categoría</p>';
    moveIndicator();
    if (updateUrl) history.replaceState(null, '', `#${category}`);
  }

  function moveIndicator() {
    const bar = $('#shop-tabs');
    const active = bar && $('[aria-selected="true"]', bar);
    if (!active) return;
    const ind = $('.indicator', bar);
    ind.style.width = `${active.offsetWidth}px`;
    ind.style.transform = `translateX(${active.offsetLeft}px)`;
  }

  // Crates: selector horizontal y escenario con el set elegido
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
        <button type="button" class="crate-pick pixel" role="tab" data-crate="${escapeHtml(p.id)}"${themeAttr(p)} aria-selected="false" aria-controls="crate-stage">
          <img src="${asset(p.image)}" alt="" width="64" height="52">
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
    if (p.theme) document.body.dataset.theme = p.theme;
    for (const btn of $$('#crate-picker [data-crate]')) {
      const active = btn.dataset.crate === id;
      btn.setAttribute('aria-selected', String(active));
      btn.tabIndex = active ? 0 : -1;
    }

    const img = $('#stage-img');
    img.src = asset(p.image);
    img.alt = `${p.name}: armas, herramientas y armadura del set`;
    img.hidden = false;

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
               .map(
                 (a, i) => `<div class="armor-slot pixel" style="--i:${i}">
                   <img src="${asset(a.icon)}" alt="" width="48" height="48"><span>${escapeHtml(a.name)}</span></div>`,
               )
               .join('')}</div>`
          : ''
      }
      ${
        items.length
          ? `<h4>Armas, herramientas y cosméticos</h4>
             <ul class="item-chips">${items.map((it, i) => `<li style="--i:${i}">${escapeHtml(it)}</li>`).join('')}</ul>`
          : ''
      }
      <div class="buy-bar">
        <div><span class="price">${formatPrice(p.price)}</span><span class="per">por llave · entrega instantánea</span></div>
        <button class="btn btn-theme btn-lg pixel" ${buyAttrs(p)}>Comprar llave</button>
      </div>`;

    const stage = $('#crate-stage');
    stage.classList.remove('swap');
    void stage.offsetWidth; // reinicia la animación de cambio
    stage.classList.add('swap');
    window.tfEmbers?.burst();
    if (updateUrl) history.replaceState(null, '', `#${p.theme || p.id}`);
  }

  // --- Ventana de compra (se crea solo en las páginas que venden) ---
  const DIALOG_HTML = `
  <dialog id="checkout-dialog" class="dialog pixel">
    <form id="checkout-form" method="dialog">
      <button type="button" class="dialog-close" id="dialog-close">Cerrar</button>
      <img class="dialog-img" id="dialog-img" alt="" hidden>
      <div class="cat" id="dialog-cat"></div>
      <h3 id="dialog-title">Producto</h3>
      <p class="dialog-desc" id="dialog-desc"></p>

      <label for="username">Tu nombre de Minecraft</label>
      <input id="username" name="username" autocomplete="username" required minlength="3" maxlength="16" pattern="[A-Za-z0-9_]{3,16}" placeholder="Steve_123">

      <label for="quantity" id="quantity-label">Cantidad</label>
      <input id="quantity" name="quantity" type="number" min="1" max="10" value="1">

      <div class="discord-box pixel" id="discord-box" hidden>
        <div class="discord-linked" id="discord-linked" hidden>
          <img id="discord-avatar" alt="" width="36" height="36">
          <div>
            <div class="discord-name">Discord: <strong id="discord-name"></strong></div>
            <div class="discord-hint" id="discord-linked-hint"></div>
          </div>
          <button type="button" class="link-btn" id="discord-logout">Cambiar</button>
        </div>
        <div id="discord-unlinked" hidden>
          <a class="btn btn-discord btn-block pixel" id="discord-login" href="/auth/discord">Vincular mi Discord</a>
          <div class="discord-hint" id="discord-unlinked-hint"></div>
        </div>
      </div>

      <div class="dialog-total">Total: <strong id="dialog-total"></strong></div>
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
    const img = $('#dialog-img');
    img.hidden = !selected.image;
    if (selected.image) img.src = asset(selected.image);
    $('#dialog-cat').textContent = isCrate ? 'Llave de crate' : CATEGORY_LABEL[selected.category] || selected.category;
    $('#dialog-title').textContent = selected.name;
    $('#dialog-desc').textContent = isCrate ? selected.tagline || selected.description : selected.description;
    $('#quantity-label').textContent = isCrate ? 'Número de llaves' : 'Cantidad';
    qtyInput.value = 1;
    qtyInput.max = maxQty;
    qtyInput.hidden = $('#quantity-label').hidden = maxQty === 1;
    $('#checkout-error').textContent = '';
    $('#username').value = localStorageGet('tf-username');
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
        style: { layout: 'vertical', color: 'gold', shape: 'pill', label: 'pay', height: 45 },

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
          if (!res.ok || !data.id) {
            showError(data.error || 'No se pudo iniciar el pago.');
            throw new Error(data.error);
          }
          localStorageSet('tf-username', username);
          return data.id;
        },

        // El comprador aprobó: el servidor cobra y entrega la compra.
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

  function localStorageGet(key) {
    try {
      return localStorage.getItem(key) || '';
    } catch {
      return '';
    }
  }
  function localStorageSet(key, value) {
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
