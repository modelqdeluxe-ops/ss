"""Texturas del TF Pad a partir de los dibujos de esta carpeta.

Todo va en la rejilla del marco: 1 píxel del dibujo = 4 píxeles de marco.png (1568x1003), es decir, el marco mide
392x251 «píxeles de pad». La pantalla del pad (zona azul) va de x 55 a 336 y de y 66 a 195.

    python3 tools/pad/build_pad.py            # escribe src/main/resources/assets/tfclient/textures/gui/pad/
    python3 tools/pad/build_pad.py --preview  # además, vistas previas en tools/pad/preview_*.png (no van al repo)

Qué sale:
- frame.png (el marco), tile.png / tile_h.png (ficha de 38 y su versión al pasar el ratón),
- icon_<app>.png (las 20 apps, 32x32),
- font.png + font.json (fuente pixel de 4 px con tildes y signos: la usa PadFont para todos los rótulos),
- coin, sun, moon, gear, heart, heart_off (iconos pequeños de la barra y de Comunidad).
Las posiciones las repite TFPadScreen.java: si cambian aquí, cambian allí.
"""
import json
import os
import shutil
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import iconos  # noqa: E402
import iconos2  # noqa: E402
import pico  # noqa: E402
from fuente4 import ACCENTS, G, glyph10  # noqa: E402
from kit import GOLD, PINK  # noqa: E402

OUT_DIR = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient',
                                        'textures', 'gui', 'pad'))
S = 4
NAVY = (24, 38, 92)
WHITE = (255, 255, 255)

# Las 20 apps, en el orden del pad (dos páginas de 5x2). Mismo orden y nombres que TFPadScreen.APPS.
APPS = [('oficios', 'OFICIOS'), ('misiones', 'MISIONES'), ('cazas', 'CAZAS'), ('tienda', 'TIENDA'), ('gts', 'GTS'),
        ('monedero', 'MONEDERO'), ('clanes', 'CLANES'), ('viajes', 'VIAJES'), ('explorar', 'EXPLORAR'), ('kits', 'KITS'),
        ('comunidad', 'COMUNIDAD'), ('camara', 'CÁMARA'), ('jugadores', 'JUGADORES'), ('ranking', 'RANKING'),
        ('titulos', 'TÍTULOS'),
        ('armario', 'ARMARIO'), ('efectos', 'EFECTOS'), ('rango', 'MI RANGO'), ('protecciones', 'PROTECCIÓN'),
        ('ayuda', 'AYUDA')]
DRAW = {'oficios': pico.pico, 'tienda': iconos.tienda, 'gts': iconos.mercado, 'monedero': iconos.monedero,
        'armario': iconos.armario, 'efectos': iconos.efectos, 'rango': iconos.rango, 'protecciones': iconos.protecciones}
DRAW.update(iconos2.ICONOS)

TILE = 38
COLS_X = [84, 140, 196, 252, 308]
ROWS_Y = [82, 136]


def img(w, h):
    return Image.new('RGBA', (w, h), (0, 0, 0, 0))


def put(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((x, y), tuple(c) + (255,) if len(c) == 3 else c)


def tile(hover=False):
    """Ficha de 38x38 con sombra de 2 debajo (38x40)."""
    n = TILE
    im = img(n, n + 2)
    for y in range(n):
        for x in range(n):
            cx, cy = min(x, n - 1 - x), min(y, n - 1 - y)
            if cx + cy < 2:
                continue
            border = cx == 0 or cy == 0 or cx + cy == 2
            top = y < n // 2
            if border:
                c = (186, 112, 20) if hover else NAVY
            elif cy == 1 and top or cx == 1 and x < n // 2 or (cx + cy == 3 and top):
                c = (255, 236, 150) if hover else WHITE
            elif cy <= 2 and not top:
                c = (236, 196, 120) if hover else (150, 206, 246)
            elif cx == 1 and x > n // 2:
                c = (250, 222, 160) if hover else (186, 228, 252)
            else:
                c = (255, 250, 232) if hover else (226, 246, 255)
            put(im, x, y, c)
    for x in range(2, n - 2):
        put(im, x, n, (40, 130, 210))
    for x in range(3, n - 3):
        put(im, x, n + 1, (66, 170, 236))
    return im


def font_atlas():
    """Atlas de la fuente pixel: celdas de 6x10, glifos en blanco (PadFont los tiñe)."""
    chars = ''.join(sorted(set(G) | set(ACCENTS), key=lambda c: (c == ' ', c)))
    cols = 16
    rows = (len(chars) + cols - 1) // cols
    im = img(cols * 6, rows * 10)
    widths = []
    for i, ch in enumerate(chars):
        g = glyph10(ch)
        widths.append(len(g[0]))
        ox, oy = (i % cols) * 6, (i // cols) * 10
        for y, row in enumerate(g):
            for x, c in enumerate(row):
                if c == '#':
                    put(im, ox + x, oy + y, WHITE)
    return im, {'chars': chars, 'widths': widths, 'cols': cols, 'cell': [6, 10]}


def small(rows, pal):
    h, w = len(rows), max(len(r) for r in rows)
    im = img(w, h)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in pal:
                put(im, x, y, pal[ch])
    return im


def mini_coin():
    im = img(11, 11)
    for y in range(11):
        for x in range(11):
            d = (x - 5) ** 2 + (y - 5) ** 2
            if d <= 16:
                s = x + y
                put(im, x, y, GOLD[1] if s < 8 else (GOLD[2] if s < 12 else GOLD[3]))
            elif d <= 27:
                put(im, x, y, NAVY)
    put(im, 4, 3, WHITE)
    for x, y in ((5, 4), (4, 5), (5, 5), (6, 5), (5, 6)):
        put(im, x, y, GOLD[3])
    return im


def outline(im):
    solid = {(x, y) for y in range(im.height) for x in range(im.width) if im.getpixel((x, y))[3]}
    for y in range(im.height):
        for x in range(im.width):
            if (x, y) not in solid and any((x + dx, y + dy) in solid for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                put(im, x, y, NAVY)
    return im


def sun():
    im = img(11, 11)
    core = {(x, y) for y in range(11) for x in range(11) if (x - 5) ** 2 + (y - 5) ** 2 <= 8}
    for x, y in {(5, 0), (5, 1), (5, 9), (5, 10), (0, 5), (1, 5), (9, 5), (10, 5), (2, 2), (8, 2), (2, 8), (8, 8)}:
        put(im, x, y, (255, 214, 80))
    for x, y in core:
        put(im, x, y, (255, 236, 120) if x + y < 10 else (250, 170, 40))
    put(im, 4, 4, WHITE)
    return outline(im)


def moon():
    im = img(11, 11)
    for y in range(11):
        for x in range(11):
            if (x - 5) ** 2 + (y - 5) ** 2 <= 16 and (x - 7) ** 2 + (y - 3) ** 2 > 10:
                put(im, x, y, (236, 240, 255) if x + y < 9 else (176, 190, 230))
    return outline(im)


def gear():
    rows = ["....##....",
            ".#.####.#.",
            ".########.",
            "..##..##..",
            "####..####",
            "####..####",
            "..##..##..",
            ".########.",
            ".#.####.#.",
            "....##...."]
    im = small(rows, {'#': (226, 234, 244)})
    for y in range(im.height):
        for x in range(im.width):
            if im.getpixel((x, y))[3] and x + y > 10:
                put(im, x, y, (176, 190, 214))
    return outline(im)


def heart(on=True):
    rows = [".##.##.", "#######", "#######", ".#####.", "..###..", "...#..."]
    if on:
        im = small(rows, {'#': PINK[2]})
        for x, y in ((1, 0), (0, 1), (1, 1)):
            put(im, x, y, PINK[0])
        put(im, 2, 1, WHITE)
    else:
        im = small(rows, {'#': (214, 226, 240)})
    return outline(im)


def build():
    if os.path.isdir(OUT_DIR):
        shutil.rmtree(OUT_DIR)
    os.makedirs(OUT_DIR)
    files = {'tile.png': tile(), 'tile_h.png': tile(True), 'coin.png': mini_coin(), 'sun.png': sun(),
             'moon.png': moon(), 'gear.png': gear(), 'heart.png': heart(True), 'heart_off.png': heart(False)}
    for key, _ in APPS:
        files[f'icon_{key}.png'] = DRAW[key]().image()
    atlas, meta = font_atlas()
    files['font.png'] = atlas
    for name, im in files.items():
        im.save(os.path.join(OUT_DIR, name))
    shutil.copyfile(os.path.join(HERE, 'marco.png'), os.path.join(OUT_DIR, 'frame.png'))
    with open(os.path.join(OUT_DIR, 'font.json'), 'w', encoding='utf-8') as f:
        json.dump(meta, f, ensure_ascii=False)
    return files, meta


# ---------------------------------------------------------------------------------------------------------------------
# Vistas previas: dibuja como TFPadScreen (misma fuente pixel, mismas posiciones)
# ---------------------------------------------------------------------------------------------------------------------

class Canvas:
    def __init__(self, files, meta):
        self.files, self.meta = files, meta
        self.art = img(392, 251)

    def blit(self, name, x, y):
        self.art.alpha_composite(self.files[name], (x, y))

    def width(self, text):
        w = 0
        for ch in text.upper():
            i = self.meta['chars'].find(ch)
            w += (self.meta['widths'][i] if i >= 0 else 2) + 1
        return max(0, w - 1)

    def text(self, text, x, y, color=WHITE, outline=NAVY):
        atlas = self.files['font.png']
        cols = self.meta['cols']
        pts = []
        cx = x
        for ch in text.upper():
            i = self.meta['chars'].find(ch)
            if i < 0:
                cx += 3
                continue
            w = self.meta['widths'][i]
            ox, oy = (i % cols) * 6, (i // cols) * 10
            for gy in range(10):
                for gx in range(w):
                    if atlas.getpixel((ox + gx, oy + gy))[3]:
                        pts.append((cx + gx, y + gy))
            cx += w + 1
        s = set(pts)
        if outline:
            for px, py in pts:
                for dx in (-1, 0, 1):
                    for dy in (-1, 0, 1, 2):
                        if (px + dx, py + dy) not in s:
                            put(self.art, px + dx, py + dy, outline)
        for px, py in pts:
            put(self.art, px, py, color)

    def fill(self, x0, y0, x1, y1, c):
        for y in range(y0, y1):
            for x in range(x0, x1):
                put(self.art, x, y, c)

    def compose(self):
        frame = Image.open(os.path.join(HERE, 'marco.png')).convert('RGBA')
        big = self.art.resize((392 * S, 251 * S), Image.NEAREST).crop((0, 0, frame.width, frame.height))
        frame.alpha_composite(big)
        return frame


def status_bar(c, title):
    """Barra de arriba: título a la izquierda; hora, monedas y ajustes a la derecha (como TFPadScreen.drawStatus)."""
    c.text(title, 96, 67)
    right = 330
    c.blit('gear.png', right - 10, 67)
    right -= 16
    coins = '1.250'
    w = c.width(coins)
    c.text(coins, right - w, 68, (255, 230, 128))
    right -= w + 14
    c.blit('coin.png', right, 67)
    right -= 10
    t = '18:30'
    w = c.width(t)
    c.text(t, right - w, 68)
    right -= w + 14
    c.blit('sun.png', right, 67)


def preview_home(files, meta, page, hover=None):
    c = Canvas(files, meta)
    status_bar(c, 'TF PAD')
    for i, (key, name) in enumerate(APPS[page * 10:page * 10 + 10]):
        cx, ty = COLS_X[i % 5], ROWS_Y[i // 5]
        c.blit('tile_h.png' if key == hover else 'tile.png', cx - TILE // 2, ty)
        c.blit(f'icon_{key}.png', cx - 16, ty + 3)
        w = c.width(name)
        c.text(name, cx - w // 2, ty + 41)
    # puntos de página
    x = 196 - (6 + 12 + 3) // 2
    for p in range(2):
        w = 12 if p == page else 6
        c.fill(x, 189, x + w, 194, NAVY)
        c.fill(x + 1, 190, x + w - 1, 193, (246, 182, 40) if p == page else (150, 206, 246))
        x += w + 3
    return c.compose()


if __name__ == '__main__':
    files, meta = build()
    print(f'{len(files) + 2} archivos en {OUT_DIR}')
    if '--preview' in sys.argv:
        preview_home(files, meta, 0, hover='misiones').save(os.path.join(HERE, 'preview_home1.png'))
        preview_home(files, meta, 1).save(os.path.join(HERE, 'preview_home2.png'))
