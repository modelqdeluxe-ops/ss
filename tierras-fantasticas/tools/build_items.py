"""Convierte los modelos de los packs (JSON de Minecraft/Blockbench) en lo que usa la web.

Para cada objeto genera:
  public/models/<set>/<objeto>.json   modelo listo para el visor 3D (texturas, elementos y animaciones)
  public/models/<set>/tex/...png      texturas originales (pixel art)
  public/img/items/<set>/<objeto>.webp miniatura renderizada con la vista de inventario

Uso: python3 tools/build_items.py <carpeta con los packs descomprimidos> [set ...]
Necesita Pillow y numpy.
"""
import json
import math
import os
import re
import shutil
import sys

import numpy as np
from PIL import Image, ImageEnhance

HERE = os.path.dirname(os.path.abspath(__file__))
PUBLIC = os.path.join(HERE, '..', 'public')

# Tipos comunes de los sets (nombre del modelo → nombre en la web)
COMMON = [
    ('sword', 'Espada'), ('great_sword', 'Gran espada'), ('rapier_sword', 'Estoque'), ('dagger', 'Daga'), ('knife', 'Cuchillo'), ('blade', 'Hoja'), ('halberd', 'Alabarda'), ('battleaxe', 'Hacha de batalla'), ('greatsword', 'Gran espada'), ('fishing', 'Caña de pescar'),
    ('backpack', 'Mochila'), ('cape', 'Capa'), ('big_sword', 'Gran espada'), ('tail', 'Cola'),
    ('flag', 'Bandera'), ('little_dragon', 'Dragoncito'), ('club', 'Garrote'), ('gauntlet', 'Guantelete'),
    ('axe', 'Hacha'), ('pickaxe', 'Pico'), ('shovel', 'Pala'), ('hoe', 'Azada'), ('hammer', 'Martillo'),
    ('battle_axe', 'Hacha de batalla'), ('mace', 'Maza'), ('scythe', 'Guadaña'), ('sickle', 'Hoz'), ('spear', 'Lanza'), ('trident', 'Tridente'), ('staff', 'Bastón'),
    ('bow', 'Arco'), ('crossbow', 'Ballesta'), ('fishing_rod', 'Caña de pescar'), ('shield', 'Escudo'),
    ('helmet', 'Casco cosmético'), ('hat', 'Sombrero'), ('wings', 'Alas'), ('wing', 'Alas'), ('key', 'Llave'), ('chest', 'Cofre'),
]

NAZGUL = [
    # Vol. 1
    ('nazgul_weaponry_vol_3/necromancer_sword', 'Espada del Nigromante'), ('nazgul_weaponry_vol_3/ocean_sword', 'Espada del Océano'),
    ('nazgul_weaponry_vol_3/aspectr_of_eather_sword', 'Espada del Éter'), ('nazgul_weaponry_vol_3/beecomb_sword', 'Espada Panal'),
    ('nazgul_weaponry_vol_3/last_rose', 'Última Rosa'), ('nazgul_weaponry_vol_3/death_grip', 'Garra de la Muerte'),
    ('nazgul_weaponry_vol_3/dark_moon_axe', 'Hacha de Luna Oscura'), ('nazgul_weaponry_vol_3/hellspawn_axe', 'Hacha Infernal'),
    ('nazgul_weaponry_vol_3/forge_hammer', 'Martillo de Forja'), ('nazgul_weaponry_vol_3/spider_bow', 'Arco Arácnido'),
    # Vol. 2
    ('nazgul_weaponry_vol_4/abyssal_blade', 'Hoja Abisal'), ('nazgul_weaponry_vol_4/great_sword', 'Gran Espada Rúnica'),
    ('nazgul_weaponry_vol_4/battle_mage_staff', 'Bastón del Mago de Guerra'), ('nazgul_weaponry_vol_4/fire_mace', 'Maza de Fuego'),
    ('nazgul_weaponry_vol_4/gargoyle_axe', 'Hacha Gárgola'), ('nazgul_weaponry_vol_4/witch_scythe', 'Guadaña de la Bruja'),
    ('nazgul_weaponry_vol_4/flowering_madness', 'Locura Floreciente'), ('nazgul_weaponry_vol_4/battle_standart', 'Estandarte de Guerra'),
    ('nazgul_weaponry_vol_4/heavy_bow', 'Arco Pesado'), ('nazgul_weaponry_vol_4/rogue_dagger', 'Daga del Pícaro'),
    # Vol. 3
    ('nazgul_weaponry_vol_5/demonic_blade', 'Hoja Demoníaca'), ('nazgul_weaponry_vol_5/cursed_sword', 'Espada Maldita'),
    ('nazgul_weaponry_vol_5/dark_sword', 'Espada Oscura'), ('nazgul_weaponry_vol_5/magnetic_blade', 'Hoja Magnética'),
    ('nazgul_weaponry_vol_5/kaz_sc', 'Guadaña de Kaz'), ('nazgul_weaponry_vol_5/mana_axe', 'Hacha de Maná'),
    ('nazgul_weaponry_vol_5/red_hammer', 'Martillo Carmesí'), ('nazgul_weaponry_vol_5/holy_spear', 'Lanza Sagrada'),
    ('nazgul_weaponry_vol_5/pox_spreader', 'Portador de la Plaga'), ('nazgul_weaponry_vol_5/obsidian_bow', 'Arco de Obsidiana'),
]

# set → (carpeta assets dentro de los packs, espacio de nombres, objetos)
SETS = {
    'valentine': ('Valentine_2025_Animated_Weapons_And_Tools_Set/Nexo Setup (TRIMS) 1.20-1.21.1/Nexo/pack/assets', 'valentine2025set', COMMON),
    'necros': ('Necros_Animated_Weapons_And_Tools_Set/Nexo Setup (TRIMS) 1.20-1.21.1/Nexo/pack/assets', 'necros_set', COMMON),
    'luminite': ('Luminite_Animated_Weapons_and_Tools_Set/Nexo Setup (TRIMS) 1.20-1.21.1/Nexo/pack/assets', 'plny_luminite_set', COMMON),
    'mecha': ('Dragon_Mecha_Overlord/Nexo Setup (1.20-1.21.1)/pack/assets', 'dragon_mecha_overlord', COMMON),
    'nazgul': ('nzpack/assets', 'nazgul_forge', NAZGUL),
    'shadow': ('Shadow_Slayer_Pack/ItemsAdder Setup/contents/shadow_slayer/resourcepack/assets', 'shadow_slayer', COMMON),
    'aether': ('Aetherburn_Pack_Setup/ItemsAdder Setup/ItemsAdder/contents/aetherburn_pack/resourcepack/assets', 'aetherburn_pack', COMMON),
    'ender': ('Ender_Dragon_Animated_Weapons_And_Tools_Set/ItemsAdder Setup/ItemsAdder/contents/ender_dragonset/resourcepack/assets', 'ender_dragonset', COMMON),
    'unicorn': ('Little_Unicorn_Animated_Weapons_And_Tools_Set/ItemsAdder Setup/ItemsAdder/contents/little_unicorn_set/resourcepack/assets', 'little_unicorn_set', COMMON),
    'cosmo': ('cosmo_pack/ItemsAdder/contents/cosmo_pack/resourcepack/assets', 'cosmo_pack', COMMON),
    'dracula': ('Dragon_world_dracula_pack_v2_/ItemsAdder Setup/ItemsAdder/contents/draculaset/resourcepack/assets', 'draculaset', COMMON),
    'lightning': ('Lightning_Power_Animated_Weapons__Tools_Set/ItemsAdder Setup/ItemsAdder/contents/lightning_powerset/resourcepack/assets', 'lightning_powerset', COMMON),
    'wither': ('Dark_Wither_Animated_Weapons_and_Tools_Set/ItemsAdder Setup/ItemsAdder/contents/darkwitherset/resourcepack/assets', 'darkwitherset', COMMON),
    'pink': ('elitecreatures-pink_legacy_animated_weapon_set/ItemsAdder/data/resourcepack/assets', 'elitecreatures', COMMON,
             'pink_legacy_animated_weapon_set/'),
    'skeleton': ('EliteCreatures_Skeleton_Overlord_Animated_Weapon_Set/ItemsAdder/data/resource_pack/assets', 'elitecreatures', COMMON),
    'lunar': ('Lunar_Dragon_2024_Weapon/Lunar_Dragon_2024_Weapon/ItemsAdder/contents/lunardragon/resourcepack/assets', 'lunardragonset', COMMON),
    'beats': ('Beats-Animated-Weapons-Tools-Set/Beats Animated Weapons & Tools Set/Itemsadder Setup/contents/beatsset/resourcepack/assets', 'beatsset', COMMON),
    'azure': ('Azure-Animated-Weapons-Tools-Set/Azure Animated Weapons & Tools Set/Itemsadder Setup/contents/azureset/resourcepack/assets', 'azureset', COMMON),
    'littledragon': ('Little-Dragon-Weapons-Tools-Set/Itemsadder Setup/contents/littledragonset/resourcepack/assets', 'littledragonset', COMMON),
    'bahamut': ('elitecreatures-bahamut_animated_weapon_set/ItemsAdder/data/resource_pack/assets', 'elitecreatures', COMMON,
                'bahamut_animated_weapon_set/'),
    'fairy': ('Fairy_Animated_Weapons__Tools_Set/Itemsadder Setup/contents/fairyset/resourcepack/assets', 'fairyset', COMMON),
    'bonita': ('Bonita-Animated-Weapons-Tools-Set/Bonita Animated Weapons & Tools Set/Itemsadder Setup/contents/bonitaset/resourcepack/assets', 'bonitaset', COMMON),
    'conqueror': ('conqueror_legacy/ItemsAdder Setup/contents/conqueror_legacy/resourcepack/assets', 'conqueror_legacy', COMMON),
}

THUMB = 256
SS = 3


def ns_path(ref, ns_default):
    ref = ref.replace('minecraft:', '') if ref.startswith('minecraft:') else ref
    if ':' in ref:
        return ref.split(':', 1)
    return ns_default, ref


def load_model(root, ns, name, depth=0):
    path = os.path.join(root, ns, 'models', name + '.json')
    if not os.path.exists(path):
        return None
    m = json.load(open(path))
    parent = m.get('parent')
    if parent and depth < 5:
        pns, pname = ns_path(parent, ns)
        if pns not in ('minecraft',) and not pname.startswith(('item/', 'builtin/', 'block/')):
            base = load_model(root, pns, pname, depth + 1)
            if base:
                merged = dict(base)
                merged['textures'] = {**base.get('textures', {}), **m.get('textures', {})}
                if m.get('elements'):
                    merged['elements'] = m['elements']
                if m.get('display'):
                    merged['display'] = {**base.get('display', {}), **m['display']}
                return merged
        if pname.startswith('builtin/'):
            return None
    return m


def texture_info(root, ns, ref):
    tns, tpath = ns_path(ref, ns)
    png = os.path.join(root, tns, 'textures', tpath + '.png')
    if not os.path.exists(png) or os.path.getsize(png) < 60:
        return None
    img = Image.open(png).convert('RGBA')
    w, h = img.size
    # Las texturas animadas y las «_e» son brillos: en el juego se ven a plena luz.
    emissive = '/animated' in '/' + tpath or tpath.endswith('_e') or 'outline' in tpath
    info = {'path': png, 'rel': f'{tns}/{tpath}.png', 'w': w, 'h': h, 'fw': w, 'fh': h, 'frames': None, 'frametime': 1,
            'emissive': emissive}
    meta = png + '.mcmeta'
    if os.path.exists(meta):
        try:
            anim = json.load(open(meta)).get('animation', {})
        except Exception:
            anim = {}
        fw = anim.get('width', w)
        fh = anim.get('height', fw if h % fw == 0 else h)
        count = h // fh if fh else 1
        frames = []
        for fr in anim.get('frames', list(range(count))):
            frames.append(fr['index'] if isinstance(fr, dict) else fr)
        info.update(fw=fw, fh=fh, frames=frames if count > 1 else None, frametime=anim.get('frametime', 1))
    elif h > w and h % w == 0:
        info.update(fh=w, frames=list(range(h // w)))
    return info


def first_frame(info):
    img = Image.open(info['path']).convert('RGBA')
    if info['frames']:
        i = info['frames'][0]
        img = img.crop((0, i * info['fh'], info['fw'], (i + 1) * info['fh']))
    return np.asarray(img).astype(np.float32) / 255.0


# --- Render de miniaturas (vista de inventario) ---

def rot_matrix(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == 'x':
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == 'y':
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def face_corners(f, t, face):
    x1, y1, z1 = f
    x2, y2, z2 = t
    return {
        'north': ((x2, y2, z1), (x1, y2, z1), (x2, y1, z1)),
        'south': ((x1, y2, z2), (x2, y2, z2), (x1, y1, z2)),
        'east': ((x2, y2, z2), (x2, y2, z1), (x2, y1, z2)),
        'west': ((x1, y2, z1), (x1, y2, z2), (x1, y1, z1)),
        'up': ((x1, y2, z1), (x2, y2, z1), (x1, y2, z2)),
        'down': ((x1, y1, z2), (x2, y1, z2), (x1, y1, z1)),
    }[face]


def render(model, texs, size):
    disp = model.get('display', {}).get('gui', {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.625] * 3})
    rx, ry, rz = disp.get('rotation', [0, 0, 0])
    tr = np.array(disp.get('translation', [0, 0, 0]), dtype=float)
    sc = np.array(disp.get('scale', [1, 1, 1]), dtype=float)
    R = rot_matrix('x', rx) @ rot_matrix('y', ry) @ rot_matrix('z', rz)
    N = size * SS
    color = np.zeros((N, N, 4), np.float32)
    depth = np.full((N, N), -1e9, np.float32)
    unit = N / 16.0 * 0.98
    light = np.array([0.35, 0.6, 1.0])
    light /= np.linalg.norm(light)
    arrays = {k: first_frame(v) for k, v in texs.items()}
    for el in model.get('elements', []):
        f, t = el['from'], el['to']
        rot = el.get('rotation')
        Rel = rot_matrix(rot['axis'], rot['angle']) if rot and rot.get('angle') else None
        origin = np.array(rot['origin'], dtype=float) if rot else None
        for face, fd in el.get('faces', {}).items():
            tex = arrays.get(str(fd.get('texture', '#0')).lstrip('#'))
            if tex is None:
                continue
            th, tw = tex.shape[:2]
            pts = []
            for p in face_corners(f, t, face):
                p = np.array(p, dtype=float)
                if Rel is not None:
                    p = Rel @ (p - origin) + origin
                pts.append(R @ ((p - 8.0) * sc) + tr)
            tl, trc, bl = pts
            normal = np.cross(bl - tl, trc - tl)
            nl = np.linalg.norm(normal)
            if nl < 1e-9:
                continue
            key = str(fd.get('texture', '#0')).lstrip('#')
            shade = 1.0 if texs[key].get('emissive') else 0.6 + 0.4 * max(0.0, float(normal / nl @ light))
            S0, S1, S2 = [np.array([N / 2 + p[0] * unit, N / 2 - p[1] * unit, p[2]]) for p in (tl, trc, bl)]
            S3 = S1 + S2 - S0
            xs, ys = [S0[0], S1[0], S2[0], S3[0]], [S0[1], S1[1], S2[1], S3[1]]
            x0, x1 = max(0, int(math.floor(min(xs)))), min(N - 1, int(math.ceil(max(xs))))
            y0, y1 = max(0, int(math.floor(min(ys)))), min(N - 1, int(math.ceil(max(ys))))
            if x1 < x0 or y1 < y0:
                continue
            a = np.array([[S1[0] - S0[0], S2[0] - S0[0]], [S1[1] - S0[1], S2[1] - S0[1]]])
            if abs(np.linalg.det(a)) < 1e-6:
                continue
            inv = np.linalg.inv(a)
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            dx, dy = gx - S0[0], gy - S0[1]
            s = inv[0, 0] * dx + inv[0, 1] * dy
            q = inv[1, 0] * dx + inv[1, 1] * dy
            inside = (s >= 0) & (s <= 1) & (q >= 0) & (q <= 1)
            if not inside.any():
                continue
            z = S0[2] + s * (S1[2] - S0[2]) + q * (S2[2] - S0[2])
            uv = fd.get('uv', [0, 0, 16, 16])
            r = fd.get('rotation', 0) % 360
            ss_, qq = s, q
            if r == 90:
                ss_, qq = q, 1 - s
            elif r == 180:
                ss_, qq = 1 - s, 1 - q
            elif r == 270:
                ss_, qq = 1 - q, s
            u = (uv[0] + ss_ * (uv[2] - uv[0])) / 16.0 * tw
            v = (uv[1] + qq * (uv[3] - uv[1])) / 16.0 * th
            px = tex[np.clip(np.floor(v).astype(int), 0, th - 1), np.clip(np.floor(u).astype(int), 0, tw - 1)]
            ok = inside & (px[..., 3] > 0.1)
            sub_d, sub_c = depth[y0:y1 + 1, x0:x1 + 1], color[y0:y1 + 1, x0:x1 + 1]
            closer = ok & (z > sub_d)
            sub_c[closer, :3] = (px[..., :3] * shade)[closer]
            sub_c[closer, 3] = 1.0
            sub_d[closer] = z[closer]
    return Image.fromarray((np.clip(color, 0, 1) * 255).astype(np.uint8), 'RGBA')


def finish_thumb(img, size, out):
    bbox = img.getbbox()
    if not bbox:
        return False
    img = img.crop(bbox)
    side = max(img.size)
    pad = int(side * 0.06)
    canvas = Image.new('RGBA', (side + 2 * pad, side + 2 * pad), (0, 0, 0, 0))
    canvas.paste(img, ((canvas.width - img.width) // 2, (canvas.height - img.height) // 2))
    smooth = canvas.width > size * 1.5
    canvas = canvas.resize((size, size), Image.LANCZOS if smooth else Image.NEAREST)
    # Un poco más de luz para que se vean claros sobre el fondo oscuro de la web
    rgb, alpha = canvas.convert('RGB'), canvas.split()[3]
    rgb = ImageEnhance.Brightness(rgb).enhance(1.12)
    rgb = ImageEnhance.Contrast(rgb).enhance(1.05)
    rgb.putalpha(alpha)
    rgb.save(out, 'WEBP', quality=90, method=6)
    return True


def model_for(packs, set_id, name):
    """Carga un modelo del set (algunos packs lo guardan en una subcarpeta: el cuarto campo de SETS)."""
    rel_root, ns, _items, *rest = SETS[set_id]
    root = os.path.join(packs, rel_root)
    prefix = rest[0] if rest else ''
    return root, ns, load_model(root, ns, name) or (load_model(root, ns, prefix + name) if prefix else None)


def build_set(packs, set_id):
    rel_root, ns, items, *_ = SETS[set_id]
    root = os.path.join(packs, rel_root)
    out_models = os.path.join(PUBLIC, 'models', set_id)
    out_thumbs = os.path.join(PUBLIC, 'img', 'items', set_id)
    shutil.rmtree(out_models, ignore_errors=True)
    shutil.rmtree(out_thumbs, ignore_errors=True)
    os.makedirs(os.path.join(out_models, 'tex'), exist_ok=True)
    os.makedirs(out_thumbs, exist_ok=True)
    listing = []
    for name, label in items:
        root, ns, model = model_for(packs, set_id, name)
        if not model:
            continue
        slug = re.sub(r'[^a-z0-9_]+', '_', name.split('/')[-1].lower())
        textures = {}
        for key, ref in model.get('textures', {}).items():
            if key == 'particle' or not isinstance(ref, str) or ref.startswith('#'):
                continue
            info = texture_info(root, ns, ref)
            if info:
                textures[key] = info
        if not textures:
            continue
        out = {'textures': {}, 'gui': model.get('display', {}).get('gui')}
        for key, info in textures.items():
            fname = re.sub(r'[^a-z0-9_.]+', '_', info['rel'].lower().replace('/', '__'))
            dst = os.path.join(out_models, 'tex', fname)
            if not os.path.exists(dst):
                shutil.copyfile(info['path'], dst)
            out['textures'][key] = {
                'src': f'/models/{set_id}/tex/{fname}', 'fw': info['fw'], 'fh': info['fh'],
                'frames': info['frames'], 'frametime': info['frametime'], 'emissive': info['emissive'],
            }
        if model.get('elements'):
            out['elements'] = [
                {k: el[k] for k in ('from', 'to', 'rotation', 'faces') if k in el} for el in model['elements']
            ]
            img = render(model, textures, THUMB)
        else:
            # Objeto plano (item/generated): el visor lo extruye como Minecraft
            layer = 'layer0' if 'layer0' in textures else next(iter(textures))
            out['generated'] = layer
            img = Image.fromarray((first_frame(textures[layer]) * 255).astype(np.uint8), 'RGBA')
        if not finish_thumb(img, THUMB, os.path.join(out_thumbs, slug + '.webp')):
            continue
        with open(os.path.join(out_models, slug + '.json'), 'w') as fh:
            json.dump(out, fh, separators=(',', ':'))
        listing.append({'id': slug, 'name': label})
        print(f'  {set_id}/{slug}: {label}')
    listing += build_armor(packs, set_id, out_models, out_thumbs)
    return listing


ARMOR = [('helmet', 'Casco de armadura'), ('chestplate', 'Peto'), ('leggings', 'Grebas'), ('boots', 'Botas')]


def find_armor_icon(root, piece):
    """Icono de inventario de una pieza de armadura (16 o 32 px), no la capa que se ve puesta."""
    best = None
    for dirpath, _dirs, files in os.walk(root):
        if os.sep + 'textures' not in dirpath or 'animation' in dirpath:
            continue
        for f in files:
            name = f.lower()
            if not name.endswith('.png') or piece not in name or 'layer' in name or name.startswith('armor_'):
                continue
            path = os.path.join(dirpath, f)
            try:
                w, h = Image.open(path).size
            except Exception:
                continue
            if w != h or w > 32:
                continue
            score = ('icon' in name) * 4 + ('armor' in dirpath) * 2 + (w == 32)
            if not best or score > best[0]:
                best = (score, path)
    return best[1] if best else None


def build_armor(packs, set_id, out_models, out_thumbs):
    rel_root = SETS[set_id][0]
    root = os.path.join(packs, rel_root)
    listing = []
    for piece, label in ARMOR:
        icon = find_armor_icon(root, piece)
        if not icon:
            continue
        slug = f'armor_{piece}'
        fname = f'{slug}.png'
        shutil.copyfile(icon, os.path.join(out_models, 'tex', fname))
        w, h = Image.open(icon).size
        model = {'textures': {'layer0': {'src': f'/models/{set_id}/tex/{fname}', 'fw': w, 'fh': h, 'frames': None,
                                         'frametime': 1, 'emissive': False}}, 'generated': 'layer0'}
        with open(os.path.join(out_models, slug + '.json'), 'w') as fh:
            json.dump(model, fh, separators=(',', ':'))
        img = Image.open(icon).convert('RGBA')
        finish_thumb(img, THUMB, os.path.join(out_thumbs, slug + '.webp'))
        listing.append({'id': slug, 'name': label})
        print(f'  {set_id}/{slug}: {label}')
    return listing


if __name__ == '__main__':
    packs = sys.argv[1]
    wanted = sys.argv[2:] or list(SETS)
    result = {}
    for set_id in wanted:
        print(set_id)
        result[set_id] = build_set(packs, set_id)
    print(json.dumps(result, ensure_ascii=False))
