"""Mide cómo quedan puestos los cosméticos de espalda del mod (alas, mochilas, capas...) y, con --fix, los acerca.

Lee los modelos ya convertidos del mod (no hacen falta los packs), calcula lo que se ve (píxeles no transparentes)
con la posición de «display.head» y mide la parte central (la que va pegada a la columna): debe quedar justo detrás
de la espalda del jugador (build_mod_items.back_gap). Si queda separada, --fix corrige solo la translación en z
(lo mismo que hace build_mod_items.py al generar).

Uso: python3 tools/check_backs.py [--fix] [set ...]
"""
import json
import os
import sys

from PIL import Image

import build_mod_items as M

ASSETS = M.ASSETS
def alpha_of_factory():
    cache = {}

    def alpha_of(ref):
        if ref in cache:
            return cache[ref]
        alpha = None
        if ref.startswith('tfclient:'):
            path = os.path.join(ASSETS, 'textures', ref.split(':', 1)[1] + '.png')
            if os.path.exists(path):
                img = Image.open(path).convert('RGBA')
                w, h = img.size
                fw, fh = w, h
                meta = path + '.mcmeta'
                if os.path.exists(meta):
                    anim = json.load(open(meta)).get('animation', {})
                    fw = anim.get('width', w)
                    fh = anim.get('height', fw if h > w and h % w == 0 else h)
                elif h > w and h % w == 0:
                    fh = w
                a = img.crop((0, 0, fw, fh)).split()[3]
                alpha = [list(a.crop((0, y, fw, y + 1)).getdata()) for y in range(fh)]
        cache[ref] = alpha
        return alpha
    return alpha_of


def main(args):
    fix = '--fix' in args
    only = [a for a in args if not a.startswith('--')]
    registry = json.load(open(os.path.join(ASSETS, 'tf_sets.json'), encoding='utf-8'))
    alpha_of = alpha_of_factory()
    bad = 0
    for s in registry['sets']:
        if only and s['id'] not in only:
            continue
        for item in s['items']:
            if item['type'] != 'back' or not item.get('worn'):
                continue
            path = os.path.join(ASSETS, 'models', item['worn'].split(':', 1)[1] + '.json')
            model = json.load(open(path, encoding='utf-8'))
            m = M.back_gap(model, alpha_of)
            if not m:
                print(f'{item["id"]:40s} SIN NADA VISIBLE')
                bad += 1
                continue
            ok = m['gap'] <= M.BACK_GAP_LIMIT
            note = 'ok' if ok else 'SEPARADO'
            print(f'{item["id"]:40s} separación {m["gap"]:+.3f}  arriba {m["top"]:+.2f}  abajo {m["bottom"]:+.2f}  {note}')
            if not ok:
                bad += 1
                if fix:
                    dz = M.snap_to_back(model, alpha_of)
                    with open(path, 'w') as fh:
                        json.dump(model, fh, separators=(',', ':'))
                    print(f'{"":40s} → corregido (z {dz:+.2f})')
    print(f'{bad} fuera de rango' + (' (corregidos)' if fix and bad else ''))


if __name__ == '__main__':
    main(sys.argv[1:])
