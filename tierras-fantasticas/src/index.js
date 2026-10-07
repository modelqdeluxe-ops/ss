// Punto de entrada del Worker: la API la responde la app y el resto son los archivos de public/ (o la página 404).
import { createApp } from './app.js';

let cached = null;

export default {
  async fetch(request, env) {
    if (!cached || cached.env !== env) cached = { env, app: createApp(env) };
    const response = await cached.app.handle(request);
    if (response) return response;
    const asset = await env.ASSETS.fetch(request);
    // Dirección que no existe: la página 404 de la web (public/404.html), con su estado 404. Se hace aquí y no con
    // not_found_handling de Cloudflare, que dejaría de llamar al Worker al navegar (/discord, /auth/discord…).
    if (asset.status === 404 && request.method === 'GET') {
      const page = await env.ASSETS.fetch(new Request(new URL('/404', request.url)));
      if (page.ok) return new Response(page.body, { status: 404, headers: page.headers });
    }
    return asset;
  },
};
