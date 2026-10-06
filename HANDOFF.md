# Traspaso — Tierras Fantásticas (léelo entero antes de tocar nada)

Última actualización: **6 de octubre de 2026**. Repo `modelqdeluxe-ops/ss`, rama de trabajo
`claude/amazing-wozniak-gtw9ll`. Este documento es para que otra IA (o persona) pueda seguir exactamente donde se
quedó el trabajo: qué es el proyecto, qué reglas puso el dueño, **qué estábamos haciendo ahora mismo**, cómo
funciona Cloudflare/Stripe/Discord/el puente con Minecraft y cómo publicar.

---

## 0. Lo que estábamos haciendo AHORA MISMO (empieza por aquí)

**Prioridades actuales (palabras del dueño):** *«lo de Stripe está en stand by, ahora estamos rediseñando la web y
corrigiendo y agregando cosas al mod».* O sea:
1. **Rediseño de la web** (en curso, detalle abajo).
2. **Mod TF Client**: correcciones y cosas nuevas que vaya pidiendo (cada cambio → subir versión y mandarle el jar).
3. **Stripe: en pausa.** No toques la configuración de pagos ni le pidas claves salvo que él lo retome.

**Petición actual del dueño (literal):** *«rediseña la web, tómatelo en serio, mira como están diseñadas de bonitas
en internet.»* Después pidió guardar el avance en git y documentarlo todo (este archivo).

**Estado:** rediseño **a medias**, subido a la rama `claude/amazing-wozniak-gtw9ll` (PR en borrador https://github.com/modelqdeluxe-ops/ss/pull/17, **sin
fusionar**: la web en vivo todavía tiene el diseño anterior). Lo que ya está hecho del rediseño:

1. **Hoja de estilos reescrita desde cero**: `tierras-fantasticas/public/styles.css` (~3.700 líneas). Antes era un
   archivo con capas de parches que se pisaban (por eso el diseño se veía remendado). Ahora tiene un orden claro:
   tokens → base → tipografía → marco → botones → cabecera → héroe → barra de datos → pilares → páginas interiores →
   bloques comunes → pie → tienda (pestañas, rangos, crates, ruleta, tienda de monedas) → componentes que ya
   funcionaban y se conservaron (visor 3D, ventana de compra, cuenta, probador de crates) → móvil y escritorio.
   **No vuelvas a añadir capas de parches al final**: edita la sección que toque.
2. **Lenguaje visual nuevo** (inspirado en webs de juegos de fantasía: Hytale, Wynncraft, Origin Realms):
   - Fondo azul noche `--bg: #0a0c13`. **Grano** muy suave en una capa fija (`body::after`, opacidad 0.6 de un SVG
     de ruido): se pinta una vez, no cuesta al desplazar.
   - **Esquinas biseladas** con `clip-path: var(--chamfer)` y un **filo dorado de 1 px**. Clases `.frame` y `.panel`:
     el filo es el fondo del propio elemento (`--frame-line`) y el relleno va en `::before` (`--frame-bg`).
     El tamaño del bisel es `--cut`. Para resaltar al pasar el ratón se cambia `--frame-line`.
   - **Botones biselados**: `.btn-gold` (placa dorada con bisel inferior), `.btn-ghost` (marco), `.btn-discord`,
     `.btn-theme` (colores de cada crate: `--t1`, `--t2`, `--t3`, que pone `themeAttr()` en `public/app.js`).
     Texto en mayúsculas, Manrope 800.
   - Títulos en **Cinzel**; bajo los títulos centrados, un **ornamento** (línea–rombo–línea, variable `--ornament`).
     Rombos dorados en vez de puntos (listas, normas con números romanos, pasos).
   - Cabecera sólida con un filo dorado degradado debajo; la página actual se marca con un rombo dorado.
   - **Pestañas de la tienda**: barra con marco; la elegida es una placa dorada (2 columnas en móvil, 3 en tablet,
     una fila en escritorio). El dueño quiere ver todas las pestañas en el móvil (sin desplazamiento lateral).
3. **Portada nueva** (`tierras-fantasticas/tools/pages.py` → `index()`), ligera como pidió el dueño:
   héroe con el **emblema** (logo, que no se puede quitar) + IP + «Visitar la tienda» → **barra de datos** montada
   sobre el borde del héroe (jugadores en línea `[data-players]`, Minecraft 1.20.1, +200 mods, nº de crates
   `[data-crate-count]`) → **tres pilares** (Reinos y castillos / Armas legendarias / Oficios y monedas) →
   4 crates → banda final «Tu aventura empieza hoy» (copiar IP, Discord, cómo entrar).
   El arte de los oficios son recortes del pack Medieval Jobs: `public/img/home/oficio-{miner,farmer,blacksmith}.webp`
   (escalados ×5, se ven con la clase `pixel-img`).
4. **Tienda** (`tienda()` en `pages.py`): cabecera con el arte del reino detrás (`.shop-head` + `hero_art()`).
5. En `public/app.js` solo cambió el marcado: las crates de la portada llevan `frame` y la ruleta `frame roulette`.
6. **Animaciones** (todas solo transform/opacity y se apagan con «reducir movimiento»): brillo del color de cada crate
   (solo opacidad), el emblema flota y su halo late. **Solo en escritorio**: el destello del emblema (usa
   `mix-blend-mode`, caro en móvil) y un acercamiento muy lento del arte del héroe (`@keyframes drift`).
7. Comprobado con capturas: portada y tienda (crates, rangos, ruleta) en 1440 px y 390 px, sin desbordes; tests 39/39.

### Lo que falta para terminar el rediseño (en este orden)
1. Revisar con capturas, con calma, las pantallas que aún no se han mirado con el estilo nuevo:
   `/tienda#gratis`, `#monedas`, `#tiendamonedas`, `/mundo`, `/ayuda`, `/cuenta` (sin sesión y con sesión),
   `/success?order=…`, la **ventana de compra** (botón «Comprar») y el **probador de crates** (clic en una crate).
   Las ventanas (`.dialog`, `.viewer`, `.crate-view`) aún tienen esquinas redondeadas: probablemente biselarlas igual.
2. Móvil: comprobar que el título del héroe no se corta (ahora `clamp(32px, 8.6vw, 82px)`), la barra de datos, los
   pilares y las pestañas.
3. Rendimiento en móvil: si el grano o algo se nota lento, quitarlo en `@media (max-width: 959px)`. Nada de
   `backdrop-filter` ni capas grandes animadas.
4. Cuando esté bien: `cd tierras-fantasticas && npm test`, marcar el PR como listo, fusionar (*squash*), esperar el
   despliegue, comprobar la web en vivo (sección 5) y **enseñarle capturas al dueño**.

---

## 1. Qué es el proyecto

Tierras Fantásticas es un servidor de Minecraft Java **1.20.1 Forge** (corre en Mohist, hosting **Ultra Servers**,
IP `216.163.187.40:19001`) con +200 mods. Este repo tiene:

| Carpeta | Qué es |
| --- | --- |
| `tierras-fantasticas/` | La web/tienda. Cloudflare Workers + D1 + archivos estáticos (`public/`). Dominio `tierrasfantásticas.store` = `https://xn--tierrasfantsticas-hpb.store`. |
| `tf-client/` | Mod Forge «TF Client» (va en el cliente y en el servidor): menú y pantalla de carga propios, puente con la web, objetos de los sets (crates), oficios (`/tf jobs`), tienda de monedas, ruleta. Versión actual **1.3.3**. |
| `wrangler.jsonc` | Configuración del Worker de Cloudflare (en la raíz a propósito). |
| `.github/workflows/` | `tf-client.yml` compila el mod en cada push que toque `tf-client/` (artefacto `tfclient-jar`); `server-ping.yml` comprueba el servidor. |

Los demás archivos de la raíz (`.xlsm`, `.apk`, `.docx`, `videos/`) **no son del proyecto**: no los toques ni los subas.

READMEs detallados: `tierras-fantasticas/README.md` (web, Stripe, Discord, puente, productos) y `tf-client/README.md`
(mod, comandos, economía, oficios).

## 2. Reglas del dueño (respétalas siempre)

- Habla con él **en español**, claro y sin rodeos. Es exigente y se enfada (con razón) si se repiten errores: lee bien
  lo que pide, haz exactamente eso y **no añadas cosas que no pidió**.
- «dale, y no me pidas permiso para la otra»: cuando el trabajo esté terminado y probado, **fusionar y publicar sin
  preguntar**. Flujo: PR en borrador → marcarlo listo → *squash merge* → reiniciar la rama:
  `git fetch origin main && git checkout -B claude/amazing-wozniak-gtw9ll origin/main && git push --force-with-lease -u origin claude/amazing-wozniak-gtw9ll`.
  Tras fusionar, esperar el despliegue de Cloudflare y **comprobar la web en vivo**.
- Los mensajes de commit terminan con las líneas de atribución de la sesión (Co-Authored-By / Claude-Session) y las
  descripciones de PR con «🤖 Generated with Claude Code» y el enlace de la sesión.
- **Cada vez que cambie el mod**: subir la versión (siguiente: **1.3.4**) en `tf-client/gradle.properties`
  (`mod_version`) y en `TFClient.VERSION`, compilar y **mandarle el `.jar`** (como archivo adjunto).
- **Nunca** lanzar el juego ni un servidor de Minecraft. El mod se comprueba compilando y simulando (p. ej. la ventana
  de oficios se simuló con PIL usando la textura del cofre de vanilla y el arte real).
- **Secretos** solo en Cloudflare (Secrets), **nunca** en el chat ni en git (lista en la sección 4).
  Stripe va en **modo real** (no quiere modo de prueba; ya se enfadó por eso).
- Comandos del mod: **solo** `/tf web …` (staff) y `/tf jobs`. No añadas más raíces, alias ni comandos para
  jugadores (se quejó dos veces de «un vergo de comandos» y de tener `jobs` y `oficios` a la vez).
- Ventana de oficios: el arte del pack encima de un cofre de 5 filas; **abajo, en el sitio del inventario, solo
  botones** (nunca los objetos del jugador). Está como él quiere en la 1.3.3: no la cambies sin que lo pida.
- Web:
  - Nada de «estrellitas», partículas ni figuritas animadas. El **brillo de color de cada crate** sí (solo opacidad).
  - Que **no vaya lenta en el móvil**.
  - Portada ligera (no meter todo en la primera página) y con el **emblema**.
  - Crates, no «llaves»: se compra el pack (la crate), nunca se habla de llaves; sin etiquetas de rareza ni
    «edición limitada», «más popular», etc.
  - La ruleta se paga con dinero **y** con monedas del servidor, y no debe ser descaradamente pay-to-win (las armas
    legendarias son un 5% por giro).

### Historial de quejas (para no repetirlas)
- 1.3.1: «rediseño horrible», portada sobrecargada, quitaste el logo → se volvió a poner el emblema y se aligeró.
- «no te pedí estrellitas», «el diseño es una mierda», «se laguea en el teléfono» → se quitaron partículas, aurora
  animada y desenfoques (1.3.3).
- «arruinaste la textura de los jobs» → se volvió al arte de la 1.3.0 sobre el cofre, con botones abajo (1.3.3).
- Alas/cascos de las crates mal colocados → se arregló leyendo la configuración de HMCCosmetics de cada pack.
- Ahora: «rediséñala en serio, mira cómo están de bonitas en internet» → rediseño en curso (sección 0).

## 3. Cómo ver y probar la web en local

```bash
cd tierras-fantasticas
npm install                          # una vez
npm test                             # 39 tests (D1 simulada; Stripe/Discord simulados)
node tools/preview.mjs               # http://127.0.0.1:8788 con datos de prueba
PW=$(npm root -g)/playwright node tools/screenshots.cjs /tmp/capturas / /tienda "/tienda#ruleta" /ayuda
```

- `tools/preview.mjs` arranca el Worker real con una D1 en memoria y mete datos de ejemplo: jugador **Notch** con
  cuenta (contraseña `contraseña-segura`), rango Aventurero y una tienda de monedas con 8 objetos.
  Con `COOKIE_FILE=/ruta/cookie.txt` guarda la cookie de sesión de Notch (para ver «Mi cuenta» con sesión iniciada).
- `tools/screenshots.cjs` hace capturas de página completa en escritorio (`d_*.png`, 1440×900) y móvil
  (`m_*.png`, 390×844) e imprime el tamaño: **si el ancho no es 1440 o 390, algo se sale de la pantalla**.
  Las imágenes con `loading="lazy"` pueden salir vacías en la captura (al desplazar cargan: no es un fallo).
- Para parar el servidor: `kill $(lsof -t -i :8788)`. **No uses `pkill -f`** (puede matar tu propia terminal).
- Si cambias el catálogo, reinicia el servidor de vista previa (si no, sirve el `products.json` viejo).
- Las páginas HTML se **generan**: edita `tools/pages.py` y ejecuta `python3 tools/pages.py`. No edites los `.html`
  a mano. Los iconos SVG están en `tools/icons.py`.
- El catálogo de crates/ruleta se genera con `python3 tools/crates.py` → `config/products.json` (premios de la
  ruleta en `ROULETTE_POOL`, precio en monedas `ROULETTE_COIN_PRICE`).
- En este entorno el navegador sin cabeza no carga webs externas con proxy/CA propios (muchas fallan por
  certificado o Cloudflare): para referencias de diseño usa lo que sepas o búsquedas web.

## 4. Cloudflare (cómo se publica la web)

- **Despliegue automático**: el proyecto de Cloudflare (*Workers & Pages → `tierras-fantasticas`*) está conectado
  a este repo de GitHub. **Cada fusión en `main` se publica sola** con `npx wrangler deploy` (sin build command).
  No hay que hacer nada a mano; tarda ~1 minuto.
- `wrangler.jsonc` (raíz): `main` = `tierras-fantasticas/src/index.js`, archivos estáticos en
  `tierras-fantasticas/public` (binding `ASSETS`), base de datos **D1** `tierras-fantasticas` (binding `DB`, se creó
  sola), y variables públicas: `SERVER_NAME`, `SERVER_IP` (`216.163.187.40:19001`), `DISCORD_URL`
  (`https://discord.gg/tRrunHBZE`), `CURRENCY` (`USD`), `DISCORD_GUILD_ID` (`1439703812368765082`).
  `keep_vars: true` para no borrar lo puesto a mano en el panel.
- **Secrets** (panel de Cloudflare → *Workers & Pages → tierras-fantasticas → Settings → Variables and Secrets*,
  tipo *Secret*). Los pone el dueño; tú **nunca** los pidas por chat ni los escribas en el repo:
  - `STRIPE_SECRET_KEY` (`sk_live_…`) y `STRIPE_WEBHOOK_SECRET` (`whsec_…` del webhook en modo Live).
  - `BRIDGE_SECRET`: la clave `bridge.secret` del archivo `config/tfclient-server.properties` del servidor.
  - `DISCORD_CLIENT_ID`, `DISCORD_CLIENT_SECRET`, `DISCORD_BOT_TOKEN`, `DISCORD_WEBHOOK_URL`.
  - `SESSION_SECRET` es opcional (si falta, la web genera una y la guarda en D1).
- Dominio propio: *Settings → Domains & Routes → Custom domain* `tierrasfantásticas.store` (ya hecho).
- **Comprobar que un despliegue llegó** (desde la terminal):
  ```bash
  curl -s "https://xn--tierrasfantsticas-hpb.store/styles.css?x=$RANDOM" | head -5     # ¿el CSS nuevo?
  curl -s -o /dev/null -w "%{http_code}\n" https://xn--tierrasfantsticas-hpb.store/
  curl -s https://xn--tierrasfantsticas-hpb.store/api/status                       # estado del puente
  ```
  Busca en la respuesta algo que solo tenga la versión nueva (un comentario del CSS, una clase nueva…).

### Stripe (pagos, modo real) — EN PAUSA por decisión del dueño
No hay que hacer nada aquí ahora. Se deja documentado para cuando lo retome:
1. Dashboard de Stripe en **Live** → *Developers → API keys* → *Secret key* `sk_live_…` → Secret `STRIPE_SECRET_KEY`.
2. *Developers → Webhooks → Add endpoint* (en Live): `https://xn--tierrasfantsticas-hpb.store/webhook/stripe` con
   los eventos `checkout.session.completed`, `checkout.session.async_payment_succeeded`,
   `checkout.session.async_payment_failed`, `checkout.session.expired`, `charge.refunded`, `charge.dispute.created`.
   Su *Signing secret* `whsec_…` → Secret `STRIPE_WEBHOOK_SECRET`.
3. Los precios salen siempre del catálogo del servidor (`config/products.json`, en céntimos). Pago = página de Stripe
   (Checkout); el webhook firmado confirma y deja la entrega en la cola del puente.

### Discord
Aplicación en <https://discord.com/developers/applications>: OAuth2 (Client ID/Secret → Secrets; redirect
`https://xn--tierrasfantsticas-hpb.store/auth/discord/callback`), bot (token → `DISCORD_BOT_TOKEN`, permisos
*Gestionar roles* + *Crear invitación*, su rol por encima de los rangos), webhook de anuncios → `DISCORD_WEBHOOK_URL`.
Detalles paso a paso en `tierras-fantasticas/README.md`, sección «3. Discord».

### Puente con el servidor de Minecraft
- El TF Client del **servidor** llama cada 10 s a `POST /bridge/poll` (protocolo 2) con `Authorization: Bearer <BRIDGE_SECRET>`.
  Manda jugadores conectados/vistos, confirmaciones de entregas (con `prizes` para la ruleta), cambios de rango del
  staff y cambios de la tienda de monedas. Recibe entregas pendientes (comandos con `{player}`/`{uuid}` y campos
  `kind`/`color`), rangos, la configuración de la ruleta y la tienda de monedas.
- En el servidor: `config/tfclient-server.properties` (`bridge.url`, `bridge.secret`, `economy.mode` =
  `auto|vault|tf|comandos`, `economy.currency`). En consola debe salir `TF Bridge: conectado con la web`.
- Pedidos con monedas (ruleta y tienda de monedas) se crean en la web con id `COIN…` e importe 0; el mod cobra las
  monedas al ejecutar `tf web ruleta girar {player} N` / `tf web tienda comprar {player} <id>` y, si no tiene
  bastantes, devuelve el error y la web lo enseña.

## 5. El mod (TF Client 1.3.3)

- Compilar: `cd tf-client && JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew build --no-daemon -q -Porg.gradle.java.installations.paths=$JAVA_HOME`
  → `build/libs/tfclient-1.20.1-1.3.3.jar`. Va en `mods/` del juego **y** del servidor, misma versión.
- Comandos (todos en `items/TFCommands.java`):
  - `/tf jobs` (todos; staff: `recargar`, `nivel`, `xp`, `reiniciar`; `ver <oficio>` lo usan los avisos del chat).
  - `/tf web sets list|give`, `/tf web tienda add|precio|quitar|lista|vaciar` (staff) y los que usa la web:
    `/tf web rango`, `/tf web monedas ver|dar|quitar|poner`, `/tf web ruleta girar`, `/tf web tienda comprar`.
- Código: `server/TFBridge.java` (puente), `shop/TFCoinShop.java`, `shop/TFRoulette.java`, `economy/` (Vault por
  reflexión en Mohist, monedas propias o comandos), `jobs/` (oficios, config `config/tfclient-jobs.json`),
  `menu/TFPanelMenu.java` + `client/TFPanelScreen.java` (ventana de oficios: 10 huecos de la rejilla en los huecos
  29-33/38-42 del cofre y 36 botones abajo; nunca el inventario del jugador).
- Herramientas: `tools/build_mod_items.py <packs>` (modelos/texturas de los packs; usa `hmc_worn.py` para lo que va en
  la espalda/cabeza según HMCCosmetics), `tools/check_mod_items.py`, `tools/build_jobs_gui.py <packs>` (arte de oficios
  sin los textos en inglés). Los packs descomprimidos no están en el repo (los subió el dueño en zips).

## 6. Mapa rápido de la web

- Servidor: `tierras-fantasticas/src/app.js` (rutas: productos, Stripe, `/bridge/poll`, ruleta `POST /api/roulette/coins`,
  tienda de monedas `GET /api/coinshop` y `POST /api/coinshop/buy`, cuentas y Discord), `src/store.js` (D1),
  `src/stripe.js`, `src/discord.js`, `src/session.js`.
- Cliente: `public/app.js` (cabecera, estado del servidor, tienda y pestañas, crates, ruleta, tienda de monedas,
  ventana de compra, cuenta), `public/viewer.js` y `public/wardrobe.js` (visor 3D y probador de crates con el
  personaje), `public/success.js` (página del pedido).
- Tests: `tierras-fantasticas/test/app.test.js`.
