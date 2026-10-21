"""¿Tienen todos los NPC de escenas la cabeza, los brazos y las piernas pegados al cuerpo en todas sus animaciones?

Con las mismas cuentas que el mod (vfx_bb.pose, la mezcla de VfxLayers): para cada NPC del catálogo, cada animación de
su modelo (sola, de principio a fin) y la pose de su escena (la de reposo con los state{} de su ~onSpawn, una sola vez
cada nombre, como ModelEngine 4), la distancia más corta entre cada extremidad y su cuerpo. Distancias exactas entre
caras (vértice-cara, arista-arista y arista que atraviesa una cara): 0 = se tocan o se cruzan; más de 0 = hay hueco.
Las pieles del panel (remapmodel y, en los dúos, changepart del segundo personaje) solo cambian la geometría de cada
hueso: se mide con la piel puesta (lo que se ve; las 21 tienen la misma geometría, se comprueba) y, con --plantilla, con
la geometría del propio modelo del pack.

Uso: python3 tools/npcs/pegado.py [pack…] [--fps N] [--azar N [--semilla S]] [--umbral PX] [--plantilla]  → la lista de huecos (en píxeles de Blockbench,
1/16 de bloque) y «todo pegado» si no hay ninguno.
"""
import json
import os
import sys
import zlib

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)
sys.path.insert(0, TOOLS)
import vfx_bb as B  # noqa: E402

RES = os.path.normpath(os.path.join(TOOLS, '..', 'src', 'main', 'resources', 'assets', 'tfclient'))
LIMBS = ('head', 'leftarm', 'rightarm', 'leftleg', 'rightleg')
NAMES = {'head': 'cabeza', 'leftarm': 'brazo izq.', 'rightarm': 'brazo der.', 'leftleg': 'pierna izq.', 'rightleg': 'pierna der.'}


def role(bone_id):
    """(«head»…«body», «» o «2» del segundo personaje), o None si no es del cuerpo."""
    n = bone_id.lower().replace('_', '')
    suf = ''
    if n.endswith('2') and n[:-1] in LIMBS + ('body',):
        n, suf = n[:-1], '2'
    return (n, suf) if n in LIMBS + ('body',) else None


# ------------------------------------------------------------------------------------------- pose (como el mod)

def extra_of(model, layers):
    """Rotación, posición y escala de cada hueso con varias animaciones a la vez, como VfxLayers.mix: [(anim, t, peso,
    override)], de abajo arriba (la primera, la de reposo)."""
    ex = {}
    for name, t, w, ov in layers:
        anim = model['anims'].get(name)
        if anim is None or w <= 0:
            continue
        at = B.anim_time(anim, t)
        if at is None:
            continue
        for tr in anim['tracks']:
            e = ex.setdefault(tr['b'], [[0.0, 0.0, 0.0], [0.0, 0.0, 0.0], [1.0, 1.0, 1.0]])
            for k, slot in (('r', 0), ('p', 1), ('s', 2)):
                if k not in tr:
                    continue
                v = B.sample(tr[k], at)
                for j in range(3):
                    now = e[slot][j]
                    if ov:
                        e[slot][j] = now + (v[j] - now) * w
                    elif slot == 2:
                        e[slot][j] = now * (1 + (v[j] - 1) * w)
                    else:
                        e[slot][j] = now + v[j] * w
    return ex


def pose(model, ex):
    """Matrices 4×4 de cada hueso (vfx_bb.pose, sin mirar a ningún lado) con la mezcla «ex»."""
    mats = []
    for i, b in enumerate(model['bones']):
        r, p, s = ex.get(i, [[0, 0, 0], [0, 0, 0], [1, 1, 1]])
        par = b['parent']
        pp = model['bones'][par]['pivot'] if par >= 0 else [0, 0, 0]
        tr = [b['pivot'][k] - pp[k] + p[k] for k in range(3)]
        rm = B.rot_matrix([b['rot'][k] + r[k] for k in range(3)])
        local = np.array([[rm[a][c] * s[c] for c in range(3)] + [tr[a]] for a in range(3)] + [[0, 0, 0, 1]], dtype=float)
        mats.append(local if par < 0 else mats[par] @ local)
    return mats


def quads_of(bone):
    """Las caras del hueso en su espacio (n×4×3)."""
    qs = [np.array(q[4:24], dtype=float).reshape(4, 5)[:, :3] for q in bone['quads']]
    return np.array(qs) if qs else np.zeros((0, 4, 3))


# ------------------------------------------------------------------------------------------- distancias exactas

def _seg_point(a, b, p):
    """Distancia de los puntos p (…×3) a los segmentos a-b (…×3)."""
    ab = b - a
    t = np.clip(np.einsum('...k,...k->...', p - a, ab) / np.maximum(np.einsum('...k,...k->...', ab, ab), 1e-12), 0, 1)
    return np.linalg.norm(a + ab * t[..., None] - p, axis=-1)


def _inside(q, x, n):
    """¿Está x (en el plano de la cara q) dentro de ella? q: …×4×3, x: …×3, n: …×3."""
    ok = np.ones(x.shape[:-1], dtype=bool)
    for i in range(4):
        e = q[..., (i + 1) % 4, :] - q[..., i, :]
        c = np.cross(e, x - q[..., i, :])
        ok &= np.einsum('...k,...k->...', c, n) >= -1e-7
    return ok


def _point_quad(p, q):
    """Distancia de cada punto p (P×3) a cada cara q (Q×4×3) → P×Q."""
    P, Q = p[:, None, :], q[None, :, :, :]
    n = np.cross(q[:, 1] - q[:, 0], q[:, 3] - q[:, 0])
    n = n / np.maximum(np.linalg.norm(n, axis=-1, keepdims=True), 1e-12)
    n = n[None]
    d = np.einsum('pqk,pqk->pq', P - Q[:, :, 0], np.broadcast_to(n, (p.shape[0],) + n.shape[1:]))
    x = P - d[..., None] * n
    inside = _inside(np.broadcast_to(Q, (p.shape[0],) + q.shape), x, np.broadcast_to(n, x.shape))
    edge = np.min([_seg_point(Q[:, :, i], Q[:, :, (i + 1) % 4], P) for i in range(4)], axis=0)
    return np.where(inside, np.abs(d), edge)


def _seg_seg(a0, a1, b0, b1):
    """Distancia entre segmentos (Ericson, «closest point of two segments»), ya emparejados (…×3)."""
    d1, d2, r = a1 - a0, b1 - b0, a0 - b0
    a = np.einsum('...k,...k->...', d1, d1)
    e = np.einsum('...k,...k->...', d2, d2)
    f = np.einsum('...k,...k->...', d2, r)
    c = np.einsum('...k,...k->...', d1, r)
    b = np.einsum('...k,...k->...', d1, d2)
    den = a * e - b * b
    s = np.where(den > 1e-12, np.clip((b * f - c * e) / np.where(den > 1e-12, den, 1), 0, 1), 0)
    t = (b * s + f) / np.maximum(e, 1e-12)
    t = np.clip(t, 0, 1)
    s = np.where(t <= 0, np.clip(-c / np.maximum(a, 1e-12), 0, 1), np.where(t >= 1, np.clip((b - c) / np.maximum(a, 1e-12), 0, 1), s))
    return np.linalg.norm(a0 + d1 * s[..., None] - (b0 + d2 * t[..., None]), axis=-1)


def _crosses(qa, qb):
    """¿Alguna arista de las caras qa atraviesa alguna cara de qb?"""
    s0 = qa.reshape(-1, 3)
    s1 = np.roll(qa, -1, axis=1).reshape(-1, 3)
    n = np.cross(qb[:, 1] - qb[:, 0], qb[:, 3] - qb[:, 0])
    n = n / np.maximum(np.linalg.norm(n, axis=-1, keepdims=True), 1e-12)
    d0 = np.einsum('sqk,qk->sq', s0[:, None] - qb[None, :, 0], n)
    d1 = np.einsum('sqk,qk->sq', s1[:, None] - qb[None, :, 0], n)
    hit = (d0 * d1 < 0)
    if not hit.any():
        return False
    t = d0 / np.where(hit, d0 - d1, 1)
    x = s0[:, None] + (s1 - s0)[:, None] * t[..., None]
    inside = _inside(np.broadcast_to(qb[None], (s0.shape[0],) + qb.shape), x, np.broadcast_to(n[None], x.shape))
    return bool((hit & inside).any())


def gap(qa, qb):
    """La distancia más corta entre dos grupos de caras (n×4×3 en el mundo); 0 si se tocan o se cruzan."""
    if len(qa) == 0 or len(qb) == 0:
        return None
    d = min(_point_quad(qa.reshape(-1, 3), qb).min(), _point_quad(qb.reshape(-1, 3), qa).min())
    if d < 1e-4:
        return 0.0
    a0, a1 = qa.reshape(-1, 3), np.roll(qa, -1, axis=1).reshape(-1, 3)
    b0, b1 = qb.reshape(-1, 3), np.roll(qb, -1, axis=1).reshape(-1, 3)
    ss = _seg_seg(a0[:, None], a1[:, None], b0[None], b1[None]).min()
    d = min(d, ss)
    if d < 1e-4 or _crosses(qa, qb) or _crosses(qb, qa):
        return 0.0
    return float(d)


# ------------------------------------------------------------------------------------------- NPC

def scene_layers(mob, model):
    """La pose de su escena: la de reposo (defaultstate idle, o «idle») y los state{} de su ~onSpawn/~onLoad (una vez cada
    nombre, como ModelEngine 4)."""
    idle, states = 'idle', []
    for x in mob['mechs']:
        if x.get('tr') not in ('onspawn', 'onload'):
            continue
        a = x.get('a') or {}
        if x['m'] == 'defaultstate' and (a.get('t') or 'idle').lower().startswith('idle') and a.get('s'):
            idle = a['s']
        if x['m'] == 'state' and a.get('s') and a.get('s') not in states and str(a.get('r', a.get('remove', 'false'))).lower() != 'true':
            states.append(a['s'])
    out = []
    if idle in model['anims']:
        out.append((idle, 1.0, False))
    for s in states:
        if s == idle or s not in model['anims']:
            continue
        out.append((s, 1.0, bool(model['anims'][s].get('ov'))))
    return out


def with_skin(model, skin, second=False):
    """El modelo con la geometría de los huesos de la piel (remapmodel; second: la del segundo personaje, changepart
    head2 ← head…), como hoja_npcs.with_skin y el mod."""
    m = json.loads(json.dumps(model))
    by = {b['id']: b for b in skin['bones']}
    for b in m['bones']:
        ro = role(b['id'])
        if ro is None or (ro[1] == '2') != second:
            continue
        src = by.get(b['id'][:-1] if second else b['id'])
        if src is not None:
            b['quads'] = src['quads']
    return m


def bbox(qs):
    if len(qs) == 0:
        return None
    p = qs.reshape(-1, 3)
    return np.round(np.concatenate([p.min(0), p.max(0)]), 3)


def check_skins(skins):
    """Pieles del panel con otra geometría que la primera (con la que se mide): debería no haber ninguna."""
    out = []
    ref = None
    for sid in skins:
        sm = json.load(open(os.path.join(RES, 'npcs', 'models', sid + '.json'), encoding='utf-8'))
        sig = {b['id']: bbox(quads_of(b)) for b in sm['bones'] if role(b['id']) and b['quads']}
        if ref is None:
            ref = sig
            continue
        for bid in set(ref) | set(sig):
            x, y = ref.get(bid), sig.get(bid)
            if x is None or y is None or np.abs(x - y).max() > 1e-3:
                out.append(f'{sid}: {bid}')
    return out


def main():
    args = sys.argv[1:]
    fps = 20
    umbral = 0.0
    if '--fps' in args:
        fps = int(args[args.index('--fps') + 1])
        del args[args.index('--fps'):args.index('--fps') + 2]
    azar, semilla = 0, ''
    if '--semilla' in args:
        semilla = args[args.index('--semilla') + 1]
        del args[args.index('--semilla'):args.index('--semilla') + 2]
    if '--azar' in args:
        azar = int(args[args.index('--azar') + 1])
        del args[args.index('--azar'):args.index('--azar') + 2]
    plantilla = '--plantilla' in args
    if plantilla:
        args.remove('--plantilla')
    if '--umbral' in args:
        umbral = float(args[args.index('--umbral') + 1])
        del args[args.index('--umbral'):args.index('--umbral') + 2]
    only = set(args)
    cat = json.load(open(os.path.join(RES, 'npcs', 'catalog.json'), encoding='utf-8'))
    problems, checked, frames, models_seen = [], 0, 0, {}
    skin_issues, skin_sets = set(), set()
    for pack in cat:
        if only and pack['id'] not in only:
            continue
        prog = json.load(open(os.path.join(RES, 'npcs', pack['id'] + '.json'), encoding='utf-8'))
        mobs = prog['mobs'] if isinstance(prog['mobs'], dict) else dict((m[0], m[1]) for m in prog['mobs'])
        skins = [o['vars']['skin'] for o in pack['options'].get('skin', []) if o.get('vars', {}).get('skin')]
        for n in pack['npcs']:
            mid = n.get('model')
            if not mid:
                continue
            checked += 1
            model = json.load(open(os.path.join(RES, 'npcs', 'models', mid + '.json'), encoding='utf-8'))
            if skins and tuple(skins) not in skin_sets:
                skin_sets.add(tuple(skins))
                skin_issues.update(check_skins(skins))
            # Como lo pone el panel: con una de las pieles (todas tienen la misma geometría: check_skins); con
            # --plantilla, la geometría del propio modelo del pack («Plantilla del pack»)
            if n.get('autoskin') and skins and not plantilla:
                sk = json.load(open(os.path.join(RES, 'npcs', 'models', skins[0] + '.json'), encoding='utf-8'))
                model = with_skin(model, sk)
                if 'skin2' in n.get('options', {}):
                    model = with_skin(model, sk, second=True)
            bodies, limbs = {}, []
            for i, b in enumerate(model['bones']):
                ro = role(b['id'])
                if ro is None or not b['quads'] or b['hidden']:
                    continue
                if ro[0] == 'body':
                    bodies[ro[1]] = i
                else:
                    limbs.append((i, ro))
            if not bodies:
                problems.append(f'{pack["id"]}/{n["mob"]}: su modelo {mid} no tiene «body»')
                continue
            local = [quads_of(b) for b in model['bones']]
            cases = []
            # su escena (y, si anda, la de andar)
            sl = scene_layers(mobs.get(n['mob'], {'mechs': []}), model)
            if sl:
                cases.append(('escena: ' + ' + '.join(x[0] for x in sl), sl))
            if mid not in models_seen:
                for an in model['anims']:
                    cases.append((an, [(an, 1.0, bool(model['anims'][an].get('ov')))]))
            models_seen[mid] = True
            for label, layers in cases:
                ln = max(model['anims'][a]['len'] for a, _, _ in layers) or 0
                steps = max(1, int(round(ln * fps)) + 1)
                worst = {}
                ts = [ln * k / max(1, steps - 1) for k in range(steps)]
                if azar:
                    # instantes al azar (siempre los mismos): lo que quede entre los fotogramas de cualquier rejilla
                    rng = np.random.default_rng(zlib.crc32((mid + label + semilla).encode()))
                    ts += sorted(rng.uniform(0, ln, azar).tolist()) if ln > 0 else []
                for t in ts:
                    mats = pose(model, extra_of(model, [(a, t, w, ov) for a, w, ov in layers]))
                    frames += 1
                    world = lambda i: (local[i] @ mats[i][:3, :3].T) + mats[i][:3, 3]  # noqa: E731
                    for li, (limb, suf) in limbs:
                        bi = bodies.get(suf)
                        if bi is None:
                            continue
                        g = gap(world(li), world(bi))
                        if g is not None and g > umbral and g > worst.get((limb, suf), (0, 0))[0]:
                            worst[(limb, suf)] = (g, t)
                for (limb, suf), (g, t) in sorted(worst.items()):
                    who = NAMES[limb] + (' (2.º)' if suf else '')
                    problems.append(f'{pack["id"]}/{n["mob"]} [{mid.split(".")[-1]}] {label}: {who} despegado {g:.3f} px a los {t:.2f} s')
    for p in problems:
        print(p)
    for s in sorted(skin_issues):
        print('piel distinta:', s)
    print(f'{checked} NPC, {len(models_seen)} modelos, {frames} poses medidas')
    print('todo pegado' if not problems and not skin_issues else f'{len(problems)} huecos, {len(skin_issues)} pieles con otra geometría')


if __name__ == '__main__':
    main()
