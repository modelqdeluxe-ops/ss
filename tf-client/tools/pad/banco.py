"""El cofre del Banco del clan (1.3.156): tfclient:banco_clan.

(El dueño: «el banco del clan, un cofre con slots mejorables con las clan coins; este cofre será custom, busca una
textura custom bien bonita y trabajada») Pixel art propio a 32x32 (el doble de detalle que un bloque normal):

- Madera de nogal oscuro en tablas, con su veta y sus nudos.
- Bandas de acero azulado en las esquinas y cantoneras con remaches.
- La junta de la tapa con un fleje de oro viejo.
- Delante, la cerradura: una placa de oro con la gema azul de la Fantastic Clan Coin (brilla de noche, capa _glow)
  y su bocallave.
- Arriba, dos flejes de acero de delante atrás y en el centro un escudo grabado con la gema.
- A los lados, un asa de hierro.

El modelo es un cofre (cuerpo de 14x9x14 y tapa de 14x5x14, como el de Minecraft) con la capa de las gemas a plena
luz.

    python3 tools/pad/banco.py              escribe texturas, modelo, estado, objeto y botín
    python3 tools/pad/banco.py --ver dir    además, una vista previa (caras y el cofre en isométrica)
"""
import json
import math
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources'))
ASSETS = os.path.join(RES, 'assets', 'tfclient')
TEX = os.path.join(ASSETS, 'textures', 'block', 'clan')
N = 32

# tonos (de oscuro a claro)
WOOD = [(34, 20, 13), (52, 31, 19), (70, 43, 26), (90, 57, 34), (112, 73, 44), (136, 92, 56)]
STEEL = [(20, 28, 48), (42, 56, 88), (72, 92, 132), (112, 134, 178), (168, 188, 224), (226, 236, 252)]
GOLD = [(70, 40, 10), (128, 84, 22), (186, 130, 38), (228, 178, 64), (250, 222, 128), (255, 248, 206)]
GEM = [(10, 26, 84), (24, 62, 168), (44, 112, 236), (96, 172, 255), (176, 226, 255), (240, 252, 255)]


def wood(seed, rows):
    """Tablas horizontales: cada fila de tablas con su tono, veta ondulada, juntas oscuras y algún nudo."""
    rnd = random.Random(seed)
    px = [[WOOD[2]] * N for _ in range(N)]
    y = 0
    plank = 0
    while y < N:
        h = rows[plank % len(rows)]
        base = rnd.choice([2, 2, 3])
        phase = rnd.random() * 6.28
        knot = (rnd.randrange(4, N - 4), y + h // 2) if rnd.random() < 0.55 else None
        for yy in range(y, min(N, y + h)):
            for x in range(N):
                t = base
                g = math.sin(x * 0.45 + phase + (yy - y) * 1.3) + 0.6 * math.sin(x * 1.7 + phase * 2)
                if g > 1.05:
                    t = base + 1
                elif g < -1.0:
                    t = base - 1
                if knot and (x - knot[0]) ** 2 + ((yy - knot[1]) * 1.6) ** 2 < 4.5:
                    t = 1 if (x - knot[0]) ** 2 + ((yy - knot[1]) * 1.6) ** 2 < 1.6 else base - 1
                if yy == y:
                    t = min(t + 1, 5)  # la arista de arriba de la tabla, con luz
                if yy == y + h - 1:
                    t = 0  # la junta
                px[yy][x] = WOOD[max(0, min(5, t))]
        y += h
        plank += 1
    return px


def put(px, x, y, c):
    if 0 <= x < N and 0 <= y < N:
        px[y][x] = c


def band_v(px, x0, w, y0, y1, pal=STEEL):
    """Una banda vertical de metal (luz a la izquierda, sombra a la derecha)."""
    for y in range(y0, y1):
        for i in range(w):
            t = 4 if i == 0 else 1 if i == w - 1 else 3 if i == 1 else 2
            put(px, x0 + i, y, pal[t])


def band_h(px, y0, h, x0, x1, pal=GOLD):
    for x in range(x0, x1):
        for i in range(h):
            t = 4 if i == 0 else 1 if i == h - 1 else 3
            put(px, x, y0 + i, pal[t])


def rivet(px, x, y, pal=GOLD):
    put(px, x, y, pal[5])
    put(px, x + 1, y, pal[3])
    put(px, x, y + 1, pal[3])
    put(px, x + 1, y + 1, pal[1])


def corner_caps(px, top, bottom):
    """Cantoneras de acero en las cuatro esquinas, con un remache de oro."""
    for (cx, cy, dx, dy) in ((0, top, 1, 1), (N - 6, top, -1, 1), (0, bottom - 6, 1, -1), (N - 6, bottom - 6, -1, -1)):
        for y in range(6):
            for x in range(6):
                lx = x if dx > 0 else 5 - x
                ly = y if dy > 0 else 5 - y
                if lx + ly <= 6:
                    t = 4 if (lx == 0 or ly == 0) else 1 if lx + ly == 6 else 3 if lx + ly < 3 else 2
                    put(px, cx + x, cy + y, STEEL[t])
        rx = cx + (1 if dx > 0 else 3)
        ry = cy + (1 if dy > 0 else 3)
        rivet(px, rx, ry)


def outline(px, top, bottom):
    for x in range(N):
        put(px, x, top, STEEL[1])
        put(px, x, bottom - 1, STEEL[0])
    for y in range(top, bottom):
        put(px, 0, y, STEEL[2])
        put(px, N - 1, y, STEEL[0])


def gem(px, lit, cx, cy, r):
    """Gema tallada redonda de la Clan Coin (y su brillo para la capa de luz)."""
    for y in range(-r, r + 1):
        for x in range(-r, r + 1):
            d = x * x + y * y
            if d > r * r + r * 0.6:
                continue
            if d > (r - 1) ** 2 + r * 0.6:
                t = 0
            elif x + y < -r * 0.6:
                t = 4
            elif x + y < 0:
                t = 3
            elif x + y < r * 0.8:
                t = 2
            else:
                t = 1
            put(px, cx + x, cy + y, GEM[t])
            if t >= 2:
                lit[(cx + x, cy + y)] = GEM[min(5, t + 1)]
    put(px, cx - r // 2, cy - r // 2, GEM[5])
    lit[(cx - r // 2, cy - r // 2)] = GEM[5]


def lock_plate(px, lit, cx, top):
    """La cerradura: placa de oro con borde, la gema y la bocallave debajo."""
    w, h = 12, 14
    x0, y0 = cx - w // 2, top
    for y in range(h):
        for x in range(w):
            # esquinas de abajo redondeadas (forma de escudo)
            if y > h - 4 and (x < (y - (h - 4)) or x >= w - (y - (h - 4))):
                continue
            edge = x == 0 or x == w - 1 or y == 0 or y == h - 1 or (y > h - 4 and (x == (y - (h - 4)) or x == w - 1 - (y - (h - 4))))
            t = 1 if edge else (4 if y == 1 or x == 1 else 3 if (x + y) % 7 else 2)
            put(px, x0 + x, y0 + y, GOLD[t])
    rivet(px, x0 + 1, y0 + 1)
    rivet(px, x0 + w - 3, y0 + 1)
    gem(px, lit, cx, y0 + 5, 3)
    # bocallave
    for (x, y) in ((0, 0), (1, 0), (0, 1), (1, 1), (0, 2), (1, 2), (0, 3), (1, 3)):
        put(px, cx - 1 + x, y0 + 10 + y, (18, 10, 4))
    put(px, cx - 1, y0 + 10, (60, 36, 10))


def handle(px, cx, cy):
    """Asa de hierro en el lateral."""
    for x in range(-4, 5):
        put(px, cx + x, cy + 3, STEEL[3] if x < 0 else STEEL[2])
        put(px, cx + x, cy + 4, STEEL[1])
    for y in range(0, 4):
        put(px, cx - 4, cy + y, STEEL[3])
        put(px, cx + 4, cy + y, STEEL[1])
    for p in ((cx - 5, cy - 1), (cx + 4, cy - 1)):
        rivet(px, p[0], p[1], STEEL)


# Las caras: la tapa ocupa las filas 4..13 (5 de 16 → 10 de 32) y el cuerpo 14..31 (9 de 16 → 18 de 32); las columnas
# 2..29 (14 de 16 → 28 de 32). Fuera de eso, transparente (el modelo usa solo esa parte).
LID_TOP, SEAM, BOTTOM, LEFT, RIGHT = 4, 14, 32, 2, 30


def front():
    px = wood(11, [5, 5, 4, 4, 5, 5, 4])
    lit = {}
    # tapa y cuerpo con sus marcos
    outline(px, LID_TOP, SEAM)
    outline(px, SEAM, BOTTOM)
    band_h(px, SEAM - 2, 3, 0, N)  # el fleje de oro de la junta
    band_v(px, 0, 4, LID_TOP, BOTTOM)
    band_v(px, N - 4, 4, LID_TOP, BOTTOM)
    corner_caps(px, LID_TOP, BOTTOM)
    for x in (7, 13, 18, 24):
        rivet(px, x, SEAM - 2)
    lock_plate(px, lit, N // 2, SEAM - 5)
    return px, lit


def side():
    px = wood(23, [5, 5, 4, 4, 5, 5, 4])
    lit = {}
    outline(px, LID_TOP, SEAM)
    outline(px, SEAM, BOTTOM)
    band_h(px, SEAM - 2, 3, 0, N)
    band_v(px, 0, 4, LID_TOP, BOTTOM)
    band_v(px, N - 4, 4, LID_TOP, BOTTOM)
    corner_caps(px, LID_TOP, BOTTOM)
    handle(px, N // 2, SEAM + 4)
    return px, lit


def top():
    """La tapa por arriba (se usa la parte 2..29: 14 de 16)."""
    px = wood(37, [4, 4, 4, 4, 4, 4, 4, 4])
    lit = {}
    for y in range(2, 30):
        for x in range(2, 30):
            if x in (2, 29) or y in (2, 29):
                put(px, x, y, STEEL[1])
    band_v(px, 8, 4, 2, 30)
    band_v(px, N - 12, 4, 2, 30)
    band_h(px, 2, 3, 2, 30, STEEL)
    band_h(px, N - 5, 3, 2, 30, STEEL)
    for (x, y) in ((3, 3), (27, 3), (3, 27), (27, 27)):
        rivet(px, x, y)
    # el escudo grabado del centro con la gema
    cx, cy = N // 2, N // 2
    half = [5, 6, 6, 6, 6, 6, 6, 5, 5, 4, 3, 2, 1]
    for i, h in enumerate(half):
        for x in range(-h, h):
            edge = x in (-h, h - 1) or i == 0 or i == len(half) - 1
            put(px, cx + x, cy - 6 + i, GOLD[1] if edge else GOLD[3] if x < 0 else GOLD[2])
    gem(px, lit, cx, cy - 1, 3)
    return px, lit


def bottom():
    px = wood(51, [4, 4, 4, 4, 4, 4, 4, 4])
    for y in range(2, 30):
        for x in range(2, 30):
            if x in (2, 29) or y in (2, 29):
                put(px, x, y, STEEL[0])
    return px, {}


def image(px, lit=None):
    im = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    for y in range(N):
        for x in range(N):
            im.putpixel((x, y), px[y][x] + (255,))
    over = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    for (x, y), c in (lit or {}).items():
        over.putpixel((x, y), c + (255,))
    return im, over


def write():
    os.makedirs(TEX, exist_ok=True)
    faces = {}
    for name, fn in (('front', front), ('side', side), ('top', top), ('bottom', bottom)):
        px, lit = fn()
        im, over = image(px, lit)
        im.save(os.path.join(TEX, f'banco_clan_{name}.png'), optimize=True)
        if lit:
            over.save(os.path.join(TEX, f'banco_clan_{name}_glow.png'), optimize=True)
        faces[name] = (im, over if lit else None)
    t = 'tfclient:block/clan/banco_clan'

    def uv_front(part):
        # la tapa: filas 2..7 de 16 (4..14 de 32); el cuerpo: 7..16
        return [1, 2, 15, 7] if part == 'lid' else [1, 7, 15, 16]

    def box(frm, to, part, glow=False):
        tex = '#front_glow' if glow else '#front'
        sid = '#side'
        f = {
            'north': {'uv': uv_front(part), 'texture': tex},
            'south': {'uv': uv_front(part), 'texture': sid},
            'east': {'uv': uv_front(part), 'texture': sid},
            'west': {'uv': uv_front(part), 'texture': sid},
        }
        if glow:
            f = {'north': {'uv': uv_front(part), 'texture': tex}}
        else:
            if part == 'lid':
                f['up'] = {'uv': [1, 1, 15, 15], 'texture': '#top'}
            else:
                f['down'] = {'uv': [1, 1, 15, 15], 'texture': '#bottom', 'cullface': 'down'}
        el = {'from': frm, 'to': to, 'faces': f}
        if glow:
            el['shade'] = False
            for v in f.values():
                v['forge_data'] = {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}
        return el

    model = {
        'parent': 'minecraft:block/block',
        'render_type': 'minecraft:cutout',
        'textures': {
            'particle': f'{t}_side', 'front': f'{t}_front', 'side': f'{t}_side', 'top': f'{t}_top',
            'bottom': f'{t}_bottom', 'front_glow': f'{t}_front_glow', 'top_glow': f'{t}_top_glow',
        },
        'elements': [
            box([1, 0, 1], [15, 9, 15], 'body'),
            box([1, 9, 1], [15, 14, 15], 'lid'),
            # la gema de la cerradura y la de la tapa, a plena luz (se ven de noche)
            box([1, 0, 0.99], [15, 9, 15], 'body', glow=True),
            box([1, 9, 0.99], [15, 14, 15], 'lid', glow=True),
            {'from': [1, 14.01, 1], 'to': [15, 14.01, 15], 'shade': False,
             'faces': {'up': {'uv': [1, 1, 15, 15], 'texture': '#top_glow',
                              'forge_data': {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}}}},
        ],
        'display': {
            'gui': {'rotation': [30, 225, 0], 'translation': [0, 0.5, 0], 'scale': [0.68, 0.68, 0.68]},
            'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.25, 0.25, 0.25]},
            'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]},
            'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]},
            'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.40, 0.40, 0.40]},
            'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [0.40, 0.40, 0.40]},
        },
    }
    md = os.path.join(ASSETS, 'models', 'block', 'clan')
    os.makedirs(md, exist_ok=True)
    with open(os.path.join(md, 'banco_clan.json'), 'w') as f:
        json.dump(model, f, indent=2)
    variants = {f'facing={d}': {'model': 'tfclient:block/clan/banco_clan', **({'y': y} if y else {})}
                for d, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270))}
    with open(os.path.join(ASSETS, 'blockstates', 'banco_clan.json'), 'w') as f:
        json.dump({'variants': variants}, f, indent=2)
    with open(os.path.join(ASSETS, 'models', 'item', 'banco_clan.json'), 'w') as f:
        json.dump({'parent': 'tfclient:block/clan/banco_clan'}, f, indent=2)
    loot = os.path.join(RES, 'data', 'tfclient', 'loot_tables', 'blocks')
    os.makedirs(loot, exist_ok=True)
    with open(os.path.join(loot, 'banco_clan.json'), 'w') as f:
        json.dump({'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'tfclient:banco_clan'}],
                                                          'conditions': [{'condition': 'minecraft:survives_explosion'}]}]}, f, indent=2)
    return faces


def preview(faces, out):
    """Las caras a 8x y el cofre en isométrica (como en el inventario)."""
    s = 8
    sheet = Image.new('RGBA', (N * s * 4 + 50, N * s + 20), (14, 18, 26, 255))
    for i, k in enumerate(('front', 'side', 'top', 'bottom')):
        im, over = faces[k]
        big = im.resize((N * s, N * s), Image.NEAREST)
        if over is not None:
            big.alpha_composite(over.resize((N * s, N * s), Image.NEAREST))
        sheet.alpha_composite(big, (10 + i * (N * s + 10), 10))
    os.makedirs(out, exist_ok=True)
    sheet.save(os.path.join(out, 'banco_caras.png'))
    # el cofre en isométrica (como en el inventario), con el dibujo de las piedras (gen_claim_blocks.iso)
    sys.path.insert(0, os.path.join(HERE, '..'))
    import gen_claim_blocks as G

    def used(k):
        im, over = faces[k]
        im = im.copy()
        if over is not None:
            im.alpha_composite(over)
        box = (LEFT, LID_TOP, RIGHT, BOTTOM) if k in ('front', 'side') else (2, 2, 30, 30)
        return im.crop(box).resize((N, N), Image.NEAREST)

    canvas = G.iso(used('front'), used('side'), used('top'), s=8)
    canvas.save(os.path.join(out, 'banco_iso.png'))


if __name__ == '__main__':
    f = write()
    print('ok: banco_clan')
    if '--ver' in sys.argv:
        preview(f, sys.argv[sys.argv.index('--ver') + 1])
