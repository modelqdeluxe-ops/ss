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
    awaiting_payment: 'Esperando confirmación de PayPal',
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

  if (!orderId) {
    show('bad', 'Sin pedido', 'Pedido no encontrado', 'No hemos encontrado ningún pedido asociado a esta página.');
    return;
  }
  $('d-order').textContent = orderId;

  // Si PayPal aún está confirmando el pago, volvemos a consultar durante un rato.
  for (let attempt = 0; attempt < 15; attempt++) {
    let order;
    try {
      const res = await fetch(`/api/order/${encodeURIComponent(orderId)}`);
      order = await res.json();
      if (!res.ok) throw new Error(order.error);
    } catch {
      show('bad', 'Sin datos', 'No pudimos comprobar el pedido',
        'Si se realizó el cargo, recibirás tu compra igualmente. Escríbenos por Discord con tu número de pedido.');
      return;
    }

    const total = new Intl.NumberFormat('en-US', { style: 'currency', currency: order.currency }).format(order.amount / 100);
    $('d-user').textContent = order.username;
    $('d-product').textContent = order.quantity > 1 ? `${order.product} ×${order.quantity}` : order.product;
    $('d-total').textContent = total;
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

    if (PAID.includes(order.status)) {
      const paidText = {
        delivered: 'Tu pago se ha completado y la compra ya está en el servidor. ¡Disfrútala!',
        queued: `Tu pago se ha completado. La compra llegará a ${order.username} en cuanto entre al servidor (si ya está dentro, en unos segundos).`,
      };
      show('ok', 'Pagado', '¡Gracias por tu compra!', paidText[order.status] || 'Tu pago se ha completado. El staff entregará tu compra en breve.');
      return;
    }
    if (order.status === 'failed') {
      show('bad', 'No completado', 'El pago no se completó', 'No se ha realizado ningún cargo. Puedes volver a la tienda e intentarlo de nuevo.');
      return;
    }
    if (order.status === 'error') {
      show('bad', 'En revisión', 'Pedido en revisión', 'Hubo un problema al verificar el pago. Escríbenos por Discord con tu número de pedido.');
      return;
    }
    show('wait', 'Pendiente', 'Pago en proceso', 'PayPal aún está confirmando el pago. Te entregaremos la compra en cuanto se complete; puedes cerrar esta página.');
    await new Promise((r) => setTimeout(r, 4000));
  }
})();
