"""Los panoramas del agujero de gusano (1.3.96): de las 6 caras que saca la prueba en el juego a equirectangulares.

    python3 -I tools/void/panoramas.py <carpeta de la prueba (con panoramas/<nombre>/screenshots/panorama_N.png)>

El dueño: «la escena del agujero de gusano al menos que muestre la dimensión; en el Nether hay fuego de textura, pero no
sé si estoy viendo el Nether de verdad». Los capturó la prueba en el juego ([prueba-jefes=panoramas], BossTest
panoramaStep) con la herramienta de panoramas de Minecraft: 6 caras de 90° mirando al sur (0 sur, 1 oeste, 2 norte, 3
este, 4 arriba, 5 abajo; arriba de cada imagen, lo de arriba; en la de arriba, el norte; en la de abajo, el sur). Aquí se
pasan a una imagen equirectangular de 1024×512 (longitud 0 = sur en el centro, que es como las lee wormhole.fsh) y se
guardan en textures/environment/tunel_<nombre>.png. El Overworld no: lo saca el juego del panorama del menú de cada uno
(TunnelPanoramas, con estas mismas cuentas).
"""
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient', 'textures', 'environment'))
W, H = 1024, 512

# (adelante, derecha, arriba) de cada cara, en x este, y arriba, z sur
FACES = [((0, 0, 1), (-1, 0, 0), (0, 1, 0)),
         ((-1, 0, 0), (0, 0, -1), (0, 1, 0)),
         ((0, 0, -1), (1, 0, 0), (0, 1, 0)),
         ((1, 0, 0), (0, 0, 1), (0, 1, 0)),
         ((0, 1, 0), (-1, 0, 0), (0, 0, -1)),
         ((0, -1, 0), (-1, 0, 0), (0, 0, 1))]


def directions():
    """La dirección de cada píxel de la equirectangular (longitud 0 = sur, en el centro; arriba, el cénit)."""
    u = (np.arange(W) + 0.5) / W
    v = (np.arange(H) + 0.5) / H
    lon = (u - 0.5) * 2 * math.pi
    lat = (0.5 - v) * math.pi
    lon, lat = np.meshgrid(lon, lat)
    return np.stack([np.cos(lat) * np.sin(lon), np.sin(lat), np.cos(lat) * np.cos(lon)], -1)


def equirect(faces):
    n = faces[0].shape[0]
    d = directions()
    out = np.zeros((H, W, 3))
    dots = np.stack([d @ np.array(f[0], float) for f in FACES], -1)
    which = np.argmax(dots, -1)
    for k, (f, r, up) in enumerate(FACES):
        m = which == k
        dd = d[m]
        fw = dd @ np.array(f, float)
        sx = (dd @ np.array(r, float)) / fw
        sy = (dd @ np.array(up, float)) / fw
        px = np.clip((sx + 1) / 2 * (n - 1), 0, n - 1)
        py = np.clip((1 - (sy + 1) / 2) * (n - 1), 0, n - 1)
        x0, y0 = np.floor(px).astype(int), np.floor(py).astype(int)
        x1, y1 = np.minimum(x0 + 1, n - 1), np.minimum(y0 + 1, n - 1)
        fx, fy = (px - x0)[:, None], (py - y0)[:, None]
        img = faces[k]
        out[m] = (img[y0, x0] * (1 - fx) * (1 - fy) + img[y0, x1] * fx * (1 - fy) + img[y1, x0] * (1 - fx) * fy
                  + img[y1, x1] * fx * fy)
    return out


def main():
    src = sys.argv[1]
    for name in ('nether', 'end', 'void'):
        d = os.path.join(src, 'panoramas', name, 'screenshots')
        if not os.path.isdir(d):
            print('falta', d)
            continue
        faces = [np.asarray(Image.open(os.path.join(d, 'panorama_%d.png' % k)).convert('RGB')).astype(float) for k in range(6)]
        eq = equirect(faces)
        img = Image.fromarray(np.clip(eq, 0, 255).astype(np.uint8))
        path = os.path.join(OUT, 'tunel_%s.png' % name)
        img.save(path, optimize=True)
        print('%s: %s (%d KB)' % (name, path, os.path.getsize(path) // 1024))


if __name__ == '__main__':
    main()
