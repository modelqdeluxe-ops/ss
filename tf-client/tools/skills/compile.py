"""Compila un pack de skills (MMOCore + MythicLib + MythicMobs) a un programa que ejecuta el TF Client.

    python3 tools/skills/compile.py <carpeta del pack descomprimido> [salida.json]

Qué sale (un JSON por clase):
  class   id, nombre, descripción
  skills  las de la clase en orden: id, nombre, descripción, cooldown (s), maná, skill de MythicMobs de entrada,
          icono (ítem de MMOCore) y cómo se lanzaba en el pack (clic del arma), solo como referencia
  tree    las skills de MythicMobs que se alcanzan desde las de la clase: cooldown, condiciones, condiciones de
          objetivo y sus mecánicas ya separadas: {m: mecánica, a: argumentos, t: objetivo, ta: args del objetivo,
          tr: disparador, c: condición en línea}
  mobs    los mobs de efecto que se invocan: tipo, opciones, ítem en la cabeza, modelo de ModelEngine y sus mecánicas
  items   los ítems de MythicMobs que se usan (para modelos en la cabeza de los soportes)
Se elige la versión para MMOCore (teclas) del pack: fuera las de «solo daño a mobs», Crucible, «sin daño a
jugadores» y los colores alternativos. El daño a jugadores lo decide luego el servidor (PvP sí/no).
"""
import json
import os
import re
import sys

import yaml

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from inventario import leveled, load, norm, scan, strip_colors  # noqa: E402

EXCLUDE = re.compile(r'only.?damage|crucible|no player dmg|_npd|no_take_away|yellow|mmoitems\.yml$|_mmoitems', re.I)
# argumentos de mecánicas que nombran otras skills o mobs
_UNUSED_SKILL_ARGS = ('s', 'skill', 'skills', 'ontick', 'onhit', 'onend', 'onstart', 'onbounce', 'onhitblock', 'oninterval',
              'onremove', 'onfinish', 'ontickskill', 'onhitskill', 'onendskill', 'onstartskill', 'then', 'else',
              'oncast', 'onapply', 'onexpire', 'onattack', 'ondamaged', 'skillontick', 'onlandskill', 'onland')
MOB_ARGS = ('type', 't', 'mob', 'mobtype', 'm', 'bullettype_mob')


def split_top(s, sep):
    out, depth, cur, quote = [], 0, '', False
    for ch in s:
        if ch == '"':
            quote = not quote
        if not quote:
            if ch in '[{':
                depth += 1
            elif ch in ']}':
                depth -= 1
        if ch == sep and depth == 0 and not quote:
            out.append(cur)
            cur = ''
        else:
            cur += ch
    out.append(cur)
    return out


def parse_args(s):
    args = {}
    for part in split_top(s, ';'):
        if '=' in part:
            k, v = part.split('=', 1)
            v = v.strip()
            if len(v) >= 2 and v[0] == '"' and v[-1] == '"':
                v = v[1:-1]
            args[k.strip().lower()] = v
    return args


def take_braces(text):
    """text empieza por '{': devuelve (contenido, resto)."""
    depth, quote = 0, False
    for i, ch in enumerate(text):
        if ch == '"':
            quote = not quote
        if quote:
            continue
        depth += ch == '{'
        depth -= ch == '}'
        if depth == 0:
            return text[1:i], text[i + 1:]
    return text[1:], ''


def parse_line(line):
    """«- mecánica{args} @objetivo{args} ~disparador ?condición 0.5» → dict, o None."""
    line = str(line).strip()
    if line.startswith('-'):
        line = line[1:].strip()
    m = re.match(r'^([\w:.\-]+)', line)
    if not m:
        return None
    name = m.group(1).lower()
    if name.startswith('e:'):
        name = 'effect:' + name[2:]
    rest = line[m.end():]
    args = {}
    if rest.startswith('{'):
        body, rest = take_braces(rest)
        args = parse_args(body)
    # valor suelto tras la mecánica («delay 28», «aura 20»...)
    pm = re.match(r'^\s+([^\s@~?{}]+)', rest)
    if pm and not re.fullmatch(r'0?\.\d+', pm.group(1)):
        args = dict(args)
        args['_'] = pm.group(1)
        rest = rest[:pm.start()] + rest[pm.end():]
    out = {'m': name, 'a': args}
    # objetivo
    tm = re.search(r'@([\w]+)', rest)
    if tm:
        out['t'] = tm.group(1).lower()
        after = rest[tm.end():]
        if after.startswith('{'):
            body, after2 = take_braces(after)
            out['ta'] = parse_args(body)
            rest = rest[:tm.start()] + ' ' + after2
        else:
            rest = rest[:tm.start()] + ' ' + after
    gm = re.search(r'~(\w+)(?::(\w+))?', rest)
    if gm:
        out['tr'] = gm.group(1).lower()
        if gm.group(2):
            out['trv'] = gm.group(2)
        rest = rest[:gm.start()] + rest[gm.end():]
    cm = re.findall(r'\?(!?)([\w:]+)(\{[^}]*\})?', rest)
    if cm:
        out['c'] = [{'not': bool(neg), 'm': c.lower(), 'a': parse_args(a[1:-1]) if a else {}} for neg, c, a in cm]
        rest = re.sub(r'\?(!?)([\w:]+)(\{[^}]*\})?', '', rest)
    hm = re.search(r'(?<![\w.])(0?\.\d+|1(?:\.0+)?)\s*$', rest.strip())
    if hm:
        out['ch'] = float(hm.group(1))  # probabilidad
    return out


def parse_condition(line):
    line = str(line).strip().lstrip('-').strip()
    m = re.match(r'^([\w:]+)\s*(\{[^}]*\})?\s*(.*)$', line)
    if not m:
        return None
    rest = m.group(3).strip().lower()
    return {'m': m.group(1).lower(), 'a': parse_args(m.group(2)[1:-1]) if m.group(2) else {},
            'v': rest or 'true'}


def pick_files(pack_dir):
    """Archivos de MythicMobs de la versión MMOCore del pack: (skills, mobs, items)."""
    skills, mobs, items = [], [], []
    cands = []
    for d, _, fs in os.walk(pack_dir):
        for f in fs:
            p = os.path.join(d, f)
            lp = '/' + os.path.relpath(p, pack_dir).lower()  # solo lo de dentro del pack (no la carpeta donde está)
            if not f.lower().endswith(('.yml', '.yaml')) or 'mythicmobs' not in lp or EXCLUDE.search(lp):
                continue
            cands.append(p)
    rel = lambda p: '/' + os.path.relpath(p, pack_dir).lower()  # noqa: E731
    sk = [p for p in cands if re.search(r'/skills?/', rel(p))]
    mm = [p for p in sk if 'mmocore' in os.path.basename(p).lower()]
    if mm:
        # la carpeta de la versión MMOCore y la normal comparten mobs; los archivos sin «mmocore» de la misma
        # carpeta son la versión para MMOItems (otro reparto de condiciones): fuera
        sk = mm + [p for p in sk if 'mmocore' not in os.path.basename(p).lower()
                   and os.path.dirname(p) not in {os.path.dirname(x) for x in mm}]
    roots = {os.path.dirname(os.path.dirname(p)) for p in sk} or {pack_dir}
    for p in cands:
        lp = rel(p)
        if not any(p.startswith(r) for r in roots):
            continue
        if re.search(r'/mobs?/', lp):
            mobs.append(p)
        elif re.search(r'/items?/', lp):
            items.append(p)
    return sk, mobs, items


# mecánicas que llaman a otra skill con s= / skill= (en las demás, s= es un sonido, un estado de animación...)
CALLERS = {'skill', 'metaskill', 'cast', 'sudoskill', 'randomskill', 'skillsequence', 'delayedskill', 'forcepull'}
CALLBACK_KEYS = ('ontick', 'onhit', 'onend', 'onstart', 'onbounce', 'onhitblock', 'oninterval', 'onremove', 'onfinish',
                 'ontickskill', 'onhitskill', 'onendskill', 'onstartskill', 'then', 'else', 'oncast', 'onapply',
                 'onexpire', 'onattack', 'ondamaged', 'onlandskill', 'onland', 'onbreak', 'ondeath', 'onswing',
                 'ondamagedskill', 'onhitentity',
                 # atajos de MythicMobs
                 'oh', 'ot', 'oe', 'os', 'ob', 'ohb', 'oi', 'ontickskill')


def inline_list(v):
    """«[ - mecánica ... - mecánica ... ]» → lista de mecánicas."""
    body = v.strip()[1:-1] if v.strip().endswith(']') else v.strip()[1:]
    items, depth, cur, quote = [], 0, '', False
    i = 0
    while i < len(body):
        ch = body[i]
        if ch == '"':
            quote = not quote
        if not quote:
            if ch in '{[':
                depth += 1
            elif ch in '}]':
                depth -= 1
        if not quote and depth == 0 and ch == '-' and (i == 0 or body[i - 1] in ' \t\n') and \
                (i + 1 < len(body) and body[i + 1] in ' \t'):
            if cur.strip():
                items.append(cur.strip())
            cur = ''
        else:
            cur += ch
        i += 1
    if cur.strip():
        items.append(cur.strip())
    return [pm for pm in (parse_line(x) for x in items) if pm]


def skill_names(v):
    return [x.strip().strip('"') for x in re.split(r'[,\s]+', str(v)) if x.strip().strip('"')]


def refs(mech):
    """Skills y mobs que nombra una mecánica (las listas en línea ya se han sacado aparte)."""
    a = mech.get('a', {})
    skills, mobs = [], []
    m = mech['m']
    if m in CALLERS:
        for k in ('s', 'skill', 'skills', '$skill', 'spell'):
            if a.get(k):
                skills += skill_names(a[k])
    for k in CALLBACK_KEYS:
        if a.get(k):
            skills += skill_names(a[k])
    if m in ('summon', 'totem', 'projectile', 'missile', 'orbital', 'shoot', 'mountmodel', 'mythicmobtype', 'spawn'):
        for k in ('type', 't', 'mob', 'mobtype', 'bullettype', 'bm', 'bulletmob', 'mm'):
            v = a.get(k)
            if v and v.upper() not in ('MOB', 'ARROW', 'ITEM', 'DISPLAY', 'BLOCK', 'SMALL_FIREBALL', 'TRIDENT',
                                       'ME', 'MODELENGINE'):
                mobs.append(v)
    return skills, mobs


def extract_inline(mechs, tree_out, prefix):
    """Las listas en línea de los argumentos de llamada pasan a ser skills con nombre propio."""
    for idx, pm in enumerate(mechs):
        for k, v in list(pm.get('a', {}).items()):
            if (k in CALLBACK_KEYS or (pm['m'] in CALLERS and k in ('s', 'skill', 'skills'))) and str(v).strip().startswith('['):
                name = f'{prefix}__{idx}_{k}'
                sub = inline_list(v)
                tree_out[name] = {'cooldown': None, 'conditions': [], 'target_conditions': [],
                                  'trigger_conditions': [], 'mechs': sub}
                extract_inline(sub, tree_out, name)
                pm['a'][k] = name


def compile_pack(pack_dir):
    inv = scan(pack_dir)
    sk_files, mob_files, item_files = pick_files(pack_dir)
    tree, mobs, items = {}, {}, {}
    for p in sk_files:
        for k, v in (load(p) or {}).items():
            if isinstance(v, dict):
                tree.setdefault(k, v)
    for p in mob_files:
        for k, v in (load(p) or {}).items():
            if isinstance(v, dict):
                mobs.setdefault(k, v)
    for p in item_files:
        for k, v in (load(p) or {}).items():
            if isinstance(v, dict):
                items.setdefault(k, v)
    lower_tree = {k.lower(): k for k in tree}
    lower_mobs = {k.lower(): k for k in mobs}

    # clase y skills en orden
    cls = inv['classes'][0] if inv['classes'] else None
    order = [norm(s) for s in (cls['skills'] if cls else [])] or list(inv['skills'].keys())
    out_skills = []
    for sid in order:
        s = inv['skills'].get(sid)
        if not s:
            continue
        entry = s.get('mm') or sid
        if entry.lower() not in lower_tree:
            # algunos packs nombran la skill de MythicMobs igual que la de MMOCore
            for cand in (sid, sid.title(), sid.replace('_', '')):
                if cand.lower() in lower_tree:
                    entry = cand
                    break
        # la clase puede fijar otro cooldown o maná para la skill
        conf = (cls or {}).get('skill_conf', {}).get(sid, {})
        cd, mana = s.get('cooldown'), s.get('mana')
        mods = dict(s.get('mods', {}))
        for k, v in conf.items():
            if isinstance(v, dict) and 'base' in v:
                mods[str(k).lower()] = leveled(v)
        if 'cooldown' in mods and isinstance(conf.get('cooldown'), dict):
            cd = mods['cooldown']
        if 'mana' in mods and isinstance(conf.get('mana'), dict):
            mana = mods['mana']
        s = dict(s, mods=mods)
        out_skills.append({'id': sid.lower(), 'name': s.get('name') or sid.title(), 'lore': s.get('lore', []),
                           'cooldown': cd, 'mana': mana, 'passive': s.get('passive'), 'mods': s.get('mods', {}),
                           'entry': lower_tree.get(entry.lower()), 'icon': s.get('icon_item'),
                           'modes': s.get('modes', [])})

    # lo que se alcanza desde las skills de la clase
    reach_s, reach_m, missing = set(), set(), set()
    stack = [s['entry'] for s in out_skills if s['entry']]
    mob_stack = []
    compiled_tree, compiled_mobs = {}, {}
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
            mechs = [pm for pm in (parse_line(l) for l in (v.get('Skills') or [])) if pm]
            inline = {}
            extract_inline(mechs, inline, key)
            for iname, ient in inline.items():
                compiled_tree[iname] = ient
                lower_tree[iname.lower()] = iname
                for pm in ient['mechs']:
                    s2, m2 = refs(pm)
                    stack += s2
                    mob_stack += m2
            compiled_tree[key] = {
                'cooldown': v.get('Cooldown'),
                'conditions': [c for c in (parse_condition(x) for x in (v.get('Conditions') or [])) if c],
                'target_conditions': [c for c in (parse_condition(x) for x in (v.get('TargetConditions') or [])) if c],
                'trigger_conditions': [c for c in (parse_condition(x) for x in (v.get('TriggerConditions') or [])) if c],
                'mechs': mechs}
            for pm in mechs:
                s2, m2 = refs(pm)
                stack += s2
                mob_stack += m2
            # condiciones que lanzan otra skill («castinstead X», «orElseCast X»)
            for cl in ('conditions', 'target_conditions', 'trigger_conditions'):
                for c in compiled_tree[key][cl]:
                    cm = re.match(r'(?:castinstead|orelsecast)\s+(\S+)', c.get('v', ''))
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
            mechs = [pm for pm in (parse_line(l) for l in (v.get('Skills') or [])) if pm]
            inline = {}
            extract_inline(mechs, inline, 'mob_' + key)
            for iname, ient in inline.items():
                compiled_tree[iname] = ient
                lower_tree[iname.lower()] = iname
                for pm in ient['mechs']:
                    s2, m2 = refs(pm)
                    stack += s2
                    mob_stack += m2
            compiled_mobs[key] = {'type': v.get('Type', 'ZOMBIE'), 'options': v.get('Options') or {},
                                  'head': v.get('ItemHead') or (v.get('Equipment') or [None])[0],
                                  'display': v.get('Display'), 'health': v.get('Health'), 'mechs': mechs}
            for pm in mechs:
                s2, m2 = refs(pm)
                stack += s2
                mob_stack += m2
    used_items = {}
    for coll in list(compiled_tree.values()) + list(compiled_mobs.values()):
        for pm in coll['mechs']:
            if pm['m'] == 'equip':
                it = pm['a'].get('item') or pm['a'].get('i') or ''
                iid = it.split(':')[0]
                if iid in items:
                    used_items[iid] = items[iid]
    for mob in compiled_mobs.values():
        h = mob.get('head')
        if isinstance(h, str) and h.split(':')[0] in items:
            used_items[h.split(':')[0]] = items[h.split(':')[0]]
    return {'pack': os.path.basename(pack_dir),
            'class': {'name': cls['name'] if cls else inv['pack'], 'lore': cls['lore'] if cls else []},
            'skills': out_skills, 'tree': compiled_tree, 'mobs': compiled_mobs, 'items': used_items,
            'weapons': inv['weapons'], 'armor': inv['armor'], 'missing': sorted(missing),
            'files': [os.path.relpath(p, pack_dir) for p in sk_files + mob_files + item_files]}


def stats(c):
    from collections import Counter
    mech = Counter()
    for coll in list(c['tree'].values()) + list(c['mobs'].values()):
        for pm in coll['mechs']:
            mech[pm['m']] += 1
    return mech


def main():
    pack = sys.argv[1]
    c = compile_pack(pack)
    no_entry = [s['id'] for s in c['skills'] if not s['entry']]
    print(f"{c['pack']}: clase «{c['class']['name']}», {len(c['skills'])} skills "
          f"(sin entrada: {no_entry or 'ninguna'}), árbol {len(c['tree'])}, mobs {len(c['mobs'])}, "
          f"ítems {len(c['items'])}, faltan {len(c['missing'])}")
    if len(sys.argv) > 2:
        with open(sys.argv[2], 'w', encoding='utf-8') as f:
            json.dump(c, f, ensure_ascii=False, indent=1)


if __name__ == '__main__':
    main()
