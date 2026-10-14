"""Simula la IA de un jefe sin abrir el juego: ejecuta su programa (bosses/<jefe>.json) con las mismas reglas que el
motor de Java (SkillRuntime/SkillConds/SkillMobs) contra un jugador que se mueve a su alrededor y le pega, y cuenta qué
ataques salen. Sirve para ver si el pack se queda bloqueado (una variable que no caduca, un cooldown que se pone sin
salir, un gcd que no es del jefe...) y si usa todos sus ataques.

    python3 tools/bosses/sim_ai.py [jefe ...] [--min 5] [--seed 1] [-v]

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

# Estados que no son ataques (reposo, pasivos, transiciones)
NOT_ATTACKS = {'passive', 'passive_tongue', 'passive_tentacle', 'idle', 'walk', 'sleep', 'land', 'in_air', 'hurt',
               'spawn', 'shield_charge_run', 'turn_left', 'turn_right', 'shield_hit', 'stun_hit', 'statue'}


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
        opts = self.mob.get('options') or {}
        self.type_speed = self.mob.get('speed', 0.25)
        self.base_speed = float(opts.get('MovementSpeed', self.type_speed))
        self.speed_mul = 1.0
        self.speed_set = False
        goals = [str(g).lower() for g in (self.mob.get('ai_goals') or [])]
        self.chases = not (goals == ['clear'])
        self.target = False
        self.combat = False
        self.fired = {}
        self.timeline = []

    def speed(self):
        return (self.type_speed if self.speed_set else self.base_speed) * self.speed_mul

    def var(self, name):
        return self.vars.get(name.lower().split('.', 1)[-1] if name.lower().startswith(('caster.', 'skill.')) else name.lower())


OLD = False  # --antes: como el motor de la 1.3.75 (para comparar)


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
        self.unknown = set()

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
        # se acerca o se aleja a 0,2 b/t y rodea al jefe
        radial = max(-0.28, min(0.28, (self.want_dist - d) * 0.1))  # corre (sprint)
        tx, tz = -dz / d, dx / d
        side = 0.12 if (self.t // 100) % 2 == 0 else -0.12
        self.px += dx / d * radial + tx * side
        self.pz += dz / d * radial + tz * side
        # pega si está cerca
        if d < 4.5 and self.t % 12 == 0:
            self.damage(6.0, 'ENTITY_ATTACK')
        elif d > 8 and self.t % 20 == 0 and random.random() < 0.4:
            self.damage(4.0, 'PROJECTILE')

    def damage(self, amount, cause):
        b = self.b
        if b.hp <= 0:
            return
        b.hp -= amount * b.maxhp / 2500.0  # que la pelea dure unos minutos sea cual sea la vida
        self.hits += 1
        # damage-amount en la escala de vida del pack (como BossEntity.hurt)
        scale = self.prog['boss']['health'] / max(1.0, float(self.prog['boss'].get('pack_health') or 1))
        self.trigger('ondamaged', {'damage-amount': amount * 2 / scale, 'damage-cause': cause})

    def dist(self):
        return math.hypot(self.px - self.b.x, self.pz - self.b.z)

    # ------------------------------------------------------------------ jefe
    def boss_step(self):
        b = self.b
        # objetivo: el jugador si está al alcance (siempre en esta prueba)
        b.target = b.ai and self.dist() < 64
        if b.target and not b.combat:
            b.combat = True
            self.trigger('onentercombat')
        moving = False
        if b.ai and b.target and b.chases and b.speed() > 0:
            d = self.dist()
            hb = self.prog['boss'].get('hitbox') or [1, 2]
            stop = hb[0] / 2 + 1.0  # como BossEntity
            if d > stop:
                step = min(b.speed() * 0.9, d - stop)
                b.x += (self.px - b.x) / d * step
                b.z += (self.pz - b.z) / d * step
                moving = True
        want = yaw_of(self.px - b.x, self.pz - b.z)
        if b.chases and b.ai:
            b.look = wrap(b.look + max(-30, min(30, wrap(want - b.look))))
            if not b.locked:
                if moving:
                    b.body = wrap(b.body + max(-20, min(20, wrap(want - b.body))))
                else:
                    b.body = wrap(b.body + wrap(want - b.body) * 0.05)
        else:
            b.look = b.body

    # ------------------------------------------------------------------ disparadores
    def trigger(self, tr, vars=None, value=None):
        for pm in self.b.mob['mechs']:
            t = pm.get('tr') or 'onspawn'
            if t != tr:
                continue
            if value is not None and str(pm.get('trv', '')).lower() != str(value).lower():
                continue
            if pm.get('hp') and not self.hp_ok(pm['hp']):
                continue
            self.exec(pm, {'targets': None, 'vars': dict(vars or {}), 'depth': 0})

    def hp_ok(self, spec):
        pct = self.b.hp / self.b.maxhp * 100
        return in_range(pct, spec.replace('%', ''))

    def timers(self):
        for pm in self.b.mob['mechs']:
            if pm.get('tr') != 'ontimer':
                continue
            every = int(num(pm.get('trv'), 20)) or 1
            if self.t % every == 0 and (not pm.get('hp') or self.hp_ok(pm['hp'])):
                self.exec(pm, {'targets': None, 'vars': {}, 'depth': 0})

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
        if m == 'playerwithin':
            return math.hypot(self.px - b.x, self.pz - b.z) <= num(arg(a, 5, 'distance', 'd'), 5)
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
        if m in ('ydiff', 'onground', 'altitude', 'blocking', 'mythicmobtype', 'boundingboxesoverlap'):
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
            b.cooldowns[meta_key(name)] = self.t + round(cd * 20)
        self.script(meta['mechs'], dict(ctx, targets=targets, depth=ctx['depth'] + 1), 0, name)
        return True

    def script(self, mechs, ctx, i, name):
        while i < len(mechs):
            pm = mechs[i]
            if pm['m'] == 'delay':
                ticks = int(num(arg(pm.get('a', {}), 0, '_', 'ticks', 'd'), 0))
                rest = i + 1
                self.later(ticks, lambda r=rest: self.script(mechs, ctx, r, name))
                return
            self.exec(pm, ctx)
            i += 1

    def later(self, ticks, fn):
        if ticks <= 0:
            fn()
        else:
            self.tasks.append((self.t + ticks, fn))

    def exec(self, pm, ctx):
        a = pm.get('a', {})
        for c in pm.get('c') or []:
            if self.cond(c, 'boss', ctx) == c.get('not', False):
                return
        if pm.get('ch') not in (None, 1, 1.0) and random.random() >= float(pm['ch']):
            return
        delay = int(num(a.get('delay'), 0))
        repeat = int(num(arg(a, 0, 'repeat'), 0))
        every = max(1, int(num(arg(a, 1, 'repeatinterval', 'repeati'), 1)))

        def once():
            self.apply(pm, ctx, self.targets(pm, ctx))

        def step(left):
            once()
            if left > 0:
                self.later(every, lambda: step(left - 1))

        self.later(delay, lambda: step(min(repeat, 200)))

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
            b.gcd = self.t + int(num(arg(a, 20, 'ticks', 't', 'duration', 'd', '_'), 20))
        elif m == 'setskillcooldown':
            if 'boss' in targets:
                b.cooldowns[meta_key(arg(a, '', 'skill', 's'))] = self.t + round(num(arg(a, 0, 'seconds', 'ticks'), 0) * 20)
        elif m in ('aura', 'buff'):
            n = str(arg(a, '', 'auraname', 'aura', 'n', 'buffname')).lower()
            if n and 'boss' in targets:
                b.auras[n] = self.t + int(num(arg(a, 200, 'duration', 'd', 'ticks'), 200))
            if a.get('ot') or a.get('ontick'):
                pass
        elif m in ('state', 'animation'):
            s = str(arg(a, '', 'state', 's', 'animation', 'a'))
            if str(arg(a, 'false', 'remove', 'r')).lower() == 'true' or 'boss' not in targets:
                return
            if s not in NOT_ATTACKS:
                # Los golpes lanzan lejos al jugador (throw de sus ataques): la distancia cambia como en una pelea
                d = self.dist()
                if d < 7:
                    k = random.uniform(3, 8) / max(d, 0.5)
                    self.px += (self.px - b.x) * k * 0.5
                    self.pz += (self.pz - b.z) * k * 0.5
                b.fired[s] = b.fired.get(s, 0) + 1
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


def meta_key(name):
    return str(name).lower()


OWN_MODEL = {'bodyrotation', 'bodyclamp', 'model', 'segment', 'partvis', 'partvisibility', 'brightness', 'renderinit',
             'defaultstate', 'state', 'animation', 'lockmodel', 'remapmodel', 'changepart', 'barset', 'barcreate',
             'setmodelscale', 'tint'}


def run(boss_id, minutes, seed, verbose):
    s = Sim(boss_id, seed, verbose)
    s.trigger('onspawn')
    end = int(minutes * 1200)
    while s.t < end and s.b.hp > 0:
        s.t += 1
        due = [f for at, f in s.tasks if at <= s.t]
        s.tasks = [(at, f) for at, f in s.tasks if at > s.t]
        for f in due:
            f()
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
    global OLD
    OLD = '--antes' in args
    bosses = [a for a in args if not a.startswith('-') and not re.match(r'^[\d.]+$', a)] or \
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
        if s.unknown:
            print('     condiciones sin simular:', ', '.join(sorted(s.unknown)))


if __name__ == '__main__':
    main()
