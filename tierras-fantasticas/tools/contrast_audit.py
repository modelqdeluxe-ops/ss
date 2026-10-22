"""Mide el contraste (WCAG 2.2) de cada texto de la web contra el fondo REAL que tiene detrás.

Uso (con la vista previa encendida: node tools/preview.mjs):
  python3 tools/contrast_audit.py [carpeta] [ruta ...]
Lanza tools/contrast_audit.cjs (capturas + textos con su color), y para cada texto toma los píxeles de un marco
alrededor de él (sin el texto) como fondo: sirve sobre degradados, imágenes y botones. Exige 4.5:1 (3:1 si el texto es
grande: 24 px, o 18.66 px en negrita). Imprime los que fallan, del peor al mejor, y el resumen.
"""
import json
import os
import subprocess
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
PAGES = ['/', '/tienda', '/tienda#rangos', '/tienda#cosmeticos', '/tienda#vfx', '/tienda#skills', '/mundo', '/ayuda',
         '/cuenta', '/legal', '/no-existe']


def lum(c):
    def ch(v):
        v /= 255
        return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b = c[:3]
    return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)


def ratio(a, b):
    la, lb = sorted((lum(a), lum(b)), reverse=True)
    return (la + 0.05) / (lb + 0.05)


def parse(css):
    inner = css[css.index('(') + 1:css.index(')')]
    if css.startswith('color(srgb'):  # lo que devuelve color-mix(): color(srgb r g b) con valores de 0 a 1
        return tuple(round(float(x) * 255) for x in inner.split()[1:4])
    v = inner.replace('/', ',').split(',')
    return tuple(int(float(x)) for x in v[:3])


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else '/tmp/contraste'
    pages = sys.argv[2:] or PAGES
    os.makedirs(out, exist_ok=True)
    env = dict(os.environ)
    if not os.environ.get('SIN_CAPTURAS'):
        subprocess.run(['node', os.path.join(HERE, 'contrast_audit.cjs'), out, *pages], check=True, env=env)
    fails, total = [], 0
    for f in sorted(os.listdir(out)):
        if not f.endswith('.json'):
            continue
        img = Image.open(os.path.join(out, f[:-5] + '.png')).convert('RGB')
        W, H = img.size
        for it in json.load(open(os.path.join(out, f))):
            x0, y0 = int(it["x"]) - 1, int(it["y"]) - 1
            x1, y1 = int(it["x"] + it["w"]) + 1, int(it["y"] + it["h"]) + 1
            if x0 < 0 or y0 < 0 or x1 >= W or y1 >= H:
                continue
            ring = [img.getpixel((x, y0)) for x in range(x0, x1, 2)] + [img.getpixel((x, y1)) for x in range(x0, x1, 2)] + \
                   [img.getpixel((x0, y)) for y in range(y0, y1, 2)] + [img.getpixel((x1, y)) for y in range(y0, y1, 2)]
            fg = parse(it['color'])
            # El peor caso del fondo (WCAG: medir donde menos contraste hay), descartando el 10 % más extremo
            rs = sorted(ratio(fg, p) for p in ring)
            worst = rs[len(rs) // 10]
            large = it['size'] >= 24 or (it['size'] >= 18.66 and it['weight'] >= 700)
            need = 3 if large else 4.5
            total += 1
            if worst < need:
                fails.append((worst, need, f[:-5], it['text'], it['cls'], it['color']))
    fails.sort()
    for w, need, page, text, cls, color in fails:
        print(f'{w:4.2f} < {need}  {page:28} «{text}»  [{cls}] {color}')
    print(f'\n{len(fails)} de {total} textos por debajo del mínimo')


if __name__ == '__main__':
    main()
