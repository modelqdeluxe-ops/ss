"""Genera los recursos de las clases de skills del TF Client a partir de los packs comprados.

    python3 tools/skills/build_skills.py <carpeta con los packs descomprimidos> [pack ...]

Por cada pack (clase):
  - compila sus skills de MythicMobs (compile.py) y reescribe las referencias a los nombres del mod
  - modelos de ítem (lo que llevan en la cabeza los soportes de efectos, cambiando de fotograma con equip):
    ítem de MythicMobs → material + CustomModelData → override del resource pack → modelo JSON y sus texturas →
    assets/tfclient/models/skills/<clase>/... y textures/skills/<clase>/...
  - modelos animados de ModelEngine (.bbmodel) → assets/tfclient/skills/models/<clase>.<modelo>.json (vfx_bb.py)
  - sonidos del pack → assets/tfclient/sounds/skills/<clase>/ y sus eventos en sounds.json (tfclient:skills.<clase>.<x>)
  - iconos de las skills (ítem de MMOCore) → assets/tfclient/textures/gui/skills/<clase>/<skill>.png
  - programa de la clase → assets/tfclient/skills/classes/<clase>.json y el índice skills/catalog.json
Los packs no se suben al repo: solo lo convertido (como con los sets y los VFX).
"""
import json
import os
import re
import shutil
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)
sys.path.insert(0, HERE)
sys.path.insert(0, TOOLS)
import compile as C  # noqa: E402
import vfx_bb as B  # noqa: E402
import vfx_compile as VC  # noqa: E402
import vfx_render as R  # noqa: E402
import jsonmodel as J  # noqa: E402

RES = os.path.normpath(os.path.join(TOOLS, '..', 'src', 'main', 'resources', 'assets', 'tfclient'))
OUT_CLASSES = os.path.join(RES, 'skills', 'classes')
OUT_MODELS = os.path.join(RES, 'skills', 'models')
OUT_TEX = os.path.join(RES, 'textures', 'skills')
OUT_ITEM_MODELS = os.path.join(RES, 'models', 'skills')
OUT_SOUNDS = os.path.join(RES, 'sounds', 'skills')
OUT_ICONS = os.path.join(RES, 'textures', 'gui', 'skills')
SOUNDS_JSON = os.path.join(RES, 'sounds.json')

# Prioridad de los resource packs dentro de un pack (el primero que tenga el archivo gana)
ROOT_ORDER = [r'resourcepack \(for players\)', r'raw resource pack', r'for vanilla', r'modelengine',
              r'itemsadder', r'nexo', r'oraxen', r'mythicrpg']
ROOT_SKIP = re.compile(r'yellow|/merge/', re.I)


def slug(s):
    s = re.sub(r'[^a-z0-9]+', '_', str(s).lower()).strip('_')
    return s or 'x'


# Nombre de la clase en el mod y en la web (id corto y estable)
CLASS_IDS = {
    'Glacia-v1.2': 'glacia', 'ZEPHYR-v1.3': 'zephyr', 'Red-DragonPack': 'dragon_rojo', 'MAGEPACK-1.1': 'mago',
    'samus2002_NECROMANCER': 'nigromante', 'THORPACK-1.3': 'thor', 'NULLPACK-1.1': 'null',
}


# Packs que traen varias versiones: la carpeta de la completa (con todas las skills de la clase)
PACK_SUBDIR = {
    'PackClass_Magic_Lightning': 'PackClass_Magic_Lightning/PackClass_Magic_Lightning/'
                                 'PackClass_Magic_Lightning_Skills_Untimate_V3/PackClass_Magic_Lightning_Skills_Untimate_V3/'
                                 'PackClass_Magic_Lightning_Skills_Untimate_V3/config to Oraxen',
}


# Packs que no son una clase de jugador (samus2002_NECROMANCER es un jefe: sus skills mueven la IA y el modelo del mob;
# la clase de nigromante es la de AWAKENED_NECROMANCER)
SKIP_PACKS = {'samus2002_NECROMANCER'}


# Tandas publicadas: solo estas clases entran en el mod (y en la web). Se añade una tanda cada vez.
TANDAS = [
    ['Glacia-v1.2', 'ZEPHYR-v1.3', 'MAGEPACK-1.1', 'Red-DragonPack', 'THORPACK-1.3'],
]
RELEASED = {p for t in TANDAS for p in t}


def pack_path(root, name):
    return os.path.join(root, name, PACK_SUBDIR[name]) if name in PACK_SUBDIR else os.path.join(root, name)


class Pack:
    def __init__(self, path, name=None):
        self.path = path
        self.name = name or os.path.basename(path)
        self.id = CLASS_IDS.get(self.name) or slug(re.sub(r'samus2002_|_pack|_v?\d[\d.]*$|-v?\d[\d.]*$', '',
                                                          self.name, flags=re.I))
        self.roots = self.find_roots()
        # Oraxen guarda el pack sin assets/minecraft: Oraxen/pack/{models,textures,sounds}
        self.oraxen = [d for d, dirs, _ in os.walk(self.path)
                       if os.path.basename(d) == 'pack' and ('models' in dirs or 'textures' in dirs)
                       and not ROOT_SKIP.search(d)]
        self.problems = []
        self.config_models = config_models(self.path)

    def find_roots(self):
        roots = []
        for d, dirs, _ in os.walk(self.path):
            if os.path.basename(d) == 'assets' and not ROOT_SKIP.search(d):
                roots.append(d)

        def rank(r):
            low = r.lower()
            for i, pat in enumerate(ROOT_ORDER):
                if re.search(pat, low):
                    return i
            return len(ROOT_ORDER)
        return sorted(roots, key=rank)

    def find(self, rel):
        """Ruta de un archivo de assets («ns/models/x.json») en el primer resource pack que lo tenga."""
        for r in self.roots:
            p = os.path.join(r, rel)
            if os.path.exists(p):
                return p
        if rel.startswith('minecraft/'):
            for r in self.oraxen:
                p = os.path.join(r, rel[len('minecraft/'):])
                if os.path.exists(p):
                    return p
        return None


def config_models(path):
    """(material, CustomModelData) → «ns:modelo» según la configuración de ItemsAdder y Oraxen del pack (cuando el
    plugin genera el override y el pack no lo trae hecho)."""
    out = {}
    for d, _, fs in os.walk(path):
        low = d.lower()
        if ROOT_SKIP.search(d) or not ('itemsadder' in low or 'oraxen' in low or 'nexo' in low):
            continue
        for f in fs:
            if not f.endswith('.yml'):
                continue
            data = C.load(os.path.join(d, f)) if hasattr(C, 'load') else None
            if not isinstance(data, dict):
                continue
            ns = ((data.get('info') or {}).get('namespace') if isinstance(data.get('info'), dict) else None)
            items = data.get('items') if isinstance(data.get('items'), dict) else data
            for it in items.values():
                if not isinstance(it, dict):
                    continue
                res = it.get('resource') if isinstance(it.get('resource'), dict) else None
                pk = it.get('Pack') if isinstance(it.get('Pack'), dict) else None
                if res and res.get('model_id') and ns:
                    mat = str(res.get('material', 'paper')).lower()
                    key = (mat, int(res['model_id']))
                    tex = res.get('textures') or res.get('texture')
                    if res.get('model_path'):
                        out.setdefault(key, f'{ns}:{res["model_path"]}')
                    elif tex:
                        t = str(tex[0] if isinstance(tex, list) else tex)
                        out.setdefault(key, 'tex:' + (t if ':' in t else f'{ns}:{t}').removesuffix('.png'))
                elif pk and pk.get('custom_model_data'):
                    mat = str(it.get('material', 'paper')).lower()
                    key = (mat, int(pk['custom_model_data']))
                    tex = pk.get('textures') or pk.get('texture')
                    if pk.get('model'):
                        model = str(pk['model'])
                        out.setdefault(key, model if ':' in model else f'minecraft:{model}')
                    elif tex:
                        t = str(tex[0] if isinstance(tex, list) else tex)
                        out.setdefault(key, 'tex:' + (t if ':' in t else f'minecraft:{t}').removesuffix('.png'))
    return out


# ---------------------------------------------------------------------------------------------------------------
# Modelos de ítem
# ---------------------------------------------------------------------------------------------------------------

def split_ref(ref, default_ns='minecraft'):
    ref = str(ref)
    if ':' in ref:
        ns, path = ref.split(':', 1)
    else:
        ns, path = default_ns, ref
    return ns, path


def item_override(pack, material, cmd):
    """Modelo («ns:ruta») que pone el resource pack al ítem vanilla con ese CustomModelData."""
    mat = str(material).lower().split(':')[-1]
    try:
        conf = pack.config_models.get((mat, int(float(cmd))))
    except (TypeError, ValueError):
        conf = None
    p = pack.find(f'minecraft/models/item/{mat}.json')
    if not p:
        return conf
    try:
        data = json.load(open(p, encoding='utf-8'))
    except Exception:  # noqa: BLE001
        return None
    for ov in data.get('overrides', []):
        pred = ov.get('predicate', {})
        if 'custom_model_data' in pred and int(float(pred['custom_model_data'])) == int(float(cmd)):
            return ov.get('model')
    return conf


class ItemModels:
    """Copia modelos de ítem (con sus padres y texturas) al espacio del mod, sin repetir."""

    def __init__(self, pack):
        self.pack = pack
        self.done = {}
        self.src = {}  # id en el mod → modelo del pack

    def model(self, ref):
        """«ns:ruta» de un modelo del pack → id del modelo en el mod («tfclient:skills/<clase>/...») o None."""
        if str(ref).startswith('tex:'):  # el plugin genera un modelo plano con esa textura
            key = str(ref)
            if key not in self.done:
                tex = self.texture(key[4:], 'minecraft')
                tns, tpath = split_ref(tex)
                out_rel = f'{self.pack.id}/generated/{tpath.split("/", 1)[-1]}'.lower()
                dest = os.path.join(OUT_ITEM_MODELS, *out_rel.split('/')) + '.json'
                os.makedirs(os.path.dirname(dest), exist_ok=True)
                with open(dest, 'w', encoding='utf-8') as f:
                    json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': tex}}, f)
                self.done[key] = f'tfclient:skills/{out_rel}'
            return self.done[key]
        ns, path = split_ref(ref)
        if ns == 'minecraft' and (path.startswith('item/') or path.startswith('block/')) and \
                not self.pack.find(f'{ns}/models/{path}.json'):
            return f'minecraft:{path}'  # modelo vanilla
        key = f'{ns}:{path}'
        if key in self.done:
            return self.done[key]
        src = self.pack.find(f'{ns}/models/{path}.json')
        if not src:
            self.pack.problems.append(f'falta el modelo {key}')
            self.done[key] = None
            return None
        out_rel = f'{self.pack.id}/{slug(ns)}/{path}'.lower()
        out_id = f'tfclient:skills/{out_rel}'
        self.done[key] = out_id
        self.src[out_id] = key
        data = json.load(open(src, encoding='utf-8'))
        if 'parent' in data:
            pns, ppath = split_ref(data['parent'], ns)
            if not (pns == 'minecraft' and not self.pack.find(f'{pns}/models/{ppath}.json')):
                parent = self.model(f'{pns}:{ppath}')
                if parent:
                    data['parent'] = parent
        for k, v in list((data.get('textures') or {}).items()):
            if isinstance(v, str) and not v.startswith('#'):
                data['textures'][k] = self.texture(v, ns)
        data.pop('overrides', None)
        dest = os.path.join(OUT_ITEM_MODELS, *out_rel.split('/')) + '.json'
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        with open(dest, 'w', encoding='utf-8') as f:
            json.dump(data, f, separators=(',', ':'))
        return out_id

    def texture(self, ref, default_ns):
        ns, path = split_ref(ref, default_ns)
        src = self.pack.find(f'{ns}/textures/{path}.png')
        if not src:
            if ns == 'minecraft':
                return f'minecraft:{path}'
            self.pack.problems.append(f'falta la textura {ns}:{path}')
            return 'minecraft:missingno'
        out_rel = f'{self.pack.id}/{slug(ns)}/{path}'.lower()
        dest = os.path.join(OUT_TEX, *out_rel.split('/')) + '.png'
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        shutil.copyfile(src, dest)
        if os.path.exists(src + '.mcmeta'):
            shutil.copyfile(src + '.mcmeta', dest + '.mcmeta')
        return f'tfclient:skills/{out_rel}'


def mm_item_model(pack, models, item_def):
    """Ítem de MythicMobs (Id/Material + Model/CustomModelData) → modelo en el mod."""
    mat = item_def.get('Id') or item_def.get('Material') or item_def.get('material') or 'paper'
    cmd = item_def.get('Model') or item_def.get('CustomModelData') or item_def.get('custom-model-data')
    if cmd is None:
        return None
    ref = item_override(pack, mat, cmd)
    return models.model(ref) if ref else None


# ---------------------------------------------------------------------------------------------------------------
# Sonidos
# ---------------------------------------------------------------------------------------------------------------

def pack_sounds(pack):
    """Eventos de sonido del pack: «ns:evento» → [ruta de cada .ogg]."""
    out = {}
    for r in pack.roots:
        for ns in os.listdir(r):
            sj = os.path.join(r, ns, 'sounds.json')
            if not os.path.exists(sj):
                continue
            try:
                data = json.load(open(sj, encoding='utf-8'))
            except Exception:  # noqa: BLE001
                continue
            for ev, d in data.items():
                files = []
                for s in d.get('sounds', []):
                    name = s['name'] if isinstance(s, dict) else s
                    # sin espacio de nombres el juego lo busca en minecraft (muchos packs lo usan así)
                    sns, spath = split_ref(name, 'minecraft')
                    p = None
                    for cand_ns in dict.fromkeys((sns, ns, 'minecraft')):
                        p = pack.find(f'{cand_ns}/sounds/{spath}.ogg')
                        if p:
                            break
                    if p:
                        files.append(p)
                if files:
                    out.setdefault(f'{ns}:{ev}', files)
                    out.setdefault(ev, files)
    # ItemsAdder registra solo cada .ogg como «ns:carpeta.archivo» aunque no esté en sounds.json
    for r in pack.roots:
        for ns in os.listdir(r):
            sd = os.path.join(r, ns, 'sounds')
            if not os.path.isdir(sd):
                continue
            for d, _, fs in os.walk(sd):
                for f in fs:
                    if f.endswith('.ogg'):
                        ev = os.path.relpath(os.path.join(d, f[:-4]), sd).replace(os.sep, '.')
                        out.setdefault(f'{ns}:{ev}', [os.path.join(d, f)])
                        out.setdefault(ev, [os.path.join(d, f)])
    return out


# Efectos que el pack usa pero no trae: el más parecido del mismo pack (anillos mágicos del Dragón Rojo → los anillos
# de su rugido)
ITEM_ALIASES = {'dragon_magic_ring': 'dragon_roar'}

# Sonidos que el pack usa pero no trae (ni ningún otro pack de la serie): el vanilla más parecido
MISSING_SOUNDS = {
    'universal.ultimate.sfx': 'block.beacon.activate',
    'guardian.aegis.fragment.hit': 'block.amethyst_block.hit',
}
# Sonidos de todos los packs (algunos usan los de otra clase de la misma serie)
GLOBAL_SOUNDS = {}


def similar_sound(sounds, key):
    """Evento del pack que se parece al pedido (fallos del pack: otro espacio de nombres, evento renombrado o de
    otra clase de la serie): mismo final o la mayor cantidad de palabras en común con el último tramo."""
    if ':' in key and key.split(':', 1)[0] == 'minecraft':
        return None
    want = key.split(':', 1)[-1].lower()
    last = want.split('.')[-1]
    words = set(re.split(r'[._]', last)) - {'', 'sfx', 'samus'}
    best, score = None, 0
    for ev, files in sounds.items():
        if ':' in ev:
            continue
        el = ev.lower().split('.')[-1]
        if el == last:
            return files
        sc = len(words & set(re.split(r'[._]', el)))
        if sc > score:
            best, score = files, sc
    return best if words and score >= max(1, len(words) - 1) else None


# ---------------------------------------------------------------------------------------------------------------

def build_pack(path, sound_events, name=None):
    pack = Pack(path, name)
    prog = C.compile_pack(path)
    models = ItemModels(pack)
    sounds = pack_sounds(pack)
    used_sounds = {}

    def sound_ref(s):
        s = str(s).strip()
        key = s if ':' in s else s
        bare = key.split(':', 1)[-1]
        files = sounds.get(key) or sounds.get(bare) or similar_sound(sounds, key) or GLOBAL_SOUNDS.get(bare)
        if not files and bare in MISSING_SOUNDS:
            key = 'minecraft:' + MISSING_SOUNDS[bare]
        if files:
            ev = f'skills.{pack.id}.{slug(key.split(":", 1)[-1])}'
            if ev not in used_sounds:
                used_sounds[ev] = files
            return f'tfclient:{ev}'
        vanilla = key.split(':', 1)[-1] if key.startswith('minecraft:') else key
        vanilla = VC.SOUND_SUBST.get(vanilla, vanilla)
        if vanilla in VC.valid_sounds():
            return f'minecraft:{vanilla}'
        pack.problems.append(f'sonido desconocido {s}')
        return None

    # ítems de MythicMobs → modelos (solo los que usan las skills y mobs alcanzados)
    item_models = {}
    missing_items = []
    used_text = json.dumps([prog['tree'], prog['mobs']])
    for iid, idef in prog['items'].items():
        if not re.search(r'(?<![\w-])' + re.escape(iid) + r'(?![\w-])', used_text):
            continue
        mid = mm_item_model(pack, models, idef)
        if mid:
            item_models[iid] = mid
        else:
            missing_items.append(iid)
    # el pack no trae el modelo (variante de color, fotogramas que faltan): el del mismo efecto que sí está
    lower_models = {k.lower(): v for k, v in item_models.items()}
    for iid in missing_items:
        base = re.sub(r'_(green|blue|yellow|purple|red)(?=_|$)', '', iid.lower())
        for a, b in ITEM_ALIASES.items():
            base = base.replace(a, b)
        stem, num = re.match(r'(.*?)(\d*)$', base).groups()
        cands = [base] + sorted((k for k in lower_models if re.fullmatch(re.escape(stem) + r'\d+', k)),
                                key=lambda k: abs(int(re.search(r'\d+$', k).group()) - int(num or 0)))
        alt = next((lower_models[c] for c in cands if c in lower_models), None)
        if alt:
            item_models[iid] = alt
        else:
            pack.problems.append(f'ítem sin modelo {iid}')

    # modelos de ModelEngine
    me_models = {}
    store = B.TextureStore(os.path.join(OUT_TEX, pack.id, 'me'), f'tfclient:textures/skills/{pack.id}/me/')
    for d, _, fs in os.walk(path):
        for f in fs:
            if f.endswith('.bbmodel'):
                mname = f[:-len('.bbmodel')].lower()
                if mname in me_models:
                    continue
                problems = []
                try:
                    m = B.convert(os.path.join(d, f), store, problems)
                except Exception as e:  # noqa: BLE001
                    problems.append(f'{f}: {e}')
                    m = None
                pack.problems += problems
                if m:
                    mid = f'{pack.id}.{slug(mname)}'
                    os.makedirs(OUT_MODELS, exist_ok=True)
                    with open(os.path.join(OUT_MODELS, mid + '.json'), 'w', encoding='utf-8') as fo:
                        json.dump(m, fo, separators=(',', ':'))
                    me_models[mname] = mid

    # reescribir el programa
    def fix_mech(pm):
        a = pm.get('a', {})
        m = pm['m']
        if m in ('effect:sound', 'sound'):
            for k in ('s', 'sound'):
                if k in a:
                    r = sound_ref(a[k])
                    if r:
                        a[k] = r
        if m == 'equip':
            it = a.get('item') or a.get('i') or ''
            iid, _, slot = it.partition(':')
            if iid in item_models:
                a['model'] = item_models[iid]
                a['slot'] = slot or 'HEAD'
        if m in ('model', 'state', 'changepart', 'partvis', 'tint', 'remapmodel', 'mountmodel', 'lockmodel',
                 'brightness', 'bodyrotation', 'bodyclamp', 'setmodelscale'):
            for k in ('mid', 'm', 'modelid', 'model', 'newmodelid', 'nmid'):
                if k in a and str(a[k]).lower() in me_models:
                    a[k] = me_models[str(a[k]).lower()]
        return pm

    for ent in prog['tree'].values():
        ent['mechs'] = [fix_mech(pm) for pm in ent['mechs']]
    for mob in prog['mobs'].values():
        mob['mechs'] = [fix_mech(pm) for pm in mob['mechs']]
        h = mob.get('head')
        if isinstance(h, str) and h.split(':')[0] in item_models:
            mob['head_model'] = item_models[h.split(':')[0]]

    # iconos de las skills
    icons = {}
    for sk in prog['skills']:
        if str(sk.get('name', '')).strip().lower() == 'passive':
            sk['hidden'] = True  # pasiva interna del pack (la que pone en marcha el modo): sin tecla ni icono
            continue
        icon = sk.get('icon')
        png = None
        if icon:
            mat, _, cmd = str(icon).partition(':')
            ref = item_override(pack, mat, cmd) if cmd else None
            png = icon_png(pack, ref) if ref else None
        if png is None:
            png = icon_by_name(pack, sk)
        if png is None:
            png = icon_from_effects(pack, models, prog, sk, me_models, store)
        if png is None:
            pack.problems.append(f'skill sin icono {sk["id"]} ({icon})')
        if png:
            dest = os.path.join(OUT_ICONS, pack.id, sk['id'] + '.png')
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            png.save(dest)
            icons[sk['id']] = f'tfclient:textures/gui/skills/{pack.id}/{sk["id"]}.png'

    for ev, files in used_sounds.items():
        rels = []
        for i, src in enumerate(files):
            name = f'{ev.split(".")[-1]}' + (f'_{i}' if len(files) > 1 else '')
            dest = os.path.join(OUT_SOUNDS, pack.id, name + '.ogg')
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            shutil.copyfile(src, dest)
            rels.append(f'tfclient:skills/{pack.id}/{name}')
        sound_events[ev] = {'subtitle': None, 'sounds': rels}

    # Textos en español (tools/skills/es.json): nombre, rol y descripción de la clase y de cada skill
    es = ES.get(pack.id, {})
    if not es:
        pack.problems.append('sin textos en español (tools/skills/es.json)')
    cls_info = dict(prog['class'])
    for k in ('name', 'role', 'desc', 'color'):
        if es.get(k):
            cls_info[k + '_es' if k != 'color' else 'color'] = es[k]
    for sk in prog['skills']:
        t = (es.get('skills') or {}).get(sk['id'])
        if t:
            sk['name_es'], sk['desc_es'] = t['name'], t['desc']
        elif not sk.get('hidden'):
            pack.problems.append(f'skill sin texto en español: {sk["id"]}')
    prog['class'] = cls_info
    cset, mm_items = class_set(pack, prog, models, LANG_ES, LANG_EN)
    out = {'id': pack.id, 'pack': pack.name, 'class': prog['class'], 'skills': prog['skills'], 'icons': icons,
           'set': cset['id'], 'hand_items': mm_items,
           'tree': prog['tree'], 'mobs': prog['mobs'], 'models': me_models, 'item_models': item_models,
           'weapons': prog['weapons'], 'armor': prog['armor']}
    os.makedirs(OUT_CLASSES, exist_ok=True)
    with open(os.path.join(OUT_CLASSES, pack.id + '.json'), 'w', encoding='utf-8') as f:
        json.dump(out, f, ensure_ascii=False, separators=(',', ':'))
    CLASS_SETS.append(cset)
    return pack, out


LANG_ES, LANG_EN, CLASS_SETS = {}, {}, []
ES = json.load(open(os.path.join(HERE, 'es.json'), encoding='utf-8'))


def icon_png(pack, ref, depth=0):
    """Icono de una skill: la textura de su modelo plano (sigue los padres hasta dar con layer0)."""
    if str(ref).startswith('tex:'):
        tns, tpath = split_ref(ref[4:])
        p = pack.find(f'{tns}/textures/{tpath}.png')
        return square(Image.open(p)) if p else None
    ns, path = split_ref(ref)
    src = pack.find(f'{ns}/models/{path}.json')
    if not src or depth > 6:
        return None
    try:
        data = json.load(open(src, encoding='utf-8'))
    except Exception:  # noqa: BLE001
        return None
    texs = data.get('textures') or {}
    tex = texs.get('layer0') or texs.get('0') or next((v for v in texs.values() if not str(v).startswith('#')), None)
    if not tex:
        parent = data.get('parent')
        return icon_png(pack, parent, depth + 1) if parent and 'generated' not in parent and 'handheld' not in parent \
            else None
    tns, tpath = split_ref(tex, ns)
    p = pack.find(f'{tns}/textures/{tpath}.png')
    return square(Image.open(p)) if p else None


def square(img):
    img = img.convert('RGBA')
    if img.height > img.width:  # textura animada: primer fotograma
        img = img.crop((0, 0, img.width, img.width))
    return img


def icon_by_name(pack, skill):
    """Icono por nombre de archivo («icon_<skill>.png», «<skill>_icon.png»...) cuando el pack no lo enlaza."""
    keys = {slug(skill['id']), slug(skill.get('name') or '')} - {'x', ''}
    # sin prefijos de clase («lostassets_paladin_lc» → «lc») para los packs que nombran así los iconos
    best = None
    for d, _, fs in os.walk(pack.path):
        if ROOT_SKIP.search(d):
            continue
        for f in fs:
            if not f.lower().endswith('.png') or 'icon' not in f.lower():
                continue
            stem = slug(re.sub(r'icons?', '', f[:-4], flags=re.I))
            for k in keys:
                if stem == k or stem.endswith('_' + k) or k.endswith('_' + stem) and len(stem) > 3:
                    best = os.path.join(d, f)
                    if stem == k:
                        return square(Image.open(best))
    return square(Image.open(best)) if best else None


SKILL_REF_KEYS = ('s', 'skill', 'skills', 'oe', 'ot', 'oh', 'os', 'onend', 'ontick', 'onhit', 'onstart', 'then')
MOB_REF_KEYS = ('t', 'type', 'mob', 'mobtype', 'm')


def icon_from_effects(pack, models, prog, sk, me_models, store):
    """Icono dibujado del efecto de la skill cuando el pack no trae ninguno: el modelo de ítem del primer efecto que
    invoca (el fotograma del medio de su animación) o, si no hay, el modelo de ModelEngine que usa."""
    tree, mobs = prog['tree'], prog['mobs']
    lower_tree = {k.lower(): k for k in tree}
    lower_mobs = {k.lower(): k for k in mobs}
    seen, stack, me_used = set(), [sk.get('entry')], []
    while stack:
        name = stack.pop(0)
        key = lower_tree.get(str(name).lower()) if name else None
        if not key or key in seen:
            continue
        seen.add(key)
        for pm in tree[key]['mechs']:
            a = pm.get('a', {})
            for k in MOB_REF_KEYS:
                mob = lower_mobs.get(str(a.get(k, '')).lower())
                if mob and pm['m'] in ('summon', 'projectile', 'missile', 'totem', 'orbital'):
                    frames = mob_frames(mobs[mob], tree, lower_tree)
                    if frames:
                        ref = models.src.get(frames[len(frames) // 2])
                        img = J.icon(pack.find, ref) if ref else None
                        if img:
                            return J.framed(img)
            for k in SKILL_REF_KEYS:
                for nm in str(a.get(k, '')).split(','):
                    if nm.strip():
                        stack.append(nm.strip().split(' ')[0])
            for k in ('mid', 'm', 'modelid', 'model'):
                if str(a.get(k, '')).startswith(pack.id + '.'):
                    me_used.append(a[k])
    for mid in me_used:
        path = os.path.join(OUT_MODELS, mid + '.json')
        if os.path.exists(path):
            model = json.load(open(path, encoding='utf-8'))
            tex = R.load_textures(model, store.out_dir)
            img = R.render(model, tex, None, 0, size=256, yaw=-20, pitch=-8)
            box = img.getbbox()
            if box:
                img = img.crop(box)
                side = max(img.size)
                sq = Image.new('RGBA', (side, side))
                sq.paste(img, ((side - img.width) // 2, (side - img.height) // 2))
                return J.framed(sq)
    return None


def mob_frames(mob, tree, lower_tree):
    """Modelos que va poniéndose un mob de efecto (equip), también desde las skills que llama."""
    frames = [mob['head_model']] if mob.get('head_model') else []
    seen, stack = set(), [mob['mechs']]
    while stack:
        for x in stack.pop(0):
            a = x.get('a', {})
            if x['m'] == 'equip' and a.get('model'):
                frames.append(a['model'])
            for k in SKILL_REF_KEYS:
                key = lower_tree.get(str(a.get(k, '')).lower())
                if key and key not in seen:
                    seen.add(key)
                    stack.append(tree[key]['mechs'])
    return frames


# ---------------------------------------------------------------------------------------------------------------
# Armas y armaduras de la clase (vienen con ella, vinculadas): un «set» más del mod (skills/class_sets.json)
# ---------------------------------------------------------------------------------------------------------------

OUT_ITEM_JSON = os.path.join(RES, 'models', 'item')
OUT_ARMOR = os.path.join(RES, 'textures', 'models', 'armor')
WORDS_ES = {'scepter': 'Cetro', 'staff': 'Bastón', 'dagger': 'Daga', 'hammer': 'Martillo', 'sword': 'Espada',
            'katana': 'Katana', 'bow': 'Arco', 'helmet': 'Casco', 'chestplate': 'Pechera', 'leggings': 'Grebas',
            'boots': 'Botas', 'head': 'Cabeza', 'gauntlet': 'Guantelete', 'glaive': 'Guja', 'scythe': 'Guadaña',
            'axe': 'Hacha', 'spear': 'Lanza', 'wand': 'Varita', 'shield': 'Escudo', 'gun': 'Pistola', 'book': 'Libro',
            'sacred': 'Sagrado', 'gear': 'Guantelete'}
ARMOR_SLOTS = {'HELMET': 'helmet', 'CHESTPLATE': 'chestplate', 'LEGGINGS': 'leggings', 'BOOTS': 'boots'}


def name_es(name, cls_name):
    """«Scepter of Glacia» → «Cetro de Glacia» (lo que no está en la lista se queda igual)."""
    m = re.match(r'^(shiny\s+)?(.+?)\s+of\s+(.+)$', name.strip(), re.I)
    if m:
        what = WORDS_ES.get(m.group(2).lower(), m.group(2))
        return f"{what} de {m.group(3)}" + (' brillante' if m.group(1) else '')
    return ' '.join(WORDS_ES.get(w.lower(), w) for w in name.split())


def cit_layers(pack, material, cmd):
    """Capas de la armadura puesta (OptiFine CIT): la propiedad con ese CustomModelData → (capa 1, capa 2)."""
    for r in pack.roots:
        cit = os.path.join(r, 'minecraft', 'optifine', 'cit')
        if not os.path.isdir(cit):
            continue
        for d, _, fs in os.walk(cit):
            for f in fs:
                if not f.endswith('.properties'):
                    continue
                props = {}
                for line in open(os.path.join(d, f), encoding='utf-8', errors='ignore'):
                    if '=' in line:
                        k, v = line.split('=', 1)
                        props[k.strip()] = v.strip()
                if props.get('type') != 'armor':
                    continue
                want = props.get('nbt.CustomModelData')
                if want is None or int(float(want)) != int(float(cmd)):
                    continue
                if str(material).lower() not in props.get('items', '').replace('\\', '').lower():
                    continue
                out = []
                for k in ('texture.leather_layer_1', 'texture.leather_layer_2'):
                    name = props.get(k)
                    path = os.path.join(d, name + '.png') if name else None
                    out.append(path if path and os.path.exists(path) else None)
                return out
    return [None, None]


def item_json(item_id, model_id):
    dest = os.path.join(OUT_ITEM_JSON, item_id + '.json')
    with open(dest, 'w', encoding='utf-8') as f:
        json.dump({'parent': model_id}, f)


def class_set(pack, prog, models, names_es, names_en):
    """Las armas (MMOItems) y la armadura (cuero con CustomModelData + capas de OptiFine) de la clase como un set."""
    set_id = f'clase_{pack.id}'
    cls_name = prog['class'].get('name') or pack.id
    items, mm_items = [], {}
    prefix = re.compile(r'^' + re.escape(pack.id.split('_')[0]) + r'_', re.I)

    def add(item_id, model_ref, typ, en, slot=None):
        mid = models.model(model_ref) if model_ref else None
        if not mid:
            pack.problems.append(f'objeto de la clase sin modelo: {item_id}')
            return None
        full = f'{set_id}_{item_id}'
        item_json(full, mid)
        names_en[f'item.tfclient.{full}'] = en
        names_es[f'item.tfclient.{full}'] = name_es(en, cls_name)
        entry = {'id': full, 'type': typ}
        if slot:
            entry['slot'] = slot
        items.append(entry)
        return full

    # Armas: las de MMOItems (sin las «brillantes», que son la misma con otro color)
    for w in prog['weapons']:
        if 'shiny' in str(w['id']).lower() or w.get('cmd') is None:
            continue
        ref = item_override(pack, w['material'], w['cmd'])
        slug_id = slug(prefix.sub('', str(w['id'])))
        typ = 'heavy' if any(k in slug_id for k in ('hammer', 'mace', 'club')) else 'sword'
        add(slug_id, ref, typ, w.get('name') or w['id'])
    # Armadura
    layers = [None, None]
    for a in prog['armor']:
        mat = str(a.get('material') or '').upper()
        if a.get('cmd') is None:
            continue
        ref = item_override(pack, mat, a['cmd'])
        piece = next((v for k, v in ARMOR_SLOTS.items() if k in mat), None)
        if piece:
            l1, l2 = cit_layers(pack, mat, a['cmd'])
            layers = [layers[0] or l1, layers[1] or l2]
            add(f'armor_{piece}', ref, 'armor', a.get('name') or piece.title(), piece)
        elif 'HEAD' in str(a['id']).upper():
            add('head', ref, 'head', a.get('name') or 'Head')
    if any(i['type'] == 'armor' for i in items):
        if not all(layers):
            pack.problems.append('faltan las capas de la armadura puesta (OptiFine CIT)')
        os.makedirs(OUT_ARMOR, exist_ok=True)
        for n, path in enumerate(layers, 1):
            if path:
                shutil.copyfile(path, os.path.join(OUT_ARMOR, f'{set_id}_layer_{n}.png'))
    # Objetos de MythicMobs que la clase lleva en la mano y mira (itemissimilar / equip ...:HAND), p. ej. el
    # guantelete del Dragón Rojo, que cambia según el modo
    hand = set()
    for key, ent in prog['tree'].items():
        if key.startswith('mob_'):
            continue  # lo que lleva en la mano un efecto (no el jugador)
        for c in ent.get('conditions', []) + ent.get('target_conditions', []):
            if c['m'] in ('itemissimilar', 'holding') and c['a'].get('i'):
                hand.add(c['a']['i'])
        for m in ent['mechs']:
            if m['m'] == 'equip' and str(m['a'].get('item', '')).upper().endswith(':HAND') and m.get('t') in (None, 'self', 'caster'):
                hand.add(str(m['a']['item']).split(':')[0])
    for iid in sorted(hand):
        idef = prog['items'].get(iid)
        if not idef:
            continue
        ref = item_override(pack, idef.get('Id') or idef.get('Material') or 'paper', idef.get('Model') or idef.get('CustomModelData') or 0)
        name = strip_mc(idef.get('Display')) or iid.replace('_', ' ')
        full = add(slug(iid), ref, 'sword', name)
        if full:
            mm_items[iid] = full
    # El arma que se entrega: la primera de MMOItems o, si la clase va con un objeto en la mano, el de nombre más
    # corto (el básico: Sacred_Gear y no Sacred_Gear_s2)
    first_weapon = next((i['id'] for i in items if i['type'] in ('sword', 'heavy') and i['id'] not in mm_items.values()),
                        None) or (min(mm_items.values(), key=len) if mm_items else None)
    return {'id': set_id, 'name': cls_name, 'color': '#7fd3ff', 'tier': 'netherite', 'clase': pack.id, 'items': items,
            'give': [i['id'] for i in items if not (i['id'] in mm_items.values() and i['id'] != first_weapon)]}, mm_items


def write_class_items(full):
    """skills/class_sets.json (los sets de las clases) y los nombres de sus objetos en los idiomas del mod."""
    path = os.path.join(RES, 'skills', 'class_sets.json')
    old = json.load(open(path, encoding='utf-8')) if os.path.exists(path) and not full else {'sets': []}
    keep = [x for x in old['sets'] if x['id'] not in {c['id'] for c in CLASS_SETS}]
    with open(path, 'w', encoding='utf-8') as f:
        json.dump({'sets': keep + CLASS_SETS}, f, ensure_ascii=False, indent=1)
    for lang, names in (('es_es', LANG_ES), ('en_us', LANG_EN)):
        lp = os.path.join(RES, 'lang', lang + '.json')
        data = json.load(open(lp, encoding='utf-8'))
        if full:
            data = {k: v for k, v in data.items() if not k.startswith('item.tfclient.clase_')}
        data.update(names)
        with open(lp, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=1)


def strip_mc(s):
    return re.sub(r'[&§][0-9a-fk-orx]', '', re.sub(r'<[^>]+>', '', str(s or ''))).strip()


def main():
    root = sys.argv[1]
    only = set(sys.argv[2:])
    everything = '--todas' in only  # para revisar todos los packs (no para publicar)
    only.discard('--todas')
    if not only and not everything:
        only = set(RELEASED)
        for d in (OUT_CLASSES, OUT_MODELS, OUT_TEX, OUT_ITEM_MODELS, OUT_SOUNDS, OUT_ICONS):
            shutil.rmtree(d, ignore_errors=True)
        for d in (OUT_ITEM_JSON, OUT_ARMOR):
            for f in os.listdir(d) if os.path.isdir(d) else []:
                if f.startswith('clase_'):
                    os.remove(os.path.join(d, f))
    sound_events = {}
    catalog = []
    for name in sorted(os.listdir(root)):
        d = pack_path(root, name)
        if os.path.isdir(d):
            for ev, files in pack_sounds(Pack(d, name)).items():
                if ':' not in ev:
                    GLOBAL_SOUNDS.setdefault(ev, files)
    for name in sorted(os.listdir(root)):
        if only and name not in only:
            continue
        d = pack_path(root, name)
        if not os.path.isdir(d) or name in SKIP_PACKS:
            continue
        pack, out = build_pack(d, sound_events, name)
        catalog.append({'id': pack.id, 'name': out['class'].get('name_es') or out['class']['name'],
                        'skills': [s['id'] for s in out['skills']]})
        print(f"{pack.id:24s} {len(out['skills'])} skills, {len(out['models'])} modelos ME, "
              f"{len(out['item_models'])} modelos de ítem, {len(out['icons'])} iconos, problemas: {len(pack.problems)}")
        for p in pack.problems[:8]:
            print('   -', p)
    # sounds.json: se conservan los eventos que no son de skills
    sj = json.load(open(SOUNDS_JSON, encoding='utf-8')) if os.path.exists(SOUNDS_JSON) else {}
    full = only == RELEASED or everything
    if full:
        sj = {k: v for k, v in sj.items() if not k.startswith('skills.')}
    for ev, d in sound_events.items():
        sj[ev] = {'sounds': d['sounds']}
    with open(SOUNDS_JSON, 'w', encoding='utf-8') as f:
        json.dump(sj, f, ensure_ascii=False, indent=1)
    write_class_items(full)
    if full:
        with open(os.path.join(RES, 'skills', 'catalog.json'), 'w', encoding='utf-8') as f:
            json.dump(catalog, f, ensure_ascii=False, indent=1)


if __name__ == '__main__':
    main()
