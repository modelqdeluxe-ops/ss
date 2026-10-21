"""El sedal de los pescadores cuelga por su peso (orden del dueño, 1.3.102: «arregla ese porque quiero poner pescadores»).

En las animaciones del pack Scenes Fishermen el sedal (hueso «bone2», hijo de la punta de la caña «tool») sube al cielo:
en «fishing» y «sitting» a 71-76° sobre la horizontal y hasta 10-13 bloques de alto, en «interact» el pez queda en el
aire (medido con las cuentas del mod y, aparte, con las de Blockbench sobre el .bbmodel: es del pack). Aquí, en cada
fotograma, el sedal se gira lo mínimo para que apunte recto hacia abajo desde la punta de la caña (con el anzuelo y el
pez colgando debajo); la caña, los brazos y el largo del sedal son los del pack. Solo la pista de giro del sedal: en
fotogramas lineales a 60 por segundo (y los de la animación), quitando los que sobran. Volver a pasarlo no cambia nada.

(1.3.103, el dueño: «el pescador al recoger el anzuelo lo hace por detrás suyo») Además, la caña y el anzuelo siempre
por delante de él:
- en «fishing» y «sitting» la caña del pack se queda a 112° (más allá de la vertical, por detrás de la cabeza) mientras
  espera y se va a 220-233° al recoger: la errata de signo del autor (build_npcs.HUECOS). Las claves de giro de la caña
  que la dejan por detrás cambian de signo en x (como en «boat», donde la caña va bien), sin saltos de 360°;
- en «interact» la apoya en el hombro (198°) con el pez a la espalda: las claves que la dejan por detrás se reflejan
  hacia delante (sostiene el pez delante de él);
- y el sedal no cuelga recto si la punta de la caña no está al menos FRONT px por delante (el tirón de «boat»): va
  tenso hacia el agua de delante, lo justo para que el anzuelo quede FRONT px por delante del pescador.

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
ARM = 'right_arm'
FWD = np.array([0.0, 0.0, -1.0])  # hacia dónde mira el pescador (los modelos miran a −z)
FRONT = 20.0  # px (16 = un bloque) por delante del pescador a los que, como poco, queda el anzuelo
ROD_FLIP = ('fishing', 'sitting')  # la errata de signo del autor: la caña por detrás → x cambiado de signo
ROD_MIRROR = ('interact',)  # la caña al hombro → reflejada hacia delante


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


def _interp(keys, t):
    """El valor en x de una pista de giro lineal en t."""
    if t <= keys[0][0]:
        return keys[0][1]
    for k0, k1 in zip(keys, keys[1:]):
        if k0[0] <= t <= k1[0]:
            return k0[1] + (k1[1] - k0[1]) * (t - k0[0]) / max(1e-9, k1[0] - k0[0])
    return keys[-1][1]


def rod(model):
    """La caña por delante en ROD_FLIP y ROD_MIRROR. Devuelve {animación: claves cambiadas}."""
    ids = [b['id'] for b in model['bones']]
    ti, ai = ids.index(ROD), ids.index(ARM)
    out = {}
    for name in ROD_FLIP + ROD_MIRROR:
        a = model['anims'].get(name)
        if not a:
            continue
        track = next((tr for tr in a['tracks'] if tr['b'] == ti), None)
        arm = next((tr for tr in a['tracks'] if tr['b'] == ai), None)
        if not track or not track.get('r') or not arm or not arm.get('r'):
            continue
        # por detrás: el giro en x del brazo más el de la caña pasa de la vertical (90°..270°; 0 = recta delante)
        behind = [90.0 < (_interp(arm['r'], k[0]) + k[1]) % 360.0 < 270.0 for k in track['r']]
        n = 0
        for k, back in zip(track['r'], behind):
            if not back:
                continue
            if name in ROD_FLIP:
                k[1] = -k[1]
            else:  # el giro de la caña (brazo + caña, en x) θ → 180° − θ
                k[1] = 180.0 - 2.0 * _interp(arm['r'], k[0]) - k[1]
            n += 1
        # sin vueltas enteras entre dos claves (la interpolación lineal daría la vuelta)
        for k0, k1 in zip(track['r'], track['r'][1:]):
            while k1[1] - k0[1] > 180:
                k1[1] -= 360
            while k1[1] - k0[1] < -180:
                k1[1] += 360
        for k in track['r']:
            k[1] = round(k[1], 3)
        if n:
            out[name] = n
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


def length(model, mats, li):
    """El largo del sedal en ese instante (px): lo más lejos de su punta que llega su geometría."""
    q = P.quads_of(model['bones'][li]).reshape(-1, 3)
    if len(q) == 0:
        return 1.0
    w = (mats[li][:3, :3] @ q.T).T
    return float(np.max(np.linalg.norm(w, axis=1)))


def hang(model):
    """Cuelga el sedal en todas las animaciones donde se ve. Devuelve {animación: (ángulo más alto antes, después)}."""
    ids = [b['id'] for b in model['bones']]
    li, ri = ids.index(LINE), ids.index(ROD)
    if model['bones'][li]['parent'] != ri:
        raise SystemExit(f'{MODEL}: el sedal ya no cuelga de la caña')
    rest = model['bones'][li]['rot']
    root = ids.index('player') if 'player' in ids else 0
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
        keys, before, off = [], -90.0, 0.0
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
            # (1.3.103) recto hacia abajo si la punta está al menos FRONT px por delante; si no, tenso hacia el agua de
            # delante, lo justo para que el anzuelo quede FRONT px por delante del pescador (la caña por detrás)
            tip = mats[li][:3, 3]
            ahead = float((tip - mats[root][:3, 3]) @ FWD)
            h = min(0.93, max(0.0, (FRONT - ahead) / max(1.0, length(model, mats, li))))
            target = FWD * h + DOWN * math.sqrt(1.0 - h * h)
            off = max(off, math.degrees(math.acos(max(-1.0, min(1.0, float(d @ target))))))
            local = parent.T @ (align(d, target) @ world)
            e = euler_zyx(local.tolist())
            keys.append([t] + [e[k] - rest[k] for k in range(3)])
        if all(k is None for k in keys) or off <= 5 * TOL:  # (ya apunta donde debe, salvo lo que quita simplify)
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
    for k, n in sorted(rod(model).items()):
        print(f'{MODEL} {k}: la caña, por delante en {n} claves (estaba por detrás del pescador)')
    done = hang(model)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(model, f, separators=(',', ':'))
    for k, (b, a) in sorted(done.items()):
        print(f'{MODEL} {k}: el sedal subía hasta {b:+.1f}°; ahora, como mucho, {a:+.1f}° (−90 = recto abajo)')


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else None)
