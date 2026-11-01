"""(1.3.164, el dueño: «el modelo ifrit tiene esas líneas negras en algunos de sus modelos, revisa todo el set») Quita las
caras de canto de los planos sin grosor (alas, fuego, chispas: elementos de menos de RAYA_MAX píxeles de grueso). Esas
caras miden 0,06 píxeles y cogen un solo texel de la textura: dibujan una raya oscura alrededor del plano (un marco
negro alrededor de la espada o la lanza, en el juego y en el probador de la web). Las caras grandes del plano se quedan.

    python3 tools/quitar_rayas.py [set ...]      (sin sets: todos)

Repasa los modelos del mod (models/item), los de la web (public/models) y los del probador (public/wear). Repetirlo no
cambia nada. build_mod_items.py lo hace también al generar (strip_edge_faces).
"""
import glob
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.normpath(os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'tfclient', 'models', 'item'))
WEB = os.path.normpath(os.path.join(HERE, '..', '..', 'tierras-fantasticas', 'public'))
RAYA_MAX = 0.1
PUNTO_MAX = 0.5   # planos algo más gruesos: se quitan sus cantos si cogen un solo texel (la raya del fuego del Ifrit)
SIDES = {0: ('north', 'south', 'up', 'down'), 1: ('north', 'south', 'east', 'west'), 2: ('east', 'west', 'up', 'down')}


def strip_edge_faces(elements):
    n = 0
    for el in elements or []:
        f, t = el.get('from'), el.get('to')
        faces = el.get('faces')
        if not f or not t or not faces:
            continue
        d = [abs(t[k] - f[k]) for k in range(3)]
        thin = [k for k in range(3) if d[k] < PUNTO_MAX]
        if len(thin) != 1:
            continue
        k = thin[0]
        for name in SIDES[k]:
            fd = faces.get(name)
            if fd is None:
                continue
            uv = fd.get('uv') or []
            point = len(uv) == 4 and abs(uv[2] - uv[0]) < 1e-6 and abs(uv[3] - uv[1]) < 1e-6
            # sin grosor: siempre; un poco más gruesos (hasta PUNTO_MAX): si la cara coge un solo texel
            if d[k] < RAYA_MAX or point:
                del faces[name]
                n += 1
    return n


def walk(obj):
    """Todas las listas «elements» de un JSON (los del probador llevan un modelo por objeto)."""
    n = 0
    if isinstance(obj, dict):
        if isinstance(obj.get('elements'), list):
            n += strip_edge_faces(obj['elements'])
        for v in obj.values():
            if isinstance(v, (dict, list)) and v is not obj.get('elements'):
                n += walk(v)
    elif isinstance(obj, list):
        for v in obj:
            n += walk(v)
    return n


def fix_file(path, compact):
    data = json.load(open(path, encoding='utf-8'))
    n = walk(data)
    if n:
        with open(path, 'w', encoding='utf-8') as fh:
            if compact:
                json.dump(data, fh, separators=(',', ':'), ensure_ascii=False)
            else:
                json.dump(data, fh, ensure_ascii=False)
    return n


def main(sets):
    total = 0
    files = []
    if sets:
        for s in sets:
            files += glob.glob(os.path.join(MOD, f'{s}_*.json')) + glob.glob(os.path.join(MOD, 'sets', s, '*.json'))
            files += glob.glob(os.path.join(WEB, 'models', s, '*.json')) + [os.path.join(WEB, 'wear', s + '.json')]
    else:
        files = glob.glob(os.path.join(MOD, '**', '*.json'), recursive=True)
        files += glob.glob(os.path.join(WEB, 'models', '*', '*.json')) + glob.glob(os.path.join(WEB, 'wear', '*.json'))
    for f in files:
        if os.path.exists(f):
            n = fix_file(f, True)
            if n:
                total += n
    print(f'{total} caras de canto quitadas en {len(files)} modelos')


if __name__ == '__main__':
    main(sys.argv[1:])
