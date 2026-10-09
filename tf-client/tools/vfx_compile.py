"""Compila el aspecto de las skills de MythicMobs (lo que se ve y se oye) a la línea de tiempo de fx.json.

Recorre una skill de MythicMobs con sus «delay», sus sub-skills y sus «summon» de mobs de VFX (ModelEngine) y saca:
  - modelos: el modelo del mob, su animación (state) y velocidad, cambios de animación, cambios de parte
    (changepart, también los que van dentro de un aura que itera fotogramas), partes ocultas (partvis), cuándo se
    quita, partículas en sus huesos (@modelpart) y temporizadores (~onTimer)
  - sonidos (los de 1.21 que no existen en 1.20.1 se cambian por otros parecidos; los propios del pack se copian)
  - partículas, anillos (particlering) y esferas (particlesphere)
Lo que hace daño, empuja o mueve al jugador no se compila: eso lo hace el servidor con las acciones de cada skill
(ver vfx_skills.py), que se escriben a mano para que la jugabilidad sea la de un survival.
"""
import os
import re

import vfx_mm as M

HERE = os.path.dirname(os.path.abspath(__file__))
_SOUNDS = None

# Sonidos de 1.21 (no existen en 1.20.1) → el más parecido de 1.20.1
SOUND_SUBST = {
    'entity.breeze.wind_burst': 'item.trident.riptide_1',
    'entity.wind_charge.wind_burst': 'item.trident.riptide_1',
    'entity.breeze.slide': 'entity.phantom.swoop',
    'entity.breeze.shoot': 'item.trident.throw',
    'entity.breeze.death': 'entity.vex.hurt',
    'entity.breeze.idle_air': 'entity.phantom.flap',
    'entity.breeze.idle_ground': 'entity.phantom.flap',
    'entity.breeze.inhale': 'entity.phantom.swoop',
    'entity.breeze.charge': 'entity.evoker.prepare_attack',
    'block.trial_spawner.open_shutter': 'block.respawn_anchor.charge',
    'block.trial_spawner.about_to_spawn_item': 'block.beacon.power_select',
    'block.trial_spawner.spawn_mob': 'block.respawn_anchor.set_spawn',
    'block.trial_spawner.ominous_activate': 'block.beacon.activate',
    'block.vault.open_shutter': 'block.iron_trapdoor.open',
    'item.mace.smash_ground_heavy': 'entity.generic.explode',
    'event.mob_effect.bad_omen': 'block.beacon.deactivate',
    'event.mob_effect.raid_omen': 'block.beacon.deactivate',
    'item.mace.smash_air': 'entity.player.attack.strong',
    'block.trial_spawner.place': 'block.respawn_anchor.set_spawn',
    'item.mace.smash_ground': 'entity.generic.explode',
    'block.trial_spawner.spawn_item': 'block.amethyst_block.chime',
    'block.trial_spawner.spawn_item_begin': 'block.beacon.power_select',
    'block.trial_spawner.ambient_ominous': 'block.respawn_anchor.ambient',
    'entity.breeze.jump': 'entity.goat.long_jump',
    'entity.breeze.land': 'entity.goat.step',
    # erratas de los packs
    'item.bucket.fil': 'item.bucket.fill',
    'item.book.equip_leather': 'item.armor.equip_leather',
    'item.shiled.break': 'item.shield.break',
}


def valid_sounds():
    global _SOUNDS
    if _SOUNDS is None:
        with open(os.path.join(HERE, 'sounds_1201.txt')) as f:
            _SOUNDS = {l.strip() for l in f if l.strip()}
    return _SOUNDS


def num(v, default=0.0):
    if v is None:
        return default
    s = str(v).strip()
    m = re.match(r'<random\.float\.([\d.\-]+)to([\d.\-]+)>', s)
    if m:
        return (float(m.group(1)) + float(m.group(2))) / 2
    try:
        return float(s)
    except ValueError:
        return default


def rand_range(v):
    m = re.match(r'<random\.float\.([\d.\-]+)to([\d.\-]+)>', str(v or '').strip())
    return [float(m.group(1)), float(m.group(2))] if m else None


def offsets(target, targs):
    """@forward{f;y;sideOffset} / @selflocation{y} / @self → (delante, arriba, lado, bloquea pitch)."""
    if target in ('forward',):
        return (num(targs.get('f', targs.get('forward'))), num(targs.get('y')), num(targs.get('sideoffset', targs.get('so'))),
                str(targs.get('lockpitch', 'false')).lower() == 'true')
    if target in ('selflocation', 'casterlocation', 'origin', 'self', ''):
        return 0.0, num(targs.get('y')), 0.0, True
    return 0.0, 0.0, 0.0, True


class Compiler:
    def __init__(self, builder, problems, skills_yaml, mobs_yaml, sound_prefix):
        self.b = builder
        self.problems = problems
        self.skills = skills_yaml
        self.mobs = mobs_yaml
        self.sound_prefix = sound_prefix
        self.keyn = 0

    # ------------------------------------------------------------------------- sonidos y partículas

    def sound(self, sid, label):
        if not sid:
            return None
        if ':' in sid and not sid.startswith('minecraft:'):
            # Sonido propio del pack: se copia al mod
            out = self.sound_prefix + '.' + sid.split(':', 1)[1].split('.')[-1]
            return self.b.sound(sid, out)
        sid = sid.replace('minecraft:', '')
        sid = SOUND_SUBST.get(sid, sid)
        if sid not in valid_sounds():
            self.problems.append(f'{label}: sonido {sid} no existe en 1.20.1')
            return None
        return 'minecraft:' + sid

    def sound_event(self, t, args, label, extra=None):
        sid = self.sound(args.get('s') or args.get('sound'), label)
        if not sid:
            return []
        ev = {'t': t, 'sound': sid, 'vol': round(num(args.get('volume', args.get('v')), 1.0), 3)}
        pr = rand_range(args.get('pitch', args.get('p')))
        ev['pitch'] = pr if pr else round(num(args.get('pitch', args.get('p')), 1.0), 3)
        if extra:
            ev.update(extra)
        out = [ev]
        rep, every = int(num(args.get('repeat'))), max(1, int(num(args.get('repeati', args.get('repeatinterval')), 1)))
        for k in range(1, rep + 1):
            e2 = dict(ev)
            e2['t'] = t + k * every
            out.append(e2)
        return out

    def particle(self, t, mech, args, target, targs, label, extra=None):
        import build_vfx as BV
        raw = (args.get('particle') or args.get('p') or 'reddust').lower()
        ptype = BV.PARTICLES.get(raw, raw)
        if ptype not in BV.VANILLA_PARTICLES:
            self.problems.append(f'{label}: partícula {raw}')
            return []
        f, y, s, _ = offsets(target, targs)
        ev = {'t': t, 'part': ptype, 'n': int(num(args.get('amount', args.get('a')), 10)),
              'hs': num(args.get('hs')), 'vs': num(args.get('vs')), 'sp': num(args.get('speed', args.get('s'))),
              'y': num(args.get('y')) + y, 'fwd': f, 'side': s}
        if 'ring' in mech:
            ev['ring'] = num(args.get('radius', args.get('r')), 1)
            ev['points'] = int(num(args.get('points'), 8))
        if 'sphere' in mech:
            ev['sphere'] = num(args.get('radius', args.get('r')), 1)
        rep = int(num(args.get('repeat')))
        if rep:
            ev['rep'] = rep
            ev['every'] = max(1, int(num(args.get('repeati', args.get('repeatinterval')), 1)))
        if ptype == 'dust':
            ev['color'] = args.get('color', args.get('c', '#ff0000'))
            ev['size'] = num(args.get('size'), 1) or 1
        elif ptype == 'dust_color_transition':
            ev['color'] = args.get('color1', '#ffffff')
            ev['color2'] = args.get('color2', '#ffffff')
            ev['size'] = num(args.get('size'), 1) or 1
        elif ptype == 'block':
            ev['block'] = 'minecraft:' + (args.get('material') or args.get('m') or 'stone').lower()
        if target == 'modelpart':
            ev['bone'] = targs.get('pid') or targs.get('p')
        if extra:
            ev.update(extra)
        return [ev]

    # ------------------------------------------------------------------------- mobs de VFX

    def mob(self, name, t0, fwd, up, side, lock, extra=None):
        """Eventos de un mob de VFX invocado en t0 (desplazado delante/arriba/lado del lanzador)."""
        mob = self.mobs.get(name)
        if not isinstance(mob, dict):
            self.problems.append(f'falta el mob de VFX {name}')
            return []
        self.keyn += 1
        key = f'{name}#{self.keyn}'
        model_ev = {'t': t0, 'key': key, 'fwd': fwd, 'up': up, 'side': side, 'life': None}
        events, swaps, vis, states, timers = [], [], [], [], []
        model_lock = True
        raw_lines = []
        for raw in mob.get('Skills') or []:
            pr = M.parse_line(raw)
            sub = None
            if pr and '~onspawn' in str(raw).lower():
                mm = re.match(r'^\s*-?\s*skill:([\w.\-]+)', str(raw))
                if mm:
                    sub = mm.group(1)
                elif pr[0] == 'skill' and not str(pr[1].get('s', '')).startswith('['):
                    sub = pr[1].get('s')
            if sub and isinstance(self.skills.get(sub), dict):
                raw_lines += [str(l) + ' ~onSpawn' for l in self.skills[sub].get('Skills') or []]
            else:
                raw_lines.append(raw)
        tints = []
        t_acc = 0
        for raw in raw_lines:
            dm = re.match(r'^-?\s*delay\s+(\d+)', str(raw).strip())
            if dm:
                t_acc += int(dm.group(1))
                continue
            p = M.parse_line(raw)
            if not p:
                continue
            mech, args, target, targs, trig = p
            d = t_acc + int(num(args.get('delay')))
            timer = re.search(r'~onTimer:(\d+)', str(raw))
            if mech == 'model':
                model_ev['model'] = (args.get('mid') or args.get('m') or '').lower()
                model_lock = str(args.get('lockpitch', 'false')).lower() == 'true'
            elif mech == 'state':
                st = {'t': d, 'anim': args.get('s') or args.get('state'), 'speed': num(args.get('speed'), 1)}
                if 'anim' not in model_ev and d == 0:
                    model_ev['anim'] = st['anim']
                    model_ev['speed'] = st['speed']
                else:
                    states.append(st)
            elif mech == 'changepart':
                swaps.append({'t': d, 'bone': args.get('p'), 'model': (args.get('nm') or '').lower()})
            elif mech == 'partvis':
                vis.append({'t': d, 'bone': args.get('p'), 'show': str(args.get('v', 'false')).lower() == 'true',
                            'children': str(args.get('child', 'true')).lower() == 'true'})
            elif mech == 'tint' and args.get('c'):
                tints.append({'t': d, 'c': args['c']})
            elif mech in ('remove', 'suicide'):
                model_ev['life'] = d if model_ev['life'] is None else min(model_ev['life'], d)
            elif mech == 'aura' and 'ot' in args:
                # Aura que itera fotogramas: cada tick cambia las partes al modelo _2, _3...
                delay, dur, every = d, int(num(args.get('d', args.get('duration')), 0)), max(1, int(num(args.get('i'), 1)))
                for cp in re.finditer(r'changepart\{([^}]*)\}', args['ot']):
                    a2 = M.parse_args(cp.group(1))
                    base = (a2.get('nm') or '').lower()
                    for k in range(1, dur // every + 1):
                        it = k + 1
                        nm = base.replace('<caster.var.iteration>', str(it))
                        if nm in self.b.src.bb:
                            swaps.append({'t': delay + k * every, 'bone': a2.get('p'), 'model': nm})
                if 'remove' in str(args.get('oe', '')):
                    end = delay + dur + 1
                    model_ev['life'] = end if model_ev['life'] is None else min(model_ev['life'], end)
            elif mech.startswith('effect:particle') or mech in ('particles', 'particle', 'particlering', 'particlesphere'):
                pe = self.particle(0 if timer else t0 + d, mech, args, target, targs, name, {'inst': key})
                for e in pe:
                    if timer:
                        e.pop('t', None)
                        e.pop('inst', None)
                        timers.append({'every': int(timer.group(1)), 'ev': e})
                    else:
                        events.append(e)
            elif mech in ('effect:sound', 'sound'):
                se = self.sound_event(0 if timer else t0 + d, args, name)
                for e in se:
                    if timer:
                        e.pop('t', None)
                        e['at'] = 'model'
                        timers.append({'every': int(timer.group(1)), 'ev': e})
                    else:
                        e.update({'fwd': fwd, 'up': up, 'side': side})
                        events.append(e)
        if not model_ev.get('model'):
            return events
        # Las primeras fases de los cambios de parte pueden venir desordenadas
        model_ev['swap'] = sorted(swaps, key=lambda s: s['t'])
        if vis:
            model_ev['vis'] = vis
        if states:
            model_ev['states'] = sorted(states, key=lambda s: s['t'])
        if timers:
            model_ev['timers'] = timers
        if tints:
            model_ev['tints'] = sorted(tints, key=lambda x: x['t'])
        if not (lock or model_lock):
            model_ev['pitch'] = True
        m = self.b.model(model_ev['model'])
        for s in model_ev['swap']:
            self.b.model(s['model'])
        if m is None:
            return events
        if 'anim' not in model_ev:
            model_ev['anim'] = next(iter(m['anims']), None)
        if model_ev['life'] is None:
            a = m['anims'].get(model_ev['anim']) if model_ev['anim'] else None
            model_ev['life'] = int(round((a['len'] if a else 1) * 20 / max(0.05, model_ev.get('speed', 1)))) + 2
        if extra:
            model_ev.update(extra)
        return [model_ev] + events

    # ------------------------------------------------------------------------- skills

    def skill(self, name, t0=0, depth=0, base=(0.0, 0.0, 0.0), who='caster'):
        """Eventos del aspecto de una skill (y sus sub-skills) a partir de t0. who: dónde se ancla lo que la skill
        hace en @self (quien lanza, o la víctima si es una skill que corre sobre cada enemigo alcanzado)."""
        if depth > 12:
            return []
        entry = self.skills.get(name)
        if not isinstance(entry, dict):
            self.problems.append(f'falta la skill {name}')
            return []
        return self.lines(entry.get('Skills') or [], t0, depth, base, who, name)

    def lines(self, raw_lines, t0=0, depth=0, base=(0.0, 0.0, 0.0), who='caster', label='?'):
        events = []
        t = t0
        for raw in raw_lines:
            line = str(raw).strip()
            m = re.match(r'^-?\s*delay\s+(\d+)', line)
            if m:
                t += int(m.group(1))
                continue
            p = M.parse_line(line)
            if not p:
                continue
            mech, args, target, targs, trig = p
            d = t + int(num(args.get('delay')))
            # Lo que va a entidades cercanas (daño, impactos en cada enemigo) lo hace el servidor
            if target in ('entitiesnearorigin', 'eno', 'eir', 'entitiesinradius', 'pir', 'playersinradius', 'trigger',
                          'target', 'livingentitiesinradius', 'leir', 'entitiesinring', 'entitiesinringnear'):
                continue
            if mech in ('skill', 'metaskill') or mech.startswith('skill:'):
                sub = args.get('s') or args.get('skill') or (mech.split(':', 1)[1] if ':' in mech else None)
                if sub and not sub.startswith('['):
                    f, y, s2, _ = offsets(target, targs)
                    events += self.skill(sub, d, depth + 1, (base[0] + f, base[1] + y, base[2] + s2), who)
            elif mech == 'summon':
                f, y, s2, lock = offsets(target, targs)
                events += self.mob(args.get('type') or args.get('mob') or args.get('t'), d, base[0] + f, base[1] + y,
                                   base[2] + s2, lock, {'at': who} if who != 'caster' else None)
            elif mech == 'orbital' and (args.get('mob') or args.get('m')):
                dur = int(num(args.get('duration', args.get('d')), 20))
                events += self.mob(args.get('mob') or args.get('m'), d, base[0], base[1] + num(args.get('oy')), base[2],
                                   True, {'follow': who, 'life': dur})
            elif mech == 'orbital' and (args.get('ontick') or args.get('ot')):
                # Órbita de partículas (onTick con efectos): un punto que gira alrededor
                sub = self.skills.get(args.get('ontick') or args.get('ot'))
                dur = int(num(args.get('duration', args.get('d')), 20))
                for raw2 in (sub or {}).get('Skills') or []:
                    p2 = M.parse_line(raw2)
                    if not p2 or not (p2[0].startswith('effect:particle') or p2[0] in ('particles', 'particle')):
                        continue
                    for e in self.particle(d, p2[0], p2[1], '', {}, label, {'at': who}):
                        e.update({'orbit': num(args.get('r', args.get('radius')), 1), 'oy': num(args.get('oy')),
                                  'period': int(num(args.get('points'), 20)), 'ticks': dur})
                        events.append(e)
            elif mech in ('projectile', 'missile') and (args.get('mob') or str(args.get('bullettype', '')).upper() == 'MOB'):
                mob = args.get('mob') or args.get('m')
                if not mob:
                    continue
                if str(args.get('targetisorigin', 'false')).lower() == 'true':
                    # Proyectil quieto en el objetivo: es el efecto de impacto sobre el enemigo
                    events += self.mob(mob, d, base[0], base[1] + num(args.get('syo')), base[2], True,
                                       {'at': who} if who != 'caster' else None)
                    continue
                v = num(args.get('v', args.get('velocity')), 5) / 20.0
                mr = num(args.get('mr', args.get('maxrange')), 40)
                life = int(mr / v) if v > 0 else 40
                f, y, s2, lock = offsets(target, targs)
                extra = {'vel': round(v, 4), 'life': min(life, 200), 'aim': True}
                if num(args.get('g')):
                    extra['grav'] = round(num(args.get('g')) / 20, 5)
                events += self.mob(mob, d, base[0] + num(args.get('sfo')), base[1] + num(args.get('syo')), base[2], lock, extra)
            elif mech in ('effect:sound', 'sound'):
                f, y, s2, _ = offsets(target, targs)
                events += self.sound_event(d, args, label, {'at': who})
            elif mech.startswith('effect:particle') or mech in ('particles', 'particle', 'particlering', 'particlesphere'):
                f, y, s2, _ = offsets(target, targs)
                if target == 'modelpart':
                    continue
                events += self.particle(d, mech, args, 'forward', {'f': base[0] + f, 'y': base[1] + y, 'sideoffset': base[2] + s2},
                                        label, {'at': who})
        return events


def flow_lines(text):
    """«[ - mecánica{...} @x - delay 5 - ... ]» → lista de líneas (respeta llaves y corchetes anidados)."""
    text = str(text).strip()
    if text.startswith('['):
        text = text[1:]
    if text.endswith(']'):
        text = text[:-1]
    out, depth, cur = [], 0, ''
    i = 0
    while i < len(text):
        ch = text[i]
        if ch in '[{':
            depth += 1
        elif ch in ']}':
            depth -= 1
        if depth == 0 and text[i:i + 3] == ' - ':
            if cur.strip():
                out.append(cur.strip())
            cur = ''
            i += 3
            continue
        cur += ch
        i += 1
    if cur.strip():
        out.append(cur.strip().lstrip('- ').strip())
    return [l.lstrip('- ').strip() for l in out if l.strip()]


def finish(events):
    """Ordena por tiempo y calcula la duración del efecto."""
    events = sorted(events, key=lambda e: e['t'])
    dur = 0
    for e in events:
        end = e['t'] + (e.get('life') or 0) + e.get('rep', 0) * e.get('every', 1)
        dur = max(dur, end)
    return {'dur': dur + 2, 'ev': events}
