"""Texturas del TF Pad a partir de los dibujos de esta carpeta.

Todo va en la rejilla del marco: 1 píxel del dibujo = 4 píxeles de marco.png (1568x1003), es decir, el marco mide
392x251 «píxeles de pad». La pantalla del pad (zona azul) va de x 55 a 336 y de y 66 a 195.

    python3 tools/pad/build_pad.py            # escribe src/main/resources/assets/tfclient/textures/gui/pad/
    python3 tools/pad/build_pad.py --preview  # además, vistas previas en tools/pad/preview_*.png (no van al repo)

Qué sale:
- frame.png (el marco), tile.png / tile_h.png (ficha de 40 y su versión al pasar el ratón),
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
import iconos3  # noqa: E402
import iconos4  # noqa: E402
import pico  # noqa: E402
from fuente4 import ACCENTS, G, glyph10  # noqa: E402
from kit import GOLD, PINK  # noqa: E402

OUT_DIR = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient',
                                        'textures', 'gui', 'pad'))
S = 4
NAVY = (24, 38, 92)
WHITE = (255, 255, 255)

# Las 20 apps, en el orden del pad (dos páginas de 5x2). Mismo orden y nombres que TFPadScreen.APPS.
APPS = [('musica', 'MÚSICA'), ('oficios', 'OFICIOS'), ('misiones', 'MISIONES'), ('pase', 'TF PASS'),
        ('gachapon', 'GACHAPÓN'), ('cazas', 'CAZAS'), ('recompensas', 'RECOMPENSAS'), ('tienda', 'TIENDA'), ('gts', 'GTS'), ('monedero', 'MONEDERO'), ('viajes', 'VIAJES'),
        ('hogares', 'HOGARES'), ('kits', 'KITS'), ('protecciones', 'PROTECCIÓN'), ('clanes', 'CLANES'),
        ('jugadores', 'JUGADORES'), ('comunidad', 'COMUNIDAD'), ('camara', 'CÁMARA'), ('ranking', 'RANKING'),
        ('armario', 'ARMARIO'), ('efectos', 'EFECTOS'), ('rango', 'MI RANGO'), ('web', 'WEB')]
DRAW = {'oficios': pico.pico, 'tienda': iconos.tienda, 'gts': iconos.mercado, 'monedero': iconos.monedero,
        'armario': iconos.armario, 'efectos': iconos.efectos, 'rango': iconos.rango, 'protecciones': iconos.protecciones}
DRAW.update(iconos2.ICONOS)
DRAW.update(iconos3.ICONOS)  # 1.3.26: oficios (pico grueso), recompensas y web
DRAW.update(iconos4.ICONOS)  # 1.3.38: pico de Minecraft, TF Pass, Gachapón, mapa, destello y pergamino

TILE = 40
COLS_X = [84, 140, 196, 252, 308]
ROWS_Y = [82, 136]


def img(w, h):
    return Image.new('RGBA', (w, h), (0, 0, 0, 0))


def put(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((x, y), tuple(c) + (255,) if len(c) == 3 else c)


def tile(hover=False):
    """Ficha de app estilo RPG (40x40 + 2 de sombra = 40x42): marco de oro biselado (luz arriba a la izquierda), engaste
    oscuro, cara de cristal azul con degradado y brillo en diagonal, y una gema azul en cada esquina como las del marco
    del pad. Al pasar el ratón el oro se aclara, la cara se enciende y las gemas brillan."""
    n = TILE
    im = img(n, n + 2)
    gold = [(255, 250, 214), (255, 230, 120), (246, 186, 48), (204, 128, 26), (150, 86, 20)]
    if hover:
        gold = [(255, 255, 240), (255, 244, 170), (255, 214, 96), (232, 160, 40), (176, 108, 26)]
    top_face = (96, 176, 255) if hover else (70, 146, 236)
    bot_face = (40, 102, 214) if hover else (30, 76, 178)
    for y in range(n):
        for x in range(n):
            cx, cy = min(x, n - 1 - x), min(y, n - 1 - y)
            d = min(cx, cy)
            if cx + cy < 3:
                continue  # esquinas recortadas
            if d == 0 or cx + cy == 3:
                c = NAVY
            elif d <= 2 and cx + cy > 4 or (d <= 2):
                # oro: claro arriba/izquierda, oscuro abajo/derecha, con una línea media
                left_top = (y < n - 1 - y and cy <= cx) or (x < n - 1 - x and cx < cy)
                if d == 1:
                    c = gold[1] if left_top else gold[3]
                else:
                    c = gold[2] if left_top else gold[4]
                if d == 1 and left_top and (x < 8 or y < 8) and cx + cy < 14:
                    c = gold[0]
            elif d == 3:
                c = (34, 40, 96) if not hover else (40, 60, 130)  # engaste
            else:
                t = (y - 4) / (n - 9)
                c = tuple(int(top_face[i] * (1 - t) + bot_face[i] * t) for i in range(3))
                if d == 4 and y < n // 2 and cy == 4:
                    c = (150, 210, 255) if not hover else (200, 236, 255)  # luz interior arriba
                elif d == 4 and y > n // 2 and cy == 4:
                    c = (22, 56, 140) if not hover else (30, 80, 180)  # sombra interior abajo
                # brillo en diagonal
                k = x + y
                if 14 <= k <= 17 and x < 22 and y < 22:
                    c = tuple(min(255, v + (34 if hover else 22)) for v in c)
            put(im, x, y, c)
    # tachuelas en las esquinas: rombo de oro con una gema azul en el centro (como las del marco del pad)
    gem = [(230, 255, 255), (110, 220, 255), (30, 150, 236)] if not hover else [(255, 255, 255), (170, 244, 255), (70, 200, 255)]
    stud = ["..O..",
            ".OGO.",
            "OGgGO",
            ".OHO.",
            "..O.."]
    for ox, oy in ((2, 2), (n - 7, 2), (2, n - 7), (n - 7, n - 7)):
        for dy, row in enumerate(stud):
            for dx, ch in enumerate(row):
                c = {'O': NAVY, 'G': gold[1], 'H': gold[3], 'g': gem[1]}.get(ch)
                if c:
                    put(im, ox + dx, oy + dy, c)
        put(im, ox + 2, oy + 1, gold[0])
        put(im, ox + 3, oy + 2, gold[3])
        put(im, ox + 1, oy + 2, gold[1])
        put(im, ox + 2, oy + 2, gem[0] if hover else gem[1])
    # sombra debajo
    for x in range(3, n - 3):
        put(im, x, n, (40, 130, 210))
    for x in range(4, n - 4):
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


def verde(im):
    """La moneda verde (1.3.38): la misma moneda de oro pintada en esmeralda. Cada píxel de oro toma el tono de una
    rampa esmeralda según su luz (misma luz, mismo dibujo y el mismo contraste que la de oro); el contorno azul noche y
    los blancos se quedan."""
    import colorsys
    ramp = [(236, 255, 214), (150, 248, 150), (64, 222, 112), (30, 176, 96), (18, 128, 84), (14, 92, 70), (10, 60, 52)]
    out = im.convert('RGBA').copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if not a:
                continue
            h, sat, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if sat < 0.25 or not (0.0 <= h <= 0.2):
                continue
            lum = (0.3 * r + 0.59 * g + 0.11 * b) / 255  # luz percibida, de 0 a 1
            f = max(0.0, min(1.0, (lum - 0.15) / 0.8)) ** 1.7   # 1 = lo más claro (el oro es muy luminoso: se baja)
            k = (1 - f) * (len(ramp) - 1)
            i = int(k)
            t = k - i
            c0, c1 = ramp[i], ramp[min(i + 1, len(ramp) - 1)]
            px[x, y] = tuple(round(c0[j] * (1 - t) + c1[j] * t) for j in range(3)) + (a,)
    return out


ITEM_DIR = os.path.normpath(os.path.join(OUT_DIR, '..', '..', 'item'))


def pattern():
    """Textura de las ventanas (1.3.28): rombos de cristal tallado muy suaves (se repite en mosaico de 16x16)."""
    im = img(16, 16)
    for y in range(16):
        for x in range(16):
            if (x + y) % 16 == 0 or (x - y) % 16 == 0:
                put(im, x, y, (52, 110, 190, 13))
    for x, y in ((0, 0), (8, 8)):
        put(im, x, y, (52, 110, 190, 30))
    for x, y in ((8, 0), (0, 8)):
        put(im, x, y, (255, 255, 255, 120))
    return im


def coin_small():
    """Moneda de 8x8 para las etiquetas de precio (1.3.28)."""
    rows = ["..####..",
            ".#hhgg#.",
            "#hhggggd",
            "#hgg+ggd",
            "#gg+++gd",
            "#ggg+ggd",
            ".#ggggd.",
            "..dddd.."]
    pal = {'#': (138, 86, 0, 255), 'h': (255, 240, 170, 255), 'g': (246, 182, 40, 255), 'd': (192, 120, 24, 255),
           '+': (255, 224, 120, 255)}
    return small(rows, pal)


def glow():
    """Luz suave detrás de los objetos en las vitrinas y cabeceras (1.3.28): blanco que se apaga hacia fuera."""
    im = img(48, 48)
    for y in range(48):
        for x in range(48):
            d = (((x - 23.5) / 24) ** 2 + ((y - 23.5) / 24) ** 2) ** 0.5
            if d < 1:
                a = int(200 * (1 - d) ** 1.6)
                if a > 0:
                    put(im, x, y, (255, 255, 255, a))
    return im


def floor_shadow():
    """Sombra ovalada bajo los objetos de las vitrinas (1.3.28)."""
    im = img(32, 6)
    for y in range(6):
        for x in range(32):
            d = (((x - 15.5) / 16) ** 2 + ((y - 2.5) / 3) ** 2) ** 0.5
            if d < 1:
                put(im, x, y, (24, 38, 92, int(70 * (1 - d))))
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
    """Corazón de like de 9x8 (11x10 con el contorno): lóbulos redondos, punta centrada, brillo arriba a la izquierda."""
    rows = [".##...##.",
            "####.####",
            "#########",
            "#########",
            ".#######.",
            "..#####..",
            "...###...",
            "....#...."]
    if on:
        im = small(rows, {'#': PINK[2]})
        for x, y in ((1, 0), (2, 0), (0, 1), (6, 0), (7, 0), (5, 1)):
            put(im, x, y, PINK[1])
        for x, y in ((1, 1), (1, 2)):
            put(im, x, y, WHITE)
        for x, y in ((8, 2), (8, 3), (7, 4), (6, 5), (5, 6), (4, 7), (7, 3), (6, 4)):
            put(im, x, y, PINK[3])
    else:
        im = small(rows, {'#': (230, 238, 248)})
        for x, y in ((8, 2), (8, 3), (7, 4), (6, 5), (5, 6), (4, 7)):
            put(im, x, y, (190, 204, 224))
    # un píxel de margen alrededor para que el contorno quepa entero
    framed = img(im.width + 2, im.height + 2)
    framed.alpha_composite(im, (1, 1))
    return outline(framed)


def emoji(kind):
    """Reacciones de Comunidad (1.3.31): los emojis de Noto Color Emoji a 44x44 (los hace make_emojis.py en emoji/)."""
    return Image.open(os.path.join(HERE, 'emoji', kind + '.png')).convert('RGBA')


def normalize(im, box=30):
    """Todos los iconos igual (1.3.38): caben en 30x30 (1 px de aire hasta el engaste de la ficha por cada lado, nunca
    pegados a él) y van centrados por su caja. Si uno mide más, se le quitan filas/columnas repetidas (las que son
    iguales a su vecina, las que menos se notan en pixel art), empezando por las del centro."""
    import numpy as np
    a = np.array(im.convert('RGBA'))
    ys, xs = np.where(a[:, :, 3] > 0)
    a = a[ys.min():ys.max() + 1, xs.min():xs.max() + 1]

    def shrink(arr, axis):
        while arr.shape[axis] > box:
            n = arr.shape[axis]
            diffs = []
            for i in range(1, n - 1):
                s0 = np.take(arr, i, axis=axis).astype(int)
                s1 = np.take(arr, i + 1, axis=axis).astype(int)
                diffs.append((int(np.abs(s0 - s1).sum()), abs(i - n / 2), i))
            _, _, i = min(diffs)
            arr = np.delete(arr, i, axis=axis)
        return arr

    a = shrink(shrink(a, 1), 0)
    out = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    h, w = a.shape[:2]
    out.alpha_composite(Image.fromarray(a), ((32 - w) // 2, (32 - h) // 2))
    return out


def build():
    if os.path.isdir(OUT_DIR):
        shutil.rmtree(OUT_DIR)
    os.makedirs(OUT_DIR)
    files = {'tile.png': tile(), 'tile_h.png': tile(True), 'coin.png': mini_coin(), 'sun.png': sun(),
             'moon.png': moon(), 'gear.png': gear(), 'heart.png': heart(True), 'heart_off.png': heart(False),
             'pattern.png': pattern(), 'glow.png': glow(), 'floor.png': floor_shadow(),
             'coin_s.png': coin_small()}
    files['coin_green.png'] = verde(files['coin.png'])
    files['coin_green_s.png'] = verde(files['coin_s.png'])
    for k in ('risa', 'wow', 'triste', 'fuego', 'top'):
        files[f'emo_{k}.png'] = emoji(k)
    for key, _ in APPS:
        files[f'icon_{key}.png'] = normalize(DRAW[key]().image())
    files['icon_admin.png'] = normalize(iconos2.admin().image())
    atlas, meta = font_atlas()
    files['font.png'] = atlas
    for name, im in files.items():
        im.save(os.path.join(OUT_DIR, name))
    shutil.copyfile(os.path.join(HERE, 'marco.png'), os.path.join(OUT_DIR, 'frame.png'))
    # el objeto de la moneda verde, sacado de la Fantastic Coin de oro (textures/item/fantastic_coin.png)
    verde(Image.open(os.path.join(ITEM_DIR, 'fantastic_coin.png'))).save(os.path.join(ITEM_DIR, 'fantastic_coin_green.png'))
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
