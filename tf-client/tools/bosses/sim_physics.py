"""Colas, segmentos y física de ModelEngine (VfxPhysics.java) copiados en Python, para verlos sin abrir el juego:
dibuja fotogramas de un jefe que gira y anda, con sus huesos seg_/tl_/jig_ simulados, y mide cómo se doblan.

    python3 tools/bosses/sim_physics.py <modelo> [carpeta] [--anim idle] [--giro 120] [--anda 0.3] [--sin]
        modelo: el id del modelo (gargola.lrd_gargoyle, esqueleto_torre.tower_skeleton...)
        --giro: grados que gira en 10 ticks (luego se queda quieto); --anda: bloques por tick hacia delante
        --sin: sin física (para comparar)
Saca una tira de imágenes (vista de arriba y de lado) y, por cada cadena, el ángulo de cada eslabón con su forma
animada en cada tick: tiene que doblarse al girar, no pasar del límite y volver poco a poco.

Mismas reglas que VfxPhysics: cada eslabón cuelga del de delante (o de su padre), mira hacia donde estaba en el mundo,
vuelve a su forma con extendrate por tick, no se aparta más de anglelimit (50° por defecto) y su trozo visible se
orienta con su eslabón (o con el siguiente si cuelga hacia atrás).
"""
import json
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..'))
import vfx_bb as B  # noqa: E402

RES = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient'))
SEG_TAGS = ('segf_', 'seg_', 'tlf_', 'tl_')


def beh_of(b):
    if b.get('beh'):
        return b['beh']
    low = b['id'].lower()
    for t in SEG_TAGS:
        if low.startswith(t) and not b['quads']:
            return t[:-1]
    return None


def is_seg(b):
    return beh_of(b) in ('seg', 'segf', 'tl', 'tlf')


def rot_to(a, b):
    """Giro mínimo que lleva el vector a al b (matriz 3×3)."""
    a = a / np.linalg.norm(a)
    b = b / np.linalg.norm(b)
    v = np.cross(a, b)
    c = float(np.dot(a, b))
    if c < -0.999999:
        axis = np.cross(a, [1, 0, 0])
        if np.linalg.norm(axis) < 1e-6:
            axis = np.cross(a, [0, 1, 0])
        axis /= np.linalg.norm(axis)
        return 2 * np.outer(axis, axis) - np.eye(3)
    vx = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
    return np.eye(3) + vx + vx @ vx / (1 + c)


def decompose(m):
    t = m[:3, 3].copy()
    sc = np.linalg.norm(m[:3, :3], axis=0)
    r = m[:3, :3] / sc
    return t, r, sc


def compose(t, r, sc):
    m = np.eye(4)
    m[:3, :3] = r * sc
    m[:3, 3] = t
    return m


class Physics:
    def __init__(self, model):
        self.m = model
        bones = model['bones']
        n = len(bones)
        self.n = n
        self.seg = [is_seg(b) and b['parent'] >= 0 for b in bones]
        self.anchor = [-1] * n
        self.next = [-1] * n
        self.cfg = [None] * n
        for i, b in enumerate(bones):
            if self.seg[i]:
                a = b['parent']
                while a >= 0 and not is_seg(bones[a]):
                    a = bones[a]['parent']
                self.anchor[i] = a if a >= 0 else b['parent']
                self.cfg[i] = {'bounded': True, 'minx': -50, 'maxx': 50, 'miny': -50, 'maxy': 50, 'extend': 0.01,
                               'elastic': 0.0}
        for i in range(n):
            a = self.anchor[i]
            if self.seg[i] and a >= 0 and self.seg[a] and self.next[a] < 0:
                self.next[a] = i
        rest = [np.array(x) for x in B.pose(model, None, 0)]
        self.outgoing = [self.decide(i, rest) if self.seg[i] else False for i in range(n)]
        self.joint = [None] * n
        self.vel = [np.zeros(3) for _ in range(n)]
        self.link = [np.eye(3) for _ in range(n)]
        self.last = None

    def descends(self, b, anc):
        p = self.m['bones'][b]['parent']
        while p >= 0:
            if p == anc:
                return True
            p = self.m['bones'][p]['parent']
        return False

    def decide(self, i, rest):
        b = self.m['bones'][i]
        if beh_of(b) in ('segf', 'tlf'):
            return self.next[i] >= 0
        nx = self.next[i]
        if nx < 0:
            return False
        p = rest[i][:3, 3]
        to_next = rest[nx][:3, 3] - p
        pts = []
        for j in range(i, self.n):
            if j != i and not self.descends(j, i):
                continue
            if j == nx or self.descends(j, nx):
                continue
            for q in self.m['bones'][j]['quads']:
                for k in range(4):
                    v = np.array(q[4 + k * 5:7 + k * 5] + [1.0])
                    pts.append((rest[j] @ v)[:3])
        if not pts:
            return False
        c = np.mean(pts, axis=0) - p
        return float(np.dot(c, to_next)) > 0

    def configure(self, part, vals, exact=True):
        want = (part or '').lower()
        for i in range(self.n):
            if not self.seg[i]:
                continue
            bid = self.m['bones'][i]['id'].lower()
            hit = not want or (bid == want or bid.endswith('_' + want) and bid.index('_') == len(bid) - len(want) - 1
                               if exact else want in bid)
            if not hit:
                continue
            for j in [i] + [j for j in range(i + 1, self.n) if self.seg[j] and self.descends(j, i)]:
                c = self.cfg[j]
                if 'bounded' in vals:
                    c['bounded'] = bool(vals['bounded'])
                if 'angle' in vals:
                    a = abs(vals['angle'])
                    c.update(minx=-a, maxx=a, miny=-a, maxy=a)
                if 'extend' in vals:
                    c['extend'] = max(0.0, min(1.0, vals['extend']))
                if 'elastic' in vals:
                    c['elastic'] = max(0.0, min(0.95, vals['elastic']))

    def apply(self, mats, world, ticks):
        dt = 0.0 if self.last is None else ticks - self.last
        fresh = self.last is None or dt < 0 or dt > 20
        if fresh:
            dt = 0.0
        dt = min(dt, 3.0)
        self.last = ticks
        h = [m.copy() for m in mats]
        _, wr, wsc = decompose(world)
        ws = wsc[0]
        winv = np.linalg.inv(world)
        moved = [False] * self.n
        solved = [False] * self.n

        def solve(i):
            solved[i] = True
            a = self.anchor[i]
            c = self.cfg[i]
            if self.seg[a]:
                if not solved[a]:
                    solve(a)
                aw = self.joint[a].copy()
                ad = self.link[a].copy()
            else:
                aw = (world @ np.append(mats[a][:3, 3], 1))[:3]
                ad = decompose(mats[a])[1] @ decompose(h[a])[1].T
            link_m = h[i][:3, 3] - h[a][:3, 3]
            ln = np.linalg.norm(link_m) * ws
            ref_m = ad @ link_m
            ref = wr @ ref_m
            if ln < 1e-5:
                self.joint[i] = aw
                self.link[i] = ad
                return
            ref = ref / np.linalg.norm(ref)
            if fresh or self.joint[i] is None:
                d = ref.copy()
                self.vel[i] = np.zeros(3)
            else:
                prev = self.joint[i] + (self.vel[i] * c['elastic'] * min(1, dt) if c['elastic'] > 0 else 0)
                d = prev - aw
                d = d / np.linalg.norm(d) if np.linalg.norm(d) > 1e-9 else ref.copy()
                k = 0 if c['extend'] <= 0 else 1 - (1 - c['extend']) ** dt
                if k > 0:
                    d = d + (ref - d) * k
                    d /= np.linalg.norm(d)
            if c['bounded']:
                frame = wr @ ad @ decompose(h[i])[1]
                dl, rl = frame.T @ d, frame.T @ ref
                yd, yr = math.atan2(dl[0], dl[2]), math.atan2(rl[0], rl[2])
                pd = math.atan2(dl[1], math.hypot(dl[0], dl[2]))
                pr = math.atan2(rl[1], math.hypot(rl[0], rl[2]))
                dy = math.degrees((yd - yr + math.pi) % (2 * math.pi) - math.pi)
                dp = math.degrees(pd - pr)
                cy = max(c['miny'], min(c['maxy'], dy))
                cp = max(c['minx'], min(c['maxx'], dp))
                if cy != dy or cp != dp:
                    yaw, pit = yr + math.radians(cy), pr + math.radians(cp)
                    d = frame @ np.array([math.cos(pit) * math.sin(yaw), math.sin(pit), math.cos(pit) * math.cos(yaw)])
                    d /= np.linalg.norm(d)
            nj = aw + d * ln
            if not fresh and dt > 0 and self.joint[i] is not None:
                self.vel[i] = (nj - self.joint[i]) / max(dt, 1e-3) * min(1, dt)
            self.joint[i] = nj
            dm = wr.T @ d
            self.link[i] = rot_to(ref_m, dm) @ ad

        for i in range(self.n):
            p = self.m['bones'][i]['parent']
            if self.seg[i]:
                if not solved[i]:
                    solve(i)
                use = self.next[i] if self.outgoing[i] and self.next[i] >= 0 else i
                if use != i and not solved[use]:
                    solve(use)
                pos = (winv @ np.append(self.joint[i], 1))[:3]
                _, r, sc = decompose(h[i])
                mats[i] = compose(pos, self.link[use] @ r, sc)
                moved[i] = True
            elif p >= 0 and moved[p]:
                mats[i] = mats[p] @ np.linalg.inv(h[p]) @ h[i]
                moved[i] = True
        return mats

    def angles(self, mats, h):
        """Cuánto se aparta cada eslabón del de delante (grados): lo que limita anglelimit."""
        out = {}
        for i in range(self.n):
            if not self.seg[i]:
                continue
            a = self.anchor[i]
            ad = self.link[a] if self.seg[a] else decompose(mats[a])[1] @ decompose(h[a])[1].T
            v1 = ad @ (h[i][:3, 3] - h[a][:3, 3])
            v2 = mats[i][:3, 3] - mats[a][:3, 3]
            if np.linalg.norm(v1) < 1e-6 or np.linalg.norm(v2) < 1e-6:
                continue
            c = np.dot(v1, v2) / np.linalg.norm(v1) / np.linalg.norm(v2)
            out[self.m['bones'][i]['id']] = math.degrees(math.acos(max(-1, min(1, c))))
        return out


def world_of(x, z, yaw):
    """Del modelo (píxeles) al mundo, como renderFollower: trasladar, girar 180−yaw y escalar 1/16."""
    a = math.radians(180 - yaw)
    r = np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])
    m = np.eye(4)
    m[:3, :3] = r / 16
    m[:3, 3] = [x, 0, z]
    return m


def main():
    args = sys.argv[1:]
    mid = args[0]
    out_dir = args[1] if len(args) > 1 and not args[1].startswith('--') else None
    anim = args[args.index('--anim') + 1] if '--anim' in args else 'idle'
    turn = float(args[args.index('--giro') + 1]) if '--giro' in args else 120.0
    walk = float(args[args.index('--anda') + 1]) if '--anda' in args else 0.0
    off = '--sin' in args
    worst, frames, model = simulate(mid, anim, turn, walk, off)
    if out_dir:
        render_frames(model, frames, out_dir, mid + ('_sin' if off else ''))


def has_chains(model):
    """¿Tiene el modelo colas/segmentos o física (lo que mira simulate)?"""
    return any(str(beh_of(b) or '').startswith(('seg', 'tl', 'jig')) for b in model['bones'])


def simulate(mid, anim='idle', turn=120.0, walk=0.0, off=False, quiet=False):
    """Gira el jefe «turn» grados en 10 ticks (y anda «walk» por tick) 60 ticks: (lo más que se aparta un eslabón de
    su forma, fotogramas para dibujar, modelo). quiet: sin imprimir (para tools/bosses/verificar.py)."""
    out = (lambda *a, **k: None) if quiet else print
    model = json.load(open(os.path.join(RES, 'bosses', 'models', mid + '.json'), encoding='utf-8'))
    ph = Physics(model)
    # La configuración de segment{} del pack (sus líneas en el programa del jefe)
    prog = json.load(open(os.path.join(RES, 'bosses', mid.split('.')[0] + '.json'), encoding='utf-8'))
    for meta in list(prog['tree'].values()) + [{'mechs': md['mechs']} for md in prog['mobs'].values()]:
        for pm in meta['mechs']:
            a = pm.get('a', {})
            if pm['m'] in ('segment', 'seg') and str(a.get('m', a.get('mid', mid))).lower() in (mid, mid.split('.', 1)[1]):
                vals = {}
                for k, keys in (('angle', ('anglelimit', 'a', 'al', 'angle')), ('extend', ('extendrate', 'e', 'er', 'extend')),
                                ('elastic', ('elasticity', 'el'))):
                    for kk in keys:
                        if kk in a:
                            vals[k] = float(a[kk])
                            break
                if 'bounded' in a or 'b' in a:
                    vals['bounded'] = str(a.get('bounded', a.get('b'))).lower() == 'true'
                ph.configure(a.get('p', a.get('pid', a.get('part', ''))), vals, str(a.get('em', 'true')).lower() == 'true')
                out('   segment{}:', a.get('p'), vals)
    segs = [model['bones'][i]['id'] for i in range(ph.n) if ph.seg[i]]
    out(f'{mid}: {len(segs)} eslabones', '(sin física)' if off else '')
    for i in range(ph.n):
        if ph.seg[i]:
            out(f'   {model["bones"][i]["id"]:24s} cuelga de {model["bones"][ph.anchor[i]]["id"]:22s} '
                  f'trozo visible {"hacia atrás (siguiente eslabón)" if ph.outgoing[i] else "hacia delante (su eslabón)"}')
    frames = []
    yaw, x, z = 0.0, 0.0, 0.0
    worst = 0.0
    for tick in range(0, 61):
        if tick <= 10:
            yaw = turn * tick / 10
        if walk:
            a = math.radians(yaw)
            x -= math.sin(a) * walk
            z += math.cos(a) * walk
        mats = [np.array(m) for m in B.pose(model, anim if anim in model['anims'] else None, tick / 20)]
        h = [m.copy() for m in mats]
        W = world_of(x, z, yaw)
        if not off:
            ph.apply(mats, W, float(tick))
        ang = ph.angles(mats, h)
        worst = max([worst] + list(ang.values()))
        if tick in (0, 3, 6, 10, 14, 20, 30, 45, 60):
            out(f'  tick {tick:2d} giro {yaw:6.1f}°  apartado de su forma: ' +
                  ' '.join(f'{k.split("_", 1)[-1]}={v:4.1f}' for k, v in list(ang.items())[:8]))
            frames.append((tick, mats, W))
    out(f'  lo más que se apartó un eslabón: {worst:.1f}° (límite 50° por eslabón)')
    return worst, frames, model


def render_frames(model, frames, out_dir, name):
    import vfx_render as R
    from PIL import Image
    os.makedirs(out_dir, exist_ok=True)
    first = model['tex'][0]['path'].split(':', 1)[-1]  # tfclient:textures/bosses/<jefe>/<x>.png
    tex = R.load_textures(model, os.path.join(RES, os.path.dirname(first)))
    tiles = []
    for tick, mats, W in frames:
        row = []
        for yaw, pitch in ((0, -89), (-90, -10)):
            tris = R.model_tris(model, tex, [m.tolist() for m in mats], world=np.eye(4))
            row.append(R.raster(tris, size=256, yaw=yaw, pitch=pitch))
        tiles.append(row)
    w = 256 * len(tiles)
    img = Image.new('RGBA', (w, 512), (30, 30, 36, 255))
    for c, row in enumerate(tiles):
        for r, im in enumerate(row):
            img.alpha_composite(im.convert('RGBA'), (c * 256, r * 256))
    path = os.path.join(out_dir, name + '.png')
    img.save(path)
    print('  imagen:', path)


if __name__ == '__main__':
    main()
