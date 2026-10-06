# Traspaso — Tierras Fantásticas (léelo entero antes de tocar nada)

Última actualización: **6 de octubre de 2026** (tarde). Repo `modelqdeluxe-ops/ss`, rama de trabajo
`claude/amazing-wozniak-gtw9ll`. Este documento es para que otra IA (o persona) pueda seguir exactamente donde se
quedó el trabajo: qué es el proyecto, qué reglas puso el dueño, **qué estábamos haciendo ahora mismo**, cómo
funciona Cloudflare/Stripe/Discord/el puente con Minecraft y cómo publicar.

---

## 0. Lo que estábamos haciendo AHORA MISMO (empieza por aquí)

**Prioridades del dueño:** 1) la web (diseño y ahora también **lo legal**), 2) el mod TF Client (cambios que vaya
pidiendo: cada uno sube versión y se le manda el `.jar`), 3) **Stripe en pausa**: no lo toques.

### Última entrega (octubre de 2026): rediseño completo + cumplimiento de Mojang + páginas legales + TF Client 1.3.4 → 1.3.5
El dueño rechazó el estilo «fantasía» (dorado, Cinzel, biseles): *«no veo un rediseño completo… todo está crudo…
rediseña todo con otro concepto… es minecraft, la tipografía cámbiala pero no a una de píxeles… animaciones en los
botones, transiciones, loading»*. Y después: *«revisa que nuestros productos no incumplan los términos de Mojang…
aclarar que no estamos afiliados… todo el tema legal, políticas de privacidad… nada debe estar con cabos sueltos»*.

**Diseño nuevo («Minecraft moderno»)** — `public/styles.css` reescrito desde cero (orden: tokens → base → carga →
botones → cabecera → portada → bloques comunes → páginas legales → pie → tienda → ventanas → cuenta → probador →
animaciones → tamaños de pantalla). Edita la sección que toque; no añadas parches al final.
- Paleta: noche `--bg #0a0d14`, superficies `--s1/--s2/--s3`, **esmeralda** (`--g-*`, acción principal),
  **amatista** (`--violet`), oro solo para monedas. Tipografías **Unbounded** (titulares, botones, precios) y
  **Figtree** (texto), de Google Fonts.
- Botones `.btn` con volumen de bloque: labio inferior (`--lip`, `--lift`), se elevan al pasar el ratón con un
  destello que los cruza y se hunden al pulsar. Variantes: `btn-primary` (verde), `btn-ghost`, `btn-discord`,
  `btn-coin` (tienda de monedas), `btn-theme` (color de cada crate, `--t1/--t2/--t3`), tamaños `btn-sm`/`btn-lg`.
  `.is-loading` pone una rueda delante del texto (se usa al reclamar, pagar, entrar y comprar con monedas).
- Tarjetas `.panel`/`.frame` con borde suave, sombra y una franja de color arriba (crates y productos); se elevan
  al pasar el ratón. Los rangos usan el color de su prefijo (`rankAttr` en app.js).
- **Pantalla de carga** solo en la primera página de la visita (script `BOOT` en `pages.py`, `sessionStorage`
  `tf-seen`, mínimo 0,7 s y máximo 3 s); en las siguientes, **transición entre páginas** con `@view-transition`.
  El héroe y las cabeceras entran escalonados cuando la página está lista (clase `ready` en `<html>`).
- **Esqueletos** con brillo mientras carga la tienda, la portada y la cuenta (`skeletons()` en pages.py); las
  imágenes aparecen con un fundido al cargar (clase `fx` + `ok`, en `img()` de app.js); las cifras de la portada
  suben desde 0; las barras, pestañas, preguntas frecuentes (se abren con transición de altura) y el menú del móvil
  están animados. Todo con transform/opacity y apagado con «reducir movimiento». Sin backdrop-filter ni capas fijas.
- Se quitó el grano fijo de fondo. El brillo de color de las crates se mantiene (el dueño lo quiere).

**Normas de Mojang** (EULA + *Minecraft Usage Guidelines*). Revisado producto por producto y corregido con el visto
bueno del dueño, **salvo los atributos de las crates** (ver abajo):
- **Ruleta retirada** de la web (lo que se parezca al juego de azar está prohibido, y daba objetos con ventaja). El
  dueño preguntó si con monedas ganadas jugando estaría permitida: Mojang prohíbe en general «anything meant to
  resemble gambling mechanics», así que se dejó fuera; el código sigue (ver README) por si se rehace de forma permitida.
- **Monedas con dinero retiradas** (las monedas solo se ganan jugando; con dinero comprarían diamantes, netherita o
  élitros en la tienda de monedas = ventaja). La tienda de monedas sigue, pagada con monedas del juego.
- **Rangos sin ventajas**: fuera kits de objetos, el «acceso al mundo de recursos» (no se puede cobrar por partes del
  servidor) y /ec. Quedan prefijo, hogares, /fly en el lobby, /hat y partículas, mascota cosmética, /nick, cola
  prioritaria, título y color, rol de Discord. **El dueño tiene que quitar en LuckPerms/EssentialsX los kits y /ec de
  los rangos y abrir el mundo de recursos a todos** (se le dijo). Quiere que los rangos den **un set de armadura**: se
  puede si es de aspecto (valores de hierro, como las crates). **Pendiente: que diga qué set lleva cada rango.**
- **Atributos de las crates — DECISIÓN DEL DUEÑO (contra la recomendación):** la 1.3.4 los bajó a hierro para
  cumplir; el dueño pidió (*«ponles atributos 1 punto mayor a la netherita… no vamos a decir qué atributos tiene…
  mojang no se va a meter»*) y la **1.3.5** les pone la netherita +1 (+1 de daño; armadura 4/9/7/4, dureza 4,
  empuje 0,2; no se queman en lava) y oculta los atributos en la descripción del objeto (la barra de armadura del
  juego sí los refleja). Se le explicó que eso es la ventaja pagada que prohíbe Mojang y que puede acabar en bloqueo
  del servidor; es su decisión y su riesgo. **La web no puede decir nada falso**: se quitaron «valores de hierro»,
  «no dan ventaja» y «cumplimos las normas de Mojang»; la web simplemente no habla de atributos. No vuelvas a poner
  esas frases mientras los atributos sean estos. El contenido de las crates sigue siendo fijo y visible (no son cajas
  al azar).
- «Donaciones» → «compras» (Mojang exige llamar a las cosas por su nombre).

**Páginas legales** (textos en `tools/legal.py`, generadas por `pages.py`; fecha en `LEGAL_DATE`):
- `/legal` — quiénes somos, **no afiliación** (frase obligatoria en español e inglés), cómo funciona la tienda
  (sin azar, monedas solo jugando, sin desbaneos), propiedad intelectual. `/terminos` — cuentas, menores, qué se compra, precios en USD finales, entrega, **5 días
  hábiles para cancelar** (art. 56 LFPC) y reembolsos, cambios del servidor, PROFECO. `/privacidad` — aviso de
  privacidad según la **LFPDPPP de 2025** (datos, finalidades necesarias y voluntaria, terceros, derechos ARCO en 20
  días hábiles, SABG como autoridad, cookies y almacenamiento).
- Responsable: **Equipo de Tierras Fantásticas**, México. Contacto público (sin registro, lo exige Mojang):
  **tierrasfantasticasmc@gmail.com**. **Pendiente del dueño**: la ley pide un domicilio en el aviso de privacidad;
  ahora pone «con domicilio en México». Si da uno (puede ser un domicilio para notificaciones), añadirlo en
  `PRIVACIDAD['responsable']` de `tools/legal.py`.
- Si cambia lo que hace la web (datos que guarda, servicios externos como mc-heads.net, mcsrvstat.us, Google Fonts,
  Stripe, Discord, Cloudflare) hay que actualizar `tools/legal.py` y `LEGAL_DATE`.
- Aviso de no afiliación en el pie de todas las páginas, bajo la tienda y en la ventana de compra (con la aceptación
  de los términos). En «Mis compras» y en la página del pedido, lo gratis sale como «Gratis» y lo de monedas como
  monedas (antes «$0.00»).

## 1. Qué es el proyecto

Tierras Fantásticas es un servidor de Minecraft Java **1.20.1 Forge** (corre en Mohist, hosting **Ultra Servers**,
IP `216.163.187.40:19001`) con +200 mods. Este repo tiene:

| Carpeta | Qué es |
| --- | --- |
| `tierras-fantasticas/` | La web/tienda. Cloudflare Workers + D1 + archivos estáticos (`public/`). Dominio `tierrasfantásticas.store` = `https://xn--tierrasfantsticas-hpb.store`. |
| `tf-client/` | Mod Forge «TF Client» (va en el cliente y en el servidor): menú y pantalla de carga propios, puente con la web, objetos de los sets (crates), oficios (`/tf jobs`), tienda de monedas, ruleta (la de la web está retirada). Versión actual **1.3.5**. |
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
- **Cada vez que cambie el mod**: subir la versión (siguiente: **1.3.6**) en `tf-client/gradle.properties`
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
  - Diseño actual: concepto «Minecraft moderno» (sección 0). Tipografías Unbounded + Figtree, **nunca** una de píxeles.
  - **Normas de Mojang (para productos nuevos):** nada que dé ventaja sobre quien no paga (kits de objetos no; los sets
    de las crates son la excepción que decidió el dueño, ver sección 0), nada al azar ni parecido al juego de azar
    (sin ruletas, llaves ni cajas sorpresa), no vender monedas del juego con dinero, no cobrar por zonas del servidor,
    ni desbaneos ni herramientas del staff, contenido para todas las edades, llamar «compras» a las compras.
  - Aviso de no afiliación con Mojang/Microsoft siempre visible y páginas legales al día (`tools/legal.py`).

### Historial de quejas (para no repetirlas)
- 1.3.1: «rediseño horrible», portada sobrecargada, quitaste el logo → se volvió a poner el emblema y se aligeró.
- «no te pedí estrellitas», «el diseño es una mierda», «se laguea en el teléfono» → se quitaron partículas, aurora
  animada y desenfoques (1.3.3).
- «arruinaste la textura de los jobs» → se volvió al arte de la 1.3.0 sobre el cofre, con botones abajo (1.3.3).
- Alas/cascos de las crates mal colocados → se arregló leyendo la configuración de HMCCosmetics de cada pack.
- «rediséñala en serio, mira cómo están de bonitas en internet» → estilo fantasía (dorado, Cinzel, biseles): lo
  rechazó: «todo está crudo… rediseña todo con otro concepto… es minecraft» → concepto «Minecraft moderno» (sección 0).
- «revisa que nuestros productos no incumplan los términos de Mojang… todo el tema legal» → sección 0.

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

## 5. El mod (TF Client 1.3.5)

- Compilar: `cd tf-client && JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew build --no-daemon -q -Porg.gradle.java.installations.paths=$JAVA_HOME`
  → `build/libs/tfclient-1.20.1-1.3.5.jar` (va en `mods/` del juego **y** del servidor, misma versión). Si el contenedor solo tiene Java 21 (pasó en octubre de 2026), basta
  `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew build --no-daemon -q`: Gradle descarga el JDK 17 solo.
- Objetos de los sets (`items/TFItems.java` → `SET_TIER`, `TFItemTypes.Material`): netherita **+1** desde la 1.3.5
  (decisión del dueño, ver sección 0), con los atributos ocultos en la descripción (`HIDE_ATTRIBUTES`).
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
