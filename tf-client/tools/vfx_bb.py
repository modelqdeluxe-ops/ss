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
import re
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import molang  # noqa: E402

SPECIAL_BONES = {'hitbox', 'shadow', 'mount'}
# Piezas del «muñeco» de los efectos de kill (en el juego las hace el propio mob)
BODY_PARTS = {'head', 'body', 'left_arm', 'right_arm', 'left_leg', 'right_leg'}
# Pieza del muñeco → parte del modelo humanoide de Minecraft (left_arm del muñeco está en -X, como leftArm)
RIG_PARTS = {'head': 'head', 'body': 'body', 'right_arm': 'rightArm', 'left_arm': 'leftArm', 'right_leg': 'rightLeg',
             'left_leg': 'leftLeg'}


def chain(bones, i):
    """El hueso y sus antepasados, del hueso a la raíz."""
    out = []
    while i >= 0:
        out.append(i)
        i = bones[i]['parent']
    return out


def rig_of(bones, pick):
    """Hueso raíz de una figura (el ancestro común de sus piezas, o el del cuerpo) y el hueso de cada pieza."""
    common = None
    for bi in pick.values():
        c = chain(bones, bi)
        common = c if common is None else [x for x in common if x in c]
    root = common[0] if common else pick.get('body', next(iter(pick.values())))
    rig = {'root': root}
    for part, key in RIG_PARTS.items():
        rig[key] = pick.get(part, -1)
    return rig


def make_figures(bones, actor_cubes, tex_out):
    """Las figuras del muñeco, que en el juego son el propio mob. Las piezas se agrupan por textura: con la del cuerpo
    gris es el mob tal cual; con otra, el mob hecho de ese material (óxido, piedra...) o, si comparte huesos con el
    cuerpo, una capa de ese material encima. Cada figura: su rig y sus texturas (-1 = la del mob)."""
    groups, loose = {}, []
    for part, bi, texs in actor_cubes:
        if not texs:
            loose.append((part, bi))  # pieza sin exportar: el mob sí la tiene
        for t in texs:
            groups.setdefault(t, {}).setdefault(part, bi)
    figures = {}
    for t, pick in groups.items():
        rig = rig_of(bones, pick)
        for part, bi in loose:
            if part not in pick and rig['root'] in chain(bones, bi):
                rig[RIG_PARTS[part]] = bi
        fig = figures.setdefault(tuple(sorted(rig.items())), {'rig': rig, 'tex': []})
        fig['tex'].append(-1 if tex_out[t]['body'] else t)
    out = []
    for fig in figures.values():
        fig['tex'].sort()  # el mob tal cual primero; las capas de material, después
        out.append(fig)
    out.sort(key=lambda f: f['tex'][0] != -1)
    return out


def first_frame(img, frames):
    return img if frames <= 1 else img.crop((0, 0, img.width, img.height // frames))


def mob_texture(img):
    """La textura de un material lista para ponérsela a un mob: los trozos vacíos (zonas que una skin no usa) se rellenan
    con el material del pecho, para que los mobs con otro reparto de textura no salgan con agujeros."""
    img = img.copy()
    k = max(1, img.width // 64)
    tile = img.crop((20 * k, 20 * k, 28 * k, 32 * k))
    if not tile.getbbox():
        return img
    px, tp = img.load(), tile.load()
    b = 4 * k
    for by in range(0, img.height, b):
        for bx in range(0, img.width, b):
            box = img.crop((bx, by, min(bx + b, img.width), min(by + b, img.height)))
            if box.getbbox():
                continue
            for y in range(by, min(by + b, img.height)):
                for x in range(bx, min(bx + b, img.width)):
                    px[x, y] = tp[x % tile.width, y % tile.height]
    return img


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
# Dónde está cada pieza en una skin de 64×64 (reparto moderno)
SKIN_OFFSET = {'head': (0, 0), 'body': (16, 16), 'right_arm': (40, 16), 'left_arm': (32, 48), 'right_leg': (0, 16),
               'left_leg': (16, 48)}


def box_uv(u, v, w, h, d):
    """UV de cada cara de una caja con el reparto de las skins (igual que HEAD_UV para 8×8×8 en 0,0)."""
    return {'north': (u + d, v + d, u + d + w, v + d + h), 'east': (u, v + d, u + d, v + d + h),
            'south': (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h), 'west': (u + d + w, v + d, u + 2 * d + w, v + d + h),
            'up': (u + d + w, v + d, u + d, v), 'down': (u + d + 2 * w, v, u + d + w, v + d)}


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


# Etiquetas de comportamiento de ModelEngine 4 (wiki «Bone Behaviors»). Las que piden un hueso SIN cubos solo cuentan
# si el hueso no tiene cubos (como ModelEngine desde la R4.0.7): la Gárgola tiene «l_hand» con cubos = mano izquierda.
CUBELESS_TAGS = {'seg': 'seg', 'segf': 'segf', 'tl': 'tl', 'tlf': 'tlf', 'l': 'leash', 'ih': 'item_head',
                 'ir': 'item_right', 'il': 'item_left'}
LIMB_TAGS = {'phead': 'head', 'prarm': 'right_arm', 'plarm': 'left_arm', 'pbody': 'body', 'prleg': 'right_leg',
             'plleg': 'left_leg'}
LIMB_TYPES = {'head', 'chest', 'body', 'right_arm', 'right_forearm', 'left_arm', 'left_forearm', 'right_leg',
              'right_foreleg', 'left_leg', 'left_foreleg'}
TAG_RE = re.compile(r'^([a-z]+)(\[([^\]]*)\])?_(.+)$', re.I)


def parse_params(text):
    """«yaw=60;pitch=30» → {'yaw': 60.0, 'pitch': 30.0} (los «x,y,z» de angle quedan en lista)."""
    out = {}
    for part in (text or '').split(';'):
        if '=' not in part:
            continue
        k, v = part.split('=', 1)
        k = k.strip().lower()
        if k == 'type':
            out[k] = v.strip().lower()
            continue
        vals = [num(x) for x in v.split(',')]
        out[k] = vals if len(vals) > 1 else vals[0]
    return out


def bone_tag(name, has_cubes):
    """(comportamiento, parámetros, id sin la etiqueta o None si conserva el nombre, problema o None).

    comportamiento: head, head_inherit, seat, hitbox_obb, hitbox_aabb, ghost, nametag, leash, item_head, item_right,
    item_left, seg, segf, tl, tlf, jig, ik, limb, o None (hueso normal)."""
    m = TAG_RE.match(name)
    if not m:
        return None, {}, None, None
    tag, br, params, rest = m.group(1).lower(), m.group(2), m.group(3), m.group(4)
    if br:
        p = parse_params(params)
        if tag in ('h', 'hi'):
            return ('head' if tag == 'h' else 'head_inherit'), p, rest, None
        if tag == 'jig':
            return 'jig', p, rest, None
        if tag == 'ik':
            return 'ik', p, rest, None
        if tag == 'limb':
            t = str(p.get('type', '')).lower()
            if t not in LIMB_TYPES:
                return None, {}, None, f'limb[type={t}]: tipo de miembro desconocido'
            return 'limb', {'type': t, 'split': 1}, rest, None
        return None, {}, None, f'etiqueta «{tag}[...]_» desconocida para ModelEngine'
    if tag == 'h':
        return 'head', {}, rest, None
    if tag == 'hi':
        return 'head_inherit', {}, rest, None
    if tag == 'p':
        return 'seat', {}, rest, None
    if tag == 'ob':
        return 'hitbox_obb', {}, rest, None
    if tag == 'b':
        return 'hitbox_aabb', {}, rest, None
    if tag == 'g':
        return 'ghost', {}, rest, None
    if tag == 'tag':
        return 'nametag', {}, rest, None
    if tag == 'jig':
        return 'jig', {}, rest, None
    if tag in LIMB_TAGS:
        return 'limb', {'type': LIMB_TAGS[tag]}, rest, None
    if tag in CUBELESS_TAGS:
        if has_cubes:
            return None, {}, None, None  # con cubos no es esa etiqueta (l_hand = mano izquierda)
        beh = CUBELESS_TAGS[tag]
        # seg_/tl_ conservan el nombre entero: «tl_tail_2» y «tail_2» conviven en la Gárgola
        return beh, {}, (rest if beh in ('leash', 'item_head', 'item_right', 'item_left') else None), None
    return None, {}, None, None


# Miembros de jugador (phead_, prarm_..., limb[type=]): caja respecto al pivote del hueso (píxeles, Y arriba), sitio
# en la skin (64×64) de la pieza y de su capa exterior, y medidas. Los brazos, de 4 de ancho (skin clásica).
LIMB_BOX = {
    'head': ((-4, 0, -4), (8, 8, 8), (0, 0), (32, 0)),
    'body': ((-4, -12, -2), (8, 12, 4), (16, 16), (16, 32)),
    'right_arm': ((-2, -12, -2), (4, 12, 4), (40, 16), (40, 32)),
    'left_arm': ((-2, -12, -2), (4, 12, 4), (32, 48), (48, 48)),
    'right_leg': ((-2, -12, -2), (4, 12, 4), (0, 16), (0, 32)),
    'left_leg': ((-2, -12, -2), (4, 12, 4), (16, 48), (0, 48)),
}
# limb[type=X]: mitades (arriba / abajo) de las piezas
LIMB_SPLIT = {'chest': ('body', 0), 'body': ('body', 1), 'right_arm': ('right_arm', 0), 'right_forearm': ('right_arm', 1),
              'left_arm': ('left_arm', 0), 'left_forearm': ('left_arm', 1), 'right_leg': ('right_leg', 0),
              'right_foreleg': ('right_leg', 1), 'left_leg': ('left_leg', 0), 'left_foreleg': ('left_leg', 1),
              'head': ('head', None)}


def limb_piece(kind, split):
    """La pieza de la skin de un miembro (right_arm, body...)."""
    return LIMB_SPLIT[kind][0] if split else kind


def limb_quads(kind, split, slim=False):
    """Caras de un miembro de jugador: -4 = la skin, -5 = su capa exterior (un poco más grande). slim: brazos de 3 px
    (las skins «Alex»), con su reparto en la skin."""
    if split:
        piece, half = LIMB_SPLIT[kind]
    else:
        piece, half = kind, None
    (x0, y0, z0), (w, h, d), main, over = LIMB_BOX[piece]
    if slim and piece in ('right_arm', 'left_arm'):
        x0, w = -1.5, 3
    hh = h if half is None else h // 2
    if half is not None:
        y0 = -hh  # cada mitad cuelga de su pivote
    out = []
    for layer, (ou, ov), grow in ((-4, main, 0.0), (-5, over, 0.5 if piece == 'head' else 0.25)):
        f = [x0 - grow, y0 - grow, z0 - grow]
        t = [x0 + w + grow, y0 + hh + grow, z0 + d + grow]
        uvs = box_uv(ou, ov, w, h, d)
        for dname, (u1, v1, u2, v2) in uvs.items():
            if half is not None and dname in ('north', 'south', 'east', 'west'):
                top = ov + d + (0 if half == 0 else hh)
                v1, v2 = top, top + hh
            cuv = [(u1, v1), (u1, v2), (u2, v2), (u2, v1)]
            verts = []
            for k, c in enumerate(FACE_CORNERS[dname]):
                pt = [t[i] if c[i] else f[i] for i in range(3)]
                verts += [round(pt[0], 4), round(pt[1], 4), round(pt[2], 4), cuv[k][0] / 64, cuv[k][1] / 64]
            n = FACE_NORMAL[dname]
            out.append([layer, n[0], n[1], n[2]] + verts + [0])
    return out


def strip_prefix(name, boss=False):
    """Id del hueso como lo nombra ModelEngine en las mecánicas (sin el prefijo de comportamiento h_ / hi_). En los
    jefes también ob_ (caja de golpe), p_ (asiento) y g_ (hueso fantasma, solo un sitio): las skills los llaman sin él
    («@modelpart{pid=bite_left}», «mountmodel{p=seat}»). l_ no: en estos packs es «left» (l_hand)."""
    for p in (('hi_', 'h_', 'ob_', 'p_', 'g_') if boss else ('hi_', 'h_')):
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


# ---------------------------------------------------------------------------------------------------------------
# Animaciones con bezier, Molang o valor antes/después: la cuenta de Blockbench (keyframe.js / timeline_animators.js)
# y se hornean en fotogramas lineales cada 1/40 s (el mod y el servidor no evalúan Molang ni curvas bezier).

BAKE_STEP = 1 / 40
DEFAULT_BEZ = {'bezier_left_time': [-0.1] * 3, 'bezier_left_value': [0] * 3, 'bezier_right_time': [0.1] * 3,
               'bezier_right_value': [0] * 3}


def needs_bake(kfs):
    for k in kfs:
        if k.get('interpolation') == 'bezier':
            return True
        dps = k.get('data_points') or []
        for dp in dps:
            if any(molang.is_expression(dp.get(ax, 0)) for ax in 'xyz'):
                return True
        if len(dps) > 1 and any(num(dps[0].get(ax, 0)) != num(dps[1].get(ax, 0)) for ax in 'xyz'):
            return True
    return False


def kf_value(k, axis, dp_index, t, length, unknown):
    dps = k['data_points']
    dp = dps[max(0, min(dp_index, len(dps) - 1))]
    v = dp.get(axis, 0)
    return molang.evaluate(v, t, unknown, length) if molang.is_expression(v) else num(v)


def bezier_y(p0, p1, p2, p3, x):
    """y de la curva bezier cúbica (puntos (tiempo, valor)) en el tiempo x (busca el parámetro por bisección)."""
    lo, hi = 0.0, 1.0
    for _ in range(40):
        mid = (lo + hi) / 2
        u = 1 - mid
        bx = u * u * u * p0[0] + 3 * u * u * mid * p1[0] + 3 * u * mid * mid * p2[0] + mid * mid * mid * p3[0]
        if bx < x:
            lo = mid
        else:
            hi = mid
    t = (lo + hi) / 2
    u = 1 - t
    return u * u * u * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t * t * t * p3[1]


def bb_sample(kfs, time, length, unknown):
    """[x, y, z] del canal en ese instante, como Blockbench (antes de cambiar signos)."""
    before = after = None
    bi = -1
    for i, k in enumerate(kfs):
        kt = num(k.get('time', 0))
        if kt < time:
            if before is None or kt > num(before.get('time', 0)):
                before, bi = k, i
        elif after is None or kt < num(after.get('time', 0)):
            after = k
    out = []
    for ai, ax in enumerate('xyz'):
        if before is not None and abs(num(before.get('time', 0)) - time) < EPS:
            out.append(kf_value(before, ax, 0, time, length, unknown))
            continue
        if after is not None and abs(num(after.get('time', 0)) - time) < EPS:
            out.append(kf_value(after, ax, 0, time, length, unknown))
            continue
        if before is not None and (before.get('interpolation') == 'step' or after is None):
            out.append(kf_value(before, ax, 9, time, length, unknown))  # el «después» del anterior
            continue
        if before is None:
            out.append(kf_value(after, ax, 0, time, length, unknown))
            continue
        tb, ta = num(before.get('time', 0)), num(after.get('time', 0))
        alpha = (time - tb) / (ta - tb) if ta > tb else 0.0
        vb = kf_value(before, ax, 9, time, length, unknown)
        va = kf_value(after, ax, 0, time, length, unknown)
        ib, ia = before.get('interpolation', 'linear'), after.get('interpolation', 'linear')
        if ib == 'catmullrom' or ia == 'catmullrom':
            bp = kfs[bi - 1] if bi - 1 >= 0 else before
            ap = kfs[bi + 2] if bi + 2 < len(kfs) else after
            out.append(catmull(alpha, kf_value(bp, ax, 9, time, length, unknown), vb, va,
                               kf_value(ap, ax, 0, time, length, unknown)))
        elif ib == 'bezier' or ia == 'bezier':
            gap = ta - tb
            p0, p3 = (tb, vb), (ta, va)
            if ib == 'bezier':
                rt = num((before.get('bezier_right_time') or DEFAULT_BEZ['bezier_right_time'])[ai])
                rv = num((before.get('bezier_right_value') or DEFAULT_BEZ['bezier_right_value'])[ai])
                p1 = (tb + max(0.0, min(gap, rt)), vb + rv)
            else:
                p1 = p0
            if ia == 'bezier':
                lt = num((after.get('bezier_left_time') or DEFAULT_BEZ['bezier_left_time'])[ai])
                lv = num((after.get('bezier_left_value') or DEFAULT_BEZ['bezier_left_value'])[ai])
                p2 = (ta + max(-gap, min(0.0, lt)), va + lv)
            else:
                p2 = p3
            out.append(bezier_y(p0, p1, p2, p3, time))
        else:
            out.append(vb + (va - vb) * alpha)
    return out


def bake_channel(kfs, length, ch, sign, unknown):
    """Fotogramas lineales cada BAKE_STEP (más los de los propios fotogramas y sus saltos antes/después)."""
    times = {round(num(k.get('time', 0)), 5) for k in kfs}
    depends = any(molang.is_expression(dp.get(ax, 0)) and molang.depends_on_time(dp.get(ax, 0))
                  for k in kfs for dp in k['data_points'] for ax in 'xyz')
    start = 0.0 if depends else min(times)
    end = max(length, max(times)) if depends else max(times)
    t = start
    while t < end:
        times.add(round(t, 5))
        t += BAKE_STEP
    times.add(round(end, 5))
    # justo antes de cada fotograma (2 ms): un escalón salta seco, como en Blockbench
    for k in kfs:
        kt = num(k.get('time', 0))
        if kt - 0.002 > start:
            times.add(round(kt - 0.002, 5))
    jumps = set()
    for k in kfs:
        dps = k['data_points']
        if len(dps) > 1:
            jumps.add(round(num(k.get('time', 0)), 5))
    out = []
    for t in sorted(times):
        v = bb_sample(kfs, t, length, unknown)
        out.append([t] + [round(v[i] * sign[i], 4) for i in range(3)] + [0])
        if t in jumps:
            # el «después» justo detrás (2 ms: no se ve el paso)
            k = next(k for k in kfs if round(num(k.get('time', 0)), 5) == t)
            post = [kf_value(k, ax, 9, t, length, unknown) for ax in 'xyz']
            out.append([round(t + 0.002, 5)] + [round(post[i] * sign[i], 4) for i in range(3)] + [0])
    # quita los repetidos en línea recta (deja el archivo pequeño)
    slim = []
    for row in out:
        if len(slim) >= 2:
            a, b = slim[-2], slim[-1]
            span = row[0] - a[0]
            if span > 0 and all(abs(a[j] + (row[j] - a[j]) * (b[0] - a[0]) / span - b[j]) < 1e-3 for j in (1, 2, 3)):
                slim[-1] = row
                continue
        slim.append(row)
    return slim


def convert(bb_path, store, problems, boss=False):
    """Devuelve el modelo convertido (dict) o None si no sirve.

    boss=True (los jefes): además de lo de siempre, cada animación dice si tapa a las de menos prioridad («ov», el
    «override» de Blockbench, como las mezcla ModelEngine) y sus scripts de la línea de tiempo («fx»: [segundo,
    skill], los «mm:SKILL» de ModelEngine); los huesos ob_ no se dibujan: son cajas de golpe («hb»: esquinas de la caja
    respecto a su pivote, en píxeles); el hueso «hitbox» da el tamaño del mob («hitbox»: ancho y alto en bloques) y
    los asientos p_ se marcan («seat»)."""
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
    tex_out, tex_map, tex_uv, tex_img = [], {}, {}, []
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
        tex_img.append(img)
        tname = (t.get('name') or '').lower()
        tex_out.append({
            'path': store.put(img),
            'frames': frames,
            'ft': max(1, int(num(t.get('frame_time', 1)) or 1)),
            'e': tname.endswith('_e') or tname.endswith('_e.png') or t.get('render_mode') == 'emissive',
            'body': tname.startswith('body_grey') or tname == 'body',
        })

    elements = {e['uuid']: e for e in d.get('elements', [])}
    bones = []
    uuid_to_bone = {}

    actor_cubes = []  # (nombre de la pieza, índice del hueso, texturas) para las figuras

    def bake_cube(el, pivot, phead, quads, bone_idx=None):
        # Una pieza del muñeco sin exportar (fallo del pack) sí cuenta para el mob, que la tiene: solo la versión -3.
        # En los jefes, el «ojo» de Blockbench (visibility) no cuenta: ModelEngine dibuja esos cubos igual y el pack los
        # enseña o esconde con partvis (el escudo del Esqueleto, el zarpazo de la Abominación). Antes se perdían.
        only_skin = el.get('export') is False or el.get('visibility') is False and not boss
        if only_skin and str(el.get('name', '')).lower() not in BODY_PARTS:
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
        part = str(el.get('name', '')).lower()
        actor = phead or part in BODY_PARTS
        if part in BODY_PARTS and bone_idx is not None:
            texs = set() if only_skin else {tex_map[fc['texture']] for fc in (el.get('faces') or {}).values()
                                            if fc.get('texture') in tex_map}
            actor_cubes.append((part, bone_idx, texs))
        flags = (1 if light > 0 else 0) | (2 if actor else 0)

        def corners(dname, ff, tt):
            out = []
            for c in FACE_CORNERS[dname]:
                p = [tt[i] if c[i] else ff[i] for i in range(3)]
                p = apply(rm, [p[i] - origin[i] for i in range(3)])
                out.append([p[i] + origin[i] - pivot[i] for i in range(3)])
            return out

        if part in BODY_PARTS:
            # La misma pieza con el reparto de una skin (-3): para enseñar un mob en las miniaturas de la web.
            # Marca 4: pieza principal (con la textura del cuerpo); si no hay, el cuerpo es de otro material.
            texs0 = {fc.get('texture') for fc in (el.get('faces') or {}).values() if isinstance(fc.get('texture'), int)}
            main = any(x in tex_map and tex_out[tex_map[x]]['body'] for x in texs0)
            dims = [abs(num(el['to'][i]) - num(el['from'][i])) for i in range(3)]
            ou, ov = SKIN_OFFSET[part]
            for dname, (u1, v1, u2, v2) in box_uv(ou, ov, round(dims[0]), round(dims[1]), round(dims[2])).items():
                a, b = FACE_AXES[dname]
                if hi[a] - lo[a] < 1e-6 or hi[b] - lo[b] < 1e-6:
                    continue
                cuv = [(u1, v1), (u1, v2), (u2, v2), (u2, v1)]
                verts = []
                for k, p in enumerate(corners(dname, f, t)):
                    verts += [round(p[0], 4), round(p[1], 4), round(p[2], 4), cuv[k][0] / 64, cuv[k][1] / 64]
                n = apply(rm, FACE_NORMAL[dname])
                quads.append([-3, round(n[0], 4), round(n[1], 4), round(n[2], 4)] + verts + [2 | (4 if main else 0)])

        if only_skin:
            return
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
                    quads.append([layer, round(n[0], 4), round(n[1], 4), round(n[2], 4)] + verts + [2])
        box_faces = None
        if el.get('box_uv', d.get('meta', {}).get('box_uv', False)) and any(not fc.get('uv') for fc in (el.get('faces') or {}).values()):
            # Box UV sin UV por cara (archivos viejos): el reparto de la caja desde uv_offset, como Blockbench
            ou, ov = (num(x) for x in (el.get('uv_offset') or [0, 0])[:2])
            dims = [math.floor(abs(num(el['to'][i]) - num(el['from'][i])) + 1e-6) for i in range(3)]
            box_faces = box_uv(ou, ov, dims[0], dims[1], dims[2])
            if el.get('mirror_uv'):
                box_faces = {k: (v[2], v[1], v[0], v[3]) for k, v in box_faces.items()}
                box_faces['east'], box_faces['west'] = box_faces['west'], box_faces['east']
        for dname, face in (el.get('faces') or {}).items():
            if dname not in FACE_CORNERS:
                continue
            ti = face.get('texture')
            uv = face.get('uv') or (list(box_faces[dname]) if box_faces else None)
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
                         + ([flags] if flags else []))

    hitbox = []
    shadow = []
    inherit_heads = set()

    def cube_box(c):
        """Caja que ocupan los cubos de un hueso (en píxeles del modelo)."""
        lo, hi = [1e9] * 3, [-1e9] * 3
        for ch in c.get('children', []):
            if isinstance(ch, str) and ch in elements:
                el = elements[ch]
                for i in range(3):
                    a, b = num(el['from'][i]), num(el['to'][i])
                    lo[i] = min(lo[i], a, b)
                    hi[i] = max(hi[i], a, b)
        return (lo, hi) if lo[0] < 1e9 else None

    def walk(children, parent, shift=(0.0, 0.0, 0.0)):
        """shift: cuánto se mueve todo lo que cuelga de aquí (los hijos de un jig[repeat=N]_ van tras el último eslabón)."""
        for c in children:
            if not isinstance(c, dict):
                continue  # cubos sueltos en la raíz: ModelEngine no los usa
            bname = c.get('name', 'bone')
            if bname.lower() in SPECIAL_BONES:
                if boss and bname.lower() == 'hitbox' and not hitbox:
                    box = cube_box(c)
                    if box:
                        lo, hi = box
                        hitbox.append([round(max(hi[0] - lo[0], hi[2] - lo[2]) / 16, 4), round((hi[1] - lo[1]) / 16, 4)])
                if boss and bname.lower() == 'shadow' and not shadow:
                    # La sombra de ModelEngine: su ancho mayor es el diámetro (sin hueso «shadow», no hay sombra)
                    box = cube_box(c)
                    if box:
                        lo, hi = box
                        shadow.append(round(max(hi[0] - lo[0], hi[2] - lo[2]) / 16, 4))
                continue  # (el «export: false» de Blockbench no cuenta: ModelEngine dibuja igual ese hueso)
            idx = len(bones)
            pivot = [num(v) for v in c.get('origin', [0, 0, 0])]
            sid = strip_prefix(bname, boss)
            phead = sid.lower().startswith('phead')
            beh, bparams = None, {}
            head = bname.startswith('h_') or bname.startswith('hi_')
            if boss:
                has_cubes = any(isinstance(ch, str) and ch in elements for ch in c.get('children', []))
                beh, bparams, new_id, why = bone_tag(bname, has_cubes)
                if why:
                    problems.append(f'{name}: hueso «{bname}»: {why}')
                if new_id is not None:
                    sid = new_id
                head = beh in ('head', 'head_inherit')
                phead = beh == 'limb' and bparams.get('type') == 'head' or sid.lower().startswith('phead')
            bone = {
                'id': sid,
                'parent': parent,
                'pivot': [round(pivot[i] + shift[i], 4) for i in range(3)],
                'rot': [round(num(v), 4) for v in (c.get('rotation') or [0, 0, 0])],
                'head': head,
                'phead': phead,
                # (en los jefes, tampoco el ojo de los huesos: el Esqueleto duerme con el escudo del trono y la espada a
                # la espalda, ocultos en Blockbench, y el pack los esconde al despertar)
                'hidden': c.get('visibility') is False and not boss,
                'shade': c.get('shade', True) is not False,
                'quads': [],
            }
            if boss and (bname.startswith('p_') or beh == 'seat'):
                bone['seat'] = True
            if boss and beh:
                bone['beh'] = beh
                if bparams:
                    bone['bp'] = bparams
                if beh == 'head_inherit':
                    inherit_heads.add(idx)
            if boss and parent >= 0 and parent in inherit_heads:
                # hi_: todo lo que cuelga de él es cabeza sin llevar la etiqueta (también por debajo de un h_ anidado),
                # con los límites h[...] de su cabeza si no trae los suyos
                if not head:
                    bone['head'] = True
                    lim = {k: v for k, v in (bones[parent].get('bp') or {}).items() if k in HEAD_KEYS}
                    if lim:
                        bone['bp'] = dict(lim, **(bone.get('bp') or {}))
                inherit_heads.add(idx)
            bones.append(bone)
            uuid_to_bone[c['uuid']] = idx
            if boss and (bname.startswith('ob_') or beh in ('hitbox_obb', 'hitbox_aabb')):
                # Caja de golpe de ModelEngine: no se ve; ob_ se mueve y gira con su hueso, b_ se mueve sin girar
                box = cube_box(c)
                if box:
                    lo, hi = box
                    bone['hb'] = [round(lo[i] - pivot[i], 4) for i in range(3)] + [round(hi[i] - pivot[i], 4) for i in range(3)]
                    if beh == 'hitbox_aabb':
                        bone['aabb'] = True
                        if abs((hi[0] - lo[0]) - (hi[2] - lo[2])) > 1e-3:
                            problems.append(f'{name}: la caja b_ «{bname}» no es cuadrada en X/Z (ModelEngine la pide así)')
                walk(c.get('children', []), idx, shift)
                continue
            if boss and beh == 'limb':
                # Miembro de jugador: sus cubos no cuentan; se dibuja la skin (modelplayerskin) con la caja del miembro
                bone['quads'] = limb_quads(bparams['type'], bparams.get('split'))
                if limb_piece(bparams['type'], bparams.get('split')) in ('right_arm', 'left_arm'):
                    # los mismos brazos para una skin slim (modelplayerskin{slim} o la del jugador): 3 px de ancho
                    bone['sq'] = limb_quads(bparams['type'], bparams.get('split'), slim=True)
                walk(c.get('children', []), idx, shift)
                continue
            for ch in c.get('children', []):
                if isinstance(ch, str) and ch in elements:
                    el = elements[ch]
                    if el.get('type') in ('null_object', 'locator'):
                        loose_points.append((el, idx, shift))  # objetos nulos (IK) y localizadores: huesos invisibles luego
                        continue
                    bake_cube(el, pivot, phead, bone['quads'], idx)
            last = idx
            if boss and beh == 'jig' and int(num(bparams.get('repeat', 1))) > 1:
                last, shift = jig_repeat(c, bone, idx, pivot, shift, int(num(bparams['repeat'])))
            walk(c.get('children', []), last, shift)

    def jig_repeat(c, bone, idx, pivot, shift, n):
        """jig[repeat=N]_ de ModelEngine: el eslabón se copia N veces en cadena (cada copia cuelga del final de la
        anterior: su pivote y el otro extremo de sus cubos) y lo que colgaba de él va tras el último. Las copias no llevan
        el «repeat» (como en ModelEngine, los eslabones de dentro no lo copian). Devuelve (último, desplazamiento)."""
        box = cube_box(c)
        bone['bp'] = {k: v for k, v in bone['bp'].items() if k != 'repeat'}
        if not bone['bp']:
            del bone['bp']
        if not box:
            problems.append(f'{name}: «{c.get("name")}»: jig[repeat] sin cubos (no se sabe lo que mide el eslabón)')
            return idx, shift
        centre = [(box[0][i] + box[1][i]) / 2 for i in range(3)]
        link = [2 * (centre[i] - pivot[i]) for i in range(3)]
        if sum(x * x for x in link) < 0.25:
            problems.append(f'{name}: «{c.get("name")}»: jig[repeat] con el pivote en medio del eslabón (se pone en un extremo)')
            return idx, shift
        prev = idx
        for k in range(1, n):
            cp = dict(bone, id=f'{bone["id"]}_{k + 1}', parent=prev,
                      pivot=[round(pivot[i] + shift[i] + k * link[i], 4) for i in range(3)])
            cp.pop('seat', None)
            prev = len(bones)
            bones.append(cp)
        return prev, tuple(shift[i] + (n - 1) * link[i] for i in range(3))

    loose_points = []
    ik_chains = []
    walk(d.get('outliner', []), -1)
    for ch in d.get('outliner', []):
        if isinstance(ch, str) and ch in elements and elements[ch].get('type') in ('null_object', 'locator'):
            loose_points.append((elements[ch], -1, (0.0, 0.0, 0.0)))
    # Objetos nulos y localizadores (Blockbench): huesos sin cubos donde están (los anima su «animator» de posición). Los
    # nulos con objetivo IK hacen de objetivo de su cadena (ModelEngine: «Blockbench IK»)
    if boss and loose_points:
        point_bone = {}
        for el, par, sh in loose_points:
            pos = [num(v) + sh[i] for i, v in enumerate(el.get('position') or el.get('from') or [0, 0, 0])]
            bidx = len(bones)
            bones.append({'id': el.get('name', 'null'), 'parent': par, 'pivot': [round(v, 4) for v in pos], 'rot': [0.0, 0.0, 0.0],
                          'head': False, 'phead': False, 'hidden': False, 'shade': True, 'quads': [],
                          'beh': 'iknull' if el.get('type') == 'null_object' else 'locator'})
            uuid_to_bone[el['uuid']] = bidx
            point_bone[el['uuid']] = bidx

        def ancestors(b):
            out = []
            while b >= 0:
                out.append(b)
                b = bones[b]['parent']
            return out

        for el, par, _sh in loose_points:
            tgt = el.get('ik_target')
            if el.get('type') != 'null_object' or not tgt:
                continue
            end = uuid_to_bone.get(tgt)
            if end is None:
                problems.append(f'{name}: el objeto nulo «{el.get("name")}» apunta a un objetivo IK que no existe')
                continue
            up = ancestors(end)
            src = uuid_to_bone.get(el.get('ik_source')) if el.get('ik_source') else None
            if src is not None and src not in up:
                problems.append(f'{name}: IK «{el.get("name")}»: el origen no está por encima del objetivo')
                continue
            chain = list(reversed(up[:up.index(src) + 1] if src is not None else up))
            null_b = point_bone[el['uuid']]
            pole = uuid_to_bone.get(el.get('ik_pole')) if el.get('ik_pole') else -1
            if any(x in chain for x in ancestors(null_b)) or pole in chain:
                problems.append(f'{name}: IK «{el.get("name")}»: el objeto nulo o el polo están dentro de la cadena '
                                f'(ModelEngine se la salta)')
                continue
            ik_chains.append({'null': null_b, 'chain': chain, 'lock': bool(el.get('lock_ik_target_rotation')),
                              'pole': pole if pole is not None else -1})
    if not bones:
        problems.append(f'{name}: sin huesos')
        return None

    anims = {}
    for a in d.get('animations', []):
        tracks = []
        for uid, an in (a.get('animators') or {}).items():
            if an.get('type', 'bone') not in ('bone', 'null_object') or uid not in uuid_to_bone:
                continue
            tr = {'b': uuid_to_bone[uid]}
            for ch, key in (('rotation', 'r'), ('position', 'p'), ('scale', 's')):
                kfs = [k for k in an.get('keyframes', []) if k.get('channel') == ch and k.get('data_points')]
                if not kfs:
                    continue
                kfs.sort(key=lambda k: num(k.get('time', 0)))
                sign = (-1, -1, 1) if ch == 'rotation' else (-1, 1, 1) if ch == 'position' else (1, 1, 1)
                if needs_bake(kfs):
                    # bezier, Molang o valores distintos antes/después: se hornea con la cuenta de Blockbench
                    unknown = set()
                    out = bake_channel(kfs, num(a.get('length', 0)), ch, sign, unknown)
                    for u in sorted(unknown):
                        problems.append(f'{name}: animación «{a.get("name")}», Molang que no se entiende: {u}')
                else:
                    out = []
                    for k in kfs:
                        dp = k['data_points'][0]
                        x, y, z = (num(dp.get(ax, 0)) * sign[i] for i, ax in enumerate('xyz'))
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
        if boss:
            anims[a['name']]['ov'] = bool(a.get('override'))
            fx = []
            for an in (a.get('animators') or {}).values():
                for k in an.get('keyframes', []):
                    if k.get('channel') != 'timeline':
                        continue
                    for dp in k.get('data_points') or []:
                        for line in str(dp.get('script') or '').splitlines():
                            line = line.strip()
                            if line.lower().startswith('mm:'):
                                fx.append([round(num(k.get('time', 0)), 5), line[3:].strip()])
            if fx:
                anims[a['name']]['fx'] = sorted(fx)
    out = {'name': name, 'tex': tex_out, 'bones': bones, 'anims': anims}
    if ik_chains:
        out['ik'] = ik_chains
    if boss and hitbox:
        out['hitbox'] = hitbox[0]
    if boss and shadow:
        out['shadow'] = shadow[0]
    figures = make_figures(bones, actor_cubes, tex_out)
    if figures:
        for fig in figures:
            # El material, relleno para cualquier mob
            fig['tex'] = [None if t < 0 else store.put(mob_texture(first_frame(tex_img[t], tex_out[t]['frames'])))
                          for t in fig['tex']]
        out['figures'] = figures
    return out


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


HEAD_KEYS = ('yaw', 'pitch', 'minyaw', 'maxyaw', 'minpitch', 'maxpitch', 'speed')


def head_limits(b):
    """(mín. yaw, máx. yaw, mín. pitch, máx. pitch) de una cabeza según su etiqueta h[...]_ (None = libre): VfxModel.headLimits."""
    p = b.get('bp') or {}

    def one(k):
        v = p.get(k)
        return (v[0] if isinstance(v, list) else v) if v is not None else None
    out = [None] * 4
    if one('yaw') is not None:
        out[0], out[1] = -abs(one('yaw')), abs(one('yaw'))
    if one('pitch') is not None:
        out[2], out[3] = -abs(one('pitch')), abs(one('pitch'))
    for i, k in enumerate(('minyaw', 'maxyaw', 'minpitch', 'maxpitch')):
        if one(k) is not None:
            out[i] = one(k)
    return out


def transpose(m):
    return [[m[j][i] for j in range(3)] for i in range(3)]


def clamp(v, lo, hi):
    """Como VfxPose.clamp: None = sin límite."""
    if lo is not None and v < lo:
        v = lo
    if hi is not None and v > hi:
        v = hi
    return v


def pose(model, anim_name, t, head=None):
    """Matrices 4x4 (listas) de cada hueso en el espacio del modelo (píxeles) en el instante t (segundos).

    head=(yaw, pitch) en grados: hacia dónde mira respecto al cuerpo, con las cabezas de ModelEngine 4 como
    VfxPose.build (giro global que no se acumula; los hijos sin etiqueta de un h_ no giran con él)."""
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
    look = head is not None and (head[0] or head[1])
    eye = [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
    q0, g = [], []
    for i, b in enumerate(model['bones']):
        r, p, s = extra.get(i, [[0, 0, 0], [0, 0, 0], [1, 1, 1]])
        par = b['parent']
        parent_pivot = model['bones'][par]['pivot'] if par >= 0 else [0, 0, 0]
        tr = [b['pivot'][k] - parent_pivot[k] + p[k] for k in range(3)]
        rm = rot_matrix([b['rot'][k] + r[k] for k in range(3)])
        gi = None
        if look and b.get('head'):
            yaw, pitch = head
            lim = head_limits(b)
            yaw, pitch = clamp(yaw, lim[0], lim[1]), clamp(pitch, lim[2], lim[3])
            gi =mul(rot_matrix([0, -yaw, 0]), rot_matrix([-pitch, 0, 0]))
        q0p = q0[par] if par >= 0 else eye
        gp = g[par] if par >= 0 else None
        c = eye
        if gi is not None or gp is not None:
            c = mul(transpose(gp) if gp is not None else eye, gi if gi is not None else eye)
            c = mul(mul(transpose(q0p), c), q0p)
        q0.append(mul(q0p, rm))
        g.append(gi)
        rm = mul(c, rm)
        local = [[rm[i2][j] * s[j] for j in range(3)] + [tr[i2]] for i2 in range(3)] + [[0, 0, 0, 1]]
        mats.append(local if par < 0 else mat4(mats[par], local))
    return mats


def mat4(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]
