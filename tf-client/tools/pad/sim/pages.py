"""Vistas previas de las apps del pad: python3 tools/pad/sim/pages.py <carpeta de salida>.

Dibuja las apps con el mismo layout que PadViewPage (pestañas, cabecera, rejilla y filas), PadCommunityPage y
PadCameraPage, con la fuente de Minecraft, y una hoja con todas (hoja_apps.png). Los datos son de ejemplo. Si cambias
posiciones en esas clases Java, cámbialas también en sim.py.
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sim import NAVY, ROOT, Sim, lighten, rgb  # noqa: E402
from skin import accent, mix, darken as darken_t  # noqa: E402

if len(sys.argv) < 2:
    sys.exit('Uso: python3 tools/pad/sim/pages.py <carpeta de salida>')
OUT = os.path.join(sys.argv[1], '')
os.makedirs(OUT, exist_ok=True)
G, B, R, GR, GO = 3, 0, 2, 4, 1
T, M, GOLD, GREEN = 0x18265C, 0x7E8CA8, 0xC27A10, 0x1E9E46
NAMES = []


class Local:
    """Como TFPadScreen con una página «a escala»: dentro, X, Y, W, H son unidades de vista (zona de la app desde 0,0)
    y todo sale bs veces más grande; las medidas «grandes» (bigu) ya no se agrandan otra vez."""

    def __init__(self, s):
        self.s = s

    def __enter__(self):
        s = self.s
        self.saved = (s.X, s.Y, s.W, s.H, s.bs, s.big)
        self.ctx = s.big_at(s.X, s.Y, px=s.big)
        self.ctx.__enter__()
        s.X, s.Y, s.W, s.H = 0, 0, int(self.saved[2] / s.bs), int(self.saved[3] / s.bs)
        s.bs, s.big = 1, s.f
        return s

    def __exit__(self, *a):
        s = self.s
        self.ctx.__exit__(*a)
        s.X, s.Y, s.W, s.H, s.bs, s.big = self.saved


ONLY = set(filter(None, os.environ.get('PAGES', '').split(',')))
FX = os.path.join(ROOT, 'src/main/resources/assets/tfclient/textures/gui/pad/fx')
SKILLS = os.path.join(ROOT, 'src/main/resources/assets/tfclient/textures/gui/skills')


def page(name, title, *args, app='', **kw):
    """Una app del servidor (PadViewPage): a la escala grande, como en el juego desde la 1.3.25. app: su id (el color,
    el icono y la frase de su cabecera)."""
    if ONLY and name not in ONLY:
        return
    toast = kw.pop('toast', None)
    s = Sim()
    s.status(title)
    with Local(s):
        s.view(*args, app=app, **kw)
    if toast:  # el aviso (TFPadScreen.drawNotice), por encima de todo
        s.toast(*toast)
    s.save(OUT + name + '.png')
    NAMES.append(name)


def fx(kind, id_):
    """La imagen de un efecto (PadView.padImage('fx/kill_…') o 'fx/class_…'): tools/pad/fx_images.py."""
    return {'img': os.path.join(FX, f'{kind}_{id_}.png'), 'smooth': kind == 'kill'}


def job(bg):
    """El retrato de un oficio (PadJobs.portrait: textures/gui/pad/jobs/<fondo>.png, tools/pad/job_images.py)."""
    return {'img': os.path.join(FX, '..', 'jobs', bg + '.png'), 'smooth': False}


def skill_icon(cls, skill):
    return {'img': os.path.join(SKILLS, cls, skill + '.png'), 'smooth': False}


APPS = [('musica', 'MÚSICA'), ('oficios', 'OFICIOS'), ('misiones', 'MISIONES'), ('pase', 'TF PASS'), ('gachapon', 'GACHAPÓN'), ('cazas', 'CAZAS'),
        ('recompensas', 'RECOMPENSAS'), ('tienda', 'TIENDA'), ('gts', 'GTS'), ('monedero', 'MONEDERO'), ('viajes', 'VIAJES'),
        ('hogares', 'HOGARES'), ('kits', 'KITS'), ('protecciones', 'PROTECCIÓN'), ('clanes', 'CLANES'),
        ('jugadores', 'JUGADORES'), ('comunidad', 'COMUNIDAD'), ('camara', 'CÁMARA'), ('ranking', 'RANKING'),
        ('armario', 'ARMARIO'), ('efectos', 'EFECTOS'), ('rango', 'MI RANGO'), ('web', 'WEB')]
ADMIN = [('admin', 'APPS'), ('tienda', 'TIENDA'), ('kits', 'KITS'), ('recompensas', 'RECOMPENSAS'), ('viajes', 'VIAJES'), ('gts', 'GTS'),
         ('comunidad', 'COMUNIDAD'), ('oficios', 'OFICIOS'), ('admin', 'AJUSTES')]

if not ONLY or 'p_inicio' in ONLY:
    s = Sim(backdrop=False)
    s.backdrop(home=APPS)
    s.status('TF PAD', back=False, music=True)
    s.home(APPS, hover='hogares', glint='tienda')
    s.save(OUT + 'p_inicio.png')
    NAMES.append('p_inicio')
    # el fondo animado en otros momentos (las luces suben, las nubes se cruzan y el brillo pasa por las fichas)
    for tt in (4.2, 9.6):
        s = Sim(backdrop=False)
        s.T = tt
        s.backdrop(tt, home=APPS)
        s.status('TF PAD', back=False, music=True)
        s.home(APPS)
        s.save(OUT + f'p_inicio_t{int(tt)}.png')
    # las fichas entrando al abrir el pad (a los 0,25 s)
    s = Sim(backdrop=False)
    s.backdrop(home=APPS, fade=0.6)
    s.status('TF PAD', back=False, music=True)
    s.home(APPS, since=0.25)
    s.save(OUT + 'p_inicio_entrada.png')

if os.environ.get('GIF') and (not ONLY or 'p_inicio' in ONLY):
    # GIF del fondo animado de la portada: la entrada de las fichas y luego unos segundos de cielo (GIF=segundos)
    secs, fps = float(os.environ['GIF']), 12
    frames = []
    for i in range(int(secs * fps)):
        tt = 20.0 + i / fps
        s = Sim(backdrop=False)
        s.T = tt
        s.backdrop(tt, home=APPS, fade=min(1.0, i / fps / 0.4))
        s.status('TF PAD', back=False, music=True)
        s.home(APPS, since=i / fps)
        im = s.frame()
        im.alpha_composite(s.art, (s.ps * 52, s.ps * 64))
        im = im.crop((0, 0, im.width, im.height - 60))
        frames.append(im.resize((im.width * 9 // 20, im.height * 9 // 20), Image.LANCZOS).convert('RGB'))
    gif = [f.quantize(255, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE) for f in frames]
    gif[0].save(OUT + 'p_inicio_animado.gif', save_all=True, append_images=gif[1:], duration=round(1000 / fps), loop=0)
    # y en WebP animado (colores sin recortar)
    frames[0].save(OUT + 'p_inicio_animado.webp', save_all=True, append_images=frames[1:], duration=round(1000 / fps), loop=0, quality=90)

if not ONLY or 'p_admin' in ONLY:
    s = Sim()
    s.status('PAD ADMIN', back=False, title_color=rgb(0xFFD36A), greens=False)
    s.home(ADMIN)
    s.save(OUT + 'p_admin.png')
    NAMES.append('p_admin')

# --- Oficios -----------------------------------------------------------------------------------------------------------
page('p_oficios', 'OFICIOS', [], '', ['Trabajas de Minero · cobras 42 monedas en el próximo pago.'], [], app='oficios', hover=2, cards=[
    ('wheat', 'Granjero', 0x6FB43C, 'Nivel 3', False, 1, {'prog': 0.5, **job('farmer')}),
    ('iron_pickaxe', 'Minero', 0x8C9BB4, 'Nivel 12', True, 1, {'prog': 0.28, 'badge': '+2', **job('miner')}),
    ('iron_axe', 'Leñador', 0xA0703C, 'Nivel 5', False, 1, {'prog': 0.8, **job('wood_cutter')}),
    ('iron_shovel', 'Excavador', 0xC8A064, 'Sin empezar', False, 1, {'prog': 0, 'tone': 3, **job('digger')}),
    ('fishing_rod', 'Pescador', 0x3C8CD2, 'Sin empezar', False, 1, {'prog': 0, 'tone': 3, **job('fisherman')}),
    ('bow', 'Cazador', 0xC83C3C, 'Nivel 1', False, 1, {'prog': 0.1, **job('hunter')}),
    ('brewing_stand', 'Alquimista', 0x9A5CF0, 'Sin empezar', False, 1, {'prog': 0, 'tone': 3, **job('alchemist')}),
    ('anvil', 'Herrero', 0x707880, 'Nivel máximo', False, 1, {'prog': 1, 'tone': 1, **job('blacksmith')}),
])
page('p_oficio', 'OFICIOS', [('m', 'MISIONES (1)'), ('a', 'CÓMO SE GANA'), ('r', 'PREMIOS')], 'm', [], [
    ('coal', 'Carbón para la forja', T, ['Saca 32 menas de carbón: 32/32 · cada día'], 1.0, '+¤120', ('COBRAR', G), None, True),
    ('iron_ore', 'Vena de hierro', T, ['Saca 16 menas de hierro: 9/16 · cada día'], 0.56, '+¤200', None, None),
], hero=('iron_pickaxe', 'Minero · tu oficio', 0x8C9BB4, ['Pica piedra y minerales bajo tierra.', 'Nivel 12 de 50 · 340/1200 xp.'], 0.28, '', None, None),
     footer=[('ATRÁS', B), ('DEJAR', R)], app='oficios')

# --- Misiones y Cazas --------------------------------------------------------------------------------------------------
page('p_misiones', 'MISIONES', [('diarias', 'DIARIAS'), ('semanales', 'SEMANALES')], 'diarias', ['Se renuevan en 5 h 12 min.'], [
    ('coal', 'Minero de carbón', T, ['Saca 24 menas de carbón  ·  24/24  ·  +100 XP de pase'], 1.0, '+¤80', ('RECLAMAR', G), None),
    ('rotten_flesh', 'Noche de zombis', T, ['Derrota 15 zombis  ·  9/15  ·  +100 XP de pase'], 0.6, '+¤90', None, None),
    ('wheat', 'Cosecha del día', M, ['Recoge 64 de trigo  ·  hecha'], 1.0, 'HECHA', None, None),
], footer=[('RECLAMAR TODO', G)], app='misiones', hover_row=1)
page('p_cazas', 'CAZAS', [], '', ['Las mismas para todo el servidor. Nuevas cazas en 2 d 4 h.'], [
    ('bone', 'Cazador de esqueletos', T, ['Derrota 40 esqueletos  ·  28/40'], 0.7, '+¤250', None, None),
    ('spider_eye', 'Telarañas fuera', T, ['Derrota 25 arañas  ·  25/25'], 1.0, '+¤180', ('RECLAMAR', G), None),
    ('blaze_rod', 'Fuego del Nether', T, ['Derrota 10 blazes  ·  2/10'], 0.2, '+¤400', None, None),
], app='cazas')

# --- Recompensas -------------------------------------------------------------------------------------------------------
page('p_recompensas', 'RECOMPENSAS', [('diaria', 'DIARIA'), ('otras', 'OTRAS (1)')], 'diaria',
     ['Día 3 de 7: ¡reclámala! Si te saltas un día, vuelves al día 1.'], [], app='recompensas', hover=3, cards=[
         ('bread', 'Día 1 · 25', 0xAABAD2, 'Reclamada', False, 8, {'tone': 3}), ('torch', 'Día 2 · 25', 0xAABAD2, 'Reclamada', False, 16, {'tone': 3}),
         ('iron_ingot', 'Día 3 · 50', 0x40C850, '¡Hoy!', True, 4, {'tone': 2}), ('cooked_beef', 'Día 4 · 50', 0xF6B628, 'Mañana', False, 12),
         ('golden_carrot', 'Día 5 · 75', 0xF6B628, '', False, 8), ('experience_bottle', 'Día 6 · 100', 0xF6B628, '', False, 8),
         ('diamond', 'Día 7 · 200', 0xF6B628, '', False, 2),
     ], footer=[('RECLAMAR DÍA 3', G)])

# --- Tienda y GTS ------------------------------------------------------------------------------------------------------
page('p_tienda', 'TIENDA', [('', 'COMPRAR'), ('v', 'VENDER')], '', [], [
    ('diamond', 'Minerales', 0x3CB4E6, ['Diamante, netherita, lapislázuli'], -1, '3 objetos', None, None),
    ('bread', 'Comida', 0xD2A050, ['Pan, filete, manzana dorada'], -1, '8 objetos', None, None),
    ('oak_log', 'Bloques', 0x8C6A3C, ['Troncos, piedra, cristal'], -1, '24 objetos', None, None),
    ('enchanted_book', 'Encantamientos', 0x9A5CF0, ['Libros para tus herramientas'], -1, '6 objetos', None, None),
], app='tienda', hover_row=0)
page('p_tienda_cat', 'TIENDA', [], '', ['Minerales'], [], app='tienda', hover=1, cards=[
    ('diamond', 'Diamante', 0xF6B628, '¤950', False), ('netherite_scrap', 'Chatarra de netherita', 0xF6B628, '¤6.000', False),
    ('lapis_lazuli', 'Lapislázuli', 0x40C850, '+¤12', False), ('emerald', 'Esmeralda', 0xF6B628, '¤1.200', False),
    ('gold_ingot', 'Lingote de oro', 0xF6B628, '¤300', False),
], footer=[('ATRÁS', B)])
page('p_tienda_comprar', 'TIENDA', [], 'i:0:0:b', [], [], app='tienda', cards=[
    ('oak_log', '16 unidades', 0xF6B628, '¤32', False, 16),
    ('oak_log', '128 unidades', 0xF6B628, 'Faltan 53', False, 128, {'off': True}),
    ('oak_log', '1.024 unidades', 0xF6B628, 'Faltan 1.845', False, 1024, {'off': True}),
], hero=('oak_log', 'Tronco de roble', 0xF6B628, ['Precio por 16 unidades · llevas 0.', 'Tienes 203 monedas.'], -1, '¤32', None, None, 16),
     input_=('OTRA CANTIDAD (DE 16 EN 16)', 'COMPRAR', ''), footer=[('ATRÁS', B)])
page('p_gts', 'GTS', [('', 'MERCADO'), ('v', 'VENDER'), ('m', 'MIS VENTAS (2)'), ('r', 'RECOGER')], '', [], [
    ('diamond_sword', 'Espada de diamante', T, ['Lo vende Steve · quedan 3 días.'], -1, '¤5.000', ('COMPRAR', GO), None),
    ('elytra', 'Élitros', T, ['Lo vende Alex · quedan 5 días.'], -1, '¤120K', ('SIN SALDO', GR, False), None),
    ('trident', 'Tridente', T, ['Tuyo · quedan 2 días.'], -1, '¤40.000', ('RETIRAR', R), None, True),
    ('totem_of_undying', 'Tótem de la inmortalidad', T, ['Lo vende Notch · quedan 18 h.'], -1, '¤25.000', ('COMPRAR', GO), None),
], input_=('BUSCAR OBJETO O JUGADOR', 'BUSCAR', ''), app='gts', hover_row=0)
page('p_gts_aviso', 'GTS', [('', 'MERCADO'), ('v', 'VENDER'), ('m', 'MIS VENTAS (3)'), ('r', 'RECOGER')], 'v',
     ['Elige qué vender y pon el precio. Estará 7 días a la venta.'], [
         ('rabbit_hide', 'Piel de conejo', T, ['Tienes 12 · se vende de 1 en 1.'], -1, '', ('VENDER', GO), None),
         ('diamond', 'Diamante', T, ['Tienes 3.'], -1, '', ('VENDER', GO), None),
         ('iron_ingot', 'Lingote de hierro', T, ['Tienes 41.'], -1, '', ('VENDER', GO), None),
     ], input_=('PRECIO EN MONEDAS', 'PUBLICAR', '34'), app='gts', tab_hover='m',
     toast=('Publicado: 1x Piel de conejo por 34 monedas. Estará 7 días en el GTS.', 1, 1.2, 0.62))
page('p_aviso_error', 'GTS', [('', 'MERCADO'), ('v', 'VENDER'), ('m', 'MIS VENTAS (3)'), ('r', 'RECOGER')], '', [], [
    ('elytra', 'Élitros', T, ['Lo vende Alex · quedan 5 días.'], -1, '¤120K', ('SIN SALDO', GR, False), None),
], app='gts', toast=('No tienes monedas suficientes.', 2, 0.12, 1.0))
page('p_gts_item', 'GTS', [], '', [], [
    ('', 'Lo que dice el objeto', T, ['Filo V', 'Irrompible III', 'Reparación'], -1, '', None, None),
], hero=('diamond_sword', 'Espada de diamante', 0xF6B628, ['Lo vende Steve · quedan 3 días.', 'Tienes 12.450 monedas.'], -1,
         '¤5.000', None, None), footer=[('ATRÁS', B), ('COMPRAR', GO)], app='gts')

# --- Monedero (1.4: el saldo en grande) ----------------------------------------------------------------------------------
page('p_monedero', 'MONEDERO', [], '', [], [
    ('clock_00', 'Por cobrar del oficio', T, ['Llega en el próximo pago.'], -1, '+¤42', None, None),
    ('emerald', 'A la venta en el GTS', T, ['2 cosas. Cobras al momento cuando se venden.'], -1, '¤45.000', ('VER', B), None),
    ('chest', 'Tienda', T, ['Compra y vende con el servidor.'], -1, '', ('ABRIR', B), None),
    ('iron_pickaxe', 'Oficios', T, ['Gana monedas trabajando.'], -1, '', ('ABRIR', B), None),
], hero=('tf:fantastic_coin', 'Tu saldo', 0xE6A019, ['Lo ganas con oficios, misiones, cazas, la tienda y el GTS.'], -1, '¤12.450', None, None),
     input_=('MANDAR: JUGADOR CANTIDAD', 'PREPARAR', ''), app='monedero')

# --- Viajes, Hogares, Kits ---------------------------------------------------------------------------------------------
page('p_viajes', 'VIAJES', [], '', [], [], app='viajes', hover=4, cards=[
    ('compass_00', 'Spawn', 0x3496FA, 'Mundo normal', False), ('red_bed', 'Tu cama', 0xE83446, 'Mundo normal', False),
    ('filled_map', 'Mercado', 0xF6B628, 'Mundo normal', False), ('ender_pearl', 'Arena PvP', 0x9A5CF0, 'Warp', False),
    ('ender_pearl', 'Nether hub', 0x9A5CF0, 'Warp', False), ('spyglass', 'Explorar', 0x40C850, 'Al azar', False),
    ('oak_door', 'Tus hogares', 0xC27A10, 'Abrir', False),
])
page('p_hogares', 'HOGARES', [], '', ['2 de 5 hogares.'], [
    ('red_bed', 'casa', T, ['Mundo normal · 120, 64, -340 · a 85 m'], -1, '', ('IR', B), ('BORRAR', R)),
    ('crimson_nylium', 'base_nether', T, ['Nether · 40, 70, 12'], -1, '', ('IR', B), ('BORRAR', R)),
], input_=('NOMBRE DEL HOGAR', 'GUARDAR AQUÍ', ''), app='hogares', hover_row=0)
page('p_kits', 'KITS', [], '', ['1 listo para reclamar.'], [], app='kits', cards=[
    ('bundle', 'Kit inicial', 0xAABAD2, 'Reclamado', False, 1, {'tone': 3}), ('bread', 'Constructor', 0x40C850, 'Listo', True, 1, {'tone': 2}),
    ('iron_sword', 'Semanal', 0xF6B628, 'en 3 d 4 h', False), ('diamond', 'VIP', 0xF6B628, 'en 5 h', False),
])
page('p_kit', 'KITS', [], '', [], [], app='kits', cards=[
    ('bread', 'Pan', 0x3496FA, '', False, 16), ('cooked_beef', 'Filete', 0x3496FA, '', False, 8),
    ('torch', 'Antorcha', 0x3496FA, '', False, 32), ('tf:fantastic_coin', '200 monedas', 0xF6B628, '', False),
], hero=('bread', 'Constructor', 0x40C850, ['Para empezar a construir.', 'Cada 12 h · listo para reclamar.'], -1, '', None, None),
     footer=[('ATRÁS', B), ('RECLAMAR', G)])

# --- Protección y Clanes -----------------------------------------------------------------------------------------------
ZT = [('a', 'AJUSTES'), ('m', 'MIEMBROS (2)'), ('b', 'BANEOS'), ('p', 'PARTÍCULAS'), ('x', 'MÁS')]
page('p_proteccion', 'PROTECCIÓN', ZT, 'a', ['Zona 100x100 · Mundo normal · 120, 64, -340'], [
    ('bricks', '¿Pueden construir?', T, [], -1, '', ('SÍ', GR), ('NO', G)),
    ('iron_pickaxe', '¿Pueden romper bloques?', T, [], -1, '', ('SÍ', GR), ('NO', G)),
    ('chest', '¿Pueden abrir cofres?', T, ['Y barriles.'], -1, '', ('SÍ', GR), ('NO', G)),
    ('ender_pearl', '¿Pueden entrar con perlas?', T, ['Lanzando perlas de ender.'], -1, '', ('SÍ', G), ('NO', GR)),
    ('tnt', '¿Las explosiones rompen?', T, ['TNT y creepers.'], -1, '', ('SÍ', G), ('NO', GR)),
], footer=[('ATRÁS', B)], app='protecciones')
page('p_clanes', 'CLANES', [('miembros', 'MIEMBROS'), ('ajustes', 'AJUSTES')], 'miembros',
     ['Los Lobos [LOB] · 3/10 miembros · jefe: Pewez777'], [
         ('player_head', 'Pewez777', 0x1E7C2C, ['Jefe · conectado'], -1, 'JEFE', None, None),
         ('player_head', 'Steve', 0x1E7C2C, ['Miembro · conectado'], -1, '', ('ECHAR', R), None),
         ('player_head', 'Alex', T, ['Miembro · hace 2 días'], -1, '', ('ECHAR', R), None),
     ], input_=('INVITAR A UN JUGADOR', 'INVITAR', ''), app='clanes')

# --- Jugadores ---------------------------------------------------------------------------------------------------------
page('p_jugadores', 'JUGADORES', [], '', ['3 conectados'], [], app='jugadores', hover=1, cards=[
    ('player_head', 'Pewez777 (tú)', 0xF6B628, 'Rey', False), ('player_head', 'Steve', 0x3496FA, 'Mortal', False),
    ('player_head', 'Alex', 0x3496FA, '', False),
])
page('p_regalar', 'JUGADORES', [('f', 'FICHA'), ('r', 'REGALAR')], 'r',
     ['Regalo para Steve: elige un objeto o escribe monedas. Te quedan 9 hoy.'], [], app='jugadores', cells=[
         ('diamond_pickaxe', '', M, False), ('golden_apple', '', M, True), ('bread', '', M, False), ('torch', '', M, False),
         ('iron_ingot', '', M, False), ('arrow', '', M, False), ('bow', '', M, False),
     ], input_=('MONEDAS', 'ENVIAR', ''), footer=[('REGALAR', G)])

# --- Ranking (1.4: el podio) -------------------------------------------------------------------------------------------
page('p_ranking', 'RANKING', [('misiones', 'MISIONES'), ('cazas', 'CAZAS'), ('likes', 'LIKES'), ('exploraciones', 'EXPLORAR'),
                              ('monedas', 'MONEDAS')], 'misiones', [], [
    ('paper', '4. Notch', T, [], -1, '61 misiones', None, None),
    ('paper', '5. Pewez777 (tú)', GOLD, [], -1, '58 misiones', None, None, True),
    ('paper', '6. Herobrine', T, [], -1, '40 misiones', None, None),
], app='ranking', cards=[
    ('player_head', '2º Alex', 0xA9B8CC, '120 misiones', False, 1, {'tone': 4}),
    ('player_head', '1º Steve', 0xF6B628, '154 misiones', False, 1, {'tone': 1, 'badge': 'TOP 1'}),
    ('player_head', '3º Jeb', 0xD08A4E, '97 misiones', False, 1, {'tone': 4}),
])

# --- Armario y Efectos (1.4: tarjetas e imágenes de los efectos) -----------------------------------------------------------
page('p_armario', 'ARMARIO', [('cabeza', 'CABEZA ·'), ('pecho', 'PECHO'), ('piernas', 'PIERNAS'), ('pies', 'PIES'), ('espalda', 'ESPALDA')],
     'cabeza', ['Llevas Corona del Rey. Solo estético.'], [], app='armario', hover=1, cards=[
         ('golden_helmet', 'Corona del Rey', 0x9E5AE0, 'PUESTO', True, 1, {'tone': 2}),
         ('diamond_helmet', 'Yelmo de cristal', 0x9E5AE0, 'Rango Mítico', False, 1, {'tone': 4}),
         ('leather_helmet', 'Sombrero de paja', 0x9E5AE0, 'Crate verano', False, 1, {'tone': 4}),
         ('turtle_helmet', 'Casco marino', 0x9E5AE0, 'PONER', False, 1, {'tone': 4}),
     ], footer=[('QUITAR', R)])
KILLS = json.load(open(os.path.join(FX, 'meta.json'), encoding='utf-8'))['kills']
kill_ids = sorted(KILLS, key=lambda k: (KILLS[k]['order'], k))
page('p_efectos', 'EFECTOS', [('', 'AL MATAR'), ('skills', 'SKILLS')], '',
     ['Equipado: Fuego Infernal · ' + KILLS['fuego_infernal']['desc']], [], app='efectos', hover=2, gallery=[
         ('nether_star', KILLS[k]['name'], KILLS[k]['color'], 'EQUIPADO' if k == 'fuego_infernal' else KILLS[k]['cat'],
          k == 'fuego_infernal', 1, dict(fx('kill', k), tone=2 if k == 'fuego_infernal' else 4)) for k in kill_ids[:12]
     ], footer=[('QUITAR EFECTO', R)])
page('p_efectos_mas', 'EFECTOS', [('', 'AL MATAR'), ('skills', 'SKILLS')], '',
     ['Elige el efecto que sale al matar. Gratis.'], [], app='efectos', gallery=[
         ('nether_star', KILLS[k]['name'], KILLS[k]['color'], KILLS[k]['cat'], False, 1, dict(fx('kill', k), tone=4))
         for k in kill_ids[20:32]
     ])
CLASSES = json.load(open(os.path.join(FX, 'meta.json'), encoding='utf-8'))['classes']
rev = [('lostassets_revenant_lc', 'Tajo Sombrío', 'Ataque básico · Recarga 0,4 s · Un tajo de sombras que hiere a los enemigos de delante.'),
       ('lostassets_revenant_rc', 'Embestida Oscura', 'Recarga 3 s · Te lanzas hacia delante dejando cortes de sombra.'),
       ('lostassets_revenant_slc', 'Hoja de Sombras', 'Recarga 5,5 s · Invocas una hoja de sombras que corta a los enemigos.'),
       ('lostassets_revenant_src', 'Cataclismo del Abismo', 'Recarga 16 s · Abres el abismo: una zona que hiere sin parar.')]
REV = 0x5A3FA8  # PadWardrobe.readable(color de la clase)
page('p_efectos_skills', 'EFECTOS', [('', 'AL MATAR'), ('skills', 'SKILLS')], 'skills', [], [
    ('enchanted_book', n, REV, [d], -1, 'TECLA ' + str(5 + i), None, None, skill_icon('revenant', sid)) for i, (sid, n, d) in enumerate(rev)
] + [
    ('', 'TUS OTRAS CLASES', T, [], -1, '', None, None),
    ('enchanted_book', 'Arquero', 0x5E8A2E, ['Tirador · movilidad y lluvia de flechas', 'A distancia · 6 skills'], -1, '', ('ACTIVAR', G), None,
     fx('class', 'arquero')),
    ('enchanted_book', 'Thor', 0x8A7420, ['Dios del trueno · martillo', 'Cuerpo a cuerpo · 5 skills'], -1, '', ('ACTIVAR', G), None,
     fx('class', 'thor')),
], app='efectos', hero=('enchanted_book', 'Aparecido', CLASSES['revenant']['color'],
                        ['Espadachín · sombras', 'Elige skill con 5-0 y úsala con clic izquierdo.'], -1, 'ACTIVA', ('DESACTIVAR', R), None,
                        fx('class', 'revenant')))

# --- Mi Rango ----------------------------------------------------------------------------------------------------------
page('p_rango', 'MI RANGO', [], '', [], [
    ('golden_helmet', 'Rey', 0xC27A10, ['Kit semanal, 5 hogares, /fly en tus zonas.'], -1, 'TU RANGO', None, None, True),
    ('diamond', 'Mítico', 0x3CB4E6, ['Kit diario, 8 hogares, partículas.'], -1, '', None, None),
    ('iron_ingot', 'Caballero', 0x6A7A99, ['Kit semanal, 3 hogares.'], -1, '', None, None),
], hero=('gold_ingot', 'Rey', 0xC27A10, ['Kit semanal, /fly en tus zonas.', '5 hogares · Más rangos en tierrasfantasticas.com.'], -1,
         'TU RANGO', ('VER', B), None), app='rango')

# --- Ajustes (PadInfoPages.Ajustes, 1.4): interruptores que se deslizan y el volumen con deslizador ---------------------
def ajustes_page(name, values=(True, True, True, 80, False), hover=None, knob=None):
    if ONLY and name not in ONLY:
        return
    from skin import accent as _acc, tagline as _tag, icon_of as _ico  # noqa: E402
    s = Sim()
    s.status('AJUSTES')
    with Local(s):
        X, Y, W, H = s.X, s.Y, s.W, s.H
        acc = _acc('ajustes')
        s.sk_window(X, Y, W, H, acc)
        bh = s.banner_h([1])
        s.sk_banner(X + 1, Y + 1, W - 2, bh, acc, _ico('ajustes'), [_tag('ajustes')])
        names = ['Sonidos del pad', 'Tic al pasar por encima', 'Animaciones', 'Volumen', 'Oír los altavoces de otros']
        about = ['Clics, pestañas y avisos.', 'Un tic suave al pasar el ratón por encima.', 'Fondo animado, brillos y transiciones.',
                 'El de los sonidos del pad.', 'La música que ponen cerca de ti.']
        icons = ['note_block', 'lever', 'firework_rocket', 'jukebox', 'bell']
        top, bottom = Y + bh + 6, Y + H - 14
        row_h = max(22, min(30, (bottom - top) // 5))
        SW = 96
        for i in range(5):
            y, h, x, w = top + i * row_h, row_h - 4, X + 6, W - 12
            hv = hover == i
            slider = i == 3
            yy = s.sk_card(x, y, w, h, 1 if hv and not slider else 0, acc)
            s.vgrad(x + 1, yy + 1, 3, h - 2, lighten(acc, 40), acc)
            ms = min(20, h - 4)
            iy = yy + (h - ms) // 2
            s.sk_slot(x + 8, iy, ms)
            s.item(icons[i], x + 8 + (ms - 16) // 2, iy + (ms - 16) // 2)
            tx = x + 8 + ms + 7
            tw = w - (tx - x) - (SW + 50 if slider else 44)
            two = h >= 24
            ty = yy + ((h - 20) // 2 + 1 if two else (h - 8) // 2)
            s.title(names[i], tx, ty, tw, rgb(0x18265C))
            if two:
                s.mtext(s.fitend(about[i], tw), tx, ty + 10, rgb(0x4A6694))
            if slider:
                sx = x + w - SW - 10
                pct = f'{values[3]}%'
                s.sk_chip(sx - 6 - s.chip_w(pct), yy + (h - 12) // 2, pct, 'blue')
                s.slider(sx, yy + (h - 14) // 2, SW, values[3] / 100, acc, hv)
            else:
                k = knob[i] if knob and knob[i] is not None else (1.0 if values[i] else 0.0)
                s.toggle(x + w - 38, yy + (h - 14) // 2, k, hv)
        note = s.fitend('Tecla del pad: se cambia en Opciones > Controles.', W - 40)
        s.mtext(note, X + (W - s.mwidth(note)) // 2, Y + H - 12, rgb(0x4A6694))
    s.save(OUT + name + '.png')
    NAMES.append(name)


ajustes_page('p_ajustes', hover=3)
ajustes_page('p_ajustes_mov', values=(True, False, True, 40, False), hover=1, knob=(None, 0.45, None, None, None))

# --- Vacíos (estado sin nada) -------------------------------------------------------------------------------------------
page('p_vacio', 'HOGARES', [], '', ['0 de 5 hogares.'], [], empty='Aún no tienes hogares. Escribe un nombre abajo y pulsa GUARDAR AQUÍ.',
     input_=('NOMBRE DEL HOGAR', 'GUARDAR AQUÍ', ''), app='hogares')
page('p_vacio_armario', 'ARMARIO', [('cabeza', 'CABEZA'), ('pecho', 'PECHO'), ('piernas', 'PIERNAS'), ('pies', 'PIES'), ('espalda', 'ESPALDA')],
     'espalda', ['Solo estético: pulsa una pieza para ponértela.'], [],
     empty='No tienes nada para espalda. Los sets de los rangos y de las crates de la tienda web aparecen aquí.', app='armario')

# --- Admin -------------------------------------------------------------------------------------------------------------
page('a_recompensas', 'RECOMPENSAS', [('cal', 'CALENDARIO'), ('otras', 'OTRAS')], 'cal',
     ['El calendario diario: cada día se reclama el siguiente de la racha. Pulsa un día para cambiarlo.'], [
         ('clock_00', 'Calendario', T, ['Encendido.'], -1, '', ('SÍ', G), None),
         ('compass_00', 'Si alguien se salta un día', T, ['Vuelve al día 1.'], -1, '', ('CAMBIAR', B), None),
     ], cards=[
         ('bread', 'Día 1', 0xF6B628, '1 obj. · 25', False, 8), ('torch', 'Día 2', 0xF6B628, '1 obj. · 25', False, 16),
         ('iron_ingot', 'Día 3', 0xF6B628, '1 obj. · 50', False, 4), ('cooked_beef', 'Día 4', 0xF6B628, '1 obj. · 50', False, 12),
         ('golden_carrot', 'Día 5', 0xF6B628, '1 obj. · 75', False, 8),
     ], footer=[('AÑADIR DÍA', G), ('QUITAR EL ÚLTIMO', R)], app='a_recompensas')
page('a_selector', 'KITS', [('i', 'TU INVENTARIO'), ('t', 'TODOS LOS OBJETOS')], 't',
     ['Añadir a Kit inicial (de 16 en 16). 1.312 objetos · página 1 de 14.'], [], app='a_kits', hover=5, cells=[
         (n, '', T, False) for n in ('stone', 'granite', 'diorite', 'andesite', 'grass_block', 'dirt', 'cobblestone', 'oak_planks',
                                     'spruce_planks', 'birch_planks', 'oak_sapling', 'sand', 'gravel', 'gold_ore', 'iron_ore',
                                     'coal_ore', 'oak_log', 'spruce_log', 'birch_log', 'glass', 'lapis_ore', 'sandstone',
                                     'white_wool', 'orange_wool', 'gold_block', 'iron_block', 'bricks', 'tnt', 'bookshelf',
                                     'obsidian', 'torch', 'chest', 'diamond_ore', 'diamond_block', 'crafting_table', 'furnace')
     ], input_=('BUSCAR (NOMBRE O ID)', 'BUSCAR', ''), footer=[('»', B), ('×16', GO), ('ATRÁS', B)])
page('a_tienda_item', 'TIENDA', [], '', ['Minerales · Diamante'], [
    ('gold_ingot', 'Precio de compra', T, ['Lo que paga el jugador por lote. 0 = no se vende.'], -1, '950', ('EDITAR', B), None, True),
    ('emerald', 'Precio de venta', T, ['Lo que cobra el jugador. 0 = la tienda no lo compra.'], -1, '400', ('EDITAR', B), None),
    ('chest', 'Cantidad por lote', T, ['Objetos que van en cada lote.'], -1, '1', ('EDITAR', B), None),
], input_=('NUEVO VALOR', 'GUARDAR', '1200'), footer=[('ATRÁS', B)], app='a_tienda')
page('a_apps', 'APPS', [], '', ['Las que apagues no salen en el pad de los jugadores.'], [
    ('iron_pickaxe', 'Oficios', T, ['Activa'], -1, '', ('SÍ', G), None),
    ('writable_book', 'Misiones', T, ['Activa'], -1, '', ('SÍ', G), None),
    ('crossbow', 'Cazas', M, ['Apagada'], -1, '', ('NO', GR), None),
    ('chest', 'Tienda', T, ['Activa'], -1, '', ('SÍ', G), None),
], app='a_apps')

# Comunidad (PadCommunityPage) con el selector de emojis (PadEmojiPicker): ver comunidad.py
import comunidad  # noqa: E402

comunidad.run(OUT, NAMES)


# Música (PadMusicPage): misma geometría que el Java
import math


def disc(s, cx, cy, r, c):
    for dy in range(-r, r + 1):
        half = int(math.floor(math.sqrt(max(0, r * r - dy * dy + r * 0.8))))
        s.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, c)


def ring(s, cx, cy, r, c):
    steps = max(24, r * 6)
    for i in range(steps):
        a = i * 2 * math.pi / steps
        x, y = cx + round(math.cos(a) * r), cy + round(math.sin(a) * r)
        s.fill(x, y, x + 1, y + 1, c)


def grad(s, x, y, w, h, top, bot):
    for i in range(h):
        t = i / max(1, h - 1)
        s.fill(x, y + i, x + w, y + i + 1, tuple(int(top[k] * (1 - t) + bot[k] * t) for k in range(len(top))))


def glyph_draw(s, rows, cx, cy, c):
    w, h = len(rows[0]), len(rows)
    x0 = cx - s.bigu(w) // 2
    y0 = cy - s.bigu(h) // 2
    with s.big_at(x0, y0):
        for gy, row in enumerate(rows):
            for gx, ch in enumerate(row):
                if ch == '#':
                    s.fill(gx, gy, gx + 1, gy + 1, c)


BROADCAST = ["#.......#", "#..#.#..#", "#.#...#.#", "#.#.#.#.#", "#.#...#.#", "#..#.#..#", "#.......#"]
PLAY = ["#......", "###....", "#####..", "#######", "#####..", "###....", "#......"]
PAUSE = ["##.##"] * 7
NEXT = ["#...#.#", "##..#.#", "###.#.#", "#####.#", "###.#.#", "##..#.#", "#...#.#"]
PREV = ["#.#...#", "#.#..##", "#.#.###", "#.#####", "#.#.###", "#.#..##", "#.#...#"]
SHUFFLE = ["##....##.", "..#..#.##", "...##....", "...##....", "..#..#.##", "##....##.", "........."]
REPEAT = ["..######.", ".#.....##", "#......#.", "#.......#", ".#......#", "##.....#.", ".######.."]
SPEAKER = ["...#.....", "..##..#..", "####...#.", "####.#.#.", "####...#.", "..##..#..", "...#....."]
IMAGE = ["#########", "#.......#", "#.##..#.#", "#.##.###.", "#..#####.", "#.#######", "#########"]
CROSS = ["#...#", ".#.#.", "..#..", ".#.#.", "#...#"]
CROSS8 = ["##....##", "###..###", ".######.", "..####..", "..####..", ".######.", "###..###", "##....##"]
PLUS8 = ["...##...", "...##...", "...##...", "########", "########", "...##...", "...##...", "...##..."]
MINUS8 = ["........", "........", "........", "########", "########", "........", "........", "........"]
CHECK8 = ["......##", ".....###", "#....##.", "##..###.", "###.##..", ".####...", "..###...", "...#...."]
ARROW_LEFT = ["....#", "...##", "..###", ".####", "#####", ".####", "..###", "...##", "....#"]
ARROW_RIGHT = ["#....", "##...", "###..", "####.", "#####", "####.", "###..", "##...", "#...."]


def glyph_at(s, rows, x, y, c):
    for gy, row in enumerate(rows):
        for gx, ch in enumerate(row):
            if ch == '#':
                s.fill(x + gx, y + gy, x + gx + 1, y + gy + 1, c)
LAV, GOLDC, GL, GREENC = rgb(0xB8C2F0), rgb(0xF6B628), rgb(0xFFEC96), rgb(0x7CF0B0)


def music_page(name, tracks, playing=None, job=None, empty=False, hover_row=-1, sure=False, sel_list=0, vol_hover=False,
               lists=('LOFI', 'PARA MINAR'), tab_first=0, picking=False, in_list=(), hover_btn=False):
    if ONLY and name not in ONLY:
        return
    s = Sim()
    s.status('MÚSICA', music=playing is not None)
    local = Local(s)
    local.__enter__()
    X, Y, W, H = s.X, s.Y, s.W, s.H
    leftW = max(140, min(240, round(W * 0.44)))
    barH = max(3, s.bigu(3))
    below = 4 + s.bigu(9) + 2 + 13 + barH + 3 + 10 + s.bigu(22) + 4
    ct = Y + 14  # 1.3.38: la tarjeta empieza a la altura de la ventana de la lista, con su pestaña encima
    c = max(32, min(round((leftW - 16 - s.bigu(16)) / 1.3), Y + H - (ct + 8) - below))
    cx, cy = X + 8, ct + 8
    tl = 'SONANDO' if playing else 'REPRODUCTOR'
    tw = s.pwidth(tl) + 14
    s.box(X + 4, Y, tw, 15, NAVY)
    grad(s, X + 5, Y + 1, tw - 2, 15, rgb(0x323C80), rgb(0x262E66))
    s.fill(X + 6, Y + 1, X + 4 + tw - 2, Y + 3, rgb(0xF6B628))
    s.fill(X + 6, Y + 1, X + 4 + tw - 2, Y + 2, rgb(0xFFEC96))
    s.ptext(tl, X + 4 + tw // 2 - s.pwidth(tl) // 2, Y + 4, rgb(0xFFE680), False)
    hh = Y + H - ct
    s.box(X, ct, leftW, hh, NAVY)
    grad(s, X + 1, ct + 1, leftW - 2, hh - 2, rgb(0x262E66), rgb(0x141A3C))
    s.fill(X + 2, ct + 1, X + leftW - 2, ct + 2, rgb(0x4A56A0))
    for (a, b, cc, d) in ((X + 2, ct + 2, X + leftW - 2, ct + 3), (X + 2, Y + H - 3, X + leftW - 2, Y + H - 2),
                          (X + 2, ct + 2, X + 3, Y + H - 2), (X + leftW - 3, ct + 2, X + leftW - 2, Y + H - 2)):
        s.fill(a, b, cc, d, (246, 182, 40, 85))
    s.fill(X + 5, ct, X + 4 + tw - 1, ct + 2, rgb(0x262E66))
    t = playing
    d = round(c * 0.92)
    slide = 0.30 if t else 0.12
    dcx, dcy, r = cx + c - d // 2 + round(c * slide), cy + c // 2, d // 2
    disc(s, dcx, dcy, r + 1, rgb(0x05060E))
    disc(s, dcx, dcy, r, rgb(0x1C1F2E))
    for k in (0.92, 0.8, 0.68, 0.56):
        ring(s, dcx, dcy, round(r * k), rgb(0x2C3044))
    for arc in (0, math.pi):
        for i in range(14):
            a = math.radians(40) + arc + i * 0.05
            rr = r * 0.48
            while rr < r * 0.9:
                px, py = dcx + round(math.cos(a) * rr), dcy + round(math.sin(a) * rr)
                s.fill(px, py, px + 1, py + 1, (255, 255, 255, 48))
                rr += 2.5
    lab = round(r * 0.36)
    disc(s, dcx, dcy, lab, rgb(0xE8B030))
    ring(s, dcx, dcy, round(lab * 0.75), (255, 255, 255, 64))
    disc(s, dcx, dcy, max(1, lab // 4), rgb(0x05060E))
    s.fill(cx + 2, cy + 2, cx + c + 2, cy + c + 2, (0, 0, 0, 102))
    if t and t.get('cover'):
        s.paste_fit(Image.open(t['cover']).convert('RGBA'), cx, cy, c, c)
    else:
        grad(s, cx, cy, c, c, rgb(0x3A4AA0), rgb(0x1D2550))
        sc = c / 48
        with s.big_at(cx + c / 2 - 16 * sc, cy + c / 2 - 16 * sc, px=sc * s.cs):
            s.blit('icon_musica', 0, 0)
    s.fill(cx - 1, cy - 1, cx + c + 1, cy, rgb(0x0C1030)); s.fill(cx - 1, cy + c, cx + c + 1, cy + c + 1, rgb(0x0C1030))
    s.fill(cx - 1, cy, cx, cy + c, rgb(0x0C1030)); s.fill(cx + c, cy, cx + c + 1, cy + c, rgb(0x0C1030))
    if t:  # ecualizador
        n = max(8, min(24, (c - 6) // s.bigu(5)))
        bw = max(2, (c - 6) // n - 1)
        for i in range(n):
            h = max(2, round(round(c * 0.28) * (0.3 + 0.7 * abs(math.sin(i * 1.7 + 0.5)))))
            bx, bot = cx + 3 + i * ((c - 6) // n), cy + c - 3
            s.fill(bx, bot - h, bx + bw, bot, (24, 38, 92, 153))
            grad(s, bx, bot - h + 1, bw, h - 1, (124, 240, 176, 204), (46, 138, 218, 204))
            s.fill(bx, bot - h, bx + bw, bot - h + 1, (255, 255, 255, 204))
    # volumen
    vx, vtop, vh, vw = X + leftW - 8 - s.bigu(6), cy + s.bigu(16), c - s.bigu(16), s.bigu(6)
    glyph_draw(s, SPEAKER, vx + vw // 2, cy + s.bigu(6), LAV)
    s.box(vx, vtop, vw, vh, rgb(0x0C1030))
    s.fill(vx + 1, vtop + 1, vx + vw - 1, vtop + vh - 1, rgb(0x39407A))
    fh = round((vh - 2) * 0.8)
    grad(s, vx + 1, vtop + vh - 1 - fh, vw - 2, fh, rgb(0x8CF0C8), rgb(0x2EB87A))
    disc(s, vx + vw // 2, vtop + vh - 1 - fh, s.bigu(4) + 1, rgb(0x0C1030))
    disc(s, vx + vw // 2, vtop + vh - 1 - fh, s.bigu(4), GL)
    if vol_hover:  # 1.3.38: el % en una etiqueta a la izquierda del botón
        pct = '80%'
        pw = s.mwidth(pct) + 6
        px, py = vx - 3 - pw, max(vtop - 4, min(vtop + vh - 6, vtop + vh - 1 - fh - 5))
        s.box(px, py, pw, 11, rgb(0x0C1030))
        s.box(px + 1, py + 1, pw - 2, 9, rgb(0x39407A))
        s.mtext(pct, px + 3, py + 2, (255, 255, 255))
    # título
    ty = cy + c + 4
    ay = ty + s.bigu(9) + 2
    title = t['title'] if t else 'Nada sonando'
    with s.big_at(X + 8, ty):
        s.mtext(s.fitend(title, round((leftW - 16 - s.bigu(18)) / s.bs)), 1, 1, (40, 40, 60))
        s.mtext(s.fitend(title, round((leftW - 16 - s.bigu(18)) / s.bs)), 0, 0, (255, 255, 255))
    s.mtext(t['artist'] if t else 'Elige una canción de tu lista', X + 8, ay, LAV)
    if t:
        bx, by = X + leftW - 8 - s.bigu(14), ty
        s.box(bx, by, s.bigu(14), s.bigu(12), (255, 255, 255, 68))
        s.box(bx + 1, by + 1, s.bigu(14) - 2, s.bigu(12) - 2, rgb(0x262E66))
        with s.big_at(bx + s.bigu(3), by + s.bigu(2)):
            for gy, row in enumerate(IMAGE):
                for gx, ch in enumerate(row):
                    if ch == '#':
                        s.fill(gx, gy, gx + 1, gy + 1, LAV)
    # barra
    bx0, by0, bw0 = X + 8, ay + 13, leftW - 16
    f = 0.38 if t else 0
    s.box(bx0, by0, bw0, barH + 2, rgb(0x0C1030))
    s.fill(bx0 + 1, by0 + 1, bx0 + bw0 - 1, by0 + 1 + barH, rgb(0x39407A))
    fw = round((bw0 - 2) * f)
    for i in range(fw):
        k = i / max(1, fw - 1)
        s.fill(bx0 + 1 + i, by0 + 1, bx0 + 2 + i, by0 + 1 + barH, (255, int(201 - 63 * k), int(74 - 14 * k)))
    if t:
        disc(s, bx0 + 1 + fw, by0 + 1 + barH // 2, s.bigu(3) + 1, rgb(0x0C1030))
        disc(s, bx0 + 1 + fw, by0 + 1 + barH // 2, s.bigu(3), GL)
    s.mtext('1:49' if t else '0:00', bx0, by0 + barH + 4, LAV)
    rt = '4:46' if t else '--:--'
    s.mtext(rt, bx0 + bw0 - s.mwidth(rt), by0 + barH + 4, LAV)
    # controles
    y0 = by0 + barH + 13
    playD, small, gap = s.bigu(22), s.bigu(15), s.bigu(6)
    total = playD + small * 5 + gap * 5
    x = X + (leftW - total) // 2
    cym = y0 + playD // 2
    for g, col in ((SHUFFLE, LAV), (PREV, (255, 255, 255))):
        glyph_draw(s, g, x + small // 2, cym, col); x += small + gap
    rr = playD // 2
    disc(s, x + rr, cym, rr + 3, (255, 216, 74, 51))
    disc(s, x + rr, cym, rr + 1, rgb(0x0C1030))
    disc(s, x + rr, cym, rr, GOLDC)
    disc(s, x + rr, cym - round(rr * 0.35), round(rr * 0.55), (255, 255, 255, 51))
    glyph_draw(s, PAUSE if t else PLAY, x + rr + (0 if t else s.bigu(1)), cym, rgb(0x1A2350))
    x += playD + gap
    glyph_draw(s, NEXT, x + small // 2, cym, (255, 255, 255)); x += small + gap
    disc(s, x + small // 2, cym, small // 2, (255, 255, 255, 34))
    glyph_draw(s, REPEAT, x + small // 2, cym, GREENC)
    x += small + gap
    if playing:
        disc(s, x + small // 2, cym, small // 2, (255, 255, 255, 34))
    glyph_draw(s, BROADCAST, x + small // 2, cym, GREENC if playing else LAV)
    # lista
    lx, lw = X + leftW + 6, W - leftW - 6
    top = Y + 14
    bottom = Y + H - 19 - (24 if job else 0)
    s.panel(lx, top, lw, bottom - top)
    # pestañas (PadMusicPage.drawListTabs): TODAS, las que quepan, y a la derecha ‹ › (si no caben) y +
    INK = (24, 38, 92)
    gap, plus, arrow = 2, 18, 13
    todas_w = s.pwidth('TODAS') + 12
    names = [s.pfit(n.upper(), 58) for n in lists]
    widths = [s.pwidth(n) + 12 + (10 if i + 1 == sel_list else 0) for i, n in enumerate(names)]
    strip_x = lx + 4 + todas_w + gap
    end = lx + lw - 4 - plus
    paged = strip_x + sum(w + gap for w in widths) > end - gap
    strip_end = end - gap - 2 * arrow - gap * 2 if paged else end - gap
    last, xx = tab_first - 1, strip_x
    for i in range(tab_first, len(widths)):
        if xx + widths[i] > strip_end:
            break
        xx += widths[i] + gap
        last = i
    chosen = None
    tx = lx + 4
    for i in range(-1, len(names)):
        if i >= 0 and (i < tab_first or i > last):
            continue
        label = 'TODAS' if i < 0 else names[i]
        w = todas_w if i < 0 else widths[i]
        if i + 1 == sel_list:
            chosen = (tx, w, label, i >= 0)
        else:
            s.tab(tx, Y, w, label, 0)
        tx = strip_x if i < 0 else tx + w + gap
    if chosen:
        if not chosen[3]:
            s.tab(chosen[0], Y, chosen[1], chosen[2], 2)
        else:
            s.tab(chosen[0], Y, chosen[1], '', 2)
            s.ptext(chosen[2], chosen[0] + 6, Y + 4, NAVY, False)
            glyph_at(s, CROSS, chosen[0] + chosen[1] - 12 + 2, Y + 3 + 2, rgb(0xC8323C))

    def arrow_btn(ax, ay, g, enabled):
        s.box(ax, ay, 13, 13, rgb(0x6A88B8) if enabled else rgb(0xB4C6DE))
        grad(s, ax + 1, ay + 1, 11, 11, rgb(0xE8F2FC), rgb(0xC8DCF0))
        glyph_at(s, g, ax + (13 - len(g[0])) // 2, ay + (13 - len(g)) // 2, rgb(0x34507E) if enabled else rgb(0xA4B6D0))
    if paged:
        ax = end - gap - 2 * arrow - gap
        arrow_btn(ax, Y + 1, ARROW_LEFT, tab_first > 0)
        arrow_btn(ax + arrow + gap, Y + 1, ARROW_RIGHT, last < len(names) - 1)
    s.tab(end, Y, plus, '', 0)
    glyph_at(s, PLUS8, end + (plus - 8) // 2, Y + 5, rgb(0x34507E))
    rows_top = top + 4
    if sel_list > 0:  # cabecera de la playlist: cuántas lleva y ELEGIR CANCIONES / LISTO
        label = 'LISTO' if picking else 'ELEGIR CANCIONES'
        bw = s.bw(label)
        bx = lx + lw - 6 - bw
        info = ('Marca las que van en «%s»' % lists[sel_list - 1]) if picking else '%d canciones' % len(tracks)
        s.mtext(s.fitend(info, bx - lx - 14), lx + 8, top + 8, rgb(0xC27A10) if picking else rgb(0x4A6694))
        s.button(bx, top + 4, bw, label, 1 if picking else 3, True, hover_btn)
        s.fill(lx + 6, top + 20, lx + lw - 6, top + 21, rgb(0xC8E4F8))
        rows_top = top + 22
    s.scissor(lx + 2, rows_top, lw - 4, bottom - rows_top - 4)
    ry = rows_top

    def small_btn(bx, by, glyph, col, full):
        """PadMusicPage.rowButton: 14x14 redondeado, borde de su color, dibujo de 8x8 justo en el centro."""
        edge = tuple(round(c * 0.72) for c in col)
        s.box(bx, by, 14, 14, edge)
        if full:
            grad(s, bx + 1, by + 1, 12, 12, tuple(round(c + (255 - c) * 0.28) for c in col), col)
            s.fill(bx + 2, by + 1, bx + 12, by + 2, (255, 255, 255, 102))
        else:
            grad(s, bx + 1, by + 1, 12, 12, (255, 255, 255), tuple(round(c + (255 - c) * 0.82) for c in col))
        s.fill(bx + 2, by + 12, bx + 12, by + 13, (0, 0, 0, 85 if full else 51))
        glyph_at(s, glyph, bx + (14 - len(glyph[0])) // 2, by + (14 - len(glyph)) // 2, (255, 255, 255) if full else col)

    for ti, tr in enumerate(tracks):
        x0, w0 = lx + 5, lw - 14
        cur = playing and tr['title'] == playing['title'] and not picking
        hv = ti == hover_row
        inl = picking and ti in in_list
        if cur or hv or inl:
            s.ucard(x0, ry, w0, 29, 2 if cur else 1)
        else:
            s.fill(x0 + 3, ry + 29, x0 + w0 - 3, ry + 30, rgb(0xC8E4F8))
        if tr.get('cover'):
            s.paste_fit(Image.open(tr['cover']).convert('RGBA'), x0 + 5, ry + 3, 24, 24)
        else:
            grad(s, x0 + 5, ry + 3, 24, 24, tr.get('color', rgb(0x5A6AD0)), rgb(0x1D2550))
            with s.big_at(x0 + 5 + 12 - 8, ry + 3 + 4, px=0.5 * s.cs):
                s.blit('icon_musica', 0, 0)
        s.fill(x0 + 4, ry + 2, x0 + 30, ry + 3, INK); s.fill(x0 + 4, ry + 27, x0 + 30, ry + 28, INK)
        s.fill(x0 + 4, ry + 2, x0 + 5, ry + 28, INK); s.fill(x0 + 29, ry + 2, x0 + 30, ry + 28, INK)
        tx = x0 + 35
        dur = tr['dur']
        right = x0 + w0 - 6
        by = ry + (29 - 14) // 2
        if picking:
            small_btn(right - 14, by, CHECK8 if inl else PLUS8, rgb(0x1E9E46), inl or hv)
            right -= 20
        elif hv:
            if sel_list > 0:
                small_btn(right - 14, by, MINUS8, rgb(0x3496FA), hover_btn)
                right -= 20
            elif sure:
                dw = s.bw('¿BORRAR?')
                s.button(right - dw, ry + (29 - 16) // 2, dw, '¿BORRAR?', 2)
                right -= dw + 6
            else:
                small_btn(right - 14, by, CROSS8, rgb(0xE83446), hover_btn)
                right -= 20
        s.mtext(dur, right - s.mwidth(dur), ry + 10, rgb(0x4A6694))
        right -= s.mwidth(dur) + 6
        ttx = tx
        if cur:
            for i, h in enumerate((5, 8, 3)):
                s.fill(tx + i * 3, ry + 12 - h, tx + i * 3 + 2, ry + 12, GOLDC)
            ttx += 12
        s.mtext(s.fitend(tr['title'], right - ttx), ttx, ry + 5, rgb(0xC27A10) if cur else NAVY)
        s.mtext(s.fitend(tr['artist'], right - tx), tx, ry + 16, rgb(0x4A6694))
        ry += 30
    s.no_scissor()
    if empty:
        mid = top + (bottom - top) // 2
        sc = s.bs * 1.25
        with s.big_at(lx + lw / 2 - 16 * sc, mid - 40 - 16 * sc + 6, px=sc * s.cs):
            s.blit('icon_musica', 0, 0)
        for i, l in enumerate(("Pega abajo el link directo de una canción", "(MP3, OGG o WAV) y pulsa AÑADIR.",
                               "Google Drive: «Cualquier persona con el enlace».")):
            l = s.fitend(l, lw - 16)
            s.mtext(l, lx + (lw - s.mwidth(l)) // 2, mid + 10 + i * 11, rgb(0x4A6694) if i == 2 else NAVY)
    if job:
        jy = bottom + 3
        s.ucard(lx, jy, lw, 20, 2)
        s.mtext(job, lx + 15, jy + 3, rgb(0xC27A10))
        s.fill(lx + 6, jy + 13, lx + lw - 4, jy + 16, rgb(0xE8D8A8)); s.fill(lx + 6, jy + 13, lx + 6 + int((lw - 10) * 0.62), jy + 16, GOLDC)
    fy = Y + H - 17
    bw = s.bw('AÑADIR')
    s.field(lx, fy, lw - bw - 4, '', 'Pega aquí el link directo de una canción')
    s.button(lx + lw - bw, fy, bw, 'AÑADIR', 3, False)
    local.__exit__(None, None, None)
    s.save(OUT + name + '.png')
    NAMES.append(name)


COVER = os.path.join(ROOT, '..', 'tierras-fantasticas', 'public', 'img')
cov = '/tmp/claude-0/-home-user-ss/138d1f06-ac1a-56c2-afa1-ae189804074a/scratchpad/mtest/cover2.png'
song = {'title': 'Epic Computer Game Music', 'artist': 'Kris Klavenes', 'cover': cov if os.path.exists(cov) else None, 'dur': '4:46'}
lib = [song, {'title': 'Taberna del Dragón', 'artist': 'Bardo Errante', 'dur': '3:12', 'color': rgb(0xC0563A)},
       {'title': 'Noche en el Bosque Encantado', 'artist': 'Lira de Plata', 'dur': '5:03', 'color': rgb(0x3AA06A)},
       {'title': 'Marcha de los Caballeros', 'artist': 'Orquesta del Reino', 'dur': '2:58', 'color': rgb(0x8A5AD0)}]
music_page('p_musica', lib, playing=song, job='Descargando… 62%')
music_page('p_musica_vacia', [], empty=True)
music_page('p_musica_fila', lib, playing=song, hover_row=1, vol_hover=True)
music_page('p_musica_borrar', lib, playing=song, hover_row=2, sure=True)
music_page('p_musica_lista', lib[:2], playing=song, hover_row=1, sel_list=1)
music_page('p_musica_elegir', lib, playing=song, hover_row=2, sel_list=1, picking=True, in_list=(0, 1))
many = ('Lofi', 'Rock', 'Dad', 'Para correr', 'Lofi noche', 'Jefes', 'Dw', 'Taberna')
music_page('p_musica_muchas', lib, playing=song, sel_list=4, lists=many, tab_first=2, hover_row=0, hover_btn=True)


# ---------------------------------------------------------------------------------------------------------------------
# Gachapón y TF Pass (1.3.38): PadGachaPage / PadPassPage, mismas posiciones que el Java
# ---------------------------------------------------------------------------------------------------------------------
RAR = {'comun': ('Común', 0x8FA3BF), 'raro': ('Raro', 0x3496FA), 'epico': ('Épico', 0xA855F7), 'legendario': ('Legendario', 0xF6B628)}


def item_of(p):
    """El icono de un premio para el simulador (sin «minecraft:»; los del mod con «tf:»)."""
    i = p['id']
    return 'tf:' + i.split(':')[1] if i.startswith('tfclient:') else i.split(':')[-1]


def prize_name(p):
    if p.get('nombre'):
        return p['nombre']
    nm = {'diamond': 'Diamante', 'netherite_ingot': 'Lingote de netherita', 'totem_of_undying': 'Tótem de la inmortalidad',
          'golden_apple': 'Manzana dorada', 'enchanted_golden_apple': 'Manzana de Notch', 'bread': 'Pan', 'iron_ingot': 'Lingote de hierro',
          'experience_bottle': 'Frasco de experiencia', 'golden_carrot': 'Zanahoria dorada', 'ender_pearl': 'Perla de ender',
          'netherite_scrap': 'Chatarra de netherita', 'cooked_beef': 'Filete', 'arrow': 'Flecha', 'lapis_lazuli': 'Lapislázuli'}
    n = nm.get(item_of(p), item_of(p))
    return n + (f" x{p['n']}" if p['n'] > 1 else '')


def dark_card(s, x, top, w, bottom, tab_y, tab):
    """PadDataPage.darkCard (1.4.1): el rótulo, una pastilla oscura con filo de oro y flechita, y la tarjeta oscura."""
    tw, tx, th = max(34, s.pwidth(tab) + 18), x + 4, s.TAB_H
    s.box(tx, tab_y, tw, th, NAVY)
    grad(s, tx + 1, tab_y + 1, tw - 2, th - 2, rgb(0x34408A), rgb(0x1E2558))
    s.fill(tx + 2, tab_y + 1, tx + tw - 2, tab_y + 2, rgb(0xFFE58A))
    s.fill(tx + 2, tab_y + 2, tx + tw - 2, tab_y + 3, (246, 182, 40, 128))
    cx = tx + tw // 2
    for i in range(top - tab_y - th):
        hw = max(0, 2 - i)
        s.fill(cx - hw, tab_y + th + i, cx + hw + 1, tab_y + th + i + 1, NAVY)
    s.ptext(tab, cx - s.pwidth(tab) // 2, tab_y + 3, rgb(0xFFE680), rgb(0x0C1030))
    h = bottom - top
    s.box(x, top, w, h, NAVY)
    grad(s, x + 1, top + 1, w - 2, h - 2, rgb(0x262E66), rgb(0x141A3C))
    s.fill(x + 2, top + 1, x + w - 2, top + 2, rgb(0x4A56A0))
    for (a, b, c, d) in ((x + 2, top + 2, x + w - 2, top + 3), (x + 2, top + h - 3, x + w - 2, top + h - 2),
                         (x + 2, top + 3, x + 3, top + h - 3), (x + w - 3, top + 3, x + w - 2, top + h - 3)):
        s.fill(a, b, c, d, (246, 182, 40, 85))


def price_button(s, x, y, label, price, style, enabled=True):
    n = str(price)
    w = s.pwidth(label) + 6 + 10 + s.pwidth(n) + 14
    s.sk_button(x, y, w, '', style, enabled)
    col = s.button_text(style, enabled)
    shade = s.button_shade(style, enabled)
    tx = x + 7
    s.ptext_shadow(label, tx, y + 2, col, shade)
    tx += s.pwidth(label) + 6
    s.fill(tx - 3, y + 4, tx - 2, y + 11, (255, 255, 255, 102))
    s.blit('coin_green_s', tx, y + 4)
    s.ptext_shadow(n, tx + 10, y + 2, col, shade)
    return w


def darken(c, k):
    return tuple(round(v * k) for v in rgb(c))


def gacha_card(s, p, x, y, win=False):
    CW, CH = 40, 50
    col = rgb(RAR[p['r']][1])
    if win:
        s.box(x - 3, y - 3, CW + 6, CH + 6, col + (200,))
    s.box(x, y, CW, CH, col)
    s.vgrad(x + 1, y + 1, CW - 2, CH - 2, tint(col, 0.45), tint(col, 0.85))
    s.fill(x + 2, y + 1, x + CW - 2, y + 2, (255, 255, 255, 136))
    s.stretch('glow', x + 2, y + 3, CW - 4, CW - 6)
    s.item(item_of(p), x + (CW - 32) // 2, y + 5, 2)
    if p['tipo'] == 'objeto' and p['n'] > 1:
        s.count(p['n'], x + (CW - 32) // 2, y + 5, 2)
    s.fill(x + 1, y + CH - 11, x + CW - 1, y + CH - 1, darken(RAR[p['r']][1], 0.55) + (204,))
    if p['tipo'] != 'objeto':
        q = f"{p['n']:,}".replace(',', '.') if p['n'] < 10000 else f"{p['n'] // 1000}K"
        s.ptext(q, x + CW // 2 - s.pwidth(q) // 2, y + CH - 12, (255, 255, 255))


from sim import tint  # noqa: E402

PREMIOS = [
    {'tipo': 'monedas', 'n': 5000, 'id': 'tfclient:fantastic_coin', 'nombre': '5.000 monedas', 'r': 'legendario', 'p': 0.67},
    {'tipo': 'objeto', 'n': 1, 'id': 'minecraft:netherite_ingot', 'nombre': '', 'r': 'legendario', 'p': 0.53},
    {'tipo': 'objeto', 'n': 2, 'id': 'minecraft:totem_of_undying', 'nombre': '', 'r': 'legendario', 'p': 0.4},
    {'tipo': 'monedas', 'n': 1500, 'id': 'tfclient:fantastic_coin', 'nombre': '1.500 monedas', 'r': 'epico', 'p': 2.45},
    {'tipo': 'objeto', 'n': 8, 'id': 'minecraft:diamond', 'nombre': '', 'r': 'epico', 'p': 2.05},
    {'tipo': 'objeto', 'n': 1, 'id': 'minecraft:enchanted_golden_apple', 'nombre': '', 'r': 'epico', 'p': 1.64},
    {'tipo': 'monedas', 'n': 500, 'id': 'tfclient:fantastic_coin', 'nombre': '500 monedas', 'r': 'raro', 'p': 5.84},
    {'tipo': 'objeto', 'n': 3, 'id': 'minecraft:diamond', 'nombre': '', 'r': 'raro', 'p': 5.84},
    {'tipo': 'objeto', 'n': 2, 'id': 'minecraft:golden_apple', 'nombre': '', 'r': 'raro', 'p': 4.38},
    {'tipo': 'monedas', 'n': 100, 'id': 'tfclient:fantastic_coin', 'nombre': '100 monedas', 'r': 'comun', 'p': 11.92},
    {'tipo': 'objeto', 'n': 16, 'id': 'minecraft:bread', 'nombre': '', 'r': 'comun', 'p': 9.54},
]


def gacha_page(name, tab='premios', result=None, offset=None, balance=7, fbw=1920, fbh=1080):
    if ONLY and name not in ONLY:
        return
    s = Sim(fbw=fbw, fbh=fbh)
    s.status('GACHAPÓN')
    loc = Local(s)
    loc.__enter__()
    X, Y, W, H = s.X, s.Y, s.W, s.H
    lw = max(210, min(270, round(W * 0.56)))
    x, w, top, bottom = X, lw, Y + s.TABS, Y + H
    dark_card(s, x, top, w, bottom, Y, 'RULETA')
    s.blit('coin_green', x + 8, top + 6)
    bal = f'{balance} monedas verdes'
    s.mtext(bal, x + 22, top + 8, rgb(0x8CF0B4))
    hint = 'Se ganan en el TF Pass'
    if s.mwidth(bal) + s.mwidth(hint) + 40 <= w:
        s.mtext(hint, x + w - 8 - s.mwidth(hint), top + 8, rgb(0x8C96C8))
    by = bottom - 6 - 16
    py = by - 13
    many = bool(result) and len(result) > 1
    rh = 58
    block = rh + 6 + 10 + (26 if many else 0)
    zt, zb = top + 20, py - 4
    rx, ry, rw = x + 8, zt + max(2, (zb - zt - block) // 2), w - 16
    s.box(rx - 1, ry - 1, rw + 2, rh + 2, rgb(0x0C1030))
    s.vgrad(rx, ry, rw, rh, rgb(0x0E1432), rgb(0x1C2452))
    center = rx + rw // 2
    s.scissor(rx, ry, rw, rh)
    # 1.4: rayas que corren, luz del color de la app bajo la flecha y, con premio, rayos de su rareza
    s.tiled('stripes', rx, ry, rw, rh, 0.05, off=s.T * 6)
    s.glow(center, ry + rh / 2, rh * 0.95, accent('gachapon'), 110 if result else 70)
    if result:
        s.rays(center, ry + rh / 2, rh * 1.1, 0.55, s.T * 30)
        s.glow(center, ry + rh / 2, rh * 0.7, rgb(RAR[result[0]['r']][1]), 150)
    strip = [PREMIOS[(i * 7) % len(PREMIOS)] for i in range(40)]
    win_i = None
    if result:
        win_i = 20
        strip[win_i] = result[0]
        off = win_i * 44 + 5
    else:
        off = offset if offset is not None else 130
    first = int((off - rw / 2) // 44) - 1
    for i in range(first, first + rw // 44 + 4):
        cx = round(center + i * 44 - off)
        gacha_card(s, strip[i % len(strip)], cx - 20, ry + 4, win=(i == win_i))
    for i in range(16):
        a = int(150 * (1 - i / 16))
        s.fill(rx + i, ry, rx + i + 1, ry + rh, (12, 16, 48, a))
        s.fill(rx + rw - 1 - i, ry, rx + rw - i, ry + rh, (12, 16, 48, a))
    if result:
        for i, (dx, dy) in enumerate(((-30, 6), (28, 10), (-24, rh - 10), (32, rh - 14), (0, 3))):
            s.sparkle(center + dx, ry + dy, 2, s.twinkle(i * 1.3 + 0.2))
    s.no_scissor()
    s.fill(center, ry, center + 1, ry + rh, (255, 230, 128, 102))
    for i in range(4):
        c = rgb(0xC07818) if i == 0 else rgb(0xF6B628)
        s.fill(center - 3 + i, ry - 2 + i, center + 4 - i, ry - 1 + i, c)
        s.fill(center - 3 + i, ry + rh + 1 - i, center + 4 - i, ry + rh + 2 - i, c)
    ly = ry + rh + 6
    if result:
        best = result[0]
        rn = RAR[best['r']][0].upper()
        text = ('MEJOR: ' + rn) if len(result) > 1 else '¡' + rn + '!'
        nm = prize_name(best)
        tw = s.pwidth(text) + s.mwidth(nm) + 10
        tx = x + (w - tw) // 2
        s.ptext(text, tx, ly - 1, lighten(rgb(RAR[best['r']][1]), 30))
        s.mtext(s.fitend(nm, w - 16 - s.pwidth(text) - 10), tx + s.pwidth(text) + 8, ly, (255, 255, 255))
    else:
        how = 'Una tirada: 1 · 5 tiradas: 5'
        s.mtext(how, x + (w - s.mwidth(how)) // 2, ly, rgb(0xB8C2F0))
    w1 = s.pwidth('GIRAR') + 6 + 10 + s.pwidth('1') + 14
    w5 = s.pwidth('GIRAR X5') + 6 + 10 + s.pwidth('5') + 14
    bx = x + (w - w1 - w5 - 8) // 2
    price_button(s, bx, by, 'GIRAR', 1, 3, balance >= 1)
    price_button(s, bx + w1 + 8, by, 'GIRAR X5', 5, 1, balance >= 5)
    pity = 'Épico o mejor asegurado en 7 tiradas'
    s.mtext(pity, x + (w - s.mwidth(pity)) // 2, py, lighten(rgb(0xA855F7), 40))
    if result and len(result) > 1:
        sz, gap, n = 22, 4, len(result)
        sx = x + (w - (n * sz + (n - 1) * gap)) // 2
        sy = ly + 14
        if sy + sz <= py - 2:
            for i, p in enumerate(result):
                cx = sx + i * (sz + gap)
                s.slot(cx, sy, sz, sz, rgb(RAR[p['r']][1]))
                s.item(item_of(p), cx + 3, sy + 3)
                if p['tipo'] == 'objeto' and p['n'] > 1:
                    s.count(p['n'], cx + 3, sy + 3)
    # derecha: PREMIOS / HISTORIAL
    lx, lw2 = X + lw + 6, W - lw - 6
    acc = accent('gachapon')
    s.sk_window(lx, top, lw2, bottom - top, acc)
    s.vgrad(lx + 1, top + 1, lw2 - 2, 3, mix(acc, (255, 255, 255), 0.14), darken_t(acc, 0.78))
    s.sk_tabs(lx + 4, Y, lw2 - 8, ['PREMIOS', 'HISTORIAL'], 0 if tab == 'premios' else 1, acc)
    rows = PREMIOS if tab == 'premios' else [dict(r, t=m) for r, m in zip(result or PREMIOS[3:9], ('ahora', 'ahora', 'hace 3 min', 'hace 2 h', 'hace 1 d', 'hace 2 d'))]
    s.scissor(lx + 2, top + 6, lw2 - 4, bottom - top - 10)
    ry2 = top + 6
    for p in rows:
        x0, w0 = lx + 5, lw2 - 14
        col = rgb(RAR[p['r']][1])
        s.fill(x0 + 3, ry2 + 23, x0 + w0 - 3, ry2 + 24, rgb(0xC8E4F8))
        s.vgrad(x0, ry2 + 3, 2, 18, lighten(col, 50), col)
        s.sk_slot(x0 + 4, ry2 + 2, 20, col)
        s.item(item_of(p), x0 + 6, ry2 + 4)
        if p['tipo'] == 'objeto' and p['n'] > 1:
            s.count(p['n'], x0 + 6, ry2 + 4)
        x0, w0 = x0 + 3, w0 - 3
        right = (f"{p['p']:.2f}".rstrip('0').rstrip('.').replace('.', ',') + ' %') if tab == 'premios' else p['t']
        rw2 = s.mwidth(right)
        s.mtext(right, x0 + w0 - 4 - rw2, ry2 + 8, rgb(0x4A6694))
        room = w0 - 26 - rw2 - 10
        s.mtext(s.fitend(prize_name(p), room), x0 + 26, ry2 + 3, NAVY)
        s.mtext(RAR[p['r']][0], x0 + 26, ry2 + 13, darken(RAR[p['r']][1], 0.7))
        ry2 += 24
    s.no_scissor()
    content = len(rows) * 24
    s.sk_scrollbar(lx + lw2 - 7, top + 7, bottom - top - 12, bottom - top - 10, content, 0, acc)
    loc.__exit__(None, None, None)
    s.save(OUT + name + '.png')
    NAMES.append(name)


def lighten(c, n):
    return tuple(min(255, v + n) for v in c)


R5 = [{'tipo': 'objeto', 'n': 1, 'id': 'minecraft:totem_of_undying', 'nombre': '', 'r': 'epico'},
      {'tipo': 'monedas', 'n': 100, 'id': 'tfclient:fantastic_coin', 'nombre': '100 monedas', 'r': 'comun'},
      {'tipo': 'objeto', 'n': 3, 'id': 'minecraft:diamond', 'nombre': '', 'r': 'raro'},
      {'tipo': 'objeto', 'n': 16, 'id': 'minecraft:bread', 'nombre': '', 'r': 'comun'},
      {'tipo': 'monedas', 'n': 500, 'id': 'tfclient:fantastic_coin', 'nombre': '500 monedas', 'r': 'raro'}]
gacha_page('p_gachapon', 'premios')
gacha_page('p_gachapon_x5', 'historial', result=R5, balance=2)
gacha_page('p_gachapon_1366', 'premios', result=[PREMIOS[1]], balance=0, fbw=1366, fbh=768)


def pass_levels():
    out = []
    for n in range(1, 51):
        if n % 10 == 0:
            big = ['diamond', 'enchanted_golden_apple', 'totem_of_undying', 'netherite_scrap', 'netherite_ingot'][n // 10 - 1]
            ps = [{'tipo': 'verdes', 'n': 10 if n == 50 else 5, 'id': 'tfclient:fantastic_coin_green', 'nombre': f"{10 if n == 50 else 5} monedas verdes"},
                  {'tipo': 'objeto', 'n': 8 if big == 'diamond' else 1, 'id': 'minecraft:' + big, 'nombre': ''}]
        elif n % 5 == 0:
            ps = [{'tipo': 'verdes', 'n': 3, 'id': 'tfclient:fantastic_coin_green', 'nombre': '3 monedas verdes'},
                  {'tipo': 'monedas', 'n': 250 * (n // 5 + 1), 'id': 'tfclient:fantastic_coin', 'nombre': f'{250 * (n // 5 + 1)} monedas'}]
        elif n % 2:
            ps = [{'tipo': 'verdes', 'n': 1, 'id': 'tfclient:fantastic_coin_green', 'nombre': '1 moneda verde'}]
        elif n % 4 == 0:
            ps = [{'tipo': 'monedas', 'n': 100 + n * 10, 'id': 'tfclient:fantastic_coin', 'nombre': f'{100 + n * 10} monedas'}]
        else:
            it = ['bread', 'iron_ingot', 'experience_bottle', 'golden_carrot', 'diamond', 'ender_pearl', 'golden_apple', 'lapis_lazuli'][(n // 2) % 8]
            ps = [{'tipo': 'objeto', 'n': {'bread': 16, 'lapis_lazuli': 32, 'diamond': 2, 'ender_pearl': 4, 'golden_apple': 2}.get(it, 8), 'id': 'minecraft:' + it, 'nombre': ''}]
        out.append((n, ps))
    return out


def pass_page(name, lv=12, claimed=10, scroll_to=None, hover=None, fbw=1920, fbh=1080):
    if ONLY and name not in ONLY:
        return
    s = Sim(fbw=fbw, fbh=fbh)
    s.status('TF PASS')
    loc = Local(s)
    loc.__enter__()
    X, Y, W, H = s.X, s.Y, s.W, s.H
    NODE, GAP, PITCH, HERO = 46, 6, 52, 50
    bottom_h = max(40, min(52, H - HERO - 8 - 92))
    track_h = H - HERO - 8 - bottom_h
    levels = pass_levels()
    ready = [n for n, _ in levels if claimed < n <= lv]
    # cabecera
    x, y, w, h = X, Y, W, HERO
    s.fill(x + 2, y + h, x + w - 2, y + h + 1, (40, 110, 200, 90))
    s.box(x, y, w, h, NAVY)
    s.vgrad(x + 1, y + 1, w - 2, h - 2, rgb(0x1E6B52), rgb(0x14284F))
    s.texture(x + 1, y + 1, w - 2, h - 2)
    s.tiled('stripes', x + 1, y + 1, w - 2, h - 2, 0.06, off=s.T * 3)
    s.fill(x + 2, y + 1, x + w - 2, y + 2, rgb(0x5FD39A))
    s.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, rgb(0xC07818))
    s.corners(x, y, w, h)
    sx, sy, ss = x + 8, y + 6, 38
    clip = s.clip
    s.scissor(x + 1, y + 1, w - 2, h - 2)
    s.rays(sx + ss / 2, sy + ss / 2, 48, 0.6, s.T * 18)
    s.glow(sx + ss / 2, sy + ss / 2, 30, rgb(0x8CF0B4), 110)
    s.glow(x + w * 0.62, y + h / 2, 60, rgb(0x5FD39A), 40)
    s.clip = clip
    cx = sx + ss // 2
    for row in range(ss):
        half = ss // 4 + row if row < ss // 4 else ss // 4 + (ss - 1 - row) if row > ss * 3 // 4 else ss // 2
        half = min(half, ss // 2)
        s.fill(cx - half, sy + row, cx + half, sy + row + 1, rgb(0x0C1030))
        if half > 2 and 0 < row < ss - 1:
            s.fill(cx - half + 1, sy + row, cx + half - 1, sy + row + 1, rgb(0xFFE58A) if row < ss // 2 else rgb(0xF6B628))
            if half > 5 and 2 < row < ss - 3:
                s.fill(cx - half + 3, sy + row, cx + half - 3, sy + row + 1, rgb(0x3FCB7C) if row < ss // 2 else rgb(0x1E8A50))
    s.ptext('NIVEL', cx - s.pwidth('NIVEL') // 2, sy + 3, (255, 255, 255))
    with s.big_at(cx, sy + 17, px=s.f * 2):
        n = str(lv)
        s.mtext(n, -s.mwidth(n) // 2 + 1, 1, (60, 60, 60))
        s.mtext(n, -s.mwidth(n) // 2, 0, (255, 255, 255))
    tx = sx + ss + 9
    s.ptext('TF PASS', tx, y + 4, rgb(0xFFD650))
    right = x + w - 6
    if ready:
        label = f'RECLAMAR TODO ({len(ready)})'
        bw = s.bw(label)
        s.sk_button(right - bw, y + h - 22, bw, label, 3)
    clock = 'Termina en 47 d 6 h'
    cw = s.chip_w(clock) + 12
    s.box(right - cw, y + 5, cw, 12, rgb(0x0C1030))
    s.vgrad(right - cw + 1, y + 6, cw - 2, 10, rgb(0x2A3470), rgb(0x1A2050))
    cx0, cy0 = right - cw + 4, y + 7
    s.fill(cx0 + 1, cy0, cx0 + 6, cy0 + 1, rgb(0xFFE9A8)); s.fill(cx0 + 1, cy0 + 7, cx0 + 6, cy0 + 8, rgb(0xFFE9A8))
    s.fill(cx0 + 2, cy0 + 1, cx0 + 5, cy0 + 3, rgb(0xF6B628)); s.fill(cx0 + 3, cy0 + 3, cx0 + 4, cy0 + 5, rgb(0xF6B628))
    s.fill(cx0 + 2, cy0 + 5, cx0 + 5, cy0 + 7, rgb(0xC27A10))
    s.mtext(clock, right - cw + 14, y + 7, rgb(0xFFE9A8))
    gt = '7'
    gw = s.mwidth(gt) + 18
    gx = right - cw - 4 - gw
    s.box(gx, y + 5, gw, 12, rgb(0x0C1030))
    s.vgrad(gx + 1, y + 6, gw - 2, 10, rgb(0x1E6B52), rgb(0x124436))
    s.blit('coin_green_s', gx + 3, y + 7)
    s.mtext(gt, gx + 13, y + 7, rgb(0x8CF0B4))
    season = 'Temporada 1: El Despertar'
    s.mtext(s.fitend(season, gx - 6 - tx), tx, y + 15, (255, 255, 255))
    bar_w = max(60, ((right - s.bw(f'RECLAMAR TODO ({len(ready)})') - 8) if ready else right) - tx)
    bx0, by0 = tx, y + 28
    s.box(bx0, by0, bar_w, 8, rgb(0x0C1030))
    s.fill(bx0 + 1, by0 + 1, bx0 + bar_w - 1, by0 + 7, rgb(0x233066))
    fw = round((bar_w - 2) * 0.6)
    s.vgrad(bx0 + 1, by0 + 1, fw, 6, rgb(0x8CF0A8), rgb(0x1E9E46))
    s.fill(bx0 + 1, by0 + 1, bx0 + 1 + fw, by0 + 2, rgb(0xD8FFE2))
    s.mtext('240 / 400 XP', bx0, by0 + 10, rgb(0xE0ECFF))
    nx = f'nivel {lv + 1}'
    s.mtext(nx, bx0 + bar_w - s.mwidth(nx), by0 + 10, rgb(0xB8C2F0))
    for i, (dx, dy) in enumerate(((w - 20, 22), (ss + 14, 4), (w // 2, h - 5))):
        s.sparkle(x + dx, y + dy, 2, s.twinkle(i * 1.7 + 0.6) * 0.9)
    s.sheen(x + 1, y + 1, w - 2, h - 2, period=8.0, alpha=0.12)
    # pista
    top = Y + HERO + 4
    s.sk_window(X, top, W, track_h, accent('pase'))
    trx, trw = X + 16, W - 32
    max_scroll = max(0, 50 * PITCH - GAP - trw)
    at = scroll_to or (ready[0] if ready else lv + 1)
    scroll = max(0, min(max_scroll, (at - 1) * PITCH + NODE / 2 - trw / 2))
    for (ax, left, on) in ((X + 3, True, scroll > 0), (X + W - 13, False, scroll < max_scroll)):
        ay = top + track_h // 2 - 9
        s.box(ax, ay, 10, 18, rgb(0x6A88B8) if on else rgb(0xC6D6EA))
        s.box(ax + 1, ay + 1, 8, 16, rgb(0xF2F8FF) if on else rgb(0xEEF3F9))
        acx, acy = ax + 5, ay + 9
        for i in range(4):
            xx = acx - 2 + i if left else acx + 1 - i
            s.fill(xx, acy - i, xx + 1, acy + i + 1, rgb(0x34507E) if on else rgb(0xB4C6DC))
    rail = top + 12
    s.scissor(trx, top + 2, trw, track_h - 4)
    x0 = round(trx - scroll + NODE / 2)
    x1 = round(trx - scroll + 49 * PITCH + NODE / 2)
    s.fill(x0, rail - 2, x1, rail + 2, rgb(0x9DB8D8)); s.fill(x0, rail - 1, x1, rail + 1, rgb(0xDCE8F4))
    xl = round(trx - scroll + (lv - 1) * PITCH + NODE / 2)
    s.fill(x0, rail - 2, xl, rail + 2, rgb(0xC07818)); s.fill(x0, rail - 1, xl, rail + 1, rgb(0xF6B628))
    s.fill(x0, rail - 1, xl, rail, rgb(0xFFE58A))
    for n, ps in levels:
        nx0 = round(trx - scroll + (n - 1) * PITCH)
        if nx0 + NODE < trx - 2 or nx0 > trx + trw + 2:
            continue
        state = 2 if n <= claimed else 1 if n <= lv else 0
        mile = n % 5 == 0
        num = str(n)
        pw = max(16, s.pwidth(num) + 8)
        px, py = nx0 + (NODE - pw) // 2, rail - 6
        s.box(px, py, pw, 12, NAVY)
        s.vgrad(px + 1, py + 1, pw - 2, 10, rgb(0xFFE58A) if state else rgb(0xE2EFFB), rgb(0xF6B628) if state else rgb(0xB8CDE6))
        s.fill(px + 1, py + 10, px + pw - 1, py + 11, rgb(0xC07818) if state else rgb(0x6A88B8))
        s.ptext(num, nx0 + NODE // 2 - s.pwidth(num) // 2, py + 1, rgb(0x5A3A00) if state else rgb(0x34507E), False)
        cy = rail + 10
        ch = top + track_h - 6 - cy
        hv = n == hover
        edge = rgb(0x2EB85A) if state == 1 else rgb(0xC07818) if mile else rgb(0x3496FA) if hv else rgb(0xBBD3EC)
        if state == 1:
            s.box(nx0 - 2, cy - 2, NODE + 4, ch + 4, (61, 214, 110, 130))
        if n == lv:
            s.box(nx0 - 2, cy - 2, NODE + 4, ch + 4, rgb(0xF6B628))
        s.box(nx0, cy, NODE, ch, edge)
        topc = rgb(0xFFF6DA) if state == 2 else rgb(0xEAFFF0) if state == 1 else rgb(0xFFF7E0) if mile else (255, 255, 255)
        botc = rgb(0xF5DEA0) if state == 2 else rgb(0xBDEFCB) if state == 1 else rgb(0xFFE7A8) if mile else rgb(0xE4EEF8)
        s.vgrad(nx0 + 1, cy + 1, NODE - 2, ch - 2, topc, botc)
        s.fill(nx0 + 2, cy + 1, nx0 + NODE - 2, cy + 2, (255, 255, 255))
        if mile:
            s.corners(nx0, cy, NODE, ch)
        iy = cy + 4
        s.stretch('glow', nx0 + 4, iy - 2, NODE - 8, 36)
        s.item(item_of(ps[0]), nx0 + (NODE - 32) // 2, iy, 2)
        if ps[0]['tipo'] == 'objeto' and ps[0]['n'] > 1:
            s.count(ps[0]['n'], nx0 + (NODE - 32) // 2, iy, 2)
        if len(ps) > 1:
            s.slot(nx0 + NODE - 19, iy + 20, 18, 18)
            s.item(item_of(ps[1]), nx0 + NODE - 18, iy + 21)
            if ps[1]['tipo'] == 'objeto' and ps[1]['n'] > 1:
                s.count(ps[1]['n'], nx0 + NODE - 18, iy + 21)
        if ps[0]['tipo'] != 'objeto':
            q = 'x' + f"{ps[0]['n']:,}".replace(',', '.')
            qw = s.mwidth(q) + 2
            s.box(nx0 + 3, iy + 24, qw + 1, 9, rgb(0x22346E))
            s.mtext(q, nx0 + 4, iy + 25, (255, 255, 255))
        sy2 = max(cy + ch - 16, iy + 34)
        if state == 1:
            s.vgrad(nx0 + 3, sy2, NODE - 6, 12, rgb(0x46D272), rgb(0x1A9442))
            s.fill(nx0 + 3, sy2, nx0 + NODE - 3, sy2 + 1, rgb(0xA8F8C0))
            s.fill(nx0 + 3, sy2 + 12, nx0 + NODE - 3, sy2 + 13, rgb(0x106A2E))
            s.ptext_shadow('COBRAR', nx0 + NODE // 2 - s.pwidth('COBRAR') // 2, sy2 + 1, (255, 255, 255), rgb(0x0E5626))
        elif state == 2:
            for ax, ay in ((6, 0), (5, 1), (6, 1), (4, 2), (5, 2), (0, 2), (0, 3), (1, 3), (3, 3), (4, 3), (1, 4), (2, 4), (3, 4), (2, 5)):
                s.put(nx0 + NODE // 2 - 4 + ax, sy2 + 4 + ay, rgb(0xC27A10))
        else:
            lx0, ly0, c = nx0 + NODE // 2 - 3, sy2 + 3, rgb(0x8EA4C4)
            s.fill(lx0 + 2, ly0, lx0 + 5, ly0 + 1, c); s.fill(lx0 + 1, ly0 + 1, lx0 + 2, ly0 + 4, c)
            s.fill(lx0 + 5, ly0 + 1, lx0 + 6, ly0 + 4, c); s.fill(lx0, ly0 + 3, lx0 + 7, ly0 + 8, c)
            s.fill(lx0 + 3, ly0 + 5, lx0 + 4, ly0 + 7, (255, 255, 255))
        if n == lv:
            yw = s.pwidth('TÚ') + 8
            yx, yy = nx0 + NODE - yw + 1, cy - 7
            s.box(yx, yy, yw, 12, NAVY)
            s.vgrad(yx + 1, yy + 1, yw - 2, 10, rgb(0xFFE58A), rgb(0xF6B628))
            s.fill(yx + 1, yy + 10, yx + yw - 1, yy + 11, rgb(0xC07818))
            s.ptext('TÚ', yx + 4, yy + 1, rgb(0x5A3A00), False)
    s.no_scissor()
    for i in range(10):
        a = int(110 * (1 - i / 10))
        if scroll > 1:
            s.fill(trx + i, top + 2, trx + i + 1, top + track_h - 2, (255, 255, 255, a))
        if scroll < max_scroll - 1:
            s.fill(trx + trw - 1 - i, top + 2, trx + trw - i, top + track_h - 2, (255, 255, 255, a))
    # abajo
    btop = top + track_h + 4
    s.sk_window(X, btop, W, bottom_h, accent('pase'))
    src = ['+100 Misión diaria', '+500 Misión semanal', '+40 Caza']
    head = 'Cómo se gana XP'
    colw = min(round(W * 0.52), max(s.bwidth(head), sum(s.chip_w(t) + 4 for t in src) - 4))
    cx2 = X + W - 6 - colw
    s.fill(cx2 - 6, btop + 4, cx2 - 5, btop + bottom_h - 4, rgb(0xC6DAEE))
    s.title(head, cx2, btop + 5, colw, GOLD_TXT)
    chx, chy = cx2, btop + 17
    for t in src:
        cw = s.chip_w(t)
        if chx > cx2 and chx + cw > cx2 + colw:
            chx, chy = cx2, chy + 13
        if chy + 11 > btop + bottom_h - 2:
            break
        s.chip(chx, chy, t, 'green', 11)
        chx += cw + 4
    show = hover or (ready[0] if ready else lv + 1)
    ps = levels[show - 1][1]
    state = 2 if show <= claimed else 1 if show <= lv else 0
    lx0, room = X + 6, cx2 - 12 - (X + 6)
    title = f'Nivel {show}' + (' · hito' if show % 5 == 0 else '')
    st = 'Ya es tuyo' if state == 2 else '¡Listo para reclamar!' if state == 1 else f'Te faltan {(show - lv) * 400 - 240:,} XP'.replace(',', '.')
    s.title(title, lx0, btop + 5, room, NAVY)
    tw = s.title_w(title, room)
    if tw + 8 + s.chip_w(st) <= room:
        s.chip(lx0 + tw + 6, btop + 3, st, 'gold' if state == 2 else 'green' if state == 1 else 'gray')
    px = lx0
    for p in ps:
        nm = prize_name(p)
        if lx0 + room - px < 50:
            break
        s.slot(px, btop + 18, 18, 18)
        s.item(item_of(p), px + 1, btop + 19)
        fit = s.fitend(nm, lx0 + room - px - 22)
        s.mtext(fit, px + 22, btop + 23, NAVY)
        px += 22 + s.mwidth(fit) + 10
    loc.__exit__(None, None, None)
    s.save(OUT + name + '.png')
    NAMES.append(name)


GOLD_TXT = rgb(0xC27A10)
pass_page('p_pase')
pass_page('p_pase_hito', lv=23, claimed=23, hover=30)
pass_page('p_pase_1366', lv=4, claimed=1, fbw=1366, fbh=768)

# hoja con todo
ims = [(lambda im: im.crop((150, 200, im.width - 150, im.height - 170)).resize((1400, 760)))(Image.open(OUT + n + '.png')) for n in NAMES]
w, h = ims[0].size
rows = (len(ims) + 1) // 2
sheet = Image.new('RGBA', (w * 2 + 10, (h + 10) * rows), (20, 24, 40, 255))
for i, im in enumerate(ims):
    sheet.alpha_composite(im, ((i % 2) * (w + 10), (i // 2) * (h + 10)))
sheet = sheet.resize((sheet.width // 2, sheet.height // 2), Image.LANCZOS)
sheet.save(OUT + 'hoja_apps.png')
print('ok', len(NAMES))
