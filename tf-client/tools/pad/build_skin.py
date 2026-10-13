"""Texturas del diseño 1.4 de las apps del pad (PadSkin.java). Solo escribe estas, sin tocar las demás:

    python3 tools/pad/build_skin.py

Qué sale (en src/main/resources/assets/tfclient/textures/gui/pad/):
- stripes.png (16x16): rayas en diagonal blancas y muy suaves, en mosaico: el dibujo de las cabeceras de las apps, de
  las vitrinas de las tarjetas y de las fichas. Se mueven despacio (PadSkin.stripes).
- bokeh.png (64x64): una luz redonda y suave (blanca, el color lo pone el pad), para el fondo animado del cristal.
- rays.png (64x64): rayos de luz desde el centro, para detrás de lo elegido (tarjetas marcadas, la ficha grande).
build_pad.py también las genera (llama a build() de aquí) cuando se rehace todo el pad.
"""
import math
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient',
                                        'textures', 'gui', 'pad'))


def stripes():
    """Rayas de 3 px cada 8 px en diagonal (bajan hacia la derecha), blanco al 100 %: el pad les pone el alfa."""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            k = (x + y) % 8
            if k < 3:
                im.putpixel((x, y), (255, 255, 255, 255 if k == 1 else 150))
    return im


def bokeh():
    """Luz redonda de borde suave: casi plana en el centro y se apaga hacia fuera (como una luz desenfocada)."""
    n = 64
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    for y in range(n):
        for x in range(n):
            d = math.hypot(x + 0.5 - n / 2, y + 0.5 - n / 2) / (n / 2)
            if d < 1:
                a = 1.0 if d < 0.55 else (1 - (d - 0.55) / 0.45) ** 1.7
                # un borde un poco más brillante, como las luces desenfocadas de verdad
                a = min(1.0, a * 0.82 + (0.18 if 0.6 < d < 0.8 else 0))
                im.putpixel((x, y), (255, 255, 255, int(255 * a)))
    return im


def rays():
    """12 rayos de luz que se apagan hacia fuera y hacia los lados."""
    n = 64
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    for y in range(n):
        for x in range(n):
            dx, dy = x + 0.5 - n / 2, y + 0.5 - n / 2
            d = math.hypot(dx, dy) / (n / 2)
            if d >= 1 or d < 0.08:
                continue
            ang = math.atan2(dy, dx)
            k = abs(math.cos(ang * 6))  # 12 rayos
            beam = max(0.0, (k - 0.55) / 0.45) ** 1.4
            a = beam * (1 - d) ** 1.2 * min(1.0, (d - 0.08) / 0.12)
            if a > 0.01:
                im.putpixel((x, y), (255, 255, 255, int(255 * a)))
    return im


# El trozo del logo que se mete en el cristal (en píxeles de marco.png: 4 por píxel de pad). TFPadScreen lo vuelve a
# pintar encima del fondo animado, así las luces pasan por detrás del logo.
# (logo_over.png ya no hace falta: el marco se pinta encima del fondo del cristal, con su logo; build_pad.frame_textures)
FILES = {'stripes.png': stripes, 'bokeh.png': bokeh, 'rays.png': rays}


def build(out_dir=OUT_DIR):
    os.makedirs(out_dir, exist_ok=True)
    for name, fn in FILES.items():
        fn().save(os.path.join(out_dir, name))
    return list(FILES)


if __name__ == '__main__':
    print('ok', build())
