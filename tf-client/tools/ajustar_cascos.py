"""(1.3.164, el dueño: «la cabeza del jugador traspasa la armadura y se ve mi skin salida del casco… ajusta bien cada
modelo nuevo») Agranda los cascos y sombreros cosméticos (tipo «head») cuyas paredes quedan dentro de la segunda capa de
la skin (el «gorro», 4,5 píxeles desde el centro de la cabeza): con skins que la usan entera (el panda de Pewez777) esa
capa asomaba por fuera del casco. Se agranda desde el centro de la cabeza lo justo para que esas paredes queden por fuera
(HAT_CLEAR). Lo abierto a propósito (la cara, coronas, cuernos) no cuenta: solo las celdas de cada cara de la cabeza en
las que el casco tiene algo.

    python3 tools/ajustar_cascos.py [set ...]   (mide y ajusta; con --medir solo mide)
"""
import json
import os
import sys

import build_mod_items as M
import check_backs as CB

HAT = 0.28125        # la segunda capa de la skin (bloques desde el centro de la cabeza)
HAT_CLEAR = 0.262    # donde deben quedar como poco las paredes del casco (la cabeza: 0,25; el gorro no se dibuja con
                     # un casco cosmético, TFHeadHatClient)
WALL_MIN = 0.15      # lo que está más cerca del centro no es pared (adornos por dentro)
INSIDE_MAX = 0.06    # parte de una cara con la pared por dentro del gorro que se tolera
GROW_MAX = 1.25      # como mucho se agranda esto
C = 0.25             # el centro de la cabeza en el marco de back_points (cuello 0, coronilla 0,5)
N = 8


def walls(pts):
    """Por cada cara de la cabeza, la distancia de la pared del casco en cada celda (lo más lejano visible)."""
    out = {}
    cell = 2 * HAT / N
    for name, ax, sg in (('izq', 0, 1), ('der', 0, -1), ('detras', 2, 1), ('delante', 2, -1), ('arriba', 1, 1)):
        others = [a for a in range(3) if a != ax]
        grid = {}
        for p in pts:
            q = [p[0], p[1] - C, p[2]]
            d = sg * q[ax]
            if d < WALL_MIN:
                continue
            u = [q[a] for a in others]
            if any(abs(x) > HAT for x in u):
                continue
            key = tuple(min(N - 1, int((x + HAT) / cell)) for x in u)
            grid[key] = max(grid.get(key, 0), d)
        out[name] = grid
    return out


def needed_scale(pts):
    worst = 1.0
    for grid in walls(pts).values():
        inside = sorted(v for v in grid.values() if v < HAT_CLEAR)
        if len(inside) / (N * N) <= INSIDE_MAX:
            continue
        # que la pared más metida (sin contar las 2 más raras) quede en HAT_CLEAR
        d = inside[min(2, len(inside) - 1)]
        worst = max(worst, HAT_CLEAR / d)
    return min(worst, GROW_MAX)


def grow(model, k):
    head = model['display']['head']
    sc = head.get('scale', [1, 1, 1])
    tr = head.get('translation', [0, 0, 0])
    head['scale'] = [round(v * k, 4) for v in sc]
    # desde el centro de la cabeza: el centro del modelo (0,625·t/16 en x y z; 0,25 + 0,625·t/16 en y) se aleja k veces
    x, y, z = (0.625 * tr[0] / 16, 0.25 + 0.625 * tr[1] / 16 - C, 0.625 * tr[2] / 16)
    head['translation'] = [round(k * x / 0.625 * 16, 3), round(((C + k * y) - 0.25) / 0.625 * 16, 3), round(k * z / 0.625 * 16, 3)]


def model_path(item):
    worn = item.get('worn')
    if worn:
        return os.path.join(M.ASSETS, 'models', worn.split(':', 1)[1] + '.json')
    return os.path.join(M.ASSETS, 'models', 'item', item['id'] + '.json')


def main(args):
    measure = '--medir' in args
    wanted = [a for a in args if not a.startswith('--')]
    alpha_of = CB.alpha_of_factory()
    sets = json.load(open(os.path.join(M.ASSETS, 'tf_sets.json'), encoding='utf-8'))['sets']
    for s in sets:
        if wanted and s['id'] not in wanted:
            continue
        for item in s['items']:
            if item['type'] != 'head':
                continue
            path = model_path(item)
            if not os.path.exists(path):
                continue
            model = json.load(open(path, encoding='utf-8'))
            if not (model.get('display') or {}).get('head'):
                continue
            pts, _ = M.back_points(model, alpha_of)
            k = needed_scale(pts)
            if k <= 1.005:
                continue
            print(f"{item['id']:28s} se agranda x{k:.3f}")
            if not measure:
                grow(model, k)
                with open(path, 'w', encoding='utf-8') as fh:
                    json.dump(model, fh, separators=(',', ':'))


if __name__ == '__main__':
    main(sys.argv[1:])
