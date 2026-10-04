(() => {
  const $ = (sel) => document.querySelector(sel);
  let config = { currency: 'usd', paymentsEnabled: false };
  let products = [];
  let selected = null;

  const formatPrice = (cents) =>
    new Intl.NumberFormat('es', { style: 'currency', currency: config.currency.toUpperCase() }).format(cents / 100);

  const escapeHtml = (s) =>
    String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

  $('#year').textContent = new Date().getFullYear();

  // --- Configuración del servidor ---
  async function loadConfig() {
    try {
      config = await (await fetch('/api/config')).json();
    } catch {
      return;
    }
    document.querySelectorAll('[data-server-name]').forEach((el) => (el.textContent = config.serverName));
    document.querySelectorAll('[data-server-ip]').forEach((el) => (el.textContent = config.serverIp));
    document.title = `${config.serverName} — Servidor de Minecraft`;
    if (config.discordUrl) {
      const link = $('.discord-link');
      link.href = config.discordUrl;
      link.hidden = false;
    }
    $('#payments-off').hidden = config.paymentsEnabled;
    loadStatus();
  }

  // --- Estado en vivo (API pública mcsrvstat.us) ---
  async function loadStatus() {
    const box = $('#status');
    const text = $('#status-text');
    try {
      const res = await fetch(`https://api.mcsrvstat.us/3/${encodeURIComponent(config.serverIp)}`);
      const data = await res.json();
      if (data.online) {
        box.className = 'status online';
        text.textContent = `En línea · ${data.players?.online ?? 0}/${data.players?.max ?? '?'} jugadores`;
      } else {
        box.className = 'status offline';
        text.textContent = 'Servidor desconectado';
      }
    } catch {
      text.textContent = 'Estado no disponible';
    }
  }

  // --- Copiar IP ---
  $('#copy-ip').addEventListener('click', async () => {
    const hint = $('#copy-hint');
    try {
      await navigator.clipboard.writeText(config.serverIp || $('[data-server-ip]').textContent);
      hint.textContent = '¡Copiada! Nos vemos dentro ✨';
    } catch {
      hint.textContent = 'Copia la IP manualmente';
    }
    setTimeout(() => (hint.textContent = 'Clic para copiar'), 2500);
  });

  // --- Productos ---
  async function loadProducts() {
    const container = $('#products');
    try {
      products = await (await fetch('/api/products')).json();
      renderProducts('all');
    } catch {
      container.innerHTML = '<p class="loading">No se pudieron cargar los productos.</p>';
    }
  }

  function renderProducts(category) {
    const list = category === 'all' ? products : products.filter((p) => p.category === category);
    $('#products').innerHTML = list
      .map(
        (p) => `
        <article class="product${p.featured ? ' featured' : ''}">
          ${p.featured ? '<span class="badge">Más popular</span>' : ''}
          <div class="product-icon">${escapeHtml(p.image || '🎁')}</div>
          <h3>${escapeHtml(p.name)}</h3>
          <p>${escapeHtml(p.description)}</p>
          <div class="product-footer">
            <span class="price">${formatPrice(p.price)}</span>
            <button class="btn btn-primary" data-buy="${escapeHtml(p.id)}" ${config.paymentsEnabled ? '' : 'disabled'}>Comprar</button>
          </div>
        </article>`,
      )
      .join('') || '<p class="loading">No hay productos en esta categoría.</p>';
  }

  document.querySelectorAll('.tab').forEach((tab) =>
    tab.addEventListener('click', () => {
      document.querySelectorAll('.tab').forEach((t) => t.classList.remove('active'));
      tab.classList.add('active');
      renderProducts(tab.dataset.category);
    }),
  );

  // --- Ventana de compra ---
  const dialog = $('#checkout-dialog');
  const qtyInput = $('#quantity');

  function updateTotal() {
    const qty = Math.max(1, Number.parseInt(qtyInput.value, 10) || 1);
    $('#dialog-total').textContent = formatPrice(selected.price * qty);
  }

  $('#products').addEventListener('click', (e) => {
    const id = e.target.closest('[data-buy]')?.dataset.buy;
    if (!id) return;
    selected = products.find((p) => p.id === id);
    if (!selected) return;

    const maxQty = selected.maxQuantity || 10;
    $('#dialog-icon').textContent = selected.image || '🎁';
    $('#dialog-title').textContent = selected.name;
    $('#dialog-desc').textContent = selected.description;
    qtyInput.value = 1;
    qtyInput.max = maxQty;
    qtyInput.hidden = $('#quantity-label').hidden = maxQty === 1;
    $('#checkout-error').textContent = '';
    $('#username').value = localStorageGet('tf-username');
    updateTotal();
    dialog.showModal();
  });

  qtyInput.addEventListener('input', updateTotal);
  $('#dialog-close').addEventListener('click', () => dialog.close());
  dialog.addEventListener('click', (e) => {
    if (e.target === dialog) dialog.close();
  });

  $('#checkout-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const username = $('#username').value.trim();
    const quantity = Number.parseInt(qtyInput.value, 10) || 1;
    const error = $('#checkout-error');
    const btn = $('#pay-btn');

    if (!/^[A-Za-z0-9_]{3,16}$/.test(username)) {
      error.textContent = 'Introduce un nombre de Minecraft válido (3-16 letras, números o _).';
      return;
    }

    btn.disabled = true;
    btn.textContent = 'Redirigiendo a la pasarela…';
    error.textContent = '';
    try {
      const res = await fetch('/api/checkout', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ productId: selected.id, username, quantity }),
      });
      const data = await res.json();
      if (!res.ok || !data.url) throw new Error(data.error || 'No se pudo iniciar el pago.');
      localStorageSet('tf-username', username);
      window.location.href = data.url;
    } catch (err) {
      error.textContent = err.message;
      btn.disabled = false;
      btn.textContent = 'Pagar de forma segura';
    }
  });

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

  loadConfig().then(loadProducts);
})();
