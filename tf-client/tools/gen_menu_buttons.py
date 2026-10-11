"""Texturas de los botones del menú de inicio (textures/gui/menu_buttons_N.png, N = escala de interfaz 1..6).

Sale del "Medieval Game Button Pack" de pzUH (CC0, dominio público):
https://opengameart.org/content/medieval-game-button-pack — tabla de madera con marco de metal; plateado en reposo,
dorado con el ratón encima. El pack no va en el repo; solo lo convertido. Uso:

  curl -sSLo Files_0.zip https://opengameart.org/sites/default/files/Files_0.zip && unzip Files_0.zip
  python3 tools/gen_menu_buttons.py Files_0/Button.png

Cada hoja tiene 8 placas de 200x20 píxeles de interfaz, una debajo de otra, ya reducidas a N píxeles reales por
píxel de interfaz (200N x 20N cada una, nítidas, sin estirar en el juego):

  0 normal · 1 ratón encima · 2 pulsado · 3 principal · 4 principal ratón · 5 principal pulsado · 6 Salir ratón ·
  7 desactivado

TFMenuButton toma CAP píxeles de interfaz de cada lado y, del centro, lo que haga falta (los botones miden 200 como
mucho).
"""
import os
import sys

import numpy as np
from PIL import Image, ImageOps

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'tfclient',
                   'textures', 'gui')
# Los tres botones largos sin texto del pack (plateado, dorado, bloqueado): x de cada uno, y, ancho y alto.
SOURCES_X, SOURCE_Y, SW, SH = (300, 744, 1190), 285, 401, 174
W, H, PLATES, SCALES = 200, 20, 8, range(1, 7)
CAP_SRC = 61  # 7 píxeles de interfaz con H = 20 (174 * 7 / 20 = 60.9)


def widen(src):
    """El botón a 10:1 (200x20): las esquinas tal cual y el centro repetido en espejo (sin costuras)."""
    wide_w = SH * W // H
    mid = src.crop((CAP_SRC, 0, SW - CAP_SRC, SH))
    out = Image.new('RGBA', (wide_w, SH))
    out.paste(src.crop((0, 0, CAP_SRC, SH)), (0, 0))
    x, flip = CAP_SRC, False
    while x < wide_w - CAP_SRC:
        part = mid.transpose(Image.FLIP_LEFT_RIGHT) if flip else mid
        part = part.crop((0, 0, min(part.width, wide_w - CAP_SRC - x), SH))
        out.paste(part, (x, 0))
        x += part.width
        flip = not flip
    out.paste(src.crop((SW - CAP_SRC, 0, SW, SH)), (wide_w - CAP_SRC, 0))
    return out


def frame_mask(silver):
    """1 en el marco de metal (gris), 0 en la madera (marrón)."""
    a = np.asarray(silver).astype(np.float32)
    sat = np.abs(a[..., 0] - a[..., 2])
    return np.clip((40.0 - sat) / 20.0, 0.0, 1.0)


def shade(img, mask, frame_k, wood_k):
    """Aclara u oscurece el marco y la madera por separado."""
    a = np.asarray(img).astype(np.float32)
    k = mask * frame_k + (1.0 - mask) * wood_k
    a[..., :3] = np.clip(a[..., :3] * k[..., None], 0, 255)
    return Image.fromarray(a.astype(np.uint8), 'RGBA')


def red_frame(silver, gold, mask):
    """El dorado con el marco en rojo (Salir con el ratón encima), hecho de la luz del marco plateado."""
    lum = ImageOps.colorize(silver.convert('L'), black=(40, 2, 6), mid=(168, 22, 32), white=(250, 128, 118))
    a = np.asarray(gold).astype(np.float32)
    r = np.asarray(lum.convert('RGB')).astype(np.float32)
    a[..., :3] = a[..., :3] * (1.0 - mask[..., None]) + r * mask[..., None]
    return Image.fromarray(a.astype(np.uint8), 'RGBA')


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    pack = Image.open(sys.argv[1]).convert('RGBA')
    silver, gold, dark = (widen(pack.crop((x, SOURCE_Y, x + SW, SOURCE_Y + SH))) for x in SOURCES_X)
    mask = frame_mask(silver)
    plates = [
        silver,                              # 0 normal
        gold,                                # 1 ratón encima
        shade(gold, mask, 0.9, 0.78),        # 2 pulsado
        shade(gold, mask, 1.0, 1.08),        # 3 principal
        shade(gold, mask, 1.12, 1.2),        # 4 principal ratón
        shade(gold, mask, 0.9, 0.85),        # 5 principal pulsado
        red_frame(silver, gold, mask),       # 6 Salir ratón
        dark,                                # 7 desactivado
    ]
    for s in SCALES:
        sheet = Image.new('RGBA', (W * s, H * s * PLATES))
        for i, p in enumerate(plates):
            small = p.convert('RGBa').resize((W * s, H * s), Image.LANCZOS).convert('RGBA')
            sheet.paste(small, (0, i * H * s))
        path = os.path.join(OUT, 'menu_buttons_%d.png' % s)
        sheet.save(path, optimize=True)
        print(path, sheet.size, os.path.getsize(path))


if __name__ == '__main__':
    main()
