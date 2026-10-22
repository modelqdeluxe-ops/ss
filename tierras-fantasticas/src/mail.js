// Copias por correo para el dueño (registros de aceptación de los Términos, recibos de compra y avisos de reembolso o
// disputa), con Resend (https://resend.com). Se configura con dos Secrets de Cloudflare:
//   RESEND_API_KEY  la clave de la API de Resend
//   RECORDS_EMAIL   a dónde llegan las copias (el correo del dueño; no va en el repositorio, que es público)
// y, opcional, MAIL_FROM (p. ej. «Tierras Fantásticas <registros@tu-dominio>», con el dominio verificado en Resend).
// Sin MAIL_FROM se usa el remitente de pruebas de Resend, que solo puede escribir al correo con el que se creó la
// cuenta de Resend: créala con el mismo correo de RECORDS_EMAIL.
// Si falta la configuración no se envía nada (los registros siguen en la base de datos D1) y nunca rompe una compra.

const ENDPOINT = 'https://api.resend.com/emails';

export function createMailer(env, log = console) {
  const key = env.RESEND_API_KEY;
  const to = env.RECORDS_EMAIL;
  const from = env.MAIL_FROM || 'Tierras Fantásticas <onboarding@resend.dev>';
  const enabled = Boolean(key && to);
  const fetcher = env.MAIL_FETCH || fetch;

  // attachments: [{ filename, content (base64) }]
  async function send({ subject, text, html, attachments }) {
    if (!enabled) return false;
    try {
      const res = await fetcher(ENDPOINT, {
        method: 'POST',
        headers: { Authorization: `Bearer ${key}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ from, to: [to], subject, text, html, attachments }),
      });
      if (!res.ok) {
        log.error(`Correo «${subject}» no enviado: ${res.status} ${(await res.text()).slice(0, 300)}`);
        return false;
      }
      return true;
    } catch (err) {
      log.error(`Correo «${subject}» no enviado:`, err.message);
      return false;
    }
  }

  return { enabled, send };
}

const esc = (s) => String(s ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);

// Tabla «campo: valor» en texto y en HTML
export function fieldsText(rows) {
  return rows.map(([k, v]) => `${k}: ${v ?? '—'}`).join('\n');
}

export function fieldsHtml(rows) {
  return `<table style="border-collapse:collapse;font:14px/1.5 Arial,sans-serif">${rows
    .map(
      ([k, v]) =>
        `<tr><td style="padding:4px 12px 4px 0;color:#6b5a4b;vertical-align:top;white-space:nowrap"><b>${esc(k)}</b></td><td style="padding:4px 0">${esc(v ?? '—')}</td></tr>`,
    )
    .join('')}</table>`;
}

export function mailHtml(title, intro, rows, outro = '') {
  return `<div style="font:14px/1.5 Arial,sans-serif;color:#2a1d14"><h2 style="margin:0 0 8px">${esc(title)}</h2><p>${esc(intro)}</p>${fieldsHtml(rows)}${outro ? `<p style="margin-top:16px;color:#4f4137">${esc(outro)}</p>` : ''}</div>`;
}
