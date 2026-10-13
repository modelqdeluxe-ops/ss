"""Texturas del diseño 1.4 de las apps del pad (PadSkin.java). Solo escribe estas, sin tocar las demás:

    python3 tools/pad/build_skin.py

Qué sale (en src/main/resources/assets/tfclient/textures/gui/pad/):
- stripes.png (16x16): rayas en diagonal blancas y muy suaves, en mosaico: el dibujo de las cabeceras de las apps, de
  las vitrinas de las tarjetas y de las fichas. Se mueven despacio (PadSkin.stripes).
- bokeh.png (64x64): una luz redonda y suave (blanca, el color lo pone el pad), para el fondo animado del cristal.
- rays.png (64x64): rayos de luz desde el centro, para detrás de lo elegido (tarjetas marcadas, la ficha grande).
- land_far / land_mid / land_near.png (256x44): el paisaje del fondo del cristal, en gris (lo tiñe el tema).
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


# El paisaje del fondo del cristal (1.3.63, TFPadScreen.landscape): tres capas en mosaico horizontal (LAND_W de ancho,
# se repiten sin costura) de LAND_H de alto, en gris (el pad las tiñe del color del tema): montañas lejanas con nieve,
# colinas con robles y abetos, y delante lomas con el castillo de Tierras Fantásticas y unas casitas. El gris marca la
# luz (cara de arriba a la izquierda más clara): al teñirlas, la forma se lee igual en todos los temas.
LAND_W, LAND_H = 256, 44


def _profile(base, waves):
    """Altura (en px desde abajo) de cada columna: base + suma de ondas enteras (así el mosaico no tiene costura),
    redondeada a pasos de 2 px (bordes de bloques, como en Minecraft)."""
    out = []
    for x in range(LAND_W):
        h = base + sum(a * math.sin(2 * math.pi * f * x / LAND_W + ph) for a, f, ph in waves)
        out.append(int(round(h / 2)) * 2)
    return out


def _land(heights, shade_top=255, body=212, edge=236):
    im = Image.new('RGBA', (LAND_W, LAND_H), (0, 0, 0, 0))
    px = im.load()
    for x, h in enumerate(heights):
        for y in range(LAND_H - h, LAND_H):
            d = y - (LAND_H - h)
            v = shade_top if d == 0 else edge if d == 1 else body
            # un poco más oscuro hacia abajo (profundidad)
            v = max(0, v - (y - (LAND_H - h)) // 6)
            px[x, y] = (v, v, v, 255)
    return im


def land_far():
    """Montañas lejanas: picos con nieve (los más altos, en blanco)."""
    hs = _profile(24, [(9, 3, 0.4), (5, 7, 1.9), (2.5, 13, 0.3)])
    im = _land(hs, 255, 220, 238)
    px = im.load()
    top = max(hs)
    for x, h in enumerate(hs):
        if h >= top - 8:  # nieve en las cumbres
            for d in range(0, 3 + (h - (top - 8)) // 3):
                y = LAND_H - h + d
                if 0 <= y < LAND_H:
                    px[x, y] = (255, 255, 255, 255)
    return im


def _tree(px, x, ground, kind, v_hi=246, v_lo=196):
    """Un árbol de bloques: roble (copa cuadrada de 7x6) o abeto (triángulo escalonado); tronco de 1x3."""
    if kind == 0:
        for dy in range(3):
            px[x % LAND_W, ground - 1 - dy] = (v_lo, v_lo, v_lo, 255)
        for dy in range(6):
            for dx in range(-3, 4):
                if (dy in (0, 5)) and abs(dx) == 3:
                    continue
                y = ground - 4 - dy
                v = v_hi if (dy >= 4 and dx <= 0) else 222
                px[(x + dx) % LAND_W, y] = (v, v, v, 255)
    else:
        for dy in range(2):
            px[x % LAND_W, ground - 1 - dy] = (v_lo, v_lo, v_lo, 255)
        for i, half in enumerate((4, 4, 3, 3, 2, 2, 1, 1, 0)):
            y = ground - 3 - i
            for dx in range(-half, half + 1):
                v = v_hi if dx < 0 else 214
                px[(x + dx) % LAND_W, y] = (v, v, v, 255)


def land_mid():
    """Colinas con árboles (robles y abetos alternos)."""
    hs = _profile(14, [(5, 2, 2.2), (3, 5, 0.7), (1.5, 11, 1.4)])
    im = _land(hs, 250, 205, 230)
    px = im.load()
    x, i = 6, 0
    while x < LAND_W - 4:
        _tree(px, x, LAND_H - hs[x] + 1, i % 3 == 1)
        x += 11 + (i * 7) % 9
        i += 1
    return im


def land_near():
    """Lomas de delante con el castillo (dos torres con almenas, la torre del homenaje con su bandera y ventanas) y dos
    casitas con su tejado."""
    hs = _profile(7, [(2.5, 3, 0.9), (1.5, 8, 2.6)])
    im = _land(hs, 246, 196, 224)
    px = im.load()

    def box(x0, y0, x1, y1, v):
        for y in range(y0, y1):
            for x in range(x0, x1):
                px[x % LAND_W, y] = (v, v, v, 255)

    cx = 176
    ground = LAND_H - max(hs[cx - 16:cx + 16])
    body, light, dark = 214, 240, 168
    box(cx - 14, ground - 12, cx + 15, ground + 2, body)       # muralla
    for x in range(cx - 14, cx + 15, 4):                         # almenas de la muralla
        box(x, ground - 14, x + 2, ground - 12, body)
    for tx in (cx - 16, cx + 11):                                # torres
        box(tx, ground - 22, tx + 6, ground + 2, body)
        box(tx, ground - 22, tx + 1, ground + 2, light)
        for x in range(tx, tx + 6, 2):
            box(x, ground - 24, x + 1, ground - 22, body)
        box(tx + 2, ground - 17, tx + 4, ground - 14, dark)      # ventana
    box(cx - 5, ground - 30, cx + 6, ground - 12, body)          # torre del homenaje
    box(cx - 5, ground - 30, cx - 4, ground - 12, light)
    for x in range(cx - 5, cx + 6, 2):
        box(x, ground - 32, x + 1, ground - 30, body)
    box(cx, ground - 39, cx + 1, ground - 32, light)             # asta
    box(cx + 1, ground - 39, cx + 6, ground - 36, 250)           # bandera
    box(cx - 2, ground - 25, cx + 3, ground - 21, dark)          # rosetón
    box(cx - 3, ground - 7, cx + 4, ground + 2, dark)             # puerta
    box(cx - 2, ground - 8, cx + 3, ground - 7, dark)
    for hx in (40, 98):                                          # casitas
        g2 = LAND_H - hs[hx]
        box(hx, g2 - 6, hx + 9, g2 + 1, body)
        for i in range(5):
            box(hx - 1 + i, g2 - 7 - i, hx + 10 - i, g2 - 6 - i, light if i < 4 else body)
        box(hx + 3, g2 - 4, hx + 5, g2 + 1, dark)
        box(hx + 6, g2 - 4, hx + 8, g2 - 2, dark)
    return im


FILES = {'stripes.png': stripes, 'bokeh.png': bokeh, 'rays.png': rays, 'land_far.png': land_far,
         'land_mid.png': land_mid, 'land_near.png': land_near}


def build(out_dir=OUT_DIR):
    os.makedirs(out_dir, exist_ok=True)
    for name, fn in FILES.items():
        fn().save(os.path.join(out_dir, name))
    return list(FILES)


if __name__ == '__main__':
    print('ok', build())
