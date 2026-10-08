"""Texturas del pad (TF Pad) a partir de los dibujos de esta carpeta.

Todo va en la rejilla del marco: 1 píxel del dibujo = 4 píxeles de marco.png (1568x1003), es decir, el marco
mide 392x251 «píxeles de pad». La pantalla del pad (zona azul) va de x 55 a 336 y de y 66 a 195.

    python3 tools/pad/build_pad.py            # escribe src/main/resources/assets/tfclient/textures/gui/pad/
    python3 tools/pad/build_pad.py --preview  # además, vistas previas en tools/pad/preview_*.png

Las posiciones las repite TFPadScreen.java: si cambian aquí, cambian allí.
"""
import os
import shutil
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import iconos  # noqa: E402
from fuente4 import text_pixels  # noqa: E402
from kit import GOLD  # noqa: E402

OUT_DIR = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient',
                                        'textures', 'gui', 'pad'))
S = 4
NAVY = (24, 38, 92)
WHITE = (255, 255, 255)

# id de la app, nombre en el pad
APPS = [('oficios', 'OFICIOS'), ('protecciones', 'PROTECCIONES'), ('tienda', 'TIENDA'), ('gts', 'GTS'),
        ('monedero', 'MONEDERO'), ('armario', 'ARMARIO'), ('efectos', 'EFECTOS'), ('rango', 'MI RANGO'),
        ('comunidad', 'COMUNIDAD')]
DRAW = {'oficios': 'oficios', 'protecciones': 'protecciones', 'tienda': 'tienda', 'gts': 'mercado',
        'monedero': 'monedero', 'armario': 'armario', 'efectos': 'efectos', 'rango': 'rango', 'comunidad': 'comunidad'}
# botones de las páginas de dentro
BUTTONS = {'oficios': 'OFICIOS', 'tienda': 'TIENDA', 'gts': 'GTS', 'discord': 'DISCORD', 'whatsapp': 'WHATSAPP',
           'web': 'PAGINA WEB', 'abrirweb': 'ABRIR EN LA WEB', 'rangos': 'VER RANGOS'}

# rejilla de la portada: centros de las columnas y fila de arriba de cada ficha
ROW1_X = [84, 140, 196, 252, 308]
ROW2_X = [112, 168, 224, 280]
ROW_Y = [80, 138]
TILE = 40
PANEL = (62, 90, 268, 100)   # x, y, ancho, alto
BACK = (70, 69)


def img(w, h):
    return Image.new('RGBA', (w, h), (0, 0, 0, 0))


def put(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((x, y), tuple(c) + (255,) if len(c) == 3 else c)


def rounded(w, h, x, y, r=2):
    cx, cy = min(x, w - 1 - x), min(y, h - 1 - y)
    return cx + cy >= r, cx == 0 or cy == 0 or cx + cy == r, cx, cy


def tile(hover=False):
    """Ficha de 40x40 con sombra de 2 debajo (40x42)."""
    n = TILE
    im = img(n, n + 2)
    for y in range(n):
        for x in range(n):
            inside, border, cx, cy = rounded(n, n, x, y)
            if not inside:
                continue
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


def label(text, color=WHITE, shade=(214, 240, 255)):
    """Texto en la fuente de 4 px con contorno azul marino (1 px a los lados, 2 debajo)."""
    w, pts = text_pixels(text)
    im = img(w + 2, 10)
    for x, y in pts:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1, 2):
                if (x + dx, y + dy) not in pts:
                    put(im, x + 1 + dx, y + 1 + dy, NAVY)
    for x, y in pts:
        put(im, x + 1, y + 1, color if y < 4 else shade)
    return im


def button(text, hover=False):
    lab = label(text)
    w = max(56, lab.width + 14)
    h = 18
    im = img(w, h + 2)
    for y in range(h):
        for x in range(w):
            inside, border, cx, cy = rounded(w, h, x, y)
            if not inside:
                continue
            if border:
                c = NAVY
            elif cy == 1 and y < h // 2:
                c = (255, 236, 150) if hover else (150, 214, 255)
            elif cy <= 2 and y > h // 2:
                c = (186, 112, 20) if hover else (24, 84, 190)
            else:
                c = (246, 182, 40) if hover else (52, 150, 250)
            put(im, x, y, c)
    for x in range(2, w - 2):
        put(im, x, h, (40, 130, 210))
        if 3 <= x < w - 3:
            put(im, x, h + 1, (66, 170, 236))
    im.alpha_composite(lab, ((w - lab.width) // 2, 4))
    return im


def back(hover=False):
    im = img(16, 18)
    for y in range(16):
        for x in range(16):
            inside, border, cx, cy = rounded(16, 16, x, y)
            if not inside:
                continue
            c = NAVY if border else ((246, 182, 40) if hover else (52, 150, 250))
            if not border and cy == 1 and y < 8:
                c = (255, 236, 150) if hover else (150, 214, 255)
            put(im, x, y, c)
    for x in range(2, 14):
        put(im, x, 16, (40, 130, 210))
    # flecha ◀
    arrow = [(4, 7), (4, 8), (5, 6), (5, 7), (5, 8), (5, 9), (6, 5), (6, 6), (6, 7), (6, 8), (6, 9), (6, 10),
             (7, 7), (7, 8), (8, 7), (8, 8), (9, 7), (9, 8), (10, 7), (10, 8), (11, 7), (11, 8)]
    for x, y in arrow:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if (x + dx, y + dy) not in arrow and im.getpixel((x + dx, y + dy))[3]:
                    put(im, x + dx, y + dy, NAVY)
    for x, y in arrow:
        put(im, x, y, WHITE)
    return im


def panel():
    x0, y0, w, h = PANEL
    im = img(w, h + 2)
    for y in range(h):
        for x in range(w):
            inside, border, cx, cy = rounded(w, h, x, y, 3)
            if not inside:
                continue
            if border:
                c = NAVY
            elif cy == 1 and y < h // 2:
                c = WHITE
            elif cy <= 2 and y > h // 2:
                c = (170, 216, 248)
            else:
                c = (232, 248, 255)
            put(im, x, y, c)
    for x in range(3, w - 3):
        put(im, x, h, (40, 130, 210))
        put(im, x, h + 1, (66, 170, 236))
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


def sun():
    im = img(11, 11)
    core = {(x, y) for y in range(11) for x in range(11) if (x - 5) ** 2 + (y - 5) ** 2 <= 8}
    rays = {(5, 0), (5, 1), (5, 9), (5, 10), (0, 5), (1, 5), (9, 5), (10, 5), (2, 2), (8, 2), (2, 8), (8, 8)}
    for x, y in rays:
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


def outline(im):
    solid = {(x, y) for y in range(im.height) for x in range(im.width) if im.getpixel((x, y))[3]}
    for y in range(im.height):
        for x in range(im.width):
            if (x, y) not in solid and any((x + dx, y + dy) in solid for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                put(im, x, y, NAVY)
    return im


def build():
    os.makedirs(OUT_DIR, exist_ok=True)
    files = {}
    files['frame.png'] = None  # se copia tal cual
    files['tile.png'] = tile()
    files['tile_h.png'] = tile(True)
    files['panel.png'] = panel()
    files['back.png'] = back()
    files['back_h.png'] = back(True)
    files['coin.png'] = mini_coin()
    files['sun.png'] = sun()
    files['moon.png'] = moon()
    for key, name in APPS:
        files[f'icon_{key}.png'] = getattr(iconos, DRAW[key])().image()
        files[f'label_{key}.png'] = label(name)
    for key, text in BUTTONS.items():
        files[f'btn_{key}.png'] = button(text)
        files[f'btn_{key}_h.png'] = button(text, True)
    for name, im in files.items():
        path = os.path.join(OUT_DIR, name)
        if im is None:
            shutil.copyfile(os.path.join(HERE, 'marco.png'), path)
        else:
            im.save(path)
    return files


def compose(files, page='home', hover=None):
    """Vista previa a escala 4 sobre el marco, igual que la dibuja TFPadScreen."""
    frame = Image.open(os.path.join(HERE, 'marco.png')).convert('RGBA')
    art = img(392, 251)

    def blit(name, x, y):
        art.alpha_composite(files[name], (x, y))

    if page == 'home':
        for i, (key, _) in enumerate(APPS):
            cx, ty = (ROW1_X[i], ROW_Y[0]) if i < 5 else (ROW2_X[i - 5], ROW_Y[1])
            blit('tile_h.png' if key == hover else 'tile.png', cx - 20, ty)
            blit(f'icon_{key}.png', cx - 16, ty + 4)
            lab = files[f'label_{key}.png']
            blit(f'label_{key}.png', cx - lab.width // 2, ty + 45)
    else:
        blit('back.png', *BACK)
        blit(f'label_{page}.png', BACK[0] + 22, BACK[1] + 4)
        blit('panel.png', PANEL[0], PANEL[1])
        blit(f'icon_{page}.png', PANEL[0] + 12, PANEL[1] + 12)
        blit('btn_oficios.png', PANEL[0] + 12, PANEL[1] + 72)
        blit('btn_tienda_h.png', PANEL[0] + 76, PANEL[1] + 72)
        blit('btn_gts.png', PANEL[0] + 140, PANEL[1] + 72)
    blit('coin.png', 290, 68)
    blit('sun.png', 232, 68)
    out = frame.copy()
    big = art.resize((art.width * S, art.height * S), Image.NEAREST).crop((0, 0, frame.width, frame.height))
    out.alpha_composite(big)
    return out


if __name__ == '__main__':
    files = build()
    print(f'{len(files)} texturas en {OUT_DIR}')
    if '--preview' in sys.argv:
        compose(files, 'home', hover='gts').save(os.path.join(HERE, 'preview_home.png'))
        compose(files, 'monedero').save(os.path.join(HERE, 'preview_monedero.png'))
