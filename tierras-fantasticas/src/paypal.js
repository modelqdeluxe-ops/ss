// Cliente mínimo de la API REST de PayPal (Orders v2), sin dependencias, para Cloudflare Workers.
// Documentación: https://developer.paypal.com/docs/api/orders/v2/
//
// El comprador aprueba el pago en la página de PayPal y vuelve a /success, que lo captura (cobra). PayPal también avisa
// con webhooks (pago capturado, reembolso, disputa), verificados con la propia API de PayPal.

const API_BASES = {
  sandbox: 'https://api-m.sandbox.paypal.com',
  live: 'https://api-m.paypal.com',
};

// Monedas de PayPal que no admiten decimales.
const ZERO_DECIMAL = new Set(['JPY', 'HUF', 'TWD']);

// Convierte céntimos a la cadena que espera PayPal ("4.99", o "499" en JPY).
export function formatAmount(cents, currency) {
  if (ZERO_DECIMAL.has(currency.toUpperCase())) return String(Math.round(cents / 100));
  return (cents / 100).toFixed(2);
}

export class PayPal {
  constructor({ clientId, clientSecret, env = 'sandbox', apiBase }) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.base = apiBase || API_BASES[env] || API_BASES.sandbox;
    this.token = null;
    this.tokenExpires = 0;
  }

  async accessToken() {
    if (this.token && Date.now() < this.tokenExpires) return this.token;
    const auth = btoa(`${this.clientId}:${this.clientSecret}`);
    const res = await fetch(`${this.base}/v1/oauth2/token`, {
      method: 'POST',
      headers: { Authorization: `Basic ${auth}`, 'Content-Type': 'application/x-www-form-urlencoded' },
      body: 'grant_type=client_credentials',
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(`PayPal auth: ${data.error_description || res.status}`);
    this.token = data.access_token;
    // Renovamos un minuto antes de que caduque.
    this.tokenExpires = Date.now() + (data.expires_in - 60) * 1000;
    return this.token;
  }

  async request(method, path, body, headers = {}) {
    const res = await fetch(`${this.base}${path}`, {
      method,
      headers: {
        Authorization: `Bearer ${await this.accessToken()}`,
        'Content-Type': 'application/json',
        ...headers,
      },
      body: body ? JSON.stringify(body) : undefined,
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      const detail = data.details?.[0]?.issue || data.name || res.status;
      const err = new Error(`PayPal ${method} ${path}: ${detail}`);
      err.status = res.status;
      err.issue = data.details?.[0]?.issue;
      throw err;
    }
    return data;
  }

  createOrder({ amount, currency, description, customId, item, brandName, returnUrl, cancelUrl, requestId }) {
    const currency_code = currency.toUpperCase();
    return this.request(
      'POST',
      '/v2/checkout/orders',
      {
        intent: 'CAPTURE',
        purchase_units: [
          {
            description,
            custom_id: customId,
            amount: {
              currency_code,
              value: formatAmount(amount, currency),
              breakdown: { item_total: { currency_code, value: formatAmount(amount, currency) } },
            },
            items: [
              {
                name: item.name.slice(0, 127),
                quantity: String(item.quantity),
                unit_amount: { currency_code, value: formatAmount(item.unitAmount, currency) },
                category: 'DIGITAL_GOODS',
              },
            ],
          },
        ],
        // Producto digital: sin dirección de envío.
        application_context: {
          brand_name: brandName,
          shipping_preference: 'NO_SHIPPING',
          user_action: 'PAY_NOW',
          return_url: returnUrl,
          cancel_url: cancelUrl,
        },
      },
      requestId ? { 'PayPal-Request-Id': requestId } : {},
    );
  }

  // Enlace a la página de PayPal donde el comprador aprueba el pago.
  static approveUrl(order) {
    return (order.links || []).find((l) => l.rel === 'approve' || l.rel === 'payer-action')?.href || null;
  }

  // Estado de un cobro (COMPLETED, PENDING, REFUNDED, PARTIALLY_REFUNDED…), para saber si un reembolso es completo.
  getCapture(captureId) {
    return this.request('GET', `/v2/payments/captures/${encodeURIComponent(captureId)}`);
  }

  // La cabecera PayPal-Request-Id hace que repetir la captura sea seguro.
  captureOrder(orderId) {
    return this.request('POST', `/v2/checkout/orders/${encodeURIComponent(orderId)}/capture`, undefined, {
      'PayPal-Request-Id': `capture-${orderId}`,
    });
  }

  getOrder(orderId) {
    return this.request('GET', `/v2/checkout/orders/${encodeURIComponent(orderId)}`);
  }

  // Comprueba con PayPal que el webhook es auténtico.
  async verifyWebhook({ headers, event, webhookId }) {
    const data = await this.request('POST', '/v1/notifications/verify-webhook-signature', {
      auth_algo: headers['paypal-auth-algo'],
      cert_url: headers['paypal-cert-url'],
      transmission_id: headers['paypal-transmission-id'],
      transmission_sig: headers['paypal-transmission-sig'],
      transmission_time: headers['paypal-transmission-time'],
      webhook_id: webhookId,
      webhook_event: event,
    });
    return data.verification_status === 'SUCCESS';
  }
}

