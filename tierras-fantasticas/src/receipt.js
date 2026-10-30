// Recibo de compra (PDF): el que se descarga el jugador al terminar de pagar y el que llega adjunto al dueño. Lleva
// todo lo que sirve de prueba ante un reembolso injustificado: el pedido, lo comprado, el importe, el medio de pago y su
// referencia, las fechas de pago y de entrega en el servidor, la aceptación de los Términos (versión y fecha) y la
// política de cancelación y reembolsos que se aceptó. La copia del dueño añade la IP y el navegador de la aceptación.
import { makePdf } from './pdf.js';
import legalArchive from '../config/legal-archive.json' with { type: 'json' };

const SELLER = 'Tierras Fantásticas (servidor de Minecraft independiente)';
const CONTACT = 'tierrasfantasticasmc@gmail.com';
const SITE = 'https://tierrasfantásticas.store';

// (30 oct 2026, el dueño: «la compra tiene una garantía de 24 horas, el ban de IP es de 15 días; las compras hechas
// antes de esta actualización están bajo los términos anteriores, de 5 días de garantía y ban de 6 días») Cada pedido
// se rige por los Términos que aceptó al pagar (order.termsVersion): desde la versión TERMS_24H, 24 horas y 15 días; antes,
// 5 días hábiles y 6 días.
export const TERMS_24H = '2026-10-30.1';

const POLICY_24H = {
  id: '24h',
  windowText: '24 horas',
  ban: '15d',
  banText: '15 días',
  lines: [
    'Garantía de 24 horas: puedes cancelar la compra y recuperar tu dinero dentro de las 24 horas siguientes a la entrega en el servidor, sin dar explicaciones, escribiendo a ' +
      CONTACT + ' con tu número de pedido. Al reembolsar se retira del servidor todo lo entregado con esta compra.',
    'Pasadas esas 24 horas no hay reembolsos: la compra es definitiva (salvo que no se entregara o se cobrara dos veces o por error). Es contenido digital que se entrega y se puede usar al momento, y pediste su entrega inmediata.',
    'Antes de reclamar al banco, a la tarjeta, a Stripe o a PayPal hay que escribirnos: lo resolvemos en un máximo de 3 días hábiles.',
    'Reembolso injustificado: si pasadas las 24 horas se reclama el dinero por otra vía (disputa o contracargo con el banco, la tarjeta, Stripe o PayPal), se retira todo lo de esta compra y el jugador recibe un baneo de IP del servidor de 15 días (en un regalo, el jugador que lo pagó). Este recibo, el registro del pago, de la entrega y de las conexiones al servidor y la aceptación de los Términos se presentan como prueba.',
  ],
};

const POLICY_5D = {
  id: '5d',
  windowText: '5 días hábiles',
  ban: '6d',
  banText: '6 días',
  lines: [
    'Cancelación: puedes cancelar la compra y recuperar tu dinero dentro de los 5 días hábiles siguientes a la entrega, escribiendo a ' +
      CONTACT + ' con tu número de pedido. Al reembolsar se retira del servidor todo lo entregado con esta compra.',
    'Pasado ese plazo no hay reembolsos: la compra es definitiva (salvo que no se entregara, se cobrara dos veces o por error, o la hiciera un menor sin permiso).',
    'Reembolso injustificado: si pasado el plazo se reclama el dinero por otra vía (disputa o contracargo con el banco, la tarjeta, Stripe o PayPal), se retira todo lo de esta compra y el jugador recibe un baneo de IP del servidor de 6 días (en un regalo, el jugador que lo pagó). Este recibo, el registro del pago y de la entrega y la aceptación de los Términos se presentan como prueba.',
  ],
};

// (30 oct 2026, el dueño: «espero que tengas una copia de los términos anteriores para citarlos en cualquier disputa»)
// La versión archivada de los Términos que aceptó un pedido (tools/legal_archive.py → /archivo-legal/): la de su número
// y, si el texto cambió dentro del mismo número, la que estaba publicada cuando aceptó. Sin número (antes del 7 oct
// 2026), la publicada en la fecha del pedido.
export function archivedTerms(order, acceptance) {
  const all = legalArchive.versions || [];
  const version = order?.termsVersion || acceptance?.version || null;
  const at = Date.parse(acceptance?.at || order?.createdAt || '') || Date.now();
  const inTime = (e) => Date.parse(e.from) <= at && (!e.to || at < Date.parse(e.to));
  const same = all.filter((e) => (version ? e.version === version : true));
  return same.find(inTime) || (version ? same[same.length - 1] : null) || null;
}

// La política de cancelación y reembolsos de un pedido: la de los Términos que aceptó al pagar
export function refundPolicyFor(order) {
  return order?.termsVersion && String(order.termsVersion) >= TERMS_24H ? POLICY_24H : POLICY_5D;
}

// La vigente (lo que se enseña antes de pagar)
export const REFUND_POLICY = POLICY_24H.lines;

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
  const archived = archivedTerms(order, acceptance);
  if (archived) rows.push(['Texto de esos Términos', `${SITE}${archived.terms}`]);
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
  for (const p of refundPolicyFor(order).lines) blocks.push({ text: `• ${p}`, size: 10, gap: 5 });
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

// --- Expediente de una disputa o un contracargo ---
// (30 oct 2026, el dueño: «ponte en los zapatos de alguien experto en disputas de PayPal o Stripe; agrega todo lo
// pertinente para que ganemos fácilmente las disputas pasada la garantía, ya he perdido casos en los que tengo la
// razón») Lo que miran PayPal, Stripe y el banco en un producto digital: que se entregó (registro del servidor), que el
// comprador lo usó después (conexiones al servidor), que aceptó la política de reembolsos antes de pagar (casilla
// obligatoria con versión, fecha, IP y navegador), que no pidió el reembolso a tiempo, que el producto era el descrito y
// que la misma cuenta compró antes sin disputar (Visa Compelling Evidence 3.0). Con todo eso se escribe la respuesta en
// inglés (la que leen los revisores) y en español, el PDF para adjuntar y los campos de pruebas de Stripe.

const HOUR = 3600 * 1000;
const isoOf = (v) => {
  if (v == null || v === '') return null;
  const d = new Date(typeof v === 'number' ? v : String(v));
  return Number.isNaN(d.getTime()) ? null : d.toISOString();
};
const enDate = (iso) => (iso ? `${iso.replace('T', ' ').replace(/\.\d+Z$/, ' UTC')}` : 'n/a');
const span = (ms, lang) => {
  const h = Math.max(0, ms) / HOUR;
  if (h < 48) return lang === 'en' ? `${Math.round(h)} hours` : `${Math.round(h)} horas`;
  const d = Math.round(h / 24);
  return lang === 'en' ? `${d} days` : `${d} días`;
};

// Los hechos de un pedido disputado. history: los demás pedidos del mismo jugador; player: {firstSeen, lastSeen} (ms)
export function disputeFacts({ order, productName, acceptance, player, delivery, history = [], at = Date.now() }) {
  const policy = refundPolicyFor(order);
  const delivered = isoOf(order.deliveredAt || delivery?.doneAt);
  const paid = isoOf(order.paidAt || order.createdAt);
  const since = delivered || paid;
  const elapsed = since ? at - Date.parse(since) : 0;
  const windowMs = policy.id === '24h' ? 24 * HOUR : 7 * 24 * HOUR; // 5 días hábiles ≈ 7 naturales
  const lastSeen = isoOf(player?.lastSeen);
  const previous = history
    .filter((o) => o.id !== order.id && o.amount > 0 && ['delivered', 'queued', 'paid'].includes(o.status) && !o.refunded)
    .filter((o) => !order.createdAt || String(o.createdAt) < String(order.createdAt))
    .map((o) => ({ id: o.id, at: isoOf(o.paidAt || o.createdAt), amount: money(o.amount, o.currency), product: o.productName || o.productId }));
  return {
    order,
    productName,
    policy,
    archived: archivedTerms(order, acceptance),
    delivered,
    paid,
    elapsedMs: elapsed,
    inWindow: elapsed <= windowMs,
    acceptance,
    firstSeen: isoOf(player?.firstSeen),
    lastSeen,
    usedAfter: Boolean(lastSeen && since && Date.parse(lastSeen) > Date.parse(since)),
    previous,
    amount: money(order.amount, order.currency),
    method: METHOD[order.method] || order.method || 'n/a',
  };
}

// La respuesta, en inglés (en) o en español (es): párrafos listos para pegar en PayPal o Stripe
export function disputeLetter(f, lang = 'en') {
  const o = f.order;
  const en = lang === 'en';
  const who = `${o.username}${o.uuid ? ` (UUID ${o.uuid})` : ''}`;
  const out = [];
  out.push(
    en
      ? `1. Product: "${f.productName}", a digital item for our Minecraft server Tierras Fantásticas, delivered automatically to the buyer's Minecraft account ${who}. Paid ${f.amount} on ${enDate(f.paid)} with ${f.method} (reference ${o.paymentIntent || 'n/a'}), order ${o.id}.`
      : `1. Producto: «${f.productName}», contenido digital de nuestro servidor de Minecraft Tierras Fantásticas, entregado automáticamente a la cuenta de Minecraft del comprador ${who}. Pagado ${f.amount} el ${enDate(f.paid)} con ${f.method} (referencia ${o.paymentIntent || '—'}), pedido ${o.id}.`,
  );
  out.push(
    f.delivered
      ? en
        ? `2. Delivered: our game server confirmed the delivery to that account on ${enDate(f.delivered)} (server delivery log). The buyer received the item; this is not an "item not received" case.`
        : `2. Entregado: el servidor confirmó la entrega en esa cuenta el ${enDate(f.delivered)} (registro de entrega del servidor). El comprador lo recibió: no es un caso de «no recibido».`
      : en
        ? '2. Delivery: the item is queued and is delivered automatically the next time the account joins the server.'
        : '2. Entrega: está en cola y se entrega sola en cuanto la cuenta entra al servidor.',
  );
  if (f.usedAfter) {
    out.push(
      en
        ? `3. Used after delivery: the same Minecraft account kept connecting to our server after the delivery (last connection recorded: ${enDate(f.lastSeen)}${f.firstSeen ? `; first connection: ${enDate(f.firstSeen)}` : ''}).`
        : `3. Usado después de la entrega: la misma cuenta de Minecraft siguió entrando al servidor después de la entrega (última conexión registrada: ${enDate(f.lastSeen)}${f.firstSeen ? `; primera: ${enDate(f.firstSeen)}` : ''}).`,
    );
  }
  const a = f.acceptance;
  out.push(
    a
      ? en
        ? `4. Terms and refund policy accepted before paying: the checkout requires ticking a mandatory checkbox "I have read and accept the Terms and Conditions and the Privacy Notice". The buyer accepted version ${a.version} on ${enDate(isoOf(a.at))} from IP address ${a.ip || 'n/a'} (browser: ${a.userAgent || 'n/a'}). The refund policy is written next to the payment button and in the Terms (https://tierrasfantásticas.store/terminos#reembolsos): refunds only within ${f.policy.id === '24h' ? '24 hours' : '5 business days'} of delivery, by emailing us; after that the sale is final, and disputing a valid charge is penalized.`
        : `4. Términos y política de reembolsos aceptados antes de pagar: para pagar hay que marcar la casilla obligatoria «He leído y acepto los Términos y condiciones y el Aviso de privacidad». El comprador aceptó la versión ${a.version} el ${enDate(isoOf(a.at))} desde la IP ${a.ip || '—'} (navegador: ${a.userAgent || '—'}). La política está junto al botón de pago y en los Términos (https://tierrasfantásticas.store/terminos#reembolsos): reembolso solo dentro de ${f.policy.windowText} desde la entrega, escribiéndonos; después la compra es definitiva y disputar un cargo válido se sanciona.`
      : en
        ? `4. Terms: the buyer accepted our Terms and Conditions (version ${o.termsVersion || 'n/a'}) with a mandatory checkbox before paying; refunds only within ${f.policy.id === '24h' ? '24 hours' : '5 business days'} of delivery.`
        : `4. Términos: el comprador aceptó los Términos (versión ${o.termsVersion || '—'}) con la casilla obligatoria antes de pagar; reembolsos solo dentro de ${f.policy.windowText} desde la entrega.`,
  );
  if (f.archived) {
    const url = `${SITE}${f.archived.terms}`;
    const commit = `https://github.com/modelqdeluxe-ops/ss/commit/${f.archived.commit}`;
    out.push(
      en
        ? `4b. Exact terms in force for this purchase: version ${f.archived.label}, published on ${enDate(isoOf(f.archived.from))}${f.archived.to ? ` and replaced on ${enDate(isoOf(f.archived.to))}` : ''}. Archived copy: ${url}. Its publication date can be verified in our public repository: ${commit} (SHA-256 of the page: ${f.archived.termsSha256}). The refund section of that version is quoted in full in the attached evidence.`
        : `4b. Términos exactos de esta compra: versión ${f.archived.label}, publicada el ${enDate(isoOf(f.archived.from))}${f.archived.to ? ` y sustituida el ${enDate(isoOf(f.archived.to))}` : ''}. Copia archivada: ${url}. Su fecha se puede comprobar en el repositorio público: ${commit} (SHA-256 de la página: ${f.archived.termsSha256}). La sección de reembolsos de esa versión va copiada entera en el expediente.`,
    );
  }
  out.push(
    en
      ? `5. Refund window: the buyer never asked us for a refund (our support email is in the receipt, the Terms and the store). The dispute was opened ${span(f.elapsedMs, 'en')} after ${f.delivered ? 'delivery' : 'payment'}, ${f.inWindow ? 'within' : 'outside'} the refund window.`
      : `5. Plazo de reembolso: el comprador nunca nos pidió el reembolso (el correo de soporte está en el recibo, los Términos y la tienda). La disputa se abrió ${span(f.elapsedMs, 'es')} después de la ${f.delivered ? 'entrega' : 'compra'}, ${f.inWindow ? 'dentro' : 'fuera'} del plazo.`,
  );
  if (f.previous.length) {
    const list = f.previous.slice(0, 10).map((p) => `${p.id} (${enDate(p.at)}, ${p.amount})`).join('; ');
    out.push(
      en
        ? `6. Purchase history: the same Minecraft account made ${f.previous.length} earlier purchase(s) that were never disputed: ${list}.`
        : `6. Historial: la misma cuenta de Minecraft hizo ${f.previous.length} compra(s) anteriores que nunca disputó: ${list}.`,
    );
  }
  out.push(
    en
      ? '7. As described: the store page lists exactly what the product includes before paying, and that is what was delivered. Game balance updates are part of the normal service and are covered in our Terms.'
      : '7. Como se describe: la página de la tienda dice exactamente qué incluye antes de pagar, y eso es lo que se entregó. Los ajustes de equilibrio del juego son parte normal del servicio y están en los Términos.',
  );
  out.push(
    en
      ? 'The charge is valid and the product was delivered and used as agreed. We ask that this dispute be resolved in our favor. Attached: receipt, terms acceptance record and delivery record.'
      : 'El cargo es válido y el producto se entregó y se usó según lo acordado. Pedimos que la disputa se resuelva a nuestro favor. Adjuntos: recibo, registro de la aceptación de los Términos y registro de la entrega.',
  );
  return out;
}

// Los campos de pruebas de una disputa de Stripe (https://docs.stripe.com/api/disputes/update): solo texto
export function stripeEvidence(f) {
  const o = f.order;
  const a = f.acceptance;
  const cut = (s) => String(s).slice(0, 20000);
  const log = [
    `${enDate(isoOf(o.createdAt))} order ${o.id} created for Minecraft account ${o.username}${o.uuid ? ` (UUID ${o.uuid})` : ''}`,
    a ? `${enDate(isoOf(a.at))} Terms and refund policy accepted (version ${a.version}) from IP ${a.ip || 'n/a'}, browser ${a.userAgent || 'n/a'}` : null,
    `${enDate(f.paid)} payment confirmed (${f.amount})`,
    f.delivered ? `${enDate(f.delivered)} delivered by our game server to the buyer's Minecraft account` : null,
    f.lastSeen ? `${enDate(f.lastSeen)} last connection of that account to our server` : null,
  ].filter(Boolean);
  return {
    product_description: cut(`${f.productName}: digital item for the Minecraft server Tierras Fantásticas (https://tierrasfantásticas.store), delivered automatically to the buyer's Minecraft account ${o.username}. Order ${o.id}.`),
    customer_name: o.username,
    ...(a?.ip ? { customer_purchase_ip: a.ip } : {}),
    ...(f.delivered ? { service_date: f.delivered.slice(0, 10) } : {}),
    access_activity_log: cut(log.join('\n')),
    refund_policy_disclosure: cut(
      `The refund policy is shown next to the payment button and in our Terms (https://tierrasfantásticas.store/terminos#reembolsos). The buyer must tick a mandatory checkbox accepting the Terms before paying${a ? ` (accepted version ${a.version} on ${enDate(isoOf(a.at))} from IP ${a.ip || 'n/a'})` : ''}. Refunds only within ${f.policy.id === '24h' ? '24 hours' : '5 business days'} of delivery by emailing us.${f.archived ? ` Exact terms accepted (archived, version ${f.archived.label}): ${SITE}${f.archived.terms} — publication verifiable at https://github.com/modelqdeluxe-ops/ss/commit/${f.archived.commit}.` : ''}`,
    ),
    refund_refusal_explanation: cut(
      `The buyer never requested a refund. The dispute was opened ${span(f.elapsedMs, 'en')} after ${f.delivered ? 'delivery' : 'payment'}, outside the refund window, and the item had been delivered${f.usedAfter ? ' and used (the account kept playing on our server)' : ''}.`,
    ),
    uncategorized_text: cut(disputeLetter(f, 'en').join('\n\n')),
  };
}

// El expediente en PDF (para adjuntarlo en PayPal, Stripe o al banco)
export function disputePdf(f) {
  const o = f.order;
  const blocks = [
    { text: 'Dispute evidence / Pruebas de la disputa', size: 20, bold: true, gap: 2 },
    { text: `${SELLER} · ${SITE} · ${CONTACT}`, size: 10, color: [0.42, 0.35, 0.29], gap: 2 },
    { text: `Order / Pedido ${o.id} · ${o.username}`, size: 11, gap: 6 },
    { rule: true },
    { text: 'Response (English)', size: 13, bold: true, gap: 4 },
  ];
  for (const p of disputeLetter(f, 'en')) blocks.push({ text: p, size: 10, gap: 6 });
  blocks.push({ rule: true }, { text: 'Respuesta (español)', size: 13, bold: true, gap: 4 });
  for (const p of disputeLetter(f, 'es')) blocks.push({ text: p, size: 10, gap: 6 });
  blocks.push({ rule: true }, { text: 'Activity log / Registro', size: 13, bold: true, gap: 4 });
  for (const line of stripeEvidence(f).access_activity_log.split('\n')) blocks.push({ text: `• ${line}`, size: 10, gap: 4 });
  blocks.push({ rule: true }, { text: `Refund policy accepted at checkout / Política aceptada al pagar (${f.policy.windowText})`, size: 12, bold: true, gap: 4 });
  for (const p of f.policy.lines) blocks.push({ text: `• ${p}`, size: 10, gap: 5 });
  if (f.archived?.refunds) {
    blocks.push(
      { rule: true },
      { text: `Terms in force for this purchase, refund section verbatim / Sección de reembolsos de los Términos de esta compra, tal cual (versión ${f.archived.label})`, size: 12, bold: true, gap: 2 },
      { text: `${SITE}${f.archived.terms} · https://github.com/modelqdeluxe-ops/ss/commit/${f.archived.commit}`, size: 9, color: [0.42, 0.35, 0.29], gap: 4 },
    );
    for (const line of f.archived.refunds.split('\n')) blocks.push({ text: line, size: 10, gap: 4 });
  }
  blocks.push({ text: `Expediente generado el ${fmtDate(new Date().toISOString())}. Términos completos: ${SITE}/terminos`, size: 9, color: [0.42, 0.35, 0.29] });
  return makePdf(blocks, { title: `Disputa ${o.id}` });
}
