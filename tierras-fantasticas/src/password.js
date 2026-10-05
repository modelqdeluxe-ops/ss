// Contraseñas de las cuentas de la web: PBKDF2-SHA256 con sal aleatoria (WebCrypto, sin dependencias).
// Se guarda "pbkdf2$iteraciones$sal$hash"; nunca la contraseña.
import { toBase64Url, fromBase64Url, safeEqual } from './session.js';

// Cloudflare Workers admite como máximo 100 000 iteraciones de PBKDF2.
const ITERATIONS = 100000;
const enc = new TextEncoder();

async function derive(password, salt, iterations) {
  const key = await crypto.subtle.importKey('raw', enc.encode(password), 'PBKDF2', false, ['deriveBits']);
  const bits = await crypto.subtle.deriveBits({ name: 'PBKDF2', hash: 'SHA-256', salt, iterations }, key, 256);
  return new Uint8Array(bits);
}

export async function hashPassword(password) {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const hash = await derive(password, salt, ITERATIONS);
  return `pbkdf2$${ITERATIONS}$${toBase64Url(salt)}$${toBase64Url(hash)}`;
}

export async function verifyPassword(password, stored) {
  const [kind, iterations, salt, hash] = String(stored || '').split('$');
  if (kind !== 'pbkdf2' || !iterations || !salt || !hash) return false;
  const computed = await derive(password, fromBase64Url(salt), Number(iterations));
  return safeEqual(toBase64Url(computed), hash);
}
