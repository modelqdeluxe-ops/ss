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


APPS = [('musica', 'MÚSICA'), ('oficios', 'OFICIOS'), ('misiones', 'MISIONES'), ('cazas', 'CAZAS'), ('tienda', 'TIENDA'), ('gts', 'GTS'),
        ('monedero', 'MONEDERO'), ('viajes', 'VIAJES'), ('hogares', 'HOGARES'), ('kits', 'KITS'),
        ('protecciones', 'PROTECCIÓN'), ('clanes', 'CLANES'), ('jugadores', 'JUGADORES'), ('comunidad', 'COMUNIDAD'),
        ('camara', 'CÁMARA'), ('ranking', 'RANKING'), ('armario', 'ARMARIO'), ('efectos', 'EFECTOS'), ('rango', 'MI RANGO'),
        ('ayuda', 'AYUDA')]
ADMIN = [('admin', 'APPS'), ('tienda', 'TIENDA'), ('kits', 'KITS'), ('viajes', 'VIAJES'), ('gts', 'GTS'),
         ('comunidad', 'COMUNIDAD'), ('oficios', 'OFICIOS'), ('admin', 'AJUSTES')]

s = Sim()
s.status('TF PAD', back=False, music=True)
s.home(APPS, hover='hogares', glint='tienda')
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
_local = Local(s)
_local.__enter__()
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
_local.__exit__(None, None, None)
s.save(OUT + 'p_comunidad.png')
NAMES.append('p_comunidad')


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


def music_page(name, tracks, playing=None, job=None, empty=False):
    s = Sim()
    s.status('MÚSICA', music=playing is not None)
    local = Local(s)
    local.__enter__()
    X, Y, W, H = s.X, s.Y, s.W, s.H
    leftW = max(140, min(240, round(W * 0.44)))
    barH = max(3, s.bigu(3))
    below = 4 + s.bigu(9) + 2 + 13 + barH + 3 + 10 + s.bigu(22) + 4
    c = max(32, min(round((leftW - 16 - s.bigu(16)) / 1.3), H - 8 - below))
    cx, cy = X + 8, Y + 8
    # tarjeta
    s.box(X, Y, leftW, H, NAVY)
    grad(s, X + 1, Y + 1, leftW - 2, H - 2, rgb(0x262E66), rgb(0x141A3C))
    s.fill(X + 2, Y + 1, X + leftW - 2, Y + 2, rgb(0x4A56A0))
    for (a, b, cc, d) in ((X + 2, Y + 2, X + leftW - 2, Y + 3), (X + 2, Y + H - 3, X + leftW - 2, Y + H - 2),
                          (X + 2, Y + 2, X + 3, Y + H - 2), (X + leftW - 3, Y + 2, X + leftW - 2, Y + H - 2)):
        s.fill(a, b, cc, d, (246, 182, 40, 85))
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
    total = playD + small * 4 + gap * 4
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
    # lista
    lx, lw = X + leftW + 6, W - leftW - 6
    with s.big_at(lx + 2, Y + 1):
        s.ptext('TU MÚSICA', 0, 0)
    cnt = f"{len(tracks)} {'canción' if len(tracks) == 1 else 'canciones'}"
    s.mtext(cnt, lx + lw - s.mwidth(cnt) - 2, Y + 3, NAVY)
    top = Y + s.bigu(13) + 2
    bottom = Y + H - 19 - (24 if job else 0)
    s.panel(lx, top, lw, bottom - top)
    s.scissor(lx + 1, top + 2, lw - 2, bottom - top - 4)
    ry = top + 2
    for tr in tracks:
        x0, w0 = lx + 3, lw - 10
        cur = playing and tr['title'] == playing['title']
        if cur:
            s.box(x0, ry, w0, 29, rgb(0xFFE9A8)); s.fill(x0, ry + 2, x0 + 2, ry + 27, GOLDC)
        s.fill(x0 + 2, ry + 29, x0 + w0 - 2, ry + 30, rgb(0xC8E4F8))
        if tr.get('cover'):
            s.paste_fit(Image.open(tr['cover']).convert('RGBA'), x0 + 5, ry + 3, 24, 24)
        else:
            grad(s, x0 + 5, ry + 3, 24, 24, tr.get('color', rgb(0x5A6AD0)), rgb(0x1D2550))
            with s.big_at(x0 + 5 + 12 - 8, ry + 3 + 4, px=0.5 * s.cs):
                s.blit('icon_musica', 0, 0)
        s.fill(x0 + 4, ry + 2, x0 + 30, ry + 3, NAVY); s.fill(x0 + 4, ry + 27, x0 + 30, ry + 28, NAVY)
        s.fill(x0 + 4, ry + 2, x0 + 5, ry + 28, NAVY); s.fill(x0 + 29, ry + 2, x0 + 30, ry + 28, NAVY)
        tx = x0 + 35
        dur = tr['dur']
        right = x0 + w0 - 6
        s.mtext(dur, right - s.mwidth(dur), ry + 10, rgb(0x4A6694))
        right -= s.mwidth(dur) + 6
        ttx = tx
        if cur:
            for i, h in enumerate((5, 8, 3)):
                s.fill(tx + i * 3, ry + 12 - h, tx + i * 3 + 2, ry + 12, rgb(0xC27A10))
            ttx += 12
        s.mtext(s.fitend(tr['title'], right - ttx), ttx, ry + 5, rgb(0x7A4A08) if cur else NAVY)
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
            s.mtext(l, lx + (lw - s.mwidth(l)) // 2, mid + 10 + i * 11, rgb(0x4A6694) if i == 2 else NAVY)
    if job:
        jy = bottom + 3
        s.box(lx, jy, lw, 20, NAVY); s.box(lx + 1, jy + 1, lw - 2, 18, rgb(0xFFF6D6))
        s.mtext(job, lx + 15, jy + 3, rgb(0x7A4A08))
        s.fill(lx + 4, jy + 13, lx + lw - 4, jy + 16, rgb(0xE8D8A8)); s.fill(lx + 4, jy + 13, lx + 4 + int((lw - 8) * 0.62), jy + 16, GOLDC)
    fy = Y + H - 16
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
