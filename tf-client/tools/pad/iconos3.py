"""Iconos de la 1.3.26: Oficios (pico grueso, a juego con el cofre, la casa o la moneda), Recompensas (regalo) y
Web (globo terráqueo). Dibujados con las reglas del pad: contorno oscuro, rampas de 5 tonos con luz arriba a la
izquierda y nada pegado al borde. Para revisar: python3 tools/pad/iconos3.py <carpeta> (los guarda ampliados)."""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from kit import BLUE, DIAM, GOLD, GREEN, RED, WOOD, Icon, review  # noqa: E402
from iconos2 import shade  # noqa: E402

OCEAN = [(200, 244, 255), (110, 206, 255), (52, 150, 250), (36, 98, 214), (30, 64, 160)]
LAND = [(214, 255, 170), (130, 232, 96), (64, 190, 74), (34, 140, 64), (26, 98, 58)]
RIBBON = [(255, 226, 236), (255, 120, 150), (230, 50, 86), (170, 26, 64), (116, 18, 50)]
BOX = [(200, 236, 255), (120, 196, 255), (60, 140, 236), (38, 92, 196), (32, 60, 150)]


def oficios():
    """Pico de diamante grueso: cabeza en media luna (4 px en el centro, puntas finas que se curvan hacia el mango),
    abrazadera de oro y mango de madera de 3 px, en diagonal como el de Minecraft."""
    ic = Icon()
    cx, cy = 19.0, 12.5          # donde se cruzan cabeza y mango
    ux, uy = 1 / math.sqrt(2), 1 / math.sqrt(2)     # eje de la cabeza (de arriba-izquierda a abajo-derecha)
    vx, vy = 1 / math.sqrt(2), -1 / math.sqrt(2)    # eje del mango (hacia fuera, arriba-derecha)
    L, T, B = 14.6, 5.6, 4.8

    def head(x, y):
        t = (x - cx) * ux + (y - cy) * uy
        s = (x - cx) * vx + (y - cy) * vy
        if abs(t) > L:
            return False
        k = abs(t) / L
        bend = -B * k * k
        th = T * (1 - k ** 1.6) + 1.0
        return bend - th / 2 <= s - 0.6 <= bend + th / 2

    def handle(x, y):
        t = (x - cx) * ux + (y - cy) * uy
        s = (x - cx) * vx + (y - cy) * vy
        return -19.5 <= s <= -1 and abs(t) <= 1.55

    hd = ic.mask(handle)
    shade(ic, hd, WOOD)
    # vetas oscuras en el mango
    for x, y in hd:
        s = (x + 0.5 - cx) * vx + (y + 0.5 - cy) * vy
        if int(-s) % 5 == 2 and (x, y) in hd:
            ic.px(x, y, WOOD[3])
    m = ic.mask(head)
    # tono por la posición a lo ancho de la cabeza: filo de fuera claro, centro medio, lado del mango oscuro
    for x, y in m:
        t = (x + 0.5 - cx) * ux + (y + 0.5 - cy) * uy
        s = (x + 0.5 - cx) * vx + (y + 0.5 - cy) * vy
        k = abs(t) / L
        bend = -B * k * k
        th = T * (1 - k ** 1.6) + 1.0
        f = (s - 0.6 - (bend - th / 2)) / max(0.1, th)   # 0 = lado del mango, 1 = filo de fuera
        ic.px(x, y, DIAM[1] if f > 0.72 else DIAM[2] if f > 0.36 else DIAM[3] if f > 0.12 else DIAM[4])
    # brillos sobre el filo, a un lado y al otro del centro
    for x, y in ((14, 5), (15, 5), (16, 6), (22, 8), (23, 9), (24, 10)):
        if (x, y) in m:
            ic.px(x, y, DIAM[0])
    # abrazadera de oro donde se unen
    band = ic.mask(lambda x, y: abs((x - cx) * vx + (y - cy) * vy + 2.2) <= 1.3 and abs((x - cx) * ux + (y - cy) * uy) <= 2.4)
    shade(ic, band, GOLD)
    return ic.outline()


def recompensas():
    """Regalo: caja azul con cinta roja en cruz, tapa con su luz y un lazo grande arriba."""
    ic = Icon()
    body = ic.rect(6, 15, 25, 28)
    shade(ic, body, BOX)
    lid = ic.rect(4, 11, 27, 15)
    shade(ic, lid, BOX)
    for x in range(5, 27):  # sombra de la tapa sobre la caja
        ic.px(x, 16, BOX[3])
    # cinta vertical y horizontal (sobre la tapa)
    rib = ic.rect(14, 11, 17, 28)
    shade(ic, rib, RIBBON)
    for y in range(12, 28):
        ic.px(14, y, RIBBON[1])
    # lazo: dos bucles redondos con su agujero oscuro, el nudo y las colas
    loops = ic.mask(lambda x, y: ((x - 11) / 4.6) ** 2 + ((y - 7.5) / 3.4) ** 2 <= 1) | \
        ic.mask(lambda x, y: ((x - 21) / 4.6) ** 2 + ((y - 7.5) / 3.4) ** 2 <= 1)
    shade(ic, loops, RIBBON)
    for x, y in ((10, 7), (11, 7), (11, 8), (21, 7), (20, 7), (20, 8)):
        ic.px(x, y, RIBBON[4])
    for x, y in ((8, 6), (9, 5), (19, 5), (18, 6)):
        ic.px(x, y, RIBBON[0])
    knot = ic.rect(14, 6, 17, 10)
    shade(ic, knot, RIBBON)
    ic.px(15, 7, RIBBON[0])
    # cinta horizontal sobre la tapa
    band = ic.rect(4, 12, 27, 13)
    for x, y in band:
        if not 14 <= x <= 17:
            ic.px(x, y, RIBBON[2] if y == 12 else RIBBON[3])
    # brillo de la caja
    for y in range(18, 26):
        ic.px(8, y, BOX[1])
    ic.px(8, 17, BOX[0])
    return ic.outline()


def web():
    """Globo terráqueo: océano azul con continentes verdes, meridianos y paralelos suaves y un brillo arriba."""
    ic = Icon()
    R, cx, cy = 13.2, 16.0, 16.0
    ball = ic.disc(cx, cy, R)
    # continentes (formas hechas a mano sobre la esfera)
    land = set()
    land |= ic.poly([(8, 7), (14, 5), (17, 7), (15, 11), (12, 12), (11, 15), (7, 14), (5, 11)])
    land |= ic.poly([(12, 16), (16, 15), (17, 19), (15, 24), (13, 27), (11, 23), (11, 19)])
    land |= ic.poly([(19, 8), (25, 8), (28, 12), (27, 16), (23, 15), (20, 12)])
    land |= ic.poly([(21, 19), (26, 18), (27, 22), (24, 25), (20, 23)])
    land &= ball
    sea = ball - land
    for x, y in sea:
        d = math.hypot(x + 0.5 - (cx - 4), y + 0.5 - (cy - 5)) / (R * 1.6)
        ic.px(x, y, OCEAN[1] if d < 0.32 else OCEAN[2] if d < 0.62 else OCEAN[3] if d < 0.88 else OCEAN[4])
    for x, y in land:
        d = math.hypot(x + 0.5 - (cx - 4), y + 0.5 - (cy - 5)) / (R * 1.6)
        ic.px(x, y, LAND[1] if d < 0.38 else LAND[2] if d < 0.7 else LAND[3])
    # brillo arriba a la izquierda
    for x, y in ((9, 6), (10, 6), (8, 7), (7, 8), (7, 9), (11, 5)):
        if (x, y) in sea:
            ic.px(x, y, OCEAN[0])
    # el puntero del ratón encima (es la página web): flecha blanca con su contorno, abajo a la derecha
    arrow = ['X.........', 'XX........', 'XWX.......', 'XWWX......', 'XWWWX.....', 'XWWWWX....', 'XWWWWWX...',
             'XWWWWWWX..', 'XWWWWXXXX.', 'XWWXWX....', 'XWX.XWX...', 'XX..XWX...', 'X....XX...']
    ox, oy = 19, 16
    for j, row in enumerate(arrow):
        for i, ch in enumerate(row):
            if ch == 'X':
                ic.px(ox + i, oy + j, (30, 24, 56))
            elif ch == 'W':
                ic.px(ox + i, oy + j, (255, 255, 255) if i < 3 else (220, 232, 248))
    return ic.outline()


ICONOS = {'oficios': oficios, 'recompensas': recompensas, 'web': web}

if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else '.'
    for name, fn in ICONOS.items():
        review(fn().image()).save(os.path.join(out, f'rev_{name}.png'))
    print('ok')
