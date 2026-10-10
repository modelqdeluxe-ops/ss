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
NOOP = {'cancelevent', 'animatearmorstand', 'bodyrotation', 'brightness', 'lockmodel', 'bodyclamp',
        'enchant', 'setgravity', 'threat', 'runaitargetselector', 'runaigoalselector', 'modifyglobalscore', 'setglobalscore',
        'feed', 'playanimation', 'swing', 'remapmodel', 'glow', 'setcollidable', 'setinvulnerable',
        'setrotation', 'mount', 'mounttarget', 'dismount', 'bindhitbox', 'clearthreat', 'cullconfig', 'posearmorstand',
        'segment', 'removehelditem', 'setnodamageticks', 'recoil', 'hide', 'showentity', 'show', 'sendtitle', 'title',
        'dismountmodel', 'dismountall', 'directionalvelocity'}
KNOWN_TARGETS = {'self', 'caster', 's', 'boss', 'mob', 'owner', 'parent', 'summoner', 'trigger', 'target', 't',
                 'targetedtarget', 'tt', 'targetedentity', 'origin', 'o', 'source', 'selflocation', 'casterlocation', 'sl',
                 'bosslocation', 'moblocation', 'selfeyelocation', 'eyelocation', 'casterseyelocation', 'boss_eye', 'sel',
                 'targetlocation', 'tl', 'targetloc', 'targetedlocation', 'targetedloc', 'forward', 'f',
                 'projectileforward', 'pf', 'entitiesnearorigin', 'eno', 'livingentitiesnearorigin', 'leno',
                 'playersnearorigin', 'pno', 'entitiesinradius', 'eir', 'livingentitiesinradius', 'leir', 'livinginradius',
                 'allinradius', 'air', 'entitiesinring', 'eirr', 'mobsinradius', 'mir', 'playersinradius', 'pir',
                 'playersinworld', 'world', 'pinr', 'nearestplayer', 'np', 'ring', 'circle', 'randomlocationsnearcaster',
                 'rlnc', 'randomlocationsnearorigin', 'rlno', 'modelpart', 'mp', 'location', 'l', 'spawnlocation',
                 'c', 'targeted', 'se', 'ownerlocation', 'children', 'child', 'summons', 'mountedmodel', 'mounted',
                 'mobsnearorigin', 'mno', 'entitiesinworld', 'eiw', 'livingentitiesinworld', 'entitiesinringnearorigin',
                 'eirno', 'entitiesincone', 'eic', 'livingentitiesincone', 'entitiesinline', 'eil', 'livinginline', 'lil',
                 'entl', 'livingentitiesinline', 'targetblock', 'tb', 'variablelocation', 'varlocation', 'vl',
                 'randomlocationsneartargetentities', 'rlnte', 'randomlocationsneartargets', 'rlnt'}
KNOWN_CONDS = {'mythicmobtype', 'mmt', 'mobtype', 'owner', 'isowner', 'isparent', 'parent', 'hasaura', 'hasbuff',
               'hasaurastacks', 'aurastacks', 'crouching', 'sneaking', 'issneaking', 'sprinting', 'issprinting', 'onground',
               'grounded', 'inblock', 'blocktype', 'inside', 'standingon', 'variableisset', 'varisset', 'varset',
               'variableequals', 'varequals', 'variableeq', 'vareq', 'variableinrange', 'varinrange', 'varrange', 'offgcd',
               'gcd', 'incombat', 'hasai', 'hastarget', 'itemissimilar', 'holding', 'wearing', 'haspermission',
               'lineofsight', 'los', 'world', 'biome', 'dimension', 'moonphase', 'stance', 'hasowner', 'targetwithin',
               'targetinlineofsight', 'targetnotwithin', 'distance', 'distancefromorigin', 'health', 'hp',
               'healthpercent', 'hppercent', 'hpp', 'entitytype', 'type', 'entitytypes', 'isplayer', 'player',
               'ismonster', 'monster', 'isliving', 'living', 'isburning', 'burning', 'onfire', 'inwater', 'isinwater',
               'haspotioneffect', 'haspotion', 'isskill', 'chance', 'altitude', 'height', 'dead', 'isdead',
               'faction', 'hastag', 'tag', 'mmocantarget', 'cantarget', 'iscaster', 'ischild', 'children', 'child',
               'modelhasdriver', 'drivingmodel', 'isdriving', 'ownerisonline', 'ismoving', 'moving', 'pitch', 'onblock',
               'hasitem', 'mobsinradius', 'mir', 'entitiesinradius', 'eir', 'playersinradius', 'pir'}

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


def wants_actors(conds):
    """SkillMovers.wantsActors: ¿busca el proyectil efectos del pack (mythicmobtype true, también dentro de una compuesta)?"""
    for c in conds or []:
        if c['m'] in ('mythicmobtype', 'mmt', 'mobtype') and c.get('v', 'true') == 'true':
            return True
        if wants_actors(c.get('parts')):
            return True
    return False


def unquote(s):
    """SkillRuntime.unquote: sin las comillas de fuera (dobles o simples)"""
    s = str(s or '').strip()
    if len(s) >= 2 and s[0] == s[-1] and s[0] in '"\'':
        return s[1:-1]
    return s


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
        self.carried = False  # la bala de un proyectil
        self.swaps, self.vis, self.tint = {}, {}, None
        self.uid = ('actor', self.id)
        self.name = mob.get('_name', '') if mob else ''
        self.custom_name = None
        # esbirros (SkillActor + SkillMinions)
        self.target = None
        self.ai = True
        self.speed_mul = 1.0
        self.walking = False
        self.rider = None
        self.text = None
        self.scale = 1.0
        self.leaving = False
        self.living = bool(mob and str(mob.get('type', '')).upper() in LIVING)

    def display_name(self):
        return self.custom_name or self.name


LIVING = {'WOLF', 'HORSE', 'ZOMBIE', 'SKELETON', 'HUSK', 'VEX', 'ALLAY', 'CAT', 'FOX', 'IRON_GOLEM', 'SPIDER', 'PHANTOM',
          'BLAZE', 'PIG', 'POLAR_BEAR', 'RAVAGER', 'DROWNED', 'WITHER_SKELETON', 'STRAY', 'SKELETON_HORSE', 'ZOMBIE_HORSE',
          'BEE', 'PARROT', 'GOAT'}


class CancelSkill(Exception):
    """SkillRuntime.CancelSkill"""


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
        self.mover = None

    def copy(self):
        c = Ctx(self.cls, self.caster)
        c.trigger, c.origin, c.targets, c.aim, c.power = self.trigger, self.origin, self.targets, self.aim, self.power
        c.dir, c.depth, c.vars, c.mods, c.mover = self.dir, self.depth + 1, self.vars, self.mods, self.mover
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
        self.tree = {}
        for k, v in cls['tree'].items():
            v = dict(v)
            v['_name'] = k
            self.tree[k.lower()] = v
        self.mobs = {}
        for k, v in cls['mobs'].items():
            v = dict(v)
            v['_name'] = k
            self.mobs[k.lower()] = v
        self.clock = 0
        self.scripts, self.tasks, self.actors, self.movers, self.auras = [], [], [], [], []
        self.states = {}
        self.player = Entity(self, 'player', [0, 0, 0], 0.0, 'Jugador')
        self.player.pitch = 8.0  # mirando al primer zombi (un poco hacia abajo), como al apuntar en el juego
        self.mobs_world = [Entity(self, 'zombie', [0, 0, 6], 180, 'Zombi'), Entity(self, 'zombie', [2.5, 0, 8], 180, 'Zombi 2'),
                           Entity(self, 'zombie', [-2.5, 0, 7.5], 180, 'Zombi 3'),
                           Entity(self, 'zombie', [0.4, 0, 2.3], 180, 'Zombi cerca')]  # para los golpes cuerpo a cuerpo
        self.entities = [self.player] + self.mobs_world
        self.particles = []
        self.sounds = []
        self.damage = []
        self.problems = []
        self.notes = []
        self.traces = []
        self.crouch = False
        self.counts = {}
        self.disguise = None
        # Lo que lleva en la mano (las clases que lo miran: el guantelete del Dragón Rojo empieza en el básico)
        hand = cls.get('hand_items') or {}
        self.hand_items = {k.lower(): v for k, v in hand.items()}
        self.hand = min(self.hand_items, key=len) if self.hand_items else None
        self.passives = [s for s in cls['skills'] if s.get('passive') and s['passive'].get('type') == 'TIMER' and s.get('entry')]
        # skills que el pack llama sin definir: en MythicMobs no hacen nada (no es un fallo nuestro)
        self.known_missing = {x.lower() for x in cls.get('missing', [])}

    # --- estado de cada quién (SkillRuntime.state)
    def state(self, who):
        return self.states.setdefault(who.key(), new_state())

    def problem(self, text):
        if text not in self.problems:
            self.problems.append(text)

    def trace(self, text):
        if TRACE and len(self.traces) < 400:
            self.traces.append(f'{self.clock:4d} {text}')

    def note(self, text):
        """Algo raro del propio pack que en MythicMobs/ModelEngine tampoco hace nada (no es un fallo del motor)."""
        if text not in self.notes:
            self.notes.append(text)

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
        for k, v in (self.cls.get('start_vars') or {}).items():
            self.state(ctx.caster)['vars'].setdefault(k.lower(), v)
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
                self.trace(f'{name}: parada por {c["m"]}{c.get("a") or ""} → {r} ({c.get("v")})')
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
                    if act.startswith('cast '):
                        if r:
                            other = ctx.copy()
                            other.targets = [cp]
                            self.run_named(act[5:].strip(), other)
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
                self.trace(f'{name}: sin objetivos que cumplan las TargetConditions')
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
        if act.startswith('cast '):
            if r:
                self.run_named(act[5:].strip(), ctx)
            return 'go'
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
            if str(name).strip().lower() not in self.known_missing:
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
            except CancelSkill:
                return True
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
                self.trace(f'  ({m["m"]} saltada: ?{"!" if c.get("not") else ""}{c["m"]})')
                return
        a = m.get('a', {})
        delay = int(num(a.get('delay'), 0))
        repeat = int(num(arg(a, '0', 'repeat'), 0))
        every = int(max(1, num(arg(a, '1', 'repeatinterval', 'repeati', 'ri'), 1)))

        def once():
            if ctx.caster.actor is not None and not ctx.caster.alive() and m['m'] != 'remove':
                return
            use = ctx
            o = a.get('origin')
            if o and str(o).strip().startswith('@'):  # origin=@Objetivo{...}
                r = self.from_string(str(o), ctx)
                if r:
                    use = ctx.copy()
                    use.depth = ctx.depth
                    use.origin = np.array(r[0].pos(), float)
            self.apply(m, use, self.targets(m, use))
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
        m['a'] = {k: (v if m['m'] == 'setname' and k in ('name', 'n') else self.text(v, ctx, first))
                  for k, v in m.get('a', {}).items()}  # setname: <target.name> de cada objetivo
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
                return who.actor.display_name() if who.actor is not None else who.obj().name
            if rest in ('hp', 'mhp'):
                return '20'
            if rest == 'damage':
                return '7' if who.entity is not None and who.entity.kind == 'player' else '0'  # espada de diamante
            return '0'
        out = re.sub(r'<([^<>]+)>', value, s)
        if re.search(r'\d\s*[*/+\-]\s*[\d(]', out) and re.fullmatch(r'[\d\s.+\-*/()]+', out):
            e = evaluate(out)
            if e is not None:
                return fmt(e)
        return out

    # --- objetivos (SkillTargets)
    def targets(self, m, ctx):
        out = self.targets_raw(m, ctx)
        if m.get('tc') and out:
            out = [x for x in out if self.passes_conds(m['tc'], x, ctx)]
        ta = m.get('ta') or {}
        sort = str(arg(ta, '', 'sort', 'sortby')).upper()
        limit = int(num(arg(ta, '0', 'limit'), 0))
        if len(out) > 1 and (sort or (limit > 0 and len(out) > limit)):
            c = self.origin(ctx) if 'ORIGIN' in sort else ctx.caster.pos()
            if sort == 'RANDOM':
                out = list(out)
                random.shuffle(out)
            elif sort in ('FURTHEST', 'FARTHEST'):
                out = sorted(out, key=lambda x: -float(np.sum((x.pos() - c) ** 2)))
            elif sort != 'NONE':
                out = sorted(out, key=lambda x: float(np.sum((x.pos() - c) ** 2)))
            if limit > 0:
                out = out[:limit]
        return out

    def passes_conds(self, conds, t, ctx):
        """SkillTargets.passes"""
        for c in conds:
            r = self.test(c, t.who, ctx, t.pos() if t.who is None else None)
            act = c.get('v', 'true')
            ok = (not r) if act in ('false', 'cancel') else r
            if not ok:
                return False
        return True

    def children(self, ctx):
        return [Tgt(Who(actor=a)) for a in self.actors if a.alive and a.owner is not None and a.owner.same(ctx.caster)
                and a.follow is None]

    def targets_raw(self, m, ctx):
        t = m.get('t')
        if t is None:
            return ctx.targets if ctx.targets else [Tgt(ctx.caster)]
        ta = m.get('ta') or {}
        y = num(arg(ta, '0', 'y', 'yoffset', 'yo'), 0)
        c = ctx.caster
        if t not in KNOWN_TARGETS:
            self.problem(f'objetivo @{t} no soportado')
            return [Tgt(c)]
        if t in ('self', 'caster', 's', 'c', 'boss', 'mob'):
            return [Tgt(c)]
        if t == 'owner':
            return [Tgt(c.owner() or c)]
        if t in ('parent', 'summoner'):
            return [Tgt(c.owner())] if c.owner() else []
        if t == 'trigger':
            return [Tgt(ctx.trigger or c)]
        if t in ('target', 't', 'targetedtarget', 'tt', 'targetedentity', 'targeted'):
            return self.target(ctx)
        if t in ('origin', 'o', 'source'):
            return [Tgt(at=self.loc_offset(ta, ctx, self.origin(ctx)) + [0, y, 0])]
        if t in ('selflocation', 'casterlocation', 'sl', 'bosslocation', 'moblocation'):
            return [Tgt(at=self.loc_offset(ta, ctx, c.pos()) + [0, y, 0])]
        if t == 'spawnlocation':
            return [Tgt(at=c.pos() + [0, y, 0])]
        if t in ('selfeyelocation', 'eyelocation', 'casterseyelocation', 'boss_eye', 'sel', 'se'):
            fo = num(arg(ta, '0', 'fo', 'forwardoffset', 'f'), 0)
            return [Tgt(at=c.eye() + [0, y, 0] + c.forward() * fo)]
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
            return self.in_radius(ctx, self.origin(ctx), r, False, False, ta)
        if t in ('playersnearorigin', 'pno'):
            return self.in_radius(ctx, self.origin(ctx), r, True, False, ta)
        if t in ('entitiesinradius', 'eir', 'livingentitiesinradius', 'leir', 'livinginradius', 'allinradius', 'air',
                 'entitiesinring', 'eirr'):
            return self.in_radius(ctx, c.pos(), r, False, False, ta)
        if t in ('mobsinradius', 'mir'):
            return self.in_radius(ctx, c.pos(), r, False, True, ta)
        if t in ('playersinradius', 'pir', 'playersinworld', 'world', 'pinr', 'nearestplayer', 'np'):
            return self.in_radius(ctx, c.pos(), r, True, False, ta)
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
            mp = self.model_part(ta, ctx)
            return [] if mp is None else [Tgt(at=mp + [0, y, 0])]
        if t == 'ownerlocation':
            o = c.owner() or c
            return [Tgt(at=o.pos() + [0, y, 0])]
        if t in ('children', 'child', 'summons'):
            return self.children(ctx)
        if t in ('mountedmodel', 'mounted'):
            return [Tgt(Who(actor=a)) for a in self.actors if a.alive and a.rider is not None and c.entity is a.rider][:1]
        if t in ('mobsnearorigin', 'mno'):
            out = self.in_radius(ctx, self.origin(ctx), r, False, True, ta)
            types = arg(ta, None, 'types', 'type', 't', 'mobtypes')
            if types:
                names = {x.strip().lower() for x in types.split(',')}
                out = [x for x in out if x.who.actor is not None and x.who.actor.name.lower() in names]
            return out
        if t in ('entitiesinworld', 'eiw', 'livingentitiesinworld'):
            return self.in_radius(ctx, c.pos(), 128, False, False, ta)
        if t in ('entitiesinringnearorigin', 'eirno'):
            lo = num(arg(ta, '0', 'min', 'minradius', 'minr'), 0)
            hi = num(arg(ta, '5', 'max', 'maxradius', 'maxr', 'r'), 5)
            o = self.origin(ctx)
            return [x for x in self.in_radius(ctx, o, hi, False, False, ta) if np.linalg.norm(x.pos() - o) >= lo]
        if t in ('entitiesincone', 'eic', 'livingentitiesincone'):
            rng = num(arg(ta, '5', 'range', 'r'), 5)
            ang = num(arg(ta, '90', 'angle', 'a'), 90)
            look = direction(c.yaw(), 0)
            cs = math.cos(math.radians(ang / 2))
            out = []
            for x in self.in_radius(ctx, c.pos(), rng, False, False, ta):
                to = x.pos() - c.pos()
                to[1] = 0
                n = np.linalg.norm(to)
                if n < 0.5 or float(np.dot(to / n, look)) >= cs:
                    out.append(x)
            return out
        if t in ('entitiesinline', 'eil', 'livinginline', 'lil', 'entl', 'livingentitiesinline'):
            rr = num(arg(ta, '1', 'r', 'radius'), 1)
            frm = c.pos()
            to = None
            for x in ctx.targets or []:
                if x.who is None or not x.who.same(c):
                    to = x.pos()
                    break
            if to is None and ctx.origin is not None and np.linalg.norm(ctx.origin - frm) > 1:
                to = ctx.origin
            if to is None:
                to = frm + direction(c.yaw(), 0) * 8
            d = to - frm
            ln = float(np.linalg.norm(d))
            out = []
            for x in self.in_radius(ctx, frm + d / 2, ln / 2 + rr + 1, False, False, ta):
                p = x.pos() + [0, 0.9, 0]
                k = 0 if ln < 1e-6 else max(0.0, min(1.0, float(np.dot(p - frm, d)) / (ln * ln)))
                if np.linalg.norm(p - (frm + d * k + [0, 0.9, 0])) <= rr + 0.6:
                    out.append(x)
            return out
        if t in ('targetblock', 'tb'):
            return [Tgt(at=np.array([c.pos()[0], 0.0, c.pos()[2]]) + c.forward() * 8 + [0, y, 0])]
        if t in ('variablelocation', 'varlocation', 'vl'):
            var = arg(ta, None, 'var', 'variable', 'v')
            v = self.var_scope(var, ctx, None).get(var_name(var)) if var else None
            if not v:
                return []
            p = str(v).split(',')
            return [Tgt(at=np.array([num(p[0]), num(p[1]) + y, num(p[2])]))] if len(p) >= 3 else []
        if t in ('randomlocationsneartargetentities', 'rlnte', 'randomlocationsneartargets', 'rlnt'):
            amount = int(max(1, min(64, num(arg(ta, '1', 'amount', 'a'), 1))))
            minr = num(arg(ta, '0', 'minradius', 'minr'), 0)
            centers = [x.pos() for x in ctx.targets or [] if x.who is not None and not x.who.same(c)]
            if not centers and ctx.aim is not None and ctx.aim.alive:
                centers = [ctx.aim.pos]
            if not centers:
                centers = [c.pos() + c.forward() * 6]
            out = []
            for ce in centers:
                for _ in range(amount):
                    ang, dd = random.random() * math.pi * 2, minr + math.sqrt(random.random()) * max(0, r - minr)
                    out.append(Tgt(at=ce + [math.cos(ang) * dd, y, math.sin(ang) * dd]))
            return out
        return [Tgt(at=c.pos())]

    def origin(self, ctx):
        return ctx.origin if ctx.origin is not None else ctx.caster.pos()

    def target(self, ctx):
        if ctx.aim is not None and ctx.aim.alive:
            return [Tgt(Who(entity=ctx.aim))]
        for t in ctx.targets or []:
            if t.who is not None and not t.who.same(ctx.caster):
                return [t]
        return []

    def loc_offset(self, ta, ctx, at):
        """SkillTargets.offset: forwardoffset/sideoffset según hacia dónde mira quien lanza"""
        fo = num(arg(ta, '0', 'forwardoffset', 'fo'), 0)
        so = num(arg(ta, '0', 'sideoffset', 'so'), 0)
        if fo == 0 and so == 0:
            return np.array(at, float)
        yaw = ctx.caster.yaw()
        return np.array(at, float) + direction(yaw, 0) * fo + direction(yaw + 90, 0) * so

    def forward(self, ta, ctx, y):
        c = ctx.caster
        f = num(arg(ta, '5', 'f', 'forward', 'amount', 'a'), 5)
        side = num(arg(ta, '0', 'sideoffset', 'so', 'side', 's'), 0)
        lock = boolean(arg(ta, 'false', 'lockpitch', 'lp')) or c.actor is not None
        d = direction(c.yaw(), 0 if lock else c.pitch())
        right = direction(c.yaw() + 90, 0)
        base = c.eye() if boolean(arg(ta, 'false', 'uel', 'useeyelocation')) else c.pos()
        return base + d * f + right * side + [0, y, 0]

    def in_radius(self, ctx, center, r, players, mobs_only, ta=None):
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
            by_type = arg(ta or {}, None, 'types', 'type', 't', 'mobtypes') is not None
            for a in self.actors:
                if a.carried and not by_type:
                    continue
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
        if bone < 0:  # sin el prefijo (ef_suelo → suelo), como VfxModel.bone
            bone = next((i for i, b in enumerate(model[0]['bones'])
                         if b['id'].lower().endswith('_' + part.lower()) and b['id'].index('_') == len(b['id']) - len(part) - 1), -1)
        if bone < 0:
            self.note(f'@modelpart: el modelo {a.me} no tiene el hueso {part} (en el pack; no sale nada, como allí)')
            return None
        mats = B.pose(model[0], a.anim if a.anim in model[0]['anims'] else ('idle' if 'idle' in model[0]['anims'] else None),
                      (a.age - a.anim_start) / 20 * a.anim_speed)
        w = actor_matrix(a.pos, a.yaw) @ np.array(mats[bone])
        return w[:3, 3]

    # --- condiciones (SkillConds.test)
    def test(self, c, who, ctx, at):
        m = c['m']
        neg = m.startswith('!')
        m = m.lstrip('!')
        if m in ('or', 'and'):  # SkillConds: compuesta, cada parte con su true/false
            oks = [self.test(p, who, ctx, at) == (p.get('v', 'true') != 'false') for p in c.get('parts', [])]
            r = any(oks) if m == 'or' else all(oks)
            return r != neg
        a = c.get('a', {})
        if any('<' in str(v) for v in a.values()):
            tg = Tgt(who) if who is not None else (Tgt(at=at) if at is not None else None)
            a = {k: self.text(v, ctx, tg) for k, v in a.items()}
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
            want = unquote(arg(a, '', 'value', 'val', 'v'))
            v = 'UNDEFINED' if v is None else v
            r = str(v).lower() == want.lower() or num(v, -1e9) == num(want, -2e9)
        elif m in ('variableinrange', 'varinrange', 'varrange'):
            var = arg(a, None, 'var', 'variable', 'name', 'key', 'k')
            v = self.var_scope(var, ctx, who).get(var_name(var)) if var else None
            r = v is not None and in_range(num(v), arg(a, '>0', 'value', 'val', 'v', 'range'))
        elif m in ('crouching', 'sneaking', 'issneaking'):
            r = e is self.player and self.crouch
        elif m in ('onground', 'grounded'):
            r = e is None or e.pos[1] <= 0.01
        elif m in ('sprinting', 'issprinting', 'isburning', 'burning', 'onfire',
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
            kind = e.kind if e is not None else str(((who.actor.mob or {}).get('type') if who is not None and who.actor is not None else '') or '').lower()
            r = bool(kind) and any(x.strip().lower() == kind for x in arg(a, '', 'types', 'type', 't').split(','))
        elif m in ('blocktype', 'inblock', 'inside', 'standingon'):
            r = False
        elif m in ('itemissimilar', 'holding'):
            r = e is self.player and self.hand is not None and self.hand == str(arg(a, '', 'i', 'item', 'material', 'm')).lower()
        elif m in ('distance', 'distancefromorigin'):
            p = at if at is not None else (who.pos() if who is not None else ctx.caster.pos())
            frm = ctx.caster.pos() if m == 'distance' else self.origin(ctx)
            r = in_range(float(np.linalg.norm(np.asarray(p) - frm)), arg(a, '>0', 'distance', 'd'))
        elif m in ('health', 'hp', 'healthpercent', 'hppercent', 'hpp'):
            r = e is not None and in_range(e.health if m in ('health', 'hp') else e.health / 20,
                                           arg(a, '>0', 'health', 'h', 'amount', 'a', 'percent', 'p'))
        elif m in ('altitude', 'height'):
            p = at if at is not None else (who.pos() if who is not None else ctx.caster.pos())
            r = in_range(float(p[1]), arg(a, '>0', 'height', 'h', 'a'))
        elif m in ('offgcd', 'gcd'):
            r = self.state(who or ctx.caster)['gcd'] <= self.clock
        elif m == 'stance':
            st = self.state(who or ctx.caster)['stance']
            r = st is not None and st.lower() == str(arg(a, '', 'stance', 's')).lower()
        elif m == 'faction':
            st = self.state(who)['faction'] if who else None
            r = st is not None and st.lower() == str(arg(a, '', 'faction', 'f')).lower()
        elif m in ('hastag', 'tag'):
            r = who is not None and str(arg(a, '', 'tag', 't')).lower() in self.state(who)['tags']
        elif m == 'hastarget':
            r = who.actor.target is not None if who is not None and who.actor is not None else ctx.aim is not None
        elif m in ('mmocantarget', 'cantarget'):
            r = e is not None and self.can_hurt(ctx, e)
        elif m == 'iscaster':
            r = who is not None and who.same(ctx.caster)
        elif m in ('ischild', 'children', 'child'):
            r = who is not None and who.actor is not None and who.actor.owner is not None
        elif m == 'modelhasdriver':
            w = who or ctx.caster
            r = w.actor is not None and w.actor.rider is not None
        elif m in ('drivingmodel', 'isdriving'):
            w = who or ctx.caster
            r = w.entity is not None and any(x.alive and x.rider is w.entity for x in self.actors)
        elif m == 'ownerisonline':
            r = ctx.caster.player() is not None
        elif m in ('ismoving', 'moving'):
            r = e is not None and bool(np.any(np.abs(e.vel[[0, 2]]) > 0.01))
        elif m == 'pitch':
            r = in_range((who or ctx.caster).pitch(), arg(a, '>0', 'pitch', 'p', 'range'))
        elif m == 'onblock':
            p = at if at is not None else (who.pos() if who is not None else ctx.caster.pos())
            names = [x.strip().lower().replace('minecraft:', '') for x in str(arg(a, '', 'material', 'm', 'types', 't', 'b')).split(',')]
            r = ('air' in names) == (p[1] > 0.2)
        elif m == 'hasitem':
            r = False
        elif m in ('mobsinradius', 'mir', 'entitiesinradius', 'eir', 'playersinradius', 'pir'):
            p = at if at is not None else (who.pos() if who is not None else ctx.caster.pos())
            rad = num(arg(a, '5', 'radius', 'r'), 5)
            types = arg(a, None, 'types', 'type', 't', 'mobtypes')
            players = m.startswith('players') or m == 'pir'
            if m in ('mobsinradius', 'mir') and not types:  # como MythicMobs: sin «types» no cuenta ninguno
                n = 0
            elif types and not players:
                names = {x.strip().lower() for x in types.split(',')}
                n = sum(1 for x in self.actors if x.alive and x.name.lower() in names and np.linalg.norm(x.pos - p) <= rad)
            else:
                n = sum(1 for x in self.entities if x.alive and (x.kind == 'player') == players and np.linalg.norm(x.pos - p) <= rad)
            r = in_range(n, arg(a, '>0', 'amount', 'a'))
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
            return self.states.setdefault('global', new_state())['vars']
        return self.state(ctx.caster)['vars']

    # --- mecánicas (SkillRuntime.apply)
    def apply(self, m, ctx, targets):
        name = m['m']
        a = m.get('a', {})
        self.count(name)
        if TRACE:
            self.trace(f'  {name} {m.get("t") or ""} → {len(targets)} obj.' + (f' [{a.get("s") or a.get("type") or a.get("mob") or ""}]'
                                                                          if name in ('skill', 'summon', 'projectile', 'aura', 'signal') else ''))
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
        elif name in ('effect:particles', 'particles', 'particle', 'e:p', 'effect:p', 'effect:particle', 'effect:particlering',
                      'particlering', 'e:pr', 'effect:pr', 'effect:particlesphere', 'particlesphere', 'e:ps', 'effect:ps',
                      'effect:particleorbital', 'particleorbital', 'e:po', 'effect:po', 'effect:particleline',
                      'particleline', 'e:pl', 'effect:pl', 'effect:particlebox', 'particlebox', 'effect:pb'):
            self.particles_fx(name, a, ctx, targets)
        elif name in ('effect:sound', 'sound', 'e:s', 'effect:s'):
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
                    self.remove_actor(t.who.actor)
        elif name == 'equip':
            slot = str(arg(a, 'HEAD', 'slot')).upper()
            item = str(arg(a, '', 'item', 'i')).split(':')[0].lower()
            for t in targets:
                if t.who is not None and t.who.actor is not None:
                    if slot in ('HEAD', '4', 'HELMET'):
                        t.who.actor.head = a.get('model')
                    elif slot in ('HAND', '0', 'MAINHAND'):
                        t.who.actor.hand = a.get('model')
                    if not a.get('model') and slot in ('HEAD', '4', 'HELMET', 'HAND', '0', 'MAINHAND') and item != 'air':
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
                        self.note(f'el modelo {h.me} no tiene la animación {s} (en el pack; no se mueve, como allí)')
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
            self.projectile(a, ctx, targets, name == 'missile', m.get('hc'), m.get('sc'))
        elif name == 'totem':
            self.totem(a, ctx, targets, m.get('hc'))
        elif name == 'orbital':
            self.orbital(a, ctx, targets)
        elif name in ('aura', 'buff', 'debuff', 'ondamaged', 'onattack', 'onshoot'):
            self.aura(a, ctx, targets, name)
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
                    scope[var_name(var)] = unquote(arg(a, '', 'value', 'val', 'v'))
        elif name in ('variablesubtract', 'varsubtract'):
            var = arg(a, None, 'var', 'variable', 'name', 'key', 'k')
            if var:
                scope = self.var_scope(var, ctx, targets[0].who if targets else None)
                scope[var_name(var)] = fmt(num(scope.get(var_name(var)), 0) - num(arg(a, '1', 'amount', 'a', 'value', 'val'), 1))
        elif name in ('setvarloc', 'setvariablelocation'):
            self.set_var_loc(a, ctx, targets)
        elif name in ('addtag', 'removetag'):
            tag = str(arg(a, '', 'tag', 't')).lower()
            for t in targets:
                if t.who is not None and tag:
                    (self.state(t.who)['tags'].add if name == 'addtag' else self.state(t.who)['tags'].discard)(tag)
        elif name == 'setstance':
            for t in targets:
                if t.who is not None:
                    self.state(t.who)['stance'] = arg(a, None, 'stance', 's')
        elif name == 'setfaction':
            for t in targets:
                if t.who is not None:
                    self.state(t.who)['faction'] = arg(a, None, 'faction', 'f')
        elif name in ('gcd', 'setgcd', 'globalcooldown'):
            ticks = int(num(arg(a, '20', 'ticks', 't', 'duration', 'd', '_'), 20))
            for t in targets:
                if t.who is not None:
                    self.state(t.who)['gcd'] = self.clock + ticks
        elif name == 'setskillcooldown':
            other = self.meta(arg(a, None, 'skill', 's'))
            sec = num(arg(a, '0', 'seconds', 's2', 'ticks'), 0)
            for t in targets:
                if other is not None and t.who is not None:
                    cd = self.state(t.who)['cd']
                    nm = other.get('_name', '')
                    if sec <= 0:
                        cd.pop(nm, None)
                    else:
                        cd[nm] = self.clock + round(sec * 20)
        elif name == 'setname':
            raw = str(arg(a, '', 'name', 'n'))
            for t in targets:
                nm = unquote(self.text(raw, ctx, t))
                if t.who is None or not nm:
                    continue
                if t.who.actor is not None:
                    t.who.actor.custom_name = nm
                elif ctx.caster.actor is not None:  # nunca al jugador: el esbirro toma el nombre de su dueño
                    ctx.caster.actor.custom_name = nm
        elif name == 'cancelskill':
            raise CancelSkill()
        elif name == 'signal':
            sig = arg(a, None, 'signal', 's')
            for t in targets:
                if sig and t.who is not None and t.who.actor is not None:
                    self.signal(t.who.actor, sig, ctx.caster)
        elif name == 'settarget':
            if ctx.caster.actor is not None:
                found = None
                if m.get('t'):  # entre los vivos que cumplen, como SkillRuntime
                    ta = dict(m.get('ta') or {})
                    ta.pop('limit', None)
                    alive = [t.entity() for t in self.targets(dict(m, ta=ta), ctx)
                             if t.entity() is not None and self.can_hurt(ctx, t.entity())]
                    if alive:
                        found = random.choice(alive) if str(ta.get('sort', '')).upper() == 'RANDOM' else alive[0]
                ctx.caster.actor.target = found
        elif name in ('setai', 'setnoai'):
            ai = boolean(arg(a, 'true', 'ai', 'a'))
            ai = ai if name == 'setai' else not ai
            for t in targets:
                if t.who is not None and t.who.actor is not None:
                    t.who.actor.ai = ai
        elif name == 'setspeed':
            for t in targets:
                if t.who is not None and t.who.actor is not None:
                    t.who.actor.speed_mul = num(arg(a, '1', 'speed', 's', 'amount', 'a'), 1)
        elif name == 'settextdisplay':
            txt = str(arg(a, '', 'text', 't')).strip('"')
            for t in targets:
                if t.who is not None and t.who.actor is not None:
                    t.who.actor.text = txt
        elif name == 'setmodelscale':
            for t in targets:
                h = self.holder(t)
                if h is not None:
                    h.scale = num(arg(a, '1', 'scale', 's', 'amount', 'a'), 1)
        elif name == 'mountmodel':
            act = ctx.caster.actor
            if act is not None:
                for t in targets:
                    if t.entity() is not None:
                        act.rider = t.entity()
                        act.follow = t.entity()
                        break
        elif name == 'chain':
            self.chain(a, ctx, targets)
        elif name == 'spin':
            for t in targets:
                if t.who is not None and t.who.actor is not None:
                    d = int(num(arg(a, '0', 'duration', 'd'), 0))
                    t.who.actor.spin = num(arg(a, '10', 'velocity', 'v'), 10)
                    t.who.actor.spin_until = t.who.actor.age + d if d > 0 else 10 ** 9
        elif name == 'slash':
            self.slash(a, ctx, targets)
        elif name == 'polygon':
            self.polygon(a, ctx, targets)
        elif name == 'fakelightning':
            for t in targets:
                self.spawn_particles('flash', t.pos() + [0, 0.2, 0], 12, 1.2, 0.1, 0.05)
        elif name == 'modifyprojectile':
            p = ctx.mover
            if p is not None and p['alive']:
                trait = str(arg(a, 'VELOCITY', 'trait', 't')).upper()
                act = str(arg(a, 'SET', 'action', 'a')).upper()
                v = num(arg(a, '1', 'value', 'v'), 1)

                def ap(now, val):
                    return now + val if act == 'ADD' else now * val if act.startswith('MULT') else val
                if trait in ('VELOCITY', 'SPEED'):
                    p['step'] = ap(p['step'], v if act.startswith('MULT') else v / 20 * p['interval'])
                elif trait in ('RADIUS', 'HITRADIUS'):
                    p['hr'], p['vr'] = ap(p['hr'], v), ap(p['vr'], v)
                elif trait == 'GRAVITY':
                    p['gravity'] = ap(p.get('gravity', 0), v if act == 'MULTIPLY' else v / 20)
        elif name in ('endprojectile', 'terminateprojectile'):
            if ctx.mover is not None:
                self.mover_end(ctx.mover)
        elif name == 'setprojectiledirection':
            p = ctx.mover
            if p is not None and p['alive'] and targets:
                d = targets[0].pos() - p['pos']
                if np.linalg.norm(d) > 1e-6:
                    p['dir'] = d / np.linalg.norm(d)
        elif name in ('onswing', 'ondeath'):
            self.aura(a, ctx, targets, name)
        elif name in NOOP:
            pass
        else:
            self.problem(f'mecánica {name} no soportada')

    def call(self, name, ctx, targets):
        meta = self.meta(name)
        if meta is None:
            if name and str(name).strip().lower() not in self.known_missing:
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
                e.vel = np.clip(np.array([away[0], vy, away[2]]), -6.0, 6.0)
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
                    if ctx.caster.entity is None:  # SkillMotion.propel: solo entidades, y se suma a lo que ya lleva
                        break
                    vel = ctx.caster.entity.vel + d / n * num(arg(a, '1', 'velocity', 'v'), 1) / 10
                if ctx.caster.entity is not None:
                    ctx.caster.entity.vel = np.clip(vel, -6.0, 6.0)  # SkillMotion.push: como mucho 6 por eje
                elif ctx.caster.actor is not None:
                    ctx.caster.actor.pos = ctx.caster.actor.pos + vel
                break
        elif name == 'jump':
            for t in targets:
                if t.entity() is not None:
                    t.entity().vel[1] = num(arg(a, '1', 'velocity', 'v'), 1)

    def from_string(self, text, ctx):
        """SkillTargets.fromString"""
        mt = re.match(r'^@(\w+)(?:\{(.*)\})?$', text.strip())
        if not mt:
            return []
        ta = {}
        for part in (mt.group(2) or '').split(';'):
            if '=' in part:
                k, x = part.split('=', 1)
                ta[k.strip().lower()] = x.strip()
        return self.targets({'m': 'origin', 't': mt.group(1).lower(), 'ta': ta}, ctx)

    def set_var_loc(self, a, ctx, targets):
        """SkillRuntime.setVarLoc"""
        var = arg(a, None, 'var', 'variable', 'name', 'key', 'k')
        if not var:
            return
        v = str(arg(a, '', 'value', 'val', 'v', 'location', 'l')).strip().strip('"')
        at = None
        if v.startswith('@'):
            r = self.from_string(v, ctx)
            if r:
                at = r[0].pos()
        elif len(v.split(',')) >= 3:
            p = v.split(',')
            at = np.array([num(p[0]), num(p[1]), num(p[2])])
        if at is None and targets:
            at = targets[0].pos()
        if at is None:
            return
        self.var_scope(var, ctx, targets[0].who if targets else None)[var_name(var)] = \
            f'{fmt(round(float(at[0]), 3))},{fmt(round(float(at[1]), 3))},{fmt(round(float(at[2]), 3))}'

    def signal(self, act, sig, frm):
        """SkillActor.signal"""
        if not act.alive or not act.mob:
            return
        for mm in act.mob.get('mechs', []):
            if mm.get('tr') != 'onsignal' or str(mm.get('trv', '')).lower() != str(sig).lower():
                continue
            c = Ctx(self.cls, Who(actor=act))
            c.origin = act.pos.copy()
            c.trigger = frm
            c.aim = act.target
            self.exec(mm, c)

    def remove_actor(self, act):
        """SkillActor.remove (con sus ~onDespawn)"""
        if not act.alive or act.leaving:
            return
        act.leaving = True
        for mm in (act.mob or {}).get('mechs', []):
            if mm.get('tr') in ('ondespawn', 'onremove'):
                c = Ctx(self.cls, Who(actor=act))
                c.origin = act.pos.copy()
                c.trigger = act.owner
                try:
                    self.exec(mm, c)
                except CancelSkill:
                    pass
        act.alive = False
        act.rider = None
        if self.disguise is act:
            self.disguise = None

    def chain(self, a, ctx, targets):
        """SkillMotion.chain"""
        bounces = int(max(1, min(32, num(arg(a, '3', 'bounces', 'b'), 3))))
        radius = num(arg(a, '5', 'bounceradius', 'br', 'radius', 'r'), 5)
        delay = int(max(1, num(arg(a, '2', 'bouncedelay', 'bd'), 2)))
        skill = self.meta(arg(a, None, 'onbounce', 'ob', 'onbounceskill'))
        first = next((t.entity() for t in targets if t.entity() is not None and self.can_hurt(ctx, t.entity())), None)
        if first is None or skill is None:
            return
        done = set()

        def bounce(at, left, frm):
            if at is None or not at.alive or left <= 0:
                return
            done.add(at.uid)
            c = ctx.copy()
            c.targets = [Tgt(Who(entity=at))]
            c.origin = frm.pos.copy() if frm is not None else ctx.caster.pos().copy()
            c.trigger = Who(entity=at)
            self.run_meta(skill, c)

            def nxt():
                best, bd = None, radius * radius
                for e in self.entities:
                    if e.uid in done or not e.alive or not self.can_hurt(ctx, e):
                        continue
                    d = float(np.sum((e.pos - at.pos) ** 2))
                    if d < bd:
                        best, bd = e, d
                bounce(best, left - 1, at)
            self.later(delay, nxt)
        bounce(first, bounces, None)

    def run_point(self, ctx, meta, p):
        c = ctx.copy()
        c.origin = np.array(p, float)
        c.targets = [Tgt(at=p)]
        self.run_meta(meta, c)

    def slash(self, a, ctx, targets):
        """SkillFx.slash"""
        meta = self.meta(arg(a, None, 'onpoint', 'op', 'onpointskill'))
        onhit = self.meta(arg(a, None, 'onhit', 'oh', 'onhitskill'))
        hr = max(0, num(arg(a, '1', 'radius', 'r', 'hitradius', 'hr'), 1))
        if meta is None and onhit is None:
            return
        w = num(arg(a, '3', 'width', 'w'), 3) / 2
        h = num(arg(a, str(w * 2), 'height', 'h'), w * 2) / 2
        arc = math.radians(num(arg(a, '180', 'angle', 'a', 'arc'), 180))
        roll = num(arg(a, '0', 'roll', 'rl'), 0)
        rot = arg(a, None, 'rot', 'rotation')
        if rot and len(str(rot).split(',')) >= 3:
            roll += num(str(rot).split(',')[2], 0)
        dur = int(max(0, num(arg(a, '0', 'duration', 'd'), 0)))
        pts = int(max(2, min(240, num(arg(a, '30', 'points', 'p'), 30))))
        fo, yo = num(arg(a, '0', 'forwardoffset', 'fo'), 0), num(arg(a, '0', 'yoffset', 'y'), 0)
        yaw = ctx.caster.yaw()
        fwd, right = direction(yaw, 0), direction(yaw + 90, 0)
        rr = math.radians(roll)
        axis = right * math.cos(rr) + np.array([0, 1, 0]) * math.sin(rr)
        for t in targets:
            c = t.pos() + fwd * fo + [0, yo, 0]
            hit = set()  # cada tajo le da una sola vez a cada uno
            for i in range(pts):
                k = i / (pts - 1)
                ang = -arc / 2 + arc * k
                p = c + axis * math.sin(ang) * w + fwd * (math.cos(ang) * h - h * 0.5)
                when = 0 if dur <= 0 else round(k * dur)

                def go(p=p, hit=hit):
                    if meta is not None:
                        self.run_point(ctx, meta, p)
                    if onhit is not None:
                        self.slash_hit(ctx, onhit, p, hr, hit)
                self.later(when, go)

    def slash_hit(self, ctx, meta, p, r, hit):
        """SkillFx.slashHit: los vivos cuya caja toca la de «r» alrededor del punto"""
        p = np.array(p, float)
        for e in list(self.entities):
            if not e.alive or e is ctx.caster.entity or e is ctx.caster.player() or e.uid in hit:
                continue
            if not self.can_hurt(ctx, e):
                continue
            lo, hi = e.pos - [0.3, 0, 0.3], e.pos + [0.3, 1.8, 0.3]
            if (hi >= p - r).all() and (lo <= p + r).all():
                hit.add(e.uid)
                c = ctx.copy()
                c.origin = p
                c.targets = [Tgt(Who(entity=e))]
                self.run_meta(meta, c)

    def polygon(self, a, ctx, targets):
        """SkillFx.polygon"""
        pts = int(max(1, min(64, num(arg(a, '5', 'points', 'p'), 5))))
        skip = int(max(1, num(arg(a, '1', 'skip', 's'), 1)))
        scale_ = num(arg(a, '3', 'scale', 'radius', 'r'), 3)
        yaw0 = math.radians(num(arg(a, '0', 'yaw', 'rotation'), 0))
        db = max(0.1, num(arg(a, '0.5', 'distancebetween', 'db'), 0.5))
        y = num(arg(a, '0', 'yoffset', 'y'), 0)
        corner = self.meta(arg(a, None, 'onstart', 'os', 'onvertex'))
        edge = self.meta(arg(a, None, 'onend', 'oe', 'onpoint', 'op'))
        if corner is None and edge is None:
            return
        for t in targets:
            c = t.pos() + [0, y, 0]
            v = [c + [math.cos(yaw0 + math.pi * 2 * i / pts) * scale_, 0, math.sin(yaw0 + math.pi * 2 * i / pts) * scale_]
                 for i in range(pts)]
            if corner is not None:
                for p in v:
                    self.run_point(ctx, corner, p)
            if edge is None or skip % pts == 0:
                continue
            for i in range(pts):
                a0, b0 = v[i], v[(i + skip) % pts]
                n = int(min(200, math.ceil(np.linalg.norm(b0 - a0) / db)))
                for k in range(1, n):
                    self.run_point(ctx, edge, a0 + (b0 - a0) * k / n)

    def minion_tick(self, act):
        """SkillMinions.tick (en el mundo de prueba el suelo está en y = 0)"""
        if act.target is not None and (not act.target.alive or np.linalg.norm(act.target.pos - act.pos) > 32):
            act.target = None
        sets_own = any(mm['m'] == 'settarget' and mm.get('t') for mm in act.mob.get('mechs', []))
        if act.target is None and not sets_own and act.age % 10 == 0:
            cands = [e for e in self.mobs_world if e.alive and np.linalg.norm(e.pos - act.pos) <= 24]
            act.target = min(cands, key=lambda e: np.linalg.norm(e.pos - act.pos)) if cands else None
        owner = act.owner.entity if act.owner is not None else None
        goal, stop = None, 1.8
        if act.target is not None:
            goal = act.target.pos
        elif owner is not None and np.linalg.norm(owner.pos - act.pos) > 3.5:
            goal, stop = owner.pos, 2.5
        frozen = not act.ai or 'noai' in self.state(Who(actor=act))['auras']
        if goal is None or frozen:
            act.pos[1] = 0.0
            act.walking = False
            return
        to = goal - act.pos
        to[1] = 0
        dist = float(np.linalg.norm(to))
        if dist > 1e-3:
            act.yaw = yaw_of(to)
        if dist <= stop:
            act.walking = False
            return
        speed = num((act.mob.get('options') or {}).get('MovementSpeed'), 0.25)
        step = min(dist - stop + 0.05, min(1.2, speed * 0.72 * act.speed_mul))
        act.pos = act.pos + to / dist * step
        act.pos[1] = 0.0
        act.walking = True

    # --- efectos (SkillRuntime.summon/spawn)
    def summon(self, a, ctx, targets):
        t = arg(a, None, 'type', 't', 'mob', 'm')
        mob = self.mobs.get(str(t).lower())
        if mob is None:
            self.problem(f'falta el mob {t}')
            return
        amount = int(max(1, min(32, num(arg(a, '1', 'amount', 'a'), 1))))
        radius = num(arg(a, '0', 'radius', 'r'), 0)
        yr = num(arg(a, '0', 'yradius', 'yr'), 0)
        up_only = boolean(arg(a, 'false', 'yradiusuponly', 'yu'))
        surface = boolean(arg(a, 'false', 'onsurface', 'os'))
        on_summon = self.meta(arg(a, None, 'onsummon', 'onsummonskill'))
        living = str(mob.get('type', '')).upper() in LIVING
        for tg in targets:
            for _ in range(amount):
                at = tg.pos().copy()
                if radius > 0:
                    at += [(random.random() * 2 - 1) * radius, 0, (random.random() * 2 - 1) * radius]
                if yr > 0:
                    at[1] += random.random() * yr if up_only else (random.random() * 2 - 1) * yr
                if surface or living:
                    at[1] = 0.0
                act = self.spawn(ctx, mob, at, ctx.caster.yaw(), 0.0)
                if act is not None and on_summon is not None:
                    c = ctx.copy()
                    c.targets = [Tgt(Who(actor=act))]
                    c.aim = None
                    self.run_meta(on_summon, c)

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
            elif tr in ('onspawn', 'onready'):
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
            sc = num(arg(a, '1', 'scale', 'size'), 1)
            if sc > 0:
                h.scale = sc

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
    def projectile(self, a, ctx, targets, missile, hc=None, sc=None):
        for t in targets:
            p = self.mover(ctx, a, 1 if missile else 0)
            p['hc'], p['sc'] = hc or [], sc or []
            fo = boolean(arg(a, 'false', 'fromorigin', 'fo'))
            tio = boolean(arg(a, 'false', 'targetisorigin', 'tio'))
            base = t.pos() if tio else self.origin(ctx) if fo else ctx.caster.pos()
            start0 = base + [0, num(arg(a, '1', 'startyoffset', 'syo'), 1), 0]
            target = t.pos() + [0, num(arg(a, '1', 'targetyoffset', 'tyo'), 1), 0]
            tso = num(arg(a, '0', 'targetsideoffset', 'tso'), 0)
            if tso:
                target = target + direction(yaw_of(target - start0) + 90, 0) * tso
            d = ctx.caster.forward() if tio else target - start0
            if np.linalg.norm(d) < 1e-6:
                d = ctx.caster.forward()
            d = d / np.linalg.norm(d)
            ho, vo = num(arg(a, '0', 'horizontaloffset', 'ho'), 0), num(arg(a, '0', 'verticaloffset', 'vo'), 0)
            if ho:  # como MythicMobs: «ho» gira (grados) y «vo» se suma a la Y
                d = direction(yaw_of(d) + ho, pitch_of(d))
            if vo:
                d2 = d + [0, vo, 0]
                if np.linalg.norm(d2) > 1e-6:
                    d = d2 / np.linalg.norm(d2)
            flat = np.array([d[0], 0, d[2]])
            flat = flat / np.linalg.norm(flat) if np.linalg.norm(flat) > 1e-6 else np.zeros(3)
            cy = ctx.caster.yaw()  # sfo/sso según hacia dónde mira quien lanza (como MythicMobs)
            p['pos'] = (start0 + direction(cy, 0) * num(arg(a, '1', 'startforwardoffset', 'sfo'), 1)
                        + direction(cy + 90, 0) * num(arg(a, '0', 'startsideoffset', 'sso'), 0))
            p['dir'] = d
            p['step'] = num(arg(a, '5', 'velocity', 'v'), 5) / 20 * p['interval']
            p['range'] = num(arg(a, '40', 'maxrange', 'mr'), 40)
            p['ticks'] = int(num(arg(a, '400', 'maxduration', 'md', 'duration', 'd'), 400))
            p['gravity'] = num(arg(a, '0', 'gravity', 'g'), 0) / 20
            p['hug'] = boolean(arg(a, 'false', 'hugsurface', 'hs'))
            p['hfs'] = num(arg(a, '0.5', 'heightfromsurface', 'hfs'), 0.5)
            if p['hug']:
                fl = np.array([d[0], 0, d[2]])
                p['dir'] = fl / np.linalg.norm(fl) if np.linalg.norm(fl) > 1e-6 else direction(ctx.caster.yaw(), 0)
                p['pos'] = np.array([p['pos'][0], p['hfs'], p['pos'][2]])
            if boolean(arg(a, 'false', 'hittargetonly', 'hto')):
                p['only'] = t.entity()
            if missile:
                p['homing'] = t.entity() or ctx.aim
                p['inertia'] = max(0.1, num(arg(a, '1.5', 'inertia', 'in'), 1.5))
            self.start_mover(p, a)

    def totem(self, a, ctx, targets, hc=None):
        for t in targets:
            p = self.mover(ctx, a, 2)
            p['hc'] = hc or []
            p['pos'] = t.pos() + [0, num(arg(a, '0', 'yoffset', 'y', 'yo'), 0), 0]
            p['dir'] = ctx.caster.forward()
            p['ticks'] = int(num(arg(a, '200', 'maxduration', 'md', 'duration', 'd'), 200))
            self.start_mover(p, a)

    def orbital(self, a, ctx, targets):
        aura_name = arg(a, None, 'auraname', 'aura', 'n', 'buffname')
        refresh = boolean(arg(a, 'false', 'refreshduration', 'rd')) or boolean(arg(a, 'false', 'mergeall', 'ma')) \
            or boolean(arg(a, 'false', 'mergesamecaster', 'ms'))  # SkillMovers: se suma a la que hay
        for t in targets:
            if t.who is None:
                continue
            dur = int(num(arg(a, '100', 'duration', 'd', 'maxduration', 'md', 'ticks'), 100))
            if aura_name:
                old = self.state(t.who)['auras'].get(str(aura_name).lower())
                if old and old['alive'] and old.get('mover') is not None and old['mover']['alive']:
                    if refresh:
                        old['left'] = max(old['left'], dur)
                        old['mover']['ticks'] = max(old['mover']['ticks'], old['mover']['age'] + dur)
                    continue
            p = self.mover(ctx, a, 3)
            p['center'] = t.who
            p['radius'] = num(arg(a, '4', 'radius', 'r'), 4)
            p['astep'] = math.pi * 2 / max(1, num(arg(a, '32', 'points', 'p'), 32)) * \
                (-1 if boolean(arg(a, 'false', 'reversed', 'rev')) else 1)
            p['oy'] = num(arg(a, '0', 'offsety', 'oy', 'yoffset'), 0)
            p['ticks'] = dur
            p['angle'] = p['astep'] * num(arg(a, '0', 'startingpoint', 'sp'), 0)
            p['rot'] = [math.radians(num(arg(a, '0', k1, k2), 0)) for k1, k2 in
                        (('rotationx', 'rx'), ('rotationy', 'ry'), ('rotationz', 'rz'))]
            p['av'] = [math.radians(num(arg(a, '0', k1, k2), 0)) for k1, k2 in
                       (('angularvelocityx', 'avx'), ('angularvelocityy', 'avy'), ('angularvelocityz', 'avz'))]
            p['pos'] = self.orbit_pos(p)
            p['dir'] = np.zeros(3)
            if aura_name:
                p['aura'] = self.aura_for_mover(p, aura_name, t.who, ctx, dur)
            self.start_mover(p, a)

    def orbit_pos(self, p):
        o = np.array([math.cos(p['angle']) * p['radius'], 0.0, math.sin(p['angle']) * p['radius']])
        t = p['age']
        rx, ry, rz = (p['rot'][i] + p['av'][i] * t for i in range(3))
        # como Vec3.xRot / yRot / zRot
        c, s_ = math.cos(rx), math.sin(rx)
        o = np.array([o[0], o[1] * c + o[2] * s_, o[2] * c - o[1] * s_])
        c, s_ = math.cos(ry), math.sin(ry)
        o = np.array([o[0] * c + o[2] * s_, o[1], o[2] * c - o[0] * s_])
        c, s_ = math.cos(rz), math.sin(rz)
        o = np.array([o[0] * c + o[1] * s_, o[1] * c - o[0] * s_, o[2]])
        return p['center'].pos() + o + [0, p['oy'], 0]

    def aura_for_mover(self, p, name, on, ctx, dur):
        """SkillAuras.forMover"""
        st = self.state(on)
        c = ctx.copy()
        c.targets = [Tgt(on)]
        au = {'name': str(name).lower(), 'on': on, 'ctx': c, 'left': dur, 'interval': 1, 'stacks': 1, 'max': 1,
              'ontick': None, 'onend': None, 'age': 0, 'alive': True, 'st': st, 'mover': p}
        st['auras'][au['name']] = au
        self.auras.append(au)
        return au

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
                'range': 1e9, 'step': 0.0, 'bullet': None, 'hc': [], 'sc': [], 'hug': False, 'hfs': 0.5, 'only': None,
                'ohb': arg(a, None, 'onhitblock', 'ohb', 'onhitblockskill'),
                'immune': int(num(arg(a, '0', 'immunedelay', 'id'), 0)), 'hit_at': {},
                'death': int(num(arg(a, '0', 'deathdelay', 'dd'), 0)), 'aura': None,
                'byo': num(arg(a, '0', 'bulletyoffset', 'byo'), 0)}

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
                if p['bullet'] is not None:
                    p['bullet'].carried = True
        os_ = arg(a, None, 'onstart', 'os', 'onstartskill')
        if os_:
            self.mover_run(p, os_, [Tgt(at=p['pos'])], None)
        self.movers.append(p)

    def mover_run(self, p, skill, targets, trigger):
        c = p['ctx'].copy()
        c.origin = np.array(p['pos'], float)
        c.targets = targets
        c.dir = p['dir']
        c.mover = p
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
            try:
                self.mover_run(p, p['onend'], [Tgt(at=p['pos'])], None)
            except CancelSkill:
                pass
        if p['bullet'] is not None:
            b = p['bullet']
            if p['death'] > 0:
                self.later(p['death'], lambda: self.remove_actor(b))
            else:
                self.remove_actor(b)
        au = p.get('aura')
        if au is not None and au['alive']:
            self.finish_aura(au)

    def mover_step(self, p):
        p['age'] += 1
        au = p.get('aura')
        if au is not None and not au['alive']:
            self.mover_end(p)
            return
        for c in p['sc']:
            r = self.test(c, p['ctx'].caster, p['ctx'], p['pos'])
            if r == (c.get('v', 'true') not in ('false', 'cancel')):
                self.mover_end(p)
                return
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
            if p.get('gravity') and not p['hug']:  # SkillMovers: la gravedad se resta a la velocidad (como MythicMobs)
                vel = p['dir'] * p['step'] + [0, -p['gravity'] * p['interval'], 0]
                sp = float(np.linalg.norm(vel))
                if sp > 1e-6:
                    p['dir'], p['step'] = vel / sp, sp
            to = p['pos'] + p['dir'] * p['step']
            if p['hug']:
                to[1] = p['hfs']  # el suelo del mundo de prueba es plano
            elif p['sb'] and to[1] < 0 <= p['pos'][1] and p['dir'][1] < 0:  # el suelo del mundo de prueba
                k = p['pos'][1] / max(1e-6, p['pos'][1] - to[1])
                p['pos'] = p['pos'] + (to - p['pos']) * k
                self.move_bullet(p)
                if p['ohb']:
                    self.mover_run(p, p['ohb'], [Tgt(at=p['pos'])], None)
                self.mover_end(p)
                return
            p['travelled'] += p['step']
            p['pos'] = to
        elif p['kind'] == 3:
            if not p['center'].alive():
                self.mover_end(p)
                return
            p['angle'] += p['astep']
            np_ = self.orbit_pos(p)
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
            # efectos del pack que el proyectil busca (hitConditions con mythicmobtype true), como SkillMovers
            if wants_actors(p['hc']):
                for act in list(self.actors):
                    if not act.alive or act.follow is not None or act is p['bullet'] or ('a', act.id) in p['hit_at']:
                        continue
                    if not ((lo - 0.3 <= act.pos).all() and (hi + 0.3 >= act.pos).all()):
                        continue
                    if not self.passes_conds(p['hc'], Tgt(Who(actor=act)), p['ctx']):
                        continue
                    p['hit_at'][('a', act.id)] = self.clock
                    p['hits'] += 1
                    self.count('impacto')
                    if p['onhit']:
                        self.mover_run(p, p['onhit'], [Tgt(Who(actor=act))], None)
                    if not p['alive']:
                        return
                    if p['se'] or (p['charges'] and p['hits'] >= p['charges']):
                        self.mover_end(p)
                        return
            for e in self.entities:
                if not e.alive or e is p['ctx'].caster.entity or e is p['ctx'].caster.player():
                    continue
                last = p['hit_at'].get(e.uid)
                if last is not None and (p['immune'] <= 0 or self.clock - last < p['immune']):
                    continue
                if p['only'] is not None and e is not p['only']:
                    continue
                if (e.kind == 'player' and not p['hp']) or (e.kind != 'player' and not p['hnp']):
                    continue
                if not self.can_hurt(p['ctx'], e):
                    continue
                if p['hc'] and not self.passes_conds(p['hc'], Tgt(Who(entity=e)), p['ctx']):
                    continue
                c = e.pos + [0, 0.9, 0]
                if (lo <= c + [0.3, 0.9, 0.3]).all() and (hi >= c - [0.3, 0.9, 0.3]).all():
                    p['hit'].add(e.uid)
                    p['hit_at'][e.uid] = self.clock
                    p['hits'] += 1
                    self.count('impacto')
                    if p['onhit']:
                        self.mover_run(p, p['onhit'], [Tgt(Who(entity=e))], e)
                    if not p['alive']:
                        return
                    if p['se'] or (p['charges'] and p['hits'] >= p['charges']):
                        self.mover_end(p)
                        return
        if p['travelled'] >= p['range'] or p['age'] >= p['ticks']:
            self.mover_end(p)

    def move_bullet(self, p):
        b = p['bullet']
        if b is None or not b.alive:
            return
        b.pos = np.array(p['pos'], float) + [0, p.get('byo', 0), 0]
        d = p['dir']
        if np.linalg.norm(d) > 1e-6:
            b.yaw = yaw_of(d)
            if p['kind'] in (0, 1):
                b.pitch = pitch_of(d)

    # --- auras (SkillAuras)
    def aura(self, a, ctx, targets, kind='aura'):
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
                    mv = au.get('mover')  # SkillAuras: el aura de una órbita alarga la órbita
                    if mv is not None and mv['alive']:
                        mv['ticks'] = max(mv['ticks'], mv['age'] + dur)
                continue
            c = ctx.copy()
            c.targets = [Tgt(t.who)]
            au = {'name': name, 'on': t.who, 'ctx': c, 'left': dur, 'interval': int(max(1, num(arg(a, '1', 'interval', 'i'), 1))),
                  'stacks': 1, 'max': int(max(1, num(arg(a, '1', 'maxstacks', 'ms', 'stacks'), 1))),
                  'ontick': arg(a, None, 'ontick', 'ot', 'ontickskill'), 'onend': arg(a, None, 'onend', 'oe', 'onendskill'),
                  'age': 0, 'alive': True, 'st': st, 'kind': kind,
                  'charges': int(num(arg(a, '0', 'charges', 'c'), 0)),
                  'onevent': arg(a, None, 'onswing', 'onswingskill', 'os2') if kind == 'onswing' else None}
            st['auras'][name] = au
            self.auras.append(au)
            os_ = arg(a, None, 'onstart', 'os', 'onstartskill')
            if os_:
                self.run_meta(self.meta(os_), self.aura_ctx(au))

    def swing(self):
        """SkillAuras.swing: el jugador da un golpe (clic izquierdo): sus auras onSwing"""
        for au in list(self.state(Who(entity=self.player))['auras'].values()):
            if not au['alive'] or au.get('kind') != 'onswing':
                continue
            if au['onevent']:
                try:
                    self.run_meta(self.meta(au['onevent']), self.aura_ctx(au))
                except CancelSkill:
                    pass
            if au['charges'] > 0:
                au['charges'] -= 1
                if au['charges'] <= 0:
                    self.finish_aura(au)

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
        if au.get('mover') is not None and au['mover']['alive']:
            self.mover_end(au['mover'])
        if au['onend']:
            self.run_meta(self.meta(au['onend']), self.aura_ctx(au))

    # --- tick (SkillRuntime.tick)
    def tick(self):
        self.clock += 1
        # Pasivas por tiempo (SkillServer.onServerTick)
        for sk in self.passives:
            every = max(1, round(float(sk['passive'].get('timer') or 20)))  # ticks, como MythicLib
            if (self.clock - 1) % every == 0:  # como SkillServer: saltan ya y luego cada «timer»
                self.cast(sk)
        due = [t for t in self.tasks if t[0] <= self.clock]
        self.tasks = [t for t in self.tasks if t[0] > self.clock]
        for _, fn in due:
            try:
                fn()
            except CancelSkill:
                pass
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
            if getattr(act, 'spin', 0) and act.age <= act.spin_until and act.follow is None:
                act.yaw += act.spin
            if act.follow is not None:
                act.pos, act.yaw = act.follow.pos.copy(), act.follow.yaw
            elif act.living:
                self.minion_tick(act)
            for mm in act.timers:
                every = int(max(1, num(mm.get('trv'), 20)))
                if act.age % every == 0:
                    c = Ctx(self.cls, Who(actor=act))
                    c.origin = act.pos.copy()
                    c.trigger = act.owner
                    c.aim = act.target
                    try:
                        self.exec(mm, c)
                    except CancelSkill:
                        pass
            if act.follow is None and act.age > (20 * 60 * 90 if act.carried else 1200):  # SkillRuntime: MAX_AGE
                self.remove_actor(act)
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


def new_state():
    return {'auras': {}, 'vars': {}, 'cd': {}, 'tags': set(), 'stance': None, 'faction': None, 'gcd': 0}


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


TRACE = False
CAM = (-35, -20)  # giro e inclinación de la cámara de las vistas previas (webp)
_GLYPHS = {}


def glyphs_of(cid):
    """Las letras-imagen de la fuente de la clase: carácter → (imagen, ascent, height)."""
    if cid not in _GLYPHS:
        out = {}
        path = os.path.join(RES, 'tfclient', 'font', f'skills_{cid}.json')
        if os.path.exists(path):
            from PIL import Image
            for prov in json.load(open(path, encoding='utf-8')).get('providers', []):
                ns, _, rel = prov['file'].partition(':')
                png = os.path.join(RES, ns, 'textures', rel)
                if os.path.exists(png):
                    img = np.asarray(Image.open(png).convert('RGBA'), np.float32) / 255
                    for ch in prov['chars']:
                        out[ch] = (img, prov.get('ascent', 7), prov.get('height', 8))
        _GLYPHS[cid] = out
    return _GLYPHS[cid]


def text_tris(act, cid):
    """Un text_display con letras-imagen, como SkillClient.renderText: 1/40 de bloque por píxel, mirando a la cámara."""
    g = glyphs_of(cid)
    rot = R.look_matrix(*CAM)
    right, up, n = -rot[0], rot[1], -rot[2]
    do = (act.mob or {}).get('display_options') or {}
    sc = [float(x) for x in str(do.get('Scale', '1,1,1')).split(',')] if do.get('Scale') is not None else [1, 1, 1]
    sc = (sc * 3)[:3]
    chars = [ch for ch in act.text if ch in g]
    if not chars:
        return []
    widths = [g[ch][0].shape[1] * g[ch][2] / g[ch][0].shape[0] for ch in chars]
    x = -sum(widths) / 2 + 1
    out = []
    for ch, w in zip(chars, widths):
        img, asc, h = g[ch]
        top = (3 + asc) * 0.025 * sc[1]
        bottom = top - h * 0.025 * sc[1]
        x0, x1 = x * 0.025 * sc[0], (x + w) * 0.025 * sc[0]
        x += w
        c = act.pos
        tl, tr = c + right * x0 + up * top, c + right * x1 + up * top
        br, bl = c + right * x1 + up * bottom, c + right * x0 + up * bottom
        out.append((np.array([tl, tr, br]), np.array([[0, 0], [1, 0], [1, 1]]), img, True, n, None))
        out.append((np.array([tl, br, bl]), np.array([[0, 0], [1, 1], [0, 1]]), img, True, n, None))
    return out


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
                world = actor_matrix(act.pos, act.yaw) @ np.diag([act.scale, act.scale, act.scale, 1.0])
                tris += R.model_tris(model, tex, mats, world=world, tick=act.age, hidden=hidden, tint=act.tint)
        if act.text:
            tris += text_tris(act, sim.cls['id'])
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
    if hand is not None:
        sim.hand = hand
    # Para la vista previa: lo que la skill pide para salir (en su entrada y en lo que llama directamente): las
    # auras que pide tener (con las cargas que pide), agachado, en el aire...
    meta = sim.meta(skill.get('entry'))
    metas = [meta] if meta else []

    def only_call(mt):
        calls = [m for m in mt.get('mechs', []) if m['m'] in ('skill', 'metaskill', 'cast')]
        return sim.meta(str(arg(calls[0].get('a', {}), '', 's', 'skill')).split(',')[0].strip()) if len(calls) == 1 else None
    cur = meta
    for _ in range(3):  # la cadena directa (entrada → SKILL → CAST): no las variantes de un combo
        cur = only_call(cur) if cur else None
        if cur is None:
            break
        metas.append(cur)
    st = sim.state(Who(entity=sim.player))
    for mt in metas:
        for c in mt.get('conditions', []):
            v = c.get('v', 'true')
            want = v == 'true' or v.startswith('orelsecast')
            if c['m'] in ('hasaura', 'hasaurastacks') and want:
                name = str(arg(c.get('a', {}), '', 'auraname', 'aura', 'name', 'n')).lower()
                rng = str(arg(c.get('a', {}), '1', 'stacks', 's', 'amount', 'a'))
                stacks = 1
                mm = re.match(r'^(>=?)?(\d+)', rng.replace(' ', ''))
                if mm:
                    stacks = int(mm.group(2)) + (1 if mm.group(1) == '>' else 0)
                if name and name not in st['auras'] and not re.search(r'<(caster|target|skill|trigger|modifier)\.', name):
                    st['auras'][name] = {'name': name, 'on': Who(entity=sim.player), 'ctx': Ctx(sim.cls, Who(entity=sim.player)),
                                         'left': 400, 'interval': 1, 'stacks': max(1, stacks), 'max': max(1, stacks),
                                         'ontick': None, 'onend': None, 'age': 0, 'alive': True, 'st': st}
            if c['m'] in ('crouching', 'sneaking', 'issneaking') and want:
                sim.crouch = True
            if c['m'] in ('onground', 'grounded') and v == 'false':
                sim.player.pos = sim.player.pos + [0, 1.2, 0]
                sim.player.vel = np.array([0.0, 0.25, 0.0])
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
    imgs = [R.raster(t, size=size, yaw=CAM[0], pitch=CAM[1], dist=dist, center=center, fov=40, ss=1, points=p) for t, p in frames]
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
    ticks = 200
    global TRACE
    if '--trace' in args:
        TRACE = True
        args.remove('--trace')
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
            for p in sim.notes:
                print('     · nota:', p)
            for p in sim.traces:
                print('       ', p)
            total_problems += len(sim.problems)
            if webp_dir:
                os.makedirs(os.path.join(webp_dir, cid), exist_ok=True)
                webp(frames, os.path.join(webp_dir, cid, sk['id'] + '.webp'))
    print(f'problemas: {total_problems}')


if __name__ == '__main__':
    main()
