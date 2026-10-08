"""Kit de pixel art para el pad: máscaras, biselado y contorno, sin suavizado."""
import math
from PIL import Image

N = 32
OUT = (30, 24, 56)

# Rampas: [brillo, claro, medio, oscuro, profundo]
GOLD = [(255, 250, 200), (255, 226, 96), (246, 182, 40), (206, 128, 24), (150, 84, 22)]
IRON = [(255, 255, 255), (226, 234, 244), (186, 198, 216), (136, 148, 172), (96, 104, 132)]
DIAM = [(236, 255, 255), (150, 248, 255), (64, 214, 240), (32, 150, 210), (26, 96, 168)]
WOOD = [(240, 196, 130), (214, 154, 88), (176, 112, 56), (132, 78, 38), (96, 54, 30)]
RED = [(255, 200, 200), (255, 110, 110), (232, 52, 70), (170, 28, 56), (110, 20, 48)]
BLUE = [(210, 246, 255), (110, 210, 255), (52, 150, 250), (36, 98, 214), (34, 60, 160)]
PURP = [(250, 214, 255), (220, 140, 255), (174, 84, 240), (122, 50, 196), (84, 34, 150)]
GREEN = [(220, 255, 200), (130, 240, 110), (64, 200, 80), (30, 140, 70), (24, 96, 60)]
TEAL = [(220, 255, 244), (120, 250, 210), (40, 216, 176), (20, 156, 150), (20, 104, 120)]
PINK = [(255, 230, 246), (255, 160, 220), (246, 96, 190), (196, 56, 150), (140, 36, 120)]
WHITE = (255, 255, 255)


class Icon:
    def __init__(self, n=N):
        self.n = n
        self.c = [[None] * n for _ in range(n)]

    # ---- máscaras -------------------------------------------------------
    def mask(self, fn):
        return {(x, y) for y in range(self.n) for x in range(self.n) if fn(x + 0.5, y + 0.5)}

    def rect(self, x0, y0, x1, y1):
        return {(x, y) for y in range(y0, y1 + 1) for x in range(x0, x1 + 1)}

    def poly(self, pts):
        def inside(px, py):
            ins = False
            j = len(pts) - 1
            for i in range(len(pts)):
                xi, yi = pts[i]
                xj, yj = pts[j]
                if (yi > py) != (yj > py) and px < (xj - xi) * (py - yi) / (yj - yi) + xi:
                    ins = not ins
                j = i
            return ins
        return self.mask(inside)

    def disc(self, cx, cy, r):
        return self.mask(lambda x, y: (x - cx) ** 2 + (y - cy) ** 2 <= r * r)

    # ---- pintura --------------------------------------------------------
    def fill(self, m, col):
        for x, y in m:
            if 0 <= x < self.n and 0 <= y < self.n:
                self.c[y][x] = col

    def bevel(self, m, ramp, hi=True, depth=1):
        """Relleno con bisel: arriba/izquierda claro, abajo/derecha oscuro."""
        for x, y in m:
            col = ramp[2]
            up = (x, y - 1) not in m
            lf = (x - 1, y) not in m
            dn = (x, y + 1) not in m
            rt = (x + 1, y) not in m
            if dn or rt:
                col = ramp[3]
                if dn and rt:
                    col = ramp[4]
            elif up or lf:
                col = ramp[1]
                if hi and up and lf:
                    col = ramp[0]
            elif depth > 1 and ((x, y + 2) not in m or (x + 2, y) not in m):
                col = ramp[3]
            self.fill({(x, y)}, col)

    def px(self, x, y, col):
        if 0 <= x < self.n and 0 <= y < self.n:
            self.c[y][x] = col

    def outline(self, col=OUT):
        solid = {(x, y) for y in range(self.n) for x in range(self.n) if self.c[y][x]}
        for y in range(self.n):
            for x in range(self.n):
                if (x, y) in solid:
                    continue
                if any((x + dx, y + dy) in solid for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    self.c[y][x] = col
        return self

    def image(self):
        im = Image.new('RGBA', (self.n, self.n), (0, 0, 0, 0))
        for y in range(self.n):
            for x in range(self.n):
                if self.c[y][x]:
                    im.putpixel((x, y), tuple(self.c[y][x]) + (255,))
        return im


def line_mask(ic, x0, y0, x1, y1, w=1):
    pts = set()
    steps = max(abs(x1 - x0), abs(y1 - y0)) * 2 + 1
    for i in range(steps + 1):
        t = i / steps
        x = round(x0 + (x1 - x0) * t)
        y = round(y0 + (y1 - y0) * t)
        for ox in range(w):
            for oy in range(w):
                pts.add((x + ox, y + oy))
    return pts


def ascii_icon(rows, pal):
    """Icono desde una rejilla de texto; '.' es transparente."""
    ic = Icon(len(rows))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                ic.px(x, y, pal[ch])
    return ic


def zoom(im, k):
    return im.resize((im.width * k, im.height * k), Image.NEAREST)


def review(im, k=12, bg=(87, 210, 253)):
    """Vista de revisión: icono ampliado sobre el color de la pantalla del pad, con rejilla."""
    z = Image.new('RGBA', (im.width * k, im.height * k), bg + (255,))
    z.alpha_composite(zoom(im, k))
    g = z.load()
    for y in range(z.height):
        for x in range(z.width):
            if x % k == 0 or y % k == 0:
                r, gg, b, a = g[x, y]
                g[x, y] = (r * 7 // 8, gg * 7 // 8, b * 7 // 8, 255)
    return z
