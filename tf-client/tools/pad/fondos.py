"""Fondos de pantalla del menú principal del TF Pad (1.3.155).

(El dueño: «que pueda poner un fondo de pantalla cualquiera en el pad, un png que se autoajuste; agrega más fondos al
pad, o colores, los default») Dibuja los fondos ilustrados que trae el pad, todos a mano con numpy y PIL (sin imágenes de
nadie): cielos, montañas, bosques, mar, galaxia… al tamaño del cristal del pad (2,18:1). Los colores lisos/degradados van
en el código (PadWallpaper.COLORS). El jugador además puede meter los suyos en .minecraft/tfclient/fondos.

    python3 tools/pad/fondos.py            escribe los PNG y fondos.json
    python3 tools/pad/fondos.py --ver dir  además, una hoja con todos para mirarlos

Salida: src/main/resources/assets/tfclient/textures/gui/pad/fondos/<id>.png y assets/tfclient/pad/fondos.json
({id, nombre} en el orden del selector).
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient'))
OUT = os.path.join(RES, 'textures', 'gui', 'pad', 'fondos')
INDEX = os.path.join(RES, 'pad', 'fondos.json')
W, H = 1152, 528


def hexc(s):
    s = s.lstrip('#')
    return np.array([int(s[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float32)


def vgrad(stops, w=W, h=H):
    """Degradado vertical con paradas [(0..1, '#rrggbb'), ...]."""
    ys = np.linspace(0, 1, h, dtype=np.float32)[:, None]
    out = np.zeros((h, w, 3), np.float32)
    pos = [p for p, _ in stops]
    cols = [hexc(c) for _, c in stops]
    for ch in range(3):
        out[:, :, ch] = np.interp(ys, pos, [c[ch] for c in cols])
    return out


def fbm(w, h, seed, octaves=5, base=4, persistence=0.5):
    """Ruido fractal (valor) en 0..1, suave: rejillas aleatorias ampliadas con bicúbica."""
    rng = np.random.default_rng(seed)
    total = np.zeros((h, w), np.float32)
    amp, norm = 1.0, 0.0
    for o in range(octaves):
        gw, gh = base * 2 ** o + 1, max(2, int((base * 2 ** o) * h / w) + 1)
        grid = (rng.random((gh, gw)) * 255).astype(np.uint8)
        layer = np.asarray(Image.fromarray(grid).resize((w, h), Image.BICUBIC), np.float32) / 255
        total += layer * amp
        norm += amp
        amp *= persistence
    return total / norm


def glow(img, cx, cy, r, color, alpha, blur):
    """Un círculo de luz difuminado sumado encima."""
    layer = Image.new('RGB', img.size, (0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=tuple(int(c) for c in hexc(color)))
    layer = layer.filter(ImageFilter.GaussianBlur(blur))
    a = np.asarray(layer, np.float32) * alpha
    return Image.fromarray(np.clip(np.asarray(img, np.float32) + a, 0, 255).astype(np.uint8))


def ridge(w, base, amp, rough, seed, points=9):
    """Línea de montañas (desplazamiento del punto medio): y por cada x."""
    rng = np.random.default_rng(seed)
    n = 2 ** points + 1
    ys = np.zeros(n)
    ys[0], ys[-1] = rng.uniform(-1, 1) * amp, rng.uniform(-1, 1) * amp
    step, scale = n - 1, amp
    while step > 1:
        half = step // 2
        for i in range(half, n - 1, step):
            ys[i] = (ys[i - half] + ys[i + half]) / 2 + rng.uniform(-1, 1) * scale
        scale *= rough
        step = half
    xs = np.linspace(0, w, n)
    return np.interp(np.arange(w), xs, ys) + base


def fill_below(arr, ys, color, alpha=1.0, top_color=None):
    """Rellena por debajo de la línea ys (por columnas), con un degradado opcional desde la cresta."""
    h, w, _ = arr.shape
    yy = np.arange(h)[:, None]
    mask = yy >= ys[None, :]
    c = hexc(color)
    if top_color is not None:
        t = np.clip((yy - ys[None, :]) / 120.0, 0, 1)[..., None]
        col = hexc(top_color) * (1 - t) + c * t
    else:
        col = np.broadcast_to(c, arr.shape)
    m = mask[..., None] * alpha
    return arr * (1 - m) + col * m


def stars(arr, n, seed, y_max=1.0, big=0.04):
    rng = np.random.default_rng(seed)
    h, w, _ = arr.shape
    img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))
    layer = Image.new('RGB', (w, h))
    d = ImageDraw.Draw(layer)
    for _ in range(n):
        x, y = rng.uniform(0, w), rng.uniform(0, h * y_max) ** 1.0
        b = rng.uniform(0.3, 1) ** 2
        col = tuple(int(v) for v in (255 * b, 255 * b, 235 * b + 20 * b * rng.random()))
        if rng.random() < big:
            r = rng.uniform(1.2, 2.2)
            d.ellipse([x - r, y - r, x + r, y + r], fill=col)
            d.line([x - 5 * r, y, x + 5 * r, y], fill=tuple(c // 3 for c in col))
            d.line([x, y - 5 * r, x, y + 5 * r], fill=tuple(c // 3 for c in col))
        else:
            d.point((x, y), fill=col)
    soft = layer.filter(ImageFilter.GaussianBlur(1.2))
    out = np.asarray(img, np.float32) + np.asarray(layer, np.float32) + np.asarray(soft, np.float32) * 1.5
    return np.clip(out, 0, 255)


def pines(arr, ys, color, seed, density=0.06, size=(28, 70)):
    """Pinos de silueta sobre la línea ys."""
    rng = np.random.default_rng(seed)
    h, w, _ = arr.shape
    img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(img)
    col = tuple(int(v) for v in hexc(color))
    x = rng.uniform(0, 12)
    while x < w:
        t = rng.uniform(*size)
        base = ys[int(min(w - 1, max(0, x)))] + 4
        wid = t * 0.42
        for k in range(4):  # cuatro pisos de ramas
            top = base - t + k * t * 0.2
            bot = base - t * 0.25 + k * t * 0.18
            ww = wid * (0.45 + k * 0.2)
            d.polygon([(x, top), (x - ww, bot), (x + ww, bot)], fill=col)
        d.rectangle([x - 2, base - t * 0.2, x + 2, base + 6], fill=col)
        x += t * rng.uniform(0.35, 0.9) / max(0.2, density * 10)
    return np.asarray(img, np.float32)


BAYER = (np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]], np.float32) + 0.5) / 16 - 0.5


def dither(arr, seed=7):
    """Tramado ordenado (Bayer 4x4) para que los degradados no hagan bandas: pesa mucho menos que el grano aleatorio."""
    h, w, _ = arr.shape
    t = np.tile(BAYER, (h // 4 + 1, w // 4 + 1))[:h, :w, None]
    return np.clip(np.floor(arr + t + 0.5), 0, 255)


def save(arr, fid):
    os.makedirs(OUT, exist_ok=True)
    Image.fromarray(np.clip(dither(arr), 0, 255).astype(np.uint8)).save(os.path.join(OUT, f'{fid}.png'), optimize=True)


# ---------------------------------------------------------------------------------------------------------------------
# Los fondos
# ---------------------------------------------------------------------------------------------------------------------

def noche():
    a = vgrad([(0, '#070b24'), (0.55, '#1b1a4d'), (0.85, '#4a2f78'), (1, '#6d4592')])
    a = stars(a, 900, 1, 0.8)
    img = glow(Image.fromarray(a.astype(np.uint8)), 880, 110, 70, '#a9b4ff', 0.35, 60)
    d = ImageDraw.Draw(img)
    d.ellipse([850, 80, 910, 140], fill=(246, 241, 214))
    d.ellipse([866, 72, 926, 132], fill=tuple(int(v) for v in hexc('#121640')))  # luna menguante
    a = np.asarray(img, np.float32)
    a = fill_below(a, ridge(W, 330, 70, 0.55, 2), '#2b1f55', top_color='#3b2c6c')
    a = fill_below(a, ridge(W, 390, 55, 0.58, 3), '#1c1540', top_color='#291d55')
    a = fill_below(a, ridge(W, 450, 40, 0.6, 4), '#0e0b24')
    return a


def aurora():
    a = vgrad([(0, '#020b18'), (0.6, '#08283d'), (1, '#0f3b4f')])
    a = stars(a, 700, 11, 0.7)
    h, w, _ = a.shape
    xs = np.arange(w)
    layer = np.zeros_like(a)
    for k, (col, y0, amp, ph, thick) in enumerate([('#3dffb0', 150, 40, 0.0, 60), ('#28d6ff', 185, 34, 1.7, 46),
                                                   ('#b06bff', 120, 28, 3.1, 36)]):
        yc = y0 + amp * np.sin(xs / 140 + ph) + 18 * np.sin(xs / 47 + ph * 2)
        yy = np.arange(h)[:, None]
        dist = (yy - yc[None, :])
        band = np.exp(-np.clip(dist, 0, None) / (thick * 2.2)) * np.exp(-np.clip(-dist, 0, None) / 10)
        # cortinas suaves (no franjas): el brillo cambia despacio a lo largo
        flick = 0.7 + 0.3 * np.sin(xs / 37 + k * 1.3)[None, :] * np.sin(xs / 83 + k)[None, :]
        layer += band[..., None] * flick[..., None] * hexc(col) * 0.55
    a = np.clip(a + np.asarray(Image.fromarray(np.clip(layer, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(6)), np.float32), 0, 255)
    a = fill_below(a, ridge(W, 360, 60, 0.55, 12), '#9fb7c9', top_color='#e6f2ff')
    a = fill_below(a, ridge(W, 420, 35, 0.6, 13), '#5d7a92', top_color='#c6d8ea')
    r3 = ridge(W, 470, 20, 0.6, 14)
    a = pines(a, r3, '#0b1d2a', 15, density=0.09, size=(30, 72))
    a = fill_below(a, r3, '#0b1d2a')
    return a


def atardecer():
    a = vgrad([(0, '#2c1a52'), (0.35, '#a33f7a'), (0.62, '#ff7b5c'), (0.8, '#ffc36b'), (1, '#ffd98f')])
    img = glow(Image.fromarray(a.astype(np.uint8)), 576, 330, 120, '#ffe08a', 0.55, 90)
    d = ImageDraw.Draw(img)
    d.ellipse([516, 270, 636, 390], fill=(255, 236, 170))
    for i, (x, y, s) in enumerate([(300, 140, 9), (330, 128, 7), (352, 150, 8), (820, 110, 10), (845, 98, 7)]):
        d.line([(x - s, y), (x, y + s * 0.5), (x + s, y)], fill=(70, 30, 70), width=2)
    a = np.asarray(img, np.float32)
    a = fill_below(a, ridge(W, 360, 50, 0.55, 21), '#b04a72', top_color='#d86a7c')
    a = fill_below(a, ridge(W, 410, 45, 0.58, 22), '#6f2c64', top_color='#8d3a6e')
    a = fill_below(a, ridge(W, 460, 35, 0.6, 23), '#3b1846')
    return a


def bosque():
    a = vgrad([(0, '#cfe9d8'), (0.5, '#9fd1b6'), (1, '#5f9f86')])
    img = glow(Image.fromarray(a.astype(np.uint8)), 880, 90, 80, '#fffbe0', 0.5, 70)
    a = np.asarray(img, np.float32)
    layers = [(250, '#7fb39c', 31), (300, '#5f9881', 32), (350, '#3f7a66', 33), (410, '#24584a', 34), (470, '#123a31', 35)]
    for i, (base, col, seed) in enumerate(layers):
        r = ridge(W, base, 18, 0.6, seed)
        a = pines(a, r, col, seed + 100, density=0.08 + i * 0.01, size=(40 + i * 12, 80 + i * 22))
        a = fill_below(a, r, col)
        # niebla entre capas
        fog = vgrad([(0, '#e8f6ee'), (1, '#e8f6ee')])
        yy = np.arange(H)[:, None, None]
        m = np.clip(1 - np.abs(yy - (base - 20)) / 60.0, 0, 1) * 0.28
        a = a * (1 - m) + fog * m
    return a


def oceano():
    a = vgrad([(0, '#3f8fd9'), (0.45, '#8fc8f2'), (0.5, '#d6efff'), (0.5001, '#1f6aa8'), (1, '#0b2e5c')])
    h, w, _ = a.shape
    # nubes suaves en el cielo
    n = fbm(w, h, 41, octaves=5, base=3)
    clouds = np.clip((n - 0.52) * 3, 0, 1)[..., None] * (np.arange(h) < h * 0.48)[:, None, None]
    a = a * (1 - clouds * 0.7) + 255 * clouds * 0.7
    img = glow(Image.fromarray(np.clip(a, 0, 255).astype(np.uint8)), 760, 250, 46, '#fff6d0', 0.6, 40)
    d = ImageDraw.Draw(img)
    d.ellipse([738, 228, 782, 272], fill=(255, 248, 220))
    rng = np.random.default_rng(42)
    for i in range(260):  # reflejo y olas
        y = rng.uniform(268, h)
        x = 760 + rng.normal(0, 18 + (y - 264) * 0.25)
        l = rng.uniform(8, 30) * (y - 250) / 200
        d.line([(x - l, y), (x + l, y)], fill=(255, 240, 200), width=1)
    for i in range(380):
        y = rng.uniform(270, h)
        x = rng.uniform(0, w)
        l = rng.uniform(6, 20) * (y - 250) / 260
        d.line([(x - l, y), (x + l, y)], fill=(120, 180, 225), width=1)
    return np.asarray(img, np.float32)


def cerezos():
    a = vgrad([(0, '#f7d6ea'), (0.6, '#fbe6ef'), (1, '#fff3f6')])
    img = Image.fromarray(a.astype(np.uint8))
    a = np.asarray(img, np.float32)
    a = fill_below(a, ridge(W, 380, 40, 0.5, 51), '#c9e6b8', top_color='#dff2cf')
    a = fill_below(a, ridge(W, 440, 30, 0.5, 52), '#9fd08c', top_color='#b7e0a2')
    img = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(img)
    rng = np.random.default_rng(53)
    # tres árboles: tronco y copa de bolas rosas
    for tx, ty, s in [(170, 420, 1.2), (980, 445, 1.0), (620, 470, 0.7)]:
        d.polygon([(tx - 8 * s, ty), (tx + 8 * s, ty), (tx + 4 * s, ty - 90 * s), (tx - 4 * s, ty - 90 * s)], fill=(110, 70, 70))
        d.line([(tx, ty - 70 * s), (tx - 40 * s, ty - 120 * s)], fill=(110, 70, 70), width=int(6 * s))
        d.line([(tx, ty - 80 * s), (tx + 45 * s, ty - 125 * s)], fill=(110, 70, 70), width=int(6 * s))
        for _ in range(140):
            ang, rad = rng.uniform(0, 2 * math.pi), rng.uniform(0, 75 * s) ** 0.9
            x, y = tx + math.cos(ang) * rad * 1.3, ty - 120 * s + math.sin(ang) * rad * 0.8
            r = rng.uniform(7, 16) * s
            c = (255, int(rng.uniform(150, 200)), int(rng.uniform(190, 220)))
            d.ellipse([x - r, y - r, x + r, y + r], fill=c)
    for _ in range(220):  # pétalos cayendo
        x, y = rng.uniform(0, W), rng.uniform(0, H)
        r = rng.uniform(2, 4)
        d.ellipse([x - r, y - r * 0.6, x + r, y + r * 0.6], fill=(255, 182, 210))
    return np.asarray(img.filter(ImageFilter.SMOOTH), np.float32)


def galaxia():
    a = vgrad([(0, '#05030f'), (1, '#0b0820')])
    h, w, _ = a.shape
    n1, n2 = fbm(w, h, 61, octaves=6, base=3), fbm(w, h, 62, octaves=6, base=4)
    # una banda en diagonal
    yy, xx = np.mgrid[0:h, 0:w]
    band = np.exp(-((yy - (h * 0.75 - xx * 0.35)) / 130.0) ** 2)
    neb = np.clip((n1 - 0.4) * 2.2, 0, 1) * band
    neb2 = np.clip((n2 - 0.45) * 2.5, 0, 1) * band
    a += neb[..., None] * hexc('#c03fd8') * 0.9 + neb2[..., None] * hexc('#2fb7ff') * 0.7
    a = stars(a, 1600, 63, 1.0, big=0.03)
    img = glow(Image.fromarray(np.clip(a, 0, 255).astype(np.uint8)), 560, 260, 40, '#ffe7ff', 0.6, 50)
    return np.asarray(img, np.float32)


def dunas():
    a = vgrad([(0, '#4a90d0'), (0.45, '#f2c58a'), (0.55, '#ffd9a0'), (1, '#ffe6b8')])
    img = glow(Image.fromarray(a.astype(np.uint8)), 300, 200, 50, '#fff2c0', 0.7, 50)
    d = ImageDraw.Draw(img)
    d.ellipse([268, 168, 332, 232], fill=(255, 244, 205))
    a = np.asarray(img, np.float32)
    for base, col, top, seed in [(300, '#e2a35c', '#f2bd76', 71), (350, '#d18a45', '#e7a861', 72), (410, '#b8702f', '#d38f4a', 73),
                                 (470, '#9a5a24', '#b9713a', 74)]:
        a = fill_below(a, ridge(W, base, 28, 0.35, seed), col, top_color=top)
    return a


def islas():
    a = vgrad([(0, '#8ec5ff'), (0.6, '#d7ecff'), (1, '#fff1e0')])
    h, w, _ = a.shape
    n = fbm(w, h, 81, octaves=5, base=3)
    clouds = np.clip((n - 0.5) * 3.2, 0, 1)[..., None]
    a = a * (1 - clouds * 0.8) + 255 * clouds * 0.8
    img = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(img)
    rng = np.random.default_rng(82)
    for cx, cy, s in [(260, 250, 1.3), (760, 180, 1.0), (980, 330, 0.7), (520, 380, 0.55)]:
        top = [(cx - 110 * s, cy), (cx + 110 * s, cy)]
        d.polygon([top[0], top[1], (cx + 60 * s, cy + 40 * s), (cx + 15 * s, cy + 110 * s), (cx - 30 * s, cy + 60 * s),
                   (cx - 80 * s, cy + 35 * s)], fill=(120, 92, 74))
        d.rectangle([cx - 110 * s, cy - 10 * s, cx + 110 * s, cy + 4 * s], fill=(108, 186, 92))
        for _ in range(int(6 * s) + 2):  # árboles
            x = cx + rng.uniform(-90, 90) * s
            r = rng.uniform(10, 18) * s
            d.ellipse([x - r, cy - 10 * s - 2 * r, x + r, cy - 10 * s], fill=(64, 140, 70))
        d.line([(cx + 70 * s, cy + 4 * s), (cx + 70 * s, cy + 160 * s)], fill=(200, 235, 255), width=max(2, int(5 * s)))  # cascada
    return np.asarray(img.filter(ImageFilter.SMOOTH), np.float32)


def castillo():
    """Tierras Fantásticas: un castillo en lo alto bajo la luna."""
    a = vgrad([(0, '#0a1233'), (0.6, '#24306e'), (1, '#4a3f8e')])
    a = stars(a, 700, 91, 0.75)
    img = glow(Image.fromarray(np.clip(a, 0, 255).astype(np.uint8)), 820, 120, 60, '#cfd8ff', 0.4, 60)
    d = ImageDraw.Draw(img)
    d.ellipse([790, 90, 850, 150], fill=(248, 244, 222))
    a = np.asarray(img, np.float32)
    a = fill_below(a, ridge(W, 400, 40, 0.55, 92), '#1b1a48', top_color='#272766')
    hill = ridge(W, 430, 30, 0.5, 93)
    img = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(img)
    col = (13, 12, 34)
    cx, base = 420, 330
    d.polygon([(250, 470), (cx - 80, base + 10), (cx + 130, base + 10), (640, 470)], fill=col)  # la roca
    for x, w_, h_ in [(cx - 60, 40, 120), (cx, 70, 150), (cx + 90, 40, 110)]:
        d.rectangle([x - w_ / 2, base - h_, x + w_ / 2, base + 20], fill=col)
        for k in range(-2, 3):  # almenas
            d.rectangle([x + k * w_ / 5 - 4, base - h_ - 10, x + k * w_ / 5 + 4, base - h_], fill=col)
        d.polygon([(x - w_ / 2 - 6, base - h_), (x, base - h_ - 60), (x + w_ / 2 + 6, base - h_)], fill=col)
    rng = np.random.default_rng(94)
    for _ in range(14):  # ventanas encendidas
        x, y = cx + rng.uniform(-70, 100), base - rng.uniform(10, 120)
        d.rectangle([x, y, x + 4, y + 7], fill=(255, 210, 120))
    d.line([(cx, base - 210), (cx, base - 240)], fill=col, width=3)
    d.polygon([(cx, base - 240), (cx + 22, base - 232), (cx, base - 225)], fill=(230, 180, 60))
    a = np.asarray(img, np.float32)
    a = fill_below(a, hill, '#080718')
    return a


def nubes():
    """Mar de nubes al amanecer."""
    a = vgrad([(0, '#ff9fb0'), (0.35, '#ffc6a0'), (0.6, '#fff0c8'), (1, '#ffffff')])
    img = glow(Image.fromarray(a.astype(np.uint8)), 576, 300, 70, '#fff3c0', 0.7, 70)
    a = np.asarray(img, np.float32)
    h, w, _ = a.shape
    for k, (y0, shade) in enumerate([(300, 0.86), (360, 0.92), (430, 1.0)]):
        n = fbm(w, h, 101 + k, octaves=4, base=6)
        top = y0 + (n[0] - 0.5) * 80
        yy = np.arange(h)[:, None]
        edge = top[None, :] + (n - 0.5) * 60
        m = np.clip((yy - edge) / 14.0, 0, 1)[..., None]
        col = np.array([255, 245, 250], np.float32) * shade
        a = a * (1 - m) + col * m
    return a


def fugaces():
    """Lluvia de estrellas en el crepúsculo."""
    a = vgrad([(0, '#0b0f2e'), (0.55, '#33275e'), (0.85, '#7a4a7e'), (1, '#c46f78')])
    a = stars(a, 800, 111, 0.85)
    img = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))
    layer = Image.new('RGB', img.size)
    d = ImageDraw.Draw(layer)
    rng = np.random.default_rng(112)
    for _ in range(16):
        x, y = rng.uniform(100, W), rng.uniform(0, 260)
        l = rng.uniform(60, 160)
        for i in range(10):
            f = i / 10
            d.line([(x - l * f, y + l * f * 0.45), (x - l * (f + 0.1), y + l * (f + 0.1) * 0.45)],
                   fill=tuple(int(255 * (1 - f)) for _ in range(3)), width=2)
    img = Image.fromarray(np.clip(np.asarray(img, np.float32) + np.asarray(layer, np.float32) + np.asarray(layer.filter(ImageFilter.GaussianBlur(2)), np.float32), 0, 255).astype(np.uint8))
    a = np.asarray(img, np.float32)
    a = fill_below(a, ridge(W, 420, 45, 0.55, 113), '#1e1530', top_color='#2d1f42')
    a = fill_below(a, ridge(W, 470, 25, 0.6, 114), '#0d0918')
    return a


FONDOS = [
    ('noche', 'Noche estrellada', noche),
    ('aurora', 'Aurora boreal', aurora),
    ('atardecer', 'Atardecer', atardecer),
    ('bosque', 'Bosque brumoso', bosque),
    ('oceano', 'Océano', oceano),
    ('cerezos', 'Cerezos', cerezos),
    ('galaxia', 'Galaxia', galaxia),
    ('dunas', 'Dunas', dunas),
    ('islas', 'Islas flotantes', islas),
    ('castillo', 'Tierras Fantásticas', castillo),
    ('nubes', 'Mar de nubes', nubes),
    ('fugaces', 'Lluvia de estrellas', fugaces),
]


def main():
    for fid, _, fn in FONDOS:
        save(fn(), fid)
    os.makedirs(os.path.dirname(INDEX), exist_ok=True)
    with open(INDEX, 'w', encoding='utf-8') as f:
        json.dump([{'id': i, 'nombre': n} for i, n, _ in FONDOS], f, ensure_ascii=False, indent=1)
        f.write('\n')
    if '--ver' in sys.argv:
        dest = sys.argv[sys.argv.index('--ver') + 1]
        os.makedirs(dest, exist_ok=True)
        cols = 3
        tw, th = 384, 176
        sheet = Image.new('RGB', (cols * tw, math.ceil(len(FONDOS) / cols) * th), 'white')
        for i, (fid, _, _) in enumerate(FONDOS):
            im = Image.open(os.path.join(OUT, f'{fid}.png')).resize((tw, th))
            sheet.paste(im, ((i % cols) * tw, (i // cols) * th))
        sheet.save(os.path.join(dest, 'fondos.png'))
    total = sum(os.path.getsize(os.path.join(OUT, f'{i}.png')) for i, _, _ in FONDOS)
    print(f'{len(FONDOS)} fondos, {total // 1024} KB')


if __name__ == '__main__':
    main()
