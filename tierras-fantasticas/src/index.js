// Punto de entrada del Worker: la API la responde la app y el resto son los archivos de public/.
import { createApp } from './app.js';

let cached = null;

export default {
  async fetch(request, env) {
    if (!cached || cached.env !== env) cached = { env, app: createApp(env) };
    const response = await cached.app.handle(request);
    return response ?? env.ASSETS.fetch(request);
  },
};
