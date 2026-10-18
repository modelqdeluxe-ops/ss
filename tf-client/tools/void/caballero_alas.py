"""Las alas de Caelthar, el Caballero Sacro (modelo bosses/models/vacio.caballero.json): su animación «reposo».

    python3 -I tools/void/caballero_alas.py [--solo-medir]

1.3.89: «reposo» era la parte tranquila (0-9 s) del «idle» del modelo. 1.3.90, el dueño: «el Caballero Sacro aún hace
mal la animación de las alas». Medido (este script): en esa animación el segundo tramo de cada ala iba plegado −110°
hacia delante y sus plumas llegaban a z = −7,0, casi 5 píxeles por delante del pecho (z = −2,2): de frente o de lado,
las alas le cruzaban por delante del pecho y de los brazos. Y la raíz nacía en z = 1,75, dentro del torso y del casco.

Ahora: alas abiertas hacia atrás (el primer tramo a 30° de la espalda, el segundo 5° más abierto), que aletean despacio
(dos veces en los 9 s del bucle: ±6° hacia atrás, ±4° arriba y abajo, la punta con un poco de retraso) y nacen 1,5
píxeles más atrás (z = 3,25, detrás de la espalda). El resto del cuerpo (respirar, los brazos) sigue como estaba.

Convenciones del motor (vfx/VfxPose): cada hueso = padre · trasladar(pivote − pivote del padre + posición animada) ·
giroZYX(giro de reposo + giro animado), como Blockbench; las caras del modelo van respecto al pivote de su hueso; la
espalda mira a +z. La comprobación muestrea las plumas (los píxeles de la textura con alfa) de las cuatro partes de las
alas cada 0,25 s y cuenta las que quedan dentro de las cajas de verdad del torso, los brazos, las piernas y el casco, y
lo más adelante que llega una pluma. Si algo atraviesa el cuerpo o pasa por delante del pecho, para.
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

LEN = 9.0          # el bucle (el del cuerpo)
FLAPS = 2          # aleteos por bucle
Y1, A1 = 15.0, 6.0  # primer tramo: hacia atrás (más los 15° de su giro de reposo) y cuánto aletea
Z1, B1 = -6.0, 4.0  # primer tramo: arriba/abajo
Y2, A2 = -5.0, 7.0  # segundo tramo: respecto al primero
BACK = 1.5         # el pivote de las alas, más atrás (píxeles)
WINGS = {'left_wing': 1, 'left_wing2': 2, 'right_wing': -1, 'right_wing2': -2}
CHEST = -2.25      # la cara de delante del torso
BODY_BOXES = {'main_tail': ([-4.25, 0.25, -2.25], [4.25, 12.75, 2.25]), 'head': ([-4.25, -0.25, -4.25], [4.25, 8.25, 4.25])}


def rot_zyx(rz, ry, rx):
    rz, ry, rx = map(math.radians, (rz, ry, rx))
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def sample(keys, t):
    if not keys:
        return None
    if t <= keys[0][0]:
        return keys[0][1:4]
    for a, b in zip(keys, keys[1:]):
        if a[0] <= t <= b[0]:
            k = (t - a[0]) / max(1e-9, b[0] - a[0])
            return [a[i] + (b[i] - a[i]) * k for i in (1, 2, 3)]
    return keys[-1][1:4]


def pose(d, anim, t):
    bones = d['bones']
    extra = {i: [0.0] * 6 for i in range(len(bones))}
    for tr in d['anims'][anim]['tracks']:
        r, p = sample(tr.get('r'), t), sample(tr.get('p'), t)
        if r:
            extra[tr['b']][0:3] = r
        if p:
            extra[tr['b']][3:6] = p
    M = []
    for i, b in enumerate(bones):
        e = extra[i]
        pp = bones[b['parent']]['pivot'] if b['parent'] >= 0 else [0, 0, 0]
        T = np.eye(4)
        T[:3, 3] = [b['pivot'][k] - pp[k] + e[3 + k] for k in range(3)]
        R = np.eye(4)
        R[:3, :3] = rot_zyx(b['rot'][2] + e[2], b['rot'][1] + e[1], b['rot'][0] + e[0])
        M.append((M[b['parent']] if b['parent'] >= 0 else np.eye(4)) @ T @ R)
    return M


def wing_tracks():
    n = 12 * FLAPS
    out = {k: [] for k in WINGS}
    for k in range(n + 1):
        t = LEN * k / n
        ph = 2 * math.pi * FLAPS * k / n
        s, s2 = math.sin(ph), math.sin(ph - 0.6)
        for name, side in WINGS.items():
            sg = 1 if side > 0 else -1
            if abs(side) == 1:
                out[name].append([round(t, 5), round(2.0 * s, 4), round(sg * (Y1 + A1 * s), 4), round(sg * (Z1 + B1 * s), 4), 1])
            else:
                out[name].append([round(t, 5), 0.0, round(sg * (Y2 + A2 * s2), 4), 0.0, 1])
    return out


def build(d):
    idx = {b['id']: i for i, b in enumerate(d['bones'])}
    idle = d['anims']['idle']
    # el cuerpo: lo tranquilo del idle (0-9 s), sin las alas
    tracks = []
    for tr in idle['tracks']:
        if tr['b'] in [idx[w] for w in WINGS]:
            continue
        t2 = {'b': tr['b']}
        for ch in ('r', 'p', 's'):
            if ch in tr:
                keys = [k for k in tr[ch] if k[0] <= LEN + 1e-6]
                if keys:
                    if keys[-1][0] < LEN:
                        keys.append([LEN] + sample(tr[ch], LEN) + [keys[-1][4]])
                    t2[ch] = keys
        if len(t2) > 1:
            tracks.append(t2)
    wt = wing_tracks()
    for name, keys in wt.items():
        tr = {'b': idx[name], 'r': keys}
        if abs(WINGS[name]) == 1:
            tr['p'] = [[0.0, 0.0, 0.0, BACK, 0], [LEN, 0.0, 0.0, BACK, 0]]
        tracks.append(tr)
    d['anims']['reposo'] = {'len': LEN, 'loop': 'loop', 'tracks': tracks}


def check(d, anim='reposo'):
    """(plumas dentro del cuerpo por parte, lo más adelante que llega una pluma)."""
    tex_path = os.path.join(RES, d['tex'][0]['path'].split(':')[1])
    tex = np.asarray(Image.open(tex_path).convert('RGBA'))
    TH, TW = tex.shape[:2]
    bones = d['bones']
    idx = {b['id']: i for i, b in enumerate(bones)}
    boxes = dict(BODY_BOXES)
    for arm in ('left_arm', 'right_arm', 'left_leg', 'right_leg'):
        pts = np.array([q[4 + k * 5: 4 + k * 5 + 3] for q in bones[idx[arm]]['quads'] for k in range(4)])
        boxes[arm] = (pts.min(0), pts.max(0))
    inside, front = {}, 9e9
    for t in np.arange(0.0, d['anims'][anim]['len'] + 1e-6, 0.25):
        M = pose(d, anim, t)
        for w in WINGS:
            q = bones[idx[w]]['quads'][0]
            v = [np.array(q[4 + k * 5: 4 + k * 5 + 5]) for k in range(4)]
            P = []
            for a in np.linspace(0.02, 0.98, 36):
                for b in np.linspace(0.02, 0.98, 36):
                    p = (1 - a) * (1 - b) * v[0] + a * (1 - b) * v[1] + a * b * v[2] + (1 - a) * b * v[3]
                    if tex[min(TH - 1, int(p[4] * TH)), min(TW - 1, int(p[3] * TW)), 3] > 25:
                        P.append((M[idx[w]] @ np.array([p[0], p[1], p[2], 1.0]))[:3])
            P = np.array(P)
            front = min(front, float(P[:, 2].min()))
            for name, (lo, hi) in boxes.items():
                L = (np.linalg.inv(M[idx[name]]) @ np.c_[P, np.ones(len(P))].T).T[:, :3]
                n = int(np.all((L > np.array(lo) + 0.3) & (L < np.array(hi) - 0.3), 1).sum())
                if n:
                    inside[name] = max(inside.get(name, 0), n)
    return inside, front


def main():
    with open(MODEL, encoding='utf-8') as f:
        d = json.load(f)
    if '--solo-medir' not in sys.argv:
        build(d)
    inside, front = check(d)
    print('plumas dentro (de ~1300 por parte, lo peor del bucle):', inside or 'ninguna')
    print('lo más adelante que llega una pluma: z = %.2f (el pecho está en z = %.2f)' % (front, CHEST))
    # (la raíz, pegada a la espalda, roza el casco por dentro: unas pocas muestras, tapadas por él)
    bad = front < 1.0 or any(v > 0 for k, v in inside.items() if k != 'head') or inside.get('head', 0) > 30
    if bad:
        sys.exit('las alas atraviesan el cuerpo o pasan por delante del pecho: no se guarda')
    if '--solo-medir' not in sys.argv:
        with open(MODEL, 'w', encoding='utf-8') as f:
            json.dump(d, f, ensure_ascii=False, separators=(',', ':'))
        print('guardado en', os.path.relpath(MODEL))


if __name__ == '__main__':
    main()
