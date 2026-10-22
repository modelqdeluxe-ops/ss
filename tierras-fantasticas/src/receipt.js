// Recibo de compra (PDF): el que se descarga el jugador al terminar de pagar y el que llega adjunto al dueño. Lleva
// todo lo que sirve de prueba ante un reembolso injustificado: el pedido, lo comprado, el importe, el medio de pago y su
// referencia, las fechas de pago y de entrega en el servidor, la aceptación de los Términos (versión y fecha) y la
// política de cancelación y reembolsos que se aceptó. La copia del dueño añade la IP y el navegador de la aceptación.
import { makePdf } from './pdf.js';

const SELLER = 'Tierras Fantásticas (servidor de Minecraft independiente)';
const CONTACT = 'tierrasfantasticasmc@gmail.com';
const SITE = 'https://tierrasfantásticas.store';

export const REFUND_POLICY = [
  'Cancelación: puedes cancelar la compra y recuperar tu dinero dentro de los 5 días hábiles siguientes a la entrega, escribiendo a ' +
    CONTACT + ' con tu número de pedido. Al reembolsar se retira del servidor todo lo entregado con esta compra.',
  'Pasado ese plazo no hay reembolsos: la compra es definitiva (salvo que no se entregara, se cobrara dos veces o por error, o la hiciera un menor sin permiso).',
  'Reembolso injustificado: si pasado el plazo se reclama el dinero por otra vía (disputa o contracargo con el banco, la tarjeta, Stripe o PayPal), se retira todo lo de esta compra y el jugador recibe un baneo de IP del servidor de 6 días (en un regalo, el jugador que lo pagó). Este recibo, el registro del pago y de la entrega y la aceptación de los Términos se presentan como prueba.',
];

const fmtDate = (iso) => {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return String(iso);
  return `${d.toLocaleString('es-MX', { timeZone: 'America/Mexico_City', dateStyle: 'long', timeStyle: 'medium' })} (hora de Ciudad de México) · ${d.toISOString()}`;
};

export function money(cents, currency) {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: currency || 'USD' }).format((cents || 0) / 100);
}

const METHOD = { stripe: 'Tarjeta con Stripe (Apple Pay, Google Pay, Link)', paypal: 'PayPal' };

// Filas del recibo [etiqueta, valor]; owner = la copia del dueño (con IP y navegador)
export function receiptRows({ order, productName, acceptance, owner = false }) {
  const rows = [
    ['Número de pedido', order.id],
    ['Fecha del pedido', fmtDate(order.createdAt)],
    ['Fecha del pago', fmtDate(order.paidAt)],
    ...(order.gift
      ? [
          ['Compra', `Regalo de ${order.gift.fromName} (paga y acepta los Términos) para ${order.username}`],
          ['UUID de quien paga', order.gift.fromUuid || '—'],
          ...(order.gift.message ? [['Mensaje del regalo', order.gift.message]] : []),
        ]
      : []),
    [order.gift ? 'Jugador que lo recibe' : 'Jugador de Minecraft', order.username],
    [order.gift ? 'UUID de quien lo recibe' : 'UUID del jugador', order.uuid || '—'],
    ['Producto', `${productName}${order.quantity > 1 ? ` ×${order.quantity}` : ''}`],
    ['Importe total pagado', `${money(order.amount, order.currency)} (${order.currency}, precio final)`],
    ['Medio de pago', METHOD[order.method] || order.method || '—'],
    ['Referencia del pago', order.paymentIntent || '—'],
    ['Sesión de pago', order.sessionId || '—'],
    ['Entrega en el servidor', order.deliveredAt ? `Entregado el ${fmtDate(order.deliveredAt)}` : 'En cola: se entrega al entrar al servidor'],
    ['Términos aceptados', acceptance ? `Versión ${acceptance.version}, el ${fmtDate(acceptance.at)}` : order.termsVersion ? `Versión ${order.termsVersion}` : '—'],
  ];
  if (owner) {
    rows.push(['IP de la aceptación', acceptance?.ip || '—'], ['Navegador de la aceptación', acceptance?.userAgent || '—']);
    if (order.discord?.username || order.discordId) rows.push(['Discord', order.discord?.username || order.discordId]);
    if (order.refunded) rows.push(['Reembolso / disputa', order.refunded]);
  }
  return rows;
}

export function receiptPdf({ order, productName, acceptance, owner = false }) {
  const blocks = [
    { text: 'Recibo de compra', size: 22, bold: true, gap: 2 },
    { text: SELLER, size: 11, color: [0.42, 0.35, 0.29], gap: 2 },
    { text: `${SITE} · ${CONTACT}`, size: 10, color: [0.42, 0.35, 0.29], gap: 6 },
    { rule: true },
  ];
  for (const [k, v] of receiptRows({ order, productName, acceptance, owner })) {
    blocks.push({ text: k, size: 9.5, bold: true, color: [0.42, 0.35, 0.29], gap: 0 }, { text: String(v), size: 11, gap: 7 });
  }
  blocks.push({ rule: true }, { text: 'Política de cancelación y reembolsos (aceptada al pagar)', size: 12, bold: true, gap: 4 });
  for (const p of REFUND_POLICY) blocks.push({ text: `• ${p}`, size: 10, gap: 5 });
  blocks.push(
    { rule: true },
    {
      text: 'Lo comprado es contenido digital del servidor (aspecto y ventajas dentro del juego), personal y vinculado a la cuenta del jugador. Tierras Fantásticas es un servidor independiente: no es un producto oficial de Minecraft y no está aprobado por Mojang ni Microsoft ni asociado con ellos.',
      size: 9,
      color: [0.42, 0.35, 0.29],
      gap: 4,
    },
    { text: `Recibo emitido el ${fmtDate(new Date().toISOString())}. Términos completos: ${SITE}/terminos`, size: 9, color: [0.42, 0.35, 0.29] },
  );
  return makePdf(blocks, { title: `Recibo ${order.id}` });
}
