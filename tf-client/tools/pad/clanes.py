"""Dibujos de los clanes (1.3.156): la Fantastic Clan Coin azul (las insignias, desde la 1.3.158: tools/pad/insignias.py).

(El dueño: «meteremos un nuevo coin, azul, Fantastic Clan Coin» y «el estandarte sería un icono o badge que tú hagas,
haz muchas») Todo pixel art propio:

- La moneda azul: la de oro (coin.png, coin_s.png del pad y el objeto fantastic_coin) con sus tonos pasados a azul,
  conservando el sombreado.
- Las insignias: 32x32 a 2x (64x64), un escudo (6 formas) con un símbolo (dibujado aquí, 16x16) y sus colores.

    python3 tools/pad/clanes.py            escribe las texturas y assets/tfclient/pad/insignias.json
    python3 tools/pad/clanes.py --ver dir  además, una hoja con todas las insignias
"""
import json
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient'))
PAD = os.path.join(RES, 'textures', 'gui', 'pad')
ITEM = os.path.join(RES, 'textures', 'item')
BADGES = os.path.join(PAD, 'insignias')
INDEX = os.path.join(RES, 'pad', 'insignias.json')


# ---------------------------------------------------------------------------------------------------------------------
# La moneda azul
# ---------------------------------------------------------------------------------------------------------------------

BLUE = [(16, 30, 90), (28, 64, 168), (46, 110, 232), (92, 170, 255), (170, 222, 255), (235, 250, 255)]


def blue_coin(src, dst):
    im = Image.open(src).convert('RGBA')
    a = np.asarray(im, np.float32)
    lum = (0.3 * a[..., 0] + 0.59 * a[..., 1] + 0.11 * a[..., 2]) / 255
    lo, hi = lum[a[..., 3] > 0].min(), lum[a[..., 3] > 0].max()
    t = np.clip((lum - lo) / max(1e-6, hi - lo), 0, 1)
    pal = np.array(BLUE, np.float32)
    idx = t * (len(pal) - 1)
    i0 = np.floor(idx).astype(int)
    i1 = np.minimum(i0 + 1, len(pal) - 1)
    f = (idx - i0)[..., None]
    col = pal[i0] * (1 - f) + pal[i1] * f
    out = a.copy()
    out[..., :3] = col
    Image.fromarray(out.astype(np.uint8), 'RGBA').save(dst)


# ---------------------------------------------------------------------------------------------------------------------
# Las insignias
# ---------------------------------------------------------------------------------------------------------------------

# Los símbolos, 16x16: «#» el color del símbolo, «+» su brillo, «.» nada
SYMBOLS = {
    'espada': [
        '..............##',
        '.............#+#',
        '............#+#.',
        '...........#+#..',
        '..........#+#...',
        '.........#+#....',
        '........#+#.....',
        '..#....#+#......',
        '..##..#+#.......',
        '...####.........',
        '....##..........',
        '...####.........',
        '..##..##........',
        '.##.............',
        '##..............',
        '#...............'],
    'corona': [
        '................',
        '................',
        '..#....##....#..',
        '..##..#++#..##..',
        '..#+#.#++#.#+#..',
        '..#++#++++#++#..',
        '..#++++++++++#..',
        '..#++++++++++#..',
        '..############..',
        '..#+#+#++#+#+#..',
        '..############..',
        '................',
        '................',
        '................',
        '................',
        '................'],
    'estrella': [
        '.......##.......',
        '.......##.......',
        '......#++#......',
        '......#++#......',
        '#######++#######',
        '.#++++++++++++#.',
        '..#++++++++++#..',
        '...#++++++++#...',
        '...#++++++++#...',
        '..#+++####+++#..',
        '..#++#....#++#..',
        '.#+##......##+#.',
        '.##..........##.',
        '................',
        '................',
        '................'],
    'calavera': [
        '................',
        '....########....',
        '...#++++++++#...',
        '..#++++++++++#..',
        '..#++++++++++#..',
        '..#+##++++##+#..',
        '..#+##++++##+#..',
        '..#+++####+++#..',
        '...#+++##+++#...',
        '....#++++++#....',
        '....#+#+#+##....',
        '.....######.....',
        '................',
        '................',
        '................',
        '................'],
    'llama': [
        '.......#........',
        '.......##.......',
        '......#+#.......',
        '......#++#..#...',
        '.....#+++#.##...',
        '...#.#++++##+#..',
        '..##.#++++++#...',
        '..#+##+++++++#..',
        '..#++++####++#..',
        '.#++++##..##++#.',
        '.#+++##....#++#.',
        '.#+++#.....#++#.',
        '..#++#....#++#..',
        '...#++#..#++#...',
        '....######......',
        '................'],
    'hoja': [
        '................',
        '..........####..',
        '........##++++#.',
        '......##++++++#.',
        '.....#++++++++#.',
        '....#++++#++++#.',
        '...#++++#++++#..',
        '...#+++#++++#...',
        '..#+++#++++#....',
        '..#++#++++#.....',
        '..#+#+++##......',
        '..##+###........',
        '.#.##...........',
        '#...............',
        '................',
        '................'],
    'ola': [
        '................',
        '................',
        '.....####.......',
        '...##++++##.....',
        '..#++####++#....',
        '.#++#....#+#....',
        '.#+#..##..#.....',
        '#++#.#++#.......',
        '#++#.#++#....##.',
        '#+++#.##....#++#',
        '.#+++##....#++#.',
        '..##++++####+#..',
        '....##++++++#...',
        '......######....',
        '................',
        '................'],
    'luna': [
        '................',
        '......####......',
        '....##++#.......',
        '...#++##........',
        '..#++#..........',
        '..#+#...........',
        '.#++#...........',
        '.#+#............',
        '.#++#...........',
        '.#++#.......#...',
        '..#++#.....##...',
        '..#+++#####+#...',
        '...##+++++##....',
        '.....#####......',
        '................',
        '................'],
    'sol': [
        '.......##.......',
        '..#....##....#..',
        '...#........#...',
        '......####......',
        '....##++++##....',
        '....#++++++#....',
        '...#++++++++#...',
        '##.#++++++++#.##',
        '##.#++++++++#.##',
        '...#++++++++#...',
        '....#++++++#....',
        '....##++++##....',
        '......####......',
        '...#........#...',
        '..#....##....#..',
        '.......##.......'],
    'arbol': [
        '......####......',
        '....##++++##....',
        '...#++++++++#...',
        '..#++++++++++#..',
        '..#++++++++++#..',
        '.#++++++++++++#.',
        '.#++++++++++++#.',
        '..#++++++++++#..',
        '...##++++++##...',
        '.....######.....',
        '.......##.......',
        '.......##.......',
        '.......##.......',
        '.....######.....',
        '................',
        '................'],
    'hacha': [
        '........##......',
        '.......#++#.....',
        '......#++++#....',
        '.....#+++++#....',
        '....#++++++#....',
        '....##+++##.....',
        '.....##.##......',
        '......##........',
        '.....##.........',
        '....##..........',
        '...##...........',
        '..##............',
        '.##.............',
        '##..............',
        '#...............',
        '................'],
    'arco': [
        '...######.......',
        '..#++....#......',
        '.#+.......#.....',
        '#+.........#....',
        '#+..........#...',
        '#+...........#..',
        '#+.##########+##',
        '#+...........#..',
        '#+..........#...',
        '#+.........#....',
        '.#+.......#.....',
        '..#++....#......',
        '...######.......',
        '................',
        '................',
        '................'],
    'corazon': [
        '................',
        '..####....####..',
        '.#++++#..#++++#.',
        '#++++++##++++++#',
        '#++++++++++++++#',
        '#++++++++++++++#',
        '.#++++++++++++#.',
        '..#++++++++++#..',
        '...#++++++++#...',
        '....#++++++#....',
        '.....#++++#.....',
        '......#++#......',
        '.......##.......',
        '................',
        '................',
        '................'],
    'gema': [
        '................',
        '....########....',
        '...#++#++#++#...',
        '..#++#++++#++#..',
        '.##############.',
        '..#+++#++#+++#..',
        '...#++#++#++#...',
        '....#++#+#+#....',
        '.....#+##+#.....',
        '......#++#......',
        '.......##.......',
        '................',
        '................',
        '................',
        '................',
        '................'],
    'rayo': [
        '.........####...',
        '........#++#....',
        '.......#++#.....',
        '......#++#......',
        '.....#++#.......',
        '....#++######...',
        '...#+++++++#....',
        '....####++#.....',
        '.......#+#......',
        '......#+#.......',
        '.....#+#........',
        '....##..........',
        '...#............',
        '................',
        '................',
        '................'],
    'ojo': [
        '................',
        '................',
        '................',
        '.....######.....',
        '...##++++++##...',
        '..#++######++#..',
        '.#++#++##++#++#.',
        '#++#++####++#++#',
        '.#++#++##++#++#.',
        '..#++######++#..',
        '...##++++++##...',
        '.....######.....',
        '................',
        '................',
        '................',
        '................'],
    'lobo': [
        '................',
        '..#..........#..',
        '..##........##..',
        '..#+#......#+#..',
        '..#++######++#..',
        '..#++++++++++#..',
        '.#++##++++##++#.',
        '.#++##++++##++#.',
        '.#++++++++++++#.',
        '..#++++##++++#..',
        '...#++####++#...',
        '....#++##++#....',
        '.....#++++#.....',
        '......####......',
        '................',
        '................'],
    'dragon': [
        '................',
        '.##.........##..',
        '#++#.......#++#.',
        '#+++#.....#+++#.',
        '.#+++#...#+++#..',
        '..#++######++#..',
        '...#++++++++#...',
        '..#++#++++#++#..',
        '..#++++++++++#..',
        '...#+++##+++#...',
        '....#+#..#+#....',
        '....##....##....',
        '................',
        '................',
        '................',
        '................'],
    'castillo': [
        '................',
        '.#.#.#....#.#.#.',
        '.#####....#####.',
        '.#+++#....#+++#.',
        '.#+++########+#.',
        '.#+++#+#+#+#++#.',
        '.#+++########+#.',
        '.#++++++++++++#.',
        '.#+++++##+++++#.',
        '.#++++#++#++++#.',
        '.#++++#++#++++#.',
        '.##############.',
        '................',
        '................',
        '................',
        '................'],
    'ancla': [
        '.......##.......',
        '......#++#......',
        '......#++#......',
        '.......##.......',
        '....########....',
        '.......##.......',
        '.......##.......',
        '.......##.......',
        '.......##.......',
        '.#.....##.....#.',
        '.##....##....##.',
        '..##...##...##..',
        '...####++####...',
        '.....######.....',
        '................',
        '................'],
}

# Las formas del escudo (32x32): una función que dice si (x, y) está dentro
def shape_heater(x, y):
    if y < 3:
        return False
    if y < 18:
        return 3 <= x <= 28
    k = (y - 18) / 12
    half = 12.5 * (1 - k ** 1.5)
    return abs(x - 15.5) <= half and y <= 30


def shape_round(x, y):
    return (x - 15.5) ** 2 + (y - 15.5) ** 2 <= 14 ** 2


def shape_kite(x, y):
    if y < 2 or y > 30:
        return False
    if y < 10:
        return abs(x - 15.5) <= 6 + y * 0.9
    half = 14 * (1 - (y - 10) / 21)
    return abs(x - 15.5) <= half


def shape_banner(x, y):
    if x < 5 or x > 26 or y < 2:
        return False
    if y < 24:
        return True
    return abs(x - 15.5) >= (y - 24) * 1.8 and y <= 30


def shape_diamond(x, y):
    return abs(x - 15.5) + abs(y - 15.5) <= 15


def shape_hex(x, y):
    dx, dy = abs(x - 15.5), abs(y - 15.5)
    return dy <= 14 and dx <= 14 and dx * 0.5 + dy * 0.866 <= 14


SHAPES = {'escudo': shape_heater, 'redondo': shape_round, 'cometa': shape_kite, 'estandarte': shape_banner,
          'rombo': shape_diamond, 'hexagono': shape_hex}

# Colores: (fondo, fondo oscuro, borde, símbolo, brillo del símbolo)
SCHEMES = {
    'oro': ((70, 40, 120), (45, 24, 82), (250, 200, 70), (255, 222, 110), (255, 250, 210)),
    'rojo': ((170, 30, 40), (110, 16, 26), (240, 200, 120), (250, 230, 200), (255, 255, 255)),
    'azul': ((30, 70, 170), (18, 40, 110), (200, 220, 255), (230, 240, 255), (255, 255, 255)),
    'verde': ((30, 120, 60), (16, 76, 36), (220, 200, 110), (240, 230, 170), (255, 255, 230)),
    'negro': ((34, 34, 42), (16, 16, 22), (200, 60, 60), (230, 70, 70), (255, 170, 160)),
    'cian': ((20, 120, 140), (10, 74, 92), (170, 250, 255), (200, 255, 255), (255, 255, 255)),
    'rosa': ((200, 70, 140), (140, 40, 96), (255, 220, 240), (255, 235, 245), (255, 255, 255)),
    'naranja': ((210, 100, 20), (150, 60, 10), (255, 230, 160), (255, 245, 210), (255, 255, 255)),
    'morado': ((100, 40, 170), (66, 22, 120), (220, 190, 255), (240, 220, 255), (255, 255, 255)),
    'blanco': ((230, 232, 240), (180, 186, 200), (80, 90, 120), (60, 70, 110), (120, 140, 200)),
}

# Las insignias que salen: (id, nombre, forma, símbolo, colores)
INSIGNIAS = [
    ('espada_oro', 'Espada dorada', 'escudo', 'espada', 'oro'),
    ('corona_rojo', 'Corona roja', 'escudo', 'corona', 'rojo'),
    ('estrella_azul', 'Estrella azul', 'redondo', 'estrella', 'azul'),
    ('calavera_negro', 'Calavera', 'escudo', 'calavera', 'negro'),
    ('llama_naranja', 'Llama', 'cometa', 'llama', 'naranja'),
    ('hoja_verde', 'Hoja', 'redondo', 'hoja', 'verde'),
    ('ola_cian', 'Ola', 'redondo', 'ola', 'cian'),
    ('luna_morado', 'Luna', 'estandarte', 'luna', 'morado'),
    ('sol_oro', 'Sol', 'redondo', 'sol', 'oro'),
    ('arbol_verde', 'Árbol', 'escudo', 'arbol', 'verde'),
    ('hacha_rojo', 'Hacha', 'cometa', 'hacha', 'rojo'),
    ('arco_verde', 'Arco', 'estandarte', 'arco', 'verde'),
    ('corazon_rosa', 'Corazón', 'redondo', 'corazon', 'rosa'),
    ('gema_cian', 'Gema', 'rombo', 'gema', 'cian'),
    ('rayo_azul', 'Rayo', 'hexagono', 'rayo', 'azul'),
    ('ojo_morado', 'Ojo', 'rombo', 'ojo', 'morado'),
    ('lobo_blanco', 'Lobo', 'escudo', 'lobo', 'blanco'),
    ('dragon_rojo', 'Mariposa roja', 'escudo', 'dragon', 'rojo'),
    ('castillo_azul', 'Castillo', 'estandarte', 'castillo', 'azul'),
    ('ancla_azul', 'Ancla', 'redondo', 'ancla', 'azul'),
    ('espada_negro', 'Espada negra', 'cometa', 'espada', 'negro'),
    ('corona_oro', 'Corona dorada', 'hexagono', 'corona', 'oro'),
    ('estrella_rosa', 'Estrella rosa', 'estandarte', 'estrella', 'rosa'),
    ('calavera_morado', 'Calavera morada', 'hexagono', 'calavera', 'morado'),
    ('llama_azul', 'Llama azul', 'escudo', 'llama', 'azul'),
    ('hoja_naranja', 'Hoja de otoño', 'cometa', 'hoja', 'naranja'),
    ('ola_azul', 'Marea', 'escudo', 'ola', 'azul'),
    ('luna_negro', 'Luna nueva', 'redondo', 'luna', 'negro'),
    ('sol_naranja', 'Sol poniente', 'estandarte', 'sol', 'naranja'),
    ('arbol_oro', 'Árbol dorado', 'hexagono', 'arbol', 'oro'),
    ('hacha_negro', 'Hacha negra', 'escudo', 'hacha', 'negro'),
    ('arco_morado', 'Arco arcano', 'rombo', 'arco', 'morado'),
    ('corazon_rojo', 'Corazón rojo', 'escudo', 'corazon', 'rojo'),
    ('gema_verde', 'Esmeralda', 'hexagono', 'gema', 'verde'),
    ('rayo_oro', 'Rayo dorado', 'escudo', 'rayo', 'oro'),
    ('ojo_cian', 'Ojo de cristal', 'redondo', 'ojo', 'cian'),
    ('lobo_negro', 'Lobo negro', 'cometa', 'lobo', 'negro'),
    ('dragon_oro', 'Mariposa dorada', 'hexagono', 'dragon', 'oro'),
    ('castillo_rojo', 'Fortaleza', 'escudo', 'castillo', 'rojo'),
    ('ancla_blanco', 'Ancla blanca', 'estandarte', 'ancla', 'blanco'),
    ('espada_azul', 'Espada azul', 'rombo', 'espada', 'azul'),
    ('corona_morado', 'Corona morada', 'redondo', 'corona', 'morado'),
    ('estrella_oro', 'Estrella dorada', 'escudo', 'estrella', 'oro'),
    ('dragon_verde', 'Mariposa verde', 'cometa', 'dragon', 'verde'),
    ('lobo_azul', 'Lobo de hielo', 'hexagono', 'lobo', 'cian'),
    ('llama_rojo', 'Llama roja', 'redondo', 'llama', 'rojo'),
    ('calavera_blanco', 'Calavera blanca', 'rombo', 'calavera', 'blanco'),
    ('gema_rosa', 'Gema rosa', 'escudo', 'gema', 'rosa'),
]


def badge(shape, symbol, scheme):
    bg, bg_dark, rim, sym, sym_hi = SCHEMES[scheme]
    inside = SHAPES[shape]
    img = np.zeros((32, 32, 4), np.uint8)
    mask = np.array([[inside(x, y) for x in range(32)] for y in range(32)])
    for y in range(32):
        for x in range(32):
            if not mask[y, x]:
                continue
            # borde: un píxel con algún vecino fuera
            edge = any(not (0 <= x + dx < 32 and 0 <= y + dy < 32 and mask[y + dy, x + dx]) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            edge2 = any(not (0 <= x + dx < 32 and 0 <= y + dy < 32 and mask[y + dy, x + dx]) for dx, dy in ((2, 0), (-2, 0), (0, 2), (0, -2)))
            if edge:
                img[y, x] = (*[max(0, c - 70) for c in rim], 255)
            elif edge2:
                img[y, x] = (*rim, 255)
            else:
                t = y / 31
                col = [int(bg[i] * (1 - t) + bg_dark[i] * t) for i in range(3)]
                # brillo de arriba a la izquierda
                if x + y < 22:
                    col = [min(255, c + 18) for c in col]
                img[y, x] = (*col, 255)
    rows = SYMBOLS[symbol]
    ox, oy = 8, 8
    for sy, row in enumerate(rows):
        for sx, ch in enumerate(row):
            if ch == '.':
                continue
            x, y = ox + sx, oy + sy
            if 0 <= x < 32 and 0 <= y < 32 and mask[y, x]:
                c = sym if ch == '#' else sym_hi
                img[y, x] = (*c, 255)
    # sombra del símbolo (abajo a la derecha)
    out = img.copy()
    for sy, row in enumerate(rows):
        for sx, ch in enumerate(row):
            if ch == '.':
                continue
            x, y = ox + sx + 1, oy + sy + 1
            if 0 <= x < 32 and 0 <= y < 32 and mask[y, x] and rows[sy + 1 if sy + 1 < 16 else sy][sx + 1 if sx + 1 < 16 else sx] == '.' \
                    and tuple(out[y, x, :3]) not in (tuple(sym), tuple(sym_hi)):
                out[y, x, :3] = (out[y, x, :3] * 0.55).astype(np.uint8)
    return Image.fromarray(out, 'RGBA').resize((64, 64), Image.NEAREST)


def main():
    blue_coin(os.path.join(PAD, 'coin.png'), os.path.join(PAD, 'coin_clan.png'))
    blue_coin(os.path.join(PAD, 'coin_s.png'), os.path.join(PAD, 'coin_clan_s.png'))
    blue_coin(os.path.join(ITEM, 'fantastic_coin.png'), os.path.join(ITEM, 'fantastic_coin_clan.png'))
    # (1.3.158) las insignias las hace ahora tools/pad/insignias.py (unas 200, con los símbolos de game-icons.net)
    if '--ver' in sys.argv:
        dest = sys.argv[sys.argv.index('--ver') + 1]
        os.makedirs(dest, exist_ok=True)
        cols = 12
        sheet = Image.new('RGBA', (cols * 72, ((len(INSIGNIAS) + cols - 1) // cols) * 72 + 72), (235, 238, 245, 255))
        for i, b in enumerate(INSIGNIAS):
            im = Image.open(os.path.join(BADGES, f'{b[0]}.png'))
            sheet.alpha_composite(im, ((i % cols) * 72 + 4, (i // cols) * 72 + 4))
        y = ((len(INSIGNIAS) + cols - 1) // cols) * 72 + 8
        for i, n in enumerate(['coin.png', 'coin_clan.png', 'coin_s.png', 'coin_clan_s.png']):
            im = Image.open(os.path.join(PAD, n)).convert('RGBA')
            sheet.alpha_composite(im.resize((im.width * 4, im.height * 4), Image.NEAREST), (8 + i * 60, y))
        coin = Image.open(os.path.join(ITEM, 'fantastic_coin_clan.png')).convert('RGBA')
        sheet.alpha_composite(coin.resize((coin.width * 3, coin.height * 3), Image.NEAREST), (260, y))
        sheet.save(os.path.join(dest, 'insignias.png'))
    print('la moneda azul')


if __name__ == '__main__':
    main()
