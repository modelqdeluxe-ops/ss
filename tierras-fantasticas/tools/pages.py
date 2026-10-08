import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from icons import I
import legal

# Genera las páginas HTML de public/ (cabecera, pie y secciones comunes). Uso: python3 tools/pages.py
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'public')
IP = '216.163.187.40:19001'
# Dirección pública (en punycode, como la piden los buscadores y las vistas previas de WhatsApp/Discord)
SITE = 'https://xn--tierrasfantsticas-hpb.store'
EMAIL = 'tierrasfantasticasmc@gmail.com'
# Versión de los Términos y del Aviso de privacidad: config/legal.json (la lee también el Worker, que pide aceptarla al
# crear la cuenta y al pagar). Si cambian los textos legales, sube allí «version» y «date»: a quien tenga una versión
# vieja se le vuelve a pedir que acepte.
LEGAL = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'config', 'legal.json'), encoding='utf-8'))
LEGAL_DATE = LEGAL['date']
# Aviso obligatorio de las normas de Mojang (en español y en inglés, que es como lo piden).
DISCLAIMER_ES = 'No es un producto oficial de Minecraft. No está aprobado por Mojang ni Microsoft ni asociado con ellos.'
DISCLAIMER_EN = 'NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.'
NAV = [('inicio', '/', 'Inicio'), ('tienda', '/tienda', 'Tienda'),
       ('mundo', '/mundo', 'El mundo'), ('ayuda', '/ayuda', 'Ayuda')]
# Solo en el menú del móvil (en escritorio está el botón de la cuenta en la cabecera).
SHEET_EXTRA = [('cuenta', '/cuenta', 'Mi cuenta')]

FONTS = ('<link rel="preconnect" href="https://fonts.googleapis.com">\n'
         '  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>\n'
         '  <link href="https://fonts.googleapis.com/css2?family=Unbounded:wght@500;600;700;800&family=Figtree:wght@400;500;600;700;800&display=swap" rel="stylesheet">')

# Pantalla de carga: solo la primera página de la visita (sessionStorage). Se quita al cargar la página (mínimo 0,7 s
# para que no parpadee, máximo 3 s). En las páginas siguientes, la clase «ready» llega en cuanto el HTML está listo
# y la página entra con una transición suave (styles.css, «Transiciones entre páginas»; la salida la pone app.js).
BOOT = ("<script>(function(d){var h=d.documentElement,s,t=Date.now();h.classList.add('js');"
        "try{s=sessionStorage.getItem('tf-seen')}catch(e){}"
        "function done(){h.classList.add('ready');try{sessionStorage.setItem('tf-seen','1')}catch(e){}}"
        "if(s){h.classList.add('seen');d.addEventListener('DOMContentLoaded',done)}"
        "else{addEventListener('load',function(){setTimeout(done,Math.max(0,700-(Date.now()-t)))});setTimeout(done,3000)}"
        "})(document)</script>")

# Fondo vivo: la nebulosa que se desplaza y respira, estrellas que titilan y alguna estrella fugaz (styles.css,
# «Fondo vivo»). Detrás de todo, sin eventos del ratón.
SKY = '''  <div class="sky" aria-hidden="true">
    <i class="neb-a"></i><i class="neb-b"></i><i class="neb-glow"></i>
    <span class="sky-stars"><i class="stars-a"></i><i class="stars-b"></i></span>
    <i class="shoot"></i><i class="shoot s2"></i>
  </div>
'''

LOADER = '''  <div class="loader" aria-hidden="true">
    <div class="loader-inner">
      <img src="/img/logo.webp" alt="" width="104" height="115">
      <div class="loader-bar"><i></i></div>
      <span>Cargando el reino…</span>
    </div>
  </div>
'''


def skeletons(n, kind='card'):
    one = ('<div class="skel-card" aria-hidden="true"><div class="skel skel-art"></div>'
           '<div class="skel skel-line"></div><div class="skel skel-line short"></div></div>')
    if kind == 'account':
        one = ('<div class="skel-card skel-account" aria-hidden="true"><div class="skel skel-line"></div>'
               '<div class="skel skel-line"></div><div class="skel skel-line short"></div></div>')
    return f'<div class="skel-grid skel-{kind}"><span class="sr-only">Cargando…</span>' + one * n + '</div>'


# Dirección de cada página (para og:url y el mapa del sitio)
PATHS = {'inicio': '/', 'tienda': '/tienda', 'mundo': '/mundo', 'ayuda': '/ayuda', 'cuenta': '/cuenta', 'legal': '/legal',
         'terminos': '/terminos', 'privacidad': '/privacidad'}


def head(title, desc, page, hero=False, preload_hero=False):
    preload = ('\n  <link rel="preload" as="image" href="/img/nebula-1672.webp" media="(min-width: 761px)">'
               '\n  <link rel="preload" as="image" href="/img/nebula-mobile.webp" media="(max-width: 760px)">'
               )
    return f'''<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
  <title>{title}</title>
  <meta name="description" content="{desc}">
  <meta name="theme-color" content="#0a0d14">
  <meta property="og:title" content="{title}">
  <meta property="og:description" content="{desc}">
  <meta property="og:image" content="{SITE}/img/og.jpg">
  <meta property="og:url" content="{SITE}{PATHS.get(page, '/')}">
  <meta property="og:site_name" content="Tierras Fantásticas">
  <meta property="og:locale" content="es_MX">
  <meta property="og:type" content="website">
  <meta name="twitter:card" content="summary_large_image">
  {FONTS}{preload}
  <link rel="stylesheet" href="/styles.css">
  <link rel="icon" type="image/png" sizes="32x32" href="/img/icon-32.png">
  <link rel="icon" type="image/webp" href="/img/logo.webp">
  <link rel="apple-touch-icon" href="/img/icon-180.png">
  {BOOT}
</head>
<body data-page="{page}"{' data-hero' if hero else ''}>
{SKY}{LOADER}'''


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
          <span class="dot" data-status-dot></span><span data-server-ip>{IP}</span><span class="hint">{I['copy']}<span data-copy-hint>Copiar</span></span>
        </button>
        <a href="/tienda" class="btn btn-buy btn-sm">Tienda</a>
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
      <button class="ip-box" type="button" data-copy-ip aria-label="Copiar la IP del servidor">
        <span class="ip-text"><small>IP del servidor</small><span class="ip" data-server-ip>{IP}</span></span>
        <span class="ip-copy">{I['copy']}<span data-copy-hint>Copiar</span></span>
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
          <p>Servidor de Minecraft de survival, aventura, fantasía y rol.</p>
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
            <li><a href="/tienda#cosmeticos">Cosméticos</a></li>
            <li><a href="/tienda#crates">Crates</a></li>
            <li><a href="/tienda#vfx">VFX</a></li>
            <li><a href="/tienda#tiendamonedas">Tienda de monedas</a></li>
            <li><a href="/ayuda">Preguntas frecuentes</a></li>
          </ul>
        </div>
        <div>
          <h4>Legal y contacto</h4>
          <ul>
            <li><a href="/legal">Aviso legal</a></li>
            <li><a href="/terminos">Términos y condiciones</a></li>
            <li><a href="/privacidad">Aviso de privacidad</a></li>
            <li><a href="mailto:{EMAIL}">{EMAIL}</a></li>
            <li><a href="/discord" target="_blank" rel="noopener">Discord</a></li>
            <li><a href="/whatsapp" target="_blank" rel="noopener">Grupo de WhatsApp</a></li>
          </ul>
        </div>
      </div>
    </div>
    <div class="wrap">
      <p class="disclaimer"><b>Servidor independiente.</b> {DISCLAIMER_ES} Minecraft es una marca de Mojang AB.
        <span lang="en">{DISCLAIMER_EN}</span></p>
    </div>
    <div class="wrap legal">
      <p class="copyright">© <span data-year></span> Tierras Fantásticas. Todos los derechos reservados. Prohibida la copia,
        reventa o distribución del contenido del servidor (sets, modelos, texturas, textos e imágenes) fuera de Tierras
        Fantásticas. Las marcas y contenidos de terceros pertenecen a sus dueños.</p>
      <span>Lo que se compra en la tienda ayuda a mantener el servidor. Precios en dólares estadounidenses (USD).</span>
    </div>
    <span class="credit">By Pewez777</span>
  </footer>
  <a href="#main" class="to-top" data-to-top aria-label="Volver arriba">{I['arrow']}</a>

  <script src="/app.js" defer></script>
</body>
</html>
'''


def hero_art(cls='hero-art'):
    # El arte del reino (de noche) es el fondo fijo de toda la web (body::before en styles.css): las cabeceras ya no
    # llevan imagen propia, dejan verlo detrás.
    return ''


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
        <h2>Únete al Discord del reino</h2>
        <p>Novedades, eventos, sorteos, el modpack y soporte del staff en Discord, y avisos rápidos en el grupo de WhatsApp.</p>
        <div class="actions">
          <a href="/discord" class="btn btn-discord btn-lg" target="_blank" rel="noopener">{I['discord']}Entrar al Discord</a>
          <a href="/whatsapp" class="btn btn-whatsapp btn-lg" target="_blank" rel="noopener">{I['whatsapp']}Grupo de WhatsApp</a>
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


def hero_title(text):
    """Título de la portada: cada letra en su <span class="ch" style="--i:N"> (entran una a una) dentro de su palabra
    (.w, que no se parte); los lectores de pantalla leen el aria-label."""
    words, i = [], 0
    for word in text.split(' '):
        letters = ''
        for ch in word:
            letters += f'<span class="ch" style="--i:{i}">{ch}</span>'
            i += 1
        words.append(f'<span class="w">{letters}</span>')
        i += 1
    return f'<h1 class="hero-title" aria-label="{text}"><span aria-hidden="true">{" ".join(words)}</span></h1>'


def index():
    # Portada mínima (lo pidió el dueño): solo el emblema, el título, la IP, la tienda y los enlaces para entrar
    # (Discord, WhatsApp y cómo entrar). Lo demás está en sus páginas (tienda, el mundo, ayuda).
    return head('Tierras Fantásticas — Servidor de Minecraft',
                'Tierras Fantásticas: servidor de Minecraft de survival, aventura, fantasía y rol. Más de 200 mods, sets animados y una comunidad activa.',
                'inicio', hero=True, preload_hero=True) + header('inicio') + f'''
  <main id="main">
    <section class="hero">
      {hero_art()}
      <div class="wrap hero-inner">
        <div class="logo-mark">
          <span class="glow" aria-hidden="true"></span>
          <img src="/img/logo.webp" alt="Emblema de Tierras Fantásticas" width="743" height="820">
          <span class="shine" aria-hidden="true"></span>
        </div>
        <span class="live-pill" data-status><span class="dot" data-status-dot></span><span data-status-text>Comprobando el servidor…</span></span>
        {hero_title('Tierras Fantásticas')}
        <p class="lead">Servidor de Minecraft de <b>survival</b>, <b>aventura</b>, <b>fantasía</b> y <b>rol</b>. Crea tu
          historia, explora, combate y forja alianzas con otros jugadores.</p>
        <div class="hero-cta">
          <button class="ip-box" type="button" data-copy-ip aria-label="Copiar la IP del servidor">
            <span class="ip-text"><small>IP del servidor</small><span class="ip" data-server-ip>{IP}</span></span>
            <span class="ip-copy">{I['copy']}<span data-copy-hint>Copiar</span></span>
          </button>
          <a href="/tienda" class="btn btn-shop btn-lg">Visitar la tienda {I['arrow']}</a>
        </div>
        <span class="hero-meta">Minecraft Java 1.20.1 · Forge</span>
        <nav class="hero-links" aria-label="Únete a la comunidad">
          <a href="/discord" class="btn btn-discord" target="_blank" rel="noopener">{I['discord']}Entra a nuestro Discord</a>
          <a href="/whatsapp" class="btn btn-whatsapp" target="_blank" rel="noopener">{I['whatsapp']}Grupo de WhatsApp</a>
          <a href="/ayuda#como-entrar" class="btn btn-ghost">{I['help']}Cómo entrar al server</a>
        </nav>
      </div>
    </section>
  </main>
''' + footer()


def tienda():
    tabs = [('gratis', 'Recompensas gratis'), ('rangos', 'Rangos'), ('cosmeticos', 'Cosméticos'), ('crates', 'Crates'),
            ('vfx', 'VFX'), ('tiendamonedas', 'Tienda de monedas')]
    tab_html = '\n'.join(
        f'          <button class="tab" type="button" role="tab" data-category="{k}" aria-selected="false">{label}</button>'
        for k, label in tabs)
    return head('Tienda — Tierras Fantásticas',
                'Tienda de Tierras Fantásticas, servidor independiente de Minecraft: rangos, cosméticos, crates con sets completos, VFX y recompensas gratis. Pago seguro y entrega automática en el juego.',
                'tienda') + header('tienda') + f'''
  <main id="main">
    <section class="shop-head">
      {hero_art()}
      <div class="wrap shop-head-inner">
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
      </div>
    </section>
    <section class="wrap shop-body">
      <div class="tabs shop-tabs frame" role="tablist" aria-label="Secciones de la tienda" id="shop-tabs">
{tab_html}
      </div>
      <div class="notice" data-payments-off hidden>{I['lock']}<span>La tienda está en mantenimiento: los pagos no están disponibles en este momento.</span></div>
      <p class="shop-intro muted" id="shop-intro"></p>
      <div class="products" id="products" role="tabpanel">
        {skeletons(8)}
      </div>
      <p class="crate-empty muted" id="crate-empty" hidden>Ninguna crate coincide con la búsqueda.</p>
      <div class="trust">
        <div class="panel item"><span class="icon">{I['lock']}</span><div><b>Pago seguro</b><span>Tarjeta, Apple Pay o Google Pay con Stripe<span data-paypal hidden>, o PayPal</span>. Nunca vemos tus datos bancarios.</span></div></div>
        <div class="panel item"><span class="icon">{I['zap']}</span><div><b>Entrega automática</b><span>El servidor te lo da solo; si no estás conectado, te espera.</span></div></div>
        <div class="panel item"><span class="icon">{I['discord']}</span><div><b>Rol en Discord</b><span>Conecta tu Discord en tu cuenta y recibe el rol de tu rango.</span></div></div>
      </div>
      <section class="owned frame" id="vinculados" aria-labelledby="owned-title">
        <div class="owned-head">
          <span class="icon">{I['shield']}</span>
          <div>
            <h2 id="owned-title">Tus compras son para siempre</h2>
            <p>Las armas, herramientas y armaduras de los rangos y de las crates quedan vinculadas a tu cuenta de
              Minecraft.</p>
          </div>
        </div>
        <ul class="owned-list">
          <li><b>Irrompibles</b><span>No se gastan ni se rompen. En el suelo no desaparecen y, si caen al vacío, vuelven
            a ti.</span></li>
          <li><b>Solo para ti</b><span>Nadie más puede cogerlas, ponérselas ni usarlas. Si otro jugador las saca de un
            cofre, vuelven a ti.</span></li>
          <li><b>No transferibles</b><span>No se pueden regalar, vender ni intercambiar.</span></li>
          <li><b>Cosméticos libres</b><span>Los cosméticos y la ropa (sombreros, alas, mochilas) no se vinculan: puedes
            regalarlos e intercambiarlos. Las alas planean como unas élitros.</span></li>
        </ul>
      </section>
      <p class="shop-legal">Tierras Fantásticas es un servidor independiente. {DISCLAIMER_ES} En la tienda no hay nada al
        azar y las monedas del servidor solo se ganan jugando. Precios en USD, finales.
        Tienes 5 días hábiles para cancelar una compra. Al comprar aceptas los <a href="/terminos">Términos y
        condiciones</a> y el <a href="/privacidad">Aviso de privacidad</a>.</p>
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
        <div class="media frame reveal"><img src="/img/hero-1280.webp" alt="Castillo y dragón en Tierras Fantásticas" width="1280" height="720" loading="lazy"></div>
        <div class="reveal">
          <span class="eyebrow">Supervivencia de aventura</span>
          <h2 class="h2">Un mundo hecho para explorar</h2>
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
      <div class="section-head reveal">
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
      <div class="section-head reveal">
        <span class="eyebrow">Cómo jugar</span>
        <h2>Únete en tres pasos</h2>
      </div>
      <div class="grid grid-3 steps">
{JOIN_STEPS}
      </div>
    </section>

    <section class="wrap section" id="normas">
      <div class="section-head reveal">
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


LEGAL_DOCS = [('legal', 'Aviso legal'), ('terminos', 'Términos y condiciones'), ('privacidad', 'Aviso de privacidad')]


def legal_page(key, title, desc, intro, sections):
    tabs = '\n'.join(
        f'          <a href="/{k}"{" aria-current=\"page\"" if k == key else ""}>{label}</a>' for k, label in LEGAL_DOCS)
    toc = '\n'.join(f'            <li><a href="#{sid}">{head_}</a></li>' for sid, head_, _ in sections)
    body = '\n'.join(
        f'''        <section class="legal-section" id="{sid}">
          <h2><span>{i:02d}</span>{head_}</h2>{html}
        </section>''' for i, (sid, head_, html) in enumerate(sections, 1))
    return head(f'{title} — Tierras Fantásticas', desc, key) + header(key) + f'''
  <main id="main">
{page_hero('Legal', title, intro)}
    <section class="wrap section pt-sm">
      <nav class="legal-tabs" aria-label="Documentos legales">
{tabs}
      </nav>
      <div class="legal-layout">
        <aside class="legal-toc" aria-label="En esta página">
          <b>En esta página</b>
          <ol>
{toc}
          </ol>
          <p class="legal-date">Última actualización: {LEGAL_DATE}</p>
        </aside>
        <article class="legal-doc">
{body}
          <p class="legal-contact">¿Dudas? Escríbenos a <a href="mailto:{EMAIL}">{EMAIL}</a>. Respondemos sin que tengas que crear ninguna cuenta.</p>
        </article>
      </div>
    </section>
  </main>
''' + footer()


def aviso_legal():
    return legal_page('legal', 'Aviso legal',
                      'Aviso legal de Tierras Fantásticas: servidor de Minecraft independiente, sin relación con Mojang ni Microsoft.',
                      'Quiénes somos, qué relación tenemos con Minecraft (ninguna oficial) y cómo funciona la tienda.',
                      legal.AVISO)


def terminos():
    return legal_page('terminos', 'Términos y condiciones',
                      'Términos y condiciones de uso y de compra de Tierras Fantásticas: cuentas, precios, entrega, cancelaciones y reembolsos.',
                      'Las reglas de la web, las cuentas y la tienda: qué compras, cómo se entrega y cómo cancelar.',
                      legal.TERMINOS)


def privacidad():
    return legal_page('privacidad', 'Aviso de privacidad',
                      'Aviso de privacidad de Tierras Fantásticas: qué datos tratamos, para qué, con quién y cómo ejercer tus derechos.',
                      'Qué datos usamos, para qué, con quién se comparten y cómo ejercer tus derechos.',
                      legal.PRIVACIDAD)


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
    <section class="wrap section" id="como-entrar">
      <div class="section-head reveal">
        <span class="eyebrow">Cómo jugar</span>
        <h2>Dentro en tres pasos</h2>
        <p>Es gratis. Solo necesitas Minecraft Java y el modpack del servidor.</p>
      </div>
      <ol class="timeline">
        <li><span class="num">1</span><h3>Prepara el modpack</h3><p>Minecraft Java 1.20.1 con Forge y el modpack de Tierras Fantásticas, que incluye el TF Client. Lo tienes en nuestro Discord.</p></li>
        <li><span class="num">2</span><h3>Pulsa «Tierras Fantásticas»</h3><p>Desde el menú del TF Client entras con un clic. Antes de conectar revisa tus mods y te dice si te falta alguno.</p></li>
        <li><span class="num">3</span><h3>Empieza tu aventura</h3><p>Funda tu reino, explora mazmorras y forja alianzas. ¿Prefieres entrar a mano? La IP es {IP}.</p></li>
      </ol>
    </section>
    <section class="wrap section pt-0">
      <div class="faq">
{faq('¿Cómo entro al servidor?', f'Necesitas Minecraft Java 1.20.1 con Forge y el modpack de Tierras Fantásticas (lo tienes en nuestro Discord). Con el TF Client pulsa «Tierras Fantásticas» en el menú; si entras a mano, la IP es {IP}.')}
{faq('Se queda en «Conectando» o me dice que faltan mods', 'El servidor usa más de 200 mods y necesitas los mismos. El TF Client revisa tus mods antes de conectar y te dice cuáles faltan. Instala el modpack completo desde nuestro Discord.')}
{faq('¿Cuánto tarda en llegar mi compra?', 'Al confirmarse el pago, el servidor te la entrega en segundos si estás conectado. Si no lo estás, te espera y la recibes al entrar, con un aviso en pantalla.')}
{faq('¿Qué nombre de usuario debo poner?', 'Tu nombre de Minecraft. La tienda lo comprueba con el servidor al escribirlo: tienes que haber entrado al menos una vez. La compra se entrega a tu cuenta (UUID), así que nunca llega a otro jugador.')}
{faq('¿Qué métodos de pago aceptan?', 'Tarjetas de crédito o débito, Apple Pay, Google Pay y Link, con la página de pago segura de Stripe<span data-paypal hidden>, y PayPal (tu cuenta de PayPal o una tarjeta en su página)</span>. Nunca vemos tus datos bancarios. Los precios están en dólares estadounidenses (USD) y el precio que ves es el total.')}
{faq('¿Puedo mejorar mi rango?', 'Sí. Si ya tienes un rango, al comprar uno superior solo pagas la diferencia. No se puede comprar un rango que ya tienes o uno inferior.')}
{faq('¿Qué recibo al comprar una crate?', 'El set completo, siempre el mismo y sin nada al azar: sus armas, herramientas, la armadura y los cosméticos (alas, mochilas, cascos). Llegan a tu inventario en cuanto estás conectado, son permanentes y quedan vinculados a tu cuenta. En la tienda puedes ver cada pieza en 3D y probártela antes.')}
{faq('¿Puedo regalar o intercambiar lo que compro?', 'Los cosméticos y la ropa, sí. Las armas, herramientas y armaduras de los rangos y de las crates, no: quedan vinculadas a tu cuenta y solo tú puedes usarlas. Lo tienes explicado en la <a href="/tienda#vinculados">tienda</a>.')}
{faq('¿Las alas sirven para volar?', 'Sí: todas las alas de la tienda planean como unas élitros, en el pecho o en el hueco de la espalda (con la pechera puesta), y nunca se gastan. Las mochilas, capas y colas son solo de adorno.')}
{faq('¿Qué es la tienda de monedas?', 'Objetos del servidor que se pagan con las monedas que ganas jugando. Las monedas no se venden con dinero, no tienen valor real y no se pueden cambiar por dinero. El staff la actualiza desde el juego y la web la muestra al momento.')}
{faq('¿Puedo cancelar una compra?', f'Sí. Tienes 5 días hábiles desde la entrega para cancelar cualquier compra y recuperar tu dinero, sin dar explicaciones: escríbenos a <a href="mailto:{EMAIL}">{EMAIL}</a> con tu número de pedido. Al reembolsarla, el servidor retira todo lo que se entregó con esa compra, esté donde esté (también en cofres), y el rango si lo era. Todos los detalles están en los <a href="/terminos#reembolsos">Términos y condiciones</a>.')}
{faq('¿Tierras Fantásticas es oficial de Minecraft?', 'No. Es un servidor independiente: no es un producto oficial de Minecraft y no está aprobado por Mojang ni Microsoft ni asociado con ellos. Lo que se compra en la tienda ayuda a mantener el servidor. Más información en el <a href="/legal">Aviso legal</a>.')}
{faq('¿Cómo recibo el rol en Discord?', 'Crea tu cuenta con tu nombre de Minecraft (botón «Entrar» arriba) y conecta tu Discord en «Mi cuenta». Cuando se confirme el pago, el bot te dará el rol de tu rango; si aún no estás en nuestro Discord, te añadirá.')}
{faq('No he recibido mi compra, ¿qué hago?', f'Entra al servidor y espera unos segundos. Si sigue sin llegar, escríbenos a <a href="mailto:{EMAIL}">{EMAIL}</a> o por Discord con el número de pedido (aparece al terminar la compra, en tu recibo y en «Mi cuenta») y tu nombre de jugador.')}
      </div>
    </section>

{DISCORD_BAND}  </main>
''' + footer()


def success():
    return head('Tu compra — Tierras Fantásticas', 'Estado de tu compra en Tierras Fantásticas.', 'compra') + header('') + f'''
  <main id="main">
{page_hero('Tu compra', '<span id="title">Comprobando tu pago…</span>', '<span id="message">Un momento, estamos confirmando el pago.</span>')}
    <section class="wrap section pt-sm">
      <div class="panel receipt-card">
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
        <div class="receipt-actions">
          <a href="/" class="btn btn-primary">Volver al inicio</a>
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
    <section class="wrap section pt-sm">
      <div id="account">{skeletons(2, 'account')}</div>
    </section>
  </main>
''' + footer()


def notfound():
    # Página 404 (la sirve Cloudflare para cualquier dirección que no existe: not_found_handling en wrangler.jsonc)
    return head('Página no encontrada — Tierras Fantásticas', 'Esta página no existe en Tierras Fantásticas.', '404') + header('') + f'''
  <main id="main">
    <section class="page-hero lost">
      {hero_art()}
      <div class="wrap">
        <span class="lost-code" aria-hidden="true">404</span>
        <span class="eyebrow">Fuera del mapa</span>
        <h1>Esta tierra aún no existe</h1>
        <p>La página que buscas no está en el reino: puede que el enlace esté mal escrito o que la hayamos movido.</p>
        <div class="actions">
          <a href="/" class="btn btn-primary btn-lg">Volver al inicio {I['arrow']}</a>
          <a href="/tienda" class="btn btn-ghost btn-lg">Ir a la tienda</a>
          <a href="/ayuda" class="btn btn-ghost btn-lg">Ayuda</a>
        </div>
      </div>
    </section>
  </main>
''' + footer()


def sitemap():
    urls = '\n'.join(f'  <url><loc>{SITE}{path}</loc></url>' for path in PATHS.values())
    return f'<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n{urls}\n</urlset>\n'


with open(os.path.join(OUT, 'sitemap.xml'), 'w', encoding='utf-8') as f:
    f.write(sitemap())
for name, fn in [('404', notfound), ('index', index), ('tienda', tienda), ('crates', crates), ('mundo', mundo), ('ayuda', ayuda), ('success', success),
                 ('cuenta', cuenta), ('legal', aviso_legal), ('terminos', terminos), ('privacidad', privacidad)]:
    with open(os.path.join(OUT, f'{name}.html'), 'w', encoding='utf-8') as f:
        f.write(fn())
print('ok')
