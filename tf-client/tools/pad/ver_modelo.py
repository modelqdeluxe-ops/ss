"""Dibuja modelos de bloque de Minecraft (JSON con elements) sin abrir el juego, en vista 3/4, con sus texturas.
Para elegir y revisar modelos (1.3.158: el cofre del banco del clan sale de una crate de madera del mod).
Uso: python3 tools/pad/ver_modelo.py <salida.png> <modelo> [<modelo>…]
  <modelo> = espacio:block/nombre (fscrates:block/crate_lvl1) o varios unidos con + (base+tapa).
  --celda N (tamaño de cada dibujo, 320), --cols N, --yaw G (giro, 35), --pitch G (inclinación, 28), --luz (sombreado)."""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets'))
SHADE = {'up': 1.0, 'down': 0.5, 'north': 0.8, 'south': 0.8, 'east': 0.6, 'west': 0.6}


def split(ref, kind):
    ns, path = ref.split(':', 1) if ':' in ref else ('minecraft', ref)
    return os.path.join(ASSETS, ns, kind, path)


def load_model(ref):
    with open(split(ref, 'models') + '.json', encoding='utf-8') as f:
        m = json.load(f)
    if 'parent' in m and 'elements' not in m:
        try:
            p = load_model(m['parent'])
            p['textures'] = {**p.get('textures', {}), **m.get('textures', {})}
            return p
        except FileNotFoundError:
            pass
    m.setdefault('textures', {})
    m['_ns'] = ref.split(':', 1)[0] if ':' in ref else 'minecraft'
    return m


def resolve(textures, ref, ns):
    seen = 0
    while ref and ref.startswith('#') and seen < 10:
        ref = textures.get(ref[1:])
        seen += 1
    if not ref or ref.startswith('#'):
        return None
    return ref if ':' in ref else ns + ':' + ref


_TEX = {}


def texture(ref):
    if ref not in _TEX:
        p = split(ref, 'textures') + '.png'
        im = Image.open(p).convert('RGBA')
        w, h = im.size
        if h > w and h % w == 0:  # animada: el primer cuadro
            im = im.crop((0, 0, w, w))
        _TEX[ref] = np.asarray(im).astype(np.float32) / 255.0
    return _TEX[ref]


def corners(d, f, t):
    x0, y0, z0 = f
    x1, y1, z1 = t
    return {
        'north': [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        'south': [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        'east': [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        'west': [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        'up': [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        'down': [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[d]


def default_uv(d, f, t):
    x0, y0, z0 = f
    x1, y1, z1 = t
    if d in ('up', 'down'):
        return [x0, z0, x1, z1]
    if d in ('north', 'south'):
        return [x0, 16 - y1, x1, 16 - y0]
    return [z0, 16 - y1, z1, 16 - y0]


def rot(p, r):
    if not r or not r.get('angle'):
        return p
    a = math.radians(r['angle'])
    o = r.get('origin', [8, 8, 8])
    x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
    c, s = math.cos(a), math.sin(a)
    ax = r['axis']
    if ax == 'x':
        y, z = y * c - z * s, y * s + z * c
    elif ax == 'y':
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + o[0], y + o[1], z + o[2])


def quads(refs):
    out = []
    for ref in refs:
        m = load_model(ref)
        for el in m.get('elements', []):
            f, t = el['from'], el['to']
            for d, face in el.get('faces', {}).items():
                tr = resolve(m['textures'], face.get('texture'), m['_ns'])
                if tr is None:
                    continue
                uv = face.get('uv') or default_uv(d, f, t)
                uvs = [(uv[0], uv[1]), (uv[2], uv[1]), (uv[2], uv[3]), (uv[0], uv[3])]
                k = (face.get('rotation', 0) // 90) % 4
                uvs = uvs[k:] + uvs[:k]
                ps = [rot(p, el.get('rotation')) for p in corners(d, f, t)]
                out.append((ps, uvs, tr, SHADE[d]))
    return out


def render(refs, size=320, yaw=35, pitch=28, light=True):
    qs = quads(refs)
    if not qs:
        return Image.new('RGBA', (size, size), (0, 0, 0, 0))
    ya, pa = math.radians(yaw), math.radians(pitch)

    def view(p):
        x, y, z = p[0] - 8, p[1] - 8, p[2] - 8
        x, z = x * math.cos(ya) - z * math.sin(ya), x * math.sin(ya) + z * math.cos(ya)
        y, z = y * math.cos(pa) - z * math.sin(pa), y * math.sin(pa) + z * math.cos(pa)
        return (x, -y, z)

    pts = np.array([view(p) for q in qs for p in q[0]])
    lo, hi = pts.min(0), pts.max(0)
    sc = 0.86 * size / max(hi[0] - lo[0], hi[1] - lo[1], 1e-3)
    cx, cy = (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2
    col = np.zeros((size, size, 4), np.float32)
    zb = np.full((size, size), np.inf, np.float32)
    for ps, uvs, tr, shade in qs:
        tex = texture(tr)
        th, tw = tex.shape[:2]
        v = [view(p) for p in ps]
        sp = [((a[0] - cx) * sc + size / 2, (a[1] - cy) * sc + size / 2, a[2]) for a in v]
        for tri in ((0, 1, 2), (0, 2, 3)):
            A, B, C = (sp[i] for i in tri)
            UA, UB, UC = (uvs[i] for i in tri)
            x0, x1 = int(max(0, math.floor(min(A[0], B[0], C[0])))), int(min(size - 1, math.ceil(max(A[0], B[0], C[0]))))
            y0, y1 = int(max(0, math.floor(min(A[1], B[1], C[1])))), int(min(size - 1, math.ceil(max(A[1], B[1], C[1]))))
            if x1 < x0 or y1 < y0:
                continue
            den = (B[1] - C[1]) * (A[0] - C[0]) + (C[0] - B[0]) * (A[1] - C[1])
            if abs(den) < 1e-9:
                continue
            xs, ys = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            w0 = ((B[1] - C[1]) * (xs - C[0]) + (C[0] - B[0]) * (ys - C[1])) / den
            w1 = ((C[1] - A[1]) * (xs - C[0]) + (A[0] - C[0]) * (ys - C[1])) / den
            w2 = 1 - w0 - w1
            inside = (w0 >= -1e-4) & (w1 >= -1e-4) & (w2 >= -1e-4)
            if not inside.any():
                continue
            z = w0 * A[2] + w1 * B[2] + w2 * C[2]
            u = (w0 * UA[0] + w1 * UB[0] + w2 * UC[0]) / 16.0
            vv = (w0 * UA[1] + w1 * UB[1] + w2 * UC[1]) / 16.0
            tx = np.clip((u * tw).astype(int), 0, tw - 1)
            ty = np.clip((vv * th).astype(int), 0, th - 1)
            s = tex[ty, tx]
            ok = inside & (s[..., 3] > 0.1)
            sub = zb[y0:y1 + 1, x0:x1 + 1]
            ok &= z < sub
            if not ok.any():
                continue
            sub[ok] = z[ok]
            c = col[y0:y1 + 1, x0:x1 + 1]
            rgb = s[..., :3] * (shade if light else 1.0)
            c[ok, :3] = rgb[ok]
            c[ok, 3] = 1.0
    return Image.fromarray((np.clip(col, 0, 1) * 255).astype(np.uint8), 'RGBA')


def main():
    args = sys.argv[1:]
    opts = {'--celda': 320, '--cols': 5, '--yaw': 35, '--pitch': 28}
    light = True
    rest = []
    i = 0
    while i < len(args):
        if args[i] in opts:
            opts[args[i]] = float(args[i + 1])
            i += 2
        elif args[i] == '--sinluz':
            light = False
            i += 1
        else:
            rest.append(args[i])
            i += 1
    out, models = rest[0], rest[1:]
    cell, cols = int(opts['--celda']), int(opts['--cols'])
    cols = max(1, min(cols, len(models)))
    rows = (len(models) + cols - 1) // cols
    sheet = Image.new('RGB', (cols * cell, rows * (cell + 20)), (96, 118, 104))
    d = ImageDraw.Draw(sheet)
    for n, mref in enumerate(models):
        im = render(mref.split('+'), cell, opts['--yaw'], opts['--pitch'], light)
        x, y = (n % cols) * cell, (n // cols) * (cell + 20)
        sheet.paste(im, (x, y), im)
        d.text((x + 6, y + cell + 3), mref.split('+')[0].split('/')[-1], fill=(255, 255, 255))
    sheet.save(out)
    print(out, len(models), 'modelos')


if __name__ == '__main__':
    main()
