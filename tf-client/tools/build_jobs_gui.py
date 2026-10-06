"""Texturas del módulo de oficios (/tf jobs) a partir del pack «Medieval Jobs»:
  - fondos de la ventana de cada oficio (textures/gui/jobs/<oficio>.png) sin los textos en inglés del cartel y de la
    cinta: el mod escribe encima, en español, el nombre que diga config/tfclient-jobs.json
  - iconos de 16 px de cada oficio como objetos (tfclient:job_<oficio>), para los menús de cofre

Uso: python3 tools/build_jobs_gui.py <carpeta con los packs descomprimidos>
"""
import json
import os
import sys
from collections import Counter

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'tfclient')
PACK = os.path.join('Mmedival_jobs_pack_1_tab_1', 'Default Resource Pack Setup', 'Resource Pack', 'assets', 'medival_jobs',
                    'textures')

JOBS = {
    'farmer': 'Granjero', 'miner': 'Minero', 'wood_cutter': 'Leñador', 'digger': 'Excavador', 'fisherman': 'Pescador',
    'hunter': 'Cazador', 'alchemist': 'Alquimista', 'blacksmith': 'Herrero', 'builder': 'Constructor',
    'enchanter': 'Encantador',
}
EN_NAMES = {
    'farmer': 'Farmer', 'miner': 'Miner', 'wood_cutter': 'Woodcutter', 'digger': 'Digger', 'fisherman': 'Fisherman',
    'hunter': 'Hunter', 'alchemist': 'Alchemist', 'blacksmith': 'Blacksmith', 'builder': 'Builder',
    'enchanter': 'Enchanter',
}

# Zonas con texto (en píxeles de la textura de 256): el cartel «JOBS» de arriba y la cinta verde con el nombre.
SIGN = (100, 30, 158, 41)
RIBBON = (98, 92, 159, 101)
SIGN_TEXT = {(248, 207, 173)}


def erase(img, box, is_text):
    """Quita el texto de una zona: cada píxel de letra (y su sombra) toma el color de fondo de su fila."""
    x0, y0, x1, y1 = box
    px = img.load()
    mask = set()
    for y in range(y0, y1):
        for x in range(x0, x1):
            if is_text(px[x, y][:3]):
                mask.add((x, y))
                # La sombra de la letra, debajo o a la derecha
                for dx, dy in ((0, 1), (1, 1), (1, 0)):
                    mask.add((x + dx, y + dy))
    for y in range(y0, y1 + 1):
        row = Counter(px[x, y] for x in range(x0, x1) if (x, y) not in mask)
        if not row:
            continue
        fill = row.most_common(1)[0][0]
        for x in range(x0, x1):
            if (x, y) in mask:
                px[x, y] = fill


def ribbon_text(rgb):
    r, g, b = rgb
    return r > 180 and g > 200 and b > 200  # letras blancas sobre la cinta verde


def main(packs):
    src = os.path.join(packs, PACK)
    gui = os.path.join(ASSETS, 'textures', 'gui', 'jobs')
    icons = os.path.join(ASSETS, 'textures', 'item', 'jobs')
    models = os.path.join(ASSETS, 'models', 'item')
    for d in (gui, icons, models):
        os.makedirs(d, exist_ok=True)
    lang = {}
    for job, name in JOBS.items():
        img = Image.open(os.path.join(src, f'{job}.png')).convert('RGBA')
        erase(img, SIGN, lambda rgb: rgb in SIGN_TEXT)
        erase(img, RIBBON, ribbon_text)
        img.save(os.path.join(gui, f'{job}.png'), optimize=True)
        Image.open(os.path.join(src, 'icons', f'{job}.png')).convert('RGBA').save(os.path.join(icons, f'{job}.png'), optimize=True)
        with open(os.path.join(models, f'job_{job}.json'), 'w') as fh:
            json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': f'tfclient:item/jobs/{job}'}}, fh, indent=1)
        lang[f'item.tfclient.job_{job}'] = name
    for lang_file, names in (('es_es.json', JOBS), ('en_us.json', EN_NAMES)):
        path = os.path.join(ASSETS, 'lang', lang_file)
        data = json.load(open(path, encoding='utf-8')) if os.path.exists(path) else {}
        for job in JOBS:
            data[f'item.tfclient.job_{job}'] = names[job]
        with open(path, 'w', encoding='utf-8') as fh:
            json.dump(data, fh, ensure_ascii=False, indent=1)
    print(f'{len(JOBS)} oficios: fondos en textures/gui/jobs, iconos tfclient:job_<oficio>')


if __name__ == '__main__':
    main(sys.argv[1])
