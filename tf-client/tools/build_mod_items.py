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

Uso: python3 tools/build_mod_items.py <carpeta con los packs descomprimidos> [set ...]
     Con sets, rehace solo esos y deja los demás como están (para añadir kits sin los packs de todos).
"""
import json
import math
import os
import re
import shutil
import sys

from PIL import Image, ImageChops

HERE = os.path.dirname(os.path.abspath(__file__))
WEB_TOOLS = os.path.join(HERE, '..', '..', 'tierras-fantasticas', 'tools')
sys.path.insert(0, WEB_TOOLS)
import build_items as B
import hmc_worn  # noqa: E402
import crates as C  # noqa: E402

RES = os.path.join(HERE, '..', 'src', 'main', 'resources')
ASSETS = os.path.join(RES, 'assets', 'tfclient')

SWORDS = {'sword', 'great_sword', 'greatsword', 'big_sword', 'rapier_sword', 'dagger', 'knife', 'blade', 'scythe',
          'sickle', 'spear', 'staff', 'halberd', 'hammer', 'mace', 'club', 'gauntlet', 'flag'}
AXES = {'axe', 'battle_axe', 'battleaxe', 'battleaxes'}
HEAD = {'helmet', 'hat', 'crown'}
BACK = {'wings', 'wing', 'backpack', 'cape', 'tail', 'quiver'}
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
back_report = []
worn_from = {}
mip_low = [1 << 30]


def item_type(slug):
    if slug in ARMOR:
        return 'armor'
    if slug in HEAD:
        return 'head'
    if slug in BACK:
        return 'back'
    if slug == 'fishing' or slug.startswith(('fishing_rod', 'fishingrod')):
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


FACE_NORMAL = {'north': (2, -1), 'south': (2, 1), 'west': (0, -1), 'east': (0, 1), 'down': (1, -1), 'up': (1, 1)}
ZF_STEP = 0.03  # píxeles de modelo


def separate_coplanar(model):
    """Caras de elementos distintos en el mismo plano y encima una de otra parpadean en el juego (z-fighting: cuadritos
    que cambian). Dentro de cada grupo, la cara más grande se queda y las más pequeñas (detalles, brillos) se adelantan
    unas centésimas de píxel hacia fuera, así siempre se dibujan encima. Devuelve cuántas caras se movieron."""
    els = model.get('elements') or []
    groups = {}
    for i, e in enumerate(els):
        f, t = e['from'], e['to']
        r = e.get('rotation') or {}
        rk = (r.get('axis'), float(r.get('angle')), tuple(r.get('origin', (8, 8, 8)))) if r.get('angle') else None
        for name, fd in e.get('faces', {}).items():
            ax, sgn = FACE_NORMAL[name]
            pos = max(f[ax], t[ax]) if sgn > 0 else min(f[ax], t[ax])
            rect = [(min(f[k], t[k]), max(f[k], t[k])) for k in range(3) if k != ax]
            if any(hi - lo < 1e-6 for lo, hi in rect):
                continue
            groups.setdefault((ax, sgn, round(pos, 3), rk), []).append((i, name, rect, (fd.get('texture'), tuple(fd.get('uv', ())))))
    moved = 0
    for (ax, sgn, pos, _), faces in groups.items():
        if len(faces) < 2:
            continue
        faces.sort(key=lambda x: -(x[2][0][1] - x[2][0][0]) * (x[2][1][1] - x[2][1][0]))
        layer = {}
        for n, (i, name, rect, look) in enumerate(faces):
            under = [layer[j] for j, (i2, _, rect2, look2) in enumerate(faces[:n]) if i2 != i and look2 != look
                     and all(min(rect[k][1], rect2[k][1]) - max(rect[k][0], rect2[k][0]) > 0.01 for k in range(2))]
            layer[n] = (max(under) + 1) if under else 0
            if not layer[n]:
                continue
            e = els[i]
            key = 'to' if (e['to'][ax] >= e['from'][ax]) == (sgn > 0) else 'from'
            new = round(e[key][ax] + sgn * ZF_STEP * min(layer[n], 5), 4)
            if -16 <= new <= 32:
                e[key] = list(e[key])
                e[key][ax] = new
                moved += 1
    return moved


class SetWriter:
    def __init__(self, packs, set_id):
        self.set_id = set_id
        rel_root, self.ns, self.items, *rest = B.SETS[set_id]
        self.prefix = rest[0] if rest else ''
        self.root = os.path.join(packs, rel_root)
        self.textures = {}  # ref original -> ref nuevo
        self.frame_size = {}  # ref nuevo -> (ancho, alto) de un fotograma
        self.flat_uv_fixed = 0
        self.zfight_fixed = 0
        self.planes_trimmed = 0
        self.alpha_cache = {}
        # Lo que el pack se pone de verdad (HMCCosmetics): el modelo «puesto» puede no ser el del inventario.
        self.hmc = hmc_worn.worn_models(os.path.join(packs, rel_root.replace('\\', '/').split('/')[0]))

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
        self.frame_size[new_ref] = (fw, fh)
        return new_ref

    def alpha_of(self, ref):
        """Canal alfa del primer fotograma de una textura ya copiada al mod (lista de filas), o None."""
        if ref in self.alpha_cache:
            return self.alpha_cache[ref]
        alpha = None
        if ref.startswith('tfclient:'):
            path = os.path.join(ASSETS, 'textures', ref.split(':', 1)[1] + '.png')
            if os.path.exists(path):
                img = Image.open(path).convert('RGBA')
                fw, fh = self.frame_size.get(ref, img.size)
                a = img.crop((0, 0, fw, fh)).split()[3]
                alpha = [list(a.crop((0, y, fw, y + 1)).getdata()) for y in range(fh)]
        self.alpha_cache[ref] = alpha
        return alpha

    def fix_flat_uv(self, face, textures):
        """UV con ancho o alto cero: Minecraft estira una sola fila de la textura por toda la cara y, con las texturas
        animadas de efectos, se ven cuadritos y rayas que parpadean. Se cambia por un único texel (el del centro de esa
        fila), que da un color liso o transparente, sin quitar la cara."""
        uv = face.get('uv')
        if not uv or len(uv) != 4:
            return
        uw, uh = abs(uv[2] - uv[0]), abs(uv[3] - uv[1])
        if (uw < 1e-6) == (uh < 1e-6):
            return  # normal, o ya es un solo texel
        ref = textures.get(str(face.get('texture', '')).lstrip('#'), '')
        while ref.startswith('#'):
            ref = textures.get(ref[1:], '')
        fw, fh = self.frame_size.get(ref, (16, 16))

        def snap(a, b, n):
            i = min(n - 1, max(0, int((a + b) / 2 * n / 16)))
            return round((i + 0.5) * 16 / n, 5)
        u, v = snap(uv[0], uv[2], fw), snap(uv[1], uv[3], fh)
        face['uv'] = [u, v, u, v]
        self.flat_uv_fixed += 1

    def snap_uv(self, face, textures):
        """Bordes de UV a 0,1-0,3 texels de un borde de píxel: el último píxel que se ve es el del dibujo de al lado
        (rayas sueltas en los bordes de las alas). Se ajustan al borde de píxel, un pelo hacia dentro."""
        ref = textures.get(str(face.get('texture', '')).lstrip('#'), '')
        while ref.startswith('#'):
            ref = textures.get(ref[1:], '')
        fw, fh = self.frame_size.get(ref, (16, 16))
        snap_face_uv(face, fw, fh)

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
                    snapped = round(angle / 22.5) * 22.5
                    if abs(angle - snapped) < 0.01:  # 22.499999… de algunos editores
                        angle = snapped
                        rot = {**rot, 'angle': snapped}
                    if angle not in ANGLES or rot.get('axis') not in ('x', 'y', 'z'):
                        problems.append(f'{label}: giro no válido {rot}')
                        continue
                faces = {}
                for face, fd in el.get('faces', {}).items():
                    tk = str(fd.get('texture', '')).lstrip('#')
                    if tk not in textures:
                        continue  # la textura no existe: la cara no se dibuja (en vez del morado)
                    faces[face] = {k: v for k, v in fd.items() if k in ('uv', 'texture', 'rotation', 'cullface', 'tintindex')}
                    self.fix_flat_uv(faces[face], textures)
                    self.snap_uv(faces[face], textures)
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

    def alpha_union(self, ref):
        """Alfa de todos los fotogramas juntos (lo que se ve en algún momento), como imagen L de un fotograma."""
        key = ('union', ref)
        if key in self.alpha_cache:
            return self.alpha_cache[key]
        alpha = None
        if ref.startswith('tfclient:'):
            path = os.path.join(ASSETS, 'textures', ref.split(':', 1)[1] + '.png')
            if os.path.exists(path):
                img = Image.open(path).convert('RGBA')
                fw, fh = self.frame_size.get(ref, img.size)
                a = img.split()[3]
                alpha = a.crop((0, 0, fw, fh))
                for y in range(fh, img.size[1] - fh + 1, fh):
                    alpha = ImageChops.lighter(alpha, a.crop((0, y, fw, y + fh)))
        self.alpha_cache[key] = alpha
        return alpha

    def trim_planes(self, model):
        """Planos sin grosor (alas, efectos) recortados a lo que se ve más un píxel. Así el borde del recorte nunca
        cae pegado al dibujo de al lado en la textura (rayas sueltas en las puntas de las alas) y se dibuja menos."""
        textures = model.get('textures', {})
        trimmed = 0
        for el in model.get('elements', []):
            f, t = el['from'], el['to']
            thin = [abs(t[k] - f[k]) < 0.01 for k in range(3)]
            if thin != [False, False, True] or any('rotation' in fd for fd in el['faces'].values()):
                continue
            big = [el['faces'].get(n) for n in ('south', 'north')]
            if not all(big) or any(not fd.get('uv') or len(fd['uv']) != 4 for fd in big):
                continue
            ref = textures.get(str(big[0].get('texture', '')).lstrip('#'), '')
            if big[1].get('texture') != big[0].get('texture') or not ref.startswith('tfclient:'):
                continue
            alpha = self.alpha_union(ref)
            if alpha is None:
                continue
            fw, fh = alpha.size
            # Fracciones de la cara (sx desde from.x, ty desde arriba) donde hay algo visible, en las dos caras
            box = None
            for name, fd in (('south', big[0]), ('north', big[1])):
                u0, v0, u1, v1 = fd['uv']
                if abs(u1 - u0) < 1e-6 or abs(v1 - v0) < 1e-6:
                    box = None
                    break
                px = [min(u0, u1) * fw / 16, max(u0, u1) * fw / 16, min(v0, v1) * fh / 16, max(v0, v1) * fh / 16]
                region = alpha.crop((int(math.floor(px[0])), int(math.floor(px[2])), int(math.ceil(px[1])), int(math.ceil(px[3]))))
                bb = region.point(lambda a: 255 if a > 8 else 0).getbbox()
                if not bb:
                    box = 'empty'
                    break
                # Texels visibles + 1 de margen, en coordenadas UV
                ul = (math.floor(px[0]) + bb[0] - 1) * 16 / fw
                ur = (math.floor(px[0]) + bb[2] + 1) * 16 / fw
                vt = (math.floor(px[2]) + bb[1] - 1) * 16 / fh
                vb = (math.floor(px[2]) + bb[3] + 1) * 16 / fh
                su = sorted(((ul - u0) / (u1 - u0), (ur - u0) / (u1 - u0)))
                if name == 'north':
                    su = sorted((1 - su[0], 1 - su[1]))  # en la cara norte la u va de to.x a from.x
                tv = sorted(((vt - v0) / (v1 - v0), (vb - v0) / (v1 - v0)))
                cur = [max(0.0, su[0]), min(1.0, su[1]), max(0.0, tv[0]), min(1.0, tv[1])]
                box = cur if box is None else [min(box[0], cur[0]), max(box[1], cur[1]), min(box[2], cur[2]), max(box[3], cur[3])]
            if box in (None, 'empty') or (box[1] - box[0] > 0.97 and box[3] - box[2] > 0.97):
                continue
            s0, s1, t0, t1 = box
            dx, dy = t[0] - f[0], t[1] - f[1]
            x0, x1 = f[0] + s0 * dx, f[0] + s1 * dx
            ytop, ybot = t[1] - t0 * dy, t[1] - t1 * dy
            for name, fd in (('south', big[0]), ('north', big[1])):
                u0, v0, u1, v1 = fd['uv']
                a, b = (s0, s1) if name == 'south' else (1 - s1, 1 - s0)
                fd['uv'] = [round(u0 + a * (u1 - u0), 5), round(v0 + t0 * (v1 - v0), 5),
                            round(u0 + b * (u1 - u0), 5), round(v0 + t1 * (v1 - v0), 5)]
            el['from'] = [round(min(x0, x1), 5), round(min(ytop, ybot), 5), f[2]]
            el['to'] = [round(max(x0, x1), 5), round(max(ytop, ybot), 5), t[2]]
            trimmed += 1
        return trimmed

    def write_model(self, path_rel, data):
        self.planes_trimmed += self.trim_planes(data)
        self.zfight_fixed += separate_coplanar(data)
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
        fallback = {}
        within = self.prefix.strip('/')
        for dirpath, _dirs, files in os.walk(self.root):
            if 'trims' in dirpath or (within and within not in dirpath):
                continue
            for f in files:
                low = f.lower()
                if not low.endswith('.png') or low.endswith('_e.png') or 'overlay' in low:
                    continue
                layer = 1 if ('layer_1' in low or low == 'armor_main.png') else 2 if ('layer_2' in low or low == 'armor_leggings.png') else None
                # Otros packs las llaman <set>_chestplate.png / <set>_leggings.png (capa de 64×32 o más).
                by_piece = 1 if low.endswith('_chestplate.png') else 2 if low.endswith('_leggings.png') else None
                if not layer and not by_piece:
                    continue
                path = os.path.join(dirpath, f)
                try:
                    w, h = Image.open(path).size
                except Exception:
                    continue
                if w % 2 or h % (w // 2):
                    continue
                if layer:
                    best.setdefault(layer, path)
                elif w >= 64 and w == 2 * h:
                    fallback.setdefault(by_piece, path)
        # Los nombres layer_1/layer_2 mandan; los de pieza solo cubren lo que falte.
        for layer, path in fallback.items():
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
              'cape': ('top', 0.02), 'tail': ('top', 0.60), 'quiver': ('center', 0.38)}


# Los packs colocan los cosméticos de espalda para los plugins de cosméticos, que ponen el objeto en la cabeza de un
# soporte de armadura montado encima del jugador. Ese punto queda más alto que el de nuestra capa, así que todos los
# packs traen el mismo desplazamiento hacia abajo (en píxeles de "display"). Medido comparando los packs sin piezas
# raras con su forma real: 29-35, mediana 32.
STAND_OFFSET = 32.0
# Altura de la mitad de lo que se ve (en bloques, desde el cuello, hacia arriba +) cuando el pack no trae posición.
# Para las alas es la mediana de las alas que sí la traen (-0.24).
TARGET_Y50 = {'wings': -0.24, 'wing': -0.24, 'backpack': -0.5, 'cape': -0.4, 'tail': -1.0, 'quiver': -0.4}
PACK_Y50_RANGE = {'wings': (-0.7, 0.1), 'wing': (-0.7, 0.1), 'backpack': (-0.8, -0.2), 'quiver': (-0.8, -0.2)}
MAX_WIDTH = 3.3        # ancho máximo de lo visible, en bloques
BACK_SURFACE = 0.125   # la espalda del jugador, en bloques desde el centro del cuerpo
BACK_GAP_MAX = 0.17    # si lo de delante del cosmético queda más lejos que esto, se acerca...
BACK_GAP_TO = 0.15     # ...hasta aquí (pegado a la espalda, sin atravesarla)


# Cosméticos que el pack llama «wing» pero que son otra cosa (el conejo de Pascua es un peluche-mochila).
KIND_BY_TAG = {'easter/wing': 'backpack'}
# Alas cuya posición del pack quedaba baja (o cuya mitad visible no es su raíz), revisadas en el probador: se calcula
# por su forma con esta altura de la mitad de lo visible (bloques desde el cuello).
# - eagle: el pack las pone a la altura de la cintura (su soporte de armadura está más alto que el de los demás).
# - oni: el aro de arriba sube la mitad de lo visible; así la máscara del centro queda entre los omóplatos.
Y50_BY_TAG = {'eagle/wing': -0.24, 'oni/wings': -0.02}
# Sets que ya estaban bien con el modelo del objeto (su modelo de HMCCosmetics trae una peana debajo).
HMC_SKIP = {'shadow'}
# Alto máximo de lo visible (bloques) y borde de arriba (desde el cuello) de lo que va como mochila: no tapa la cabeza.
BACKPACK_MAX_HEIGHT = 0.85
BACKPACK_TOP = 0.05
QUIVER_TILT = 28.0  # grados respecto a la vertical


def _euler_xyz(m):
    """Ángulos (grados) de R = Rx·Ry·Rz, como los aplica Minecraft en «display»."""
    b = math.asin(max(-1.0, min(1.0, m[0][2])))
    if abs(math.cos(b)) > 1e-6:
        a = math.atan2(-m[1][2], m[2][2])
        c = math.atan2(-m[0][1], m[0][0])
    else:
        a = math.atan2(m[2][1], m[1][1])
        c = 0.0
    return [round(math.degrees(v), 2) for v in (a, b, c)]


def upright_diagonal(pts, wts, tilt):
    """Giro que pone lo largo del objeto en vertical y lo más fino hacia la espalda, inclinado tilt grados."""
    centered = [[p[0] / 16 - 0.5, p[1] / 16 - 0.5, p[2] / 16 - 0.5] for p in pts]
    best, best_score = None, None
    for rx in (0, 90, 180, 270):
        for ry in (0, 90, 180, 270):
            for rz in (0, 90, 180, 270):
                m = _mul(_mul(_rot('x', rx), _rot('y', ry)), _rot('z', rz))
                vs = [_apply(m, p) for p in centered]
                sp = [_wquantile([v[i] for v in vs], wts, 0.97) - _wquantile([v[i] for v in vs], wts, 0.03) for i in range(3)]
                score = sp[1] - sp[2] - 0.2 * sp[0]
                if best_score is None or score > best_score + 1e-6:
                    best, best_score = m, score
    return _euler_xyz(_mul(_rot('z', tilt), best))


def _wquantile(vals, weights, q):
    order = sorted(range(len(vals)), key=lambda i: vals[i])
    total = sum(weights)
    acc = 0.0
    for i in order:
        acc += weights[i]
        if acc >= q * total:
            return vals[i]
    return vals[order[-1]]


def visible_points(model, alpha_of, n=12):
    """Puntos de las caras donde la textura no es transparente (coordenadas del modelo, con el giro de cada elemento),
    con su peso (superficie). Los planos grandes casi vacíos y los efectos sueltos apenas cuentan."""
    textures = model.get('textures', {})
    pts, wts = [], []
    for el in model.get('elements', []):
        f, t = el['from'], el['to']
        r = el.get('rotation')
        m = _rot(r['axis'], float(r['angle'])) if r and r.get('angle') else None
        for face, fd in el.get('faces', {}).items():
            ref = textures.get(str(fd.get('texture', '')).lstrip('#'), '')
            while ref.startswith('#'):
                ref = textures.get(ref[1:], '')
            alpha = alpha_of(ref)
            if alpha is None:
                continue
            tl, tr, bl = [list(map(float, c)) for c in B.face_corners(f, t, face)]
            if m is not None:
                o = r['origin']
                tl, tr, bl = [[a + b for a, b in zip(_apply(m, [c[0] - o[0], c[1] - o[1], c[2] - o[2]]), o)] for c in (tl, tr, bl)]
            eu = [tr[i] - tl[i] for i in range(3)]
            ev = [bl[i] - tl[i] for i in range(3)]
            cx = [eu[1] * ev[2] - eu[2] * ev[1], eu[2] * ev[0] - eu[0] * ev[2], eu[0] * ev[1] - eu[1] * ev[0]]
            area = math.sqrt(sum(c * c for c in cx))
            if area < 1e-6:
                continue
            uv = fd.get('uv', [0, 0, 16, 16])
            rot_uv = fd.get('rotation', 0) % 360
            h, w = len(alpha), len(alpha[0])
            for i in range(n):
                for j in range(n):
                    a, b = (i + 0.5) / n, (j + 0.5) / n
                    su, sv = {0: (a, b), 90: (b, 1 - a), 180: (1 - a, 1 - b), 270: (1 - b, a)}[rot_uv]
                    u = (uv[0] + su * (uv[2] - uv[0])) / 16 * w
                    v = (uv[1] + sv * (uv[3] - uv[1])) / 16 * h
                    if alpha[min(h - 1, max(0, int(v)))][min(w - 1, max(0, int(u)))] > 25:
                        pts.append([tl[k] + a * eu[k] + b * ev[k] for k in range(3)])
                        wts.append(area / (n * n))
    return pts, wts


def snap_face_uv(face, fw, fh):
    uv = face.get('uv')
    if not uv or len(uv) != 4:
        return
    out = list(uv)
    for i, n in ((0, fw), (2, fw), (1, fh), (3, fh)):
        j = i + 2 if i < 2 else i - 2
        a, b = uv[i] * n / 16, uv[j] * n / 16
        if abs(a - b) < 1e-6:
            continue  # un solo texel (ya arreglado)
        r = round(a)
        if 1e-4 < abs(a - r) <= 0.3:
            inward = 0.02 if b > a else -0.02
            out[i] = round((r + inward) * 16 / n, 5)
    face['uv'] = out


def drop_floor_plates(model):
    """Quita las «peanas»: planos horizontales grandes debajo de todo (el pack las pone para verlo en el inventario o
    como aura en el suelo). Puesto en la espalda quedaban a la altura de los pies."""
    els = model.get('elements') or []
    ys = [v for e in els for v in (e['from'][1], e['to'][1])]
    if not ys:
        return
    lo, hi = min(ys), max(ys)
    keep = []
    for e in els:
        d = [abs(e['to'][k] - e['from'][k]) for k in range(3)]
        if d[1] < 0.05 and d[0] * d[2] > 60 and min(e['from'][1], e['to'][1]) <= lo + 0.15 * (hi - lo):
            continue
        keep.append(e)
    model['elements'] = keep


def place_on_back(model, slug, tag, designed, alpha_of):
    """Coloca el modelo en la espalda del jugador.

    El mod lo dibuja como un objeto puesto en la cabeza (como CustomHeadLayer) pero siguiendo el cuerpo. Se mide con
    lo que de verdad se ve (los píxeles no transparentes):
    - Altura: con modelo de cosmético del pack (designed) se respeta la de su autor, corrigiendo solo la del soporte de
      armadura de los plugins (STAND_OFFSET); sin él, la mitad de lo visible va a TARGET_Y50.
    - Fondo: lo de delante queda justo detrás de la espalda (los packs lo dejaban separado). Con posición del pack
      solo se acerca, nunca se aleja.
    - Centrado a lo ancho.
    """
    kind = KIND_BY_TAG.get(tag, slug)
    head = dict(model.get('display', {}).get('head', {}))
    rot = list(head.get('rotation', [0, 0, 0]))
    scale = head.get('scale', [1, 1, 1])
    pts, wts = visible_points(model, alpha_of)
    if not pts:
        problems.append(f'{tag}: el cosmético no tiene nada visible')
        return

    def oriented(scl):
        R = _mul(_mul(_rot('x', rot[0]), _rot('y', rot[1])), _rot('z', rot[2]))
        return [_apply(R, [scl[0] * (p[0] / 16 - 0.5), scl[1] * (p[1] / 16 - 0.5), scl[2] * (p[2] / 16 - 0.5)]) for p in pts]

    def q(vs, i, x):
        return _wquantile([v[i] for v in vs], wts, x)

    def mid(vs, i):
        # Mitad de lo visible; estable aunque lo visible esté en dos grupos (puntas arriba, raíz abajo).
        return (q(vs, i, 0.35) + q(vs, i, 0.5) + q(vs, i, 0.65)) / 3

    def spread(vs, i):
        return q(vs, i, 0.97) - q(vs, i, 0.03)

    # El modelo del objeto también puede traer la posición del plugin (muy por debajo: soporte de armadura). Se usa la
    # del pack solo si deja la mitad de lo visible a una altura creíble de la espalda; si no, se calcula.
    designed = (designed or (head.get('translation') or [0, 0])[1] < -20) and kind != 'quiver' and tag not in Y50_BY_TAG
    mode = 'forma'
    if designed and head.get('translation') and any(scale):
        trans = [float(v) for v in head['translation']]
        trans[1] += STAND_OFFSET
        y50 = 0.25 + 0.625 * (trans[1] / 16 + mid(oriented([float(v) for v in scale]), 1))
        lo, hi = PACK_Y50_RANGE.get(kind, (-1.2, 0.2))
        if lo <= y50 <= hi:
            mode = 'pack'
            scale = [float(v) for v in scale]
    if mode == 'forma':
        trans = [0.0, 0.0, 0.0]
        unit = oriented([1, 1, 1])
        # Carcaj: de pie y en diagonal sobre la espalda (del hombro a la cadera), con lo plano pegado a ella.
        if kind == 'quiver':
            rot = upright_diagonal(pts, wts, QUIVER_TILT)
            unit = oriented([1, 1, 1])
        # Alas de lado (envergadura en z): se giran 90 grados para que se abran a los lados.
        if slug in ('wings', 'wing') and spread(unit, 2) > 1.5 * spread(unit, 0):
            rot[1] = (rot[1] + 90) % 360
            unit = oriented([1, 1, 1])
        if not any(scale):
            # Algunos packs esconden el modelo en la cabeza (escala 0): se le da un tamaño razonable.
            k = (2.4 if kind in ('wings', 'wing') else 0.7) / 0.625 / max(spread(unit, 0), 0.05)
            scale = [k, k, k]
        scale = [max(-4.0, min(4.0, float(v))) for v in scale]
    vs = oriented(scale)
    # Tamaño máximo razonable a lo ancho (en bloques): más grande tapa la pantalla y atraviesa paredes.
    width = spread(vs, 0) * 0.625
    if width > MAX_WIDTH:
        k = MAX_WIDTH / width
        scale = [v * k for v in scale]
        vs = oriented(scale)
    # Marco del objeto «en la cabeza»: +y arriba, +z hacia la espalda, 1 unidad = 0.625 bloques, origen 0.25 bloques por
    # encima del cuello. En el cuerpo: y = 0.25 + 0.625 * (t/16 + v), z = 0.625 * (t/16 + v).
    body_y = lambda qq: 0.25 + 0.625 * (trans[1] / 16 + qq)
    body_z = lambda qq: 0.625 * (trans[2] / 16 + qq)
    if mode == 'forma':
        y50 = body_y(mid(vs, 1))
        trans[1] += (Y50_BY_TAG.get(tag, TARGET_Y50.get(kind, -0.3)) - y50) / 0.625 * 16
    if kind in ('backpack', 'quiver'):
        height = spread(vs, 1) * 0.625
        if height > BACKPACK_MAX_HEIGHT and mode == 'forma':
            k = BACKPACK_MAX_HEIGHT / height
            scale = [v * k for v in scale]
            vs = oriented(scale)
            trans[1] += (TARGET_Y50.get(kind, -0.3) - body_y(mid(vs, 1))) / 0.625 * 16
        top = body_y(q(vs, 1, 0.97))
        if top > BACKPACK_TOP:
            trans[1] -= (top - BACKPACK_TOP) / 0.625 * 16
    z_front = body_z(q(vs, 2, 0.05))
    if z_front > BACK_GAP_MAX or mode == 'forma':
        trans[2] -= (z_front - BACK_GAP_TO) / 0.625 * 16
    x_mid = (q(vs, 0, 0.03) + q(vs, 0, 0.97)) / 2 + trans[0] / 16
    if abs(x_mid) * 0.625 > 0.02:
        trans[0] -= x_mid * 16
    trans = [round(v, 3) for v in trans]
    if any(abs(v) > 80 for v in trans):
        problems.append(f'{tag}: el cosmético queda demasiado lejos ({trans})')
        trans = [max(-80, min(80, v)) for v in trans]
    head.update({'rotation': rot, 'translation': trans, 'scale': [round(v, 4) for v in scale]})
    model.setdefault('display', {})['head'] = head
    back_report.append((tag, mode, rot, trans, scale))


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


# Better Combat: cada arma usa una de sus plantillas (combos, alcance, animaciones). Se busca por el nombre del objeto
# (en orden) y si no, por el tipo. Arcos, ballestas, cañas y escudos se quedan como en Minecraft.
BC_BY_NAME = [
    (('great_sword', 'greatsword', 'big_sword'), 'claymore'),
    (('rapier',), 'rapier'),
    (('dagger', 'knife'), 'dagger'),
    (('sickle',), 'sickle'),
    (('scythe', 'death_grip', 'kaz_sc', 'pox_spreader'), 'scythe'),
    (('spear', 'last_rose'), 'spear'),
    (('halberd', 'battle_standart', 'flag'), 'glaive'),
    (('staff', 'flowering_madness'), 'battlestaff'),
    (('battle_axe', 'battleaxe'), 'double_axe'),
    (('pickaxe',), 'pickaxe'),
    (('gauntlet',), 'fist'),
    (('hammer',), 'hammer'),
    (('mace', 'club'), 'mace'),
    (('trident',), 'trident'),
]
BC_BY_TYPE = {'sword': 'sword', 'axe': 'axe', 'heavy': 'hammer', 'pickaxe': 'pickaxe', 'trident': 'trident'}
BC_HEAVY_AXES = ('dark_moon_axe', 'gargoyle_axe', 'hellspawn_axe', 'mana_axe')  # hachas de guerra (Nazgul)


def better_combat_preset(item_type, slug):
    if item_type in ('bow', 'crossbow', 'fishing_rod', 'shield', 'armor', 'head', 'back', 'shovel', 'hoe'):
        return None
    if slug in BC_HEAVY_AXES:
        return 'heavy_axe'
    for keys, preset in BC_BY_NAME:
        if any(k in slug for k in keys):
            return preset
    return BC_BY_TYPE.get(item_type)


def write_weapon_attributes(sets_out):
    """data/tfclient/weapon_attributes/<objeto>.json → {"parent": "bettercombat:<plantilla>"}. Sin Better Combat no
    hace nada; con él, las armas hacen sus combos."""
    folder = os.path.join(ASSETS, '..', '..', 'data', 'tfclient', 'weapon_attributes')
    shutil.rmtree(folder, ignore_errors=True)
    os.makedirs(folder)
    count = {}
    for s in sets_out:
        for i in s['items']:
            preset = better_combat_preset(i['type'], i['id'][len(s['id']) + 1:])
            if not preset:
                continue
            with open(os.path.join(folder, i['id'] + '.json'), 'w') as fh:
                json.dump({'parent': f'bettercombat:{preset}'}, fh)
            count[preset] = count.get(preset, 0) + 1
    print('Better Combat:', sum(count.values()), 'armas', dict(sorted(count.items(), key=lambda kv: -kv[1])))


def write_accessory_tags(sets_out):
    """Huecos de accesorios para los cosméticos de espalda, así el pecho queda libre para la pechera:
    - Accessories (wispforest): etiquetas accessories:back (y accessories:cape para las capas).
    - Curios (lo usa Artifacts en 1.20.1): etiqueta curios:back y el hueco «back» activado para los jugadores.
    Si esos mods no están, estos datos no hacen nada."""
    data = os.path.join(ASSETS, '..', '..', 'data')
    back = [f'tfclient:{i["id"]}' for s in sets_out for i in s['items'] if i['type'] == 'back']
    capes = [f'tfclient:{i["id"]}' for s in sets_out for i in s['items'] if i['type'] == 'back' and i['id'].endswith('_cape')]
    files = {
        'accessories/tags/items/back.json': {'replace': False, 'values': back},
        'accessories/tags/items/cape.json': {'replace': False, 'values': capes},
        'curios/tags/items/back.json': {'replace': False, 'values': back},
        # El hueco «back» es uno de los predefinidos de Curios (icono y orden suyos); se declara aquí por si ningún otro
        # mod lo hace. Curios junta los archivos del mismo hueco y se queda con el tamaño mayor: no le quita a nadie.
        'tfclient/curios/slots/back.json': {'size': 1},
        'tfclient/curios/entities/tf_back.json': {'entities': ['minecraft:player'], 'slots': ['back']},
    }
    for rel, content in files.items():
        path = os.path.join(data, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w') as fh:
            json.dump(content, fh, indent=1)
    print(f'Accesorios: {len(back)} cosméticos de espalda ({len(capes)} capas) en accessories:back / curios:back')


def remove_set(set_id, old_entry):
    """Borra lo que el mod tiene de un set (para rehacerlo sin tocar los demás)."""
    for item in (old_entry or {}).get('items', []):
        path = os.path.join(ASSETS, 'models', 'item', item['id'] + '.json')
        if os.path.exists(path):
            os.remove(path)
    shutil.rmtree(os.path.join(ASSETS, 'models', 'item', 'sets', set_id), ignore_errors=True)
    shutil.rmtree(os.path.join(ASSETS, 'textures', 'item', 'sets', set_id), ignore_errors=True)
    armor = os.path.join(ASSETS, 'textures', 'models', 'armor')
    for f in os.listdir(armor) if os.path.isdir(armor) else []:
        if f.startswith(set_id + '_layer_'):
            os.remove(os.path.join(armor, f))


def main(packs, only=None):
    """Sin `only` rehace todos los sets (hacen falta los packs de todos). Con `only` rehace solo esos y deja los demás
    como están en tf_sets.json (así se puede añadir un kit nuevo sin tener a mano los packs de los otros)."""
    registry = os.path.join(ASSETS, 'tf_sets.json')
    previous = {s['id']: s for s in json.load(open(registry, encoding='utf-8'))['sets']} if os.path.exists(registry) else {}
    old_lang = json.load(open(os.path.join(ASSETS, 'lang', 'es_es.json'), encoding='utf-8')) if only else {}
    if only:
        for set_id in only:
            remove_set(set_id, previous.get(set_id))
    else:
        for sub in ('models/item', 'textures/item/sets', 'textures/models/armor'):
            path = os.path.join(ASSETS, sub)
            if sub == 'models/item':
                for f in os.listdir(path) if os.path.isdir(path) else []:
                    if f != 'sets' and f.endswith('.json') and '_' in f and not f.startswith('job_'):
                        os.remove(os.path.join(path, f))
                shutil.rmtree(os.path.join(path, 'sets'), ignore_errors=True)
            else:
                shutil.rmtree(path, ignore_errors=True)
    sets_out, lang = [], {'itemGroup.tfclient.sets': 'Tierras Fantásticas · Sets'}
    total = 0
    for spec in C.mod_sets():
        set_id, set_name, color = spec['id'], spec['name'], spec['color']
        if only and set_id not in only:
            # Se queda como estaba; solo se actualiza su nivel de atributos
            entry = previous.get(set_id)
            if not entry:
                problems.append(f'{set_id}: no está en tf_sets.json y no se ha pedido construirlo')
                continue
            entry = {**entry, 'tier': spec['tier']}
            sets_out.append(entry)
            lang.update({k: v for k, v in old_lang.items()
                         if k == f'tfclient.set.{set_id}' or any(k == f'item.tfclient.{i["id"]}' for i in entry['items'])})
            total += len(entry['items'])
            continue
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
                    designed = False
                    base = full.split('/')[-1]
                    candidates = []
                    for ref in ([] if set_id in HMC_SKIP else w.hmc['BACKPACK']):
                        ns, path = ref.split(':', 1) if ':' in ref else (w.ns, ref)
                        if ns == w.ns and path.split('/')[-1].startswith(base):
                            candidates.append((path, '_' + path.split('/')[-1][len(base):].lstrip('_')))
                    candidates += [(full + sfx, sfx) for sfx in ('_cosmetic', '_cosmetics', '_cosmeticscore', '_thirdperson', '_1')]
                    for name, suffix in candidates:
                        m = w.model(name)
                        if m:
                            worn = w.convert(m, tag + suffix)
                            designed = suffix != '_1'
                            worn_from[tag] = name
                            break
                    worn = worn or json.loads(json.dumps(model))
                    worn.pop('overrides', None)
                    drop_floor_plates(worn)
                    place_on_back(worn, slug, tag, designed, w.alpha_of)
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
        entry = {'id': set_id, 'name': set_name, 'color': color, 'tier': spec['tier'], 'items': items}
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
        print(f'{set_id}: {len(items)} objetos' + (f', {w.flat_uv_fixed} caras con UV plana arregladas' if w.flat_uv_fixed else '')
              + (f', {w.zfight_fixed} caras superpuestas separadas' if w.zfight_fixed else ''))
    with open(os.path.join(ASSETS, 'tf_sets.json'), 'w') as fh:
        json.dump({'sets': sets_out}, fh, ensure_ascii=False, indent=1)
    write_accessory_tags(sets_out)
    write_weapon_attributes(sets_out)
    os.makedirs(os.path.join(ASSETS, 'lang'), exist_ok=True)
    for lang_file in ('es_es.json', 'en_us.json'):
        path = os.path.join(ASSETS, 'lang', lang_file)
        old = json.load(open(path, encoding='utf-8')) if os.path.exists(path) else {}
        old = {k: v for k, v in old.items()
               if k.startswith('item.tfclient.job_') or not k.startswith(('item.tfclient.', 'tfclient.set.', 'itemGroup.tfclient'))}
        with open(path, 'w', encoding='utf-8') as fh:
            json.dump({**old, **lang}, fh, ensure_ascii=False, indent=1)
    print(f'TOTAL {total} objetos en {len(sets_out)} sets; mipmap mínimo {mip_low[0]}px')
    print('Espalda:')
    for tag, mode, rot, trans, scale in back_report:
        print(f'  {tag:28s} {mode:5s} rot={rot} trans={trans} scale={[round(v, 3) for v in scale]}'
              f'  modelo={worn_from.get(tag, "el del objeto")}')
    print(f'{len(problems)} avisos')
    for p in problems:
        print('  -', p)


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2:] or None)
