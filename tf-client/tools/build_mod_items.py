"""Mete los objetos de los sets (los mismos que enseña la web) en el TF Client.

Lee los packs descomprimidos y escribe en src/main/resources:
  assets/tfclient/models/item/<set>_<objeto>.json        modelo del objeto (con sus variantes al tensar, lanzar...)
  assets/tfclient/models/item/sets/<set>/*.json          variantes y modelos cosméticos (alas, mochila, capa, cola)
  assets/tfclient/textures/item/sets/<set>/...           texturas y sus .mcmeta (animaciones)
  assets/tfclient/textures/models/armor/<set>_layer_*.png texturas de la armadura puesta
  assets/tfclient/lang/es_es.json, en_us.json            nombres
  assets/tfclient/tf_sets.json                           lista de sets y objetos que registra el mod

Comprueba además las reglas de Minecraft para los modelos (giros de 0/±22,5/±45 grados, coordenadas entre -16 y 32,
texturas que existen) para que ningún objeto salga como el cubo morado y negro.

Uso: python3 tools/build_mod_items.py <carpeta con los packs descomprimidos>
"""
import json
import math
import os
import re
import shutil
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
WEB_TOOLS = os.path.join(HERE, '..', '..', 'tierras-fantasticas', 'tools')
sys.path.insert(0, WEB_TOOLS)
import build_items as B  # noqa: E402
import crates as C  # noqa: E402

RES = os.path.join(HERE, '..', 'src', 'main', 'resources')
ASSETS = os.path.join(RES, 'assets', 'tfclient')

SWORDS = {'sword', 'great_sword', 'greatsword', 'big_sword', 'rapier_sword', 'dagger', 'knife', 'blade', 'scythe',
          'sickle', 'spear', 'staff', 'halberd', 'hammer', 'mace', 'club', 'gauntlet', 'flag'}
AXES = {'axe', 'battle_axe', 'battleaxe'}
HEAD = {'helmet', 'hat'}
BACK = {'wings', 'wing', 'backpack', 'cape', 'tail'}
ARMOR = {'armor_helmet': 'helmet', 'armor_chestplate': 'chestplate', 'armor_leggings': 'leggings', 'armor_boots': 'boots'}

VARIANTS = {
    'bow': ['_0', '_1', '_2', '_pulling_0', '_pulling_1', '_pulling_2'],
    'crossbow': ['_0', '_1', '_2', '_charged', '_firework'],
    'fishing_rod': ['_cast'],
    'shield': ['_blocking'],
    'trident': ['_throwing'],
}
ANGLES = {-45.0, -22.5, 0.0, 22.5, 45.0}
DISPLAY = {'thirdperson_righthand', 'thirdperson_lefthand', 'firstperson_righthand', 'firstperson_lefthand', 'gui', 'head',
           'ground', 'fixed'}
problems = []
mip_low = [1 << 30]


def item_type(slug):
    if slug in ARMOR:
        return 'armor'
    if slug in HEAD:
        return 'head'
    if slug in BACK:
        return 'back'
    if slug == 'fishing' or 'fishing_rod' in slug:
        return 'fishing_rod'
    if slug.endswith('crossbow'):
        return 'crossbow'
    if slug.endswith('bow'):
        return 'bow'
    if slug == 'shield':
        return 'shield'
    if slug == 'trident':
        return 'trident'
    if slug in ('pickaxe', 'shovel', 'hoe'):
        return slug
    if slug in AXES or slug.endswith('_axe'):
        return 'axe'
    if 'hammer' in slug or 'mace' in slug or slug in ('club', 'gauntlet'):
        return 'heavy'
    if slug in SWORDS or any(k in slug for k in ('sword', 'blade', 'dagger', 'scythe', 'spear', 'staff', 'standart',
                                                    'rose', 'grip', 'madness', 'spreader', 'kaz')):
        return 'sword'
    return 'sword'


def lowbit(n):
    return n & -n


class SetWriter:
    def __init__(self, packs, set_id):
        self.set_id = set_id
        rel_root, self.ns, self.items, *rest = B.SETS[set_id]
        self.prefix = rest[0] if rest else ''
        self.root = os.path.join(packs, rel_root)
        self.textures = {}  # ref original -> ref nuevo

    def model(self, name):
        m = B.load_model(self.root, self.ns, name)
        if not m and self.prefix:
            m = B.load_model(self.root, self.ns, self.prefix + name)
        return m

    def tex(self, ref):
        """Copia una textura del pack y devuelve su nombre dentro del mod."""
        if ref in self.textures:
            return self.textures[ref]
        tns, tpath = B.ns_path(ref, self.ns)
        src = os.path.join(self.root, tns, 'textures', tpath + '.png')
        if not os.path.exists(src) or os.path.getsize(src) < 60:
            problems.append(f'{self.set_id}: falta la textura {ref}')
            self.textures[ref] = None
            return None
        clean = re.sub(r'[^a-z0-9_/.-]+', '_', tpath.lower())
        new_ref = f'tfclient:item/sets/{self.set_id}/{clean}'
        dst = os.path.join(ASSETS, 'textures', 'item', 'sets', self.set_id, clean + '.png')
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        img = Image.open(src).convert('RGBA')
        w, h = img.size
        meta = None
        if os.path.exists(src + '.mcmeta'):
            try:
                meta = json.load(open(src + '.mcmeta'))
            except Exception:
                meta = None
        anim = (meta or {}).get('animation')
        fw = (anim or {}).get('width', w)
        fh = (anim or {}).get('height', fw if anim is not None and h % fw == 0 else h)
        if anim is None and h > w and h % w == 0:
            # Tira de fotogramas sin .mcmeta: Minecraft la vería estirada, así que la animamos nosotros
            anim, fh = {'frametime': 2}, w
        if anim is not None and (h % fh or w % fw):
            problems.append(f'{self.set_id}: animación con medidas raras {tpath} {w}x{h}')
        # Medidas que no son múltiplo de 16 quitan los mipmaps de todo el atlas de Minecraft: cada fotograma se lleva
        # al múltiplo de 16 más cercano por arriba (vecino más próximo, sin difuminar el pixel art).
        nfw = fw if lowbit(fw) >= 16 else -(-fw // 16) * 16
        nfh = fh if lowbit(fh) >= 16 else -(-fh // 16) * 16
        if (nfw, nfh) != (fw, fh) and w % fw == 0 and h % fh == 0:
            cols, rows = w // fw, h // fh
            out = Image.new('RGBA', (nfw * cols, nfh * rows), (0, 0, 0, 0))
            for r in range(rows):
                for c in range(cols):
                    frame = img.crop((c * fw, r * fh, (c + 1) * fw, (r + 1) * fh)).resize((nfw, nfh), Image.NEAREST)
                    out.paste(frame, (c * nfw, r * nfh))
            img = out
            if anim is not None:
                if 'width' in anim:
                    anim['width'] = nfw
                if 'height' in anim:
                    anim['height'] = nfh
            fw, fh = nfw, nfh
        scale = 1
        mip_low[0] = min(mip_low[0], lowbit(fw * scale), lowbit(fh * scale))
        img.save(dst, optimize=True)
        if anim is not None:
            keep = {k: v for k, v in anim.items() if k in ('frametime', 'frames', 'interpolate', 'width', 'height')}
            if 'frames' in keep:
                count = (img.width // fw) * (img.height // fh)
                valid = [fr for fr in keep['frames'] if (fr['index'] if isinstance(fr, dict) else fr) < count]
                if len(valid) != len(keep['frames']):
                    problems.append(f'{self.set_id}: {tpath} pedía fotogramas que no existen (corregido)')
                keep['frames'] = valid or list(range(count))
            with open(dst + '.mcmeta', 'w') as fh_:
                json.dump({'animation': keep}, fh_)
        self.textures[ref] = new_ref
        return new_ref

    def convert(self, model, label):
        """Modelo del pack → modelo del mod (texturas renombradas, sin datos de Blockbench)."""
        out = {}
        parent = model.get('parent')
        if parent and not model.get('elements'):
            out['parent'] = 'minecraft:' + parent.split(':')[-1] if not parent.startswith('minecraft:') else parent
        textures = {}
        for key, ref in model.get('textures', {}).items():
            if not isinstance(ref, str):
                continue
            if ref.startswith('#'):
                textures[key] = ref
                continue
            new = self.tex(ref)
            if new:
                textures[key] = new
        out['textures'] = textures
        if 'particle' not in textures and textures:
            textures['particle'] = next(v for v in textures.values() if not v.startswith('#'))
        for k in ('ambientocclusion', 'gui_light'):
            if k in model:
                out[k] = model[k]
        if model.get('elements'):
            elements = []
            for el in model['elements']:
                f, t = el['from'], el['to']
                if any(v < -16 or v > 32 for v in f + t):
                    problems.append(f'{label}: elemento fuera de -16..32')
                    continue
                rot = el.get('rotation')
                if rot:
                    angle = float(rot.get('angle', 0))
                    if angle not in ANGLES or rot.get('axis') not in ('x', 'y', 'z'):
                        problems.append(f'{label}: giro no válido {rot}')
                        continue
                faces = {}
                for face, fd in el.get('faces', {}).items():
                    tk = str(fd.get('texture', '')).lstrip('#')
                    if tk not in textures:
                        continue  # la textura no existe: la cara no se dibuja (en vez del morado)
                    faces[face] = {k: v for k, v in fd.items() if k in ('uv', 'texture', 'rotation', 'cullface', 'tintindex')}
                if not faces:
                    continue
                e = {'from': f, 'to': t, 'faces': faces}
                if rot:
                    e['rotation'] = {k: v for k, v in rot.items() if k in ('angle', 'axis', 'origin', 'rescale')}
                if el.get('shade') is False:
                    e['shade'] = False
                elements.append(e)
            out['elements'] = elements
        if model.get('display'):
            # Solo los contextos que conoce Minecraft 1.20.1 (los packs nuevos traen p. ej. on_shelf)
            out['display'] = {k: v for k, v in model['display'].items() if k in DISPLAY}
        return out

    def write_model(self, path_rel, data):
        dst = os.path.join(ASSETS, 'models', 'item', path_rel + '.json')
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with open(dst, 'w') as fh:
            json.dump(data, fh, separators=(',', ':'))

    def variant(self, base, suffix, label):
        for name in (base + suffix, base + suffix.replace('_pulling', '')):
            m = self.model(name)
            if m:
                rel = f'sets/{self.set_id}/{name.split("/")[-1]}'
                self.write_model(rel, self.convert(m, label))
                return f'tfclient:item/{rel}'
        return None

    def armor_layers(self):
        """Texturas de la armadura puesta. Si vienen animadas (tira de fotogramas), se guarda cada fotograma
        y el mod va cambiando de textura con el tiempo. Devuelve (capas, fotogramas, ticks por fotograma)."""
        best = {}
        for dirpath, _dirs, files in os.walk(self.root):
            if 'trims' in dirpath:
                continue
            for f in files:
                low = f.lower()
                if not low.endswith('.png') or low.endswith('_e.png') or 'overlay' in low:
                    continue
                layer = 1 if ('layer_1' in low or low == 'armor_main.png') else 2 if ('layer_2' in low or low == 'armor_leggings.png') else None
                if not layer:
                    continue
                path = os.path.join(dirpath, f)
                try:
                    w, h = Image.open(path).size
                except Exception:
                    continue
                if w % 2 or h % (w // 2):
                    continue
                best.setdefault(layer, path)
        out_dir = os.path.join(ASSETS, 'textures', 'models', 'armor')
        os.makedirs(out_dir, exist_ok=True)
        frames, frametime = 1, 2
        for layer, path in best.items():
            img = Image.open(path).convert('RGBA')
            w, h = img.size
            n = h // (w // 2)
            if os.path.exists(path + '.mcmeta'):
                try:
                    frametime = json.load(open(path + '.mcmeta')).get('animation', {}).get('frametime', frametime)
                except Exception:
                    pass
            if n == 1:
                img.save(os.path.join(out_dir, f'{self.set_id}_layer_{layer}.png'), optimize=True)
            else:
                frames = n
                for i in range(n):
                    img.crop((0, i * h // n, w, (i + 1) * h // n)).save(
                        os.path.join(out_dir, f'{self.set_id}_layer_{layer}_f{i}.png'), optimize=True)
        return sorted(best), frames, frametime


def armor_icon_model(writer, slug):
    """Icono de la pieza de armadura (el mismo que usa la web)."""
    src = os.path.join(HERE, '..', '..', 'tierras-fantasticas', 'public', 'models', writer.set_id, 'tex', slug + '.png')
    if not os.path.exists(src):
        return None
    clean = f'armor/{slug}'
    dst = os.path.join(ASSETS, 'textures', 'item', 'sets', writer.set_id, clean + '.png')
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    shutil.copyfile(src, dst)
    w, h = Image.open(src).size
    mip_low[0] = min(mip_low[0], lowbit(w), lowbit(h))
    return {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'tfclient:item/sets/{writer.set_id}/{clean}'}}


def _rot(axis, deg):
    a = math.radians(deg)
    c, s_ = math.cos(a), math.sin(a)
    if axis == 'x':
        return [[1, 0, 0], [0, c, -s_], [0, s_, c]]
    if axis == 'y':
        return [[c, 0, s_], [0, 1, 0], [-s_, 0, c]]
    return [[c, -s_, 0], [s_, c, 0], [0, 0, 1]]


def _mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def _apply(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


# Dónde va cada cosmético en la espalda, en bloques desde el cuello hacia abajo (centro, o borde de arriba).
BACK_PLACE = {'wings': ('center', 0.30), 'wing': ('center', 0.30), 'backpack': ('center', 0.38),
              'cape': ('top', 0.02), 'tail': ('top', 0.60)}


def place_on_back(model, slug, tag):
    """Coloca el modelo en la espalda del jugador.

    El mod lo dibuja como un objeto puesto en la cabeza (como CustomHeadLayer) pero siguiendo el cuerpo. Los packs
    traen la posición pensada para los plugins de cosméticos (un soporte de armadura montado encima del jugador), que
    aquí lo dejaba a la altura de los pies. Se conserva el giro y el tamaño del pack y se calcula el desplazamiento
    con la forma real del modelo para que quede pegado a la espalda.
    """
    head = dict(model.get('display', {}).get('head', {}))
    rot = head.get('rotation', [0, 0, 0])
    scale = head.get('scale', [1, 1, 1])
    pts = []
    for el in model.get('elements', []):
        f, t = el['from'], el['to']
        corners = [[x, y, z] for x in (f[0], t[0]) for y in (f[1], t[1]) for z in (f[2], t[2])]
        r = el.get('rotation')
        if r and r.get('angle'):
            m = _rot(r['axis'], float(r['angle']))
            o = r['origin']
            corners = [[a + b for a, b in zip(_apply(m, [c[0] - o[0], c[1] - o[1], c[2] - o[2]]), o)] for c in corners]
        pts += corners
    if not pts:
        return
    rot = list(rot)

    def oriented(scl):
        R = _mul(_mul(_rot('x', rot[0]), _rot('y', rot[1])), _rot('z', rot[2]))
        return [_apply(R, [scl[0] * (p[0] / 16 - 0.5), scl[1] * (p[1] / 16 - 0.5), scl[2] * (p[2] / 16 - 0.5)]) for p in pts]

    def extent(vs, i):
        return max(v[i] for v in vs) - min(v[i] for v in vs)

    # Algunos packs tienen las alas de lado (la envergadura en z): se giran 90 grados para que se abran a los lados.
    unit = oriented([1, 1, 1])
    if slug in ('wings', 'wing') and extent(unit, 2) > 1.5 * extent(unit, 0):
        rot[1] = (rot[1] + 90) % 360
        unit = oriented([1, 1, 1])
    if not any(scale):
        # Algunos packs esconden el modelo en la cabeza (escala 0): se le da un tamaño razonable.
        target = 1.8 if slug in ('wings', 'wing') else 0.7
        k = target / 0.625 / max(extent(unit, 0), 0.05)
        scale = [k, k, k]
    scale = [max(-4.0, min(4.0, float(v))) for v in scale]
    q = oriented(scale)
    lo = [min(v[i] for v in q) for i in range(3)]
    hi = [max(v[i] for v in q) for i in range(3)]
    mode, y_neck = BACK_PLACE.get(slug, ('center', 0.35))
    # En el marco del objeto «en la cabeza»: +y arriba, +z hacia la espalda, 1 unidad = 1/0.625 bloques.
    # Altura en el cuerpo: y_cuerpo = -0.25 - 0.625 * qy (hacia abajo desde el cuello).
    qy_target = -(y_neck + 0.25) / 0.625
    ty = qy_target - ((lo[1] + hi[1]) / 2 if mode == 'center' else hi[1])
    tx = -(lo[0] + hi[0]) / 2
    tz = 0.15 / 0.625 - lo[2]  # justo detrás de la espalda (que está a 2 píxeles del centro)
    trans = [round(v * 16, 3) for v in (tx, ty, tz)]
    if any(abs(v) > 80 for v in trans):
        problems.append(f'{tag}: el cosmético queda demasiado lejos ({trans})')
        trans = [max(-80, min(80, v)) for v in trans]
    head.update({'rotation': rot, 'translation': trans, 'scale': scale})
    model.setdefault('display', {})['head'] = head


def overrides(kind, refs):
    o = []
    if kind == 'bow':
        steps = [({'pulling': 1}, 0), ({'pulling': 1, 'pull': 0.65}, 1), ({'pulling': 1, 'pull': 0.9}, 2)]
        o = [{'predicate': p, 'model': refs[i]} for p, i in steps if refs.get(i)]
    elif kind == 'crossbow':
        steps = [({'pulling': 1}, 0), ({'pulling': 1, 'pull': 0.58}, 1), ({'pulling': 1, 'pull': 1.0}, 2),
                 ({'charged': 1}, 'charged'), ({'charged': 1, 'firework': 1}, 'firework')]
        o = [{'predicate': p, 'model': refs[i]} for p, i in steps if refs.get(i)]
    elif kind == 'fishing_rod' and refs.get('cast'):
        o = [{'predicate': {'cast': 1}, 'model': refs['cast']}]
    elif kind == 'shield' and refs.get('blocking'):
        o = [{'predicate': {'blocking': 1}, 'model': refs['blocking']}]
    elif kind == 'trident' and refs.get('throwing'):
        o = [{'predicate': {'throwing': 1}, 'model': refs['throwing']}]
    return o


def main(packs):
    for sub in ('models/item', 'textures/item/sets', 'textures/models/armor'):
        path = os.path.join(ASSETS, sub)
        if sub == 'models/item':
            for f in os.listdir(path) if os.path.isdir(path) else []:
                if f != 'sets' and f.endswith('.json') and '_' in f:
                    os.remove(os.path.join(path, f))
            shutil.rmtree(os.path.join(path, 'sets'), ignore_errors=True)
        else:
            shutil.rmtree(path, ignore_errors=True)
    crates = {row[0]: row for row in C.CRATES}
    sets_out, lang = [], {'itemGroup.tfclient.sets': 'Tierras Fantásticas · Sets'}
    total = 0
    for set_id, row in crates.items():
        set_name = row[1].replace('Crate ', '')
        color = row[7][1]
        w = SetWriter(packs, set_id)
        listing = C.listing(set_id)
        items = []
        for entry in listing:
            slug, label = entry['id'], entry['name']
            kind = item_type(slug)
            item_id = f'{set_id}_{slug}'
            tag = f'{set_id}/{slug}'
            info = {'id': item_id, 'type': kind}
            if kind == 'armor':
                model = armor_icon_model(w, slug)
                info['slot'] = ARMOR[slug]
            else:
                full = next((n for n, _ in w.items if n.split('/')[-1] == slug), slug)
                raw = w.model(full)
                if not raw:
                    problems.append(f'{tag}: sin modelo')
                    continue
                model = w.convert(raw, tag)
                if not model.get('elements') and not model.get('parent'):
                    problems.append(f'{tag}: modelo vacío')
                    continue
                if kind in VARIANTS:
                    refs = {}
                    for suffix in VARIANTS[kind]:
                        ref = w.variant(full, suffix, tag + suffix)
                        if ref:
                            key = suffix.lstrip('_').replace('pulling_', '')
                            refs[int(key) if key.isdigit() else key] = ref
                    if kind == 'fishing_rod' and 'cast' not in refs:
                        ref = w.variant('fishing', '_cast', tag + '_cast')
                        if ref:
                            refs['cast'] = ref
                    ov = overrides(kind, refs)
                    if ov:
                        model['overrides'] = ov
                if kind == 'back':
                    worn = None
                    for suffix in ('_cosmetic', '_cosmetics', '_cosmeticscore', '_1'):
                        m = w.model(full + suffix)
                        if m:
                            worn = w.convert(m, tag + suffix)
                            break
                    worn = worn or json.loads(json.dumps(model))
                    worn.pop('overrides', None)
                    place_on_back(worn, slug, tag)
                    rel = f'sets/{set_id}/{slug}_worn'
                    w.write_model(rel, worn)
                    info['worn'] = f'tfclient:item/{rel}'
            if not model:
                problems.append(f'{tag}: sin modelo')
                continue
            w.write_model(item_id, model)
            if kind == 'armor':
                label_full = f'{label} {set_name}'
            elif set_id == 'nazgul':
                label_full = label
            else:
                label_full = f'{label} {set_name}'
            lang[f'item.tfclient.{item_id}'] = label_full
            items.append(info)
        entry = {'id': set_id, 'name': set_name, 'color': color, 'items': items}
        if any(i['type'] == 'armor' for i in items):
            layers, frames, frametime = w.armor_layers()
            if layers != [1, 2]:
                problems.append(f'{set_id}: faltan capas de armadura {layers}')
            if frames > 1:
                entry['armorFrames'] = frames
                entry['armorFrametime'] = frametime
                print(f'  {set_id}: armadura animada ({frames} fotogramas)')
        lang[f'tfclient.set.{set_id}'] = set_name
        sets_out.append(entry)
        total += len(items)
        print(f'{set_id}: {len(items)} objetos')
    with open(os.path.join(ASSETS, 'tf_sets.json'), 'w') as fh:
        json.dump({'sets': sets_out}, fh, ensure_ascii=False, indent=1)
    os.makedirs(os.path.join(ASSETS, 'lang'), exist_ok=True)
    for lang_file in ('es_es.json', 'en_us.json'):
        path = os.path.join(ASSETS, 'lang', lang_file)
        old = json.load(open(path, encoding='utf-8')) if os.path.exists(path) else {}
        old = {k: v for k, v in old.items() if not k.startswith(('item.tfclient.', 'tfclient.set.', 'itemGroup.tfclient'))}
        with open(path, 'w', encoding='utf-8') as fh:
            json.dump({**old, **lang}, fh, ensure_ascii=False, indent=1)
    print(f'TOTAL {total} objetos en {len(sets_out)} sets; mipmap mínimo {mip_low[0]}px')
    print(f'{len(problems)} avisos')
    for p in problems:
        print('  -', p)


if __name__ == '__main__':
    main(sys.argv[1])
