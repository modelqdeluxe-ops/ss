"""Vistas previas de las apps del pad: python3 tools/pad/sim/pages.py <carpeta de salida>.

Dibuja las apps con el mismo layout que PadViewPage (pestañas, cabecera, rejilla y filas), PadCommunityPage y
PadCameraPage, con la fuente de Minecraft, y una hoja con todas (hoja_apps.png). Los datos son de ejemplo. Si cambias
posiciones en esas clases Java, cámbialas también en sim.py.
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sim import NAVY, ROOT, Sim, rgb  # noqa: E402

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


def page(name, title, *args, **kw):
    """Una app del servidor (PadViewPage): a la escala grande, como en el juego desde la 1.3.25."""
    s = Sim()
    s.status(title)
    with Local(s):
        s.view(*args, **kw)
    s.save(OUT + name + '.png')
    NAMES.append(name)


APPS = [('musica', 'MÚSICA'), ('oficios', 'OFICIOS'), ('misiones', 'MISIONES'), ('pase', 'TF PASS'), ('gachapon', 'GACHAPÓN'), ('cazas', 'CAZAS'),
        ('recompensas', 'RECOMPENSAS'), ('tienda', 'TIENDA'), ('gts', 'GTS'), ('monedero', 'MONEDERO'), ('viajes', 'VIAJES'),
        ('hogares', 'HOGARES'), ('kits', 'KITS'), ('protecciones', 'PROTECCIÓN'), ('clanes', 'CLANES'),
        ('jugadores', 'JUGADORES'), ('comunidad', 'COMUNIDAD'), ('camara', 'CÁMARA'), ('ranking', 'RANKING'),
        ('armario', 'ARMARIO'), ('efectos', 'EFECTOS'), ('rango', 'MI RANGO'), ('web', 'WEB')]
ADMIN = [('admin', 'APPS'), ('tienda', 'TIENDA'), ('kits', 'KITS'), ('recompensas', 'RECOMPENSAS'), ('viajes', 'VIAJES'), ('gts', 'GTS'),
         ('comunidad', 'COMUNIDAD'), ('oficios', 'OFICIOS'), ('admin', 'AJUSTES')]

s = Sim()
s.status('TF PAD', back=False, music=True)
s.home(APPS, hover='hogares', glint='tienda')
s.save(OUT + 'p_inicio.png')
NAMES.append('p_inicio')

s = Sim()
s.status('PAD ADMIN', back=False, title_color=rgb(0xFFD36A), greens=False)
s.home(ADMIN)
s.save(OUT + 'p_admin.png')
NAMES.append('p_admin')

page('p_oficios', 'OFICIOS', [], '', ['Trabajas de Minero · cobras 42 monedas en el próximo pago.'], [], cards=[
    ('wheat', 'Granjero', 0x6FB43C, 'Nivel 3', False, 1, {'prog': 0.5}),
    ('iron_pickaxe', 'Minero', 0x8C9BB4, 'Nivel 12', True, 1, {'prog': 0.28, 'badge': '+2'}),
    ('iron_axe', 'Leñador', 0xA0703C, 'Nivel 5', False, 1, {'prog': 0.8}),
    ('iron_shovel', 'Excavador', 0xC8A064, 'Sin empezar', False, 1, {'prog': 0, 'tone': 3}),
    ('fishing_rod', 'Pescador', 0x3C8CD2, 'Sin empezar', False, 1, {'prog': 0, 'tone': 3}),
    ('bow', 'Cazador', 0xC83C3C, 'Nivel 1', False, 1, {'prog': 0.1}),
    ('brewing_stand', 'Alquimista', 0x9A5CF0, 'Sin empezar', False, 1, {'prog': 0, 'tone': 3}),
    ('anvil', 'Herrero', 0x707880, 'Nivel máximo', False, 1, {'prog': 1, 'tone': 1}),
])
page('p_oficio', 'OFICIOS', [('m', 'MISIONES (1)'), ('a', 'CÓMO SE GANA'), ('r', 'PREMIOS')], 'm', [], [
    ('coal', 'Carbón para la forja', T, ['Saca 32 menas de carbón: 32/32 · cada día'], 1.0, '+¤120', ('COBRAR', G), None, True),
    ('iron_ore', 'Vena de hierro', T, ['Saca 16 menas de hierro: 9/16 · cada día'], 0.56, '+¤200', None, None),
], hero=('iron_pickaxe', 'Minero · tu oficio', 0x8C9BB4, ['Pica piedra y minerales bajo tierra.', 'Nivel 12 de 50 · 340/1200 xp.'], 0.28, '', None, None),
     footer=[('ATRÁS', B), ('DEJAR', R)])
page('p_tienda', 'TIENDA', [('', 'COMPRAR'), ('v', 'VENDER')], '', [], [
    ('diamond', 'Minerales', 0x3CB4E6, ['Diamante, netherita, lapislázuli'], -1, '3 objetos', None, None),
    ('bread', 'Comida', 0xD2A050, ['Pan, filete, manzana dorada'], -1, '8 objetos', None, None),
])
page('p_tienda_cat', 'TIENDA', [], '', ['Minerales'], [], cards=[
    ('diamond', 'Diamante', 0xF6B628, '¤950', False), ('netherite_scrap', 'Chatarra de netherita', 0xF6B628, '¤6.000', False),
    ('lapis_lazuli', 'Lapislázuli', 0x40C850, '+¤12', False), ('emerald', 'Esmeralda', 0xF6B628, '¤1.200', False),
    ('gold_ingot', 'Lingote de oro', 0xF6B628, '¤300', False),
], footer=[('ATRÁS', B)])
page('p_gts', 'GTS', [('', 'MERCADO'), ('v', 'VENDER'), ('m', 'MIS VENTAS (2)'), ('r', 'RECOGER')], '', [], [
    ('diamond_sword', 'Espada de diamante', T, ['Lo vende Steve · quedan 3 días.'], -1, '¤5.000', ('COMPRAR', GO), None),
    ('elytra', 'Élitros', T, ['Lo vende Alex · quedan 5 días.'], -1, '¤120K', ('SIN SALDO', GR, False), None),
    ('trident', 'Tridente', T, ['Tuyo · quedan 2 días.'], -1, '¤40.000', ('RETIRAR', R), None, True),
    ('totem_of_undying', 'Tótem de la inmortalidad', T, ['Lo vende Notch · quedan 18 h.'], -1, '¤25.000', ('COMPRAR', GO), None),
    ('enchanted_book', 'Libro encantado', T, ['Lo vende Steve · quedan 6 días.'], -1, '¤2.500', ('COMPRAR', GO), None),
], input_=('BUSCAR OBJETO O JUGADOR', 'BUSCAR', ''))
page('p_gts_item', 'GTS', [], '', [], [
    ('', 'Lo que dice el objeto', T, ['Filo V', 'Irrompible III', 'Reparación'], -1, '', None, None),
], hero=('diamond_sword', 'Espada de diamante', 0xF6B628, ['Lo vende Steve · quedan 3 días.', 'Tienes 12.450 monedas.'], -1,
         '¤5.000', None, None), footer=[('ATRÁS', B), ('COMPRAR', GO)])
page('p_gts_item_solo', 'GTS', [], '', [], [],
     hero=('totem_of_undying', 'Tótem de la inmortalidad', 0xF6B628, ['Lo vende Notch · quedan 18 h.', 'Tienes 30.000 monedas.'], -1,
           '¤25.000', None, None), footer=[('ATRÁS', B), ('COMPRAR', GO)])
page('p_tienda_comprar', 'TIENDA', [('b', 'COMPRAR'), ('s', 'VENDER')], 'b', [], [], cards=[
    ('diamond', '1 unidad', 0xF6B628, '¤950', False, 1),
    ('diamond', '8 uds.', 0xF6B628, '¤7.600', False, 8),
    ('diamond', '32 uds.', 0xF6B628, 'Faltan 18K', False, 32, {'off': True}),
    ('diamond', '64 uds.', 0xF6B628, 'Faltan 48K', False, 64, {'off': True}),
], hero=('diamond', 'Diamante', 0xF6B628, ['Por unidad · llevas 5.', 'Tienes 12.450 monedas.'], -1, '¤950', None, None),
     input_=('OTRA CANTIDAD (LOTES)', 'COMPRAR', ''), footer=[('ATRÁS', B)])
page('p_viajes', 'VIAJES', [], '', [], [], cards=[
    ('compass_00', 'Spawn', 0x3496FA, 'Mundo normal', False), ('red_bed', 'Tu cama', 0xE83446, 'Mundo normal', False),
    ('filled_map', 'Mercado', 0xF6B628, 'Mundo normal', False), ('ender_pearl', 'Arena PvP', 0x9A5CF0, 'Warp', False),
    ('ender_pearl', 'Nether hub', 0x9A5CF0, 'Warp', False), ('spyglass', 'Explorar', 0x40C850, 'Al azar', False),
    ('oak_door', 'Tus hogares', 0xC27A10, 'Abrir', False),
])
page('p_kits', 'KITS', [], '', ['1 listo para reclamar.'], [], cards=[
    ('bundle', 'Kit inicial', 0xAABAD2, 'Reclamado', False, 1, {'tone': 3}), ('bread', 'Constructor', 0x40C850, 'Listo', True, 1, {'tone': 2}),
    ('iron_sword', 'Semanal', 0xF6B628, 'en 3 d 4 h', False), ('diamond', 'VIP', 0xF6B628, 'en 5 h', False),
])
page('p_recompensas', 'RECOMPENSAS', [('diaria', 'DIARIA'), ('otras', 'OTRAS (1)')], 'diaria',
     ['Día 3 de 7: ¡reclámala! Si te saltas un día, vuelves al día 1.'], [], cards=[
         ('bread', 'Día 1 · 25', 0xAABAD2, 'Reclamada', False, 8, {'tone': 3}), ('torch', 'Día 2 · 25', 0xAABAD2, 'Reclamada', False, 16, {'tone': 3}),
         ('iron_ingot', 'Día 3 · 50', 0x40C850, '¡Hoy!', True, 4, {'tone': 2}), ('cooked_beef', 'Día 4 · 50', 0xF6B628, 'Mañana', False, 12),
         ('golden_carrot', 'Día 5 · 75', 0xF6B628, '', False, 8), ('experience_bottle', 'Día 6 · 100', 0xF6B628, '', False, 8),
         ('diamond', 'Día 7 · 200', 0xF6B628, '', False, 2),
     ], footer=[('RECLAMAR DÍA 3', G)])
page('a_recompensas', 'RECOMPENSAS', [('cal', 'CALENDARIO'), ('otras', 'OTRAS')], 'cal',
     ['El calendario diario: cada día se reclama el siguiente de la racha. Pulsa un día para cambiarlo.'], [
         ('clock_00', 'Calendario', T, ['Encendido.'], -1, '', ('SÍ', G), None),
         ('compass_00', 'Si alguien se salta un día', T, ['Vuelve al día 1.'], -1, '', ('CAMBIAR', B), None),
     ], cards=[
         ('bread', 'Día 1', 0xF6B628, '1 obj. · 25', False, 8), ('torch', 'Día 2', 0xF6B628, '1 obj. · 25', False, 16),
         ('iron_ingot', 'Día 3', 0xF6B628, '1 obj. · 50', False, 4), ('cooked_beef', 'Día 4', 0xF6B628, '1 obj. · 50', False, 12),
         ('golden_carrot', 'Día 5', 0xF6B628, '1 obj. · 75', False, 8), ('experience_bottle', 'Día 6', 0xF6B628, '1 obj. · 100', False, 8),
         ('diamond', 'Día 7', 0xF6B628, '1 obj. · 200', False, 2),
     ], footer=[('AÑADIR DÍA', G), ('QUITAR EL ÚLTIMO', R)])
page('a_selector', 'KITS', [('i', 'TU INVENTARIO'), ('t', 'TODOS LOS OBJETOS')], 't',
     ['Añadir a Kit inicial (de 16 en 16). 1.312 objetos · página 1 de 14.'], [], cells=[
         (n, '', T, False) for n in ('stone', 'granite', 'diorite', 'andesite', 'grass_block', 'dirt', 'cobblestone', 'oak_planks',
                                     'spruce_planks', 'birch_planks', 'oak_sapling', 'sand', 'gravel', 'gold_ore', 'iron_ore',
                                     'coal_ore', 'oak_log', 'spruce_log', 'birch_log', 'glass', 'lapis_ore', 'sandstone',
                                     'white_wool', 'orange_wool', 'gold_block', 'iron_block', 'bricks', 'tnt', 'bookshelf',
                                     'obsidian', 'torch', 'chest', 'diamond_ore', 'diamond_block', 'crafting_table', 'furnace')
     ], input_=('BUSCAR (NOMBRE O ID)', 'BUSCAR', ''), footer=[('»', B), ('×16', GO), ('ATRÁS', B)])
page('p_kit', 'KITS', [], '', [], [], cards=[
    ('bread', 'Pan', 0x3496FA, '', False, 16), ('cooked_beef', 'Filete', 0x3496FA, '', False, 8),
    ('torch', 'Antorcha', 0x3496FA, '', False, 32), ('tf:fantastic_coin', '200 monedas', 0xF6B628, '', False),
], hero=('bread', 'Constructor', 0x40C850, ['Para empezar a construir.', 'Cada 12 h · listo para reclamar.'], -1, '', None, None),
     footer=[('ATRÁS', B), ('RECLAMAR', G)])
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
    ('coal', 'Minero de carbón', T, ['Saca 24 menas de carbón  ·  24/24  ·  +100 XP de pase'], 1.0, '+¤80', ('RECLAMAR', G), None),
    ('rotten_flesh', 'Noche de zombis', T, ['Derrota 15 zombis  ·  9/15  ·  +100 XP de pase'], 0.6, '+¤90', None, None),
], footer=[('RECLAMAR TODO', G)])
page('a_tienda_item', 'TIENDA', [], '', ['Minerales · Diamante'], [
    ('gold_ingot', 'Precio de compra', T, ['Lo que paga el jugador por lote. 0 = no se vende.'], -1, '950', ('EDITAR', B), None, True),
    ('emerald', 'Precio de venta', T, ['Lo que cobra el jugador. 0 = la tienda no lo compra.'], -1, '400', ('EDITAR', B), None),
    ('chest', 'Cantidad por lote', T, ['Objetos que van en cada lote.'], -1, '1', ('EDITAR', B), None),
], input_=('NUEVO VALOR', 'GUARDAR', '1200'), footer=[('ATRÁS', B)])
# Protección → AJUSTES (PadClaims.settings, 1.3.34): cada opción es una pregunta; SÍ y NO, el que vale ahora en verde
ZT = [('a', 'AJUSTES'), ('m', 'MIEMBROS (2)'), ('b', 'BANEOS'), ('p', 'PARTÍCULAS'), ('x', 'MÁS')]
page('p_proteccion', 'PROTECCIÓN', ZT, 'a', ['Zona 100x100 · Mundo normal · 120, 64, -340'], [
    ('bricks', '¿Pueden construir?', T, [], -1, '', ('SÍ', GR), ('NO', G)),
    ('iron_pickaxe', '¿Pueden romper bloques?', T, [], -1, '', ('SÍ', GR), ('NO', G)),
    ('chest', '¿Pueden abrir cofres?', T, ['Y barriles.'], -1, '', ('SÍ', GR), ('NO', G)),
    ('ender_pearl', '¿Pueden entrar con perlas?', T, ['Lanzando perlas de ender.'], -1, '', ('SÍ', G), ('NO', GR)),
    ('tnt', '¿Las explosiones rompen?', T, ['TNT y creepers.'], -1, '', ('SÍ', G), ('NO', GR)),
    ('blaze_powder', '¿Arden los monstruos?', T, ['Los que entran en la zona.'], -1, '', ('SÍ', G), ('NO', GR)),
    ('bell', '¿Avisarte si entra alguien?', T, [], -1, '', ('SÍ', GR), ('NO', G)),
], footer=[('ATRÁS', B)])
page('a_apps', 'APPS', [], '', ['Las que apagues no salen en el pad de los jugadores.'], [
    ('iron_pickaxe', 'Oficios', T, ['Activa'], -1, '', ('SÍ', G), None),
    ('writable_book', 'Misiones', T, ['Activa'], -1, '', ('SÍ', G), None),
    ('crossbow', 'Cazas', M, ['Apagada'], -1, '', ('NO', GR), None),
])

# Comunidad (PadCommunityPage): pestañas, + FOTO y las publicaciones una debajo de otra; REACCIONAR abre el selector
photo = Image.open(os.path.join(ROOT, 'src/main/resources/assets/tfclient/textures/gui/menu_background.png')).convert('RGBA')
REACTS = ('risa', 'wow', 'triste', 'fuego', 'top')


def comunidad(out, picker):
    s = Sim()
    s.status('COMUNIDAD')
    _local = Local(s)
    _local.__enter__()
    X, Y, W, H = s.X, s.Y, s.W, s.H
    top, bottom = Y + 14, Y + H
    s.panel(X, top, W, bottom - top)
    x, chosen = X + 4, None
    for key, label in (('recientes', 'RECIENTES'), ('populares', 'POPULARES'), ('mias', 'MÍAS')):
        w = s.pwidth(label) + 14
        if key == 'recientes':
            chosen = (x, w, label)
        else:
            s.tab(x, Y, w, label, 0)
        x += w + 2
    s.tab(chosen[0], Y, chosen[1], chosen[2], 2)
    fw = s.bw('+ FOTO')
    s.button(X + W - fw - 4, Y - 3, fw, '+ FOTO', GO)
    pw = max(128, min(256, (W - 20) // 2)) // 16 * 16
    ph = pw * 9 // 16
    ch = ph + 14
    s.scissor(X + 2, top + 4, W - 4, bottom - top - 8)
    posts = [('Pewez777', 'hace 12 min', 'Atardecer desde la torre del spawn', 18, True, [3, 1, 0, 5, 2], 3),
             ('Steve', 'hace 2 h', '', 4, False, [0, 0, 0, 0, 0], -1)]
    cy = top + 5
    pick = None
    for name, ago, cap, likes, liked, reacts, mine_r in posts:
        x, y, w, h = X + 5, cy, W - 16, ch - 4
        s.ucard(x, y, w, h, 0)
        px, py = x + 5, y + 5
        s.box(px - 2, py - 2, pw + 4, ph + 4, (24, 38, 92))
        s.box(px - 1, py - 1, pw + 2, ph + 2, (192, 120, 24))
        s.paste(photo.resize((pw, ph)), px, py)
        cx = px + pw + 10
        cw = x + w - 6 - cx
        s.mtext(name, cx, py + 1, (194, 122, 16))
        s.mtext(ago, cx, py + 12, (74, 102, 148))
        if cap:
            s.wrap_ellipsis('«' + cap + '»', cx, py + 26, cw, (24, 38, 92), 3)
        # REACCIONAR y solo las reacciones que ya tiene (la tuya en oro)
        ey, ex = py + ph - 32, cx
        bw = s.bw('REACCIONAR')
        open_ = picker and pick is None
        s.button(ex, ey - 1, bw, 'REACCIONAR', B, hover=open_)
        if open_:
            pick = (ex, ey - 1, mine_r)
        ex += bw + 4
        for k, rname in enumerate(REACTS):
            if not reacts[k]:
                continue
            cnt = str(reacts[k])
            rw = 15 + s.mwidth(cnt) + 2
            if ex + rw > cx + cw:
                break
            if k == mine_r:
                s.box(ex, ey, rw, 15, rgb(0xE0A030))
                s.fill(ex + 1, ey + 1, ex + rw - 1, ey + 14, rgb(0xFFF3C8))
            s.paste_fit(s.img('emo_' + rname), ex + 2, ey + 2, 11, 11)
            s.mtext(cnt, ex + 15, ey + 4, (24, 38, 92))
            ex += rw + 2
        ly = py + ph - 15
        s.blit('heart' if liked else 'heart_off', cx, ly)
        s.mtext(f'{likes} likes', cx + 16, ly + 2, (24, 38, 92))
        bw = s.bw('DENUNCIAR')
        s.button(x + w - 6 - bw, ly - 1, bw, 'DENUNCIAR', B)
        cy += ch
    s.no_scissor()
    if pick:  # el selector, encima del botón (PadCommunityPage.drawPicker)
        cell, emo = 20, 14
        pw_, ph_ = len(REACTS) * cell + 6, cell + 6
        bx, by, mine_r = pick
        x0 = max(X + 4, min(bx, X + W - pw_ - 4))
        y0 = by - ph_ - 2
        if y0 < top + 2:
            y0 = by + 18
        s.fill(x0 + 2, y0 + 2, x0 + pw_ + 2, y0 + ph_ + 2, (24, 38, 92, 85))
        s.box(x0, y0, pw_, ph_, (24, 38, 92))
        s.box(x0 + 1, y0 + 1, pw_ - 2, ph_ - 2, (192, 120, 24))
        s.fill(x0 + 2, y0 + 2, x0 + pw_ - 2, y0 + ph_ - 2, (255, 255, 255))
        hover = 1
        for k, rname in enumerate(REACTS):
            ex, ey = x0 + 3 + k * cell, y0 + 3
            if k == mine_r or k == hover:
                s.box(ex, ey, cell, cell, rgb(0xE0A030 if k == mine_r else 0x9DBCE0))
                s.fill(ex + 1, ey + 1, ex + cell - 1, ey + cell - 1, rgb(0xFFF3C8 if k == mine_r else 0xEAF4FD))
            sz = emo + 2 if k == hover else emo
            o = (cell - sz) // 2
            s.paste_fit(s.img('emo_' + rname), ex + o, ey + o, sz, sz)
    _local.__exit__(None, None, None)
    s.save(OUT + out + '.png')
    NAMES.append(out)


comunidad('p_comunidad', False)
comunidad('p_comunidad_reaccionar', True)


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
LAV, GOLDC, GL, GREENC = rgb(0xB8C2F0), rgb(0xF6B628), rgb(0xFFEC96), rgb(0x7CF0B0)


def music_page(name, tracks, playing=None, job=None, empty=False, hover_row=-1, sure=False, sel_list=0, vol_hover=False):
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
    # pestañas: TODAS, las playlists y + (PadMusicPage.drawListTabs)
    tx, chosen = lx + 4, None
    for i, label in enumerate(['TODAS', 'FAVORITAS', 'CAMINO']):
        w = s.pwidth(label) + 12 + (9 if i == sel_list and i > 0 else 0)
        if i == sel_list:
            chosen = (tx, w, label)
        else:
            s.tab(tx, Y, w, label, 0)
        tx += w + 2
    if sel_list == 0:
        s.tab(chosen[0], Y, chosen[1], chosen[2], 2)
    else:  # 1.3.38: el nombre a la izquierda y la X detrás, con su hueco
        s.tab(chosen[0], Y, chosen[1], '', 2)
        s.ptext(chosen[2], chosen[0] + 6, Y + 4, NAVY, False)
        bx = chosen[0] + chosen[1] - 11
        for gy, row in enumerate(CROSS):
            for gx, ch in enumerate(row):
                if ch == '#':
                    s.fill(bx + gx, Y + 6 + gy, bx + gx + 1, Y + 7 + gy, rgb(0xC8323C))
    s.tab(tx, Y, s.pwidth('+') + 12, '+', 0)
    s.scissor(lx + 2, top + 4, lw - 4, bottom - top - 8)
    ry = top + 4
    def small_btn(bx, by, glyph, col):
        s.box(bx, by, 14, 14, col + (64,))
        s.box(bx + 1, by + 1, 12, 12, (255, 255, 255, 240))
        for gy, row in enumerate(glyph):
            for gx, ch in enumerate(row):
                if ch == '#':
                    s.fill(bx + 7 - len(row) // 2 + gx, by + 7 - len(glyph) // 2 + gy, bx + 8 - len(row) // 2 + gx,
                           by + 8 - len(glyph) // 2 + gy, col)

    for ti, tr in enumerate(tracks):
        x0, w0 = lx + 5, lw - 14
        cur = playing and tr['title'] == playing['title']
        hv = ti == hover_row
        if cur or hv:
            s.ucard(x0, ry, w0, 29, 2 if cur else 1)
        else:
            s.fill(x0 + 3, ry + 29, x0 + w0 - 3, ry + 30, rgb(0xC8E4F8))
        if tr.get('cover'):
            s.paste_fit(Image.open(tr['cover']).convert('RGBA'), x0 + 5, ry + 3, 24, 24)
        else:
            grad(s, x0 + 5, ry + 3, 24, 24, tr.get('color', rgb(0x5A6AD0)), rgb(0x1D2550))
            with s.big_at(x0 + 5 + 12 - 8, ry + 3 + 4, px=0.5 * s.cs):
                s.blit('icon_musica', 0, 0)
        INK = (24, 38, 92)
        s.fill(x0 + 4, ry + 2, x0 + 30, ry + 3, INK); s.fill(x0 + 4, ry + 27, x0 + 30, ry + 28, INK)
        s.fill(x0 + 4, ry + 2, x0 + 5, ry + 28, INK); s.fill(x0 + 29, ry + 2, x0 + 30, ry + 28, INK)
        tx = x0 + 35
        dur = tr['dur']
        right = x0 + w0 - 6
        if hv:  # 1.3.38: de derecha a izquierda, cada uno en su sitio: [X o ¿BORRAR?] [+] duración / [−] duración
            by = ry + (29 - 14) // 2
            if sel_list > 0:
                small_btn(right - 14, by, [".....", ".....", "#####", ".....", "....."], rgb(0x3496FA))
                right -= 19
            else:
                if sure:
                    dw = s.bw('¿BORRAR?')
                    s.button(right - dw, ry + (29 - 16) // 2, dw, '¿BORRAR?', 2)
                    right -= dw + 5
                else:
                    small_btn(right - 14, by, CROSS, rgb(0xE83446))
                    right -= 18
                small_btn(right - 14, by, ["..#..", "..#..", "#####", "..#..", "..#.."], rgb(0x1E9E46))
                right -= 19
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
music_page('p_musica_lista', lib, playing=song, hover_row=1, sel_list=1)


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
    tw = s.pwidth(tab) + 14
    s.box(x + 4, tab_y, tw, 15, NAVY)
    grad(s, x + 5, tab_y + 1, tw - 2, 15, rgb(0x323C80), rgb(0x262E66))
    s.fill(x + 6, tab_y + 1, x + 4 + tw - 2, tab_y + 3, rgb(0xF6B628))
    s.fill(x + 6, tab_y + 1, x + 4 + tw - 2, tab_y + 2, rgb(0xFFE58A))
    s.ptext(tab, x + 4 + tw // 2 - s.pwidth(tab) // 2, tab_y + 4, rgb(0xFFE680), False)
    h = bottom - top
    s.box(x, top, w, h, NAVY)
    grad(s, x + 1, top + 1, w - 2, h - 2, rgb(0x262E66), rgb(0x141A3C))
    s.fill(x + 2, top + 1, x + w - 2, top + 2, rgb(0x4A56A0))
    for (a, b, c, d) in ((x + 2, top + 2, x + w - 2, top + 3), (x + 2, top + h - 3, x + w - 2, top + h - 2),
                         (x + 2, top + 3, x + 3, top + h - 3), (x + w - 3, top + 3, x + w - 2, top + h - 3)):
        s.fill(a, b, c, d, (246, 182, 40, 85))
    s.fill(x + 5, top, x + 4 + tw - 1, top + 2, rgb(0x262E66))


def price_button(s, x, y, label, price, style, enabled=True):
    n = str(price)
    w = s.pwidth(label) + 6 + 10 + s.pwidth(n) + 14
    s.button(x, y, w, '', style, enabled)
    col = (255, 255, 255) if enabled else rgb(0x8C9AC4)
    tx = x + 7
    s.ptext(label, tx, y + 2, col)
    tx += s.pwidth(label) + 6
    s.fill(tx - 3, y + 4, tx - 2, y + 11, (255, 255, 255, 102))
    s.blit('coin_green_s', tx, y + 4)
    s.ptext(n, tx + 10, y + 2, col)
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
    s = Sim(fbw=fbw, fbh=fbh)
    s.status('GACHAPÓN')
    loc = Local(s)
    loc.__enter__()
    X, Y, W, H = s.X, s.Y, s.W, s.H
    lw = max(210, min(270, round(W * 0.56)))
    x, w, top, bottom = X, lw, Y + 14, Y + H
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
    s.panel(lx, top, lw2, bottom - top)
    tx, chosen = lx + 4, None
    for key, label in (('premios', 'PREMIOS'), ('historial', 'HISTORIAL')):
        tw = s.pwidth(label) + 14
        if key == tab:
            chosen = (tx, tw, label)
        else:
            s.tab(tx, Y, tw, label, 0)
        tx += tw + 2
    s.tab(chosen[0], Y, chosen[1], chosen[2], 2)
    rows = PREMIOS if tab == 'premios' else [dict(r, t=m) for r, m in zip(result or PREMIOS[3:9], ('ahora', 'ahora', 'hace 3 min', 'hace 2 h', 'hace 1 d', 'hace 2 d'))]
    s.scissor(lx + 2, top + 4, lw2 - 4, bottom - top - 8)
    ry2 = top + 4
    for p in rows:
        x0, w0 = lx + 5, lw2 - 14
        col = rgb(RAR[p['r']][1])
        s.fill(x0 + 3, ry2 + 23, x0 + w0 - 3, ry2 + 24, rgb(0xC8E4F8))
        s.slot(x0 + 1, ry2 + 2, 20, 20, col)
        s.item(item_of(p), x0 + 3, ry2 + 4)
        if p['tipo'] == 'objeto' and p['n'] > 1:
            s.count(p['n'], x0 + 3, ry2 + 4)
        right = (f"{p['p']:.2f}".rstrip('0').rstrip('.').replace('.', ',') + ' %') if tab == 'premios' else p['t']
        rw2 = s.mwidth(right)
        s.mtext(right, x0 + w0 - 4 - rw2, ry2 + 8, rgb(0x4A6694))
        room = w0 - 26 - rw2 - 10
        s.mtext(s.fitend(prize_name(p), room), x0 + 26, ry2 + 3, NAVY)
        s.mtext(RAR[p['r']][0], x0 + 26, ry2 + 13, darken(RAR[p['r']][1], 0.7))
        ry2 += 24
    s.no_scissor()
    content = len(rows) * 24
    s.scrollbar(lx + lw2 - 7, top + 5, bottom - top - 10, bottom - top - 8, content)
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
    s.fill(x + 2, y + 1, x + w - 2, y + 2, rgb(0x5FD39A))
    s.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, rgb(0xC07818))
    s.corners(x, y, w, h)
    sx, sy, ss = x + 8, y + 6, 38
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
        s.button(right - bw, y + h - 22, bw, label, 3)
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
    # pista
    top = Y + HERO + 4
    s.panel(X, top, W, track_h)
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
            s.fill(nx0 + 3, sy2, nx0 + NODE - 3, sy2 + 13, rgb(0x1E9E46))
            s.fill(nx0 + 3, sy2, nx0 + NODE - 3, sy2 + 1, rgb(0x8CF0A8))
            s.ptext('COBRAR', nx0 + NODE // 2 - s.pwidth('COBRAR') // 2, sy2 + 1, (255, 255, 255))
        elif state == 2:
            for ax, ay in ((6, 0), (5, 1), (6, 1), (4, 2), (5, 2), (0, 2), (0, 3), (1, 3), (3, 3), (4, 3), (1, 4), (2, 4), (3, 4), (2, 5)):
                s.put(nx0 + NODE // 2 - 4 + ax, sy2 + 4 + ay, rgb(0xC27A10))
        else:
            lx0, ly0, c = nx0 + NODE // 2 - 3, sy2 + 3, rgb(0x8EA4C4)
            s.fill(lx0 + 2, ly0, lx0 + 5, ly0 + 1, c); s.fill(lx0 + 1, ly0 + 1, lx0 + 2, ly0 + 4, c)
            s.fill(lx0 + 5, ly0 + 1, lx0 + 6, ly0 + 4, c); s.fill(lx0, ly0 + 3, lx0 + 7, ly0 + 8, c)
            s.fill(lx0 + 3, ly0 + 5, lx0 + 4, ly0 + 7, (255, 255, 255))
        if n == lv:
            yw = s.pwidth('TÚ') + 6
            yx, yy = nx0 + NODE - yw + 1, cy - 5
            s.box(yx, yy, yw, 9, NAVY)
            s.fill(yx + 1, yy + 1, yx + yw - 1, yy + 8, rgb(0xF6B628))
            s.ptext('TÚ', yx + 3, yy - 1, rgb(0x5A3A00), False)
    s.no_scissor()
    for i in range(10):
        a = int(110 * (1 - i / 10))
        if scroll > 1:
            s.fill(trx + i, top + 2, trx + i + 1, top + track_h - 2, (255, 255, 255, a))
        if scroll < max_scroll - 1:
            s.fill(trx + trw - 1 - i, top + 2, trx + trw - i, top + track_h - 2, (255, 255, 255, a))
    # abajo
    btop = top + track_h + 4
    s.panel(X, btop, W, bottom_h)
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
