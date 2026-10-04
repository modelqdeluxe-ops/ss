// Cookies firmadas con HMAC: guardan la cuenta de Discord vinculada sin base de datos.
const crypto = require('crypto');

function createSession(secret) {
  const key = secret || crypto.randomBytes(32).toString('hex');
  const sign = (value) => crypto.createHmac('sha256', key).update(value).digest('base64url');

  function encode(data) {
    const value = Buffer.from(JSON.stringify(data)).toString('base64url');
    return `${value}.${sign(value)}`;
  }

  function decode(token) {
    if (typeof token !== 'string') return null;
    const [value, mac] = token.split('.');
    if (!value || !mac) return null;
    const expected = sign(value);
    if (mac.length !== expected.length || !crypto.timingSafeEqual(Buffer.from(mac), Buffer.from(expected))) return null;
    try {
      const data = JSON.parse(Buffer.from(value, 'base64url').toString('utf8'));
      if (data.exp && Date.now() > data.exp) return null;
      return data;
    } catch {
      return null;
    }
  }

  return { encode, decode };
}

function parseCookies(header = '') {
  const out = {};
  for (const part of header.split(';')) {
    const i = part.indexOf('=');
    if (i > 0) out[part.slice(0, i).trim()] = decodeURIComponent(part.slice(i + 1).trim());
  }
  return out;
}

function serializeCookie(name, value, { maxAge, secure } = {}) {
  let cookie = `${name}=${encodeURIComponent(value)}; Path=/; HttpOnly; SameSite=Lax`;
  if (maxAge !== undefined) cookie += `; Max-Age=${maxAge}`;
  if (secure) cookie += '; Secure';
  return cookie;
}

module.exports = { createSession, parseCookies, serializeCookie };
