// Tierras Fantásticas: menú, estado del servidor, tienda, crates, cuentas de jugador con Discord y compra con Stripe o PayPal.
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

  // --- Transición al cambiar de página: el contenido se desvanece hacia arriba y una barra de neón corre por arriba
  // (styles.css, «Transiciones entre páginas»). Solo en los enlaces a otras páginas de la web. ---
  const pageBar = document.createElement('div');
  pageBar.className = 'page-bar';
  pageBar.setAttribute('aria-hidden', 'true');
  document.body.append(pageBar);
  document.addEventListener('click', (e) => {
    const a = e.target.closest('a[href]');
    if (!a || e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
    if ((a.target && a.target !== '_self') || a.hasAttribute('download')) return;
    const url = new URL(a.href, location.href);
    if (url.origin !== location.origin || url.pathname.startsWith('/api/')) return;
    if (url.pathname === location.pathname && url.search === location.search) return; // misma página (#ancla)
    if (/\.(?!html$)\w+$/.test(url.pathname)) return; // archivos (imágenes, el .jar...)
    e.preventDefault();
    document.documentElement.classList.add('leaving');
    setTimeout(() => location.assign(url.href), 280);
  });
  // Al volver con el botón «atrás» la página sale de la caché tal como se fue: se quita el desvanecido
  addEventListener('pageshow', (e) => {
    if (e.persisted) document.documentElement.classList.remove('leaving');
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

  // --- Volver arriba: aparece al bajar más de una pantalla ---
  const toTop = $('[data-to-top]');
  if (toTop) {
    // Una comprobación por fotograma como mucho, y solo se toca la clase si cambia (scroll fluido en el móvil)
    let shown = null;
    let queued = false;
    const update = () => {
      queued = false;
      const show = window.scrollY > window.innerHeight * 0.9;
      if (show !== shown) toTop.classList.toggle('show', (shown = show));
    };
    window.addEventListener(
      'scroll',
      () => {
        if (!queued) {
          queued = true;
          requestAnimationFrame(update);
        }
      },
      { passive: true },
    );
    update();
    toTop.addEventListener('click', (e) => {
      e.preventDefault();
      window.scrollTo({ top: 0, behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
    });
  }

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
    // Lo que menciona PayPal solo se ve si PayPal está conectado.
    $$('[data-paypal]').forEach((el) => (el.hidden = !config.paypalEnabled));
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

  // --- Aceptar los Términos (casilla obligatoria: el servidor guarda versión, fecha, IP y navegador como prueba) ---
  const termsLabel = (id) => `
    <label class="terms-check" for="${id}">
      <input type="checkbox" id="${id}">
      <span class="terms-box" aria-hidden="true"></span>
      <span>He leído y acepto los <a href="/terminos" target="_blank" rel="noopener">Términos y condiciones</a> y el
        <a href="/privacidad" target="_blank" rel="noopener">Aviso de privacidad</a>.</span>
    </label>`;

  // Si la cuenta tiene una versión vieja de los Términos, los pide de nuevo. Devuelve true si los aceptó.
  function askTerms() {
    return new Promise((resolve) => {
      $('#terms-dialog')?.remove();
      document.body.insertAdjacentHTML(
        'beforeend',
        `<dialog class="dialog terms-dialog" id="terms-dialog" aria-labelledby="terms-title">
          <form method="dialog" class="dialog-body">
            <h3 id="terms-title">Actualizamos los Términos</h3>
            <p class="muted">Para seguir, revisa y acepta la versión vigente de los Términos y condiciones y del Aviso de
              privacidad. Guardamos la fecha y la versión que aceptas; puedes verlas en «Mi cuenta».</p>
            ${termsLabel('terms-again')}
            <p class="error" id="terms-error" role="alert"></p>
            <div class="dialog-actions">
              <button class="btn btn-ghost" value="cancel" type="submit">Ahora no</button>
              <button class="btn btn-primary" id="terms-accept" type="button" disabled>Aceptar y seguir</button>
            </div>
          </form>
        </dialog>`,
      );
      const dlg = $('#terms-dialog');
      const box = $('#terms-again');
      const btn = $('#terms-accept');
      box.addEventListener('change', () => (btn.disabled = !box.checked));
      let done = false;
      btn.addEventListener('click', async () => {
        btn.disabled = true;
        btn.classList.add('is-loading');
        const res = await fetch('/api/account/terms', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ acceptTerms: true }),
        }).catch(() => null);
        const data = res ? await res.json().catch(() => ({})) : {};
        btn.classList.remove('is-loading');
        if (!res?.ok) {
          btn.disabled = false;
          $('#terms-error').textContent = data.error || 'No se pudo guardar. Inténtalo de nuevo.';
          return;
        }
        if (data.user) me.user = data.user;
        done = true;
        dlg.close();
      });
      dlg.addEventListener('close', () => {
        dlg.remove();
        resolve(done);
      });
      dlg.showModal();
    });
  }

  // Envía una petición de la cuenta; si el servidor pide aceptar los Términos actualizados, los pide y la repite.
  async function postWithTerms(url, body) {
    const send = () =>
      fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
    let res = await send();
    let data = await res.clone().json().catch(() => ({}));
    if (res.status === 403 && data.code === 'terms' && (await askTerms())) {
      res = await send();
      data = await res.json().catch(() => ({}));
    }
    return { res, data };
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
    gratis: 'Recompensa gratis', rangos: 'Rango', crates: 'Crate', ruleta: 'Ruleta', monedas: 'Monedas de oro', cosmeticos: 'Cosmético',
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
      const [list] = await Promise.all([fetch('/api/products').then((r) => r.json()), page === 'tienda' ? loadVfx() : null]);
      products = list;
    } catch {
      $$('#products').forEach(
        (el) => (el.innerHTML = '<p class="muted">No se pudieron cargar los productos. Recarga la página.</p>'),
      );
      return;
    }
    if (page === 'tienda') initShop();
  }

  // Brillo de la crate: un aura de su color que late (con retrasos distintos para que no lata todo a la vez).
  const glow = (i) => `<span class="crate-glow" aria-hidden="true" style="--d:${(-i * 0.7).toFixed(1)}s"></span>`;

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
            <button class="btn btn-buy btn-sm" type="button" ${buyAttrs(p)}>Comprar</button>`}
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
            <button class="btn btn-buy btn-sm" type="button" ${buyAttrs(p)}>Comprar crate</button>
          </div>
        </div>
      </article>`;
  }

  function giftFoot(p) {
    if (p.price !== 0) return '';
    return `<span class="price free">Gratis</span><button class="btn btn-claim btn-sm" type="button" data-claim="${escapeHtml(p.id)}">Reclamar</button>`;
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
      const { res, data } = await postWithTerms('/api/claim', { productId: btn.dataset.claim });
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
    return `<span class="price"><small>Mejora · antes ${formatPrice(p.price)}</small>${formatPrice(q.unit)}</span><button class="btn btn-buy btn-sm" type="button" ${buyAttrs(p)}>Mejorar</button>`;
  }

  function rerenderRanks() {
    if (page === 'tienda' && currentCategory === 'rangos' && me.user?.rank) showRank(rankShow.current?.id);
  }

  const INTRO = {
    gratis: 'Recompensas que puedes reclamar gratis con tu cuenta, una vez por jugador.',
    rangos: '',
    cosmeticos: '',
    crates: 'Cada crate es un set completo, siempre el mismo y sin nada al azar: armas, herramientas, armadura y cosméticos animados. Es permanente, irrompible y queda vinculado a tu cuenta. Toca una para probártela en tu personaje.',
    ruleta: '',
    vfx: '',
    monedas: 'Monedas de oro para la economía del servidor: compra terrenos, objetos y lo que veas en la tienda de monedas.',
    tiendamonedas: '',
  };

  let currentCategory = null;
  let coinShopTimer = null;
  function initShop() {
    const tabs = $$('#shop-tabs [role="tab"]');
    for (const tab of tabs) {
      const cat = tab.dataset.category;
      const count =
        cat === 'tiendamonedas' ? null : cat === 'vfx' ? (vfxData ? vfxData.kills.length + vfxData.packs.length : 0) : products.filter((p) => p.category === cat).length;
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
      if (h.startsWith('rangos-')) return { cat: 'rangos', rank: `rango-${h.slice(7)}` };
      return tabs.some((t) => t.dataset.category === h && !t.hidden) ? { cat: h } : null;
    };
    const start = fromHash() || { cat: 'crates' };
    selectCategory(start.cat, false, start.rank);
    if (start.crate) openCrateView(start.crate);
    window.addEventListener('hashchange', () => {
      const h = fromHash();
      if (!h) return;
      if (h.cat !== currentCategory) selectCategory(h.cat, false, h.rank);
      else if (h.rank) showRank(h.rank);
      if (h.crate) openCrateView(h.crate);
    });
  }

  function selectCategory(category, updateUrl, focus) {
    currentCategory = category;
    for (const tab of $$('#shop-tabs [role="tab"]')) {
      const active = tab.dataset.category === category;
      tab.setAttribute('aria-selected', String(active));
      tab.tabIndex = active ? 0 : -1;
      if (active) tab.scrollIntoView({ block: 'nearest', inline: 'nearest' });
    }
    clearInterval(coinShopTimer);
    closeStages();
    const box = $('#products');
    box.className = 'products';
    // El tono de la sección (filos de las tarjetas y etiquetas), el mismo que su pestaña
    box.style.setProperty('--tone', `var(--tone-${category}, var(--a-400))`);
    $('#shop-intro').textContent = INTRO[category] || '';
    $('#shop-intro').hidden = !INTRO[category];
    $('#shop-search-wrap').hidden = category !== 'crates';
    $('#crate-empty').hidden = true;
    if (category === 'crates') {
      box.classList.add('crate-grid');
      box.innerHTML = crates().map((p, i) => crateCard(p, i)).join('');
      filterCrates();
    } else if (category === 'ruleta') {
      renderRoulette(box);
    } else if (category === 'tiendamonedas') {
      renderCoinShop(box);
      coinShopTimer = setInterval(() => renderCoinShop(box, true), 15000);
    } else if (category === 'rangos') {
      renderRanks(box, focus);
    } else if (category === 'cosmeticos') {
      renderCosmetics(box);
    } else if (category === 'vfx') {
      renderVfx(box);
    } else {
      box.classList.add('grid', 'grid-3');
      const list = products.filter((p) => p.category === category);
      box.innerHTML = list.map(productCard).join('') || '<p class="loading">No hay productos en esta sección</p>';
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

  // --- VFX: efectos de kill sueltos y paquetes de skills. Por ahora gratis: se obtienen y se equipan con la cuenta y
  // el servidor los pone solo. En el juego se activan solos y se ven en un indicador junto a la barra. ---
  let vfxData = null;
  let vfxTab = 'kills';
  let vfxCat = 'todos';
  async function loadVfx() {
    try {
      vfxData = await (await fetch('/api/vfx')).json();
    } catch {
      vfxData = null;
    }
  }

  const TRIGGER = {
    golpe: 'Al golpear',
    golpe_critico: 'Golpe crítico',
    golpe_corriendo: 'Golpe corriendo',
    golpe_agachado: 'Golpe agachado',
    dano: 'Al recibir daño',
    combate: 'En combate',
  };
  const triggerText = (s) => {
    let t = TRIGGER[s.trigger] || s.trigger;
    if (s.vida && s.vida < 1) t = `Con menos del ${Math.round(s.vida * 100)} % de vida`;
    if (s.chance && s.chance < 1) t += ` · ${Math.round(s.chance * 100)} %`;
    return t;
  };
  const seconds = (n) => `${String(n).replace('.', ',')} s`;

  function vfxFoot(kind, id) {
    const mine = vfxData?.me;
    const key = `${kind}:${id}`;
    const attrs = `data-vfx-kind="${kind}" data-vfx-id="${escapeHtml(id)}"`;
    if (!mine || !mine.owned.includes(key)) {
      return `<span class="price free">Gratis</span><button class="btn btn-claim btn-sm" type="button" data-vfx-claim ${attrs}>Obtener</button>`;
    }
    if (mine[kind] === id) {
      return `<span class="price owned">Equipado</span><button class="btn btn-ghost btn-sm" type="button" data-vfx-remove ${attrs}>Quitar</button>`;
    }
    return `<span class="price owned">Tuyo</span><button class="btn btn-primary btn-sm" type="button" data-vfx-equip ${attrs}>Equipar</button>`;
  }

  // Lo seleccionado en el escenario: { kind: 'kill' | 'skill', id, pack }
  let vfxSel = null;
  const vfxCatOf = (k) => (vfxData.cats || []).find((c) => c.id === k.cat);
  const vfxPackOf = (id) => vfxData.packs.find((p) => p.id === id);
  const vfxColor = (c) => (HEX.test(c || '') ? c : '#45e9ff');

  // Lo que llevas puesto: el efecto de kill y el paquete de skills (con su miniatura)
  function vfxSlot(kind, label) {
    const id = vfxData.me?.[kind];
    const item = id ? (kind === 'kill' ? vfxData.kills : vfxData.packs).find((x) => x.id === id) : null;
    return `<div class="vfx-slot${item ? ' is-on' : ''}">
        <div class="vfx-slot-art">${item ? img(item.image, '') : '<span aria-hidden="true">—</span>'}</div>
        <div><span>${label}</span><b>${item ? escapeHtml(item.name) : 'Ninguno'}</b></div>
      </div>`;
  }

  // El escenario: la animación de lo elegido en grande, con su ficha y el botón
  function vfxStage() {
    let title, kicker, desc, preview, color, foot, extra = '';
    if (vfxSel?.kind === 'skill') {
      const pack = vfxPackOf(vfxSel.pack);
      const sk = pack.skills.find((x) => x.id === vfxSel.id) || pack.skills[0];
      title = sk.name;
      kicker = `Skill de ${pack.name}`;
      desc = sk.desc;
      preview = sk.preview || pack.image;
      color = vfxColor(pack.color);
      extra = `<div class="vfx-specs"><span class="vfx-trigger">${escapeHtml(triggerText(sk))}</span><span class="vfx-cd">Cooldown ${seconds(sk.cooldown)}</span></div>
        <p class="vfx-note">Se consigue con el paquete <b>${escapeHtml(pack.name)}</b> (${pack.skills.length} skills).</p>`;
      foot = vfxFoot('pack', pack.id);
    } else {
      const k = vfxData.kills.find((x) => x.id === vfxSel?.id) || vfxData.kills[0];
      const cat = vfxCatOf(k);
      title = k.name;
      kicker = `Efecto de kill${cat ? ` · ${cat.name}` : ''}`;
      desc = k.desc;
      preview = k.preview || k.image;
      color = vfxColor(cat?.color);
      extra = '<p class="vfx-note">Sale al derrotar a un jugador o a cualquier mob: la propia víctima hace la animación.</p>';
      foot = vfxFoot('kill', k.id);
    }
    return `
      <div class="vfx-stage panel" style="--vfx:${color}">
        <div class="vfx-screen">
          <img class="vfx-anim" src="${asset(preview)}" alt="" decoding="async">
          <span class="vfx-live" aria-hidden="true"><i></i>Vista previa</span>
        </div>
        <div class="vfx-info">
          <span class="cat">${escapeHtml(kicker)}</span>
          <h3>${escapeHtml(title)}</h3>
          <p>${escapeHtml(desc)}</p>
          ${extra}
          <div class="card-foot">${foot}</div>
          <div class="vfx-loadout">${vfxSlot('kill', 'Tu efecto de kill')}${vfxSlot('pack', 'Tu paquete de skills')}</div>
        </div>
      </div>`;
  }

  function vfxKillTile(k) {
    const sel = vfxSel?.kind !== 'skill' && (vfxSel?.id || vfxData.kills[0].id) === k.id;
    const on = vfxData.me?.kill === k.id;
    return `<button type="button" class="vfx-tile${sel ? ' is-sel' : ''}${on ? ' is-on' : ''}" data-vfx-pick="kill" data-id="${escapeHtml(k.id)}"
        data-anim="${escapeHtml(asset(k.preview || k.image))}" aria-pressed="${sel}">
        <span class="vfx-tile-art">${img(k.image, '')}</span>
        <span class="vfx-tile-name">${escapeHtml(k.name)}</span>${on ? '<span class="vfx-tag">Equipado</span>' : ''}
      </button>`;
  }

  function vfxPackBlock(p) {
    const on = vfxData.me?.pack === p.id;
    return `<section class="vfx-pack panel${on ? ' is-on' : ''}" style="--vfx:${vfxColor(p.color)}">
        <header class="vfx-pack-head">
          <span class="vfx-pack-art">${img(p.image, '')}</span>
          <div class="vfx-pack-text">
            <span class="cat">Paquete · ${p.skills.length} skills pasivas</span>
            <h3>${escapeHtml(p.name)}</h3>
            <p>${escapeHtml(p.desc)}</p>
          </div>
          <div class="vfx-pack-buy">${vfxFoot('pack', p.id)}</div>
        </header>
        <div class="vfx-skill-grid">${p.skills
          .map((sk) => {
            const sel = vfxSel?.kind === 'skill' && vfxSel.pack === p.id && vfxSel.id === sk.id;
            return `<button type="button" class="vfx-skill${sel ? ' is-sel' : ''}" data-vfx-pick="skill" data-pack="${escapeHtml(p.id)}" data-id="${escapeHtml(sk.id)}" aria-pressed="${sel}">
              <span class="vfx-skill-art">${sk.preview ? `<img src="${asset(sk.preview)}" alt="" loading="lazy" decoding="async">` : ''}</span>
              <span class="vfx-skill-text"><b>${escapeHtml(sk.name)}</b><span>${escapeHtml(triggerText(sk))} · ${seconds(sk.cooldown)}</span></span>
            </button>`;
          })
          .join('')}</div>
      </section>`;
  }

  function renderVfx(box) {
    if (!vfxData) {
      box.innerHTML = '<p class="loading">No se pudieron cargar los efectos. Recarga la página.</p>';
      return;
    }
    box.classList.add('vfx-shop');
    if (!vfxSel) vfxSel = vfxTab === 'packs' ? { kind: 'skill', pack: vfxData.packs[0].id, id: vfxData.packs[0].skills[0].id } : { kind: 'kill', id: vfxData.kills[0].id };
    const cats = vfxData.cats || [];
    const tab = (id, label, n) =>
      `<button type="button" role="tab" data-vfx-tab="${id}" aria-selected="${vfxTab === id}">${label}<span>${n}</span></button>`;
    let body;
    if (vfxTab === 'packs') {
      body = `<div class="vfx-packs">${vfxData.packs.map(vfxPackBlock).join('')}</div>`;
    } else {
      const shown = cats.filter((c) => vfxCat === 'todos' || c.id === vfxCat);
      body = `<div class="vfx-filter" role="group" aria-label="Categorías">${[{ id: 'todos', name: 'Todos' }, ...cats]
        .map(
          (c) =>
            `<button type="button" class="chip" data-vfx-cat="${escapeHtml(c.id)}" aria-pressed="${c.id === vfxCat}"${
              c.color && HEX.test(c.color) ? ` style="--vfx:${c.color}"` : ''
            }>${escapeHtml(c.name)}</button>`,
        )
        .join('')}</div>
      ${shown
        .map((c) => {
          const list = vfxData.kills.filter((k) => k.cat === c.id);
          if (!list.length) return '';
          return `<section class="vfx-group" style="--vfx:${vfxColor(c.color)}">
          <header><h3>${escapeHtml(c.name)}</h3><span>${list.length}</span></header>
          <div class="vfx-tiles">${list.map(vfxKillTile).join('')}</div>
        </section>`;
        })
        .join('')}`;
    }
    box.innerHTML = `
      <div class="vfx-hero">
        <div>
          <h2>Efectos visuales</h2>
          <p>Todo gratis. Se activan solos en el juego: los efectos de kill al derrotar a alguien y las skills al pelear.</p>
        </div>
      </div>
      <div id="vfx-stage">${vfxStage()}</div>
      <div class="vfx-tabs" role="tablist" aria-label="VFX">${tab('kills', 'Efectos de kill', vfxData.kills.length)}${tab(
        'packs',
        'Paquetes de skills',
        vfxData.packs.length,
      )}</div>
      ${body}`;
  }

  // Al pasar el ratón por una kill se anima su miniatura (solo en pantallas con ratón)
  document.addEventListener('pointerover', (e) => {
    const t = e.target.closest?.('.vfx-tile[data-anim]');
    if (!t || e.pointerType === 'touch') return;
    const im = t.querySelector('.vfx-tile-art img');
    if (im && !im.dataset.still) {
      im.dataset.still = im.src;
      im.src = t.dataset.anim;
    }
  });
  document.addEventListener('pointerout', (e) => {
    const t = e.target.closest?.('.vfx-tile[data-anim]');
    if (!t || t.contains(e.relatedTarget)) return;
    const im = t.querySelector('.vfx-tile-art img');
    if (im?.dataset.still) {
      im.src = im.dataset.still;
      delete im.dataset.still;
    }
  });

  document.addEventListener('click', async (e) => {
    const pick = e.target.closest('[data-vfx-pick]');
    if (pick) {
      vfxSel = pick.dataset.vfxPick === 'skill' ? { kind: 'skill', pack: pick.dataset.pack, id: pick.dataset.id } : { kind: 'kill', id: pick.dataset.id };
      $('#vfx-stage').innerHTML = vfxStage();
      $$('[data-vfx-pick]').forEach((b) => {
        const sel = b === pick;
        b.classList.toggle('is-sel', sel);
        b.setAttribute('aria-pressed', String(sel));
      });
      // En el móvil el escenario queda arriba: se sube para verlo
      const stage = $('#vfx-stage');
      if (stage.getBoundingClientRect().top < 0) stage.scrollIntoView({ behavior: 'smooth', block: 'start' });
      return;
    }
    const vt = e.target.closest('[data-vfx-tab], [data-vfx-cat]');
    if (vt) {
      if (vt.dataset.vfxTab && vt.dataset.vfxTab !== vfxTab) {
        vfxTab = vt.dataset.vfxTab;
        vfxSel = null;
      } else if (vt.dataset.vfxCat) vfxCat = vt.dataset.vfxCat;
      if (currentCategory === 'vfx') renderVfx($('#products'));
      return;
    }
    const btn = e.target.closest('[data-vfx-claim], [data-vfx-equip], [data-vfx-remove]');
    if (!btn) return;
    if (!me.user) {
      toast('Entra con tu cuenta para obtener efectos');
      setTimeout(() => (location.href = `/cuenta?return=${encodeURIComponent(location.pathname + location.hash)}`), 900);
      return;
    }
    const kind = btn.dataset.vfxKind;
    const id = btn.dataset.vfxId;
    btn.disabled = true;
    btn.classList.add('is-loading');
    try {
      const url = 'vfxClaim' in btn.dataset ? '/api/vfx/claim' : '/api/vfx/equip';
      const body = { kind, id: 'vfxRemove' in btn.dataset ? null : id };
      const { res, data } = await postWithTerms(url, body);
      if (res.ok) {
        vfxData.me = data.me;
        const list = kind === 'kill' ? vfxData.kills : vfxData.packs;
        const name = list.find((x) => x.id === id)?.name || '';
        toast('vfxRemove' in btn.dataset ? `Quitaste ${name}` : `${name} equipado: en el juego se activa solo`);
        if (currentCategory === 'vfx') renderVfx($('#products'));
        return;
      }
      toast(data.error || 'No se pudo guardar el cambio.');
    } catch {
      toast('No se pudo guardar el cambio.');
    }
    btn.disabled = false;
    btn.classList.remove('is-loading');
  });

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
            <span class="coin-spins-label"><img src="/img/coin.png" alt="" width="20" height="20" class="coin-img">O gira con las monedas del servidor</span>
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
      const { res, data } = await postWithTerms(
        spin ? '/api/roulette/coins' : '/api/coinshop/buy',
        spin ? { spins: Number(btn.dataset.coinSpin) } : { id: btn.dataset.coinBuy },
      );
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
      </div>
      ${
        items.length
          ? `<div class="coin-grid">${items
              .map(
                (it) => `<article class="panel coin-item">
                  <div class="coin-icon" data-letter="${escapeHtml(it.name.slice(0, 1).toUpperCase())}">${it.icon ? img(it.icon, '') : ''}</div>
                  <h3>${escapeHtml(it.name)}</h3>
                  <span class="muted small">× ${it.count}</span>
                  <span class="coin-price"><img src="/img/coin.png" alt="" width="20" height="20" class="coin-img">${fmt.format(it.price)}</span>
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

  // --- Escaparates 3D: el personaje con la skin del jugador y lo que se vende puesto (rangos y cosméticos) ---
  const stages = [];
  let wardrobeLib = null;
  const loadWear = (set) => (wearCache[set] ||= fetch(`/wear/${set}.json`).then((r) => r.json()));
  const skinName = () => storageGet('tf-skin') || me.user?.name || storageGet('tf-username') || '';
  function closeStages() {
    while (stages.length) stages.pop().destroy();
  }
  async function makeStage(canvas) {
    wardrobeLib ||= await import('/wardrobe.js');
    const w = wardrobeLib.createWardrobe(canvas);
    stages.push(w);
    return w;
  }
  // Campo «Skin de»: cambia la skin del escaparate al dejar de escribir
  function bindSkin(input, redraw) {
    let t = null;
    input.value = skinName();
    input.addEventListener('input', () => {
      clearTimeout(t);
      const v = input.value.trim();
      t = setTimeout(() => {
        if (!v || /^[A-Za-z0-9_]{3,16}$/.test(v)) {
          storageSet('tf-skin', v);
          redraw();
        }
      }, 600);
    });
  }
  // El nombre sigue a la cabeza y la sombra a los pies (variables CSS del escenario)
  function followAnchors(w, stage) {
    w.onAnchors(({ head, feet }) => {
      stage.style.setProperty('--head-x', `${head.x.toFixed(1)}px`);
      stage.style.setProperty('--head-y', `${head.y.toFixed(1)}px`);
      stage.style.setProperty('--feet-x', `${feet.x.toFixed(1)}px`);
      stage.style.setProperty('--feet-y', `${feet.y.toFixed(1)}px`);
      stage.classList.add('anchored');
    });
  }
  const ICON_CHECK = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m5 12.5 4.5 4.5L19 7.5"/></svg>';
  const rankVars = (p) => {
    const hex = p.rank?.hex || '#4ade80';
    const n = Number.parseInt(hex.slice(1), 16);
    return `--rk:${hex};--rk2:${p.colors?.[0] || hex};--rk3:rgba(${n >> 16}, ${(n >> 8) & 255}, ${n & 255}, 0.3)`;
  };

  // Rangos: la escalera de rangos arriba; el personaje con el set del rango puesto y lo que incluye al lado
  const rankShow = { current: null, wardrobe: null, token: 0, outfit: null };
  function renderRanks(box, focus) {
    const list = ranks();
    if (!list.length) {
      box.innerHTML = '<p class="loading">No hay rangos ahora mismo.</p>';
      return;
    }
    box.className = 'products rank-shop';
    box.innerHTML = `
      <nav class="rk-ladder" aria-label="Rangos, de menor a mayor">${list
        .map(
          (p) => `<button type="button" class="rk-step" data-rank="${escapeHtml(p.id)}" style="${rankVars(p)}" aria-pressed="false">
            <i class="rk-gem" aria-hidden="true"></i>
            <span class="rk-step-name">${escapeHtml(p.rank?.prefix || p.name)}</span>
            <span class="rk-step-price">${formatPrice(p.price)}</span>
          </button>`,
        )
        .join('')}</nav>
      <article class="rk-show" id="rk-show">
        <div class="rk-stage">
          <span class="rk-aura" aria-hidden="true"></span>
          <span class="rk-floor" aria-hidden="true"></span>
          <canvas aria-label="Tu personaje con el set del rango puesto"></canvas>
          <div class="rk-nametag" aria-hidden="true"><b class="rk-tag-prefix"></b><span class="rk-tag-name"></span></div>
          <p class="viewer-status" id="rk-status">Cargando…</p>
          <label class="cv-skin rk-skin"><span>Skin de</span><input id="rk-skin" maxlength="16" spellcheck="false" autocomplete="off" placeholder="Tu skin"></label>
          <span class="rk-drag">Arrastra para girar</span>
        </div>
        <div class="rk-info" id="rk-info"></div>
      </article>
      <section class="rk-cmds frame" id="rk-cmds" aria-live="polite"></section>`;
    $$('.rk-step', box).forEach((b) => b.addEventListener('click', () => showRank(b.dataset.rank, true)));
    makeStage($('.rk-stage canvas', box)).then((w) => {
      rankShow.wardrobe = w;
      followAnchors(w, $('.rk-stage', box));
      bindSkin($('#rk-skin', box), () => drawRank());
      drawRank();
    }).catch((err) => {
      console.error(err);
      $('#rk-status').textContent = 'No se pudo cargar el escaparate 3D en este navegador.';
    });
    const first = list.find((p) => p.id === focus) || list.find((p) => p.id === rankShow.current?.id) || list[list.length - 1];
    showRank(first.id);
  }

  function showRank(id, updateUrl) {
    const p = ranks().find((x) => x.id === id);
    const show = $('#rk-show');
    if (!p || !show) return;
    rankShow.current = p;
    const all = ranks();
    const n = all.indexOf(p) + 1;
    show.setAttribute('style', rankVars(p));
    for (const b of $$('.rk-step')) b.setAttribute('aria-pressed', String(b.dataset.rank === p.id));
    // En el móvil la escalera se desliza: el rango elegido queda a la vista (sin mover la página)
    const ladder = $('.rk-ladder');
    const step = $(`.rk-step[data-rank="${p.id}"]`);
    if (ladder && step && ladder.scrollWidth > ladder.clientWidth) {
      ladder.scrollTo({ left: step.offsetLeft - (ladder.clientWidth - step.offsetWidth) / 2, behavior: 'smooth' });
    }
    const prefix = p.rank?.prefix || p.name.replace(/^Rango\s+/i, '');
    const who = skinName() || 'Tú';
    $('.rk-tag-prefix', show).textContent = `[${prefix}]`;
    $('.rk-tag-name', show).textContent = who;
    const q = ownedQuote(p);
    const mine = q?.owned && me.user?.rank?.id === p.id;
    const buy = q?.owned
      ? `<div class="rk-price"><span class="rk-price-main">${mine ? 'Tu rango' : 'Incluido'}</span></div><button class="btn btn-ghost btn-lg" type="button" disabled>${mine ? ICON_CHECK + ' Ya lo tienes' : 'Ya tienes uno mayor'}</button>`
      : `<div class="rk-price">${q ? `<small>Mejora desde tu rango · antes ${formatPrice(p.price)}</small>` : '<small>Pago único · para siempre</small>'}<span class="rk-price-main">${formatPrice(q ? q.unit : p.price)}</span></div>
         <button class="btn btn-rank btn-lg" type="button" ${buyAttrs(p)}>${q ? 'Mejorar a ' : 'Conseguir '}${escapeHtml(prefix)}</button>`;
    $('#rk-info').innerHTML = `
      <div class="rk-anim">
        <span class="rk-tier">Rango ${n} de ${all.length}${n === all.length ? ' · el más alto' : ''}</span>
        <h2 class="rk-name">${escapeHtml(prefix)}</h2>
        <p class="rk-tagline">${escapeHtml(p.tagline || '')}</p>
        <p class="rk-pitch">${escapeHtml(p.description || '')}</p>
        <div class="rk-chat" aria-label="Así se ve tu nombre en el chat"><span class="rk-chat-prefix">[${escapeHtml(prefix)}]</span> <span class="rk-chat-name">${escapeHtml(who)}</span><span class="rk-chat-msg">: ¡Hola, reino!</span></div>
        <ul class="rk-perks">${(p.perks || []).map((x) => `<li>${ICON_CHECK}<span>${escapeHtml(x)}</span></li>`).join('')}
          <li>${ICON_CHECK}<span>Llega al instante a tu cuenta del servidor</span></li></ul>
        <div class="rk-try">
          <div class="rk-try-head"><span>Pruébate las piezas</span><button type="button" class="rk-reset" data-rk-reset>Set completo</button></div>
          <div class="rk-pieces" role="group" aria-label="Piezas del set: toca una para ponértela">${(p.models || [])
            .map(
              (m) => `<button type="button" class="rk-piece" data-rk-piece="${escapeHtml(m.id)}" aria-pressed="false" title="${escapeHtml(m.name)}">
                ${img(thumbOf(p.set, m.id), m.name, '', ' width="44" height="44"')}<i class="worn-dot" aria-hidden="true"></i></button>`,
            )
            .join('')}</div>
        </div>
        <div class="rk-buy">${buy}</div>
      </div>`;
    // Debajo del escaparate: lo que trae el rango en el servidor, con sus comandos (lo que no tenía el anterior, marcado)
    const prev = all[n - 2];
    const prevName = prev ? prev.rank?.prefix || prev.name.replace(/^Rango\s+/i, '') : '';
    const cmds = $('#rk-cmds');
    cmds.setAttribute('style', rankVars(p));
    cmds.innerHTML = `
      <div class="rk-cmds-head">
        <h3>Lo que trae <span class="rk-cmds-name">${escapeHtml(prefix)}</span> en el servidor</h3>
        <p>${prev ? `Lo marcado como <b>nuevo</b> no lo tiene ${escapeHtml(prevName)}.` : 'Tu primer rango del reino.'} Se activa solo al entrar al servidor.</p>
      </div>
      <ul class="rk-cmd-list">${(p.serverPerks || [])
        .map(
          (x) => `<li class="${x.new && prev ? 'is-new' : ''}">
            ${x.cmd ? `<code>${escapeHtml(x.cmd)}</code>` : `<span class="rk-cmd-ico">${ICON_CHECK}</span>`}
            <span class="rk-cmd-text">${escapeHtml(x.text)}</span>${x.new && prev ? '<b class="rk-new">Nuevo</b>' : ''}
          </li>`,
        )
        .join('')}</ul>`;
    if (updateUrl) history.replaceState(null, '', `#${p.id.replace(/^rango-/, 'rangos-')}`);
    rankShow.outfit = null;
    $$('[data-rk-piece]').forEach((b) => b.addEventListener('click', () => equipRankPiece(b.dataset.rkPiece)));
    $('[data-rk-reset]').addEventListener('click', () => {
      rankShow.outfit = null;
      drawRank();
    });
    drawRank();
  }

  // Tocar una pieza la pone en su hueco (o la quita si ya estaba puesta)
  async function equipRankPiece(slug) {
    const p = rankShow.current;
    if (!p || !wardrobeLib) return;
    const set = await loadWear(p.set);
    const it = set.items[slug];
    if (!it) return;
    rankShow.outfit ||= wardrobeLib.defaultOutfit(set);
    const slot = it.type === 'armor' ? it.slot : wardrobeLib.slotOf(it);
    if (!slot) return;
    if (rankShow.outfit[slot] === slug) delete rankShow.outfit[slot];
    else rankShow.outfit[slot] = slug;
    drawRank();
  }

  async function drawRank() {
    const p = rankShow.current;
    const w = rankShow.wardrobe;
    if (!p || !w) return;
    const token = ++rankShow.token;
    const status = $('#rk-status');
    status.hidden = false;
    status.textContent = 'Poniéndote el set…';
    try {
      const set = await loadWear(p.set);
      if (token !== rankShow.token) return;
      const outfit = rankShow.outfit || wardrobeLib.defaultOutfit(set);
      const worn = new Set(Object.values(outfit));
      for (const b of $$('[data-rk-piece]')) {
        b.classList.toggle('worn', worn.has(b.dataset.rkPiece));
        b.setAttribute('aria-pressed', String(worn.has(b.dataset.rkPiece)));
      }
      await w.showPlayer(set, outfit, skinName());
      if (token === rankShow.token) status.hidden = true;
    } catch (err) {
      console.error(err);
      if (token === rankShow.token) status.textContent = 'No se pudo cargar el set.';
    }
  }

  // Cosméticos: el personaje con los cosméticos puestos; se combinan piezas de cualquier colección
  const SLOT_OF = { head: 'helmet', back: 'back', held: 'hand', balloon: 'offhand' };
  const SLOT_LABEL = { head: 'Cabeza', back: 'Espalda', held: 'En la mano', balloon: 'Globo' };
  const cosShow = { wardrobe: null, outfit: {}, selected: null, filter: 'Todos', token: 0 };
  const cosmetics = () => products.filter((p) => p.category === 'cosmeticos');
  function renderCosmetics(box) {
    const list = cosmetics();
    if (!list.length) {
      box.innerHTML = '<p class="loading">No hay cosméticos ahora mismo.</p>';
      return;
    }
    const collections = ['Todos', ...new Set(list.map((p) => p.collection))];
    const themes = [];
    for (const p of list) {
      const key = `${p.collection}·${p.theme}`;
      let t = themes.find((x) => x.key === key);
      if (!t) themes.push((t = { key, name: p.theme, collection: p.collection, items: [] }));
      t.items.push(p);
    }
    box.className = 'products cos-shop';
    box.innerHTML = `
      <div class="cos-layout">
        <div class="cos-stage-col">
          <div class="cos-stage">
            <span class="rk-aura" aria-hidden="true"></span>
            <span class="rk-floor" aria-hidden="true"></span>
            <canvas aria-label="Tu personaje con los cosméticos puestos"></canvas>
            <p class="viewer-status" id="cos-status">Cargando…</p>
            <label class="cv-skin rk-skin"><span>Skin de</span><input id="cos-skin" maxlength="16" spellcheck="false" autocomplete="off" placeholder="Tu skin"></label>
            <span class="rk-drag">Arrastra para girar</span>
          </div>
          <div class="cos-buy" id="cos-buy"></div>
        </div>
        <div class="cos-side">
          <div class="cos-head">
            <h2>Cosméticos</h2>
            <p class="muted">Sombreros, mochilas, alas, globos y objetos de mano con animación. Solo cambian tu aspecto. Toca una pieza para probártela y combina las que quieras.</p>
            <div class="cos-filters" role="group" aria-label="Colecciones">${collections
              .map((c) => `<button type="button" class="chip" data-cos-filter="${escapeHtml(c)}" aria-pressed="${c === 'Todos'}">${escapeHtml(c)}</button>`)
              .join('')}</div>
          </div>
          <div class="cos-themes">${themes
            .map(
              (t) => `<section class="cos-theme" data-collection="${escapeHtml(t.collection)}" style="${themeVarsCss(t.items[0])}">
                <header><div><h3>${escapeHtml(t.name)}</h3><span class="muted small">${escapeHtml(t.collection)} · ${t.items.length} piezas</span></div>
                  <button type="button" class="btn btn-ghost btn-sm" data-cos-theme="${escapeHtml(t.key)}">Probar conjunto</button></header>
                <div class="cos-pieces">${t.items
                  .map(
                    (p) => `<button type="button" class="cos-piece" data-cos="${escapeHtml(p.id)}" aria-pressed="false">
                      <span class="cos-art">${img(p.image, '', '', ' width="72" height="72"')}<i class="worn-dot" aria-hidden="true"></i></span>
                      <span class="cos-name">${escapeHtml(p.name)}</span>
                      <span class="cos-meta"><span>${SLOT_LABEL[p.slot] || ''}</span><b>${formatPrice(p.price)}</b></span>
                    </button>`,
                  )
                  .join('')}</div>
              </section>`,
            )
            .join('')}</div>
        </div>
      </div>`;
    cosShow.themes = themes;
    $$('[data-cos-filter]', box).forEach((b) =>
      b.addEventListener('click', () => {
        cosShow.filter = b.dataset.cosFilter;
        $$('[data-cos-filter]', box).forEach((x) => x.setAttribute('aria-pressed', String(x === b)));
        $$('.cos-theme', box).forEach((t) => (t.hidden = cosShow.filter !== 'Todos' && t.dataset.collection !== cosShow.filter));
      }),
    );
    $$('[data-cos]', box).forEach((b) => b.addEventListener('click', () => pickCosmetic(b.dataset.cos)));
    $$('[data-cos-theme]', box).forEach((b) => b.addEventListener('click', () => wearTheme(b.dataset.cosTheme)));
    makeStage($('.cos-stage canvas', box)).then((w) => {
      cosShow.wardrobe = w;
      followAnchors(w, $('.cos-stage', box));
      bindSkin($('#cos-skin', box), () => drawCosmetics());
      wearTheme(themes[0].key);
    }).catch((err) => {
      console.error(err);
      $('#cos-status').textContent = 'No se pudo cargar el escaparate 3D en este navegador.';
    });
  }
  const themeVarsCss = (p) => Object.entries(themeVars(p) || {}).map(([k, v]) => `${k}:${v}`).join(';');

  function wearTheme(key) {
    const t = cosShow.themes.find((x) => x.key === key);
    if (!t) return;
    cosShow.outfit = {};
    for (const p of t.items) cosShow.outfit[SLOT_OF[p.slot]] = p.id;
    cosShow.selected = t.items[0].id;
    refreshCosmetics();
    drawCosmetics();
  }

  function pickCosmetic(id) {
    const p = products.find((x) => x.id === id);
    if (!p) return;
    const slot = SLOT_OF[p.slot];
    // Tocar la pieza que ya está seleccionada y puesta la quita
    if (cosShow.selected === id && cosShow.outfit[slot] === id) delete cosShow.outfit[slot];
    else cosShow.outfit[slot] = id;
    cosShow.selected = id;
    refreshCosmetics();
    drawCosmetics();
  }

  function refreshCosmetics() {
    const worn = new Set(Object.values(cosShow.outfit));
    for (const b of $$('[data-cos]')) {
      b.setAttribute('aria-pressed', String(b.dataset.cos === cosShow.selected));
      b.classList.toggle('worn', worn.has(b.dataset.cos));
    }
    const p = products.find((x) => x.id === cosShow.selected);
    const box = $('#cos-buy');
    if (!p || !box) return;
    // El color del escenario y de la compra es el de la colección elegida
    box.parentElement.setAttribute('style', themeVarsCss(p));
    box.innerHTML = `
      <span class="cos-buy-art">${img(p.image, '', '', ' width="52" height="52"')}</span>
      <span class="cos-buy-text"><small>${escapeHtml(p.theme)} · ${SLOT_LABEL[p.slot] || ''}</small><b>${escapeHtml(p.name)}</b></span>
      <button class="btn btn-buy" type="button" ${buyAttrs(p)}>Comprar · ${formatPrice(p.price)}</button>`;
  }

  async function drawCosmetics() {
    const w = cosShow.wardrobe;
    if (!w) return;
    const token = ++cosShow.token;
    const status = $('#cos-status');
    status.hidden = false;
    status.textContent = 'Poniéndote los cosméticos…';
    try {
      // Un «set» con las piezas puestas, aunque sean de colecciones distintas
      const items = {};
      const outfit = {};
      for (const [slot, id] of Object.entries(cosShow.outfit)) {
        const p = products.find((x) => x.id === id);
        if (!p) continue;
        const set = await loadWear(p.set);
        if (!set.items[p.item]) continue;
        items[id] = set.items[p.item];
        outfit[slot] = id;
      }
      if (token !== cosShow.token) return;
      await w.showPlayer({ items, armor: {} }, outfit, skinName());
      if (token === cosShow.token) status.hidden = true;
    } catch (err) {
      console.error(err);
      if (token === cosShow.token) status.textContent = 'No se pudo cargar el cosmético.';
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
        <p class="cv-bound">${ICON_LOCK}<span>Permanente e irrompible, vinculado a tu cuenta: solo tú puedes usarlo. Los cosméticos (alas, sombreros, mochilas) sí se pueden intercambiar.</span></p>
        <div class="cv-buy">
          <span class="price"><small>Set completo</small><span id="cv-price"></span></span>
          <button class="btn btn-buy btn-lg" type="button" id="cv-buy">Comprar crate</button>
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

      ${termsLabel('pay-terms')}
      <div class="pay-actions">
        <button type="submit" class="btn btn-buy btn-lg btn-block pay-btn" id="pay-btn" disabled>Pagar con tarjeta</button>
        <button type="button" class="btn btn-paypal btn-lg btn-block pay-btn" id="pay-paypal" hidden disabled>Pagar con <b class="pp-word">Pay<i>Pal</i></b></button>
      </div>
      <p class="pay-note">${ICON_LOCK}<span id="pay-note-text">Pago seguro con <b>Stripe</b>: tarjeta, Apple Pay o Google Pay. Nunca vemos tus datos bancarios.</span></p>
      <p class="pay-legal">Precio final en USD. Tienes 5 días hábiles para cancelar la compra.
        Tierras Fantásticas es un servidor independiente: no es un producto oficial de Minecraft ni está asociado con Mojang o Microsoft.</p>
    </form>
  </dialog>`;

  const sells = ['tienda', 'crates'].includes(page);
  let dialog, qtyInput;
  if (sells) {
    document.body.insertAdjacentHTML('beforeend', DIALOG_HTML);
    dialog = $('#checkout-dialog');
    qtyInput = $('#quantity');
    $('#pay-terms').addEventListener('change', () => setPayable());
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
    const off = !player || blocked || paying || !$('#pay-terms').checked;
    $('#pay-btn').disabled = off;
    $('#pay-paypal').disabled = off;
  }

  // Botones de pago según lo que esté conectado en el servidor (tarjeta con Stripe y/o PayPal).
  function renderPayMethods() {
    const card = config.cardEnabled !== false;
    const pp = Boolean(config.paypalEnabled);
    $('#pay-btn').hidden = !card;
    $('#pay-paypal').hidden = !pp;
    $('#pay-note-text').innerHTML = card && pp
      ? 'Pago seguro con <b>Stripe</b> (tarjeta, Apple Pay o Google Pay) o con <b>PayPal</b>. Nunca vemos tus datos bancarios.'
      : pp
        ? 'Pago seguro con <b>PayPal</b> (tu cuenta o tarjeta). Nunca vemos tus datos bancarios.'
        : 'Pago seguro con <b>Stripe</b>: tarjeta, Apple Pay o Google Pay. Nunca vemos tus datos bancarios.';
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
    $('#pay-terms').checked = false;
    $('#pay-btn').textContent = 'Pagar con tarjeta';
    $('#pay-btn').classList.remove('is-loading');
    $('#pay-paypal').classList.remove('is-loading');
    renderPayMethods();
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
        const coins = Number.isFinite(me.user.coins) ? me.user.coins : null;
        chip.innerHTML = `<img src="${escapeHtml(me.user.head)}" alt="" width="26" height="26" class="pixel-img"><span>${escapeHtml(me.user.name)}</span>${
          coins === null
            ? ''
            : `<span class="chip-coins" title="Tus monedas del servidor"><img src="/img/coin.png" alt="" width="18" height="18" class="coin-img">${new Intl.NumberFormat('es-ES').format(coins)}</span>`
        }`;
        chip.setAttribute('aria-label', `Mi cuenta (${me.user.name}${coins === null ? '' : `, ${coins} monedas`})`);
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
      pay('stripe');
    });
    $('#pay-paypal').addEventListener('click', () => pay('paypal'));
  }

  // El servidor crea el pago con el precio del catálogo y nos manda a la página segura de Stripe o de PayPal.
  async function pay(method) {
    if (paying || !player || player.quote?.blocked) return;
    if (!$('#pay-terms').checked) return showError('Marca la casilla para aceptar los Términos y el Aviso de privacidad.');
    const quantity = Number.parseInt(qtyInput.value, 10) || 1;
    const max = selected.maxQuantity || 10;
    if (quantity < 1 || quantity > max) return showError(`La cantidad debe estar entre 1 y ${max}.`);
    showError('');
    paying = true;
    setPayable();
    const btn = method === 'paypal' ? $('#pay-paypal') : $('#pay-btn');
    const label = btn.innerHTML;
    btn.textContent = method === 'paypal' ? 'Abriendo PayPal…' : 'Abriendo el pago seguro…';
    btn.classList.add('is-loading');
    try {
      const res = await fetch('/api/checkout', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ productId: selected.id, username: player.name, uuid: player.uuid, quantity, acceptTerms: true, method }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok || !data.url) throw new Error(data.error || 'No se pudo iniciar el pago.');
      storageSet('tf-username', player.name);
      window.location.href = data.url;
    } catch (err) {
      paying = false;
      btn.innerHTML = label;
      btn.classList.remove('is-loading');
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
          <div id="auth-terms">${termsLabel('auth-terms-check')}</div>
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
    $('#auth-terms').hidden = !register;
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
      if (!$('#auth-terms-check').checked) return error('Marca la casilla para aceptar los Términos y el Aviso de privacidad.');
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
        body: JSON.stringify({ name, password, acceptTerms: authTab === 'register' && $('#auth-terms-check').checked }),
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
          ${termsStatus(u)}
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
        <div class="orders-head"><span class="cat">Mis compras</span><a class="btn btn-buy btn-sm" href="/tienda">Ir a la tienda</a></div>
        <div id="orders"><p class="muted">Cargando…</p></div>
      </section>`;

    $('#terms-review')?.addEventListener('click', async () => {
      if (await askTerms()) {
        renderAccount();
        toast('Términos aceptados');
      }
    });
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

  // Qué versión de los Términos aceptó la cuenta y cuándo (o el aviso para aceptar la vigente).
  function termsStatus(u) {
    if (!u.termsOk) {
      return `<div class="terms-status warn"><span>Actualizamos los Términos y condiciones. Acéptalos para reclamar
        recompensas y comprar con tu cuenta.</span><button class="btn btn-primary btn-sm" type="button" id="terms-review">Revisar y aceptar</button></div>`;
    }
    const at = u.termsAt ? new Date(u.termsAt).toLocaleDateString('es-MX', { day: 'numeric', month: 'long', year: 'numeric' }) : '';
    return `<p class="terms-status ok">✓ Aceptaste los <a href="/terminos">Términos</a> y el <a href="/privacidad">Aviso de
      privacidad</a>${at ? ` el ${escapeHtml(at)}` : ''}.</p>`;
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
