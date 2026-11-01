"""El icono del estandarte del clan (1.3.159): 32x32 de pixel art, asta de madera con remate dorado, travesaño dorado y
tela roja con ribete, un escudo dorado y dos puntas abajo. En el mundo el estandarte lo dibuja ClanBannerRenderer con el
color y la insignia del clan; este icono es el del objeto (inventario, mano).
Uso: python3 tools/pad/estandarte_icono.py"""
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient', 'textures', 'item',
                                    'estandarte_clan.png'))

im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
px = im.load()
WOOD, WOOD_D, GOLD, GOLD_D, GOLD_L = (110, 72, 38), (74, 46, 24), (242, 193, 78), (184, 132, 40), (255, 232, 150)
RED, RED_D, RED_L, TRIM = (205, 48, 60), (140, 26, 40), (236, 92, 100), (120, 22, 34)


def rect(x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px[x, y] = c + (255,)


# asta y remate
rect(15, 3, 16, 30, WOOD)
rect(16, 3, 16, 30, WOOD_D)
rect(14, 1, 17, 3, GOLD)
rect(15, 0, 16, 0, GOLD_L)
# travesaño
rect(5, 5, 26, 6, GOLD)
rect(5, 6, 26, 6, GOLD_D)
rect(4, 4, 5, 7, GOLD_L)
rect(26, 4, 27, 7, GOLD_L)
# tela con ribete y dos puntas
for y in range(7, 27):
    for x in range(7, 25):
        notch = y > 22 and abs(x - 15.5) < (y - 22) * 1.6
        if notch:
            continue
        edge = x in (7, 24) or y == 7 or (y >= 22 and abs(x - 15.5) < (y - 21) * 1.6 + 1)
        shade = RED_L if x < 10 else RED_D if x > 21 else RED
        px[x, y] = (TRIM if edge else shade) + (255,)
# el escudo dorado
for y in range(10, 20):
    w = 4 if y < 16 else 4 - (y - 15)
    for x in range(int(15.5 - w), int(15.5 + w) + 1):
        if 0 <= x < 32:
            px[x, y] = (GOLD_L if x < 15 else GOLD) + (255,)
rect(15, 12, 16, 16, GOLD_D)
os.makedirs(os.path.dirname(OUT), exist_ok=True)
im.save(OUT)
print(OUT)
