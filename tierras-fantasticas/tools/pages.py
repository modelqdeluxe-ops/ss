import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from icons import I

# Genera las páginas HTML de public/ (cabecera, pie y secciones comunes). Uso: python3 tools/pages.py
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'public')
IP = '216.163.187.40:19001'
NAV = [('inicio', '/', 'Inicio'), ('tienda', '/tienda', 'Tienda'),
       ('mundo', '/mundo', 'El mundo'), ('ayuda', '/ayuda', 'Ayuda')]
# Solo en el menú del móvil (en escritorio está el botón de la cuenta en la cabecera).
SHEET_EXTRA = [('cuenta', '/cuenta', 'Mi cuenta')]

FONTS = ('<link rel="preconnect" href="https://fonts.googleapis.com">\n'
         '  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>\n'
         '  <link href="https://fonts.googleapis.com/css2?family=Cinzel:wght@600;700&family=Manrope:wght@400;500;600;700;800&display=swap" rel="stylesheet">')


def head(title, desc, page, hero=False, preload_hero=False):
    preload = ('\n  <link rel="preload" as="image" href="/img/hero-1920.webp" '
               'imagesrcset="/img/hero-768.webp 768w, /img/hero-1280.webp 1280w, /img/hero-1920.webp 1920w" imagesizes="100vw">'
               if preload_hero else '')
    return f'''<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
  <title>{title}</title>
  <meta name="description" content="{desc}">
  <meta name="theme-color" content="#07080d">
  <meta property="og:title" content="{title}">
  <meta property="og:description" content="{desc}">
  <meta property="og:image" content="/img/og.jpg">
  <meta property="og:type" content="website">
  <meta name="twitter:card" content="summary_large_image">
  {FONTS}{preload}
  <link rel="stylesheet" href="/styles.css">
  <link rel="icon" type="image/webp" href="/img/logo.webp">
  <script>document.documentElement.classList.add('js')</script>
</head>
<body data-page="{page}"{' data-hero' if hero else ''}>
  <div class="aurora" aria-hidden="true"><span></span><span></span><span></span></div>
'''


def header(page):
    cur = ' aria-current="page"'
    new = '<span class="tag">Nuevo</span>'
    links = '\n'.join(
        f'        <a href="{href}"{cur if key == page else ""}>{label}</a>'
        for key, href, label in NAV)
    arrow = I['arrow']
    sheet_links = '\n'.join(
        f'      <a href="{href}"{cur if key == page else ""}>{label}{arrow}</a>'
        for key, href, label in NAV + SHEET_EXTRA)
    return f'''  <a class="sr-only" href="#main">Saltar al contenido</a>
  <header class="header" id="header">
    <div class="wrap header-inner">
      <a href="/" class="brand" aria-label="Tierras Fantásticas, inicio">
        <img src="/img/logo.webp" alt="" width="34" height="38">
        <span>Tierras Fantásticas</span>
      </a>
      <nav class="nav" aria-label="Secciones">
{links}
      </nav>
      <div class="header-actions">
        <button class="ip-chip" type="button" data-copy-ip aria-label="Copiar la IP del servidor">
          <span class="dot" data-status-dot></span><span data-server-ip>{IP}</span><span class="hint" data-copy-hint>Copiar</span>
        </button>
        <a href="/tienda" class="btn btn-gold btn-sm">Tienda</a>
        <a href="/cuenta" class="account-chip" data-account-chip aria-label="Entrar o crear cuenta">{I['users']}<span>Entrar</span></a>
        <button class="menu-btn" type="button" data-menu-open aria-label="Abrir menú" aria-expanded="false" aria-controls="sheet">{I['menu']}</button>
      </div>
    </div>
  </header>

  <div class="sheet" id="sheet" aria-label="Menú" aria-hidden="true">
    <div class="sheet-top">
      <a href="/" class="brand"><img src="/img/logo.webp" alt="" width="34" height="38"><span>Tierras Fantásticas</span></a>
      <button class="menu-btn" type="button" data-menu-close aria-label="Cerrar menú">{I['x']}</button>
    </div>
    <nav aria-label="Secciones">
{sheet_links}
    </nav>
    <div class="sheet-foot">
      <button class="btn btn-ghost btn-lg ip-button" type="button" data-copy-ip>
        <span class="ip" data-server-ip>{IP}</span><span class="copy" data-copy-hint>Copiar IP</span>
      </button>
      <a href="#" class="btn btn-discord btn-lg discord-link" target="_blank" rel="noopener" hidden>{I['discord']}Únete al Discord</a>
    </div>
  </div>
'''


def footer():
    return f'''
  <footer class="footer">
    <div class="wrap footer-inner">
      <div class="footer-brand">
        <img src="/img/logo.webp" alt="" width="48" height="53" loading="lazy">
        <div>
          <b data-server-name>Tierras Fantásticas</b>
          <p>Servidor de Minecraft de aventura, castillos y magia.</p>
          <p>IP: <span data-server-ip>{IP}</span></p>
        </div>
      </div>
      <div class="footer-cols">
        <div>
          <h4>Servidor</h4>
          <ul>
            <li><a href="/">Inicio</a></li>
            <li><a href="/mundo">El mundo</a></li>
            <li><a href="/mundo#normas">Normas</a></li>
          </ul>
        </div>
        <div>
          <h4>Tienda</h4>
          <ul>
            <li><a href="/tienda#rangos">Rangos</a></li>
            <li><a href="/tienda#crates">Crates</a></li>
            <li><a href="/tienda#ruleta">Ruleta</a></li>
            <li><a href="/tienda#tiendamonedas">Tienda de monedas</a></li>
          </ul>
        </div>
        <div>
          <h4>Ayuda</h4>
          <ul>
            <li><a href="/ayuda">Preguntas frecuentes</a></li>
            <li><a href="#" class="discord-link" target="_blank" rel="noopener" hidden>Discord</a></li>
          </ul>
        </div>
      </div>
    </div>
    <div class="wrap legal">
      <span>© <span data-year></span> Tierras Fantásticas. No afiliado a Mojang AB ni a Microsoft.</span>
      <span>Las compras son donaciones voluntarias para mantener el servidor.</span>
    </div>
  </footer>

  <script src="/app.js" defer></script>
</body>
</html>
'''


def hero_art(cls='hero-art'):
    return f'''<div class="{cls}" aria-hidden="true">
        <img src="/img/hero-1920.webp" srcset="/img/hero-768.webp 768w, /img/hero-1280.webp 1280w, /img/hero-1920.webp 1920w" sizes="100vw" alt="" width="1920" height="1080" fetchpriority="high">
      </div>'''


def page_hero(eyebrow, title, text):
    return f'''    <section class="page-hero">
      {hero_art()}
      <div class="wrap">
        <span class="eyebrow">{eyebrow}</span>
        <h1>{title}</h1>
        <p>{text}</p>
      </div>
    </section>
'''


def feature(icon, title, text):
    return f'''        <article class="panel feature reveal">
          <span class="icon">{I[icon]}</span>
          <h3>{title}</h3>
          <p>{text}</p>
        </article>'''


def step(title, text):
    return f'''        <article class="panel step reveal">
          <h3>{title}</h3>
          <p>{text}</p>
        </article>'''


DISCORD_BAND = f'''    <section class="wrap section">
      <div class="panel cta-band reveal">
        <span class="eyebrow">Comunidad</span>
        <h2 style="margin-top:14px">Únete al Discord del reino</h2>
        <p>Novedades, eventos, sorteos, el modpack y soporte del staff. Aquí empieza todo.</p>
        <div class="actions">
          <a href="#" class="btn btn-discord btn-lg discord-link" target="_blank" rel="noopener" hidden>{I['discord']}Entrar al Discord</a>
          <a href="/ayuda" class="btn btn-ghost btn-lg">Ver ayuda</a>
        </div>
      </div>
    </section>
'''

JOIN_STEPS = '\n'.join([
    step('Prepara el modpack', 'Minecraft Java 1.20.1 con Forge y el modpack de Tierras Fantásticas, que incluye el TF Client. Lo tienes en nuestro Discord.'),
    step('Pulsa «Tierras Fantásticas»', 'Desde el menú del TF Client entras con un clic. Antes de conectar revisa tus mods y te dice si te falta alguno.'),
    step('Empieza tu aventura', f'Funda tu reino, explora mazmorras y forja alianzas. ¿Prefieres entrar a mano? La IP es {IP}.'),
])


def index():
    return head('Tierras Fantásticas — Servidor de Minecraft',
                'Tierras Fantásticas: servidor de Minecraft de aventura con castillos, reinos y mazmorras. Más de 200 mods, sets animados y una comunidad activa.',
                'inicio', hero=True, preload_hero=True) + header('inicio') + f'''
  <main id="main">
    <section class="home-hero">
      {hero_art('home-hero-art')}
      <div class="wrap home-hero-grid">
        <div class="home-hero-copy">
          <span class="pill"><span class="dot" data-status-dot></span><span data-status-text>Comprobando el servidor…</span></span>
          <h1><span class="title-sheen" data-server-name>Tierras Fantásticas</span></h1>
          <p class="lead">Un reino de castillos, magia y mazmorras en Minecraft Java 1.20.1. Construye tu fortaleza, forja alianzas y conquista tierras que nadie ha pisado.</p>
          <div class="home-hero-cta">
            <button class="ip-card" type="button" data-copy-ip aria-label="Copiar la IP del servidor">
              <span class="ip-label">IP del servidor</span>
              <span class="ip" data-server-ip>{IP}</span>
              <span class="ip-copy" data-copy-hint>Copiar</span>
            </button>
            <a href="/tienda" class="btn btn-gold btn-lg">Visitar la tienda {I['arrow']}</a>
          </div>
          <ul class="hero-facts">
            <li><strong data-players>—</strong><span>jugando ahora</span></li>
            <li><strong>200+</strong><span>mods</span></li>
            <li><strong data-crate-count>40</strong><span>sets animados</span></li>
            <li><strong>0%</strong><span>pay-to-win</span></li>
          </ul>
        </div>
        <a class="showcase" id="showcase" href="/tienda#crates" aria-label="Ver las crates">
          <span class="showcase-glow" aria-hidden="true"></span>
          <span class="showcase-ring" aria-hidden="true"></span>
          <img class="showcase-img" src="/img/crates/ifrit.webp" alt="" width="600" height="400">
          <span class="showcase-info">
            <span class="showcase-kicker">Crate destacada</span>
            <b class="showcase-name">Ifrit</b>
            <span class="showcase-meta"></span>
          </span>
          <span class="showcase-dots" aria-hidden="true"></span>
        </a>
      </div>
    </section>

    <section class="wrap section">
      <div class="section-head center">
        <span class="eyebrow">Cómo jugar</span>
        <h2>Dentro en tres pasos</h2>
        <p>Es gratis. Solo necesitas Minecraft Java y el modpack del servidor.</p>
      </div>
      <ol class="timeline">
        <li class="reveal"><span class="num">1</span><h3>Prepara el modpack</h3><p>Minecraft Java 1.20.1 con Forge y el modpack de Tierras Fantásticas, que incluye el TF Client. Lo tienes en nuestro Discord.</p></li>
        <li class="reveal"><span class="num">2</span><h3>Pulsa «Tierras Fantásticas»</h3><p>Desde el menú del TF Client entras con un clic. Antes de conectar revisa tus mods y te dice si te falta alguno.</p></li>
        <li class="reveal"><span class="num">3</span><h3>Empieza tu aventura</h3><p>Funda tu reino, explora mazmorras y forja alianzas. ¿Prefieres entrar a mano? La IP es {IP}.</p></li>
      </ol>
    </section>

    <section class="wrap section">
      <div class="section-head">
        <span class="eyebrow">El reino</span>
        <h2>Mucho más que supervivencia</h2>
      </div>
      <div class="bento">
        <article class="bento-card big reveal">
          <img src="/img/hero-1280.webp" alt="" loading="lazy" width="1280" height="720">
          <div><span class="icon">{I['castle']}</span><h3>Castillos y reinos</h3><p>Funda tu reino, levanta murallas y compite con otros clanes por el control de las tierras.</p></div>
        </article>
{feature('swords', 'Mazmorras y jefes', 'Asalta fortalezas y derrota jefes en eventos semanales con recompensas exclusivas.')}
{feature('sparkles', 'Más de 200 mods', 'Magia, criaturas, biomas y tecnología: un modpack pensado para la aventura, no para el grindeo.')}
{feature('coins', 'Oficios y economía', 'Elige un oficio, sube de nivel, cumple misiones y gana monedas para la tienda del servidor.')}
{feature('shield', 'Terrenos protegidos', 'Reclama tu territorio y protege tu base. Nadie toca lo que es tuyo.')}
{feature('users', 'Comunidad activa', 'Staff atento, eventos y sorteos. La tienda solo da ventajas de estilo y comodidad.')}
      </div>
    </section>

    <section class="section crates-band">
      <div class="wrap">
        <div class="section-head split-head">
          <div>
            <span class="eyebrow">Tienda</span>
            <h2>Crates <span class="gold-text">legendarias</span></h2>
            <p>Cada crate es un set completo con animaciones propias. Pruébatelo en tu personaje antes de comprarlo.</p>
          </div>
          <a class="btn btn-ghost" href="/tienda#crates" data-crates-all>Ver todas las crates {I['arrow']}</a>
        </div>
        <div class="crate-row" id="crate-spotlight">
          <p class="loading">Cargando crates…</p>
        </div>
      </div>
    </section>

    <section class="wrap section">
      <div class="section-head center">
        <span class="eyebrow">Rangos</span>
        <h2>Lleva tu nombre con estilo</h2>
        <p>Prefijos, kits, hogares extra y ventajas de comodidad. Sin romper el equilibrio del juego.</p>
      </div>
      <div class="rank-strip" id="rank-strip"></div>
      <p class="center-cta"><a class="btn btn-ghost" href="/tienda#rangos">Comparar rangos {I['arrow']}</a></p>
    </section>

{DISCORD_BAND}  </main>
''' + footer()


def tienda():
    tabs = [('gratis', 'Recompensas gratis'), ('rangos', 'Rangos'), ('crates', 'Crates'), ('ruleta', 'Ruleta'),
            ('monedas', 'Monedas'), ('tiendamonedas', 'Tienda de monedas')]
    tab_html = '\n'.join(
        f'          <button class="tab" type="button" role="tab" data-category="{k}" aria-selected="false">{label}</button>'
        for k, label in tabs)
    return head('Tienda — Tierras Fantásticas',
                'Tienda oficial de Tierras Fantásticas: crates con sets completos, rangos, ruleta y monedas. Pago seguro con tarjeta y entrega automática en el juego.',
                'tienda') + header('tienda') + f'''
  <main id="main">
    <section class="wrap shop-head">
      <div>
        <span class="eyebrow">Tienda oficial</span>
        <h1>Tienda</h1>
        <p class="lead">Todo llega solo a tu cuenta del servidor: en segundos si estás conectado y, si no, al entrar. Pago seguro con tarjeta.</p>
      </div>
      <label class="shop-search" id="shop-search-wrap" hidden>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
        <span class="sr-only">Buscar crate</span>
        <input id="shop-search" type="search" placeholder="Buscar crate…" autocomplete="off" spellcheck="false">
      </label>
    </section>
    <section class="wrap" style="padding-bottom:56px">
      <div class="tabs shop-tabs" role="tablist" aria-label="Secciones de la tienda" id="shop-tabs">
{tab_html}
      </div>
      <div class="notice" data-payments-off hidden>{I['lock']}<span>La tienda está en mantenimiento: los pagos no están disponibles en este momento.</span></div>
      <p class="shop-intro muted" id="shop-intro"></p>
      <div class="products" id="products" role="tabpanel">
        <p class="loading">Cargando la tienda…</p>
      </div>
      <p class="crate-empty muted" id="crate-empty" hidden>Ninguna crate coincide con la búsqueda.</p>
      <div class="panel compare" id="compare" hidden></div>
      <div class="trust">
        <div class="panel item"><span class="icon">{I['lock']}</span><div><b>Pago seguro con Stripe</b><span>Tarjeta, Apple Pay o Google Pay. Nunca vemos tus datos bancarios.</span></div></div>
        <div class="panel item"><span class="icon">{I['zap']}</span><div><b>Entrega automática</b><span>El servidor te lo da solo; si no estás conectado, te espera.</span></div></div>
        <div class="panel item"><span class="icon">{I['discord']}</span><div><b>Rol en Discord</b><span>Conecta tu Discord en tu cuenta y recibe el rol de tu rango.</span></div></div>
      </div>
    </section>
  </main>
''' + footer()


def crates():
    # Las crates están ahora dentro de la tienda: la dirección antigua lleva a su pestaña.
    return '''<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Crates — Tierras Fantásticas</title>
  <meta name="robots" content="noindex">
  <meta http-equiv="refresh" content="0; url=/tienda#crates">
  <script>location.replace('/tienda#' + (location.hash.slice(1) ? 'crates-' + location.hash.slice(1) : 'crates'))</script>
  <style>body{margin:0;background:#07080d;color:#ece8df;font:16px system-ui,sans-serif;display:grid;place-items:center;min-height:100vh}a{color:#e3b74c}</style>
</head>
<body><p>Las crates están en la <a href="/tienda#crates">tienda</a>.</p></body>
</html>
'''


def mundo():
    return head('El mundo — Tierras Fantásticas',
                'Así es Tierras Fantásticas: supervivencia de aventura con reinos, mazmorras, más de 200 mods y el TF Client.',
                'mundo') + header('mundo') + f'''
  <main id="main">
{page_hero('El reino', 'El mundo te espera', 'Supervivencia con alma de aventura: castillos que asaltar, reinos que fundar y una comunidad que te recibe con los brazos abiertos.')}
    <section class="wrap section">
      <div class="split">
        <div class="media reveal"><img src="/img/hero-1280.webp" alt="Castillo y dragón en Tierras Fantásticas" width="1280" height="720" loading="lazy"></div>
        <div class="reveal">
          <span class="eyebrow">Supervivencia de aventura</span>
          <h2 class="display" style="font-size:clamp(28px,5vw,40px);margin-top:14px">Un mundo hecho para explorar</h2>
          <p class="muted">Biomas nuevos, estructuras que no verás en otro sitio y criaturas que hacen que cada salida sea una aventura. Juega solo, con amigos o al frente de tu propio reino.</p>
          <ul class="list-check">
            <li>Más de 200 mods de magia, criaturas, biomas y tecnología</li>
            <li>Reinos, clanes y terrenos protegidos</li>
            <li>Eventos semanales con jefes y recompensas</li>
            <li>Economía entre jugadores y tiendas propias</li>
          </ul>
        </div>
      </div>
    </section>

    <section class="wrap section">
      <div class="section-head">
        <span class="eyebrow">TF Client</span>
        <h2>El cliente oficial del servidor</h2>
        <p>Viene en el modpack. Hace que entrar sea tan fácil como pulsar un botón.</p>
      </div>
      <div class="grid grid-2 grid-4">
{feature('plug', 'Conexión directa', 'Un botón para entrar a Tierras Fantásticas. Antes revisa tus mods y te dice si falta alguno.')}
{feature('castle', 'Menú propio', 'Pantalla de carga y menú con el arte del reino, sin textos de Mojang.')}
{feature('music', 'Música del reino', 'Banda sonora propia en la carga y el menú; se apaga al entrar a jugar.')}
{feature('gift', 'Compras al instante', 'Lo que compras en la tienda te llega dentro del juego, con aviso en pantalla.')}
      </div>
    </section>

    <section class="wrap section">
      <div class="section-head">
        <span class="eyebrow">Cómo jugar</span>
        <h2>Únete en tres pasos</h2>
      </div>
      <div class="grid grid-3 steps">
{JOIN_STEPS}
      </div>
    </section>

    <section class="wrap section" id="normas">
      <div class="section-head">
        <span class="eyebrow">Normas</span>
        <h2>Las leyes del reino</h2>
        <p>Pocas y claras, para que todos disfrutemos. El staff puede sancionar lo que rompa el espíritu de estas normas.</p>
      </div>
      <div class="rules">
        <div class="panel rule reveal"><div><b>Respeto ante todo</b><span>Nada de insultos, acoso, discriminación ni spam, en el juego ni en Discord.</span></div></div>
        <div class="panel rule reveal"><div><b>Sin trampas</b><span>Prohibidos los hacks, X-ray, duplicaciones y aprovechar fallos. Si encuentras uno, avísanos.</span></div></div>
        <div class="panel rule reveal"><div><b>No destruyas lo ajeno</b><span>El grifeo y el robo fuera de las zonas de guerra pactadas se sancionan.</span></div></div>
        <div class="panel rule reveal"><div><b>Cuida el servidor</b><span>Evita granjas o máquinas que causen lag. Ante la duda, pregunta al staff.</span></div></div>
        <div class="panel rule reveal"><div><b>Una cuenta por persona</b><span>Nada de multicuentas para saltarse límites o sanciones.</span></div></div>
      </div>
    </section>

{DISCORD_BAND}  </main>
''' + footer()


def faq(q, a):
    return f'''        <details>
          <summary>{q}</summary>
          <p>{a}</p>
        </details>'''


def ayuda():
    return head('Ayuda — Tierras Fantásticas',
                'Preguntas frecuentes de Tierras Fantásticas: cómo entrar, pagos, entregas, Discord y crates.',
                'ayuda') + header('ayuda') + f'''
  <main id="main">
{page_hero('Ayuda', 'Preguntas frecuentes', 'Todo lo que necesitas saber para entrar al servidor y sobre la tienda.')}
    <section class="wrap section">
      <div class="faq">
{faq('¿Cómo entro al servidor?', f'Necesitas Minecraft Java 1.20.1 con Forge y el modpack de Tierras Fantásticas (lo tienes en nuestro Discord). Con el TF Client pulsa «Tierras Fantásticas» en el menú; si entras a mano, la IP es {IP}.')}
{faq('Se queda en «Conectando» o me dice que faltan mods', 'El servidor usa más de 200 mods y necesitas los mismos. El TF Client revisa tus mods antes de conectar y te dice cuáles faltan. Instala el modpack completo desde nuestro Discord.')}
{faq('¿Cuánto tarda en llegar mi compra?', 'Al confirmarse el pago, el servidor te la entrega en segundos si estás conectado. Si no lo estás, te espera y la recibes al entrar, con un aviso en pantalla.')}
{faq('¿Qué nombre de usuario debo poner?', 'Tu nombre de Minecraft. La tienda lo comprueba con el servidor al escribirlo: tienes que haber entrado al menos una vez. La compra se entrega a tu cuenta (UUID), así que nunca llega a otro jugador.')}
{faq('¿Qué métodos de pago aceptan?', 'Tarjetas de crédito o débito, Apple Pay, Google Pay y Link, con la página de pago segura de Stripe. Nunca vemos tus datos bancarios.')}
{faq('¿Puedo mejorar mi rango?', 'Sí. Si ya tienes un rango, al comprar uno superior solo pagas la diferencia. No se puede comprar un rango que ya tienes o uno inferior.')}
{faq('¿Qué recibo al comprar una crate?', 'El set completo: todas sus armas, herramientas, la armadura y los cosméticos (alas, mochilas, cascos). Llegan a tu inventario en cuanto estás conectado. En la tienda puedes ver cada pieza en 3D y probártela en tu personaje.')}
{faq('¿Cómo funciona la ruleta?', 'Cada giro te da un premio al azar para el servidor: monedas, diamantes, netherita, tótems o, con poca probabilidad, un arma legendaria de la forja de Nazgul. Las probabilidades están en la pestaña de la ruleta. La tienda elige el premio al confirmarse el pago y te lo entrega en el juego; en la página de tu compra verás qué te tocó.')}
{faq('¿Qué es la tienda de monedas?', 'Objetos del servidor (minerales, objetos raros…) que se pagan con las monedas que ganas jugando. El staff la actualiza desde el juego y la web la muestra al momento.')}
{faq('¿Cómo recibo el rol en Discord?', 'Crea tu cuenta con tu nombre de Minecraft (botón «Entrar» arriba) y conecta tu Discord en «Mi cuenta». Cuando se confirme el pago, el bot te dará el rol de tu rango; si aún no estás en nuestro Discord, te añadirá.')}
{faq('No he recibido mi compra, ¿qué hago?', 'Entra al servidor y espera unos segundos. Si sigue sin llegar, escríbenos por Discord con el número de pedido (aparece al terminar la compra, en tu recibo y en «Mi cuenta») y tu nombre de usuario.')}
      </div>
    </section>

{DISCORD_BAND}  </main>
''' + footer()


def success():
    return head('Tu compra — Tierras Fantásticas', 'Estado de tu compra en Tierras Fantásticas.', 'compra') + header('') + f'''
  <main id="main">
{page_hero('Tu compra', '<span id="title">Comprobando tu pago…</span>', '<span id="message">Un momento, estamos confirmando el pago con Stripe.</span>')}
    <section class="wrap section" style="padding-top:32px">
      <div class="panel" style="max-width:560px;padding:24px">
        <span class="eyebrow" id="state">Comprobando</span>
        <dl class="receipt" id="details" hidden>
          <dt>Pedido</dt><dd id="d-order"></dd>
          <dt>Jugador</dt><dd id="d-user"></dd>
          <dt>Producto</dt><dd id="d-product"></dd>
          <dt>Total</dt><dd id="d-total"></dd>
          <dt>En el juego</dt><dd id="d-delivery"></dd>
          <dt id="d-discord-label" hidden>Discord</dt><dd id="d-discord" hidden></dd>
        </dl>
        <div class="prizes" id="prizes" hidden>
          <h3>Te tocó</h3>
          <ul id="prize-list"></ul>
        </div>
        <div style="display:flex;gap:12px;flex-wrap:wrap;margin-top:24px">
          <a href="/" class="btn btn-gold">Volver al inicio</a>
          <a href="/tienda" class="btn btn-ghost">Seguir comprando</a>
        </div>
      </div>
    </section>
  </main>
''' + footer().replace('  <script src="/app.js" defer></script>\n', '  <script src="/app.js" defer></script>\n  <script src="/success.js" defer></script>\n')


def cuenta():
    return head('Mi cuenta — Tierras Fantásticas',
                'Tu cuenta de Tierras Fantásticas: entra con tu nombre de Minecraft, conecta tu Discord y mira tus compras.',
                'cuenta') + header('cuenta') + f'''
  <main id="main">
{page_hero('Mi cuenta', 'Tu cuenta del reino', 'Entra con tu nombre de Minecraft, conecta tu Discord y sigue tus compras.')}
    <section class="wrap section" style="padding-top:32px">
      <div id="account"><p class="loading">Cargando tu cuenta…</p></div>
    </section>
  </main>
''' + footer()


for name, fn in [('index', index), ('tienda', tienda), ('crates', crates), ('mundo', mundo), ('ayuda', ayuda), ('success', success), ('cuenta', cuenta)]:
    with open(os.path.join(OUT, f'{name}.html'), 'w', encoding='utf-8') as f:
        f.write(fn())
print('ok')
