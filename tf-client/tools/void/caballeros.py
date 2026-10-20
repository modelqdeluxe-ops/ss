"""Los caballeros sacros del santuario del Void (TF Client 1.3.97), sin abrir el juego.

    python3 -I tools/void/caballeros.py [--ver carpeta]

El dueño: «busca skins o modelos de caballeros sacros para que pongas en ese santuario, que tenga vida, con diálogos»
(y, en la 1.3.98, «no todos los caballeros deben tener el mismo color»).
El pack de NPCs de donde salió Caelthar (NPCs For Mini Games vol. 1) está cifrado en el repo y no trae otros caballeros
a mano, así que los demás caballeros de la Orden son su mismo modelo (vacio.caballero) con la armadura de otro metal:
la textura de Caelthar (oro) se repinta por luminosidad con la rampa de cada uno (lo dorado; lo demás, igual). Las alas
son solo de Caelthar (el renderer las esconde en los demás).

Escribe, por cada caballero, textures/vacio/caballero_<metal>/<la de Caelthar>.png y su modelo
bosses/models/vacio.caballero_<metal>.json (el de Caelthar con esa textura). --ver: una hoja con todas.
"""
import colorsys
import json
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient'))
MODEL = os.path.join(RES, 'bosses', 'models', 'vacio.caballero.json')
# metal → la rampa (oscuro, medio, claro). 1.3.98 (el dueño: «no todos los caballeros deben tener el mismo color»): cada
# uno de un tono bien distinto (el bronce, el rosa y el marfil de la 1.3.97 se parecían al oro de Caelthar)
METALES = {
    'plata': ((34, 38, 52), (150, 160, 182), (242, 246, 255)),
    'azur': ((8, 16, 58), (38, 82, 196), (156, 204, 255)),
    'onice': ((12, 6, 20), (60, 38, 92), (188, 150, 240)),
    'jade': ((4, 30, 20), (26, 142, 90), (176, 255, 206)),
    'carmesi': ((36, 4, 10), (172, 28, 48), (255, 156, 150)),
}


def ramp(stops, x):
    """La rampa de tres colores en x (0..1)."""
    a, b, c = (np.array(s, float) for s in stops)
    x = x[..., None]
    lo = a + (b - a) * np.clip(x / 0.5, 0, 1)
    hi = b + (c - b) * np.clip((x - 0.5) / 0.5, 0, 1)
    return np.where(x < 0.5, lo, hi)


def repintar(img, stops):
    arr = np.asarray(img.convert('RGBA')).astype(float)
    rgb = arr[..., :3] / 255
    hsv = np.array([[colorsys.rgb_to_hsv(*px) for px in row] for row in rgb])
    h, s, v = hsv[..., 0] * 360, hsv[..., 1], hsv[..., 2]
    gold = (h >= 14) & (h <= 62) & (s > 0.22) & (arr[..., 3] > 0)
    lum = 0.299 * rgb[..., 0] + 0.587 * rgb[..., 1] + 0.114 * rgb[..., 2]
    # (la luminosidad del oro va de ~0,25 a ~0,95: se estira a 0..1 para usar toda la rampa)
    x = np.clip((lum - 0.18) / 0.78, 0, 1)
    new = ramp(stops, x)
    out = arr.copy()
    out[..., :3] = np.where(gold[..., None], new, arr[..., :3])
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), 'RGBA')


def main():
    model = json.load(open(MODEL, encoding='utf-8'))
    src = model['tex'][0]['path']          # tfclient:textures/vacio/caballero/xxx.png
    rel = src.split(':', 1)[1]
    img = Image.open(os.path.join(RES, rel))
    sheets = []
    for metal, stops in METALES.items():
        out = repintar(img, stops)
        new_rel = rel.replace('/caballero/', f'/caballero_{metal}/')
        dest = os.path.join(RES, new_rel)
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        out.save(dest)
        m = json.loads(json.dumps(model))
        m['tex'][0]['path'] = 'tfclient:' + new_rel
        with open(os.path.join(RES, 'bosses', 'models', f'vacio.caballero_{metal}.json'), 'w', encoding='utf-8') as fo:
            json.dump(m, fo, separators=(',', ':'))
        sheets.append(out)
        print(f'caballero_{metal}: {new_rel}')
    if '--ver' in sys.argv:
        folder = sys.argv[sys.argv.index('--ver') + 1]
        os.makedirs(folder, exist_ok=True)
        w, h = img.size
        sheet = Image.new('RGBA', (w * (len(sheets) + 1) * 3, h * 3), (40, 40, 40, 255))
        for i, im in enumerate([img.convert('RGBA')] + sheets):
            sheet.alpha_composite(im.resize((w * 3, h * 3), Image.NEAREST), (i * w * 3, 0))
        sheet.save(os.path.join(folder, 'caballeros.png'))


if __name__ == '__main__':
    main()
