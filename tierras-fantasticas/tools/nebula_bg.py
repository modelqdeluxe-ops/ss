"""Fondo vivo de la web: el reino de noche oscurecido con una nebulosa de neón (rosa, violeta y cian) y estrellas.

Uso: python3 tools/nebula_bg.py
Lee public/img/night-1672.webp y escribe en public/img/:
  nebula-{768,1280,1672}.webp   la base fija: el reino oscuro con un velo suave de la nebulosa
  nebula-a-{640,1280}.webp      la nebulosa (con transparencia): se desplaza y respira muy despacio
  nebula-b-{640,1280}.webp      otra nebulosa con las nubes en otro sitio: aparece y se apaga, así cambia de color
  stars-a.webp, stars-b.webp    dos mosaicos de estrellas (con transparencia) que titilan a destiempo
La web los pone en capas fijas que solo se animan con transform y opacity (las mueve la tarjeta gráfica; en el móvil
van menos capas). Todo sale de ruido fractal con semillas fijas: el resultado es siempre el mismo.
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


# Nubes de cada capa: (color, centro x, centro y, radio x, radio y, giro, fuerza), en fracciones de la imagen
CLOUDS_A = [
    (MAGENTA, 0.80, 0.18, 0.36, 0.22, -0.35, 0.78),
    (VIOLET, 0.50, 0.30, 0.58, 0.18, -0.42, 0.85),
    (CYAN, 0.16, 0.14, 0.26, 0.15, 0.25, 0.75),
    (MAGENTA, 0.08, 0.72, 0.22, 0.16, 0.5, 0.45),
    (VIOLET, 0.95, 0.62, 0.20, 0.18, 0.0, 0.40),
]
CLOUDS_B = [
    (CYAN, 0.66, 0.10, 0.30, 0.16, 0.3, 0.70),
    (MAGENTA, 0.32, 0.26, 0.34, 0.15, -0.6, 0.80),
    (VIOLET, 0.90, 0.40, 0.24, 0.22, 0.4, 0.60),
    (CYAN, 0.12, 0.55, 0.20, 0.14, -0.2, 0.40),
]


def nebula(w, h, rng, clouds):
    """La nebulosa a baja resolución (se amplía después): nubes de color con detalle fractal y filamentos."""
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    u, v = xx / w, yy / h
    detail = fractal(w, h, rng, 3, 6)
    fine = fractal(w, h, rng, 6, 5, 0.6)
    ridge = 1 - np.abs(fractal(w, h, rng, 4, 5) * 2 - 1)  # crestas: las hebras de gas
    out = np.zeros((h, w, 3), np.float32)
    for color, cx, cy, rx, ry, angle, power in clouds:
        shape = blob(u, v, cx, cy, rx, ry, angle)
        dens = shape * (0.35 + 0.65 * detail) * (0.55 + 0.45 * fine)
        dens = np.clip(dens * 1.15 - 0.1, 0, 1) ** 1.5
        out += color * (dens * power)[..., None]
        out += color * (shape * ridge ** 6 * power * 0.55)[..., None]
    # Núcleos casi blancos donde se juntan las nubes
    glow = np.clip(out.max(axis=2) - 0.8, 0, 1)
    out += np.array([1.0, 0.85, 1.0]) * (glow ** 1.5 * 0.35)[..., None]
    # Más fuerte en el cielo; abajo (el valle) solo un reflejo
    out *= (0.30 + 0.70 * np.clip(1.25 - v * 1.35, 0, 1))[..., None]
    return np.clip(out, 0, 1)


def to_rgba(neb):
    """Color sumado → color con transparencia (sobre un fondo oscuro se ve casi igual que la mezcla «pantalla»)."""
    alpha = np.clip(neb.max(axis=2), 0, 1)
    color = np.where(alpha[..., None] > 1e-3, neb / np.maximum(alpha[..., None], 1e-3), 0)
    rgba = np.dstack([np.clip(color, 0, 1), alpha])
    return Image.fromarray((rgba * 255).astype(np.uint8), 'RGBA')


def base(width=1672):
    """El reino oscurecido con la nebulosa A muy suave (lo que se ve si las capas animadas no cargan)."""
    rng = np.random.default_rng(SEED)
    img = Image.open(os.path.join(IMG, 'night-1672.webp')).convert('RGB')
    h = round(img.height * width / img.width)
    img = np.asarray(img.resize((width, h), Image.LANCZOS), dtype=np.float32) / 255.0
    # El reino, mucho más oscuro y hacia el índigo: queda la silueta (castillo, árboles, luna)
    lum = img.mean(axis=2, keepdims=True)
    indigo = np.array([0.22, 0.17, 0.46])
    dark = (img * 0.5 + lum * indigo * 1.0) * 0.40
    small = nebula(420, round(420 * h / width), rng, CLOUDS_A)
    neb = np.asarray(Image.fromarray((small * 255).astype(np.uint8)).resize((width, h), Image.BICUBIC)
                     .filter(ImageFilter.GaussianBlur(width / 900)), np.float32) / 255.0 * 0.35
    out = 1 - (1 - dark) * (1 - neb)
    yy, xx = np.mgrid[0:h, 0:width].astype(np.float32)
    u, v = xx / width, yy / h
    # Viñeta para que el texto se lea en los bordes y abajo
    r = np.sqrt(((u - 0.55) / 0.85) ** 2 + ((v - 0.35) / 0.9) ** 2)
    out *= np.clip(1.15 - r * 0.5, 0.5, 1)[..., None] * np.clip(1.08 - v * 0.35, 0.6, 1)[..., None]
    return Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8))


def nebula_layer(clouds, seed, width=1280, strength=0.85):
    rng = np.random.default_rng(seed)
    h = round(width * 941 / 1672)
    small = nebula(420, round(420 * h / width), rng, clouds)
    big = Image.fromarray((small * 255).astype(np.uint8)).resize((width, h), Image.BICUBIC)
    big = big.filter(ImageFilter.GaussianBlur(width / 900))
    return to_rgba(np.asarray(big, np.float32) / 255.0 * strength)


def stars(seed, size=512, count=90):
    """Mosaico de estrellas (se repite): puntos pequeños y redondos de brillos distintos, sin destellos."""
    rng = np.random.default_rng(seed)
    scale = 4  # se dibuja a 4× y se reduce: estrellas redondas y suaves
    S = size * scale
    a = np.zeros((S, S), np.float32)
    for _ in range(count):
        x, y = rng.random() * S, rng.random() * S
        bright = rng.random() ** 2.4
        sigma = (0.35 + bright * 0.55) * scale
        r = int(sigma * 4) + 1
        x0, y0 = int(x), int(y)
        ys, xs = np.mgrid[max(0, y0 - r):min(S, y0 + r), max(0, x0 - r):min(S, x0 + r)].astype(np.float32)
        spot = np.exp(-((xs - x) ** 2 + (ys - y) ** 2) / (2 * sigma * sigma)) * (0.35 + 0.65 * bright)
        sl = (slice(max(0, y0 - r), min(S, y0 + r)), slice(max(0, x0 - r), min(S, x0 + r)))
        a[sl] = np.maximum(a[sl], spot)
    img = Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8)).resize((size, size), Image.LANCZOS)
    alpha = np.asarray(img, np.float32) / 255.0
    tint = np.array([0.92, 0.9, 1.0])
    rgba = np.dstack([np.ones((size, size, 3)) * tint, alpha])
    return Image.fromarray((rgba * 255).astype(np.uint8), 'RGBA')


def save(img, name, **kw):
    path = os.path.join(IMG, name)
    img.save(path, 'WEBP', method=6, **kw)
    print(path, img.size, os.path.getsize(path) // 1024, 'KB')


def main():
    big = base(1672)
    for w in SIZES:
        img = big if w == big.width else big.resize((w, round(big.height * w / big.width)), Image.LANCZOS)
        save(img, f'nebula-{w}.webp', quality=80)
    for name, clouds, seed in (('a', CLOUDS_A, SEED), ('b', CLOUDS_B, SEED + 1)):
        layer = nebula_layer(clouds, seed)
        for w in (640, 1280):
            img = layer if w == layer.width else layer.resize((w, round(layer.height * w / layer.width)), Image.LANCZOS)
            save(img, f'nebula-{name}-{w}.webp', quality=78)
    save(stars(11), 'stars-a.webp', quality=85)
    save(stars(12, count=70), 'stars-b.webp', quality=85)


if __name__ == '__main__':
    main()
