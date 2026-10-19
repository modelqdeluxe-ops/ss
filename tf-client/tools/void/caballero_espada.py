"""Caelthar con su espada, dibujado sin el juego: para ver si la espada está de verdad en la mano.

    python3 -I tools/void/caballero_espada.py salida.png [--actual]

1.3.93 (el dueño, con captura: «la espada mal puesta en el NPC»): la 1.3.92 la ponía erguida, por fuera del brazo y
con el puño por debajo de la mano: flotaba al lado. Esto dibuja el modelo del caballero (bosses/models/vacio.caballero
.json, en reposo) y la espada (models/item/fairy_greatsword.json con sus texturas) con la misma cadena de
transformaciones que KnightRenderer.sword (pose_nueva, abajo; con --actual, la de la 1.3.92), de frente, de lado, de
tres cuartos y por detrás, y mide dónde queda el puño de la espada respecto a la mano y la punta respecto al suelo.
Medido: en la 1.3.92 el puño quedaba a 4,8 píxeles de la mano (por fuera y por delante); la 1.3.93 la clavó en el
suelo con la mano en el puño (el dueño: «como mierda: él vuela»); la 1.3.93b, como un jugador (pose_nueva).

Convenciones: el modelo va en píxeles, con +y arriba y la espalda hacia +z (el pecho mira a -z); cada hueso = padre ·
trasladar(pivote − pivote del padre) · giro, y sus caras van respecto a su pivote (vfx/VfxPose). La espada: como
ItemRenderer.renderStatic en THIRD_PERSON_RIGHT_HAND (su «display»: trasladar, girar XYZ, escalar; luego −0,5 y los
elementos en 1/16).
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient'))
MODEL = os.path.join(RES, 'bosses', 'models', 'vacio.caballero.json')
SWORD = os.path.join(RES, 'models', 'item', 'fairy_greatsword.json')


def T(x, y, z):
    m = np.eye(4)
    m[:3, 3] = (x, y, z)
    return m


def S(x, y=None, z=None):
    return np.diag([x, x if y is None else y, x if z is None else z, 1.0])


def R(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    m = np.eye(4)
    if axis == 'x':
        m[1:3, 1:3] = [[c, -s], [s, c]]
    elif axis == 'y':
        m[0, 0], m[0, 2], m[2, 0], m[2, 2] = c, s, -s, c
    else:
        m[0:2, 0:2] = [[c, -s], [s, c]]
    return m


def chain(*ms):
    out = np.eye(4)
    for m in ms:
        out = out @ m
    return out


# La espada en la mano, en los píxeles del caballero, desde el hueso del brazo derecho (= KnightRenderer.sword)
def pose_actual():
    return chain(T(1, 0, 0), S(16), S(-1, -1, 1), T(-2.5 / 16, 0.625, -0.25), R('z', -12), R('x', 180), R('y', 270), S(0.8),
                 T(0, 3.25 / 16, -1 / 16))


# (1.3.93b, el dueño: «la pusiste como mierda, recuerda que él vuela y se ve mal; pónsela normal, tal cual fuera un
# jugador») Como la lleva un jugador: el brazo adelantado 18° (HumanoidModel.ArmPose.ITEM: xRot·0,5 − π/10) y la espada
# con lo mismo que ItemInHandLayer (translateToHand, girar X −90, Y 180, trasladar 1/16, 0,125, −0,625). El brazo del
# caballero es el de un jugador (pivote 5, 22, 0; el antebrazo de x −1,25 a 3,25 y la mano a 10,25 píxeles), con +y
# arriba en vez de abajo: los ejes de Minecraft son los nuestros con x e y al revés (escalar −1, −1, 1).
ARM_PITCH = 18.0             # el brazo, hacia delante (grados)


def arm_extra():
    return R('x', ARM_PITCH)


def pose_nueva(disp):
    tr = [c / 16.0 for c in disp.get('translation', [0, 0, 0])]
    return chain(S(16), S(-1, -1, 1), R('x', -90), R('y', 180), T(1 / 16.0, 0.125, -0.625), T(*tr), T(-0.5, -0.5, -0.5), S(1 / 16.0))


def bone_mats(model, extra=None):
    bones = model['bones']
    mats = []
    for i, b in enumerate(bones):
        p = b['parent']
        piv = np.array(b['pivot'], float)
        ppiv = np.array(bones[p]['pivot'], float) if p >= 0 else np.zeros(3)
        rx, ry, rz = b['rot']
        m = chain(T(*(piv - ppiv)), R('z', rz), R('y', ry), R('x', rx))
        if extra and b['id'] in extra:
            m = m @ extra[b['id']]
        mats.append((mats[p] if p >= 0 else np.eye(4)) @ m)
    return mats


def load_png(path):
    im = Image.open(path).convert('RGBA')
    w, h = im.size
    if h > w and h % w == 0:
        im = im.crop((0, 0, w, w))
    return np.asarray(im).astype(np.float32) / 255.0


def tex_path(ref):
    ns, p = ref.split(':', 1) if ':' in ref else ('minecraft', ref)
    if not p.startswith('textures/'):
        p = 'textures/' + p
    if not p.endswith('.png'):
        p += '.png'
    return os.path.join(RES, p) if ns == 'tfclient' else None


def knight_tris(model, mats):
    tris = []
    texs = [load_png(tex_path(t['path'])) for t in model['tex']]
    for bi, b in enumerate(model['bones']):
        if b['id'] in ('left_island', 'right_island', 'name'):
            continue
        q = b['quads']
        if not isinstance(q, list):
            continue
        for quad in q:
            ti = int(quad[0])
            if ti < 0 or ti >= len(texs):
                continue
            vs = []
            for k in range(4):
                x, y, z, u, v = quad[4 + 5 * k:9 + 5 * k]
                p = mats[bi] @ np.array([x, y, z, 1.0])
                vs.append((p[:3], (u, v)))
            tris.append((texs[ti], vs[0], vs[1], vs[2]))
            tris.append((texs[ti], vs[0], vs[2], vs[3]))
    return tris


def sword_tris(model, m):
    texrefs = model['textures']
    cache = {}

    def tex(ref):
        ref = ref.lstrip('#')
        while ref in texrefs and not ':' in ref and ref != texrefs[ref]:
            ref = texrefs[ref].lstrip('#') if texrefs[ref].startswith('#') else texrefs[ref]
            if ':' in ref:
                break
        if ref not in cache:
            cache[ref] = load_png(tex_path(ref))
        return cache[ref]

    tris = []
    for e in model['elements']:
        f, t = np.array(e['from'], float), np.array(e['to'], float)
        rot = e.get('rotation')
        em = np.eye(4)
        if rot and rot.get('angle', 0):
            o = np.array(rot['origin'], float)
            em = chain(T(*o), R(rot['axis'], rot['angle']), T(*(-o)))
        x0, y0, z0 = f
        x1, y1, z1 = t
        corners = {
            'north': [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
            'south': [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
            'west': [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
            'east': [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
            'up': [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
            'down': [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
        }
        for face, fd in e.get('faces', {}).items():
            uv = fd.get('uv', [0, 0, 16, 16])
            u0, v0, u1, v1 = (c / 16.0 for c in uv)
            uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
            img = tex(fd['texture'])
            vs = []
            for c, (u, v) in zip(corners[face], uvs):
                p = m @ em @ np.array([c[0], c[1], c[2], 1.0])
                vs.append((p[:3], (u, v)))
            tris.append((img, vs[0], vs[1], vs[2]))
            tris.append((img, vs[0], vs[2], vs[3]))
    return tris


def render(tris, view, size=520, px=11.0, center=(0, 22, 0)):
    W = H = size
    col = np.zeros((H, W, 3), np.float32) + np.array([0.55, 0.7, 0.9], np.float32)
    zb = np.full((H, W), 1e9, np.float32)
    ys, xs = np.mgrid[0:H, 0:W]
    xs = xs + 0.5
    ys = ys + 0.5
    c = np.array(center, float)
    for img, a, b, cc in tris:
        P = []
        for p, uv in (a, b, cc):
            q = view @ np.append(p - c, 1.0)
            P.append((W / 2 + q[0] * px, H / 2 - q[1] * px, q[2], uv))
        (ax, ay, az, auv), (bx, by, bz, buv), (cx, cy, cz, cuv) = P
        den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if abs(den) < 1e-9:
            continue
        x0, x1 = int(max(0, min(ax, bx, cx))), int(min(W - 1, max(ax, bx, cx)) + 1)
        y0, y1 = int(max(0, min(ay, by, cy))), int(min(H - 1, max(ay, by, cy)) + 1)
        if x0 >= x1 or y0 >= y1:
            continue
        X = xs[y0:y1, x0:x1]
        Y = ys[y0:y1, x0:x1]
        l1 = ((by - cy) * (X - cx) + (cx - bx) * (Y - cy)) / den
        l2 = ((cy - ay) * (X - cx) + (ax - cx) * (Y - cy)) / den
        l3 = 1 - l1 - l2
        ins = (l1 >= -1e-4) & (l2 >= -1e-4) & (l3 >= -1e-4)
        if not ins.any():
            continue
        z = l1 * az + l2 * bz + l3 * cz
        u = l1 * auv[0] + l2 * buv[0] + l3 * cuv[0]
        v = l1 * auv[1] + l2 * buv[1] + l3 * cuv[1]
        th, tw = img.shape[:2]
        tx = np.clip((u * tw).astype(int), 0, tw - 1)
        ty = np.clip((v * th).astype(int), 0, th - 1)
        texel = img[ty, tx]
        ok = ins & (texel[..., 3] > 0.1) & (z < zb[y0:y1, x0:x1])
        zb[y0:y1, x0:x1][ok] = z[ok]
        col[y0:y1, x0:x1][ok] = texel[..., :3][ok]
    return Image.fromarray((np.clip(col, 0, 1) * 255).astype(np.uint8))


def main():
    out = sys.argv[1]
    actual = '--actual' in sys.argv
    model = json.load(open(MODEL))
    extra = None if actual else {'right_arm': arm_extra()}
    mats = bone_mats(model, extra)
    arm = [b['id'] for b in model['bones']].index('right_arm')
    sword = json.load(open(SWORD))
    if actual:
        # (desde S(16), en bloques; los elementos de la espada, en 1/16)
        disp = sword['display']['thirdperson_righthand']
        tr = [c / 16.0 for c in disp.get('translation', [0, 0, 0])]
        m = mats[arm] @ pose_actual() @ T(*tr) @ T(-0.5, -0.5, -0.5) @ S(1 / 16.0)
    else:
        m = mats[arm] @ pose_nueva(sword['display']['thirdperson_righthand'])
    tris = knight_tris(model, mats) + sword_tris(sword, m)
    # dónde queda el puño de la espada y dónde la mano (píxeles del caballero)
    grip = m @ np.array([8.0, 3.8, 8.0, 1.0])
    hand = mats[arm] @ np.array([1.0, -9.6, 0.0, 1.0])
    tip = m @ np.array([8.0, 32.0, 7.5, 1.0])
    print('punta de la hoja %s' % np.round(tip[:3], 2))
    print('puño de la espada %s; mano %s; distancia %.2f px' % (np.round(grip[:3], 2), np.round(hand[:3], 2),
                                                               np.linalg.norm(grip[:3] - hand[:3])))
    # La cámara mira hacia +z en la vista (lo de menos z, delante) y la x de la pantalla es la −x de la vista (como al
    # mirar de verdad): de frente, el pecho (−z) hacia la cámara y su brazo derecho (+x) a la izquierda de la imagen
    mir = S(-1, 1, 1)
    views = [('frente', mir), ('lado', mir @ R('y', 90)), ('tres_cuartos', mir @ R('y', 45)), ('detras', mir @ R('y', 180))]
    ims = [render(tris, v, center=(0, 20, 0)) for _, v in views]
    sheet = Image.new('RGB', (520 * len(ims), 520))
    for i, im in enumerate(ims):
        sheet.paste(im, (520 * i, 0))
    sheet.save(out)
    print('hecho:', out, [n for n, _ in views])


if __name__ == '__main__':
    main()
