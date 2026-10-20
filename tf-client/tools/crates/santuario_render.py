"""Vista previa con las texturas de verdad de la cinemática de las crates (TF Client 1.3.99), sin abrir el juego.

    xvfb-run -a python3 -I tools/crates/santuario_render.py <carpeta> [ticks...]

Dibuja la escena de santuario.py como la cinemática: los bloques con sus modelos y texturas de Minecraft 1.20.1 (el
jar del cliente se baja de Mojang la primera vez a ~/.cache/tfclient/; no se sube al repo), la luz de la escena (la
misma cuenta que SanctuaryScene.light: la sombra verde azulada, el haz, la luz del hueco, el sol de arriba, el
turquesa del estanque, las luces mágicas), el agua con su shader (crate_water, con la textura animada del agua del
juego, como el atlas en el mod), la niebla, la crate (cine_mythical), el haz de sol, su foco y sus rayos, el polvo, los
halos, las chispas y la bruma, y luego los MISMOS shaders del mod (crate_sky, crate_bloom, crate_post). No hace
oclusión ambiental ni dibuja la llave ni los premios: sirve para ver la composición, los colores y la luz antes de la
prueba en el juego. Lo que suma luz no escribe profundidad (como GLOW_ADD en el juego; si la escribiera, las bandas
del haz se pisarían y saldría una cuadrícula).

Para compararla con la imagen del dueño: el encuadre del tick 0 es el suyo (cámara a 3,7 de alto, 10,5 bloques delante
del altar, 36° de campo vertical).
"""
import io
import json
import math
import os
import sys
import urllib.request
import zipfile

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import santuario as S  # noqa: E402

RES = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets'))
SHADERS = os.path.join(RES, 'tfclient', 'shaders', 'core')
CACHE = os.path.expanduser('~/.cache/tfclient/minecraft-1.20.1-client.jar')

# los de SceneLevel, SanctuaryScene y CrateCinematicScreen (si cambian allí, cámbialos aquí)
GRASS, FOLIAGE, WATER = 0x5AAE4E, 0x4AAA44, 0x3FD8E0
FOG, FOG_START, FOG_END = (0.10, 0.19, 0.20), 18.0, 80.0
THRESHOLD, BLOOM, RAYS, EXPOSURE = 0.78, 0.5, 0.7, 1.55   # los de ScenePost y CrateCinematicScreen (sin abrir)
CRATE_TINT = (0.86, 0.84, 0.80)
BARS = 0.035                       # las franjas de cine (CrateCinematicScreen.overlay)
# la luz de la escena (SanctuaryScene.light): ambiente, haz, luz del hueco, rebote del agua
AMB = (0.20, 0.28, 0.36)
BEAM_C = (1.25, 1.12, 0.80)
SKY_C = (0.34, 0.38, 0.28)
POOL_C = (0.02, 0.30, 0.34)
CROWN_C = (0.95, 0.90, 0.45)
TINTS = {'mangrove_leaves': 0x92C648, 'spruce_leaves': 0x619961, 'birch_leaves': 0x80A755, 'lily_pad': 0x208030}


def rgb(c):
    return ((c >> 16) & 255) / 255.0, ((c >> 8) & 255) / 255.0, (c & 255) / 255.0


# ----------------------------------------------------------------------------------------------- los recursos
def client_jar():
    if not os.path.exists(CACHE):
        os.makedirs(os.path.dirname(CACHE), exist_ok=True)
        m = json.load(urllib.request.urlopen('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'))
        v = [x for x in m['versions'] if x['id'] == '1.20.1'][0]
        vj = json.load(urllib.request.urlopen(v['url']))
        with urllib.request.urlopen(vj['downloads']['client']['url']) as r, open(CACHE + '.part', 'wb') as f:
            f.write(r.read())
        os.replace(CACHE + '.part', CACHE)
    return zipfile.ZipFile(CACHE)


def split_id(s, kind=''):
    ns, path = s.split(':', 1) if ':' in s else ('minecraft', s)
    return ns, path


class Assets:
    def __init__(self):
        self.z = client_jar()
        self.names = set(self.z.namelist())
        self.models = {}

    def read(self, ns, path):
        if ns == 'minecraft':
            p = f'assets/minecraft/{path}'
            return self.z.read(p) if p in self.names else None
        p = os.path.join(RES, ns, path)
        return open(p, 'rb').read() if os.path.exists(p) else None

    def json(self, ns, path):
        b = self.read(ns, path)
        return json.loads(b) if b else None

    def model(self, name):
        if name in self.models:
            return self.models[name]
        ns, path = split_id(name)
        m = self.json(ns, f'models/{path}.json') or {}
        if 'parent' in m and not m['parent'].startswith('builtin/'):
            base = self.model(m['parent'] if ':' in m['parent'] else ns + ':' + m['parent'])
        else:
            base = {'elements': None, 'textures': {}, 'ao': True}
        out = {'elements': m.get('elements', base['elements']), 'textures': {**base['textures'], **m.get('textures', {})},
               'ao': m.get('ambientocclusion', base['ao']), 'ns': ns}
        self.models[name] = out
        return out

    def texture(self, ref):
        ns, path = split_id(ref)
        b = self.read(ns, f'textures/{path}.png')
        if b is None:
            return None
        im = Image.open(io.BytesIO(b)).convert('RGBA')
        w, h = im.size
        if h > w:
            im = im.crop((0, 0, w, w))
        return im


def parse_state(s):
    name, props = (s.split('[', 1) + [''])[:2]
    props = props.rstrip(']')
    d = dict(kv.split('=') for kv in props.split(',') if '=' in kv)
    return name, d


def parts_for(assets, state):
    name, props = parse_state(state)
    ns, path = split_id(name)
    bs = assets.json(ns, f'blockstates/{path}.json')
    if not bs:
        return []
    out = []
    if 'variants' in bs:
        for key, v in bs['variants'].items():
            conds = dict(kv.split('=') for kv in key.split(',')) if key else {}
            if all(props.get(k, val) == val for k, val in conds.items()):
                v = v[0] if isinstance(v, list) else v
                out.append((v['model'], v.get('x', 0), v.get('y', 0)))
                break
    else:
        def match(w):
            if w is None:
                return True
            if 'OR' in w:
                return any(match(x) for x in w['OR'])
            if 'AND' in w:
                return all(match(x) for x in w['AND'])
            return all(props.get(k) in str(v).split('|') for k, v in w.items())
        for part in bs['multipart']:
            if match(part.get('when')):
                a = part['apply']
                a = a[0] if isinstance(a, list) else a
                out.append((a['model'], a.get('x', 0), a.get('y', 0)))
    return out


def resolve(textures, ref, ns):
    for _ in range(8):
        if ref is None or not ref.startswith('#'):
            break
        ref = textures.get(ref[1:])
    if ref is None:
        return None
    return ref if ':' in ref else ns + ':' + ref


# ------------------------------------------------------------------------------------------------- geometría
DIRS = {'down': (0, -1, 0), 'up': (0, 1, 0), 'north': (0, 0, -1), 'south': (0, 0, 1), 'west': (-1, 0, 0), 'east': (1, 0, 0)}
SHADE = {'down': 0.5, 'up': 1.0, 'north': 0.8, 'south': 0.8, 'west': 0.6, 'east': 0.6}


def face_corners(d, f, t):
    x0, y0, z0 = f
    x1, y1, z1 = t
    return {
        'north': [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        'south': [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        'west': [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        'east': [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        'up': [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        'down': [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[d]


def default_uv(d, f, t):
    x0, y0, z0 = f
    x1, y1, z1 = t
    return {'up': [x0, z0, x1, z1], 'down': [x0, 16 - z1, x1, 16 - z0], 'north': [16 - x1, 16 - y1, 16 - x0, 16 - y0],
            'south': [x0, 16 - y1, x1, 16 - y0], 'west': [z0, 16 - y1, z1, 16 - y0], 'east': [16 - z1, 16 - y1, 16 - z0, 16 - y0]}[d]


def rot_elem(p, r):
    if not r:
        return p
    o = np.array(r['origin'], float)
    a = math.radians(r['angle'])
    c, s = math.cos(a), math.sin(a)
    v = np.array(p, float) - o
    ax = r['axis']
    if ax == 'y':
        v = np.array([v[0] * c + v[2] * s, v[1], -v[0] * s + v[2] * c])
    elif ax == 'x':
        v = np.array([v[0], v[1] * c - v[2] * s, v[1] * s + v[2] * c])
    else:
        v = np.array([v[0] * c - v[1] * s, v[0] * s + v[1] * c, v[2]])
    if r.get('rescale'):
        k = 1.0 / max(1e-6, math.cos(a))
        if ax == 'y':
            v[0] *= k
            v[2] *= k
        elif ax == 'x':
            v[1] *= k
            v[2] *= k
        else:
            v[0] *= k
            v[1] *= k
    return v + o


def rot_var(p, xr, yr):
    v = np.array(p, float) - 0.5
    if xr:
        a = -math.radians(xr)
        c, s = math.cos(a), math.sin(a)
        v = np.array([v[0], v[1] * c - v[2] * s, v[1] * s + v[2] * c])
    if yr:
        a = -math.radians(yr)
        c, s = math.cos(a), math.sin(a)
        v = np.array([v[0] * c + v[2] * s, v[1], -v[0] * s + v[2] * c])
    return v + 0.5


def rot_dir(d, xr, yr):
    v = np.array(DIRS[d], float)
    v = rot_var(v + 0.5, xr, yr) - 0.5
    best = max(DIRS, key=lambda k: float(np.dot(DIRS[k], v)))
    return best


class Mesher:
    def __init__(self, assets, sc):
        self.assets = assets
        self.sc = sc
        self.tex_index = {}
        self.tex_list = []
        self.cache = {}
        self.full = np.zeros(len(sc.pal), bool)
        for i, st in enumerate(sc.pal):
            self.full[i] = self.is_full(st)

    def tex(self, ref):
        if ref not in self.tex_index:
            im = self.assets.texture(ref)
            if im is None:
                im = Image.new('RGBA', (16, 16), (255, 0, 255, 255))
            if im.size != (16, 16):
                im = im.resize((16, 16), Image.NEAREST)
            self.tex_index[ref] = len(self.tex_list)
            self.tex_list.append(im)
        return self.tex_index[ref]

    def is_full(self, st):
        name = st.split('[')[0]
        if name == 'minecraft:air' or 'leaves' in name or name in ('minecraft:water',) or 'glass' in name:
            return False
        for (m, xr, yr) in parts_for(self.assets, st):
            mod = self.assets.model(m)
            els = mod['elements'] or []
            if len(els) == 1 and els[0]['from'] == [0, 0, 0] and els[0]['to'] == [16, 16, 16] and not els[0].get('rotation'):
                return True
        return False

    def quads(self, st):
        """Las caras del bloque (en 0..1): lista de (4 esquinas, 4 uv (en píxeles 0..16), textura, dir con la que se tapa,
        tinte, sombra)."""
        if st in self.cache:
            return self.cache[st]
        out = []
        name = st.split('[')[0].split(':')[1]
        for (m, xr, yr) in parts_for(self.assets, st):
            mod = self.assets.model(m)
            for el in mod['elements'] or []:
                f, t = el['from'], el['to']
                for d, face in el['faces'].items():
                    ref = resolve(mod['textures'], face.get('texture'), mod['ns'])
                    if ref is None:
                        continue
                    ti = self.tex(ref)
                    uv = face.get('uv') or default_uv(d, f, t)
                    uvs = [(uv[0], uv[1]), (uv[2], uv[1]), (uv[2], uv[3]), (uv[0], uv[3])]
                    k = (face.get('rotation', 0) // 90) % 4
                    uvs = uvs[k:] + uvs[:k]
                    corners = []
                    for p in face_corners(d, f, t):
                        p = rot_elem(p, el.get('rotation'))
                        corners.append(rot_var(np.array(p) / 16.0, xr, yr))
                    cull = rot_dir(face['cullface'], xr, yr) if 'cullface' in face else None
                    tint = None
                    if 'tintindex' in face:
                        tint = TINTS.get(name, WATER if name == 'water' else GRASS if name in (
                            'grass_block', 'grass', 'tall_grass', 'fern', 'large_fern') else FOLIAGE)
                    shade = SHADE[rot_dir(d, xr, yr)] if el.get('shade', True) else 1.0
                    out.append((corners, uvs, ti, cull, tint, shade))
        self.cache[st] = out
        return out


def build_mesh(sc, assets, light):
    """Los vértices de lo opaco/recortado y del agua: (x, y, z, u, v, r, g, b, a) por vértice, en triángulos."""
    me = Mesher(assets, sc)
    a = sc.a
    solid, water = [], []

    def fullat(x, y, z):
        if not sc.inside(x, y, z):
            return False
        return me.full[a[x - S.X0, y - S.Y0, z - S.Z0]]

    def is_water(x, y, z):
        st = sc.get(x, y, z)
        return st.startswith('minecraft:water') or 'waterlogged=true' in st

    for (i, j, k) in np.argwhere(a != 0):
        st = sc.pal[a[i, j, k]]
        x, y, z = int(i) + S.X0, int(j) + S.Y0, int(k) + S.Z0
        name = st.split('[')[0]
        glow = name in S.GLOW and (sc.flags[a[i, j, k]] & S.F_GLOW)
        if name == 'minecraft:water':
            continue
        for (corners, uvs, ti, cull, tint, shade) in me.quads(st):
            if cull is not None:
                dx, dy, dz = DIRS[cull]
                if fullat(x + dx, y + dy, z + dz):
                    continue
            tr, tg, tb = rgb(tint) if tint is not None else (1.0, 1.0, 1.0)
            vs = []
            for p, (u, v) in zip(corners, uvs):
                wx, wy, wz = x + p[0], y + p[1], z + p[2]
                if glow:
                    l = (1.0, 1.0, 1.0)
                    ao = 1.0
                else:
                    l = light(wx, wy, wz)
                    ao = occlusion(fullat, x, y, z, cull, p) if cull is not None else 1.0
                vs.append((wx, wy, wz, ti, u, v, tr * shade * l[0] * ao, tg * shade * l[1] * ao, tb * shade * l[2] * ao, 1.0))
            for q in (0, 1, 2, 0, 2, 3):
                solid.append(vs[q])
    water = water_mesh(sc, light, fullat)
    return me, solid, water


def occlusion(fullat, x, y, z, face, p):
    """La oclusión ambiental del juego (la luz suave) en la esquina p de la cara 'face' del bloque (x, y, z): los tres
    bloques que tocan esa esquina por fuera la oscurecen."""
    nx, ny, nz = DIRS[face]
    ax = [i for i in range(3) if (nx, ny, nz)[i] == 0]
    base = [x + nx, y + ny, z + nz]
    s = []
    for i in ax:
        d = 1 if p[i] > 0.5 else -1
        c = list(base)
        c[i] += d
        s.append((c, i, d))
    s1 = fullat(*s[0][0])
    s2 = fullat(*s[1][0])
    cc = list(base)
    cc[s[0][1]] += s[0][2]
    cc[s[1][1]] += s[1][2]
    corner = (s1 and s2) or fullat(*cc)
    center = fullat(*base)
    v = [0.2 if o else 1.0 for o in (s1, s2, corner, center)]
    return sum(v) / 4.0


def water_mesh(sc, light, fullat):
    """El agua para el shader crate_water (la misma cuenta que SanctuaryScene.buildWater): superficie con la orilla, el
    brillo del fondo y la luz por esquina; las caras de los lados (cascadas) con su altura desde abajo."""
    def is_water(x, y, z):
        st = sc.get(x, y, z)
        return st.startswith('minecraft:water') or 'waterlogged=true' in st

    def glows(x, y, z):
        n = sc.name(x, y, z)
        return n == 'minecraft:sea_lantern' or n == 'minecraft:sea_pickle'

    out = []
    a = sc.a
    for (i, j, k) in np.argwhere(a != 0):
        x, y, z = int(i) + S.X0, int(j) + S.Y0, int(k) + S.Z0
        if not is_water(x, y, z):
            continue
        above = is_water(x, y + 1, z)
        h = 1.0 if above else 0.875
        falling = 'level=8' in sc.get(x, y, z)
        inn = 0.16 if falling else 0.0
        x0 = x + (0 if is_water(x - 1, y, z) else inn)
        x1 = x + 1 - (0 if is_water(x + 1, y, z) else inn)
        z0 = z + (0 if is_water(x, y, z - 1) else inn)
        z1 = z + 1 - (0 if is_water(x, y, z + 1) else inn)
        if not above:
            vs = []
            for (cx, cz), (px_, pz_) in zip(((x, z), (x + 1, z), (x + 1, z + 1), (x, z + 1)), ((x0, z0), (x1, z0), (x1, z1), (x0, z1))):
                shore = sum(0 if is_water(cx + dx, y, cz + dz) else 1 for dx in (-1, 0) for dz in (-1, 0)) / 4.0
                g = 0
                for dx in range(-2, 2):
                    for dz in range(-2, 2):
                        if glows(cx + dx, y - 1, cz + dz) or glows(cx + dx, y, cz + dz):
                            g += 1
                l = light(cx, y + h, cz)
                vs.append((px_, y + h, pz_, shore, 0.0, min(1.0, g * 0.3), min(1.0, (l[0] + l[1] + l[2]) / 3), 0.0, 0.0))
            for q in (0, 1, 2, 0, 2, 3):
                out.append(vs[q])
        y0 = y
        while is_water(x, y0 - 1, z) and y0 > y - 30:
            y0 -= 1
        for d, (dx, dz) in (('north', (0, -1)), ('south', (0, 1)), ('west', (-1, 0)), ('east', (1, 0))):
            if is_water(x + dx, y, z + dz) or fullat(x + dx, y, z + dz):
                continue
            if dz:
                zz = z0 if dz < 0 else z1
                c = [(x0, y + h, zz), (x1, y + h, zz), (x1, y, zz), (x0, y, zz)]
            else:
                xx = x0 if dx < 0 else x1
                c = [(xx, y + h, z0), (xx, y + h, z1), (xx, y, z1), (xx, y, z0)]
            vs = []
            for p in c:
                wx, wy, wz = p
                l = light(wx, wy, wz)
                u = (wx + wz) * 0.5
                vs.append((wx, wy, wz, min(1.0, (y + h - y0) / 16.0), 1.0, 0.0, min(1.0, (l[0] + l[1] + l[2]) / 3), u, wy - y0))
            for q in (0, 1, 2, 0, 2, 3):
                out.append(vs[q])
    return out


# ------------------------------------------------------------------------------------------------------- luz
def smooth(a, b, x):
    t = min(1.0, max(0.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


def make_light(sc):
    """La misma cuenta que SanctuaryScene.light (la luz de la escena en un punto)."""
    sun = np.array(S.SUN, float)
    crate = np.array(S.CRATE, float)
    op = S.anchors()['opening']
    pb = S.anchors()['poolbox']
    lights = S.lights(sc)
    grid = {}
    for l in lights:
        cx, cy, cz = int(math.floor(l[0] / 4)), int(math.floor(l[1] / 4)), int(math.floor(l[2] / 4))
        for gx in (-1, 0, 1):
            for gy in (-1, 0, 1):
                for gz in (-1, 0, 1):
                    grid.setdefault((cx + gx, cy + gy, cz + gz), []).append(l)

    def light(x, y, z):
        # el haz: una columna ancha con el borde marcado (y un halo suave alrededor)
        dx, dy, dz = x - crate[0], y - (crate[1] + 0.6), z - crate[2]
        along = dx * sun[0] + dy * sun[1] + dz * sun[2]
        ox, oy, oz = dx - sun[0] * along, dy - sun[1] * along, dz - sun[2] * along
        off = math.sqrt(ox * ox + oy * oy + oz * oz)
        beam = (1.0 - smooth(1.7, 2.9, off)) * 0.9 + math.exp(-off * off / 18.0) * 0.3
        # la luz que entra por el hueco de las copas (lo de debajo y lo alto, más)
        ex = abs(x - op[0])
        ez = max(0.0, abs(z - op[2]) - op[3])
        sky = math.exp(-ex * ex / 30.0) * math.exp(-ez * ez / 14.0) * (0.45 + 0.55 * min(1.0, max(0.0, (y - 1.0) / 9.0)))
        # lo alto, alrededor del hueco: le da el sol de lleno (las hojas de arriba, verde amarillo)
        crown = math.exp(-ex * ex / 40.0) * math.exp(-ez * ez / 8.0) * smooth(5.5, 8.5, y)
        # el agua turquesa alumbra desde abajo lo que tiene cerca
        px = max(0.0, abs(x - 0.5) - (pb[0] + 0.5))
        pz = max(0.0, pb[1] - z, z - pb[2] - 1)
        pool = math.exp(-(px * px + pz * pz) / 3.0) * min(1.0, max(0.0, (3.4 - y) / 3.0))
        r = AMB[0] + BEAM_C[0] * beam + SKY_C[0] * sky + POOL_C[0] * pool + CROWN_C[0] * crown
        g = AMB[1] + BEAM_C[1] * beam + SKY_C[1] * sky + POOL_C[1] * pool + CROWN_C[1] * crown
        b = AMB[2] + BEAM_C[2] * beam + SKY_C[2] * sky + POOL_C[2] * pool + CROWN_C[2] * crown
        for l in grid.get((int(math.floor(x / 4)), int(math.floor(y / 4)), int(math.floor(z / 4))), ()):
            ddx, ddy, ddz = x - l[0], y - l[1], z - l[2]
            d2 = ddx * ddx + ddy * ddy + ddz * ddz
            reach = 3.5 * l[6] + 1.5
            if d2 >= reach * reach:
                continue
            kk = 1.0 - math.sqrt(d2) / reach
            kk = kk * kk * min(1.2, l[6] + 0.3)
            r += l[3] * kk
            g += l[4] * kk
            b += l[5] * kk
        return min(1.0, r), min(1.0, g), min(1.0, b)
    return light, lights


# --------------------------------------------------------------------------------------------------- efectos
def hsh(i, salt):
    h = (i * 374761393 + salt * 668265263) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def frac(x):
    return x - math.floor(x)


class Fx:
    """Lo que suma luz (como CinematicFx): halos, haz, polvo, luciérnagas, bruma, espuma. Vértices con la textura del
    brillo (u, v) y color."""

    def __init__(self, eye, right, up):
        self.v = []
        self.eye, self.right, self.up = np.array(eye), np.array(right), np.array(up)

    def quad(self, ps, uvs, col):
        for q in (0, 1, 2, 0, 2, 3):
            self.v.append((*ps[q], *uvs[q], *col))

    def glow(self, x, y, z, size, r, g, b, a):
        if a <= 0.003 or size <= 0:
            return
        R, U = self.right * size, self.up * size
        c = np.array([x, y, z])
        self.quad([c - R - U, c + R - U, c + R + U, c - R + U], [(0, 1), (1, 1), (1, 0), (0, 0)], (r, g, b, a))

    def band(self, a, b, wa, wb, r, g, bl, aa, ab, u0=0.0, u1=1.0):
        a, b = np.array(a, float), np.array(b, float)
        ax = b - a
        m = (a + b) * 0.5 - self.eye
        s = np.cross(ax, m)
        L = np.linalg.norm(s)
        if L < 1e-5:
            return
        s /= L
        ps = [a - s * wa, a + s * wa, b + s * wb, b - s * wb]
        for q, uv, al in zip((0, 1, 2, 0, 2, 3), ((u0, .5), (u1, .5), (u1, .5), (u0, .5), (u1, .5), (u0, .5)),
                             (aa, aa, ab, aa, ab, ab)):
            self.v.append((*ps[q], *uv, r, g, bl, al))

    def flat(self, cx, y, cz, rad, r, g, b, a):
        self.quad([(cx - rad, y, cz - rad), (cx - rad, y, cz + rad), (cx + rad, y, cz + rad), (cx + rad, y, cz - rad)],
                  [(0, 0), (0, 1), (1, 1), (1, 0)], (r, g, b, a))


def effects(fx, t, lights, power=1.0):
    """Lo mismo que CinematicFx (lightHalos, sunShafts, motes, waterfallLights, fireflies, mist) a la vez."""
    sun = np.array(S.SUN, float)
    c = S.CRATE
    an = S.anchors()
    # halos de las luces mágicas
    for i, l in enumerate(lights, 1):
        d = np.array(l[:3]) - fx.eye
        if d.dot(d) > 70 * 70:
            continue
        flick = 0.82 + 0.18 * math.sin(t * (0.07 + hsh(i, 171) * 0.08) + i)
        s = l[6]
        fx.glow(l[0], l[1], l[2], 0.75 * s + 0.2, l[3], l[4], l[5], 0.32 * flick * min(1.0, s + 0.3))
        fx.glow(l[0], l[1], l[2], 0.18 * s + 0.08, (l[3] + 1) / 2, (l[4] + 1) / 2, (l[5] + 1) / 2, 0.45 * flick)
    # el haz (CinematicFx.sunShafts): halo ancho, la columna con el borde marcado, el núcleo claro y vetas
    r, g, b = 1.0, 0.93, 0.70
    base = np.array([c[0], c[1] - 0.3, c[2]])
    top = base + sun * 26.0
    br = 0.88 + 0.12 * math.sin(t * 0.05)
    mid = base + sun * 9.0
    fx.band(base, mid, 3.4, 3.4, r, g, b, 0.02 * power * br, 0.04 * power * br)
    fx.band(mid, top, 3.4, 3.0, r, g, b, 0.04 * power * br, 0.07 * power * br)
    fx.band(base, mid, 2.2, 2.3, r, g, b, 0.04 * power * br, 0.07 * power * br, 0.16, 0.84)
    fx.band(mid, top, 2.3, 2.3, r, g, b, 0.07 * power * br, 0.12 * power * br, 0.16, 0.84)
    fx.band(mid, top, 1.0, 1.2, (r + 1) / 2, (g + 1) / 2, (b + 1) / 2, 0.04 * power, 0.12 * power, 0.12, 0.88)
    # el sol que entra por el hueco: un foco blanco cálido donde el haz sale de las copas
    src = np.array(an['sunspot'][:3])
    fx.glow(*src, 4.0, 1.0, 0.97, 0.85, 0.5 * power)
    fx.glow(*src, 1.8, 1.0, 1.0, 0.94, 0.95 * power)
    fx.glow(*src, 0.8, 1.0, 1.0, 1.0, 1.0 * power)
    for i in range(9):
        # rayos que bajan en abanico desde el foco, sobre la cascada, la escalera y el estanque
        ang = (i - 4) * 0.13 + 0.05 * math.sin(t * 0.01 + i)
        d = np.array([math.sin(ang) * 0.9, -1.0, 0.55 + 0.25 * hsh(i, 29)])
        d /= np.linalg.norm(d)
        L = 9.0 + hsh(i, 31) * 6.0
        life = 0.7 + 0.3 * math.sin(t * (0.02 + hsh(i, 33) * 0.02) + i * 1.7)
        fx.band(src, src + d * L, 0.12 + hsh(i, 35) * 0.2, 0.45 + hsh(i, 37) * 0.6, 1.0, 0.96, 0.80, 0.34 * life * power, 0.0)
    p1 = np.cross(sun, [0, 1, 0])
    p1 /= np.linalg.norm(p1)
    p2 = np.cross(sun, p1)
    p2 /= np.linalg.norm(p2)
    for i in range(9):
        ang = hsh(i, 3) * 6.2832 + t * 0.004 * (hsh(i, 5) - 0.5)
        rad = 0.3 + hsh(i, 7) * 1.7
        ox, oy = math.cos(ang) * rad, math.sin(ang) * rad
        b0 = base + p1 * ox + p2 * oy
        b1 = b0 + sun * 24.0
        a = (0.06 + 0.05 * math.sin(t * (0.03 + hsh(i, 9) * 0.04) + i * 2.1)) * power
        fx.band(b0, b1, 0.10 + hsh(i, 11) * 0.15, 0.2 + hsh(i, 13) * 0.3, 1, 0.97, 0.88, a * 0.5, a)
    # los rayos que bajan del hueco sobre la escalera y la cascada (y alguno hacia el estanque)
    op = an['opening']
    for i in range(7):
        x = op[0] + (hsh(i, 15) - 0.5) * 8.0
        z = op[2] + (hsh(i, 17) - 0.5) * op[3] * 1.6
        tilt = (hsh(i, 19) - 0.5) * 0.35
        d = np.array([tilt, -0.93, 0.36])
        d /= np.linalg.norm(d)
        b0 = np.array([x, op[1] + 1.0, z])
        life = 0.65 + 0.35 * math.sin(t * (0.02 + hsh(i, 21) * 0.02) + i * 1.9)
        a = 0.13 * life * power
        fx.band(b0, b0 + d * (11.0 + hsh(i, 23) * 4.0), 0.35 + hsh(i, 25) * 0.6, 0.9 + hsh(i, 27) * 0.9, 1, 0.95, 0.78, a, 0.0)
    # donde cae el haz: el charco de luz en el altar y en el agua
    fx.flat(c[0], c[1] + 0.01, c[2], 2.6, r, g, b, 0.36 * power)
    fx.flat(c[0], 0.895, c[2] - 1.0, 4.8, r, g, b, 0.16 * power)
    # espuma al pie de las cascadas de los lados (CinematicFx.waterfallLights)
    for k, sp in enumerate(S.spring_points()):
        for i in range(8):
            ph = frac(t * 0.08 + hsh(i + k * 13, 251))
            ang = hsh(i + k * 13, 253) * 6.2832
            fx.glow(sp[0] + math.cos(ang) * 0.5 * ph, sp[1] + 0.08 + ph * 0.3, sp[2] + 0.4 + math.sin(ang) * 0.4 * ph,
                    0.25 + ph * 0.3, 0.80, 1.0, 1.0, 0.24 * (1 - ph))
    f = an['fall']
    fz = an['fall_z'][0] + 0.3
    for i in range(14):
        ph = frac(t * 0.07 + hsh(i, 241))
        x = f[0] + (f[1] - f[0]) * hsh(i, 243)
        fx.glow(x, f[3] + 0.1 + ph * 0.5, fz + 0.2 + ph * 0.6, 0.3 + ph * 0.4, 0.80, 1.0, 1.0, 0.22 * (1 - ph))
    # el polvo del haz
    for i in range(130):
        along = 0.3 + hsh(i, 31) * 13.0
        ang = hsh(i, 33) * 6.2832
        rad = math.sqrt(hsh(i, 37)) * 2.4
        p = np.array(c) + sun * along + p1 * math.cos(ang) * rad + p2 * math.sin(ang) * rad
        tw = 0.5 + 0.5 * math.sin(t * (0.15 + hsh(i, 43) * 0.2) + i * 1.7)
        fx.glow(*p, 0.035 + hsh(i, 45) * 0.03, 1.0, 0.95, 0.78, (0.25 + 0.55 * tw) * power)
    # luciérnagas (chispas mágicas)
    for i in range(110):
        period = 60.0 + hsh(i, 191) * 90.0
        ph = frac(t / period + hsh(i, 193))
        on = max(0.0, math.sin(ph * math.pi * 2)) ** 3
        if on < 0.02:
            continue
        x = -11.0 + hsh(i, 195) * 22.0
        z = -16.0 + hsh(i, 197) * 26.0
        y = 0.6 + hsh(i, 199) * 6.0
        fx.glow(x, y, z, 0.13, 0.85, 1.0, 0.6, 0.40 * on)
        fx.glow(x, y, z, 0.035, 1, 1, 0.85, 0.85 * on)
    # la bruma baja sobre el agua
    for i in range(26):
        x = -9.0 + hsh(i, 201) * 18.0
        z = -14.0 + hsh(i, 205) * 24.0
        y = 0.4 + hsh(i, 207) * 0.6
        a = 0.03 * (0.6 + 0.4 * math.sin(t * 0.02 + i))
        fx.glow(x, y, z, 2.6 + hsh(i, 209) * 2.0, 0.55, 0.95, 0.95, a)


# ------------------------------------------------------------------------------------------------- la crate
def crate_quads(assets, me, light):
    """La crate de estilo cine_mythical (base y tapa) en su sitio, a la altura de la cinemática (~1,1 de alto)."""
    out = []
    for m in ('fscrates:block/cine_mythical', 'fscrates:block/cine_mythical_lid'):
        mod = assets.model(m)
        for el in mod['elements'] or []:
            f, t = el['from'], el['to']
            for d, face in el['faces'].items():
                ref = resolve(mod['textures'], face.get('texture'), mod['ns'])
                if ref is None:
                    continue
                ti = me.tex(ref)
                uv = face.get('uv') or default_uv(d, f, t)
                uvs = [(uv[0], uv[1]), (uv[2], uv[1]), (uv[2], uv[3]), (uv[0], uv[3])]
                k = (face.get('rotation', 0) // 90) % 4
                uvs = uvs[k:] + uvs[:k]
                ps = [rot_elem(p, el.get('rotation')) for p in face_corners(d, f, t)]
                out.append((ps, uvs, ti, SHADE[d]))
    allp = np.array([p for q in out for p in q[0]])
    lo, hi = allp.min(0), allp.max(0)
    s = 1.1 / max(1e-3, hi[1] - lo[1])
    cx, cz = (lo[0] + hi[0]) / 2, (lo[2] + hi[2]) / 2
    verts = []
    for ps, uvs, ti, shade in out:
        vs = []
        for p, (u, v) in zip(ps, uvs):
            wx = S.CRATE[0] + (p[0] - cx) * s
            wy = S.CRATE[1] + (p[1] - lo[1]) * s
            wz = S.CRATE[2] + (p[2] - cz) * s
            vs.append((wx, wy, wz, ti, u, v, shade * CRATE_TINT[0], shade * CRATE_TINT[1], shade * CRATE_TINT[2], 1.0))
        for q in (0, 1, 2, 0, 2, 3):
            verts.append(vs[q])
    return verts


# ------------------------------------------------------------------------------------------------------ dibujo
VS = """
#version 330
uniform mat4 mvp; uniform vec3 eye;
in vec3 pos; in vec3 uvl; in vec4 col;
out vec2 tuv; out vec4 c; out float dist;
uniform vec2 tiles;
void main(){
  gl_Position = mvp*vec4(pos,1.0);
  float t = uvl.x; vec2 cell = vec2(mod(t, tiles.x), floor(t / tiles.x));
  tuv = (cell * 16.0 + clamp(uvl.yz, 0.02, 15.98)) / (tiles * 16.0);
  c = col; dist = length(pos - eye);
}
"""
FS = """
#version 330
uniform sampler2D atlas; uniform vec3 fogc; uniform vec2 fogr; uniform float cutoff;
in vec2 tuv; in vec4 c; in float dist; out vec4 o;
void main(){
  vec4 t = texture(atlas, tuv) * c;
  if (t.a < cutoff) discard;
  float f = clamp((dist - fogr.x) / (fogr.y - fogr.x), 0.0, 1.0);
  o = vec4(mix(t.rgb, fogc, f), t.a);
}
"""
FX_VS = """
#version 330
uniform mat4 mvp; in vec3 pos; in vec2 uv; in vec4 col; out vec2 tuv; out vec4 c;
void main(){ gl_Position = mvp*vec4(pos,1.0); tuv = uv; c = col; }
"""
FX_FS = """
#version 330
uniform sampler2D glowtex; in vec2 tuv; in vec4 c; out vec4 o;
void main(){ o = texture(glowtex, tuv) * c; }
"""


def look_at(eye, at):
    f = np.array(at, float) - eye
    f /= np.linalg.norm(f)
    r = np.cross(f, [0, 1, 0])
    r /= np.linalg.norm(r)
    u = np.cross(r, f)
    m = np.identity(4)
    m[0, :3], m[1, :3], m[2, :3] = r, u, -f
    m[:3, 3] = -m[:3, :3] @ eye
    return m, r, u, f


def perspective(fov, aspect, n, f):
    t = 1 / math.tan(math.radians(fov) / 2)
    m = np.zeros((4, 4))
    m[0, 0], m[1, 1] = t / aspect, t
    m[2, 2], m[2, 3] = (f + n) / (n - f), 2 * f * n / (n - f)
    m[3, 2] = -1
    return m


def main():
    out = sys.argv[1]
    ticks = [float(t) for t in sys.argv[2:]] or [0, 76, 140, 258]
    os.makedirs(out, exist_ok=True)
    import moderngl
    assets = Assets()
    sc = S.build()
    light, lights = make_light(sc)
    me, solid, water = build_mesh(sc, assets, light)
    crate = crate_quads(assets, me, light)
    n = len(me.tex_list)
    cols = 32
    rows = (n + cols - 1) // cols
    atlas = Image.new('RGBA', (cols * 16, rows * 16))
    for i, im in enumerate(me.tex_list):
        atlas.paste(im, ((i % cols) * 16, (i // cols) * 16))
    W, H = 1280, 720
    ctx = moderngl.create_standalone_context()
    tex = ctx.texture(atlas.size, 4, atlas.tobytes())
    tex.filter = (moderngl.NEAREST, moderngl.NEAREST)
    glow_im = Image.open(os.path.join(RES, 'fscrates', 'textures', 'gui', 'glow.png')).convert('RGBA')
    gtex = ctx.texture(glow_im.size, 4, glow_im.tobytes())
    gtex.filter = (moderngl.LINEAR, moderngl.LINEAR)
    prog = ctx.program(vertex_shader=VS, fragment_shader=FS)
    fxp = ctx.program(vertex_shader=FX_VS, fragment_shader=FX_FS)
    prog['tiles'].value = (cols, rows)
    prog['fogc'].value = FOG
    prog['fogr'].value = (FOG_START, FOG_END)

    def vao(verts):
        if not verts:
            return None
        d = np.array(verts, 'f4')
        return ctx.vertex_array(prog, [(ctx.buffer(d.tobytes()), '3f 3f 4f', 'pos', 'uvl', 'col')])
    v_solid, v_crate = vao(solid), vao(crate)
    wprog = ctx.program(vertex_shader=open(os.path.join(SHADERS, 'crate_water.vsh')).read(),
                        fragment_shader=open(os.path.join(SHADERS, 'crate_water.fsh')).read())
    # la textura del agua del juego (animada: en el juego la anima el atlas de bloques), para el shader del agua
    strips = {n: np.asarray(Image.open(io.BytesIO(assets.z.read(f'assets/minecraft/textures/block/{n}.png'))).convert('RGBA'))
              for n in ('water_still', 'water_flow')}
    wtex = ctx.texture((48, 32), 4)
    wtex.filter = (moderngl.NEAREST, moderngl.NEAREST)

    def water_frame(tick):
        st, fl = strips['water_still'], strips['water_flow']
        a = np.zeros((32, 48, 4), np.uint8)
        k = int(tick // 2) % (st.shape[0] // 16)
        a[0:16, 0:16] = st[k * 16:(k + 1) * 16]
        k = int(tick) % (fl.shape[0] // 32)
        a[0:32, 16:48] = fl[k * 32:(k + 1) * 32]
        wtex.write(a.tobytes())
    v_water = None
    if water:
        wd = np.array(water, 'f4')
        v_water = ctx.vertex_array(wprog, [(ctx.buffer(wd.tobytes()), '3f 4f 2f', 'Position', 'Color', 'UV0')])
    quadsrc = open(os.path.join(SHADERS, 'crate_quad.vsh')).read()
    sky = ctx.program(vertex_shader=quadsrc, fragment_shader=open(os.path.join(SHADERS, 'crate_sky.fsh')).read())
    bloom = ctx.program(vertex_shader=quadsrc, fragment_shader=open(os.path.join(SHADERS, 'crate_bloom.fsh')).read())
    post = ctx.program(vertex_shader=quadsrc, fragment_shader=open(os.path.join(SHADERS, 'crate_post.fsh')).read())
    qb = ctx.buffer(np.array([-1, -1, 0, 1, -1, 0, 1, 1, 0, -1, -1, 0, 1, 1, 0, -1, 1, 0], 'f4').tobytes())
    qv = {p: ctx.vertex_array(p, [(qb, '3f', 'Position')]) for p in (sky, bloom, post)}
    scene_t = ctx.texture((W, H), 4)
    scene_t.filter = (moderngl.LINEAR, moderngl.LINEAR)
    depth_t = ctx.depth_texture((W, H))
    depth_t.compare_func = ''
    depth_t.filter = (moderngl.NEAREST, moderngl.NEAREST)
    scene = ctx.framebuffer(color_attachments=[scene_t], depth_attachment=depth_t)
    at_, bt_ = ctx.texture((W // 4, H // 4), 4), ctx.texture((W // 4, H // 4), 4)
    ct_, dt_ = ctx.texture((W // 4, H // 4), 4), ctx.texture((W // 4, H // 4), 4)
    for tt in (at_, bt_, ct_, dt_):
        tt.filter = (moderngl.LINEAR, moderngl.LINEAR)
        tt.repeat_x = tt.repeat_y = False
    fa, fb = ctx.framebuffer([at_]), ctx.framebuffer([bt_])
    fc, fd = ctx.framebuffer([ct_]), ctx.framebuffer([dt_])
    fin_t = ctx.texture((W, H), 4)
    fin = ctx.framebuffer([fin_t])
    for tick in ticks:
        eye, at, fov = S.camera_at(tick)
        view, r, u, f = look_at(np.array(eye, float), at)
        proj = perspective(fov, W / H, 0.05, 220)
        mvp = (proj @ view).astype('f4').T.tobytes()
        scene.use()
        ctx.clear(0, 0, 0, 1)
        ctx.disable(moderngl.DEPTH_TEST | moderngl.BLEND)
        tanY = math.tan(math.radians(fov) / 2)
        sky['CamRight'].value, sky['CamUp'].value, sky['CamFwd'].value = tuple(r), tuple(u), tuple(f)
        sky['TanHalf'].value = (tanY * W / H, tanY)
        sky['SunDir'].value = tuple(S.SUN)
        sky['Time'].value = tick
        qv[sky].render()
        ctx.enable(moderngl.DEPTH_TEST)
        prog['mvp'].write(mvp)
        prog['eye'].value = tuple(eye)
        tex.use(0)
        prog['atlas'].value = 0
        prog['cutoff'].value = 0.1
        v_solid.render()
        v_crate.render()
        ctx.enable(moderngl.BLEND)
        ctx.blend_func = moderngl.SRC_ALPHA, moderngl.ONE_MINUS_SRC_ALPHA
        if v_water and os.environ.get('SIN_AGUA') != '1':
            wprog['ModelViewMat'].write(view.astype('f4').T.tobytes())
            wprog['ProjMat'].write(proj.astype('f4').T.tobytes())
            wprog['CamPos'].value = tuple(eye)
            wprog['LightDir'].value = tuple(S.SUN)
            wprog['BeamPos'].value = tuple(S.CRATE)
            wprog['Time'].value = tick
            wprog['FogStart'].value, wprog['FogEnd'].value = FOG_START, FOG_END
            wprog['FogColor'].value = (*FOG, 1.0)
            water_frame(tick)
            wtex.use(2)
            wprog['Sampler0'].value = 2
            wprog['StillUV'].value = (0.0, 0.0, 16 / 48, 0.5)
            wprog['FlowUV'].value = (16 / 48, 0.0, 1.0, 1.0)
            v_water.render()
        fx = Fx(eye, r, u)
        if os.environ.get('SIN_FX') != '1':
            effects(fx, tick, lights)
        if fx.v:
            d = np.array(fx.v, 'f4')
            va = ctx.vertex_array(fxp, [(ctx.buffer(d.tobytes()), '3f 2f 4f', 'pos', 'uv', 'col')])
            ctx.blend_func = moderngl.SRC_ALPHA, moderngl.ONE
            scene.depth_mask = False      # (en el juego, GLOW_ADD no escribe profundidad; si la escribe, el haz se pisa)
            gtex.use(0)
            fxp['glowtex'].value = 0
            fxp['mvp'].write(mvp)
            va.render()
        scene.depth_mask = True
        ctx.disable(moderngl.DEPTH_TEST | moderngl.BLEND)

        def bpass(src, dst, mode, itx, ity, dx, dy):
            dst.use()
            src.use(0)
            bloom['InSampler'].value = 0
            bloom['InTexel'].value = (itx, ity)
            bloom['Dir'].value = (dx, dy)
            bloom['Mode'].value = float(mode)
            bloom['Threshold'].value = THRESHOLD
            qv[bloom].render()
        tw, th = 4.0 / W, 4.0 / H
        bpass(scene_t, fa, 0, 1.0 / W, 1.0 / H, 0, 0)
        bpass(at_, fb, 1, 0, 0, tw * 1.5, 0)
        bpass(bt_, fa, 1, 0, 0, 0, th * 1.5)
        bpass(at_, fb, 1, 0, 0, tw * 3.5, 0)
        bpass(bt_, fa, 1, 0, 0, 0, th * 3.5)
        bpass(scene_t, fc, 2, 1.0 / W, 1.0 / H, 0, 0)
        bpass(ct_, fd, 1, 0, 0, tw * 1.5, 0)
        bpass(dt_, fc, 1, 0, 0, 0, th * 1.5)
        bpass(ct_, fd, 1, 0, 0, tw * 2.5, 0)
        bpass(dt_, fc, 1, 0, 0, 0, th * 2.5)
        sp = proj @ view @ np.array([*(np.array(eye) + np.array(S.SUN) * 1000), 1.0])
        su, sv, vis = 0.5, 0.5, 0.0
        if sp[3] > 1e-4:
            su, sv = sp[0] / sp[3] * 0.5 + 0.5, sp[1] / sp[3] * 0.5 + 0.5
            vis = max(0.0, min(1.0, 1.3 - 0.42 * math.hypot(su - 0.5, sv - 0.5)))
        fin.use()
        scene_t.use(0)
        at_.use(1)
        ct_.use(2)
        depth_t.use(3)
        post['SceneSampler'].value = 0
        post['BloomSampler'].value = 1
        post['BlurSampler'].value = 2
        post['DepthSampler'].value = 3
        post['SunPos'].value = (su, sv)
        post['SunVis'].value = vis
        post['BloomStrength'].value = BLOOM
        post['RayStrength'].value = RAYS
        post['Exposure'].value = EXPOSURE
        post['Clip'].value = (0.05, 220.0)
        post['Focus'].value = (float(np.linalg.norm(np.array(eye) - np.array([S.CRATE[0], S.CRATE[1] + 0.6, S.CRATE[2]]))), 16.0)
        qv[post].render()
        img = Image.frombytes('RGBA', (W, H), fin_t.read()).transpose(Image.FLIP_TOP_BOTTOM).convert('RGB')
        bar = int(H * BARS)
        px = img.load()
        for yy in list(range(bar)) + list(range(H - bar, H)):
            for xx in range(W):
                px[xx, yy] = (0, 0, 0)
        name = os.path.join(out, f'render_{int(tick):03d}.png')
        img.save(name)
        arr = np.asarray(img).astype(float)
        print(f'tick {tick:g}: brillo medio {arr[bar:H - bar].mean():.0f}, {name}')


if __name__ == '__main__':
    main()
