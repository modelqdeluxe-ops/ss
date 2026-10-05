"""Capas para animar la pantalla de carga de TF Client, sacadas de la propia imagen.

Uso: python3 tools/make_layers.py   (desde la carpeta del proyecto)

Escribe en assets/:
- mask_island_N.png   zona de cada isla flotante (bordes muy suaves): una copia de la imagen ahí sube y baja
- mask_dragon.png     lo mismo para el dragón
- mask_water.png      cascadas (máscara en alfa) para el agua que corre
- mask_title.png      caras blancas de las letras del título, para el brillo
- mask_sky.png        el cielo, para que las nubes y los rayos pasen por detrás de castillos e islas
- lights.png          las luces cálidas (ventanas, linternas, antorchas) aisladas, para que titilen
- layers.json         posiciones y cajas que usa index.html
"""
import json
import os

import numpy as np
from PIL import Image, ImageFilter
from scipy import ndimage as ndi

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..')
A = os.path.join(ROOT, 'assets')
SRC = os.path.join(A, 'loading_background.png')

img = Image.open(SRC).convert('RGB')
W, H = img.size
rgb = np.asarray(img).astype(np.float32)
r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
luma = 0.299 * r + 0.587 * g + 0.114 * b


def save_mask(m, name, blur=1.0):
    """Máscara 0..1 → PNG blanco con esa alfa (para CSS mask-image)."""
    a = Image.fromarray((np.clip(m, 0, 1) * 255).astype(np.uint8), 'L')
    if blur:
        a = a.filter(ImageFilter.GaussianBlur(blur))
    out = Image.new('RGBA', (W, H), (255, 255, 255, 0))
    out.putalpha(a)
    out.save(os.path.join(A, name), optimize=True)
    return a


def box(x0, y0, x1, y1):
    m = np.zeros((H, W), bool)
    m[y0:y1, x0:x1] = True
    return m


# ---- Detalle local: el cielo es liso, castillos/islas/dragón tienen textura ----
gy, gx = np.gradient(ndi.gaussian_filter(luma, 1.2))
detail = ndi.gaussian_filter(np.hypot(gx, gy), 6)

# ---- Cielo ----
sky = (detail < 2.4) & (np.arange(H)[:, None] < 640)
sky = ndi.binary_opening(sky, iterations=2)
sky = ndi.binary_closing(sky, iterations=3)
# fuera las zonas que no son cielo aunque sean lisas (título, sol)
sky &= ~box(570, 380, 1360, 650)
lab, n = ndi.label(sky)
sizes = ndi.sum(sky, lab, range(1, n + 1))
keep = np.isin(lab, [i + 1 for i, s in enumerate(sizes) if s > 4000])
sky = keep
save_mask(sky.astype(float), 'mask_sky.png', blur=4)

# ---- Cascadas: agua clara (azul claro / blanca) dentro de sus cajas ----
FALLS = [(100, 300, 185, 475), (380, 395, 445, 545), (570, 630, 620, 775), (695, 900, 755, 1065),
         (1095, 160, 1150, 400), (1615, 60, 1655, 220), (1530, 110, 1570, 215), (1580, 110, 1600, 200),
         (760, 50, 840, 235), (920, 210, 945, 330), (1240, 210, 1255, 300)]
water = np.zeros((H, W), bool)
for x0, y0, x1, y1 in FALLS:
    zone = box(x0, y0, x1, y1)
    clear = ((b > 200) & (b - r > 25) & (g > 150)) | ((r > 215) & (g > 225) & (b > 235))
    water |= zone & clear
water = ndi.binary_closing(water, iterations=1)
save_mask(water.astype(float), 'mask_water.png', blur=0.8)

# ---- Título: caras blancas de las letras ----
title = box(585, 395, 1340, 632) & (np.minimum(np.minimum(r, g), b) > 205)
title = ndi.binary_opening(title, iterations=1)
save_mask(title.astype(float), 'mask_title.png', blur=0.6)

# ---- Luces cálidas: puntos más brillantes que su entorno y de color cálido, solo en ventanas y linternas ----
LIGHT_ZONES = [(60, 40, 720, 560),                      # castillo y casas de la izquierda
               (0, 560, 70, 690), (220, 450, 300, 530), (370, 650, 430, 740),   # linternas izquierda
               (500, 740, 880, 1000),                   # aldea y casas del río
               (1630, 650, 1720, 760), (1850, 620, 1920, 730), (1480, 900, 1580, 1030),
               (1660, 580, 1730, 660), (1830, 550, 1910, 630), (1380, 1000, 1460, 1080)]  # linternas derecha
around = ndi.gaussian_filter(luma, 9)
warm = (r > 205) & (g > 120) & (b < 170) & (r - b > 70)
lights = warm & (luma - around > 34)
zones = np.zeros((H, W), bool)
for bx in LIGHT_ZONES:
    zones |= box(*bx)
lights &= zones
lights = ndi.binary_dilation(lights, iterations=1)
la = np.zeros((H, W, 4), np.uint8)
la[..., :3] = rgb.astype(np.uint8)
la[..., 3] = (lights * 255).astype(np.uint8)
Image.fromarray(la, 'RGBA').filter(ImageFilter.GaussianBlur(0.7)).save(os.path.join(A, 'lights.png'), optimize=True)


# ---- Islas flotantes y dragón: zonas de bordes muy suaves que se mueven unos píxeles (sin recortar) ----
def soft_ellipse(cx, cy, rx, ry, feather):
    yy, xx = np.mgrid[0:H, 0:W]
    d = np.sqrt(((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2)
    edge = feather / max(rx, ry)
    return np.clip((1 + edge - d) / edge, 0, 1)


FLOATS = {
    'island_0': (770, 128, 92, 112, 34),
    'island_1': (1100, 205, 178, 205, 46),
    'island_2': (838, 320, 62, 66, 26),
    'island_3': (1597, 120, 84, 104, 30),
}
meta = {'size': [W, H], 'objects': {}}
for name, (cx, cy, rx, ry, fe) in FLOATS.items():
    m = soft_ellipse(cx, cy, rx, ry, fe)
    m = ndi.gaussian_filter(m, 4)
    pad = int(fe * max(rx, ry) / min(rx, ry)) + 16   # el borde suave llega hasta (1 + feather/r) veces el radio
    x0, y0 = max(0, cx - rx - pad), max(0, cy - ry - pad)
    x1, y1 = min(W, cx + rx + pad), min(H, cy + ry + pad)
    a = Image.fromarray((m[y0:y1, x0:x1] * 255).astype(np.uint8), 'L')
    out = Image.new('RGBA', a.size, (255, 255, 255, 0))
    out.putalpha(a)
    out.save(os.path.join(A, 'mask_' + name + '.png'), optimize=True)
    meta['objects'][name] = {'x': int(x0), 'y': int(y0), 'w': int(x1 - x0), 'h': int(y1 - y0)}

# dragón: sus píxeles oscuros / morados, engordados y muy difuminados (sin la isla ni la montaña de su caja)
dx0, dy0, dx1, dy1 = 1440, 0, 1920, 560
dragon = box(dx0, dy0, dx1, dy1) & ((luma < 115) | ((b > g + 30) & (r > g + 10)))
dragon &= ~box(1440, 0, 1765, 228)       # isla de la derecha y su cielo
dragon &= ~box(1440, 360, 1665, 560)     # montaña de debajo
dragon &= ~box(1440, 500, 1920, 560)     # nada por debajo de las garras
dragon = ndi.binary_opening(dragon, iterations=2)
lab, n = ndi.label(dragon)
sizes = ndi.sum(dragon, lab, range(1, n + 1))
dragon = np.isin(lab, [i + 1 for i, s_ in enumerate(sizes) if s_ > 300])
dragon = ndi.binary_dilation(dragon, iterations=10)
dm = ndi.gaussian_filter(dragon.astype(np.float32), 9)
dm = np.clip(dm * 1.35, 0, 1)
a = Image.fromarray((dm[dy0:dy1, dx0:dx1] * 255).astype(np.uint8), 'L')
out = Image.new('RGBA', a.size, (255, 255, 255, 0))
out.putalpha(a)
out.save(os.path.join(A, 'mask_dragon.png'), optimize=True)
meta['objects']['dragon'] = {'x': dx0, 'y': dy0, 'w': dx1 - dx0, 'h': dy1 - dy0}

with open(os.path.join(A, 'layers.json'), 'w') as fh:
    json.dump(meta, fh, indent=1)
print('listo:', {k: int(v.sum()) for k, v in [('cielo', sky), ('agua', water), ('titulo', title), ('luces', lights), ('dragon', dragon)]})


# ---- Brillo alrededor del título (la misma máscara, muy difuminada) ----
glow = ndi.gaussian_filter(ndi.binary_dilation(title, iterations=4).astype(np.float32), 10)
save_mask(np.clip(glow * 1.6, 0, 1), 'mask_title_glow.png', blur=0)

# ---- Destellos del río: puntos sobre agua clara del río, elegidos con una semilla fija ----
river = box(905, 885, 1455, 1072) & (luma > 150) & (detail < 9)
river &= ~box(905, 950, 1040, 1072)     # isla con casa de la izquierda
ys, xs = np.nonzero(river)
rng = np.random.default_rng(7)
pick = rng.choice(len(xs), size=min(46, len(xs)), replace=False)
glints = [[int(xs[i]), int(ys[i])] for i in pick]
meta = json.load(open(os.path.join(A, 'layers.json')))
meta['glints'] = glints
with open(os.path.join(A, 'layers.json'), 'w') as fh:
    json.dump(meta, fh, indent=1)
print('destellos del río:', len(glints))
