"""Las texturas de la Luna de Sangre (TF Client 1.3.175), hechas aquí (nada copiado de Minecraft).

    python3 tools/luna/texturas_luna.py

Escribe en assets/tfclient/textures/:
  entity/luna_ojos/*.png      las capas de ojos rojos que brillan (RenderType.eyes: solo suman luz): los píxeles de los
                              ojos de cada modelo en rojo vivo y un halo tenue alrededor, en la cara de delante de la
                              cabeza. Dónde están los ojos se midió en las texturas de Minecraft 1.20.1 (los píxeles
                              oscuros o blancos de la cara): zombi, husk, ahogado, esqueleto, stray y esqueleto wither en
                              la fila 12 (u 9-10 y 13-14); creeper, 2x2 en las filas 10-11; aldeano zombi, saqueador,
                              vindicador, invocador y bruja en la fila 14.
  environment/luna_sangre_fases.png   la luna del cielo de Minecraft (sus 8 fases, todas llenas y rojas) mientras dura
  environment/luna_sangre.png         la luna grande (64x64, pixel art) que dibuja BloodMoonClient encima
  environment/luna_sangre_halo.png    su resplandor (aditivo)
  gui/luna_vineta.png                 la viñeta roja de la pantalla
"""
import os

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient', 'textures'))

CORE = (255, 46, 30)      # el ojo
HOT = (255, 120, 80)      # el centro del ojo (un poco más claro)
GLOW = (110, 6, 4)        # el halo en la cara (suma poquito)

# (archivo, tamaño, cara de delante de la cabeza (u0, v0, u1, v1), píxeles de los ojos)
ROW12 = [(9, 12), (10, 12), (13, 12), (14, 12)]
ROW14 = [(9, 14), (10, 14), (13, 14), (14, 14)]
MASKS = [
    ('humanoide', (64, 64), (8, 8, 16, 16), ROW12),         # zombi, husk, ahogado
    ('esqueleto', (64, 32), (8, 8, 16, 16), ROW12),         # esqueleto, stray, esqueleto wither
    ('creeper', (64, 32), (8, 8, 16, 16), [(9, 10), (10, 10), (9, 11), (10, 11), (13, 10), (14, 10), (13, 11), (14, 11)]),
    ('aldeano', (64, 64), (8, 10, 16, 20), ROW14),          # aldeano zombi, saqueador, vindicador, invocador
    ('bruja', (64, 128), (8, 10, 16, 20), ROW14),
]


def eye_mask(size, face, eyes):
    img = np.zeros((size[1], size[0], 4), np.uint8)
    u0, v0, u1, v1 = face
    eyeset = set(eyes)
    for (u, v) in eyes:
        for du in (-1, 0, 1):
            for dv in (-1, 0, 1):
                x, y = u + du, v + dv
                if (x, y) in eyeset or not (u0 <= x < u1 and v0 <= y < v1):
                    continue
                if img[y, x, 3] == 0:
                    k = 1.0 if du == 0 or dv == 0 else 0.55
                    img[y, x] = (int(GLOW[0] * k), int(GLOW[1] * k), int(GLOW[2] * k), 255)
    for i, (u, v) in enumerate(eyes):
        # el píxel de dentro de cada ojo (el que da a la nariz), más caliente
        inner = (u == 10 or u == 13)
        img[v, u] = (*(HOT if inner else CORE), 255)
    return Image.fromarray(img, 'RGBA')


def moon(px, seed=1175):
    """Una luna llena roja, pixel art: degradado de carmesí a naranja, mares oscuros y cráteres con el borde claro."""
    r = np.random.default_rng(seed)
    y, x = np.mgrid[0:px, 0:px] + 0.5
    c = px / 2
    d = np.hypot(x - c, y - c) / (px * 0.47)
    inside = d <= 1.0
    # el degradado: más claro arriba a la izquierda
    light = 0.55 + 0.45 * np.clip(1 - (((x - px * 0.38) ** 2 + (y - px * 0.36) ** 2) ** 0.5) / (px * 0.75), 0, 1)
    base = np.stack([200 + 55 * light, 30 + 70 * light ** 2, 18 + 30 * light ** 2], -1)
    # mares (manchas grandes y suaves)
    mare = np.zeros((px, px))
    for _ in range(5):
        mx, my, mr = r.uniform(0.25, 0.75) * px, r.uniform(0.25, 0.75) * px, r.uniform(0.12, 0.22) * px
        mare = np.maximum(mare, np.clip(1 - np.hypot(x - mx, y - my) / mr, 0, 1))
    base *= (1 - 0.35 * np.clip(mare * 1.6, 0, 1))[..., None]
    # cráteres
    for _ in range(int(px * 0.55)):
        cx, cy, cr = r.uniform(0.15, 0.85) * px, r.uniform(0.15, 0.85) * px, r.uniform(0.03, 0.09) * px
        dd = np.hypot(x - cx, y - cy)
        floor = dd < cr * 0.8
        rim = (dd >= cr * 0.8) & (dd < cr * 1.1) & ((x - cx) + (y - cy) < 0)
        base[floor] *= 0.78
        base[rim] = np.minimum(255, base[rim] * 1.18)
    # el borde, más oscuro (es una esfera)
    base *= (0.75 + 0.25 * np.sqrt(np.clip(1 - d ** 2, 0, 1)))[..., None]
    # cuantizar a pocos tonos (aspecto pixel art)
    base = np.round(base / 12) * 12
    a = np.where(inside, 255, 0)
    img = np.dstack([np.clip(base, 0, 255), a]).astype(np.uint8)
    return Image.fromarray(img, 'RGBA')


def halo(px):
    y, x = np.mgrid[0:px, 0:px] + 0.5
    d = np.hypot(x - px / 2, y - px / 2) / (px / 2)
    edge = np.clip((1 - d) / 0.3, 0, 1)
    a = np.clip(np.exp(-((d / 0.38) ** 2)) * 0.85 + np.exp(-((d / 0.7) ** 2)) * 0.3, 0, 1) * (edge * edge * (3 - 2 * edge))
    img = np.zeros((px, px, 4), np.uint8)
    img[..., 0], img[..., 1], img[..., 2] = 255, 52, 30
    img[..., 3] = (a * 255).astype(np.uint8)
    return Image.fromarray(img, 'RGBA')


def vignette(px):
    y, x = np.mgrid[0:px, 0:px] + 0.5
    d = np.hypot((x - px / 2) / (px / 2), (y - px / 2) / (px / 2)) / np.sqrt(2)
    t = np.clip((d - 0.42) / 0.58, 0, 1)
    a = t * t * (3 - 2 * t) * 0.92
    img = np.zeros((px, px, 4), np.uint8)
    img[..., 0], img[..., 1], img[..., 2] = 92, 0, 4
    img[..., 3] = (a * 255).astype(np.uint8)
    return Image.fromarray(img, 'RGBA')


def save(img, rel):
    path = os.path.join(TEX, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)
    print(rel, img.size)


def main():
    for name, size, face, eyes in MASKS:
        save(eye_mask(size, face, eyes), f'entity/luna_ojos/{name}.png')
    small = moon(32)
    phases = Image.new('RGBA', (128, 64))
    for i in range(8):
        phases.paste(small, ((i % 4) * 32, (i // 4) * 32))
    save(phases, 'environment/luna_sangre_fases.png')
    save(moon(64, seed=1176), 'environment/luna_sangre.png')
    save(halo(128), 'environment/luna_sangre_halo.png')
    save(vignette(256), 'gui/luna_vineta.png')


if __name__ == '__main__':
    main()
