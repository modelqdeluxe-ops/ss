"""Iconos de las apps nuevas del pad (1.3.22). Los que tienen objeto en Minecraft se calcan de la estructura de su
versión de 32x32 (Faithful 32x) con calco.py; Clanes, Ranking y Cámara se dibujan a mano con las mismas reglas
(luz arriba a la izquierda, 5 tonos por material, contorno azul marino).

Las referencias van en tools/pad/ref/ (PNG de 32x32).
"""
import math
import os

from calco import calco, hls
from kit import *

REF = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'ref')

PAGE = [(255, 255, 255), (240, 246, 252), (214, 226, 240), (170, 186, 210), (120, 136, 170)]
PAPER = [(255, 250, 230), (250, 236, 196), (236, 214, 160), (200, 168, 112), (150, 118, 72)]
TAG = [(255, 246, 214), (250, 226, 160), (236, 196, 116), (200, 150, 76), (150, 104, 52)]
SACK = [(255, 214, 150), (240, 170, 96), (214, 128, 64), (170, 90, 44), (120, 60, 34)]
LEATHER = [(232, 170, 110), (196, 124, 70), (156, 90, 48), (116, 62, 36), (82, 42, 28)]


def ref(name):
    return os.path.join(REF, name + '.png')


def deg(r, g, b):
    h, l, s = hls((r, g, b))
    return h * 360, l, s


# ---------------------------------------------------------------------------------------------------------------------
# Calcados
# ---------------------------------------------------------------------------------------------------------------------

def misiones():
    """Libro y pluma: el diario de misiones."""
    def mat(r, g, b):
        d, l, s = deg(r, g, b)
        return 'cover' if s > 0.12 and (d < 50 or d > 340) and l < 0.75 else 'page'
    return calco(ref('writable_book'), mat, {'page': PAGE, 'cover': BLUE})


def cazas():
    """Ballesta cargada."""
    def mat(r, g, b):
        d, l, s = deg(r, g, b)
        return 'iron' if s < 0.12 else 'wood'
    return calco(ref('crossbow_arrow'), mat, {'iron': IRON, 'wood': WOOD})


def viajes():
    """Mapa con tierras y agua."""
    def mat(r, g, b):
        d, l, s = deg(r, g, b)
        if 200 < d < 260 and s > 0.3:
            return 'water'
        if 70 < d < 160 and s > 0.25:
            return 'land'
        return 'paper'
    return calco(ref('filled_map'), mat, {'water': BLUE, 'land': GREEN, 'paper': PAPER})


def explorar():
    """Catalejo: latón, cuero y lente."""
    def mat(r, g, b):
        d, l, s = deg(r, g, b)
        if 160 < d < 220 and s > 0.2:
            return 'glass'
        if 25 < d < 65 and s > 0.35 and l > 0.38:
            return 'brass'
        return 'leather'
    return calco(ref('spyglass'), mat, {'glass': DIAM, 'brass': GOLD, 'leather': LEATHER})


def kits():
    """Saco de kits atado con cordel."""
    return calco(ref('bundle'), lambda r, g, b: 'sack', {'sack': SACK})


def titulos():
    """Etiqueta de nombre (sin las letras de la referencia)."""
    def mat(r, g, b):
        d, l, s = deg(r, g, b)
        return 'string' if s < 0.15 and l > 0.55 else 'paper'

    def post(ic, m):
        # borra el grabado de la referencia: un píxel más oscuro que casi todos sus vecinos toma el tono que más se
        # repite alrededor (dos pasadas). El sombreado del calco se queda.
        paper = {p for p, k in m.items() if k == 'paper'}
        rank = {c: i for i, c in enumerate(TAG)}
        for _ in range(3):
            changes = {}
            for x, y in paper:
                c = ic.c[y][x]
                nb = [ic.c[y + dy][x + dx] for dx in (-1, 0, 1) for dy in (-1, 0, 1)
                      if (dx or dy) and (x + dx, y + dy) in paper]
                if len(nb) < 7 or c not in rank:
                    continue
                darker = sum(1 for n in nb if n in rank and rank[n] < rank[c])
                if darker >= 5:
                    common = max(set(nb), key=nb.count)
                    changes[(x, y)] = common
            for (x, y), c in changes.items():
                ic.px(x, y, c)
    return calco(ref('name_tag'), mat, {'string': PAGE, 'paper': TAG}, post=post)


def ayuda():
    """Libro de conocimiento verde con una interrogación dorada."""
    def mat(r, g, b):
        d, l, s = deg(r, g, b)
        return 'cover' if s > 0.18 and 70 < d < 170 else 'page'

    return calco(ref('knowledge_book'), mat, {'cover': GREEN, 'page': PAGE})


def comunidad():
    """Foto enmarcada (paisaje con sol) con un corazón: la red social del servidor."""
    def mat(r, g, b):
        d, l, s = deg(r, g, b)
        if s < 0.12 and l > 0.5:
            return 'canvas'
        return 'frame'

    def post(ic, m):
        canvas = {p for p, k in m.items() if k == 'canvas'}
        if not canvas:
            return
        x0 = min(x for x, y in canvas); x1 = max(x for x, y in canvas)
        y0 = min(y for x, y in canvas); y1 = max(y for x, y in canvas)
        h = y1 - y0
        for x, y in canvas:
            t = (y - y0) / max(h, 1)
            col = (150, 220, 255) if t < 0.35 else (110, 200, 255)
            # colinas
            hill = y1 - 4 - round(3 * math.sin((x - x0) / 3.2))
            if y >= hill:
                col = GREEN[1] if y == hill else GREEN[2]
            if y >= y1 - 1:
                col = GREEN[3]
            ic.px(x, y, col)
        # sol
        sx, sy = x0 + 4, y0 + 3
        for x, y in ((sx, sy), (sx + 1, sy), (sx, sy + 1), (sx + 1, sy + 1), (sx - 1, sy), (sx, sy - 1)):
            if (x, y) in canvas:
                ic.px(x, y, (255, 230, 90))
        # nube
        for x, y in ((x1 - 7, y0 + 3), (x1 - 6, y0 + 3), (x1 - 5, y0 + 3), (x1 - 6, y0 + 2), (x1 - 4, y0 + 3)):
            if (x, y) in canvas:
                ic.px(x, y, WHITE)
        # corazón abajo a la derecha (por encima del marco)
        heart = [".##.##.", "#######", "#######", ".#####.", "..###..", "...#..."]
        hx, hy = 22, 20
        for dy, row in enumerate(heart):
            for dx, ch in enumerate(row):
                if ch == '#':
                    ic.px(hx + dx, hy + dy, PINK[2])
        for x, y in ((hx + 1, hy), (hx, hy + 1), (hx + 1, hy + 1)):
            ic.px(x, y, PINK[0])
        ic.px(hx + 2, hy + 1, WHITE)
        for x, y in ((hx + 6, hy + 1), (hx + 6, hy + 2), (hx + 5, hy + 3), (hx + 4, hy + 4), (hx + 3, hy + 5)):
            ic.px(x, y, PINK[3])
        heart_px = {(hx + dx, hy + dy) for dy, row in enumerate(heart) for dx, ch in enumerate(row) if ch == '#'}
        for x, y in list(heart_px):
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                p = (x + dx, y + dy)
                if p not in heart_px and ic.c[p[1]][p[0]] is not None:
                    ic.px(p[0], p[1], OUT)
    return calco(ref('painting'), mat, {'frame': WOOD, 'canvas': PAGE}, post=post)


# ---------------------------------------------------------------------------------------------------------------------
# Dibujados a mano
# ---------------------------------------------------------------------------------------------------------------------

def shade(ic, m, ramp, hi=True):
    """5 tonos por cercanía al borde: luz arriba-izquierda, sombra abajo-derecha."""
    for x, y in m:
        up = (x, y - 1) not in m
        lf = (x - 1, y) not in m
        dn = (x, y + 1) not in m
        rt = (x + 1, y) not in m
        col = ramp[2]
        if dn and rt:
            col = ramp[4]
        elif dn or rt:
            col = ramp[3]
        elif up and lf and hi:
            col = ramp[0]
        elif up or lf:
            col = ramp[1]
        ic.px(x, y, col)


def clanes():
    """Estandarte del clan: barra de madera con remates de oro, tela roja con cola de golondrina y espadas cruzadas."""
    ic = Icon()
    rod = ic.rect(4, 4, 27, 5)
    cloth = set()
    for y in range(6, 28):
        for x in range(7, 25):
            # cola de golondrina: corte en V abajo
            if y >= 22 and abs(x - 15.5) < (y - 21) * 1.1:
                continue
            cloth.add((x, y))
    shade(ic, cloth, RED)
    # banda de oro arriba y ribete
    for x in range(7, 25):
        ic.px(x, 6, GOLD[2]); ic.px(x, 7, GOLD[3])
    for x in range(8, 24):
        ic.px(x, 6, GOLD[1])
    # emblema: dos espadas cruzadas
    sword1 = [(11 + i, 11 + i) for i in range(10)]
    sword2 = [(20 - i, 11 + i) for i in range(10)]
    for x, y in sword1 + sword2:
        ic.px(x, y, IRON[1])
    for x, y in sword1[:7] + sword2[:7]:
        ic.px(x + 1, y, IRON[3]) if (x + 1, y) in cloth else None
    for (x, y) in (sword1[0], sword2[0]):
        ic.px(x, y, WHITE)
    # guardas y pomos de oro
    for x, y in ((17, 16), (18, 17), (16, 17), (14, 16), (13, 17), (15, 17)):
        pass
    for x, y in ((18, 19), (19, 18), (13, 19), (12, 18)):
        ic.px(x, y, GOLD[1])
    for x, y in ((21, 21), (10, 21)):
        ic.px(x, y, GOLD[2])
    # barra
    for x, y in rod:
        ic.px(x, y, WOOD[1] if y == 4 else WOOD[3])
    for x0 in (2, 27):
        for x, y in ic.rect(x0, 3, x0 + 2, 6):
            ic.px(x, y, GOLD[2])
        ic.px(x0, 3, GOLD[0]); ic.px(x0 + 1, 3, GOLD[1]); ic.px(x0 + 2, 6, GOLD[4]); ic.px(x0 + 2, 5, GOLD[3])
    # cordón de colgar
    for x, y in ((15, 2), (16, 2), (14, 3), (17, 3)):
        ic.px(x, y, GOLD[2])
    return ic.outline()


def ranking():
    """Trofeo de oro con estrella en la copa y peana de madera."""
    ic = Icon()
    cup = set()
    for y in range(4, 17):
        half = 9 if y < 9 else 9 - (y - 8) * 0.75
        for x in range(32):
            if abs(x - 15.5) <= half:
                cup.add((x, y))
    stem = ic.rect(14, 17, 17, 20)
    plate = ic.rect(11, 21, 20, 22)
    shade(ic, cup | stem | plate, GOLD)
    # boca de la copa: interior más oscuro
    for x in range(8, 24):
        ic.px(x, 4, GOLD[1]); ic.px(x, 5, GOLD[3])
    ic.px(7, 4, GOLD[0]); ic.px(24, 4, GOLD[2])
    # asas
    for pts, col in (([(5, 6), (4, 7), (4, 8), (4, 9), (5, 10), (6, 11)], GOLD[1]),
                     ([(26, 6), (27, 7), (27, 8), (27, 9), (26, 10), (25, 11)], GOLD[3])):
        for x, y in pts:
            ic.px(x, y, col)
    ic.px(6, 6, GOLD[2]); ic.px(25, 6, GOLD[3])
    # estrella
    star = ["..#..", ".###.", "#####", ".###.", ".#.#."]
    for dy, row in enumerate(star):
        for dx, ch in enumerate(row):
            if ch == '#':
                ic.px(14 + dx - 1, 8 + dy, WHITE if dy < 2 else (255, 250, 200))
    # brillo vertical de la copa
    for y in range(7, 14):
        ic.px(10, y, GOLD[0])
    # peana de madera
    base = ic.rect(9, 23, 22, 28)
    shade(ic, base, WOOD)
    for x in range(12, 20):
        ic.px(x, 25, GOLD[2]); ic.px(x, 26, GOLD[3])
    ic.px(12, 25, GOLD[1])
    return ic.outline()


def camara():
    """Cámara de fotos clásica: cuerpo de cuero claro, placa de plata, objetivo grande y flash."""
    ic = Icon()
    body = {(x, y) for y in range(10, 27) for x in range(3, 29)} - {(3, 10), (28, 10), (3, 26), (28, 26)}
    top = {(x, y) for y in range(6, 10) for x in range(9, 20)} - {(9, 6), (19, 6)}
    shade(ic, body, [(255, 236, 200), (246, 214, 166), (226, 186, 132), (186, 142, 96), (140, 100, 64)])
    # placa de plata arriba del cuerpo
    plate = {(x, y) for y in range(10, 14) for x in range(3, 29)} - {(3, 10), (28, 10)}
    shade(ic, plate, IRON)
    shade(ic, top, IRON)
    # visor
    for x, y in ic.rect(11, 7, 14, 8):
        ic.px(x, y, BLUE[3] if y == 8 else BLUE[1])
    ic.px(11, 7, WHITE)
    # disparador rojo
    for x, y in ((22, 8), (23, 8), (22, 9), (23, 9)):
        ic.px(x, y, RED[2])
    ic.px(22, 8, RED[1])
    # flash
    for x, y in ic.rect(5, 11, 7, 12):
        ic.px(x, y, (255, 250, 210) if y == 11 else (255, 236, 150))
    # objetivo: anillos
    cx, cy = 16.0, 18.5
    for x in range(32):
        for y in range(32):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d <= 7.2:
                col = IRON[3] if d > 6.2 else IRON[1] if d > 5.0 else OUT if d > 4.2 else BLUE[2]
                if d <= 4.2:
                    col = BLUE[3] if (x + y) > cx + cy + 1 else BLUE[2]
                    if d <= 2.0:
                        col = BLUE[4]
                ic.px(x, y, col)
    # reflejos del cristal
    for x, y in ((14, 16), (13, 17), (14, 17)):
        ic.px(x, y, WHITE)
    ic.px(18, 21, BLUE[1])
    # correas laterales
    for y in range(15, 22):
        ic.px(3, y, (140, 100, 64)); ic.px(28, y, (110, 76, 50))
    return ic.outline()


def jugadores():
    """Carnet de jugador: foto con su cara, nombre y datos, y una estrella de oro."""
    ic = Icon()
    card = {(x, y) for y in range(7, 26) for x in range(2, 30)}
    card -= {(2, 7), (29, 7), (2, 25), (29, 25), (3, 7), (2, 8), (28, 7), (29, 8), (2, 24), (3, 25), (29, 24), (28, 25)}
    shade(ic, card, [(255, 255, 255), (236, 248, 255), (214, 238, 255), (160, 206, 244), (110, 160, 220)])
    # franja azul arriba
    for x, y in card:
        if y in (8, 9, 10) and (x, y) in card:
            ic.px(x, y, BLUE[1] if y == 8 else BLUE[2])
    # foto con la cara
    face = ["HHHHHHHH",
            "HHHHHHHH",
            "HSSSSSSH",
            "SSSSSSSS",
            "SEWSSWES",
            "SSSNNSSS",
            "SSMSSMSS",
            "SSMMMMSS"]
    pal = {'H': (122, 76, 44), 'S': (246, 196, 150), 'E': (60, 80, 200), 'W': WHITE, 'N': (214, 150, 110),
           'M': (176, 96, 80)}
    for x, y in ic.rect(5, 12, 14, 22):
        ic.px(x, y, (150, 206, 246))
    for dy, row in enumerate(face):
        for dx, ch in enumerate(row):
            ic.px(6 + dx, 13 + dy, pal[ch])
    for x in range(5, 15):
        ic.px(x, 21, BLUE[2]); ic.px(x, 22, BLUE[3])
    for y in range(12, 23):
        ic.px(4, y, OUT); ic.px(15, y, OUT)
    for x in range(4, 16):
        ic.px(x, 11, OUT); ic.px(x, 23, OUT)
    # líneas de datos
    for y, (a, b) in ((13, (18, 27)), (16, (18, 25)), (19, (18, 26))):
        for x in range(a, b + 1):
            ic.px(x, y, (110, 160, 220) if y != 13 else BLUE[3])
    # estrella de oro
    star = ["..#..", ".###.", "#####", ".#.#."]
    for dy, row in enumerate(star):
        for dx, ch in enumerate(row):
            if ch == '#':
                ic.px(23 + dx, 20 + dy, GOLD[1] if dy < 2 else GOLD[2])
    return ic.outline()


ICONOS = {'misiones': misiones, 'cazas': cazas, 'viajes': viajes, 'explorar': explorar, 'kits': kits,
          'titulos': titulos, 'ayuda': ayuda, 'comunidad': comunidad, 'jugadores': jugadores, 'clanes': clanes,
          'ranking': ranking, 'camara': camara}
