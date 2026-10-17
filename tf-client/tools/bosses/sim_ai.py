"""Simula la IA de un jefe sin abrir el juego: ejecuta su programa (bosses/<jefe>.json) con las mismas reglas que el
motor de Java (SkillRuntime/SkillConds/SkillMobs) contra un jugador que se mueve a su alrededor y le pega, y cuenta qué
ataques salen. Sirve para ver si el pack se queda bloqueado (una variable que no caduca, un cooldown que se pone sin
salir, un gcd que no es del jefe...) y si usa todos sus ataques.

    python3 tools/bosses/sim_ai.py [jefe ...] [--min 5] [--seed 1] [--dif normal] [--smart 0..1] [-v] [--antes]
                                  [--sin-alcance]

--dif P: una dificultad de /tf boss (facil, normal, dificil, pesadilla, demencial): su inteligencia, su velocidad
(el tempo de los ataques) y su ritmo (cooldowns). --smart N: solo la inteligencia del cerebro táctico
(skills/SkillBrain.java; 0 = el pack tal cual, por defecto).
El jugador simulado, además de moverse y pegar, a veces se cura (come), se cubre con escudo o dispara con arco, y se
mide si el jefe le castiga (cuántas curas reciben un ataque que acorta distancia o a distancia en el segundo y medio
siguiente), cuánto repite ataques seguidos y (1.3.83) cuántos golpes lanza fuera de su alcance («al aire») o, si van
por delante, sin mirarle. --antes: el motor de la 1.3.75; --sin-alcance: el cerebro de la 1.3.82, sin mirar hasta
dónde llega cada golpe ni girarse antes (para comparar).

Simplificaciones: el jugador es un punto; los tótems y proyectiles no se simulan (salvo los que detectan dónde está
el jugador para morder, en la Hidra); las auras solo cuentan su nombre y su duración; las animaciones solo se apuntan.
Si se cambia la semántica en Java, se cambia aquí igual.
"""
import json
import math
import os
import random
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient', 'bosses')
sys.path.insert(0, HERE)
import mythic_ai as AI  # noqa: E402

# Estados que no son ataques (reposo, pasivos, transiciones)
NOT_ATTACKS = {'passive', 'passive_tongue', 'passive_tentacle', 'idle', 'walk', 'sleep', 'land', 'in_air', 'hurt',
               'spawn', 'shield_charge_run', 'turn_left', 'turn_right', 'shield_hit', 'stun_hit', 'statue', 'bank_left',
               'bank_right'}


def num(v, d=0.0):
    try:
        return float(str(v).strip())
    except (TypeError, ValueError):
        m = re.match(r'<random\.(?:float\.)?(-?[\d.]+)to(-?[\d.]+)>', str(v or ''))
        if m:
            return random.uniform(float(m.group(1)), float(m.group(2)))
        return d


def arg(a, d, *keys):
    for k in keys:
        if k in a:
            return a[k]
    return d


def in_range(v, spec):
    spec = str(spec).strip()
    try:
        if 'to' in spec:
            lo, hi = spec.split('to', 1)
            return float(lo) <= v <= float(hi)
        for op in ('<=', '>=', '<', '>', '='):
            if spec.startswith(op):
                x = float(spec[len(op):])
                return {'<=': v <= x, '>=': v >= x, '<': v < x, '>': v > x, '=': abs(v - x) < 1e-9}[op]
        return abs(v - float(spec)) < 1e-9
    except ValueError:
        return True


def crunch(expr, x=0.0):
    """SkillVars.math (variablemath): la cuenta de Crunch con «x»; None si no se entiende."""
    s = str(expr).replace(' ', '').lower()
    pos = [0]

    def eat(op):
        if s.startswith(op, pos[0]):
            pos[0] += len(op)
            return True
        return False

    def or_():
        v = and_()
        while pos[0] < len(s) and s[pos[0]] == '|':
            pos[0] += 2 if s.startswith('||', pos[0]) else 1
            r = and_()
            v = 1.0 if v != 0 or r != 0 else 0.0
        return v

    def and_():
        v = cmp_()
        while pos[0] < len(s) and s[pos[0]] == '&':
            pos[0] += 2 if s.startswith('&&', pos[0]) else 1
            r = cmp_()
            v = 1.0 if v != 0 and r != 0 else 0.0
        return v

    def cmp_():
        v = add()
        for op, f in (('==', lambda a, b: a == b), ('=', lambda a, b: a == b), ('!=', lambda a, b: a != b),
                      ('<=', lambda a, b: a <= b), ('>=', lambda a, b: a >= b), ('<', lambda a, b: a < b),
                      ('>', lambda a, b: a > b)):
            if eat(op):
                return 1.0 if f(v, add()) else 0.0
        return v

    def add():
        v = mul()
        while True:
            if eat('+'):
                v += mul()
            elif eat('-'):
                v -= mul()
            else:
                return v

    def mul():
        v = unary()
        while True:
            if eat('*'):
                v *= unary()
            elif eat('/'):
                d = unary()
                v = 0.0 if d == 0 else v / d
            elif eat('%'):
                d = unary()
                v = 0.0 if d == 0 else math.fmod(v, d)
            else:
                return v

    def unary():
        if eat('-'):
            return -unary()
        if eat('+'):
            return unary()
        if pos[0] < len(s) and s[pos[0]] == '!' and not s.startswith('!=', pos[0]):
            pos[0] += 1
            return 1.0 if unary() == 0 else 0.0
        v = atom()
        if eat('^'):
            return math.pow(v, unary())
        return v

    def atom():
        if eat('('):
            v = or_()
            eat(')')
            return v
        m = re.match(r'(\d+\.?\d*|\.\d+)(e-?\d+)?', s[pos[0]:])
        if m:
            pos[0] += m.end()
            return float(m.group(0))
        m = re.match(r'[a-z]+', s[pos[0]:])
        if not m:
            raise ValueError(s)
        name = m.group(0)
        pos[0] += m.end()
        if not eat('('):
            return {'x': x, 'pi': math.pi, 'e': math.e, 'true': 1.0, 'false': 0.0}[name]
        args = []
        if not eat(')'):
            args.append(or_())
            while eat(','):
                args.append(or_())
            eat(')')
        a = args[0] if args else 0.0
        fns = {'sin': math.sin, 'cos': math.cos, 'tan': math.tan, 'asin': math.asin, 'acos': math.acos, 'atan': math.atan,
               'sinh': math.sinh, 'cosh': math.cosh, 'tanh': math.tanh, 'sqrt': math.sqrt, 'cbrt': lambda v: math.copysign(abs(v) ** (1 / 3), v),
               'ln': math.log, 'log': math.log10, 'abs': abs, 'round': lambda v: float(math.floor(v + 0.5)), 'ceil': math.ceil,
               'floor': math.floor, 'sign': lambda v: float((v > 0) - (v < 0)), 'signum': lambda v: float((v > 0) - (v < 0))}
        if name in fns:
            return float(fns[name](a))
        if name in ('rand', 'random'):
            return random.random() * (a if args else 1.0)
        if name == 'min':
            return min(args) if args else 0.0
        if name == 'max':
            return max(args) if args else 0.0
        raise ValueError(name)

    try:
        v = or_()
        return v if pos[0] == len(s) else None
    except (ValueError, KeyError, ZeroDivisionError, OverflowError):
        return None


def wrap(a):
    a = (a + 180.0) % 360.0
    return a - 180.0


def yaw_of(dx, dz):
    return math.degrees(math.atan2(-dx, dz))


class Boss:
    def __init__(self, prog, mob, players=1):
        self.prog = prog
        self.mob = prog['mobs'][mob]
        self.name = mob
        self.x = self.z = 0.0
        self.body = 0.0
        self.look = 0.0
        self.maxhp = prog['boss']['health'] * 1.0
        self.hp = self.maxhp
        self.vars = {}
        self.var_until = {}
        self.cooldowns = {}
        self.gcd = 0
        self.auras = {}
        self.ai = True
        self.locked = False
        self.invulnerable = False  # setinvulnerable{v}
        opts = self.mob.get('options') or {}
        self.type_speed = self.mob.get('speed', 0.25)
        self.base_speed = float(opts.get('MovementSpeed', self.type_speed))
        self.speed_mul = 1.0
        self.speed_set = False
        # Su IA de MythicMobs sobre la de su tipo (SkillAI / mythic_ai): persigue, pega con el golpe de Minecraft y a
        # quién busca (el jugador de la prueba: con «players», o con «hurtbytarget» en cuanto le pega)
        self.goals = AI.goals_of(self.mob)
        self.ai_targets = AI.targets_of(self.mob)
        self.chases = AI.chases(self.goals)
        self.melee = AI.melee(self.goals)
        self.melee_wait = 0
        self.scores = {}
        if AI.flag(self.mob, 'NoAI'):
            self.ai = False
        self.target = False
        self.combat = False
        self.fired = {}
        self.timeline = []

    def speed(self):
        return (self.type_speed if self.speed_set else self.base_speed) * self.speed_mul

    def var(self, name):
        return self.vars.get(name.lower().split('.', 1)[-1] if name.lower().startswith(('caster.', 'skill.')) else name.lower())


OLD = False  # --antes: como el motor de la 1.3.75 (para comparar)
NO_REACH = False  # --sin-alcance: el cerebro sin mirar hasta dónde llega cada golpe ni girarse antes (1.3.82)

SMART = 0.0  # --smart: inteligencia del cerebro (como Difficulty.smart)
SPEED = 1.0  # --dif: velocidad de la dificultad (Difficulty.speed)
RHYTHM = 1.0  # --dif: ritmo de la dificultad (Difficulty.rhythm)
# Como Difficulty.PRESETS: (velocidad, ritmo, inteligencia)
PRESETS = {'facil': (0.9, 0.8, 0.3), 'normal': (1.0, 1.0, 0.55), 'dificil': (1.08, 1.15, 0.75),
           'pesadilla': (1.16, 1.3, 0.9), 'demencial': (1.25, 1.5, 1.0)}


def tempo():
    """BossEntity.tempo (SkillRuntime.tempo): los retardos y el gcd de sus ataques se dividen por esto."""
    return max(0.5, min(2.0, SPEED * (1 + 0.12 * SMART)))

OFFENSE = {'damage', 'basedamage', 'hit', 'percentdamage', 'throw', 'projectile', 'missile', 'shoot', 'totem', 'summon',
           'lunge', 'propel', 'leap', 'mountmodel', 'stun', 'ignite', 'pull'}
AREA = {'pir', 'playersinradius', 'eir', 'entitiesinradius', 'livinginradius', 'leir', 'livingentitiesinradius', 'ring',
        'playersinring', 'rlnc', 'randomlocationsnearcaster', 'air'}


class Attack:
    def __init__(self, name, line, pool):
        self.name, self.line, self.pool = name, line, pool
        self.min, self.max = 0.0, 40.0
        self.gap = self.ranged = self.aoe = self.grab = self.rear = False
        self.uses = self.hits = self.miss = 0
        self.active_end = 0
        self.gcd = 0
        self.no_cancel = False
        # SkillBrain.reachOf: hasta dónde llega su golpe (bloques); far = sin tope; frontal = va por delante; fov = el
        # pack ya mira hacia qué lado está el jugador
        self.reach_v = float('inf')
        self.far = True
        self.frontal = False
        self.fov = False

    def reach(self, sim=None):
        return float('inf') if self.far else self.reach_v

    def cap(self, sim=None):
        """El alcance con el que decide el cerebro (--sin-alcance: como antes de la 1.3.83, sin mirarlo)."""
        return float('inf') if NO_REACH else self.reach()

    def sweet(self, sim=None):
        hi = min(self.max, 24)
        if not self.far and not NO_REACH:
            hi = min(hi, max(self.min + 0.5, self.reach_v * 0.8))
        return self.min + max(0.0, hi - self.min) * 0.35


# SkillBrain.reachOf: hasta dónde llega de verdad cada ataque (las mismas tablas)
HITS = {'damage', 'basedamage', 'hit', 'percentdamage', 'throw', 'ignite', 'stun', 'mountmodel', 'totem', 'potion', 'pull',
        'velocity'}
REACHES_FAR = {'projectile', 'missile', 'shoot', 'chainmissile', 'cmi', 'lunge', 'propel', 'leap', 'teleport', 'tp',
               'teleportto', 'tpto', 'beam', 'raytrace', 'raytraceto', 'jump', 'summon'}
CALLS = {'skill', 'metaskill', 'randomskill', 'cast', 'skillsequence', 'aura', 'buff', 'debuff'}
AROUND = {'pir', 'playersinradius', 'eir', 'entitiesinradius', 'livinginradius', 'leir', 'livingentitiesinradius', 'mir',
          'mobsinradius', 'rlnc', 'randomlocationsnearcaster', 'iir', 'bir', 'sphere'}
CONE = {'livingincone', 'lic', 'entitiesincone', 'eic', 'playersincone'}
RING = {'ring', 'eirr', 'entitiesinringradius', 'playersinring', 'pirr'}
SELF = {'self', 'caster', 'selflocation', 'casterlocation', 'origin'}


def _ta_num(ta, d, *keys):
    for k in keys:
        if k in ta:
            return num(ta[k], d)
    return d


def bone_index(model, name):
    """VfxModel.bone: el id tal cual, en minúsculas o sin su prefijo de comportamiento (ef_suelo → «suelo»)."""
    ids = [b['id'] for b in model['bones']]
    if name in ids:
        return ids.index(name)
    low = name.lower()
    for i, b in enumerate(ids):
        if b.lower() == low:
            return i
    tail = '_' + low
    for i, b in enumerate(ids):
        if b.lower().endswith(tail) and b.find('_') == len(b) - len(tail):
            return i
    return -1


def bone_hit(model, scale, at, dur, bone, hr, states, round_):
    """SkillBrain.boneHit: lo que llega un golpe en un hueso con la animación que tiene puesta en ese instante (hacia
    delante, o en redondo si el pack ya elige por el lado). Solo la cadena de padres del hueso (es lo mismo que la pose
    entera de VfxPose.compute)."""
    sys.path.insert(0, os.path.join(HERE, '..'))
    import vfx_bb as VB
    b = bone_index(model, bone)
    if b < 0:
        return -1
    anim, start, speed = None, 0, 1.0
    for st, name, sp in states:
        an = model['anims'].get(name)
        if an is not None and st <= at and (anim is None or st >= start):
            anim, start, speed = an, st, sp
    chain = []
    i = b
    while i >= 0:
        chain.append(i)
        i = model['bones'][i]['parent']
    chain.reverse()
    on = set(chain)
    front, around = -1.0, 0.0
    for k in range(dur + 1):
        t = (at - start + k) / 20.0 * speed
        extra = {}
        tt = VB.anim_time(anim, t) if anim else None
        if anim and tt is not None:
            for tr in anim['tracks']:
                if tr['b'] not in on:
                    continue
                e = extra.setdefault(tr['b'], [[0, 0, 0], [0, 0, 0], [1, 1, 1]])
                if 'r' in tr:
                    e[0] = VB.sample(tr['r'], tt)
                if 'p' in tr:
                    e[1] = VB.sample(tr['p'], tt)
                if 's' in tr:
                    e[2] = VB.sample(tr['s'], tt)
        m = None
        for i2 in chain:
            bn = model['bones'][i2]
            r, p, sc = extra.get(i2, [[0, 0, 0], [0, 0, 0], [1, 1, 1]])
            par = bn['parent']
            pp = model['bones'][par]['pivot'] if par >= 0 else [0, 0, 0]
            tr3 = [bn['pivot'][q] - pp[q] + p[q] for q in range(3)]
            rm = VB.rot_matrix([bn['rot'][q] + r[q] for q in range(3)])
            local = [[rm[r2][c] * sc[c] for c in range(3)] + [tr3[r2]] for r2 in range(3)] + [[0, 0, 0, 1]]
            m = local if m is None else VB.mat4(m, local)
        f, lat = -m[2][3] * scale / 16.0, m[0][3] * scale / 16.0  # (el modelo mira a -Z)
        around = max(around, math.hypot(f, lat) + hr)
        if abs(lat) < hr:
            front = max(front, f + math.sqrt(hr * hr - lat * lat))
    return around if round_ or front < 0 else front


def flat_spot(tgt):
    t, ta = tgt['t'], tgt.get('ta') or {}
    if t in SELF:
        return 0.0
    if t in AROUND or t in CONE:
        return _ta_num(ta, 5, 'r', 'radius')
    if t in RING:
        return _ta_num(ta, _ta_num(ta, 5, 'r', 'radius'), 'max', 'maxradius')
    if t == 'forward':
        return _ta_num(ta, 5, 'f', 'forward')
    return -1.0


def reach_hits(tree, name, meta, at, inherited, model, start, states, bones, reach, far, seen, depth):
    """SkillBrain.hits: recorre el ataque en el tiempo (sus delay y lo que llama)."""
    if meta is None or depth > 4 or name.lower() in seen:
        return
    seen.add(name.lower())
    t = start
    for pm in meta['mechs']:
        m, a = pm['m'], pm.get('a', {})
        if m == 'delay':
            t += int(max(0, num(arg(a, 0, '_', 'ticks', 't', 'd'), 0)))
            continue
        when = t + int(num(a.get('delay'), 0))
        tgt = pm if pm.get('t') is not None else inherited
        on_self = tgt is not None and tgt['t'] in ('self', 'caster')
        if m in ('state', 'animation') and (pm.get('t') is None or on_self) and model is not None \
                and str(arg(a, 'false', 'remove', 'r')).lower() != 'true':
            st = arg(a, None, 'state', 's', 'animation', 'a')
            if st is not None:
                st = str(st)
                if '<' in st:
                    pre = st[:st.index('<')]
                    st = next((n for n in sorted(model['anims']) if n.startswith(pre)), st)
                states.append((when, st, num(arg(a, 1, 'speed', 'sp'), 1)))
            continue
        if m in REACHES_FAR or m == 'setspeed' and num(arg(a, 1, 'speed', 's'), 1) >= 2 or m == 'velocity' and on_self:
            r = flat_spot(tgt) if m == 'summon' and tgt is not None and not on_self else -1
            if r >= 0:
                reach[0] = max(reach[0], r + num(arg(a, 0, 'radius', 'r'), 0) + 2)
            else:
                far[0] = True
            continue
        if m in HITS and not (on_self and m != 'totem'):
            hr = num(arg(a, 1.25, 'hr', 'horizontalradius'), 1.25) if m == 'totem' else 0
            if tgt is not None and tgt['t'] == 'modelpart' and model is not None:
                ta = tgt.get('ta') or {}
                pid = ta.get('pid', ta.get('p'))
                dur = int(min(20, num(arg(a, 10, 'maxduration', 'md'), 10))) if m == 'totem' else 0
                if pid is None:
                    far[0] = True
                else:
                    bones.append((when, dur, str(pid), max(hr, 0.5)))
                if not on_self:
                    at.frontal = True
            else:
                r = -1 if tgt is None else flat_spot(tgt)
                if r < 0:
                    far[0] = True
                else:
                    reach[0] = max(reach[0], r + hr)
                    if tgt['t'] in CONE or tgt['t'] == 'forward':
                        at.frontal = True
            continue
        if m in CALLS:
            for sn, sub in called(tree, pm):
                reach_hits(tree, sn, sub, at, tgt, model, when, states, bones, reach, far, seen, depth + 1)


def reach_of(tree, name, meta, at, model, scale=1.0):
    """SkillBrain.reachOf."""
    for c in meta.get('target_conditions', []) + meta.get('conditions', []) + (at.line.get('c') or []):
        if c['m'] in ('fieldofview', 'fov', 'infieldofview'):
            if abs(wrap(num(arg(c.get('a', {}), 0, 'rotation', 'r'), 0))) >= 45:
                at.fov = True
    states, bones, reach, far = [], [], [0.0], [False]
    reach_hits(tree, name, meta, at, at.line if at.line.get('t') is not None else None, model, 0, states, bones, reach,
               far, set(), 0)
    anyone = reach[0] > 0
    for when, dur, bone, hr in bones:
        r = bone_hit(model, scale, when, dur, bone, hr, states, at.fov or at.rear)
        if r < 0:
            far[0] = True
        else:
            reach[0] = max(reach[0], r)
            anyone = True
    at.far = far[0] or not anyone
    at.reach_v = float('inf') if at.far else reach[0]
    if at.far:
        at.frontal = False
    return at


def called(tree, pm):
    out = []
    a = pm.get('a', {})
    for k in ('s', 'skill', 'skills', 'oh', 'onhit', 'ot', 'ontick', 'oe', 'onend', 'os', 'onstart'):
        v = a.get(k)
        if v is None:
            continue
        if k in ('s', 'skill', 'skills') and pm['m'] not in ('skill', 'metaskill', 'randomskill', 'cast', 'skillsequence'):
            continue
        for part in str(v).split(','):
            sub = tree.get(part.strip().split(' ')[0].lower())
            if sub is not None:
                out.append((part.strip().split(' ')[0], sub))
    return out


def offensive(tree, name, meta, seen, depth):
    if meta is None or depth > 4 or name.lower() in seen:
        return False
    seen.add(name.lower())
    for pm in meta['mechs']:
        if pm['m'] in OFFENSE:
            return True
        if pm['m'] in ('potion', 'velocity') and pm.get('t') not in (None, 'self'):
            return True
        if pm['m'] == 'setspeed' and num(arg(pm.get('a', {}), 1, 'speed', 's'), 1) >= 2:
            return True
        for n, sub in called(tree, pm):
            if offensive(tree, n, sub, seen, depth + 1):
                return True
    return False


def spec(s):
    s = str(s).strip()
    try:
        if 'to' in s:
            lo, hi = s.split('to', 1)
            return float(lo), float(hi)
        if s.startswith('<='):
            return 0.0, float(s[2:])
        if s.startswith('<'):
            return 0.0, float(s[1:])
        if s.startswith('>='):
            return float(s[2:]), 40.0
        if s.startswith('>'):
            return float(s[1:]), 40.0
    except ValueError:
        pass
    return None


def analyze(tree, name, meta, at):
    for c in meta.get('target_conditions', []) + meta.get('conditions', []):
        want = str(c.get('v', 'true')).lower() != 'false'
        a = c.get('a', {})
        if c['m'] == 'distance':
            r = spec(arg(a, '', 'distance', 'd'))
            if r and want:
                at.min, at.max = max(at.min, r[0]), min(at.max, r[1])
        elif c['m'] == 'targetwithin':
            d = num(arg(a, 16, 'distance', 'd'), 16)
            if want:
                at.max = min(at.max, d)
            else:
                at.min = max(at.min, d)
        elif c['m'] == 'targetnotwithin':
            d = num(arg(a, 16, 'distance', 'd'), 16)
            if want:
                at.min = max(at.min, d)
            else:
                at.max = min(at.max, d)
    for c in meta.get('target_conditions', []):
        if c['m'] in ('fieldofview', 'fov', 'infieldofview'):
            r = num(arg(c.get('a', {}), 0, 'rotation', 'r'), 0) % 360
            if 110 <= r <= 250:
                at.rear = True
    n = name.lower()
    if 'tail' in n or 'leap_turn' in n:
        at.rear = True
    if any(k in n for k in ('grab', 'enclose', 'absorb', 'cage')):
        at.grab = True

    def radius(pm):
        return num(arg(pm.get('ta') or {}, 5, 'r', 'radius'), 5)

    def tags(nm, mt, seen, depth, area):
        if mt is None or depth > 4 or nm.lower() in seen:
            return
        seen.add(nm.lower())
        for pm in mt['mechs']:
            t = pm.get('t')
            m = pm['m']
            here = area if t is None else (t in AREA and 4 <= radius(pm) <= 16)
            if m in ('lunge', 'propel', 'leap'):
                at.gap = True
            elif m in ('projectile', 'missile', 'shoot'):
                if 'oe' in pm.get('a', {}) or 'onend' in pm.get('a', {}):
                    at.gap = True
                else:
                    at.ranged = True
            elif m in ('mountmodel', 'stun', 'lock'):
                at.grab = True
            elif m == 'setspeed':
                if num(arg(pm.get('a', {}), 1, 'speed', 's'), 1) >= 2:
                    at.gap = True
            elif m == 'summon':
                at.aoe = at.aoe or depth > 0 or here
            elif m in ('damage', 'basedamage', 'hit', 'throw', 'potion', 'percentdamage'):
                at.aoe = at.aoe or here
            if m == 'totem' and num(arg(pm.get('a', {}), 1.25, 'hr', 'horizontalradius'), 1.25) >= 3:
                at.aoe = True
            call_area = here if m in ('skill', 'metaskill', 'randomskill') else False
            for sn, sub in called(tree, pm):
                tags(sn, sub, seen, depth + 1, call_area)
    tags(name, meta, set(), 0, False)
    at.active_end = active_end(tree, name, meta, 0, set(), 0)
    for pm in meta['mechs']:
        a = pm.get('a', {})
        if pm['m'] in ('gcd', 'setgcd', 'globalcooldown'):
            at.gcd = max(at.gcd, int(num(arg(a, 20, 'ticks', 't', 'duration', 'd', '_'), 20)))
        if pm['m'] in ('setvar', 'setvariable') and arg(a, None, 'duration', 'd') is None:
            at.no_cancel = True
    if at.grab:
        at.no_cancel = True
    if not at.ranged and not at.gap and at.min >= 6 and at.max >= 12:
        at.ranged = True
    return at


ACTIVE = {'damage', 'basedamage', 'hit', 'percentdamage', 'throw', 'projectile', 'missile', 'shoot', 'totem', 'summon',
          'lunge', 'propel', 'leap', 'pull', 'mountmodel', 'potion', 'velocity'}


def active_end(tree, name, meta, start, seen, depth):
    """SkillBrain.activeEnd: tick en que acaba lo último que hace el ataque (lo de después es recuperación)."""
    if meta is None or depth > 4 or name.lower() in seen:
        return start
    seen = seen | {name.lower()}
    t = end = start
    for pm in meta['mechs']:
        a = pm.get('a', {})
        if pm['m'] == 'delay':
            t += int(max(0, num(arg(a, 0, '_', 'ticks', 't', 'd'), 0)))
            continue
        at = t + int(num(a.get('delay'), 0))
        rep_ = int(num(arg(a, 0, 'repeat', 'r_'), 0))
        every = int(max(1, num(arg(a, 1, 'repeatinterval', 'repeati', 'ri'), 1)))
        last = at + rep_ * every
        if pm['m'] in ACTIVE and not (pm.get('t') == 'self' and pm['m'] in ('potion', 'velocity')):
            extra = int(num(arg(a, 2, 'maxduration', 'md'), 2)) if pm['m'] == 'totem' else \
                10 if pm['m'] in ('projectile', 'missile') else 2
            end = max(end, last + extra)
        if pm['m'] in ('skill', 'metaskill', 'randomskill', 'skillsequence'):
            for sn, sub in called(tree, pm):
                end = max(end, active_end(tree, sn, sub, last, seen, depth + 1))
    return end


class Brain:
    """Lo mismo que SkillBrain.java (si se cambia allí, aquí igual)."""

    def __init__(self, sim):
        self.sim = sim
        self.attacks = []
        self.handled = set()
        tree = sim.tree
        for i, pm in enumerate(sim.b.mob['mechs']):
            if pm.get('tr') != 'ontimer' or num(pm.get('trv'), 20) < 10:
                continue
            anyone = False
            if pm['m'] == 'randomskill':
                for part in str(arg(pm.get('a', {}), '', 'skills', 's')).split(','):
                    n = part.strip().split(' ')[0]
                    meta = tree.get(n.lower())
                    if meta is not None and offensive(tree, n, meta, set(), 0):
                        self.attacks.append(reach_of(tree, n, meta, analyze(tree, n, meta, Attack(n, pm, True)), sim.model, sim.model_scale))
                        anyone = True
            elif pm['m'] in ('skill', 'metaskill', 'cast'):
                if pm.get('t') in (None, 'self', 'caster'):
                    continue
                n = str(arg(pm.get('a', {}), '', 's', 'skill', 'skills')).split(',')[0].strip()
                meta = tree.get(n.lower())
                if meta is not None and offensive(tree, n, meta, set(), 0):
                    self.attacks.append(reach_of(tree, n, meta, analyze(tree, n, meta, Attack(n, pm, False)), sim.model, sim.model_scale))
                    anyone = True
            if anyone:
                self.handled.add(id(pm))
        self.recent = []
        self.next = 0
        self.hesitate_until = -1
        self.hesitated = False
        self.urgent = False
        self.waiting = False
        self.current = None
        self.current_at = 0
        self.current_hit = False
        self.current_cast = 0
        self.chain_tried = False
        self.chains = 0
        self.planned = None
        self.planned_until = 0
        self.plan_check = 0
        self.can = []
        self.preferred = 3.0
        self.strafe = 1
        self.strafe_until = 0
        self.reach_wait = -1  # SkillBrain.Brain.reachWait
        self.turns = 0

    def interval(self):
        b = self.sim.b
        t = 20 - 16 * SMART
        hp = b.hp / b.maxhp
        if hp < 0.5:
            t *= 0.85
        if hp < 0.25:
            t *= 0.8
        return max(3, round(t / max(0.5, self.sim.rhythm())))

    def situation(self):
        s, b = self.sim, self.sim.b
        dist = s.dist()
        to = yaw_of(s.px - b.x, s.pz - b.z)
        return {'dist': dist, 'behind': abs(wrap(to - b.body)) > 110, 'healing': s.t < s.heal_until,
                'blocking': s.t < s.block_until, 'shooting': s.t < s.aim_until or s.t - s.last_shot < 60,
                'far': dist > 10, 'crowd': 1, 'low': False}

    def usable(self):
        s = self.sim
        out = []
        for a in self.attacks:
            pm = a.line
            if pm.get('hp') and not s.hp_ok(pm['hp']):
                continue
            ctx = {'targets': None, 'vars': {}, 'depth': 0}
            if any(s.cond(c, 'boss', ctx) == c.get('not', False) for c in pm.get('c') or []):
                continue
            targets = s.targets(pm, ctx)
            if not targets:
                continue
            if not s.can_run(a.name, dict(ctx, targets=targets)):
                continue
            # SkillBrain.usable: su golpe no le llega (espera hasta 3 s a que el jugador entre en su alcance)
            if s.dist() > a.cap(s) + 0.9:
                if self.reach_wait < 0:
                    self.reach_wait = s.t
                if s.t - self.reach_wait < 60:
                    continue
            out.append(a)
        return out

    def score(self, a, st):
        sc = 1.0
        span = max(2.0, min(a.max, 24) - a.min)
        sc += SMART * 1.5 * (1 - min(1.0, abs(st['dist'] - a.sweet(self.sim)) / span))
        if st['healing']:
            sc += SMART * ((4 if a.gap else 0) + (3 if a.ranged else 0))
        if st['shooting']:
            sc += SMART * ((2.2 if a.gap else 0) + (1 if a.ranged else 0))
        if st['far']:
            sc += SMART * ((1.4 if a.gap else 0) + (1.4 if a.ranged else 0))
        if st['blocking']:
            sc += SMART * ((3 if a.grab else 0) + (1.2 if a.aoe else 0))
        if st['behind']:
            sc += SMART * (3 if a.rear else 0)
        if st['crowd'] >= 2:
            sc += SMART * ((1 + st['crowd'] * 0.7) if a.aoe else 0)
        if st['low']:
            sc += SMART * (1 if a.gap else 0.4)
        for i, r in enumerate(self.recent):
            if r == a.name:
                sc -= SMART * (2.2 if i == 0 else 0.9 if i == 1 else 0.3)
        if a is self.planned:
            sc += SMART * 1.2
        if a.uses >= 2:
            sc += SMART * (1.2 * a.hits / a.uses - 0.35 * min(4, a.miss))
        sc += random.random() * (0.35 + 2.6 * (1 - SMART))
        return sc

    def tick(self):
        s, b = self.sim, self.sim.b
        now = s.t
        if self.current is not None and now - self.current_at > 70:
            self.current.miss = 0 if self.current_hit else self.current.miss + 1
            self.current = None
        if not b.target or b.hp <= 0:
            return
        st = self.situation()
        if st['healing'] and SMART >= 0.5:
            self.urgent = True
        if b.gcd > s.t:
            # Combos (SkillBrain.chain): pasada la parte peligrosa, a veces corta la recuperación y encadena otro
            cur = self.current
            if SMART >= 0.6 and cur is not None and not cur.no_cancel and cur.gcd > 0 and not self.chain_tried \
                    and now - self.current_at >= cur.active_end / tempo() + 3 and b.gcd - s.t > 6:
                self.chain_tried = True
                if random.random() < 0.3 + 0.55 * SMART:
                    self.chain(st)
            return
        if not self.urgent and now < self.next:
            return
        if not self.urgent and now < self.hesitate_until:
            return
        self.next = now + self.interval()
        ok = self.usable()
        self.waiting = not ok
        if not ok:
            self.urgent = False
            return
        if not self.urgent and not self.hesitated and SMART >= 0.5 and random.random() < 0.24 * (1 - SMART):
            self.hesitated = True
            self.hesitate_until = now + 3 + random.randrange(10)
            return
        self.hesitated = False
        self.urgent = False
        done = set()
        for _ in range(3):
            cands = [a for a in ok if id(a) not in done]
            if not cands:
                break
            best = max(cands, key=lambda a: self.score(a, st))
            # SkillBrain.tick: un golpe por delante, primero se gira hacia el jugador (medio camino cada vez, 4 como mucho);
            # uno que sale a la vez que otro, solo si ya le mira
            if not self.facing(best):
                if done:
                    break
                # con el modelo bloqueado no puede girarse: espera a que el pack lo suelte
                if self.turns < (10 if b.locked else 4):
                    if not b.locked:
                        want = yaw_of(s.px - b.x, s.pz - b.z)
                        b.body = wrap(b.body + wrap(want - b.body) * 0.5)
                        b.look = want
                    self.turns += 1
                    self.hesitated = True
                    self.next = now + 2
                    return
            self.turns = 0
            self.fire(best)
            done.add(id(best))
            if b.gcd > s.t or random.random() > 0.35 + 0.4 * SMART:
                break
            ok = self.usable()

    def facing(self, a):
        """SkillBrain.facing: los golpes por delante, si tiene al jugador a menos de 50° de su cuerpo."""
        s, b = self.sim, self.sim.b
        if NO_REACH or not a.frontal or a.fov or a.rear or s.dist() < 1e-2:
            return True
        return abs(wrap(yaw_of(s.px - b.x, s.pz - b.z) - b.body)) <= 50

    def chain(self, st):
        s, b = self.sim, self.sim.b
        saved = b.gcd
        b.gcd = s.t
        ok = [a for a in self.usable() if a is not self.current and self.facing(a)]
        if not ok:
            b.gcd = saved
            return
        best = max(ok, key=lambda a: self.score(a, st))
        s.cancel_cast(self.current_cast)
        # Host.restoreMotion: como estaba antes del ataque que corta (su velocidad, el modelo sin bloquear, su IA)
        if getattr(self, 'motion', None):
            b.speed_mul, b.speed_set, b.locked, b.ai = self.motion
        self.chains += 1
        if s.verbose:
            print(f'{s.t:6d}        combo: {self.current.name} -> {best.name}')
        self.fire(best)

    def fire(self, a):
        s = self.sim
        s.cast_seq += 1
        ctx = {'targets': None, 'vars': {}, 'depth': 0, 'cast': s.cast_seq}
        self.current_cast = s.cast_seq
        self.chain_tried = False
        self.reach_wait = -1
        # SkillBrain.fire: cómo estaba antes de empezarlo (Host.motion), para dejarlo así si encadena otro
        b = s.b
        self.motion = (b.speed_mul, b.speed_set, b.locked, b.ai)
        # ¿Lo lanza donde su golpe llega? (los que no tienen tope no cuentan)
        if not a.far:
            s.reach_fired += 1
            if s.dist() > a.reach() + 0.9:
                s.air += 1
        # ¿Lo lanza mirándole? (los que van por delante; los que el pack elige por el lado no cuentan)
        if a.frontal and not a.fov and not a.rear:
            s.front_fired += 1
            if abs(wrap(yaw_of(s.px - s.b.x, s.pz - s.b.z) - s.b.body)) > 50:
                s.sideways += 1
        if a is self.planned:
            self.planned_until = 0
        s.firing = a
        if a.pool:
            s.run_meta(a.name, dict(ctx, targets=s.targets(a.line, ctx)))
        else:
            s.exec(a.line, ctx)
        s.firing = None
        if self.current is not None and self.current is not a:
            self.current.miss = 0 if self.current_hit else self.current.miss + 1
        a.uses += 1
        self.current, self.current_at, self.current_hit = a, s.t, False
        self.recent.insert(0, a.name)
        del self.recent[6:]
        # ¿le entra? (el jugador esquiva a veces; con escudo para casi todo menos agarres)
        d = s.dist()
        p = 0.65 if a.min - 1 <= d <= min(a.max, a.reach()) + 1 else 0.2
        if s.t < s.block_until and not a.grab:
            p *= 0.3

        def land(att=a):
            if self.current is att and not self.current_hit and random.random() < p:
                self.current_hit = True
                att.hits += 1
        s.later(15, land)

    def attacking(self):
        """SkillBrain.attacking: su gcd o lo que dura el ataque elegido (packs sin gcd: Abominación, Astwood)."""
        s = self.sim
        if s.b.gcd > s.t:
            return True
        return self.current is not None and s.t - self.current_at < self.current.active_end / tempo()

    MOMENT = {'distance', 'targetwithin', 'targetnotwithin', 'playerwithin', 'playernotwithin', 'offgcd', 'gcd',
              'fieldofview', 'fov', 'infieldofview', 'lineofsight', 'los', 'ydiff'}

    def possible(self, a):
        """SkillBrain.possible: ¿es de su fase y su postura? (sin distancia, cooldown ni gcd)"""
        s = self.sim
        if a.line.get('hp') and not s.hp_ok(a.line['hp']):
            return False
        meta = s.tree.get(a.name.lower())
        ctx = {'targets': None, 'vars': {}, 'depth': 0}
        for c in meta.get('conditions', []):
            if c['m'] in self.MOMENT:
                continue
            act = str(c.get('v', 'true')).lower()
            if act.startswith('cast ') or act.startswith('power'):
                continue
            r = s.cond(c, 'boss', ctx)
            if act.startswith('castinstead'):
                if r:
                    return True
                continue
            if not s.passes(c, r):
                return False
        return True

    def plan(self):
        s, b = self.sim, self.sim.b
        if not b.target:
            return None
        now = s.t
        if now >= self.plan_check:
            self.plan_check = now + 10
            can = [a for a in self.attacks if self.possible(a)]
            if can:
                self.can = can
            if self.planned is None or now >= self.planned_until or self.planned not in self.can:
                soon = [a for a in self.can if b.cooldowns.get(meta_key(a.name), -1) - s.t < 40] or list(self.can)
                random.shuffle(soon)

                def age(a):
                    return self.recent.index(a.name) if a.name in self.recent else 99
                self.planned = max(soon, key=age) if soon else None
                self.planned_until = now + 60 + random.randrange(60)
        p = self.planned
        if p is None:
            return None
        if SMART < 0.4:
            near = min([a.min for a in self.can] or [99])
            if near < 1 or near > 12:
                return None
            return near + 0.5, 0, (near if self.waiting else 0), 40
        self.preferred = max(1.5, min(p.sweet(s), 14))
        strafe = 0
        if SMART >= 0.6 and self.waiting and b.gcd <= s.t:
            if now >= self.strafe_until:
                self.strafe = random.choice([1, -1])
                self.strafe_until = now + 40 + random.randrange(50)
            strafe = self.strafe
        return self.preferred, strafe, (min(p.min, 12) if self.waiting else 0), min(p.max, 40)



class Sim:
    def __init__(self, boss_id, seed=1, verbose=False):
        random.seed(seed)
        self.prog = json.load(open(os.path.join(RES, boss_id + '.json'), encoding='utf-8'))
        self.tree = {k.lower(): v for k, v in self.prog['tree'].items()}
        self.b = Boss(self.prog, self.prog['boss']['mob'])
        self.t = 0
        self.tasks = []  # (at, fn)
        self.px, self.pz = 14.0, 0.0
        self.want_dist = 10.0
        self.verbose = verbose
        self.hits = 0
        self.cancelled_hits = 0
        self.melee_tries = self.melee_hits = 0
        # El modelo del jefe (los scripts «mm:» de sus animaciones) y qué animación tiene cada prioridad
        mp = os.path.join(RES, 'models', str(self.prog['boss'].get('model')) + '.json')
        self.model = json.load(open(mp, encoding='utf-8')) if os.path.exists(mp) else None
        self.script_tok = {}
        self.fireballs = 0
        self.unknown = set()
        # Lo que hace el jugador (para ver si el jefe le castiga)
        self.heal_until = self.block_until = self.aim_until = -1
        self.last_shot = -1000
        self.heals = []  # (empieza, ¿castigada?)
        self.firing = None
        self.cast_seq = 0
        self.next_backstep = 0
        self.backsteps = 0
        # ModelEngine en la entidad: move{} (hasta qué tick lo lleva el pack), rootmotion (hueso raíz, si manda, sus
        # pesos) y la escala de su caja (model{hitboxscale}, scale{hitbox=true})
        self.move_until = 0
        self.root_bone, self.root_on, self.root_w, self.root_bw = None, False, -1.0, -1.0
        self.hb_scale = 1.0
        self.in_skill_damage = False
        self.model_scale = 1.0  # la escala del modelo (model{scale}, setmodelscale): SkillBrain.modelScale
        self.reach_fired = self.air = 0
        self.front_fired = self.sideways = 0
        self.brain = None  # (se hace en su primer tick: run)

    # ------------------------------------------------------------------ jugador
    def player_step(self):
        b = self.b
        if self.t % 60 == 0:
            self.want_dist = random.choice([2.5, 3.5, 5, 7, 9, 12, 15, 18, 22])
        if self.t % 300 == 150:
            # en una incursión hay gente por todos lados: a veces el que más le pega está a un lado o detrás
            ang = math.radians(random.uniform(0, 360))
            d = self.dist()
            self.px, self.pz = b.x + math.cos(ang) * d, b.z + math.sin(ang) * d
        dx, dz = self.px - b.x, self.pz - b.z
        d = math.hypot(dx, dz) or 1e-6
        # Se cura de vez en cuando (si está algo lejos), se cubre cuando lo tiene encima y tensa el arco de lejos
        if self.t % 500 == 250 and d > 6 and self.t >= self.heal_until:
            self.heal_until = self.t + 32
            self.heals.append([self.t, False])
        if d < 5 and self.t >= self.block_until and random.random() < 0.01:
            self.block_until = self.t + 40
        if d > 9 and self.t >= self.aim_until and random.random() < 0.01:
            self.aim_until = self.t + 25
        # se acerca o se aleja a 0,2 b/t y rodea al jefe (comiendo va despacio)
        radial = max(-0.28, min(0.28, (self.want_dist - d) * 0.1))  # corre (sprint)
        if self.t < self.heal_until:
            radial *= 0.2
        tx, tz = -dz / d, dx / d
        side = 0.12 if (self.t // 100) % 2 == 0 else -0.12
        self.px += dx / d * radial + tx * side
        self.pz += dz / d * radial + tz * side
        # pega si está cerca
        busy = self.t < self.heal_until or self.t < self.block_until
        if d < 4.5 and self.t % 12 == 0 and not busy:
            self.damage(6.0, 'ENTITY_ATTACK')
        elif d > 8 and self.t % 20 == 0 and random.random() < 0.4 and not busy:
            self.last_shot = self.t
            self.damage(4.0, 'PROJECTILE')

    def damage(self, amount, cause):
        b = self.b
        if b.hp <= 0 or b.invulnerable and not OLD:
            return
        self.hits += 1
        # damage-amount en la escala de vida del pack (como BossEntity.hurt)
        scale = self.prog['boss']['health'] / max(1.0, float(self.prog['boss'].get('pack_health') or 1))
        vars = {'damage-amount': amount * 2 / scale, 'damage-cause': cause}
        if OLD:
            # La 1.3.75 lanzaba ~onDamaged con el golpe ya dentro
            b.hp -= amount * b.maxhp / 2500.0
            self.trigger('ondamaged', vars)
            return
        # Como BossEntity.hurt: ~onDamaged ANTES de que entre (vida de antes para sus umbrales); cancelevent lo anula y
        # modifydamage lo cambia (sus cifras ADD/SET van en la escala del pack: aquí, ×scale/2)
        hit = {'amount': amount, 'cancelled': False, 'scale': scale / 2}
        self.trigger('ondamaged', vars, hit=hit)
        self.trigger('oncombat', vars, hit=hit)  # SkillMobs.damaged: ~onCombat también al recibir
        if hit['cancelled'] or hit['amount'] <= 0 or b.hp <= 0:
            self.cancelled_hits += 1
            return
        b.hp -= hit['amount'] * b.maxhp / 2500.0  # que la pelea dure unos minutos sea cual sea la vida

    def dist(self):
        return math.hypot(self.px - self.b.x, self.pz - self.b.z)

    # ------------------------------------------------------------------ jefe
    def boss_step(self):
        b = self.b
        # objetivo: el jugador si está al alcance (siempre en esta prueba) y lo busca (players; hurtbytarget, si le ha
        # pegado; con clear, solo si una skill se lo da)
        names = {AI.target_name(x) for x in b.ai_targets}
        hunts = 'players' in names or 'hurtbytarget' in names and self.hits > 0
        b.target = b.ai and self.dist() < 64 and hunts
        if b.target and not b.combat:
            b.combat = True
            self.trigger('onentercombat')
        moving = False
        ox, oz = b.x, b.z
        if self.t < self.move_until:
            pass  # move{} del pack: este tick lo lleva el pack (BossEntity.customServerAiStep)
        elif b.ai and b.target and b.chases and b.speed() > 0:
            d = self.dist()
            hb = self.prog['boss'].get('hitbox') or [1, 2]
            stop = hb[0] * self.hb_scale / 2 + 1.0  # como BossEntity
            gx, gz = self.px, self.pz
            plan = self.brain.plan() if self.brain else None
            if plan:
                want = max(stop, plan[0])
                rx, rz = b.x - self.px, b.z - self.pz
                rl = math.hypot(rx, rz) or 1e-6
                if d < plan[2] - 0.2 and not self.brain.attacking():
                    # Demasiado cerca para lo que va a hacer: paso atrás (≈2,5 bloques) y se aparta
                    if SMART >= 0.6 and d < plan[2] - 0.8 and self.t >= self.next_backstep and not b.locked:
                        b.x += rx / rl * 2.5
                        b.z += rz / rl * 2.5
                        self.next_backstep = self.t + 70 + random.randrange(60)
                        self.backsteps += 1
                    gx, gz = b.x + rx / rl * 4, b.z + rz / rl * 4
                    stop = 0.6
                elif plan[1] and d < want + 3:
                    ang = math.radians(25 * plan[1])
                    ux, uz = rx / rl, rz / rl
                    tx = ux * math.cos(ang) - uz * math.sin(ang)
                    tz = ux * math.sin(ang) + uz * math.cos(ang)
                    gx, gz = self.px + tx * max(want, 2.5), self.pz + tz * max(want, 2.5)
                    stop = 0.6
                elif d < want - 2.5:
                    gx, gz = b.x + rx / rl * 4, b.z + rz / rl * 4
                    stop = 0.6
                else:
                    stop = want
            gd = math.hypot(gx - b.x, gz - b.z)
            if (gx, gz) != (self.px, self.pz):
                if gd > stop:
                    step = min(b.speed() * 0.9, gd - stop)
                    b.x += (gx - b.x) / gd * step
                    b.z += (gz - b.z) / gd * step
                    moving = True
            elif d > stop:
                step = min(b.speed() * 0.9, d - stop)
                b.x += (self.px - b.x) / d * step
                b.z += (self.pz - b.z) / d * step
                moving = True
        b.moving = moving
        self.melee_step()
        self.ghast_step()
        want = yaw_of(self.px - b.x, self.pz - b.z)
        if b.chases and b.ai:
            b.look = wrap(b.look + max(-30, min(30, wrap(want - b.look))))
            if not b.locked:
                track = (2 + 8 * SMART) if (self.brain and SMART >= 0.4 and self.brain.attacking()) else 0
                if track and not moving:
                    b.body = wrap(b.body + max(-track, min(track, wrap(want - b.body))))
                elif moving:
                    # BossEntity.turnBody: andando, el cuerpo va hacia donde anda (retrocediendo, mirándole)
                    go = yaw_of(b.x - ox, b.z - oz) if (b.x, b.z) != (ox, oz) else want
                    to = want if abs(wrap(go - want)) > 110 else go
                    b.body = wrap(b.body + max(-20, min(20, wrap(to - b.body))))
                else:
                    diff = wrap(want - b.body)
                    if abs(diff) > 30:  # la cabeza arrastra al cuerpo
                        b.body = wrap(b.body + max(-30, min(30, diff - math.copysign(30, diff))))
                    b.body = wrap(b.body + wrap(want - b.body) * 0.05)
        else:
            b.look = b.body

    def melee_step(self):
        """BossEntity.meleeStep: el golpe de Minecraft (MeleeAttackGoal) con sus ~onAttack y ~onCombat antes de entrar."""
        b = self.b
        if not (b.ai and b.target and b.melee):
            b.melee_wait = 0
            return
        if b.melee_wait > 0:
            b.melee_wait -= 1
        w = (self.prog['boss'].get('hitbox') or [1, 2])[0] * self.hb_scale
        reach = (w * 2) ** 2 + 0.6 if AI.has(b.goals, 'meleeattack', 'zombieattack') else 4 + 0.6
        if b.melee_wait > 0 or self.dist() ** 2 > reach:
            return
        b.melee_wait = 20
        dmg = float(self.prog['boss'].get('damage') or 0)
        hit = {'amount': dmg, 'cancelled': False, 'scale': 1.0}
        vars = {'damage-amount': dmg, 'damage-cause': 'ENTITY_ATTACK', 'damage-type': 'ENTITY_ATTACK'}
        self.melee_tries += 1
        self.trigger('onattack', vars, hit=hit)
        self.trigger('oncombat', vars, hit=hit)
        if not hit['cancelled'] and hit['amount'] > 0:
            self.melee_hits += 1

    def play_scripts(self, s, a):
        """SkillMobs.scripts: los «mm:» de la línea de tiempo de la animación que pone (a su velocidad y con el tempo),
        mientras siga puesta en su prioridad; las de bucle, otra vez en cada vuelta."""
        anim = (self.model or {}).get('anims', {}).get(s)
        if not anim or not anim.get('fx'):
            return
        prio = int(num(arg(a, 1, 'priority', 'p'), 1))
        tok = object()
        self.script_tok[prio] = (s, tok)
        sp = max(0.05, num(arg(a, 1, 'speed', 'sp'), 1) * tempo())
        length = max(0.05, float(anim.get('length') or 0))

        def lap():
            if self.script_tok.get(prio, (None, None))[1] is not tok:
                return
            for t, skill in anim['fx']:
                def go(sk=skill):
                    if self.script_tok.get(prio, (None, None))[1] is tok:
                        self.run_meta(sk, {'targets': None, 'vars': {}, 'depth': 0, 'hit': None})
                self.later(int(round(float(t) * 20 / sp)), go)
            if anim.get('loop') == 'loop':
                self.later(max(1, int(round(length * 20 / sp))), lap)

        lap()

    def ghast_step(self):
        """BossEntity.ghastStep: la bola de fuego del ghast (20 de carga, 40 de espera), con ~onShoot antes de salir."""
        b = self.b
        if not (b.ai and b.target and AI.has(b.goals, 'type:ghast')) or self.dist() >= 64:
            b.ghast_charge = 0 if not b.target else max(0, getattr(b, 'ghast_charge', 0) - 1)
            return
        b.ghast_charge = getattr(b, 'ghast_charge', 0) + 1
        if b.ghast_charge == 20:
            b.ghast_charge = -40
            hit = {'amount': 0, 'cancelled': False, 'scale': 1.0}
            self.trigger('onshoot', {'bow-tension': 1}, hit=hit)
            if not hit['cancelled']:
                self.fireballs += 1

    # ------------------------------------------------------------------ disparadores
    def trigger(self, tr, vars=None, value=None, hit=None):
        for pm in self.b.mob['mechs']:
            t = pm.get('tr') or 'oncombat'  # sin disparador: ~onCombat (MythicMobs)
            if t != tr:
                continue
            if value is not None and str(pm.get('trv', '')).lower() != str(value).lower():
                continue
            if pm.get('hp') and not self.hp_ok(pm['hp']):
                continue
            self.exec(pm, {'targets': None, 'vars': dict(vars or {}), 'depth': 0, 'hit': hit})

    def hp_ok(self, spec):
        pct = self.b.hp / self.b.maxhp * 100
        return in_range(pct, spec.replace('%', ''))

    def rhythm(self):
        r = RHYTHM
        if SMART >= 0.5:
            hp = self.b.hp / self.b.maxhp
            if hp < 0.5:
                r *= 1 + 0.12 * SMART
            if hp < 0.25:
                r *= 1 + 0.12 * SMART
        return r

    def can_run(self, name, ctx):
        """SkillRuntime.usable: ¿saldría? (sin hacer nada)"""
        meta = self.tree.get(str(name).lower())
        if meta is None:
            return False
        b = self.b
        if num(meta.get('cooldown'), 0) > 0 and b.cooldowns.get(meta_key(name), -1) > self.t:
            return False
        for c in meta.get('conditions', []):
            act = str(c.get('v', 'true')).lower()
            if act.startswith('cast ') or act.startswith('power'):
                continue
            r = self.cond(c, 'boss', ctx)
            if act.startswith('castinstead'):
                if r:
                    return True
                continue
            if not self.passes(c, r):
                return False
        if not meta.get('target_conditions'):
            return True
        ins = ctx['targets'] if ctx['targets'] is not None else ['boss']
        return any(all(self.passes(c, self.cond(c, w, ctx)) for c in meta['target_conditions']) for w in ins)

    def timers(self):
        on = self.brain is not None
        for pm in self.b.mob['mechs']:
            if pm.get('tr') != 'ontimer':
                continue
            if on and id(pm) in self.brain.handled:
                continue
            every = int(num(pm.get('trv'), 20)) or 1
            if self.t % every == 0 and (not pm.get('hp') or self.hp_ok(pm['hp'])):
                self.exec(pm, {'targets': None, 'vars': {}, 'depth': 0})
        if on:
            self.brain.tick()

    # ------------------------------------------------------------------ condiciones
    def cond(self, c, who, ctx):
        m, a = c['m'], c.get('a', {})
        b = self.b
        if m in ('or', 'and'):
            res = [self.cond(p, who, ctx) == (str(p.get('v', 'true')) != 'false') for p in c.get('parts', [])]
            return any(res) if m == 'or' else all(res)
        if m in ('varequals', 'variableequals'):
            v = b.var(arg(a, '', 'var', 'variable', 'name', 'key', 'k'))
            want = str(arg(a, '', 'value', 'val', 'v')).strip('"')
            v = 'UNDEFINED' if v is None else v
            return str(v).lower() == want.lower() or (num(v, None) is not None and num(v, None) == num(want, None) and want != '')
        if m in ('variableinrange', 'varinrange'):
            v = b.var(arg(a, '', 'var', 'variable', 'name', 'key', 'k'))
            return v is not None and in_range(num(v), arg(a, '>0', 'value', 'val', 'v', 'range'))
        if m in ('offgcd', 'gcd'):
            return b.gcd <= self.t
        if m in ('hasaura', 'hasbuff'):
            n = str(arg(a, '', 'auraname', 'aura', 'name', 'n', 'buffname', 'b')).lower()
            return b.auras.get(n, -1) > self.t
        if m == 'incombat':
            return b.target
        if m in ('playerwithin', 'playernotwithin'):
            within = math.hypot(self.px - b.x, self.pz - b.z) <= num(arg(a, 5, 'distance', 'd'), 5)
            return within != (m == 'playernotwithin')
        if m == 'hasai':
            return b.ai
        if m in ('health', 'hp') and who == 'boss':
            # en la vida del pack, como SkillConds
            pack = float(self.prog['boss'].get('pack_health') or self.prog['boss']['health'])
            return in_range(b.hp / b.maxhp * pack, arg(a, '>0', 'health', 'h', 'amount', 'a'))
        if m in ('healthpercent', 'hppercent', 'hpp') and who == 'boss':
            return in_range(b.hp / b.maxhp, arg(a, '>0', 'percent', 'p', 'health', 'h'))
        if m in ('lineofsight', 'los', 'targetinlineofsight'):
            return b.target  # aquí no hay paredes
        if m in ('targetwithin', 'targetnotwithin'):
            within = b.target and self.dist() <= num(arg(a, 16, 'distance', 'd'), 16)
            return within != (m == 'targetnotwithin')
        if m == 'distance':
            if who == 'player':
                return in_range(self.dist(), arg(a, '>0', 'distance', 'd'))
            return in_range(0.0, arg(a, '>0', 'distance', 'd'))
        if m in ('fieldofview', 'fov'):
            if who != 'player':
                return True
            angle = num(arg(a, 90, 'angle', 'a'), 90)
            rot = num(arg(a, 0, 'rotation', 'r'), 0)
            want = yaw_of(self.px - b.x, self.pz - b.z)
            return abs(wrap(want - (b.look - rot))) <= angle / 2
        if m in ('damagecause',):
            want = str(arg(a, '', 'cause', 'c', 'type', 't')).upper().split(',')
            return ctx['vars'].get('damage-cause', '') in [w.strip() for w in want]
        if m in ('mobsinradius', 'mir'):
            return in_range(0, arg(a, '>0', 'amount', 'a'))
        if m in ('modelhaspassengers',):
            return False
        # SkillConds: en la prueba no hay marcador (todo a 0), el jugador no lleva nada puesto ni es op, y en el chunk
        # del jefe solo está él
        if m == 'score' and who == 'boss' and not arg(a, None, 'entry', 'ent', 'e'):
            # la del jefe: en memoria (setmobscore, modifymobscore), como SkillConds
            return in_range(b.scores.get(str(arg(a, '', 'objective', 'obj', 'o')), 0.0), arg(a, '0', 'value', 'val', 'v'))
        if m in ('score', 'globalscore', 'scoreglobal'):
            return in_range(0, arg(a, '0', 'value', 'val', 'v'))
        if m == 'playerkills':
            return in_range(0, arg(a, '0', 'kills', 'k'))
        if m == 'mobsinchunk':
            return in_range(1, arg(a, '0', 'amount', 'a'))
        if m in ('itemrecharging', 'wearing', 'iswearing', 'wielding', 'iswielding', 'haspermission'):
            return False
        if m == 'stance':
            # SkillConds: la postura de quien la mire (setstance)
            return who == 'boss' and str(getattr(b, 'stance', '') or '').lower() == str(arg(a, '', 'stance', 's')).lower()
        if m in ('variableisset', 'varisset'):
            return b.var(str(arg(a, '', 'var', 'variable', 'name', 'key', 'k'))) is not None
        if m in ('moving', 'ismoving'):
            # el jugador de la prueba siempre se mueve; el jefe, si anda
            return who == 'player' or bool(getattr(b, 'moving', False))
        if m in ('sprinting', 'crouching', 'iscrouching', 'sneaking', 'inblock'):
            return False
        if m == 'altitude':
            # en el suelo (0); los que vuelan como un ghast, a media altura (6): la prueba no tiene alturas
            alt = 6.0 if AI.has(b.goals, 'type:ghast') or AI.flag(b.mob, 'NoGravity') else 0.0
            return in_range(alt, arg(a, '>0', 'altitude', 'a', 'height', 'h'))
        if m in ('ydiff', 'onground', 'blocking', 'mythicmobtype', 'boundingboxesoverlap'):
            return m in ('ydiff', 'onground')
        self.unknown.add('cond:' + m)
        return True

    def passes(self, c, r):
        act = str(c.get('v', 'true')).lower()
        if act in ('false', 'cancel'):
            return not r
        return r

    # ------------------------------------------------------------------ ejecución
    def run_meta(self, name, ctx):
        meta = self.tree.get(str(name).lower())
        if meta is None or ctx['depth'] > 40:
            return False
        b = self.b
        cd = num(meta.get('cooldown'), 0)
        if cd > 0 and b.cooldowns.get(meta_key(name), -1) > self.t:
            return False
        if OLD and cd > 0:
            b.cooldowns[meta_key(name)] = self.t + round(cd * 20)
        for c in meta.get('conditions', []):
            act = str(c.get('v', 'true')).lower()
            r = self.cond(c, 'boss', ctx)
            if act.startswith('castinstead'):
                if r:
                    self.run_meta(act[len('castinstead'):].strip(), dict(ctx, depth=ctx['depth'] + 1))
                    return True
                continue
            if not self.passes(c, r):
                return False
        for c in meta.get('trigger_conditions', []):
            if not self.passes(c, self.cond(c, 'player', ctx)):
                return False
        targets = ctx['targets']
        if meta.get('target_conditions'):
            ins = targets if targets is not None else ['boss']
            outs = [w for w in ins if all(self.passes(c, self.cond(c, w, ctx)) for c in meta['target_conditions'])]
            if not outs:
                return False
            targets = outs
        if cd > 0 and not OLD:
            b.cooldowns[meta_key(name)] = self.t + round(cd * 20 / self.rhythm())
        self.script(meta['mechs'], dict(ctx, targets=targets, depth=ctx['depth'] + 1), 0, name)
        return True

    def script(self, mechs, ctx, i, name):
        while i < len(mechs):
            pm = mechs[i]
            if pm['m'] == 'delay':
                ticks = int(max(0, round(num(arg(pm.get('a', {}), 0, '_', 'ticks', 'd'), 0) / tempo())))
                rest = i + 1
                self.later(ticks, lambda r=rest: self.script(mechs, ctx, r, name), ctx.get('cast', 0))
                return
            self.exec(pm, ctx)
            i += 1

    def root_motion(self, anim):
        """rootmotion (SkillModels.rootStep): el jefe anda, tick a tick, lo que la animación mueve su hueso raíz (en el
        suelo, en los ejes de su cuerpo, con el peso de los ataques)."""
        model = load_model(self.prog['boss'].get('model'))
        if not model:
            return
        ids = [bb['id'] for bb in model['bones']]
        an = model['anims'].get(anim)
        if self.root_bone not in ids or not an:
            return
        bi = ids.index(self.root_bone)
        tr = next((x.get('p') for x in an['tracks'] if x['b'] == bi and x.get('p')), None)
        if not tr:
            return
        w = 1.0 if self.root_w < 0 else min(1.0, self.root_w)
        ticks = max(1, int(round(an['len'] * 20 / tempo())))
        prev = vfx_sample(tr, 0)
        for k in range(1, ticks + 1):
            cur = vfx_sample(tr, k / 20 * tempo())

            def step(dx=cur[0] - prev[0], dz=cur[2] - prev[2]):
                r = math.radians(180 - self.b.body)  # del modelo al mundo (como SkillModels.world)
                self.b.x += (dx * math.cos(r) + dz * math.sin(r)) / 16 * w
                self.b.z += (-dx * math.sin(r) + dz * math.cos(r)) / 16 * w
            self.later(k, step)
            prev = cur

    def later(self, ticks, fn, cast=0):
        if ticks <= 0:
            fn()
        else:
            self.tasks.append((self.t + ticks, cast, fn))

    def cancel_cast(self, cast):
        """SkillRuntime.cancelCast: lo que le queda a ese uso (sus delay) ya no sale."""
        if cast:
            self.tasks = [x for x in self.tasks if x[1] != cast]

    def exec(self, pm, ctx):
        a = pm.get('a', {})
        for c in pm.get('c') or []:
            if self.cond(c, 'boss', ctx) == c.get('not', False):
                return
        if pm.get('ch') not in (None, 1, 1.0) and random.random() >= float(pm['ch']):
            return
        # SkillRuntime.exec: el modificador de vida de la línea, también dentro de una skill
        if pm.get('hp') and not self.hp_ok(pm['hp']):
            return
        delay = int(max(0, round(num(a.get('delay'), 0) / tempo())))
        repeat = int(num(arg(a, 0, 'repeat'), 0))
        every = max(1, int(round(num(arg(a, 1, 'repeatinterval', 'repeati'), 1) / tempo())))
        cast = ctx.get('cast', 0)

        def once():
            self.apply(pm, ctx, self.targets(pm, ctx))

        def step(left):
            once()
            if left > 0:
                self.later(every, lambda: step(left - 1), cast)

        self.later(delay, lambda: step(min(repeat, 200)), cast)

    def targets(self, pm, ctx):
        t = pm.get('t')
        if t is None:
            if pm['m'] in OWN_MODEL and not OLD:
                return ['boss']
            return ctx['targets'] if ctx['targets'] else ['boss']
        if t in ('self', 'caster'):
            return ['boss']
        if t in ('target', 't', 'trigger'):
            return ['player'] if self.b.target else []
        if t in ('threattable', 'tt', 'ttp', 'threattableplayers', 'randomthreattarget', 'rtt'):
            return ['player'] if self.b.target else []
        if t in ('pir', 'playersinradius', 'eir', 'livinginradius', 'entitiesinradius'):
            r = num(arg(pm.get('ta') or {}, 5, 'r', 'radius'), 5)
            return ['player'] if self.dist() <= r else []
        return ['point']

    def apply(self, pm, ctx, targets):
        m, a = pm['m'], pm.get('a', {})
        b = self.b
        if m in ('skill', 'metaskill'):
            for n in str(arg(a, '', 's', 'skill', 'skills')).split(','):
                self.run_meta(n.strip().split(' ')[0], dict(ctx, targets=targets))
        elif m == 'randomskill':
            names = [x.strip().split()[0] for x in str(arg(a, '', 'skills', 's')).split(',') if x.strip()]
            if names:
                self.run_meta(random.choice(names), dict(ctx, targets=targets))
        elif m in ('setvar', 'setvariable'):
            var = str(arg(a, '', 'var', 'variable', 'name', 'key', 'k')).lower()
            name = var.split('.', 1)[1] if var.startswith(('caster.', 'skill.')) else var
            if var.startswith('target.'):
                return
            b.vars[name] = str(arg(a, '', 'value', 'val', 'v')).strip('"')
            dur = int(num(arg(a, 0, 'duration', 'd'), 0))
            if dur > 0:
                dur = max(1, round(dur / tempo()))
            tok = object()
            b.var_until[name] = tok
            if dur > 0 and not OLD:
                def expire(n=name, k=tok):
                    if b.var_until.get(n) is k:
                        b.vars.pop(n, None)
                self.later(dur, expire)
        elif m in ('variablesubtract', 'varsubtract', 'variableadd', 'varadd'):
            var = str(arg(a, '', 'var', 'variable', 'name', 'key', 'k')).lower()
            name = var.split('.', 1)[1] if var.startswith(('caster.', 'skill.')) else var
            raw = str(arg(a, '1', 'amount', 'a', 'value', 'val'))
            amount = ctx['vars'].get('damage-amount', 0) if 'damage-amount' in raw else num(raw, 1)
            sign = -1 if 'sub' in m else 1
            b.vars[name] = str(num(b.vars.get(name), 0) + sign * amount)
        elif m in ('gcd', 'setgcd', 'globalcooldown'):
            if OLD and 'boss' not in targets:
                return
            b.gcd = self.t + int(round(num(arg(a, 20, 'ticks', 't', 'duration', 'd', '_'), 20) / tempo()))
        elif m == 'setskillcooldown':
            if 'boss' in targets:
                b.cooldowns[meta_key(arg(a, '', 'skill', 's'))] = self.t + round(num(arg(a, 0, 'seconds', 'ticks'), 0) * 20)
        elif m in ('aura', 'buff'):
            n = str(arg(a, '', 'auraname', 'aura', 'n', 'buffname')).lower()
            if n and 'boss' in targets:
                b.auras[n] = self.t + int(num(arg(a, 200, 'duration', 'd', 'ticks'), 200))
            if a.get('ot') or a.get('ontick'):
                pass
        elif m in ('state', 'animation', 'statetoggle', 'togglestate'):
            s = str(arg(a, '', 'state', 's', 'animation', 'a'))
            # «hit_<random.1to3>» (SkillModels.state lo resuelve cada vez)
            s = re.sub(r'<random\.(?:int\.)?(-?\d+)to(-?\d+)>', lambda mt: str(random.randint(int(mt.group(1)), int(mt.group(2)))), s)
            if 'boss' in targets and str(arg(a, 'false', 'remove', 'r')).lower() == 'true':
                self.script_tok = {k: v for k, v in self.script_tok.items() if v[0] != s}
            if str(arg(a, 'false', 'remove', 'r')).lower() == 'true' or 'boss' not in targets:
                return
            self.play_scripts(s, a)
            if self.root_on and self.root_bone:
                self.root_motion(s)
            if s not in NOT_ATTACKS:
                # Los golpes lanzan lejos al jugador (throw de sus ataques): la distancia cambia como en una pelea
                d = self.dist()
                if d < 7:
                    k = random.uniform(3, 8) / max(d, 0.5)
                    self.px += (self.px - b.x) * k * 0.5
                    self.pz += (self.pz - b.z) * k * 0.5
                b.fired[s] = b.fired.get(s, 0) + 1
                a = self.firing
                for h in self.heals:
                    if not h[1] and 0 <= self.t - h[0] <= 30 and (a is None or a.gap or a.ranged):
                        h[1] = True
                b.timeline.append((self.t, s))
                if self.verbose:
                    print(f'{self.t:6d} d={self.dist():5.1f} hp={b.hp / b.maxhp * 100:5.1f}%  {s}')
        elif m == 'setspeed':
            if 'boss' in targets:
                b.speed_mul = num(arg(a, 1, 'speed', 's', 'amount', 'a'), 1)
                b.speed_set = True
        elif m in ('setai', 'setnoai'):
            if 'boss' in targets:
                b.ai = str(arg(a, 'true', 'ai', 'a')).lower() == 'true'
        elif m in ('setmobscore', 'settargetscore', 'modifymobscore', 'modifytargetscore'):
            # SkillMore.score: las de los mobs, en memoria (las de los jugadores no se tocan)
            obj = str(arg(a, '', 'objective', 'obj', 'o'))
            if obj and ('boss' in targets or m.endswith('mobscore')):
                v = num(arg(a, 0, 'value', 'val', 'v'), 0)
                now = b.scores.get(obj, 0.0)
                act = str(arg(a, 'SET', 'action', 'a', 'mode')).upper()
                if m.startswith('modify'):
                    v = {'ADD': now + v, 'SUBTRACT': now - v, 'SUB': now - v, 'MULTIPLY': now * v, 'MULT': now * v,
                         'DIVIDE': now / v if v else now, 'DIV': now / v if v else now}.get(act, v)
                b.scores[obj] = v
        elif m == 'setstance':
            if 'boss' in targets:
                b.stance = str(arg(a, '', 'stance', 's'))
        elif m in ('runaigoalselector', 'aigoal', 'aigoals', 'runaitargetselector', 'aitarget'):
            # BossEntity.addAiGoal: uno más en su IA (clear quita los de antes; en los de búsqueda, también su objetivo)
            tgt = m in ('runaitargetselector', 'aitarget')
            g = arg(a, None, *(('target', 'aitargetselector', 's', 'string', 'targetselector', 'goal', 'g') if tgt else
                               ('goal', 'aigoalselector', 'g', 'goalselector', 's', 'string', 't', 'target')))
            if g is not None:
                if tgt:
                    b.ai_targets = AI.apply(b.ai_targets, [g], True)
                else:
                    b.goals = AI.apply(b.goals, [g])
                    b.chases, b.melee = AI.chases(b.goals), AI.melee(b.goals)
        elif m in ('resetai', 'resetaigoals'):
            b.goals = AI.vanilla_goals(b.mob.get('type'))
            b.chases, b.melee = AI.chases(b.goals), AI.melee(b.goals)
            b.ai = True
        elif m == 'lockmodel':
            if 'boss' in targets:
                b.locked = str(arg(a, 'true', 'lock', 'l')).lower() == 'true'
        elif m == 'setrotation':
            if 'boss' in targets:
                y = num(arg(a, 0, 'yaw', 'y'), 0)
                rel = str(arg(a, 'false', 'relative', 'r')).lower() == 'true'
                b.body = wrap(b.body + y if rel else y)
                if not b.chases:
                    b.look = b.body
        elif m == 'signal':
            if 'boss' in targets:
                self.trigger('onsignal', value=arg(a, '', 'signal', 's'))
        elif m == 'totem':
            oh = str(arg(a, '', 'onhit', 'oh')).lower()
            if 'detect_bite' in oh:
                # la Hidra: ¿está el jugador delante, a su alcance? (centro, izquierda o derecha)
                d = self.dist()
                rel = wrap(yaw_of(self.px - b.x, self.pz - b.z) - b.body)
                side = 'center' if abs(rel) < 25 else ('left' if rel < 0 else 'right')
                if d < 12 and abs(rel) < 70 and oh.endswith(side):
                    self.run_meta(oh, dict(ctx, targets=['player']))
        elif m in ('sethealth',) and 'boss' in targets and num(arg(a, 0, 'amount', 'a'), 0) <= 0:
            pass
        # El golpe de ~onDamaged (SkillRuntime: cancelevent, modifydamage)
        elif m == 'cancelevent':
            if ctx.get('hit') is not None:
                ctx['hit']['cancelled'] = True
        elif m == 'modifydamage':
            hit = ctx.get('hit')
            if hit is not None:
                v = num(arg(a, 1, 'amount', 'a', 'damage', 'd', 'multiplier', 'm'), 1)
                act = str(arg(a, 'MULTIPLY', 'action', 'act', 'type', 't', 'mode')).upper()
                h = hit['amount']
                h = {'ADD': h + v * hit['scale'], 'SUBTRACT': h - v * hit['scale'], 'SUB': h - v * hit['scale'],
                     'DIVIDE': h / v if v else h, 'SET': v * hit['scale']}.get(act, h * v)
                hit['amount'] = max(0.0, h)
        elif m in ('setinvulnerable', 'invulnerable'):
            if 'boss' in targets:
                b.invulnerable = str(arg(a, 'true', 'value', 'v', 'invulnerable', 'i')).lower() == 'true'
        elif m == 'suicide':
            b.hp = 0
        # SkillMore.switchCases: con cada objetivo, el primer caso cuya condición cumple (o todos con unique=false);
        # DEFAULT si ninguno
        elif m == 'switch':
            cases = [c.split('=', 1) for c in str(a.get('cases', '')).split(',') if '=' in c]
            unique = str(arg(a, 'true', 'unique', 'first')).lower() == 'true'
            for w in targets:
                any_hit, fallback = False, None
                for name, meta in cases:
                    ent = self.tree.get(meta.strip().lower())
                    if ent is None:
                        continue
                    if name.strip().upper() == 'DEFAULT':
                        fallback = meta.strip()
                        continue
                    if all(self.passes(c, self.cond(c, w, ctx)) for c in ent.get('target_conditions', [])):
                        any_hit = True
                        self.run_meta(meta.strip(), dict(ctx, targets=[w]))
                        if unique:
                            break
                if not any_hit and fallback:
                    self.run_meta(fallback, dict(ctx, targets=[w]))
        # SkillMore.consume: el jefe se cura «heal» (vida del pack) por cada jugador al que hace daño
        elif m == 'consume':
            if 'player' in targets:
                pack = float(self.prog['boss'].get('pack_health') or self.prog['boss']['health'])
                b.hp = min(b.maxhp, b.hp + num(arg(a, 1, 'heal', 'h'), 1) * b.maxhp / pack)
        # SkillMore.swap / teleportIn: el jefe y el jugador cambian de sitio / el jugador va a ese punto del jefe
        elif m in ('swap', 'tpswap'):
            if 'player' in targets:
                b.x, b.z, self.px, self.pz = self.px, self.pz, b.x, b.z
        elif m in ('teleportin', 'tpin', 'tpdir', 'tpi'):
            if 'player' in targets:
                v = [num(x, 0) for x in (str(arg(a, '0,0,0', 'vector', 'direction', 'dir', 'd', 'v')).split(',') + ['0', '0'])[:3]]
                r = math.radians(b.body)
                fx, fz = -math.sin(r), math.cos(r)  # SkillRuntime.dir(yaw)
                rx, rz = -math.sin(r + math.pi / 2), math.cos(r + math.pi / 2)
                bx, bz = (self.px, self.pz) if str(arg(a, 'false', 'targetasorigin', 'tao')).lower() == 'true' else (b.x, b.z)
                self.px, self.pz = bx + fx * v[0] + rx * v[2], bz + fz * v[0] + rz * v[2]
        # SkillMore.variableMath: la variable pasa a valer la cuenta (x = lo que vale)
        elif m in ('variablemath', 'mathvariable', 'varmath', 'mathvar'):
            var = str(arg(a, '', 'var', 'variable', 'name', 'key', 'k')).lower()
            if var and not var.startswith('target.'):
                name = var.split('.', 1)[1] if var.startswith(('caster.', 'skill.')) else var
                v = crunch(str(arg(a, 'x', 'equation', 'eq', 'e')).strip('"'), num(b.vars.get(name), 0))
                if v is not None and math.isfinite(v):
                    b.vars[name] = str(int(v)) if v == int(v) else str(v)
        # ~onSkillDamage (SkillRuntime.hurt → SkillMobs.skillDamage): el jefe da al jugador con una mecánica de daño
        elif m in ('damage', 'basedamage', 'percentdamage', 'hit') and 'player' in targets:
            if not self.in_skill_damage:
                self.in_skill_damage = True
                try:
                    self.trigger('onskilldamage')
                finally:
                    self.in_skill_damage = False
        # ModelEngine en la entidad (BossEntity.move, syncYaw; SkillModels.rootStep; setHitboxScale)
        elif m == 'move':
            if 'boss' in targets:
                side, front = num(arg(a, 0, 'side', 'x'), 0), num(arg(a, 0, 'front', 'z'), 0)
                ln = math.hypot(side, front)
                if ln > 1:
                    side, front = side / ln, front / ln
                v = b.speed() * 0.9 * num(arg(a, 1, 'speed'), 1)
                if str(arg(a, 'false', 'global', 'g')).lower() != 'true':
                    r = math.radians(b.body)
                    side, front = side * math.cos(r) - front * math.sin(r), front * math.cos(r) + side * math.sin(r)
                b.x += side * v
                b.z += front * v
                self.move_until = self.t + 2
        elif m == 'syncyaw':
            if 'boss' in targets:
                to = str(arg(a, 'yaw', 'target', 't', 'to')).lower()
                fr = str(arg(a, 'head', 'body', 'b', 'from', 'src')).lower()
                v = b.body if fr == 'body' else b.look  # (aquí la cabeza mira a donde la entidad)
                if to in ('yaw', 'all', 'head'):
                    b.look = v
                if to in ('body', 'all'):
                    b.body = v
        elif m == 'rootmotion':
            if 'boss' in targets:
                if arg(a, None, 'override', 'ov') is not None:
                    self.root_on = str(arg(a, 'false', 'override', 'ov')).lower() == 'true'
                if arg(a, None, 'weight', 'w') is not None:
                    self.root_w = num(arg(a, -1, 'weight', 'w'), -1)
                if arg(a, None, 'baseweight', 'bw') is not None:
                    self.root_bw = num(arg(a, -1, 'baseweight', 'bw'), -1)
        elif m == 'model':
            if 'boss' in targets and arg(a, None, 'scale', 'size') is not None:
                self.model_scale = num(arg(a, 1, 'scale', 'size'), 1) or 1.0
            if 'boss' in targets and arg(a, None, 'rootbone', 'root', 'rt'):
                self.root_bone = str(arg(a, '', 'rootbone', 'root', 'rt'))
            if 'boss' in targets and arg(a, None, 'hitboxscale', 'hbs') is not None:
                self.hb_scale = num(arg(a, 1, 'hitboxscale', 'hbs'), 1)
        elif m in ('scale', 'meg_scale'):
            if 'boss' in targets and str(arg(a, 'false', 'hitbox', 'h')).lower() == 'true':
                self.hb_scale = num(arg(a, 1, 'scale', 's'), 1)


_MODELS = {}


def load_model(mid):
    """El modelo convertido de un jefe (bosses/models/<id>.json), o None."""
    if not mid:
        return None
    if mid not in _MODELS:
        p = os.path.join(RES, 'models', mid + '.json')
        _MODELS[mid] = json.load(open(p, encoding='utf-8')) if os.path.exists(p) else None
    return _MODELS[mid]


def vfx_sample(kfs, t):
    """Valor de una pista del modelo (como VfxPose.sample)."""
    sys.path.insert(0, os.path.join(HERE, '..'))
    import vfx_bb
    return vfx_bb.sample(kfs, t)


def meta_key(name):
    return str(name).lower()


# Las de ModelEngine que, sin objetivo, van al propio modelo (igual que SkillRuntime.OWN_MODEL)
OWN_MODEL = {'bodyrotation', 'bodyclamp', 'model', 'segment', 'seg', 'partvis', 'partvisibility', 'brightness',
             'renderinit', 'defaultstate', 'state', 'animation', 'lockmodel', 'remapmodel', 'changepart', 'barset',
             'barcreate', 'setmodelscale', 'tint', 'modifystate', 'modifyanimation', 'modstate', 'statetoggle',
             'togglestate', 'headconfig', 'lockmodelhead', 'lockhead', 'hitboxconfig', 'setitemmodel',
             'linkitembone', 'setmodeltag', 'setmodeltagvisible', 'enchant', 'billboard', 'glowbone', 'cycleparts',
             'scale', 'meg_scale', 'ik', 'inversekinematics', 'ikchain', 'leashself', 'animatemodel', 'rootmotion',
             'changeparent', 'submodel', 'petrify', 'syncyaw', 'move'}


def run(boss_id, minutes, seed, verbose):
    s = Sim(boss_id, seed, verbose)
    # SkillMobs.spawned: ~onSpawn, ~onSpawnOrLoad, ~onReady (el jefe sale de su grieta) y ~onCombat
    for tr in ('onspawn', 'onspawnorload', 'onready', 'oncombat'):
        s.trigger(tr)
    # SkillMobs.tick: el cerebro se hace en su primer tick, ya con su modelo puesto (su escala)
    if SMART > 0:
        s.brain = Brain(s)
    end = int(minutes * 1200)
    while s.t < end and s.b.hp > 0:
        s.t += 1
        due = [x for x in s.tasks if x[0] <= s.t]
        s.tasks = [x for x in s.tasks if x[0] > s.t]
        for x in due:
            x[2]()
        s.player_step()
        s.boss_step()
        s.timers()
        for k in [k for k, v in s.b.auras.items() if v <= s.t]:
            del s.b.auras[k]
    return s


def attacks_of(prog):
    """Todas las animaciones de ataque que puede poner el pack (state{s=X} del árbol)."""
    out = set()
    for e in prog['tree'].values():
        for pm in e['mechs']:
            if pm['m'] in ('state', 'animation'):
                s = str(arg(pm.get('a', {}), '', 'state', 's'))
                if s and s not in NOT_ATTACKS and str(arg(pm.get('a', {}), 'false', 'remove', 'r')).lower() != 'true':
                    out.add(s)
    return out


def main():
    args = [a for a in sys.argv[1:]]
    minutes = float(args[args.index('--min') + 1]) if '--min' in args else 5
    seed = int(args[args.index('--seed') + 1]) if '--seed' in args else 1
    verbose = '-v' in args
    global OLD, SMART, SPEED, RHYTHM, NO_REACH
    OLD = '--antes' in args
    NO_REACH = '--sin-alcance' in args
    if '--dif' in args:
        SPEED, RHYTHM, SMART = PRESETS[args[args.index('--dif') + 1]]
    if '--smart' in args:
        SMART = float(args[args.index('--smart') + 1])
    skip = {args[i + 1] for i, a in enumerate(args) if a in ('--dif', '--min', '--seed', '--smart') and i + 1 < len(args)}
    bosses = [a for a in args if not a.startswith('-') and a not in skip and not re.match(r'^[\d.]+$', a)] or \
        [c['id'] for c in json.load(open(os.path.join(RES, 'catalog.json'), encoding='utf-8'))]
    for bid in bosses:
        s = run(bid, minutes, seed, verbose)
        if OLD and bid == 'esqueleto_torre':
            pass
        allk = attacks_of(s.prog)
        fired = s.b.fired
        never = sorted(allk - set(fired))
        total = sum(fired.values())
        mins = s.t / 1200
        print(f'{bid:16s} {s.t / 20:6.0f} s, vida {max(0, s.b.hp) / s.b.maxhp * 100:5.1f}%: {total} ataques '
              f'({total / max(mins, 1e-6):.1f}/min), {len(fired)} distintos de {len(allk)}')
        for k, v in sorted(fired.items(), key=lambda x: -x[1]):
            print(f'     {v:4d}  {k}')
        if never:
            print('     sin salir:', ', '.join(never))
        rep = sum(1 for i in range(1, len(s.b.timeline)) if s.b.timeline[i][1] == s.b.timeline[i - 1][1])
        punished = sum(1 for h in s.heals if h[1])
        print(f'     repite seguido: {rep} de {max(0, len(s.b.timeline) - 1)}; curas castigadas: {punished} de {len(s.heals)}'
              + (f'; combos: {s.brain.chains}; pasos atrás: {s.backsteps}' if s.brain else ''))
        if s.brain:
            for a in s.brain.attacks:
                tags = ','.join(k for k in ('gap', 'ranged', 'aoe', 'grab', 'rear') if getattr(a, k))
                if '-v' in sys.argv:
                    print(f'       cerebro: {a.name:44s} {a.min:4.1f}-{a.max:4.1f} {tags:18s} usos {a.uses:3d} entran {a.hits:3d}')
        if s.cancelled_hits:
            print(f'     golpes anulados por el pack (cancelevent/modifydamage/invulnerable): {s.cancelled_hits} de {s.hits}')
        if s.fireballs:
            print(f'     bolas de fuego del ghast: {s.fireballs}')
        if s.reach_fired:
            print(f'     golpes con alcance lanzados fuera de su alcance (al aire): {s.air} de {s.reach_fired}')
        if s.front_fired:
            print(f'     golpes por delante lanzados sin mirarle (más de 50°): {s.sideways} de {s.front_fired}')
        if s.melee_hits:
            print(f'     golpes de Minecraft (~onAttack) que entran: {s.melee_hits} de {s.melee_tries}')
        if s.unknown:
            print('     condiciones sin simular:', ', '.join(sorted(s.unknown)))


if __name__ == '__main__':
    main()
