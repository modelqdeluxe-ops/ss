"""Las grietas de la corrosión del Vacío en pantalla (TF Client 1.3.96, VoidLightClient), sin abrir el juego.

    python3 -I tools/void/corrosion_textura.py [--ver carpeta]

Escribe src/main/resources/assets/tfclient/textures/gui/corrosion_grietas.png (1024 × 576): grietas violetas que brillan
y salen de los bordes hacia dentro, ramificadas, con un halo. El cliente la dibuja más grande que la pantalla con poca
corrosión (solo asoman las puntas por los bordes) y la va encogiendo hasta su tamaño al máximo (las grietas llegan a
~un cuarto de la pantalla; el centro siempre queda limpio). Semilla fija: sale siempre igual.
--ver: además, cómo queda sobre una escena oscura y una clara a 0,3 / 0,6 / 1,0 de corrosión.
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient', 'textures', 'gui',
                   'corrosion_grietas.png')
W, H = 1024, 576
SS = 3                      # supermuestreo (bordes suaves)
CORE = (236, 196, 255)      # el filo de la grieta, casi blanco violeta
GLOW = (150, 60, 235)       # su halo
REACH = 0.44                # hasta dónde entran, en fracción de la mitad corta de la pantalla (las largas, un poco más)


def cracks(rng):
    """Segmentos (x0, y0, x1, y1, grosor) de las grietas, en píxeles de la textura."""
    segs = []
    seeds = []
    # sitios de salida repartidos por el borde (más en las esquinas, donde se nota menos)
    for i in range(30):
        t = (i + rng.uniform(-0.35, 0.35)) / 30
        p = t * 2 * (W + H)
        if p < W:
            seeds.append((p, 0))
        elif p < W + H:
            seeds.append((W, p - W))
        elif p < 2 * W + H:
            seeds.append((2 * W + H - p, H))
        else:
            seeds.append((0, 2 * (W + H) - p))
    cx, cy = W / 2, H / 2
    for (x, y) in seeds:
        ang = math.atan2(cy - y, cx - x) + rng.uniform(-0.5, 0.5)
        long_one = rng.random() < 0.35
        reach = min(W, H) / 2 * REACH * (1.5 if long_one else rng.uniform(0.55, 1.0))
        grow(rng, segs, x, y, ang, reach, 4.2 if long_one else 3.2, 0)
    return segs


def grow(rng, segs, x, y, ang, reach, width, depth):
    step = 9.0
    n = int(reach / step)
    for k in range(n):
        ang += rng.uniform(-0.38, 0.38)
        # que no se vaya hacia fuera ni demasiado de lado
        nx, ny = x + math.cos(ang) * step, y + math.sin(ang) * step
        w = width * (1 - 0.75 * k / max(1, n))
        segs.append((x, y, nx, ny, max(0.7, w)))
        x, y = nx, ny
        if depth < 2 and k > 1 and rng.random() < 0.22:
            grow(rng, segs, x, y, ang + rng.choice((-1, 1)) * rng.uniform(0.5, 1.0), reach * (n - k) / n * 0.7,
                 w * 0.75, depth + 1)


def render(segs):
    big = Image.new('L', (W * SS, H * SS), 0)
    d = ImageDraw.Draw(big)
    for (x0, y0, x1, y1, w) in segs:
        d.line([(x0 * SS, y0 * SS), (x1 * SS, y1 * SS)], fill=255, width=max(1, int(round(w * SS))))
        r = w * SS / 2
        d.ellipse([x1 * SS - r, y1 * SS - r, x1 * SS + r, y1 * SS + r], fill=255)
    core = np.asarray(big.resize((W, H), Image.LANCZOS)).astype(np.float32) / 255
    halo = np.asarray(Image.fromarray((core * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(7))).astype(np.float32) / 255
    halo = np.clip(halo * 2.6, 0, 1)
    wide = np.asarray(Image.fromarray((core * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(22))).astype(np.float32) / 255
    wide = np.clip(wide * 3.0, 0, 1)
    # un velo oscuro violeta muy suave en los bordes (para que el brillo se lea también en lo claro)
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    r = np.sqrt(((xx - W / 2) / (W / 2)) ** 2 + ((yy - H / 2) / (H / 2)) ** 2)
    edge = np.clip((r - 0.72) / 0.62, 0, 1) ** 1.6
    a_core = core
    a_halo = halo * 0.75
    a_wide = wide * 0.35
    a_dark = edge * 0.5
    # composición «sobre» de abajo arriba: oscuro → halo ancho → halo → filo
    rgb = np.zeros((H, W, 3), np.float32)
    a = np.zeros((H, W), np.float32)
    for col, al in (((14, 4, 30), a_dark), ((90, 30, 160), a_wide), (GLOW, a_halo), (CORE, a_core)):
        c = np.array(col, np.float32) / 255
        rgb = rgb * (1 - al[..., None]) * a[..., None] + c * al[..., None]
        na = al + a * (1 - al)
        rgb = np.where(na[..., None] > 1e-5, rgb / np.maximum(na, 1e-5)[..., None], 0)
        a = na
    out = np.dstack([np.clip(rgb * 255, 0, 255), np.clip(a * 255, 0, 255)]).astype(np.uint8)
    return Image.fromarray(out, 'RGBA')


def preview(tex, folder):
    """Sobre una escena oscura (el Páramo de noche) y una clara, a varias corrosiones (como VoidLightClient.draw)."""
    os.makedirs(folder, exist_ok=True)
    sw, sh = 960, 540
    scenes = {'oscura': Image.new('RGBA', (sw, sh), (28, 18, 40, 255)), 'clara': Image.new('RGBA', (sw, sh), (170, 175, 160, 255))}
    for name, bg in scenes.items():
        row = Image.new('RGBA', (sw * 3, sh), (0, 0, 0, 255))
        for i, c in enumerate((0.3, 0.6, 1.0)):
            s, alpha = scale_alpha(c)
            w, h = int(sw * s), int(sh * s)
            t = tex.resize((w, h), Image.BILINEAR)
            arr = np.asarray(t).astype(np.float32)
            arr[..., 3] *= alpha
            t = Image.fromarray(arr.astype(np.uint8), 'RGBA')
            frame = bg.copy()
            frame.alpha_composite(t, ((sw - w) // 2, (sh - h) // 2)) if w <= sw else frame.alpha_composite(
                t.crop(((w - sw) // 2, (h - sh) // 2, (w - sw) // 2 + sw, (h - sh) // 2 + sh)))
            row.paste(frame, (i * sw, 0))
        row.convert('RGB').save(os.path.join(folder, f'corrosion_{name}.png'))


def scale_alpha(c):
    """Lo mismo que VoidLightClient.draw: tamaño (veces la pantalla) y opacidad de las grietas con corrosión c (0..1)."""
    def smooth(a, b, x):
        t = min(1, max(0, (x - a) / (b - a)))
        return t * t * (3 - 2 * t)
    grow = smooth(0.0, 0.95, c)
    return 1.45 - 0.45 * grow, smooth(0.02, 0.25, c) * 0.72


def main():
    rng = __import__('random').Random(1396)
    tex = render(cracks(rng))
    tex.save(OUT, optimize=True)
    a = np.asarray(tex)[..., 3].astype(np.float32) / 255
    cy, cx = slice(H // 4, 3 * H // 4), slice(W // 4, 3 * W // 4)
    print('corrosion_grietas.png: %dx%d, %.1f kB; opacidad media %.3f (centro %.3f)' % (
        W, H, os.path.getsize(OUT) / 1024, a.mean(), a[cy, cx].mean()))
    if '--ver' in sys.argv:
        preview(tex, sys.argv[sys.argv.index('--ver') + 1])


if __name__ == '__main__':
    main()
