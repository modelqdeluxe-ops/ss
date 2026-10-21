"""Desde la ruta de la cámara de la cinemática de las crates: qué parte de la imagen es cielo (los rayos que no chocan
con nada) y cuánto de eso está cerca del horizonte (lo que hacía que pareciera una pradera; el dueño: «no quiero una
pradera»). Tiene que salir casi 0 %.

    python3 -I tools/crates/cielo_visible.py
"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import santuario as S  # noqa: E402

sc = S.build()
solid = sc.a != 0
# lo que casi no tapa: flores, hierba, enredaderas finas... cuentan como transparentes para el cielo
thin = set()
for i, st in enumerate(sc.pal):
    n = st.split('[')[0]
    if any(k in n for k in ('grass', 'fern', 'vine', 'flower', 'orchid', 'allium', 'tulip', 'bluet', 'daisy', 'lily',
                            'petals', 'carpet', 'chain', 'lichen', 'blossom', 'pad', 'mushroom', 'pickle', 'water')):
        if not n.endswith(('_block', 'mushroom_stem')):
            thin.add(i)
mask = np.isin(sc.a, list(thin))
block = solid & ~mask


def cast(o, d, maxd=140.0):
    x, y, z = o
    ix, iy, iz = math.floor(x), math.floor(y), math.floor(z)
    step = [1 if c > 0 else -1 for c in d]
    tmax, tdelta = [], []
    for c, dc, ic, s in ((x, d[0], ix, step[0]), (y, d[1], iy, step[1]), (z, d[2], iz, step[2])):
        if abs(dc) < 1e-9:
            tmax.append(1e9)
            tdelta.append(1e9)
        else:
            nb = ic + (1 if s > 0 else 0)
            tmax.append((nb - c) / dc)
            tdelta.append(abs(1 / dc))
    t = 0.0
    while t < maxd:
        i, j, k = ix - S.X0, iy - S.Y0, iz - S.Z0
        if 0 <= i < S.NX and 0 <= j < S.NY and 0 <= k < S.NZ:
            if block[i, j, k]:
                return t
        elif iy > S.Y1 or iy < S.Y0 - 2:
            return None
        a = int(np.argmin(tmax))
        t = tmax[a]
        if a == 0:
            ix += step[0]
        elif a == 1:
            iy += step[1]
        else:
            iz += step[2]
        tmax[a] += tdelta[a]
    return None


W, H = 64, 36
for tick in (0, 40, 76, 102, 140, 200, 258, 330):
    eye, at, fov = S.camera_at(tick)
    f = np.array(at) - eye
    f /= np.linalg.norm(f)
    r = np.cross(f, [0, 1, 0])
    r /= np.linalg.norm(r)
    u = np.cross(r, f)
    ty = math.tan(math.radians(fov) / 2)
    tx = ty * 16 / 9
    sky = low = 0
    rows = []
    for py in range(H):
        row = ''
        for px in range(W):
            nx = (px + 0.5) / W * 2 - 1
            ny = 1 - (py + 0.5) / H * 2
            d = f + r * nx * tx + u * ny * ty
            d /= np.linalg.norm(d)
            hit = cast(eye, d)
            if hit is None:
                sky += 1
                el = math.degrees(math.asin(d[1]))
                if el < 25:
                    low += 1
                row += '#' if el < 25 else '+'
            else:
                row += '.' if hit < 12 else ':' if hit < 25 else 'o'
        rows.append(row)
    print(f'tick {tick}: cielo {sky / (W * H) * 100:.1f} %, cielo bajo (<25°) {low / (W * H) * 100:.1f} %')
    if tick in (0, 140):
        print('\n'.join(rows))
