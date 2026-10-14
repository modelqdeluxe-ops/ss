"""Vistas previas de las apps del pad: python3 tools/pad/sim/pages.py <carpeta de salida>.

Dibuja las apps con el mismo layout que PadViewPage (pestañas, cabecera, rejilla y filas), PadCommunityPage y
PadCameraPage, con la fuente de Minecraft, y una hoja con todas (hoja_apps.png). Los datos son de ejemplo. Si cambias
posiciones en esas clases Java, cámbialas también en sim.py.
"""
import json
import math
import os
import sys

import numpy as np

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sim import MC, NAVY, ROOT, Sim, lighten, rgb  # noqa: E402
from skin import accent, mix, darken as darken_t, deep as deep_t, WHITE  # noqa: E402

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
        ('armario', 'ARMARIO'), ('efectos', 'EFECTOS'), ('rango', 'MI RANGO'), ('web', 'WEB'), ('guia', 'GUÍA')]
ADMIN = [('apps', 'APPS'), ('tienda', 'TIENDA'), ('kits', 'KITS'), ('recompensas', 'RECOMPENSAS'), ('viajes', 'VIAJES'), ('gts', 'GTS'),
         ('comunidad', 'COMUNIDAD'), ('oficios', 'OFICIOS'), ('cazas', 'CAZAS'), ('admin', 'AJUSTES')]

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
    s.admin_hint()
    s.save(OUT + 'p_admin.png')
    NAMES.append('p_admin')
    s = Sim()
    s.status('PAD ADMIN', back=False, title_color=rgb(0xFFD36A), greens=False)
    s.home(ADMIN, hover='tienda')
    s.admin_hint('a_tienda', 'TIENDA')
    s.save(OUT + 'p_admin_tienda.png')

# --- Oficios -----------------------------------------------------------------------------------------------------------
page('p_oficios', 'OFICIOS', [], '', ['Trabajas de Minero · cobras 42 monedas en el próximo pago.'], [], app='oficios', hover=2, gallery=[
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
# SKINS (PadWardrobe.skinsView): el escaparate con tu skin en 3D (solo la skin) y tus skins, cada una en 3D
PLAYER = os.path.join(MC, 'entity/player')
STEVE, ALEX = (os.path.join(PLAYER, 'wide/steve.png'), False), (os.path.join(PLAYER, 'slim/alex.png'), True)
page('p_skins', 'ARMARIO', [('skins', 'SKINS'), ('cabeza', 'CABEZA'), ('pecho', 'PECHO'), ('piernas', 'PIERNAS'), ('pies', 'PIES'),
                            ('espalda', 'ESPALDA')], 'skins', [], [], app='armario', hover=2,
     hero=('player_head', 'Tu skin', 0x9E5AE0, ['Llevas la skin de tu cuenta de Minecraft.',
                                                'Pulsa una skin de abajo para verla o ponértela.'], -1, '2/16', None, None,
           {'model': STEVE}),
     cards=[('player_head', 'De siempre', 0x9E5AE0, 'PUESTA', True, 1, {'tone': 2, 'model': STEVE}),
              ('player_head', 'Alex', 0x9E5AE0, 'VER', False, 1, {'tone': 1, 'model': ALEX}),
              ('player_head', 'Zuri', 0x9E5AE0, 'VER', False, 1, {'tone': 1, 'model': (os.path.join(PLAYER, 'wide/zuri.png'), False)})],
     footer=[('AÑADIR SKIN', G)])
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
     ['Cada día se reclama el siguiente de la racha. Pulsa un día.'], [
         ('', 'El calendario', T, [], -1, '', None, None),
         ('clock_00', 'Calendario', T, ['Sale en Recompensas: cada día se reclama el siguiente.'], -1, '', ('SÍ', G), None),
         ('compass_00', 'Reiniciar si se salta un día', T, ['Vuelve al día 1: la racha se pierde.'], -1, '', ('SÍ', G), None),
     ], cards=[
         ('bread', 'Día 1', 0xF6B628, '¤25', False, 8), ('torch', 'Día 2', 0xF6B628, '¤25', False, 16),
         ('iron_ingot', 'Día 3', 0xF6B628, '¤50', False, 4), ('cooked_beef', 'Día 4', 0xF6B628, '¤50', False, 12),
         ('golden_carrot', 'Día 5', 0xF6B628, '¤75', False, 8), ('emerald', 'Día 6', 0xF6B628, '+3 VERDES', False),
         ('diamond', 'Día 7', 0xF6B628, '¤150', False),
     ], footer=[('AÑADIR DÍA', G), ('QUITAR EL ÚLTIMO', R)], app='a_recompensas')
# 1.3.67: el kit en UNA pantalla (PadAdminKits.sheet): se crea con tu inventario y todo se cambia ahí mismo
page('a_kit', 'KITS', [], '', [], [
    ('chest', '6 objetos', T, ['Pulsa uno arriba para cambiarlo o quitarlo.'], -1, '', ('MI INVENTARIO', GO), ('ELEGIR', G)),
    ('gold_nugget', 'Monedas', T, ['Al monedero; pulsa la fila para escribirlas.'], -1, '+¤250', ('-', B), ('+', B)),
    ('emerald', 'Monedas verdes', T, ['Del Gachapón; pulsa la fila para escribirlas.'], -1, 'Ninguna', ('-', B), ('+', B)),
    ('clock_00', 'Cada cuánto', T, ['Pulsa la fila para escribir otras horas.'], -1, 'Cada día', ('«', B), ('»', B)),
    ('golden_helmet', 'Quién puede', T, ['Un rango y los de encima.'], -1, 'Todos los jugadores', ('«', B), ('»', B)),
    ('gold_nugget', 'Precio', T, ['Al reclamarlo; pulsa la fila para escribirlo.'], -1, 'Gratis', ('-', B), ('+', B)),
    ('name_tag', 'Nombre', T, [], -1, 'Kit inicial', ('CAMBIAR', B), None),
], compact=True, hero=('iron_sword', 'Kit inicial', 0xF6B628, ['Cada día · 6 objetos · +250 monedas · Todos los jugadores · gratis'], -1, 'BORRADOR',
         ('PUBLICAR', G), None),
     cells=[(n, '', T, False, c) for n, c in (('iron_sword', 1), ('iron_pickaxe', 1), ('bread', 16), ('torch', 32), ('oak_log', 64),
                                             ('leather_chestplate', 1))],
     footer=[('ATRÁS', B), ('PROBAR', GO), ('COPIAR', GR), ('BORRAR', R)], app='a_kits')
# 1.3.67: Efectos › COMBATE (PadCombat.view): las estelas de arma, del cliente, con la vista previa
def combate_page(name, st=None, t=250, hero_line='Clásico · Automático · brillo', rows_from=0):
    st = st or {}
    rows = [
        ('', 'ESTILO', T, [], -1, '', None, None),
        ('nether_star', 'Estilo', T, ['Una cinta de luz suave.'], -1, hero_line.split(' · ')[0], ('«', B), ('»', B)),
        ('amethyst_shard', 'Color', T, ['El de tu arma (su material y encantamientos).'], -1, 'Automático', ('«', B), ('»', B)),
        ('glowstone_dust', 'Mezcla', T, ['Luz que suma: brilla, sobre todo de noche.'], -1, 'Brillo', ('«', B), ('»', B)),
        ('clock_00', 'Duración', T, ['Cuánto tarda en apagarse.'], -1, '0,30 s', ('-', B), ('+', B)),
        ('iron_sword', 'Ancho', T, ['Qué parte de la hoja cubre, desde la punta.'], -1, '60 %', ('-', B), ('+', B)),
        ('glass', 'Opacidad', T, [], -1, '85 %', ('-', B), ('+', B)),
        ('feather', 'Cola', T, ['Cómo se apaga el final.'], -1, 'La del estilo', ('«', B), ('»', B)),
        ('tinted_glass', 'Lo menos que se apaga', T, ['0 %: la cola se va del todo.'], -1, 'Nada', ('-', B), ('+', B)),
        ('end_rod', 'Filo brillante', T, ['Una línea de luz por el borde.'], -1, 'No', ('-', B), ('+', B)),
        ('', 'CUÁNDO', T, [], -1, '', None, None),
        ('diamond_sword', 'Al golpear', T, [], -1, '', ('SÍ', G), None),
        ('target_side', 'Solo ataques', T, ['No al picar bloques ni al usar cosas.'], -1, '', ('SÍ', G), None),
        ('redstone', 'Según la carga', T, ['Un golpe flojo deja una estela floja.'], -1, '', ('SÍ', G), None),
        ('chest', 'Al sacar el arma', T, ['Al cambiar de casilla a un arma.'], -1, '', ('SÍ', G), None),
        ('player_head', 'Otros jugadores y mobs', T, [], -1, '', ('SÍ', G), None),
        ('spyglass', 'Hasta qué distancia', T, ['Las de los demás, desde tan lejos.'], -1, '32 bloques', ('-', B), ('+', B)),
        ('', 'CON QUÉ', T, [], -1, '', None, None),
        ('golden_sword', 'Espadas', T, [], -1, '', ('SÍ', G), None),
        ('iron_axe', 'Hachas', T, [], -1, '', ('SÍ', G), None),
        ('iron_pickaxe', 'Picos', M, [], -1, '', ('NO', R), None),
    ][rows_from:]
    page(name, 'EFECTOS', [('', 'AL MATAR'), ('skills', 'SKILLS'), ('combate', 'COMBATE')], 'combate', [], rows, compact=True,
         hero=('diamond_sword', 'Estelas de arma', T, [hero_line, 'Cámbialo abajo: se guarda solo.'],
               -1, '', ('ACTIVADAS', G), None, {'trail': {'st': st, 't': t, 'color': 0x58F4E6}}),
         footer=[('RESTABLECER', GR)], app='efectos')


combate_page('p_combate')
combate_page('p_combate_neon', {'profile': 2}, 240, 'Neón · Automático · brillo')
combate_page('p_combate_brasas', {'profile': 3}, 300, 'Brasas · Automático · brillo')
combate_page('p_combate_humo', {'profile': 5, 'blend': 1}, 260, 'Humo · Automático · translúcida', rows_from=10)
page('a_selector', 'KITS', [('i', 'TU INVENTARIO'), ('t', 'TODOS LOS OBJETOS')], 't',
     ['Añadir a Kit inicial (de 16 en 16). 1.312 objetos · página 1 de 14.'], [], app='a_kits', hover=5, cells=[
         (n, '', T, False) for n in ('stone', 'granite', 'diorite', 'andesite', 'grass_block', 'dirt', 'cobblestone', 'oak_planks',
                                     'spruce_planks', 'birch_planks', 'oak_sapling', 'sand', 'gravel', 'gold_ore', 'iron_ore',
                                     'coal_ore', 'oak_log', 'spruce_log', 'birch_log', 'glass', 'lapis_ore', 'sandstone',
                                     'white_wool', 'orange_wool', 'gold_block', 'iron_block', 'bricks', 'tnt', 'bookshelf',
                                     'obsidian', 'torch', 'chest', 'diamond_ore', 'diamond_block', 'crafting_table', 'furnace')
     ], input_=('BUSCAR (NOMBRE O ID)', 'BUSCAR', ''), footer=[('»', B), ('×16', GO), ('ATRÁS', B)])
# 1.3.66: el pad de administrador con las piezas de Kits (PadAdminUi): interruptores que dicen qué pasa, valores con su
# unidad y CAMBIAR (que abre el campo de abajo), fichas grandes y el selector de objetos
PADT = os.path.join(ROOT, 'src/main/resources/assets/tfclient/textures/gui/pad')


def app_card(icon, name, color, on):
    return (icon, name, color if on else 0x8C96AA, 'VISIBLE' if on else 'OCULTA', False,
            {'img': os.path.join(PADT, f'icon_{icon}.png'), 'smooth': False, 'tone': 2 if on else 3})


def toggle(icon, title, help_, on, on_t, off_t):
    lines = ([help_] if help_ else []) + [on_t if on else off_t]
    return (icon, title, T if on else M, lines, -1, '', ('SÍ', G) if on else ('NO', R), None)


def value(icon, title, help_, val, editing=False, second=None):
    return (icon, title, T, [help_] if help_ else [], -1, val, ('ESCRIBE ABAJO', GO) if editing else ('CAMBIAR', B), second, editing)


def section(title):
    return ('', title, T, [], -1, '', None, None)


page('a_apps', 'APPS', [], '', ['22 de 24 visibles. Pulsa una app para mostrarla u ocultarla en el pad de todos los jugadores (al momento).'], [],
     cards=[app_card('musica', 'Música', 0x3496FA, True), app_card('oficios', 'Oficios', 0xE0892E, True),
            app_card('misiones', 'Misiones', 0x22A884, True), app_card('pase', 'TF Pass', 0x2EAF5C, True),
            app_card('gachapon', 'Gachapón', 0xE0489E, False), app_card('cazas', 'Cazas', 0xC8553D, True),
            app_card('recompensas', 'Recompensas', 0xE2466A, True), app_card('tienda', 'Tienda', 0xE6A019, True),
            app_card('gts', 'GTS', 0x8B5CF0, False), app_card('monedero', 'Monedero', 0xD49A12, True),
            app_card('viajes', 'Viajes', 0x1AA3C8, True), app_card('hogares', 'Hogares', 0xDB6440, True)],
     footer=[('MOSTRAR TODAS', G)], app='a_apps', hover=2)
AJ_TABS = [('', 'VIAJES'), ('explorar', 'EXPLORAR'), ('jugadores', 'JUGADORES'), ('economia', 'ECONOMÍA'), ('sistema', 'SISTEMA')]
page('a_ajustes', 'AJUSTES', AJ_TABS, '', ['Las esperas y los warps de la app Viajes.'], [
    value('clock_00', 'Espera entre viajes', 'Para volver a viajar (al spawn, a un warp o a un punto).', '30 s', True),
    value('repeater', 'Cuenta atrás antes de viajar', 'Quieto y sin recibir daño. 0 = al momento.', '3 s'),
    toggle('ender_pearl', 'Warps de EssentialsX en Viajes', 'Los /warp del servidor salen en la app Viajes.', True,
           'Salen en Viajes (menos los que ocultes en VIAJES › WARPS).', ''),
], input_=('SEGUNDOS (0 A 3.600)', 'GUARDAR', ''), footer=[('CANCELAR', GR)], app='a_ajustes')
page('a_ajustes_economia', 'AJUSTES', AJ_TABS, 'economia', ['Envíos del Monedero y el GTS (el mercado).'], [
    toggle('gold_nugget', 'Mandar monedas a otros', 'Desde la app Monedero, a otro jugador.', True, 'Se pueden mandar monedas desde el Monedero.', ''),
    value('gold_ingot', 'Máximo por envío', 'Lo más que se puede mandar de una vez.', '¤1.000.000'),
    value('emerald', 'GTS: cosas a la venta a la vez', 'Por jugador.', '10 a la vez'),
    value('clock_00', 'GTS: días a la venta', 'Luego vuelve a su dueño (a GTS › Recoger).', '7 días'),
], app='a_ajustes')
page('a_tienda', 'TIENDA', [], '', ['Pulsa una categoría para ver sus objetos y precios. Para una nueva, escribe su nombre abajo y pulsa CREAR.'], [
    section('Categorías (3)'),
    ('diamond', 'Minerales', T, ['6 objetos · la tienda compra 4'], -1, '', ('EDITAR', B), None),
    ('bread', 'Comida', T, ['9 objetos · la tienda compra 2'], -1, '', ('EDITAR', B), None),
    ('oak_sapling', 'Granjas', T, ['Vacía: entra y pulsa AÑADIR OBJETO.'], -1, '', ('EDITAR', B), None),
    section('La tienda'),
    toggle('chest', 'Tienda abierta', '', True, 'Todos pueden comprar y vender.', ''),
], input_=('NOMBRE DE UNA CATEGORÍA NUEVA', 'CREAR', ''), app='a_tienda')
page('a_tienda_cat', 'TIENDA', [], '', [], [
    section('La categoría'),
    value('name_tag', 'Nombre', 'El que ven los jugadores en la tienda.', 'Minerales'),
], hero=('diamond', 'Minerales', 0xE6A019, ['6 objetos · se compran 5 · la tienda compra 4',
                                            'Oro: lo que paga el jugador. Verde: lo que le paga la tienda.'],
         -1, '', None, None),
     cards=[('diamond', 'Diamante', 0x40C850, '¤950', False, {'badge': '+¤400'}),
            ('iron_ingot', 'Lingote de hierro', 0x40C850, '¤60', False, 16, {'badge': '+¤25'}),
            ('gold_ingot', 'Lingote de oro', 0x40C850, '¤120', False, 16, {'badge': '+¤50'}),
            ('emerald', 'Esmeralda', 0xF6B628, '¤300', False),
            ('lapis_lazuli', 'Lapislázuli', 0x40C850, 'SOLO VENTA', False, 32, {'badge': '+¤8', 'tone': 4}),
            ('coal', 'Carbón', 0x8C96AA, 'SIN PRECIO', False, 64, {'tone': 3})],
     footer=[('ATRÁS', B), ('AÑADIR OBJETO', G)], app='a_tienda')
page('a_tienda_item', 'TIENDA', [], '', [], [
    value('gold_nugget', 'Precio de compra', 'Lo que paga el jugador. 0 = no se puede comprar.', '¤950', True, ('QUITAR', GR)),
    value('hopper', 'Precio de venta', 'Lo que le paga la tienda. 0 = no lo compra.', '¤400', False, ('QUITAR', GR)),
    value('diamond', 'Unidades por compra', 'Se compra y se vende de tantas en tantas.', '×1'),
], hero=('diamond', 'Diamante', 0xE6A019, ['El jugador lo compra por 950 monedas.', 'La tienda se lo compra por 400 monedas.'], -1, '', None, None),
     input_=('LO QUE PAGA EL JUGADOR (0 = NO SE COMPRA)', 'GUARDAR', '1200'), footer=[('ATRÁS', B), ('CANCELAR', GR)], app='a_tienda')
page('a_viajes', 'VIAJES', [('', 'PUNTOS'), ('warps', 'WARPS')], '', ['Pulsa uno para cambiarlo. Nuevo: ve al sitio y AÑADIR AQUÍ.'], [
    ('filled_map', 'Plaza mayor', T, ['Mundo · 120, 64, -340'], -1, '', ('EDITAR', B), ('IR', GR)),
    ('nether_star', 'Arena PvP', T, ['Mundo · -880, 70, 512'], -1, '', ('EDITAR', B), ('IR', GR)),
    ('diamond_pickaxe', 'Minas del norte', T, ['Mundo · 40, 12, -2210'], -1, '', ('EDITAR', B), ('IR', GR)),
], input_=('NOMBRE DEL PUNTO (SE PONE DONDE ESTÁS)', 'AÑADIR AQUÍ', ''), app='a_viajes')
page('a_viajes_punto', 'VIAJES', [], '', [], [
    value('name_tag', 'Nombre', 'El que ven los jugadores.', 'Plaza mayor'),
    ('filled_map', 'Icono', T, ['Mapa.'], -1, '', ('CAMBIAR', B), None),
    ('compass_00', 'Posición', T, ['Lo mueve a donde estás ahora, mirando hacia donde miras.'], -1, '', ('MOVER AQUÍ', GO), None),
    ('barrier', 'Borrar el punto', T, ['Deja de salir en Viajes.'], -1, '', ('BORRAR', R), None),
], hero=('filled_map', 'Plaza mayor', 0x1AA3C8, ['Mundo · 120, 64, -340', 'Sale en la app Viajes de todos los jugadores.'], -1, '',
         ('IR', B), None), footer=[('ATRÁS', B)], app='a_viajes')
page('a_oficios', 'OFICIOS', [], '', ['Ajustes de los oficios. Lo que paga cada uno, sus misiones y niveles: en config/tfclient/oficios.json (y RECARGAR).'], [
    section('General'),
    toggle('iron_pickaxe', 'Oficios', '', True, 'Encendidos: se gana dinero trabajando.', ''),
    value('book', 'Oficios a la vez', 'Cuántos puede tener cada jugador.', '2'),
    value('clock_00', 'Espera para cambiar', 'Tras unirse a uno, para poder dejarlo o cambiarlo.', '1 h'),
], footer=[('RECARGAR OFICIOS', GO)], app='a_oficios')

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
    ct = Y + s.TABS  # la tarjeta empieza a la altura de la ventana de la lista, con su pestaña encima
    c = max(32, min(round((leftW - 16 - s.bigu(16)) / 1.3), Y + H - (ct + 8) - below))
    cx, cy = X + 8, ct + 8
    tl = 'SONANDO' if playing else 'REPRODUCTOR'
    tw = s.tab_w(['REPRODUCTOR'])
    s.box(X + 4, Y, tw, s.TABS + 1, NAVY)
    grad(s, X + 5, Y + 1, tw - 2, s.TABS, rgb(0x323C80), rgb(0x262E66))
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
    top = Y + s.TABS
    bottom = Y + H - 19 - (24 if job else 0)
    s.panel(lx, top, lw, bottom - top)
    # pestañas (PadMusicPage.drawListTabs): todas del mismo ancho (PadSkin.tabWidth), TODAS, las que quepan, ‹ › y +
    INK = (24, 38, 92)
    gap, plus, arrow = s.TAB_SPACE, s.TAB_H + 1, s.TAB_H
    names = [s.pfit(n.upper(), 58) for n in lists]
    tab_w = min(s.tab_w(['TODAS'] + names) + 10, max(s.TAB_MIN, (lw - 8 - plus - gap) // 3))
    widths = [tab_w for _ in names]
    strip_x = lx + 4 + tab_w + gap
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
        if i + 1 == sel_list:
            chosen = (tx, tab_w, label, i >= 0)
        else:
            s.sk_tab(tx, Y, tab_w, s.pfit(label, tab_w - 8), 0, accent('musica'))
        tx = strip_x if i < 0 else tx + tab_w + gap
    if chosen:
        if not chosen[3]:
            s.sk_tab(chosen[0], Y, chosen[1], chosen[2], 2, accent('musica'))
        else:
            s.sk_tab(chosen[0], Y, chosen[1], '', 2, accent('musica'))
            lab = s.pfit(chosen[2], chosen[1] - 20)
            s.ptext_shadow(lab, chosen[0] + (chosen[1] - 10) // 2 + 1 - s.pwidth(lab) // 2, Y + 4, WHITE, darken_t(deep_t(accent('musica')), 0.42))
            glyph_at(s, CROSS, chosen[0] + chosen[1] - 13 + 2, Y + 4 + 2, rgb(0xFFD0D6))

    def arrow_btn(ax, ay, g, enabled):
        s.box(ax, ay, arrow, arrow, rgb(0x6A88B8) if enabled else rgb(0xB4C6DE))
        grad(s, ax + 1, ay + 1, arrow - 2, arrow - 2, rgb(0xE8F2FC), rgb(0xC8DCF0))
        glyph_at(s, g, ax + (arrow - len(g[0])) // 2, ay + (arrow - len(g)) // 2, rgb(0x34507E) if enabled else rgb(0xA4B6D0))
    if paged:
        ax = end - gap - 2 * arrow - gap
        arrow_btn(ax, Y, ARROW_LEFT, tab_first > 0)
        arrow_btn(ax + arrow + gap, Y, ARROW_RIGHT, last < len(names) - 1)
    s.sk_tab(end, Y, plus, '', 0, accent('musica'))
    glyph_at(s, PLUS8, end + (plus - 8) // 2, Y + (s.TAB_H - 8) // 2, rgb(0x34507E))
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
    tw, tx, th = s.tab_w([tab]), x + 4, s.TAB_H
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


RAR_P = {'comun': 62.0, 'raro': 26.0, 'epico': 9.0, 'legendario': 3.0}


def rank_of(r):
    """PadGachaPage.rank: 1 (la más común) a 4 (la más rara)."""
    return {'comun': 1, 'raro': 2, 'epico': 3, 'legendario': 4}[r]


def gacha_card2(s, p, cx, cy, sc, d, cw, ch, win=False, since=9.0):
    """PadGachaPage.drawCard (1.3.67): tarjeta a escala sc con su centro en (cx, cy); d: sombra de los lados."""
    col = rgb(RAR[p['r']][1])
    w, h = cw, ch
    with s.big_at(cx - w * sc / 2, cy - h * sc / 2, px=s.f * sc):
        x, y = 0, 0
        if win:
            pulse = 0.5 + 0.5 * math.sin(since * 7)
            s.box(x - 3, y - 3, w + 6, h + 6, col + (round(110 + 120 * pulse),))
            s.box(x - 2, y - 2, w + 4, h + 4, lighten(col, 70) + (200,))
        s.box(x, y, w, h, col)
        s.vgrad(x + 1, y + 1, w - 2, h - 2, tint(col, 0.5), tint(col, 0.88))
        s.fill(x + 2, y + 1, x + w - 2, y + 2, (255, 255, 255, 153))
        s.stretch('glow', x + 2, y + 3, w - 4, w - 6)
        big = 2 if h >= 50 else 1
        iy = y + 4 + (h - 14 - 16 * big) // 2
        s.item(item_of(p), x + w // 2 - 8 * big, iy, big)
        if p['tipo'] == 'objeto' and p['n'] > 1:
            s.count(p['n'], x + w // 2 - 8 * big, iy, big)
        s.fill(x + 1, y + h - 11, x + w - 1, y + h - 1, darken(RAR[p['r']][1], 0.5) + (221,))
        if p['tipo'] != 'objeto':
            q = f"{p['n']:,}".replace(',', '.') if p['n'] < 10000 else f"{p['n'] // 1000}K"
            s.ptext(q, x + w // 2 - s.pwidth(q) // 2, y + h - 11, (255, 255, 255))
        if d > 0.05:
            s.fill(x, y, x + w, y + h, (11, 8, 32, round(170 * d)))


def gacha_page(name, tab='ruleta', result=None, best=0, phase='idle', off=130.0, speed=0.0, kick=0.0, since=0.6, balance=7,
               fbw=1920, fbh=1080):
    """PadGachaPage (1.3.67): RULETA · PREMIOS · HISTORIAL. phase: idle (quieta), spin (girando a speed u/s, con la
    lengüeta doblada kick) o win (el premio salió hace since s)."""
    if ONLY and name not in ONLY:
        return
    import random
    s = Sim(fbw=fbw, fbh=fbh)
    s.T = 3.3
    s.status('GACHAPÓN')
    loc = Local(s)
    loc.__enter__()
    X, Y, W, H = s.X, s.Y, s.W, s.H
    acc = accent('gachapon')
    sel = {'ruleta': 0, 'premios': 1, 'historial': 2}[tab]
    s.sk_tabs(X + 4, Y, W - 8, ['RULETA', 'PREMIOS', 'HISTORIAL'], sel, acc)
    top, bottom = Y + s.TABS, Y + H
    if tab == 'premios':
        s.sk_window(X, top, W, bottom - top, acc)
        cols = max(1, (W - 16) // 84)
        cw = (W - 16) // cols
        s.scissor(X + 2, top + 3, W - 4, bottom - top - 6)
        y = top + 5
        cur, col = None, 0
        for p in PREMIOS:
            if p['r'] != cur:
                if cur is not None and col > 0:
                    y += 66
                cur, col = p['r'], 0
                c = rgb(RAR[p['r']][1])
                s.fill(X + 6, y + 4, X + 8, y + 12, c)
                t = RAR[p['r']][0].upper()
                s.ptext_shadow(t, X + 12, y + 3, lighten(c, 10), darken(RAR[p['r']][1], 0.4))
                pc = f"{RAR_P[p['r']]:.0f} %"
                s.mtext(pc, X + W - 12 - s.mwidth(pc), y + 4, rgb(0x4A6694))
                s.fill(X + 14 + s.pwidth(t) + 4, y + 8, X + W - 18 - s.mwidth(pc), y + 9, c + (90,))
                y += 16
            px = X + 8 + col * cw + (cw - 84 + 4) // 2
            c = rgb(RAR[p['r']][1])
            w0, h0 = 80, 62
            s.box(px, y, w0, h0, c)
            s.vgrad(px + 1, y + 1, w0 - 2, 37, tint(c, 0.55), tint(c, 0.85))
            s.fill(px + 1, y + 38, px + w0 - 1, y + h0 - 1, rgb(0xF4F7FC))
            s.fill(px + 2, y + 1, px + w0 - 2, y + 2, (255, 255, 255, 153))
            s.stretch('glow', px + w0 // 2 - 17, y + 2, 34, 34)
            s.item(item_of(p), px + w0 // 2 - 16, y + 3, 2)
            if p['tipo'] == 'objeto' and p['n'] > 1:
                s.count(p['n'], px + w0 // 2 - 16, y + 3, 2)
            nm = s.fitend(prize_name(p), w0 - 6)
            s.mtext(nm, px + (w0 - s.mwidth(nm)) // 2, y + 41, NAVY)
            pc = (f"{p['p']:.2f}".rstrip('0').rstrip('.').replace('.', ',') + ' %')
            s.mtext(pc, px + (w0 - s.mwidth(pc)) // 2, y + 51, darken(RAR[p['r']][1], 0.7))
            col += 1
            if col >= cols:
                col = 0
                y += 66
        s.no_scissor()
        loc.__exit__(None, None, None)
        s.save(OUT + name + '.png')
        NAMES.append(name)
        return
    # ---- la ruleta
    x, w, h = X, W, bottom - top
    win = phase == 'win' and result
    bestp = result[best] if win else None
    bcol = rgb(RAR[bestp['r']][1]) if win else None
    s.box(x, top, w, h, rgb(0x0A0620))
    s.vgrad(x + 1, top + 1, w - 2, h - 2, rgb(0x2B1747), rgb(0x100A26))
    s.scissor(x + 1, top + 1, w - 2, h - 2)
    cx = x + w // 2
    s.rays(cx, top + h * 0.42, w * 0.75, 0.22 if win else 0.10, s.T * 6)
    s.glow(cx, top + h * 0.42, h * 0.85, bcol if win else acc, 120 if win else 70)
    s.tiled('stripes', x + 1, top + 1, w - 2, h - 2, 0.04, off=s.T * 4)
    s.no_scissor()
    s.fill(x + 2, top + 1, x + w - 2, top + 2, (255, 255, 255, 85))
    hy = top + 5
    s.blit('coin_green', x + 7, hy - 1)
    bal = f'{balance}'
    s.ptext_shadow(bal, x + 21, hy, rgb(0x8CF0B4), rgb(0x0A3A20))
    s.mtext('monedas verdes' if balance != 1 else 'moneda verde', x + 25 + s.pwidth(bal), hy + 1, rgb(0x8C96C8))
    # garantía: barra de pasos
    every, left = 10, 3
    done = every - left
    pc_ = rgb(0xA855F7)
    seg, gap = 5, 1
    bw = every * (seg + gap) - gap
    bx = x + w - 7 - bw
    for i in range(every):
        sx = bx + i * (seg + gap)
        s.fill(sx, hy + 1, sx + seg, hy + 8, pc_ if i < done else rgb(0x2A2350))
        if i < done:
            s.fill(sx, hy + 1, sx + seg, hy + 2, lighten(pc_, 60))
    label = f'ÉPICO EN {left}'
    s.ptext_shadow(label, bx - 5 - s.pwidth(label), hy, lighten(pc_, 50), rgb(0x0A0620))
    # el carrete
    rh = max(44, min(70, h - 22 - 28 - 40))
    ch = rh - 10
    cw = round(ch * 0.8)
    pitch = cw + 6
    rx, rw, ry = x + 6, w - 12, top + 20
    s.box(rx - 1, ry - 1, rw + 2, rh + 2, rgb(0x05030F))
    s.vgrad(rx, ry, rw, rh, rgb(0x0B0820), rgb(0x1A1238))
    center, cy = rx + rw // 2, ry + rh // 2
    s.scissor(rx, ry, rw, rh)
    s.glow(center, cy, rh * 0.9, bcol if win else rgb(0xFFE680), 150 if win else 60)
    strip = [PREMIOS[(i * 7) % len(PREMIOS)] for i in range(60)]
    win_i = 34
    if win:
        strip[win_i] = bestp
        off = win_i * pitch
    half = rw / 2
    first = int((off - half) // pitch) - 1
    idx = list(range(first, first + rw // pitch + 4))
    idx.sort(key=lambda i: -abs(i * pitch - off))
    for i in idx:
        dx = i * pitch - off
        d = min(1.0, abs(dx) / half)
        sc = 1 - 0.24 * d
        is_win = bool(win) and i == win_i
        if is_win:
            sc *= 1 + 0.16 * (0.75 + 0.25 * math.cos((since - 0.3) * 5) if since >= 0.3 else 1.0)
        gacha_card2(s, strip[i % len(strip)], center + dx, cy, sc, d, cw, ch, is_win, since)
    if speed > 300:
        k = min(1.0, (speed - 300) / 1800)
        for i in range(9):
            ly = ry + 4 + (i * 37 % (rh - 8))
            ln = 20 + (i * 53 % 50) * k
            lx = rx + ((i * 97 + 3300 * (0.6 + i * 0.07)) % (rw + ln)) - ln
            s.hgrad(lx, ly, ln, 1, (255, 255, 255, 0), (255, 255, 255, round(110 * k)))
    if win and since < 0.35:
        s.fill(rx, ry, rx + rw, ry + rh, lighten(bcol, 80) + (round(170 * (1 - since / 0.35)),))
    fade = min(40, rw // 6)
    s.hgrad(rx, ry, fade, rh, (11, 8, 32, 240), (11, 8, 32, 0))
    s.hgrad(rx + rw - fade, ry, fade, rh, (11, 8, 32, 0), (11, 8, 32, 240))
    if win:
        for i, (dx, dy) in enumerate(((-34, 6), (30, 9), (-26, rh - 9), (36, rh - 13), (0, 3), (-48, rh // 2), (50, rh // 2 - 6))):
            s.sparkle(center + dx, ry + dy, 2, s.twinkle(i * 1.3 + 0.2))
    s.no_scissor()
    fw, fh = cw + 8, ch + 6
    fx, fy = center - fw // 2, cy - fh // 2
    frame = lighten(bcol, 40) if win else rgb(0xF6B628)

    def outline(x0, y0, w0, h0, c):
        s.fill(x0 + 1, y0, x0 + w0 - 1, y0 + 1, c)
        s.fill(x0 + 1, y0 + h0 - 1, x0 + w0 - 1, y0 + h0, c)
        s.fill(x0, y0 + 1, x0 + 1, y0 + h0 - 1, c)
        s.fill(x0 + w0 - 1, y0 + 1, x0 + w0, y0 + h0 - 1, c)
    outline(fx, fy, fw, fh, frame + (220,))
    outline(fx - 1, fy - 1, fw + 2, fh + 2, frame + (80,))
    # la lengüeta (doblada kick) y la flecha de abajo
    tipx, tipy = center - kick * 3, ry + 7 - kick * 1.5
    from PIL import ImageDraw as _ID  # noqa: F401
    for (a_, b_, c_, col) in ((center - 5, center + 6, tipy, rgb(0xC07818)), (center - 4, center + 5, tipy - 1.5, rgb(0xF6B628))):
        for yy in range(int(ry - 3), int(c_) + 1):
            t_ = (yy - (ry - 3)) / max(1, c_ - (ry - 3))
            l_ = a_ + (tipx + 0.5 - a_) * t_
            r_ = b_ + (tipx + 0.5 - b_) * t_
            s.fill(round(l_), yy, round(r_), yy + 1, col)
    s.fill(center - 5, ry - 5, center + 6, ry - 2, rgb(0xFFE58A))
    for i in range(4):
        s.fill(center - 4 + i, ry + rh + 3 - i, center + 5 - i, ry + rh + 4 - i, rgb(0xF6B628))
    by = bottom - 21
    zt, zb = ry + rh + 5, by - 4
    if win:
        rn = RAR[bestp['r']][0].upper()
        title = ('MEJOR: ' + rn) if len(result) > 1 else '¡' + rn + '!'
        tw = s.pwidth(title)
        ty = zt + 2
        if zb - zt >= 32:
            with s.big_at(cx - tw, ty + 9 - 10, px=s.f * 2):
                s.ptext_shadow(title, 0, 0, lighten(bcol, 40), darken(RAR[bestp['r']][1], 0.35))
            ty += 22
        else:
            s.ptext_shadow(title, cx - tw // 2, ty, lighten(bcol, 40), darken(RAR[bestp['r']][1], 0.35))
            ty += 12
        nm = s.fitend(prize_name(bestp), w - 20)
        if ty + 9 <= zb:
            s.mtext(nm, cx - s.mwidth(nm) // 2, ty, (255, 255, 255))
        ty += 12
        if len(result) > 1:
            sz, gp, n = 22, 5, len(result)
            sx = cx - (n * sz + (n - 1) * gp) // 2
            if ty + sz <= zb:
                for i, p in enumerate(result):
                    k = min(1.0, max(0.0, (since - 0.15 - i * 0.16) / 0.14))
                    bx0 = sx + i * (sz + gp)
                    if k >= 0.5:
                        s.slot(bx0, ty, sz, sz, rgb(RAR[p['r']][1]))
                        s.item(item_of(p), bx0 + 3, ty + 3)
                        if p['tipo'] == 'objeto' and p['n'] > 1:
                            s.count(p['n'], bx0 + 3, ty + 3)
                    else:
                        s.box(bx0, ty, sz, sz, rgb(0x4A3A8A))
                        s.vgrad(bx0 + 1, ty + 1, sz - 2, sz - 2, rgb(0x3A2A70), rgb(0x1E1440))
                        s.ptext('?', bx0 + sz // 2 - 2, ty + 6, rgb(0xB8A8F0))
                    if k >= 1 and i == best:
                        outline(bx0 - 2, ty - 2, sz + 4, sz + 4, rgb(0xFFE58A) + (230,))
                        outline(bx0 - 1, ty - 1, sz + 2, sz + 2, rgb(0xF6B628) + (200,))
    else:
        how = '1 tirada: 1  ·  5 tiradas: 5  ·  ESPACIO para girar'
        s.mtext(how, cx - s.mwidth(how) // 2, zt + 2, rgb(0xB8C2F0))
        if zb - zt >= 30:
            bw2 = min(w - 40, 300)
            bx2, by2 = cx - bw2 // 2, zt + 16
            s.box(bx2 - 1, by2 - 1, bw2 + 2, 8, rgb(0x05030F))
            at = bx2
            for r_, pct_ in RAR_P.items():
                sw = bw2 * pct_ / 100
                c = rgb(RAR[r_][1])
                s.fill(round(at), by2, round(at + sw), by2 + 6, c)
                s.fill(round(at), by2, round(at + sw), by2 + 1, lighten(c, 60))
                at += sw
            ly = by2 + 10
            labels = [(RAR[r_][0] + f' {pct_:.0f} %', rgb(RAR[r_][1])) for r_, pct_ in RAR_P.items()]
            lw0 = sum(8 + s.mwidth(t) + 10 for t, _ in labels)
            lx = cx - lw0 // 2
            for t, c in labels:
                s.fill(lx, ly + 1, lx + 5, ly + 6, c)
                s.mtext(t, lx + 8, ly, lighten(c, 70))
                lx += 8 + s.mwidth(t) + 10
    # chispas
    if win and since < 1.4:
        rnd = random.Random(7)
        n = 10 + rank_of(bestp['r']) * 10
        for _ in range(n):
            a_ = rnd.random() * math.pi * 2
            v = 50 + rnd.random() * (60 + rank_of(bestp['r']) * 25)
            life = 0.7 + rnd.random() * 0.7
            if since > life:
                continue
            tt = since
            px_ = math.cos(a_) * v * tt
            py_ = (math.sin(a_) * v - 40) * tt + 80 * tt * tt
            colr = (255, 255, 255) if rnd.random() < 0.33 else rgb(0xFFE680) if rnd.random() < 0.33 else bcol
            sz = 2 if rnd.random() < 0.25 else 1
            al = round(255 * min(1.0, (life - since) / life * 1.6))
            s.fill(round(cx + px_), round(cy + py_), round(cx + px_) + sz, round(cy + py_) + sz, colr + (al,))
    w1 = s.pwidth('GIRAR') + 6 + 10 + s.pwidth('1') + 14
    w5 = s.pwidth('GIRAR X5') + 6 + 10 + s.pwidth('5') + 14
    bx3 = x + (w - w1 - w5 - 10) // 2
    busy = phase == 'spin'
    price_button(s, bx3, by, 'GIRAR', 1, 3, balance >= 1 and not busy)
    price_button(s, bx3 + w1 + 10, by, 'GIRAR X5', 5, 1, balance >= 5 and not busy)
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
gacha_page('p_gachapon')
gacha_page('p_gachapon_giro', phase='spin', off=812.0, speed=2100.0, kick=0.8)
gacha_page('p_gachapon_epico', result=[PREMIOS[4]], phase='win', since=0.45)
gacha_page('p_gachapon_x5', result=R5, best=0, phase='win', since=1.4, balance=2)
gacha_page('p_gachapon_premios', tab='premios')
gacha_page('p_gachapon_1366', result=[PREMIOS[1]], phase='win', since=0.9, balance=0, fbw=1366, fbh=768)


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


def sim_poly(s, pts, color):
    """Un polígono en unidades (PadSkin.line / tri): en píxeles reales, respetando el recorte."""
    from PIL import ImageDraw
    ox, oy = s.o
    rp = [(ox + x * s.f, oy + y * s.f) for x, y in pts]
    x0, y0 = int(min(p[0] for p in rp)) - 1, int(min(p[1] for p in rp)) - 1
    x1, y1 = int(max(p[0] for p in rp)) + 2, int(max(p[1] for p in rp)) + 2
    if x1 <= x0 or y1 <= y0:
        return
    im = Image.new('RGBA', (x1 - x0, y1 - y0))
    ImageDraw.Draw(im).polygon([(x - x0, y - y0) for x, y in rp], fill=tuple(color[:3]) + ((color[3],) if len(color) > 3 else (255,)))
    s._paste_px(im, x0, y0)


def sim_line(s, x0, y0, x1, y1, w, color):
    dx, dy = x1 - x0, y1 - y0
    ln = math.hypot(dx, dy)
    if ln < 0.01:
        return
    nx, ny = -dy / ln * w / 2, dx / ln * w / 2
    sim_poly(s, [(x0 + nx, y0 + ny), (x1 + nx, y1 + ny), (x1 - nx, y1 - ny), (x0 - nx, y0 - ny)], color)


def _hash(v):
    v &= 0xFFFFFFFF
    v ^= v >> 16
    v = (v * 0x7FEB352D) & 0xFFFFFFFF
    v ^= v >> 15
    v = (v * 0x846CA68B) & 0xFFFFFFFF
    v ^= v >> 16
    return v


def _vgrad_a(s, x0, y0, x1, y1, ct, cb):
    """Degradado vertical con alfa (PadSkin.vgrad)."""
    r = s._rect(x0, y0, x1, y1)
    W_, H_ = r[2] - r[0], r[3] - r[1]
    if W_ <= 0 or H_ <= 0:
        return
    ct, cb = np.array(ct, float), np.array(cb, float)
    t = (np.arange(H_) + 0.5) / H_
    col = np.outer(1 - t, ct) + np.outer(t, cb)
    s._arr_paste(np.repeat(col[:, None], W_, axis=1), r[0], r[1])


def pass_night(s, x, top, w, mh, lv, center, scroll):
    """PadPassPage.drawNight (1.3.74): noche esmeralda, aurora, estrellas, luna, la luz de tu nivel, colinas en silueta
    con borde de luna, luciérnagas, rayas y los lados en sombra."""
    t = s.T
    bot, x0, x1 = top + mh, x + 1, x + w - 1
    s.vgrad(x0, top + 1, w - 2, mh - 2, rgb(0x0F3A44), rgb(0x0A1430))
    drift = scroll * 0.05
    for band in range(2):
        col, a = ((0x5C, 0xE4, 0x8A), 120) if band == 0 else ((0x3F, 0xC8, 0xE0), 88)
        base, thick = top + mh * (0.17 if band == 0 else 0.27), (13 if band == 0 else 9)
        cx = x0
        while cx < x1:
            u = cx + drift
            cy = base + math.sin(u * 0.019 + t * 0.55 + band * 2.1) * 6 + math.sin(u * 0.047 - t * 0.8) * 2.5
            k = 0.5 + 0.5 * math.sin(u * 0.011 + t * 0.35 + band * 1.3)
            c = col + (round(a * (0.35 + 0.65 * k)),)
            cx1 = min(cx + 3, x1)
            _vgrad_a(s, cx, cy - thick, cx1, cy, col + (0,), c)
            _vgrad_a(s, cx, cy, cx1, cy + thick * 1.8, c, col + (0,))
            cx += 3
    for i in range(34):
        h = _hash(i * 7919 + 13)
        span = w - 6
        sx = x + 3 + ((h & 0xFFFF) / 65535 * span - scroll * 0.03) % span
        sy = top + 3 + ((h >> 16) & 0xFF) / 255 * mh * 0.5
        b = 0.3 + 0.7 * s.twinkle(i * 1.7 + 0.4)
        if i % 9 == 0:
            s.sparkle(round(sx), round(sy), 2, b, rgb(0xCFE8FF))
        else:
            c = rgb(0xFFF0C0) if i % 4 == 0 else rgb(0xDDEEFF)
            s.fill(sx, sy, sx + 1, sy + 1, c + (round(70 + 150 * b),))
    mx_, my_ = x + w * 0.88 - scroll * 0.01, top + 13
    s.glow(mx_, my_, 34, rgb(0xFFF4C8), 80)
    s.tex_alpha('moon', mx_ - 11, my_ - 11, 22, 22, 1.0, smooth=False)
    if lv > 0:
        cx, cy = center(lv)
        s.glow(cx, cy, mh * 0.95, rgb(0xFFD650), 64)
        s.rays(cx, cy, w * 0.62, 0.10, t * 6)
    lh = min(56, mh * 0.55)
    lw = 480 * lh / 56
    _vgrad_a(s, x0, bot - lh - 16, x1, bot - lh * 0.4, (0x3F, 0xC8, 0xE0, 0), (0x3F, 0xC8, 0xE0, 85))
    for i, (nm, tint_c, rim, sp) in enumerate((('land_far', 0x245A68, 0x9FE8D8, 0.15), ('land_mid', 0x16404E, 0x86DCC0, 0.35),
                                               ('land_near', 0x0A1F2B, 0x6CC8A0, 0.6))):
        off = (scroll * sp) % lw
        ly = bot - lh - (2 - i) * 4
        lx = x0 - off
        while lx < x + w:
            s.tex_alpha(nm, lx, ly - 1, lw, lh, 0.85, rgb(rim), smooth=False)
            s.tex_alpha(nm, lx, ly, lw, lh, 1.0, rgb(tint_c), smooth=False)
            lx += lw
    s.fill(x0, bot - 6, x1, bot - 1, rgb(0x081C26))
    for i in range(12):
        h = _hash(i * 104729 + 7)
        rise = mh * 0.75
        life = (t * (6 + (h & 7)) + ((h >> 8) & 0xFF)) % rise
        span = w - 10
        fx = x + 5 + (((h >> 16) & 0xFFFF) / 65535 * span - scroll * 0.5 + math.sin(t * 0.9 + i) * 5) % span
        fy = bot - 8 - life
        fade = min(1, life / 12) * min(1, (rise - life) / 18)
        b = (0.45 + 0.55 * s.twinkle(i * 2.3)) * fade
        col = rgb(0x8CF0B4) if i % 3 == 0 else rgb(0xFFE680)
        s.glow(fx, fy, 4, col, round(110 * b))
        s.fill(fx - 0.5, fy - 0.5, fx + 0.5, fy + 0.5, (255, 255, 255, round(230 * b)))
    s.tiled('stripes', x0, top + 1, w - 2, mh - 2, 0.04, off=t * 4)
    side = min(36, w // 8)
    s.hgrad(x0, top + 1, side, mh - 2, (6, 14, 28, 160), (6, 14, 28, 0))
    s.hgrad(x1 - side, top + 1, side, mh - 2, (6, 14, 28, 0), (6, 14, 28, 160))
    s.fill(x + 2, top + 1, x + w - 2, top + 2, (255, 255, 255, 85))


def pass_page(name, lv=12, claimed=10, scroll_to=None, hover=None, fbw=1920, fbh=1080):
    """PadPassPage (1.3.67): el MAPA DE LA TEMPORADA. HUD arriba, el mapa (cielo, colinas en paralaje y el camino que
    ondula de medallón en medallón) y la tira de cómo se gana XP."""
    if ONLY and name not in ONLY:
        return
    s = Sim(fbw=fbw, fbh=fbh)
    s.T = 2.0
    s.status('TF PASS')
    loc = Local(s)
    loc.__enter__()
    X, Y, W, H = s.X, s.Y, s.W, s.H
    PITCH, R, RB, HUD_H, FOOT_H = 60, 18, 22, 40, 15
    levels = pass_levels()
    mx_ = len(levels)
    top, bot = Y + HUD_H + 3, Y + H - FOOT_H - 3
    mh = bot - top
    at = scroll_to or (claimed + 1 if claimed < lv else lv)
    scroll = max(0, min((mx_ - 1) * PITCH + 68 - W, (at - 1) * PITCH + 34 - W / 2))

    def center(n):
        mid = top + mh * 0.42
        amp = max(0, min(14, (mh - 2 * RB - 34) / 2))
        return X + 34 + (n - 1) * PITCH - scroll, mid + math.sin(n * 0.95) * amp

    def state(n):
        return 2 if n <= claimed else 1 if n <= lv else 0

    # ---- el mapa: el escenario de noche (PadPassPage.drawNight, 1.3.74)
    s.box(X, top, W, mh, rgb(0x06101C))
    s.scissor(X + 1, top + 1, W - 2, mh - 2)
    pass_night(s, X, top, W, mh, lv, center, scroll)
    for pas in range(3):
        for n in range(1, mx_):
            ax, ay = center(n)
            bx, by = center(n + 1)
            if bx < X - PITCH or ax > X + W + PITCH:
                continue
            gold = n + 1 <= lv
            if pas == 0:
                sim_line(s, ax, ay, bx, by, 11, (4, 10, 20, 153))
            elif pas == 1:
                sim_line(s, ax, ay, bx, by, 8, rgb(0xF6B628) if gold else rgb(0x33506E))
            else:
                ln = math.hypot(bx - ax, by - ay)
                d = R
                while d < ln - R:
                    k = d / ln
                    px, py = ax + (bx - ax) * k, ay + (by - ay) * k
                    s.fill(round(px - 1), round(py), round(px + 2), round(py) + 1, rgb(0xFFF0B0) if gold else rgb(0x7896C0))
                    d += 7
    for n, ps in levels:
        cx, cy = center(n)
        if cx < X - RB - 4 or cx > X + W + RB + 4:
            continue
        big = n % 5 == 0
        r = RB if big else R
        st = state(n)
        hv = hover == n
        ring = rgb(0xF6B628) if st == 2 else rgb(0x2EB85A) if st == 1 else rgb(0x8FA6C8)
        inner = rgb(0xFFF4D0) if st == 2 else rgb(0xEAFFF0) if st == 1 else rgb(0xE4EEF8)
        if hv:
            cy -= 2
        if st == 1:
            s.glow(cx, cy, r * 1.9, rgb(0x5CE48A), 150)
        if n == lv:
            s.glow(cx, cy, r * 1.7, rgb(0xFFE680), 120)
        s.fill(cx - r * 0.8, cy + r + 2, cx + r * 0.8, cy + r + 4, (24, 48, 64, 64))
        if big:
            s.disc(cx, cy, r + 4, rgb(0xC07818))
        s.disc(cx, cy, r + 2, rgb(0x102040))
        s.disc(cx, cy, r, lighten(ring, 30) if hv else ring)
        s.disc(cx, cy, r - 3, inner)
        s.disc(cx - r * 0.25, cy - r * 0.3, r * 0.45, (255, 255, 255, 85))
        p0 = ps[0]
        s.item(item_of(p0), round(cx - 16), round(cy - 16), 2)
        if p0['tipo'] == 'objeto' and p0['n'] > 1:
            s.count(p0['n'], round(cx - 16), round(cy - 16), 2)
        if len(ps) > 1:
            more = f'+{len(ps) - 1}'
            mw = s.pwidth(more) + 6
            s.box(round(cx - r - 2), round(cy - r - 2), mw, 11, rgb(0x22346E))
            s.ptext(more, round(cx - r + 1), round(cy - r - 1), (255, 255, 255), False)
        if st == 0:
            s.disc(cx, cy, r - 3, (228, 238, 248, 128))
            lx_, ly_ = round(cx + r - 9), round(cy + r - 11)
            c_ = rgb(0x6A80A4)
            s.fill(lx_ + 2, ly_, lx_ + 5, ly_ + 1, c_)
            s.fill(lx_ + 1, ly_ + 1, lx_ + 2, ly_ + 4, c_)
            s.fill(lx_ + 5, ly_ + 1, lx_ + 6, ly_ + 4, c_)
            s.fill(lx_, ly_ + 3, lx_ + 7, ly_ + 8, c_)
            s.fill(lx_ + 3, ly_ + 5, lx_ + 4, ly_ + 7, (255, 255, 255))
        elif st == 2:
            s.disc(cx + r - 5, cy + r - 6, 6, rgb(0xC07818))
            s.disc(cx + r - 5, cy + r - 6, 5, rgb(0xF6B628))
            for px_, py_ in ((6, 0), (5, 1), (6, 1), (4, 2), (5, 2), (0, 2), (0, 3), (1, 3), (3, 3), (4, 3), (1, 4), (2, 4), (3, 4), (2, 5)):
                s.fill(round(cx + r - 8) + px_, round(cy + r - 9) + py_, round(cx + r - 8) + px_ + 1, round(cy + r - 9) + py_ + 1, (255, 255, 255))
        num = str(n)
        pw = max(16, s.pwidth(num) + 8)
        px, py = round(cx - pw / 2), round(cy + r + 1)
        s.box(px, py, pw, 11, rgb(0x102040))
        s.vgrad(px + 1, py + 1, pw - 2, 9, rgb(0xFFE58A) if st else rgb(0xF2F7FD), rgb(0xF6B628) if st else rgb(0xC4D6EA))
        s.ptext(num, round(cx) - s.pwidth(num) // 2, py + 1, rgb(0x5A3A00) if st else rgb(0x34507E), False)
        if big:
            fx, fy = round(cx + r - 2), round(cy - r - 10)
            s.fill(fx, fy, fx + 1, fy + 12, rgb(0x5A3A10))
            sim_poly(s, [(fx + 1, fy), (fx + 1, fy + 6), (fx + 9, fy + 3)], rgb(0xE2466A))
        if st == 1:
            tu = s.pwidth('TÚ') + 8 if n == lv else 0
            tw = tu + s.pwidth('COBRAR') + 8
            tx, ty = round(cx - tw / 2), round(cy - r - 15)
            s.box(tx, ty, tw, 12, rgb(0x0E5626))
            s.vgrad(tx + 1, ty + 1, tw - 2, 10, rgb(0x5CE48A), rgb(0x1A9442))
            s.fill(tx + 2, ty + 1, tx + tw - 2, ty + 2, rgb(0xA8F8C0))
            if tu:
                s.vgrad(tx + 1, ty + 1, tu - 1, 10, rgb(0xFFE58A), rgb(0xF6B628))
                s.ptext('TÚ', tx + 4, ty + 1, rgb(0x5A3A00), False)
            s.ptext_shadow('COBRAR', tx + tu + 4, ty + 2, (255, 255, 255), rgb(0x0E5626))
            for i in range(3):
                s.fill(round(cx) - 2 + i, ty + 12 + i, round(cx) + 3 - i, ty + 13 + i, rgb(0x1A9442))
    # la chincheta TÚ (si tu nivel está por cobrar, va en su etiqueta)
    cx, cy = center(lv) if state(lv) != 1 else (-999, -999)
    r = RB if lv % 5 == 0 else R
    py_ = cy - r - 3
    w_ = s.pwidth('TÚ') + 8
    bx_, by_ = round(cx - w_ / 2), round(py_ - 12 - 4)
    sim_poly(s, [(cx - 3, by_ + 11), (cx + 4, by_ + 11), (cx + 0.5, py_)], rgb(0x102040))
    sim_poly(s, [(cx - 2, by_ + 11), (cx + 3, by_ + 11), (cx + 0.5, py_ - 1.5)], rgb(0xF6B628))
    s.box(bx_, by_, w_, 12, rgb(0x102040))
    s.vgrad(bx_ + 1, by_ + 1, w_ - 2, 10, rgb(0xFFE58A), rgb(0xF6B628))
    s.ptext('TÚ', bx_ + 4, by_ + 1, rgb(0x5A3A00), False)
    s.no_scissor()
    # flechas
    for ax_, on, left in ((X + 3, scroll > 0, True), (X + W - 13, scroll < (mx_ - 1) * PITCH + 68 - W, False)):
        ay_ = top + mh // 2 - 9
        s.box(ax_, ay_, 10, 18, rgb(0x34507E) if on else (52, 80, 126, 128))
        s.box(ax_ + 1, ay_ + 1, 8, 16, rgb(0xF2F8FF) if on else (238, 243, 249, 128))
        c_ = rgb(0x34507E) if on else rgb(0xB4C6DC)
        for i in range(4):
            xx = ax_ + 5 - 2 + i if left else ax_ + 5 + 1 - i
            s.fill(xx, ay_ + 9 - i, xx + 1, ay_ + 9 + i + 1, c_)
    # el bocadillo
    if hover:
        n = hover
        ps = levels[n - 1][1]
        st = state(n)
        head = f'Nivel {n}' + (' · hito' if n % 5 == 0 else '')
        stt = 'Ya es tuyo' if st == 2 else '¡Listo! Pulsa para cobrar' if st == 1 else f'Te faltan {(n - lv) * 640 - 340:,} XP'.replace(',', '.')
        w_ = max(s.bwidth(head), s.mwidth(stt)) + 14
        for p in ps:
            w_ = max(w_, s.mwidth(prize_name(p)) + 34)
        h_ = 26 + len(ps) * 18
        cx, cy = center(n)
        r = RB if n % 5 == 0 else R
        bx_ = round(max(X + 4, min(X + W - 4 - w_, cx - w_ / 2)))
        below = cy - r - 8 - h_ < top + 2
        by_ = round(cy + r + 14) if below else round(cy - r - 10 - h_)
        by_ = max(top + 2, min(bot - h_ - 2, by_))
        s.fill(bx_ + 2, by_ + h_, bx_ + w_ - 2, by_ + h_ + 2, (16, 32, 64, 64))
        s.box(bx_, by_, w_, h_, rgb(0x102040))
        s.vgrad(bx_ + 1, by_ + 1, w_ - 2, h_ - 2, (255, 255, 255), rgb(0xEAF2FB))
        s.fill(bx_ + 1, by_ + 1, bx_ + w_ - 1, by_ + 3, rgb(0xF6B628) if st == 2 else rgb(0x2EB85A) if st == 1 else rgb(0x8FA6C8))
        s.title(head, bx_ + 7, by_ + 5, w_ - 14, NAVY)
        s.mtext(stt, bx_ + 7, by_ + 15, rgb(0x1A9442) if st == 1 else rgb(0xC27A10) if st == 2 else rgb(0x7E8CA8))
        py = by_ + 25
        for p in ps:
            s.sk_slot(bx_ + 6, py, 18)
            s.item(item_of(p), bx_ + 7, py + 1)
            s.mtext(s.fitend(prize_name(p), w_ - 34), bx_ + 28, py + 5, NAVY)
            py += 18
    # ---- el HUD
    x, y, w, h = X, Y, W, HUD_H
    s.fill(x + 2, y + h, x + w - 2, y + h + 1, (24, 38, 92, 64))
    s.box(x, y, w, h, rgb(0x0A1830))
    s.vgrad(x + 1, y + 1, w - 2, h - 2, rgb(0x183F3A), rgb(0x0E1C34))
    s.tiled('stripes', x + 1, y + 1, w - 2, h - 2, 0.05, off=s.T * 3)
    s.fill(x + 2, y + 1, x + w - 2, y + 2, rgb(0x4FC88C))
    s.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, rgb(0xC07818))
    sx, sy, ss = x + 6, y + 2, 36
    s.scissor(x + 1, y + 1, w - 2, h - 2)
    s.rays(sx + ss / 2, sy + ss / 2, 44, 0.5, s.T * 18)
    s.glow(sx + ss / 2, sy + ss / 2, 28, rgb(0x8CF0B4), 110)
    s.no_scissor()
    cxb = sx + ss // 2
    for row in range(ss):
        half = ss // 4 + row if row < ss // 4 else ss // 4 + (ss - 1 - row) if row > ss * 3 // 4 else ss // 2
        half = min(half, ss // 2)
        s.fill(cxb - half, sy + row, cxb + half, sy + row + 1, rgb(0x0C1030))
        if half > 2 and 0 < row < ss - 1:
            s.fill(cxb - half + 1, sy + row, cxb + half - 1, sy + row + 1, rgb(0xFFE58A) if row < ss // 2 else rgb(0xF6B628))
            if half > 5 and 2 < row < ss - 3:
                s.fill(cxb - half + 3, sy + row, cxb + half - 3, sy + row + 1, rgb(0x3FCB7C) if row < ss // 2 else rgb(0x1E8A50))
    s.ptext('NIVEL', cxb - s.pwidth('NIVEL') // 2, sy + 3, (255, 255, 255))
    with s.big_at(cxb - s.mwidth(str(lv)), sy + 16, px=s.f * 2):
        s.mtext(str(lv), 1, 1, (62, 62, 62))
        s.mtext(str(lv), 0, 0, (255, 255, 255))
    tx = sx + ss + 7
    right = x + w - 5
    clock = '42 d 5 h'
    cw = s.mwidth(clock) + 16
    s.box(right - cw, y + 4, cw, 12, rgb(0x05101E))
    s.vgrad(right - cw + 1, y + 5, cw - 2, 10, rgb(0x2A3470), rgb(0x1A2050))
    s.mtext(clock, right - cw + 12, y + 6, rgb(0xFFE9A8))
    gt = '7'
    gw = s.mwidth(gt) + 16
    gx = right - cw - 4 - gw
    s.box(gx, y + 4, gw, 12, rgb(0x05101E))
    s.vgrad(gx + 1, y + 5, gw - 2, 10, rgb(0x1E6B52), rgb(0x124436))
    s.blit('coin_green_s', gx + 3, y + 6)
    s.mtext(gt, gx + 13, y + 6, rgb(0x8CF0B4))
    ready = lv - claimed
    bar_right = right
    if ready > 0:
        label = f'RECLAMAR TODO ({ready})'
        bw = s.bw(label)
        bx = right - bw
        s.sk_button(bx, y + h - 5 - 16, bw, label, 3, True)
        bar_right = bx - 6
    s.ptext_shadow('TF PASS', tx, y + 4, rgb(0xFFD650), rgb(0x3A2A00))
    season = 'Temporada 1: El Despertar'
    room = gx - 6 - (tx + s.pwidth('TF PASS') + 6)
    if room > 30:
        s.mtext(s.fitend(season, room), tx + s.pwidth('TF PASS') + 6, y + 5, rgb(0xE0ECFF))
    bxx, byy, bww, bhh = tx, y + 18, max(60, bar_right - tx), 9
    f_ = 0.42
    s.box(bxx, byy, bww, bhh, rgb(0x05101E))
    s.fill(bxx + 1, byy + 1, bxx + bww - 1, byy + bhh - 1, rgb(0x1E2C50))
    fw = round((bww - 2) * f_)
    s.vgrad(bxx + 1, byy + 1, fw, bhh - 2, rgb(0x9CF7B6), rgb(0x1E9E46))
    s.fill(bxx + 1, byy + 1, bxx + 1 + fw, byy + 2, rgb(0xE0FFE8))
    for i in range(1, 10):
        mk = bxx + 1 + (bww - 2) * i // 10
        s.fill(mk, byy + 1, mk + 1, byy + bhh - 1, (5, 16, 30, 64))
    s.mtext('270 / 640 XP', bxx, byy + 11, rgb(0xE0ECFF))
    nx = f'nivel {lv + 1}'
    s.mtext(nx, bxx + bww - s.mwidth(nx), byy + 11, rgb(0xB8C2F0))
    # ---- abajo: XP
    yy = Y + H - FOOT_H
    s.box(X, yy, W, FOOT_H, rgb(0x0A1830))
    s.vgrad(X + 1, yy + 1, W - 2, FOOT_H - 2, rgb(0x183F3A), rgb(0x0E1C34))
    s.ptext_shadow('XP:', X + 6, yy + 3, rgb(0xFFD650), rgb(0x3A2A00))
    chx = X + 8 + s.pwidth('XP:') + 4
    for t_ in ('+40 Misión diaria', '+120 Misión semanal', '+25 Caza', '+10 Oficio'):
        cw_ = s.chip_w(t_)
        if chx + cw_ > X + W - 6:
            break
        s.sk_chip(chx, yy + 2, t_, 'green', 11)
        chx += cw_ + 4
    loc.__exit__(None, None, None)
    s.save(OUT + name + '.png')
    NAMES.append(name)


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


# --- Guía (PadGuidePage): el texto se lee del propio Java -----------------------------------------------------------
def guide_topics():
    import re
    src = open(os.path.join(ROOT, 'src/main/java/net/tierrasfantasticas/tfclient/pad/PadGuidePage.java'), encoding='utf-8').read()
    body = src[src.index('    static {'):]
    out = []
    for m in re.finditer(r'topic\("([^"]*)", "([^"]*)", "([^"]*)",(.*?)\);\n\n', body + '\n\n', re.S):
        blocks = [(k, t) for k, t in re.findall(r'\b(h|p|s|b|tip)\("((?:[^"\\]|\\.)*)"\)', m.group(4))]
        out.append((m.group(1), m.group(2), m.group(3), blocks))
    return out


def guide_page(name, sel, text_scroll=0, hover=None):
    if ONLY and name not in ONLY:
        return
    topics = guide_topics()
    s = Sim()
    s.status('GUÍA')
    with Local(s):
        X, Y, W, H = s.X, s.Y, s.W, s.H
        acc = accent('guia')
        s.sk_window(X, Y, W, H, acc)
        bh = s.banner_h([1])
        s.sk_banner(X + 1, Y + 1, W - 2, bh, acc, 'guia', [tagline('guia')])
        top, bottom = Y + bh + 5, Y + H - 5
        ROW = 18
        lw = max(104, min(126, W // 4))
        lx, ly, lh = X + 5, top, bottom - top
        s.box(lx - 1, ly - 1, lw + 2, lh + 2, mix((198, 218, 238), acc, 0.2))
        s.fill(lx, ly, lx + lw, ly + lh, rgb(0xF3F8FD))
        clip = s.clip
        s.scissor(lx, ly, lw, lh)
        for i, (app, nm, icon, _) in enumerate(topics):
            y = ly + i * ROW
            tc = accent(app or 'guia')
            if i == sel:
                s.vgrad(lx, y, lw, ROW, mix(tc, WHITE, 0.25), darken_t(tc, 0.85))
                s.fill(lx, y, lx + lw, y + 1, lighten(tc, 70))
                s.fill(lx, y + ROW - 1, lx + lw, y + ROW, darken_t(tc, 0.6))
            else:
                if hover == i:
                    s.fill(lx, y, lx + lw, y + ROW, tint(tc, 0.22))
                s.fill(lx + 3, y + ROW - 1, lx + lw - 3, y + ROW, (24, 38, 92, 20))
            s.fill(lx, y + 1, lx + 2, y + ROW - 1, WHITE if i == sel else tc)
            s.icon_crisp(icon, lx + 13, y + ROW / 2, 14)
            if i == sel:
                s.btext(nm, lx + 24, y + 6, darken_t(tc, 0.45))
                s.btext(nm, lx + 23, y + 5, WHITE)
            else:
                s.mtext(nm, lx + 23, y + 5, TEXT)
        s.clip = clip
        s.sk_scrollbar(lx + lw + 2, ly, lh, lh, len(topics) * ROW, 0, acc)
        app, nm, icon, blocks = topics[sel]
        tc = accent(app or 'guia')
        cx = lx + lw + 10
        cw = X + W - 6 - cx
        s.sk_slot(cx, top, 28, deep_t(tc))
        s.icon_crisp(icon, cx + 14, top + 14, 24)
        right = cx + cw
        if app:
            label = 'ABRIR ' + nm.upper()
            if s.bw(label) > cw // 2:
                label = 'ABRIR'
            bw = s.bw(label)
            s.sk_button(right - bw, top + 6, bw, label, 3)
            right -= bw + 6
        s.btext(nm, cx + 35, top + 6, (24, 38, 92, 32))
        s.btext(nm, cx + 34, top + 5, darken_t(tc, 0.7))
        summ = next((t for k, t in blocks if k == 'p'), '')
        s.mtext(s.fitend(summ, right - cx - 34), cx + 34, top + 17, MUTED)
        s.fill(cx, top + 31, cx + cw, top + 32, tint(tc, 0.5))
        s.fill(cx, top + 32, cx + cw, top + 33, WHITE)
        tx, ty, tw = cx, top + 35, cw - 7
        th = bottom - ty
        s.scissor(tx, ty, tw + 1, th)
        y = ty - text_scroll
        step, first = 0, True
        content = 0
        for k, t in blocks:
            t = t.replace('\\"', '"')
            if k == 'h':
                h = (0 if first else 6) + 14
                s.sk_section(tx, y + (0 if first else 6) + 2, tw, t, tc)
                step = 0
            elif k == 'p':
                h = s.nlines(t, tw) * 10 + 5
                s.wrap(t, tx, y + 1, tw, TEXT)
            elif k == 's':
                step += 1
                h = max(13, s.nlines(t, tw - 16) * 10 + 3)
                s.disc(tx + 6, y + 5.5, 6, darken_t(tc, 0.62))
                s.disc(tx + 6, y + 5.5, 5, tc)
                s.mtext(str(step), tx + 6 - s.mwidth(str(step)) // 2 + 1, y + 2, WHITE)
                s.wrap(t, tx + 16, y + 1, tw - 16, TEXT)
            elif k == 'b':
                h = s.nlines(t, tw - 11) * 10 + 2
                s.diamond(tx + 4, y + 4)
                s.wrap(t, tx + 11, y, tw - 11, TEXT)
            else:
                h = 16 + s.nlines(t, tw - 12) * 10 + 9
                bh2 = h - 5
                s.box(tx, y + 1, tw, bh2, tint(tc, 0.55))
                s.vgrad(tx + 1, y + 2, tw - 2, bh2 - 2, tint(tc, 0.16), tint(tc, 0.28))
                s.fill(tx + 1, y + 2, tx + 3, y + bh2, tc)
                s.btext('CONSEJO', tx + 8, y + 5, darken_t(tc, 0.62))
                s.wrap(t, tx + 8, y + 16, tw - 12, TEXT)
            y += h
            content += h
            first = False
        s.clip = clip
        if text_scroll < content + 4 - th:
            s.vgrad_a(tx, ty + th - 10, tw, 10, (244, 249, 254, 0), (244, 249, 254, 230))
        s.sk_scrollbar(tx + tw + 3, ty, th, th, content + 4, text_scroll, tc)
    s.save(OUT + name + '.png')
    NAMES.append(name)


from skin import deep as deep_t, tint, tagline, WHITE  # noqa: E402,F811
from sim import TEXT, MUTED  # noqa: E402
guide_page('p_guia', 0)
guide_page('p_guia_musica', 1, hover=4)
guide_page('p_guia_musica2', 1, text_scroll=170)
guide_page('p_guia_armario', 20)
