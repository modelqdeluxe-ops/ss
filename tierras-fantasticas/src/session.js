// Cookies firmadas con HMAC-SHA256 (WebCrypto): guardan la cuenta de Discord vinculada sin base de datos.
const enc = new TextEncoder();
const dec = new TextDecoder();

export function toBase64Url(bytes) {
  let bin = '';
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function fromBase64Url(text) {
  const bin = atob(text.replace(/-/g, '+').replace(/_/g, '/'));
  return Uint8Array.from(bin, (c) => c.charCodeAt(0));
}

export function randomHex(bytes = 16) {
  return [...crypto.getRandomValues(new Uint8Array(bytes))].map((b) => b.toString(16).padStart(2, '0')).join('');
}

// Comparación en tiempo constante de dos textos.
export function safeEqual(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string') return false;
  const x = enc.encode(a);
  const y = enc.encode(b);
  let diff = x.length ^ y.length;
  for (let i = 0; i < Math.max(x.length, y.length); i++) diff |= (x[i] || 0) ^ (y[i] || 0);
  return diff === 0;
}

export async function createSession(secret) {
  const key = await crypto.subtle.importKey('raw', enc.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  const sign = async (value) => toBase64Url(new Uint8Array(await crypto.subtle.sign('HMAC', key, enc.encode(value))));

  async function encode(data) {
    const value = toBase64Url(enc.encode(JSON.stringify(data)));
    return `${value}.${await sign(value)}`;
  }

  async function decode(token) {
    if (typeof token !== 'string') return null;
    const [value, mac] = token.split('.');
    if (!value || !mac || !safeEqual(mac, await sign(value))) return null;
    try {
      const data = JSON.parse(dec.decode(fromBase64Url(value)));
      if (data.exp && Date.now() > data.exp) return null;
      return data;
    } catch {
      return null;
    }
  }

  return { encode, decode };
}

export function parseCookies(header = '') {
  const out = {};
  for (const part of (header || '').split(';')) {
    const i = part.indexOf('=');
    if (i > 0) {
      try {
        out[part.slice(0, i).trim()] = decodeURIComponent(part.slice(i + 1).trim());
      } catch {
        /* cookie mal formada */
      }
    }
  }
  return out;
}

export function serializeCookie(name, value, { maxAge, secure } = {}) {
  let cookie = `${name}=${encodeURIComponent(value)}; Path=/; HttpOnly; SameSite=Lax`;
  if (maxAge !== undefined) cookie += `; Max-Age=${maxAge}`;
  if (secure) cookie += '; Secure';
  return cookie;
}
