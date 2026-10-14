"""Auditor de los jefes: revisa cada programa (assets/tfclient/bosses/<jefe>.json) contra el motor de Java y los datos
convertidos, sin abrir el juego. Lo lanza build_bosses.py al terminar; también suelto:

    python3 tools/bosses/audit.py [jefe ...]

Mira:
  - mecánicas, condiciones y objetivos (@X) que el motor no tiene (lee las etiquetas «case» de SkillRuntime.apply,
    SkillConds.test y SkillTargets.targets: si se implementa algo nuevo allí, el auditor lo sabe solo);
  - skills que se llaman y no existen (skill, randomskill, onHit/onTick/onEnd, castinstead...);
  - animaciones (state, defaultstate) que no tiene el modelo de ningún mob que llegue a esa skill;
  - huesos que no existen en su modelo (partvis, brightness, changepart, mountmodel, @modelpart, origin=@ModelPart{}),
    con las mismas reglas que VfxModel.bone (sin prefijo de comportamiento: «right_arm_trail» es «tl_right_arm_trail»)
    y em=false (los que lo llevan en el nombre);
  - sonidos tfclient: que estén en sounds.json y que sus .ogg existan; modelos y objetos que no existen;
  - mobs sin modelo y lo que build_bosses.py apuntó como «missing».

Sale con código 1 si hay algún problema. Cada problema dice dónde está (skill o mob) para buscarlo en el pack.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
JAVA = os.path.join(ROOT, 'src', 'main', 'java', 'net', 'tierrasfantasticas', 'tfclient', 'skills')
RES = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'tfclient')
BOSSES = os.path.join(RES, 'bosses')

CALL_KEYS = ('s', 'skill', 'skills', 'oh', 'onhit', 'ot', 'ontick', 'oe', 'onend', 'os', 'onstart', 'onbouncedskill',
             'onbounce', 'ob')
CALLERS = {'skill', 'metaskill', 'cast', 's', 'randomskill', 'skillsequence', 'sudoskill', 'sudo'}
# Las que llaman skills al chocar, cada tick, al acabar... (en un summon, «os» es «onSurface», no una skill)
CALLBACKS = {'projectile', 'p', 'missile', 'totem', 'orbital', 'shoot', 'shootfireball', 'aura', 'buff', 'debuff',
             'ondamaged', 'onattack', 'onshoot'}
SELF = (None, 'self', 'caster')


# ----------------------------------------------------------------------------------------------- lo que sabe el motor

def _labels(path, start):
    """Etiquetas de los «case "a", "b" ->» del switch que empieza en la línea que contiene «start»."""
    src = open(path, encoding='utf-8').read()
    i = src.index(start)
    # hasta el «default ->» de ese switch (el primero con la misma sangría que sus case)
    first = re.search(r'\n(\s*)case\s', src[i:])
    indent = first.group(1)
    end = src.index('\n' + indent + 'default ->', i)
    region = src[i:end]
    out = set()
    for grp in re.findall(r'case\s+((?:"[^"]*"\s*,\s*)*"[^"]*")\s*->', region, re.S):
        out.update(re.findall(r'"([^"]*)"', grp))
    return out


def engine():
    rt = os.path.join(JAVA, 'SkillRuntime.java')
    mechs = _labels(rt, 'switch (m.m) {')
    # Las que se resuelven antes de llegar al switch (el guion de la skill, los disparadores de los mobs)
    src = open(rt, encoding='utf-8').read()
    mechs.update(re.findall(r'm\.m\.equals\("([^"]+)"\)', src))
    mechs.update({'delay', 'wait'})
    conds = _labels(os.path.join(JAVA, 'SkillConds.java'), 'return switch (c.m()) {')
    conds.update({'or', 'and'})  # SkillConds.test las resuelve antes del switch
    targs = _labels(os.path.join(JAVA, 'SkillTargets.java'), 'List<Tgt> out = switch (t) {')
    return mechs, conds, targs


# ----------------------------------------------------------------------------------------------- modelos

_models = {}


def model(mid):
    if mid not in _models:
        p = os.path.join(BOSSES, 'models', mid + '.json')
        _models[mid] = json.load(open(p, encoding='utf-8')) if os.path.exists(p) else None
    return _models[mid]


def bone(m, part, exact=True):
    """Como VfxModel.bone (exacto, sin mayúsculas, o sin el prefijo de comportamiento) o, con em=false, los que lo
    llevan en el nombre. Devuelve cuántos huesos encuentra."""
    p = part.lower()
    ids = [b['id'] for b in m['bones']]
    if not exact:
        return sum(1 for i in ids if p in i.lower())
    if part in ids or any(i.lower() == p for i in ids):
        return 1
    return sum(1 for i in ids if i.lower().endswith('_' + p) and i.index('_') == len(i) - len(p) - 1)


def exact(v):
    """em=...: como SkillRuntime.bool (solo «true», «1» o «yes» es sí; el «em=bubo» de la Abominación es no)."""
    return str(v).lower() in ('true', '1', 'yes')


def anims(m):
    return set(m['anims']) if m else set()


# ----------------------------------------------------------------------------------------------- recorrer el programa

def called(pm):
    """Skills que lanza una línea."""
    a = pm.get('a', {})
    out = []
    for k in CALL_KEYS:
        v = a.get(k)
        if v is None:
            continue
        if k in ('s', 'skill', 'skills') and pm['m'] not in CALLERS:
            continue
        if k not in ('s', 'skill', 'skills') and pm['m'] not in CALLBACKS:
            continue
        for part in str(v).split(','):
            n = part.strip().split(' ')[0]
            if n:
                out.append(n)
    return out


def conds_of(meta):
    for key in ('conditions', 'target_conditions', 'trigger_conditions'):
        for c in meta.get(key) or []:
            yield c


def flat(conds):
    for c in conds or []:
        yield c
        yield from flat(c.get('parts'))


def audit(bid, engine_sets):
    mechs, condset, targset = engine_sets
    prog = json.load(open(os.path.join(BOSSES, bid + '.json'), encoding='utf-8'))
    pack = bid
    tree = prog['tree']
    lower = {k.lower(): k for k in tree}
    problems = []

    def find(n):
        return lower.get(str(n).lower())

    sounds = json.load(open(os.path.join(RES, 'sounds.json'), encoding='utf-8'))

    # Qué mobs llegan a cada skill (para saber con qué modelo se hace)
    reach = {}
    for mob, md in prog['mobs'].items():
        todo = [n for pm in md['mechs'] for n in called(pm)]
        seen = set()
        while todo:
            k = find(todo.pop())
            if k is None or k in seen:
                continue
            seen.add(k)
            reach.setdefault(k, set()).add(mob)
            for pm in tree[k]['mechs']:
                todo += called(pm)
            for c in flat(list(conds_of(tree[k]))):
                act = str(c.get('v', ''))
                if act.lower().startswith(('castinstead', 'orelsecast', 'cast ')):
                    todo.append(act.split(None, 1)[1] if ' ' in act else '')

    all_models = sorted({md.get('model') for md in prog['mobs'].values() if md.get('model')})
    all_anims = set().union(*[anims(model(m)) for m in all_models]) if all_models else set()

    def mob_models(where_meta, where_mob):
        if where_mob:
            mm = prog['mobs'][where_mob].get('model')
            return [mm] if mm else []
        mobs = reach.get(where_meta)
        if not mobs:
            return all_models
        return sorted({prog['mobs'][x].get('model') for x in mobs if prog['mobs'][x].get('model')})

    def model_id(raw, default):
        if not raw:
            return default
        raw = str(raw).lower()
        return raw if '.' in raw else pack + '.' + raw

    def check_line(pm, where, meta=None, mob=None):
        m, a = pm['m'], pm.get('a', {})
        if m not in mechs:
            problems.append(f'{where}: mecánica «{m}» sin implementar')
        t = pm.get('t')
        if t is not None and t not in targset:
            problems.append(f'{where}: objetivo «@{t}» sin implementar ({m})')
        for c in flat(pm.get('c')) if pm.get('c') else []:
            if c['m'] not in condset:
                problems.append(f'{where}: condición «{c["m"]}» sin implementar')
        for c in flat(pm.get('tc')) if pm.get('tc') else []:
            if c['m'] not in condset:
                problems.append(f'{where}: condición de objetivo «{c["m"]}» sin implementar')
        for n in called(pm):
            if find(n) is None and not n.startswith('<'):
                problems.append(f'{where}: llama a la skill «{n}», que no existe')
        models = mob_models(meta, mob)
        default = models[0] if len(models) == 1 else None
        # Animaciones
        if m in ('state', 'animation', 'defaultstate') and t in SELF:
            s = a.get('state', a.get('s', a.get('animation', a.get('a'))))
            if s and str(a.get('r', a.get('remove', 'false'))).lower() != 'true':
                # (SkillModels.state no mira su «model»: el «wood_cirlce» de Astwood va igual)
                have = set().union(*[anims(model(x)) for x in models]) if models else all_anims
                if s not in have:
                    problems.append(f'{where}: la animación «{s}» no está en {", ".join(models) or "ningún modelo"}')
        # Huesos
        refs = []
        if m in ('partvis', 'partvisibility', 'brightness', 'changepart'):
            part = a.get('p', a.get('part', a.get('partid', a.get('pid'))))
            em = exact(a.get('em', a.get('exactmatch', 'true')))
            if part:
                refs.append((model_id(a.get('m', a.get('mid', a.get('modelid'))), default), part, em))
        if m == 'mountmodel':
            part = a.get('p', a.get('pbone', a.get('seat')))
            if part:
                refs.append((model_id(a.get('m', a.get('mid')), default), part, True))
        if t == 'modelpart':
            ta = pm.get('ta') or {}
            part = ta.get('pid', ta.get('p', ta.get('part')))
            em = exact(ta.get('em', 'true'))
            if part:
                refs.append((model_id(ta.get('mid', ta.get('m')), default), part, em))
        for v in a.values():
            for mid, pid in re.findall(r'@modelpart\{[^}]*?(?:mid|m)=([\w.]+)[^}]*?pid=([\w]+)', str(v), re.I):
                refs.append((model_id(mid, default), pid, True))
            for tg in re.findall(r'@(\w+)', str(v)):
                if tg.lower() not in targset and not str(v).startswith('<'):
                    problems.append(f'{where}: objetivo «@{tg}» (en un argumento) sin implementar')
        for mid, part, em in refs:
            cands = [mid] if mid else models
            if not cands:
                continue
            if not any(model(x) and bone(model(x), part, em) for x in cands):
                problems.append(f'{where}: el hueso «{part}» no está en {", ".join(c for c in cands if c)}')
            for x in cands:
                if x and model(x) is None:
                    problems.append(f'{where}: no existe el modelo {x}')
        # Modelos, sonidos y objetos
        if m == 'model':
            mid = model_id(a.get('m', a.get('mid', a.get('model'))), None)
            if mid and model(mid) is None:
                problems.append(f'{where}: no existe el modelo {mid}')
        if m in ('sound', 'effect:sound', 'e:s', 'effect:s'):
            s = str(a.get('s', a.get('sound', '')))
            if s.startswith('tfclient:'):
                key = s.split(':', 1)[1]
                if key not in sounds:
                    problems.append(f'{where}: el sonido {s} no está en sounds.json')
        for k in ('bulletitem', 'item'):
            v = a.get(k)
            if isinstance(v, str) and v.startswith('tfclient:'):
                if not os.path.exists(os.path.join(RES, 'models', v.split(':', 1)[1] + '.json')):
                    problems.append(f'{where}: no existe el modelo de objeto {v}')

    for name, meta in tree.items():
        for i, pm in enumerate(meta['mechs']):
            check_line(pm, f'skill {name} #{i + 1}', meta=name)
        for c in flat(list(conds_of(meta))):
            if c['m'] not in condset:
                problems.append(f'skill {name}: condición «{c["m"]}» sin implementar')
            act = str(c.get('v', ''))
            if act.lower().startswith(('castinstead', 'orelsecast')) and ' ' in act:
                n = act.split(None, 1)[1].strip()
                if find(n) is None:
                    problems.append(f'skill {name}: {act.split()[0]} a «{n}», que no existe')
    for mob, md in prog['mobs'].items():
        if not md.get('model') and md.get('kind') == 'entity':
            problems.append(f'mob {mob}: sin modelo de ModelEngine (se vería el mob de Minecraft)')
        for i, pm in enumerate(md['mechs']):
            check_line(pm, f'mob {mob} #{i + 1} ({pm.get("tr") or "onspawn"})', mob=mob)
    for x in prog.get('missing') or []:
        problems.append(f'falta en el pack: {x}')
    # Los .ogg de sus sonidos
    for key, ev in sounds.items():
        if not key.startswith('bosses.' + bid + '.'):
            continue
        for snd in ev.get('sounds', []):
            name = snd if isinstance(snd, str) else snd.get('name', '')
            if name.startswith('tfclient:') and not os.path.exists(os.path.join(RES, 'sounds', name.split(':', 1)[1] + '.ogg')):
                problems.append(f'sonido {key}: falta el archivo {name}.ogg')
    # Sin repetir (la misma línea en onspawn y onload)
    seen, out = set(), []
    for p in problems:
        if p not in seen:
            seen.add(p)
            out.append(p)
    return out


def main(ids=None):
    sets = engine()
    if not ids:
        ids = [c['id'] for c in json.load(open(os.path.join(BOSSES, 'catalog.json'), encoding='utf-8'))]
    total = 0
    for bid in ids:
        probs = audit(bid, sets)
        total += len(probs)
        print(f'auditoría {bid:16s} problemas: {len(probs)}')
        for p in probs:
            print('   -', p)
    return total


if __name__ == '__main__':
    sys.exit(1 if main([a for a in sys.argv[1:] if not a.startswith('-')]) else 0)
