"""Vistas previas de las apps del pad: python3 tools/pad/sim/pages.py <carpeta de salida>.

Dibuja las apps con el mismo layout que PadViewPage (pestañas, cabecera, rejilla y filas), PadCommunityPage y
PadCameraPage, con la fuente de Minecraft, y una hoja con todas (hoja_apps.png). Los datos son de ejemplo. Si cambias
posiciones en esas clases Java, cámbialas también en sim.py.
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sim import ROOT, Sim, rgb  # noqa: E402

if len(sys.argv) < 2:
    sys.exit('Uso: python3 tools/pad/sim/pages.py <carpeta de salida>')
OUT = os.path.join(sys.argv[1], '')
os.makedirs(OUT, exist_ok=True)
G, B, R, GR, GO = 3, 0, 2, 4, 1
T, M, GOLD, GREEN = 0x18265C, 0x7E8CA8, 0xC27A10, 0x1E9E46
NAMES = []


def page(name, title, *args, **kw):
    s = Sim()
    s.status(title)
    s.view(*args, **kw)
    s.save(OUT + name + '.png')
    NAMES.append(name)


APPS = [('oficios', 'OFICIOS'), ('misiones', 'MISIONES'), ('cazas', 'CAZAS'), ('tienda', 'TIENDA'), ('gts', 'GTS'),
        ('monedero', 'MONEDERO'), ('viajes', 'VIAJES'), ('hogares', 'HOGARES'), ('kits', 'KITS'),
        ('protecciones', 'PROTECCIÓN'), ('clanes', 'CLANES'), ('jugadores', 'JUGADORES'), ('comunidad', 'COMUNIDAD'),
        ('camara', 'CÁMARA'), ('ranking', 'RANKING'), ('armario', 'ARMARIO'), ('efectos', 'EFECTOS'), ('rango', 'MI RANGO'),
        ('ayuda', 'AYUDA')]
ADMIN = [('admin', 'APPS'), ('tienda', 'TIENDA'), ('kits', 'KITS'), ('viajes', 'VIAJES'), ('gts', 'GTS'),
         ('comunidad', 'COMUNIDAD'), ('oficios', 'OFICIOS'), ('admin', 'AJUSTES')]

s = Sim()
s.status('TF PAD', back=False)
s.home(APPS)
s.save(OUT + 'p_inicio.png')
NAMES.append('p_inicio')

s = Sim()
s.status('PAD ADMIN', back=False, title_color=rgb(0xFFD36A))
s.home(ADMIN)
s.save(OUT + 'p_admin.png')
NAMES.append('p_admin')

page('p_oficios', 'OFICIOS', [], '', ['Trabajas de Minero · cobras 42 monedas en el próximo pago.'], [
    ('iron_pickaxe', 'Minero · tu oficio', 0x8C9BB4, ['Pica piedra y minerales bajo tierra.'], 0.28, 'NIVEL 12', None, None, True),
    ('wheat', 'Granjero', 0x6FB43C, ['Cosecha y planta cultivos.'], 0.5, 'NIVEL 3', None, None),
    ('fishing_rod', 'Pescador', 0x3C8CD2, ['Pesca en ríos y mares.'], -1, 'NUEVO', None, None),
])
page('p_tienda', 'TIENDA', [('', 'COMPRAR'), ('v', 'VENDER')], '', [], [
    ('diamond', 'Minerales', 0x3CB4E6, ['Diamante, netherita, lapislázuli'], -1, '3 objetos', None, None),
    ('bread', 'Comida', 0xD2A050, ['Pan, filete, manzana dorada'], -1, '8 objetos', None, None),
])
page('p_tienda_cat', 'TIENDA', [], '', ['Minerales'], [], cards=[
    ('diamond', 'Diamante', 0xF6B628, '950 monedas', False), ('netherite_scrap', 'Chatarra de netherita', 0xF6B628, '6K monedas', False),
    ('lapis_lazuli', 'Lapislázuli', 0x40C850, 'Vende +12', False), ('emerald', 'Esmeralda', 0xF6B628, '1,2K monedas', False),
    ('gold_ingot', 'Lingote de oro', 0xF6B628, '300 monedas', False),
], footer=[('ATRÁS', B)])
page('p_gts', 'GTS', [('', 'MERCADO'), ('v', 'VENDER'), ('m', 'MIS VENTAS (2)'), ('r', 'RECOGER')], '', [], [], cards=[
    ('diamond_sword', 'Espada de diamante', 0xF6B628, '5K · Steve', False), ('elytra', 'Élitros', 0xF6B628, '120K · Alex', False),
    ('trident', 'Tridente', 0x40C850, '40K · tuyo', True), ('totem_of_undying', 'Tótem', 0xF6B628, '25K · Notch', False),
    ('enchanted_book', 'Libro encantado', 0xF6B628, '2,5K · Steve', False),
], input_=('BUSCAR OBJETO O JUGADOR', 'BUSCAR', ''))
page('p_viajes', 'VIAJES', [], '', [], [], cards=[
    ('compass_00', 'Spawn', 0x3496FA, 'Mundo normal', False), ('red_bed', 'Tu cama', 0xE83446, 'Mundo normal', False),
    ('filled_map', 'Mercado', 0xF6B628, 'Mundo normal', False), ('ender_pearl', 'Arena PvP', 0x9A5CF0, 'Warp', False),
    ('ender_pearl', 'Nether hub', 0x9A5CF0, 'Warp', False), ('spyglass', 'Explorar', 0x40C850, 'Al azar', False),
    ('oak_door', 'Tus hogares', 0xC27A10, 'Abrir', False),
])
page('p_kits', 'KITS', [], '', ['1 listo para reclamar.'], [], cards=[
    ('bundle', 'Kit inicial', 0xAABAD2, 'Reclamado', False), ('bread', 'Diario', 0x40C850, 'Listo', True),
    ('iron_sword', 'Semanal', 0xF6B628, 'en 3 d 4 h', False), ('diamond', 'VIP', 0xF6B628, 'en 5 h', False),
])
page('p_kit', 'KITS', [], '', ['Diario · cada día'], [], cells=[
    ('bread', '', T, False), ('cooked_beef', '', T, False), ('torch', '', T, False), ('tf:fantastic_coin', '200', GOLD, False),
], footer=[('ATRÁS', B), ('RECLAMAR', G)])
page('p_jugadores', 'JUGADORES', [], '', ['3 conectados'], [], cards=[
    ('player_head', 'Pewez777 (tú)', 0xF6B628, 'Rey', False), ('player_head', 'Steve', 0x3496FA, 'Mortal', False),
    ('player_head', 'Alex', 0x3496FA, '', False),
])
page('p_regalar', 'JUGADORES', [('f', 'FICHA'), ('r', 'REGALAR')], 'r',
     ['Regalo para Steve: elige un objeto o escribe monedas. Te quedan 9 hoy.'], [], cells=[
         ('diamond_pickaxe', '', M, False), ('golden_apple', '', M, True), ('bread', '', M, False), ('torch', '', M, False),
         ('iron_ingot', '', M, False), ('tf:pad_admin', 'NO', M, False), ('arrow', '', M, False), ('bow', '', M, False),
     ], input_=('MONEDAS', 'ENVIAR', ''), footer=[('REGALAR', G)])
page('p_hogares', 'HOGARES', [], '', ['2 de 5 hogares.'], [
    ('red_bed', 'casa', T, ['Mundo normal · 120, 64, -340 · a 85 m'], -1, '', ('IR', B), ('BORRAR', R)),
    ('crimson_nylium', 'base_nether', T, ['Nether · 40, 70, 12'], -1, '', ('IR', B), ('BORRAR', R)),
], input_=('NOMBRE DEL HOGAR', 'GUARDAR AQUÍ', ''))
page('p_misiones', 'MISIONES', [('diarias', 'DIARIAS'), ('semanales', 'SEMANALES')], 'diarias', ['Se renuevan en 5 h 12 min.'], [
    ('coal', 'Minero de carbón', T, ['Saca 24 menas de carbón  ·  24/24'], 1.0, '+80', ('RECLAMAR', G), None),
    ('rotten_flesh', 'Noche de zombis', T, ['Derrota 15 zombis  ·  9/15'], 0.6, '+90', None, None),
], footer=[('RECLAMAR TODO', G)])
page('a_tienda_item', 'TIENDA', [], '', ['Minerales · Diamante'], [
    ('gold_ingot', 'Precio de compra', T, ['Lo que paga el jugador por lote. 0 = no se vende.'], -1, '950', ('EDITAR', B), None, True),
    ('emerald', 'Precio de venta', T, ['Lo que cobra el jugador. 0 = la tienda no lo compra.'], -1, '400', ('EDITAR', B), None),
    ('chest', 'Cantidad por lote', T, ['Objetos que van en cada lote.'], -1, '1', ('EDITAR', B), None),
], input_=('NUEVO VALOR', 'GUARDAR', '1200'), footer=[('ATRÁS', B)])
page('a_apps', 'APPS', [], '', ['Las que apagues no salen en el pad de los jugadores.'], [
    ('iron_pickaxe', 'Oficios', T, ['Activa'], -1, '', ('SÍ', G), None),
    ('writable_book', 'Misiones', T, ['Activa'], -1, '', ('SÍ', G), None),
    ('crossbow', 'Cazas', M, ['Apagada'], -1, '', ('NO', GR), None),
])

# Comunidad (PadCommunityPage): pestañas, + FOTO y las publicaciones una debajo de otra
photo = Image.open(os.path.join(ROOT, 'src/main/resources/assets/tfclient/textures/gui/menu_background.png')).convert('RGBA')
s = Sim()
s.status('COMUNIDAD')
X, Y, W, H = s.X, s.Y, s.W, s.H
x = X
for key, label in (('recientes', 'RECIENTES'), ('populares', 'POPULARES'), ('mias', 'MÍAS')):
    w = s.pwidth(label) + 12
    sel = key == 'recientes'
    s.box(x, Y, w, 13, (24, 38, 92))
    s.box(x + 1, Y + 1, w - 2, 11, rgb(0xF6B628 if sel else 0xE8F8FF))
    s.ptext(label, x + w // 2 - s.pwidth(label) // 2, Y + 1, (255, 255, 255) if sel else (24, 38, 92), sel)
    x += w + 3
fw = s.bw('+ FOTO')
s.button(X + W - fw, Y - 1, fw, '+ FOTO', GO)
top, bottom = Y + 17, Y + H
s.panel(X, top, W, bottom - top)
pw = max(128, min(256, (W - 20) // 2)) // 16 * 16
ph = pw * 9 // 16
ch = ph + 12
s.scissor(X + 1, top + 2, W - 2, bottom - top - 4)
posts = [('Pewez777', 'hace 12 min', 'Atardecer desde la torre del spawn', 18, True, False),
         ('Steve', 'hace 2 h', '', 4, False, False)]
cy = top + 4
for name, ago, cap, likes, liked, mine in posts:
    x, y, w, h = X + 4, cy, W - 14, ch - 4
    s.box(x, y, w, h, rgb(0xB8D4EE))
    s.box(x + 1, y + 1, w - 2, h - 2, (255, 255, 255))
    px, py = x + 4, y + 4
    s.box(px - 1, py - 1, pw + 2, ph + 2, (24, 38, 92))
    s.paste(photo.resize((pw, ph)), px, py)
    cx = px + pw + 10
    cw = x + w - 6 - cx
    s.mtext(name, cx, py + 1, (24, 38, 92))
    s.mtext(ago, cx, py + 12, rgb(0x4A6694))
    if cap:
        s.wrap_ellipsis('«' + cap + '»', cx, py + 26, cw, (24, 38, 92), 3)
    ly = py + ph - 15
    s.blit('heart' if liked else 'heart_off', cx, ly)
    s.mtext(f'{likes} likes', cx + 16, ly + 2, (24, 38, 92))
    bw = s.bw('DENUNCIAR')
    s.button(x + w - 6 - bw, ly - 1, bw, 'DENUNCIAR', B)
    cy += ch
s.no_scissor()
s.save(OUT + 'p_comunidad.png')
NAMES.append('p_comunidad')

# hoja con todo
ims = [Image.open(OUT + n + '.png').crop((150, 200, 1418, 830)) for n in NAMES]
w, h = ims[0].size
rows = (len(ims) + 1) // 2
sheet = Image.new('RGBA', (w * 2 + 10, (h + 10) * rows), (20, 24, 40, 255))
for i, im in enumerate(ims):
    sheet.alpha_composite(im, ((i % 2) * (w + 10), (i // 2) * (h + 10)))
sheet = sheet.resize((sheet.width // 2, sheet.height // 2), Image.LANCZOS)
sheet.save(OUT + 'hoja_apps.png')
print('ok', len(NAMES))
