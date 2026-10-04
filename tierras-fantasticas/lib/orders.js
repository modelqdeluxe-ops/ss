// Registro de pedidos en un archivo JSON. Suficiente para un servidor pequeño;
// para mucho volumen conviene migrar a una base de datos.
const fs = require('fs');
const path = require('path');

const FILE = process.env.ORDERS_FILE || path.join(__dirname, '..', 'data', 'orders.json');

let orders = {};
try {
  orders = JSON.parse(fs.readFileSync(FILE, 'utf8'));
} catch {
  orders = {};
}

// Escritura serializada para no corromper el archivo con webhooks simultáneos.
let writing = Promise.resolve();
function persist() {
  const snapshot = JSON.stringify(orders, null, 2);
  writing = writing.then(async () => {
    await fs.promises.mkdir(path.dirname(FILE), { recursive: true });
    const tmp = `${FILE}.tmp`;
    await fs.promises.writeFile(tmp, snapshot);
    await fs.promises.rename(tmp, FILE);
  });
  return writing;
}

function get(id) {
  return orders[id];
}

async function upsert(id, fields) {
  orders[id] = { ...orders[id], ...fields, updatedAt: new Date().toISOString() };
  await persist();
  return orders[id];
}

// Estados en los que el pedido ya se pagó y se intentó entregar.
const CLAIMED = ['delivering', 'delivered', 'manual', 'delivery_failed'];

function isClaimed(id) {
  return CLAIMED.includes(orders[id]?.status);
}

// Marca el pedido como "en entrega" solo si nadie lo ha reclamado antes (idempotencia
// frente a capturas repetidas y reintentos del webhook de PayPal).
async function claimForDelivery(id, fields) {
  if (isClaimed(id)) return false;
  await upsert(id, { ...fields, status: 'delivering' });
  return true;
}

module.exports = { get, upsert, claimForDelivery, isClaimed };
