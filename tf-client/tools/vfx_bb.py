"""Lectura de modelos de Blockbench (.bbmodel, formato libre de ModelEngine) para los VFX del TF Client.

Convierte un .bbmodel al formato que dibuja el mod (assets/tfclient/vfx/models/<id>.json):
  tex    texturas: ruta, fotogramas (texturas animadas), tiempo por fotograma, emisiva
  bones  huesos: padre, pivote, giro en reposo, si es cabeza (h_) o cabeza de jugador (phead), si empieza oculto,
         y sus caras ya horneadas (posición relativa al pivote del hueso, UV de 0 a 1, normal y textura)
  anims  animaciones: duración, bucle y pistas por hueso (giro, posición y escala con su interpolación)

Sigue exactamente lo que enseña Blockbench (lo que vio quien hizo el modelo):
  - giros en orden ZYX (Rz·Ry·Rx), en grados, sobre el origen del cubo o el pivote del hueso
  - las animaciones suman al giro (-x, -y, +z) y a la posición (-x, +y, +z); la escala multiplica
  - caras con el mismo reparto de UV que Minecraft (y su giro de 90°)
  - catmullrom, lineal y escalón con la misma regla que el editor
ModelEngine no exporta los cubos sueltos fuera de huesos ni los marcados sin exportar, y los huesos hitbox,
shadow y mount no se ven: aquí tampoco.
"""
import base64
import hashlib
import io
import json
import math
import os

from PIL import Image

SPECIAL_BONES = {'hitbox', 'shadow', 'mount'}
INTERP = {'linear': 0, 'catmullrom': 1, 'step': 2, 'bezier': 0}

# Esquinas de cada cara (arriba-izquierda, abajo-izquierda, abajo-derecha, arriba-derecha vista desde fuera),
# como índices (0 = from, 1 = to) en x, y, z. Mismo orden que FaceBakery de Minecraft.
FACE_CORNERS = {
    'north': [(1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)],
    'south': [(0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)],
    'west': [(0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)],
    'east': [(1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)],
    'up': [(0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)],
    'down': [(0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)],
}
FACE_NORMAL = {'north': (0, 0, -1), 'south': (0, 0, 1), 'west': (-1, 0, 0), 'east': (1, 0, 0), 'up': (0, 1, 0),
               'down': (0, -1, 0)}
# Cabeza de una skin de 64×64 (u1, v1, u2, v2 en píxeles), con las mismas orientaciones que las caras de arriba
HEAD_UV = {'north': (8, 8, 16, 16), 'east': (0, 8, 8, 16), 'south': (24, 8, 32, 16), 'west': (16, 8, 24, 16),
           'up': (16, 8, 8, 0), 'down': (24, 0, 16, 8)}
# Ejes que forman cada cara (para saber si tiene superficie)
FACE_AXES = {'north': (0, 1), 'south': (0, 1), 'west': (2, 1), 'east': (2, 1), 'up': (0, 2), 'down': (0, 2)}


def num(v):
    if isinstance(v, (int, float)):
        return float(v)
    try:
        return float(str(v).strip() or 0)
    except ValueError:
        return 0.0


def rot_matrix(deg):
    """Matriz de giro de Blockbench: euler ZYX → R = Rz·Ry·Rx."""
    x, y, z = (math.radians(num(a)) for a in deg)
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return mul(mul(rz, ry), rx)


def mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def apply(m, v):
    return [m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2],
            m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
            m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2]]


def strip_prefix(name):
    """Id del hueso como lo nombra ModelEngine en las mecánicas (sin el prefijo de comportamiento h_ / hi_)."""
    for p in ('hi_', 'h_'):
        if name.startswith(p):
            return name[len(p):]
    return name


class TextureStore:
    """Guarda las texturas sin repetir (la misma imagen en varios modelos se escribe una vez)."""

    def __init__(self, out_dir, ref_prefix):
        self.out_dir = out_dir
        self.ref_prefix = ref_prefix
        self.written = {}
        os.makedirs(out_dir, exist_ok=True)

    def put(self, img):
        buf = io.BytesIO()
        img.save(buf, 'PNG', optimize=True)
        data = buf.getvalue()
        key = hashlib.sha1(data).hexdigest()[:14]
        if key not in self.written:
            with open(os.path.join(self.out_dir, key + '.png'), 'wb') as f:
                f.write(data)
            self.written[key] = len(data)
        return f'{self.ref_prefix}{key}.png'


def load_texture(tex, bb_path):
    src = tex.get('source') or ''
    if src.startswith('data:image'):
        return Image.open(io.BytesIO(base64.b64decode(src.split(',', 1)[1]))).convert('RGBA')
    for cand in (tex.get('path'), os.path.join(os.path.dirname(bb_path), tex.get('name', ''))):
        if cand and os.path.exists(cand):
            return Image.open(cand).convert('RGBA')
    return None


def convert(bb_path, store, problems):
    """Devuelve el modelo convertido (dict) o None si no sirve."""
    d = json.load(open(bb_path, encoding='utf-8'))
    name = os.path.basename(bb_path)[:-len('.bbmodel')]
    res = d.get('resolution') or {}
    res_w, res_h = num(res.get('width', 16)) or 16, num(res.get('height', 16)) or 16

    # Texturas (solo las que usa alguna cara)
    raw_tex = d.get('textures', [])
    used = set()
    for el in d.get('elements', []):
        for face in (el.get('faces') or {}).values():
            if isinstance(face.get('texture'), int):
                used.add(face['texture'])
    tex_out, tex_map, tex_uv = [], {}, {}
    for i, t in enumerate(raw_tex):
        if i not in used:
            continue
        img = load_texture(t, bb_path)
        if img is None:
            problems.append(f'{name}: falta la imagen de la textura {t.get("name")}')
            continue
        uw = num(t.get('uv_width')) or res_w
        uh = num(t.get('uv_height')) or res_h
        frame_h = img.width * uh / uw
        frames = max(1, round(img.height / frame_h)) if frame_h > 0 else 1
        if abs(frames * frame_h - img.height) > 0.5:
            frames = 1  # no es una tira de fotogramas: la imagen entera
        tex_map[i] = len(tex_out)
        tex_uv[i] = (uw, uh)
        tname = (t.get('name') or '').lower()
        tex_out.append({
            'path': store.put(img),
            'frames': frames,
            'ft': max(1, int(num(t.get('frame_time', 1)) or 1)),
            'e': tname.endswith('_e') or tname.endswith('_e.png'),
            'body': tname.startswith('body_grey') or tname == 'body',
        })

    elements = {e['uuid']: e for e in d.get('elements', [])}
    bones = []
    uuid_to_bone = {}

    def bake_cube(el, pivot, phead, quads):
        if el.get('export') is False or el.get('visibility') is False:
            return
        if el.get('type', 'cube') != 'cube':
            problems.append(f'{name}: elemento {el.get("type")} no soportado ({el.get("name")})')
            return
        inf = num(el.get('inflate', 0))
        f = [num(v) - inf for v in el['from']]
        t = [num(v) + inf for v in el['to']]
        lo = [min(f[i], t[i]) for i in range(3)]
        hi = [max(f[i], t[i]) for i in range(3)]
        origin = [num(v) for v in el.get('origin', [0, 0, 0])]
        rm = rot_matrix(el.get('rotation', [0, 0, 0]))
        light = num(el.get('light_emission', 0))

        def corners(dname, ff, tt):
            out = []
            for c in FACE_CORNERS[dname]:
                p = [tt[i] if c[i] else ff[i] for i in range(3)]
                p = apply(rm, [p[i] - origin[i] for i in range(3)])
                out.append([p[i] + origin[i] - pivot[i] for i in range(3)])
            return out

        if phead:
            # Cabeza de jugador (ModelEngine pone ahí la cabeza con la skin): caras con el reparto de una skin
            # (-1 = cabeza, -2 = capa del sombrero, un poco más grande). Sin skin se usan las caras normales.
            for layer, du, grow in ((-1, 0, 0.0), (-2, 32, 0.5)):
                ff = [f[i] - grow for i in range(3)]
                tt = [t[i] + grow for i in range(3)]
                for dname, (u1, v1, u2, v2) in HEAD_UV.items():
                    verts = []
                    cuv = [(u1 + du, v1), (u1 + du, v2), (u2 + du, v2), (u2 + du, v1)]
                    for k, p in enumerate(corners(dname, ff, tt)):
                        verts += [round(p[0], 4), round(p[1], 4), round(p[2], 4), cuv[k][0] / 64, cuv[k][1] / 64]
                    n = apply(rm, FACE_NORMAL[dname])
                    quads.append([layer, round(n[0], 4), round(n[1], 4), round(n[2], 4)] + verts)
        for dname, face in (el.get('faces') or {}).items():
            if dname not in FACE_CORNERS:
                continue
            ti = face.get('texture')
            uv = face.get('uv')
            if not isinstance(ti, int) or ti not in tex_map or not uv or len(uv) < 4:
                continue
            a, b = FACE_AXES[dname]
            if hi[a] - lo[a] < 1e-6 or hi[b] - lo[b] < 1e-6:
                continue  # cara sin superficie (plano de grosor cero visto de lado)
            uw, uh = tex_uv[ti]
            u1, v1, u2, v2 = (num(x) for x in uv[:4])
            if abs(u2 - u1) < 1e-9 and abs(v2 - v1) < 1e-9:
                continue  # UV de un punto: ModelEngine tampoco la dibuja
            corners_uv = [(u1, v1), (u1, v2), (u2, v2), (u2, v1)]
            r = int(num(face.get('rotation', 0))) // 90 % 4
            verts = []
            for k, p in enumerate(corners(dname, f, t)):
                cu, cv = corners_uv[(k + r) % 4]
                verts += [round(p[0], 4), round(p[1], 4), round(p[2], 4), round(cu / uw, 6), round(cv / uh, 6)]
            n = apply(rm, FACE_NORMAL[dname])
            quads.append([tex_map[ti], round(n[0], 4), round(n[1], 4), round(n[2], 4)] + verts
                         + ([1] if light > 0 else []))

    def walk(children, parent):
        for c in children:
            if not isinstance(c, dict):
                continue  # cubos sueltos en la raíz: ModelEngine no los usa
            bname = c.get('name', 'bone')
            if bname.lower() in SPECIAL_BONES or c.get('export') is False:
                continue
            idx = len(bones)
            pivot = [num(v) for v in c.get('origin', [0, 0, 0])]
            sid = strip_prefix(bname)
            phead = sid.lower().startswith('phead')
            bone = {
                'id': sid,
                'parent': parent,
                'pivot': [round(v, 4) for v in pivot],
                'rot': [round(num(v), 4) for v in (c.get('rotation') or [0, 0, 0])],
                'head': bname.startswith('h_') or bname.startswith('hi_'),
                'phead': phead,
                'hidden': c.get('visibility') is False,
                'shade': c.get('shade', True) is not False,
                'quads': [],
            }
            bones.append(bone)
            uuid_to_bone[c['uuid']] = idx
            for ch in c.get('children', []):
                if isinstance(ch, str) and ch in elements:
                    bake_cube(elements[ch], pivot, phead, bone['quads'])
            walk(c.get('children', []), idx)

    walk(d.get('outliner', []), -1)
    if not bones:
        problems.append(f'{name}: sin huesos')
        return None

    anims = {}
    for a in d.get('animations', []):
        tracks = []
        for uid, an in (a.get('animators') or {}).items():
            if an.get('type', 'bone') != 'bone' or uid not in uuid_to_bone:
                continue
            tr = {'b': uuid_to_bone[uid]}
            for ch, key in (('rotation', 'r'), ('position', 'p'), ('scale', 's')):
                kfs = [k for k in an.get('keyframes', []) if k.get('channel') == ch and k.get('data_points')]
                if not kfs:
                    continue
                kfs.sort(key=lambda k: num(k.get('time', 0)))
                out = []
                for k in kfs:
                    dp = k['data_points'][0]
                    x, y, z = num(dp.get('x', 0)), num(dp.get('y', 0)), num(dp.get('z', 0))
                    if ch == 'rotation':
                        x, y = -x, -y
                    elif ch == 'position':
                        x = -x
                    out.append([round(num(k.get('time', 0)), 5), round(x, 4), round(y, 4), round(z, 4),
                                INTERP.get(k.get('interpolation', 'linear'), 0)])
                tr[key] = out
            if len(tr) > 1:
                tracks.append(tr)
        anims[a.get('name', 'anim')] = {
            'len': round(num(a.get('length', 0)), 5),
            'loop': a.get('loop', 'once') if a.get('loop') in ('once', 'hold', 'loop') else 'once',
            'tracks': tracks,
        }
    return {'name': name, 'tex': tex_out, 'bones': bones, 'anims': anims}


# ---------------------------------------------------------------------------------------------------------------
# Pose (la misma cuenta que hace el mod en Java; aquí sirve para las miniaturas y para comprobar los modelos)

EPS = 1 / 1200


def catmull(t, p0, p1, p2, p3):
    v0 = (p2 - p0) * 0.5
    v1 = (p3 - p1) * 0.5
    t2 = t * t
    t3 = t * t2
    return (2 * p1 - 2 * p2 + v0 + v1) * t3 + (-3 * p1 + 3 * p2 - 2 * v0 - v1) * t2 + v0 * t + p1


def sample(kfs, time):
    """Valor de una pista en un instante, con la regla de Blockbench (timeline_animators.js)."""
    before = after = None
    bi = -1
    for i, k in enumerate(kfs):
        if k[0] < time:
            if before is None or k[0] > before[0]:
                before, bi = k, i
        elif after is None or k[0] < after[0]:
            after = k
    if before is not None and abs(before[0] - time) < EPS:
        return before[1:4]
    if after is not None and abs(after[0] - time) < EPS:
        return after[1:4]
    if before is not None and before[4] == 2:
        return before[1:4]
    if before is not None and after is None:
        return before[1:4]
    if after is not None and before is None:
        return after[1:4]
    alpha = (time - before[0]) / (after[0] - before[0])
    if before[4] == 1 or after[4] == 1:
        bp = kfs[bi - 1] if bi - 1 >= 0 else before
        ap = kfs[bi + 2] if bi + 2 < len(kfs) else after
        return [catmull(alpha, bp[j], before[j], after[j], ap[j]) for j in (1, 2, 3)]
    return [before[j] + (after[j] - before[j]) * alpha for j in (1, 2, 3)]


def anim_time(anim, t):
    """Tiempo dentro de la animación (None = ya terminó y no cuenta)."""
    ln = anim['len']
    if anim['loop'] == 'loop' and ln > 0:
        return t % ln
    if t > ln + EPS:
        return ln if anim['loop'] == 'hold' else None
    return t


def pose(model, anim_name, t):
    """Matrices 4x4 (listas) de cada hueso en el espacio del modelo (píxeles) en el instante t (segundos)."""
    anim = model['anims'].get(anim_name) if anim_name else None
    at = anim_time(anim, t) if anim else None
    extra = {}
    if anim and at is not None:
        for tr in anim['tracks']:
            e = extra.setdefault(tr['b'], [[0, 0, 0], [0, 0, 0], [1, 1, 1]])
            if 'r' in tr:
                e[0] = sample(tr['r'], at)
            if 'p' in tr:
                e[1] = sample(tr['p'], at)
            if 's' in tr:
                e[2] = sample(tr['s'], at)
    mats = []
    for i, b in enumerate(model['bones']):
        r, p, s = extra.get(i, [[0, 0, 0], [0, 0, 0], [1, 1, 1]])
        parent_pivot = model['bones'][b['parent']]['pivot'] if b['parent'] >= 0 else [0, 0, 0]
        tr = [b['pivot'][k] - parent_pivot[k] + p[k] for k in range(3)]
        rm = rot_matrix([b['rot'][k] + r[k] for k in range(3)])
        local = [[rm[i2][j] * s[j] for j in range(3)] + [tr[i2]] for i2 in range(3)] + [[0, 0, 0, 1]]
        mats.append(local if b['parent'] < 0 else mat4(mats[b['parent']], local))
    return mats


def mat4(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]
