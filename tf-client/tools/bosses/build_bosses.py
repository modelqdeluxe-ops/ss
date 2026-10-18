"""Genera los jefes (bosses) del TF Client a partir de los packs comprados (MythicMobs + ModelEngine).

    python3 tools/bosses/build_bosses.py <carpeta con los packs descomprimidos> [pack ...]

Sin «pack»: todos (borra y rehace la salida de todos los jefes; hacen falta TODOS los packs en la carpeta). Con
«pack ...»: solo esos (borra y rehace solo lo suyo y los pone en el catálogo, en el orden de JEFES): así se añade un
jefe nuevo sin tener los packs de los demás (no están en el repo).

Cada pack da uno o varios jefes (ver JEFES abajo: carpeta del pack → id del jefe y su mob de MythicMobs). Los zips y
rar que traiga dentro (sonidos, resource packs, modelos) se descomprimen al lado la primera vez. Los .yml de
MythicMobs se reconocen por su carpeta (skills/, mobs/, items/) o, si no, por lo que llevan dentro. Sale:
  - assets/tfclient/bosses/<jefe>.json      el programa del jefe: el árbol de skills de MythicMobs que alcanza su mob
                                            (y lo que invoca), sus mobs con sus opciones, textos y valores base
  - assets/tfclient/bosses/catalog.json     el índice (orden de la ventana /tf boss)
  - assets/tfclient/bosses/models/<jefe>.<modelo>.json   los .bbmodel de ModelEngine (vfx_bb.py, modo jefe: prioridad
                                            de las animaciones, scripts «mm:» de la línea de tiempo, cajas de golpe ob_,
                                            asientos p_ y el tamaño del hueso «hitbox»)
  - textures/bosses/<jefe>/...              texturas de esos modelos, de los modelos de ítem y de los glifos
  - models/bosses/<jefe>/...                modelos de ítem (balas «display» de los misiles, objetos que sueltan)
  - sounds/bosses/<jefe>/*.ogg              sonidos del pack, eventos tfclient:bosses.<jefe>.<x> en sounds.json
  - font/bosses_<jefe>.json                 los glifos de sus text_display (el lanzallamas de la Hidra...)
Los packs no se suben al repo: solo lo convertido (como con las skills y los VFX). Las texturas de ítem entran en el
atlas de bloques con el de las skills (build_skills.write_atlas).
"""
import json
import os
import re
import shutil
import sys

import yaml

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)
sys.path.insert(0, os.path.join(TOOLS, 'skills'))
sys.path.insert(0, TOOLS)
import build_skills as BS  # noqa: E402
import compile as C  # noqa: E402
import vfx_bb as B  # noqa: E402
import vfx_compile as VC  # noqa: E402

sys.path.insert(0, HERE)
import mythic_ai as AI  # noqa: E402

RES = BS.RES
OUT = os.path.join(RES, 'bosses')
OUT_MODELS = os.path.join(OUT, 'models')
OUT_TEX = os.path.join(RES, 'textures', 'bosses')
OUT_ITEM_MODELS = os.path.join(RES, 'models', 'bosses')
OUT_SOUNDS = os.path.join(RES, 'sounds', 'bosses')
# Modelos de los objetos que sueltan: models/item/jefe_<jefe>_<objeto>.json (el id de TFBosses.itemId)
OUT_ITEM_JSON = os.path.join(RES, 'models', 'item')
SOUNDS_JSON = BS.SOUNDS_JSON

# Carpeta del pack → jefe. «mob»: el mob de MythicMobs que es el jefe.
# «vida» y «dano»: vida y daño base del jefe en dificultad Normal para un jugador (los de los packs son de
# ejemplo: la Abominación traía 50 de vida y todos «Damage: 2»); la ventana /tf boss los multiplica por la dificultad.
JEFES = {
    'LITTLEROOM_GARGOYLE_v1_0': {'id': 'gargola', 'mob': 'LRD_GARGOYLE', 'vida': 420, 'dano': 7},
    'LITTLEROOM_HYDRA_v1_0': {'id': 'hidra', 'mob': 'LR_HYDRA', 'vida': 1500, 'dano': 9},
    'LITTLEROOM_PARASITIC_ABOMINATION_v1_0': {'id': 'abominacion', 'mob': 'PARASITIC_ABOMINATION', 'vida': 650, 'dano': 8},
    'LITTLEROOM_TowerSkeleton_v1_0': {'id': 'esqueleto_torre', 'mob': 'TOWER_SKELETON', 'vida': 1200, 'dano': 9},
    'Astwood': {'id': 'astwood', 'mob': 'astwood', 'vida': 800, 'dano': 6},
    # (1.3.82) Los de LittleRoom de la segunda tanda
    'LITTLEROOM_pumpkinking_v1_0': {'id': 'rey_calabaza', 'mob': 'LR_PUMPKINKING', 'vida': 500, 'dano': 7},
    'LITTLEROOM_The_Archivist': {'id': 'archivista', 'mob': 'the_archivist', 'vida': 600, 'dano': 7},
    'LITTLEROOM_ELDRIC_v1_0': {'id': 'eldric', 'mob': 'LRD_eldric', 'vida': 650, 'dano': 8},
    'LITTLEROOM_borealdruid_v1_0': {'id': 'druida_boreal', 'mob': 'boreal_druid', 'vida': 400, 'dano': 7},
    'LITTLEROOM_beholder_v1_2': {'id': 'contemplador', 'mob': 'LRD_beholder', 'vida': 600, 'dano': 7},
    'LITTLEROOM_ASTRAL_ARCHON': {'id': 'arconte_astral', 'mob': 'astral_archon', 'vida': 800, 'dano': 8},
    'LITTLEROOM_kazk_v1_0': {'id': 'kazk', 'mob': 'LRD_kazk', 'vida': 800, 'dano': 8},
    'LITTLEROOM_hollowkeeper_v1_0': {'id': 'guardian_hueco', 'mob': 'LRD_HOLLOWKEEPER', 'vida': 1000, 'dano': 8},
    'littleroom_ignevar': {'id': 'ignevar', 'mob': 'ignevar', 'vida': 1000, 'dano': 9},
    'LITTLEROOM_PIGLINMECH_v1_0': {'id': 'piglin_mecha', 'mob': 'PIGLIN_MECH', 'vida': 1100, 'dano': 9},
    'LITTLEROOM_MEGAWARDEN_v1_0-': {'id': 'mega_guardian', 'mob': 'mega_warden', 'vida': 1200, 'dano': 9},
    # Pohaku (LITTLEROOM_pohaku_1.0) espera al dueño: su pelea necesita un escenario (schematic) con balistas y
    # mercenarios puestos a mano; sin ellos no se le aturde y no recibe daño
    'Littleroom_WitherStorm_1.0': {'id': 'tormenta_wither', 'mob': 'LRD_WITHERSTORM', 'vida': 1800, 'dano': 9},
}
ES = json.load(open(os.path.join(HERE, 'es.json'), encoding='utf-8'))

# Skills que el pack llama sin definirlas (fallos del pack): en MythicMobs esas líneas no hacen nada, pero así el
# ataque se queda sin daño. Se completan con las mismas mecánicas que usa el pack en sus ataques parecidos.
FIXES = {
    # El pisotón de la Hidra llama a «LR_HYDRA_stomp_damage» y el pack no la trae: como su coletazo, más corto
    'LR_HYDRA': """
LR_HYDRA_stomp_damage:
  Skills:
  - basedamage{m=1}
  - throw{v=6;vy=5}
""",
}

# Objetos con CustomModelData cuyo override de Minecraft no trae el pack (pero sí el modelo): material, Model → modelo.
# El Arconte Astral lanza «ink_sac» con Model 107 y su resource pack trae astral_archon_slash.json sin el ink_sac.json
# que lo engancha (en MythicMobs saldría una bolsa de tinta: es un olvido del pack)
ITEM_FIXES = {
    'LITTLEROOM_ASTRAL_ARCHON': {('ink_sac', 107): 'littleroom_astralarchon:astral_archon_slash'},
}

# Huecos de los packs revisados a mano: lo que el pack nombra y no trae (skills, animaciones, huesos, modelos). En
# MythicMobs/ModelEngine esas líneas no hacen nada, y aquí tampoco (fiel); el auditor los deja como aviso con su porqué
# en vez de como problema. Si el dueño quiere que alguno haga algo, se completa en FIXES.
HUECOS = {
    'archivista': {
        'THEARCHIVIST_start_fight': 'el pack la llama al aparecer y no la trae: en MythicMobs no hace nada',
        'THEARCHIVIST_stun_75': 'el aturdimiento del Archivista no viene en el pack (ni sus skills ni su animación)',
        'THEARCHIVIST_stun_50': 'el aturdimiento del Archivista no viene en el pack (ni sus skills ni su animación)',
        'THEARCHIVIST_stun_timer': 'el aturdimiento del Archivista no viene en el pack (ni sus skills ni su animación)',
        'THEARCHIVIST_stun_damagelimit': 'el aturdimiento del Archivista no viene en el pack (ni sus skills ni su animación)',
        'THEARCHIVIST_stun_end': 'el aturdimiento del Archivista no viene en el pack (ni sus skills ni su animación)',
        'dodge_right': 'el modelo no trae la animación del esquive: en ModelEngine no se ve, pero se aparta igual',
        'dodge_left_attack': 'el modelo no trae la animación del esquive: en ModelEngine no se ve, pero se aparta igual',
    },
    'druida_boreal': {
        'borealdruid_clearsnow': 'quitaría la nieve del suelo tras el golpe y no viene en el pack (además cambiaría el mundo)',
    },
    'ignevar': {
        'IGNEVAR_recoil_hit': 'temblor de cámara al caer un meteorito que no viene en el pack (los demás temblores sí)',
        'meteor': 'partículas en un hueso «meteor» que el suelo roto no tiene (copiado del meteorito): no salen',
    },
    'mega_guardian': {
        'MEGAWARDEN_stun_hit': 'no viene en el pack; el aturdimiento por fases (stun_75/50/25) sí',
        'MEGAWARDEN_leftswipe_totem': 'la llaman los scripts de las animaciones y no viene en el pack; el zarpazo hace '
                                      'su daño con MEGAWARDEN_melee_damage',
        'MEGAWARDEN_rightswipe_totem': 'la llaman los scripts de las animaciones y no viene en el pack; el zarpazo hace '
                                       'su daño con MEGAWARDEN_melee_damage',
        'mega_guardian.concussion': 'el modelo de las estrellas del aturdimiento no viene en el pack: no se ven',
    },
    'pohaku': {
        'pohaku_movement_04': 'uno de sus movimientos al azar no viene en el pack: esa vez se queda quieto',
        'ob_headhitbox': 'el modelo trae «headhitbox» sin ob_: ModelEngine no engancha ahí la caja de la cabeza',
    },
    'tormenta_wither': {
        'turn_left': 'el modelo no trae las animaciones de giro: en ModelEngine gira sin ellas',
        'turn_right': 'el modelo no trae las animaciones de giro: en ModelEngine gira sin ellas',
    },
}

# Tipos de MythicMobs que en el juego son un mob de verdad (se les pega, tienen IA); los demás (soportes, marcadores,
# displays) son efectos que dibuja el cliente. Uno de estos con «Invincible: true» también es solo un efecto (las
# púas y rocas de Astwood: vindicadores invencibles sin IA). La lista está en mythic_ai (la usa también el auditor).
LIVING = AI.LIVING
# Velocidad por defecto de cada tipo en Minecraft (setspeed{s=X} de MythicMobs = X por esta; atributo movement_speed)
SPEED = {'IRON_GOLEM': 0.25, 'HUSK': 0.23, 'ZOMBIE': 0.23, 'VINDICATOR': 0.35, 'SKELETON': 0.25,
         'WITHER_SKELETON': 0.25, 'SPIDER': 0.3, 'RAVAGER': 0.3, 'PIGLIN_BRUTE': 0.35, 'WOLF': 0.3,
         'POLAR_BEAR': 0.25, 'GIANT': 0.5, 'ZOMBIE_VILLAGER': 0.23, 'PILLAGER': 0.35, 'EVOKER': 0.5, 'BLAZE': 0.23,
         'STRAY': 0.25, 'DROWNED': 0.23, 'ZOMBIFIED_PIGLIN': 0.23, 'PIG_ZOMBIE': 0.23, 'PIGLIN': 0.35, 'ILLUSIONER': 0.5,
         'WITCH': 0.25, 'VEX': 0.6, 'GHAST': 0.7, 'PHANTOM': 0.7, 'WARDEN': 0.3, 'CREEPER': 0.25, 'ENDERMAN': 0.3,
         'ENDERMITE': 0.25, 'SILVERFISH': 0.25, 'CAVE_SPIDER': 0.3, 'HOGLIN': 0.3, 'ZOGLIN': 0.3, 'STRIDER': 0.175,
         'SHULKER': 0.0, 'GUARDIAN': 0.5, 'ELDER_GUARDIAN': 0.3, 'WITHER': 0.6, 'SNOWMAN': 0.2, 'SNOW_GOLEM': 0.2,
         'BAT': 0.25, 'ALLAY': 0.1, 'CHICKEN': 0.25, 'PIG': 0.25, 'COW': 0.2, 'SHEEP': 0.23, 'GOAT': 0.2, 'HORSE': 0.225,
         'SKELETON_HORSE': 0.2, 'ZOMBIE_HORSE': 0.2, 'DONKEY': 0.175, 'MULE': 0.175, 'LLAMA': 0.175, 'CAMEL': 0.09,
         'FOX': 0.3, 'CAT': 0.3, 'OCELOT': 0.3, 'PANDA': 0.15, 'RABBIT': 0.3, 'PARROT': 0.2, 'BEE': 0.3, 'FROG': 1.0,
         'AXOLOTL': 1.0, 'TURTLE': 0.25, 'DOLPHIN': 1.2, 'SQUID': 0.7, 'GLOW_SQUID': 0.7, 'VILLAGER': 0.5,
         'WANDERING_TRADER': 0.5, 'SNIFFER': 0.1, 'MUSHROOM_COW': 0.2, 'MOOSHROOM': 0.2}


# Tipos de bala que son un objeto (projectile/missile{bulletType}: ver SkillMovers.ITEM_BULLETS)
ITEM_BULLETS = {'ITEM', 'MYTHICITEM', 'TRACKING', 'ARMOR_STAND', 'ARMORSTAND', 'PSTAND', 'REALTRACKING', 'RTRACKING',
                'REAL_ARMOR_STAND', 'REALARMORSTAND', 'STAND', 'BLOCK', 'SMALLBLOCK', 'SMALL_BLOCK', 'ARROW'}


def unpack_nested(pack_dir):
    """Los zips y rar que trae el pack dentro (sonidos, resource packs, modelos): se descomprimen al lado
    («<archivo>__dentro/») la primera vez, y los que traigan dentro esos, también (hasta 3 niveles)."""
    import subprocess
    import zipfile
    for _level in range(3):
        found = False
        for d, _, fs in os.walk(pack_dir):
            if '.deobfCachedPacks' in d or '.assetCache' in d:
                continue
            for f in fs:
                if not f.lower().endswith(('.zip', '.rar')):
                    continue
                src = os.path.join(d, f)
                dest = src + '__dentro'
                if os.path.isdir(dest):
                    continue
                os.makedirs(dest)
                found = True
                try:
                    if zipfile.is_zipfile(src):
                        with zipfile.ZipFile(src) as z:
                            for info in z.infolist():
                                # nada fuera de su carpeta (rutas con «..» o absolutas)
                                target = os.path.normpath(os.path.join(dest, info.filename))
                                if not target.startswith(os.path.normpath(dest) + os.sep):
                                    continue
                                z.extract(info, dest)
                    else:
                        subprocess.run(['bsdtar', '-xf', src, '-C', dest], check=False, capture_output=True)
                except Exception as e:  # noqa: BLE001
                    print(f'   no se pudo abrir {os.path.relpath(src, pack_dir)}: {e}')
        if not found:
            break


# Claves de una entrada de MythicMobs según lo que es (para los .yml que no están en una carpeta skills/mobs/items)
_MOB_KEYS = {'Type', 'Health', 'Display', 'BossBar', 'AIGoalSelectors', 'AITargetSelectors', 'Faction', 'Mount',
             'Equipment', 'Drops', 'DamageModifiers', 'Disguise', 'Modules', 'Options', 'Damage', 'Armor'}


def kind_of(entry):
    """«mob», «item», «skill» o None, por lo que lleva dentro una entrada de un .yml de MythicMobs."""
    if not isinstance(entry, dict):
        return None
    keys = set(entry)
    if 'Type' in keys or 'Health' in keys or (keys & _MOB_KEYS and 'Skills' in keys and not keys & {'Id', 'Material'}):
        return 'mob'
    if keys & {'Id', 'Material'}:
        return 'item'
    if keys & {'Skills', 'Conditions', 'TargetConditions', 'TriggerConditions', 'Cooldown'}:
        return 'skill'
    return None


def files(pack_dir):
    """Los .yml de MythicMobs del pack: (skills, mobs, ítems). Por su carpeta (…/MythicMobs/…/skills|mobs|items/) y, los
    que no están en una de esas (los «hv_tenebris(Skills).yml» sueltos), por lo que llevan dentro."""
    sk, mb, it = [], [], []
    for d, _, fs in sorted(os.walk(pack_dir)):
        low = d.replace('\\', '/').lower()
        if re.search(r'/(assets|\.deobfcachedpacks|\.assetcache|modelengine|itemsadder|nexo|oraxen|__macosx)(/|$)', low):
            continue
        for f in sorted(fs):
            if not f.lower().endswith(('.yml', '.yaml')) or f.lower() in ('packinfo.yml', 'config.yml', 'plugin.yml'):
                continue
            if f.startswith('._'):  # basura de macOS
                continue
            p = os.path.join(d, f)
            if 'mythicmobs' in low and re.search(r'/skills?(/|$)', low):
                sk.append(p)
            elif 'mythicmobs' in low and re.search(r'/mobs?(/|$)', low):
                mb.append(p)
            elif 'mythicmobs' in low and re.search(r'/items?(/|$)', low):
                it.append(p)
            else:
                try:
                    data = C.load(p)
                except Exception:  # noqa: BLE001
                    continue
                if '__error__' in data:
                    continue
                kinds = {kind_of(v) for v in data.values()} - {None}
                for k in sorted(kinds):
                    {'skill': sk, 'mob': mb, 'item': it}[k].append(p)
                if kinds:
                    LOOSE.add(p)
    return sk, mb, it


# Los .yml sueltos (sin carpeta skills/mobs/items): de cada uno solo se coge lo que es de cada clase
LOOSE = set()


def load_all(paths, kind=None):
    out = {}
    for p in paths:
        data = C.load(p)
        if '__error__' in data:
            raise SystemExit(f'{p}: {data["__error__"]}')
        for k, v in data.items():
            if isinstance(v, dict) and (p not in LOOSE or kind is None or kind_of(v) == kind):
                out.setdefault(k, v)
    return out


_GLOBAL = {}


def global_index(root):
    """Las skills y los mobs de TODOS los packs de la carpeta: en MythicMobs los nombres son de todo el servidor, así
    que un pack que llama a una skill de otro (Ignevar usa «THEARCHIVIST_tele_to_floor» del Archivista) la encuentra
    si los dos están instalados, como lo estarán aquí."""
    if root not in _GLOBAL:
        sk, mb = {}, {}
        for name in sorted(os.listdir(root)):
            d = os.path.join(root, name)
            if not os.path.isdir(d):
                continue
            s_files, m_files, _i = files(d)
            for k, v in load_all(s_files, 'skill').items():
                sk.setdefault(k.lower(), (name, k, v))
            for k, v in load_all(m_files, 'mob').items():
                mb.setdefault(k.lower(), (name, k, v))
        _GLOBAL[root] = (sk, mb)
    return _GLOBAL[root]


def compile_boss(pack_dir, root_mob, extra_skills, notes=None):
    """El árbol de MythicMobs que alcanza el jefe: sus mecánicas, las skills que llaman, los mobs que invoca (con las
    suyas) y los ítems que suelta. extra_skills: las que lanzan los scripts de los modelos y los objetos."""
    sk_files, mob_files, item_files = files(pack_dir)
    tree, mobs, items = load_all(sk_files, 'skill'), load_all(mob_files, 'mob'), load_all(item_files, 'item')
    g_sk, g_mb = global_index(os.path.dirname(os.path.abspath(pack_dir)))
    me = os.path.basename(os.path.abspath(pack_dir))
    for k, v in (yaml.safe_load(FIXES.get(root_mob, '')) or {}).items():
        tree.setdefault(k, v)
    lower_tree = {k.lower(): k for k in tree}
    lower_mobs = {k.lower(): k for k in mobs}
    missing = set()
    reach_s, reach_m = set(), set()
    # (en orden: un conjunto sin ordenar cambia el orden del JSON en cada pasada)
    stack, mob_stack = sorted(extra_skills), [root_mob]
    compiled_tree, compiled_mobs = {}, {}
    used_items = set()

    def follow(mechs):
        for pm in mechs:
            s2, m2 = C.refs(pm)
            # «summon{os=true}» es «onSurface», no la skill «onStart» de un proyectil
            stack.extend(x for x in s2 if x.lower() not in ('true', 'false'))
            mob_stack.extend(m2)
            if pm['m'] == 'dropitem':
                used_items.add(str(pm['a'].get('i') or pm['a'].get('item') or '').split(':')[0])
            if pm['m'] in ('projectile', 'missile') and str(pm['a'].get('bullettype', '')).upper() == 'MOB':
                if pm['a'].get('mob'):
                    mob_stack.append(pm['a']['mob'])

    while stack or mob_stack:
        while stack:
            name = stack.pop()
            key = lower_tree.get(str(name).lower())
            if not key and str(name).lower() in g_sk and g_sk[str(name).lower()][0] != me:
                other, key, entry = g_sk[str(name).lower()]
                tree[key] = entry
                lower_tree[key.lower()] = key
                if notes is not None:
                    notes.append(f'skill «{key}» del pack {other} (en MythicMobs los nombres son de todo el servidor)')
            if not key:
                missing.add(f'skill:{name}')
                continue
            if key in reach_s:
                continue
            reach_s.add(key)
            if key not in tree:  # lista en línea ya compilada
                continue
            v = tree[key]
            mechs = [pm for pm in (C.parse_line(line) for line in (v.get('Skills') or [])) if pm]
            inline = {}
            C.extract_inline(mechs, inline, key)
            for iname, ient in inline.items():
                compiled_tree[iname] = ient
                lower_tree[iname.lower()] = iname
                follow(ient['mechs'])
            compiled_tree[key] = {
                'cooldown': v.get('Cooldown'),
                'conditions': [c for c in (C.parse_condition(x) for x in (v.get('Conditions') or [])) if c],
                'target_conditions': [c for c in (C.parse_condition(x) for x in (v.get('TargetConditions') or [])) if c],
                'trigger_conditions': [c for c in (C.parse_condition(x) for x in (v.get('TriggerConditions') or [])) if c],
                'mechs': mechs}
            follow(mechs)
            for cl in ('conditions', 'target_conditions', 'trigger_conditions'):
                for c in compiled_tree[key][cl]:
                    cm = re.match(r'(?:castinstead|orelsecast|cast)\s+(\S+)', c.get('v', ''))
                    if cm:
                        stack.append(cm.group(1))
        while mob_stack:
            name = mob_stack.pop()
            key = lower_mobs.get(str(name).lower())
            # (un nombre de mob de Minecraft no se busca en otros packs: el Abyss tiene un «WITHER_SKELETON» suyo que
            # chocaría con el esqueleto del Wither de Minecraft que saca la Tormenta del Wither)
            if not key and str(name).upper() not in AI.LIVING and str(name).lower() in g_mb and g_mb[str(name).lower()][0] != me:
                other, key, entry = g_mb[str(name).lower()]
                mobs[key] = entry
                lower_mobs[key.lower()] = key
                if notes is not None:
                    notes.append(f'mob «{key}» del pack {other} (en MythicMobs los nombres son de todo el servidor)')
            if not key and str(name).upper() in AI.LIVING:
                # summon{type=WITHER_SKELETON}: un mob de Minecraft tal cual (SkillRuntime.summonVanilla)
                if notes is not None and f'mob de Minecraft {str(name).upper()}' not in notes:
                    notes.append(f'mob de Minecraft {str(name).upper()}')
                continue
            if not key:
                missing.add(f'mob:{name}')
                continue
            if key in reach_m:
                continue
            reach_m.add(key)
            v = mobs[key]
            mechs = [pm for pm in (C.parse_line(line) for line in (v.get('Skills') or [])) if pm]
            inline = {}
            C.extract_inline(mechs, inline, 'mob_' + key)
            for iname, ient in inline.items():
                compiled_tree[iname] = ient
                lower_tree[iname.lower()] = iname
                follow(ient['mechs'])
            mob = {'type': str(v.get('Type', 'ZOMBIE')).upper(), 'options': v.get('Options') or {},
                   'display': v.get('Display'), 'health': v.get('Health'), 'mechs': mechs}
            for yk, ok in (('AIGoalSelectors', 'ai_goals'), ('AITargetSelectors', 'ai_targets'), ('Damage', 'damage'),
                           ('Armor', 'armor'), ('Faction', 'faction'), ('DamageModifiers', 'damage_modifiers'),
                           ('BossBar', 'bossbar'), ('Modules', 'modules'), ('DisplayOptions', 'display_options'), ('Hearing', 'hearing'),
                           ('Drops', 'drops'), ('Equipment', 'equipment')):
                if v.get(yk) is not None:
                    mob[ok] = v[yk]
            compiled_mobs[key] = mob
            follow(mechs)
    return {'tree': compiled_tree, 'mobs': compiled_mobs, 'items': {k: items[k] for k in sorted(used_items) if k in items},
            'all_items': items, 'missing': sorted(missing)}


def model_scripts(models):
    """Las skills que lanzan los scripts «mm:» de las animaciones de los modelos."""
    out = set()
    for m in models.values():
        for a in m['anims'].values():
            for _t, skill in a.get('fx', []):
                out.add(skill)
    return out


def item_skills(items):
    out = set()
    for it in items.values():
        for line in it.get('Skills') or []:
            pm = C.parse_line(line)
            if pm:
                s2, _ = C.refs(pm)
                out.update(s2)
    return out


def me_textures(pack_dir, bid):
    """Las texturas de los .bbmodel del pack como las deja ModelEngine en su resource pack
    (assets/modelengine/textures/entity/<nombre>.png): una carpeta «assets» de apoyo en el directorio temporal."""
    import tempfile
    root = os.path.join(tempfile.gettempdir(), 'tf_bosses_me', bid, 'assets')
    shutil.rmtree(root, ignore_errors=True)
    dest = os.path.join(root, 'modelengine', 'textures', 'entity')
    os.makedirs(dest, exist_ok=True)
    for d, _, fs in os.walk(pack_dir):
        for f in fs:
            if not f.endswith('.bbmodel'):
                continue
            path = os.path.join(d, f)
            for t in json.load(open(path, encoding='utf-8')).get('textures', []):
                img = B.load_texture(t, path)
                name = os.path.splitext(str(t.get('name') or ''))[0].lower()
                if img is not None and name and not os.path.exists(os.path.join(dest, name + '.png')):
                    img.save(os.path.join(dest, name + '.png'))
    return root


def mob_kind(mob):
    """«entity» (un mob de verdad en el juego) o «actor» (un efecto que dibuja el cliente)."""
    opts = mob.get('options') or {}
    if mob['type'] in LIVING and not opts.get('Invincible'):
        return 'entity'
    return 'actor'


def build(pack_dir, name, conf, sound_events):
    pack = BS.Pack(pack_dir, name)
    pack.id = conf['id']
    for k, v in ITEM_FIXES.get(name, {}).items():
        pack.config_models.setdefault(k, v)
    bid = pack.id
    problems = pack.problems

    # Modelos de ModelEngine (todos los del pack: las skills los nombran por su nombre de archivo)
    me_models, converted = {}, {}
    store = B.TextureStore(os.path.join(OUT_TEX, bid, 'me'), f'tfclient:textures/bosses/{bid}/me/')
    for d, _, fs in sorted(os.walk(pack_dir)):
        for f in sorted(fs):
            if not f.endswith('.bbmodel'):
                continue
            mname = f[:-len('.bbmodel')].lower()
            if mname in me_models:
                continue
            m = B.convert(os.path.join(d, f), store, problems, boss=True)
            if m:
                mid = f'{bid}.{BS.slug(mname)}'
                me_models[mname] = mid
                converted[mname] = m

    sk_files, mob_files, item_files = files(pack_dir)
    all_items = load_all(item_files, 'item')
    if not hasattr(pack, 'notes'):
        pack.notes = []
    prog = compile_boss(pack_dir, conf['mob'], model_scripts(converted) | item_skills(all_items), pack.notes)
    if conf['mob'] not in prog['mobs']:
        raise SystemExit(f'{name}: no está el mob {conf["mob"]}')

    # Sonidos
    sounds = BS.pack_sounds(pack)
    used_sounds = {}

    def sound_ref(s):
        s = str(s).strip()
        bare = s.split(':', 1)[-1]
        found = sounds.get(s) or sounds.get(bare) or BS.similar_sound(sounds, s)
        if found:
            ev = f'bosses.{bid}.{BS.slug(bare)}'
            used_sounds.setdefault(ev, found)
            return f'tfclient:{ev}'
        vanilla = bare if s.startswith('minecraft:') or ':' not in s else None
        vanilla = VC.SOUND_SUBST.get(vanilla, vanilla) if vanilla else None
        if vanilla and vanilla in VC.valid_sounds():
            return f'minecraft:{vanilla}'
        problems.append(f'sonido desconocido {s}')
        return None

    # Modelos de ítem: las balas «display» (material + model=CustomModelData) y los objetos que suelta el jefe.
    # Sus texturas «modelengine:entity/x» las genera ModelEngine desde los .bbmodel (el pack no las trae): se sacan de
    # ahí a un resource pack de apoyo
    pack.roots.append(me_textures(pack_dir, bid))
    models = BS.ItemModels(pack, 'bosses')
    item_models = {}

    def display_model(material, cmd):
        key = f'{str(material).lower()}:{cmd}'
        if key not in item_models:
            ref = BS.item_override(pack, material, cmd)
            mid = models.model(ref) if ref else None
            if not mid:
                problems.append(f'sin modelo de ítem para {key}')
            item_models[key] = mid
        return item_models[key]

    def display_item(name):
        """El modelo de un objeto que enseña un item_display (setDisplayEntityItem o su DisplayOptions.Item): el del pack
        (Items: material y Model) o uno de Minecraft. (1.3.89: se buscaba solo entre los objetos que suelta el jefe y las
        llamas de Ignevar, «ignevar_flamethower_01..10» = INK_SAC con su Model, quedaban como minecraft:item/... que no
        existe: no se veían.)"""
        items_low = {str(k).lower(): v for k, v in (prog.get('all_items') or {}).items()}
        idef = items_low.get(str(name).lower())
        if idef is not None:
            mat = idef.get('Id') or idef.get('Material') or 'paper'
            cmd = idef.get('Model') or idef.get('CustomModelData')
            mid = display_model(mat, cmd) if cmd is not None else None
            if mid is None:
                mid = f'minecraft:item/{str(mat).lower()}'
                item_models[f'{str(mat).lower()}:'] = mid
            return mid
        if name and re.fullmatch(r'[a-z0-9_]+', str(name).lower()):
            return item_models.setdefault(f'{str(name).lower()}:', f'minecraft:item/{str(name).lower()}')
        return None

    def fix_mech(pm):
        a = pm.get('a', {})
        m = pm['m']
        if m in ('effect:sound', 'sound', 'e:s', 'effect:s'):
            for k in ('s', 'sound'):
                if k in a:
                    r = sound_ref(a[k])
                    if r:
                        a[k] = r
        if m in ('model', 'state', 'changepart', 'partvis', 'partvisibility', 'tint', 'remapmodel', 'mountmodel',
                 'lockmodel', 'brightness', 'bodyrotation', 'bodyclamp', 'setmodelscale', 'renderinit', 'segment',
                 'defaultstate', 'dismountall', 'dismountmodel'):
            for k in ('mid', 'm', 'modelid', 'model', 'newmodelid', 'nmid', 'nm', 'n', 'newmodel'):
                if k in a and str(a[k]).lower() in me_models:
                    a[k] = me_models[str(a[k]).lower()]
        if m == 'setdisplayentityitem':
            # El objeto del pack (Items: su material y Model) o uno de Minecraft: el modelo que dibuja el efecto
            name = str(a.get('i', a.get('item', a.get('material', a.get('m', '')))))
            mid = display_item(name)
            if mid:
                a['itemmodel'] = mid
        bt = str(a.get('bullettype', a.get('bullet', a.get('bt', '')))).upper()
        if m in ('projectile', 'missile') and bt in ITEM_BULLETS and 'bulletitem' not in a:
            # Balas ITEM/MYTHICITEM/TRACKING/REALTRACKING/BLOCK/SMALLBLOCK/ARROW: el modelo de su objeto (el del pack con
            # su Model, o el de Minecraft); con material=air no se ve nada
            if bt == 'ARROW':
                kind = str(a.get('arrowtype', 'NORMAL')).upper()
                mat, cmd = {'SPECTRAL': 'spectral_arrow'}.get(kind, 'arrow'), None
            else:
                mat = str(a.get('bulletmaterial', a.get('material', a.get('mat', 'stone'))))
                cmd = a.get('bulletmodel', a.get('model'))
                idef = {str(k).lower(): v for k, v in (prog.get('all_items') or {}).items()}.get(mat.lower())
                if idef is not None:  # un objeto de MythicMobs (MYTHICITEM)
                    mat = idef.get('Id') or idef.get('Material') or 'paper'
                    cmd = cmd or idef.get('Model') or idef.get('CustomModelData')
            mat = str(mat).lower()
            if mat != 'air':
                mid = display_model(mat, cmd) if cmd is not None else None
                a['bulletitem'] = mid or item_models.setdefault(f'{mat}:', f'minecraft:item/{mat}')
        if m in ('projectile', 'missile') and str(a.get('bullettype', '')).lower() == 'display':
            mat = a.get('material') or a.get('mat') or 'stone'
            cmd = a.get('model') or a.get('custommodeldata') or a.get('cmd')
            mid = display_model(mat, cmd) if cmd else None
            if mid:
                a['bulletitem'] = mid
        return pm

    for ent in prog['tree'].values():
        ent['mechs'] = [fix_mech(pm) for pm in ent['mechs']]
    for mob in prog['mobs'].values():
        mob['mechs'] = [fix_mech(pm) for pm in mob['mechs']]
        mob['kind'] = mob_kind(mob)
        # El objeto con el que nace un item_display (DisplayOptions.Item)
        dopt = mob.get('display_options') or {}
        if mob['type'] == 'ITEM_DISPLAY' and dopt.get('Item'):
            mid = display_item(dopt['Item'])
            if mid:
                mob['display_item'] = mid
        mob['speed'] = SPEED.get(mob['type'], 0.25)
        # El modelo de ModelEngine que se pone (para la vista previa, el tamaño y la caja de golpe del mob)
        for pm in mob['mechs']:
            if pm['m'] == 'model' and pm.get('tr') in (None, 'onspawn', 'oncombat', 'onspawnorload', 'onready'):
                mid = pm['a'].get('mid') or pm['a'].get('m') or pm['a'].get('modelid')
                if mid:
                    mob['model'] = mid
                    break
    # Los modelos puestos dentro de una lista en línea del ~onSpawn (skill{s=[ - model{...} ]})
    for key, mob in prog['mobs'].items():
        if mob.get('model'):
            continue
        for pm in mob['mechs']:
            if pm['m'] in ('skill', 's') and pm.get('tr') in (None, 'onspawn', 'oncombat', 'onspawnorload', 'onready'):
                sub = prog['tree'].get(pm['a'].get('s', ''))
                for sp in (sub or {}).get('mechs', []):
                    if sp['m'] == 'model':
                        mob['model'] = sp['a'].get('mid') or sp['a'].get('m')
                        break
            if mob.get('model'):
                break

    # Erratas del pack en los nombres de animación (Astwood pone «absord_life_start» y su modelo la llama
    # «absorb_life_start»: en ModelEngine no sale, y su absorber vida iba sin animación)
    pack.notes = []
    all_anims = {a for m in converted.values() for a in m['anims']}

    def fix_anim(pm):
        if pm['m'] not in ('state', 'animation', 'defaultstate'):
            return
        for k in ('state', 's', 'animation'):
            v = pm['a'].get(k)
            if v and v not in all_anims:
                good = typo_fix(v, all_anims)
                if good:
                    pm['a'][k] = good
                    note = f'errata del pack: animación «{v}» → «{good}»'
                    if note not in pack.notes:
                        pack.notes.append(note)

    for ent in prog['tree'].values():
        for pm in ent['mechs']:
            fix_anim(pm)
    for mob in prog['mobs'].values():
        for pm in mob['mechs']:
            fix_anim(pm)

    # Scripts de las animaciones: con el nombre de la skill tal como está en el árbol
    lower_tree = {k.lower(): k for k in prog['tree']}
    for m in converted.values():
        for a in m['anims'].values():
            if 'fx' in a:
                a['fx'] = [[t, lower_tree.get(s.lower(), s)] for t, s in a['fx']]

    # Objetos que suelta (dropitem): su modelo y nombre (el Esqueleto de la Torre deja su glaive, escudo y cuernos)
    drops = {}
    for iid, idef in prog['items'].items():
        mat = idef.get('Id') or idef.get('Material') or 'paper'
        cmd = idef.get('Model') or idef.get('CustomModelData')
        mid = display_model(mat, cmd) if cmd is not None else None
        drops[iid] = {'material': str(mat).lower(), 'model': mid, 'name': BS.strip_mc(idef.get('Display') or iid),
                      'type': str(idef.get('Type') or '').upper()}
        # el escudo cubriéndose: el modelo de «blocking» del mismo pack
        if str(mat).upper() == 'SHIELD' and cmd is not None:
            p = pack.find('minecraft/models/item/shield.json')
            if p:
                data = json.load(open(p, encoding='utf-8'))
                for ov in data.get('overrides', []):
                    pr = ov.get('predicate', {})
                    if pr.get('blocking') and int(float(pr.get('custom_model_data', -1))) == int(float(cmd)):
                        drops[iid]['blocking'] = models.model(ov['model'])
        # Sus skills (~onSwing del arma, ~onTimer del escudo), con los sonidos y modelos ya del mod
        drops[iid]['mechs'] = [fix_mech(pm) for pm in (C.parse_line(x) for x in (idef.get('Skills') or [])) if pm]
        drops[iid]['name_es'] = (ES.get(bid) or {}).get('drops', {}).get(iid) or drops[iid]['name']
        if mid:
            item = {'parent': mid}
            if drops[iid].get('blocking'):
                item['overrides'] = [{'predicate': {'blocking': 1}, 'model': drops[iid]['blocking']}]
            os.makedirs(OUT_ITEM_JSON, exist_ok=True)
            with open(os.path.join(OUT_ITEM_JSON, item_id(bid, iid) + '.json'), 'w', encoding='utf-8') as fo:
                json.dump(item, fo, indent=1)

    glyphs = BS.write_glyphs(pack, prog, 'bosses')

    # Sonidos al mod
    for ev, srcs in used_sounds.items():
        rels = []
        for i, src in enumerate(srcs):
            nm = ev.split('.')[-1] + (f'_{i}' if len(srcs) > 1 else '')
            dest = os.path.join(OUT_SOUNDS, bid, nm + '.ogg')
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            shutil.copyfile(src, dest)
            rels.append(f'tfclient:bosses/{bid}/{nm}')
        sound_events[ev] = {'sounds': rels}

    # Modelos de ModelEngine al mod
    os.makedirs(OUT_MODELS, exist_ok=True)
    for mname, mid in me_models.items():
        with open(os.path.join(OUT_MODELS, mid + '.json'), 'w', encoding='utf-8') as fo:
            json.dump(converted[mname], fo, separators=(',', ':'))

    es = ES.get(bid) or {}
    if not es:
        problems.append('sin textos en español (tools/bosses/es.json)')
    root = prog['mobs'][conf['mob']]
    hitbox = None
    if root.get('model'):
        mm = converted.get(root['model'].split('.', 1)[-1])
        hitbox = mm.get('hitbox') if mm else None
    # Su relevo: los mobs de verdad que saca al morir con al menos la mitad de su vida (el Mamut del Druida Boreal)
    succ = {}
    for sname in successors(prog, conf['mob']):
        succ[sname] = (es.get('successors') or {}).get(sname) or BS.strip_mc(prog['mobs'][sname].get('display') or sname)
        pack.notes.append(f'relevo: al morir sale {sname} ({succ[sname]}) y la pelea sigue hasta que cae')
    boss = {'mob': conf['mob'], 'name': es.get('name') or BS.strip_mc(root.get('display') or conf['mob']),
            'title': es.get('title', ''), 'desc': es.get('desc', ''), 'color': es.get('color', '#ffffff'),
            'health': conf['vida'], 'damage': conf['dano'], 'pack_health': root.get('health') or conf['vida'],
            'model': root.get('model'), 'hitbox': hitbox, 'attacks': es.get('attacks', []),
            'preview_hide': es.get('preview_hide') if 'preview_hide' in es else spawn_hidden(prog, conf['mob'])}
    if succ:
        boss['successors'] = succ
    # (1.3.88, orden del dueño) «suelo»: sus golpes no suben al jugador al cielo (el Contemplador, JEFES.md §3.13)
    if es.get('suelo'):
        boss['suelo'] = True
    out = {'id': bid, 'pack': name, 'boss': boss, 'class': {'name': boss['name']}, 'skills': [],
           'tree': prog['tree'], 'mobs': prog['mobs'], 'models': me_models, 'item_models': {k: v for k, v in item_models.items() if v},
           'drops': drops, 'glyphs': glyphs, 'missing': [m.split(':', 1)[1] for m in prog['missing'] if m.startswith('skill:')]}
    if HUECOS.get(bid):
        out['gaps'] = HUECOS[bid]
    for m in prog['missing']:
        if m.split(':', 1)[1] in HUECOS.get(bid, {}):
            pack.notes.append(f'hueco del pack: {m} ({HUECOS[bid][m.split(":", 1)[1]]})')
        else:
            problems.append(f'falta {m}')
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, bid + '.json'), 'w', encoding='utf-8') as f:
        json.dump(out, f, ensure_ascii=False, separators=(',', ':'))
    return pack, out


def spawn_hidden(prog, root):
    """Las piezas que el jefe esconde al aparecer (partvis{v=false} de su ~onSpawn, también en lo que llama sin
    retardo): la vista previa de /tf boss lo enseña así, como en la pelea."""
    tree, low = prog['tree'], {k.lower(): k for k in prog['tree']}
    out, seen = [], set()
    todo = [pm for pm in prog['mobs'][root]['mechs'] if pm.get('tr') in ('onspawn', 'onready', 'onspawnorload')]
    while todo:
        pm = todo.pop(0)
        a = pm.get('a', {})
        if float(a.get('delay', 0) or 0) > 0:
            continue
        if pm['m'] in ('partvis', 'partvisibility') and pm.get('t') in (None, 'self', 'caster') \
                and str(a.get('v', a.get('visible', a.get('visibility', 'false')))).lower() == 'false':
            part = a.get('p', a.get('part', a.get('pid', a.get('partid'))))
            if part and str(a.get('em', a.get('exactmatch', 'true'))).lower() != 'false' and part not in out:
                out.append(part)
        if pm['m'] in ('skill', 'metaskill', 's'):
            k = low.get(str(a.get('s', a.get('skill', ''))).lower())
            if k and k not in seen:
                seen.add(k)
                todo += tree[k]['mechs']
    return out


def successors(prog, root):
    """Los mobs de verdad (entity) que el jefe saca en su ~onDeath (o en lo que llama desde ahí) con al menos la mitad
    de su vida del pack: su relevo, que sigue la pelea (BossEntity.becomeSuccessor)."""
    tree, mobs = prog['tree'], prog['mobs']
    low = {k.lower(): k for k in tree}
    lowm = {k.lower(): k for k in mobs}
    main_hp = float(mobs[root].get('health') or 0)
    found, seen = [], set()
    todo = [pm for pm in mobs[root]['mechs'] if pm.get('tr') == 'ondeath']
    while todo:
        pm = todo.pop(0)
        if pm['m'] in ('summon', 'spawn'):
            a = pm.get('a', {})
            k = lowm.get(str(a.get('type', a.get('t', a.get('mob', a.get('m', ''))))).lower())
            md = mobs.get(k) if k else None
            if md and k != root and md.get('kind') == 'entity' and float(md.get('health') or 0) >= 0.5 * main_hp \
                    and k not in found:
                found.append(k)
        for n in C.refs(pm)[0]:
            k = low.get(str(n).lower())
            if k and k not in seen:
                seen.add(k)
                todo += tree[k]['mechs']
    return found


def typo_fix(name, names):
    """El nombre bien escrito si «name» tiene una sola letra cambiada por otra (absord → absorb) y solo hay uno así; si
    no, None. Los números no cuentan: head_6_bite no es head_1_bite."""
    cands = []
    for n in names:
        if len(n) != len(name):
            continue
        diff = [(x, y) for x, y in zip(name, n) if x != y]
        if len(diff) == 1 and diff[0][0].isalpha() and diff[0][1].isalpha():
            cands.append(n)
    return cands[0] if len(cands) == 1 else None


def item_id(bid, iid):
    """El id del objeto en el mod (igual que TFBosses.itemId)."""
    return re.sub(r'[^a-z0-9_]+', '_', f'jefe_{bid}_{iid}'.lower())


def confs():
    """[(carpeta del pack, conf)] en el orden de JEFES (un pack puede dar varios jefes: una lista)."""
    out = []
    for name, c in JEFES.items():
        for one in (c if isinstance(c, list) else [c]):
            out.append((name, one))
    return out


def clean(bid, sj):
    """Borra lo que salió de un jefe (para rehacerlo solo a él)."""
    old = os.path.join(OUT, bid + '.json')
    if os.path.exists(old):
        try:
            for iid in json.load(open(old, encoding='utf-8')).get('drops', {}):
                p = os.path.join(OUT_ITEM_JSON, item_id(bid, iid) + '.json')
                if os.path.exists(p):
                    os.remove(p)
        except ValueError:
            pass
        os.remove(old)
    if os.path.isdir(OUT_MODELS):
        for f in os.listdir(OUT_MODELS):
            if f.startswith(bid + '.'):
                os.remove(os.path.join(OUT_MODELS, f))
    for d in (OUT_TEX, OUT_ITEM_MODELS, OUT_SOUNDS):
        shutil.rmtree(os.path.join(d, bid), ignore_errors=True)
    font = os.path.join(BS.OUT_FONTS, f'bosses_{bid}.json')
    if os.path.exists(font):
        os.remove(font)
    for k in [k for k in sj if k.startswith(f'bosses.{bid}.')]:
        del sj[k]


def main():
    root = sys.argv[1]
    only = set(sys.argv[2:])
    full = not only
    sj = json.load(open(SOUNDS_JSON, encoding='utf-8')) if os.path.exists(SOUNDS_JSON) else {}
    if full:
        for d in (OUT, OUT_TEX, OUT_ITEM_MODELS, OUT_SOUNDS):
            shutil.rmtree(d, ignore_errors=True)
        for f in os.listdir(BS.OUT_FONTS) if os.path.isdir(BS.OUT_FONTS) else []:
            if f.startswith('bosses_'):
                os.remove(os.path.join(BS.OUT_FONTS, f))
        for f in os.listdir(OUT_ITEM_JSON) if os.path.isdir(OUT_ITEM_JSON) else []:
            if f.startswith('jefe_'):
                os.remove(os.path.join(OUT_ITEM_JSON, f))
        sj = {k: v for k, v in sj.items() if not k.startswith('bosses.')}
    cat_path = os.path.join(OUT, 'catalog.json')
    old_catalog = [] if full or not os.path.exists(cat_path) else json.load(open(cat_path, encoding='utf-8'))
    sound_events, catalog = {}, []
    for name, conf in confs():
        if only and name not in only:
            continue
        d = os.path.join(root, name)
        if not os.path.isdir(d):
            print(f'falta el pack {name} en {root}')
            continue
        unpack_nested(d)
        if not full:
            clean(conf['id'], sj)
        pack, out = build(d, name, conf, sound_events)
        catalog.append({'id': pack.id, 'name': out['boss']['name']})
        print(f"{pack.id:18s} {len(out['tree'])} skills, {len(out['mobs'])} mobs, {len(out['models'])} modelos ME, "
              f"{len(out['item_models'])} modelos de ítem, {len(out['drops'])} objetos, problemas: {len(pack.problems)}")
        for p in pack.problems[:int(os.environ.get('MAXP', 20))]:
            print('   -', p)
        for n in getattr(pack, 'notes', []):
            print('   ·', n)
    sj.update(sound_events)
    with open(SOUNDS_JSON, 'w', encoding='utf-8') as f:
        json.dump(sj, f, ensure_ascii=False, indent=1)
    # El catálogo (orden de la ventana /tf boss): el de JEFES; sin «pack», solo los hechos; con «pack», los de antes
    # más los rehechos
    built = {c['id'] for c in catalog}
    merged = {c['id']: c for c in old_catalog if c['id'] not in built}
    merged.update({c['id']: c for c in catalog})
    order = [c['id'] for _n, c in confs()]
    final = sorted(merged.values(), key=lambda c: order.index(c['id']) if c['id'] in order else len(order))
    os.makedirs(OUT, exist_ok=True)
    with open(cat_path, 'w', encoding='utf-8') as f:
        json.dump(final, f, ensure_ascii=False, indent=1)
    if full:
        # La grieta (geometría propia, no de un pack; 1.3.88: la 3.0, con shaders) vive en las mismas carpetas: se rehace
        import grieta3
        grieta3.main()
    BS.write_atlas()
    # El auditor (tools/bosses/audit.py): lo que el motor no tiene o el pack nombra mal; debe dar 0 en cada jefe
    # (por su ruta: tools/skills tiene otro audit.py, el de las clases)
    import importlib.util
    spec = importlib.util.spec_from_file_location('boss_audit', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'audit.py'))
    audit = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(audit)
    audit.main([c['id'] for c in catalog])


if __name__ == '__main__':
    main()
