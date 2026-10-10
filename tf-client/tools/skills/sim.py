"""Simulador de las skills de clase sin el juego: ejecuta el programa de una clase (assets/tfclient/skills/classes/<id>.json)
con las mismas reglas que el motor del mod (skills/SkillRuntime.java y compañía) en un mundo de prueba (quien lanza en
el centro mirando al sur y tres zombis delante), y:

  - comprueba que todo se puede ejecutar: mecánicas, objetivos y condiciones que el motor no conoce, skills o mobs que
    faltan, bucles sin fin, errores;
  - cuenta lo que pasa (efectos que salen, daño, sonidos...);
  - saca una vista previa animada (WebP) para la web: el jugador, los efectos con sus modelos y las partículas.

    python3 tools/skills/sim.py <clase> [skill] [--webp carpeta] [--ticks 100]
    python3 tools/skills/sim.py --todas            (revisa todas las clases del mod)

Las reglas que copia del mod están marcadas con el nombre del método de Java. Si cambias el motor, cambia esto igual.
"""
import json
import math
import os
import random
import re
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)
sys.path.insert(0, HERE)
sys.path.insert(0, TOOLS)
import jsonmodel as J  # noqa: E402
import vfx_bb as B  # noqa: E402
import vfx_render as R  # noqa: E402
from vfx_preview import PARTICLE_COLORS, hex_rgb  # noqa: E402

RES = os.path.normpath(os.path.join(TOOLS, '..', 'src', 'main', 'resources', 'assets'))
CLASSES = os.path.join(RES, 'tfclient', 'skills', 'classes')

# Lo que el motor sabe hacer (SkillRuntime.apply, SkillTargets.resolve, SkillConds.test)
NOOP = {'cancelevent', 'gcd', 'setai', 'animatearmorstand', 'bodyrotation', 'brightness', 'lockmodel', 'bodyclamp',
        'setmodelscale', 'enchant', 'setgravity', 'setnoai', 'setspeed', 'settarget', 'threat',
        'runaitargetselector', 'runaigoalselector', 'modifyglobalscore', 'setglobalscore',
        'feed', 'playanimation', 'swing', 'remapmodel', 'mountmodel', 'glow', 'setcollidable', 'setinvulnerable',
        'setrotation', 'mount', 'mounttarget', 'dismount'}
KNOWN_TARGETS = {'self', 'caster', 's', 'boss', 'mob', 'owner', 'parent', 'summoner', 'trigger', 'target', 't',
                 'targetedtarget', 'tt', 'targetedentity', 'origin', 'o', 'source', 'selflocation', 'casterlocation', 'sl',
                 'bosslocation', 'moblocation', 'selfeyelocation', 'eyelocation', 'casterseyelocation', 'boss_eye', 'sel',
                 'targetlocation', 'tl', 'targetloc', 'targetedlocation', 'targetedloc', 'forward', 'f',
                 'projectileforward', 'pf', 'entitiesnearorigin', 'eno', 'livingentitiesnearorigin', 'leno',
                 'playersnearorigin', 'pno', 'entitiesinradius', 'eir', 'livingentitiesinradius', 'leir', 'livinginradius',
                 'allinradius', 'air', 'entitiesinring', 'eirr', 'mobsinradius', 'mir', 'playersinradius', 'pir',
                 'playersinworld', 'world', 'pinr', 'nearestplayer', 'np', 'ring', 'circle', 'randomlocationsnearcaster',
                 'rlnc', 'randomlocationsnearorigin', 'rlno', 'modelpart', 'mp', 'location', 'l', 'spawnlocation'}
KNOWN_CONDS = {'mythicmobtype', 'mmt', 'mobtype', 'owner', 'isowner', 'isparent', 'parent', 'hasaura', 'hasbuff',
               'hasaurastacks', 'aurastacks', 'crouching', 'sneaking', 'issneaking', 'sprinting', 'issprinting', 'onground',
               'grounded', 'inblock', 'blocktype', 'inside', 'standingon', 'variableisset', 'varisset', 'varset',
               'variableequals', 'varequals', 'variableeq', 'vareq', 'variableinrange', 'varinrange', 'varrange', 'offgcd',
               'gcd', 'incombat', 'hasai', 'hastarget', 'itemissimilar', 'holding', 'wearing', 'haspermission',
               'lineofsight', 'los', 'world', 'biome', 'dimension', 'moonphase', 'stance', 'hasowner', 'targetwithin',
               'targetinlineofsight', 'targetnotwithin', 'distance', 'distancefromorigin', 'health', 'hp',
               'healthpercent', 'hppercent', 'hpp', 'entitytype', 'type', 'entitytypes', 'isplayer', 'player',
               'ismonster', 'monster', 'isliving', 'living', 'isburning', 'burning', 'onfire', 'inwater', 'isinwater',
               'haspotioneffect', 'haspotion', 'isskill', 'chance', 'altitude', 'height', 'dead', 'isdead'}

PARTICLE_NAMES = {'reddust': 'dust', 'redstone': 'dust', 'smoke_normal': 'smoke', 'smoke_large': 'large_smoke',
                  'explosion_large': 'explosion', 'explosion_huge': 'explosion_emitter', 'explosion_normal': 'poof',
                  'spell_witch': 'witch', 'spell_instant': 'instant_effect', 'spell': 'effect', 'enchantment_table': 'enchant',
                  'water_drop': 'rain', 'totem': 'totem_of_undying', 'fireworks_spark': 'firework',
                  'villager_happy': 'happy_villager', 'drip_water': 'dripping_water', 'drip_lava': 'dripping_lava',
                  'block_crack': 'block', 'blockcrack': 'block', 'block_dust': 'block', 'crit_magic': 'enchanted_hit',
                  'magic_crit': 'enchanted_hit', 'water_splash': 'splash'}
BLOCK_COLORS = {'ice': '#a8d8ff', 'packed_ice': '#9cc8f8', 'blue_ice': '#74b4ff', 'snow_block': '#f4fbff', 'stone': '#8a8a8a',
                'dirt': '#7a5a3a', 'redstone_block': '#c81818', 'obsidian': '#2a1840', 'gold_block': '#ffd83a',
                'lava': '#ff7a1a', 'magma_block': '#c8501a', 'netherrack': '#7a2a2a', 'sand': '#e0d29a',
                'cobblestone': '#7a7a7a', 'glass': '#cfeeff', 'light_blue_concrete': '#3ab0e0'}


def num(s, default=0.0):
    """SkillRuntime.num"""
    if s is None:
        return default
    s = str(s).strip().strip('"').strip("'")
    if not s:
        return default
    try:
        return float(s)
    except ValueError:
        pass
    e = evaluate(s)
    if e is not None:
        return e
    m = re.match(r'^<random\.(?:float|int)\.(-?[\d.]+)to(-?[\d.]+)>', s)
    if m:
        a, b = float(m.group(1)), float(m.group(2))
        return a + random.random() * (b - a)
    m = re.match(r'^(-?[\d.]+)to(-?[\d.]+)$', s)
    if m:
        a, b = float(m.group(1)), float(m.group(2))
        return a + random.random() * (b - a)
    m = re.match(r'^-?\d+(\.\d+)?', s)
    if m:
        return float(m.group())
    return default


def evaluate(s):
    if not re.fullmatch(r'[\d\s.+\-*/()]+', s) or not re.search(r'\d', s):
        return None
    try:
        return float(eval(s, {'__builtins__': {}}, {}))  # solo números y + - * / ( )
    except Exception:  # noqa: BLE001
        return None


def boolean(s):
    return str(s).strip().lower() in ('true', '1', 'yes')


def arg(a, default, *keys):
    for k in keys:
        if k in a:
            return a[k]
    return default


def direction(yaw, pitch):
    y, p = math.radians(yaw), math.radians(pitch)
    return np.array([-math.sin(y) * math.cos(p), -math.sin(p), math.cos(y) * math.cos(p)])


def yaw_of(d):
    return math.degrees(math.atan2(-d[0], d[2]))


def pitch_of(d):
    h = math.hypot(d[0], d[2])
    return math.degrees(-math.atan2(d[1], h))


# ---------------------------------------------------------------------------------------------------------------
# Mundo de prueba
# ---------------------------------------------------------------------------------------------------------------

class Entity:
    def __init__(self, sim, kind, pos, yaw=0.0, name=''):
        self.sim, self.kind, self.name = sim, kind, name
        self.pos = np.array(pos, float)
        self.yaw, self.pitch = yaw, 0.0
        self.health = 20.0
        self.alive = True
        self.vel = np.zeros(3)
        self.effects = {}
        self.uid = id(self)

    def eye(self):
        return self.pos + [0, 1.62, 0]


class Actor:
    """SkillActor"""
    next_id = 1

    def __init__(self, sim, mob, pos, yaw, pitch, owner):
        self.id = Actor.next_id
        Actor.next_id += 1
        self.sim, self.mob = sim, mob
        self.pos = np.array(pos, float)
        self.yaw, self.pitch = yaw, pitch
        self.owner = owner
        self.age = 0
        self.alive = True
        self.head = mob.get('head_model') if mob else None
        self.hand = None
        self.me = None
        self.anim, self.anim_start, self.anim_speed = None, 0, 1.0
        self.timers = []
        self.follow = None
        self.swaps, self.vis, self.tint = {}, {}, None
        self.uid = ('actor', self.id)
        self.name = mob.get('_name', '') if mob else ''


class Who:
    def __init__(self, entity=None, actor=None):
        self.entity, self.actor = entity, actor

    def obj(self):
        return self.entity or self.actor

    def pos(self):
        return self.obj().pos

    def eye(self):
        return self.entity.eye() if self.entity else self.actor.pos + [0, 1.6, 0]

    def yaw(self):
        return self.obj().yaw

    def pitch(self):
        return self.obj().pitch

    def alive(self):
        return self.obj().alive

    def owner(self):
        return self.actor.owner if self.actor else None

    def player(self):
        w = self
        for _ in range(16):
            if w is None:
                return None
            if w.entity is not None and w.entity.kind == 'player':
                return w.entity
            w = w.owner()
        return None

    def key(self):
        return self.obj().uid

    def same(self, o):
        return o is not None and self.key() == o.key()

    def forward(self):
        return direction(self.yaw(), self.pitch())


class Tgt:
    def __init__(self, who=None, at=None):
        self.who, self.at = who, None if at is None else np.array(at, float)
        self.power = 1.0

    def pos(self):
        return self.who.pos() if self.who else self.at

    def entity(self):
        return self.who.entity if self.who else None


class Ctx:
    def __init__(self, cls, caster):
        self.cls, self.caster = cls, caster
        self.trigger = None
        self.origin = None
        self.targets = None
        self.aim = None
        self.power = 1.0
        self.dir = None
        self.depth = 0
        self.vars = None
        self.mods = {}

    def copy(self):
        c = Ctx(self.cls, self.caster)
        c.trigger, c.origin, c.targets, c.aim, c.power = self.trigger, self.origin, self.targets, self.aim, self.power
        c.dir, c.depth, c.vars, c.mods = self.dir, self.depth + 1, self.vars, self.mods
        return c

    def as_(self, who):
        c = Ctx(self.cls, who)
        c.trigger, c.origin, c.aim, c.power, c.depth, c.mods = self.caster, who.pos().copy(), self.aim, self.power, \
            self.depth + 1, self.mods
        return c


class Script:
    def __init__(self, ctx, mechs):
        self.ctx, self.mechs = ctx, mechs
        self.pc, self.wait = 0, 0


class Sim:
    """El mundo y el motor (SkillRuntime + SkillMovers + SkillAuras + SkillModels)."""

    def __init__(self, cls, seed=7):
        random.seed(seed)
        Actor.next_id = 1
        self.cls = cls
        self.tree = {k.lower(): v for k, v in cls['tree'].items()}
        self.mobs = {}
        for k, v in cls['mobs'].items():
            v = dict(v)
            v['_name'] = k
            self.mobs[k.lower()] = v
        self.clock = 0
        self.scripts, self.tasks, self.actors, self.movers, self.auras = [], [], [], [], []
        self.states = {}
        self.player = Entity(self, 'player', [0, 0, 0], 0.0, 'Jugador')
        self.mobs_world = [Entity(self, 'zombie', [0, 0, 6], 180, 'Zombi'), Entity(self, 'zombie', [2.5, 0, 8], 180, 'Zombi 2'),
                           Entity(self, 'zombie', [-2.5, 0, 7.5], 180, 'Zombi 3')]
        self.entities = [self.player] + self.mobs_world
        self.particles = []
        self.sounds = []
        self.damage = []
        self.problems = []
        self.counts = {}
        self.disguise = None
        # Lo que lleva en la mano (las clases que lo miran: el guantelete del Dragón Rojo empieza en el básico)
        hand = cls.get('hand_items') or {}
        self.hand_items = {k.lower(): v for k, v in hand.items()}
        self.hand = min(self.hand_items, key=len) if self.hand_items else None
        self.passives = [s for s in cls['skills'] if s.get('passive') and s['passive'].get('type') == 'TIMER' and s.get('entry')]

    # --- estado de cada quién (SkillRuntime.state)
    def state(self, who):
        return self.states.setdefault(who.key(), {'auras': {}, 'vars': {}, 'cd': {}})

    def problem(self, text):
        if text not in self.problems:
            self.problems.append(text)

    def count(self, key):
        self.counts[key] = self.counts.get(key, 0) + 1

    def meta(self, name):
        return self.tree.get(str(name or '').strip().lower())

    # --- entrada (SkillRuntime.cast)
    def cast(self, skill):
        meta = self.meta(skill.get('entry'))
        if meta is None:
            self.problem(f'la skill {skill["id"]} no tiene entrada')
            return False
        ctx = Ctx(self.cls, Who(entity=self.player))
        ctx.origin = self.player.pos.copy()
        ctx.aim = self.mobs_world[0]
        ctx.vars = {}
        ctx.mods = {k.lower(): float(v) for k, v in (skill.get('mods') or {}).items() if v is not None}
        return self.run_meta(meta, ctx)

    def run_meta(self, meta, ctx):
        """SkillRuntime.runMeta"""
        if meta is None or ctx.depth > 40 or len(self.scripts) > 4000:
            if ctx.depth > 40:
                self.problem('cadena de skills demasiado larga (¿se llama a sí misma?)')
            return False
        name = meta.get('_name', '')
        if meta.get('cooldown'):
            st = self.state(ctx.caster)
            until = st['cd'].get(name)
            if until is not None and until > self.clock:
                return False
            st['cd'][name] = self.clock + round(num(meta['cooldown']) * 20)
        for c in meta.get('conditions') or []:
            r = self.test(c, ctx.caster, ctx, None)
            out = self.outcome(c, r, ctx)
            if out == 'stop':
                return False
            if out == 'replaced':
                return True
        if meta.get('trigger_conditions') and ctx.trigger is not None:
            for c in meta['trigger_conditions']:
                if self.outcome(c, self.test(c, ctx.trigger, ctx, None), ctx) != 'go':
                    return False
        run = ctx
        if meta.get('target_conditions'):
            ins = ctx.targets if ctx.targets is not None else [Tgt(ctx.caster)]
            outs = []
            replaced = False
            for t in ins:
                cp = Tgt(t.who, t.at)
                cp.power = t.power
                keep = True
                for c in meta['target_conditions']:
                    r = self.test(c, t.who, ctx, t.at)
                    act = c.get('v', 'true')
                    if act.startswith('power'):
                        if r:
                            cp.power *= num(act[5:].strip(), 1)
                        continue
                    # castinstead / orElseCast: ese objetivo se va a la otra skill; los demás siguen
                    alt = act.startswith('castinstead')
                    if alt or act.startswith('orelsecast'):
                        if r == alt:
                            other = ctx.copy()
                            other.targets = [cp]
                            self.run_named(act[len('castinstead' if alt else 'orelsecast'):].strip(), other)
                            replaced = True
                            keep = False
                            break
                        continue
                    if not self.passes(act, r):
                        keep = False
                        break
                if keep:
                    outs.append(cp)
            if not outs:
                return replaced
            run = ctx.copy()
            run.depth = ctx.depth
            run.targets = outs
        s = Script(run, meta['mechs'])
        if not self.run_script(s):
            self.scripts.append(s)
        return True

    def outcome(self, c, r, ctx):
        act = c.get('v', 'true')
        if act.startswith('castinstead'):
            if not r:
                return 'go'
            self.run_named(act[len('castinstead'):].strip(), ctx)
            return 'replaced'
        if act.startswith('orelsecast'):
            if r:
                return 'go'
            self.run_named(act[len('orelsecast'):].strip(), ctx)
            return 'replaced'
        if act.startswith('power'):
            if r:
                ctx.power *= num(act[5:].strip(), 1)
            return 'go'
        return 'go' if self.passes(act, r) else 'stop'

    @staticmethod
    def passes(act, r):
        if act in ('false', 'cancel'):
            return not r
        return r

    def run_named(self, name, ctx):
        meta = self.meta(name)
        if meta is None:
            self.problem(f'falta la skill {name}')
            return
        self.run_meta(meta, ctx.copy())

    def run_script(self, s):
        """Script.run"""
        while s.pc < len(s.mechs):
            if s.wait > 0:
                return False
            if s.ctx.caster.actor is not None and not s.ctx.caster.alive():
                return True
            m = s.mechs[s.pc]
            s.pc += 1
            if m['m'] == 'delay':
                s.wait = int(max(0, num(arg(m.get('a', {}), '0', '_', 'ticks', 't', 'd'))))
                continue
            try:
                self.exec(m, s.ctx)
            except Exception as e:  # noqa: BLE001
                self.problem(f'error en {m["m"]}: {e}')
        return True

    def later(self, ticks, fn):
        if ticks <= 0:
            fn()
        else:
            self.tasks.append((self.clock + ticks, fn))

    # --- una línea (SkillRuntime.exec)
    def exec(self, m, ctx):
        if any('<' in str(v) for v in list(m.get('a', {}).values()) + list((m.get('ta') or {}).values())):
            m = self.resolve(m, ctx)
        if m.get('ch', 1.0) < 1.0 and random.random() >= m['ch']:
            return
        for c in m.get('c') or []:
            r = self.test({'m': c['m'], 'a': c.get('a', {}), 'v': 'true'}, ctx.caster, ctx, None)
            if c.get('not') == r:
                return
        a = m.get('a', {})
        delay = int(num(a.get('delay'), 0))
        repeat = int(num(arg(a, '0', 'repeat'), 0))
        every = int(max(1, num(arg(a, '1', 'repeatinterval', 'repeati', 'ri'), 1)))

        def once():
            if ctx.caster.actor is not None and not ctx.caster.alive() and m['m'] != 'remove':
                return
            self.apply(m, ctx, self.targets(m, ctx))
        if delay <= 0 and repeat <= 0:
            once()
            return
        self.later(delay, once)
        for k in range(1, min(repeat, 400) + 1):
            self.later(delay + k * every, once)

    def resolve(self, m, ctx):
        """SkillVars.resolve"""
        first = ctx.targets[0] if ctx.targets else None
        m = dict(m)
        m['a'] = {k: self.text(v, ctx, first) for k, v in m.get('a', {}).items()}
        m['ta'] = {k: self.text(v, ctx, first) for k, v in (m.get('ta') or {}).items()}
        return m

    def text(self, s, ctx, target):
        s = str(s)
        if '<' not in s:
            return s

        def value(mt):
            t = mt.group(1).strip().lower()
            r = re.fullmatch(r'random\.float\.(-?[\d.]+)to(-?[\d.]+)', t)
            if r:
                a, b = float(r.group(1)), float(r.group(2))
                return str(round(a + random.random() * (b - a), 3))
            r = re.fullmatch(r'random\.(?:int\.)?(-?\d+)to(-?\d+)', t)
            if r:
                return str(random.randint(int(r.group(1)), int(r.group(2))))
            for pre in ('modifier.', 'spell.mod.', 'skill.mod.', 'mod.'):
                if t.startswith(pre):
                    k = t[len(pre):]
                    if k not in ctx.mods:
                        self.problem(f'la variable <{t}> no tiene valor en la skill')
                    return fmt(ctx.mods.get(k, 0))
            if t.startswith('skill.var.'):
                return str((ctx.vars or {}).get(t[10:], '0'))
            if t == 'skill.power':
                return fmt(ctx.power)
            if t == 'skill.targets':
                return str(len(ctx.targets or [1]))
            if t.startswith('skill.'):
                k = t[6:]
                if k in ctx.mods:
                    return fmt(ctx.mods[k])
                return str((ctx.vars or {}).get(k, '0'))
            if t.startswith('stat.'):
                return '0'
            if t.startswith('caster.'):
                who, rest = ctx.caster, t[7:]
            elif t.startswith('target.'):
                who, rest = (target.who if target else None), t[7:]
                if who is None and target is not None:
                    return loc(target.at, rest[2:] if rest.startswith('l.') else rest)
            elif t.startswith('trigger.'):
                who, rest = ctx.trigger, t[8:]
            else:
                return mt.group(0)  # no es una variable (un color en un nombre): se queda
            if who is None:
                return '0'
            if rest.startswith('owner.'):
                who = who.owner()
                rest = rest[6:]
                if who is None:
                    return '0'
            if rest.startswith('var.'):
                return str(self.state(who)['vars'].get(rest[4:], '0'))
            if rest.startswith('l.'):
                k = rest[2:]
                if k.startswith('yaw'):
                    return fmt(who.yaw())
                if k.startswith('pitch'):
                    return fmt(who.pitch())
                return loc(who.pos(), k)
            if rest == 'name':
                return who.obj().name
            if rest in ('hp', 'mhp'):
                return '20'
            return '0'
        out = re.sub(r'<([^<>]+)>', value, s)
        if re.search(r'\d\s*[*/+\-]\s*[\d(]', out) and re.fullmatch(r'[\d\s.+\-*/()]+', out):
            e = evaluate(out)
            if e is not None:
                return fmt(e)
        return out

    # --- objetivos (SkillTargets)
    def targets(self, m, ctx):
        t = m.get('t')
        if t is None:
            return ctx.targets if ctx.targets else [Tgt(ctx.caster)]
        ta = m.get('ta') or {}
        y = num(arg(ta, '0', 'y', 'yoffset', 'yo'), 0)
        c = ctx.caster
        if t not in KNOWN_TARGETS:
            self.problem(f'objetivo @{t} no soportado')
            return [Tgt(c)]
        if t in ('self', 'caster', 's', 'boss', 'mob'):
            return [Tgt(c)]
        if t == 'owner':
            return [Tgt(c.owner() or c)]
        if t in ('parent', 'summoner'):
            return [Tgt(c.owner())] if c.owner() else []
        if t == 'trigger':
            return [Tgt(ctx.trigger or c)]
        if t in ('target', 't', 'targetedtarget', 'tt', 'targetedentity'):
            return self.target(ctx)
        if t in ('origin', 'o', 'source'):
            return [Tgt(at=self.origin(ctx) + [0, y, 0])]
        if t in ('selflocation', 'casterlocation', 'sl', 'bosslocation', 'moblocation', 'spawnlocation'):
            return [Tgt(at=c.pos() + [0, y, 0])]
        if t in ('selfeyelocation', 'eyelocation', 'casterseyelocation', 'boss_eye', 'sel'):
            return [Tgt(at=c.eye() + [0, y, 0])]
        if t in ('targetlocation', 'tl', 'targetloc', 'targetedlocation', 'targetedloc'):
            tg = self.target(ctx)
            if tg:
                return [Tgt(at=tg[0].pos() + [0, y, 0])]
            for x in ctx.targets or []:
                if x.at is not None:
                    return [Tgt(at=x.at + [0, y, 0])]
            return [Tgt(at=c.pos() + c.forward() * 8 + [0, y, 0])]
        if t in ('forward', 'f'):
            return [Tgt(at=self.forward(ta, ctx, y))]
        if t in ('projectileforward', 'pf'):
            d = ctx.dir if ctx.dir is not None else c.forward()
            f = num(arg(ta, '1', 'f', 'forward'), 1)
            return [Tgt(at=self.origin(ctx) + d / max(1e-6, np.linalg.norm(d)) * f + [0, y, 0])]
        r = min(64, num(arg(ta, '5', 'r', 'radius'), 5))
        if t in ('entitiesnearorigin', 'eno', 'livingentitiesnearorigin', 'leno'):
            return self.in_radius(ctx, self.origin(ctx), r, False, False)
        if t in ('playersnearorigin', 'pno'):
            return self.in_radius(ctx, self.origin(ctx), r, True, False)
        if t in ('entitiesinradius', 'eir', 'livingentitiesinradius', 'leir', 'livinginradius', 'allinradius', 'air',
                 'entitiesinring', 'eirr'):
            return self.in_radius(ctx, c.pos(), r, False, False)
        if t in ('mobsinradius', 'mir'):
            return self.in_radius(ctx, c.pos(), r, False, True)
        if t in ('playersinradius', 'pir', 'playersinworld', 'world', 'pinr', 'nearestplayer', 'np'):
            return self.in_radius(ctx, c.pos(), r, True, False)
        if t in ('ring', 'circle'):
            pts = int(max(1, min(128, num(arg(ta, '8', 'points', 'p'), 8))))
            rr = num(arg(ta, '5', 'radius', 'r'), 5)
            return [Tgt(at=c.pos() + direction(c.yaw() + 360 * i / pts, 0) * rr + [0, y, 0]) for i in range(pts)]
        if t in ('randomlocationsnearcaster', 'rlnc', 'randomlocationsnearorigin', 'rlno'):
            amount = int(max(1, min(64, num(arg(ta, '1', 'amount', 'a'), 1))))
            base = self.origin(ctx) if 'origin' in t or t == 'rlno' else c.pos()
            out = []
            for _ in range(amount):
                ang, d = random.random() * math.pi * 2, math.sqrt(random.random()) * r
                out.append(Tgt(at=base + [math.cos(ang) * d, y, math.sin(ang) * d]))
            return out
        if t in ('modelpart', 'mp'):
            return [Tgt(at=self.model_part(ta, ctx) + [0, y, 0])]
        return [Tgt(at=c.pos())]

    def origin(self, ctx):
        return ctx.origin if ctx.origin is not None else ctx.caster.pos()

    def target(self, ctx):
        if ctx.aim is not None and ctx.aim.alive:
            return [Tgt(Who(entity=ctx.aim))]
        for t in ctx.targets or []:
            if t.who is not None and t.who.entity is not None and not t.who.same(ctx.caster):
                return [t]
        return []

    def forward(self, ta, ctx, y):
        c = ctx.caster
        f = num(arg(ta, '5', 'f', 'forward', 'amount', 'a'), 5)
        side = num(arg(ta, '0', 'sideoffset', 'so', 'side', 's'), 0)
        lock = boolean(arg(ta, 'false', 'lockpitch', 'lp')) or c.actor is not None
        d = direction(c.yaw(), 0 if lock else c.pitch())
        right = direction(c.yaw() + 90, 0)
        return c.pos() + d * f + right * side + [0, y, 0]

    def in_radius(self, ctx, center, r, players, mobs_only):
        out = []
        for e in self.entities:
            if not e.alive or (ctx.caster.entity is e):
                continue
            if players and e.kind != 'player':
                continue
            if mobs_only and e.kind == 'player':
                continue
            if np.linalg.norm(e.pos - center) <= r or np.linalg.norm(e.pos + [0, 0.9, 0] - center) <= r:
                out.append(Tgt(Who(entity=e)))
        if not players:
            for a in self.actors:
                if a.alive and a.follow is None and ctx.caster.actor is not a and np.linalg.norm(a.pos - center) <= r:
                    out.append(Tgt(Who(actor=a)))
        return out

    def model_part(self, ta, ctx):
        part = arg(ta, None, 'pid', 'p', 'partid', 'part')
        a = ctx.caster.actor or (self.disguise if ctx.caster.entity is self.player else None)
        if a is None or a.me is None or part is None:
            return ctx.caster.pos().copy()
        model = self.model(a.me)
        if model is None:
            return ctx.caster.pos().copy()
        bone = next((i for i, b in enumerate(model[0]['bones']) if b['id'].lower() == part.lower()), -1)
        if bone < 0:
            self.problem(f'@modelpart: el modelo {a.me} no tiene el hueso {part}')
            return ctx.caster.pos().copy()
        mats = B.pose(model[0], a.anim if a.anim in model[0]['anims'] else ('idle' if 'idle' in model[0]['anims'] else None),
                      (a.age - a.anim_start) / 20 * a.anim_speed)
        w = actor_matrix(a.pos, a.yaw) @ np.array(mats[bone])
        return w[:3, 3]

    # --- condiciones (SkillConds.test)
    def test(self, c, who, ctx, at):
        m = c['m']
        neg = m.startswith('!')
        m = m.lstrip('!')
        a = c.get('a', {})
        if m not in KNOWN_CONDS:
            self.problem(f'condición {m} no soportada (se da por cumplida)')
            return not neg
        e = who.entity if who else None
        r = True
        if m in ('mythicmobtype', 'mmt', 'mobtype'):
            types = arg(a, '', 'types', 'type', 't', 'mobtypes', 'm')
            name = who.actor.name if who is not None and who.actor is not None else ''
            r = any(x.strip().lower() == name.lower() for x in types.split(','))
        elif m in ('owner', 'isowner'):
            r = who is not None and ((ctx.caster.owner() is not None and ctx.caster.owner().same(who))
                                     or (e is not None and e is ctx.caster.player() and ctx.caster.actor is not None))
        elif m in ('isparent', 'parent'):
            r = who is not None and ctx.caster.owner() is not None and ctx.caster.owner().same(who)
        elif m in ('hasaura', 'hasbuff', 'hasaurastacks', 'aurastacks'):
            name = arg(a, None, 'auraname', 'aura', 'name', 'n', 'buffname', 'b')
            aura = self.state(who)['auras'].get(str(name).lower()) if who and name else None
            stacks = aura['stacks'] if aura and aura['alive'] else 0
            rng = arg(a, None, 'stacks', 's', 'amount', 'a')
            r = stacks > 0 if rng is None else in_range(stacks, rng)
        elif m in ('variableisset', 'varisset', 'varset'):
            var = arg(a, None, 'var', 'variable', 'name', 'key', 'k')
            r = var is not None and var_name(var) in self.var_scope(var, ctx, who)
        elif m in ('variableequals', 'varequals', 'variableeq', 'vareq'):
            var = arg(a, None, 'var', 'variable', 'name', 'key', 'k')
            v = self.var_scope(var, ctx, who).get(var_name(var)) if var else None
            want = str(arg(a, '', 'value', 'val', 'v')).strip('"')
            r = v is not None and (str(v).lower() == want.lower() or num(v, -1e9) == num(want, -2e9))
        elif m in ('variableinrange', 'varinrange', 'varrange'):
            var = arg(a, None, 'var', 'variable', 'name', 'key', 'k')
            v = self.var_scope(var, ctx, who).get(var_name(var)) if var else None
            r = v is not None and in_range(num(v), arg(a, '>0', 'value', 'val', 'v', 'range'))
        elif m in ('crouching', 'sneaking', 'issneaking', 'sprinting', 'issprinting', 'isburning', 'burning', 'onfire',
                   'inwater', 'isinwater', 'haspotioneffect', 'haspotion', 'dead', 'isdead'):
            r = False
        elif m in ('targetwithin', 'targetinlineofsight', 'targetnotwithin'):
            d = num(arg(a, '16', 'distance', 'd'), 16)
            within = ctx.aim is not None and np.linalg.norm(ctx.aim.pos - ctx.caster.pos()) <= d
            r = (m == 'targetnotwithin') != within
        elif m in ('isplayer', 'player'):
            r = e is not None and e.kind == 'player'
        elif m in ('ismonster', 'monster'):
            r = e is not None and e.kind != 'player'
        elif m in ('isliving', 'living'):
            r = e is not None
        elif m in ('entitytype', 'type', 'entitytypes'):
            r = e is not None and any(x.strip().lower() == e.kind for x in arg(a, '', 'types', 'type', 't').split(','))
        elif m in ('blocktype', 'inblock', 'inside', 'standingon'):
            r = False
        elif m in ('itemissimilar', 'holding'):
            r = e is self.player and self.hand is not None and self.hand == str(arg(a, '', 'i', 'item', 'material', 'm')).lower()
        return r != neg

    def var_scope(self, var, ctx, who):
        v = str(var).lower()
        if v.startswith('skill.'):
            if ctx.vars is None:
                ctx.vars = {}
            return ctx.vars
        if v.startswith('target.') and who is not None:
            return self.state(who)['vars']
        if v.startswith('global.'):
            return self.states.setdefault('global', {'auras': {}, 'vars': {}, 'cd': {}})['vars']
        return self.state(ctx.caster)['vars']

    # --- mecánicas (SkillRuntime.apply)
    def apply(self, m, ctx, targets):
        name = m['m']
        a = m.get('a', {})
        self.count(name)
        if name in ('skill', 'metaskill', 'cast', 's'):
            names = arg(a, None, 's', 'skill', 'skills', '$skill', 'spell')
            for n in str(names or '').split(','):
                self.call(n.strip().split(' ')[0], ctx, targets)
        elif name == 'randomskill':
            opts = []
            for part in str(arg(a, '', 'skills', 's')).split(','):
                p = part.strip().split()
                if p:
                    opts.append((p[0], max(0, num(p[1], 1)) if len(p) > 1 else 1))
            if opts:
                total = sum(w for _, w in opts)
                r = random.random() * total
                for n, w in opts:
                    r -= w
                    if r <= 0:
                        self.call(n, ctx, targets)
                        break
        elif name == 'skillsequence':
            for n in str(arg(a, '', 'skills', 's')).split(','):
                self.call(n.strip(), ctx, targets)
        elif name in ('sudoskill', 'sudo'):
            for t in targets:
                if t.who is not None:
                    c = ctx.as_(t.who)
                    self.run_meta(self.meta(arg(a, None, 's', 'skill')), c)
        elif name in ('effect:particles', 'particles', 'particle', 'e:p', 'effect:particle', 'effect:particlering',
                      'particlering', 'e:pr', 'effect:particlesphere', 'particlesphere', 'e:ps', 'effect:particleorbital',
                      'particleorbital', 'e:po', 'effect:particleline', 'particleline', 'e:pl', 'effect:particlebox',
                      'particlebox'):
            self.particles_fx(name, a, ctx, targets)
        elif name in ('effect:sound', 'sound', 'e:s'):
            s = arg(a, None, 'sound', 's')
            if s and not (s.startswith('tfclient:') or s.startswith('minecraft:')):
                self.problem(f'sonido sin convertir: {s}')
            for t in targets:
                self.sounds.append((self.clock, s, t.pos().copy()))
        elif name in ('effect:lightning', 'lightning', 'effect:blockmask', 'blockmask', 'effect:blockwave', 'blockwave'):
            for t in targets:
                self.spawn_particles('flash' if 'lightning' in name else 'block', t.pos() + [0, 0.2, 0], 12, 1.2, 0.1, 0.05,
                                     arg(a, 'stone', 'material', 'm', 'block', 'b'))
        elif name == 'summon':
            self.summon(a, ctx, targets)
        elif name == 'remove':
            for t in targets:
                if t.who is not None and t.who.actor is not None:
                    t.who.actor.alive = False
        elif name == 'equip':
            slot = str(arg(a, 'HEAD', 'slot')).upper()
            item = str(arg(a, '', 'item', 'i')).split(':')[0].lower()
            for t in targets:
                if t.who is not None and t.who.actor is not None:
                    if slot in ('HEAD', '4', 'HELMET'):
                        t.who.actor.head = a.get('model')
                    elif slot in ('HAND', '0', 'MAINHAND'):
                        t.who.actor.hand = a.get('model')
                    if not a.get('model') and slot in ('HEAD', '4', 'HELMET', 'HAND', '0', 'MAINHAND'):
                        self.problem(f'equip sin modelo: {a.get("item")}')
                elif t.who is not None and t.who.entity is self.player and slot in ('HAND', '0', 'MAINHAND'):
                    if self.hand in self.hand_items and item in self.hand_items:
                        self.hand = item
        elif name == 'model':
            self.model_mech(a, ctx, targets)
        elif name in ('state', 'animation'):
            s = arg(a, None, 'state', 's', 'animation', 'a')
            for t in targets:
                h = self.holder(t)
                if h is None or h.me is None or not s:
                    continue
                if boolean(arg(a, 'false', 'remove', 'r')):
                    if h.anim and h.anim.lower() == s.lower():
                        h.anim = None
                else:
                    model = self.model(h.me)
                    if model and s not in model[0]['anims'] and s.lower() not in {k.lower() for k in model[0]['anims']}:
                        self.problem(f'el modelo {h.me} no tiene la animación {s}')
                    h.anim, h.anim_start, h.anim_speed = s, h.age, num(arg(a, '1', 'speed', 'sp'), 1)
        elif name == 'changepart':
            for t in targets:
                h = self.holder(t)
                if h is not None:
                    part = arg(a, None, 'partid', 'pid', 'part', 'p')
                    nm = arg(a, None, 'newmodelid', 'nmid', 'nm', 'newmodel')
                    if part and nm:
                        nm = nm if '.' in nm else self.cls['id'] + '.' + nm.lower()
                        h.swaps[part.lower()] = (nm, arg(a, part, 'newpartid', 'npid', 'np', 'newpart'))
        elif name in ('partvisibility', 'partvis'):
            for t in targets:
                h = self.holder(t)
                part = arg(a, None, 'partid', 'pid', 'part', 'p')
                if h is not None and part:
                    h.vis[part.lower()] = boolean(arg(a, 'true', 'visible', 'v', 'visibility'))
        elif name == 'tint':
            for t in targets:
                h = self.holder(t)
                if h is not None:
                    h.tint = hex_rgb(arg(a, '#ffffff', 'color', 'c'))
        elif name in ('projectile', 'p', 'missile', 'shoot', 'shootfireball'):
            self.projectile(a, ctx, targets, name == 'missile')
        elif name == 'totem':
            self.totem(a, ctx, targets)
        elif name == 'orbital':
            self.orbital(a, ctx, targets)
        elif name in ('aura', 'buff', 'debuff', 'ondamaged', 'onattack', 'onshoot'):
            self.aura(a, ctx, targets)
        elif name in ('command', 'consolecommand', 'cmd'):
            c = str(arg(a, '', 'command', 'cmd', 'c')).strip().strip('"').lower().lstrip('/').split()
            if len(c) >= 2 and c[0] in ('meg', 'modelengine'):
                if c[1] == 'disguise' and len(c) >= 3:
                    self.model_mech({'mid': c[2] if '.' in c[2] else self.cls['id'] + '.' + c[2]}, ctx, targets)
                elif c[1] == 'undisguise':
                    self.model_mech({'remove': 'true'}, ctx, targets)
        elif name in ('auraremove', 'removeaura', 'removebuff'):
            n = str(arg(a, '', 'auraname', 'aura', 'name', 'n', 'buffname', 'b')).lower()
            for t in targets:
                if t.who is not None:
                    au = self.state(t.who)['auras'].get(n)
                    if au and au['alive']:
                        self.finish_aura(au)
        elif name in ('damage', 'd', 'basedamage', 'percentdamage'):
            amount = num(arg(a, '1', 'amount', 'a', 'damage'), 1)
            for t in targets:
                e = t.entity()
                if e is None or not self.can_hurt(ctx, e):
                    continue
                dmg = amount * ctx.power * t.power
                e.health -= dmg
                self.damage.append((self.clock, e.name, round(dmg, 2)))
        elif name in ('heal', 'healpercent', 'potion', 'potionclear', 'ignite', 'extinguish', 'setblock', 'setblocktype',
                      'message', 'msg', 'actionmessage', 'stun', 'prison', 'freeze', 'look'):
            if name == 'potion' and not arg(a, None, 'type', 't'):
                self.problem('potion sin tipo')
            if name == 'look' and ctx.caster.actor is not None and targets:
                d = targets[0].pos() - ctx.caster.eye()
                if np.linalg.norm(d) > 1e-6:
                    ctx.caster.actor.yaw = yaw_of(d)
        elif name in ('throw', 'pull', 'leap', 'lunge', 'jump', 'velocity', 'propel'):
            self.motion(name, a, ctx, targets)
        elif name in ('teleport', 'tp', 'teleportto', 'tpt'):
            for t in targets:
                if t.who is not None and t.who.same(ctx.caster):
                    continue
                if ctx.caster.actor is not None:
                    ctx.caster.actor.pos = t.pos().copy()
                elif ctx.caster.entity is not None:
                    ctx.caster.entity.pos = t.pos().copy()
                break
        elif name in ('setvariable', 'setvar', 'variableset', 'variableadd', 'varadd', 'variableunset', 'unsetvariable'):
            var = arg(a, None, 'var', 'variable', 'name', 'key', 'k')
            if var:
                scope = self.var_scope(var, ctx, targets[0].who if targets else None)
                if 'unset' in name:
                    scope.pop(var_name(var), None)
                elif 'add' in name:
                    scope[var_name(var)] = fmt(num(scope.get(var_name(var)), 0) + num(arg(a, '1', 'amount', 'a', 'value', 'val'), 1))
                else:
                    scope[var_name(var)] = str(arg(a, '', 'value', 'val', 'v')).strip('"')
        elif name in NOOP:
            pass
        else:
            self.problem(f'mecánica {name} no soportada')

    def call(self, name, ctx, targets):
        meta = self.meta(name)
        if meta is None:
            if name:
                self.problem(f'falta la skill {name}')
            return
        c = ctx.copy()
        c.targets = targets
        self.run_meta(meta, c)

    def can_hurt(self, ctx, e):
        owner = ctx.caster.player()
        if owner is not None and e is owner:
            return False
        if owner is None and ctx.caster.entity is e:
            return False
        return e.alive and e.kind != 'player'

    def motion(self, name, a, ctx, targets):
        if name == 'throw':
            v, vy = num(arg(a, '1', 'velocity', 'v'), 1) / 10, num(arg(a, '1', 'velocityy', 'vy'), 1) / 10
            for t in targets:
                e = t.entity()
                if e is None or (e is not ctx.caster.entity and not self.can_hurt(ctx, e)):
                    continue
                away = e.pos - ctx.caster.pos()
                away[1] = 0
                n = np.linalg.norm(away)
                away = away / n * v if n > 1e-6 else np.zeros(3)
                e.vel = np.array([away[0], vy, away[2]])
        elif name in ('leap', 'lunge', 'propel'):
            for t in targets:
                d = t.pos() - ctx.caster.pos()
                if name == 'lunge':
                    d[1] = 0
                n = np.linalg.norm(d)
                if n < 1e-6:
                    continue
                if name == 'leap':
                    vel = d / n * min(4, num(arg(a, '100', 'velocity', 'v'), 100) / 100)
                elif name == 'lunge':
                    vel = d / n * min(4, num(arg(a, '1', 'velocity', 'v'), 1)) + [0, num(arg(a, '0', 'velocityy', 'vy'), 0), 0]
                else:
                    vel = d / n * num(arg(a, '1', 'velocity', 'v'), 1) / 10
                if ctx.caster.entity is not None:
                    ctx.caster.entity.vel = vel
                elif ctx.caster.actor is not None:
                    ctx.caster.actor.pos = ctx.caster.actor.pos + vel
                break
        elif name == 'jump':
            for t in targets:
                if t.entity() is not None:
                    t.entity().vel[1] = num(arg(a, '1', 'velocity', 'v'), 1)

    # --- efectos (SkillRuntime.summon/spawn)
    def summon(self, a, ctx, targets):
        t = arg(a, None, 'type', 't', 'mob', 'm')
        mob = self.mobs.get(str(t).lower())
        if mob is None:
            self.problem(f'falta el mob {t}')
            return
        amount = int(max(1, min(32, num(arg(a, '1', 'amount', 'a'), 1))))
        radius = num(arg(a, '0', 'radius', 'r'), 0)
        for tg in targets:
            for _ in range(amount):
                at = tg.pos().copy()
                if radius > 0:
                    at += [(random.random() * 2 - 1) * radius, 0, (random.random() * 2 - 1) * radius]
                self.spawn(ctx, mob, at, ctx.caster.yaw(), 0.0)

    def spawn(self, ctx, mob, at, yaw, pitch):
        if len(self.actors) >= 2000:
            return None
        act = Actor(self, mob, at, yaw, pitch, ctx.caster)
        self.actors.append(act)
        self.count('actor:' + act.name)
        mine = ctx.as_(Who(actor=act))
        on_spawn = []
        for mm in mob.get('mechs', []):
            tr = mm.get('tr') or 'onspawn'
            if tr == 'ontimer':
                act.timers.append(mm)
            elif tr in ('onspawn', 'onload', 'onready'):
                on_spawn.append(mm)
        s = Script(mine, on_spawn)
        if not self.run_script(s):
            self.scripts.append(s)
        return act

    def holder(self, t):
        if t.who is None:
            return None
        if t.who.actor is not None:
            return t.who.actor
        if t.who.entity is self.player:
            return self.disguise
        return None

    def model_mech(self, a, ctx, targets):
        mid = arg(a, None, 'mid', 'm', 'modelid', 'model')
        for t in targets:
            if boolean(arg(a, 'false', 'remove', 'r')):
                h = self.holder(t)
                if h is not None:
                    if h.follow is not None:
                        h.alive = False
                        self.disguise = None
                    else:
                        h.me = None
                continue
            if not mid:
                continue
            if self.model(mid) is None:
                self.problem(f'falta el modelo de ModelEngine {mid}')
            h = self.holder(t)
            if h is None and t.who is not None and t.who.entity is self.player:
                h = Actor(self, None, self.player.pos, self.player.yaw, 0, ctx.caster)
                h.follow = self.player
                self.actors.append(h)
                self.disguise = h
            if h is None:
                continue
            h.me = mid
            h.anim = 'spawn' if 'spawn' in (self.model(mid) or [{'anims': {}}])[0]['anims'] else None
            h.anim_start = h.age

    def model(self, mid):
        if not hasattr(self, '_models'):
            self._models = {}
        if mid not in self._models:
            path = os.path.join(RES, 'tfclient', 'skills', 'models', mid + '.json')
            if os.path.exists(path):
                m = json.load(open(path))
                tex_dir = os.path.join(RES, 'tfclient', 'textures', 'skills', mid.split('.')[0], 'me')
                self._models[mid] = (m, R.load_textures(m, tex_dir))
            else:
                self._models[mid] = None
        return self._models[mid]

    # --- partículas (SkillFx; aquí solo para ver)
    def particles_fx(self, name, a, ctx, targets):
        p = str(arg(a, 'reddust', 'particle', 'p', 'part')).lower()
        p = PARTICLE_NAMES.get(p, p)
        color = arg(a, None, 'color', 'c', 'color1')
        if p == 'block':
            color = BLOCK_COLORS.get(str(arg(a, 'stone', 'material', 'm', 'block', 'b')).lower(), '#9a9a9a')
        n = int(min(400, num(arg(a, '10', 'amount', 'a', 'count'), 10)))
        hs, vs = num(arg(a, '0', 'hspread', 'hs', 'xspread', 'spread'), 0), num(arg(a, '0', 'vspread', 'vs', 'yspread'), 0)
        sp = num(arg(a, '0', 'speed', 's'), 0)
        y = num(arg(a, '0', 'y', 'yoffset'), 0)
        fo, so = num(arg(a, '0', 'forwardoffset', 'fo'), 0), num(arg(a, '0', 'sideoffset', 'so'), 0)
        for t in targets:
            at = t.pos() + [0, y, 0] + direction(ctx.caster.yaw(), 0) * fo + direction(ctx.caster.yaw() + 90, 0) * so
            if 'ring' in name:
                r = num(arg(a, '5', 'radius', 'r'), 5)
                pts = int(min(256, num(arg(a, '8', 'points', 'p'), 8)))
                for i in range(pts):
                    ang = math.pi * 2 * i / max(1, pts)
                    self.spawn_particles(p, at + [math.cos(ang) * r, 0, math.sin(ang) * r], max(1, min(n, 4)), hs, vs, sp, color)
            elif 'sphere' in name or 'box' in name:
                r = num(arg(a, '1', 'radius', 'r'), 1)
                for _ in range(n):
                    u, ang = random.random() * 2 - 1, random.random() * math.pi * 2
                    rr = math.sqrt(1 - u * u)
                    self.spawn_particles(p, at + np.array([rr * math.cos(ang), u, rr * math.sin(ang)]) * r, 1, 0, 0, 0, color)
            elif 'orbital' in name:
                r = num(arg(a, '1', 'radius', 'r'), 1)
                self.spawn_particles(p, at + [r, num(arg(a, '0', 'oy', 'offsety'), 0), 0], 1, 0, 0, 0, color)
            elif 'line' in name:
                frm = ctx.caster.pos() + [0, 1, 0]
                to = at + [0, 1, 0]
                for k in range(12):
                    self.spawn_particles(p, frm + (to - frm) * k / 11, 1, 0, 0, 0, color)
            else:
                self.spawn_particles(p, at, n, hs, vs, sp, color)

    def spawn_particles(self, kind, at, n, hs, vs, sp, color=None):
        rgb = hex_rgb(color) if color and str(color).startswith('#') else hex_rgb(PARTICLE_COLORS.get(kind, '#ffffff'))
        big = kind in ('explosion', 'explosion_emitter', 'flash', 'sweep_attack', 'sonic_boom')
        for _ in range(min(n, 60)):
            pos = np.array(at, float) + [random.gauss(0, hs), random.gauss(0, vs), random.gauss(0, hs)]
            vel = np.array([random.gauss(0, sp), random.gauss(0, sp), random.gauss(0, sp)])
            life = 6 if big else random.randint(8, 18)
            self.particles.append({'pos': pos, 'vel': vel, 'rgb': rgb, 'age': 0, 'life': life,
                                   'size': 0.35 if big else 0.07})

    # --- proyectiles (SkillMovers)
    def projectile(self, a, ctx, targets, missile):
        for t in targets:
            p = self.mover(ctx, a, 1 if missile else 0)
            fo = boolean(arg(a, 'false', 'fromorigin', 'fo'))
            base = self.origin(ctx) if fo else ctx.caster.pos()
            start0 = base + [0, num(arg(a, '1', 'startyoffset', 'syo'), 1), 0]
            target = t.pos() + [0, num(arg(a, '1', 'targetyoffset', 'tyo'), 1), 0]
            d = target - start0
            if np.linalg.norm(d) < 1e-6:
                d = ctx.caster.forward()
            d = d / np.linalg.norm(d)
            ho, vo = num(arg(a, '0', 'horizontaloffset', 'ho'), 0), num(arg(a, '0', 'verticaloffset', 'vo'), 0)
            if ho or vo:
                d = direction(yaw_of(d) + ho, pitch_of(d) - vo)
            flat = np.array([d[0], 0, d[2]])
            flat = flat / np.linalg.norm(flat) if np.linalg.norm(flat) > 1e-6 else np.zeros(3)
            right = direction(yaw_of(d) + 90, 0)
            p['pos'] = start0 + flat * num(arg(a, '1', 'startforwardoffset', 'sfo'), 1) + right * num(arg(a, '0', 'startsideoffset', 'sso'), 0)
            p['dir'] = d
            p['step'] = num(arg(a, '5', 'velocity', 'v'), 5) / 20 * p['interval']
            p['range'] = num(arg(a, '40', 'maxrange', 'mr'), 40)
            p['ticks'] = int(num(arg(a, '400', 'maxduration', 'md', 'duration', 'd'), 400))
            if missile:
                p['homing'] = t.entity() or ctx.aim
                p['inertia'] = max(0.1, num(arg(a, '1.5', 'inertia', 'in'), 1.5))
            self.start_mover(p, a)

    def totem(self, a, ctx, targets):
        for t in targets:
            p = self.mover(ctx, a, 2)
            p['pos'] = t.pos() + [0, num(arg(a, '0', 'yoffset', 'y'), 0), 0]
            p['dir'] = ctx.caster.forward()
            p['ticks'] = int(num(arg(a, '200', 'maxduration', 'md', 'duration', 'd'), 200))
            self.start_mover(p, a)

    def orbital(self, a, ctx, targets):
        for t in targets:
            if t.who is None:
                continue
            p = self.mover(ctx, a, 3)
            p['center'] = t.who
            p['radius'] = num(arg(a, '4', 'radius', 'r'), 4)
            p['astep'] = math.pi * 2 / max(1, num(arg(a, '32', 'points', 'p'), 32))
            p['oy'] = num(arg(a, '0', 'offsety', 'oy', 'yoffset'), 0)
            p['ticks'] = int(num(arg(a, '100', 'duration', 'd', 'maxduration', 'md'), 100))
            p['angle'] = 0.0
            p['pos'] = t.pos() + [p['radius'], p['oy'], 0]
            p['dir'] = np.zeros(3)
            self.start_mover(p, a)

    def mover(self, ctx, a, kind):
        still = kind in (2, 3)
        return {'ctx': ctx.copy(), 'kind': kind, 'interval': int(max(1, num(arg(a, '1', 'interval', 'i'), 1))),
                'hr': num(arg(a, '1.25', 'horizontalradius', 'hitradius', 'hr'), 1.25),
                'vr': num(arg(a, arg(a, '1.25', 'hr'), 'verticalradius', 'vr'), 1.25),
                'hp': boolean(arg(a, 'true', 'hitplayers', 'hp')), 'hnp': boolean(arg(a, 'false', 'hitnonplayers', 'hnp')),
                'se': boolean(arg(a, 'false' if still else 'true', 'stopatentity', 'se')),
                'sb': boolean(arg(a, 'false' if still else 'true', 'stopatblock', 'sb')),
                'charges': int(num(arg(a, '0', 'charges', 'c', 'maxcharges'), 0)), 'hits': 0, 'hit': set(),
                'ontick': arg(a, None, 'ontick', 'ot', 'ontickskill'), 'onhit': arg(a, None, 'onhit', 'oh', 'onhitskill'),
                'onend': arg(a, None, 'onend', 'oe', 'onendskill'), 'age': 0, 'travelled': 0.0, 'alive': True,
                'range': 1e9, 'step': 0.0, 'bullet': None}

    def start_mover(self, p, a):
        typ = str(arg(a, '', 'bullettype', 'bt', 'type')).upper()
        mob = arg(a, None, 'mob', 'bulletmob', 'bm', 'mobtype', 'mm')
        if mob and typ in ('MOB', ''):
            m = self.mobs.get(str(mob).lower())
            if m is None:
                self.problem(f'falta el mob {mob}')
            else:
                d = p['dir']
                yaw = yaw_of(d) if np.linalg.norm(d) > 0 else p['ctx'].caster.yaw()
                pitch = pitch_of(d) if np.linalg.norm(d) > 0 and p['kind'] in (0, 1) else 0
                p['bullet'] = self.spawn(p['ctx'], m, p['pos'], yaw, pitch)
        os_ = arg(a, None, 'onstart', 'os', 'onstartskill')
        if os_:
            self.mover_run(p, os_, [Tgt(at=p['pos'])], None)
        self.movers.append(p)

    def mover_run(self, p, skill, targets, trigger):
        c = p['ctx'].copy()
        c.origin = np.array(p['pos'], float)
        c.targets = targets
        c.dir = p['dir']
        if trigger is not None:
            c.trigger = Who(entity=trigger)
        meta = self.meta(skill)
        if meta is None:
            self.problem(f'falta la skill {skill}')
            return
        self.run_meta(meta, c)

    def mover_end(self, p):
        if not p['alive']:
            return
        p['alive'] = False
        if p['onend']:
            self.mover_run(p, p['onend'], [Tgt(at=p['pos'])], None)
        if p['bullet'] is not None:
            p['bullet'].alive = False

    def mover_step(self, p):
        p['age'] += 1
        if p['age'] % p['interval']:
            if p['age'] >= p['ticks']:
                self.mover_end(p)
            return
        frm = np.array(p['pos'], float)
        if p['kind'] in (0, 1):
            h = p.get('homing')
            if p['kind'] == 1 and h is not None and h.alive:
                to = h.pos + [0, 0.9, 0] - p['pos']
                if np.linalg.norm(to) > 1e-6:
                    nd = p['dir'] * p['inertia'] + to / np.linalg.norm(to)
                    p['dir'] = nd / np.linalg.norm(nd)
            to = p['pos'] + p['dir'] * p['step']
            if p['sb'] and to[1] < 0 <= p['pos'][1] and p['dir'][1] < 0:  # el suelo del mundo de prueba
                k = p['pos'][1] / max(1e-6, p['pos'][1] - to[1])
                p['pos'] = p['pos'] + (to - p['pos']) * k
                self.move_bullet(p)
                self.mover_end(p)
                return
            p['travelled'] += p['step']
            p['pos'] = to
        elif p['kind'] == 3:
            if not p['center'].alive():
                self.mover_end(p)
                return
            p['angle'] += p['astep']
            c = p['center'].pos()
            np_ = c + [math.cos(p['angle']) * p['radius'], p['oy'], math.sin(p['angle']) * p['radius']]
            p['dir'] = np_ - p['pos']
            p['pos'] = np_
        self.move_bullet(p)
        if p['ontick']:
            self.mover_run(p, p['ontick'], [Tgt(at=p['pos'])], None)
        if not p['alive']:
            return
        if p['onhit'] or p['se']:
            lo = np.minimum(frm, p['pos']) - [p['hr'], p['vr'], p['hr']]
            hi = np.maximum(frm, p['pos']) + [p['hr'], p['vr'], p['hr']]
            for e in self.entities:
                if not e.alive or e is p['ctx'].caster.entity or e is p['ctx'].caster.player() or e.uid in p['hit']:
                    continue
                if (e.kind == 'player' and not p['hp']) or (e.kind != 'player' and not p['hnp']):
                    continue
                c = e.pos + [0, 0.9, 0]
                if (lo <= c + [0.3, 0.9, 0.3]).all() and (hi >= c - [0.3, 0.9, 0.3]).all():
                    p['hit'].add(e.uid)
                    p['hits'] += 1
                    self.count('impacto')
                    if p['onhit']:
                        self.mover_run(p, p['onhit'], [Tgt(Who(entity=e))], e)
                    if p['se'] or (p['charges'] and p['hits'] >= p['charges']):
                        self.mover_end(p)
                        return
        if p['travelled'] >= p['range'] or p['age'] >= p['ticks']:
            self.mover_end(p)

    def move_bullet(self, p):
        b = p['bullet']
        if b is None or not b.alive:
            return
        b.pos = np.array(p['pos'], float)
        d = p['dir']
        if np.linalg.norm(d) > 1e-6:
            b.yaw = yaw_of(d)
            if p['kind'] in (0, 1):
                b.pitch = pitch_of(d)

    # --- auras (SkillAuras)
    def aura(self, a, ctx, targets):
        name = str(arg(a, 'aura', 'auraname', 'aura', 'name', 'n', 'buffname', 'b')).lower()
        dur = int(num(arg(a, '200', 'duration', 'd', 'ticks', 't', 'time'), 200))
        for t in targets:
            if t.who is None:
                continue
            st = self.state(t.who)
            au = st['auras'].get(name)
            if au and au['alive']:
                au['stacks'] = min(au['max'], au['stacks'] + 1)
                if boolean(arg(a, 'true', 'refreshduration', 'rd')):
                    au['left'] = dur
                continue
            c = ctx.copy()
            c.targets = [Tgt(t.who)]
            au = {'name': name, 'on': t.who, 'ctx': c, 'left': dur, 'interval': int(max(1, num(arg(a, '1', 'interval', 'i'), 1))),
                  'stacks': 1, 'max': int(max(1, num(arg(a, '1', 'maxstacks', 'ms', 'stacks'), 1))),
                  'ontick': arg(a, None, 'ontick', 'ot', 'ontickskill'), 'onend': arg(a, None, 'onend', 'oe', 'onendskill'),
                  'age': 0, 'alive': True, 'st': st}
            st['auras'][name] = au
            self.auras.append(au)
            os_ = arg(a, None, 'onstart', 'os', 'onstartskill')
            if os_:
                self.run_meta(self.meta(os_), self.aura_ctx(au))

    def aura_ctx(self, au):
        c = au['ctx'].copy()
        c.targets = [Tgt(au['on'])]
        c.origin = au['on'].pos().copy()
        return c

    def finish_aura(self, au):
        if not au['alive']:
            return
        au['alive'] = False
        if au['st']['auras'].get(au['name']) is au:
            del au['st']['auras'][au['name']]
        if au['onend']:
            self.run_meta(self.meta(au['onend']), self.aura_ctx(au))

    # --- tick (SkillRuntime.tick)
    def tick(self):
        self.clock += 1
        # Pasivas por tiempo (SkillServer.onServerTick)
        for sk in self.passives:
            every = max(5, round(max(0.25, float(sk['passive'].get('timer') or 1)) * 20))
            if self.clock % every == 0:
                self.cast(sk)
        due = [t for t in self.tasks if t[0] <= self.clock]
        self.tasks = [t for t in self.tasks if t[0] > self.clock]
        for _, fn in due:
            fn()
        now, self.scripts = self.scripts, []
        for s in now:
            if s.wait > 0:
                s.wait -= 1
            done = False if s.wait > 0 else self.run_script(s)
            if not done:
                self.scripts.append(s)
        for p in list(self.movers):
            if p['alive']:
                self.mover_step(p)
        self.movers = [p for p in self.movers if p['alive']]
        for au in list(self.auras):
            if not au['alive']:
                continue
            au['age'] += 1
            au['left'] -= 1
            if au['ontick'] and au['age'] % au['interval'] == 0:
                self.run_meta(self.meta(au['ontick']), self.aura_ctx(au))
            if au['left'] <= 0:
                self.finish_aura(au)
        self.auras = [a for a in self.auras if a['alive']]
        for act in list(self.actors):
            if not act.alive:
                continue
            act.age += 1
            if act.follow is not None:
                act.pos, act.yaw = act.follow.pos.copy(), act.follow.yaw
            for mm in act.timers:
                every = int(max(1, num(mm.get('trv'), 20)))
                if act.age % every == 0:
                    c = Ctx(self.cls, Who(actor=act))
                    c.origin = act.pos.copy()
                    c.trigger = act.owner
                    self.exec(mm, c)
            if act.age > 1200 and act.follow is None:
                act.alive = False
        self.actors = [a for a in self.actors if a.alive]
        # Física sencilla de las entidades (para ver empujes y saltos)
        for e in self.entities:
            if np.any(e.vel):
                e.pos = e.pos + e.vel
                e.vel = e.vel * [0.6, 1, 0.6] - [0, 0.08, 0]
                if e.pos[1] <= 0:
                    e.pos[1] = 0
                    e.vel[:] = 0
            if e.health <= 0 and e.alive and e.kind != 'player':
                e.alive = False
        for p in self.particles:
            p['age'] += 1
            p['pos'] = p['pos'] + p['vel']
        self.particles = [p for p in self.particles if p['age'] < p['life']]

    def busy(self):
        return bool(self.scripts or self.tasks or self.movers or self.auras or
                    any(a.follow is None for a in self.actors) or self.particles)


def fmt(d):
    d = float(d)
    return str(int(d)) if d == int(d) else str(round(d, 4))


def loc(p, k):
    if p is None:
        return '0'
    i = {'x': 0, 'y': 1, 'z': 2}.get(k[:1])
    return fmt(round(float(p[i]), 3)) if i is not None else '0'


def var_name(var):
    v = str(var).lower()
    for pre in ('caster.', 'target.', 'skill.', 'global.', 'world.'):
        if v.startswith(pre):
            return v[len(pre):]
    return v


def in_range(v, rng):
    r = str(rng).replace(' ', '')
    try:
        for op in ('>=', '<=', '!=', '>', '<', '='):
            if r.startswith(op):
                x = float(r[len(op):])
                return {'>=': v >= x, '<=': v <= x, '!=': v != x, '>': v > x, '<': v < x, '=': v == x}[op]
        if 'to' in r:
            a, b = r.split('to', 1)
            return float(a) <= v <= float(b)
        return v == float(r)
    except ValueError:
        return True


# ---------------------------------------------------------------------------------------------------------------
# Dibujo
# ---------------------------------------------------------------------------------------------------------------

def actor_matrix(pos, yaw):
    """Del modelo de ModelEngine (píxeles) al mundo: como SkillClient.Actor.renderModel."""
    a = math.radians(180 - yaw)
    c, s = math.cos(a), math.sin(a)
    m = np.array([[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]], float)
    m[:3, 3] = pos
    return m @ np.diag([1 / 16, 1 / 16, 1 / 16, 1])


def rot(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == 'x':
        r = [[1, 0, 0], [0, c, -s], [0, s, c]]
    elif axis == 'y':
        r = [[c, 0, s], [0, 1, 0], [-s, 0, c]]
    else:
        r = [[c, -s, 0], [s, c, 0], [0, 0, 1]]
    m = np.eye(4)
    m[:3, :3] = r
    return m


def trans(x, y, z):
    m = np.eye(4)
    m[:3, 3] = [x, y, z]
    return m


def scale(x, y, z):
    return np.diag([x, y, z, 1.0])


def head_matrix(pos, yaw, pitch, small, display):
    """El modelo en la cabeza de un soporte: como SkillClient.renderHead (y la transformación «head» del modelo)."""
    m = trans(*pos) @ rot('y', 180 - yaw)
    if small:
        m = m @ scale(0.5, 0.5, 0.5)
    m = m @ scale(-1, -1, 1) @ trans(0, -1.501, 0) @ trans(0, 1 / 16, 0)
    if pitch:
        m = m @ rot('x', pitch)
    m = m @ trans(0, -0.25, 0) @ rot('y', 180) @ scale(0.625, -0.625, -0.625)
    d = (display or {}).get('head') or {}
    tr = [max(-80, min(80, float(v))) / 16 for v in d.get('translation', [0, 0, 0])]
    rx, ry, rz = [float(v) for v in d.get('rotation', [0, 0, 0])]
    sc = [max(-4, min(4, float(v))) for v in d.get('scale', [1, 1, 1])]
    m = m @ trans(*tr) @ rot('x', rx) @ rot('y', ry) @ rot('z', rz) @ scale(*sc)
    return m  # el modelo ya va centrado (jsonmodel resta 8 px)


def hand_matrix(pos, yaw, small, display):
    """Lo que lleva en la mano derecha un soporte: como SkillClient.renderHand (y la transformación de la mano)."""
    m = trans(*pos) @ rot('y', 180 - yaw)
    if small:
        m = m @ scale(0.5, 0.5, 0.5)
    m = m @ scale(-1, -1, 1) @ trans(0, -1.501, 0) @ trans(-5 / 16, 2 / 16, 0) @ rot('x', -90) @ rot('y', 180)
    m = m @ trans(1 / 16, 0.125, -0.625)
    d = (display or {}).get('thirdperson_righthand') or {}
    tr = [max(-80, min(80, float(v))) / 16 for v in d.get('translation', [0, 0, 0])]
    rx, ry, rz = [float(v) for v in d.get('rotation', [0, 0, 0])]
    sc = [max(-4, min(4, float(v))) for v in d.get('scale', [1, 1, 1])]
    return m @ trans(*tr) @ rot('x', rx) @ rot('y', ry) @ rot('z', rz) @ scale(*sc)


class ItemModels:
    def __init__(self):
        self.cache = {}

    def find(self, rel):
        p = os.path.join(RES, *rel.split('/'))
        return p if os.path.exists(p) else None

    def get(self, ref):
        if ref not in self.cache:
            ns, path = J.split_ref(ref)
            tris = J.tris(self.find, ref)
            disp = None
            src = self.find(f'{ns}/models/{path}.json')
            seen = 0
            while src and seen < 8:
                data = json.load(open(src))
                if data.get('display'):
                    disp = data['display']
                    break
                par = data.get('parent')
                if not par:
                    break
                pns, pp = J.split_ref(par, ns)
                src = self.find(f'{pns}/models/{pp}.json')
                seen += 1
            self.cache[ref] = (tris, disp)
        return self.cache[ref]


def scene(sim, items, steve):
    tris = []
    for act in sim.actors:
        if act.head:
            t, disp = items.get(act.head)
            if t:
                w = head_matrix(act.pos, act.yaw, act.pitch, bool(act.mob and act.mob.get('options', {}).get('Small')), disp)
                for pts, uv, img, em, n, tint in t:
                    p4 = np.c_[pts, np.ones(3)] @ w.T
                    n2 = w[:3, :3] @ n
                    tris.append((p4[:, :3], uv, img, True, n2 / max(1e-6, np.linalg.norm(n2)), tint))
        if act.hand:
            t, disp = items.get(act.hand)
            if t:
                w = hand_matrix(act.pos, act.yaw, bool(act.mob and act.mob.get('options', {}).get('Small')), disp)
                for pts, uv, img, em, n, tint in t:
                    p4 = np.c_[pts, np.ones(3)] @ w.T
                    n2 = w[:3, :3] @ n
                    tris.append((p4[:, :3], uv, img, True, n2 / max(1e-6, np.linalg.norm(n2)), tint))
        if act.me:
            m = sim.model(act.me)
            if m:
                model, tex = m
                anim = act.anim if act.anim in model['anims'] else ('idle' if 'idle' in model['anims'] else None)
                t = (act.age - act.anim_start) / 20 * act.anim_speed
                if anim and model['anims'][anim].get('loop') == 'once' and t > model['anims'][anim]['len']:
                    anim, t = ('idle' if 'idle' in model['anims'] else None), 0
                mats = B.pose(model, anim, t)
                hidden = {b['id'] for b in model['bones'] if act.vis.get(b['id'].lower()) is False}
                tris += R.model_tris(model, tex, mats, world=actor_matrix(act.pos, act.yaw), tick=act.age, hidden=hidden,
                                     tint=act.tint)
    if steve is not None:
        if sim.disguise is None:
            tris += figure_tris(steve, steve[1], sim.player.pos, sim.player.yaw)
        for e in sim.mobs_world:
            if e.alive:
                tris += figure_tris(steve, steve[2], e.pos, e.yaw)
    pts = []
    for p in sim.particles:
        k = p['age'] / max(1, p['life'])
        pts.append((p['pos'], (*p['rgb'], 0.95 * (1 - k * k)), p['size']))
    return tris, pts


def figures():
    """Steve y el zombi de las vistas previas (las mismas pieles que las de los VFX, del client.jar)."""
    import build_vfx as BV
    import vfx_preview as VP
    tmp = os.path.join(TOOLS, '..', 'build', 'tf_preview')
    os.makedirs(tmp, exist_ok=True)
    BV.steve_figure()  # deja steve.png en build/tf_preview
    from PIL import Image
    steve_skin = np.asarray(Image.open(os.path.join(tmp, 'steve.png')).convert('RGBA'), np.float32) / 255
    model = VP.player_model(os.path.join(tmp, 'steve.png'), None, tmp)
    return model, steve_skin, BV.zombie_skin()


def figure_tris(fig, skin, pos, yaw):
    model, _, _ = fig
    world = actor_matrix(pos, yaw)
    return R.model_tris(model, [skin], B.pose(model, None, 0), world=world, actor=skin)


def run_skill(cls, skill, ticks=100, frames=False, step=2, hand=None):
    sim = Sim(cls)
    if sim.passives:
        # Las pasivas van solas en el juego: antes de la skill, unos segundos con ellas (modos, auras, traje). Luego
        # se paran para ver solo la skill.
        for _ in range(45):
            sim.tick()
        sim.particles, sim.sounds, sim.damage, sim.counts = [], [], [], {}
        sim.passives = []
    if hand is not None:
        sim.hand = hand
    # Para la vista previa: las auras que la skill pide tener (hasaura ... true), puestas
    meta = sim.meta(skill.get('entry'))
    for c in (meta or {}).get('conditions', []):
        if c['m'] in ('hasaura', 'hasaurastacks') and (c.get('v', 'true') == 'true' or c.get('v', '').startswith('orelsecast')):
            name = str(arg(c.get('a', {}), '', 'auraname', 'aura', 'name', 'n')).lower()
            st = sim.state(Who(entity=sim.player))
            if name and name not in st['auras']:
                au = {'name': name, 'on': Who(entity=sim.player), 'ctx': Ctx(sim.cls, Who(entity=sim.player)), 'left': 400,
                      'interval': 1, 'stacks': 1, 'max': 1, 'ontick': None, 'onend': None, 'age': 0, 'alive': True, 'st': st}
                st['auras'][name] = au
    # ...y si la skill anima el modelo que lleva puesto el jugador (el traje), puesto también
    for m in (meta or {}).get('mechs', []):
        if m['m'] in ('state', 'changepart', 'partvisibility') and m.get('t') in (None, 'self') and sim.disguise is None:
            mid = arg(m.get('a', {}), None, 'mid', 'm', 'modelid')
            if mid:
                sim.model_mech({'mid': mid if '.' in mid else sim.cls['id'] + '.' + mid}, Ctx(sim.cls, Who(entity=sim.player)),
                               [Tgt(Who(entity=sim.player))])
            break
    ok = sim.cast(skill)
    out_frames = []
    items = ItemModels() if frames else None
    steve = figures() if frames else None
    quiet = 0
    for _ in range(ticks):
        sim.tick()
        if frames and sim.clock % step == 0:
            out_frames.append(scene(sim, items, steve))
        quiet = quiet + 1 if not sim.busy() else 0
        if quiet > 6:
            break
    return sim, ok, out_frames


def webp(frames, path, size=320):
    import vfx_preview as VP
    if not frames:
        return 0
    lo, hi = VP.frame_box(frames, keep=(3, 97))
    lo = np.minimum(lo, [-1, 0, -1])
    hi = np.maximum(hi, [1, 2, 1])
    # sin pasarse: como mucho 9 bloques alrededor del jugador
    lo = np.maximum(lo, [-9, -1, -4])
    hi = np.minimum(hi, [9, 9, 12])
    center = (lo + hi) / 2
    rad = max(np.linalg.norm(hi - lo) / 2, 1.5)
    dist = rad / math.tan(math.radians(20))
    imgs = [R.raster(t, size=size, yaw=-35, pitch=-20, dist=dist, center=center, fov=40, ss=1, points=p) for t, p in frames]
    imgs += [imgs[-1]] * 3
    imgs[0].save(path, 'WEBP', save_all=True, append_images=imgs[1:], duration=100, loop=0, quality=72, method=4)
    return len(imgs)


def load_class(cid):
    return json.load(open(os.path.join(CLASSES, cid + '.json')))


def main():
    args = sys.argv[1:]
    webp_dir = None
    if '--webp' in args:
        i = args.index('--webp')
        webp_dir = args[i + 1]
        del args[i:i + 2]
    ticks = 120
    if '--ticks' in args:
        i = args.index('--ticks')
        ticks = int(args[i + 1])
        del args[i:i + 2]
    if not args or args[0] == '--todas':
        ids = sorted(f[:-5] for f in os.listdir(CLASSES) if f.endswith('.json'))
    else:
        ids = [args[0]]
    only = args[1] if len(args) > 1 and args[0] != '--todas' else None
    total_problems = 0
    for cid in ids:
        cls = load_class(cid)
        print(f'== {cid} ({cls["class"].get("name")})')
        for sk in cls['skills']:
            if only and sk['id'] != only:
                continue
            if sk.get('hidden'):
                continue
            # Las clases que cambian de forma (el guantelete del Dragón Rojo): la skill con lo que la deja lanzarse
            best = None
            for hand in ([None] + sorted((cls.get('hand_items') or {}).keys(), key=len)[1:]):
                hand = hand.lower() if hand else None
                sim, ok, frames = run_skill(cls, sk, ticks, frames=False, hand=hand)
                score = sum(sim.counts.values()) + len(sim.sounds)
                if best is None or score > best[0]:
                    best = (score, hand)
            sim, ok, frames = run_skill(cls, sk, ticks, frames=webp_dir is not None, hand=best[1])
            dmg = sum(d for _, _, d in sim.damage)
            acts = sum(v for k, v in sim.counts.items() if k.startswith('actor:'))
            print(f'  {sk["id"]:28s} {"ok" if ok else "NO SE LANZA":11s} ticks {sim.clock:3d}  efectos {acts:3d}  '
                  f'daño {dmg:6.1f}  sonidos {len(sim.sounds):3d}  impactos {sim.counts.get("impacto", 0)}')
            for p in sim.problems:
                print('     -', p)
            total_problems += len(sim.problems)
            if webp_dir:
                os.makedirs(os.path.join(webp_dir, cid), exist_ok=True)
                webp(frames, os.path.join(webp_dir, cid, sk['id'] + '.webp'))
    print(f'problemas: {total_problems}')


if __name__ == '__main__':
    main()
