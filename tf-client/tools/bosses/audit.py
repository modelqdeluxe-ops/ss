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
  - mobs sin modelo y lo que build_bosses.py apuntó como «missing»;
  - lo que el motor ACEPTA PERO NO HACE (lo lee del Java: mecánicas con «-> { /* ... */ }» y condiciones con
    «-> true;»): si un pack lo usa y no está en STUB_OK (con su porqué), es un problema; si está, sale como aviso;
  - disparadores (~onX) que el motor no lanza (lee los run(h, "...") de SkillMobs).

Sale con código 1 si hay algún problema. Cada problema dice dónde está (skill o mob) para buscarlo en el pack.
Los avisos (·) no cuentan, pero léelos: dicen lo que no se verá igual que con MythicMobs/ModelEngine.

    python3 tools/bosses/audit.py --cobertura    # qué tiene el motor de MythicMobs/ModelEngine y qué le falta
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

# Lo que el motor acepta sin hacer nada a propósito, y por qué no cambia la pelea. Lo que esté «de mentira» en el Java y
# NO esté aquí es un problema si un pack lo usa (impleméntalo con la semántica de MythicMobs o apúntalo aquí con su
# porqué, si de verdad no importa).
STUB_OK = {
    # mecánicas
    'mount': 'los efectos del pack no se montan en nada (los agarres van con mountmodel)',
    'mounttarget': 'los efectos del pack no se montan en nada (los agarres van con mountmodel)',
    'dismount': 'los efectos del pack no se montan en nada (los agarres van con mountmodel)',
    'animatearmorstand': 'soportes de armadura de Minecraft: los jefes y sus efectos son modelos de ModelEngine',
    'posearmorstand': 'soportes de armadura de Minecraft: los jefes y sus efectos son modelos de ModelEngine',
    'runaitargetselector': 'IA de Bukkit: la de los jefes es BossEntity + su cerebro (SkillBrain)',
    'runaigoalselector': 'IA de Bukkit: la de los jefes es BossEntity + su cerebro (SkillBrain)',
    'modifyglobalscore': 'marcadores globales: no hay ninguno (ninguna condición los lee)',
    'setglobalscore': 'marcadores globales: no hay ninguno (ninguna condición los lee)',
    'feed': 'el hambre es de los jugadores',
    'playanimation': 'el brazo de Minecraft: los jefes son modelos',
    'swing': 'el brazo de Minecraft: los jefes son modelos',
    'removehelditem': 'los jefes no llevan objetos de Minecraft en la mano',
    'setcollidable': 'los jefes no empujan ni se dejan empujar ya',
    'bindhitbox': 'las cajas de golpe salen de los huesos ob_ del modelo (BossPart)',
    'cullconfig': 'ajuste de dibujo de ModelEngine que aquí no hace falta',
    'renderinit': 'ajuste de dibujo de ModelEngine que aquí no hace falta',
    # condiciones que se dan por cumplidas
    'wearing': 'se da por cumplida (lo que lleva puesto alguien no se mira)',
    'haspermission': 'se da por cumplida (permisos de Bukkit)',
}
# Mecánicas que nombran un hueso del modelo (p/pid/part/partid/b/bone): el auditor mira que exista
BONE_MECHS = {'partvis', 'partvisibility', 'brightness', 'changepart', 'segment', 'seg', 'enchant', 'billboard', 'glowbone',
              'setmodeltag', 'setmodeltagvisible', 'setitemmodel', 'linkitembone', 'headconfig', 'hitboxconfig',
              'jiggleimpulse', 'cycleparts', 'ik', 'inversekinematics', 'iktarget', 'tint', 'vfx', 'vfxchangemodel',
              'animatemodel'}
# Disparadores que el motor no lanza pero que no hacen falta en estos casos (ver trigger_ok)
QUIET_TRIGGERS = {'onload', 'onattack'}


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


def _stubs(path, start, body):
    """Etiquetas de los case de ese switch cuyo cuerpo es «body» (una regex): los que no hacen nada."""
    src = open(path, encoding='utf-8').read()
    i = src.index(start)
    first = re.search(r'\n(\s*)case\s', src[i:])
    end = src.index('\n' + first.group(1) + 'default ->', i)
    out = set()
    for grp in re.findall(r'case\s+((?:"[^"]*"\s*,\s*)*"[^"]*")\s*->\s*' + body, src[i:end], re.S):
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


def stubs():
    """Lo que el motor acepta sin hacer nada: mecánicas «-> { /* ... */ }» y condiciones «-> true;»."""
    m = _stubs(os.path.join(JAVA, 'SkillRuntime.java'), 'switch (m.m) {', r'\{\s*(?:/\*.*?\*/)?\s*\}')
    c = _stubs(os.path.join(JAVA, 'SkillConds.java'), 'return switch (c.m()) {', r'true\s*;')
    return m, c


def triggers():
    """Disparadores que lanza SkillMobs (run(h, "x", ...)), más ~onTimer:N y ~onSignal:X, que van aparte."""
    src = open(os.path.join(JAVA, 'SkillMobs.java'), encoding='utf-8').read()
    return set(re.findall(r'run\(h,\s*"(\w+)"', src)) | {'ontimer', 'onsignal'}


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


def bone_any(mid, models, part):
    """¿Algún modelo (ese o los del mob) tiene un hueso que se llame así tal cual?"""
    for x in ([mid] if mid else models):
        if x and model(x) and bone(model(x), str(part)):
            return True
    return False


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


def audit(bid, engine_sets, warnings=None, prog=None):
    mechs, condset, targset = engine_sets
    stub_m, stub_c = stubs()
    fired = triggers()
    if prog is None:
        prog = json.load(open(os.path.join(BOSSES, bid + '.json'), encoding='utf-8'))
    pack = bid
    tree = prog['tree']
    lower = {k.lower(): k for k in tree}
    problems = []
    warns = warnings if warnings is not None else []

    def stub(kind, name, where):
        if name in STUB_OK:
            warns.append((f'{kind} «{name}» no hace nada aquí: {STUB_OK[name]}', where))
        else:
            problems.append(f'{where}: {kind} «{name}» se acepta pero NO HACE NADA en el motor (impleméntala o '
                            f'explica en STUB_OK por qué no importa)')

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
        elif m in stub_m:
            stub('mecánica', m, where)
        t = pm.get('t')
        if t is not None and t not in targset:
            problems.append(f'{where}: objetivo «@{t}» sin implementar ({m})')
        for c in flat(pm.get('c')) if pm.get('c') else []:
            if c['m'] not in condset:
                problems.append(f'{where}: condición «{c["m"]}» sin implementar')
            elif c['m'] in stub_c:
                stub('condición', c['m'], where)
        for c in flat(pm.get('tc')) if pm.get('tc') else []:
            if c['m'] not in condset:
                problems.append(f'{where}: condición de objetivo «{c["m"]}» sin implementar')
            elif c['m'] in stub_c:
                stub('condición', c['m'], where)
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
        if m in BONE_MECHS:
            part = a.get('p', a.get('part', a.get('partid', a.get('pid'))))
            if part is None and m in ('setitemmodel', 'setmodeltag', 'setmodeltagvisible'):
                part = a.get('b', a.get('bone'))  # (en brightness, «b» es la luz de bloque)
            if m in ('modelplayerskin', 'modelskin'):
                part = None  # «p» son varios miembros
            em = exact(a.get('em', a.get('exactmatch', a.get('exact', a.get('match', 'true')))))
            if part:
                refs.append((model_id(a.get('m', a.get('mid', a.get('modelid', a.get('model')))), default), part, em))
        if m == 'ikchain':
            for k in ('root', 'r', 'tip', 't', 'pole'):
                if a.get(k):
                    refs.append((model_id(a.get('m', a.get('mid')), default), a[k], True))
        # changeparent{p;c}, submodel{p;sp} (con r=true, sp lleva su etiqueta: «ob_cola») y model{rootbone}
        if m == 'changeparent':
            for k in ('parent', 'p', 'child', 'c'):
                if a.get(k):
                    refs.append((model_id(a.get('m', a.get('mid')), default), a[k], True))
        if m == 'submodel':
            mid = model_id(a.get('m', a.get('mid')), default)
            for k in ('partid', 'p', 'pid', 'part', 'subpartid', 'sp', 'spid', 'subpart'):
                part = a.get(k)
                if not part:
                    continue
                if k in ('subpartid', 'sp', 'spid', 'subpart') and not bone_any(mid, models, part):
                    part = re.sub(r'^[a-z]+(\[[^\]]*\])?_', '', str(part).lower())  # con su etiqueta: «ob_cola» es «cola»
                refs.append((mid, part, True))
        if m == 'model' and a.get('rootbone', a.get('root', a.get('rt'))):
            refs.append((model_id(a.get('m', a.get('mid', a.get('model'))), default), a.get('rootbone', a.get('root', a.get('rt'))), True))
        if m == 'mountmodel':
            part = a.get('p', a.get('pbone', a.get('seat')))
            if part:
                refs.append((model_id(a.get('m', a.get('mid')), default), part, True))
        # Correas: ModelEngine solo las cuelga de huesos de correa (l_ sin cubos)
        if m in ('leash', 'leashself'):
            names = [a.get(k) for k in (('p', 'part', 'partid', 'pid') if m == 'leash' else ('s', 'sid', 'src', 'source', 'sourceid', 'd', 'did', 'dest', 'destid'))]
            for part in [x for x in names if x]:
                mid = model_id(a.get('m', a.get('mid', a.get('modelid'))), default)
                cands = [mid] if mid else models
                found = [b for x in cands if model(x) for b in model(x)['bones'] if bone({'bones': [b]}, part)]
                if not found:
                    problems.append(f'{where}: el hueso de correa «{part}» no está en {", ".join(c for c in cands if c)}')
                elif not any(b.get('beh') == 'leash' for b in found):
                    problems.append(f'{where}: «{part}» no es un hueso de correa (l_ sin cubos): ModelEngine no le cuelga la correa')
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
            elif c['m'] in stub_c:
                stub('condición', c['m'], f'skill {name}')
            act = str(c.get('v', ''))
            if act.lower().startswith(('castinstead', 'orelsecast')) and ' ' in act:
                n = act.split(None, 1)[1].strip()
                if find(n) is None:
                    problems.append(f'skill {name}: {act.split()[0]} a «{n}», que no existe')
    for mob, md in prog['mobs'].items():
        if not md.get('model') and md.get('kind') == 'entity':
            problems.append(f'mob {mob}: sin modelo de ModelEngine (se vería el mob de Minecraft)')
        for i, pm in enumerate(md['mechs']):
            where = f'mob {mob} #{i + 1} ({pm.get("tr") or "onspawn"})'
            tr = pm.get('tr') or 'onspawn'
            if tr not in fired:
                why = trigger_ok(tr, pm, tree, lower)
                if why:
                    warns.append((f'~{tr} no salta aquí, y da igual: {why}', where))
                else:
                    problems.append(f'{where}: el disparador ~{tr} NO SALTA en el motor (impleméntalo en SkillMobs)')
            check_line(pm, where, mob=mob)
    for x in prog.get('missing') or []:
        problems.append(f'falta en el pack: {x}')
    problems += audit_models(bid, warns)
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


def audit_models(bid, warns):
    """Los modelos del jefe: texturas (que existan, tiras de fotogramas bien cortadas, caras dentro de la textura),
    animaciones (pistas a huesos que existen, fotogramas dentro de su duración), IK y etiquetas. Devuelve problemas."""
    probs = []
    prefix = bid + '.'
    for f in sorted(os.listdir(os.path.join(BOSSES, 'models'))):
        if f.startswith(prefix) and f.endswith('.json'):
            probs += check_model(f[:-5], model(f[:-5]), warns)
    return probs


def check_model(mid, m, warns):
    """Lo de audit_models para un modelo."""
    from PIL import Image
    probs = []
    if True:
        nb = len(m['bones'])
        # Texturas
        sizes = []
        for i, t in enumerate(m['tex']):
            rel = t['path'].split(':', 1)[-1]
            path = os.path.join(RES, rel)
            if not os.path.exists(path):
                probs.append(f'modelo {mid}: falta la textura {rel}')
                sizes.append(None)
                continue
            w, h = Image.open(path).size
            sizes.append((w, h))
            if t['frames'] > 1 and h % t['frames']:
                probs.append(f'modelo {mid}: la textura {i} tiene {t["frames"]} fotogramas pero {h} px de alto (no se reparte)')
            if max(w, h // max(1, t['frames'])) > 2048:
                warns.append((f'modelo {mid}: textura {i} muy grande ({w}×{h})', mid))
        out_uv = 0
        for b in m['bones']:
            for q in b['quads']:
                ti = int(q[0])
                if ti < 0:
                    continue
                if ti >= len(m['tex']):
                    probs.append(f'modelo {mid}: hueso {b["id"]}: cara con la textura {ti}, que no existe')
                    continue
                for k in range(4):
                    u, v = q[4 + k * 5 + 3], q[4 + k * 5 + 4]
                    if u < -0.001 or u > 1.001 or v < -0.001 or v > 1.001:
                        out_uv += 1
                        break
        if out_uv:
            probs.append(f'modelo {mid}: {out_uv} caras con la UV fuera de su textura (se verían estiradas o repetidas)')
        # Etiquetas en huesos con cubos (ModelEngine las ignora)
        for b in m['bones']:
            low = b['id'].lower()
            if b['quads'] and b.get('beh') in ('seg', 'segf', 'tl', 'tlf', 'leash', 'item_head', 'item_right', 'item_left'):
                probs.append(f'modelo {mid}: el hueso {b["id"]} tiene la etiqueta {b["beh"]} y cubos (ModelEngine la pide sin cubos)')
            if low.startswith(('tl_', 'seg_', 'tlf_', 'segf_')) and b['quads'] and not b.get('beh'):
                warns.append((f'modelo {mid}: «{b["id"]}» parece un segmento pero tiene cubos: es un hueso normal', mid))
        # Animaciones
        for an, a in m['anims'].items():
            for tr in a['tracks']:
                if not 0 <= tr['b'] < nb:
                    probs.append(f'modelo {mid}: animación {an}: pista de un hueso que no existe ({tr["b"]})')
                    continue
                for key in ('r', 'p', 's'):
                    for kf in tr.get(key) or []:
                        if a['len'] > 0 and kf[0] > a['len'] + 0.05:
                            warns.append((f'modelo {mid}: animación {an}: fotogramas después de su final ({kf[0]} > {a["len"]} s)', mid))
                            break
                        if any(x != x or abs(x) > 1e6 for x in kf[1:4]):
                            probs.append(f'modelo {mid}: animación {an}: un fotograma con un valor imposible')
                            break
        # IK
        for c in m.get('ik') or []:
            if any(not 0 <= b < nb for b in c['chain']) or not 0 <= c['null'] < nb:
                probs.append(f'modelo {mid}: cadena IK con huesos que no existen')
    return probs


def trigger_ok(tr, pm, tree, lower):
    """Por qué no importa que ese disparador no salte, o None si sí importa."""
    if tr == 'onload':
        # ~onLoad es para un mob que vuelve del disco (al cargar su chunk): los de los jefes nunca se guardan
        if not_saved():
            return 'los jefes no se guardan en el mundo (BossEntity.shouldBeSaved = false): nunca vuelven del disco'
        return None
    if tr == 'onattack':
        # Los jefes no pegan con el golpe de Minecraft: un ~onAttack que solo lo cancela no hace falta
        if only_cancels(pm, tree, lower, 0):
            return 'los jefes no pegan con el golpe de Minecraft (solo lo cancelaba)'
        return None
    return None


def not_saved():
    src = open(os.path.join(ROOT, 'src', 'main', 'java', 'net', 'tierrasfantasticas', 'tfclient', 'bosses',
                            'BossEntity.java'), encoding='utf-8').read()
    return re.search(r'boolean shouldBeSaved\(\)\s*\{\s*return false;', src) is not None


def only_cancels(pm, tree, lower, depth):
    if pm['m'] == 'cancelevent':
        return True
    if pm['m'] in ('skill', 'metaskill', 'cast', 's') and depth < 5:
        n = lower.get(str(pm.get('a', {}).get('s', pm.get('a', {}).get('skill', ''))).lower())
        return n is not None and all(only_cancels(x, tree, lower, depth + 1) for x in tree[n]['mechs'])
    return False


def selftest():
    """--prueba: mete errores a propósito en una copia de un jefe y comprueba que el auditor los ve todos."""
    import copy
    sets = engine()
    base = json.load(open(os.path.join(BOSSES, 'esqueleto_torre.json'), encoding='utf-8'))
    if audit('esqueleto_torre', sets, [], base):
        print('prueba: el Esqueleto ya tiene problemas; arréglalos antes')
        return 1
    stub_m, stub_c = stubs()
    fake_stub = next((x for x in sorted(stub_m) if x not in STUB_OK), None)
    boss = base['boss']['mob']
    skill = next(k for k, v in base['tree'].items() if v['mechs'])
    cases = [
        ('mecánica que no existe', lambda p: p['tree'][skill]['mechs'].append({'m': 'mecanicainventada', 'a': {}}),
         'mecanicainventada'),
        ('condición que no existe', lambda p: p['tree'][skill]['conditions'].append({'m': 'condinventada', 'a': {}, 'v': 'true'}),
         'condinventada'),
        ('objetivo que no existe', lambda p: p['tree'][skill]['mechs'].append({'m': 'damage', 'a': {}, 't': 'objinventado'}),
         'objinventado'),
        ('skill que no existe', lambda p: p['tree'][skill]['mechs'].append({'m': 'skill', 'a': {'s': 'skill_inventada'}}),
         'skill_inventada'),
        ('animación que no existe', lambda p: p['mobs'][boss]['mechs'].append({'m': 'state', 'a': {'s': 'anim_inventada'}, 't': 'self'}),
         'anim_inventada'),
        ('hueso que no existe', lambda p: p['mobs'][boss]['mechs'].append({'m': 'partvis', 'a': {'p': 'hueso_inventado'}, 't': 'self'}),
         'hueso_inventado'),
        ('sonido que no existe', lambda p: p['tree'][skill]['mechs'].append({'m': 'sound', 'a': {'s': 'tfclient:bosses.x.inventado'}}),
         'bosses.x.inventado'),
        ('disparador que no salta', lambda p: p['mobs'][boss]['mechs'].append({'m': 'skill', 'a': {'s': skill}, 't': 'self', 'tr': 'onbowhit'}),
         'onbowhit'),
        ('~onAttack que hace algo', lambda p: p['mobs'][boss]['mechs'].append({'m': 'damage', 'a': {'a': '5'}, 'tr': 'onattack'}),
         'onattack'),
        ('modelo que no existe', lambda p: p['mobs'][boss]['mechs'].append({'m': 'model', 'a': {'m': 'modelo_inventado'}, 't': 'self'}),
         'modelo_inventado'),
    ]
    if fake_stub:
        cases.append(('mecánica que no hace nada sin explicar', lambda p: p['tree'][skill]['mechs'].append({'m': fake_stub, 'a': {}}),
                      fake_stub))
    # Una mecánica «de mentira» nueva en el Java, sin su porqué en STUB_OK: tiene que salir como problema
    STUB_OK_BACKUP = STUB_OK.pop('bindhitbox', None)
    cases.append(('mecánica de mentira sin su porqué', lambda p: p['tree'][skill]['mechs'].append({'m': 'bindhitbox', 'a': {}}),
                  'bindhitbox'))
    cases.append(('hueso de una mecánica de ModelEngine que no existe',
                  lambda p: p['mobs'][boss]['mechs'].append({'m': 'enchant', 'a': {'p': 'hueso_que_no_hay'}, 't': 'self'}),
                  'hueso_que_no_hay'))
    cases.append(('changeparent a un hueso que no existe',
                  lambda p: p['mobs'][boss]['mechs'].append({'m': 'changeparent', 'a': {'p': 'head', 'c': 'hueso_movido_inventado'}, 't': 'self'}),
                  'hueso_movido_inventado'))
    cases.append(('correa en un hueso que no es de correa',
                  lambda p: p['mobs'][boss]['mechs'].append({'m': 'leashself', 'a': {'s': 'head', 'd': 'jaw'}, 't': 'self'}),
                  'no es un hueso de correa'))
    bad = 0
    for name, hurt, needle in cases:
        prog = copy.deepcopy(base)
        hurt(prog)
        probs = audit('esqueleto_torre', sets, [], prog)
        ok = any(needle in x for x in probs)
        bad += not ok
        print(f'  {"OK " if ok else "NO "} {name}' + ('' if ok else f'  (no lo vio: {needle})'))
    if STUB_OK_BACKUP is not None:
        STUB_OK['bindhitbox'] = STUB_OK_BACKUP
    # Modelos estropeados: UV fuera de la textura, pista de un hueso que no existe, textura que falta, IK rota
    import copy as _copy
    real = model('esqueleto_torre.tower_skeleton')
    for name, hurt, needle in [
        ('cara con la UV fuera de su textura', lambda mm: next(b for b in mm['bones'] if b['quads'])['quads'][0].__setitem__(7, 1.7),
         'UV fuera'),
        ('pista de animación de un hueso que no existe',
         lambda mm: next(iter(mm['anims'].values()))['tracks'].append({'b': 99999, 'r': [[0, 0, 0, 0, 0]]}), 'no existe (99999)'),
        ('textura que falta', lambda mm: mm['tex'].append({'path': 'tfclient:textures/bosses/no_hay.png', 'frames': 1, 'ft': 1,
                                                          'e': False, 'body': False}), 'falta la textura'),
        ('cadena IK rota', lambda mm: mm.setdefault('ik', []).append({'null': 99999, 'chain': [0, 1], 'lock': False, 'pole': -1}),
         'cadena IK'),
    ]:
        mm = _copy.deepcopy(real)
        hurt(mm)
        ok = any(needle in x for x in check_model('prueba', mm, []))
        bad += not ok
        cases.append(None)
        print(f'  {"OK " if ok else "NO "} {name}')
    print(f'prueba del auditor: {len(cases) - bad} de {len(cases)} errores encontrados')
    return bad


def coverage():
    """--cobertura: el catálogo de mythic_ref.py contra el motor."""
    sys.path.insert(0, HERE)
    import mythic_ref
    mechs, conds, targs = engine()
    stub_m, stub_c = stubs()
    have = {'mecánicas': (mechs, stub_m), 'condiciones': (conds, stub_c), 'objetivos': (targs, set()),
            'disparadores': (triggers(), set())}
    for kind, entries in mythic_ref.ALL.items():
        impl, dead = have[kind]
        ok, fake, miss = [], [], []
        for name, aliases, hot in entries:
            names = [name] + aliases
            label = ('*' if hot else '') + name
            if any(n in impl for n in names):
                if all(n in dead for n in names if n in impl):
                    fake.append(label)
                else:
                    ok.append(label)
            else:
                miss.append(label)
        miss.sort(key=lambda x: (not x.startswith('*'), x))
        print(f'{kind:13s} {len(ok)}/{len(entries)} hechas; sin efecto: {len(fake)}; faltan: {len(miss)}')
        if fake:
            print('   sin efecto:', ', '.join(fake))
        if miss:
            print('   faltan    :', ', '.join(miss))
    # Etiquetas de hueso: las que conoce el conversor (vfx_bb.bone_tag)
    sys.path.insert(0, os.path.normpath(os.path.join(HERE, '..')))
    import vfx_bb
    known = set()
    for name, aliases, hot in mythic_ref.TAGS:
        probe = name.replace('[]', '[type=head]' if name == 'limb[]' else '[a=1]') + '_x'
        beh, _p, _id, why = vfx_bb.bone_tag(probe, has_cubes=False)
        if beh and not why:
            known.add(name)
    tags = [('*' if hot else '') + name for name, _a, hot in mythic_ref.TAGS]
    miss = [t for t in tags if t.lstrip('*') not in known]
    print(f'{"etiquetas":13s} {len(tags) - len(miss)}/{len(tags)} de hueso que entiende el conversor' + (f'; faltan: {", ".join(miss)}' if miss else ''))
    print('(* = la usan mucho los packs de jefes. Las que faltan las marca el auditor normal en cuanto un pack las use.)')


def main(ids=None, show_warnings=True):
    sets = engine()
    if not ids:
        ids = [c['id'] for c in json.load(open(os.path.join(BOSSES, 'catalog.json'), encoding='utf-8'))]
    total = 0
    for bid in ids:
        warns = []
        probs = audit(bid, sets, warns)
        total += len(probs)
        # Los avisos, agrupados (la misma explicación una vez, con cuántas veces sale)
        seen = {}
        for w, _where in warns:
            seen[w] = seen.get(w, 0) + 1
        print(f'auditoría {bid:16s} problemas: {len(probs)}   avisos: {len(seen)}')
        for p in probs:
            print('   -', p)
        if show_warnings:
            for w, n in seen.items():
                print('   ·', w + (f' (×{n})' if n > 1 else ''))
    return total


if __name__ == '__main__':
    if '--cobertura' in sys.argv:
        coverage()
        sys.exit(0)
    if '--prueba' in sys.argv:
        sys.exit(1 if selftest() else 0)
    sys.exit(1 if main([a for a in sys.argv[1:] if not a.startswith('-')], '--sin-avisos' not in sys.argv) else 0)
