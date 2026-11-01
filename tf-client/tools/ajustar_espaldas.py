"""(1.3.165, el dueño, con capturas del probador de la web: «hay varias con las alas malas en la espalda», «algunas alas o
mochilas están separadas del cuerpo») Coloca lo de la espalda (alas, mochilas) de los sets que se le pidan con tres
reglas, medidas sobre lo que se ve del modelo puesto (check_backs / build_mod_items.back_points):

1. Nada que se vea atravesando: dentro de los brazos (0,25 < |x| < 0,55, de la cintura al cuello, hasta la espalda) o
   saliendo por el pecho (|x| < 0,25, por delante del pecho). Lo que queda escondido dentro del torso (las correas de
   las mochilas, la raíz de las alas) no cuenta. Si más de PEN_MAX queda así, se echa hacia atrás lo justo.
2. Pegado a la espalda: lo más cercano del torso (cuantil 15 de |x| < 0,3) a menos de GAP_MAX; si no, se acerca, pero
   nunca tanto que vuelva a meterse en el cuerpo (la regla 1 manda).
3. Mochilas: el bulto (lo que va detrás de la espalda, |x| < 0,35) centrado en la espalda (BAG_Y desde el cuello), no
   colgando de la cadera. Alas: su raíz (la parte pegada a la espalda) entre ROOT_LO y ROOT_HI.

    python3 tools/ajustar_espaldas.py [--medir] set [set ...]
Después hay que volver a exportar el probador (tierras-fantasticas/tools/wear_export.py).
"""
import json
import os
import sys

import build_mod_items as M
import check_backs as CB

BACK = 0.125         # la espalda (bloques desde el centro del cuerpo)
PEN_MAX = 0.01       # parte de lo que se ve que se tolera dentro del cuerpo o de los brazos
GAP_MAX = 0.04
BAG_Y = -0.40        # centro de la mochila (bloques desde el cuello)
ROOT_LO, ROOT_HI = -0.45, -0.22


def bad(p):
    """Se ve atravesando el cuerpo: en los brazos o por delante del pecho."""
    if not (-0.80 < p[1] < 0.05):
        return False
    if abs(p[0]) < 0.25:
        return p[2] < -BACK - 0.01   # correas apoyadas en el pecho (a -0,12) no cuentan
    return abs(p[0]) < 0.55 and p[2] < BACK


def measure(pts, wts):
    W = sum(wts) or 1
    inside = sum(w for p, w in zip(pts, wts) if bad(p)) / W
    torso = [(p, w) for p, w in zip(pts, wts) if abs(p[0]) <= 0.3 and -0.85 <= p[1] <= 0.15]
    gap = M._wquantile([p[2] - BACK for p, _ in torso], [w for _, w in torso], 0.15) if len(torso) > 12 else None
    return inside, gap


def shift_z_out(pts, wts):
    """Lo mínimo que hay que echar hacia atrás para que dentro quede como mucho PEN_MAX."""
    W = sum(wts)
    # lo que hay que mover cada punto malo para que deje de serlo (al pecho: hasta -0,115; a los brazos: hasta la espalda)
    need = sorted(((-BACK - 0.01 - p[2]) if abs(p[0]) < 0.25 else (BACK - p[2]) + 0.005, w)
                  for p, w in zip(pts, wts) if bad(p))
    if not need or sum(w for _, w in need) / W <= PEN_MAX:
        return 0.0
    # mover lo justo para que lo que siga mal no pase de PEN_MAX
    acc = sum(w for _, w in need)
    for d, w in need:
        acc -= w
        if acc / W <= PEN_MAX:
            return max(0.0, d)
    return need[-1][0]


def apply(model, dz=0.0, dy=0.0):
    head = model['display']['head']
    t = head['translation']
    head["translation"] = [t[0], round(t[1] + dy / 0.625 * 16, 3), round(t[2] + dz / 0.625 * 16, 3)]


def fix(model, kind, alpha_of):
    notes = []
    pts, wts = M.back_points(model, alpha_of)
    if not pts:
        return notes
    # 3. altura
    if kind == 'backpack':
        bag = [(p[1], w) for p, w in zip(pts, wts) if abs(p[0]) < 0.35 and p[2] > BACK + 0.05]
        if bag:
            y50 = M._wquantile([b[0] for b in bag], [b[1] for b in bag], 0.5)
            dy = BAG_Y - y50
            if abs(dy) > 0.03:
                apply(model, dy=dy)
                notes.append(f'mochila centrada en la espalda (y {dy:+.2f})')
    else:
        torso = [(p, w) for p, w in zip(pts, wts) if abs(p[0]) <= 0.3]
        if torso:
            zc = M._wquantile([p[2] for p, _ in torso], [w for _, w in torso], 0.15)
            root = [(p[1], w) for p, w in torso if p[2] <= zc + 0.08]
            ry = M._wquantile([r[0] for r in root], [r[1] for r in root], 0.5)
            target = min(ROOT_HI, max(ROOT_LO, ry))
            if abs(target - ry) > 0.03:
                apply(model, dy=target - ry)
                notes.append(f'raíz de las alas a {target:+.2f} (y {target - ry:+.2f})')
    pts, wts = M.back_points(model, alpha_of)
    # 1. fuera del cuerpo
    out = shift_z_out(pts, wts)
    if out > 0.005:
        apply(model, dz=out)
        notes.append(f'fuera del cuerpo (z +{out:.2f})')
    pts, wts = M.back_points(model, alpha_of)
    # 2. pegado (sin volver a entrar)
    _, gap = measure(pts, wts)
    if gap is not None and gap > GAP_MAX:
        arms = [p[2] for p in pts if 0.25 <= abs(p[0]) < 0.55 and -0.80 < p[1] < 0.05]
        zmin = min(arms) if arms else BACK + 1
        step = min(gap - GAP_MAX / 2, max(0.0, zmin - BACK - 0.005))
        if step > 0.005:
            apply(model, dz=-step)
            notes.append(f'pegado a la espalda (z -{step:.2f})')
    return notes


def main(args):
    only_measure = '--medir' in args
    wanted = [a for a in args if not a.startswith('--')]
    alpha_of = CB.alpha_of_factory()
    sets = json.load(open(os.path.join(M.ASSETS, 'tf_sets.json'), encoding='utf-8'))['sets']
    for s in sets:
        if wanted and s['id'] not in wanted:
            continue
        for item in s['items']:
            if item['type'] != 'back':
                continue
            worn = item.get('worn')
            path = os.path.join(M.ASSETS, 'models', worn.split(':', 1)[1] + '.json') if worn else \
                os.path.join(M.ASSETS, 'models', 'item', item['id'] + '.json')
            model = json.load(open(path, encoding='utf-8'))
            kind = 'backpack' if item['id'].endswith('backpack') else 'wings'
            before = measure(*M.back_points(model, alpha_of))
            notes = [] if only_measure else fix(model, kind, alpha_of)
            after = measure(*M.back_points(model, alpha_of))
            print(f"{item['id']:24s} dentro {before[0]:5.1%} → {after[0]:5.1%}  hueco {before[1]:+.3f} → {after[1]:+.3f}  {'; '.join(notes)}")
            if notes:
                with open(path, 'w', encoding='utf-8') as fh:
                    json.dump(model, fh, separators=(',', ':'))


if __name__ == '__main__':
    main(sys.argv[1:])
