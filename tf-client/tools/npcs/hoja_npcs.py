"""Hoja de contacto de los NPC de escenas: cada NPC del catálogo con su animación de escena (la de su defaultstate
idle, o «idle»), dibujado con vfx_render (las mismas cuentas que el mod), para revisar texturas, UV y poses sin abrir el
juego. Con las pieles que les pone el panel al colocarlos (autoskin: la piel según su orden, como remapmodel).
Uso: python3 tools/npcs/hoja_npcs.py <carpeta de salida> [pack…]  → <pack>.png por pack."""
import json
import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)
sys.path.insert(0, TOOLS)
import vfx_render as R  # noqa: E402

RES = os.path.normpath(os.path.join(TOOLS, '..', 'src', 'main', 'resources', 'assets', 'tfclient'))
CELL = 300


def with_skin(model, tex, skin, skin_tex, suffix=''):
    """remapmodel (o, con suffix «2», el changepart del segundo personaje): los huesos del mismo nombre, con los de la
    piel (su geometría es relativa a su hueso, como en el mod)."""
    m = json.loads(json.dumps(model))
    off = len(tex)
    m['tex'] = m['tex'] + skin['tex']
    by = {b['id']: b for b in skin['bones']}
    for b in m['bones']:
        src = by.get(b['id'][:-len(suffix)] if suffix and b['id'].endswith(suffix) else (None if suffix else b['id']))
        if src is None:
            continue
        quads = []
        for q in src['quads']:
            q = list(q)
            if q[0] >= 0:
                q[0] = q[0] + off
            quads.append(q)
        b['quads'] = quads
    return m, tex + skin_tex


def main():
    out = sys.argv[1]
    only = set(sys.argv[2:])
    os.makedirs(out, exist_ok=True)
    cat = json.load(open(os.path.join(RES, 'npcs', 'catalog.json'), encoding='utf-8'))
    for pack in cat:
        if only and pack['id'] not in only:
            continue
        npcs = [n for n in pack['npcs'] if n.get('model')]
        cols = 5
        rows = (len(npcs) + cols - 1) // cols
        sheet = Image.new('RGB', (cols * CELL, rows * (CELL + 18)), (120, 150, 120))
        d = ImageDraw.Draw(sheet)
        for i, n in enumerate(npcs):
            mid = n['model']
            model = json.load(open(os.path.join(RES, 'npcs', 'models', mid + '.json'), encoding='utf-8'))
            tex = R.load_textures(model, os.path.join(RES, 'textures', 'npcs', pack['id'], 'me'))
            anim = n.get('state') or 'idle'
            if n.get('autoskin'):
                by_id = {o['id']: o['ops'][0][1] for o in pack['options'].get('skin', [])}
                skins = [by_id[k] for k in n['autoskin'] if by_id.get(k)]
                for suffix, k in (('', i), ('2', i + 3)):
                    if not skins or (suffix and 'skin2' not in n.get('options', {})):
                        continue
                    sid = skins[k % len(skins)]
                    sm = json.load(open(os.path.join(RES, 'npcs', 'models', sid + '.json'), encoding='utf-8'))
                    st = R.load_textures(sm, os.path.join(RES, 'textures', 'npcs', sid.split('.')[0], 'me'))
                    model, tex = with_skin(model, tex, sm, st, suffix)
            if anim not in model['anims']:
                anim = 'idle' if 'idle' in model['anims'] else None
            try:
                times = [0.0, 0.5, 1.0, 1.5]
                lo, hi = R.bounds(model, anim, times)
                center = (lo + hi) / 2
                dist = max(1.2, float((hi - lo).max()) * 1.9)
                img = R.render(model, tex, anim, 0.8, size=CELL, yaw=-25, pitch=-12, dist=dist, center=center, fov=40)
                bg = Image.new('RGBA', img.size, (120, 150, 120, 255))
                bg.alpha_composite(img)
                sheet.paste(bg.convert('RGB'), ((i % cols) * CELL, (i // cols) * (CELL + 18)))
            except Exception as e:  # noqa: BLE001
                d.text(((i % cols) * CELL + 6, (i // cols) * (CELL + 18) + 40), f'ERROR {e}'[:60], fill=(255, 40, 40))
            d.text(((i % cols) * CELL + 4, (i // cols) * (CELL + 18) + CELL + 2), f"{n['name']} [{anim}]"[:48], fill=(0, 0, 0))
        sheet.save(os.path.join(out, pack['id'] + '.png'))
        print(pack['id'], len(npcs))


if __name__ == '__main__':
    main()
