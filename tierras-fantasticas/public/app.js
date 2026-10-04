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
    $('#processing').hidden = true;
    $('#paypal-buttons').classList.remove('disabled');
    dialog.showModal();
    renderPayPalButtons();
  });

  qtyInput.addEventListener('input', updateTotal);
  $('#dialog-close').addEventListener('click', () => dialog.close());
  dialog.addEventListener('click', (e) => {
    if (e.target === dialog) dialog.close();
  });
  // Enter en un campo no debe cerrar la ventana.
  $('#checkout-form').addEventListener('submit', (e) => e.preventDefault());

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

  loadConfig().then(loadProducts);
})();
