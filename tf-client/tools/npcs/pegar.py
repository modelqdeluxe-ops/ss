"""Pega al cuerpo la cabeza, los brazos y las piernas de los NPC de escenas en todas sus animaciones (orden del dueño,
1.3.101: «no quiero ninguna extremidad despegada, por más pequeñito que sea el hueco»).

Las animaciones del propio pack las despegan (medido con pegado.py y, aparte, con las reglas de Blockbench: lo mismo al
milésimo; ModelEngine las enseña igual). Aquí, en cada fotograma, lo justo para que vuelvan a tocarse:
  - una pierna (hermana del cuerpo): se acerca el CUERPO a la pierna (con la cabeza y los brazos, que van con él), así los
    pies siguen en el suelo; si así no se pegan las dos, se acerca la pierna;
  - un brazo o la cabeza (hijos del cuerpo): se acercan al cuerpo.
Se mide con la geometría del modelo del pack (la «Plantilla del pack») y con la de la piel del panel (todas las pieles
tienen la misma), y se mueve lo que pida la peor. Solo la POSICIÓN de esos huesos (sus giros, los del pack): su pista
de posición se vuelve a hacer en fotogramas lineales (los de la animación y 60 por segundo, quitando los que sobran) con
lo del pack más la corrección; +0,10 px y comprobado a 4 veces esos fotogramas,
para que tampoco quede hueco entre dos. Una animación sin huecos no se toca (volver a pasarlo no cambia nada).

Uso: python3 tools/npcs/pegar.py [carpeta assets/tfclient] [--fps N] [--procesos N]  (sin carpeta: los del jar; los
13 packs, unos 40 min con 4 procesos). Lo llama build_npcs al convertir.
"""
import json
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)
sys.path.insert(0, TOOLS)
sys.path.insert(0, HERE)
import vfx_bb as B  # noqa: E402
import pegado as P  # noqa: E402

MARGIN = 0.10      # px de más al acercar (que se toquen de verdad, también entre dos fotogramas)
TOL = 0.005        # px: lo que se permite quitar al simplificar la pista horneada
EPS = 1e-4
CHECK = 4          # tras cada pasada se comprueba a 4 veces los fotogramas (lo que quede entre dos)


# ------------------------------------------------------------------------------------------- puntos más cercanos

def _closest_on_seg(a, b, p):
    ab = b - a
    t = np.clip(np.einsum('...k,...k->...', p - a, ab) / np.maximum(np.einsum('...k,...k->...', ab, ab), 1e-12), 0, 1)
    return a + ab * t[..., None]


def _point_quad_closest(p, q):
    """Para cada punto p (P×3) y cara q (Q×4×3): el punto de la cara más cercano (P×Q×3)."""
    n = np.cross(q[:, 1] - q[:, 0], q[:, 3] - q[:, 0])
    n = n / np.maximum(np.linalg.norm(n, axis=-1, keepdims=True), 1e-12)
    Pp = p[:, None, :]
    d = np.einsum('pqk,qk->pq', Pp - q[None, :, 0], n)
    x = Pp - d[..., None] * n[None]
    inside = P._inside(np.broadcast_to(q[None], (p.shape[0],) + q.shape), x, np.broadcast_to(n[None], x.shape))
    best = None
    bestd = None
    for i in range(4):
        c = _closest_on_seg(q[None, :, i], q[None, :, (i + 1) % 4], Pp)
        dd = np.linalg.norm(c - Pp, axis=-1)
        if best is None:
            best, bestd = c, dd
        else:
            m = dd < bestd
            best = np.where(m[..., None], c, best)
            bestd = np.where(m, dd, bestd)
    return np.where(inside[..., None], x, best)


def closest(qa, qb):
    """(distancia, punto de qa, punto de qb) de los dos grupos de caras; distancia 0 si se tocan o se cruzan."""
    if len(qa) == 0 or len(qb) == 0:
        return None
    if P.gap(qa, qb) == 0.0:
        return 0.0, None, None
    cands = []
    pa = qa.reshape(-1, 3)
    c = _point_quad_closest(pa, qb)
    dd = np.linalg.norm(c - pa[:, None], axis=-1)
    i, j = np.unravel_index(dd.argmin(), dd.shape)
    cands.append((dd[i, j], pa[i], c[i, j]))
    pb = qb.reshape(-1, 3)
    c = _point_quad_closest(pb, qa)
    dd = np.linalg.norm(c - pb[:, None], axis=-1)
    i, j = np.unravel_index(dd.argmin(), dd.shape)
    cands.append((dd[i, j], c[i, j], pb[i]))
    a0, a1 = qa.reshape(-1, 3), np.roll(qa, -1, axis=1).reshape(-1, 3)
    b0, b1 = qb.reshape(-1, 3), np.roll(qb, -1, axis=1).reshape(-1, 3)
    A0, A1, B0, B1 = a0[:, None], a1[:, None], b0[None], b1[None]
    d1, d2, r = A1 - A0, B1 - B0, A0 - B0
    a = np.einsum('...k,...k->...', d1, d1)
    e = np.einsum('...k,...k->...', d2, d2)
    f = np.einsum('...k,...k->...', d2, r)
    cc = np.einsum('...k,...k->...', d1, r)
    b = np.einsum('...k,...k->...', d1, d2)
    den = a * e - b * b
    s = np.where(den > 1e-12, np.clip((b * f - cc * e) / np.where(den > 1e-12, den, 1), 0, 1), 0)
    t = np.clip((b * s + f) / np.maximum(e, 1e-12), 0, 1)
    s = np.where(t <= 0, np.clip(-cc / np.maximum(a, 1e-12), 0, 1), np.where(t >= 1, np.clip((b - cc) / np.maximum(a, 1e-12), 0, 1), s))
    ca = A0 + d1 * s[..., None]
    cb = B0 + d2 * t[..., None]
    dd = np.linalg.norm(ca - cb, axis=-1)
    i, j = np.unravel_index(dd.argmin(), dd.shape)
    cands.append((dd[i, j], ca[i, j], cb[i, j]))
    return min(cands, key=lambda x: x[0])


# ------------------------------------------------------------------------------------------- un modelo

def pairs_of(model):
    """[(extremidad, cuerpo, quién se mueve: «body» o «limb»)] de cada personaje del modelo."""
    bones = model['bones']
    bodies = {}
    for i, b in enumerate(bones):
        ro = P.role(b['id'])
        if ro and ro[0] == 'body' and b['quads']:
            bodies[ro[1]] = i
    out = []
    for i, b in enumerate(bones):
        ro = P.role(b['id'])
        if ro is None or ro[0] == 'body' or not b['quads'] or b['hidden'] or ro[1] not in bodies:
            continue
        bi = bodies[ro[1]]
        k, under = b['parent'], False
        while k >= 0:
            if k == bi:
                under = True
                break
            k = bones[k]['parent']
        out.append((i, bi, 'limb' if under else 'body'))
    return out


def geometries(model, skin):
    """Las caras de cada hueso (en su espacio) con la plantilla del pack y, si hay piel, con ella (y en el 2.º)."""
    gs = [[P.quads_of(b) for b in model['bones']]]
    if skin is not None:
        m = P.with_skin(P.with_skin(model, skin), skin, second=True)
        gs.append([P.quads_of(b) for b in m['bones']])
    return gs


def solve(model, gs, pairs, layers):
    """Lo que hay que mover cada hueso (en el espacio de su padre) en esa pose para que todo toque: {hueso: Δ}."""
    delta = {}
    tries = {}
    base = P.extra_of(model, layers)
    for _ in range(24):
        ex = {k: [list(v[0]), list(v[1]), list(v[2])] for k, v in base.items()}
        for bone, d in delta.items():
            e = ex.setdefault(bone, [[0.0, 0.0, 0.0], [0.0, 0.0, 0.0], [1.0, 1.0, 1.0]])
            e[1] = [e[1][k] + d[k] for k in range(3)]
        mats = P.pose(model, ex)
        worst = None
        for g in gs:
            for li, bi, mover in pairs:
                wl = (g[li] @ mats[li][:3, :3].T) + mats[li][:3, 3]
                wb = (g[bi] @ mats[bi][:3, :3].T) + mats[bi][:3, 3]
                c = closest(wl, wb)
                if c is None or c[0] <= EPS:
                    continue
                if worst is None or c[0] > worst[0]:
                    worst = (c[0], li, bi, mover, c[1], c[2])
        if worst is None:
            return delta
        d, li, bi, mover, pa, pb = worst
        v = (pb - pa) * (1 + MARGIN / d)
        # una pierna: el cuerpo hacia ella (los pies en el suelo), salvo que sea la pierna la que se mueve en la animación
        # (una patada: mover el cuerpo lo llevaría de lado); si con eso no basta (otra pierna lo aparta), la pierna
        key = (li, bi)
        tries[key] = tries.get(key, 0) + 1
        if mover == 'body':
            off = lambda i: np.linalg.norm(base[i][1]) if i in base else 0.0  # noqa: E731
            if off(li) > off(bi) + 0.01 or tries[key] > 4:
                mover = 'limb'
        bone, dw = (li, v) if mover == 'limb' else (bi, -v)
        par = model['bones'][bone]['parent']
        M = mats[par][:3, :3] if par >= 0 else np.eye(3)
        if abs(np.linalg.det(M)) < 1e-9:
            return delta  # su padre está a escala 0 (escondido): no se ve
        dp = np.linalg.solve(M, dw)
        delta[bone] = delta.get(bone, np.zeros(3)) + dp
    return delta


def simplify(keys):
    """Quita los fotogramas que la interpolación lineal ya da (con TOL px)."""
    if len(keys) <= 2:
        return keys
    out = [keys[0]]
    i = 0
    while i < len(keys) - 1:
        j = i + 1
        while j + 1 < len(keys):
            t0, t1 = keys[i][0], keys[j + 1][0]
            ok = True
            for k in range(i + 1, j + 1):
                al = (keys[k][0] - t0) / (t1 - t0) if t1 > t0 else 0
                for c in (1, 2, 3):
                    if abs(keys[i][c] + (keys[j + 1][c] - keys[i][c]) * al - keys[k][c]) > TOL:
                        ok = False
                        break
                if not ok:
                    break
            if not ok:
                break
            j += 1
        out.append(keys[j])
        i = j
    return out


def grid(ln, fps):
    n = max(1, int(round(ln * fps)))
    return [round(ln * k / n, 5) for k in range(n + 1)]


def gap_at(model, name, ov, gs, pairs, t):
    """El hueco más grande de esa pose (0 si todo toca)."""
    mats = P.pose(model, P.extra_of(model, [(name, t, 1.0, ov)]))
    w = 0.0
    for g in gs:
        for li, bi, _m in pairs:
            wl = (g[li] @ mats[li][:3, :3].T) + mats[li][:3, 3]
            wb = (g[bi] @ mats[bi][:3, :3].T) + mats[bi][:3, 3]
            gp = P.gap(wl, wb)
            if gp is not None and gp > w:
                w = gp
    return w


def bake(anim, fixes, times):
    """La pista de posición de cada hueso corregido: la de ahora más su corrección, en «times» (lineal, simplificada)."""
    by_bone = {tr['b']: tr for tr in anim['tracks']}
    for bone, at in fixes.items():
        tr = by_bone.get(bone)
        if tr is None:
            tr = {'b': bone}
            anim['tracks'].append(tr)
            by_bone[bone] = tr
        orig = tr.get('p')
        keys = []
        for t in times:
            at_t = B.anim_time(anim, t)
            v = B.sample(orig, at_t) if orig and at_t is not None else [0.0, 0.0, 0.0]
            d = at.get(t, np.zeros(3))
            keys.append([t] + [round(float(v[k] + d[k]), 4) for k in range(3)] + [0])
        tr['p'] = simplify(keys)


def fix_anim(model, name, gs, pairs, fps, passes=6, extra=()):
    """Pega las extremidades en esa animación: (el hueco más grande que tenía, si se ha cambiado). Tras cada pasada se
    comprueba a CHECK veces los fotogramas; donde aún queda hueco (entre dos fotogramas), esos instantes entran en la
    siguiente."""
    anim = model['anims'][name]
    ov = bool(anim.get('ov'))
    ln = anim['len'] or 0
    times = set(grid(ln, fps)) | {round(t, 5) for t in extra if 0 <= t <= ln}
    for tr in anim['tracks']:
        for ch in ('r', 'p', 's'):
            for kf in tr.get(ch) or []:
                if 0 <= kf[0] <= ln:
                    times.add(round(kf[0], 5))
    worst, changed = 0.0, False
    for k in range(passes):
        ts = sorted(times)
        fixes = {}
        for t in ts:
            g = gap_at(model, name, ov, gs, pairs, t)
            if g > EPS:
                if not changed:
                    worst = max(worst, g)
                for bone, d in solve(model, gs, pairs, [(name, t, 1.0, ov)]).items():
                    fixes.setdefault(bone, {})[t] = d
        if fixes:
            bake(anim, fixes, ts)
            changed = True
        bad = []
        for t in grid(ln, fps * CHECK):
            g = gap_at(model, name, ov, gs, pairs, t)
            if g > EPS:
                bad.append(t)
                if not changed:
                    worst = max(worst, g)
        if not bad:
            break
        times |= set(bad)
    else:
        print(f'   ¡ojo! {name}: aún hay hueco entre fotogramas tras {passes} pasadas')
    return worst, changed


def fix_model(model, skin, fps=60):
    pairs = pairs_of(model)
    if not pairs:
        return {}
    gs = geometries(model, skin)
    out = {}
    for name in list(model['anims']):
        w, changed = fix_anim(model, name, gs, pairs, fps)
        if changed:
            out[name] = w
    return out


def _one(job):
    path, skin, fps = job
    model = json.load(open(path, encoding='utf-8'))
    fixed = fix_model(model, skin, fps)
    if fixed:
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(model, f, separators=(',', ':'))
    return os.path.basename(path)[:-5], fixed


def main(res=None, fps=60, procs=1):
    res = res or os.path.normpath(os.path.join(TOOLS, '..', 'src', 'main', 'resources', 'assets', 'tfclient'))
    cat = json.load(open(os.path.join(res, 'npcs', 'catalog.json'), encoding='utf-8'))
    skin_id = None
    used = {}
    for pack in cat:
        skins = [o['vars']['skin'] for o in pack['options'].get('skin', []) if o.get('vars', {}).get('skin')]
        for n in pack['npcs']:
            if n.get('model'):
                used.setdefault(n['model'], False)
                if n.get('autoskin') and skins:
                    used[n['model']] = True
                    skin_id = skin_id or skins[0]
    skin = None
    if skin_id:
        skin = json.load(open(os.path.join(res, 'npcs', 'models', skin_id + '.json'), encoding='utf-8'))
    # los más largos primero (el pescador: 490 s de animación)
    jobs = [(os.path.join(res, 'npcs', 'models', mid + '.json'), skin if used[mid] else None, fps) for mid in used]
    jobs.sort(key=lambda j: -os.path.getsize(j[0]))
    total = 0
    if procs > 1:
        from concurrent.futures import ProcessPoolExecutor
        with ProcessPoolExecutor(procs) as ex:
            results = list(ex.map(_one, jobs))
    else:
        results = [_one(j) for j in jobs]
    for mid, fixed in sorted(results):
        if fixed:
            total += len(fixed)
            print(f'{mid}: ' + ', '.join(f'{k} ({v:.2f} px)' for k, v in sorted(fixed.items())))
    print(f'{len(used)} modelos, {total} animaciones pegadas')


if __name__ == '__main__':
    args = sys.argv[1:]
    fps, procs = 60, 1
    if '--fps' in args:
        fps = int(args[args.index('--fps') + 1])
        del args[args.index('--fps'):args.index('--fps') + 2]
    if '--procesos' in args:
        procs = int(args[args.index('--procesos') + 1])
        del args[args.index('--procesos'):args.index('--procesos') + 2]
    main(args[0] if args else None, fps, procs)
