// Integración con Discord: inicio de sesión (OAuth2), roles con el bot y anuncios por webhook.
// Documentación: https://discord.com/developers/docs

const DEFAULT_API = 'https://discord.com/api/v10';
const SNOWFLAKE_RE = /^\d{17,20}$/;

export const isSnowflake = (id) => SNOWFLAKE_RE.test(String(id));

export class Discord {
  constructor({ clientId, clientSecret, botToken, guildId, webhookUrl, redirectUri, apiBase }) {
    this.api = apiBase || DEFAULT_API;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.botToken = botToken;
    this.guildId = guildId;
    this.webhookUrl = webhookUrl;
    this.redirectUri = redirectUri;
  }

  // ¿Se puede vincular la cuenta y dar roles?
  get loginEnabled() {
    return Boolean(this.clientId && this.clientSecret && this.redirectUri);
  }

  get rolesEnabled() {
    return Boolean(this.loginEnabled && this.botToken && this.guildId);
  }

  authorizeUrl(state) {
    const params = new URLSearchParams({
      client_id: this.clientId,
      redirect_uri: this.redirectUri,
      response_type: 'code',
      // guilds.join permite al bot meter al comprador en el Discord si aún no está.
      scope: 'identify guilds.join',
      state,
      prompt: 'none',
    });
    return `https://discord.com/oauth2/authorize?${params}`;
  }

  async request(method, path, { body, auth, reason, form } = {}) {
    const headers = { Authorization: auth };
    if (reason) headers['X-Audit-Log-Reason'] = encodeURIComponent(reason);
    let payload;
    if (form) {
      headers['Content-Type'] = 'application/x-www-form-urlencoded';
      payload = new URLSearchParams(form).toString();
    } else if (body) {
      headers['Content-Type'] = 'application/json';
      payload = JSON.stringify(body);
    }
    const res = await fetch(`${this.api}${path}`, { method, headers, body: payload });
    const data = res.status === 204 ? null : await res.json().catch(() => null);
    if (!res.ok) {
      const err = new Error(`Discord ${method} ${path}: ${data?.message || data?.error_description || res.status}`);
      err.status = res.status;
      throw err;
    }
    return { status: res.status, data };
  }

  // Intercambia el código del OAuth por el token del usuario y lee su perfil.
  async exchangeCode(code) {
    const basic = btoa(`${this.clientId}:${this.clientSecret}`);
    const { data: token } = await this.request('POST', '/oauth2/token', {
      auth: `Basic ${basic}`,
      form: { grant_type: 'authorization_code', code, redirect_uri: this.redirectUri },
    });
    const { data: user } = await this.request('GET', '/users/@me', { auth: `Bearer ${token.access_token}` });
    return {
      id: user.id,
      username: user.global_name || user.username,
      avatar: user.avatar ? `https://cdn.discordapp.com/avatars/${user.id}/${user.avatar}.png?size=64` : null,
      accessToken: token.access_token,
      expiresAt: Date.now() + token.expires_in * 1000,
    };
  }

  // Da los roles al usuario. Si no está en el servidor de Discord, lo añade con esos roles.
  async grantRoles({ userId, accessToken, roleIds, reason }) {
    const bot = `Bot ${this.botToken}`;
    const roles = roleIds.filter(isSnowflake);
    if (!roles.length) return { joined: false, roles: [] };

    if (accessToken) {
      try {
        const res = await this.request('PUT', `/guilds/${this.guildId}/members/${userId}`, {
          auth: bot,
          body: { access_token: accessToken, roles },
        });
        // 201 = lo acabamos de añadir (ya con los roles); 204 = ya era miembro.
        if (res.status === 201) return { joined: true, roles };
      } catch (err) {
        // Token caducado o sin permiso para invitar: seguimos e intentamos solo dar el rol.
        console.warn('No se pudo añadir al usuario al Discord:', err.message);
      }
    }

    for (const roleId of roles) {
      await this.request('PUT', `/guilds/${this.guildId}/members/${userId}/roles/${roleId}`, { auth: bot, reason });
    }
    return { joined: false, roles };
  }

  // Mensaje en el canal de anuncios mediante un webhook de Discord.
  async announce({ content, embed }) {
    if (!this.webhookUrl) return;
    const res = await fetch(this.webhookUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ content, embeds: embed ? [embed] : [], allowed_mentions: { parse: ['users'] } }),
    });
    if (!res.ok) throw new Error(`Webhook de Discord: ${res.status}`);
  }
}

