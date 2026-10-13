"""Texturas del diseño 1.4 de las apps del pad (PadSkin.java). Solo escribe estas, sin tocar las demás:

    python3 tools/pad/build_skin.py

Qué sale (en src/main/resources/assets/tfclient/textures/gui/pad/):
- stripes.png (16x16): rayas en diagonal blancas y muy suaves, en mosaico: el dibujo de las cabeceras de las apps, de
  las vitrinas de las tarjetas y de las fichas. Se mueven despacio (PadSkin.stripes).
- bokeh.png (64x64): una luz redonda y suave (blanca, el color lo pone el pad), para el fondo animado del cristal.
- rays.png (64x64): rayos de luz desde el centro, para detrás de lo elegido (tarjetas marcadas, la ficha grande).
- land_far / land_mid / land_near.png (480x44): el paisaje del fondo del cristal, en gris (lo tiñe el tema).
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


# El paisaje del fondo del cristal (TFPadScreen.landscape). 1.3.64: una sola panorámica de LAND_W de ancho (más que el
# cristal más ancho: nada se repite, un solo castillo), quieta y centrada; tres capas en gris (el pad las tiñe del color
# del tema): montañas lejanas con nieve, colinas con robles y abetos, y delante lomas con el castillo de Tierras
# Fantásticas y dos casitas con su chimenea (el humo lo anima el pad: CHIMNEYS). El gris marca la luz (cara de arriba a
# la izquierda más clara): al teñirlas, la forma se lee igual en todos los temas.
LAND_W, LAND_H = 480, 56
CASTLE_X = 334
COTTAGES = ((70, 0), (196, 1))  # (x, modelo)
CHIMNEYS = []  # (x, y) de la boca de cada chimenea, en píxeles de la textura (los rellena land_near)


def _profile(base, waves):
    """Altura (en px desde abajo) de cada columna: base + suma de ondas, redondeada a pasos de 2 px (bordes de bloques,
    como en Minecraft)."""
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
            v = max(0, v - d // 6)  # un poco más oscuro hacia abajo (profundidad)
            px[x, y] = (v, v, v, 255)
    return im


def land_far():
    """Montañas lejanas: picos con nieve (los más altos, en blanco)."""
    hs = _profile(24, [(9, 5, 0.4), (5, 11, 1.9), (2.5, 23, 0.3)])
    im = _land(hs, 255, 220, 238)
    px = im.load()
    top = max(hs)
    for x, h in enumerate(hs):
        if h >= top - 8:
            for d in range(0, 3 + (h - (top - 8)) // 3):
                y = LAND_H - h + d
                if 0 <= y < LAND_H:
                    px[x, y] = (255, 255, 255, 255)
    return im


def _tree(px, x, ground, kind, v_hi=246, v_lo=196):
    """Un árbol de bloques: roble (copa de 7x6 con esquinas recortadas) o abeto (triángulo escalonado)."""
    if not 4 <= x < LAND_W - 4:
        return
    if kind == 0:
        for dy in range(3):
            px[x, ground - 1 - dy] = (v_lo, v_lo, v_lo, 255)
        for dy in range(6):
            for dx in range(-3, 4):
                if (dy in (0, 5)) and abs(dx) == 3:
                    continue
                v = v_hi if (dy >= 4 and dx <= 0) else 222
                px[x + dx, ground - 4 - dy] = (v, v, v, 255)
    else:
        for dy in range(2):
            px[x, ground - 1 - dy] = (v_lo, v_lo, v_lo, 255)
        for i, half in enumerate((4, 4, 3, 3, 2, 2, 1, 1, 0)):
            for dx in range(-half, half + 1):
                v = v_hi if dx < 0 else 214
                px[x + dx, ground - 3 - i] = (v, v, v, 255)


def land_mid():
    """Colinas con árboles: robles y abetos, a distancias distintas (sin un patrón que se note)."""
    hs = _profile(14, [(5, 3, 2.2), (3, 7, 0.7), (1.5, 17, 1.4)])
    im = _land(hs, 250, 205, 230)
    px = im.load()
    x, i = 9, 0
    gaps = (13, 9, 17, 11, 21, 8, 15, 12, 19, 10)
    while x < LAND_W - 6:
        _tree(px, x, LAND_H - hs[x] + 1, 1 if i % 3 == 1 or i % 7 == 4 else 0)
        x += gaps[i % len(gaps)]
        i += 1
    return im


def _box(px, x0, y0, x1, y1, v):
    for y in range(y0, y1):
        for x in range(x0, x1):
            if 0 <= x < LAND_W and 0 <= y < LAND_H:
                px[x, y] = (v, v, v, 255)


def _cottage(px, x, ground, model):
    """Una casita: muros con su lado en sombra, tejado de tejas a dos aguas con alero, chimenea, puerta con marco y
    ventana encendida con su cruz (más clara que el muro: al teñir parece luz). model 1: más ancha, con dos ventanas."""
    w = 13 if model == 0 else 17
    wall_h = 7 if model == 0 else 8
    top = ground - wall_h
    _box(px, x, top, x + w, ground + 1, 212)
    _box(px, x + w - 2, top, x + w, ground + 1, 192)          # lado en sombra
    # tejado: filas que suben, cada una 1 px más estrecha por lado; tejas en filas alternas; alero de 2 px
    rows = w // 2 + 2
    for i in range(rows):
        y = top - 1 - i
        x0, x1 = x - 2 + i, x + w + 2 - i
        if x1 - x0 <= 0:
            break
        _box(px, x0, y, x1, y + 1, 236 if i % 2 == 0 else 222)
        _box(px, x0, y, x0 + 1, y + 1, 250)                      # el canto con luz
        _box(px, x1 - 1, y, x1, y + 1, 200)
    _box(px, x - 2, top - 1, x + w + 2, top, 186)               # sombra bajo el alero
    # chimenea en la vertiente derecha: asoma 3 px por encima del tejado, con su remate más ancho
    cx = x + w - 5
    roof_top = top - (x + w + 2 - cx)                          # lo alto del tejado en esa columna
    _box(px, cx, roof_top - 3, cx + 2, top - 2, 204)
    _box(px, cx, roof_top - 3, cx + 1, top - 2, 226)
    _box(px, cx - 1, roof_top - 4, cx + 3, roof_top - 3, 232)
    CHIMNEYS.append((cx + 1, roof_top - 4))
    # puerta con marco
    dx = x + 2 if model == 0 else x + (w - 3) // 2
    _box(px, dx - 1, ground - 6, dx + 4, ground + 1, 182)
    _box(px, dx, ground - 5, dx + 3, ground + 1, 150)
    _box(px, dx + 2, ground - 3, dx + 3, ground - 2, 222)      # pomo
    # ventanas encendidas con su cruz
    for wx in ([x + 7] if model == 0 else [x + 2, x + 12]):
        _box(px, wx - 1, top + 1, wx + 4, top + 6, 176)
        _box(px, wx, top + 2, wx + 3, top + 5, 252)
        _box(px, wx + 1, top + 2, wx + 2, top + 5, 196)
        _box(px, wx, top + 3, wx + 3, top + 4, 196)


def land_near():
    """Lomas de delante con el castillo (una vez: muralla con almenas, dos torres con tejado en punta, la torre del
    homenaje con su bandera, rosetón y puerta) y dos casitas distintas."""
    CHIMNEYS.clear()
    hs = _profile(7, [(2.5, 4, 0.9), (1.5, 9, 2.6)])
    cx = CASTLE_X
    for x in range(cx - 30, cx + 31):                            # la loma del castillo, un poco más alta
        k = 1 - abs(x - cx) / 30
        hs[x] = max(hs[x], int(round((7 + 3 * k) / 2)) * 2)
    for hx, _ in COTTAGES:                                       # suelo llano bajo cada casita
        g = max(hs[hx - 3:hx + 22])
        for x in range(hx - 3, hx + 22):
            hs[x] = g
    im = _land(hs, 246, 196, 224)
    px = im.load()
    ground = LAND_H - max(hs[cx - 18:cx + 18])
    body, light, dark = 214, 240, 168
    _box(px, cx - 14, ground - 12, cx + 15, ground + 2, body)   # muralla
    _box(px, cx - 14, ground - 12, cx + 15, ground - 11, light)
    for x in range(cx - 14, cx + 15, 4):                         # almenas
        _box(px, x, ground - 14, x + 2, ground - 12, body)
    for tx in (cx - 17, cx + 11):                                # torres con tejado en punta
        _box(px, tx, ground - 22, tx + 7, ground + 2, body)
        _box(px, tx, ground - 22, tx + 1, ground + 2, light)
        _box(px, tx + 6, ground - 22, tx + 7, ground + 2, 196)
        for i in range(5):
            _box(px, tx - 1 + i, ground - 23 - i * 2, tx + 8 - i, ground - 21 - i * 2, 236 if i % 2 == 0 else 224)
        _box(px, tx + 3, ground - 34, tx + 4, ground - 32, light)
        _box(px, tx + 2, ground - 17, tx + 5, ground - 13, dark)  # ventana
    _box(px, cx - 6, ground - 30, cx + 7, ground - 12, body)    # torre del homenaje
    _box(px, cx - 6, ground - 30, cx - 5, ground - 12, light)
    _box(px, cx + 6, ground - 30, cx + 7, ground - 12, 196)
    for x in range(cx - 6, cx + 7, 3):
        _box(px, x, ground - 32, x + 2, ground - 30, body)
    _box(px, cx, ground - 41, cx + 1, ground - 32, light)       # asta
    _box(px, cx + 1, ground - 41, cx + 7, ground - 38, 250)     # bandera
    _box(px, cx + 1, ground - 38, cx + 5, ground - 37, 236)
    _box(px, cx - 2, ground - 26, cx + 3, ground - 21, dark)    # rosetón
    _box(px, cx - 1, ground - 25, cx + 2, ground - 22, 200)
    _box(px, cx - 3, ground - 7, cx + 4, ground + 2, dark)      # puerta en arco
    _box(px, cx - 2, ground - 8, cx + 3, ground - 7, dark)
    for hx, model in COTTAGES:
        _cottage(px, hx, LAND_H - hs[hx] - 1, model)
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
    print('chimeneas (TFPadScreen.CHIMNEYS):', CHIMNEYS)
