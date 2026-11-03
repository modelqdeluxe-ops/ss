// Página de confirmación de compra: consulta el estado del pedido hasta que se resuelve.
(async () => {
  const $ = (id) => document.getElementById(id);
  const orderId = new URLSearchParams(location.search).get('order');
  const deliveryText = {
    delivered: 'Entregado en el servidor',
    queued: 'Se entrega al entrar al servidor',
    delivering: 'Entregando…',
    pending: 'Procesando…',
    manual: 'El staff lo entregará en breve',
    delivery_failed: 'El staff lo entregará en breve',
    awaiting_payment: 'Esperando que el banco confirme el cobro',
    expired: 'Pago caducado',
    failed: 'Pago no completado',
    error: 'En revisión',
  };
  const PAID = ['delivered', 'queued', 'delivering', 'manual', 'delivery_failed'];

  function show(kind, label, title, message) {
    $('state').dataset.kind = kind;
    $('state').textContent = label;
    $('title').textContent = title;
    $('message').textContent = message;
  }

  function receipt(order) {
    const link = $('receipt-pdf');
    link.href = `/api/order/${encodeURIComponent(order.id || orderId)}/receipt`;
    link.download = `recibo-${order.id || orderId}.pdf`;
    link.hidden = false;
    link.nextElementSibling.classList.replace('btn-primary', 'btn-ghost');
    const key = `tf-recibo-${order.id || orderId}`;
    try {
      if (localStorage.getItem(key)) return;
      localStorage.setItem(key, '1');
    } catch {
      // Sin almacenamiento: se descarga igualmente
    }
    link.click();
  }

  if (!orderId) {
    show('bad', 'Sin pedido', 'Pedido no encontrado', 'No hemos encontrado ningún pedido asociado a esta página.');
    return;
  }
  $('d-order').textContent = orderId;

  // Si Stripe o PayPal aún no nos han avisado del pago (o hay que cobrar el de PayPal), se lo preguntamos y volvemos a mirar durante un rato.
  for (let attempt = 0, coinWaits = 0; attempt < 15 && coinWaits < 150; attempt++, coinWaits++) {
    let order;
    try {
      const res = await fetch(`/api/order/${encodeURIComponent(orderId)}/sync`, { method: 'POST' });
      order = await res.json();
      if (!res.ok) throw new Error(order.error);
    } catch {
      show('bad', 'Sin datos', 'No pudimos comprobar el pedido',
        'Si se realizó el cargo, recibirás tu compra igualmente. Escríbenos por Discord con tu número de pedido.');
      return;
    }

    const total = order.coins
      ? `${new Intl.NumberFormat('es-ES', { useGrouping: 'always' }).format(order.coins)} monedas del servidor`
      : order.amount === 0
        ? 'Gratis'
        : new Intl.NumberFormat('en-US', { style: 'currency', currency: order.currency }).format(order.amount / 100);
    $('d-user').textContent = order.gift ? `${order.username} · regalo de ${order.gift.from}` : order.username;
    if (order.cart && Array.isArray(order.items)) {
      // (3 nov 2026) el carrito: todos sus productos, pagados en un solo pago (y quien paga, si hay algo para otros)
      $('d-product-label').textContent = 'Productos';
      if (order.items.some((it) => it.username && it.username !== order.username)) $('d-user-label').textContent = 'Paga';
      $('d-product').replaceChildren(
        ...order.items.map((it) => {
          const line = document.createElement('span');
          line.className = 'receipt-line';
          // Para quién es cada uno: los regalos dicen a quién van
          const whom = it.gift ? ` · regalo para ${it.username}` : it.username && it.username !== order.username ? ` · para ${it.username}` : '';
          line.textContent = `${it.product}${it.quantity > 1 ? ` ×${it.quantity}` : ''}${it.upgradeFrom ? ` (mejora desde ${it.upgradeFrom})` : ''}${whom}`;
          return line;
        }),
      );
    } else {
      $('d-product').textContent = (order.quantity > 1 ? `${order.product} ×${order.quantity}` : order.product) + (order.upgradeFrom ? ` (mejora desde ${order.upgradeFrom})` : '');
    }
    const usd = (c) => new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(c / 100);
    $('d-total').textContent = order.coupon?.discount ? `${total} · cupón ${order.coupon.code} (−${usd(order.coupon.discount)})` : total;
    // Pagado: el carrito de este navegador ya no hace falta
    if (order.cart && PAID.includes(order.status)) {
      try {
        localStorage.removeItem('tf-cart');
      } catch {
        // sin almacenamiento
      }
      window.dispatchEvent(new Event('tf-cart-changed'));
    }
    $('d-delivery').textContent = deliveryText[order.status] || order.status;
    $('details').hidden = false;
    if (order.discord) {
      const discordText = {
        granted: `Rol dado a ${order.discord.username}`,
        joined: `${order.discord.username} añadido al Discord con su rol`,
        failed: 'El staff te dará el rol en breve',
      };
      $('d-discord').textContent = discordText[order.discord.status] || `Vinculado: ${order.discord.username}`;
      $('d-discord').hidden = $('d-discord-label').hidden = false;
    }

    // Ruleta: las armas que salieron al confirmarse el pago
    if (order.prizes?.length) {
      const list = $('prize-list');
      list.replaceChildren(
        ...order.prizes.map((p) => {
          const li = document.createElement('li');
          if (p.thumb) {
            const img = document.createElement('img');
            img.src = p.thumb;
            img.alt = '';
            img.width = img.height = 56;
            li.append(img);
          }
          const span = document.createElement('span');
          span.textContent = p.name;
          li.append(span);
          return li;
        }),
      );
      $('prizes').hidden = false;
    }

    // Con monedas (ruleta, tienda de monedas o una clase de skills): se cobra en el juego; esperamos a que el servidor responda.
    if (order.coins) {
      const shop = order.kind === 'tienda';
      const skill = order.kind === 'skills';
      if (skill) {
        if (order.status === 'delivered') {
          show('ok', 'Hecho', '¡Clase conseguida!', `Se cobraron tus monedas en el servidor. ${order.product} ya es tuya y es tu clase activa: tienes sus skills en las teclas de la barra.`);
          return;
        }
        if (order.status === 'delivery_failed') {
          show('bad', 'Sin comprar', 'No se pudo comprar', order.error || 'No tenías bastantes monedas en el servidor. No se ha cobrado nada.');
          return;
        }
        show('wait', 'En cola', 'Esperando al servidor', `Se cobrará en el juego en cuanto ${order.username} esté conectado; después la clase será tuya y pasará a ser tu clase activa.`);
        await new Promise((r) => setTimeout(r, 4000));
        attempt = Math.min(attempt, 10);
        continue;
      }
      if (order.status === 'delivered') {
        if (shop) show('ok', 'Hecho', '¡Compra hecha!', `Se cobraron tus monedas en el servidor y ${order.product} ya está en tu inventario.`);
        else show('ok', 'Hecho', '¡La ruleta ha girado!', 'Se cobraron tus monedas en el servidor y los premios ya están en tu inventario.');
        return;
      }
      if (order.status === 'delivery_failed') {
        show('bad', shop ? 'Sin comprar' : 'Sin girar', shop ? 'No se pudo comprar' : 'No se pudo girar',
          order.error || 'No tenías bastantes monedas en el servidor. No se ha cobrado nada.');
        return;
      }
      show('wait', 'En cola', 'Esperando al servidor', shop
        ? `Se cobrará y te llegará en el juego en cuanto ${order.username} esté conectado.`
        : `La ruleta girará en el juego en cuanto ${order.username} esté conectado. Puedes dejar esta página abierta para ver qué te toca.`);
      await new Promise((r) => setTimeout(r, 4000));
      attempt = Math.min(attempt, 10);
      continue;
    }

    // Recompensa gratis: no hubo pago
    if (PAID.includes(order.status) && !order.amount) {
      show('ok', 'Reclamada', '¡Recompensa reclamada!', order.status === 'delivered'
        ? 'Tu recompensa ya está en el servidor. ¡Disfrútala!'
        : `Tu recompensa llegará a ${order.username} en cuanto entre al servidor (si ya está dentro, en unos segundos).`);
      return;
    }
    // Pagado con dinero: el recibo en PDF (además del de Stripe o PayPal) se descarga solo una vez y queda el botón
    if (['queued', 'delivered', 'delivery_failed'].includes(order.status) && order.amount > 0) receipt(order);
    if (PAID.includes(order.status) && order.kind === 'skills') {
      show('ok', 'Pagado', '¡Gracias por tu compra!', `Tu pago se ha completado. ${order.product} ya es de ${order.username} para siempre y es su clase activa: la tendrá en el servidor en unos segundos si está conectado o al entrar. La clase activa se cambia en la tienda, en Skills.`);
      return;
    }
    // (3 nov 2026) Carrito con regalos o para varios jugadores: a cada uno le llega lo suyo
    const forOthers = order.cart && Array.isArray(order.items) ? [...new Set(order.items.filter((it) => it.username && it.username !== order.username).map((it) => it.username))] : [];
    if (PAID.includes(order.status) && forOthers.length) {
      const join = (names) => (names.length > 1 ? `${names.slice(0, -1).join(', ')} y ${names.at(-1)}` : names[0]);
      const list = join(forOthers);
      const all = join([...forOthers, ...(order.items.some((it) => it.username === order.username) ? ['tú'] : [])]);
      const gifts = order.items.some((it) => it.gift);
      show('ok', 'Pagado', gifts ? '¡Regalos enviados!' : '¡Gracias por tu compra!', order.status === 'delivered'
        ? `Todo está entregado en el servidor: ${list} ya ${forOthers.length > 1 ? 'tienen' : 'tiene'} lo suyo${gifts ? ', con un aviso de que es tu regalo' : ''}.`
        : `Tu pago se ha completado. Cada uno (${all}) recibirá lo suyo en cuanto entre al servidor (si ya está dentro, en unos segundos)${gifts ? '; los regalos llegan con tu nombre y tu mensaje' : ''}.`);
      return;
    }
    if (PAID.includes(order.status) && order.gift) {
      show('ok', 'Pagado', '¡Regalo enviado!', order.status === 'delivered'
        ? `${order.username} ya tiene tu regalo en el servidor, con un aviso de que es tuyo.`
        : `Tu pago se ha completado. ${order.username} recibirá tu regalo en cuanto entre al servidor (si ya está dentro, en unos segundos), con un aviso de que es tuyo${order.gift.message ? ' y tu mensaje' : ''}.`);
      return;
    }
    if (PAID.includes(order.status)) {
      const paidText = {
        delivered: 'Tu pago se ha completado y la compra ya está en el servidor. ¡Disfrútala!',
        queued: `Tu pago se ha completado. La compra llegará a ${order.username} en cuanto entre al servidor (si ya está dentro, en unos segundos) y todo el servidor lo sabrá.`,
      };
      show('ok', 'Pagado', '¡Gracias por tu compra!', paidText[order.status] || 'Tu pago se ha completado. El staff entregará tu compra en breve.');
      return;
    }
    if (order.status === 'failed' || order.status === 'expired') {
      show('bad', 'No completado', 'El pago no se completó', 'No se ha realizado ningún cargo. Puedes volver a la tienda e intentarlo de nuevo.');
      return;
    }
    if (order.status === 'error') {
      show('bad', 'En revisión', 'Pedido en revisión', 'Hubo un problema al verificar el pago. Escríbenos a tierrasfantasticasmc@gmail.com o por Discord con tu número de pedido.');
      return;
    }
    const waitText = order.status === 'awaiting_payment'
      ? 'Tu banco aún está confirmando el cobro. Te entregaremos la compra en cuanto se complete; puedes cerrar esta página.'
      : 'Estamos confirmando el pago. Si ya pagaste, tu compra llegará igualmente; puedes cerrar esta página.';
    show('wait', 'Pendiente', 'Pago en proceso', waitText);
    await new Promise((r) => setTimeout(r, 4000));
  }
})();
