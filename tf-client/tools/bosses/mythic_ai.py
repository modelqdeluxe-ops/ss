"""La IA de MythicMobs de los mobs de los jefes (AIGoalSelectors y AITargetSelectors), leída de SkillAI.java para que
el simulador y el auditor usen las mismas tablas que el motor: los alias, los que existen en MythicMobs y lo que trae
cada tipo de Minecraft sin lista. AI_OK es lo que BossEntity ya hace (o, si no hace nada, por qué no importa).
"""
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
JAVA = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'java', 'net', 'tierrasfantasticas', 'tfclient',
                                     'skills', 'SkillAI.java'))

# Lo que hace BossEntity con cada objetivo de IA. Los de «None» los copia de verdad; los demás no hacen nada y el texto
# dice por qué no cambia la pelea. Lo que no esté aquí (bowattack, los «type:x» del ghast, el vex...) es un problema
# en el pack que lo use: se copia entonces, con la semántica de Minecraft/MythicMobs.
AI_OK = {
    'meleeattack': None, 'zombieattack': None, 'spiderattack': None, 'movetowardstarget': None, 'lookattarget': None,
    'lookatplayers': None, 'randomstroll': None, 'panic': None, 'float': None, 'leapattarget': None,
    'gotospawnlocation': None, 'restrictsun': None, 'fleesun': None, 'clear': None,
    'randomlookaround': 'solo gira la cabeza a ningún sitio en concreto (no cambia a quién ataca ni a dónde va)',
    'randomnod': 'solo cabecea',
    'donothing': 'no hace nada (sin condiciones, que son de MythicMobs Premium)',
    'breed': 'cría con otro de su tipo: en una pelea no hay pareja ni comida',
    'eatgrass': 'comer hierba cambiaría el mundo (la hierba pasa a tierra): los jefes no lo cambian',
    'opendoor': 'las grietas no tienen puertas',
    'breakdoors': 'romper puertas cambiaría el mundo: los jefes no lo cambian',
    'movethroughvillage': 'las grietas no están en aldeas',
    'movetowardsrestriction': 'los mobs de los jefes no tienen zona de restricción (la de su grieta es la correa)',
}
TARGETS_OK = {'clear', 'hurtbytarget', 'players', 'monsters', 'type:spider'}

# Tipos de Minecraft que son seres vivos (tienen IA): en el juego, un mob de verdad del jefe (BossEntity); los demás
# (soportes, marcadores, displays) son efectos que dibuja el cliente
LIVING = {'IRON_GOLEM', 'HUSK', 'ZOMBIE', 'VINDICATOR', 'SKELETON', 'WITHER_SKELETON', 'SPIDER', 'RAVAGER', 'PIGLIN_BRUTE',
          'WOLF', 'POLAR_BEAR', 'GIANT', 'ZOMBIE_VILLAGER', 'PILLAGER', 'EVOKER', 'BLAZE', 'SLIME', 'MAGMA_CUBE',
          # (1.3.82) el resto de seres vivos de Minecraft que traen los packs nuevos
          'STRAY', 'DROWNED', 'ZOMBIFIED_PIGLIN', 'PIG_ZOMBIE', 'PIGLIN', 'ILLUSIONER', 'WITCH', 'VEX', 'GHAST', 'PHANTOM',
          'WARDEN', 'CREEPER', 'ENDERMAN', 'ENDERMITE', 'SILVERFISH', 'CAVE_SPIDER', 'HOGLIN', 'ZOGLIN', 'STRIDER', 'SHULKER',
          'GUARDIAN', 'ELDER_GUARDIAN', 'WITHER', 'ENDER_DRAGON', 'SNOWMAN', 'SNOW_GOLEM', 'BAT', 'ALLAY', 'CHICKEN', 'PIG',
          'COW', 'SHEEP', 'GOAT', 'HORSE', 'SKELETON_HORSE', 'ZOMBIE_HORSE', 'DONKEY', 'MULE', 'LLAMA', 'CAMEL', 'FOX',
          'CAT', 'OCELOT', 'PANDA', 'RABBIT', 'PARROT', 'BEE', 'FROG', 'AXOLOTL', 'TURTLE', 'DOLPHIN', 'SQUID', 'GLOW_SQUID',
          'VILLAGER', 'WANDERING_TRADER', 'SNIFFER', 'MUSHROOM_COW', 'MOOSHROOM'}


def _src():
    return open(JAVA, encoding='utf-8').read()


def _block(src, start, end='\n    }\n'):
    i = src.index(start)
    return src[i:src.index(end, i)]


def _entries(block):
    return dict(re.findall(r'Map\.entry\("([^"]+)",\s*"([^"]+)"\)', block))


def _set(src, name):
    m = re.search(r'Set<String> ' + name + r' = Set\.of\((.*?)\);', src, re.S)
    return set(re.findall(r'"([^"]+)"', m.group(1)))


def _switch(src, method):
    """{TIPO: [objetivos]} del switch de ese método de SkillAI (con «default» para el resto)."""
    body = _block(src, 'public static List<String> ' + method)
    out = {}
    for labels, items in re.findall(r'(case\s+(?:"[^"]+"\s*,?\s*)+|default)\s*->\s*List\.of\((.*?)\);', body, re.S):
        vals = re.findall(r'"([^"]+)"', items)
        if labels == 'default':
            out['default'] = vals
        else:
            for t in re.findall(r'"([^"]+)"', labels):
                out[t] = vals
    return out


_SRC = _src()
GOAL_ALIAS = _entries(_block(_SRC, 'GOAL_ALIAS = Map.ofEntries(', ');'))
TARGET_ALIAS = _entries(_block(_SRC, 'TARGET_ALIAS = Map.ofEntries(', ');'))
GOALS = _set(_SRC, 'GOALS')
TARGETS = _set(_SRC, 'TARGETS')
_VG = _switch(_SRC, 'vanillaGoals')
_VT = _switch(_SRC, 'vanillaTargets')


def _bare(raw):
    s = str(raw or '').strip().lower()
    if '{' in s:
        s = s[:s.index('{')].strip()
    p = s.split()
    i = 1 if len(p) > 1 and re.fullmatch(r'-?\d+', p[0]) else 0
    return p[i] if i < len(p) else ''


def goal_name(raw):
    n = _bare(raw)
    return GOAL_ALIAS.get(n, n)


def target_name(raw):
    n = _bare(raw)
    return TARGET_ALIAS.get(n, n)


def vanilla_goals(mtype):
    return list(_VG.get(str(mtype or '').upper(), _VG['default']))


def vanilla_targets(mtype):
    return list(_VT.get(str(mtype or '').upper(), _VT.get('default', [])))


def apply(base, entries, targets=False):
    """SkillAI.apply: «clear» quita lo de antes; lo que MythicMobs no conoce se ignora."""
    out = list(base)
    if entries is None:
        return out
    for e in entries:
        if e is None or not str(e).strip():
            continue
        n = target_name(e) if targets else goal_name(e)
        if n == 'clear':
            out = []
            continue
        if n not in (TARGETS if targets else GOALS):
            continue
        out.append(str(e).strip().lower())
    return out


def unknown(entries, targets=False):
    """Los de la lista del pack que MythicMobs no conoce (los ignora)."""
    return [e for e in entries or [] if str(e).strip()
            and (target_name(e) if targets else goal_name(e)) not in ((TARGETS if targets else GOALS) | {'clear'})]


def goals_of(mob):
    return apply(vanilla_goals(mob.get('type')), mob.get('ai_goals'))


def targets_of(mob):
    return apply(vanilla_targets(mob.get('type')), mob.get('ai_targets'), True)


def has(goals, *names):
    return any(goal_name(g) in names for g in goals)


def chases(goals):
    return has(goals, 'meleeattack', 'zombieattack', 'spiderattack', 'movetowardstarget', 'movewithindistanceoftarget')


def melee(goals):
    return has(goals, 'meleeattack', 'zombieattack', 'spiderattack')
