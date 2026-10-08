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


page('p_oficios', 'OFICIOS', [], '', ['Trabajas de Minero · cobras 42 monedas en el próximo pago.'], [
    ('iron_pickaxe', 'Minero · tu oficio', 0x8C9BB4, ['Pica piedra y minerales bajo tierra.'], 0.28, 'NIVEL 12', None, None, True),
    ('wheat', 'Granjero', 0x6FB43C, ['Cosecha y planta cultivos.'], 0.5, 'NIVEL 3', None, None),
    ('fishing_rod', 'Pescador', 0x3C8CD2, ['Pesca en ríos y mares.'], -1, 'NUEVO', None, None),
])
page('p_oficio', 'OFICIOS', [('m', 'MISIONES (1)'), ('a', 'CÓMO SE GANA'), ('r', 'PREMIOS')], 'm',
     ['Minero · nivel 12/50 · 340/1200 xp'], [
         ('coal', 'Carbón para el invierno', GREEN, ['Romper: 64/64 · cada día'], 1.0, '+120', ('COBRAR', G), None, True),
         ('iron_ingot', 'Hierro de calidad', T, ['Romper: 20/48 · cada día'], 0.42, '+200', None, None),
         ('gray_dye', 'Diamantes', M, ['Se desbloquea en el nivel 15.'], -1, '+800', None, None),
     ], footer=[('ATRÁS', B), ('DEJAR', R)])
page('p_tienda', 'TIENDA', [('', 'COMPRAR'), ('v', 'VENDER')], '', [], [
    ('diamond', 'Minerales', 0x3CB4E6, ['Diamantes, hierro, oro y más.'], -1, '12 objetos', None, None),
    ('bread', 'Comida', 0xD2A050, ['Para no pasar hambre.'], -1, '8 objetos', None, None),
    ('oak_log', 'Bloques', 0x8C6E3C, ['Madera, piedra, cristal…'], -1, '20 objetos', None, None),
])
page('p_tienda_cat', 'TIENDA', [], '', ['Minerales · pulsa un objeto.'], [], cells=[
    ('diamond', '950', GOLD, False), ('iron_ingot', '120', GOLD, False), ('gold_ingot', '300', GOLD, False),
    ('emerald', '1,2K', GOLD, False), ('coal', '20', GOLD, False), ('redstone', '45', GOLD, False),
    ('lapis_lazuli', '60', GOLD, False), ('quartz', '+40', GREEN, False), ('netherite_ingot', '35K', GOLD, False),
    ('copper_ingot', '30', GOLD, False), ('amethyst_shard', '80', GOLD, False), ('raw_iron', '+15', GREEN, False),
], footer=[('ATRÁS', B)])
page('p_tienda_item', 'TIENDA', [('b', 'COMPRAR'), ('s', 'VENDER')], 'b', [], [
    ('diamond', 'Diamante', T, ['Compra 950 monedas · Venta 400 monedas (1 ud.)', 'Llevas 3.'], -1, '', None, None),
], cells=[('diamond', '950', GOLD, False), ('diamond', '7,6K', GOLD, False), ('diamond', '30K', M, False), ('diamond', '60K', M, False)],
     input_=('OTRA CANTIDAD (LOTES)', 'COMPRAR', ''), footer=[('ATRÁS', B)])
page('p_gts', 'GTS', [('', 'MERCADO'), ('v', 'VENDER'), ('m', 'MIS VENTAS (2)'), ('r', 'RECOGER')], '', [], [], cells=[
    ('diamond_sword', '5K', GOLD, False), ('elytra', '120K', GOLD, False), ('golden_apple', '800', GOLD, False),
    ('enchanted_book', '2,5K', GOLD, False), ('trident', '40K', GREEN, True), ('ender_pearl', '150', GOLD, False),
    ('totem_of_undying', '25K', GOLD, False), ('shulker_shell', '3K', GOLD, False),
], input_=('BUSCAR OBJETO O JUGADOR', 'BUSCAR', ''))
page('p_gts_vender', 'GTS', [('', 'MERCADO'), ('v', 'VENDER'), ('m', 'MIS VENTAS (2)'), ('r', 'RECOGER')], 'v',
     ['Vendes 1× Tridente. Escribe el precio (1500, 5k, 2m) y PUBLICAR.'], [], cells=[
         ('diamond_pickaxe', '', M, False), ('trident', '', M, True), ('bread', '', M, False), ('torch', '', M, False),
         ('iron_ingot', '', M, False), ('tf:fantastic_coin', 'NO', M, False), ('arrow', '', M, False), ('bow', '', M, False),
     ], input_=('PRECIO', 'PUBLICAR', '40k'))
page('p_hogares', 'HOGARES', [], '', ['2 de 5 hogares. Escribe un nombre y GUARDAR AQUÍ guarda donde estás.'], [
    ('red_bed', 'casa', T, ['Mundo normal · 120, 64, -340 · a 85 m'], -1, '', ('IR', B), ('BORRAR', R)),
    ('crimson_nylium', 'base_nether', T, ['Nether · 40, 70, 12'], -1, '', ('IR', B), ('BORRAR', R)),
], input_=('NOMBRE DEL HOGAR', 'GUARDAR AQUÍ', ''))
page('p_armario', 'ARMARIO', [('head', 'CABEZA ·'), ('chest', 'PECHO'), ('legs', 'PIERNAS'), ('feet', 'PIES'), ('back', 'ESPALDA')],
     'head', ['Solo cambia cómo te ven: tu armadura real sigue igual.'], [
         ('diamond_helmet', 'Casco de Oni', T, ['De Crate Oni.'], -1, 'PUESTO', ('QUITAR', R), None, True),
         ('golden_helmet', 'Corona del Rey', T, ['De Rango Rey.'], -1, '', ('PONER', G), None),
     ])
page('p_efectos', 'EFECTOS', [('', 'AL MATAR'), ('skills', 'SKILLS')], '', ['Lo que se ve al derrotar a alguien. Gratis: elige uno.'], [
    ('wither_skeleton_skull', 'Explosión de almas', T, [], -1, 'EQUIPADO', ('QUITAR', R), None, True),
    ('wither_skeleton_skull', 'Lluvia de pétalos', T, [], -1, '', ('EQUIPAR', G), None),
    ('wither_skeleton_skull', 'Rayo divino', T, [], -1, '', ('EQUIPAR', G), None),
])
page('p_proteccion', 'PROTECCIÓN', [], '', ['Tus zonas: 2. Pulsa una para administrarla.'], [
    ('tfb:claims/proteccion_100x100_front', 'Zona 100x100', T, ['Mundo normal · 120, 64, -340', '3 miembros'], -1, 'ESTÁS AQUÍ', None, None, True),
    ('tfb:claims/proteccion_25x25_front', 'Zona 25x25', T, ['Mundo normal · 900, 70, 40', 'Sin miembros'], -1, '', None, None),
])
page('p_proteccion_ajustes', 'PROTECCIÓN', [('a', 'AJUSTES'), ('m', 'MIEMBROS (3)'), ('b', 'BANEOS'), ('p', 'PARTÍCULAS'), ('x', 'MÁS')], 'a',
     ['Zona 100x100 · Mundo normal · 120, 64, -340'], [
         (None, 'Bloques y terreno', GOLD, [], -1, '', None, None),
         ('bricks', 'No construir', T, ['Los de fuera no colocan bloques.'], -1, '', ('SÍ', G), None),
         ('tnt', 'Sin explosiones', T, ['La TNT y los creepers no destruyen.'], -1, '', ('NO', GR), None),
         ('golden_apple', 'Regeneración', M, ['Para ti y tus miembros.'], -1, '', ('250X250', GR, False), None),
     ], footer=[('ATRÁS', B)])
page('p_proteccion_miembros', 'PROTECCIÓN', [('a', 'AJUSTES'), ('m', 'MIEMBROS (3)'), ('b', 'BANEOS'), ('p', 'PARTÍCULAS'), ('x', 'MÁS')], 'm',
     ['Pueden construir y usarlo todo.'], [
         ('player_head', 'Steve', T, ['Conectado'], -1, '', ('QUITAR', R), None),
         ('player_head', 'Alex', T, ['Desconectado'], -1, '', ('QUITAR', R), None),
     ], input_=('NOMBRE DEL JUGADOR', 'AÑADIR', ''), footer=[('ATRÁS', B)])
page('p_proteccion_particulas', 'PROTECCIÓN', [('a', 'AJUSTES'), ('m', 'MIEMBROS (3)'), ('b', 'BANEOS'), ('p', 'PARTÍCULAS'), ('x', 'MÁS')], 'p',
     ['Partículas encendidas · densidad 10 · pulsa una.'], [], cells=[
         ('poppy', 'CORAZÓN', T, False), ('blaze_powder', 'LLAMA', GOLD, True), ('torch', 'LLAMITA', T, False),
         ('soul_torch', 'ALMA', T, False), ('end_rod', 'VARA', T, False), ('iron_sword', 'CRÍTICO', T, False),
         ('diamond_sword', 'MÁGICO', T, False), ('enchanted_book', 'RUNAS', T, False),
     ], footer=[('MENOS', GR), ('MÁS', GR), ('APAGAR', R), ('ATRÁS', B)])
page('p_monedero', 'MONEDERO', [], '', [], [
    ('tf:fantastic_coin', 'Tu saldo', T, ['Se gana con oficios, misiones, cazas, la tienda y el GTS.'], -1, '1.250 monedas', None, None),
    ('clock_00', 'Por cobrar del oficio', T, ['Llega en el próximo pago.'], -1, '+42 monedas', None, None),
    ('emerald', 'A la venta en el GTS', T, ['2 cosas. Cobras al momento cuando se venden.'], -1, '45.000 monedas', ('VER', B), None),
], input_=('MANDAR: JUGADOR CANTIDAD', 'PREPARAR', ''))
page('p_rango', 'MI RANGO', [], '', ['Tu rango: Rey. Pulsa uno para ver todo lo que trae.'], [
    ('iron_ingot', 'Mortal', 0x55FF55, ['Prefijo «Mortal» con color en el chat', '5 hogares'], -1, '', None, None),
    ('gold_ingot', 'Rey', 0xFFAA00, ['Prefijo «Rey» con color en el chat', '10 hogares'], -1, 'TU RANGO', None, None, True),
    ('diamond', 'Dragón', 0xAA00AA, ['Prefijo «Dragón» con color en el chat', '15 hogares'], -1, '', None, None),
])
page('p_viajes', 'VIAJES', [], '', ['Elige a dónde ir. 3 s sin moverte y ¡listo!'], [
    ('compass_00', 'Spawn', T, ['El corazón del servidor'], -1, '', ('VIAJAR', B), None),
    ('filled_map', 'Mercado central', T, ['Mundo normal'], -1, '', ('VIAJAR', B), None),
    ('spyglass', 'Explorar', T, ['Un sitio seguro al azar, a 800-6000 bloques del spawn'], -1, '', ('EXPLORAR', GO), None),
    ('red_bed', 'Tus hogares', T, ['Los sitios que guardaste'], -1, '', ('VER', B), None),
])
page('p_misiones', 'MISIONES', [('diarias', 'DIARIAS'), ('semanales', 'SEMANALES')], 'diarias', ['Se renuevan en 5 h 12 min.'], [
    ('coal', 'Minero de carbón', T, ['Saca 24 menas de carbón  ·  24/24'], 1.0, '+80', ('RECLAMAR', G), None),
    ('rotten_flesh', 'Noche de zombis', T, ['Derrota 15 zombis  ·  9/15'], 0.6, '+90', None, None),
], footer=[('RECLAMAR TODO', G)])

# Comunidad (PadCommunityPage)
photo = Image.open(os.path.join(ROOT, 'src/main/resources/assets/tfclient/textures/gui/menu_background.png')).convert('RGBA')
s = Sim()
s.status('COMUNIDAD')
X, Y, W, H = s.X, s.Y, s.W, s.H
x = X
for key, label in (('recientes', 'RECIENTES'), ('populares', 'POPULARES'), ('mias', 'MÍAS')):
    w = s.pwidth(label) + 10
    sel = key == 'recientes'
    s.box(x, Y, w, 13, (24, 38, 92))
    s.box(x + 1, Y + 1, w - 2, 11, rgb(0xF6B628 if sel else 0xE8F8FF))
    s.ptext(label, x + w // 2 - s.pwidth(label) // 2, Y + 1, (255, 255, 255) if sel else (24, 38, 92), sel)
    x += w + 3
fw = s.bw('+ FOTO')
s.button(X + W - fw, Y - 1, fw, '+ FOTO', GO)
top = Y + 16
s.panel(X, top, W, H - 16)
px, py = X + 6, top + 5
s.box(px - 1, py - 1, 146, 83, (24, 38, 92))
s.art.alpha_composite(photo.resize((144, 81)), (px, py))
cx = px + 152
cw = X + W - 6 - cx
s.mtext('Pewez777', cx, top + 6, (24, 38, 92))
s.mtext('hace 12 min', cx, top + 16, rgb(0x4A6694))
s.mtext('1/24', cx + cw - s.mwidth('1/24'), top + 16, rgb(0x4A6694))
s.wrap('«Atardecer desde la torre del spawn»', cx, top + 29, cw, (24, 38, 92), 3)
heart = s.img('heart').resize((14, 12), Image.NEAREST)
s.art.alpha_composite(heart, (cx, top + 62))
s.mtext('18 likes', cx + 20, top + 65, (24, 38, 92))
s.button(cx, top + H - 16 - 19, cw, 'DENUNCIAR', B)
s.save(OUT + 'p_comunidad.png')
NAMES.append('p_comunidad')

# Cámara (PadCameraPage)
s = Sim()
s.status('CÁMARA')
s.panel(X, Y, W, H)
px, py = X + 6, Y + 5
s.box(px - 1, py - 1, 146, 83, (24, 38, 92))
s.art.alpha_composite(photo.resize((144, 81)), (px, py))
by = Y + H - 17
cx = px + 152
cw = X + W - 6 - cx
s.button(px - 1, by, 146, 'MODO FOTO', G)
s.button(cx, by, cw, 'CARPETA', B)
s.mtext('Foto 1 de 7', cx, py + 1, (24, 38, 92))
s.mtext('8 oct · 18:30', cx, py + 11, rgb(0x4A6694))
s.button(cx, py + 26, cw, 'PUBLICAR', GO)
s.button(cx, py + 45, cw, 'BORRAR', R)
s.save(OUT + 'p_camara.png')
NAMES.append('p_camara')

# hoja con todo
ims = [Image.open(OUT + n + '.png').crop((180, 220, 1400, 820)) for n in NAMES]
w, h = ims[0].size
rows = (len(ims) + 1) // 2
sheet = Image.new('RGBA', (w * 2 + 10, (h + 10) * rows), (20, 24, 40, 255))
for i, im in enumerate(ims):
    sheet.alpha_composite(im, ((i % 2) * (w + 10), (i // 2) * (h + 10)))
sheet = sheet.resize((sheet.width // 2, sheet.height // 2), Image.LANCZOS)
sheet.save(OUT + 'hoja_apps.png')
print('ok', len(NAMES))
