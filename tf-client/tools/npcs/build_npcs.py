"""Convierte los packs de NPC de escenas (MythicMobs + ModelEngine) al TF Client, SIN meterlos en el jar.

    python3 -I tools/npcs/build_npcs.py <carpeta con los packs descomprimidos>            # convertir (a npcs/)
    python3 -I tools/npcs/build_npcs.py --al-jar                                          # meterlos en el jar
    python3 -I tools/npcs/build_npcs.py --auditar                                         # auditar los del jar

El dueño (21 oct 2026): «ve convirtiendo todos estos packs al TF Client, son NPCs de escenas para ambientar lugares… los
quiero todos correctamente con sus animaciones correctas, sin errores de textura, animación… no los agregues al jar hasta
que yo te diga».

Cada pack se convierte con el mismo conversor que los jefes y la fauna (tools/bosses/build_bosses.py: el motor del mod
ejecuta MythicMobs y ModelEngine, así que los NPC hacen lo que dice su pack: su modelo, su animación de escena, lo que
hacen al hacerles clic, al ver a un jugador, al andar entre sus puntos…), pero la salida va a **tf-client/npcs/**, una
carpeta con la misma forma que src/main/resources (assets/tfclient/...) que **no entra en el jar**:
  - npcs/assets/tfclient/npcs/<pack>.json          el programa del pack (sus mobs y skills, como un jefe)
  - npcs/assets/tfclient/npcs/catalog.json         los packs y sus NPC (lo que enseña el panel de control)
  - npcs/assets/tfclient/npcs/models/<pack>.<modelo>.json   los .bbmodel (vfx_bb, modo jefe)
  - npcs/assets/tfclient/textures/npcs/, sounds/npcs/, models/npcs/   texturas, sonidos y modelos de ítem
  - npcs/assets/tfclient/sounds.json               SOLO los sonidos de los NPC (al meterlos en el jar se juntan con
                                                   el sounds.json del mod)
Con --al-jar se copia todo eso a src/main/resources (juntando sounds.json): eso es «meterlos en el jar», y solo se
hace cuando el dueño lo diga. Los packs no se suben al repo: solo lo convertido.
"""
import json
import os
import re
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)
sys.path.insert(0, os.path.join(TOOLS, 'bosses'))
import build_bosses as BB  # noqa: E402

BS = BB.BS
STAGE = os.path.normpath(os.path.join(TOOLS, '..', 'npcs'))
STAGE_RES = os.path.join(STAGE, 'assets', 'tfclient')
JAR_RES = BS.RES

# Carpeta del pack (lo que tiene que llevar su nombre, sin mayúsculas) → id en el mod y su nombre (el que trae el pack)
PACKS = [
    ('scenes_tavern', 'npc_tavern', 'Scenes Tavern'),
    ('scenes_market', 'npc_market', 'Scenes Market'),
    ('scenes_fishermen', 'npc_fishermen', 'Scenes Fishermen'),
    ('scenes_combat_vol1', 'npc_combat_vol1', 'Scenes Combat Vol 1'),
    ('scenes_townsfolks_vol1', 'npc_townsfolks_vol1', 'Scenes Townsfolks Vol 1'),
    ('scenes_townsfolks_vol2', 'npc_townsfolks_vol2', 'Scenes Townsfolks Vol 2'),
    ('scenes_townsfolks_vol3', 'npc_townsfolks_vol3', 'Scenes Townsfolks Vol 3'),
    ('scenes_wanderers_vol_1', 'npc_wanderers_vol1', 'Scenes Wanderers Vol 1'),
    ('scenes_guards_vol_1', 'npc_guards_vol1', 'Scenes Guards Vol 1'),
    ('scenes_miners', 'npc_miners', 'Scenes Miners'),
    ('scenes_blacksmith', 'npc_blacksmith', 'Scenes Blacksmith'),
    ('scenes_farmers', 'npc_farmers', 'Scenes Farmers'),
    ('scenes_lumberjacks', 'npc_lumberjacks', 'Scenes Lumberjacks'),
]


# Fallos del propio pack, revisados uno a uno: lo que hace MythicMobs/ModelEngine con ellos (y aquí lo mismo). Clave: el
# texto que sale en el problema (el auditor los pasa a avisos con su porqué: build_bosses.HUECOS hace lo mismo).
HUECOS = {
    'npc_fishermen': {
        'fishing': 'el sedal de «fishing» sube al cielo, y es del pack: en su pose de pesca (la mayor parte de los 164 s) el '
                   'brazo va a -55° y la caña (hueso «tool») a -58° en x, que con las cuentas de Blockbench (euler ZYX, x e y '
                   'restadas; da igual el orden de los ejes) dejan la caña a 113°, más allá de la vertical, y el sedal '
                   '(«bone2», 390° = 30°, escala z 2,25-2,7) sigue hacia arriba hasta unos 10 bloques del suelo. En «boat» la '
                   'caña va a +25° y el sedal cae bien; con +58° en «fishing» colgaría al agua (errata de signo del autor, '
                   'parece). Todas sus animaciones tienen «override», así que no se suman. ModelEngine lo enseñaría igual; '
                   'por orden del dueño (1.3.102) tools/npcs/sedal.py lo cuelga recto hacia abajo en todas las animaciones '
                   'y (1.3.103) pone la caña delante (signo de x cambiado donde queda por detrás) y el anzuelo delante',
    },
    'npc_lumberjacks': {
        'lumberjack_log': 'las escalas de plank1/plank2 de «lumberjack_log» llevan «-» (Molang vacío): Blockbench y '
                          'ModelEngine lo leen como 0 (las tablas se encogen y desaparecen); el conversor hace lo mismo',
        'modelengine:lumberjack_interact': 'el pack no trae ese sonido (solo scenes_saw.ogg): en el servidor no suena; '
                                           'aquí tampoco',
    },
    'npc_townsfolks_vol1': {
        'b_seat': 'la caja del banco donde se sienta el jugador no es cuadrada (24×31,5 px): es solo para hacerle clic; '
                  'se usa tal cual',
    },
    'npc_townsfolks_vol3': {
        'npc_townsfolks_vol3.scene_townsfolk__vol3_beggar': 'errata del pack en el ~onLoad del mendigo (dos «_»): '
            'ModelEngine no encuentra el modelo y no hace nada, pero el mendigo lo conserva porque se puso con save=true '
            '(el mod también guarda el modelo de cada NPC)',
    },
    'npc_guards_vol1': {
        'sawing': 'el dúo de guardias pide «sawing» al segundo modelo, que no la tiene: ModelEngine no hace nada; aquí '
                  'tampoco',
    },
    'npc_market': {
        'npc_market.command': 'marcador del pack sin modelo («command»): la entidad invisible a la que miran los clientes '
                              'del mercado; aquí es un marcador que solo ve el staff con el panel',
    },
    'npc_wanderers_vol1': {
        'npc_wanderers_vol1.command': 'marcadores del pack sin modelo («command»): los generadores y el quitador de '
                                      'caminantes; aquí son marcadores que solo ve el staff con el panel',
    },
}

# Las rutas de los caminantes: el pack trae path1/path_1_follow solo de ejemplo (comentadas, con las coordenadas del
# mundo de su autor: «make sure to add your own paths using the tool on the scenes website»). Aquí las hace el panel de
# control: cada ruta N son dos variables globales guardadas con el mundo (como setvariable{save=true}):
# scenes_route_N = «(x,y,z),(x,y,z),…» y scenes_route_N_start = «x,y,z». Las skills son las del ejemplo del pack, con
# @TargetedTarget en vez de @EIR (el ejemplo cogía al más cercano: podía ser un jugador y lo teletransportaba): el
# caminante al que se le pasa la skill, sea el generador del pack (skill{skill=pathN} @EIR) o el panel (el caminante
# mismo). No @target: en MythicMobs es el objetivo de combate del que lanza, que un caminante no tiene.
ROUTES = 6


def route_skills():
    out = []
    for n in range(1, ROUTES + 1):
        out.append(f"""path{n}:
  TargetConditions:
  - stance stand
  Skills:
  - setStance{{stance=path{n}}} @TargetedTarget
  - setvariable{{var=global.scenes_path<target.uuid>;value={n};type=STRING;save=true}} @TargetedTarget
  - teleportto{{location=<global.var.scenes_route_{n}_start>;delay=10}} @TargetedTarget
path_{n}_follow:
  Skills:
  - followPath{{speed=1;tolerance=4;path=<global.var.scenes_route_{n}>}} @self
""")
    return ''.join(out)

def stage_paths():
    """Toda la salida del conversor de jefes, a npcs/ (y su carpeta, «npcs» en vez de «bosses»)."""
    BB.KIND = 'npcs'
    BS.RES = STAGE_RES
    BS.OUT_FONTS = os.path.join(STAGE_RES, 'font')
    BB.OUT = os.path.join(STAGE_RES, 'npcs')
    BB.OUT_MODELS = os.path.join(BB.OUT, 'models')
    BB.OUT_TEX = os.path.join(STAGE_RES, 'textures', 'npcs')
    BB.OUT_ITEM_MODELS = os.path.join(STAGE_RES, 'models', 'npcs')
    BB.OUT_SOUNDS = os.path.join(STAGE_RES, 'sounds', 'npcs')
    BB.OUT_ITEM_JSON = os.path.join(STAGE_RES, 'models', 'item')


def find_pack(root, key):
    for name in sorted(os.listdir(root)):
        low = re.sub(r'[^a-z0-9]+', '_', name.lower())
        if key in low and os.path.isdir(os.path.join(root, name)):
            return name
    return None


def uses_routes(pack_dir):
    """¿Llaman sus mobs a las rutas path_N_follow (los caminantes)?"""
    _sk, mob_files, _it = BB.files(pack_dir)
    for p in mob_files:
        with open(p, encoding='utf-8', errors='replace') as f:
            if re.search(r'path_\d+_follow', f.read()):
                return True
    return False


def pack_mobs(pack_dir):
    """Los mobs que trae el pack (en el orden de sus archivos)."""
    _sk, mob_files, _it = BB.files(pack_dir)
    return list(BB.load_all(mob_files, 'mob'))


# Los menús de DeluxeMenus del pack (lo que el dueño del servidor abría con la herramienta del pack) → grupos del panel
MENU_GROUPS = [
    ('scene_skins', 'skin', 'Piel'),
    ('scene_hats', 'hat', 'Sombrero'),
    ('scene_backpacks', 'back', 'Espalda'),
    ('scene_leftarm', 'left', 'Mano izquierda'),
    ('scene_rightarm', 'right', 'Mano derecha'),
    ('scene_armors', 'armor', 'Armadura'),
]
CAST = re.compile(r'mm\s+test\s+cast\s+([\w\-]+)', re.I)
SPAWN = re.compile(r'mm\s+mobs?\s+spawn\s+([\w\-]+)', re.I)
OWN = '<global.var.skinchange>'  # en los menús: «el modelo del NPC que se está editando»


def menu_files(pack_dir):
    out = []
    for d, _dirs, fs in sorted(os.walk(pack_dir)):
        low = d.replace('\\', '/').lower()
        if 'deluxemenus' not in low or 'itemsadder' in low or '__macosx' in low:
            continue
        out += [os.path.join(d, f) for f in sorted(fs) if f.endswith('.yml') and not f.startswith('._')]
    return out


def menu_items(path):
    """(nombre del ítem, nombre para mostrar, comandos al hacer clic) de un menú."""
    data = BB.C.load(path)
    if not isinstance(data, dict) or '__error__' in data:
        return []
    out = []
    for key, it in (data.get('items') or {}).items():
        if not isinstance(it, dict):
            continue
        cmds = [str(c) for k in ('left_click_commands', 'click_commands', 'right_click_commands')
                for c in (it.get(k) or [])]
        out.append((str(key), BS.strip_mc(it.get('display_name') or key), cmds))
    return out


def all_npc_names(root):
    """Los nombres de los NPC en los menús «scenes_npcs» de TODOS los packs (el menú es común: cada pack trae el de
    todos los que había hasta entonces) → {mob: (nombre, orden)}."""
    names = {}
    for name in sorted(os.listdir(root)):
        d = os.path.join(root, name)
        if not os.path.isdir(d):
            continue
        for p in menu_files(d):
            if not os.path.basename(p).startswith('scenes_npcs'):
                continue
            for _key, shown, cmds in menu_items(p):
                for c in cmds:
                    m = SPAWN.search(c)
                    if m and m.group(1) not in names:
                        names[m.group(1)] = (shown, len(names))
    return names


def option_ops(skill):
    """Lo que hace una opción del menú, de su skill: [('part', hueso, modelo, hueso nuevo) | ('remap', modelo)] y las
    variables globales que guarda (para que su ~onLoad las vuelva a poner) → (ops, vars). «» = el modelo del NPC."""
    ops, vs = [], {}
    for line in (skill or {}).get('Skills') or []:
        pm = BB.C.parse_line(line)
        if not pm:
            continue
        a = pm['a']
        if pm['m'] == 'changepart':
            pid = a.get('pid') or a.get('partid') or a.get('p')
            nm = a.get('nm') or a.get('newmodelid') or a.get('nmid') or a.get('newmodel')
            npid = a.get('npid') or a.get('newpartid') or pid
            if pid and nm:
                ops.append(['part', pid, '' if nm == OWN else nm.lower(), npid])
        elif pm['m'] == 'remapmodel':
            n = a.get('n') or a.get('newmodelid') or a.get('nm')
            if n:
                ops.append(['remap', '' if n == OWN else n.lower()])
        elif pm['m'] in ('setvariable', 'setvar'):
            var = a.get('var') or a.get('variable') or ''
            mv = re.fullmatch(r'global\.(\w+)<target\.uuid>', var)
            if mv:
                v = a.get('value') or a.get('val') or ''
                vs[mv.group(1).lower()] = '' if v == OWN else v.lower()
    return ops, vs


def shared_menus(root):
    """Los menús de los packs de la serie (son comunes: cada pack trae los de su época; las tabernas y el mercado, ninguno)
    y las skills de sus opciones → ([(menú, nombre, comandos)], {skill: entrada})."""
    menus, skills, seen = [], {}, set()
    for name in sorted(os.listdir(root)):
        d = os.path.join(root, name)
        if not os.path.isdir(d):
            continue
        for p in menu_files(d):
            base = os.path.basename(p)[:-4]
            for _key, shown, cmds in menu_items(p):
                k = (base, shown, tuple(cmds))
                if k not in seen:
                    seen.add(k)
                    menus.append((base, shown, cmds))
        sk_files, _mb, _it = BB.files(d)
        for k, v in BB.load_all(sk_files, 'skill').items():
            skills.setdefault(k, v)
    return menus, skills


def pack_options(menus, shared_skills, skills, models):
    """Las opciones de los menús que se pueden poner de verdad en este pack: las que solo usan modelos que trae (las
    pieles «skin_N» de los menús son plantillas para el dueño del servidor y no vienen: las pieles van aparte,
    add_skins). La skill, la del pack o, si no la trae, la de la serie."""
    groups, notes = {}, []
    seen = set()
    for base, shown, cmds in menus:
        g = next((gid for pre, gid, _l in MENU_GROUPS if base.startswith(pre)), None)
        for c in cmds:
            m = CAST.search(c)
            if not m:
                continue
            sk = m.group(1)
            gid = 'reset' if sk == 'reset_cosmetics' else g
            if not gid or gid == 'skin' or (gid, sk) in seen:
                continue
            seen.add((gid, sk))
            ops, vs = option_ops(skills.get(sk) or shared_skills.get(sk))
            if not ops:
                continue
            need = {o[2] for o in ops if o[0] == 'part' and o[2]} | {o[1] for o in ops if o[0] == 'remap' and o[1]}
            missing = sorted(n for n in need if n not in models)
            if missing:
                notes.append(f'opción «{shown}» ({sk}) sin modelo en el pack: {", ".join(missing)}')
                continue
            groups.setdefault(gid, []).append({'id': sk, 'name': shown, 'ops': ops, 'vars': vs})
    return groups, notes


def npc_name(mob, names):
    """El nombre del menú del pack («Blacksmith 1») o uno legible: scene_tavern_talk1 → Tavern talk 1."""
    if mob in names:
        return names[mob][0]
    s = re.sub(r'^(scene_|scenes_|npc_)', '', mob)
    s = re.sub(r'(\D)(\d+)$', r'\1 \2', s.replace('_', ' ')).strip()
    return s[:1].upper() + s[1:]


def model_bones(mid):
    p = os.path.join(BB.OUT_MODELS, mid + '.json')
    if not os.path.exists(p):
        return set()
    return {b.get('id') for b in json.load(open(p, encoding='utf-8')).get('bones', [])}


def summoned(out):
    """Los mobs que invoca otro mob del pack (el caballo del jinete, la silla del columpio…): no se ponen solos."""
    found = set()

    def scan(mechs):
        for pm in mechs or []:
            if pm.get('m') in ('summon', 'mount', 'summonpassenger', 'summonmount'):
                for k in ('type', 't', 'mob', 'm', 'mobtype'):
                    v = (pm.get('a') or {}).get(k)
                    if v:
                        found.add(str(v))
    for m in out['mobs'].values():
        scan(m.get('mechs'))
    for t in out['tree'].values():
        scan(t.get('mechs') if isinstance(t, dict) else t)
    return found


def pins_of(mechs, tree):
    """Los «pins» de MythicMobs (sitios con nombre, /mm pin) por los que anda: @pin{name} y ?distanceFromPin{pin}."""
    out = []

    def add(n):
        if n and n not in out:
            out.append(n)

    def scan(lines, depth=0):
        for pm in lines or []:
            if pm.get('t') == 'pin':
                add((pm.get('ta') or {}).get('name') or (pm.get('ta') or {}).get('n'))
            for c in pm.get('c') or []:
                if c.get('m') == 'distancefrompin':
                    add((c.get('a') or {}).get('pin') or (c.get('a') or {}).get('p'))
            if pm.get('m') in ('skill', 's', 'metaskill') and depth < 4:
                k = (pm.get('a') or {}).get('s') or (pm.get('a') or {}).get('skill')
                if k and str(k).lower() in tree:
                    scan(tree[str(k).lower()].get('mechs'), depth + 1)
    scan(mechs)
    return out


def routes_of(mechs, tree):
    """Las rutas de caminante que usa (path_N_follow) o que da a los que genera (spawner_wanderers_N → pathN)."""
    txt = json.dumps(mechs)
    found = {int(n) for n in re.findall(r'path_(\d+)_follow', txt)}
    for k in re.findall(r'"s(?:kill)?": "(spawner_wanderers_\d+)"', txt):
        found |= {int(n) for n in re.findall(r'"path(\d+)"', json.dumps(tree.get(k.lower(), {})))}
    return sorted(found)


def catalog_entry(bid, title, out, names, options):
    """Lo que enseña el panel de un pack: cada NPC con su modelo, su animación de escena, sus pins y rutas y qué se le
    puede cambiar. Los mobs sin modelo («command») son marcadores del pack (generadores, puestos): salen como tales."""
    npcs = []
    inner = summoned(out)
    tree = out['tree']
    for mob, m in out['mobs'].items():
        model = m.get('model')
        marker = not model or model.split('.', 1)[-1] == 'command'
        if mob in inner and mob not in names:
            continue  # lo saca otro mob del pack (la silla del columpio)
        if marker and not any(pm['m'] in ('summon', 'skill', 'remove') for pm in m['mechs']) and 'stand' not in mob:
            continue
        # en el mod es una entidad (se coloca, se mueve, se guarda con el mundo), aunque el pack la haga invencible
        m['kind'] = 'entity'
        state, walks = None, False
        for pm in m['mechs']:
            a = pm.get('a', {})
            if pm['m'] == 'defaultstate' and not state and (a.get('t') or a.get('type') or 'idle') == 'idle':
                state = a.get('s') or a.get('state')
            if pm['m'] in ('goto', 'followpath', 'pathto') or (pm['m'] in ('skill', 's') and 'follow' in json.dumps(a)):
                walks = True
        opts = {}
        if not marker:
            bones = model_bones(model)
            for gid, lst in options.items():
                ok = [o['id'] for o in lst if any(op[0] == 'remap' or op[1] in bones for op in o['ops'])]
                if ok:
                    opts[gid] = ok
        hostile = bool(m.get('ai_targets')) and not (m.get('options') or {}).get('Invincible')
        e = {'mob': mob, 'name': npc_name(mob, names), 'model': None if marker else model, 'state': state,
             'walks': walks, 'hostile': hostile, 'options': opts}
        if marker:
            e['marker'] = True
        pins = pins_of(m['mechs'], tree)
        if pins:
            e['pins'] = pins
        routes = routes_of(m['mechs'], tree)
        if routes:
            e['routes'] = routes
        npcs.append(e)
    npcs.sort(key=lambda n: (bool(n.get('marker')), names.get(n['mob'], ('', 10 ** 6))[1], n['mob']))
    return {'id': bid, 'name': title, 'npcs': npcs, 'options': options}


# Las pieles: casi todos los NPC de Scenes llevan la piel de plantilla del pack (amarilla, con una «S»): el autor espera
# que el dueño del servidor haga las suyas con su web («skin_1»…, que no vienen). Los packs sí traen 21 pieles
# medievales de verdad (las de los caminantes, que se las ponen solos): son las pieles del panel para todos. Encajan en
# cualquier NPC (los mismos huesos head/body/right_arm…, con la geometría relativa a su hueso); en los dúos, el segundo
# personaje (head2…) con changepart. A los que llevan la plantilla y no se ponen piel solos, el panel les pone una al
# colocarlos (autoskin).
SKIN_PACK = 'npc_wanderers_vol1'
# Las que les pone el panel al colocarlos (mirando las 21: las de yelmo y armadura, para los guardias; las demás, de
# aldeano, para el resto). El panel deja elegir cualquiera.
ARMORED = {1, 2, 3, 5, 9, 14}
ARMORED_PACKS = {'npc_guards_vol1'}
SKIN_RE = re.compile(r'^wanderer_(\d+)$')
LIMBS = ['head', 'body', 'right_arm', 'left_arm', 'right_leg', 'left_leg']


def skin_pool():
    """Las pieles de verdad (pack.modelo), en orden."""
    d = os.path.join(BB.OUT, 'models')
    found = []
    for f in os.listdir(d) if os.path.isdir(d) else []:
        if not f.startswith(SKIN_PACK + '.'):
            continue
        m = SKIN_RE.match(f[len(SKIN_PACK) + 1:-5])
        if m:
            found.append((int(m.group(1)), f[:-5]))
    return [mid for _n, mid in sorted(found)]


def template_texture():
    """La textura de la plantilla «S» (la del modelo «skin» del pack de las pieles)."""
    from PIL import Image
    import numpy as np
    p = os.path.join(BB.OUT, 'models', SKIN_PACK + '.skin.json')
    if not os.path.exists(p):
        return None
    t = json.load(open(p, encoding='utf-8'))['tex'][0]['path'].split('/')[-1]
    return np.asarray(Image.open(os.path.join(BB.OUT_TEX, SKIN_PACK, 'me', t)).convert('RGBA'), dtype=float)


def uses_template(pack_id, mid, tpl):
    from PIL import Image
    import numpy as np
    if tpl is None:
        return False
    m = json.load(open(os.path.join(BB.OUT, 'models', mid + '.json'), encoding='utf-8'))
    for t in m['tex']:
        im = np.asarray(Image.open(os.path.join(BB.OUT_TEX, pack_id, 'me', t['path'].split('/')[-1])).convert('RGBA'), dtype=float)
        if im.shape == tpl.shape and np.abs(im - tpl).mean() < 5:
            return True
    return False


def self_skinned(mob, prog):
    """¿Se pone una piel él solo (remapmodel a un modelo fijo al aparecer: los caminantes, el minero no muerto)?"""
    seen = set()

    def scan(mechs, depth=0):
        for pm in mechs or []:
            a = pm.get('a') or {}
            if pm['m'] == 'remapmodel' and '<' not in str(a.get('n', '<')):
                return True
            names = []
            if pm['m'] in ('skill', 's', 'metaskill'):
                names = [a.get('s') or a.get('skill')]
            elif pm['m'] == 'randomskill':
                names = str(a.get('skills') or a.get('s') or '').split(',')
            for k in names:
                k = str(k or '').strip().lower()
                if k and k not in seen and k in prog['tree'] and depth < 4:
                    seen.add(k)
                    if scan(prog['tree'][k]['mechs'], depth + 1):
                        return True
        return False
    return scan([pm for pm in prog['mobs'][mob]['mechs'] if pm.get('tr') in ('onspawn', 'onload', 'onspawnorload')])


SECOND = '_2'  # las pieles del segundo personaje: <piel>_2 (second_skin)


def write_second_skins(pool, models_dir):
    """Las pieles del segundo personaje de los dúos (pegado.second_skin), junto a las del primero."""
    sys.path.insert(0, HERE)
    import pegado
    for mid in pool:
        skin = json.load(open(os.path.join(models_dir, mid + '.json'), encoding='utf-8'))
        with open(os.path.join(models_dir, mid + SECOND + '.json'), 'w', encoding='utf-8') as f:
            json.dump(pegado.second_skin(skin), f, ensure_ascii=False, separators=(',', ':'))


def add_skins(catalog):
    """Pone las pieles en el catálogo (grupos skin y skin2) y la marca autoskin."""
    pool = skin_pool()
    tpl = template_texture()
    if not pool:
        return
    write_second_skins(pool, os.path.join(BB.OUT, 'models'))
    for c in catalog.values():
        prog = json.load(open(os.path.join(BB.OUT, c['id'] + '.json'), encoding='utf-8'))
        skins = [{'id': f'piel_{i + 1}', 'name': f'Piel {i + 1}', 'ops': [['remap', mid]], 'vars': {'skin': mid}}
                 for i, mid in enumerate(pool)]
        skins.append({'id': 'piel_pack', 'name': 'Plantilla del pack', 'ops': [['remap', '']], 'vars': {'skin': ''}})
        # (1.3.103) el segundo personaje, con las piezas de second_skin (la piel reflejada: mira al primero, como en las
        # plantillas del pack; con las de la piel tal cual le quedaban la cara y el pecho en la espalda)
        skins2 = [{'id': f'piel2_{i + 1}', 'name': f'Piel {i + 1}', 'ops': [['part', b + '2', mid + SECOND, b + '2'] for b in LIMBS],
                   'vars': {}} for i, mid in enumerate(pool)]
        skins2.append({'id': 'piel2_pack', 'name': 'Plantilla del pack', 'ops': [['part', b + '2', '', b + '2'] for b in LIMBS], 'vars': {}})
        used1 = used2 = False
        for n in c['npcs']:
            if not n.get('model'):
                continue
            bones = model_bones(n['model'])
            tpl_skin = uses_template(c['id'], n['model'], tpl)
            if 'head' in bones:
                n['options'] = {'skin': [o['id'] for o in skins], **n['options']}
                used1 = True
            if 'head2' in bones:
                n['options'] = {'skin2': [o['id'] for o in skins2], **n['options']}
                used2 = True
            if tpl_skin and not self_skinned(n['mob'], prog):
                armored = c['id'] in ARMORED_PACKS
                n['autoskin'] = [f'piel_{i + 1}' for i in range(len(pool)) if ((i + 1) in ARMORED) == armored]
        if used1:
            c['options']['skin'] = skins
        if used2:
            c['options']['skin2'] = skins2


def convert(root, only=()):
    stage_paths()
    if not only:
        shutil.rmtree(STAGE_RES, ignore_errors=True)
    os.makedirs(BB.OUT, exist_ok=True)
    sj_path = os.path.join(STAGE_RES, 'sounds.json')
    sj = json.load(open(sj_path, encoding='utf-8')) if os.path.exists(sj_path) else {}
    cat_path = os.path.join(BB.OUT, 'catalog.json')
    catalog = {c['id']: c for c in (json.load(open(cat_path, encoding='utf-8')) if os.path.exists(cat_path) else [])}
    total_problems = 0
    names = all_npc_names(root)
    menus, shared_skills = shared_menus(root)
    for key, bid, title in PACKS:
        if only and bid not in only and key not in only:
            continue
        name = find_pack(root, key)
        if not name:
            print(f'falta el pack {key} en {root}')
            continue
        d = os.path.join(root, name)
        BB.unpack_nested(d)
        mobs = pack_mobs(d)
        if not mobs:
            print(f'{name}: sin mobs')
            continue
        BB.clean(bid, sj)
        BB.ES[bid] = {'name': title, 'drops': {}}
        # «raices»: todos los mobs del pack se compilan (cada NPC es un mob raíz; ninguno sale solo en un bioma)
        conf = {'id': bid, 'mob': mobs[0], 'vida': 5, 'dano': 0, 'raices': mobs}
        sound_events = {}
        if uses_routes(d):
            BB.FIXES[mobs[0]] = route_skills()
        pack, out = BB.build(d, name, conf, sound_events)
        gaps = HUECOS.get(bid, {})
        if gaps:
            out['gaps'] = dict(gaps)
            keep = []
            for p in pack.problems:
                hit = next((g for g in gaps if g in p), None)
                if hit:
                    pack.notes.append(f'hueco del pack: {p} ({gaps[hit]})')
                else:
                    keep.append(p)
            pack.problems[:] = keep
        out['boss']['npc'] = True
        sk_files, _mb, _it = BB.files(d)
        skills = BB.load_all(sk_files, 'skill')
        options, opt_notes = pack_options(menus, shared_skills, skills, {k.split('.', 1)[-1] for k in out['models']})
        pack.notes.extend(opt_notes)
        catalog[bid] = catalog_entry(bid, title, out, names, options)
        with open(os.path.join(BB.OUT, bid + '.json'), 'w', encoding='utf-8') as f:
            json.dump(out, f, ensure_ascii=False, separators=(',', ':'))
        sj.update(sound_events)
        total_problems += len(pack.problems)
        print(f"{bid:22s} {len(out['tree'])} skills, {len(out['mobs'])} mobs, {len(out['models'])} modelos, "
              f"{len(catalog[bid]['npcs'])} NPC, problemas: {len(pack.problems)}")
        for p in pack.problems[:int(os.environ.get('MAXP', 30))]:
            print('   -', p)
        for n in getattr(pack, 'notes', []):
            print('   ·', n)
    with open(sj_path, 'w', encoding='utf-8') as f:
        json.dump(sj, f, ensure_ascii=False, indent=1)
    add_skins(catalog)
    order = [b for _k, b, _t in PACKS]
    with open(cat_path, 'w', encoding='utf-8') as f:
        json.dump(sorted(catalog.values(), key=lambda c: order.index(c['id']) if c['id'] in order else 99), f,
                  ensure_ascii=False, indent=1)
    # (1.3.101, orden del dueño) la cabeza, los brazos y las piernas pegados al cuerpo en todas las animaciones (las del
    # pack las despegan: tools/npcs/pegar.py)
    sys.path.insert(0, HERE)
    # (1.3.102, orden del dueño) el sedal de los pescadores cuelga por su peso (en el pack sube al cielo: sedal.py)
    import sedal
    sedal.main(STAGE_RES)
    import pegar
    pegar.main(STAGE_RES, procs=int(os.environ.get('PROCESOS', '4')))
    # (1.3.106, orden del dueño) los muebles: los de Nexo de la taberna en el panel y, a los que se sientan o se tumban en
    # algo que no está, el suyo (muebles.py)
    import muebles
    muebles.main(root, STAGE_RES)
    return [c['id'] for c in json.load(open(cat_path, encoding='utf-8'))]


def audit(ids, res=None):
    """El auditor de los jefes (tools/bosses/audit.py), sobre los NPC convertidos (npcs/ o, con res, los del jar).
    Devuelve cuántos problemas hay."""
    import importlib.util
    res = res or STAGE_RES
    spec = importlib.util.spec_from_file_location('npc_audit', os.path.join(TOOLS, 'bosses', 'audit.py'))
    au = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(au)
    au.RES = res
    au.BOSSES = os.path.join(res, 'npcs')
    return au.main(ids)


def to_jar():
    """Mete los NPC convertidos en el jar (src/main/resources): solo cuando el dueño lo ordene."""
    src = STAGE_RES
    for d, _dirs, fs in os.walk(src):
        for f in fs:
            p = os.path.join(d, f)
            rel = os.path.relpath(p, src)
            if rel == 'sounds.json':
                continue
            dest = os.path.join(JAR_RES, rel)
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            shutil.copyfile(p, dest)
    sj_main = os.path.join(JAR_RES, 'sounds.json')
    main = json.load(open(sj_main, encoding='utf-8'))
    main = {k: v for k, v in main.items() if not k.startswith('npcs.')}
    main.update(json.load(open(os.path.join(src, 'sounds.json'), encoding='utf-8')))
    with open(sj_main, 'w', encoding='utf-8') as f:
        json.dump(main, f, ensure_ascii=False, indent=1)
    print('NPC copiados a src/main/resources (y sus sonidos en sounds.json)')


def main():
    if sys.argv[1:2] == ['--al-jar']:
        to_jar()
        return
    if sys.argv[1:2] == ['--auditar']:
        # Los del jar (lo que llama tools/bosses/verificar.py): 0 problemas o sale con error
        cat = os.path.join(JAR_RES, 'npcs', 'catalog.json')
        ids = [c['id'] for c in json.load(open(cat, encoding='utf-8'))] if os.path.exists(cat) else []
        total = audit(ids, JAR_RES) if ids else 0
        print(f'NPC de escenas: {len(ids)} packs, {total} problemas')
        sys.exit(1 if total else 0)
    root = sys.argv[1]
    only = set(sys.argv[2:])
    ids = convert(root, only)
    audit(ids)


if __name__ == '__main__':
    main()
