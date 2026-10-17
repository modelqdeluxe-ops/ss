"""El cielo de las grietas (1.3.86; 1.3.87 más fino): las texturas que dibuja RiftSky cerca de una grieta.

    python3 tools/bosses/rift_sky.py [--preview carpeta]

  textures/environment/grieta_cielo.png    panorama equirectangular (2:1, 2048x1024) sin costuras: el vacío morado con
                                           nebulosas (violeta, magenta y añil), filamentos, franjas de polvo oscuro y
                                           una banda galáctica. Sin estrellas (1.3.87: en el panorama se veían como
                                           píxeles gordos; ahora todas las dibuja RiftSky) y con tramado para que el
                                           degradado oscuro no salga en escalones. RiftSky lo pone en una esfera
                                           alrededor de la cámara (u = longitud, v = latitud: las mismas cuentas que aquí).
  textures/environment/grieta_nube.png     una nube de nebulosa suelta (con alfa) para las capas que laten encima.
  textures/environment/grieta_estrella.png el destello de una estrella (las que titilan).
  textures/environment/grieta_fugaz.png    la estela de una estrella fugaz (cabeza a la derecha, cola que se afina y
                                           se apaga a la izquierda; suave también de ancho).
  textures/environment/grieta_eclipse.png  el «sol negro» del vacío: un disco negro con corona violeta (encima de la
                                           grieta).

Todas llevan su .png.mcmeta con «blur»: Minecraft las filtra suaves en vez de píxel a píxel.
Todo sale del ruido de valor 3D sobre la dirección (no hay costura en la longitud y los polos no se estiran raro).
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient', 'textures',
                                    'environment'))

W, H = 2048, 1024


class Noise3:
    """Ruido de valor 3D (trilineal suavizado) sobre una red de 64³ que se repite."""

    def __init__(self, seed, size=64):
        self.g = np.random.default_rng(seed).random((size, size, size)).astype(np.float32)
        self.n = size

    def __call__(self, p):
        n = self.n
        x, y, z = p[..., 0], p[..., 1], p[..., 2]
        xi, yi, zi = np.floor(x).astype(int), np.floor(y).astype(int), np.floor(z).astype(int)
        fx, fy, fz = x - xi, y - yi, z - zi
        fx, fy, fz = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy), fz * fz * (3 - 2 * fz)
        g = self.g

        def at(dx, dy, dz):
            return g[(xi + dx) % n, (yi + dy) % n, (zi + dz) % n]

        c00 = at(0, 0, 0) * (1 - fx) + at(1, 0, 0) * fx
        c10 = at(0, 1, 0) * (1 - fx) + at(1, 1, 0) * fx
        c01 = at(0, 0, 1) * (1 - fx) + at(1, 0, 1) * fx
        c11 = at(0, 1, 1) * (1 - fx) + at(1, 1, 1) * fx
        c0 = c00 * (1 - fy) + c10 * fy
        c1 = c01 * (1 - fy) + c11 * fy
        return c0 * (1 - fz) + c1 * fz

    def fbm(self, p, octaves=6, lac=2.03, gain=0.52):
        out = np.zeros(p.shape[:-1], np.float32)
        amp, tot = 1.0, 0.0
        q = p.copy()
        for o in range(octaves):
            out += amp * self(q + o * 17.3)
            tot += amp
            amp *= gain
            q = q * lac
        return out / tot


def directions(w, h):
    """La dirección de cada píxel del panorama: u = longitud (0..2π), v = latitud (+90° arriba .. -90° abajo)."""
    u = (np.arange(w) + 0.5) / w
    v = (np.arange(h) + 0.5) / h
    lon = u * 2 * np.pi
    lat = (0.5 - v) * np.pi
    lon, lat = np.meshgrid(lon, lat)
    return np.stack([np.cos(lat) * np.cos(lon), np.sin(lat), np.cos(lat) * np.sin(lon)], -1).astype(np.float32)


def smooth(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def sky():
    d = directions(W, H)
    n1, n2, n3, n4 = Noise3(101), Noise3(202), Noise3(303), Noise3(404)
    # Dominio deformado: las nebulosas se retuercen como humo
    warp = np.stack([n2.fbm(d * 2.0 + 3.1, 4), n3.fbm(d * 2.0 + 7.7, 4), n4.fbm(d * 2.0 + 1.3, 4)], -1) - 0.5
    q = d * 2.4 + warp * 1.6
    dens = n1.fbm(q, 7)
    # banda galáctica a lo largo de un círculo inclinado
    axis = np.array([0.32, 0.86, -0.4], np.float32)
    axis /= np.linalg.norm(axis)
    band = np.exp(-(d @ axis) ** 2 / 0.06)
    # compresión suave (1.3.87): antes se cortaba en 1 y lo más brillante salía en manchas planas de borde duro
    x = np.clip((dens - 0.4) * 2.4 + band * 0.4, 0, None)
    dens = (1 - np.exp(-x * 1.5)) ** 1.25
    # detalle fino dentro de la nebulosa (que lo brillante también tenga textura de humo)
    dens *= 0.72 + 0.5 * n1.fbm(d * 9.0 + warp * 3.0 + 21.0, 5)
    # polvo oscuro que corta la nebulosa
    dust = smooth(0.5, 0.7, n3.fbm(d * 5.5 + warp * 2.0, 5)) * (0.4 + 0.6 * band)
    dens *= 1 - 0.75 * dust
    # filamentos: hilos brillantes retorcidos donde ya hay nebulosa (ruido «de cresta»)
    ridge = 1 - np.abs(2 * n2.fbm(d * 4.2 + warp * 2.4 + 11.0, 5) - 1)
    dens = np.clip(dens + smooth(0.86, 0.98, ridge) * smooth(0.05, 0.4, dens) * 0.35, 0, 1)
    hue = n4.fbm(d * 1.6 + 9.0, 4)  # 0: añil, 1: magenta
    base = np.array([5, 1, 11], np.float32)
    deep = np.array([34, 6, 66], np.float32)
    violet = np.array([118, 34, 214], np.float32)
    magenta = np.array([196, 52, 190], np.float32)
    indigo = np.array([46, 44, 168], np.float32)
    hot = np.array([236, 196, 255], np.float32)
    hi = indigo[None, None] * (1 - smooth(0.35, 0.65, hue))[..., None] + magenta[None, None] * smooth(0.35, 0.65, hue)[..., None]
    col = base + (deep - base) * smooth(0.0, 0.35, dens)[..., None]
    col = col + (violet - col) * smooth(0.3, 0.7, dens)[..., None] * 0.9
    col = col + (hi - col) * smooth(0.6, 0.95, dens)[..., None] * 0.7
    col = col + (hot - col) * smooth(0.82, 1.05, dens)[..., None] * 0.3
    # el horizonte un poco más claro (como el resplandor del End) y el cénit más negro
    lat = d[..., 1]
    col *= (0.75 + 0.35 * np.exp(-(lat / 0.35) ** 2))[..., None]
    # tramado (medio nivel de ruido): sin él el degradado oscuro se ve en escalones al estirarlo
    col += np.random.default_rng(56).random((H, W, 1)) - 0.5
    img = Image.fromarray(np.clip(col, 0, 255).astype(np.uint8), 'RGB')
    return img


def cloud(n=256, seed=7):
    """Una nube suelta de nebulosa: violeta con borde magenta, alfa que se apaga hacia fuera."""
    yy, xx = np.mgrid[0:n, 0:n] / (n - 1) * 2 - 1
    r = np.hypot(xx, yy)
    p = np.stack([xx * 2.2, yy * 2.2, np.full_like(xx, 0.37)], -1).astype(np.float32) + 5
    nz = Noise3(seed).fbm(p, 6)
    a = np.clip((nz - 0.35) * 2.4, 0, 1) * np.clip(1 - r, 0, 1) ** 1.6
    col = np.array([128, 40, 230], np.float32) * (1 - a[..., None]) + np.array([214, 120, 255], np.float32) * a[..., None]
    arr = np.dstack([col, a * 255]).astype(np.uint8)
    return Image.fromarray(arr, 'RGBA').filter(ImageFilter.GaussianBlur(1.2))


def star(n=64):
    """El destello de una estrella: punto blanco con halo y una cruz muy fina (blanco: el color lo pone RiftSky)."""
    yy, xx = np.mgrid[0:n, 0:n] / (n - 1) * 2 - 1
    r = np.hypot(xx, yy)
    core = np.exp(-(r / 0.09) ** 2)
    halo = np.exp(-(r / 0.32) ** 2) * 0.32
    cross = (np.exp(-(xx / 0.022) ** 2) + np.exp(-(yy / 0.022) ** 2)) * np.exp(-(r / 0.55) ** 2) * 0.22
    a = np.clip(core + halo + cross, 0, 1) * np.clip((1 - r) * 4, 0, 1)
    arr = np.dstack([np.full((n, n, 3), 255), a * 255]).astype(np.uint8)
    return Image.fromarray(arr, 'RGBA')


def streak(w=512, h=32):
    """La estela de una fugaz: blanca (el color lo pone RiftSky), cabeza redonda a la derecha y una cola que se afina
    y se apaga hacia la izquierda; de ancho cae en campana (sin bordes duros)."""
    x = (np.arange(w) + 0.5) / w           # 0 cola .. 1 cabeza
    y = (np.arange(h) + 0.5) / h * 2 - 1   # -1..1 de ancho
    xx, yy = np.meshgrid(x, y)
    width = 0.08 + 0.42 * xx ** 1.6        # la cola se afina
    body = np.exp(-(yy / width) ** 2) * xx ** 2.2
    hx = (xx - 0.965) / 0.03
    head = np.exp(-(hx ** 2 + (yy / 0.5) ** 2) * 1.0)
    a = np.clip(body * 0.85 + head, 0, 1) * np.clip((1 - xx) * 30, 0, 1)
    arr = np.dstack([np.full((h, w, 3), 255), a * 255]).astype(np.uint8)
    return Image.fromarray(arr, 'RGBA')


def eclipse(n=512):
    """El sol negro: disco negro (opaco) con un anillo fino muy brillante, corona violeta con rayos suaves y un halo."""
    yy, xx = np.mgrid[0:n, 0:n] / (n - 1) * 2 - 1
    r = np.hypot(xx, yy)
    ang = np.arctan2(yy, xx)
    R = 0.34
    disc = smooth(R + 0.006, R - 0.006, r)
    ring = np.exp(-((r - R) / 0.012) ** 2)
    nz = Noise3(909)
    rays = nz.fbm(np.stack([np.cos(ang) * 3, np.sin(ang) * 3, r * 0.6], -1).astype(np.float32) + 4, 5)
    rays = smooth(0.35, 0.75, rays)
    out = np.clip(r - R, 0, None)
    corona = np.exp(-out / (0.05 + 0.16 * rays)) * (r > R - 0.01)
    halo = np.exp(-(out / 0.45) ** 2) * 0.35
    glow = np.clip(ring + corona * 0.8 + halo, 0, 1) * np.clip((1 - r) * 5, 0, 1)
    white = np.array([255, 236, 255], np.float32)
    violet = np.array([170, 70, 255], np.float32)
    deep = np.array([90, 20, 170], np.float32)
    col = deep + (violet - deep) * np.clip(corona, 0, 1)[..., None]
    col = col + (white - col) * np.clip(ring * 1.2, 0, 1)[..., None]
    col = col * (1 - disc[..., None])  # el disco, negro
    a = np.clip(np.maximum(disc, glow), 0, 1)
    arr = np.dstack([np.clip(col, 0, 255), a * 255]).astype(np.uint8)
    return Image.fromarray(arr, 'RGBA')


def main():
    os.makedirs(OUT, exist_ok=True)
    s = sky()
    s.save(os.path.join(OUT, 'grieta_cielo.png'), optimize=True)
    cloud().save(os.path.join(OUT, 'grieta_nube.png'), optimize=True)
    star().save(os.path.join(OUT, 'grieta_estrella.png'), optimize=True)
    streak().save(os.path.join(OUT, 'grieta_fugaz.png'), optimize=True)
    eclipse().save(os.path.join(OUT, 'grieta_eclipse.png'), optimize=True)
    names = ('grieta_cielo.png', 'grieta_nube.png', 'grieta_estrella.png', 'grieta_fugaz.png', 'grieta_eclipse.png')
    for f in names:
        # suaves (lineales) al estirarlas; el panorama se repite en la longitud, las demás no
        with open(os.path.join(OUT, f + '.mcmeta'), 'w') as fh:
            fh.write('{"texture": {"blur": true, "clamp": %s}}\n' % ('false' if f == 'grieta_cielo.png' else 'true'))
        print(f, os.path.getsize(os.path.join(OUT, f)) // 1024, 'KB')
    if '--preview' in sys.argv:
        dest = sys.argv[sys.argv.index('--preview') + 1]
        os.makedirs(dest, exist_ok=True)
        s.save(os.path.join(dest, 'cielo.png'))


if __name__ == '__main__':
    main()
