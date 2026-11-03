// Cupones de descuento de la tienda (3 nov 2026, el dueño: «en el panel de staff dame la posibilidad de crear cupones
// de descuento, tanto en porcentaje como en valores enteros»). Lo que no depende de la base de datos: el código, lo que
// el staff puede crear, cuánto descuenta y cómo se reparte el total entre los productos de una compra.
//
// - kind 'percent': value = 1–99 (%). kind 'amount': value = céntimos de dólar (500 = 5,00 USD).
// - El descuento sale del precio en dólares (el del catálogo o el de la mejora de rango). Si se paga en pesos (Mercado
//   Pago o tarjeta desde México), cada producto ya descontado se pasa a pesos al cambio del día.
// - El total con cupón nunca baja de MIN_TOTAL (1,00 USD): Stripe no cobra menos de 0,50 USD ni de 10 MXN, y así ningún
//   cupón deja un pago que las pasarelas rechacen. Para dar algo gratis está «Test» en el panel de staff.

export const CODE_RE = /^[A-Z0-9][A-Z0-9_-]{2,23}$/;
export const MIN_TOTAL = 100;
export const PERCENT_MAX = 99;
// 1.000 USD como mucho de descuento fijo o de compra mínima: para que un error al teclear no cree un cupón absurdo
export const AMOUNT_MAX = 100000;
export const MAX_USES_MAX = 1000000;
export const NOTE_MAX = 80;

// El código tal y como se guarda: en mayúsculas y sin espacios (VERANO10). null si no es válido.
export function normalizeCode(value) {
  if (typeof value !== 'string') return null;
  const code = value.trim().toUpperCase();
  return CODE_RE.test(code) ? code : null;
}

const money = (cents) => `${(cents / 100).toFixed(2)} USD`;

// «10 %» o «5,00 USD»: lo que descuenta, para la tienda y el panel
export function couponLabel(c) {
  if (!c) return '';
  return c.kind === 'percent' ? `${c.value} %` : `${(c.value / 100).toFixed(2).replace('.', ',')} USD`;
}

// Dólares escritos por el staff («5», «5.5», «5,50») → céntimos; null si no es un importe válido
export function parseUsd(value) {
  if (typeof value === 'number') value = String(value);
  if (typeof value !== 'string') return null;
  const text = value.trim().replace(',', '.');
  if (!/^\d{1,7}(\.\d{1,2})?$/.test(text)) return null;
  const cents = Math.round(Number(text) * 100);
  return Number.isSafeInteger(cents) ? cents : null;
}

/**
 * Lo que el staff manda al crear un cupón, comprobado. Devuelve { coupon } o { error }.
 * body: { code, kind: 'percent'|'amount', value, maxUses?, perPlayer?, minTotal?, expiresAt?, note? }
 * value: el porcentaje (entero) o los dólares («5» o «2.50»); minTotal en dólares; expiresAt «AAAA-MM-DD» (caduca al
 * acabar ese día en Ciudad de México) o una fecha ISO.
 */
export function couponFromInput(body) {
  const b = body && typeof body === 'object' ? body : {};
  const code = normalizeCode(b.code);
  if (!code) return { error: 'El código tiene que tener de 3 a 24 letras sin tildes, números, - o _ (por ejemplo VERANO10).' };
  const kind = b.kind === 'percent' ? 'percent' : b.kind === 'amount' ? 'amount' : null;
  if (!kind) return { error: 'Elige si el cupón descuenta un porcentaje o una cantidad fija.' };
  let value;
  if (kind === 'percent') {
    value = Number(typeof b.value === 'string' ? b.value.trim().replace(',', '.') : b.value);
    if (!Number.isInteger(value) || value < 1 || value > PERCENT_MAX) {
      return { error: `El porcentaje tiene que ser un número entero de 1 a ${PERCENT_MAX}.` };
    }
  } else {
    value = parseUsd(b.value);
    if (!value || value < 1 || value > AMOUNT_MAX) return { error: `La cantidad tiene que ir de 0,01 a ${AMOUNT_MAX / 100} USD.` };
  }
  let maxUses = null;
  if (b.maxUses !== undefined && b.maxUses !== null && String(b.maxUses).trim() !== '') {
    maxUses = Number(String(b.maxUses).trim());
    if (!Number.isInteger(maxUses) || maxUses < 1 || maxUses > MAX_USES_MAX) return { error: 'Los usos máximos tienen que ser un número entero (1 o más), o déjalo vacío para ilimitados.' };
  }
  let minTotal = 0;
  if (b.minTotal !== undefined && b.minTotal !== null && String(b.minTotal).trim() !== '') {
    minTotal = parseUsd(b.minTotal);
    if (minTotal === null || minTotal > AMOUNT_MAX) return { error: 'La compra mínima tiene que ser un importe en dólares (por ejemplo 10 o 9.99), o déjala vacía.' };
  }
  let expiresAt = null;
  if (typeof b.expiresAt === 'string' && b.expiresAt.trim()) {
    const raw = b.expiresAt.trim();
    // Un día del calendario: vale hasta el final de ese día en Ciudad de México (UTC-6, sin horario de verano)
    const day = /^(\d{4})-(\d{2})-(\d{2})$/.exec(raw);
    const at = day ? Date.parse(`${raw}T23:59:59.999-06:00`) : Date.parse(raw);
    if (!Number.isFinite(at)) return { error: 'La fecha de caducidad no es válida.' };
    if (at <= Date.now()) return { error: 'La fecha de caducidad ya pasó.' };
    expiresAt = new Date(at).toISOString();
  }
  const note = typeof b.note === 'string' ? b.note.replace(/[\p{Cc}\p{Cf}]/gu, ' ').trim().slice(0, NOTE_MAX) : '';
  return { coupon: { code, kind, value, maxUses, perPlayer: b.perPlayer === true, minTotal, expiresAt, note: note || null } };
}

/**
 * Por qué no vale un cupón ahora (o null si vale). usage = { used, reserved, mine } (pagos con el cupón, pagos
 * empezados hace poco que aún no se pagaron y los del mismo jugador).
 */
export function couponProblem(c, { subtotal, usage, now = Date.now() }) {
  if (!c) return 'Ese cupón no existe.';
  if (!c.active) return 'Ese cupón está pausado.';
  if (c.expiresAt && Date.parse(c.expiresAt) <= now) return 'Ese cupón ya caducó.';
  if (c.maxUses && usage) {
    if (usage.used >= c.maxUses) return c.maxUses === 1 ? 'Ese cupón ya se usó: era de un solo uso.' : 'Ese cupón ya se usó todas las veces que se podía.';
    // Apartado por otro pago que se está haciendo ahora (si no se paga, vuelve a quedar libre al caducar su página de pago)
    if (usage.used + usage.reserved >= c.maxUses) return 'Ese cupón lo está usando otra persona ahora mismo. Si no termina de pagar, vuelve a valer en una hora.';
  }
  if (c.perPlayer && usage && usage.mine > 0) return 'Ya usaste este cupón: es de un solo uso por jugador.';
  if (c.minTotal && subtotal < c.minTotal) return `Este cupón es para compras de ${money(c.minTotal)} o más.`;
  if (subtotal <= MIN_TOTAL) return `El cupón solo se aplica a compras de más de ${money(MIN_TOTAL)}.`;
  return null;
}

// Cuánto descuenta el cupón sobre un subtotal (céntimos de dólar): sin pasar de dejar MIN_TOTAL
export function couponDiscount(c, subtotal) {
  if (!c || !(subtotal > MIN_TOTAL)) return 0;
  const raw = c.kind === 'percent' ? Math.round((subtotal * c.value) / 100) : c.value;
  return Math.max(0, Math.min(raw, subtotal - MIN_TOTAL));
}

/**
 * Reparte un total entre las líneas de una compra en proporción a lo que vale cada una (resto mayor), sin que ninguna
 * quede en 0: así cada producto lleva su parte del descuento y la suma es exactamente el total. amounts: céntimos de
 * cada línea (sin descuento); devuelve los céntimos de cada línea con el descuento.
 */
export function allocate(amounts, total) {
  const sum = amounts.reduce((a, b) => a + b, 0);
  if (!amounts.length) return [];
  if (total >= sum || sum <= 0) return amounts.slice();
  const exact = amounts.map((a) => (a * total) / sum);
  const out = exact.map((x) => Math.floor(x));
  let left = total - out.reduce((a, b) => a + b, 0);
  const order = exact.map((x, i) => [x - Math.floor(x), i]).sort((a, b) => b[0] - a[0] || a[1] - b[1]);
  for (let k = 0; left > 0; k = (k + 1) % order.length, left--) out[order[k][1]]++;
  // Ninguna línea a 0 (las pasarelas no cobran un producto de 0): se le pasa un céntimo de la más cara
  for (let i = 0; i < out.length; i++) {
    if (out[i] > 0 || amounts[i] <= 0) continue;
    const j = out.indexOf(Math.max(...out));
    if (out[j] <= 1) break;
    out[j]--;
    out[i]++;
  }
  return out;
}
