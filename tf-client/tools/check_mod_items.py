"""Revisa los modelos, texturas y datos que genera build_mod_items.py con las mismas reglas que Minecraft 1.20.1.

Uso: python3 tools/check_mod_items.py   (sale con error si encuentra algo)
"""
import glob
import json
import os
import re
import sys

from PIL import Image

ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'tfclient')
VALID = re.compile(r'^[a-z0-9_./-]+$')
DISPLAY = {'thirdperson_righthand', 'thirdperson_lefthand', 'firstperson_righthand', 'firstperson_lefthand', 'gui', 'head',
           'ground', 'fixed'}


def main():
    os.chdir(ASSETS)
    errs = []

    def tex_exists(ref):
        ns, path = ref.split(':', 1)
        return ns == 'minecraft' or os.path.exists(f'textures/{path}.png')

    def model_exists(ref):
        ns, path = ref.split(':', 1)
        return ns == 'minecraft' or os.path.exists(f'models/{path}.json')

    models = glob.glob('models/item/**/*.json', recursive=True)
    for f in models:
        if not VALID.match(f[len('models/'):]):
            errs.append(('nombre', f))
        m = json.load(open(f))
        tx = m.get('textures', {})
        for k, v in tx.items():
            if v.startswith('#'):
                if v[1:] not in tx:
                    errs.append(('textura #', f, k, v))
            elif not tex_exists(v) or not VALID.match(v.split(':')[1]):
                errs.append(('textura', f, v))
        if 'parent' in m and not model_exists(m['parent']):
            errs.append(('padre', f, m['parent']))
        if not m.get('elements') and not m.get('parent'):
            errs.append(('vacío', f))
        if m.get('parent', '').endswith(('generated', 'handheld', 'handheld_rod')) and 'layer0' not in tx:
            errs.append(('layer0', f))
        for el in m.get('elements', []):
            if any(v < -16 or v > 32 for v in el['from'] + el['to']):
                errs.append(('fuera de -16..32', f))
            r = el.get('rotation')
            if r and ('origin' not in r or float(r.get('angle', 0)) not in (-45, -22.5, 0, 22.5, 45)
                      or r.get('axis') not in ('x', 'y', 'z')):
                errs.append(('giro', f, r))
            for face, fd in el['faces'].items():
                if face not in ('north', 'south', 'east', 'west', 'up', 'down') or fd.get('rotation', 0) % 90:
                    errs.append(('cara', f, face))
                if fd['texture'].lstrip('#') not in tx:
                    errs.append(('textura de cara', f, fd['texture']))
        for o in m.get('overrides', []):
            if not model_exists(o['model']):
                errs.append(('variante', f, o['model']))
        for ctx in m.get('display', {}):
            if ctx not in DISPLAY:
                errs.append(('display', f, ctx))
    for f in glob.glob('textures/**/*.mcmeta', recursive=True):
        a = json.load(open(f))['animation']
        w, h = Image.open(f[:-7]).size
        fw = a.get('width', w)
        fh = a.get('height', fw if h % fw == 0 else h)
        if h % fh or w % fw:
            errs.append(('animación', f, w, h))
        n = (w // fw) * (h // fh)
        for fr in a.get('frames', []):
            if (fr['index'] if isinstance(fr, dict) else fr) >= n:
                errs.append(('fotograma', f))
    data = json.load(open('tf_sets.json'))
    ids = [i['id'] for s in data['sets'] for i in s['items']]
    lang = json.load(open('lang/es_es.json', encoding='utf-8'))
    for i in ids:
        if not os.path.exists(f'models/item/{i}.json'):
            errs.append(('sin modelo', i))
        if f'item.tfclient.{i}' not in lang:
            errs.append(('sin nombre', i))
    if len(ids) != len(set(ids)):
        errs.append(('ids repetidos',))
    for s in data['sets']:
        for i in s['items']:
            if i.get('worn') and not model_exists(i['worn']):
                errs.append(('cosmético', i['id']))
        if any(i['type'] == 'armor' for i in s['items']):
            suffix = '_f0' if s.get('armorFrames') else ''
            if not all(os.path.exists(f"textures/models/armor/{s['id']}_layer_{n}{suffix}.png") for n in (1, 2)):
                errs.append(('capas de armadura', s['id']))
    print(f'{len(models)} modelos, {len(ids)} objetos en {len(data["sets"])} sets: {len(errs)} errores')
    for e in errs[:50]:
        print('  ', e)
    return 1 if errs else 0


if __name__ == '__main__':
    sys.exit(main())
