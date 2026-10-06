"""Exporta para el probador de la web (public/wardrobe.js) los objetos de cada set tal como los usa el TF Client.

Lee los modelos ya convertidos del mod (tf-client/src/main/resources), que traen las correcciones de texturas y la
posición real de cada pieza en el jugador (en la mano, en la cabeza, en la espalda), y escribe:
  public/wear/<set>.json        objetos del set: elementos, texturas y posición en el jugador
  public/wear/<set>/*.png       sus texturas (las animadas, en tira vertical de fotogramas)

Uso: python3 tools/wear_export.py   (después de tf-client/tools/build_mod_items.py)
"""
import json
import os
import re
import shutil

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
PUBLIC = os.path.join(HERE, '..', 'public')
MOD = os.path.join(HERE, '..', '..', 'tf-client', 'src', 'main', 'resources', 'assets', 'tfclient')
OUT = os.path.join(PUBLIC, 'wear')

DISPLAY = ('thirdperson_righthand', 'head', 'gui')


def mod_path(ref, kind):
    ns, path = ref.split(':', 1) if ':' in ref else ('tfclient', ref)
    if ns != 'tfclient':
        return None
    return os.path.join(MOD, kind, path + ('.json' if kind == 'models' else '.png'))


def load_model(ref, depth=0):
    """Modelo del mod con sus padres resueltos. Los objetos planos (item/generated) quedan marcados como tales."""
    path = mod_path(ref, 'models')
    if not path or not os.path.exists(path):
        return None
    m = json.load(open(path, encoding='utf-8'))
    parent = m.get('parent')
    if parent and not m.get('elements'):
        base = load_model(parent, depth + 1) if parent.startswith('tfclient:') and depth < 5 else None
        if base:
            merged = dict(base)
            merged['textures'] = {**base.get('textures', {}), **m.get('textures', {})}
            merged['display'] = {**base.get('display', {}), **m.get('display', {})}
            return merged
        if 'generated' in parent or 'handheld' in parent:
            m['generated'] = True
    return m


class Textures:
    def __init__(self, set_id):
        self.set_id = set_id
        self.dir = os.path.join(OUT, set_id)
        self.done = {}

    def get(self, ref):
        if ref in self.done:
            return self.done[ref]
        path = mod_path(ref, 'textures')
        if not path or not os.path.exists(path):
            self.done[ref] = None
            return None
        img = Image.open(path)
        w, h = img.size
        meta = {}
        if os.path.exists(path + '.mcmeta'):
            try:
                meta = json.load(open(path + '.mcmeta')).get('animation', {}) or {}
            except Exception:
                meta = {}
        fw = meta.get('width', w)
        fh = meta.get('height', w if (meta or h > w) and h % w == 0 else h)
        count = max(1, (w // fw) * (h // fh)) if fw and fh else 1
        frames = None
        if count > 1:
            raw = meta.get('frames')
            frames = [f['index'] if isinstance(f, dict) else f for f in raw] if raw else list(range(count))
        name = re.sub(r'[^a-z0-9_.]+', '_', ref.split(':', 1)[1].lower().replace('/', '__')) + '.png'
        if name.startswith('item__sets__'):
            name = name[len('item__sets__'):]
        os.makedirs(self.dir, exist_ok=True)
        shutil.copyfile(path, os.path.join(self.dir, name))
        info = {'src': f'/wear/{self.set_id}/{name}', 'fw': fw, 'fh': fh, 'frames': frames,
                'frametime': meta.get('frametime', 1), 'emissive': False}
        self.done[ref] = info
        return info


def export_model(model, textures):
    if not model:
        return None
    out = {'textures': {}}
    keys = {}
    for key, ref in (model.get('textures') or {}).items():
        if key == 'particle' or not isinstance(ref, str):
            continue
        while ref.startswith('#'):
            ref = model['textures'].get(ref[1:], '')
        info = textures.get(ref) if ref else None
        if info:
            out['textures'][key] = info
            keys[key] = True
    if not out['textures']:
        return None
    if model.get('elements'):
        out['elements'] = [{k: e[k] for k in ('from', 'to', 'rotation', 'faces') if k in e} for e in model['elements']]
    else:
        out['generated'] = 'layer0' if 'layer0' in out['textures'] else next(iter(out['textures']))
    display = model.get('display') or {}
    out['display'] = {k: display[k] for k in DISPLAY if k in display}
    return out


def armor_layers(set_id, info):
    """Capas de la armadura puesta (64×32 o mayor). Las animadas se juntan en una tira vertical."""
    folder = os.path.join(MOD, 'textures', 'models', 'armor')
    out = {}
    frames = info.get('armorFrames', 1) or 1
    for layer in info.get('armorLayers', []) or [1, 2]:
        single = os.path.join(folder, f'{set_id}_layer_{layer}.png')
        parts = [os.path.join(folder, f'{set_id}_layer_{layer}_f{i}.png') for i in range(frames)] if frames > 1 else []
        os.makedirs(os.path.join(OUT, set_id), exist_ok=True)
        name = f'armor_layer_{layer}.png'
        dst = os.path.join(OUT, set_id, name)
        if parts and all(os.path.exists(p) for p in parts):
            imgs = [Image.open(p).convert('RGBA') for p in parts]
            w, h = imgs[0].size
            strip = Image.new('RGBA', (w, h * len(imgs)))
            for i, im in enumerate(imgs):
                strip.paste(im, (0, i * h))
            strip.save(dst, optimize=True)
            out[str(layer)] = {'src': f'/wear/{set_id}/{name}', 'fw': w, 'fh': h, 'frames': list(range(len(imgs))),
                               'frametime': info.get('armorFrametime', 2), 'emissive': False}
        elif os.path.exists(single):
            shutil.copyfile(single, dst)
            w, h = Image.open(single).size
            out[str(layer)] = {'src': f'/wear/{set_id}/{name}', 'fw': w, 'fh': h, 'frames': None, 'frametime': 1,
                               'emissive': False}
    return out


def main():
    registry = json.load(open(os.path.join(MOD, 'tf_sets.json'), encoding='utf-8'))
    shutil.rmtree(OUT, ignore_errors=True)
    os.makedirs(OUT, exist_ok=True)
    total = 0
    for s in registry['sets']:
        set_id = s['id']
        textures = Textures(set_id)
        items = {}
        for item in s['items']:
            slug = item['id'][len(set_id) + 1:]
            entry = {'type': item['type']}
            if item.get('slot'):
                entry['slot'] = item['slot']
            if item['type'] != 'armor':
                model = export_model(load_model(f'tfclient:item/{item["id"]}'), textures)
                if not model:
                    continue
                entry['model'] = model
            if item.get('worn'):
                worn = export_model(load_model(item['worn']), textures)
                if worn:
                    entry['worn'] = worn
            items[slug] = entry
        data = {'id': set_id, 'name': s['name'], 'color': s.get('color'), 'items': items,
                'armor': armor_layers(set_id, s)}
        with open(os.path.join(OUT, f'{set_id}.json'), 'w', encoding='utf-8') as fh:
            json.dump(data, fh, ensure_ascii=False, separators=(',', ':'))
        total += len(items)
    print(f'{len(registry["sets"])} sets, {total} objetos para el probador en public/wear/')


if __name__ == '__main__':
    main()
