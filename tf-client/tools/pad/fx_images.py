"""Imágenes de los efectos para la app Efectos del pad, sacadas de las de la web:

    python3 tools/pad/fx_images.py

De dónde salen (carpeta tierras-fantasticas/ del repo, la web):
- config/vfx.json: los efectos de kill (id, nombre, descripción, categoría con su color) y su imagen de tarjeta,
  public/img/vfx/kill_<id>.webp (512x512, el zombi con el efecto puesto, fondo transparente).
- config/skills.json: las clases de skills (id, papel, color, grupo) y su icono, public/img/skills/<clase>/<skill>.png
  (32x32 o 64x64, pixel art).

Qué escribe (src/main/resources/assets/tfclient/textures/gui/pad/fx/):
- kill_<id>.png: 144x144 (más o menos lo que mide la imagen de una tarjeta de la galería, 50 unidades, a 1080p). Se
  recorta lo que no es transparente, se centra en un cuadrado con un poco de aire y se reduce con Lanczos (es un render
  3D: el pad lo dibuja suavizado). Una sola imagen por efecto (la de la tarjeta de la web); el pad la anima (se mece,
  rayos de luz detrás de la equipada).
- class_<id>.png: 64x64, el icono de la clase al doble sin suavizar si era de 32 (es pixel art: el pad lo dibuja nítido).
- meta.json: {"kills": {id: {name, desc, cat, color, order}}, "classes": {id: {name, role, group, color, desc}}}. Lo
  lee PadWardrobe (servidor) para el color de cada tarjeta, su categoría (order: la de la web, para agruparlas) y su
  descripción.
Solo los efectos de kill que trae el mod (assets/tfclient/vfx/catalog.json) y las clases de assets/tfclient/skills/.
Los iconos de cada skill ya están en el mod (textures/gui/skills/<clase>/<skill>.png): esos no se tocan.
"""
import json
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))                       # tf-client/
WEB = os.path.normpath(os.path.join(ROOT, '..', 'tierras-fantasticas'))      # la web
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'tfclient')
OUT = os.path.join(ASSETS, 'textures', 'gui', 'pad', 'fx')
KILL_PX, CLASS_PX = 144, 64


def hexcolor(s, default=0x8C9BB4):
    try:
        return int(s.lstrip('#'), 16)
    except (AttributeError, ValueError):
        return default


def kill_image(src):
    """El efecto recortado a lo que se ve, centrado en un cuadrado (8 % de aire) y reducido a 96x96."""
    im = Image.open(src).convert('RGBA')
    box = im.getchannel('A').point(lambda a: 255 if a > 8 else 0).getbbox() or (0, 0, im.width, im.height)
    crop = im.crop(box)
    side = int(max(crop.width, crop.height) * 1.08)
    sq = Image.new('RGBA', (side, side), (0, 0, 0, 0))
    sq.alpha_composite(crop, ((side - crop.width) // 2, (side - crop.height) // 2))
    out = sq.resize((KILL_PX, KILL_PX), Image.LANCZOS)
    # sin restos casi invisibles en el borde (se verían como un halo al suavizar)
    a = out.getchannel('A').point(lambda v: 0 if v < 6 else v)
    out.putalpha(a)
    return out


def class_image(src):
    im = Image.open(src).convert('RGBA')
    if im.width != CLASS_PX:
        im = im.resize((CLASS_PX, CLASS_PX), Image.NEAREST if im.width < CLASS_PX else Image.LANCZOS)
    return im


def build():
    if not os.path.isdir(WEB):
        sys.exit('No encuentro la web en ' + WEB)
    os.makedirs(OUT, exist_ok=True)
    vfx = json.load(open(os.path.join(WEB, 'config', 'vfx.json'), encoding='utf-8'))
    skills = json.load(open(os.path.join(WEB, 'config', 'skills.json'), encoding='utf-8'))
    in_mod = {k['id'] for k in json.load(open(os.path.join(ASSETS, 'vfx', 'catalog.json'), encoding='utf-8'))['kills']}
    cats = {c['id']: c for c in vfx.get('cats', [])}
    groups = {g['id']: g for g in skills.get('groups', [])}
    meta = {'kills': {}, 'classes': {}}
    for k in vfx['kills']:
        if k['id'] not in in_mod:
            continue
        src = os.path.join(WEB, 'public', k.get('image', f"img/vfx/kill_{k['id']}.webp"))
        if not os.path.exists(src):
            print('falta', src)
            continue
        kill_image(src).save(os.path.join(OUT, f"kill_{k['id']}.png"), optimize=True)
        cat = cats.get(k.get('cat'), {})
        order = list(cats).index(k['cat']) if k.get('cat') in cats else len(cats)
        meta['kills'][k['id']] = {'name': k['name'], 'desc': k.get('desc', ''), 'cat': cat.get('name', ''),
                                  'color': hexcolor(cat.get('color')), 'order': order}
    mod_classes = {f[:-5] for f in os.listdir(os.path.join(ASSETS, 'skills', 'classes')) if f.endswith('.json')}
    for c in skills['classes']:
        if c['id'] not in mod_classes:
            continue
        src = os.path.join(WEB, 'public', c.get('icon', ''))
        if not c.get('icon') or not os.path.exists(src):
            print('falta', src)
            continue
        class_image(src).save(os.path.join(OUT, f"class_{c['id']}.png"), optimize=True)
        grp = groups.get(c.get('group'), {})
        meta['classes'][c['id']] = {'name': c['name'], 'role': c.get('role', ''), 'group': grp.get('name', ''),
                                    'color': hexcolor(c.get('color')), 'desc': c.get('desc', '')}
    with open(os.path.join(OUT, 'meta.json'), 'w', encoding='utf-8') as f:
        json.dump(meta, f, ensure_ascii=False, indent=1, sort_keys=True)
    return meta


if __name__ == '__main__':
    m = build()
    size = sum(os.path.getsize(os.path.join(OUT, f)) for f in os.listdir(OUT))
    print('ok', len(m['kills']), 'efectos,', len(m['classes']), 'clases,', size // 1024, 'KB')
