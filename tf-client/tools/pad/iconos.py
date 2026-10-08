"""Los 6 iconos del pad, 32x32, dibujados píxel a píxel."""
import sys
from PIL import Image
from kit import *


def rango():
    ic = Icon()
    gold, vel = set(), set()
    half = {6: [(15, 15)], 7: [(14, 15)], 8: [(14, 15)], 9: [(13, 15), (4, 5)], 10: [(13, 15), (4, 5)],
            11: [(12, 15), (4, 6)], 12: [(12, 15), (4, 6)], 13: [(11, 15), (4, 7)], 14: [(11, 15), (4, 7)],
            15: [(10, 15), (4, 8)], 16: [(4, 15)], 17: [(4, 15)], 18: [(4, 15)], 19: [(4, 15)]}
    vhalf = {11: (8, 11), 12: (7, 11), 13: (8, 10), 14: (8, 10), 15: (9, 9)}
    for y, spans in half.items():
        for a, b in spans:
            for x in range(a, b + 1):
                gold |= {(x, y), (31 - x, y)}
    for y, (a, b) in vhalf.items():
        for x in range(a, b + 1):
            vel |= {(x, y), (31 - x, y)}
    vel -= gold
    band = ic.rect(3, 20, 28, 26)
    # terciopelo
    for x, y in vel:
        ic.px(x, y, RED[3] if (x, y - 1) in vel else RED[2])
    # cuerpo
    ic.bevel(gold, GOLD)
    for x, y in gold:
        if ic.c[y][x] == GOLD[2] and x >= 21:
            ic.px(x, y, GOLD[3] if x >= 26 else GOLD[2])
        if ic.c[y][x] == GOLD[2] and x <= 6:
            ic.px(x, y, GOLD[1])
    for x in range(4, 28):
        ic.px(x, 19, GOLD[3])
    # banda
    for x, y in band:
        col = GOLD[2]
        if y == 20: col = GOLD[1]
        if y == 25: col = GOLD[3]
        if y == 26: col = GOLD[4]
        if x == 3 and y < 26: col = GOLD[1]
        if x == 28: col = GOLD[4] if y >= 25 else GOLD[3]
        ic.px(x, y, col)
    for x in (4, 5, 6): ic.px(x, 20, GOLD[0])
    ic.px(4, 21, GOLD[0])
    # perlas
    def pearl(x0, y0):
        for dx, dy in ((1, 0), (2, 0), (0, 1), (1, 1), (2, 1), (3, 1), (0, 2), (1, 2), (2, 2), (3, 2), (1, 3), (2, 3)):
            ic.px(x0 + dx, y0 + dy, IRON[2])
        ic.px(x0 + 1, y0, IRON[1]); ic.px(x0, y0 + 1, IRON[1])
        ic.px(x0 + 1, y0 + 1, WHITE)
        ic.px(x0 + 3, y0 + 2, IRON[3]); ic.px(x0 + 2, y0 + 3, IRON[3]); ic.px(x0 + 1, y0 + 3, IRON[3])
        ic.px(x0 + 3, y0 + 1, IRON[2])
    pearl(14, 2); pearl(3, 5); pearl(25, 5)
    # gemas
    ruby = {(x, y) for y in range(21, 26) for x in range(13, 19)} - {(13, 21), (18, 21), (13, 25), (18, 25)}
    for x, y in ruby:
        ic.px(x, y, RED[2])
    for x, y in ((14, 21), (15, 21), (13, 22), (14, 22)): ic.px(x, y, RED[1])
    ic.px(14, 22, WHITE)
    for x, y in ((18, 23), (18, 24), (17, 24), (17, 25), (16, 25), (15, 25), (14, 25)): ic.px(x, y, RED[3])
    for gx in (6, 23):
        for x, y in ic.rect(gx, 22, gx + 2, 24):
            ic.px(x, y, BLUE[2])
        ic.px(gx, 22, BLUE[0]); ic.px(gx + 1, 22, BLUE[1]); ic.px(gx, 23, BLUE[1])
        ic.px(gx + 2, 24, BLUE[4]); ic.px(gx + 1, 24, BLUE[3]); ic.px(gx + 2, 23, BLUE[3])
    for x, y in ((15, 11), (16, 11), (14, 12), (15, 12), (16, 12), (17, 12), (15, 13), (16, 13)):
        ic.px(x, y, BLUE[2])
    ic.px(15, 11, BLUE[0]); ic.px(14, 12, BLUE[1]); ic.px(15, 12, BLUE[1])
    ic.px(17, 12, BLUE[3]); ic.px(16, 13, BLUE[4])
    return ic.outline()


def tienda():
    """Cofre de madera con herrajes de oro y cerradura con esmeralda."""
    ic = Icon()
    lid = {(x, y) for y in range(7, 15) for x in range(3, 29)}
    lid |= {(x, 4) for x in range(9, 23)} | {(x, 5) for x in range(6, 26)} | {(x, 6) for x in range(4, 28)}
    box = ic.rect(3, 15, 28, 27)
    # madera: tablones horizontales
    for x, y in lid | box:
        col = WOOD[2]
        if y <= 6 or (y == 7 and 5 <= x <= 26):
            col = WOOD[1]
        if y == 4 or (y == 5 and x < 12):
            col = WOOD[0]
        if y in (13, 14):
            col = WOOD[3]
        if y in (19, 23):
            col = WOOD[3]
        if y in (20, 24):
            col = WOOD[1]
        if y == 16:
            col = WOOD[4]
        if y == 27:
            col = WOOD[4]
        if x == 3:
            col = WOOD[1]
        if x == 28:
            col = WOOD[4]
        ic.px(x, y, col)
    ic.px(5, 8, WOOD[0]); ic.px(6, 8, WOOD[0]); ic.px(4, 9, WOOD[0])
    # vetas
    for x, y in ((9, 11), (10, 11), (21, 10), (22, 10), (8, 18), (9, 18), (23, 21), (24, 21), (11, 26), (12, 26), (20, 26)):
        ic.px(x, y, WOOD[3])
    # filo de la tapa
    for x in range(3, 29):
        ic.px(x, 15, GOLD[3] if x not in (3, 4, 5, 6, 25, 26, 27, 28) else GOLD[2])
    # esquineras de oro
    for (x0, x1) in ((3, 6), (25, 28)):
        for x, y in ic.rect(x0, 7, x1, 27):
            ic.px(x, y, GOLD[2])
        for y in range(7, 28):
            ic.px(x0, y, GOLD[1])
            ic.px(x1, y, GOLD[3])
        for x in range(x0, x1 + 1):
            ic.px(x, 27, GOLD[4])
            ic.px(x, 14, GOLD[3])
            ic.px(x, 15, GOLD[1])
        ic.px(x0, 7, GOLD[0]); ic.px(x0, 15, GOLD[0])
        # remaches
        for ry in (10, 21):
            ic.px(x0 + 1, ry, GOLD[4]); ic.px(x0 + 2, ry, GOLD[4])
            ic.px(x0 + 1, ry - 1, GOLD[0])
    # herrajes siguiendo la curva de la tapa
    for x, y in ((4, 6), (5, 6), (6, 6), (6, 5), (7, 5)):
        ic.px(x, y, GOLD[1])
    for x, y in ((25, 6), (26, 6), (27, 6), (24, 5), (25, 5)):
        ic.px(x, y, GOLD[2])
    ic.px(4, 6, GOLD[0]); ic.px(6, 5, GOLD[0]); ic.px(27, 6, GOLD[3]); ic.px(28, 7, GOLD[3])
    # cerradura
    under = {p: ic.c[p[1]][p[0]] for p in ((12, 12), (19, 12), (12, 21), (19, 21))}
    for x, y in ic.rect(12, 12, 19, 21):
        ic.px(x, y, GOLD[2])
    for y in range(12, 22):
        ic.px(12, y, GOLD[1]); ic.px(19, y, GOLD[3])
    for x in range(12, 20):
        ic.px(x, 12, GOLD[1]); ic.px(x, 21, GOLD[4])
    ic.px(12, 12, GOLD[0]); ic.px(13, 12, GOLD[0]); ic.px(12, 13, GOLD[0])
    for (x, y), col in under.items():
        ic.c[y][x] = col
    # esmeralda azulada
    em = {(15, 14), (16, 14), (14, 15), (15, 15), (16, 15), (17, 15), (14, 16), (15, 16), (16, 16), (17, 16),
          (14, 17), (15, 17), (16, 17), (17, 17), (15, 18), (16, 18)}
    for x, y in em:
        ic.px(x, y, TEAL[2])
    for x, y in ((15, 14), (14, 15), (14, 16)): ic.px(x, y, TEAL[1])
    ic.px(15, 15, WHITE)
    for x, y in ((17, 16), (17, 17), (16, 18)): ic.px(x, y, TEAL[3])
    ic.px(16, 17, TEAL[2]); ic.px(15, 18, TEAL[3])
    ic.px(15, 19, OUT); ic.px(16, 19, OUT); ic.px(15, 20, OUT)
    return ic.outline()


def protecciones():
    """Escudo: borde de oro, campo azul y torre de plata."""
    ic = Icon()
    left = {3: 4}
    for y in range(4, 18): left[y] = 3
    left.update({18: 4, 19: 4, 20: 5, 21: 5, 22: 6, 23: 7, 24: 8, 25: 9, 26: 10, 27: 11, 28: 12, 29: 13, 30: 14})
    outer = {(x, y) for y, l in left.items() for x in range(l, 32 - l)}
    inner = {(x, y) for y, l in left.items() if 5 <= y <= 28 for x in range(l + 2, 30 - l)}
    for x, y in outer - inner:
        col = GOLD[1] if x < 16 else GOLD[3]
        if y <= 4: col = GOLD[1] if x < 24 else GOLD[2]
        if x >= 16 and y >= 20: col = GOLD[4] if (x + 1, y) not in outer or (x, y + 1) not in outer else GOLD[3]
        if x < 16 and (x - 1, y) not in outer: col = GOLD[1]
        ic.px(x, y, col)
    for x in range(5, 12): ic.px(x, 3, GOLD[0])
    for y in range(4, 9): ic.px(3, y, GOLD[0])
    ic.px(4, 4, GOLD[0])
    for x, y in inner:
        col = BLUE[2] if x < 16 else BLUE[3]
        if (x, y - 1) not in inner: col = BLUE[4]
        elif (x - 1, y) not in inner: col = BLUE[1]
        ic.px(x, y, col)
    # torre de plata
    tower = ic.rect(11, 10, 20, 24) | ic.rect(10, 8, 12, 10) | ic.rect(15, 8, 16, 10) | ic.rect(19, 8, 21, 10)
    tower -= ic.rect(10, 10, 10, 10) | ic.rect(21, 10, 21, 10)
    for x, y in tower:
        col = IRON[1] if x < 16 else IRON[2]
        if (x, y - 1) not in tower: col = WHITE if x < 16 else IRON[1]
        if (x + 1, y) not in tower or (x, y + 1) not in tower: col = IRON[3]
        if (x - 1, y) not in tower and (x, y - 1) in tower: col = WHITE
        ic.px(x, y, col)
    for x in range(11, 21): ic.px(x, 11, IRON[3])
    # puerta y ventana
    for x, y in ic.rect(14, 19, 17, 24) - {(14, 19), (17, 19)}:
        ic.px(x, y, OUT)
    for x, y in ((15, 14), (16, 14), (15, 15), (16, 15)):
        ic.px(x, y, OUT)
    ic.px(14, 20, (60, 54, 100)); ic.px(15, 19, (60, 54, 100))
    # contorno de la torre sobre el campo
    for x, y in list(inner - tower):
        if any((x + dx, y + dy) in tower for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            ic.px(x, y, BLUE[4])
    # brillo
    for x, y in ((6, 7), (6, 8), (6, 9), (7, 7)):
        ic.px(x, y, BLUE[0])
    return ic.outline()



def armario():
    """Túnica real morada con ribete de oro, colgada de una percha."""
    half = [
        "................",  # 0
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "..............ww",  # 7
        "............wwgg",
        "..........wwhgnn",
        "........wwhhhgnn",
        "......wwhhhmmmgn",
        "....wwhhhmmmmmgg",
        "...hhhhmmmmmmmmm",
        "..hhmmmmmmmmmmmm",
        "..hmmmmmmmmmmmmm",
        "..GGGGmmmmmmmmmm",
        "..GGGG.hmmmmmmmm",
        ".......hmmmmmmmm",
        ".......GGGGGGGGG",
        ".......hmmmmmmmm",
        "......hhmmmdmmmm",
        "......hhmmmdmmmm",
        "......hhmmmdmmmm",
        "......hhmmmdmmmm",
        ".....hhhmmmdmmmm",
        ".....hhhmmmdmmmm",
        ".....hhhmmmdmmmm",
        ".....GGGGGGGGGGG",
        "................",
        "................",
        "................",
    ]
    swap = {'h': 'm', 'm': 'd', 'd': 'D', 'g': 'G', 'G': 'E', 'w': 'W', 'n': 'n'}
    rows = []
    for r in half:
        right = ''.join(swap.get(ch, ch) for ch in reversed(r))
        rows.append(r + right)
    pal = {'h': PURP[1], 'm': PURP[2], 'd': PURP[3], 'D': PURP[4], 'g': GOLD[1], 'G': GOLD[2], 'E': GOLD[3],
           'w': WOOD[1], 'W': WOOD[2], 'n': PURP[4]}
    ic = ascii_icon(rows, pal)
    # borde inferior y lateral derecho más oscuros
    for y in range(13, 28):
        for x in range(16, 32):
            if ic.c[y][x] and (x + 1 >= 32 or ic.c[y][x + 1] is None):
                ic.px(x, y, PURP[4])
    for x in range(5, 27):
        ic.px(x, 28, GOLD[2] if x < 16 else GOLD[3])
        ic.px(x, 29, GOLD[3] if x < 16 else GOLD[4])
    for x in range(7, 25):
        ic.px(x, 20, GOLD[3] if x >= 16 else GOLD[2])
    # brillos
    ic.px(4, 13, PURP[0]); ic.px(5, 13, PURP[0]); ic.px(3, 14, PURP[0])
    ic.px(5, 28, GOLD[0]); ic.px(6, 28, GOLD[0]); ic.px(7, 19, GOLD[0]); ic.px(8, 19, GOLD[0])
    ic.px(2, 16, GOLD[0]); ic.px(3, 16, GOLD[0])
    # hebilla con gema
    for x, y in ic.rect(14, 18, 17, 21):
        ic.px(x, y, GOLD[2])
    ic.px(14, 18, GOLD[0]); ic.px(15, 18, GOLD[1]); ic.px(14, 19, GOLD[1])
    ic.px(17, 21, GOLD[4]); ic.px(16, 21, GOLD[3]); ic.px(17, 20, GOLD[3])
    ic.px(15, 19, TEAL[1]); ic.px(16, 19, TEAL[2]); ic.px(15, 20, TEAL[2]); ic.px(16, 20, TEAL[3])
    # gancho
    for x, y in ((15, 1), (16, 1), (17, 1), (14, 2), (18, 2), (18, 3), (17, 4), (16, 5), (16, 6)):
        ic.px(x, y, IRON[2])
    ic.px(15, 1, WHITE); ic.px(14, 2, IRON[1]); ic.px(18, 3, IRON[3]); ic.px(17, 4, IRON[3])
    ic.px(15, 7, WOOD[0]); ic.px(14, 7, WOOD[0])
    return ic.outline()


def efectos():
    """Destello mágico grande con chispas."""
    ic = Icon()
    def star(cx, cy, ws):
        m = set()
        for d, w in enumerate(ws):
            for k in range(-w, w + 1):
                m |= {(cx + k, cy + d), (cx + k, cy - d), (cx + d, cy + k), (cx - d, cy + k)}
        return m
    cx, cy = 13, 16
    big = star(cx, cy, [4, 4, 3, 3, 2, 2, 1, 1, 1, 1, 0, 0, 0])
    for x, y in big:
        dx, dy = x - cx, y - cy
        m = abs(dx) + abs(dy)
        lit = dx < 0 or dy < 0
        if m <= 2: col = WHITE
        elif m <= 4: col = PINK[0]
        elif m <= 8: col = PINK[1] if lit else PURP[1]
        else: col = PINK[2] if lit else PURP[2]
        if dx > 0 and dy > 0 and m > 2: col = PURP[1] if m <= 4 else PURP[2]
        ic.px(x, y, col)
    ic.px(cx - 1, cy - 1, WHITE)
    def chispa(cx, cy, ws, ramp):
        for x, y in star(cx, cy, ws):
            m = abs(x - cx) + abs(y - cy)
            ic.px(x, y, WHITE if m <= 1 else (ramp[1] if m <= 3 else ramp[2]))
    chispa(26, 6, [2, 1, 1, 0, 0], DIAM)
    chispa(26, 26, [1, 1, 0, 0], GOLD)
    chispa(4, 5, [1, 0, 0], DIAM)
    return ic.outline()


def oficios():
    import pico
    return pico.pico()


def rrect(ic, x0, y0, x1, y1, r=2):
    m = ic.rect(x0, y0, x1, y1)
    for x, y in list(m):
        cx = min(x - x0, x1 - x)
        cy = min(y - y0, y1 - y)
        if cx + cy < r:
            m.discard((x, y))
    return m


def monedero():
    """Pila de monedas de oro y una moneda grande delante con un rombo en relieve."""
    ic = Icon()
    stack = set()
    for i in range(5):
        y = 26 - i * 3
        for x in range(2, 14):
            stack |= {(x, y), (x, y - 1), (x, y - 2)}
            ic.px(x, y, GOLD[4] if x > 9 else GOLD[3])
            ic.px(x, y - 1, GOLD[2] if x > 3 else GOLD[1])
            ic.px(x, y - 2, GOLD[1] if x > 3 else GOLD[0])
    top = rrect(ic, 2, 9, 13, 12, 2)
    stack |= top
    for x, y in top:
        ic.px(x, y, GOLD[1])
    for x in range(4, 12):
        ic.px(x, 9, GOLD[0]); ic.px(x, 12, GOLD[2])
    for x, y in ((4, 10), (5, 10)):
        ic.px(x, y, WHITE)
    cx, cy = 19.5, 18.5
    face = ic.disc(cx, cy, 10.3)
    inner = ic.disc(cx, cy, 7.4)
    for x, y in face:
        dx, dy = x + 0.5 - cx, y + 0.5 - cy
        if (x, y) in inner:
            col = GOLD[2]
            if dx + dy < -7: col = GOLD[3]
            if dx + dy > 7: col = GOLD[1]
        else:
            col = GOLD[2]
            if dx + dy < -2: col = GOLD[1]
            if dx + dy < -9: col = GOLD[0]
            if dx + dy > 4: col = GOLD[3]
            if dx + dy > 10: col = GOLD[4]
        ic.px(x, y, col)
    # canto interior del aro: sombra arriba-izquierda, luz abajo-derecha
    for x, y in face - inner:
        if any((x + dx, y + dy) in inner for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            ic.px(x, y, GOLD[4] if dx + dy < 0 else GOLD[0])
    # rombo en relieve con contorno grabado
    gem = {(x, y) for y in range(32) for x in range(32) if abs(x + 0.5 - cx) + abs(y + 0.5 - cy) <= 4.6}
    ring = {(x, y) for y in range(32) for x in range(32) if 4.6 < abs(x + 0.5 - cx) + abs(y + 0.5 - cy) <= 5.7}
    for x, y in ring:
        ic.px(x, y, GOLD[4])
    for x, y in gem:
        dx, dy = x + 0.5 - cx, y + 0.5 - cy
        if dx <= 0 and dy <= 0: col = GOLD[0]
        elif dx > 0 and dy > 0: col = GOLD[3]
        else: col = GOLD[1] if dy <= 0 else GOLD[2]
        ic.px(x, y, col)
    ic.px(18, 16, WHITE); ic.px(19, 15, WHITE)
    # contorno de la moneda sobre la pila
    for x, y in stack - face:
        if any((x + dx, y + dy) in face for dx, dy in ((1, 0), (0, 1), (0, -1), (-1, 0))):
            ic.px(x, y, OUT)
    for x, y in ((13, 13), (12, 14), (14, 12)):
        ic.px(x, y, WHITE)
    return ic.outline()


def comunidad():
    """Dos bocadillos de chat: uno azul detrás y uno blanco delante con tres puntos."""
    ic = Icon()
    back = rrect(ic, 12, 3, 29, 16, 3) | {(24, 17), (25, 17), (26, 17), (25, 18), (26, 18), (26, 19)}
    for x, y in back:
        col = BLUE[2]
        if (x, y - 1) not in back or (x - 1, y) not in back: col = BLUE[1]
        if (x, y + 1) not in back or (x + 1, y) not in back: col = BLUE[3]
        ic.px(x, y, col)
    for x in range(15, 27):
        ic.px(x, 4, BLUE[0])
    for x, y in ((20, 8), (21, 8), (22, 8), (23, 8), (24, 8), (25, 8), (20, 11), (21, 11), (22, 11), (23, 11)):
        ic.px(x, y, BLUE[0])
    front = rrect(ic, 2, 11, 21, 26, 3) | {(6, 27), (7, 27), (8, 27), (5, 28), (6, 28), (5, 29)}
    # contorno entre los dos bocadillos
    for x, y in front:
        if any((x + dx, y + dy) in back and (x + dx, y + dy) not in front for dx, dy in ((1, 0), (0, -1), (1, -1))):
            pass
    edge = {(x, y) for x, y in back if (x, y) not in front and any((x + dx, y + dy) in front for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    for x, y in front:
        col = IRON[1]
        if (x, y - 1) not in front or (x - 1, y) not in front: col = WHITE
        if (x, y + 1) not in front or (x + 1, y) not in front: col = IRON[2]
        if ((x, y + 1) not in front and (x + 1, y) not in front): col = IRON[3]
        ic.px(x, y, col)
    for x, y in edge:
        ic.px(x, y, OUT)
    for x in range(5, 18):
        ic.px(x, 12, WHITE)
    # tres puntos
    for i, ramp in enumerate((PURP, BLUE, TEAL)):
        x0 = 6 + i * 5
        for x, y in ((x0, 17), (x0 + 1, 17), (x0, 18), (x0 + 1, 18), (x0 + 2, 17), (x0 + 2, 18), (x0, 19), (x0 + 1, 19), (x0 + 2, 19)):
            ic.px(x, y, ramp[2])
        ic.px(x0, 17, ramp[0]); ic.px(x0 + 1, 17, ramp[1]); ic.px(x0, 18, ramp[1])
        ic.px(x0 + 2, 19, ramp[4]); ic.px(x0 + 1, 19, ramp[3]); ic.px(x0 + 2, 18, ramp[3])
    return ic.outline()


def mercado():
    """GTS: puesto de mercado con toldo de rayas y cosas a la venta."""
    ic = Icon()
    # interior del puesto
    for x, y in ic.rect(5, 11, 26, 20):
        ic.px(x, y, WOOD[3] if y > 12 else WOOD[4])
    for x in range(5, 27):
        ic.px(x, 11, WOOD[4]); ic.px(x, 12, WOOD[4])
    # postes
    for x0 in (3, 26):
        for y in range(10, 28):
            ic.px(x0, y, WOOD[1]); ic.px(x0 + 1, y, WOOD[2]); ic.px(x0 + 2, y, WOOD[3])
    # mostrador
    for x, y in ic.rect(2, 21, 29, 28):
        col = WOOD[2]
        if y == 21: col = WOOD[0]
        elif y == 22: col = WOOD[1]
        elif y in (25,): col = WOOD[3]
        elif y == 28: col = WOOD[4]
        if x == 2 and y > 21: col = WOOD[1]
        if x == 29: col = WOOD[4]
        ic.px(x, y, col)
    for x in (9, 16, 23):
        for y in range(23, 28):
            ic.px(x, y, WOOD[3])
    # toldo: rayas rojas y blancas con borde ondulado
    STRIPE_R = [(255, 150, 150), (238, 62, 72), (176, 30, 52)]
    STRIPE_W = [(255, 255, 255), (244, 240, 236), (206, 196, 196)]
    awn = set()
    for x in range(1, 31):
        top = 4 if 3 <= x <= 28 else 5
        bot = 9
        k = (x - 1) % 5
        if k in (1, 2, 3):
            bot = 11
        for y in range(top, bot + 1):
            awn.add((x, y))
    for x, y in awn:
        red = ((x - 1) // 5) % 2 == 0
        R = STRIPE_R if red else STRIPE_W
        col = R[1]
        if y == 4 or (y == 5 and x in (1, 2, 29, 30)): col = R[0]
        if (x, y + 1) not in awn: col = R[2]
        ic.px(x, y, col)
    for x in range(3, 29):
        ic.px(x, 4, (255, 210, 210) if ((x - 1) // 5) % 2 == 0 else WHITE)
    # remate del toldo
    for x in range(4, 28):
        ic.px(x, 3, GOLD[1] if x < 16 else GOLD[2])
    ic.px(4, 3, GOLD[0]); ic.px(5, 3, GOLD[0])
    # mercancía: manzana, poción y gema
    apple = {(7, 17), (8, 17), (9, 17), (10, 17), (6, 18), (7, 18), (8, 18), (9, 18), (10, 18), (11, 18),
             (6, 19), (7, 19), (8, 19), (9, 19), (10, 19), (11, 19), (7, 20), (8, 20), (9, 20), (10, 20)}
    for x, y in apple:
        ic.px(x, y, RED[2] if x < 10 else RED[3])
    ic.px(7, 18, RED[1]); ic.px(7, 17, RED[1]); ic.px(8, 18, WHITE)
    ic.px(9, 16, WOOD[3]); ic.px(9, 15, WOOD[3]); ic.px(10, 15, GREEN[1]); ic.px(11, 15, GREEN[2])
    pot = {(15, 14), (16, 14), (15, 15), (16, 15), (14, 16), (15, 16), (16, 16), (17, 16),
           (13, 17), (14, 17), (15, 17), (16, 17), (17, 17), (18, 17), (13, 18), (14, 18), (15, 18), (16, 18), (17, 18), (18, 18),
           (13, 19), (14, 19), (15, 19), (16, 19), (17, 19), (18, 19), (14, 20), (15, 20), (16, 20), (17, 20)}
    for x, y in pot:
        ic.px(x, y, PURP[2] if x < 17 else PURP[3])
    ic.px(15, 14, WOOD[1]); ic.px(16, 14, WOOD[2])
    ic.px(15, 15, IRON[1]); ic.px(16, 15, IRON[2])
    ic.px(14, 17, WHITE); ic.px(14, 18, PURP[0]); ic.px(13, 18, PURP[1])
    gem = {(22, 16), (23, 16), (21, 17), (22, 17), (23, 17), (24, 17), (20, 18), (21, 18), (22, 18), (23, 18), (24, 18), (25, 18),
           (21, 19), (22, 19), (23, 19), (24, 19), (22, 20), (23, 20)}
    for x, y in gem:
        ic.px(x, y, TEAL[2])
    ic.px(22, 16, TEAL[0]); ic.px(21, 17, TEAL[1]); ic.px(22, 17, WHITE); ic.px(20, 18, TEAL[1])
    ic.px(24, 19, TEAL[3]); ic.px(23, 20, TEAL[3]); ic.px(25, 18, TEAL[3])
    # contorno de la mercancía sobre el fondo del puesto
    goods = apple | pot | gem | {(9, 16), (9, 15), (10, 15), (11, 15)}
    for x, y in ic.rect(5, 13, 25, 20):
        if (x, y) not in goods and any((x + dx, y + dy) in goods for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            ic.px(x, y, OUT)
    return ic.outline()


ICONOS = {'mercado': mercado, 'monedero': monedero, 'comunidad': comunidad, 'armario': armario, 'efectos': efectos, 'oficios': oficios, 'rango': rango, 'tienda': tienda, 'protecciones': protecciones}

if __name__ == '__main__':
    names = sys.argv[1:] or list(ICONOS)
    for name in names:
        im = ICONOS[name]().image()
        im.save(f'ic_{name}.png')
        review(im).save(f'rev_{name}.png')
