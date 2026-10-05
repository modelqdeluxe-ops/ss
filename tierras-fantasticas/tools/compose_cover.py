"""Portada de un crate, igual para todos: las armas del set en abanico detrás y el cofre delante.

Uso: python3 tools/compose_cover.py <carpeta packs> <set> [<set> ...]
Escribe public/img/crates/<set>.webp (1200×800, fondo transparente).
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageEnhance

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_items as B  # noqa: E402

PUBLIC = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'public')
ORDER = ['bow', 'scythe', 'hammer', 'axe', 'great_sword', 'greatsword', 'big_sword', 'battle_axe', 'battleaxe', 'sword', 'spear', 'staff', 'trident',
         'mace', 'halberd', 'sickle', 'blade', 'rapier_sword', 'knife', 'pickaxe', 'crossbow', 'dagger', 'shovel', 'hoe']
NAZGUL = ['spider_bow', 'witch_scythe', 'gargoyle_axe', 'red_hammer', 'demonic_blade', 'ocean_sword', 'holy_spear',
          'abyssal_blade', 'great_sword', 'fire_mace', 'mana_axe', 'flowering_madness', 'obsidian_bow']
MAX_WEAPONS = 9
W, H = 1200, 800


def render_item(packs, set_id, name):
    items = B.SETS[set_id][2]
    full = next((n for n, _ in items if n.split('/')[-1] == name), name)
    root, ns, model = B.model_for(packs, set_id, full)
    if not model:
        return None
    texs = {k: B.texture_info(root, ns, v) for k, v in model.get('textures', {}).items()
            if k != 'particle' and isinstance(v, str) and not v.startswith('#')}
    texs = {k: v for k, v in texs.items() if v}
    if not texs or not model.get('elements'):
        return None
    img = B.render(model, texs, 640)
    box = img.getbbox()
    return img.crop(box) if box else None


def upright(im):
    """Pone vertical el eje largo, con la punta hacia arriba como en la vista de inventario."""
    a = np.asarray(im.split()[3]) > 40
    ys, xs = np.nonzero(a)
    if len(xs) < 10:
        return im
    x = xs - xs.mean()
    y = -(ys - ys.mean())
    sxx, syy, sxy = (x * x).mean(), (y * y).mean(), (x * y).mean()
    theta = 0.5 * math.atan2(2 * sxy, sxx - syy)
    if math.sin(theta) < 0:
        theta += math.pi
    im = im.rotate(90 - math.degrees(theta), resample=Image.BICUBIC, expand=True)
    return im.crop(im.getbbox())


def compose(packs, set_id):
    names = NAZGUL if set_id == 'nazgul' else ORDER
    weapons = [w for w in (render_item(packs, set_id, n) for n in names) if w is not None][:MAX_WEAPONS]
    S = 2  # se dibuja al doble y se reduce al final
    canvas = Image.new('RGBA', (W * S, H * S), (0, 0, 0, 0))
    cx, cy = W * S / 2, H * S * 1.02
    n = len(weapons)
    for i, im in enumerate(weapons):
        phi = -62 + 124 * i / max(1, n - 1)
        im = upright(im)
        scale = (H * S * 0.5) / im.height
        im = im.resize((max(1, int(im.width * scale)), int(im.height * scale)), Image.LANCZOS)
        im = im.rotate(-phi, resample=Image.BICUBIC, expand=True)
        r = H * S * 0.62
        x = cx + r * math.sin(math.radians(phi))
        y = cy - r * math.cos(math.radians(phi))
        canvas.alpha_composite(im, (int(x - im.width / 2), int(y - im.height / 2)))
    chest = render_item(packs, set_id, 'chest')
    if chest is not None:
        scale = (H * S * 0.42) / max(chest.size)
        chest = chest.resize((int(chest.width * scale), int(chest.height * scale)), Image.LANCZOS)
        canvas.alpha_composite(chest, (int(cx - chest.width / 2), int(H * S * 0.97 - chest.height)))
    # Encaje en el lienzo final, centrado y con margen
    box = canvas.getbbox()
    art = canvas.crop(box)
    fit = min((W * S * 0.96) / art.width, (H * S * 0.96) / art.height)
    art = art.resize((int(art.width * fit), int(art.height * fit)), Image.LANCZOS)
    final = Image.new('RGBA', (W * S, H * S), (0, 0, 0, 0))
    final.alpha_composite(art, ((W * S - art.width) // 2, (H * S - art.height) // 2))
    final = final.resize((W, H), Image.LANCZOS)
    rgb, alpha = final.convert('RGB'), final.split()[3]
    rgb = ImageEnhance.Brightness(rgb).enhance(1.1)
    rgb.putalpha(alpha)
    out = os.path.join(PUBLIC, 'img', 'crates', f'{set_id}.webp')
    rgb.save(out, 'WEBP', quality=90, method=6)
    print(out, n, 'armas', 'con cofre' if chest is not None else 'sin cofre')


if __name__ == '__main__':
    packs, *sets = sys.argv[1:]
    for s in sets or list(B.SETS):
        compose(packs, s)
