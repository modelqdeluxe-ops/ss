"""Limpia el contorno exterior del marco del pad (marco.png → también frame.png).

El marco salió de un dibujo con fondo blanco: en el borde de fuera quedaban píxeles grises claros (los «píxeles
blancos» que se veían alrededor). Esto los quita (hasta 3 capas, solo los claros y sin color que tocan el exterior) y
deja una línea oscura limpia en todo el contorno. No toca nada de dentro. Se puede pasar varias veces.

    python3 tools/pad/limpiar_bordes.py
"""
import os
import shutil

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, 'marco.png')
OUT = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient', 'textures', 'gui',
                                    'pad', 'frame.png'))
LINE = (22, 20, 30)


def outside(alpha):
    """Lo transparente conectado con el borde de la imagen (el exterior; el hueco de la pantalla no cuenta)."""
    h, w = alpha.shape
    seen = np.zeros_like(alpha, dtype=bool)
    stack = [(0, x) for x in range(w)] + [(h - 1, x) for x in range(w)] + [(y, 0) for y in range(h)] + [(y, w - 1) for y in range(h)]
    while stack:
        y, x = stack.pop()
        if y < 0 or x < 0 or y >= h or x >= w or seen[y, x] or alpha[y, x]:
            continue
        seen[y, x] = True
        stack += [(y + 1, x), (y - 1, x), (y, x + 1), (y, x - 1)]
    return seen


def touching(mask):
    p = np.pad(mask, 1)
    return p[:-2, 1:-1] | p[2:, 1:-1] | p[1:-1, :-2] | p[1:-1, 2:]


def main():
    im = np.array(Image.open(SRC).convert('RGBA')).astype(np.int32)
    rgb = im[..., :3]
    lum = rgb.mean(axis=2)
    sat = rgb.max(axis=2) - rgb.min(axis=2)
    removed = 0
    for _ in range(3):
        out = outside(im[..., 3] > 0)
        fringe = (im[..., 3] > 0) & touching(out) & (lum > 120) & (sat < 48)
        n = int(fringe.sum())
        if n == 0:
            break
        im[fringe] = 0
        removed += n
    out = outside(im[..., 3] > 0)
    edge = (im[..., 3] > 0) & touching(out)
    # el borde que queda, oscuro: los grises se pasan a la línea; los colores se oscurecen (no pierden su tono)
    grey = edge & (sat < 48) & (lum > 70)
    im[grey, 0], im[grey, 1], im[grey, 2] = LINE
    colour = edge & ~grey & (lum > 110)
    im[colour, :3] = im[colour, :3] * 2 // 5
    Image.fromarray(im.astype(np.uint8), 'RGBA').save(SRC)
    shutil.copyfile(SRC, OUT)
    print(f'quitados {removed} píxeles claros del contorno; {int(grey.sum())} grises y {int(colour.sum())} de color oscurecidos')


if __name__ == '__main__':
    main()
