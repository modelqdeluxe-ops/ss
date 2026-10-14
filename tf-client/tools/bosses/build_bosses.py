"""Genera los jefes (bosses) del TF Client a partir de los packs comprados (MythicMobs + ModelEngine).

    python3 tools/bosses/build_bosses.py <carpeta con los packs descomprimidos> [pack ...]

Cada pack es un jefe (ver JEFES abajo: carpeta del pack → id del jefe y su mob de MythicMobs). Sale:
  - assets/tfclient/bosses/<jefe>.json      el programa del jefe: el árbol de skills de MythicMobs que alcanza su mob
                                            (y lo que invoca), sus mobs con sus opciones, textos y valores base
  - assets/tfclient/bosses/catalog.json     el índice (orden de la ventana /tf boss)
  - assets/tfclient/bosses/models/<jefe>.<modelo>.json   los .bbmodel de ModelEngine (vfx_bb.py, modo jefe: prioridad
                                            de las animaciones, scripts «mm:» de la línea de tiempo, cajas de golpe ob_,
                                            asientos p_ y el tamaño del hueso «hitbox»)
  - textures/bosses/<jefe>/...              texturas de esos modelos, de los modelos de ítem y de los glifos
  - models/bosses/<jefe>/...                modelos de ítem (balas «display» de los misiles, objetos que sueltan)
  - sounds/bosses/<jefe>/*.ogg              sonidos del pack, eventos tfclient:bosses.<jefe>.<x> en sounds.json
  - font/bosses_<jefe>.json                 los glifos de sus text_display (el lanzallamas de la Hidra...)
Los packs no se suben al repo: solo lo convertido (como con las skills y los VFX). Las texturas de ítem entran en el
atlas de bloques con el de las skills (build_skills.write_atlas).
"""
import json
import os
import re
import shutil
import sys

import yaml

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)
sys.path.insert(0, os.path.join(TOOLS, 'skills'))
sys.path.insert(0, TOOLS)
import build_skills as BS  # noqa: E402
import compile as C  # noqa: E402
import vfx_bb as B  # noqa: E402
import vfx_compile as VC  # noqa: E402

RES = BS.RES
OUT = os.path.join(RES, 'bosses')
OUT_MODELS = os.path.join(OUT, 'models')
OUT_TEX = os.path.join(RES, 'textures', 'bosses')
OUT_ITEM_MODELS = os.path.join(RES, 'models', 'bosses')
OUT_SOUNDS = os.path.join(RES, 'sounds', 'bosses')
# Modelos de los objetos que sueltan: models/item/jefe_<jefe>_<objeto>.json (el id de TFBosses.itemId)
OUT_ITEM_JSON = os.path.join(RES, 'models', 'item')
SOUNDS_JSON = BS.SOUNDS_JSON

# Carpeta del pack → jefe. «mob»: el mob de MythicMobs que es el jefe.
# «vida» y «dano»: vida y daño base del jefe en dificultad Normal para un jugador (los de los packs son de
# ejemplo: la Abominación traía 50 de vida y todos «Damage: 2»); la ventana /tf boss los multiplica por la dificultad.
JEFES = {
    'LITTLEROOM_GARGOYLE_v1_0': {'id': 'gargola', 'mob': 'LRD_GARGOYLE', 'vida': 420, 'dano': 7},
    'LITTLEROOM_HYDRA_v1_0': {'id': 'hidra', 'mob': 'LR_HYDRA', 'vida': 1500, 'dano': 9},
    'LITTLEROOM_PARASITIC_ABOMINATION_v1_0': {'id': 'abominacion', 'mob': 'PARASITIC_ABOMINATION', 'vida': 650, 'dano': 8},
    'LITTLEROOM_TowerSkeleton_v1_0': {'id': 'esqueleto_torre', 'mob': 'TOWER_SKELETON', 'vida': 1200, 'dano': 9},
    'Astwood': {'id': 'astwood', 'mob': 'astwood', 'vida': 800, 'dano': 6},
}
ES = json.load(open(os.path.join(HERE, 'es.json'), encoding='utf-8'))

# Skills que el pack llama sin definirlas (fallos del pack): en MythicMobs esas líneas no hacen nada, pero así el
# ataque se queda sin daño. Se completan con las mismas mecánicas que usa el pack en sus ataques parecidos.
FIXES = {
    # El pisotón de la Hidra llama a «LR_HYDRA_stomp_damage» y el pack no la trae: como su coletazo, más corto
    'LR_HYDRA': """
LR_HYDRA_stomp_damage:
  Skills:
  - basedamage{m=1}
  - throw{v=6;vy=5}
""",
}

# Tipos de MythicMobs que en el juego son un mob de verdad (se les pega, tienen IA); los demás (soportes, marcadores,
# displays) son efectos que dibuja el cliente. Uno de estos con «Invincible: true» también es solo un efecto (las
# púas y rocas de Astwood: vindicadores invencibles sin IA).
LIVING = {'IRON_GOLEM', 'HUSK', 'ZOMBIE', 'VINDICATOR', 'SKELETON', 'WITHER_SKELETON', 'SPIDER', 'RAVAGER', 'PIGLIN_BRUTE',
          'WOLF', 'POLAR_BEAR', 'GIANT', 'ZOMBIE_VILLAGER', 'PILLAGER', 'EVOKER', 'BLAZE', 'SLIME', 'MAGMA_CUBE'}
# Velocidad por defecto de cada tipo en Minecraft (setspeed{s=X} de MythicMobs = X por esta)
SPEED = {'IRON_GOLEM': 0.25, 'HUSK': 0.23, 'ZOMBIE': 0.23, 'VINDICATOR': 0.35, 'SKELETON': 0.25,
         'WITHER_SKELETON': 0.25, 'SPIDER': 0.3, 'RAVAGER': 0.3, 'PIGLIN_BRUTE': 0.35, 'WOLF': 0.3,
         'POLAR_BEAR': 0.25, 'GIANT': 0.5, 'ZOMBIE_VILLAGER': 0.23, 'PILLAGER': 0.35, 'EVOKER': 0.5, 'BLAZE': 0.23}


def files(pack_dir):
    """Los .yml de MythicMobs del pack: (skills, mobs, ítems)."""
    sk, mb, it = [], [], []
    for d, _, fs in os.walk(pack_dir):
        low = d.replace('\\', '/').lower()
        if 'mythicmobs' not in low:
            continue
        for f in sorted(fs):
            if not f.lower().endswith(('.yml', '.yaml')) or f.lower() == 'packinfo.yml':
                continue
            p = os.path.join(d, f)
            if re.search(r'/skills?(/|$)', low):
                sk.append(p)
            elif re.search(r'/mobs?(/|$)', low):
                mb.append(p)
            elif re.search(r'/items?(/|$)', low):
                it.append(p)
    return sk, mb, it


def load_all(paths):
    out = {}
    for p in paths:
        data = C.load(p)
        if '__error__' in data:
            raise SystemExit(f'{p}: {data["__error__"]}')
        for k, v in data.items():
            if isinstance(v, dict):
                out.setdefault(k, v)
    return out


def compile_boss(pack_dir, root_mob, extra_skills):
    """El árbol de MythicMobs que alcanza el jefe: sus mecánicas, las skills que llaman, los mobs que invoca (con las
    suyas) y los ítems que suelta. extra_skills: las que lanzan los scripts de los modelos y los objetos."""
    sk_files, mob_files, item_files = files(pack_dir)
    tree, mobs, items = load_all(sk_files), load_all(mob_files), load_all(item_files)
    for k, v in (yaml.safe_load(FIXES.get(root_mob, '')) or {}).items():
        tree.setdefault(k, v)
    lower_tree = {k.lower(): k for k in tree}
    lower_mobs = {k.lower(): k for k in mobs}
    missing = set()
    reach_s, reach_m = set(), set()
    stack, mob_stack = list(extra_skills), [root_mob]
    compiled_tree, compiled_mobs = {}, {}
    used_items = set()

    def follow(mechs):
        for pm in mechs:
            s2, m2 = C.refs(pm)
            # «summon{os=true}» es «onSurface», no la skill «onStart» de un proyectil
            stack.extend(x for x in s2 if x.lower() not in ('true', 'false'))
            mob_stack.extend(m2)
            if pm['m'] == 'dropitem':
                used_items.add(str(pm['a'].get('i') or pm['a'].get('item') or '').split(':')[0])
            if pm['m'] in ('projectile', 'missile') and str(pm['a'].get('bullettype', '')).upper() == 'MOB':
                if pm['a'].get('mob'):
                    mob_stack.append(pm['a']['mob'])

    while stack or mob_stack:
        while stack:
            name = stack.pop()
            key = lower_tree.get(str(name).lower())
            if not key:
                missing.add(f'skill:{name}')
                continue
            if key in reach_s:
                continue
            reach_s.add(key)
            if key not in tree:  # lista en línea ya compilada
                continue
            v = tree[key]
            mechs = [pm for pm in (C.parse_line(line) for line in (v.get('Skills') or [])) if pm]
            inline = {}
            C.extract_inline(mechs, inline, key)
            for iname, ient in inline.items():
                compiled_tree[iname] = ient
                lower_tree[iname.lower()] = iname
                follow(ient['mechs'])
            compiled_tree[key] = {
                'cooldown': v.get('Cooldown'),
                'conditions': [c for c in (C.parse_condition(x) for x in (v.get('Conditions') or [])) if c],
                'target_conditions': [c for c in (C.parse_condition(x) for x in (v.get('TargetConditions') or [])) if c],
                'trigger_conditions': [c for c in (C.parse_condition(x) for x in (v.get('TriggerConditions') or [])) if c],
                'mechs': mechs}
            follow(mechs)
            for cl in ('conditions', 'target_conditions', 'trigger_conditions'):
                for c in compiled_tree[key][cl]:
                    cm = re.match(r'(?:castinstead|orelsecast|cast)\s+(\S+)', c.get('v', ''))
                    if cm:
                        stack.append(cm.group(1))
        while mob_stack:
            name = mob_stack.pop()
            key = lower_mobs.get(str(name).lower())
            if not key:
                missing.add(f'mob:{name}')
                continue
            if key in reach_m:
                continue
            reach_m.add(key)
            v = mobs[key]
            mechs = [pm for pm in (C.parse_line(line) for line in (v.get('Skills') or [])) if pm]
            inline = {}
            C.extract_inline(mechs, inline, 'mob_' + key)
            for iname, ient in inline.items():
                compiled_tree[iname] = ient
                lower_tree[iname.lower()] = iname
                follow(ient['mechs'])
            mob = {'type': str(v.get('Type', 'ZOMBIE')).upper(), 'options': v.get('Options') or {},
                   'display': v.get('Display'), 'health': v.get('Health'), 'mechs': mechs}
            for yk, ok in (('AIGoalSelectors', 'ai_goals'), ('AITargetSelectors', 'ai_targets'), ('Damage', 'damage'),
                           ('Armor', 'armor'), ('Faction', 'faction'), ('DamageModifiers', 'damage_modifiers'),
                           ('BossBar', 'bossbar'), ('Modules', 'modules'), ('DisplayOptions', 'display_options'),
                           ('Drops', 'drops'), ('Equipment', 'equipment')):
                if v.get(yk) is not None:
                    mob[ok] = v[yk]
            compiled_mobs[key] = mob
            follow(mechs)
    return {'tree': compiled_tree, 'mobs': compiled_mobs, 'items': {k: items[k] for k in used_items if k in items},
            'all_items': items, 'missing': sorted(missing)}


def model_scripts(models):
    """Las skills que lanzan los scripts «mm:» de las animaciones de los modelos."""
    out = set()
    for m in models.values():
        for a in m['anims'].values():
            for _t, skill in a.get('fx', []):
                out.add(skill)
    return out


def item_skills(items):
    out = set()
    for it in items.values():
        for line in it.get('Skills') or []:
            pm = C.parse_line(line)
            if pm:
                s2, _ = C.refs(pm)
                out.update(s2)
    return out


def me_textures(pack_dir, bid):
    """Las texturas de los .bbmodel del pack como las deja ModelEngine en su resource pack
    (assets/modelengine/textures/entity/<nombre>.png): una carpeta «assets» de apoyo en el directorio temporal."""
    import tempfile
    root = os.path.join(tempfile.gettempdir(), 'tf_bosses_me', bid, 'assets')
    shutil.rmtree(root, ignore_errors=True)
    dest = os.path.join(root, 'modelengine', 'textures', 'entity')
    os.makedirs(dest, exist_ok=True)
    for d, _, fs in os.walk(pack_dir):
        for f in fs:
            if not f.endswith('.bbmodel'):
                continue
            path = os.path.join(d, f)
            for t in json.load(open(path, encoding='utf-8')).get('textures', []):
                img = B.load_texture(t, path)
                name = os.path.splitext(str(t.get('name') or ''))[0].lower()
                if img is not None and name and not os.path.exists(os.path.join(dest, name + '.png')):
                    img.save(os.path.join(dest, name + '.png'))
    return root


def mob_kind(mob):
    """«entity» (un mob de verdad en el juego) o «actor» (un efecto que dibuja el cliente)."""
    opts = mob.get('options') or {}
    if mob['type'] in LIVING and not opts.get('Invincible'):
        return 'entity'
    return 'actor'


def build(pack_dir, name, conf, sound_events):
    pack = BS.Pack(pack_dir, name)
    pack.id = conf['id']
    bid = pack.id
    problems = pack.problems

    # Modelos de ModelEngine (todos los del pack: las skills los nombran por su nombre de archivo)
    me_models, converted = {}, {}
    store = B.TextureStore(os.path.join(OUT_TEX, bid, 'me'), f'tfclient:textures/bosses/{bid}/me/')
    for d, _, fs in sorted(os.walk(pack_dir)):
        for f in sorted(fs):
            if not f.endswith('.bbmodel'):
                continue
            mname = f[:-len('.bbmodel')].lower()
            if mname in me_models:
                continue
            m = B.convert(os.path.join(d, f), store, problems, boss=True)
            if m:
                mid = f'{bid}.{BS.slug(mname)}'
                me_models[mname] = mid
                converted[mname] = m

    sk_files, mob_files, item_files = files(pack_dir)
    all_items = load_all(item_files)
    prog = compile_boss(pack_dir, conf['mob'], model_scripts(converted) | item_skills(all_items))
    if conf['mob'] not in prog['mobs']:
        raise SystemExit(f'{name}: no está el mob {conf["mob"]}')

    # Sonidos
    sounds = BS.pack_sounds(pack)
    used_sounds = {}

    def sound_ref(s):
        s = str(s).strip()
        bare = s.split(':', 1)[-1]
        found = sounds.get(s) or sounds.get(bare) or BS.similar_sound(sounds, s)
        if found:
            ev = f'bosses.{bid}.{BS.slug(bare)}'
            used_sounds.setdefault(ev, found)
            return f'tfclient:{ev}'
        vanilla = bare if s.startswith('minecraft:') or ':' not in s else None
        vanilla = VC.SOUND_SUBST.get(vanilla, vanilla) if vanilla else None
        if vanilla and vanilla in VC.valid_sounds():
            return f'minecraft:{vanilla}'
        problems.append(f'sonido desconocido {s}')
        return None

    # Modelos de ítem: las balas «display» (material + model=CustomModelData) y los objetos que suelta el jefe.
    # Sus texturas «modelengine:entity/x» las genera ModelEngine desde los .bbmodel (el pack no las trae): se sacan de
    # ahí a un resource pack de apoyo
    pack.roots.append(me_textures(pack_dir, bid))
    models = BS.ItemModels(pack, 'bosses')
    item_models = {}

    def display_model(material, cmd):
        key = f'{str(material).lower()}:{cmd}'
        if key not in item_models:
            ref = BS.item_override(pack, material, cmd)
            mid = models.model(ref) if ref else None
            if not mid:
                problems.append(f'sin modelo de ítem para {key}')
            item_models[key] = mid
        return item_models[key]

    def fix_mech(pm):
        a = pm.get('a', {})
        m = pm['m']
        if m in ('effect:sound', 'sound', 'e:s', 'effect:s'):
            for k in ('s', 'sound'):
                if k in a:
                    r = sound_ref(a[k])
                    if r:
                        a[k] = r
        if m in ('model', 'state', 'changepart', 'partvis', 'partvisibility', 'tint', 'remapmodel', 'mountmodel',
                 'lockmodel', 'brightness', 'bodyrotation', 'bodyclamp', 'setmodelscale', 'renderinit', 'segment',
                 'defaultstate', 'dismountall', 'dismountmodel'):
            for k in ('mid', 'm', 'modelid', 'model', 'newmodelid', 'nmid', 'nm', 'n', 'newmodel'):
                if k in a and str(a[k]).lower() in me_models:
                    a[k] = me_models[str(a[k]).lower()]
        if m in ('projectile', 'missile') and str(a.get('bullettype', '')).lower() == 'display':
            mat = a.get('material') or a.get('mat') or 'stone'
            cmd = a.get('model') or a.get('custommodeldata') or a.get('cmd')
            mid = display_model(mat, cmd) if cmd else None
            if mid:
                a['bulletitem'] = mid
        return pm

    for ent in prog['tree'].values():
        ent['mechs'] = [fix_mech(pm) for pm in ent['mechs']]
    for mob in prog['mobs'].values():
        mob['mechs'] = [fix_mech(pm) for pm in mob['mechs']]
        mob['kind'] = mob_kind(mob)
        mob['speed'] = SPEED.get(mob['type'], 0.25)
        # El modelo de ModelEngine que se pone (para la vista previa, el tamaño y la caja de golpe del mob)
        for pm in mob['mechs']:
            if pm['m'] == 'model' and pm.get('tr') in (None, 'onspawn'):
                mid = pm['a'].get('mid') or pm['a'].get('m') or pm['a'].get('modelid')
                if mid:
                    mob['model'] = mid
                    break
    # Los modelos puestos dentro de una lista en línea del ~onSpawn (skill{s=[ - model{...} ]})
    for key, mob in prog['mobs'].items():
        if mob.get('model'):
            continue
        for pm in mob['mechs']:
            if pm['m'] in ('skill', 's') and pm.get('tr') in (None, 'onspawn'):
                sub = prog['tree'].get(pm['a'].get('s', ''))
                for sp in (sub or {}).get('mechs', []):
                    if sp['m'] == 'model':
                        mob['model'] = sp['a'].get('mid') or sp['a'].get('m')
                        break
            if mob.get('model'):
                break

    # Scripts de las animaciones: con el nombre de la skill tal como está en el árbol
    lower_tree = {k.lower(): k for k in prog['tree']}
    for m in converted.values():
        for a in m['anims'].values():
            if 'fx' in a:
                a['fx'] = [[t, lower_tree.get(s.lower(), s)] for t, s in a['fx']]

    # Objetos que suelta (dropitem): su modelo y nombre (el Esqueleto de la Torre deja su glaive, escudo y cuernos)
    drops = {}
    for iid, idef in prog['items'].items():
        mat = idef.get('Id') or idef.get('Material') or 'paper'
        cmd = idef.get('Model') or idef.get('CustomModelData')
        mid = display_model(mat, cmd) if cmd is not None else None
        drops[iid] = {'material': str(mat).lower(), 'model': mid, 'name': BS.strip_mc(idef.get('Display') or iid),
                      'type': str(idef.get('Type') or '').upper()}
        # el escudo cubriéndose: el modelo de «blocking» del mismo pack
        if str(mat).upper() == 'SHIELD' and cmd is not None:
            p = pack.find('minecraft/models/item/shield.json')
            if p:
                data = json.load(open(p, encoding='utf-8'))
                for ov in data.get('overrides', []):
                    pr = ov.get('predicate', {})
                    if pr.get('blocking') and int(float(pr.get('custom_model_data', -1))) == int(float(cmd)):
                        drops[iid]['blocking'] = models.model(ov['model'])
        # Sus skills (~onSwing del arma, ~onTimer del escudo), con los sonidos y modelos ya del mod
        drops[iid]['mechs'] = [fix_mech(pm) for pm in (C.parse_line(x) for x in (idef.get('Skills') or [])) if pm]
        drops[iid]['name_es'] = (ES.get(bid) or {}).get('drops', {}).get(iid) or drops[iid]['name']
        if mid:
            item = {'parent': mid}
            if drops[iid].get('blocking'):
                item['overrides'] = [{'predicate': {'blocking': 1}, 'model': drops[iid]['blocking']}]
            os.makedirs(OUT_ITEM_JSON, exist_ok=True)
            with open(os.path.join(OUT_ITEM_JSON, item_id(bid, iid) + '.json'), 'w', encoding='utf-8') as fo:
                json.dump(item, fo, indent=1)

    glyphs = BS.write_glyphs(pack, prog, 'bosses')

    # Sonidos al mod
    for ev, srcs in used_sounds.items():
        rels = []
        for i, src in enumerate(srcs):
            nm = ev.split('.')[-1] + (f'_{i}' if len(srcs) > 1 else '')
            dest = os.path.join(OUT_SOUNDS, bid, nm + '.ogg')
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            shutil.copyfile(src, dest)
            rels.append(f'tfclient:bosses/{bid}/{nm}')
        sound_events[ev] = {'sounds': rels}

    # Modelos de ModelEngine al mod
    os.makedirs(OUT_MODELS, exist_ok=True)
    for mname, mid in me_models.items():
        with open(os.path.join(OUT_MODELS, mid + '.json'), 'w', encoding='utf-8') as fo:
            json.dump(converted[mname], fo, separators=(',', ':'))

    es = ES.get(bid) or {}
    if not es:
        problems.append('sin textos en español (tools/bosses/es.json)')
    root = prog['mobs'][conf['mob']]
    hitbox = None
    if root.get('model'):
        mm = converted.get(root['model'].split('.', 1)[-1])
        hitbox = mm.get('hitbox') if mm else None
    boss = {'mob': conf['mob'], 'name': es.get('name') or BS.strip_mc(root.get('display') or conf['mob']),
            'title': es.get('title', ''), 'desc': es.get('desc', ''), 'color': es.get('color', '#ffffff'),
            'health': conf['vida'], 'damage': conf['dano'], 'pack_health': root.get('health') or conf['vida'],
            'model': root.get('model'), 'hitbox': hitbox, 'attacks': es.get('attacks', []),
            'preview_hide': es.get('preview_hide', [])}
    out = {'id': bid, 'pack': name, 'boss': boss, 'class': {'name': boss['name']}, 'skills': [],
           'tree': prog['tree'], 'mobs': prog['mobs'], 'models': me_models, 'item_models': {k: v for k, v in item_models.items() if v},
           'drops': drops, 'glyphs': glyphs, 'missing': [m.split(':', 1)[1] for m in prog['missing'] if m.startswith('skill:')]}
    for m in prog['missing']:
        problems.append(f'falta {m}')
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, bid + '.json'), 'w', encoding='utf-8') as f:
        json.dump(out, f, ensure_ascii=False, separators=(',', ':'))
    return pack, out


def item_id(bid, iid):
    """El id del objeto en el mod (igual que TFBosses.itemId)."""
    return re.sub(r'[^a-z0-9_]+', '_', f'jefe_{bid}_{iid}'.lower())


def main():
    root = sys.argv[1]
    only = set(sys.argv[2:])
    full = not only
    if full:
        for d in (OUT, OUT_TEX, OUT_ITEM_MODELS, OUT_SOUNDS):
            shutil.rmtree(d, ignore_errors=True)
        for f in os.listdir(BS.OUT_FONTS) if os.path.isdir(BS.OUT_FONTS) else []:
            if f.startswith('bosses_'):
                os.remove(os.path.join(BS.OUT_FONTS, f))
        for f in os.listdir(OUT_ITEM_JSON) if os.path.isdir(OUT_ITEM_JSON) else []:
            if f.startswith('jefe_'):
                os.remove(os.path.join(OUT_ITEM_JSON, f))
    sound_events, catalog = {}, []
    for name, conf in JEFES.items():
        if only and name not in only:
            continue
        d = os.path.join(root, name)
        if not os.path.isdir(d):
            print(f'falta el pack {name} en {root}')
            continue
        pack, out = build(d, name, conf, sound_events)
        catalog.append({'id': pack.id, 'name': out['boss']['name']})
        print(f"{pack.id:18s} {len(out['tree'])} skills, {len(out['mobs'])} mobs, {len(out['models'])} modelos ME, "
              f"{len(out['item_models'])} modelos de ítem, {len(out['drops'])} objetos, problemas: {len(pack.problems)}")
        for p in pack.problems[:int(os.environ.get('MAXP', 20))]:
            print('   -', p)
    sj = json.load(open(SOUNDS_JSON, encoding='utf-8')) if os.path.exists(SOUNDS_JSON) else {}
    if full:
        sj = {k: v for k, v in sj.items() if not k.startswith('bosses.')}
    sj.update(sound_events)
    with open(SOUNDS_JSON, 'w', encoding='utf-8') as f:
        json.dump(sj, f, ensure_ascii=False, indent=1)
    if full:
        order = [c['id'] for c in JEFES.values()]
        catalog.sort(key=lambda c: order.index(c['id']))
        with open(os.path.join(OUT, 'catalog.json'), 'w', encoding='utf-8') as f:
            json.dump(catalog, f, ensure_ascii=False, indent=1)
    if full:
        # El cristal de las grietas (modelo propio, no de un pack) vive en las mismas carpetas: se vuelve a hacer
        import rift_model
        rift_model.main()
    BS.write_atlas()


if __name__ == '__main__':
    main()
