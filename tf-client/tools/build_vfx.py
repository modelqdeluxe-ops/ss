"""Mete los VFX (efectos de kill y paquetes de skills) en el TF Client y en la web.

Lee los packs descomprimidos (ModelEngine + MythicMobs + sonidos de ItemsAdder/Oraxen) y escribe:
  tf-client/src/main/resources/assets/tfclient/
    vfx/models/<modelo>.json     modelos animados ya horneados (ver vfx_bb.py)
    vfx/fx.json                  línea de tiempo de cada efecto: modelos, sonidos y partículas (lo dibuja el cliente)
    vfx/catalog.json             efectos de kill y paquetes de skills con sus disparadores y cooldowns (servidor)
    textures/vfx/*.png           texturas (sin repetir)
    textures/gui/vfx/<id>.png    icono de cada efecto y paquete (para el indicador junto a la barra)
    sounds/vfx/**.ogg, sounds.json
  tierras-fantasticas/config/vfx.json      catálogo para la tienda (nombres, descripciones, cooldowns)
  tierras-fantasticas/public/img/vfx/*.webp miniaturas (sacadas del propio modelo animado)

Los packs comprados no se suben al repositorio: solo lo convertido que usa el mod.

Uso: python3 tools/build_vfx.py <carpeta con los packs descomprimidos>
"""
import glob
import json
import os
import shutil
import sys

import numpy as np
from PIL import Image

import vfx_bb as B
import vfx_mm as M
import vfx_render as R
import vfx_skills as SK

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'tfclient')
WEB = os.path.join(HERE, '..', '..', 'tierras-fantasticas')
MODELS_OUT = os.path.join(RES, 'vfx', 'models')
TEX_OUT = os.path.join(RES, 'textures', 'vfx')
ICON_OUT = os.path.join(RES, 'textures', 'gui', 'vfx')
SOUND_OUT = os.path.join(RES, 'sounds', 'vfx')
WEB_IMG = os.path.join(WEB, 'public', 'img', 'vfx')

# Partículas de Bukkit/MythicMobs → Minecraft 1.20.1
PARTICLES = {
    'reddust': 'dust', 'redstone': 'dust', 'dust': 'dust', 'dust_color_transition': 'dust_color_transition',
    'enchantment_table': 'enchant', 'enchant': 'enchant', 'spell_instant': 'instant_effect',
    'instant_effect': 'instant_effect', 'spell': 'effect', 'smoke_normal': 'smoke', 'smoke': 'smoke',
    'water_drop': 'rain', 'block_crack': 'block', 'totem': 'totem_of_undying', 'fireworks_spark': 'firework',
    'villager_happy': 'happy_villager', 'explosion_huge': 'explosion_emitter', 'explosion_large': 'explosion',
    'drip_water': 'dripping_water',
}
VANILLA_PARTICLES = {
    'end_rod', 'white_ash', 'ash', 'crit', 'cloud', 'firework', 'flash', 'electric_spark', 'warped_spore', 'smoke',
    'campfire_cosy_smoke', 'campfire_signal_smoke', 'wax_off', 'portal', 'crimson_spore', 'lava', 'explosion_emitter',
    'explosion', 'rain', 'spit', 'soul', 'happy_villager', 'flame', 'soul_fire_flame', 'reverse_portal', 'heart',
    'falling_obsidian_tear', 'falling_dripstone_water', 'enchanted_hit', 'cherry_leaves', 'dust',
    'dust_color_transition', 'enchant', 'instant_effect', 'effect', 'block', 'totem_of_undying', 'dripping_water',
    'sweep_attack', 'glow', 'snowflake', 'dragon_breath', 'witch', 'note', 'bubble', 'splash', 'falling_water',
    'sonic_boom', 'scrape', 'wax_on', 'squid_ink', 'glow_squid_ink', 'sculk_soul', 'damage_indicator', 'poof',
}

# Efectos de kill: tipo de MythicMobs → (id, nombre, descripción). Volumen: (carpeta del pack, etiqueta)
KILL_VOLUMES = [('v1_16x', 'Vol. 1'), ('v2', 'Vol. 2'), ('v3', 'Vol. 3'), ('v4', 'Vol. 4'), ('v5_16x', 'Vol. 5')]
KILLS = {
    'v1_16x': {
        'AngelicBless': ('bendicion_angelical', 'Bendición Angelical', 'Unas alas de luz se llevan el cuerpo al cielo.'),
        'ArcadeGameover': ('game_over', 'Game Over', 'Un comecocos de recreativa se zampa al caído.'),
        'HellfireBurn': ('fuego_infernal', 'Fuego Infernal', 'Llamas de alma consumen el cuerpo hasta las cenizas.'),
        'Quicksand': ('arenas_movedizas', 'Arenas Movedizas', 'El suelo se abre y la arena se lo traga.'),
        'ImposterInstinct': ('impostor', 'Impostor', 'Alguien sospechoso aparece por detrás. Puñalada.'),
        'TentacleGrasp': ('tentaculos', 'Tentáculos', 'Tentáculos surgen del suelo y lo arrastran abajo.'),
        'SharkAttack': ('ataque_tiburon', 'Ataque de Tiburón', 'Un tiburón sale del suelo y se lo lleva de un bocado.'),
        'KnockoutKO': ('knockout', 'K.O.', 'Golpe de cómic: K.O. y fuera del combate.'),
        'PlantfoodFeasting': ('planta_carnivora', 'Planta Carnívora', 'Una planta gigante brota y lo devora.'),
        'TertisSmash': ('bloques_caen', 'Bloques que Caen', 'Piezas de colores caen del cielo y lo aplastan.'),
    },
    'v2': {
        'HologramFlicker': ('holograma', 'Holograma', 'El cuerpo parpadea como un holograma y se apaga.'),
        'HolyAbsorption': ('absorcion_sagrada', 'Absorción Sagrada', 'Un haz de luz sagrada lo absorbe.'),
        'AquaSplash': ('derretido', 'Derretido', 'Se derrite en un charco de agua.'),
        'Photosynthesis': ('naturaleza', 'Naturaleza', 'Las enredaderas brotan y lo cubren.'),
        'RustyDecay': ('oxido', 'Óxido', 'Se oxida y se cae a trozos.'),
        'SoundWaveDisperse': ('onda_sonora', 'Onda Sonora', 'Una onda de sonido lo deshace.'),
        'SpectralFade': ('espectral', 'Espectral', 'Su espíritu sale del cuerpo y se desvanece.'),
        'StoneCrumble': ('piedra', 'Piedra', 'Se vuelve de piedra y se desmorona.'),
        'VoidCollapse': ('colapso_vacio', 'Colapso del Vacío', 'Un agujero del vacío lo aplasta en un destello.'),
        'WaterEvaporation': ('evaporacion', 'Evaporación', 'Se convierte en agua y se evapora.'),
    },
    'v3': {
        'NebulaDissipation': ('nebulosa', 'Nebulosa', 'Se disuelve en una nube de colores.'),
        'SmokyDisappearance': ('humo', 'Humo', 'Desaparece en una nube de humo.'),
        'PaperTurns': ('papel_quemado', 'Papel Quemado', 'Arde como una hoja de papel.'),
        'Reemergence': ('sombras', 'Sombras', 'Las sombras del suelo se lo tragan.'),
        'MosaicOver': ('mosaico', 'Mosaico', 'Se rompe en teselas de colores.'),
        'MirrorReflectionFade': ('espejo', 'Espejo', 'Su reflejo se agrieta y se esfuma.'),
        'FloralBloom': ('floracion', 'Floración', 'Una rosa brota donde cayó.'),
        'ElectricZparks': ('chispas', 'Chispas', 'Una descarga eléctrica lo hace chisporrotear.'),
        'DimensionalPulls': ('portal', 'Portal', 'Un portal lo arrastra a otra dimensión.'),
        'TimeRetrace': ('reloj', 'Reloj', 'El tiempo se rebobina y lo borra.'),
    },
    'v4': {
        'AcidicCorrosion': ('acido', 'Ácido', 'El ácido lo corroe hasta deshacerlo.'),
        'ClockworkDisassembly': ('engranajes', 'Engranajes', 'Se desmonta pieza a pieza como un reloj.'),
        'FrostInfection': ('escarcha', 'Escarcha', 'El hielo lo cubre y se hace añicos.'),
        'Abstracted': ('abstracto', 'Abstracto', 'Se deforma en formas imposibles.'),
        'GraffitiSpray': ('grafiti', 'Grafiti', 'Un grafiti de cómic estalla en su lugar.'),
        'OrigamiFold': ('origami', 'Origami', 'Se pliega como una figura de papel.'),
        'PlasmaOrb': ('orbe_plasma', 'Orbe de Plasma', 'Un orbe de plasma lo encierra y descarga rayos.'),
        'MagneticResonance': ('magnetismo', 'Magnetismo', 'Un campo magnético lo desarma.'),
        'InkBlots': ('manchas_tinta', 'Manchas de Tinta', 'Se disuelve en manchas de tinta.'),
        'PureForm': ('bioluminiscencia', 'Bioluminiscencia', 'Brilla con luz dorada y se eleva.'),
    },
    'v5_16x': {
        'SweetHoney': ('miel', 'Miel', 'Se convierte en bloques de miel.'),
        'OutlineRetrace': ('contorno', 'Contorno', 'Un lápiz repasa su silueta y la borra.'),
        'QuillInk': ('pluma_tinta', 'Pluma y Tinta', 'Una pluma lo borra trazo a trazo.'),
        'Constellation': ('constelacion', 'Constelación', 'Planetas en órbita lo desintegran.'),
        'AuroraSky': ('aurora', 'Aurora', 'Una aurora boreal lo envuelve y se lo lleva.'),
        'NorthstarRecalls': ('estrella_norte', 'Estrella del Norte', 'Se vuelve dorado y se apaga como una estrella.'),
        'KineticEnergy': ('energia_cinetica', 'Energía Cinética', 'La energía lo dispersa en todas direcciones.'),
        'FogEnvelop': ('niebla', 'Niebla', 'Una niebla mística lo envuelve y lo borra.'),
        'JadeCrystallize': ('jade', 'Jade', 'Se cristaliza en jade y se rompe en pedazos.'),
        'PrismaticLight': ('prisma', 'Prisma', 'Un prisma lo descompone en luz.'),
    },
}

problems = []
# Momento de la animación (fracción) para la miniatura de los efectos en los que el automático no luce
THUMB = {'abstracto': 0.3, 'constelacion': 0.4, 'grafiti': 0.55, 'magnetismo': 0.62, 'manchas_tinta': 0.45,
         'mosaico': 0.22, 'origami': 0.62, 'papel_quemado': 0.28, 'espejo': 0.3, 'prisma': 0.4, 'piedra': 0.2,
         'floracion': 0.35, 'derretido': 0.5, 'estrella_norte': 0.3, 'onda_sonora': 0.25, 'orbe_plasma': 0.62}


def find_one(root, pattern):
    hits = sorted(glob.glob(os.path.join(root, '**', pattern), recursive=True))
    return hits[0] if hits else None


class Sources:
    """Índice de lo que hay en los packs: modelos por nombre, sonidos por espacio de nombres."""

    def __init__(self, root):
        self.root = root
        self.bb = {}
        for p in sorted(glob.glob(os.path.join(root, '**', '*.bbmodel'), recursive=True)):
            self.bb.setdefault(os.path.basename(p)[:-8].lower(), p)
        self.sound_defs = {}
        for p in sorted(glob.glob(os.path.join(root, '**', 'assets', '*', 'sounds.json'), recursive=True)):
            ns = os.path.basename(os.path.dirname(p))
            try:
                data = json.load(open(p, encoding='utf-8'))
            except ValueError:
                continue
            for k, v in data.items():
                self.sound_defs.setdefault(f'{ns}:{k}', v)
        self.oggs = {}
        for p in sorted(glob.glob(os.path.join(root, '**', 'assets', '*', 'sounds', '**', '*.ogg'), recursive=True)):
            rel = p.split(os.sep + 'assets' + os.sep, 1)[1]
            ns, rest = rel.split(os.sep, 1)
            key = f'{ns}:' + rest[len('sounds' + os.sep):-4].replace(os.sep, '/')
            self.oggs.setdefault(key, p)

    def sound_file(self, event):
        """Evento de sonido de un pack (ns:clave) → archivo .ogg."""
        d = self.sound_defs.get(event)
        if not d:
            return None
        for s in d.get('sounds', []):
            name = s['name'] if isinstance(s, dict) else s
            if ':' not in name:
                name = 'minecraft:' + name
            if name in self.oggs:
                return self.oggs[name]
        return None


class Builder:
    def __init__(self, src):
        self.src = src
        self.store = B.TextureStore(TEX_OUT, 'tfclient:textures/vfx/')
        self.models = {}
        self.fx = {}
        self.sounds = {}

    def model(self, name):
        name = name.lower()
        if name in self.models:
            return self.models[name]
        path = self.src.bb.get(name)
        if not path:
            problems.append(f'falta el modelo {name}')
            return None
        m = B.convert(path, self.store, problems)
        if m:
            self.models[name] = m
        return m

    def sound(self, event, out_name):
        """Copia el sonido de un pack y lo registra como tfclient:vfx.<out_name>."""
        f = self.src.sound_file(event)
        if not f:
            problems.append(f'falta el sonido {event}')
            return None
        dest = os.path.join(SOUND_OUT, *out_name.split('.')) + '.ogg'
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        shutil.copyfile(f, dest)
        self.sounds['vfx.' + out_name] = {'sounds': [{'name': 'tfclient:vfx/' + out_name.replace('.', '/')}]}
        return 'tfclient:vfx.' + out_name


def particle_event(args, targs, t0, label):
    raw = (args.get('particle') or args.get('p') or 'reddust').lower()
    ptype = PARTICLES.get(raw, raw)
    if ptype not in VANILLA_PARTICLES:
        problems.append(f'{label}: partícula desconocida {raw}')
        return None
    ev = {
        't': t0 + int(B.num(args.get('delay', 0))),
        'part': ptype,
        'n': int(B.num(args.get('amount', args.get('a', 10)))),
        'hs': B.num(args.get('hs', 0)),
        'vs': B.num(args.get('vs', 0)),
        'sp': B.num(args.get('speed', args.get('s', 0))),
        'y': B.num(args.get('y', 0)) + B.num(targs.get('y', 0)),
        'rep': int(B.num(args.get('repeat', 0))),
        'every': max(1, int(B.num(args.get('repeatinterval', args.get('repeati', 1))))),
    }
    if ptype == 'dust':
        ev['color'] = args.get('color', args.get('c', '#ff0000'))
        ev['size'] = B.num(args.get('size', 1)) or 1
    elif ptype == 'dust_color_transition':
        ev['color'] = args.get('color1', '#ffffff')
        ev['color2'] = args.get('color2', '#ffffff')
        ev['size'] = B.num(args.get('size', 1)) or 1
    elif ptype == 'block':
        ev['block'] = 'minecraft:' + (args.get('material') or args.get('m') or 'stone').lower()
    bone = targs.get('p') or targs.get('pid')
    if bone:
        ev['bone'] = bone
    return ev


def build_kills(b):
    kills = []
    for vol, label in KILL_VOLUMES:
        pack = find_one(b.src.root, f'EC_KillEffects_{vol}')
        if not pack:
            problems.append(f'falta el pack de kill effects {vol}')
            continue
        mobs = M.load(glob.glob(os.path.join(pack, 'Mobs', '*.yml'))[0])
        skills = M.load(glob.glob(os.path.join(pack, 'Skills', '*.yml'))[0])
        for kind, (kid, name, desc) in KILLS[vol].items():
            mob = mobs.get(f'KFX_{vol}-{kind}')
            if not mob:
                problems.append(f'{vol}: no está el efecto {kind}')
                continue
            lines = M.skills_of(mob) + M.skills_of(skills.get(f'KFX_{vol}-{kind}-Cast-MainHand'))
            model_name, life, events, sound = None, None, [], None
            for mech, args, target, targs, trig in lines:
                if mech == 'model':
                    model_name = args.get('m') or args.get('mid')
                elif mech in ('remove', 'hide') and 'delay' in args:
                    d = int(B.num(args['delay']))
                    life = d if life is None else min(life, d)
                elif mech == 'sound' and sound is None:
                    sound = args.get('s')
                elif mech in ('effect:particles', 'particles', 'effect:particle'):
                    ev = particle_event(args, targs, 0, f'{vol}/{kind}')
                    if ev:
                        events.append(ev)
            m = b.model(model_name) if model_name else None
            if not m:
                problems.append(f'{vol}/{kind}: sin modelo')
                continue
            anim = 'spawn' if 'spawn' in m['anims'] else next(iter(m['anims']), None)
            life = life or 50
            fx_id = 'kill_' + kid
            # El modelo hace de cuerpo: el de verdad no se dibuja mientras cae (20 ticks)
            ev0 = [{'t': 0, 'model': model_name.lower(), 'anim': anim, 'life': life, 'skin': True, 'hop': 1.0},
                   {'t': 0, 'hide': 'victim', 'ticks': 20}]
            if sound:
                s = b.sound(sound, 'kill.' + kid)
                if s:
                    ev0.append({'t': 0, 'sound': s, 'vol': 3.0, 'pitch': 1.0})
            else:
                problems.append(f'{vol}/{kind}: sin sonido')
            b.fx[fx_id] = {'dur': life + 2, 'ev': ev0 + events}
            entry = {'id': kid, 'name': name, 'desc': desc, 'fx': fx_id, 'volume': label, 'model': model_name.lower(),
                     'anim': anim}
            if kid in THUMB:
                entry['thumbTime'] = THUMB[kid] * m['anims'][anim]['len']
            kills.append(entry)
    return kills


def best_time(m, anim, tex):
    """Instante más vistoso de la animación (más superficie de color) para la miniatura."""
    ln = m['anims'][anim]['len'] if anim in m['anims'] else 1
    best, best_t = -1, ln / 2
    for k in range(1, 12):
        t = ln * k / 12
        img = R.render(m, tex, anim, t, size=96, ss=1, tick=int(t * 20))
        a = np.asarray(img, np.float32) / 255
        rgb, al = a[..., :3], a[..., 3]
        sat = rgb.max(-1) - rgb.min(-1)
        score = float((al * (0.35 + sat)).sum())
        if score > best:
            best, best_t = score, t
    return best_t


GREY_SKIN = None


def grey_skin():
    """La piel gris de los efectos de kill (cabeza de las miniaturas de los hechizos)."""
    global GREY_SKIN
    if GREY_SKIN is None:
        m = json.load(open(os.path.join(MODELS_OUT, 'kfx_plasma_orb.json')))
        t = next(x for x in m['tex'] if x['body'])
        GREY_SKIN = np.asarray(Image.open(os.path.join(TEX_OUT, t['path'].split('/')[-1])).convert('RGBA'), np.float32) / 255
    return GREY_SKIN


def thumbnail(m, anim, t, size, tex, yaw=-28, pitch=-14):
    lo, hi = R.bounds(m, anim, [t])
    center = (lo + hi) / 2
    rad = max(np.linalg.norm(hi - lo) / 2, 8)
    dist = rad / np.tan(np.radians(20)) * 1.08
    return R.render(m, tex, anim, t, size=size, center=center, dist=dist, yaw=yaw, pitch=pitch, tick=int(t * 20),
                    skin=grey_skin())


def write_images(b, entries):
    os.makedirs(ICON_OUT, exist_ok=True)
    os.makedirs(WEB_IMG, exist_ok=True)
    for e in entries:
        m = b.models[e['model']]
        tex = R.load_textures(m, TEX_OUT)
        t = e.get('thumbTime')
        if t is None:
            t = best_time(m, e['anim'], tex)
        img = thumbnail(m, e['anim'], t, 512, tex, **e.get('view', {}))
        bg = Image.new('RGBA', img.size, (0, 0, 0, 0))
        bg.alpha_composite(img)
        bg.save(os.path.join(WEB_IMG, e['image'] + '.webp'), 'WEBP', quality=86, method=6)
        icon = img.copy()
        bbox = icon.getbbox()
        if bbox:
            icon = icon.crop(bbox)
        side = max(icon.size)
        sq = Image.new('RGBA', (side, side))
        sq.alpha_composite(icon, ((side - icon.width) // 2, (side - icon.height) // 2))
        sq.resize((32, 32), Image.LANCZOS).save(os.path.join(ICON_OUT, e['image'] + '.png'))


def main(root):
    for d in (MODELS_OUT, TEX_OUT, ICON_OUT, SOUND_OUT, WEB_IMG):
        shutil.rmtree(d, ignore_errors=True)
        os.makedirs(d, exist_ok=True)
    b = Builder(Sources(root))
    kills = build_kills(b)
    packs = SK.build(b, problems)

    for name, m in b.models.items():
        with open(os.path.join(MODELS_OUT, name + '.json'), 'w') as f:
            json.dump(m, f, separators=(',', ':'))
    with open(os.path.join(RES, 'vfx', 'fx.json'), 'w') as f:
        json.dump(b.fx, f, separators=(',', ':'), ensure_ascii=False)
    sounds_path = os.path.join(RES, 'sounds.json')
    sounds = {}
    if os.path.exists(sounds_path):
        sounds = {k: v for k, v in json.load(open(sounds_path)).items() if not k.startswith('vfx.')}
    sounds.update(dict(sorted(b.sounds.items())))
    with open(sounds_path, 'w') as f:
        json.dump(sounds, f, indent=1, ensure_ascii=False)

    for k in kills:
        k['image'] = 'kill_' + k['id']
    for p in packs:
        p['image'] = 'pack_' + p['id']
    write_images(b, kills + packs)

    catalog = {
        'kills': [{'id': k['id'], 'name': k['name'], 'desc': k['desc'], 'fx': k['fx'], 'volume': k['volume']}
                  for k in kills],
        'packs': [{key: p[key] for key in ('id', 'name', 'desc', 'color', 'skills')} for p in packs],
    }
    with open(os.path.join(RES, 'vfx', 'catalog.json'), 'w') as f:
        json.dump(catalog, f, indent=1, ensure_ascii=False)
    web = {
        'kills': [{'id': k['id'], 'name': k['name'], 'desc': k['desc'], 'volume': k['volume'],
                   'image': f'img/vfx/{k["image"]}.webp'} for k in kills],
        'packs': [{'id': p['id'], 'name': p['name'], 'desc': p['desc'], 'color': p['color'],
                   'image': f'img/vfx/{p["image"]}.webp',
                   'skills': [{k: s[k] for k in ('name', 'desc', 'trigger', 'cooldown', 'vida', 'chance') if k in s}
                              for s in p['skills']]} for p in packs],
    }
    with open(os.path.join(WEB, 'config', 'vfx.json'), 'w') as f:
        json.dump(web, f, indent=1, ensure_ascii=False)

    total = sum(os.path.getsize(os.path.join(dp, fn)) for dp, _, fns in os.walk(os.path.join(RES, 'vfx')) for fn in fns)
    print(f'{len(kills)} efectos de kill, {len(packs)} paquetes, {len(b.models)} modelos, {len(b.fx)} efectos, '
          f'{len(b.store.written)} texturas, {len(b.sounds)} sonidos; vfx/ {total // 1024} KB')
    if problems:
        print('AVISOS:')
        for p in problems:
            print(' -', p)


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    main(sys.argv[1])
