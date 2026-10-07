"""Paquetes de skills de VFX: lo que se ve sale de los YAML de MythicMobs de cada pack (vfx_compile.py) y la
jugabilidad (disparador pasivo, cooldown, daño y empujes) se define aquí, pensada para un survival.

Disparadores (todos pasivos, sin teclas ni comandos):
  golpe            al dar un golpe cargado cuerpo a cuerpo
  golpe_critico    golpe cargado cayendo (el crítico de Minecraft)
  golpe_corriendo  golpe cargado mientras corres
  golpe_agachado   golpe cargado agachado
  dano             al recibir daño de alguien (con «vida»: solo si te quedas por debajo de esa fracción de vida)
  combate          sola, cada vez que esté lista mientras peleas
Acciones en el servidor (cada una con su tick «t»):
  area {fwd, up, side, r, vr, limit}  a los enemigos en esa zona: dano, lanzar, empuje, atraer, pociones, aturdir,
                                      impacto (efecto que se ve en cada enemigo alcanzado)
  linea {len, r}                      igual, a lo largo de una línea hacia delante
  proyectil {v, ticks, r, grav, apuntar, atraviesa, fwd, up, alFinal {fx, area, zona}}
  zona {fwd, up, r, ticks, cada}      zona que dura (tornados): lo que entra recibe sus efectos cada «cada» ticks
  self {impulso: [delante, arriba], pociones}  sobre quien la lanza (sin daño de caída después del impulso)
  esquivar                            con disparador «dano»: el golpe no hace nada
"""
import glob
import os

import vfx_compile as C
import vfx_mm as M

C.M = M


def potion(pid, ticks, level=0):
    return {'id': 'minecraft:' + pid, 'ticks': ticks, 'nivel': level}


class PackBuilder:
    def __init__(self, b, problems, root_glob, skills_file, mobs_file, sound_prefix):
        self.b = b
        self.problems = problems
        base = sorted(glob.glob(os.path.join(b.src.root, root_glob)))
        if not base:
            raise SystemExit(f'No encuentro el pack {root_glob}')
        base = base[0]
        self.c = C.Compiler(b, problems, M.load(os.path.join(base, skills_file)), M.load(os.path.join(base, mobs_file)),
                            sound_prefix)

    def fx(self, fx_id, parts, who='caster'):
        """parts: lista de (skill de MythicMobs o lista de eventos, tick de inicio). who='origin' para los impactos:
        el efecto sale donde está el enemigo alcanzado."""
        events = []
        for part, t0 in parts:
            if isinstance(part, str):
                events += self.c.skill(part, t0, who=who)
            else:
                for e in part:
                    e = dict(e)
                    e['t'] = e.get('t', 0) + t0
                    events.append(e)
        if not events:
            self.problems.append(f'{fx_id}: sin eventos')
        self.b.fx[fx_id] = C.finish(events)
        return fx_id

    def mob(self, name, t0=0, fwd=0, up=0, side=0, lock=True, **extra):
        return self.c.mob(name, t0, fwd, up, side, lock, extra or None)


# ---------------------------------------------------------------------------------------------------------------
# Filo del Vendaval (samus2002 · Gale Glaive)

def gale(b, problems):
    p = PackBuilder(b, problems, '*GALE_GLAIVE*', 'MythicMobs (For Crucible)/Skills/gale_glaive_skills.yml',
                    'MythicMobs (For Crucible)/Mobs/gale_glaive_mobs.yml', 'gale')
    impact = p.fx('viento_impacto', [('Gale_Slash_DAMAGE', 0)], who='origin')
    hit = {'area': {'fwd': 0.3, 'r': 4, 'vr': 2, 'limit': 5}, 'impacto': impact}
    teal = {'part': 'dust_color_transition', 'color': '#cbfee9', 'color2': '#0eaf9b', 'size': 0.45, 'n': 1, 'hs': 0,
            'vs': 0, 'sp': 0}
    # Runa del Viento: tres órbitas de polvo alrededor del jugador (orbital con onTick de MythicMobs)
    runa_orbits = [dict(teal, t=0, orbit=0.8, oy=0.2, period=45, ticks=45, start=s, at='caster') for s in (0, 15, 30)]
    skills = [
        {
            'id': 'corte', 'name': 'Corte del Vendaval', 'trigger': 'golpe', 'cooldown': 1.2,
            'desc': 'Al golpear: tajo de arriba abajo, tajo de abajo arriba y estocada que atrae a los enemigos.',
            'stages': [
                {'fx': p.fx('viento_corte_1', [('Gale_Slash_Stack_1', 0)]), 'actions': [dict(hit, t=1, dano=3)]},
                {'fx': p.fx('viento_corte_2', [('Gale_Slash_Stack_2', 0)]), 'actions': [dict(hit, t=1, dano=3)]},
                {'fx': p.fx('viento_corte_3', [('Gale_Slash_Stack_3', 0)]),
                 'actions': [dict(hit, t=1, dano=4, atraer=0.6, area={'fwd': 1.7, 'r': 4, 'vr': 2, 'limit': 5})]},
            ],
        },
        {
            'id': 'danza', 'name': 'Danza de Cuchillas', 'trigger': 'golpe_corriendo', 'cooldown': 7,
            'desc': 'Golpeando mientras corres: carga el viento, te lanzas hacia delante y levantas a los enemigos.',
            'stages': [{'fx': p.fx('viento_danza', [('Dancing_Blade_SKILL', 0)]), 'actions': [
                {'t': 1, 'self': {'pociones': [potion('slowness', 14, 4)]}},
                {'t': 16, 'self': {'impulso': [1.5, 0.2]}},
                {'t': 19, 'area': {'fwd': 1, 'r': 4, 'vr': 2, 'limit': 3}, 'dano': 4, 'lanzar': 0.9,
                 'pociones': [potion('slow_falling', 40)], 'impacto': p.fx('viento_danza_impacto', [('Dancing_Blade_DAMAGE', 0)], who='origin')},
                {'t': 21, 'area': {'fwd': 1, 'r': 4, 'vr': 2, 'limit': 3}, 'dano': 2, 'lanzar': 0.6},
            ]}],
        },
        {
            'id': 'perforanubes', 'name': 'Perforanubes', 'trigger': 'golpe_agachado', 'cooldown': 10,
            'desc': 'Golpeando agachado: lanzas una lanza de viento que frena a quien toca; al caer levanta un tornado que atrae y daña.',
            'stages': [{'fx': p.fx('viento_lanza', [('Cloudpiercer', 0)]), 'actions': [
                {'t': 1, 'self': {'pociones': [potion('slowness', 15, 3)]}},
                {'t': 16, 'proyectil': {'v': 1.25, 'ticks': 40, 'r': 1.2, 'grav': 0.005, 'up': 2.2, 'fwd': 0.3,
                                        'apuntar': True,
                                        'alFinal': {'fx': p.fx('viento_tornado', [('Cloudpiercer_SUMMON_TORNADO', 0)], who='origin'),
                                                    'zona': {'r': 5, 'vr': 3, 'ticks': 140, 'cada': 20, 'limit': 8},
                                                    'dano': 2, 'atraer': 0.35,
                                                    'impacto': impact}},
                 'dano': 5, 'pociones': [potion('slowness', 60, 1)],
                 'impacto': p.fx('viento_lanza_impacto', [('Cloudpiercer_HIT', 0)], who='origin')},
            ]}],
        },
        {
            'id': 'torrente', 'name': 'Torrente', 'trigger': 'combate', 'cooldown': 14,
            'desc': 'En combate: haces girar la guja sobre tu cabeza atrayendo a los enemigos y descargas un gran tajo que los aparta.',
            'stages': [{'fx': p.fx('viento_torrente', [
                (p.mob('VFX_Torrent_Twirl', 0, up=1.5, follow='caster', life=22), 0),
                ('Torrent_Spin_Skill2', 0), ('Torrent_SLASH', 22)]), 'actions': [
                {'t': 1, 'area': {'r': 7, 'vr': 3, 'limit': 8}, 'atraer': 0.35, 'lanzar': 0.25},
                {'t': 8, 'area': {'r': 7, 'vr': 3, 'limit': 8}, 'atraer': 0.35, 'lanzar': 0.2},
                {'t': 23, 'area': {'fwd': 0.3, 'r': 6, 'vr': 2.5, 'limit': 8}, 'dano': 5, 'empuje': 1.4, 'lanzar': 0.3,
                 'impacto': p.fx('viento_torrente_impacto', [('Torrent_SLASH_DAMAGE', 0)], who='origin')},
            ]}],
        },
        {
            'id': 'runa', 'name': 'Runa del Viento', 'trigger': 'combate', 'cooldown': 12,
            'desc': 'En combate: el viento te rodea, saltas más y tus golpes levantan a los enemigos un instante.',
            'stages': [{'fx': p.fx('viento_runa', [(runa_orbits, 0)]), 'actions': [
                {'t': 1, 'self': {'pociones': [potion('jump_boost', 45, 0)], 'marca': {'id': 'runa', 'ticks': 45,
                                                                                 'pociones': [potion('levitation', 8, 1)]}}},
            ]}],
        },
        {
            'id': 'furia', 'name': 'Furia del Vendaval', 'trigger': 'dano', 'cooldown': 60, 'vida': 0.3,
            'desc': 'Con poca vida: un tornado te eleva casi intocable, atrae a todos los enemigos y caes sobre ellos con un tajo que agrieta el suelo.',
            'stages': [{'fx': p.fx('viento_furia', [('Windbound_Fury', 0)]), 'actions': [
                {'t': 1, 'self': {'pociones': [potion('levitation', 80, 1), potion('resistance', 100, 3)]}},
                *[{'t': 2 + 4 * k, 'area': {'r': 15, 'vr': 8, 'limit': 12}, 'atraer': 0.3, 'lanzar': 0.12} for k in range(19)],
                {'t': 80, 'area': {'r': 7, 'vr': 4, 'limit': 10}, 'dano': 4, 'lanzar': -0.9, 'impacto': impact},
                {'t': 85, 'self': {'impulso': [0, -2.5]}},
                {'t': 90, 'area': {'r': 10, 'vr': 4, 'limit': 12}, 'dano': 6, 'pociones': [potion('slowness', 60, 3)],
                 'impacto': p.fx('viento_furia_impacto', [('Windbound_Fury_HIT', 0)], who='origin')},
            ]}],
        },
        {
            'id': 'salto', 'name': 'Salto del Viento', 'trigger': 'dano', 'cooldown': 20, 'vida': 0.5,
            'desc': 'Si te quedas a media vida: saltas por los aires y caes despacio.',
            'stages': [{'fx': p.fx('viento_salto', [('Vault', 0)]), 'actions': [
                {'t': 1, 'self': {'impulso': [0.3, 1.1], 'pociones': [potion('slow_falling', 60, 0)]}},
            ]}],
        },
    ]
    return {'id': 'vendaval', 'name': 'Filo del Vendaval', 'color': '#2fd3b0', 'model': 'vfx_gale_tornado',
            'anim': 'spawn', 'thumbTime': 0.39, 'view': {'pitch': -22},
            'desc': 'Skills de viento: tajos en combo, una danza de cuchillas, una lanza que levanta tornados y una furia final.',
            'skills': skills}


# ---------------------------------------------------------------------------------------------------------------
# Ronin del Trueno (samus2002 · Heroes Thunder Ronin)

def ronin(b, problems):
    p = PackBuilder(b, problems, '*THUNDER_RONIN*', 'MythicMobs (For Crucible)/Skills/RPG_Class_Heroes_Thunder_Ronin_Skills.yml',
                    'MythicMobs (For Crucible)/Mobs/RPG_Class_Heroes_Thunder_Ronin_Mobs.yml', 'ronin')
    impact = p.fx('trueno_impacto', [('Thunder_Slash_DAMAGE', 0)], who='origin')
    crit = p.fx('trueno_impacto_critico', [('Thunder_Slash_DAMAGE2', 0)], who='origin')
    stun = p.fx('trueno_aturdido', [('Thunder_Slash_DAMAGE2_STUN', 0)], who='victim')
    slash = {'area': {'fwd': 0.6, 'r': 4, 'vr': 2, 'limit': 4}, 'impacto': impact}
    skills = [
        {
            'id': 'corte', 'name': 'Corte del Trueno', 'trigger': 'golpe', 'cooldown': 1.2,
            'desc': 'Al golpear: tajo a la izquierda, tajo a la derecha y un salto que cae como un rayo y puede paralizar.',
            'stages': [
                {'fx': p.fx('trueno_corte_1', [('Thunder_Slash_ST_1', 0)]), 'actions': [dict(slash, t=1, dano=3)]},
                {'fx': p.fx('trueno_corte_2', [('Thunder_Slash_ST_2', 0)]), 'actions': [dict(slash, t=1, dano=3)]},
                {'fx': p.fx('trueno_corte_3', [('Thunder_Slash_ST_3', 0)]), 'actions': [
                    {'t': 1, 'self': {'impulso': [0.5, 0.6]}},
                    {'t': 6, 'self': {'impulso': [0.35, -0.9]}},
                    {'t': 10, 'area': {'fwd': 0.6, 'r': 5, 'vr': 2.5, 'limit': 4}, 'dano': 5, 'impacto': crit,
                     'aturdir': 40, 'probAturdir': 0.2, 'impactoAturdir': stun},
                ]},
            ],
        },
        {
            'id': 'reflejo', 'name': 'Reflejo Relámpago', 'trigger': 'dano', 'cooldown': 3, 'chance': 0.22,
            'desc': 'Al recibir un golpe: 22 % de esquivarlo como un rayo y ganar velocidad un momento.',
            'stages': [{'fx': p.fx('trueno_reflejo', [('Lightning_Reflex_AVOID', 0)]), 'actions': [
                {'esquivar': True, 't': 1, 'self': {'pociones': [potion('speed', 40, 2)]}},
            ]}],
        },
        {
            'id': 'destello', 'name': 'Trueno y Destello', 'trigger': 'golpe_corriendo', 'cooldown': 6,
            'desc': 'Golpeando mientras corres: cargas un círculo de rayos y sales disparado cortando todo lo que hay delante.',
            'stages': [{'fx': p.fx('trueno_destello', [('Thunderclap_And_Flash_CAST', 0)]), 'actions': [
                {'t': 10, 'linea': {'len': 11, 'r': 1.6, 'limit': 6}, 'dano': 5, 'impacto': impact},
                {'t': 11, 'self': {'impulso': [2.2, 0.0]}},
            ]}],
        },
        {
            'id': 'contraataque', 'name': 'Contraataque Tormenta', 'trigger': 'dano', 'cooldown': 8,
            'desc': 'Al recibir un golpe: das un paso atrás y respondes con dos tajos que paralizan.',
            'stages': [{'fx': p.fx('trueno_contraataque', [('Flashstorm_Reversal_CAST', 0)]), 'actions': [
                {'t': 1, 'self': {'impulso': [-1.1, 0.1]}},
                {'t': 5, 'area': {'fwd': 0.6, 'r': 4, 'vr': 2, 'limit': 4}, 'dano': 3, 'aturdir': 40, 'impacto': impact,
                 'impactoAturdir': stun},
                {'t': 9, 'area': {'fwd': 0.6, 'r': 4, 'vr': 2, 'limit': 4}, 'dano': 3, 'impacto': impact},
            ]}],
        },
        {
            'id': 'caida', 'name': 'Caída Crepitante', 'trigger': 'golpe_critico', 'cooldown': 12,
            'desc': 'Con un golpe crítico: te elevas cargando energía y caes como un rayo, golpeando todo a tu alrededor.',
            'stages': [{'fx': p.fx('trueno_caida', [('Crackling_Skyfall_CAST', 0)]), 'actions': [
                {'t': 1, 'self': {'pociones': [potion('levitation', 30, 4)]}},
                {'t': 31, 'self': {'impulso': [0.6, -2.2]}},
                {'t': 34, 'area': {'r': 9, 'vr': 5, 'limit': 15}, 'dano': 5, 'empuje': 0.6, 'impacto': impact},
            ]}],
        },
        {
            'id': 'andanada', 'name': 'Andanada de Trueno', 'trigger': 'golpe_agachado', 'cooldown': 9,
            'desc': 'Golpeando agachado: una ráfaga de estocadas eléctricas hacia delante durante dos segundos.',
            'stages': [{'fx': p.fx('trueno_andanada', [('Thunderous_Barrage_CAST', 0)]), 'actions': [
                {'t': 8, 'self': {'pociones': [potion('slowness', 30, 2)]}},
                *[{'t': 8 + 4 * k, 'area': {'fwd': 2.5, 'r': 3, 'vr': 2, 'limit': 5}, 'dano': 1.2, 'impacto': impact}
                  for k in range(10)],
            ]}],
        },
        {
            'id': 'dios', 'name': 'Dios del Trueno', 'trigger': 'dano', 'cooldown': 90, 'vida': 0.4,
            'desc': 'Con poca vida: un dragón de rayos aparece a tu espalda y te da fuerza y velocidad durante diez segundos.',
            'stages': [{'fx': p.fx('trueno_dios', [('Flaming_Thunder_God_CAST', 0)]), 'actions': [
                {'t': 1, 'self': {'pociones': [potion('strength', 210, 0), potion('speed', 210, 0)]}},
            ]}],
        },
    ]
    return {'id': 'trueno', 'name': 'Ronin del Trueno', 'color': '#e8f06a', 'model': 'flaming_rhunder_god_1',
            'anim': 'loop', 'thumbTime': 1.1, 'desc': 'Skills de rayo: cortes en combo, esquivas relámpago, embestidas eléctricas y un dragón del trueno.',
            'skills': skills}


# ---------------------------------------------------------------------------------------------------------------
# Hechizos del Alma (NeiCore · Dynamic Player VFX): el jugador se convierte en un mago con su propia cara que lanza
# el hechizo. Mientras dura, el modelo (con la cabeza del jugador) sigue sus pasos y el jugador de verdad no se ve.

def cinematic(p, spawn_skill, t0=30):
    """Eventos de la cinemática: la entrada (dynamic_spawn con su arcoíris) y el modelo principal del hechizo, que
    sigue al jugador y lleva su cabeza. Devuelve (eventos, tick en que vuelve a verse el jugador)."""
    c = p.c
    events = c.mob('dynamicPlayerVfxAllSpawn', 0, 0, 0, 0, True, {'follow': 'caster', 'followYaw': True})
    entry = c.skills[spawn_skill]['Skills'][0]
    args = C.M.parse_line(entry)[1]
    lines = C.flow_lines(args.get('os'))
    t = 0
    model = None
    show_at = None
    for line in lines:
        m = C.re.match(r'^delay\s+(\d+)', line)
        if m:
            t += int(m.group(1))
            continue
        pr = C.M.parse_line(line)
        if not pr:
            continue
        mech, a, target, targs, trig = pr
        d = t + int(C.num(a.get('delay')))
        if mech == 'model':
            model = {'t': d, 'model': a.get('m').lower(), 'key': 'hechizo', 'follow': 'caster', 'followYaw': True,
                     'skin': True, 'life': 400}
            c.b.model(model['model'])
            events.append(model)
        elif mech == 'state' and model is not None and not model.get('anim'):
            model['anim'] = a.get('s')
        elif mech == 'partvis' and model is not None:
            model.setdefault('vis', []).append({'t': d - model['t'], 'bone': a.get('p'),
                                                'show': str(a.get('v', 'false')).lower() == 'true', 'children': True})
        elif mech == 'showentity':
            show_at = d
        elif mech == 'remove' and model is not None:
            model['life'] = d - model['t']
        elif mech in ('sound', 'effect:sound', 'particlering', 'effect:particlering') or mech.startswith('e:p') \
                or mech.startswith('effect:particle'):
            events += c.lines([line], d, label=spawn_skill)
    return events, show_at


def hechizos(b, problems):
    p = PackBuilder(b, problems, '*dynamicplayervfx*', 'dynamicplayervfx_0_1/MythicMobs/Packs/DynamicPlayerVfx/Skills/dynamicPlayerVfxSkills.yml',
                    'dynamicplayervfx_0_1/MythicMobs/Packs/DynamicPlayerVfx/Mobs/dynamicPlayerVfxMobs.yml', 'hechizos')

    def spell(fx_id, spawn_skill, extra=()):
        events, show = cinematic(p, spawn_skill)
        hide = {'t': 0, 'hide': 'caster', 'ticks': show or 60}
        p.b.fx[fx_id] = C.finish(events + [hide] + list(extra))
        return fx_id, show or 60

    def proj_model(model, t, vels, life, up=1.3, hide=('main',)):
        p.b.model(model)
        return {'t': t, 'model': model, 'anim': 'dynamic_spawn', 'key': model + '#proj', 'up': up, 'fwd': 0.3, 'life': life,
                'vel': 0.005, 'aim': False, 'vels': vels, 'vis': [{'t': 0, 'bone': h, 'show': False, 'children': True} for h in hide]}

    heal_fx, heal_t = spell('hechizo_curacion', 'dynamicPlayerVfxNCMagicHealthMobSpawn')
    water_fx, water_t = spell('hechizo_agua', 'dynamicPlayerVfxNCWaterShootPlayerSkinMobSpawn',
                              [proj_model('watershootnc', 30, [{'t': 55, 'vel': 1.25}], 95)])
    star_fx, star_t = spell('hechizo_estrella', 'dynamicPlayerVfxNCBounceStarPlayerSkinMobSpawn',
                            [proj_model('bouncestarnc', 30, [{'t': 52, 'vel': 1.25}, {'t': 102, 'vel': 1.0}, {'t': 152, 'vel': 0.005}], 170)])
    void_fx, void_t = spell('hechizo_vacio', 'dynamicPlayerVfxNCVoidLaserMobSpawn',
                            [{'t': 95, 'part': 'explosion_emitter', 'n': 1, 'hs': 0, 'vs': 0, 'sp': 0, 'fwd': 4, 'y': 1, 'at': 'caster'}])
    sun_fx, sun_t = spell('hechizo_sol', 'dynamicPlayerVfxNCSunSwordMobSpawn')
    explosion = p.fx('hechizo_explosion', [([
        {'t': 0, 'sound': 'minecraft:entity.generic.explode', 'vol': 2, 'pitch': 1.5},
        {'t': 0, 'part': 'explosion_emitter', 'n': 1, 'hs': 0, 'vs': 0, 'sp': 0},
        {'t': 0, 'part': 'end_rod', 'n': 1, 'hs': 1, 'vs': 1, 'sp': 0.3, 'ring': 1, 'points': 8},
    ], 0)])
    splash = p.fx('hechizo_agua_impacto', [([
        {'t': 0, 'sound': 'minecraft:entity.player.splash', 'vol': 2, 'pitch': 2},
        {'t': 0, 'part': 'splash', 'n': 20, 'hs': 0.5, 'vs': 0.5, 'sp': 0.1, 'y': 1},
    ], 0)])
    hit = p.fx('hechizo_impacto', [([
        {'t': 0, 'part': 'flash', 'n': 1, 'hs': 0.1, 'vs': 0.1, 'sp': 0.1, 'y': 1},
        {'t': 0, 'part': 'electric_spark', 'n': 8, 'hs': 0.4, 'vs': 0.6, 'sp': 0.2, 'y': 1},
    ], 0)])

    def guard(ticks, level=2):
        return {'t': 1, 'self': {'pociones': [potion('resistance', ticks, level), potion('speed', ticks, 1)]}}

    skills = [
        {
            'id': 'curacion', 'name': 'Curación Mágica', 'trigger': 'dano', 'cooldown': 40, 'vida': 0.35,
            'desc': 'Con poca vida: te conviertes en mago y te curas durante unos segundos, protegido.',
            'stages': [{'fx': heal_fx, 'actions': [guard(heal_t, 3)] + [
                {'t': 50 + 5 * k, 'self': {'curar': 1}} for k in range(11)]}],
        },
        {
            'id': 'agua', 'name': 'Disparo de Agua', 'trigger': 'golpe_corriendo', 'cooldown': 50,
            'desc': 'Golpeando mientras corres: invocas una esfera de agua que sale disparada y atraviesa a los enemigos.',
            'stages': [{'fx': water_fx, 'actions': [guard(water_t),
                {'t': 85, 'proyectil': {'v': 1.25, 'ticks': 40, 'r': 2.2, 'up': 1.3, 'fwd': 0.3, 'atraviesa': True},
                 'dano': 6, 'impacto': splash}]}],
        },
        {
            'id': 'estrella', 'name': 'Estrella Rebotante', 'trigger': 'combate', 'cooldown': 80,
            'desc': 'En combate: una estrella de energía avanza arrollando enemigos y explota al final.',
            'stages': [{'fx': star_fx, 'actions': [guard(star_t),
                {'t': 82, 'proyectil': {'v': 1.25, 'ticks': 88, 'r': 1.5, 'up': 1.3, 'fwd': 0.3, 'atraviesa': True,
                                        'vels': [{'t': 50, 'v': 1.0}, {'t': 100, 'v': 0.005}],
                                        'alFinal': {'fx': explosion, 'area': {'r': 6, 'vr': 3, 'limit': 10}, 'dano': 6}},
                 'dano': 5, 'impacto': hit}]}],
        },
        {
            'id': 'vacio', 'name': 'Láser del Vacío', 'trigger': 'golpe_agachado', 'cooldown': 80,
            'desc': 'Golpeando agachado: abres el vacío y disparas un láser que lo atraviesa todo y estalla delante de ti.',
            'stages': [{'fx': void_fx, 'actions': [guard(void_t)] + [
                {'t': 85 + 3 * k, 'linea': {'len': 15, 'r': 1.5, 'limit': 8}, 'dano': 1.5, 'impacto': hit} for k in range(5)] + [
                {'t': 95, 'area': {'fwd': 5, 'r': 5, 'vr': 3, 'limit': 10}, 'dano': 5, 'impacto': explosion}]}],
        },
        {
            'id': 'sol', 'name': 'Espada Solar', 'trigger': 'golpe_critico', 'cooldown': 60,
            'desc': 'Con un golpe crítico: una espada de sol barre todo lo que tienes delante, lanzándolo y prendiéndole fuego.',
            'stages': [{'fx': sun_fx, 'actions': [guard(sun_t)] + [
                {'t': 60 + 4 * k, 'area': {'fwd': 3, 'r': 3.5, 'vr': 2.5, 'limit': 8}, 'dano': 1.5, 'lanzar': 0.5,
                 'empuje': 0.8, 'fuego': 3, 'impacto': hit} for k in range(8)]}],
        },
    ]
    return {'id': 'hechizos', 'name': 'Hechizos del Alma', 'color': '#b77dff', 'model': 'magichealthnc',
            'anim': 'dynamic_spawn', 'thumbTime': 2.8, 'desc': 'Hechizos con cinemática: te conviertes en mago con tu propia cara para curarte o lanzar agua, estrellas, láseres y una espada de sol.',
            'skills': skills}


def build(b, problems):
    packs = [gale(b, problems), ronin(b, problems), hechizos(b, problems)]
    return packs
