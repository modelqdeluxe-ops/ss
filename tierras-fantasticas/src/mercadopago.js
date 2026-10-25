// Cliente mínimo de la API de Mercado Pago (Checkout Pro), sin dependencias, para Cloudflare Workers.
// Documentación: https://www.mercadopago.com.mx/developers/es/docs/checkout-pro/landing
//
// (2026-10-26, el dueño: «conecta Mercado Pago; la cuenta es de México») El comprador paga en la página de Mercado Pago
// (tarjeta, OXXO, saldo…) y vuelve a /success; la web pregunta a la API por los pagos del pedido (external_reference) y
// Mercado Pago también avisa con un webhook. Lo que cuenta es siempre lo que dice la API con nuestro token: un aviso
// falso no puede dar nada.

const API = 'https://api.mercadopago.com';

/**
 * (2026-10-26, el dueño: «cobramos en dólares») Los precios son en dólares; una cuenta de Mercado Pago de México solo
 * cobra en pesos, así que se cobra lo mismo en pesos al cambio del día: centavos de peso, redondeados hacia arriba
 * ($4.99 a 18.68 → $93.22 MXN).
 */
export function mxnCents(usdCents, rate) {
  return Math.ceil(usdCents * rate - 1e-9);
}

let rateCache = null;

/**
 * Pesos por dólar de hoy: el de Mercado Pago (con nuestro token) o, si no contesta, el de open.er-api.com; se guarda
 * 6 h. Si no hay ninguno, fallback (MP_RATE de Cloudflare o 18.5).
 */
export async function usdToMxn({ token, fallback = 18.5, apiBase } = {}) {
  if (rateCache && Date.now() - rateCache.at < 6 * 3600_000) return rateCache.rate;
  let rate = null;
  try {
    const res = await fetch(`${apiBase || 'https://api.mercadolibre.com'}/currency_conversions/search?from=USD&to=MXN`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    const data = await res.json().catch(() => ({}));
    if (res.ok && Number(data.ratio) > 1) rate = Number(data.ratio);
  } catch {
    /* sin respuesta */
  }
  if (!rate) {
    try {
      const res = await fetch('https://open.er-api.com/v6/latest/USD');
      const data = await res.json().catch(() => ({}));
      if (res.ok && Number(data.rates?.MXN) > 1) rate = Number(data.rates.MXN);
    } catch {
      /* sin respuesta */
    }
  }
  if (!rate) return fallback;
  rateCache = { rate, at: Date.now() };
  return rate;
}

/** Para las pruebas. */
export function resetRateCache() {
  rateCache = null;
}

export class MercadoPago {
  constructor({ accessToken, apiBase }) {
    this.token = accessToken;
    this.base = apiBase || API;
  }

  async request(method, path, body, headers = {}) {
    const res = await fetch(`${this.base}${path}`, {
      method,
      headers: { Authorization: `Bearer ${this.token}`, 'Content-Type': 'application/json', ...headers },
      body: body ? JSON.stringify(body) : undefined,
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      const err = new Error(`Mercado Pago ${method} ${path}: ${data.message || data.error || res.status}`);
      err.status = res.status;
      throw err;
    }
    return data;
  }

  // La «preferencia»: lo que se cobra y a dónde vuelve el comprador. init_point es la página de pago.
  createPreference({ orderId, title, description, pictureUrl, quantity, unitPrice, currency, successUrl, failureUrl, pendingUrl, notificationUrl, statement }) {
    return this.request(
      'POST',
      '/checkout/preferences',
      {
        items: [
          {
            id: orderId,
            title: title.slice(0, 250),
            description: (description || '').slice(0, 250),
            ...(pictureUrl ? { picture_url: pictureUrl } : {}),
            category_id: 'virtual_goods',
            quantity,
            currency_id: currency,
            unit_price: unitPrice,
          },
        ],
        external_reference: orderId,
        back_urls: { success: successUrl, failure: failureUrl, pending: pendingUrl },
        auto_return: 'approved',
        ...(notificationUrl ? { notification_url: notificationUrl } : {}),
        ...(statement ? { statement_descriptor: statement.slice(0, 22) } : {}),
        metadata: { order_id: orderId },
      },
      { 'X-Idempotency-Key': `create-${orderId}` },
    );
  }

  getPayment(id) {
    return this.request('GET', `/v1/payments/${encodeURIComponent(id)}`);
  }

  // Los pagos de un pedido (el más nuevo primero).
  async paymentsFor(orderId) {
    const data = await this.request(
      'GET',
      `/v1/payments/search?external_reference=${encodeURIComponent(orderId)}&sort=date_created&criteria=desc`,
    );
    return data.results || [];
  }

  /**
   * La firma del webhook (cabecera x-signature «ts=…,v1=…»): HMAC-SHA256 con la clave secreta de «id:<data.id>;
   * request-id:<x-request-id>;ts:<ts>;». Sin clave configurada, no se comprueba (igual se pregunta a la API).
   */
  static async verifySignature({ secret, signature, requestId, dataId }) {
    if (!secret) return true;
    if (!signature) return false;
    const parts = Object.fromEntries(
      signature.split(',').map((p) => p.trim().split('=')).filter((kv) => kv.length === 2),
    );
    if (!parts.ts || !parts.v1) return false;
    let manifest = '';
    if (dataId) manifest += `id:${String(dataId).toLowerCase()};`;
    if (requestId) manifest += `request-id:${requestId};`;
    manifest += `ts:${parts.ts};`;
    const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
    const mac = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(manifest));
    const hex = [...new Uint8Array(mac)].map((b) => b.toString(16).padStart(2, '0')).join('');
    if (hex.length !== parts.v1.length) return false;
    let diff = 0;
    for (let i = 0; i < hex.length; i++) diff |= hex.charCodeAt(i) ^ parts.v1.charCodeAt(i);
    return diff === 0;
  }
}
