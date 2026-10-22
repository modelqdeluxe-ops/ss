"""Quita de los modelos de los sets las piezas que se colaron de otro objeto (1.3.112, el dueño: «en algunos modelos de
los sets, tipo el de Oni, se quedaron con un objeto de textura pegado al arma; quiero textura limpia, checa todos»).

Los packs traen algunos modelos con un trozo de otro objeto: las espadas y lanzas «left/right» de Oni llevan dentro el
cofre de la crate de Oni pegado a la empuñadura, y el casco de Bahamut un bloque suelto flotando debajo. Revisados todos
los sets (las miniaturas de la web, y los modelos con grupos de cubos que no se tocan: alas, escudos con adornos,
la cabeza del martillo de Lightning, el corazón flotante del bastón de Bonita o las runas de la daga Shadow son del
diseño), solo estos.

Para cada uno se agrupan los cubos que se tocan y se queda el grupo con el cubo más largo (la hoja o el mango del arma;
en el casco, el casco); los demás grupos se quitan. Se hace en el modelo del mod
(src/main/resources/assets/tfclient/models/item/<set>_<objeto>.json) y en el de la web
(tierras-fantasticas/public/models/<set>/<objeto>.json, el visor 3D), y se rehace la miniatura de la web.
Si se vuelven a importar los packs (build_mod_items.py / build_items.py), hay que volver a pasar este script.

Uso: python3 tools/limpiar_piezas.py
"""
import json
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'tfclient', 'models', 'item')
WEB = os.path.join(HERE, '..', '..', 'tierras-fantasticas')
sys.path.insert(0, os.path.join(WEB, 'tools'))

LIMPIAR = [('oni', 'left_sword'), ('oni', 'right_sword'), ('oni', 'left_spear'), ('oni', 'right_spear'),
           ('bahamut', 'helmet')]


def caja(e):
    f, t = np.array(e['from'], float), np.array(e['to'], float)
    pts = np.array([[x, y, z] for x in (f[0], t[0]) for y in (f[1], t[1]) for z in (f[2], t[2])])
    r = e.get('rotation')
    if r and r.get('angle'):
        o = np.array(r['origin'], float)
        a = math.radians(r['angle'])
        c, s = math.cos(a), math.sin(a)
        m = {'x': [[1, 0, 0], [0, c, -s], [0, s, c]], 'y': [[c, 0, s], [0, 1, 0], [-s, 0, c]],
             'z': [[c, -s, 0], [s, c, 0], [0, 0, 1]]}[r['axis']]
        pts = (pts - o) @ np.array(m).T + o
    return pts.min(0), pts.max(0)


def grupos(elements, tol=0.35):
    """Los cubos que se tocan (cajas con sus giros, con un margen), juntos."""
    cajas = [caja(e) for e in elements]
    padre = list(range(len(elements)))

    def raiz(i):
        while padre[i] != i:
            padre[i] = padre[padre[i]]
            i = padre[i]
        return i

    for i in range(len(cajas)):
        for j in range(i + 1, len(cajas)):
            (a0, a1), (b0, b1) = cajas[i], cajas[j]
            if np.all(a0 - tol <= b1) and np.all(b0 - tol <= a1):
                padre[raiz(i)] = raiz(j)
    out = {}
    for i in range(len(cajas)):
        out.setdefault(raiz(i), set()).add(i)
    return list(out.values()), cajas


def que_quitar(elements):
    gs, cajas = grupos(elements)
    largo = max(range(len(elements)), key=lambda i: float(np.max(cajas[i][1] - cajas[i][0])))
    return sorted(i for g in gs if largo not in g for i in g)


def main():
    import build_items as B
    assets = os.path.normpath(os.path.join(MOD, '..', '..', '..'))
    for set_id, obj in LIMPIAR:
        mod_path = os.path.join(MOD, f'{set_id}_{obj}.json')
        web_path = os.path.join(WEB, 'public', 'models', set_id, f'{obj}.json')
        m = json.load(open(mod_path))
        fuera = que_quitar(m['elements'])
        if not fuera:
            print(f'{set_id}/{obj}: ya está limpio')
            continue
        quedan = [e for i, e in enumerate(m['elements']) if i not in fuera]
        m['elements'] = quedan
        with open(mod_path, 'w') as f:
            json.dump(m, f, separators=(',', ':'))
        w = json.load(open(web_path))
        if len(w['elements']) == len(quedan) + len(fuera):
            w['elements'] = [e for i, e in enumerate(w['elements']) if i not in fuera]
            with open(web_path, 'w') as f:
                json.dump(w, f, separators=(',', ':'))
        else:
            print(f'  ¡ojo! la web tiene {len(w["elements"])} cubos y el mod {len(quedan) + len(fuera)}: la web, sin tocar')
        texs = {}
        for k, ref in m.get('textures', {}).items():
            if not ref.startswith('#'):
                info = B.texture_info(assets, 'tfclient', ref)
                if info:
                    texs[k] = info
        B.finish_thumb(B.render(m, texs, B.THUMB), B.THUMB, os.path.join(WEB, 'public', 'img', 'items', set_id, f'{obj}.webp'))
        print(f'{set_id}/{obj}: fuera {len(fuera)} cubos (quedan {len(quedan)})')


if __name__ == '__main__':
    main()
