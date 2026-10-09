"""Modelos de ítem de Minecraft (JSON con elements) → triángulos para el rasterizador de vfx_render.

Sirve para dibujar los iconos de las skills que el pack no trae (la clase del Dragón Rojo): se dibuja el efecto de la
skill (el modelo que lleva en la cabeza el soporte) visto de frente, como lo vería el jugador.
"""
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import vfx_render as R  # noqa: E402

# Esquinas de cada cara (arriba-izquierda, arriba-derecha, abajo-derecha, abajo-izquierda) como las reparte Minecraft
# para el UV [u0, v0, u1, v1], y su normal
FACES = {
    'north': (((1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0)), (0, 0, -1)),
    'south': (((0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)), (0, 0, 1)),
    'west': (((0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)), (-1, 0, 0)),
    'east': (((1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1)), (1, 0, 0)),
    'up': (((0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)), (0, 1, 0)),
    'down': (((0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)), (0, -1, 0)),
}


def split_ref(ref, default_ns='minecraft'):
    ref = str(ref)
    return ref.split(':', 1) if ':' in ref else (default_ns, ref)


def load(find, ref, depth=0):
    """Modelo con sus padres resueltos: (elements, texturas) o (None, {}). find(«ns/models/x.json») → ruta."""
    ns, path = split_ref(ref)
    src = find(f'{ns}/models/{path}.json')
    if not src or depth > 8:
        return None, {}
    import json
    data = json.load(open(src, encoding='utf-8'))
    elements, textures = None, {}
    if data.get('parent'):
        pns, ppath = split_ref(data['parent'], ns)
        elements, textures = load(find, f'{pns}:{ppath}', depth + 1)
    textures = dict(textures)
    for k, v in (data.get('textures') or {}).items():
        textures[k] = v if str(v).startswith('#') or ':' in str(v) else f'{ns}:{v}'
    if data.get('elements'):
        elements = data['elements']
    return elements, textures


def resolve(textures, key):
    seen = set()
    while key and str(key).startswith('#') and key not in seen:
        seen.add(key)
        key = textures.get(key[1:])
    return key


def tris(find, ref):
    """Triángulos del modelo en bloques (16 px = 1), centrado en el origen del modelo."""
    elements, textures = load(find, ref)
    if not elements:
        return []
    cache = {}

    def image(tref):
        if tref not in cache:
            tns, tpath = split_ref(tref)
            p = find(f'{tns}/textures/{tpath}.png')
            img = None
            if p:
                im = Image.open(p).convert('RGBA')
                if im.height > im.width and im.height % im.width == 0:
                    im = im.crop((0, 0, im.width, im.width))  # animada: primer fotograma
                img = np.asarray(im, dtype=np.float32) / 255.0
            cache[tref] = img
        return cache[tref]

    out = []
    for el in elements:
        f, t = np.array(el['from'], float), np.array(el['to'], float)
        rot = el.get('rotation')
        if rot:
            origin = np.array(rot.get('origin', [8, 8, 8]), float)
            ang = math.radians(float(rot.get('angle', 0)))
            ax = rot.get('axis', 'y')
            c, s = math.cos(ang), math.sin(ang)
            if ax == 'x':
                M = np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
            elif ax == 'y':
                M = np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
            else:
                M = np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])
            if rot.get('rescale'):
                k = 1 / max(abs(c), 1e-6)
                S = np.diag([1 if ax == 'x' else k, 1 if ax == 'y' else k, 1 if ax == 'z' else k])
                M = M @ S
        else:
            origin, M = np.zeros(3), np.eye(3)
        for face, fd in (el.get('faces') or {}).items():
            if face not in FACES:
                continue
            tref = resolve(textures, fd.get('texture'))
            img = image(tref) if tref else None
            if img is None:
                continue
            corners, normal = FACES[face]
            pts = np.array([[t[i] if c[i] else f[i] for i in range(3)] for c in corners])
            pts = (M @ (pts - origin).T).T + origin
            u0, v0, u1, v1 = [float(x) / 16 for x in (fd.get('uv') or default_uv(face, f, t))]
            uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
            r = int(fd.get('rotation', 0)) // 90 % 4
            uvs = uvs[r:] + uvs[:r]
            n = M @ np.array(normal, float)
            P = (pts - 8) / 16
            UV = np.array(uvs)
            for a, b, c in ((0, 1, 2), (0, 2, 3)):
                out.append((P[[a, b, c]], UV[[a, b, c]], img, False, n, None))
    return out


def default_uv(face, f, t):
    if face in ('north', 'south'):
        return [f[0], 16 - t[1], t[0], 16 - f[1]]
    if face in ('east', 'west'):
        return [f[2], 16 - t[1], t[2], 16 - f[1]]
    return [f[0], f[2], t[0], t[2]]


def icon(find, ref, size=64, yaw=0, pitch=-8):
    """Imagen cuadrada del modelo visto de frente, recortada a lo que se ve y centrada."""
    tr = tris(find, ref)
    if not tr:
        return None
    # los efectos brillan (en el juego van a toda luz): sin sombreado
    tr = [(p, uv, im, True, n, t) for p, uv, im, _, n, t in tr]
    # la vista en la que más se ve: de frente, desde arriba (efectos planos en el suelo) o de lado
    best, best_cov = None, 0
    for yw, pt in ((yaw, pitch), (yaw, -65), (90, pitch), (yaw + 180, pitch)):
        img = R.raster(tr, size * 4, yaw=yw, pitch=pt, fov=30, ss=1)
        box = img.getbbox()
        if not box:
            continue
        crop = img.crop(box)
        side = max(crop.size)
        cov = sum(1 for a in crop.getchannel('A').getdata() if a > 40) / float(side * side)
        if cov > best_cov * 1.4:
            best, best_cov = img, cov
    if best is None:
        return None
    img = best
    box = img.getbbox()
    img = img.crop(box)
    side = max(img.width, img.height)
    sq = Image.new('RGBA', (side, side))
    sq.paste(img, ((side - img.width) // 2, (side - img.height) // 2))
    return sq.resize((size, size), Image.LANCZOS)


def framed(img, size=32):
    """Con el mismo marco que los iconos de los packs: fondo negro y borde gris de 1 px."""
    out = Image.new('RGBA', (size, size), (155, 155, 155, 255))
    out.paste(Image.new('RGBA', (size - 2, size - 2), (0, 0, 0, 255)), (1, 1))
    inner = img.resize((size - 4, size - 4), Image.LANCZOS)
    out.alpha_composite(inner, (2, 2))
    return out
