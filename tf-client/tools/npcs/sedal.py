"""El sedal de los pescadores cuelga por su peso (orden del dueño, 1.3.102: «arregla ese porque quiero poner pescadores»).

En las animaciones del pack Scenes Fishermen el sedal (hueso «bone2», hijo de la punta de la caña «tool») sube al cielo:
en «fishing» y «sitting» a 71-76° sobre la horizontal y hasta 10-13 bloques de alto, en «interact» el pez queda en el
aire (medido con las cuentas del mod y, aparte, con las de Blockbench sobre el .bbmodel: es del pack). Aquí, en cada
fotograma, el sedal se gira lo mínimo para que apunte recto hacia abajo desde la punta de la caña (con el anzuelo y el
pez colgando debajo); la caña, los brazos y el largo del sedal son los del pack. Solo la pista de giro del sedal: en
fotogramas lineales a 60 por segundo (y los de la animación), quitando los que sobran. Volver a pasarlo no cambia nada.

Uso: python3 tools/npcs/sedal.py [carpeta assets/tfclient]  (sin carpeta: los del jar). Lo llama build_npcs.
"""
import json
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)
sys.path.insert(0, TOOLS)
sys.path.insert(0, HERE)
import vfx_bb as B  # noqa: E402
import pegado as P  # noqa: E402

MODEL = 'npc_fishermen.scene_fishermen'
LINE, ROD = 'bone2', 'tool'
FPS = 60
TOL = 0.05  # grados que se permite quitar al simplificar
DOWN = np.array([0.0, -1.0, 0.0])


def euler_zyx(r):
    """Los grados (x, y, z) de vfx_bb.rot_matrix (R = Rz·Ry·Rx) que dan la matriz r."""
    y = math.asin(max(-1.0, min(1.0, -r[2][0])))
    if abs(math.cos(y)) > 1e-6:
        x = math.atan2(r[2][1], r[2][2])
        z = math.atan2(r[1][0], r[0][0])
    else:  # (de canto: el giro en z se lo lleva x)
        x = math.atan2(-r[1][2], r[1][1])
        z = 0.0
    return [math.degrees(x), math.degrees(y), math.degrees(z)]


def align(d, to):
    """El giro más corto que lleva la dirección d a «to» (Rodrigues)."""
    d = d / np.linalg.norm(d)
    v = np.cross(d, to)
    c = float(np.dot(d, to))
    if np.linalg.norm(v) < 1e-9:
        if c > 0:
            return np.eye(3)
        # al revés: media vuelta alrededor de cualquier eje perpendicular
        ax = np.cross(d, [1.0, 0.0, 0.0])
        if np.linalg.norm(ax) < 1e-6:
            ax = np.cross(d, [0.0, 0.0, 1.0])
        ax /= np.linalg.norm(ax)
        return 2 * np.outer(ax, ax) - np.eye(3)
    k = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
    return np.eye(3) + k + k @ k * (1 / (1 + c))


def rotation_of(m3):
    """La parte de giro de una matriz con escala (descomposición polar)."""
    u, _s, vt = np.linalg.svd(m3)
    r = u @ vt
    if np.linalg.det(r) < 0:
        u[:, -1] *= -1
        r = u @ vt
    return r


def simplify(keys):
    """Quita los fotogramas que la interpolación lineal ya da (con TOL grados)."""
    if len(keys) <= 2:
        return keys
    out, i = [keys[0]], 0
    while i < len(keys) - 1:
        j = i + 1
        while j + 1 < len(keys):
            t0, t1 = keys[i][0], keys[j + 1][0]
            ok = all(abs(keys[i][c] + (keys[j + 1][c] - keys[i][c]) * ((keys[k][0] - t0) / (t1 - t0)) - keys[k][c]) <= TOL
                     for k in range(i + 1, j + 1) for c in (1, 2, 3))
            if not ok:
                break
            j += 1
        out.append(keys[j])
        i = j
    return out


def line_angle(model, name, t):
    """Grados del sedal sobre la horizontal en ese instante (None si no se ve: escala 0)."""
    ids = [b['id'] for b in model['bones']]
    a = model['anims'][name]
    mats = P.pose(model, P.extra_of(model, [(name, t, 1.0, bool(a.get('ov')))]))
    m3 = mats[ids.index(LINE)][:3, :3]
    if abs(np.linalg.det(m3)) < 1e-9:
        return None
    d = rotation_of(m3) @ np.array([0.0, 0.0, -1.0])
    return math.degrees(math.asin(max(-1.0, min(1.0, d[1]))))


def hang(model):
    """Cuelga el sedal en todas las animaciones donde se ve. Devuelve {animación: (ángulo más alto antes, después)}."""
    ids = [b['id'] for b in model['bones']]
    li, ri = ids.index(LINE), ids.index(ROD)
    if model['bones'][li]['parent'] != ri:
        raise SystemExit(f'{MODEL}: el sedal ya no cuelga de la caña')
    rest = model['bones'][li]['rot']
    out = {}
    for name, a in model['anims'].items():
        ln = a['len'] or 0
        n = max(1, int(round(ln * FPS)))
        times = {round(ln * k / n, 5) for k in range(n + 1)}
        for tr in a['tracks']:
            for ch in ('r', 'p', 's'):
                for kf in tr.get(ch) or []:
                    if 0 <= kf[0] <= ln:
                        times.add(round(kf[0], 5))
        times = sorted(times)
        ov = bool(a.get('ov'))
        keys, before = [], -90.0
        for t in times:
            mats = P.pose(model, P.extra_of(model, [(name, t, 1.0, ov)]))
            m3 = mats[li][:3, :3]
            if abs(np.linalg.det(m3)) < 1e-9:
                keys.append(None)  # no se ve (escala 0): se deja como esté
                continue
            world = rotation_of(m3)
            d = world @ np.array([0.0, 0.0, -1.0])
            before = max(before, math.degrees(math.asin(max(-1.0, min(1.0, d[1])))))
            parent = rotation_of(mats[ri][:3, :3])
            local = parent.T @ (align(d, DOWN) @ world)
            e = euler_zyx(local.tolist())
            keys.append([t] + [e[k] - rest[k] for k in range(3)])
        if all(k is None for k in keys) or before <= -89.5:
            continue
        # donde no se ve, el valor del vecino (no importa: está a escala 0)
        last = next(k for k in keys if k is not None)
        filled = []
        for t, k in zip(times, keys):
            k = [t] + (k[1:] if k is not None else last[1:])
            last = k
            filled.append(k)
        # sin saltos de 360° entre fotogramas (la interpolación lineal daría la vuelta entera)
        arr = np.unwrap(np.radians(np.array([k[1:] for k in filled])), axis=0)
        keys = [[t] + [round(float(math.degrees(v)), 3) for v in row] + [0] for t, row in zip(times, arr)]
        track = next((tr for tr in a['tracks'] if tr['b'] == li), None)
        if track is None:
            track = {'b': li}
            a['tracks'].append(track)
        track['r'] = simplify(keys)
        after = max((x for x in (line_angle(model, name, t) for t in times[::max(1, len(times) // 400)]) if x is not None),
                    default=None)
        out[name] = (before, after)
    return out


def main(res=None):
    res = res or os.path.normpath(os.path.join(TOOLS, '..', 'src', 'main', 'resources', 'assets', 'tfclient'))
    path = os.path.join(res, 'npcs', 'models', MODEL + '.json')
    model = json.load(open(path, encoding='utf-8'))
    done = hang(model)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(model, f, separators=(',', ':'))
    for k, (b, a) in sorted(done.items()):
        print(f'{MODEL} {k}: el sedal subía hasta {b:+.1f}°; ahora, como mucho, {a:+.1f}° (−90 = recto abajo)')


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else None)
