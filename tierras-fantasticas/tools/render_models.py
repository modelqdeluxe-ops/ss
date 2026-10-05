"""Renderiza modelos de objeto de Minecraft (JSON de Blockbench) a PNG transparente con la vista de inventario.

Uso: python3 tools/render_models.py <carpeta assets> <salida> <tamaño> modelo1 modelo2 ...
Necesita Pillow y numpy."""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = sys.argv[1]  # assets folder containing <ns>/models and <ns>/textures
OUT = sys.argv[2]
SIZE = int(sys.argv[3]) if len(sys.argv) > 3 else 512
SS = 3  # supersampling


def load_tex(ref, ns_default):
    ns, path = ref.split(':', 1) if ':' in ref else (ns_default, ref)
    img = Image.open(os.path.join(ROOT, ns, 'textures', path + '.png')).convert('RGBA')
    w, h = img.size
    if h > w and h % w == 0:  # animated strip: first frame
        img = img.crop((0, 0, w, w))
    return np.asarray(img).astype(np.float32) / 255.0


def rot_matrix(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == 'x':
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == 'y':
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def face_corners(f, t, face):
    x1, y1, z1 = f
    x2, y2, z2 = t
    return {
        'north': ((x2, y2, z1), (x1, y2, z1), (x2, y1, z1)),
        'south': ((x1, y2, z2), (x2, y2, z2), (x1, y1, z2)),
        'east': ((x2, y2, z2), (x2, y2, z1), (x2, y1, z2)),
        'west': ((x1, y2, z1), (x1, y2, z2), (x1, y1, z1)),
        'up': ((x1, y2, z1), (x2, y2, z1), (x1, y2, z2)),
        'down': ((x1, y1, z2), (x2, y1, z2), (x1, y1, z1)),
    }[face]


def render(model_path, out_path):
    m = json.load(open(model_path))
    ns = model_path.split(os.sep + 'models' + os.sep)[0].split(os.sep)[-1]
    texs = {k: load_tex(v, ns) for k, v in m.get('textures', {}).items() if k != 'particle'}
    disp = m.get('display', {}).get('gui', {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.625] * 3})
    rx, ry, rz = disp.get('rotation', [0, 0, 0])
    tr = np.array(disp.get('translation', [0, 0, 0]), dtype=float)
    sc = np.array(disp.get('scale', [1, 1, 1]), dtype=float)
    R = rot_matrix('x', rx) @ rot_matrix('y', ry) @ rot_matrix('z', rz)

    def transform(p):
        v = (np.asarray(p, dtype=float) - 8.0) * sc
        return R @ v + tr

    N = SIZE * SS
    color = np.zeros((N, N, 4), np.float32)
    depth = np.full((N, N), -1e9, np.float32)
    # The GUI slot spans 16 model units; leave a little margin.
    unit = N / 16.0 * 0.98
    light = np.array([0.35, 0.6, 1.0])
    light /= np.linalg.norm(light)

    for el in m.get('elements', []):
        f, t = el['from'], el['to']
        rot = el.get('rotation')
        Rel = rot_matrix(rot['axis'], rot['angle']) if rot and rot.get('angle') else None
        origin = np.array(rot['origin'], dtype=float) if rot else None
        for face, fd in el.get('faces', {}).items():
            tex = texs.get(fd.get('texture', '#0').lstrip('#'))
            if tex is None:
                continue
            th, tw = tex.shape[:2]
            corners = []
            for p in face_corners(f, t, face):
                p = np.array(p, dtype=float)
                if Rel is not None:
                    p = Rel @ (p - origin) + origin
                corners.append(transform(p))
            tl, trc, bl = corners
            e1, e2 = trc - tl, bl - tl
            normal = np.cross(e2, e1)
            nlen = np.linalg.norm(normal)
            if nlen < 1e-9:
                continue
            normal /= nlen
            shade = 0.55 + 0.45 * max(0.0, float(normal @ light))
            # screen coords
            def scr(p):
                return np.array([N / 2 + p[0] * unit, N / 2 - p[1] * unit, p[2]])

            S0, S1, S2 = scr(tl), scr(trc), scr(bl)
            S3 = S1 + S2 - S0
            xs = [S0[0], S1[0], S2[0], S3[0]]
            ys = [S0[1], S1[1], S2[1], S3[1]]
            x0, x1 = max(0, int(math.floor(min(xs)))), min(N - 1, int(math.ceil(max(xs))))
            y0, y1 = max(0, int(math.floor(min(ys)))), min(N - 1, int(math.ceil(max(ys))))
            if x1 < x0 or y1 < y0:
                continue
            a = np.array([[S1[0] - S0[0], S2[0] - S0[0]], [S1[1] - S0[1], S2[1] - S0[1]]])
            det = np.linalg.det(a)
            if abs(det) < 1e-6:
                continue
            inv = np.linalg.inv(a)
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            dx, dy = gx - S0[0], gy - S0[1]
            s = inv[0, 0] * dx + inv[0, 1] * dy
            tt = inv[1, 0] * dx + inv[1, 1] * dy
            inside = (s >= 0) & (s <= 1) & (tt >= 0) & (tt <= 1)
            if not inside.any():
                continue
            z = S0[2] + s * (S1[2] - S0[2]) + tt * (S2[2] - S0[2])
            uv = fd.get('uv', [0, 0, 16, 16])
            r = fd.get('rotation', 0) % 360
            ss_, ts_ = s, tt
            if r == 90:
                ss_, ts_ = tt, 1 - s
            elif r == 180:
                ss_, ts_ = 1 - s, 1 - tt
            elif r == 270:
                ss_, ts_ = 1 - tt, s
            u = (uv[0] + ss_ * (uv[2] - uv[0])) / 16.0 * tw
            v = (uv[1] + ts_ * (uv[3] - uv[1])) / 16.0 * th
            ui = np.clip(np.floor(u).astype(int), 0, tw - 1)
            vi = np.clip(np.floor(v).astype(int), 0, th - 1)
            px = tex[vi, ui]
            ok = inside & (px[..., 3] > 0.1)
            sub_d = depth[y0:y1 + 1, x0:x1 + 1]
            sub_c = color[y0:y1 + 1, x0:x1 + 1]
            closer = ok & (z > sub_d)
            rgb = px[..., :3] * shade
            sub_c[closer, :3] = rgb[closer]
            sub_c[closer, 3] = 1.0
            sub_d[closer] = z[closer]

    img = Image.fromarray((np.clip(color, 0, 1) * 255).astype(np.uint8), 'RGBA')
    bbox = img.getbbox()
    if bbox:
        img = img.crop(bbox)
        side = max(img.size)
        pad = int(side * 0.04)
        canvas = Image.new('RGBA', (side + 2 * pad, side + 2 * pad), (0, 0, 0, 0))
        canvas.paste(img, ((canvas.width - img.width) // 2, (canvas.height - img.height) // 2))
        img = canvas
    img = img.resize((SIZE, SIZE), Image.LANCZOS)
    img.save(out_path)


os.makedirs(OUT, exist_ok=True)
for name in sys.argv[4:]:
    path = os.path.join(ROOT, 'nazgul_forge', 'models', 'nazgul_weaponry_vol_4', name + '.json')
    render(path, os.path.join(OUT, name + '.png'))
    print('rendered', name)
