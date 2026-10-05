// Cliente mínimo de la API de Stripe (Checkout Sessions) sin dependencias, para Cloudflare Workers.
// Documentación: https://docs.stripe.com/api/checkout/sessions
//
// El comprador paga en la página de pago de Stripe (tarjeta, Apple Pay, Google Pay, Link...): los datos
// bancarios nunca pasan por la web. Stripe avisa del pago con un webhook firmado.

const DEFAULT_API = 'https://api.stripe.com';
const enc = new TextEncoder();

// Monedas sin decimales en Stripe: el importe va en unidades, no en céntimos.
const ZERO_DECIMAL = new Set(['BIF', 'CLP', 'DJF', 'GNF', 'JPY', 'KMF', 'KRW', 'MGA', 'PYG', 'RWF', 'UGX', 'VND', 'VUV', 'XAF', 'XOF', 'XPF']);

// Los precios del catálogo están en céntimos; Stripe los quiere en la unidad mínima de la moneda.
export function toStripeAmount(cents, currency) {
  return ZERO_DECIMAL.has(currency.toUpperCase()) ? Math.round(cents / 100) : cents;
}

// Stripe recibe los datos como formulario con corchetes: line_items[0][price_data][currency]=usd
export function toForm(data, prefix = '', out = new URLSearchParams()) {
  for (const [key, value] of Object.entries(data)) {
    if (value === undefined || value === null) continue;
    const name = prefix ? `${prefix}[${key}]` : key;
    if (Array.isArray(value)) {
      value.forEach((item, i) => {
        if (item !== null && typeof item === 'object') toForm(item, `${name}[${i}]`, out);
        else out.append(`${name}[${i}]`, String(item));
      });
    } else if (typeof value === 'object') {
      toForm(value, name, out);
    } else {
      out.append(name, String(value));
    }
  }
  return out;
}

function hex(buffer) {
  return [...new Uint8Array(buffer)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

function timingSafeEqual(a, b) {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

export class Stripe {
  constructor({ secretKey, apiBase }) {
    this.secretKey = secretKey;
    this.api = apiBase || DEFAULT_API;
  }

  async request(method, path, { form, idempotencyKey } = {}) {
    const headers = { Authorization: `Bearer ${this.secretKey}`, 'Stripe-Version': '2024-06-20' };
    let body;
    if (form) {
      headers['Content-Type'] = 'application/x-www-form-urlencoded';
      body = toForm(form).toString();
    }
    if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;
    const res = await fetch(`${this.api}${path}`, { method, headers, body });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      const err = new Error(`Stripe ${method} ${path}: ${data.error?.message || res.status}`);
      err.status = res.status;
      err.code = data.error?.code;
      throw err;
    }
    return data;
  }

  // Página de pago de Stripe para un pedido. El precio sale del catálogo de la web, nunca del navegador.
  createCheckoutSession({ orderId, currency, unitAmount, quantity, name, description, image, successUrl, cancelUrl, metadata }) {
    return this.request('POST', '/v1/checkout/sessions', {
      idempotencyKey: `checkout-${orderId}`,
      form: {
        mode: 'payment',
        client_reference_id: orderId,
        success_url: successUrl,
        cancel_url: cancelUrl,
        locale: 'es',
        // Stripe exige al menos 30 minutos; una hora da tiempo de sobra para pagar.
        expires_at: Math.floor(Date.now() / 1000) + 3600,
        line_items: [
          {
            quantity,
            price_data: {
              currency: currency.toLowerCase(),
              unit_amount: toStripeAmount(unitAmount, currency),
              product_data: {
                name: name.slice(0, 250),
                description: description ? description.slice(0, 500) : undefined,
                images: image ? [image] : undefined,
              },
            },
          },
        ],
        metadata,
        // El pago (PaymentIntent) también lleva el pedido: así los reembolsos y disputas se encuentran.
        payment_intent_data: { metadata, description: `${name} — pedido ${orderId}`.slice(0, 1000) },
      },
    });
  }

  retrieveCheckoutSession(id) {
    return this.request('GET', `/v1/checkout/sessions/${encodeURIComponent(id)}`);
  }

  // Comprueba la cabecera Stripe-Signature (HMAC-SHA256 de "timestamp.cuerpo" con el secreto del webhook).
  // https://docs.stripe.com/webhooks#verify-manually
  static async verifyWebhook(payload, header, secret, toleranceSeconds = 300) {
    if (!header || !secret) return false;
    let timestamp = null;
    const signatures = [];
    for (const part of header.split(',')) {
      const i = part.indexOf('=');
      const name = part.slice(0, i).trim();
      const value = part.slice(i + 1).trim();
      if (name === 't') timestamp = value;
      if (name === 'v1') signatures.push(value);
    }
    if (!timestamp || !signatures.length) return false;
    if (Math.abs(Date.now() / 1000 - Number(timestamp)) > toleranceSeconds) return false;

    const key = await crypto.subtle.importKey('raw', enc.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
    const expected = hex(await crypto.subtle.sign('HMAC', key, enc.encode(`${timestamp}.${payload}`)));
    return signatures.some((sig) => timingSafeEqual(sig, expected));
  }
}
