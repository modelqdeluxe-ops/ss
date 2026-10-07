"""Fondo de la web: el reino de noche oscurecido con una nebulosa de neón (rosa, violeta y cian) en el cielo.

Uso: python3 tools/nebula_bg.py
Lee public/img/night-1672.webp y escribe public/img/nebula-{768,1280,1672}.webp. Es una sola imagen fija (la web no
pone capas encima con mezclas ni desenfoques: así no pesa en el móvil). La nebulosa sale de ruido fractal con una
semilla fija, así que el resultado es siempre el mismo.
"""
import os

import numpy as np
from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
IMG = os.path.join(HERE, '..', 'public', 'img')
SIZES = (768, 1280, 1672)
SEED = 7

MAGENTA = np.array([1.0, 0.22, 0.80])
VIOLET = np.array([0.52, 0.26, 1.0])
CYAN = np.array([0.12, 0.78, 1.0])


def noise(w, h, cells, rng, blur=0.6):
    """Ruido de valores suave: una rejilla aleatoria ampliada (bicúbica) y desenfocada, sin cuadros ni rayas."""
    gw = max(3, cells)
    gh = max(3, round(cells * h / w))
    grid = Image.fromarray((rng.random((gh, gw)) * 255).astype(np.uint8))
    img = grid.resize((w, h), Image.BICUBIC).filter(ImageFilter.GaussianBlur(w / gw * blur))
    a = np.asarray(img, dtype=np.float32) / 255.0
    return (a - a.min()) / max(1e-6, a.max() - a.min())


def fractal(w, h, rng, base=3, octaves=6, gain=0.55):
    out = np.zeros((h, w), np.float32)
    amp, total = 1.0, 0.0
    for o in range(octaves):
        out += noise(w, h, base * 2 ** o, rng) * amp
        total += amp
        amp *= gain
    out /= total
    return (out - out.min()) / max(1e-6, out.max() - out.min())


def blob(u, v, cx, cy, rx, ry, angle=0.0):
    """Nube elíptica suave (gaussiana) girada angle radianes."""
    ca, sa = np.cos(angle), np.sin(angle)
    x, y = u - cx, v - cy
    xr, yr = x * ca + y * sa, -x * sa + y * ca
    return np.exp(-((xr / rx) ** 2 + (yr / ry) ** 2))


def nebula(w, h, rng):
    """La nebulosa a baja resolución (se amplía después): nubes de color con detalle fractal y filamentos."""
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    u, v = xx / w, yy / h
    detail = fractal(w, h, rng, 3, 6)
    fine = fractal(w, h, rng, 6, 5, 0.6)
    ridge = 1 - np.abs(fractal(w, h, rng, 4, 5) * 2 - 1)  # crestas: las hebras de gas
    clouds = [
        (MAGENTA, blob(u, v, 0.80, 0.18, 0.36, 0.22, -0.35), 0.78),
        (VIOLET, blob(u, v, 0.50, 0.30, 0.58, 0.18, -0.42), 0.85),
        (CYAN, blob(u, v, 0.16, 0.14, 0.26, 0.15, 0.25), 0.75),
        (MAGENTA, blob(u, v, 0.08, 0.72, 0.22, 0.16, 0.5), 0.45),
        (VIOLET, blob(u, v, 0.95, 0.62, 0.20, 0.18, 0.0), 0.40),
    ]
    out = np.zeros((h, w, 3), np.float32)
    for color, shape, power in clouds:
        dens = shape * (0.35 + 0.65 * detail) * (0.55 + 0.45 * fine)
        dens = np.clip(dens * 1.15 - 0.1, 0, 1) ** 1.5
        out += color * (dens * power)[..., None]
        out += color * (shape * ridge ** 6 * power * 0.55)[..., None]
    # Núcleos casi blancos donde se juntan las nubes
    glow = np.clip(out.max(axis=2) - 0.8, 0, 1)
    out += np.array([1.0, 0.85, 1.0]) * (glow ** 1.5 * 0.35)[..., None]
    return np.clip(out, 0, 1)


def build(width=1672):
    rng = np.random.default_rng(SEED)
    base = Image.open(os.path.join(IMG, 'night-1672.webp')).convert('RGB')
    h = round(base.height * width / base.width)
    base = np.asarray(base.resize((width, h), Image.LANCZOS), dtype=np.float32) / 255.0

    # El reino, mucho más oscuro y hacia el índigo: queda la silueta (castillo, árboles, luna) sin quitar protagonismo
    lum = base.mean(axis=2, keepdims=True)
    indigo = np.array([0.22, 0.17, 0.46])
    dark = (base * 0.5 + lum * indigo * 1.0) * 0.40

    # La nebulosa se hace a 420 px de ancho y se amplía (suave y ligera de calcular)
    small = nebula(420, round(420 * h / width), rng)
    neb = np.asarray(Image.fromarray((small * 255).astype(np.uint8)).resize((width, h), Image.BICUBIC)
                     .filter(ImageFilter.GaussianBlur(width / 900)), np.float32) / 255.0
    yy, xx = np.mgrid[0:h, 0:width].astype(np.float32)
    u, v = xx / width, yy / h
    # Más fuerte en el cielo; abajo (el valle) solo un reflejo
    neb *= (0.30 + 0.70 * np.clip(1.25 - v * 1.35, 0, 1))[..., None] * 0.85

    # Estrellas pequeñas, más en el cielo
    stars = np.zeros((h, width), np.float32)
    n = int(width * h / 700)
    sy = (rng.random(n) ** 1.7 * h * 0.75).astype(int)
    sx = rng.integers(0, width, n)
    stars[sy, sx] = rng.random(n) ** 3
    stars = np.asarray(Image.fromarray((np.clip(stars, 0, 1) * 255).astype(np.uint8))
                       .filter(ImageFilter.GaussianBlur(0.6)), np.float32) / 255 * 2.2

    # Mezcla «pantalla» (aclara sin tapar la silueta) y viñeta para que el texto se lea en los bordes y abajo
    out = 1 - (1 - dark) * (1 - neb)
    out = 1 - (1 - out) * (1 - np.clip(stars, 0, 1)[..., None] * np.array([0.92, 0.9, 1.0]))
    r = np.sqrt(((u - 0.55) / 0.85) ** 2 + ((v - 0.35) / 0.9) ** 2)
    out *= np.clip(1.15 - r * 0.5, 0.5, 1)[..., None] * np.clip(1.08 - v * 0.35, 0.6, 1)[..., None]
    return Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8))


def main():
    big = build(1672)
    for w in SIZES:
        img = big if w == big.width else big.resize((w, round(big.height * w / big.width)), Image.LANCZOS)
        path = os.path.join(IMG, f'nebula-{w}.webp')
        img.save(path, 'WEBP', quality=80, method=6)
        print(path, img.size, os.path.getsize(path) // 1024, 'KB')


if __name__ == '__main__':
    main()
