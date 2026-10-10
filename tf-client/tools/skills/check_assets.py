"""Revisa que todo lo que usan las clases de skills del mod existe y vale en Minecraft 1.20.1:

  - sonidos: los del mod (tfclient:skills.*) están en sounds.json y sus .ogg en el jar; los de Minecraft existen;
  - modelos de ítem de los efectos (lo que llevan en la cabeza o en la mano) y sus texturas (también las de sus padres);
  - modelos de ModelEngine (skills/models/<clase>.<modelo>.json) y sus texturas;
  - iconos de las skills, las fuentes de letras-imagen (tfclient:skills_<clase>) y sus texturas;
  - partículas: que Minecraft 1.20.1 las tenga (con los nombres de Bukkit pasados a los de Minecraft, como SkillClient).

    python3 tools/skills/check_assets.py          (sale con 1 si hay algún fallo)
"""
import glob
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)
sys.path.insert(0, TOOLS)
import vfx_compile as VC  # noqa: E402

RES = os.path.normpath(os.path.join(TOOLS, '..', 'src', 'main', 'resources', 'assets'))
TF = os.path.join(RES, 'tfclient')

# Las partículas de Minecraft 1.20.1 (las de assets/minecraft/particles del juego y las que no tienen dibujo propio)
PARTICLES_1201 = set('''shriek falling_lava dust_color_transition firework soul_fire_flame happy_villager nautilus cloud
egg_crack composter falling_honey dripping_dripstone_water glow lava soul large_smoke dust sneeze falling_dripstone_lava
ash poof bubble_pop sweep_attack warped_spore falling_dripstone_water cherry_leaves mycelium sculk_charge glow_squid_ink
electric_spark damage_indicator dolphin smoke dripping_honey falling_obsidian_tear underwater effect wax_on
spore_blossom_air campfire_cosy_smoke flash campfire_signal_smoke landing_lava witch fishing sculk_soul
dripping_dripstone_lava dripping_obsidian_tear dripping_water reverse_portal note landing_obsidian_tear splash
sculk_charge_pop explosion falling_water portal entity_effect snowflake dragon_breath spit landing_honey squid_ink heart
falling_nectar falling_dust angry_villager scrape dripping_lava white_ash small_flame crit falling_spore_blossom
enchanted_hit crimson_spore current_down rain ambient_entity_effect flame wax_off enchant bubble sonic_boom
bubble_column_up instant_effect totem_of_undying vibration end_rod block item block_marker explosion_emitter
elder_guardian item_slime item_snowball'''.split())

# Los nombres de Bukkit/MythicMobs → Minecraft (los mismos que SkillClient.PARTICLE_NAMES)
PARTICLE_NAMES = {
    'reddust': 'dust', 'redstone': 'dust', 'smoke_normal': 'smoke', 'smoke_large': 'large_smoke',
    'explosion_large': 'explosion', 'explosion_huge': 'explosion_emitter', 'explosion_normal': 'poof',
    'spell_witch': 'witch', 'spell_instant': 'instant_effect', 'spell': 'effect', 'spell_mob': 'entity_effect',
    'spell_mob_ambient': 'ambient_entity_effect', 'enchantment_table': 'enchant', 'water_drop': 'rain',
    'totem': 'totem_of_undying', 'fireworks_spark': 'firework', 'villager_happy': 'happy_villager',
    'villager_angry': 'angry_villager', 'drip_water': 'dripping_water', 'drip_lava': 'dripping_lava',
    'water_splash': 'splash', 'water_wake': 'fishing', 'suspended': 'underwater', 'suspended_depth': 'underwater',
    'crit_magic': 'enchanted_hit', 'magic_crit': 'enchanted_hit', 'mob_appearance': 'elder_guardian',
    'slime': 'item_slime', 'snowball': 'item_snowball', 'snow_shovel': 'item_snowball', 'block_crack': 'block',
    'blockcrack': 'block', 'block_dust': 'block', 'item_crack': 'item', 'iconcrack': 'item', 'town_aura': 'mycelium',
    'footstep': 'poof', 'damage_indicator': 'damage_indicator', 'sweep': 'sweep_attack',
    'bubble_column_up': 'bubble_column_up'}

PARTICLE_MECHS = {'effect:particles', 'particles', 'particle', 'e:p', 'effect:p', 'effect:particle', 'effect:particlering',
                  'particlering', 'e:pr', 'effect:pr', 'effect:particlesphere', 'particlesphere', 'e:ps', 'effect:ps',
                  'effect:particleorbital', 'particleorbital', 'e:po', 'effect:po', 'effect:particleline', 'particleline',
                  'e:pl', 'effect:pl', 'effect:particlebox', 'particlebox', 'effect:pb'}


def asset(ref, kind, ext):
    """«ns:ruta» → archivo en assets (kind: models, textures, sounds)."""
    ns, _, path = str(ref).rpartition(':')
    return os.path.join(RES, ns or 'minecraft', kind, path + ext)


def check_item_model(ref, problems, seen, depth=0):
    """Un modelo de ítem del mod: que exista y tenga sus texturas (las de los padres del mod también)."""
    if ref in seen or depth > 8:
        return
    seen.add(ref)
    ns = str(ref).rpartition(':')[0] or 'minecraft'
    if ns == 'minecraft':
        return  # de Minecraft: está en el juego
    path = asset(ref, 'models', '.json')
    if not os.path.exists(path):
        problems.append(f'modelo de ítem que no está: {ref}')
        return
    data = json.load(open(path, encoding='utf-8'))
    for tex in (data.get('textures') or {}).values():
        if isinstance(tex, str) and not tex.startswith('#'):
            tns = tex.rpartition(':')[0] or 'minecraft'
            if tns != 'minecraft' and not os.path.exists(asset(tex, 'textures', '.png')):
                problems.append(f'textura que no está: {tex} (en {ref})')
    par = data.get('parent')
    if par and not str(par).startswith('builtin/'):
        check_item_model(par if ':' in par else 'minecraft:' + par, problems, seen, depth + 1)


def main():
    problems = []
    sounds_json = json.load(open(os.path.join(TF, 'sounds.json'), encoding='utf-8'))
    valid_vanilla = VC.valid_sounds()
    seen_models, seen_me = set(), set()
    n = {'clases': 0, 'sonidos': set(), 'modelos ME': set(), 'modelos de ítem': set(), 'iconos': 0, 'partículas': set()}
    for f in sorted(glob.glob(os.path.join(TF, 'skills', 'classes', '*.json'))):
        d = json.load(open(f, encoding='utf-8'))
        cid = d['id']
        n['clases'] += 1
        where = lambda k: f'{cid}/{k}'  # noqa: E731
        groups = [(k, v['mechs']) for k, v in d['tree'].items()] + [('mob:' + k, v.get('mechs', [])) for k, v in d['mobs'].items()]
        for k, mechs in groups:
            for m in mechs:
                a = m.get('a', {})
                name = m['m']
                if name in ('effect:sound', 'sound', 'e:s', 'effect:s'):
                    s = a.get('s') or a.get('sound')
                    if not s or '<' in str(s):
                        continue
                    n['sonidos'].add(s)
                    if s.startswith('tfclient:'):
                        ev = sounds_json.get(s[len('tfclient:'):])
                        if ev is None:
                            problems.append(f'{where(k)}: el sonido {s} no está en sounds.json')
                            continue
                        for snd in ev.get('sounds', []):
                            sn = snd['name'] if isinstance(snd, dict) else snd
                            if not os.path.exists(asset(sn, 'sounds', '.ogg')):
                                problems.append(f'{where(k)}: falta el archivo de {sn}')
                    elif s.startswith('minecraft:'):
                        if s[len('minecraft:'):] not in valid_vanilla:
                            problems.append(f'{where(k)}: Minecraft 1.20.1 no tiene el sonido {s}')
                    else:
                        problems.append(f'{where(k)}: sonido sin convertir {s}')
                elif name == 'equip' and a.get('model'):
                    n['modelos de ítem'].add(a['model'])
                    check_item_model(a['model'], problems, seen_models)
                elif name in ('model', 'state', 'changepart') and (a.get('mid') or a.get('newmodelid') or a.get('nmid')):
                    for key in ('mid', 'newmodelid', 'nmid'):
                        mid = a.get(key)
                        if not mid or '<' in str(mid):
                            continue
                        mid = mid if '.' in mid else f'{cid}.{str(mid).lower()}'
                        n['modelos ME'].add(mid)
                        if mid in seen_me:
                            continue
                        seen_me.add(mid)
                        path = os.path.join(TF, 'skills', 'models', mid + '.json')
                        if not os.path.exists(path):
                            problems.append(f'{where(k)}: no está el modelo de ModelEngine {mid}')
                            continue
                        model = json.load(open(path, encoding='utf-8'))
                        for t in model.get('tex', []):
                            if not os.path.exists(asset(t['path'].replace('textures/', '').removesuffix('.png'), 'textures', '.png')):
                                problems.append(f'{mid}: no está la textura {t["path"]}')
                elif name in PARTICLE_MECHS:
                    p = str(a.get('particle') or a.get('p') or a.get('part') or 'reddust').lower().replace('minecraft:', '')
                    if '<' in p:
                        continue
                    p = PARTICLE_NAMES.get(p, p)
                    n['partículas'].add(p)
                    if p not in PARTICLES_1201:
                        problems.append(f'{where(k)}: Minecraft 1.20.1 no tiene la partícula {p}')
        for mob, md in d['mobs'].items():
            if md.get('head_model'):
                n['modelos de ítem'].add(md['head_model'])
                check_item_model(md['head_model'], problems, seen_models)
        for sid, icon in (d.get('icons') or {}).items():
            n['iconos'] += 1
            if not os.path.exists(asset(icon.removesuffix('.png').replace('textures/', ''), 'textures', '.png')):
                problems.append(f'{cid}: falta el icono de {sid}')
        for s in d['skills']:
            if not s.get('hidden') and s['id'] not in (d.get('icons') or {}):
                problems.append(f'{cid}: la skill {s["id"]} no tiene icono')
        if d.get('glyphs'):
            fp = os.path.join(TF, 'font', f'skills_{cid}.json')
            if not os.path.exists(fp):
                problems.append(f'{cid}: falta la fuente skills_{cid}')
            else:
                have = set()
                for prov in json.load(open(fp, encoding='utf-8'))['providers']:
                    have |= set(''.join(prov['chars']))
                    if not os.path.exists(asset(prov['file'].removesuffix('.png'), 'textures', '.png')):
                        problems.append(f'{cid}: falta la imagen de la fuente {prov["file"]}')
                    if prov.get('ascent', 0) > prov.get('height', 8):
                        problems.append(f'{cid}: en la fuente, ascent > height ({prov["file"]})')
                missing = set(d['glyphs']) - have
                if missing:
                    problems.append(f'{cid}: la fuente no tiene {len(missing)} letras')
    for p in problems:
        print('-', p)
    print(f"{n['clases']} clases: {len(n['sonidos'])} sonidos, {len(n['modelos ME'])} modelos de ModelEngine, "
          f"{len(n['modelos de ítem'])} modelos de ítem, {n['iconos']} iconos, {len(n['partículas'])} partículas: "
          f"{len(problems)} fallos")
    sys.exit(1 if problems else 0)


if __name__ == '__main__':
    main()
